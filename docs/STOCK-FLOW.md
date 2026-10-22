# The stock-and-flow base (research and design, October 2026)

The project's point is to find the driving factors behind the symptoms.
Crises, coups and protest are symptoms; the simulation should discover
them as products of underlying trends. The event lattice and its links
(docs/WORLD-MODEL.md) watch symptoms. This layer sits beneath them: the
material base (wuli) as sectors with stocks and flows, connected by
influences, from which events emerge as threshold crossings of stocks.

## What stays: stocks and flows on the lattice

This layer adds fidelity; it does not replace what works. Each sector and
stock is a node of the lattice, per place (the place tree) and time scale
(quarter, year, half-decade), on the dimension axis beside the event
kinds. Its flows are dynamic Bayesian models learned from what is known at
each origin, so the delays, adjustment times and table points that system
dynamics sets by hand are estimated, pooled up the place tree. Nodes still
feed one another: states down, strain up, errors across, a coarser
forecast carried down as an offset. A stock's imbalances are its
contradictions: coverage against desired coverage, debt service against
income, observed against expected progress, credit against wages; strain
accumulates and resolves at a leap (a crisis), and the principal
contradiction may name an archetype. Selection pressure decides what
enters a forecast: model averaging adopts a stock-flow input only where
the node forecasts better with it, in a nested comparison, checked against
a placebo. Every piece is designed on the design years and tested as
registered. The checklist is docs/PRINCIPLES.md (epic bnm-62z).

## The material base: where things are

People need food, homes and industry need energy, industry needs ores:
the economy rests on where resources lie and where they are processed,
and that geography sets supply chains, dependencies and what is fought
over. Three tracked dimensions, each stocks and edges of this layer:
- endowments: mineral deposits and reserves, energy reserves and fields,
  arable land and its suitability, water; stocks that deplete and are
  discovered, located by country and cell (bead bnm-62z.19);
- processing capacity: refineries, smelters, rare-earth separation, steel
  mills, fertilizer, cement, power plants, fabs; located plants with
  capacities and dates, so capacity is a stock filled by construction and
  drained by retirement, and its concentration a vulnerability (bnm-62z.20);
- resource flows: bilateral trade in raw and processed goods, who
  depends on whom for each input, the edges shocks travel along
  (bnm-62z.21).
A sector's output is bounded by its inputs, which come from endowments
through processing and trade; the conflict kinds read the lootable and
contested ones (Ross 2004; Lujala et al. 2005: diamonds and oil fields).

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

### Material base sources (scripts/material.py, tools/bnm/material.clj)

Written to data/derived/material/; each source's licence and what it
feeds:

| source | licence, attribution | feeds |
|---|---|---|
| Our World in Data grapher: oil, gas, coal proved reserves | OWID's processing CC BY 4.0 ("Our World in Data"); oil and coal from the Energy Institute Statistical Review, under EI's own terms (free use with attribution, not CC BY); gas reserves are EIA's (public domain) | endowments.csv |
| FAOSTAT land use (Inputs_LandUse) | CC BY 4.0, "FAO. FAOSTAT" | endowments.csv |
| FAOSTAT detailed trade matrix | CC BY 4.0, "FAO. FAOSTAT" | cereal-trade.csv, cereal-trade-coverage.csv |
| EIA international energy data (INTL bulk) | US government work, public domain; credit "U.S. Energy Information Administration" | capacity.csv (power_*) |
| WRI Global Power Plant Database v1.3 | CC BY 4.0, "Global Energy Observatory, Google, KTH Royal Institute of Technology in Stockholm, Enipedia, World Resources Institute. 2019. Global Power Plant Database. Published on Resource Watch and Google Earth Engine" (README citation) | capacity.csv (plants_*), plant-cells.csv, concentration.csv |
| OWID energy data (owid-energy-data.csv) | OWID CC BY 4.0; production from the Energy Institute (EI terms, as above) | production.csv |

How they may be read:
- every value is as published now (revised); readers lag it, a year at
  least, trade 1-2 years;
