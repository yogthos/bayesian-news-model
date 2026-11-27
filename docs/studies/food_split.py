"""bnm-7yg.6: food price shocks' global wave and domestic part, on a run's
food year country rows at December origins 2003-2022 (registered in
docs/CALIBRATION.md, "Diagnosis: food price shocks' global wave").

  1. the share of the outcomes' variance that is the origin's level,
     beyond binomial noise;
  2. the world FAO food index's twelve-month log change as published by
     the origin, against each origin's rate and the run's mean p;
  3. the Brier gain with each origin's level known (an oracle: logits
     shifted so the origin's mean p is its rate), and with a causal level
     model: logit(rate) - logit(mean p) regressed on the world change over
     origins resolved LAG months before, applied as a shift;
  4. the run's within-origin AUC against a country's own food inflation
     known at the origin, and that net of the world change.

  python3 -I docs/studies/food_split.py <repo> <run>
"""
import importlib.util
import math
import os
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("scorecard", os.path.join(HERE, "scorecard.py"))
sc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sc)

LAG = 48
FROM, TO = 2003, 2022
RIDGE = 1.0
EPS = 1e-6


def month_index(date):
    y, m = int(date[:4]), int(date[5:7])
    return y * 12 + m - 1


def logit(p):
    p = min(max(p, EPS), 1 - EPS)
    return math.log(p / (1 - p))


def expit(z):
    return 0.0 if z < -700 else 1 / (1 + math.exp(-z))


def read_series(path, wanted):
    """{(unit, var): [(period, known, value)]} for the (unit, var) pairs
    `wanted` accepts, sorted by period."""
    out = defaultdict(list)
    for line in open(path):
        f = line.rstrip("\n").split("\t")
        if len(f) < 6 or not wanted(f[0], f[1]):
            continue
        try:
            out[(f[0], f[1])].append((month_index(f[3]), month_index(f[4]), float(f[5])))
        except ValueError:
            continue
    return {k: sorted(v) for k, v in out.items()}


def latest(s, t):
    """The latest (period, value) published by month t, or None."""
    best = None
    for p, k, v in s:
        if k <= t and (best is None or p > best[0]):
            best = (p, v)
    return best


def change(s, t, months):
    """The log change over `months` of the latest period published by t,
    both periods published by t; None without them."""
    last = latest(s, t)
    if last is None:
        return None
    p, v = last
    prev = [w for q, k, w in s if q == p - months and k <= t]
    if not prev or prev[0] <= 0 or v <= 0:
        return None
    return math.log(v / prev[0])


def shifted(ps, a):
    return [expit(logit(p) + a) for p in ps]


def shift_to_rate(ps, rate):
    """The shift a with mean expit(logit(p) + a) = rate (bisection)."""
    lo, hi = -30.0, 30.0
    for _ in range(200):
        mid = (lo + hi) / 2
        if sum(shifted(ps, mid)) / len(ps) < rate:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def level_fit(rows, ridge=RIDGE):
    """rows [(x, d)]: d = a + b x by least squares, ridge on both (prior 0)."""
    n = len(rows)
    sx = sum(x for x, _ in rows); sy = sum(d for _, d in rows)
    sxx = sum(x * x for x, _ in rows); sxy = sum(x * d for x, d in rows)
    A = [[n + ridge, sx], [sx, sxx + ridge]]
    det = A[0][0] * A[1][1] - A[0][1] * A[1][0]
    if abs(det) < 1e-12:
        return 0.0, 0.0
    a = (A[1][1] * sy - A[0][1] * sxy) / det
    b = (A[0][0] * sxy - A[1][0] * sy) / det
    return a, b


def excess_share(by):
    """by {origin: [(p, y)]}: the between-origin variance of the rate beyond
    binomial noise, as a share of the outcomes' variance."""
    ys = [y for rs in by.values() for _, y in rs]
    yb = sum(ys) / len(ys)
    tot = yb * (1 - yb)
    rates = [(sum(y for _, y in rs) / len(rs), len(rs)) for rs in by.values()]
    mr = sum(r for r, _ in rates) / len(rates)
    var = sum((r - mr) ** 2 for r, _ in rates) / len(rates)
    noise = sum(r * (1 - r) / n for r, n in rates) / len(rates)
    return (var - noise) / tot if tot else float("nan")


