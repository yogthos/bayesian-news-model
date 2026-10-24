"""Control-layer indicators per country-year (bead bnm-4hv.2;
docs/COUPLED-SYSTEMS.md 4a, the C subsystem). src/bnm/control.clj fits the
latent control vector on them; this script assembles inputs and, with
`check`, runs the descriptive validation on the fitted stocks.

Writes data/derived/control/control-indicators.csv, long:
  iso, year (the data year), known (the first year the value could be
  read), indicator, value
and data/derived/control/control-indicators-coverage.txt.

Known years are each source's release lag; the values are today's
vintages read as if published then (the caveat every stock file carries):
  Panizza bank ownership +2; BRSS the wave's release year (2021 for the
  2019 wave, read from the corrected release); V-Dem +1; ICSD and WID +2
  (as scripts/econ-factors.py); KAOPEN and FKRSU +2 (the 2023 KAOPEN and
  the 2021 FKRSU update came out two years after their last year);
  expropriation acts +1 (an act is reported when it happens, but the
  window to the end of y is complete only after y; the channel table's
  P3; the compilation itself is later); OECD PMR +2; PWT +3; the Bundesbank's banking groups and WDI
  rates +1.

Indicators (the doc's stock in brackets):
  state-bank-share  [c-bank] government-owned banks' share of banking
                    assets, 0-1. Panizza (BOATW) SOE3: banks the state owns
                    50% or more of, development banks excluded, which is
                    BRSS's definition; BRSS fills country-years Panizza
                    lacks (waves c.1999, 2001, 2005, 2008-10, 2011-16).
                    No level adjustment: one would need the overlap, which
                    for early fills lies in the future. Germany is the
                    Bundesbank's Landesbanken and savings banks over all
                    banks (balance sheet totals, December, +1, 1995 on),
                    replacing both: Panizza codes them only in part.
  state-own         [c-own] V-Dem v2clstown negated, so high = more of the
                    economy owned or directly controlled by the state.
  public-k-share    [c-pubk] general government's share of the capital
                    stock, IMF ICSD (from data/derived/econ/econ-indicators.
                    csv). Weak alone: ICSD holds splits constant where it
                    has no data, and its private sector includes SOEs.
  public-wealth-ni  [c-pubk] net public wealth / national income, WID
                    (same file).
  ka-open           [c-open] Chinn-Ito KAOPEN normalised, 0-1, 1970-2023.
  exprop-10y        log(1 + expropriation acts in the ten years to y), from
                    Esberg & Perlman's compilation of Kobrin, Minor and
                    Hajzler (1960-2014): y from 1969 to 2014, zero where a
                    country has no act. The sources cover developing
                    countries only (France's 1982 bank nationalisations are
                    not in it).
  pmr-public-owner  OECD PMR 'quality and scope of public ownership', 0-6,
                    2018 and 2023 only (57 economies).
  top1-wealth       [c-conc] WID top 1% share of net personal wealth
                    (shwealj992 p99p100), rows WID rates as measured
                    (data_quality >= 1) only: the rest are regional
                    imputations.
  repression        [c-repr] the financial repression gap: real GDP growth
                    less the real deposit rate (Fisher), points, the mean
                    of the five years to y (at least three, y among them).
                    WDI FR.INR.DPST, FP.CPI.TOTL.ZG, NY.GDP.MKTP.KD.ZG,
                    +1. Deposit rates are national definitions; the euro
                    area's members have none after 1998.
  corruption        [c-capt] V-Dem political corruption index v2x_corr.
Written as stocks but not in the factor:
  ka-restrict       FKRSU capital-control restrictions, mean of the 0/1
                    asset-class by direction codes (2021 update, 1995-2019,
                    100 countries). Kept out of the factor: with KAOPEN it
                    would be one measure twice; their correlation is in the
                    coverage file.
  execorr, pubcorr  V-Dem executive and public-sector corruption.
  bank-brss         the BRSS share alone (the splice's second source).
Auxiliary (validation only): log-gdp-pc (PWT, +3).

  python -I scripts/control.py          # assemble the indicators
  python -I scripts/control.py test     # self-test: known years, units
  python -I scripts/control.py check    # validation of the fitted stocks
"""
import os
import sys
import warnings

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda *p: os.path.join(ROOT, 'data', *p)
RAW = lambda *p: D('raw', *p)
OUT = D('derived', 'control', 'control-indicators.csv')
COV = D('derived', 'control', 'control-indicators-coverage.txt')
ECON = D('derived', 'econ', 'econ-indicators.csv')
STOCKS = D('derived', 'stocks', 'control.csv')
CHECK = D('derived', 'control', 'control-check.txt')
FIXED = D('derived', 'control', 'control-fixed.csv')
VARIANTS = D('derived', 'control', 'control-variants.csv')

LAG = {'panizza': 2, 'vdem': 1, 'kaopen': 2, 'fkrsu': 2, 'exprop': 1, 'pmr': 2, 'bbk': 1, 'wdi': 1}
# BRSS: data year -> the wave's release year (SOURCE.txt)
# (the 2019 wave's values are read from the April 2021 corrected release)
BRSS_KNOWN = {1999: 2001, 2001: 2004, 2005: 2008, 2008: 2012, 2009: 2012, 2010: 2012,
              2011: 2021, 2012: 2021, 2013: 2021, 2014: 2021, 2015: 2021, 2016: 2021}

