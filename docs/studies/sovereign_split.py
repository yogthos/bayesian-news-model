"""bnm-7yg.6: sovereign defaults, liquidity against solvency, on a run's
sovereign year country rows at December origins 2003-2022 (registered in
docs/CALIBRATION.md, "Diagnosis: sovereign defaults, liquidity against
solvency").

  1. rows split at external debt 50% of GNI as read at the origin
     (Manasse and Roubini 2009's first branch; rows without it a third
     group): onsets, rate, the run's mean p and AUC in each;
  2. at each December origin a ridge logit (reference.py's fit) on the
     country inputs VARS as published by the origin, refit on rows
     resolved LAG months before: pooled, and split, one per debt group
     (a group with under MIN_ONSETS training onsets takes the pooled fit),
     each scored beside the run on the rows all three have.

  python3 -I docs/studies/sovereign_split.py <repo> <run> [--half-life YEARS]

With --half-life (bnm-7yg.6.2, after t162) each training row is weighted
0.5^(age / half-life) and the intercept is set at the history's level
(bnm.theory :level :history): at the weighted mean of the slope terms, the
logit of the training rows' unweighted rate.
"""
import importlib.util
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


sc = _load("scorecard")
ref = _load("reference")
fs = _load("food_split")

KIND = "sovereign_default"
LAG = 48
FROM, TO = 2003, 2022
TRAIN_FROM = 1975
THRESHOLD = 50.0
MIN_ONSETS = 5
VARS = ["debt/external_gni", "debt/government_gdp", "debt/reserves_months",
        "debt/current_account_gdp", "economy/inflation", "economy/gdp_growth"]
SIGNED_LOG = {"economy/inflation"}


def group(external_debt):
    if external_debt is None or (isinstance(external_debt, float) and math.isnan(external_debt)):
        return "unknown"
    return "high" if external_debt >= THRESHOLD else "low"


def features(series, place, t):
    """The VARS for `place` as published by month t; nan where none."""
    out = []
    for v in VARS:
        s = series.get((place, v))
        last = fs.latest(s, t) if s else None
        x = float("nan") if last is None else last[1]
        if v in SIGNED_LOG and not math.isnan(x):
            x = math.copysign(math.log1p(abs(x)), x)
        out.append(x)
    return out


def weights(train_origins, origin, half_life):
    return 0.5 ** ((origin - np.asarray(train_origins)) / 12.0 / half_life)


def wfit(X, y, w, lam=ref.LAMBDA, iters=50):
    """reference.py's ridge logit with row weights w."""
    n, d = X.shape
    A = np.hstack([np.ones((n, 1)), X])
    b = np.zeros(d + 1)
    rate = min(max(float((w * y).sum() / w.sum()), 1e-4), 1 - 1e-4)
    b[0] = math.log(rate / (1 - rate))
    pen = np.full(d + 1, lam)
    pen[0] = 0.0
    for _ in range(iters):
        p = 1 / (1 + np.exp(-(A @ b)))
        g = A.T @ (w * (p - y)) + pen * b
        H = (A * (w * p * (1 - p))[:, None]).T @ A + np.diag(pen) + 1e-9 * np.eye(d + 1)
        step = np.linalg.solve(H, g)
        b -= step
        if np.max(np.abs(step)) < 1e-8:
            break
    return b[0], b[1:]


def history_intercept(X, y, w, b):
    rate = min(max(float(y.mean()), 1e-4), 1 - 1e-4)
    return math.log(rate / (1 - rate)) - float((w * (X @ b)).sum() / w.sum())


def predict(X, y, Xte, w=None):
    Xtr, Xt = ref.standardize(X, Xte)
    if w is None:
        b0, b = ref.fit(Xtr, y)
    else:
        _, b = wfit(Xtr, y, w)
        b0 = history_intercept(Xtr, y, w, b)
    return 1 / (1 + np.exp(-(b0 + Xt @ b)))


