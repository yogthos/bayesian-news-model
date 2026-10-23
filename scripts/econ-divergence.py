"""Inputs for the asset-growth divergence stock (bead bnm-3ka.7;
docs/ECONOMIC-CHARACTER.md 8a div/*, H4). src/bnm/econ_divergence.clj turns
them into gaps against each country's own history; this script only
assembles the levels.

Writes data/derived/econ/divergence-inputs.csv, long:
  iso, year (the data year), known (the first year the value could be
  read), indicator, value

Indicators (all logs, so a gap is a proportional deviation):
  equity-gdp      JST: log(equity price index / nominal GDP). The index is
                  the cumulated nominal capital gain eq_capgain (base 1 in
                  the country's first year; the base cancels in a gap
                  against the country's own history). 17 countries.
  price-dividend  JST: -log(eq_dp), price over dividends.
  house-income    JST: log(nominal house price index / nominal GDP per
                  head).
  cape            Shiller (US): log CAPE (PE10), December of the year.
  equity-surplus  Fed Z.1 (US): log(market value of NFC equity,
                  LM103164105.A / NFC net operating surplus,
                  FU106402101.A).
  mcap-gdp        WDI CM.MKT.LCAP.GD.ZS with GFDD DM.01 fill, log: copied
                  from data/derived/econ/econ-indicators.csv
                  (log-mcap-gdp), its known year kept.
  wealth-income   WID private wealth / national income, log: copied from
                  econ-indicators.csv (private-wealth-ni), known kept.

Known years: JST +1 (a compiled annual dataset; GDP for a year is out the
next), Shiller +1 (the year's earnings are reported in the next), Z.1 +1
(LAG in scripts/econ-factors.py), WDI/GFDD +1 and WID +2 as there.

  python -I scripts/econ-divergence.py          # assemble
  python -I scripts/econ-divergence.py test     # no value before it is known
"""
import io
import os
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda *p: os.path.join(ROOT, 'data', *p)
OUT = D('derived', 'econ', 'divergence-inputs.csv')
LAG = {'jst': 1, 'shiller': 1, 'z1': 1}


def frame(iso, year, value, indicator, lag):
    d = pd.DataFrame({'iso': iso, 'year': year, 'value': value}).dropna()
    d = d[np.isfinite(d.value)]
    d['year'] = d.year.astype(int)
    d['known'] = d.year + lag
    d['indicator'] = indicator
    return d[['iso', 'year', 'known', 'indicator', 'value']]


def jst():
    j = pd.read_csv(D('raw', 'slow', 'jst', 'jst.csv')).sort_values(['iso', 'year'])
    out = []
    for iso, g in j.groupby('iso'):
        g = g.copy()
        cg = g.eq_capgain
        # the price index: cumulated gains over the country's contiguous run
        # from its first observed gain; a gap in the series restarts nothing
        # (a missing year breaks the chain, so the index stops there)
        idx, level, ok = [], 1.0, True
        started = False
        for x in cg:
            if pd.isna(x):
                if started:
                    ok = False
                idx.append(np.nan)
                continue
            if not ok:
                idx.append(np.nan)
                continue
            started = True
            level *= 1.0 + x
            idx.append(level)
        g['pidx'] = idx
        with np.errstate(divide='ignore', invalid='ignore'):
            out.append(frame(iso, g.year, np.log(g.pidx / g.gdp), 'equity-gdp', LAG['jst']))
            out.append(frame(iso, g.year, -np.log(g.eq_dp.where(g.eq_dp > 0)), 'price-dividend', LAG['jst']))
            out.append(frame(iso, g.year, np.log(g.hpnom / (g.gdp / g['pop'])), 'house-income', LAG['jst']))
    return pd.concat(out)


def shiller():
    s = pd.read_csv(D('raw', 'slow', 'market', 'shiller.csv'), parse_dates=['Date'])
    s = s[(s.Date.dt.month == 12) & (s.PE10 > 0)]
    return frame('USA', s.Date.dt.year, np.log(s.PE10), 'cape', LAG['shiller'])


def z1():
    z = zipfile.ZipFile(D('raw', 'econ', 'fed-z1', 'z1_csv_files.zip'))
    t = pd.read_csv(z.open('csv/S11_1_i_a.csv'), na_values=['ND'])
    eq = t['LM103164105.A']
    os_ = t.loc[:, [c for c in t.columns if c.startswith('FU106402101.A')]].iloc[:, 0]
    with np.errstate(divide='ignore', invalid='ignore'):
        v = np.log(pd.to_numeric(eq, errors='coerce') / pd.to_numeric(os_, errors='coerce').where(lambda x: x > 0))
    return frame('USA', t.date, v, 'equity-surplus', LAG['z1'])


def copied():
    e = pd.read_csv(D('derived', 'econ', 'econ-indicators.csv'))
    m = e[e.indicator == 'log-mcap-gdp'].assign(indicator='mcap-gdp')
    w = e[(e.indicator == 'private-wealth-ni') & (e.value > 0)].copy()
    w['value'] = np.log(w.value)
    w['indicator'] = 'wealth-income'
    return pd.concat([m, w])[['iso', 'year', 'known', 'indicator', 'value']]


def build():
    d = pd.concat([jst(), shiller(), z1(), copied()])
    d = d.sort_values(['indicator', 'iso', 'year'])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    d.to_csv(OUT, index=False, float_format='%.6f')
    for ind, g in d.groupby('indicator'):
        print(f'{ind:15s} {g.iso.nunique():4d} countries  {g.year.min()}-{g.year.max()}  {len(g):6d} rows')
    print('wrote', OUT)


def test():
    d = pd.read_csv(OUT)
    assert (d.known > d.year).all(), 'a value dated at or before its data year'
    lag = {'equity-gdp': 1, 'price-dividend': 1, 'house-income': 1, 'cape': 1, 'equity-surplus': 1,
           'mcap-gdp': 1, 'wealth-income': 2}
    for ind, g in d.groupby('indicator'):
        assert ((g.known - g.year) == lag[ind]).all(), ind
    assert not d.duplicated(['iso', 'year', 'indicator']).any()
    print('ok', len(d), 'rows')


if __name__ == '__main__':
    test() if sys.argv[1:] == ['test'] else build()
