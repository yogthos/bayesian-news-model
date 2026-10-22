"""Mine against processing output by country (beads bnm-1pq.4, bnm-3ka.3).

Writes, under data/derived/supply/ (gitignored: BGS data are licensed for
research use only and may not be passed on, so nothing derived from them is
committed):

  stages.csv        commodity,stage,iso,year,quantity,unit,source,known
                    one source per commodity and stage (below), so a series'
                    level never jumps where one source ends and another starts
  stages-alt.csv    the same columns from the sources not chosen (USGS MYB
                    world production), for cross-checks only
  stage-plants.csv  commodity,stage,iso,facility,capacity,unit,source,known
                    plant capacities from USGS MYB vol. III 2024 (facilities)

Stages are mine, smelter, refinery and chemical (none chosen yet: no source
held gives country output of lithium chemicals, rare-earth separation or
cobalt sulphate; see docs/ESSENTIALS.md section 5).

Sources and the choice per commodity and stage
----------------------------------------------
BGS World Mineral Statistics (data/raw/material/bgs-world-mineral-statistics),
production 1970-2024, one value per country, commodity and year. Chosen for
every stage except lithium: it is the longest single series (the 1975 start
the displacement targets need) and has no overlapping editions or subtotal
rows. Known = data year + 2 (BGS_LAG): the yearbook "World Mineral Production
2018-2022" came out in early 2024; the API's current vintage is used, so later
revisions are read early, which this cannot undo.
  Country codes: BGS codes the Republic of the Congo ("Congo") as COD, the
  same as the DRC; it is COG here. Zaire (ZAR in BGS's own older codes) is
  COD. The Soviet Union (SUN), Yugoslavia (YUG), Czechoslovakia (CSK) and East
  Germany (DDR) are kept as they were reported; FRG and Germany share DEU.
  Rhodesia is ZWE, Yemen Arab Republic YEM, Guernsey GGY.
  First years after coverage breaks: refined copper from 1977 (1976
  partial), primary gallium from 2011 (no Chinese output reported before),
  platinum from 1997 (South Africa missing 1977-95).
  Known oddities left in: Zaire's mined cobalt 1972-75 is 31-34 kt against
  10-15 kt either side (it sets the 1975 displacement); China's natural
  graphite is a larger series than USGS's (2.2 Mt in 1995 against 0.94 Mt in
  1994, and 81% of the world in 2000 where MYB has 51%); germanium is a few
  tonnes and erratic.
  Iron ore and manganese ore are gross weight (grades differ: China's low
  grade ore inflates its mine share). Refined lead and refined copper include
  secondary (scrap) metal; BGS does not split them. Ferro-chrome is left
  out (no Chinese output after 2009 in BGS). Crude steel (iron,
  refinery) is the steelmaking stage after pig iron, scrap-fed EAF included.

USGS MCS (data/raw/material/usgs-mcs), machine-readable editions 2022-2026,
data years 2020-2025. Chosen for lithium mine (lithium content): BGS's lithium
changes unit and coverage in 2012 (gross mineral weight before, three partial
lithium-content series after) and MYB mixes gross weight, carbonate equivalent
and content by country. Each data year takes the latest edition that reports
it, known = that edition's year (published each January), so a value is never
read before the edition it comes from. US output is withheld ("W") and so
missing. Editions before 2022 are PDF only (not parsed).

USGS MYB world production 1990-2022 (data/raw/material/usgs-myb) is not chosen
for any stage: it starts in 1990 and its edition overlaps and subtotal rows
double count (natural graphite reports "Total" next to "Amorphous" and
"Crystalline flake" from 2013, so summing details doubles China and four
others; older editions' "Unspecified" rows also survive a latest-per-detail
dedupe). It is written to stages-alt.csv for cobalt, rare earths, gallium and
graphite, deduplicated per country-year by taking only the latest edition that
reports it, and within it the largest "total" row if any, else the sum of
details. Known = edition + 2.

USGS DS140 (data/raw/material/usgs-ds140) is US supply and demand only, with
no output by country, so it gives no shares and is not used.

Plants: USGS MYB vol. III 2024 facilities, annual capacity by facility
(currentness 2025-2026: known = the currentness year). Vol. III covers
countries other than the United States, so US plants are missing. Active
plants only, primary (secondary/scrap rows dropped), the stage's dominant
content unit only, capacity shared across rows counted once.

Run: scratchpad venv python -I scripts/minerals.py
"""
import csv
import glob
import io
import os
import re
import sys
import zipfile
from collections import defaultdict

