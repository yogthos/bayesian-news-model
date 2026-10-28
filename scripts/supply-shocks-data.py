"""Inputs for the supply-shock trigger test (bead bnm-82j.3): who supplies
whom, how much each supplier shipped or produced, how much of its use each
country buys abroad, and how much traffic each strait carried, each row
dated by the year it was known. bnm.supply-shocks turns them into each
country's share of supply lost per family (food, fertiliser, energy).

Sources (all open, no account):
  usda-psd       USDA FAS PSD grains: wheat, rice (milled), corn; production,
                 exports, imports, domestic consumption by country and market
                 year. Known in the market year itself: USDA's monthly WASDE
                 carries the current year's estimate for every country by
                 December (the values here are the current vintage, revised
                 since; the in-season estimate is not kept)
  owid-energy    OWID energy (Energy Institute Statistical Review): oil and gas
                 production and consumption (TWh), their shares of primary
                 energy. Known the year after (the Review comes out in June)
  faostat-fertilizers  FAOSTAT fertilisers by nutrient: N, P2O5, K2O
                 production, imports, exports, agricultural use (tonnes of
                 nutrient), summed over the three. Known two years after
                 (FAOSTAT's release lag, as bnm.food reads it)
  imf-portwatch  daily chokepoint transits: deadweight capacity of dry bulk
                 and tanker transits per strait and year, full years only.
                 Known in the year (daily, published as it happens)
  flows          bilateral imports, for supplier shares, each with BACI's own
                 known year (data year + 2): cereals from bnm.food's matrix
                 (data/derived/material/cereal-trade.csv, FAOSTAT, known + 2),
                 fertilisers BACI HS 31 (hs2-pairs), crude 270900, LNG 271111
                 and pipeline gas 271121 (hs6-energy-pairs), tonnes
  fbs            bnm.food's import dependence and use of wheat, rice and maize
                 (data/derived/stocks/food.csv, FAOSTAT balances, rows by the
                 year known: data year + 2)
  chokepoints    bnm.chokepoints' strait shares of each importer's imports
                 (data/derived/stocks/chokepoints.csv, rows by year known):
                 cereals (hs10), fertilisers (hs31), crude oil, LNG

Writes data/derived/shocks/:
  inputs.csv  source,iso,product,year,known,variable,value
  flows.csv   product,importer,exporter,year,known,tonnes

    python -I scripts/supply-shocks-data.py [--selftest]
"""
import csv
import gzip
import io
import os
import sys
import zipfile
from collections import defaultdict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RAW = os.path.join(ROOT, "data", "raw", "material")
DER = os.path.join(ROOT, "data", "derived")
OUT = os.path.join(DER, "shocks")

PSD_ITEMS = {"Wheat": "wheat", "Rice, Milled": "rice", "Corn": "maize"}
PSD_ATTRS = {"Production": "production", "Exports": "exports", "Imports": "imports",
             "Domestic Consumption": "use"}
# PSD names that pycountry does not find (scripts/supply-stocks.py's, and the EU)
PSD_NAMES = {"Burma": "MMR", "Congo (Brazzaville)": "COG", "Congo (Kinshasa)": "COD", "Gambia, The": "GMB",
             "Korea, North": "PRK", "Korea, South": "KOR", "Laos": "LAO", "Russia": "RUS", "Turkey": "TUR",
             "Iran": "IRN", "Syria": "SYR", "Vietnam": "VNM", "Taiwan": "TWN", "Bolivia": "BOL",
             "Tanzania": "TZA", "Moldova": "MDA", "Cote d'Ivoire": "CIV", "Venezuela": "VEN", "Brunei": "BRN",
             "Cabo Verde": "CPV", "Reunion": "REU", "Eswatini": "SWZ", "North Macedonia": "MKD",
             "Hong Kong": "HKG", "United States": "USA", "European Union": "EUN", "EU-15": "EUN",
             "Turkiye": "TUR", "Burkina": "BFA", "Bahamas, The": "BHS", "Micronesia": "FSM",
             "Yemen (Sanaa)": "YEM", "Czechia": "CZE", "Macau": "MAC"}
# PSD's dissolved states, never mapped onto their successors
PSD_SKIP = {"Union of Soviet Socialist Repu", "Yugoslavia (>05/92)", "Czechoslovakia", "German Democratic Republic",
            "Yemen (Aden)", "Serbia and Montenegro", "Other", "EU-12", "Former Czechoslovakia", "Former Yugoslavia"}

