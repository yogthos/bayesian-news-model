"""bnm-7ny: read node_tap.clj's two tables (t137-base's currency year node
and t137's) and say where the issued ranking changes: the AUC of the
offset, of each model's log odds and of the contradiction model's lift over
the offset (f - o); and, for rows 9+ years past the last crisis, onset rows
against quiet, each run's mean offset, lift and contradiction inputs.
Usage: python3 -I node_tap.py <onsets.tsv> <base.tsv> <decay.tsv>"""
import sys, bisect, collections

def auc(rows, key):
    pos = [key(r) for r in rows if r["y"]]
    neg = sorted(key(r) for r in rows if not r["y"])
    tot = 0.0
    for p in pos:
        lo = bisect.bisect_left(neg, p); hi = bisect.bisect_right(neg, p)
        tot += lo + 0.5 * (hi - lo)
    return tot / (len(pos) * len(neg))

def read(path, ons):
    out = []
    for i, line in enumerate(open(path)):
        if i == 0:
            continue
        place, t, y, o, fc, fr, fa, wc, wr, cx = line.rstrip("\n").split("\t")
        t = int(t)
        ms = ons.get(place, [])
        j = bisect.bisect_right(ms, t)
        since = None if j == 0 else (t - ms[j - 1]) // 12
        out.append({"place": place, "t": t, "y": y == "true", "o": float(o), "fc": float(fc), "fr": float(fr),
                    "fa": float(fa), "wc": float(wc), "cx": [float(v) for v in cx.split(",") if v],
                    "nine": since is not None and since >= 9})
    return out

def main():
    ons = collections.defaultdict(list)
    for line in open(sys.argv[1]):
        k, u, m = line.rstrip("\n").split("\t")
        if k == "currency_crisis":
            ons[u].append(int(m))
    for u in ons:
        ons[u].sort()
    runs = {"base": read(sys.argv[2], ons), "decay": read(sys.argv[3], ons)}
    print("AUC over %d rows" % len(runs["base"]))
    for name, rs in runs.items():
        print("  %-5s offset %.4f  contradiction %.4f  regime %.4f  all %.4f  lift (f_c - o) %.4f  mean w_c %.2f" % (
            name, auc(rs, lambda r: r["o"]), auc(rs, lambda r: r["fc"]), auc(rs, lambda r: r["fr"]), auc(rs, lambda r: r["fa"]),
            auc(rs, lambda r: r["fc"] - r["o"]), sum(r["wc"] for r in rs) / len(rs)))
    print("9+ years past the last crisis: mean offset, lift, contradiction inputs")
    for name, rs in runs.items():
        for lbl, sel in (("onset", True), ("quiet", False)):
            xs = [r for r in rs if r["nine"] and r["y"] == sel]
            n = len(xs)
            w = len(xs[0]["cx"]) if xs else 0
            cx = [sum(r["cx"][i] for r in xs) / n for i in range(w)]
            print("  %-5s %-5s rows %4d  o %+.3f  lift %+.3f  cx %s" % (
                name, lbl, n, sum(r["o"] for r in xs) / n, sum(r["fc"] - r["o"] for r in xs) / n, " ".join("%+.2f" % v for v in cx)))

main()
