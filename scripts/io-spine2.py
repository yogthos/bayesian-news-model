"""The input-output spine, spliced on common sectors (docs/STOCK-FLOW.md):
the Long-run WIOD (1965-1999, ISIC3, data/derived/io/lrwiod-*.csv from
scripts/io-spine.py), WIOD 2016 (2000-2014, ISIC4; CC BY 4.0, Timmer et al.
2015) and the OECD's inter-country tables (2023 edition, 2015-2020, ISIC4,
data/raw/io/icio2023/x/<year>_SML.csv) on 21 shared sectors. ICIO has no
prices: the price changes end in 2014.

The concordance follows ISIC3: ISIC4's publishing (J58) joins paper and
printing (ISIC3 22), film and broadcasting (J59_J60) and sewerage and waste
(E37-E39) join the public and personal services (ISIC3 92 and 90), repair
of machinery (C33, inside ISIC3 29-35) joins machinery. ICIO's E, J58T60
and C31T33 are split by each country's WIOD 2016 shares of gross output in
2014 (E36 / E37-E39, J58 / J59_J60, C31_C32 / C33; the median share for
countries WIOD 2016 lacks), on output, value added and flows, rows and
columns alike.

Reads data/raw/io/wiod2016/{niots/*.xlsx, Socio_Economic_Accounts.xlsx},
writes data/derived/io/:
  spine-sectors.csv  country,year,sector,go,va,ii,taxes_margins,source,backcast,break
                     ii = go - va in every source; taxes_margins = ii less
                     the sector's intermediate flows: zero in LR-WIOD (whose
                     product cells carry taxes and margins), WIOD 2016's
                     taxes less subsidies on products and international
                     transport margins, ICIO's taxes less subsidies (TLS).
                     backcast: LR-WIOD's share of the country-year's flow
                     cells that are interpolated or extrapolated (0 for the
                     later sources). break: at a join's first year (2000,
                     2015), 1 where the overlap ratio is more than 12% from
                     1, 0 where not, na where there is no overlap; 0 elsewhere
  spine-flows.csv    country,year,from,to,value   (intermediate flows,
                     domestic and imported together in all three sources,
                     millions of US$; LR-WIOD's national tables are in the
                     competitive-imports format, whose product rows are
                     total use)
  spine-overlap.csv  country,sector,join,ratio,break  (the later source's
                     gross output over the earlier's in the year both cover:
                     2000 for WIOD 2016 on LR-WIOD, 2014 for ICIO on WIOD
                     2016; medians about 1, but a quarter of the 2000 pairs
                     and a fifth of the 2014 ones more than 12% apart, some
                     by multiples where a sector is tiny: link the series,
                     or stay within a source, where continuity matters)
  spine-prices.csv   country,year,sector,price_change,basis,source  (gross
                     output prices on the year before. LR-WIOD's (to 2000)
                     from the socio-economic accounts' GO/GO_PYP, out of US$
                     into national currency (basis lcu) by io-spine.py's
                     implied exchange rate, US$ (basis usd) in 1965; WIOD
                     2016's (2001-2014) from GO_PI, national currency.
                     Merged sectors: current over previous-year-price sums)

The rest of the world (ICIO's ROW, LR-WIOD's xROW) is a supplier, not a
using country, and is left out of the rows.

Tables are revised and backcast: as known at the time they are not;
readers lag them (docs/STOCK-FLOW.md: three years).

Run with the scratchpad venv's python -I (openpyxl, numpy).
"""
import collections
import csv
import glob
import os
import statistics
import sys

import numpy as np
import openpyxl

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "data/derived/io")
W16 = os.path.join(ROOT, "data/raw/io/wiod2016")
ICIO_RAW = os.path.join(ROOT, "data/raw/io/icio2023/x")
NOT_COUNTRIES = {"ROW", "xROW"}
BREAK = 0.12

