"""The capacity test (bead bnm-4hv.11), as registered in docs/COUPLED-
SYSTEMS.md 9e (c1d2996): does investment already made, completing through
the time to build, predict next year's capacity growth better than
persistence, the growth's mean and the same completion rate from another
country?

Inputs:
  data/derived/coupled/allocation.csv   the engine's rows (bnm.allocation-run):
      investment/klems/completion-rate-<industry> (primary) and
      investment/financed-completion-rate-<industry> (secondary S1)
  data/raw/material/unsd-energy/by-commodity/UNSD_ENERGY_EC.csv (transaction 133)
  data/raw/material/unsd-energy/by-commodity/UNSD_ENERGY_4100.csv (transaction 131)
  data/derived/supply/stages.csv (BGS rows: smelters and refineries C24-C25, mines B)
Outputs:
  data/derived/capacity/test.txt        the result
  data/derived/capacity/test-rows.csv   the scored rows and the forecasts

Usage: python -I scripts/capacity-test.py
       python -I scripts/capacity-test.py --selftest
"""
import csv
import os
import sys

import numpy as np
import pandas as pd
import pycountry

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COUPLED = os.path.join(ROOT, "data", "derived", "coupled", "allocation.csv")
UNSD = os.path.join(ROOT, "data", "raw", "material", "unsd-energy", "by-commodity")
STAGES = os.path.join(ROOT, "data", "derived", "supply", "stages.csv")
OUT = os.path.join(ROOT, "data", "derived", "capacity")

SEED = 20261028
DRAWS = 2000
MIN_ROWS = 20
DESIGN_END = 2002
CLIP = 1.0
PRIMARY = "investment/klems/completion-rate-"
SECONDARY = "investment/financed-completion-rate-"
STAGE_INDUSTRY = {"smelter": "C24-C25", "refinery": "C24-C25", "mine": "B"}


# -- targets ----------------------------------------------------------------------------------

UNSD_AREAS = {251: "FRA", 382: "ITA", 757: "CHE", 579: "NOR", 412: "XKX"}


def m49_iso3():
    """M49 to ISO3, with UNSD's own codes for areas that include a
    neighbour (France with Monaco, Italy with San Marino, ...)."""
    return {int(c.numeric): c.alpha_3 for c in pycountry.countries} | UNSD_AREAS


def unsd_series(path, transaction, family, industry):
    """[iso, family, node, industry, year, value] of one UNSD capacity series."""
    d = pd.read_csv(path, dtype={"REF_AREA": str, "TRANSACTION": str})
    d = d[d.TRANSACTION == transaction]
    iso = m49_iso3()
    out = []
    for r in d.itertuples(index=False):
        c = iso.get(int(r.REF_AREA)) if str(r.REF_AREA).isdigit() else None
        v = pd.to_numeric(r.OBS_VALUE, errors="coerce")
        if c is None or pd.isna(v):
            continue
        mult = 0 if pd.isna(r.UNIT_MULT) else int(r.UNIT_MULT)
        out.append([c, family, f"{family}/{c}", industry, int(r.TIME_PERIOD), float(v) * 10 ** mult])
    return out


def stage_series(path):
    """The stage table's BGS rows of the stages with an industry, the last
    edition of each data year."""
    d = pd.read_csv(path)
    d = d[(d.source == "BGS") & d.stage.isin(STAGE_INDUSTRY)]
    d = d.sort_values("known").groupby(["commodity", "stage", "iso", "year"], as_index=False).last()
    return [[r.iso, "stages", f"stages/{r.commodity}-{r.stage}/{r.iso}", STAGE_INDUSTRY[r.stage], int(r.year), float(r.quantity)]
            for r in d.itertuples(index=False)]


def growth(levels):
    """Rows [iso family node industry year g] from [... year value]: the log
    change from the year before, both positive, clipped to [-CLIP, CLIP]."""
    by = {}
    for iso, fam, node, ind, y, v in levels:
        by[(node, y)] = (iso, fam, ind, v)
    out = []
    for (node, y), (iso, fam, ind, v) in sorted(by.items()):
        prev = by.get((node, y - 1))
        if prev and prev[3] > 0 and v > 0:
            out.append([iso, fam, node, ind, y, float(np.clip(np.log(v / prev[3]), -CLIP, CLIP))])
    return out


# -- the predictor --------------------------------------------------------------------------------

def read_predictors(path, prefixes):
    """{prefix {(iso, industry, data_year) value}} from the coupled table."""
    out = {p: {} for p in prefixes}
    with open(path) as f:
        r = csv.reader(f)
        h = next(r)
        ix = {k: i for i, k in enumerate(h)}
        for row in r:
            v = row[ix["variable"]]
            for p in prefixes:
                if v.startswith(p):
                    out[p][(row[ix["iso"]], v[len(p):], int(row[ix["data_year"]]))] = float(row[ix["value"]])
    return out


