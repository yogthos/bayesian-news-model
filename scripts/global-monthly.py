"""The global drivers of crisis waves, monthly (bnm-1ft; reports/Crisis
mechanisms data and signals.md): the dollar, oil, food prices and risk
spreads, each the month's value, available one month after the period
(market prices and indices published at the month's end; the BIS's
effective rates within the next month).

  world global/usd_eer     log BIS nominal effective rate of the US dollar,
                           narrow basket (27 economies), 1964- (WS_EER)
  world global/oil         log WTI spot, 1946- (FRED WTISPLC)
  world global/food        log World Bank Pink Sheet food index, 1960-
                           (copied from data/fast/series.tsv's fast/food)
  world global/baa_spread  Moody's Baa yield less the 10-year Treasury, %,
                           1953- (FRED BAA, GS10)
  world global/vix         log VIX, the month's mean of daily closes,
                           1990- (FRED VIXCLS)

The US policy rate is already in data/slow/series.tsv (finance/policy_rate,
USA). Reads only files on disk.

  python3 -I scripts/global-monthly.py > data/derived/global-monthly.tsv
"""
import csv
import io
import math
import os
import sys
import zipfile

RAW = "data/raw"


def month_end(y, m):
    days = [31, 29 if (y % 4 == 0 and (y % 100 != 0 or y % 400 == 0)) else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    return f"{y:04d}-{m:02d}-{days[m - 1]:02d}"


def line(var, y, m, value):
    ay, am = (y + 1, 1) if m == 12 else (y, m + 1)
    return ["world", var, "m", month_end(y, m), month_end(ay, am), repr(float(value))]


def fred_monthly(f):
    """{(y, m): mean of the period's values} from a FRED csv, daily or monthly."""
    acc = {}
    r = csv.reader(f)
    next(r)
    for d, v in r:
        try:
            x = float(v)
        except ValueError:
            continue
        acc.setdefault((int(d[:4]), int(d[5:7])), []).append(x)
    return {k: sum(v) / len(v) for k, v in acc.items()}


def spread(baa, gs10):
    return {k: baa[k] - gs10[k] for k in baa if k in gs10}


def us_eer(zf):
    """{(y, m): the US dollar's nominal narrow effective rate} from WS_EER's flat csv."""
    out = {}
    with zipfile.ZipFile(zf) as z:
        with z.open(z.namelist()[0]) as f:
            r = csv.reader(io.TextIOWrapper(f, "utf-8"))
            h = next(r)
            ix = {n.split(":")[0]: i for i, n in enumerate(h)}
            for row in r:
                if (row[ix["FREQ"]].startswith("M:") and row[ix["EER_TYPE"]].startswith("N:")
                        and row[ix["EER_BASKET"]].startswith("N:") and row[ix["REF_AREA"]].startswith("US:")):
                    try:
                        p, v = row[ix["TIME_PERIOD"]], float(row[ix["OBS_VALUE"]])
                    except ValueError:
                        continue
                    out[(int(p[:4]), int(p[5:7]))] = v
    return out


def food(f):
    out = []
    for l in f:
        p = l.rstrip("\n").split("\t")
        if len(p) == 6 and p[0] == "world" and p[1] == "fast/food":
            out.append(["world", "global/food"] + p[2:])
    return out


def main():
    def fred(path):
        with open(path, encoding="utf-8") as f:
            return fred_monthly(f)
    rows = []
    for (y, m), v in sorted(us_eer(os.path.join(RAW, "slow/bis/WS_EER.zip")).items()):
        rows.append(line("global/usd_eer", y, m, math.log(v)))
    for (y, m), v in sorted(fred(os.path.join(RAW, "slow/market/WTISPLC.csv")).items()):
        rows.append(line("global/oil", y, m, math.log(v)))
    with open("data/fast/series.tsv", encoding="utf-8") as f:
        rows += food(f)
    for (y, m), v in sorted(spread(fred(os.path.join(RAW, "slow/market/BAA.csv")), fred(os.path.join(RAW, "slow/market/GS10.csv"))).items()):
        rows.append(line("global/baa_spread", y, m, v))
    for (y, m), v in sorted(fred(os.path.join(RAW, "slow/fred/VIXCLS.csv")).items()):
        rows.append(line("global/vix", y, m, math.log(v)))
    w = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
    for r in rows:
        w.writerow(r)


if __name__ == "__main__":
    main()