FAO_ELEMENTS = {"Production": "production", "Import quantity": "imports", "Export quantity": "exports",
                "Agricultural Use": "use"}
FAO_ITEMS = {"Nutrient nitrogen N (total)", "Nutrient phosphate P2O5 (total)", "Nutrient potash K2O (total)"}

# bnm.chokepoints/straits, by PortWatch id
STRAITS = {"chokepoint1": "suez", "chokepoint2": "panama", "chokepoint3": "bosporus", "chokepoint4": "bab-el-mandeb",
           "chokepoint5": "malacca", "chokepoint6": "hormuz", "chokepoint7": "cape-of-good-hope",
           "chokepoint8": "gibraltar", "chokepoint9": "dover", "chokepoint10": "oresund", "chokepoint11": "taiwan",
           "chokepoint12": "korea", "chokepoint13": "tsugaru", "chokepoint14": "luzon", "chokepoint15": "lombok",
           "chokepoint19": "sunda", "chokepoint20": "makassar", "chokepoint21": "magellan",
           "chokepoint26": "bering", "chokepoint28": "kerch"}
ENERGY_HS6 = {"270900": "oil", "271111": "gas", "271121": "gas"}
CHOKE_PRODUCTS = {"hs10": "food", "hs31": "fert", "crude-oil": "oil", "lng": "gas"}


def name_to3(name):
    if name in PSD_SKIP:
        return None
    if name in PSD_NAMES:
        return PSD_NAMES[name]
    import pycountry
    try:
        return pycountry.countries.lookup(name).alpha_3
    except LookupError:
        return None


def m49_to3(code):
    import pycountry
    c = pycountry.countries.get(numeric=code.lstrip("'").zfill(3))
    return c.alpha_3 if c else None


def psd_rows(zpath):
    """[(iso, item, year, known, variable, value)] of PSD, values in tonnes."""
    out, unmapped = [], set()
    with zipfile.ZipFile(zpath) as z:
        with z.open("psd_grains_pulses.csv") as f:
            for r in csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig")):
                item = PSD_ITEMS.get(r["Commodity_Description"])
                var = PSD_ATTRS.get(r["Attribute_Description"])
                if not item or not var:
                    continue
                iso = name_to3(r["Country_Name"])
                if iso is None:
                    unmapped.add(r["Country_Name"])
                    continue
                y = int(r["Market_Year"])
                out.append((iso, item, y, y, var, float(r["Value"]) * 1000.0))
    return out, unmapped


def owid_rows(path):
    out = []
    cols = {"oil_production": ("oil", "production"), "oil_consumption": ("oil", "use"),
            "gas_production": ("gas", "production"), "gas_consumption": ("gas", "use"),
            "oil_share_energy": ("oil", "share"), "gas_share_energy": ("gas", "share")}
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            iso = r["iso_code"]
            if len(iso) != 3 or iso.startswith("OWID"):
                continue
            y = int(r["year"])
            for c, (p, v) in cols.items():
                if r.get(c):
                    x = float(r[c])
                    out.append((iso, p, y, y + 1, v, x / 100.0 if v == "share" else x))
    return out


def fao_fert_rows(zpath):
    acc = defaultdict(float)
    with zipfile.ZipFile(zpath) as z:
        name = [n for n in z.namelist() if "All_Data" in n][0]
        with z.open(name) as f:
            for r in csv.DictReader(io.TextIOWrapper(f, encoding="latin-1")):
                var = FAO_ELEMENTS.get(r["Element"])
                if var is None or r["Item"] not in FAO_ITEMS or not r["Value"]:
                    continue
                iso = m49_to3(r["Area Code (M49)"])
                if iso is None:
                    continue
                acc[(iso, int(r["Year"]), var)] += float(r["Value"])
    return [(iso, "fert", y, y + 2, v, x) for (iso, y, v), x in sorted(acc.items())]


def portwatch_rows(path):
    """Yearly deadweight capacity per strait and class (bulk: dry bulk; tanker),
    and the sum over the straits (iso WLD), full years only."""
    cap = defaultdict(float)
    days = defaultdict(set)
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            s = STRAITS.get(r["portid"])
            y = int(r["year"])
            days[y].add(r["date"])
            for cls, col in (("bulk", "capacity_dry_bulk"), ("tanker", "capacity_tanker")):
                x = float(r[col] or 0)
                cap[("WLD", cls, y)] += x
                if s:
                    cap[(s, cls, y)] += x
    full = {y for y, d in days.items() if len(d) >= 365}
    return [(s, cls, y, y, "capacity", x) for (s, cls, y), x in sorted(cap.items()) if y in full]


