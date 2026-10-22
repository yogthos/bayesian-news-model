"""The material base by country and year (docs/STOCK-FLOW.md, beads
bnm-62z.19-.21): where resources are, where they are processed, and how
they move. Research tooling over data/raw/material/ (sources and licences
in docs/STOCK-FLOW.md, "Material base sources"), writing
data/derived/material/:

  endowments.csv  iso,year,variable,value,unit,flag
      oil_reserves (tonnes; Energy Institute via Our World in Data),
      gas_reserves (cubic metres; EIA via OWID) (1980-2021),
      coal_reserves (tonnes, 2008-): proved reserves;
      arable_land, cropland, irrigated (land area equipped for
      irrigation): FAOSTAT land use, 1000 ha (1961-), flag = FAOSTAT's
      (A official, E estimated, I imputed, X external; L "missing" rows
      dropped);
  capacity.csv    iso,year,variable,value,unit,flag
      power_<x>: EIA installed electrical capacity, MW (1980-). The
      hierarchy: power_total = power_fossil + power_nuclear +
      power_renewable + power_pumped_storage (up to EIA's rounding);
      power_renewable = power_hydro (conventional, no pumped storage) +
      power_nonhydro_renewable; power_nonhydro_renewable = power_wind +
      power_solar + power_geothermal + power_biomass + power_tide. Sum only
      leaves, never a parent with its children.
      plants_<fuel>: the WRI Global Power Plant Database's dated plants
      summed by commissioning year (rounded) and carried forward every
      year to the last, flag as_of_2021. A snapshot (capacity data
      mostly 2017-2019, published 2021) without retired plants, so the
      history is survivors only and plants with no commissioning year are
      missing: plants_undated_<fuel> (MW) and plants_undated_share_<fuel>
      give, per country, what that leaves out (a third of the world's
      capacity). Not to be read causally before 2021: use it for the fuel
      split and concentration at the snapshot; history from power_*.
  production.csv  iso,year,variable,value,unit,flag
      oil, gas, coal production (OWID energy data, from the Energy
      Institute; TWh);
  cereal-trade.csv  importer,exporter,year,item,tonnes,source,flag
      wheat (grain plus wheat and meslin flour at 1.37 t grain per t
      flour), maize, rice (FAO's "Rice, paddy (rice milled equivalent)",
      milled equivalent) by origin, FAOSTAT detailed trade matrix
      (1986-). source "reported": the importer's own import quantities;
      "mirror": for an importer, year and item it did not report, the
      exporters' reported exports to it; "mirror_pair": the importer
      reported the item that year but nothing from this exporter, which
      did report (kept apart: the sides may attribute a cargo to
      different origins). A reporting importer's own figures are kept even
      where exporters report more (Egypt's 2020 wheat: 9.0 Mt reported,
      13.2 Mt by exporters). Self-trade (re-imports) dropped.
  cereal-trade-coverage.csv  iso,year,item,status,mirror_exporters
      status reported | mirror | none for every country and year it
      appears in the matrix: with "none" a missing flow is unknown, not
      zero.
  succession.csv  code,name,until,successors
      historical entities kept under their own codes in every source
      (USSR SUN, Yugoslavia YUG, Czechoslovakia CSK, Serbia and
      Montenegro SCG, Sudan before 2011 SDF, Belgium-Luxembourg BLX,
      Netherlands Antilles ANT, Ethiopia PDR ETP, East and West Germany
      DDR and DEW); Kosovo is XKX in every source.

Codes are ISO 3166 alpha-3, plus XKX and the historical codes; regions,
income groups, the world and minor territories without an ISO code are
dropped. Every value is as published now (revised, not as known at the
time): readers lag them, a year at least (trade: 1-2 years). Run with the
scratchpad venv's python -I (pycountry).
"""
import csv
import json
import os
import sys

import pycountry

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "data/raw/material")
OUT = os.path.join(ROOT, "data/derived/material")

