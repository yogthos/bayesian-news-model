"""Checks the I-O spine's outputs (data/derived/io/spine-*.csv from
scripts/io-spine2.py): per country, year and sector, the intermediate flows
into the sector plus taxes_margins equal go - va, and ii = go - va (within
1e-6 relative); no negative gross output; every flow's using sector has a
sector row; no rest-of-world rows; prices have a basis. Exits 1 on failure.

Run with python3 -I scripts/io-spine-check.py (stdlib only).
"""
import collections
import csv
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "data/derived/io")
TOL = 1e-6


def close(a, b, scale):
    return abs(a - b) <= TOL * max(abs(scale), 1.0)


def main():
    errors = []

    def fail(msg):
        if len(errors) < 50:
            print("FAIL", msg, file=sys.stderr)
        errors.append(msg)

    into = collections.defaultdict(float)
    with open(os.path.join(OUT, "spine-flows.csv"), newline="") as f:
        for d in csv.DictReader(f):
            into[(d["country"], d["year"], d["to"])] += float(d["value"])
    seen = set()
    with open(os.path.join(OUT, "spine-sectors.csv"), newline="") as f:
        for d in csv.DictReader(f):
            k = (d["country"], d["year"], d["sector"])
            seen.add(k)
            go, va, ii, tm = (float(d[v]) for v in ("go", "va", "ii", "taxes_margins"))
            if d["country"] in ("ROW", "xROW"):
                fail("rest of world as a using country: %s" % (k,))
            if go < 0:
                fail("negative go %s %r" % (k, go))
            if not close(ii, go - va, go):
                fail("ii != go - va %s: %r %r" % (k, ii, go - va))
            if not close(into.get(k, 0.0) + tm, go - va, go):
                fail("flows + taxes_margins != go - va %s: %r %r" % (k, into.get(k, 0.0) + tm, go - va))
            if d["break"] not in ("0", "1", "na"):
                fail("break %s %r" % (k, d["break"]))
    for k in into:
        if k not in seen:
            fail("flows into a sector without a sector row: %s" % (k,))
    with open(os.path.join(OUT, "spine-prices.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if d["basis"] not in ("lcu", "usd"):
                fail("price basis %s %r" % ((d["country"], d["year"], d["sector"]), d["basis"]))
            if d["country"] in ("ROW", "xROW"):
                fail("rest of world in prices: %s" % d["country"])
            if not float(d["price_change"]) > 0:
                fail("non-positive price change %s" % ((d["country"], d["year"], d["sector"]),))
    print("checked", len(seen), "sector rows,", len(into), "flow columns;", len(errors), "failures", file=sys.stderr)
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
