"""BACI bilateral trade at HS6 as a panel (docs/ESSENTIALS.md section 8 step 1,
bead bnm-1pq.2). Research tooling: reads the CEPII BACI V202601 zips straight
from data/raw/material/ (never unzipped) and writes data/derived/supply/:

  hs6.csv         year,known,hs6,exporter,importer,value,tonnes,uv
      HS92 bilateral flows, 1995-2024. value in thousand USD, tonnes in
      metric tons (blank where BACI has none), uv = value / tonnes in
      kUSD per tonne (= USD per kg; blank without tonnes).
  hs6-hs12.csv, hs6-hs17.csv
      the same for the HS2012 (2012-) and HS2017 (2017-) editions, when
      their zips are present in data/raw/material/baci-hs12 and baci-hs17.
      Each edition keeps its own codes: an HS12 850760 is not an HS92 code.
  hs6-world.csv   hs,year,known,hs6,value,tonnes,share,exporters,hhi
      per edition (hs = HS92, HS12, HS17), year and product: world value
      and tonnes, share of that edition's world trade value in the year,
      the number of exporters with positive value, and the exporters'
      Herfindahl index of value shares (0-1).

Known: CEPII publishes BACI once a year, in January (V202601 was released
2026-01-22 and runs to 2024), so the data for year Y are first known in the
release of January Y+2: known = year + 2. Every edition of a release is
reconciled again, so earlier years are revised as well; one vintage is used
for all years, a known look-ahead in levels but not in timing.

Countries: BACI's numeric codes become ISO3 through its own
country_codes file. Code 490, "Other Asia, nes", is Taiwan: UN Comtrade does
not list Taiwan and files its trade under 490 (the UN's "Other Asia, n.e.s.";
BACI's own documentation does not say so), so it is written TWN rather than
BACI's S19. Small amounts of other unallocated Asian trade may sit in it too. Former
entities keep their successors' codes as BACI gives them (58
Belgium-Luxembourg to 1998 as BEL, 280 West Germany as DEU, 736 Sudan to
2011 as SDN; they never overlap with the successor in a year). Groups
without an ISO code keep BACI's (ZA1 the Southern African Customs Union to
1999, R20 EFTA n.e.s.).

    python -I scripts/baci.py [--root data/raw/material]
                              [--out data/derived/supply]
                              [--editions HS92,HS12,HS17] [--world-only]
"""

import argparse
import os
import re
import sys
import zipfile

import numpy as np
import pandas as pd

LAG = 2  # BACI for year Y is released in January of Y + 2

EDITIONS = {  # edition -> (directory under root, bilateral file name)
    "HS92": ("baci", "hs6.csv"),
    "HS12": ("baci-hs12", "hs6-hs12.csv"),
    "HS17": ("baci-hs17", "hs6-hs17.csv"),
}

ISO_OVERRIDE = {490: "TWN"}  # "Other Asia, nes" is Taiwan


def iso_map(z):
    """BACI numeric country code -> ISO3, with the overrides above."""
    name = next(n for n in z.namelist() if n.startswith("country_codes_"))
    cc = pd.read_csv(z.open(name), dtype={"country_iso3": str})
    m = dict(zip(cc.country_code.astype(int), cc.country_iso3))
    m.update(ISO_OVERRIDE)
    return m


def year_files(z, edition):
    """[(year, member name)] of the edition's yearly flow files, in order."""
    pat = re.compile(rf"BACI_{edition}_Y(\d{{4}})_V\d+\.csv$")
    out = []
    for n in z.namelist():
        m = pat.search(n)
        if m:
            out.append((int(m.group(1)), n))
    return sorted(out)


