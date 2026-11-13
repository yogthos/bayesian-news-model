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

Building it surfaced a second count per place. At an input's first reading, augur's evidence integrated a model's coefficient uncertainty separately in every row, so a model unsure of a place-varying input was paid or rewarded for it once per place. In the planted world that was 2 to 8 nats in one origin (26 under augur v0.23.0, whose marginal did not converge there). The fix is `:evidence-at :sequential`: each row is scored given the period's rows before it. Both options are off by default, and 2026-Q4-r1 reproduces byte for byte with them off (data/runs/logs/repro-r1-exposure.log).

Runs, at 9b81890 with augur v0.23.0 (data/runs/t59-*/README.txt):
- t59-exposure: t57-frailty's command plus both options (the candidate);
- t59-slope: the slope alone;
- t59-seq: the sequential evidence alone.

Logs: data/runs/logs/score-t59-*.txt; calibration in score-t59-calibration.txt.

These runs predate augur v0.23.1 (bnm-2sf). v0.23.0's vector Laplace marginal could stop short of the mode, overstating a wide slope prior's marginal by 10 to 25 nats an origin in the planted world, so the grid chose σ_u too large. t59-slope and t59-exposure, and any run with a vector shared state, need rerunning before their numbers are read; RECURSIVE.md step 3d has the planted world's corrected numbers.

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

## 10. The frailty from a fresh quarter scale: t60-fresh (bnm-56c.35)

