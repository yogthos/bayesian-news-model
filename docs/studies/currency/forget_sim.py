"""bnm-7ny: a proxy for the averaging's forgetting at currency crises' year
node, from node_tap.clj's rows. Two models only (contradiction and regime,
which hold nearly all the weight 2003-2022). Starting from each run's own
weights at December 2003, each December's weights are the last ones to the
power `forget`, times each model's Bernoulli likelihood of the rows that
became known since (an origin's rows are known `lag` months on), then
normalized; the issued p is the weight-mixed probability. Checks the proxy
by its forget 0.995 trace against the run's weights, then reports AUC and
Brier of the mixture at other rates. A proxy: the node's evidence is the
joint predictive with each model's spread, not Bernoulli on its log odds.
Usage: python3 -I forget_sim.py <tap.tsv> [lag months, default 24]"""
import sys, math, bisect, collections

def rows(path):
    by = collections.defaultdict(list)
    for i, l in enumerate(open(path)):
        if i == 0:
            continue
        f = l.rstrip("\n").split("\t")
        by[int(f[1])].append({"y": f[2] == "true", "fc": float(f[4]), "fr": float(f[5]), "wc": float(f[7]), "wr": float(f[8])})
    return dict(sorted(by.items()))

ex = lambda x: 1.0 / (1.0 + math.exp(-x))

def ll(rs, key):
    return sum(math.log(max(1e-12, ex(r[key]) if r["y"] else 1.0 - ex(r[key]))) for r in rs)

def auc(pairs):
    pos = [p for p, y in pairs if y]; neg = sorted(p for p, y in pairs if not y)
    return sum(bisect.bisect_left(neg, p) + 0.5 * (bisect.bisect_right(neg, p) - bisect.bisect_left(neg, p)) for p in pos) / (len(pos) * len(neg))

def sim(by, forget, lag):
    ts = list(by)
    t0 = ts[0]
    s = by[t0][0]["wc"] + by[t0][0]["wr"]
    lw = {"fc": math.log(max(1e-300, by[t0][0]["wc"] / s)), "fr": math.log(max(1e-300, by[t0][0]["wr"] / s))}
    out, trace = [], []
    for t in ts:
        if t != t0:
            known = [r for o in ts if t - 12 - lag < o <= t - lag for r in by[o]] if True else []
            for k in lw:
                lw[k] = forget * lw[k] + ll(known, k)
            m = max(lw.values())
            z = math.log(sum(math.exp(v - m) for v in lw.values())) + m
            lw = {k: v - z for k, v in lw.items()}
        wc = math.exp(lw["fc"])
        trace.append((t // 12, wc, by[t][0]["wc"] / (by[t][0]["wc"] + by[t][0]["wr"])))
        for r in by[t]:
            out.append((wc * ex(r["fc"]) + (1 - wc) * ex(r["fr"]), r["y"]))
    return out, trace

def main():
    by = rows(sys.argv[1])
    lag = int(sys.argv[2]) if len(sys.argv) > 2 else 24
    _, tr = sim(by, 0.995, lag)
    print("proxy check at forget 0.995, weight on contradiction (proxy | run, renormalized over the two):")
    print("  " + "  ".join("%d %.2f|%.2f" % x for x in tr if x[0] % 3 == 0 or x[0] >= 2017))
    print("forget  AUC     Brier")
    for f in (0.995, 0.97, 0.9, 0.8, 0.6):
        out, _ = sim(by, f, lag)
        print("%.3f   %.4f  %.5f" % (f, auc(out), sum((p - y) ** 2 for p, y in out) / len(out)))

main()
