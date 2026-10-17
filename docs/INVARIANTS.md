# Invariants across eras: why the model is not learning them

No regime rule covers every era: each crisis is a different concrete
expression of the same tendencies. A regime written in by hand, like the
wage-share terms in docs/REGIMES.md, fixes one expression. This document
asks why the model does not learn what is common to all of them, and tests
candidates on 150 years of data.

## The crash-by-crash audit

There is a crash of one form or another roughly every decade. Listing each
one's systemic causes shows which dimensions a model needs, and which the
current one lacks.

| crash | systemic causes | dimensions |
|---|---|---|
| 1907 | trust company credit, gold drain, no lender of last resort | credit, liquidity, external flows |
| 1920-21 | post-war commodity bust, sharp tightening | commodities, tightening |
| 1929-32 | margin and credit boom, overcapacity in farming and industry, gold standard, debt deflation, tariffs | leverage, overaccumulation, monetary regime, trade |
| 1937-38 | fiscal and monetary tightening in a fragile recovery | tightening |
| 1966, 1969-70 | credit crunch, inflation, rising wage share, Penn Central | tightening, profit squeeze |
| 1973-74 | oil embargo, end of Bretton Woods, profit squeeze, rich valuations | energy, monetary regime, wages, valuation |
| 1980-82 | Volcker tightening, Latin American debt | tightening, external debt |
| 1987 | portfolio insurance, rich valuations, rising rates | market structure, valuation, tightening |
| 1990 | savings and loans, commercial property, Gulf war oil | property credit, energy |
| 1997-98 | Asian crisis, Russian default, LTCM | external contagion, leverage |
| 2000-02 | technology overinvestment | overaccumulation, valuation |
| 2007-09 | household mortgage debt, shadow bank leverage | household credit, property, leverage |
| 2010-12 | euro sovereign debt | public debt, monetary union |
| 2015-16 | China devaluation, commodity and oil collapse | external, commodities, dollar |
| 2018 | tightening, trade war | tightening, trade |
| 2020 | pandemic | exogenous |
| 2022 | inflation, tightening, war and energy | inflation, tightening, energy, geopolitics |
| 2025 | tariff shock | trade policy |

**The recurring pattern** has two parts:
- a *stock* stretched against its own history, which differs each time:
  credit, property, capacity, valuation, the wage share, public debt, or
  external debt;
- a *flow* that turns, which also differs: tightening, an energy or
  commodity shock, contagion from abroad, or a policy shock.

What is invariant is the structure, not the channel.

**What the US monthly model has.** It holds valuation, trend and
volatility. The regime layer adds tightening and household debt. It lacks:
- energy;
- trade and geopolitical shocks;
- contagion from abroad;
- market leverage;
- the dollar;
- the strength of the stabilizing loops (policy capacity, financial
  repression).

## Why it does not learn the invariants

1. **Representation.** The hazard is linear in specific channels.
   - When the channel differs from one crisis to the next, each coefficient
     averages over episodes where it mattered and episodes where it didn't.
   - With few episodes, the averages flip between eras.
   - Measures that are indifferent to which dimension is stretched are
     needed.
2. **Too few episodes.** The US has about a dozen bear markets since 1926,
   too few to learn anything across eras. Pooling 18 countries over 150
   years (Jordà-Schularick-Taylor) gives 88 systemic crises and 356 years
   with a real equity fall of 25%.
3. **No connection across timescales.** The hazard sees each month's inputs
   at one scale.
   - Displacement at several scales at once (resonance) is missing, as is
     the slow global cycle of crisis and memory.
   - Holling's panarchy names the two cross-scale links: "revolt", where a
     small fast collapse triggers a larger slow one, and "remember", where
     the slow level shapes recovery.
4. **Adaptation chases the last wave.** Discounting old data lets the
   intercept follow recent crisis frequency, which inverts the ranking. Risk
   is lowest just after a wave and builds in the calm.
   - This happened in the US monthly test (docs/TRADING.md) and again here.
   - Regimes need a latent state, not forgetting.
