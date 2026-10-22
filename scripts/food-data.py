"""FAOSTAT Food Balance Sheets, cereals only, for bnm.food (bead bnm-62z.7).

Reads the two bulk zips in data/raw/material/faostat-fbs (FAO. FAOSTAT,
CC BY 4.0):
  FoodBalanceSheetsHistoric_E_All_Data_(Normalized).zip  1961-2013 (old method)
  FoodBalanceSheets_E_All_Data_(Normalized).zip          2010-     (new method)
and writes data/raw/material/faostat-fbs/fbs-cereals.csv

  iso,year,item,element,value,flag,method

item: wheat (2511 Wheat and products), maize (2514 Maize and products),
rice (2805 Rice (Milled Equivalent), historic; 2807 Rice and products,
new, which is in paddy equivalent and is converted to milled at FAO's
0.667, the ratio of the two methods' production in 2010-2013 to three
decimals). "Cereals - Excluding Beer" is not taken: in the new method it
holds rice as paddy, so it breaks in 2010; bnm.food sums the three.
element: production, imports, exports, stock_variation, supply (domestic
supply quantity), food, feed; values in tonnes (FAO publishes 1000 t).
Where the two methods overlap (2010-2013) the new one is kept. The
methods' Stock Variation signs differ (historic: supply = production +
imports - exports + variation; new: ... - variation), so bnm.food takes
the stock change from the balance, not from this element. The world
(area 5000) is written as iso WLD, for checks only.

Country codes as scripts/material.py's (ISO alpha-3 from M49, historical
entities their own codes). Run with the scratchpad venv's python -I.
"""
import csv
import io
import os
import sys
import zipfile

from importlib import util as _util

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "data/raw/material/faostat-fbs")

_spec = _util.spec_from_file_location("material", os.path.join(ROOT, "scripts", "material.py"))
_material = _util.module_from_spec(_spec)
_spec.loader.exec_module(_material)
code_of_m49 = _material.code_of_m49

ITEMS = {"2511": "wheat", "2514": "maize", "2805": "rice", "2807": "rice"}
RICE_PADDY_TO_MILLED = 0.667  # new method (2807) only
ELEMENTS = {"Production": "production", "Import Quantity": "imports", "Import quantity": "imports",
            "Export Quantity": "exports", "Export quantity": "exports", "Stock Variation": "stock_variation",
            "Domestic supply quantity": "supply", "Food": "food", "Feed": "feed"}
FILES = [("historic", "fbsh.zip", "FoodBalanceSheetsHistoric_E_All_Data_(Normalized).csv"),
         ("new", "fbs.zip", "FoodBalanceSheets_E_All_Data_(Normalized).csv")]


def rows(method, zname, member):
    with zipfile.ZipFile(os.path.join(DIR, zname)) as z, z.open(member) as raw:
        f = io.TextIOWrapper(raw, encoding="latin-1", newline="")
        r = csv.reader(f)
        cols = next(r)
        i = {c: n for n, c in enumerate(cols)}
        for d in r:
            item = ITEMS.get(d[i["Item Code"]])
            el = ELEMENTS.get(d[i["Element"]])
            if not item or not el or d[i["Value"]] == "":
                continue
            unit = d[i["Unit"]]
            if unit not in ("1000 t", "t"):
                continue
            area = d[i["Area Code"]]
            iso = "WLD" if area == "5000" else code_of_m49(d[i["Area Code (M49)"]])
            if not iso:
                continue
            v = float(d[i["Value"]]) * (1000.0 if unit == "1000 t" else 1.0)
            if d[i["Item Code"]] == "2807":
                v *= RICE_PADDY_TO_MILLED
            yield (iso, int(d[i["Year"]]), item, el, v, d[i["Flag"]], method)


def main():
    out = {}
    for method, zname, member in FILES:
        for row in rows(method, zname, member):
            k = row[:4]
            if method == "new" or k not in out:
                out[k] = row
    path = os.path.join(DIR, "fbs-cereals.csv")
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["iso", "year", "item", "element", "value", "flag", "method"])
        for k in sorted(out):
            w.writerow(out[k])
    print(f"{len(out)} rows -> {path}")


if __name__ == "__main__":
    main()
