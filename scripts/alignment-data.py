"""Point-in-time country-year alignment table: trade dependence on Russia,
China, the US and the EU (IMF DOTS), a US defence pact (ATOP 5.1) and US
troops per million people (troopdata). See docs/ALIGNMENT-DATA.md.

  python3 -I scripts/alignment-data.py \
    --dots data/raw/slow/imf-dots \
    --atop "data/raw/slow/atop/extracted/ATOP 5.1 (.csv)" \
    --troops data/raw/slow/troopdata/troopdata-rebuild-country-year.csv \
    --pop data/raw/slow/worldbank/SP.POP.TOTL.json \
    --countries resources/bnm/countries.edn \
    --cow data/derived/cow-iso3.csv \
    --out data/derived/alignment-annual.csv

Writes iso3,year,var,value,known_from_year. known_from_year is the first
year a forecast may read the value (the lags are below and in the doc).
Every input is read as data (JSON, CSV, EDN text by regex); nothing is
executed. Stdlib only.
"""
import argparse
import csv
import glob
import json
import math
import os
import re
import sys

FIRST_YEAR = 1980
# Publication lags, in years after the value's year (docs/ALIGNMENT-DATA.md).
LAG_DOTS = 2     # annual DOTS for t is complete in the IMF's releases by mid t+1
LAG_ATOP = 1     # a treaty in force during t is public record by the end of t
LAG_TROOPS = 2   # DMDC year-end counts by early t+1, WDI population for t by mid t+1
CIF_FOB = 1.1    # the IMF's conventional CIF/FOB factor, for mirror flows
POWERS = {"RU": "russia", "CN": "china", "US": "us", "B0": "eu"}
MIRROR = ("RU", "CN", "US")  # partners whose own reports were fetched
X, M = "TXG_FOB_USD", "TMG_CIF_USD"

# ATOP 5.1 ends in 2018. Changes to the US's defence pacts from 2019 on, from
# the public treaty record (accession or withdrawal in force): ATOP counts a
# pact in a year it is in force at any point of the year.
ATOP_LAST = 2018
EXTEND_TO = 2025
PACT_CHANGES = {  # iso3: (first year with a pact, first year without)
    "MKD": (2020, None),   # NATO accession, 27 March 2020
    "FIN": (2023, None),   # NATO accession, 4 April 2023
    "SWE": (2024, None),   # NATO accession, 7 March 2024
    "VEN": (None, 2020),   # OAS Charter withdrawal in force 27 April 2019
    "NIC": (None, 2024),   # OAS Charter withdrawal in force 19 November 2023
}


def iso2_map(path):
    text = open(path, encoding="utf-8").read()
    m = dict(re.findall(r':iso2 "([A-Z]{2})",\s*:iso3 "([A-Z]{3})"', text))
    m.update({"XK": "XKX", "KV": "XKX"})
    return m


def load_dots(d):
    s = {}
    for f in sorted(glob.glob(os.path.join(d, "*.json"))):
        for doc in json.load(open(f, encoding="utf-8"))["series"]["docs"]:
            k = doc["dimensions"]
            vals = {}
            for p, v in zip(doc["period"], doc["value"]):
                if isinstance(v, (int, float)) and not isinstance(v, bool):
                    vals[int(p)] = float(v)
            s[(k["REF_AREA"], k["INDICATOR"], k["COUNTERPART_AREA"])] = vals
    return s


def flow(s, rep, ind, par, t):
    """The flow from rep's own report, else (for RU/CN/US partners) the
    partner's mirror report; (value, mirrored?) or (None, False)."""
    v = s.get((rep, ind, par), {}).get(t)
    if v is not None:
        return v, False
    if par in MIRROR:
        if ind == X:
            w = s.get((par, M, rep), {}).get(t)
            return (w / CIF_FOB, True) if w is not None else (None, False)
        w = s.get((par, X, rep), {}).get(t)
        return (w * CIF_FOB, True) if w is not None else (None, False)
    return None, False


def trade_rows(s, iso2, stats):
    reps = sorted({k[0] for k in s if k[0] in iso2})
    years = sorted({t for v in s.values() for t in v if t >= FIRST_YEAR})
    rows, bil = [], {}
    for rep in reps:
        iso = iso2[rep]
        for t in years:
            xt, mt = s.get((rep, X, "W00"), {}).get(t), s.get((rep, M, "W00"), {}).get(t)
            tot = (xt or 0) + (mt or 0) if xt is not None and mt is not None else None
            partners = dict(POWERS)
            if t <= 1991:
                partners["SUH"] = "ussr"
            for par, name in partners.items():
                if par == rep or (par == "RU" and t < 1992):
                    continue
                x, mx = flow(s, rep, X, par, t)
                m, mm = flow(s, rep, M, par, t)
                if x is None or m is None:
                    continue
                stats["mirror"] += mx or mm
                stats["own"] += not (mx or mm)
                bil[(iso, name, t)] = x + m
                # A bilateral flow above the reporter's total is inconsistent
                # data (partner reports against a tiny reported total): dropped.
                if tot and tot > 0 and x + m <= tot:
                    rows.append((iso, t, "dep_" + name, (x + m) / tot, t + LAG_DOTS))
    for (iso, name, t), v in bil.items():
        if name in ("russia", "china"):
            p = bil.get((iso, name, t - 1))
            if p and p > 0 and v > 0:
                rows.append((iso, t, "trade_change_" + name, math.log(v / p), t + LAG_DOTS))
    return rows


