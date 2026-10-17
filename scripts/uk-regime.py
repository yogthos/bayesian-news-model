"""The US regime terms on independent data: UK share price falls, 1881-2016
(docs/REGIMES.md, bnm-t5b.16.3).

The terms were chosen on US data after seeing its era table, so the US
held-out result carries hindsight. This applies the same fixed forms,
unchanged, to the Bank of England's "A millennium of macroeconomic data":
  tightening x wage share     12-month change in Bank Rate times the wage
                              share's deviation from its previous 20 years
  unemployment x wage share   12-month change in unemployment times the same
                              (the US used payroll growth, whose sign is the
                              opposite: falling unemployment when labour's
                              share is high should precede falls)
  credit x low wage share     3-year growth of bank lending times minus the
                              wage share's deviation (realization on credit)
alone and with their raw parts, by era, against a 20% fall in the spliced
monthly share price index within 12 months. The wage share (compensation
of employees over GDP(I) at factor cost) for year Y is used from April of
Y+2; monthly series are read a month late.

    uv run --no-project --with pandas --with openpyxl --with scikit-learn==1.9.1 python -I \
        scripts/uk-regime.py data/raw/slow/boe/millennium.xlsx
"""
import sys

import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score

MONTHS = {m: i for i, m in enumerate(["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"])}


def monthly(f, sheet, col, start_row):
    d = pd.read_excel(f, sheet_name=sheet, header=None)
    years = pd.to_numeric(d.iloc[start_row:, 0], errors="coerce").ffill()
    months = d.iloc[start_row:, 1].map(lambda m: MONTHS.get(str(m).strip()[:3]))
    vals = pd.to_numeric(d.iloc[start_row:, col], errors="coerce")
    s = pd.Series(vals.values, index=(years * 12 + months).values)
    s = s[~pd.isna(s.index)]
    s.index = s.index.astype(int)
    return s.dropna().groupby(level=0).last()


def annual(f, sheet, col, start_row):
    d = pd.read_excel(f, sheet_name=sheet, header=None)
    years = pd.to_numeric(d.iloc[start_row:, 0], errors="coerce")
    vals = pd.to_numeric(d.iloc[start_row:, col], errors="coerce")
    s = pd.Series(vals.values, index=years.values).dropna()
    s = s[~pd.isna(s.index)]
    s.index = s.index.astype(int)
    return s


if __name__ == "__main__":
    f = sys.argv[1]
    price = monthly(f, "M13. Mthly share prices 1709+ ", 23, 4)
    bank = monthly(f, "M9. Mthly short-term rates", 2, 7)
    unemp = monthly(f, "M5. Administrative unemp 1881+", 6, 8)
    credit = monthly(f, "M8. Mthly credit aggs 1921+", 15, 5)
    comp = annual(f, "A17. GDP(I) components", 6, 5)
    gdpi = annual(f, "A17. GDP(I) components", 48, 5)
    ws = (comp / gdpi).dropna()
    ws_dev = (ws - ws.shift(1).rolling(20, min_periods=10).mean()).dropna()

    months = range(1881 * 12, 2017 * 12)
    lp = np.log(price)
    rows = []
    for t in months:
        if t not in lp.index:
            continue
        fut = [lp.get(t + k) for k in range(1, 13)]
        if any(v is None or np.isnan(v) for v in fut):
            continue
        y = min(fut) - lp[t] <= np.log(0.8)
        s = t - 1
        yr = (t // 12) - 2 if t % 12 >= 3 else (t // 12) - 3  # year Y known from April of Y+2
        wd = ws_dev.get(yr)
        tight = bank.get(s) - bank.get(s - 12) if s in bank.index and (s - 12) in bank.index else None
        du = unemp.get(s) - unemp.get(s - 12) if s in unemp.index and (s - 12) in unemp.index else None
        cr3 = (np.log(credit[s]) - np.log(credit[s - 36])) if s in credit.index and (s - 36) in credit.index else None
        rows.append({"t": t, "y": y, "wage_dev": wd, "tightening": tight, "unemployment12": du, "credit3": cr3,
                     "tightening_x_wage": None if tight is None or wd is None else tight * wd,
                     "unemployment_x_wage": None if du is None or wd is None else du * wd,
                     "credit_x_low_wage": None if cr3 is None or wd is None else -cr3 * wd})
    d = pd.DataFrame(rows)
    d["year"] = d.t // 12
    eras = [("1881-1913", 1881, 1913), ("1919-1949", 1919, 1949), ("1950-1979", 1950, 1979), ("1980-2016", 1980, 2016)]
    expect = {"tightening_x_wage": ">0.5 (US 0.82, 0.74, 0.63)", "unemployment_x_wage": "<0.5 (US payrolls x wage 0.79, 0.71, 0.63)",
              "credit_x_low_wage": ">0.5 (US 0.71, 0.31, 0.70)"}
    print("UK: univariate AUC against a 20% fall in share prices within 12 months, by era")
    print(f"  {'input':22s} " + "  ".join(e[0] for e in eras) + "   expected from the US")
    for c in ["tightening", "unemployment12", "credit3", "wage_dev", "tightening_x_wage", "unemployment_x_wage", "credit_x_low_wage"]:
        out = []
        for _, lo, hi in eras:
            q = d[(d.year >= lo) & (d.year <= hi) & d[c].notna()]
            out.append(f"{roc_auc_score(q.y, q[c]):.2f}" if q.y.sum() >= 5 and (~q.y).sum() >= 5 else "  - ")
        print(f"  {c:22s} " + "       ".join(out) + "   " + expect.get(c, ""))
    print("\npositive months by era:", [int(d[(d.year >= lo) & (d.year <= hi)].y.sum()) for _, lo, hi in eras])
