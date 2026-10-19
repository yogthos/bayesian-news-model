#!/bin/sh
# Sums the weekly bins into bins k weeks wide: data/bins/<source>-<7k>.tsv.
# Exact, since floor(day / 7k) = floor(floor(day / 7) / k).
#   scripts/rebin.sh gdelt 4    # 28-day bins
set -e
src=$1; k=$2
out="data/bins/$src-$((7 * k)).tsv"
awk -F'\t' -v k="$k" 'BEGIN{OFS="\t"} {b=int($1/k); key=b FS $2 FS $3 FS $4 FS $5 FS $6 FS $7; s[key]+=$8; if (b!=last) {for (x in s) if (x!=key) {print x, s[x]; delete s[x]} last=b}} END{for (x in s) print x, s[x]}' "data/bins/$src.tsv" \
  | sort -t"$(printf '\t')" -k1,1n -k2,6 -k7,7n -S 1G > "$out"
echo "wrote $out"
