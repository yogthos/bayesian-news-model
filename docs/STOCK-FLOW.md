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

Light products scaled to UNSD. The hybrid tables' naphtha, LPG, bitumen
and other products run 1.5-3 times UNSD's 2011 production (below), so
the script scales them, with jet and other kerosene together, to UNSD
(data/derived/supply/oil-products.csv; scaling.csv records each region's
figures and fit): for each, the matrix of its uses by origin and
destination (every industry and final demand) is fitted by
biproportional scaling (RAS) to production by origin (UNSD flow 01) and
to use by destination (total energy supply, GA; not for kerosene, whose
bunkers GA leaves out), and each product's supply in a region scaled
with its own uses from there, so supply still equals use product by
product (the script asserts it: the tables' own rounding, up to 1e-4 of
supply, is left as it was). A rest-of-world region's figure is the sum
of its countries' (`ROW_MEMBERS`: by UN continent, the Middle East with
Iran and without Turkey and Cyprus, the Caucasus and Central Asia with
WA, which takes every country no other region does). A region under 100
kt takes the median ratio as an origin and is free as a destination,
taking up what the two sets of totals leave. Scaling production alone
would not do: the excess is not spread evenly over origins, and
Germany's, Japan's and Korea's naphtha use, much of it imported, fell to
0.6 of UNSD's. Nor would the median ratio for the rest of the world: it
halved the Middle East's naphtha output (51.7 Mt in EXIOBASE, 46.2 in
UNSD, 26.7 at the median ratio of 0.52), and with the region's use free
the Gulf's exports to Asia's fixed destinations squeezed its own use to
2.4 Mt against UNSD's 15.1. World naphtha goes from 508 to 264 Mt
(EXIOBASE/UNSD median 1.92 before), LPG 301 to 230 (1.12), bitumen 193
to 106 (1.88), other products 174 to 178 (1.56; China's rises 2.5-fold
to UNSD's 79 Mt, the Americas' rest doubles to 21), jet and kerosene
326 to 322 (Spain's 9.6 Mt to UNSD's 6.7). Production fits UNSD within
1% for naphtha and kerosene, within 5% for LPG, 9% for bitumen (Mexico)
and 38% for other products (Indonesia: its uses all go to destinations
with targets of their own); use fits UNSD exactly where it has a figure.
Downstream, the naphtha in basic plastics falls by a third to two
thirds: Germany 0.93 to 0.59 t/t, Japan 0.97 to 0.68, Korea 1.49 to
1.00, France 0.75 to 0.32, China 1.09 to 0.33, the US 0.24 to 0.09, the
rest of Asia 0.40 to 0.29; the Middle East's 0.38 to 0.31 beside its
0.93 t of ethane (0.05 while its use was free). Each stays a feedstock
(0.1 t/t or more) but America's, whose plastics come from ethane and
LPG.

