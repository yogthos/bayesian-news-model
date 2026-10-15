#!/usr/bin/env bash
# Normalize one source's raw files on N processes: scripts/ingest.sh gdelt 3
cd "$(dirname "$0")/.."
n="${2:-3}"
for ((i = 0; i < n; i++)); do
  nohup jolt -A:tools -M -m bnm.ingest "$1" "$i" "$n" >> "data/ingest-$1-$i.log" 2>&1 &
done
wait
echo "ingest $1 done" >> "data/ingest-$1-0.log"
