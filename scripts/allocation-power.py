"""Power of the registered allocation test (bead bnm-4hv.12;
docs/COUPLED-SYSTEMS.md 9d), before any outcome is read.

Reads only which Müller-Verner country-years hold total, household and
manufacturing credit (a mask: no credit value enters the simulation) and
simulates the registered design on that mask:

  outcome    y[c,t]  the change in the manufacturing share of private credit
  baseline   B: y[c,t] = a_c + rho y[c,t-1] + e
  with       M: B + beta x[c,t-1]
  placebo    P: B + beta x[c',t-1], c' a fixed derangement of countries

x is a standardised within-country AR(1) regressor (persistence `--phi`,
the control stocks move slowly); the outcome's own noise has SD 1 and AR
coefficient `--rho`, so beta^2 Var(x) / (beta^2 Var(x) + 1) is the share of
the outcome's variance the regressor explains (the partial R^2). Income is
left out: it enters B, M and P alike.

Expanding window, as registered: at each test target year t (2003-2014)
the three models are fitted by within-country OLS on the rows with target
year <= t - 1 and score the rows of year t. Skill = 1 - SSE_M / SSE_X
pooled over the test rows; its 90% interval by resampling countries (the
cluster bootstrap). A pass: the lower ends of skill against B and against P
are both above 0.

Prints, per partial R^2, the share of simulations that pass and the median
skill, and the null's pass rate. The equivalence bound is the smallest
skill that passes 80% of the time.

Usage: python -I scripts/allocation-power.py <MuellerVerner.dta> [--sims N]
"""
import argparse

import numpy as np
import pandas as pd


def mask(path):
    mv = pd.read_stata(path, columns=["country", "year", "Total", "HH", "C"])
    mv = mv.groupby(["country", "year"], as_index=False).last()
    ok = mv[["Total", "HH", "C"]].notna().all(axis=1)
    have = set(zip(mv.country[ok], mv.year[ok].astype(int)))
    # y[c,t] needs the share at t and t-1; its lag y[c,t-1] needs t-2
    rows = sorted((c, t) for c, t in have if (c, t - 1) in have and (c, t - 2) in have)
    return pd.DataFrame(rows, columns=["c", "t"])


def within_fit(Xtr, ytr, gtr):
    """OLS with country intercepts: the coefficients and each country's
    intercept (its training mean of y - X b)."""
    df = pd.DataFrame(Xtr)
    mx = df.groupby(gtr).transform("mean").to_numpy()
    my = pd.Series(ytr).groupby(gtr).transform("mean").to_numpy()
    Xd, yd = Xtr - mx, ytr - my
    b, *_ = np.linalg.lstsq(Xd, yd, rcond=None)
    a = pd.Series(ytr - Xtr @ b).groupby(gtr).mean()
    return b, a, float(np.mean(ytr - Xtr @ b))


def simulate(m, r2, rho, phi, rng, test_years, nboot):
    countries = m.c.unique()
    span = range(m.t.min() - 3, m.t.max() + 1)
    beta = np.sqrt(r2 / (1 - r2)) if r2 > 0 else 0.0
    x, y = {}, {}
    for c in countries:
        xs = [rng.standard_normal()]
        for _ in span[1:]:
            xs.append(phi * xs[-1] + np.sqrt(1 - phi ** 2) * rng.standard_normal())
        x[c] = dict(zip(span, xs))
    perm = dict(zip(countries, np.roll(rng.permutation(countries), 1)))
    for c in countries:
        ys = {span[0]: rng.standard_normal()}
        for t in span[1:]:
            ys[t] = rho * ys[t - 1] + beta * x[c][t - 1] + rng.standard_normal()
        y[c] = ys
    g = m.c.to_numpy()
    tt = m.t.to_numpy()
    Y = np.array([y[c][t] for c, t in zip(g, tt)])
    L = np.array([y[c][t - 1] for c, t in zip(g, tt)])
    X = np.array([x[c][t - 1] for c, t in zip(g, tt)])
    XP = np.array([x[perm[c]][t - 1] for c, t in zip(g, tt)])
    designs = {"B": L[:, None], "M": np.c_[L, X], "P": np.c_[L, XP]}
    err = {k: [] for k in designs}
    who = []
    for ty in test_years:
        tr, te = tt <= ty - 1, tt == ty
        if not te.any():
            continue
        for k, D in designs.items():
            b, a, a0 = within_fit(D[tr], Y[tr], g[tr])
            alpha = np.array([a.get(c, a0) for c in g[te]])
            err[k].append(Y[te] - alpha - D[te] @ b)
        who.append(g[te])
    e = {k: np.concatenate(v) ** 2 for k, v in err.items()}
    who = np.concatenate(who)
    cs = np.unique(who)
    idx = {c: np.flatnonzero(who == c) for c in cs}
    sums = {k: np.array([v[idx[c]].sum() for c in cs]) for k, v in e.items()}
    skill = {o: 1 - sums["M"].sum() / sums[o].sum() for o in ("B", "P")}
    draws = rng.integers(0, len(cs), size=(nboot, len(cs)))
    lo = {o: np.quantile(1 - sums["M"][draws].sum(1) / sums[o][draws].sum(1), 0.05) for o in ("B", "P")}
    return skill, lo, len(who), len(cs)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dta")
    ap.add_argument("--sims", type=int, default=300)
    ap.add_argument("--boot", type=int, default=500)
    ap.add_argument("--rho", type=float, default=0.2)
    ap.add_argument("--phi", type=float, default=0.9)
    ap.add_argument("--seed", type=int, default=20261024)
    a = ap.parse_args()
    m = mask(a.dta)
    test_years = range(2003, 2015)
    print(f"mask: {len(m)} outcome rows with a lag, {m.c.nunique()} countries, "
          f"{int((m.t >= 2003).sum())} test rows (2003-2014), {m[m.t >= 2003].c.nunique()} test countries")
    print(f"rho {a.rho}, phi {a.phi}, {a.sims} simulations, {a.boot} bootstrap draws")
    rng = np.random.default_rng(a.seed)
    for r2 in [0.0, 0.005, 0.01, 0.015, 0.02, 0.025, 0.03, 0.04, 0.05]:
        res = [simulate(m, r2, a.rho, a.phi, rng, test_years, a.boot) for _ in range(a.sims)]
        passes = np.mean([lo["B"] > 0 and lo["P"] > 0 for _, lo, _, _ in res])
        sk = np.median([s["B"] for s, _, _, _ in res])
        hi = np.quantile([s["B"] for s, _, _, _ in res], [0.1, 0.9])
        print(f"partial R2 {r2:.3f}: pass {passes:.3f}, skill vs B median {sk:+.4f} [{hi[0]:+.4f}, {hi[1]:+.4f}]")


if __name__ == "__main__":
    main()
