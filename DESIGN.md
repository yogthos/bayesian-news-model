# bayesian-news-model: design exploration

Status: exploration / proposal (2026-10-15). Nothing here is implemented yet.

Goal: a Jolt library that takes news an LLM has parsed into structured events
and models (1) what is happening, (2) how events connect and drive each other,
and (3) how they are likely to evolve, with calibrated probabilities that can be
scored against what actually happens.

---

## 1. What we found in the existing code

### worldmonitor-bayesian (TypeScript)

Good at ingestion, weak at modelling. The "Bayesian" part is mostly cosmetic:

- `bayesianUpdate(prior, lik, ev) = lik*prior/ev` (`src/services/black-swan-analyzer.ts:205`)
  with a constant evidence term (never `Σ P(E|H)P(H)`, so unnormalised), called
  once with literals (`:240`). All other signal "probabilities" are constants.
- The correlation matrix is `Math.random()` (`:444`). The "martingale" is a capped
  decayed sum. "PageRank" runs over a graph where every node links to every other
  node, because every signal gets `timestamp: now`.
- Classification is headline keyword matching. No LLM, no actors, no event
  propositions. Hypotheses are fixed category buckets ("military", "economic").
- Syndicated wire copies are counted as independent evidence.

**Worth carrying over (as ideas):**
- Source tier/type metadata, plus the *convergence* / *triangulation* intuition:
  agreement across independent source types is stronger than raw volume.
- Rolling-baseline z-scores of topic volume (anomaly features).
- Cross-modal divergence signals: prediction markets move before news, or prices
  move without news.
- Prediction-market prices as external priors/anchors. Note that their unit bug
  treats a 0-100 price as 0-1.

### newsroom (Jolt)

A mature pipeline that already does most of the "LLM parses news" half:

| Need | Already in newsroom |
|---|---|
| LLM client, providers, retries | `newsroom.llm.client/chat` (`client.clj:94`), `providers/role-llm` |
| Response cache | `llm.cache/cached` (SHA-256 of the request, SQLite) |
| JSON from prompts | `analysis/json-object` plus enum-vector validation (`one-of`, `importance-of`) |
| Story clustering | embeddings plus `grouping-prompt` (`analysis.clj:125`) |
| Per-story structure | `dossier-prompt`: actors, facts, disputes, expectations |
| **Causal skeleton** | `connection-prompt` / `parse-connections` (`analysis.clj:777,870`): links with effect, channel, mechanism, lag, confidence; risks with likelihood |
| **Outcome labels** | `projections` table: claims judged held/partly/failed, with closeness |
| Source reliability | `retrospective/standings`: an ad hoc pseudo-count of 0.5×2 that should become a Beta-Binomial |

Conventions to follow:
- A pure core separate from an IO adapter.
- The chat fn has the signature `(fn [llm req] -> {:content ..})`, so tests stub
  it and the cache composes around it.
- ebb (missionary) for async, `clojure.test` + writ for tests, SQLite through
  `jolt.db`.

**Integration gap:** newsroom has no analysis plugin hook. Follow-up steps are a
closed `case` (`pipeline.clj:1383`). We need either a small core PR
(`defanalysis` multimethod) or a plugin watching `pipeline/stored`.

### Jolt constraints checked

- `Math/exp`, `log`, `sqrt`, `pow` and `rand` work. Speed: ~1M `log` calls in about 120 ms.
  That is enough for particle filters and Gibbs at news scale.
- `Math/lgamma` is missing, so we write Lanczos `log-gamma` / `log-beta` ourselves.
- We need a seedable PRNG of our own (e.g. splitmix64 / xoshiro) for reproducible
  sampling and tests.

---

## 2. What the literature says (condensed)

Full notes and links are in section 8. The design follows from these points:

1. **Use PLOVER, not CAMEO, as the LLM's ontology.**
   - PLOVER (the POLECAT successor to ICEWS) has about 16 event types plus an
     explicit **mode**: did / threatened / planned / stopped / negated.
   - Mode is exactly the threat-vs-action distinction a forecasting model needs.
2. **LLM coders are noisy annotators, not ground truth.**
   - Their output is sensitive to prompt, label order and codebook wording.
     He et al. 2026 found that a better codebook alone does not fix this.
   - So we estimate the coder's confusion matrix (Dawid-Skene) and feed the
     model *soft* labels.
