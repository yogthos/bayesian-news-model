"""bnm-5lr: the level outside the Macrohistory panel. t100 issues about
0.043 to every country without JST data, every year, while their rate
was 0.029 in 2013-19 and 0 in 2020-22. Per December origin year, these
countries' onset rate on the lattice's banking year rows against world
means (over the same countries, each value as known by the origin, from
data/slow/series.tsv's known-at dates) of stocks the EM-crisis
literature reads: reserves in months of imports, external debt over GNI,
the current account, private credit over GDP against its own ten years,
and the share pegged (Gourinchas & Obstfeld 2012; Obstfeld, Shambaugh &
Taylor 2010). Rolling logit refit on origins known by then (three years
back), Brier by fold on these rows, against a constant and the lattice.
python3 -I docs/studies/calm_em.py <repo> <run>"""
import csv, math, sys, datetime
repo, run = sys.argv[1], sys.argv[2]
jst = {r['iso'] for r in csv.DictReader(open(f"{repo}/data/raw/slow/jst/jst.csv"))}
want = {'debt/reserves_months', 'debt/external_gni', 'debt/current_account_gdp', 'finance/private_credit_gdp', 'finance/fx_peg'}
ser = {}
for l in open(f"{repo}/data/slow/series.tsv"):
    u, v, f, end, known, x = l.rstrip('\n').split('\t')[:6]
    if v in want and u not in jst and f == 'a':
        try: ser.setdefault(v, {}).setdefault(u, []).append((int(end[:4]), known, float(x)))
        except ValueError: pass
def latest(v, u, yr):
    """the newest value known by Dec 31 of yr, if its period is at most 3 years old"""
    cut = f"{yr}-12-31"; best = None
    for y, k, x in ser.get(v, {}).get(u, []):
        if k <= cut and y >= yr - 3 and (best is None or y > best[0]): best = (y, x)
    return best
def credit_stretch(u, yr):
    b = latest('finance/private_credit_gdp', u, yr)
    if not b: return None
    y0, x0 = b
    past = [x for y, k, x in ser.get('finance/private_credit_gdp', {}).get(u, []) if y0 - 10 <= y < y0 and k <= f"{yr}-12-31"]
    if len(past) < 6: return None
    m = sum(past) / len(past); s = (sum((p - m) ** 2 for p in past) / len(past)) ** 0.5
    return (x0 - m) / max(s, 0.02 * abs(m), 1e-9)
rows = []
for l in open(f"{repo}/data/runs/{run}/banking_crisis/forecasts.tsv"):
    s, u, o, p, y = l.rstrip("\n").split("\t")[:5]
    if s == "year" and len(u) == 3 and u not in jst and y in ("true", "false") and int(o) % 12 == 11:
        rows.append((int(o) // 12, u, float(p), y == "true"))
years = sorted({r[0] for r in rows}); units = sorted({r[1] for r in rows})
def wmean(f, yr):
    vs = [x for x in (f(u, yr) for u in units) if x is not None]
    return (sum(vs) / len(vs), len(vs)) if vs else (None, 0)
feat = {}
for yr in years:
    feat[yr] = {'reserves': wmean(lambda u, y: (latest('debt/reserves_months', u, y) or (0, None))[1], yr)[0],
                'extdebt': wmean(lambda u, y: (latest('debt/external_gni', u, y) or (0, None))[1], yr)[0],
                'ca': wmean(lambda u, y: (latest('debt/current_account_gdp', u, y) or (0, None))[1], yr)[0],
                'credit': wmean(credit_stretch, yr)[0],
                'peg': wmean(lambda u, y: (latest('finance/fx_peg', u, y) or (0, None))[1], yr)[0]}
def fit(X, Y, prior=1.0):
    k = len(X[0]); b = [0.0] * k
    for _ in range(60):
        g = [-prior * b[j] for j in range(k)]; H = [[(prior if i == j else 0.0) for j in range(k)] for i in range(k)]
        for x, y in zip(X, Y):
            p = 1 / (1 + math.exp(-sum(bj * xj for bj, xj in zip(b, x))))
            for i in range(k):
                g[i] += (y - p) * x[i]
                for j in range(k): H[i][j] += p * (1 - p) * x[i] * x[j]
        A = [H[i][:] + [g[i]] for i in range(k)]
        for c in range(k):
            piv = max(range(c, k), key=lambda r: abs(A[r][c])); A[c], A[piv] = A[piv], A[c]
            for r in range(k):
                if r != c:
                    f = A[r][c] / A[c][c]; A[r] = [a - f * bb for a, bb in zip(A[r], A[c])]
        d = [A[i][k] / A[i][i] for i in range(k)]
        b = [bj + dj for bj, dj in zip(b, d)]
        if max(abs(x) for x in d) < 1e-8: break
    return b
scale = {'reserves': (4.5, 1.5), 'extdebt': (60, 20), 'ca': (-4, 3), 'credit': (0.5, 1), 'peg': (0.6, 0.15)}
def xs(yr, cols):
    return [1.0] + [0.0 if feat[yr][c] is None else (feat[yr][c] - scale[c][0]) / scale[c][1] for c in cols]
cache = {}
def pred(yr, cols):
    key = (yr, tuple(cols))
    if key not in cache:
        tr = [r for r in rows if r[0] <= yr - 3]
        b = fit([xs(r[0], cols) for r in tr], [1.0 if r[3] else 0.0 for r in tr])
        cache[key] = 1 / (1 + math.exp(-sum(bj * xj for bj, xj in zip(b, xs(yr, cols)))))
    return cache[key]
print("year  rate   reserves extdebt  ca     credit  peg    lattice-p")
for yr in years:
    if yr >= 1985:
        rs = [r for r in rows if r[0] == yr]; f = feat[yr]
        fm = lambda v: "   -  " if v is None else f"{v:6.2f}"
        print(yr, f"{sum(r[3] for r in rs)/len(rs):.3f}", fm(f['reserves']), fm(f['extdebt']), fm(f['ca']), fm(f['credit']), fm(f['peg']), f"{sum(r[2] for r in rs)/len(rs):.3f}")
models = {'constant': [], 'reserves': ['reserves'], 'extdebt': ['extdebt'], 'ca': ['ca'], 'credit': ['credit'], 'peg': ['peg'], 'res+extdebt': ['reserves', 'extdebt']}
print("\nfold       " + "".join(f"{m:>13}" for m in models) + "      lattice")
for a, b in [(2003, 2006), (2007, 2009), (2010, 2012), (2013, 2019), (2020, 2022), (2003, 2022)]:
    rs = [r for r in rows if a <= r[0] <= b]
    out = [sum((pred(r[0], c) - r[3]) ** 2 for r in rs) / len(rs) for c in models.values()]
    print(f"{a}-{b}  " + "".join(f"{v:13.5f}" for v in out) + f"  {sum((r[2] - r[3]) ** 2 for r in rs) / len(rs):11.5f}")
print("\nlevel issued 2013-19 / 2020-22:", {m: (round(sum(pred(y, c) for y in range(2013, 2020)) / 7, 3), round(sum(pred(y, c) for y in (2020, 2021, 2022)) / 3, 3)) for m, c in models.items()})
