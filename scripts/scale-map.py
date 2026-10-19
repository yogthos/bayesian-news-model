#!/usr/bin/env python3
"""Which method works at which scale: one markdown table from an event-model
report (skill against persistence by node level, and escalation AUC in quiet
places) and onset anticipation files (recall gain over history on hard rows by
window and level).

    scripts/scale-map.py --report data/runs/v6-q/report.txt \
        --onsets data/runs/r3-pooled [--models mlpool,pool,hedge,named]

Reads <run>/anticipation.txt, anticipation-subregion.txt and
anticipation-region.txt where they exist."""
import argparse, os, re
from collections import defaultdict

LEVELS = ["dyad", "pair", "actor", "province", "country", "subregion", "region", "world"]


def event_rows(path):
    """level -> (mean skill %, mean quiet-row AUC model, history)."""
    skill, auc = defaultdict(list), defaultdict(list)
    for line in open(path):
        m = re.match(r"\s+(\w+)\s+[\w-]+\s+n=\s*\d+\s+err [\d. ]+skill\s+([+-][\d.]+)%", line)
        if m:
            skill[m.group(1)].append(float(m.group(2)))
        m = re.match(r"\s+(\w+)\s+[\w-]+\s+quiet\s+n=.*auc ([\d.]+)/([\d.]+)", line)
        if m:
            auc[m.group(1)].append((float(m.group(2)), float(m.group(3))))
    out = {}
    for lv in LEVELS:
        if lv in skill or lv in auc:
            s = sum(skill[lv]) / len(skill[lv]) if skill[lv] else None
            a = auc[lv]
            out[lv] = (s, (sum(x for x, _ in a) / len(a), sum(y for _, y in a) / len(a)) if a else None)
    return out


num = r"([+-]?\d+\.\d+)"
cell = re.compile(r"r@10% " + num + " " + num + r" \[" + num + " " + num + r"\]")


def onset_rows(path, models):
    """(kind, window, era) -> model -> (r@10, gain, lo, hi), hard rows only."""
    out, block = defaultdict(dict), None
    for line in open(path):
        if not line.startswith(" "):
            block = line.split()
            continue
        if not block or block[-1] != "hard":
            continue
        name = line.split()[0]
        c = cell.search(line)
        if c and name in models:
            out[(block[0], block[1], block[2])][name] = tuple(float(c.group(i)) for i in range(1, 5))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report")
    ap.add_argument("--onsets")
    ap.add_argument("--models", default="mlpool,pool,hedge,named")
    a = ap.parse_args()
    models = a.models.split(",")
    if a.report:
        print("Event model (%s): mean skill of the forecast mean against persistence over channels;"
              " escalation AUC in quiet places, model / own history.\n" % os.path.basename(os.path.dirname(a.report)))
        print("| level | skill vs persistence | quiet escalation AUC |")
        print("|---|---|---|")
        for lv, (s, auc) in event_rows(a.report).items():
            print("| %s | %s | %s |" % (lv, "%+.1f%%" % s if s is not None else "-",
                                        "%.2f / %.2f" % auc if auc else "-"))
        print()
    if a.onsets:
        files = [("country", "anticipation.txt"), ("subregion", "anticipation-subregion.txt"),
                 ("region", "anticipation-region.txt")]
        tables = {lv: onset_rows(os.path.join(a.onsets, f), models)
                  for lv, f in files if os.path.exists(os.path.join(a.onsets, f))}
        print("Onsets (%s), hard rows: recall in the top 10%% and its gain over history"
              " [90%% interval]; * when the interval excludes zero.\n" % os.path.basename(a.onsets))
        for m in models:
            print("**%s**\n" % m)
            print("| kind | window | era | " + " | ".join(tables) + " |")
            print("|---|---|---|" + "---|" * len(tables))
            keys = sorted(set(k for t in tables.values() for k in t))
            for k in keys:
                cells = []
                for lv in tables:
                    v = tables[lv].get(k, {}).get(m)
                    cells.append("-" if not v else "%.2f %+.2f [%+.2f %+.2f]%s" % (v + ("*" if v[2] > 0 else "",)))
                print("| %s | %s | %s | %s |" % (k + (" | ".join(cells),)))
            print()


if __name__ == "__main__":
    main()