FACTOR = ['state-bank-share', 'state-own', 'public-k-share', 'public-wealth-ni', 'ka-open',
          'exprop-10y', 'pmr-public-owner', 'repression', 'top1-wealth', 'corruption']


# ---------------------------------------------------------------- helpers

def frame(iso, year, value, indicator, lag=None, known=None):
    d = pd.DataFrame({'iso': np.asarray(iso), 'year': np.asarray(year), 'value': np.asarray(value, dtype=float)})
    if known is not None:
        d['known'] = np.asarray(known)
    d = d.dropna()
    d = d[np.isfinite(d.value)]
    d['year'] = d.year.astype(int)
    d['known'] = (d.year + lag) if known is None else d.known.astype(int)
    d['indicator'] = indicator
    return d[['iso', 'year', 'known', 'indicator', 'value']]


_ALIAS = {
    'bosnia and herzegovina': 'BIH', 'bosnia & herzegovina': 'BIH', 'congo, dem. rep.': 'COD',
    'congo, democratic republic of': 'COD', 'democratic republic of congo': 'COD', 'congo, rep.': 'COG',
    'congo, republic of': 'COG', 'congo': 'COG', 'republic of congo': 'COG', 'korea': 'KOR', 'korea, rep.': 'KOR',
    'korea, republic of': 'KOR', 'south korea': 'KOR', 'korea (south)': 'KOR', 'republic of korea': 'KOR',
    'russia': 'RUS', 'russian federation': 'RUS', 'kyrgyz republic': 'KGZ', 'kyrgyzstan': 'KGZ',
    'macedonia': 'MKD', 'macedonia, fyr': 'MKD', 'fyr macedonia': 'MKD', 'north macedonia': 'MKD',
    'iran': 'IRN', 'iran, islamic rep.': 'IRN', 'turkey': 'TUR', 'turkiye': 'TUR', 'vietnam': 'VNM',
    'viet nam': 'VNM', 'taiwan': 'TWN', 'taiwan, china': 'TWN', 'chinese taipei': 'TWN', 'kosovo': 'XKX',
    'cote d\'ivoire': 'CIV', "côte d'ivoire": 'CIV', 'ivory coast': 'CIV', 'egypt': 'EGY',
    'egypt, arab rep.': 'EGY', 'gambia': 'GMB', 'gambia, the': 'GMB', 'the gambia': 'GMB', 'bahamas': 'BHS',
    'bahamas, the': 'BHS', 'the bahamas': 'BHS', 'hong kong': 'HKG', 'hong kong sar': 'HKG',
    'hong kong, china': 'HKG', 'hong kong sar, china': 'HKG', 'macao': 'MAC', 'macau': 'MAC',
    'macao sar, china': 'MAC', 'lao pdr': 'LAO', 'laos': 'LAO', 'moldova': 'MDA', 'slovak republic': 'SVK',
    'slovakia': 'SVK', 'czech republic': 'CZE', 'czechia': 'CZE', 'venezuela': 'VEN', 'venezuela, rb': 'VEN',
    'bolivia': 'BOL', 'tanzania': 'TZA', 'syria': 'SYR', 'yemen': 'YEM', 'yemen, rep.': 'YEM',
    'st. lucia': 'LCA', 'saint lucia': 'LCA', 'st. kitts and nevis': 'KNA', 'st kitts and nevis': 'KNA',
    'saint kitts and nevis': 'KNA', 'st. vincent and the grenadines': 'VCT', 'st vincent and the grenadines': 'VCT',
    'saint vincent and the grenadines': 'VCT', 'micronesia': 'FSM', 'swaziland': 'SWZ', 'eswatini': 'SWZ',
    'cape verde': 'CPV', 'cabo verde': 'CPV', 'east timor': 'TLS', 'timor-leste': 'TLS', 'burma': 'MMR',
    'myanmar': 'MMR', 'brunei': 'BRN', 'brunei darussalam': 'BRN', 'palestine': 'PSE', 'west bank and gaza': 'PSE',
    'united states': 'USA', 'united states of america': 'USA', 'usa': 'USA', 'united kingdom': 'GBR', 'uk': 'GBR',
    'netherlands antilles': 'ANT', 'curacao': 'CUW', 'curaçao': 'CUW', 'sint maarten': 'SXM',
    'guernsey': 'GGY', 'jersey': 'JEY', 'isle of man': 'IMN', 'serbia and montenegro': 'SRB',
    'yugoslavia': 'SRB', 'uae': 'ARE', 'united arab emirates': 'ARE', 'micronesia, fed. sts.': 'FSM',
    'the netherlands': 'NLD', 'netherlands': 'NLD', 'british virgin islands': 'VGB',
    'virgin islands (british)': 'VGB', 'cayman islands': 'CYM', 'west african monetary union': None,
    'eastern caribbean currency union': None, 'ecuador ': 'ECU', 'central african rep.': 'CAF',
    'dominican rep.': 'DOM', 'azerbajan': 'AZE', 'bosnia-herzegovina': 'BIH',
    'british virgin islands, the': 'VGB', "china-people's rep.": 'CHN', 'guinea bissau': 'GNB', 'lituania': 'LTU',
    'macao, china': 'MAC', 'macau, china': 'MAC', 'macedonia, republic of': 'MKD', 'palestinian territory': 'PSE',
    'salvador, el': 'SLV', 'samoa (western)': 'WSM', 'serbia & montenegro': 'SRB', 'st. kitts': 'KNA',
    'trinidad & tobago': 'TTO', 'sao tome and principe': 'STP', 'são tomé and príncipe': 'STP',
}


