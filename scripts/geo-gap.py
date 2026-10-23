"""Material capability against financial power, and dyadic tension, for the
major powers (bead bnm-3ka.9; docs/ECONOMIC-CHARACTER.md section 5, H9).
src/bnm/geo_gap.clj builds the stock and the descriptive dyad panel; this
script only assembles inputs.

Writes data/derived/econ/geo-inputs.csv, long (iso, year = data year,
known, indicator, value), world shares in [0, 1]:
  steel-share     crude steel production, BGS World Mineral Statistics,
                  over the sum of all countries; +2
  energy-share    primary energy consumption, OWID (Energy Institute),
                  over OWID's World row; +1
  manuf-share     manufacturing value added, WDI NV.IND.MANF.CD, over
                  WDI's WLD row; +1
  mcap-share      stock market capitalisation, WDI CM.MKT.LCAP.CD, over WLD;
                  +1
  reserve-share   currency share of allocated official FX reserves, IMF
                  COFER (world, annual, 1995-): USD -> USA, JPY -> JPN, GBP ->
                  GBR, CNY -> CHN, CHF -> CHE, CAD -> CAN, AUD -> AUS; DEM,
                  FRF, NLG to their countries to 1998 (the ECU, 8.5% in 1995, is
                  not assigned); EUR (1999-) split over
                  the euro members by their share of the euro area's GDP
                  that year; 0 for every other country from 1995 (their
                  currencies are not identified, "other currencies" was 2-5%);
                  +1
  cinc            COW NMC 6.0 CINC (to 2016), for comparison; +1
and data/derived/econ/geo-dyads.csv for the 28 dyads of the eight major
powers (USA CHN RUS JPN DEU GBR FRA IND), years 1950-2024:
  a, b, year,
  mid_onset       a MID starting that year with a and b on opposite sides
                  (COW MID 5.0 participants, MIDB; to 2014)
  mid_any         a MID with them on opposite sides ongoing that year
  mid_hostlev     the highest hostility level of either side in those MIDs
  er_ab, er_ba    export restrictions by a toward b (by b toward a): the
                  number of OECD-inventory measures (2009-2024) in force on a
                  raw material in each HS2 chapter, averaged over b's imports
                  from a by chapter the year before (BACI HS92), the weight
                  of the chapters b actually buys from a
Sanctions: the Global Sanctions Data Base is a manual download (request
form) and is not on disk, so it is not used. The dyadic MID file
(dyadic_mid_4.02.zip) on disk is truncated; the dyads are built from MIDB.

  python -I scripts/geo-gap.py
  python -I scripts/geo-gap.py test
"""
import io
import os
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda *p: os.path.join(ROOT, 'data', *p)
OUT = D('derived', 'econ', 'geo-inputs.csv')
DYADS = D('derived', 'econ', 'geo-dyads.csv')
POWERS = ['USA', 'CHN', 'RUS', 'JPN', 'DEU', 'GBR', 'FRA', 'IND']
COW = {2: 'USA', 710: 'CHN', 365: 'RUS', 740: 'JPN', 255: 'DEU', 260: 'DEU', 200: 'GBR', 220: 'FRA', 750: 'IND'}
EURO = {  # member: first year in the euro
    'AUT': 1999, 'BEL': 1999, 'FIN': 1999, 'FRA': 1999, 'DEU': 1999, 'IRL': 1999, 'ITA': 1999, 'LUX': 1999,
    'NLD': 1999, 'PRT': 1999, 'ESP': 1999, 'GRC': 2001, 'SVN': 2007, 'CYP': 2008, 'MLT': 2008, 'SVK': 2009,
    'EST': 2011, 'LVA': 2014, 'LTU': 2015, 'HRV': 2023}
LAG = {'steel-share': 2, 'energy-share': 1, 'manuf-share': 1, 'mcap-share': 1, 'reserve-share': 1, 'cinc': 1}


def frame(iso, year, value, indicator):
    d = pd.DataFrame({'iso': iso, 'year': year, 'value': value}).dropna()
    d = d[np.isfinite(d.value)]
    d['year'] = d.year.astype(int)
    d['known'] = d.year + LAG[indicator]
    d['indicator'] = indicator
    return d[['iso', 'year', 'known', 'indicator', 'value']]


