# The recursive world model: design spec

Adopted 11 November 2026 on the user's direction. Written from branch `structure` at 7ff3ccf (local), LOOP-AUDIT.md (origin/structure e79d91a), WEIGHTING-DIAGNOSTIC.md, the `bnm-56c.2` notes, augur at f9fdb4d (v0.19.0, ~/src/augur-loops), and docs PLAN, HANDOVER, LATTICE, RELATIONS, BUFFERS, COUPLED-SYSTEMS §6, PRINCIPLES and DOWNSCALE.

## 0. The idea in one paragraph

There is one kind of object, the node, at every place and every time scale. The world model is the root node; it is not a separate system. A node's state is a set of quantities about the world: stocks, flows, buffer invariants, a latent hazard frailty per kind, regime probabilities and tensions. Its forecast is something it issues, not part of its state. States move between nodes in three ways:
- up, by identities (sums, survival products), plus emergent features computed only at the parent;
- down, as lagged context and as constraints;
- sideways, along lagged sibling edges.

Forecasts are computed from states and written to the ledger, and nothing reads them back. This turns the audit's main finding into a design rule: today the lattice passes the combined forecast `:s = logit p` as the state, which is why five loops read a node's own output. Zooming in or out is moving along the place tree or the time tree. Identity quantities survive the zoom exactly. Emergent quantities appear only when you zoom out.

## 1. The node contract

### 1.1 Identity and the trees

```clojure
;; node id
[level place scale]   ; e.g. [:country "KEN" :year], [:world "001" :half-decade]
;; place tree (bnm.geo + bnm.cells):
;;   :cell-country "KEN@63fe8..."  (A5 r6 x country; storage and measurement)
;;   -> :province (GeoNames admin1) -> :country -> :subregion (M49) -> :region -> :world
;; time tree (months as the clock, as bnm.lattice):
;;   :week -> :month -> :quarter -> :year -> :half-decade -> :decade (-> :century, a prior only)
```

Cells are storage. Following CVP and PRINCIPLES 11, the lowest inference level is the province. Cells enter only through `bnm.cells/aggregate`, by `:sum` for stocks and `:mean` for intensities.

### 1.2 What every node holds (`bnm.node`, plain EDN)

```clojure
{:id [level place scale]  :t origin-month
 ;; MATERIAL: a stock changes only by its flows (PRINCIPLES 8)
 :stocks {stock-id {:level x :sd s :unit u :known k :usable {:share :sd}
                    :record <bnm.buffers/record>}}
 :flows  {flow-id {:rate r :sd s :from stock-id|:outside|[sibling-id stock-id]
                   :to ... :unit u :known k :rigid share}}
 :buffers {stock-id {:cover {1 v 3 v 5 v} :gross-cover v :drain-rate v :restoration v
                     :claim-gap v :band :hedge|:speculative|:ponzi :rigidity v
                     :displacement {5 z 10 z 20 z} :regime :buffered|:constrained|:unknown}}
 ;; EVENTS: one latent per kind, the same 13 kinds everywhere
 :hazard {kind {:z m :z-var v      ; frailty: log-rate shift, augur.hier/states-style
                :rate lambda        ; expected onsets per month, at this node
                :window w           ; this scale's horizon, months
                :p p-window}}       ; 1 - exp(-lambda*w*E[e^z]), computed, not learned
 ;; REGIME: filtered, one per node (per kind family where data differ)
 :regime {:probs {:calm a :strained b :depleted c}
          :drivers {:world-price .. :cover .. :evidence-cusum ..}}
 ;; CONTRADICTIONS: pairs of opposed aspects, from theory (LATTICE.md, ANATOMY.md)
 :tension [{:pair [:accumulation :constraint] :a id :b id
            :gap g :acc A :since t :resolved-at [t ...]}]
 ;; the node's sheaf coboundary against its covers (self vs parent, self vs coarser)
 :gaps {[:parent] g [:coarser scale] g}
 ;; PROVENANCE
 :reads [...]  ; bnm.sheaf reads, every one before t, including :self
 :known k}     ; latest input's known-at
```

Issued forecasts are kept apart from the state, in a write-only ledger book that no node reader can reach:

```clojure
{:id [level place scale] :t origin
 :forecast {kind {:p p :interval [lo hi] :f f :q q
                  :confidence {:regime-entropy h :gate-certainty c :combined k}
                  :weights {group w} :per-model {group {:p .. :lp-issue nil}}}}}
```

`:lp-issue` is filled in when the outcome is known. It is the log score of the forecast the model issued, which is the evidence the averaging uses (audit issue 2).

The five categories map onto the user's dialectics as follows:

| framing | where it lives |
|---|---|
| accumulation against constraint | `:stocks` growth against `:buffers :cover` / the engine floor; `:tension` pair `[:accumulation :constraint]` |
| buffer against flow | `:buffers` (cover, restoration > 1 = runs out first); regime switch `:buffered → :constrained` = the phase transition |
| investment against profit | SFC flows (E1, E2): investment flow against the profit flow, the claim gap band; `:tension` pair |
| small bubbles up | children → parent identities and emergent features (§2) |
| top feeds down | parent `:hazard :z`, `:regime`, world prices, identity constraints (§3) |
| leap negates the contradiction | `:tension :resolved-at` on a known onset; **and** event → stock edges (§5, step 8) |

### 1.3 The node's step: one function at every level

```clojure
(bnm.node/step node-spec ctx) -> {:state node' :issued forecast :pending rows}
;; ctx, every field read through a bnm.sheaf reader whose :origin is t:
{:own-prev    state at t - per           ; :self read, recorded
 :children    [state at t]               ; same scale, finer place: identities + emergent
 :finer       [states over (t-per, t]]   ; same place, finer scale
 :parent-prev state at t - per           ; lagged: context/constraint
 :coarser     latest state before t      ; lagged
 :siblings    {id state at t - per}      ; lagged, with edge weights
 :obs         measurements known by t    ; gauges, stocks, flows, catalogue onsets (to :through only)
 :resolved    outcomes known by t}       ; for learning
```

Inside, in order:
1. **Assimilate.** Measured stocks and flows update `:stocks` and `:flows`. Where the engine runs for the place, this is its EnKF analysis; otherwise it is the latest known value with its sd.
2. **Invariants.** Run `bnm.buffers/invariants` on `:stocks`. The regime switch is the engine's `min(wanted, available)`.
3. **Aggregate.** Combine children and finer scales into the identity quantities (§2.1) and the emergent features (§2.3).
4. **Filter the frailty `z` per kind.** The prior is the parent's z (lagged) times ρ. The evidence is this node's own resolved onsets plus its children's (augur.hier/states, top down and bottom up).
5. **Filter the regime** from its drivers (§6.3).
6. **Tensions.** Compute the gaps, accumulate them, and reset them on a known onset.
7. **Learn and issue.** Learn from the resolved rows using the issue-time evidence. Issue the forecast through the regime-gated combination of the node's groups (§6.3). The forecast reads the state; nothing reads the forecast.
8. **Pending rows.** Each carries `x`, `o`, every model's issue-time predictive, and the regime probabilities at issue.

What the levels differ in is data only: which stocks are measured, the cadence, which kinds have a catalogue there, and whether an engine runs. A node with no measured stocks has empty `:stocks`. Its hazard is then a hazard over 1–5 year windows (PLAN principle 3), and its `z` is carried by its parent.

## 2. Composition up the trees

### 2.1 Identity quantities: exact up both trees

**Place tree (children C of parent P, same scale):**
- **Stocks** (extensive, same unit): `S_P = Σ_c S_c`, with sd² = Σ sd_c² + 2Σ cov. Use the covariance where engine ensemble members give it, otherwise independence, flagged.
- **Flows:** `F_P(ext) = Σ_c F_c(ext)`. Flows between two children cancel at the parent: `F_P(c→c') = 0`. This makes the parent SFC-consistent: Σ net lending of the children = P's net lending with the outside, and at the world node Σ current accounts ≈ 0, with the residual tracked (1.7 hidden buffers).
- **Usable stock:** `Σ usable_c`. The usable shares do not average.
- **Intensive gauges** (rates, shares): weighted means with declared weights (GDP, population, area), as `cells/aggregate :mean`. These are identities only given their weights, and the weights are declared in the record (`:aggregate {:how :mean :weight :gdp}`).
- **Counts:** `N_P = Σ N_c`, de-duplicated where an event touches two children (the Hawkes κ problem). The rule is to count by the event's cell, never by the dyad.
- **Hazards as Poisson rates:** `λ_P = Σ_c λ_c` exactly for expected counts.
- **Hazard as P(any onset in P):** use the conditional form, integrated over the shared frailty:
  ```
  P_any(P) = 1 − E_z[ Π_c (1 − p_c(z)) ],   p_c(z) = 1 − exp(−λ_c e^{z} w)
  ```
  This uses Gauss–Hermite quadrature over z ~ N(z_P, v_P), reusing `bnm.lattice/hermite`. With v_P = 0 it reduces to `1 − Π(1 − p_c)`. The difference between the two is the emergent correlation (§2.3).

