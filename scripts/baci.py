"""BACI bilateral trade at HS6 as a panel (docs/ESSENTIALS.md section 8 step 1,
beads bnm-1pq.2 and bnm-1pq.17). Research tooling: reads the CEPII BACI
V202601 zips straight from data/raw/material/ (never unzipped) and writes
data/derived/supply/, for every edition whose zip is present (HS92
1995-2024, HS12 2012- in baci-hs12/, HS17 2017- in baci-hs17/). Each
edition keeps its own codes: an HS12 850760 is not an HS92 code. Values
are thousand USD, tonnes metric tons (blank where BACI has none).

  hs6-essentials.csv.gz  hs,year,known,hs6,exporter,importer,value,tonnes,uv
      bilateral flows of the essentials codes only: every hs_code and hs6
      of subnodes.csv, and every code of the broad critical-materials
      mapping (eu-2023, usgs-2022 in tools/bnm/essentials_check.clj, read
      from that file so the two cannot drift), and the technology codes
      of scripts/tech-trade.py (TECH_CODES: HS17 8703xx, HS92 850780, HS12
      850760, in their own edition only). uv = value / tonnes in kUSD
      per tonne (= USD per kg; blank without tonnes).
  hs6-country.csv.gz     hs,year,known,hs6,country,exports,imports
      country x product totals: a country's exports and imports of the
      product in the year (0 where it has flows one way only). Summed over
      countries, exports and imports are each the world value.
  hs2-pairs.csv.gz       hs,year,known,hs2,exporter,importer,value,tonnes
      country-pair flows by HS2 chapter, for chokepoint routing (tonnes
      blank where none of the pair's flows carries any).
  hs6-world.csv   hs,year,known,hs6,value,tonnes,tonnes_cover,share,exporters,hhi
      per edition, year and product: world value and tonnes, the share of
      the value whose flows carry tonnes (tonnes_cover: BACI leaves q blank
      for about 1-3% of flows, 1% of value, so tonnes undercount where it
      is below 1), share of that edition's world trade value in the year,
      the number of exporters with positive value, and the exporters'
      Herfindahl index of value shares (0-1).

A run over some editions rewrites only their rows in every output. The
full bilateral files (hs6.csv, hs6-hs12.csv, hs6-hs17.csv: 13, 6.9 and
4.3 GB) are written only with --full, and the zips rebuild them in
minutes. scripts/tech-trade.py reads hs6-essentials.csv.gz.

Known and vintage. CEPII publishes BACI once a year, in January (V202601
was released 2026-01-22 and runs to 2024; V202102 came in February), so
the data for year Y are first known in the release of January Y+2: known =
year + 2. That is right for timing only. Every release re-reconciles all
years (new mirror data, revised reporter filings, a re-estimated CIF/FOB
model, re-imputed quantities), so V202601's 2016 is not what V201801 said
about 2016: one vintage for all years is a look-ahead in levels. For the
rise score, which is a ratio of trends of world shares, the revision is
expected to be small next to 8-year changes, but that is an assumption,
not a measurement. Older vintages are kept by CEPII under
https://www.cepii.fr/DATA_DOWNLOAD/baci/legacy/<version>/ (HS92, zipped):
V201901 (.../201901/trade_flows/BACI_HS92_V201901.zip, 2.00 GB, to 2017),
V202001 (.../202001/trade_flows/, 2.54 GB), V202102 (2.35 GB), V202201
(2.26 GB), V202401 (2.46 GB), V202401b (2.45 GB), V202501 (2.32 GB);
the V202301 link on CEPII's page is dead (404). A vintage check scores
year Y from the release of Y+2 (e.g. 2017 from V201901, 2022 from V202401)
and compares ranks; not done yet.

What the flows are. BACI reconciles each flow from the exporter's and the
importer's declarations (mirror flows, importers weighted by reporting
reliability, CIF values brought to FOB), so a flow is the reconciled
estimate, not either filing. Re-exports are not removed: goods that pass
through an entrepot (Hong Kong, Singapore, the Netherlands, Belgium, the
UAE) appear as that entrepot's exports as well as their origin's. So
"exporters" counts shippers, not producers, and the HHI is the
concentration of shipping countries: it understates producer
concentration where a hub re-exports (cobalt and rare earths through
Belgium and the Netherlands, electronics through Hong Kong) and can
overstate it where the hub is itself the main shipper. Production
concentration (scripts/minerals.py) is the measure of supply; this one is
corroboration.

Countries: BACI's numeric codes become ISO3 through its own country_codes
file. Code 490, "Other Asia, nes", is Taiwan: the file gives it no ISO3
(S19) and has no 158/Taiwan entry; UN Comtrade does not list Taiwan and
files partner trade with it under 490 (the UN M49 "Other Asia, n.e.s."),
and in V202601 490 exports 170 bn USD in 2000 and 577 bn in 2024 with
integrated circuits (854211) the largest at 231 bn, which is Taiwan's
profile and size. It is written TWN rather than S19. Small amounts of
other unallocated Asian trade may sit in it too. Former entities keep
their successors' codes as BACI gives them (280 West Germany as DEU, 736
Sudan to 2011 as SDN, 891 Serbia and Montenegro as SCG; they never overlap
with the successor in a year). 58 Belgium-Luxembourg to 1998 is written
BEL, which is lossy: Luxembourg's trade sits in BEL until 1998 and is
apart (LUX) from 1999, so BEL steps down and LUX appears at 1999; a
Belgium series or exporter count across 1998-99 is not like for like.
Groups without an ISO code keep BACI's (ZA1 the Southern African Customs
Union to 1999, R20 EFTA n.e.s.).

    python -I scripts/baci.py [--root data/raw/material]
                              [--out data/derived/supply]
                              [--editions HS92,HS12,HS17] [--world-only] [--full]
    python -I scripts/baci.py --selftest
"""

