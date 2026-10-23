# bayesian-news-model

A Jolt library that models world events as a hierarchy of connected,
self-exciting processes, and forecasts how they are likely to evolve. It
learns online from events that an LLM (or an event dataset) has coded from
the news. The whole model is plain data that can be stored in a database.

Its one dependency is [augur](https://github.com/jlt-commons/augur), the
statistics core (posteriors, calibration, hazards, scores and forecast tests),
which has none. [newsroom](../newsroom) uses it: newsroom
codes articles into events, feeds them to the model, and stores the model.

## Usage

```clojure
(require '[bnm.core :as bnm] '[bnm.extract :as extract])

;; code articles into events with an LLM: the prompt, then its parsed JSON answer
(def prompt (extract/prompt [{:n 1 :title "..." :text "..." :published "2026-10-14"}]))
(def events (extract/events parsed-answer "20261014"))
;; => [{:date "20261014" :a1 "ISR" :s1 "MIL" :a2 "LBN" :s2 "REB" :adm "LBN.06"
;;      :type :assault :mode :did :n 1} ...]

(def m (or (bnm/load store) (bnm/create)))        ; store: any bnm.store/ModelStore
(def m (bnm/add-events m "newsroom" events))      ; learns as weekly bins close
(def m (bnm/save! store m))                       ; writes only what changed
(bnm/risks (bnm/outlook m {:horizon 13}))         ; likeliest escalations next quarter
(bnm/drivers store "UKR:OPP" :protest)            ; one node, read without loading the model

;; scoring live, as the backtest scores: on the eve of a quarter, keep its
;; outlook; once the quarter is over it is scored, and the outcomes
;; recalibrate the model and teach it the climatology
(when (bnm/period-eve? m)
  (def m (bnm/register m (bnm/outlook m {:horizon 13}))))
(bnm/record m)                                    ; Brier scores, by kind and channel
```

### Scoring live

The model keeps a track of how its forecasts turn out (`bnm.track`), in quarters (`:period-bins`, 13 by default):
- the observed totals of the quarter in progress;
- each node's last six quarters, which give its stratum and the baseline an escalation is measured from (the last quarter's total);
- the climatology of escalation;
- the outlook registered on each quarter's eve;
- the scores of the last twelve quarters.

When a quarter ends, its outlook is scored against what was observed. The outcomes recalibrate the probabilities (`:p-cal`), the climatology learns from them, and the quarter joins the history. An outlook row carries the benchmarks it is scored against: the climatology's probability (`:p-clim`) and the node's history's (`:p-hist`). `bnm.train` run with `--every 13` saves its history and climatology with the model, so a model shipped from training carries them into live use.

### Storage

A model is a few named parts of plain data and one row per node, behind the `bnm.store/ModelStore` protocol: `read-part`, `read-nodes`, `node-ids` and `write!`. A host such as newsroom implements it over two tables:
- **parts** — `:header`, `:volumes`, `:pending` and `:track`, stored as text;
- **nodes** — id, level, links as EDN text, and data as a blob.

Each node's learned arrays are packed as little-endian doubles, about 6.6 KB per node. The model is kept in memory and saved after each batch of events. Before a weekly bin closes, a save writes only the buffered events; after one closes, it writes every node. A single node's drivers can be read straight from the store.

The library provides `memory-store` (for tests) and `file-store` (one binary file, for shipping a trained model).

## The model

The model is described in [DESIGN.md](DESIGN.md) and in `bnm.model`'s docstring.