**Time tree (finer scale f inside coarser scale g, same place):**
- **Flows:** `F_g = Σ_{f in g} F_f`. Monthly flows sum to the year.
- **Stocks:** `S_g = S_{f = last in g}`, the end-of-period stock. Average stocks are a separate declared measure.
- **Hazards:** `1 − p_g = Π_{f in g} (1 − p_f)` over consecutive, non-overlapping fine windows; equivalently, cumulative hazards add, `Λ_g = Σ Λ_f`. This is the identity Round 3 / Test 9b must hold under a hazard that varies through time, not only under a constant one.
- **Overlapping windows** (the lattice's quarter nodes forecast 1–2 years ahead) are not composable by product. The composition functions refuse them. For the year node, the quarter states used are the per-month rates `λ`, not window probabilities.

### 2.2 Functions (new `src/bnm/compose.clj`, pure)

```clojure
(sum-stocks children stock-id)               -> {:level :sd :n :missing}
(net-flows children flows internal?)          -> external flows; internal pairs cancel
(rate-sum children kind)                      -> lambda
(any-onset children kind {:z :z-var} w)       -> P_any by quadrature
(survival-chain fine-ps)                      -> coarse p   ; refuses overlapping windows
(cumhaz-split coarse-lambda shares)           -> fine lambdas (top-down allocation)
(reconcile-project values identities)         -> values projected onto the identities
                                                 (augur.enkf/project for linear ones; weighted
                                                 least squares / MinT for hazards on log-rate scale)
```

Each one has a test in `test/bnm/compose_test.clj` (§4).

### 2.3 Emergent at the parent: exists only when zoomed out

These are computed in step 3 of the node step (`src/bnm/emergent.clj`). They are new state variables of the parent, and they are passed back down as context.

| feature | definition | why it is only at the parent |
|---|---|---|
| **frailty variance / synchrony** | v_P, the spread of z across children; the correlation of children's residuals (augur.hier/states level `:sd`) | a single child has no cross-section |
| **common shock** | z_P itself, filtered from Σ children's (observed − expected) counts | 2020–21 food: rate 0.49 everywhere, AUC 0.5 within the year. This is z_P, not any child's input |
| **breadth** | share of children with displacement > 1.5 or regime = constrained (`bnm.invariants/aggregate`) | a count over children |
| **dispersion / uneven development** | variance of children's growth, cover and claim gap | |
| **concentration of stress** | HHI of the children's constrained shortfalls | where the loss lands |
| **prices** | at the world node (and region for regional markets): the price clears the aggregate imbalance. Today it is read from data (FAO, Brent, Pink Sheet). Later the price network's level, with the convex storage curve on world cover (`bnm.food` storage pressure, `:market-reading` inverted) | price is not a sum of anything |
| **net external position** | Σ flows across the parent's boundary (internal flows cancelled) | only meaningful for a closed set of children |
| **contagion intensity** | spectral radius of the children's sibling-edge matrix (Hawkes branching, trade-weighted exposure) | a property of the network |
| **accumulated tension at the coarser scale** | Σ of the fine tensions at the coarser node's own pace (`accumulate`) | months of strain become years |

**How resolutions combine to give the higher features.** Fine resolutions supply the identity quantities exactly. The parent learns only what is left once those are subtracted: its emergent features and its own frailty. For example, the parent's onset rate is `Σ λ_c × E[e^{z_P}]`. The children carry where; z_P carries the common level (when). The parent therefore never re-estimates what the children already know. It estimates the common component, which is the decouple/recouple of Berry and West (DOWNSCALE.md) carried up the whole tree.

### 2.4 Siblings

Edges are data (`resources/edges/*.edn`, plus derived tables), read at t − per only:
- **adjacency** (`bnm.cells/neighbours` aggregated to provinces and countries): the spatial prior (BYM2/ICAR) on z;
- **trade** (BACI shares, the EXIOBASE yearly table published at t, about t−2): exposure of `:flows` to the sibling's constrained shortfalls. One child's cut outflow is another's cut inflow, as in the engine's propagation;
- **price network** (Leontief `(I−A')^{-1}` on product × region, with lags by stage; PLAN 5b);
- **event contagion** (Hawkes α between places, weekly scale only).

A sibling edge's coefficient is learned, with a heavy prior, and partly pooled across all edges of a type.

## 3. The single world node at the top

The world node is `[:world "001" scale]` at every scale. Its state follows the §1.2 contract, filled as follows.

| part | content | sources (known-at rule) |
|---|---|---|
| `:stocks` | world grain stocks (wheat, maize, rice, less China; USDA PSD), crude and products inventories (JODI / IEA OECD), FX reserves Σ, global debt Σ (BIS), World3 aggregates at decade | USDA y+1 (revised, flagged), JODI m+2, BIS q+1 |
| `:flows` | world output, trade volume, Σ current accounts (residual = discrepancy), grain use | IMF WEO/WTO |
| `:buffers` | world stocks-to-use, oil cover in days, reserves cover of world imports | via bnm.buffers |
| **prices** (emergent, §2.3) | FAO food index (level, 12 and 24-month change), Brent, Pink Sheet metals and fertiliser, USD index, VIX, global credit (BIS global liquidity), US policy rate | monthly, known about t+1m |
| `:hazard` | z_world per kind: the common shock filtered from Σ country onsets; λ_world = Σ λ_country | catalogues to `:through` only (issue 7) |
| `:regime` | calm / strained / depleted for world food, energy and finance, driven by prices and world cover | |
| `:tension` | world credit against world output growth; world stocks against use; price against cover (`:market-reading` against measured cover) | |

**How it constrains everything below.** This is the missing top-down loop: L12, issue 3, B4.
1. **Context × exposure.** Every country node reads the world price and credit state at t, lagged by publication, through its exposure (food imports, net fuel, openness: `fast_run/exposure-fn`). This happens at the year and half-decade scales, not only at quarters (Round 2, Test 3a).
2. **Frailty prior.** A child's z prior mean is `ρ · z_parent(t−per)`, so a world common shock raises every country's rate before any country's own data show it. This is the only route by which the 2020–21 level could be anticipated within the year: the frailty is filtered from monthly or quarterly onsets known by t, not from 3–4-year-late year rows.
3. **Regime prior.** A child's regime transition matrix is conditioned on the world regime: `P(strained_c | world strained)` is higher, learned with a heavy prior from the episode library.
4. **Identity constraints.** Children's flows and stocks are projected onto the world identities: Σ CA = 0 plus the discrepancy, Σ grain stocks = world, and λ_world consistent with its children. `compose/reconcile-project`, with the residual kept as a hidden-buffer signal.
5. **It replaces the degenerate world node** (issue 10). The "any onset anywhere" target is dropped. The world is scored on counts per kind (Poisson) and on world price shocks.

There is no circularity. Children read `world(t−1)` or prices known by t. The world at t reads children at t through identities, and its own frailty from counts known by t.

## 4. Coherence invariants (testable)

New `test/bnm/coherence_test.clj` (synthetic) and `tools/bnm/coherence_report.clj` (on a run's book; a diagnostic, nothing fitted).

| # | property | test |
|---|---|---|
| C1 | **Stock and flow identity up the place tree**: for every parent and stock, \|S_P − Σ S_c\| ≤ tol·\|S_P\|, and the same for external flows; internal flows sum to 0 | synthetic tree of 3 levels; real: `coherence_report` on world grain and reserves |
| C2 | **SFC closure at every node**: rows and columns of the TFM/BSM sum to 0 (`bnm.stockflow/imbalance`); Σ net lending of children = parent's external net lending | sfc_test extension; a run hitting a clamp fails (issue 16) |
| C3 | **Zoom is lossless for identities**: `(aggregate (allocate x shares)) = x` and `(allocate (aggregate xs) (shares xs)) = xs` to 1e-12; `survival-chain` ∘ `cumhaz-split` = identity | compose_test, property-based over random trees |
| C4 | **Hazard composition**: λ_P = Σ λ_c exactly; P_any with v = 0 equals `1 − Π(1−p_c)`; computed and asserted: P_any falls as v grows at a fixed expected count (z = −v/2), and rises as v grows at a fixed mean log rate (z = 0) when events are rare | compose_test |
| C5 | **Time composition**: for a linearly rising hazard over 24 months, the coarse p from the fine pieces matches the analytic window probability to 1e-9; overlapping windows are refused | Round 3 Test 9b |
| C6 | **No node reads its own issued forecast**: no read in any trace has `:dir :issued`; the ledger book is not reachable from `sh/reader`; the `:self` read is of `:state` only | node_test plus golden; Round 1 Test 1b |
| C7 | **Weighting invariance of states**: two runs differing only in `:weighting` have identical states and `x` to 1e-12 | Round 1 Test 1a, extended to every node state field |
| C8 | **Known at the forecast date**: every read has `at ≤ t`, every observation `known ≤ t`; catalogues are counted only to `:through`; the vintage is the one published by t, or the read is flagged `:revised` | sheaf `record!` throws; stocks_test (issue 18); Round 4 Test 7 |
| C9 | **Evidence is issue-time**: the weight update for a row uses `:lp-issue`, the log predictive stored at issue | augur dynamic_test, Round 1 Test 2a |
| C10 | **A link that carries nothing costs nothing**: adding a noise group at any node changes pooled Brier by ≤ 0.0003, and by ≤ the per-regime bound in each regime stratum | the noise rule (bnm-56c.2 acceptance) |
| C11 | **One-way per edge per step**: within one origin, the dependency graph of reads is acyclic (up at t, down and sideways only from before t) | node_test builds the read graph per step and checks for cycles |
| C12 | **Regime switch conserves**: a buffered → constrained switch creates and destroys nothing; wanted = delivered + booked | regime_test (exists) |
| C13 | **Reset on leap**: after a known onset at place p, p's tensions are 0 and the matching stock edge has fired (event → stock) | step 8 test |
| C14 | **Branching subcritical in learning**: the spectral radius of the learned Hawkes matrix is < 1 after every EM step | Round 6 Test 17 |

## 5. Mapping the current components

| component | role in the recursive model | reuse / refactor / missing |
|---|---|---|
| `bnm.sheaf` | the reader for every node read; the coboundary gives `:gaps` | **reuse**. Add `:dir :self`, and an `:issued` book kept outside the reader |
| `bnm.lattice` | the node step for the event part (`:hazard`, `:tension`, issue, learning) at country/subregion/region/world × quarter/year/half-decade | **refactor**: the book entry `{:p :s ...}` becomes `{:state {...} }` with `:s` from `:state-from` (Round 1); the downscale offset becomes the frailty prior (step 4); `inputs` gains the world and stock groups; `reconcile` moves to compose |
| `tools/bnm/world_run.clj`, `lattice_political/banking.clj` | the spec builders per kind | **refactor**: one spec of 13 kinds that share the node's state (stocks, regime, z), with separate coefficient sets per kind. Relations between kinds stay off as main effects (RELATIONS: level); kinds interact through shared state and severity (PLAN 5) |
| `bnm.relations` | readers across kinds | **reuse** for severity-given-onset only |
| `tools/bnm/reconcile.clj`, `lattice/reconcile` | time reconciliation | **replace** with `compose/survival-chain` and `reconcile-project` (fixes issue 9's double count) |
| `bnm.buffers` | `:stocks` → `:buffers` at every node | **reuse unchanged**. Add an `:aggregate` declaration per record so compose knows how to sum it |
| `bnm.hazard` (pooled over families) | the hazard prior for each node's constrained regime: maps `:buffers` to a break probability per stock | **reuse**. Fix the folds (issue 8). Expose `predict` as the node's `:buffers`-group input |
| `bnm.scanner` | a world-node view: rank every node's stocks by the pooled hazard; residuals = hidden buffers from C1/C2 gaps | **reuse**. Feed it the coherence residuals |
| `bnm.episodes` | the regime prior's training data (transition rates by family) | **reuse** |
| `bnm.engine` (held stocks, regime switch, propagation) | the material dynamics inside a node with an engine; sibling cuts via counterparts | **reuse**. **Missing**: event → stock edges (issue 6) and the node ↔ engine adapter (E10) |
| `bnm.sfc` | the flow identities at country nodes; world = Σ | **refactor**: close the debt-service loop, no clamps (issue 16); expose `:stocks`/`:flows` in node form |
| `bnm.world3` | the world node's decade-scale stocks | **reuse** as a prior-only slow state (no data link; PLAN "no forecasting record") |
| `bnm.allocation`, `bnm.capacity` | flows (credit → capacity) inside the node | reuse; nothing reads `capacity/*` today (E8) |
| `bnm.model` (weekly Hawkes) | the week-scale node; provinces and countries | **refactor**: the country total comes from provinces, reconciled (H3); the year frailty sets the baseline (H4); Hawkes α = sibling edges; branching projection (H1) |
| `bnm.cells` | storage under provinces: `aggregate`/`rollup` are the cell → province → country identity functions | **reuse** as compose's leaf aggregator |
| `bnm.supply` (EXIOBASE graph, `outage-path`, `exposure`) | the trade and price sibling edges; shock propagation | reuse; **missing**: yearly tables and the dynamic price network (PLAN 5b) |
| `augur.dynamic` | per-node DGLMs and averaging | reuse. **Add** `:evidence-at :issue` (Round 1) and `:weighting {:rule :gated}` (step 6) |
| `augur.hier` (`states`, `grouped-slopes`, `intercepts`) | the frailty over the place tree; slopes pooled across siblings | reuse. **Add** an online one-step `states-step` (today `states` fits over a span with grid search; `refilter` reruns it) |
| `augur.enkf/project` | identity projection (C1/C2) | reuse |
| `augur.changepoint` | the evidence CUSUM driver for the regime | reuse |
| `augur.ledger` | the issued-forecast book, scoring and `:lp-issue` | reuse |
| missing outright | `bnm.node`, `bnm.compose`, `bnm.emergent`, `bnm.world-state`, `bnm.regime-filter`, `bnm.edges`, `tools/bnm/world_model.clj` | new |

The name `bnm.regime` is already taken by tools/bnm/regime.clj (a US eras test), so the filter is `bnm.regime-filter`.

## 6. Statistics: tractable with few events

### 6.1 Where the information comes from
- **Places pool through the frailty tree.** A country with no onsets of a kind borrows the subregion's, region's and world's z (augur.hier/states, Duffie et al. frailty). A level whose states do not help forecast is given sd = 0 by the prequential grid and drops out, which is the "costs nothing" property at the structural level.
- **Slopes pool across siblings** (augur.hier/grouped-slopes): shared b plus deviations d_g ~ N(0, τ²). The groups are the PLAN phase-3 structure types (export mix, cell profiles) and the region. A single pooled slope averages Dube–Vargas-type opposite signs to zero; this does not.
- **Stock types pool through the invariants** (bnm.hazard, families partially pooled). Reserves teach about fuel.
- **Kinds pool through shared state, not shared coefficients.** The node's regime and stocks are common to all 13 kinds. Each kind has its own coefficients on them, shrunk toward a cross-kind mean by `:group-shrink`.
- **Scales pool through composition.** The year rate is not learned only from 3–4-year-late year rows. Its λ is the sum of the quarters' λ over the year (§2.1), and the year node learns a correction. So evidence at the fast scale reaches the slow scale's level at once. This is the structural fix for the diagnostic's Q4 lag.
- **Priors are heavy and from data** (PLAN principle 6), with discount near 1 at coarse scales and about 0.97 at the week.

### 6.2 Few groups per node
A node averages a small number of groups:
- base (offset = parent frailty prior);
- own gauges;
- world context × exposure;
- buffers / hazard;
- tension;
- history;
- fine-scale up.

Each is nested in the base, with group shrinkage. The `all` model stays as a reference: it is the default `:state-from` in Round 1, until step 4 replaces the reference with z.

### 6.3 Regime-gated encompassing inside every node (bnm-56c.2's design, placed)

- **Regime filter** (`src/bnm/regime_filter.clj`):
  - Three hidden states, filtered online. Transition probabilities are logistic in drivers known at t:
    - world-node price momentum and the world regime (the diagnostic's r = 0.65 signal: price, not buffers);
    - the node's `:buffers` (min cover, restoration > 1, the claim-gap band) and breadth among its children;
    - a small fixed weight on an evidence CUSUM (augur.changepoint) over issue-time scores.
  - The prior transition matrix comes from the episode library and is pooled across siblings. The filtered probabilities `P(r)` are part of `:regime`. They are state, so they pass down as context.
- **Gates:**
  - Models are ordered by evidence. Each model's increment is taken over the combination of the stronger ones (online encompassing).
  - Gate evidence `E_r(g)` is a discounted log-likelihood ratio from **issue-time** scores. A row updates each regime's evidence by the `P(r)` stored at issue, not the current one.
  - Gate evidence is pooled across siblings: `E_r(g)_node = E_own + κ E_siblings`, with κ set by empirical Bayes and the result shrunk toward the parent's `E_r(g)`. This addresses the thin stressed set: only two food episodes.
  - `w_g = Σ_r P(r) σ(E_r(g)/c)`. Gates start nearly shut.
- **Confidence:**
  - `:confidence` = regime entropy, each gate's evidence sd, and a combined score.
  - When confidence is low, the forecast shrinks toward the stronger models.
  - Because the frailty prior is the base offset, "the stronger models" at a node includes the parent's common shock. A new regime with no gate evidence still inherits the level from above. That is how `down`'s 2020–21 role is carried by structure rather than by a weight.
- **Loop guards:** the regime's inputs are states and lagged issue-time scores only. The gates' output never enters any state (C7).

## 7. Revised build plan (test-first; each step scored, opt-in, ledger amendment)

Every step uses the following protocol:
- write the failing test first;
- add a spec key, off by default, and check the golden test is byte-identical when it is off;
- score on the development folds (rolling, grouped, gapped) against t48 and named;
- apply the noise rule and the planted tests;
- register the step for the forward ledger.

The base stays t48 until a step is adopted.

**Base of record (13 November 2026, later): t71-stack** (fourth note to amendment L1, docs/PHASE-CHANGES.md): t65-gauges with `--real-lags true` (bnm-a1e), `--at-risk catalogue` (bnm-96j), `--lead-std history` (bnm-11y) and `--frailty-gauges true` (bnm-v1y, step 5a). Steps bench against it. Against t65-gauges pooled +0.0023 [+0.0004, +0.0046], against t53-noclim +0.0062, no fold lost (docs/CALIBRATION.md 15-17); alerts at 4:1 pooled cost 0.298 against 0.310, 8 of 21 episodes caught. Open: market-crash false alarms from vix12 and the 2022 curve; food 2020, growth 2007-08 and 2019, banking and sovereign defaults never alert; σ at the grid edge (bnm-02u).

Earlier: **Base of record (13 November 2026): t65-gauges** (third note to amendment L1, docs/PHASE-CHANGES.md): t63-issue's command (t53 plus the fixes of LEADING.md 4 and `:evidence-at :issue-joint`) with the leading gauges and their priors from theory (`--leading true --leading-priors true`, bnm-56c.36). Steps bench against it. Against t53-noclim pooled +0.0040 [+0.0018, +0.0065], no fold lost against t53, t62 or t63 (docs/CALIBRATION.md 13). Open: market-crash false alarms 2018-22 from the 2022 curve inversion and vix12, an approach limit, not tuned post hoc.

Earlier: **Base of record (12 November 2026): t53-noclim** (second note to amendment L1, docs/PHASE-CHANGES.md): t52's command with `--known r7-at-fx` and `:flow-std-from :resolved` on the data of bnm-56c.31 and .32. Steps bench against it. Against t52 pooled −0.0009 [−0.0036, +0.0018], no fold lost; market crashes 2007–09 −0.127, one origin, a level shift (docs/LOOP-AUDIT.md 6).

**Step 0: Round 1, under way. What it must respect now:**
- `:state-from` has the signature `(fn [{:keys [scale level place t models x o]}] -> {:s double ...})`. It is pure in inputs and model states and independent of the weights. The default reference is the `all` model's predictive. Return a map so that `:z` and `:regime` can be added in steps 4 and 6 without changing the shape.
- The book entry becomes an open map: the state and the issued p are kept apart, even if `:p` is still stored for now. The errors fed back (`:feedback`) use the reference `p`, not the combined one.
- The own-state read goes through `sh/latest-before` with `:dir :self` (1b).
- Pending rows store `:lp` per model at issue, and a slot `:regime-at-issue` (nil now).
- Add `:evidence-at :issue` to augur and pin the release (2a, 2b).
- C6, C7 and C9 tests are in place.
- Then re-run candidate A and the regime-gated draft on clean states.

**Step 1: node schema, compose and the coherence report. No forecast change.**
- Files: `src/bnm/node.clj` (schema, `validate`, `from-lattice-entry`, `from-buffer-rows`), `src/bnm/compose.clj`, `test/bnm/compose_test.clj` (C3–C5), `test/bnm/node_test.clj` (C6, C11), `tools/bnm/coherence_report.clj`.
- Delivers: t48 unchanged, plus a report of C1/C4 gaps on t48's book. For example, how far the year p differs from the composition of its quarters' p.
- **Status (11 November 2026): done, no forecast changed** (bnm-56c.18).
  - Built: `bnm.node`, `bnm.compose`, compose_test, node_test, coherence_report_test and `tools/bnm/coherence_report.clj`. docs/COHERENCE.md has the gaps on t50 and t48.
  - C4's sign: at a fixed expected count, frailty variance lowers P(any onset); at a fixed mean log rate, it raises P(any onset) for rare events. The tests assert both.
  - The lattice's Hermite nodes are now public (`bnm.lattice/hermite`).

**Step 2: the world node (audit Round 2, issues 3, 4, 10).** Bead `bnm-56c.16`/`bnm-tee`/`bnm-0cd.2`.
- `src/bnm/world_state.clj`: series with known-at; the reader throws after t (Test 3b).
- The lattice's `:world-gauges` group at the year and half-decade nodes, direct and × exposure (Test 3a planted).
- The world node's `:state` = world state, replacing the any-onset target.
- Quarters get a learned pool weight on the offset, or read world gauges outside its pull (Test 4).
- Registered acceptance: food price shocks 2020–22 by fold, market crashes (the VIX state), the noise rule, and a planted false world move.
- **Round 4 hygiene runs alongside:** the stocks.clj ordering (Test 18), catalogue `:through` (Test 7), the `:revised` reserves flag and the bnm-pjg.6 delay test (Test 12).
- **Status (12 November 2026): built, scored, not adopted** (bnm-56c.19, with bnm-h8d and bnm-56c.22).
  - Built, all opt-in: `bnm.world-state` (Test 3b), the lattice's `:world-gauges` group (Test 3a), `:downscale-learn` (Test 4, a small gain on synthetic data), `:learn-offsets :mixture` (56c.22), `bnm.world-gauges` and `bnm.world-run --world-gauges/--pwt-stale/--catalogue-through/--set`. Round 4: Test 18 fixed, Test 7 opt-in, Test 12 marked, the delay test run (docs/PHASE-CHANGES.md).
  - The candidate t51-world-norel passes the planted and false-move rules and the pooled noise rule, and fails the per-fold noise rule and the folds against t50 (pooled −0.0046, 2020–22 −0.0044 lost; in 2020–22 food price shocks −0.025 and market crashes −0.027; market crashes pooled −0.037). The cut loop alone (s2-ref) carries the whole food 2020–22 loss; the world group earned no posterior weight at the food year node after 2010, so the issued forecast never read it there. At market crashes it did: the world-gauges model held 0.77–1.00 of the year country node's weight in 2005–08 and was the issued forecast in 2006–07, most of the −0.305 loss in 2007–09 (scratchpad step-2 diagnosis, 12 November).
  - Next: the world state is in the reference but not in what is issued. Step 3's frailty, or a weighting that lets the reference's world group reach the forecast, is where the 2020–22 level would have to come from.
  - **Rescored after fixes 2 and 3 (12 November 2026, bnm-56c.24/.25, docs/PHASE-CHANGES.md): not adopted.** The world gauges now enter once per origin as the countries' offset (`bnm.world-term`, food and Brent only; VIX, credit, dollar and policy rate unwired; `bnm.lead-test` gates any return), and the reference is the base model, which carries that term. fs-s2-cand: food 2020–22 +0.0019 against t50 (t51 −0.025), planted met; noise (−0.00044 pooled), false move (2013–19) and 2020–22 against t50 (−0.0047, lost) not met, all three carried by market crashes (−0.065 in 2020–22, −0.36 in 2007–09), where food and Brent momentum fail the lead test: approach wrong there. Gating the term per kind by the lead test is the open decision. Year nodes also issue 0.5 before any resolved row (bnm-56c.26).
  - **Closed as sequencing (12 November 2026, bnm-56c.29).** With the gate as a statistical test (Diebold–Mariano HLN, Newey–West, Holm within α = 0.10 per origin over 26 cells), no gauge is admitted at any kind in 2003–22: against t52 no fold lost, pooled −0.0003 [−0.0032, +0.0027]; against t50 2020–22 still lost (−0.0034). The signal is real but too weak for a hard admit/reject test (a planted lead passes 35–48% under the full correction; food12 at market crashes was worth +0.12 in 2007–09 under the old gate). A hard gate is the wrong instrument at this stage; the gauges enter step 5 as confidence-weighted inputs to the regime-gated weighting instead. The world term stays off in the base; the gate code stays as the harness for step 5.

**Step 3: frailty states (new; makes the place tree recursive).**
- Augur: `hier/states-step` (an online filter step for one period, with the variances fixed from the last prequential grid fit, re-gridded each year).
- Lattice: `:state-from :frailty`. The state passed up and down is z_kind, not logit p. The downscale offset becomes `ρ z_parent + log λ_parent-share`.
- Tests:
  - C4;
  - a planted common shock: a world where all countries' rate jumps for 2 years with no country signal. With the frailty, the year node's mean p must track the jump within a year, from quarterly onsets known by t; without it, it must not;
  - a noise level (a random grouping of countries) gets sd → 0.
- Score: food 2020–22 against the oracle ceilings in weighting-diagnostic Q6.

**Step 3b design: the common shock as one state (12 November 2026, bnm-56c.20, bnm-3be).** Lattice option `:frailty`, off by default.
- **What z is.** One scalar per node `[scale level]`, per kind, per origin: z_t, a log-odds shift shared by every place of the level for the window issued at t. It is AR(1) around zero across the node's origins, with persistence ρ and stationary sd σ. For rare events a log-odds shift is the log-rate shift of §1.2. Each kind runs its own lattice, so z is per kind.
- **How it is filtered, with no lookahead.** z_s is updated only when origin s's rows are known: their `:known-at` is at or before t, so the targets' resolution lag holds. The update is one Laplace step on that origin's batch (augur `hier/states-step`). It is taken against the reference model's issue-time predictive: by default the base model (intercept plus offset), which reads no weight, so C7 holds. The prior is the filter run forward one period from the last filtered origin. Rows of an origin that resolve late update the current state.
- **The prior at issue.** At origin t the prior is N(μ_t, v_t), the filter run forward from its last filtered origin to t (h periods: μ = ρ^h m and v = ρ^{2h} v + σ²(1 − ρ^{2h})). Under `:prior :parent` the prior is instead ρ times the coarser level's z at the same scale, filtered before the node issues, so it is lagged. That is §3.2's form. It is not the default: at one scale every level resolves at the same lag, and from the same onsets, so the parent's z adds nothing fresher than the node's own. The fresher source is a finer scale, which is step 4.
- **ρ and σ.** These come from an online prequential grid (`:rhos`, `:sds`). One filter runs per pair, and each is scored on each batch by the batch's marginal likelihood under the prior that pair would have issued with, i.e. at the horizon the prior serves. The forecasts use the best cumulative score, after a pair with σ > 0 pays `:penalty` nats (2, Akaike's price for two parameters, as in `augur.hier/states`); ties go to the smallest σ. σ = 0 is in the grid, so a node with no common shock gets none and the option costs nothing there. Origins issued before the node's first resolved outcome are neither scored nor filtered: there the base reads only climatology 0.5 or the prior, and its miss is that uninformed start (bnm-56c.26), not a common shock.
- **How it enters the predictor.** Every model's linear predictor gets z as an offset. The forecast is integrated over N(μ_t, v_t) by the five-point Hermite rule: p = E_z[p(o + z)]. The models' issue-time predictives are stored without z, and z goes into their evidence below.
- **Why the n-fold count goes away.** Each resolved row carries its origin's shared effect, `:shared [id μ_s v_s]`, where (μ_s, v_s) is the prior the forecast was issued with. augur's `step` learns one origin's rows jointly with z_s: the state is augmented by z_s, updated, and z_s marginalized out. A time-common input (the intercept, or a world gauge identical at every place) is confounded with z_s within the origin. Its information from that origin is therefore bounded by about 1/σ², one observation's worth, whether the origin holds 1 row or 165. The averaging's evidence for the origin is the batch's log marginal with z_s integrated (Laplace), not the sum of n independent log scores. A shock common to n places then moves the weights by about one observation's evidence instead of n. That is bnm-3be fixed in both places: the coefficients, and the weights.
- **With `:clim-learn :current`.** The level is climatology plus the intercept; z is a mean-zero transient around it. z is filtered against the base predictive, which already holds the level, so z is the departure from the level and never the level itself. The intercept learns the departure from the current climatology, and at most about one observation per origin, so a persistent shift reaches the level slowly while z takes the transient part. Without `:clim-learn :current`, a change of climatology is counted twice (bnm-56c.27), and the drifting intercept and z would compete for the same quantity. The candidate therefore runs with both.
- **Mean one.** The shift applied is z less half its stationary variance, as in `augur.hier/states`: with nothing known the expected rate is the base's. The first run without this, t57-frailty-v1, issued rare kinds too high; that was a bug, fixed in 4aca271.
- **Status (12 November 2026): built, scored, not adopted** (branch `frailty`; augur v0.22.0: `dynamic` `:shared` and `hier/states-step`; 2026-Q4-r1 reproduces byte for byte with the option off).
  - The planted tests pass. Under the option, the variance of a time-common slope saturates as places are added; without it, it falls as 1/n. The weights stop locking at 100 places. A world with no shock chooses σ = 0.
  - t57-frailty against t53-noclim: pooled year Brier +0.0022 [+0.0006, +0.0040], a significant gain; market crashes +0.030 (country year 0.1996 → 0.1701). Against t56-cand: +0.0008 [-0.0003, +0.0018].
  - It fails the fold rule in 2007-09 (-0.0032 [-0.0052, -0.0005]): market crashes on t53-noclim's weight-lock path, and banking crises on the persistence ρ = 0.8 carried through 2006-10.
  - Traced gaps, both structural (docs/CALIBRATION.md 7):
    - A persistent level that neither the AR(1) z nor the intercept (now learning about one observation per origin, at a discount set when it learned one per country) carries: sovereign defaults -0.0017.
    - A common shock that loads on a place-varying exposure, still counted per place: food 2010-12.
  - Next: a persistent level component and an exposure loading for the frailty. The fresher scale for 2020-type shocks is step 4.

**Step 3c design: a persistent level beside the transient (bnm-56c.20.1, bnm-3be).** t57-frailty left a level that neither state carried: sovereign defaults' filtered z sat at -0.4 to -1.3 for 2003-15. Two options were considered.
- **(a) A local level.** A second state per node and kind, L, a random walk with sd σ_L a period, beside the AR(1) transient z. The rows of origin s see L_s + z_s (z less half its stationary variance, as before; L as it is, since a random walk has no stationary variance and L starts at 0 with variance 0, so with nothing known the rate is still the base's). The pair is filtered jointly, once per origin, with no lookahead (same `:known-at` rule): run forward h periods (z by ρ^h, L's variance up by σ_L² h, their covariance by ρ^h), the sum updated by one Laplace step on the origin's batch (`hier/states-step`), and L and z each moved by its covariance with the sum (the Gaussian projection). The issued prior and the rows' `:shared` effect are the sum's mean and variance, so augur needs no change. σ_L comes from the same prequential grid (`:level-sds`), a non-zero σ_L paying `:level-penalty` nats (2, the transient's price, on top of the transient's own); ties go to σ_L = 0, so a node with no persistent level keeps the transient alone, computed exactly as before.
- **(b) The intercept's discount set for per-origin counting.** augur discounts the coefficients' covariance once a period (C/δ), then learns the period's rows. For the intercept alone with information I a period, the precision runs P_k = δ P_{k-1} + I, so the weight of a new period is I/P_k = (1 - δ)/(1 - δ^k) once the prior's precision is small against I: it does not depend on I. Per-origin counting (I about one observation, bounded by 1/v_s) leaves the intercept's memory where per-country counting (I about n p(1 - p)) put it: a running mean, weight about 1/k after k periods while k ≪ 1/(1 - δ) = 500 years. The equivalent discount for per-origin counting is therefore the same 0.998; what the count changes is only the prior's weight (about one origin against a fifth), which is negligible. A discount is a random walk on the coefficient: its steady gain g = 1 - δ corresponds to σ_L² = R (1 - δ)²/δ with R = 1/I, so δ = 0.998 at R ≈ 1 is σ_L ≈ 0.002 a year. Carrying a 12-year calm needs a gain of 0.1-0.2 a period, σ_L ≈ 0.1-0.25 or δ ≈ 0.8-0.9. As a scale discount that would forget every coefficient of every model at that rate (augur's discount is shared by the whole state), at every node, with one value for all kinds.
- **Decision: (a).** It is (b)'s random walk restricted to the level, chosen per node by its prequential score (σ_L = 0 where it is not needed), and it leaves the coefficients' discount alone. (b) as derived is either a no-op (0.998 is already the per-origin equivalent) or a global forgetting of every slope; doing it for the intercept alone would need a per-coefficient discount in augur. It is not run.
- **Banking's ρ.** Splitting persistence between L and z lets the grid choose a smaller ρ when the persistent part goes to L. It does not by itself stop a calm being carried: L carries it longer, but only by its gain (about g of each origin's departure), so a two-year calm moves L by a fraction of z where ρ = 0.8 carried most of it. The expected effect on banking 2006-10 is small; scored in docs/CALIBRATION.md 8.
- **The price.** At 1 nat (Akaike's price for σ_L alone) the planted world with no shift chose a level for two origins (years 9-10): a walk with no transient (σ = 0, σ_L = 0.2) pays 1 nat where the transient pays 2, and caught the base's early learning of its own level. A second state is priced as the transient is, 2 nats; the null world then keeps σ_L = 0 at every origin.
- **Tests** (lattice_test bnm-56c-20-1-*): over 60 years, a planted level shift of -1.2 log odds from year 20 under transient shocks is carried (σ_L > 0 chosen, the level below -0.5, the late forecasts within half the gap of the transient alone, which mean-reverts and stays above the new rate); a world with no shift keeps σ_L = 0 at every origin and issues exactly what `:frailty` without the level does (forecasts and book equal); `:level-sds [0.0]` changes nothing; the prior adds the level's random-walk variance; bad values are refused.
- **Status (12 November 2026): built, scored, not adopted** (8143b5d; docs/CALIBRATION.md 8). t58-level scores as t57-frailty at the year nodes (+0.00001 [-0.0002, +0.0002]) and still fails 2007-09 against t53-noclim. The level is chosen at most quarter nodes, but never at sovereign defaults' year country node. Where it is chosen at the year scale (banking, sovereign's regions) the lagged random walk carries the 1980s-90s waves into the calm after them: sovereign -0.0005 and banking log -0.008 against t57-frailty. A slow level cannot take a calm that follows a wave within the information lag. That needs a fresher source (step 4) or a switching regime (step 5).

**Step 3d design: the frailty's loading on exposure, a shared random slope (bnm-56c.20.2, bnm-3be).** t57-frailty left food 2010-12 locked (the all-inputs model at 0.96 in 2011). The 2007-08 shock hit places in proportion to their exposure to world prices. A scalar z takes its level once, but its exposure-weighted pattern is a place-varying input, so the exposure group's coefficients learned it once per place, and the averaging's evidence counted it once per place.
- **What u is.** Per node, kind and origin, a loading u_j on each column j of the place's exposure (the spec's `:exposure`; at the political year nodes two columns, trade openness and capital openness). Each column is centred and scaled across the node's places at the origin, which uses only what is known at t. The place's shock is z + Σ_j u_j e_ij. Centring means the slope moves no place's mean against the node's level: z stays the level and u the tilt, which also keeps the two nearly uncorrelated in the filter. Scaling gives u a fixed unit, log odds per sd of exposure, so its AR(1) and its sd mean the same thing across origins.
- **One loading per column, not one on a summary.** Within an origin the exposure group's inputs are (what comes down) × e_ij, column by column. A single loading on a summary (the mean of the standardized columns, say) takes the shock's pattern only along that direction, and leaves the orthogonal combination (trade less capital openness) learned once per place. Food's 2007-08 shock loads on trade openness, which is not the summary. One u per column spans every exposure input's within-origin pattern. It costs no more grid: the columns are standardized, so one σ_u is shared by all of them, at z's persistence ρ. The grid grows by the number of σ_u values, as a summary's would. The state is 3-d instead of 2-d, which is cheap for the Laplace step. Each u_j has the proper prior N(0, σ_u²), so a column one origin does not identify stays at its prior; the step is never singular.
- **Filtered once per origin, no lookahead.** Same rule as z: origin s updates only when its rows are known by t. The joint state [z (L) u_1 … u_J] is run forward h periods (z and u by ρ^h, the level as a random walk), then updated by one Laplace step on the origin's rows, each loading [1 (1) e_i] (augur `hier/states-step` with a vector state). The Newton step is taken in the form (I + V W) δ = V g − (s − m), which needs no inverse of V, so a dimension of variance 0 (the level at its start, z at σ = 0) stays put.
- **Mean one at every exposure.** The place's shift is z + u·e less half its stationary variance, ½(σ² + σ_u² |e_i|²). That half variance differs by place, so it goes into the row's own offset (and its models' issued predictives), not the shared mean.
- **Prior and price.** σ_u comes from the prequential grid (`:exposure-sds`), each pair scored by the batch's marginal under the prior it issued. A non-zero σ_u pays `:exposure-penalty` nats (2, a second state's price, as the level's). Ties go to σ_u = 0, and the grid lists σ_u = 0 candidates first, in the order they had without the option. A node whose shocks do not load on exposure therefore keeps σ_u = 0 and issues exactly what `:frailty` alone does. Under `:prior :parent`, z comes from the coarser level, uncorrelated with the node's own u and level.
- **Rows learned jointly with (z, u).** Each resolved row carries `:shared [s μ_s P_s]` (the joint prior issued) and its `:load` [1 (1) e_i]. augur's `step` (v0.23.0) augments the coefficients with the vector, learns the origin's rows, and integrates it out. An input that varies within the origin only through the exposure (affine in e_i) is confounded with u. From one origin it learns at most about one observation of variance σ_u² per unit of the exposures' spread: n var(e) / (1/(p(1-p)) + n σ_u²) in the Gaussian limit, which saturates as places are added. The averaging's evidence is the batch's Laplace marginal with the vector integrated.
- **What the slope alone did not fix: the coefficients' uncertainty counted once per row.** In the planted world the weights still locked. The traced cause is separate from the shock. At the exposure input's first reading its coefficient is at its prior variance (about 1), and the standardized input runs to 3-4 sd, so q_i reaches 15 at the extreme places. augur's evidence scored each row against the state independently, so each row integrated that one coefficient on its own. A model unsure of an input that varies across a period's 150 rows was then paid (or rewarded) for its uncertainty 150 times: 2 to 8 nats in one origin, and with the forgetting at 0.995 those nats stay. With the coefficients' variances set to 0 the gap was 0.07 nats. This is the same class of bug as bnm-3be: something common to an origin (here the coefficient's uncertainty) counted once per place. The fix is augur's `:evidence-at :sequential` (v0.23.0). A period's rows are scored at learn time each given the period's rows before it, shared effects augmented: the chain rule's log marginal, the evidence a linear model already gets, and the normalizer of the update `step` performs. It changes the averaging wherever many rows share a period, not just under the frailty, so it is a separate lattice option (`:evidence-at :sequential`), off by default (the ledger's issue scores rows independently).
- **Tests** (lattice_test bnm-56c-20-2-*):
  - a planted world of places with exposure spread over [0, 2], a common shock and a loading on exposure (both AR(1) at 0.6).
  - With the scalar frailty alone, the exposure coefficient's variance falls 4.5 times from 30 places to 150 (Gaussian reference, per place: 4.7). With σ_u fixed at 0.5 it falls 1.56 times (reference, once per origin: 1.52), and at 150 places it is 6 times the scalar's. With the grid the first three origins choose σ_u = 0 and teach the coefficient per place (ratio 2.0).
  - σ_u = 0.5, the grid's nearest to the planted 0.58 per sd of exposure, is chosen at every origin that chooses a slope.
  - The filtered u tracks the planted loading (correlation 0.82), and σ_u > 0 is chosen.
  - Base against exposure model alone (so all that separates them within an origin is affine in e): per origin, the evidence gap grows with places under the scalar frailty (mean |gap| 1.5 nats at 30 places, 3.4 at 150, weights locked at 1.00). With the slope alone, the first reading still moves them 7.7 nats at 150 places (0.5 at 30). With the slope and `:evidence-at :sequential` the gap is 0.5 at 150 places (1.1 at 30), the largest 2.2. The top weight reaches 0.99 over some fifteen origins, by what each origin's bounded evidence says: the weight's log odds moves at most 2.2 nats an origin (9.7 under the scalar frailty).
  - A world with no loading keeps σ_u = 0 at every origin and issues exactly what `:frailty {}` does (forecasts and book).
  - `:exposure-sds [0.0]` changes nothing.
  - The prior is z + u·e less ½(σ² + σ_u²|e|²), its variance a P a'.
  - The parent prior runs.
  - Bad values are refused, and a slope without the spec's `:exposure` is refused.
  - augur: the d = 1 vector state is the scalar one to 1e-9; the 2-d Laplace marginal matches a quadrature; a dimension of variance 0 stays at its mean; a planted (z, u) is filtered; a time-common slope on an exposure input saturates with rows; sequential evidence of one row is the log score, counts a coefficient's uncertainty once, and stops one period of 200 rows locking the weights.
- **The planted numbers above are from augur v0.23.1 (bnm-2sf).** v0.23.0's vector Laplace marginal took clamped Newton steps that could cycle short of the mode. In the planted world it overstated the σ_u = 1 candidates' marginal by 11 to 25 nats an origin (quadrature: -98.57; v0.23.1 -98.58; v0.23.0 -87.48), so the filter chose σ_u = 1 at 22 origins where the converged scores prefer 0.5 by 50 nats. That wider slope prior is what made the variance look saturated at 30 places (ratio 1.37) and kept the weights at 0.78; the 26-nat origin was the same marginal in the evidence. The coefficients' learning (augur's row-by-row update with the vector augmented) matches a grid integration of one origin to a few percent.
- **Status (13 November 2026): built, scored, not adopted** (9b81890, augur v0.23.0; docs/CALIBRATION.md 9). The slope alone (t59-slope) scores -0.0006 [-0.0013, +0.0001] against t57-frailty pooled. It leaves food 2010-12 as it was: food's year history starts with the 2007-08 shock, filtered at σ_u = 0 before the slope had earned its price. With `:evidence-at :sequential` (t59-exposure) food 2010-12 improves (0.1158 → 0.0979), but market crashes lock on `:down` in 2003-07 (-0.064, -0.140). The sequential rule rewards in-period learning of a regional shock the node does not model, so a regional shared state comes first.

**Step 3e design: the σ grid past 1 (bnm-02u).** Written 13 November 2026, before any world run of it. In t66-fix, t69-pool and the base of record t71-stack every year node's frailty chooses σ = 1.0, the top of the default `:sds` [0, 0.25, 0.5, 1] (docs/CALIBRATION.md 16 loss 4, 17 loss 4). A choice at the edge of a grid says the score was still rising there, so the grid truncates it. The remedy is a grid whose top is not chosen.
- **What size the episodes need.** σ is the stationary sd of a year node's common log-odds shift. The departures the catalogue shows at the year country nodes, from the kind's calm rate to the episode's: food 2007-08 about +2.6 (0.10 to 0.6), market crashes 2006-07 about +4 (0.18 to 0.9 and above), growth 2019 about +2.4 (0.10 to 0.55), growth 2007-08 about +0.9. At σ = 1 these are 2.4-4 sd draws of a Gaussian AR(1), so each one that resolves pays a candidate with a larger σ by several nats. At σ = 2 the largest is a 2 sd draw. So the scale the episodes ask for is about 2, and 3 is the guard that keeps 2 from being an edge in its turn. Past 3 the stationary prior is no longer a shock on a rate: its median multiplier is e^{-4.5} and its 95% band spans more than five logits either way.
- **The spacing.** Below 1 the grid is geometric, ratio 2. A year node filters 15 (food) to 150 (the long catalogues) origins by 2022, so the score resolves log σ to about 1/√(2n), 0.06-0.18. Steps of ratio 2 (0.69 in log σ) are 4-12 of those apart, coarse where the choice now falls; ratios of 1.33-1.5 (0.29-0.41) are 2-7. A finer grid adds candidates whose scores are almost the same as their neighbours', and little information, at the thinly filtered nodes where the choice matters most. So the grid goes on to 1.5, 2, 3: `:frailty {:sds :wide}`, [0, 0.25, 0.5, 1, 1.5, 2, 3]. The default is unchanged.
- **The price stays 2 nats for every non-zero σ.** The price is Akaike's charge for the two parameters (ρ, σ) a non-zero state adds. The grid's resolution adds no parameter. A price rising with σ would be a prior on σ, chosen to hold it down, with no value from first principles, and it would also change the choice among the old candidates. With a flat price, the decision between σ = 0 and a non-zero state differs from the default grid only through the maximum over more non-zero candidates: 28 with ρ instead of 16. The new ones' scores move with σ = 1's, so the added optimism is small, but it is not zero.
- **The half variance at large σ.** The shift applied is the state less σ²/2, so the expected odds multiplier is one: exact for rare events, where odds are rates. In probability it is not one. Before anything is filtered, a base p issues E[expit(logit p − σ²/2 + σZ)]:

  | base p | σ = 0.5 | 1 | 1.5 | 2 | 3 |
  |---|---|---|---|---|---|
  | 0.02 | 0.0199 | 0.0194 | 0.0181 | 0.0156 | 0.0088 |
  | 0.05 | 0.0494 | 0.0468 | 0.0415 | 0.0337 | 0.0172 |
  | 0.12 | 0.1167 | 0.1060 | 0.0885 | 0.0680 | 0.0321 |
  | 0.20 | 0.1920 | 0.1690 | 0.1365 | 0.1020 | 0.0464 |
  | 0.40 | 0.3772 | 0.3203 | 0.2501 | 0.1820 | 0.0801 |

  It is the model's prior, not a bug, and the selection charges for it: each candidate is scored on its origins under the prior it would have issued, so a σ whose stationary prior issues too low pays. Once origins are filtered the state's mean carries the level, because the filter sees the state less the same half variance. But a large σ at a node of a common kind issues low in a calm stretch, where its state drifts back to its stationary prior, and that can be part of why a large σ scores: it is a level effect as well as a shock size. The runs must show the calm years (market crashes 2010-12, food 2010-12, the carried calms of docs/CALIBRATION.md) beside the episodes. A shift that has mean one in probability would be place-dependent and would change the default at every σ, so it is not made here.
- **The integrals at large σ.** Two approximations were built for σ ≤ 1, and both were checked against a 4000-step trapezoid:
  - The forecast integrated over the shock by five Hermite points errs by under 2e-4 relative at σ ≤ 1. At σ = 2 it errs by 1.2%, and at σ = 3 by up to 10% (base log odds −2: 0.0287 against 0.0320). Twenty points err by under 0.1% at σ = 3.
  - A candidate's score, the origin's log marginal with the shock integrated by Laplace (augur `hier/shared-marginal`), errs by under 0.01 nats at σ ≤ 1. At σ = 3 it errs by up to 0.07 nats an origin on one row or a quiet batch, and on quiet origins it errs toward the larger σ (+0.067 nats on 20 quiet rows). Over 20 origins that is about a nat, half the price.

  So a state with σ above 1 is integrated by twenty Hermite points (`hermite-fine`), and its candidate's score is the marginal by twenty points about Laplace's mode and curvature (`shared-ll`, adaptive Gauss-Hermite; a vector candidate's rows all load [1 g_t] within an origin, so its marginal is one-dimensional in the shift). At σ ≤ 1 both are computed exactly as before, so the ledger's issue and the base of record reproduce, and the wide grid changes a node only where a candidate above 1 is in play. The averaging's evidence in augur keeps Laplace with the chosen state's shared effect. Its error is about the same for every model under the same shock, so it moves the weights less than the selection; it is recorded, not changed.
- **Tests** (lattice_test bnm-02u-*): `:sds :wide` is the grid above, every non-zero σ at 2 nats, the default unchanged and another keyword refused; the twenty-point rule's weights, second and fourth moments; the five-point rule is `hermite-rule 5`; the forecast at σ = 3 within 0.2% of the trapezoid from base log odds −8 to 2, and at σ ≤ 1 the five-point rule as before; the score at σ = 3 within 0.002 nats of the trapezoid on one row, quiet and busy batches and rows with q > 0, and Laplace's value at σ ≤ 1; a planted shock of stationary sd 3 chooses 1.0 on the default grid and above 1 on the wide one, and a world with no shock chooses 0.
- **Status (13 November 2026): built, scored, not adopted** (941f650; docs/CALIBRATION.md 18). t74-sigma moves the year nodes to 1.5-3: growth collapses and food sit at 3, the new top. The larger σ is chosen as a calm-level knob through the half variance, as the table above warned. Where ρ = 0 the issued prior is the stationary N(−σ²/2, σ²) whatever was filtered. Calm years and market crashes' alerts gain; the 2006-07 crash and food 2020-22 lose. Pooled Brier +0.0006 [-0.0015, +0.0023], log 2020-22 -0.022 [-0.035, -0.007]. The mean-one rule (bnm-0fe) comes before the grid can be judged.

**Step 4: time-tree coherence (audit Round 3, issues 9, 13).**
- `compose/survival-chain` and `cumhaz-split` replace `tools/bnm/reconcile.clj`. Reconciling against the quarters' own part, net of the downscale, gives the year weight w exactly (9a). Time-varying hazard alignment (9b).
- World runs reconcile, recorded (9c). The at-risk filter reads busy only to t, and both populations are reported (13).
- The year's λ is built from its quarters' λ (§6.1) as a nested group.

**Step 4a design: the frailty from a fresh quarter scale (bnm-56c.35).** Steps 3b-3d left one obstacle for the outlier episodes (food 2007-08 and 2020-22, the 2008 crashes, the 2020 collapses): the year node's z is filtered from its own rows, and those are known 24 months plus the catalogue's lag after the origin, 12 to 36 months after the episode began (docs/CALIBRATION.md 7-9). The existing quarter scales are no fresher: quarter-lead's window is t+3 to t+12, known 12 months plus the lag after t.
- **The fresh scale.** A scale may be `:frailty-only`. Its nodes run as any other (rows, the base model, the climatology, the frailty filter), but they issue nothing: no forecast, book entry, weights, errors or flow standardizer. It is not in the sheaf's base (`bnm.sheaf/base`), so no other node's layout, up or down reads or contradictions see it. Every input group is left out: the node reads the intercept alone, and its offset is its climatology. Its base model is therefore the climatology plus a learned intercept, the same reference the year's z is filtered against. The world run's scale is `bnm.lattice-political/quarter-now`: window (t, t+3], known 3 months plus the lag after it. That is 6 months for market crashes, coups, protest and violence surges (lag 3), and 12 months for food under `--food-outcomes monthly` (lag 9).
- **Which kinds.** `--fresh-frailty quarter-now` gives the scale to every kind whose catalogue dates onsets within the year: at most 45% of its onsets in any one month of the year. An annual catalogue puts all of them in January, so its quarter windows would only see a seasonal pulse, and they resolve no sooner than the year's. Today the rule picks coups, market crashes, protest surges, violence surges and food. It leaves out banking crises (90% January: JST's years) and conflict onsets (47%: COW's years before UCDP), and the annual kinds. Growth collapses would need a monthly catalogue, from industrial production (a follow-up). A kind without the scale keeps its own prior, and the run says so.
- **The prior.** Under `:frailty {:prior :finer :source :quarter-now}`, every node at a scale coarser than the source takes its prior from the source scale's node at the same level. That covers quarter-lead, quarter-w2, the year and the half-decade. The candidates are the node's own grid (κ = 0, computed exactly as under `:prior :own`) plus, for each κ > 0 in `:cross` and each σ, a candidate whose prior is
  - m = κ m_src − S/2 and v = κ² v_src + (S − κ² σ_src²), with S = max(σ², κ² σ_src²);
  - here (m_src, v_src) is the source's chosen candidate run forward to t at the source's own period (3 months, not the node's 12), and σ_src its stationary sd.
  - So the node's shock is κ times the source's plus a part of its own, with stationary variance S. S/2 keeps the expected rate the base's (mean one, as in 3b).
  - A κ > 0 candidate pays `:penalty` (2 nats) even at σ = 0, since it carries a state. Its own persistence is unused (ρ = 0).
  - κ is chosen by the same prequential score (`:cross`, default [0 0.5 1 1.5]), so a node where the source does not help keeps κ = 0 and its own prior. `:cross [0.0]` is `:prior :own` byte for byte.
- **Why a coefficient chosen online, not a derived map.** The year's z is a log-odds shift of P(an onset within 24 months). The source's is a shift of P(an onset within 3 months), filtered from a quarter that the year window outlasts by seven quarters. For rare events a shock held through the window shifts both by the same log rate (κ = 1). A shock lasting a fraction f of the window shifts the year's by log(1 + f(e^z − 1)), less than z. For common events (market crashes run at 0.18-0.96 a country year) the year's probability saturates. The filtered source state is also shrunk toward 0, which pushes κ above 1. No single map is right across kinds and levels, and κ is one number the batch marginal identifies, so it is learned as ρ and σ are. 0 is in the grid so it switches off where useless, and 1.5 allows for the shrinkage.
- **Why not add the node's own run-forward state.** At the year scale the own state is 12-36 months stale. The year grid already chose ρ = 0 at market crashes and food in t57-frailty, so their own state carried nothing forward. Adding the two would also count the persistence twice: the own state is filtered against the base predictive, so it takes the whole departure, including what the source explained. The own filter keeps running (it scores and updates as before), so κ = 0 still has it.
- **No lookahead.** The scales run finest first within a month (`:scales` order), and the source is due at every month a coarser node is (3 divides 12). By the time a year node issues at t, the source has learned only rows with `:known-at` ≤ t. Its state is then run forward from its last filtered origin to t. A planted test redraws every event after a cut and checks that every forecast and book entry issued by the cut is unchanged.
- **Food's year history.** It starts in 2007 because FAOSTAT's consumer price indices start in 2000. Food inflation starts in 2001; the rule needs three years of past, so the catalogue covers places from January 2004. The first year window then closes in December 2005, known 18 months later, and the uninformed-start rule (3b) leaves the origins issued before that unfiltered. The first filtered origin is 2007. Monthly outcomes cover the same span (also from 2004) but are known 9 months sooner, so the first filtered origin is 2006 and there is one more origin of history. No series on disk reaches further back: no per-country food CPI before 2000 (the BIS's monthly CPI is general and covers few countries). This is a data limit, not a bug.
- **Tests** (lattice_test bnm-56c-35-*, `fresh-world`): 120 places with events by month, four planted episodes of +2 log odds for eight quarters. The year window is t to t+12, known at t+24; the quarter-now window is t to t+3, known at t+6.
  - At 467, the first year origin six months into the last episode, the year issues z = 1.10 with `:finer` and −0.125 with its own prior. Mean p is 0.34 against 0.13, at a realized rate of 0.55. κ = 1 is chosen.
  - The frailty-only scale issues nothing.
  - With the scale present, and with `:cross [0.0]`, every year forecast and book entry is unchanged.
  - `:sds [0.0]` issues what no frailty does.
  - There is no lookahead.
  - The source runs forward at its own period (the bug the scoping flagged, avoided).
  - Bad values are refused: no source, a source that is not finer than another scale, a source without `:finer`, negative or empty `:cross`, `:finer` with a level or slope, and a frailty-only scale without `:frailty`.
  - The planted world also shows the price of switching. The year origin whose window caught the onset was issued at the calm the source still read. When its rows resolve, κ > 0 loses about as much as it gained, and the year went back to κ = 0 one origin later. An episode longer than the year lag earns κ back; a short one may not.
- **Status (13 November 2026): built, scored, not adopted** (63d9bda; docs/CALIBRATION.md 10). t60-fresh scores as t60-monthly pooled (+0.0000 [-0.0003, +0.0003]) and loses food 2020-22 (-0.018): under monthly outcomes food's episode is 2022, inside the CPI's 12-month window plus lag, so the source still read the calm when the year windows that caught it were issued. The 2008 crashes ran their course inside the 6-month market lag, and growth collapses have no sub-annual catalogue. The outlier episodes need information that leads onsets (step 5's regime on gauges read at t), not fresher outcomes.

**Step 5: regime filter and gated encompassing (bnm-56c.2).**
- `src/bnm/regime_filter.clj`. Augur `:weighting {:rule :gated :regimes P :pool-siblings κ}`. Drivers: world state (step 2) first, buffers when step 6 lands. The world gauges (food12, brent12 and the unwired VIX, credit, dollar, policy rate) enter here with confidence weights, not a hard gate: their weight at a kind grows with the posterior evidence for their lead and shrinks to zero under noise, so step 2's harness (noise, planted, false move, short burst) applies to the weights.
- Acceptance as registered in the bead:
  - the noise rule pooled and per regime;
  - a planted quiet-until-depleted signal;
  - a planted false regime switch;
  - scores weighted by the regime known at the origin;
  - market crashes (the real state-dependent case) as the main target.

**Step 5a design: the level from world gauges, the ranking from places (bnm-v1y).** Written 13 November 2026, before any world run of it. docs/CALIBRATION.md 15 found that a world gauge, the same at every country, can move every country's level but cannot rank them. The averaging picks one model; it does not take the level of one and the ranking of another. So the gauges reach the forecast only through the coefficients of whichever model holds the weight, and that model is chosen for ranking: at food the all-inputs model, whose gauge coefficients learned from one episode (2008, the counterexample) and fell toward 0; at market crashes `:down` and fiscal in most years, which read no gauge at all. §12 adds that a common shock is one observation per origin, so the gauge-to-level map needs a prior from theory (LEADING.md "Priors"). §15 adds that t65's 2006-08 crash catch rested on a few nats the leading model earned in the 1980s from a defective standardizer, through the weight lock. Two designs were weighed.
- **(A) The gauges set the frailty's mean.** At a node, the rows of origin t see z_t + β·g_t. g_t holds the world gauges as read at t (the same at every place), z_t is the transient shock as in 3b, and β is a static state with the theory prior N(β0, 0.25²) per gauge. [z β] is filtered jointly once per origin, as the slope of 3d is: a vector state, each row loading [1 g_t], with augur's `:shared`/`:load`, scored under `:evidence-at :issue-joint`. The named gauges leave every model's inputs. A group left with no input is dormant and drops out (weight 0, as any group whose source ends), so the averaging chooses among models that rank. Every model issues with the same shared effect: the level is the same whichever model holds the weight, and the evidence between models no longer depends on it.
- **(B) A log-linear pool of a level model and a ranking model.** The leading model's place-constant part is added to the ranking model's linear predictor, with pool weights learned by the joint issued score.
- **Why (A).**
  1. In (B) the pool weights are learned from the same few episodes as the model weights. Whether the level is added is then decided by how many nats it earned in 2006-08 (or, as §15 traced, in 1983-88): the weight lock moves from the models to the pool. In (A) the level is not a choice the averaging makes. It enters by construction, β moves from its theory prior once per origin, and an origin informs β by at most about g_t²/σ², whatever the number of places.
  2. (B) counts the level twice. Both models carry an intercept, and the ranking model's coefficients have already learned part of the common shock through its intercept and region-common inputs. Separating "the level model's place-constant part" from the ranking model's needs a centring rule per origin that the issued predictive does not define. In (A) the level is one term, beside the transient that 3b already filters against the base. The base, with `:clim-learn :current`, holds the climatology.
  3. (A) reuses tested machinery (3d's vector state, the joint Laplace step, augur v0.24.0's joint evidence with loads) and needs no augur release. (B) needs a new pooled predictive and its joint score in augur.
  4. (A) has its own risk: if z has no variance of its own (σ = 0), the shared effect is β·g_t with nothing else common to the origin, and β learns once per place, which is §12's bug again. So at a node with gauges the σ = 0 candidates are left out: the gauges are a mean for a shock, not a replacement for it. The grid still chooses ρ and σ > 0.
- **Mean one.** The transient's half variance is taken as before; β·g is not corrected. It is an effect with a theory prior, not a shock of mean one, and its uncertainty widens the issued predictive through the Hermite rule, as any shared variance does.
- **Where.** `:frailty {:gauges {scale [names]}}`, names from the scale's `:gauge-names`, each needing a `:named-prior` (the theory prior is required: §12). In the world run, `--frailty-gauges true` puts the world gauges of `--leading` at the year node: market crashes and growth collapses `[:baa12 :us-curve :vix12]`, food `[:world-food12]`. `:food12-x-imports` varies by country, so it ranks and stays a model input. The quarter-lead keeps its gauges as model inputs, so the option changes one scale, the scored one. Only `:prior :own`, with no level and no slope.
- **Tests first** (lattice_test bnm-v1y-*, a planted world of 150 places): a common level driven by a quiet world gauge with four episodes, a transient the gauge does not see, a place input that ranks, and noise inputs. The combination beats the current averaging and each single model (world, place, all-inputs) on the late log score. β's variance at 30 places is within 1.5 times that at 150 (once per origin). The issued shock at the first origin is β0·g less the half variance. With no gauges the option changes nothing, and bad values are refused.
- **Runs.** t69-pool = t65-gauges' command + `--real-lags true --lead-std history --frailty-gauges true`. t69-pool-nolead is the same without `--lead-std history`. Bench: t65-gauges and t66-fix.
- **Added after the planted test, before any world run: `:gauges-from :all`.** Where the gauges are, [z β] is filtered against the all-inputs model's issued predictive, not the base's. Against the base, which omits what ranks the places, a common shift is attenuated on the average log odds (the logistic's omitted-heterogeneity bias). In the planted world β learned 0.56 against 0.8; against the ranking model it learned 0.69-0.70. The all-inputs model reads no weight, so C7 holds. The planted test's main comparison drops the all-inputs model (base, world, place), so that the averaging has to choose between level and ranking. With it, the all-inputs model is the right model in that world, and the combination ties it (-0.479 against -0.479).
- **Status (13 November 2026): built, scored, not adopted** (40e872a, 32a9867, 819ef67; docs/CALIBRATION.md 16).
  - t69-pool loses no fold against t65-gauges, t66-fix or t53-noclim. It gains pooled against t66-fix (+0.0017 [+0.0001, +0.0038]) and on the log score against t65-gauges.
  - It reaches market crashes 2006-07 under the history standardizer (Dec 2007: 0.542) and catches food 2021 (0.227).
  - Its cost is false alarms from VIX12 after crashes and from the 2022 curve.

**Step 6: buffers and hazard into the node (audit Round 5a; PLAN phase 1).**
- First fix the hazard folds (Test 8). Each country node's `:stocks`/`:buffers` come from bnm.buffers records, and `hazard/predict` is a nested group (Test 5a), tested against world prices first, since drawdowns carried nothing for food.
- The world node's stocks are the C1-checked sums. The scanner reads the coherence residuals as hidden buffers (PLAN 1.7).

**Step 7: events into stocks; engine closed (audit Round 5b; issues 6, 16; bnm-4hv.4/.5/.8/.18).**
- An event → stock edge module, dated at known-at (Test 6a): default → debt service stops; food shock → reserves draw; IMF drawing → refill; conflict → capacity cut.
- SFC with deposits ≥ 0 and the debt-service loop closed (Test 16, C2).
- An engine node adapter: `tools/bnm/world_model.clj` runs the engine at the year scale for countries with data, its W module reading the world node. Engine states become a nested group (E10). Run the event test bnm-4hv.8.

**Step 8: provinces and the week (audit Round 6; PLAN phase 2; issues 14, 15, 17).**
- Province nodes for protest/violence/conflict (GDELT, UCDP GED), with adjacency from `cells/neighbours` as the BYM2 prior on province z.
- The weekly Hawkes model is the week-scale node. The country total comes from provinces, reconciled (14a). Its baseline × e^{z_year} (14b). Spectral projection after the M-step (17, C14).
- The weekly model's 2000–26 numbers are labelled development (15).
- Test against "country p × historical share" and the weekly model's ranking.

**Step 9: the price network (PLAN 5b).**
- Product × region nodes under the world node. Leontief pass-through with stage lags as sibling edges, EXIOBASE yearly tables published at t, and discounted online updating.
- The world price becomes emergent: the network's level against the measured price is a `:gaps` entry.
- Margins feed `:buffers` (carry, claim gap).
- Registered sector-price test as in PLAN.

**Step 10: shock hindcasts and the remaining scales (PLAN 5, 4, 6).**
- Tohoku, Suez and the Black Sea, with parameters fixed beforehand, through the step 7/9 machinery. Severity given onset by CRPS.
- Month nodes; decade nodes driven by cohort demography; the century as an era prior.
- One run (`tools/bnm/world_model.clj`) issues the quarterly ledger.

**Order and parallelism:**
- Steps 0 → 1 → 2 is the critical path, with Round 4 inside step 2.
- Step 3 needs augur.
- Steps 4 and 5 can run in parallel after 3.
- Step 6 can start its fold fix during step 2.
- Steps 7, 8 and 9 follow and are independent of one another.
- Step 10 comes last.

**What stops** (from PLAN §5, kept):
- main-effect links between kinds;
- quarter-level onset timing from prices;
- cells as the inference unit;
- the principal contradiction as an input;
- early-warning statistics and long-cycle phase.

## Critical files
- /Users/yogthos/src/bnm-structure/src/bnm/lattice.clj (the `step` book entry at :659–671, the `inputs` at :304–430, the downscale at :589–608)
- /Users/yogthos/src/bnm-structure/src/bnm/sheaf.clj (`:self` dir, issued book kept apart)
- /Users/yogthos/src/bnm-structure/tools/bnm/lattice_political.clj (world node :190, :246–250; year groups :72–78)
- /Users/yogthos/src/bnm-structure/src/bnm/buffers.clj and /Users/yogthos/src/bnm-structure/src/bnm/engine.clj (stocks, regime switch)
- /Users/yogthos/src/augur-loops/src/augur/dynamic.clj and /Users/yogthos/src/augur-loops/src/augur/hier.clj (issue-time evidence, gated weighting, online frailty)