5. **A missing dimension: the stabilizing loops.**
   - From 1950 to 1972 the 18 countries had one systemic crisis in 23 years.
   - Banks were regulated and capital controlled.
   - Nothing in the inputs measures that, so the level of risk shifts
     between eras unexplained.

## Tests on the Macrohistory Database (`bnm.macrohistory`, `bnm.invariants`)

**Setup**
- **Dimensions.** Each country-year has 14 dimensions: credit, mortgages,
  house prices, equity prices, investment, current account, public debt,
  inflation, short rate, term spread, wage share, unemployment, bank
  leverage and exchange rate.
- **Stretch.** Each dimension is measured as its stretch, a z-score against
  the country's own previous 10 years (also 5 and 20).
- **Specific inputs.** The signed stretches.
- **Invariant inputs:**
  - the root mean square stretch (*norm*);
  - the share of dimensions stretched past 1.5 (*breadth*);
  - the largest stretch;
  - the norm at 5 and 20 years, and *resonance* (the smallest of the three
    norms: displaced at every scale);
  - rising variability of growth;
  - the world's mean norm;
  - the share of countries in crisis;
  - recent 10-25% equity falls;
  - years since the country's last crisis and since the world's last wave.
- **Targets.** A systemic crisis starting within two years; a real equity
  fall of 25% within two years.

**Univariate AUC by era** (`data/runs/macro-7`):

| input | crisis: 1870-1913 | 1919-38 | 1973-99 | 2000-20 | fall: 1870-1913 | 1919-38 | 1950-72 | 1973-99 | 2000-20 |
|---|---|---|---|---|---|---|---|---|---|
| credit | 0.69 | 0.64 | 0.73 | 0.70 | 0.58 | 0.50 | 0.59 | 0.57 | 0.60 |
| equity prices | 0.50 | 0.41 | 0.49 | 0.71 | 0.59 | 0.48 | 0.57 | 0.46 | 0.79 |
| short rate | 0.53 | 0.63 | 0.60 | 0.90 | 0.61 | 0.64 | 0.46 | 0.71 | 0.79 |
| norm | 0.61 | 0.54 | 0.59 | 0.69 | 0.63 | 0.53 | 0.68 | 0.68 | 0.63 |
| breadth | 0.64 | 0.53 | 0.55 | 0.71 | 0.61 | 0.52 | 0.67 | 0.66 | 0.66 |
| resonance | 0.63 | 0.57 | 0.60 | 0.75 | 0.67 | 0.54 | 0.66 | 0.70 | 0.64 |
| world norm | 0.53 | 0.57 | 0.56 | 0.78 | 0.58 | 0.49 | 0.65 | 0.73 | 0.80 |
| years since world wave | 0.57 | 0.77 | 0.78 | 0.98 | 0.58 | 0.79 | 0.67 | 0.56 | 0.78 |

(1950-72 has one crisis and is left out for crises.)

**Every invariant points the same way in every era, for both targets.** Of
the specific channels, credit does for crises, the Schularick-Taylor result.
The others flip:
- equity prices;
- bank leverage;
- the exchange rate;
- unemployment.

**Fitted on four eras, scored on the fifth** (AUC within the held-out era,
prior 10):

| inputs | crisis: 1870-1913 | 1919-38 | 1973-99 | 2000-20 | fall: 1870-1913 | 1919-38 | 1950-72 | 1973-99 | 2000-20 | fall, pooled |
|---|---|---|---|---|---|---|---|---|---|---|
| specific (14 channels) | 0.59 | 0.56 | 0.73 | 0.60 | 0.62 | 0.55 | 0.59 | 0.61 | 0.66 | 0.52 |
| credit alone | 0.63 | 0.64 | 0.73 | 0.70 | 0.58 | 0.51 | 0.59 | 0.57 | 0.60 | 0.40 |
| invariant | 0.45 | 0.48 | 0.49 | 0.35 | 0.58 | 0.63 | 0.63 | 0.75 | 0.70 | **0.63** |
| displacement at three scales | 0.56 | 0.49 | 0.59 | 0.74 | 0.57 | 0.54 | 0.66 | 0.69 | 0.68 | 0.60 |