import argparse
import gzip
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

COLUMNS = ["hs", "year", "known", "hs6", "value", "tonnes", "tonnes_cover", "share",
           "exporters", "hhi"]


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
    df = df.assign(vq=df.value.where(df.tonnes.notna(), 0.0))
    g = df.groupby("hs6").agg(value=("value", "sum"),
                              tonnes=("tonnes", lambda s: s.sum(min_count=1)),
                              vq=("vq", "sum"))
    g["tonnes_cover"] = np.where(g.value > 0, g.vq / g.value.where(g.value > 0), np.nan)
    g = g.drop(columns="vq")
    g["share"] = g.value / total
    g["exporters"] = by_ex.groupby(level="hs6").size().reindex(g.index).fillna(0).astype(int)
    g["hhi"] = (sh ** 2).groupby(level="hs6").sum().reindex(g.index)
    g = g.reset_index()
    g.insert(0, "known", year + LAG)
    g.insert(0, "year", year)
    g.insert(0, "hs", edition)
    # the world total is the sum of the flows, to rounding
    assert abs(g.value.sum() - total) <= 1e-6 * total, (edition, year)
    return g[COLUMNS]


SHAPES = {  # output file -> its columns
    "hs6-essentials.csv.gz": ["hs", "year", "known", "hs6", "exporter", "importer", "value",
                              "tonnes", "uv"],
    "hs6-country.csv.gz": ["hs", "year", "known", "hs6", "country", "exports", "imports"],
    "hs2-pairs.csv.gz": ["hs", "year", "known", "hs2", "exporter", "importer", "value", "tonnes"],
}


def essential_codes(subnodes, check):
    """The HS6 codes of hs6-essentials: subnodes.csv's hs_code and hs6 (all
    editions), and the codes of the broad critical-materials tables of the
    check tool (the text from `(def eu-2023` to `(def subnode-lists`)."""
    sn = pd.read_csv(subnodes, dtype=str)
    codes = set(sn.hs_code.dropna()) | set(sn.hs6.dropna())
    text = open(check).read()
    lo, hi = text.index("(def eu-2023"), text.index("(def subnode-lists")
    codes |= set(re.findall(r'"(\d{6})"', text[lo:hi]))
    return {c.zfill(6) for c in codes if c.isdigit()}


