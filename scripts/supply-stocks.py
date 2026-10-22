"""Stocks for the product graph's outage path (bnm.supply/outage-path;
docs/STOCK-FLOW.md, "Product graph", bead bnm-1pq.23): what a region
holds of a product, in months of its normal use, so that an outage
drains a stock before it cuts a flow.

Writes data/derived/supply/stocks.csv:

  region,product,months,year,known,basis,source
    months  the stock over a month of the region's use (EXIOBASE's
            regions; a rest-of-world region its countries summed)
    year    the year the figure describes; known the year it is known
    basis   data (a series read here) or prior (a cited figure, no
            series)
    source  where it comes from

From (each in data/raw/material/<dir>, with a SOURCE.txt):

  crude (C_COIL)      JODI Oil World Database 2011, primary: crude oil
                      closing stocks (kt) over refinery intake (kt per
                      month), both the year's monthly mean (jodi/)
  refined products    JODI 2011, secondary: closing stocks over total
                      demand, per product (gasoline, gas/diesel oil,
                      jet kerosene, other kerosene, fuel oil, LPG,
                      naphtha)
  natural gas         Europe: GIE's Storage Database (gie-storage/),
  (C_GASE)            working gas (technical, TWh GCV) of the
                      facilities working in 2011 (started by then, not
                      closed before), at NCV/GCV 0.9 and gas's 0.048
                      TJ/t, times FILL (a prior: AGSI+'s daily fill
                      needs an API key, on the manual list), over
                      EXIOBASE's 2011 gas use (use.csv); the US: EIA's
                      2011 mean working gas over its 2011 consumption
                      (eia-ng-storage/). Elsewhere none (not known).
  grain               USDA PSD (usda-psd/), market year 2011/12: ending
                      stocks over domestic consumption: wheat (C_WHEA),
                      milled rice (C_PARI), coarse grains (C_OCER: corn,
                      barley, sorghum, oats, rye, millet, mixed grain);
                      the EU's figure for every member (PSD reports the
                      EU as one)
  nuclear fuel        EU: the Euratom Supply Agency's 2011 report: EU
  (C_NUCF)            utilities' uranium inventories (47,343 tU) could
                      fuel their reactors for at least two and a half
                      years (euratom-esa/): 30 months. Elsewhere a prior,
                      18 months: the fuel in a light-water reactor's core
                      lasts to its next refuelling, 12-24 months
                      (a third to a quarter of the core replaced each
                      outage), with no inventory beyond it counted
  fertiliser          prior, 2 months (the middle of 1-3: plant, port
  (C_NFER, C_PFER)    and dealer inventories; no open series)
  metals              prior, 1 month (steel, aluminium, copper, lead/zinc/
                      tin, other non-ferrous): LME and SHFE warehouse
                      stocks are a few days to weeks of world use, and
                      consumers' and stockholders' inventories are not
                      published (LME/SHFE series on the manual list)

    python -I scripts/supply-stocks.py [data-root]
    python -I scripts/supply-stocks.py --selftest
"""
import csv
import importlib.util
import os
import sys
import zipfile

import pandas as pd

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = next((a for a in sys.argv[1:] if not a.startswith("--")), "data")
RAW = os.path.join(ROOT, "raw/material")
OUT = os.path.join(ROOT, "derived/supply")


