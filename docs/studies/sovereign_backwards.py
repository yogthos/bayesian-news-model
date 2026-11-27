"""bnm-7yg.6: why the sovereign inputs rank 2003-2022's defaults backwards
(registered in docs/CALIBRATION.md under that title). On a run's sovereign
year country rows at December origins 2003-2022, with sovereign_split.py's
inputs and causal pooled refit: rows, onsets, rate, the run's AUC, each
input's AUC alone with the theory's sign and the pooled logit's AUC, by
development fold, by time since the last known default and by the external
debt reading's age; then 2020-22's onsets with their inputs.

  python3 -I docs/studies/sovereign_backwards.py <repo> <run>
"""
import importlib.util
import math
import os
import sys
from collections import defaultdict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


sc = _load("scorecard")
fs = _load("food_split")
ss = _load("sovereign_split")

KNOWN_LAG = 18
SINCE = 1970
SIGNS = {"debt/external_gni": +1, "debt/government_gdp": +1, "debt/reserves_months": -1,
         "debt/current_account_gdp": -1, "economy/inflation": +1, "economy/gdp_growth": -1}
SHORT = {"debt/external_gni": "extdebt", "debt/government_gdp": "govdebt", "debt/reserves_months": "reserves",
         "debt/current_account_gdp": "curacct", "economy/inflation": "infl", "economy/gdp_growth": "growth"}
FOLDS = [(2003, 2006), (2007, 2009), (2010, 2012), (2013, 2019), (2020, 2022)]


def since_band(onsets, place, t):
    """'none', '0-8' or '9+': years since the place's last default known at t
    (onset month + KNOWN_LAG <= t), counting onsets from SINCE."""
    known = [m for m in onsets.get(place, []) if m // 12 >= SINCE and m + KNOWN_LAG <= t]
    if not known:
        return "none"
    return "0-8" if (t - max(known)) // 12 <= 8 else "9+"


def signed_auc(ps, sign):
    ok = [(sign * x, y) for x, y in ps if not math.isnan(x)]
    if not any(y for _, y in ok) or all(y for _, y in ok):
        return float("nan")
    return sc.auc(ok)


def age(s, t):
    last = fs.latest(s, t) if s else None
    return None if last is None else t - last[0]


def read_onsets(repo):
    out = defaultdict(list)
    for line in open(os.path.join(repo, "data/derived/econ-events.csv")):
        k, u, m, *_ = line.rstrip("\n").split(",")
        if k == "sovereign_default":
            out[u].append(int(m))
    return out


def main(argv):
    repo, run = argv[0], argv[1]
    rows = {k: v for k, v in sc.read(repo, run, ss.KIND).items() if k[1] // 12 >= ss.TRAIN_FROM}
    series = fs.read_series(os.path.join(repo, "data/slow/series.tsv"), lambda u, v: v in ss.VARS)
    onsets = read_onsets(repo)
    keys = sorted(rows, key=lambda k: (k[1], k[0]))
    X = np.array([ss.features(series, u, o) for u, o in keys])
    y = np.array([bool(rows[k][1]) for k in keys])
    org = np.array([o for _, o in keys])
    p_run = np.array([rows[k][0] for k in keys])
    win = (org // 12 >= ss.FROM) & (org // 12 <= ss.TO)
    pooled = np.full(len(keys), float("nan"))
    for o in sorted(set(org[win])):
        tr, te = org <= o - ss.LAG, org == o
        if y[tr].sum() >= ss.MIN_ONSETS:
            pooled[te] = ss.predict(X[tr], y[tr].astype(float), X[te])
    band = np.array([since_band(onsets, u, o) for u, o in keys])
    ages = [age(series.get((u, "debt/external_gni")), o) for u, o in keys]
    stale = np.array(["none" if a is None else ("<=24m" if a <= 24 else ">24m") for a in ages])

    head = f"{'group':<18}{'rows':>6}{'pos':>5}{'rate':>7}{run:>8}{'pooled':>8}" + "".join(f"{SHORT[v]:>9}" for v in ss.VARS)
    def line(label, m):
        m = m & win & ~np.isnan(pooled)
        if not m.sum():
            return
        f = lambda v: signed_auc(list(zip(X[m, ss.VARS.index(v)], y[m])), SIGNS[v])
        cells = "".join(f"{f(v):9.3f}" for v in ss.VARS)
        print(f"{label:<18}{m.sum():6d}{int(y[m].sum()):5d}{y[m].mean():7.4f}"
              f"{sc.auc(list(zip(p_run[m], y[m]))):8.3f}{sc.auc(list(zip(pooled[m], y[m]))):8.3f}{cells}")

    print(f"sovereign year country rows, December origins {ss.FROM}-{ss.TO}, run {run}; AUCs, each input alone with the theory's sign")
    print(head)
    all_ = np.ones(len(keys), dtype=bool)
    line("all", all_)
    line("without 2020-22", org // 12 < 2020)
    print("1. by fold")
    for a, b in FOLDS:
        line(f"  {a}-{b}", (org // 12 >= a) & (org // 12 <= b))
    print("2. since the last known default")
    for g in ["none", "0-8", "9+"]:
        line(f"  {g}", band == g)
    print("3. external debt reading's age")
    for g in ["<=24m", ">24m", "none"]:
        line(f"  {g}", stale == g)
    print("4. onsets 2020-22 (inputs as read at the origin)")
    print(f"  {'place':<6}{'origin':>7}{run:>8}  " + "  ".join(f"{SHORT[v]:>8}" for v in ss.VARS) + "  since")
    for i in np.where(win & y & (org // 12 >= 2020))[0]:
        u, o = keys[i]
        print(f"  {u:<6}{o // 12:>7}{p_run[i]:8.3f}  " + "  ".join(f"{X[i, j]:8.1f}" for j in range(len(ss.VARS))) + f"  {band[i]}")


if __name__ == "__main__":
    main(sys.argv[1:])
