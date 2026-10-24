"""The registered allocation test (bead bnm-4hv.12; docs/COUPLED-SYSTEMS.md
9d, with its addendum and second addendum).

  outcome  y[c,t] = 100 (s[c,t] - s[c,t-1]), s = C / Total (Muller-Verner)
  B        y = a_c + rho y[c,t-1] + lambda ln income[c,t-1] + e
  M        B + beta x[c,t-1]
  P        B + beta x[c',t-1], c' the fixed derangement of the second addendum

One-step forecasts at yearly origins with an expanding window. Each row's
inputs are read once, at its own origin to = t + 1: the outcome's lag
(data year to-2, Muller-Verner known at +2), income (Maddison 2023 gdppc,
the latest data year at or before to-2, known at +1) and the regressor:
  registered  control/state (data/derived/stocks/control.csv) at its
              latest data year at or before to-2, as known by to
  fit         a column of data/derived/control/control-variants.csv at
              fit year to-1 (S1 `equal`, S2 `no-own`, and the primary's
              `stock` under that alignment)
At origin to the models are fitted, by OLS with country intercepts (a
country with fewer than five training rows takes the pooled intercept),
on the paired rows with target year <= to-2, and score the rows of
target year to-1. Test targets 2003-2014.

Skill against X = 1 - SSE_M / SSE_X over the test rows; its 90% interval
from 2,000 country-resampled draws (one default_rng(20261024) per
regressor; the same draws for B and P). Pass: both lower ends above 0.
Equivalent to nothing: no pass and the upper end against B below the
bound. The secondaries' p is the larger of their two one-sided bootstrap
p-values (the share of draws with skill <= 0), Holm over S1 and S2.

Usage: python -I scripts/allocation-test.py --rows OUTDIR   (the scored rows; no outcome read)
       python -I scripts/allocation-test.py [--bound-primary B] [--bound-s1 B] [--bound-s2 B]
       python -I scripts/allocation-test.py --selftest
"""
import importlib.util
import os

import numpy as np
import pandas as pd


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_spec = importlib.util.spec_from_file_location("allocation_power", os.path.join(ROOT, "scripts", "allocation-power.py"))
_ap = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_ap)
derange, within_fit = _ap.derange, _ap.within_fit

SEED = 20261024
NBOOT = 2000
TEST_YEARS = range(2003, 2015)
PRIMARY_MASK = ["Total", "HH", "C"]
MODELS = {"B": ["ylag", "inc"], "M": ["ylag", "inc", "x"], "P": ["ylag", "inc", "xp"], "B0": ["ylag"]}


# -- readers: each returns (value, known year, ...) or None ------------------------------

def read_registered(rows, to):
    """control/state at origin `to`: of the rows (year known, data year,
    value) with year <= to and data year <= to-2, the latest data year, as
    last known by `to`: (value, known, data year)."""
    ok = [(dy, y, v) for y, dy, v in rows if y <= to and dy <= to - 2]
    if not ok:
        return None
    dy, y, v = max(ok)
    return (v, y, dy)


def read_fit(table, iso, to):
    """A variant at fit year to-1: (value, to-1)."""
    v = table.get((iso, to - 1))
    return None if v is None else (v, to - 1)


def read_income(series, to):
    """ln income at its latest data year <= to-2 (known at +1): (value, known)."""
    ys = [y for y in series if y <= to - 2]
    if not ys:
        return None
    y = max(ys)
    return (series[y], y + 1)


# -- the mask and the rows -----------------------------------------------------------------

def part_present(sectors, p):
    if isinstance(p, tuple):
        a, b, ab = p
        return (a in sectors and b in sectors) or ab in sectors
    return p in sectors


def group_value(m, parts):
    """The sum of `parts` in sector map m (a pair part (a, b, ab) is a + b
    when both are there, else ab), None when one is missing; as
    bnm.allocation/group-value."""
    tot = 0.0
    for p in parts:
        if isinstance(p, tuple):
            a, b, ab = p
            v = m[a] + m[b] if (a in m and b in m) else m.get(ab)
        else:
            v = m.get(p)
        if v is None:
            return None
        tot += float(v)
    return tot


def mask(presence, parts):
    """The (country, t) whose parts are all present at t, t-1 and t-2."""
    have = {k for k, s in presence.items() if all(part_present(s, p) for p in parts)}
    return sorted((c, t) for c, t in have if (c, t - 1) in have and (c, t - 2) in have)


