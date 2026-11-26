"""bnm-7yg.2: small reference models per kind, against which the lattice is
judged. Reads reference_tap.clj's rows (each kind's year country node: the
outcome, when it became known, the issued p and the inputs x the node's
models read) and, at each December origin 2003-2022, refits on the rows
whose outcome was known by then:

  ref      a ridge logit on x (standardized on the training rows, a missing
           value the mean; prior N(0, 1/LAMBDA) on each coefficient, none on
           the intercept); no offset, no averaging, no hierarchy,
  ref+own  the same with the logit of the country's own resolved rate
           (shrunk to the training rate by SHRINK rows) as one more input.

Writes each as a run, data/runs/<prefix>-ref/<kind>/forecasts.tsv and
data/runs/<prefix>-refown/..., in the forecasts.tsv layout, so
scorecard.py scores them beside the lattice on the same rows.

  python3 -I docs/studies/reference.py <repo> <tap-dir> <prefix> [k1,k2]
"""
import math
import os
import sys
from collections import defaultdict

import numpy as np

LAMBDA = 1.0
SHRINK = 5.0
FROM, TO = 2003, 2022


def fit(X, y, lam=LAMBDA, iters=50):
    """Ridge logistic regression by Newton's method; X without the intercept
    column. Returns (intercept, coefficients)."""
    n, d = X.shape
    A = np.hstack([np.ones((n, 1)), X])
    w = np.zeros(d + 1)
    rate = min(max(y.mean(), 1e-4), 1 - 1e-4)
    w[0] = math.log(rate / (1 - rate))
    pen = np.full(d + 1, lam)
    pen[0] = 0.0
    for _ in range(iters):
        z = A @ w
        p = 1 / (1 + np.exp(-z))
        g = A.T @ (p - y) + pen * w
        H = (A * (p * (1 - p))[:, None]).T @ A + np.diag(pen) + 1e-9 * np.eye(d + 1)
        step = np.linalg.solve(H, g)
        w -= step
        if np.max(np.abs(step)) < 1e-8:
            break
    return w[0], w[1:]


def standardize(train, test):
    """Columns standardized on the training rows; missing (nan) values
    become 0 (the mean); constant columns 0."""
    m = np.nanmean(train, axis=0)
    s = np.nanstd(train, axis=0)
    m = np.where(np.isnan(m), 0.0, m)
    s = np.where((s > 0) & ~np.isnan(s), s, 1.0)
    f = lambda M: np.nan_to_num((M - m) / s, nan=0.0)
    return f(train), f(test)


class Known:
    """Outcomes by the month they became known: the rate over all places,
    and a place's own rate shrunk to it by SHRINK rows, as known at s."""
    def __init__(self, rows):
        import bisect
        self.bisect = bisect
        self.all = sorted((k, y) for _, _, y, k, _ in rows)
        self.all_k = [k for k, _ in self.all]
        self.all_c = np.cumsum([y for _, y in self.all])
        by = defaultdict(list)
        for u, _, y, k, _ in rows:
            by[u].append((k, y))
        self.place = {u: ([k for k, _ in sorted(v)], np.cumsum([y for _, y in sorted(v)])) for u, v in by.items()}

    def _upto(self, ks, cs, s):
        i = self.bisect.bisect_right(ks, s)
        return (int(cs[i - 1]) if i else 0), i

    def base(self, s):
        n1, n = self._upto(self.all_k, self.all_c, s)
        return (n1 + 0.5) / (n + 1)

    def own(self, u, s):
        b = self.base(s)
        if u not in self.place:
            return b
        n1, n = self._upto(*self.place[u], s)
        return (n1 + SHRINK * b) / (n + SHRINK)


def read_tap(path):
    rows = []
    for line in open(path):
        f = line.rstrip("\n").split("\t")
        place, t, y, known = f[0], int(f[1]), f[2] == "true", int(f[3])
        x = [float(v) if v != "" else float("nan") for v in f[6:]]
        rows.append((place, t, y, known, x))
    return rows


def forecasts(rows):
    """{variant: [(place, t, p, y)]} at December origins FROM-TO."""
    width = max(len(r[4]) for r in rows)
    X = np.array([r[4] + [float("nan")] * (width - len(r[4])) for r in rows])
    y = np.array([r[2] for r in rows], dtype=float)
    t = np.array([r[1] for r in rows])
    known = np.array([r[3] for r in rows])
    kn = Known(rows)
    lo = lambda q: math.log(q / (1 - q))
    # each row's own rate as known at its origin
    own_x = np.array([lo(kn.own(r[0], r[1])) for r in rows])
    out = defaultdict(list)
    for o in sorted(set(t)):
        if not (FROM <= o // 12 <= TO):
            continue
        tr = known <= o
        te = t == o
        if tr.sum() < 20 or y[tr].sum() < 2:
            continue
        Xtr, Xte = standardize(X[tr], X[te])
        b0, b = fit(Xtr, y[tr])
        p = 1 / (1 + np.exp(-(b0 + Xte @ b)))
        idx = np.where(te)[0]
        for i, pi in zip(idx, p):
            out["ref"].append((rows[i][0], o, float(pi), bool(y[i])))
        own_tr = own_x[tr][:, None]
        own_te = np.array([[lo(kn.own(rows[i][0], o))] for i in idx])
        Xtr2, Xte2 = standardize(np.hstack([X[tr], own_tr]), np.hstack([X[te], own_te]))
        b0, b = fit(Xtr2, y[tr])
        p = 1 / (1 + np.exp(-(b0 + Xte2 @ b)))
        for i, pi in zip(idx, p):
            out["refown"].append((rows[i][0], o, float(pi), bool(y[i])))
    return out


def write(repo, run, kind, fs):
    d = os.path.join(repo, "data/runs", run, kind)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "forecasts.tsv"), "w") as w:
        for place, o, p, y in fs:
            w.write(f"year\t{place}\t{o}\t{p}\t{'true' if y else 'false'}\n")


def main(argv):
    repo, tap, prefix = argv[:3]
    kinds = argv[3].split(",") if len(argv) > 3 else sorted(f[:-4] for f in os.listdir(tap) if f.endswith(".tsv"))
    for k in kinds:
        out = forecasts(read_tap(os.path.join(tap, k + ".tsv")))
        for v, fs in out.items():
            write(repo, f"{prefix}-{v}", k, fs)
        print(k, {v: len(fs) for v, fs in out.items()})


if __name__ == "__main__":
    main(sys.argv[1:])
