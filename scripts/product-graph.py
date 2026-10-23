"""The product graph of the material economy (docs/STOCK-FLOW.md, "Product
graph"; epic bnm-1pq): what each product is made from and who uses it, per
region, in physical units where the source has them.

Reads the EXIOBASE hybrid MR-HSUT 2011 v3.3.18 (data/raw/exiobase/
hybrid-v3.3.18-2011: MR_HSUP, MR_HUSE, MR_HSUTs_FD; 200 products, 164
industries, 43 countries and 5 rest-of-world regions; goods in tonnes,
energy in TJ, services in M EUR; CC BY 4.0) and the monetary product-by-
product tables v3.8.2 for 1995, 2011 and 2022 (data/raw/exiobase/
v3.8.2-pxp/IOT_<year>_pxp.zip: A and x, M EUR at basic prices; CC BY-SA
4.0; 49 regions, Taiwan apart, folded into WA here as the hybrid tables
do). Writes data/derived/supply/:

  products.csv    code,name,unit,group
                  group: raw (crops, animals, forestry, fish, coal, crude,
                  gas, ores, quarrying), energy (coke, refined products,
                  nuclear fuel, electricity, gases, steam, biofuels),
                  material (textiles, wood, pulp and paper, basic
                  chemicals, plastics and fertiliser, glass, cement, basic
                  metals, secondary raw materials), manufactured (food,
                  apparel, printing, rubber and plastic products, metal
                  products, machinery, vehicles, furniture), service
                  (M EUR services and the "tonnes (service)" waste
                  treatment and re-processing services)
  industries.csv  code,name
  recipes.csv     region,output,input,phys,input_unit,output_unit,
                  share_1995,share_2011,share_2022,physical,override
                  phys: input per unit of output in the hybrid units
                  (tonnes per tonne, TJ per tonne, M EUR per tonne...), the
                  industry-technology assumption on the hybrid supply and
                  use tables: the inputs of the industries making the
                  product, each per unit of its output in the product's
                  unit, weighted by the industry's share of the product's
                  supply; all origins together. An industry making
                  products in more than one unit (waste treatment and
                  the secondary material it recovers, incineration and its
                  electricity, pulp and its heat) has its inputs split
                  between its units by the value of its output in each
                  (rough prices, as for supply.csv; equal parts where a
                  unit's output has no price), so no input is counted
                  whole against each unit. share_<year>: the input's
                  value per unit of the output's value in the monetary pxp
                  table of that year (the technical coefficient, all
                  origins summed; blank where the output is not made that
                  year). The monetary tables route some feedstocks through
                  other products (Germany's N fertiliser buys its gas as
                  gas distribution services, its plastics their naphtha as
                  chemicals nec), the hybrid ones do not: a feedstock can
                  be large in phys and small in share. Valuing phys at
                  monetary output over hybrid supply does not mend it (the
                  two releases disagree on some products' size: shares of
                  9 for gas in German N fertiliser). physical: 1 when both
                  units are tonnes or TJ.
                  Kept: rows with a 2011 share of at least 1e-4,
                  or physical rows carrying at least 1% of the output's
                  inputs in the input's unit (as the hybrid has them).
                  override: the source where the row is not the hybrid
                  table's but OVERRIDES' (an independent source
                  contradicts it: gas in N fertiliser, electricity in
                  mining; blank otherwise).
  overrides.csv   region,output,input,hybrid,value,unit,source,reason
                  each overridden recipe row: the hybrid's value, the one
                  written, and why.
  production.csv  region,product,quantity,unit,value_1995,value_2011,
                  value_2022   (hybrid supply 2011; M EUR output by year)
  supply.csv      region,industry,product,quantity,unit,market_share,
                  industry_share
                  what each industry makes: market_share of the product's
                  supply in the region, industry_share of the industry's
                  output value (at monetary output 2011 over hybrid supply,
                  rough where the releases disagree); industry shares of at
                  least 1e-3.
  use.csv         region,product,user,kind,quantity,unit,share,value_share,
                  value_rough
                  use in the region of the product (all origins) by each
                  industry (kind industry, user its code) and final demand
                  category (kind final); share: of the product's total use
                  in the region; value_share: of the industry's output
                  value, the use valued at origin prices (monetary output
                  2011 over hybrid supply; rough, as for supply.csv);
                  blank where that ratio is above 1 (about a thousand
                  rows, where the two releases disagree on a product's
                  size), the ratio then in value_rough: flagged, not a
                  share. Rows with share or value ratio of at least 1e-4.
  trade.csv       product,origin,dest,quantity,share
                  the product used in dest (by industries and final demand)
                  by origin, the MR tables' import shares (domestic
                  included); shares of at least 1e-3.
  balances.csv    region,check,input,output,unit,residual
                  product: a region's supply of a product less every use of
                  it (intermediate and final, at home and abroad), relative
                  to supply; refinery: tonnes of refined products out of
                  the refinery industry against tonnes of crude, NGL, other
                  hydrocarbons and refinery feedstocks in; refinery-all:
                  against every tonne of hydrocarbon, petroleum product,
                  additive and biofuel the refinery industry takes in
                  (natural gas, product backflows, blending components);
                  refinery-unscaled, refinery-all-unscaled: the same
                  before the scaling below.
  scaling.csv     region,products,kind,exiobase_kt,unsd_kt,target_kt,
                  fitted_kt,source
                  naphtha, LPG, bitumen, other products, and jet and other
                  kerosene together, run 1.5-3 times UNSD's 2011 figures in
                  the hybrid tables (their refinery output carries what the
                  refinery industry also takes in: gas processing, product
                  backflows). Each is scaled to UNSD (data/derived/supply/
                  oil-products.csv, from scripts/oil-products.py, which
                  runs first): its origin-by-destination uses (every
                  industry and final demand) fitted by biproportional
                  scaling to production by origin (flow 01; kind
                  production) and use by destination (total energy supply,
                  GA; kind use; not for kerosene, whose bunkers GA leaves
                  out), a region's supply scaled with its uses, so supply
                  still equals use. source unsd where both figures exceed
                  100 kt; median, the median ratio, for an origin without
                  one; a destination without one is left free. Every table
                  above is written after the scaling; without
                  oil-products.csv nothing is scaled.

    python -I scripts/product-graph.py [data-root]
"""
import csv
import os
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = sys.argv[1] if len(sys.argv) > 1 else "data"
HY = os.path.join(ROOT, "raw/exiobase/hybrid-v3.3.18-2011")
PXP = os.path.join(ROOT, "raw/exiobase/v3.8.2-pxp")
OUT = os.path.join(ROOT, "derived/supply")
YEARS = [1995, 2011, 2022]
PHYS = {"tonnes", "TJ"}

RAW_PREFIX = ("p01.", "p02", "p05", "p10.a", "p10.b", "p10.c", "p10.d", "p10.f", "p10.h",
              "p11", "p12", "p13", "p14")