def build(mask_rows, xr, inc, perm):
    """The paired rows: each mask row with its inputs read at its origin
    t + 1, kept where income, the regressor and the partner's regressor are
    all there. `known` is the latest known year of any input (the
    outcome's lag, data year t-1, is known at t + 1)."""
    out = []
    for c, t in mask_rows:
        to = t + 1
        i = inc(c, to)
        x = xr(c, to)
        xp = xr(perm[c], to) if c in perm else None
        if i is None or x is None or xp is None:
            continue
        out.append((c, t, to, x[0], xp[0], i[0], max(t + 1, x[1], xp[1], i[1])))
    return pd.DataFrame(out, columns=["c", "t", "origin", "x", "xp", "inc", "known"])


def attach_outcome(df, values, parts):
    """y and its lag, in pp of the share of `parts` in Total; rows whose
    share is undefined (Total <= 0) drop."""
    def share(c, t):
        m = values.get((c, t))
        if m is None:
            return None
        tot, g = m.get("Total"), group_value(m, parts)
        if tot is None or g is None or not tot > 0:
            return None
        return g / tot
    ys, ls, keep = [], [], []
    for c, t in zip(df.c, df.t):
        s0, s1, s2 = share(c, t), share(c, t - 1), share(c, t - 2)
        ok = None not in (s0, s1, s2)
        keep.append(ok)
        ys.append(100 * (s0 - s1) if ok else np.nan)
        ls.append(100 * (s1 - s2) if ok else np.nan)
    out = df.assign(y=ys, ylag=ls)
    return out[np.array(keep, dtype=bool)].reset_index(drop=True)


# -- the forecasts and the scores -------------------------------------------------------------

def forecast(df, test_years, designs):
    """At each origin to = ty + 1, fit on rows with t <= ty - 1 and forecast
    the rows with t == ty. Rows: c, t, origin, y, f_<k>, e_<k> (squared
    error), and the latest target year and known year used in training."""
    g, tt = df.c.to_numpy(), df.t.to_numpy()
    Y = df.y.to_numpy(dtype=float)
    known = df.known.to_numpy()
    out = []
    for ty in test_years:
        to = ty + 1
        tr, te = tt <= ty - 1, tt == ty
        if not te.any() or not tr.any():
            continue
        assert (known[te] <= to).all() and (known[tr] <= to).all()
        part = df.loc[te, ["c", "t", "origin", "y"]].copy()
        part["train_max_t"] = int(tt[tr].max())
        part["train_max_known"] = int(max(known[tr].max(), tt[tr].max() + 2))
        for k, cols in designs.items():
            D = df[cols].to_numpy(dtype=float)
            b, a, a0 = within_fit(D[tr], Y[tr], g[tr])
            alpha = np.array([a.get(c, a0) for c in g[te]])
            f = alpha + D[te] @ b
            part["f_" + k] = f
            part["e_" + k] = (Y[te] - f) ** 2
        out.append(part)
    return pd.concat(out, ignore_index=True)


def evaluate(test, nboot, seed, against=("B", "P")):
    """Skill of M against each X over `test`, the 5% and 95% quantiles of
    the country-resampled skill, and the one-sided p (share of draws <= 0)."""
    cs = np.array(sorted(test.c.unique()))
    sums = {k: test.groupby("c")["e_" + k].sum().reindex(cs).to_numpy() for k in ("M",) + tuple(against)}
    draws = np.random.default_rng(seed).integers(0, len(cs), size=(nboot, len(cs)))
    res = {"skill": {}, "lo": {}, "hi": {}, "p": {}, "rows": len(test), "countries": len(cs)}
    for x in against:
        res["skill"][x] = 1 - sums["M"].sum() / sums[x].sum()
        bs = 1 - sums["M"][draws].sum(1) / sums[x][draws].sum(1)
        res["lo"][x], res["hi"][x] = np.quantile(bs, [0.05, 0.95])
        res["p"][x] = float(np.mean(bs <= 0))
    return res


def verdict(lo, hi, bound):
    if lo["B"] > 0 and lo["P"] > 0:
        return "pass"
    if hi["B"] < bound:
        return "equivalent to nothing"
    return "fail"


def holm(ps, alpha=0.05):
    """Holm's step-down rejections, in the order of `ps`."""
    order = np.argsort(ps, kind="stable")
    rej = [False] * len(ps)
    for i, j in enumerate(order):
        if ps[j] <= alpha / (len(ps) - i):
            rej[j] = True
        else:
            break
    return rej


