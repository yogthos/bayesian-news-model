"""World reference series for the technology-demand check (bead bnm-1pq.7):
what PV's implied silver and silicon demand are set against.

Writes data/derived/supply/tech-demand-ref.csv: series,year,value,unit,source
  silver-mine    world mine production, t Ag: BGS 1970-2021 (sum of the
                 countries), USGS MCS world total after (latest edition)
  silicon-metal  world silicon metal production, t: USGS MYB 1990-2022 (sum
                 of the countries, latest edition of each country-year,
                 scripts/minerals.py's dedup), USGS MCS world total after
                 (latest edition; silicon content)

Retrospective (as published now): the check compares levels and growth,
it is not read by the model.

    python -I scripts/tech-demand-ref.py
"""
import csv
import os
import re
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import minerals as m  # noqa: E402
import pandas as pd  # noqa: E402


def bgs_silver():
    path = os.path.join(m.RAW, "bgs-world-mineral-statistics", "bgs_world_mineral_statistics_1970-2021.csv.gz")
    d = pd.read_csv(path, low_memory=False)
    d = d[(d.bgs_commodity_trans == "silver, mine") & (d.bgs_statistic_type_trans == "Production")]
    if set(d.units.dropna()) != {"kilograms (metal content)"}:
        sys.exit(f"BGS silver: unexpected units {set(d.units.dropna())}")
    d["year"] = d.year.str[:4].astype(int)
    tot = d.groupby("year").quantity.sum() / 1000.0
    return {int(y): float(v) for y, v in tot.items() if v > 0}


def myb_silicon():
    path = os.path.join(m.RAW, "usgs-myb", "world-production-1990-2022", "USGS_MYB_V1_Production_Data_Release.csv")
    d = pd.read_csv(path, low_memory=False, encoding="utf-8-sig")
    d = d[(d.COMMODITY == "Silicon") & (d.COMMODITY_FORM == "Silicon metal")]
    d["ed"] = d.DATA_SOURCE.str.extract(r"MYB_(\d{4})")[0].astype(float)
    d["iso"] = d.COUNTRY_OR_REGION.map(m.usgs_iso)
    d["q"] = pd.to_numeric(d.QUANTITY, errors="coerce") * d.UNITS.map(m.MYB_SCALE)
    d = d[d.iso.notna() & d.ed.notna()]
    groups = defaultdict(list)
    for r in d.itertuples():
        groups[(r.iso, int(r.YEAR))].append({"ed": int(r.ed), "detail": r.FORM_DETAIL,
                                             "q": None if pd.isna(r.q) else r.q})
    tot = defaultdict(float)
    for (_, y), rs in groups.items():
        q, _ = m.myb_dedup(rs)
        tot[y] += q
    return dict(tot)


def mcs_world(com, pattern):
    t = m.mcs_tables()
    x = t[(t.com == com) & t.country.str.contains("world", case=False, na=False)
          & t.type.str.contains(pattern, case=False, na=False)]
    out = {}
    for y, g in x.groupby("year"):
        r = g.loc[g.ed.idxmax()]
        q = m.number(r.value)
        if q:
            out[int(y)] = (float(q), int(r.ed))
    return out


def main():
    rows = []
    ag = bgs_silver()
    for y, v in sorted(ag.items()):
        rows.append(("silver-mine", y, v, "t Ag", "BGS"))
    for y, (v, ed) in sorted(mcs_world("silve", "mine production").items()):
        if y > max(ag):
            rows.append(("silver-mine", y, v, "t Ag", f"MCS{ed}"))
    si = myb_silicon()
    for y, v in sorted(si.items()):
        rows.append(("silicon-metal", y, v, "t", "MYB"))
    for y, (v, ed) in sorted(mcs_world("silic", "silicon metal").items()):
        if y > max(si):
            rows.append(("silicon-metal", y, v * 1000.0, "t", f"MCS{ed}"))
    out = os.path.join(m.OUT, "tech-demand-ref.csv")
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["series", "year", "value", "unit", "source"])
        for s, y, v, u, src in rows:
            w.writerow([s, y, "%.6g" % v, u, src])
    print("wrote", out, len(rows), file=sys.stderr)


if __name__ == "__main__":
    main()
