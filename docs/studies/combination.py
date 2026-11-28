"""bnm-7yg.8.6 (R1, registered in docs/CALIBRATION.md, "R1 registration in
detail"): does combining the year node's group models beat the averaging's
selection? Reads docs/studies/combination_tap.clj's tables and scores, at
December origins 2003-2022 (year country rows), t162's issued p against a
median pool, a log pool, a causal non-negative stack, a causal reference
logit on the node's inputs, the latest food inflation (food only) and the
clim/own baselines; group increments, the stack's stability, the 2003-06
dependence and the data-rich/poor halves; then each kind's flags.

  python3 -I docs/studies/combination.py <repo> <tap-dir> [k1,k2] [run]

The selected is <run>'s (t162's) issued p, which differs from the lattice's
where the run recalibrates a kind's year rows after (inflation crises).
"""
import importlib.util
import math
import os
import sys
from collections import defaultdict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


ref = _load("reference")
sc = _load("scorecard")

FROM, TO = 2003, 2022
TRAIN_FROM = 1975
DRAWS = 2000
STAB_DRAWS = 200
EPS = 1e-6


def expit(z):
    if np.ndim(z):
        return 1 / (1 + np.exp(-np.asarray(z, float)))
    return 1 / (1 + math.exp(-z))


def logit(p):
    p = np.clip(np.asarray(p, float), EPS, 1 - EPS)
    return np.log(p / (1 - p))


# --- pools -------------------------------------------------------------------

def log_pool(F, live):
    """expit of the mean live log odds; nan where none is live."""
    n = live.sum(1)
    s = np.where(live, F, 0.0).sum(1)
    return np.where(n > 0, expit(s / np.maximum(n, 1)), np.nan)


def median_pool(F, live):
    """The median of the live models' probabilities; nan where none is live."""
    out = np.full(len(F), np.nan)
    for i in np.where(live.any(1))[0]:
        out[i] = float(np.median(expit(F[i][live[i]])))
    return out


# --- the node's offsets (R1 amendment) ----------------------------------------

def parse_osz(s):
    return [(float(a), float(b)) for a, b in (t.split(":") for t in s.split(";") if t)]


def mean_offset(osz):
    """The offset whose probability is the offsets' average (as the lattice's o)."""
    return float(logit(sum(w * expit(o) for w, o in osz)))


def integrate(e, osz):
    """p_i = sum_j w_ij expit(o_ij + e_i): log odds e_i beyond the offset,
    integrated over row i's offsets after the frailty."""
    return np.array([sum(w * expit(o + float(ei)) for w, o in oz) for ei, oz in zip(e, osz)])


def log_pool_osz(E, live, osz):
    n = live.sum(1)
    m = np.where(live, E, 0.0).sum(1) / np.maximum(n, 1)
    return np.where(n > 0, integrate(m, osz), np.nan)


def median_pool_osz(E, live, osz):
    out = np.full(len(E), np.nan)
    for i in np.where(live.any(1))[0]:
        out[i] = float(np.median([sum(w * expit(o + e) for w, o in osz[i]) for e in E[i][live[i]]]))
    return out


# --- fits --------------------------------------------------------------------

def _ridge_logit(Z, y, pen, iters=60, offset=None):
    b = np.zeros(Z.shape[1])
    off = np.zeros(len(y)) if offset is None else offset
    if offset is None:
        rate = min(max(y.mean(), 1e-4), 1 - 1e-4)
        b[0] = math.log(rate / (1 - rate))
    for _ in range(iters):
        p = expit(Z @ b + off)
        g = Z.T @ (p - y) + pen * b
        H = (Z * (p * (1 - p))[:, None]).T @ Z + np.diag(pen) + 1e-9 * np.eye(len(b))
        step = np.linalg.solve(H, g)
        b -= step
        if np.max(np.abs(step)) < 1e-8:
            break
    return b


