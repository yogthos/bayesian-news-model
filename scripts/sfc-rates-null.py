"""The holdout interval and power of the rate-channel test (bead bnm-4hv.15;
docs/COUPLED-SYSTEMS.md 9h). The skill of model m against comparator x on
the holdout's rows is set against its spread when the true skill is s,
simulated under an error model fitted to observed errors (as the review of
bnm-4hv.14 refitted 9f's, scripts/sfc-rules-null.py), not assumed:

  - the comparator's errors per country are Gaussian with, by outcome,
    the fitted correlation across origins at each horizon (rho_h ** gap),
    between the three- and five-year errors of one origin (c35), across
    the four sectors (net lending's C) and their relative scales;
  - a country scale, lognormal with the fitted spread of country RMSEs;
  - the model's errors sqrt(1 - s) (kappa e_x + sqrt(1 - kappa^2) e_i),
    kappa the fitted correlation of the model's errors with the
    comparator's.

    python -I scripts/sfc-rates-null.py fit.edn shape.edn --split test --m rates --x estimated \\
        [--sims 2000] [--seed 20261029] [--observed observed.edn] [--edn-out null.edn]

fit.edn: scored rows ({:iso :o :h :outcome :data :split :f {model value}})
whose --split rows the model is fitted to. shape.edn: the holdout's rows
(only :iso :o :h :outcome are read). Prints, per horizon group and outcome,
the fitted parameters, the null's q05 and q95 (the interval is [p - q95,
p - q05]), and the power: P(lo > -0.05) (non-inferior) and P(hi < -0.05)
(inferior) at true skills -0.2 ... +0.1. With --observed (scored rows; its
holdout split is read), each cell's point skill and interval. --edn-out writes
{[hg outcome] [q05 q95]} for bnm.sfc-rules-run rates-score.
"""
import collections
import re
import sys

import numpy as np

SECS = ["hh", "nfc", "fin", "gov"]
DELTA = 0.05


def rows(path):
    s = open(path).read()
    out = []
    pat = (r'\{:iso "(\w+)",\s*:o (\d+),\s*:h (\d+),\s*:outcome \[([^\]]*)\]'
           r'(?:,\s*:data ([^,\s]+))?(?:,\s*:split :(\w+))?(?:,\s*:f\s*\{([^}]*)\})?')
    for m in re.finditer(pat, s):
        iso, o, h, out_, d, sp, f = m.groups()
        fm = {}
        if f:
            fm = dict((k, float(v)) for k, v in re.findall(r':([\w-]+) ([-0-9.eE]+|nil)', f) if v != 'nil')
        out.append(dict(iso=iso, o=int(o), h=int(h), outcome=tuple(out_.replace(':', '').replace(',', ' ').split()),
                        data=float(d) if d and d != 'nil' else None, split=sp, f=fm))
    return out


def corr(a):
    a = np.asarray(a, float)
    return float(np.corrcoef(a.T)[0, 1]) if len(a) > 2 else 0.0


def fit(R, m, x, kind):
    def err(r, k): return r['f'][k] - r['data']
    T = [r for r in R if r['outcome'][0] == kind and m in r['f'] and x in r['f'] and r['data'] is not None]
    P = {}
    if kind == 'nl':
        by = collections.defaultdict(dict)
        for r in T:
            by[(r['iso'], r['o'], r['h'])][r['outcome'][1]] = err(r, x)
        M = np.array([[v[s] for s in SECS] for v in by.values() if all(s in v for s in SECS)])
        P['C'] = np.corrcoef(M.T)
        sd = M.std(0)
        P['scale'] = sd / np.sqrt(np.mean(sd ** 2))
    P['kappa'] = corr([(err(r, m), err(r, x)) for r in T])
    b = collections.defaultdict(dict)
    for r in T:
        b[(r['iso'], r['o'], r['outcome'])][r['h']] = err(r, x)
    P['c35'] = corr([(v[3], v[5]) for v in b.values() if 3 in v and 5 in v])
    c = collections.defaultdict(dict)
    for r in T:
        c[(r['iso'], r['h'], r['outcome'])][r['o']] = err(r, x)
    P['rho'] = {}
    for h in [1, 3, 5]:
        P['rho'][h] = max(0.0, corr([(v[o], v[o + 1]) for k, v in c.items() if k[1] == h for o in v if o + 1 in v]))
    rm = collections.defaultdict(list)
    for r in T:
        rm[r['iso']].append(err(r, x) ** 2)
    P['csd'] = float(np.std(np.log(np.sqrt([np.mean(v) for v in rm.values()])))) if len(rm) > 2 else 0.5
    return P


