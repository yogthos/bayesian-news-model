"""Observed time to build for power plants, from Global Energy Monitor's
Integrated Power Tracker summary tables (bead bnm-4hv.11; docs/COUPLED-
SYSTEMS.md 5.3). By Little's law, the mean time a megawatt spends under
construction is the capacity under construction over the rate it leaves
construction for operation: T = construction / mean annual additions. The
additions are 2021-2025 (the five full years before the September 2026
snapshot). A steady pipeline is assumed: a growing one (solar) reads short,
a stalled one (projects in construction for years) reads long.

Input:  data/raw/material/gem-summary-tables/global-integrated-power-tracker/
        1XS9...xlsx (capacity by country and status, by technology) and
        1RJX...xlsx (capacity additions by country and year, by technology).
Output: data/derived/capacity/time-to-build-gem.csv
        technology,construction_mw,additions_mw_per_year,years
        (one row per technology, world, and one for all technologies).

Usage: python -I scripts/time-to-build.py
"""
import csv
import os
import sys

import openpyxl

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEM = os.path.join(ROOT, "data", "raw", "material", "gem-summary-tables", "global-integrated-power-tracker")
STATUS = "1XS9kjfssMYFqQ7uELf90YvDidUIYpHoCxuTVvCZZjZA.xlsx"
ADDS = "1RJXc1oU569BAUUkeviR_l-Wz6HXX6mQTbxnZb-OfSPc.xlsx"
OUT = os.path.join(ROOT, "data", "derived", "capacity", "time-to-build-gem.csv")
YEARS = range(2021, 2026)
TECH = ["Coal", "Oil and gas", "Wind", "Utility-scale solar", "Nuclear", "Hydropower", "Bioenergy", "Geothermal"]


def table(ws):
    """(header, rows) of a sheet: the header is the first row starting with Region."""
    head, rows = None, []
    for r in ws.iter_rows(values_only=True):
        if head is None:
            if r and r[0] == "Region":
                head = list(r)
        elif r and r[2]:
            rows.append(dict(zip(head, r)))
    return head, rows


def num(x):
    return float(x) if isinstance(x, (int, float)) else 0.0


def little(construction, additions):
    """Years in construction by Little's law, None without additions."""
    return construction / additions if additions > 0 else None


def main():
    st = openpyxl.load_workbook(os.path.join(GEM, STATUS), read_only=True, data_only=True)
    ad = openpyxl.load_workbook(os.path.join(GEM, ADDS), read_only=True, data_only=True)
    out, tc, ta = [], 0.0, 0.0
    for t in TECH:
        _, srows = table(st[t])
        head, arows = table(ad[t])
        cons = sum(num(r.get("Construction")) for r in srows)
        cols = [h for h in head if isinstance(h, (int, float)) and int(h) in YEARS]
        adds = sum(num(r.get(c)) for r in arows for c in cols) / len(YEARS)
        tc, ta = tc + cons, ta + adds
        out.append([t, round(cons), round(adds), round(little(cons, adds), 2)])
    out.append(["All", round(tc), round(ta), round(little(tc, ta), 2)])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["technology", "construction_mw", "additions_mw_per_year", "years"])
        w.writerows(out)
    for r in out:
        print(*r, sep="\t")


def selftest():
    assert little(300.0, 100.0) == 3.0
    assert little(5.0, 0.0) is None
    print("ok")


if __name__ == "__main__":
    selftest() if sys.argv[1:] == ["--selftest"] else main()
