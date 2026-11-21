"""Each systemic banking crisis's span, start to end (bnm-suj): events.tsv
keeps only onsets, so lattice_banking's :busy marked only the onset quarter
and the years inside a crisis stayed at risk, learned and scored as
non-onsets (162 of 2558 banking year rows 2003-22).

The crises are the ones scripts/slow-normalize.py dates onsets from, by its
rules: the ESRB database's systemic, not exogenous crises (data/raw/slow/
esrb/crises.xlsx), start to its "End of crisis management date" (else its
"back to normal" date, else the start month alone); Laeven-Valencia's
(data/raw/slow/lv/lv2026.csv), borderline ones left out, and left out where
the ESRB dates a crisis for the country within two years, January of the
start year to December of the end year.

  python3 -I scripts/banking-spans.py data/raw/slow/esrb/crises.xlsx data/raw/slow/lv/lv2026.csv > data/derived/banking-spans.tsv

Writes iso, from (YYYY-MM), to (YYYY-MM), source.
"""
import csv
import importlib.util
import os
import re
import sys



def _sn():
    here = os.path.dirname(os.path.abspath(__file__))
    spec = importlib.util.spec_from_file_location("slow_normalize", os.path.join(here, "slow-normalize.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


_SN = None


def sn():
    global _SN
    if _SN is None:
        _SN = _sn()
    return _SN


def month(c):
    return c[:7] if c and re.match(r"\d{4}-\d{2}", str(c)) else None


def esrb_spans(rows):
    out = []
    for r in rows[2:]:
        if not r or not r[0] or len(r) < 7:
            continue
        exogenous = (r[6] or "").strip().lower().startswith("exogenous")
        start = month(r[2])
        if r[5] != "1" or exogenous or not start:
            continue
        end = month(r[3]) or month(r[4]) or start
        out.append((sn().esrb_iso(r[0]), start, max(start, end), "ESRB"))
    return out


def lv_spans(rows, esrb):
    years = {}
    for iso, start, _, _ in esrb:
        years.setdefault(iso, set()).add(int(start[:4]))
    out = []
    for r in rows:
        y = int(r["start"])
        if r["borderline"] == "1" or any(abs(y - e) <= 2 for e in years.get(r["iso"], ())):
            continue
        out.append((r["iso"], "%d-01" % y, "%d-12" % max(y, int(r["end"])), "Laeven-Valencia"))
    return out


def main():
    sheets = sn().xlsx(sys.argv[1])
    esrb = [s for name, rows in sheets.items() if name.lower().startswith("systemic") for s in esrb_spans(rows)]
    lv = lv_spans(list(csv.DictReader(open(sys.argv[2]))), esrb)
    w = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
    for s in sorted(esrb + lv):
        w.writerow(s)


if __name__ == "__main__":
    main()