def holm_adjusted(ps):
    order = np.argsort(ps, kind="stable")
    adj, run_max = [0.0] * len(ps), 0.0
    for i, j in enumerate(order):
        run_max = max(run_max, min(1.0, (len(ps) - i) * ps[j]))
        adj[j] = run_max
    return adj


def clustered_beta(df, cols, k="x"):
    """The within-country OLS coefficient on `k` over all rows of df, with
    a country-clustered (CR1) standard error."""
    X = df[cols].to_numpy(dtype=float)
    y = df.y.to_numpy(dtype=float)
    g = df.c.to_numpy()
    Xd = X - pd.DataFrame(X).groupby(g).transform("mean").to_numpy()
    yd = y - pd.Series(y).groupby(g).transform("mean").to_numpy()
    XtX = np.linalg.inv(Xd.T @ Xd)
    b = XtX @ Xd.T @ yd
    u = yd - Xd @ b
    meat = np.zeros((len(cols), len(cols)))
    for c in np.unique(g):
        s = Xd[g == c].T @ u[g == c]
        meat += np.outer(s, s)
    G, N, K = len(np.unique(g)), len(y), len(cols) + len(np.unique(g))
    V = XtX @ meat @ XtX * G / (G - 1) * (N - 1) / (N - K)
    j = cols.index(k)
    return float(b[j]), float(np.sqrt(V[j, j]))


def run(data, xreader, perm, test_years, nboot, seed, num, need=None, bound=0.025, mask_rows=None):
    """The test for one regressor reader on data {values, inc}: the verdict,
    skills, intervals, p, the full-sample beta and the test rows."""
    need = need or ["Total", "HH"] + list(num)
    m = mask_rows if mask_rows is not None else mask(presence_of(data["values"]), need)
    inc = lambda iso, to: read_income(data["inc"].get(iso, {}), to)
    df = attach_outcome(build(m, xreader, inc, perm), data["values"], num)
    test = forecast(df, test_years, MODELS)
    res = evaluate(test, nboot, seed)
    res["verdict"] = verdict(res["lo"], res["hi"], bound)
    res["p_iu"] = max(res["p"].values())
    res["beta"], res["se"] = clustered_beta(df[df.t <= max(test_years)], MODELS["M"])
    res["test"], res["rows_all"] = test, df
    return res


# -- the data and the report -------------------------------------------------------------------

PANEL = os.path.join(ROOT, "data", "derived", "allocation", "panel.csv")
CONTROL = os.path.join(ROOT, "data", "derived", "stocks", "control.csv")
VARIANTS = os.path.join(ROOT, "data", "derived", "control", "control-variants.csv")
MADDISON = os.path.join(ROOT, "data", "raw", "slow", "maddison", "mpd2023_web.xlsx")


def load_presence():
    """{(iso, year): sectors} of the Muller-Verner rows: which values exist, not what they are."""
    d = pd.read_csv(PANEL, usecols=["iso", "year", "source", "sector"])
    d = d[d.source == "mv"]
    return {k: set(v) for k, v in d.groupby(["iso", "year"]).sector}


def load_values():
    d = pd.read_csv(PANEL)
    d = d[d.source == "mv"]
    out = {}
    for iso, y, sec, v in zip(d.iso, d.year, d.sector, d.value):
        out.setdefault((iso, int(y)), {})[sec] = float(v)
    return out


def load_control():
    d = pd.read_csv(CONTROL)
    d = d[d.variable == "control/state"]
    out = {}
    for iso, y, dy, v in zip(d.iso, d.year, d.data_year, d.value):
        out.setdefault(iso, []).append((int(y), int(dy), float(v)))
    return out


def load_variants():
    d = pd.read_csv(VARIANTS)
    return {k: dict(zip(zip(g.iso, g.year.astype(int)), g.value.astype(float))) for k, g in d.groupby("variant")}


def load_income():
    m = pd.read_excel(MADDISON, sheet_name="Full data", usecols=["countrycode", "year", "gdppc"]).dropna()
    m = m[m.gdppc > 0]
    out = {}
    for iso, y, v in zip(m.countrycode, m.year, m.gdppc):
        out.setdefault(iso, {})[int(y)] = float(np.log(v))
    return out


