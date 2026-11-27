"""bnm-7yg.6: how sovereign defaults' since-last input ranks 2003-2022's
defaults under three codings, on sovereign_history.clj's rows:
  current  lb/history: log(1 + years since the last known onset), or since
           the catalogue's from when there is none (66 countries from 1800,
           106 from 1970);
  horizon  years since the later of the last known onset and January 1970,
           the common horizon every country's catalogue covers;
  unknown  none (blank, the standardizer's 0) when no onset is known.
AUC with shorter ranked riskier, pooled and by band (none / 0-8 / 9+ years
since the last known onset), and a causal pooled logit on since-last,
own10 and own30 (refit each year on rows two or more years back).

  python3 -I docs/studies/sovereign_history.py <rows.tsv>
"""
import math
import sys

import numpy as np

HORIZON = 12 * 1970
CODINGS = ["current", "horizon", "unknown"]
BANDS = ["all", "none", "0-8", "9+"]


def read(path):
    lines = open(path).read().splitlines()
    head = lines[0].split("\t")
    out = []
    for l in lines[1:]:
        if not l:
            continue
        r = dict(zip(head, l.split("\t")))
        out.append({"place": r["place"], "year": int(r["year"]), "y": r["y"] == "1", "from": int(r["from"]),
                    "last": int(r["last"]) if r["last"] else None,
                    "own10": float(r["own10"]), "own30": float(r["own30"]), "since_last": float(r["since_last"])})
    return out


def since(r, coding):
    t = 12 * r["year"] + 11
    last = r["last"]
    if coding == "current":
        return math.log(1 + (t - (last if last is not None else r["from"])) / 12.0)
    if coding == "horizon":
        return math.log(1 + (t - max(last if last is not None else HORIZON, HORIZON)) / 12.0)
    if coding == "unknown":
        return float("nan") if last is None else math.log(1 + (t - last) / 12.0)
    raise ValueError(coding)


def band(r):
    if r["last"] is None or r["last"] < HORIZON:
        return "none"
    return "0-8" if (12 * r["year"] + 11 - r["last"]) // 12 <= 8 else "9+"


def auc_short(pairs):
    ok = [(-x, y) for x, y in pairs if not math.isnan(x)]
    pos = np.array([s for s, y in ok if y])
    neg = np.array([s for s, y in ok if not y])
    if len(pos) == 0 or len(neg) == 0:
        return float("nan")
    d = pos[:, None] - neg[None, :]
    return float(((d > 0) + 0.5 * (d == 0)).mean())


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
        g = Z.T @ (y - p) - pen * b
        H = (Z * (p * (1 - p))[:, None]).T @ Z + np.diag(pen)
        b = b + np.linalg.solve(H, g)
    return lambda Xn: np.column_stack([np.ones(len(Xn)), (Xn - mu) / sd]) @ b


def causal(rows, coding):
    X = np.array([[since(r, coding), r["own10"], r["own30"]] for r in rows])
    X[np.isnan(X)] = np.nan
    col = X[:, 0]
    y = np.array([r["y"] for r in rows])
    yr = np.array([r["year"] for r in rows])
    s = np.full(len(rows), np.nan)
    for Y in range(2005, 2023):
        tr, te = yr <= Y - 2, yr == Y
        Xt = X.copy()
        m = np.nanmean(col[tr])
        Xt[np.isnan(Xt[:, 0]), 0] = m
        if y[tr].sum() >= 5:
            s[te] = logit(Xt[tr], y[tr].astype(float))(Xt[te])
    return s


def main(argv):
    rows = read(argv[0])
    y = np.array([r["y"] for r in rows])
    bs = np.array([band(r) for r in rows])
    drift = max(abs(since(r, "current") - r["since_last"]) for r in rows)
    print(f"rows {len(rows)} onsets {y.sum()}; current coding vs lb/history max |diff| {drift:.2e}")
    for b in BANDS:
        m = np.ones(len(rows), bool) if b == "all" else bs == b
        print(f"  {b:5} rows {m.sum():5} onsets {y[m].sum():3}")
    print("since-last alone, AUC (shorter riskier):")
    print("coding    " + " ".join(f"{b:>7}" for b in BANDS))
    for c in CODINGS:
        a = []
        for b in BANDS:
            idx = [i for i, r in enumerate(rows) if b == "all" or bs[i] == b]
            a.append(auc_short([(since(rows[i], c), rows[i]["y"]) for i in idx]))
        print(f"{c:9} " + " ".join(f"{v:7.3f}" for v in a))
    print("causal pooled logit on since-last, own10, own30, 2005-2022, AUC:")
    print("coding    " + " ".join(f"{b:>7}" for b in BANDS))
    for c in CODINGS:
        s = causal(rows, c)
        ok = ~np.isnan(s)
        a = [auc(s[ok & ((bs == b) | (b == "all"))], y[ok & ((bs == b) | (b == "all"))]) for b in BANDS]
        print(f"{c:9} " + " ".join(f"{v:7.3f}" for v in a))
    nf = {}
    for r in rows:
        if r["last"] is None:
            k = r["from"] // 12
            nf.setdefault(k, [0, 0])
            nf[k][0] += 1
            nf[k][1] += r["y"]
    print("no known onset, by catalogue start: " + "  ".join(f"{k}: {v[1]}/{v[0]} rate {v[1]/v[0]:.4f}" for k, v in sorted(nf.items())))


if __name__ == "__main__":
    main(sys.argv[1:])
