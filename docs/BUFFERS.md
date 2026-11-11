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
| `:id` `:name` `:unit` `:time-unit` | id `module/place/stock` for engine stocks. The time unit (year, quarter, month, week, day) is the unit of t, of the flows, the lag and every term below |
| `:holder {:unit :place}` | who holds it, and where (`country` = one per country) |
| `:kind` | `:material`, `:information` (a perception) or `:state` (a share or rate, like the wage share); only `:material` is a buffer |
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
invariant as of a date reads only what was known by then; of several
vintages of one t, the last published by then. A record's flags
(`:prior`, `:revised-vintage`, `:lag-unknown`) are carried onto its rows.

## The invariants

Each gives rows of one flat table: `stock, holder, place, date (as of),
invariant, value, sd, known (the latest input's known date), flags`, plus
`:window`, `:exhaustion` and `:band` where they apply. This is what the
regime switch (bnm-56c.4) and the hazard and scanner (bnm-56c.6) read. A
missing input gives a nil value and a flag, never a zero (bug bnm-as1).

Times are in years and rates per year whatever the record's time unit,
so a monthly stock and a yearly one compare. Windows are in years.
`:exhaustion` is a date on the series' own time axis.

| invariant | definition | unit |
|---|---|---|
| cover | usable stock over net drawdown, for each window (default 1, 3 and 5 years). Drawdown is outflow less inflow when both are measured within the window, else the stock's least-squares slope. `:exhaustion` is the last observation plus cover. A stock not draining gets the cap (100 years), flagged `:not-draining`, never a negative value. An empty stock gets 0, flagged `:empty`. With no usable share, the headline is used and flagged `:usable-missing`. A declared flow whose unit is not the stock's per time is flagged `:flow-unit-mismatch` | years |
| gross-cover | usable stock over the mean gross outflow in the first window: how long the stock lasts if the inflow stops (stocks-to-use, reserves over imports), where cover is how long it lasts at the current imbalance. nil, `:missing-outflow`, without one | years |
| drain-rate | net drawdown over usable stock: the inverse of cover, continuous through zero, negative when the stock grows; none for an empty stock | 1/year |
| restoration | time to restore the inflow times the drain rate, which is that time over cover but continuous past the cap and negative for a growing stock. Above 1 it is flagged `:runs-out-first`. An empty stock has run out: nil, `:empty` | ratio |
| rigidity | rigid share of each outflow, weighted by the outflow's level; a record with no outflows reads a `:rigidity` series (a share measured directly, say the debt service ratio). An outflow of unknown rigidity bounds the share between all cut and all rigid: the midpoint, with a uniform spread over the bounds, flagged `:partial-rigidity`. nil when no outflow's rigidity is known | share |
| carry | yield less (cost + decay, or 1/life), all in the record's time unit | 1/year |
| claim-gap | claims over the flow that services them. Minsky's bands on the same ratio (Foley 2003): hedge when income covers interest and principal, at or below 1/(r + 1/m); speculative when it covers the interest, at or below 1/r (any ratio at r = 0); otherwise Ponzi. Claims and income of different dates are flagged `:dates-differ` | years |
| idle-share | installed but not earning, with what binds instead | share |
| holder-hhi, holder-leverage | concentration over holders, and the holder's leverage | index, ratio |
| market-reading | the cover a price implies: competitive storage's convex price (bnm.food's storage pressure, a curve whose terms are data) inverted, cover_ref((1 + c)/(p/p_ref) - c). The cover is what `:ref-cover` measures, stock over gross use when the curve is fitted to stocks-to-use, not the net-drawdown cover above | years |
| displacement | the stock's stretch against its own past at 5, 10 and 20 observations (`bnm.invariants/stretch`); a record's `:displace` names another series to measure (claims, a price, an investment flow) | z |
| resonance | the smallest absolute stretch of the three: displaced at every scale | z |
| stock-x-flow | absolute stretch of the stock times that of its net flow | z² |
| breadth | across stocks at one date, the share of displacements past 1.5 (`bnm.invariants/aggregate`) | share |

