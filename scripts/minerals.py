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
  stages-chem.csv   commodity,stage,iso,year,known,quantity,unit,method,
                    net_exports,domestic_use,capacity,utilisation,world
                    how each chemical-stage value was built (below)

Stages are mine, smelter, refinery, chemical and separation. No source held
gives country output of lithium chemicals or rare-earth separation, so those
two (lithium "chemical", t LCE; rare-earths "separation", t REO; bead
bnm-1pq.15) are built from trade, plant lists and a mass balance against the
mine stage: see "chemical stages" below. They are added to stages.csv and
stage-plants.csv without changing any other row. Cobalt sulphate is still
missing.

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
  Repaired: Zaire's and Zambia's mined cobalt in 1972-75 is ore content in
  BGS (31-34 kt for Zaire against 10-15 kt either side) where every other
  year is recovered metal; those four years take the refined value
  (BGS_REPAIR).
  Known oddities left in: Russia's mined vanadium is missing in 2000-01
  and steps up in 2002 (slag counted); China's natural
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
and content by country. Every edition's value of a data year is written, known
= that edition's year (published each January): the edition-year estimate of
the year before is known at the edition year (lag 1), its revision a year
later (lag 2); bnm.stages reads only the latest edition known (the source
column names it). US output is withheld ("W") and so missing. Editions
before 2022 are PDF only (not parsed).

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
(currentness 2025-2026: known = the currentness year), which covers
countries other than the United States, plus the USGS US facilities
worksheet (same layout, 2026). Active plants only, primary (secondary/scrap
rows dropped), the stage's dominant content unit only, capacity shared
across rows counted once. Nickel's smelters, pig-iron and mixed-hydroxide
plants are the refinery stage, as BGS's nickel smelter/refinery is (without
them Indonesia had no plant). MYB 2024 has no Chinese copper smelter; those
come from the USGS China GIS compilation (2023, known 2023, capacity in
blister/anode metal). SFI (steel, cement, ethylene) and OGIM (oil and gas)
have no plants of these stages.

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
STAGES = ("mine", "smelter", "refinery", "chemical", "separation")

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
    return bgs_repair(out)


# commodity, stage -> (stage taken from, countries, years): BGS values
# replaced by another stage's where the series changes definition
BGS_REPAIR = {
    # Zaire's and Zambia's mined cobalt is their recovered (refined) metal in
    # every BGS year (1976 on, Zaire's mine and refined rows are equal),
    # except 1972-75, where it is the cobalt contained in ore, about twice
    # as much (Zaire 30.8-33.9 kt against 13.0-17.5 kt refined; Zambia 3.0-
    # 5.2 kt against 1.9-2.1 kt). World mine output doubles in 1972 and
    # halves in 1976 with it.
    ("cobalt", "mine"): ("refinery", ("COD", "ZMB"), range(1972, 1976)),
}


def bgs_repair(rows):
    q = {(c, s, i, y): v for c, s, i, y, v, *_ in rows}
    out = []
    for r in rows:
        com, stage, iso, y = r[:4]
        rep = BGS_REPAIR.get((com, stage))
        if rep and iso in rep[1] and y in rep[2]:
            src = q.get((com, rep[0], iso, y))
            if src is None:
                sys.exit(f"BGS repair: no {com} {rep[0]} for {iso} {y}")
            r = (*r[:4], src, *r[5:])
        out.append(r)
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
        # every edition's value of a data year (the estimate, then each
        # revision), known at that edition; bnm.stages reads the latest
        # edition known by then and never sums editions
        if x.duplicated(["ed", "iso", "year"]).any():
            sys.exit(f"MCS {key}: several rows for an edition, country and year")
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


# nickel plant forms that are chemicals (sulphate, chloride), not the BGS
# smelter/refinery stage
NI_CHEMICAL = re.compile(r"sulfate|chloride|chemical|compounds", re.I)


