"""Each country's real exchange rate against the dollar, annual (bnm-mn8;
reports/Crisis mechanisms data and signals.md: overvaluation is a level
against the country's own past, not a change): log of the year's average
local currency per dollar (WDI PA.NUS.FCRF, finance/fx_per_usd) less the
log of the country's consumer price level plus the US one, each level
cumulated from WDI's CPI inflation (economy/inflation). Higher is weaker
(cheaper); a fall against its own past is a real appreciation. Only the
latest unbroken run of years is kept, since a gap in the inflation series
breaks the cumulated level. Available when its last input is.

  python3 -I scripts/rer-annual.py data/slow/series.tsv > data/derived/rer-annual.tsv
"""
import csv
import math
import sys


def rer(f):
    fx, infl, av = {}, {}, {}
    for line in f:
        p = line.rstrip("\n").split("\t")
        if len(p) != 6 or p[2] != "a" or p[1] not in ("finance/fx_per_usd", "economy/inflation"):
            continue
        u, v, _, pend, avail, x = p
        try:
            x = float(x)
        except ValueError:
            continue
        y = int(pend[:4])
        (fx if v == "finance/fx_per_usd" else infl).setdefault(u, {})[y] = x
        av[(u, v, y)] = avail
    us = infl.get("USA", {})
    out = []
    for u in sorted(fx):
        if u == "USA" or u not in infl:
            continue
        years = sorted(y for y in fx[u] if fx[u][y] > 0 and y in infl[u] and y in us)
        run = []
        for y in years:
            run = run + [y] if run and y == run[-1] + 1 else [y]
        lp = lus = 0.0
        for i, y in enumerate(run):
            if i > 0:
                lp += math.log1p(max(infl[u][y], -99.0) / 100.0)
                lus += math.log1p(us[y] / 100.0)
            a = max(av[(u, "finance/fx_per_usd", y)], av[(u, "economy/inflation", y)], av[("USA", "economy/inflation", y)])
            out.append([u, "derived/rer_usd", "a", f"{y}-12-31", a, repr(math.log(fx[u][y]) - lp + lus)])
    return out


def main():
    with open(sys.argv[1], encoding="utf-8") as f:
        rows = rer(f)
    w = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
    for r in rows:
        w.writerow(r)


if __name__ == "__main__":
    main()
