"""bnm-ris: banking's within-year ranking by December origin, run by run:
the AUC of the year node's country forecasts among that origin's resolved
rows, with the onsets counted. Which years carry the loss from t101 (0.657)
to t104-base (0.575)?

  python3 -I docs/studies/banking_rank_years.py data/runs t101-reserves-rho0 t104-base t105-debt-rho0
"""
import sys
from collections import defaultdict


def rows(base, run):
    out = defaultdict(dict)
    for line in open(f"{base}/{run}/banking_crisis/forecasts.tsv"):
        f = line.rstrip("\n").split("\t")
        if len(f) < 5 or f[0] != "year" or f[4] not in ("true", "false") or len(f[1]) != 3:
            continue
        t = int(f[2])
        if t % 12 != 11:
            continue
        out[t // 12][f[1]] = (float(f[3]), f[4] == "true")
    return out


def auc(xs):
    pos = [p for p, y in xs if y]
    neg = [p for p, y in xs if not y]
    if not pos or not neg:
        return None
    return sum((a > b) + 0.5 * (a == b) for a in pos for b in neg) / (len(pos) * len(neg))


def main(base, runs):
    data = {r: rows(base, r) for r in runs}
    print("year  onsets  " + "  ".join(f"{r[:16]:>16}" for r in runs))
    tot = {r: [0.0, 0] for r in runs}
    for y in range(2003, 2023):
        common = set.intersection(*(set(data[r].get(y, {})) for r in runs))
        if not common:
            continue
        k = sum(data[runs[0]][y][u][1] for u in common)
        cells = []
        for r in runs:
            a = auc([data[r][y][u] for u in common])
            if a is not None:
                tot[r][0] += a * k
                tot[r][1] += k
            cells.append(f"{a:16.3f}" if a is not None else f"{'-':>16}")
        print(f"{y}  {k:6d}  " + "  ".join(cells))
    print("onset-weighted  " + "  ".join(f"{tot[r][0] / tot[r][1]:16.3f}" for r in runs if tot[r][1]))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