COMMON = {
    "AGR": (["AtB"], ["A01", "A02", "A03"]),
    "MIN": (["C"], ["B"]),
    "FOOD": (["D15t16"], ["C10-C12"]),
    "TEX": (["D17t19"], ["C13-C15"]),
    "PAPER": (["D21t22"], ["C17", "C18", "J58"]),
    "REF": (["D23"], ["C19"]),
    "CHEM": (["D24"], ["C20", "C21"]),
    "RUB": (["D25"], ["C22"]),
    "MINR": (["D26"], ["C23"]),
    "METAL": (["D27t28"], ["C24", "C25"]),
    "MACH": (["D29"], ["C28", "C33"]),
    "ELEC": (["D30t33"], ["C26", "C27"]),
    "TRANSEQ": (["D34t35"], ["C29", "C30"]),
    "OTHMAN": (["Dnec"], ["C16", "C31_C32"]),
    "UTIL": (["E"], ["D35", "E36"]),
    "CONS": (["F"], ["F"]),
    "TRADE": (["G", "H"], ["G45", "G46", "G47", "I"]),
    "TRANSP": (["I60t63", "I64"], ["H49", "H50", "H51", "H52", "H53", "J61"]),
    "FIN": (["J"], ["K64", "K65", "K66"]),
    "BUS": (["K"], ["J62_J63", "L68", "M69_M70", "M71", "M72", "M73", "M74_M75", "N"]),
    "PUB": (["LtQ"], ["E37-E39", "J59_J60", "O84", "P85", "Q", "R_S", "T", "U"]),
}
ICIO = {
    "AGR": ["A01_02", "A03"], "MIN": ["B05_06", "B07_08", "B09"], "FOOD": ["C10T12"], "TEX": ["C13T15"],
    "PAPER": ["C17_18"], "REF": ["C19"], "CHEM": ["C20", "C21"], "RUB": ["C22"], "MINR": ["C23"],
    "METAL": ["C24", "C25"], "MACH": ["C28"], "ELEC": ["C26", "C27"], "TRANSEQ": ["C29", "C30"],
    "OTHMAN": ["C16"], "UTIL": ["D"], "CONS": ["F"], "TRADE": ["G", "I"],
    "TRANSP": ["H49", "H50", "H51", "H52", "H53", "J61"], "FIN": ["K"],
    "BUS": ["J62_63", "L", "M", "N"], "PUB": ["O", "P", "Q", "R", "S", "T"],
}
# ICIO industries spanning two common sectors: ((sector, WIOD 2016 code), ...)
ICIO_SPLIT = {
    "E": (("UTIL", "E36"), ("PUB", "E37-E39")),
    "J58T60": (("PAPER", "J58"), ("PUB", "J59_J60")),
    "C31T33": (("OTHMAN", "C31_C32"), ("MACH", "C33")),
}
OFI = {c: k for k, cs in ICIO.items() for c in cs}
OF3 = {c: k for k, (a, _) in COMMON.items() for c in a}
OF4 = {c: k for k, (_, b) in COMMON.items() for c in b}


def new_sectors():
    return collections.defaultdict(lambda: collections.defaultdict(float))


