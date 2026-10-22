"""HS6 -> CPA 2.1 -> EXIOBASE product concordance, with HS editions chained to
HS92, and the HS6 sub-nodes that stand for the new essentials (lithium,
cobalt, rare earths, chips, batteries...). docs/ESSENTIALS.md section 8 step 4,
bead bnm-1pq.5. Research tooling: reads data/raw/material/ and writes
data/derived/supply/:

  concordance.csv  hs_edition,hs_code,hs6,cpa,exiobase_product,weight,from_year,to_year
      One row per (edition, code, HS92 code, CPA, EXIOBASE product, era).
      hs_code is the code in its own edition (HS92, HS12, HS17, HS22); hs6
      is the HS92 code it chains to, so codes compare across years. weight
      is the share of the code's trade value that goes to the row; the
      weights of one (hs_edition, hs_code) sum to 1 in every year. The year
      columns are empty except for the HS92 codes that change content in
      2017 (HS92_SPLIT_YEAR): their rows end in 2016 and a second set starts
      in 2017.
  subnodes.csv     subnode,name,hs_edition,hs_code,hs6,exiobase_parent,kind,note
      The HS6 codes standing for the new essentials, each with its EXIOBASE
      parent (the product the concordance sends the code to). kind is
      "exact" when the code is the thing and "pooled" when it shares the
      code with other goods (HS92 850780 holds Li-ion inside "other
      accumulators", 854140 holds PV cells with LEDs).
  concordance-coverage.csv  hs_edition,year,world_value,mapped_value,share,single_share
      Share of BACI world trade value that reaches an EXIOBASE product, and
      the share whose code goes to a single product (no split guessed).
  concordance-unmapped.csv  hs_edition,hs_code,value,years
      Codes with no product, largest first (BACI value summed over years,
      kUSD; listed codes BACI never reports have value 0).

The chain
  HS17 -> CPA 2.1: Eurostat CN2017 -> CPA 2.1 (CN8 lines; HS6 = CN8 prefix).
  HS22 -> CPA 2.1: Eurostat CN2022 -> CPA 2.1.
  HS12 -> HS17: the UNSD conversion table HS17 -> HS12 (the 1:1 default
      Comtrade and BACI use) inverted: the HS17 codes that convert to an
      HS12 code are its parts. A code no HS17 code converts to takes its
      parts from the n:n correlation table instead.
  HS92 -> HS12 -> HS17: the HS12 codes UNSD's HS12 -> HS92 table converts
      to an HS92 code, each taken to HS17 as above. Comtrade converts each
      reporter's edition with that edition's own table, so this is what BACI
      HS92 holds in 2012-2016, and the nearest we have to 1995-2011. From
      2017 BACI HS92 holds HS17 reports converted with the direct HS17 ->
      HS92 table, and 63 codes then change products (video consoles join
      950490, precious-metal waste joins ash 262090); those get second rows
      from the direct table. HS92 codes no HS12 code converts to (deleted
      before HS12: 854280, 847110) take the direct HS17 parts, else the n:n
      table.
  every edition -> HS92 (the hs6 column): the UNSD conversion table of
      that edition to HS 1992, which is what BACI HS92 uses. Chaining
      HS17 -> HS12 -> HS92 instead gives another code for 145 HS17 codes
      (7.6% of 2017-19 HS17 value): the two UNSD tables pick different
      defaults where HS92 codes were split (ICs 854231 -> 854211 direct,
      854219 chained; laptops 847130 -> 847193 direct, 847120 chained). The
      chain is sometimes the better description, but BACI HS92 moves the
      trade to the direct code in 2017 (847120 falls from 131 to 22 bn USD,
      847193 rises from 65 to 137), so the direct table is the one that
      matches the series.
  CPA 2.1 -> EXIOBASE: EXIOBASE v3 products are CPA 2002 (NACE Rev. 1.1)
      aggregates; their codes say so (p13.20.11 copper ores = CPA 2002
      13.20.11, p24.b N-fertiliser...; Classifications_v_3_3_18.xlsx,
      Correspondence_products). No CPA 2.1 -> EXIOBASE table is shipped with
      EXIOBASE, so CPA_RULES below map CPA 2.1 codes to the products by
      their descriptions, most specific prefix first, and HS_RULES split a
      CPA class where EXIOBASE is finer than CPA (coal kinds, jet fuels,
      metal scrap by metal, biodiesel).

Weights. Many-to-many links are weighted by trade value where we have it,
from the earliest data that resolve the split: an HS92 code split over HS12
codes takes each one's share of BACI HS12 world trade value, mean of
2012-2014; an HS12 code split over HS17 codes takes BACI HS17 value, mean of
2017-2019 (ERA_YEARS). Below HS6 there is no trade value (CN8 trade is
EU-only and not held), so an HS6 code split over CPA codes takes equal
weights per CN8 line. 157 of 5,387 HS17 codes split across CPA classes.
Parts with no trade fall back to the next edition's value, then equal
weights. Two fuel codes are split by UNSD energy import tonnes instead,
taken in the first years of each edition (HS92 1995-97, HS12 2012-14, HS17
2017-19, HS22 2022-23): petroleum oils 2710 over refined products (fuel oil
is 30% of the 1995-97 mix, 20% of 2017-19), and bituminous coal 270112 over
coking and other coal (35% coking in 1995-97, 21% in 2017-19; the CN8 split
was 50/50). Tonnes understate coking coal's value share, since it costs
more per tonne.

Look-ahead. The table is fixed, so splitting a 1995 HS92 code uses 2012-14
(and below that 2017-19) weights: up to 17-22 years ahead. It only matters
where the parts of a code reach different products (154 HS92 codes, 7% of
1995 trade value). Moving the weights from 2017-19 to 2012-14 changes the
product of 0.15% of 1995 world trade value, so drift is small, but a
product born later is counted from the start: HS92 382390 sends 22% to
biodiesel (C_BIOD), its 2012-14 share, also in 1995 when there was almost
no biodiesel trade. Fuel tonnes have a shorter look-ahead (UNSD energy for
year t is known about t+2). Use the HS92 table for structure, not as a
point-in-time feature in a backtest of the early years for codes that split
across products.

EXIOBASE's split of a NACE 1.1 class does not always follow CPA 2.1: ethyl
alcohol (CPA 20.14.74/75) goes to C_BIOG, biogasoline (fuel ethanol is the
bulk of its trade since the mid-2000s); electricity trade (2716) goes to
C_POWZ, electricity n.e.c., since trade does not say how it was made;
unrecorded media (CPA 26.80) go to C_CHEM, as NACE 1.1 24.65 did.

    python -I scripts/concordance.py [--root data/raw/material]
        [--products data/derived/supply/products.csv]
        [--oil data/derived/supply/oil-products.csv]
        [--energy data/raw/material/unsd-energy/by-commodity]
        [--out data/derived/supply] [--world FILE] [--world-only FILE]

--world reads BACI world values (hs_edition,year,hs6,value) from FILE instead
of the zips (the zips take minutes); --world-only writes that file and stops.
"""