- **Hierarchy.** From the bottom:
  - leaves: dyads (events between two countries), pairs (events between two actors of one country, such as `UKR:GOV|UKR:OPP`) and each country's remaining events;
  - local nodes: actors (a country's government, military, police, opposition, armed groups, identity groups, civil society and business) and provinces;
  - countries;
  - UN M49 subregions;
  - regions;
  - the world.

  Each event counts once for every node it touches. The busiest units get nodes of their own: by default 2,500 dyads, 1,500 actors, 1,500 pairs and 1,500 provinces.
- **Connections.** Every node's expected activity in six channels is driven by its own past, its parents' past and its strongest same-level neighbours' past, at two time scales. This is a multivariate Hawkes process. The six channels are verbal cooperation, material cooperation, verbal conflict, protest, coercion and violence. The learned excitation weights are the "what drives what" graph.
- **Recursion.** A parent predicts both from its own dynamics and from the sum of its children's predictions. It blends the two by recent accuracy, so forecasts are built up from the leaves.
- **Online learning.** Online EM with forgetting runs under scale-free Gamma priors, so every bin fed in is training and the model keeps refining itself. The priors are centred: before any evidence, a node's prior predicts exactly its typical rate. A fixed share of that comes from its baseline, and the sources share the rest by kind. A test checks this as a prior predictive check. Strictly, this is approximate, empirical Bayes: conjugate Gamma posteriors, estimated by EM and scaled by each node's observed mean.
- **Forecasts.** Forecasts are Monte Carlo simulations. Each simulation first draws the parameters from their posteriors, so forecast intervals carry the model's uncertainty about itself as well as the world's noise. Excitation is projected below criticality first, so a forecast cannot explode. Backtests use 100 simulations and report the Monte Carlo error of their probabilities.

## Data and backtesting

The historical record trains the model and tests it: give it the record up to a year, have it forecast the next year, and compare with what happened.

```sh
scripts/fetch-data.sh all          # GDELT 1.0, POLECAT, ICEWS into data/raw (~60 GB)
# optional, needs a myACLED account: ACLED_EMAIL=... ACLED_PASSWORD=... scripts/fetch-data.sh acled
scripts/ingest.sh gdelt 3          # normalize to data/norm/<source>, 3 processes
jolt -A:tools -M -m bnm.bins gdelt # weekly bins: data/bins/gdelt.tsv
jolt -A:tools -M -m bnm.train --sources gdelt --eval-from 2000 --name base --save-origins true
jolt -A:tools -M -m bnm.refit base '{:max-branching 0.9}'   # re-forecast saved origins
```

`bnm.train` makes one pass through time. From `--eval-from` on, at the start of every period (a calendar year, or with `--every 13` a quarter) it:
1. reshapes the structure from the volumes seen so far (yearly);
2. forecasts the period;
3. scores that forecast once the period has been observed.

Scoring (`bnm.eval`) follows the early-warning literature (see [docs/RESEARCH.md](docs/RESEARCH.md)): most escalation is continuation, so results are split by the stratum of each node's history (quiet, where an escalation is an onset; low; active), and set against benchmarks that know only that history (persistence, a three-period mean, the node's own record as a sample, its own rate of escalating, and climatology). `data/runs/<name>/report.txt` has:
- errors and CRPS on totals, per level and channel, against the benchmarks;
- for escalation: Brier skill against climatology, ROC AUC, average precision, and precision and recall among the top 10 per period, by stratum;
- per period, the places the model most expected to escalate, beside those that did and the onsets (`forecasts/`).

### Space

Located quantities are kept on A5 equal-area cells (`bnm.cells`, resolution 6, about 8,300 km²), one entry per country where a cell crosses a border; countries, provinces and regions aggregate them ([docs/PRINCIPLES.md](docs/PRINCIPLES.md), 11).

```sh
python -I scripts/cells.py           # crosswalk: cell -> country, province, area share (data/derived/cells)
python -I scripts/cell-assets.py     # plant and resource sources read into one form
jolt -A:tools -M -m bnm.cell-assets  # assets on cells: data/derived/cells/assets.csv
python -I scripts/cell-grids.py      # GDP, lights, built-up, EDGAR CO2 on cells: cell-grids.csv
jolt -A:tools -M -m bnm.cell-profiles-run  # profiles, paths, cell HHI (stocks/cells.csv), features
python -I scripts/located-shocks.py fetch   # ShakeMap MMI grids for M6+ earthquakes
python -I scripts/located-shocks.py build   # quakes, cyclones, floods, conflict onsets on cells (data/derived/hazards)
jolt -A:tools -M -m bnm.located-shocks-run --from 1991 --to 2009  # cell against country exposure (development years)
```

The crosswalk is drawn from Natural Earth's current borders (public domain) and Marine Regions' EEZs (CC BY 4.0) under `data/raw/geo/`; `bnm.cells/country-in-year` reads a cell's country for a past year (the USSR, Yugoslavia, Sudan before 2011, the two Germanys).

## Tests

```sh
jolt -M:test
```
