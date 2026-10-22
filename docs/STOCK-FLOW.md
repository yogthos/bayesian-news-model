# The stock-and-flow base (research and design, October 2026)

The project's point is to find the driving factors behind the symptoms.
Crises, coups and protest are symptoms; the simulation should discover
them as products of underlying trends. The event lattice and its links
(docs/WORLD-MODEL.md) watch symptoms. This layer sits beneath them: the
material base (wuli) as sectors with stocks and flows, connected by
influences, from which events emerge as threshold crossings of stocks.

## What the store already holds

Loose national totals and prices, not yet wired together:
- energy: oil, gas and coal production, consumption, imports, exports,
  crude stocks, import dependence, prices;
- food: crop production and yields, food prices;
- metals: copper, aluminium, iron ore and nickel prices;
- economy: industrial production, sector shares, capital-output ratio;
- technology: R&D, patents, research output by field, semiconductor
  production.

Missing: the wiring (how much each sector draws from the others) and the
stocks the flows fill and drain.

## The mathematics (system dynamics; Forrester 1961, Sterman 2000)

- A stock integrates its flows: dS/dt = in - out; discretely S[t+1] =
  S[t] + dt (in - out).
- Material delay, order n: n stocks in series, each draining at L_i /
  (tau/n); conserves what passes; step response Erlang-n (variance
  tau^2/n). DELAY1: dL/dt = in - L/tau, out = L/tau.
- Perception (SMOOTH): dP/dt = (X - P)/tau, exponential smoothing; trend:
  P smoothed over tau_p, reference R = P smoothed over tau_h, trend = (P -
  R)/(R tau_h).
- Goal seeking (balancing): flow = (S* - S)/AT. Reinforcing: dS/dt = r S.
  Limits: dS/dt = r S f(S/K), f a table with f(0) = 1, f(1) = 0.
- Decisions by anchoring and adjustment (Sterman 1989): O = max(0, L^ +
  a_S (S* - S) + a_SL (SL* - SL)), L^ smoothed demand; players
  under-weight the supply line (beta = a_SL/a_S < 1), which makes the
  oscillations.
- Price from coverage (Meadows 1970): P = P_ref f(coverage / desired
  coverage); capacity investment on expected profit through a DELAY3
  construction lag gives commodity cycles whose period scales with the
  delays.
- Nonlinear effects as table functions y = f(x / x_ref), f(1) = 1,
  piecewise linear (DYNAMO's TABHL).

Models worth taking structure from:
- World3 (Meadows et al. 1972, 2004): industrial output IO = IC (1 -
  FCAOR) CUF / ICOR; dIC/dt = IO FIOAI - IC/ALIC; FCAOR = f(remaining
  resource fraction), the cost of obtaining resources rising as they
  deplete; food F = LY AL LFH (1 - PL); life expectancy as multipliers.
- Earth4All (Dixson-Decleve et al. 2022): inventory dINV/dt = OUTP - DEL;
  social tension STE = 1 + PESTF (ORP - AP), observed progress in
  wellbeing (smoothed) against expected progress (Davies' J-curve as an
  equation); social trust smoothed toward a level set by inequality and
  public spending; tension and trust set the reform delay. A 2024 Club of
  Rome review finds it driven by preset forcing functions, not mass or
  energy balanced, unstable without them.
- Keen-Goodwin (Keen 1995; Grasselli & Costa Lima 2012): wage share w,
  employment l, debt ratio d; profit share pi = 1 - w - r d; dl/dt = l
  (k(pi)/nu - alpha - beta - delta); dd/dt = d (r - k(pi)/nu + delta) +
  k(pi) - (1 - w); a good equilibrium and a bad one with d unbounded.
- Structural-demographic (Goldstone 1991; Turchin 2013; written out by
  Stachowiak & Pasek 2024): Psi = MMP EMP SFD, mass mobilization (w^-1
  N_urb A_20-29), elite mobilization (e / epsilon), state fiscal distress
  (debt x distrust). EMP grows as e^2 and dominates; the elite parameter
  spans 50x without justification.
- Commodity cycles (Meadows 1970), long wave (Sterman 1985: capital
  self-ordering), FREE (Fiddaman 1997: energy embodied in capital
  vintages), Threshold 21 (national models for 40+ countries, unvalidated
  out of sample).

Meadows' archetypes as structures, each a motif to detect in a fitted
model and a candidate tension for the anatomy:
- policy resistance: dS/dt = sum_i (G_i - S)/tau_i, actors with different
  goals on one stock;
- tragedy of the commons: dR/dt = regen(R) - sum_i h_i, each harvest grown
  on private gain, the feedback from R delayed or missing;
- drift to low performance: dG/dt = min(0, S - G)/tau_G, the goal eroding
  toward performance;
- escalation: dA/dt = (k B - A)/tau, dB/dt = (k A - B)/tau, runaway for k >
  1;
- success to the successful: A's share = f(A/(A + B)), f convex;
- shifting the burden: a quick fix q Gap acts fast and erodes the
  capacity Cap for the fundamental fix phi Cap Gap;
- rule beating and seeking the wrong goal: the loop closes on a proxy.

Leverage points (Meadows 1999), weakest to strongest, as model elements:
parameters; buffer sizes (stock against flow); stock-flow structure;
delays; balancing loop strength; reinforcing loop gain; information flows
(who perceives which stock); rules (decision functions); self-
organization (structure that changes); goals (S*); paradigm (the model's
boundary).

## Production networks (how sectors pass shocks)

- With Cobb-Douglas inputs Hulten's theorem holds: a sector matters as its
  sales share, and energy's small share predicts small effects (Acemoglu
  et al. 2012). With complementarities the response is concave and the
  1970s oil shocks hit about three times harder (Baqaee & Farhi 2019).
