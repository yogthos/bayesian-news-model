"""bnm-7yg.8.8 (R3, registered in docs/CALIBRATION.md, "R2 and R3
registration"): the hierarchical challenger (docs/studies/hierarchical.py)
judged once at the kinds R1 flagged combination, against t162's issued p,
R1's stack, reference logit, median and log pools (all with the frailty's
offsets) and the clim/own baselines; December origins 2003-2022, year
country rows; subgroups by era and by the data-rich/poor halves (reported).

Verdict per kind, against t162: the log-score gain's interval at the
Bonferroni level over the three kinds (1 - 0.10/3) above 0, the Brier gain's
point estimate above 0, and the alert cost (p >= 0.2, a miss costing 4)
not significantly worse (its difference's 90% interval reaching 0 or
below). The challenger joins the 2027-Q1 issue as a reported shadow if it
passes; if R1's winner at the kind (the stack at conflict and war onsets,
the log pool at growth collapses) passes the same test too, the one with
the higher pooled log score joins; if only R1's winner passes, it joins.

  python3 -I docs/studies/judge.py <repo> <tap-dir> <challenger-dir> [k1,k2]
"""
import importlib.util
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
KINDS = ["conflict_onset", "war_onset", "growth_collapse"]
FALLBACK = {"conflict_onset": "stack", "war_onset": "stack", "growth_collapse": "log pool"}
LEVEL = 1 - 0.10 / len(KINDS)
THRESHOLD, MISS = 0.2, 4.0


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


cb = _load("combination")


def row_cost(p, y):
    return np.where(y & (p < THRESHOLD), MISS, 0.0) + np.where(~y & (p >= THRESHOLD), 1.0, 0.0)


def align(path, place, origin):
    got = {}
    with open(path) as fh:
        for l in fh:
            u, o, p = l.rstrip("\n").split("\t")
            got[(u, int(o))] = float(p)
    return np.array([got.get((u, int(o)), np.nan) for u, o in zip(place, origin)])


def passes(r):
    return r["log"][1] > 0 and r["brier"] > 0 and r["cost"][1] <= 0


def verdict(res, fallback):
    ok = [nm for nm in ("challenger", fallback) if nm and nm in res and passes(res[nm])]
    if not ok:
        return None
    return max(ok, key=lambda nm: res[nm]["pooled log"])


def against(q, sel, y, years, mask, level):
    m = mask & ~np.isnan(q) & ~np.isnan(sel)
    lg = cb.row_log(q[m], y[m]) - cb.row_log(sel[m], y[m])
    br = (sel[m] - y[m]) ** 2 - (q[m] - y[m]) ** 2
    cd = row_cost(q[m], y[m]) - row_cost(sel[m], y[m])
    return {"log": cb.block_ci(lg, years[m], level=level), "brier": float(br.mean()) if m.any() else float("nan"),
            "cost": cb.block_ci(cd, years[m]), "pooled log": float(cb.row_log(q[m], y[m]).mean()) if m.any() else float("nan"),
            "n": int(m.sum())}


def judge_kind(repo, tap, chal, kind):
    d = cb.with_issued(cb.read(os.path.join(tap, kind + ".tsv")),
                       cb.read_issued(os.path.join(repo, "data/runs/t162", kind, "forecasts.tsv")))
    y, years = d["y"], d["origin"] // 12
    win = (years >= cb.FROM) & (years <= cb.TO)
    fc = {"challenger": align(os.path.join(chal, kind + ".tsv"), d["place"], d["origin"]),
          "stack": cb.stack_forecast_osz(d), "reference": cb.reference_forecast_osz(d),
          "median pool": cb.median_pool_osz(d["E"], d["live"], d["osz"]),
          "log pool": cb.log_pool_osz(d["E"], d["live"], d["osz"])}
    allrows = {(u, int(o)): (float(p), bool(t)) for u, o, p, t in zip(d["place"], d["origin"], d["p"], y)}
    clim, own = cb.sc.baselines(allrows)
    for nm, b in (("clim", clim), ("own", own)):
        fc[nm] = np.array([b[(u, int(o))][0] if (u, int(o)) in b else np.nan for u, o in zip(d["place"], d["origin"])])
    sel = d["p"]
    places = lambda m: [d["place"][i] for i in np.where(m)[0]]
    out = {"kind": kind, "rows": int(win.sum()), "onsets": int(y[win].sum())}
    out["scores"] = {nm: cb.summary(q[win & ~np.isnan(q)], y[win & ~np.isnan(q)], d["origin"][win & ~np.isnan(q)], places(win & ~np.isnan(q)))
                     for nm, q in dict(fc, **{"t162": sel}).items()}
    for nm, q in dict(fc, **{"t162": sel}).items():
        m = win & ~np.isnan(q)
        out["scores"][nm]["cost"] = float(row_cost(q[m], y[m]).mean())
    out["vs t162"] = {nm: against(q, sel, y, years, win, LEVEL) for nm, q in fc.items()}
    ch = fc["challenger"]
    out["challenger vs"] = {nm: against(ch, q, y, years, win, 0.90) for nm, q in fc.items() if nm != "challenger"}
    share = (np.nan_to_num(d["X"], nan=0.0) != 0).mean(1)
    med = float(np.median(share[win]))
    subs = {"2003-12": years <= 2012, "2013-22": years >= 2013, "data-rich": share > med, "data-poor": share <= med}
    out["subgroups"] = {sn: against(ch, sel, y, years, win & sm, 0.90) for sn, sm in subs.items()}
    out["verdict"] = verdict(out["vs t162"], FALLBACK.get(kind))
    return out


def fmt(c):
    return f"{c[0]:+.5f} [{c[1]:+.5f}, {c[2]:+.5f}]"


def report(r):
    print(f"\n== {r['kind']}: rows {r['rows']}, onsets {r['onsets']}; joins the 2027-Q1 issue as a reported shadow: {r['verdict'] or 'nothing'}")
    print(f"   {'forecast':<12} {'log':>9} {'Brier':>8} {'AUC':>6} {'within':>6} {'p/rate':>6} {'cost':>6}")
    for nm, s in r["scores"].items():
        print(f"   {nm:<12} {s['log']:+.5f} {s['brier']:.5f} {s['auc']:.3f} {s['within']:.3f} {s['p/rate']:.2f} {s['cost']:.3f}")
    print(f"   against t162 (log at {LEVEL:.4f}, alert cost difference at 0.90; cost below 0 is better):")
    for nm, a in r["vs t162"].items():
        print(f"   {nm:<12} n {a['n']:>5}  log {fmt(a['log'])}  Brier {a['brier']:+.5f}  cost {fmt(a['cost'])}{'  PASSES' if passes(a) else ''}")
    print("   the challenger against each (90%):")
    for nm, a in r["challenger vs"].items():
        print(f"   {nm:<12} log {fmt(a['log'])}  Brier {a['brier']:+.5f}  cost {fmt(a['cost'])}")
    print("   the challenger against t162 by subgroup (90%):")
    for sn, a in r["subgroups"].items():
        print(f"   {sn:<12} n {a['n']:>5}  log {fmt(a['log'])}  Brier {a['brier']:+.5f}  cost {fmt(a['cost'])}")


def main(argv):
    repo, tap, chal = argv[:3]
    for k in (argv[3].split(",") if len(argv) > 3 else KINDS):
        report(judge_kind(repo, tap, chal, k))
        sys.stdout.flush()


if __name__ == "__main__":
    main(sys.argv[1:])
