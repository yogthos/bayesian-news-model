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
nearest other country in log income in the same window (placebo_map:
never itself, ties to the alphabetically first). Controls: Germany and
Japan are in every regression; their own slopes on k are printed next to
the US, UK, China, Korea and Taiwan, and H1 is rerun without them.

Identification: the exporter-window effects absorb the proxy's main
effect (fit refuses a regressor they absorb), so b is identified only off
proxy x k: whether high-proxy exporters lose more in capital-intensive
products than in other products, relative to their own portfolio. That
is the design (8e). Tests: two-way clustered SEs, t against t(G - 1),
the wild cluster bootstrap by exporter, and the effective number of
exporter clusters with the one carrying most of the variance (G_eff
below about 5 means neither the clustered t nor the bootstrap holds).

Checks: k from six other measures (capital-intensity-alt.csv), the
symmetric growth and log change of the share, the core exporters (at
least 0.1% of world goods exports, HK and Singapore out), no China,
manufactures only, the export-vs-value-added shares (processing trade),
and econ/financialisation (bnm-3ka.5) once data/derived/stocks/
econ-factors.csv exists.

Writes data/derived/econ/migration-h12.txt and migration-h12.csv (one
row per regression). Run with python -I scripts/migration-h12.py;
python -I scripts/migration-h12.py --selftest runs the helper tests.