def derange(countries, rng):
    """As scripts/allocation-power.py (e5ca51a)."""
    cs = np.array(sorted(set(countries)))
    assert len(cs) > 1, "a derangement needs two countries"
    while True:
        p = rng.permutation(cs)
        if not (p == cs).any():
            return dict(zip(cs.tolist(), p.tolist()))


def panel(g, x, pi):
    """Paired rows: g, g the year before, x and the placebo's x."""
    gi = {(r[2], r[4]): r[5] for r in g}
    rows = []
    for iso, fam, node, ind, y, v in g:
        prev = gi.get((node, y - 1))
        xv = x.get((iso, ind, y))
        partner = pi.get(iso)
        xp = x.get((partner, ind, y)) if partner else None
        if prev is not None and xv is not None and xp is not None:
            rows.append(dict(iso=iso, family=fam, node=node, industry=ind, y=y, g=v, g_prev=prev, x=xv, xp=xp))
    return pd.DataFrame(rows)


# -- the models ---------------------------------------------------------------------------------

def ols(x, y):
    """(a, b) of y = a + b x; b = 0 when x has no spread."""
    x = np.asarray(x, float)
    y = np.asarray(y, float)
    vx = ((x - x.mean()) ** 2).sum()
    b = 0.0 if vx == 0 else ((x - x.mean()) * (y - y.mean())).sum() / vx
    return y.mean() - b * x.mean(), b


def forecasts(df):
    """The scored rows (targets after DESIGN_END) with the four forecasts,
    fitted at each origin on the rows whose target year is at most y - 1."""
    out = []
    for y in sorted(df.y.unique()):
        if y <= DESIGN_END:
            continue
        test = df[df.y == y]
        train = df[df.y <= y - 1]
        for fam, t in test.groupby("family"):
            tr = train[train.family == fam]
            if len(tr) < MIN_ROWS:
                tr = train
                if len(tr) < MIN_ROWS:
                    continue
            a, b = ols(tr.x, tr.g)
            ap, bp = ols(tr.xp, tr.g)
            t = t.assign(B=t.g_prev, A=tr.g.mean(), M=a + b * t.x, P=ap + bp * t.xp, Z=0.0)
            out.append(t)
    return pd.concat(out, ignore_index=True) if out else pd.DataFrame()


def skill(f, base):
    return 1.0 - ((f.g - f.M) ** 2).sum() / ((f.g - f[base]) ** 2).sum()


def bootstrap(f, bases=("B", "A", "P")):
    """{base (skill, lo, hi, share of draws at most 0)} by resampling countries."""
    cs = sorted(f.iso.unique())
    sse = {k: f.assign(e=(f.g - f[k]) ** 2).groupby("iso").e.sum().reindex(cs).to_numpy() for k in ("M",) + tuple(bases)}
    rng = np.random.default_rng(SEED)
    idx = rng.integers(0, len(cs), size=(DRAWS, len(cs)))
    out = {}
    for b in bases:
        s = 1.0 - sse["M"][idx].sum(axis=1) / sse[b][idx].sum(axis=1)
        out[b] = (skill(f, b), float(np.quantile(s, 0.05)), float(np.quantile(s, 0.95)), float((s <= 0).mean()))
    return out


def clustered(df):
    """(b, se) of g = a + b x with country-clustered standard errors."""
    X = np.column_stack([np.ones(len(df)), df.x.to_numpy()])
    y = df.g.to_numpy()
    xtx = np.linalg.inv(X.T @ X)
    beta = xtx @ X.T @ y
    e = y - X @ beta
    meat = np.zeros((2, 2))
    for _, i in df.groupby("iso").indices.items():
        s = X[i].T @ e[i]
        meat += np.outer(s, s)
    g = df.iso.nunique()
    n = len(df)
    c = g / (g - 1) * (n - 1) / (n - 2) if g > 1 and n > 2 else 1.0
    v = c * xtx @ meat @ xtx
    return float(beta[1]), float(np.sqrt(v[1, 1]))


def fmt(t):
    s, lo, hi, p = t
    return f"{s:+.4f} [{lo:+.4f}, {hi:+.4f}] (p {p:.3f})"


