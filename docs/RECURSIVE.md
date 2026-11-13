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

**Base of record (12 November 2026): t53-noclim** (second note to amendment L1, docs/PHASE-CHANGES.md): t52's command with `--known r7-at-fx` and `:flow-std-from :resolved` on the data of bnm-56c.31 and .32. Steps bench against it. Against t52 pooled −0.0009 [−0.0036, +0.0018], no fold lost; market crashes 2007–09 −0.127, one origin, a level shift (docs/LOOP-AUDIT.md 6).

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

**Step 4: time-tree coherence (audit Round 3, issues 9, 13).**
- `compose/survival-chain` and `cumhaz-split` replace `tools/bnm/reconcile.clj`. Reconciling against the quarters' own part, net of the downscale, gives the year weight w exactly (9a). Time-varying hazard alignment (9b).
- World runs reconcile, recorded (9c). The at-risk filter reads busy only to t, and both populations are reported (13).
- The year's λ is built from its quarters' λ (§6.1) as a nested group.

**Step 5: regime filter and gated encompassing (bnm-56c.2).**
- `src/bnm/regime_filter.clj`. Augur `:weighting {:rule :gated :regimes P :pool-siblings κ}`. Drivers: world state (step 2) first, buffers when step 6 lands. The world gauges (food12, brent12 and the unwired VIX, credit, dollar, policy rate) enter here with confidence weights, not a hard gate: their weight at a kind grows with the posterior evidence for their lead and shrinks to zero under noise, so step 2's harness (noise, planted, false move, short burst) applies to the weights.
- Acceptance as registered in the bead:
  - the noise rule pooled and per regime;
  - a planted quiet-until-depleted signal;
  - a planted false regime switch;
  - scores weighted by the regime known at the origin;
  - market crashes (the real state-dependent case) as the main target.

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