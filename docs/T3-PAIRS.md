# T3 pairs: boosting with SHAP (T7) and the named interactions (T3)

Beads bnm-3qu.8 (T7) and bnm-3qu.4 (T3); docs/LIT-NEXT-STEPS.md, ranked
trials 3 and 7. T7 is a diagnostic: shallow boosted trees on the design
years, read for their SHAP interaction values, to choose or veto the
state × trigger pairs T3 tests. The logit stays the model (Beutel et al.
2019). Everything here uses origins 1960-2000, so no outcome after 2002
is read.

## Rows, states and triggers

The rows are T2's: each kind's year-end forecast from the year node of
t44-world-norel. The outcome is an onset in (t, t+2], seen from the end
of year t. L is the base's log odds. There are two groups:
- financial: banking, currency, sovereign default, growth collapse,
  fiscal consolidation, inflation, market crash. 30,521 rows, 2,773
  onsets.
- political: protest surge, violence surge, coup, conflict onset, war
  onset. 24,327 rows, 2,117 onsets.

Every input is read as known at the end of year t (tools/bnm/interactions.clj).

**States** are demeaned by year and scaled on the design years, signed so
that stretched is high. They are T1's named states with two changes:
reserves become low reserves, and food import exposure is replaced by the
plain food import share, because the grain gap is a trigger here. The
agriculture share of GDP is added. The full list: credit gap, Keen debt
gap, debt service, low reserves, fuel price gap, youth bulge, elite
overproduction, anocracy, technology gap, food import share, agriculture
share.

**Triggers** are scaled on the design years and signed so that stress is
high. They are not demeaned, because a world tightening is a trigger too.

| Trigger | Definition | Rows with it (fin / pol) |
|---|---|---|
| house-growth | 3-year real house-price growth: BIS, else JST (as T2) | 5,319 / 3,364 |
| equity-drawdown | log share prices' fall from the 24-month peak (OECD) | 6,271 / 4,009 |
| tightening | 12-month change in the 3-month rate (OECD), else the policy rate (BIS), else JST's short rate a year late | 6,667 / 4,259 |
| tot-fall | 12-month fall in the log commodity terms of trade (IMF, from 1980) | 15,057 / 13,589 |
| export-price-fall | fuel, metal and food export shares (WDI) × the year's change in world log oil, metal and grain prices | 17,753 / 13,669 |
| grain-gap | world grain price gap (wheat, maize, rice cycles); the same at every country | 26,417 / 21,444 |
| drought | minus the mean log precipitation ratio over the latest 12 months known (CRU) | 30,301 / 24,026 |
| election | legislative or presidential election held in year t (V-Dem) | 29,137 / 23,183 |
| neighbour-conflict | log(1 + conflict, war, violence-surge and coup onsets in the rest of the subregion over 36 months, as known) | 30,521 / 24,327 |

Two definitions need a caveat:
- *Elections.* V-Dem publishes the election flag the next March. An
  election is public on the day it is held, so year t counts as known at
  the end of t.
- *Neighbourhood conflict.* This uses the subregion (UN M49), not
  contiguity: the store has no contiguity table. Each catalogue's
  known-lag applies, which mostly makes it a 1-4-year-old count.

## T7: the diagnostic

```
jolt -A:tools -M -m bnm.interactions --export data/runs/sequencing/t7-design-rows.tsv
python -I scripts/t7_shap.py data/runs/sequencing/t7-design-rows.tsv data/runs/sequencing/t7-shap.tsv
```

The script runs on a research venv with xgboost 3.4.1 and uses xgboost's
own TreeSHAP (`pred_interactions`). For each group:
- **Boosted trees.** Depth 2 (primary: depth-2 trees hold exactly pairwise
  interactions) and depth 3, with eta 0.05, subsample and column sample
  0.8, and min child weight 5. L is the offset (base_margin) and also a
  feature, so the trees can recalibrate. The other features are kind
  dummies, the states and the triggers; the trees route missing values
  themselves. Rounds are chosen by leave-era-out within the fitting eras.
