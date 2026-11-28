"""bnm-7yg.8.11 step 1 (registered in docs/CALIBRATION.md, "Target audit at
currency crises and sovereign defaults"): read-only. On R1's tap and the
sovereign catalogue's three sources: sovereign onsets by dating source and
era, and both kinds' input coverage at onset rows (share of non-zero
inputs; each group's presence, from the tap's groups file when it exists).

  python3 -I docs/studies/target_audit.py <repo> <tap-dir> <onsets.tsv>

onsets.tsv: kind, unit, onset month, the run's catalogues (as for
aftermath_strata.py).
"""
import csv
import importlib.util
import os
import sys
from collections import Counter, defaultdict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
FROM, TO, SPLIT = 2003, 2022, 2017


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def runs_start(ys):
    return {y for y in ys if y - 1 not in ys}


def dated_by(year, sources):
    """The sources with an onset within a year of `year`, in order."""
    return [s for s, ys in sources.items() if any(abs(year - y) <= 1 for y in ys)]


def nonzero_share(X):
    """Each row's share of non-zero inputs, the intercept (column 0) left out."""
    return (np.nan_to_num(X[:, 1:], nan=0.0) != 0).mean(1)


def present(X, ix):
    """Whether any of a group's inputs (positions in x, 0 the intercept) is non-zero."""
    cols = [i for i in ix if i != 0]
    return (np.nan_to_num(X[:, cols], nan=0.0) != 0).any(1) if cols else np.zeros(len(X), bool)


def sovereign_sources(repo):
    lv, rr, gmd = defaultdict(set), defaultdict(set), defaultdict(set)
    for r in csv.DictReader(open(os.path.join(repo, "data/derived/lv2018.csv"))):
        if r["kind"] in ("sovereign", "restructuring"):
            lv[r["iso"]].add(int(r["year"]))
    for r in csv.DictReader(open(os.path.join(repo, "data/raw/slow/rr/onsets.csv"))):
        if r["default"] == "1":
            rr[r["iso"]].add(int(r["year"]))
    g = defaultdict(set)
    for r in csv.DictReader(open(os.path.join(repo, "data/derived/fiscal.csv"))):
        if r["var"] == "gmd_sovereign":
            g[r["iso"]].add(int(r["year"]))
    for u, ys in g.items():
        gmd[u] = runs_start(ys)
    return lv, rr, gmd


def sovereign_report(repo, d, onsets_path):
    """Catalogue onsets 2004-2024 by dating source and year (the run's
    catalogue, `onsets_path`), and the year node's rate by era: its window
    is 24 months (bnm.lattice-banking's :year), so origins 2003-14 read
    onsets to 2016 (three sources) and origins 2016-22 onsets from 2017 (GMD alone)."""
    lv, rr, gmd = sovereign_sources(repo)
    ons = []
    for l in open(onsets_path):
        k, u, m = l.rstrip("\n").split("\t")
        if k == "sovereign_default" and 2004 <= int(m) // 12 <= 2024:
            ons.append((u, int(m) // 12))
    print("\n== sovereign_default: the run's catalogue onsets 2004-2024 by dating source")
    by = Counter(tuple(dated_by(y, {"LV": lv[u], "RR": rr[u], "GMD": gmd[u]})) for u, y in ons)
    for src, n in by.most_common():
        print(f"   {'+'.join(src) or 'none'}: {n}")
    per = defaultdict(Counter)
    for u, y in ons:
        per[y]["GMD only" if dated_by(y, {"LV": lv[u], "RR": rr[u], "GMD": gmd[u]}) == ["GMD"] else "LV/RR"] += 1
    print("   by year (LV/RR-dated, GMD only): " + " ".join(f"{y}:{per[y]['LV/RR']},{per[y]['GMD only']}" for y in range(2004, 2025)))
    yrs = d["origin"] // 12
    for nm, m in (("origins 2003-14 (onsets to 2016)", (yrs >= FROM) & (yrs <= 2014)),
                  ("origin 2015 (onsets 2016-17)", yrs == 2015),
                  ("origins 2016-22 (onsets from 2017, GMD alone)", (yrs >= 2016) & (yrs <= TO))):
        print(f"   {nm}: rows {int(m.sum())}, positive rows {int(d['y'][m].sum())}, rate {d['y'][m].mean():.4f}")


def coverage_report(tap, kind, d):
    yrs = d["origin"] // 12
    win = (yrs >= FROM) & (yrs <= TO)
    sh = nonzero_share(d["X"])
    y = d["y"]
    print(f"\n== {kind}: input coverage, December year country rows 2003-2022 ({int(win.sum())} rows, {int(y[win].sum())} onsets)")
    print(f"   share of non-zero inputs: all rows median {np.median(sh[win]):.3f}, onset rows median {np.median(sh[win & y]):.3f}")
    for lo, hi in ((0, 0.1), (0.1, 0.3), (0.3, 0.5), (0.5, 1.01)):
        m = win & (sh >= lo) & (sh < hi)
        print(f"   share {lo:.1f}-{min(hi, 1):.1f}: rows {int(m.sum()):>5}, onsets {int(y[m].sum()):>3}, rate {y[m].mean() if m.any() else float('nan'):.4f}")
    gpath = os.path.join(tap, kind + ".groups.tsv")
    if os.path.exists(gpath):
        groups = {g: [int(i) for i in ix.split(",") if i] for g, ix in (l.rstrip("\n").split("\t") for l in open(gpath))}
        print("   group present at: all rows / onset rows")
        for g, ix in sorted(groups.items()):
            if g in ("all", "base"):
                continue
            p = present(d["X"], ix)
            print(f"   {g:<14} {p[win].mean():.3f} / {p[win & y].mean():.3f}")
    else:
        print("   (no groups file: group presence not reported)")


def main(argv):
    repo, tap, onsets = argv[0], argv[1], argv[2]
    cb = _load("combination")
    for k in ("currency_crisis", "sovereign_default"):
        d = cb.read(os.path.join(tap, k + ".tsv"))
        if k == "sovereign_default":
            sovereign_report(repo, d, onsets)
        coverage_report(tap, k, d)


if __name__ == "__main__":
    main(sys.argv[1:])
