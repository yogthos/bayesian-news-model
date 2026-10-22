"""The input-output spine from the Long-run WIOD (CC BY 4.0; Woltjer,
Gouma & Timmer 2021), research tooling for docs/STOCK-FLOW.md.

Reads data/raw/io/lrwiod/{lr_wiod_niot_final.csv, lr_wiod_sea_final.xlsx}
and data/raw/slow/epwt/EPWT_7.0_FV.xlsx, writes data/derived/io/:
  lrwiod-sectors.csv  country,year,sector,go,va,ii,cons_h,cons_g,gfcf,inv,x,m,va_label,backcast
      each sector's gross output, value added, intermediate inputs and
      final uses (national IO tables, millions of current US$), the
      label of its value added cell (niot, National Accounts, wiod_2013,
      or [extrapolated]/[interpolated]/[estimated ...]: backcast cells,
      built later from later benchmarks, so not as known at the time),
      and backcast, the share of the country-year's 23x23 flow cells
      labelled [interpolated]/[extrapolated]/[estimated ...];
  lrwiod-flows.csv    country,year,from,to,value
      the intermediate flows between the 23 sectors. The national tables
      are in the competitive-imports format: each product row is total
      use, domestic and imported together (imports enter as the negative
      final-use column xM), and a column's flows sum to its ii exactly;
  lrwiod-prices.csv   country,year,sector,go,go_pyp,fx,price_change,basis
      gross output in current US$ and in the previous year's US$ (the
      socio-economic accounts' GO and GO_PYP), fx the change in local
      currency per US$ on the year before, and price_change the gross
      output price on the year before. The previous-year-price tables
      convert at the previous year's exchange rate, so GO/GO_PYP is in
      US$ and carries the exchange rate's move; price_change takes it out
      (basis lcu) where fx is known, and stays in US$ (basis usd) where it
      is not (1965, the first year). fx is implied: EPWT 7.0's GDP in
      current national currency over LR-WIOD's GDP in current US$; its
      level is off by the two sources' vintage gap, its year-on-year change
      is the exchange rate's (Germany 1986 0.738 against 0.737 from the
      average DM/US$ rates). Merged sectors take sum(go)/sum(go_pyp).

Run with python -I scripts/io-spine.py (openpyxl).
"""
import csv
import collections
import os
import sys

import openpyxl

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "data/raw/io/lrwiod")
EPWT = os.path.join(ROOT, "data/raw/slow/epwt/EPWT_7.0_FV.xlsx")
OUT = os.path.join(ROOT, "data/derived/io")
SECTORS = ["AtB", "C", "D15t16", "D17t19", "D21t22", "D23", "D24", "D25", "D26", "D27t28", "D29",
           "D30t33", "D34t35", "Dnec", "E", "F", "G", "H", "I60t63", "I64", "J", "K", "LtQ"]
SEC = set(SECTORS)
FINAL = {"xCONS_h": "cons_h", "xCONS_g": "cons_g", "xGFCF": "gfcf", "xINV": "inv", "xX": "x", "xM": "m"}


def national():
    acc = collections.defaultdict(dict)
    flows = []
    cells = collections.Counter()
    backcast = collections.Counter()
    with open(os.path.join(RAW, "lr_wiod_niot_final.csv"), newline="") as f:
        for d in csv.DictReader(f):
            key = (d["countrycode"], d["year"])
            r, c, v = d["row_isic3"], d["col_isic3"], float(d["value"])
            if r in SEC and c in SEC:
                flows.append((d["countrycode"], d["year"], r, c, v))
                cells[key] += 1
                if d["label"].startswith("["):
                    backcast[key] += 1
            elif c in SEC and r in ("xVA", "xII", "xGO"):
                acc[key + (c,)][{"xVA": "va", "xII": "ii", "xGO": "go"}[r]] = v
                if r == "xVA":
                    acc[key + (c,)]["va_label"] = d["label"]
            elif r in SEC and c in FINAL:
                acc[key + (r,)][FINAL[c]] = v
    share = {k: backcast[k] / n for k, n in cells.items()}
    return acc, flows, share


def sea():
    """{(country, year, sector): {GO, GO_PYP}} and {(country, year): GDP}, US$."""
    wb = openpyxl.load_workbook(os.path.join(RAW, "lr_wiod_sea_final.xlsx"), read_only=True)
    rows = wb["data"].iter_rows(values_only=True)
    head = next(rows)
    years = {i: h[1:] for i, h in enumerate(head) if isinstance(h, str) and h.startswith("y")}
    go = collections.defaultdict(dict)
    gdp = {}
    for r in rows:
        country, var, code = r[0], r[1], r[2]
        if var in ("GO", "GO_PYP") and code in SEC:
            for i, y in years.items():
                if r[i] is not None:
                    go[(country, y, code)][var] = float(r[i])
        elif var == "EXP" and code == "xGDPe":
            for i, y in years.items():
                if r[i]:
                    gdp[(country, y)] = float(r[i])
    return go, gdp


def fx_change(gdp_usd):
    """{(country, year): local currency per US$ over the year before},
    implied by EPWT's GDP in current national currency over LR-WIOD's in
    current US$ (both annual, so the rate is an annual average)."""
    wb = openpyxl.load_workbook(EPWT, read_only=True)
    rows = wb["EPWT7.0"].iter_rows(values_only=True)
    head = list(next(rows))
    ci, yi, gi = head.index("Countrycode"), head.index("Year"), head.index("XGDPnatcur")
    rate = {}
    for r in rows:
        k = (r[ci], str(r[yi]))
        if r[gi] and k in gdp_usd:
            rate[k] = float(r[gi]) / gdp_usd[k]
    # the US is US$ by definition (the implied rate wanders 0.1% with vintages)
    return {(c, y): 1.0 if c == "USA" else x / rate[(c, str(int(y) - 1))] for (c, y), x in rate.items()
            if (c, str(int(y) - 1)) in rate}


def main():
    os.makedirs(OUT, exist_ok=True)
    acc, flows, share = national()
    cols = ["go", "va", "ii", "cons_h", "cons_g", "gfcf", "inv", "x", "m"]
    with open(os.path.join(OUT, "lrwiod-sectors.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector"] + cols + ["va_label", "backcast"])
        for (c, y, s), d in sorted(acc.items()):
            w.writerow([c, y, s] + [d.get(k, "") for k in cols] + [d.get("va_label", ""), share.get((c, y), "")])
    with open(os.path.join(OUT, "lrwiod-flows.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "from", "to", "value"])
        w.writerows(sorted(flows))
    go, gdp = sea()
    fx = fx_change(gdp)
    n = 0
    with open(os.path.join(OUT, "lrwiod-prices.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector", "go", "go_pyp", "fx", "price_change", "basis"])
        for (c, y, s), d in sorted(go.items()):
            if d.get("GO_PYP", 0) > 0 and "GO" in d:
                p = d["GO"] / d["GO_PYP"]
                x = fx.get((c, y))
                w.writerow([c, y, s, d["GO"], d["GO_PYP"], "" if x is None else x,
                            p if x is None else p * x, "usd" if x is None else "lcu"])
                n += 1
    print("sectors", len(acc), "flows", len(flows), "prices", n, file=sys.stderr)


if __name__ == "__main__":
    main()