- **Logit with the same inputs.** It has the recalibration (L, kind
  dummies, L × kind), the states and triggers (0 where unknown, plus a
  flag), and ridge 1e-3. The recalibration alone is fitted as well.
- **Era rotation.** Leave one era out, with eras 1960-69, 1970-79,
  1980-89 and 1990-2000 by origin.
- **Strength of a pair.** The mean of |φ_ij + φ_ji| over the rows where
  both inputs are known. Where one is missing, the trees' default branch
  stands for coverage, not for the pair; the mean over all rows is in the
  output for the record.
- **Sign of a pair.** The correlation of φ_ij + φ_ji with the centred
  product of the two inputs.
- **Stability.** Each pair's rank in the four era-left-out fits.

### Forecast check (leave one era out, mean log score per row)

| Group | raw base | recalibrated | logit, same inputs | trees d2 | trees d3 |
|---|---|---|---|---|---|
| financial | -0.31348 | -0.28598 | **-0.27730** | -0.28211 | -0.28303 |
| political | -0.28658 | **-0.26707** | -0.26889 | -0.27137 | -0.27063 |

The 90% intervals come from resampled origin years:
- **Trees against the logit:** financial -0.0048 [-0.0082, -0.0020] at
  depth 2 and -0.0057 [-0.0091, -0.0027] at depth 3; political -0.0025
  [-0.0047, -0.0003] and -0.0017 [-0.0044, +0.0006]. The trees never beat
  the logit with the same inputs.
- **Logit against the recalibration:** financial +0.0087 [+0.0054,
  +0.0124], political -0.0018 [-0.0033, -0.0002].

This agrees with Beutel et al. (2019) and with Neunhoeffer & Sternberg
(2019): out of era, nothing nonlinear survives that a logit with the
levels does not already carry.

Two other readings:
- The levels help the financial kinds within the design period. This is
  not the out-of-period test T4 and T8 ran.
- In both groups, the trees' largest interactions are L × kind (the
  recalibration slopes) and L × the technology gap: the base's
  miscalibration, as T2 and T8 found, is bigger than any state × trigger
  pair.

### Ranking: state × trigger pairs, depth 2 (top 10 of 99)

Fold ranks are the pair's rank in the fit leaving out 1960s, 1970s, 1980s
and 1990s, in that order.

