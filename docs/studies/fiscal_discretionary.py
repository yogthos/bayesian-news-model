"""bnm-7yg.6: discretionary fiscal consolidation (registered in
docs/CALIBRATION.md, "Discretionary fiscal consolidation: is the label
cyclical, and is a cyclically adjusted one forecastable?"). Part A: the
current label's onsets by year against D1 (cyclically adjusted balance up
1.5 points) and D2 (D1 with real expenditure growth <= 0), and how many of
the current label's onsets fall in recovery years. Part B: a causal pooled
logit's AUC and log skill against climatology for each label, 2003-2022.

  python3 -I docs/studies/fiscal_discretionary.py <repo>
"""
import math
import os
import sys
from collections import defaultdict

import numpy as np

LAMBDA = 100.0
ELASTICITY = 0.5
FROM, TO = 1975, 2025
TEST = (2003, 2022)


def hp(y, lam):
    n = len(y)
    if n < 3:
        return np.array(y, float)
    D = np.zeros((n - 2, n))
    for i in range(n - 2):
        D[i, i:i + 3] = [1.0, -2.0, 1.0]
    return np.linalg.solve(np.eye(n) + lam * D.T @ D, np.asarray(y, float))


def output_gap(growth):
    """{year gap %} from {year real growth %}: HP on the log level of each
    run of consecutive years (10 or more)."""
    out = {}
    ys = sorted(growth)
    runs, cur = [], []
    for y in ys:
        if cur and y != cur[-1] + 1:
            runs.append(cur)
            cur = []
        cur.append(y)
    if cur:
        runs.append(cur)
    for r in runs:
        if len(r) < 10:
            continue
        lv = np.cumsum([math.log(1 + growth[y] / 100.0) for y in r])
        tr = hp(lv, LAMBDA)
        for y, a, b in zip(r, lv, tr):
            out[y] = 100.0 * (a - b)
    return out


def once_in(n, years):
    out = []
    for y in sorted(years):
        if not out or y - out[-1] >= n:
            out.append(y)
    return out


def current(bal, exp):
    return once_in(3, [y for y in bal if y - 1 in bal and y in exp and y - 1 in exp
                       and bal[y] - bal[y - 1] >= 1.5 and exp[y] - exp[y - 1] <= -1.0])


def cab(bal, gap):
    return {y: bal[y] - ELASTICITY * gap[y] for y in bal if y in gap}


def d1(bal, gap):
    c = cab(bal, gap)
    return once_in(3, [y for y in c if y - 1 in c and c[y] - c[y - 1] >= 1.5])


def d2(bal, gap, rexp):
    c = cab(bal, gap)
    return once_in(3, [y for y in c if y - 1 in c and c[y] - c[y - 1] >= 1.5
                       and y in rexp and y - 1 in rexp and rexp[y] <= rexp[y - 1]])


def auc(s, y):
    pos, neg = s[y], s[~y]
    if len(pos) == 0 or len(neg) == 0:
        return float("nan")
    d = pos[:, None] - neg[None, :]
    return float(((d > 0) + 0.5 * (d == 0)).mean())


def logit(X, y, lam=1.0, it=50):
    mu, sd = X.mean(0), X.std(0)
    sd[sd < 1e-9] = 1.0
    Z = np.column_stack([np.ones(len(X)), (X - mu) / sd])
    b = np.zeros(Z.shape[1])
    pen = np.r_[1e-4, np.full(X.shape[1], lam)]
    for _ in range(it):
        p = 1 / (1 + np.exp(-(Z @ b)))
        b = b + np.linalg.solve((Z * (p * (1 - p))[:, None]).T @ Z + np.diag(pen), Z.T @ (y - p) - pen * b)
    return lambda Xn: 1 / (1 + np.exp(-(np.column_stack([np.ones(len(Xn)), (Xn - mu) / sd]) @ b)))


def read(repo):
    f = defaultdict(lambda: defaultdict(dict))
    with open(os.path.join(repo, "data/derived/fiscal.csv")) as fh:
        fh.readline()
        for line in fh:
            iso, yr, var, v = line.rstrip("\n").split(",")
            f[var][iso][int(yr)] = float(v)
    s = defaultdict(lambda: defaultdict(dict))
    want = {"economy/gdp_growth", "debt/government_gdp", "regime/election_legislative",
            "regime/election_presidential", "economy/inflation"}
    with open(os.path.join(repo, "data/slow/series.tsv")) as fh:
        for line in fh:
            p = line.rstrip("\n").split("\t")
            if len(p) >= 6 and p[1] in want and p[2] == "a":
                s[p[1]][p[0]][int(p[3][:4])] = float(p[5])
    return f, s