3. **Report counts measure media attention, not events.**
   - GDELT audits found only about 21% of "protests" were real.
   - Failure modes: wire echo, re-reports of old events, and media-fatigue decay.
   - Defences: dedupe to canonical events, count *independent* sources (Dong et al.
     copy detection), and model a separate reporting-intensity process.
4. **Self-exciting processes model "events trigger events".**
   - Multivariate Hawkes over (dyad × event type) gives an excitation matrix
     α[i→j]. That matrix *is* the "how events connect" graph.
   - The discrete-time daily-bin variant is fitted by EM with conjugate Gamma
     priors.
5. **Latent escalation phases (HMM/DBN)** are interpretable and cheap, and they
   absorb regime breaks that stationary Hawkes cannot.
6. **Rule mining (TLogic)** beats embedding TKG models on ICEWS and is
   explainable. Rules like "A threatens B ⇒ B mobilizes within k days, conf c"
   are Beta-Binomial counts.
7. **LLMs are good at eliciting priors and structure, worse at final numbers.**
   - LLM-elicited CPTs make reasonable BN priors that data then refines.
   - Pure LLM forecasts still trail superforecasters and Metaculus Pros.
   - The best results pool model + LLM + crowd/market in **log-odds space**,
     weighted by track record, then recalibrate.
8. **Baselines are hard to beat.** "No change" and "inertia" win often (ViEWS,
   TKG work). Report against them, and output distributions, not point scores.

---

## 3. Architecture

```
 articles (newsroom gather/dedupe/group)
        │
        ▼
 [L0] extract     LLM → PLOVER event records (JSON, enum-validated)
        │
        ▼
 [L1] resolve     canonical events: event-level dedup, source independence,
        │         retrospective/old-news filtering
        ▼
 [L2] observe     coder confusion + source reliability → soft evidence
        │
        ├──────────────► [L3a] rates      discrete-time Hawkes / Gamma-Poisson
        │                                 per (dyad, type); excitation graph α
        ├──────────────► [L3b] phases     per-dyad escalation HMM / particle filter
        │
        ▼
 [L4] questions   propositions ("X strikes Y by date") as nodes in a sparse
        │         noisy-OR network; parents = L3 states/intensities,
        │         edge priors elicited by LLM (newsroom connection-prompt),
        │         refined by mined rules
        ▼
 [L5] forecast    log-odds pooling (model ⊕ LLM ⊕ market ⊕ base rate),
                  recalibration, scoring against resolved questions
```

Every layer is a pure function over EDN data. Time advances by an explicit
`step` over daily bins. The model state is a value that can be serialised,
diffed and replayed.

### 3.1 Data shapes (EDN)

```clojure
;; L0 output: one per (article, event mentioned)
{:event/id       "e-2026-10-14-0193"
 :article/id     "…url hash…"
 :source         {:outlet "reuters.com" :tier 1 :type :wire}
 :published      #inst "2026-10-14T08:12Z"
 :occurred       {:date "2026-10-13" :precision :day}   ; or :week / :unknown
 :retrospective? false                                  ; re-report of an old event?
 :type           :assault          ; PLOVER: protest, assault, coerce, threaten, mobilize, ...
 :mode           :did              ; :did :threatened :planned :stopped :negated
 :actor1         {:id "RUS-MIL" :name "Russian forces" :kind :state-military :country "RUS"}
 :actor2         {:id "UKR" :kind :state :country "UKR"}
 :location       {:country "UKR" :admin1 "Kharkiv"}
 :magnitude      {:fatalities 3}   ; optional
 :quote          "…evidence span from the text…"
 :coder          {:model "deepseek-v4" :prompt-hash "…" :confidence 0.8}}

;; L1 output
{:canonical/id "c-…" :type :assault :mode :did :dyad ["RUS-MIL" "UKR"]
 :day "2026-10-13" :reports [<event ids>] :independent-sources 3
 :origins #{"reuters.com" "kyivindependent.com" "gov.ua"}}

;; L4 question
{:q/id "q-rus-ukr-ceasefire-2026-11"
 :claim "Russia and Ukraine announce a ceasefire by 2026-11-30"
 :resolves "2026-11-30"
 :pattern {:type :agree :mode :did :dyad ["RUS" "UKR"]}  ; machine-checkable when possible
 :parents [{:node [:phase ["RUS" "UKR"]] :p-beta [2 8]} ...]
 :leak-beta [1 50]}
```