import argparse
import csv
import io
import os
import sys
import zipfile
from collections import defaultdict

import pandas as pd

EDITION_FIRST_YEAR = {"HS92": 1995, "HS12": 2012, "HS17": 2017, "HS22": 2022}
BACI = {"HS92": "baci/BACI_HS92_V202601.zip",
        "HS12": "baci-hs12/BACI_HS12_V202601.zip",
        "HS17": "baci-hs17/BACI_HS17_V202601.zip"}
UNSD = "unsd-hs-correspondence"
# The first years of each edition: the weights that split an edition's
# codes come from the earliest data that resolves them, so a code is split
# as it stood when the edition started, with as little look-ahead as the
# data allow (see "Weights" above). HS22 stops at 2023: UNSD energy 2024 is
# partial.
ERA_YEARS = {"HS92": (1995, 1996, 1997), "HS12": (2012, 2013, 2014),
             "HS17": (2017, 2018, 2019), "HS22": (2022, 2023)}
# BACI HS92 from 2017 holds HS17 reports converted with UNSD's direct HS17
# -> HS92 table, so an HS92 code can change content then (video consoles
# join games n.e.s. 950490, laptops move from 847120 to 847193). HS92 codes
# whose products differ get a second set of rows from this year on.
HS92_SPLIT_YEAR = 2017
# UNSD energy commodities that split HS6 coal codes (import tonnes).
COAL_SPLIT = {"270112": {"0121": "C_COKC", "0129": "C_OTBC"}}

