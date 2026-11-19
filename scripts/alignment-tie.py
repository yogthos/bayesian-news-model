"""The western tie (bnm-58f): how far a country sits under US/European
political direction, from public treaty and membership dates only, fixed
before any check against an outcome (docs/ALIGNMENT-DATA.md, "Western tie").

  1.0  NATO member, EU member, or a US treaty ally outside NATO (ANZUS,
       the bilateral treaties with Japan, South Korea and the Philippines,
       the Manila Pact with Thailand)
  0.5  US major non-NATO ally (designation) and none of the above
  0.0  otherwise; the Rio Treaty alone is no tie (its members split on
       every bloc question since)

A membership counts from its accession year through the year before it ends.
Dates are public record, so a year's value is known in that year.

  python3 -I scripts/alignment-tie.py data/derived/alignment-annual.csv data/derived/alignment-tie.csv
"""
import csv
import sys

NATO = {**{c: 1949 for c in "BEL CAN DNK FRA ISL ITA LUX NLD NOR PRT GBR USA".split()},
        "GRC": 1952, "TUR": 1952, "DEU": 1955, "ESP": 1982,
        **{c: 1999 for c in "CZE HUN POL".split()},
        **{c: 2004 for c in "BGR EST LVA LTU ROU SVK SVN".split()},
        "ALB": 2009, "HRV": 2009, "MNE": 2017, "MKD": 2020, "FIN": 2023, "SWE": 2024}
NATO = {c: (y, None) for c, y in NATO.items()}

EU = {**{c: 1958 for c in "BEL FRA DEU ITA LUX NLD".split()},
      "DNK": 1973, "IRL": 1973, "GRC": 1981, "ESP": 1986, "PRT": 1986,
      **{c: 1995 for c in "AUT FIN SWE".split()},
      **{c: 2004 for c in "CYP CZE EST HUN LVA LTU MLT POL SVK SVN".split()},
      "BGR": 2007, "ROU": 2007, "HRV": 2013}
EU = {c: (y, None) for c, y in EU.items()}
EU["GBR"] = (1973, 2020)          # left 31 January 2020

# ANZUS in force 1952; the US suspended its obligations to New Zealand in 1986.
US_TREATY = {"AUS": (1952, None), "NZL": (1952, 1986), "PHL": (1952, None),
             "JPN": (1952, None), "KOR": (1954, None), "THA": (1954, None)}

MNNA = {**{c: (1987, None) for c in "AUS EGY ISR JPN KOR".split()},
        "JOR": (1996, None), "NZL": (1996, None), "ARG": (1998, None),
        "BHR": (2002, None), "PHL": (2003, None), "THA": (2003, None), "TWN": (2003, None),
        "KWT": (2004, None), "MAR": (2004, None), "PAK": (2004, None),
        "AFG": (2012, 2022),      # rescinded July 2022
        "TUN": (2015, None), "BRA": (2019, None), "COL": (2022, None), "QAT": (2022, None),
        "KEN": (2024, None)}

TABLES = (("nato", NATO), ("eu", EU), ("us_treaty", US_TREATY), ("mnna", MNNA))


def member(table, c, year):
    span = table.get(c)
    return int(bool(span) and span[0] <= year and (span[1] is None or year < span[1]))


def components(c, year):
    return {name: member(t, c, year) for name, t in TABLES}


def tie(c, year):
    k = components(c, year)
    if k["nato"] or k["eu"] or k["us_treaty"]:
        return 1.0
    return 0.5 if k["mnna"] else 0.0


def rows(countries, y0, y1):
    """(iso3, year, var, value, known_from_year) for every component and the tie."""
    for c in countries:
        for y in range(y0, y1 + 1):
            for name, v in components(c, y).items():
                yield (c, y, name, v, y)
            yield (c, y, "western_tie", tie(c, y), y)


def main(src, out):
    with open(src, encoding="utf-8") as f:
        r = list(csv.DictReader(f))
    countries = sorted({x["iso3"] for x in r})
    years = [int(x["year"]) for x in r]
    with open(out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["iso3", "year", "var", "value", "known_from_year"])
        w.writerows(rows(countries, min(years), max(years)))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