def _product_graph():
    spec = importlib.util.spec_from_file_location("product_graph", os.path.join(HERE, "product-graph.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


PG = _product_graph()

FILL = 0.6
"""The share of working gas in store at a cut's start, a prior: the
midpoint of the year's cycle (about 30% at the end of winter, about 90%
at its start, in Europe). AGSI+ has the daily series, behind a key."""

GAS_TJ_PER_T = 0.048
NCV_PER_GCV = 0.9
MAX_MONTHS = 36.0

JODI_PRODUCTS = {"GASOLINE": "C_MGSL", "GASDIES": "C_DOIL", "JETKERO": "C_KJET", "KEROSENE": "C_KERO",
                 "RESFUEL": "C_FOIL", "LPG": "C_LPGA", "NAPHTHA": "C_NAPT"}

PSD_GRAINS = {"Wheat": "C_WHEA", "Rice, Milled": "C_PARI", "Corn": "C_OCER", "Barley": "C_OCER",
              "Sorghum": "C_OCER", "Oats": "C_OCER", "Rye": "C_OCER", "Millet": "C_OCER",
              "Mixed Grain": "C_OCER"}

EU = ["AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GR", "HR", "HU", "IE", "IT", "LT", "LU",
      "LV", "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK", "GB"]

PRIORS = [
    (["C_NFER", "C_PFER"], 2.0, 2026,
     "prior: fertiliser inventories at plants, ports and dealers, 1-3 months of use (no open series)"),
    (["C_STEL", "C_ALUM", "C_COPP", "C_LZTP", "C_ONFM"], 1.0, 2026,
     "prior: metal inventories; LME/SHFE warehouse stocks are days to weeks of world use, "
     "consumers' and stockholders' unpublished"),
]

NUCLEAR_EU = (30.0, 2011, 2012,
              "ESA Annual Report 2011: EU utilities' uranium inventories 47,343 tU, at ~18,000 tU/year "
              "gross requirements 'at least two and a half years'")
NUCLEAR_PRIOR = (18.0, 2026,
                 "prior: fuel in a light-water reactor core lasts to its next refuelling, 12-24 months")

# PSD names that pycountry does not find
PSD_NAMES = {"Burma": "MMR", "Congo (Brazzaville)": "COG", "Congo (Kinshasa)": "COD", "Gambia, The": "GMB",
             "Korea, North": "PRK", "Korea, South": "KOR", "Laos": "LAO", "Russia": "RUS", "Turkey": "TUR",
             "Iran": "IRN", "Syria": "SYR", "Vietnam": "VNM", "Taiwan": "TWN", "Bolivia": "BOL",
             "Tanzania": "TZA", "Moldova": "MDA", "Cote d'Ivoire": "CIV", "Venezuela": "VEN", "Brunei": "BRN",
             "Cabo Verde": "CPV", "Reunion": "REU", "Eswatini": "SWZ", "North Macedonia": "MKD",
             "Hong Kong": "HKG", "United States": "USA"}


def iso2to3(a2):
    import pycountry
    c = pycountry.countries.get(alpha_2=a2)
    return c.alpha_3 if c else None


def name_to3(name):
    if name in PSD_NAMES:
        return PSD_NAMES[name]
    import pycountry
    try:
        return pycountry.countries.lookup(name).alpha_3
    except LookupError:
        return None


def region_of(regions):
    """iso3 -> EXIOBASE region (product-graph's ISO3 and ROW_MEMBERS; WA
    every country no other region takes)."""
    m = {PG.ISO3[r]: r for r in regions if r in PG.ISO3}
    for r, members in PG.ROW_MEMBERS.items():
        if r in regions:
            m.update({i: r for i in members.split()})
    taken = set(m)

    def f(iso3):
        if iso3 is None:
            return None
        return m.get(iso3, "WA" if "WA" in regions and iso3 not in taken else None)
    return f


def months(stock, use_per_month):
    if use_per_month is None or use_per_month <= 0 or stock is None or stock < 0:
        return None
    return min(MAX_MONTHS, stock / use_per_month)


def jodi(path, product_flows, regions):
    """{(region, code): months}: the year's mean closing stock over the
    mean monthly use flow, countries with both reported in at least six
    months (kt), summed by region."""
    d = pd.read_csv(path, dtype=str)
    d["OBS_VALUE"] = pd.to_numeric(d.OBS_VALUE, errors="coerce")
    d = d[(d.UNIT_MEASURE == "KTONS") & d.ENERGY_PRODUCT.isin(product_flows)]
    reg = region_of(regions)
    out = {}
    acc = {}
    for prod, (code, use_flow) in product_flows.items():
        x = d[d.ENERGY_PRODUCT == prod]
        st = x[x.FLOW_BREAKDOWN == "CLOSTLV"].groupby("REF_AREA").OBS_VALUE
        us = x[x.FLOW_BREAKDOWN == use_flow].groupby("REF_AREA").OBS_VALUE
        s_n, u_n = st.apply(lambda v: (v > 0).sum()), us.apply(lambda v: (v > 0).sum())
        s_m, u_m = st.mean(), us.mean()
        for a2 in s_m.index:
            if a2 not in u_m.index or s_n[a2] < 6 or u_n[a2] < 6 or u_m[a2] <= 0:
                continue
            r = reg(iso2to3(a2))
            if r is None:
                continue
            s, u = acc.get((r, code), (0.0, 0.0))
            acc[(r, code)] = (s + s_m[a2], u + u_m[a2])
    for k, (s, u) in acc.items():
        m = months(s, u)
        if m is not None:
            out[k] = m
    return out


def gie_capacity(path):
    """{iso2: working gas TWh (GCV)} of the facilities working in 2011."""
    d = pd.read_excel(path, header=3)
    d = d[d["Country Code"].notna()]
    su = pd.to_numeric(d["Start-up\nyear"], errors="coerce")
    cl = pd.to_numeric(d["Closing year"], errors="coerce")
    status = d["Status"].str.lower()
    ok = status.isin(["operational", "closed", "mothballed"]) & ((su <= 2011) | su.isna()) & ((cl > 2011) | cl.isna())
    ok &= ~((status == "closed") & cl.isna())
    tw = pd.to_numeric(d["Working gas\n(technical) TWh"], errors="coerce").fillna(0.0)
    return tw[ok].groupby(d["Country Code"][ok]).sum().to_dict()


def gas_use(use_csv):
    """{region: tonnes per month} of natural gas used (EXIOBASE 2011)."""
    u = pd.read_csv(use_csv, usecols=["region", "product", "kind", "quantity"])
    u = u[(u["product"] == "C_GASE") & u["kind"].isin(["industry", "final"])]
    return (u.groupby("region")["quantity"].sum() / 12.0).to_dict()


def europe_gas(cap, use_pm, regions):
    reg = region_of(regions)
    tonnes = {}
    for a2, twh in cap.items():
        r = reg(iso2to3("GB" if a2 == "UK" else a2))
        if r is not None:
            tonnes[r] = tonnes.get(r, 0.0) + twh * 3600.0 * NCV_PER_GCV / GAS_TJ_PER_T * FILL
    return {r: m for r, t in tonnes.items() if (m := months(t, use_pm.get(r))) is not None}


def eia_us(dirpath):
    """US months of use: 2011's mean working gas over its consumption (MMcf)."""
    s = pd.read_excel(os.path.join(dirpath, "NG_STOR_SUM_DCU_NUS_M.xls"), sheet_name="Data 1", header=2)
    s = s[pd.to_datetime(s["Date"]).dt.year == 2011]
    wg = s["U.S. Total Natural Gas in Underground Storage (Working Gas) (MMcf)"].astype(float).mean()
    c = pd.read_excel(os.path.join(dirpath, "NG_CONS_SUM_DCU_NUS_A.xls"), sheet_name="Data 1", header=2)
    c = c[pd.to_datetime(c["Date"]).dt.year == 2011]
    tot = c["U.S. Natural Gas Total Consumption (MMcf)"].astype(float).iloc[0]
    return wg / (tot / 12.0)


def psd(zpath, regions):
    """{(region, code): months}: market year 2011 ending stocks over
    domestic consumption (1000 t), the EU's for its members."""
    with zipfile.ZipFile(zpath) as z:
        with z.open("psd_grains_pulses.csv") as f:
            d = pd.read_csv(f, dtype=str)
    d = d[(d.Market_Year == "2011") & d.Commodity_Description.isin(PSD_GRAINS)
          & d.Attribute_Description.isin(["Ending Stocks", "Domestic Consumption"])]
    d = d.assign(value=d.Value.astype(float), code=d.Commodity_Description.map(PSD_GRAINS))
    t = d.pivot_table(index=["Country_Name", "code"], columns="Attribute_Description", values="value", aggfunc="sum")
    reg = region_of(regions)
    acc = {}
    for (name, code), row in t.iterrows():
        rs = [r for r in EU if r in regions] if name == "European Union" else [reg(name_to3(name))]
        for r in rs:
            if r is None:
                continue
            s, u = acc.get((r, code), (0.0, 0.0))
            acc[(r, code)] = (s + row.get("Ending Stocks", 0.0), u + row.get("Domestic Consumption", 0.0))
    return {k: m for k, (s, u) in acc.items() if (m := months(s, u / 12.0)) is not None}


def rows_for(regions, used, jodi_crude, jodi_prod, gas_eu, us_gas, grain):
    """The stocks.csv rows: data first, priors where a product is used and
    no data are."""
    out = []
    for (r, p), m in sorted(jodi_crude.items()):
        out.append((r, p, m, 2011, 2012, "data", "JODI Oil World 2011: crude closing stocks / refinery intake"))
    for (r, p), m in sorted(jodi_prod.items()):
        out.append((r, p, m, 2011, 2012, "data", "JODI Oil World 2011: closing stocks / total demand"))
    for r, m in sorted(gas_eu.items()):
        out.append((r, "C_GASE", m, 2011, 2026, "data",
                    f"GIE Storage Database 2026 (facilities working in 2011) x fill prior {FILL} / EXIOBASE 2011 gas use"))
    if us_gas is not None and "US" in regions:
        out.append(("US", "C_GASE", us_gas, 2011, 2012, "data", "EIA 2011: mean working gas / consumption"))
    for (r, p), m in sorted(grain.items()):
        out.append((r, p, m, 2011, 2013, "data", "USDA PSD 2011/12: ending stocks / domestic consumption"))
    for r in regions:
        if (r, "C_NUCF") in used:
            if r in EU:
                m, y, k, s = NUCLEAR_EU
                out.append((r, "C_NUCF", m, y, k, "data", s))
            else:
                m, k, s = NUCLEAR_PRIOR
                out.append((r, "C_NUCF", m, 2011, k, "prior", s))
        for ps, m, k, s in PRIORS:
            for p in ps:
                if (r, p) in used:
                    out.append((r, p, m, 2011, k, "prior", s))
    return out


def main():
    regions = sorted(pd.read_csv(os.path.join(OUT, "production.csv"), usecols=["region"])["region"].unique())
    rec = pd.read_csv(os.path.join(OUT, "recipes.csv"), usecols=["region", "input"])
    used = set(zip(rec["region"], rec["input"]))
    jd = os.path.join(RAW, "jodi")
    crude = jodi(os.path.join(jd, "primary-2011.csv"), {"CRUDEOIL": ("C_COIL", "REFINOBS")}, regions)
    prods = jodi(os.path.join(jd, "secondary-2011.csv"), {k: (v, "TOTDEMO") for k, v in JODI_PRODUCTS.items()}, regions)
    use_pm = gas_use(os.path.join(OUT, "use.csv"))
    gas = europe_gas(gie_capacity(os.path.join(RAW, "gie-storage/Storage-Database_public_v3.xlsx")), use_pm, regions)
    us = eia_us(os.path.join(RAW, "eia-ng-storage"))
    grain = psd(os.path.join(RAW, "usda-psd/psd_grains_pulses_csv.zip"), regions)
    rows = [x for x in rows_for(regions, used, crude, prods, gas, us, grain) if (x[0], x[1]) in used]
    with open(os.path.join(OUT, "stocks.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["region", "product", "months", "year", "known", "basis", "source"])
        for r in rows:
            w.writerow([r[0], r[1], f"{r[2]:.4g}", r[3], r[4], r[5], r[6]])
    by = {}
    for r in rows:
        by.setdefault((r[1], r[5]), []).append(r[2])
    print(f"stocks.csv: {len(rows)} rows")
    for (p, b), ms in sorted(by.items()):
        ms = sorted(ms)
        print(f"  {p:7s} {b:5s} {len(ms):3d} regions, months median {ms[len(ms) // 2]:.2f} ({ms[0]:.2f}-{ms[-1]:.2f})")


def selftest():
    assert months(6.0, 2.0) == 3.0 and months(1.0, 0.0) is None and months(1e9, 1.0) == MAX_MONTHS
    f = region_of(["DE", "WM", "WA", "US"])
    assert f("DEU") == "DE" and f("IRN") == "WM" and f("THA") == "WA" and f("USA") == "US"
    used = {("DE", "C_NUCF"), ("US", "C_NUCF"), ("DE", "C_NFER")}
    rs = rows_for(["DE", "US"], used, {("DE", "C_COIL"): 2.0}, {}, {"DE": 1.5}, 1.8, {})
    by = {(r[0], r[1]): r for r in rs}
    assert by[("DE", "C_NUCF")][2] == 30.0 and by[("DE", "C_NUCF")][5] == "data"
    assert by[("US", "C_NUCF")][2] == 18.0 and by[("US", "C_NUCF")][5] == "prior"
    assert by[("DE", "C_NFER")][2] == 2.0 and ("US", "C_NFER") not in by
    assert by[("US", "C_GASE")][2] == 1.8 and by[("DE", "C_GASE")][2] == 1.5
    print("supply-stocks.py selftest ok")


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        selftest()
    else:
        main()