Balances: every good's supply equals its uses (intermediate and final, at
home and abroad) to rounding (median residual 1e-9; the 922 region-
products off by more than 1% are all services, mostly waste treatment).
Refineries: their refined products out against crude, NGL, other
hydrocarbons and refinery feedstocks in ran 1-54% above before the
scaling (median +14%), because refineries also take natural gas (gas
processing for ethane and LPG sits in the refinery industry), product
backflows and blending components; scaled, +6% (median; -13% to +49%,
Croatia the outlier). Against every hydrocarbon, product, additive and
biofuel they take, output was 4.5% below input (median; -18% to +5%)
and is 12% below once scaled (-26% to +7%). That balance is not a mass
balance where gas processing sits in the refinery: the gas comes in
whole and only its liquids go out, the dry gas going back to the grid
unrecorded (the Middle East's refineries take 90 Mt of gas). Without
the natural gas (refinery-nogas) it reads what it should: a refinery
burns some of what it makes (refinery gas, petroleum coke) and that
fuel is counted both out and back in, so output less everything in is
minus its own fuel and losses, 6-8% of intake in practice. Unscaled it
was -1% (median; -12% to +29%), too little for any refinery, the
light-product excess; scaled it is -7% (-20% to +23%; Croatia, Turkey,
the Middle East above zero). So the intake is left as it is: the
scaling brings the refineries into balance rather than out of it, and
no figure says which intake the excess came from. One consequence: the
industry-technology recipes divide the refinery's unchanged intake by
less output, so the crude in a tonne of any refined product rises
about 10% (the world's 3,606 Mt of crude over 4,282 Mt of products,
0.84 t/t, against 0.76 before; crude intake over gross output is
about 0.9 in UNSD).

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

Fuel burnt for heat is not a feedstock. A kiln, furnace or boiler can
burn less gas and more oil, coal or electricity, and the fuels of a heat
output (a material or manufactured good, an ore or quarry product, a
coal product, a steelworks gas, a biofuel: `supply/heat-output?`) other
than its material feedstock are one input, an energy bundle: a CES of
gas, oil products, coal, steam and electricity (as its pool), weights
their heat at the IPCC values (TJ per unit of output), elasticity
`supply/energy-sigma`. The bundle is critical, as one input, when one of
its fuels is by the rule above, when their shares add to 5%, or, outside
manufactured goods, when their heat reaches 2 GJ per tonne
(`supply/heat-critical`: the heat of 0.04 t of gas or 0.1 t of hard
coal, as the feedstock rule reads 0.1 t of one fuel; cement 3 GJ/t in
Germany, glass 3.5, ceramics 7, paper 12); otherwise its fuels are
substitutable one by one as before. The material feedstocks stay
critical one by one (`supply/material-feedstock`): every fuel of N
fertiliser (gas, coal, naphtha into ammonia: `supply/petrochemical`);
the oil and gas liquids of basic plastics (naphtha, ethane, LPG,
refinery gas, gas and fuel oil), its natural gas counted as the
crackers' heat (ethane from gas processing is a refinery product, a
feedstock where the tables record it); the oil feedstocks of chemicals
nec, its gas counted as heat (Germany's ammonia is in N fertiliser);
coke and coal in steel, coal products and the steelworks' gases;
petroleum coke and coal tar in aluminium (anodes); crude, NGL, other
hydrocarbons and refinery feedstocks everywhere. A feedstock is in the
bundle or out of it, never both, so the bundle and the feedstocks
cannot count one tonne twice; a path can run through both (steel's
coke critical on its own, and the blast furnace gas made from coke in
steel's bundle), but in a minimum a second path to the same cut costs
nothing. Electricity for electrolysis (aluminium, chlor-alkali) sits in
the bundle as if it were heat; it is most of their bundle's weight, so
a power cut still costs them nearly one for one.

Gas used as the reductant in direct-reduction steel is no heat, and
EXIOBASE cannot tell it apart: it has one steel product, whose gas rows
are as small in the Middle East (0.017 t per t) and Mexico (0.019) as in
Spain (0.020), where no DRI is made. So it is a per-region override
(`supply/dri-share`): in Mexico 0.32 of the steel, in the Middle East
0.87, goes with the gas used there one for one, as a perfect-substitute mean of
the gas and an untouched rest that sits beside the other critical
inputs; the gas row stays in the energy bundle as the heat it also is.
The shares are DRI over crude steel in 2011 from worldsteel's Steel
Statistical Yearbook 2012 (table 42, DRI; table 1, crude steel;
data/raw/material/worldsteel, bead bnm-1pq.24): Mexico 5,854 kt over
18,101 kt; the Middle East 20,056 kt (Iran 10,368, Saudi Arabia 5,153,
the Emirates 1,800, Oman 1,400 estimated, Qatar 1,335) over 23,002 kt
(0.81 without Oman's estimate, from a country with no crude steel row:
its DRI is exported). They had been 0.3 and 0.8, remembered rather than
read; Midrex's World Direct Reduction Statistics, the other free
source, returned 404 and 403 when fetched (October 2026), so it is not
used. A 40% cut of the gas used there costs the Middle East's steel
35% (32% at the old 0.8), against 25% with its gas as heat alone;
Mexico's loses 28% either way, its energy bundle, mostly gas, already
losing more than its DRI part (0.32 of the 40%, 13%). The Middle East's
share rests on one estimate: Oman's 1,400 kt is worldsteel's, not a
reported figure, so the share lies between 0.81 and 0.87, and the
steel's loss under the 40% cut between about 32% (0.4 x 0.81, as at the
old 0.8) and 35%; the DRI part sets a floor of 0.4 times the share
either way. The override matters where DRI is most of the
steel and little where it is not. Gas DRI elsewhere
(Venezuela and Trinidad, Egypt and Libya, Russia, India's few gas
plants) is a smaller part of its region's steel and stays heat.

sigma: the prior is 0.5, half the medium-run interfuel elasticities of
the energy models (GTAP-E 1 between gas and oil and 0.5 between coal and
the rest; MIT EPPA 1) for a horizon of months: Moll, Schularick &
Zachmann (2023) note a glass furnace cannot switch from gas to fuel oil
overnight, and their appendix lists 36 German firms that switched
boilers, kilns and furnaces to oil, LPG, coal or electricity in 2022. It
is not learned. Nor is the bundle a CES production function in which
the other fuels expand: that needs their prices and their supply, and
the graph has neither. It is the CES of the fuels' availabilities, each
at most its normal use (no fuel is bought in addition, as no import is
reallocated), and a power mean of numbers at or below 1 is at most
their weighted mean, so the bundle loses at least the cut fuel's share
of the heat times its cut whatever sigma (the unit tests check it from
sigma 0.1 to 20). Sigma moves the loss only between that floor and the
whole cut, and little (a 40% cut of half the heat: 20% linear, 25% at
0.5, 31% at 0.2; Germany's energy-intensive industry below loses 8%
at 0.25, 8% at 0.5 and 7% at 1). What the bundle buys is that the
loss is the gas's share of the heat, not the whole cut; real fuel
switching, buying more oil or coal, would take it below the floor.

The other artefacts the review turned up, fixed the same way. Equipment
(machinery, electrical and precision equipment, vehicles, fabricated
metal: `supply/equipment`) in a farm's or mine's recipe is its capital's
replacement and repair, deferred in a short-run shortage, and
substitutable whatever its share: Germany's vegetables lost 14% under
the gas cut because machinery, 7% of their value, was critical and
itself lost 13% through motor vehicles, aluminium and the gas they
burn; now 2%. An electricity or heat carrier (TJ; nuclear fuel too) is
critical on its fuel alone: the other goods a power station buys are
upkeep (EXIOBASE's industry mix gives Bulgaria's wind power chemicals at
18% of its value and steam at 13%, and it fell 40% with the gas). Its
fuel is read by the feedstock test where it has an ncv, and by value
share where it has none (`supply/uncalorific-fuels`: forestry, wood and
crops in biomass power, uranium ore in nuclear fuel; dropping the share
test outright had cut those links, 16 region-rows). And gas
distribution services in a carrier's recipe are the monetary route of
the gas its physical row already holds, left with value added (France's
nuclear power had them at 34% of its value, and lost 26% under the gas
cut). That hides no gas: every region's gas power has a physical gas
row of 0.13 TJ per TJ or more (Estonia; 1.3-2.7 elsewhere), critical by
the feedstock test, and gas power loses the cut, 40%, in all 29
European regions that make it. Nuclear power does not lose nothing: 2-
18% (Germany 9%, France 6%, Spain and Hungary 13%, WE 18%), because
nuclear fuel, its critical input, buys chemicals (conversion and
enrichment: 34% of its value in Germany), substitutable in its CES
bundle but losing what the gas takes from chemicals. That is the
steady state, with every stock drawn: a reactor holds a year or more
of fuel, and with the stocks (below) nuclear power loses under 1% in
the first month of the cut and 0.6-5% by month 12, its upkeep rather
than its fuel (its fuel stocks are at most 5% drawn).

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
530,416 recipe rows (48 regions, 7,118 region-products, 205,913 rows
physical in both units) 28,671 are critical, the energy bundles' fuels
among them.

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
- outage-path: the same month by month with stocks (below): a stock
  drains at the shortfall before the cut passes on; outage is its limit.
- tools/bnm/supply_check.clj reports the balances, EXIOBASE against UNSD,
  the validation and two shocks, and asserts the real-data checks (ok or
  FAIL, exit 1 on a failure). They take over an hour with the stock paths, so the unit
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
  (countries above 100 kt), as the hybrid tables have it: the fuels
  agree, the rest do not. Scaled (above), naphtha, LPG, bitumen and
  other products are at UNSD's (median 1.00; within 20% in 31 of 31
  countries, 36 of 36, 30 of 30 and 24 of 25), jet and kerosene together
  in all 35.

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

  Neither gap is the script's. The table is the hybrid supply table as
  it stands, before the scaling (tonnes, divided by 1,000 against UNSD's
  kt; scaling.csv keeps it as exiobase_kt), and every
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
  on the light and non-fuel products. The graph now carries them at
  UNSD's (above).