def fit_stack(F, y, lam=1.0, offset=None):
    """logit p = offset + a + F b, b >= 0, ridge lam on b, by an active set:
    fit, pin the negative weights to 0, refit the rest, until none is
    negative."""
    k = F.shape[1]
    active = np.ones(k, bool)
    while active.any():
        Z = np.column_stack([np.ones(len(y)), F[:, active]])
        b = _ridge_logit(Z, y, np.r_[0.0, np.full(int(active.sum()), lam)], offset=offset)
        w = np.zeros(k)
        w[active] = b[1:]
        if (w >= 0).all():
            return float(b[0]), w
        active &= w > 0
    return float(_ridge_logit(np.ones((len(y), 1)), y, np.zeros(1), offset=offset)[0]), np.zeros(k)


def train_mask(origin, known, o):
    return (known <= o) & (origin >= TRAIN_FROM * 12)


# --- scores ------------------------------------------------------------------

def block_ci(d, years, draws=DRAWS, seed=1):
    if len(d) == 0:
        return (float("nan"),) * 3
    rng = np.random.default_rng(seed)
    ys = np.unique(years)
    by = {v: d[years == v] for v in ys}
    ms = [np.concatenate([by[v] for v in rng.choice(ys, len(ys))]).mean() for _ in range(draws)]
    return float(d.mean()), float(np.percentile(ms, 5)), float(np.percentile(ms, 95))


def effective_n(w):
    w = np.asarray(w, float)
    w = w[w > 0] / w[w > 0].sum()
    return float(math.exp(-(w * np.log(w)).sum()))


def beats(log_ci, brier_gain):
    """A comparator beats the selected forecast: its log-score gain's 90%
    interval above 0 and its Brier gain's point estimate above 0."""
    return log_ci[1] > 0 and brier_gain > 0


def flags(res, stable, increments_zero, halves_differ):
    """The registered flags, literally; res {comparator: (log ci, Brier gain)}
    against the selected forecast."""
    b = {k: beats(*v) for k, v in res.items()}
    stack, fixed = b.get("stack", False), b.get("log pool", False) or b.get("median pool", False)
    out = []
    if (stack or fixed) and not (stack and not fixed and not stable):
        out.append("combination")
    if (fixed and not stack) or (stack and not stable):
        out.append("estimation")
    if not any(b.values()) and increments_zero:
        out.append("inputs")
    if b.get("reference") or b.get("own") or b.get("latest") or halves_differ:
        out.append("target/coverage")
    return out or ["selection holds"]


def row_log(p, y):
    p = np.clip(p, EPS, 1 - EPS)
    return np.where(y, np.log(p), np.log(1 - p))


def summary(p, y, origin, place):
    ps = list(zip(p.tolist(), y.tolist()))
    rows = list(zip(origin.tolist(), place, p.tolist(), y.tolist()))
    return {"log": float(row_log(p, y).mean()), "brier": float(((p - y) ** 2).mean()),
            "auc": sc.auc(ps), "within": sc.within_auc(rows), "p/rate": float(p.mean() / max(y.mean(), 1e-9))}


# --- reading -----------------------------------------------------------------

def read(path):
    with open(path) as fh:
        head = fh.readline().rstrip("\n").split("\t")
        lines = [l.rstrip("\n").split("\t") for l in fh]
    fcols = [i for i, h in enumerate(head) if h.startswith("f:")]
    wcols = [i for i, h in enumerate(head) if h.startswith("w:")]
    x0 = max(wcols) + 1

    def num(s):
        return float(s) if s != "" else np.nan
    d = {"groups": [head[i][2:] for i in fcols],
         "place": [l[0] for l in lines],
         "origin": np.array([int(l[1]) for l in lines]),
         "y": np.array([l[2] == "true" for l in lines]),
         "known": np.array([int(l[3]) for l in lines]),
         "p": np.array([num(l[5]) for l in lines]),
         "o": np.array([num(l[4]) for l in lines]),
         "osz": [parse_osz(l[6]) for l in lines],
         "F": np.array([[num(l[i]) for i in fcols] for l in lines]),
         "W": np.array([[num(l[i]) for i in wcols] for l in lines]),
         "X": np.array([[num(v) for v in l[x0:]] for l in lines])}
    d["live"] = (d["W"] > 0) & ~np.isnan(d["F"])
    # each model's log odds beyond the node's mean offset, and the offset the
    # issued p is integrated over, as one number for fitting
    d["E"] = d["F"] - d["o"][:, None]
    d["o_eff"] = np.array([mean_offset(oz) for oz in d["osz"]])
    return d