Actor IDs need a normalised registry: countries (ISO3) plus a sector suffix (-GOV,
-MIL, -REB, -OPP, -CIV), like CAMEO actor codes, with an LLM fallback for new
actors. Newsroom's `entities` namespace is a starting point.

---

## 4. The model, layer by layer

### L0 Extraction (LLM)

- The prompt has a fixed label order, PLOVER definitions with boundary rules,
  temperature 0, and a pinned prompt hash. Answers are JSON validated by
  enum vectors, in newsroom's style.
- The library takes `chat` as an argument, so newsroom passes in its cached
  role-LLM and tests pass a stub.
- Optional cheap pre-filter: newsroom's TinyDecide `screen` stage.

### L1 Resolution / dedup

- **Event-level clustering key:** (dyad, PLOVER type, mode, admin1, occurred-date ±1).
  Borderline merges go to an LLM "same real-world event?" check.
- **Independence:** group outlets that share near-identical text (MinHash/shingles,
  or newsroom's embeddings with cosine above about 0.9) or credit the same wire.
  Each group is one *origin*. Evidence strength scales with independent origins,
  not report count.
- **Drop** records where `:retrospective?` is true or occurred-date is outside the
  window. They go into history, not today's counts.

### L2 Observation model

**Coder confusion (Dawid-Skene, Bayesian):**

```
θ_k ~ Dirichlet(α_k)            row k of coder confusion: P(coded=j | true=k)
P(true=k | coded=j) ∝ π_k · θ_kj
```

- Seed θ with a strong diagonal prior: the LLM is mostly right.
- Update from a small gold set, hand-labelled or aligned with ACLED.
- Re-estimate whenever the model or prompt hash changes.

**Source reliability:**
- Each origin `s` has `r_s ~ Beta(a_s, b_s)`, the probability that a report it
  makes reflects a real event.
- Priors come from tier and type. Updates come from newsroom's judged projections,
  which replace the fixed 0.5×2 pseudo-count in `standings`.

**Event reality:**

```
logit P(real | origins) = logit π_0 + Σ_{independent origins s} log(r_s/(1-r_s)) · w
```

Here `w < 1` tempers residual dependence. This feeds the counts as fractional
(expected) events.

### L3a Rates and excitation (discrete-time Hawkes)

Count `y_{i,t}` of canonical events in cell `i` = (dyad or region, type, mode) on day t:

```
y_{i,t} ~ Poisson(λ_{i,t})
λ_{i,t} = μ_i · a_t + Σ_j Σ_{d≥1} α_{j→i} · g(d) · y_{j,t-d}
g(d) = (1-β) β^{d-1}            geometric kernel, β fixed or learned on a grid
```

- `a_t` is a media-attention covariate: total deduped volume relative to a
  baseline. It keeps coverage surges from looking like excitation.
- **Fitting:** EM with latent parents.
  - The E-step splits each y_{i,t} among background and earlier (j, d) in
    proportion to their contribution.
  - The M-step is closed-form. With `μ_i ~ Gamma`, `α_{j→i} ~ Gamma(shrinkage)`
    it is MAP-EM.
- **Sparsity:** only consider j→i where the cells share an actor or a region.
  Pool α across actor classes hierarchically.
- **Nonstationarity:** exponentially forget old sufficient statistics (λ≈0.97/day).
- **Output:** the α matrix is the learned "what triggers what" graph. Horizon
  probabilities come from simulating forward (branching sampler):
  `P(≥1 event in cell i within H days)`.

### L3b Escalation phases

Per dyad, a latent phase `z_t ∈ {calm, tension, crisis, conflict}`:

- Sticky transition matrix with Dirichlet priors.
- Emissions are Poisson counts per QuadClass (verbal/material × coop/conflict)
  with Gamma priors per phase. Empty days are an explicit observation, which
  fixes Schrodt's padding issue.
- Forward filtering gives `P(z_t | y_{1:t})` online. A forward simulation gives
  `P(z_{t+H} = crisis)`.
- The particle filter version is for when we add continuous latent "tension"
  (random walk on log rate). Use it later if the HMM is too coarse.

### L4 Question layer: noisy-OR network

A forecast question is a binary node Q with parents X_i (phases, "≥1 event of type
T in cell i before deadline" from L3, or other questions):

```
P(Q=1 | X) = 1 - (1-leak) · Π_{i: X_i=1} (1 - p_i)
p_i ~ Beta(a_i, b_i)
```

- **Priors from the LLM:**
  - Newsroom's `connection-prompt` already emits links with
    `confidence ∈ {high, medium, low}` and risks with
    `likelihood ∈ {unlikely, possible, likely, very likely}`.
  - Map these words to Beta priors, e.g. likely → Beta(7,3), low confidence →
    lower concentration.
  - Calibrate the word → number map against outcomes over time.
- **Updates from history:** TLogic-style mined rules. Count how often pattern A in
  a window was followed by pattern B within k days. These are Beta-Binomial
  updates to p_i.
- **Inference:**
  - exact enumeration for small parent sets (≤ ~15 binary parents);
  - Gibbs/likelihood weighting otherwise;
  - integrate over Beta uncertainty by sampling, which gives a predictive
    *distribution* rather than a point estimate.
- **Keep the graph sparse and shallow.** Loopy inference is overconfident on
  dense loops.

### L5 Pooling, calibration, scoring

```
logit p* = γ · Σ_k w_k · logit p_k          (Σ w_k = 1; γ ≥ 1 extremizes)
```

- Sources k: the structured model (L4), an optional LLM forecaster (Halawi-style
  retrieval + several samples + trimmed mean), prediction markets, and the base rate.
- `w_k` comes from trailing log-score, using a softmax over the negative
  cumulative loss.
- Recalibrate with isotonic/Platt on resolved questions.
- Track Brier, log score and reliability bins against no-change / inertia baselines.
- Labels come from machine-checkable `:pattern`s resolved against later canonical
  events, plus newsroom's judged projections for free-text claims.
- **Leakage guard:** backtest LLM components only on periods after the model's
  training cutoff.

---

## 5. Proposed namespaces

```
bnm.math          log-gamma, log-beta, logit/expit, log-sum-exp, seeded PRNG,
                  samplers (gamma, beta, dirichlet, poisson, categorical)
bnm.dist          conjugate families as data: {:dist :beta :a :b}; update, mean,
                  var, sample, forget (exponential discount)
bnm.schema        PLOVER enums, mode, actor kinds; malli schemas; validators
bnm.extract       prompt builder + parser (pure); takes `chat` fn for the IO call
bnm.resolve       canonical-event clustering, origin/independence grouping
bnm.observe       Dawid-Skene confusion, source reliability, soft evidence
bnm.hawkes        discrete-time multivariate Hawkes: EM fit, step, simulate
bnm.phase         HMM forward filter/smoother, Viterbi, Gibbs/Baum-Welch fit
bnm.network       noisy-OR nodes, enumeration + Gibbs inference
bnm.rules         temporal rule mining → Beta updates
bnm.forecast      pooling, calibration, scoring (Brier/log/reliability)
bnm.model         top-level state value + `step` (one day) + `forecast`
bnm.store         SQLite persistence (jolt.db), schema like newsroom's tables
bnm.newsroom      adapter: newsroom rows → events; connection-prompt → priors;
                  projections → labels
```

Everything above `bnm.store` and `bnm.newsroom` is pure and testable with writ
laws. Examples: posterior mean moves toward data; Hawkes EM log-likelihood is
non-decreasing; noisy-OR is monotone in parents; pooling of identical forecasts
is the identity when γ=1.

---

## 6. Build order (each step useful on its own)

1. **`bnm.math` + `bnm.dist`**: numerics foundation, property tests.
2. **`bnm.schema` + `bnm.extract`**: PLOVER extraction prompt. Evaluate it on
   about 200 hand-checked newsroom articles to get a first confusion matrix.
3. **`bnm.resolve`**: canonical events and independent-origin counts. This alone
   fixes worldmonitor's double counting.
4. **`bnm.phase`** (HMM per dyad): the first end-to-end "is X–Y escalating, and
   how likely is crisis in 14 days" output. It is cheap and interpretable.
5. **`bnm.forecast` scoring + `bnm.newsroom` labels**: measure everything from
   here on against inertia baselines.
6. **`bnm.hawkes`**: the excitation graph, "what drives what".
7. **`bnm.network` + `bnm.rules`**: question-level forecasts with LLM-elicited
   priors from `connection-prompt`.
8. **Pooling with an LLM forecaster and markets**, then the newsroom
   integration: a `{{bayes}}` block in the briefing prompt and a `/model` page.

**Historical data for steps 4-6:** newsroom's own archive is small. For
backfilling and validating the dynamics we could load ACLED or POLECAT/ICEWS
exports, which are already event-coded, through the same L1+ layers. The LLM
extraction then only has to cover the live tail.

---

## 7. Open questions

- **Granularity of "world state":** dyads (actor pairs) vs. regions vs. newsroom
  storylines. Dyads match the literature, storylines match newsroom's UI. We
  could support both: the cell key is just data.
- **Free-text vs. machine-checkable questions:** how much do we restrict
  questions to `:pattern`s the model can resolve itself?
- **Newsroom coupling:** library depends on newsroom vs. newsroom depends on the
  library. Recommended: the library depends on nothing newsroom-specific (takes
  `chat`), and newsroom adds it as a dep plus a small analysis-hook PR.
- **Historical backfill source:** ACLED (licence and terms) vs. POLECAT (Harvard
  Dataverse) vs. GDELT (free but noisy).

---

## 8. References

**Ontologies and data**
- CAMEO manual: http://data.gdeltproject.org/documentation/CAMEO.Manual.1.1b3.pdf
- PLOVER/POLECAT, Halterman et al. 2023: https://arxiv.org/abs/2304.01331
- ACLED: https://acleddata.com · ICEWS: https://dataverse.harvard.edu/dataverse/icews
- GDELT reliability and dedup: https://ideas.repec.org/a/spr/jcsosc/v8y2025i3d10.1007_s42001-025-00409-4.html

**LLM event coding**
- ConfliBERT vs. LLMs: https://arxiv.org/abs/2412.15060
- He et al. 2026, "When Better Codebooks Are Not Enough": https://arxiv.org/abs/2606.06781
- Zero-shot PLOVER: https://arxiv.org/abs/2308.07876

**Dynamics**
- Spatio-temporal Hawkes on ACLED (Browning et al. 2024): https://arxiv.org/abs/2408.14940
- Linderman & Adams 2014, Hawkes network discovery: https://arxiv.org/abs/1402.0914
- Schrodt, HMMs of conflict escalation: https://parusanalytics.com/eventdata/papers.dir/schro97.pdf
- BSTS/CausalImpact: https://research.google/pubs/inferring-causal-impact-using-bayesian-structural-time-series-models/

**Temporal knowledge graphs**
- TLogic: https://arxiv.org/abs/2112.08025 · RE-NET: https://arxiv.org/abs/1904.05530
- LLM in-context TKG forecasting: https://arxiv.org/abs/2305.10613 · popularity bias: https://arxiv.org/abs/2407.11638

**Event causality**
- Bhattacharjya et al. IJCAI 2020: https://www.ijcai.org/proceedings/2020/167

**Forecasting**
- ViEWS prediction challenge: https://arxiv.org/abs/2407.11045
- Halawi et al. 2024: https://arxiv.org/abs/2402.18563
- ForecastBench: https://arxiv.org/abs/2409.19839
- Metaculus bot benchmark: https://www.lesswrong.com/posts/Surnjh8A4WjgtQTkZ/q2-ai-benchmark-results-pros-maintain-clear-lead
- Satopää et al. 2014, log-odds pooling and extremizing: https://doi.org/10.1016/j.ijforecast.2013.09.009

**LLM + structured models**
- LLM-elicited BN priors (Nafar et al. 2025): https://arxiv.org/abs/2505.15918
- RAG conflict forecasting: https://arxiv.org/abs/2505.09852
- Sequential Bayesian updating of linguistic beliefs: https://arxiv.org/abs/2604.18576

**Source reliability**
- Dawid & Skene 1979: https://doi.org/10.2307/2346806
- Dong et al., source dependence / copy detection: https://lunadong.com/publication/dependence_vldb.pdf
- Truth discovery survey: https://arxiv.org/abs/1505.02463

**Inference**
- Pearl 1988; Henrion 1989 (leaky noisy-OR).

Some references were cited from memory rather than confirmed during this
research pass: CAMEO, RE-NET, Linderman & Adams, Satopää, Dawid-Skene, Pearl and
Henrion. Spot-check their URLs and venues.
