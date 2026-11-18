"""JST 1880-2016: a dimension-agnostic turn invariant. Each dimension's
stretch is its z against the country's previous 10 years (at least 6), as
bnm.invariants/stretch. Per country-year: stretched = share of dimensions
with |z| >= 1; reversing = share of dimensions with |z_{y-1}| >= 1 now moving
back toward their mean; turn = stretched x reversing. World gauges: their
means over countries. Correlated with the share of countries starting a
crisis in y+1..y+2, all years, and by era. python3 -I turn_dims.py jst.csv"""
import csv, sys, math
from collections import defaultdict

def num(s):
    try:
        v = float(s); return None if math.isnan(v) else v
    except (TypeError, ValueError):
        return None

data = defaultdict(dict)
for r in csv.DictReader(open(sys.argv[1])):
    y = num(r.get("year"))
    if y is not None: data[r["iso"]][int(y)] = r

def div(a, b): return a / b if a is not None and b else None
def lg(x): return math.log(x) if x and x > 0 else None
dims = {
    "credit": lambda r, p: div(num(r.get("tloans")), num(r.get("gdp"))),
    "mortgages": lambda r, p: div(num(r.get("tmort")), num(r.get("gdp"))),
    "house": lambda r, p: lg(div(num(r.get("hpnom")), num(r.get("cpi")))),
    "investment": lambda r, p: num(r.get("iy")),
    "current-account": lambda r, p: div(num(r.get("ca")), num(r.get("gdp"))),
    "public-debt": lambda r, p: num(r.get("debtgdp")),
    "inflation": lambda r, p: (lg(num(r.get("cpi"))) - lg(num(p.get("cpi")))) if p and lg(num(r.get("cpi"))) is not None and lg(num(p.get("cpi"))) is not None else None,
    "short-rate": lambda r, p: num(r.get("stir")),
    "term-spread": lambda r, p: (num(r.get("ltrate")) - num(r.get("stir"))) if num(r.get("ltrate")) is not None and num(r.get("stir")) is not None else None,
    "unemployment": lambda r, p: num(r.get("unemp")),
    "leverage": lambda r, p: num(r.get("lev")),
    "exchange-rate": lambda r, p: lg(num(r.get("xrusd"))),
}
isos = sorted(data)
series = {u: {k: {y: f(data[u][y], data[u].get(y - 1)) for y in data[u]} for k, f in dims.items()} for u in isos}

def stretch(xs, y):
    x = xs.get(y)
    past = [xs.get(y - k) for k in range(1, 11)]
    past = [v for v in past if v is not None]
    if x is None or len(past) < 6: return None
    m = sum(past) / len(past); sd = math.sqrt(sum((v - m) ** 2 for v in past) / len(past))
    return None if sd < 1e-12 else max(-5.0, min(5.0, (x - m) / sd))

def country(u, y):
    st, rev, n = 0, 0, 0
    for k in dims:
        z, z0 = stretch(series[u][k], y), stretch(series[u][k], y - 1)
        if z is None or z0 is None: continue
        n += 1
        if abs(z) >= 1: st += 1
        if abs(z0) >= 1 and (z - z0) * (1 if z0 > 0 else -1) < 0: rev += 1
    if n < 5: return None
    return st / n, rev / n, (st / n) * (rev / n)

def onset(u, y): return num(data[u].get(y, {}).get("crisisJST")) == 1.0
def share_onset(y):
    xs = [onset(u, y) for u in isos if y in data[u]]
    return sum(xs) / len(xs) if xs else None

rows = []
for y in range(1880, 2017):
    cs = [c for c in (country(u, y) for u in isos if y in data[u]) if c]
    f = [share_onset(y + 1), share_onset(y + 2)]
    if len(cs) < 8 or None in f: continue
    rows.append((y, sum(c[0] for c in cs) / len(cs), sum(c[1] for c in cs) / len(cs), sum(c[2] for c in cs) / len(cs), sum(f)))

def corr(a, b):
    n = len(a); ma = sum(a) / n; mb = sum(b) / n
    sa = math.sqrt(sum((x - ma) ** 2 for x in a)); sb = math.sqrt(sum((x - mb) ** 2 for x in b))
    return sum((x - ma) * (y - mb) for x, y in zip(a, b)) / (sa * sb)

eras = [("all", lambda y: True), ("1880-1913", lambda y: y <= 1913), ("1919-1938", lambda y: 1919 <= y <= 1938),
        ("1950-1985", lambda y: 1950 <= y <= 1985), ("1986-2016", lambda y: y >= 1986)]
print("n years %d. Correlation with crisis starts in y+1..y+2 (share of countries):" % len(rows))
print("%-28s" % "world gauge" + "".join("%11s" % e for e, _ in eras))
for name, i in (("stretched share", 1), ("reversing share", 2), ("turn = stretched x reversing", 3)):
    print("%-28s" % name + "".join("%+11.2f" % corr([r[i] for r in rows if p(r[0])], [r[4] for r in rows if p(r[0])]) for _, p in eras))