def read_issued(path):
    """A run's issued year country forecasts, {(place, origin): p}."""
    out = {}
    with open(path) as fh:
        for l in fh:
            c = l.rstrip("\n").split("\t")
            if c[0] == "year" and len(c[1]) == 3:
                out[(c[1], int(c[2]))] = float(c[3])
    return out


def with_issued(d, issued):
    """d with p the run's issued forecast where it has one (a kind whose year
    rows are recalibrated after the lattice issues a p different from the
    lattice's), the lattice's kept as "lattice p" for the rebuild check."""
    e = dict(d)
    e["lattice p"] = d["p"]
    e["p"] = np.array([issued.get((u, int(o)), p) for u, o, p in zip(d["place"], d["origin"], d["p"])])
    return e


# --- the study ----------------------------------------------------------------

def causal(d, fit_predict, extra_train=None):
    """p at the December origins FROM-TO, each from a fit on the rows known by
    its origin (and in extra_train); nan elsewhere."""
    out = np.full(len(d["y"]), np.nan)
    yrs = d["origin"] // 12
    for o in sorted(set(d["origin"][(yrs >= FROM) & (yrs <= TO)])):
        tr = train_mask(d["origin"], d["known"], o)
        if extra_train is not None:
            tr = tr & extra_train
        te = d["origin"] == o
        if d["y"][tr].sum() < 5:
            continue
        out[te] = fit_predict(tr, te)
    return out


def stack_inputs(d):
    return np.where(d["live"], d["F"], 0.0)


def stack_forecast(d, extra_train=None):
    S, yf = stack_inputs(d), d["y"].astype(float)

    def fp(tr, te):
        a, b = fit_stack(S[tr], yf[tr])
        return expit(a + S[te] @ b)
    return causal(d, fp, extra_train)


def stack_forecast_osz(d, extra_train=None):
    S, yf = np.where(d["live"], d["E"], 0.0), d["y"].astype(float)

    def fp(tr, te):
        a, b = fit_stack(S[tr], yf[tr], offset=d["o_eff"][tr])
        return integrate(a + S[te] @ b, [d["osz"][i] for i in np.where(te)[0]])
    return causal(d, fp, extra_train)


def reference_forecast_osz(d):
    X = np.nan_to_num(d["X"], nan=0.0)
    yf = d["y"].astype(float)

    def fp(tr, te):
        Xtr, Xte = ref.standardize(X[tr], X[te])
        Z = lambda M: np.column_stack([np.ones(len(M)), M])
        pen = np.r_[0.0, np.full(Xtr.shape[1], ref.LAMBDA)]
        b = _ridge_logit(Z(Xtr), yf[tr], pen, offset=d["o_eff"][tr])
        return integrate(Z(Xte) @ b, [d["osz"][i] for i in np.where(te)[0]])
    return causal(d, fp)


def reference_forecast(d, drop_flags=False):
    X = np.nan_to_num(d["X"], nan=0.0)
    if drop_flags:
        X = X[:, [j for j in range(X.shape[1]) if not set(np.unique(X[:, j]).tolist()) <= {0.0, 1.0}]]
    yf = d["y"].astype(float)

    def fp(tr, te):
        Xtr, Xte = ref.standardize(X[tr], X[te])
        b0, b = ref.fit(Xtr, yf[tr])
        return expit(b0 + Xte @ b)
    return causal(d, fp)


