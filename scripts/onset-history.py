"""Each country's onset history per kind at each December (gap 1: coups
and war onsets; the conflict literature's strongest predictor is history):
from data/slow/events.tsv, the onsets known by the December (an onset is
known once its catalogue publishes it, the onset month plus the
catalogue's known-lag, or the file's own availability if later):

  unit  history/<kind>_since    years from the last known onset (60 when none)
  unit  history/<kind>_count3   known onsets in the last 3 years
  unit  history/<kind>_count10  known onsets in the last 10 years

for every ISO3 country in data/ref/countryInfo.txt, Decembers 1946-2026.

  python3 -I scripts/onset-history.py > data/derived/onset-history.tsv
"""
import csv
import sys

KINDS = {"coup": 3, "conflict_onset": 18, "war_onset": 18}
NONE = 60.0


def month(d):
    return int(d[:4]) * 12 + int(d[5:7]) - 1


def history(f, units, lags, y0, y1):
    known = {}
    for line in f:
        p = line.rstrip("\n").split("\t")
        if len(p) < 6 or p[0] not in lags:
            continue
        k, u, onset = p[0], p[1], month(p[2])
        at = max(onset + lags[k], month(p[5]))
        known.setdefault((k, u), []).append((onset, at))
    out = []
    for k in lags:
        for u in units:
            evs = known.get((k, u), [])
            for y in range(y0, y1 + 1):
                t = y * 12 + 11
                ons = sorted(o for o, a in evs if a <= t)
                since = (t - ons[-1]) // 12 if ons else NONE
                c3 = sum(1 for o in ons if o > t - 36)
                c10 = sum(1 for o in ons if o > t - 120)
                for name, v in (("since", since), ("count3", c3), ("count10", c10)):
                    out.append([u, f"history/{k}_{name}", "a", f"{y}-12-31", f"{y}-12-31", repr(float(v))])
    return out


def units(path="data/ref/countryInfo.txt"):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if not line.startswith("#") and len(p) > 1 and len(p[1]) == 3:
                out.append(p[1])
    return out


def main():
    with open("data/slow/events.tsv", encoding="utf-8") as f:
        rows = history(f, units(), KINDS, 1946, 2026)
    w = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
    for r in rows:
        w.writerow(r)


if __name__ == "__main__":
    main()
