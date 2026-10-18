# Running the models day to day

## One command

```bash
scripts/readings.sh            # rebuild derived inputs, read every model on the data in hand
scripts/readings.sh --fetch    # first refresh data: slow sources, markets, GDELT, normalize, bins
scripts/readings.sh --onsets   # also refit the onset hazards on fresh rows (hours)
```

The report is written to `data/derived/readings.txt`. Everything it reads
or writes lives in `data/derived/` (`tools/bnm/paths.clj`).

## Schedule

| what | how often | why |
|---|---|---|
| `scripts/readings.sh` | daily or weekly | the reaction layer is daily; the event model learns each closed weekly bin |
| `scripts/readings.sh --fetch` | weekly | most slow sources are monthly or quarterly |
| `scripts/readings.sh --fetch --onsets` | monthly | onset rows are quarterly and take hours to rebuild |
| a full event-model retrain (`bnm.train`, as v6-q) | only when the model's learning code or config changes | new data needs none: the live model is training continued (below) |
| the coup file (Cline Center) | when a new version is out | the Illinois Data Bank refuses scripts |

## What the report says

**Data, latest period.** The newest observation of key series. A series
months behind its usual lag means its source has stopped updating.

**US market** (`bnm.trader`, the model of record; docs/INVARIANTS.md):
- **The drawdown probabilities.** P(the market falls 10% or 20% below
  today's level within 6 or 12 months), recalibrated yearly on resolved
  forecasts, beside the base rate and where today sits among months since
  1990.
- **The banking-crisis channel.** The panel's crisis hazard on the credit
  gap, applied to the US, and the 12-month drawdown risk including it by
  total probability.
- **The reaction layer.** The market against its 200-day average, 21-day
  against one-year volatility, and the drawdown from the 52-week high, from
  FRED's daily S&P 500 (to the day before).

**Onsets** (`bnm.onsets`): the countries likeliest to see a conflict
onset, war onset, protest or violence surge, coup or banking crisis within
a year, from the latest quarter. Per kind the model with the best log score
on 2000-07; the members are own history, the literature's named stocks and
the event-code mix (what kinds of events are reported, docs/SHOCKS.md),
which is what lifts the hard cases.

**Event model** (`bnm.live`): the latest saved model learns every complete
weekly bin since its last, and its outlook gives the likeliest country
escalations next quarter. Each comes with:
- the calibrated and raw probability;
- the climatology and own-history benchmarks;
- the expected total with its 80% range.

**The calibration monitor**, beside each hazard: two CUSUM charts over the
last ten years of resolved forecasts.

## Why each piece is built the way it is

**Monitoring calibration continuously (CUSUM).** Calibration decays when
conditions move away from those trained on. Recalibration schedules and
threshold alerts don't control false alarms over continuous monitoring. A
sequential CUSUM chart does (Steiner et al. 2000; Monitoring the
calibration of probability forecasts, arXiv 2510.25573).

**How the charts are built (`bnm.hazard/cusum`).**
- One chart watches for events more frequent than forecast (odds × 2),
  the other for less frequent (odds × 0.5).
- The threshold is h = 4.
- Outcome windows overlap (a year's onset falls in four quarterly
  forecasts; a 12-month fall in twelve monthly ones), so each increment
  counts 1/overlap.
- The verdict uses the chart's current value; its ten-year maximum records
  past drift.

**Whether a drift is worth refitting (`bnm.hazard/recalibration-gain`).**
Rare events come in waves, so a quiet or busy stretch trips a chart without
the forecasts being wrong for the long run. Beside a drift the report says
whether shifting the forecasts by an intercept refit each year on recent
outcomes (half-life about three years) would have scored better, out of
sample, over the last ten years and over the whole record. Refit only when
both say so.

**On first run (October 2026)**
- The US drawdown hazards were calibrated.
- Violence surges were under-forecast (CUSUM 4.5), but refitting would not
  have helped over ten years (log score −0.0003): a busy stretch.
- Banking crises were over-forecast (6.1): none since 2020. Refitting would
  have helped over ten years (+0.004) and lost over the record (−0.016),
  where the quiet years before 2007 looked the same.
- Neither was refit. The stack already refits yearly on discounted outcomes,
  and no faster recalibration beat it on 2008-24 for any onset kind.

**Real-time data.**
- Forecast evaluation should use the data as published at each origin.
  Rankings can change when revised data stand in for real-time data
  (Robertson & Tallman 1998; vintage-aware forecasting practice).
- The slow layer already respects publication lags (each value enters
  when it became available).
- Payrolls and industrial production were tested against the Philadelphia
  Fed's real-time vintages (`scripts/rtdsm.py`, `BNM_REALTIME=1`). Held-out
  AUC is 0.830 against 0.831 on revised data, because the dimension-free
  aggregate dilutes any one series' revisions.
- The annual BEA series (wage share, profit rate) are revised more and
  have not been checked this way.

**Credit-driven crashes (2007).**
- Growth-at-risk work finds credit and risk indicators carry the downside
  information (Adrian, Boyarchenko & Giannone 2019; targeted financial
  conditions).
- The US equity record before 1990 has no credit-driven crash, so its sign
  cannot be learned there.
- So the crisis risk comes from the Macrohistory panel: the BIS-style
  credit gap (one-sided HP filter, λ = 1562.5 annual;
  `scripts/jst-credit-gap.py`, statsmodels' `hpfilter`) is the most stable
  crisis signal (AUC 0.58-0.78 in every era with crises).
- It enters the drawdown reading by total probability: P(fall) = 1 −
  (1 − P(fall without the channel)) · (1 − k · P(crisis)). The excess k =
  0.29 is measured before 2000 (P(fall | crisis) 0.40 against 0.15).
- Held out it lifts AUC from 0.831 to 0.836, and the probability before
  2007 from 0.06-0.16 to 0.09-0.18: flagged, weakly. The US gap was large
  before 2007 but not unusual against its own past.
- **Credit-financed booms** (Greenwood, Hanson, Shleifer & Sørensen 2022)
  now enter the panel hazard: three-year credit growth with real equity or
  house-price growth, each against the country's own past (the boom zone:
  credit in its top fifth, an asset in its top third; and their
  interaction), so the panel's coefficients carry to US series in other
  units. Real time on the panel the crisis AUC over 2000-20 rose from 0.69
  to 0.75, from 1950 from 0.52 to 0.62, and the timing of crisis waves from
  0.58 to 0.76 (`bnm.macrohistory`, the boom section).
- The US crisis probability is now 0.14 in mid-2004 to mid-2007 against
  0.08 in 2003 (before: flat at 0.09-0.10), and 0.12 in 2000. It leads the
  2008 fall by two to three years, so on the 12-month drawdown target it
  scores no better (AUC 0.837 against 0.839): the lead is the point, and it
  is read on the crisis channel's two-year horizon.

**Exogenous shocks (2020)** (`scripts/detection-lag.py`). No model
anticipates them. Measured across every US bear market since 1990, from
the peak:
- the weekly hazard fires late, 17-21% down;
- the 200-day average fires at 6.5-16%;
- a volatility jump fires first in 2020, at 9%.

The hazards say what the risk is before a fall. The reaction layer says
whether one is under way.

**The event model's ranges and probabilities.**
- The 80% range is widened or narrowed until 80% of outcomes fall in it,
  learned per level, channel and stratum as forecasts resolve
  (`bnm.calibrate/learn-coverage`). Learning it from the size of the errors
  instead fed on itself: widening moved the median, errors grew, and v4-q's
  coverage climbed to 0.95.
- Below the country level outcomes still fall above the range more often
  than below it (13-14% against 6-7%). A factor per side (`:coverage-sides`)
  evens the tails but drags the median down and scores worse, so it is not
  used. At the country level the tails are even.
- The fix is in the forecast itself: each path carries one log-normal
  shock per node and channel, held over the quarter, log-sd 0.4 for quiet
  nodes and fading with volume (`bnm.model/shock-sd`). Gamma mixing leaves a
  sparse node's log total left-skewed; the shock lengthens its upper tail.
  Refit over v6-q's origins it evened the outer tenths below the country
  (pairs: summed deviation of the PIT from flat 1.28 to 0.80) with CRPS
  unchanged and country escalation AUC up (violence 0.792 to 0.797). It
  changes forecasts only, so a model trained without it takes it on load.
  Learning its size per kind from the tails measured worse (the shock moves
  the median, and the size chased it), so it is fixed.
  Replayed live from January 2023 with it on, country Brier held or
  improved (violence 0.0423 against 0.0424) and the 80% range covered
  0.81-0.82 as the widening learned without it relearned.
- The escalation probability is the tail, above 1.5 times the last
  quarter, of a log-normal fitted to the forecast sample, then calibrated.
  From 100 draws a counted share is zero for most nodes, and the ties lose
  the ranking.
- A surging node can be forecast at several times its last quarter (Yemen
  and Saudi Arabia, October 2026). Over 2000-26 such forecasts (3x or more)
  came true with a log bias of +0.05, so they are not runaway excitation.
- In use it keeps learning. Every weekly bin updates its parameters
  (online EM with forgetting). On each quarter's eve it forecasts the
  quarter, and when the quarter ends it scores that forecast. The scores
  recalibrate the escalation probabilities, re-learn the ranges, and add to
  the climatology and the live record printed with the outlook.
- The tracked nodes are rebuilt yearly from recent volumes, keeping what
  each node learned, and the priors are re-pooled, as in training. So the
  history never has to be relearned. A retrain is needed only when the code
  that learns changes, since the learned state was made by the old code.
  When the monitor flags drift, the quarterly scoring is already
  recalibrating; a retrain helps only if the drift outlasts that.
- `bnm.shadow` replays the live path from a saved origin and sets its live
  record beside the backtest's. From January 2023 they agree: 2,880 country
  forecasts per channel over 12 quarters, coverage 0.80 both, calibrated
  Brier 0.042 live against 0.045 (violence).
- After a retrain, delete `data/derived/event-model.edn` so the pipeline
  starts from the new run.

## Known limits

- **Crises.** Exogenous shocks are not anticipated. Credit crises like
  2007 are flagged only weakly.
- **Onset readings.** These come from the last onset run; `--onsets`
  refreshes them. The catalogues lag (UCDP to 2024, coups to early 2026),
  so the latest quarters are forecasts that have not been scored.
- **The event model's driver graph.** It shifts with prior settings
  (docs/INVARIANTS.md, prior sensitivity), so read it as uncertain.
- **Policy value of the onset probabilities.** They rank countries well,
  but for rare onsets acting on them rarely beats ignoring them (Sarlin
  usefulness near zero). Coups and banking crises are the exceptions
  (0.1-0.2).
