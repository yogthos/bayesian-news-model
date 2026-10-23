"""Country imports and exports of the technology products (bead bnm-1pq.7,
docs/ESSENTIALS.md section 8 step 6), from data/derived/supply/
hs6-essentials.csv.gz, which scripts/baci.py writes with these codes
(its TECH_CODES):

  HS17 2017-: 870380 (battery-electric cars) and every other 8703 code
      (all passenger cars, the EV share's denominator)
  HS92 1995-: 850780 (accumulators other than lead-acid and
      nickel-cadmium: Li-ion with NiMH and the rest)

Writes data/derived/supply/tech-trade.csv:
  hs,year,known,hs6,iso,imports_t,imports_v,imports_vt,exports_t,exports_v,exports_vt
value in thousand USD, tonnes as BACI gives them; *_vt is the value of the
flows whose tonnes are known (BACI leaves some blank), so the tonnes'
cover of the value is *_vt / *_v. known is BACI's (data year + 2).

    python -I scripts/tech-trade.py [data-dir]
    python -I scripts/tech-trade.py --selftest
"""
import csv
import gzip
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

KEEP = {  # edition -> the codes read
    "HS17": lambda c: c.startswith("8703"),
    "HS92": lambda c: c == "850780",
}


def scan(path, acc):
    """Add the KEEP flows of the essentials file at `path` to acc
    {(hs, year, known, hs6, iso): {side_v, side_t, side_vt}}."""
    with gzip.open(path, "rt", newline="") as f:
        header = f.readline().rstrip("\n").split(",")
        ix = {k: i for i, k in enumerate(header)}
        for line in f:
            r = line.rstrip("\n").split(",")
            keep = KEEP.get(r[ix["hs"]])
            code = r[ix["hs6"]]
            if keep is None or not keep(code):
                continue
            hs, y, kn = r[ix["hs"]], int(r[ix["year"]]), int(r[ix["known"]])
            v = float(r[ix["value"]] or 0.0)
            t = r[ix["tonnes"]]
            for iso, side in ((r[ix["importer"]], "imports"), (r[ix["exporter"]], "exports")):
                a = acc.setdefault((hs, y, kn, code, iso), {})
                a[side + "_v"] = a.get(side + "_v", 0.0) + v
                if t:
                    a[side + "_t"] = a.get(side + "_t", 0.0) + float(t)
                    a[side + "_vt"] = a.get(side + "_vt", 0.0) + v


def selftest():
    """Checks on a toy essentials file: python -I scripts/tech-trade.py --selftest."""
    import gzip
    import tempfile
    rows = ["hs,year,known,hs6,exporter,importer,value,tonnes,uv",
            "HS17,2020,2022,870380,CHN,DEU,10,4,2.5",
            "HS17,2020,2022,870380,USA,DEU,6,,",
            "HS17,2020,2022,870321,JPN,DEU,5,2,2.5",
            "HS92,2020,2022,850780,CHN,USA,8,1,8",
            "HS92,2020,2022,870380,CHN,USA,9,1,9",
            "HS12,2020,2022,850760,CHN,USA,7,1,7",
            "HS92,2020,2022,283691,CHL,CHN,3,1,3"]
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "hs6-essentials.csv.gz")
        with gzip.open(path, "wt") as f:
            f.write("\n".join(rows) + "\n")
        acc = {}
        scan(path, acc)
    assert set(acc) == {("HS17", 2020, 2022, "870380", "CHN"), ("HS17", 2020, 2022, "870380", "USA"),
                        ("HS17", 2020, 2022, "870380", "DEU"), ("HS17", 2020, 2022, "870321", "JPN"),
                        ("HS17", 2020, 2022, "870321", "DEU"), ("HS92", 2020, 2022, "850780", "CHN"),
                        ("HS92", 2020, 2022, "850780", "USA")}, sorted(acc)
    assert acc[("HS17", 2020, 2022, "870380", "DEU")] == {"imports_v": 16.0, "imports_t": 4.0, "imports_vt": 10.0}
    assert acc[("HS17", 2020, 2022, "870380", "USA")] == {"exports_v": 6.0}
    print("tech-trade selftest ok", file=sys.stderr)


def main():
    if sys.argv[1:] == ["--selftest"]:
        return selftest()
    base = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "data")
    sup = os.path.join(base, "derived", "supply")
    acc = {}
    scan(os.path.join(sup, "hs6-essentials.csv.gz"), acc)
    print("scanned", len(acc), file=sys.stderr)
    cols = ["imports_t", "imports_v", "imports_vt", "exports_t", "exports_v", "exports_vt"]
    out = os.path.join(sup, "tech-trade.csv")
    tmp = out + ".part"
    with open(tmp, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["hs", "year", "known", "hs6", "iso"] + cols)
        for k in sorted(acc):
            a = acc[k]
            w.writerow(list(k) + ["%.6g" % a[c] if c in a else "" for c in cols])
    os.replace(tmp, out)
    print("wrote", out, len(acc), "rows", file=sys.stderr)


if __name__ == "__main__":
    main()