def _causal_logit(d, Z, pen):
    yf = d["y"].astype(float)
    return causal(d, lambda tr, te: expit(Z[te] @ _ridge_logit(Z[tr], yf[tr], pen)))


def increment(d, g):
    """Forecasts without and with group g's departure from the selected log
    odds, each a causal recalibration of the selected."""
    fs = logit(d["p"])
    dep = np.where(d["live"][:, g], d["E"][:, g] - (fs - d["o_eff"]), 0.0)
    one = np.ones(len(fs))
    base = _causal_logit(d, np.column_stack([one, fs]), np.array([0.0, 1e-3]))
    plus = _causal_logit(d, np.column_stack([one, fs, dep]), np.array([0.0, 1e-3, 1.0]))
    return base, plus


def stability(d, year):
    """The largest CV of the stack's three largest weights over STAB_DRAWS
    refits on origin-resampled training rows, at December of `year`."""
    tr = np.where(train_mask(d["origin"], d["known"], year * 12 + 11))[0]
    S, yf, off = np.where(d["live"], d["E"], 0.0), d["y"].astype(float), d["o_eff"]
    _, full = fit_stack(S[tr], yf[tr], offset=off[tr])
    top = [i for i in np.argsort(-full)[:3] if full[i] > 0]
    if not top:
        return 0.0, []
    rng = np.random.default_rng(3)
    yrs = d["origin"][tr] // 12
    idx = {v: tr[yrs == v] for v in np.unique(yrs)}
    uy = list(idx)
    ws = np.array([fit_stack(S[p], yf[p], offset=off[p])[1][top]
                   for p in (np.concatenate([idx[v] for v in rng.choice(uy, len(uy))]) for _ in range(STAB_DRAWS))])
    cv = ws.std(0) / np.maximum(np.abs(ws.mean(0)), 1e-9)
    return float(cv.max()), [(d["groups"][i], float(full[i]), float(c)) for i, c in zip(top, cv)]


def compare(q, d, sel, years, mask):
    m = mask & ~np.isnan(q)
    lg = row_log(q[m], d["y"][m]) - row_log(sel[m], d["y"][m])
    br = (sel[m] - d["y"][m]) ** 2 - (q[m] - d["y"][m]) ** 2
    return block_ci(lg, years[m]), float(br.mean()) if m.any() else float("nan"), int(m.sum())


