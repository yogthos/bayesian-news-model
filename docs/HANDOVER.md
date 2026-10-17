# Handover

## State as of 2026-10-17 (branch `forecast-calibration`)

The sections below this one are the 2026-10-16 handover; most of their
"next" items are done. Where things stand now:

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
