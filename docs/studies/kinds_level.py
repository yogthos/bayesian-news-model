"""Per kind, year country nodes, December origins 2003-2022: pooled and
within-year AUC, and how the yearly mean p tracks the yearly rate (Pearson
r of the 20 yearly pairs), for each run given; and the frailty's chosen rho
at the year country node. python3 -I kinds_level.py <repo> run1 run2 ..."""
import sys, os, bisect, math
from collections import defaultdict, Counter

repo, runs = sys.argv[1], sys.argv[2:]

def rows_of(run, kind):
    out = []
    for line in open(os.path.join(repo, "data/runs", run, kind, "forecasts.tsv")):
        s, place, o, p, y = line.rstrip("\n").split("\t")
        o = int(o)
        if s != "year" or len(place) != 3 or y not in ("true", "false") or o % 12 != 11 or not (2003 <= o // 12 <= 2022):
            continue
        out.append((o // 12, float(p), y == "true"))
    return out

def auc(ps):
    pos = [p for p, y in ps if y]; neg = sorted(p for p, y in ps if not y)
    if not pos or not neg: return float("nan")
    return sum(bisect.bisect_left(neg, p) + 0.5 * (bisect.bisect_right(neg, p) - bisect.bisect_left(neg, p)) for p in pos) / (len(pos) * len(neg))

def within(rs):
    by = defaultdict(list)
    for y, p, o in rs: by[y].append((p, o))
    num = den = 0.0
    for ps in by.values():
        a = sum(1 for _, o in ps if o); b = len(ps) - a
        if a and b: num += auc(ps) * a * b; den += a * b
    return num / den if den else float("nan")

def level_r(rs):
    by = defaultdict(list)
    for y, p, o in rs: by[y].append((p, o))
    mp = [sum(p for p, _ in v) / len(v) for _, v in sorted(by.items())]
    rt = [sum(o for _, o in v) / len(v) for _, v in sorted(by.items())]
    n = len(mp); a = sum(mp) / n; b = sum(rt) / n
    sa = math.sqrt(sum((x - a) ** 2 for x in mp)); sb = math.sqrt(sum((x - b) ** 2 for x in rt))
    return sum((x - a) * (y - b) for x, y in zip(mp, rt)) / (sa * sb) if sa and sb else float("nan")

def rho(run, kind):
    path = os.path.join(repo, "data/runs", run, kind, "frailty.tsv")
    if not os.path.exists(path): return "-"
    c = Counter()
    for line in open(path):
        f = line.split("\t")
        if f[0] == "year" and f[1] == "country" and int(f[2]) >= 12 * 2000:
            c["%s/%s" % (f[5], f[6])] += 1
    return c.most_common(1)[0][0] if c else "-"

kinds = sorted(k for k in os.listdir(os.path.join(repo, "data/runs", runs[-1])) if os.path.isdir(os.path.join(repo, "data/runs", runs[-1], k)))
print("%-21s" % "kind" + "".join("| %-34s" % r for r in runs) + "| rho/sd (last run)")
print("%-21s" % "" + "".join("| %-34s" % "AUC pooled  within  level-r" for _ in runs))
for k in kinds:
    cells = []
    for r in runs:
        rs = rows_of(r, k)
        cells.append("| %.3f       %.3f   %+.2f      " % (auc([(p, o) for _, p, o in rs]), within(rs), level_r(rs)))
    print("%-21s" % k + "".join(cells) + "| " + rho(runs[-1], k))