def lrwiod():
    sectors = new_sectors()
    backcast = {}
    go2000 = collections.defaultdict(float)
    with open(os.path.join(OUT, "lrwiod-sectors.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if d["country"] in NOT_COUNTRIES:
                continue
            if d["year"] == "2000":
                go2000[(d["country"], OF3[d["sector"]])] += float(d["go"] or 0)
                continue
            k = (d["country"], d["year"], OF3[d["sector"]])
            for v in ("go", "va"):
                sectors[k][v] += float(d[v] or 0)
            backcast[(d["country"], d["year"])] = float(d["backcast"] or 0)
    flows = collections.defaultdict(float)
    with open(os.path.join(OUT, "lrwiod-flows.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if int(d["year"]) < 2000 and d["country"] not in NOT_COUNTRIES:
                flows[(d["country"], d["year"], OF3[d["from"]], OF3[d["to"]])] += float(d["value"])
    acc = collections.defaultdict(lambda: [0.0, 0.0, None])
    with open(os.path.join(OUT, "lrwiod-prices.csv"), newline="") as f:
        for d in csv.DictReader(f):
            if int(d["year"]) <= 2000 and d["country"] not in NOT_COUNTRIES:
                a = acc[(d["country"], d["year"], OF3[d["sector"]])]
                a[0] += float(d["go"])
                a[1] += float(d["go_pyp"])
                a[2] = float(d["fx"]) if d["fx"] else None
    prices = {k: (g / p * (fx or 1.0), "usd" if fx is None else "lcu")
              for k, (g, p, fx) in acc.items() if p > 0}
    return sectors, flows, prices, backcast, go2000


def wiod16():
    """Sectors (go, va), flows (domestic and imported) and each country's
    gross output by WIOD 2016 code in 2014 (for splitting ICIO)."""
    sectors = new_sectors()
    flows = collections.defaultdict(float)
    detail = {}
    for path in sorted(glob.glob(os.path.join(W16, "niots", "*_NIOT_nov16.xlsx"))):
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
            elif code in ("VA", "GO") and origin == "TOT":
                for i, c in cols.items():
                    if r[i] is not None:
                        sectors[(country, y, OF4[c])][code.lower()] += float(r[i])
                        if code == "GO" and y == "2014":
                            detail[(country, c)] = float(r[i])
    return sectors, flows, detail


def wiod16_prices():
    """{(country, year, sector): price change}, national currency: current
    over previous-year-price output, sum(GO_t) / sum(GO_t PI_t-1 / PI_t)."""
    ws = openpyxl.load_workbook(os.path.join(W16, "Socio_Economic_Accounts.xlsx"), read_only=True)
    sheet = ws[[s for s in ws.sheetnames if s.lower().startswith("data")][0]]
    rows = sheet.iter_rows(values_only=True)
    head = list(next(rows))
    years = {i: str(int(str(h).lstrip("_"))) for i, h in enumerate(head) if str(h).lstrip("_").isdigit()}
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
        g = go.get((c, y, code), 0.0)
        if prev and prev > 0 and p > 0 and g > 0:
            a = out[(c, y, OF4[code])]
            a[0] += g
            a[1] += g * prev / p
    return {k: v[0] / v[1] for k, v in out.items() if v[1] > 0}


def split_shares(detail):
    """{industry: (by-country weights, median weights)} for ICIO_SPLIT."""
    out = {}
    countries = {c for c, _ in detail}
    for ind, parts in ICIO_SPLIT.items():
        by = {}
        for c in countries:
            xs = [max(detail.get((c, code), 0.0), 0.0) for _, code in parts]
            if sum(xs) > 0:
                by[c] = [x / sum(xs) for x in xs]
        second = statistics.median(w[1] for w in by.values())
        out[ind] = (by, [1 - second, second])
    return out


def icio(years, shares):
    """Sectors (go, va) and flows (intermediate, from every country into
    each country's industries) from the OECD's inter-country tables."""
    def parts(country, ind):
        if ind in OFI:
            return [(OFI[ind], 1.0)]
        by, med = shares[ind]
        return [(sec, w) for (sec, _), w in zip(ICIO_SPLIT[ind], by.get(country, med))]

    sectors = new_sectors()
    flows = collections.defaultdict(float)
    for y in years:
        ys = str(y)
        with open(os.path.join(ICIO_RAW, "%d_SML.csv" % y), newline="") as f:
            r = csv.reader(f)
            head = next(r)
            colparts = []
            for i, h in enumerate(head[1:], 1):
                if "_" in h:
                    c, ind = h.split("_", 1)
                    if c not in NOT_COUNTRIES and (ind in OFI or ind in ICIO_SPLIT):
                        colparts.append((i, c, parts(c, ind)))
            idx = np.array([i for i, _, _ in colparts])
            byrow = collections.defaultdict(lambda: np.zeros(len(idx)))
            for row in r:
                name = row[0]
                if name in ("VA", "OUT"):
                    var = "va" if name == "VA" else "go"
                    vals = np.array([float(x or 0) for x in row[1:]])[idx - 1]
                    for j, (_, c, ps) in enumerate(colparts):
                        for sec, w in ps:
                            sectors[(c, ys, sec)][var] += w * vals[j]
                    continue
                if "_" not in name:
                    continue  # TLS: the residual taxes_margins
                cr, ind = name.split("_", 1)
                if ind not in OFI and ind not in ICIO_SPLIT:
                    continue
                vals = np.array([float(x or 0) for x in row[1:]])[idx - 1]
                for frm, w in parts(cr, ind):
                    byrow[frm] += w * vals
            for frm, vec in byrow.items():
                for j, (_, c, ps) in enumerate(colparts):
                    if vec[j]:
                        for sec, w in ps:
                            flows[(c, ys, frm, sec)] += float(vec[j]) * w
    return sectors, flows


def finish(sectors, flows):
    """ii = go - va, taxes_margins = ii less the sector's flows."""
    into = collections.defaultdict(float)
    for (c, y, _, to), v in flows.items():
        into[(c, y, to)] += v
    for k, s in sectors.items():
        s["ii"] = s["go"] - s["va"]
        s["taxes_margins"] = s["ii"] - into.get(k, 0.0)


def main():
    s3, f3, p3, backcast, go2000 = lrwiod()
    s4, f4, detail = wiod16()
    p4 = wiod16_prices()
    s5all, f5all = icio(range(2014, 2021), split_shares(detail))
    s5 = new_sectors()
    s5.update({k: v for k, v in s5all.items() if k[1] != "2014"})
    f5 = {k: v for k, v in f5all.items() if k[1] != "2014"}
    for s, f in ((s3, f3), (s4, f4), (s5, f5)):
        finish(s, f)
    brk = {}
    with open(os.path.join(OUT, "spine-overlap.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "sector", "join", "ratio", "break"])
        for (c, y, sec), v in sorted(s4.items()):
            if y == "2000" and go2000.get((c, sec), 0) > 0:
                ratio = v["go"] / go2000[(c, sec)]
                brk[(c, "2000", sec)] = int(abs(ratio - 1) > BREAK)
                w.writerow([c, sec, "wiod16/lrwiod 2000", ratio, brk[(c, "2000", sec)]])
        for (c, y, sec), v in sorted(s5all.items()):
            prev = s4.get((c, "2014", sec), {}).get("go", 0) if y == "2014" else 0
            if prev > 0:
                ratio = v["go"] / prev
                brk[(c, "2015", sec)] = int(abs(ratio - 1) > BREAK)
                w.writerow([c, sec, "icio2023/wiod16 2014", ratio, brk[(c, "2015", sec)]])
    with open(os.path.join(OUT, "spine-sectors.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector", "go", "va", "ii", "taxes_margins", "source", "backcast", "break"])
        for src, s, first in (("lrwiod", s3, None), ("wiod16", s4, "2000"), ("icio2023", s5, "2015")):
            for k in sorted(s):
                b = brk.get(k, "na") if k[1] == first else 0
                w.writerow(list(k) + [s[k]["go"], s[k]["va"], s[k]["ii"], s[k]["taxes_margins"], src,
                                      backcast.get(k[:2], 0) if src == "lrwiod" else 0, b])
    with open(os.path.join(OUT, "spine-flows.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "from", "to", "value"])
        for fl in (f3, f4, f5):
            for k in sorted(fl):
                w.writerow(list(k) + [fl[k]])
    with open(os.path.join(OUT, "spine-prices.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["country", "year", "sector", "price_change", "basis", "source"])
        for k in sorted(p3):
            w.writerow(list(k) + [p3[k][0], p3[k][1], "lrwiod"])
        for k in sorted(p4):
            w.writerow(list(k) + [p4[k], "lcu", "wiod16"])
    print("sectors", len(s3) + len(s4) + len(s5), "flows", len(f3) + len(f4) + len(f5),
          "prices", len(p3) + len(p4), file=sys.stderr)


if __name__ == "__main__":
    main()
