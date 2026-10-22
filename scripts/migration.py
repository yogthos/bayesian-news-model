"""The product migration panel (bead bnm-3ka.2; docs/ECONOMIC-CHARACTER.md
8b, 8e H1-H2, ranked step 1).

Reads
  data/derived/supply/hs6-country.csv.gz  BACI country x product totals;
                                       the HS92 exports, 1995-2024
                                       (known = year + 2; code 490 is TWN)
  data/derived/supply/concordance.csv  HS -> EXIOBASE product, weights, and
                                       from/to years for the 63 HS92 codes
                                       BACI fills differently from 2017
  data/raw/exiobase/v3.8.2-pxp/IOT_<y>_pxp.zip  satellite F (value added
                                       by component, employment) for
                                       1995 2000 2005 2008 2011 2015 2019 2022
writes data/derived/econ/
  capital-intensity.csv  product,year,known,cfc_va,capital_share,cfc_per_worker,va
      per EXIOBASE product and table year, summed over all 49 regions:
      consumption of fixed capital over value added (cfc_va, the
      primary measure: CFC is about delta K, so CFC/VA tracks K/VA),
      the gross capital share (CFC + rents + royalties + net operating
      surplus over value added) and CFC per worker (M EUR per 1000
      persons). known is 2021, the year v3.8.2 was published.
  capital-intensity-region.csv  region,product,code_nr,year,known,cfc,va,emp
      the same satellite rows per EXIOBASE region (for the value-added
      shares in scripts/migration-h12.py's processing-trade check).
  capital-intensity-alt.csv  product,year,known,cfc_va,capital_share,
      log_cfc_per_worker,cfc_va_median,cfc_va_emerging,klems_industry,
      klems_k_per_worker,klems_cap_share
      the 1995 measures side by side, for the sensitivity of H1/H2 to k:
      CFC/VA as the median over regions and pooled over the emerging
      regions (the world sum is 65% US, Japan and western Europe), and EU
      KLEMS 2023 (earliest year <= 2000; 29 European countries, the US and
      Japan) log net non-residential capital stock per person employed
      (demeaned within country) and capital compensation over VA, medians
      over countries, by NACE 1.1 division -> KLEMS industry (16 goods
      industries). CFC/VA ranks mining and refining low (rents inflate
      VA); KLEMS K per worker ranks them near the top.
  migration-hs6.csv      hs6,year,known,exporter,value,share,cap_intensity,cap_class,split
      every exporter of every HS92 product and year: its export value
      (thousand US$), its share of the product's world exports, the
      product's capital intensity (the concordance-weighted cfc_va of the
      EXIOBASE products it maps to, with that year's rows) and class.
      split is 1 where the code splits across EXIOBASE products: in
      1995-2011 its split carries a weight look-ahead (the HS92 weights are
      read off later reports), so its intensity there is approximate.
  migration.csv          product,year,known,exporter,value,share,cap_intensity,cap_class
      the same aggregated to EXIOBASE products (each HS6 value spread by
      that year's weights), shares of the product's world exports.

Capital intensity is fixed at the 1995 table (before the window opens, so
the migration does not cause it). Classes are terciles of the 1995 cfc_va
over the EXIOBASE goods products that carry mapped trade, unweighted:
low, mid, high; high is "capital-intensive". An HS6 code takes the class
of its intensity under the same cutoffs. The other table years are a drift
check (rank correlation with 1995, printed).

The HS92 exports of hs6-country.csv.gz are cached in
data/derived/econ/hs6-exporter.csv; delete it to rebuild.

Run with python -I scripts/migration.py (--measures: the intensity files
only; --selftest: the helper tests)
"""
import io
import os
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda *p: os.path.join(ROOT, 'data', *p)
OUT = D('derived', 'econ')
EXIO_YEARS = [1995, 2000, 2005, 2008, 2011, 2015, 2019, 2022]
REF_YEAR = 1995
EXIO_KNOWN = 2021
BACI_LAG = 2

VA_ROWS = ['Other net taxes on production',
           "Compensation of employees; wages, salaries, & employers' social contributions: Low-skilled",
           "Compensation of employees; wages, salaries, & employers' social contributions: Medium-skilled",
           "Compensation of employees; wages, salaries, & employers' social contributions: High-skilled",
           'Operating surplus: Consumption of fixed capital',
           'Operating surplus: Rents on land',
           'Operating surplus: Royalties on resources',
           'Operating surplus: Remaining net operating surplus']
