# Signals: stocks and flows as one mechanic (bnm-cnf)

Written 18 November 2026, before the code.

## Why

The thesis (docs/INVARIANTS.md): a crisis is a stock stretched against its own history meeting a flow that turns, and which stock and which flow differ each time. Banking credit, food supply, energy production, reserves and terms of trade are all the same object: a series of values, each published at a known month, read as of an origin and transformed into a signal.

The simulation side already has one mechanic (bnm.stockflow models as data, bnm.engine modules with reads and emits). The forecast side did not: each kind hand-built its rows (lattice-banking's year-raw and extended-table, lattice-political's year rows, bnm.named's gauge builders), and the transforms existed in many copies. bnm.signals now holds the transforms once (stretch, aggregate, the running standardizers; test/bnm/signals_test.clj pins each against the copies it replaced). This document adds the other half: a signal is declared as data, and a node's inputs are assembled from declarations.

## A signal

```clojure
{:name   :tot12                       ; the column's name, unique in a node
 :var    "trade/commodity_tot"        ; a series in the slow table (bnm.named/load-series)
 :unit   :own                         ; whose series: :own (the row's country), or a fixed unit ("USA", "world")
 :read   [:change 12]                 ; how it is read at the origin (below)
 :times  {:var "energy/fuel_exports_share" :read [:value]}  ; optional: multiplied by another read (an exposure)
 :stale  30                           ; months a value stays current (bnm.stocks :stale)
 :domain :external                    ; its group in the node's layout
 :sign   +1                           ; theory's sign for fragility (+1 higher is more fragile, -1 less, 0 none)
 :prior  [0.2 0.0625]}                ; optional named prior [mean variance] on the standardized column
```

Reads, every one point in time (only values published by the origin, bnm.stocks):
- `[:value]`: the latest value (bnm.stocks/value).
- `[:change n]`: the latest value less the one n months of periods before it (bnm.stocks/change).
- `[:volatility n]`: the spread of successive changes over n months (bnm.stocks/volatility).
- `[:stretch opts]`: the latest published value against the published values before it, by observation (bnm.signals/stretch; opts :window :min :floor :floor-frac :cap :eps).
- `[:turn opts]`: the stretch's reversal, the thesis's turning flow: the size of the move back toward the mean when the previous observation was stretched past :threshold (1.0) and the latest moved back, 0 otherwise; nil when either stretch is unknown.

A missing read is nil; the node's standardizer reads nil as its mean (0), as every gauge today.

## A node's inputs

`(bnm.signals/assemble db decls)` gives `{:row (fn [unit t] vector) :names [...] :groups {domain [index ...]} :named-prior {name [m v]}}`: the row's entries in declaration order, groups by :domain, priors by name for the lattice's `:named-prior` (indices offset for the intercept there, as today). A node built from declarations reads nothing else; a kind adds a dimension by adding a declaration, not code.

## What moves onto it, and how it is checked

1. The transforms (done): stretch, aggregate, running standardizers. Gate: signals_test bit-equality, and t80-base reproduced byte for byte on all 13 kinds.
2. The banking redesign's new coverage (bnm-7bh step 1) is written as declarations from the start: commodity terms of trade and its turn times fuel and metal export shares, reserves in months of imports, the current account, World Bank private credit's stretch and turn.
3. Existing hand-built columns move onto declarations one builder at a time, each move gated by a byte-for-byte reproduction of the base: banking's extended-table single-series stretches first, then bnm.named's gauge builders, then the political year rows.
4. Engine emits as a source (`:module` in place of `:var`), so a stock-flow model's state is read the same way as a downloaded series.

Not moved: macrohistory's JST pipeline (the panel's own splices and dated inputs) stays as it is until step 3 reaches it; the lattice core's incremental standardizer (its state is threaded through bnm.lattice/step) and bnm.tension/zscore (its mean includes the value scored) are different transforms and keep their own code.

## Progress (18 November 2026)

- **Transforms** (ecddeed): bnm.signals' stretch, z-against, aggregate, running-z and history-standardizer replace the copies in bnm.invariants, bnm.precursors and lattice-banking (stretch-at, quarter-stretch, the gauge standardizer, the leading gauges' history standardizer). signals_test: 13,194 assertions, bit for bit.
- **Declarations** (aed747d, 8eb5932): reads (value, change, volatility, stretch, turn, lagged), exposures, and operators (mul, minus, div, neg, min, max, sum-or-zero, mean-of, fn for a quantity across countries).
- **bnm.named's builders moved** (acc31b1, 8eb5932, 935a6a5): leading, financial, terms-of-trade, fx, structure, political, political-economy (under each staleness override), institutions, youth. named_decl_test checks each against its hand-built reader on the slow table, every country and quarter end from 1985 or 1990: 341,292 assertions, equal.
- **Sources** (dcb10ec): `series-from-annual` reads bnm.states' tables (the stock tables, the coupled engine's states) as the slow table's series.
- **Still hand-built**: bnm.named's nonlinear, regime and leaders (quantile hinges across countries, election calendars), the event-stream loaders (counts, mix, ties, calm, sync, breaks, departure); lattice-banking's year-raw and extended-table (JST, PWT, WID tables, not series); the political year rows (bnm.political-gauges). The JST, PWT and WID tables can move the same way once read as series (series-from-annual's shape), each move gated by the base's reproduction.