# historical entities: M49 code -> (code, name, last year, successors)
HISTORICAL = {
    "810": ("SUN", "USSR", 1991, "ARM AZE BLR EST GEO KAZ KGZ LTU LVA MDA RUS TJK TKM UKR UZB"),
    "890": ("YUG", "Yugoslav SFR", 1991, "BIH HRV MKD SCG SVN"),
    "200": ("CSK", "Czechoslovakia", 1992, "CZE SVK"),
    "891": ("SCG", "Serbia and Montenegro", 2005, "SRB MNE XKX"),
    "736": ("SDF", "Sudan (former)", 2011, "SDN SSD"),
    "058": ("BLX", "Belgium-Luxembourg", 1999, "BEL LUX"),
    "530": ("ANT", "Netherlands Antilles", 2010, "CUW SXM BES"),
    "230": ("ETP", "Ethiopia PDR", 1992, "ETH ERI"),
    "278": ("DDR", "German Democratic Republic", 1990, "DEU"),
    "280": ("DEW", "Germany, Federal Republic (West)", 1990, "DEU"),
}
# other sources' codes for the same entities
ALIAS = {"XKS": "XKX", "KOS": "XKX", "OWID_KOS": "XKX",
         "OWID_USS": "SUN", "OWID_CZS": "CSK", "OWID_YGS": "YUG", "OWID_SRM": "SCG",
         "DEUW": "DEW", "NLDA": "ANT"}
VALID = ({c.alpha_3 for c in pycountry.countries} | {h[0] for h in HISTORICAL.values()} | {"XKX"})

WHEAT_FLOUR_TO_GRAIN = 1.37  # FAO extraction rate of about 73%
GPPD_PUBLISHED = 2021


def code_of(code):
    """A source's country code (ISO alpha-3, EIA's, GPPD's or OWID's) as
    ours, or None for regions, aggregates and codeless territories."""
    code = ALIAS.get(code, code)
    return code if code in VALID else None


def code_of_m49(code):
    code = code.strip().lstrip("'")
    if not code.isdigit():
        return None
    code = code.zfill(3)
    if code in HISTORICAL:
        return HISTORICAL[code][0]
    c = pycountry.countries.get(numeric=code)
    return c.alpha_3 if c else None


def opened(*path):
    return open(os.path.join(RAW, *path), newline="", encoding="utf-8-sig")


def reserves():
    rows = []
    for name, var, unit in (("oil-proved-reserves", "oil_reserves", "tonnes"),
                            ("natural-gas-proved-reserves", "gas_reserves", "cubic metres"),
                            ("coal-proved-reserves", "coal_reserves", "tonnes")):
        with opened("owid-grapher", name + ".csv") as f:
            r = csv.reader(f)
            next(r)
            for ent, code, year, v in r:
                iso = code_of(code)
                if iso and v != "":
                    rows.append((iso, int(year), var, float(v), unit, ""))
    return rows


def land():
    want = {"Arable land": "arable_land", "Cropland": "cropland", "Land area equipped for irrigation": "irrigated"}
    rows = []
    with opened("faostat/x", "Inputs_LandUse_E_All_Data_(Normalized).csv") as f:
        for d in csv.DictReader(f):
            if d["Item"] in want and d["Element"] == "Area" and d["Value"] and d["Flag"] != "L":
                iso = code_of_m49(d["Area Code (M49)"])
                if iso:
                    rows.append((iso, int(d["Year"]), want[d["Item"]], float(d["Value"]), d["Unit"], d["Flag"]))
    return rows