import pandas as pd
import pycountry

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "data/raw/material")
OUT = os.path.join(ROOT, "data/derived/supply")

BGS_LAG = 2
MYB_LAG = 2
STAGES = ("mine", "smelter", "refinery", "chemical")

# commodity, stage -> (BGS commodity, BGS sub-commodity or None, unit[, first year])
# A first year drops the years before a coverage break, so that a country
# missing from the series does not read as its share falling to nothing.
BGS = {
    ("cobalt", "mine"): ("cobalt, mine", None, "t Co"),
    ("cobalt", "refinery"): ("cobalt, refined", None, "t Co"),
    ("copper", "mine"): ("copper, mine", None, "t Cu"),
    ("copper", "smelter"): ("copper, smelter", None, "t Cu"),
    ("copper", "refinery"): ("copper, refined", None, "t Cu", 1977),  # 1976 is partial
    ("nickel", "mine"): ("nickel, mine", None, "t Ni"),
    ("nickel", "refinery"): ("nickel, smelter/refinery", None, "t Ni"),
    ("tin", "mine"): ("tin, mine", None, "t Sn"),
    ("tin", "smelter"): ("tin, smelter", None, "t Sn"),
    ("zinc", "mine"): ("zinc, mine", None, "t Zn"),
    ("zinc", "smelter"): ("zinc, slab", None, "t Zn"),  # slab zinc: the smelter (USGS facilities call it so)
    ("lead", "mine"): ("lead, mine", None, "t Pb"),
    ("lead", "refinery"): ("lead, refined", None, "t Pb"),
    ("aluminium", "mine"): ("bauxite", None, "t bauxite"),
    ("aluminium", "refinery"): ("alumina", None, "t Al2O3"),
    ("aluminium", "smelter"): ("aluminium, primary", None, "t Al"),
    ("iron", "mine"): ("iron ore", None, "t ore"),
    ("iron", "smelter"): ("iron, pig", None, "t pig iron"),
    ("iron", "refinery"): ("steel, crude", None, "t crude steel"),
    ("chromium", "mine"): ("chromium ores and concentrates", None, "t ore"),
    # chromium smelter (ferro-chrome) is left out: BGS has no Chinese
    # ferro-chrome after 2009 and world output halves in 2015, a coverage
    # break that would read as a fall in China's share
    ("manganese", "mine"): ("manganese ore", None, "t ore"),
    ("magnesium", "mine"): ("magnesite", None, "t magnesite"),
    ("magnesium", "smelter"): ("magnesium metal, primary", None, "t Mg"),
    ("rare-earths", "mine"): ("rare earth oxides", None, "t REO"),
    ("gallium", "refinery"): ("gallium, primary", None, "t Ga", 2011),  # no China before 2011
    ("germanium", "refinery"): ("germanium metal", None, "t Ge"),
    ("graphite", "mine"): ("graphite", None, "t graphite"),
    ("molybdenum", "mine"): ("molybdenum, mine", None, "t Mo"),
    ("tungsten", "mine"): ("tungsten, mine", None, "t W"),
    ("antimony", "mine"): ("antimony, mine", None, "t Sb"),
    ("vanadium", "mine"): ("vanadium, mine", None, "t V"),
    ("platinum", "mine"): ("platinum group metals, mine", "Platinum", "kg Pt", 1997),  # South Africa missing 1977-95
}

# BGS units the stage's quantities are reported in, checked per row
BGS_UNITS = {"t": {"tonnes (metric)", "tonnes (metal content)", "tonnes (Al2O3 content)"},
             "kg": {"kilograms", "kilograms (metal content)"}}

BGS_ISO = {"Rhodesia": "ZWE", "GUERNSEY": "GGY", "Yemen Arab Republic": "YEM",
           "Congo": "COG", "Zaire": "COD"}
BGS_CODE = {"ZAR": "COD"}

MCS = {("lithium", "mine"): ("lithium", "t Li")}

# commodity, stage -> (MYB commodity, form, unit detail or None, unit)
MYB = {
    ("cobalt", "mine"): ("Cobalt", "Mine", "Cobalt content", "t Co"),
    ("cobalt", "refinery"): ("Cobalt", "Refinery", "Cobalt content", "t Co"),
    ("rare-earths", "mine"): ("Rare earths", "Rare earths", "Rare-earth oxide equivalent", "t REO"),
    ("gallium", "refinery"): ("Gallium", "Primary", None, "t Ga"),
    ("graphite", "mine"): ("Graphite (natural)", "Mine", None, "t graphite"),
}
MYB_SCALE = {"Metric tons": 1.0, "Thousand metric tons": 1000.0, "Kilograms": 0.001,
             "Thousand kilograms": 1.0}

