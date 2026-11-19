"""Brier in the wave years (t113's registered side test): year country
nodes, December origins 2013-2015 and 2019-2022, mean Brier of each run
and the gain of the second over the first (+ = second better), per kind.
python3 -I wave_brier.py <repo> bench run [kind ...]"""
import sys, os
repo, bench, run, kinds = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
WAVES = {"2013-15": range(2013, 2016), "2019-22": range(2019, 2023)}

def rows(r, k):
    d = {}
    for l in open(os.path.join(repo, "data/runs", r, k, "forecasts.tsv")):
        s, pl, o, p, y = l.rstrip("\n").split("\t"); o = int(o)
        if s == "year" and len(pl) == 3 and y in ("true", "false") and o % 12 == 11:
            d[(pl, o // 12)] = (float(p), y == "true")
    return d

for k in kinds:
    a, b = rows(bench, k), rows(run, k)
    out = []
    for name, yrs in WAVES.items():
        ks = [x for x in a if x[1] in yrs and x in b]
        ba = sum((a[x][0] - a[x][1]) ** 2 for x in ks) / len(ks)
        bb = sum((b[x][0] - b[x][1]) ** 2 for x in ks) / len(ks)
        out.append("%s %+.5f (onsets %d)" % (name, ba - bb, sum(a[x][1] for x in ks)))
    print("%-21s %s" % (k, "   ".join(out)))