Results: docs/ECONOMIC-CHARACTER.md, "Results: product migration
(bnm-3ka.2)". In short: no H1 proxy has the predicted sign under any
measure of k; mcap's wrong-signed b rests on Hong Kong's re-exports and
goes in the core sample; the full-sample fits are carried by one or two
exporters (Brazil for fin-va, Bermuda for inv). In the core sample H2 is
positive and significant (wild p 0.01).
"""
import importlib.util
import io
import os
import sys
import zipfile

import numpy as np
import pandas as pd
from scipy import stats

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


AUDIT = []


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
        # the latest data year each regressor reads, and when it was known
        used = {'fin-va': (fin_va[fin_va.year <= t0 - SPINE_LAG].year.max(), SPINE_LAG),
                'mcap': (mcap[mcap.year <= t0 - WDI_LAG].year.max(), WDI_LAG),
                'credit': (cr[cr.year == t0].year.max(), 0),
                'inv': (inv[inv.year <= t0 - PWT_LAG].year.max(), PWT_LAG),
                'income': (inc[inc.year <= t0 - PWT_LAG].year.max(), PWT_LAG)}
        for name, (y, lag) in used.items():
            AUDIT.append({'t0': t0, 'regressor': name, 'data_year': int(y), 'known': int(y) + lag})
            assert int(y) + lag <= t0, (name, t0, y)
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

def load_migration():
    spec = importlib.util.spec_from_file_location('migration', os.path.join(ROOT, 'scripts', 'migration.py'))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


mig = load_migration()

# The capital-intensity measures compared (data/derived/econ/capital-intensity-alt.csv,
# scripts/migration.py): EXIOBASE 1995 unless marked KLEMS (EU KLEMS 2023, 29
# European countries, the US and Japan, earliest year <= 2000, 16 goods industries).
MEASURES = {'cfc_va_median': 'CFC/VA, median over 49 regions',
            'cfc_va_emerging': 'CFC/VA, emerging regions pooled',
            'capital_share': 'gross capital share of VA',
            'log_cfc_per_worker': 'log CFC per worker',
            'klems_k_per_worker': 'KLEMS log K per worker',
            'klems_cap_share': 'KLEMS capital compensation share'}


def panel():
    alt_df = pd.read_csv(D('derived', 'econ', 'capital-intensity-alt.csv'))
    alt = {m: alt_df.set_index('product')[m].dropna().to_dict() for m in MEASURES}
    conc = pd.read_csv(D('derived', 'supply', 'concordance.csv'), dtype={'hs_code': str, 'hs6': str})
    conc = conc[conc.hs_edition == 'HS92'][['hs_code', 'exiobase_product', 'weight', 'from_year', 'to_year']]
    conc = conc.rename(columns={'hs_code': 'hs6', 'exiobase_product': 'product'})
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
        o = outcomes(j.share0.fillna(0.0).to_numpy(), j.share1.fillna(0.0).to_numpy())
        for c, v in o.items():
            j[c] = v
        j['t0'] = t0
        j['share0'] = j.share0.fillna(0.0)
        for name, kmap in alt.items():
            j['k_' + name] = j.hs6.map(mig.hs6_intensity(mig.concordance_rows(conc, t0), kmap).cap_intensity)
        parts.append(j[['hs6', 'exporter', 't0', 'share0', 'dshare', 'arc', 'dlog', 'cap_intensity']
                       + ['k_' + n for n in alt]])
    p = pd.concat(parts, ignore_index=True)
    p['k'] = zscore_products(p, 'cap_intensity')
    for name in alt:
        p['k_' + name] = zscore_products(p, 'k_' + name)
    return p


def zscore_products(p, col):
    """`col` standardised over products (one value per HS6 code)."""
    u = p.drop_duplicates('hs6')[col]
    return (p[col] - u.mean()) / u.std()


def outcomes(s0, s1):
    """Share changes between window ends, for shares s0 and s1 (fractions):
    dshare, the change in percentage points (dominated by large exporters);
    arc, the symmetric growth rate 200 (s1 - s0) / (s1 + s0), bounded at
    +-200 for entry and exit; dlog, the log change where both are positive."""
    s0, s1 = np.asarray(s0, float), np.asarray(s1, float)
    tot = s0 + s1
    with np.errstate(divide='ignore', invalid='ignore'):
        arc = np.where(tot > 0, 200.0 * (s1 - s0) / tot, np.nan)
        dlog = np.where((s0 > 0) & (s1 > 0), np.log(s1) - np.log(s0), np.nan)
    return {'dshare': 100.0 * (s1 - s0), 'arc': arc, 'dlog': dlog}


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


def fit(df, xcols, ycol='dshare', boot=0, seed=0):
    """OLS of `ycol` on `xcols` with exporter-window and product-window
    fixed effects. SEs clustered by exporter, by product and two-way; with
    boot > 0, also the wild cluster bootstrap p-value for the first
    regressor (see wild_p)."""
    g_iw = codes(df.exporter.values, df.t0.values)
    g_pw = codes(df.hs6.values, df.t0.values)
    raw = df[[ycol] + xcols].to_numpy(float)
    M = demean(raw, [g_iw, g_pw])
    y, X = M[:, 0], M[:, 1:]
    absorbed = [c for c, a, b in zip(xcols, X.T, raw[:, 1:].T) if np.linalg.norm(a) <= 1e-8 * max(np.linalg.norm(b), 1.0)]
    if absorbed:
        raise ValueError(f'absorbed by the fixed effects, not identified: {absorbed}')
    bread = np.linalg.inv(X.T @ X)
    b = bread @ X.T @ y
    e = y - X @ b
    gi, gp = codes(df.exporter.values), codes(df.hs6.values)
    vi, Gi = cluster_v(X, e, gi, bread)
    vp, Gp = cluster_v(X, e, gp, bread)
    vip, _ = cluster_v(X, e, codes(df.exporter.values, df.hs6.values), bread)
    v2 = vi + vp - vip
    se = lambda v: np.sqrt(np.clip(np.diag(v), 0, None))
    r = {'b': b, 'se_i': se(vi), 'se_p': se(vp), 'se_2': se(v2), 'n': len(df), 'Gi': Gi, 'Gp': Gp}
    x1 = X[:, 0]
    if X.shape[1] > 1:
        x1 = x1 - X[:, 1:] @ np.linalg.lstsq(X[:, 1:], x1, rcond=None)[0]
    inf = cluster_influence(gi, x1, e)
    r.update(g_eff=inf['g_eff'], top_share=inf['top_share'], top=pd.unique(df.exporter.values)[inf['top']])
    # with few exporter clusters, t against t(G - 1) rather than the normal
    r['p_t'] = 2 * stats.t.sf(abs(b[0] / r['se_2'][0]), Gi - 1)
    if boot:
        r['p_wild'] = _wild(y, X, gi, boot, seed)
    return r


def cluster_influence(g, x, e):
    """How far one exporter carries the first coefficient's variance: each
    cluster's share of the sum of squared scores (x the regressor with the
    others partialled out, e the residuals), the top cluster and the
    effective number of clusters 1 / sum(share^2). With few effective
    clusters the clustered t is unreliable and so is the wild bootstrap
    (MacKinnon & Webb 2017)."""
    S2 = np.bincount(g, weights=x * e) ** 2
    sh = S2 / S2.sum()
    return {'top': int(sh.argmax()), 'top_share': float(sh.max()), 'g_eff': float(1.0 / (sh ** 2).sum())}


WEBB = np.array([-np.sqrt(1.5), -1.0, -np.sqrt(0.5), np.sqrt(0.5), 1.0, np.sqrt(1.5)])


def _wild(y, X, g, B, seed):
    x = X[:, 0]
    if X.shape[1] > 1:
        W = X[:, 1:]
        x = x - W @ np.linalg.lstsq(W, x, rcond=None)[0]
        y = y - W @ np.linalg.lstsq(W, y, rcond=None)[0]
    G = g.max() + 1
    xx = x @ x
    S = np.bincount(g, weights=x * y, minlength=G)
    Q = np.bincount(g, weights=x * x, minlength=G)

    def tstat(Sw):
        b = Sw.sum(axis=-1) / xx
        sc = Sw - b[..., None] * Q
        return b / (np.sqrt(G / (G - 1) * (sc ** 2).sum(axis=-1)) / xx)

    t = tstat(S)
    w = WEBB[np.random.default_rng(seed).integers(0, 6, size=(B, G))]
    return (1 + np.sum(np.abs(tstat(w * S)) >= abs(t))) / (B + 1)


def wild_p(df, xcols, ycol='dshare', B=999, seed=0):
    """p-value for the first of `xcols` = 0, by the restricted wild cluster
    bootstrap over exporters (Webb six-point weights; Cameron, Gelbach &
    Miller 2008; Roodman et al. 2019), with the t-statistic on one-way
    exporter-clustered SEs. The fixed effects and the other regressors are
    partialled out first (FWL); the bootstrap draws are not re-demeaned,
    which is exact for the coefficient and close for the SE."""
    return fit(df, xcols, ycol, boot=B, seed=seed)['p_wild']


TABLE = []


def report(label, df, x, extra=('income',), kcol='k', ycol='dshare', boot=999):
    """b on proxy x k (with each of `extra` x k) and its tests; one line."""
    cols = [x] + [c for c in extra if c != x]
    d = df.dropna(subset=cols + [kcol, ycol]).copy()
    for c in cols:
        d[c + 'k'] = d[c] * d[kcol]
    r = fit(d, [c + 'k' for c in cols], ycol, boot=boot)
    say(f'  {label:<40} b={r["b"][0]: .4f}  se(exp)={r["se_i"][0]:.4f}  se(prod)={r["se_p"][0]:.4f}  '
        f'se(2way)={r["se_2"][0]:.4f}  t2={r["b"][0] / r["se_2"][0]: .2f}  p(t,G-1)={r["p_t"]:.3f}  '
        f'p(wild)={r.get("p_wild", np.nan):.3f}  n={r["n"]}  exporters={r["Gi"]}  products={r["Gp"]}  '
        f'top={r["top"]} {100 * r["top_share"]:.0f}%  G_eff={r["g_eff"]:.1f}')
    TABLE.append({'label': label, 'proxy': x, 'k': kcol, 'y': ycol, 'b': r['b'][0], 'se2': r['se_2'][0],
                  'p_t': r['p_t'], 'p_wild': r.get('p_wild', np.nan), 'exporters': r['Gi'], 'n': r['n'],
                  'top': r['top'], 'top_share': r['top_share'], 'g_eff': r['g_eff']})
    return r


def standardise(reg, cols):
    reg = reg.copy()
    for c in cols:
        reg[c] = (reg[c] - reg[c].mean()) / reg[c].std()
    return reg


def placebo_map(reg, col):
    """t0, exporter, donor: within each window, each country with `col`
    and income known is matched to the nearest other such country in log
    income (ties to the alphabetically first). Windows with one country
    are dropped: there is no other to draw from."""
    out = []
    for t0, g in reg.dropna(subset=[col, 'income']).sort_values('exporter').groupby('t0'):
        if len(g) < 2:
            continue
        inc = g.income.to_numpy()
        d = np.abs(inc[:, None] - inc[None, :])
        np.fill_diagonal(d, np.inf)
        out.append(pd.DataFrame({'t0': t0, 'exporter': g.exporter.to_numpy(),
                                 'donor': g.exporter.to_numpy()[d.argmin(axis=1)]}))
    m = pd.concat(out, ignore_index=True)
    assert (m.exporter != m.donor).all()
    return m


def placebo(reg, col):
    """`reg` with each country's `col` replaced by its placebo donor's."""
    m = placebo_map(reg, col)
    vals = reg.set_index(['exporter', 't0'])[col]
    g = reg.merge(m, on=['t0', 'exporter'])
    g[col] = vals.reindex(pd.MultiIndex.from_arrays([g.donor, g.t0])).to_numpy()
    return g.drop(columns='donor')


def placebo_stats(reg, col):
    """How the placebo mapping behaves: mutual pairs, stability across
    windows and the correlation of the donor's value with the own one."""
    m = placebo_map(reg, col)
    pair = m.merge(m, left_on=['t0', 'donor'], right_on=['t0', 'exporter'], suffixes=('', '_d'))
    mutual = (pair.donor_d == pair.exporter).mean()
    w = m.pivot(index='exporter', columns='t0', values='donor')
    cols = list(w.columns)
    same = np.nanmean([(w[a] == w[b])[w[a].notna() & w[b].notna()].mean() for a, b in zip(cols, cols[1:])])
    v = reg.set_index(['exporter', 't0'])[col]
    own = v.reindex(pd.MultiIndex.from_arrays([m.exporter, m.t0])).to_numpy()
    don = v.reindex(pd.MultiIndex.from_arrays([m.donor, m.t0])).to_numpy()
    return {'countries': len(m), 'mutual': mutual, 'stable': same, 'corr': np.corrcoef(own, don)[0, 1]}


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