def setup():
    """Everything but the outcome: the mask, readers, income and the placebo map."""
    pres, ctl, var, inc = load_presence(), load_control(), load_variants(), load_income()
    m = mask(pres, PRIMARY_MASK)
    incr = lambda iso, to: read_income(inc.get(iso, {}), to)
    readers = {
        "primary": lambda iso, to: read_registered(ctl.get(iso, []), to),
        "primary-fit": lambda iso, to: read_fit(var["stock"], iso, to),
        "S1": lambda iso, to: read_fit(var["equal"], iso, to),
        "S2": lambda iso, to: read_fit(var["no-own"], iso, to),
    }
    have = sorted({c for c, t in m if incr(c, t + 1) is not None and readers["primary"](c, t + 1) is not None})
    perm = derange(have, np.random.default_rng(SEED))
    return {"presence": pres, "mask": m, "inc": inc, "incr": incr, "readers": readers, "perm": perm}


def write_rows(outdir):
    """The paired rows of each regressor (c, t), for the power recomputation."""
    s = setup()
    os.makedirs(outdir, exist_ok=True)
    print(f"mask: {len(s['mask'])} rows; placebo map over {len(s['perm'])} countries")
    for name, xr in s["readers"].items():
        df = build(s["mask"], xr, s["incr"], s["perm"])
        df[["c", "t"]].to_csv(os.path.join(outdir, f"rows-{name}.csv"), index=False)
        te = df[df.t.isin(TEST_YEARS)]
        print(f"{name}: {len(df)} paired rows in {df.c.nunique()} countries; "
              f"{len(te)} test rows in {te.c.nunique()} countries")


def fmt(r, x):
    return f"{r['skill'][x]:+.4f} [{r['lo'][x]:+.4f}, {r['hi'][x]:+.4f}] p={r['p'][x]:.4f}"


def report(bounds):
    s = setup()
    data = {"values": load_values(), "inc": s["inc"]}
    rd, perm = s["readers"], s["perm"]

    def go(name, num=("C",), need=None, mask_rows=None):
        return run(data, rd[name], perm, TEST_YEARS, NBOOT, SEED, list(num), need=need,
                   bound=bounds.get(name, 0.025), mask_rows=mask_rows)

    res = {k: go(k, mask_rows=s["mask"]) for k in ("primary", "S1", "S2", "primary-fit")}
    print(f"placebo map over {len(perm)} countries")
    print("\nPRIMARY (control/state, registered reading)")
    r = res["primary"]
    print(f"  rows {r['rows']} in {r['countries']} countries (all paired rows {len(r['rows_all'])})")
    print(f"  M vs B: {fmt(r, 'B')}")
    print(f"  M vs P: {fmt(r, 'P')}")
    print(f"  bound {bounds.get('primary', 0.025):+.4f}; verdict: {r['verdict']}")

    print("\nSECONDARY (fit-year reading, Holm over S1 and S2)")
    ps = [res["S1"]["p_iu"], res["S2"]["p_iu"]]
    rej, adj = holm(ps), holm_adjusted(ps)
    for i, k in enumerate(("S1", "S2")):
        r = res[k]
        print(f"  {k}: rows {r['rows']} in {r['countries']} countries")
        print(f"    M vs B: {fmt(r, 'B')}")
        print(f"    M vs P: {fmt(r, 'P')}")
        print(f"    p (IU) {ps[i]:.4f}, Holm-adjusted {adj[i]:.4f}, passes after Holm: {rej[i]}; "
              f"bound {bounds.get(k, 0.025):+.4f}; interval verdict: {r['verdict']}")

    print("\nREPORTED ONLY")
    for k in ("primary", "S1", "S2", "primary-fit"):
        r = res[k]
        print(f"  full-sample within beta, {k}: {r['beta']:+.4f} (clustered SE {r['se']:.4f}, "
              f"t {r['beta'] / r['se']:+.2f}) over {len(r['rows_all'][r['rows_all'].t <= 2014])} rows")
    r = res["primary-fit"]
    print(f"  primary at fit year to-1: rows {r['rows']}; M vs B {fmt(r, 'B')}; M vs P {fmt(r, 'P')}; {r['verdict']}")
    t = res["primary"]["test"]
    for lo, hi in ((2003, 2007), (2008, 2014)):
        e = evaluate(t[(t.t >= lo) & (t.t <= hi)], NBOOT, SEED)
        print(f"  primary {lo}-{hi}: rows {e['rows']}; M vs B {fmt(e, 'B')}; M vs P {fmt(e, 'P')}")
    e = evaluate(t, NBOOT, SEED, against=("B0",))
    print(f"  primary against persistence alone: {fmt(e, 'B0')}")
    for label, num, need in (("tradable", ["A", ("B", "C", "BC")], ["Total", "A", ("B", "C", "BC")]),
                             ("household", ["HH"], ["Total", "HH"])):
        r = go("primary", num=num, need=need)
        print(f"  {label} share: rows {r['rows']} in {r['countries']} countries; M vs B {fmt(r, 'B')}; "
              f"M vs P {fmt(r, 'P')}; beta {r['beta']:+.4f} (SE {r['se']:.4f})")