def name_iso(name):
    """ISO3 of a country name (BRSS headers); None when unknown."""
    import pycountry
    if not isinstance(name, str):
        return None
    n = ' '.join(name.replace('\n', ' ').split()).strip()
    k = n.lower().rstrip('*').strip()
    if k in _ALIAS:
        return _ALIAS[k]
    for f in (lambda: pycountry.countries.get(name=n), lambda: pycountry.countries.get(common_name=n),
              lambda: pycountry.countries.get(official_name=n)):
        c = f()
        if c:
            return c.alpha_3
    try:
        c = pycountry.countries.search_fuzzy(n)
        return c[0].alpha_3 if len(c) == 1 else None
    except LookupError:
        return None


def countries():
    """ISO3 codes of current countries (pycountry), plus Kosovo."""
    import pycountry
    return {c.alpha_3 for c in pycountry.countries} | {'XKX'}


def share(v):
    """A BRSS answer as a 0-1 share, or NaN: numbers, '31,9%', 'Not Available'."""
    if isinstance(v, (int, float, np.floating, np.integer)):
        return float(v)
    if not isinstance(v, str):
        return np.nan
    s = v.strip().replace(',', '.').replace('%', '').strip()
    try:
        return float(s)
    except ValueError:
        return np.nan


# ---------------------------------------------------------------- bank ownership

def panizza():
    p = pd.read_excel(RAW('econ', 'panizza-bank-ownership', 'BOATW20230331.xlsx'), sheet_name='data')
    return frame(p.ISO, p.year, p.SOE3, 'state-bank-share', LAG['panizza'])


def brss_wide(path, sheet, header_row, key_col, keys, first_col):
    """{(iso, data_year): share} from a wide BRSS sheet: countries along
    `header_row` from `first_col`, the question's rows found by `key_col`
    matching `keys` ({row key: data year})."""
    s = pd.read_excel(RAW('econ', 'wb-brss', path), sheet_name=sheet, header=None)
    names = s.iloc[header_row]
    out = []
    for _, row in s.iterrows():
        k = str(row.iloc[key_col]).strip()
        if k in keys:
            for c in range(first_col, s.shape[1]):
                iso = name_iso(names.iloc[c])
                if iso:
                    out.append((iso, keys[k], share(row.iloc[c])))
    return out


def brss():
    """The BRSS waves' government-owned share of banking assets, 0-1."""
    rows = []
    # 2019 wave: percent, one row per year 2011-2016
    for iso, y, v in brss_wide('2021_04_26_brss-public-release.xlsx', '13', 0, 0,
                               {f'Q13_7_1_{y}': y for y in range(2011, 2017)}, 2):
        rows.append((iso, y, v / 100.0))
    # 2011 wave: fractions, 2008-2010
    rows += brss_wide('brss-bank-regulation.xlsx', 'BRSS 2011', 0, 0,
                      {'q13_07_01_01': 2008, 'q13_07_01_02': 2009, 'q13_07_01_03': 2010}, 2)
    # 2007 wave (Survey III): fractions, year-end 2005
    rows += brss_wide('banking_regulation_survey_iii_061008.xls', 'III', 0, 0, {'3.8.1': 2005}, 3)
    # 2003 wave (Survey II): fractions, year-end 2001
    rows += brss_wide('caprio_2003_banking_regulation_database_0_0.xls', 'Cleaned 3-5-04', 0, 0, {'3.8.1': 2001}, 3)
    # 2001 wave (Survey I): percent, column 8 of '3-capital', countries down column 0, data c.1999
    s = pd.read_excel(RAW('econ', 'wb-brss', 'caprio_2000_banking_regulation_database_0.xls'),
                      sheet_name='3-capital', header=None)
    assert 'government' in str(s.iloc[0, 8]).lower(), s.iloc[0, 8]
    for _, row in s.iloc[2:].iterrows():
        iso = name_iso(row.iloc[0])
        if iso:
            rows.append((iso, 1999, share(row.iloc[8]) / 100.0))
    d = pd.DataFrame(rows, columns=['iso', 'year', 'value']).dropna()
    d = d[(d.value >= 0) & (d.value <= 1)].drop_duplicates(['iso', 'year'])
    return frame(d.iso, d.year, d.value, 'bank-brss', known=d.year.map(BRSS_KNOWN))


def bbk_december(lines):
    """{year: value} of the December observations of a Bundesbank CSV
    download (lines of text), missing ('-') left out."""
    out = {}
    import csv
    for r in csv.reader(lines):
        if len(r) >= 2 and len(r[0]) == 7 and r[0][4] == '-' and r[0].endswith('-12'):
            try:
                out[int(r[0][:4])] = float(r[1])
            except ValueError:
                pass
    return out


def germany_banks(total, landesbanken, sparkassen):
    """Germany's state bank share: Landesbanken and savings banks over all
    categories of banks (balance sheet totals, {year: value}), known a year
    later."""
    ys = sorted(set(total) & set(landesbanken) & set(sparkassen))
    v = [(landesbanken[y] + sparkassen[y]) / total[y] for y in ys]
    return frame(['DEU'] * len(ys), ys, v, 'state-bank-share', LAG['bbk'])