**Equity falls.** Invariant inputs carry from one era to another better
than the channels: pooled out-of-era AUC is 0.63 against 0.52.

**Banking crises.** Credit is the portable signal. The full invariant set
does worse: its global and memory terms move with time, not with countries,
so fitted on other eras they get the level wrong.

**Real time**, refitting each year on the outcomes known by then, from 1950:

| inputs | crisis AUC | crisis within years | fall AUC | fall within years |
|---|---|---|---|---|
| specific | 0.50 | 0.70 | 0.64 | 0.57 |
| credit + displacement at three scales | 0.57 | 0.65 | 0.63 | 0.51 |
| credit + scales + memory | 0.42 | 0.55 | 0.62 | 0.55 |

"Within years" ranks countries in the same year only. The pooled AUC also
asks when the waves come, and there every model is near chance for crises.

**The timing of global waves is the unsolved part.**
- Years since the last world wave is the most stable invariant found
  (0.57-0.98 in every era).
- In a fitted model it inverts.
- With about six global waves in 150 years, a logistic term can't learn
  "a wave spreads, then memory protects, then memory fades".
- That shape belongs to the process: self-excitation with a refractory
  period and slow build-up. It needs a process model, like the Hawkes
  machinery of the event layer, not an input.

## What the Chinese literature adds

China has had equity crashes (Shanghai fell about 70% in 2007-08 and 45% in
2015). It has had no systemic banking crisis or recession since reform. The
research behind that record is little read in the West, and several strands
bear on the gaps above.

- **跨周期调节, cross-cycle adjustment.**
  - Official since 2020-21, alongside 逆周期调节 (counter-cyclical
    adjustment).
  - Policy is set against the financial cycle and the long cycle, not only
    the business cycle.
  - The literature on 金融周期 measures China's financial cycle from credit
    and house prices, as the BIS does. It finds it longer and larger than
    the business cycle, with value as an early warning; one securities
    house's measure peaked in 2016.
  - This is the stabilizing loop the Macrohistory tests find missing:
    macroprudential policy (宏观审慎), state-owned banks and capital
    controls act on the stretch before the trigger arrives.
- **Systemic risk indices weighted by co-movement (系统性金融风险).**
  - China's version of the CISS uses 13 indicators across bond, bank,
    equity and currency markets.
  - It weights them by their time-varying correlations, so risk counts
    more when markets move together.
  - Synchrony across dimensions is a candidate invariant not yet tested
    here.
- **周金涛's nesting of cycles (周期嵌套, 共振).** He nests four cycles,
  each inside the longer one:
  - Kitchin (inventory, 3-4 years);
  - Juglar (equipment, about 10);
  - Kuznets (property, about 20);
  - Kondratiev (about 60).

  Crises come when their phases coincide. The resonance test here is that
  idea without calendars: displaced at 5, 10 and 20 years at once. It is
  era-stable. His dated forecasts for 2018-19 did not come true, which
  argues for the structure without fixed periods.
