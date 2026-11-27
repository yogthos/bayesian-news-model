"""bnm-7yg.6: growth-at-risk, out of sample (registered in docs/CALIBRATION.md,
"Growth-at-risk: do financial conditions move the lower tail out of
sample?"). Pooled linear quantile regressions of per-head growth h quarters
ahead, refit at each origin quarter on pairs whose outcome is known: B on
last known growth and own volatility; F adds the NFCI, the BIS credit gap
and its missing flag. Pinball loss paired B - F at origins 2003Q1-2022Q4,
90% intervals resampling years; coverage.

  python3 -I docs/studies/growth_at_risk.py <repo>
"""
import os
import sys
from collections import defaultdict

import numpy as np

QS = [0.05, 0.10]
HS = [4, 8]
TRAIN_FROM = 1980 * 4
TEST = (2003 * 4, 2022 * 4 + 3)
VOL_N = 12
GAP_LAG = 2


def qindex(s):
    return int(s[:4]) * 4 + int(s[5]) - 1


def mindex(date):
    return int(date[:4]) * 12 + int(date[5:7]) - 1


def at_quarter_end(monthly, q):
    """The monthly series' value in quarter q's last month, or None."""
    return monthly.get((q // 4) * 12 + (q % 4) * 3 + 2)


def lagged(quarterly, q, lag):
    """The latest value of a quarterly series for quarters <= q - lag."""
    best = None
    for k in range(q - lag, q - lag - 8, -1):
        if k in quarterly:
            best = quarterly[k]
            break
    return best


def features(growth, nfci, gap, t):
    """B's [last known growth (t-1), sd of the last VOL_N known quarters] and
    F's extra [nfci at t, credit gap two quarters back or 0, gap missing];
    None when growth at t-1 or a full volatility window is missing."""
    hist = [growth.get(k) for k in range(t - VOL_N, t)]
    if any(v is None for v in hist):
        return None
    n = at_quarter_end(nfci, t)
    c = lagged(gap, t, GAP_LAG)
    return [hist[-1], float(np.std(hist)), n if n is not None else 0.0,
            c if c is not None else 0.0, 1.0 if c is None else 0.0]


def qreg(X, y, q, it=200, eps=1e-6):
    """Linear quantile regression by iteratively reweighted least squares;
    returns [intercept, slopes...]."""
    Z = np.column_stack([np.ones(len(y)), X])
    b = np.linalg.lstsq(Z, y, rcond=None)[0]
    for _ in range(it):
        r = y - Z @ b
        w = np.where(r >= 0, q, 1 - q) / np.maximum(np.abs(r), eps)
        Zw = Z * w[:, None]
        nb = np.linalg.solve(Z.T @ Zw + 1e-9 * np.eye(Z.shape[1]), Zw.T @ y)
        if np.max(np.abs(nb - b)) < 1e-8:
            b = nb
            break
        b = nb
    return b


def pinball(y, f, q):
    r = y - f
    return float(np.mean(np.maximum(q * r, (q - 1) * r)))


def block_ci(d, years, draws=2000, seed=1):
    rng = np.random.default_rng(seed)
    ys = np.unique(years)
    by = {v: d[years == v] for v in ys}
    ms = []
    for _ in range(draws):
        pick = rng.choice(ys, len(ys))
        ms.append(np.concatenate([by[v] for v in pick]).mean())
    return float(d.mean()), float(np.percentile(ms, 5)), float(np.percentile(ms, 95))


def read(repo):
    growth = defaultdict(dict)
    with open(os.path.join(repo, "data/derived/gdp-quarterly.csv")) as fh:
        head = fh.readline().rstrip("\n").split(",")
        for line in fh:
            r = dict(zip(head, line.rstrip("\n").split(",")))
            if r["yoy_pc"] != "":
                growth[r["iso"]][qindex(r["quarter"])] = float(r["yoy_pc"])
    nfci, gap = {}, defaultdict(dict)
    with open(os.path.join(repo, "data/slow/series.tsv")) as fh:
        for line in fh:
            p = line.rstrip("\n").split("\t")
            if len(p) < 6:
                continue
            if p[1] == "finance/conditions":
                nfci[mindex(p[3])] = float(p[5])
            elif p[1] == "finance/credit_gap":
                m = mindex(p[3])  # the quarter covered; GAP_LAG covers BIS's ~4-month release
                gap[p[0]][(m // 12) * 4 + (m % 12) // 3] = float(p[5])
    return growth, nfci, gap


def rows(growth, nfci, gap, h):
    """[(unit, origin t, features, outcome growth at t-1+h)]: the outcome
    is the quarter h after the last known one."""
    out = []
    for u, gr in growth.items():
        for t in range(TRAIN_FROM, TEST[1] + 1):
            f = features(gr, nfci, gap.get(u, {}), t)
            y = gr.get(t - 1 + h)
            if f is not None and y is not None:
                out.append((u, t, f, y))
    return out


def run(growth, nfci, gap, h, q):
    rs = rows(growth, nfci, gap, h)
    T = np.array([r[1] for r in rs])
    X = np.array([r[2] for r in rs])
    Y = np.array([r[3] for r in rs])
    out_q = T - 1 + h
    yy, fb, ff, yrs, bf = [], [], [], [], None
    for t in range(TEST[0], TEST[1] + 1):
        tr, te = out_q <= t - 1, T == t
        if not te.any() or tr.sum() < 200:
            continue
        bb = qreg(X[tr][:, :2], Y[tr], q)
        bf = qreg(X[tr], Y[tr], q)
        pb = np.column_stack([np.ones(te.sum()), X[te][:, :2]]) @ bb
        pf = np.column_stack([np.ones(te.sum()), X[te]]) @ bf
        fb.append(pb); ff.append(pf); yy.append(Y[te]); yrs.append(np.full(te.sum(), t // 4))
    fb, ff, yy, yrs = map(np.concatenate, (fb, ff, yy, yrs))
    d = np.maximum(q * (yy - fb), (q - 1) * (yy - fb)) - np.maximum(q * (yy - ff), (q - 1) * (yy - ff))
    return {"n": len(yy), "B": pinball(yy, fb, q), "F": pinball(yy, ff, q), "ci": block_ci(d, yrs),
            "covB": float(np.mean(yy < fb)), "covF": float(np.mean(yy < ff)), "coefF": bf}


def main(argv):
    growth, nfci, gap = read(argv[0])
    print(f"units {len(growth)}; NFCI months {len(nfci)}; credit-gap units {len(gap)}")
    print("h  q     rows   pinball B   pinball F   B-F [90%]                        cover B  cover F  F coefs (int, growth, vol, nfci, gap, gap-missing)")
    for h in HS:
        for q in QS:
            r = run(growth, nfci, gap, h, q)
            m, lo, hi = r["ci"]
            print(f"{h}  {q:.2f}  {r['n']:6}  {r['B']:.6f}    {r['F']:.6f}    {m:+.6f} [{lo:+.6f}, {hi:+.6f}]   "
                  f"{r['covB']:.3f}    {r['covF']:.3f}    " + " ".join(f"{c:+.4f}" for c in r["coefF"]))


if __name__ == "__main__":
    main(sys.argv[1:])