BANK_FIRST = 1995  # Panizza's first year: before it Germany would be the bank share's only country


def bundesbank():
    p = lambda k: open(RAW('econ', 'bbk-banking-groups', f'BBBK1.M.{k}.csv'), encoding='utf-8-sig').read().splitlines()
    d = germany_banks(bbk_december(p('OU0308')), bbk_december(p('OU1024')), bbk_december(p('OU1074')))
    return d[d.year >= BANK_FIRST]


def bank_share(pz, br, de=None):
    """Panizza where it has the country-year, BRSS where it does not; for
    Germany the Bundesbank's banking groups (`de`) alone: Panizza codes
    the Landesbanken and savings banks only in part (its share falls from
    0.2 to 0.015 by 2012, while BRSS reports 0.37-0.40 and the Bundesbank's
    groups 0.29-0.36)."""
    have = set(zip(pz.iso, pz.year))
    fill = br[[(i, y) not in have for i, y in zip(br.iso, br.year)]].copy()
    fill['indicator'] = 'state-bank-share'
    d = pd.concat([pz, fill], ignore_index=True)
    if de is not None:
        d = pd.concat([d[d.iso != 'DEU'], de], ignore_index=True)
    return d


# ---------------------------------------------------------------- repression

def wdi_zip(code):
    import io
    import zipfile
    z = zipfile.ZipFile(RAW('econ', 'wdi-finance-structure', f'{code}.zip'))
    name = [n for n in z.namelist() if n.startswith('API_')][0]
    w = pd.read_csv(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'), skiprows=4)
    w = w.melt(id_vars=['Country Code'], value_vars=[c for c in w.columns if c.isdigit()], var_name='year', value_name='v')
    return w.rename(columns={'Country Code': 'iso'}).assign(year=lambda d: d.year.astype(int)).dropna()


def wdi_json(code):
    import json
    w = json.load(open(RAW('slow', 'worldbank', f'{code}.json')))[1]
    d = pd.DataFrame([(x['countryiso3code'], int(x['date']), x['value']) for x in w if x['countryiso3code']],
                     columns=['iso', 'year', 'v'])
    return d.dropna()


def repression(dep, cpi, growth, window=5, min_years=3):
    """[c-repr] the financial repression gap: real GDP growth less the real
    deposit rate (Fisher: (1 + i) / (1 + inflation) - 1), in points, the
    mean over the `window` years to y of those it has (at least
    `min_years`, y among them). High = deposits paid well below growth.
    Inputs are frames iso, year, v in percent (WDI FR.INR.DPST,
    FP.CPI.TOTL.ZG, NY.GDP.MKTP.KD.ZG); known a year later."""
    m = dep.merge(cpi, on=['iso', 'year'], suffixes=('_i', '_p')).merge(growth.rename(columns={'v': 'v_g'}), on=['iso', 'year'])
    m = m.dropna()
    m = m[m.v_p > -100]
    m['gap'] = m.v_g - 100.0 * ((1 + m.v_i / 100.0) / (1 + m.v_p / 100.0) - 1)
    rows = []
    for iso, g in m.groupby('iso'):
        s = g.set_index('year').gap
        for y in s.index:
            w = s[(s.index > y - window) & (s.index <= y)]
            if len(w) >= min_years:
                rows.append((iso, y, w.mean()))
    d = pd.DataFrame(rows, columns=['iso', 'year', 'value'])
    return frame(d.iso, d.year, d.value, 'repression', LAG['wdi'])


# ---------------------------------------------------------------- V-Dem

def vdem():
    import rdata
    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        v = rdata.conversion.convert(rdata.parser.parse_file(RAW('political', 'vdem-full-v16', 'vdem.RData')))['vdem']
    v = v[['country_text_id', 'year', 'v2clstown', 'v2x_corr', 'v2x_execorr', 'v2x_pubcorr']]
    v = v[v.year >= 1950]
    iso, y = v.country_text_id, v.year
    return [frame(iso, y, -v.v2clstown, 'state-own', LAG['vdem']),
            frame(iso, y, v.v2x_corr, 'corruption', LAG['vdem']),
            frame(iso, y, v.v2x_execorr, 'execorr', LAG['vdem']),
            frame(iso, y, v.v2x_pubcorr, 'pubcorr', LAG['vdem'])]


# ---------------------------------------------------------------- the rest

def econ_indicators():
    """public-k-share, public-wealth-ni and log-gdp-pc from scripts/
    econ-factors.py's table (run it first)."""
    if not os.path.exists(ECON):
        sys.exit(f'{ECON} missing: run python -I scripts/econ-factors.py first')
    e = pd.read_csv(ECON)
    e = e[e.indicator.isin(['public-k-share', 'public-wealth-ni', 'log-gdp-pc'])]
    return e[['iso', 'year', 'known', 'indicator', 'value']]


def kaopen():
    k = pd.read_csv(RAW('slow', 'kaopen', 'kaopen.csv'))
    return frame(k.ccode, k.year, k.ka_open, 'ka-open', LAG['kaopen'])


FKRSU_COLS = [f'{a}_{d}' for a in ['eq', 'bo', 'mm', 'ci', 'de'] for d in ['plbn', 'siln', 'pabr', 'siar']] + \
             ['cci', 'cco', 'fci', 'fco', 'gsi', 'gso', 'dii', 'dio', 'ldi', 're_pabr', 're_plbn', 're_slbn']


def fkrsu():
    f = pd.read_stata(RAW('econ', 'fkrsu-capital-controls', '2021-FKRSU-Update-12-08-2021.dta'))
    cols = [c for c in FKRSU_COLS if c in f.columns]
    v = f[cols].astype(float)
    ka = v.mean(axis=1).where(v.notna().sum(axis=1) >= len(cols) // 2)
    return frame(f.code_wdi, f.Year, ka, 'ka-restrict', LAG['fkrsu'])


def exprop_acts():
    e = pd.read_csv(RAW('political', 'expropriation-covert-confiscation', 'extracted',
                        'exprop_dem_gdp_2021updated.csv'), encoding='latin-1', usecols=['year', 'country', 'Country.ISO3', 'wdicode'])
    e = e.dropna(subset=['year'])
    iso = e['Country.ISO3'].fillna(e.wdicode).replace({'ZAR': 'COD', 'ROM': 'ROU', 'TMP': 'TLS'})
    return pd.DataFrame({'iso': iso, 'year': e.year.astype(int)}).dropna()


def exprop(acts, universe, first=1969, last=2014, window=10):
    """log(1 + acts in [y - window + 1, y]) for every country of `universe`
    and y in [first, last]."""
    c = acts.groupby(['iso', 'year']).size()
    rows = []
    for iso in sorted(universe | set(acts.iso)):
        per = c[iso] if iso in c.index.get_level_values(0) else pd.Series(dtype=float)
        for y in range(first, last + 1):
            n = per[(per.index > y - window) & (per.index <= y)].sum() if len(per) else 0
            rows.append((iso, y, np.log1p(n)))
    d = pd.DataFrame(rows, columns=['iso', 'year', 'value'])
    return frame(d.iso, d.year, d.value, 'exprop-10y', LAG['exprop'])


def pmr():
    parts = []
    for f in ['DF_PMR_all.csv.gz', 'DF_PMR_WBG_all.csv.gz']:
        p = pd.read_csv(RAW('econ', 'oecd-pmr-soe', f))
        p = p[p['MEASURE: Measure'].str.startswith('PUBLIC_OWNER:')]
        parts.append(frame(p['REF_AREA: Reference area'].str[:3], p['TIME_PERIOD: Time period'], p.OBS_VALUE,
                           'pmr-public-owner', LAG['pmr']))
    return pd.concat(parts).drop_duplicates(['iso', 'year'])


def wid_top1():
    import pycountry
    w = pd.read_csv(RAW('slow', 'wid', 'wid_shares_extract.csv'))
    w = w[(w.variable == 'shwealj992') & (w.percentile == 'p99p100') & (w.data_quality >= 1) & (w.year >= 1950)]
    iso = w.country.map(lambda c: getattr(pycountry.countries.get(alpha_2=c), 'alpha_3', None) if len(c) == 2 else None)
    return frame(iso, w.year, w.value, 'top1-wealth', 2)


# ---------------------------------------------------------------- main

def coverage(d, br, pz, de):
    lines = ['indicator coverage (countries, data years, rows, known - data year)', '']
    for i, g in d.groupby('indicator'):
        lag = (g.known - g.year)
        lines.append(f'{i:18s} {"factor" if i in FACTOR else "      "} {g.iso.nunique():4d} countries  '
                     f'{g.year.min()}-{g.year.max()}  {len(g):6d} rows  lag {lag.min()}-{lag.max()}')
    m = pz.merge(br, on=['iso', 'year'], suffixes=('_p', '_b'))
    lines += ['', f'bank share, Panizza SOE3 against BRSS on {len(m)} shared country-years: '
              f'r = {np.corrcoef(m.value_p, m.value_b)[0, 1]:.2f}, mean BRSS - Panizza = {(m.value_b - m.value_p).mean():+.3f}']
    for nm, src in [('Panizza', pz), ('BRSS', br)]:
        g = src[src.iso == 'DEU'].merge(de, on=['iso', 'year'], suffixes=('_s', '_d'))
        if len(g):
            lines.append(f'Germany, Bundesbank groups against {nm} on {len(g)} years: mean {g.value_d.mean():.3f} '
                         f'against {g.value_s.mean():.3f} (the Bundesbank series replaces both)')
    ka = d[d.indicator == 'ka-open'].merge(d[d.indicator == 'ka-restrict'], on=['iso', 'year'])
    if len(ka):
        lines.append(f'KAOPEN against FKRSU restrictions on {len(ka)} country-years: '
                     f'r = {np.corrcoef(ka.value_x, ka.value_y)[0, 1]:.2f}')
    f = d[d.indicator.isin(FACTOR)]
    k = f.groupby(['iso', 'year']).indicator.nunique()
    lines += ['', 'factor indicators per country-year (data year): '
              + ', '.join(f'{n}: {int((k == n).sum())}' for n in sorted(k.unique()))]
    return '\n'.join(lines)


def build():
    universe = countries()
    pz, br, de = panizza(), brss(), bundesbank()
    parts = [bank_share(pz, br, de), br]
    vd = vdem()
    parts += vd
    parts.append(econ_indicators())
    parts.append(kaopen())
    parts.append(fkrsu())
    # zeros for the countries V-Dem codes, not for every territory
    parts.append(exprop(exprop_acts(), set(vd[0].iso) & universe))
    parts.append(pmr())
    parts.append(wid_top1())
    parts.append(repression(wdi_zip('FR.INR.DPST'), wdi_json('FP.CPI.TOTL.ZG'), wdi_json('NY.GDP.MKTP.KD.ZG')))
    d = pd.concat(parts, ignore_index=True)
    d = d[d.iso.isin(universe)].sort_values(['iso', 'indicator', 'year'])
    assert (d.known >= d.year).all()
    assert not d.duplicated(['iso', 'year', 'indicator']).any(), d[d.duplicated(['iso', 'year', 'indicator'], keep=False)].head()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    d.to_csv(OUT, index=False)
    c = coverage(d, br, pz, de)
    open(COV, 'w').write(c + '\n')
    print(c)
    print(len(d), 'rows to', OUT)


def selftest():
    # BRSS answers in every unit the waves use
    assert share('31,9%') == 31.9 and np.isnan(share('Not Available')) and share(0.25) == 0.25
    # expropriation window: acts in 1960 and 1965 count until 1969 and 1974
    acts = pd.DataFrame({'iso': ['AAA', 'AAA'], 'year': [1960, 1965]})
    e = exprop(acts, {'BBB'}, first=1969, last=1975).set_index(['iso', 'year']).value
    assert e['AAA', 1969] == np.log1p(2) and e['AAA', 1974] == np.log1p(1) and e['AAA', 1975] == 0.0
    assert e['BBB', 1970] == 0.0
    # an act is known the year after it (the channel table's P3, +1), not the year itself
    ek = exprop(acts, {'BBB'}, first=1969, last=1975)
    assert (ek.known == ek.year + 1).all()
    # the splice keeps Panizza and fills from BRSS only where Panizza is missing
    pz = frame(['X', 'X'], [2000, 2001], [0.5, 0.4], 'state-bank-share', 2)
    br = frame(['X', 'X', 'Y'], [2001, 2005, 2005], [0.9, 0.3, 0.2], 'bank-brss', known=[2004, 2008, 2008])
    s = bank_share(pz, br).set_index(['iso', 'year'])
    assert s.loc[('X', 2001), 'value'] == 0.4 and s.loc[('X', 2005), 'known'] == 2008 and len(s) == 4
    # Germany from the Bundesbank's banking groups, replacing Panizza and BRSS
    de = germany_banks({2000: 100.0, 2001: 200.0}, {2000: 20.0, 2001: 30.0}, {2000: 15.0, 2001: 40.0})
    assert list(de.value) == [0.35, 0.35] and list(de.known) == [2001, 2002] and set(de.iso) == {'DEU'}
    pz2 = frame(['DEU', 'X'], [2000, 2000], [0.01, 0.5], 'state-bank-share', 2)
    br2 = frame(['DEU'], [2005], [0.4], 'bank-brss', known=[2008])
    s2 = bank_share(pz2, br2, de)
    assert sorted(s2[s2.iso == 'DEU'].year) == [2000, 2001] and s2[s2.iso == 'DEU'].value.eq(0.35).all()
    assert s2[s2.iso == 'X'].value.iloc[0] == 0.5
    m = bbk_december(['"",K', '1999-11,1.0,', '1999-12,2.5,', '2000-12,-,Nothing exists', '2001-12,3.0,Provisional value'])
    assert m == {1999: 2.5, 2001: 3.0}, m
    # the repression gap: growth less the Fisher real deposit rate, five-year
    # mean of at least three years, known a year later
    wd = lambda vs: pd.DataFrame({'iso': 'A', 'year': range(2000, 2000 + len(vs)), 'v': vs})
    r = repression(wd([10.0] * 5), wd([10.0] * 5), wd([3.0] * 5)).set_index('year')
    assert list(r.index) == [2002, 2003, 2004] and np.allclose(r.value, 3.0) and (r.known == r.index + 1).all()
    h = repression(wd([100.0] * 3), wd([300.0] * 3), wd([0.0] * 3)).set_index('year')
    assert np.isclose(h.value[2002], 50.0)
    g = repression(wd([5.0, np.nan, 5.0, 5.0]), wd([0.0] * 4), wd([0.0, 0.0, 0.0, 9.0])).set_index('year')
    assert list(g.index) == [2003] and np.isclose(g.value[2003], (-5 - 5 + 4) / 3)
    assert name_iso('Korea, Rep.') == 'KOR' and name_iso('United States') == 'USA'
    assert name_iso("CHINA-PEOPLE'S REP.") == 'CHN' and name_iso('Macedonia, Republic of ') == 'MKD'
    # the written table: nothing dated before it is known
    if os.path.exists(OUT):
        t = pd.read_csv(OUT)
        assert (t.known >= t.year).all()
        b = t[t.indicator == 'state-bank-share']
        assert b.value.between(0, 1).all()
        so = t[(t.indicator == 'state-own') & (t.iso == 'CHN') & (t.year == 1990)].value
        sg = t[(t.indicator == 'state-own') & (t.iso == 'USA') & (t.year == 1990)].value
        assert so.iloc[0] > sg.iloc[0], 'state-own must be high for China'
        # each source's lag as released: the 2023 KAOPEN (data to 2023) and
        # the 2021 FKRSU update (to 2019) came out two years after their last
        # year; the 2019 BRSS wave is read from the April 2021 corrected file
        lag = (t.known - t.year).groupby(t.indicator)
        want = {'ka-open': 2, 'ka-restrict': 2, 'exprop-10y': 1, 'state-own': 1, 'repression': 1}
        for i, k in want.items():
            assert lag.min()[i] == k and lag.max()[i] == k, (i, lag.min()[i], lag.max()[i])
        # Germany's series starts with Panizza's (1995): earlier, it would be the
        # indicator's only country and set its scale alone
        assert t[(t.indicator == 'state-bank-share')].year.min() == 1995
        b = t[(t.indicator == 'bank-brss') & (t.year >= 2011)]
        assert (b.known == 2021).all()
    print('ok')


# ---------------------------------------------------------------- validation

EVENTS = [('CHN', 1988, 2005, '-', "China's reforms, SOE retrenchment"),
          ('RUS', 1990, 2000, '-', "Russia's privatisation"),
          ('RUS', 2000, 2012, '+', "Russia's re-statisation"),
          ('VEN', 2000, 2012, '+', "Venezuela's nationalisations"),
          ('GBR', 1979, 1995, '-', "Britain's privatisation"),
          ('ARG', 1988, 2000, '-', 'Argentina, Menem privatisations'),
          ('MEX', 1985, 2000, '-', 'Mexico privatisations'),
          ('BRA', 1989, 2002, '-', 'Brazil privatisations'),
          ('PER', 1989, 2000, '-', 'Peru privatisations'),
          ('CHL', 1974, 1990, '-', 'Chile privatisations'),
          ('BOL', 2004, 2012, '+', "Bolivia's nationalisations")]
GULF = ['SAU', 'ARE', 'QAT', 'KWT', 'OMN', 'BHR']
NAMED = ['CHN', 'VNM'] + GULF + ['DEU', 'JPN', 'KOR', 'USA', 'GBR']


def check():
    s = pd.read_csv(STOCKS)
    ind = pd.read_csv(OUT)
    out = []
    p = lambda *a: out.append(' '.join(str(x) for x in a))
    for axis in sorted(v for v in s.variable.unique() if v.startswith('control/') and v.count('/') == 1
                       and v.split('/')[1] in ('state', 'capture')):
        a = s[s.variable == axis].pivot(index='iso', columns='year', values='value')
        p(f'\n==== {axis} (year = year known; rank of {a.shape[0]} countries scored, 1 = highest)')
        for y in [1985, 1995, 2005, 2015, 2025]:
            if y not in a.columns:
                continue
            col = a[y].dropna()
            r = col.rank(ascending=False)
            p(f'{y}: n={len(col)} ' + '  '.join(f'{i} {col[i]:+.2f} ({int(r[i])})' for i in NAMED if i in col.index))
        if axis != 'control/state':
            continue
        fx = pd.read_csv(FIXED) if os.path.exists(FIXED) else None
        if fx is not None:
            fx = fx[fx.axis == axis.split('/')[1]].pivot(index='iso', columns='year', values='value')
        p('\npaths (year known; causal stock / under the last fit, which separates the data from refitting):')
        for iso in ['KOR', 'CHN', 'VNM', 'RUS', 'VEN', 'GBR', 'ARG', 'MEX', 'BRA', 'PER', 'CHL', 'BOL']:
            cells = []
            for y in range(1976, 2027, 3):
                c = a.loc[iso, y] if iso in a.index and y in a.columns else np.nan
                f = fx.loc[iso, y] if fx is not None and iso in fx.index and y in fx.columns else np.nan
                if pd.notna(c) or pd.notna(f):
                    cells.append(f'{y} {c:+.2f}/{f:+.2f}')
            p(f'  {iso}: ' + '  '.join(cells))
        p('\nKorea over time:', '  '.join(f'{y} {a.loc["KOR", y]:+.2f}' for y in range(1980, 2027, 5)
                                         if y in a.columns and pd.notna(a.loc['KOR', y])))
        p('\nevent checks, windows set before the fit (score change; the expected sign in brackets):')
        for iso, y0, y1, sign, label in EVENTS:
            if iso in a.index and y0 in a.columns and y1 in a.columns and pd.notna(a.loc[iso, y0]) and pd.notna(a.loc[iso, y1]):
                dlt = a.loc[iso, y1] - a.loc[iso, y0]
                ok = (dlt < 0) if sign == '-' else (dlt > 0)
                p(f'  {iso} {y0}->{y1} [{sign}] {a.loc[iso, y0]:+.2f} -> {a.loc[iso, y1]:+.2f} '
                  f'({dlt:+.2f}) {"yes" if ok else "NO"}  {label}')
            else:
                p(f'  {iso} {y0}->{y1}: not scored at both ends  {label}')
        # more than income: partial ranking after log income
        inc = ind[ind.indicator == 'log-gdp-pc']
        p('\nmore than income (cross-section at year known Y against log GDP per head of data year Y-1, PWT):')
        for y in [1995, 2005, 2015, 2022]:
            if y not in a.columns:
                continue
            g = inc[inc.year == y - 1].set_index('iso').value
            x = pd.concat([a[y].rename('f'), g.rename('inc')], axis=1).dropna()
            b = np.polyfit(x.inc, x.f, 1)
            res = x.f - np.polyval(b, x.inc)
            r2 = np.corrcoef(x.inc, x.f)[0, 1] ** 2
            rk = res.rank(ascending=False)
            p(f'  {y}: n={len(x)} slope {b[0]:+.2f} R2 {r2:.2f}; residual rank: '
              + '  '.join(f'{i} {res[i]:+.2f} ({int(rk[i])})' for i in NAMED if i in res.index))
    if os.path.exists(VARIANTS):
        variants(pd.read_csv(VARIANTS), s, ind, p)
    text = '\n'.join(out)
    open(CHECK, 'w').write(text + '\n')
    print(text)


def events_hit(a):
    """(right sign, scored) of EVENTS on the wide table `a` (iso x year)."""
    hit = n = 0
    for iso, y0, y1, sign, _ in EVENTS:
        if iso in a.index and y0 in a.columns and y1 in a.columns and pd.notna(a.loc[iso, y0]) and pd.notna(a.loc[iso, y1]):
            d = a.loc[iso, y1] - a.loc[iso, y0]
            n += 1
            hit += int(d < 0 if sign == '-' else d > 0)
    return hit, n


def variants(v, s, ind, p):
    """The registered rule's alternatives side by side (docs/COUPLED-
    SYSTEMS.md 4d): rank agreement, state-own alone, events, income and
    where Germany, Japan and Korea land."""
    w = {k: g.pivot(index='iso', columns='year', values='value') for k, g in v.groupby('variant')}
    so = ind[ind.indicator == 'state-own'].assign(year=lambda d: d.year + 1)
    w['state-own alone'] = so[so.year <= 2026].pivot(index='iso', columns='year', values='value')
    names = ['stock', 'ml', 'equal', 'no-own', 'state-own alone']
    names = [n for n in names if n in w]
    long = {n: w[n].stack().rename(n) for n in names}
    j = pd.concat(long.values(), axis=1)
    j = j[(j.index.get_level_values(1) >= 1995) & (j.index.get_level_values(1) <= 2025)]
    p('\n==== variants of control/state (rule: docs/COUPLED-SYSTEMS.md 4d; the stock is fixed by it)')
    p('Spearman, pooled country-years 1995-2025 holding both (state-own alone: V-Dem, data year Y-1):')
    for a_ in names:
        p(f'  {a_:16s} ' + '  '.join(f'{b:>8s} {j[[a_, b]].dropna().corr("spearman").iloc[0, 1]:+.3f}' for b in names if b != a_))
    p('Spearman within each year, median over 1995-2025:')
    for a_ in names:
        cells = []
        for b in names:
            if b == a_:
                continue
            rs = [j.xs(y, level=1)[[a_, b]].dropna().corr('spearman').iloc[0, 1] for y in range(1995, 2026)
                  if y in j.index.get_level_values(1)]
            cells.append(f'{b:>8s} {np.nanmedian(rs):+.3f}')
        p(f'  {a_:16s} ' + '  '.join(cells))
    p('within-country changes (5-year differences), Spearman against the stock\'s:')
    st = w['stock']
    d5 = lambda t: (t - t.shift(5, axis=1)).stack()
    for n in names:
        if n != 'stock':
            x = pd.concat([d5(st).rename('a'), d5(w[n]).rename('b')], axis=1).dropna()
            p(f'  {n:16s} {x.corr("spearman").iloc[0, 1]:+.3f} over {len(x)}')
    inc = ind[ind.indicator == 'log-gdp-pc']
    for n in names:
        a = w[n]
        h, k = events_hit(a)
        r2 = []
        for y in [1995, 2005, 2015, 2022]:
            if y in a.columns:
                g = inc[inc.year == y - 1].set_index('iso').value
                x = pd.concat([a[y].rename('f'), g.rename('inc')], axis=1).dropna()
                r2.append(f'{y} {np.corrcoef(x.inc, x.f)[0, 1] ** 2:.2f}')
        p(f'\n{n}: events right sign {h} of {k}; R2 on log income ' + ', '.join(r2))
        for y in [1995, 2005, 2015, 2025]:
            if y in a.columns:
                col = a[y].dropna()
                r = col.rank(ascending=False)
                p(f'  {y} n={len(col)}: ' + '  '.join(f'{i} {int(r[i])}' for i in ['DEU', 'JPN', 'KOR', 'USA', 'GBR', 'CHN'] if i in r.index))
    # comparability: the stock with and without V-Dem's state-own
    k = s[s.variable == 'control/n'].set_index(['iso', 'year']).value
    sd = s[s.variable == 'control/state-sd'].set_index(['iso', 'year']).value
    st_l = st.stack()
    has = pd.Series([(i, y) in set(so.set_index(['iso', 'year']).index) for i, y in st_l.index], index=st_l.index)
    p('\ncomparability: stock rows with and without state-own (by the snapshot\'s V-Dem year Y-1 only; a stale V-Dem value counts as without here)')
    for flag, lab in [(True, 'with'), (False, 'without')]:
        x = st_l[has == flag]
        p(f'  {lab:8s} {len(x):6d} rows, mean |score| {x.abs().mean():.2f}, mean posterior SD {sd.reindex(x.index).mean():.2f}')
    p('  control/n distribution: ' + ', '.join(f'{int(n)}: {c}' for n, c in k.value_counts().sort_index().items()))


if __name__ == '__main__':
    arg = sys.argv[1] if len(sys.argv) > 1 else 'build'
    {'build': build, 'test': selftest, 'check': check}[arg]()
