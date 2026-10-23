"""Supplier and importer shares per HS92 product and year, for
bnm.essentiality (docs/ESSENTIALS.md section 8 step 8, bead bnm-1pq.9),
cut down from data/derived/supply/hs6-country.csv.gz so the Clojure side
need not read 42 million rows.

Writes data/derived/supply/essentiality-trade.csv.gz:

  year,known,hs6,world,exporters,importers
    one row per HS92 product and data year, 2008 on
    world      the product's world exports (kUSD)
    exporters  iso3:kUSD;... the exporters with at least 0.1% of world
               exports (the rest stay in world, so shares keep them in
               the denominator)
    importers  region:kUSD;... imports summed per EXIOBASE region (the
               region codes of scripts/product-graph.py: a country's own,
               or a rest-of-world region WA WE WF WL WM)

    python3 scripts/essentiality-trade.py
"""

import collections
import csv
import gzip
import ast

SRC = "data/derived/supply/hs6-country.csv.gz"
OUT = "data/derived/supply/essentiality-trade.csv.gz"
FIRST = 2008
MIN_X = 1e-3


def regions():
    """{iso3 region}: EXIOBASE's country regions and rest-of-world members;
    WA for any other country."""
    tables = {}
    for node in ast.parse(open("scripts/product-graph.py").read()).body:
        if isinstance(node, ast.Assign) and getattr(node.targets[0], "id", None) in ("ISO3", "ROW_MEMBERS"):
            tables[node.targets[0].id] = ast.literal_eval(node.value)
    out = {iso: r for r, iso in tables["ISO3"].items()}
    for r, ms in tables["ROW_MEMBERS"].items():
        for iso in (ms.split() if isinstance(ms, str) else ms):
            out[iso] = r
    return out


def flush(w, key, rows, region):
    year, known, hs6 = key
    tx = sum(x for _, x, _ in rows)
    if tx <= 0:
        return
    imports = collections.Counter()
    for iso, _, m in rows:
        if m > 0:
            imports[region.get(iso, "WA")] += m
    xs = ";".join(f"{iso}:{x:.6g}" for iso, x, _ in sorted(rows, key=lambda r: -r[1]) if x >= MIN_X * tx)
    ms = ";".join(f"{r}:{m:.6g}" for r, m in sorted(imports.items()))
    w.writerow([year, known, hs6, f"{tx:.6g}", xs, ms])


def main():
    region = regions()
    groups = collections.defaultdict(list)
    with gzip.open(SRC, "rt", newline="") as f:
        r = csv.reader(f)
        next(r)
        for hs, year, known, hs6, iso, x, m in r:
            if hs != "HS92" or int(year) < FIRST:
                continue
            groups[(year, known, hs6)].append((iso, float(x or 0), float(m or 0)))
    with gzip.open(OUT, "wt", newline="") as f:
        w = csv.writer(f)
        w.writerow(["year", "known", "hs6", "world", "exporters", "importers"])
        for key in sorted(groups):
            flush(w, key, groups[key], region)
    print(f"{len(groups)} product-years -> {OUT}")


if __name__ == "__main__":
    main()
