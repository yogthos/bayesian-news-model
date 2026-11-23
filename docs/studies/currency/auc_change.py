"""Where a kind's year AUC changed between two runs (bead bnm-7ny).
Usage: python3 -I auc_change.py <repo> <onsets.tsv> <kind> <bench> <run>
Year country rows, December origins 2003-2022. Prints, by band of years
since the kind's last onset (true onsets, so a diagnosis, not a feature),
the rows, onsets and mean p in each run, and each band's share of the AUC
change, counted twice (each column sums to the whole change): for an onset row, the change in the share of quiet rows it
outranks; for a quiet row, the change in the share of onset rows that
outrank it."""
import sys, os, bisect, collections

def years_since(ons, o):
    i = bisect.bisect_right(ons, o)
    return None if i == 0 else (o - ons[i - 1]) // 12

def band(y):
    return "never" if y is None else "0-1" if y <= 1 else "2-4" if y <= 4 else "5-8" if y <= 8 else "9+"

def rows(repo, run, kind):
    out = {}
    for line in open(os.path.join(repo, "data/runs", run, kind, "forecasts.tsv")):
        s, u, o, p, y = line.rstrip("\n").split("\t")
        o = int(o)
        if s == "year" and len(u) == 3 and y in ("true", "false") and o % 12 == 11 and 2003 <= o // 12 <= 2022:
            out[(u, o)] = (float(p), y == "true")
    return out

def main():
    repo, path, kind, bench, run = sys.argv[1:6]
    ons = collections.defaultdict(list)
    for line in open(path):
        k, u, m = line.rstrip("\n").split("\t")
        if k == kind: ons[u].append(int(m))
    for u in ons: ons[u].sort()
    a, b = rows(repo, bench, kind), rows(repo, run, kind)
    keys = sorted(set(a) & set(b))
    pos = [k for k in keys if a[k][1]]; neg = [k for k in keys if not a[k][1]]
    def auc(r):
        ps = sorted(r[k][0] for k in neg)
        tot = 0.0
        for k in pos:
            lo = bisect.bisect_left(ps, r[k][0]); hi = bisect.bisect_right(ps, r[k][0])
            tot += lo + 0.5 * (hi - lo)
        return tot / (len(pos) * len(neg))
    print("%s rows %d onsets %d  AUC %s %.4f  %s %.4f" % (kind, len(keys), len(pos), bench, auc(a), run, auc(b)))
    na, nb = sorted(a[k][0] for k in neg), sorted(b[k][0] for k in neg)
    pa, pb = sorted(a[k][0] for k in pos), sorted(b[k][0] for k in pos)
    frac = lambda xs, v: (bisect.bisect_left(xs, v) + bisect.bisect_right(xs, v)) / 2 / len(xs)
    agg = collections.defaultdict(lambda: [0, 0, 0.0, 0.0, 0.0, 0.0])
    for k in keys:
        g = band(years_since(ons.get(k[0], []), k[1]))
        r = agg[g]; r[0] += 1; r[2] += a[k][0]; r[3] += b[k][0]
        if a[k][1]:
            r[1] += 1; r[4] += (frac(nb, b[k][0]) - frac(na, a[k][0])) / len(pos)
        else:
            r[5] += ((1 - frac(pb, b[k][0])) - (1 - frac(pa, a[k][0]))) / len(neg)
    print("band   rows onsets  p %-9s p %-9s  AUC change from onset rows | from quiet rows" % (bench[:9], run[:9]))
    for g in ["0-1", "2-4", "5-8", "9+", "never"]:
        if g in agg:
            n, c, sa, sb, d, e = agg[g]
            print("%-6s %5d %5d   %.3f       %.3f        %+.4f | %+.4f" % (g, n, c, sa / n, sb / n, d, e))
    by = collections.defaultdict(lambda: [0, 0.0])
    for k in pos:
        r = by[k[1] // 12]; r[0] += 1; r[1] += frac(nb, b[k][0]) - frac(na, a[k][0])
    print("onset rows by origin year: n, mean change in share of quiet rows outranked")
    for yr, (n, d) in sorted(by.items()): print(yr, n, "%+.3f" % (d / n))

main()
