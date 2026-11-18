#!/usr/bin/env python3
"""Quarterly real GDP per head, year on year, for the quarterly growth-collapse
outcome (bnm.economic-events/quarterly-growth-catalogue, bead bnm-56c.37,
docs/GROWTH-QUARTERLY.md). Reads, each as downloaded (URLs in its SOURCE.txt):

  data/raw/oecd-qna/qna_b1gq_sa.csv              OECD QNA, GDP, seasonally and calendar
                                                 adjusted, local currency, chain-linked
                                                 volumes (UNIT_MEASURE XDC, PRICE_BASE LR,
                                                 TABLE_IDENTIFIER T0102); CC BY 4.0
  data/raw/imf-qnea/qnea_b1gq_q_xdc.csv          IMF QNEA, real GDP, local currency, SA
                                                 where it has eight quarters, else NSA
  data/raw/dbnomics-imf-ifs-gdp/ifs_NGDP_R_*.json IMF IFS as frozen by DBnomics (2025),
                                                 SA or NSA, whichever is longer

and writes data/derived/gdp-quarterly.csv

  iso,quarter,yoy,pop_year,pop_growth,yoy_pc,source

yoy is log(GDP_q / GDP_{q-4}). The sources are spliced by that growth, never by
level: each quarter takes the first source with a growth for it, in order
OECD QNA, IMF QNEA, IFS (the last fills QNEA's short histories). A growth of
0.7 or more in absolute value is a level break (a rebased series), dropped,
and the next source fills it. yoy_pc is per head: yoy less the population's
log growth in the quarter's year (the latest earlier year when that one is
missing). The population is the annual catalogue's: WDI's GDP growth less
its growth of GDP per head (data/slow/series.tsv). A quarter with no
population growth by its year has no row. source and pop_year are each
row's provenance.

The year's population growth is WDI's latest vintage, out about mid-year
after, as every outcome here is latest vintage; the year before's (known
in time) loses the onsets a population shock makes (Jordan 2013, Saudi
Arabia 2020) and a few marginal ones: recall against the annual catalogue
0.84 against 0.87 (docs/GROWTH-QUARTERLY.md, Results).
Eurostat adds no lattice unit beyond OECD and QNEA, and WB GEM's quarterly
provenance is undocumented, so neither is read. Standard library only.

  python3 -I scripts/quarterly-gdp.py [--raw data/raw] [--series data/slow/series.tsv]
                                      [--ref data/ref/countryInfo.txt] [--out data/derived/gdp-quarterly.csv]
"""
import argparse
import collections
import csv
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BREAK = 0.7


def qidx(p):
    """'2020-Q2' -> quarters since year 0 (2020*4 + 1)."""
    y, k = p.split("-Q")
    return int(y) * 4 + int(k) - 1


def quarter_name(i):
    return f"{i // 4}Q{i % 4 + 1}"


