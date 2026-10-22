"""The input-output spine, spliced on common sectors (docs/STOCK-FLOW.md):
the Long-run WIOD (1965-1999, ISIC3, data/derived/io/lrwiod-*.csv from
scripts/io-spine.py), WIOD 2016 (2000-2014, ISIC4; CC BY 4.0, Timmer et al.
2015) and the OECD's inter-country tables (2023 edition, 2015-2020, ISIC4,
data/raw/io/icio2023/x/<year>_SML.csv) on 21 shared sectors. ICIO has no
prices: the price changes end in 2014.

Reads data/raw/io/wiod2016/{niots/*.xlsx, Socio_Economic_Accounts.xlsx},
writes data/derived/io/:
  spine-sectors.csv  country,year,sector,go,va,ii,source
  spine-flows.csv    country,year,from,to,value   (intermediate flows,
                     domestic and imported together, millions of US$)
  spine-overlap.csv  country,sector,join,ratio  (the later source's gross
                     output over the earlier's in the year both cover:
                     2000 for WIOD 2016 on LR-WIOD, 2014 for ICIO on
                     WIOD 2016; median about 1, a tenth of sectors more
                     than 12% apart: link the series where continuity
                     matters)
  spine-prices.csv   country,year,sector,price_change  (gross output
                     prices on the year before; LR-WIOD's from its
                     previous-year-price tables, WIOD 2016's from the
                     socio-economic accounts' GO_PI)

Tables are revised and backcast: as known at the time they are not;
readers lag them (docs/STOCK-FLOW.md: three years).

Run with the scratchpad venv's python -I (openpyxl).
"""
import collections
import csv
import glob
import os
import sys

import openpyxl

OUT = "data/derived/io"

COMMON = {
    "AGR": (["AtB"], ["A01", "A02", "A03"]),
    "MIN": (["C"], ["B"]),
    "FOOD": (["D15t16"], ["C10-C12"]),
    "TEX": (["D17t19"], ["C13-C15"]),
    "PAPER": (["D21t22"], ["C17", "C18"]),
    "REF": (["D23"], ["C19"]),
    "CHEM": (["D24"], ["C20", "C21"]),
    "RUB": (["D25"], ["C22"]),
    "MINR": (["D26"], ["C23"]),
    "METAL": (["D27t28"], ["C24", "C25"]),
    "MACH": (["D29"], ["C28"]),
    "ELEC": (["D30t33"], ["C26", "C27"]),
    "TRANSEQ": (["D34t35"], ["C29", "C30"]),
    "OTHMAN": (["Dnec"], ["C16", "C31_C32", "C33"]),
    "UTIL": (["E"], ["D35", "E36", "E37-E39"]),
    "CONS": (["F"], ["F"]),
    "TRADE": (["G", "H"], ["G45", "G46", "G47", "I"]),
    "TRANSP": (["I60t63", "I64"], ["H49", "H50", "H51", "H52", "H53", "J61"]),
    "FIN": (["J"], ["K64", "K65", "K66"]),
    "BUS": (["K"], ["J58", "J59_J60", "J62_J63", "L68", "M69_M70", "M71", "M72", "M73", "M74_M75", "N"]),
    "PUB": (["LtQ"], ["O84", "P85", "Q", "R_S", "T", "U"]),
}
ICIO = {
    "AGR": ["A01_02", "A03"], "MIN": ["B05_06", "B07_08", "B09"], "FOOD": ["C10T12"], "TEX": ["C13T15"],
    "PAPER": ["C17_18"], "REF": ["C19"], "CHEM": ["C20", "C21"], "RUB": ["C22"], "MINR": ["C23"],
    "METAL": ["C24", "C25"], "MACH": ["C28"], "ELEC": ["C26", "C27"], "TRANSEQ": ["C29", "C30"],
    "OTHMAN": ["C16", "C31T33"], "UTIL": ["D", "E"], "CONS": ["F"], "TRADE": ["G", "I"],
    "TRANSP": ["H49", "H50", "H51", "H52", "H53", "J61"], "FIN": ["K"],
    "BUS": ["J58T60", "J62_63", "L", "M", "N"], "PUB": ["O", "P", "Q", "R", "S", "T"],
}
OFI = {c: k for k, cs in ICIO.items() for c in cs}
OF3 = {c: k for k, (a, _) in COMMON.items() for c in a}
OF4 = {c: k for k, (_, b) in COMMON.items() for c in b}