CFC = 'Operating surplus: Consumption of fixed capital'
SURPLUS = [CFC, 'Operating surplus: Rents on land', 'Operating surplus: Royalties on resources',
           'Operating surplus: Remaining net operating surplus']
EMP = [f'Employment: {s}-skilled {g}' for s in ('Low', 'Medium', 'High') for g in ('male', 'female')]


def log(*a):
    print(*a, file=sys.stderr, flush=True)


def exio_year(year):
    z = zipfile.ZipFile(D('raw', 'exiobase', 'v3.8.2-pxp', f'IOT_{year}_pxp.zip'))
    base = f'IOT_{year}_pxp'
    prods = pd.read_csv(z.open(f'{base}/products.txt'), sep='\t')
    name_code = dict(zip(prods.Name, prods.CodeTxt))
    F = pd.read_csv(z.open(f'{base}/satellite/F.txt'), sep='\t', header=[0, 1], index_col=0, low_memory=False)
    F = F.loc[VA_ROWS + EMP].astype(float)
    world = F.T.groupby(level=1).sum()       # product name x stressor, all regions
    va = world[VA_ROWS].sum(axis=1)
    out = pd.DataFrame({
        'product': [name_code[n] for n in world.index],
        'year': year, 'known': EXIO_KNOWN,
        'cfc_va': world[CFC] / va,
        'capital_share': world[SURPLUS].sum(axis=1) / va,
        'cfc_per_worker': world[CFC] / world[EMP].sum(axis=1),
        'va': va}).replace([np.inf, -np.inf], np.nan)
    return out.reset_index(drop=True)


def capital_intensity():
    path = os.path.join(OUT, 'capital-intensity.csv')
    if os.path.exists(path):
        return pd.read_csv(path)
    ci = pd.concat([exio_year(y) for y in EXIO_YEARS])
    ci.to_csv(path, index=False)
    return ci


def exio_regions(year):
    """region,product,code_nr,year,cfc,va,emp: the 1995-style satellite
    rows per EXIOBASE region (M EUR, 1000 persons)."""
    z = zipfile.ZipFile(D('raw', 'exiobase', 'v3.8.2-pxp', f'IOT_{year}_pxp.zip'))
    base = f'IOT_{year}_pxp'
    prods = pd.read_csv(z.open(f'{base}/products.txt'), sep='\t')
    F = pd.read_csv(z.open(f'{base}/satellite/F.txt'), sep='\t', header=[0, 1], index_col=0, low_memory=False)
    F = F.loc[VA_ROWS + EMP].astype(float)
    t = pd.DataFrame({'cfc': F.loc[CFC], 'va': F.loc[VA_ROWS].sum(), 'emp': F.loc[EMP].sum()})
    t.index.names = ['region', 'name']
    t = t.reset_index()
    t['product'] = t.name.map(dict(zip(prods.Name, prods.CodeTxt)))
    t['code_nr'] = t.name.map(dict(zip(prods.Name, prods.CodeNr)))
    t['year'] = year
    t['known'] = EXIO_KNOWN
    return t[['region', 'product', 'code_nr', 'year', 'known', 'cfc', 'va', 'emp']]


def region_table():
    path = os.path.join(OUT, 'capital-intensity-region.csv')
    if os.path.exists(path):
        return pd.read_csv(path)
    rt = pd.concat([exio_regions(y) for y in EXIO_YEARS])
    rt.to_csv(path, index=False)
    return rt


KLEMS_KNOWN = 2023


