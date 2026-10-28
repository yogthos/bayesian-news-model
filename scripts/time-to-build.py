"""Observed time to build for power plants, from Global Energy Monitor's
Integrated Power Tracker summary tables (bead bnm-4hv.11; docs/COUPLED-
SYSTEMS.md 5.2a). By Little's law, the mean time a megawatt spends under
construction is the capacity under construction over the rate it leaves
construction for operation: T = construction / mean annual additions. The
additions are 2021-2025 (the five full years before the September 2026
snapshot). Little's law needs a steady pipeline. A growing one reads
long: today's pipeline holds this year's larger starts while the past
additions are older, smaller ones (solar: 3.1 years against EIA's 2). A
stalled one (projects in construction for years) reads long too.

The growth-adjusted column corrects for the first: with additions growing
at r (log-linear over 2021-2025) and a fixed build time T, what is under
construction at the snapshot is what completes in the next T years,
C = c(t) (e^(rT) - 1) / r, so T = ln(1 + r C / c(t)) / r, c(t) the trend
rate of additions at the snapshot. Empty where the pipeline is too large
for its shrinking additions (nuclear, hydro): not steady either way.

Input:  data/raw/material/gem-summary-tables/global-integrated-power-tracker/
        1XS9...xlsx (capacity by country and status, by technology) and
        1RJX...xlsx (capacity additions by country and year, by technology).
Output: data/derived/capacity/time-to-build-gem.csv
        technology,construction_mw,additions_mw_per_year,years,years_growth_adjusted
        (one row per technology, world, and one for all technologies).

Usage: python -I scripts/time-to-build.py
"""
import csv
import math
import os
import sys

import openpyxl

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEM = os.path.join(ROOT, "data", "raw", "material", "gem-summary-tables", "global-integrated-power-tracker")
STATUS = "1XS9kjfssMYFqQ7uELf90YvDidUIYpHoCxuTVvCZZjZA.xlsx"
ADDS = "1RJXc1oU569BAUUkeviR_l-Wz6HXX6mQTbxnZb-OfSPc.xlsx"
OUT = os.path.join(ROOT, "data", "derived", "capacity", "time-to-build-gem.csv")
YEARS = range(2021, 2026)
AT = 2026 + 8 / 12  # the snapshot, September 2026, on a calendar where year Y is [Y, Y + 1)
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


def rnd(x):
    return "" if x is None else round(x, 2)


def num(x):
    return float(x) if isinstance(x, (int, float)) else 0.0


def little(construction, additions):
    """Years in construction by Little's law, None without additions."""
    return construction / additions if additions > 0 else None


def little_growth(construction, adds):
    """Years in construction by Little's law for a pipeline growing at a
    constant rate: `adds` {year MW} over YEARS gives the rate (log-linear
    fit) and the trend rate of additions at AT. None without additions or
    when the pipeline is not steady (1 + r C / c(t) <= 0)."""
    ys = [y for y in YEARS if adds.get(y, 0) > 0]
    if len(ys) < 2:
        return None
    x = [y - YEARS[0] for y in ys]
    ly = [math.log(adds[y]) for y in ys]
    mx, my = sum(x) / len(x), sum(ly) / len(ly)
    r = sum((a - mx) * (b - my) for a, b in zip(x, ly)) / sum((a - mx) ** 2 for a in x)
    # additions in year Y = c0 e^(r Y)(e^r - 1)/r: c0 from the fitted line at Y = 0
    k = 1.0 if abs(r) < 1e-12 else (math.exp(r) - 1) / r
    c0 = math.exp(my - r * mx) / k
    ct = c0 * math.exp(r * (AT - YEARS[0]))
    if abs(r) < 1e-12:
        return construction / ct
    z = 1 + r * construction / ct
    return math.log(z) / r if z > 0 else None


def main():
    st = openpyxl.load_workbook(os.path.join(GEM, STATUS), read_only=True, data_only=True)
    ad = openpyxl.load_workbook(os.path.join(GEM, ADDS), read_only=True, data_only=True)
    out, tc, ta, tby = [], 0.0, 0.0, {}
    for t in TECH:
        _, srows = table(st[t])
        head, arows = table(ad[t])
        cons = sum(num(r.get("Construction")) for r in srows)
        by_year = {int(h): sum(num(r.get(h)) for r in arows) for h in head if isinstance(h, (int, float)) and int(h) in YEARS}
        adds = sum(by_year.values()) / len(YEARS)
        tc, ta = tc + cons, ta + adds
        for y, v in by_year.items():
            tby[y] = tby.get(y, 0.0) + v
        out.append([t, round(cons), round(adds), round(little(cons, adds), 2), rnd(little_growth(cons, by_year))])
    out.append(["All", round(tc), round(ta), round(little(tc, ta), 2), rnd(little_growth(tc, tby))])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["technology", "construction_mw", "additions_mw_per_year", "years", "years_growth_adjusted"])
        w.writerows(out)
    for r in out:
        print(*r, sep="\t")


def selftest():
    assert little(300.0, 100.0) == 3.0
    assert little(5.0, 0.0) is None
    # a pipeline growing at r with a fixed build time T: additions in year
    # Y are c0 e^(r Y)(e^r - 1)/r, and what is under construction at the
    # snapshot is what completes over the next T years
    r, t, c0 = 0.25, 1.5, 1000.0
    adds = {y: c0 * math.exp(r * (y - YEARS[0])) * (math.exp(r) - 1) / r for y in YEARS}
    cons = c0 * math.exp(r * (AT - YEARS[0])) * (math.exp(r * t) - 1) / r
    assert abs(little_growth(cons, adds) - t) < 1e-9, little_growth(cons, adds)
    assert little(cons, sum(adds.values()) / len(YEARS)) > 2 * t, "past additions against today's pipeline read long"
    flat = {y: 100.0 for y in YEARS}
    assert abs(little_growth(300.0, flat) - 3.0) < 1e-9, "a steady pipeline: Little's law as it is"
    shrinking = {y: 100.0 * math.exp(-0.5 * (y - YEARS[0])) for y in YEARS}
    assert little_growth(5000.0, shrinking) is None, "a pipeline too large for its shrinking additions is not steady"
    print("ok")


if __name__ == "__main__":
    selftest() if sys.argv[1:] == ["--selftest"] else main()
