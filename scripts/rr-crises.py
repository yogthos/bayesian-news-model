"""Reinhart-Rogoff global crisis data (HBS BFFS) to onsets: one row per
country and year with 1 where a banking, currency, sovereign external
default or inflation crisis starts (the first year of a run of 1s), and
whether the country is on the gold standard that year.

    python3 scripts/rr-crises.py in.xlsx out.csv   (needs pandas, openpyxl)
"""
import sys
import pandas as pd

src, dst = sys.argv[1], sys.argv[2]
d = pd.read_excel(src).iloc[1:]
cols = {"Banking Crisis ": "banking", "Currency Crises": "currency", "Inflation Crises": "inflation",
        [c for c in d.columns if c.startswith("SOVEREIGN EXTERNAL DEBT 1")][0]: "default"}
d = d[["CC3", "Year", "Gold Standard"] + list(cols)].rename(columns=cols)
d = d[d.CC3.notna() & d.Year.notna()]
d["Year"] = d.Year.astype(int)
out = []
for cc, g in d.sort_values("Year").groupby("CC3"):
    prev = {k: 0 for k in cols.values()}
    for _, r in g.iterrows():
        gold = pd.to_numeric(r["Gold Standard"], errors="coerce")
        row = {"iso": cc, "year": r.Year, "gold": "" if pd.isna(gold) else int(gold)}
        for k in cols.values():
            v = pd.to_numeric(r[k], errors="coerce")
            on = 1 if v == 1 else 0
            row[k] = 1 if (on and not prev[k]) else 0
            row[k + "_known"] = 0 if pd.isna(v) else 1
            prev[k] = on
        out.append(row)
pd.DataFrame(out).to_csv(dst, index=False)
