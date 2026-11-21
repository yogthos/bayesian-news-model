"""WDI's bank nonperforming loans, capital to assets and liquid reserves to
assets as declared series (banking data, bnm-ris): the banking crises of
2012-18 in countries without debt service, house prices or a short rate
(Angola, Azerbaijan, Tajikistan, Ghana, Lebanon and others) had no bank
series in the slow table at all.

Values are the current vintage (data/raw/slow/worldbank-banks, the World
Bank API); each country's year is known at the end of the month of the
first WDI edition that held it (data/raw/wdi-archives-banks, source 57,
each edition fetched for its own year and the three before), as
scripts/wdi-first-published.py dates growth per head:
  - a year no edition held in its window: the first edition four or more
    years on, or the month after the last edition;
  - a year before the series' first edition: that edition;
  - never before the month after the year ends.
NPLs and capital are in WDI from the April 2006 edition, liquid reserves
from 1999; the median lag is 9 months, the 90th percentile 21.

  python3 -I scripts/banking-series.py data/raw/slow/worldbank-banks data/raw/wdi-archives-banks > data/derived/banking-series.tsv
"""
import calendar
import csv
import importlib.util
import json
import os
import sys

NAMES = {"FB.AST.NPER.ZS": "banks/npl", "FB.BNK.CAPA.ZS": "banks/capital_assets",
         "FD.RES.LIQU.AS.ZS": "banks/liquid_reserves"}


def current(body):
    """{(iso, year): value} of a World Bank API v2 body."""
    rows = body[1] if isinstance(body, list) and len(body) > 1 and body[1] else []
    return {(r["countryiso3code"], int(r["date"])): r["value"]
            for r in rows if r.get("value") is not None and r.get("countryiso3code")}


def first_editions(editions, when):
    """({(iso, year): (y, m)} the first edition holding a value, sorted
    months of the editions holding any) from {version id: body}."""
    first, held = {}, set()
    for vid, body in editions.items():
        m = when[vid]
        for r in (body.get("source") or {}).get("data") or []:
            if r["value"] is None:
                continue
            held.add(m)
            c = {x["concept"]: x["id"] for x in r["variable"]}
            k = (c["Country"], int(c["Time"][2:]))
            if k not in first or m < first[k]:
                first[k] = m
    return first, sorted(held)


def known(iso, y, first, held):
    """(year, month) a country's year was first published."""
    if (iso, y) in first:
        return first[(iso, y)]
    later = [e for e in held if e[0] >= y + 4]
    if later:
        return later[0]
    ly, lm = held[-1]
    return (ly + (lm == 12), lm % 12 + 1)


def month_end(y, m):
    return "%04d-%02d-%02d" % (y, m, calendar.monthrange(y, m)[1])


def series(cur, first, held, var):
    out = []
    for (iso, y), v in cur.items():
        e = max(known(iso, y, first, held), (y + 1, 1))
        out.append([iso, var, "a", "%d-12-31" % y, month_end(*e), repr(float(v))])
    out.sort()
    return out


def _edition_month():
    here = os.path.dirname(os.path.abspath(__file__))
    spec = importlib.util.spec_from_file_location("wdi_first_published", os.path.join(here, "wdi-first-published.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m.edition_month


def main():
    cur_dir, arch = sys.argv[1], sys.argv[2]
    edition_month = _edition_month()
    vs = json.load(open(os.path.join(arch, "versions.json")))["source"][0]["concept"][0]["variable"]
    when = {v["id"]: edition_month(v) for v in vs}
    rows = []
    for code, var in NAMES.items():
        cur = current(json.load(open(os.path.join(cur_dir, code + ".json"))))
        d = os.path.join(arch, code)
        eds = {f[:-5]: json.load(open(os.path.join(d, f))) for f in os.listdir(d) if f.endswith(".json")}
        first, held = first_editions(eds, when)
        rows += series(cur, first, held, var)
    w = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
    for r in sorted(rows):
        w.writerow(r)


if __name__ == "__main__":
    main()
