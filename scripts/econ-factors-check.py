"""Descriptive validation of the economic-character factors (beads
bnm-3ka.4, bnm-3ka.5; docs/ECONOMIC-CHARACTER.md 8e H3). Reads
data/derived/stocks/econ-factors.csv (bnm.econ-factors-run) and the income
control in data/derived/econ/econ-indicators.csv; writes
data/derived/econ/econ-factors-check.txt.

  1. Ranks of the focus countries (the US and UK expected high on
     financialisation, China and Korea high on the investment regime,
     Germany and Japan between), at years known 2009 and 2019.
  2. Not just income: the cross-country correlation of each factor with log
     GDP per head (PWT, the value known by the same year), and the ranks of
     the residual of the factor on log income.
  3. H3 as registered: the investment regime known in 2009 (data to about
     2007) separates CHN, KOR, TWN, VNM from USA, GBR, AUS, CAN at equal
     income: the groups' mean residual (on log income) differs by more than
     1 SD of the factor, and |corr with log income| < 0.5. Bootstrap over
     countries (2,000 draws) for the interval.
  4. Is the regime learned or labelled? k-means (k=3, 100 starts, seed 0)
     on the two factors (country means over years known 1997-2021, i.e.
     data about 1995-2019) against the doc's income-dominated k-means
     (7e: eight 1995-2019 feature means, scratchpad regime.py's features).
     Reported per clustering: the share of log income's variance between
     clusters (eta^2), and whether the poles (USA, GBR against CHN, KOR) and
     the middle (DEU, JPN) fall in different clusters.

  python -I scripts/econ-factors-check.py
"""
import json
import os

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda *p: os.path.join(ROOT, 'data', *p)
OUT = D('derived', 'econ', 'econ-factors-check.txt')
FOCUS = ['USA', 'GBR', 'CAN', 'AUS', 'FRA', 'DEU', 'JPN', 'ITA', 'KOR', 'CHN', 'TWN', 'VNM', 'IND', 'BRA', 'RUS', 'SGP']
FACTORS = ['econ/financialisation', 'econ/investment-regime']
REPORTED = FACTORS + ['econ/financial-depth']
lines = []


def say(*a):
    s = ' '.join(str(x) for x in a)
    lines.append(s)
    print(s, flush=True)


def income_known(ind, y):
    """{iso: log GDP per head}, the latest data year known by y."""
    g = ind[(ind.indicator == 'log-gdp-pc') & (ind.known <= y)].sort_values('year')
    return g.groupby('iso').value.last()


def residual(f, x):
    j = pd.concat([f.rename('f'), x.rename('x')], axis=1).dropna()
    b = np.polyfit(j.x, j.f, 1)
    return j.f - np.polyval(b, j.x), j


def ranks(s):
    return s.rank(ascending=False).astype(int)


def section_ranks(st, ind):
    for y in [2009, 2019]:
        inc = income_known(ind, y)
        say(f'\n-- year known {y}: score, rank, rank after removing log income (n countries)')
        for v in REPORTED:
            f = st[(st.variable == v) & (st.year == y)].set_index('iso').value
            r, j = residual(f, inc)
            c = np.corrcoef(j.f, j.x)[0, 1]
            say(f'{v}: corr with log GDP per head {c:+.2f} (n {len(j)})')
            rk, rr = ranks(f), ranks(r)
            for iso in FOCUS:
                if iso in f.index:
                    say(f'  {iso} {f[iso]:+.2f}  rank {rk[iso]:3d}/{len(f)}'
                        + (f'  partial rank {rr[iso]:3d}/{len(r)} (resid {r[iso]:+.2f})' if iso in r.index else ''))


def h3(st, ind, rng):
    y = 2009
    inc = income_known(ind, y)
    f = st[(st.variable == 'econ/investment-regime') & (st.year == y)].set_index('iso').value
    r, j = residual(f, inc)
    sd = j.f.std()
    A, B = ['CHN', 'KOR', 'TWN', 'VNM'], ['USA', 'GBR', 'AUS', 'CAN']
    a, b = [i for i in A if i in r.index], [i for i in B if i in r.index]
    c = np.corrcoef(j.f, j.x)[0, 1]
    raw = (j.f[a].mean() - j.f[b].mean()) / sd
    gap = (r[a].mean() - r[b].mean()) / sd
    boot = []
    isos = j.index.values
    for _ in range(2000):
        s = j.loc[rng.choice(isos, len(isos), replace=True)]
        aa = [rng.choice(a) for _ in a]
        bb = [rng.choice(b) for _ in b]
        s = pd.concat([s, j.loc[aa + bb]])
        bfit = np.polyfit(s.x, s.f, 1)
        rr = lambda k: (j.f[k] - np.polyval(bfit, j.x[k])).mean()
        boot.append((rr(aa) - rr(bb)) / s.f.std())
    lo, hi = np.percentile(boot, [5, 95])
    say(f'\n-- H3, investment regime known {y} (data to about 2007), n {len(j)}')
    say(f'groups {a} vs {b}')
    say(f'raw mean gap {raw:+.2f} SD; at equal income (residual on log income) {gap:+.2f} SD, 90% bootstrap [{lo:+.2f}, {hi:+.2f}]')
    say(f'|corr with log income| {abs(c):.2f}')
    for i in a + b:
        say(f'  {i} factor {j.f[i]:+.2f} log income {j.x[i]:.2f} residual {r[i]:+.2f}')
    ok = gap > 1 and abs(c) < 0.5
    say('H3 ' + ('passes' if ok else 'fails') + ': needs gap > 1 SD and |corr| < 0.5')


