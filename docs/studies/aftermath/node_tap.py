"""Read two node_tap.clj tables of one kind (without and with the decay):
the AUC of the issued forecast, the offset and each model group with any
weight, and the mean weight of each group by period. Generalizes
docs/studies/currency/node_tap.py.
Usage: python3 -I node_tap.py <base.tsv> <decay.tsv>"""
import sys, bisect, collections

def read(path):
    lines = open(path).read().splitlines()
    head = lines[0].split("\t")
    return head, [dict(zip(head, l.split("\t"))) for l in lines[1:]]

def auc(rows, key):
    pos = [key(r) for r in rows if r["y"] == "true"]
    neg = sorted(key(r) for r in rows if r["y"] != "true")
    if not pos or not neg:
        return float("nan")
    return sum(bisect.bisect_left(neg, p) + 0.5 * (bisect.bisect_right(neg, p) - bisect.bisect_left(neg, p)) for p in pos) / (len(pos) * len(neg))

def main():
    for name, path in zip(["base", "decay"], sys.argv[1:3]):
        head, rs = read(path)
        groups = [h[2:] for h in head if h.startswith("f:")]
        mw = {g: sum(float(r["w:" + g]) for r in rs) / len(rs) for g in groups}
        live = [g for g in groups if mw[g] > 0.02]
        print("== %s rows %d  AUC issued %.4f offset %.4f" % (name, len(rs), auc(rs, lambda r: float(r["p"])), auc(rs, lambda r: float(r["o"]))))
        print("   group AUC (mean weight): " + "  ".join("%s %.4f (%.2f)" % (g, auc(rs, lambda r, g=g: float(r["f:" + g])), mw[g]) for g in sorted(live, key=lambda g: -mw[g])))
        for lo, hi in ((2003, 2009), (2010, 2016), (2017, 2022)):
            s = [r for r in rs if lo <= int(r["t"]) // 12 <= hi]
            print("   %d-%d weights: " % (lo, hi) + "  ".join("%s %.2f" % (g, sum(float(r["w:" + g]) for r in s) / len(s)) for g in sorted(live, key=lambda g: -mw[g])))

main()
