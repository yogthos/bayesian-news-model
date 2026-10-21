#!/usr/bin/env python3
"""The long political catalogues the lattice learns from before the modern
one (data/slow/events.tsv) begins (research tooling; needs pandas):

  data/derived/political-long.csv    kind,iso,year,source
  data/derived/political-states.csv  iso,from,to   (years in the state system)

  conflict_onset, war_onset  the first year of a Correlates of War intra-state
                             war (1816-2014; every one reached 1000 battle
                             deaths, so before UCDP the conflict catalogue holds
                             wars only)
  coup                       a leader removed by the military, by other
                             government actors, or in a military power
                             struggle (Archigos 4.1, 1875-2015; successful
                             removals only, where the Cline catalogue also
                             counts attempts)

Countries are named through slow-normalize's gazetteer; Correlates of War
codes through the Gleditsch-Ward list (data/raw/slow/ucdp/iisystem.dat),
whose codes are COW's for nearly every state, and the Cline file's own
cowcode-country pairs.
"""
import csv, importlib.util, os, sys
import pandas as pd

RAW = "data/raw/slow"
spec = importlib.util.spec_from_file_location("sn", "scripts/slow-normalize.py")
sn = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sn)

iso_of = {}
states = {}
for line in open(f"{RAW}/ucdp/iisystem.dat", encoding="latin-1"):
    f = line.rstrip("\n").split("\t")
    if len(f) >= 5:
        iso = sn.iso3_of_name(f[2])
        if iso:
            iso_of[int(f[0])] = iso
            a, b = int(f[3][-4:]), int(f[4][-4:])
            lo, hi = states.get(iso, (a, b))
            states[iso] = (min(lo, a), max(hi, b, 2026 if b >= 2017 else b))
for r in csv.DictReader(open(f"{RAW}/coups/Coup-data-2.2.2.csv", encoding="utf-8")):
    iso = sn.iso3_of_name(r["country"])
    if iso:
        iso_of.setdefault(int(r["cowcode"]), iso)

# states the lists name otherwise, or that no longer exist (their successors)
iso_of.update({210: "NLD", 255: "DEU", 300: "AUT", 329: "ITA", 680: "YEM"})

rows, missing = set(), set()
# the war's state (side A, the government), not the states intervening in it;
# wars between non-state groups have none and are left out
w = pd.read_csv(f"{RAW}/cow-intra/INTRA-STATE WARS v5.1 CSV.csv", encoding="latin-1")
for war, g in w.groupby("WarNum"):
    y = int(g["StartYr1"].min())
    for c in set(g["CcodeA"]):
        if c > 0:
            iso = iso_of.get(int(c))
            if iso:
                rows.add(("war_onset", iso, y, "COW intra-state %d" % war))
                rows.add(("conflict_onset", iso, y, "COW intra-state %d" % war))
            else:
                missing.add(int(c))

a = pd.read_stata(f"{RAW}/archigos/Archigos_4.1_stata14.dta")
coup_exits = {"Removed by Military, without Foreign Support", "Removed by Military, with Foreign Support",
              "Removed by Other Government Actors, without Foreign Support",
              "Removed by Other Government Actors, with Foreign Support",
              "Removed in Military Power Struggle Short of Coup"}
for r in a.itertuples():
    if r.exitcode in coup_exits and pd.notna(r.enddate):
        iso = iso_of.get(int(r.ccode))
        if iso:
            rows.add(("coup", iso, int(str(r.enddate)[:4]), "Archigos %s" % r.obsid))
        else:
            missing.add(int(r.ccode))

os.makedirs("data/derived", exist_ok=True)
with open("data/derived/political-long.csv", "w") as f:
    f.write("kind,iso,year,source\n")
    # one onset per kind, country and year
    seen = set()
    for k, iso, y, s in sorted(rows):
        if (k, iso, y) not in seen:
            seen.add((k, iso, y))
            f.write(f"{k},{iso},{y},{s}\n")
with open("data/derived/political-states.csv", "w") as f:
    f.write("iso,from,to\n")
    for iso, (lo, hi) in sorted(states.items()):
        f.write(f"{iso},{lo},{hi}\n")
from collections import Counter
print(Counter(k for k, _, _ in seen), len(states), "states")
print("unmapped codes:", sorted(missing), file=sys.stderr)

# --- the annual gauges: data/derived/political-annual.csv  iso,year,var,value ---------------------
#   vdem_*      V-Dem v16 indices from 1789 (regime, exclusion, repression,
#               civil society, the military's place in the executive)
#   mad_gdppc, mad_pop   Maddison Project 2023 (from 1800 where estimated)
#   nmc_*       Correlates of War national material capabilities 6.0
#               (1816-2016): population, urban population, soldiers,
#               military spending, iron and steel, energy
import zipfile, io
VDEM = ["v2x_polyarchy", "v2x_libdem", "v2x_regime", "v2x_clphy", "v2x_ex_military", "v2x_neopat", "v2x_rule",
        "v2xpe_exlecon", "v2xpe_exlsocgr", "v2xpe_exlgeo", "v2pepwrses", "v2csreprss", "v2x_cspart", "v2x_corr",
        "v2x_civlib", "v2x_freexp_altinf", "v2xlg_legcon", "v2x_jucon", "v2caassemb", "v2x_polyarchy_stock",
        "v2x_partip", "v2x_frassoc_thick"]
out = []
with zipfile.ZipFile(f"{RAW}/vdem/V-Dem-CY-Core-v16_csv.zip") as z:
    with z.open("V-Dem-CY-Core-v16.csv") as f:
        v = pd.read_csv(io.TextIOWrapper(f, encoding="utf-8"), usecols=["country_text_id", "year"] + VDEM)
for c in VDEM:
    d = v[["country_text_id", "year", c]].dropna()
    out.extend((i, int(y), "vdem_" + c.replace("v2x_", "").replace("v2", ""), float(x)) for i, y, x in d.itertuples(index=False))
m = pd.read_excel(f"{RAW}/maddison/mpd2023_web.xlsx", sheet_name="Full data")
for c in ["gdppc", "pop"]:
    d = m[["countrycode", "year", c]].dropna()
    out.extend((i, int(y), "mad_" + c, float(x)) for i, y, x in d.itertuples(index=False))
n = pd.read_csv(f"{RAW}/cow/nmc/NMC-60-abridged.csv")
for c in ["tpop", "upop", "milper", "milex", "irst", "pec"]:
    d = n[n[c] >= 0][["ccode", "year", c]]
    out.extend((iso_of[int(cc)], int(y), "nmc_" + c, float(x)) for cc, y, x in d.itertuples(index=False) if int(cc) in iso_of)
pd.DataFrame(out, columns=["iso", "year", "var", "value"]).to_csv("data/derived/political-annual.csv", index=False)
o = pd.DataFrame(out, columns=["iso", "year", "var", "value"])
print(o.groupby("var").agg(countries=("iso", "nunique"), first=("year", "min"), last=("year", "max")).to_string())