USGS_ISO = {
    "Burma": "MMR", "Myanmar (Burma)": "MMR", "Congo (Kinshasa)": "COD", "Zaire": "COD",
    "Congo (Brazzaville)": "COG", "Korea, North": "PRK", "North Korea": "PRK",
    "Korea, Republic of": "KOR", "Republic of Korea": "KOR", "South Korea": "KOR",
    "Korea": "KOR", "Russia": "RUS", "Iran": "IRN", "Laos": "LAO", "Syria": "SYR",
    "Vietnam": "VNM", "Bolivia": "BOL", "Venezuela": "VEN", "Tanzania": "TZA",
    "Moldova": "MDA", "Taiwan": "TWN", "Turkey": "TUR", "Brunei": "BRN",
    "Cote d'Ivoire": "CIV", "Côte d'Ivoire": "CIV", "Czech Republic": "CZE",
    "Czechoslovakia": "CSK", "Soviet Union, former": "SUN", "Yugoslavia": "YUG",
    "Serbia and Montenegro": "SCG", "Macedonia": "MKD", "Swaziland": "SWZ",
    "Cape Verde": "CPV", "Gambia, The": "GMB", "French Guiana (Guyane)": "GUF",
    "Germany, Western states": "DEU", "Germany, Eastern states": "DDR",
    "Netherlands Antilles": "ANT", "Kosovo": "XKX", "Réunion": "REU",
    "Macau": "MAC", "Hong Kong": "HKG", "Canada ": "CAN",
}
NOT_COUNTRY = re.compile(r"world|total|other|commonwealth|europe|asia|africa|america|oceania", re.I)


def usgs_iso(name):
    """ISO alpha-3 for a USGS short country name, None for aggregates."""
    if not isinstance(name, str):
        return None
    name = name.strip()
    if NOT_COUNTRY.search(name):
        return None
    if name in USGS_ISO:
        return USGS_ISO[name]
    try:
        return pycountry.countries.lookup(name).alpha_3
    except LookupError:
        return None


def number(x):
    """A float from a USGS or BGS cell ("1,700", "W", "", nan), else None."""
    if x is None:
        return None
    if isinstance(x, (int, float)):
        return None if pd.isna(x) else float(x)
    s = str(x).strip().replace(",", "")
    try:
        return float(s)
    except ValueError:
        return None


# ------------------------------------------------------------------ BGS

def bgs_rows():
    path = os.path.join(RAW, "bgs-world-mineral-statistics", "bgs_world_mineral_statistics_1970-2021.csv.gz")
    d = pd.read_csv(path, low_memory=False)
    d = d[d.bgs_statistic_type_trans == "Production"].copy()
    d["y"] = d.year.str[:4].astype(int)
    d["q"] = pd.to_numeric(d.quantity, errors="coerce")
    d["iso"] = [BGS_ISO[n] if n in BGS_ISO else (BGS_CODE.get(c, c) if isinstance(c, str) else None)
                for n, c in zip(d.country_trans, d.country_iso3_code)]
    out = []
    for (com, stage), (bc, sub, unit, *first) in BGS.items():
        x = d[d.bgs_commodity_trans == bc]
        if first:
            x = x[x.y >= first[0]]
        subs = (sub,) if isinstance(sub, str) else sub
        x = x[x.bgs_sub_commodity_trans.isna()] if sub is None else x[x.bgs_sub_commodity_trans.isin(subs)]
        if sub is not None and len(subs) > 1:
            both = x[x.q.notna()].groupby(["iso", "y"]).bgs_sub_commodity_trans.nunique()
            if (both > 1).any():
                sys.exit(f"BGS {bc}: a country-year reports several of {subs}")
        ok = BGS_UNITS[unit.split()[0]]
        bad = set(x.units.dropna()) - ok
        if bad:
            sys.exit(f"BGS {bc}/{sub}: units {bad} are not {unit}")
        x = x[x.iso.notna() & x.q.notna()]
        for (iso, y), q in x.groupby(["iso", "y"]).q.sum().items():
            if q > 0:
                out.append((com, stage, iso, int(y), q, unit, "BGS", int(y) + BGS_LAG))
    return out


# ------------------------------------------------------------------ MCS

