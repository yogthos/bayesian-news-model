# bayesian-news-model

A Jolt library that models world events as a hierarchy of connected,
self-exciting processes, and forecasts how they are likely to evolve. It
learns online from events that an LLM (or an event dataset) has coded from
the news. The whole model is plain data that can be stored in a database.

The library has no dependencies. [newsroom](../newsroom) uses it: newsroom
codes articles into events, feeds them to the model, and stores the model.

## Usage

```clojure
(require '[bnm.core :as bnm] '[bnm.extract :as extract])

;; code articles into events with an LLM: the prompt, then its parsed JSON answer
(def prompt (extract/prompt [{:n 1 :title "..." :text "..." :published "2026-10-14"}]))
(def events (extract/events parsed-answer "20261014"))
;; => [{:date "20261014" :a1 "ISR" :a2 "LBN" :type :assault :mode :did :n 1} ...]

(def m (-> (bnm/create) (bnm/add-events "newsroom" events)))   ; learns as weekly bins close
(bnm/risks (bnm/outlook m {:horizon 13}))       ; likeliest escalations next quarter
(bnm/connections m "UKR" :violence)             ; what drives Ukraine's violence

(let [{:keys [meta nodes]} (bnm/->rows m)] ...) ; store; restore with bnm/load-rows
```

## The model

The model is described in [DESIGN.md](DESIGN.md) and in `bnm.model`'s docstring.

- **Hierarchy.** The nodes are:
  - dyads (events between two countries) and each country's remaining events;
  - countries;
  - UN M49 subregions;
  - regions;
  - the world.
  Each event counts once for every node it touches.
- **Connections.** Every node's expected activity in six channels is driven by its own past, its parents' past and its strongest same-level neighbours' past, at two time scales. This is a multivariate Hawkes process. The six channels are verbal cooperation, material cooperation, verbal conflict, protest, coercion and violence. The learned excitation weights are the "what drives what" graph.
- **Recursion.** A parent predicts both from its own dynamics and from the sum of its children's predictions. It blends the two by recent accuracy, so forecasts are built up from the leaves.
- **Online learning.** Online EM with forgetting runs under scale-free Gamma priors. Every bin fed in is training, so the model keeps refining itself.
- **Forecasts.** Forecasts are Monte Carlo simulations. Excitation is projected below criticality first, so a forecast cannot explode.

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

## Tests

```sh
jolt -M:test
```
