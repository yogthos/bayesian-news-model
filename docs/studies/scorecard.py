"""bnm-7yg.1: the scorecard the 26 November review asked for, per kind, on
the year country nodes at December origins 2003-2022 (the rows bnm.alerts
reads), for each run given and for two causal baselines read from the
first run's outcomes:

  clim  the rate over every country row whose outcome had resolved by the
        origin (origins at least LAG months before; (n1+0.5)/(n+1)),
  own   the country's own resolved rate, shrunk to clim by SHRINK rows.

Columns: rows, onsets; pooled AUC; within-origin AUC (pairs within one
origin only: country ranking, not timing); average precision; mean p over
the rate; level r (the 20 origins' mean p against their rate); top decile's
mean p and rate; Brier skill and log score per row against clim and against
own; recall when each origin alerts its top 10% of countries; alert cost at
p >= 0.2 with a miss at 4 false alarms, and the cheaper of never or always
alerting. The last line per run pools the kinds with equal weight.

  python3 -I docs/studies/scorecard.py <repo> run1 [run2 ...] [--kinds k1,k2] [--places|--outside iso.csv]

Every run is scored on the rows all of them have (a reference run issues
only 2003-2022); run1 also supplies the baselines' history. --places keeps
the countries in the csv's iso column, --outside the rest.
"""
import bisect
import math
import os
import sys
from collections import defaultdict

LAG = 48       # months: a 24-month window and up to 18 months' catalogue lag, at December origins
SHRINK = 5.0   # pseudo-rows of climatology in the own-history rate
BUDGET = 0.10
THRESHOLD = 0.2
MISS = 4
FROM, TO = 2003, 2022
KINDS = ["banking_crisis", "conflict_onset", "coup", "currency_crisis", "fiscal_consolidation",
         "food_price_shock", "growth_collapse", "inflation_crisis", "market_crash", "protest_surge",
         "sovereign_default", "violence_surge", "war_onset"]


def auc(ps):
    pos = [p for p, y in ps if y]
    neg = sorted(p for p, y in ps if not y)
    if not pos or not neg:
        return float("nan")
    s = sum(bisect.bisect_left(neg, p) + 0.5 * (bisect.bisect_right(neg, p) - bisect.bisect_left(neg, p)) for p in pos)
    return s / (len(pos) * len(neg))


def within_auc(rows):
    """rows (origin, place, p, y): the AUC over pairs within one origin."""
    by = defaultdict(list)
    for o, _, p, y in rows:
        by[o].append((p, y))
    num = den = 0.0
    for ps in by.values():
        a = auc(ps)
        if not math.isnan(a):
            n = sum(1 for _, y in ps if y) * sum(1 for _, y in ps if not y)
            num += a * n
            den += n
    return num / den if den else float("nan")


def avg_precision(ps):
    ranked = sorted(ps, key=lambda r: -r[0])
    hits, total = 0, 0.0
    for i, (_, y) in enumerate(ranked, 1):
        if y:
            hits += 1
            total += hits / i
    return total / hits if hits else float("nan")


def climatology(hist, t):
    """hist {(place, origin): y}: the rate over the rows resolved by origin t."""
    ys = [y for (_, o), y in hist.items() if o <= t - LAG]
    return (sum(ys) + 0.5) / (len(ys) + 1)


def own_history(hist, place, t, clim):
    ys = [y for (u, o), y in hist.items() if u == place and o <= t - LAG]
    return (sum(ys) + SHRINK * clim) / (len(ys) + SHRINK)


def recall_at_budget(rows, share):
    """rows (origin, place, p, y): the onsets caught when each origin alerts
    its top `share` of rows (at least one), ties broken by order."""
    by = defaultdict(list)
    for o, _, p, y in rows:
        by[o].append((p, y))
    caught = pos = 0
    for ps in by.values():
        k = max(1, round(share * len(ps)))
        top = sorted(ps, key=lambda r: -r[0])[:k]
        caught += sum(1 for _, y in top if y)
        pos += sum(1 for _, y in ps if y)
    return caught / pos if pos else float("nan")


def cost(ps, t, miss):
    misses = sum(1 for p, y in ps if y and p < t)
    fa = sum(1 for p, y in ps if not y and p >= t)
    return (miss * misses + fa) / len(ps)


def skill(score, ref):
    return 1.0 - score / ref


def brier(ps):
    return sum((p - y) ** 2 for p, y in ps) / len(ps)


def loglik(ps):
    return sum(math.log(min(max(p, 1e-6), 1 - 1e-6) if y else 1 - min(max(p, 1e-6), 1 - 1e-6)) for p, y in ps) / len(ps)


def pearson(xs, ys):
    n = len(xs)
    if n < 3:
        return float("nan")
    mx, my = sum(xs) / n, sum(ys) / n
    sx = math.sqrt(sum((x - mx) ** 2 for x in xs))
    sy = math.sqrt(sum((y - my) ** 2 for y in ys))
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / (sx * sy) if sx and sy else float("nan")


def read(repo, run, kind):
    """{(place, origin): (p, y)} for every resolved December country row."""
    out = {}
    for line in open(os.path.join(repo, "data/runs", run, kind, "forecasts.tsv")):
        s, place, o, p, y = line.rstrip("\n").split("\t")
        o = int(o)
        if s == "year" and len(place) == 3 and y in ("true", "false") and o % 12 == 11:
            out[(place, o)] = (float(p), y == "true")
    return out