def cov(rk, P, kind):
    n = len(rk)
    S = np.zeros((n, n))
    for i, a in enumerate(rk):
        for j, b in enumerate(rk):
            do = abs(a[1] - b[1])
            if a[2] == b[2]:
                t = P['rho'][a[2]] ** do
            elif {a[2], b[2]} == {3, 5}:
                t = P['c35'] * np.sqrt(P['rho'][3] * P['rho'][5]) ** do
            else:
                t = 0.0
            if kind == 'nl':
                t *= P['C'][SECS.index(a[3][1]), SECS.index(b[3][1])] * P['scale'][SECS.index(a[3][1])] * P['scale'][SECS.index(b[3][1])]
            elif a[3] != b[3]:
                t = 0.0
            S[i, j] = t
    w, V = np.linalg.eigh(S)
    return V * np.sqrt(np.clip(w, 0, None))


def simulate(shape, P, kind, s, sims, rng):
    rk = [r for r in shape if r[3][0] == kind]
    isos = sorted(set(r[0] for r in rk))
    L = {iso: cov([r for r in rk if r[0] == iso], P, kind) for iso in isos}
    k = P['kappa']
    out = np.empty(sims)
    for t in range(sims):
        sm = sx = 0.0
        for iso in isos:
            Li = L[iso]
            n = Li.shape[0]
            sc = np.exp(P['csd'] * rng.standard_normal())
            zx = Li @ rng.standard_normal(n)
            zi = Li @ rng.standard_normal(n)
            ex = sc * zx
            em = sc * np.sqrt(1 - s) * (k * zx + np.sqrt(max(0.0, 1 - k * k)) * zi)
            sm += (em ** 2).sum()
            sx += (ex ** 2).sum()
        out[t] = 1 - sm / sx
    return out


def skill(rs, m, x):
    def sse(k): return sum((r['f'][k] - r['data']) ** 2 for r in rs)
    return 1 - sse(m) / sse(x)


def main(argv):
    fitp, shapep = argv[0], argv[1]
    opt = dict(zip(argv[2::2], argv[3::2]))
    split, m, x = opt.get('--split', 'test'), opt.get('--m', 'rates'), opt.get('--x', 'estimated')
    sims, seed = int(opt.get('--sims', 2000)), int(opt.get('--seed', 20261029))
    R = [r for r in rows(fitp) if r['split'] == split]
    shape = [(r['iso'], r['o'], r['h'], r['outcome']) for r in rows(shapep)]
    obs = [r for r in rows(opt['--observed']) if r['split'] == 'holdout'] if '--observed' in opt else None
    qs = {}
    print(f"fitted on {len(R)} {split} rows ({m} against {x}); holdout shape {len(shape)} rows, {len(set(s[0] for s in shape))} countries")
    for kind in ['nl', 'debt']:
        P = fit(R, m, x, kind)
        print(f"{kind}: kappa {P['kappa']:.4f} c35 {P['c35']:.2f} rho {({h: round(v, 2) for h, v in P['rho'].items()})} "
              f"country-scale sd {P['csd']:.2f}" + (f" sector scales {np.round(P['scale'], 2).tolist()}" if kind == 'nl' else ''))
        for hg, hs in [('h1', {1}), ('h35', {3, 5})]:
            sh = [r for r in shape if r[2] in hs]
            if not any(r[3][0] == kind for r in sh):
                continue
            ki = {'nl': 1, 'debt': 2}[kind] * 10 + {'h1': 1, 'h35': 2}[hg]
            null = simulate(sh, P, kind, 0.0, sims, np.random.default_rng([seed, ki, 0]))
            q05, q95 = np.quantile(null, 0.05), np.quantile(null, 0.95)
            qs[(hg, kind)] = (q05, q95)
            pw = []
            for si, s in enumerate([-0.2, -0.1, -0.05, 0.0, 0.05, 0.1]):
                v = simulate(sh, P, kind, s, 1000, np.random.default_rng([seed, ki, si + 1]))
                pw.append(f"{s:+.2f}: {np.mean(v - q95 > -DELTA):.2f}/{np.mean(v - q05 < -DELTA):.2f}")
            line = f"  {hg} {kind:4s} rows {sum(1 for r in sh if r[3][0] == kind)}  null q05 {q05:+.4f} q95 {q95:+.4f}"
            if obs:
                rs = [r for r in obs if r['outcome'][0] == kind and r['h'] in hs and m in r['f'] and x in r['f'] and r['data'] is not None]
                if rs:
                    p = skill(rs, m, x)
                    line += f"  observed {p:+.4f} [{p - q95:+.4f}, {p - q05:+.4f}] (n {len(rs)})"
            print(line)
            print("      power P(lo > -0.05)/P(hi < -0.05) by true skill  " + "  ".join(pw))
    if '--edn-out' in opt:
        with open(opt['--edn-out'], 'w') as f:
            f.write("{" + " ".join(f"[:{hg} :{k}] [{a!r} {b!r}]" for (hg, k), (a, b) in sorted(qs.items())) + "}\n")


if __name__ == "__main__":
    main(sys.argv[1:])
