# Regimes: why predictors flip, and what political economy says about it

The trader's tests (docs/TRADING.md) found that the usual macro predictors
of bear markets change sign between eras:
- the term spread;
- inflation;
- monetary tightening;
- payroll growth.

The response there was to drop them. This document asks instead what sets
their sign, using the theories that make the setting explicit:
- Marxian crisis theory;
- Goodwin's distributive cycle;
- the regime schools that grew from them.

The claims are then tested with the same point-in-time, held-out discipline
as everything else.

## What the theories say

**Crisis theory.** Accumulation runs into one of a few limits, and which
limit binds depends on the period. Crisis theory names three:

- **Profit squeeze.** Weisskopf (1979); Boddy & Crotty (1975). Goodwin's
  growth cycle (1967) formalizes it as a predator-prey system between the
  employment rate and the wage share.
  - When labour is strong, a tight labour market raises the wage share and
    cuts profits.
  - Tightening, inflation and payroll growth then come before the crash.
- **Realization, or underconsumption.** Baran & Sweezy; Kotz (2009).
  - When labour is weak, profits are high but demand falls short.
  - Demand is carried by debt, and the crash comes when the debt does.
- **Overaccumulation and the falling rate of profit.** Marx, *Capital* III;
  Shaikh; Kliman.
  - Investment outruns profitability, and the profit rate falls.

Moseley's reading joins the profit squeeze and realization: a falling profit
rate that policy keeps from clearing through depression turns into a
realization problem, covered by household debt.

**Why this explains the flips.** The same signal means different things in
different regimes:
- Before about 1980 the wage share was high, so a tight labour market
  signalled a profit squeeze.
- After about 1980 the wage share was low, so it signals little, and credit
  is what matters.

**Regimes have names in this literature:**
- the Social Structure of Accumulation school (Gordon, Edwards & Reich;
  Kotz);
- the French Regulation school (Aglietta, Boyer), with its Fordist and
  post-Fordist accumulation regimes.

The eras the trader's tests split by roughly match them:
- 1930-49: the gold standard, depression and war;
- 1950-69: the regulated, Fordist regime;
- 1970-89: crisis and transition;
- 1990 on: neoliberal and financialized.

**Imperialism and uneven development.**
- Lenin's *Imperialism* (1916) ties the export of capital and the
  competition for raw materials and markets to falling returns at home.
- His law of uneven development ties war to shifts in relative strength.
- Stalin's *Economic Problems of Socialism in the USSR* (1952) kept the
  thesis that wars between capitalist powers remained inevitable.
- Varga (1946) had argued that wartime state regulation would damp
  capitalist crises, and he was condemned for it in the Varga debate.

Seventy years on, Varga's reading fits the post-war record better:
- crises were damped;
- no war broke out between major capitalist powers under US hegemony and
  nuclear deterrence.

That is itself a regime effect. A model can't hold "inevitable", only
hazards conditioned on regimes.

The quantitative descendant of uneven development is power transition theory
(Organski & Kugler 1980). Its tests are mixed:
- Kim & Morrow (1992) find war risk rises near parity, but not with the
  challenger's growth.
- Others find the opposite.

**Long waves.** Kondratiev's long cycles (Moscow Conjuncture Institute,
1925), and Perez's technological revolutions:
- an installation period ends in a financial bubble;
- a deployment period follows.

Both give useful vocabulary for technology's selection pressure. But with
two or three cycles in the data they can't be estimated or tested, so they
are context here, not inputs.

**Systems thinking.** Bogdanov's *Tektology* anticipated systems theory, and
Meadows gives the stock-and-flow form used throughout this model. Goodwin's
cycle is a two-stock system. A regime is which loop dominates.

## Testing it on US bear markets (`bnm.regime`, `bnm.us-polecon`)

**Data.** BEA annual accounts on FRED from 1929, read point in time (year Y
is known from April of Y+1):

| measure | definition |
|---|---|
| wage share | compensation of employees / national income |
| profit rate | corporate profits with inventory and capital consumption adjustments / last year's current-cost net stock of private nonresidential fixed assets |
| investment share | private nonresidential investment / GDP |

Each enters as its deviation from the previous 20 years' mean, and as its
change over three years. US household credit to GDP (BIS, from 1947) and
monthly unemployment and payrolls complete the set.

