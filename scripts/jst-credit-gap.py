"""The BIS credit-to-GDP gap on the Macrohistory panel (bnm-0n8.5).

For each country and year, the gap is credit to GDP minus its one-sided
Hodrick-Prescott trend: the filter run on the data up to that year only,
its last point taken (Drehmann & Tsatsaronis 2014), so the gap is known in
real time. The smoothing parameter is the BIS's 400,000 for quarterly data,
scaled to annual by Ravn & Uhlig's fourth power: 400000 / 4^4 = 1562.5.
The reference implementation is statsmodels' hpfilter.

    uv run --no-project --with pandas --with statsmodels==0.15.0 python -I \
        scripts/jst-credit-gap.py data/raw/slow/jst/jst.csv data/derived/jst-credit-gap.csv
"""
import sys
import warnings

import pandas as pd
from statsmodels.tsa.filters.hp_filter import hpfilter

warnings.simplefilter("ignore")
LAMBDA = 400000 / 4 ** 4

d = pd.read_csv(sys.argv[1])
d["ratio"] = 100.0 * d.tloans / d.gdp
rows = []
for iso, g in d.sort_values("year").groupby("iso"):
    g = g[["year", "ratio"]].dropna()
    years, vals = g.year.to_numpy(), g.ratio.to_numpy()
    for i in range(len(vals)):
        if i < 9:
            continue
        _, trend = hpfilter(vals[:i + 1], lamb=LAMBDA)
        rows.append({"iso": iso, "year": int(years[i]), "gap": vals[i] - trend[-1]})
pd.DataFrame(rows).to_csv(sys.argv[2], index=False)
print("wrote", sys.argv[2], len(rows), file=sys.stderr)
