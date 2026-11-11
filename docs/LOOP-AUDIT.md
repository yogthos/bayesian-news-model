# Loop audit: the Bayesian world simulation (bnm-56c)

Read only. Code: origin/structure e79d91a, exported to a scratch directory. The local checkout /Users/yogthos/src/bnm-structure is 6 commits behind; `lattice.clj` line numbers below are from the export. augur is v0.19.0 (f9fdb4dd), read from ~/.jolt/gitlibs. Nothing under data/ was written and no runs were started.

Tags: [R] read in the code, [I] inferred.

## 0. The whole in one paragraph

The system is not yet one dynamic whole. It is four separate systems:
- the forecast lattice (with bnm.onsets' named model as the quarters' offset);
- the weekly event model;
- the buffer hazard and scanner;
- the coupled engine.

Only one of them, the lattice, is in the base of record (t48/t50-world-norel), and it has loops in both directions only inside itself. Inside the lattice, the loops through the node's own combined forecast are too strong: the combination re-enters its own models by five routes. The loops that carry information from outside are too weak or missing: no world commodity state at the year nodes, no buffers, no engine, no events into stocks. The two findings that prompted this audit are instances of a single pattern. The lattice feeds its output back to itself, but does not take in the state of the world around it.

## 1. Loop map

Columns: direction | opposites | timing / known at the forecast date | opposite direction exists? | guard | does it confound evaluation?

### 1a. Lattice (src/bnm/lattice.clj, tools/bnm/{world_run,lattice_political,lattice_banking}.clj)

| # | Loop | Dir | Opposites | Timing | Opposite exists? | Guard | Eval confounded? |
|---|---|---|---|---|---|---|---|
| L1 | Own state: the node's combined p at t−per → its own contradiction gaps (lattice.clj:365, 381) | self | the place against itself above it | lagged one period, known | – | standardizer cap ±5; contradiction prior | **yes**: weighting changes the inputs |
| L2 | Year → quarter offset (`:downscale`, pool 0.5, :630-644; world_run.clj:63-69) → quarter states → year `:up` (:347) | down, then up | slow constraint against fast flow | year issued ≤ t−3..t−12; quarters at t read back at t | yes, but it is the same quantity going round | `:up-net-of-downscale` / `:up-lag` exist but are **off** in the base | **yes**: 86% of A's food loss |
| L3 | Space up: children's p → parent (`up-space`, :353) | up | the parts against the whole | same origin, finer levels first | yes (L4) | none | – |
| L4 | Space down: ancestors' state at t−per → country `:down`, `:exposure`, contradiction gap (:360, :421) | down | region strain against the country | lagged one period | yes (L3), so country(t−1) → region(t−1) → country(t) | none | **yes**: ancestors' states depend on the weighting too |
| L5 | Time down: coarser scales' latest state before t → `:down` (:358) | down | slow against fast | lagged | yes (`:up`) | none | yes |
| L6 | Errors y − p of the combined forecast for parent, children and coarser scales → `:feedback` (:430, :646) | both | forecast against outcome | known-at = window end + lag | yes | none | yes: the errors depend on the weighting |
| L7 | Coupled learning between adjacent levels' gauge models (`couple-levels`, :736; augur couple :680) | both | country experience against region experience | each period | yes | means only, strength 0.1, flows excluded | small |
| L8 | DMA weights from learn-time evidence (lattice.clj:617 → augur dynamic.clj:656-678, `log-score` at :664) | self | model against model | scored when the row is known (3–4 years after the origin), **by the model's state then, not at issue** | – | forget 0.995, eps 1e-6 | **yes**: rewards hindsight fit |
| L9 | Climatology offset at year nodes, from resolved rows only (:619-624) | self | level against events | lags 3–4 years | – | – | causes the level lag in transitions |
| L10 | Tension accumulation with decay 0.7 and reset on a known onset (:376; banking resolutions lattice_banking.clj:528-530) | self | build-up against leap | known-at | political kinds: no reset (lattice_political.clj:262) | decay | – |
| L11 | Relations between kinds (world_run.clj:81-183; books reset before each step, :348) | across kinds | one kind exciting or inhibiting another | before t | per edge | prior 0.2, var 0.1, nested | base has none |
| L12 | World commodity state → country **year** nodes | down | world price against national dependence | – | **missing**: only the quarters read world food/energy (lattice_political.clj:58-61); the year `:world` group is `world3`, the share of states with a known onset (:194-196) | – | – |
| L13 | World node: target "any onset anywhere" (lattice_political.clj:246-250, members = all) → every country's `:down` | down | – | lagged | – | none | trend confound (p 0.89→0.95) |

