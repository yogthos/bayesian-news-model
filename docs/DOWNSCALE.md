# Carrying the year down to the quarter (research, October 2026)

Trial 39: the year node gains from its contradictions out of sample; the
quarter, on its own gauges with named as offset, is level with named.
Earlier attempts to give the quarter the year's strain as inputs it learns a
weight for (trials 36-38) lost. The question: how should a slow node's
knowledge reach a fast one?

## What the literature does

- **Top-down latent factor, not a learned input** (Berry & West 2020;
  West 2020's decouple/recouple). An aggregate model forecasts a latent
  factor; each fine model takes it as a known regressor, sampled from the
  aggregate's predictive, so the aggregate's uncertainty is carried down.
  The fine model does not have to rediscover what the aggregate knows from
  its own few events.
- **Vulnerability plus trigger** (Ghosh, Ostry & Tamirisa, IMF 2009; the
  same split in Goldstone et al. 2010, where slow institutions carry the
  two-year risk). Triggers are hard to predict; the slow vulnerability sets
  the level, the fast signals modulate it. In hazard terms, the quarter's
  log hazard is the year's log hazard plus a trigger term.
- **Hazard disaggregation.** Under a piecewise-constant hazard, a quarter
  carries a quarter of the year's cumulative hazard: on the complementary
  log-log scale, cloglog(p_q) = cloglog(p_y) - log 4 for a one-year window
  (scaled by the windows' lengths otherwise). This is an offset with no
  parameter to learn.
- **Combining probability forecasts** (Ranjan & Gneiting 2010). A linear
  pool of calibrated forecasts is miscalibrated; the beta-transformed linear
  pool recalibrates it with two shape parameters and a weight. The logistic
  pool (a weighted sum of log odds) is the same idea on the log-odds scale.
  Few parameters, so they can be set on 1980-2002 and frozen.
- **Temporal hierarchies** (Athanasopoulos, Hyndman, Kourentzes &
  Petropoulos 2017; probabilistic: Zambon, Azzimonti & Corani 2024, BUIS).
  Base forecasts at each scale are reconciled so the fine ones compound to
  the coarse one; reconciliation improves the fine scale when the coarse
  one is better, which is now our case at the year.

## Why trials 36-38 lost

The quarter learned a weight for the year's strain from about two decades
of quarterly onsets. The coefficient was noisy and the model averaging paid
for it on the hard rows. Every method above avoids that: the slow signal
enters with its coefficient fixed by the hazard algebra (or set once on the
design years), and the fast model learns only the deviation.

## Recommended order (to register before running, designed on 1980-2002)

1. **Year hazard as the quarter's offset.** Replace (or pool with) named's
   offset by the year node's forecast disaggregated to the quarter window on
   the cloglog scale. The quarter's DMA then learns triggers only. Variant:
   a logistic pool of named and the year-implied hazard, two weights set on
   1980-2002 and frozen.
2. **Carry the year's uncertainty down** (Berry & West): draw the year's
   log odds from its predictive and average the quarter's forecast over the
   draws, rather than passing the point.
3. **Reconcile** (bnm-90b): BUIS across quarter windows and the year, the
   year acting as the constraint, scored on the same paired test.

Each step is one comparison on the paired test against trial 39's quarter.

## Sources

- Berry & West, Bayesian forecasting of many count-valued time series, JBES 2020 (arXiv 1805.05232)
- West, Bayesian forecasting of multivariate time series: scalability, structure uncertainty and decisions, AISM 2020 (arXiv 1911.09656)
- McAlinn & West, Dynamic Bayesian predictive synthesis (arXiv 1601.07463)
- Ghosh, Ostry & Tamirisa, Anticipating the next crisis, F&D 2009
- Goldstone et al., A global model for forecasting political instability, AJPS 54(1) 2010
- Ranjan & Gneiting, Combining probability forecasts, JRSS-B 2010
- Athanasopoulos et al., Forecasting with temporal hierarchies, EJOR 262 2017
- Zambon, Azzimonti & Corani, Probabilistic reconciliation of forecasts via importance sampling, Stat. Comput. 2024 (arXiv 2210.02286)
