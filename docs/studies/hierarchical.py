"""bnm-7yg.8.7 (R2, registered in docs/CALIBRATION.md, "R2 registration"):
the hierarchical challenger at the kinds R1 flagged combination, from
docs/studies/combination_tap.clj's tables (the node's inputs x, its offset o
and the frailty's offsets osz) and its groups files:

  logit p_it = a + l_it + b'w_it + c'm_t + g o~_it

  l_it  the common per-origin shock, taken from the lattice's frailty: the
        issued p is integrated over the shifts osz_j - o, and fitted with
        their mean as an offset (world gauges enter only there);
  w_it  the country inputs net of the origin's mean (x_it - m_t), m_t the
        origin's mean across countries (the between part, its own slopes c);
  o~_it the node's offset net of the origin's mean, g's prior N(0, 0.1);
  each input's prior N(0, 1/n_g) (its group's n_g inputs share a unit prior
  variance; an input in several groups takes the smallest share; one read by
  every group, or by none, N(0, 1)), inputs standardized on the training
  rows; a free; training rows weighted 0.5^(age / 10 years), refit at each
  December origin 2003-2022 on the rows known by it from 1975 on.

No scores here: R3 judges (docs/studies/judge.py).

  python3 -I docs/studies/hierarchical.py <tap-dir> <out-dir> [k1,k2]
"""
import importlib.util
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
HALF_LIFE = 10.0
G_VAR = 0.1
FROM, TO, TRAIN_FROM = 2003, 2022, 1975
MIN_ONSETS = 5


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def expit(z):
    return 1 / (1 + np.exp(-np.clip(np.asarray(z, float), -700, 700)))


def logit(p):
    p = np.clip(np.asarray(p, float), 1e-12, 1 - 1e-12)
    return np.log(p / (1 - p))


def input_variances(groups, nx):
    """Prior variances of x_1..x_{nx-1} (x_0 the intercept): an input's group
    shares a unit variance among its inputs that not every group reads."""
    named = {g: set(ix) - {0} for g, ix in groups.items() if g not in ("all", "base")}
    core = set.intersection(*named.values()) if named else set()
    sizes = {g: len(ix - core) for g, ix in named.items()}
    v = np.ones(nx - 1)
    for i in range(1, nx):
        if i in core:
            continue
        shares = [1.0 / sizes[g] for g, ix in named.items() if i in ix]
        if shares:
            v[i - 1] = min(shares)
    return v


def decompose(X, origin):
    """(within, between): each row's inputs less its origin's mean across
    rows, and that mean."""
    B = np.zeros_like(X, dtype=float)
    for o in np.unique(origin):
        m = origin == o
        B[m] = X[m].mean(0)
    return X - B, B


def wridge(Z, y, w, pen, offset=None, iters=60):
    """Weighted ridge logit: maximizes sum w (y log p + (1-y) log (1-p)) -
    sum pen b^2 / 2, logit p = offset + Z b."""
    b = np.zeros(Z.shape[1])
    off = np.zeros(len(y)) if offset is None else offset
    for _ in range(iters):
        p = expit(Z @ b + off)
        g = Z.T @ (w * (p - y)) + pen * b
        H = (Z * (w * p * (1 - p))[:, None]).T @ Z + np.diag(pen) + 1e-9 * np.eye(len(b))
        step = np.linalg.solve(H, g)
        b -= step
        if np.max(np.abs(step)) < 1e-8:
            break
    return b


def shifts(osz, o):
    return [[(w, oj - oi) for w, oj in oz] for oz, oi in zip(osz, o)]


def fit_offset(sh):
    """The mean shift: the log odds whose p is the shifts' average about 0."""
    return np.array([float(logit(sum(w * expit(s) for w, s in sz))) for sz in sh])


def _standardize(tr, te):
    m, s = tr.mean(0), tr.std(0)
    s = np.where(s > 0, s, 1.0)
    return (tr - m) / s, (te - m) / s


def challenger(d, groups):
    """p at the December origins FROM-TO; nan elsewhere."""
    X = np.nan_to_num(np.asarray(d["X"], float)[:, 1:], nan=0.0)
    v = input_variances(groups, X.shape[1] + 1)
    W, B = decompose(X, d["origin"])
    oc = d["o"] - decompose(d["o"][:, None], d["origin"])[1][:, 0]
    F = np.column_stack([W, B, oc])
    pen = np.r_[0.0, 1 / v, 1 / v, 1 / G_VAR]
    sh = shifts(d["osz"], d["o"])
    off = fit_offset(sh)
    y = np.asarray(d["y"], float)
    out = np.full(len(y), np.nan)
    yrs = d["origin"] // 12
    for o in sorted(set(d["origin"][(yrs >= FROM) & (yrs <= TO)])):
        tr = (d["known"] <= o) & (d["origin"] >= TRAIN_FROM * 12)
        te = d["origin"] == o
        if y[tr].sum() < MIN_ONSETS:
            continue
        Ftr, Fte = _standardize(F[tr], F[te])
        Z = lambda M: np.column_stack([np.ones(len(M)), M])
        wt = 0.5 ** ((o - d["origin"][tr]) / 12.0 / HALF_LIFE)
        b = wridge(Z(Ftr), y[tr], wt, pen, offset=off[tr])
        eta = Z(Fte) @ b
        out[te] = [sum(w * expit(e + s) for w, s in sz) for e, sz in zip(eta, [sh[i] for i in np.where(te)[0]])]
    return out


def read_groups(path):
    with open(path) as fh:
        return {g: [int(i) for i in ix.split(",") if i] for g, ix in (l.rstrip("\n").split("\t") for l in fh)}


def write_forecasts(path, place, origin, p):
    with open(path, "w") as fh:
        for u, o, q in zip(place, origin, p):
            if not math.isnan(q):
                fh.write(f"{u}\t{int(o)}\t{float(q)!r}\n")


def main(argv):
    tap, out = argv[0], argv[1]
    kinds = argv[2].split(",") if len(argv) > 2 else ["conflict_onset", "war_onset", "growth_collapse"]
    cb = _load("combination")
    os.makedirs(out, exist_ok=True)
    for k in kinds:
        d = cb.read(os.path.join(tap, k + ".tsv"))
        p = challenger(d, read_groups(os.path.join(tap, k + ".groups.tsv")))
        m = ~np.isnan(p)
        write_forecasts(os.path.join(out, k + ".tsv"), d["place"], d["origin"], p)
        print(k, "rows", int(m.sum()), "origins", len(set(d["origin"][m])), "p range", float(p[m].min()), float(p[m].max()))


if __name__ == "__main__":
    main(sys.argv[1:])
