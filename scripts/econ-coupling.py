"""Bilateral trade balances against bilateral asset positions (bead
bnm-3ka.8; docs/ECONOMIC-CHARACTER.md section 4, H5). src/bnm/
econ_coupling.clj runs the descriptive regressions; this script only
assembles the dyads.

Writes data/derived/econ/coupling-pairs.csv, one row per ordered pair and
year (holder h, issuer i):
  holder, issuer, year (data year), known,
  surplus     h's goods trade surplus with i: BACI exports h->i less i->h,
              summed over HS2 chapters (data/derived/supply/hs2-pairs.csv.gz,
              the HS92 vintage only, so no year is counted twice), USD mn
  pip         h's portfolio investment assets in i, IMF PIP (formerly CPIS),
              reported by h: A, P_TOTINV_P_USD, sector S1 to S1, annual,
              USD mn
  dip         h's direct investment position in i, IMF DIP (formerly
              CDIS): h's reported outward position (OTWD_D_NETAL_FALL_ALL,
              DV_TYPE O), else i's reported inward position from h
              (INWD_D_NETLA_FALL_ALL), USD mn
  gcap        h's portfolio holdings of i's issuers by nationality
              (Coppola, Maggiori, Neiman & Schreger 2021, restated: bonds B
              plus equity and fund shares EF, Restatement_Full; Fund
              Holdings methodology where present, else Issuance), USD mn.
              Academic use only. The EMU investor aggregate is dropped.
  gdp_i, gdp_h  WDI NY.GDP.MKTP.CD, USD mn
Known: the latest source's: BACI +2 (the file's known column), PIP +1,
DIP +2, GCAP +2 (a 2023 vintage for 2007-2020), WDI +1. Rows need a
surplus and at least one position.

  python -I scripts/econ-coupling.py          # assemble
  python -I scripts/econ-coupling.py test     # known after every input
"""
import io
import os
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda *p: os.path.join(ROOT, 'data', *p)
IMF = lambda f: D('raw', 'econ', 'imf-sdmx-holdings-reserves', f)
OUT = D('derived', 'econ', 'coupling-pairs.csv')
LAG = {'baci': 2, 'pip': 1, 'dip': 2, 'gcap': 2, 'wdi': 1}


