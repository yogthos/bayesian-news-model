"""The product migration panel (bead bnm-3ka.2; docs/ECONOMIC-CHARACTER.md
8b, 8e H1-H2, ranked step 1).

Reads
  data/derived/supply/hs6.csv          BACI HS92 bilateral, 1995-2024
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

The exporter aggregation of hs6.csv (13 GB) is cached in
data/derived/econ/hs6-exporter.csv; delete it to rebuild.

Run with python -I scripts/migration.py
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


def exporter_values():
    """year,known,hs6,exporter,value: hs6.csv summed over importers."""
    path = os.path.join(OUT, 'hs6-exporter.csv')
    if os.path.exists(path):
        return pd.read_csv(path, dtype={'hs6': str})
    parts = []
    reader = pd.read_csv(D('derived', 'supply', 'hs6.csv'), usecols=['year', 'known', 'hs6', 'exporter', 'value'],
                         dtype={'year': 'int16', 'known': 'int16', 'hs6': str, 'exporter': str, 'value': 'float64'},
                         chunksize=20_000_000)
    for i, ch in enumerate(reader):
        parts.append(ch.groupby(['year', 'known', 'hs6', 'exporter'], sort=False).value.sum().reset_index())
        log('chunk', i, int(ch.year.min()), int(ch.year.max()))
    ev = pd.concat(parts).groupby(['year', 'known', 'hs6', 'exporter']).value.sum().reset_index()
    ev = ev[ev.value > 0]
    ev.to_csv(path, index=False)
    return ev


def concordance_rows(conc, year):
    """The HS92 rows that hold in `year` (bnm.concordance/index's rule)."""
    f, t = conc.from_year, conc.to_year
    return conc[(f.isna() | (f <= year)) & (t.isna() | (year <= t))]


def classify(x, cuts):
    lo, hi = cuts
    return np.where(pd.isna(x), '', np.where(x <= lo, 'low', np.where(x <= hi, 'mid', 'high')))


def main():
    os.makedirs(OUT, exist_ok=True)
    ci = capital_intensity()
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
    ev['share'] = ev.value / ev.groupby(['year', 'hs6']).value.transform('sum')

    hs_rows, ex_rows = [], []
    for year, g in ev.groupby('year'):
        rows = concordance_rows(conc, year).copy()
        rows['k'] = rows['product'].map(kmap)
        rows['wk'] = rows.weight * rows.k
        per = rows.groupby('hs6').agg(wk=('wk', 'sum'), w=('weight', lambda w: w[rows.loc[w.index, 'k'].notna()].sum()),
                                      n=('product', 'nunique'))
        per['cap_intensity'] = per.wk / per.w.where(per.w > 0)
        per['split'] = (per.n > 1).astype(int)
        h = g.join(per[['cap_intensity', 'split']], on='hs6')
        h['split'] = h.split.fillna(0).astype(int)
        h['cap_class'] = classify(h.cap_intensity, cuts)
        hs_rows.append(h)
        x = g.merge(rows[['hs6', 'product', 'weight']], on='hs6')
        x['value'] = x.value * x.weight
        x = x.groupby(['product', 'year', 'known', 'exporter']).value.sum().reset_index()
        x['share'] = x.value / x.groupby('product').value.transform('sum')
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


if __name__ == '__main__':
    main()