def mcs_tables():
    """[(edition, DataFrame with Commodity, Country, Type, year, value)] for
    world production tables, editions 2022-2026."""
    base = os.path.join(RAW, "usgs-mcs")
    out = []
    for ed, pattern in ((2022, "2022/world.zip"), (2023, "2023/world.zip"), (2024, "2024/world.zip")):
        with zipfile.ZipFile(os.path.join(base, pattern)) as z:
            for n in z.namelist():
                if not n.endswith("_world.csv"):
                    continue
                m = re.search(r"mcs\d{4}-(\w+?)_world", n, re.I)
                if not m:
                    continue
                com = m.group(1).lower()[:5]
                t = pd.read_csv(io.BytesIO(z.read(n)), encoding="utf-8-sig", dtype=str, encoding_errors="replace")
                for c in t.columns:
                    m = re.match(r"Prod_t_(?:est_|Est_)?(\d{4})$", c, re.I)
                    if m:
                        for _, r in t.iterrows():
                            out.append((ed, com, r.get("Country"), r.get("Type"), int(m.group(1)), r[c]))
    with zipfile.ZipFile(os.path.join(base, "2025/World_Data_Release_MCS_2025.zip")) as z:
        t = pd.read_csv(io.BytesIO(z.read("MCS2025_World_Data.csv")), encoding="utf-8-sig", dtype=str,
                        encoding_errors="replace")
        t.columns = [c.strip() for c in t.columns]
        for c in t.columns:
            m = re.match(r"PROD_(?:EST_\s*)?(\d{4})$", c)
            if m:
                for _, r in t.iterrows():
                    out.append((2025, str(r.COMMODITY).strip().lower()[:5], r.COUNTRY, r.TYPE, int(m.group(1)), r[c]))
    t = pd.read_csv(os.path.join(base, "2026/MCS2026_Commodities_Data.csv"), dtype=str, encoding="cp1252",
                    encoding_errors="replace")
    t = t[t.Section.str.startswith("World", na=False) & (t.Statistics == "Production")]
    for _, r in t.iterrows():
        y = number(r.Year)
        if y:
            out.append((2026, str(r.Commodity).strip().lower()[:5], r.Country,
                        r.Statistics_detail, int(y), r.Value))
    return pd.DataFrame(out, columns=["ed", "com", "country", "type", "year", "value"])


def mcs_rows():
    t = mcs_tables()
    out = []
    for (com, stage), (key, unit) in MCS.items():
        x = t[(t.com == key[:5]) & t.type.str.contains("mine production", case=False, na=False)].copy()
        x["iso"] = x.country.map(usgs_iso)
        x["q"] = x.value.map(number)
        x = x[x.iso.notna()]
        # each data year from the latest edition reporting it
        latest = x.groupby("year").ed.max()
        x = x[x.ed == x.year.map(latest)]
        for _, r in x.iterrows():
            if r.q is not None and r.q > 0:
                out.append((com, stage, r.iso, int(r.year), r.q, unit, f"MCS{int(r.ed)}", int(r.ed)))
    return out


# ------------------------------------------------------------------ MYB

def myb_dedup(rows):
    """One quantity per country-year from MYB rows of one commodity-form:
    only the latest edition reporting that country-year; within it the
    largest row whose detail says "total", else the sum of the details.
    rows: dicts with ed, detail, q. Returns (q, ed)."""
    ed = max(r["ed"] for r in rows)
    rs = [r for r in rows if r["ed"] == ed and r["q"] is not None]
    tot = [r["q"] for r in rs if re.search(r"total", str(r["detail"]), re.I)]
    return (max(tot) if tot else sum(r["q"] for r in rs)), ed


def myb_rows():
    path = os.path.join(RAW, "usgs-myb", "world-production-1990-2022", "USGS_MYB_V1_Production_Data_Release.csv")
    d = pd.read_csv(path, low_memory=False, encoding="utf-8-sig")
    d["ed"] = d.DATA_SOURCE.str.extract(r"MYB_(\d{4})")[0].astype(float)
    d["iso"] = d.COUNTRY_OR_REGION.map(usgs_iso)
    d["q"] = pd.to_numeric(d.QUANTITY, errors="coerce") * d.UNITS.map(MYB_SCALE)
    out = []
    for (com, stage), (c, form, ud, unit) in MYB.items():
        x = d[(d.COMMODITY == c) & (d.COMMODITY_FORM == form) & d.iso.notna() & d.ed.notna()]
        if ud:
            x = x[x.UNIT_DETAIL == ud]
        groups = defaultdict(list)
        for r in x.itertuples():
            groups[(r.iso, int(r.YEAR))].append({"ed": int(r.ed), "detail": r.FORM_DETAIL,
                                                 "q": None if pd.isna(r.q) else r.q})
        for (iso, y), rs in groups.items():
            q, ed = myb_dedup(rs)
            if q > 0:
                out.append((com, stage, iso, y, q, unit, f"MYB{ed}", ed + MYB_LAG))
    return out


