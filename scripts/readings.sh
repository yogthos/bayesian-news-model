#!/usr/bin/env bash
# Today's readings, end to end (docs/OPERATIONS.md):
#
#   scripts/readings.sh            rebuild the derived inputs and read the models on the data in hand
#   scripts/readings.sh --fetch    first refresh the data (slow sources, markets, GDELT, normalize, bins)
#   scripts/readings.sh --onsets   also refit the onset hazards on fresh rows (hours)
#
# Writes data/derived/readings.txt.
set -euo pipefail
cd "$(dirname "$0")/.."

FETCH=0 ONSETS=0
for a in "$@"; do
  case "$a" in
    --fetch) FETCH=1 ;;
    --onsets) ONSETS=1 ;;
    *) echo "unknown option $a" >&2; exit 1 ;;
  esac
done

step() { echo; echo "== $*"; }
mkdir -p data/derived

if [[ $FETCH == 1 ]]; then
  step "fetch slow sources and markets"
  scripts/fetch-slow.sh fred
  scripts/fetch-slow.sh worldbank
  scripts/fetch-slow.sh bis
  scripts/fetch-slow.sh oecd
  scripts/fetch-slow.sh market
  scripts/fetch-slow.sh shocks
  scripts/fetch-slow.sh events
  step "fetch GDELT, normalize, bin"
  scripts/fetch-data.sh gdelt
  scripts/ingest.sh gdelt 3
  jolt -A:tools -M -m bnm.bins gdelt
  step "normalize slow sources"
  python3 scripts/slow-normalize.py
fi

step "trader inputs"
jolt -A:tools -M -m bnm.trader-export

step "bear regime (Markov switching, refit yearly)"
uv run --no-project --with statsmodels==0.15.0 --with pandas python -I scripts/regime-hmm.py \
  data/derived/trader-inputs.tsv data/derived/regimes.tsv

step "risk learned on small falls"
jolt -A:tools -M -m bnm.scales --name scales-live

step "credit gap on the macrohistory panel (one-sided HP)"
uv run --no-project --with pandas --with statsmodels==0.15.0 python -I scripts/jst-credit-gap.py \
  data/raw/slow/jst/jst.csv data/derived/jst-credit-gap.csv

step "crisis coefficients from the macrohistory panel"
jolt -A:tools -M -m bnm.macrohistory --name macro-live --waves waves-1 > /dev/null

step "US drawdown hazard of record"
jolt -A:tools -M -m bnm.trader --name trader-live > /dev/null

if [[ $ONSETS == 1 ]]; then
  step "onset hazards on fresh rows (hours)"
  jolt -A:tools -M -m bnm.onsets --name onsets-live \
    --kinds conflict_onset,war_onset,protest_surge,violence_surge,coup,banking_crisis --models history,named
  cp data/runs/onsets-live/preds.tsv data/derived/onset-preds.tsv
fi

step "event model: catch up and outlook"
jolt -A:tools -M -m bnm.live

step "readings"
jolt -A:tools -M -m bnm.readings