- Validation: US crude reaches gasoline, jet, diesel, naphtha and basic
  plastics in one recipe step (dependence 0.21-0.23, plastics 0.075; each
  37% exposed, the domestic share of US refinery crude), land and air
  transport and chemicals in two. Germany's and America's basic plastics
  need naphtha (0.58 and 0.07 t/t, after the scaling; 0.96 and 0.20
  before) and crude (1.1 and 0.9 t/t) and natural gas (0.10 and 0.21
  t/t); N fertiliser needs gas (Germany 0.60, the US
  0.78, China 0.33 t/t beside its coal). The gas links are invisible in
  value (dependence below 0.001: the monetary tables route gas through
  gas distribution services), which is why exposure and the feedstock
  rule read the physical rows.
- Germany 2022: gas for use in Germany cut 26%, industry's cut from
  July 2022 to March 2023 (Moll, Schularick & Zachmann 2023, against
  2018-21). Energy-intensive output (Destatis's paper, coke and refined
  products, chemicals, basic metals, non-metallic minerals) falls 8%
  weighted by 2011 value added, as Destatis's index is (7% by output
  value, where refining, large in output and thin in value added, weighs
  more), the rest of manufacturing 1%: N fertiliser 26%, ceramics 21%,
  glass 19%, chemicals 18%, cement 18%, paper 15%, aluminium 9%,
  plastics 5%, steel 1%, machinery and vehicles 1%. Destatis's
  energy-intensive index fell by close to 20% at its low and the rest of
  industry hardly. The direction and the decoupling are reproduced; the
  size is not, and the comparison is not like for like in three ways,
  all making the model's figure smaller. The 20% is the index's trough,
  while the 26% is industry's gas use averaged over nine months. The 26%
  is industry's average: Moll et al. expect the energy-intensive sectors
  cut more (their gas use by sector was not yet out), and the model cuts
  every user in Germany alike, households and power stations included.
  And the real fall held price and demand (gas at several times its
  price, ammonia and other gas-intensive goods imported instead) that a
  quantity cut does not. So the model's 8% is a lower bound on what the
  cut alone would do, and the calibration is not met: energy-sigma is
  not tuned to it (8% at 0.25, 7% at 1; sigma moves it little, above).
