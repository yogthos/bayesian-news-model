#!/usr/bin/env python3
"""The Penn World Table's columns the lattice reads, from the full .dta
(data/raw/slow/pwt/pwt110.dta) to data/raw/slow/pwt/pwt110-full.csv:
output, the capital stock, TFP, the labour share, the investment share,
the relative price of investment, human capital, employment, depreciation.
Research tooling (needs pandas)."""
import pandas as pd
cols = ["countrycode", "year", "rgdpna", "rkna", "rtfpna", "labsh", "csh_i", "pl_i", "hc", "emp", "delta"]
df = pd.read_stata("data/raw/slow/pwt/pwt110.dta", columns=cols)
df.to_csv("data/raw/slow/pwt/pwt110-full.csv", index=False)
print(len(df), "rows,", df["countrycode"].nunique(), "countries,", int(df["year"].min()), "-", int(df["year"].max()))