def run_kind(tap_dir, kind, latest=None, issued=None):
    d = read(os.path.join(tap_dir, kind + ".tsv"))
    d = with_issued(d, issued or {})
    years = d["origin"] // 12
    win = (years >= FROM) & (years <= TO)
    sel = d["p"]
    places = lambda m: [d["place"][i] for i in np.where(m)[0]]
    out = {"kind": kind, "rows": int(win.sum()), "onsets": int(d["y"][win].sum()), "groups": d["groups"]}
    comps = {"log pool": log_pool_osz(d["E"], d["live"], d["osz"]),
             "median pool": median_pool_osz(d["E"], d["live"], d["osz"]),
             "stack": stack_forecast_osz(d), "reference": reference_forecast_osz(d),
             "reference without 0/1": reference_forecast(d, drop_flags=True)}
    registered = {"log pool, no offsets": log_pool(d["F"], d["live"]), "median pool, no offsets": median_pool(d["F"], d["live"]),
                  "stack, no offsets": stack_forecast(d), "reference, no offsets": reference_forecast(d)}
    W = np.where(d["live"], d["W"], 0.0)
    W = W / np.maximum(W.sum(1, keepdims=True), 1e-300)
    rebuilt = np.array([sum(w * sum(wg * expit(o + e) for wg, e in zip(W[i], d["E"][i]) if wg > 0) for w, o in d["osz"][i])
                        for i in range(len(d["p"]))])
    lp = d["lattice p"]
    out["rebuilt"] = (float(np.corrcoef(rebuilt[win], lp[win])[0, 1]), float(np.abs(rebuilt[win] - lp[win]).mean()))
    out["recalibrated rows"] = int((win & (np.abs(sel - lp) > 1e-12)).sum())
    allrows = {(u, int(o)): (float(p), bool(y)) for u, o, p, y in zip(d["place"], d["origin"], d["p"], d["y"])}
    clim, own = sc.baselines(allrows)
    for nm, b in (("clim", clim), ("own", own)):
        comps[nm] = np.array([b[(u, int(o))][0] if (u, int(o)) in b else np.nan for u, o in zip(d["place"], d["origin"])])
    if latest is not None:
        comps["latest"] = latest(d)
    out["scores"] = {"selected": summary(sel[win], d["y"][win], d["origin"][win], places(win))}
    res = {}
    for nm, q in comps.items():
        m = win & ~np.isnan(q)
        out["scores"][nm] = summary(q[m], d["y"][m], d["origin"][m], places(m))
        ci, brg, n = compare(q, d, sel, years, win)
        out["scores"][nm]["n"] = n
        res[nm] = (ci, brg)
    out["against selected"] = res
    out["registered, no offsets"] = {}
    for nm, q in registered.items():
        m = win & ~np.isnan(q)
        ci, brg, n = compare(q, d, sel, years, win)
        out["registered, no offsets"][nm] = (summary(q[m], d["y"][m], d["origin"][m], places(m)), ci, brg)
    eff = defaultdict(list)
    for i in np.where(win)[0]:
        if d["live"][i].any():
            eff[int(years[i])].append(effective_n(np.where(d["live"][i], d["W"][i], 0.0)))
    out["effective n by year"] = {y: float(np.mean(v)) for y, v in sorted(eff.items())}
    out["effective n"] = float(np.mean(list(out["effective n by year"].values()))) if eff else float("nan")
    incs = []
    for g in range(len(d["groups"])):
        if not d["live"][win, g].any():
            continue
        base, plus = increment(d, g)
        m = win & ~np.isnan(base) & ~np.isnan(plus)
        incs.append((d["groups"][g], block_ci(row_log(plus[m], d["y"][m]) - row_log(base[m], d["y"][m]), years[m])))
    out["increments"] = incs
    inc_zero = all(lo <= 0 <= hi for _, (_, lo, hi) in incs)
    out["stability"] = {y: stability(d, y) for y in (2012, 2022)}
    stable = all(cv <= 0.5 for cv, _ in out["stability"].values())
    late = win & (years >= 2020)
    no_early = stack_forecast_osz(d, extra_train=~((years >= 2003) & (years <= 2006)))
    out["2020-22 log"] = {}
    for nm, q in (("selected", sel), ("stack", comps["stack"]), ("stack without 2003-06", no_early), ("log pool", comps["log pool"])):
        m = late & ~np.isnan(q)
        out["2020-22 log"][nm] = float(row_log(q[m], d["y"][m]).mean())
    share = (np.nan_to_num(d["X"], nan=0.0) != 0).mean(1)
    med = float(np.median(share[win]))
    out["half rows"] = {"rich": int((win & (share > med)).sum()), "poor": int((win & (share <= med)).sum())}
    out["halves"] = {hn: {nm: beats(*compare(q, d, sel, years, win & hm)[:2]) for nm, q in comps.items()}
                     for hn, hm in (("rich", share > med), ("poor", share <= med))}
    differ = any(out["halves"]["rich"][nm] != out["halves"]["poor"][nm] for nm in comps)
    out["flags"] = flags(res, stable, inc_zero, differ)
    return out


