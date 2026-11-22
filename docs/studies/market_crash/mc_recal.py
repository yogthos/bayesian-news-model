# Proxy: shift each December origin's year log odds by the calibration-in-the-large
# gap over the trailing N years of rows resolved and published by then (origin <= o-24).
import sys, os, math, collections
run, kind = sys.argv[1], sys.argv[2]
rows = collections.defaultdict(list)
for line in open(os.path.join("data/runs", run, kind, "forecasts.tsv")):
    s, place, o, p, y = line.rstrip("\n").split("\t")
    o = int(o)
    if s == "year" and len(place) == 3 and o % 12 == 11 and y in ("true", "false"):
        rows[o // 12].append((min(max(float(p), 1e-4), 1 - 1e-4), y == "true"))
lg = lambda p: math.log(p / (1 - p)); ex = lambda x: 1 / (1 + math.exp(-x))
def score(ps):  # brier, alert cost at 0.2 with miss 4
    n = len(ps); b = sum((p - y) ** 2 for p, y in ps) / n
    c = sum(4 * (y and p < 0.2) + ((not y) and p >= 0.2) for p, y in ps) / n
    return b, c
for N in (0, 5, 10, 20):
    for shrink in (1.0, 0.5):
        out = collections.defaultdict(list)
        for yr in range(2003, 2023):
            past = [r for t in range(yr - 1 - N, yr - 1) for r in rows.get(t, [])] if N else []
            sh = 0.0
            if past:
                mp = sum(p for p, _ in past) / len(past); rt = (sum(y for _, y in past) + 0.5) / (len(past) + 1)
                sh = shrink * (lg(rt) - lg(mp))
            for p, y in rows.get(yr, []):
                out["2003-09" if yr < 2010 else "2010-22"].append((ex(lg(p) + sh), y)); out["all"].append((ex(lg(p) + sh), y))
        if N == 0 and shrink == 0.5: continue
        print("N=%2d shrink=%.1f  " % (N, shrink) + "  ".join("%s brier %.4f cost %.3f" % ((e,) + score(out[e])) for e in ("2003-09", "2010-22", "all")))