- the power plant database is a snapshot (capacity data mostly
  2017-2019, published 2021) with no retired plants and a third of the
  world's capacity undated (plants_undated_*): its yearly plants_* series
  is survivors only. Never read it causally before 2021; it gives the
  fuel split and the concentration (plant-cells.csv, concentration.csv,
  vintage 2021) at the snapshot, and history comes from EIA's power_*;
- EIA's capacity series nest: total = fossil + nuclear + renewable +
  pumped storage; renewable = hydro + non-hydro; non-hydro = wind + solar
  + geothermal + biomass + tide; sum leaves only;
- cereal trade is the importer's report, else (for that importer, year
  and item) the exporters' mirror ("mirror_pair" fills a pair a
  reporting importer left out; an importer's own figures stand even when
  exporters report more); where the coverage file says "none" a
  flow is unknown, not zero. Rice is milled equivalent, wheat includes
  flour as grain (x 1.37);
- historical entities keep their own codes (succession.csv: SUN, YUG,
  CSK, SCG, SDF for Sudan before 2011, BLX, ANT, ETP, DDR, DEW); Kosovo is
  XKX everywhere; regions and the world are dropped.

Not yet held (beads bnm-62z.19-.21): mineral reserves and deposits (USGS
MCS/MRDS, BGS), uranium, oil and gas fields (PRIO PETRODATA), GAEZ
suitability, water (WRI Aqueduct), lootable resources (DIADATA, GEMDATA,
PRIO-GRID); refineries, LNG, smelters, rare-earth separation, steel,
fertilizer, cement, fabs, and GEM's trackers (CC BY 4.0) with
commissioning and retirement dates, which would give capacity as a stock
with flows; bilateral trade beyond cereals (BACI or UN Comtrade).

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

## Beyond pairs: what the catalogues show (October 2026)

bnm.hypergraph sets each triple of kinds' joint onsets (a country's
three-year window, the design years 1960-2002) against what their pairs
imply with no three-way term: the log-linear model [AB][BC][AC], fitted
by iterative proportional fitting. With the pair margins fixed the 2x2x2
table has one degree of freedom, so the test is G² on 1 df against that
fit and the effect size is the three-way log ratio of odds ratios (0.5
added to every cell when one is empty). The lift, observed over fitted
all-three count, is kept only as a description: the fit's margins contain
the triple's own count, which pulls it toward one (a table with no A-B
window without C has an infinite ratio of odds ratios and a lift of
1.08). "Joint" means joint onsets within the window: a crisis already
running when the window opens counts as absent.

Triples are kept when the fit expects five or more all-three windows (a
rule on the pairs, not on the triple's own count, which would favour
high lifts): 106 of them at the 1960 window phase. Both tails, G²
p < 0.05: one above (coups, growth collapses and violence surges, log
ratio 1.10) and six below, seven against 5.3 expected by chance; the
country-resampled 90% interval of the log ratio picks the same seven. After
Benjamini-Hochberg across the 106, one survives at q < 0.10: conflict
onsets, coups and war onsets, below (log ratio -1.57, q 0.02). That
triple holds a nested pair: most war onsets are conflict onsets that
escalated (65% of the time together in a window), so its three-way term
is near-definitional. Seven pairs nest at 50% or more on ten or more
joint onsets (banking crises and market crashes, violence surges with
war onsets, with protest surges and with conflict onsets, conflict and
war onsets, growth collapses and market crashes, currency and inflation
crises); without triples holding one, 71 remain, four reach p < 0.05
against 3.6 expected, and none survives the correction. At the 1961 and
1962 phases nothing survives either, the conflict-coup-war triple
included (6 and 5 at p < 0.05, about 5 expected). The three triples the
first pass flagged above one do not hold up: banking crises, growth
collapses and market crashes (p 0.16; it holds two nested pairs), and
conflict onsets, fiscal consolidations and inflation crises (expected
under five, now not tested); only coups, growth collapses and violence
surges remains nominal, and it does not survive the correction.