def choke_rows(path):
    """[(iso, product, year, known, strait, share)]: the year is the year known."""
    out = []
    with open(path, newline="") as f:
        r = csv.reader(f)
        next(r)
        for iso, y, var, v in r:
            k = var[len("chokepoint/"):]
            for suf, p in CHOKE_PRODUCTS.items():
                if k.endswith("-" + suf):
                    strait = k[:-len(suf) - 1]
                    if strait != "seaborne":
                        out.append((iso, p, int(y), int(y), strait, float(v)))
    return out


def fbs_rows(path):
    """[(iso, item, year, known, variable, value)]: the year is the year known."""
    out = []
    with open(path, newline="") as f:
        r = csv.reader(f)
        next(r)
        for iso, y, var, v in r:
            for item in ("wheat", "rice", "maize"):
                for name in ("import-dependence", "use"):
                    if var == "food/%s-%s" % (item, name) and v:
                        out.append((iso, item, int(y), int(y), name, float(v)))
    return out


def cereal_flows(path):
    out = defaultdict(float)
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            y = int(r["year"])
            out[(r["item"], r["importer"], r["exporter"], y, y + 2)] += float(r["tonnes"] or 0)
    return out


def baci_flows(path, pick):
    """{(product importer exporter year known) tonnes} of a BACI pairs file, `pick`
    the row's product or None."""
    out = defaultdict(float)
    with gzip.open(path, "rt", newline="") as f:
        r = csv.reader(f)
        h = next(r)
        ix = {k: i for i, k in enumerate(h)}
        code = "hs2" if "hs2" in ix else "hs6"
        for row in r:
            p = pick(row[ix[code]])
            if p is None or not row[ix["tonnes"]]:
                continue
            out[(p, row[ix["importer"]], row[ix["exporter"]], int(row[ix["year"]]), int(row[ix["known"]]))] += \
                float(row[ix["tonnes"]])
    return out


def write(inputs, flows):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "inputs.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["source", "iso", "product", "year", "known", "variable", "value"])
        for src, rows in inputs:
            for iso, p, y, k, v, x in rows:
                w.writerow([src, iso, p, y, k, v, repr(round(x, 6))])
    with open(os.path.join(OUT, "flows.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["product", "importer", "exporter", "year", "known", "tonnes"])
        for (p, i, e, y, k), t in sorted(flows.items()):
            if t > 0 and i != e:
                w.writerow([p, i, e, y, k, repr(round(t, 3))])


def main():
    psd, unmapped = psd_rows(os.path.join(RAW, "usda-psd", "psd_grains_pulses_csv.zip"))
    print("psd rows", len(psd), "unmapped", sorted(unmapped))
    owid = owid_rows(os.path.join(RAW, "owid-energy", "owid-energy-data.csv"))
    print("owid rows", len(owid))
    fert = fao_fert_rows(os.path.join(RAW, "faostat-fertilizers", "Inputs_FertilizersNutrient_E_All_Data_(Normalized).zip"))
    print("fertiliser rows", len(fert))
    pw = portwatch_rows(os.path.join(RAW, "imf-portwatch", "Daily_Chokepoints_Data.csv"))
    print("portwatch rows", len(pw))
    fbs = fbs_rows(os.path.join(DER, "stocks", "food.csv"))
    print("fbs rows", len(fbs))
    ck = choke_rows(os.path.join(DER, "stocks", "chokepoints.csv"))
    print("chokepoint rows", len(ck))
    flows = cereal_flows(os.path.join(DER, "material", "cereal-trade.csv"))
    flows.update(baci_flows(os.path.join(DER, "supply", "hs2-pairs.csv.gz"), lambda c: "fert" if c == "31" else None))
    flows.update(baci_flows(os.path.join(DER, "supply", "hs6-energy-pairs.csv.gz"), ENERGY_HS6.get))
    print("flows", len(flows))
    write([("psd", psd), ("owid", owid), ("fao", fert), ("portwatch", pw), ("choke", ck), ("fbs", fbs)], flows)


def selftest():
    assert name_to3("Korea, South") == "KOR"
    assert name_to3("European Union") == "EUN"
    assert name_to3("Union of Soviet Socialist Repu") is None
    assert m49_to3("'004") == "AFG"
    assert m49_to3("'001") is None  # World
    print("selftest ok")


if __name__ == "__main__":
    selftest() if "--selftest" in sys.argv else main()