def iso2_map(ref):
    """ISO alpha-2 -> alpha-3 from GeoNames' countryInfo.txt, with the codes
    the sources use for Greece, the UK and Kosovo."""
    out = {"EL": "GRC", "UK": "GBR", "XK": "XKX"}
    with open(ref, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            p = line.rstrip("\n").split("\t")
            if len(p) > 1 and len(p[0]) == 2 and len(p[1]) == 3:
                out.setdefault(p[0], p[1])
    return out


def load_oecd(path):
    """{iso: {quarter: level}}: real GDP in local currency (chain-linked volumes)."""
    s = collections.defaultdict(dict)
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if r["UNIT_MEASURE"] == "XDC" and r["PRICE_BASE"] == "LR" and r["TABLE_IDENTIFIER"] == "T0102" and r["OBS_VALUE"]:
                s[r["REF_AREA"]][qidx(r["TIME_PERIOD"])] = float(r["OBS_VALUE"])
    return {u: v for u, v in s.items() if len(v) >= 8}


def load_qnea(path):
    """{iso: {quarter: level}}: the seasonally adjusted series where it has
    eight quarters or more, else the unadjusted one."""
    s = collections.defaultdict(lambda: collections.defaultdict(dict))
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if not r.get("COUNTRY") or r["OBS_VALUE"] in ("", "NA"):
                continue
            s[r["COUNTRY"]][r["S_ADJUSTMENT"]][qidx(r["TIME_PERIOD"])] = float(r["OBS_VALUE"])
    out = {}
    for u, by in s.items():
        for a in ("SA", "NSA"):
            if len(by.get(a, {})) >= 8:
                out[u] = by[a]
                break
    return out


def _dbnomics(path, m2):
    with open(path) as f:
        d = json.load(f)
    out = {}
    for x in d["series"]["docs"]:
        c = x["dimensions"]["REF_AREA"]
        out[m2.get(c, c) if len(c) == 2 else c] = {qidx(p): float(v) for p, v in zip(x["period"], x["value"])
                                                    if isinstance(v, (int, float))}
    return out


def load_ifs(sa_path, nsa_path, m2):
    """{iso: {quarter: level}}: IFS's SA or NSA series, whichever is longer."""
    sa, nsa = _dbnomics(sa_path, m2), _dbnomics(nsa_path, m2)
    out = {}
    for u in set(sa) | set(nsa):
        a, b = sa.get(u, {}), nsa.get(u, {})
        v = a if len(a) >= len(b) else b
        if len(v) >= 8:
            out[u] = v
    return out


def yoy(levels):
    return {i: math.log(v / levels[i - 4]) for i, v in levels.items()
            if (i - 4) in levels and v > 0 and levels[i - 4] > 0}


def splice(sources):
    """[(name, {iso: {quarter: level}})] in order of preference ->
    {iso: {quarter: (yoy, name)}}: each quarter's growth from the first
    source that has it, level breaks (|yoy| >= 0.7) dropped."""
    out = collections.defaultdict(dict)
    for name, by in sources:
        for u, levels in by.items():
            for i, g in yoy(levels).items():
                if abs(g) < BREAK and i not in out[u]:
                    out[u][i] = (g, name)
    return dict(out)


def population_growth(series):
    """{iso: {year: log growth of population}} from WDI's GDP growth (%) and
    GDP per head in data/slow/series.tsv."""
    pc, g = collections.defaultdict(dict), collections.defaultdict(dict)
    with open(series) as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) < 6:
                continue
            if p[1] == "economy/gdp_per_capita":
                pc[p[0]][int(p[3][:4])] = float(p[5])
            elif p[1] == "economy/gdp_growth":
                g[p[0]][int(p[3][:4])] = float(p[5])
    out = {}
    for u, gs in g.items():
        w = pc.get(u, {})
        out[u] = {y: math.log(1 + x / 100) - math.log(w[y] / w[y - 1]) for y, x in gs.items()
                  if w.get(y, 0) > 0 and w.get(y - 1, 0) > 0 and x > -100}
    return out


def per_capita(spliced, pop):
    """Rows (iso, quarter, yoy, pop_year, pop_growth, yoy_pc, source), the
    population growth of the quarter's year (or the latest earlier)."""
    for u in sorted(spliced):
        pg = pop.get(u, {})
        years = sorted(pg)
        for i in sorted(spliced[u]):
            g, src = spliced[u][i]
            earlier = [y for y in years if y <= i // 4]
            if not earlier:
                continue
            y = earlier[-1]
            yield (u, i, g, y, pg[y], g - pg[y], src)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--raw", default=os.path.join(ROOT, "data", "raw"))
    ap.add_argument("--series", default=os.path.join(ROOT, "data", "slow", "series.tsv"))
    ap.add_argument("--ref", default=os.path.join(ROOT, "data", "ref", "countryInfo.txt"))
    ap.add_argument("--out", default=os.path.join(ROOT, "data", "derived", "gdp-quarterly.csv"))
    a = ap.parse_args(argv)
    m2 = iso2_map(a.ref)
    ifs = os.path.join(a.raw, "dbnomics-imf-ifs-gdp")
    sources = [("oecd-qna", load_oecd(os.path.join(a.raw, "oecd-qna", "qna_b1gq_sa.csv"))),
               ("imf-qnea", load_qnea(os.path.join(a.raw, "imf-qnea", "qnea_b1gq_q_xdc.csv"))),
               ("imf-ifs", load_ifs(os.path.join(ifs, "ifs_NGDP_R_SA_XDC.json"), os.path.join(ifs, "ifs_NGDP_R_NSA_XDC.json"), m2))]
    # ISO alpha-3 units only (OECD's aggregates, EA20, OECD, G7..., are left out)
    iso3 = set(m2.values())
    sources = [(n, {u: v for u, v in by.items() if u in iso3}) for n, by in sources]
    rows = list(per_capita(splice(sources), population_growth(a.series)))
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    with open(a.out, "w", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["iso", "quarter", "yoy", "pop_year", "pop_growth", "yoy_pc", "source"])
        for u, i, g, y, p, gpc, src in rows:
            w.writerow([u, quarter_name(i), f"{g:.6f}", y, f"{p:.6f}", f"{gpc:.6f}", src])
    by_src = collections.Counter(r[6] for r in rows)
    print(f"{len(rows)} unit-quarters in {len(set(r[0] for r in rows))} units -> {a.out}; by source {dict(by_src)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