- Pure Leontief overpredicts collapses: Germany's gas use fell about 20%
  in 2022 (industry 26%), energy-intensive output fell sharply, GDP dipped
  mildly (Moll, Schularick & Zachmann 2023).
- What worked: Leontief only on inputs rated critical, half weight on
  important ones, inventories in days of use, imports never binding;
  forecast UK 2020 Q2 at -21.5% against -22.1% (Pichler et al. 2022).
- Elasticities from data: regress changes in log input cost shares on
  changes in log relative prices (Atalay 2017, below 0.2 for intermediates);
  near-Leontief for specific imported inputs in the short run.
- Food: price spikes when stocks-to-use nears its floor, convex
  (competitive storage; Wright 2011; Bobenrieth, Wright & Zeng 2013); harvest
  shocks cascade along the trade network (Puma et al. 2015).
- Technology diffuses slowly (Comin & Hobijn 2010: adoption lags ~45
  years): a productivity inflow, a logistic per technology.

## Data (free for local research unless noted)

- input-output: Long-run WIOD 1965-2000 (25 countries + rest of world, 23
  sectors, current and previous-year prices, CC BY), WIOD 2016 (43
  countries, 56 sectors, 2000-2014), OECD ICIO (~80 economies, ~45
  industries, 1995-2022), FIGARO (EU, 2010-), EXIOBASE 3.10 (physical
  extensions; since 3.9 derived coefficients may not be published);
  avoid Eora and GTAP (paid or restricted);
- sectors: UN main aggregates (200+ countries, 1970-, 7 sectors), ETD
  (51 developing, 12 sectors, 1990-2018), KLEMS, UNIDO INDSTAT2, Penn World
  Table 11 (capital stocks, 1950-2023);
- physical: FAOSTAT (1961-, production, trade, balances), Energy Institute
  Statistical Review (1965-), EIA, USGS minerals; prices: World Bank Pink
  Sheet (monthly, 1960-); avoid the full IEA balances.
- Causally, at date t only the table for t - 3 is known.

## The record, and the pitfalls

Formal out-of-sample tests are rare. World3 was compared to data by eye
(Herrington 2021: the "double resources" and "comprehensive technology"
runs fit best) and recalibrated without changing its story (Nebel et al.
2023); the Austrian agent-based model's gains over a VAR are not
significant (Poledna et al. 2023); T21 and Earth4All have none. Pitfalls:
hand calibration and forcing functions; weak identification of delays and
table points; pattern matching taken for forecasting; structure fitted to
one country assumed everywhere; multiplicative indices one term
dominates. Our discipline (test first, designs on design years, tests
registered before scoring, placebos) is what the field lacks.

## Code to hand (data/raw/models, read, not run)

- WorldDynamics.jl (MIT): World1-3 and World3-03, every equation tagged
  with its line in the book's appendix; its test carries Vensim's
  trajectories of five World3-03 variables every half year 1900-2100, a
  reference a port must track. Each sector runs alone with its neighbours
  as recorded trajectories: the way to test sectors one at a time.
- Earth4All.jl (MIT): twelve sectors, the Vensim source, and Vensim's
  output for every sector in two scenarios.
- pyworld3 (CeCILL, GPL-like: read and cross-check, do not copy): World3,
  its 46 table functions as JSON.

## What to adopt

1. Stocks with explicit accounting, checked for balance and units (each
   flow leaves one stock and enters another).
2. Delays: material (DELAYN) for pipelines (capacity under construction,
   crops, credit to investment), perceptions (SMOOTH) for expectations.
3. Decision rules by anchoring and adjustment, the supply-line weight
   estimated per country, not assumed.
4. Prices from coverage ratios; commodity cycles from the delays.
5. Production: Leontief on critical inputs, substitution elsewhere,
   inventories as buffers; elasticities estimated from the WIOD price
   tables on years before each shock.
6. Depletion cost (World3's FCAOR) and the food stocks-to-use floor as
   table functions with priors on their points.
7. A macro-financial core: wage share, employment, debt ratio (Keen).
8. Social tension as observed against expected progress (Earth4All,
   Davies) and structural-demographic stress (Goldstone, Turchin), with
   components measured where we can and weights estimated.
9. Archetypes as motifs and tensions; leverage-point classes on every
   parameter, sensitivities reported by class.
10. Estimation by state-space filtering (the Kalman filter, as Peterson's
    FIMLOF) or Bayesian sampling, never by hand; the event lattice's
    dynamic models already learn this way.
11. Validation: Barlas' structure tests (extreme conditions, dimensions,
    boundary), the World3 and Earth4All reference trajectories as golden
    tests of the engine, and out-of-sample scoring as for every trial.

## Plan

1. The engine (bnm.stockflow): stocks, flows, DELAYN, SMOOTH, table
   functions, Euler steps; tested against WorldDynamics' World3-03
   reference trajectories (a port of World3-03 as the engine's first
   model, sector by sector, each against its recorded neighbours).
2. The pilot chain: energy (oil, gas, coal, electricity) -> energy-
   intensive industry (metals, chemicals, minerals, refining, transport)
   -> manufacturing, construction, services -> GDP, on the Long-run WIOD
   spliced to WIOD 2016 and OECD ICIO, the tables lagged three years;
   elasticities estimated before each shock.
3. Its test, registered before scoring: given the observed energy path,
   each sector's and country's output one to three years ahead after 1973,
   1979, 1990, 2008 and 2022, against AR(1) and the Hulten benchmark,
   leaving one shock out at a time; critical-input flags against uniform
   substitution.
4. Then the stocks it produces (output gaps, input-cost squeezes, coverage)
   become the event lattice's gauges, and events become its threshold
   crossings.
