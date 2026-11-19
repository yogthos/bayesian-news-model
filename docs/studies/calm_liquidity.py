"""bnm-5lr: banks' liability stocks as banking's world level. Per December
origin year, the world onset rate on the lattice's banking year rows,
forecast by a logit refit on origins known by then (three years back) on
the JST countries' mean loan-to-deposit ratio and non-core funding share
from the row published by then (yr-1), alone and with the world credit
stretch read the same way (bnm.macrohistory's, via calm_level.clj's
output is not reused: stretch is not needed to test liquidity). Brier on
the country rows by fold, against a constant and the lattice.
python3 -I docs/studies/calm_liquidity.py <repo> <run>"""
import csv, math, sys
repo, run = sys.argv[1], sys.argv[2]
jst = list(csv.DictReader(open(f"{repo}/data/raw/slow/jst/jst.csv")))
def world(k, yr, base=0):
    vals = []
    for r in jst:
        if int(r['year']) == yr - 1 and r[k] not in ('', 'NA'):
            v = float(r[k])
            if base:  # change against the country's own mean of the 10 years before
                past = [float(q[k]) for q in jst if q['iso'] == r['iso'] and yr - 1 - base <= int(q['year']) < yr - 1 and q[k] not in ('', 'NA')]
                if len(past) < 5: continue
                v -= sum(past) / len(past)
            vals.append(v)
    return sum(vals) / len(vals) if vals else None
rows = []
for l in open(f"{repo}/data/runs/{run}/banking_crisis/forecasts.tsv"):
    s, u, o, p, y = l.rstrip("\n").split("\t")[:5]
    if s == "year" and len(u) == 3 and y in ("true", "false") and int(o) % 12 == 11:
        rows.append((int(o) // 12, float(p), y == "true"))
years = sorted({r[0] for r in rows})
feat = {}
for yr in years:
    feat[yr] = {'ltd': world('ltd', yr), 'noncore': world('noncore', yr),
                'ltd10': world('ltd', yr, 10), 'noncore10': world('noncore', yr, 10)}
def fit(X, Y, prior=1.0):
    k = len(X[0]); b = [0.0] * k
    for _ in range(50):
        g = [-prior * b[j] for j in range(k)]; H = [[(prior if i == j else 0.0) for j in range(k)] for i in range(k)]
        for x, y in zip(X, Y):
            p = 1 / (1 + math.exp(-sum(bj * xj for bj, xj in zip(b, x))))
            for i in range(k):
                g[i] += (y - p) * x[i]
                for j in range(k): H[i][j] += p * (1 - p) * x[i] * x[j]
        # solve H d = g
        n = k; A = [H[i][:] + [g[i]] for i in range(n)]
        for c in range(n):
            piv = max(range(c, n), key=lambda r: abs(A[r][c])); A[c], A[piv] = A[piv], A[c]
            for r in range(n):
                if r != c:
                    f = A[r][c] / A[c][c]; A[r] = [a - f * bb for a, bb in zip(A[r], A[c])]
        d = [A[i][n] / A[i][i] for i in range(n)]
        b = [bj + dj for bj, dj in zip(b, d)]
        if max(abs(x) for x in d) < 1e-8: break
    return b
scales = {'ltd': (110, 10), 'noncore': (54, 4), 'ltd10': (0, 8), 'noncore10': (0, 3)}
def xs(yr, cols):
    out = [1.0]
    for c in cols:
        v = feat[yr][c]; m, s = scales[c]
        out.append(0.0 if v is None else (v - m) / s)
    return out
cache = {}
def pred(yr, cols):
    key = (yr, tuple(cols))
    if key not in cache:
        tr = [r for r in rows if r[0] <= yr - 3]
        b = fit([xs(r[0], cols) for r in tr], [1.0 if r[2] else 0.0 for r in tr])
        cache[key] = 1 / (1 + math.exp(-sum(bj * xj for bj, xj in zip(b, xs(yr, cols)))))
    return cache[key]
models = {'constant': [], 'ltd': ['ltd'], 'noncore': ['noncore'], 'ltd+noncore': ['ltd', 'noncore'],
          'ltd vs 10y': ['ltd10'], 'noncore vs 10y': ['noncore10']}
print("year  rate   ltd     noncore  ltd10   noncore10")
for yr in years:
    if yr >= 2000:
        rs = [r for r in rows if r[0] == yr]; f = feat[yr]
        fm = lambda v: "   -  " if v is None else f"{v:7.2f}"
        print(yr, f"{sum(r[2] for r in rs)/len(rs):.3f}", fm(f['ltd']), fm(f['noncore']), fm(f['ltd10']), fm(f['noncore10']))
print("\nfold       " + "  ".join(f"{m:>14}" for m in models) + "         lattice")
for a, b in [(2003, 2006), (2007, 2009), (2010, 2012), (2013, 2019), (2020, 2022), (2003, 2022)]:
    rs = [r for r in rows if a <= r[0] <= b]
    out = [sum((pred(r[0], c) - r[2]) ** 2 for r in rs) / len(rs) for c in models.values()]
    lat = sum((r[1] - r[2]) ** 2 for r in rs) / len(rs)
    print(f"{a}-{b}  " + "  ".join(f"{v:14.5f}" for v in out) + f"  {lat:14.5f}")
print("\nlevel issued (mean p) 2006 / 2020-22:", {m: (round(pred(2006, c), 3), round(sum(pred(y, c) for y in (2020, 2021, 2022) if y in feat) / 3, 3)) for m, c in models.items()})
