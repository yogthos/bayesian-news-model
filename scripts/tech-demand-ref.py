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

Also writes data/derived/supply/tech-installs.csv: iso,year,series,value,
which bnm.tech-demand does read (bead bnm-1pq.25):
  bev-sales, phev-sales  new battery-electric and plug-in hybrid cars sold
                         (IEA Global EV Outlook 2026 via OWID,
                         data/raw/material/owid-iea-ev-sales); IEA's World
                         as WLD, OWID's aggregates dropped
  bev-share, phev-share  their shares of new car sales, %
  wind-onshore, wind-offshore  installed capacity, MW, year end (IRENASTAT,
                         data/raw/material/irena-stat); no world row
Missing values are left out, not written as zeros.

    python -I scripts/tech-demand-ref.py
    python -I scripts/tech-demand-ref.py --selftest
"""
import csv
import json
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


OWID_SERIES = {"bev-sales": 820024, "phev-sales": 820026, "bev-share": 820031, "phev-share": 820032}
IRENA_TECH = {"Onshore wind energy": "wind-onshore", "Offshore wind energy": "wind-offshore"}


def owid_indicator(data, meta):
    """{(iso, year): value} of an OWID indicator API's data.json and
    metadata.json; World as WLD, aggregates (OWID_ or no code) dropped."""
    names = {e["id"]: ("WLD" if e.get("code") == "OWID_WRL" else e.get("code"))
             for e in meta["dimensions"]["entities"]["values"]}
    out = {}
    for v, y, e in zip(data["values"], data["years"], data["entities"]):
        iso = names.get(e)
        if v is None or not iso or iso.startswith("OWID_"):
            continue
        out[(iso, int(y))] = float(v)
    return out


def jsonstat_wind(js):
    """{(series, iso, year): MW} from IRENASTAT's json-stat2 response of
    the wind query (one data type and grid connection)."""
    ids, size = js["id"], js["size"]
    cats = []
    for d in ids:
        c = js["dimension"][d]["category"]
        idx = c["index"]
        order = sorted(idx, key=idx.get) if isinstance(idx, dict) else list(idx)
        cats.append([(k, c["label"][k]) for k in order])
    for d, n in zip(ids, size):
        if d not in ("Country/area", "Technology", "Year") and n != 1:
            sys.exit(f"IRENA: dimension {d} has {n} values, expected 1")
    out = {}
    vals = js["value"]
    strides = [1] * len(size)
    for i in range(len(size) - 2, -1, -1):
        strides[i] = strides[i + 1] * size[i + 1]
    ci, ti, yi = ids.index("Country/area"), ids.index("Technology"), ids.index("Year")
    for flat, v in enumerate(vals):
        if v is None:
            continue
        pos = [(flat // strides[i]) % size[i] for i in range(len(size))]
        series = IRENA_TECH.get(cats[ti][pos[ti]][1])
        if series is None:
            sys.exit(f"IRENA: unexpected technology {cats[ti][pos[ti]]}")
        out[(series, cats[ci][pos[ci]][0], int(cats[yi][pos[yi]][1]))] = float(v)
    return out


def installs():
    rows = []
    d = os.path.join(m.RAW, "owid-iea-ev-sales")
    for s, i in OWID_SERIES.items():
        with open(os.path.join(d, f"indicator-{i}.data.json")) as f:
            data = json.load(f)
        with open(os.path.join(d, f"indicator-{i}.metadata.json")) as f:
            meta = json.load(f)
        for (iso, y), v in owid_indicator(data, meta).items():
            rows.append((iso, y, s, v))
    with open(os.path.join(m.RAW, "irena-stat", "wind-capacity.json-stat2.json")) as f:
        for (s, iso, y), v in jsonstat_wind(json.load(f)).items():
            rows.append((iso, y, s, v))
    out = os.path.join(m.OUT, "tech-installs.csv")
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["iso", "year", "series", "value"])
        for iso, y, s, v in sorted(rows):
            w.writerow([iso, y, s, "%.6g" % v])
    print("wrote", out, len(rows), file=sys.stderr)


def selftest():
    meta = {"dimensions": {"entities": {"values": [{"id": 1, "code": "CHN"}, {"id": 2, "code": "OWID_WRL"},
                                                   {"id": 3, "code": "OWID_EUR"}, {"id": 4}]}}}
    data = {"values": [5, 7, 9, 11, None], "years": [2020, 2020, 2020, 2020, 2021], "entities": [1, 2, 3, 4, 1]}
    assert owid_indicator(data, meta) == {("CHN", 2020): 5.0, ("WLD", 2020): 7.0}, owid_indicator(data, meta)
    js = {"id": ["Country/area", "Technology", "Data Type", "Year"], "size": [2, 2, 1, 2],
          "dimension": {"Country/area": {"category": {"index": {"AAA": 0, "BBB": 1}, "label": {"AAA": "A", "BBB": "B"}}},
                        "Technology": {"category": {"index": {"5": 0, "21": 1},
                                                    "label": {"5": "Onshore wind energy", "21": "Offshore wind energy"}}},
                        "Data Type": {"category": {"index": {"1": 0}, "label": {"1": "MW"}}},
                        "Year": {"category": {"index": {"0": 0, "1": 1}, "label": {"0": "2000", "1": "2001"}}}},
          "value": [1, 2, None, 4, 5, 6, 7, None]}
    got = jsonstat_wind(js)
    assert got == {("wind-onshore", "AAA", 2000): 1.0, ("wind-onshore", "AAA", 2001): 2.0,
                   ("wind-offshore", "AAA", 2001): 4.0, ("wind-onshore", "BBB", 2000): 5.0,
                   ("wind-onshore", "BBB", 2001): 6.0, ("wind-offshore", "BBB", 2000): 7.0}, got
    print("tech-demand-ref selftest ok", file=sys.stderr)


def main():
    if sys.argv[1:] == ["--selftest"]:
        return selftest()
    installs()
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
