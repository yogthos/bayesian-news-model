"""Oil products by country and year from the UN energy statistics (docs/
STOCK-FLOW.md, "Product graph"; epic bnm-1pq): the time dimension the
product graph's single hybrid year (EXIOBASE 2011) lacks.

Reads data/raw/material/unsd-energy/by-commodity/UNSD_ENERGY_<code>.csv
(UNSD Energy Statistics Database, 1990-2023, 2024 partial; UNdata terms,
attribution "UN Statistics Division"), one file per commodity: crude oil,
NGL, refinery feedstocks, additives, other hydrocarbons and every refined
product. Writes data/derived/supply/oil-products.csv:

  iso,year,known,product,exiobase,flow,flow_name,value,unit,status
    iso      ISO alpha-3 from M49 (historical entities as scripts/
             material.py's: SUN, YUG, CSK, SCG, SDF, ...; UNSD's France
             incl. Monaco as FRA, Italy and San Marino ITA, Norway incl.
             Svalbard NOR, Switzerland incl. Liechtenstein CHE, Kosovo
             XKX); dropped: Antarctic fisheries, the Pacific Islands trust
             territory (1990-91) and the two Yemens (1990)
    known    year + 2: a year's figures are treated as known two years
             later (UNSD publishes a year's balances about 18-24 months
             after it ends, and revises them); read a value only from that
             year on
    product  the UNSD commodity code; exiobase the matching product code
             of the product graph (data/derived/supply/products.csv)
    flow     the UNSD transaction code, flow_name its label: 01
             production, 03 imports, 04 exports, 051/052 bunkers, 06
             stock changes, 08x transformation (086 oil refineries, 085PP
             petrochemical plants, 088 power), 09x own use, 101 losses,
             NA final consumption, 11 non-energy use, 12x final energy use
             by sector (1213 chemicals, 1221 road, 1223 domestic aviation,
             1231 households, 1232 agriculture...), 131 refinery capacity
             (crude oil, thousand tonnes a year), GA total energy supply
    value    in thousand tonnes (kt) as UNSD gives it (OBS_VALUE, unit
             multiplier 3, metric tons); status: UNSD's (A reported, E
             estimated)

    python -I scripts/oil-products.py [data-root]
"""
import csv
import os
import sys
from importlib import util as _util

ROOT = sys.argv[1] if len(sys.argv) > 1 else "data"
SRC = os.path.join(ROOT, "raw/material/unsd-energy")
OUT = os.path.join(ROOT, "derived/supply")
HERE = os.path.dirname(os.path.abspath(__file__))

_spec = _util.spec_from_file_location("material", os.path.join(HERE, "material.py"))
_material = _util.module_from_spec(_spec)
_spec.loader.exec_module(_material)
code_of_m49 = _material.code_of_m49

PRODUCTS = {
    "4100": "C_COIL", "4200": "C_GASL", "4300": "C_REFF", "4400": "C_ADDC", "4500": "C_OGPL",
    "4610": "C_RGAS", "4620": "C_ETHA", "4630": "C_LPGA", "4640": "C_NAPT", "4651": "C_AGSL",
    "4652": "C_MGSL", "4653": "C_GJET", "4661": "C_KJET", "4669": "C_KERO", "4670": "C_DOIL",
    "4680": "C_FOIL", "4691": "C_WHSP", "4692": "C_LUBR", "4693": "C_PARW", "4694": "C_PETC",
    "4695": "C_BITU", "4699": "C_NSPP",
}
KNOWN_LAG = 2
# UNSD's areas that hold a country and its neighbours: the country's code
AREAS = {"251": "FRA",  # France incl. Monaco
         "382": "ITA",  # Italy and San Marino
         "579": "NOR",  # Norway incl. Svalbard
         "757": "CHE",  # Switzerland incl. Liechtenstein
         "412": "XKX"}  # Kosovo


def labels():
    """{code label} of the transaction code list."""
    out = {}
    section = None
    with open(os.path.join(SRC, "codelists_commodity_transaction.txt"), encoding="utf-8") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if line.startswith("## "):
                section = line[3:]
                continue
            if section == "CL_TRANSACTION_NRG" and " " in line and not line.startswith("CL_"):
                code, label = line.split(" ", 1)
                out[code] = label
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    names = labels()
    n = 0
    dropped = set()
    units = set()
    with open(os.path.join(OUT, "oil-products.csv"), "w", newline="") as out:
        w = csv.writer(out)
        w.writerow(["iso", "year", "known", "product", "exiobase", "flow", "flow_name", "value", "unit", "status"])
        for code, exio in PRODUCTS.items():
            with open(os.path.join(SRC, "by-commodity", f"UNSD_ENERGY_{code}.csv"), newline="", encoding="utf-8") as fh:
                for d in csv.DictReader(fh):
                    iso = AREAS.get(d["REF_AREA"]) or code_of_m49(d["REF_AREA"])
                    if not iso:
                        dropped.add(d["REF_AREA"])
                        continue
                    if (d["UNIT_MULT"], d["UNIT_MEASURE"]) != ("3", "TN"):
                        raise SystemExit(f"{code}: unexpected unit {d['UNIT_MULT']} {d['UNIT_MEASURE']}")
                    try:
                        v = float(d["OBS_VALUE"])
                    except ValueError:
                        continue
                    y = int(d["TIME_PERIOD"])
                    t = d["TRANSACTION"]
                    w.writerow([iso, y, y + KNOWN_LAG, code, exio, t, names.get(t, ""), f"{v:g}", "kt",
                                d["OBS_STATUS"]])
                    n += 1
    print(f"oil-products.csv: {n} rows; areas without an ISO code dropped: {sorted(dropped)}")


if __name__ == "__main__":
    main()