The design is in docs/RECURSIVE.md step 4a. A frailty-only scale, `quarter-now`, has a window of (t, t+3] and is known 3 months plus the lag after it. Coarser nodes take their prior as κ times its state, κ chosen in the prequential grid from [0 0.5 1 1.5] (κ = 0 is the node's own prior). Runs at 63d9bda with augur v0.23.0; both ran in about 2 h 40 min:
- t60-fresh: t57-frailty's command plus `--food-outcomes monthly --fresh-frailty quarter-now` and `:frailty {:prior :finer :source :quarter-now}`;
- t60-monthly: t57-frailty's command plus `--food-outcomes monthly` alone, to isolate the scale.

The rule gave the scale to coups, food, market crashes, protest surges and violence surges. The other eight kinds keep their own prior and score exactly as t57-frailty. With the option off, 2026-Q4-r1 reproduces byte for byte (data/runs/logs/repro-r1-fresh.log). Logs:
- data/runs/logs/score-t60-*-vs-*.txt: the year folds over 13 kinds and over 12 without food, quarter-lead, and per kind with intervals;
- score-t60-*-world-vs-*.txt (bnm.world-score);
- score-t60-calibration.txt and score-t60-outliers.txt.

Food under monthly outcomes is a different target from the base's quarterly-rule catalogue. Paired against t53-noclim or t57-frailty, it scores their forecasts of the old target on the new outcomes. So the clean test of the scale is t60-fresh against t60-monthly, and the 12-kind pool leaves food out.

Year nodes, Brier (+ = better, 90% intervals):

| fold | t60-fresh vs t60-monthly | vs t57-frailty | vs t53-noclim | vs t53-noclim, no food |
|---|---|---|---|---|
| 2003-06 | +0.0000 [-0.0001, +0.0002] | -0.0007 [-0.0021, +0.0001] | +0.0017 [-0.0017, +0.0047] | +0.0027 [+0.0000, +0.0051] |
| 2007-09 | +0.0002 [+0.0000, +0.0003] | +0.0001 [-0.0006, +0.0008] | -0.0028 [-0.0049, -0.0001] LOSS | -0.0033 [-0.0055, -0.0004] LOSS |
| 2010-12 | +0.0007 [+0.0002, +0.0011] | +0.0030 [+0.0006, +0.0058] | +0.0010 [-0.0007, +0.0025] | +0.0003 [-0.0021, +0.0027] |
| 2013-19 | +0.0002 [-0.0001, +0.0005] | -0.0000 [-0.0003, +0.0002] | +0.0044 [+0.0019, +0.0070] | +0.0049 [+0.0022, +0.0077] |
| 2020-22 | -0.0014 [-0.0023, -0.0005] LOSS | -0.0016 [-0.0027, -0.0007] LOSS | +0.0024 [-0.0012, +0.0062] | +0.0014 [-0.0008, +0.0045] |
| pooled | +0.0000 [-0.0003, +0.0003] | +0.0001 [-0.0006, +0.0009] | +0.0024 [+0.0008, +0.0041] GAIN | +0.0025 [+0.0008, +0.0043] GAIN |

- Log score pooled: -0.0009 [-0.0029, +0.0009] against t60-monthly, and +0.0066 [+0.0019, +0.0116] against t53-noclim.
- Without food, against t60-monthly: -0.0001 [-0.0001, +0.0000], with 2013-19 -0.0002 [-0.0003, -0.0000].
- Quarter-lead, 2003-22: +0.0003 [+0.0001, +0.0006] against t60-monthly and +0.0006 [-0.0001, +0.0016] against t57-frailty.
- Per kind against t60-monthly, all within their intervals: food +0.0008 [-0.0038, +0.0044] (log -0.010), market crashes -0.0010 [-0.0021, +0.0000], coups +0.0002, violence +0.0002, protest -0.0000.
- t60-monthly against t57-frailty: +0.0001 [-0.0006, +0.0009]. Only food moves: 2010-12 +0.0024 GAIN, 2003-06 -0.0007.

Where κ was chosen (origins since 2000, country nodes):
- food: year κ = 1.5 at 12 of 17 origins (from 2012), quarter-lead never;
- market crashes: year never (once, in 1984), quarter-lead κ = 1.5 at 83 of 99;
- protest surges: year κ = 0.5 throughout;
- violence surges: year κ = 0.5 at 9 of 23;
- coups: never.

Calibration, country year, mean p (rate) and Brier, t53-noclim → t57-frailty → t60-monthly → t60-fresh. Food is under its own target: the base's catalogue for the first two runs, the monthly one for the last two.

| kind | span | mean p | rate | Brier |
|---|---|---|---|---|
| market crashes | 2003-22 | 0.326 → 0.286 → 0.286 → 0.288 | 0.178 | 0.1996 → 0.1701 → 0.1701 → 0.1711 |
| market crashes | 2007-09 | 0.253 → 0.257 → 0.257 → 0.258 | 0.750 | 0.4051 → 0.4428 → 0.4428 → 0.4409 |
| food | 2003-22 | 0.183 → 0.247 / 0.224 → 0.207 | 0.154 / 0.151 | 0.1560 → 0.1580 / 0.1521 → 0.1514 |
| food | 2010-12 | 0.206 → 0.296 / 0.218 → 0.191 | 0.060 / 0.058 | 0.0844 → 0.1158 / 0.0788 → 0.0701 |
| food | 2020-22 | 0.060 → 0.114 / 0.100 → 0.110 | 0.415 / 0.384 | 0.3716 → 0.3369 / 0.3160 → 0.3342 |
| coups | 2003-22 | 0.055 → 0.056 → 0.056 → 0.052 | 0.045 | 0.0427 → 0.0426 → 0.0426 → 0.0424 |
| protest | 2003-22 | 0.040 → 0.050 → 0.050 → 0.048 | 0.030 | 0.0276 → 0.0277 → 0.0277 → 0.0278 |
| violence | 2003-22 | 0.119 → 0.107 → 0.107 → 0.109 | 0.080 | 0.0709 → 0.0710 → 0.0710 → 0.0708 |

Growth collapses, banking crises, sovereign defaults and inflation crises are t57-frailty's to four decimals (score-t60-calibration.txt).

**The outlier episodes.** These are year country nodes. The year is the origin's (December), and its window is the next 24 months. Each cell is the mean p issued, with the realized rate in brackets. Food is under the base catalogue for t53-noclim and t57-frailty, and the monthly one for the t60 runs.

| episode | origin | t53-noclim | t57-frailty | t60-monthly | t60-fresh |
|---|---|---|---|---|---|
| food 2007-08 | 2006 | 0.500 (0.418) | 0.500 (0.418) | 0.233 (0.432) | 0.233 (0.432) |
| | 2007 | 0.102 (0.313) | 0.238 (0.313) | 0.242 (0.341) | 0.242 (0.341) |
| | 2008 | 0.109 (0.010) | 0.230 (0.010) | 0.200 (0.008) | 0.200 (0.008) |
| food 2020-22 | 2019 | 0.053 (0.087) | 0.112 (0.087) | 0.108 (0.107) | 0.070 (0.107) |
| | 2020 | 0.053 (0.493) | 0.109 (0.493) | 0.099 (0.458) | 0.092 (0.458) |
| | 2021 | 0.057 (0.500) | 0.109 (0.500) | 0.100 (0.475) | 0.066 (0.475) |
| | 2022 | 0.078 (0.131) | 0.132 (0.131) | 0.100 (0.118) | 0.210 (0.118) |
| market crashes 2008 | 2006 | 0.281 (0.957) | 0.269 (0.957) | 0.269 (0.957) | 0.271 (0.957) |
| | 2007 | 0.289 (0.957) | 0.252 (0.957) | 0.252 (0.957) | 0.253 (0.957) |
| | 2008 | 0.212 (0.333, n 3) | 0.254 (0.333) | 0.254 (0.333) | 0.255 (0.333) |
| | 2009 | 0.143 (0.143, n 14) | 0.277 (0.143) | 0.277 (0.143) | 0.276 (0.143) |
| growth collapses | 2007 | 0.051 (0.235) | 0.071 (0.235) | 0.071 (0.235) | 0.071 (0.235) |
| | 2008 | 0.061 (0.202) | 0.068 (0.202) | 0.068 (0.202) | 0.068 (0.202) |
| | 2009 | 0.127 (0.036) | 0.031 (0.036) | 0.031 (0.036) | 0.031 (0.036) |
| | 2019 | 0.068 (0.554) | 0.078 (0.554) | 0.078 (0.554) | 0.078 (0.554) |
| | 2020 | 0.071 (0.063) | 0.077 (0.063) | 0.077 (0.063) | 0.077 (0.063) |

No run forecasts an outlier episode. The fresh scale changes none of them for the better, and it moves food 2020-22 the wrong way.

Losses and non-gains traced:
- **Food 2020-22 (-0.0182 Brier against t60-monthly): the episode starts after the freshest information the year has.** Under the monthly rule the episode is 2022: onsets ran 3-9 a quarter through 2019-21, then 16, 31, 21 and 19 in 2022's four quarters. A food quarter is known 12 months after its origin (a 3-month window plus the 9-month publication lag), so the quarters each year origin had seen were these:

  | year origin | quarters seen through | source z | κ | issued vs t60-monthly | rate |
  |---|---|---|---|---|---|
  | Dec 2020 | Jan-Mar 2020 | +0.2 to +0.6 | 1.0 | 0.092 vs 0.099 | 0.458 |
  | Dec 2021 | Jan-Mar 2021 | -0.74 | 1.5 | 0.066 vs 0.100 | 0.475 |
  | Dec 2022 | Jan-Mar 2022 | +2.7 | 1.5 | 0.210 vs 0.100 | 0.118 |

  - At Dec 2021 the source still read the 2016-19 calm (z -0.5 to -1.1). Run forward and scaled by κ = 1.5, it moved the issue below the year's own prior.
  - At Dec 2022 the episode had been seen, but the next window (2023-24) is calmer, and many places are busy and out of risk.
  - κ = 1.5 was earned on 2012-19, when the source's calm and the year's calm went together (the 2010-12 gain, +0.0088 for food).
  - The source, the lag and the choice behave as specified, and no read is after t. A shock that begins and peaks within 12 months of publication lag cannot reach a year window that opens at the origin. Not a bug: an information limit.
- **Food 2007-08: no change.** Food's year node had no history in which κ could earn its price. Its first filtered origin is December 2006 (monthly outcomes, one more than the base catalogue's 2007; RECURSIVE step 4a traces why the history starts there). κ stays 0, tied with the node's own candidates, until 2012. The fresh scale cannot act on an episode at the start of the node's history.
- **Market crashes (-0.0010 [-0.0021, +0.0000]; 2013-19 -0.0031): through the quarters, not the year's prior.**
  - The year node never chose κ > 0 after 1984, so its prior is t57-frailty's own.
  - The year's forecasts still moved: +0.006 to +0.009 in mean p at 2013-17, where the rate was 0 to 0.04. It reads the quarter-lead's states (`:up`), and the quarter-lead took κ = 1.5 at 83 of 99 origins.
  - One origin in 1984 at κ = 1.5 also issued its rows a different `:shared` shock, which the base learned from; the year's own filtered z differs slightly from then on.
  - The averaging leaned on the world model a little more (0.88 against 0.85 in 2014).
  - The mechanism is the up read carrying a changed quarter state. It was read from the forecasts and weights, not tested in a planted world. Not a bug.