TECH_CODES = {  # edition -> code prefixes kept in hs6-essentials for scripts/tech-trade.py
    "HS92": ("850780",),  # accumulators other than lead-acid and NiCd
    "HS12": ("850760",),  # Li-ion accumulators
    "HS17": ("8703",),    # every passenger car code (870380 battery-electric)
}


def shapes(df, edition, codes):
    """The rows of one year's flows for each SHAPES file."""
    year = int(df.year.iloc[0])
    known = year + LAG
    keep = df.hs6.isin(codes)
    if edition in TECH_CODES:
        keep |= df.hs6.str.startswith(TECH_CODES[edition])
    ess = df[keep].copy()
    ess.insert(0, "hs", edition)
    ess.insert(2, "known", known)
    ess["uv"] = np.where(ess.tonnes > 0, ess.value / ess.tonnes, np.nan)
    ex = df.groupby(["hs6", "exporter"]).value.sum().rename("exports")
    im = df.groupby(["hs6", "importer"]).value.sum().rename("imports")
    ex.index.names = im.index.names = ["hs6", "country"]
    cty = pd.concat([ex, im], axis=1).fillna(0.0).reset_index()
    cty.insert(0, "hs", edition)
    cty.insert(1, "year", year)
    cty.insert(2, "known", known)
    g = df.assign(hs2=df.hs6.str[:2]).groupby(["hs2", "exporter", "importer"])
    pairs = pd.concat([g.value.sum(), g.tonnes.sum(min_count=1)], axis=1).reset_index()
    pairs.insert(0, "hs", edition)
    pairs.insert(1, "year", year)
    pairs.insert(2, "known", known)
    total = df.value.sum()
    for t in (cty.exports.sum(), cty.imports.sum(), pairs.value.sum()):
        assert abs(t - total) <= 1e-6 * total, (edition, year)
    return {"hs6-essentials.csv.gz": ess, "hs6-country.csv.gz": cty, "hs2-pairs.csv.gz": pairs}


def keep_others(path, f, done):
    """Copy the rows of `path` (gzip CSV with hs first) whose edition is not
    in `done` to the open text file `f`."""
    if not os.path.exists(path):
        return
    with gzip.open(path, "rt") as g:
        next(g, None)
        for line in g:
            if line.split(",", 1)[0] not in done:
                f.write(line)