# ------------------------------------------------------------------ CPA 2.1 -> EXIOBASE
# (CPA 2.1 prefix without dots, EXIOBASE product). The longest matching
# prefix wins. Where NACE Rev. 2 moved an activity between divisions, the
# product follows NACE 1.1, as EXIOBASE does: electrical equipment for
# vehicles (29.31, wiring sets 854430, ignition 8511, signalling 8512) was
# 31.61, electrical machinery; alarms (26.30.50) were 31.62; optical fibres
# (27.31.12, 9001.10) were 33.40, optical instruments; natural asphalt
# (08.99.10) was 14.50, other mining.
CPA_RULES = [
    # agriculture (01)
    ("0111", "C_OCER"), ("01111", "C_WHEA"), ("01116", "C_FVEG"), ("01117", "C_FVEG"),
    ("01118", "C_OILS"), ("01119", "C_OILS"),
    ("0112", "C_PARI"), ("0113", "C_FVEG"), ("011360", "C_OTCR"), ("011371", "C_SUGB"),
    ("011372", "C_OTCR"), ("0114", "C_SUGB"), ("0115", "C_OTCR"), ("0116", "C_FIBR"),
    ("0119", "C_OTCR"), ("0121", "C_FVEG"), ("0122", "C_FVEG"), ("0123", "C_FVEG"),
    ("0124", "C_FVEG"), ("0125", "C_FVEG"), ("0126", "C_OILS"), ("0127", "C_OTCR"),
    ("0128", "C_OTCR"), ("0129", "C_OTCR"), ("0130", "C_OTCR"),
    ("0141", "C_CATL"), ("0142", "C_CATL"), ("014220", "C_OANP"), ("0143", "C_OMEA"),
    ("0144", "C_OMEA"), ("0145", "C_OMEA"), ("014530", "C_WOOL"), ("0146", "C_PIGS"),
    ("01471", "C_PLTR"), ("01472", "C_OANP"), ("0149", "C_OANP"), ("01491", "C_OMEA"),
    ("014925", "C_WOOL"),
    ("02", "C_FORE"), ("03", "C_FISH"),
    # mining (05-08)
    ("0510", "C_OTBC"), ("0520", "C_LIBC"), ("0610", "C_COIL"), ("061020", "C_OGPL"),
    ("0620", "C_GASE"), ("0710", "C_IRON"), ("0721", "C_ORAN"),
    ("072911", "C_COPO"), ("072912", "C_NIKO"), ("072913", "C_ALUO"), ("072914", "C_PREO"),
    ("072915", "C_LZTO"), ("072919", "C_ONFO"),
    ("0811", "C_STON"), ("0812", "C_SDCL"), ("0891", "C_CHMF"), ("0892", "C_PEAT"),
    ("0893", "C_CHMF"), ("0899", "C_CHMF"),
    # food, drink, tobacco (10-12)
    ("10", "C_OFOD"), ("101111", "C_PCAT"), ("101131", "C_PCAT"), ("101112", "C_PPIG"),
    ("101132", "C_PPIG"), ("10111", "C_POME"), ("10112", "C_POME"), ("10113", "C_POME"),
    ("101141", "C_WOOL"), ("101142", "C_PCAT"), ("101143", "C_PCAT"), ("101144", "C_POME"),
    ("101145", "C_POME"), ("101150", "C_POME"), ("101160", "C_POME"),
    ("1012", "C_PPLT"), ("1013", "C_POME"), ("101311", "C_PPIG"), ("101312", "C_PCAT"), ("1020", "C_FSHP"), ("104", "C_VOIL"),
    ("105", "C_DAIR"), ("10611", "C_RICE"), ("1081", "C_SUGR"),
    ("11", "C_BEVR"), ("12", "C_TOBC"),
    # textiles to printing (13-18)
    ("13", "C_TEXT"), ("14", "C_GARM"), ("15", "C_LETH"), ("16", "C_WOOD"),
    ("1711", "C_PULP"), ("17", "C_PAPE"), ("18", "C_MDIA"),
    # coke and refined petroleum (19)
    ("191010", "C_COKE"), ("191020", "C_COTA"), ("191030", "C_COTA"),
    ("192011", "C_PATF"), ("192012", "C_BKBP"),
    ("192021", "C_MGSL"), ("192022", "C_AGSL"), ("192023", "C_WHSP"), ("192024", "C_KJET"),
    ("192025", "C_NAPT"), ("192026", "C_DOIL"), ("192027", "C_KERO"), ("192028", "C_FOIL"),
    ("192029", "C_LUBR"), ("192031", "C_LPGA"), ("192032", "C_NSPP"), ("192039", "C_RGAS"),
    ("192041", "C_PARW"), ("192042", "C_PETC"),
    # chemicals (20-21)
    ("20", "C_CHEM"), ("20131", "C_NUCF"), ("201472", "C_CHAR"), ("201474", "C_BIOG"),
    ("201475", "C_BIOG"), ("201510", "C_NFER"), ("201520", "C_NFER"), ("20153", "C_NFER"),
    ("201560", "C_NFER"), ("20154", "C_PFER"), ("20155", "C_PFER"), ("20157", "C_PFER"),
    ("201580", "C_PFER"), ("2016", "C_PLAS"), ("21", "C_CHEM"),
    # rubber, plastics, non-metallic minerals (22-23)
    ("22", "C_RUBP"), ("231", "C_GLAS"), ("2320", "C_CRMC"), ("2331", "C_BRIK"),
    ("2332", "C_BRIK"), ("234", "C_CRMC"), ("235", "C_CMNT"), ("236", "C_ONMM"),
    ("237", "C_ONMM"), ("239", "C_ONMM"),
    # basic metals (24)
    ("241", "C_STEL"), ("242", "C_STEL"), ("243", "C_STEL"), ("2441", "C_PREM"),
    ("2442", "C_ALUM"), ("2443", "C_LZTP"), ("2444", "C_COPP"), ("2445", "C_ONFM"),
    ("2446", "C_NUCF"), ("245", "C_METC"),
    # metal products, machinery, equipment, vehicles (25-30)
    ("25", "C_FABM"), ("2540", "C_MACH"),
    ("261", "C_RATV"), ("2620", "C_OFMA"), ("2630", "C_RATV"), ("263050", "C_ELMA"),
    ("2640", "C_RATV"),
    ("265", "C_MEIN"), ("2660", "C_MEIN"), ("2670", "C_MEIN"), ("2680", "C_CHEM"),
    ("27", "C_ELMA"), ("273112", "C_MEIN"), ("275", "C_MACH"), ("28", "C_MACH"),
    ("2823", "C_OFMA"), ("29", "C_MOTO"), ("2931", "C_ELMA"), ("30", "C_OTRE"),
    # furniture and other manufacturing (31-32)
    ("31", "C_FURN"), ("32", "C_FURN"), ("3250", "C_MEIN"),
    # energy, water, waste (35-38)
    ("3511", "C_POWZ"), ("3521", "C_MGWG"), ("3530", "C_HWAT"), ("36", "C_WATR"),
    ("37", "C_RYMS"), ("38", "C_RYMS"), ("381151", "C_GLAW"), ("381152", "C_PAPR"),
    ("381155", "C_PLAW"),
    # services and goods classified with them
    ("58", "C_MDIA"), ("5829", "C_COMP"), ("59", "C_MDIA"), ("71", "C_OBUS"),
    ("74", "C_OBUS"), ("90", "C_RECR"), ("91", "C_RECR"), ("96", "C_OSER"),
]

# (HS6 or CN8 prefix, EXIOBASE product): where EXIOBASE is finer than CPA.
HS_RULES = [
    ("270111", "C_ANTH"), ("27011210", "C_COKC"), ("27011290", "C_OTBC"),
    ("270119", "C_SUBC"), ("27101270", "C_GJET"), ("27101921", "C_KJET"),
    ("271320", "C_BITU"), ("271390", "C_NSPP"), ("382600", "C_BIOD"),
    # metal waste and scrap (CPA 38.11.58 and 38.32.29 by metal)
    ("7204", "C_STEW"), ("7404", "C_COPW"), ("7602", "C_ALUW"), ("7802", "C_LZTW"),
    ("7902", "C_LZTW"), ("8002", "C_LZTW"), ("7112", "C_PREW"), ("7503", "C_ONFW"),
    ("810197", "C_ONFW"), ("810297", "C_ONFW"), ("810330", "C_ONFW"), ("810420", "C_ONFW"),
    ("810530", "C_ONFW"), ("810730", "C_ONFW"), ("810830", "C_ONFW"), ("810930", "C_ONFW"),
    ("811013", "C_ONFW"), ("811020", "C_ONFW"), ("811213", "C_ONFW"), ("811222", "C_ONFW"),
    ("811252", "C_ONFW"), ("811300", "C_ONFW"),
]
METAL_WASTE_CPA = ("381158", "383229")

