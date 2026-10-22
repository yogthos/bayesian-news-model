"""HS6 -> CPA 2.1 -> EXIOBASE product concordance, with HS editions chained to
HS92, and the HS6 sub-nodes that stand for the new essentials (lithium,
cobalt, rare earths, chips, batteries...). docs/ESSENTIALS.md section 8 step 4,
bead bnm-1pq.5. Research tooling: reads data/raw/material/ and writes
data/derived/supply/:

  concordance.csv  hs_edition,hs_code,hs6,cpa,exiobase_product,weight
      One row per (edition, code, HS92 code, CPA, EXIOBASE product). hs_code
      is the code in its own edition (HS92, HS12, HS17, HS22); hs6 is the HS92
      code it chains to, so codes compare across years. weight is the share
      of the code's trade value that goes to the row; the weights of one
      (hs_edition, hs_code) sum to 1.
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
  HS92 -> HS17 and HS12 -> HS17: the UNSD conversion tables HS17 -> HS92
      and HS17 -> HS12 (the 1:1 defaults Comtrade and BACI use) inverted:
      the HS17 codes that convert to an older code are its parts. A code no
      HS17 code converts to (854280 in HS92, deleted later) takes its parts
      from the n:n correlation table instead.
  every edition -> HS92 (the hs6 column): the UNSD conversion table of
      that edition to HS 1992.
  CPA 2.1 -> EXIOBASE: EXIOBASE v3 products are CPA 2002 (NACE Rev. 1.1)
      aggregates; their codes say so (p13.20.11 copper ores = CPA 2002
      13.20.11, p24.b N-fertiliser...; Classifications_v_3_3_18.xlsx,
      Correspondence_products). No CPA 2.1 -> EXIOBASE table is shipped with
      EXIOBASE, so CPA_RULES below map CPA 2.1 codes to the products by
      their descriptions, most specific prefix first, and HS_RULES split a
      CPA class where EXIOBASE is finer than CPA (coal kinds, jet fuels,
      metal scrap by metal, biodiesel).

Weights. Many-to-many links are weighted by trade value where we have it:
an HS92 (or HS12) code split over several HS17 codes takes each part's share
of BACI HS17 world trade value, mean of 2017-2019. Below HS6 there is no
trade value (CN8 trade is EU-only and not held), so an HS6 code split over
CPA codes takes equal weights per CN8 line. 157 of 5,387 HS17 codes split
across CPA classes. Parts with no trade fall back to equal weights.

EXIOBASE's split of a NACE 1.1 class does not always follow CPA 2.1: ethyl
alcohol (CPA 20.14.74/75) goes to C_BIOG, biogasoline (fuel ethanol is the
bulk of its trade since the mid-2000s); electricity trade (2716) goes to
C_POWZ, electricity n.e.c., since trade does not say how it was made;
unrecorded media (CPA 26.80) go to C_CHEM, as NACE 1.1 24.65 did.

    python -I scripts/concordance.py [--root data/raw/material]
        [--exio data/raw/exiobase/hybrid-v3.3.18-2011/Classifications_v_3_3_18.xlsx]
        [--products data/derived/supply/products.csv]
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
WEIGHT_YEARS = (2017, 2018, 2019)

# ------------------------------------------------------------------ CPA 2.1 -> EXIOBASE
# (CPA 2.1 prefix without dots, EXIOBASE product). The longest matching
# prefix wins.
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
    ("01471", "C_PLTR"), ("01472", "C_OANP"), ("0149", "C_OANP"), ("014925", "C_WOOL"),
    ("02", "C_FORE"), ("03", "C_FISH"),
    # mining (05-08)
    ("0510", "C_OTBC"), ("0520", "C_LIBC"), ("0610", "C_COIL"), ("061020", "C_OGPL"),
    ("0620", "C_GASE"), ("0710", "C_IRON"), ("0721", "C_ORAN"),
    ("072911", "C_COPO"), ("072912", "C_NIKO"), ("072913", "C_ALUO"), ("072914", "C_PREO"),
    ("072915", "C_LZTO"), ("072919", "C_ONFO"),
    ("0811", "C_STON"), ("0812", "C_SDCL"), ("0891", "C_CHMF"), ("0892", "C_PEAT"),
    ("0893", "C_CHMF"), ("0899", "C_CHMF"), ("089910", "C_BITU"),
    # food, drink, tobacco (10-12)
    ("10", "C_OFOD"), ("101111", "C_PCAT"), ("101131", "C_PCAT"), ("101112", "C_PPIG"),
    ("101132", "C_PPIG"), ("10111", "C_POME"), ("10112", "C_POME"), ("10113", "C_POME"),
    ("101141", "C_WOOL"), ("101142", "C_PCAT"), ("101143", "C_PCAT"), ("101144", "C_POME"),
    ("101145", "C_POME"), ("101150", "C_POME"), ("101160", "C_POME"),
    ("1012", "C_PPLT"), ("1013", "C_POME"), ("1020", "C_FSHP"), ("104", "C_VOIL"),
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
    ("261", "C_RATV"), ("2620", "C_OFMA"), ("2630", "C_RATV"), ("2640", "C_RATV"),
    ("265", "C_MEIN"), ("2660", "C_MEIN"), ("2670", "C_MEIN"), ("2680", "C_CHEM"),
    ("27", "C_ELMA"), ("275", "C_MACH"), ("28", "C_MACH"), ("2823", "C_OFMA"),
    ("29", "C_MOTO"), ("30", "C_OTRE"),
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


def oil_mix(path):
    """{product: kt}, world imports of the refined products, mean 2017-2019
    (UNSD energy, via scripts/oil-products.py)."""
    if not path or not os.path.exists(path):
        print("no", path, "- 2710 keeps CN8-line weights", file=sys.stderr)
        return {}
    d = pd.read_csv(path, dtype={"flow": str}, usecols=["year", "exiobase", "flow", "value"])
    d = d[(d.flow == "03") & d.year.isin(WEIGHT_YEARS)]
    return (d.groupby("exiobase").value.sum() / len(WEIGHT_YEARS)).to_dict()


def reweight_oil(rows, oil):
    """Petroleum oils (2710): BACI reports all of them as 271000, and CN8
    lines are no guide to volume, so the products a 2710 code reaches are
    weighted by their world import tonnes instead. Products without tonnes
    (waste oils, C_RYMS) drop out unless they are all the code reaches."""
    by_code = defaultdict(list)
    for k in rows:
        if k[1].startswith("2710"):
            by_code[k[:3]].append(k)
    for keys in by_code.values():
        oily = [k for k in keys if oil.get(k[4], 0) > 0]
        if not oily:
            continue
        per = defaultdict(list)
        for k in oily:
            per[k[4]].append(k)
        tot = sum(oil[p] for p in per)
        for k in keys:
            rows[k] = 0.0
        for p, ks in per.items():
            for k in ks:
                rows[k] = oil[p] / tot / len(ks)


def build(root, world, oil=None):
    lists = hs_lists(root)
    cn17 = cn_cpa(root, "CN2017_CPA21.csv")
    cn22 = cn_cpa(root, "CN2022_CPA21.csv")
    p17 = code_to_products(cn17)
    p22 = code_to_products(cn22)
    c17_92 = conversion(root, "HS17", "HS92")
    c17_12 = conversion(root, "HS17", "HS12")
    c12_92 = conversion(root, "HS12", "HS92")
    c22_92 = conversion(root, "HS22", "HS92")
    w17 = world[(world.hs_edition == "HS17") & world.year.isin(WEIGHT_YEARS)]
    v17 = (w17.groupby("hs6").value.sum() / len(WEIGHT_YEARS)).to_dict()
    parts92 = parts_of(c17_92, correlation(root, "HS17", "HS92"), lists["HS92"], v17)
    parts12 = parts_of(c17_12, correlation(root, "HS17", "HS12"), lists["HS12"], v17)

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

    for code in lists["HS92"]:
        emit("HS92", code, code, via17(parts92.get(code, [])))
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

    if oil:
        reweight_oil(rows, oil)

    out = pd.DataFrame([(*k, w) for k, w in rows.items()],
                       columns=["hs_edition", "hs_code", "hs6", "cpa", "exiobase_product", "weight"])
    out = out[out.hs6.notna() & (out.weight > 1e-12)]
    out["cpa"] = out.cpa.str.replace(r"^(\d\d)(\d\d)(\d\d)$", r"\1.\2.\3", regex=True)
    out = out.sort_values(["hs_edition", "hs_code", "weight"], ascending=[True, True, False])
    checks = {"chain HS17 = HS12 o HS17->HS12":
              sum(c12_92.get(c17_12.get(k)) == c17_92.get(k) for k in c17_92) / len(c17_92)}
    return out, lists, checks


# ------------------------------------------------------------------ sub-nodes

# (id, name, [(edition, code, kind, note)])
SUBNODES = [
    ("lithium_carbonate", "Lithium carbonate", [("HS92", "283691", "exact", "")]),
    ("lithium_hydroxide", "Lithium oxide and hydroxide", [("HS92", "282520", "exact", "")]),
    ("cobalt_ore", "Cobalt ores and concentrates", [("HS92", "260500", "exact", "")]),
    ("cobalt_oxides", "Cobalt oxides and hydroxides", [("HS92", "282200", "exact", "")]),
    ("cobalt_unwrought", "Cobalt mattes, intermediates and unwrought cobalt",
     [("HS92", "810510", "pooled", "HS92 810510 also holds cobalt waste and scrap (810530 from HS02)"),
      ("HS12", "810520", "exact", ""), ("HS17", "810520", "exact", ""), ("HS22", "810520", "exact", "")]),
    ("cobalt_articles", "Cobalt articles n.e.s.", [("HS92", "810590", "exact", "wrought cobalt")]),
    ("rare_earth_compounds", "Rare-earth compounds",
     [("HS92", "284610", "exact", "cerium compounds"),
      ("HS92", "284690", "exact", "other rare-earth, Y and Sc compounds; 2846 is not split in HS22")]),
    ("rare_earth_metals", "Rare-earth metals, scandium and yttrium", [("HS92", "280530", "exact", "")]),
    ("permanent_magnets", "Permanent magnets",
     [("HS92", "850511", "pooled", "metal permanent magnets, NdFeB and SmCo with AlNiCo")]),
    ("polysilicon", "Silicon >= 99.99% (polysilicon)", [("HS92", "280461", "exact", "solar and electronic grade")]),
    ("germanium", "Germanium",
     [("HS92", "811230", "exact", "deleted in HS07 (into 811292); BACI HS92 has it only to 2006")]),
    ("gallium_pool", "Gallium with In, Nb and others",
     [("HS92", "811291", "pooled", "unwrought Ga, Hf, In, Nb, Re, Tl; gallium is not separable in any HS edition"),
      ("HS12", "811292", "pooled", "unwrought Ga, Ge, In, Nb, V"),
      ("HS17", "811292", "pooled", "unwrought Ga, Ge, In, Nb, V"),
      ("HS22", "811292", "pooled", "unwrought Ga, Ge, In, Nb, V")]),
    ("li_ion_batteries", "Lithium-ion accumulators",
     [("HS92", "850780", "pooled", "inside other accumulators; Li-ion is split out from HS12"),
      ("HS12", "850760", "exact", ""), ("HS17", "850760", "exact", ""), ("HS22", "850760", "exact", "")]),
    ("pv_cells", "Photovoltaic cells and modules",
     [("HS92", "854140", "pooled", "photosensitive devices including PV and LEDs; split in HS22"),
      ("HS22", "854142", "exact", "PV cells not assembled in modules"),
      ("HS22", "854143", "exact", "PV cells in modules or panels")]),
    ("integrated_circuits", "Integrated circuits",
     [("HS92", "854211", "exact", "HS92 IC codes swap at the 2007 and 2017 revisions; sum them"),
      ("HS92", "854219", "exact", ""), ("HS92", "854220", "exact", ""), ("HS92", "854280", "exact", ""),
      ("HS12", "854231", "exact", "processors and controllers"), ("HS12", "854232", "exact", "memories"),
      ("HS12", "854233", "exact", "amplifiers"), ("HS12", "854239", "exact", "other ICs"),
      ("HS17", "854231", "exact", ""), ("HS17", "854232", "exact", ""),
      ("HS17", "854233", "exact", ""), ("HS17", "854239", "exact", "")]),
    ("evs", "Battery-electric cars",
     [("HS92", "870390", "pooled", "inside cars n.e.s.; battery-electric is split out from HS17"),
      ("HS17", "870380", "exact", ""), ("HS22", "870380", "exact", "")]),
    ("graphite_natural", "Natural graphite",
     [("HS92", "250410", "exact", "powder or flakes (battery anodes)"),
      ("HS92", "250490", "exact", "other forms")]),
    ("graphite_synthetic", "Artificial graphite", [("HS92", "380110", "exact", "")]),
    ("nickel_sulphate", "Nickel sulphate", [("HS92", "283324", "exact", "")]),
    ("manganese_ore", "Manganese ores and concentrates", [("HS92", "260200", "exact", "")]),
    ("ferromanganese", "Ferro-manganese",
     [("HS92", "720211", "exact", "> 2% carbon"), ("HS92", "720219", "exact", "<= 2% carbon")]),
    ("manganese_metal", "Manganese and articles", [("HS92", "811100", "exact", "")]),
    ("manganese_oxides", "Manganese oxides",
     [("HS92", "282010", "exact", "manganese dioxide (battery-grade EMD)"),
      ("HS92", "282090", "exact", "other manganese oxides")]),
    ("copper_cathode", "Refined copper cathodes", [("HS92", "740311", "exact", "")]),
]


def subnode_rows(conc, lists):
    top = (conc.sort_values("weight", ascending=False)
               .drop_duplicates(["hs_edition", "hs_code"])
               .set_index(["hs_edition", "hs_code"]))
    out = []
    for sid, name, entries in SUBNODES:
        for ed, code, kind, note in entries:
            if code not in lists[ed]:
                raise SystemExit(f"{sid}: {code} is not an {ed} code")
            r = top.loc[(ed, code)]
            if r.weight < 0.5:
                raise SystemExit(f"{sid}: {ed} {code} has no dominant product")
            out.append((sid, name, ed, code, r.hs6, r.exiobase_product, kind, note))
    return pd.DataFrame(out, columns=["subnode", "name", "hs_edition", "hs_code", "hs6",
                                      "exiobase_parent", "kind", "note"])


# ------------------------------------------------------------------ coverage

def coverage(conc, world, lists):
    mapped = set(zip(conc.hs_edition, conc.hs_code))
    top = (conc.groupby(["hs_edition", "hs_code", "exiobase_product"]).weight.sum()
               .groupby(level=[0, 1]).max())
    single = set(top[top > 0.999].index)
    w = world.copy()
    keys = list(zip(w.hs_edition, w.hs6))
    w["mv"] = w.value.where([k in mapped for k in keys], 0.0)
    w["sv"] = w.value.where([k in single for k in keys], 0.0)
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

    conc, lists, checks = build(a.root, world, oil_mix(a.oil))
    products = set(pd.read_csv(a.products).code)
    stray = set(conc.exiobase_product) - products
    if stray:
        raise SystemExit(f"products not in {a.products}: {sorted(stray)}")
    sums = conc.groupby(["hs_edition", "hs_code"]).weight.sum()
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
