"""A kind's year country rows at December origins 2003-2022, by origin year:
rows with an own-kind onset in the last 0-4 years ("recent") against the
rest, each group's onset rate and each run's mean forecast. Shows when the
recent rows' rate runs above the rest's (what a post-onset level learns
from) and whether the runs follow it. Diagnosis after t146 (bnm-7ny).

  python3 -I docs/studies/aftermath/recent_by_year.py <repo> <onsets.tsv> <kind> run [run ...]
"""
import os, sys
from collections import defaultdict

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from aftermath_strata import read_onsets, years_since  # noqa: E402


def rows(repo, run, kind, ons):
    out = {}
    for line in open(os.path.join(repo, "data/runs", run, kind, "forecasts.tsv")):
        s, place, o, p, y = line.rstrip("\n").split("\t")
        o = int(o)
        if s != "year" or len(place) != 3 or y not in ("true", "false") or o % 12 != 11 or not (2003 <= o // 12 <= 2022):
            continue
        ys = years_since(ons.get(place, []), o)
        out[(place, o)] = (ys is not None and ys <= 4, float(p), y == "true")
    return out


def main():
    repo, path, kind, runs = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
    ons = read_onsets(path)[kind]
    by_run = {r: rows(repo, r, kind, ons) for r in runs}
    keys = set.intersection(*(set(v) for v in by_run.values()))
    print("%s: recent = an onset in the last 0-4 years; p = mean forecast" % kind)
    print("%-6s | %-5s %-6s %s | %-5s %-6s %s" % ("year", "n", "rate", " ".join("%-8s" % r[:8] for r in runs),
                                                  "n", "rate", " ".join("%-8s" % r[:8] for r in runs)))
    print("       | recent" + " " * (13 + 9 * len(runs) - 7) + "| rest")
    tot = defaultdict(lambda: [0, 0, [0.0] * len(runs)])
    for yr in range(2003, 2023):
        o = 12 * yr + 11
        cells = []
        for rec in (True, False):
            ks = [k for k in keys if k[1] == o and by_run[runs[0]][k][0] == rec]
            n = len(ks)
            rate = sum(by_run[runs[0]][k][2] for k in ks) / n if n else float("nan")
            ps = [sum(by_run[r][k][1] for k in ks) / n if n else float("nan") for r in runs]
            cells.append("%-5d %-6.3f %s" % (n, rate, " ".join("%-8.3f" % p for p in ps)))
            t = tot[rec]
            t[0] += n
            t[1] += sum(by_run[runs[0]][k][2] for k in ks)
            for i, r in enumerate(runs):
                t[2][i] += sum(by_run[r][k][1] for k in ks)
        print("%-6d | %s | %s" % (yr, cells[0], cells[1]))
    cells = []
    for rec in (True, False):
        n, pos, ps = tot[rec]
        cells.append("%-5d %-6.3f %s" % (n, pos / n, " ".join("%-8.3f" % (p / n) for p in ps)))
    print("%-6s | %s | %s" % ("all", cells[0], cells[1]))


if __name__ == "__main__":
    main()