The uncertainty propagates the parts' sds to first order, in absolute
terms (sd of a/b is sqrt((sa/b)^2 + (a sb/b^2)^2)), so a ratio whose
numerator is zero keeps its spread: the usable share's sd, the
drawdown's standard error (from residuals and the observations' own
`:sd`), and the restoration time's sd. A cover at the cap has sd 0; the
drain rate carries the spread there. A z-score's sd is its sampling
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
  sfc-finance's balance sheet takes the flow of funds. Such a flow is
  marked `:net`: its direction is not known, and revaluations reach the
  balance sheet without being a flow.

The publication lag is the one known rule the module's level emits (or
all its emits) share. A stock with no unit takes the one its flows share
per time (World3's stocks, flagged `:unit-from-flows`).

Today (`tools/bnm/buffers_run.clj`, data/derived/buffers/report.txt):
134 modules, 148 stock records (55 material, 73 state, 20 information),
208 level emits. The state variables are marked in the mapping file:
sfc-accounts' wage share and employment in 35 countries, Keen's omega
and lambda, technology's adoption. 51 material stocks have flows:
World3's 15 (population 4, capital 2, agriculture 4, resources 1,
pollution 4), sfc-finance's balance sheet in 35 countries, and Keen's
debt (by mapping). 4 have none and need mappings: cycles' capacity and
pipeline, food's and food-world's grain stock.

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

**Grain, USDA PSD around 2007-08** (current vintage, revised since: the
EU's 2007/08 figures were last revised in 2015; known a year after the
market year, and flagged `:revised-vintage`). World stocks-to-use, in
months:

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
defaulted in April 2022. EWN puts end-2022 reserves at $33m and end-2023
at $69m, against about $1.9bn and $4.4bn official. That is not the
usable figure: the gap with the official series after 2021 is
unexplained, so EWN's recent values for Sri Lanka should not be read as
usable reserves. No usable-share series is on disk. This is one case,
seen with hindsight, from a revised vintage.

## Regime switch

`bnm.engine` (bead bnm-56c.4; PLAN 1.3; the engine docstring has the
rules). A break is a regime switch: every outflow from a stock is the
lesser of what is wanted and what the stock can supply in the step.

**The constraint.** A module stock that declares `:outflows` is held by
the engine. The module says what it wants to send (its own rate emits);
the engine gives the stock's level (a `:level` emit, from `:init`) and
integrates it. Each step the stock can supply its level plus the step's
inflows less a floor (`:floor`: 0 by default, or a minimum operating
level as a number, a parameter, or an emit or read, so it can move with
data). Outflows wanted beyond that are cut. The stock is `:buffered` when
nothing is cut and `:constrained` when something is. Nothing in the rule
names a kind of stock.

**The sharing rule** is data per stock (`:sharing`): `:pro-rata` by
default, each outflow cut by the same share; or `:rigid-first`, each
outflow's rigid share (the schema's `:rigid`, 0 when not given) served
before any flexible part, the flexible parts then pro rata. A part-rigid
flow is split: its rigid share first, its flexible share with the rest.

**Propagation.** Readers see only what was delivered: the cut is made
right after the module's step, before anything reads its emits, so the
whole-step rule holds (no reader ever sees two values of one emit, and a
held level changes only between steps). A cut outflow is then a cut
inflow to whatever reads it. A reader at lag 0 (later in the causal
order) gets it the same step, and its own stock can switch the same
step; a reader at lag n gets it n years later. So a cascade runs at the
lags of the wiring, and no chain is written by hand. In an ensemble a
shared module's held stocks are the stepping member's in every member,
and an analysis may not rewrite what a held stock settled with
`update-emits`; it corrects the stock with `update-holdings`.

**Where a cut lands** is data per outflow (`:shortfall`):

| booking | what it is |
|---|---|
| `:arrears` | a claim of the counterpart on the holder; it accumulates (the holder's liability, the counterpart's asset, so it nets to zero) |
| `:forced-saving` | the counterpart keeps what it would have paid: its saving rises above plan and the holder's receipts fall below it by the same amount |
| `:unmet-demand` (default) | the counterpart wanted the flow and went without |
| `:price` | the delivered amount clears at a price higher by wanted over delivered |