def baselines(all_rows):
    """The clim and own baselines {(place, origin): (p, y)} at the window's
    rows, from every resolved row (cumulative counts, as climatology and
    own_history compute them)."""
    hist = {k: y for k, (_, y) in all_rows.items()}
    by_o = defaultdict(lambda: [0, 0])
    by_u = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    for (u, o), y in hist.items():
        by_o[o][0] += y; by_o[o][1] += 1
        by_u[u][o][0] += y; by_u[u][o][1] += 1
    def upto(counts, t):
        n1 = n = 0
        for o, (a, b) in counts.items():
            if o <= t - LAG:
                n1 += a; n += b
        return n1, n
    clim, own, cache = {}, {}, {}
    for (u, o) in all_rows:
        if not (FROM <= o // 12 <= TO):
            continue
        if o not in cache:
            n1, n = upto(by_o, o)
            cache[o] = (n1 + 0.5) / (n + 1)
        c = cache[o]
        a, b = upto(by_u[u], o)
        clim[(u, o)] = (c, hist[(u, o)])
        own[(u, o)] = ((a + SHRINK * c) / (b + SHRINK), hist[(u, o)])
    return clim, own


def metrics(rows, clim, own, keys=None):
    """rows {(place, origin): (p, y)} in the window, scored on `keys` (by
    default every key clim has)."""
    keys = [k for k in (clim if keys is None else keys) if k in rows and k in clim]
    ps = [rows[k] for k in keys]
    rs = [(k[1], k[0], rows[k][0], rows[k][1]) for k in keys]
    cs = [clim[k] for k in keys]
    ow = [own[k] for k in keys]
    by = defaultdict(list)
    for o, _, p, y in rs:
        by[o].append((p, y))
    origins = sorted(by)
    mp = [sum(p for p, _ in by[o]) / len(by[o]) for o in origins]
    rt = [sum(y for _, y in by[o]) / len(by[o]) for o in origins]
    top = sorted(ps, key=lambda r: -r[0])[:max(1, len(ps) // 10)]
    pos = sum(y for _, y in ps)
    return {
        "rows": len(ps), "pos": pos,
        "auc": auc(ps), "within": within_auc(rs), "ap": avg_precision(ps),
        "p/rate": (sum(p for p, _ in ps) / len(ps)) / (pos / len(ps)) if pos else float("nan"),
        "level r": pearson(mp, rt),
        "top p": sum(p for p, _ in top) / len(top), "top rate": sum(y for _, y in top) / len(top),
        "BSS clim": skill(brier(ps), brier(cs)), "log clim": loglik(ps) - loglik(cs),
        "BSS own": skill(brier(ps), brier(ow)), "log own": loglik(ps) - loglik(ow),
        "rec@10%": recall_at_budget(rs, BUDGET),
        "cost": cost(ps, THRESHOLD, MISS),
        "const": min(MISS * pos, len(ps) - pos) / len(ps),
    }


COLS = [("rows", "{:5d}"), ("pos", "{:4d}"), ("auc", "{:.3f}"), ("within", "{:.3f}"), ("ap", "{:.3f}"),
        ("p/rate", "{:5.2f}"), ("level r", "{:+.2f}"), ("top p", "{:.3f}"), ("top rate", "{:.3f}"),
        ("BSS clim", "{:+.3f}"), ("log clim", "{:+.4f}"), ("BSS own", "{:+.3f}"), ("log own", "{:+.4f}"),
        ("rec@10%", "{:.3f}"), ("cost", "{:.3f}"), ("const", "{:.3f}")]


def line(label, m):
    cells = []
    for c, f in COLS:
        v = m[c]
        cells.append(f"{'nan':>{len(f.format(0.0 if 'd' not in f else 0))}}" if isinstance(v, float) and math.isnan(v) else f.format(v))
    return f"{label:<32}" + "  ".join(cells)


def main(argv):
    repo, rest = argv[0], argv[1:]
    kinds = KINDS
    if "--kinds" in rest:
        i = rest.index("--kinds")
        kinds = rest[i + 1].split(",")
        rest = rest[:i] + rest[i + 2:]
    places, invert = None, False
    for flag in ("--places", "--outside"):
        if flag in rest:
            i = rest.index(flag)
            import csv
            places = {r["iso"] for r in csv.DictReader(open(rest[i + 1]))}
            invert = flag == "--outside"
            rest = rest[:i] + rest[i + 2:]
    runs = rest
    print(f"scorecard (bnm-7yg.1): year country nodes, December origins {FROM}-{TO}; clim/own causal baselines "
          f"(outcomes resolved {LAG} months before the origin; own shrunk by {SHRINK:g} rows); "
          f"top 10% alerted per origin; cost at p >= {THRESHOLD} with a miss at {MISS}")
    print(f"{'kind / run':<32}" + "  ".join(f"{c:>{len(f.format(0.0 if 'd' not in f else 0))}}" for c, f in COLS))
    pooled = defaultdict(lambda: defaultdict(list))
    for k in kinds:
        first = read(repo, runs[0], k)
        clim, own = baselines(first)
        read_runs = [(r, read(repo, r, k)) for r in runs]
        # every run on the same rows: those each one has, in the place filter
        keys = [key for key in clim if all(key in rows for _, rows in read_runs)
                and (places is None or ((key[0] in places) != invert))]
        for label, rows in [("clim", clim), ("own", own)] + read_runs:
            m = metrics(rows, clim, own, keys)
            print(line(f"{k[:20]} {label}", m))
            for c, _ in COLS:
                pooled[label][c].append(m[c])
        print()
    for label, cols in pooled.items():
        m = {c: (sum(v for v in vs if not (isinstance(v, float) and math.isnan(v))) /
                 max(1, sum(1 for v in vs if not (isinstance(v, float) and math.isnan(v)))))
             for c, vs in cols.items()}
        m["rows"], m["pos"] = int(sum(cols["rows"])), int(sum(cols["pos"]))
        print(line(f"pooled (kinds equal) {label}", m))


if __name__ == "__main__":
    main(sys.argv[1:])