ENERGY_PREFIX = ("p10.e", "p10.g", "p23", "p40", "p24.e", "p24.f", "p24.g", "p24.h", "p24.i")
MATERIAL_PREFIX = ("p17", "p20", "p21", "p24", "p26", "p27", "p37")
REFINERY_IN = ["C_COIL", "C_GASL", "C_OGPL", "C_REFF"]
REFINERY_EXTRA = ["C_ADDC", "C_BIOG", "C_BIOD", "C_OBIO"]
# refined products whose hybrid tonnages run 1.5-3 times UNSD's 2011
# production, scaled to it; jet and other kerosene together (countries
# split them differently: Spain reports its jet as kerosene)
SCALED = [["C_NAPT"], ["C_LPGA"], ["C_BITU"], ["C_NSPP"], ["C_KJET", "C_KERO"]]
SCALE_MIN_KT = 100.0
# UNSD's total energy supply leaves out international bunkers, so no use
# target for jet fuel
NO_USE_TARGET = [["C_KJET", "C_KERO"]]
# the rest-of-world regions' countries in UNSD's data (ISO3), by UN
# continent; the Middle East as EXIOBASE has it (Iran with it, Turkey and
# Cyprus apart); WA (Asia and Pacific, Taiwan in it, the Caucasus and
# Central Asia with it) every country left
ROW_MEMBERS = {
    "WM": "ARE BHR IRN IRQ ISR JOR KWT LBN OMN PSE QAT SAU SYR YEM",
    "WF": "AGO BDI BEN BFA BWA CAF CIV CMR COD COG COM CPV DJI DZA EGY ERI ETH GAB GHA GIN GMB GNB GNQ KEN LBR "
          "LBY LSO MAR MDG MLI MOZ MRT MUS MWI NAM NER NGA RWA SDF SDN SSD SEN SHN SLE SOM STP SWZ SYC TCD TGO "
          "TUN TZA UGA ZMB ZWE",
    "WL": "ABW AIA ANT ARG ATG BHS BLM BLZ BMU BOL BRB CHL COL CRI CUB CUW CYM DMA DOM ECU FLK GRD GTM GUY HND "
          "HTI JAM KNA LCA MAF MSR NIC PAN PER PRI PRY SLV SPM SUR SXM TCA TTO URY VCT VEN VGB",
    "WE": "ALB AND BIH BLR FRO GGY GIB GRL IMN ISL JEY LIE MCO MDA MKD MNE SMR SRB UKR XKX",
}
ISO3 = {"AU": "AUS", "AT": "AUT", "BE": "BEL", "BR": "BRA", "BG": "BGR", "CA": "CAN", "CN": "CHN", "CY": "CYP",
        "CZ": "CZE", "DK": "DNK", "EE": "EST", "FI": "FIN", "FR": "FRA", "DE": "DEU", "GR": "GRC", "HU": "HUN",
        "HR": "HRV", "IN": "IND", "ID": "IDN", "IE": "IRL", "IT": "ITA", "JP": "JPN", "LV": "LVA", "LT": "LTU",
        "LU": "LUX", "MT": "MLT", "MX": "MEX", "NL": "NLD", "NO": "NOR", "PL": "POL", "PT": "PRT", "RO": "ROU",
        "RU": "RUS", "SK": "SVK", "SI": "SVN", "ZA": "ZAF", "KR": "KOR", "ES": "ESP", "SE": "SWE", "CH": "CHE",
        "TR": "TUR", "GB": "GBR", "US": "USA"}

# ------------------------------------------------- overrides (bnm-1pq.28)
#
# Where an independent source clearly contradicts a hybrid recipe row,
# the row is overridden with a cited value (override_recipe; written to
# recipes.csv with the source in its override column, and to
# overrides.csv). bnm.supply reads physical rows as evidence of what is
# absent or negligible (negligible-heat), so a near-zero hybrid row that
# is a data error, not physics, would decide criticality wrongly.
#
# Net calorific values, TJ per tonne, as bnm.supply/ncv (IPCC 2006
# defaults, vol. 2, table 1.2); test/bnm/supply_test.clj holds the two equal
NCV = {"C_ANTH": 0.0267, "C_COKC": 0.0282, "C_OTBC": 0.0258, "C_SUBC": 0.0189, "C_LIBC": 0.0119,
       "C_PEAT": 0.00976, "C_PATF": 0.0207, "C_BKBP": 0.0207, "C_COKE": 0.0282, "C_GCOK": 0.0282,
       "C_COTA": 0.028, "C_COIL": 0.0423, "C_GASE": 0.048, "C_GASL": 0.0442, "C_OGPL": 0.0423,
       "C_REFF": 0.043, "C_ADDC": 0.0443, "C_RGAS": 0.0495, "C_ETHA": 0.0464, "C_LPGA": 0.0473,
       "C_NAPT": 0.0445, "C_MGSL": 0.0443, "C_AGSL": 0.0443, "C_GJET": 0.0443, "C_KJET": 0.0441,
       "C_KERO": 0.0438, "C_DOIL": 0.043, "C_FOIL": 0.0404, "C_WHSP": 0.0402, "C_LUBR": 0.0402,
       "C_BITU": 0.0402, "C_PARW": 0.0402, "C_PETC": 0.0325, "C_NSPP": 0.0402, "C_CHAR": 0.0295,
       "C_BIOG": 0.027, "C_BIOD": 0.027, "C_OBIO": 0.0274, "C_MBIO": 0.0504, "C_COOG": 0.0387,
       "C_MGWG": 0.0387, "C_MBFG": 0.00247, "C_MOSG": 0.00706}
NEGLIGIBLE_HEAT = 0.01  # bnm.supply/negligible-heat

# 1. Electricity in mining and quarrying. The hybrid table gives the
# quarries (stone, sand and clay, chemical and fertiliser minerals) and
# uranium mining next to no electricity in nearly every region (Germany's
# mining 3 TJ in all, against 11,100 TJ of fuel), where UNSD's 2011 energy
# statistics give mining and quarrying (final consumption, transaction
# 1214E; the energy sector's own extraction is apart) a quarter to two
# thirds of its energy as electricity. A quarry crushes, screens and
# washes on electricity. A mining product whose recipe has its electricity
# under NEGLIGIBLE_HEAT of its heat takes UNSD's electricity share of the
# region's mining energy: electricity = the recipe's other heat x share /
# (1 - share), split by source as the region's industries use it. The
# sector's share: quarrying's own is lower (more diesel, less grinding),
# but any share in this range is far from negligible.
# MINING_ELEC_UNSD: region: (electricity TJ, all energy TJ), UNSD Energy
# Statistics Database 2011, 1214E, every commodity (TJ by its conversion
# factor, natural gas net at 0.9 of gross; a rest-of-world region the sum
# of its reporting countries, ROW_MEMBERS). None: no electricity figure
# (Brazil, China, India, Indonesia, Switzerland), or a share above
# MINING_ELEC_MAX, its fuels missing (Britain, Cyprus, Malta, Luxembourg
# report electricity alone; Bulgaria 3,352 of 3,701 TJ); those take the
# median share of the rest.
MINING = ["C_ORAN", "C_IRON", "C_COPO", "C_NIKO", "C_ALUO", "C_PREO", "C_LZTO", "C_ONFO", "C_STON", "C_SDCL",
          "C_CHMF"]
MINING_ELEC_MAX = 0.8
MINING_ELEC_UNSD = {
    "AU": (50200, 119900), "AT": (4669, 7362), "BE": (1519, 2210), "BR": None, "BG": None, "CA": (48410, 131300),
    "CN": None, "CY": None, "CZ": (1415, 3418), "DK": (288, 3176), "EE": (58, 428), "FI": (3938, 6610),
    "FR": (6404, 23070), "DE": (6912, 16220), "GR": (148, 1439), "HU": (205, 1043), "HR": (144, 415), "IN": None,
    "ID": None, "IE": (1793, 3198), "IT": (3107, 7283), "JP": (4892, 17060), "LV": (43, 240), "LT": (104, 204),
    "LU": None, "MT": None, "MX": (26270, 48730), "NL": (900, 5954), "NO": (2192, 5289), "PL": (8006, 15360),
    "PT": (2084, 5660), "RO": (882, 2158), "RU": (81630, 229700), "SK": (155, 466), "SI": (335, 560),
    "ZA": (115300, 166900), "KR": (6941, 10480), "ES": (4874, 12630), "SE": (11970, 20260), "CH": None,
    "TR": (3946, 8917), "GB": None, "US": (121400, 306900), "WA": (29530, 126000), "WL": (124900, 313100),
    "WE": (4313, 13800), "WF": (13010, 39470), "WM": (6080, 8669)}