- Natural gas for use in Europe cut 40%: N fertiliser and gas-fired
  electricity fall 40% in Germany, France, the Netherlands and Poland;
  crops lose 2-9% (wheat 4-7%; vegetables 2% in Germany, 9% in the
  Netherlands' greenhouses), cattle 4-7%; steel 14-33% (Germany 17%,
  Italy 14%, France 22%, Poland 23%, the Netherlands 26%, Britain 27%,
  Czechia 29%, Spain 33%: gas the heat of its furnaces, the loss the gas
  share of the heat, or Italy's gas-heated coke ovens for Germany's);
  America's N fertiliser loses 2%, China's 8%. Before the energy bundle,
  the equipment rule and the scaling, crops lost 2-6% but Germany's
  vegetables 14%, and steel 13-40% (gas burnt at 0.1 t/t, or at 5% of
  value as in the Netherlands, counted as a feedstock; blast furnace gas
  a feedstock of coke, and gas one of blast furnace gas); earlier still
  gas power lost 2% in Germany while wheat lost 25% in France and 36% in
  Poland. The availability-capped bundle cannot buy more oil, so it
  still overstates the gas-heavy users: read the ordering, not the size.
- Natural gas for use in the Middle East and Mexico cut 40% (the DRI
  override, `supply/dri-share`): steel loses 35% in the Middle East
  (25% with its gas read as heat alone) and 28% in Mexico (the same
  without the override).
