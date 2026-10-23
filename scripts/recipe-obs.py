"""Observations for the recipe filter (bnm.recipes, bead bnm-1pq.6;
docs/ESSENTIALS.md 7a and 8 step 5). Reads data/raw and writes
data/derived/supply/recipe-obs/:

  pxp-shares.csv  year,region,output,input,share
                  The monetary technical coefficient (input value per unit
                  of output value, all origins summed) from each EXIOBASE
                  v3.8.2 pxp table held (1995, 2000, 2005, 2008, 2011,
                  2015, 2019, 2022), for the outputs bnm.recipes reads,
                  every input with a share of at least 1e-6. Taiwan is
                  folded into WA by output weight, as scripts/
                  product-graph.py does.
  unsd.csv        iso,year,known,series,value
                  UNSD energy statistics, per country (ISO alpha-3 from
                  M49), known = year + 2:
                    fuel_<f>       TJ of fuel f (coal, gas, oil) into
                                   electricity-only and CHP plants
                                   (transactions 08811, 08812, 08821,
                                   08822), at UNSD's conversion factors
                                   (gas in TJ as UNSD reports it, gross
                                   calorific value);
                    heatfuel_<f>   TJ of fuel f into heat-only plants
                                   (08831, 08832);
                    heat_<f>       TJ of heat produced from f (8000T);
                    elec_<f>       TJ of electricity from f (7000T);
                    elec_fossil    TJ of electricity from combustible fossil
                                   fuels (7000T: coal, coal products,
                                   manufactured gases, gas, oil, peat,
                                   oil shale);
                    elec_total     TJ of gross electricity production
                                   (7000, transaction 01);
                    gas_nonenergy  TJ of natural gas to non-energy use
                                   (3000, 11; ammonia and methanol
                                   feedstock);
                    coke_prod      kt of coke oven coke produced (0311, 01);
                    coke_bf        kt of coke into blast furnaces (0311, 084);
                    coal_coke      kt of hard coal into coke ovens (0100, 081);
                    coal_bf        kt of hard coal into blast furnaces,
                                   injected (0100, 084);
                    refinery_in    kt into refineries (GR, 086);
                    refinery_out   kt of gross refinery output (GR, O).
  iron.csv        series,year,edition,known,value
                  USGS Minerals Yearbook series for the blast-furnace ore
                  recipe, every edition kept (a later one replaces an
                  earlier one in bnm.recipes once known), t:
                    china_usable  China's usable iron ore, gross weight:
                                  MYB editions from 2014 on (2014-2015
                                  label it usable ore; from 2016 the
                                  country row is usable ore), and the 2024
                                  volume III table (2020-2024);
                    dri_world     world direct-reduced iron, gross weight:
                                  the MYB world total of each edition, and
                                  the 2024 volume III country sum;
                    fe_grade      Fe content over gross weight of world
                                  iron ore less China's (MYB world total
                                  less China's row, its usable ore in the
                                  2014-2015 editions), each edition;
                    ore_apparent  world apparent iron-ore consumption, t
                                  actual weight, China's production
                                  adjusted to the world average Fe content:
                                  worldsteel World Steel in Figures, the
                                  edition's iron-ore page (data year
                                  edition - 2), editions that adjust China
                                  (2012 on; 2016 is not online), known in
                                  the edition's year (bnm-1pq.21).
                  Volume I editions are known edition + 2, the 2024
                  volume III table in 2026.
  nh3.csv         iso,year,edition,known,value
                  USGS ammonia production by country, t of NH3 (USGS
                  reports N content: x 17.031/14.007), every Minerals
                  Yearbook edition kept (known edition + 2) and the 2024
                  volume III table (2020-2024, known 2026). Aggregates,
                  former states and the two German states are left out.
                  USGS, BGS and UNSD carry no methanol series, so none is
                  written (bnm.recipes leaves out countries with large gas
                  methanol instead). Rows the MYB data release
                  gives a country from another country's (France in the
                  2012-2016 editions, Estonia in 2014, Italy 2012 in 2012;
                  MYB_NH3_BAD) are left out, bnm-ia4.
  fao.csv         iso,year,known,nutrient,element,tonnes
                  FAOSTAT Inputs_FertilizersNutrient: N, P2O5 and K2O,
                  agricultural use and production, tonnes of nutrient;
                  known = year + 2; the world as WLD.

    python -I scripts/recipe-obs.py [data-root]

Run with the scratchpad venv (pandas, numpy, pycountry).
"""
import csv
import io
import os
import re
import sys
import zipfile
from importlib import util as _util

import numpy as np
import pandas as pd

