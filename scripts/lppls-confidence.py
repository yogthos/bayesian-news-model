"""LPPLS bubble confidence for the US market (docs/INVARIANTS.md), from the
reference implementation in the lppls package (Boulder Investment
Technologies; Sornette's log-periodic power law singularity model and the
multi-scale confidence indicator of Demirer, Demos, Gupta & Sornette 2019).

Monthly log wealth from French's market returns. At each month, nested
windows from 120 down to 24 months ending at that month are fitted; the
positive (negative) confidence is the share of fits that qualify as a
bubble (anti-bubble). Each month's value uses only data up to it.

    uv run --no-project --with lppls==0.6.24 python -I scripts/lppls-confidence.py \
        data/runs/trader-inputs/inputs.tsv data/runs/trader-inputs/lppls.tsv
"""
import sys

import numpy as np
import pandas as pd
from lppls import lppls

if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    d = pd.read_csv(src, sep="\t")
    price = np.exp(np.cumsum(np.log1p(d.mkt.to_numpy())))
    t = d.month.to_numpy().astype(float)
    obs = np.array([t, np.log(price)])
    model = lppls.LPPLS(observations=obs)
    res = model.mp_compute_nested_fits(workers=8, window_size=120, smallest_window_size=24,
                                       outer_increment=1, inner_increment=6, max_searches=25)
    ind = model.compute_indicators(res)
    out = pd.DataFrame({"month": ind["time"].astype(int), "pos_conf": ind["pos_conf"], "neg_conf": ind["neg_conf"]})
    out.to_csv(dst, sep="\t", index=False)
    print("wrote", dst, len(out), file=sys.stderr)
