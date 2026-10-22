"""The material base by country and year (docs/STOCK-FLOW.md, beads
bnm-62z.19-.21): where resources are, where they are processed, and how
they move. Research tooling over data/raw/material/ (each source's terms
in docs/STOCK-FLOW.md), writing data/derived/material/:

  endowments.csv  iso,year,variable,value,unit
      oil_reserves (tonnes), gas_reserves (cubic metres) (1980-),
      coal_reserves (tonnes, 2008-): proved reserves (Energy Institute and
      EIA via Our World in Data, CC BY);
      arable_land, cropland, irrigated: FAOSTAT land use (1961-, CC BY);
  capacity.csv    iso,year,variable,value,unit
      power_<fuel>: installed electrical capacity by fuel (EIA, 1980-,
      public domain); plants_<fuel>: the WRI Global Power Plant Database's
      plants (CC BY) summed by commissioning year, a lower bound (plants
      retired before 2021 are missing);
  production.csv  iso,year,variable,value,unit
      oil, gas, coal production (Our World in Data's energy data, from
      the Energy Institute; TWh);
  cereal-trade.csv  reporter,partner,year,item,import_tonnes
      wheat, maize and rice imports by partner (FAOSTAT detailed trade
      matrix, 1986-): who depends on whom for food.

Every value is as published now (revised, not as known at the time):
readers lag them, a year at least. Run with the scratchpad venv's
python -I (pycountry for the UN M49 codes).
"""
import csv
import json
import os
import sys

import pycountry

RAW = "data/raw/material"
OUT = "data/derived/material"


def iso_of_m49(code):
    code = code.strip().lstrip("'")
    c = pycountry.countries.get(numeric=code.zfill(3)) if code.isdigit() else None
    return c.alpha_3 if c else None


def reserves():
    rows = []
    for name, var, unit in (("oil-proved-reserves", "oil_reserves", "tonnes"),
                            ("natural-gas-proved-reserves", "gas_reserves", "cubic metres"),
                            ("coal-proved-reserves", "coal_reserves", "tonnes")):
        with open(os.path.join(RAW, "owid-grapher", name + ".csv"), newline="") as f:
            r = csv.reader(f)
            next(r)
            for ent, code, year, v in r:
                if len(code) == 3 and v != "":
                    rows.append((code, int(year), var, float(v), unit))
    return rows


def land():
    want = {"Arable land": "arable_land", "Cropland": "cropland", "Land area equipped for irrigation": "irrigated"}
    rows = []
    with open(os.path.join(RAW, "faostat/x", "Inputs_LandUse_E_All_Data_(Normalized).csv"), newline="", encoding="latin-1") as f:
        for d in csv.DictReader(f):
            if d["Item"] in want and d["Element"] == "Area" and d["Value"]:
                iso = iso_of_m49(d["Area Code (M49)"])
                if iso:
                    rows.append((iso, int(d["Year"]), want[d["Item"]], float(d["Value"]), d["Unit"]))
    return rows


def power_capacity():
    fuels = {"Electricity installed capacity": "power_total", "Fossil fuels electricity installed capacity": "power_fossil",
             "Nuclear electricity installed capacity": "power_nuclear", "Hydroelectricity installed capacity": "power_hydro",
             "Renewable electricity installed capacity": "power_renewable"}
    rows = []
    with open(os.path.join(RAW, "eia/x", "INTL.txt")) as f:
        for line in f:
            d = json.loads(line)
            if d.get("f") != "A" or d.get("units") != "million kilowatts":
                continue
            base = d.get("name", "").split(",")[0]
            iso = d.get("geography")
            if base in fuels and iso and len(iso) == 3:
                for y, v in d.get("data", []):
                    if isinstance(v, (int, float)):
                        rows.append((iso, int(y), fuels[base], 1000.0 * v, "MW"))
    return rows


def plants():
    acc = {}
    with open(os.path.join(RAW, "gppd/x", "global_power_plant_database.csv"), newline="", encoding="utf-8") as f:
        for d in csv.DictReader(f):
            if d["commissioning_year"]:
                k = (d["country"], int(float(d["commissioning_year"])), "plants_" + d["primary_fuel"].lower())
                acc[k] = acc.get(k, 0.0) + float(d["capacity_mw"] or 0)
    # cumulative: the capacity commissioned by each year that still stood in 2021
    rows, run = [], {}
    for (iso, y, var) in sorted(acc):
        run[(iso, var)] = run.get((iso, var), 0.0) + acc[(iso, y, var)]
        rows.append((iso, y, var, run[(iso, var)], "MW"))
    return rows


def production():
    want = {"oil_production": "oil", "gas_production": "gas", "coal_production": "coal"}
    rows = []
    with open(os.path.join(RAW, "owid-energy", "owid-energy-data.csv"), newline="") as f:
        for d in csv.DictReader(f):
            iso = d.get("iso_code", "")
            if len(iso) == 3:
                for col, var in want.items():
                    if d.get(col):
                        rows.append((iso, int(d["year"]), var, float(d[col]), "TWh"))
    return rows


def cereal_trade():
    items = {"Wheat": "wheat", "Maize (corn)": "maize", "Rice": "rice"}
    acc = {}
    with open(os.path.join(RAW, "faostat/x", "Trade_DetailedTradeMatrix_E_All_Data_(Normalized).csv"), newline="", encoding="latin-1") as f:
        for d in csv.DictReader(f):
            if d["Item"] in items and d["Element"] == "Import quantity" and d["Value"]:
                rep = iso_of_m49(d["Reporter Country Code (M49)"])
                par = iso_of_m49(d["Partner Country Code (M49)"])
                if rep and par:
                    k = (rep, par, int(d["Year"]), items[d["Item"]])
                    acc[k] = acc.get(k, 0.0) + float(d["Value"])
    return [k + (v,) for k, v in sorted(acc.items())]


def write(name, header, rows):
    """Rows by their keys (all but the value and unit), each key once: a
    series published twice (EIA's) is written once."""
    seen, out = set(), []
    for row in sorted(rows):
        k = row[:3] if len(row) == 5 and isinstance(row[3], float) else row[:-1]
        if k not in seen:
            seen.add(k)
            out.append(row)
    rows = out
    with open(os.path.join(OUT, name), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)
    print(name, len(rows), file=sys.stderr)


def main():
    os.makedirs(OUT, exist_ok=True)
    write("endowments.csv", ["iso", "year", "variable", "value", "unit"], reserves() + land())
    write("capacity.csv", ["iso", "year", "variable", "value", "unit"], power_capacity() + plants())
    write("production.csv", ["iso", "year", "variable", "value", "unit"], production())
    write("cereal-trade.csv", ["reporter", "partner", "year", "item", "import_tonnes"], cereal_trade())


if __name__ == "__main__":
    main()
