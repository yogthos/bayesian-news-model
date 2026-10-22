"""Country-year indicators for the two economic-character factors (beads
bnm-3ka.4 and bnm-3ka.5; docs/ECONOMIC-CHARACTER.md 8a, H3). src/bnm/
econ_factors.clj fits the factors; this script only assembles inputs.

Writes data/derived/econ/econ-indicators.csv, long:
  iso, year (the data year), known (the first year the value could be
  read), concept (financialisation | investment-regime), indicator, value
and data/derived/econ/econ-indicators-coverage.txt (countries and years per
indicator).

Known years: the release lag of each source, as elsewhere in the project
(scripts/migration-h12.py): WDI and GFDD +1; Fed Z.1 +1; OECD and Eurostat
sector accounts +2 (docs/ECONOMIC-CHARACTER.md 8a); WID +2; the IMF FD
index and ICSD +2; PWT +3. A row whose source is a later vintage still
carries the data year's known year: the vintage is not modelled (the
values are the 2026 vintage, revised since first release).

Financialisation indicators:
  log-mcap-gdp      log stock market capitalisation / GDP (%), WDI
                    CM.MKT.LCAP.GD.ZS, GFDD DM.01 where WDI is missing
  log-traded-gdp    log stocks traded / GDP (%), WDI CM.MKT.TRAD.GD.ZS,
                    GFDD DM.02 fill
  log-credit-gdp    log domestic credit to the private sector / GDP (%),
                    WDI FD.AST.PRVT.GD.ZS, GFDD DI.14 fill
  fd-index          IMF Financial Development index FD_IX (0-1)
  nfc-payout        non-financial corporations' distributed income paid
                    (D42) plus net equity buybacks (US only: minus Z.1 net
                    issuance FU103164105), over gross operating surplus
                    (B2A3G). OECD Table 0800; Eurostat nasa_10_nf_tr where
                    OECD lacks the country-year. Outside the US D42 is all
                    there is: buybacks are not in the sector accounts.
  nfc-net-lending   NFC net lending (B9) / gross operating surplus. Over
                    GOS, not value added: Japan has no S11 B1G in Table 0800
  nfc-inv-profit    NFC gross fixed capital formation (P51G on total fixed
                    assets, N11G; the table also breaks it down by asset) /
                    gross operating surplus (Stockhammer's accumulation
                    against profit)
  nfc-property-income
                    NFC property income received (D4, resources) / gross
                    operating surplus (8a's portfolio income; Krippner).
                    It includes dividends from subsidiaries, so holding-
                    company economies (France, the Netherlands) read high
  fin-va-share      finance and insurance (ISIC K) share of gross value
                    added, OECD Table 6. HOOK: when data/derived/stocks/
                    econ-structure.csv exists (bead bnm-3ka.1, the
                    corrected finance VA share), its finance VA variable
                    replaces this one. Its known year is its `known`
                    column if present, else its `year` (the stocks
                    convention, with `data_year` the data year).
  private-wealth-ni private wealth / national income, WID wpweali999
Investment regime indicators:
  inv-gdp           investment share of GDP, PWT 11 csh_i
  public-k-share    general government share of the capital stock, ICSD
                    (S13 / (S13 + private + PPP), current prices)
  gov-inv-share     general government share of gross fixed capital
                    formation, ICSD (same split)
  public-wealth-ni  net public wealth / national income, WID wgweali999
  irr               PWT 11 internal rate of return on capital
  hh-cons-share     household consumption / GDP, GMD hcons_GDP (forecast
                    rows dropped), +1
  current-account   current account / GDP, WDI BN.CAB.XOKA.GD.ZS, +1
  SOE share: no open cross-country panel (the doc, section 3); not built.
Also written, not a factor indicator: log-gdp-pc (PWT rgdpna / pop, +3),
for the validation's income control.

  python -I scripts/econ-factors.py            # assemble the indicators
  python -I scripts/econ-factors.py test       # no value dated before it
                                               # is known (both layouts of
                                               # econ-structure.csv)
  python -I scripts/econ-factors-check.py      # validation of the factors
"""
import io
import json
import os
import re
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda *p: os.path.join(ROOT, 'data', *p)
RAW = lambda *p: D('raw', *p)
OUT = D('derived', 'econ', 'econ-indicators.csv')
COV = D('derived', 'econ', 'econ-indicators-coverage.txt')
STRUCTURE = D('derived', 'stocks', 'econ-structure.csv')
FIN, INV, AUX = 'financialisation', 'investment-regime', 'aux'
LAG = {'wdi': 1, 'z1': 1, 'sna': 2, 'wid': 2, 'imf': 2, 'pwt': 3}