### 1b. Time tree and reconciliation (bnm-con, phase 4)

| # | Loop | Dir | Timing | Status |
|---|---|---|---|---|
| T1 | Quarter → year (`:up`), year → half-decade | up | at t | live |
| T2 | Year → quarter offset (downscale under a constant hazard, Gauss-Hermite for its uncertainty) | down | latest before t, 3–12 months stale | live; the quarters drop every other flow group (world_run.clj:65-66) |
| T3 | `bnm.reconcile`: quarters scaled to the year (tools/bnm/reconcile.clj:18-35, lattice.clj `reconcile`) | down | year's latest before t | **not run** in any world run (bnm-con). When run, it pools the year a second time (see issue 9) |
| T4 | Counts summed across places, Corani & Zambon's BUIS (LATTICE.md:109-111) | both | – | **not built** |

### 1c. Coupled engine (from the engine sub-audit; details in its report)

| # | Loop | Dir | Opposites | Guard | Note |
|---|---|---|---|---|---|
| E1 | SFC profit → investment → output | internal | investment against profit | kappa ≤ 2 (keen.clj:60) | lag 1 |
| E2 | Debt → interest → profit → borrowing | internal | accumulation against servicing | – | **open**: credit = target × expected output (sfc.clj:449-454) |
| E3 | Deposits as the residual buffer | internal | buffer against flow | **none** | negative deposits, bnm-4hv.18 (sfc.clj:505, 529-530) |
| E4 | Phillips: employment → wage share | internal | labour against capital | clamps [0.05,0.95] (sfc.clj:550-556) | off in the adopted configs |
| E5 | Ensemble analysis | data → model | model against data | rescale clamp [0.2,5] (sfc_analysis.clj:279) | the clamp hides divergence, bnm-tvk |
| E6 | Keen Goodwin-Minsky | internal | wages against profit, debt against growth | phi-max, kappa-max, d-max | recalibrated on windows ending at y−1 |
| E7 | World3 | internal | depletion against capital | table functions | no data, no country link |
| E8 | Allocation → capacity | up | credit against capacity | – | **nothing reads `capacity/*`** |
| E9 | Held-stock regime switch (engine.clj:384-446) | internal | wanted against delivered | floor | the shortfall is booked, but nothing reacts; flows are exogenous |
| E10 | Engine → lattice | up | – | – | **missing** in the base: `--states` is refused for the base (world_run.clj:281-299); tables only, 3 states, trials only |
| E11 | Events or forecasts → engine | down | – | – | **missing** (located_shocks is a test only); essentiality reads t44 hazards one way |

### 1d. Buffers, hazard and scanner (from the buffers sub-audit)

| # | Loop | Dir | Timing | Opposite exists? | Note |
|---|---|---|---|---|---|
| B1 | Reserves cover, grain stocks-to-use, energy stocks, credit gap, investment → pooled hazard (hazard.clj, buffers.clj:85-103) | up | known-date filtered; expanding fit every 5 years (hazard.clj:257-266) | no | causal, but on current revised vintages |
| B2 | Hazard and scanner → lattice | up | – | **missing** | ledger.clj:132-138 only hashes scanner.tsv |
| B3 | Realised events → buffers (a default, a food shock, conflict, an IMF drawing) | down | – | **missing** | buffer state is purely exogenous |
| B4 | World grain and oil cover → country rows | down | – | **missing** | only prices come down, to quarters, by exposure (fast_run.clj:55-68) |
| B5 | JST/PWT stocks stretched against their own decade → banking year node | up | causal (lattice_banking.clj:208-219) | – | the only stock path into the lattice |

