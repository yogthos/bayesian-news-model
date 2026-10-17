# The trader's test

Can the model tell a trader, early enough and accurately enough to act on,
that a major market event is coming? This document defines the tests, gives
what they found, and says what the evidence allows.

## The question, made testable

At each decision point the hazard gives the probability that the market falls
20% (or 10%) below today's level within the next h months or weeks: "if I
hold now, how likely is a loss this large before long?". Acting on it means
moving to Treasury bills when the probability is high, with 10bp per switch.

A probability is useful to a trader if it beats what traders already use:

- **buy and hold**;
- **the 10-month moving average** (Faber 2007): out when the price is below
  its 10-month mean, a rule that has halved drawdowns out of sample;
- **volatility management** (Moreira & Muir 2017): exposure scaled down when
  recent volatility is high.

Each test fixes everything on early years and scores later ones once:
- the inputs;
- the prior and discount of the hazard;
- the trading thresholds.

The thresholds are also shown across their whole range, so a result does not
rest on a lucky choice.

| tool | market | data | tuned on | held out |
|---|---|---|---|---|
| `bnm.trader` | US, monthly | French market returns 1926-, Shiller CAPE, FRED, GZ spread | 1950-1989 | 1990- |
| `bnm.weekly` | US, weekly | French daily returns 1926- | 1950-1989 | 1990- |
| `bnm.panel` | US and 21 developed markets, monthly | French country returns 1975- | 1980-2004 | 2005- |

`scripts/fetch-slow.sh market` downloads the data.

## Choosing inputs without hindsight

The bear market literature proposes many predictors:
- the term spread and inflation (Chen 2009; Nyberg 2013; Resnick & Shoesmith
  2002);
- default spreads and the excess bond premium (Gilchrist & Zakrajšek 2012);
- valuation;
- monetary tightening;
- unemployment rises.

Each input's univariate AUC was measured against 20% drawdowns within 12
months, in separate eras before 1990:

| input | 1930-49 | 1950-69 | 1970-89 | |
|---|---|---|---|---|
| CAPE (lagged 3 months) | 0.85 | 0.72 | 0.81 | stable |
| drawdown from 12-month high | 0.70 | 0.75 | 0.66 | stable |
| 12-month momentum | 0.30 | 0.29 | 0.32 | stable (inverse) |
| deceleration of momentum | 0.72 | 0.64 | 0.73 | stable |
| CAPE × drawdown | 0.71 | 0.76 | 0.67 | stable |
| CAPE × deceleration | 0.74 | 0.64 | 0.75 | stable |
| 6-month volatility | 0.67 | 0.71 | 0.56 | mostly |
| term spread | 0.66 | 0.32 | 0.31 | flips |
| inflation | 0.29 | 0.67 | 0.53 | flips |
| tightening (bill rate, 12 months) | 0.49 | 0.74 | 0.72 | flips after 1990 (0.28) |
| payroll growth | 0.80 | 0.61 | 0.55 | flips after 1990 |
| curve re-steepening after inversion | 0.50 | 0.66 | 0.46 | unstable (0.83 after 1990) |

The macro predictors change sign between eras, which is the instability
Welch & Goyal (2008) document for return prediction. Fitting them on the
1930s and applying them after the war inverted the model: AUC 0.39 on
1950-1989. They were dropped by a rule fixed before looking at 1990 on: keep
an input only if its sign agrees across the pre-1990 eras.

What remains:
- valuation, which is the stock;
- the turn of the trend: drawdown, deceleration, momentum, below the average;
- volatility;
- valuation times the turn (a rich market rolling over).

That last term is the Meadows pattern in market form: the stock has built up,
and the flow turns.

Two other findings constrain the hazard:

- **Long memory.** With about a dozen US bear markets, a discount of 0.93 a
  year made the intercept chase the last crash wave and inverted the ranking
  (AUC 0.36). Discounts are limited to 0.98-1.
- **Combinations did not help here.** Averaging one-input hazards with sign
  restrictions (Rapach, Strauss & Zhou 2010; Campbell & Thompson 2008) gave
  AUC 0.63, against 0.70 for the joint hazard.

## What the tests found

### Monthly US, 20% within 12 months (`data/runs/trader-7`)

**Discrimination and calibration**
- Held-out AUC is 0.70 (0.69 from 2000), Brier skill is positive, and the
  probabilities are calibrated.
- In the top fifth of held-out months the model said 0.24, and a fall came
  24.4% of the time. In the other four fifths it said 0.06-0.12, and falls
  came 2-8% of the time.