# UNSD's HS92 list carries national 2710 subdivisions as "legacy non-WCO
# codes"; some old reports used them. (code, CPA 2.1, product).
LEGACY_2710 = [
    ("271011", "192022", "C_AGSL"), ("271012", "192021", "C_MGSL"), ("271013", "192021", "C_MGSL"),
    ("271014", "192024", "C_GJET"), ("271015", "192023", "C_WHSP"), ("271016", "192025", "C_NAPT"),
    ("271019", "192025", "C_NAPT"), ("271021", "192024", "C_KJET"), ("271022", "192027", "C_KERO"),
    ("271025", "192027", "C_KERO"), ("271026", "192026", "C_DOIL"), ("271027", "192026", "C_DOIL"),
    ("271029", "192028", "C_FOIL"), ("271091", "192028", "C_FOIL"), ("271093", "192028", "C_FOIL"),
    ("271094", "192042", "C_BITU"), ("271095", "192029", "C_LUBR"), ("271096", "192029", "C_LUBR"),
    ("271099", "192042", "C_NSPP"),
]


def longest(rules, code):
    best = None
    for prefix, product in rules:
        if code.startswith(prefix) and (best is None or len(prefix) > len(best[0])):
            best = (prefix, product)
    return best[1] if best else None


def exio_of(cn8, cpa):
    """The EXIOBASE product of a CN8 line with CPA 2.1 code `cpa` (no dots)."""
    hs = longest(HS_RULES, cn8)
    if hs and (not cpa.startswith(METAL_WASTE_CPA) or hs.endswith("W")):
        return hs
    if cpa.startswith(METAL_WASTE_CPA):
        return "C_RYMS"
    return longest(CPA_RULES, cpa)


# ------------------------------------------------------------------ readers

def six(x):
    s = str(x).strip()
    return s if len(s) == 6 and s.isdigit() else None


def read_sheet(path, sheet):
    return pd.read_excel(path, sheet_name=sheet, header=None, dtype=str)


def conversion(root, new, old):
    """{new-code: old-code}, the UNSD 1:1 conversion default."""
    files = {("HS17", "HS92"): ("HS2017toHS1992ConversionAndCorrelationTables.xlsx", 0),
             ("HS17", "HS12"): ("HS2017toHS2012ConversionAndCorrelationTables.xlsx", 0),
             ("HS22", "HS92"): ("HS2022toHS1992ConversionAndCorrelationTables.xlsx", 0),
             ("HS12", "HS92"): ("HS_2012_to_HS_1992_Correlation_and_conversion_tables.xls",
                                "Conversion HS 2012-HS 1992")}
    f, sheet = files[(new, old)]
    d = read_sheet(os.path.join(root, UNSD, f), sheet)
    out = {}
    for row in d.itertuples(index=False):
        codes = [c for c in (six(v) for v in row) if c]
        if len(codes) == 2:
            out[codes[0]] = codes[1]
    return out


def correlation(root, new, old):
    """[(new-code, old-code)], the UNSD n:n correlation."""
    files = {("HS17", "HS92"): ("HS2017toHS1992ConversionAndCorrelationTables.xlsx", 1),
             ("HS17", "HS12"): ("HS2017toHS2012ConversionAndCorrelationTables.xlsx", 1)}
    f, sheet = files[(new, old)]
    d = read_sheet(os.path.join(root, UNSD, f), sheet)
    out = []
    for row in d.itertuples(index=False):
        codes = [c for c in (six(v) for v in row) if c]
        if len(codes) == 2:
            out.append((codes[0], codes[1]))
    return out


def cn_cpa(root, name):
    """DataFrame cn8, hs6, cpa from a Eurostat CN -> CPA 2.1 table, one row
    per CN concept (deduplicated on the URI column s)."""
    d = pd.read_csv(os.path.join(root, "eurostat-cn-cpa", name), dtype=str)
    d = d.dropna(subset=["s", "t"]).drop_duplicates(["s", "t"])
    d["cn8"] = d.s.str.rsplit("/", n=1).str[-1].str[:8]
    d["cpa"] = d.t.str.rsplit("/", n=1).str[-1]
    d = d[d.cn8.str.fullmatch(r"\d{8}") & d.cpa.str.fullmatch(r"\d{6}")]
    d["hs6"] = d.cn8.str[:6]
    return d[["cn8", "hs6", "cpa"]].drop_duplicates()


def hs_lists(root):
    x = pd.read_excel(os.path.join(root, UNSD, "HSCodeandDescription.xlsx"),
                      sheet_name=None, dtype=str)
    out = {}
    for ed in ("HS92", "HS12", "HS17", "HS22"):
        d = x[ed]
        d = d[d.Level == "6"]
        out[ed] = dict(zip(d.Code, d.Description))
    return out


def world_values(root, editions=("HS92", "HS12", "HS17")):
    """BACI world trade value by edition, year and HS6 (kUSD)."""
    parts = []
    for ed in editions:
        path = os.path.join(root, BACI[ed])
        if not os.path.exists(path):
            print("no", path, file=sys.stderr)
            continue
        with zipfile.ZipFile(path) as z:
            for name in sorted(n for n in z.namelist() if "_Y" in n and n.endswith(".csv")):
                with z.open(name) as fh:
                    d = pd.read_csv(fh, usecols=["t", "k", "v"], dtype={"k": str})
                g = d.groupby(["t", "k"], as_index=False).v.sum()
                g.columns = ["year", "hs6", "value"]
                g["hs6"] = g.hs6.str.zfill(6)
                g.insert(0, "hs_edition", ed)
                parts.append(g)
                print(ed, name, len(d), file=sys.stderr)
    return pd.concat(parts, ignore_index=True)


# ------------------------------------------------------------------ the concordance

def code_to_products(cn):
    """{hs6: [(cpa, product, weight)]}: equal weight per CN8 line."""
    out = {}
    for hs6, g in cn.groupby("hs6"):
        acc = defaultdict(float)
        for r in g.itertuples(index=False):
            acc[(r.cpa, exio_of(r.cn8, r.cpa))] += 1.0 / len(g)
        out[hs6] = [(c, p, w) for (c, p), w in acc.items()]
    return out