def plant_stage(level2, ftype, level3, descriptor=None):
    if level2 == "Alumina":
        return "refinery"
    if level2 == "Aluminum":
        return "smelter"
    if level2 == "Magnesium":
        return "smelter"
    if level2 == "Nickel" and not (isinstance(ftype, str) and ftype.startswith("Mines") and level3 != "Refinery"):
        # BGS has one nickel processing stage, smelter/refinery: matte
        # smelters, ferronickel and pig iron, mixed hydroxide (HPAL) and
        # refineries all report to it
        if level3 in ("Smelter", "Refinery") or (
                not isinstance(level3, str) and not NI_CHEMICAL.search(str(descriptor or ""))):
            return "refinery"
        return None
    if isinstance(level3, str) and level3.lower() in ("mine", "smelter", "refinery"):
        return level3.lower()
    if level2 in ("Gallium", "Germanium"):
        return "refinery"
    if isinstance(ftype, str) and ftype.startswith("Mines"):
        return "mine"
    return None


def vol3_table():
    """MYB vol. III 2024 facilities (countries other than the US) and the
    USGS US facilities worksheet (2026, same layout) as one table."""
    v3 = pd.read_csv(os.path.join(RAW, "usgs-myb", "2024-vol3", "2024MYBv3_Facilities_table.csv"),
                     low_memory=False, encoding="utf-8-sig")
    v3["known"] = pd.to_datetime(v3["Currentness Date"], errors="coerce").dt.year
    v3["source"] = "MYB2024v3"
    us = pd.read_excel(os.path.join(RAW, "usgs-us-facilities",
                                    "Mines, smelters, refineries, and recycling facilities in the United States_worksheet.xlsx"),
                       sheet_name=0)
    us = us.rename(columns={"Global entry": "Global Entry", "Country (Short Form)": "Country (Short Form) or Locality",
                            "GMAS Descriptor": "Descriptor"})
    us["Country (Short Form) or Locality"] = "United States"
    us["known"] = pd.to_numeric(us["Date Updated"], errors="coerce")
    us["source"] = "USGS-US2026"
    cols = ["Global Entry", "Country (Short Form) or Locality", "Facility Name", "Feature Type",
            "Level 2 (Commodity)", "Level 3 (Type)", "Level 4 (Phase)", "Level 5 (Form)", "Descriptor", "Commodity Unit",
            "Annual Production Capacity", "Shared Capacity", "Facility Status", "known", "source"]
    return pd.concat([v3[cols], us[cols]], ignore_index=True)


# China compilation (USGS, 2023; from MYB 2019 vol. III and company
# reports): fills a commodity-stage-country the 2024 table has no plant for.
# Only China's copper smelters so far (MYB 2024 lists its copper refineries
# but no smelter).
CHN_GIS_FILL = {("Copper", "Smelted"): ("copper", "smelter")}
CHN_GIS_KNOWN = 2023
CHN_GIS_SCALE = {"kmt": 1000.0, "mt": 1.0}


def chn_gis_rows():
    """[(commodity, stage, facility, capacity t)] from the China compilation."""
    import pyogrio.raw
    path = f"/vsizip/{RAW}/usgs-gis-compilations/china/CHN_GIS.gdb.zip/CHN_GIS.gdb"
    meta, _, _, fields = pyogrio.raw.read(path, layer="CHN_Mineral_Facilities", read_geometry=False)
    d = pd.DataFrame({n: f for n, f in zip(meta["fields"], fields)})
    d = d[(d.FeatureTyp == "Mineral Processing Plants") & (d.LocOpStat == "Assumed Active")]
    out = []
    for r in d.itertuples():
        key = CHN_GIS_FILL.get((r.DsgAttr01, str(r.DsgAttr05).strip()))
        scale = CHN_GIS_SCALE.get(str(r.DsgAttr07).strip())
        if key and scale and r.DsgAttr06 and r.DsgAttr06 > 0:
            out.append((*key, r.FeatureNam, float(r.DsgAttr06) * scale))
    return out


