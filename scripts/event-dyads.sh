#!/usr/bin/env bash
# Events between countries per quarter (docs/STRUCTURE.md, plan step 1): from
# the normalized GDELT events, every event whose actors are two different
# countries, counted for the pair in alphabetical order, as cooperation
# (agree, consult, support, request, cooperate, aid, concede, retreat) or
# conflict (accuse, reject, threaten, sanction, mobilize, coerce, assault),
# any mode. Protest is left out: it is rarely one state's act on another.
# Quarters as bnm.onsets counts them: (12 year + month - 1) div 3.
#
#   scripts/event-dyads.sh [data/norm/gdelt] [data/slow/event-dyads.tsv]
set -euo pipefail
src=${1:-data/norm/gdelt}
dst=${2:-data/slow/event-dyads.tsv}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
find "$src" -name '*.tsv' -print0 | xargs -0 -n 200 -P 8 sh -c '
  awk -F"\t" "BEGIN {
    split(\"agree consult support request cooperate aid concede retreat\", c, \" \"); for (i in c) kind[c[i]] = \"coop\"
    split(\"accuse reject threaten sanction mobilize coerce assault\", f, \" \"); for (i in f) kind[f[i]] = \"conflict\"
  } {
    d = \$1; if (length(d) != 8) next
    a = \$2; b = \$4
    if (length(a) != 3 || length(b) != 3 || a == b) next
    k = kind[\$7]; if (k == \"\") next
    if (a > b) { t = a; a = b; b = t }
    q = int((12 * substr(d, 1, 4) + substr(d, 5, 2) - 1) / 3)
    s[a \"\t\" b \"\t\" q \"\t\" k] += \$9
  } END { for (x in s) print x \"\t\" s[x] }" "$@" > "'"$tmp"'/part.$$.$RANDOM"
' _
awk -F'\t' '{ s[$1 "\t" $2 "\t" $3 "\t" $4] += $5 } END { for (k in s) print k "\t" s[k] }' "$tmp"/part.* | sort > "$dst"
echo "wrote $dst ($(wc -l < "$dst") rows)" >&2