def mining_elec_share(region):
    """(UNSD's electricity share of the region's mining energy, 'unsd'),
    or the median of the regions with one, 'median'."""
    ok = {r: e / t for r, v in MINING_ELEC_UNSD.items() if v for e, t in [v] if e / t <= MINING_ELEC_MAX}
    if region in ok:
        return ok[region], "unsd"
    return float(np.median(list(ok.values()))), "median"


# 2. Natural gas into N fertiliser. Ammonia takes 0.55-0.75 t of gas per
# t (feed and fuel, 28-36 GJ/t). Where the hybrid row is under a quarter
# of what USGS's 2011 ammonia output needs at 0.58 t/t (the best plants),
# and UNSD's 2011 non-energy gas (transaction 11, net at 0.9 of gross)
# confirms the gas route, the row is set to NFER_GAS_PER_NH3 t per t of
# NH3 over the hybrid's N fertiliser output, at most NFER_GAS_MAX (no
# product needs more than a tonne of ammonia's). Left alone: Norway (no
# gas row; Yara Porsgrunn's feedstock is not shown by UNSD, whose non-
# energy gas there is Tjeldbergodden's methanol) and the Czech Republic
# (Unipetrol's ammonia from heavy-oil residue). NFER_EVIDENCE: region:
# (USGS NH3 kt, hybrid N fertiliser output kt, hybrid gas t/t, UNSD non-
# energy gas kt), USGS Minerals Yearbook (data/derived/supply/recipe-obs/
# nh3.csv, its latest edition; a rest-of-world region the sum of its
# countries).
NFER_GAS_PER_NH3 = 0.65
NFER_GAS_MAX = 0.75
NFER_EVIDENCE = {"LT": (1057.2, 937.9, 0.00284, 840), "TR": (243.2, 1206.4, 0.00611, 220),
                 "WA": (8211, 16439.5, 0.0131, 11399), "WL": (8869, 3533.2, 0.0488, 10649),
                 "WF": (5035, 4568.5, 0.153, 6718), "PL": (2332.1, 1781.4, 0.112, 1586),
                 "HR": (447.4, 19.7, 0.110, 361), "GB": (729.5, 723.4, 0.0846, 402)}
_USGS = "USGS MYB {:,.0f} kt NH3 2011 x 0.65 t gas/t over {:,.0f} kt N fertiliser (hybrid){}; UNSD non-energy gas {:,.0f} kt"
OVERRIDES = [
    # region, output, input, value (t/t), source, reason and citation
    ("LT", "C_NFER", "C_GASE", 0.733, "usgs-ammonia",
     "Achema (Jonava) makes ammonia from gas; hybrid 0.0028 t/t. " + _USGS.format(1057.2, 937.9, "", 840)),
    ("TR", "C_NFER", "C_GASE", 0.131, "usgs-ammonia",
     "Toros and Igsas ammonia from gas, most N fertiliser from imported NH3; hybrid 0.0061 t/t. "
     + _USGS.format(243.2, 1206.4, "", 220)),
    ("WA", "C_NFER", "C_GASE", 0.325, "usgs-ammonia",
     "Pakistan, Bangladesh, Malaysia, Uzbekistan: gas ammonia; hybrid 0.013 t/t. "
     + _USGS.format(8211, 16439.5, "", 11399)),
    ("WL", "C_NFER", "C_GASE", 0.75, "usgs-ammonia",
     "Trinidad, Venezuela, Argentina: gas ammonia; hybrid 0.049 t/t. "
     + _USGS.format(8869, 3533.2, ", capped at 0.75", 10649)),
    ("WF", "C_NFER", "C_GASE", 0.716, "usgs-ammonia",
     "Egypt, Algeria, Nigeria, Libya: gas ammonia; hybrid 0.15 t/t. " + _USGS.format(5035, 4568.5, "", 6718)),
    ("PL", "C_NFER", "C_GASE", 0.75, "usgs-ammonia",
     "Azoty (Pulawy, Tarnow, Kedzierzyn, Police) and Anwil ammonia from gas; hybrid 0.11 t/t. "
     + _USGS.format(2332.1, 1781.4, ", capped at 0.75", 1586)),
    ("HR", "C_NFER", "C_GASE", 0.75, "usgs-ammonia",
     "Petrokemija (Kutina) ammonia from gas; hybrid 0.11 t/t on an output of 20 kt. "
     + _USGS.format(447.4, 19.7, ", capped at 0.75", 361)),
    ("GB", "C_NFER", "C_GASE", 0.655, "usgs-ammonia",
     "CF Fertilisers (Billingham, Ince) ammonia from gas; hybrid 0.085 t/t. " + _USGS.format(729.5, 723.4, "", 402)),
]


def override_recipe(region, output, col, pcode, punit, mix):
    """(col', {input: (hybrid, value, source)}): a recipe column (input
    per unit of `output`, the hybrid's units) with OVERRIDES and the
    mining electricity rule applied; `mix` {electricity product: share}
    the region's industries' electricity by source. `col` is not changed."""
    col = col.copy()
    got = {}
    for r, o, i, v, src, _ in OVERRIDES:
        if (r, o) == (region, output) and i in pcode:
            k = pcode.index(i)
            got[i] = (col[k], v, src)
            col[k] = v
    if output in MINING:
        elec = [k for k, c in enumerate(pcode) if c.startswith("C_POW") and punit[k] == "TJ"]
        other = sum(col[k] * NCV[c] for k, c in enumerate(pcode) if c in NCV)
        other += sum(col[k] for k in range(len(pcode)) if punit[k] == "TJ" and k not in elec)  # steam, nuclear fuel
        e = sum(col[k] for k in elec)
        if other > 0 and e < NEGLIGIBLE_HEAT * (e + other):
            sh, how = mining_elec_share(region)
            tot = other * sh / (1.0 - sh)
            src = "unsd-mining-electricity" if how == "unsd" else "unsd-mining-electricity-median"
            for k in elec:
                v = tot * mix.get(pcode[k], 0.0)
                if v > 0 or col[k] > 0:
                    got[pcode[k]] = (col[k], v, src)
                    col[k] = v
    return col, got


def group(code, unit):
    if unit == "tonnes (service)" or unit == "Meuro" and not code.startswith(("p34", "p35", "p37")):
        return "service"
    if code.startswith(RAW_PREFIX):
        return "raw"
    if code.startswith(ENERGY_PREFIX):
        return "energy"
    if code.startswith(MATERIAL_PREFIX):
        return "material"
    return "manufactured"


def read_hybrid(name):
    """(row labels [region name code codetxt unit], column header rows, matrix)."""
    path = os.path.join(HY, name)
    with open(path, newline="") as fh:
        r = csv.reader(fh)
        hdr = [next(r) for _ in range(4)]
    df = pd.read_csv(path, header=None, skiprows=4, low_memory=False)
    labels = df.iloc[:, :5].astype(str).values.tolist()
    m = df.iloc[:, 5:].to_numpy(dtype=float)
    return labels, [h[5:] for h in hdr], m


