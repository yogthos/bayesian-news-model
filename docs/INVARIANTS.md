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
