#!/usr/bin/env bash
# The event-code mix per country and quarter (docs/SHOCKS.md, plan step 1):
# event counts by type and mode from the normalized GDELT events, each
# counted for the country where it happened (the admin code's prefix), or
# the acting country when no place is given. Quarters as bnm.onsets counts
# them: (12 year + month - 1) div 3.
#
#   scripts/event-mix.sh [data/norm/gdelt] [data/slow/event-mix.tsv]
set -euo pipefail
src=${1:-data/norm/gdelt}
dst=${2:-data/slow/event-mix.tsv}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
find "$src" -name '*.tsv' -print0 | xargs -0 -n 200 -P 8 sh -c '
  awk -F"\t" "{
    d = \$1; if (length(d) != 8) next
    c = \$6; sub(/\\..*/, \"\", c); if (c == \"\") c = \$2
    if (c == \"\") next
    q = int((12 * substr(d, 1, 4) + substr(d, 5, 2) - 1) / 3)
    s[c \"\t\" q \"\t\" \$7 \"-\" \$8] += \$9
  } END { for (k in s) print k \"\t\" s[k] }" "$@" > "'"$tmp"'/part.$$.$RANDOM"
' _
awk -F'\t' '{ s[$1 "\t" $2 "\t" $3] += $4 } END { for (k in s) print k "\t" s[k] }' "$tmp"/part.* | sort > "$dst"
echo "wrote $dst ($(wc -l < "$dst") rows)" >&2
