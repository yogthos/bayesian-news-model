"""H1 and H2 on the product migration panel, as description and
attribution, not event tests (bead bnm-3ka.2; docs/ECONOMIC-CHARACTER.md
8e). Power is low: fin/* varies across few countries.

Unit: exporter i x HS92 product p x five-year window w (1995-2000, ...,
2015-2020). Outcome: the change in i's share of p's world exports over the
window, in percentage points, over the exporter-products with a positive
share at either end (scripts/migration.py, data/derived/econ/migration-hs6.csv).

  dshare_ipw = b (z_iw x k_p) + c (log income_iw x k_p) + a_iw + g_pw + e

with exporter-window and product-window fixed effects (the main effects of
z and k are absorbed), k_p the product's capital intensity (1995 EXIOBASE
CFC/VA, standardised over products), and standard errors clustered by
exporter, by product, and two-way (Cameron, Gelbach & Miller 2011). Every
regressor is what was known at the window's start year t0:

  H1 (prediction b < 0), financialisation placeholders until bnm-3ka.5:
    fin-va    the spine's finance share of value added (data/derived/io/
              spine-sectors.csv), data year <= t0 - 2. Sources change at
              2000 (LR-WIOD -> WIOD16, the US level ratio 1.63) and 2015
              (ICIO); the exporter-window effects absorb levels, the
              interaction does not, so the windows are also run apart.
    mcap      WDI market capitalisation / GDP, data year <= t0 - 1.
    credit    the credit gap, data/derived/stocks/credit.csv (credit/gap,
              already dated by the year known), at t0.
  H2 (prediction b > 0):
    inv       PWT 11.0 investment share csh_i, the mean of the five data
              years to t0 - 3 (PWT's release lag).
  log income: PWT real GDP per person, data year t0 - 3.

Each regressor is standardised over the country-windows in the sample.
Placebo (state-diag's rule): each country gets the regressor of the
country nearest it in log income in the same window. Controls: Germany
and Japan are in every regression; their own slopes on k (the
exporter's share change regressed on k within product-window, pooled
windows, product-clustered) are printed next to the US, UK, China, Korea
and Taiwan, and H1 is rerun without them.

Writes data/derived/econ/migration-h12.txt. Run with
python -I scripts/migration-h12.py

Result (2026-10-22; 2.92M exporter-product-window rows, 4,991 products,
231 exporters). b per SD of the proxy per SD of k, in pp of world share
over five years, two-way clustered SE:
  H1 fin-va  +0.0094 (0.0073), 44 exporters; no DEU/JPN +0.0067 (0.0068);
             placebo +0.0068 (0.0046); positive in every window
     mcap    +0.0074 (0.0027), 97 exporters; placebo +0.0003 (0.0035)
     credit  +0.0022 (0.0032), 149 exporters
  H2 inv     +0.0056 (0.0057), 185 exporters; placebo +0.0015 (0.0038)
H1 is not supported: no proxy has the predicted sign, and market
capitalisation's positive coefficient goes the wrong way. H2 has the
predicted sign but is not distinguishable from zero. The controls lose
too: own slopes on k are Germany -0.17 (0.03), Japan -0.08 (0.02), the US
-0.07 (0.04), the UK -0.14 (0.02); China +0.19 (0.05), Korea +0.12
(0.02), Taiwan +0.13 (0.02). The migration out of capital-intensive goods
is a rich-country one, not a financialised-country one, on these proxies.
The proxies are placeholders (bnm-3ka.5 builds fin/*).
"""
import io
import os
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda *p: os.path.join(ROOT, 'data', *p)
WINDOWS = [1995, 2000, 2005, 2010, 2015]
SPAN = 5
SPINE_LAG, WDI_LAG, PWT_LAG = 2, 1, 3
FOCUS = ['USA', 'GBR', 'DEU', 'JPN', 'FRA', 'ITA', 'CHE', 'CHN', 'KOR', 'TWN']
out_lines = []


def say(*a):
    s = ' '.join(str(x) for x in a)
    out_lines.append(s)
    print(s, flush=True)


# ---------------------------------------------------------------- regressors

def latest(df, iso_year_val, t0, lag):
    """{iso: value} of the latest data year <= t0 - lag."""
    d = df[df.year <= t0 - lag].sort_values('year')
    return d.groupby('iso')[iso_year_val].last()