The macro predictors enter multiplied by the regime:
- the wage share's deviation, for the profit squeeze;
- a low wage share times household debt growth, for realization on credit.

**Univariate AUC against a 20% fall within 12 months** (`data/runs/regime-5`):

| input | 1930-49 | 1950-69 | 1970-89 | 1990- |
|---|---|---|---|---|
| tightening | 0.49 | 0.74 | 0.72 | **0.28** |
| tightening × wage share | 0.42 | 0.82 | 0.74 | 0.63 |
| payroll growth | 0.61 | 0.58 | 0.63 | **0.39** |
| payroll growth × wage share | 0.41 | 0.79 | 0.71 | 0.63 |
| rising unemployment × profit rate | - | 0.65 | 0.66 | 0.79 |
| household debt growth | - | 0.28 | 0.64 | 0.78 |
| household debt growth × low wage share | - | 0.71 | 0.31 | 0.70 |
| investment share | 0.49 | 0.23 | 0.31 | **0.80** |

**What the table shows**
- Tightening and payroll growth flip after 1990. Taken through the wage
  share, they keep their post-war sign. That is the profit squeeze as
  Goodwin and Weisskopf state it.
- The 1930-49 era behaves differently throughout, which the regime reading
  expects: deflation and the gold standard, not a labour squeeze.
- A high investment share went with booms under the Fordist regime and
  with crashes after 1990, the dot-com overaccumulation.

**The hazard**, tuned on 1950-89 and scored on 1990 on:

| inputs | 1950-89 | 1990- | 2000- | 2003- |
|---|---|---|---|---|
| base: valuation, trend, volatility | 0.64 | 0.69 | 0.68 | 0.66 |
| base + the flipping macro, raw | 0.59 | 0.68 | 0.65 | 0.66 |
| base + the same variables, raw (tightening, payrolls, unemployment, household debt, wage share, profit rate) | 0.68 | 0.61 | 0.63 | 0.60 |
| base + household debt, raw | 0.68 | 0.70 | 0.71 | 0.67 |
| base + profit squeeze (through the regime) | 0.63 | 0.72 | 0.70 | 0.74 |
| base + realization (debt × low wage share) | 0.71 | 0.75 | 0.76 | 0.73 |
| base + squeeze + realization | 0.71 | **0.79** | **0.80** | **0.79** |

The same variables entered raw make the hazard worse than the base (0.61).
Entered through the regime that the theory names, they improve it to 0.79.
The gain also holds without the 2000-02 bear market, the held-out episode
with the most months (0.79 from 2003). That is the answer to "why do
predictors flip": the regime decides what they mean, and once the regime is
in the model they stop flipping.

**Trading** (`data/runs/trader-realization`, threshold chosen on 1950-89):

| strategy | Sharpe |
|---|---|
| buy and hold | 0.61 |
| 10-month average | 0.69 |
| model | **0.74** |

The model beats the trend rule held out for the first time, with the same
18% maximum drawdown. Across thresholds the model's Sharpe is 0.43-0.78. It
beats the trend rule at two of seven, so the edge is not robust.

**Limits, stated plainly**
- **Hindsight.** The profit-squeeze form and the realization term were
  chosen after the era table was seen. The realization term was added
  knowing that 2007, a credit crisis, was the miss.
- **2007 is still not caught in level.** The probability was 0.10-0.14
  before it. The ranking improves mostly because quiet months get lower
  probabilities.
- **Calibration is worse.** The top fifth said 0.31 against 0.24 observed.
- **Few episodes.** There are about a dozen bear markets before 1990 and
  five after. AUC differences of 0.05 are within what chance gives with
  this few episodes.

Reading for August 2026: P(20% fall within 12 months) = 0.12, against a
base of 0.08.

## Countries: profit rates, uneven development and resource pressure

The Penn World Table 11 gives, by country and year (taken as known two
years on):
- labour's share;
- the real internal rate of return on capital, a profit rate;
- the investment share.

`bnm.named/political-economy` builds:
- **Profitability:** the profit rate and its change over three years, and
  the change in labour's share. A squeeze on profits leads to austerity and
  then unrest; a squeeze on wages leads straight to unrest.
- **Uneven development:** growth over five years relative to the world's.
- **Resource pressure:** the core's (G7) profit rate falling, times the
  country's resource rents. This is Lenin's capital looking outward for raw
  materials.