def plant_rows():
    d = vol3_table()
    d = d[d["Facility Status"].str.lower().str.startswith("assumed active", na=False)]
    d = d[d["Level 4 (Phase)"].fillna("") != "Secondary"]
    d["commodity"] = d["Level 2 (Commodity)"].map(PLANT_COMMODITY)
    d["stage"] = [plant_stage(a, b, c, e) for a, b, c, e in
                  zip(d["Level 2 (Commodity)"], d["Feature Type"], d["Level 3 (Type)"], d["Descriptor"])]
    d["iso"] = d["Country (Short Form) or Locality"].map(usgs_iso)
    d["cap"] = pd.to_numeric(d["Annual Production Capacity"], errors="coerce")
    d["facility"] = d["Facility Name"]
    d = d[d.commodity.notna() & d.stage.notna() & d.iso.notna() & (d.cap > 0) & d.known.notna()]
    out = []
    for (com, stage), x in d.groupby(["commodity", "stage"]):
        # the stage's dominant unit, by number of plants
        unit = x["Commodity Unit"].value_counts().idxmax()
        x = x[x["Commodity Unit"] == unit]
        # a capacity shared across rows (several products) counts once,
        # with the largest row's own facility and country
        x = x.assign(key=x["Shared Capacity"].fillna(x["Global Entry"]))
        x = x.sort_values("cap").drop_duplicates("key", keep="last")
        for r in x.itertuples():
            out.append((com, stage, r.iso, r.facility, r.cap, unit, r.source, int(r.known)))
    have = {(c, s, i) for c, s, i, *_ in out}
    units = {(c, s): u for c, s, i, f, q, u, *_ in out}
    for com, stage, fac, cap in chn_gis_rows():
        if (com, stage, "CHN") not in have and (com, stage) in units:
            out.append((com, stage, "CHN", fac, cap, units[(com, stage)], "USGS-CHN-GIS2023", CHN_GIS_KNOWN))
    return out