EIA_SERIES = {
    "Electricity installed capacity": "power_total",
    "Fossil fuels electricity installed capacity": "power_fossil",
    "Nuclear electricity installed capacity": "power_nuclear",
    "Renewable electricity installed capacity": "power_renewable",
    "Hydroelectricity installed capacity": "power_hydro",
    "Non-hydro renewable electricity installed capacity": "power_nonhydro_renewable",
    "Wind electricity installed capacity": "power_wind",
    "Solar electricity installed capacity": "power_solar",
    "Geothermal electricity installed capacity": "power_geothermal",
    "Biomass and waste electricity installed capacity": "power_biomass",
    "Tide and wave electricity installed capacity": "power_tide",
    "Hydroelectric pumped storage electricity installed capacity": "power_pumped_storage",
}


def power_capacity():
    rows = []
    with opened("eia/x", "INTL.txt") as f:
        for line in f:
            d = json.loads(line)
            if d.get("f") != "A" or d.get("units") != "million kilowatts":
                continue
            var = EIA_SERIES.get(d.get("name", "").split(",")[0])
            iso = code_of(d.get("geography") or "")
            if var and iso:
                for y, v in d.get("data", []):
                    if isinstance(v, (int, float)):
                        rows.append((iso, int(y), var, 1000.0 * v, "MW", ""))
    return rows


def plants():
    dated, undated = {}, {}
    with opened("gppd/x", "global_power_plant_database.csv") as f:
        for d in csv.DictReader(f):
            iso = code_of(d["country"])
            mw = float(d["capacity_mw"] or 0)
            if not iso or mw <= 0:
                continue
            fuel = d["primary_fuel"].lower()
            if d["commissioning_year"]:
                k = (iso, fuel, int(round(float(d["commissioning_year"]))))
                dated[k] = dated.get(k, 0.0) + mw
            else:
                undated[(iso, fuel)] = undated.get((iso, fuel), 0.0) + mw
    last = max(y for _, _, y in dated)
    flag = "as_of_%d" % GPPD_PUBLISHED
    rows, by = [], {}
    for (iso, fuel, y), mw in dated.items():
        by.setdefault((iso, fuel), {})[y] = mw
    # cumulative capacity commissioned by each year among the plants in the
    # snapshot, carried every year to the last
    for (iso, fuel), ys in sorted(by.items()):
        run = 0.0
        for y in range(min(ys), last + 1):
            run += ys.get(y, 0.0)
            rows.append((iso, y, "plants_" + fuel, run, "MW", flag))
    for (iso, fuel) in sorted(set(by) | set(undated)):
        u = undated.get((iso, fuel), 0.0)
        total = u + sum(by.get((iso, fuel), {}).values())
        rows.append((iso, last, "plants_undated_" + fuel, u, "MW", flag))
        rows.append((iso, last, "plants_undated_share_" + fuel, u / total, "share", flag))
    return rows


def production():
    want = {"oil_production": "oil", "gas_production": "gas", "coal_production": "coal"}
    rows = []
    with opened("owid-energy", "owid-energy-data.csv") as f:
        for d in csv.DictReader(f):
            iso = code_of(d.get("iso_code", ""))
            if iso:
                for col, var in want.items():
                    if d.get(col):
                        rows.append((iso, int(d["year"]), var, float(d[col]), "TWh", ""))
    return rows


CEREALS = {"Wheat": ("wheat", 1.0), "Wheat and meslin flour": ("wheat", WHEAT_FLOUR_TO_GRAIN),
           "Maize (corn)": ("maize", 1.0), "Rice, paddy (rice milled equivalent)": ("rice", 1.0)}


