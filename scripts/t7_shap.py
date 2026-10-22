"""T7 (bnm-3qu.8): boosted trees with SHAP interactions, a diagnostic for
choosing T3's state x trigger pairs. The logit stays the model (Beutel et
al. 2019); nothing here forecasts.

Input: the design rows bnm.interactions --export writes (no outcome after
2002): per kind's year-end row, the outcome (an onset in (t, t+2]), the
base's log odds L (t44-world-norel), the named states (demeaned by year,
scaled on the design years) and the triggers (scaled), NA where unknown.

Per kind group:
- shallow gradient-boosted trees (xgboost, depth 2 and 3), L the offset
  (base_margin) and a feature, kind dummies, states and triggers (missing
  left to the trees); rounds chosen by inner leave-era-out;
- the logit with the same inputs (L, kind dummies and L x kind: the
  recalibration; states and triggers, unknown as 0 with a flag), ridge
  1e-3; and the recalibration alone;
- leave-one-era-out log scores (eras 1960-69, 1970-79, 1980-89, 1990-2000
  by origin), differences with a 90% interval from resampled origin years;
- SHAP interaction values (TreeSHAP, xgboost's pred_interactions) of the
  fit on all design rows and of each era-left-out fit: per state x
  trigger pair the mean of |phi_ij + phi_ji| over the rows where both are
  known (and, for the record, over all the group's rows), its
  sign (the correlation of phi_ij + phi_ji with the centred product of
  the two inputs, where both are known), and per kind.

Usage:
  python -I scripts/t7_shap.py data/runs/sequencing/t7-design-rows.tsv \
      data/runs/sequencing/t7-shap.tsv [--seed 1]
"""

import sys

import numpy as np
import pandas as pd
import xgboost as xgb

ERAS = [(1960, 1969), (1970, 1979), (1980, 1989), (1990, 2000)]
STATES = ["credit-gap", "keen-debt-gap", "debt-service", "low-reserves", "fuel-price-gap",
          "youth-bulge", "elite-overproduction", "anocracy", "technology-gap",
          "food-import-share", "agri-share"]
TRIGGERS = ["house-growth", "equity-drawdown", "tightening", "tot-fall", "export-price-fall",
            "grain-gap", "drought", "election", "neighbour-conflict"]
# the theory pairs (docs/LIT-NEXT-STEPS.md 1b and ranked trial 3) as far as
# the inputs name them
THEORY = {("credit-gap", "house-growth"), ("credit-gap", "tightening"),
          ("keen-debt-gap", "tightening"), ("food-import-share", "grain-gap"),
          ("anocracy", "grain-gap"), ("agri-share", "drought"),
          ("fuel-price-gap", "tot-fall"), ("fuel-price-gap", "export-price-fall"),
          ("anocracy", "neighbour-conflict")}


def features(df, kinds):
    X = pd.DataFrame({"L": df["L"]})
    for k in kinds[1:]:
        X["kind=" + k] = (df["kind"] == k).astype(float)
    for c in STATES + TRIGGERS:
        X[c] = df[c]
    return X


def logit_design(df, kinds, with_inputs):
    cols = [np.ones(len(df)), df["L"].to_numpy()]
    for k in kinds[1:]:
        d = (df["kind"] == k).to_numpy(float)
        cols += [d, d * df["L"].to_numpy()]
    if with_inputs:
        for c in STATES + TRIGGERS:
            v = df[c].to_numpy()
            known = ~np.isnan(v)
            cols += [np.where(known, v, 0.0), (~known).astype(float)]
    return np.column_stack(cols)


def fit_logit(X, y, ridge=1e-3, iters=50):
    mu = X[:, 1:].mean(0)
    sd = X[:, 1:].std(0)
    sd[sd < 1e-12] = 1.0
    Z = np.column_stack([X[:, 0], (X[:, 1:] - mu) / sd])
    b = np.zeros(Z.shape[1])
    pen = np.full(Z.shape[1], ridge * len(y))
    pen[0] = 0.0
    for _ in range(iters):
        p = 1.0 / (1.0 + np.exp(-(Z @ b)))
        w = p * (1 - p)
        g = Z.T @ (y - p) - pen * b
        H = (Z * w[:, None]).T @ Z + np.diag(pen) + 1e-9 * np.eye(len(b))
        step = np.linalg.solve(H, g)
        b += step
        if np.max(np.abs(step)) < 1e-8:
            break
    return lambda Xn: 1.0 / (1.0 + np.exp(-(np.column_stack([Xn[:, 0], (Xn[:, 1:] - mu) / sd]) @ b)))