# ---------------------------------------------------------------- helpers

def iso2_map():
    """ISO2 -> ISO3 from a World Bank API file, plus the codes Eurostat and
    WID use that the World Bank does not."""
    w = json.load(open(RAW('slow', 'worldbank', 'NY.GDP.MKTP.CD.json')))[1]
    m = {x['country']['id']: x['countryiso3code'] for x in w if x['countryiso3code']}
    m.update({'EL': 'GRC', 'UK': 'GBR', 'TW': 'TWN', 'XK': 'XKX'})
    return m


def real_countries():
    """ISO3 codes of economies, not World Bank aggregates (an aggregate has
    no region in WDI's country metadata)."""
    z = zipfile.ZipFile(RAW('econ', 'wdi-finance-structure', 'CM.MKT.LCAP.GD.ZS.zip'))
    name = [n for n in z.namelist() if n.startswith('Metadata_Country_')][0]
    m = pd.read_csv(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'))
    return set(m.loc[m.Region.notna(), 'Country Code'])


def frame(iso, year, value, concept, indicator, lag):
    d = pd.DataFrame({'iso': iso, 'year': year, 'value': value}).dropna()
    d = d[np.isfinite(d.value)]
    d['year'] = d.year.astype(int)
    d['known'] = d.year + lag
    d['concept'] = concept
    d['indicator'] = indicator
    return d[['iso', 'year', 'known', 'concept', 'indicator', 'value']]


def wdi(code):
    z = zipfile.ZipFile(RAW('econ', 'wdi-finance-structure', f'{code}.zip'))
    name = [n for n in z.namelist() if n.startswith('API_')][0]
    w = pd.read_csv(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'), skiprows=4)
    w = w.melt(id_vars=['Country Code'], value_vars=[c for c in w.columns if c.isdigit()],
               var_name='year', value_name='v').dropna()
    return w.rename(columns={'Country Code': 'iso'}).assign(year=lambda d: d.year.astype(int))


_gfdd = None


def gfdd(code):
    global _gfdd
    if _gfdd is None:
        z = zipfile.ZipFile(RAW('econ', 'wb-gfdd', 'GFDD_CSV.zip'))
        _gfdd = pd.read_csv(z.open('GFDDData.csv'))
    g = _gfdd[_gfdd['Indicator Code'] == code]
    g = g.melt(id_vars=['Country Code'], value_vars=[c for c in g.columns if c.isdigit()],
               var_name='year', value_name='v').dropna()
    return g.rename(columns={'Country Code': 'iso'}).assign(year=lambda d: d.year.astype(int))


def splice(primary, fill):
    """primary's rows, plus fill's where primary has no (iso, year)."""
    k = set(zip(primary.iso, primary.year))
    return pd.concat([primary, fill[[(i, y) not in k for i, y in zip(fill.iso, fill.year)]]])


def market_indicators(countries):
    out = []
    for name, w, g in [('log-mcap-gdp', 'CM.MKT.LCAP.GD.ZS', 'GFDD.DM.01'),
                       ('log-traded-gdp', 'CM.MKT.TRAD.GD.ZS', 'GFDD.DM.02'),
                       ('log-credit-gdp', 'FD.AST.PRVT.GD.ZS', 'GFDD.DI.14')]:
        s = splice(wdi(w), gfdd(g))
        s = s[s.iso.isin(countries) & (s.v > 0)]
        out.append(frame(s.iso, s.year, np.log(s.v), FIN, name, LAG['wdi']))
    return out


def fd_index():
    t = pd.read_csv(RAW('econ', 'imf-sdmx-holdings-reserves', 'FDI.csv.gz'),
                    usecols=['COUNTRY', 'INDICATOR', 'TIME_PERIOD', 'OBS_VALUE'])
    t = t[t.INDICATOR == 'FD_IX']
    return frame(t.COUNTRY, t.TIME_PERIOD, t.OBS_VALUE, FIN, 'fd-index', LAG['imf'])


# ---------------------------------------------------------------- sector accounts

def s11_pivot(t):
    """{(iso, year): {transaction: value}} for S11, national currency, from
    Table 0800 rows with bare codes. P51G is broken down by asset in the
    table; only the total (N11G, fixed assets) is taken. D4 received (the
    resources side) is D4R."""
    t = t[(t.SECTOR == 'S11') & (t.UNIT_MEASURE == 'XDC') & (t.PRICE_BASE == 'V')
          & t.INSTR_ASSET.isin(['_Z', 'N11G'])]
    # uses (D) for payments, investment and balances; B9 and B1G appear on the uses side
    want = {('D42', 'D'), ('B2A3G', 'D'), ('B2G', 'D'), ('B1G', 'D'), ('B9', 'D'), ('P51G', 'D'), ('D4', 'C')}
    t = t[[(a, b) in want for a, b in zip(t.TRANSACTION, t.ACCOUNTING_ENTRY)]]
    t = t.assign(TRANSACTION=np.where((t.TRANSACTION == 'D4') & (t.ACCOUNTING_ENTRY == 'C'), 'D4R', t.TRANSACTION))
    p = t.pivot_table(index=['REF_AREA', 'TIME_PERIOD'], columns='TRANSACTION', values='OBS_VALUE', aggfunc='first')
    p.index.names = ['iso', 'year']
    return p


def oecd_s11():
    cols = ['REF_AREA', 'SECTOR', 'ACCOUNTING_ENTRY', 'TRANSACTION', 'INSTR_ASSET', 'UNIT_MEASURE',
            'PRICE_BASE', 'TIME_PERIOD', 'OBS_VALUE']
    path = RAW('econ', 'oecd-sna-sector-accounts', 'DF_TABLE14_all.csv.gz')
    head = pd.read_csv(path, nrows=0).columns
    use = [c for c in head if c.split(':')[0] in cols]
    t = pd.read_csv(path, usecols=use)
    t.columns = [c.split(':')[0] for c in t.columns]
    for c in ['REF_AREA', 'SECTOR', 'ACCOUNTING_ENTRY', 'TRANSACTION', 'INSTR_ASSET', 'UNIT_MEASURE', 'PRICE_BASE']:
        t[c] = t[c].str.split(':').str[0]
    return s11_pivot(t)


def eurostat_s11(iso2):
    rows = []
    with io.TextIOWrapper(__import__('gzip').open(RAW('econ', 'eurostat-nasa-10-nf-tr', 'nasa_10_nf_tr.tsv.gz')),
                          encoding='utf-8') as f:
        header = f.readline().rstrip('\n').split('\t')
        years = [int(h.strip()) for h in header[1:]]
        for line in f:
            parts = line.rstrip('\n').split('\t')
            freq, unit, direct, item, sector, geo = parts[0].split(',')
            if sector != 'S11' or unit != 'CP_MNAC':
                continue
            if direct == 'PAID' and item in ('D42', 'B2A3G', 'B1G', 'B9', 'P51G'):
                name = item
            elif direct == 'RECV' and item == 'D4':
                name = 'D4R'
            else:
                continue
            for y, v in zip(years, parts[1:]):
                v = re.sub(r'[^0-9.\-eE]', '', v.strip())
                if v:
                    rows.append((iso2.get(geo), y, name, float(v)))
    e = pd.DataFrame(rows, columns=['iso', 'year', 'item', 'v']).dropna()
    p = e.pivot_table(index=['iso', 'year'], columns='item', values='v', aggfunc='first')
    return p


def z1_buybacks():
    """US NFC net buybacks (minus net equity issuance, FU103164105.A), by year."""
    z = zipfile.ZipFile(RAW('econ', 'fed-z1', 'z1_csv_files.zip'))
    a = pd.read_csv(z.open('csv/S11_1_i_a.csv'), na_values=['ND'])
    a = a.loc[:, ~a.columns.duplicated()]
    s = a[['date', 'FU103164105.A']].dropna()
    return {int(d): -float(v) for d, v in zip(s.date, s['FU103164105.A'])}


def nfc_indicators(iso2, countries):
    o = oecd_s11()
    e = eurostat_s11(iso2)
    # OECD first; Eurostat for country-years OECD lacks (whole rows, so one source per ratio)
    o_ok = o.dropna(how='all')
    e = e[~e.index.isin(o_ok.index)]
    p = pd.concat([o_ok, e])
    p = p[p.index.get_level_values('iso').isin(countries)]
    os_ = p['B2A3G'].fillna(p.get('B2G')) if 'B2G' in p else p['B2A3G']
    bb = z1_buybacks()
    iso = p.index.get_level_values('iso')
    yr = p.index.get_level_values('year')
    buy = np.array([bb.get(y, np.nan) if i == 'USA' else 0.0 for i, y in zip(iso, yr)])
    # the US accounts are in USD millions in both sources
    payout = (p['D42'] + buy) / os_
    out = []
    ok = os_ > 0
    out.append(frame(iso[ok], yr[ok], payout[ok].values, FIN, 'nfc-payout', LAG['sna']))
    # all four over the same denominator, gross operating surplus: Japan has no
    # S11 value added (B1G) in Table 0800, and it is one of the comparisons
    out.append(frame(iso[ok], yr[ok], (p['B9'] / os_)[ok].values, FIN, 'nfc-net-lending', LAG['sna']))
    out.append(frame(iso[ok], yr[ok], (p['P51G'] / os_)[ok].values, FIN, 'nfc-inv-profit', LAG['sna']))
    out.append(frame(iso[ok], yr[ok], (p['D4R'] / os_)[ok].values, FIN, 'nfc-property-income', LAG['sna']))
    return out


def structure_rows(s, variable, countries):
    """fin-va-share rows from econ-structure.csv's `variable`, with the data
    year and the year known. The file has had two layouts: `year` the data
    year with a `known` column, and the stocks convention, `year` the year
    known with a `data_year` column. A file with neither is read as the
    stocks convention (year = known, data year unknown, so the same)."""
    r = s[(s.variable == variable) & s.iso.isin(countries)]
    if 'known' in r.columns:
        data_year, known = r.year, r.known
    elif 'data_year' in r.columns:
        data_year, known = r.data_year, r.year
    else:
        data_year, known = r.year, r.year
    d = pd.DataFrame({'iso': r.iso.values, 'year': data_year.values.astype(int),
                      'known': known.values.astype(int), 'value': r.value.values.astype(float)})
    d = d[np.isfinite(d.value)]
    d['concept'] = FIN
    d['indicator'] = 'fin-va-share'
    return d[['iso', 'year', 'known', 'concept', 'indicator', 'value']]


def fin_va(countries):
    """The finance VA share: econ-structure.csv's when it exists (the hook),
    else OECD Table 6 K over _T, current prices."""
    if os.path.exists(STRUCTURE):
        s = pd.read_csv(STRUCTURE)
        names = [v for v in s.variable.unique() if re.search(r'fin', v) and re.search(r'va|share', v)]
        if names:
            v = sorted(names, key=len)[0]
            print(f'fin-va-share from {STRUCTURE} ({v})', flush=True)
            return [structure_rows(s, v, countries)]
    cols = ['REF_AREA', 'ACTIVITY', 'TRANSACTION', 'PRICE_BASE', 'UNIT_MEASURE', 'TIME_PERIOD', 'OBS_VALUE']
    head = pd.read_csv(RAW('econ', 'oecd-sna-sector-accounts', 'DF_TABLE6_all.csv.gz'), nrows=0).columns
    use = [c for c in head if c.split(':')[0] in cols]
    t = pd.read_csv(RAW('econ', 'oecd-sna-sector-accounts', 'DF_TABLE6_all.csv.gz'), usecols=use)
    t.columns = [c.split(':')[0] for c in t.columns]
    for c in ['REF_AREA', 'ACTIVITY', 'TRANSACTION', 'PRICE_BASE', 'UNIT_MEASURE']:
        t[c] = t[c].str.split(':').str[0]
    t = t[(t.TRANSACTION == 'B1G') & (t.PRICE_BASE == 'V') & (t.UNIT_MEASURE == 'XDC') & t.ACTIVITY.isin(['K', '_T'])]
    p = t.pivot_table(index=['REF_AREA', 'TIME_PERIOD'], columns='ACTIVITY', values='OBS_VALUE', aggfunc='first').dropna()
    p = p[(p['_T'] > 0) & p.index.get_level_values(0).isin(countries)]
    return [frame(p.index.get_level_values(0), p.index.get_level_values(1), (p['K'] / p['_T']).values,
                  FIN, 'fin-va-share', LAG['sna'])]


# ---------------------------------------------------------------- WID, ICSD, PWT

def wid(iso2):
    z = zipfile.ZipFile(RAW('slow', 'wid', 'wid_all_data.zip'))
    rows = []
    for n in z.namelist():
        m = re.fullmatch(r'WID_data_([A-Z]{2})\.csv', n)
        if not m or m.group(1) not in iso2:
            continue
        for line in io.TextIOWrapper(z.open(n), encoding='utf-8'):
            if 'weali999;p0p100;' not in line:
                continue
            c, var, pct, y, v = line.split(';')[:5]
            if var in ('wpweali999', 'wgweali999') and v:
                rows.append((iso2[c], var, int(y), float(v)))
    w = pd.DataFrame(rows, columns=['iso', 'var', 'year', 'v'])
    w = w[w.year >= 1960]
    p = w[w['var'] == 'wpweali999']
    g = w[w['var'] == 'wgweali999']
    return [frame(p.iso, p.year, p.v, FIN, 'private-wealth-ni', LAG['wid']),
            frame(g.iso, g.year, g.v, INV, 'public-wealth-ni', LAG['wid'])]


def icsd():
    t = pd.read_csv(RAW('econ', 'imf-sdmx-holdings-reserves', 'ICSD.csv.gz'),
                    usecols=['COUNTRY', 'INDICATOR', 'TIME_PERIOD', 'OBS_VALUE'])
    p = t.pivot_table(index=['COUNTRY', 'TIME_PERIOD'], columns='INDICATOR', values='OBS_VALUE', aggfunc='first')
    out = []
    # current prices. ICSD gives PPP investment only in volume terms
    # (P51G_PUPVT_Q_*), so the investment split leaves PPPs out; the capital
    # split has them. The constant-price % of GDP forms are not used: at 2017
    # prices they misstate past shares where the investment deflator moved
    # (China's 1995 private investment reads 4.5% of GDP).
    for name, base in [('public-k-share', 'CAPSTCK'), ('gov-inv-share', 'P51G')]:
        g = p[f'{base}_S13_V_XDC']
        pv = p[f'{base}_PS_V_XDC']
        ppp = p.get(f'{base}_PUPVT_V_XDC', pd.Series(0.0, index=p.index)).fillna(0.0)
        tot = g + pv + ppp
        s = (g / tot)[(tot > 0) & g.notna() & pv.notna()]
        out.append(frame(s.index.get_level_values(0), s.index.get_level_values(1), s.values, INV, name, LAG['imf']))
    return out


def current_account():
    """Current account / GDP (%), WDI BN.CAB.XOKA.GD.ZS (8a's regime list)."""
    w = json.load(open(RAW('slow', 'worldbank', 'BN.CAB.XOKA.GD.ZS.json')))[1]
    d = pd.DataFrame([(x['countryiso3code'], int(x['date']), x['value']) for x in w
                      if x['value'] is not None and x['countryiso3code']], columns=['iso', 'year', 'v'])
    return frame(d.iso, d.year, d.v, INV, 'current-account', LAG['wdi'])


def household_share():
    """Household consumption / GDP (%), GMD hcons_GDP (8a: loads negatively
    on the regime), forecast rows dropped. GMD is a 2026 vintage compiled
    from WDI, WEO and national sources: known +1, as WDI."""
    import pyreadstat
    g, _ = pyreadstat.read_dta(RAW('slow', 'fiscal', 'GMD_2026_09.dta'),
                               usecols=['ISO3', 'year', 'hcons_GDP', 'forecast_hcons_GDP'])
    g = g[(g.forecast_hcons_GDP == 0) & (g.year >= 1960)]
    return frame(g.ISO3, g.year, g.hcons_GDP, INV, 'hh-cons-share', LAG['wdi'])


def pwt():
    p = pd.read_csv(RAW('slow', 'pwt', 'pwt110.csv'))
    out = [frame(p.countrycode, p.year, p.csh_i, INV, 'inv-gdp', LAG['pwt']),
           frame(p.countrycode, p.year, p.irr, INV, 'irr', LAG['pwt']),
           frame(p.countrycode, p.year, np.log(p.rgdpna / p['pop']), AUX, 'log-gdp-pc', LAG['pwt'])]
    # PWT's China labour share is imputed for 1970-2000 (doc 7e); labsh is not used here.
    return out


# ---------------------------------------------------------------- main

def coverage(d):
    lines = ['indicator coverage (countries, data years, rows; known = data year + lag)', '']
    for (c, i), g in d.groupby(['concept', 'indicator']):
        lines.append(f'{c:18s} {i:20s} {g.iso.nunique():4d} countries  {g.year.min()}-{g.year.max()}  '
                     f'{len(g):6d} rows  lag {int((g.known - g.year).iloc[0])}')
    lines.append('')
    lines.append('countries with at least 3 indicators of the concept in some data year:')
    for c, g in d[d.concept != AUX].groupby('concept'):
        k = g.groupby(['iso', 'year']).indicator.nunique()
        lines.append(f'  {c}: {k[k >= 3].index.get_level_values(0).nunique()}')
    return '\n'.join(lines)


def build():
    iso2 = iso2_map()
    pw = pwt()
    countries = set(pw[0].iso) | real_countries() | {'TWN'}
    parts = []
    parts += market_indicators(countries)
    parts.append(fd_index())
    parts += nfc_indicators(iso2, countries)
    parts += fin_va(countries)
    parts += wid(iso2)
    parts += icsd()
    parts.append(current_account())
    parts.append(household_share())
    parts += pw
    d = pd.concat(parts, ignore_index=True)
    d = d[d.iso.isin(countries)].sort_values(['iso', 'concept', 'indicator', 'year'])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    d.to_csv(OUT, index=False)
    c = coverage(d)
    open(COV, 'w').write(c + '\n')
    print(c)
    print(len(d), 'rows to', OUT)


def selftest():
    """No value is dated before its known year, in either layout of
    econ-structure.csv, nor in the assembled table."""
    v = 'econ/finance-va-share'
    old = pd.DataFrame({'iso': ['USA', 'USA'], 'year': [2015, 2016], 'variable': [v, v],
                        'value': [0.07, 0.08], 'known': [2018, 2019]})
    d = structure_rows(old, v, {'USA'})
    assert list(d.year) == [2015, 2016] and list(d.known) == [2018, 2019], d
    new = pd.DataFrame({'iso': ['USA'], 'year': [2018], 'data_year': [2015], 'variable': [v], 'value': [0.07]})
    d = structure_rows(new, v, {'USA'})
    assert list(d.year) == [2015] and list(d.known) == [2018], d
    # the S11 pivot takes P51G on total fixed assets (N11G), not a breakdown
    # by asset, and D4 received as D4R
    toy = pd.DataFrame({
        'REF_AREA': ['ITA'] * 7, 'TIME_PERIOD': [2015] * 7, 'SECTOR': ['S11'] * 7,
        'UNIT_MEASURE': ['XDC'] * 7, 'PRICE_BASE': ['V'] * 7,
        'TRANSACTION': ['P51G', 'P51G', 'P51G', 'B2A3G', 'D4', 'D4', 'D42'],
        'ACCOUNTING_ENTRY': ['D', 'D', 'D', 'D', 'C', 'D', 'D'],
        'INSTR_ASSET': ['N111G', 'N11G', 'N112G', '_Z', '_Z', '_Z', '_Z'],
        'OBS_VALUE': [2.0, 150.0, 30.0, 300.0, 40.0, 60.0, 140.0]})
    pv = s11_pivot(toy)
    assert pv.loc[('ITA', 2015), 'P51G'] == 150.0, pv
    assert pv.loc[('ITA', 2015), 'D4R'] == 40.0, pv
    assert pv.loc[('ITA', 2015), 'D42'] == 140.0, pv
    if os.path.exists(OUT):
        t = pd.read_csv(OUT)
        assert (t.known >= t.year).all(), t[t.known < t.year].head()
        if os.path.exists(STRUCTURE):
            s = pd.read_csv(STRUCTURE)
            k = 'known' if 'known' in s.columns else 'year'
            src = s[s.variable.str.contains('fin')].groupby('iso')[k].min()
            got = t[t.indicator == 'fin-va-share'].groupby('iso').known.min()
            j = pd.concat([src, got], axis=1, join='inner')
            assert (j.iloc[:, 1] >= j.iloc[:, 0]).all(), j[j.iloc[:, 1] < j.iloc[:, 0]]
    print('selftest ok')


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'test':
        selftest()
    else:
        build()
        selftest()
