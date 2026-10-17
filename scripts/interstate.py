"""Uneven development and interstate conflict (docs/REGIMES.md, bnm-t5b.16.4).

Lenin's law of uneven development, and power transition theory after it
(Organski & Kugler 1980), say war comes from shifts in relative strength.
With the Correlates of War national capabilities (NMC 6.0, CINC, 1816-2016)
and militarized interstate disputes (MID 5.0, 1816-2014):

  monadic   each state-year: does the state initiate (originator, side A) a
            dispute reaching the use of force (hostility level 4 or more)
            in the next year? Inputs in invariant form: its CINC share's
            growth over ten years, its share's stretch against its own
            twenty years, and the system's concentration change.
  dyadic    each year's pairs among the eight most capable states: does a
            dispute with the use of force between them start within two
            years? Inputs: parity (smaller share over larger), and the
            speed of the shift in their ratio over ten years.

Each input's AUC by era; an input is stable if it is on the same side of
0.5 in every era.

    uv run --no-project --with pandas --with scikit-learn==1.9.1 python -I scripts/interstate.py \
        data/raw/slow/cow/nmc/NMC-60-abridged.csv "data/raw/slow/cow/mid/MIDB 5.0.csv"
"""
import itertools
import sys

import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score

ERAS = [("1870-1913", 1870, 1913), ("1919-1938", 1919, 1938), ("1946-1989", 1946, 1989), ("1990-2013", 1990, 2013)]


def era_table(d, target, cols, title):
    print(title)
    print(f"  {'input':26s} " + "  ".join(e[0] for e in ERAS) + "   positives")
    for c in cols:
        out, pos = [], []
        for _, lo, hi in ERAS:
            q = d[(d.year >= lo) & (d.year <= hi) & d[c].notna() & d[target].notna()]
            pos.append(int(q[target].sum()))
            out.append(f"{roc_auc_score(q[target], q[c]):.2f}" if q[target].sum() >= 5 and (q[target] == 0).sum() >= 5 else "  - ")
        aucs = [float(x) for x in out if x.strip() != "-"]
        stable = "stable" if aucs and (all(a > 0.5 for a in aucs) or all(a < 0.5 for a in aucs)) else ""
        print(f"  {c:26s} " + "       ".join(out) + f"   {pos}  {stable}")


if __name__ == "__main__":
    nmc = pd.read_csv(sys.argv[1])[["ccode", "year", "cinc"]].dropna()
    mid = pd.read_csv(sys.argv[2])
    mid = mid[mid.hostlev >= 4]
    # monadic: initiation of a forceful dispute
    init = set(zip(mid[(mid.orig == 1) & (mid.sidea == 1)].ccode, mid[(mid.orig == 1) & (mid.sidea == 1)].styear))
    nmc = nmc.sort_values(["ccode", "year"])
    g = nmc.groupby("ccode")
    nmc["lcinc"] = np.log(nmc.cinc.clip(lower=1e-6))
    nmc["growth10"] = nmc.lcinc - g.lcinc.shift(10)
    nmc["stretch20"] = (nmc.lcinc - g.lcinc.transform(lambda s: s.shift(1).rolling(20, min_periods=10).mean())) / \
        g.lcinc.transform(lambda s: s.shift(1).rolling(20, min_periods=10).std())
    conc = nmc.groupby("year").cinc.apply(lambda s: (s ** 2).sum())
    nmc["concentration_change10"] = nmc.year.map(conc - conc.shift(10))
    nmc["rank"] = nmc.groupby("year").cinc.rank(ascending=False)
    nmc["initiates"] = [1 if (c, y + 1) in init else 0 for c, y in zip(nmc.ccode, nmc.year)]
    era_table(nmc, "initiates", ["growth10", "stretch20", "concentration_change10", "lcinc"],
              "Monadic: a state initiating a dispute with the use of force next year")

    # dyadic: the eight most capable states
    sides = mid.groupby(["dispnum", "sidea"]).ccode.apply(set)
    starts = mid.groupby("dispnum").styear.min()
    fights = set()
    for disp in mid.dispnum.unique():
        a, b = sides.get((disp, 1), set()), sides.get((disp, 0), set())
        for x in a:
            for y in b:
                fights.add((min(x, y), max(x, y), starts[disp]))
    cinc = {(c, y): v for c, y, v in zip(nmc.ccode, nmc.year, nmc.cinc)}
    rows = []
    for year, grp in nmc.groupby("year"):
        top = grp.nlargest(8, "cinc").ccode.tolist()
        for x, y in itertools.combinations(sorted(top), 2):
            cx, cy = cinc[(x, year)], cinc[(y, year)]
            px, py = cinc.get((x, year - 10)), cinc.get((y, year - 10))
            parity = min(cx, cy) / max(cx, cy)
            shift = abs(np.log(cx / cy) - np.log(px / py)) if px and py else None
            # the challenger closing on the stronger: positive when the weaker grows relatively
            closing = None
            if px and py:
                weak, strong = (x, y) if cx < cy else (y, x)
                closing = (np.log(cinc[(weak, year)] / cinc[(strong, year)]) -
                           np.log(cinc[(weak, year - 10)] / cinc[(strong, year - 10)]))
            fight = int(any((x, y, s) in fights for s in (year + 1, year + 2)))
            rows.append({"year": year, "parity": parity, "shift10": shift, "closing10": closing, "fight": fight})
    dy = pd.DataFrame(rows)
    print()
    era_table(dy, "fight", ["parity", "shift10", "closing10"],
              "Dyadic: a dispute with the use of force within two years between two of the eight most capable states")
