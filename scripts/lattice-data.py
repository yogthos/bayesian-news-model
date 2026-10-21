#!/usr/bin/env python3
"""The downloaded sources the lattice reads, as tidy tables (research
tooling; needs pandas, openpyxl, pyreadstat):

  data/derived/lattice-annual.csv     iso,year,var,value
  data/derived/lattice-quarterly.csv  iso,q,var,value   (q = 4 year + quarter - 1)

Annual, each a raw level the lattice stretches against the country's own
decade or differences itself:
  bvx_bank_ret, bvx_nonfin_ret   real total returns on bank and nonfinancial
                                 equity (Baron, Verner & Xiong 2021)
  bvx_bank_crash                 a 30% fall in bank equity (C_B30)
  mv_unproductive                log of household and non-tradable credit
                                 (households, construction, trade,
                                 accommodation, real estate) over tradable
                                 credit (agriculture, mining, manufacturing)
                                 (Mueller & Verner 2023)
  epwt_profit_rate, epwt_labour_share  (Extended Penn World Table 7.0)
  wid_top10, wid_bottom50        pre-tax national income shares; wid_top1_wealth
  gdd_household, gdd_corporate, gdd_government  debt, % of GDP (IMF)
  romer_distress                 the year's mean of the semiannual 0-15
                                 financial distress scale (Romer & Romer 2017)
Quarterly:
  oecd_price_income, oecd_price_rent  house prices to disposable income and
                                 to rents (OECD analytical house prices)
"""
import json, glob, os
import pandas as pd

RAW = "data/raw/slow"
out_a, out_q = [], []

def add(rows, df, iso, t, var, col):
    d = df[[iso, t, col]].dropna()
    rows.extend((r[0], int(r[1]), var, float(r[2])) for r in d.itertuples(index=False))

# ISO2 -> ISO3 from WDI's own records
iso2 = {}
for f in glob.glob(f"{RAW}/worldbank/*.json")[:3]:
    data = json.load(open(f))
    for rec in (data[1] if isinstance(data, list) and len(data) > 1 else []):
        if rec.get("countryiso3code") and rec.get("country", {}).get("id"):
            iso2[rec["country"]["id"]] = rec["countryiso3code"]

bvx = pd.read_stata(f"{RAW}/bvx/replication kit/data/BVX_annual_regdata.dta")
for var, col in [("bvx_bank_ret", "Rtot_real"), ("bvx_nonfin_ret", "Rtot_nonfin_real"), ("bvx_bank_crash", "C_B30")]:
    add(out_a, bvx, "ISO3", "year", var, col)

mv = pd.read_stata(f"{RAW}/mullerverner/ReStud_Replication_Kit_20230912/Build/MuellerVerner.dta")
mv = mv.groupby(["country", "year"], as_index=False).last()
def pair(a, b, ab):
    # a sector pair, or the combined column some countries report instead
    both = mv[a] + mv[b]
    return both.where(both.notna(), mv[ab])
unprod = mv["HH"] + pair("F", "L", "FL") + pair("G", "I", "GI")
prod = mv["A"] + pair("B", "C", "BC")
mv["unprod"] = (unprod / prod).where((unprod > 0) & (prod > 0))
mv["unprod"] = mv["unprod"].apply(lambda x: None if pd.isna(x) else __import__("math").log(x))
add(out_a, mv, "country", "year", "mv_unproductive", "unprod")

ep = pd.read_excel(f"{RAW}/epwt/EPWT_7.0_FV.xlsx", sheet_name="EPWT7.0")
add(out_a, ep, "Countrycode", "Year", "epwt_profit_rate", "rnat2017")
add(out_a, ep, "Countrycode", "Year", "epwt_labour_share", "LabShare")

# "NA" is Namibia, not a missing value; KS is Kosovo
w = pd.read_csv(f"{RAW}/wid/wid_shares_extract.csv", keep_default_na=False, na_values={"value": [""]})
w["value"] = pd.to_numeric(w["value"], errors="coerce")
w["iso"] = w["country"].map(lambda c: iso2.get(c, "XKX" if c == "KS" else None))
for var, v, p in [("wid_top10", "sptincj992", "p90p100"), ("wid_bottom50", "sptincj992", "p0p50"),
                  ("wid_top1_wealth", "shwealj992", "p99p100")]:
    add(out_a, w[(w["variable"] == v) & (w["percentile"] == p)], "iso", "year", var, "value")

g = pd.read_csv(f"{RAW}/imf-gdd/gdd_long.csv")
for var, ind in [("gdd_household", "HH_ALL"), ("gdd_corporate", "NFC_ALL"), ("gdd_government", "GG_DEBT_GDP")]:
    add(out_a, g[g["indicator"] == ind], "iso3", "year", var, "value_pct_gdp")

names = {"Australia": "AUS", "Austria": "AUT", "Belgium": "BEL", "Canada": "CAN", "Denmark": "DNK",
         "Finland": "FIN", "France": "FRA", "Germany": "DEU", "Greece": "GRC", "Iceland": "ISL",
         "Ireland": "IRL", "Italy": "ITA", "Japan": "JPN", "Luxembourg": "LUX", "Netherlands": "NLD",
         "New Zealand": "NZL", "Norway": "NOR", "Portugal": "PRT", "Spain": "ESP", "Sweden": "SWE",
         "Switzerland": "CHE", "Turkey": "TUR", "United Kingdom": "GBR", "United States": "USA"}
rr = pd.read_excel(f"{RAW}/romer/Data and Programs/Romer&RomerCrisesData.xlsx", sheet_name="New Meas. of Financial Distress")
rr["year"] = rr["Date"].astype(str).str.slice(0, 4).astype(int)
long = rr.melt(id_vars=["Date", "year"], var_name="name", value_name="v").dropna()
# the workbook's headers drop the spaces ("UnitedStates")
squash = {k.replace(" ", ""): v for k, v in names.items()}
long["iso"] = long["name"].astype(str).str.replace(" ", "").map(squash)
yr = long.dropna(subset=["iso"]).groupby(["iso", "year"], as_index=False)["v"].mean()
add(out_a, yr, "iso", "year", "romer_distress", "v")

o = pd.read_csv(f"{RAW}/oecd-housing/house_prices.csv")
o = o[(o["FREQ"] == "Q") & (o["MEASURE"].isin(["HPI_YDH", "HPI_RPI"]))]
def qindex(s):
    y, q = s.split("-Q")
    return 4 * int(y) + int(q) - 1
o["q"] = o["TIME_PERIOD"].map(qindex)
for var, m in [("oecd_price_income", "HPI_YDH"), ("oecd_price_rent", "HPI_RPI")]:
    add(out_q, o[o["MEASURE"] == m], "REF_AREA", "q", var, "OBS_VALUE")

os.makedirs("data/derived", exist_ok=True)
pd.DataFrame(out_a, columns=["iso", "year", "var", "value"]).to_csv("data/derived/lattice-annual.csv", index=False)
pd.DataFrame(out_q, columns=["iso", "q", "var", "value"]).to_csv("data/derived/lattice-quarterly.csv", index=False)
a = pd.DataFrame(out_a, columns=["iso", "year", "var", "value"])
print(a.groupby("var").agg(countries=("iso", "nunique"), first=("year", "min"), last=("year", "max"), n=("value", "size")))
q = pd.DataFrame(out_q, columns=["iso", "q", "var", "value"])
print(q.groupby("var").agg(countries=("iso", "nunique"), n=("value", "size")))
print(len(iso2), "ISO2 codes mapped")
