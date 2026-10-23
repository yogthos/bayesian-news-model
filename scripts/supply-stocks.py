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
                      Each country's stock is capped at JODI_CAP months
                      of its own use before a region's are summed (a
                      tiny demand inflates the ratio: Greece's naphtha
                      at 36 months, Switzerland's fuel oil 25).
                      China reports no crude stock to JODI: a prior from
                      its phase-1 strategic reserve (12.43 Mt, NBS,
                      November 2014) and Xinhua's commercial crude stock
                      (29.2 Mt, July 2010) over its 2011 refinery intake
                      (JODI). A region using an oil product with no
                      figure takes the lower quartile of the reporting
                      regions' (every refinery and terminal holds
                      working stock; zero would make a cut bite in
                      month 1), basis prior.
  natural gas         Europe: GIE's Storage Database (gie-storage/),
  (C_GASE)            working gas (technical, TWh GCV) of the
                      facilities working in 2011 (started by then, not
                      closed before), at NCV/GCV 0.9 and gas's 0.048
                      TJ/t, times FILL (a prior: AGSI+'s daily fill
                      needs an API key, on the manual list), over
                      EXIOBASE's 2011 gas use (use.csv); the US: EIA's
                      2011 mean working gas over its 2011 consumption
                      (eia-ng-storage/). Russia: a prior, Gazprom's
                      working gas at the start of a withdrawal season
                      (62.2 bcm in 2005, 72.7 bcm in 2022: 65 bcm) times
                      FILL over EXIOBASE's use. Elsewhere none: in
                      Europe GIE lists every facility, so a country with
                      none has none; outside it (Canada, China, Japan's
                      and Korea's LNG tanks) not known, left at zero.
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
middle of the year's cycle. GIE's AGSI+ (the daily series, behind a key)
as ACER's public summary gives it: EU storage 35.75% full on 1 April,
2011-2015 mean (ACER, "EU gas in storage and filling level by 1 April");
86-87% in late November, 2011-2024 mean, and about 90% at the start of
winter. A cycle from 0.36 to 0.9 averages about 0.62; 0.6 is a cut at
no particular season (October meets 0.9, March 0.4)."""

JODI_CAP = {"C_COIL": 6.0, "C_MGSL": 6.0, "C_DOIL": 6.0, "C_KJET": 6.0, "C_KERO": 6.0, "C_FOIL": 6.0,
            "C_LPGA": 4.0, "C_NAPT": 3.0}
"""The most months of its use a country's JODI stock is taken to cover:
crude and the fuels 6, above the largest obligation (Switzerland's
compulsory stocks, 4.5 months of petrol, diesel and heating oil; the
IEA's 90 days of net imports) and Japan's 4.3 months of crude, the
highest regional ratio in 2011; LPG 4 (Japan's national and private LPG
stockpiles, about 90 days together); naphtha 3, a feedstock with no
obligation. Above it, the ratio measures a tiny demand, not a stock."""

CN_CRUDE_KT = 12430.0 + 29200.0
"""China's crude stocks, kt, a prior (JODI has no Chinese crude stock):
the phase-1 strategic reserve, 12.43 Mt at Zhenhai, Zhoushan, Huangdao
and Dalian (National Bureau of Statistics, November 2014, China Daily
21 Nov 2014; filled by 2009), and the commercial crude stock, 29.2 Mt in
July 2010 (Xinhua's China Oil, Gas & Petrochemicals, via Interfax);
Xinhua's 33.6 Mt of May 2014 was the highest since January 2010, so
2011 lay between. Phase 2's sites were not yet filled."""

RU_GAS_BCM = 65.0
"""Russia's working gas at the start of a withdrawal season, bcm, a
prior: Gazprom's 62.2 bcm (2005) and 72.7 bcm (2022, a record)."""
GAS_GJ_PER_M3 = 0.0335  # NCV of Russian pipeline gas per standard cubic metre

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


def jodi(path, product_flows, regions, uses=None):
    """{(region, code): months}: the year's mean closing stock over the
    mean monthly use flow, countries with both reported in at least six
    months (kt), each stock capped at JODI_CAP months of its use, summed
    by region. `uses`, when a dict, gets {(iso2, code): mean monthly use}
    of every country reporting the use flow."""
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
        if uses is not None:
            uses.update({(a2, code): u_m[a2] for a2 in u_m.index if u_n[a2] >= 6 and u_m[a2] > 0})
        for a2 in s_m.index:
            if a2 not in u_m.index or s_n[a2] < 6 or u_n[a2] < 6 or u_m[a2] <= 0:
                continue
            r = reg(iso2to3(a2))
            if r is None:
                continue
            s, u = acc.get((r, code), (0.0, 0.0))
            acc[(r, code)] = (s + min(s_m[a2], JODI_CAP.get(code, MAX_MONTHS) * u_m[a2]), u + u_m[a2])
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


def russia_gas(use_pm):
    """Russia's months of gas use in storage, a prior (RU_GAS_BCM)."""
    t = RU_GAS_BCM * 1e9 * GAS_GJ_PER_M3 / 1000.0 / GAS_TJ_PER_T * FILL
    return months(t, use_pm.get("RU"))


def lower_quartile(xs):
    xs = sorted(xs)
    return xs[(len(xs) - 1) // 4] if xs else None


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


OIL = ["C_COIL"] + list(JODI_PRODUCTS.values())


def rows_for(regions, used, jodi_crude, jodi_prod, gas_eu, us_gas, grain, cn_crude=None, ru_gas=None):
    """The stocks.csv rows: data first, priors where a product is used and
    no data are (an oil product: the reporting regions' lower quartile)."""
    out = []
    if cn_crude is not None and "CN" in regions and ("CN", "C_COIL") not in jodi_crude:
        out.append(("CN", "C_COIL", cn_crude, 2011, 2026, "prior",
                    "prior: NBS phase-1 SPR 12.43 Mt + Xinhua commercial crude 29.2 Mt (2010) / JODI 2011 refinery intake"))
    if ru_gas is not None and "RU" in regions:
        out.append(("RU", "C_GASE", ru_gas, 2011, 2026, "prior",
                    f"prior: Gazprom working gas {RU_GAS_BCM:.0f} bcm x fill {FILL} / EXIOBASE 2011 gas use"))
    oil = {**jodi_crude, **jodi_prod}
    have = set(oil) | {(r[0], r[1]) for r in out}
    for p in OIL:
        q = lower_quartile([m for (r, c), m in oil.items() if c == p])
        if q is None:
            continue
        for r in regions:
            if (r, p) in used and (r, p) not in have:
                out.append((r, p, q, 2011, 2026, "prior",
                            "prior: lower quartile of the JODI 2011 regions' months (no figure reported)"))
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
    uses = {}
    crude = jodi(os.path.join(jd, "primary-2011.csv"), {"CRUDEOIL": ("C_COIL", "REFINOBS")}, regions, uses)
    cn = months(CN_CRUDE_KT, uses.get(("CN", "C_COIL")))
    prods = jodi(os.path.join(jd, "secondary-2011.csv"), {k: (v, "TOTDEMO") for k, v in JODI_PRODUCTS.items()}, regions)
    use_pm = gas_use(os.path.join(OUT, "use.csv"))
    gas = europe_gas(gie_capacity(os.path.join(RAW, "gie-storage/Storage-Database_public_v3.xlsx")), use_pm, regions)
    us = eia_us(os.path.join(RAW, "eia-ng-storage"))
    grain = psd(os.path.join(RAW, "usda-psd/psd_grains_pulses_csv.zip"), regions)
    rows = [x for x in rows_for(regions, used, crude, prods, gas, us, grain, cn, russia_gas(use_pm))
            if (x[0], x[1]) in used]
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
    # a country's stock capped at JODI_CAP months of its own use
    import tempfile
    with tempfile.NamedTemporaryFile("w", suffix=".csv", delete=False) as f:
        f.write("REF_AREA,TIME_PERIOD,ENERGY_PRODUCT,FLOW_BREAKDOWN,UNIT_MEASURE,OBS_VALUE,ASSESSMENT_CODE\n")
        for mth in range(1, 13):
            for a2, st, use in [("GR", 360.0, 10.0), ("DE", 50.0, 100.0)]:
                f.write(f"{a2},2011-{mth:02d},NAPHTHA,CLOSTLV,KTONS,{st},1\n")
                f.write(f"{a2},2011-{mth:02d},NAPHTHA,TOTDEMO,KTONS,{use},1\n")
    uses = {}
    j = jodi(f.name, {"NAPHTHA": ("C_NAPT", "TOTDEMO")}, ["GR", "DE"], uses)
    os.unlink(f.name)
    assert j[("GR", "C_NAPT")] == JODI_CAP["C_NAPT"] and j[("DE", "C_NAPT")] == 0.5, j
    assert uses[("GR", "C_NAPT")] == 10.0
    # China's crude and Russia's gas from priors; missing oil at the lower quartile
    regs = ["CN", "DE", "FR", "IT", "PL", "RU"]
    used = {(r, "C_COIL") for r in regs} | {("RU", "C_GASE"), ("CN", "C_DOIL"), ("DE", "C_DOIL")}
    crude = {("DE", "C_COIL"): 2.0, ("FR", "C_COIL"): 1.0, ("IT", "C_COIL"): 0.5, ("PL", "C_COIL"): 3.0}
    rs = rows_for(regs, used, crude, {("DE", "C_DOIL"): 1.2}, {}, None, {}, 1.1, 1.0)
    by = {(r[0], r[1]): r for r in rs}
    assert by[("CN", "C_COIL")][2] == 1.1 and by[("CN", "C_COIL")][5] == "prior"
    assert by[("RU", "C_COIL")][2] == 0.5 and by[("RU", "C_COIL")][5] == "prior", by[("RU", "C_COIL")]
    assert by[("CN", "C_DOIL")][2] == 1.2 and by[("RU", "C_GASE")][2] == 1.0
    assert by[("DE", "C_COIL")][5] == "data" and sum(1 for r in rs if r[:2] == ("DE", "C_COIL")) == 1
    assert 0.9 < russia_gas({"RU": 27.4e6}) < 1.1
    print("supply-stocks.py selftest ok")


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        selftest()
    else:
        main()
