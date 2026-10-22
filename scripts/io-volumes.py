"""Real gross output growth on the spine's 21 sectors (docs/STOCK-FLOW.md,
the energy pilot bnm-62z.6): data/derived/io/spine-volumes.csv,
country,year,sector,volume_change,source: a sector's output this year
over last year's at constant prices.

- Long-run WIOD (1966-2000): the year's gross output at the previous
  year's prices over the previous year's gross output (both US$, so the
  currency cancels), from data/derived/io/lrwiod-prices.csv; merged
  sectors summed first.
- WIOD 2016 (2001-2014): the socio-economic accounts' gross output volume
  index (GO_QI), its year's change, merged sectors weighted by the
  previous year's gross output (national currency).

The OECD tables carry no prices, so no volumes after 2014. Run from the
repo root with the scratchpad venv's python -I (openpyxl).
"""
import collections
import csv
import importlib.util
import os
import sys

import openpyxl

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "data/derived/io")

spec = importlib.util.spec_from_file_location("spine", os.path.join(ROOT, "scripts/io-spine2.py"))
spine = importlib.util.module_from_spec(spec)
spec.loader.exec_module(spine)


def lrwiod():
    go = {}
    pyp = {}
    with open(os.path.join(OUT, "lrwiod-prices.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if d["country"].startswith("x"):
                continue
            k = (d["country"], int(d["year"]), spine.OF3[d["sector"]])
            go[k] = go.get(k, 0.0) + float(d["go"])
            pyp[k] = pyp.get(k, 0.0) + float(d["go_pyp"])
    return {k: pyp[k] / go[(c, y - 1, s)] for k in pyp
            for (c, y, s) in [k] if go.get((c, y - 1, s), 0) > 0}


def wiod16():
    ws = openpyxl.load_workbook(os.path.join(ROOT, "data/raw/io/wiod2016/Socio_Economic_Accounts.xlsx"), read_only=True)["DATA"]
    rows = ws.iter_rows(values_only=True)
    head = list(next(rows))
    years = {i: int(h) for i, h in enumerate(head) if isinstance(h, (int, float))}
    qi, go = {}, {}
    for r in rows:
        country, var, code = r[0], r[1], r[3]
        if code not in spine.OF4 or var not in ("GO_QI", "GO"):
            continue
        for i, y in years.items():
            if r[i] is not None:
                (qi if var == "GO_QI" else go)[(country, y, code)] = float(r[i])
    acc = collections.defaultdict(lambda: [0.0, 0.0])
    for (c, y, code), q in qi.items():
        prev = qi.get((c, y - 1, code))
        w = go.get((c, y - 1, code), 0.0)
        if prev and prev > 0 and w > 0:
            a = acc[(c, y, spine.OF4[code])]
            a[0] += w * q / prev
            a[1] += w
    return {k: v[0] / v[1] for k, v in acc.items() if v[1] > 0}


def main():
    lr = lrwiod()
    w16 = wiod16()
    with open(os.path.join(OUT, "spine-volumes.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector", "volume_change", "source"])
        for k in sorted(lr):
            if k[1] <= 2000:
                w.writerow(list(k) + [lr[k], "lrwiod"])
        for k in sorted(w16):
            if k[1] >= 2001:
                w.writerow(list(k) + [w16[k], "wiod16"])
    print("volumes", sum(1 for k in lr if k[1] <= 2000) + sum(1 for k in w16 if k[1] >= 2001), file=sys.stderr)


if __name__ == "__main__":
    main()