# ------------------------------------------------------------------ chemical stages
#
# Lithium chemicals (stage "chemical", t LCE) and rare-earth separation
# (stage "separation", t REO), bead bnm-1pq.15. No source gives country
# output, so it is built; every value's method and inputs go to
# stages-chem.csv. Methods:
#
#   trade     P = max(0, NX + U): net exports NX of the stage's chemicals in
#             BACI (HS92 tonnes; lithium carbonate 283691, lithium oxide and
#             hydroxide 282520, rare-earth compounds 284610 and 284690) plus
#             domestic use U. U is a held figure only for US lithium (MCS
#             salient consumption; withheld from data year 2023, when the
#             last published figure is held). Elsewhere U = 0: what a
#             producer outside China uses at home is assumed small, since no
#             battery cell or magnet output by country is held to estimate
#             it from. Only countries with a chemical-stage plant on record
#             count ("gated"): Myanmar's and Laos's rare-earth compounds are
#             mixed carbonate from ionic clays, an intermediate China
#             separates, and Canada's 95 kt of hydroxide in 2018, or the
#             Netherlands' and Nigeria's lithium, are re-exports or
#             misreports with no plant behind them.
#   reported  the output a held source reports: US rare-earth compounds and
#             metals (MCS salient statistics, data years 2017-2025).
#   balance   China closes the mass balance against the mine stage: world
#             chemical output equals world mine output that year (no stock
#             change, no losses; lithium mine output in LCE), so
#             P_CHN = W - the others. Net exports sum to zero over the
#             world, so this is P_CHN = NX_CHN + U_CHN with U_CHN world use
#             less the others' (a consumer with no plant uses its net
#             imports); stages-chem.csv carries NX_CHN and the implied U_CHN.
#   facility  the facility year 2025 (MYB vol. III 2024, currentness
#             2025-26, and the USGS US worksheet 2026): P = capacity x u for
#             each country with a listed capacity, one utilisation
#             u = min(1, W / listed world capacity), W the MCS 2026 world
#             mine output of 2025 (above listed capacity for both, so u = 1:
#             the others at capacity, an upper bound); the US's rare earths
#             as reported; China closes the balance, since MYB lists none of
#             its lithium hydroxide plants.
#
# Gate evidence and when it is known: active chemical plants with a stated
# capacity in MYB vol. III 2024 / USGS US (known 2026; Austria's rare-earth
# chemicals plant and Russia's lithium plants state none and do not gate); active processing plants (and, for lithium,
# brine operations, which make carbonate at the salar) in
# data/derived/cells/assets.csv from the USGS GIS compilations (known_from:
# Latin America 2016, the others 2022-24; rows with dup_of skipped). A data
# year is written once per evidence year (an edition, source
# BNM-CHEM<year>, known that year and no earlier than BACI's data year + 2),
# so bnm.stages reads the latest edition known by K and China's balance
# never uses a plant list from after K. An edition is written only once
# China's own plants are on record (2023), so trade years are known from
# 2023 at the earliest.
#
# Units: LCE = Li x 5.323; LiOH.H2O 0.880 t LCE per t (BACI 282520 taken as
# monohydrate, the traded battery grade); LiCl 0.871. Rare-earth compounds
# in BACI and plant capacities in gross weight are taken as REO one for one
# (separated oxides; carbonates and chlorides hold less, which overstates
# such exporters a little).
#
# Known limits: the plant lists are snapshots, so a plant built in 2024
# gates its country's earlier years too (harmless where its net exports
# were negative, as Korea's lithium); a separator whose feedstock arrives as
# compounds (Estonia's Silmet) has negative net exports in the same codes
# and reads as zero in trade years; Japan and France separated rare earths
# in the 1990s and have no plant on record, so they read as zero; MYB lists
# China's lithium carbonate plants but none of its hydroxide plants, which
# would understate China in the facility year were it not the closing
# country there. Flows without tonnes in BACI (3-10% of flows) are left out.

LCE_PER_LI = 5.323
LCE_PER = {"carbonate": 1.0, "hydroxide": 0.880, "chloride": 0.871}

CHEM = {
    "lithium": dict(stage="chemical", unit="t LCE", hs={"283691": 1.0, "282520": LCE_PER["hydroxide"]},
                    mine_scale=LCE_PER_LI, mcs="lithi", level2="Lithium",
                    assets=("lithium",), asset_types=("processing_plant", "brine")),
    "rare-earths": dict(stage="separation", unit="t REO", hs={"284610": 1.0, "284690": 1.0},
                        mine_scale=1.0, mcs="rare ", level2="Rare earths",
                        assets=("rare earths", "rare earth elements (ree)"), asset_types=("processing_plant",)),
}
CHEM_STAGES = {(c, v["stage"]) for c, v in CHEM.items()}
CHEM_CLOSING = "CHN"
CHEM_SOURCE = "BNM-CHEM"
CHEM_FACILITY_YEAR = 2025
ACTIVE = re.compile(r"^(a|active|assumed active)$", re.I)
# rare-earth plants that are not separation: mixed intermediates,
# concentrate, metal, magnets, other elements
RE_NOT_SEPARATION = re.compile(r"mixed|concentrat|magnet|metal|cesium|ndfeb", re.I)


def chem_capacity_factor(com, unit, form):
    """Factor from a plant row's capacity to the stage unit, or None when
    its unit does not convert."""
    unit, form = str(unit), str(form).lower()
    if com == "lithium":
        if unit == "Li2CO3 content":
            return 1.0
        if unit == "Li content":
            return LCE_PER_LI
        if unit == "Gross weight":
            return next((f for k, f in LCE_PER.items() if k in form), None)
        return None
    if unit in ("Oxide content", "Rare-earth-oxide equivalent", "Gross weight", "Compounds weight"):
        return 1.0
    return None


