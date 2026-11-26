"""bnm-7yg.4: rolling, out-of-sample, intercept-only recalibration of a run's
year country nodes. At each December origin t the issued log odds L move by
a_t, the intercept that best fits

    y ~ expit(L + a)

over the rows whose outcomes had resolved by t (origins at least LAG
months before) within the trailing WINDOW years, with a N(0, PRIOR_SD^2)
prior on a. The ranking within an origin is unchanged; only the level moves.
(T8's online map, tools/bnm/recalibrate_online.clj, also learned a slope
and lost: an error that changes sign with each common shock.)

Writes a copy of the run with the named kinds' year country rows at
December origins FROM-TO recalibrated, every other row as issued, so
bnm.links and bnm.alerts score it against the run.

  python3 -I docs/studies/recalibrate_intercept.py <repo> <run> <out-run> k1,k2
"""
import math
import os
import shutil
import sys

LAG = 48
WINDOW = 10
PRIOR_SD = 1.0
FROM, TO = 2003, 2022
EPS = 1e-6


def logit(p):
    p = min(max(p, EPS), 1 - EPS)
    return math.log(p / (1 - p))


def expit(z):
    if z < -700:
        return 0.0
    return 1 / (1 + math.exp(-z))


def intercept(rows, prior_sd=PRIOR_SD, iters=50):
    """rows [(L, y)]: the MAP shift a under a N(0, prior_sd^2) prior."""
    a = 0.0
    for _ in range(iters * 4):
        ps = [expit(L + a) for L, _ in rows]
        g = sum(p - y for p, (_, y) in zip(ps, rows)) + a / prior_sd ** 2
        h = sum(p * (1 - p) for p in ps) + 1 / prior_sd ** 2
        step = max(-1.0, min(1.0, g / h))  # damped: plain Newton overshoots from far off
        a -= step
        if abs(step) < 1e-10:
            break
    return a


def shifts(rows):
    """rows {(place, origin): (p, y)} of every resolved December row: {origin: a}
    at the December origins FROM-TO."""
    by = {}
    for (_, o), (p, y) in rows.items():
        by.setdefault(o, []).append((logit(p), y))
    out = {}
    for o in sorted(by):
        if not (FROM <= o // 12 <= TO):
            continue
        train = [r for t, rs in by.items() if o - LAG - 12 * (WINDOW - 1) <= t <= o - LAG for r in rs]
        out[o] = intercept(train) if train else 0.0
    return out


def recalibrate(path_in, path_out):
    """Rewrites a forecasts.tsv; returns {origin: a}."""
    lines = [l.rstrip("\n").split("\t") for l in open(path_in)]
    rows = {}
    for s, u, o, p, y in lines:
        if s == "year" and len(u) == 3 and int(o) % 12 == 11 and y in ("true", "false"):
            rows[(u, int(o))] = (float(p), y == "true")
    a = shifts(rows)
    with open(path_out, "w") as w:
        for s, u, o, p, y in lines:
            if s == "year" and len(u) == 3 and int(o) in a:
                p = repr(expit(logit(float(p)) + a[int(o)]))
            w.write("\t".join([s, u, o, p, y]) + "\n")
    return a


def main(argv):
    repo, run, out, kinds = argv[0], argv[1], argv[2], argv[3].split(",")
    src, dst = os.path.join(repo, "data/runs", run), os.path.join(repo, "data/runs", out)
    if os.path.exists(dst):
        shutil.rmtree(dst)
    shutil.copytree(src, dst)
    for k in kinds:
        a = recalibrate(os.path.join(src, k, "forecasts.tsv"), os.path.join(dst, k, "forecasts.tsv"))
        print(k, " ".join(f"{o // 12}:{v:+.2f}" for o, v in sorted(a.items())))


if __name__ == "__main__":
    main(sys.argv[1:])