- **Market crashes 2008: the source sees the crash after most countries have crashed.** Quarter-lead country nodes:

  | quarter-lead origin | quarters seen through | source z | at risk | rate | issued (t60-monthly) |
  |---|---|---|---|---|---|
  | Dec 2007 | Jul-Sep 2007 | -0.92 | 41 | 0.93 | 0.171 |
  | Mar 2008 | Jul-Sep 2007 | -0.92 | 35 | 0.94 | 0.121 |
  | Jun 2008 | Jan-Mar 2008 | 1.28 | 20 | 0.90 | 0.168 (0.191) |
  | Sep 2008 | Jul-Sep 2008 | 4.25 | 3 | 0.33 | |

  - A quarter is known 6 months after its origin. The crash shows only in the source's Jan-Mar 2008 window (z 1.28; 2.98 by Jul-Sep).
  - At the source's ρ = 0.5, z = 1.28 run forward two periods and taken at κ = 1.5 is +0.48, less half the stationary variance 2.25 (1.125). That is below the node's calm prior, so the issue barely moves.
  - By September 2008 three countries were still at risk.
  - The year at Dec 2007 (rate 0.957) issues 0.253, as t57-frailty does.
  - The source does what it is built to do. The episode's at-risk mass is gone within the 6-month lag. Not a bug.
