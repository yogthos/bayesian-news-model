"""t80-base banking year rows: AUC pooled and within year, all rows vs rows
with no crisis under way in the 12 months to the origin (ESRB end of crisis
management, Laeven-Valencia end year). Run from the repo root with python3 -I."""
import csv, importlib.util, re, sys
from collections import defaultdict

repo = sys.argv[1]
run = sys.argv[2]
spec = importlib.util.spec_from_file_location("sn", repo + "/scripts/slow-normalize.py")
sn = importlib.util.module_from_spec(spec); spec.loader.exec_module(sn)

def m(y, mo): return 12 * y + mo - 1
spells = defaultdict(list)
for r in csv.DictReader(open(repo + "/data/raw/slow/lv/lv2026.csv")):
    if r["borderline"] == "1": continue
    spells[r["iso"]].append((m(int(r["start"]), 1), m(int(r["end"]), 12), "lv"))
for name, rows in sn.xlsx(repo + "/data/raw/slow/esrb/crises.xlsx").items():
    if not name.lower().startswith("systemic"): continue
    for r in rows[2:]:
        if not r or not r[0] or len(r) < 7 or r[5] != "1": continue
        if (r[6] or "").strip().lower().startswith("exogenous"): continue
        if not (r[2] and re.match(r"\d{4}-\d{2}", r[2]) and r[3] and re.match(r"\d{4}-\d{2}", r[3])): continue
        iso = sn.esrb_iso(r[0])
        spells[iso].append((m(int(r[2][:4]), int(r[2][5:7])), m(int(r[3][:4]), int(r[3][5:7])), "esrb"))

def under_way(u, t):
    return any(s <= t and e >= t - 11 for s, e, _ in spells.get(u, []))

rows = []
for line in open(repo + "/data/runs/" + run + "/banking_crisis/forecasts.tsv"):
    s, place, o, p, y = line.rstrip("\n").split("\t")
    o = int(o)
    if s != "year" or len(place) != 3 or y not in ("true", "false") or o % 12 != 11 or not (2003 <= o // 12 <= 2022): continue
    rows.append((place, o, float(p), y == "true"))

def auc(ps):
    pos = [p for p, y in ps if y]; neg = sorted(p for p, y in ps if not y)
    if not pos or not neg: return float("nan")
    import bisect
    tot = sum(bisect.bisect_left(neg, p) + 0.5 * (bisect.bisect_right(neg, p) - bisect.bisect_left(neg, p)) for p in pos)
    return tot / (len(pos) * len(neg))

def within(rs):
    by = defaultdict(list)
    for u, o, p, y in rs: by[o].append((p, y))
    num = den = 0.0
    for ps in by.values():
        npos = sum(1 for _, y in ps if y); nneg = len(ps) - npos
        if npos and nneg:
            num += auc(ps) * npos * nneg; den += npos * nneg
    return num / den

kept = [r for r in rows if not under_way(r[0], r[1])]
dropped = [r for r in rows if under_way(r[0], r[1])]
for name, rs in (("all rows (as scored)", rows), ("no crisis under way", kept)):
    print("%-22s rows %4d onsets %3d  pooled AUC %.3f  within-year AUC %.3f  mean p %.3f" % (
        name, len(rs), sum(r[3] for r in rs), auc([(r[2], r[3]) for r in rs]), within(rs), sum(r[2] for r in rs) / len(rs)))
print("dropped rows %d (onsets among them %d), their mean p %.3f" % (len(dropped), sum(r[3] for r in dropped), sum(r[2] for r in dropped) / max(1, len(dropped))))