def parts_of(conv, corr, old_codes, value):
    """{old-code: [(hs17, weight)]}: the HS17 codes making up each code of an
    older edition, weighted by HS17 trade value."""
    kids = defaultdict(list)
    for new, old in conv.items():
        kids[old].append(new)
    via_corr = defaultdict(list)
    for new, old in corr:
        via_corr[old].append(new)
    out = {}
    for old in old_codes:
        ks = sorted(set(kids.get(old) or via_corr.get(old) or []))
        if not ks:
            continue
        vs = [max(value.get(k, 0.0), 0.0) for k in ks]
        tot = sum(vs)
        out[old] = [(k, (v / tot) if tot > 0 else 1.0 / len(ks)) for k, v in zip(ks, vs)]
    return out


def fuel_mix(oil_path, energy_dir):
    """{edition: {hs-prefix: {product: tonnes}}}: world imports of the
    products that split a fuel code, summed over the edition's ERA_YEARS
    (UNSD energy). 2710 by refined product (scripts/oil-products.py output,
    kt); 270112 into coking and other bituminous coal (UNSD 0121, 0129)."""
    out = {ed: {} for ed in ERA_YEARS}
    if oil_path and os.path.exists(oil_path):
        d = pd.read_csv(oil_path, dtype={"flow": str}, usecols=["year", "exiobase", "flow", "value"])
        d = d[d.flow == "03"]
        for ed, yrs in ERA_YEARS.items():
            out[ed]["2710"] = d[d.year.isin(yrs)].groupby("exiobase").value.sum().to_dict()
    else:
        print("no", oil_path, "- 2710 keeps CN8-line weights", file=sys.stderr)
    for prefix, commodities in COAL_SPLIT.items():
        parts = []
        for com, prod in commodities.items():
            path = os.path.join(energy_dir or "", f"UNSD_ENERGY_{com}.csv")
            if not os.path.exists(path):
                print("no", path, f"- {prefix} keeps CN8-line weights", file=sys.stderr)
                parts = []
                break
            d = pd.read_csv(path, dtype=str)
            d = d[d.TRANSACTION == "03"]
            d = d.assign(year=d.TIME_PERIOD.astype(int),
                         t=d.OBS_VALUE.astype(float) * 10.0 ** d.UNIT_MULT.astype(int), product=prod)
            parts.append(d[["year", "product", "t"]])
        if parts:
            d = pd.concat(parts)
            for ed, yrs in ERA_YEARS.items():
                out[ed][prefix] = d[d.year.isin(yrs)].groupby("product").t.sum().to_dict()
    out["HS92+"] = out["HS17"]
    return out


def reweight_fuels(rows, mix):
    """Fuel codes whose CN8 lines are no guide to volume: BACI reports all
    petroleum oils (2710) as 271000, and bituminous coal 270112 has one
    coking and one other CN8 line. The products such a code reaches are
    weighted by their world import tonnes in the edition's first years
    instead. Products without tonnes (waste oils, C_RYMS) drop out unless
    they are all the code reaches."""
    by_code = defaultdict(list)
    for k in rows:
        for prefix, tonnes in mix.get(k[0], {}).items():
            if k[1].startswith(prefix):
                by_code[k[:3]].append((k, tonnes))
    for entries in by_code.values():
        tonnes = entries[0][1]
        keys = [k for k, _ in entries]
        heavy = [k for k in keys if tonnes.get(k[4], 0) > 0]
        if not heavy:
            continue
        per = defaultdict(list)
        for k in heavy:
            per[k[4]].append(k)
        tot = sum(tonnes[p] for p in per)
        for k in keys:
            rows[k] = 0.0
        for p, ks in per.items():
            for k in ks:
                rows[k] = tonnes[p] / tot / len(ks)