**Held-out AUC from 2008** (`data/runs/polecon-1`; "hard" is onsets in
countries with none in the previous ten years):

| kind | named | political economy alone | named + political economy | hard: named | hard: named + political economy |
|---|---|---|---|---|---|
| conflict onset | 0.894 | 0.882 | 0.892 | 0.881 | 0.870 |
| war onset | 0.870 | 0.827 | 0.865 | 0.794 | 0.788 |
| protest surge | 0.880 | 0.810 | 0.874 | 0.818 | 0.800 |
| violence surge | 0.799 | 0.793 | 0.799 | 0.772 | 0.774 |

At country level the political economy inputs add nothing:
- On their own they rank about as well as the country's own history.
- Added to the named inputs (food, oil, growth, inflation, youth
  unemployment, climate, protest momentum), they slightly lower AUC.

There are three reasons it could fail, none of them a refutation:
- **Data timing.** PWT is annual and arrives years late, so the profit rate
  the model sees is two to three years old.
- **What the named inputs already carry.** The squeeze reaches people
  through prices, jobs and growth, which the named inputs already measure
  monthly or quarterly.
- **Where Lenin's mechanism acts.** It works through interstate rivalry and
  intervention, not through where a conflict starts. The UCDP onsets here
  are coded by location, mostly civil conflicts. Testing it would need
  dyadic interstate data (Correlates of War MIDs, national capabilities)
  and power-transition measures.

## What this changes in the model

The cross-domain structure now has two forms:
- named interactions with signs the literature fixes, such as food prices
  through import dependence and fragility;
- regime terms, where a slow political economy stock (the wage share, the
  profit rate) decides what a fast signal means.

The second form answers why predictors flip, and it is where the US gain
came from.

What remains to test:
- the regime terms on other long national accounts (the UK has them from
  1855, via the Bank of England's millennium dataset);
- a dyadic interstate test of uneven development;
- a Markov-switching version that infers the regime instead of assuming it
  from the wage share (bnm-t5b.5).

## Out of sample: the UK (`scripts/uk-regime.py`)

The terms above were chosen on US data after seeing its era table. The
same fixed forms, applied to the Bank of England's "A millennium of
macroeconomic data", score as follows against a 20% fall in UK share
prices within 12 months:

| term | 1919-49 | 1950-79 | 1980-2016 | expected from the US |
|---|---|---|---|---|
| tightening × wage share | 0.43 | 0.28 | 0.42 | above 0.5 |
| unemployment change × wage share | 0.83 | 0.58 | 0.55 | below 0.5 (the US used payroll growth) |
| credit growth × low wage share | 0.46 | 0.75 | 0.33 | above 0.5 |

**The hand-written regime terms do not replicate.** The UK points the
other way or flips. The US held-out gain from them (0.69 to 0.79) was the
hindsight the section above warned of.

Regimes written in by hand fix one country's concrete expression. That is
why the model now uses only inputs that do not name a channel:
- regimes discovered from the data (scripts/regime-hmm.py);
- displacement against each dimension's own history (bnm.us-invariants);
- dynamics learned on small falls (bnm.scales).

All of these are set out in docs/INVARIANTS.md.

## Uneven development and interstate conflict (`scripts/interstate.py`)

**Data.** Correlates of War national capabilities (NMC 6.0) and
militarized interstate disputes (MID 5.0). The target is a dispute reaching
the use of force.

| input | 1870-1913 | 1919-38 | 1946-89 | 1990-2013 |
|---|---|---|---|---|
| **state initiates next year** | | | | |
| capability share vs its own 20 years | 0.53 | 0.56 | 0.57 | 0.60 |
| capability share growth, 10 years | 0.50 | 0.57 | 0.56 | 0.58 |
| capability level | 0.66 | 0.69 | 0.69 | 0.76 |
| **pair of the 8 strongest fights within 2 years** | | | | |
| parity | 0.56 | 0.45 | 0.62 | - |
| speed of the shift in their ratio | 0.38 | 0.53 | 0.40 | - |
| weaker closing on stronger | 0.50 | 0.49 | 0.52 | - |

**Monadic.** Uneven development in invariant form is stable but weak: a
state whose strength is rising against its own past initiates force a
little more often, in every era.

**Dyadic.** The power-transition terms are not stable, as Kim & Morrow
(1992) found, and the 2000s and 2010s have no forceful dispute between the
strongest to test them on.