def pact_rows(atop_dir, cow):
    states, pact = {}, {}
    with open(os.path.join(atop_dir, "atop5_1sy.csv"), encoding="utf-8") as f:
        for r in csv.DictReader(f):
            t = int(r["year"])
            if t >= FIRST_YEAR and r["state"] in cow and r["state"] != "2":
                states.setdefault(t, set()).add(cow[r["state"]])
    with open(os.path.join(atop_dir, "atop5_1dy.csv"), encoding="utf-8") as f:
        for r in csv.DictReader(f):
            t = int(r["year"])
            if t < FIRST_YEAR or "2" not in (r["mem1"], r["mem2"]):
                continue
            other = r["mem2"] if r["mem1"] == "2" else r["mem1"]
            if other in cow:
                states.setdefault(t, set()).add(cow[other])
                if r["defense"] == "1":
                    pact.setdefault(t, set()).add(cow[other])
    rows = []
    for t in sorted(states):
        for iso in sorted(states[t]):
            rows.append((iso, t, "us_defence_pact", 1 if iso in pact.get(t, ()) else 0, t + LAG_ATOP))
    base_states, base_pact = states[ATOP_LAST], set(pact[ATOP_LAST])
    for t in range(ATOP_LAST + 1, EXTEND_TO + 1):
        p = set(base_pact)
        for iso, (on, off) in PACT_CHANGES.items():
            if on is not None and t >= on:
                p.add(iso)
            if off is not None and t >= off:
                p.discard(iso)
        for iso in sorted(base_states | set(PACT_CHANGES)):
            rows.append((iso, t, "us_defence_pact", 1 if iso in p else 0, t + LAG_ATOP))
    return rows


def troop_rows(troops_path, pop_path):
    pop = {}
    for r in json.load(open(pop_path, encoding="utf-8"))[1]:
        if r.get("value") and r.get("countryiso3code"):
            pop[(r["countryiso3code"], int(r["date"]))] = float(r["value"])
    rows = []
    with open(troops_path, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            iso, t, n = r["iso3c"], int(r["year"]), r["troops_ad"]
            if iso in ("", "NA", "USA") or t < FIRST_YEAR or n in ("", "NA"):
                continue
            p = pop.get((iso, t))
            if p:
                rows.append((iso, t, "us_troops_per_million", float(n) / (p / 1e6), t + LAG_TROOPS))
    return rows


def main():
    a = argparse.ArgumentParser()
    for k in ("dots", "atop", "troops", "pop", "countries", "cow", "out"):
        a.add_argument("--" + k, required=True)
    a = a.parse_args()
    iso2 = iso2_map(a.countries)
    cow = {}
    with open(a.cow, encoding="utf-8") as f:
        for line in f:
            c, i = line.strip().split(",")
            cow[c] = i
    s = load_dots(a.dots)
    unmapped = sorted({k[0] for k in s} - set(iso2))
    print("DOTS reporters not mapped to ISO3 (aggregates and former states):", " ".join(unmapped), file=sys.stderr)
    stats = {"own": 0, "mirror": 0}
    rows = trade_rows(s, iso2, stats) + pact_rows(a.atop, cow) + troop_rows(a.troops, a.pop)
    print("bilateral country-years: own reports %(own)d, mirror-filled %(mirror)d" % stats, file=sys.stderr)
    # One row per key: a duplicate (two DOTS codes for one ISO3) keeps the first.
    seen, out = set(), []
    for r in sorted(rows, key=lambda r: (r[0], r[2], r[1])):
        if (r[0], r[1], r[2]) not in seen:
            seen.add((r[0], r[1], r[2]))
            out.append(r)
    with open(a.out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["iso3", "year", "var", "value", "known_from_year"])
        for iso, t, v, val, k in out:
            w.writerow([iso, t, v, ("%.6g" % val) if isinstance(val, float) else val, k])
    print("wrote", len(out), "rows to", a.out, file=sys.stderr)


if __name__ == "__main__":
    main()
