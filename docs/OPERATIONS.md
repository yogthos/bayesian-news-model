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
| a full event-model retrain (`bnm.train`, as v4-q) | yearly, or when the monitor flags it | the model learns online, but structure and priors are set at training |
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

**Onsets** (`bnm.onsets`, stack model): the countries likeliest to see a
conflict onset, war onset, protest or violence surge, coup or banking
crisis within a year, from the latest quarter.

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

**On first run (October 2026)**
- The US drawdown hazards were calibrated.
- Violence surges were under-forecast (CUSUM 4.5).
- Banking crises were over-forecast (6.1): crises have been rare since
  2017.
- These are the ones to refit first.

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

**Exogenous shocks (2020)** (`scripts/detection-lag.py`). No model
anticipates them. Measured across every US bear market since 1990, from
the peak:
- the weekly hazard fires late, 17-21% down;
- the 200-day average fires at 6.5-16%;
- a volatility jump fires first in 2020, at 9%.

The hazards say what the risk is before a fall. The reaction layer says
whether one is under way.

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