def read_pxp(year, regions, products):
    """{'A': (R, P, P) summed over origins, 'x': (R, P)} in the hybrid's
    region order, Taiwan folded into WA by output weight."""
    z = zipfile.ZipFile(os.path.join(PXP, f"IOT_{year}_pxp.zip"))
    d = f"IOT_{year}_pxp/"
    x = pd.read_csv(z.open(d + "x.txt"), sep="\t")
    preg = list(dict.fromkeys(x["region"]))
    pprod = list(x["sector"][: len(x) // len(preg)])
    np_ = len(pprod)
    xv = x["indout"].to_numpy(dtype=float).reshape(len(preg), np_)
    a = pd.read_csv(z.open(d + "A.txt"), sep="\t", skiprows=3, header=None)
    am = a.iloc[:, 2:].to_numpy(dtype=float)
    del a
    agg = am.reshape(len(preg), np_, len(preg), np_).sum(axis=0).transpose(1, 0, 2).copy()  # (dest, input, output)
    del am
    if pprod != products:
        raise SystemExit(f"pxp {year}: products differ from the hybrid's")
    ix = {r: k for k, r in enumerate(preg)}
    A = np.zeros((len(regions), np_, np_))
    X = np.zeros((len(regions), np_))
    for k, r in enumerate(regions):
        if r == "WA" and "TW" in ix:
            w1, w2 = xv[ix["WA"]], xv[ix["TW"]]
            tot = w1 + w2
            with np.errstate(invalid="ignore", divide="ignore"):
                A[k] = np.where(tot > 0, (agg[ix["WA"]] * w1 + agg[ix["TW"]] * w2) / tot, 0.0)
            X[k] = tot
        else:
            A[k] = agg[ix[r]]
            X[k] = xv[ix[r]]
    return {"A": A, "x": X}


def unit_split(vd, pr, punit, units):
    """{unit: (I,)} the share of each industry's inputs that goes with its
    output in that unit: 1 for an industry making products in one unit; for
    one making them in several, the value of its output in each (vd (P, I)
    supply, pr (P,) prices), or equal parts where some unit's output has no
    value. The shares of an industry add to 1."""
    out = {}
    val = {}
    for u in units:
        cols = [p for p in range(len(punit)) if punit[p] == u]
        out[u] = vd[cols].sum(axis=0)  # physical output in u, (I,)
        val[u] = (vd[cols] * pr[cols][:, None]).sum(axis=0)
    has = {u: out[u] > 0 for u in units}
    n = sum(has[u].astype(float) for u in units)
    priced = np.all([~has[u] | (val[u] > 0) for u in units], axis=0)
    vtot = sum(val[u] for u in units)
    with np.errstate(invalid="ignore", divide="ignore"):
        return {u: np.where(has[u], np.where(priced & (vtot > 0), val[u] / vtot, 1.0 / np.maximum(n, 1.0)), 0.0)
                for u in units}


def unsd_2011(flow):
    """{(iso3, exiobase code): kt} of UNSD's 2011 figures for `flow` (01
    production, GA total energy supply), or None without oil-products.csv."""
    path = os.path.join(OUT, "oil-products.csv")
    if not os.path.exists(path):
        return None
    o = pd.read_csv(path, dtype={"flow": str, "iso": str})
    o = o[(o["year"] == 2011) & (o["flow"] == flow)]
    return o.groupby(["iso", "exiobase"])["value"].sum().to_dict()


def region_figures(regions, un):
    """{(region, code): kt}: UNSD's figures `un` ({(iso3, code): kt}) for
    the graph's regions, a rest-of-world region the sum of its countries
    (ROW_MEMBERS; WA every country no other region takes)."""
    iso_region = {ISO3[r]: r for r in regions if r in ISO3}
    for r, members in ROW_MEMBERS.items():
        if r in regions:
            iso_region.update({i: r for i in members.split()})
    taken = set(ISO3.values()) | {i for m in ROW_MEMBERS.values() for i in m.split()}
    out = {}
    for (iso, code), v in un.items():
        r = iso_region.get(iso, "WA" if "WA" in regions and iso not in taken else None)
        if r is not None:
            out[(r, code)] = out.get((r, code), 0.0) + v
    return out


def targets(regions, pcode, x, figs, grp):
    """{region: (exiobase kt, unsd kt or None, target kt, source)} for the
    products `grp` together: x (R, P) EXIOBASE's tonnes (production or
    use), figs UNSD's kt by region (region_figures). Target UNSD's where
    both are above SCALE_MIN_KT; else the median ratio over those regions
    times EXIOBASE's (small or unreported producers). {} where no region
    has a figure."""
    ix = [pcode.index(c) for c in grp]
    own = {}
    for s, reg in enumerate(regions):
        e = x[s, ix].sum() / 1000.0
        hit = [figs[(reg, c)] for c in grp if (reg, c) in figs]
        own[reg] = (e, sum(hit) if hit else None)
    ok = {r: u / e for r, (e, u) in own.items() if u is not None and e > SCALE_MIN_KT and u > SCALE_MIN_KT}
    if not ok:
        return {}
    med = float(np.median(list(ok.values())))
    return {r: (e, u, u if r in ok else e * med, "unsd" if r in ok else "median") for r, (e, u) in own.items() if e > 0}


def scale_to_unsd(regions, pcode, V4, U4, Y4, prod_un, use_un, groups=SCALED):
    """Scale V4, U4, Y4 in place, each group of `groups` to UNSD's 2011
    figures (prod_un, use_un: {(iso3, code): kt}, flows 01 and GA): for a
    group the origin-by-destination matrix of its uses (every industry and
    final demand) is fitted by biproportional scaling (RAS) to production
    by origin and, but for NO_USE_TARGET, to use by destination. A
    rest-of-world region's figure is the sum of its countries'
    (region_figures). Each product's supply in a region is scaled with its
    own uses from there, so supply still equals use product by product.
    Origins without a UNSD figure take the median ratio; destinations
    without one are free (they take up what the production and use totals
    leave). Returns [(region, group, kind, exiobase kt, unsd kt, target kt
    (None for a free destination), fitted kt, source unsd|median|free)]."""
    prod_f = region_figures(regions, prod_un)
    use_f = region_figures(regions, use_un)
    R = len(regions)
    report = []
    for grp in groups:
        ix = [pcode.index(c) for c in grp]
        q = np.stack([V4[r, :, r, :].sum(axis=1) for r in range(R)])
        M0p = {p: U4[:, p, :, :].sum(axis=2) + Y4[:, p, :, :].sum(axis=2) for p in ix}  # (origin, dest) tonnes
        M0 = sum(M0p.values())
        use = np.zeros((R, len(pcode)))
        for p in ix:
            use[:, p] = M0p[p].sum(axis=0)
        rt = targets(regions, pcode, q, prod_f, grp)
        if not rt:
            continue
        ct = targets(regions, pcode, use, use_f, grp) if grp not in NO_USE_TARGET else {}
        rows = np.array([rt[r][2] * 1000.0 if r in rt else 0.0 for r in regions])
        # destinations without a UNSD figure are left free: they take up
        # the difference between the production and use targets' totals
        cols = np.array([ct[r][2] * 1000.0 if r in ct and ct[r][3] == "unsd" else np.nan for r in regions])
        M = M0.copy()
        for _ in range(5000):
            with np.errstate(invalid="ignore", divide="ignore"):
                rs = M.sum(axis=1)
                M *= np.where(rs > 0, rows / rs, 0.0)[:, None]
                cs = M.sum(axis=0)
                cf = np.where(np.isfinite(cols) & (cs > 0), cols / cs, 1.0)
                M *= cf[None, :]
            rs = M.sum(axis=1)
            with np.errstate(invalid="ignore", divide="ignore"):
                if np.nanmax(np.abs(np.where(rows > 0, rs / rows - 1, 0.0))) < 1e-10 and np.abs(cf - 1).max() < 1e-10:
                    break
        with np.errstate(invalid="ignore", divide="ignore"):
            f = np.where(M0 > 0, M / M0, 1.0)  # (origin, dest)
        for p in ix:
            before = M0p[p].sum(axis=1)
            with np.errstate(invalid="ignore", divide="ignore"):
                fv = np.where(before > 0, (M0p[p] * f).sum(axis=1) / before, 1.0)
            U4[:, p, :, :] *= f[:, :, None]
            Y4[:, p, :, :] *= f[:, :, None]
            for r in range(R):
                V4[r, p, r, :] *= fv[r]
        fitted_rows = M.sum(axis=1) / 1000.0
        fitted_cols = M.sum(axis=0) / 1000.0
        for k, reg in enumerate(regions):
            if reg in rt:
                e, u, t, src = rt[reg]
                report.append((reg, grp, "production", e, u, t, fitted_rows[k], src))
            if reg in ct:
                e, u, t, src = ct[reg]
                free = src != "unsd"
                report.append((reg, grp, "use", e, u, None if free else t, fitted_cols[k], "free" if free else src))
    return report


def refinery_balance(regions, prod, punit, pcode, ind, Vd, Ud):
    """{(region, check): (in, out, (out - in) / in)} for the refinery
    industry, as balances.csv has it."""
    refn = [j for j, (c, _) in enumerate(ind) if c == "A_REFN"][0]
    refin = [pcode.index(c) for c in REFINERY_IN if c in pcode]
    refout = [p for p in range(len(prod)) if prod[p][3].startswith("p23.20") and punit[p] == "tonnes"]
    refall = [p for p in range(len(prod)) if punit[p] == "tonnes"
              and (prod[p][3].startswith(("p11", "p23.20")) or pcode[p] in REFINERY_EXTRA)]
    nogas = [p for p in refall if pcode[p] != "C_GASE"]
    out = {}
    for s, reg in enumerate(regions):
        cout = Vd[s][refout, refn].sum()
        for name, idx in [("refinery", refin), ("refinery-all", refall), ("refinery-nogas", nogas)]:
            cin = Ud[s][idx, refn].sum()
            if cin > 0 or cout > 0:
                out[(reg, name)] = (cin, cout, (cout - cin) / cin if cin > 0 else np.nan)
    return out


def fmt(v):
    return "" if v is None or not np.isfinite(v) else f"{v:.6g}"


def main():
    os.makedirs(OUT, exist_ok=True)
    plab, phdr, V = read_hybrid("MR_HSUP_2011_v3_3_18.csv")
    ulab, uhdr, U = read_hybrid("MR_HUSE_2011_v3_3_18.csv")
    flab, fhdr, Y = read_hybrid("MR_HSUTs_2011_v3_3_18_FD.csv")
    regions = list(dict.fromkeys(l[0] for l in plab))
    R = len(regions)
    P = len(plab) // R
    I = len(phdr[0]) // R
    C = len(fhdr[0]) // R
    assert ulab == plab and flab == plab and uhdr == phdr
    assert list(dict.fromkeys(phdr[0])) == regions and list(dict.fromkeys(fhdr[0])) == regions
    prod = [(l[3], l[1], l[4], l[2]) for l in plab[:P]]  # codetxt, name, unit, code
    pcode = [p[0] for p in prod]
    punit = [p[2] for p in prod]
    pnames = [p[1] for p in prod]
    ind = [(phdr[3][j], phdr[1][j]) for j in range(I)]
    fd = [fhdr[3][c] for c in range(C)]
    print(f"hybrid: {R} regions, {P} products, {I} industries, {C} final demand categories")

    V4 = V.reshape(R, P, R, I)
    U4 = U.reshape(R, P, R, I)
    Y4 = Y.reshape(R, P, R, C)
    del V, U, Y
    off = V4.sum() - sum(V4[r, :, r, :].sum() for r in range(R))
    print(f"supply off the regional diagonal: {off:.3g} (should be 0)")

    # light and non-fuel refined products scaled to UNSD's 2011 figures
    # (scale_to_unsd): supply still equals uses; the refinery's inputs are
    # not touched (the excess is what its refinery industry also takes in:
    # gas processing, product backflows), so the refinery balance moves
    # toward its crude
    before = refinery_balance(regions, prod, punit, pcode, ind, np.stack([V4[r, :, r, :] for r in range(R)]),
                              U4.sum(axis=0).transpose(1, 0, 2))
    prod_un = unsd_2011("01")
    six = [pcode.index(c) for grp in SCALED for c in grp]
    with np.errstate(invalid="ignore", divide="ignore"):
        off0 = supply_less_use(V4[:, six], U4[:, six], Y4[:, six])
    scaling = None if prod_un is None else scale_to_unsd(regions, pcode, V4, U4, Y4, prod_un, unsd_2011("GA"))
    if scaling is None:
        print("no oil-products.csv: refined products not scaled to UNSD (run scripts/oil-products.py first)")
    else:
        # the tables' own rounding (up to 1e-4 of supply) stays as it was
        with np.errstate(invalid="ignore", divide="ignore"):
            off = supply_less_use(V4[:, six], U4[:, six], Y4[:, six])
        made = np.isfinite(off0) & np.isfinite(off)
        moved = np.abs(off[made] - off0[made]).max()
        print(f"scaled products: supply less use moved by at most {moved:.2g} of supply (should be 0); "
              f"as read, up to {np.abs(off0[made]).max():.2g}")
        assert moved < 1e-9, "the scaling broke a product's balance"
        with open(os.path.join(OUT, "scaling.csv"), "w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["region", "products", "kind", "exiobase_kt", "unsd_kt", "target_kt", "fitted_kt", "source"])
            for reg, grp, kind, e, u, t, fit, src in scaling:
                w.writerow([reg, "+".join(grp), kind, fmt(e), fmt(u), fmt(t), fmt(fit), src])
        for grp in SCALED:
            for kind in ["production", "use"]:
                rs = [x for x in scaling if x[1] == grp and x[2] == kind]
                if not rs:
                    continue
                un = [x for x in rs if x[7] == "unsd"]
                off = np.array([x[6] / x[5] - 1 for x in un if x[5] > 0])
                print(f"scaled {'+'.join(grp)} {kind}: {len(un)} regions to UNSD (EXIOBASE/UNSD median "
                      f"{np.median([x[3] / x[4] for x in un]):.2f}), {len(rs) - len(un)} "
                      f"{'at the median ratio' if kind == 'production' else 'free'}; "
                      f"world {sum(x[3] for x in rs) / 1000:.0f} -> {sum(x[6] for x in rs) / 1000:.0f} Mt; "
                      f"fitted to UNSD within {np.abs(off).max():.1%} (median {np.median(np.abs(off)):.1%})")

    Vd = np.stack([V4[r, :, r, :] for r in range(R)])  # (R, P, I)
    q = Vd.sum(axis=2)  # (R, P) production
    Ud = U4.sum(axis=0).transpose(1, 0, 2).copy()  # (dest, P, I)
    Yd = Y4.sum(axis=0).transpose(1, 0, 2).copy()  # (dest, P, C)
    T = U4.sum(axis=3) + Y4.sum(axis=3)  # (origin, P, dest)

    # monetary tables
    mon_products = None
    with zipfile.ZipFile(os.path.join(PXP, "IOT_2011_pxp.zip")) as z:
        x = pd.read_csv(z.open("IOT_2011_pxp/x.txt"), sep="\t")
        mon_products = list(x["sector"][: len(x) // len(dict.fromkeys(x["region"]))])
    pxp_names = {}
    with zipfile.ZipFile(os.path.join(PXP, "IOT_2011_pxp.zip")) as z:
        pt = pd.read_csv(z.open("IOT_2011_pxp/products.txt"), sep="\t")
        pxp_names = dict(zip(pt["CodeTxt"], pt["Name"]))
    if [pxp_names.get(c) for c in pcode] != mon_products:
        raise SystemExit("pxp product order differs from the hybrid's")
    mon = {}
    for y in YEARS:
        mon[y] = read_pxp(y, regions, mon_products)
        print(f"pxp {y}: read")

    # products, industries
    with open(os.path.join(OUT, "products.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["code", "name", "unit", "group"])
        for c, n, u, k in prod:
            w.writerow([c, n.strip(), u, group(k, u)])
    with open(os.path.join(OUT, "industries.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["code", "name"])
        for c, n in ind:
            w.writerow([c, n.strip()])

    # prices: monetary output 2011 over hybrid supply, M EUR per unit. The
    # two releases (hybrid v3.3.18, monetary v3.8.2) do not always agree on
    # a product's size, so these value the use table only roughly
    with np.errstate(invalid="ignore", divide="ignore"):
        price = np.where(q > 0, mon[2011]["x"] / q, 0.0)  # (R, P)

    # recipes: industry technology in hybrid units
    units = sorted(set(punit))
    nrec = 0
    nphys = 0
    for r, (_, nfer, _, _) in NFER_EVIDENCE.items():
        got = q[regions.index(r), pcode.index("C_NFER")] / 1000.0
        assert abs(got / nfer - 1) < 0.01, f"{r}: N fertiliser output {got:.1f} kt, the override cites {nfer}"
    elec = [k for k, c in enumerate(pcode) if c.startswith("C_POW") and punit[k] == "TJ"]
    reason = {(r, o, i): why for r, o, i, _, _, why in OVERRIDES}
    overridden = []
    with open(os.path.join(OUT, "recipes.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "output", "input", "phys", "input_unit", "output_unit",
                    "share_1995", "share_2011", "share_2022", "physical", "override"])
        for s, reg in enumerate(regions):
            used = Ud[s][elec].sum(axis=1)
            mix = {pcode[k]: used[n] / used.sum() for n, k in enumerate(elec)} if used.sum() > 0 else {}
            M = np.zeros((I, P))
            theta = unit_split(Vd[s], price[s], punit, units)  # {unit: (I,)}
            for u in units:
                cols = [p for p in range(P) if punit[p] == u]
                g = Vd[s][cols].sum(axis=0)  # (I,) output in unit u
                for p in cols:
                    if q[s, p] <= 0:
                        continue
                    with np.errstate(invalid="ignore", divide="ignore"):
                        M[:, p] = np.where(g > 0, Vd[s, p] / q[s, p] / g * theta[u], 0.0)
            a = Ud[s] @ M  # (input P, output P)
            for p in range(P):
                if q[s, p] <= 0:
                    continue
                col, got = override_recipe(reg, pcode[p], a[:, p], pcode, punit, mix)
                # the output's physical inputs by unit as the hybrid has them, for the 1%
                # rule (an override adds rows, it does not drop any)
                by_unit = {u: a[[i for i in range(P) if punit[i] == u], p].sum() for u in PHYS}
                for i in range(P):
                    if i == p:
                        continue
                    sh = [mon[y]["A"][s, i, p] if mon[y]["x"][s, p] > 0 else np.nan for y in YEARS]
                    phys = punit[i] in PHYS and punit[p] in PHYS
                    keep = sh[1] >= 1e-4 or (phys and col[i] > 0 and col[i] >= 0.01 * by_unit[punit[i]])
                    if not keep or (col[i] <= 0 and not sh[1] > 0):
                        continue
                    src = got[pcode[i]][2] if pcode[i] in got else ""
                    w.writerow([reg, pcode[p], pcode[i], fmt(col[i]), punit[i], punit[p],
                                fmt(sh[0]), fmt(sh[1]), fmt(sh[2]), 1 if phys else 0, src])
                    nrec += 1
                    nphys += phys
                    if src:
                        why = reason.get((reg, pcode[p], pcode[i]), "electricity share of mining energy " + (
                            "UNSD 2011 1214E" if src == "unsd-mining-electricity" else "the median of UNSD's regions")
                            + f" {mining_elec_share(reg)[0]:.3f}, split by the region's industrial mix")
                        overridden.append([reg, pcode[p], pcode[i], fmt(got[pcode[i]][0]), fmt(col[i]),
                                           f"{punit[i]}/{punit[p]}", src, why])
    print(f"recipes: {nrec} rows, {nphys} physical")
    with open(os.path.join(OUT, "overrides.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "output", "input", "hybrid", "value", "unit", "source", "reason"])
        w.writerows(overridden)
    print(f"overrides: {len(overridden)} recipe rows "
          f"({sum(1 for x in overridden if x[6] == 'usgs-ammonia')} N fertiliser gas, the rest mining electricity in "
          f"{len({(x[0], x[1]) for x in overridden if x[6] != 'usgs-ammonia'})} recipes)")

    # production
    with open(os.path.join(OUT, "production.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "product", "quantity", "unit", "value_1995", "value_2011", "value_2022"])
        for s, reg in enumerate(regions):
            for p in range(P):
                if q[s, p] > 0 or mon[2011]["x"][s, p] > 0:
                    w.writerow([reg, pcode[p], fmt(q[s, p]), punit[p]] + [fmt(mon[y]["x"][s, p]) for y in YEARS])

    # what each industry makes
    nsup = 0
    with open(os.path.join(OUT, "supply.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "industry", "product", "quantity", "unit", "market_share", "industry_share"])
        for s, reg in enumerate(regions):
            val = Vd[s] * price[s][:, None]  # (P, I)
            tot = val.sum(axis=0)
            for j in range(I):
                for p in np.nonzero(Vd[s][:, j] > 0)[0]:
                    ish = val[p, j] / tot[j] if tot[j] > 0 else np.nan
                    if ish >= 1e-3 or not np.isfinite(ish):
                        w.writerow([reg, ind[j][0], pcode[p], fmt(Vd[s][p, j]), punit[p],
                                    fmt(Vd[s][p, j] / q[s, p]), fmt(ish)])
                        nsup += 1
    print(f"supply: {nsup} rows")

    # use by industry and final demand, valued at origin prices
    nuse = 0
    vs_over = 0
    with open(os.path.join(OUT, "use.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "product", "user", "kind", "quantity", "unit", "share", "value_share", "value_rough"])
        for s, reg in enumerate(regions):
            uval = np.einsum("rpj,rp->pj", U4[:, :, s, :], price)  # (P, I)
            outval = (Vd[s] * price[s][:, None]).sum(axis=0)  # (I,)
            tot = Ud[s].sum(axis=1) + Yd[s].sum(axis=1)
            for p in range(P):
                if tot[p] <= 0:
                    continue
                for j in range(I):
                    qty = Ud[s, p, j]
                    if qty <= 0:
                        continue
                    sh = qty / tot[p]
                    vs = uval[p, j] / outval[j] if outval[j] > 0 else np.nan
                    if sh >= 1e-4 or (np.isfinite(vs) and vs >= 1e-4):
                        over = np.isfinite(vs) and vs > 1
                        vs_over += over
                        w.writerow([reg, pcode[p], ind[j][0], "industry", fmt(qty), punit[p], fmt(sh),
                                    "" if over else fmt(vs), fmt(vs) if over else ""])
                        nuse += 1
                for c in range(C):
                    qty = Yd[s, p, c]
                    if qty > 0 and qty / tot[p] >= 1e-4:
                        w.writerow([reg, pcode[p], fd[c], "final", fmt(qty), punit[p], fmt(qty / tot[p]), "", ""])
                        nuse += 1
    print(f"use: {nuse} rows; {vs_over} industry inputs valued above the industry's output (value_rough)")

    # trade: origins of what each region uses
    ntr = 0
    with open(os.path.join(OUT, "trade.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["product", "origin", "dest", "quantity", "share"])
        for p in range(P):
            for s, reg in enumerate(regions):
                col = T[:, p, s]
                tot = col.sum()
                if tot <= 0:
                    continue
                for r in range(R):
                    if col[r] > 0 and col[r] / tot >= 1e-3:
                        w.writerow([pcode[p], regions[r], reg, fmt(col[r]), fmt(col[r] / tot)])
                        ntr += 1
    print(f"trade: {ntr} rows")

    # balances
    after = refinery_balance(regions, prod, punit, pcode, ind, Vd, Ud)
    rows = []
    for s, reg in enumerate(regions):
        used = T[s].sum(axis=1)  # (P,) every use of s's output
        for p in range(P):
            if q[s, p] > 0:
                rows.append([reg, "product:" + pcode[p], used[p], q[s, p], punit[p], (q[s, p] - used[p]) / q[s, p]])
        for name in ["refinery", "refinery-all", "refinery-nogas"]:
            if (reg, name) in after:
                cin, cout, res = after[(reg, name)]
                rows.append([reg, name, cin, cout, "tonnes", res])
                if scaling is not None:
                    rows.append([reg, name + "-unscaled", before[(reg, name)][0], before[(reg, name)][1], "tonnes",
                                 before[(reg, name)][2]])
    with open(os.path.join(OUT, "balances.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "check", "input", "output", "unit", "residual"])
        for r in rows:
            w.writerow(r[:2] + [fmt(r[2]), fmt(r[3]), r[4], fmt(r[5])])
    prodres = np.array([abs(r[5]) for r in rows if r[1].startswith("product:") and r[3] > 0])
    bygroup = {}
    for r in rows:
        if r[1].startswith("product:") and r[3] > 0 and abs(r[5]) > 0.01:
            g = group(prod[pcode.index(r[1][8:])][3], r[4])
            bygroup[g] = bygroup.get(g, 0) + 1
    print(f"product balances off by more than 1%, by group: {bygroup}")
    print(f"product balances: {len(prodres)}; |residual| median {np.median(prodres):.3g}, "
          f"90th pct {np.percentile(prodres, 90):.3g}, above 1%: {(prodres > 0.01).sum()}")
    for name in ["refinery-unscaled", "refinery", "refinery-all-unscaled", "refinery-all", "refinery-nogas-unscaled",
                 "refinery-nogas"]:
        rs = [r for r in rows if r[1] == name and r[2] > 1e6]
        if not rs:
            continue
        res = np.array([r[5] for r in rs])
        print(f"{name}: {len(rs)} regions refining over 1 Mt; residual (out - in) / in median {np.median(res):+.3f}, "
              f"range {res.min():+.3f} to {res.max():+.3f}")
        print("  " + ", ".join(f"{r[0]} {r[5]:+.2f}" for r in rs))


# ---------------------------------------------------------------- self-test

def toy_tables(regions, pcode, seed=7):
    """V4, U4, Y4 for a toy economy in which every region's supply of a
    product equals its uses (tonnes)."""
    R, P, I, C = len(regions), len(pcode), 3, 2
    rng = np.random.default_rng(seed)
    U4 = rng.uniform(0.5, 2.0, (R, P, R, I)) * 1e5
    Y4 = rng.uniform(0.5, 2.0, (R, P, R, C)) * 1e5
    V4 = np.zeros((R, P, R, I))
    for r in range(R):
        V4[r, :, r, :] = (U4[r].sum(axis=(1, 2)) + Y4[r].sum(axis=(1, 2)))[:, None] / I
    return V4, U4, Y4


def supply_less_use(V4, U4, Y4):
    """(R, P) each region's supply of each product less every use of it,
    relative to its supply."""
    R = V4.shape[0]
    sup = np.stack([V4[r, :, r, :].sum(axis=1) for r in range(R)])
    use = U4.sum(axis=(2, 3)) + Y4.sum(axis=(2, 3))
    return (sup - use) / sup


def selftest():
    """python -I scripts/product-graph.py --selftest"""
    regions = ["DE", "FR", "WM", "WA"]
    pcode = ["C_NAPT", "C_LPGA", "C_KJET", "C_KERO", "C_OTHR"]
    # a region of the rest of the world takes the sum of its countries:
    # WM Saudi Arabia and Iran, WA (the rest) Thailand
    un = {("DEU", "C_NAPT"): 1200.0, ("FRA", "C_NAPT"): 900.0, ("SAU", "C_NAPT"): 700.0, ("IRN", "C_NAPT"): 500.0,
          ("THA", "C_NAPT"): 800.0, ("NOR", "C_NAPT"): 5000.0}
    figs = region_figures(regions, un)
    assert figs[("WM", "C_NAPT")] == 1200.0 and figs[("WA", "C_NAPT")] == 800.0, figs
    assert figs[("DE", "C_NAPT")] == 1200.0 and ("NO", "C_NAPT") not in figs, "only the graph's regions"

    V4, U4, Y4 = toy_tables(regions, pcode)
    V0, U0, Y0 = V4.copy(), U4.copy(), Y4.copy()
    assert np.abs(supply_less_use(V4, U4, Y4)).max() < 1e-12
    prod_un = {**un, ("DEU", "C_KJET"): 600.0, ("DEU", "C_KERO"): 300.0, ("FRA", "C_KJET"): 700.0,
               ("SAU", "C_KJET"): 1000.0, ("THA", "C_KERO"): 400.0}
    # use: Germany, France and the Gulf; Thailand none, so WA is free and
    # takes up what is left (production 4,100 kt, these 3,000)
    use_un = {("DEU", "C_NAPT"): 1500.0, ("FRA", "C_NAPT"): 600.0, ("SAU", "C_NAPT"): 900.0}
    rep = scale_to_unsd(regions, pcode, V4, U4, Y4, prod_un, use_un, groups=[["C_NAPT"], ["C_KJET", "C_KERO"]])
    got = {(r[0], "+".join(r[1]), r[2]): r for r in rep}
    # supply equals use, product by product, region by region
    assert np.abs(supply_less_use(V4, U4, Y4)).max() < 1e-9
    # production fitted to UNSD by origin, use by destination where it has a figure
    for reg, t in [("DE", 1200.0), ("FR", 900.0), ("WM", 1200.0), ("WA", 800.0)]:
        e, u, target, fitted, src = [got[(reg, "C_NAPT", "production")][k] for k in (3, 4, 5, 6, 7)]
        assert src == "unsd" and target == t and abs(fitted / t - 1) < 1e-6, (reg, got[(reg, "C_NAPT", "production")])
        assert abs(V4[regions.index(reg), 0, regions.index(reg), :].sum() / 1000.0 - t) < 1e-6 * t
    for reg, t in [("DE", 1500.0), ("FR", 600.0), ("WM", 900.0)]:
        assert abs(got[(reg, "C_NAPT", "use")][6] / t - 1) < 1e-6, got[(reg, "C_NAPT", "use")]
    assert abs(got[("WA", "C_NAPT", "use")][6] - (4100.0 - 3000.0)) < 1e-3, "the free destination takes the rest"
    assert got[("WA", "C_NAPT", "use")][7] == "free"
    # kerosene: production only (UNSD's supply leaves out bunkers)
    assert ("DE", "C_KJET+C_KERO", "use") not in got
    assert abs(got[("DE", "C_KJET+C_KERO", "production")][6] - 900.0) < 1e-3
    # a product not scaled is left alone
    k = pcode.index("C_OTHR")
    assert np.array_equal(V4[:, k], V0[:, k]) and np.array_equal(U4[:, k], U0[:, k]) and np.array_equal(Y4[:, k], Y0[:, k])
    assert np.array_equal(U4[:, pcode.index("C_LPGA")], U0[:, pcode.index("C_LPGA")])

    # a group of two products fitted on use too keeps each product's supply
    # equal to its own uses
    V4, U4, Y4 = toy_tables(regions, pcode, seed=3)
    scale_to_unsd(regions, pcode, V4, U4, Y4, {**un, ("DEU", "C_LPGA"): 400.0},
                  {("DEU", "C_NAPT"): 1500.0, ("FRA", "C_NAPT"): 600.0, ("SAU", "C_NAPT"): 900.0},
                  groups=[["C_NAPT", "C_LPGA"]])
    assert np.abs(supply_less_use(V4, U4, Y4)).max() < 1e-9

    # an origin without a figure takes the median ratio of those with one
    V4, U4, Y4 = toy_tables(regions, pcode, seed=5)
    e = {r: V4[i, 0, i, :].sum() / 1000.0 for i, r in enumerate(regions)}
    part = {("DEU", "C_NAPT"): 1200.0, ("FRA", "C_NAPT"): 900.0, ("SAU", "C_NAPT"): 1200.0}
    rep = scale_to_unsd(regions, pcode, V4, U4, Y4, part, {}, groups=[["C_NAPT"]])
    wa = [r for r in rep if r[0] == "WA" and r[2] == "production"][0]
    med = np.median([1200.0 / e["DE"], 900.0 / e["FR"], 1200.0 / e["WM"]])
    assert wa[7] == "median" and wa[4] is None and abs(wa[5] - e["WA"] * med) < 1e-6 * wa[5], wa
    assert np.abs(supply_less_use(V4, U4, Y4)).max() < 1e-9
    # no figure at all for a group: nothing scaled
    V4, U4, Y4 = toy_tables(regions, pcode, seed=5)
    V0 = V4.copy()
    assert scale_to_unsd(regions, pcode, V4, U4, Y4, {}, {}, groups=[["C_NAPT"]]) == []
    assert np.array_equal(V4, V0)

    # refinery balances: against crude and feedstocks, everything, and
    # everything but natural gas (gas processing returns its dry gas to the
    # grid, which the tables do not record as refinery output)
    prod = [("C_COIL", "crude", "tonnes", "p11.a"), ("C_GASE", "gas", "tonnes", "p11.b"),
            ("C_DOIL", "diesel", "tonnes", "p23.20.d"), ("C_RGAS", "refinery gas", "tonnes", "p23.20.a")]
    pc = [p[0] for p in prod]
    pu = [p[2] for p in prod]
    ind = [("A_REFN", "refining")]
    Vd = np.array([[[0.0], [0.0], [90.0], [6.0]]])
    Ud = np.array([[[100.0], [20.0], [0.0], [5.0]]])
    b = refinery_balance(["XX"], prod, pu, pc, ind, Vd, Ud)
    assert abs(b[("XX", "refinery")][2] - (96.0 - 100.0) / 100.0) < 1e-12
    assert abs(b[("XX", "refinery-all")][2] - (96.0 - 125.0) / 125.0) < 1e-12
    assert abs(b[("XX", "refinery-nogas")][2] - (96.0 - 105.0) / 105.0) < 1e-12

    # overrides (OVERRIDES, MINING_ELEC_UNSD): each pinned to its source
    ov = {(r, o, i): (v, src) for r, o, i, v, src, _ in OVERRIDES}
    assert sorted(r for r, _, _ in ov) == ["GB", "HR", "LT", "PL", "TR", "WA", "WF", "WL"], sorted(ov)
    for r, (nh3, nfer, hyb, unsd) in NFER_EVIDENCE.items():
        v = ov[(r, "C_NFER", "C_GASE")][0]
        assert abs(v - min(NFER_GAS_MAX, NFER_GAS_PER_NH3 * nh3 / nfer)) < 5e-4, (r, v)
        assert hyb < v / 4, f"{r}: an override only where the hybrid row is under a quarter of the physics"
        assert unsd >= 0.5 * NFER_GAS_PER_NH3 * nh3, f"{r}: UNSD's non-energy gas confirms a gas route"
    assert ov[("LT", "C_NFER", "C_GASE")][0] == 0.733 and ov[("GB", "C_NFER", "C_GASE")][0] == 0.655
    assert ov[("TR", "C_NFER", "C_GASE")][0] == 0.131 and ov[("WA", "C_NFER", "C_GASE")][0] == 0.325
    assert ov[("WF", "C_NFER", "C_GASE")][0] == 0.716
    assert all(ov[(r, "C_NFER", "C_GASE")][0] == 0.75 for r in ["PL", "HR", "WL"]), "capped at a tonne of NH3's gas"
    assert abs(mining_elec_share("DE")[0] - 6912 / 16220) < 1e-12 and mining_elec_share("DE")[1] == "unsd"
    med = mining_elec_share("CN")
    assert med[1] == "median" and abs(med[0] - 0.4038) < 1e-4, med
    assert mining_elec_share("GB")[1] == "median", "Britain reports only electricity for mining"
    assert mining_elec_share("BG")[1] == "median", "Bulgaria's fuels for mining are mostly missing"
    # applied to a recipe column
    pc = ["C_SDCL", "C_IRON", "C_DOIL", "C_POWC", "C_POWG", "C_POWT", "C_HWAT", "C_NFER", "C_GASE", "C_MACH"]
    pu = ["tonnes", "tonnes", "tonnes", "TJ", "TJ", "Meuro", "TJ", "tonnes", "tonnes", "tonnes"]
    mix = {"C_POWC": 0.75, "C_POWG": 0.25}
    col = np.array([0, 0, 1e-4, 1e-9, 0, 1e-8, 1e-7, 0, 0, 0.0])
    new, got = override_recipe("DE", "C_SDCL", col, pc, pu, mix)
    s = 6912 / 16220
    heat = 1e-4 * NCV["C_DOIL"] + 1e-7
    assert abs(new[3] + new[4] - heat * s / (1 - s)) < 1e-15 and abs(new[3] / (new[3] + new[4]) - 0.75) < 1e-12
    assert new[5] == col[5] and new[2] == col[2] and new[6] == col[6], "only the electricity by source moves"
    assert set(got) == {"C_POWC", "C_POWG"} and got["C_POWC"][2] == "unsd-mining-electricity"
    assert col[3] == 1e-9, "the column is not changed in place"
    # electricity already at 1% of the heat or more: the hybrid row stands
    col2 = col.copy()
    col2[3] = 0.02 * heat
    assert override_recipe("DE", "C_SDCL", col2, pc, pu, mix)[1] == {}
    # no fuel at all: nothing to scale from
    assert override_recipe("DE", "C_SDCL", np.zeros(len(pc)), pc, pu, mix)[1] == {}
    # not a mining product
    assert override_recipe("DE", "C_MACH", col, pc, pu, mix)[1] == {}
    # N fertiliser: the gas set to the cited value
    col3 = np.zeros(len(pc))
    col3[8] = 0.00283659
    new, got = override_recipe("LT", "C_NFER", col3, pc, pu, mix)
    assert new[8] == 0.733 and got["C_GASE"] == (0.00283659, 0.733, "usgs-ammonia")
    assert override_recipe("DE", "C_NFER", col3, pc, pu, mix)[1] == {}, "Germany's stands"
    print("product-graph.py selftest ok")


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        selftest()
    else:
        main()