ROOT = sys.argv[1] if len(sys.argv) > 1 else "data"
HERE = os.path.dirname(os.path.abspath(__file__))
PXP = os.path.join(ROOT, "raw/exiobase/v3.8.2-pxp")
UNSD = os.path.join(ROOT, "raw/material/unsd-energy/by-commodity")
FAO = os.path.join(ROOT, "raw/material/faostat-fertilizers/Inputs_FertilizersNutrient_E_All_Data_(Normalized).zip")
OUT = os.path.join(ROOT, "derived/supply/recipe-obs")
MYB = os.path.join(ROOT, "raw/material/usgs-myb/world-production-1990-2022/USGS_MYB_V1_Production_Data_Release.csv")
MYB3 = os.path.join(ROOT, "raw/material/usgs-myb/2024-vol3/2024MYBv3_Production_table.csv")
MYB_LAG = 2

_spec = _util.spec_from_file_location("material", os.path.join(HERE, "material.py"))
_material = _util.module_from_spec(_spec)
_spec.loader.exec_module(_material)
code_of_m49 = _material.code_of_m49

YEARS = [1995, 2000, 2005, 2008, 2011, 2015, 2019, 2022]
OUTPUTS = ["C_NFER", "C_PFER", "C_STEL", "C_CMNT", "C_ALUM", "C_COPP", "C_COKE",
           "C_POWC", "C_POWG", "C_POWP", "C_WHEA", "C_OCER", "C_PARI"]
MIN_SHARE = 1e-6
UNSD_LAG = 2
FAO_LAG = 2
# UNSD areas holding a country and its neighbours (as scripts/oil-products.py)
AREAS = {"251": "FRA", "382": "ITA", "579": "NOR", "757": "CHE", "412": "XKX"}

FUELS = {"coal": ["0100", "0200"], "gas": ["3000"], "oil": ["4680", "4670", "4100"]}
ELEC = {"coal": ["01CL", "01LB"], "gas": ["01NG"], "oil": ["01RF", "01DL", "01CR"]}
FOSSIL = ["01CL", "01LB", "01CP", "01MG", "01NG", "01RF", "01DL", "01CR", "01PP", "01PT", "01OS"]


def iso_of(m49):
    m49 = m49.strip().lstrip("'").zfill(3)
    return AREAS.get(m49) or code_of_m49(m49)