def chem_plants(d=None):
    """[(commodity, stage, iso, facility, capacity or None, unit, source,
    known)]: active, primary chemical-stage plants in MYB vol. III 2024 and
    the USGS US worksheet. Capacity None where none is given (-999) or its
    unit does not convert; the plant still gates its country."""
    d = vol3_table() if d is None else d
    d = d[d["Facility Status"].str.lower().str.startswith("assumed active", na=False)]
    d = d[d["Level 4 (Phase)"].fillna("") != "Secondary"]
    out = []
    for com, c in CHEM.items():
        x = d[d["Level 2 (Commodity)"] == c["level2"]]
        if com == "lithium":
            x = x[(x["Level 3 (Type)"] == "Refinery") |
                  (x["Level 3 (Type)"].isna() & x["Descriptor"].str.contains("carbonate", case=False, na=False))]
        else:
            x = x[(x["Feature Type"] == "Mineral Processing Plant") & (x["Level 3 (Type)"].fillna("") != "Mine")
                  & ~x["Descriptor"].fillna("").str.contains(RE_NOT_SEPARATION)]
        x = x.assign(iso=x["Country (Short Form) or Locality"].map(usgs_iso),
                     cap=pd.to_numeric(x["Annual Production Capacity"], errors="coerce"),
                     fac=x["Facility Name"],
                     f=[chem_capacity_factor(com, u, fm) for u, fm in zip(x["Commodity Unit"], x["Level 5 (Form)"])])
        x = x[x.iso.notna() & x.known.notna()]
        x = x.assign(q=[q * f if (f is not None and q > 0) else float("nan") for q, f in zip(x.cap, x.f)],
                     key=x["Shared Capacity"].fillna(x["Global Entry"]))
        # a capacity shared across rows counts once
        x = x.sort_values("q", na_position="first").drop_duplicates("key", keep="last")
        for r in x.itertuples():
            out.append((com, c["stage"], r.iso, r.fac, None if pd.isna(r.q) else float(r.q), c["unit"],
                        r.source, int(r.known)))
    return out


def chem_asset_gates(path=None):
    """{(commodity, iso): earliest known year} of active chemical-stage
    assets (processing plants; lithium brines) in the cell asset table."""
    path = path or os.path.join(ROOT, "data/derived/cells/assets.csv")
    out = {}
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if r["dup_of"] or not ACTIVE.match(r["status"].strip()) or not r["known_from"] or not r["iso"]:
                continue
            for com, c in CHEM.items():
                if r["commodity"] in c["assets"] and r["asset_type"] in c["asset_types"]:
                    k = int(float(r["known_from"]))
                    out[(com, r["iso"])] = min(k, out.get((com, r["iso"]), k))
    return out


def chem_trade(path=None):
    """{(commodity, iso, year): (net exports in the stage unit, known)} from
    BACI HS92 tonnes (flows without tonnes left out)."""
    path = path or os.path.join(OUT, "hs6-essentials.csv.gz")
    codes = {h: (com, f) for com, c in CHEM.items() for h, f in c["hs"].items()}
    out, known = defaultdict(float), {}
    for ch in pd.read_csv(path, dtype={"hs6": str}, chunksize=1_000_000):
        ch = ch[(ch.hs == "HS92") & ch.hs6.isin(list(codes)) & ch.tonnes.notna()]
        for hs6, ex, im, y, kn, t in zip(ch.hs6, ch.exporter, ch.importer, ch.year, ch.known, ch.tonnes):
            com, f = codes[hs6]
            out[(com, ex, int(y))] += f * t
            out[(com, im, int(y))] -= f * t
            known[int(y)] = int(kn)
    return {k: (v, known[k[2]]) for k, v in out.items()}