Each step's record per stock (`regimes`, and `:regimes` in a run's
result) holds the level, inflow, floor, available amount, wanted,
delivered, shortfall, regime, the outflows cut and, per outflow, its
booking. `holdings` gives the levels and each booking's running total.
Only arrears are a stock: `arrears` turns them into claims, the holder's
negative and its counterpart's positive, which sum to zero, so an outflow
booked as arrears must name its `:counterpart`. The other bookings are
records; none moves money. A cut reaches the counterpart's accounts only
through the wiring, so when the counterpart is a module of the run it must
read the flow, and a stock it holds there must count it as an inflow;
`validate` refuses the run otherwise, since what was delivered would leave
one account and enter none. That is what keeps forced saving and price
bookings consistent. The SFC core cannot hold a stock today (its flows are
matrices); wired in, its identities would hold for the same reason, with
every sector reading the delivered flow.

**Missing values.** A missing or NaN level, inflow, outflow or floor makes
the step `:unknown`: what went and the level after are NaN, readers see
NaN, and an outflow not emitted stays not emitted. Nothing becomes a zero,
except a lagged inflow before the run's start: the run starts with nothing
in transit. An inflow that runs negative is not the stock's to cut and can
take it below its floor; the record flags `:below-floor`.

**Accounts.** A held stock moves only by what was delivered, so cuts
create and destroy nothing. In a test economy of three sectors that pay
each other in a ring with every stock constrained in turn, the stocks
plus what is in transit on the lagged edges stay at their starting total
to 1e-10. Each stock's next level equals its level plus delivered inflow
less delivered outflow, and each outflow's wanted amount equals what was
delivered plus what was booked (test/bnm/regime_test.clj).

**The default run.** The constraint is always on, but only for stocks
that declare outflows. No module in today's golden runs does: World3
integrates its own stocks, and the SFC core's balance sheet is a matrix.
So it cannot bind there. `tools/bnm/coupled_run.clj`'s golden checks
pass, and the coupled table is byte-identical to the one before the
change. A test checks that World3's modules hold no stock and leave no
regime record.

**Synthetic demonstrations** (test/bnm/regime_test.clj): a stock drained
to its floor switches on the step its level plus inflow less the floor
first falls short of what is wanted. In a chain of three stocks, A
exhausted cuts B and then C, a year apart each when the inflows are read
a year late, and all in the same step when they are read at lag 0. With
a rigid debt service and flexible imports, rigid-first cuts only the
imports, while pro rata cuts both and books the debt service's shortfall
as arrears. A run whose series change after a date has the same records
up to that date.

**One real illustration, descriptive only** (`tools/bnm/regime_run.clj`,
case in `resources/buffers/regimes.edn`, output
data/derived/buffers/regime-demo.txt). Sri Lanka's foreign reserves
(WDI, ex gold, USD m) go through the reserves record in
`resources/buffers/stocks.edn`: debt service is rigid and booked as
arrears, imports are flexible and booked as unmet demand. The wanted
outflows are the recorded imports and debt service. The inflow is the
residual that closes the recorded change in reserves. The floor is the
reserves committed to short forward positions within a year (IMF IRFCL,
as of the December before): 357 for 2020, 1,373 for 2021, 2,143 for
2022.

| year | level | floor | wanted | regime | cut |
|---|---|---|---|---|---|
| 2018 | 7,031 | 1,495 | 34,236 | buffered | |
| 2019 | 6,100 | 723 | 30,803 | buffered | |
| 2020 | 6,690 | 357 | 23,320 | buffered | |
| 2021 | 5,257 | 1,373 | 26,276 | buffered | |
| 2022 | 2,962 | 2,143 | 22,578 | constrained | imports 275 (rigid first); imports 234 and debt service 41 in arrears (pro rata) |
| 2023 | 2,143 | 2,263 | 22,033 | buffered | |

The replay switches in 2022, the year Sri Lanka defaulted (April) and ran
short of fuel and imports. With no floor it never switches, since
recorded reserves never reached zero. Recorded reserves ended 2022 at
1,869, below the floor: the encumbered part was drawn on. The cut is
small (1.2% of what was wanted) because the wanted flows are the recorded
ones, which already include the import compression of 2022. In reality
the debt service, not the imports, went into arrears, which the
pro-rata rule shows only in part. This is one case, replayed with
hindsight from data years and current vintages. It is an illustration,
not a result.

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
