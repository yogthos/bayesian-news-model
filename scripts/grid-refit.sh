#!/usr/bin/env bash
# Re-forecasts a run's saved origins under several forecast settings.
#   scripts/grid-refit.sh RUN '{:level-noise 0.3}' '{:level-noise 0.5}' ...
cd "$(dirname "$0")/.."
run="$1"; shift
for cfg in "$@"; do
  out="data/runs/$run/refit-$(echo "$cfg" | tr -cd 'a-z0-9.-').txt"
  jolt -A:tools -M -m bnm.refit "$run" "$cfg" 30 > "$out" 2>&1
  echo "== $cfg"; grep -E "^  country|^  subregion   violence|^  region    violence" "$out" | grep -E "violence|verbal-conflict|protest"
done