def mcs_us_salient():
    """{(commodity, statistic, year): [(edition, value)]} of two US salient
    statistics: lithium consumption (t Li) and rare-earth production of
    compounds and metals (t REO). Withheld values ("W") are left out."""
    base = os.path.join(RAW, "usgs-mcs")
    cols = {"lithium": ("Consump_t", "consumption"), "rare-earths": ("USprod_Compounds-Metals_t", "production")}
    out = defaultdict(list)

    def add(com, stat, y, ed, v):
        v, y = number(v), number(y)
        if v is not None and y is not None:
            out[(com, stat, int(y))].append((ed, v))

    for ed, zpath in ((2022, "2022/salient.zip"), (2023, "2023/salient.zip"), (2024, "2024/salient.zip"),
                      (2025, "2025/Salient_Commodity_Data_Release_Grouped_MCS_2025.zip")):
        with zipfile.ZipFile(os.path.join(base, zpath)) as z:
            for n in z.namelist():
                m = re.search(r"mcs\d{4}-(lithi|lithium|raree|rareee)_salient\.csv$", n)
                if not m:
                    continue
                com = "lithium" if m.group(1).startswith("lithi") else "rare-earths"
                col, stat = cols[com]
                t = pd.read_csv(io.BytesIO(z.read(n)), encoding="utf-8-sig", dtype=str)
                for y, v in zip(t.Year, t[col]):
                    add(com, stat, y, ed, v)
    t = pd.read_csv(os.path.join(base, "2026/MCS2026_Commodities_Data.csv"), dtype=str, encoding="cp1252",
                    encoding_errors="replace")
    t = t[t.Section.str.startswith("Salient", na=False)]
    for com_, det, y, v in zip(t.Commodity, t.Statistics_detail.astype(str), t.Year, t.Value):
        if com_ == "Lithium" and det.startswith("Consumption"):
            add("lithium", "consumption", y, 2026, v)
        elif com_ == "Rare Earths" and det == "Production: Compounds and metals":
            add("rare-earths", "production", y, 2026, v)
    return out


def latest_by(eds, k):
    """The value of the latest edition known by k in [(edition, value)]."""
    eds = [(e, v) for e, v in eds if e <= k]
    return max(eds)[1] if eds else None


def chem_world(stage_rows, com, y, k):
    """(world mine output of com in data year y in the chemical stage's
    unit, from its latest edition known by k; that edition's known year),
    or None."""
    rs = [r for r in stage_rows if r[0] == com and r[1] == "mine" and r[3] == y and r[7] <= k]
    if not rs:
        return None
    src = max(rs, key=lambda r: r[7])[6]
    rs = [r for r in rs if r[6] == src]
    return sum(r[4] for r in rs) * CHEM[com]["mine_scale"], max(r[7] for r in rs)


def chem_balance(nx, gated, world, use=None, reported=None, closing=CHEM_CLOSING):
    """{iso: (quantity, method, net exports, domestic use)} for one data
    year. Gated countries other than the closing one: as reported, else
    max(0, NX + U). The closing country: world output less the others, with
    its implied domestic use. nx {iso: net exports}, gated a set of isos
    (the closing one among them), world the stage's world output, use
    {iso: U}, reported {iso: quantity}. Raises when the others exceed world
    output."""
    use, reported = use or {}, reported or {}
    out = {}
    for iso in sorted(set(gated) - {closing}):
        n = nx.get(iso, 0.0)
        if iso in reported:
            out[iso] = (reported[iso], "reported", n, None)
        else:
            out[iso] = (max(0.0, n + use.get(iso, 0.0)), "trade+use" if iso in use else "trade", n, use.get(iso))
    rest = world - sum(v[0] for v in out.values())
    if rest < 0:
        raise ValueError(f"chemical balance: others {world - rest:.0f} exceed world {world:.0f}")
    n = nx.get(closing, 0.0)
    out[closing] = (rest, "balance", n, rest - n)
    return out