def food_latest(repo):
    fs = _load("food_split")
    series = fs.read_series(os.path.join(repo, "data/slow/series.tsv"), lambda u, v: v == "food/food_inflation")

    def latest(d):
        x = np.full(len(d["place"]), np.nan)
        for i, (u, o) in enumerate(zip(d["place"], d["origin"])):
            s = series.get((u, "food/food_inflation"))
            r = fs.latest(s, int(o)) if s else None
            if r:
                x[i] = math.copysign(math.log1p(abs(r[1])), r[1])
        ok = ~np.isnan(x)
        yf = d["y"].astype(float)

        def fp(tr, te):
            t2 = tr & ok
            b = _ridge_logit(np.column_stack([np.ones(t2.sum()), x[t2]]), yf[t2], np.array([0.0, 1.0]))
            return np.where(ok[te], expit(b[0] + b[1] * np.nan_to_num(x[te])), np.nan)
        return causal(d, fp)
    return latest


def fmt_ci(c):
    return f"{c[0]:+.5f} [{c[1]:+.5f}, {c[2]:+.5f}]"


def report(r):
    print(f"\n== {r['kind']}: rows {r['rows']}, onsets {r['onsets']}, groups {len(r['groups'])}, "
          f"effective n of models {r['effective n']:.2f}; flags: {', '.join(r['flags'])}")
    print(f"   the models, weights and offsets rebuild the lattice's p: correlation {r['rebuilt'][0]:.4f}, mean |diff| {r['rebuilt'][1]:.5f}"
          f"; rows where t162 issued a recalibrated p: {r['recalibrated rows']}")
    print(f"   {'forecast':<22} {'n':>5} {'log':>9} {'Brier':>8} {'AUC':>6} {'within':>6} {'p/rate':>6}   log gain vs selected [90%]      Brier gain")
    s = r["scores"]["selected"]
    print(f"   {'selected (t162)':<22} {r['rows']:>5} {s['log']:+.5f} {s['brier']:.5f} {s['auc']:.3f} {s['within']:.3f} {s['p/rate']:.2f}")
    for nm, (ci, brg) in r["against selected"].items():
        s = r["scores"][nm]
        print(f"   {nm:<22} {s['n']:>5} {s['log']:+.5f} {s['brier']:.5f} {s['auc']:.3f} {s['within']:.3f} {s['p/rate']:.2f}   "
              f"{fmt_ci(ci)}  {brg:+.5f}{'  BEATS' if beats(ci, brg) else ''}")
    for nm, (s, ci, brg) in r["registered, no offsets"].items():
        print(f"   {nm:<22} {'':>5} {s['log']:+.5f} {s['brier']:.5f} {s['auc']:.3f} {s['within']:.3f} {s['p/rate']:.2f}   {fmt_ci(ci)}  {brg:+.5f}  (registered form, no flag)")
    print("   group increments (log, 90%): " + "; ".join(f"{g} {fmt_ci(c)}" for g, c in r["increments"]))
    for y, (cv, top) in r["stability"].items():
        print(f"   stack at {y}: largest CV {cv:.2f}; top " + ", ".join(f"{g} {w:.2f} (cv {c:.2f})" for g, w, c in top))
    print("   2020-22 log: " + ", ".join(f"{k} {v:+.5f}" for k, v in r["2020-22 log"].items()))
    print(f"   halves at the median share of non-zero inputs: rich {r['half rows']['rich']} rows, poor {r['half rows']['poor']}")
    print("   beats selected, rich/poor half: " + ", ".join(f"{nm} {int(r['halves']['rich'][nm])}/{int(r['halves']['poor'][nm])}" for nm in r["halves"]["rich"]))
    print("   effective n by year: " + " ".join(f"{y}:{v:.2f}" for y, v in r["effective n by year"].items()))


def main(argv):
    repo, tap = argv[0], argv[1]
    kinds = argv[2].split(",") if len(argv) > 2 else sorted(f[:-4] for f in os.listdir(tap) if f.endswith(".tsv"))
    run = argv[3] if len(argv) > 3 else "t162"
    for k in kinds:
        issued = read_issued(os.path.join(repo, "data/runs", run, k, "forecasts.tsv"))
        report(run_kind(tap, k, latest=food_latest(repo) if k == "food_price_shock" else None, issued=issued))
        sys.stdout.flush()


if __name__ == "__main__":
    main(sys.argv[1:])