No triple survives multiplicity correction apart from a near-definitional
one that does not survive a shift of the window phase. That is not proof
the pairs carry everything: at this power (the median triple's standard
error) a three-way log ratio of odds ratios smaller than about 1.6, a
factor of five in the odds ratio, would go undetected. What can be said
is that no large polycrisis term is visible, so none is added to the
event lattice and the pairwise links (docs/WORLD-MODEL.md) remain the
joint structure. Recipes as hyperedges (critical inputs needed together)
remain the production side's form (bnm.hypergraph/availability): a
critical recipe is Leontief, a substitutable one the share-weighted mean
(perfect substitutes with no reallocation, the CES limit sigma ->
infinity), and a CES recipe with elasticity sigma lies between (Cobb-Douglas
at 1). A shock given at a node with a recipe caps it, and a cycle of
recipes is refused.

## Technology (bnm.technology, October 2026)

Diffusion of technologies per country as logistic stocks (WDI/ITU
internet, mobile, electricity access; the Cross-country Historical
Adoption of Technology dataset (Comin & Hobijn 2009, NBER w15319, free
for research use, data/raw/slow/chat) for telephones, cars, television,
radio, computers, tractors, electricity, 1880-2005), each country's lag
behind the frontier in years, a technology gap and the productivity
inflow (the gap closed), with R&D and patent intensity as leading flows;
causal (CHAT known two years after its data year, though it is a 2009
compilation). The aggregate gap breaks when CHAT ends (about 2006-10);
the inflow compares only technologies known in both years.

## Commodity cycles' prices (bnm.cycles, October 2026)

bnm.cycles-run reads each commodity's price from one source over its
whole history, so the level does not jump where sources meet: the World
Bank's Pink Sheet (CMO Historical Data Monthly, CC BY 4.0, downloaded
2026-10-22 to data/raw/market/pinksheet, converted by scripts/pinksheet.py)
for Brent, Dubai, coal, the three gas prices, aluminium, copper, iron ore,
nickel, wheat, maize, rice, soybeans and sugar, monthly from 1960 (coal
1970, Japan LNG 1977); FRED's WTISPLC for WTI (1946, longer than the Pink
Sheet's 1982); the IMF index from the store for all-index. Each month is
known the month after, as WTISPLC; the sheet is a current vintage, its
history rarely revised, and its October 2026 edition rounds to about three
significant figures (annual means within 3.4% of the January 2025
edition's full precision). Where the store's FRED series overlap it (1992
on; the run prints each pair), log annual means correlate at 0.998-1.000
with levels within 5% for crude, European and US gas, coal, aluminium,
copper, nickel and maize; Japan LNG correlates at 0.93, wheat 0.98 (the
sheet's HRW 14% above), soybeans 0.995 (13% above), rice 0.98, sugar 0.998
(the sheet in $/kg, the IMF in cents/lb). Iron ore correlates at 0.95: the
sheet's is the annual contract price in US cents per dry metric ton unit
before 2006 and the China spot price in $/t after, a seam that happens to
land near the spot level (2005 65, 2006 69), while the IMF's series in the
store jumps at its own seam (2007 37, 2009 80; the two agree from 2010).
Rows now start in 1968 for most commodities (coal 1978, Japan LNG 1985), so
the design years bnm.state-diag uses before 2003 see them. Caveats: the
sheet's Brent before 1976 and Dubai before 1985 are Saudi Light prices
(the two identical before 1976), and its US gas before Henry Hub trading
(about 1990) is an older US price it does not name. On the design years
(1968-2002, 35 rows each) the metals and grains keep some skill (dir-3y
against the next three years' real price change 0.23-0.46 for copper,
nickel, wheat, maize, rice, soybeans and sugar), crude and gas have none
(-0.28 to -0.31, the 1970s shocks), aluminium and iron ore little
(0.06-0.09), and the gap's reversion alone does as well or better almost
everywhere.
