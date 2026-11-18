"""bnm-ris: each group's own issued forecast at banking's year country
nodes (world-run --trace-groups), against the lattice's combined forecast
on the same rows, and the weight the averaging gave each group. If the
groups that read credit rank and score well alone, the combination (the
averaging, the frailty) loses them; if not, their learning does.
python3 -I banking_groups.py <repo> <trace.tsv> <run>"""
import math, os, sys, bisect
from collections import defaultdict

repo, trace, run = sys.argv[1], sys.argv[2], sys.argv[3]

def expit(x):
    return 1.0 / (1.0 + math.exp(-x)) if x > -700 else 0.0

def auc(ps):
    pos = [p for p, y in ps if y]; neg = sorted(p for p, y in ps if not y)
    if not pos or not neg: return float("nan")
    return sum(bisect.bisect_left(neg, p) + 0.5 * (bisect.bisect_right(neg, p) - bisect.bisect_left(neg, p)) for p in pos) / (len(pos) * len(neg))

def ll(p, y):
    p = min(1 - 1e-9, max(1e-9, p)); return math.log(p if y else 1 - p)

outcome, combined = {}, {}
for line in open(os.path.join(repo, "data/runs", run, "banking_crisis/forecasts.tsv")):
    s, u, o, p, y = line.rstrip("\n").split("\t")
    if s == "year" and len(u) == 3 and y in ("true", "false") and int(o) % 12 == 11:
        outcome[(u, int(o))] = y == "true"; combined[(u, int(o))] = float(p)

groups = defaultdict(dict)
for line in open(trace):
    k, u, o, g, f, q = line.rstrip("\n").split("\t")
    o = int(o)
    if (u, o) in outcome:
        # the predictive's probability as the averaging scores it (probit approximation)
        groups[g][(u, o)] = expit(float(f) / math.sqrt(1.0 + math.pi * float(q) / 8.0))

weights = defaultdict(dict)
for line in open(os.path.join(repo, "data/runs", run, "banking_crisis/weights.tsv")):
    s, lv, yr, g, w = line.rstrip("\n").split("\t")
    if s == "year" and lv == "country": weights[g][int(yr)] = float(w)

JST = set("AUS BEL CAN CHE DEU DNK ESP FIN FRA GBR IRL ITA JPN NLD NOR PRT SWE USA".split())

def within(ps):
    """AUC within each origin, pooled with each origin weighed by its pairs."""
    by = defaultdict(list)
    for (u, o), (p, y) in ps.items(): by[o].append((p, y))
    num = den = 0.0
    for rows in by.values():
        a = sum(1 for _, y in rows if y); b = len(rows) - a
        if a and b: num += auc(rows) * a * b; den += a * b
    return num / den if den else float("nan")

for a, b in ((1980, 2002), (2003, 2022)):
    keys = [k for k in outcome if a <= k[1] // 12 <= b]
    rate = sum(outcome[k] for k in keys) / len(keys)
    clim = sum(ll(rate, outcome[k]) for k in keys) / len(keys)
    print("%d-%d rows %d onsets %d   (log skill per row against the span's own rate)" % (a, b, len(keys), sum(outcome[k] for k in keys)))
    rows = [("combined", combined)] + sorted(groups.items())
    for g, ps in rows:
        ks = [k for k in keys if k in ps]
        if not ks: continue
        w = [weights[g].get(k[1] // 12, 0.0) for k in ks] if g != "combined" else [1.0]
        print("  %-14s AUC %.3f  within %.3f  within JST %.3f  log skill %+.4f  mean p %.3f  mean weight %.3f" % (
            g, auc([(ps[k], outcome[k]) for k in ks]),
            within({k: (ps[k], outcome[k]) for k in ks}),
            within({k: (ps[k], outcome[k]) for k in ks if k[0] in JST}),
            sum(ll(ps[k], outcome[k]) for k in ks) / len(ks) - clim,
            sum(ps[k] for k in ks) / len(ks), sum(w) / len(w)))