def kmeans(Z, k, rng, starts=100):
    best = None
    for _ in range(starts):
        C = Z[rng.choice(len(Z), k, replace=False)]
        for _ in range(100):
            lab = ((Z[:, None, :] - C[None]) ** 2).sum(2).argmin(1)
            C = np.array([Z[lab == q].mean(0) if (lab == q).any() else C[q] for q in range(k)])
        sse = ((Z - C[lab]) ** 2).sum()
        if best is None or sse < best[0]:
            best = (sse, lab)
    return best[1]


def describe(name, lab, inc):
    lab = pd.Series(lab, index=inc.index)
    eta = 1 - sum(((inc[lab == q] - inc[lab == q].mean()) ** 2).sum() for q in lab.unique()) / ((inc - inc.mean()) ** 2).sum()
    g = lambda i: lab.get(i, None)
    poles = (g('USA') is not None and g('CHN') is not None
             and {g('USA'), g('GBR')}.isdisjoint({g('CHN'), g('KOR')}))
    say(f'\n{name}: {len(lab)} countries, eta^2 of log income on clusters {eta:.2f}; '
        f'poles (USA, GBR | CHN, KOR) separated: {poles}; DEU {g("DEU")} JPN {g("JPN")}')
    for q in sorted(lab.unique()):
        m = lab[lab == q].index
        say(f'  cluster {q} ({len(m)}, mean log income {inc[m].mean():.2f}): '
            + ' '.join(sorted(m) if len(m) <= 40 else sorted(set(m) & set(FOCUS)) + ['...']))


def doc_features():
    """The doc's 7e features (scratchpad regime.py), 1995-2019 means."""
    import pyreadstat
    R = ROOT
    pwt = pd.read_csv(f'{R}/data/raw/slow/pwt/pwt110.csv')
    gmd, _ = pyreadstat.read_dta(f'{R}/data/raw/slow/fiscal/GMD_2026_09.dta', usecols=['ISO3', 'year', 'finv_GDP', 'CA_GDP'])
    gmd = gmd.rename(columns={'ISO3': 'countrycode'})
    w = json.load(open(f'{R}/data/raw/slow/worldbank/FS.AST.PRVT.GD.ZS.json'))[1]
    cr = pd.DataFrame([dict(countrycode=x['countryiso3code'], year=int(x['date']), credit=x['value']) for x in w if x['value'] is not None])
    sp = pd.read_csv(f'{R}/data/derived/io/spine-sectors.csv')
    MAN = "FOOD TEX PAPER CHEM RUB MINR METAL MACH TRANSEQ OTHMAN REF".split()
    tot = sp.groupby(['country', 'year']).va.sum()
    f = pd.DataFrame({'fin': sp[sp.sector == 'FIN'].groupby(['country', 'year']).va.sum() / tot,
                      'man': sp[sp.sector.isin(MAN)].groupby(['country', 'year']).va.sum() / tot}).reset_index().rename(columns={'country': 'countrycode'})
    d = pwt.merge(gmd, on=['countrycode', 'year'], how='left').merge(cr, on=['countrycode', 'year'], how='left').merge(f, on=['countrycode', 'year'], how='left')
    feats = ['csh_i', 'irr', 'labsh', 'finv_GDP', 'CA_GDP', 'credit', 'fin', 'man']
    return d[(d.year >= 1995) & (d.year <= 2019)].groupby('countrycode')[feats].mean().dropna()


def section_clusters(st, ind, rng):
    say('\n-- 4. learned regime: k-means k=3 on the two factors vs the doc\'s eight features')
    m = st[st.variable.isin(FACTORS) & st.year.between(1997, 2021)].groupby(['iso', 'variable']).value.mean().unstack().dropna()
    inc = ind[(ind.indicator == 'log-gdp-pc') & ind.year.between(1995, 2019)].groupby('iso').value.mean()
    m = m[m.index.isin(inc.index)]
    Z = ((m - m.mean()) / m.std()).values
    describe('factors (fin, inv)', kmeans(Z, 3, rng), inc[m.index])
    for iso in ['USA', 'GBR', 'DEU', 'JPN', 'KOR', 'CHN']:
        if iso in m.index:
            say(f'    {iso} fin {m.loc[iso, FACTORS[0]]:+.2f} inv {m.loc[iso, FACTORS[1]]:+.2f}')
    try:
        g = doc_features()
        g = g[g.index.isin(inc.index)]
        Zg = ((g - g.mean()) / g.std()).values
        describe('doc 7e features', kmeans(Zg, 3, rng), inc[g.index])
        common = g.index.intersection(m.index)
        Zc = ((m.loc[common] - m.loc[common].mean()) / m.loc[common].std()).values
        describe(f'factors on the doc\'s {len(common)} countries', kmeans(Zc, 3, rng), inc[common])
    except Exception as e:  # the doc features need GMD and the spine
        say('doc features unavailable:', repr(e))


def main():
    st = pd.read_csv(D('derived', 'stocks', 'econ-factors.csv'))
    ind = pd.read_csv(D('derived', 'econ', 'econ-indicators.csv'))
    rng = np.random.default_rng(0)
    pd.set_option('display.width', 200)
    say('econ-factors validation (descriptive); factors in SD units of the training panel')
    section_ranks(st, ind)
    h3(st, ind, rng)
    section_clusters(st, ind, rng)
    open(OUT, 'w').write('\n'.join(lines) + '\n')
    print('to', OUT)


if __name__ == '__main__':
    main()
