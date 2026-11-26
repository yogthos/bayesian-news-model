"""bnm-ris: where banking's year node loses the signal. Reads node_tap.clj's
output and, for JST countries 2003-2020 and all countries 2003-2022, scores
each layer: the mean offset, each group's log odds, and the issued p (AUC,
and mean log score per row against a constant at the subset's own rate,
a hindsight reference). Also each group's mean weight.
  python3 -I docs/studies/banking/node_tap.py <tap.tsv> <jst.csv>
"""
import csv, math, sys

def auc(xs, ys):
    pos = [x for x, y in zip(xs, ys) if y]; neg = sorted(x for x, y in zip(xs, ys) if not y)
    if not pos or not neg: return float('nan')
    import bisect
    s = sum(bisect.bisect_left(neg, p) + 0.5 * (bisect.bisect_right(neg, p) - bisect.bisect_left(neg, p)) for p in pos)
    return s / (len(pos) * len(neg))

def ll(p, y):
    p = min(max(p, 1e-9), 1 - 1e-9); return math.log(p if y else 1 - p)

def sig(z): return 1 / (1 + math.exp(-z))

def main(tap, jstcsv):
    jst = {r['iso'] for r in csv.DictReader(open(jstcsv))}
    rows = list(csv.DictReader(open(tap), delimiter='\t'))
    fs = [c for c in rows[0] if c.startswith('f_')]
    for name, keep in [('JST 2003-2020', lambda r: r['place'] in jst and int(r['t']) // 12 <= 2020),
                       ('non-JST 2003-2022', lambda r: r['place'] not in jst),
                       ('all 2003-2022', lambda r: True)]:
        rs = [r for r in rows if keep(r)]
        ys = [r['y'] == 'true' for r in rs]
        rate = sum(ys) / len(ys)
        base = sum(ll(rate, y) for y in ys) / len(ys)
        print(f"{name}: rows {len(rs)} onsets {sum(ys)} rate {rate:.3f}")
        layers = [('issued p', lambda r: float(r['p']) if r['p'] not in ('', 'nil') else None),
                  ('offset', lambda r: sig(float(r['o'])) if r['o'] not in ('', 'nil') else None)]
        layers += [(c[2:], (lambda c: lambda r: sig(float(r[c])) if r[c] not in ('', 'nil') else None)(c)) for c in fs]
        for lab, get in layers:
            ps = [(get(r), y) for r, y in zip(rs, ys)]
            ps = [(p, y) for p, y in ps if p is not None]
            if not ps: continue
            a = auc([p for p, _ in ps], [y for _, y in ps])
            sk = sum(ll(p, y) for p, y in ps) / len(ps) - base if len(ps) == len(rs) else float('nan')
            mp = sum(p for p, _ in ps) / len(ps)
            w = ''
            if lab not in ('issued p', 'offset'):
                ws = [float(r['w_' + lab]) for r in rs]; w = f"  mean weight {sum(ws)/len(ws):.2f}"
            print(f"   {lab:<16} AUC {a:.3f}  log vs constant {sk:+.4f}  mean p {mp:.3f}  rows {len(ps)}{w}")

if __name__ == '__main__':
    main(*sys.argv[1:3])