def read_year(z, member, iso):
    """The flows of one year: year, hs6 (6-character code), exporter,
    importer (ISO3), value (kUSD), tonnes (NaN where missing)."""
    df = pd.read_csv(z.open(member), dtype={"t": "int16", "i": "int32", "j": "int32",
                                             "k": str, "v": "float64", "q": str})
    df["q"] = pd.to_numeric(df.q.str.strip(), errors="coerce")
    unknown = set(df.i.unique()) | set(df.j.unique())
    unknown -= set(iso)
    if unknown:
        raise SystemExit(f"{member}: country codes without ISO3: {sorted(unknown)}")
    return pd.DataFrame({"year": df.t.astype(int), "hs6": df.k.str.zfill(6),
                         "exporter": df.i.map(iso), "importer": df.j.map(iso),
                         "value": df.v, "tonnes": df.q})


def world(df, edition):
    """World rows of one year's flows (see the module doc)."""
    year = int(df.year.iloc[0])
    total = df.value.sum()
    by_ex = df[df.value > 0].groupby(["hs6", "exporter"]).value.sum()
    prod = by_ex.groupby(level="hs6").sum()
    sh = by_ex / by_ex.index.get_level_values("hs6").map(prod).values
    g = df.groupby("hs6").agg(value=("value", "sum"),
                              tonnes=("tonnes", lambda s: s.sum(min_count=1)))
    g["share"] = g.value / total
    g["exporters"] = by_ex.groupby(level="hs6").size().reindex(g.index).fillna(0).astype(int)
    g["hhi"] = (sh ** 2).groupby(level="hs6").sum().reindex(g.index)
    g = g.reset_index()
    g.insert(0, "known", year + LAG)
    g.insert(0, "year", year)
    g.insert(0, "hs", edition)
    # the world total is the sum of the flows, to rounding
    assert abs(g.value.sum() - total) <= 1e-6 * total, (edition, year)
    return g[["hs", "year", "known", "hs6", "value", "tonnes", "share", "exporters", "hhi"]]


def run(root, out, editions, bilateral):
    os.makedirs(out, exist_ok=True)
    worlds = []
    for ed in editions:
        d, fname = EDITIONS[ed]
        zips = [f for f in sorted(os.listdir(os.path.join(root, d)))
                if re.fullmatch(rf"BACI_{ed}_V\d+\.zip", f)] if os.path.isdir(os.path.join(root, d)) else []
        if not zips:
            print(f"{ed}: no zip under {os.path.join(root, d)}, skipped", file=sys.stderr)
            continue
        z = zipfile.ZipFile(os.path.join(root, d, zips[-1]))
        iso = iso_map(z)
        path = os.path.join(out, fname)
        tmp = path + ".part"
        first = True
        rows = 0
        for year, member in year_files(z, ed):
            df = read_year(z, member, iso)
            worlds.append(world(df, ed))
            if bilateral:
                df.insert(1, "known", df.year + LAG)
                df["uv"] = np.where(df.tonnes > 0, df.value / df.tonnes, np.nan)
                df.to_csv(tmp, mode="w" if first else "a", header=first, index=False,
                          float_format="%.10g")
                first = False
                rows += len(df)
            print(ed, year, len(df), file=sys.stderr, flush=True)
        if bilateral and not first:
            os.replace(tmp, path)
            print(f"{path}: {rows} rows", file=sys.stderr)
    w = pd.concat(worlds, ignore_index=True)
    wpath = os.path.join(out, "hs6-world.csv")
    w.to_csv(wpath + ".part", index=False, float_format="%.10g")
    os.replace(wpath + ".part", wpath)
    print(f"{wpath}: {len(w)} rows", file=sys.stderr)


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--root", default="data/raw/material")
    p.add_argument("--out", default="data/derived/supply")
    p.add_argument("--editions", default="HS92,HS12,HS17")
    p.add_argument("--world-only", action="store_true",
                   help="write hs6-world.csv only, not the bilateral files")
    a = p.parse_args()
    eds = [e.strip().upper() for e in a.editions.split(",") if e.strip()]
    bad = [e for e in eds if e not in EDITIONS]
    if bad:
        raise SystemExit(f"unknown editions {bad}; known: {sorted(EDITIONS)}")
    run(a.root, a.out, eds, not a.world_only)


if __name__ == "__main__":
    main()
