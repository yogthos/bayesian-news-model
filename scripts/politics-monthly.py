#!/usr/bin/env python3
"""Monthly event counts per country and channel from a source's weekly bins
(data/bins/<source>.tsv, see bnm.bins), for the slow layer's politics domain:

  data/raw/slow/politics/<source>.tsv   unit month channel count total

A record counts for each country it involves. total is every event the
source reported that month, so the normalizer can read each count as a
share of coverage, which removes the source's growth.

  python3 -I scripts/politics-monthly.py [gdelt]
"""
import datetime, os, sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
source = sys.argv[1] if len(sys.argv) > 1 else "gdelt"
EPOCH = datetime.date(1970, 1, 1)
BIN_DAYS = 7

counts = defaultdict(float)
totals = defaultdict(float)
month_of_bin = {}
with open(os.path.join(ROOT, "data", "bins", source + ".tsv")) as f:
    for line in f:
        b, a1, s1, a2, s2, adm, c, n = line.rstrip("\n").split("\t")
        m = month_of_bin.get(b)
        if m is None:
            d = EPOCH + datetime.timedelta(days=int(b) * BIN_DAYS + BIN_DAYS // 2)
            m = month_of_bin[b] = "%04d-%02d" % (d.year, d.month)
        n = float(n)
        totals[m] += n
        counts[(a1, m, c)] += n
        if a2 and a2 != a1:
            counts[(a2, m, c)] += n

out = os.path.join(ROOT, "data", "raw", "slow", "politics", source + ".tsv")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w") as f:
    for (unit, m, c), n in sorted(counts.items()):
        if len(unit) == 3 and unit.isalpha():
            f.write("%s\t%s\t%s\t%g\t%g\n" % (unit, m, c, n, totals[m]))
print("%d months, %d rows" % (len(totals), len(counts)), file=sys.stderr)
