"""FAOSTAT Food Balance Sheets, cereals only, for bnm.food (bead bnm-62z.7).

Reads the two bulk zips in data/raw/material/faostat-fbs (FAO. FAOSTAT,
CC BY 4.0):
  FoodBalanceSheetsHistoric_E_All_Data_(Normalized).zip  1961-2013 (old method)
  FoodBalanceSheets_E_All_Data_(Normalized).zip          2010-     (new method)
and writes data/raw/material/faostat-fbs/fbs-cereals.csv

  iso,year,item,element,value,flag,method

item: wheat (2511 Wheat and products), maize (2514 Maize and products),
rice (2805 Rice (Milled Equivalent), historic; 2807 Rice and products,
new, which is in paddy equivalent). The new method's rice is converted to
the historic's milled equivalent element by element, at the ratio of the
two methods' world totals over their overlap (2010-2013): production and
supply about 0.67, imports about 0.58 (one factor for all would put new-
method rice imports ~15% above the historic's); the factors are printed.
The stock change is written from the stock variation itself (each
method's, at the supply factor for new-method rice), so the per-element
factors do not open a gap in the balance. "Cereals - Excluding Beer" is
not taken: in the new method it holds rice as paddy, so it breaks in 2010;
bnm.food sums the three.
element: production, imports, exports, stock_variation (as published),
stock_change (the stock variation as a build: historic supply = production
+ imports - exports + variation, so its negative; new supply = ... -
variation, so itself), supply (domestic supply quantity), food, feed;
values in tonnes (FAO publishes 1000 t). Where the two methods overlap
(2010-2013) the new one is kept (method says which); bnm.food flags the
break and restarts its detrended stock there. The world (area 5000) is
written as iso WLD: FAO's world totals, which bnm.food reads for the world
(they cover countries without a published balance sheet).

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
OVERLAP = range(2010, 2014)
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
            yield (iso, int(d[i["Year"]]), item, el, v, d[i["Flag"]], method, d[i["Item Code"]])


def rice_factors(raw):
    """{element: historic 2805 / new 2807} over the overlap's world totals;
    the stock variation at the supply factor."""
    tot = {}
    for iso, year, item, el, v, flag, method, code in raw:
        if iso == "WLD" and code in ("2805", "2807") and year in OVERLAP:
            tot[(code, el)] = tot.get((code, el), 0.0) + v
    f = {el: tot[("2805", el)] / tot[("2807", el)]
         for (code, el) in tot if code == "2807" and tot.get(("2805", el)) and tot[("2807", el)]}
    f["stock_variation"] = f["supply"]
    return f


def main():
    raw = [r for method, zname, member in FILES for r in rows(method, zname, member)]
    f = rice_factors(raw)
    print("new-method rice to milled, by element:", {k: round(v, 3) for k, v in sorted(f.items())})
    out = {}
    for iso, year, item, el, v, flag, method, code in raw:
        if code == "2807":
            v *= f.get(el, f["supply"])
        rs = [(iso, year, item, el, v, flag, method)]
        if el == "stock_variation":
            rs.append((iso, year, item, "stock_change", -v if method == "historic" else v, flag, method))
        for row in rs:
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