def run(root, out, editions, bilateral=True, codes=None, full=False,
        subnodes=None, check=None):
    """Read the editions' zips and write the outputs (see the module doc).
    bilateral False writes hs6-world.csv only; `codes` defaults to
    essential_codes(subnodes, check)."""
    os.makedirs(out, exist_ok=True)
    if bilateral and codes is None:
        codes = essential_codes(subnodes, check)
    worlds = []
    files = {}
    done = set()
    for ed in editions:
        d, fname = EDITIONS[ed]
        zips = [f for f in sorted(os.listdir(os.path.join(root, d)))
                if re.fullmatch(rf"BACI_{ed}_V\d+\.zip", f)] if os.path.isdir(os.path.join(root, d)) else []
        if not zips:
            print(f"{ed}: no zip under {os.path.join(root, d)}, skipped", file=sys.stderr)
            continue
        done.add(ed)
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
                for name, part in shapes(df, ed, codes).items():
                    if name not in files:
                        files[name] = gzip.open(os.path.join(out, name + ".part"), "wt", compresslevel=6)
                        files[name].write(",".join(SHAPES[name]) + "\n")
                    part[SHAPES[name]].to_csv(files[name], header=False, index=False,
                                              float_format="%.10g")
            if full:
                df.insert(1, "known", df.year + LAG)
                df["uv"] = np.where(df.tonnes > 0, df.value / df.tonnes, np.nan)
                df.to_csv(tmp, mode="w" if first else "a", header=first, index=False,
                          float_format="%.10g")
                first = False
                rows += len(df)
            print(ed, year, len(df), file=sys.stderr, flush=True)
        if full and not first:
            os.replace(tmp, path)
            print(f"{path}: {rows} rows", file=sys.stderr)
    for name, f in files.items():
        path = os.path.join(out, name)
        keep_others(path, f, done)
        f.close()
        os.replace(path + ".part", path)
        print(f"{path} written", file=sys.stderr)
    wpath = os.path.join(out, "hs6-world.csv")
    if not worlds:
        print(f"no edition read; {wpath} left as it was", file=sys.stderr)
        return
    w = pd.concat(worlds, ignore_index=True)
    done = set(w.hs)
    if os.path.exists(wpath):  # keep the editions this run did not read
        old = pd.read_csv(wpath, dtype={"hs6": str})
        old = old[~old.hs.isin(done)].reindex(columns=COLUMNS)
        if len(old):
            w = pd.concat([old, w], ignore_index=True)
    order = {e: i for i, e in enumerate(EDITIONS)}
    w = w.sort_values(["hs", "year", "hs6"], key=lambda c: c.map(order) if c.name == "hs" else c,
                      kind="stable")
    w.to_csv(wpath + ".part", index=False, float_format="%.10g")
    os.replace(wpath + ".part", wpath)
    print(f"{wpath}: {len(w)} rows", file=sys.stderr)


def selftest():
    """Checks on toy flows: python -I scripts/baci.py --selftest."""
    def flows(year, codes):
        return pd.DataFrame({"year": year, "hs6": codes, "exporter": "AAA", "importer": "BBB",
                             "value": 1.0, "tonnes": 2.0})
    codes = {"283691"}
    kept = lambda ed, df: sorted(shapes(df, ed, codes)["hs6-essentials.csv.gz"].hs6)
    tech = ["283691", "850780", "850760", "870321", "870380", "870899"]
    assert kept("HS92", flows(2000, tech)) == ["283691", "850780"], kept("HS92", flows(2000, tech))
    assert kept("HS12", flows(2015, tech)) == ["283691", "850760"], kept("HS12", flows(2015, tech))
    assert kept("HS17", flows(2020, tech)) == ["283691", "870321", "870380"], kept("HS17", flows(2020, tech))
    e = shapes(flows(2020, tech), "HS17", codes)["hs6-essentials.csv.gz"]
    assert list(e.columns) == ["hs", "year", "known", "hs6", "exporter", "importer", "value", "tonnes", "uv"]
    assert (e.known == 2022).all() and (e.uv == 0.5).all()
    print("baci selftest ok", file=sys.stderr)


def main():
    if sys.argv[1:] == ["--selftest"]:
        return selftest()
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--root", default="data/raw/material")
    p.add_argument("--out", default="data/derived/supply")
    p.add_argument("--editions", default="HS92,HS12,HS17")
    p.add_argument("--world-only", action="store_true",
                   help="write hs6-world.csv only")
    p.add_argument("--full", action="store_true",
                   help="also write the full bilateral files (hs6.csv, hs6-hs12.csv, hs6-hs17.csv)")
    p.add_argument("--subnodes", default="data/derived/supply/subnodes.csv")
    p.add_argument("--check", default="tools/bnm/essentials_check.clj",
                   help="the file holding the broad critical-materials mapping")
    a = p.parse_args()
    eds = [e.strip().upper() for e in a.editions.split(",") if e.strip()]
    bad = [e for e in eds if e not in EDITIONS]
    if bad:
        raise SystemExit(f"unknown editions {bad}; known: {sorted(EDITIONS)}")
    run(a.root, a.out, eds, bilateral=not a.world_only, full=a.full,
        subnodes=a.subnodes, check=a.check)


if __name__ == "__main__":
    main()