def wdi(code):
    z = zipfile.ZipFile(D('raw', 'econ', 'wdi-finance-structure', f'{code}.zip'))
    name = [n for n in z.namelist() if n.startswith('API_')][0]
    w = pd.read_csv(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'), skiprows=4)
    w = w.melt(id_vars=['Country Code'], value_vars=[c for c in w.columns if c.isdigit()], var_name='year', value_name='v').dropna()
    return w.rename(columns={'Country Code': 'iso'}).assign(year=lambda d: d.year.astype(int))


def real_countries():
    z = zipfile.ZipFile(D('raw', 'econ', 'wdi-finance-structure', 'NY.GDP.MKTP.CD.zip'))
    name = [n for n in z.namelist() if n.startswith('Metadata_Country_')][0]
    m = pd.read_csv(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'))
    return set(m.loc[m.Region.notna(), 'Country Code'])


def wdi_share(code, indicator, cs):
    w = wdi(code)
    world = w[w.iso == 'WLD'].set_index('year').v
    c = w[w.iso.isin(cs)].copy()
    c['share'] = c.v / c.year.map(world)
    return frame(c.iso, c.year, c.share, indicator)


def steel():
    b = pd.read_csv(D('raw', 'material', 'bgs-world-mineral-statistics', 'bgs_world_mineral_statistics_1970-2021.csv.gz'),
                    low_memory=False)
    s = b[(b.bgs_commodity_trans == 'steel, crude') & (b.bgs_statistic_type_trans == 'Production')].copy()
    s['year'] = s.year.str[:4].astype(int)
    s = s.dropna(subset=['country_iso3_code', 'quantity'])
    s = s.groupby(['country_iso3_code', 'year'], as_index=False).quantity.sum()
    tot = s.groupby('year').quantity.transform('sum')
    return frame(s.country_iso3_code, s.year, s.quantity / tot, 'steel-share')


def energy(cs):
    o = pd.read_csv(D('raw', 'material', 'owid-energy', 'owid-energy-data.csv'), usecols=['country', 'year', 'iso_code', 'primary_energy_consumption'])
    world = o[o.country == 'World'].set_index('year').primary_energy_consumption
    c = o[o.iso_code.isin(cs)].copy()
    return frame(c.iso_code, c.year, c.primary_energy_consumption / c.year.map(world), 'energy-share')


def reserves(cs):
    c = pd.read_csv(D('raw', 'econ', 'imf-sdmx-holdings-reserves', 'COFER.csv.gz'), low_memory=False)
    c = c[(c.COUNTRY == 'G001') & (c.INDICATOR == 'AFXRA') & (c.TYPE_OF_TRANSFORMATION == 'SHRO_PT') & (c.FREQUENCY == 'A')]
    c = c.assign(year=c.TIME_PERIOD.astype(int), share=c.OBS_VALUE.astype(float) / 100.0)
    direct = {'CI_USD': 'USA', 'CI_JPY': 'JPN', 'CI_GBP': 'GBR', 'CI_CNY': 'CHN', 'CI_CHF': 'CHE', 'CI_CAD': 'CAN',
              'CI_AUD': 'AUS', 'CI_DEM': 'DEU', 'CI_FRF': 'FRA', 'CI_NLG': 'NLD'}
    rows = [(direct[r.FXR_CURRENCY], r.year, r.share) for r in c.itertuples() if r.FXR_CURRENCY in direct]
    gdp = wdi('NY.GDP.MKTP.CD')
    for r in c[c.FXR_CURRENCY == 'CI_EUR'].itertuples():
        mem = [m for m, y0 in EURO.items() if y0 <= r.year]
        g = gdp[(gdp.year == r.year) & gdp.iso.isin(mem)]
        for x in g.itertuples():
            rows.append((x.iso, r.year, r.share * x.v / g.v.sum()))
    d = pd.DataFrame(rows, columns=['iso', 'year', 'share']).groupby(['iso', 'year'], as_index=False).share.sum()
    years = sorted(c.year.unique())
    full = pd.MultiIndex.from_product([sorted(cs), years], names=['iso', 'year']).to_frame(index=False)
    d = full.merge(d, on=['iso', 'year'], how='left').fillna({'share': 0.0})
    return frame(d.iso, d.year, d.share, 'reserve-share')


def cinc():
    n = pd.read_csv(D('raw', 'slow', 'cow', 'nmc', 'NMC-60-abridged.csv'), na_values=[-9])
    n['iso'] = n.ccode.map(COW)
    n = n.dropna(subset=['iso'])
    n = n.groupby(['iso', 'year'], as_index=False).cinc.sum()
    return frame(n.iso, n.year, n.cinc, 'cinc')


def mids():
    m = pd.read_csv(D('raw', 'slow', 'cow', 'mid', 'MIDB 5.0.csv'))
    m['iso'] = m.ccode.map(COW)
    m = m.dropna(subset=['iso'])
    out = []
    for disp, g in m.groupby('dispnum'):
        a_side, b_side = g[g.sidea == 1], g[g.sidea == 0]
        for ra in a_side.itertuples():
            for rb in b_side.itertuples():
                if ra.iso == rb.iso:
                    continue
                y0, y1 = max(ra.styear, rb.styear), min(ra.endyear, rb.endyear)
                if y1 < y0:
                    continue
                a, b = sorted([ra.iso, rb.iso])
                for y in range(y0, y1 + 1):
                    out.append((a, b, y, int(y == y0), max(ra.hostlev, rb.hostlev)))
    d = pd.DataFrame(out, columns=['a', 'b', 'year', 'mid_onset', 'mid_hostlev'])
    return d.groupby(['a', 'b', 'year'], as_index=False).agg(mid_onset=('mid_onset', 'max'), mid_hostlev=('mid_hostlev', 'max')).assign(mid_any=1)


def restrictions():
    r = pd.read_csv(D('raw', 'material', 'oecd-export-restrictions-irm', 'OECD_EXP_RESTRIC_all.csv.gz'),
                    usecols=['REF_AREA', 'COMMODITY', 'POLICY_TYPE', 'POLICY_DESCRIPTION', 'TIME_PERIOD'])
    r = r[(r.POLICY_DESCRIPTION == 'TEMP') & (r.POLICY_TYPE != 'NO_RESTRICTION') & r.REF_AREA.isin(POWERS)]
    r['hs2'] = r.COMMODITY.str.extract(r'HS\d\d_(\d\d)')[0]
    n = r.groupby(['REF_AREA', 'TIME_PERIOD', 'hs2']).size().rename('n').reset_index()
    t = pd.read_csv(D('derived', 'supply', 'hs2-pairs.csv.gz'), usecols=['hs', 'year', 'hs2', 'exporter', 'importer', 'value'],
                    dtype={'hs': str, 'hs2': str})
    t = t[(t.hs == 'HS92') & t.exporter.isin(POWERS) & t.importer.isin(POWERS) & (t.exporter != t.importer)]
    out = []
    for (ex, im, y), g in t.groupby(['exporter', 'importer', 'year']):
        tot = g.value.sum()
        if tot <= 0:
            continue
        w = g.set_index('hs2').value / tot
        for yr in [y + 1]:  # weights from the year before the measures
            nn = n[(n.REF_AREA == ex) & (n.TIME_PERIOD == yr)].set_index('hs2').n
            if yr < 2009 or yr > 2024:
                continue
            out.append((ex, im, yr, float((w * nn.reindex(w.index).fillna(0)).sum())))
    return pd.DataFrame(out, columns=['ex', 'im', 'year', 'er'])


def build():
    cs = real_countries()
    d = pd.concat([steel(), energy(cs), wdi_share('NV.IND.MANF.CD', 'manuf-share', cs),
                   wdi_share('CM.MKT.LCAP.CD', 'mcap-share', cs), reserves(cs), cinc()])
    d = d[d.iso.isin(cs)].sort_values(['indicator', 'iso', 'year'])
    d.to_csv(OUT, index=False, float_format='%.6g')
    for ind, g in d.groupby('indicator'):
        print(f'{ind:14s} {g.iso.nunique():4d} countries {g.year.min()}-{g.year.max()}')
    dy = pd.DataFrame([(a, b, y) for i, a in enumerate(sorted(POWERS)) for b in sorted(POWERS)[i + 1:] for y in range(1950, 2025)],
                      columns=['a', 'b', 'year'])
    dy = dy.merge(mids(), on=['a', 'b', 'year'], how='left')
    dy.loc[dy.year > 2014, ['mid_onset', 'mid_any', 'mid_hostlev']] = np.nan
    dy.loc[dy.year <= 2014, ['mid_onset', 'mid_any', 'mid_hostlev']] = dy.loc[dy.year <= 2014, ['mid_onset', 'mid_any', 'mid_hostlev']].fillna(0)
    er = restrictions()
    dy = dy.merge(er.rename(columns={'ex': 'a', 'im': 'b', 'er': 'er_ab'}), on=['a', 'b', 'year'], how='left')
    dy = dy.merge(er.rename(columns={'ex': 'b', 'im': 'a', 'er': 'er_ba'}), on=['a', 'b', 'year'], how='left')
    dy.to_csv(DYADS, index=False, float_format='%.6g')
    print(dy[dy.year <= 2014].groupby(['a', 'b']).mid_any.sum().loc[lambda s: s > 0].to_string())
    print(dy[(dy.a == 'CHN') & (dy.b == 'USA')].tail(8).to_string())
    print('wrote', OUT, DYADS)


def test():
    d = pd.read_csv(OUT)
    for ind, g in d.groupby('indicator'):
        assert ((g.known - g.year) == LAG[ind]).all(), ind
    assert not d.duplicated(['iso', 'year', 'indicator']).any()
    sh = d[d.indicator.str.endswith('-share')]
    assert (sh.value >= 0).all() and (sh.value <= 1.0001).all()
    r = d[d.indicator == 'reserve-share'].groupby('year').value.sum()
    assert (r > 0.85).all() and (r < 1.0001).all(), r  # ECU (to 1998) and other currencies unassigned
    dy = pd.read_csv(DYADS)
    assert len(dy) == 28 * 75
    assert dy[dy.year > 2014].mid_any.isna().all()
    print('ok', len(d), len(dy))


if __name__ == '__main__':
    test() if sys.argv[1:] == ['test'] else build()