| Group | Rank | Pair | Strength | Sign | Onsets (both known) | Fold ranks | Fold signs |
|---|---|---|---|---|---|---|---|
| financial | 1 | youth bulge × grain gap | 0.0387 | -0.60 | 2,456 | 1, 1, 1, 72 | -.62 -.57 -.49 .00 |
| financial | 2 | credit gap × tightening (theory) | 0.0247 | +0.12 | 426 | 3, 36, 9, 36 | +.13 +.15 +.15 .00 |
| financial | 3 | agriculture share × export price fall | 0.0133 | +0.64 | 1,383 | 10, 96, 6, 1 | +.56 .00 +.25 +.63 |
| financial | 4 | youth bulge × neighbour conflict | 0.0124 | +0.71 | 2,456 | 83, 3, 8, 6 | .00 +.82 +.77 +.55 |
| financial | 5 | low reserves × equity drawdown | 0.0121 | +0.71 | 406 | 75, 81, 39, 57 | 0 in all |
| financial | 6 | technology gap × tightening | 0.0120 | +0.01 | 498 | 7, 2, 23, 5 | +.04 +.04 +.05 +.05 |
| financial | 7 | food import share × tot fall | 0.0092 | +0.06 | 1,194 | 27, 30, 17, 90 | -.39 -.41 -.18 .00 |
| financial | 8 | youth bulge × tightening | 0.0085 | -0.02 | 466 | 59, 11, 59, 4 | mixed |
| financial | 9 | fuel price gap × grain gap | 0.0083 | -0.45 | 1,901 | 2, 16, 4, 65 | -.39 +.40 -.50 .00 |
| financial | 10 | food import share × neighbour conflict | 0.0080 | -0.68 | 1,907 | 44, 24, 64, 94 | all - |
| political | 1 | low reserves × equity drawdown | 0.0141 | -0.33 | 228 | 45, 2, 9, 53 | .00 -.33 -.46 .00 |
| political | 2 | low reserves × grain gap | 0.0080 | -0.51 | 1,063 | 2, 38, 41, 55 | -.48 .00 .00 .00 |
| political | 3 | technology gap × grain gap | 0.0062 | +0.32 | 1,678 | 12, 4, 13, 10 | +.25 +.30 +.35 +.57 |
| political | 4 | credit gap × export price fall | 0.0047 | -0.37 | 810 | 13, 5, 17, 34 | -.25 -.38 .00 -.22 |
| political | 5 | youth bulge × export price fall | 0.0038 | -0.37 | 1,102 | 17, 53, 11, 32 | mixed |
| political | 6 | fuel price gap × tightening | 0.0037 | -0.08 | 253 | 53, 43, 46, 60 | 0 in all |
| political | 7 | anocracy × drought | 0.0027 | +0.50 | 2,065 | 77, 73, 74, 18 | only 1990s |
| political | 8 | anocracy × export price fall | 0.0017 | -0.33 | 1,137 | 14, 71, 72, 14 | mixed |
| political | 9 | technology gap × drought | 0.0011 | -0.58 | 1,925 | 83, 80, 80, 12 | only 1990s |
| political | 10 | youth bulge × equity drawdown | 0.0010 | -0.05 | 256 | 4, 7, 53, 68 | mixed |

At depth 3 the top five are:
- financial: youth bulge × grain gap, youth bulge × neighbour conflict,
  technology gap × tightening, credit gap × tightening, and anocracy ×
  grain gap;
- political: agriculture share, fuel price gap, technology gap and low
  reserves each × equity drawdown (all on 200-280 onsets, unstable),
  then technology gap × grain gap.

The interactions are much weaker for the political kinds. Their trees
stop at 38-250 rounds, against 190-560 for the financial kinds.

**The theory pairs** (LIT-NEXT-STEPS 1b and ranked trial 3), depth 2
rank in each group:

| Pair | Financial | Political |
|---|---|---|
| credit gap × tightening | 2 (top 10 in 2 of 4 eras; + in 3) | 0 |
| fuel price gap × tot fall | 15 (- sign) | 0 |
| credit gap × house growth | 23 (+ in all four eras; 369 onsets) | n/a |
| food import share × grain gap | 24 | 0 |
| anocracy × grain gap | 25 | 0 |
| fuel price gap × export price fall | 30 (- sign) | 0 |
| agriculture share × drought | 39 | 0 |
| anocracy × neighbour conflict | 64 | 0 |
| Keen debt gap × tightening | 0 | 0 |