def chem_facility(caps, world, reported=None, closing=CHEM_CLOSING):
    """{iso: (quantity, method, capacity, utilisation)} for the facility
    year: each country other than the closing one at its listed capacity x
    one utilisation u = min(1, world / listed world capacity), or as
    reported; the closing country the rest of world output (its own
    listing is incomplete: MYB has none of China's lithium hydroxide
    plants). caps {iso: capacity}, the closing country's among them."""
    reported = reported or {}
    tot = sum(caps.values())
    u = min(1.0, world / tot) if tot > 0 else 0.0
    out = {}
    for iso, q in sorted(caps.items()):
        if iso == closing:
            continue
        out[iso] = (reported[iso], "reported", q, None) if iso in reported else (q * u, "facility", q, u)
    rest = world - sum(v[0] for v in out.values())
    if rest < 0:
        raise ValueError(f"facility balance: others {world - rest:.0f} exceed world {world:.0f}")
    out[closing] = (rest, "balance", caps.get(closing), None)
    return out


def chem_us(com, y, k, salient):
    """(domestic use, reported output) for the US in data year y as known
    by k: lithium consumption (MCS; withheld from data year 2023, so the
    last figure published by k is held), rare-earth compounds and metals
    produced."""
    if com == "lithium":
        pub = [(yy, latest_by(eds, k)) for (cm, st, yy), eds in salient.items()
               if cm == com and st == "consumption" and yy <= y and latest_by(eds, k) is not None]
        return ({"USA": max(pub)[1] * LCE_PER_LI} if pub else {}), {}
    v = latest_by(salient.get((com, "production", y), []), k)
    return {}, ({"USA": v} if v is not None else {})


def chem_rows(stage_rows, trade=None, plants=None, gates=None, salient=None, mcs=None):
    """(stage rows, provenance rows) of the chemical stages. Provenance:
    commodity, stage, iso, year, known, quantity, unit, method, net_exports,
    domestic_use, capacity, utilisation, world."""
    trade = chem_trade() if trade is None else trade
    plants = chem_plants() if plants is None else plants
    gates = dict(chem_asset_gates() if gates is None else gates)
    salient = mcs_us_salient() if salient is None else salient
    mcs = mcs_tables() if mcs is None else mcs
    for com, stage, iso, fac, cap, unit, src, k in plants:
        if cap:
            gates[(com, iso)] = min(k, gates.get((com, iso), k))
    rows, prov = [], []
    for com, c in CHEM.items():
        g = {iso: k for (cm, iso), k in gates.items() if cm == com}
        by_year = defaultdict(dict)
        k0s = defaultdict(int)
        for (cm, iso, y), (v, kn) in trade.items():
            if cm == com:
                by_year[y][iso] = v
                k0s[y] = max(k0s[y], kn)
        for y in sorted(by_year):
            for e in sorted({max(k0s[y], kg) for kg in g.values()}):
                gated = {iso for iso, kg in g.items() if kg <= e}
                w = chem_world(stage_rows, com, y, e)
                if CHEM_CLOSING not in gated or w is None:
                    continue
                world = w[0]
                use, rep = chem_us(com, y, e, salient)
                for iso, (q, method, n, u) in chem_balance(by_year[y], gated, world, use, rep).items():
                    prov.append((com, c["stage"], iso, y, e, q, c["unit"], method, n, u, None, None, world))
                    if q > 0:
                        rows.append((com, c["stage"], iso, y, q, c["unit"], f"{CHEM_SOURCE}{e}", e))
        # the facility year
        caps, kf = defaultdict(float), 0
        for cm, stage, iso, fac, cap, unit, src, k in plants:
            if cm == com and cap:
                caps[iso] += cap
                kf = max(kf, k)
        x = mcs[(mcs.com == c["mcs"]) & (mcs.year == CHEM_FACILITY_YEAR) & (mcs.ed <= kf)
                & mcs.country.fillna("").str.contains("world total", case=False)]
        if com == "lithium":
            x = x[x.type.str.contains("mine production", case=False, na=False)]
        x = x[x.value.map(number).notna()]
        if caps and len(x):
            world = number(x.sort_values("ed").value.iloc[-1]) * c["mine_scale"]
            rep = chem_us(com, CHEM_FACILITY_YEAR, kf, salient)[1]
            for iso, (q, method, cap, u) in sorted(chem_facility(caps, world, rep).items()):
                prov.append((com, c["stage"], iso, CHEM_FACILITY_YEAR, kf, q, c["unit"], method,
                             None, None, cap, u, world))
                if q > 0:
                    rows.append((com, c["stage"], iso, CHEM_FACILITY_YEAR, q, c["unit"], f"{CHEM_SOURCE}{kf}", kf))
    return rows, prov