def processing_check():
    """Gross export share against EXIOBASE production value-added share,
    for the focus countries in the high-k class and in electronics
    (computers C_OFMA, radio-TV-telecom C_RATV): does the export share
    overstate the migration of capital-intensive production? EXIOBASE
    3.8 years after 2011 are nowcasts."""
    ex = pd.read_csv(D('derived', 'econ', 'migration.csv'), usecols=['product', 'year', 'exporter', 'value', 'cap_class'])
    rt = pd.read_csv(D('derived', 'econ', 'capital-intensity-region.csv'))
    cls = ex.drop_duplicates('product').set_index('product').cap_class
    rt['cap_class'] = rt['product'].map(cls)
    region = {'USA': 'US', 'GBR': 'GB', 'DEU': 'DE', 'JPN': 'JP', 'CHN': 'CN', 'KOR': 'KR', 'TWN': 'TW'}
    years = [y for y in mig.EXIO_YEARS if y in set(ex.year)]
    groups = {'high k': lambda d: d.cap_class == 'high', 'electronics': lambda d: d['product'].isin(['C_OFMA', 'C_RATV'])}
    rows = []
    for gname, sel in groups.items():
        e = ex[sel(ex)]
        r = rt[sel(rt) & (rt.va > 0)]
        for y in years:
            et = e[e.year == y].groupby('exporter').value.sum()
            rv = r[r.year == y].groupby('region').va.sum()
            for iso, reg in region.items():
                rows.append({'group': gname, 'year': y, 'iso': iso,
                             'export': 100 * et.get(iso, 0.0) / et.sum(), 'va': 100 * rv.get(reg, 0.0) / rv.sum()})
    t = pd.DataFrame(rows)
    say('Processing trade: world share (%) of gross exports vs of production value added (EXIOBASE), by year')
    for gname in groups:
        w = t[t.group == gname].pivot_table(index='iso', columns='year', values=['export', 'va'])
        say(f'  {gname}:')
        say(w.round(1).to_string())
        first, last = years[0], years[-1]
        d = (w[('export', last)] - w[('export', first)]).rename('d export') .to_frame()
        d['d va'] = w[('va', last)] - w[('va', first)]
        say(f'  change {first}-{last} (pp):')
        say(d.round(1).to_string())
    say('')


