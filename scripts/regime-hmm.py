"""Discovered regimes for the US market (docs/INVARIANTS.md, step 4).

A two-state Markov-switching model of monthly market returns, mean and
variance switching (Hamilton 1989), from statsmodels' MarkovRegression,
in two forms:
  plain   fixed transition probabilities;
  tvtp    transition probabilities driven by displacement inputs, lagged a
          month (Filardo 1994; statsmodels' exog_tvtp): valuation, the
          drawdown from the 12-month high, the volatility ratio and momentum.
Refit each January on the months before it, then filtered forward through
the year with those parameters, so each month's probability uses only what
was known then. The bear regime is the high-variance one in each fit.

    uv run --no-project --with statsmodels==0.15.0 python -I scripts/regime-hmm.py \
        data/runs/trader-inputs/inputs.tsv data/runs/trader-inputs/regimes.tsv
"""
import sys
import warnings

import numpy as np
import pandas as pd
from statsmodels.tsa.regime_switching.markov_regression import MarkovRegression

warnings.simplefilter("ignore")
src, dst = sys.argv[1], sys.argv[2]
d = pd.read_csv(src, sep="\t")
d["year"] = d.month // 12
ret = (d.mkt - d.rf).to_numpy() * 100.0
inputs = ["cape", "drawdown12", "vol-ratio", "mom12"]
# transition into month t uses what was known at the end of t-1
z = d[inputs].shift(1)


def fit_filter(endog, exog, n_train):
    """Fit on the first n_train months, filter all of endog: P(bear) per month."""
    kw = {"k_regimes": 2, "trend": "c", "switching_variance": True}
    if exog is not None:
        kw["exog_tvtp"] = exog[:n_train]
    res = MarkovRegression(endog[:n_train], **kw).fit(disp=False, maxiter=200, search_reps=5)
    if exog is not None:
        kw["exog_tvtp"] = exog
    full = MarkovRegression(endog, **kw)
    probs = full.filter(res.params).filtered_marginal_probabilities
    probs = np.asarray(probs)
    names = full.param_names
    sig = [res.params[names.index("sigma2[%d]" % k)] for k in range(2)]
    return probs[:, int(np.argmax(sig))]


rows = []
first = 1940
for year in range(1950, int(d.year.max()) + 1):
    n_train = int((d.year < year).sum())
    upto = d.year <= year
    endog = ret[upto.to_numpy()]
    start = int((d.year < first).sum())
    endog = endog[start:]
    n = n_train - start
    out = {}
    for label in ["plain", "tvtp"]:
        exog = None
        if label == "tvtp":
            zz = z[upto].iloc[start:]
            mu, sd = zz.iloc[:n].mean(), zz.iloc[:n].std()
            zz = ((zz - mu) / sd).fillna(0.0)
            exog = np.column_stack([np.ones(len(zz)), zz.to_numpy()])
        try:
            out[label] = fit_filter(endog, exog, n)[n:]
        except Exception as e:  # a failed fit leaves the year blank
            print(year, label, "failed:", e, file=sys.stderr)
            out[label] = [np.nan] * (len(endog) - n)
    months = d.month[upto].to_numpy()[start:][n:]
    for i, m in enumerate(months):
        rows.append({"month": m, "plain": out["plain"][i], "tvtp": out["tvtp"][i]})
    print(year, file=sys.stderr, end=" ", flush=True)

pd.DataFrame(rows).to_csv(dst, sep="\t", index=False, na_rep="")
print("\nwrote", dst, file=sys.stderr)