def build(root, world, mix=None):
    lists = hs_lists(root)
    cn17 = cn_cpa(root, "CN2017_CPA21.csv")
    cn22 = cn_cpa(root, "CN2022_CPA21.csv")
    p17 = code_to_products(cn17)
    p22 = code_to_products(cn22)
    c17_92 = conversion(root, "HS17", "HS92")
    c17_12 = conversion(root, "HS17", "HS12")
    c12_92 = conversion(root, "HS12", "HS92")
    c22_92 = conversion(root, "HS22", "HS92")
    def mean_value(ed):
        w = world[(world.hs_edition == ed) & world.year.isin(ERA_YEARS[ed])]
        return (w.groupby("hs6").value.sum() / len(ERA_YEARS[ed])).to_dict()
    v17, v12 = mean_value("HS17"), mean_value("HS12")
    parts92 = parts_of(c17_92, correlation(root, "HS17", "HS92"), lists["HS92"], v17)
    direct92 = parts_of(c17_92, [], lists["HS92"], v17)
    parts12 = parts_of(c17_12, correlation(root, "HS17", "HS12"), lists["HS12"], v17)
    kids12 = defaultdict(list)
    for new, old in c12_92.items():
        kids12[old].append(new)

    rows = defaultdict(float)

    def emit(ed, code, hs92, links):
        for cpa, prod, w in links:
            rows[(ed, code, hs92, cpa, prod)] += w

    def via17(parts):
        acc = []
        for k, wk in parts:
            acc += [(c, p, wk * w) for c, p, w in p17.get(k, [])]
        tot = sum(w for _, _, w in acc)
        return [(c, p, w / tot) for c, p, w in acc] if tot > 0 else []

    def via12(hs12):
        """An HS92 code as the HS12 codes converting to it, each weighted
        by its HS12 trade value (else its parts' HS17 value, else equally)
        and split into HS17 parts."""
        vs = [max(v12.get(h, 0.0), 0.0) for h in hs12]
        if sum(vs) <= 0:
            vs = [sum(max(v17.get(k, 0.0), 0.0) for k, _ in parts12.get(h, [])) for h in hs12]
        if sum(vs) <= 0:
            vs = [1.0] * len(hs12)
        acc = defaultdict(float)
        for h, v in zip(hs12, vs):
            for c, p, w in via17(parts12.get(h, [])):
                acc[(c, p)] += v * w
        tot = sum(acc.values())
        return [(c, p, w / tot) for (c, p), w in acc.items()] if tot > 0 else []

    # HS92 codes go through HS12 where it can: that is what BACI HS92 holds
    # in 2012-2016 (Comtrade converts each edition with its own table) and
    # the nearest we have to 1995-2011. Going straight to HS17 left 4% of
    # 1995-2011 trade, codes no HS17 code converts to (I/O units 847192,
    # recorded media 852490), to the n:n table, which drags in whatever the
    # HS17 side links to (847192 went 71% to C_RATV through 854231 ICs).
    for code in lists["HS92"]:
        links = via12(sorted(kids12[code])) if code in kids12 else []
        emit("HS92", code, code, links or via17(parts92.get(code, [])))
    # From HS92_SPLIT_YEAR, an HS92 code holds the HS17 codes UNSD's table
    # converts to it ("HS92+" until the rows are written out).
    for code, parts in direct92.items():
        emit("HS92+", code, code, via17(parts))
    for code, cpa, prod in LEGACY_2710:
        if code in lists["HS92"] and not any(k[:2] == ("HS92", code) for k in rows):
            emit("HS92", code, code, [(cpa, prod, 1.0)])
    for code in lists["HS12"]:
        emit("HS12", code, c12_92.get(code), via17(parts12.get(code, [])))
    for code in lists["HS17"]:
        emit("HS17", code, c17_92.get(code), p17.get(code, []))
    for code in lists["HS22"]:
        emit("HS22", code, c22_92.get(code), p22.get(code, []))
    # BACI keeps some codes outside an edition's list: its HS12 and HS17
    # files report all of 2710 as 271000, the HS92 code. Those take the
    # HS92 code's links.
    links92 = defaultdict(list)
    for (ed, code, hs92, cpa, prod), w in list(rows.items()):
        if ed == "HS92":
            links92[code].append((cpa, prod, w))
    for ed in ("HS12", "HS17", "HS22"):
        extra = set(world[world.hs_edition == ed].hs6) - set(lists[ed])
        for code in sorted(extra & set(links92)):
            emit(ed, code, code, links92[code])

    if mix:
        reweight_fuels(rows, mix)

    out = pd.DataFrame([(*k, w) for k, w in rows.items()],
                       columns=["hs_edition", "hs_code", "hs6", "cpa", "exiobase_product", "weight"])
    out = out[out.hs6.notna() & (out.weight > 1e-12)]
    out = split_hs92_era(out)
    out["cpa"] = out.cpa.str.replace(r"^(\d\d)(\d\d)(\d\d)$", r"\1.\2.\3", regex=True)
    out = out.sort_values(["hs_edition", "hs_code", "from_year", "weight"],
                          ascending=[True, True, True, False], na_position="first")
    checks = {"chain HS17 = HS12 o HS17->HS12":
              sum(c12_92.get(c17_12.get(k)) == c17_92.get(k) for k in c17_92) / len(c17_92)}
    return out, lists, checks


def split_hs92_era(out):
    """The "HS92+" rows become HS92 rows from HS92_SPLIT_YEAR on, with the
    code's own rows ending the year before, for the codes where the two
    send value to different products; elsewhere they are dropped."""
    def mix(d):
        return d.groupby(["hs_code", "exiobase_product"]).weight.sum()
    a = mix(out[out.hs_edition == "HS92"])
    b = mix(out[out.hs_edition == "HS92+"])
    j = pd.concat([a.rename("a"), b.rename("b")], axis=1).fillna(0.0)
    j = j[j.index.get_level_values(0).isin(set(b.index.get_level_values(0)))]
    diff = (j.a - j.b).abs().groupby(level=0).max()
    split = set(diff[diff > 1e-6].index)
    out = out.copy()
    out["from_year"] = pd.array([pd.NA] * len(out), dtype="Int64")
    out["to_year"] = pd.array([pd.NA] * len(out), dtype="Int64")
    late = out.hs_edition == "HS92+"
    out = out[~late | out.hs_code.isin(split)].copy()
    late = out.hs_edition == "HS92+"
    out.loc[late, "from_year"] = HS92_SPLIT_YEAR
    out.loc[late, "hs_edition"] = "HS92"
    out.loc[~late & (out.hs_edition == "HS92") & out.hs_code.isin(split), "to_year"] = HS92_SPLIT_YEAR - 1
    return out


def rows_in(conc, year):
    """The rows that hold in `year` (all of them without a year bound)."""
    f, t = conc.from_year, conc.to_year
    return conc[(f.isna() | (f <= year)) & (t.isna() | (t >= year))]


# ------------------------------------------------------------------ sub-nodes

ALL = ("HS92", "HS12", "HS17", "HS22")
LATER = ("HS12", "HS17", "HS22")