def pxp_shares():
    rows = []
    for y in YEARS:
        with zipfile.ZipFile(os.path.join(PXP, f"IOT_{y}_pxp.zip")) as z:
            d = f"IOT_{y}_pxp/"
            pt = pd.read_csv(z.open(d + "products.txt"), sep="\t")
            code = dict(zip(pt["Name"], pt["CodeTxt"]))
            x = pd.read_csv(z.open(d + "x.txt"), sep="\t")
            regs = list(dict.fromkeys(x["region"]))
            prods = list(x["sector"][: len(x) // len(regs)])
            npr = len(prods)
            xv = x["indout"].to_numpy(dtype=float).reshape(len(regs), npr)
            a = pd.read_csv(z.open(d + "A.txt"), sep="\t", skiprows=3, header=None)
            am = a.iloc[:, 2:].to_numpy(dtype=float)
            del a
        codes = [code[p] for p in prods]
        oix = [codes.index(c) for c in OUTPUTS]
        # (origin region, input, dest region, output) summed over origins
        agg = am.reshape(len(regs), npr, len(regs), npr).sum(axis=0)[:, :, oix]  # (input, dest, out)
        del am
        ix = {r: k for k, r in enumerate(regs)}
        for r in regs:
            if r == "TW":
                continue
            m = agg[:, ix[r], :]
            if r == "WA" and "TW" in ix:
                w1 = xv[ix["WA"], oix]
                w2 = xv[ix["TW"], oix]
                tot = w1 + w2
                with np.errstate(invalid="ignore", divide="ignore"):
                    m = np.where(tot > 0, (m * w1 + agg[:, ix["TW"], :] * w2) / tot, 0.0)
            for j, out in enumerate(OUTPUTS):
                for i in np.nonzero(m[:, j] >= MIN_SHARE)[0]:
                    rows.append([y, r, out, codes[i], f"{m[i, j]:.6g}"])
        print(f"pxp {y}: {len(rows)} rows so far", flush=True)
    with open(os.path.join(OUT, "pxp-shares.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["year", "region", "output", "input", "share"])
        w.writerows(rows)


def unsd_rows(commodity):
    with open(os.path.join(UNSD, f"UNSD_ENERGY_{commodity}.csv"), newline="") as fh:
        for d in csv.DictReader(fh):
            iso = iso_of(d["REF_AREA"])
            if not iso or not d["OBS_VALUE"]:
                continue
            v = float(d["OBS_VALUE"])
            f = float(d["CONVERSION_FACTOR"]) if d["CONVERSION_FACTOR"] else 1.0
            yield iso, int(d["TIME_PERIOD"]), d["TRANSACTION"], v, v * f


def unsd():
    acc = {}

    def add(iso, y, s, v):
        acc[(iso, y, s)] = acc.get((iso, y, s), 0.0) + v

    for f, comms in FUELS.items():
        for c in comms:
            for iso, y, t, _, tj in unsd_rows(c):
                if t in ("08811", "08812", "08821", "08822"):
                    add(iso, y, f"fuel_{f}", tj)
                elif t in ("08831", "08832"):
                    add(iso, y, f"heatfuel_{f}", tj)
    for iso, y, t, _, tj in unsd_rows("7000T"):
        for f, ts in ELEC.items():
            if t in ts:
                add(iso, y, f"elec_{f}", tj)
        if t in FOSSIL:
            add(iso, y, "elec_fossil", tj)
    for iso, y, t, _, tj in unsd_rows("8000T"):
        for f, ts in ELEC.items():
            if t in ts:
                add(iso, y, f"heat_{f}", tj)
    for iso, y, t, _, tj in unsd_rows("7000"):
        if t == "01":
            add(iso, y, "elec_total", tj)
    for iso, y, t, _, tj in unsd_rows("3000"):
        if t == "11":
            add(iso, y, "gas_nonenergy", tj)
    for iso, y, t, kt, _ in unsd_rows("0311"):
        if t == "01":
            add(iso, y, "coke_prod", kt)
        elif t == "084":
            add(iso, y, "coke_bf", kt)
    for iso, y, t, kt, _ in unsd_rows("0100"):
        if t == "081":
            add(iso, y, "coal_coke", kt)
        elif t == "084":
            add(iso, y, "coal_bf", kt)
    for iso, y, t, kt, _ in unsd_rows("GR"):
        if t == "086":
            add(iso, y, "refinery_in", kt)
        elif t == "O":
            add(iso, y, "refinery_out", kt)
    with open(os.path.join(OUT, "unsd.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["iso", "year", "known", "series", "value"])
        for (iso, y, s), v in sorted(acc.items()):
            w.writerow([iso, y, y + UNSD_LAG, s, f"{v:.6g}"])


def fao():
    nut = {"3102": "N", "3103": "P2O5", "3104": "K2O"}
    el = {"5157": "use", "5510": "production"}
    rows = []
    with zipfile.ZipFile(FAO) as z:
        name = "Inputs_FertilizersNutrient_E_All_Data_(Normalized).csv"
        with z.open(name) as fh:
            for d in csv.DictReader(io.TextIOWrapper(fh, encoding="latin-1")):
                if d["Item Code"] not in nut or d["Element Code"] not in el or not d["Value"]:
                    continue
                m49 = d["Area Code (M49)"].strip().lstrip("'")
                iso = "WLD" if m49 == "001" else iso_of(m49)
                if not iso:
                    continue
                y = int(d["Year"])
                rows.append([iso, y, y + FAO_LAG, nut[d["Item Code"]], el[d["Element Code"]], d["Value"]])
    with open(os.path.join(OUT, "fao.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["iso", "year", "known", "nutrient", "element", "tonnes"])
        w.writerows(sorted(rows))


def iron():
    rows = {}
    with open(MYB, newline="", encoding="utf-8-sig") as fh:
        for d in csv.DictReader(fh):
            if d["UNIT_DETAIL"] != "Gross weight" or not re.fullmatch(r"-?[0-9.]+", d["QUANTITY"]):
                continue
            m = re.match(r"MYB_(\d{4})_", d["DATA_SOURCE"])
            if not m:
                continue
            ed = int(m.group(1))
            q = float(d["QUANTITY"]) * 1000.0
            ctry = d["COUNTRY_OR_REGION"].split("\n")[0].strip()
            c, form, det = d["COMMODITY"], d["COMMODITY_FORM"], d["FORM_DETAIL"]
            series = None
            if c == "Iron ore" and form == "Mine" and ctry == "China" and q > 0:
                if det == "Usable ore" or (det == "Unspecified" and ed >= 2016):
                    series = "china_usable"
            elif c == "Iron and steel" and form == "Direct-reduced iron" and ctry.startswith("World"):
                series = "dri_world"
            if series:
                rows[(series, int(d["YEAR"]), ed)] = (ed + MYB_LAG, q)
    dri = {}
    with open(MYB3, newline="", encoding="utf-8-sig") as fh:
        for d in csv.DictReader(fh):
            if not d["Value"]:
                continue
            y = int(d["Time Period"])
            if d["Level 2 (Commodity)"] == "Direct-reduced iron":
                dri[y] = dri.get(y, 0.0) + float(d["Value"])
            elif (d["Country (Short Form) or Locality"] == "China" and d["Level 2 (Commodity)"] == "Iron ore"
                  and d["Descriptor"] == "Usable" and d["Commodity Unit"] == "Gross weight"):
                rows[("china_usable", y, 2024)] = (2026, float(d["Value"]))
    for y, q in dri.items():
        rows[("dri_world", y, 2024)] = (2026, q)
    rows.update(fe_grade())
    rows.update(ore_apparent())
    with open(os.path.join(OUT, "iron.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["series", "year", "edition", "known", "value"])
        for (s, y, ed), (k, q) in sorted(rows.items()):
            w.writerow([s, y, ed, k, f"{q:.6g}"])


def fe_grade():
    """{("fe_grade", year, edition): (known, Fe/gross of world ore less China)}."""
    acc = {}
    with open(MYB, newline="", encoding="utf-8-sig") as fh:
        for d in csv.DictReader(fh):
            if d["COMMODITY"] != "Iron ore" or not re.fullmatch(r"-?[0-9.]+", d["QUANTITY"]):
                continue
            m = re.match(r"MYB_(\d{4})_", d["DATA_SOURCE"])
            ctry = d["COUNTRY_OR_REGION"].split("\n")[0].strip()
            if not m or d["UNIT_DETAIL"] not in ("Gross weight", "Iron content"):
                continue
            k = (int(d["YEAR"]), int(m.group(1)))
            a = acc.setdefault(k, {"world": {}, "china": {}, "china_usable": {}})
            q = float(d["QUANTITY"])
            if ctry.startswith("World"):
                a["world"][d["UNIT_DETAIL"]] = a["world"].get(d["UNIT_DETAIL"], 0.0) + q
            elif ctry == "China" and q > 0:
                part = "china_usable" if d["FORM_DETAIL"] == "Usable ore" else "china"
                a[part][d["UNIT_DETAIL"]] = a[part].get(d["UNIT_DETAIL"], 0.0) + q
    out = {}
    for (y, ed), a in acc.items():
        c = a["china_usable"] or a["china"]
        w = a["world"]
        if len(w) == 2 and len(c) == 2:
            g = (w["Iron content"] - c["Iron content"]) / (w["Gross weight"] - c["Gross weight"])
            out[("fe_grade", y, ed)] = (ed + MYB_LAG, g)
    return out


WSIF = os.path.join(ROOT, "raw/material/worldsteel")
_NUM = r"(\d{1,3}(?:[ ,]\d{3})*\.\d)"


def wsif_iron_ore(text):
    """(data year, production, exports, imports, apparent consumption) in Mt
    from a World Steel in Figures text layer (pdftotext -layout), or None
    when the edition does not adjust China's ore to the world average Fe."""
    if "adjusted so that Fe content" not in text:
        return None
    y = re.search(r"Iron ore,\s*(\d{4})", text)
    i = re.search(r"actual weight", text)
    if not y or not i:
        return None
    for line in text[i.start():].split("\n"):
        m = re.match(r"\s*World\s+" + r"\s+".join([_NUM] * 4), line)
        if m:
            p, e, im, a = (float(v.replace(",", "").replace(" ", "")) for v in m.groups())
            if abs(p - e + im - a) > 0.01 * a:
                raise ValueError(f"iron ore page does not add up: {m.groups()}")
            return int(y.group(1)), p, e, im, a
    return None


def ore_apparent():
    import subprocess
    out = {}
    for f in sorted(os.listdir(WSIF)):
        m = re.search(r"(\d{4})", f)
        if "World-Steel-in-Figures" not in f or not f.endswith(".pdf") or not m:
            continue
        ed = int(m.group(1))
        text = subprocess.run(["pdftotext", "-layout", os.path.join(WSIF, f), "-"],
                              capture_output=True, text=True, check=True).stdout
        r = wsif_iron_ore(text)
        if r:
            y, _, _, _, a = r
            out[("ore_apparent", y, ed)] = (ed, a * 1e6)
    return out


NH3_PER_N = 17.031 / 14.007
USGS_NAMES = {"Korea, North": "PRK", "Korea, Republic of": "KOR", "Russia": "RUS", "Iran": "IRN",
              "Syria": "SYR", "Vietnam": "VNM", "Venezuela": "VEN", "Taiwan": "TWN", "Turkey": "TUR",
              "Czech Republic": "CZE", "Czechia": "CZE", "Myanmar (Burma)": "MMR", "Burma": "MMR",
              "Bosnia and Herzegovina": "BIH", "Serbia": "SRB", "Libya": "LBY", "Bolivia": "BOL",
              "Tanzania": "TZA", "Brunei": "BRN", "Laos": "LAO", "Moldova": "MDA"}
NOT_COUNTRIES = ("World", "Soviet Union", "Czechoslovakia", "Yugoslavia", "Serbia and Montenegro",
                 "Germany, ")


def usgs_iso(name):
    import pycountry
    name = name.split("\n")[0].strip()
    if not name or name.startswith(NOT_COUNTRIES):
        return None
    if name in USGS_NAMES:
        return USGS_NAMES[name]
    try:
        return pycountry.countries.lookup(name).alpha_3
    except LookupError:
        return None


# Minerals Yearbook ammonia rows that are another country's (bnm-ia4), left
# out: {(country, edition): reason}; every year of that edition's row goes.
# France's row in the 2012-2016 editions carries 2,600-3,517 kt N for
# 2009-2016 (2011: 3,500, Egypt's figure in the same tables); France made
# about 800 kt N (the 2006-2011 editions), and the 2017 edition revises
# 2013-2014 to 810 and 760. With those editions out, France 2009-2011 is
# the 2011 edition's 800 kt N (972,714 t NH3), 2012 has no figure, and
# 2013 on is the 2017 edition's. Estonia's 2014 edition (3,333 and 3,000 kt
# N for 2012-2014, against 14-99 in every other edition) is the same kind
# of slip, as is Italy's 2012 in the 2012 edition (2,365 against 590).
MYB_NH3_BAD = {("France", 2012): "another country's row", ("France", 2013): "another country's row",
               ("France", 2014): "another country's row", ("France", 2015): "another country's row",
               ("France", 2016): "another country's row", ("Estonia", 2014): "out of line with every other edition"}
MYB_NH3_BAD_YEAR = {("Italy", 2012, 2012): "4x every other edition"}


def nh3_bad(country, year, ed):
    country = country.split("\n")[0].strip()
    return (country, ed) in MYB_NH3_BAD or (country, year, ed) in MYB_NH3_BAD_YEAR


def nh3():
    rows = {}
    with open(MYB, newline="", encoding="utf-8-sig") as fh:
        for d in csv.DictReader(fh):
            if d["COMMODITY"] != "Nitrogen" or d["COMMODITY_FORM"] != "Ammonia":
                continue
            if d["UNITS"] != "Thousand metric tons" or not re.fullmatch(r"[0-9.]+", d["QUANTITY"]):
                continue
            m = re.match(r"MYB_(\d{4})_", d["DATA_SOURCE"])
            iso = usgs_iso(d["COUNTRY_OR_REGION"])
            if not m or not iso:
                continue
            ed = int(m.group(1))
            if nh3_bad(d["COUNTRY_OR_REGION"], int(d["YEAR"]), ed):
                continue
            rows[(iso, int(d["YEAR"]), ed)] = (ed + MYB_LAG, float(d["QUANTITY"]) * 1000.0 * NH3_PER_N)
    with open(MYB3, newline="", encoding="utf-8-sig") as fh:
        for d in csv.DictReader(fh):
            if (d["Level 2 (Commodity)"] != "Nitrogen" or d["Level 5 (Form)"] != "Ammonia"
                    or d["Data Type"] != "Production" or d["Unit"] != "Metric tons" or not d["Value"]):
                continue
            iso = usgs_iso(d["Country (Short Form) or Locality"])
            if iso:
                rows[(iso, int(d["Time Period"]), 2024)] = (2026, float(d["Value"]) * NH3_PER_N)
    with open(os.path.join(OUT, "nh3.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["iso", "year", "edition", "known", "value"])
        for (iso, y, ed), (k, q) in sorted(rows.items()):
            w.writerow([iso, y, ed, k, f"{q:.6g}"])


def main():
    os.makedirs(OUT, exist_ok=True)
    what = sys.argv[2:] or ["unsd", "fao", "iron", "nh3", "pxp"]
    if "unsd" in what:
        unsd()
        print("unsd.csv written", flush=True)
    if "fao" in what:
        fao()
        print("fao.csv written", flush=True)
    if "iron" in what:
        iron()
        print("iron.csv written", flush=True)
    if "nh3" in what:
        nh3()
        print("nh3.csv written", flush=True)
    if "pxp" in what:
        pxp_shares()
        print("pxp-shares.csv written", flush=True)


if __name__ == "__main__":
    main()
