# Handover

## State as of 2026-11-14 (branch `structure`)

**The base of record is t80-base** (fifth note to amendment L1, 14
November, docs/PHASE-CHANGES.md): `bnm.world-run --out t80-base --without-relations true --known r7-at-fx --leak-fixes true --pwt-stale true --lag-stale true --gauge-std stable --leading true --leading-priors true --real-lags true --at-risk catalogue --lead-std history --frailty-gauges true --dated-credit true --set '{:learn-offsets :mixture :flow-std-from :resolved :clim-learn :current :frailty {} :discount-from :data :flow-std :stable :evidence-at :issue-joint}'`, on t53-noclim's inputs:
t71-stack plus the banking node's credit inputs from the BIS, dated by
its releases (`--dated-credit true`, bnm-9vm option A; docs/CALIBRATION.md
"Dated credit for banking: t79-bis"). A data-vintage correction, adopted
neutral: banking year nodes pooled Brier +0.00001 [-0.00023, +0.00029], no
fold gains or loses; all 13 kinds +0.00000. Only banking_crisis changes
(t79-bis's forecasts); the other twelve kinds are t71-stack's byte for
byte.
- The ledger builds 2027-Q1 on t80 (`base`, `command`, `base-files`;
  `known` r7-at-fx and `inputs`/`base-inputs` unchanged: the BIS credit is
  in data/slow/series.tsv). The command rerun to data/runs/t80-base gives
  t79-bis's banking and t71-stack's other twelve byte for byte, reading
  exactly `inputs`; 2026-Q4-r1 still reproduces from its archive. No
  forecast issued.
- Caveats: the BIS's long credit series were first published in 2013, so
  the 2003-12 origins read a backcast; post-crisis false alarms at 2010-12
  (Ireland, Sweden, Belgium) where all-lender credit to GDP rose as GDP
  fell. Recorded, not tuned.

The section below is the earlier 13 November handover, kept as written.

## State as of 2026-11-13, later (branch `structure`)

**The base of record is t71-stack** (fourth note to amendment L1, 13
November, docs/PHASE-CHANGES.md): `bnm.world-run --out t71-stack
--without-relations true --known r7-at-fx --leak-fixes true --pwt-stale true --lag-stale true --gauge-std stable --leading true --leading-priors true --real-lags true --at-risk catalogue --lead-std history --frailty-gauges true --set '{:learn-offsets :mixture :flow-std-from :resolved :clim-learn :current :frailty {} :discount-from :data :flow-std :stable :evidence-at :issue-joint}'`, at 5f7dd70 with augur v0.24.0, on t53-noclim's inputs: t65-gauges
plus the true publication lags (bnm-a1e), the catalogue's at-risk rule
(bnm-96j), the leading gauges standardized on their history (bnm-11y) and
the year node's world gauges in the frailty (bnm-v1y); docs/CALIBRATION.md
15-17. Against t65-gauges year Brier +0.0023 [+0.0004, +0.0046] pooled,
log +0.0080; against t53-noclim +0.0062; no fold lost against either.
Alerts at 4:1 (PLAN principle 10): pooled cost 0.298 against 0.310 for no
skill, 8 of 21 episodes caught against 6.
- Known open issues, not fixed: market-crash false alarms from vix12 and
  the 2022 curve; food 2020, growth 2007-08 and 2019, banking crises and
  sovereign defaults never alert; the frailty's σ at the edge of its grid
  (bnm-02u). Recorded, not tuned after seeing the scores.
- The ledger builds 2027-Q1 on t71 (`base`, `command`, `base-files`;
  `known` r7-at-fx and `inputs`/`base-inputs` unchanged: the publication
  lags and at-risk rules are code over series.tsv). The command reproduces
  t71-stack byte for byte (t71-stack-repro); 2026-Q4-r1 still reproduces
  from its archive. `score` now scores an issue on the catalogues its own
  command built (`issue-catalogues`), so a t71 issue is scored on its
  at-risk rows. No forecast issued.

The section below is the earlier 13 November handover, kept as written.

## State as of 2026-11-13 (branch `structure`)

**The base of record is t65-gauges** (third note to amendment L1, 13
November, docs/PHASE-CHANGES.md): `bnm.world-run --out t65-gauges
--without-relations true --known r7-at-fx --leak-fixes true --pwt-stale true --lag-stale true --gauge-std stable --leading true --leading-priors true --set '{:learn-offsets :mixture :flow-std-from :resolved :clim-learn :current :frailty {} :discount-from :data :flow-std :stable :evidence-at :issue-joint}'`, at 7dc9281 with augur v0.24.0, on t53-noclim's inputs.
Against t53-noclim year Brier +0.0040 [+0.0018, +0.0065] pooled, no fold
lost against t53-noclim, t62-fixes or t63-issue (docs/CALIBRATION.md 13).
- Known open issue, not fixed: market-crash false alarms 2018-22 (Dec 2018
  0.49, Dec 2020 0.34, Dec 2022 0.36 against rates 0.05-0.21), from the
  2022 curve inversion and vix12. An approach limit (CALIBRATION 13 loss
  1), deliberately not tuned after seeing the scores.
- The ledger builds 2027-Q1 on t65 (`base`, `command`, `base-files`;
  `known` r7-at-fx and `inputs`/`base-inputs` unchanged: the leading
  gauges are series.tsv rows). The command reproduces t65-gauges byte for
  byte (t65-gauges-repro); 2026-Q4-r1 still reproduces from its archive.
  No forecast issued.

The section below is the earlier 12 November handover, kept as written.

## State as of 2026-11-12, later (branch `structure`)

**The base of record is t53-noclim** (second note to amendment L1 of 12
November, docs/PHASE-CHANGES.md): `bnm.world-run --out t53-noclim
--without-relations true --known r7-at-fx --leak-fixes true --pwt-stale
true --set '{:learn-offsets :mixture :flow-std-from :resolved}'`, on the
series.tsv of bnm-56c.31 and the econ-events.csv of bnm-56c.32. Against
t52 year Brier -0.0009 [-0.0036, +0.0018], no fold lost; market crashes
2007-09 -0.127, one origin, a level shift, not a lost
signal (docs/LOOP-AUDIT.md 6).
- bnm-56c.33's fix is the lattice option `:flow-std-from :resolved`; the
  default `:all` is the old standardizer, kept so registered issues
  reproduce. The golden snapshot tracks the default.
- The ledger builds 2027-Q1 on t53 (`base`, `known` r7-at-fx, `command`,
  `base-files`, `base-inputs`). The command reproduces t53-noclim byte for
  byte; 2026-Q4-r1 still reproduces from its archive. No forecast issued.

The section below is the earlier 12 November handover, kept as written.

## State as of 2026-11-12 (branch `structure`)

**The base of record is t52-world-norel** (note to amendment L1 of 12
November, docs/PHASE-CHANGES.md): `bnm.world-run --out t52-world-norel
--without-relations true --known r6-at-fx --leak-fixes true --pwt-stale
true --set '{:learn-offsets :mixture}'`. That is t51b's command (the
look-ahead fixes, bnm-56c.23; PWT at 36 months, bnm-h8d; learning at the
offset mixture, bnm-56c.22) with its quarters pooled with r6-at-fx,
r6-at-8ul's onsets run on the corrected series.tsv. To December 2025 t52
is byte for byte t51b (r6-at-fx's named rows differ only at 2026
quarters): year Brier against t50 -0.0013 [-0.0026, +0.0002], no fold
lost; quarter-lead -0.0006, the honest score. The noise rule fails, from
the base's weighting (sequencing).
- The ledger (bnm.ledger) builds 2027-Q1 on t52 (`command`, `base`,
  `base-files`, `base-inputs`, `inputs` with data/runs/r6-at-fx/preds.tsv).
  Commands with a quoted argument (`--set '{...}'`) are now written and
  read back as one argument (`command-line`, `manifest-args`). 2026-Q4
  and 2026-Q4-r1 still reproduce byte for byte from their archives with
  their own commands. No forecast was issued.
