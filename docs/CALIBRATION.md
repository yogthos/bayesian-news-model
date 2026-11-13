# Calibration of the base (bnm-56c.15, recursive step 3 part a)

12 November 2026. Base of record t53-noclim. Rows are the issued forecasts (forecasts.tsv) whose outcome is known, by the year of the origin. "Const" is the Brier score of a constant at the period's observed rate (hindsight). "Causal" is the Brier score of the rate among the same node's rows resolved by the origin (24 months after their origin): the climatology a forecaster could have known. Scratch scripts: c15/cal.clj, c15/cap.clj and c15/w1n.clj in the session scratchpad.

## 1. The table (t53-noclim)

Country year, 2003–2022, all kinds:

| kind | n | mean p | rate | Brier | const | causal | within-origin AUC |
|---|---|---|---|---|---|---|---|
| banking crises | 2558 | 0.053 | 0.032 | 0.0317 | 0.0310 | 0.0314 | 0.63 |
| conflict onsets | 3308 | 0.082 | 0.082 | 0.0638 | 0.0752 | 0.0754 | 0.86 |
| coups | 3481 | 0.055 | 0.045 | 0.0427 | 0.0431 | 0.0437 | 0.77 |
| currency crises | 3262 | 0.038 | 0.036 | 0.0355 | 0.0346 | 0.0354 | 0.76 |
| fiscal consolidations | 2944 | 0.250 | 0.272 | 0.1971 | 0.1981 | 0.2028 | 0.55 |
| food price shocks | 2913 | 0.183 | 0.154 | 0.1560 | 0.1306 | 0.1395 | 0.54 |
| growth collapses | 3205 | 0.087 | 0.106 | 0.0990 | 0.0946 | 0.0973 | 0.53 |
| inflation crises | 3197 | 0.015 | 0.012 | 0.0120 | 0.0121 | 0.0123 | 0.80 |
| market crashes | 798 | 0.326 | 0.178 | 0.1996 | 0.1463 | 0.1605 | 0.38 |
| protest surges | 3411 | 0.040 | 0.030 | 0.0276 | 0.0296 | 0.0297 | 0.84 |
| sovereign defaults | 3217 | 0.035 | 0.031 | 0.0307 | 0.0304 | 0.0306 | 0.64 |
| violence surges | 3387 | 0.119 | 0.080 | 0.0709 | 0.0736 | 0.0792 | 0.75 |
| war onsets | 3402 | 0.034 | 0.025 | 0.0231 | 0.0244 | 0.0246 | 0.88 |

The worst kinds at every level:

| kind | node | period | n | mean p | rate | Brier | const | causal |
|---|---|---|---|---|---|---|---|---|
| market crashes | year country | 1980-2002 | 529 | 0.259 | 0.312 | 0.2395 | 0.2146 | 0.2209 |
| market crashes | year country | 2003-2022 | 798 | 0.326 | 0.178 | 0.1996 | 0.1463 | 0.1605 |
| market crashes | year subregion | 2003-2022 | 246 | 0.436 | 0.260 | 0.2272 | 0.1925 | 0.2098 |
| market crashes | year region | 2003-2022 | 96 | 0.534 | 0.406 | 0.2334 | 0.2412 | 0.2475 |
| market crashes | year world | 2003-2022 | 20 | 0.894 | 0.900 | 0.0926 | 0.0900 | 0.0916 |
| market crashes | quarter-lead country | 2003-2022 | 3077 | 0.118 | 0.072 | 0.0740 | 0.0672 | 0.0700 |
| food price shocks | year country | 2003-2022 | 2913 | 0.183 | 0.154 | 0.1560 | 0.1306 | 0.1395 |
| food price shocks | year subregion | 2003-2022 | 399 | 0.438 | 0.449 | 0.2413 | 0.2474 | 0.2598 |
| food price shocks | quarter-lead country | 2003-2022 | 11174 | 0.088 | 0.062 | 0.0803 | 0.0583 | 0.0600 |
| growth collapses | year country | 1980-2002 | 3569 | 0.103 | 0.063 | 0.0632 | 0.0593 | 0.0595 |
| growth collapses | year country | 2003-2022 | 3205 | 0.087 | 0.106 | 0.0990 | 0.0946 | 0.0973 |
| growth collapses | year subregion | 2003-2022 | 398 | 0.285 | 0.317 | 0.2216 | 0.2164 | 0.2295 |
| growth collapses | quarter-lead country | 2003-2022 | 12152 | 0.030 | 0.042 | 0.0423 | 0.0401 | 0.0405 |
| banking crises | year country | 1980-2002 | 2863 | 0.070 | 0.085 | 0.0820 | 0.0780 | 0.0797 |
| banking crises | year country | 2003-2022 | 2558 | 0.053 | 0.032 | 0.0317 | 0.0310 | 0.0314 |
| banking crises | year subregion | 2003-2022 | 380 | 0.143 | 0.108 | 0.0983 | 0.0963 | 0.0979 |
| banking crises | year region | 2003-2022 | 100 | 0.403 | 0.270 | 0.1833 | 0.1971 | 0.2021 |
| banking crises | quarter-lead country | 2003-2022 | 10128 | 0.022 | 0.012 | 0.0128 | 0.0120 | 0.0121 |
| sovereign defaults | year country | 2003-2022 | 3217 | 0.035 | 0.031 | 0.0307 | 0.0304 | 0.0306 |
| currency crises | year country | 2003-2022 | 3262 | 0.038 | 0.036 | 0.0355 | 0.0346 | 0.0354 |

Reliability by decile of p, country year 2003–2022 (mean p / rate):
- market crashes: 0.14/0.20, 0.18/0.19, 0.21/0.31, 0.26/0.23, 0.30/0.26, 0.34/0.20, 0.37/0.11, 0.42/0.09, 0.50/0.09, 0.55/0.10 (backwards);
- growth collapses: 0.04/0.16 ... 0.12/0.02, 0.17/0.05 (backwards);
- banking crises: 0.04/0.01, then 0.05/0.02-0.06 flat, 0.08/0.05 (no ranking);
- food price shocks: flat, the two top deciles are the 0.5 rows of 2003-06.

What the table says:
- Against the causal climatology the base loses only on market crashes (+0.039), food price shocks (+0.017; +0.005 on the rows after 2006), growth collapses (+0.0017) and banking crises (+0.0003); sovereign defaults and currency crises are level with it, every other kind beats it.
- The rest of "loses to a constant at the observed rate" is hindsight: the causal climatology loses to that constant too (banking 0.0314 against 0.0310). Banking, coups, wars, violence and inflation run above their 2003-22 rate because the climatology is the rate since 1870 and the year intercept barely moves (discount 0.998 a year): 2003-22 was calmer than the record before it (banking 1980-2002 rate 0.085).
- The loss is in the level over time, not in the ranking across countries. Each origin's rows set to that origin's mean p score as the base does (market crashes 0.1957, food 0.1458, growth 0.0985); each origin's rows shifted in log odds so their mean is the causal climatology score as the climatology or better (0.1641, 0.1389, 0.0974; currency crises 0.0342 against 0.0355). Growth collapses rank backwards pooled (AUC 0.34) but not within an origin (0.53): the high p came after the shocks of 2008 and 2020, the low p before them.

## 2. Where the excess comes from

Model by model, from captures of the single-kind runs that reproduce t53-noclim's forecasts.tsv and weights.tsv byte for byte (mc34-base for market crashes, c15-food-base, c15-bank-base). Country year, 2003–2022, mean p and Brier:

| | market crashes | food price shocks | banking crises |
|---|---|---|---|
| climatology offset alone | 0.281 / 0.1604 | 0.229 / 0.1524 | 0.051 / 0.0314 |
| base model (intercept + offset) | 0.310 / 0.1723 | 0.155 / 0.1549 | 0.053 / 0.0315 |
| best single model | fiscal 0.1669 | world 0.1567 .. up 0.1529 | booms 0.0309 |
| issued (the averaging) | 0.326 / 0.1996 | 0.183 / 0.1560 | 0.053 / 0.0317 |

### Market crashes: one episode counted 93 times

