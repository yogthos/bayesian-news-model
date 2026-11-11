# Buffers: one schema for every stock, and its invariants

`bnm.buffers` (bead bnm-56c.3; docs/PLAN.md 1.1 and 1.2). A buffer hides an
imbalance: while a stock covers a flow gap nothing shows, and the break
comes when cover runs short and the flow can't be restored in time. Nothing
in the code is specific to one kind of stock. Reserves, fuel, grain, debt
and capital are rows of data to the same functions.

## The schema (`bnm.buffers/record`)

One record per stock:

| field | meaning |
|---|---|
| `:id` `:name` `:unit` `:time-unit` | id `module/place/stock` for engine stocks |
| `:holder {:unit :place}` | who holds it, and where (`country` = one per country) |
| `:kind` | `:material` or `:information` (a perception; not a buffer) |
| `:inflows`, `:outflows` | `{:var :unit :counterpart :rigid :rigid-sd}`: the counterpart is the stock the flow leaves or enters, nil for outside; `:rigid` is the share of the flow that can't be cut |
| `:measure {:frequency :lag}` | how often it is measured, and how late: a value for t is known at t + lag (the engine's known-year rule) |
| `:usable {:share :sd}` | usable against headline (Sri Lanka 2022: about 3%) |
| `:life` or `:decay` | optional: asset life or decay rate |
| `:idle {:share :sd :binding}` | optional: installed but idle, and what binds instead |
| `:restore {:time :sd}` | time to restore the inflow |
| `:carry {:cost :yield}` | per time unit |
| `:claims {:rate :maturity}` | terms of claims on the flow |
| `:holders {name share}`, `:leverage` | who holds it, and how leveraged |
| `:market {:ref-price :ref-cover :c}` | to read a cover from a price |

The series are observations `{:t :v :known :sd}` under `:stock :inflow
:outflow :flows :usable :claims :income :price :idle :leverage :restore`.
An observation is known at `:known`, else at t plus the lag. Every
invariant as of a date reads only what was known by then.

## The invariants

Each gives rows of one flat table: `stock, holder, place, date (as of),
invariant, value, sd, known (the latest input's known date), flags`, plus
`:window`, `:exhaustion` and `:band` where they apply. This is what the
regime switch (bnm-56c.4) and the hazard and scanner (bnm-56c.6) read. A
missing input gives a nil value and a flag, never a zero (bug bnm-as1).

| invariant | definition | unit |
|---|---|---|
| cover | usable stock over net drawdown, for each window (default 1, 3 and 5 time units). Drawdown is outflow less inflow when both are measured, else the stock's least-squares slope. `:exhaustion` is the last observation plus cover. A stock not draining gets the cap (100 time units), flagged `:not-draining`, never a negative value. An empty stock gets 0, flagged `:empty`. With no usable share, the headline is used and flagged `:usable-missing`. | time |
| drain-rate | net drawdown over usable stock: the inverse of cover, continuous through zero, negative when the stock grows | 1/time |
| restoration | time to restore the inflow over cover. Above 1 it is flagged `:runs-out-first` | ratio |
| rigidity | rigid share of each outflow, weighted by the outflow's level | share |
| carry | yield less (cost + decay, or 1/life) | 1/time |
| claim-gap | claims over the flow that services them. Minsky's bands on the same ratio: hedge at or below 1/(r + 1/m), speculative at or below 1/r, otherwise Ponzi | time |
| idle-share | installed but not earning, with what binds instead | share |
| holder-hhi, holder-leverage | concentration over holders, and the holder's leverage | index, ratio |
| market-reading | the cover a price implies: competitive storage's convex price (bnm.food's storage pressure) inverted, cover_ref((1 + c)/(p/p_ref) - c) | time |
| displacement | the stock's stretch against its own past at 5, 10 and 20 observations (`bnm.invariants/stretch`) | z |
| resonance | the smallest absolute stretch of the three: displaced at every scale | z |
| stock-x-flow | absolute stretch of the stock times that of its net flow | z² |
| breadth | across stocks at one date, the share of displacements past 1.5 (`bnm.invariants/aggregate`) | share |