def predict_split(X, y, groups, Xte, gte, w=None):
    out = predict(X, y, Xte, w)
    for g in set(gte):
        tr, te = groups == g, gte == g
        if y[tr].sum() >= MIN_ONSETS and (1 - y[tr]).sum() >= MIN_ONSETS:
            out[te] = predict(X[tr], y[tr], Xte[te], None if w is None else w[tr])
    return out


def scores(label, rows):
    """rows [(origin, place, p, y)]."""
    ps = [(p, y) for _, _, p, y in rows]
    return (f"{label:<14} rows {len(ps):5d}  pos {sum(y for _, y in ps):4d}  Brier {sc.brier(ps):.5f}  "
            f"log {sc.loglik(ps):+.5f}  AUC {sc.auc(ps):.3f}  within {sc.within_auc(rows):.3f}  "
            f"mean p {sum(p for p, _ in ps) / len(ps):.4f}")


def main(argv):
    repo, run = argv[0], argv[1]
    hl = float(argv[argv.index("--half-life") + 1]) if "--half-life" in argv else None
    rows = sc.read(repo, run, KIND)
    rows = {k: v for k, v in rows.items() if k[1] // 12 >= TRAIN_FROM}
    series = fs.read_series(os.path.join(repo, "data/slow/series.tsv"), lambda u, v: v in VARS)
    keys = sorted(rows, key=lambda k: (k[1], k[0]))
    X = np.array([features(series, u, o) for u, o in keys])
    y = np.array([float(rows[k][1]) for k in keys])
    org = np.array([o for _, o in keys])
    grp = np.array([group(x[0]) for x in X])
    p_run = np.array([rows[k][0] for k in keys])
    win = (org // 12 >= FROM) & (org // 12 <= TO)

    print(f"sovereign year country rows, December origins {FROM}-{TO}, run {run}; split at external debt {THRESHOLD:g}% of GNI"
          + (f"; half-life {hl:g} years at the history's level" if hl else ""))
    for g in ["low", "high", "unknown"]:
        m = win & (grp == g)
        if m.sum():
            ps = list(zip(p_run[m], y[m]))
            print(f"1. {g:<8} rows {m.sum():5d}  onsets {int(y[m].sum()):4d}  rate {y[m].mean():.4f}  "
                  f"{run} mean p {p_run[m].mean():.4f}  AUC {sc.auc(ps):.3f}")

    pooled, split = {}, {}
    for o in sorted(set(org[win])):
        tr = org <= o - LAG
        te = org == o
        if y[tr].sum() < MIN_ONSETS:
            continue
        w = None if hl is None else weights(org[tr], o, hl)
        pp = predict(X[tr], y[tr], X[te], w)
        sp = predict_split(X[tr], y[tr], grp[tr], X[te], grp[te], w)
        for i, a, b in zip(np.where(te)[0], pp, sp):
            pooled[i], split[i] = float(a), float(b)
    idx = sorted(pooled)
    mk = lambda f: [(int(org[i]), keys[i][0], f(i), bool(y[i])) for i in idx]
    print(f"\n2. causal refits, {len(idx)} rows")
    print(scores(run, mk(lambda i: float(p_run[i]))))
    print(scores("pooled logit", mk(lambda i: pooled[i])))
    print(scores("split logit", mk(lambda i: split[i])))
    for g in ["low", "high", "unknown"]:
        sel = [i for i in idx if grp[i] == g]
        if sel and y[sel].sum():
            f = lambda d: sc.auc([(d[i], bool(y[i])) for i in sel])
            print(f"   {g:<8} onsets {int(y[sel].sum()):3d}  AUC {run} {sc.auc([(float(p_run[i]), bool(y[i])) for i in sel]):.3f}  "
                  f"pooled {f(pooled):.3f}  split {f(split):.3f}")


if __name__ == "__main__":
    main(sys.argv[1:])