- Not adopted: bnm-56c.26 (a node with nothing resolved issues 0.5). The
  opt-in `--clim-prior kinds` (bnm.lattice `:clim-prior`) made food price
  shocks run away (year p 0.98 at 2012, t52a-climprior) and lost three
  folds; the base is unstable to its early states through the year-quarter
  loop, bead bnm-56c.27 (related to bnm-56c.9). 56c.26 stays open behind it.
- Runs: r6-at-fx (kinds run separately and spliced, see its README.txt),
  t52-world-norel (built as t52b-noclim and renamed), t52a-climprior.

The sections below are the 11 November handover, kept as written.

## State as of 2026-11-11 (branch `structure`)

**The base of record is t50-world-norel** (note to amendment L1,
docs/PHASE-CHANGES.md): `bnm.world-run --out t50-world-norel
--without-relations true --known r6-at-8ul`, t48's command on the
catalogues rebuilt after the gazetteer fix (bnm-8ul), its quarters pooled
with the onsets run r6-at-8ul. r6-at-8ul is r6-at's command on those
catalogues with lapsed sources kept: a source that has published for a
country but has nothing fresh keeps its last values and a lapse flag
whose prior is on its own scale (bnm.named/vectorizer, augur v0.18.0
logit :raw). Before, a presence indicator learned from the countries a
source never covers was applied to every country when the source went
stale, and r6-at's named forecasts stepped at 2025q3 (CRU climate).
The lattice reads PWT with a 30-month staleness and so misses its
political-economy gauges at September origins (bnm-h8d, needs a
decision: fixing it moves the base and the first issue's reproduction).

**The forward ledger** (bnm.ledger; registration and amendment L1 in
docs/PHASE-CHANGES.md)
- Issued: 2026-Q4 (11 November, on t48 and r6-at; role :reported, no
  verdict) and 2026-Q4-r1 (11 November, on t50; role :primary),
  registered/ledger/<issue>/, inputs archived read-only in
  data/ledger-inputs/<issue>/. 2026-Q4-r1 reproduced 2026-Q4 byte for
  byte on its archive with 2026-Q4's own manifest command, and t50 on
  every row to December 2025.
- Next: refresh the catalogues (several stop 5-18 months short of a
  December origin), then, dated in February or March 2027,
  `jolt -A:tools -M -m bnm.ledger issue --issue 2027-Q1 --date <date>`.
  It reruns 2026-Q4-r1 on data/ledger-inputs/2026-Q4-r1/ and refuses
  unless that issue's forecasts come back byte for byte; register the
  printed hashes in bnm.ledger/issued in the commit that adds the issue.
- Nothing is scored before the first windows close; no claim before
  three year-node issues (2031 at the earliest).

**The recursive model** (docs/RECURSIVE.md; epic bnm-56c)
- Step 0, loop audit Round 1, is in (bnm-56c.17): `:state-from`, `:evidence-at :issue` and the `:self` read, all opt-in. The review of Round 1 found four bugs, now fixed:
  - at quarter nodes with `:downscale-uncertainty`, `:lp` scored the mean offset, not the offset mixture that was issued;
  - the C9 test passed vacuously;
  - `:up-net-of-downscale` was dropped under `:state-from`;
  - `:reference` without `:all` failed with a nil error.
  The learning path still uses the mean offset (bnm-56c.22, a base change).
- Step 1 is in (bnm-56c.18), with no forecast change:
  - `bnm.node` holds the node contract, built from lattice book entries (`split-book` keeps the issued forecasts in their own book) and from bnm.buffers records;
  - `bnm.compose` holds the identities up both trees;
  - the tests are compose_test (C3–C5), node_test (C6, C11) and coherence_report_test;
  - `tools/bnm/coherence_report.clj` produced docs/COHERENCE.md on t50 and t48. Parents sit below the independent composition of their children (subregion year −0.086 after the review's corrections), mostly because country p runs above observed rates (banking 0.062 against 0.032), not dependence; the year sits above its quarters' rates at the country (log ratio +0.28).
  - The review of step 1 found seven issues, all fixed test-first on recursive-2: the report composed unscored children and had no calibration column; `node/hazard-p` used 1 − exp(−λw E[e^z]); five Hermite points were inaccurate at parent scale; `cumhaz-split` edge cases; the downscale's Jensen gap under `:up-net-of-downscale`; C6/C11 tests that could not fail (now `node/state-reader` refuses issued forecasts, and `read-graph` keys reads by kind).
- Step 2 (bnm-56c.19, with bnm-h8d and bnm-56c.22) is built and scored, **not adopted** (docs/PHASE-CHANGES.md, "the world node and the world state at the year"):
  - `bnm.world-state` and `bnm.world-gauges`: world food (IMF index), Brent, VIX, world credit, US policy rate, dollar, read as of t with publication lags, at the year and half-decade nodes directly and × exposure; the world node's state is these series.
  - `bnm.world-run` options, all off by default: `--world-gauges`, `--world-extra noise|planted|false` (the harness), `--pwt-stale` (h8d), `--catalogue-through` (Test 7), `--year-delay n` (pjg.6), `--set` (`:state-from`, `:evidence-at`, `:downscale-learn`, `:learn-offsets :mixture`). Without them t50 reproduces byte for byte.
  - t51-world-norel against t50: pooled −0.0046, 2020–22 lost (food price shocks −0.025, market crashes −0.027). Planted and false-move rules pass; the per-fold noise rule fails. The cut loop alone carries the food 2020–22 loss; the world group got no posterior weight at the food year node after 2010.
- Next: step 3 (frailty states), or a way for the reference's world group to reach the issued forecast; the user decides on adoption.

**Look-ahead fixes (bnm-56c.23, 12 November; branch fix-leaks)**
- Data (data/slow regenerated; data/ledger-inputs untouched): PWT rows dated by the release that first published them (resources/bnm/releases.tsv; irr not before PWT 9.0, labsh and the capital stock not before 8.0); UIS tertiary enrolment at 21 months, not WDI's 9; a daily FRED series' incomplete last month left out (the archived October 2026 VIX was 1-15 October); EM-DAT's year to date left out.
- Code, opt-in `bnm.world-run --leak-fixes true` (off, t50 reads as before): Maddison and NMC read no later than their releases (bnm.releases), banking's direct PWT read the same way, urban growth in one unit across 2016. `--world-extra` now works without `--world-gauges` (the harness column as its own group).
- **Vintage caveat, not fixable by lags.** Every input is today's revised download. V-Dem v16 is retro-coded for years before any V-Dem existed (first public data 2016); WID's distributional series did not exist before WID.world (2017; bottom-50 for most countries 2017-2022); PWT 11, Maddison 2023, NMC 6.0, WDI (WPP projections, IGME revisions), GMD 2026_09, JST R6 and the research datasets (MV, EPWT, GDD, Romer, BVX) carry revisions no origin could have read. The release dates above fix *when* a year is read, not *which* estimate of it. Real-time vintages where archives exist (ALFRED, old PWT/Maddison/NMC/V-Dem releases, WDI archives) are bead bnm-09d.
- Corrected candidate **t51b-world-norel** (t50 + `--leak-fixes true --pwt-stale true --set '{:learn-offsets :mixture}'`), not adopted: against t50 year Brier −0.0013 [−0.0026, +0.0002], no fold lost, quarter-lead −0.0006 [−0.0011, −0.0002]; leak fixes alone (fx-leak) −0.0001 year, −0.0008 quarter-lead (the honest score, not a loss of the fix); h8d + .22 −0.0012 and lose 2010–12 (market crashes). Planted passes; noise fails (−0.0008), as it does on fx-leak (−0.0019): the base's weighting. Adoption is the user's decision (docs/PHASE-CHANGES.md).
- Still leaky: the quarters' offset pools with r6-at-8ul's named forecasts, fit on the old series.tsv (PWT at 24 months, UIS at 9); rerunning that onsets run on the corrected data is part of adopting any corrected base.

## State as of 2026-11-10 (branch `structure`)

The sections below are the 2026-10-17 and 2026-10-16 handovers, kept as
written. They predate the lattice, the world model and the coupled
systems; read them for the event model and the US hazard only. Every
number here is from the record it names; docs/TRIALS.md is the ledger and
docs/PHASE-CHANGES.md holds each registration and result.

**The event model is a lattice** (docs/LATTICE.md, bnm.lattice; trials
36-41)
- A node is one place at one time scale (country, subregion, region,
  world; quarter, year, half-decade), each its own model, learning from
  1870 by dynamic model averaging.
- Thirteen kinds of onset are stepped together (docs/WORLD-MODEL.md,
  `bnm.world-run`; trial 42): banking, currency, sovereign and inflation
  crises, growth collapses, fiscal consolidations, food price shocks,
  market crashes, protest and violence surges, coups, conflict and war
  onsets.
- The quarters take the year's forecast as their offset, pooled with
  named's (trial 41's C). Against named this was level; against the
  trial 39/40 quarters coups gain after Holm. Trial 41 tested C
  reconciled; the reconciliation is a separate step (`bnm.reconcile`)
  that no world run has had, so the base's quarters are unreconciled
  (bnm-con). The year nodes are not affected.

**The base of record was t48-world-norel** (t50-world-norel since 11
November, above), the world without relations:
`bnm.world-run --without-relations true`. A run now has to name its
model; with neither `--without-relations true` nor `--graph` it is
refused (until 10 November a bare run read trial 43's graph).
- Its history: t44-world-norel, rebuilt as t46 (regression check), t47
  (WDI zeros that were missing values, bnm-as1) and t48 (unknown errors
  fed back as 0, groups without inputs, bnm-wns). t48 against t47: Brier
  +0.002 [+0.000 +0.004] pooled; hard top-10 recall 0.271 against 0.274.
- Links between kinds (trials 42-45) did not gain: trial 42 lost, 43 was
  level, 44 and 45 level. docs/RELATIONS.md has each edge's form and the
  weight it earned.
- Nothing added over the base since has been kept: the stock-flow states
  (T1-T9, H4), supply shocks (SS) and fast signals (FSa-FSc) are level,
  failing or equivalent to nothing. T5 passes on JST 1996-2015 against
  history alone and does not carry to the lattice, which already reads
  those states. T1(b) passes only after a fix made after the outcome.
- Cells against provinces (CVP) fails: provinces stay the unit inference
  reads; cells are storage and the material layer. Located shocks (LS):
  countries stay the unit.
- The use is the watch list (bnm.watchlist; rule registered under
  bnm-82j.1, second form bnm-82j.6): top-10 recall on hard onsets.
- 2023 on is locked. `bnm.anticipation --confirm true` has never been
  run: no confirmation output is in data/runs, the docs or the history.
  Runs learn through 2025, and the lattice trials score origins
  2003-2022, whose two-year windows read onsets to 2024.

**Coupled systems** (docs/COUPLED-SYSTEMS.md, epic bnm-4hv)
- Built and tested: the engine (bnm.engine), the stock-flow consistent
  core (bnm.sfc), the control layer (bnm.control), credit allocation
  (bnm.allocation) and capacity from investment (bnm.capacity).
- Results: the core is consistent and does not beat persistence; the
  allocation and capacity tests are equivalent to nothing (9d, 9e); damped
  reversion is the net-lending forecast (9g); bnm.sfc/rates is
  non-inferior to estimated on one holdout country and is the core for
  scenario runs (9h, 9i).
- Not built: real to political (bnm-4hv.4), political to control
  (bnm-4hv.5), the hindcasts (bnm-4hv.7), the registered event test
  (bnm-4hv.8).

**Due: the 2027 rates score** (bnm-4hv.16, deferred to 2027-11-01)
- The frozen forecasts are scored once against the first OECD Table
  14/720 vintage downloaded on or after 1 November 2027, by
  `bnm.sfc-rates-confirm`, as docs/COUPLED-SYSTEMS.md 9h "Run under A2"
  gives it.
- The three registered files are committed in `registered/sfc-2027/`
  (hashes in its README) and must never be edited; the scorer and the
  test suite check them. No target of data year 2025 or later has been
  read.

**Elsewhere**: the essentials and essentiality (docs/ESSENTIALS.md; it
reads t44-world-norel's hazards, see there), the economy's character
(docs/ECONOMIC-CHARACTER.md), ties and structure (docs/STRUCTURE.md).

**Working**
- Tests: `jolt -M:test`. `data/` is outside git; in a worktree it is a
  symlink to the main checkout's.
- Issues are in beads (`bd ready`, `bd show <id>`).

## State as of 2026-10-17 (branch `forecast-calibration`)

The sections below this one are the 2026-10-16 handover; most of their
"next" items are done. Where things stood then:

**Crash and crisis hazards** (docs/TRADING.md, docs/INVARIANTS.md)
- The US drawdown hazard of record (`bnm.trader`, run `data/runs/record`)
  uses:
  - the stable base (valuation, trend, volatility);
  - a Markov-switching bear regime discovered from returns
    (scripts/regime-hmm.py, statsmodels);
  - the 12-month oil change;
  - dynamics learned on small falls (`bnm.scales`);
  - dimension-free displacement over fifteen US dimensions
    (`bnm.us-invariants`).
- Its held-out AUC is about 0.83. It is recalibrated yearly by a logistic
  fit.
- No regime is written by hand. The wage-share terms (`bnm.us-polecon`)
  were a failed test: hindsight in the US, no replication in the UK.
- Catches 2000 and 2022. 2007 is flagged only by the panel crisis model
  (`bnm.macrohistory`) and by the BIS credit gap, which fails the pre-1990
  rule. 2020 is exogenous.
- Cross-country invariants: `bnm.macrohistory` (JST, 1870-2020) and
  `bnm.waves` (Reinhart-Rogoff, 1800-2016).

**Event model** (docs/INVARIANTS.md, final sections)
- The baseline is a discounted local level (`:level-discount`, 0.8 prior
  share).
- Country forecasts are their children summed (`:bottom-up`).
- Calibration is monotone (pool-adjacent violators).
- Reports show non-randomized PIT histograms.
- On 2008-09, 80% coverage is 0.78-0.85 and the PIT is near flat
  (`data/runs/final-1`).

**Onset hazards** (docs/SLOW-LAYER.md): year-block bootstrap intervals and
Sarlin usefulness in the report (`data/runs/harness-1`).

**Slow layer**: scenarios (`bnm.slow/scenario`) roll a shock through the
learned cross-domain regressions.

**Open**
- bnm-t5b.9, the corpus gaps. Coups (Cline Center, Powell-Thyne),
  Laeven-Valencia 2026 (IMF) and disasters (EM-DAT, ReliefWeb) refuse
  scripted downloads. Put the files in `data/raw/slow/{coups,lv,disasters}`
  by hand.
- bnm-enb.4/.5, partial pooling and prior sensitivity. Being re-run at HEAD
  on 1993-99 (`s2-*` runs, `data/runs/sensitivity.txt`).

## The 2026-10-16 handover

State of the work as of 2026-10-16, and what remains, for picking it up on
another machine. README.md describes the library; docs/RESEARCH.md the
literature behind the plans below.

## Moving the work

The code is on GitHub (`main`). The corpus and run outputs under `data/` are
not in git, nor are the local agent and issue-tracker files (`CLAUDE.md`,
`AGENTS.md`, `.beads/`, `.claude/`), which are gitignored. On the new
machine:

```bash
git clone git@github.com:yogthos/bayesian-news-model.git ~/src/bayesian-news-model
H=yogthos@jellyfin:src/bayesian-news-model
# corpus: raw and ref (copied already), normalised events, weekly bins
for d in raw ref norm bins; do rsync -a --progress --partial $H/data/$d ~/src/bayesian-news-model/data/; done
# finished runs only (see below for what each is)
for r in v2-q v2-q-flat sens-default sens-nopool sens-w2; do
  rsync -a --progress --partial $H/data/runs/$r ~/src/bayesian-news-model/data/runs/; done
# issue tracker and agent instructions
for f in .beads CLAUDE.md AGENTS.md; do rsync -a --progress --partial $H/$f ~/src/bayesian-news-model/; done
```

Not worth copying: `data/norm-v1`, `data/bins-v1` (the format before actors
and provinces), `data/scratch`, and the runs `full-q`, `full-y`, `v2`, `v3`,
`smoke` (older code, superseded), and `v2-y`, `v2-y-flat`, `sens-w32`
(stopped part way; rerun them).

Sizes: raw 52 GB, norm 14 GB, bins 4.1 GB, the five finished runs 2.4 GB.
Only `data/bins` is needed to train; `norm` only to rebuild bins, `raw` only
to re-ingest.

Memory: a full-hierarchy trainer peaks around 1-2 GB. Jolt's heap ceiling is
2 GB by default (`JOLT_MAX_HEAP=<n>g` or `off` to change it). The old machine
(7 GB) ran two trainers and a sensitivity run at once; a bigger one can run
more of the queue below in parallel.

Tests: `jolt -A:test -M -m bnm.test-runner` (18 tests, 171 assertions, all passing).

## Where things stand

The hierarchy: 2,500 dyads, up to 1,500 each of actors (country and sector),
pairs of actors, and provinces (GeoNames admin1), under countries,
subregions, regions and the world; about 7,500 nodes. Online EM for a
discrete-time multivariate Hawkes process with forgetting; partial pooling of
the priors across nodes of a kind; parameter uncertainty drawn into
forecasts; a `ModelStore` protocol for databases (see README).

The Bayesian review epic, `bnm-enb` (`bd show bnm-enb`):

| issue | state |
|---|---|
| .1 parameter uncertainty in forecasts | done |
| .2 centred priors | done |
| .3 more simulations, Monte Carlo error reported | done |
| .4 partial pooling | code done; close once the pooled/unpooled comparison is in (below) |
| .5 prior sensitivity | 3 of 8 runs done; finish them and write up |
| .6 posterior predictive checks | done |
| .7 count-scale observation model | open, next model change |
| .8 backtest report out of memory | fixed (this commit) |

### Results: quarterly backtest, 2000-2026, GDELT only

`data/runs/v2-q` (full hierarchy) and `data/runs/v2-q-flat` (same code at
58add0f, countries and dyads only), 106 quarters each, 100 simulations.
`v2-q` ran out of memory writing its report after training; the report was
rebuilt from its rows with `bnm.rescore` (checked: rebuilding the flat run
reproduces its original report byte for byte).

Country escalation (>= 1.5x last quarter), average precision model/history,
and Brier skill against climatology raw/calibrated:

| | full | flat |
|---|---|---|
| violence, all | AP 0.192 / 0.121, BSS -54% / +0.5% | AP 0.199 / 0.121, BSS -42% / +1.7% |
| violence, onsets (quiet) | AP 0.099 / 0.049 | AP 0.095 / 0.049 |
| protest, onsets | AP 0.104 / 0.047 | AP 0.107 / 0.047 |
| country violence totals, log error (persistence 0.164) | 0.574 | 0.505 |

What this says:

- Actors and provinces do not improve country forecasts; they make the
  totals slightly worse. Their value is their own forecasts: actor and
  province escalations rank well (AUC 0.85-0.94 against 0.65-0.79 for their
  own history; onset AP about 2-5x history's).
- Both rank escalations clearly better than history (AUC ~0.76-0.85 against
  0.60-0.76), so the "where" is there.
- Both are badly overconfident before calibration (raw Brier skill well below
  zero) and only just above climatology after it.
- **The forecast means are much worse than persistence** at every level
  (log error ~0.5 against ~0.16 for countries; actors and provinces too).
  The means overshoot: the simulated distributions have a heavy upper tail
  (explosive branching, and the Gamma parameter draws added in .1). This is
  the most important thing to fix (see 1 below).

### Results: prior sensitivity, 1993-1999 (partial)

Country violence, raw Brier skill / calibrated, onset AP model/history:

| run | all | onsets |
|---|---|---|
| sens-default | -3.7% / +5.7% | AP 0.145 / 0.090 |
| sens-nopool | -4.4% / +6.4% | AP 0.158 / 0.090 |
| sens-w2 (prior weight 2) | -2.2% / +4.9% | AP 0.150 / 0.090 |

The prior weight barely matters, and pooling does not help (slightly worse
onsets); worth confirming over the full record before keeping it on.

## What to do next, in order

1. **Fix the overshooting forecast means.** Compare the simulated mean, the
   median (`:q50`, in rows/*.tsv) and persistence; check whether the error
   comes from a few explosive simulations (cap or temper branching per
   simulation), from the parameter draws (`:param-uncertainty false` with
   `bnm.refit` on saved origins isolates it without retraining), or from the
   baseline. `bnm.refit` re-forecasts saved origins with config overrides;
   `--save-origins true` on a training run saves them (about 55 MB each).
2. **bnm-enb.7, the count-scale observation model**: negative binomial
   draws of raw counts per week, with the dispersion learned per node, so
   zero weeks and bursts come out right (the PPC found far too few quiet
   weeks). Probably also helps 1.
3. **Finish the sensitivity series** (the remaining variants, then the
   comparison), at HEAD:

   ```bash
   run() { jolt -A:tools -M -m bnm.train --sources gdelt --to 1999 --eval-from 1993 --every 13 \
             --sims 50 --name "$1" --config "$2" > "data/$1.log" 2>&1; }
   run sens-w32 '{:prior-weight 32.0}'
   run sens-mu10 '{:share {:mu 0.1}}'
   run sens-mu40 '{:share {:mu 0.4}}'
   run sens-f98 '{:forget 0.98}'
   run sens-f995 '{:forget 0.995}'
   jolt -A:tools -M -m bnm.sensitivity sens-default sens-nopool sens-w2 sens-w32 \
     sens-mu10 sens-mu40 sens-f98 sens-f995 > data/runs/sensitivity.txt
   ```

   The three finished ones ran at 43a671e; HEAD differs only in how the
   report is written, so they compare. Then close bnm-enb.4 and .5 with the
   write-up.
4. **The annual pair** (stopped part way), once 1 and 2 have landed, since
   they would change it:

   ```bash
   flat='{:structure-opts {:max-actors 0 :max-pairs 0 :max-provinces 0}}'
   jolt -A:tools -M -m bnm.train --sources gdelt --eval-from 2000 --name v2-y --save-origins true > data/v2-y.log 2>&1 &
   jolt -A:tools -M -m bnm.train --sources gdelt --eval-from 2000 --name v2-y-flat --config "$flat" > data/v2-y-flat.log 2>&1 &
   ```

   About 50 minutes a year each on the old machine. A run that dies after
   training can have its report rebuilt with `bnm.rescore <run>`.
5. **Multi-source training**: `--sources gdelt,icews,polecat` (bins for all
   three are built). Compare with GDELT alone over the years they overlap.
6. **A pretrained snapshot for newsroom**: train to the present, write it
   with `store/write-file`, publish it as a GitHub release asset (not in git:
   it is ~55 MB).
7. **Newsroom integration**: yogthos/newsroom issue #49 has the steps
   (implement `ModelStore` over its database, feed extracted events with
   `core/add-events`, read `core/risks` and `core/drivers`).
8. **The slow layer** (stocks: BIS, Jordà-Schularick-Taylor, WDI and so on)
   that drives baselines first and excitation second, per docs/RESEARCH.md;
   and a rewrite of DESIGN notes into the README once it settles.

## Tools

All under `tools/bnm/`, run as `jolt -A:tools -M -m bnm.<tool>`:

- `ingest`, `bins`: normalise the raw sources into daily events, and bin
  them weekly per node. (`gen_countries` regenerates the country and
  province tables from GeoNames.)
- `train`: backtest; `--every 13` for quarters, `--ppc true` for posterior
  predictive checks, `--save-origins true` to keep each origin's model.
- `refit`: re-forecast saved origins with forecast-time config overrides.
- `rescore`: rebuild a run's scored.edn and report from its rows.
- `sensitivity`: compare runs' scores and driver graphs.
- `bench`: time a forecast.