def labels(f, s):
    out = {"current": {}, "D1": {}, "D2": {}}
    gaps = {}
    for u, bal in f["gov_bal_gdp"].items():
        bal = {y: v for y, v in bal.items() if FROM <= y <= TO}
        exp = f["gov_exp_gdp"].get(u, {})
        rexp = f["gov_exp_real"].get(u, {})
        gap = output_gap(s["economy/gdp_growth"].get(u, {}))
        gaps[u] = gap
        out["current"][u] = current(bal, exp)
        out["D1"][u] = d1(bal, gap)
        out["D2"][u] = d2(bal, gap, rexp)
    return out, gaps


def design(f, s, lab, gaps, with_balance=True):
    rows = []
    for u, bal in f["gov_bal_gdp"].items():
        on = set(lab[u])
        busy = {y + k for y in lab[u] for k in (1, 2)}
        gr = s["economy/gdp_growth"].get(u, {})
        debt = s["debt/government_gdp"].get(u, {})
        el = s["regime/election_legislative"].get(u, {})
        ep = s["regime/election_presidential"].get(u, {})
        inf = s["economy/inflation"].get(u, {})
        for y in range(1980, TEST[1] + 1):
            k = y - 1
            if y in busy or k not in bal or k not in gr or k not in debt or k - 2 not in debt:
                continue
            if y not in bal:
                continue
            x = ([bal[k]] if with_balance else []) + [debt[k], debt[k] - debt[k - 2], gr[k],
                                                      max(el.get(k, 0.0), ep.get(k, 0.0)),
                                                      math.copysign(math.log1p(abs(inf.get(k, 0.0))), inf.get(k, 0.0))]
            rows.append((u, y, x, y in on))
    return rows


def forecast(rows):
    yr = np.array([r[1] for r in rows])
    X = np.array([r[2] for r in rows], float)
    y = np.array([r[3] for r in rows])
    p = np.full(len(rows), np.nan)
    clim = np.full(len(rows), np.nan)
    for Y in range(TEST[0], TEST[1] + 1):
        tr, te = yr <= Y - 2, yr == Y
        if y[tr].sum() < 10 or not te.any():
            continue
        p[te] = logit(X[tr], y[tr].astype(float))(X[te])
        clim[te] = y[tr].mean()
    ok = ~np.isnan(p)
    pp, cc, yy = np.clip(p[ok], 1e-6, 1 - 1e-6), np.clip(clim[ok], 1e-6, 1 - 1e-6), y[ok]
    ll = lambda q: np.mean(yy * np.log(q) + (1 - yy) * np.log(1 - q))
    return {"n": int(ok.sum()), "onsets": int(yy.sum()), "auc": auc(pp, yy), "skill": ll(pp) - ll(cc)}


def main(argv):
    f, s = read(argv[0])
    lab, gaps = labels(f, s)
    print(f"units with a balance {len(f['gov_bal_gdp'])}; with an output gap {sum(1 for g in gaps.values() if g)}")
    print("== Part A: onsets by year (current / D1 / D2), 1990-2025")
    for y in range(1990, 2026):
        c = [sum(y in v for v in lab[k].values()) for k in ("current", "D1", "D2")]
        print(f"  {y}: {c[0]:3} {c[1]:3} {c[2]:3}")
    rec = lambda u, y: (y in gaps.get(u, {}) and y - 1 in gaps[u] and gaps[u][y] - gaps[u][y - 1] >= 1.0)
    judged = [(u, y) for u, ys in lab["current"].items() for y in ys if 1990 <= y <= 2025 and y in gaps.get(u, {}) and y - 1 in gaps[u]]
    r = [(u, y) for u, y in judged if rec(u, y)]
    nr = [(u, y) for u, y in judged if not rec(u, y)]
    d2s = {(u, y) for u, ys in lab["D2"].items() for y in ys}
    d2r = [(u, y) for u, y in d2s if 1990 <= y <= 2025 and rec(u, y)]
    keep_nr = sum((u, y) in d2s for u, y in nr)
    print(f"current onsets 1990-2025 with a gap: {len(judged)}; in recovery years (gap up >= 1): {len(r)} ({len(r)/len(judged):.2f})")
    print(f"D2 onsets in recovery years: {len(d2r)} (cut {1 - len(d2r)/len(r):.2f}); "
          f"D2 keeps {keep_nr}/{len(nr)} ({keep_nr/len(nr):.2f}) of current non-recovery onsets, "
          f"{sum((u, y) in d2s for u, y in r)}/{len(r)} of recovery ones")
    print("== Part B: causal pooled logit, 2003-2022")
    for k in ("current", "D1", "D2"):
        for wb in (True, False):
            res = forecast(design(f, s, lab[k], gaps, wb))
            print(f"  {k:7} {'with' if wb else 'without'} balance: rows {res['n']} onsets {res['onsets']} "
                  f"AUC {res['auc']:.3f} log skill vs clim {res['skill']:+.4f}")


if __name__ == "__main__":
    main(sys.argv[1:])