def regressors():
    sp = pd.read_csv(D('derived', 'io', 'spine-sectors.csv'))
    tot = sp.groupby(['country', 'year']).va.sum()
    fin = sp[sp.sector == 'FIN'].set_index(['country', 'year']).va
    fin_va = (fin / tot).rename('v').reset_index().rename(columns={'country': 'iso'})

    z = zipfile.ZipFile(D('raw', 'econ', 'wdi-finance-structure', 'CM.MKT.LCAP.GD.ZS.zip'))
    name = [n for n in z.namelist() if n.startswith('API_')][0]
    w = pd.read_csv(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'), skiprows=4)
    w = w.melt(id_vars=['Country Code'], value_vars=[c for c in w.columns if c.isdigit()], var_name='year', value_name='v')
    mcap = w.dropna().rename(columns={'Country Code': 'iso'}).assign(year=lambda d: d.year.astype(int))

    cr = pd.read_csv(D('derived', 'stocks', 'credit.csv'))
    cr = cr[cr.variable == 'credit/gap'][['iso', 'year', 'value']].rename(columns={'value': 'v'})

    pwt = pd.read_csv(D('raw', 'slow', 'pwt', 'pwt110.csv')).rename(columns={'countrycode': 'iso'})
    inc = pwt.assign(v=np.log(pwt['rgdpna'] / pwt['pop']))[['iso', 'year', 'v']].dropna()
    inv = pwt[['iso', 'year', 'csh_i']].rename(columns={'csh_i': 'v'}).dropna()

    rows = []
    for t0 in WINDOWS:
        r = pd.DataFrame({
            'fin-va': latest(fin_va, 'v', t0, SPINE_LAG),
            'mcap': latest(mcap, 'v', t0, WDI_LAG),
            'credit': cr[cr.year == t0].set_index('iso').v,
            'inv': inv[(inv.year <= t0 - PWT_LAG) & (inv.year > t0 - PWT_LAG - 5)].groupby('iso').v.mean(),
            'income': latest(inc, 'v', t0, PWT_LAG)})
        r['t0'] = t0
        rows.append(r.rename_axis('exporter').reset_index())
    return pd.concat(rows)


# ---------------------------------------------------------------- the panel

def panel():
    hs = pd.read_csv(D('derived', 'econ', 'migration-hs6.csv'),
                     usecols=['hs6', 'year', 'exporter', 'share', 'cap_intensity', 'cap_class'],
                     dtype={'hs6': str, 'year': 'int16', 'exporter': str, 'share': 'float64',
                            'cap_intensity': 'float64', 'cap_class': str})
    hs = hs[hs.year.isin(set(WINDOWS) | {t + SPAN for t in WINDOWS})]
    hs = hs[hs.cap_intensity.notna()]
    parts = []
    for t0 in WINDOWS:
        a = hs[hs.year == t0].set_index(['hs6', 'exporter'])
        b = hs[hs.year == t0 + SPAN].set_index(['hs6', 'exporter'])
        prods = a.index.get_level_values(0).unique().intersection(b.index.get_level_values(0).unique())
        j = a[['share', 'cap_intensity', 'cap_class']].join(b[['share']], how='outer', lsuffix='0', rsuffix='1')
        j = j[j.index.get_level_values(0).isin(prods)].reset_index()
        k = pd.concat([a.cap_intensity, b.cap_intensity]).groupby(level=0).first()
        j['cap_intensity'] = j.hs6.map(k)  # the 1995 intensity, the same both ends unless the 2017 rows differ
        j['dshare'] = 100.0 * (j.share1.fillna(0.0) - j.share0.fillna(0.0))
        j['t0'] = t0
        parts.append(j[['hs6', 'exporter', 't0', 'dshare', 'cap_intensity']])
    p = pd.concat(parts, ignore_index=True)
    p['k'] = (p.cap_intensity - p.drop_duplicates('hs6').cap_intensity.mean()) / p.drop_duplicates('hs6').cap_intensity.std()
    return p


# ---------------------------------------------------------------- estimation

def codes(*cols):
    return pd.MultiIndex.from_arrays(cols).factorize()[0] if len(cols) > 1 else pd.factorize(cols[0])[0]


def demean(M, groups, tol=1e-10, maxit=500):
    """M (n x k) with every group-set's means taken out, by alternating
    projections."""
    M = M.copy()
    for _ in range(maxit):
        delta = 0.0
        for g in groups:
            n = np.bincount(g)
            for j in range(M.shape[1]):
                m = np.bincount(g, weights=M[:, j]) / n
                M[:, j] -= m[g]
                delta = max(delta, np.abs(m).max())
        if delta < tol:
            break
    return M


def cluster_v(X, e, g, bread):
    G = g.max() + 1
    S = np.zeros((G, X.shape[1]))
    np.add.at(S, g, X * e[:, None])
    return (G / (G - 1)) * bread @ (S.T @ S) @ bread, G


def fit(df, xcols, ycol='dshare'):
    g_iw = codes(df.exporter.values, df.t0.values)
    g_pw = codes(df.hs6.values, df.t0.values)
    M = demean(df[[ycol] + xcols].to_numpy(float), [g_iw, g_pw])
    y, X = M[:, 0], M[:, 1:]
    bread = np.linalg.inv(X.T @ X)
    b = bread @ X.T @ y
    e = y - X @ b
    gi, gp = codes(df.exporter.values), codes(df.hs6.values)
    vi, Gi = cluster_v(X, e, gi, bread)
    vp, Gp = cluster_v(X, e, gp, bread)
    vip, _ = cluster_v(X, e, codes(df.exporter.values, df.hs6.values), bread)
    v2 = vi + vp - vip
    se = lambda v: np.sqrt(np.clip(np.diag(v), 0, None))
    return {'b': b, 'se_i': se(vi), 'se_p': se(vp), 'se_2': se(v2), 'n': len(df), 'Gi': Gi, 'Gp': Gp}


def report(label, df, x, extra=('income',)):
    cols = [x] + [c for c in extra if c != x]
    d = df.dropna(subset=cols).copy()
    for c in cols:
        d[c + 'k'] = d[c] * d.k
    r = fit(d, [c + 'k' for c in cols])
    say(f'  {label:<34} b={r["b"][0]: .4f}  se(exp)={r["se_i"][0]:.4f}  se(prod)={r["se_p"][0]:.4f}  '
        f'se(2way)={r["se_2"][0]:.4f}  t2={r["b"][0] / r["se_2"][0]: .2f}  n={r["n"]}  exporters={r["Gi"]}  products={r["Gp"]}')
    return r


def standardise(reg, cols):
    reg = reg.copy()
    for c in cols:
        reg[c] = (reg[c] - reg[c].mean()) / reg[c].std()
    return reg


def placebo(reg, col):
    """Each country takes `col` from its nearest other country in income,
    same window."""
    out = []
    for t0, g in reg.dropna(subset=[col, 'income']).groupby('t0'):
        inc = g.income.to_numpy()
        vals = g[col].to_numpy()
        d = np.abs(inc[:, None] - inc[None, :])
        np.fill_diagonal(d, np.inf)
        g = g.copy()
        g[col] = vals[d.argmin(axis=1)]
        out.append(g)
    return pd.concat(out)


def own_slopes(p):
    """Each focus exporter's share change on k, product-window means out."""
    p = p.copy()
    p['dm'] = p.dshare - p.groupby(['hs6', 't0']).dshare.transform('mean')
    rows = []
    for iso in FOCUS:
        d = p[p.exporter == iso]
        if len(d) < 50:
            continue
        x = d.k.to_numpy() - d.k.mean()
        y = d.dm.to_numpy()
        b = (x @ y) / (x @ x)
        e = y - y.mean() - b * x
        g = codes(d.hs6.values)
        S = np.bincount(g, weights=x * e)
        G = g.max() + 1
        se = np.sqrt(G / (G - 1) * (S @ S)) / (x @ x)
        loss_hi = d[d.k > d.k.quantile(2 / 3)].dshare.mean()
        rows.append((iso, b, se, len(d), loss_hi))
    return rows


def main():
    p = panel()
    reg = regressors()
    raw = reg.copy()
    say('Product migration panel, H1 and H2 (bead bnm-3ka.2). Outcome: change in world export share (pp) over 5 years.')
    say(f'HS6 x exporter x window rows: {len(p)}; products {p.hs6.nunique()}; exporters {p.exporter.nunique()}; windows {WINDOWS}')
    say('k: 1995 EXIOBASE CFC/VA of the HS6 code, standardised over products.\n')

    say('Regressor levels at window start (raw):')
    show = raw[raw.exporter.isin(FOCUS)].pivot_table(index='exporter', columns='t0', values=['fin-va', 'mcap', 'credit', 'inv'])
    say(show.round(3).to_string())
    say('')

    say('Own slopes on k (pooled windows, product-window means out, product-clustered): b, se, rows, mean dshare in the top-third k')
    for iso, b, se, n, lh in own_slopes(p):
        say(f'  {iso}  b={b: .4f}  se={se:.4f}  t={b / se: .2f}  n={n}  top-k mean dshare={lh: .4f}')
    say('')

    cols = ['fin-va', 'mcap', 'credit', 'inv', 'income']
    zreg = standardise(reg, cols)
    d = p.merge(zreg, on=['exporter', 't0'], how='inner')

    say('H1, prediction b < 0 (fin proxy x k, with log income x k; exporter-window and product-window FE):')
    for x in ['fin-va', 'mcap', 'credit']:
        report(x, d, x)
    say('  without Germany and Japan:')
    dd = d[~d.exporter.isin(['DEU', 'JPN'])]
    for x in ['fin-va', 'mcap', 'credit']:
        report(x + ' (no DEU, JPN)', dd, x)
    say('  fin-va by window (sources change at 2000 and 2015):')
    for t0 in WINDOWS:
        report(f'fin-va {t0}-{t0 + SPAN}', d[d.t0 == t0], 'fin-va')
    say('  placebo (regressor of the nearest country in income):')
    for x in ['fin-va', 'mcap', 'credit']:
        pl = standardise(placebo(raw, x), cols)
        report(x + ' placebo', p.merge(pl, on=['exporter', 't0'], how='inner'), x)
    say('')

    say('H2, prediction b > 0 (investment share x k):')
    report('inv', d, 'inv')
    report('inv (no DEU, JPN)', dd, 'inv')
    pl = standardise(placebo(raw, 'inv'), cols)
    report('inv placebo', p.merge(pl, on=['exporter', 't0'], how='inner'), 'inv')
    say('  inv, without the income interaction:')
    report('inv alone', d, 'inv', extra=())

    with open(D('derived', 'econ', 'migration-h12.txt'), 'w') as f:
        f.write('\n'.join(out_lines) + '\n')


if __name__ == '__main__':
    main()