# ------------------------------------------------------------------ plants

# MYB vol. III Level 2 commodity -> our commodity; stage from Level 3 (Type)
# where the table gives it, else from the feature type
PLANT_COMMODITY = {"Cobalt": "cobalt", "Copper": "copper", "Nickel": "nickel", "Tin": "tin",
                   "Zinc": "zinc", "Lead": "lead", "Rare earths": "rare-earths", "Graphite": "graphite",
                   "Gallium": "gallium", "Germanium": "germanium", "Lithium": "lithium",
                   "Bauxite": "aluminium", "Alumina": "aluminium", "Aluminum": "aluminium",
                   "Molybdenum": "molybdenum", "Tungsten": "tungsten", "Antimony": "antimony",
                   "Vanadium": "vanadium", "Magnesium": "magnesium", "Magnesite": "magnesium"}


def plant_stage(level2, ftype, level3):
    if level2 == "Alumina":
        return "refinery"
    if level2 == "Aluminum":
        return "smelter"
    if level2 == "Magnesium":
        return "smelter"
    if isinstance(level3, str) and level3.lower() in ("mine", "smelter", "refinery"):
        return level3.lower()
    if level2 in ("Gallium", "Germanium"):
        return "refinery"
    if isinstance(ftype, str) and ftype.startswith("Mines"):
        return "mine"
    return None


def plant_rows():
    path = os.path.join(RAW, "usgs-myb", "2024-vol3", "2024MYBv3_Facilities_table.csv")
    d = pd.read_csv(path, low_memory=False, encoding="utf-8-sig")
    d = d[d["Facility Status"].str.lower().str.startswith("assumed active", na=False)]
    d = d[d["Level 4 (Phase)"].fillna("") != "Secondary"]
    d["commodity"] = d["Level 2 (Commodity)"].map(PLANT_COMMODITY)
    d["stage"] = [plant_stage(a, b, c) for a, b, c in
                  zip(d["Level 2 (Commodity)"], d["Feature Type"], d["Level 3 (Type)"])]
    d["iso"] = d["Country (Short Form) or Locality"].map(usgs_iso)
    d["cap"] = pd.to_numeric(d["Annual Production Capacity"], errors="coerce")
    d["facility"] = d["Facility Name"]
    d["known"] = pd.to_datetime(d["Currentness Date"], errors="coerce").dt.year
    d = d[d.commodity.notna() & d.stage.notna() & d.iso.notna() & (d.cap > 0) & d.known.notna()]
    out = []
    for (com, stage), x in d.groupby(["commodity", "stage"]):
        # the stage's dominant unit, by number of plants
        unit = x["Commodity Unit"].value_counts().idxmax()
        x = x[x["Commodity Unit"] == unit]
        # a capacity shared across rows (several products) counts once
        x = x.assign(key=x["Shared Capacity"].fillna(x["Global Entry"]))
        x = x.sort_values("cap").groupby("key").last()
        for r in x.itertuples():
            out.append((com, stage, r.iso, r.facility, r.cap, unit, "MYB2024v3", int(r.known)))
    return out


# ------------------------------------------------------------------ write

def write(name, header, rows):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    rows = sorted(rows, key=lambda r: tuple(str(v) for v in r[:4]))
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        for r in rows:
            w.writerow([f"{v:.6g}" if isinstance(v, float) else v for v in r])
    print(f"{path}: {len(rows)} rows")


def check(rows):
    """One source and one unit per commodity-stage; stages known; known
    after the data year."""
    src, unit = defaultdict(set), defaultdict(set)
    for com, stage, iso, y, q, u, s, k in rows:
        assert stage in STAGES, stage
        assert k > y, (com, stage, iso, y, k)
        src[(com, stage)].add(re.sub(r"\d+$", "", s))
        unit[(com, stage)].add(u)
    for key in src:
        assert len(src[key]) == 1, (key, src[key])
        assert len(unit[key]) == 1, (key, unit[key])


def main():
    rows = bgs_rows() + mcs_rows()
    check(rows)
    write("stages.csv", ["commodity", "stage", "iso", "year", "quantity", "unit", "source", "known"], rows)
    write("stages-alt.csv", ["commodity", "stage", "iso", "year", "quantity", "unit", "source", "known"], myb_rows())
    write("stage-plants.csv", ["commodity", "stage", "iso", "facility", "capacity", "unit", "source", "known"],
          plant_rows())


if __name__ == "__main__":
    main()