def cereal_trade():
    """(flows, coverage): each importer's reported imports by origin, and
    for an importer, year and item it did not report, the exporters'
    reported exports to it."""
    imports, exports, reported, seen = {}, {}, set(), {}

    def add(acc, k, v, flag):
        t, fl = acc.get(k, (0.0, set()))
        acc[k] = (t + v, fl | ({flag} if flag else set()))

    with opened("faostat/x", "Trade_DetailedTradeMatrix_E_All_Data_(Normalized).csv") as f:
        cols = next(csv.reader([f.readline()]))
        i = {c: n for n, c in enumerate(cols)}
        for line in f:
            if '"Wheat' not in line and '"Maize (corn)"' not in line and '"Rice, paddy (rice milled' not in line:
                continue
            d = next(csv.reader([line]))
            item = CEREALS.get(d[i["Item"]])
            el = d[i["Element"]]
            if not item or el not in ("Import quantity", "Export quantity") or not d[i["Value"]]:
                continue
            name, factor = item
            rep = code_of_m49(d[i["Reporter Country Code (M49)"]])
            par = code_of_m49(d[i["Partner Country Code (M49)"]])
            year = int(d[i["Year"]])
            v = float(d[i["Value"]]) * factor
            for c in (rep, par):
                if c:
                    lo, hi = seen.get(c, (year, year))
                    seen[c] = (min(lo, year), max(hi, year))
            if el == "Import quantity":
                if rep:
                    reported.add((rep, year, name))
                if rep and par and rep != par:
                    add(imports, (rep, par, year, name), v, d[i["Flag"]])
            elif rep and par and rep != par:
                add(exports, (par, rep, year, name), v, d[i["Flag"]])
    flows = [k + (v, "reported", "".join(sorted(fl))) for k, (v, fl) in imports.items()]
    mirror_exporters = {}
    for (imp, exp, year, name), (v, fl) in exports.items():
        if (imp, year, name) not in reported:
            flows.append((imp, exp, year, name, v, "mirror", "".join(sorted(fl))))
            mirror_exporters[(imp, year, name)] = mirror_exporters.get((imp, year, name), 0) + 1
        elif (imp, exp, year, name) not in imports:
            # the importer reported this item and year, but nothing from this
            # exporter: kept apart, since the two sides may attribute the
            # same cargo to different origins
            flows.append((imp, exp, year, name, v, "mirror_pair", "".join(sorted(fl))))
    coverage = []
    for c, (lo, hi) in seen.items():
        for year in range(lo, hi + 1):
            for name in ("maize", "rice", "wheat"):
                k = (c, year, name)
                status = "reported" if k in reported else "mirror" if k in mirror_exporters else "none"
                coverage.append((c, year, name, status, mirror_exporters.get(k, 0)))
    return flows, coverage


def succession():
    return [(c, n, y, s) for c, n, y, s in HISTORICAL.values()] + [("XKX", "Kosovo (also XKS, KOS, OWID_KOS)", "", "")]


def write(name, header, rows, nkey):
    """Rows by their first `nkey` fields, each key once: a series published
    twice (EIA's, under a country and a region code) is written once, and
    only if both copies agree."""
    seen, out, dropped = {}, [], 0
    for row in sorted(rows, key=lambda r: tuple(str(x) for x in r[:nkey])):
        k = row[:nkey]
        if k in seen:
            a, b = seen[k][nkey], row[nkey]
            if a != b and not (isinstance(a, float) and isinstance(b, float) and abs(a - b) <= 1e-9 * max(abs(a), abs(b))):
                raise ValueError("%s: %r published twice with %r and %r" % (name, k, a, b))
            dropped += 1
            continue
        seen[k] = row
        out.append(row)
    with open(os.path.join(OUT, name), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(out)
    print(name, len(out), "rows;", dropped, "duplicates dropped", file=sys.stderr)


def main():
    os.makedirs(OUT, exist_ok=True)
    head = ["iso", "year", "variable", "value", "unit", "flag"]
    write("succession.csv", ["code", "name", "until", "successors"], succession(), 1)
    write("endowments.csv", head, reserves() + land(), 3)
    write("capacity.csv", head, power_capacity() + plants(), 3)
    write("production.csv", head, production(), 3)
    flows, coverage = cereal_trade()
    write("cereal-trade.csv", ["importer", "exporter", "year", "item", "tonnes", "source", "flag"], flows, 4)
    write("cereal-trade-coverage.csv", ["iso", "year", "item", "status", "mirror_exporters"], coverage, 3)


if __name__ == "__main__":
    main()
