"""Does nonlinearity help the US drawdown hazard? (bnm-t5b.15.2)

Gradient-boosted trees (scikit-learn's HistGradientBoostingClassifier)
against a logistic regression on the same inputs, both refit each January
on the months whose 12-month outcome is known, scored on 1950-1989 and
1990 on. The literature's expectation (Beutel, List & von Schweinitz 2019)
is that the logit holds up better out of sample.

    uv run --no-project --with scikit-learn==1.9.1 --with pandas python -I scripts/nonlinear-check.py \
        data/runs/trader-inputs/inputs.tsv
"""
import sys
import warnings

import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer

warnings.simplefilter("ignore")
d = pd.read_csv(sys.argv[1], sep="\t")
logw = np.cumsum(np.log1p(d.mkt.to_numpy()))
n = len(d)
# a 20% fall below this month's level within the next 12 months
y = np.full(n, np.nan)
for i in range(n - 12):
    y[i] = float(np.min(logw[i + 1:i + 13]) - logw[i] <= np.log(0.8))
d["y"] = y
d["year"] = d.month // 12
sets = {
    "base": ["cape", "mom12", "sma10", "vol6", "vol-ratio", "drawdown12", "cape-x-drawdown", "cape-x-decel",
             "cape-x-below-sma"],
}
sets["base+regime+oil+small"] = sets["base"] + ["hmm-plain", "oil12", "small-scale"]
sets["+invariants"] = sets["base+regime+oil+small"] + ["us-norm", "us-breadth", "us-resonance", "us-flow-norm",
                                                       "us-stock-x-flow"]
models = {
    "logit": lambda: make_pipeline(SimpleImputer(), StandardScaler(), LogisticRegression(C=0.1, max_iter=2000)),
    "boosted trees": lambda: HistGradientBoostingClassifier(max_depth=3, learning_rate=0.05, max_iter=200,
                                                           min_samples_leaf=40, l2_regularization=1.0),
}
for sname, cols in sets.items():
    for mname, make in models.items():
        preds = []
        for year in range(1950, int(d.year.max()) + 1):
            train = d[(d.month + 12 < year * 12) & d.y.notna() & (d.year >= 1930)]
            test = d[(d.year == year) & d.y.notna()]
            if len(test) == 0 or train.y.sum() < 5:
                continue
            # inputs not yet observed in the training years are left out of that year's fit
            use = [c for c in cols if train[c].nunique() > 1]
            m = make().fit(train[use].to_numpy(), train.y.to_numpy())
            preds.append(pd.DataFrame({"year": test.year, "p": m.predict_proba(test[use].to_numpy())[:, 1], "y": test.y}))
        p = pd.concat(preds)
        auc = lambda q: roc_auc_score(q.y, q.p) if q.y.nunique() > 1 else float("nan")
        print(f"{sname:24s} {mname:14s} 1950-89 {auc(p[p.year < 1990]):.3f}  1990- {auc(p[p.year >= 1990]):.3f}  2000- {auc(p[p.year >= 2000]):.3f}")