The uncertainty combines the parts' relative spreads: the usable share's
sd, the drawdown's standard error (from residuals and the observations'
own `:sd`), and the restoration time's sd. A z-score's sd is its sampling
spread against n past values.

## Derived from the engine, and what needs a mapping

`engine-stocks` reads the module declarations (`data/derived/coupled/
graph.edn`, or the live modules). Every `:stocks` entry becomes a record.
Flows attach in two ways:

- **From a stock-flow model** (`:models`, here World3's `bnm.world3/
  sectors`): each flow's `:from` and `:to`, with the other end as its
  counterpart.
- **Inferred**: a module with one material stock that reads another
  module's emit at a lag in the level pass integrates it. This is how
  sfc-finance's balance sheet takes the flow of funds.

The publication lag is the one known rule the module's level emits (or
all its emits) share.

Today (`tools/bnm/buffers_run.clj`, data/derived/buffers/report.txt):
134 modules, 148 stock records (128 material, 20 information), 208 level
emits. 51 material stocks have flows: World3's 15 (population 4,
capital 2, agriculture 4, resources 1, pollution 4), sfc-finance's
balance sheet in 35 countries, and Keen's debt (by mapping). 77 have none
and need mappings:

- sfc-accounts' wage share and employment, 35 countries each (state
  variables, not buffers);
- Keen's omega and lambda;
- cycles' capacity and pipeline;
- food's and food-world's grain stock;
- technology's adoption.

The table modules declare stocks but not flows, since they replay a
model's table.

Stocks the engine doesn't hold enter through `resources/buffers/
stocks.edn`, which is data, not code. It holds world wheat, maize and
rice stocks (USDA PSD), foreign reserves per country (EWN, WDI), private
debt against income (BIS), and extensions that add terms to engine
stocks: Keen's debt flows, World3's capital lives, and the SFC balance
sheet. The supply model's inventories (`data/derived/supply/stocks.csv`,
918 region-product stocks in months of use, `bnm.supply/outage-path`)
are read by `supply-records`. A stock there is already cover against
gross use, and under a cut of share f its cover is months / f.

## Two descriptive checks (nothing fitted)

**Grain, USDA PSD around 2007-08** (current vintage, revised since;
known a year after the market year). World stocks-to-use, in months:

| | 2005 | 2006 | 2007 | 2008 | 2009 |
|---|---|---|---|---|---|
| wheat, world | 3.13 | 3.01 | 2.61 | 2.52 | 3.21 |
| wheat, less China | 2.83 | 2.80 | 2.23 | 2.12 | 2.80 |
| maize, less China | 2.03 | 1.86 | 1.49 | 1.71 | 1.75 |
| rice, less China | 1.57 | 1.71 | 1.67 | 1.78 | 2.20 |

(as of the year shown; data for the year before)

The market's stocks of wheat and maize (less China) were at their lowest
before the 2007-08 spike. Cover over the net drawdown runs to decades: the
world drew little from stocks net, so the gross ratio carries the signal.
Rice stocks were rising when its price spiked in 2008, the export-ban
case: a flow cut that the buffer, sitting with other holders, could not
cover.

**Sri Lanka's reserves** (EWN, FX reserves less gold, known a year
later). As of 2022, from 2021's data, cover over the one-year drawdown was
1.29 years, running out at 2022.29 (about April). The three-year
window gave 2.7 years. WDI's gross months of imports read 1.6. Sri Lanka
defaulted in April 2022. EWN puts end-2022 reserves at $33m, against
about $1.9bn official, so EWN already shows something close to the usable
figure. No usable-share series is on disk. This is one case, seen with
hindsight.

## Data still needed

- Usable shares: encumbered, pledged and swapped reserves (IMF reserve
  template, Data Template on International Reserves), China's grain
  stocks off the market.
- Restoration times by stock type (a prior per type for the hazard to
  learn from).
- Monthly reserves (IFS) for a sub-annual window.
- Futures prices for grain and oil, for the market reading. The World
  Bank pink sheet has spot prices only.
- In-season PSD vintages, rather than the current revised one.
- Sector-by-instrument balance sheets, to split the SFC matrix into
  buffers.