# -- the selftest ------------------------------------------------------------------------

def synth(beta, rng, n=60, years=range(1970, 2015)):
    """A toy world: shares s = C / Total whose yearly change in pp follows
    y[t] = 0.2 y[t-1] + beta x[t-1] + 0.3 ln income[t-1] + a_c + noise,
    x a within-country AR(1) published at +1 (control rows: year = data
    year + 1), its fit-year variant, and income at +1."""
    cs = [f"C{i:02d}" for i in range(n)]
    values, ctl, var, inc = {}, {}, {}, {}
    for c in cs:
        a = rng.normal(0, 0.5)
        x, lninc = {}, {}
        xv, iv = rng.standard_normal(), rng.normal(9, 0.5)
        for t in years:
            xv = 0.9 * xv + np.sqrt(1 - 0.81) * rng.standard_normal()
            iv += rng.normal(0.02, 0.02)
            x[t], lninc[t] = xv, iv
        s, yprev = 0.2, 0.0
        for t in years:
            if t > years[0]:
                yv = a + 0.2 * yprev + beta * x[t - 1] + 0.3 * (lninc[t - 1] - 9) + rng.standard_normal()
                s += yv / 100
                yprev = yv
            values[(c, t)] = {"Total": 1.0, "HH": 0.3, "C": s}
        ctl[c] = [(t + 1, t, x[t]) for t in years]
        for t in years:
            var[(c, t + 1)] = x[t]
        inc[c] = {t: lninc[t] for t in years}
    return {"values": values, "ctl": ctl, "var": var, "inc": inc, "countries": cs}


def presence_of(values):
    return {k: set(v) for k, v in values.items()}