def lrwiod():
    sectors = collections.defaultdict(lambda: collections.defaultdict(float))
    with open(os.path.join(OUT, "lrwiod-sectors.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if int(d["year"]) >= 2000:
                continue
            k = (d["country"], d["year"], OF3[d["sector"]])
            for v in ("go", "va", "ii"):
                sectors[k][v] += float(d[v] or 0)
    flows = collections.defaultdict(float)
    with open(os.path.join(OUT, "lrwiod-flows.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if int(d["year"]) < 2000:
                flows[(d["country"], d["year"], OF3[d["from"]], OF3[d["to"]])] += float(d["value"])
    # LR-WIOD's flows are domestic; its imported intermediates are ii less them
    prices = collections.defaultdict(list)
    with open(os.path.join(OUT, "lrwiod-prices.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if int(d["year"]) < 2000:
                prices[(d["country"], d["year"], OF3[d["sector"]])].append(float(d["price_change"]))
    return sectors, flows, prices


def wiod16(raw):
    sectors = collections.defaultdict(lambda: collections.defaultdict(float))
    flows = collections.defaultdict(float)
    for path in sorted(glob.glob(os.path.join(raw, "niots", "*_NIOT_nov16.xlsx"))):
        country = os.path.basename(path)[:3]
        ws = openpyxl.load_workbook(path, read_only=True)["National IO-tables"]
        rows = ws.iter_rows(values_only=True)
        head = next(rows)
        next(rows)
        cols = {i: c for i, c in enumerate(head) if c in OF4}
        for r in rows:
            year, code, origin = r[0], r[1], r[3]
            if year is None:
                continue
            y = str(int(year))
            if code in OF4 and origin in ("Domestic", "Imports"):
                for i, c in cols.items():
                    if r[i]:
                        flows[(country, y, OF4[code], OF4[c])] += float(r[i])
            elif code in ("VA", "GO", "II_fob") and origin == "TOT":
                for i, c in cols.items():
                    if r[i] is not None:
                        sectors[(country, y, OF4[c])][{"VA": "va", "GO": "go", "II_fob": "ii"}[code]] += float(r[i])
    return sectors, flows


def wiod16_prices(raw):
    """{(country, year, sector): [price changes]} from GO_PI weighted by GO."""
    ws = openpyxl.load_workbook(os.path.join(raw, "Socio_Economic_Accounts.xlsx"), read_only=True)
    sheet = ws[[s for s in ws.sheetnames if s.lower().startswith("data")][0]]
    rows = sheet.iter_rows(values_only=True)
    head = list(next(rows))
    years = {i: str(int(h[1:] if isinstance(h, str) and h.startswith("_") else h)) for i, h in enumerate(head)
             if (isinstance(h, (int, float))) or (isinstance(h, str) and h.lstrip("_").isdigit())}
    pi, go = {}, {}
    for r in rows:
        country, var, code = r[0], r[1], r[3]
        if code not in OF4 or var not in ("GO_PI", "GO"):
            continue
        for i, y in years.items():
            if r[i] is not None:
                (pi if var == "GO_PI" else go)[(country, y, code)] = float(r[i])
    out = collections.defaultdict(lambda: [0.0, 0.0])
    for (c, y, code), p in pi.items():
        prev = pi.get((c, str(int(y) - 1), code))
        w = go.get((c, y, code), 0.0)
        if prev and prev > 0 and w > 0:
            acc = out[(c, y, OF4[code])]
            acc[0] += w * p / prev
            acc[1] += w
    return {k: v[0] / v[1] for k, v in out.items() if v[1] > 0}


def icio(raw, years):
    """Sectors and flows (intermediate, from every country into each
    country's industries) from the OECD's inter-country tables."""
    sectors = collections.defaultdict(lambda: collections.defaultdict(float))
    flows = collections.defaultdict(float)
    for y in years:
        path = os.path.join(raw, "%d_SML.csv" % y)
        with open(path, newline="") as f:
            r = csv.reader(f)
            head = next(r)
            cols = {}
            for i, h in enumerate(head[1:], 1):
                if "_" in h:
                    c, ind = h.split("_", 1)
                    if ind in OFI:
                        cols[i] = (c, OFI[ind])
            for row in r:
                name = row[0]
                if name in ("VA", "OUT"):
                    for i, (c, sec) in cols.items():
                        sectors[(c, str(y), sec)]["va" if name == "VA" else "go"] += float(row[i] or 0)
                    continue
                if "_" not in name:
                    continue
                _, ind = name.split("_", 1)
                if ind not in OFI:
                    continue
                frm = OFI[ind]
                for i, (c, sec) in cols.items():
                    v = row[i]
                    if v and v != "0":
                        x = float(v)
                        flows[(c, str(y), frm, sec)] += x
                        sectors[(c, str(y), sec)]["ii"] += x
    return sectors, flows


def main():
    raw = "data/raw/io/wiod2016"
    s3, f3, p3 = lrwiod()
    s4, f4 = wiod16(raw)
    p4 = wiod16_prices(raw)
    s5, f5 = icio("data/raw/io/icio2023/x", range(2015, 2021))
    s5o, _ = icio("data/raw/io/icio2023/x", [2014])
    s3o = collections.defaultdict(float)
    with open(os.path.join(OUT, "lrwiod-sectors.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if d["year"] == "2000":
                s3o[(d["country"], OF3[d["sector"]])] += float(d["go"] or 0)
    with open(os.path.join(OUT, "spine-overlap.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "sector", "join", "ratio"])
        for (c, y, sec), v in sorted(s4.items()):
            if y == "2000" and s3o.get((c, sec), 0) > 0:
                w.writerow([c, sec, "wiod16/lrwiod 2000", v["go"] / s3o[(c, sec)]])
        for (c, y, sec), v in sorted(s5o.items()):
            prev = s4.get((c, "2014", sec), {}).get("go", 0)
            if prev > 0:
                w.writerow([c, sec, "icio2023/wiod16 2014", v["go"] / prev])
    with open(os.path.join(OUT, "spine-sectors.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector", "go", "va", "ii", "source"])
        for src, s in (("lrwiod", s3), ("wiod16", s4), ("icio2023", s5)):
            for k in sorted(s):
                w.writerow(list(k) + [s[k]["go"], s[k]["va"], s[k]["ii"], src])
    with open(os.path.join(OUT, "spine-flows.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "from", "to", "value"])
        for fl in (f3, f4, f5):
            for k in sorted(fl):
                w.writerow(list(k) + [fl[k]])
    with open(os.path.join(OUT, "spine-prices.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector", "price_change"])
        for k in sorted(p3):
            # sectors joined in LR-WIOD: the mean of their price changes
            w.writerow(list(k) + [sum(p3[k]) / len(p3[k])])
        for k in sorted(p4):
            w.writerow(list(k) + [p4[k]])
    print("sectors", len(s3) + len(s4) + len(s5), "flows", len(f3) + len(f4) + len(f5), "prices", len(p3) + len(p4), file=sys.stderr)


if __name__ == "__main__":
    main()