ENTREPOTS = ['HKG', 'SGP']


def core_exporters(ex, min_share=0.001):
    """t0, exporter: the exporters holding at least `min_share` of world
    goods exports in the window's start year, entrepots (re-exporters)
    out. Small exporters' share changes in thin products and Hong Kong's
    re-exports otherwise carry much of the clustered variance."""
    tot = ex.groupby(['year', 'exporter']).value.sum()
    sh = (tot / tot.groupby(level=0).transform('sum')).rename('s').reset_index()
    sh = sh[(sh.s >= min_share) & ~sh.exporter.isin(ENTREPOTS)]
    return sh.rename(columns={'year': 't0'})[['t0', 'exporter']]


def econ_factor():
    """{(iso, t0): econ/financialisation} from data/derived/stocks/econ-factors.csv
    (bnm-3ka.5; dated by the year known), or None if it is not built."""
    path = D('derived', 'stocks', 'econ-factors.csv')
    if not os.path.exists(path):
        return None
    f = pd.read_csv(path)
    f = f[f.variable == 'econ/financialisation']
    if f.empty:
        return None
    rows = []
    for t0 in WINDOWS:
        v = f[f.year <= t0].sort_values('year').groupby('iso').last()
        v = v[v.year >= t0 - 2]
        rows.append(pd.DataFrame({'exporter': v.index, 't0': t0, 'fin': v.value.to_numpy()}))
    return pd.concat(rows)