0 means the trees never split on the pair together. Falsification test
of ranked trial 7 ("the top interactions include none of the theory
pairs"):
- **Financial: not falsified.** Credit × tightening ranks second, but it
  is not stable across eras.
- **Political: falsified.** No theory pair reaches the top 10 at either
  depth. Food import share × grain prices (Hendrix & Haggard), anocracy ×
  grain prices, agriculture share × drought (von Uexkull et al.) and
  anocracy × neighbour conflict are all near zero.

Per LIT-NEXT-STEPS, the pairs are revised before T3 runs.

### The choice (at most 6, design years only)

The rule was written down with the tables above in view, so it is a
judgement, made explicit. A pair is chosen when all of these hold:
1. at depth 2, its full-fit rank is at most 10;
2. it is in the top 15 in at least 3 of the 4 era-left-out fits;
3. it has the full fit's sign in at least 3 of them;
4. |sign| ≥ 0.1, so the shape is roughly a product, which is what T3 tests;
5. at least 100 onsets have both inputs known;
6. at depth 3 it is still in the top 15.

The chosen pairs, ranked:

| # | Pair | Kinds | Sign | Why |
|---|---|---|---|---|
| 1 | youth bulge × world grain price gap | financial (all seven) | − | rank 1 at both depths, top in three eras, sign stable |
| 2 | youth bulge × neighbourhood conflict | financial (all seven) | + | rank 4 / 2, top 8 in three eras, sign stable |
| 3 | technology gap × world grain price gap | political (all five) | + | rank 3 / 5, top 13 in all four eras, + in all four |
| 4 | credit gap × house-price growth | banking crises | + | named in advance (Greenwood et al. 2022; Richter et al. 2021); T2 record c +1.14 (z 4.8, p 0.050); T7: + in all four eras, rank 23 |

Near misses, for the record:
- credit gap × tightening: top 10 in two eras only;
- agriculture share × export price fall: rank 22 at depth 3;
- technology gap × tightening: no sign (+0.01), so not a product;
- fuel price gap × grain gap: top 15 in two eras only.

The trees spread every pair almost evenly over the kinds of its group.
Kind dummies seldom enter a depth-2 tree with two other inputs, so the
per-kind SHAP does not say which kinds carry a pair. Each pair is tested
over its whole group.

How to read the three:
- Two involve a world series (the grain gap, about 41 yearly values whose
  peak is 1973-75), so they are differential exposures to world shocks:
  - *technology gap × grain gap:* poorer countries get more coups and
    conflicts when world grain prices spike, Hendrix & Haggard's and
    Bellemare's mechanism with income in place of the food import share;
  - *youth bulge × grain gap:* older, richer countries had more financial
    crises after the 1970s price spikes.
- *Youth bulge × neighbourhood conflict:* young countries in troubled
  subregions default and collapse more.

## T3: the named interactions

tools/bnm/interactions.clj (`bnm.interactions`); see its docstring.
- **Per-pair model.** For each pair, over its kinds:
  logit(y) = a_k + b_k(L - m_k) + α s + β g + γ s g.
  - a_k and b_k are the recalibration nuisance per kind, as in T2.
  - γ is tested by the year-clustered score test at the fit without it,
    one-sided in the pair's sign.
  - The null comes from 200 permutations of whole country series of the
    state, which is demeaned and scaled again on each draw.
  - The placebo is the state read at the next country with it that year.
  - Holm runs over the four pairs.
- **Principal contradiction.** In each group, a row's gauges are its
  kind's pairs, c_j = sign_j s_j g_j / sd_j. They are combined as A =
  Σ softmax(τ c)_j c_j: τ = 0 is the plain mean and τ = ∞ the argmax.
  - The model is logit(y) = recalibration + every input's level (0 and a
    flag where unknown) + θ A.
  - τ is profiled on {0, ¼, ½, 1, 2, 4, 8, ∞}.
  - Leave-one-era-out log scores compare the levels model, the pairs
    entered one by one, the mean, the argmax, and τ chosen in the
    training eras.

### Design-year results (origins 1960-2000, 200 permutations, seed 1)

```
jolt -A:tools -M -m bnm.interactions        # data/runs/sequencing/interactions-t3-design.tsv
```

| Pair | Kinds | Sign | Rows, onsets | γ (ML), se | Score z | Placebo z | Null mean z | p (perm.) | Holm |
|---|---|---|---|---|---|---|---|---|---|
| credit gap × house growth | banking | + | 809, 30 | +0.196, 0.098 | +1.84 | +0.49 | +0.01 | 0.060 | 0.219 |
| youth bulge × grain gap | financial (7) | − | 24,285, 2,456 | −0.064, 0.042 | −1.63 | +0.19 | −0.07 | 0.134 | 0.269 |
| youth bulge × neighbour conflict | financial (7) | + | 24,285, 2,456 | −0.023, 0.031 | −0.72 | −1.22 | −0.06 | 0.657 | 0.657 |
| technology gap × grain gap | political (5) | + | 17,460, 1,678 | +0.159, 0.053 | +2.16 | −0.44 | −0.05 | 0.055 | 0.219 |

