"""Cross-border bank claims, reporting country on counterparty country,
from the BIS locational banking statistics (bnm-ris contagion exposure):
amounts outstanding, total claims, all instruments, all currencies, on all
counterparty sectors, cross-border, all reporting banks; USD millions at
each quarter's end. The BIS publishes a quarter about four months after
it ends; a quarter is taken as known LAG months after its last month.

    python3 -I scripts/lbs-claims.py
    -> data/derived/lbs-claims.csv  rep,cp,month,usd_m,known_month
"""
import csv
import io
import math
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAG = 5
WANT = {"FREQ": "Q", "L_MEASURE": "S", "L_POSITION": "C", "L_INSTR": "A", "L_DENOM": "TO1",
        "L_CURR_TYPE": "A", "L_PARENT_CTY": "5J", "L_REP_BANK_TYPE": "A", "L_CP_SECTOR": "A", "L_POS_TYPE": "N"}
# cheap prefilter on the raw line before csv parsing
NEEDLES = ("S: Amounts outstanding", ",C: Total claims,", "TO1: All currencies", "A: All sectors", "N: Cross-border")


def code(v):
    return v.split(": ", 1)[0] if v else ""


def claims(fh, iso2):
    """(rep iso3, cp iso3, quarter-end month, USD millions, known month) for
    each wanted row with both countries known and a finite value."""
    header = next(csv.reader([fh.readline()]))
    idx = {c.split(":")[0]: i for i, c in enumerate(header)}
    for raw in fh:
        if not all(n in raw for n in NEEDLES):
            continue
        r = next(csv.reader([raw]))
        if any(code(r[idx[k]]) != v for k, v in WANT.items()):
            continue
        rep, cp = iso2.get(code(r[idx["L_REP_CTY"]])), iso2.get(code(r[idx["L_CP_COUNTRY"]]))
        period, value = r[idx["TIME_PERIOD"]], r[idx["OBS_VALUE"]]
        if not (rep and cp and period and value):
            continue
        x = float(value)
        if math.isnan(x):
            continue
        if code(r[idx["UNIT_MULT"]]) != "6":
            raise ValueError(f"lbs-claims: unit multiplier {r[idx['UNIT_MULT']]!r}, expected millions")
        y, q = period.split("-Q")
        m = int(y) * 12 + 3 * int(q) - 1
        yield (rep, cp, m, x, m + LAG)


def main():
    iso2 = {}
    for l in open(os.path.join(ROOT, "data", "ref", "countryInfo.txt"), encoding="utf-8"):
        if l.startswith("#"):
            continue
        f = l.rstrip("\n").split("\t")
        iso2[f[0]] = f[1]
    z = zipfile.ZipFile(os.path.join(ROOT, "data", "raw", "slow", "bis-lbs", "WS_LBS_D_PUB_csv_flat.zip"))
    n = 0
    with z.open(z.namelist()[0]) as fh, open(os.path.join(ROOT, "data", "derived", "lbs-claims.csv"), "w", newline="") as out:
        w = csv.writer(out)
        w.writerow(["rep", "cp", "month", "usd_m", "known_month"])
        for row in claims(io.TextIOWrapper(fh, encoding="utf-8"), iso2):
            w.writerow(row)
            n += 1
    if n == 0:
        raise SystemExit("lbs-claims: no rows matched")
    print("rows", n)


if __name__ == "__main__":
    main()