def klems_table(max_year=2000):
    """geo,industry,year,K,EMP,CAP,VA from EU KLEMS 2023: each country's
    earliest year (at most `max_year`) with all four; K is the all-asset
    net capital stock less residential structures, current prices."""
    base = D('raw', 'econ', 'euklems-intanprod-2023')
    key = ['nace_r2_code', 'geo_code', 'year']
    na = pd.read_csv(os.path.join(base, 'national_accounts.csv'), usecols=key + ['EMP', 'VA_CP'])
    ka = pd.read_csv(os.path.join(base, 'capital_accounts.csv'), usecols=key + ['K_GFCF', 'K_Rstruc'])
    ga = pd.concat(c[c['var'] == 'CAP'] for c in pd.read_csv(os.path.join(base, 'growth_accounts_basic.csv'),
                                                            usecols=key + ['var', 'value'], chunksize=2_000_000))
    ga = ga.rename(columns={'value': 'CAP'}).drop(columns='var')
    d = na.merge(ka, on=key).merge(ga, on=key)
    d['K'] = d.K_GFCF - d.K_Rstruc.fillna(0.0)
    d = d.rename(columns={'nace_r2_code': 'industry', 'geo_code': 'geo', 'VA_CP': 'VA'})
    d = d[(d.geo.str.len() == 2) & (d.year <= max_year)].dropna(subset=['K', 'EMP', 'CAP', 'VA'])
    first = d.groupby('geo').year.transform('min')
    return d[d.year == first][['geo', 'industry', 'year', 'K', 'EMP', 'CAP', 'VA']]


def alt_measures(ci, rt):
    """capital-intensity-alt.csv: per EXIOBASE product, the 1995 measures
    side by side. known is the latest vintage behind the row (KLEMS 2023)."""
    ref = ci[ci.year == REF_YEAR].set_index('product')
    r95 = rt[rt.year == REF_YEAR]
    out = pd.DataFrame({'cfc_va': ref.cfc_va, 'capital_share': ref.capital_share,
                        'log_cfc_per_worker': np.log(ref.cfc_per_worker.where(ref.cfc_per_worker > 0))})
    out = out.join(region_measures(r95))
    codes = r95.drop_duplicates('product').set_index('product').code_nr
    out['klems_industry'] = codes.reindex(out.index).map(nace_klems)
    kl = klems_table()
    log('KLEMS countries and years:', kl.drop_duplicates('geo').set_index('geo').year.to_dict())
    out = out.join(klems_measures(kl), on='klems_industry')
    out.insert(0, 'year', REF_YEAR)
    out.insert(1, 'known', KLEMS_KNOWN)
    out = out.rename_axis('product').reset_index()
    out.to_csv(os.path.join(OUT, 'capital-intensity-alt.csv'), index=False, float_format='%.8g')
    return out


def exporter_values():
    """year,known,hs6,exporter,value: HS92 exports per country and product,
    from hs6-country.csv.gz (scripts/baci.py)."""
    path = os.path.join(OUT, 'hs6-exporter.csv')
    if os.path.exists(path):
        return pd.read_csv(path, dtype={'hs6': str})
    parts = []
    reader = pd.read_csv(D('derived', 'supply', 'hs6-country.csv.gz'),
                         usecols=['hs', 'year', 'known', 'hs6', 'country', 'exports'],
                         dtype={'hs': str, 'year': 'int16', 'known': 'int16', 'hs6': str,
                                'country': str, 'exports': 'float64'},
                         chunksize=10_000_000)
    for i, ch in enumerate(reader):
        ch = ch[(ch.hs == 'HS92') & (ch.exports > 0)]
        parts.append(ch.rename(columns={'country': 'exporter', 'exports': 'value'})
                     [['year', 'known', 'hs6', 'exporter', 'value']])
        log('chunk', i)
    ev = pd.concat(parts, ignore_index=True)
    ev.to_csv(path, index=False)
    return ev


def concordance_rows(conc, year):
    """The HS92 rows that hold in `year` (bnm.concordance/index's rule)."""
    f, t = conc.from_year, conc.to_year
    return conc[(f.isna() | (f <= year)) & (t.isna() | (year <= t))]


def classify(x, cuts):
    lo, hi = cuts
    return np.where(pd.isna(x), '', np.where(x <= lo, 'low', np.where(x <= hi, 'mid', 'high')))


def hs6_intensity(rows, kmap):
    """cap_intensity and split per HS6 code from concordance `rows`
    (hs6, product, weight): the weight-averaged k of the products with a
    known k (weights renormalised over them); split is 1 where the code
    maps to more than one product."""
    rows = rows.copy()
    rows['k'] = rows['product'].map(kmap)
    rows['wk'] = rows.weight * rows.k
    rows['wknown'] = rows.weight.where(rows.k.notna(), 0.0)
    per = rows.groupby('hs6').agg(wk=('wk', 'sum'), w=('wknown', 'sum'), n=('product', 'nunique'))
    per['cap_intensity'] = per.wk / per.w.where(per.w > 0)
    per['split'] = (per.n > 1).astype(int)
    return per[['cap_intensity', 'split']]


