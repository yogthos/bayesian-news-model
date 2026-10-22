"""The input-output spine from the Long-run WIOD (CC BY 4.0; Woltjer,
Gouma & Timmer 2021), research tooling for docs/STOCK-FLOW.md.

Reads data/raw/io/lrwiod/ and writes data/derived/io/:
  lrwiod-sectors.csv  country,year,sector,go,va,ii,cons_h,cons_g,gfcf,inv,x,m,va_label
      each sector's gross output, value added, intermediate inputs and
      final uses (national IO tables, millions of current US$), and the
      label of its value added cell (niot, National Accounts, wiod_2013,
      or [extrapolated]/[interpolated]/[estimated ...]: backcast cells,
      built later from later benchmarks, so not as known at the time);
  lrwiod-flows.csv    country,year,from,to,value
      the domestic intermediate flows between the 23 sectors;
  lrwiod-prices.csv   country,year,sector,price_change
      each sector's gross output in current prices over the same in the
      previous year's prices (the inter-country tables), the year's
      price change, from which relative prices and elasticities follow.

Run with python3 -I scripts/io-spine.py (stdlib only).
"""
import csv
import collections
import os
import sys

RAW = "data/raw/io/lrwiod"
OUT = "data/derived/io"
SECTORS = ["AtB", "C", "D15t16", "D17t19", "D21t22", "D23", "D24", "D25", "D26", "D27t28", "D29",
           "D30t33", "D34t35", "Dnec", "E", "F", "G", "H", "I60t63", "I64", "J", "K", "LtQ"]
SEC = set(SECTORS)
FINAL = {"xCONS_h": "cons_h", "xCONS_g": "cons_g", "xGFCF": "gfcf", "xINV": "inv", "xX": "x", "xM": "m"}


def national():
    acc = collections.defaultdict(dict)
    flows = []
    with open(os.path.join(RAW, "lr_wiod_niot_final.csv"), newline="") as f:
        for d in csv.DictReader(f):
            key = (d["countrycode"], d["year"])
            r, c, v = d["row_isic3"], d["col_isic3"], float(d["value"])
            if r in SEC and c in SEC:
                flows.append((d["countrycode"], d["year"], r, c, v))
            elif c in SEC and r in ("xVA", "xII", "xGO"):
                acc[key + (c,)][{"xVA": "va", "xII": "ii", "xGO": "go"}[r]] = v
                if r == "xVA":
                    acc[key + (c,)]["va_label"] = d["label"]
            elif r in SEC and c in FINAL:
                acc[key + (r,)][FINAL[c]] = v
    return acc, flows


def gross_output(path):
    """{(country, year, sector): row sum} of an inter-country table."""
    go = collections.defaultdict(float)
    with open(path, newline="") as f:
        for d in csv.DictReader(f):
            if d["row_isic3"] in SEC:
                go[(d["row_country"], d["year"], d["row_isic3"])] += float(d["value"])
    return go


def main():
    os.makedirs(OUT, exist_ok=True)
    acc, flows = national()
    cols = ["go", "va", "ii", "cons_h", "cons_g", "gfcf", "inv", "x", "m"]
    with open(os.path.join(OUT, "lrwiod-sectors.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector"] + cols + ["va_label"])
        for (c, y, s), d in sorted(acc.items()):
            w.writerow([c, y, s] + [d.get(k, "") for k in cols] + [d.get("va_label", "")])
    with open(os.path.join(OUT, "lrwiod-flows.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "from", "to", "value"])
        w.writerows(sorted(flows))
    cur = gross_output(os.path.join(RAW, "lr_wiod_wiot_final_filled.csv"))
    pyp = gross_output(os.path.join(RAW, "lr_wiod_wiot_pyp_final.csv"))
    with open(os.path.join(OUT, "lrwiod-prices.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector", "price_change"])
        for k in sorted(pyp):
            if pyp[k] > 0 and k in cur:
                w.writerow(list(k) + [cur[k] / pyp[k]])
    print("sectors", len(acc), "flows", len(flows), "prices", len(pyp), file=sys.stderr)


if __name__ == "__main__":
    main()
