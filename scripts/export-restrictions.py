"""Export restrictions by country, HS6 and year, from the OECD Inventory of
Export Restrictions on Industrial Raw Materials (data/raw/material/
oecd-export-restrictions-irm; docs/ESSENTIALS.md section 8 step 8, bead
bnm-1pq.9), for bnm.essentiality's restriction hazard.

The extract is long: one row per measure attribute. A measure is
(country, commodity, policy type, policy variation) in a year; its
"Direction of change" attribute (DIR) says whether it was introduced,
tightened, eased, eliminated or left unchanged that year.

Writes data/derived/supply/export-restrictions.csv:

  iso,hs6,year,known,covered,in_force,onsets,types
    covered   1: the inventory lists the country and commodity that year
              (a row of any kind, "no restrictions" included)
    in_force  measures in force that year (any direction but
              elimination; the "no restrictions" rows not counted)
    onsets    measures introduced, reintroduced or increased that year
    types     the policy types in force, ';'-joined (M1 export tax, M3
              quota, M4 prohibition, M6 licensing, ...)
    known     year + 1: the inventory's yearly update covers the year
              before

The commodity codes are HS 2007 six-digit codes; raw materials' codes are
the same in HS92 for nearly all of them, and they are kept as they are
(no concordance).

    python3 scripts/export-restrictions.py
"""

import collections
import csv
import gzip
import os

SRC = "data/raw/material/oecd-export-restrictions-irm/OECD_EXP_RESTRIC_all.csv.gz"
OUT = "data/derived/supply/export-restrictions.csv"
ONSET = {"introduction", "reintroduction", "increase"}


def main():
    direction = {}
    covered = set()
    with gzip.open(SRC, "rt", newline="") as f:
        for row in csv.DictReader(f):
            com = row["COMMODITY"]
            if not com.startswith("HS07_"):
                continue
            iso, hs6, year = row["REF_AREA"], com[5:], int(row["TIME_PERIOD"])
            covered.add((iso, hs6, year))
            if row["POLICY_TYPE"] == "NO_RESTRICTION":
                continue
            key = (iso, hs6, year, row["POLICY_TYPE"], row["POLICY_VARIATION"])
            if row["POLICY_DESCRIPTION"] == "DIR":
                direction[key] = row["OBS_VALUE"].strip().lower()
            else:
                direction.setdefault(key, None)
    in_force = collections.Counter()
    onsets = collections.Counter()
    types = collections.defaultdict(set)
    for (iso, hs6, year, ptype, _), d in direction.items():
        k = (iso, hs6, year)
        if d != "elimination":
            in_force[k] += 1
            types[k].add(ptype)
        if d in ONSET:
            onsets[k] += 1
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["iso", "hs6", "year", "known", "covered", "in_force", "onsets", "types"])
        for k in sorted(covered):
            iso, hs6, year = k
            w.writerow([iso, hs6, year, year + 1, 1, in_force[k], onsets[k],
                        ";".join(sorted(types[k], key=lambda t: int(t[1:])))])
    print(f"{len(covered)} country-code-years, {sum(1 for k in covered if in_force[k])} with a measure,"
          f" {sum(onsets.values())} onsets -> {OUT}")


if __name__ == "__main__":
    main()