def check_chem(old, rows, prov, old_plants, plants):
    """The chemical stages are new stages, added without touching the
    others, and the balance holds in every trade edition."""
    assert not ({(r[0], r[1]) for r in old} & CHEM_STAGES), "a chemical stage existed already"
    assert not ({(p[0], p[1]) for p in old_plants} & CHEM_STAGES)
    assert {(r[0], r[1]) for r in rows} == CHEM_STAGES, {(r[0], r[1]) for r in rows}
    assert {(p[0], p[1]) for p in plants} <= CHEM_STAGES
    for r in rows:
        assert r[7] > r[3] and r[4] > 0, r
    eds = defaultdict(list)
    for p in prov:
        eds[(p[0], p[3], p[4])].append(p)
    for key, ps in eds.items():
        assert sum(1 for p in ps if p[7] == "balance") == 1, key
        assert abs(sum(p[5] for p in ps) - ps[0][12]) <= 1e-6 * max(1.0, ps[0][12]), key


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


def check_repairs(rows, plants):
    """The repairs below hold in what is written."""
    q = {(c, s, i, y): v for c, s, i, y, v, *_ in rows}
    for iso in ("COD", "ZMB"):
        for y in range(1972, 1976):
            # mined cobalt is recovered metal, as in the years either side
            assert q[("cobalt", "mine", iso, y)] <= 1.001 * q[("cobalt", "refinery", iso, y)], (iso, y)
    # MCS: every edition's value of a data year, known at that edition
    li = [(y, k) for c, s, i, y, v, u, src, k in rows if c == "lithium"]
    assert all(k > y for y, k in li)
    assert {k - y for y, k in li} == {1, 2}, {k - y for y, k in li}
    have = {(c, s, i) for c, s, i, *_ in plants}
    assert ("copper", "smelter", "CHN") in have, "China's copper smelters"
    assert ("nickel", "refinery", "IDN") in have, "Indonesia's nickel smelters (BGS: smelter/refinery)"
    assert not any(c == "nickel" and s == "smelter" for c, s, *_ in plants)


def main():
    rows = bgs_rows() + mcs_rows()
    check(rows)
    plants = plant_rows()
    check_repairs(rows, plants)
    cplants = chem_plants()
    chem, prov = chem_rows(rows, plants=cplants)
    with_caps = [p for p in cplants if p[4]]
    check_chem(rows, chem, prov, plants, with_caps)
    check(rows + chem)
    write("stages.csv", ["commodity", "stage", "iso", "year", "quantity", "unit", "source", "known"], rows + chem)
    write("stages-alt.csv", ["commodity", "stage", "iso", "year", "quantity", "unit", "source", "known"], myb_rows())
    write("stage-plants.csv", ["commodity", "stage", "iso", "facility", "capacity", "unit", "source", "known"],
          plants + with_caps)
    write("stages-chem.csv", ["commodity", "stage", "iso", "year", "known", "quantity", "unit", "method",
                              "net_exports", "domestic_use", "capacity", "utilisation", "world"],
          [tuple("" if v is None else v for v in p) for p in prov])


if __name__ == "__main__":
    main()
