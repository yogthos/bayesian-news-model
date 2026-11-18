"""JST 1870-2020: does a world 'turn' (credit stretched while real house
price growth decelerates) lead the share of countries starting a banking
crisis, against the outcome-based count of recent crises? Each signal is
causal: year y reads data through y; the target is onset share in y+1..y+2.
python3 -I turn_study.py <jst.csv>"""
import csv, sys, math
from collections import defaultdict

rows = defaultdict(dict)
for r in csv.DictReader(open(sys.argv[1])):
    try:
        y = int(r["year"])
    except ValueError:
        continue
    rows[r["iso"]][y] = r

def f(r, k):
    try:
        v = float(r.get(k, ""))
        return v if not math.isnan(v) else None
    except (TypeError, ValueError):
        return None

isos = sorted(rows)
years = range(1880, 2019)
# per country-year features, each from data up to y
feat = defaultdict(dict)
for u in isos:
    d = rows[u]
    for y in years:
        r = d.get(y)
        if not r: continue
        cg = [f(d.get(y - k, {}), "tloans") for k in range(0, 11)]
        gdp = [f(d.get(y - k, {}), "gdp") for k in range(0, 11)]
        ratio = [c / g if c and g else None for c, g in zip(cg, gdp)]
        hp = [f(d.get(y - k, {}), "hpnom") for k in range(0, 4)]
        cpi = [f(d.get(y - k, {}), "cpi") for k in range(0, 4)]
        rhp = [h / c if h and c else None for h, c in zip(hp, cpi)]
        if all(x is not None for x in ratio):
            gap = ratio[0] - sum(ratio[1:]) / 10.0          # credit/GDP above its 10-year mean
        else:
            gap = None
        if all(x is not None for x in rhp):
            g1 = math.log(rhp[0] / rhp[1]); g3 = math.log(rhp[0] / rhp[3]) / 3.0
            decel = g1 < g3                                   # house growth below its 3-year pace
        else:
            decel = None
        onset = f(r, "crisisJST") == 1.0
        feat[u][y] = {"gap": gap, "decel": decel, "onset": onset}

def share(y, pred):
    xs = [pred(feat[u][y]) for u in isos if y in feat[u]]
    xs = [x for x in xs if x is not None]
    return sum(xs) / len(xs) if xs else None

series = []
for y in years:
    stretched = share(y, lambda z: None if z["gap"] is None else z["gap"] > 0.05)
    turn = share(y, lambda z: None if z["gap"] is None or z["decel"] is None else (z["gap"] > 0.05 and z["decel"]))
    recent = sum(share(y - k, lambda z: z["onset"]) or 0 for k in range(0, 3))
    fut = [share(y + k, lambda z: z["onset"]) for k in (1, 2)]
    if None in (stretched, turn) or None in fut: continue
    series.append((y, stretched, turn, recent, sum(fut)))

def corr(a, b):
    n = len(a); ma = sum(a) / n; mb = sum(b) / n
    sa = math.sqrt(sum((x - ma) ** 2 for x in a)); sb = math.sqrt(sum((x - mb) ** 2 for x in b))
    return sum((x - ma) * (y - mb) for x, y in zip(a, b)) / (sa * sb)

print("years %d-%d, n=%d. Correlation with the share of countries starting a crisis in y+1..y+2:" % (series[0][0], series[-1][0], len(series)))
for name, i in (("stretched share (credit gap > 5 pts)", 1), ("turn share (stretched and house growth decelerating)", 2), ("recent crises (share in y-2..y, outcome-based)", 3)):
    print("  %-55s all %+.2f   pre-1940 %+.2f   post-1950 %+.2f" % (name, corr([s[i] for s in series], [s[4] for s in series]),
          corr([s[i] for s in series if s[0] < 1940], [s[4] for s in series if s[0] < 1940]),
          corr([s[i] for s in series if s[0] >= 1950], [s[4] for s in series if s[0] >= 1950])))
print("\nyear stretched turn recent future-onsets (post-1985)")
for s in series:
    if s[0] >= 1985: print("%d  %.2f  %.2f  %.2f  %.2f" % s)
