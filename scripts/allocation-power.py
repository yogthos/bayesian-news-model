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

A country with fewer than five training rows takes the pooled intercept,
as registered. The placebo's map is `derange`: uniform over derangements
of the sorted countries.

The bound is recomputed on the rows actually scored by passing them with
`--rows` (a c,t csv from scripts/allocation-test.py --rows), in place of the
mask from the .dta.

Usage: python -I scripts/allocation-power.py <MuellerVerner.dta> [--sims N] [--boot N]
       python -I scripts/allocation-power.py --rows <rows.csv> [--sims N] [--boot N]
       python -I scripts/allocation-power.py --selftest
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


MIN_ROWS = 5


def derange(countries, rng):
    """{c c'}: a derangement of `countries` (no country maps to itself),
    uniform over derangements, drawn by `rng` from the sorted countries, so
    the same countries and seed give the same map whatever their order."""
    cs = np.array(sorted(set(countries)))
    assert len(cs) > 1, "a derangement needs two countries"
    while True:
        p = rng.permutation(cs)
        if not (p == cs).any():
            return dict(zip(cs.tolist(), p.tolist()))


def within_fit(Xtr, ytr, gtr):
    """OLS with country intercepts: the coefficients, each country's
    intercept (its training mean of y - X b) where it has at least MIN_ROWS
    training rows, and the pooled intercept the others take."""
    df = pd.DataFrame(Xtr)
    mx = df.groupby(gtr).transform("mean").to_numpy()
    my = pd.Series(ytr).groupby(gtr).transform("mean").to_numpy()
    Xd, yd = Xtr - mx, ytr - my
    b, *_ = np.linalg.lstsq(Xd, yd, rcond=None)
    r = pd.Series(ytr - Xtr @ b).groupby(gtr)
    a = r.mean()[r.size() >= MIN_ROWS]
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
    perm = derange(countries, rng)
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
    ap.add_argument("dta", nargs="?")
    ap.add_argument("--rows")
    ap.add_argument("--sims", type=int, default=400)
    ap.add_argument("--boot", type=int, default=1000)
    ap.add_argument("--rho", type=float, default=0.2)
    ap.add_argument("--phi", type=float, default=0.9)
    ap.add_argument("--seed", type=int, default=20261024)
    a = ap.parse_args()
    m = pd.read_csv(a.rows) if a.rows else mask(a.dta)
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


def selftest():
    """Checks on toy inputs: python -I scripts/allocation-power.py --selftest."""
    cs = np.array([f"C{i:02d}" for i in range(30)])
    for seed in range(300):
        p = derange(cs, np.random.default_rng(seed))
        assert sorted(p) == sorted(cs) and sorted(p.values()) == sorted(cs), "a permutation"
        assert all(p[c] != c for c in cs), f"a fixed point at seed {seed}"
    assert derange(cs, np.random.default_rng(20261024)) == derange(cs[::-1], np.random.default_rng(20261024)), \
        "the same countries and seed give the same derangement, whatever their order"
    # a country with fewer than MIN_ROWS training rows takes the pooled intercept
    g = np.array(["A"] * 6 + ["B"] * 3)
    X = np.arange(9.0)[:, None]
    y = np.r_[np.full(6, 1.0), np.full(3, 5.0)] + 0.5 * X[:, 0]
    b, a, a0 = within_fit(X, y, g)
    assert abs(b[0] - 0.5) < 1e-9 and abs(a["A"] - 1.0) < 1e-9, (b, a)
    assert "B" not in a.index, "three rows: pooled"
    print("allocation-power selftest ok")


if __name__ == "__main__":
    import sys
    if sys.argv[1:] == ["--selftest"]:
        selftest()
    else:
        main()
