#!/usr/bin/env python3
"""The years each country is inside a conflict or a war, for the aftermath's
clock from an episode's end (docs/AFTERMATH-STOCKS.md, rank 5; research
tooling; needs pandas):

  data/derived/episode-states.csv   kind,iso,year,source

  conflict_onset  a year with any armed conflict active in the country: before
                  1946 a Correlates of War intra-state war's years (start to
                  end of each of its spells; the long catalogue holds wars
                  only), from 1946 any UCDP/PRIO conflict-year (its location)
  war_onset       the same, from 1946 only UCDP conflict-years at 1000 battle
                  deaths or more (intensity 2)

The countries as the catalogues name them: COW's side A through political-
long.py's code list, UCDP's locations as slow-normalize's ucdp() reads them.
A COW war still under way at the data's end (EndYr -7) runs to 1945 here,
where UCDP takes over.

  python3 -I scripts/episode-states.py
"""
import csv, importlib.util, io, os, sys, zipfile

RAW = "data/raw/slow"
MODERN = 1946


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def cow_years(row):
    """The years of a COW war's spells: (StartYr_i, EndYr_i) for i in 1-4,
    -8 not a spell, -7 under way (to MODERN - 1)."""
    out = set()
    for i in range(1, 5):
        a, b = int(row["StartYr%d" % i]), int(row["EndYr%d" % i])
        if a < 0:
            continue
        b = MODERN - 1 if b == -7 else b
        out.update(range(a, min(b, MODERN - 1) + 1))
    return out


def main():
    sn = load("sn", "scripts/slow-normalize.py")
    iso_of = {}
    for line in open(f"{RAW}/ucdp/iisystem.dat", encoding="latin-1"):
        f = line.rstrip("\n").split("\t")
        if len(f) >= 5:
            iso = sn.iso3_of_name(f[2])
            if iso:
                iso_of[int(f[0])] = iso
    for r in csv.DictReader(open(f"{RAW}/coups/Coup-data-2.2.2.csv", encoding="utf-8")):
        iso = sn.iso3_of_name(r["country"])
        if iso:
            iso_of.setdefault(int(r["cowcode"]), iso)
    iso_of.update({210: "NLD", 255: "DEU", 300: "AUT", 329: "ITA", 680: "YEM"})

    rows = set()
    for r in csv.DictReader(open(f"{RAW}/cow-intra/INTRA-STATE WARS v5.1 CSV.csv", encoding="latin-1")):
        c = int(r["CcodeA"])
        iso = iso_of.get(c) if c > 0 else None
        if not iso:
            continue
        for y in cow_years(r):
            for k in ("conflict_onset", "war_onset"):
                rows.add((k, iso, y, "COW intra-state " + r["WarNum"]))

    gw = sn.gw_names()
    z = zipfile.ZipFile(f"{RAW}/ucdp/acd.zip")
    with z.open(z.namelist()[0]) as f:
        acd = list(csv.DictReader(io.TextIOWrapper(f, encoding="utf-8")))
    for r in acd:
        y = int(r["year"])
        if y < MODERN:
            continue
        locs = [gw.get(g.strip()) for g in r["gwno_loc"].split(",")]
        names = [sn.iso3_of_name(n) for n in r["location"].split(",")]
        for u in {u for u in locs + names if u}:
            rows.add(("conflict_onset", u, y, "UCDP " + r["conflict_id"]))
            if r["intensity_level"] == "2":
                rows.add(("war_onset", u, y, "UCDP " + r["conflict_id"]))

    os.makedirs("data/derived", exist_ok=True)
    seen = set()
    with open("data/derived/episode-states.csv", "w") as f:
        f.write("kind,iso,year,source\n")
        for k, iso, y, s in sorted(rows):
            if (k, iso, y) not in seen:
                seen.add((k, iso, y))
                f.write(f"{k},{iso},{y},{s}\n")
    from collections import Counter
    print(Counter(k for k, _, _ in seen), file=sys.stderr)


if __name__ == "__main__":
    main()
