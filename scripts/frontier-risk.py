"""Political risk and equity returns where political onsets happen
(docs/TRADING.md, bnm-t5b.15.3).

The OECD test found no relation between the onset hazards' political risk
and next-quarter returns, perhaps because those markets see few onsets.
This repeats it over the World Bank's S&P equity index returns (annual,
dollars, 84 markets including frontier ones, 1990 on): each country's
out-of-sample probability of any political onset within a year (conflict
or war onset, protest or violence surge, from the onset hazards'
predictions at the year's last quarter) against the next year's return.
Twelve-month momentum (the year's own return) is the reference.

    python3 -I scripts/frontier-risk.py data/runs/events-2/preds.tsv \
        data/raw/slow/worldbank/CM.MKT.INDX.ZG.json
"""
import json
import sys
from collections import defaultdict

OECD = set("AUS AUT BEL CAN CHE CHL COL CRI CZE DEU DNK ESP EST FIN FRA GBR GRC HUN IRL ISL ISR ITA JPN KOR "
           "LTU LUX LVA MEX NLD NOR NZL POL PRT SVK SVN SWE TUR USA".split())
KINDS = {"conflict_onset", "war_onset", "protest_surge", "violence_surge"}


def ranks(xs):
    order = sorted(range(len(xs)), key=lambda i: xs[i])
    r = [0.0] * len(xs)
    for k, i in enumerate(order):
        r[i] = float(k)
    return r


def spearman(xs, ys):
    rx, ry = ranks(xs), ranks(ys)
    n = len(xs)
    mx, my = sum(rx) / n, sum(ry) / n
    c = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    vx = sum((a - mx) ** 2 for a in rx)
    vy = sum((b - my) ** 2 for b in ry)
    return c / (vx * vy) ** 0.5 if vx and vy else 0.0


def mean(xs):
    return sum(xs) / len(xs)


if __name__ == "__main__":
    preds_path, wb_path = sys.argv[1], sys.argv[2]
    # P(any onset) at each year's last quarter, by model and unit
    probs = defaultdict(dict)
    with open(preds_path) as f:
        for line in f:
            kind, win, model, q, unit, p, _ = line.rstrip("\n").split("\t")
            if win != "w1" or kind not in KINDS or model not in ("pool", "history"):
                continue
            q = int(q)
            if q % 4 != 3:
                continue
            probs[(model, unit, q // 4)][kind] = float(p)
    signal = {}
    for (model, unit, year), m in probs.items():
        if len(m) == 4:
            none = 1.0
            for v in m.values():
                none *= 1.0 - v
            signal[(model, unit, year)] = 1.0 - none
    rets = {}
    for r in json.load(open(wb_path))[1]:
        if r["value"] is not None:
            rets[(r["countryiso3code"], int(r["date"]))] = r["value"] / 100.0
    rows = []
    for (model, unit, year), s in signal.items():
        nxt = rets.get((unit, year + 1))
        if nxt is None:
            continue
        rows.append({"model": model, "unit": unit, "year": year, "risk": s, "ret": nxt,
                     "mom": rets.get((unit, year)), "oecd": unit in OECD})
    print("Political risk at year end and next year's equity index return (World Bank S&P indices, dollars).")
    print("IC: mean yearly rank correlation, signed so that positive means the signal predicts higher returns.")
    for label, keep in [("all markets", lambda r: True), ("emerging and frontier (non-OECD)", lambda r: not r["oecd"])]:
        for era, lo, hi in [("2000-2007", 2000, 2007), ("2008-", 2008, 2100)]:
            print(f"\n{label}, {era}")
            for name, model, key, sign in [("risk (pool), low = good", "pool", "risk", -1),
                                           ("risk (history), low = good", "history", "risk", -1),
                                           ("momentum", "pool", "mom", 1)]:
                ics, lss, falls_hi, falls_lo, n = [], [], [], [], 0
                for year in range(lo, min(hi, 2030) + 1):
                    rs = [r for r in rows if r["model"] == model and r["year"] == year and keep(r) and r[key] is not None]
                    if len(rs) < 9:
                        continue
                    n += len(rs)
                    rs.sort(key=lambda r: sign * r[key])
                    ics.append(spearman([sign * r[key] for r in rs], [r["ret"] for r in rs]))
                    t = len(rs) // 3
                    lss.append(mean([r["ret"] for r in rs[-t:]]) - mean([r["ret"] for r in rs[:t]]))
                    falls_lo += [r["ret"] <= -0.2 for r in rs[:t]]
                    falls_hi += [r["ret"] <= -0.2 for r in rs[-t:]]
                if not ics:
                    continue
                sd = (sum((x - mean(ics)) ** 2 for x in ics) / max(1, len(ics) - 1)) ** 0.5
                t_stat = mean(ics) / (sd / len(ics) ** 0.5) if sd else 0.0
                print(f"  {name:28s} years {len(ics):2d} obs {n:4d}  IC {mean(ics):+.3f} (t {t_stat:+.2f})"
                      f"  best minus worst third {mean(lss) * 100:+5.1f}%/yr"
                      f"  falls of 20%: worst third {mean(falls_lo):.2f}, best third {mean(falls_hi):.2f}")
