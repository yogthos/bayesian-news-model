"""Calibration of a lattice run's year country nodes, December origins
2003-2022, resolved rows (bnm.paired/read-lattice's rows): per kind, the
expected calibration error over forecast deciles and the deciles' mean p
against the realized rate. The 4:1 alert at p >= 0.2 is optimal only if
p is calibrated (reports/Signal detection for crisis forecasting.md).
python3 -I calibration.py <repo> <run>"""
import os, sys

repo, run = sys.argv[1], sys.argv[2]
base = os.path.join(repo, "data/runs", run)
print("kind                   rows  ECE    deciles: mean p / rate")
for kind in sorted(k for k in os.listdir(base) if os.path.isdir(os.path.join(base, k))):
    rows = []
    for line in open(os.path.join(base, kind, "forecasts.tsv")):
        s, u, o, p, y = line.rstrip("\n").split("\t")
        o = int(o)
        if s == "year" and len(u) == 3 and y in ("true", "false") and o % 12 == 11 and 2003 <= o // 12 <= 2022:
            rows.append((float(p), y == "true"))
    rows.sort()
    n = len(rows)
    bins = [rows[i * n // 10:(i + 1) * n // 10] for i in range(10)]
    ece = sum(len(b) / n * abs(sum(p for p, _ in b) / len(b) - sum(y for _, y in b) / len(b)) for b in bins if b)
    cells = " ".join("%.3f/%.3f" % (sum(p for p, _ in b) / len(b), sum(y for _, y in b) / len(b)) for b in bins if b)
    print("%-21s %5d  %.4f %s" % (kind, n, ece, cells))