def brier(rows):
    return sum((p - y) ** 2 for p, y in rows) / len(rows)


def main(argv):
    repo, run = argv[0], argv[1]
    rows = sc.read(repo, run, "food_price_shock")  # {(place, o): (p, y)}
    by = defaultdict(list)
    for (u, o), (p, y) in rows.items():
        if FROM <= o // 12 <= TO:
            by[o].append((u, p, y))
    series = read_series(os.path.join(repo, "data/slow/series.tsv"),
                         lambda u, v: (u == "world" and v == "food/price_index") or v == "food/food_inflation")
    world = series.get(("world", "food/price_index"), [])
    origins = sorted(by)
    w12 = {o: change(world, o, 12) for o in origins}
    rate = {o: sum(y for _, _, y in by[o]) / len(by[o]) for o in origins}
    meanp = {o: sum(p for _, p, _ in by[o]) / len(by[o]) for o in origins}

    print(f"food year country rows, December origins {FROM}-{TO}, run {run}")
    print(f"{'origin':>6} {'rows':>5} {'rate':>6} {'mean p':>7} {'world12':>8}")
    for o in origins:
        x = w12[o]
        print(f"{o // 12:>6} {len(by[o]):>5} {rate[o]:6.3f} {meanp[o]:7.3f} {('%+.3f' % x) if x is not None else 'none':>8}")

    share = excess_share({o: [(p, y) for _, p, y in by[o]] for o in origins})
    print(f"\n1. origin level's share of the outcomes' variance beyond binomial noise: {share:.3f}")
    have = [o for o in origins if w12[o] is not None]
    print(f"2. r(rate, world12) {sc.pearson([rate[o] for o in have], [w12[o] for o in have]):+.2f}  "
          f"r(mean p, world12) {sc.pearson([meanp[o] for o in have], [w12[o] for o in have]):+.2f}  "
          f"r(rate, mean p) {sc.pearson([rate[o] for o in origins], [meanp[o] for o in origins]):+.2f}  ({len(have)} origins with world12)")

    base = [(p, y) for o in origins for _, p, y in by[o]]
    oracle = [(q, y) for o in origins
              for q, (_, _, y) in zip(shifted([p for _, p, _ in by[o]], shift_to_rate([p for _, p, _ in by[o]], min(max(rate[o], 1e-3), 1 - 1e-3))), by[o])]
    causal, shifts = [], {}
    for o in origins:
        train = [(w12[q], logit(min(max(rate[q], 1e-3), 1 - 1e-3)) - logit(meanp[q]))
                 for q in origins if q <= o - LAG and w12[q] is not None]
        a, b = level_fit(train) if train else (0.0, 0.0)
        s = a + b * (w12[o] if w12[o] is not None else 0.0)
        shifts[o] = s
        causal += [(q, y) for q, (_, _, y) in zip(shifted([p for _, p, _ in by[o]], s), by[o])]
    b0 = brier(base)
    print(f"3. Brier {run} {b0:.5f}; oracle level {brier(oracle):.5f} (gain {b0 - brier(oracle):+.5f}); "
          f"causal level on world12 {brier(causal):.5f} (gain {b0 - brier(causal):+.5f})")
    print("   causal shifts: " + " ".join(f"{o // 12}:{s:+.2f}" for o, s in shifts.items()))

    own, net, run_rows = [], [], []
    for o in origins:
        for u, p, y in by[o]:
            s = series.get((u, "food/food_inflation"))
            last = latest(s, o) if s else None
            run_rows.append((o, u, p, y))
            if last is not None:
                own.append((o, u, last[1], y))
                if w12[o] is not None:
                    net.append((o, u, last[1] - 100 * w12[o], y))
    print(f"4. within-origin AUC: {run} {sc.within_auc(run_rows):.3f}; own food inflation known at the origin "
          f"{sc.within_auc(own):.3f} ({len(own)} rows); net of the world change {sc.within_auc(net):.3f}")
    keys = {(o, u) for o, u, _, _ in own}
    print(f"   {run} on those rows {sc.within_auc([r for r in run_rows if (r[0], r[1]) in keys]):.3f}")


if __name__ == "__main__":
    main(sys.argv[1:])