- None passes Holm. Two come close on their own: technology gap × grain gap
  (z 2.2, p 0.055, placebo the other way) and credit × house prices (z 1.8,
  p 0.060 on 30 onsets: the continuous product needs both known, T2's zone
  had 36).
- The first T7 pair keeps its sign but is weak (p 0.13). The second
  takes the wrong sign in the logit: the trees' synergy there is not a
  product shape, so the T7 sign (+0.71 correlation) did not carry over.
- These are the same rows T7 chose on, so the design-year p values are
  optimistic for the three T7 pairs; only credit × house was named
  beforehand. The test years are the test.

**The principal contradiction** (financial group: three gauges for banking,
two for the other kinds; the political group has one gauge, so τ is not
identified there):

| | log lik |
|---|---|
| τ = 0 (mean) | −7156.01 |
| τ = ¼ | −7157.95 |
| τ = 1 | −7158.51 |
| τ = ∞ (argmax) | −7158.50 |

- In sample the mean wins: τ̂ = 0, LR against the argmax 4.99. Any τ > 0
  is about as bad as the argmax, because the products are heavy-tailed: a
  single large gauge dominates the softmax already at τ = ¼.
- Leave one era out (log score per row, 90% year-bootstrap):
  - argmax − mean +0.00051 [−0.00006, +0.00119]: level;
  - τ chosen in the training eras − mean −0.00023 [−0.00041, −0.00007]
    (chosen τ 0, 0, 4, 1 by era): the learned temperature does worse than
    either end;
  - mean − levels −0.00084 [−0.00165, −0.00015], pairs one by one − levels
    −0.00220 [−0.00431, −0.00056]: the gauges hurt out of era, however
    they are combined;
  - levels − recalibration +0.00367 [+0.00231, +0.00519].
- Political: pairs − levels +0.00034 [−0.00017, +0.00082], level.

Reading: on the design years the data do not prefer the argmax. In sample
they prefer the plain mean; out of era neither end beats the other, and
the interactions as a whole lose to the levels alone. The "principal
contradiction" (τ large) is not supported, and LIT-NEXT-STEPS' falsifier
for T3 ("the chosen temperature is high ... refutes the principal
selection", "intervals include zero for most pairs") is met on the
design years for the selection and nearly so for the pairs.

### For the test years (2003-2022)

```
jolt -A:tools -M -m bnm.interactions --from 2003 --to 2022 --draws 200 --seed 1 \
    --out data/runs/sequencing/interactions-t3-test.tsv
```

Origins 2003-2020 (windows to 2022). Scales, centres, the gauges' spreads
and every out-of-sample fit come from 1960-2002 (`--design-from`,
`--design-to` default to those); the pair tests and the τ profile are
recomputed on the test rows; the out-of-sample block fits on the design
rows and scores the test rows.

Suggested registration:
1. (primary) per pair, γ in its stated sign, one-sided 5% on the
   permutation p, Holm over the four: credit × house prices (banking, +),
   youth bulge × grain gap (financial, −), youth bulge × neighbour
   conflict (financial, +), technology gap × grain gap (political, +).
   Expectation from the design years: at most the two near-misses.
2. (principal contradiction, financial group) argmax − mean on log score
   per test row, fitted on 1960-2002, the lower end of the 90% interval
   above 0. Falsified (the mean is not beaten by the selected gauge) if
   not; the design years say it will be.
3. For the record: the τ profile on the test rows, the placebo z per pair,
   mean − levels and pairs − levels out of sample, the political group's
   pairs − levels.