def world_shares(df, keys):
    """Each row's value over its `keys` group's total."""
    return (df.value / df.groupby(keys).value.transform('sum')).to_numpy()


# --------------------------------------------- alternative capital intensities

# EXIOBASE regions outside the 1995 high-income core: the pool for an
# intensity not weighted towards rich-country technology.
EMERGING = ['CN', 'IN', 'BR', 'ID', 'MX', 'TR', 'RU', 'ZA', 'BG', 'RO', 'WA', 'WE', 'WF', 'WL', 'WM']

# NACE 1.1 division (EXIOBASE CodeNr) -> EU KLEMS 2023 industry
_KLEMS = {1: 'A', 2: 'A', 5: 'A', 10: 'B', 11: 'B', 12: 'B', 13: 'B', 14: 'B', 15: 'C10-C12', 16: 'C10-C12',
          17: 'C13-C15', 18: 'C13-C15', 19: 'C13-C15', 20: 'C16-C18', 21: 'C16-C18', 22: 'C16-C18', 23: 'C19',
          24: 'C20', 25: 'C22-C23', 26: 'C22-C23', 27: 'C24-C25', 28: 'C24-C25', 29: 'C28', 30: 'C26',
          31: 'C27', 32: 'C26', 33: 'C26', 34: 'C29-C30', 35: 'C29-C30', 36: 'C31-C33', 37: 'E', 40: 'D', 41: 'E'}


def nace_klems(code_nr):
    return _KLEMS.get(int(code_nr[1:3]))


def region_measures(rr):
    """Per product, from region rows (region, product, cfc, va): the median
    over regions with positive VA of CFC/VA clipped to [0, 1], and the
    pooled CFC/VA of the EMERGING regions."""
    r = rr[rr.va > 0].assign(ratio=lambda d: (d.cfc / d.va).clip(0, 1))
    med = r.groupby('product').ratio.median()
    e = r[r.region.isin(EMERGING)].groupby('product')[['cfc', 'va']].sum()
    return pd.DataFrame({'cfc_va_median': med, 'cfc_va_emerging': e.cfc / e.va}).reindex(sorted(set(rr['product'])))


def klems_measures(kl):
    """Per KLEMS industry from rows (geo, industry, K, EMP, CAP, VA): the
    log net capital stock per person employed, demeaned within country
    (so currencies cancel), median over countries; and the capital
    compensation share CAP/VA, median over countries."""
    d = kl[(kl.K > 0) & (kl.EMP > 0) & (kl.VA > 0)].copy()
    d['lk'] = np.log(d.K / d.EMP)
    d['lk'] -= d.groupby('geo').lk.transform('mean')
    d['cs'] = d.CAP / d.VA
    return d.groupby('industry').agg(klems_k_per_worker=('lk', 'median'), klems_cap_share=('cs', 'median'))


def measures(ci):
    """The alternative intensities and how they rank against cfc_va."""
    alt = alt_measures(ci, region_table())
    cols = ['cfc_va', 'cfc_va_median', 'cfc_va_emerging', 'capital_share', 'log_cfc_per_worker',
            'klems_k_per_worker', 'klems_cap_share']
    traded = alt[alt.klems_industry.notna()]
    log('Spearman rank correlations over goods products (EXIOBASE 1995, KLEMS earliest year):')
    log(traded[cols].corr(method='spearman').round(2).to_string())
    r95 = region_table().query('year == @REF_YEAR')
    r95 = r95.assign(industry=r95.code_nr.map(nace_klems)).dropna(subset=['industry'])
    ind = r95.groupby('industry')[['cfc', 'va']].sum()
    ind = pd.DataFrame({'exio_cfc_va': ind.cfc / ind.va}).join(
        alt.drop_duplicates('klems_industry').set_index('klems_industry')[['klems_k_per_worker', 'klems_cap_share']])
    log(f'By KLEMS goods industry ({len(ind)}): EXIOBASE world CFC/VA against KLEMS')
    log(ind.sort_values('exio_cfc_va').round(3).to_string())
    log(ind.corr(method='spearman').round(2).to_string())
    return alt