def main():
    p = panel()
    reg = regressors()
    raw = reg.copy()
    say('Product migration panel, H1 and H2 (bead bnm-3ka.2). Outcome: change in world export share (pp) over 5 years.')
    say(f'HS6 x exporter x window rows: {len(p)}; products {p.hs6.nunique()}; exporters {p.exporter.nunique()}; windows {WINDOWS}')
    say('k: 1995 EXIOBASE CFC/VA of the HS6 code, standardised over products.')
    say('Tests: se(2way) two-way clustered (CGM); p(t,G-1) the two-way t against t with exporters - 1 df;')
    say('p(wild) the restricted wild cluster bootstrap over exporters (Webb weights, 999 draws).\n')

    say('Known years: the latest data year each regressor reads at window start t0, and when it was known (<= t0):')
    au = pd.DataFrame(AUDIT)
    say(au.pivot(index='regressor', columns='t0', values='known').to_string())
    say('k (EXIOBASE 1995 table) is from the v3.8.2 vintage, known 2021: a fixed product attribute, not a value held at t0.\n')

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
    proxies = ['fin-va', 'mcap', 'credit']

    say('H1, prediction b < 0 (fin proxy x k, with log income x k; exporter-window and product-window FE):')
    for x in proxies:
        report(x, d, x)
    say('  without Germany and Japan:')
    dd = d[~d.exporter.isin(['DEU', 'JPN'])]
    for x in proxies:
        report(x + ' (no DEU, JPN)', dd, x)
    say('  fin-va by window (sources change at 2000 and 2015):')
    for t0 in WINDOWS:
        report(f'fin-va {t0}-{t0 + SPAN}', d[d.t0 == t0], 'fin-va')
    say('  placebo (regressor of the nearest other country in income, same window):')
    for x in proxies + ['inv']:
        st = placebo_stats(raw, x)
        say(f'    {x}: {st["countries"]} country-windows, mutual pairs {st["mutual"]:.2f}, '
            f'donor unchanged from the previous window {st["stable"]:.2f}, corr(own, donor) {st["corr"]:.2f}')
    for x in proxies:
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
    say('')

    say('Sensitivity to the capital-intensity measure (each standardised over products; b per SD x SD):')
    for m, desc in MEASURES.items():
        say(f'  k = {m} ({desc}):')
        for x in proxies + ['inv']:
            report(f'{x} | {m}', d, x, kcol='k_' + m)
    say('')

    say('Outcome and sample robustness (k = cfc_va):')
    for ycol, desc in [('arc', 'symmetric growth of the share, %'), ('dlog', 'log change, both ends positive')]:
        say(f'  y = {ycol} ({desc}):')
        for x in proxies + ['inv']:
            report(f'{x} | y={ycol}', d, x, ycol=ycol)
    say('  without China:')
    for x in proxies + ['inv']:
        report(f'{x} | no CHN', d[d.exporter != 'CHN'], x)
    ex = pd.read_csv(D('derived', 'econ', 'migration.csv'), usecols=['year', 'exporter', 'value'])
    core = core_exporters(ex[ex.year.isin(WINDOWS)])
    dc = d.merge(core, on=['t0', 'exporter'])
    say(f'  core exporters (>= 0.1% of world goods exports at t0, HKG and SGP out; {dc.exporter.nunique()} exporters):')
    for x in proxies + ['inv']:
        report(f'{x} | core', dc, x)
    for x in proxies + ['inv']:
        report(f'{x} | core, y=arc', dc, x, ycol='arc')
    say('  manufactures only (HS chapters 28-96, fuels and farm goods out):')
    man = d[d.hs6.str[:2].astype(int).between(28, 96)]
    for x in proxies + ['inv']:
        report(f'{x} | manufactures', man, x)
    say('')

    processing_check()

    ef = econ_factor()
    if ef is None:
        say('econ/financialisation (bnm-3ka.5): data/derived/stocks/econ-factors.csv not built; H1 not rerun on it.')
    else:
        say('H1 on econ/financialisation (bnm-3ka.5, the latest value known by t0, at most 2 years old):')
        r2 = reg.merge(ef, on=['exporter', 't0'], how='left')
        z2 = standardise(r2, cols + ['fin'])
        d2 = p.merge(z2, on=['exporter', 't0'], how='inner')
        report('econ/financialisation', d2, 'fin')
        report('econ/financialisation (no DEU, JPN)', d2[~d2.exporter.isin(['DEU', 'JPN'])], 'fin')
        report('econ/financialisation | y=arc', d2, 'fin', ycol='arc')
        report('econ/financialisation | core', d2.merge(core, on=['t0', 'exporter']), 'fin')
        report('econ/financialisation | core, y=arc', d2.merge(core, on=['t0', 'exporter']), 'fin', ycol='arc')
        r2r = r2.dropna(subset=['fin', 'income'])
        say(f'    corr(econ/financialisation, log income) over country-windows: {r2r.fin.corr(r2r.income):.2f}')
        st = placebo_stats(r2, 'fin')
        say(f'    placebo: {st["countries"]} country-windows, corr(own, donor) {st["corr"]:.2f}')
        pl = standardise(placebo(r2, 'fin'), cols + ['fin'])
        report('econ/financialisation placebo', p.merge(pl, on=['exporter', 't0'], how='inner'), 'fin')

    pd.DataFrame(TABLE).to_csv(D('derived', 'econ', 'migration-h12.csv'), index=False, float_format='%.6g')
    with open(D('derived', 'econ', 'migration-h12.txt'), 'w') as f:
        f.write('\n'.join(out_lines) + '\n')