def report(name, df, f, lines):
    bs = bootstrap(f)
    p_iu = max(v[3] for v in bs.values())
    passed = all(v[1] > 0 for v in bs.values())
    lines.append(f"== {name}")
    lines.append(f"paired rows {len(df)} ({df.iso.nunique()} countries); test rows {len(f)} in {f.iso.nunique()} countries, "
                 f"targets {int(f.y.min())}-{int(f.y.max())}")
    for b, label in (("B", "persistence"), ("A", "mean"), ("P", "placebo")):
        lines.append(f"  M vs {b} ({label}): {fmt(bs[b])}")
    eq = bs["A"][2] < 0.02
    verdict = "PASS" if passed else ("fail, equivalent to nothing (upper end vs A below +0.02)" if eq else "fail")
    lines.append(f"  IU p {p_iu:.3f}: {verdict}")
    lines.append("  for the record:")
    lines.append(f"    M vs zero growth: {skill(f, 'Z'):+.4f}")
    for fam, t in f.groupby("family"):
        bf, sef = clustered(df[df.family == fam])
        lines.append(f"    {fam}: test rows {len(t)}, M vs B {skill(t, 'B'):+.4f}, vs A {skill(t, 'A'):+.4f}, "
                     f"vs P {skill(t, 'P'):+.4f}; b {bf:+.4f} (clustered SE {sef:.4f}) on {int((df.family == fam).sum())} rows")
    for lo, hi in ((2003, 2007), (2008, 2014), (2015, 9999)):
        t = f[(f.y >= lo) & (f.y <= hi)]
        if len(t):
            e = bootstrap(t, ("A",))["A"]
            lines.append(f"    {lo}-{min(hi, int(f.y.max()))}: n {len(t)}, M vs B {skill(t, 'B'):+.4f}, M vs A {fmt(e)}, M vs P {skill(t, 'P'):+.4f}")
    return passed


def main():
    levels = (unsd_series(os.path.join(UNSD, "UNSD_ENERGY_EC.csv"), "133", "power", "D-E")
              + unsd_series(os.path.join(UNSD, "UNSD_ENERGY_4100.csv"), "131", "refining", "C19")
              + stage_series(STAGES))
    g = growth(levels)
    xs = read_predictors(COUPLED, [PRIMARY, SECONDARY])
    countries = sorted({k[0] for k in xs[PRIMARY]})
    pi = derange(countries, np.random.default_rng(SEED))
    lines = [f"capacity test (docs/COUPLED-SYSTEMS.md 9e); placebo map over {len(countries)} countries, seed {SEED}"]
    os.makedirs(OUT, exist_ok=True)
    frames = []
    for name, pre in (("primary: investment/klems/completion-rate", PRIMARY), ("S1: investment/financed-completion-rate", SECONDARY)):
        df = panel(g, xs[pre], pi)
        f = forecasts(df)
        report(name, df, f, lines)
        frames.append(f.assign(predictor=pre))
    pd.concat(frames).to_csv(os.path.join(OUT, "test-rows.csv"), index=False)
    txt = "\n".join(lines) + "\n"
    open(os.path.join(OUT, "test.txt"), "w").write(txt)
    print(txt, end="")


def selftest():
    assert m49_iso3()[251] == "FRA" and m49_iso3()[382] == "ITA" and m49_iso3()[840] == "USA"
    g = growth([["X", "f", "n", "C19", 2000, 100.0], ["X", "f", "n", "C19", 2001, 110.0],
                ["X", "f", "n", "C19", 2002, 1000.0], ["X", "f", "n", "C19", 2004, 5.0]])
    assert [r[4] for r in g] == [2001, 2002] and abs(g[0][5] - np.log(1.1)) < 1e-12 and g[1][5] == CLIP, g
    a, b = ols([0, 1, 2], [1, 3, 5])
    assert abs(a - 1) < 1e-12 and abs(b - 2) < 1e-12
    assert ols([1, 1], [2, 4]) == (3.0, 0.0)
    pi = derange(["A", "B", "C"], np.random.default_rng(SEED))
    assert all(k != v for k, v in pi.items()) and sorted(pi.values()) == ["A", "B", "C"]
    gg = [["A", "f", "n1", "C19", y, 0.1 * (y - 2000)] for y in range(2000, 2004)]
    p = panel(gg, {("A", "C19", y): 1.0 for y in range(2000, 2004)} | {("B", "C19", 2002): 2.0}, {"A": "B"})
    assert list(p.y) == [2002] and p.xp.iloc[0] == 2.0 and abs(p.g_prev.iloc[0] - 0.1) < 1e-12, p
    # the fit at origin y uses only targets up to y - 1, the family's or pooled
    rows = [dict(iso=f"C{i % 5}", family="f", node=f"n{i}", industry="C19", y=1990 + i // 5, g=0.5 * x, g_prev=0.0, x=x, xp=0.0)
            for i, x in enumerate(np.linspace(0, 1, 100))]
    f = forecasts(pd.DataFrame(rows))
    assert f.y.min() == 2003 and np.allclose(f.M, f.g), "an exact linear relation is forecast exactly"
    assert skill(f, "A") > 0.9
    print("capacity-test selftest ok")


if __name__ == "__main__":
    selftest() if sys.argv[1:] == ["--selftest"] else main()