def main():
    os.makedirs(OUT, exist_ok=True)
    ci = capital_intensity()
    measures(ci)
    if '--measures' in sys.argv:
        return
    ref = ci[ci.year == REF_YEAR].set_index('product')
    for y in EXIO_YEARS[1:]:
        other = ci[ci.year == y].set_index('product').cfc_va
        both = pd.concat([ref.cfc_va, other], axis=1).dropna()
        log(f'cfc_va rank correlation 1995 vs {y}: {both.rank().corr().iloc[0, 1]:.3f}')

    conc = pd.read_csv(D('derived', 'supply', 'concordance.csv'), dtype={'hs_code': str, 'hs6': str})
    conc = conc[conc.hs_edition == 'HS92'][['hs_code', 'exiobase_product', 'weight', 'from_year', 'to_year']]
    conc = conc.rename(columns={'hs_code': 'hs6', 'exiobase_product': 'product'})
    traded = sorted(set(conc['product']))
    k = ref.cfc_va.reindex(traded).dropna()
    cuts = (float(k.quantile(1 / 3)), float(k.quantile(2 / 3)))
    log(f'{len(k)} traded EXIOBASE products, cfc_va tercile cuts {cuts[0]:.4f} {cuts[1]:.4f}')
    kmap = ref.cfc_va.to_dict()

    ev = exporter_values()
    ev['known'] = ev.year + BACI_LAG
    ev['share'] = world_shares(ev, ['year', 'hs6'])

    hs_rows, ex_rows = [], []
    for year, g in ev.groupby('year'):
        rows = concordance_rows(conc, year)
        per = hs6_intensity(rows, kmap)
        h = g.join(per, on='hs6')
        h['split'] = h.split.fillna(0).astype(int)
        h['cap_class'] = classify(h.cap_intensity, cuts)
        hs_rows.append(h)
        x = g.merge(rows[['hs6', 'product', 'weight']], on='hs6')
        x['value'] = x.value * x.weight
        x = x.groupby(['product', 'year', 'known', 'exporter']).value.sum().reset_index()
        x['share'] = world_shares(x, ['product'])
        x['cap_intensity'] = x['product'].map(kmap)
        x['cap_class'] = classify(x.cap_intensity, cuts)
        ex_rows.append(x)
        log(year, len(h), len(x))

    hs = pd.concat(hs_rows)[['hs6', 'year', 'known', 'exporter', 'value', 'share', 'cap_intensity', 'cap_class', 'split']]
    ex = pd.concat(ex_rows)[['product', 'year', 'known', 'exporter', 'value', 'share', 'cap_intensity', 'cap_class']]
    for name, df, key in (('hs6', hs, 'hs6'), ('exiobase', ex, 'product')):
        s = df.groupby([key, 'year']).share.sum()
        assert np.allclose(s, 1.0), f'{name} shares do not sum to 1'
        assert (df.known == df.year + BACI_LAG).all()
    hs.to_csv(os.path.join(OUT, 'migration-hs6.csv'), index=False, float_format='%.6g')
    ex.to_csv(os.path.join(OUT, 'migration.csv'), index=False, float_format='%.8g')
    log(f'hs6 panel {len(hs)} rows, {hs.hs6.nunique()} products, {hs.exporter.nunique()} exporters; '
        f'exiobase panel {len(ex)} rows, {ex["product"].nunique()} products')
    log('hs6 classes (products, 1995):', hs[hs.year == 1995].drop_duplicates('hs6').cap_class.value_counts().to_dict())


# ---------------------------------------------------------------- self-test