- Middle East crude output cut 50%: refining falls 48% in Japan, 29% in
  India and Indonesia, 24% in China, 17% in Korea, 11% in the rest of
  Asia-Pacific; 2-31% in Europe and the Americas (Russia 2%, Brazil 7%,
  the US 14%, Germany 29%). Asia's mean 26% against 15% (before the
  scaling 27% against 16%, India 33% and China 27%: smaller naphtha
  flows carry less of the Gulf's crude into Asia's other products). The import shares are EXIOBASE's, estimated where no bilateral data
  exist: Korea's is far below its known Gulf dependence (about 85%) and
  Germany's and Britain's above theirs; bilateral crude flows (BACI) are
  the next step.

What is missing: one hybrid year (2011) of structure, with UNSD's yearly
oil products as the only time dimension; plant locations (refineries,
crackers, fertiliser and steel plants), so a cut is per region, not per
cell (only SFI's ethylene assets are at hand); bilateral trade (the MR
tables' shares, Taiwan inside WA, region-wide rather than per user);
reallocation of imports; stocks that refill or are managed (below, a
stock only drains); fuel switching beyond the bundle (other fuels held at their
availability, none bought in addition); and value shares the monetary
tables route differently from the physical flows.

### Stocks: an outage drains a stock before it cuts a flow (bead bnm-1pq.23)

`supply/outage` is instantaneous: a cut flows straight through the
recipes. `supply/outage-path` runs it month by month (H = 24 by
default) with stocks, in months of a region's normal use of a product,
held at [:use region product], what the region uses of it from every
origin. Each month a stocked node supplies its inflow (its recipe, the
cut applied) plus what it draws from its stock, at most the shortfall
and at most what is left, min(1, inflow + stock); the stock falls by
what was drawn, and only once it is empty does the cut pass downstream
whole. The cut holds every month and nothing refills a stock, but the
inflow is the trade-share mean of every origin, so imports from origins
the cut does not reach keep flowing and a stock covers only the cut
origins' share (Korea's crude drawn at its Gulf share times the cut,
not at the cut). `outage` is the path's limit once the stocks a cut
reaches are drawn (the unit tests check the two agree then, and every
month when there are no stocks). The rule min(1, r + s) is monotone and
continuous like the others, and since stocks only fall each month's
operator lies below the last's, so a month is solved from the last
month's answer (chaotic iteration from above, as in
hypergraph/propagate, whose `prepared` and `rule` it calls, public for
this): only what a stock running short moves is recomputed. The unit
tests check that this is so: no availability and no stock ever rises,
and every month equals the month solved afresh from the top with the
stocks it began with. A stock is drawn at the shortfall against normal
use, even where its users are held back by another input and need less,
so it drains early if anything.

A power station's upkeep is a stock too. Its machinery, metals and
construction (`supply/upkeep`: equipment, steel, aluminium, copper,
lead/zinc/tin, other non-ferrous and precious metals, foundry work,
fabricated metal products, construction, renting of machinery) are
substitutable in its recipe (a carrier is critical on its fuel alone),
and in the steady state their loss passes on through the CES bundle, so
nuclear power lost 0.6-5% by month 12 of the gas cut, all of it upkeep.
But repair and replacement wait for the next planned outage, as a farm
defers its equipment: in `hypergraph` each upkeep input of an
electricity or heat carrier enters through its own node [:upkeep region
station input], and `outage-path` gives each `supply/upkeep-months` of
it (6, a prior: maintenance is done at planned outages, yearly for a
thermal plant and at refuelling, every 12-24 months, for a nuclear one,
so a cut finds a station on average half a cycle from its next; putting
off a second outage is not counted). A 30% cut of an upkeep input draws
it in 20 months. :upkeep-months 0 turns it off, a stocks key [region
station input] sets one station's, and each month reports what is left
where any was drawn (:upkeep). `outage` is unchanged.

