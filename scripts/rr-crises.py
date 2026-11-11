"""Reinhart-Rogoff global crisis data (HBS BFFS) to onsets: one row per
country and year with 1 where a banking, currency, sovereign external
default or inflation crisis starts (the first year of a run of crisis
years), and whether the country is on the gold standard that year.

    python3 -I scripts/rr-crises.py in.xlsx out.csv

Standard library only (the xlsx through slow-normalize's reader).

- A crisis year is any value of 1 or more. The currency column also holds
  2 (38 cells: a crash in a year of currency reform, e.g. Russia 1998,
  Brazil 1986 and 1993, Germany 1948); reading only 1 as a crisis split
  runs, so the year after a 2 became a false onset and a crash that began
  with a 2 was dated a year late or lost.
- Country codes are stripped (the file has "HUN " for Hungary).
- <kind>_known is 1 where the cell holds a number. RR fill 0 for years
  before a state existed, so known is not coverage: read it with the
  years the state existed (scripts/episodes.py does).
"""
import csv
import importlib.util
import os
import sys

KINDS = [("Banking Crisis", "banking"), ("Currency Crises", "currency"), ("Inflation Crises", "inflation"),
         ("SOVEREIGN EXTERNAL DEBT 1", "default")]
COLUMNS = ["iso", "year", "gold", "banking", "banking_known", "currency", "currency_known", "inflation",
           "inflation_known", "default", "default_known"]


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def onsets(table):
    """Rows (dicts of COLUMNS) from the sheet's rows (lists of strings,
    the header first; rows without a country or year are skipped)."""
    head = [(h or "").strip() for h in table[0]]
    col = {k: next(i for i, h in enumerate(head) if h.startswith(p)) for p, k in KINDS}
    icc, iyear, igold = head.index("CC3"), head.index("Year"), head.index("Gold Standard")
    by = {}
    for r in table[1:]:
        r = list(r) + [None] * (len(head) - len(r))
        cc, y = (r[icc] or "").strip(), num(r[iyear])
        if cc and y is not None:
            by.setdefault(cc, {})[int(y)] = r
    out = []
    for cc in sorted(by):
        prev = {k: False for _, k in KINDS}
        for y in sorted(by[cc]):
            r = by[cc][y]
            gold = num(r[igold])
            row = {"iso": cc, "year": y, "gold": "" if gold is None else int(gold)}
            for _, k in KINDS:
                v = num(r[col[k]])
                on = v is not None and v >= 1
                row[k] = 1 if on and not prev[k] else 0
                row[k + "_known"] = 0 if v is None else 1
                prev[k] = on
            out.append(row)
    return out


def read_xlsx(p):
    spec = importlib.util.spec_from_file_location("sn", os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                                     "slow-normalize.py"))
    sn = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sn)
    sheets = sn.xlsx(p)
    return sheets.get("Sheet1") or next(iter(sheets.values()))


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    with open(dst, "w", newline="") as f:
        w = csv.DictWriter(f, COLUMNS, lineterminator="\n")
        w.writeheader()
        w.writerows(onsets(read_xlsx(src)))