def params(depth, seed):
    return {"objective": "binary:logistic", "eval_metric": "logloss", "max_depth": depth,
            "eta": 0.05, "subsample": 0.8, "colsample_bytree": 0.8, "min_child_weight": 5,
            "lambda": 1.0, "seed": seed, "nthread": 4}


def era_of(t):
    for i, (a, b) in enumerate(ERAS):
        if a <= t <= b:
            return i
    return -1


def choose_rounds(X, y, L, eras, depth, seed):
    """Rounds by leave-era-out over the eras present."""
    d = xgb.DMatrix(X, label=y, base_margin=L, missing=np.nan)
    folds = [(np.where(eras != e)[0], np.where(eras == e)[0]) for e in np.unique(eras)]
    cv = xgb.cv(params(depth, seed), d, num_boost_round=800, folds=folds,
                early_stopping_rounds=50, verbose_eval=False)
    return len(cv)


def boost(X, y, L, eras, depth, seed):
    n = choose_rounds(X, y, L, eras, depth, seed)
    d = xgb.DMatrix(X, label=y, base_margin=L, missing=np.nan)
    return xgb.train(params(depth, seed), d, num_boost_round=n), n


def logscore(p, y):
    p = np.clip(p, 1e-12, 1 - 1e-12)
    return np.where(y == 1, np.log(p), np.log(1 - p))


def year_boot(diff, years, draws=1000, seed=1):
    rng = np.random.default_rng(seed)
    uy = np.unique(years)
    sums = np.array([diff[years == u].sum() for u in uy])
    ns = np.array([(years == u).sum() for u in uy])
    out = []
    for _ in range(draws):
        i = rng.integers(0, len(uy), len(uy))
        out.append(sums[i].sum() / ns[i].sum())
    return np.quantile(out, [0.05, 0.95])


def pair_strengths(model, X, L, df, kinds):
    d = xgb.DMatrix(X, base_margin=L, missing=np.nan)
    phi = model.predict(d, pred_interactions=True)  # n x (F+1) x (F+1)
    names = list(X.columns)
    out = []
    for s in STATES:
        for t in TRIGGERS:
            i, j = names.index(s), names.index(t)
            v = phi[:, i, j] + phi[:, j, i]
            xs, xt = X[s].to_numpy(), X[t].to_numpy()
            both = ~np.isnan(xs) & ~np.isnan(xt)
            sign = np.nan
            if both.sum() > 30 and np.std(v[both]) > 0:
                prod = (xs[both] - xs[both].mean()) * (xt[both] - xt[both].mean())
                if np.std(prod) > 0:
                    sign = float(np.corrcoef(v[both], prod)[0, 1])
            per_kind = {}
            for k in kinds:
                sel = both & (df["kind"] == k).to_numpy()
                per_kind[k] = float(np.mean(np.abs(v[sel]))) if sel.sum() > 0 else 0.0
            # the mean over the rows where both are known (primary): where one
            # is missing the trees' default branch stands for coverage, not
            # for the pair; the mean over all rows for the record
            sk = float(np.mean(np.abs(v[both]))) if both.sum() > 0 else 0.0
            out.append({"state": s, "trigger": t, "strength": sk, "strength_all": float(np.mean(np.abs(v))),
                        "sign": sign, "rows": int(both.sum()),
                        "onsets": int(df["y"].to_numpy()[both].sum()), "per_kind": per_kind})
    # every pair, for context: where state x trigger pairs stand among all
    m = np.abs(phi).mean(0)
    allp = []
    F = len(names)
    for i in range(F):
        for j in range(i + 1, F):
            allp.append((names[i], names[j], float(m[i, j] + m[j, i])))
    allp.sort(key=lambda r: -r[2])
    return out, allp


