#!/usr/bin/env python3
"""When each country's year of a WDI series was first published, from the
WDI Database Archives (World Bank DataBank source 57; data/raw/wdi-archives,
see its SOURCE.txt): the month of the first archived edition that holds a
value for it. An edition is dated by the later of its id's month and its
label's month (the archive lists 201703 as "2017 Apr" and 201704 as "2017
Mar"), so the table errs late. Editions from 2001 are fetched, so a year
first published before 2001 is not in the table and keeps series.tsv's
date (bnm.named/load-series :first-published).

  data/derived/wdi-first-published.tsv   iso, var, year, month (YYYY-MM)
  data/derived/wdi-editions.tsv          year, month: the first edition holding
                                         the year for 100 countries or more

Run with python3 -I scripts/wdi-first-published.py (standard library only).
"""
import json, os, sys

RAW = "data/raw/wdi-archives"
OUT = "data/derived/wdi-first-published.tsv"
EDITIONS = "data/derived/wdi-editions.tsv"
# the archive's series and the series.tsv variable whose year it dates
SERIES = {"NY.GDP.PCAP.KD.ZG": "economy/gdp_per_capita", "SP.POP.GROW": "people/population"}
MONTHS = {m: i + 1 for i, m in enumerate(["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"])}


def edition_month(v):
    """(year, month) of an edition: the later of its id's and its label's."""
    vid = v["id"]
    a = (int(vid[:4]), int(vid[4:6]))
    y, m = v["value"].split()
    b = (int(y), MONTHS[m[:3]])
    return max(a, b)


def main():
    vs = json.load(open(os.path.join(RAW, "versions.json")))["source"][0]["concept"][0]["variable"]
    when = {v["id"]: edition_month(v) for v in vs}
    first = {}
    held = {var: set() for var in SERIES.values()}  # editions holding the series at all (2001 April's holds no growth per head)
    for s, var in SERIES.items():
        d = os.path.join(RAW, s)
        for f in sorted(os.listdir(d)):
            vid = f[:-5]
            body = json.load(open(os.path.join(d, f)))
            for r in body["source"]["data"]:
                if r["value"] is None:
                    continue
                held[var].add(when[vid])
                c = {x["concept"]: x["id"] for x in r["variable"]}
                k = (c["Country"], var, int(c["Time"][2:]))
                if k not in first or when[vid] < first[k]:
                    first[k] = when[vid]
    # each edition was fetched for its own year and the three before: a
    # country's year that no edition holds within that window was out no
    # earlier than the first edition past it (four years on), or, if none,
    # after the last edition
    earliest = {}
    for s, var in SERIES.items():
        eds = sorted(held[var])
        earliest[var], last = eds[0], eds[-1]
        after_last = (last[0] + (last[1] == 12), last[1] % 12 + 1)
        isos = {k[0] for k in first if k[1] == var}
        for iso in isos:
            for y in range(earliest[var][0] - 3, last[0] + 1):
                if (iso, var, y) not in first:
                    later = [e for e in eds if e[0] >= y + 4]
                    first[(iso, var, y)] = later[0] if later else after_last
    # a year first seen in the earliest fetched edition may have been out
    # before it: those are left out (series.tsv's date stands)
    rows = sorted((k, v) for k, v in first.items() if v > earliest[k[1]])
    with open(OUT, "w") as w:
        w.write("iso\tvar\tyear\tmonth\n")
        for (iso, var, y), (ey, em) in rows:
            w.write("%s\t%s\t%d\t%04d-%02d\n" % (iso, var, y, ey, em))
    print("wrote", OUT, len(rows), "rows", file=sys.stderr)
    # the edition each data year came out in, for every WDI series
    # (--wdi-editions): the first edition holding the year's growth per
    # head for at least 100 countries
    held_by = {}
    d = os.path.join(RAW, "NY.GDP.PCAP.KD.ZG")
    for f in sorted(os.listdir(d)):
        body = json.load(open(os.path.join(d, f)))
        n = {}
        for r in body["source"]["data"]:
            if r["value"] is not None:
                y = int(r["variable"][1]["id"][2:])
                n[y] = n.get(y, 0) + 1
        for y, k in n.items():
            if k >= 100:
                held_by[y] = min(held_by.get(y, when[f[:-5]]), when[f[:-5]])
    # a year held by the earliest edition may have been out before it
    held_by = {y: e for y, e in held_by.items() if e > earliest["economy/gdp_per_capita"]}
    with open(EDITIONS, "w") as w:
        w.write("year\tmonth\n")
        for y in sorted(held_by):
            ey, em = held_by[y]
            w.write("%d\t%04d-%02d\n" % (y, ey, em))
    print("wrote", EDITIONS, len(held_by), "years", file=sys.stderr)


if __name__ == "__main__":
    main()
