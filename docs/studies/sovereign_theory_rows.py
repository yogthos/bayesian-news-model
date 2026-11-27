"""bnm-7yg.6: why sovereign defaults' theory offset ranks at chance under a
ten-year half-life (t161's AUC cost). On sovereign_theory_fits.clj's dump of
the design rows, refit at each December origin 2003-2022 as augur.logit/fit
does (weighted, inputs standardized by the weighted rows, prior on that
scale, the intercept nearly free) and score the origin's rows: the offset's
AUC under each variant, and each term's AUC.

  python3 -I docs/studies/sovereign_theory_rows.py <rows.tsv>
"""
import sys

import numpy as np


def weights(ts, t, half_life):
    if half_life is None:
        return np.ones(len(ts))
    return 0.5 ** ((t - ts) / (12.0 * half_life))


def fit(X, y, w, prior, penalties=None, raw=False, iterations=25):
    """augur.logit/fit: Newton on the weighted log likelihood with a ridge
    prior on the weighted-standardized columns; x[0] the intercept."""
    n, k = X.shape
    ws = w.sum()
    mean = (w[:, None] * X).sum(0) / ws
    sd = np.sqrt((w[:, None] * (X - mean) ** 2).sum(0) / ws)
    sd = np.where(sd > 1e-9, sd, 1.0)
    mean[0], sd[0] = 0.0, 1.0
    Z = (X - mean) / sd
    Z[:, 0] = 1.0
    pen = prior * (np.asarray(penalties, float) if penalties is not None else np.ones(k))
    if raw:
        pen[1:] = pen[1:] / sd[1:] ** 2
    pen[0] = 1e-4
    yf = y.astype(float)

    def objective(b):
        e = Z @ b
        return (w * (yf * e - np.logaddexp(0, e))).sum() - 0.5 * (pen * b * b).sum()

    b = np.zeros(k)
    obj = objective(b)
    for _ in range(iterations):
        p = 1 / (1 + np.exp(-(Z @ b)))
        g = Z.T @ (w * (yf - p)) - pen * b
        H = (Z * (w * np.maximum(p * (1 - p), 1e-9))[:, None]).T @ Z + np.diag(pen)
        step = np.linalg.solve(H, g)
        t, tries = 1.0, 0
        while True:
            cand = b + t * step
            o = objective(cand)
            if o > obj or tries >= 30:
                break
            t, tries = t * 0.5, tries + 1
        moved = np.abs(cand - b).max()
        if o <= obj:
            break
        b, obj = cand, o
        if moved <= 1e-6:
            break
    beta = b / sd
    beta[0] = b[0] - (beta[1:] * mean[1:]).sum()
    return beta


def auc(s, y):
    pos, neg = s[y], s[~y]
    if len(pos) == 0 or len(neg) == 0:
        return float("nan")
    d = pos[:, None] - neg[None, :]
    return float(((d > 0) + 0.5 * (d == 0)).mean())


def read(path):
    lines = open(path).read().splitlines()
    meta = lines[0].split("\t")
    i = meta.index("raw")
    pens = [float(v) for v in meta[1:i] if v != ""] or None
    raw = meta[i + 1] == "true"
    head = lines[1].split("\t")
    rows = [l.split("\t") for l in lines[2:] if l]
    place = np.array([r[0] for r in rows])
    t = np.array([int(r[1]) for r in rows])
    known = np.array([int(float(r[2])) for r in rows])
    y = np.array([r[3] == "1" for r in rows])
    X = np.array([[float(v) for v in r[4:]] for r in rows])
    return {"names": head[4:], "place": place, "t": t, "known": known, "y": y, "X": X, "pens": pens, "raw": raw}


def offsets(d, half_life, cols=None, prior=1.0, rel_prior=False):
    """Point-in-time offsets at the December rows 2003-2022: {row index: (log odds, terms)}."""
    X = d["X"] if cols is None else d["X"][:, cols]
    pens = d["pens"] if (cols is None or d["pens"] is None) else [d["pens"][c] for c in cols]
    out_s, out_terms, idx = [], [], []
    for t in sorted(set(d["t"][(d["t"] // 12 >= 2003) & (d["t"] // 12 <= 2022)])):
        tr = d["known"] <= t
        if not (d["y"][tr].any() and (~d["y"][tr]).any()):
            continue
        w = weights(d["t"][tr], t, half_life)
        pr = prior * (w.sum() / tr.sum() if rel_prior else 1.0)
        b = fit(X[tr], d["y"][tr], w, pr, pens, d["raw"])
        te = np.where(d["t"] == t)[0]
        out_s.append(X[te] @ b)
        out_terms.append(X[te] * b)
        idx.append(te)
    return np.concatenate(idx), np.concatenate(out_s), np.vstack(out_terms)


def main(argv):
    d = read(argv[0])
    names, k = d["names"], d["X"].shape[1]
    print("rows", len(d["y"]), "columns", " ".join(names), "penalties", d["pens"], "raw", d["raw"])
    variants = [("no half-life", None, None, False), ("half-life 10", 10.0, None, False),
                ("half-life 10, prior relative to weight", 10.0, None, True),
                ("no half-life, no missing-share", None, list(range(k - 1)), False),
                ("half-life 10, no missing-share", 10.0, list(range(k - 1)), False)]
    for label, h, cols, rel in variants:
        idx, s, terms = offsets(d, h, cols, rel_prior=rel)
        y = d["y"][idx]
        cn = names if cols is None else [names[c] for c in cols]
        print(f"== {label}: offset AUC {auc(s, y):.3f} ({len(y)} rows, {y.sum()} onsets)")
        print("   term AUC: " + "  ".join(f"{n} {auc(terms[:, j], y):.3f}" for j, n in enumerate(cn) if j > 0))
        print("   term sd:  " + "  ".join(f"{n} {terms[:, j].std():.3f}" for j, n in enumerate(cn) if j > 0))
    idx = np.where((d["t"] // 12 >= 2003) & (d["t"] // 12 <= 2022))[0]
    y = d["y"][idx]
    print("inputs alone: " + "  ".join(f"{n} {auc(d['X'][idx, j], y):.3f}" for j, n in enumerate(names) if j > 0))


if __name__ == "__main__":
    main(sys.argv[1:])
