"""How fast do the faster layers react once a crash starts? (bnm-0n8.6)

No model anticipates an exogenous shock (2020), so what matters is how much
of the fall has happened before a signal fires. For every US bear market
(a 20% fall from a peak) since 1990, the drawdown from the peak on the day
each detector first fires after it:

  weekly hazard   the weekly model's P(10% fall within 13 weeks) above twice
                  its base rate (bnm.weekly, at each week's close)
  200-day         the market below its 200-day average (daily)
  vol jump        21-day volatility above 1.8 times its 252-day level (daily)

and the drawdown at the trough, for scale.

    python3 -I scripts/detection-lag.py data/raw/slow/market/F-F_Research_Data_Factors_daily.csv \
        data/runs/weekly-live/preds-10-within-13w.tsv
"""
import csv
import math
import sys

daily_path, weekly_path = sys.argv[1], sys.argv[2]
dates, rets = [], []
with open(daily_path) as f:
    started = False
    for line in f:
        if line.startswith(",Mkt-RF"):
            started = True
            continue
        if not started:
            continue
        cells = [c.strip() for c in line.split(",")]
        if len(cells) < 5 or not cells[0].isdigit() or len(cells[0]) != 8:
            if started and dates:
                break
            continue
        dates.append(cells[0])
        rets.append((float(cells[1]) + float(cells[4])) / 100.0)

wealth = [1.0]
for r in rets:
    wealth.append(wealth[-1] * (1 + r))
wealth = wealth[1:]
n = len(wealth)

weekly = {}
with open(weekly_path) as f:
    for d, p, y in csv.reader(f, delimiter="\t"):
        weekly[d] = float(p)
held = [p for d, p in weekly.items() if d >= "19900101"]
base = sum(held) / len(held)


def sd(xs):
    m = sum(xs) / len(xs)
    return math.sqrt(sum((x - m) ** 2 for x in xs) / len(xs))


# bear markets since 1990: from a peak, a fall of 20% before a new high
bears, i = [], next(k for k in range(n) if dates[k] >= "19900101")
peak = i
while i < n:
    if wealth[i] >= wealth[peak]:
        peak = i
    elif wealth[i] <= 0.8 * wealth[peak]:
        trough = i
        while trough + 1 < n and wealth[trough + 1] < wealth[peak]:
            trough += 1
        lo = min(range(peak, trough + 1), key=lambda k: wealth[k])
        bears.append((peak, lo))
        i, peak = trough + 1, trough + 1
        continue
    i += 1

print("US bear markets since 1990: drawdown from the peak when each detector first fires")
print(f"(weekly hazard threshold: twice its held-out mean, {2 * base:.3f})")
print(f"  {'peak':10s} {'trough':10s} {'max fall':>8s}   {'weekly hazard':>16s}   {'200-day':>16s}   {'vol jump':>16s}")
for pk, lo in bears:
    def fire(test):
        for k in range(pk, lo + 1):
            if test(k):
                return f"{dates[k][:4]}-{dates[k][4:6]}-{dates[k][6:]} {100 * (1 - wealth[k] / wealth[pk]):4.1f}%"
        return "not before trough"
    hz = fire(lambda k: dates[k] in weekly and weekly[dates[k]] > 2 * base)
    sma = fire(lambda k: k >= 200 and wealth[k] < sum(wealth[k - 199:k + 1]) / 200)
    vol = fire(lambda k: k >= 252 and sd(rets[k - 20:k + 1]) > 1.8 * sd(rets[k - 251:k + 1]))
    print(f"  {dates[pk]:10s} {dates[lo]:10s} {100 * (1 - wealth[lo] / wealth[pk]):7.1f}%   {hz:>16s}   {sma:>16s}   {vol:>16s}")
