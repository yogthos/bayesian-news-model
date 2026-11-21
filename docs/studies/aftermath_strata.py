"""Rank 0 of reports/Crisis aftermath and trend reversal.md: per kind, the
year country rows at December origins 2003-2022 bucketed by whole years
since the kind's last onset at or before the origin (0-1, 2-4, 5-8, 9+,
never), with each bucket's rows, mean forecast and onset rate. A bucket
whose mean p sits well above its rate is aftermath read as risk (post-crisis
bias); below it, recurrence the forecast misses.

  python3 -I docs/studies/aftermath_strata.py <repo> <onsets.tsv> run [run ...]

onsets.tsv: kind, unit, onset month (12*year + month-1), every kind's
catalogue onsets (world-run's run-catalogues under --at-risk catalogue,
banking's own lb/catalogue).
"""
import bisect
import os
import sys
from collections import defaultdict

BANDS = ["0-1", "2-4", "5-8", "9+", "never"]


def years_since(onsets, origin):
    """Whole years from the last onset at or before `origin` (sorted months), or None."""
    i = bisect.bisect_right(onsets, origin)
    return None if i == 0 else (origin - onsets[i - 1]) // 12


def bucket(y):
    if y is None:
        return "never"
    return "0-1" if y <= 1 else "2-4" if y <= 4 else "5-8" if y <= 8 else "9+"


def table(rows):
    """{band: (rows, mean p, onset rate)} of (years since, p, outcome) rows."""
    acc = defaultdict(list)
    for y, p, o in rows:
        acc[bucket(y)].append((p, o))
    return {b: (len(v), sum(p for p, _ in v) / len(v), sum(1 for _, o in v if o) / len(v)) for b, v in acc.items()}


def read_onsets(path):
    out = defaultdict(lambda: defaultdict(list))
    for line in open(path):
        k, u, m = line.rstrip("\n").split("\t")
        out[k][u].append(int(m))
    for k in out:
        for u in out[k]:
            out[k][u].sort()
    return out


def rows_of(repo, run, kind, ons):
    for line in open(os.path.join(repo, "data/runs", run, kind, "forecasts.tsv")):
        s, place, o, p, y = line.rstrip("\n").split("\t")
        o = int(o)
        if s != "year" or len(place) != 3 or y not in ("true", "false") or o % 12 != 11 or not (2003 <= o // 12 <= 2022):
            continue
        yield years_since(ons.get(place, []), o), float(p), y == "true"


def main():
    repo, path, runs = sys.argv[1], sys.argv[2], sys.argv[3:]
    ons = read_onsets(path)
    kinds = sorted(d for d in os.listdir(os.path.join(repo, "data/runs", runs[0])) if os.path.isdir(os.path.join(repo, "data/runs", runs[0], d)))
    print("%-21s %-6s" % ("kind", "run") + "".join("| %-21s" % b for b in BANDS))
    print("%-28s" % "" + "".join("| %-21s" % "rows   p      rate" for _ in BANDS))
    for k in kinds:
        for r in runs:
            t = table(list(rows_of(repo, r, k, ons[k])))
            cells = ["| %-5d %.3f  %.3f  " % t[b] if b in t else "| %-21s" % "" for b in BANDS]
            print("%-21s %-6s" % (k, r[:6]) + "".join(cells))


if __name__ == "__main__":
    main()
