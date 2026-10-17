"""Philadelphia Fed Real-Time Data Set vintage matrices to long CSV
(docs/OPERATIONS.md, bnm-0n8.4): one row per vintage, observation month and
value, months as 12*year + month - 1. A vintage is the data as published in
the middle of its month.

Read with the normalizer's stdlib xlsx reader: iptMvMd.xlsx carries a
malformed creation date that openpyxl and pandas refuse.

    python3 -I scripts/rtdsm.py data/raw/slow/rtdsm/employMvMd.xlsx data/derived/rt-payrolls.csv
"""
import csv
import importlib.util
import os
import re
import sys

spec = importlib.util.spec_from_file_location("norm", os.path.join(os.path.dirname(os.path.abspath(__file__)), "slow-normalize.py"))
norm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(norm)

src, dst = sys.argv[1], sys.argv[2]
rows = next(iter(norm.xlsx(src).values()))
header = rows[0]


def vintage_month(name):
    # EMPLOY64M12 -> 1964-12; two-digit years from 1964 to 2063
    m = re.match(r"[A-Z]+(\d\d)M(\d\d?)$", name or "")
    if not m:
        return None
    yy, mm = int(m.group(1)), int(m.group(2))
    y = 1900 + yy if yy >= 64 else 2000 + yy
    return 12 * y + mm - 1


vint = [vintage_month(h) for h in header]
with open(dst, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["vintage", "month", "value"])
    for r in rows[1:]:
        if not r or not r[0] or ":" not in str(r[0]):
            continue
        y, m = str(r[0]).split(":")
        obs = 12 * int(y) + int(m) - 1
        for j in range(1, min(len(r), len(vint))):
            v = norm.num(r[j]) if r[j] not in (None, "", "#N/A") else None
            if v is not None and vint[j] is not None:
                w.writerow([vint[j], obs, v])
print("wrote", dst, file=sys.stderr)
