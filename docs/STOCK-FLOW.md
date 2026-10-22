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

## Product graph (bnm.supply, October 2026)

Epic bnm-1pq, step 1: the material economy as products, what each is made
from, where, and who uses it. scripts/product-graph.py reads the EXIOBASE
hybrid MR-SUT 2011 (v3.3.18, CC BY 4.0: 200 products, 164 industries, 43
countries and 5 rest-of-world regions, Taiwan inside WA; goods in tonnes,
energy in TJ, services in M EUR) and the monetary product-by-product
tables v3.8.2 for 1995, 2011 and 2022 (CC BY-SA 4.0), and writes
data/derived/supply/: products.csv (unit and group: raw, energy, material,
manufactured, service), recipes.csv, production.csv, supply.csv (what each
industry makes), use.csv (by industry and final demand), trade.csv (the
MR tables' import shares, domestic included) and balances.csv.

A recipe row is an output made in a region, an input used there (all
origins together) and two numbers: phys, the input per unit of output in
the hybrid units, from the industry-technology assumption on the hybrid
supply and use tables (the inputs of the industries making the product,
each per unit of its output in the product's unit, weighted by its share
of the product's supply); and share_<year>, the monetary technical
coefficient (the input's value per unit of the output's value). An
industry making products in more than one unit (waste treatment and the
secondary material it recovers, incineration and its electricity, pulp
and its heat) has its inputs split between its units by the value of its
output in each; before that split each unit's recipes took the industry's
whole input, so upstream counted a recycler's power once through the
steel and again through the waste service. Every phys coefficient is
input-unit per output-unit, so along any path the units telescope and
upstream's sums are in the input's unit per unit of the product; nothing
adds tonnes to TJ. The two numbers do not always agree on routing: the
monetary tables send Germany's N fertiliser its gas as gas distribution
services and its plastics their naphtha as chemicals nec, where the
hybrid ones show 0.60 t of gas per t of fertiliser and 0.96 t of naphtha
per t of plastics. Valuing phys at monetary output over hybrid supply does
not mend it (the two releases disagree on products' sizes: a "value
share" of 9 for that gas), so the physical rows keep phys and the
monetary share side by side and the rule below reads both.

Shares above 1. The monetary A has 4 coefficients above 1 in 2011 (Korea's
lead-zinc ores buy 17 times their value in hotels) and about 20 region-products
whose inputs add to 1 or more of their value (tiny outputs, cattle in four
European countries): bnm.supply drops a share above 1 and scales an
output's shares to sum to 0.99 where they reach 1 (`supply/share-flags`:
4 dropped, 1,236 rows scaled), so neither reaches the critical test, the
CES weights or downstream's dependence. use.csv's value_share, valued at
the rough prices, comes out above 1 in 1,029 rows; there it is blank and
the ratio sits in value_rough, flagged, and `users` reports it as
:value-rough, not a share.

Balances: every good's supply equals its uses (intermediate and final, at
home and abroad) to rounding (median residual 1e-9; the 922 region-
products off by more than 1% are all services, mostly waste treatment).
Refineries: their refined products out against crude, NGL, other
hydrocarbons and refinery feedstocks in run 1-54% above (median +14%),
because refineries also take natural gas (gas processing for ethane and
LPG sits in the refinery industry), product backflows and blending
components; against every hydrocarbon, product, additive and biofuel they
take, output is 4.6% below input (median; -18% to +5%), the fuel and
losses a refinery burns, as it should be.

### Critical or substitutable (the rule)

An input is critical (Leontief: output capped by it, no substitute) when
it is a good, not a service, and either its value share in the output is
at least 5% (`supply/critical-share`) or it is a feedstock: a raw
material or fuel converted into a material or energy carrier at 0.1 per
unit or more (`supply/feedstock`), tonnes per tonne, or for a carrier in
TJ (electricity, heat) the fuel's heat per TJ at the IPCC 2006 default
net calorific values (`supply/ncv`: gas in gas-fired power, 1.3 TJ per TJ
in Germany, whose monetary share is 4e-6). Feedstocks are critical
whatever the monetary table makes of their value (gas in N fertiliser,
naphtha, ethane and LPG in plastics, crude in every refined product, iron
ore and coke in steel, coal in China's ammonia). The feedstock test is
kept to conversion because elsewhere the hybrid tonnage of a fuel is no
ingredient (the industry-technology split gives Britain's beverages
0.54 t of gas per t), and natural gas in a refinery feeds only what gas
processing makes (ethane, LPG, refinery gas; `supply/gas-processing`):
the split spreads it over every refinery product, 0.1-0.4 t per t of
diesel in eight regions. Electricity by source (EXIOBASE's twelve C_POW*
products) is one carrier to its users: its rows are critical together
when their shares add to 5% and enter the recipe as one pool, their
share-weighted mean (losing gas power costs a user its gas-fired share,
not all its power).

Crops are the exception: fertiliser (N, P and compound) and chemicals nec
(crop protection) are substitutable in a crop's recipe whatever their
share (`supply/yield-inputs`). A field given 40% less fertiliser yields
less, not 40% less: yield-response curves are concave and soils carry
nutrients over a season, so short-run yield losses are a fraction of the
input cut. Before, P fertiliser at 5-6% and chemicals at 6-9% of French
and Polish cereals' value made them critical, and the gas cut took 20-36%
of those countries' wheat, one for one with their gas-hit inputs. In the
CES bundle (sigma 0.2, with the land, labour and capital of value added)
a 40% fertiliser cut at an 8% share costs a crop about 10%.

Every other input is substitutable. A product's recipe is then nested:
the minimum of its critical inputs and a bundle, the bundle a CES of the
substitutable inputs and value added (labour and capital, weighted by one
less the input shares), elasticity sigma 0.2 by default
(`supply/default-sigma`). Why: intermediate inputs are close to
complements at business-cycle horizons. Atalay (2017) estimates the
elasticity among intermediates near zero; Boehm, Flaaen & Pandalai-Nayar
(2019) find Japanese affiliates in the US after the 2011 Tohoku
earthquake cut output almost one for one with their imported inputs, an
elasticity around 0.2 in the short run; Baqaee & Farhi (2019) show such
complementarity makes large shocks' losses convex, far above Hulten's
first-order sales-share answer. Pure Leontief overpredicts (Moll,
Schularick & Zachmann 2023 on Germany's 2022 gas), so only the inputs
production physically cannot do without bind fully; the rest pass on a
CES loss, and a larger sigma (1, Cobb-Douglas, for a year or more) is the
option for longer horizons. The thresholds are this model's, not the
literature's: Pichler et al. (2022) had analysts rate each input of each
industry critical or not (the IHS Markit survey), which is the idea the
5% share and the 0.1 per unit stand in for where no rating exists; they
are a rule to audit, not an estimate.

Imports are not reallocated: what a region uses of a product is the
share-weighted mean of its origins, at EXIOBASE's 2011 import shares, and
a region that imports its feedstock (Japan's crude, the Netherlands' gas)
loses what its suppliers lose in proportion. That is a short-run
assumption (weeks to months: contracts, shipping, crude grades and plant
specifications), and it overstates losses beyond that, when buyers turn
to other origins. The shares are per region, not per user: a region's
refineries and households draw on the same origins.

Substitutable inputs below a 0.1% share are left with value added in
outage (a CES bundle barely feels them; they are half the rows). Of
530,512 recipe rows (48 regions, 7,118 region-products, 206,009 rows
physical in both units) 10,668 are critical.

### What bnm.supply gives

- downstream: the products and industries a product made in a region
  reaches through recipes and trade, with the depth (recipe steps), the
  dependence share (the share of the node's value tracing back to the
  product over every path: value shares times trade shares summed, the
  cost-push Leontief inverse) and the exposure (what the node would lose
  were the product cut entirely and the critical recipes Leontief:
  1 - hypergraph/propagate on the critical rows alone). Value dependence
  misses hard links the monetary table routes elsewhere; exposure keeps
  them. An industry is the mix of the products it makes.
- upstream: what a unit needs, the Leontief inverse in the hybrid units
  (tonnes, TJ, M EUR per unit, through trade shares), value dependence
  alongside.
- users: a product's direct uses by industry and final demand.
- outage: a cut of x of a product in a region (where it is made, or where
  it is used) propagated through the nested recipes across regions by
  hypergraph/propagate (recipes loop: power needs coal, coal mines need
  power), with how it settled (:settled, the updates and residual).
- tools/bnm/supply_check.clj reports the balances, EXIOBASE against UNSD,
  the validation and two shocks, and asserts the real-data checks (ok or
  FAIL, exit 1 on a failure). They take about seven minutes, so the unit
  suite (test/bnm/supply_test.clj) runs on synthetic graphs only.

hypergraph/propagate. Every rule (minimum, share-weighted mean, CES with
positive weights, each capped by a node's given value) is monotone and
continuous, so the system has a greatest fixed point, and iteration from
the top (recipe nodes at max(1, the largest given value)) falls to it.
Fixed points are not unique: a loop of critical recipes holds at any
level its cuts allow, and the greatest is the one with no shortfall
beyond what the cuts force. Each recomputation only lowers a node and
never below that point, so the order (first in first out, or last in
first out) changes the work, not the answer; the tests check both orders
against synchronous iteration on random graphs with loops. It stops when
every node is within tol of its recipe (the residual is returned), which
leaves it at most tol / (1 - g) above the fixed point on a loop of gain
g; past a cap on updates (2,000 per node) it throws with the residual
instead of returning. The two shocks below settle in 9.5 and 5.8 million
updates to a residual of 1e-6.

### What it shows (tools/bnm/supply_check.clj)

- EXIOBASE's 2011 refinery output against UNSD's 2011 production
  (countries above 100 kt): the fuels agree, the rest do not.

      product          countries  median  within 20%  furthest
      motor gasoline       37      1.00       34      NL 1.40, BE 0.78
      gas/diesel oil       37      1.00       35      ZA 1.64, MX 1.29
      jet kerosene         33      1.00       32      ES 12.4
      other kerosene       13      1.01       10      CN 0.36, IE 2.14
      jet + kerosene       35      1.00       32      IE 2.14, NO 1.69, ES 1.44
      heavy fuel oil       37      1.00       36      DE 1.36
      refinery gas         35      1.01       27      US 2.47, FI 2.08
      LPG                  36      1.24       18      CA 3.58, SE 3.13, US 2.43
      naphtha              31      1.94        6      ES 5.84, BE 3.11, CN 3.06
      bitumen              30      1.90        1      PT 2.70, AU 2.50
      other products       25      1.56        5      ID 4.07, TR 3.97

  Neither gap is the script's. production.csv is the hybrid supply table
  as it stands (tonnes, divided by 1,000 against UNSD's kt), and every
  naphtha tonne comes from the refinery industry. Spain's jet is UNSD's
  reporting: it puts Spain's refinery kerosene, 6.45 Mt, under other
  kerosene (and moves it to jet as a transfer), leaving 0.26 Mt of jet;
  EXIOBASE has 3.18 Mt of jet and the same 6.45 Mt of kerosene, so jet
  and kerosene together are 1.44 times UNSD's, a double count in
  EXIOBASE rather than a 12-fold one. Naphtha, LPG, bitumen and the
  other non-fuel products run 1.5-3 times UNSD's across countries, by
  ratios that differ by country, so not a unit or calorific factor: the
  hybrid tables' refinery output carries what its refinery industry also
  takes in (gas processing, product backflows from petrochemicals;
  products out 14% above crude and feedstocks in), and that excess lands
  on the light and non-fuel products. Read EXIOBASE's naphtha tonnages
  (and its naphtha-to-plastics phys) as up to twice too large; UNSD's
  yearly figures are the ones to scale by.
- Validation: US crude reaches gasoline, jet, diesel, naphtha and basic
  plastics in one recipe step (dependence 0.21-0.23, plastics 0.075; each
  37% exposed, the domestic share of US refinery crude), land and air
  transport and chemicals in two. Germany's and America's basic plastics
  need naphtha (0.96 and 0.20 t/t) and crude (1.4 and 1.3 t/t) and natural
  gas (0.14 and 0.24 t/t); N fertiliser needs gas (Germany 0.60, the US
  0.78, China 0.33 t/t beside its coal). The gas links are invisible in
  value (dependence below 0.001: the monetary tables route gas through
  gas distribution services), which is why exposure and the feedstock
  rule read the physical rows.
- Natural gas for use in Europe cut 40%: N fertiliser and gas-fired
  electricity fall 40% in Germany, France, the Netherlands and Poland;
  crops lose 2-6% (wheat 4-6%; Germany's vegetables 14%), cattle 4-6%;
  steel 13-40% (gas burnt at 0.1 t/t or more counts as a feedstock);
  America's N fertiliser loses 3%, China's 9%. Before the changes above,
  gas power lost 2% in Germany (gas into TJ was never a feedstock) while
  wheat lost 25% in France and 36% in Poland (fertiliser and crop
  protection critical, and Poland's diesel capped by refinery gas).
  Leontief on critical inputs still overstates the depth (Germany's 2022
  output fell far less, Moll et al. 2023): read the ordering, not the
  size.
- Middle East crude output cut 50% (unchanged by the fixes): refining
  falls 48% in Japan, 33% in India, 27-29% in China and Indonesia, 17% in
  Korea, 11% in the rest of Asia-Pacific; 2-31% in Europe and the Americas
  (Russia 2%, Brazil 7%, the US 14%, Germany 29%). Asia's mean 27% against
  16%. The import shares are EXIOBASE's, estimated where no bilateral data
  exist: Korea's is far below its known Gulf dependence (about 85%) and
  Germany's and Britain's above theirs; bilateral crude flows (BACI) are
  the next step.

What is missing: one hybrid year (2011) of structure, with UNSD's yearly
oil products as the only time dimension; plant locations (refineries,
crackers, fertiliser and steel plants), so a cut is per region, not per
cell (only SFI's ethylene assets are at hand); bilateral trade (the MR
tables' shares, Taiwan inside WA, region-wide rather than per user);
reallocation of imports; inventories (Pichler et al. 2022's days of use);
fuel switching (gas burnt for heat is a feedstock once at 0.1 t/t, which
is why steel loses up to 40%); and value shares the monetary tables route
differently from the physical flows.

### Time: the UN energy statistics

The hybrid tables are one year. scripts/oil-products.py writes
data/derived/supply/oil-products.csv from the UNSD Energy Statistics
Database: per country and year (1990-2023, 2024 partial), crude, NGL,
feedstocks and every refined product's production, imports, exports,
stock changes, refinery and petrochemical intake, own use and use by
sector, in kt, with refinery capacity (crude, transaction 131), each row
tagged with the EXIOBASE product it matches. A year's figures are taken
as known three years later (column known = year + 3). The database fills
a year in over the second and third years after it: downloaded in
October 2026 it held 2023 for every country and 2024 for about half (600
production rows against 1,214 for 2023), so year + 2 would read most
countries' figures before they were out. The values are the current
vintage, revised since first release; the lag does not undo that.
