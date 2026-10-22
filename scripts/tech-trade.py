"""Country imports and exports of the technology products (bead bnm-1pq.7,
docs/ESSENTIALS.md section 8 step 6), from the BACI bilateral panels that
scripts/baci.py writes:

  data/derived/supply/hs6-hs17.csv  HS17 2017-: 870380 (battery-electric
      cars) and every other 8703 code (all passenger cars, the EV share's
      denominator)
  data/derived/supply/hs6.csv       HS92 1995-: 850780 (accumulators other
      than lead-acid and nickel-cadmium: Li-ion with NiMH and the rest)

Writes data/derived/supply/tech-trade.csv:
  hs,year,known,hs6,iso,imports_t,imports_v,imports_vt,exports_t,exports_v,exports_vt
value in thousand USD, tonnes as BACI gives them; *_vt is the value of the
flows whose tonnes are known (BACI leaves some blank), so the tonnes'
cover of the value is *_vt / *_v. known is BACI's (data year + 2).

    python -I scripts/tech-trade.py [data-dir]
"""
import csv
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SOURCES = [
    ("HS17", "hs6-hs17.csv", lambda c: c.startswith("8703")),
    ("HS92", "hs6.csv", lambda c: c == "850780"),
]
NEEDLES = {"HS17": ",8703", "HS92": ",850780,"}


def scan(path, hs, keep, acc):
    needle = NEEDLES[hs]
    with open(path, newline="") as f:
        header = f.readline().rstrip("\n").split(",")
        ix = {k: i for i, k in enumerate(header)}
        for line in f:
            if needle not in line:
                continue
            r = line.rstrip("\n").split(",")
            code = r[ix["hs6"]]
            if not keep(code):
                continue
            y, kn = int(r[ix["year"]]), int(r[ix["known"]])
            v = float(r[ix["value"]] or 0.0)
            t = r[ix["tonnes"]]
            for iso, side in ((r[ix["importer"]], "imports"), (r[ix["exporter"]], "exports")):
                a = acc.setdefault((hs, y, kn, code, iso), {})
                a[side + "_v"] = a.get(side + "_v", 0.0) + v
                if t:
                    a[side + "_t"] = a.get(side + "_t", 0.0) + float(t)
                    a[side + "_vt"] = a.get(side + "_vt", 0.0) + v


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "data")
    sup = os.path.join(base, "derived", "supply")
    acc = {}
    for hs, name, keep in SOURCES:
        scan(os.path.join(sup, name), hs, keep, acc)
        print(hs, "done", len(acc), file=sys.stderr)
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