# ---------------------------------------------------------------- self-test

def selftest():
    """python -I scripts/migration-h12.py --selftest"""
    rng = np.random.default_rng(7)
    # outcomes: pp change, symmetric (arc) growth bounded in [-200, 200], log change on both-positive
    o = outcomes(np.array([0.1, 0.0, 0.2, 0.3]), np.array([0.2, 0.1, 0.0, 0.3]))
    assert np.allclose(o['dshare'], [10.0, 10.0, -20.0, 0.0])
    assert np.allclose(o['arc'], [200 * 0.1 / 0.3, 200.0, -200.0, 0.0])
    assert np.allclose(o['dlog'][[0, 3]], [np.log(2.0), 0.0]) and np.isnan(o['dlog'][1]) and np.isnan(o['dlog'][2])
    # fit recovers the interaction under exporter-window and product-window effects
    n_i, n_p = 40, 150
    rows = []
    for t0 in (1995, 2000):
        z = rng.normal(size=n_i)
        k = rng.normal(size=n_p)
        a = rng.normal(size=n_i)
        g = rng.normal(size=n_p)
        I, P = np.meshgrid(np.arange(n_i), np.arange(n_p), indexing='ij')
        I, P = I.ravel(), P.ravel()
        rows.append(pd.DataFrame({'exporter': [f'C{i}' for i in I], 'hs6': [f'{p:06d}' for p in P], 't0': t0,
                                  'z': z[I], 'k': k[P], 'u': rng.normal(size=I.size),
                                  'a': a[I], 'g': g[P], 'cl': rng.normal(size=n_i)[I]}))
    df = pd.concat(rows, ignore_index=True)
    df['zk'] = df.z * df.k
    df['y1'] = df.a + df.g + 3 * df.z + 0.5 * df.zk + df.u + df.cl
    df['y0'] = df.a + df.g + df.u + df.cl
    r = fit(df, ['zk'], 'y1')
    assert abs(r['b'][0] - 0.5) < 0.1, r['b']
    assert r['se_2'][0] > 0 and r['Gi'] == n_i and r['Gp'] == n_p
    # the proxy's main effect is absorbed by the exporter-window effects: refuse it
    try:
        fit(df, ['z'], 'y1')
        raise AssertionError('absorbed regressor accepted')
    except ValueError:
        pass
    # wild cluster bootstrap by exporter: rejects a real effect, not a null one
    assert wild_p(df, ['zk'], 'y1', B=199, seed=1) < 0.05
    assert wild_p(df, ['zk'], 'y0', B=199, seed=1) > 0.05
    # cluster dominance: the exporter-score shares and their effective number
    inf = cluster_influence(np.array([0, 0, 1, 2, 3]), np.array([1.0, 1.0, 1.0, 1.0, 1.0]), np.array([5.0, 5.0, 1.0, -1.0, 1.0]))
    assert inf['top'] == 0 and abs(inf['top_share'] - 100 / 103) < 1e-12
    assert abs(inf['g_eff'] - 1 / ((100 / 103) ** 2 + 3 * (1 / 103) ** 2)) < 1e-12
    r1 = fit(df, ['zk'], 'y1')
    assert 10 < r1['g_eff'] <= n_i
    # core sample: exporters with at least min_share of world goods exports at t0, entrepots out
    ex = pd.DataFrame({'year': [1995] * 4 + [2000] * 2, 'exporter': ['A', 'B', 'HKG', 'D', 'A', 'B'],
                       'value': [50.0, 49.95, 30.0, 0.05, 1.0, 1.0]})
    core = core_exporters(ex, 0.001)
    assert set(core[core.t0 == 1995].exporter) == {'A', 'B'} and set(core[core.t0 == 2000].exporter) == {'A', 'B'}
    # placebo: never a country's own value, deterministic, single-country windows dropped
    reg = pd.DataFrame({'exporter': ['A', 'B', 'C', 'D', 'E'], 't0': [1995, 1995, 1995, 1995, 2000],
                        'income': [1.0, 1.1, 3.0, 3.2, 1.0], 'x': [10.0, 20.0, 30.0, 40.0, 50.0]})
    m = placebo_map(reg, 'x')
    assert (m.exporter != m.donor).all()
    assert dict(zip(m.exporter, m.donor)) == {'A': 'B', 'B': 'A', 'C': 'D', 'D': 'C'}
    assert 'E' not in set(m.exporter)
    pl = placebo(reg, 'x')
    assert dict(zip(pl.exporter, pl.x)) == {'A': 20.0, 'B': 10.0, 'C': 40.0, 'D': 30.0}
    assert placebo_map(reg.iloc[::-1], 'x').sort_values('exporter').donor.tolist() == ['B', 'A', 'D', 'C']
    print('migration-h12.py selftest ok')


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        selftest()
    else:
        main()