### 1e. Weekly Hawkes model, ledger and online learning (from the event-model sub-audit)

| # | Loop | Dir | Timing | Guard | Note |
|---|---|---|---|---|---|
| H1 | Self and cross excitation (model.clj:7-25) | lateral | causal; online EM with forgetting | branching ≤ 0.95, **at forecast only** (model.clj:674-700) | learning itself is unconstrained |
| H2 | Parent country → provinces | down | causal | inside the cap | – |
| H3 | Provinces → country | up | – | – | **missing**: the country total is the sum of the dyad and pair leaves (model.clj:837-854), not of the provinces |
| H4 | Lattice or slow hazard → weekly baseline | down | – | – | **missing** (model.clj:182-200) |
| H5 | Weekly model → lattice | up | static rows file | – | reaches only bnm.onsets' mix group (onsets.clj:206-219), not the lattice |
| H6 | Calibration, spread and climatology learn from closed periods (train.clj:202-221) | self | closed only | coverage controller target 0.8 | the calibrator's input drifts with the spread (eval.clj:158-161, 200) |
| H7 | recalibrate_online learns at t from rows of origin t−2−lag (recalibrate_online.clj:141-148) | self | correct | phi 0.95 | phi and w picked on the scored years |
| H8 | Ledger issue: the previous issue is rerun on archived inputs (ledger.clj:442-468) | – | current catalogue vintage | byte identity | bnm-56c.13 |

## 2. Issues

Ordered by severity.

### CRITICAL

**1. The node's combined forecast feeds its own models by five routes, so a weighting rule changes the models it weighs.**
- Where: lattice.clj:365 (own state), :347 with :630-644 (`:up` through the downscaled offset), :360 (`:down` from ancestors), :421 (exposure = down × exposure), :430 with :646 (feedback errors of combined forecasts), and :702 (`:s` = logit of the *combined* p).
- Failure scenario (measured): under candidate A, the `all`, `down` and `up` models are different models. Mean |Δp| was 0.07–0.32 in 2009–14. Food 2020–22 Brier was 0.3720 for A's `all` against 0.3472 for t48's, which is 86% of A's loss. Every weighting comparison so far (temper, stack, evidence, synth, rk13) measures the rule and its side effect on the inputs together.
- Also a stability issue: the year's p re-enters through the quarters at about 0.5 × the horizon ratio, through the ancestors, and through itself, with no damping other than learned coefficients. A persistent level can sustain itself; `down` reached a 0.67 mean p in 2013 against a 0.05 rate.
- `:up-net-of-downscale` and `:up-lag` exist but are off in the base. They cut only one of the five routes.

