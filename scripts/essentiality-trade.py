"""Supplier and importer shares per HS92 product and year, for
bnm.essentiality (docs/ESSENTIALS.md section 8 step 8, bead bnm-1pq.9),
cut down from data/derived/supply/hs6-country.csv.gz so the Clojure side
need not read 42 million rows.

Writes data/derived/supply/essentiality-trade.csv.gz:

  year,known,hs6,world,exporters,importers,slices,flows
    one row per HS92 product and data year, 2008 on
    world      the product's world exports (kUSD)
    exporters  iso3:kUSD;... the exporters with at least 0.1% of world
               exports (the rest stay in world, so shares keep them in
               the denominator)
    importers  region:kUSD;... imports summed per EXIOBASE region (the
               region codes of scripts/product-graph.py: a country's own,
               or a rest-of-world region WA WE WF WL WM)
    slices     bilateral where hs6-essentials.csv.gz (scripts/baci.py's
               essentials codes) has the code's flows, else world: the
               supplier slices of an importer then fall back to world
               export shares (bead bnm-1pq.30)
    flows      region/iso:kUSD;... each region's imports by exporter,
               every exporter (so a region's flows add up to its imports),
               blank for world

    python -I scripts/essentiality-trade.py
    python -I scripts/essentiality-trade.py --selftest
"""

import collections
import csv
import gzip
import ast

SRC = "data/derived/supply/hs6-country.csv.gz"
PAIRS = "data/derived/supply/hs6-essentials.csv.gz"
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


def bilateral(path, region, first=FIRST):
    """{(year, known, hs6): {(region, exporter): kUSD}} of the HS92 flows
    in an hs6-essentials file, the importers summed per region."""
    out = collections.defaultdict(collections.Counter)
    with gzip.open(path, "rt", newline="") as f:
        r = csv.reader(f)
        next(r)
        for hs, year, known, hs6, ex, im, v, *_ in r:
            if hs != "HS92" or int(year) < first:
                continue
            v = float(v or 0)
            if v > 0:
                out[(year, known, hs6)][(region.get(im, "WA"), ex)] += v
    return out


def row(key, rows, region, pairs=None):
    """The output row of one product-year (see the module doc), None if it
    has no exports. With `pairs` (bilateral flows), each region's flows
    must add up to its imports."""
    year, known, hs6 = key
    tx = sum(x for _, x, _ in rows)
    if tx <= 0:
        return None
    imports = collections.Counter()
    for iso, _, m in rows:
        if m > 0:
            imports[region.get(iso, "WA")] += m
    xs = ";".join(f"{iso}:{x:.6g}" for iso, x, _ in sorted(rows, key=lambda r: -r[1]) if x >= MIN_X * tx)
    ms = ";".join(f"{r}:{m:.6g}" for r, m in sorted(imports.items()))
    if pairs is None:
        return [year, known, hs6, f"{tx:.6g}", xs, ms, "world", ""]
    by_r = collections.Counter()
    for (r, _), v in pairs.items():
        by_r[r] += v
    for r in set(by_r) | set(imports):
        a, b = by_r.get(r, 0.0), imports.get(r, 0.0)
        assert abs(a - b) <= 1e-6 * max(a, b) + 1e-3, (key, r, a, b)
    fs = ";".join(f"{r}/{ex}:{v:.6g}" for (r, ex), v in sorted(pairs.items()))
    return [year, known, hs6, f"{tx:.6g}", xs, ms, "bilateral", fs]


def main():
    region = regions()
    pairs = bilateral(PAIRS, region)
    groups = collections.defaultdict(list)
    with gzip.open(SRC, "rt", newline="") as f:
        r = csv.reader(f)
        next(r)
        for hs, year, known, hs6, iso, x, m in r:
            if hs != "HS92" or int(year) < FIRST:
                continue
            groups[(year, known, hs6)].append((iso, float(x or 0), float(m or 0)))
    n = 0
    with gzip.open(OUT, "wt", newline="") as f:
        w = csv.writer(f)
        w.writerow(["year", "known", "hs6", "world", "exporters", "importers", "slices", "flows"])
        for key in sorted(groups):
            out = row(key, groups[key], region, pairs.get(key))
            if out:
                w.writerow(out)
                n += out[6] == "bilateral"
    print(f"{len(groups)} product-years ({n} bilateral) -> {OUT}")


def selftest():
    region = {"AAA": "RA", "BBB": "RA", "CCC": "RB"}
    rows = [("AAA", 60.0, 30.0), ("BBB", 0.0, 40.0), ("CCC", 40.0, 30.0)]
    # RA's members import 70 (BBB 30 from AAA, AAA 40 from CCC), RB 30
    # from AAA; the pairs say the same
    pairs = {("RA", "AAA"): 30.0, ("RA", "CCC"): 40.0, ("RB", "AAA"): 30.0}
    out = row(("2020", "2022", "270210"), rows, region, pairs)
    assert out[6] == "bilateral" and out[5] == "RA:70;RB:30", out
    assert out[7] == "RA/AAA:30;RA/CCC:40;RB/AAA:30", out
    assert row(("2020", "2022", "010111"), rows, region)[6:] == ["world", ""]
    try:
        row(("2020", "2022", "270210"), rows, region, {("RA", "CCC"): 70.0})
    except AssertionError:
        pass
    else:
        raise AssertionError("flows that do not add up to the imports must fail")
    print("essentiality-trade selftest ok")


if __name__ == "__main__":
    import sys
    selftest() if sys.argv[1:] == ["--selftest"] else main()