- **钱学森, open complex giant systems (开放的复杂巨系统).**
  - Economies are systems that reductionist models can't close.
  - His method, 从定性到定量综合集成法 ("from qualitative to quantitative
    meta-synthesis"), combines expert judgment with models in a structured
    loop.
  - For this project it is a design rule: the crash audit above is the
    qualitative half, and the dimensions it names define the quantitative
    half.
- **陈平, endogenous cycles.**
  - Macro fluctuation is generated within the economy, in financial and
    industrial structure, not by outside noise.
  - Time-frequency analysis of the S&P finds persistent cycles ("color
    chaos").
  - His metabolic growth theory has technologies compete as logistic
    populations, in "technology wavelets".
  - All of this argues for measuring state at several frequencies.
- **Profitability in China.**
  - 高峰's work on accumulation and the general rate of profit, and a PERI
    working paper reading China's "new normal" as a falling profit rate
    (stagnant rate of surplus value, rising value composition of capital).
  - 齐昊 classifies the Marxian explanations of low profitability: profit
    squeeze, monopoly capital, decay of the social structure of
    accumulation, and crisis of Fordism.

Several papers I looked for (陶玲 and 朱迎's index, 马勇 on financial cycles)
did not come up in search. Their claims are not used here.

## What to build next

1. **Synchrony.** Test correlation across dimensions and across countries,
   following the CISS weighting.
2. **Stabilizing loops.** Add the strength of the balancing loops as a
   dimension:
   - capital account openness (Chinn-Ito from 1970, Quinn from 1950);
   - reserves;
   - fiscal and monetary space.
3. **Global waves as a process.** Model the global crisis process as
   self-exciting with a refractory period and slow build-up, and feed its
   intensity to the hazards. That is where memory and timing belong.
4. **Discovered regimes.** Use a latent state (a hidden Markov model whose
   transitions depend on displacement and memory), in place of
   hand-written regime terms or discounting.
5. **The trigger as an invariant.** Measure the largest adverse change in
   any dimension: displacement of the flows, beside displacement of the
   stocks.
6. **Fill the US gaps found in the audit:**
   - oil (FRED from 1946);
   - geopolitical risk (Caldara-Iacoviello, monthly from 1900);
   - margin debt;
   - the dollar;
   - foreign markets.

## Step 1: global waves as a self-exciting process (`bnm.waves`)

Within JST the process cannot be learned in real time.
- In-sample, over 1900-2020, a logistic hazard on world crisis kernels
  learns a clear shape. The weights are +4.9 at 1 year (contagion), -2.0 at
  3 (protection), +2.1 at 10 and -1.7 at 30. In-sample AUC is 0.70.
- In real time it inverts (timing AUC 0.34). Before each wave it has seen
  only one or two.

**More waves.** The Reinhart-Rogoff chronology (HBS BFFS) gives 303
banking crisis starts in 70 countries since 1800, with waves in:
- 1814;
- 1857;
- 1890-93;
- 1907;
- 1914;
- 1921-23;
- 1929-34;
- the 1980s-90s;
- 2008.

`bnm.waves` fits world kernels there, for banking crises and for currency,
default and inflation crises, refit each year.

Timing of waves (three or more starts within two years, `data/runs/waves-1`):

| inputs | 1860-1913 | 1919-49 | 1950-79 | 1980-2014 | 1950-2014 |
|---|---|---|---|---|---|
| world banking kernels | 0.58 | 0.34 | 0.49 | 0.62 | 0.83 |
| world, all crisis kinds | 0.53 | 0.60 | 0.43 | 0.54 | 0.68 |
| all, with own history | 0.56 | 0.64 | 0.43 | 0.52 | 0.68 |

Most of the 1950-2014 figure separates the quiet Bretton Woods decades from
the crisis-heavy 1980s and 90s. Within eras the skill is weak, and inverted
in 1919-49 for the banking kernels.

**Fed to the Macrohistory hazards** as the world intensity, from the model
fixed as output before the results (`data/runs/macro-10`, real time, rows
from 1900):

| crisis, inputs | 1919-38 | 1950-72 | 1973-99 | 2000-20 | from 1950 | within years | timing |
|---|---|---|---|---|---|---|---|
| credit + displacement at three scales | 0.57 | 0.78 | 0.63 | 0.65 | 0.48 | 0.67 | 0.46 |
| + world intensity | 0.60 | 0.42 | 0.65 | 0.54 | **0.58** | 0.68 | **0.66** |
| world intensity alone | 0.50 | 0.11 | 0.54 | 0.02 | 0.42 | 0.50 | 0.55 |

The intensity improves timing from 1950. But it reads 2008 as low risk: it
learned that risk persists after the 1980s-90s emerging-market waves, not
that it builds in the calm.

## Step 2: the stabilizing loops

**Proxies from JST:**
- the real rate (financial repression);
- non-core bank funding;
- loans to deposits;
- a peg;
- the short rate (room to cut);
- public debt.

**Direct measures:**
- Chinn-Ito capital account openness (from 1970);
- the Reinhart-Rogoff gold standard flag.

| crisis, inputs | 1919-38 | 1950-72 | 1973-99 | 2000-20 | from 1950 | within | timing |
|---|---|---|---|---|---|---|---|
| JST stabilizer proxies | 0.64 | 0.52 | 0.64 | 0.31 | 0.46 | 0.43 | 0.46 |
| openness, gold | 0.24 | 0.02 | 0.40 | 0.08 | 0.19 | 0.53 | 0.07 |
| credit + scales + intensity + openness, gold | 0.57 | 0.34 | 0.69 | 0.32 | 0.48 | 0.63 | 0.50 |

**This fails.** The proxies change meaning between eras. Negative real
rates meant repression under capital controls in 1950-72 and cheap fuel for
credit in the 2000s. Openness and gold, nearly the same across countries in
any year, invert.

## What steps 1 and 2 show

Each input that moves with time but is nearly the same across countries
inverts when fitted in real time:
- crisis memory;
- world kernels;
- regime flags;
- capital account openness.

Each takes its sign from the one or two regime switches seen before the
year it predicts. Inputs that rank countries within a year (credit,
displacement) carry from era to era.

**The timing of crises is limited by the number of global episodes**,
about a dozen in two centuries, not by the number of dimensions. Learning
when needs more episodes. The many small events at shorter timescales can
supply them, if the dynamics are similar across scales: learn the shape
there and rescale it to the large ones.

## Step 3: synchrony

**Inputs**
- Across dimensions: the mean absolute correlation of the dimensions'
  changes over ten years, a simple form of Kritzman's absorption ratio and
  of the CISS weighting.
- Across countries: the country's correlation with the world.

**Results** (`data/runs/macro-11`)
- **Direction.** Low synchrony across dimensions comes before crises in
  every era (AUC 0.16-0.45 for high synchrony). The dimensions decouple
  first, the opposite of the CISS premise.
- **In a model it fails.** Pooled over eras the relation reverses
  (Simpson's paradox: eras with more synchrony also had more crises), and
  the hazard gains nothing. Pooled AUC is 0.54 with synchrony against 0.58
  without.
- **Removing each year's cross-country mean from the inputs doesn't fix
  it.** It makes things worse: within-year ranking falls from 0.67 to 0.59
  (`data/runs/macro-12`). The raw stretches carry timing, because credit
  booms are global.

## Step 4: discovered regimes (scripts/regime-hmm.py)

**Model**
- A two-state Markov-switching model of US monthly returns, mean and
  variance switching (Hamilton 1989), from statsmodels' `MarkovRegression`
  (statsmodels 0.15.0).
- Refit each January on the months before it, and filtered forward with
  those parameters.
- The bear regime is the high-variance state of each fit.
- A second version drives the transitions by displacement (valuation,
  drawdown, volatility ratio, momentum) through `exog_tvtp` (Filardo 1994).

The filtered probability enters the drawdown hazard (`bnm.trader`, 20%
within 12 months, tuned 1950-89):

| inputs | tune | held out 1990- | 2000- | top fifth predicted / observed | hedge by probability, Sharpe |
|---|---|---|---|---|---|
| base | 0.64 | 0.69 | 0.68 | 0.24 / 0.24 | 0.65 |
| base + discovered regime | 0.72 | **0.76** | 0.77 | 0.30 / 0.28 | **0.71** |
| base + regime, transitions driven by displacement | 0.69 | 0.66 | 0.72 | | |
| base + hand-written regime terms (hindsight) | 0.71 | 0.79 | 0.80 | 0.31 / 0.24 | 0.70 |
| base + discovered + hand-written | 0.72 | 0.80 | 0.82 | 0.27 / 0.24 | 0.74 |

The 10-month rule's Sharpe is 0.69.

**What the regime gives**
- The discovered regime improves both the tuning and the held-out years. It
  comes close to the hand-written terms with nothing chosen on the held-out
  years.
- The hedge sized by its probability beats the trend rule at three of five
  sizings.
- Displacement-driven transitions overfit: Brier skill held out is -0.22.

**What it doesn't**
- The lower fifths are overestimated: 0.08-0.17 predicted against 0.02-0.07
  observed.
- 2007 is still missed (0.09-0.13).

## Step 5: the trigger as an invariant

**Inputs**
- Each dimension's one-year change, scored against its own past changes.
- The largest change, and the root mean square change.
- The stock's displacement times the flow's.

**Results** (`data/runs/macro-13`)
- All three are era-stable (5/5 for crises; stock times flow 5/5 for falls
  as well). Crises: 0.61, 0.53, 0.94, 0.65 and 0.64; falls 0.54 to 0.69.
- On its own the trigger ranks countries within a year well (0.65), but its
  timing inverts.
- Added to the best crisis model, within-year AUC goes from 0.68 to 0.69,
  and pooled is unchanged at 0.58.

## Step 6: the US shock dimensions the audit found missing (`bnm.us-shocks`)

**Inputs**
- Oil: WTI, FRED from 1946, deflated by CPI.
- Geopolitical risk: Caldara & Iacoviello's historical index, from 1900.
- Each enters as its stretch against its own previous ten years and as its
  change over twelve months, read a month late.

**Left out**
- Margin debt: there is no free series before FINRA's from 1997.
- The trade-weighted dollar: it starts in 1973, and the major-currency index
  ended in 2019.

**Era AUC** (`data/runs/regime-6`):

| input | 1930-49 | 1950-69 | 1970-89 | 1990- |
|---|---|---|---|---|
| oil, 12-month change | - | 0.82 | 0.82 | 0.71 |
| oil stretch | - | 0.42 | 0.49 | 0.76 |
| geopolitical risk, 12-month change | 0.54 | 0.61 | 0.50 | 0.53 |
| geopolitical risk stretch | 0.52 | 0.66 | 0.33 | 0.49 |
| discovered regime | - | 0.32 | 0.60 | 0.65 |

Only the oil change is stable before 1990. The audit that named these
dimensions includes the held-out crashes (1990, 2022). With all four shock
inputs the hazard reached held-out AUC 0.81 and Sharpe 0.81, but the
geopolitical and oil-stretch terms flip between eras, so that result
carries hindsight. It is reported, not used.

**The disciplined set:** the stable base, the discovered regime and the oil
change (20% within 12 months, tuned 1950-89):

| inputs | tune | held out 1990- | 2000- | Brier skill held out |
|---|---|---|---|---|
| base | 0.64 | 0.69 | 0.68 | 0.01 |
| base + oil change | 0.67 | 0.76 | 0.74 | 0.04 |
| base + discovered regime | 0.72 | 0.76 | 0.77 | 0.02 |
| **base + regime + oil change** | **0.79** | **0.77** | **0.81** | **0.04** (0.20 from 2000) |

**Trading, held out 1990 on** (10-month rule: Sharpe 0.69, maximum
drawdown 18%):

| strategy | Sharpe by sizing or threshold | beats the rule |
|---|---|---|
| hedge by probability (k = 1.5, 2, 3, 4, 6) | 0.70, 0.71, 0.72, 0.75, 0.73 | 5 of 5 |
| the rule unless risk is low | 0.69, 0.69, 0.69, 0.71, 0.74 | 2 of 5, never worse |
| model alone (1-4 × base) | 0.48, 0.67, 0.78, 0.61, 0.65, 0.72, 0.72 | 3 of 7 |

The hedge sized by probability, at the sizing chosen on 1950-89, has
Sharpe 0.70 and a maximum drawdown of 13%. It is the first result that
beats the trend rule at every setting. Its inputs are:
- a discovered regime;
- the stable base;
- the one shock input stable before 1990.

**Still wrong**
- The top fifth is overconfident (0.37 predicted, 0.27 observed), so the
  probabilities need recalibration before they are read as levels.
- 2007 is still missed (0.07-0.10).
- The warning before 2022 rose to 0.45.

Reading for August 2026: P(20% fall within 12 months) = 0.155, above 69% of
held-out months, against a base rate of 0.08. Held-out months in that
bucket saw falls 7% of the time.

## Where this leaves the epic

| step | result |
|---|---|
| 1 crisis process | world intensity improves JST crisis timing from 1950 (0.48 to 0.58) but misses 2008 |
| 2 stabilizing loops | fails: proxies change meaning, openness and gold invert in real time |
| 3 synchrony | fails in the hazard; low synchrony before crises in every era |
| 4 discovered regimes | works: US held-out 0.69 to 0.76, with nothing chosen on held-out years |
| 5 trigger | era-stable, marginal gain |
| 6 US shocks | the oil change is stable and helps; geopolitical risk does not |

**What generalizes**
- Inputs that rank units within a period: credit, displacement, trigger.
- Regimes inferred from the data's own dynamics, not written in.
- Shock flows that were stable before the test period.

**What does not**
- Anything that moves only with time and is learned from a handful of
  regime switches.

The open item is the one that problem points to: learning crisis dynamics
on the many small events and rescaling them to the large ones
(bnm-t5b.16.11), and recalibration (bnm-enb.12).

## The gaps revisited: invariant forms of the loops and of synchrony

**The research** points to one error behind both failures: they were
measured as levels.
- Kritzman et al. find that changes in the absorption ratio, not its level,
  mark turning points.
- The early-warning literature (Scheffer et al. 2009; Dakos et al. 2010;
  Veraart et al. 2012) reads a weakening restoring force from rising
  autocorrelation and rising spatial correlation, each against the system's
  own past.
- In those terms, the stabilizing loop is the restoring force, whatever
  instrument provides it. Naming the instruments (capital controls, gold,
  repression) was the overspecificity.

**Annual (Macrohistory, `bnm.macrohistory/with-shifts`, `data/runs/macro-14`).**
The inputs:
- persistence, the mean lag-1 autocorrelation of the dimensions' stretches
  over 15 years, and its rise against the previous ten;
- the rise in synchrony across dimensions and with the world;
- the rise in variability across all dimensions;
- the opening of the capital account over five years;
- the real rate's stretch.

None is stable across the eras with crises. The persistence rise is the
closest (0.52, 0.56, 0.65, 0.66). Added to the best crisis model, timing
improves (0.66 to 0.74) and within-year ranking falls (0.68 to 0.59).
Fifteen annual points per estimate is too few: the literature's caveat is
that these signals need long, high-frequency records.

(A scoring bug fixed along the way: era AUCs now need five positives.
1950-72 has one crisis and no longer counts in the crisis tables; the
invariants named above stay stable in every era with crises.)

**Monthly (`bnm.us-loops`, `data/runs/regime-7`).** The inputs, over nine
US monthly series (production, unemployment, payrolls, prices, bill and
bond rates, default spread, real oil, the market):
- the same coupling;
- its shift (12 months against 60);
- the persistence of short-run deviations, and its rise;
- Kritzman's coupling across the 22 stock markets.

| input | 1930-49 | 1950-69 | 1970-89 | 1990- |
|---|---|---|---|---|
| macro synchrony | 0.56 | 0.26 | 0.37 | 0.27 |
| macro synchrony shift | 0.51 | 0.59 | 0.45 | 0.63 |
| persistence rise (critical slowing down) | - | 0.64 | 0.65 | 0.40 |
| market synchrony shift (Kritzman) | - | - | 0.41 | 0.66 |

**What holds**
- **Low synchrony comes before falls**, in both post-war eras and held out,
  as it came before crises in every era of the Macrohistory data. Two
  independent datasets agree: before a break, a system's dimensions
  decouple. It reads as finance drifting from production and employment:
  Borio's financial cycle running apart from the business cycle, fictitious
  capital decoupling from real accumulation.
- **Critical slowing down flips held out**, as Guttal et al. found for
  stock markets. Crashes look like growing shocks hitting a stretched
  system, not a restoring force failing.

**In the US hazard decoupling adds nothing.** Held-out AUC is 0.767 against
0.771: what it carries, the discovered regime and valuation already carry.

## Small falls teach about big ones (`bnm.scales`, bnm-t5b.16.11)

**Setup**
- Three scales, each a fall of about 1.2 annual standard deviations scaled
  by the square root of the horizon: 5% within 4 weeks, 10% within 13
  weeks, 20% within 52 weeks.
- Inputs are scale-free, with windows equal to the horizon and in
  volatility units: momentum, drawdown, distance below the average,
  deceleration and the volatility ratio.
- One fit on 1930-89 per scale; scored on 1990 on.

| fitted on / scored | 5% in 4 weeks | 10% in 13 weeks | 20% in 52 weeks |
|---|---|---|---|
| 5% in 4 weeks | 0.638 | **0.691** | **0.662** |
| 10% in 13 weeks | 0.605 | 0.643 | 0.487 |
| 20% in 52 weeks | 0.498 | 0.506 | 0.630 |
| pooled over scales | 0.632 | 0.683 | 0.635 |

**What it shows**
- Fitted on small falls alone, the hazard ranks the held-out year-long
  bear markets better than a hazard fitted on them.
- The pattern before a fall is alike across scales once time is measured
  in the fall's own horizon: the Omori and productivity laws across
  magnitudes (Petersen et al. 2010).
- This is evidence against treating large crashes as a different
  population (Sornette's dragon kings), at least for ranking.
- The reverse fails: big-fall dynamics, learned from few episodes, don't
  transfer down.

**In real time**
- Refit each January on resolved small falls, scored with the large-scale
  inputs.
- The score is era-stable (0.66 and 0.54 before 1990, 0.65 after).
- Added to the disciplined US set (`data/runs/scale8bf8eb`): held-out AUC
  0.771 to **0.808** (0.835 from 2000), Brier skill 0.04 to 0.05.
- The hedge sized by probability: 0.64, 0.69, 0.72, 0.76 and 0.76 across
  sizings, against 0.69 for the 10-month rule. The sizing chosen on
  1950-89 is the worst one.

## Recalibration (bnm-enb.12)

**Event model** (`bnm.calibrate`). The binned calibrator now pools adjacent
violators across bins (`isotonic`, ported from scikit-learn), so a higher
forecast is never calibrated lower: the CORP fit (Dimitriadis, Gneiting &
Jordan 2021). The stored format is unchanged.

Replayed over v3-q (`data/runs/v3-q-iso` against `data/runs/v3-q`), the
calibrated Brier skill improves in every country stratum, by 0.1 to 0.8
points. Examples:
- violence, all: +3.9% to +4.0%;
- protest, low: +6.2% to +6.4%;
- coercion, active: +1.5% to +1.8%.

**PIT.** Each row now carries the non-randomized PIT interval of its
outcome (`pit-bounds`; Czado, Gneiting & Held 2009). The report prints the
PIT histogram in tenths per level and channel (`pit-histogram`). The
own-history CRPS benchmark (VIEWS-style, the last six periods as the
empirical distribution) was already reported beside the model's.

**Drawdown hazard** (`bnm.trader`). Each January the forecasts already
resolved are fitted, and the fit maps the year's forecasts:

| recalibration, held out 1990- | Brier skill | AUC | top fifth predicted / observed |
|---|---|---|---|
| none | 0.050 | 0.808 | 0.41 / 0.29 |
| isotonic | 0.079 | 0.666 | |
| logistic on the logit (Cox 1958) | **0.112** | 0.786 | 0.20 / 0.28 |

The isotonic fit's flat steps tie forecasts and cost the ranking, the
plateau problem the calibration literature warns of for rare events. The
logistic fit is strictly monotone and keeps most of it, so it is the one
used.

Reading for August 2026, recalibrated: P(20% fall within 12 months) =
0.107, against a base rate of 0.08 (raw 0.189).

**PIT check** (`data/runs/pit-check`, 2008-09).

Country rows, PIT in tenths, times ten:

| channel | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| violence | 1.1 | 1.0 | 1.1 | 1.0 | 1.0 | 0.9 | 0.9 | 0.8 | 0.6 | 1.3 |
| protest | 0.9 | 1.1 | 1.3 | 1.2 | 1.1 | 1.0 | 0.9 | 0.7 | 0.6 | 1.1 |

**Reading**
- Outcomes fall too often in the lower half and too rarely in tenths 8-9:
  the central forecast is slightly high.
- The top tenth has excess mass: the upper tail is too thin.
- 80% coverage is 0.74-0.82.

The spread recalibration widens symmetrically in log space. A skewed
widening (more upper tail) and a small downward shift of the median are
what the histogram asks for.