def selftest():
    """python -I scripts/migration.py --selftest"""
    # classification: a cut belongs to the class below, NaN has no class
    assert list(classify(np.array([0.05, 0.1, 0.15, 0.25, np.nan]), (0.1, 0.2))) == ['low', 'low', 'mid', 'high', '']
    # concordance rows by year
    conc = pd.DataFrame({'hs6': ['a', 'a', 'b'], 'product': ['P', 'Q', 'P'], 'weight': [1.0, 1.0, 1.0],
                         'from_year': [np.nan, 2017, np.nan], 'to_year': [2016, np.nan, np.nan]})
    assert list(concordance_rows(conc, 2000)['product']) == ['P', 'P']
    assert list(concordance_rows(conc, 2017)['product']) == ['Q', 'P']
    # HS6 intensity: the weighted mean over the products with a known k; split flag
    rows = pd.DataFrame({'hs6': ['x', 'x', 'y', 'z'], 'product': ['P', 'Q', 'P', 'R'], 'weight': [0.25, 0.75, 1.0, 1.0]})
    per = hs6_intensity(rows, {'P': 0.1, 'Q': 0.3})
    assert abs(per.loc['x', 'cap_intensity'] - (0.25 * 0.1 + 0.75 * 0.3)) < 1e-12
    assert per.loc['x', 'split'] == 1 and per.loc['y', 'split'] == 0
    assert np.isnan(per.loc['z', 'cap_intensity'])
    rows2 = pd.DataFrame({'hs6': ['x', 'x'], 'product': ['P', 'R'], 'weight': [0.5, 0.5]})
    assert abs(hs6_intensity(rows2, {'P': 0.1}).loc['x', 'cap_intensity'] - 0.1) < 1e-12, 'unknown k renormalised away'
    # shares sum to one within each key
    df = pd.DataFrame({'p': ['a', 'a', 'b'], 'y': [1, 1, 1], 'value': [1.0, 3.0, 2.0]})
    sh = world_shares(df, ['p', 'y'])
    assert np.allclose(sh, [0.25, 0.75, 1.0])
    # EXIOBASE CodeNr (NACE 1.1) -> EU KLEMS 2023 industry
    for code, ind in [('p01.a', 'A'), ('p05', 'A'), ('p11.a', 'B'), ('p15.c', 'C10-C12'), ('p18', 'C13-C15'),
                      ('p21.1', 'C16-C18'), ('p23.20.a', 'C19'), ('p24.a', 'C20'), ('p26.d', 'C22-C23'),
                      ('p27.41', 'C24-C25'), ('p29', 'C28'), ('p30', 'C26'), ('p31', 'C27'), ('p32', 'C26'),
                      ('p34', 'C29-C30'), ('p36', 'C31-C33'), ('p40.11.a', 'D'), ('p41', 'E'), ('p45', None), ('p65', None)]:
        assert nace_klems(code) == ind, (code, nace_klems(code))
    # region measures: median over regions (rich-country weight removed) and the emerging-region pool
    rr = pd.DataFrame({'region': ['US', 'JP', 'CN', 'US', 'JP', 'CN'], 'product': ['P', 'P', 'P', 'Q', 'Q', 'Q'],
                       'cfc': [30.0, 20.0, 1.0, 1.0, 2.0, 0.0], 'va': [100.0, 100.0, 10.0, 10.0, 10.0, -1.0]})
    m = region_measures(rr)
    assert abs(m.loc['P', 'cfc_va_median'] - 0.2) < 1e-12
    assert abs(m.loc['Q', 'cfc_va_median'] - 0.15) < 1e-12, 'regions with VA <= 0 are dropped'
    assert abs(m.loc['P', 'cfc_va_emerging'] - 0.1) < 1e-12
    assert np.isnan(m.loc['Q', 'cfc_va_emerging'])
    # KLEMS measures: within-country log K/L demeaned, median over countries; CAP/VA median
    kl = pd.DataFrame({'geo': ['AT', 'AT', 'DE', 'DE'], 'industry': ['C19', 'C13-C15'] * 2,
                       'K': [400.0, 100.0, 900.0, 100.0], 'EMP': [1.0, 1.0, 1.0, 1.0],
                       'CAP': [6.0, 2.0, 5.0, 1.0], 'VA': [10.0, 10.0, 10.0, 10.0]})
    km = klems_measures(kl)
    assert km.loc['C19', 'klems_k_per_worker'] > km.loc['C13-C15', 'klems_k_per_worker']
    assert abs((km.loc['C19', 'klems_k_per_worker'] - km.loc['C13-C15', 'klems_k_per_worker'])
               - np.median([np.log(4.0), np.log(9.0)])) < 1e-12
    assert abs(km.loc['C19', 'klems_cap_share'] - 0.55) < 1e-12
    print('migration.py selftest ok')


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        selftest()
    else:
        main()