def real_countries():
    z = zipfile.ZipFile(D('raw', 'econ', 'wdi-finance-structure', 'NY.GDP.MKTP.CD.zip'))
    name = [n for n in z.namelist() if n.startswith('Metadata_Country_')][0]
    m = pd.read_csv(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'))
    return set(m.loc[m.Region.notna(), 'Country Code'])


def gdp():
    z = zipfile.ZipFile(D('raw', 'econ', 'wdi-finance-structure', 'NY.GDP.MKTP.CD.zip'))
    name = [n for n in z.namelist() if n.startswith('API_')][0]
    w = pd.read_csv(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'), skiprows=4)
    w = w.melt(id_vars=['Country Code'], value_vars=[c for c in w.columns if c.isdigit()],
               var_name='year', value_name='gdp').dropna()
    w['year'] = w.year.astype(int)
    w['gdp'] = w.gdp / 1e6
    return w.rename(columns={'Country Code': 'iso'})


def trade():
    parts = []
    for ch in pd.read_csv(D('derived', 'supply', 'hs2-pairs.csv.gz'), chunksize=4_000_000,
                          usecols=['hs', 'year', 'exporter', 'importer', 'value'], dtype={'hs': str}):
        ch = ch[ch.hs == 'HS92']
        parts.append(ch.groupby(['year', 'exporter', 'importer'], as_index=False).value.sum())
    t = pd.concat(parts).groupby(['year', 'exporter', 'importer'], as_index=False).value.sum()
    t['value'] = t.value / 1000.0  # BACI thousands -> USD mn
    x = t.rename(columns={'exporter': 'holder', 'importer': 'issuer', 'value': 'x_hi'})
    m = t.rename(columns={'exporter': 'issuer', 'importer': 'holder', 'value': 'x_ih'})
    s = x.merge(m, on=['year', 'holder', 'issuer'], how='outer').fillna({'x_hi': 0.0, 'x_ih': 0.0})
    s['surplus'] = s.x_hi - s.x_ih
    return s[['holder', 'issuer', 'year', 'surplus']]


def pip():
    keep = []
    cols = ['COUNTRY', 'ACCOUNTING_ENTRY', 'INDICATOR', 'SECTOR', 'COUNTERPART_SECTOR', 'COUNTERPART_COUNTRY',
            'FREQUENCY', 'TIME_PERIOD', 'OBS_VALUE']
    for ch in pd.read_csv(IMF('PIP_dataonly.csv.gz'), usecols=cols, chunksize=3_000_000, dtype=str):
        ch = ch[(ch.FREQUENCY == 'A') & (ch.ACCOUNTING_ENTRY == 'A') & (ch.INDICATOR == 'P_TOTINV_P_USD')
                & (ch.SECTOR == 'S1') & (ch.COUNTERPART_SECTOR == 'S1')]
        keep.append(ch)
    p = pd.concat(keep).dropna(subset=['TIME_PERIOD', 'OBS_VALUE'])
    p = p.assign(year=p.TIME_PERIOD.astype(int), pip=p.OBS_VALUE.astype(float) / 1e6)  # USD -> USD mn
    return p.rename(columns={'COUNTRY': 'holder', 'COUNTERPART_COUNTRY': 'issuer'})[['holder', 'issuer', 'year', 'pip']]


def dip():
    keep = []
    cols = ['COUNTRY', 'DV_TYPE', 'INDICATOR', 'COUNTERPART_COUNTRY', 'FREQUENCY', 'TIME_PERIOD', 'OBS_VALUE']
    for ch in pd.read_csv(IMF('DIP_dataonly.csv.gz'), usecols=cols, chunksize=3_000_000, dtype=str):
        ch = ch[(ch.FREQUENCY == 'A') & (ch.DV_TYPE == 'O')
                & ch.INDICATOR.isin(['OTWD_D_NETAL_FALL_ALL', 'INWD_D_NETLA_FALL_ALL'])]
        keep.append(ch)
    d = pd.concat(keep).dropna(subset=['TIME_PERIOD', 'OBS_VALUE'])
    d = d.assign(year=d.TIME_PERIOD.astype(int), v=d.OBS_VALUE.astype(float) / 1e6)
    out = d[d.INDICATOR == 'OTWD_D_NETAL_FALL_ALL'].rename(columns={'COUNTRY': 'holder', 'COUNTERPART_COUNTRY': 'issuer'})
    inw = d[d.INDICATOR == 'INWD_D_NETLA_FALL_ALL'].rename(columns={'COUNTRY': 'issuer', 'COUNTERPART_COUNTRY': 'holder'})
    m = out[['holder', 'issuer', 'year', 'v']].merge(inw[['holder', 'issuer', 'year', 'v']], on=['holder', 'issuer', 'year'],
                                                      how='outer', suffixes=('_out', '_in'))
    m['dip'] = m.v_out.where(m.v_out.notna(), m.v_in)
    return m[['holder', 'issuer', 'year', 'dip']]


def gcap():
    g = pd.read_stata(D('raw', 'econ', 'gcap-restated-holdings', 'Restated_Bilateral_External_Portfolios.dta'))
    g = g[g.Asset_Class_Code.isin(['B', 'EF']) & (g.Investor != 'EMU')]
    g['Methodology'] = g.Methodology.astype(str)
    rank = {'Fund Holdings': 0, 'Enhanced Fund Holdings': 1, 'Issuance': 2}
    g['r'] = g.Methodology.map(rank)
    best = g.groupby(['Investor', 'Issuer', 'Year']).r.transform('min')
    g = g[g.r == best]
    s = g.groupby(['Investor', 'Issuer', 'Year'], as_index=False).Restatement_Full.sum(min_count=1)
    return s.rename(columns={'Investor': 'holder', 'Issuer': 'issuer', 'Year': 'year', 'Restatement_Full': 'gcap'})


def build():
    cs = real_countries()
    t = trade()
    pos = pip().merge(dip(), on=['holder', 'issuer', 'year'], how='outer').merge(gcap(), on=['holder', 'issuer', 'year'], how='outer')
    d = t.merge(pos, on=['holder', 'issuer', 'year'], how='inner')
    d = d[d.holder.isin(cs) & d.issuer.isin(cs) & (d.holder != d.issuer)]
    g = gdp()
    d = d.merge(g.rename(columns={'iso': 'issuer', 'gdp': 'gdp_i'}), on=['issuer', 'year'], how='left')
    d = d.merge(g.rename(columns={'iso': 'holder', 'gdp': 'gdp_h'}), on=['holder', 'year'], how='left')
    d['known'] = d.year + max(LAG.values())
    d = d[['holder', 'issuer', 'year', 'known', 'surplus', 'pip', 'dip', 'gcap', 'gdp_i', 'gdp_h']].sort_values(['holder', 'issuer', 'year'])
    d.to_csv(OUT, index=False, float_format='%.4f')
    print(len(d), 'rows;', d[['pip', 'dip', 'gcap']].notna().sum().to_dict())
    print(d[(d.holder == 'USA') & (d.issuer == 'CHN')].tail(4).to_string())
    print(d[(d.holder == 'CHN') & (d.issuer == 'USA')].tail(4).to_string())
    print('wrote', OUT)


def test():
    d = pd.read_csv(OUT)
    assert ((d.known - d.year) >= max(LAG.values())).all()
    assert not d.duplicated(['holder', 'issuer', 'year']).any()
    assert (d.holder != d.issuer).all()
    # the balance is antisymmetric where both directions are present
    m = d.merge(d, left_on=['holder', 'issuer', 'year'], right_on=['issuer', 'holder', 'year'])
    assert np.allclose(m.surplus_x, -m.surplus_y, atol=1e-3)
    print('ok', len(d), 'rows')


if __name__ == '__main__':
    test() if sys.argv[1:] == ['test'] else build()