- **Growth collapses: unchanged by construction.** The catalogue is annual (every onset in January), so the rule gives it no fresh scale. A monthly growth-collapse catalogue (industrial production) is the prerequisite, a follow-up.
- **2007-09 against t53-noclim (-0.0028).** This is t57-frailty's fold loss, carried over unchanged (§7: the market crash lock path and banking's persistence). The fresh scale adds +0.0002 to it.

Bugs: none found in the runs. Building the option avoided the one the scoping named, the source run forward at the node's period rather than its own; a test pins that.

Verdict: the fresh scale works in the planted world. There the year's z at the first origin six months into an episode is 1.10, against -0.125 with its own prior. On the world it changes nothing pooled: +0.0000 [-0.0003, +0.0003] against t60-monthly and +0.0024 [+0.0008, +0.0041] against t53-noclim, the same as t57-frailty's +0.0022. It fails the fold rule against t60-monthly in 2020-22 (food) and, as t57-frailty does, against t53-noclim in 2007-09. It does not reach the outlier episodes:
- food's 2022 shock starts inside the CPI's publication lag;
- the 2008 crashes ran their course inside the market lag;
- growth collapses have no sub-annual catalogue.

Fresher frailty cannot reach them, because an episode's onset precedes every outcome that reveals it. What is left is information that leads onsets, not the outcomes themselves: prices, spreads and indices read at t, which the quarter gauges and the world gauges already carry, or a regime state that switches on them (step 5). Monthly food outcomes alone (t60-monthly) are neutral pooled (+0.0001 [-0.0006, +0.0009] against t57-frailty) and help food 2010-12 (Brier 0.0788 against t57-frailty's 0.1158 on the old target). Not adopted (adoption is the user's decision). The option stays in the code, off by default.

## 11. The three bugs of LEADING.md 4 fixed: t62-fixes (bnm-x6o, bnm-jnw, bnm-9fg)

docs/LEADING.md section 4 found three bugs that kept the `:up` model out of the year averaging (market crashes: weight under 0.005 since 1968; food: 0.00 in 2019-21 while it was the best-scoring model). Each is fixed behind an option, off by default, with its own test; 2026-Q4-r1 reproduces byte for byte with them off (data/runs/logs/repro-r1-fixes.log). Code at 39a8a6f, augur v0.23.0.

1. **Dead gauge (bnm-x6o), `--lag-stale true`.** The year row read WDI at `{:stale 18}`, and tertiary enrolment is published 21 months after its year, so `:tertiary5` (and protest's and violence's `[:tertiary5 :growth3]` tensions) were nil at every country and origin. With the option each slow series is read at its own lag (bnm.named/lag-stale): tertiary 33 months, the Penn World Table 60. Test: bnm-x6o-each-slow-series-is-read-at-its-own-publication-lag.
2. **Discounting before any data (bnm-jnw), `:discount-from :data`.** augur's step discounts the covariance on periods with no rows, and the lattice steps every node from 1870, so a late node met its first data at its prior times 1/δ^n (food quarter ~15x). With the option a node is not stepped until it has rows. Test: bnm-jnw-no-discount-before-a-node-s-first-data (variance 11.2 → the declared 1.0).
3. **The standardizers' cap on a constant input (bnm-9fg), `:flow-std :stable` and `--gauge-std stable`.** Two places, not one:
   - the flow standardizer's one-pass spread (rounding noise above the 1e-9 guard);
   - the gauges' standardizer (bnm.lattice-banking), where world3 is 0 at every origin before a kind's first known onset. Its spread is then the 1e-12 variance floor (sd 1e-6), so its first move lands on the cap. This, not the flow standardizer, is what put food's world3 at +5.00 at every country at Dec 2007 (LEADING.md 4.3 named the flow one).

   Both now keep Welford sums and read a spread under max(1e-9, 1e-8 |mean|) as none. Tests: bnm-9fg-a-constant-input-is-not-put-on-the-cap, bnm-9fg-a-gauge-flat-until-now-is-not-put-on-the-cap. In the replay, world3 at food Dec 2007 reads 0.00 (was +5.00).

### The stale audit

Every slow series the lattice reads, against its publication lag in data/slow/series.tsv. "Refused" is the share of country-origins in 2000-22 with a published value that the staleness refuses.

| read | stale | lag (median) | refused |
|---|---|---|---|
| year row WDI: tertiary enrolment | 18 | 21 | **100% (dead)** |
| year row WDI: urban share, infant mortality, working age, inflation, food inflation | 18 | 9 | 0-1.6% |
| quarter political economy: PWT profit rate / labour share (`--pwt-stale`) | 36 | editions 2-5 years apart | **19% / 29%** (dead 2018, 2023-24) |
| quarter political: food imports, fuel imports / exports shares | 30 | 9 | 12-14% (countries whose series stopped; age 11 months at the median read) |
| trade openness (quarter and exposure) | 30 | 9 | 3% (same) |
| every other quarter political, financial and world input | 6-30 | 0-12 | 0-1% |

- PWT is nil everywhere before mid-2016 in any case: the first vintage on disk is PWT 9.0, published 2016-06, and every earlier year is dated by it.
- Only tertiary enrolment and the PWT pair are refused by a staleness shorter than their lag, and `--lag-stale` covers both. The other refusals are series that stopped, which the staleness is there to refuse.

### Runs

Each run's command is in data/runs/t62-*/README.txt.
- **t62-fixes**: t57-frailty plus the three fixes.
- **t62-fixes-seq**: plus `:evidence-at :sequential` (LEADING.md 4.4).
- **t62-fixes-min**: t62-fixes plus `:flow-std-min-origins 10` (bnm-56c.34 at its tested value; step 7 below).

Logs: data/runs/logs/score-t62-*.txt, plus calibration, outliers, up-weights and kind-folds files.

Year nodes, Brier (+ = better, 90% intervals):

| fold | t62-fixes vs t57-frailty | vs t53-noclim | t62-fixes-seq vs t57-frailty | vs t53-noclim | t62-fixes-min vs t57-frailty | vs t53-noclim |
|---|---|---|---|---|---|---|
| 2003-06 | +0.0007 [-0.0002, +0.0015] | +0.0031 [+0.0000, +0.0061] | +0.0025 [+0.0006, +0.0045] | +0.0049 [+0.0024, +0.0080] | +0.0025 [+0.0006, +0.0048] | +0.0050 [+0.0015, +0.0084] |
| 2007-09 | +0.0022 [-0.0012, +0.0040] | -0.0010 [-0.0050, +0.0012] | +0.0009 [-0.0029, +0.0029] | -0.0023 [-0.0061, -0.0002] LOSS | +0.0001 [-0.0026, +0.0026] | -0.0031 [-0.0057, -0.0009] LOSS |
| 2010-12 | +0.0007 [-0.0018, +0.0028] | -0.0015 [-0.0043, +0.0014] | -0.0026 [-0.0059, +0.0008] | -0.0047 [-0.0070, -0.0025] LOSS | +0.0026 [-0.0008, +0.0061] | +0.0005 [-0.0024, +0.0029] |
| 2013-19 | +0.0016 [+0.0007, +0.0025] | +0.0060 [+0.0028, +0.0094] | +0.0025 [+0.0008, +0.0041] | +0.0068 [+0.0032, +0.0108] | +0.0006 [-0.0006, +0.0018] | +0.0050 [+0.0018, +0.0083] |
| 2020-22 | -0.0010 [-0.0032, +0.0007] | +0.0030 [+0.0012, +0.0046] | +0.0010 [-0.0011, +0.0026] | +0.0050 [+0.0027, +0.0072] | +0.0006 [-0.0014, +0.0020] | +0.0046 [+0.0019, +0.0070] |
| pooled | +0.0009 [-0.0001, +0.0016] | +0.0031 [+0.0013, +0.0051] GAIN | +0.0013 [-0.0001, +0.0025] | +0.0035 [+0.0013, +0.0060] GAIN | +0.0013 [+0.0003, +0.0023] GAIN | +0.0035 [+0.0016, +0.0053] GAIN |

- Log pooled, against t57-frailty: t62-fixes +0.0021 [-0.0004, +0.0041], t62-fixes-seq +0.0038 [+0.0002, +0.0072] GAIN, t62-fixes-min +0.0033 [+0.0003, +0.0062] GAIN.
- t62-fixes is the only one with no fold loss against either bench. t57-frailty itself loses 2007-09 against t53-noclim (§7).
- Per kind against t57-frailty (bnm.world-score):
  - market crashes gain in all three: +0.0140 [+0.0050, +0.0230], +0.0338 [+0.0184, +0.0498] and +0.0179 [+0.0063, +0.0291];
  - food: -0.0012 [-0.0047, +0.0010], -0.0145 [-0.0265, -0.0031] (seq, a loss) and -0.0004 [-0.0079, +0.0080];
  - small losses in protest (-0.0007 to -0.0014) and in sovereign defaults (t62-fixes -0.0001 [-0.0003, -0.0000]);
  - everything else within ±0.0004.

Calibration, country year, mean p (rate) and Brier, t53-noclim → t57-frailty → t62-fixes → t62-fixes-seq → t62-fixes-min:

| kind | span | rate | mean p | Brier |
|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.326 → 0.286 → 0.259 → 0.198 → 0.235 | 0.1996 → 0.1701 → 0.1561 → **0.1363** → 0.1523 |
| market crashes | 1980-2002 | 0.312 | 0.259 → 0.234 → 0.226 → 0.215 → 0.205 | 0.2395 → 0.2305 → 0.2309 → 0.2313 → 0.2349 |
| market crashes | 2010-12 | 0.080 | 0.244 → 0.276 → 0.206 → 0.139 → 0.170 | 0.1234 → 0.1174 → 0.1001 → 0.0902 → 0.0954 |
| food | 2003-22 | 0.154 | 0.183 → 0.247 → 0.250 → 0.286 → 0.273 | 0.1560 → 0.1580 → 0.1592 → 0.1725 → 0.1584 |
| food | 2010-12 | 0.060 | 0.206 → 0.296 → 0.323 → 0.394 → 0.288 | 0.0844 → 0.1158 → 0.1238 → 0.1746 → 0.1056 |
| food | 2020-22 | 0.415 | 0.060 → 0.114 → 0.119 → 0.139 → 0.159 | 0.3716 → 0.3369 → 0.3365 → 0.3228 → 0.3176 |
| growth collapses | 2003-22 | 0.106 | 0.087 → 0.083 → 0.083 → 0.084 → 0.083 | 0.0990 → 0.0966 → 0.0966 → 0.0966 → 0.0966 |
| protest surges | 2003-22 | 0.030 | 0.040 → 0.050 → 0.047 → 0.054 → 0.050 | 0.0276 → 0.0277 → 0.0284 → 0.0292 → 0.0288 |
| violence surges | 2003-22 | 0.080 | 0.119 → 0.107 → 0.107 → 0.105 → 0.110 | 0.0709 → 0.0710 → 0.0717 → 0.0714 → 0.0706 |

Banking, sovereign, inflation, coups, conflict, war, currency and fiscal are within ±0.0005 of t57-frailty (score-t62-calibration.txt). Market crashes in 2003-22 are now below every ceiling of LEADING.md 2 under the sequential evidence (0.1363 against 0.1416-0.1546), and below t57-frailty in all three runs.

**The outlier episodes** (mean p issued at the December origin, rate in brackets):

| episode | origin | t57-frailty | t62-fixes | t62-fixes-seq | t62-fixes-min |
|---|---|---|---|---|---|
| food 2007-08 | 2006 | 0.500 (0.418) | 0.500 | 0.500 | 0.500 |
| | 2007 | 0.238 (0.313) | 0.213 | 0.213 | 0.213 |
| food 2020-22 | 2020 | 0.109 (0.493) | 0.109 | 0.140 | 0.169 |
| | 2021 | 0.109 (0.500) | 0.109 | 0.143 | 0.143 |
| market crashes 2008 | 2006 | 0.269 (0.957) | 0.269 | 0.304 | 0.273 |
| | 2007 | 0.252 (0.957) | 0.291 | 0.273 | 0.260 |
| growth collapses | 2007 | 0.071 (0.235) | 0.070 | 0.073 | 0.070 |
| | 2008 | 0.068 (0.202) | 0.067 | 0.069 | 0.067 |
| | 2019 | 0.078 (0.554) | 0.078 | 0.078 | 0.078 |

The fixes do not reach the episodes. The market-crash gain is in calm years (2010-19), where the forecasts come down toward the rate.

**The `:up` weight at the year country node** (Decembers; full table in score-t62-up-weights.txt):

| kind | years | t57-frailty | t62-fixes | t62-fixes-seq | t62-fixes-min |
|---|---|---|---|---|---|
| market crashes | 1965-67 | 0.06 | 0.05-0.07 | 0.05-0.07 | 0.00 (no flows yet) |
| market crashes | 1968-2022 | ≤ 0.004 | ≤ 0.003 | ≤ 0.005 | ≤ 0.003 |
| food | 2008-09 | 0.06, 0.04 | 0.06, 0.03 | 0.06, 0.04 | 0.06, 0.00 |
| food | 2010-22 | 0.000 | 0.000 | 0.000 | 0.000 |

The leaders: t62-fixes, market crashes all-inputs 0.33-0.99 from 2006, food patronage 0.58-0.99 from 2012; seq and min, all-inputs at 0.98-1.00 from 2005 (market crashes) and 2010-15 (food). **`:up` is still locked out.**

### Why: the losses traced (PLAN principle 9)

Traced on replays of food and market crashes with each run's exact options (the lead/replayfix.clj of LEADING.md, dumping every December's issued rows, each model's issued predictive and the year models; session scratchpad).

1. **One origin's cross-section as the flows' scale (bnm-56c.34, known).** Once bugs 2-3 are gone, `:up` still lands near the cap at the node's second informed origin:
   - food Dec 2008: mean -4.07, 32% of countries at -5;
   - market crashes Dec 1965: -3.91, 93% at the cap.

   The standardizer scales by the first informed origin's spread across countries, not a spread over time. On issued scores this costs food's `:up` 37 nats at 2008 against base. It then learns a qw2 slope from those capped rows and loses 33 more in 2012. By 2019 patronage is 59 nats ahead of `:up` on cumulative issued log score. In t57 `:up` had caught up by 2021; with the fixes its 2020-21 issues are worse (-168 and -143 against t57's -137 and -101), because its qlead slopes were learned differently. `:flow-std-min-origins 10` removes the cap: t62-fixes-min food 2010-12 gains +0.010 on t57. But it holds `:up` at 0 until the tenth informed origin (food 2017), so the group has no input and drops out (weight 0, by design).
2. **The averaging's evidence rewards hindsight, not forecasts.** This is why `:up` stays out once the caps are gone. In t57 and t62-fixes (evidence at learn time) and t62-fixes-seq (sequential at learn time), a row is scored about 27 months after issue (market crashes; 42 for food), by a state that has since learned its neighbours. On the *issued* predictive the ranking is the other way round:
   - market crashes (seq replay): `:up` leads the all-inputs model by 16-31 nats from 1980 to 2007, and by 2007 `:down` by 35 and regime and violence by 24. In 2006-07 it beats base by 8 and 10 nats per origin. Its weight is ≤ 0.005 throughout, while all-inputs holds 0.96-1.00 from 2000.
   - food (seq replay): the all-inputs model is the *worst* issued model from 2008 on, 25 nats behind base by 2008 and 207 by 2015. Its weight goes to 0.98-1.00 from 2010, and food 2010-12 issues 0.394 against a rate of 0.060: Brier 0.1746, the -0.0145 loss.

   Under the sequential rule the widest model learns each period's pattern from the period's first rows and is paid for "predicting" the rest. This is §9's mechanism, here with the 2006-08 global shock: the frailty filters no shock at food's first origins, because it has no history to choose σ from. Scoring by the issued predictive (`:evidence-at :issue`, replayed with all three fixes) gives the ranking back but not the lock. It counts each row's coefficient uncertainty separately (bug 4), so `:up`'s 2006-07 advantage shrinks to 2-4 nats, and `:up` reaches 0.12-0.28 only in 1980-95 (market crashes) and stays 0 at food. The rule that both scores at issue and counts an origin's rows jointly (the issued predictive's joint marginal per origin, with the shared shock) does not exist in augur.
3. **The forgetting is per period.** `:forget 0.995` applies at every period of every scale, so at the year node it is 0.5% a year: a half-life of 138 years. A group that falls 40 nats behind in its first informed decade cannot come back within the sample unless it out-scores the leader by more than that. This is a design reading, not a code bug, but it is what makes a lock permanent.
4. **The revived gauge costs protest surges (-0.0007 to -0.0014).** Replays of protest with and without `--lag-stale` on the same code: 0.0277 → 0.0291. The gauge reads correctly (WDI gross enrolment, 5-year change). Its tension moves the averaging to the all-inputs model five years earlier (2005-11, where history had held 0.6-0.96), which is the same evidence rule as 2. Violence: 0.0710 → 0.0713.
5. **Market crashes 2007-09 under min and seq (-0.031, -0.020 against t53-noclim).** Both lock on all-inputs from 2005 (0.99-1.00), which issues 0.26-0.30 at the 2006-07 origins (rate 0.96). Same mechanism as 2; under min, the 1964-73 decade without flows sets the path (LOOP-AUDIT 7).

Bugs found and fixed: the gauge-standardizer cap (part of bnm-9fg above). Items 1-3 are known defects with options, or design: the first is fixed by an existing option, and the second needs a new evidence rule in augur (issue time, joint per origin), too large to fold in here. It goes to the user as a decision.

Verdict:
- The three fixes are correct and tested. t62-fixes gains pooled against t53-noclim (+0.0031 [+0.0013, +0.0051]) and fails no fold against either bench, while t57-frailty fails 2007-09 against t53-noclim.
- It ties t57-frailty pooled (+0.0009 [-0.0001, +0.0016]) and gains market crashes (+0.014 [+0.005, +0.023]).
- t62-fixes-min is the strongest against t57-frailty (+0.0013 [+0.0003, +0.0023] GAIN, no fold loss), but it loses 2007-09 against t53-noclim.
- t62-fixes-seq has the best market crashes (0.1363), but its food loss (-0.0145) fails the rule.
- None reopens `:up`, and none reaches the outlier episodes.
- What locks `:up` now is the averaging's evidence: it is computed from rows learned in hindsight, while on the issued forecasts `:up` beats the models holding the weight. Next: an issue-time joint evidence rule in augur.
- Not adopted (adoption is the user's decision); every option stays off by default.
