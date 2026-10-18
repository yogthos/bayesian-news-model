# Anticipating shocks, and the shape of the forecasts below the country

October 2026, epic bnm-g5t. The goal is real predictive power, shown by
anticipating shocks. docs/PHASE-CHANGES.md sets out what is possible and
how to score it; this note adds what the literature says about the
techniques not yet tried here, and the plan. Citations marked (unverified)
were not confirmed against a primary source; (abstract) only against one.

## The gap, measured

From the onset run of record (2008 on held out, window of a year):

| kind | stack AUC / usefulness | own history alone |
|---|---|---|
| conflict onset | 0.893 / 0.305 | 0.883 / 0.304 |
| war onset | 0.869 / 0.041 | 0.826 / 0.030 |
| protest surge | 0.848 / 0.146 | 0.830 / 0.200 |
| violence surge | 0.802 / 0.051 | 0.799 / 0.044 |
| coup | 0.794 / 0.041 | 0.791 / 0.032 |
| banking crisis | 0.615 / 0.044 | 0.722 / 0.136 |

Usefulness is Sarlin's, weighing a missed onset by 0.9. The slow layer adds
almost nothing to a country's own onset history, and in the hard cases (no
onset of the kind in ten years) nothing is useful: 0.00-0.02 for every kind
but banking crises. Finding where onsets already happen is not prediction.

## What the literature says

**Conflict and protest onsets.**
- Nothing submitted to the VIEWS 2023/24 challenge beat the benchmark that
  takes a country's last 12 months as the forecast distribution (CRPS 24.92;
  an NB GLMM 25.17, a Markov model 25.27, ConflictForecast's forest with news
  topics 25.96). Hurdles and Tweedie draws did not win either.
- The one measured edge on hard onsets is news composition: Mueller & Rauh
  (JEEA 2022) raise hard-onset AUC from 0.63 (history) to 0.75 with topic
  shares and their decayed stocks; year ahead 0.73, about 20% precision at
  20% recall (Mueller, Rauh & Seimon 2024). Composition, not volume, carries
  it.
- War-talk tension in the news led interstate wars by a year or more and
  civil wars by about six months (Chadefaux, JPR 2014, abstract).
- A self-exciting hurdle model beat its non-exciting version out of sample
  on Indonesian terrorism (Porter & White, AoAS 2012). A two-state observed
  Markov model was third in VIEWS and strong on upward changes.
- Coups do not spread (Miller, Joseph & Ohl, JCR 2018); protests do, within
  one or two weeks, more along trade ties (Magee & Massoud 2022, in sample,
  unverified). A learned connectivity network beat spatial lags out of
  sample (Dorff, Gallop & Minhas, ISQ 2022, abstract).
- GDELT's Goldstein score is a function of the event code; NumArticles in
  GDELT 2.0 counts only the first 15 minutes. The GKG themes start in 2013,
  so before that the event-code mix per node is the backtestable stand-in.

**Markets.**
- Option-implied tail measures (variance risk premium, Kelly-Jiang tail
  risk, crash-insurance demand) are risk premia: high readings predict high
  returns, not crashes. SKEW has no peer-reviewed record.
- Credit growth and asset-price growth together over three years give about
  a 40% chance of a crisis within three years against 7% (Greenwood, Hanson,
  Shleifer & Sørensen, JF 2022); credit growth alone forecasts modestly.
  This is likely why the US credit gap flagged 2007 only weakly.
- The excess bond premium carries the credit spreads' predictive power for
  downturns (Gilchrist & Zakrajšek); free monthly from the Fed since 1973.
- Long calm predicts banking crises out of sample, the level of volatility
  does not (Danielsson, Valenzuela & Zer, RFS 2018); loose conditions lower
  near-term downside risk and raise it one to three years on (Adrian et al.,
  AEJ Macro 2022). The sign flips by horizon, as critical slowing down did.
- Severity given a shock is more predictable than timing: credit growth
  times the spread jump predicts how bad a crisis gets (Krishnamurthy &
  Muir, JF 2025); bank credit above its 95th percentile predicts bank-equity
  crashes (Baron & Xiong, QJE 2017).
- Quantile regression on financial conditions did no better than GARCH out
  of sample (Brownlees & Souza, JME 2021).
- Exogenous shocks: no date is predictable, but in 2020 the information was
  in exposed stocks four to five weeks before the index broke (Ramelli &
  Wagner 2020).

**Shape below the country.** Gamma mixing (the local level, the negative
binomial draws) makes the log of a sparse node's total left-skewed, about
-1.5 at shape 0.5 and near zero at large shape: the lower tail too long and
the upper too short where series are sparse, symmetric where they are
large, as measured. The symmetric widening stretches the lower tail to
cover the upper. Weekly noise averages out of a quarter's total, so the fix
belongs in the persistent intensity: a lognormal multiplier per path (the
dynamic random effect of Berry & West, JBES 2020, made persistent), its
variance pooled across the hierarchy by level, channel and volume (as
DESeq2 shrinks dispersion, Love, Huber & Anders 2014). Overdispersed
offspring (Lloyd-Smith et al., Nature 2005) is the second step if the upper
tail still falls short. A quantile recalibration anchored at the median
(Kuleshov, Fenner & Ermon 2018) is the fallback.

## Plan

Each step must pass the acceptance test on the epic (bnm-g5t) or is
dropped. `bnm.anticipation` scores every onset model against own history at
5% and 10% alert budgets, with intervals over years, matched controls, in
2003-14 and 2015-26, and over the hard cases; onset runs now also forecast
the window two to four quarters ahead (`lead`), where a gain is
anticipation, not a nowcast.

1. **The event-code mix as a slow stock.** Per country and quarter, the
   shares of CAMEO root codes and quad classes, with decayed stocks, into
   the onset stack. Scored on the hard cases.
2. **The fast layer fed by the slow one.** The onset hazard and the
   composition stocks as covariates of the escalation probability's
   calibration, so a country can rise in the ranking before its events do.
3. **Credit growth × asset-price growth and the excess bond premium** in the
   banking-crisis and drawdown hazards; a severity model, P(20% fall | 10%
   fall).
4. **A path-persistent lognormal intensity shock** with pooled variance in
   the event model, and the retrain (v7-q), scored on PIT by volume, CRPS
   and coverage against v6-q.
5. **The 12-month empirical benchmark** in the event backtest, as the bar.
6. Protest contagion over a learned affinity network, if 1-2 pass.
