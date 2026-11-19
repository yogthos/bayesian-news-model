"""The level bug across kinds: does the year node's yearly mean p follow the
rate of earlier years (the outcomes known by the origin) rather than the
year's own? And what would a level held at its long-run value do?

For each kind, year country nodes at December origins:
- r of the yearly mean p (2003-2022) with the yearly rate k years earlier,
  k = 0..5 (the rate of origin Y-k's rows);
- what Brier would be with each year's log odds shifted so its mean p is
  the long-run rate of every row known by the origin (origins <= Y-3, the
  24-month window and the catalogue's lag), by fold; and the same shift
  halfway. Offline, a diagnosis only: nothing here is a forecast of record.

python3 -I level_lag.py <repo> <run>"""
import sys, os, math
from collections import defaultdict

repo, run = sys.argv[1], sys.argv[2]
FOLDS = [(2003, 2006), (2007, 2009), (2010, 2012), (2013, 2019), (2020, 2022)]
lg = lambda p: math.log(p / (1 - p))
ex = lambda x: 1 / (1 + math.exp(-x))

def rows(kind):
    by = defaultdict(list)
    for line in open(os.path.join(repo, "data/runs", run, kind, "forecasts.tsv")):
        s, pl, o, p, y = line.rstrip("\n").split("\t")
        o = int(o)
        if s == "year" and len(pl) == 3 and y in ("true", "false") and o % 12 == 11:
            by[o // 12].append((min(max(float(p), 1e-6), 1 - 1e-6), y == "true"))
    return by

def corr(a, b):
    n = len(a); ma = sum(a) / n; mb = sum(b) / n
    sa = math.sqrt(sum((x - ma) ** 2 for x in a)); sb = math.sqrt(sum((x - mb) ** 2 for x in b))
    return sum((x - ma) * (y - mb) for x, y in zip(a, b)) / (sa * sb) if sa and sb else float("nan")

def shift_to(ps, target):
    lo, hi = -10.0, 10.0
    for _ in range(60):
        m = (lo + hi) / 2
        if sum(ex(lg(p) + m) for p in ps) / len(ps) < target: lo = m
        else: hi = m
    return (lo + hi) / 2

kinds = sorted(k for k in os.listdir(os.path.join(repo, "data/runs", run)) if os.path.isdir(os.path.join(repo, "data/runs", run, k)))
print("kind                  r(p_Y, rate_Y-k) k=0..5                  | Brier gain by fold if level = long-run known rate (full / half); + better")
for k in kinds:
    by = rows(k)
    yrs = list(range(2003, 2023))
    if not all(y in by for y in yrs): continue
    mp = [sum(p for p, _ in by[y]) / len(by[y]) for y in yrs]
    rate = {y: sum(o for _, o in v) / len(v) for y, v in by.items()}
    rs = [corr(mp, [rate.get(y - j, float("nan")) for y in yrs]) for j in range(6)]
    gains = []
    for a, b in FOLDS:
        g_full = g_half = 0.0; n = 0
        for y in range(a, b + 1):
            known = [o for yy, v in by.items() if yy <= y - 3 for _, o in v]
            if not known: continue
            lr = sum(known) / len(known)
            d = shift_to([p for p, _ in by[y]], lr)
            for p, o in by[y]:
                b0 = (p - o) ** 2
                g_full += b0 - (ex(lg(p) + d) - o) ** 2
                g_half += b0 - (ex(lg(p) + d / 2) - o) ** 2
                n += 1
        gains.append("%+.5f/%+.5f" % (g_full / n, g_half / n))
    print("%-21s %s | %s" % (k, " ".join("%+.2f" % r for r in rs), "  ".join(gains)))