def selftest():
    ap = _ap
    parts = ["C"]

    # readers: the registered reading takes the latest data year <= to-2 known by to
    rows = [(2000, 1999, 1.0), (2001, 2000, 2.0), (2002, 2001, 3.0), (2003, 2001, 3.5)]
    assert read_registered(rows, 2002) == (2.0, 2001, 2000), read_registered(rows, 2002)
    assert read_registered(rows, 2003) == (3.5, 2003, 2001), "the latest knowledge of data year to-2"
    assert read_registered(rows, 2001) == (1.0, 2000, 1999)
    assert read_registered(rows, 2000) is None, "data year 1999 known in 2000 is to-1"
    assert read_fit({("A", 2001): 5.0}, "A", 2002) == (5.0, 2001), "the fit year to-1"
    assert read_fit({("A", 2002): 5.0}, "A", 2002) is None
    assert read_income({1998: 7.0, 2000: 8.0, 2001: 9.0}, 2002) == (8.0, 2001), "data year to-2, known +1"
    assert read_income({1998: 7.0, 2001: 9.0}, 2002) == (7.0, 1999), "the latest at or before to-2"

    # the mask needs Total, HH and C at t, t-1 and t-2
    pres = {("A", t): {"Total", "HH", "C"} for t in range(2000, 2008)}
    pres[("A", 2003)] = {"Total", "C"}
    assert mask(pres, ["Total", "HH", "C"]) == [("A", 2002), ("A", 2006), ("A", 2007)], mask(pres, ["Total", "HH", "C"])

    # Holm over two
    assert holm([0.02, 0.04]) == [True, True]
    assert holm([0.03, 0.04]) == [False, False]
    assert holm([0.01, 0.30]) == [True, False]
    assert holm([0.30, 0.01]) == [False, True]

    # the verdict: both lower ends above 0 pass; else an upper end below the bound is nothing
    assert verdict({"B": 0.01, "P": 0.02}, {"B": 0.05, "P": 0.06}, 0.025) == "pass"
    assert verdict({"B": 0.01, "P": -0.01}, {"B": 0.05, "P": 0.06}, 0.025) == "fail"
    assert verdict({"B": -0.02, "P": -0.01}, {"B": 0.02, "P": 0.03}, 0.025) == "equivalent to nothing"

    # a planted effect passes, a null does not
    def go(beta, seed):
        d = synth(beta, np.random.default_rng(seed))
        perm = ap.derange(d["countries"], np.random.default_rng(20261024))
        xr = lambda iso, to: read_registered(d["ctl"].get(iso, []), to)
        return run(d, xr, perm, range(2003, 2015), 500, 20261024, parts)
    r = go(0.6, 1)
    assert r["verdict"] == "pass", r
    assert r["skill"]["B"] > 0.05 and r["skill"]["P"] > 0.05, r["skill"]
    assert r["beta"] > 0
    nulls = [go(0.0, s)["verdict"] for s in range(2, 8)]
    assert "pass" not in nulls, nulls

    # no row uses data known after its origin: every input's known year is
    # at most the row's origin, and the training targets (known +2) at most
    # the scoring origin
    d = synth(0.5, np.random.default_rng(3))
    perm = ap.derange(d["countries"], np.random.default_rng(20261024))
    xr = lambda iso, to: read_registered(d["ctl"].get(iso, []), to)
    m = mask(presence_of(d["values"]), ["Total", "HH", "C"])
    df = build(m, xr, lambda iso, to: read_income(d["inc"].get(iso, {}), to), perm)
    assert (df.origin == df.t + 1).all()
    assert (df.known <= df.origin).all(), "an input known after the origin"
    df = attach_outcome(df, d["values"], parts)
    test = forecast(df, range(2003, 2015), {"B": ["ylag", "inc"], "M": ["ylag", "inc", "x"], "P": ["ylag", "inc", "xp"]})
    assert (test.train_max_t <= test.origin - 2).all(), "a training target not yet published"
    assert (test.train_max_known <= test.origin).all()

    # and changing anything known after an origin leaves that origin's forecasts as they were
    to = 2008
    d2 = {k: v for k, v in d.items()}
    d2["values"] = {k: ({"Total": 1.0, "HH": 0.3, "C": v["C"] + 5.0} if k[1] >= to - 1 else v)
                    for k, v in d["values"].items()}
    d2["ctl"] = {c: [(y, dy, v + (9.0 if (y > to or dy > to - 2) else 0.0)) for y, dy, v in rs]
                 for c, rs in d["ctl"].items()}
    d2["inc"] = {c: {y: v + (3.0 if y > to - 2 else 0.0) for y, v in s.items()} for c, s in d["inc"].items()}

    def preds(dd):
        xr = lambda iso, to_: read_registered(dd["ctl"].get(iso, []), to_)
        df = build(m, xr, lambda iso, to_: read_income(dd["inc"].get(iso, {}), to_), perm)
        df = attach_outcome(df, dd["values"], parts)
        t = forecast(df, [to - 1], {"B": ["ylag", "inc"], "M": ["ylag", "inc", "x"], "P": ["ylag", "inc", "xp"]})
        return t[["c", "t", "f_B", "f_M", "f_P"]].reset_index(drop=True)
    p1, p2 = preds(d), preds(d2)
    assert len(p1) > 0 and p1.equals(p2), "a forecast moved with data known after its origin"

    # the pooled intercept for a country with fewer than five training rows
    df = pd.DataFrame({"c": ["A"] * 8 + ["B"] * 3 + ["B"], "t": list(range(2000, 2008)) + [2005, 2006, 2007, 2009],
                       "ylag": 0.0, "inc": 0.0, "y": [1.0] * 8 + [5.0] * 4})
    df["origin"] = df.t + 1
    df["known"] = df.origin
    t = forecast(df, [2009], {"B": ["ylag"]})
    pooled = np.mean([1.0] * 8 + [5.0] * 3)
    assert abs(t.f_B.iloc[0] - pooled) < 1e-9, (t.f_B.iloc[0], pooled)
    print("allocation-test selftest ok")


if __name__ == "__main__":
    import argparse
    ap_ = argparse.ArgumentParser()
    ap_.add_argument("--selftest", action="store_true")
    ap_.add_argument("--rows", metavar="OUTDIR")
    for k in ("primary", "S1", "S2"):
        ap_.add_argument(f"--bound-{k.lower()}", type=float, default=0.025)
    a = ap_.parse_args()
    if a.selftest:
        selftest()
    elif a.rows:
        write_rows(a.rows)
    else:
        report({"primary": a.bound_primary, "S1": a.bound_s1, "S2": a.bound_s2})
