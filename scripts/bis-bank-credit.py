"""The BIS's bank credit to the private non-financial sector, % of GDP
(WS_TC: TC_BORROWERS P, TC_LENDERS B, UNIT_TYPE 770, adjusted for breaks),
each year's fourth quarter, for bnm.lattice-banking's --wide-credit.

Bank credit is the concept of JST's tloans, on which the banking node's
credit coefficients were learned; the all-lender series
(finance/credit_gdp) adds non-bank and cross-border corporate debt, which
at a financial centre (Luxembourg: 450% of GDP, bank credit 100%) is
most of it.

    python3 -I scripts/bis-bank-credit.py
    -> data/derived/bis-bank-credit.csv  iso,year,value
"""
import csv
import io
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    iso2 = {}
    for line in open(os.path.join(ROOT, "data", "ref", "countryInfo.txt"), encoding="utf-8"):
        if line.startswith("#"):
            continue
        f = line.rstrip("\n").split("\t")
        iso2[f[0]] = f[1]
    z = zipfile.ZipFile(os.path.join(ROOT, "data", "raw", "slow", "bis", "WS_TC.zip"))
    out = {}
    with z.open(z.namelist()[0]) as fh:
        rd = csv.reader(io.TextIOWrapper(fh, encoding="utf-8"))
        h = next(rd)
        idx = {c.split(":")[0]: i for i, c in enumerate(h)}

        def g(r, k):
            return r[idx[k]].split(":")[0].strip()

        for r in rd:
            if (g(r, "FREQ") != "Q" or g(r, "TC_BORROWERS") != "P" or g(r, "TC_LENDERS") != "B"
                    or g(r, "UNIT_TYPE") != "770" or g(r, "TC_ADJUST") != "A"):
                continue
            tp = r[idx["TIME_PERIOD"]].strip()
            iso = iso2.get(g(r, "BORROWERS_CTY"))
            if not iso or not tp.endswith("Q4") or r[idx["OBS_VALUE"]].strip() == "":
                continue
            out[(iso, int(tp[:4]))] = float(r[idx["OBS_VALUE"]])
    path = os.path.join(ROOT, "data", "derived", "bis-bank-credit.csv")
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["iso", "year", "value"])
        for (iso, y), v in sorted(out.items()):
            w.writerow([iso, y, repr(v)])
    print("wrote", path, len(out), "rows", len({k[0] for k in out}), "countries")


if __name__ == "__main__":
    main()