def main(argv):
    src, dst = argv[1], argv[2]
    seed = int(argv[argv.index("--seed") + 1]) if "--seed" in argv else 1
    df = pd.read_csv(src, sep="\t", na_values=["NA"])
    assert df["t"].max() + 2 <= 2002, "design rows only"
    lines = []

    def say(s):
        print(s, flush=True)
        lines.append(s)

    say(f"# T7: {len(df)} design rows, origins {df['t'].min()}-{df['t'].max()} (windows to {df['t'].max() + 2}), seed {seed}")
    for group in ["financial", "political"]:
        g = df[df["group"] == group].reset_index(drop=True)
        kinds = sorted(g["kind"].unique())
        X = features(g, kinds)
        y = g["y"].to_numpy(float)
        L = g["L"].to_numpy(float)
        eras = g["t"].map(era_of).to_numpy()
        years = g["t"].to_numpy()
        say(f"# {group}: kinds {','.join(kinds)}; {len(g)} rows, {int(y.sum())} onsets")
        # out of sample, leave one era out
        Xr_all = logit_design(g, kinds, False)
        Xl_all = logit_design(g, kinds, True)
        for depth in [2, 3]:
            ls = {"recal": np.zeros(len(g)), "logit": np.zeros(len(g)), "boost": np.zeros(len(g))}
            rounds = []
            fold_models = []
            for e in range(len(ERAS)):
                tr, te = eras != e, eras == e
                ls["recal"][te] = logscore(fit_logit(Xr_all[tr], y[tr])(Xr_all[te]), y[te])
                ls["logit"][te] = logscore(fit_logit(Xl_all[tr], y[tr])(Xl_all[te]), y[te])
                mdl, n = boost(X[tr], y[tr], L[tr], eras[tr], depth, seed)
                rounds.append(n)
                fold_models.append((e, mdl, tr))
                p = mdl.predict(xgb.DMatrix(X[te], base_margin=L[te], missing=np.nan))
                ls["boost"][te] = logscore(p, y[te])
            raw = logscore(1.0 / (1.0 + np.exp(-L)), y)
            say(f"oos\t{group}\tdepth {depth}\trounds {rounds}\tlog raw {raw.mean():.5f}\trecal {ls['recal'].mean():.5f}"
                f"\tlogit {ls['logit'].mean():.5f}\tboost {ls['boost'].mean():.5f}")
            for a, b in [("boost", "logit"), ("logit", "recal"), ("boost", "recal")]:
                diff = ls[a] - ls[b]
                lo, hi = year_boot(diff, years, seed=seed)
                say(f"oos\t{group}\tdepth {depth}\t{a} - {b}\t{diff.mean():+.5f} [{lo:+.5f}, {hi:+.5f}]")
            # SHAP interactions: all design rows, then each era-left-out fit
            full, nfull = boost(X, y, L, eras, depth, seed)
            strengths, allp = pair_strengths(full, X, L, g, kinds)
            fold_ranks = {}
            for e, mdl, tr in fold_models:
                st, _ = pair_strengths(mdl, X, L, g, kinds)
                st.sort(key=lambda r: -r["strength"])
                for rank, r in enumerate(st, 1):
                    fold_ranks.setdefault((r["state"], r["trigger"]), []).append(
                        (rank, r["sign"] if not np.isnan(r["sign"]) else 0.0))
            strengths.sort(key=lambda r: -r["strength"])
            say(f"# {group} depth {depth}: full fit {nfull} rounds; top pairs of any inputs: "
                + "; ".join(f"{a} x {b} {v:.4f}" for a, b, v in allp[:12]))
            say("pair\tgroup\tdepth\trank\tstate\ttrigger\tstrength (both known)\tsign\trows\tonsets\tfold ranks (era left out)\tfold signs\ttop10 folds\tsame-sign folds\ttheory\tstrength (all rows)\tper kind")
            for rank, r in enumerate(strengths, 1):
                fr = fold_ranks[(r["state"], r["trigger"])]
                sgn = np.sign(r["sign"]) if not np.isnan(r["sign"]) else 0.0
                top10 = sum(1 for k, _ in fr if k <= 10)
                same = sum(1 for _, s in fr if np.sign(s) == sgn and sgn != 0)
                pk = sorted(r["per_kind"].items(), key=lambda kv: -kv[1])
                say(f"pair\t{group}\t{depth}\t{rank}\t{r['state']}\t{r['trigger']}\t{r['strength']:.5f}\t{r['sign']:+.3f}"
                    f"\t{r['rows']}\t{r['onsets']}\t{','.join(str(k) for k, _ in fr)}"
                    f"\t{','.join(f'{s:+.2f}' for _, s in fr)}\t{top10}\t{same}"
                    f"\t{'theory' if (r['state'], r['trigger']) in THEORY else ''}"
                    f"\t{r['strength_all']:.5f}"
                    f"\t{'; '.join(f'{k} {v:.4f}' for k, v in pk)}")
    with open(dst, "w") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", dst)


if __name__ == "__main__":
    main(sys.argv)