- The averaging issues the worst of 21 models (0.1996; every single model is between 0.1669 and 0.1977). The country node sits on the world model at 1.00 from 2009 to 2015 and on the all-inputs model at 1.00 from 2017 to 2022.
- The world model is the intercept and one gauge, world3 (the world's onsets over three years). world3 is the same at every country at an origin (sd 0.000 across places from 2002): a pure time series. Its coefficient went -0.21 (2008) to -0.44 (2009) to -0.58 (2010), when the December 2006 and 2007 rows resolved: 93 rows at rate 0.96 issued while world3 read -1.51 (calm). One calm-before-the-crash episode was learned as 93 observations. When world3 fell back to -0.5 to -1.3 (2013-15) the world model read 0.48, 0.53, 0.56 against rates 0.04, 0.09, 0.05.
- The same 93 rows gave the world model about 34 nats over the others (log(0.41/0.28) per row): the averaging's posterior took it to weight 1.00 and held it there for seven years. That is the weight lock of LOOP-AUDIT 6 and 7, and this is where it comes from.
- The intercept and the climatology both took the episode: the climatology rose from 0.251 (2008) to 0.315 (2010), and the base model's intercept from -0.02 to +0.34. The base model read 0.39 in 2010-11 (rates 0.23, 0.03).
- In isolation (market crashes alone, relations off), counting each origin once:
  - c15-mc-temper, averaging tempered to power 1/47 (`:weighting {:rule :temper :power 0.0213}`): country year Brier 0.1996 -> 0.1707 (2003-22), 0.2395 -> 0.2208 (1980-2002); weights no longer lock (at most 0.17).
  - c15-mc-w1n, each period's learning rows weighted 1/n (coefficients and evidence, a diagnostic only, it throws away the cross-section): 0.1645 and 0.2195, against the causal climatology's 0.1605 and 0.2209.
  So about three quarters of the loss to climatology is the weights and nearly all of it is the per-row counting of a shock common to the countries. What is left of the excess mean p (0.30 against 0.18) is the climatology, the rate since the 1960s, above the 2003-22 rate.

### Food price shocks: the uninformed start and the double count

- 612 of the 2913 rows (2003-06) are issued at 0.5: the node has nothing resolved until 2007 (bnm-56c.26). They cost 0.25 each against a rate of 0.10-0.42, and they are all of food's excess mean p. On the rows from 2007 the base runs below its outcomes (0.099 against 0.140; Brier 0.1311, climatology 0.1264).
- At 2007 the offset becomes the node's own rate (-1.78) and every model's intercept, learned at offset 0 from the first rows, is -1.76: p 0.028 against a rate of 0.31 (bnm-56c.27's double count). The base intercept is still -0.94 in 2022.
- The 2010-11 overshoot (all-inputs model at 0.97-1.00, p 0.23, 0.33 against 0.11, 0.05) and 2020-21 (rate 0.50, p 0.05) are the common shock again.

### Growth collapses and banking crises

- Growth collapses: the loss is the level over time. Low p before 2008 and 2020 (0.05-0.07 against rates 0.20-0.56), high after 2008 (0.13-0.15 against 0.04-0.05), while the node is locked on the all-inputs model (2007-10) and the exposure model (1.00 in 2011-14). Within an origin the ranking is near chance (0.53). Growth collapses alone with the averaging tempered to power 1/165 (c15-gc-temper): country year Brier 0.0990 -> 0.0962 (2003-22, now ahead of the causal climatology's 0.0973) and 0.0632 -> 0.0613 (1980-2002).
- Banking crises: every model is within 0.003 of the climatology (0.051); the intercept moves less than 0.1 in 25 years. The excess is the 1870-2002 rate against a calm 2003-22, with one global shock in 2006-07 (rates 0.17, 0.15). Nothing causal removes it: the causal climatology loses to the in-sample constant by the same amount.

### What did not move it

- `:clim-learn :current` alone (t53): market crashes 0.1894, food 0.1610 (2010-11 at 0.30 and 0.44), every other kind within 0.002.
- An online recalibration (tools/bnm/recalibrate_online.clj, T8b's settings, on t53-noclim, origins 2003-20): financial and social pooled Brier -0.00044 [-0.00071, -0.00013], all kinds +0.00035 [-0.00132, +0.00294]; banking crises +0.0005 [+0.0000, +0.0011] (worse), food +0.0153 [+0.0002, +0.0418] (worse), market crashes -0.022 [-0.061, +0.019], growth collapses -0.0027 [-0.0043, -0.0010]. A monotone map learned on resolved rows cannot follow an error that changes sign with each common shock.

## 3. Classification (PLAN principle 9)

| cause | kinds | size (country year Brier, 2003-22) | verdict |
|---|---|---|---|
| A period's rows counted as independent evidence in the averaging: one common shock at n places moves the weights n times (the weight lock of LOOP-AUDIT 6, 7) | market crashes, growth collapses, food | market crashes 0.1996 -> 0.1707 tempered to 1/n; growth 0.0990 -> 0.0962 | implementation bug (reclassed 12 November 2026, was sequencing: step 5): the averaging counted one origin's evidence once per place. Fix: `:weighting {:rule :temper :power :per-place}` (section 6); the frailty's per-origin shock (step 3b) replaces it later as the principled version |
| Time-common terms (intercept, world3, parent states) learned from n rows per origin: a slope from one episode | market crashes (world3 -0.21 -> -0.58 off 2006-07), growth collapses, food 2010-11 | market crashes 0.1707 -> 0.1645 with each period counted once | sequencing: step 3. This is what the frailty's per-origin common shock is for, so here the frailty is the fix, not something that would hide a calibration error |
| Year node issues 0.5 before its first resolved outcome | food 2003-06 (612 rows) | all of food's excess mean p | implementation bug bnm-56c.26, fixed behind `--clim-prior kinds` |
| Offset and intercept both take a change of climatology | food 2007 (p 0.03, rate 0.31), market crashes 2009-11 | | implementation bug bnm-56c.27, fixed behind `:clim-learn :current` |
| Climatology is the rate since the first outcome; 2003-22 was calmer | banking, coups, wars, violence, inflation, currency | causal climatology loses to the in-sample constant by as much as the base (banking 0.0314 vs 0.0310) | not a bug and not causal-fixable: hindsight. A faster level would chase the shocks (recalibration with discounts 0.9-1.0 loses on banking) |
| A monotone recalibration | all | pooled -0.0004 Brier, banking and food worse | not the fix: the error changes sign with each shock |

No new implementation bug was found at the time; the first row has since been reclassed as one (section 6). The two that bear on calibration have their fixes and tests already (lattice_test bnm-56c-27-a-change-of-climatology-decays, bnm-56c-26-a-node-with-nothing-resolved-reads-the-prior-rate; world_run_test bnm-56c-26-the-prior-rate-is-the-other-kinds'); they had not been scored together on the bnm-56c.33 base.

## 4. Candidate t55-cand: the two fixes together

t53-noclim's command plus `--clim-prior kinds` and `:clim-learn :current` (data/runs/t55-cand/README.txt; scores data/runs/logs/score-t55-cand-*.txt). Year Brier against t53-noclim (+ = better, 90% intervals):

| fold | all kinds | food | market crashes | growth | violence |
|---|---|---|---|---|---|
| 2003-06 | +0.0093 [+0.0060, +0.0128] | +0.087 | +0.036 | +0.003 | -0.002 |
| 2007-09 | +0.0055 [-0.0044, +0.0108] | -0.002 | +0.095 | -0.012 | -0.004 |
| 2010-12 | -0.0043 [-0.0059, -0.0026] LOSS | -0.048 | -0.000 | +0.004 | -0.004 |
| 2013-19 | -0.0024 [-0.0054, +0.0003] | -0.014 | -0.013 | +0.002 | -0.004 |
| 2020-22 | +0.0039 [+0.0002, +0.0075] | +0.062 | -0.010 | +0.000 | -0.000 |
| pooled | +0.0012 [-0.0013, +0.0038] | +0.013 | +0.009 | +0.000 | -0.003 |

Quarter-lead pooled +0.0006 [-0.0001, +0.0015].

Calibration after, country year 2003-22, mean p (rate) base -> candidate, Brier base -> candidate: food 0.183 -> 0.210 (0.154), 0.1560 -> 0.1431; market crashes 0.326 -> 0.345 (0.178), 0.1996 -> 0.1909; banking 0.053 -> 0.055 (0.032), 0.0317 -> 0.0320; growth 0.087 -> 0.081 (0.106), 0.0990 -> 0.0989; violence 0.119 -> 0.139 (0.080), 0.0709 -> 0.0739; currency 0.038 -> 0.065 (0.036); protest 0.040 -> 0.055 (0.030); inflation 0.015 -> 0.025 (0.012). Every other kind within 0.003 of mean p.

Where the 2010-12 loss comes from:
- Food, -0.048: 2010 and 2011 issue 0.29 and 0.44 against rates 0.11 and 0.05 (t53 with `:clim-learn` alone issues the same 0.30 and 0.44). The node moves to the all-inputs model (0.96 in 2011) after the 2007-08 rows resolve: the common shock of section 2. Without the double count the base no longer sits low enough to hide it.
- Violence -0.004, protest -0.003: the level is higher by 0.02-0.04 from the 1980s on (violence 2000-04 mean p 0.163 base, 0.181 with `:clim-learn`, 0.193 with both). All three runs sit on the all-inputs model at 1.00 from 1995 to 2015, so the gap is that model's intercept path set when the node started (1979-81 at the other kinds' rate instead of 0.5), carried forward at discount 0.998: LOOP-AUDIT 7's initial-condition sensitivity. Not decomposed term by term here. In declining-rate kinds the double count of bnm-56c.27 moved the level down with the climatology twice, which happened to track the decline; removing it raises those kinds' mean p.
- Market crashes swing +0.095 (2007-09) and -0.013, -0.010 later: the same weight-lock path variance LOOP-AUDIT 7 found, not evidence either way.

Verdict: the fixes do what they say (food 2003-06 +0.087, food 2003-22 Brier 0.1560 -> 0.1431), the candidate fails the fold rule in 2010-12, and the loss traces to the common-shock counting (sequencing) and the locked all-inputs intercept (sequencing), not to the fixes. Not adopted; rescore after step 3/5.

## 5. What this means for step 3

The frailty is the right place for most of the miscalibration rather than a risk of absorbing it: the excess on the worst kinds is a shared shock counted once per country. Two things should land with or before it:
- the averaging's evidence counted once per origin (per-node tempering 1/places, or the frailty's marginal likelihood), otherwise the frailty's own gains will be locked in or out by single episodes;
- the frailty's variance estimated with the climatology and intercept not double counting (`:clim-learn :current`), since its common shock and a drifting intercept are otherwise the same quantity.
The level left after that (a since-1870 climatology above the calm 2003-22) is not a calibration error a causal model can remove.

## 6. The averaging tempered per origin: t56-temper-only and t56-cand

The first row of section 3 is an implementation bug: the averaging counted one origin's evidence once per place. Its fix (a50a0b1) is `:weighting {:rule :temper :power :per-place}` (or under `:weighting-at`): each period the node's averaging is tempered to the number of origins among the rows it learns over their number, so one origin's rows at n places weigh as one row (power 1/n when one origin resolves, 2/n when two do; 1 at one place). Off by default; the ledger's issue is unchanged. Test: lattice_test averaging-tempered-per-place (the power is 1/n and 2/n exactly, one place is the posterior, at 40 places with a common input the top weight goes from 0.997 untempered to 0.50 against 0.52 at one place, bad powers refused). The frailty's per-origin shock (step 3b) replaces it later as the principled version.

Runs (data/runs/t56-*/README.txt): t56-temper-only is the ledger's t53-noclim command plus the temper; t56-cand adds `:clim-learn :current`. Scores: data/runs/logs/score-t56-*.txt.

Year Brier against t53-noclim (+ = better, 90% intervals):

| fold | t56-temper-only | t56-cand |
|---|---|---|
| 2003-06 | +0.00236 [+0.00105, +0.00373] | +0.00223 [+0.00091, +0.00356] |
| 2007-09 | -0.00052 [-0.00353, +0.00169] | +0.00006 [-0.00556, +0.00353] |
| 2010-12 | -0.00005 [-0.00186, +0.00217] | -0.00467 [-0.00657, -0.00216] LOSS |
| 2013-19 | +0.00428 [+0.00107, +0.00760] | +0.00319 [+0.00015, +0.00638] |
| 2020-22 | +0.00050 [-0.00184, +0.00344] | +0.00238 [-0.00089, +0.00556] |
| pooled 2003-22 | +0.00231 [+0.00069, +0.00404] GAIN | +0.00140 [-0.00053, +0.00325] |
| pooled log | +0.00380 [-0.00070, +0.00791] | +0.00088 [-0.00554, +0.00680] |

Pooled by kind (Brier; log where its interval excludes 0): market crashes +0.0261 [+0.0086, +0.0486] and +0.0248 [+0.0079, +0.0472]; growth collapses +0.0027 [+0.0016, +0.0040] and +0.0030 [+0.0018, +0.0043]; food +0.0040 [-0.0008, +0.0114] and -0.0032 [-0.0131, +0.0069]; conflict onsets -0.0015 [-0.0037, +0.0005] (log -0.0089 [-0.0154, -0.0018]) and -0.0026 [-0.0054, -0.0000]; inflation -0.0002 [-0.0003, +0.0000] (log -0.0060) and -0.0003 [-0.0005, -0.0001]; war onsets log -0.0051 and -0.0066; every other kind within its interval of 0.

Calibration, country year 2003-22, mean p (rate), Brier, t53-noclim -> temper-only -> cand:

| kind | rate | mean p | Brier |
|---|---|---|---|
| market crashes | 0.178 | 0.326 -> 0.313 -> 0.322 | 0.1996 -> 0.1736 -> 0.1748 |
| food price shocks | 0.154 | 0.183 -> 0.175 -> 0.252 | 0.1560 -> 0.1521 -> 0.1592 |
| growth collapses | 0.106 | 0.087 -> 0.075 -> 0.076 | 0.0990 -> 0.0963 -> 0.0960 |
| banking crises | 0.032 | 0.053 -> 0.058 -> 0.056 | 0.0317 -> 0.0316 -> 0.0316 |
| violence surges | 0.080 | 0.119 -> 0.104 -> 0.121 | 0.0709 -> 0.0713 -> 0.0724 |
| currency crises | 0.036 | 0.038 -> 0.062 -> 0.074 | 0.0355 -> 0.0353 -> 0.0360 |

1980-2002: market crashes 0.2395 -> 0.2203 -> 0.2199, growth 0.0632 -> 0.0610 -> 0.0608, banking 0.0820 -> 0.0800 -> 0.0799. The temper alone reproduces the single-kind probes of section 2 (market crashes 0.1707 at a fixed 1/47, growth 0.0962 at 1/165).

Where the losses come from (score-t56-by-year.txt, score-t56-within-auc.txt, the weights.tsv files):
- Market crashes 2010-12 (-0.028 in both runs, the kind's only losing fold): t53-noclim's lock on the world model (1.00 from 2009 to 2015) issued 0.14-0.19 in 2009-11 against rates 0.14, 0.23, 0.03, then 0.48-0.56 in 2013-15 against 0.04-0.09. Tempered, no weight passes 0.15 and the node issues 0.33-0.35 throughout. The lock's luck in 2009-11 is the price of removing its cost in 2013-15 (+0.055 in 2013-19); what is left of the 0.33 level is the world model's coefficients learned from the one 2006-07 episode at every place (section 3's second row, step 3), which the temper does not touch.
- Conflict onsets, war, inflation, coups (log score; Brier within its interval for temper-only): with the evidence counted once per origin and forgetting at 0.995 a period, the weights barely leave their prior in a century: at conflict onsets the all-inputs model is at 0.23-0.32 and 18 others share the rest, where t53-noclim sat on it or on history at 1.00. Ranking within an origin is not what is lost (conflict AUC 0.857 -> 0.872, war 0.879 -> 0.883, inflation 0.802 -> 0.806): the blend of nearly equal weights is less sharp and its level sits above the rate (inflation mean p 0.015 -> 0.023 against 0.012, coups 0.055 -> 0.073 against 0.045). That is the power, not the code: 1/places is right where what separates the models is the same at every place (world3 at market crashes, growth), and throws away real evidence where it differs by place (a country's own history and politics). The count that fits both is the effective number of independent rows per origin, n / (1 + (n - 1) rho), rho the correlation across places of the models' log-score differences at an origin; or the frailty, which takes the common part out of the rows before the averaging sees them.
- t56-cand's 2010-12 LOSS adds food -0.035 to the market crashes: under `:clim-learn :current` food issues 0.33 and 0.35 in 2010-11 (rates 0.11, 0.05) and 0.20-0.27 in 2012-13 (0.01, 0.05). Without the double count its level is the climatology since 2003, high after 2006-07, plus models blended near equally that learned 2007-08 as a common shock at every place (section 4's food diagnosis, step 3); the double count had pulled the level down and hid it. The level rises in the declining kinds too (currency 0.062 -> 0.074, inflation, coups), as in t55-cand. The 2003-06 food rows still issue 0.5 here (no `--clim-prior kinds`).

No bug was found in the option or its interaction with `:clim-learn`. Verdict: t56-temper-only passes the fold rule (no fold's interval below 0) with a pooled gain, and loses log score on four well-ranked kinds through the bluntness of 1/places; t56-cand fails the fold rule in 2010-12. Neither is adopted here; adoption is the user's decision. Next: the effective-sample power, or the frailty with the temper on, scored the same way.
## 7. The frailty: t57-frailty (bnm-56c.20, bnm-3be)

t53-noclim's ledger command plus `:clim-learn :current` and `:frailty {}` (the default grid: ρ in {0, 0.5, 0.8, 0.95}, σ in {0, 0.25, 0.5, 1}, a 2-nat price on σ > 0), at 4aca271 with augur v0.22.0 (data/runs/t57-frailty/README.txt; design in docs/RECURSIVE.md step 3). A first run, t57-frailty-v1, issued rare kinds too high. Integrating over a shock of sd 1 around 0 raised the expected rate by about e^{1/2} before anything was known: food 2013-18 issued 0.16-0.24 against rates of 0.05-0.09. That was an implementation bug, fixed in 4aca271 with a test: the shift applied is the state less half its stationary variance, as in augur.hier/states. Scores below are for the fixed run. Logs: data/runs/logs/score-t57-frailty-*.txt (and -v1-).

Year nodes, Brier against t53-noclim (+ = better, 90% intervals):

| fold | t57-frailty vs t53-noclim | vs t56-cand |
|---|---|---|
| 2003-06 | +0.0025 [-0.0001, +0.0049] | +0.0002 [-0.0026, +0.0028] |
| 2007-09 | -0.0032 [-0.0052, -0.0005] LOSS | -0.0033 [-0.0051, +0.0015] |
| 2010-12 | -0.0021 [-0.0044, +0.0003] | +0.0025 [-0.0002, +0.0054] |
| 2013-19 | +0.0044 [+0.0019, +0.0071] | +0.0012 [+0.0004, +0.0021] |
| 2020-22 | +0.0040 [+0.0007, +0.0074] | +0.0016 [+0.0003, +0.0028] |
| pooled | +0.0022 [+0.0006, +0.0040] GAIN | +0.0008 [-0.0003, +0.0018] |

- Log score pooled: +0.0072 [+0.0019, +0.0133] against t53-noclim and +0.0063 [+0.0029, +0.0097] against t56-cand.
- Quarter-lead pooled: -0.00003 [-0.0009, +0.0007] against t53-noclim and -0.0007 [-0.0014, -0.0001] against t56-cand. The quarter loss is banking -0.0032 and coups -0.0020 (not traced here).
- Per kind against t53-noclim, gains: market crashes +0.030 [+0.011, +0.052], growth collapses +0.0024 [+0.0004, +0.0043], conflict onsets +0.0010 and currency crises +0.0010 (log gains). Food is -0.0019 [-0.0098, +0.0069].
- Per kind against t53-noclim, losses: sovereign defaults -0.0017 [-0.0028, -0.0009] and inflation crises -0.0002 [-0.0005, -0.0000]. Banking -0.0006 is not significant.
- The 2007-09 loss: market crashes -0.038, fiscal -0.005, sovereign defaults -0.004, banking -0.003. In 2010-12, food is -0.031.

Calibration, country year 2003-22, mean p (rate) and Brier, t53-noclim → t56-cand → t57-frailty:

| kind | t53-noclim | t56-cand | t57-frailty |
|---|---|---|---|
| market crashes | 0.326 (0.178), 0.1996 | 0.322, 0.1748 | 0.286, 0.1701 |
| food price shocks | 0.183 (0.154), 0.1560 | 0.252, 0.1592 | 0.247, 0.1580 |
| growth collapses | 0.087 (0.106), 0.0990 | 0.076, 0.0960 | 0.083, 0.0966 |
| banking crises | 0.053 (0.032), 0.0317 | 0.056, 0.0316 | 0.045, 0.0322 |
| sovereign defaults | 0.035 (0.031), 0.0307 | 0.046, 0.0308 | 0.049, 0.0324 |

- Market crashes 1980-2002: 0.2395 → 0.2305.
- Food 2020-22 (rate 0.415): 0.3716 → 0.3359 (t56) → 0.3369, mean p 0.060 → 0.114. The frailty filtered z = +2.0 for 2020-21, but only once those rows resolved, in 2022-23: from year rows it cannot reach the year itself (RECURSIVE step 3's planted 2020 test needs the fresher quarter scale, step 4).

What it does:
- **Weights.** The weights no longer lock on market crashes: the world model's weight is at most 0.85 (2014), where t53-noclim held 1.00 from 2009 to 2015.
- **The common shock.** The filtered common shock is the episode: market crashes z = +3.3 for the 2006-07 windows, growth collapses +1.5 (2007-08) and +2.9 (2018-19), food +2.0 (2020-21).
- **Grid choices.** The grid chose σ = 1 at most year country nodes. It chose ρ = 0 at market crashes and food, so their forecasts carry no shock forward; ρ = 0.8 at banking crises and sovereign defaults; ρ = 0.5 at protest (σ 0.5).

Losses traced:
- **Market crashes, 2007-09 (-0.038).** t53-noclim issued 0.29 in 2007 (a year at rate 0.96) because its averaging sat on the all-inputs model at 1.00 in 2007-08: the lock of LOOP-AUDIT 6 and 7, here on the right side. t57-frailty blends (regime and violence groups, at most 0.65) and issues 0.25. This is the lock's path variance, not lost skill: across 2003-22 market crashes gain +0.030. Not a bug.
- **Banking crises, 2007-12 (2007-09 -0.003, 2010-12 -0.002).** The frailty's persistence (ρ = 0.8, chosen by the prequential grid on banking's 1870-2002 waves) carried the calm of 2003-04 (z = -1.6) into the 2006-07 issues (0.025 against rates of 0.17 and 0.15). It then carried the 2006-07 crisis (z = +1.6) into 2009-10 (0.10 against 0.02 and 0.016). The filter, the lag and the choice all behave as specified (the prior at origin t reads only the origins resolved 24 months before). The persistence learned before 2003 did not hold through 2007-10. Not a bug: this is what a carried state costs when the waves change length.
- **Sovereign defaults (-0.0017 pooled) and inflation crises (-0.0002).** These kinds run above their rate: sovereign defaults issue 0.06-0.08 in 2003-09 against 0.01-0.02. The filtered z stays negative for twelve years straight (-0.4 to -1.3, 2003-15); banking's does the same in 2016-22 and market crashes' in 2011-17. So the 2003-22 calm is a persistent level, and neither state now carries it:
  - z is AR(1) around 0, and at a two-period lag ρ = 0.5-0.8 carries a quarter to two thirds of it;
  - the intercept, with the shock counted once per origin, now learns the level from about one observation per origin, with the year discount (0.998) set when it learned from one observation per country.
  t56-cand counts the coefficients' evidence per country, and it has none of this excess on sovereign defaults (0.0308). This is a structural gap of the scalar transient frailty, not a code bug. The fix is a persistent level component: a second state near a random walk with a small σ, or the year intercept's discount set for per-origin counting. Both are open (bnm-56c.20 notes).
- **Food 2010-12 (-0.031).** The all-inputs model still locks: 0.96 in 2011, then the patronage model 0.90-0.98 in 2012-13. The 2007-08 shock hit places in proportion to their exposure to world prices. A scalar z takes the shock's level once, but its exposure-weighted pattern is a place-varying input, so it is still counted once per place, in both the coefficients and the evidence. The fix would be a loading of the frailty on exposure (a shared random slope). Traced, not fixed: the same mechanism as above, one level further in.

Verdict: the frailty does what bnm-3be asked, with three caveats:
- It fixes the pooled score (+0.0022, a significant gain against t53-noclim, +0.0008 against t56-cand) and market crash calibration (0.1701, below the temper's 0.1707 in isolation).
- It fails the fold rule against t53-noclim in 2007-09, on market crashes' weight-lock path variance and banking crises' carried persistence.
- It leaves a persistent-level gap on sovereign defaults, and an exposure-weighted common shock on food.
Not adopted (adoption is the user's decision). The two structural follow-ups are a persistent level component and the exposure loading.

## 8. The persistent level: t58-level (bnm-56c.20.1, bnm-3be)

t57-frailty's command plus `:frailty {:level-sds [0.0 0.1 0.2]}`: a random-walk level per node beside the AR(1) transient, filtered jointly once per origin, σ_L chosen by the same prequential grid at a price of 2 nats (design: docs/RECURSIVE.md step 3c, which also derives why option (b), the intercept's discount reset for per-origin counting, is a no-op: the discount's memory does not depend on the count). At 8143b5d with augur v0.22.0 (data/runs/t58-level/README.txt). With the option off, 2026-Q4-r1 reproduces byte for byte (data/runs/logs/repro-r1-level.log). Logs: data/runs/logs/score-t58-level-*.txt; the traces below are in score-t58-level-trace.txt.

Year nodes, Brier (+ = better, 90% intervals):

| fold | t58-level vs t53-noclim | vs t57-frailty |
|---|---|---|
| 2003-06 | +0.0022 [+0.0000, +0.0044] | -0.0002 [-0.0010, +0.0005] |
| 2007-09 | -0.0029 [-0.0050, -0.0003] LOSS | +0.0003 [-0.0001, +0.0006] |
| 2010-12 | -0.0021 [-0.0045, +0.0003] | +0.0000 [-0.0003, +0.0004] |
| 2013-19 | +0.0044 [+0.0017, +0.0071] | -0.0000 [-0.0002, +0.0003] |
| 2020-22 | +0.0040 [-0.0000, +0.0083] | +0.0001 [-0.0009, +0.0009] |
| pooled | +0.0022 [+0.0006, +0.0042] GAIN | +0.0000 [-0.0002, +0.0002] |

- Log score pooled: +0.0066 [+0.0012, +0.0129] against t53-noclim, -0.0006 [-0.0015, +0.0004] against t57-frailty.
- Quarter-lead pooled: -0.00005 [-0.0009, +0.0008] against t53-noclim, -0.00002 [-0.0004, +0.0005] against t57-frailty.
- Per kind against t57-frailty: sovereign defaults -0.0005 [-0.0010, -0.0002] (log -0.0029 [-0.0057, -0.0007]); banking crises -0.0008 [-0.0017, +0.0002] (log -0.0079 [-0.0153, -0.0001]); violence surges +0.0004 [+0.0000, +0.0008]; every other kind within its interval of 0. Against t53-noclim: sovereign defaults -0.0022 [-0.0035, -0.0012] and banking -0.0014 [-0.0025, -0.0003], both worse than t57-frailty's -0.0017 and -0.0006.

Calibration, country year, mean p (rate) and Brier, t53-noclim → t57-frailty → t58-level:

| kind | 2003-22 rate | mean p | Brier |
|---|---|---|---|
| market crashes | 0.178 | 0.326 → 0.286 → 0.286 | 0.1996 → 0.1701 → 0.1702 |
| food price shocks | 0.154 | 0.183 → 0.247 → 0.247 | 0.1560 → 0.1580 → 0.1580 |
| growth collapses | 0.106 | 0.087 → 0.083 → 0.083 | 0.0990 → 0.0966 → 0.0966 |
| banking crises | 0.032 | 0.053 → 0.045 → 0.058 | 0.0317 → 0.0322 → 0.0330 |
| sovereign defaults | 0.031 | 0.035 → 0.049 → 0.050 | 0.0307 → 0.0324 → 0.0329 |
| inflation crises | 0.012 | 0.015 → 0.021 → 0.019 | 0.0120 → 0.0122 → 0.0122 |

1980-2002: banking 0.0820 → 0.0800 → 0.0834 (mean p 0.070 → 0.075 → 0.112, rate 0.085), sovereign 0.0680 → 0.0646 → 0.0646, inflation 0.0384 → 0.0373 → 0.0378; the others unchanged.

Where the level was chosen: almost everywhere at the quarter scales (the quarter country nodes at nearly every origin since 2003 for most kinds). At the year country nodes, where the table is scored, it was chosen for banking crises (68 of 150 origins, 17 since 2003), coups (70, none since 2003) and a few others; for sovereign defaults never. The planted test's mechanism works (a level carried, σ_L = 0 when none), but the real calm is not that world.

Losses traced:
- **Banking crises.** From about 1960 the year country node chose σ_L = 0.2 with the transient at ρ = 0 (all persistence moved to the level). The level followed the Bretton Woods calm down to -2.5 (1968) and the 1980s-90s wave up to +1.4 (1993-96). At a three-period information lag (24-month windows, known 12 months after) and a gain of a fraction of each origin's departure, it was at its highest when the wave ended: the issues of 1997-2004 ran from 0.18 down to 0.08 (t57-frailty: 0.10 down to 0.04) against rates of 0.06 down to 0. That is the 1980-2002 Brier 0.0800 → 0.0834 and 2003-06 -0.0035 against t57-frailty. In 2011-18, with ρ = 0, the transient no longer carries the calm (t57-frailty's ρ = 0.8 did), and the level, at -0.2 to -0.45, carries less of it: 0.08 down to 0.05 issued against t57-frailty's 0.06 down to 0.03. The filter, the lag and the choice behave as specified. This is the risk RECURSIVE step 3c named: a random walk carries a wave into the calm after it for longer than ρ = 0.8 did. Not a bug.
- **Sovereign defaults.** The year country node's filter is identical to t57-frailty's at every origin (σ_L = 0 throughout: the level never earned its 2 nats in the cumulative score from 1870). The change comes from the region level, which chose σ_L = 0.1 and carried the 1980s debt-crisis wave at +1.2 to +1.4 from 1995 to 2003, still +0.5 in 2008. The countries' forecasts read the region's through the downscaled offset (`:learn-offsets :mixture`), and issue 0.002-0.006 higher in 2000-12. Same mechanism as banking, one level up. Not a bug.
- **Why the level does not take the 2003-15 calm.** The calm followed the 1980-2002 wave. A random walk has to unwind the wave's level before it can go negative, and with the lag and its gain it reaches the calm's level only after the calm has mostly passed. Its cumulative prequential score since 1870 pays for every such lag, so at the year country node σ_L = 0 wins. The persistent component that would help moves faster than the lag (the fresher quarter scale, step 4) or is a regime state that switches (step 5), not a slow level.

Verdict: the level does nothing for what it was built for. The year nodes score the same as t57-frailty pooled (+0.00001 [-0.0002, +0.0002]) and fail the fold rule against t53-noclim in 2007-09 as t57-frailty does. Sovereign defaults (-0.0005) and banking crises (log -0.008) lose against t57-frailty through a lagged level carrying the 1980s-90s waves into the calm. No bug was found. Option (b) was not run, since as derived it changes nothing (0.998 is already the per-origin equivalent) unless it forgets every coefficient. Not adopted (adoption is the user's decision). The option stays in the code, off by default; `:level-sds` with a price is the harness if a faster level source (step 4's quarters) is fed to it.

## 9. The loading on exposure: t59-exposure (bnm-56c.20.2, bnm-3be)

Design: docs/RECURSIVE.md step 3d. The option is `:frailty {:exposure-sds [0.0 0.25 0.5]}`: a loading u_j per exposure column (at the political year nodes, trade openness and capital openness), each column standardized across the node's places at the origin. The place's shock is then z + u.e, filtered jointly once per origin and learned with the rows as augur's vector `:shared`; σ_u is chosen by the same prequential grid at 2 nats.

Building it surfaced a second count per place. At an input's first reading, augur's evidence integrated a model's coefficient uncertainty separately in every row, so a model unsure of a place-varying input was paid or rewarded for it once per place. In the planted world that was 3 to 26 nats in one origin. The fix is `:evidence-at :sequential`: each row is scored given the period's rows before it. Both options are off by default, and 2026-Q4-r1 reproduces byte for byte with them off (data/runs/logs/repro-r1-exposure.log).

Runs, at 9b81890 with augur v0.23.0 (data/runs/t59-*/README.txt):
- t59-exposure: t57-frailty's command plus both options (the candidate);
- t59-slope: the slope alone;
- t59-seq: the sequential evidence alone.

Logs: data/runs/logs/score-t59-*.txt; calibration in score-t59-calibration.txt.

Year nodes, Brier (+ = better, 90% intervals):

| fold | t59-exposure vs t53-noclim | vs t57-frailty | t59-slope vs t57-frailty | t59-seq vs t57-frailty |
|---|---|---|---|---|
| 2003-06 | -0.0027 [-0.0110, +0.0040] | -0.0051 [-0.0112, -0.0004] LOSS | -0.0007 [-0.0026, +0.0010] | -0.0054 [-0.0101, -0.0015] LOSS |
| 2007-09 | -0.0138 [-0.0185, -0.0011] LOSS | -0.0106 [-0.0149, +0.0011] | -0.0034 [-0.0049, +0.0005] | -0.0084 [-0.0116, +0.0005] |
| 2010-12 | -0.0010 [-0.0028, +0.0008] | +0.0011 [-0.0021, +0.0045] | +0.0006 [+0.0002, +0.0009] | -0.0000 [-0.0030, +0.0032] |
| 2013-19 | +0.0052 [+0.0024, +0.0080] | +0.0009 [-0.0007, +0.0021] | -0.0004 [-0.0008, -0.0001] LOSS | +0.0013 [-0.0001, +0.0025] |
| 2020-22 | +0.0058 [+0.0031, +0.0083] | +0.0018 [-0.0004, +0.0037] | -0.0007 [-0.0017, +0.0003] | +0.0016 [+0.0001, +0.0031] |
| pooled | +0.0010 [-0.0021, +0.0039] | -0.0012 [-0.0033, +0.0007] | -0.0006 [-0.0013, +0.0001] | -0.0011 [-0.0030, +0.0006] |

- Log pooled. t59-exposure: +0.0035 [-0.0058, +0.0125] against t53-noclim and -0.0037 [-0.0105, +0.0021] against t57-frailty. t59-slope: +0.0055 [+0.0002, +0.0111] against t53-noclim and -0.0017 [-0.0039, +0.0006] against t57-frailty. t59-seq: -0.0035 [-0.0093, +0.0016] against t57-frailty.
- Against t53-noclim, t59-slope pools +0.0016 [-0.0002, +0.0038] (t57-frailty: +0.0022) and loses 2007-09.
- Quarter-lead pooled: every run within ±0.0004 of both benches, no interval excluding 0.
- Per kind, t59-exposure against t57-frailty:
  - gains: sovereign defaults +0.0006 [+0.0002, +0.0011], banking +0.0011 [+0.0000, +0.0023], inflation log +0.0037;
  - losses: market crashes -0.016 [-0.047, +0.010] (2003-06 -0.064, 2007-09 -0.140), violence surges -0.0012 [-0.0022, -0.0002], protest -0.0014 [-0.0027, -0.0002];
  - food +0.0021 [-0.0064, +0.0123].
- Per kind, t59-slope against t57-frailty:
  - gains: growth +0.0010 [+0.0001, +0.0020], sovereign +0.0005 [+0.0001, +0.0009], inflation log +0.0035;
  - losses: market crashes -0.0066 [-0.0160, +0.0002] (2007-09 -0.046), violence log -0.0043;
  - food -0.0009 [-0.0028, +0.0007].

Calibration, country year, mean p (rate) and Brier, in the order t53-noclim → t57-frailty → t59-slope → t59-seq → t59-exposure:

| kind | span | rate | mean p | Brier |
|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.326 → 0.286 → 0.276 → 0.247 → 0.241 | 0.1996 → 0.1701 → 0.1768 → 0.1827 → 0.1864 |
| market crashes | 1980-2002 | 0.312 | 0.259 → 0.234 → 0.233 → 0.237 → 0.236 | 0.2395 → 0.2305 → 0.2309 → 0.2238 → 0.2244 |
| food price shocks | 2003-22 | 0.154 | 0.183 → 0.247 → 0.239 → 0.247 → 0.245 | 0.1560 → 0.1580 → 0.1589 → 0.1572 → 0.1559 |
| food | 2010-12 | 0.060 | 0.206 → 0.296 → 0.291 → 0.241 → 0.236 | 0.0844 → 0.1158 → 0.1141 → 0.1005 → 0.0979 |
| food | 2020-22 | 0.415 | 0.060 → 0.114 → 0.095 → 0.144 → 0.151 | 0.3716 → 0.3369 → 0.3490 → 0.3190 → 0.3185 |
| growth collapses | 2003-22 | 0.106 | 0.087 → 0.083 → 0.080 → 0.085 → 0.086 | 0.0990 → 0.0966 → 0.0956 → 0.0966 → 0.0963 |
| growth collapses | 1980-2002 | 0.063 | 0.103 → 0.090 → 0.083 → 0.098 → 0.084 | 0.0632 → 0.0611 → 0.0604 → 0.0644 → 0.0606 |
| banking crises | 2003-22 | 0.032 | 0.053 → 0.045 → 0.058 → 0.045 → 0.050 | 0.0317 → 0.0322 → 0.0318 → 0.0322 → 0.0312 |
| banking crises | 1980-2002 | 0.085 | 0.070 → 0.075 → 0.058 → 0.075 → 0.060 | 0.0820 → 0.0800 → 0.0812 → 0.0801 → 0.0812 |
| sovereign defaults | 2003-22 | 0.031 | 0.035 → 0.049 → 0.046 → 0.051 → 0.048 | 0.0307 → 0.0324 → 0.0319 → 0.0322 → 0.0318 |
| inflation crises | 2003-22 | 0.012 | 0.015 → 0.021 → 0.017 → 0.023 → 0.013 | 0.0120 → 0.0122 → 0.0121 → 0.0124 → 0.0120 |

Food's averaging weights at the year country node, 2009-13 (top weights over 0.1):

| run | 2009 | 2010 | 2011 | 2012 | 2013 |
|---|---|---|---|---|---|
| t53-noclim | military 0.37 | all 0.97 | all 1.00 | world 0.97 | history 0.75 |
| t57-frailty | military 0.32 | economy 0.49, all 0.40 | all 0.96 | patronage 0.90 | patronage 0.98 |
| t59-slope | military 0.32 | economy 0.49, all 0.40 | all 0.96 | patronage 0.90 | patronage 0.98 |
| t59-seq | economy 0.31 | economy 1.00 | economy 0.94 | all 1.00 | all 1.00 |
| t59-exposure | economy 0.31 | economy 1.00 | economy 0.94 | all 1.00 | all 1.00 |

Where the slope was chosen (σ_u > 0 at the year country node, origins since 2003 out of 20):
- banking 20, conflict 19, coups 20, currency 19, growth 19, inflation 19, market crashes 20, sovereign 19, violence 20;
- food 13 of its 15 origins (its year history starts in 2007);
- fiscal 2;
- most quarter country nodes, never the world node.

The loadings are modest: |u| up to 0.9 log odds per sd of trade openness at food in 2016-19.

What it does and does not do:
- **The slope does not take food's 2007-08 shock.** Food's year country node has 15 filtered origins, the first of them the 2007-08 shock itself, filtered at σ_u = 0: the slope had earned nothing yet. Its 2008 loadings are 0.04 and 0.07. So the rows that taught the 2010-11 lock carried the scalar shock only, and t59-slope's food weights in 2009-13 are t57-frailty's to two decimals (all 0.96 in 2011). Food 2010-12 moves from 0.1158 to 0.1141. A loading that must earn its 2-nat price before the episode cannot remove that episode's count per place. That needs a longer food history (the 1870- annual catalogue at the year scale) or the price paid from a sibling kind's evidence. Not a bug.
- **The sequential evidence moves food 2010-12 (0.1158 → 0.1005) but does not stop the lock.** It moves it from all-inputs (0.96 in 2011) to the economy model (1.00 in 2010), then all-inputs again at 1.00 in 2012-13. Food 2020-22 improves (0.3369 → 0.3190; mean p 0.114 → 0.144).

Losses traced:
- **Market crashes under t59-seq and t59-exposure: 2003-06 -0.068 and -0.064, 2007-09 -0.105 and -0.140.** The year country node's weights lock on the `:down` model: 0.90-0.99 from 2003 to 2007, where t57-frailty spread them over regime and violence. It then issues 0.15 and 0.13 in 2006-07 (rate 0.96), against t57-frailty's 0.27 and 0.25. The only change is the evidence rule.
  - `:down` reads the region's and subregion's states, which are the same within a region at an origin and differ between regions.
  - Scored independently, a model's fixed coefficients cannot follow a shock that is regional rather than global.
  - Scored sequentially within the period, its uncertain coefficient on region-common inputs learns the period's regional pattern from the first rows of each region and predicts the rest. That is a regional random effect the frailty (one z per node) does not model, so it is rewarded once per country in the region: the same count per place, at the region level.
  - This is the mechanism; it was read from the weights and the inputs, not tested in a planted world.
  - The sequential rule is the correct marginal when the rows are exchangeable given the shared state. Here they are not, because a regional shock is left out. Not a code bug: the evidence is computed as specified. The fix that keeps the rule is a shared state per region inside the country node (z + z_region + u.e), or, in the slope's terms, a loading on region membership.
- **Market crashes under t59-slope: 2007-09 -0.046.** The slope candidates changed the grid's choice at the year country node. In 2001-05 it kept ρ = 0.5 with σ_u = 0.5 (filtered z -1.2 to -1.7, capital-openness loading -0.49), where t57-frailty moved to ρ = 0 from 2003. So the 2003-05 calm, tilted against open economies, was carried into the 2006-07 issues: 0.23 and 0.21 against t57-frailty's 0.27 and 0.25. This is the same mechanism as banking's carried persistence in section 7. Not a bug.
- **Food 2020-22 under t59-slope (-0.012).** In 2016-19 the node chose ρ = 0.8 and loadings of -0.7 to -0.9 on trade openness (open economies calmer). The calm and its tilt were carried, so 2019-21 issued 0.090-0.097 against t57-frailty's 0.109-0.112 at rates of 0.09-0.50. The 2020-21 shock hit regardless of openness (u near 0 once filtered). The same carried-calm mechanism; the filter and the lag behave as specified. Not a bug.
- **Violence surges and protest (-0.001).** t59-exposure loses violence -0.0012 and protest -0.0014; t59-slope loses violence log -0.0043 (Brier within its interval). Most of violence's 2003-06 -0.003 comes with the slope, chosen at all 20 year-country origins (σ_u 0.25, ρ 0.8 then 0.95). Not traced further. These kinds' shocks are not plausibly exposure-loaded, so 2 nats is too cheap a price where the slope fits noise in two correlated columns. The price is scored on the same cumulative criterion as the transient's. A higher price (or one column) is the obvious variant.

Verdict:
- The slope does what it was built for in the planted world: the exposure coefficient's information saturates with places, and the filtered loading tracks the planted one.
- On the world it does not fix food 2010-12. Food's year history starts with the very episode, so the slope never priced in before it.
- It scores slightly below t57-frailty pooled (-0.0006 [-0.0013, +0.0001]), with small calibration gains on growth, sovereign defaults and inflation.
- The sequential evidence fixes a real per-row count of coefficient uncertainty. It helps food (2010-12 0.1158 → 0.1005, 2020-22 0.3369 → 0.3190), but on the world it exposes a regional common shock, counted per country through `:down`, and locks market crashes in 2003-07.
- t59-exposure (both) fails the fold rule against t53-noclim (2007-09) and against t57-frailty (2003-06).
- No code bug was found in either option.
- Not adopted (adoption is the user's decision). Both options stay in the code, off by default.
- The structural next steps:
  - a regional shared state inside the country node, which the sequential evidence needs before it can be used;
  - a longer food history at the year scale, so the slope can price in before 2007.

## Regional shared states: t61-regional (bnm-56c.20.3, bnm-3be)

Design: docs/RECURSIVE.md step 3e. The option is `:frailty {:regional-sds [0.0 0.25 0.5]}`, which adds one state per region inside each node (the groups of its places at `:regional-levels`, `[:region]` by default). Each place loads on them by its membership centred across the node's places. The states are AR(1) at z's persistence, and σ_r is chosen by the prequential grid at a price of 2 nats. With the option off, 2026-Q4-r1 reproduces byte for byte (data/runs/logs/repro-r1-regional.log).

Before anything was built, a planted test confirmed section 9's inference. Under a regional shock that the node does not model, a model reading three region-constant noise columns gains against the base under `:evidence-at :sequential`:
- -4.1 nats over 34 origins at one country per region;
- +0.1 at 16 countries per region;
- +10.1 at 40, where its mean weight rises from 0.10 to 0.54.

Scored independently, it loses more as countries are added (-2.9 → -83.8 nats). With regional states, from the second informed origin on, its evidence moves 0.55 nats an origin at 40 countries per region (0.22 at one country per region, 1.68 with the frailty alone), and it loses in every world.

Runs, at bb9417a with augur v0.23.0 (data/runs/t61-*/README.txt):
- t61-regional: t57-frailty's command plus the regional states and `:evidence-at :sequential`;
- t61-regional-indep: the regional states alone;
- two market-crash-only diagnostics, both with `:regional-levels [:region :subregion]`. t61-mc-sub chooses σ_r by the grid. t61-mc-p0 forces σ_r = 0.5 at every country node; it ran at ac2d212, which fixes a crash when the grid has no σ_r = 0.

Logs: data/runs/logs/score-t61-*.txt. Calibration and outliers are in score-t61-calibration.txt.

Year nodes, Brier (+ = better, 90% intervals):

| fold | t61-regional vs t53-noclim | vs t57-frailty | vs t59-seq | t61-regional-indep vs t53-noclim | vs t57-frailty |
|---|---|---|---|---|---|
| 2003-06 | -0.0029 [-0.0097, +0.0029] | -0.0053 [-0.0097, -0.0016] LOSS | +0.0000 [-0.0005, +0.0007] | +0.0023 [-0.0001, +0.0047] | -0.0002 [-0.0006, +0.0003] |
| 2007-09 | -0.0117 [-0.0155, -0.0018] LOSS | -0.0085 [-0.0118, +0.0006] | -0.0001 [-0.0006, +0.0005] | -0.0034 [-0.0055, -0.0006] LOSS | -0.0002 [-0.0008, +0.0004] |
| 2010-12 | -0.0025 [-0.0044, -0.0003] LOSS | -0.0003 [-0.0036, +0.0031] | -0.0003 [-0.0009, +0.0002] | -0.0026 [-0.0050, -0.0001] LOSS | -0.0004 [-0.0009, -0.0000] LOSS |
| 2013-19 | +0.0060 [+0.0028, +0.0092] | +0.0016 [-0.0001, +0.0030] | +0.0003 [-0.0003, +0.0008] | +0.0047 [+0.0017, +0.0080] | +0.0004 [-0.0002, +0.0010] |
| 2020-22 | +0.0038 [+0.0002, +0.0072] | -0.0001 [-0.0021, +0.0018] | -0.0018 [-0.0029, -0.0005] LOSS | +0.0021 [-0.0013, +0.0057] | -0.0019 [-0.0029, -0.0007] LOSS |
| pooled | +0.0010 [-0.0021, +0.0037] | -0.0013 [-0.0032, +0.0005] | -0.0002 [-0.0006, +0.0002] | +0.0020 [+0.0002, +0.0040] GAIN | -0.0002 [-0.0007, +0.0002] |

- Log score pooled:
  - t61-regional: -0.0058 [-0.0120, -0.0002] against t57-frailty and -0.0023 [-0.0044, -0.0003] against t59-seq;
  - t61-regional-indep: -0.0026 [-0.0049, -0.0004] against t57-frailty.
- Quarter-lead pooled: both runs are within ±0.0003 of every bench, and no interval excludes 0.
- Per kind against t57-frailty:
  - growth collapses lose in both runs: -0.0047 [-0.0084, -0.0019] in t61-regional and -0.0045 [-0.0076, -0.0017] in t61-regional-indep;
  - sovereign defaults gain +0.0007 [+0.0003, +0.0013] in t61-regional;
  - market crashes gain +0.0022 [+0.0012, +0.0035] in t61-regional-indep, and lose -0.0107 [-0.0377, +0.0127] in t61-regional;
  - food: -0.0011 [-0.0024, +0.0000] in t61-regional-indep, +0.0002 in t61-regional.

Market crashes in the two diagnostics (Brier):

| run | vs t57-frailty 2003-22 | 2003-06 | 2007-09 | vs t53-noclim 2003-22 |
|---|---|---|---|---|
| t61-mc-sub | -0.0127 [-0.0431, +0.0117] | -0.066 LOSS | -0.121 | +0.0169 [-0.0210, +0.0547] |
| t61-mc-p0 | +0.0190 [+0.0055, +0.0338] | +0.003 | +0.029 | +0.0485 [+0.0215, +0.0812] |

Calibration, country year: mean p (rate) and Brier, in the order t53-noclim → t57-frailty → t59-seq → t61-regional-indep → t61-regional:

| kind | span | rate | mean p | Brier |
|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.326 → 0.286 → 0.247 → 0.281 → 0.243 | 0.1996 → 0.1701 → 0.1827 → 0.1679 → 0.1808 |
| market crashes | 1980-2002 | 0.312 | 0.259 → 0.234 → 0.237 → 0.234 → 0.236 | 0.2395 → 0.2305 → 0.2238 → 0.2305 → 0.2242 |
| food price shocks | 2003-22 | 0.154 | 0.183 → 0.247 → 0.247 → 0.244 → 0.246 | 0.1560 → 0.1580 → 0.1572 → 0.1590 → 0.1578 |
| food | 2010-12 | 0.060 | 0.206 → 0.296 → 0.241 → 0.296 → 0.241 | 0.0844 → 0.1158 → 0.1005 → 0.1158 → 0.1006 |
| food | 2020-22 | 0.415 | 0.060 → 0.114 → 0.144 → 0.104 → 0.138 | 0.3716 → 0.3369 → 0.3190 → 0.3454 → 0.3249 |
| growth collapses | 2003-22 | 0.106 | 0.087 → 0.083 → 0.085 → 0.087 → 0.088 | 0.0990 → 0.0966 → 0.0966 → 0.1011 → 0.1013 |
| growth collapses | 1980-2002 | 0.063 | 0.103 → 0.090 → 0.098 → 0.086 → 0.087 | 0.0632 → 0.0611 → 0.0644 → 0.0613 → 0.0616 |
| banking crises | 2003-22 | 0.032 | 0.053 → 0.045 → 0.045 → 0.046 → 0.045 | 0.0317 → 0.0322 → 0.0322 → 0.0318 → 0.0318 |
| sovereign defaults | 2003-22 | 0.031 | 0.035 → 0.049 → 0.051 → 0.047 → 0.045 | 0.0307 → 0.0324 → 0.0322 → 0.0323 → 0.0317 |
| inflation crises | 2003-22 | 0.012 | 0.015 → 0.021 → 0.023 → 0.020 → 0.021 | 0.0120 → 0.0122 → 0.0124 → 0.0122 → 0.0122 |

For market crashes in the diagnostics, 2003-22 mean p and Brier are 0.240 and 0.1828 (t61-mc-sub) and 0.240 and 0.1511 (t61-mc-p0). For 1980-2002 the Brier scores are 0.2255 and 0.2264.

Issued p against the realized rate, country year, by origin year (issued in the order t53-noclim, t57-frailty, t59-seq, t61-regional-indep, t61-regional):

| kind | year | rate | issued |
|---|---|---|---|
| food | 2007 | 0.313 | 0.102, 0.238, 0.238, 0.238, 0.238 |
| food | 2008 | 0.010 | 0.109, 0.230, 0.227, 0.230, 0.227 |
| food | 2020 | 0.493 | 0.053, 0.109, 0.140, 0.094, 0.126 |
| food | 2021 | 0.500 | 0.057, 0.109, 0.159, 0.102, 0.159 |
| food | 2022 | 0.131 | 0.078, 0.132, 0.124, 0.127, 0.125 |
| market crashes | 2007 | 0.957 | 0.289, 0.252, 0.159, 0.252, 0.158 |
| market crashes | 2008 | 0.333 | 0.212, 0.254, 0.201, 0.254, 0.198 |
| market crashes | 2009 | 0.143 | 0.143, 0.277, 0.331, 0.276, 0.328 |
| growth collapses | 2008 | 0.202 | 0.061, 0.068, 0.069, 0.036, 0.036 |
| growth collapses | 2009 | 0.036 | 0.127, 0.031, 0.032, 0.034, 0.034 |
| growth collapses | 2020 | 0.063 | 0.071, 0.077, 0.076, 0.066, 0.066 |

For market crashes in 2007 (rate 0.957), t61-mc-sub issued 0.159 and t61-mc-p0 issued 0.285.

Market crashes' averaging weights at the year country node (weights of at least 0.15):

| run | 2003 | 2004 | 2005 | 2006 | 2007 | 2008 |
|---|---|---|---|---|---|---|
| t57-frailty | regime 0.94 | regime 0.55, violence 0.18 | regime 0.43, violence 0.21 | regime 0.36, violence 0.23 | regime 0.16, violence 0.48 | violence 0.65 |
| t59-seq | down 0.90 | down 0.98 | down 0.99 | down 0.98 | all 0.15, down 0.84 | all 0.36, down 0.63 |
| t61-regional-indep | regime 0.94 | regime 0.55, violence 0.18 | regime 0.43, violence 0.21 | regime 0.35, violence 0.23 | regime 0.16, violence 0.47 | violence 0.65 |
| t61-regional | down 0.91 | down 0.98 | down 0.99 | down 0.98 | all 0.16, down 0.84 | all 0.37, down 0.62 |
| t61-mc-sub | down 0.89 | down 0.98 | down 0.99 | down 0.98 | down 0.85 | all 0.34, down 0.65 |
| t61-mc-p0 | all 0.20, regime 0.71 | all 0.31, regime 0.38 | all 0.32, regime 0.28 | all 0.74 | all 0.98 | all 0.99 |

What it does:
- **The mechanism holds on the world.** In t61-mc-p0, region and subregion states are forced on from the start, and the sequential evidence no longer locks on `:down`. Market crashes issue 0.26 and 0.29 in 2006-07 (t59-seq: 0.15 and 0.16). Their 2003-22 Brier is 0.1511, below every run so far (t57-frailty 0.1701, t53-noclim 0.1996). Against t57-frailty that is +0.019 [+0.005, +0.034], and no fold is lost.
- **The grid does not turn it on in time.** At market crashes' year country node, t61-regional chose region states at 16 of 59 origins: in 2000 and 2002, then from 2009. t61-mc-sub, with subregions, chose them from 1996. The lock had formed by then: `:down` held 0.69-0.97 from 1996 to 1999, on rows issued while σ_r was still 0. So t61-regional's weights are t59-seq's to two decimals, and t61-mc-sub's are nearly so.
- **Where it was chosen.**
  - growth collapses' year country node: 122 of 148 origins, from 1896;
  - food's year country node: from 2013, 9 of its 15 origins;
  - many quarter country and subregion nodes (market crashes: 108 of 248 quarter-lead country origins).

Losses traced:
- **Market crashes in t61-regional and t61-mc-sub (against t57-frailty: 2003-06 -0.066, 2007-09 -0.11 to -0.12).** This is t59-seq's lock, unchanged. The sequence:
  - The regional states must earn 2 nats in the cumulative prequential score from the node's first origin.
  - Until they do, rows are issued, learned and weighed with σ_r = 0. `:down` learns each period's regional pattern once per country, and the sequential evidence rewards it once per country.
  - That evidence was taken in 1994-99, before σ_r > 0 was chosen, and the averaging carried it into 2003-07 (forget 0.995).
  - Forced on from the start, the regional states remove it (t61-mc-p0).

  This is the same mechanism as the slope on food in section 9: a state that must earn its price before the episode cannot remove that episode's count per place. Seeing it needed subregion states: t61-mc-sub chose them four origins earlier than t61-regional's region states (1996 against 2000), but still after the lock. Not a code bug.
- **Growth collapses (-0.0047 against t57-frailty in both runs).** The regional states change the grid's choice of persistence. ρ is shared by z and the r's, and the regional tilts persist (Europe +1.0 in 2007-08 and +0.4 in 2020-21). From 1995 the node chose ρ = 0.8, where t57-frailty had 0.5, so z carries more of each episode:
  - The 2003-05 calm (z -1.8 to -2.2) was carried into the 2007-08 issues: 0.042 and 0.036 against t57-frailty's 0.071 and 0.068, at rates of 0.235 and 0.202.
  - The 2008-09 shock was carried into 2011-12: 0.133 and 0.148 against 0.090 and 0.102, at rates of 0.05 and 0.03.
  - The 2018-20 shocks were carried into 2022: 0.212 against 0.107, at a rate of 0.006.

  The filter, the lag and the choice behave as specified. This is the carried-persistence mechanism of banking in section 7, here caused by tying ρ_r to ρ. A separate persistence for the regional states is the obvious variant. Not a code bug.
- **Food 2020-22 (Brier 0.3369 → 0.3454 for independent scoring, 0.3190 → 0.3249 for sequential).** From 2013 the node chose σ_r = 0.5 at ρ = 0.8. It carried a calm Europe (-0.7 to -0.8 in 2017-19) and a high Asia (+0.6 to +0.7) into the 2020-21 issues. The 2020-21 shock hit regardless of region (the filtered r's are near 0 after it). The issues fell to 0.094-0.102, against t57-frailty's 0.109. This is the carried-tilt mechanism of the slope in section 9. Not a code bug.
- **2020-22 against t59-seq (-0.0018).** This is growth collapses 2022, above. No other kind moves by more than 0.002 in that fold.

Bugs found on the way:
- **augur v0.23.0's vector Laplace does not converge from a distant prior (bnm-2sf, open).**
  - What it does: `hier/shared-marginal-n` takes undamped Newton steps, clamped per component at 5. In the planted region world (one origin, a 6-d state) it cycled for 100 iterations. The returned point had a log posterior of -181, against -64.8 at the mode, and z = 5.09 against 2.30.
  - The effect: its log marginal then misprices grid candidates. A world with no regional shock chose σ_r = 1 for 12 origins. The averaging's evidence for rows with a vector shared state goes through the same function, so t59-slope, t59-exposure and t61 depend on it wherever an origin's miss is large. How often that happens on the world has not been measured.
  - The fix: backtracking on the log posterior converges in 4 steps, and with it that world keeps σ_r = 0. It belongs in augur (a release and a deps bump), which this branch cannot make.
- **The first informed origins are learned with no shared state (bnm-93n, open).** Rows issued before the node's frailty has filtered anything carry the tie-break's σ = 0. In the planted world that first origin moved the averaging by 9-15 nats, the same with and without the option. At food's year country node, that origin is the 2007-08 shock.
- **A grid without σ_r = 0 crashed (fixed in ac2d212).** At a node with no regional group, the regional candidates were dropped, the grid was left empty and the run threw. They now fall back to their σ_r = 0 twins. t61-regional and t61-regional-indep list 0.0, so their grids were unaffected.

Verdict:
- The inference of section 9 is confirmed, in a planted world and on the world (t61-mc-p0). Under sequential evidence, a regional shock that the node does not model is rewarded once per country through a region-constant input. Region and subregion states remove that reward, and market crashes then score 0.1511 for 2003-22, +0.019 [+0.005, +0.034] against t57-frailty.
- As built, with σ_r chosen by the prequential grid at 2 nats, the states arrive after the lock has formed. t61-regional scores as t59-seq (pooled -0.0002 [-0.0006, +0.0002]). It fails the fold rule against t57-frailty (2003-06) and against t53-noclim (2007-09, 2010-12).
- Without the sequential rule (t61-regional-indep), the regional states score -0.0002 [-0.0007, +0.0002] against t57-frailty. They lose 2010-12 and 2020-22, on growth collapses and food, through the persistence they share with z.
- Not adopted (adoption is the user's decision). The option stays in the code, off by default.
- Next steps, in order:
  - augur's line search (bnm-2sf);
  - a separate persistence for the regional states;
  - a way to have them in place before the lock forms: subregions at a smaller price, or σ_r chosen from evidence across kinds rather than from one node's history. Forcing σ_r is not a candidate rule, because it was chosen after seeing the result.