# (id, name, [(editions, code, kind, note)]). A code that keeps its HS92
# scope in a later edition is listed for that edition too, so a sub-node is
# found whichever edition the trade is in. kind: "exact" when the code is
# the thing, "pooled" when it shares the code with other goods.
SUBNODES = [
    ("lithium_carbonate", "Lithium carbonate", [(ALL, "283691", "exact", "")]),
    ("lithium_hydroxide", "Lithium oxide and hydroxide", [(ALL, "282520", "exact", "")]),
    ("lithium_ore_pool", "Lithium ores with other minerals n.e.s.",
     [(ALL, "253090", "pooled", "spodumene, petalite, lepidolite inside mineral substances n.e.s.; "
                                "no lithium-ore code in HS92-HS22")]),
    ("cobalt_ore", "Cobalt ores and concentrates", [(ALL, "260500", "exact", "")]),
    ("cobalt_oxides", "Cobalt oxides and hydroxides", [(ALL, "282200", "exact", "")]),
    ("cobalt_unwrought", "Cobalt mattes, intermediates and unwrought cobalt",
     [(("HS92",), "810510", "pooled", "HS92 810510 also holds cobalt waste and scrap (810530 from HS02)"),
      (LATER, "810520", "exact", "")]),
    ("cobalt_articles", "Cobalt articles n.e.s.", [(ALL, "810590", "exact", "wrought cobalt")]),
    ("rare_earth_compounds", "Rare-earth compounds",
     [(ALL, "284610", "exact", "cerium compounds"),
      (ALL, "284690", "exact", "other rare-earth, Y and Sc compounds; 2846 is not split in HS22")]),
    ("rare_earth_metals", "Rare-earth metals, scandium and yttrium", [(ALL, "280530", "exact", "")]),
    ("permanent_magnets", "Permanent magnets",
     [(ALL, "850511", "pooled", "metal permanent magnets, NdFeB and SmCo with AlNiCo")]),
    ("polysilicon", "Silicon >= 99.99% (polysilicon)", [(ALL, "280461", "exact", "solar and electronic grade")]),
    ("silicon_metal", "Silicon < 99.99% (metallurgical)", [(ALL, "280469", "exact", "")]),
    ("germanium", "Germanium",
     [(("HS92",), "811230", "exact", "deleted in HS07 (into 811292); BACI HS92 has it only to 2006")]),
    ("vanadium_metal", "Vanadium",
     [(("HS92",), "811240", "exact", "deleted in HS07 (into 811292, the gallium pool)")]),
    ("gallium_pool", "Gallium with In, Nb and others",
     [(("HS92",), "811291", "pooled",
       "unwrought Ga, Hf, In, Nb, Re, Tl; gallium is not separable in any HS edition"),
      (("HS12", "HS17"), "811292", "pooled", "unwrought Ga, Ge, Hf, In, Nb, Re, V"),
      (("HS22",), "811292", "pooled", "unwrought Ga, Ge, In, Nb, V; Hf and Re split out in HS22")]),
    ("nb_ta_v_ore", "Niobium, tantalum and vanadium ores",
     [(ALL, "261590", "pooled", "coltan, pyrochlore and vanadium ores in one code")]),
    ("vanadium_oxides", "Vanadium oxides and hydroxides", [(ALL, "282530", "exact", "")]),
    ("ferrovanadium", "Ferro-vanadium", [(ALL, "720292", "exact", "")]),
    ("tungsten_ore", "Tungsten ores and concentrates", [(ALL, "261100", "exact", "")]),
    ("tungstates", "Tungstates", [(ALL, "284180", "exact", "ammonium paratungstate")]),
    ("tungsten_metal", "Unwrought tungsten and powders",
     [(ALL, "810110", "exact", "powders; HS92 unwrought tungsten (810191) is about 78% scrap by value "
                               "and goes to C_ONFW, so it is left out"),
      (LATER, "810194", "exact", "unwrought")]),
    ("tantalum_metal", "Unwrought tantalum and powders",
     [(("HS92",), "810310", "pooled", "with waste and scrap (810330 from HS02)"),
      (LATER, "810320", "exact", "")]),
    ("rare_gases", "Rare gases other than argon",
     [(ALL, "280429", "pooled", "helium and neon with krypton and xenon")]),
    ("platinum_group_metals", "Platinum-group metals",
     [(ALL, "711011", "exact", "platinum, unwrought or powder"), (ALL, "711019", "exact", "platinum, semi-manufactured"),
      (ALL, "711021", "exact", "palladium, unwrought or powder"), (ALL, "711029", "exact", "palladium, semi-manufactured"),
      (ALL, "711031", "exact", "rhodium, unwrought or powder"), (ALL, "711039", "exact", "rhodium, semi-manufactured"),
      (ALL, "711041", "exact", "iridium, osmium, ruthenium, unwrought or powder"),
      (ALL, "711049", "exact", "iridium, osmium, ruthenium, semi-manufactured")]),
    ("uranium_ore", "Uranium ores and concentrates", [(ALL, "261210", "exact", "")]),
    ("natural_uranium", "Natural uranium and its compounds", [(ALL, "284410", "exact", "")]),
    ("nickel_intermediates", "Nickel mattes and intermediates",
     [(ALL, "750110", "exact", "mattes"),
      (ALL, "750120", "exact", "oxide sinters and other intermediates (MHP)")]),
    ("li_ion_batteries", "Lithium-ion accumulators",
     [(("HS92",), "850780", "pooled", "inside other accumulators; Li-ion is split out from HS12"),
      (LATER, "850760", "exact", "")]),
    ("pv_cells", "Photovoltaic cells and modules",
     [(("HS92", "HS12", "HS17"), "854140", "pooled", "photosensitive devices including PV and LEDs; split in HS22"),
      (("HS22",), "854142", "exact", "PV cells not assembled in modules"),
      (("HS22",), "854143", "exact", "PV cells in modules or panels")]),
    ("integrated_circuits", "Integrated circuits",
     [(("HS92",), "854211", "exact",
       "BACI HS92 has ICs in 854219 for 2012-16 and 854211 from 2017 (with HS17 smart cards 852352); sum them"),
      (("HS92",), "854219", "exact", ""), (("HS92",), "854220", "exact", ""), (("HS92",), "854280", "exact", ""),
      (LATER, "854231", "exact", "processors and controllers"), (LATER, "854232", "exact", "memories"),
      (LATER, "854233", "exact", "amplifiers"), (LATER, "854239", "exact", "other ICs")]),
    ("evs", "Battery-electric cars",
     [(("HS92",), "870390", "pooled",
       "cars n.e.s.; from 2017 BACI HS92 870390 also holds all hybrids (HS17 870340-870370) and BEVs"),
      (("HS12",), "870390", "pooled", "cars n.e.c., battery-electric with others"),
      (("HS17", "HS22"), "870380", "exact", "")]),
    ("graphite_natural", "Natural graphite",
     [(ALL, "250410", "exact", "powder or flakes (battery anodes)"), (ALL, "250490", "exact", "other forms")]),
    ("graphite_synthetic", "Artificial graphite", [(ALL, "380110", "exact", "")]),
    ("nickel_sulphate", "Nickel sulphate", [(ALL, "283324", "exact", "")]),
    ("manganese_ore", "Manganese ores and concentrates", [(ALL, "260200", "exact", "")]),
    ("ferromanganese", "Ferro-manganese",
     [(ALL, "720211", "exact", "> 2% carbon"), (ALL, "720219", "exact", "<= 2% carbon")]),
    ("manganese_metal", "Manganese and articles", [(ALL, "811100", "exact", "")]),
    ("manganese_oxides", "Manganese oxides",
     [(ALL, "282010", "exact", "manganese dioxide (battery-grade EMD)"),
      (ALL, "282090", "exact", "other manganese oxides")]),
    ("copper_cathode", "Refined copper cathodes", [(ALL, "740311", "exact", "")]),
]