scripts/supply-stocks.py writes data/derived/supply/stocks.csv
(region, product, months, year, known, basis data or prior, source),
918 rows:

    product                 basis  regions  months (median, range)  source
    crude                   data     40     0.97 (0.17-4.33)   JODI Oil World 2011: closing stocks / refinery intake
                            prior     1     1.13 (China)       NBS phase-1 SPR 12.43 Mt + Xinhua commercial 29.2 Mt
                                                                 over JODI's 2011 refinery intake
                            prior     5     0.55               the JODI regions' lower quartile (Russia; the
                                                                 Baltics, Luxembourg, Malta, with no refinery)
    gasoline, diesel, jet,  data   26-47    0.6-1.9 medians    JODI 2011: closing stocks / total demand, each
      kerosene, fuel oil,                   (cap 3-6)            country capped (below)
      LPG, naphtha          prior   1-22    0.33-1.17          the JODI regions' lower quartile (China's products,
                                                                 Russia's naphtha and jet, small users)
    natural gas             data     22     1.34 (0.00-10.97)  GIE Storage Database (working gas of the facilities
                                                                 working in 2011) x fill 0.6 (prior) / EXIOBASE gas use;
                                                                 US: EIA 2011 mean working gas / consumption (1.33)
                            prior     1     0.99 (Russia)      Gazprom's working gas, 65 bcm, x fill 0.6
    wheat, rice, coarse     data     48     1.47, 6.20, 1.25   USDA PSD 2011/12: ending stocks / domestic consumption
      grains                                                    (the EU's figure for every member)
    nuclear fuel            data     26     30                 ESA Annual Report 2011: EU utilities' 47,343 tU, "at
                                                                 least two and a half years" of requirements
                            prior    18     18                 outside the EU: the core lasts to its next refuelling,
                                                                 12-24 months
    N, P fertiliser         prior    48     2                  plant, port and dealer inventories, 1-3 months
    steel, aluminium,       prior    48     1                  LME/SHFE warehouse stocks are days to weeks of world
      copper, Pb/Zn/Sn,                                         use; consumers' and stockholders' are unpublished
      other non-ferrous

JODI's ratios run long where demand is tiny (Greece's naphtha had hit
the old 36-month cap, Switzerland's fuel oil 25 months, South Africa's
products 8-17), so each country's stock is capped at a plausible
maximum of its own use before a region's are summed: 6 months of crude
and the fuels (above the largest obligation, Switzerland's compulsory
4.5 months of petrol, diesel and heating oil, and the IEA's 90 days of
net imports; Japan's 4.3 months of crude is the largest regional ratio),
4 of LPG (Japan's national and private stockpiles together, about 90
days), 3 of naphtha (a feedstock no one is obliged to store). Japan's
crude includes the government stocks it reports; countries include
those inconsistently.

China reports no crude stock to JODI, and with none it lost most of its
refining in month 1 of the crude cut. It holds large ones: the phase-1
strategic reserve, 12.43 Mt at Zhenhai, Zhoushan, Huangdao and Dalian
(the National Bureau of Statistics' first figure, November 2014; filled
by 2009) and a commercial crude stock Xinhua's China Oil, Gas &
Petrochemicals put at 29.2 Mt in July 2010 (via Interfax; its 33.6 Mt of
May 2014 was the highest since January 2010). Together 41.6 Mt over
2011's 37.5 Mt a month of refinery intake: 1.13 months, a prior and
flagged as one (no open series; EIA's later estimates of Chinese
stockpiling start after 2011, and IEA's are not open).

Where no figure is reported, zero overstated the fast losses for oil:
every refinery and terminal holds working stock. A region using an oil
product with no figure (China's products, Russia's crude, which Russia
does not report, a few small users) takes the lower quartile of the
reporting regions' months, a prior, so as not to overstate the buffer
either. Gas is different: GIE's database is the census of European
facilities, so a European country with none listed (Finland, Greece,
the Baltics but Latvia, Luxembourg, Slovenia, Cyprus, Malta) is taken to
have none and zero is right, except where a facility has since closed
without a closing year (Ireland's Kinsale field store, 2001-2017, is
missing: a gap); outside Europe and the US only Russia is
filled (Gazprom's 62.2 bcm in 2005, 72.7 in 2022: 65 bcm), and Canada's
storage, China's (about 3% of its use then) and Japan's and Korea's LNG
tanks (a few weeks) are left at zero, a gap that matters for none of
the shocks below.

The gas figures are capacities times a fill: the GIE database gives
today's working gas per facility with its start-up and closing years,
and the fill, 0.6, is a prior checked against GIE's AGSI+ as ACER's
public summary has it: EU storage 35.75% full on 1 April (2011-2015
mean), 86-87% in late November (2011-2024 mean) and about 90% at the
start of winter. A cycle from 0.36 to 0.9 averages about 0.62, so 0.6
is a cut at no particular season; one in October meets about 0.9, one in
March 0.4. Storage also carries the seasonal swing, which the model's
constant monthly use leaves out, so it is a buffer at its best.
Britain's is small (0.24 months), Austria's and Hungary's large (11 and
7: Austria's Haidach and 7Fields serve Germany's grid too, so its own
figure is overstated).

Manual list (gated or not downloadable, October 2026): GIE AGSI+ daily
fill (API key, free registration); IEA emergency stocks (not open); LME
and SHFE stock series (partly open, no bulk history); Midrex World
Direct Reduction Statistics PDFs (404/403); Xinhua's monthly Chinese
crude stocks (subscription).

What it shows (tools/bnm/supply_check.clj, loss at months 1, 3, 6, 12,
24 against the steady state; upkeep deferred, the oil stocks capped and
China's crude a prior):

    natural gas for use in Europe cut 40%
      DE N fertiliser   0%   0%  40%  40%  40%   steady 40%   (storage 1.55 months)
      DE gas power      0%   1%  40%  40%  40%          40%
      DE nuclear power  0%   0%   0%   0%   0%           9%   (was 0 0 1 1 1)
      DE steel          0%   1%  15%  15%  15%          17%
      FR N fertiliser   0%   0%  40%  40%  40%          40%   (1.90)
      FR nuclear power  0%   0%   0%   0%   0%           6%   (was 0 0 0 1 1)
      NL nuclear power  0%   0%   2%   2%   2%           8%
      PL N fertiliser   1%   1%  34%  40%  40%          40%   (2.06)
      GB N fertiliser  16%  40%  40%  40%  40%          40%   (0.24)
      GB nuclear power  0%   1%   1%   1%   1%           4%
      GB steel          9%  26%  26%  26%  27%          27%
    Middle East crude output cut 50% (refining industry)
      WM refining       2%   2%   2%  50%  50%   steady 50%   (crude 3.03 months)
      JP refining       0%   0%   0%  48%  48%          48%   (4.33)
      IN refining       0%  27%  28%  29%  29%          29%   (0.32)
      KR refining       0%   0%   1%  13%  16%          17%   (1.44)
      CN refining       0%   0%   0%  22%  23%          24%   (1.13, a prior; was 19 19 19 22 23)
      DE refining       0%   0%   0%  29%  29%          29%   (2.57)
      US refining       0%   1%   2%   4%  14%          14%   (2.08)
    a wheat harvest 30% short everywhere (world stocks 3.6 months)
      RU food products  0%   0%   0%  16%  30%   steady 30%
      WM food products  0%   0%   0%  30%  30%          30%
      CN food products  0%   0%   0%   2%   3%           6%
      storage pressure (bnm.food, against the stocks at the start)
                        1.07 1.25 1.63 2.67 4.29

Nuclear power loses nothing through month 12 in Germany and France
(against 9% and 6% in the steady state), and at most 1.7% anywhere in
Europe: its fuel stocks (18-30 months) are at most 5% drawn by then and
its upkeep waits for the next planned outage (the least left at month
12 is Belgium's construction, 3 of its 6 months). The 1.7% left, the
Netherlands' (Britain's 1%, Hungary's less), is no upkeep: the monetary
tables give these stations natural gas itself at 0.3-0.9% of their value
(the Netherlands 0.87%) against 0.2 kg of it per TJ, the industry mix's
gas as the gas-distribution row was, and a 40% cut of it costs a CES
bundle 1-2%. The check that nuclear loses under 1% through month 12
fails on it; treating a carrier's gas row like its gas distribution, a
monetary route of another station's fuel, would clear it, and is left
for a decision. European gas storage delays the N fertiliser cut by
months: none in month 1 or 3 where storage holds over a month of use,
the whole cut by month 6 (Germany, France, Italy, the Netherlands), as
storage carried the 2022 winter; Britain, with a week of storage, loses
16% in month 1. Crude stocks hold refining for months (Japan's four
months of crude, then its products, keep it whole to month 6), India's
for a month; China, with its reserve and commercial stock in, now holds
its refining whole to month 6 instead of losing 19% at once. Grain
stocks hold food whole for the first half year while world stocks fall
and bnm.food's storage pressure (1 + c)/(s/s_ref + c) rises from 1.07
to 4.3: the quantity loss is delayed, and the scarcity shows first in
the price, as competitive storage has it. No availability rises from one
month to the next in any of the three paths. None of these is a
calibration: the stocks are the right size, give or take the fill, the
reporting and the priors, and the timing follows from them.

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

## Finance rows: the spine against the national accounts (bead bnm-3ka.1)

scripts/econ-structure.py sets the spine's finance (FIN) and
manufacturing value-added shares beside BEA's (NAICS 52 and 31-33, the
2021 historical file to 1996 and the 2026 release from 1997), OECD
Table 6 (ISIC4 K and C over total gross value added) and EU KLEMS 2023
(NACE K and C). It writes data/derived/econ/structure-inputs.csv and
structure-ratios.csv (each spine segment's share over each benchmark's,
pooled over the years both cover).

The US gap is in the Long-run WIOD, not our scripts: our J rows equal its
socio-economic accounts' VA. Its US value added matches BEA's to 0.1-1%
in the industries both classify alike (agriculture, mining, utilities,
construction, trade, hotels, real estate and business services); finance
is 0.44-0.60 of BEA's from 1965
to 1999 (0.51 pooled); output is short too (0.55-0.62). The total stays
within 1% of BEA's GDP: what J lacks sits mostly in the public and
personal services (LtQ) and some in manufacturing. LR-WIOD builds the US from BEA's original make-use tables
(Woltjer et al. 2021, 3.26; version 1.1), which predate BEA's 2003 move
to allocating FISIM to its users: the pattern is the finance industry's
imputed services left with their users. WIOD 2016 and ICIO match BEA
(1.01 and 1.04), so the 1.63 jump at 2000 is the definition changing,
not finance growing. Elsewhere LR-WIOD's finance is 0.8-1.3 of OECD's
(the UK, Greece and the Netherlands about 0.8, France 1.2-1.3, Mexico
1.24); WIOD 2016's is within 10% except Australia (1.27), Turkey (1.25)
and Canada (0.78). The spine's manufacturing is 4-22% above the
benchmarks (the US 1.05 to 1999, 1.12 in WIOD 2016, 1.19 in ICIO):
io-spine2.py puts all of ISIC4 J58 in PAPER, software publishing
included, which ISIC3 classed outside manufacturing.

bnm.econ-structure (tools/bnm/econ_structure_run.clj) builds the
series by data year, econ/finance-va-share and
econ/manufacturing-va-share, into data/derived/econ/
econ-structure-panel.csv (iso,year,variable,value,known,source; year the
data year, known the year it could be read). The stock,
data/derived/stocks/econ-structure.csv, follows the stocks convention
(iso,year,variable,value,data_year,source; year the year known): each
year holds the latest data year known by then, so a back-cast published
after later years never becomes a stock value. A year with a benchmark takes it (BEA,
then OECD, then EU KLEMS; known a year on for BEA, two for the others).
A spine year without one takes the spine share times the benchmark's ratio
to that spine segment over the nearest five overlap years, using only
overlap years published by the value's known year: never before the
spine's three-year lag, and not before the first overlap year is out. So
LR-WIOD's Britain in 1965 is known in 1998, when OECD's 1995 is out. A
segment with no overlap is chained through the next at their common year
(LR-WIOD and WIOD 2016 in 2000). Countries without a benchmark (China,
India, Taiwan, Indonesia and some ICIO-only countries) keep the spine's
share, source the segment. The US is BEA throughout. The benchmarks are
current vintages, revised since first release (BEA's NAICS history is
the 2021 vintage): the known year dates a value, it does not restore what
was published then.

The 7b regression in docs/ECONOMIC-CHARACTER.md (change in manufacturing
2000-2014 on the 2000 finance share, log income and the 2000
manufacturing share, 43 WIOD 2016 countries) left ELEC out of
manufacturing. With ELEC, on WIOD 2016: finance +0.009 (t 0.08); on the
corrected series: +0.031 (t 0.26), without Luxembourg +0.24 (t 0.9).
Finance size still does not predict deindustrialisation; the -0.08 was
partly the missing sector.
