"""The general government's balance, expenditure and revenue, % of GDP, as
declared series (gap 2, docs/CALIBRATION.md t120): the Global Macro
Database's annual columns in data/derived/fiscal.csv, the ones the fiscal
consolidation catalogue is built on (bnm.economic-events). A year's values
are taken as known on 30 September of the next year, as the slow table
dates the IMF's government debt (debt/government_gdp); the GMD is one
vintage, so earlier years carry later revisions, as WDI's do.

  python3 -I scripts/fiscal-series.py data/derived/fiscal.csv > data/derived/fiscal-series.tsv
"""
import csv
import math
import sys

NAMES = {"gov_bal_gdp": "fiscal/balance_gdp", "gov_exp_gdp": "fiscal/expenditure_gdp", "gov_rev_gdp": "fiscal/revenue_gdp"}


def series(f):
    out = []
    for row in csv.DictReader(f):
        name = NAMES.get(row["var"])
        if not name:
            continue
        try:
            x = float(row["value"])
        except ValueError:
            continue
        if math.isnan(x):
            continue
        y = int(row["year"])
        out.append([row["iso"], name, "a", f"{y}-12-31", f"{y + 1}-09-30", repr(x)])
    out.sort()
    return out


def main():
    with open(sys.argv[1], encoding="utf-8") as f:
        rows = series(f)
    w = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
    for r in rows:
        w.writerow(r)


if __name__ == "__main__":
    main()