def subnode_rows(conc, lists):
    def tops(rows):
        share = rows.groupby(["hs_edition", "hs_code", "hs6", "exiobase_product"], as_index=False).weight.sum()
        return (share.sort_values("weight", ascending=False)
                     .drop_duplicates(["hs_edition", "hs_code"])
                     .set_index(["hs_edition", "hs_code"]))
    top, late = tops(rows_in(conc, HS92_SPLIT_YEAR - 1)), tops(rows_in(conc, HS92_SPLIT_YEAR))
    out = []
    for sid, name, entries in SUBNODES:
        for eds, code, kind, note in entries:
            for ed in eds:
                if code not in lists[ed]:
                    raise SystemExit(f"{sid}: {code} is not an {ed} code")
                r = top.loc[(ed, code)]
                if r.weight < 0.5:
                    raise SystemExit(f"{sid}: {ed} {code} has no dominant product")
                if (ed, code) in late.index and late.loc[(ed, code)].exiobase_product != r.exiobase_product:
                    raise SystemExit(f"{sid}: {ed} {code} changes parent in {HS92_SPLIT_YEAR}")
                out.append((sid, name, ed, code, r.hs6, r.exiobase_product, kind, note))
    return pd.DataFrame(out, columns=["subnode", "name", "hs_edition", "hs_code", "hs6",
                                      "exiobase_parent", "kind", "note"])


# ------------------------------------------------------------------ coverage

def coverage(conc, world, lists):
    mapped = set(zip(conc.hs_edition, conc.hs_code))

    def single_codes(rows):
        top = (rows.groupby(["hs_edition", "hs_code", "exiobase_product"]).weight.sum()
                   .groupby(level=[0, 1]).max())
        return set(top[top > 0.999].index)
    early, late = single_codes(rows_in(conc, HS92_SPLIT_YEAR - 1)), single_codes(rows_in(conc, HS92_SPLIT_YEAR))
    w = world.copy()
    keys = list(zip(w.hs_edition, w.hs6))
    w["mv"] = w.value.where([k in mapped for k in keys], 0.0)
    w["sv"] = w.value.where([k in (late if y >= HS92_SPLIT_YEAR else early)
                             for k, y in zip(keys, w.year)], 0.0)
    w["mapped"] = w.mv > 0
    cov = w.groupby(["hs_edition", "year"], as_index=False).agg(world_value=("value", "sum"),
                                                                 mapped_value=("mv", "sum"),
                                                                 single_value=("sv", "sum"))
    cov["share"] = cov.mapped_value / cov.world_value
    cov["single_share"] = cov.single_value / cov.world_value
    cov = cov.drop(columns="single_value")
    un = (w[~w.mapped].groupby(["hs_edition", "hs6"], as_index=False)
          .agg(value=("value", "sum"), years=("year", lambda s: f"{s.min()}-{s.max()}")))
    un = un.rename(columns={"hs6": "hs_code"})
    idle = [(ed, c, 0.0, "") for ed, codes in lists.items() for c in sorted(codes)
            if (ed, c) not in mapped and not ((un.hs_edition == ed) & (un.hs_code == c)).any()]
    un = pd.concat([un, pd.DataFrame(idle, columns=un.columns)], ignore_index=True)
    return cov, un.sort_values(["value", "hs_edition", "hs_code"], ascending=[False, True, True])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="data/raw/material")
    ap.add_argument("--products", default="data/derived/supply/products.csv")
    ap.add_argument("--out", default="data/derived/supply")
    ap.add_argument("--oil", default="data/derived/supply/oil-products.csv")
    ap.add_argument("--energy", default="data/raw/material/unsd-energy/by-commodity")
    ap.add_argument("--world")
    ap.add_argument("--world-only")
    a = ap.parse_args()

    if a.world_only:
        world_values(a.root).to_csv(a.world_only, index=False)
        return
    if a.world:
        world = pd.read_csv(a.world, dtype={"hs6": str})
    else:
        world = world_values(a.root)

    conc, lists, checks = build(a.root, world, fuel_mix(a.oil, a.energy))
    products = set(pd.read_csv(a.products).code)
    stray = set(conc.exiobase_product) - products
    if stray:
        raise SystemExit(f"products not in {a.products}: {sorted(stray)}")
    sums = conc.groupby(["hs_edition", "hs_code", conc.from_year.fillna(0)]).weight.sum()
    assert ((sums - 1).abs() < 1e-9).all(), sums[(sums - 1).abs() >= 1e-9]

    subs = subnode_rows(conc, lists)
    cov, un = coverage(conc, world, lists)

    os.makedirs(a.out, exist_ok=True)
    conc.to_csv(os.path.join(a.out, "concordance.csv"), index=False, float_format="%.10g")
    subs.to_csv(os.path.join(a.out, "subnodes.csv"), index=False)
    cov.to_csv(os.path.join(a.out, "concordance-coverage.csv"), index=False, float_format="%.6g")
    un.to_csv(os.path.join(a.out, "concordance-unmapped.csv"), index=False, float_format="%.6g")

    for ed in ("HS92", "HS12", "HS17", "HS22"):
        n = len(lists[ed])
        m = conc[conc.hs_edition == ed].hs_code.nunique()
        print(f"{ed}: {m}/{n} codes mapped")
    for k, v in checks.items():
        print(f"{k}: {v:.4f}")
    print(cov.to_string(index=False))
    print(un.head(25).to_string(index=False))


if __name__ == "__main__":
    main()