**Trading, held out (1990 on)**

| strategy | annual | Sharpe | max drawdown |
|---|---|---|---|
| buy and hold | 11.3% | 0.61 | 50% |
| 10-month average | 10.1% | 0.69 | 18% |
| model alone | 9.9% | 0.58 | 43% |
| 10-month average unless the model says risk is low | 10.1% | 0.69 | 18% |

The veto keeps the trend rule but stays invested when the hazard is below
its base rate. An earlier run showed it at 0.72, but that came from CAPE
being missing after mid-2023 in the Shiller mirror. With CAPE carried to the
current month (multpl.com's table of Shiller's series, identical where both
have a month), the veto matches the trend rule at every threshold.

### Weekly US (`data/runs/weekly-2`)

**Discrimination**
- Held-out AUC is 0.69-0.72.
- For a 20% fall within 52 weeks, the top fifth of weeks saw falls 32% of
  the time, against a 17% base.

**Trading, held out**
- Nothing beats buy and hold materially: Sharpe 0.54 holding, 0.55 for the
  40-week average, 0.55-0.56 for the veto, and 0.58 for volatility
  management.
- On the tuning years the veto improved the 40-week rule from 0.70 to 0.75.

### The panel of 22 markets (`data/runs/panel-1`)

- Crashes are global. In the held-out episodes, the share of markets falling
  20% within 12 months swings between none and nearly all.
- Ranking markets against each other within a month is uninformative
  (within-month AUC about 0.5). The panel adds markets, not independent
  episodes.
- The hazard combined with the 10-month rule beats buy and hold on Sharpe in
  14-16 of 21 markets, but not the rule itself.

## What the evidence allows

**What works**
- The drawdown hazard is a calibrated, out-of-sample measure of crash risk.
- It concentrates risk: falls are three to five times as frequent in its top
  fifth as in its bottom three. That is usable for sizing hedges and for
  deciding when protection is worth buying.
- It ran high before and through the 2000-02 bear market.

**What it is not**
- It is not a timing signal that beats trend-following.
- It did not anticipate the turns whose triggers were new to it:
  - 2007, credit and housing: the probability was about 0.10 before the turn;
  - 2020, an exogenous shock;
  - 2022, inflation and tightening, a pattern absent since the 1970s.

Each crash has its own trigger, and a trigger matters only in the right
conditions. That is why single macro predictors flip sign between eras.

This agrees with the literature. Predictable index drawdown risk is mostly
priced: trend rules capture what is left of the timing, and volatility
management captures what is left of the risk.

## Political risk and country returns (`bnm.country-risk`, `data/runs/events-2`)

Changes in country risk ratings have predicted country returns (Erb, Harvey &
Viskanta 1996; Bekaert et al. 2014). The onset hazards rank political onsets
well (held-out AUC 0.84-0.90), so the test asks whether markets price that
slowly.

**The test**
- The signal is each country's out-of-sample probability of any political
  onset within a year: conflict or war onset, protest or violence surge.
  Both its level and its change over the year are tested.
- It is scored against the next quarter's return on the country's share price
  index, over 47 OECD markets. The returns are local currency monthly
  averages, so the quarter after the signal's is used.
- Twelve-month momentum is the control. It is a known cross-country signal
  (Asness, Moskowitz & Pedersen 2013).

| signal, 2008 on | rank IC | t | long-short a year |
|---|---|---|---|
| risk level | 0.00 | 0.0 | -0.9% |
| risk change | -0.03 | -1.2 | -1.1% |
| history model | 0.00 | 0.1 | -1.5% |
| 12-month momentum | 0.06 | 1.7 | +3.6% |

Over 2000-2026, momentum has an IC of 0.094 (t 3.4), which shows the test
finds what the literature does.

Political risk predicts nothing about these markets' returns. Two readings
are possible:
- markets price it already;
- these markets, mostly stable democracies, see too little of it.

Testing frontier markets, where political onsets happen, would need dollar
returns on their indices. No free source was found.

## Summary for a trader

**What the model gives**
- Calibrated probabilities of political onsets by country. Held-out AUC is
  0.84-0.90, and 0.79-0.88 on onsets in countries quiet for ten years.
- A calibrated measure of index drawdown risk, with held-out AUC about 0.70,
  three to five times as many falls in its top fifth as in its bottom three,
  and a reading every month (the "now" line of each report).

**What the evidence does not support**
- Using either one as a timing or selection signal that beats the simple
  rules (trend-following, momentum) after costs.
