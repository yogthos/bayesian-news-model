"""bnm-7yg.6: which part of sovereign defaults' issued year country forecast
ranks 2003-2022's defaults backwards (registered in docs/CALIBRATION.md).
On sovereign_parts.clj's taps of two runs: per run, pooled and by time since
the last known default, the AUC of the issued log odds, of the mean offset o,
of the history group's departure from it and of each history input, and the
parts' means by outcome.

  python3 -I docs/studies/sovereign_parts.py <repo> <label=tsv> [label=tsv ...]
"""
import importlib.util
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


sb = _load("sovereign_backwards")
HIST = ["since-last", "own10", "own30"]
BANDS = ["all", "none", "0-8", "9+"]
PARTS = ["issued", "o", "departure"]


def expit(x):
    return 1.0 / (1.0 + math.exp(-x))


def month(year):
    return 12 * year + 11


def _num(s):
    return float(s) if s != "" else float("nan")


def read_rows(lines):
    it = iter(lines)
    head = next(it).split("\t")
    groups = [h[2:] for h in head if h.startswith("f_")]
    out = []
    for line in it:
        if not line.strip():
            continue
        r = dict(zip(head, line.rstrip("\n").split("\t")))
        out.append({"place": r["place"], "year": int(r["year"]), "y": r["y"] == "1", "o": float(r["o"]),
                    "f": {g: float(r["f_" + g]) for g in groups},
                    "w": {g: float(r["w_" + g]) for g in groups},
                    "h": {h: _num(r.get(h, "")) for h in HIST}})
    return out


def parts(r):
    p = sum(r["w"][g] * expit(r["f"][g]) for g in r["f"])
    p = min(max(p, 1e-9), 1 - 1e-9)
    return {"issued": math.log(p / (1 - p)), "o": r["o"], "departure": r["f"].get("history", float("nan")) - r["o"]}


def table(rows, onsets):
    """{band: {measure: auc}} and {band: (rows, onsets)}."""
    by = {b: [] for b in BANDS}
    for r in rows:
        by["all"].append(r)
        by[sb.since_band(onsets, r["place"], month(r["year"]))].append(r)
    aucs, counts = {}, {}
    for b, rs in by.items():
        counts[b] = (len(rs), sum(r["y"] for r in rs))
        ps = [parts(r) for r in rs]
        a = {k: sb.signed_auc([(p[k], r["y"]) for p, r in zip(ps, rs)], +1) for k in PARTS}
        a.update({h: sb.signed_auc([(r["h"][h], r["y"]) for r in rs], +1) for h in HIST})
        means = {}
        for k in PARTS:
            for yv in (True, False):
                xs = [p[k] for p, r in zip(ps, rs) if r["y"] == yv]
                means[(k, yv)] = sum(xs) / len(xs) if xs else float("nan")
        aucs[b] = (a, means)
    return aucs, counts


def main(argv):
    repo, runs = argv[0], [a.split("=", 1) for a in argv[1:]]
    onsets = sb.read_onsets(repo)
    for label, path in runs:
        rows = read_rows(open(path).read().splitlines())
        aucs, counts = table(rows, onsets)
        print(f"== {label}: AUC (higher = ranks defaults higher), by years since last known default")
        print("band   rows ons  " + " ".join(f"{k:>9}" for k in PARTS + HIST))
        for b in BANDS:
            a, _ = aucs[b]
            print(f"{b:5} {counts[b][0]:5} {counts[b][1]:3}  " + " ".join(f"{a[k]:9.3f}" for k in PARTS + HIST))
        print("means (onset / no onset):")
        for b in BANDS:
            _, m = aucs[b]
            print(f"{b:5}  " + "  ".join(f"{k} {m[(k, True)]:+.2f}/{m[(k, False)]:+.2f}" for k in PARTS))
        w = {}
        for r in rows:
            for g, v in r["w"].items():
                w[g] = w.get(g, 0.0) + v / len(rows)
        print("mean weights: " + "  ".join(f"{g} {v:.2f}" for g, v in sorted(w.items(), key=lambda kv: -kv[1])[:4]))


if __name__ == "__main__":
    main(sys.argv[1:])
