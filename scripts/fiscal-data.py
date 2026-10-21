#!/usr/bin/env python3
"""Fiscal series from the Global Macro Database (Mueller, Xu, Lehbib & Chen
2025; data/raw/slow/fiscal/GMD_2026_09.dta, research use, not re-hosted),
research tooling, needs pandas:

  data/derived/fiscal.csv    iso,year,var,value
    gov_exp_gdp, gov_rev_gdp, gov_bal_gdp   general government expenditure,
                                            revenue and balance (deficit
                                            negative), % of GDP
    gov_exp_real                            expenditure deflated by the GDP
                                            deflator (local currency)
    gmd_sovereign                           1 in a year of sovereign debt crisis

Years after 2025 are the release's projections and are left out.
"""
import pandas as pd

d = pd.read_stata("data/raw/slow/fiscal/GMD_2026_09.dta")
d = d[d["year"] <= 2025]
out = []
def add(var, s):
    x = d[["ISO3", "year"]].assign(v=s).dropna()
    out.extend((r.ISO3, int(r.year), var, float(r.v)) for r in x.itertuples(index=False))
add("gov_exp_gdp", d["govexp_GDP"])
add("gov_rev_gdp", d["govrev_GDP"])
add("gov_bal_gdp", d["govdef_GDP"])
add("gov_exp_real", d["govexp"] / d["deflator"].where(d["deflator"] > 0))
add("gmd_sovereign", d["SovDebtCrisis"].where(d["SovDebtCrisis"] == 1))
o = pd.DataFrame(out, columns=["iso", "year", "var", "value"])
o.to_csv("data/derived/fiscal.csv", index=False)
print(o.groupby("var").agg(countries=("iso", "nunique"), first=("year", "min"), last=("year", "max"), n=("value", "size")).to_string())