**2. DMA evidence is learn-time, not forecast-time: the weights reward hindsight.**
- Where: augur dynamic.clj:664 (`log-score mdl rows` on the model's current state), :625 for `:evidence` (the same), called from lattice.clj:617.
- A year row (origin Y, window Y..Y+2) is scored at about Y+3/4. The model scoring it has meanwhile learned rows of origins Y−3..Y−1, whose windows overlap Y's. That is not a leak in the forecasts, since everything is known by the time the weights are used, but the evidence measures retrospective fit, not predictive skill.
- Failure scenario (measured): `all` held +88 nats of learn-time evidence over the base at 2021 in food, while its forecast-time edge was about 0 (+0.004 to −0.003 per row). Selection locks onto `all` at weight 1.000, and every rule built on `ev` inherits the bias, including regime-gated encompassing as drafted.
- Proper DMA uses the one-step-ahead predictive density at issue. The pending row already holds x and o, but not each model's issue-time predictive.

### HIGH

**3. Missing top-down loop: no world commodity or financial state at the country year nodes.**
- Where: year-names and year-groups at lattice_political.clj:72-78. The `:world` group is `world3` (:194-196), a count of onsets, slow (36 months) and lagged by known-lag (18 months for food). Financial kinds (market crash, currency, sovereign) use the political year gauges, with no VIX and no global credit at the year (lattice_political.clj:199-216). Global credit and the VIX sit only at the quarters (:69-70).
- Failure scenario (measured): world food momentum correlates r = 0.65 with the yearly food-shock rate, and r = 0.68 with `all`'s edge. The year node cannot see it, so 2020–21 is a level miss. The ceiling of a constant at the year's rate is 0.2189 against the lattice's 0.3472. Market crashes have the same shape: the lattice is worse than a constant (0.1737 against 0.0619).

**4. The top-down constraint overwhelms the fast scale.**
- Where: the quarter design drops every flow group and keeps only the year offset pooled 0.5 with named (world_run.clj:63-69). The year forecast it reads is 3–12 months stale (`latest-before`, lattice.clj:636), and the year learns 3–4 years late.
- Failure scenario (measured): 2021 food quarter rate 0.30, forecasts 0.012–0.024. Quarter models are 97% correlated copies of the offset. The slow side of the contradiction has no counterweight: no fast signal at the quarter can lift the level against the offset.

**5. No buffer state, engine state or hazard reaches any forecast node (B2, E10).**
- Where: no require of bnm.buffers, bnm.hazard, bnm.scanner, bnm.engine, sfc or world3 from lattice, world_run, fast_run or named. The `--states` path is refused for the base (world_run.clj:281-299) and admits only table modules (states.clj:52).
- Failure scenario: a held stock flipping to `:constrained`, reserves cover falling under 3 months, or SFC deposits draining cannot move any onset probability. The PLAN 1.3 regime switch and the 1.5–1.7 hazard have no path to the watch list.
- The "regime layer driven mainly by buffer invariants" in bnm-56c.2's design has no input wired to it. The diagnostic also found that buffer drawdowns do not predict food events (|r| ≤ 0.12). So that layer should be tested against world prices as well, not assumed to be driven by buffers.

**6. Missing loop: realised events never deplete or refill buffers or engine stocks (B3, E11, E9).**
- Where: BUFFERS.md:223-229. No module declares outflows in the golden runs. Held-stock flows are exogenous series. located_shocks is a test only.
- Failure scenario: a default does not stop debt service; a food shock does not draw down reserves; an IMF drawing, which is itself the reserve_crisis label (episodes.py:100, 489), does not refill them. Accumulation is never negated by its crisis in the stock layer. The lattice does this only as a tension reset, and only for banking.

**7. Catalogue end read as quiet in the forward issue.**
- Where: `history` (lattice_banking.clj:116-125) and `count-known` / `world3` (lattice_political.clj:161-168, 194-196) count onsets known by t − lag, without checking `:through`. The targets do check it (lattice_banking.clj:112).
- Failure scenario: coup, conflict, war and default catalogues stop 5–18 months before the 2026-12 origin. "Years since last", own10, others and world3 all drift toward calm across the gap, in exactly the issue the ledger scores forward. This is related to bnm-56c.13.

**8. Hazard folds are not clean (buffers audit 1).**
- Where: hazard-stocks.edn:91-97 and hazard_run.clj:76, 96-98. reserve_crisis, price_level_break and external-debt share one reserves stock record and the claims and income series.
- Failure scenario: with reserve_crisis held out, rows with identical features and co-located twin crises stay in training, so the "finds the new from invariants" PASS is overstated. The same shared stock fills two slots of the scanner's top decile (buffers audit 11).

### MEDIUM

**9. Reconciliation double-counts the year, and is not run at all (T3, bnm-con).**
- Where: reconcile.clj:27-33. The quarter p already contains 0.5 × the year's scaled forecast through `:downscale-pool`. `reconcile` then pools the windows' union 50/50 in log odds with the same year forecast again, giving the year an effective weight of about 0.75.
- It also equates the union of (t, t+24] with a year forecast for (t_y, t_y+24], where t_y lies 3–12 months before t. That holds only under a constant hazard, which is the condition that fails in a transition.
- The base runs none of it, so phase 4 has nothing coherent to build on yet.

**10. A degenerate world node feeds every country.**
- Where: lattice_political.clj:190 (members of "world" = all countries) and :246-250.
- Failure scenario: its target is true almost every year, so its state is a slow trend (p 0.89→0.95). That trend enters every country's `:down`, `:exposure` and feedback errors as a time trend whose coefficient differs by run (diagnostic Q2). It is a confounder, not a world state.

**11. Space loops count the country twice.**
- Where: lattice.clj:353 and :360. Country(t−1) → subregion(t−1) state → country(t) `:down`, alongside the country's own state(t−1) (L1). The subregion's gauges are the members' mean (lattice_political.clj:230-235). In small subregions (2–4 countries) the parent is largely the country itself.
- Failure scenario: collinear `own` and `parent` inputs. The contradiction gap own − parent carries little independent signal and its coefficient is unstable.

**12. Revised vintages are read as first-release data, everywhere.**
- Where:
  - year gauges (lattice_political.clj:201, annual CSVs with an assumed one-year lag);
  - reserves (IMF IL current download, `:lag 2` without `:revised`, hazard-stocks.edn:24, so the vintage sensitivity at hazard_run.clj:239-244 does not test it);
  - engine tables (keen_run.clj:26-29, tension, food);
  - catalogues (recalibrate_online.clj:54-57).
- Failure scenario: the most powerful channels on record (reserves T1(b) +0.160; the regime and economy year groups) are measured on data no one had. bnm-pjg.6, the delay test, is still open.

**13. The scored population is selected with hindsight.**
- Where: `onset-in?`'s busy check runs to lo = t + offset (lattice_banking.clj:111-114; 12 months ahead for w2); `at-risk?` (onset.clj:11-16, onsets.clj:535); episodes' `ongoing` (hazard.clj:179, episodes.py:967-974).
- The forecasts themselves are unaffected, but the scored and learned rows exclude cases decided by future crises. Deployed forecasts cover every row, so the test population is not the population that is forecast.

**14. Weekly model: provinces are not reconciled to the country, and the top-down loop is missing (H3, H4).**
- Where: structure.clj:10-14, model.clj:837-854, 182-200.
- A surge across a country's provinces never raises the country's forecast, and a structurally fragile quiet country decays toward its recent quiet.

**15. Event-model settings were tuned on the reported period.**
- Where: refit.clj; OPERATIONS.md:147-191; model.clj:83-102 (max-branching, shock size, spread mode, bottom-up).
- The AUC of about 0.86 that PLAN phase 2 uses as its benchmark is a development number.

**16. The engine's internal guards are clamps.**
- Where: sfc.clj:505 and 529-530 (deposits), :550-556; sfc_analysis.clj:279; Keen caps.
- These keep diverging runs finite so that they score instead of failing (bnm-4hv.18, bnm-tvk, bnm-rr7). The debt-service loop is open in the adopted SFC core (sfc.clj:449-454), so no Minsky build-up can occur.

**17. The branching cap applies only at forecast, not in learning (H1).**
- Where: model.clj:674-700 against :547-613.
- The learned state can sit above criticality while forecasts are capped, which makes the learned drivers hard to interpret.

### LOW

**18. `bnm.stocks` binary-searches availability dates that are not monotone.**
- Where: stocks.clj:22-31 (`last-index`), :85-89 (`known`), :33-45 (`latest`).
- data/slow/series.tsv has 121 pairs where annual and monthly rows of one variable share a period but not an availability order; named.clj loads them without regard to frequency. `known` can then return values not yet published (a leak), and `latest` can skip values.
- It is not on the lattice's named variables today. fast.clj:86 and :97 do call it.
- This is a real bug and needs fixing regardless.

**19. The node's own-state read bypasses the sheaf reader.**
- Where: lattice.clj:365, a direct `get-in book`.
- It is not recorded in `:reads`, so the read audit and the golden test cannot see the loop of issue 1.

**20. Other minor items.**
- recalibrate_online accepts its own `*-online` output as `--run` (recalibrate_online.clj:305, 370-372).
- Essentiality reads t44, not t48, and takes the union of conflict and war (essentiality.clj:20-22).
- Live and backtest event vintages differ (core.clj:99-104).
- Hazard tests read 2023–25, against the HANDOVER lock (hazard_run.clj:28).
- The investment-bust label is circular with its feature.
- A stale coupled table can be read when tables.edn is missing (states.clj:51).

## 3. Test-first fix plan

Each item says the test to write first (it must fail on e79d91a), then the fix and the beads it touches. Nothing changes the base of record. Every item is opt-in by a spec key and goes through the development protocol and a ledger amendment.

### Round 1: cut the self-loop and measure it (issues 1, 19, 2). Beads: bnm-56c.2, new child "lattice: weighting-invariant states"

- **Test 1a, invariance (lattice_test).** Run a synthetic lattice twice, differing only in `:weighting` (posterior against `{:rule :temper :power 0.01}`). With a new `:state-from :all` (or `:state-from :reference`), assert that every model's inputs `:x` in the trace are identical across the two runs, to 1e-12. On e79d91a they differ, so the test fails.
  - Fix: the book's `:s` (and the `p` used for errors) comes from one fixed reference model's predictive, which is the all-inputs model's f, or the base's. The combination is then used only for the issued forecast. This covers routes L1, L2, L4, L5, L6 and exposure in one place (lattice.clj:702 and :646).
- **Test 1b.** A read audit that counts the own-state read: assert that the trace `:reads` holds a `:self` read at t−per. Fix: route lattice.clj:365 through `sh/latest-before`.
- **Test 1c, golden.** The default spec reproduces t48's golden snapshot byte for byte, so the option is inert when off.
- **Test 2a, augur (dynamic_test).** Use a synthetic stream where model B fits overlapping later windows retrospectively but has no forecast skill. Under `:evidence-at :issue` (new), the weights stay at the base; under the learn-time default, they move to B. Fails today, since there is no issue-time option.
  - Fix: the pending row carries each model's issue-time log predictive (lattice.clj:675-677 `made` → pending). The averaging scores the row with that number when it becomes known. augur release, then pin.
- **Test 2b.** Re-run weighting-diagnostic Q4 on t48's inputs: the issue-time evidence must reproduce the forecast-time gains in the diagnostic's table, within 1e-9.
- Then rerun candidate A and the regime-gated design with states fixed and issue-time evidence. That is the clean comparison bnm-56c.2 needs before anything else is judged.

### Round 2: wire the missing top-down state (issues 3, 4, 10). Beads: bnm-56c.16 (price network), bnm-tee (gauges), bnm-0cd.2

- **Test 3a, planted (lattice_test).** Build a synthetic world where the countries' year rate is a logistic function of a world price momentum series, plus country noise. With a new `:world-gauges {scale (fn [t])}` group, entered directly and × exposure, the year node's Brier on the planted transition years must beat a node without it by a fixed margin. Fails today, since there is no such input.
  - Fix: a world-state group at the year and half-decade nodes: the FAO food index 12- and 24-month change, Brent, the VIX and global credit, read as of t with their publication lags. Each also enters × the country's exposure (fast_run.clj:55-68 already computes food, energy and openness). Use these same series as the world node's `:state`, replacing the degenerate any-onset state (issue 10).
- **Test 3b, leak guard.** The world-gauge reader throws on any value whose availability is after t (sheaf-style). Shift the series by one month and assert the read is refused.
- **Test 4, quarter counterweight.** On a synthetic level shift that only the quarter's world-price gauge sees, assert that the quarter can depart from a stale year offset. Fix: learn the downscale pool weight online, as a coefficient on the offset in place of the fixed 0.5. Or let the quarter read the world-gauge group outside the offset's pull.
- Registered acceptance (written first): food price shocks 2020–22 by fold, the noise rule, and a planted false world move that must not open the group.

### Round 3: coherence of the time tree (issues 9, 13). Beads: bnm-56c.9, bnm-con, bnm-90b

- **Test 9a.** `reconcile` applied to quarters built with `:downscale-pool w` must give the year an effective weight of exactly w, not about 0.75. Fails today. Fix: reconcile against the quarters' own forecast net of the downscaled part (the book already stores `:down`). Or drop the offset pool when reconciling.
- **Test 9b.** Window alignment: with a hazard rising linearly over 24 months, the reconciled quarter must match the true window probability within a tolerance. Fix: carry the year's hazard over (t_y, t] forward, by using the year's quadrature moments and adjusting for elapsed time.
- **Test 9c.** World runs call reconcile, or record that they don't. Decide and register (bnm-con).
- **Test 13.** For the at-risk filter, assert that `onset-in?` reads `busy` only up to t (known at the origin). Score both populations and report the difference. No change to registered numbers.

### Round 4: data-vintage and coverage hygiene (issues 7, 12, 18). Beads: bnm-56c.13, bnm-pjg.6, bnm-h8d

- **Test 18 (stocks_test).** A series with an annual and a monthly row in one period, with non-monotone availability. `known` must not return a row published after t. Fails today. Fix: sort and search by availability (or key by frequency), and assert one entry per [variable frequency period] at load.
- **Test 7.** For a catalogue with `:through` before t − lag, `history`, `count-known` and `world3` return nil (unknown) for the uncovered tail, never a count over it. Fails today. Fix: clamp the counting window to `:through`, and treat the uncovered part as missing so the group can drop out (bnm-wns).
- **Test 12.** Mark IMF IL reserves `:revised` in hazard-stocks.edn and assert the without-revised sensitivity drops it. Run bnm-pjg.6's delay test on the adopted groups.

### Round 5: buffers and engine into the forecast, and events into the stocks (issues 5, 6, 8, 16). Beads: bnm-56c.6, bnm-56c.10, bnm-4hv.4, .5, .8, bnm-4hv.18, bnm-82j.4

- **Test 8.** The hazard's leave-one-out holds out by stock record and purges same-place breaks of other families within the window. Assert that no training row shares a [record place window] with a test row. Then rerun T1(b).
- **Test 5a, nested gauges.** With `--states` or `:buffers` in a non-base run, assert that the lattice reads reserves cover, drain rate and drawdown duration, known at t, as a nested group. A planted quiet-until-depleted signal must earn weight, and a planted false depletion must not. Run it first against world prices (Round 2), since the diagnostic found that drawdowns carry nothing for food.
- **Test 6a, engine.** Inject a realised onset, such as a default or a food shock, and assert the engine's stocks respond: debt service stops, reserves draw down, an IMF drawing refills reserves, and conflict cuts capacity. All of it is dated at the onset's known-at, never before. Fails today. Fix: an event-to-stock edge module, with the engine's `check-known!` applied.
- **Test 16.** The SFC must hold deposits ≥ 0 and close the debt-service loop. A run that hits a clamp is reported as failed, not scored (bnm-4hv.18, bnm-tvk, bnm-rr7).

### Round 6: the event model's loops (issues 14, 15, 17). Beads: bnm-56c.7 (phase 2), bnm-82j.5, bnm-g5t.12

- **Test 14a.** Province forecasts sum to the country's within a tolerance after reconciliation. Fails today.
- **Test 14b.** Planted structural hazard: a quiet country with a high slow hazard gets a higher weekly baseline when the lattice's year p is passed down, lagged.
- **Test 17.** The learned state's spectral radius stays below 1 after each EM step. Fails if it can exceed 1 today. Fix: project after the M-step.
- **Issue 15.** Label the event model's 2000–26 numbers as development in PLAN phase 2. Score it forward only from the ledger.

Order: Round 1 first, because until the self-loop is cut and the evidence is issue-time, no weighting or regime result (Round 2's included) is interpretable. Then Round 2, the level miss. Round 4 is small and can run alongside. Rounds 3, 5 and 6 follow the PLAN phases.

## 4. Sources

- Weighting diagnostic: WEIGHTING-DIAGNOSTIC.md.
- Sub-audits (engine, buffers, event model): summarised above.
- Code: origin/structure e79d91a.
