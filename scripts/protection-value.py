"""Is the drawdown hazard worth more for buying protection than for timing?
(bnm-t5b.15.4)

From 1990, when the VIX begins: each month the investor holds the US market
and may buy a one-month put 5% out of the money on it, priced by Black-
Scholes at the month's VIX (plus a skew premium, since out-of-the-money
puts trade above at-the-money volatility). The put pays at the month's end
on the market's price return (total return less a 2% dividend yield).

Strategies: never hedge; always hedge; hedge when the model's probability
of a fall is above a multiple of its base rate (the rule set in advance:
the multiples are fixed, not tuned); hedge when protection is cheap (VIX
below its running median); hedge when both hold.

    uv run --no-project --with pandas --with scipy==1.18.1 python -I scripts/protection-value.py \
        data/runs/trader-inputs/inputs.tsv data/runs/record/preds-10-within-6m.tsv data/slow/series.tsv
"""
import sys

import numpy as np
import pandas as pd
from scipy.stats import norm


def bs_put(s, k, t, r, sigma):
    d1 = (np.log(s / k) + (r + sigma ** 2 / 2) * t) / (sigma * np.sqrt(t))
    d2 = d1 - sigma * np.sqrt(t)
    return k * np.exp(-r * t) * norm.cdf(-d2) - s * norm.cdf(-d1)


def stats(rets, rf):
    ex = rets - rf
    w = np.cumprod(1 + rets)
    dd = 1 - w / np.maximum.accumulate(w)
    return {"annual": w[-1] ** (12 / len(rets)) - 1, "vol": rets.std() * np.sqrt(12),
            "sharpe": ex.mean() / ex.std() * np.sqrt(12), "maxdd": dd.max()}


if __name__ == "__main__":
    inputs, preds_path, series = sys.argv[1], sys.argv[2], sys.argv[3]
    d = pd.read_csv(inputs, sep="\t")[["month", "mkt", "rf"]]
    p = pd.read_csv(preds_path, sep="\t", header=None, names=["month", "p", "y"])[["month", "p"]]
    s = pd.read_csv(series, sep="\t", header=None, names=["unit", "var", "f", "pend", "avail", "v"])
    vix = s[(s.unit == "world") & (s["var"] == "finance/vix")].copy()
    vix["month"] = vix.pend.str[:4].astype(int) * 12 + vix.pend.str[5:7].astype(int) - 1
    vix = vix[["month", "v"]].rename(columns={"v": "vix"})
    df = d.merge(p, on="month").merge(vix, on="month").sort_values("month").reset_index(drop=True)
    # decided at the end of month t, paid off over month t+1
    df["next_mkt"] = df.mkt.shift(-1)
    df["next_rf"] = df.rf.shift(-1)
    df = df.dropna().reset_index(drop=True)
    price_ret = df.next_mkt - 0.02 / 12
    base = df.p.expanding().mean().shift(1).fillna(df.p.iloc[0])
    vix_median = df.vix.expanding().median()
    for skew in [0.0, 3.0]:
        sigma = (df.vix + skew) / 100.0
        premium = bs_put(1.0, 0.95, 1 / 12, df.rf * 12, sigma)
        payoff = np.maximum(0.95 - (1 + price_ret), 0.0)
        rules = {
            "never": np.zeros(len(df)),
            "always": np.ones(len(df)),
            "model > 1.5x base": (df.p > 1.5 * base).astype(float),
            "model > 2x base": (df.p > 2 * base).astype(float),
            "model > 3x base": (df.p > 3 * base).astype(float),
            "cheap (VIX < median)": (df.vix < vix_median).astype(float),
            "model > 1.5x and cheap": ((df.p > 1.5 * base) & (df.vix < vix_median)).astype(float),
            "model > 2x and cheap": ((df.p > 2 * base) & (df.vix < vix_median)).astype(float),
        }
        print(f"Skew premium {skew:.0f} vol points; {len(df)} months from {df.month.iloc[0] // 12}")
        for name, h in rules.items():
            r = (df.next_mkt + h * (payoff - premium)).to_numpy()
            st = stats(r, df.next_rf.to_numpy())
            print(f"  {name:24s} annual {st['annual'] * 100:6.2f}%  vol {st['vol'] * 100:5.2f}%  sharpe {st['sharpe']:5.2f}"
                  f"  maxdd {st['maxdd'] * 100:5.1f}%  hedged {h.mean() * 100:3.0f}%  premium paid {(h * premium).sum() * 100:5.1f}%"
                  f"  payoff {(h * payoff).sum() * 100:5.1f}%")
