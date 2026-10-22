"""World Bank commodity prices (the Pink Sheet) for bnm.cycles.

Reads data/raw/market/pinksheet/CMO-Historical-Data-Monthly.xlsx (World
Bank, Commodity Markets, "CMO Historical Data Monthly"; data catalog
dataset 0037798, Global Commodity Prices, Creative Commons Attribution
4.0) and writes data/raw/market/pinksheet/pinksheet-monthly.csv

  month,<commodity>,...

month as YYYY-MM, prices in nominal US dollars in the sheet's units
(crude $/bbl, coal $/mt, gas $/mmbtu, metals $/mt, iron ore $/dmtu, grains
$/mt, sugar $/kg), one column per commodity bnm.cycles-run reads; a month
the sheet leaves blank ("…", "..") is empty. The sheet is the current
vintage: its history is rarely revised, but it is one download, not the
prices as first published.

  venv/bin/python -I scripts/pinksheet.py [xlsx] [out.csv]

The xlsx is untrusted data: run with -I, from outside its directory.
"""
import csv
import re
import sys

import openpyxl

XLSX = "data/raw/market/pinksheet/CMO-Historical-Data-Monthly.xlsx"
OUT = "data/raw/market/pinksheet/pinksheet-monthly.csv"

# commodity -> the Monthly Prices sheet's column header (stripped)
COLUMNS = {
    "oil-brent": "Crude oil, Brent",
    "oil-dubai": "Crude oil, Dubai",
    "oil-wti": "Crude oil, WTI",
    "coal": "Coal, Australian",
    "gas-us": "Natural gas, US",
    "gas-europe": "Natural gas, Europe",
    "gas-asia": "Liquefied natural gas, Japan",
    "aluminium": "Aluminum",
    "copper": "Copper",
    "iron-ore": "Iron ore, cfr spot",
    "nickel": "Nickel",
    "wheat": "Wheat, US HRW",
    "maize": "Maize",
    "rice": "Rice, Thai 5%",
    "soybeans": "Soybeans",
    "sugar": "Sugar, world",
}


def number(x):
    if isinstance(x, (int, float)) and not isinstance(x, bool):
        return float(x) if x > 0 else None
    return None


def main(xlsx=XLSX, out=OUT):
    wb = openpyxl.load_workbook(xlsx, read_only=True, data_only=True)
    rows = list(wb["Monthly Prices"].iter_rows(values_only=True))
    hi = next(i for i, r in enumerate(rows) if r and r[1] == "Crude oil, average")
    header = [str(c).strip() if c is not None else "" for c in rows[hi]]
    col = {}
    for k, name in COLUMNS.items():
        if name not in header:
            sys.exit(f"pinksheet: no column {name!r}")
        col[k] = header.index(name)
    updated = next((r[0] for r in rows[:hi] if r and isinstance(r[0], str) and r[0].startswith("Updated")), "?")
    data = []
    for r in rows[hi + 2:]:
        m = re.fullmatch(r"(\d{4})M(\d{2})", str(r[0] or "").strip())
        if not m:
            continue
        data.append([f"{m.group(1)}-{m.group(2)}"] + [number(r[col[k]]) for k in COLUMNS])
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["month"] + list(COLUMNS))
        for d in data:
            w.writerow([d[0]] + ["" if x is None else repr(x) for x in d[1:]])
    print(f"{updated}; {len(data)} months {data[0][0]}..{data[-1][0]} -> {out}")
    for i, k in enumerate(COLUMNS, 1):
        ms = [d[0] for d in data if d[i] is not None]
        print(f"  {k:11s} {ms[0]}..{ms[-1]} ({len(ms)} months)" if ms else f"  {k:11s} none")


if __name__ == "__main__":
    main(*sys.argv[1:3])
