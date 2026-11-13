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

### augur v0.23.1 (bnm-2sf, the Newton fix in hier/shared-marginal)

Rerun on the working tree's augur v0.23.1 (2de08aa; deps.edn not committed, the exposure-slope tests' fallout is on branch fix-2sf-fallout). Commands in data/runs/t57-v231/README.txt and data/runs/t62-fixes-v231/README.txt.

- **t57-v231 against t57-frailty:** pooled Brier +0.00000 [-0.00000, +0.00001]; every kind within ±0.00002.
  - Forecasts move materially only where Newton had not converged: food at the 2024-25 origins (|Δp| up to 0.35; unscored, but these are current forecasts), fiscal consolidation 2022-25 (quarter-lead up to 0.21), market crashes' quarter-w2 2008-09 (up to 0.09).
  - Elsewhere the moves are 1e-14 to 1e-4.
- **t62-fixes-v231:**
  - against t57-v231: pooled +0.0009 [-0.0001, +0.0016], folds +0.0007, +0.0022, +0.0006, +0.0016 [+0.0007, +0.0025], -0.0010 [-0.0032, +0.0007]; market crashes +0.0139 [+0.0049, +0.0230] GAIN, food -0.0012 [-0.0048, +0.0010], sovereign defaults -0.0001 [-0.0003, -0.0000];
  - against t53-noclim: +0.0031 [+0.0013, +0.0051] GAIN, no fold loss (2007-09 -0.0010 [-0.0050, +0.0012]);
  - against t62-fixes: -0.00001 [-0.00001, +0.00000].

The Newton fix changes none of §11's conclusions; every number above holds to the fourth decimal on either version.

Verdict:
- The three fixes are correct and tested. t62-fixes gains pooled against t53-noclim (+0.0031 [+0.0013, +0.0051]) and fails no fold against either bench, while t57-frailty fails 2007-09 against t53-noclim.
- It ties t57-frailty pooled (+0.0009 [-0.0001, +0.0016]) and gains market crashes (+0.014 [+0.005, +0.023]).
- t62-fixes-min is the strongest against t57-frailty (+0.0013 [+0.0003, +0.0023] GAIN, no fold loss), but it loses 2007-09 against t53-noclim.
- t62-fixes-seq has the best market crashes (0.1363), but its food loss (-0.0145) fails the rule.
- None reopens `:up`, and none reaches the outlier episodes.
- What locks `:up` now is the averaging's evidence: it is computed from rows learned in hindsight, while on the issued forecasts `:up` beats the models holding the weight. Next: an issue-time joint evidence rule in augur.
- Not adopted (adoption is the user's decision); every option stays off by default.

## 12. Evidence as the joint predictive issued per origin: t63-issue (bnm-e1y)

§11 loss 2 named the bug: the averaging's evidence is not the issued forecast's prequential score. Of the three rules, the default scores rows at learn time with the updated model, `:issue` integrates each row's coefficient uncertainty separately (one count per row), and `:sequential` scores at learn time. bnm-e1y (related bnm-3be, bnm-56c.2) builds the rule the evidence calls for.

### Design

- **augur v0.24.0** (cd956a1), `:evidence-at :issue-joint`. Each model is scored by the joint predictive it issued for an origin's rows. The coefficients are N(m, C) as they stood at issue, with C discounted as `step` does. Each shared effect is integrated as issued (the frailty's `[t mean var]`, with `:load` under a slope). All of it is integrated over the origin's rows together, so one origin is one draw of the coefficients and of the shock, and only information available at issue is used.
- **`d/joint-score`.** This is Laplace's approximation in the coefficients and the shared state together. With θ ~ N(μ, S), S = diag(C, V) = LL' and θ = μ + Lv, damped Newton maximizes Σ log p(y_i | η_i) - v'v/2. The log marginal is that maximum less ½ log|I + Z'WZ|, where Z = XL. Each step costs a p × p system (p = coefficients plus shared dimensions) whatever the number of rows. A singular S (a variance of 0) is factored, not inverted.
- **Mixture rows.** A row issued over a mixture of offsets carries `:q 0` and the mixture's log odds. Its mean is set so that, under x'Cx, it scores the issued probability (MacKay's probit factor). A row issued at x'm + o is scored at f exactly.
- **What is stored.** The lattice keeps the averaged models' state at issue on each pending row (`:ist`, one value per origin, so no copy is made) beside `:lp`. Rows are grouped per origin by that state.
- **Stack and synth.** These rules read rows one at a time, so they read `:issued` as under `:issue`.
- **Cost.** The runs took 45.6 min, against 2 h 40 min for t60 (the machine was shared with other jobs in both cases).

**Exactness.** Laplace was checked against brute-force quadrature: 41 points per dimension over the coefficients and the shock, three dimensions. With a prior variance about 1-2, Laplace is 0.017 (1 row), 0.025 (4 rows) and 0.020 (12 rows) nats below quadrature for the whole origin. A Poisson case agrees to 0.001, and a two-dimensional shared state with loads [1 e] to 0.024. At 15 times the variance, the error on 12 rows is 0.087. The error is per origin, not per row, and grows slowly with the prior's width. A hybrid (exact one-row marginals plus Laplace's dependence term) was tried and is worse (up to 0.9 nats).

### Tests

- **augur** (5 new; suite 196 tests green):
  - Laplace against quadrature (tolerance 0.03 nats, 0.1 at a 15 times wider prior), with the discount as `step`'s;
  - nothing uncertain gives the rows' likelihood exactly (1e-12);
  - a mixture row scores what was issued;
  - per row (`:issue`) the unsure model's gap grows with the rows, while jointly it stays bounded (|gap| < 5 at 1000 rows) and a shock is counted once;
  - two origins learned in one period are each scored under their own state and summed;
  - the hindsight stream of `evidence-at-issue-does-not-reward-hindsight`: base > 0.99 jointly, while `:sequential` gives B > 0.9.
- **lattice** (test/bnm/lattice_test.clj, bnm-e1y-*):
  - **The best issued forecaster gains the weight.** 150 places, a common shock z_y, a leading gauge z_y + noise with an unsure coefficient (variance 16), and a hindsight competitor. Under the default, `:issue` and `:sequential` the leading model's weight never exceeds 0.1, with `:sequential` handing the weight to the hindsight model. Under `:issue-joint` it averages over 0.6 in the second half and is 0.79 at the end.
  - **A model that fits only in hindsight gains no weight.** Its sign flips every era, and the node discounts at 0.95. Its weight is ≥ 0.96 under `:sequential` and < 0.05 under `:issue-joint` from the twelfth origin on.
  - **Evidence bounded per origin.** In the exposure world of §9 with the loading shared, the mean |per-origin gap| at 150 places is no more than 1.5 times that at 30, is under 0.2 of the scalar frailty's, and its maximum is under 5 nats. The weight stays below the frailty-alone lock (> 0.99).
  - Pending rows keep their state, and the option off gives the same forecasts until a row is learned.
  - A `:forget-half-life` test (below).
- **Option off.** Full bnm suite green on augur v0.24.0 (1360 tests, at 32acf39 with the fix-2sf-fallout merge). 2026-Q4-r1 reproduces byte for byte (data/runs/logs/repro-r1-issue-joint.log, forecasts sha bdb361c8…, as repro-r1-fixes).

`:forget-half-life h` (d38328b) sets each node's forgetting to 0.5^(per/12h). `:forget 0.995` is per period, so it is 138 years at the year node, 35 at the quarters and 690 at the half-decade.

### Runs

- t63-issue: t62-fixes plus `:evidence-at :issue-joint`.
- t63-issue-f: t63-issue plus `:forget-half-life 10`. h = 10 years was chosen and written into data/runs/t63-issue-f/README.txt before either run started. The record's crisis waves last about a decade, so the effective window is about 15 years at every scale, and a first-decade deficit halves each decade.

Both ran at d38328b with augur v0.24.0 (commands in data/runs/t63-*/README.txt). Logs: data/runs/logs/score-t63-*.txt (folds, world, calibration, outliers, up-weights, kind-folds). The traces below come from replays of food, market crashes and inflation crises with t63-issue's options, dumping each December's per-model evidence at the year country node (session scratchpad, replay63.clj). The replays' mean issued p matches the run's to 0.002; the run's means include rows that have no outcome.

Year nodes, Brier (+ = better, 90% intervals):

| fold | t63-issue vs t53-noclim | vs t57-frailty | vs t62-fixes | t63-issue-f vs t53-noclim | vs t62-fixes | vs t63-issue |
|---|---|---|---|---|---|---|
| 2003-06 | +0.0024 [-0.0018, +0.0060] | -0.0001 [-0.0021, +0.0020] | -0.0007 [-0.0021, +0.0007] | +0.0026 [-0.0003, +0.0054] | -0.0005 [-0.0014, +0.0003] | +0.0002 [-0.0010, +0.0014] |
| 2007-09 | -0.0065 [-0.0088, -0.0024] LOSS | -0.0034 [-0.0050, +0.0003] | -0.0055 [-0.0084, +0.0012] | -0.0030 [-0.0048, -0.0007] LOSS | -0.0020 [-0.0040, +0.0018] | +0.0036 [+0.0001, +0.0051] |
| 2010-12 | -0.0009 [-0.0030, +0.0013] | +0.0013 [-0.0025, +0.0051] | +0.0006 [-0.0035, +0.0051] | +0.0001 [-0.0024, +0.0022] | +0.0016 [-0.0027, +0.0060] | +0.0009 [-0.0004, +0.0020] |
| 2013-19 | +0.0054 [+0.0022, +0.0085] | +0.0010 [-0.0002, +0.0022] | -0.0007 [-0.0018, +0.0005] | +0.0033 [+0.0011, +0.0054] | -0.0028 [-0.0045, -0.0008] LOSS | -0.0021 [-0.0034, -0.0007] LOSS |
| 2020-22 | +0.0042 [+0.0010, +0.0076] | +0.0002 [-0.0008, +0.0011] | +0.0012 [-0.0004, +0.0032] | +0.0044 [+0.0012, +0.0078] | +0.0014 [-0.0002, +0.0036] | +0.0002 [-0.0002, +0.0008] |
| pooled | +0.0024 [+0.0005, +0.0048] GAIN | +0.0002 [-0.0008, +0.0012] | -0.0006 [-0.0018, +0.0006] | +0.0022 [+0.0007, +0.0038] GAIN | -0.0009 [-0.0021, +0.0005] | -0.0003 [-0.0012, +0.0007] |

- **Log pooled.** t63-issue: +0.0057 [-0.0003, +0.0121] against t53-noclim, -0.0014 against t57-frailty, and -0.0035 [-0.0062, -0.0003] against t62-fixes (a loss). t63-issue-f: +0.0059 [+0.0010, +0.0108] against t53-noclim and -0.0034 [-0.0064, -0.0002] against t62-fixes.
- **Per kind, t63-issue against t62-fixes (bnm.world-score).**
  - Gains: sovereign defaults +0.0014 [+0.0005, +0.0025] and protest surges +0.0023 [+0.0006, +0.0044].
  - Losses: inflation crises -0.0037 [-0.0083, -0.0009] and currency crises -0.0010 [-0.0019, -0.0003].
  - Within their intervals: market crashes -0.0077 [-0.0184, +0.0024] and food -0.0007 [-0.0102, +0.0099].
- **Per kind against t53-noclim.** Market crashes +0.036 [+0.009, +0.065] and growth +0.0024 [+0.0005, +0.0044]; inflation -0.0041 [-0.0087, -0.0013].
- **t63-issue-f against t62-fixes.** Market crashes -0.014 [-0.024, -0.005], a loss.

Calibration, country year, mean p (rate) and Brier, t57-frailty → t62-fixes → t62-fixes-seq → t63-issue → t63-issue-f:

| kind | span | rate | mean p | Brier |
|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.286 → 0.259 → 0.198 → 0.266 → 0.282 | 0.1701 → 0.1561 → 0.1363 → 0.1638 → 0.1703 |
| market crashes | 1980-2002 | 0.312 | 0.234 → 0.226 → 0.215 → 0.217 → 0.220 | 0.2305 → 0.2309 → 0.2313 → 0.2358 → 0.2353 |
| market crashes | 2010-12 | 0.080 | 0.276 → 0.206 → 0.139 → 0.297 → 0.233 | 0.1174 → 0.1001 → 0.0902 → 0.1220 → 0.1079 |
| market crashes | 2006-07 | 0.957 | 0.260 → 0.280 → 0.288 → 0.233 → 0.259 | 0.5282 → 0.5063 → 0.4938 → 0.5655 → 0.5300 |
| food | 2003-22 | 0.154 | 0.247 → 0.250 → 0.286 → 0.256 → 0.257 | 0.1580 → 0.1592 → 0.1725 → 0.1599 → 0.1607 |
| food | 2010-12 | 0.060 | 0.296 → 0.323 → 0.394 → 0.238 → 0.241 | 0.1158 → 0.1238 → 0.1746 → 0.1001 → 0.1017 |
| food | 2020-22 | 0.415 | 0.114 → 0.119 → 0.139 → 0.128 → 0.127 | 0.3369 → 0.3365 → 0.3228 → 0.3302 → 0.3308 |
| inflation crises | 2003-22 | 0.012 | 0.021 → 0.019 → – → 0.030 → 0.021 | 0.0122 → 0.0123 → – → 0.0161 → 0.0124 |
| sovereign defaults | 2003-22 | 0.031 | 0.049 → 0.050 → – → 0.048 → 0.051 | 0.0324 → 0.0326 → – → 0.0311 → 0.0313 |
| protest surges | 2003-22 | 0.030 | 0.050 → 0.047 → – → 0.040 → 0.041 | 0.0277 → 0.0284 → – → 0.0262 → 0.0265 |
| violence surges | 2003-22 | 0.080 | 0.107 → 0.107 → – → 0.097 → 0.096 | 0.0710 → 0.0717 → – → 0.0701 → 0.0698 |

Growth, banking, coups, war, currency and fiscal are within 0.003 of t62-fixes in mean p (score-t63-calibration.txt).

**The `:up` weight at the year country node** (Decembers; score-t63-up-weights.txt):

| kind | years | t62-fixes | t63-issue | t63-issue-f |
|---|---|---|---|---|
| market crashes | 1968-89 | ≤ 0.003 | ≤ 0.009 | ≤ 0.028 |
| market crashes | 1990-95 | 0.000-0.002 | 0.21-0.32 | 0.09-0.26 |
| market crashes | 2000-22 | ≤ 0.001 | 0.009-0.071 | 0.001-0.040 |
| food | 2010-22 | 0.000 | 0.000 | 0.000 |

The leaders under t63-issue:
- market crashes: `:down` 0.49-0.99 (1980-2009, 2020-22) and fiscal 0.40-0.79 (2010-19);
- food: economy 0.98-0.99 (2010-11), then all-inputs 0.80-1.00 (2012-22);
- inflation crises: exposure 0.91-1.00 (2000-09).

**The outlier episodes** (mean p at the December origin, rate in brackets):

| episode | origin | t57-frailty | t62-fixes | t62-fixes-seq | t63-issue | t63-issue-f |
|---|---|---|---|---|---|---|
| food 2007-08 | 2006 | 0.500 (0.418) | 0.500 | 0.500 | 0.500 | 0.500 |
| | 2007 | 0.238 (0.313) | 0.213 | 0.213 | 0.213 | 0.213 |
| food 2020-22 | 2020 | 0.109 (0.493) | 0.109 | 0.140 | 0.122 | 0.122 |
| | 2021 | 0.109 (0.500) | 0.109 | 0.143 | 0.131 | 0.129 |
| market crashes 2008 | 2006 | 0.269 (0.957) | 0.269 | 0.304 | 0.240 | 0.261 |
| | 2007 | 0.252 (0.957) | 0.291 | 0.273 | 0.225 | 0.256 |
| growth collapses | 2007 | 0.071 (0.235) | 0.070 | 0.073 | 0.072 | 0.068 |
| | 2008 | 0.068 (0.202) | 0.067 | 0.069 | 0.069 | 0.067 |
| | 2019 | 0.078 (0.554) | 0.078 | 0.078 | 0.078 | 0.079 |

The new rule reaches none of the episodes, and it moves the 2008 crashes further down.

### Why: the losses traced (PLAN principle 9)

Cumulative evidence relative to the base model in nats, from the replays (`:issue-joint` against `:issue`, the same rows, as issued):

| kind | year | `:up` joint / per row | all-inputs joint / per row | best model, joint |
|---|---|---|---|---|
| market crashes | 1990 | +2.0 / -1.3 | -13.6 / -20.6 | history +3 |
| | 2006 | -1.0 / -5.1 | -26.4 / -35.3 | `:down` +3 |
| | 2022 | -1.2 / -3.4 | -27.7 / -41.5 | `:down` +4 |
| food | 2012 | +4.3 / -40.1 | +16.5 / -26.9 | all +17 |
| | 2016 | +11.3 / -48.4 | +35.3 / -93.5 | all +35 |
| | 2022 | +29.1 / -27.6 | +58.3 / -66.7 | all +58 |

1. **§11's issued ranking was the per-row count, not the issue time.**
   - Food: per row, all-inputs is the worst issued model (-67 to -94 nats by 2016-22, §11's 207 behind on its own accounting). Scored jointly it is the best (+58 by 2022), ahead of economy (+43) and `:up` (+29). Its many unsure coefficients on place-varying inputs were paid once per place; counted once per origin, its issued forecast is the best one. So its weight of 1.00 from 2015 is what the issued evidence says, and food 2010-12 improves (0.1238 → 0.1001), while 2020-22 is mixed (0.3365 → 0.3302).
   - Market crashes: jointly, `:up` stays within ±2.5 nats of the base from 1968 to 2022. Its largest single-origin moves are -2.4 (1968), -1.4 (1992) and +1.2 (1976). It leads all-inputs by 25-27 nats, as §11 found, but it does not lead `:down` (+3 to +4) or fiscal (+2 to +4). §11's 2006-07 lead of 8-10 nats per origin, and its 35 over `:down`, came from rows scored one by one. The crash was a common shift. Jointly, with the origin's shock integrated at its issued variance, predicting that shift is worth about one observation, not one per place: `:up` gains 0.0 nats over the base at 2006-07. So `:up` is not locked out by the rule. As issued and counted once per origin it is not the better forecaster, and the 1990-95 weights of 0.21-0.32 are where it was.
   - Per-origin evidence is now bounded. The largest single-origin gains over base are 10.8 nats (food all-inputs, 2014, about 150 places), 6.7 (market crashes `:down`, 1976) and 3.7 (inflation exposure, 2002).
2. **The remaining lock is a regional common shock (2007-09: market crashes -0.055 against t62-fixes, inflation crises -0.020; ×1000 Brier per kind in score-t63-kind-folds.txt).**
   - Market crashes: `:down` (the region's and subregion's states) holds 0.62-0.82 from 2000 to 2008 and issues 0.240 and 0.225 at Dec 2006-07 (rate 0.957), against t62-fixes' all-inputs at 0.269-0.291. `:down` earned its weight jointly at 1976 (+6.7) and 1980 (+3.3).
   - Inflation crises: exposure (`:down` times openness) took the weight in 1995-2002, gaining +2.5, +2.8 and +3.7 at 1997, 1998 and 2002, and holds 0.99-1.00 from 2003 to 2008. It issues 0.07-0.08 at 2006-08 against a rate of 0.006, with a largest single-country p of 0.78. That is the inflation loss (Brier 0.0123 → 0.0161 over 2003-22).
   - The joint marginal integrates a model's coefficient uncertainty over the origin's rows. A model whose unsure coefficients sit on region-common inputs therefore predicts within-region clustering, and is rightly rewarded for it when the scalar frailty (one z per node) leaves the regional shock out. This is §9's mechanism, now with no hindsight: it is a property of the issued joint predictive, not a code bug. Under `:forget 0.995` a few such origins in the 1970s-90s hold the weight for decades.
   - The fix §9 named stands: a regional shared state inside the country node (z + z_region; branch `regional`, t61). With it, these models would be set against the shock they proxy.
3. **Forgetting (t63-issue-f).** The half-life of 10 years undoes part of both locks:
   - 2007-09 improves +0.0036 [+0.0001, +0.0051] on t63-issue: inflation +19.8 and market crashes +29.9 (×1000). Exposure falls off by 2009 and issues 0.034-0.044.
   - 2013-19 loses -0.0021 [-0.0034, -0.0007], mostly market crashes (-27.7 ×1000). The year node's weight follows the last decade's evidence back to the world3 model (0.74-0.85 in 2010-15) and issues 0.28 in 2003-22 against t63-issue's 0.27 and t62-fixes' 0.26.
   - It does not reopen food's `:up` (0.000), because all-inputs is also ahead on the last decade's joint evidence.
   - A shorter memory trades the locks' path dependence for following the recent evidence; pooled it is no better (-0.0003 [-0.0012, +0.0007] on t63-issue).

Bugs: none found in the runs. The rule was built and tested; the replays reproduce the run.

Verdict:
- **The rule is correct and exact to Laplace's error.** That error is a few hundredths of a nat per origin, checked against quadrature. The tests show it does what the evidence called for: no hindsight, one count per origin, and the best issued forecaster gains the weight where the current rules keep it out.
- **On the world.** t63-issue gains pooled against t53-noclim (+0.0024 [+0.0005, +0.0048]) and ties t57-frailty (+0.0002) and t62-fixes (-0.0006 [-0.0018, +0.0006]; log -0.0035, a loss). It fails the fold rule in 2007-09 against t53-noclim (-0.0065). t63-issue-f fails 2007-09 against t53-noclim (-0.0030) and 2013-19 against t62-fixes (-0.0028).
- **`:up` does not reopen,** and the reason has changed. Under the issued joint score, `:up` is not the better forecaster of market crashes or food. §11's issued ranking came from the per-row count. The lock that remains (`:down` at market crashes, exposure at inflation) is the regional common shock the scalar frailty omits.
- **Next.** The regional shared state, with `:issue-joint` as its evidence.
- **Adoption.** Neither is adopted (the user's decision); both options stay off by default. augur v0.24.0 is pinned in deps.edn (32acf39).

## 13. Leading gauges with priors from theory: t65-gauges (bnm-56c.36)

LEADING.md 5 found the leading information in the data but unread (stress, the BAA spread) or unlearnable (§12: a common shock is about one observation per origin, so a slope for it cannot be learned from the few episodes on record). This step wires unrevised market gauges at the market-crash and growth-collapse year and quarter-lead nodes, and world food times food imports at the food year node. Their coefficient priors come from theory and were fixed before any run (docs/LEADING.md "Priors", committed in 0532af3 before the code). The stress indexes are left out: they are revised every week (STLFSI4 changed at 1689 of 1711 past weeks in four weeks; v1 against v4 correlates 0.70). The BAA spread, the curve and the VIX are unchanged at every day.

Code 7dc9281: `--leading true` (bnm.named/leading, bnm.lattice-political/leading-sets) and `--leading-priors true` (bnm.lattice `:named-prior` with `:gauge-names`, a coefficient prior by input name). Both are off by default.

Tests:
- bnm-56c-36-leading-gauges-are-read-at-their-availability-date: a December value published in January is not read at the December origin, and a stopped quote series goes stale.
- bnm-56c-36-leading-gauges-and-priors-by-kind.
- bnm-56c-36-a-named-prior-holds-at-the-first-data: under `:discount-from :data` the node meets its first rows at the declared mean 0.5 and variance 0.0625, in the group's and the all-inputs model. With the option off, the forecasts are identical. Unknown names, a width mismatch and a variance of 0 are refused.

The suite is green (1363 tests, data/runs/logs/suite-t65.log), and 2026-Q4-r1 reproduces byte for byte (data/runs/logs/repro-r1-leading.log, sha bdb361c8…).

### Runs

- **t65-gauges:** t63-issue (t62-fixes plus `:evidence-at :issue-joint`) plus `--leading true --leading-priors true`.
- **t65-gauges-flat:** the same gauges at the node's default priors.

Commands are in data/runs/t65-*/README.txt. Logs: data/runs/logs/score-t65-*.txt (folds, world, calibration, outliers, weights, kind-folds, flat-kind-folds). The other ten kinds are identical to t63-issue at every row, so the gauges reach no kind they were not wired to.

Year nodes, Brier (+ = better, 90% intervals):

| fold | t65-gauges vs t53-noclim | vs t62-fixes | vs t63-issue | vs t65-gauges-flat | flat vs t53-noclim | flat vs t63-issue |
|---|---|---|---|---|---|---|
| 2003-06 | +0.0039 [+0.0011, +0.0064] | +0.0008 [-0.0007, +0.0021] | +0.0015 [+0.0001, +0.0029] | +0.0020 [+0.0005, +0.0036] | +0.0019 [-0.0024, +0.0055] | -0.0005 [-0.0009, -0.0000] LOSS |
| 2007-09 | +0.0031 [-0.0040, +0.0073] | +0.0041 [-0.0015, +0.0073] | +0.0096 [-0.0017, +0.0133] | +0.0093 [-0.0013, +0.0129] | -0.0062 [-0.0085, -0.0025] LOSS | +0.0003 [-0.0003, +0.0007] |
| 2010-12 | +0.0009 [-0.0015, +0.0031] | +0.0024 [+0.0006, +0.0043] | +0.0017 [-0.0017, +0.0052] | +0.0003 [-0.0004, +0.0010] | +0.0006 [-0.0012, +0.0022] | +0.0015 [-0.0013, +0.0043] |
| 2013-19 | +0.0069 [+0.0020, +0.0114] | +0.0009 [-0.0016, +0.0028] | +0.0015 [-0.0012, +0.0037] | +0.0005 [+0.0003, +0.0007] | +0.0064 [+0.0014, +0.0109] | +0.0011 [-0.0018, +0.0032] |
| 2020-22 | +0.0016 [-0.0033, +0.0057] | -0.0014 [-0.0052, +0.0020] | -0.0026 [-0.0055, +0.0001] | +0.0002 [+0.0000, +0.0005] | +0.0013 [-0.0035, +0.0055] | -0.0028 [-0.0059, -0.0002] LOSS |
| pooled | +0.0040 [+0.0018, +0.0065] GAIN | +0.0009 [-0.0005, +0.0022] | +0.0015 [-0.0001, +0.0036] | +0.0015 [+0.0004, +0.0029] GAIN | +0.0025 [+0.0000, +0.0053] GAIN | +0.0001 [-0.0012, +0.0013] |

- **t65-gauges loses no fold against any bench.** It removes t63-issue's 2007-09 loss against t53-noclim (-0.0065 → +0.0031). Log pooled: +0.0096 [+0.0034, +0.0157] GAIN against t53-noclim and +0.0033 [+0.0007, +0.0070] GAIN against flat. The 2020-22 log score loses against t62-fixes (-0.0131 [-0.0207, -0.0040]) and t63-issue (-0.0097 [-0.0181, -0.0017]): see loss 1.
- **t65-gauges-flat** fails 2007-09 against t53-noclim, as t63-issue did, and fails 2003-06 and 2020-22 against t63-issue. The gauges without the priors do not pay.
- **Per kind (bnm.world-score).**
  - Against t63-issue: food +0.0078 [+0.0025, +0.0140] GAIN, market crashes +0.0118 [-0.0071, +0.0330], growth +0.00007 [+0.00001, +0.00018] GAIN.
  - Against flat: market crashes +0.0184 [+0.0045, +0.0359] GAIN, growth +0.00007 GAIN, food +0.0005 [-0.0014, +0.0024].
  - Against t53-noclim: market crashes +0.048 [+0.020, +0.083] GAIN, growth +0.0025 [+0.0006, +0.0045] GAIN.

Calibration, country year, mean p and Brier, t62-fixes → t63-issue → t65-gauges-flat → t65-gauges:

| kind | span | rate | mean p | Brier |
|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.259 → 0.266 → 0.277 → 0.277 | 0.1561 → 0.1638 → 0.1703 → **0.1520** |
| market crashes | 1980-2002 | 0.312 | 0.226 → 0.217 → 0.224 → 0.256 | 0.2309 → 0.2358 → 0.2340 → 0.2335 |
| market crashes | 2006-07 | 0.957 | 0.280 → 0.233 → 0.231 → 0.318 | 0.5063 → 0.5655 → 0.5687 → 0.4505 |
| market crashes | 2010-12 | 0.080 | 0.206 → 0.297 → 0.247 → 0.245 | 0.1001 → 0.1220 → 0.1123 → 0.1128 |
| food | 2003-22 | 0.154 | 0.250 → 0.256 → 0.238 → 0.237 | 0.1592 → 0.1599 → 0.1526 → **0.1521** |
| food | 2010-12 | 0.060 | 0.323 → 0.238 → 0.222 → 0.214 | 0.1238 → 0.1001 → 0.0906 → 0.0868 |
| food | 2020-22 | 0.415 | 0.119 → 0.128 → 0.142 → 0.142 | 0.3365 → 0.3302 → 0.3249 → 0.3275 |
| growth collapses | 2003-22 | 0.106 | 0.083 → 0.084 → 0.084 → 0.084 | 0.0966 → 0.0966 → 0.0966 → 0.0965 |
| growth collapses | 2007-08 | 0.219 | 0.069 → 0.071 → 0.071 → 0.073 | 0.1934 → 0.1921 → 0.1921 → 0.1914 |

Market crashes in 2003-22 (0.1520) are still above LEADING.md 2's stress ceiling (0.1416-0.1456, from a revised series) and t62-fixes-seq (0.1363), and below every other run. Food 2003-22 (0.1521) is below t53-noclim (0.1560) for the first time since t53.

**The outlier episodes** (mean p at the December origin, rate in brackets):

| episode | origin | t62-fixes | t63-issue | t65-gauges-flat | t65-gauges |
|---|---|---|---|---|---|
| food 2007-08 | 2006 (0.418) | 0.500 | 0.500 | 0.500 | 0.550 |
| | 2007 (0.313) | 0.213 | 0.213 | 0.217 | 0.236 |
| food 2020-22 | 2020 (0.493) | 0.109 | 0.122 | 0.126 | 0.121 |
| | 2021 (0.500) | 0.109 | 0.131 | 0.154 | 0.154 |
| market crashes 2008 | 2006 (0.957) | 0.269 | 0.240 | 0.232 | 0.287 |
| | 2007 (0.957) | 0.291 | 0.225 | 0.229 | 0.348 |
| growth collapses | 2007 (0.235) | 0.070 | 0.072 | 0.072 | 0.073 |
| | 2008 (0.202) | 0.067 | 0.069 | 0.069 | 0.073 |
| | 2019 (0.554) | 0.078 | 0.078 | 0.078 | 0.078 |

The priors move market crashes 2006-07 for the first time: 0.348 at Dec 2007 (t63-issue 0.225). The gauges' z values at the US are BAA12 +2.1, VIX12 +2.5 and curve -0.4 at Dec 2007, and curve -1.8 at Dec 2006. Food 2006 moves the wrong way: the node had no resolved outcome, so it issued an even 0.5 lifted by the prior (food12 x imports +0.8 sd). Food 2007 moves the right way (0.213 → 0.236). Growth and food 2020-21 hardly move.

**The `:leading` group's weight at the country node** (Decembers; score-t65-weights.txt):

| kind, scale | years | t65-gauges-flat | t65-gauges |
|---|---|---|---|
| market crashes, year | 1990-95 | 0.08-0.21 | 0.37-0.70 |
| | 2000-05 | ≤ 0.008 (`:down` 0.83-0.92) | 0.06-0.10 (`:down` 0.54-0.88) |
| | 2006-08 | 0.02-0.04 | **0.40-0.57 (the leader)** |
| | 2010-22 | 0.80-0.95 | 0.99-1.00 |
| market crashes, quarter-lead | 1995-2022 | ≤ 0.01 (assets, then all-inputs) | ≤ 0.01 (0.21 in 2010) |
| growth, year | 2003-22 | 0.000 | ≤ 0.04 (economy 0.43-1.00) |
| growth, quarter-lead | 2010-22 | 0.06-0.78 | 0.54-0.97 |
| food, year | 2005-08 | 0.05-0.08 (even, uninformed) | 0.05-0.08 |
| | 2010-22 | 0.000 (the all-inputs model, which reads them, holds 1.00 from 2015) | 0.000 (the same) |

### Why: the losses traced (PLAN principle 9)

Each loss was traced to its origins and gauges from forecasts.tsv, weights.tsv and the gauges' z against their own quarterly history up to the origin (bnm.named/leading on data/slow). No replay with coefficient dumps was made, so the coefficient values below are the priors, not the learned ones.

1. **Market crashes 2020-22, -0.036 (×1000: -36.5) against t63-issue, in both runs (flat -42.0); the 2020-22 log loss.** The year node's weight sits at 0.99-1.00 on the `:leading` model from 2010.
   - Dec 2020 issues 0.34 against a rate of 0.08: VIX12 +1.3 sd after the COVID crash, curve -0.7.
   - Dec 2022 issues 0.36 against a rate of 0.05: curve -2.2 sd, the deepest inversion since 1982, then no US recession in 2023-24.
   - Dec 2018 (2013-19 fold) issues 0.49 against 0.21: VIX12 +2.7 after the Q4 2018 fall.

   The sign of every term is theory's. The flat run learns the same signs from 2007-09 and loses more. The verdict is **approach, partly**: the curve's 2022-24 miss is that gauge's known out-of-sample failure, not a bug. VIX12 is the term LEADING.md's prior already called partly concurrent. A VIX rise after a crash is the crash already counted (the onset falls before the window), so it reads as risk to come. Under the joint evidence one origin is about one observation, so three wrong origins in 2018-22 cannot undo the weight the 2006-08 origins earned. The fix theory suggests is to read VIX12 only at growth collapses, or with the crash's own state as a control. It is a change to the gauges, recorded here, not made: it would be chosen after seeing the scores.
2. **t65-gauges-flat 2007-09 against t53-noclim (-0.0062): the §12 lock, unchanged.** Without priors, the leading model's coefficients sit at mean 0 (variance 1, `:shrink` 0.1 in the all-inputs model) when the episode arrives. Its weight is 0.02-0.04, and `:down` keeps the node (0.71-0.43) as in t63-issue, issuing 0.23 at Dec 2006-07. This is LEADING.md 5's "cannot be learned from the few episodes", confirmed. With the priors the same gauges carry 0.40-0.57 and the fold turns to +0.0031.
3. **Food 2003-06 (-1.2 ×1000, t65-gauges against t63-issue and flat).** This is the Dec 2006 origin only. The node had no resolved outcome, so its models carried even weights and the leading model's prior lifted the even 0.5 to 0.55 (rate 0.418). The prior acted as designed. The 0.5 itself is the uninformed base (`--clim-prior` off, bnm-56c.26), a sequencing matter, not this step's.
4. **Inflation crises 2007-09 (-20.7 against t53-noclim):** inherited from t63-issue unchanged (§12 loss 2, the regional lock). The gauges do not touch the kind.

Bugs found: one, in this step's own test (two runs sharing one world's noise generator, fixed before the commit). None in the runs.

Verdict:
- **The gauges and their priors do what LEADING.md 5 asked.** The financial leading information now enters at the 2008 episode (market crashes Dec 2007 0.225 → 0.348) and holds the year node's weight from it.
- **On the world.** t65-gauges gains pooled against t53-noclim (+0.0040 [+0.0018, +0.0065]) and loses no fold against t53-noclim, t62-fixes or t63-issue. It mends t63-issue's 2007-09 fold failure. It gains market crashes against flat (+0.018) and food against t63-issue (+0.008).
- **The priors, not the gauges alone, make the gain.** t65-gauges against flat: +0.0015 [+0.0004, +0.0029] GAIN, 2007-09 +0.0093. Flat ties t63-issue pooled (+0.0001) and fails three folds.
- **Not reached:** growth 2007-08 (the year leading model never takes weight: economy holds 0.43-1.00) and food and growth 2019-21.
- **Cost:** false alarms at market crashes after 2018 (loss 1).
- **Adoption** is the user's decision. Both options stay off by default.

## 14. Alerts at the 4:1 cost ratio

The user's decision of 13 November 2026: a missed crisis costs 4 false alarms. Forecasts stay calibrated and acceptance stays on proper scores and the fold rule. The asymmetry goes into an alert layer on top: **alert when p >= C_false / (C_false + C_miss) = 1/5 = 0.2.** Every candidate's report adds the alert table (PLAN principle 10).

Tool: `jolt -A:tools -M -m bnm.alerts --run RUN [--bench BASE] [--threshold 0.2] [--miss-cost 4] [--from 2003 --to 2022] [--episodes ratio|decile] [--ratio 2] [--min-rows 10]`. It reads year country nodes, December origins and resolved rows only, as bnm.paired/read-lattice does. It reports:
- hit rate (onset rows alerted), false-alarm rate (quiet rows alerted), alert share;
- cost per row, (misses x 4 + false alarms) / rows. Beside it is the cost of the better constant rule, never or always alerting, given the span's rate (min(4r, 1 - r)): the no-skill reference;
- with `--bench`, the bench's cost minus the run's (+ = run cheaper), with a 90% interval that resamples countries and years (augur.score/paired-loss-2way with the cost as the loss, as bnm.world-score does), per kind and pooled with kinds weighed equally;
- episodes and the fixed outliers of 10-13, below, and the world node's counts.

**Episodes.** An episode is a December origin with at least 10 resolved rows whose realized country rate is at least 2x the kind's 2003-2022 pooled rate. It is caught when the mean issued p reaches 0.2. The ratio rule is the default because the top-decile rule (`--episodes decile`) always flags two of the twenty origins per kind, whether or not the kind had a crisis-shaped year. Twice the rate is a property of the year itself. For rare kinds the rule flags years such as inflation 2020 (rate 0.025) whose rate is below 0.2. A calibrated forecast at the origin would not alert on those, so the table also counts the episodes whose rate itself reaches 0.2 (`rate>=t`).

Tests: test/bnm/alerts_test.clj covers the threshold edge (p = 0.2 alerts), hit, miss and false-alarm counts, the constant rule, empty kinds and origins, the episode rules, the sign of the cost difference both ways, and the reader.

Logs: data/runs/logs/alerts-t65-gauges-vs-t53-noclim.txt, alerts-t65-gauges-vs-t62-fixes.txt, alerts-t63-issue.txt, alerts-t53-noclim.txt.

**t65-gauges against t53-noclim**, country year nodes, 2003-2022:

| kind | rows | onsets | hit | false alarm | alerts | constant | cost | t53 cost | Δcost (+ = t65 cheaper) |
|---|---|---|---|---|---|---|---|---|---|
| banking crises | 2558 | 82 | 0.0% | 0.2% | 0.2% | 0.128 | 0.131 | 0.129 | -0.0016 [-0.0054, +0.0000] |
| conflict onsets | 3308 | 271 | 41.0% | 6.5% | 9.3% | 0.328 | 0.253 | 0.256 | +0.0030 [-0.0127, +0.0213] |
| coups | 3481 | 157 | 7.6% | 3.6% | 3.8% | 0.180 | 0.201 | 0.198 | -0.0032 [-0.0133, +0.0055] |
| currency crises | 3262 | 117 | 0.0% | 0.0% | 0.0% | 0.143 | 0.143 | 0.147 | +0.0040 [-0.0037, +0.0103] |
| fiscal consolidations | 2944 | 801 | 89.8% | 84.6% | 86.0% | 0.728 | 0.728 | 0.716 | -0.0119 [-0.0464, +0.0258] |
| food price shocks | 2913 | 450 | 61.8% | 49.2% | 51.2% | 0.618 | 0.653 | 0.639 | -0.0137 [-0.1159, +0.0895] |
| growth collapses | 3205 | 339 | 0.0% | 0.1% | 0.1% | 0.423 | 0.424 | 0.432 | +0.0084 [+0.0003, +0.0190] GAIN |
| inflation crises | 3197 | 39 | 0.0% | 1.3% | 1.3% | 0.049 | 0.062 | 0.049 | -0.0125 [-0.0258, -0.0038] LOSS |
| market crashes | 798 | 142 | 91.5% | 80.8% | 82.7% | 0.712 | 0.724 | 0.831 | +0.1065 [-0.0533, +0.3051] |
| protest surges | 3411 | 104 | 41.3% | 2.6% | 3.8% | 0.122 | 0.097 | 0.114 | +0.0173 [+0.0029, +0.0327] GAIN |
| sovereign defaults | 3217 | 101 | 0.0% | 0.3% | 0.3% | 0.126 | 0.128 | 0.127 | -0.0016 [-0.0075, +0.0034] |
| violence surges | 3387 | 271 | 26.6% | 7.4% | 8.9% | 0.320 | 0.303 | 0.310 | +0.0068 [-0.0300, +0.0467] |
| war onsets | 3402 | 85 | 14.1% | 2.0% | 2.3% | 0.100 | 0.106 | 0.106 | +0.0000 [-0.0061, +0.0073] |
| pooled (kinds equally) | 39083 | 2959 | 28.7% | 18.4% | 19.2% | 0.306 | **0.304** | 0.312 | +0.0078 [-0.0073, +0.0256] |

Pooled cost per row (constant rule 0.306 in every run): t53-noclim 0.312, t63-issue 0.316, t65-gauges 0.304. Against t62-fixes, t65-gauges is +0.0135 [+0.0002, +0.0280] GAIN.

Episodes (the same 22 in every run, 11 with a rate of at least 0.2): t53-noclim catches 5, t62-fixes, t63-issue and t65-gauges 6 each. Fiscal consolidations 2019-20 and market crashes 2006-07 are caught by all four, and so is food 2006, where every run issues the uninformed 0.5. Food 2007 is caught from t62-fixes on. Missed by t65-gauges with a rate of at least 0.2: food 2020 and 2021 and growth collapses 2007, 2018 and 2019. Missed below 0.2: banking 2006-07 (rates 0.17, 0.15; p 0.025, 0.022), sovereign defaults 2018, 2019 and 2021, coups 2020, currency 2014, inflation 2020, protest 2004, and wars 2012 and 2021.

The fixed outliers (mean p at the December origin; caught at p >= 0.2):

| episode | origin (rate) | t53-noclim | t62-fixes | t63-issue | t65-gauges |
|---|---|---|---|---|---|
| food 2007-08 | 2006 (0.418) | 0.500 caught | 0.500 caught | 0.500 caught | 0.550 caught |
| | 2007 (0.313) | 0.102 missed | 0.213 caught | 0.213 caught | 0.236 caught |
| food 2020-22 | 2020 (0.493) | 0.053 missed | 0.109 missed | 0.122 missed | 0.121 missed |
| | 2021 (0.500) | 0.057 missed | 0.109 missed | 0.131 missed | 0.154 missed |
| market crashes 2008 | 2006 (0.957) | 0.281 caught | 0.269 caught | 0.240 caught | 0.287 caught |
| | 2007 (0.957) | 0.289 caught | 0.291 caught | 0.225 caught | 0.348 caught |
| growth collapses | 2007 (0.235) | 0.051 missed | 0.070 missed | 0.072 missed | 0.073 missed |
| | 2008 (0.202) | 0.061 missed | 0.067 missed | 0.069 missed | 0.073 missed |
| | 2019 (0.554) | 0.068 missed | 0.078 missed | 0.078 missed | 0.078 missed |

What the tables say:
- **At 4:1 the model barely beats the constant rule.** The pooled cost is 0.304 against 0.306. It beats the constant rule clearly only at conflict onsets, protest surges and violence surges, where it separates countries (hit rate 27-41% at false-alarm rates of 3-7%). At the common-shock kinds it is close to a constant rule. Market crashes and fiscal consolidations alert at 83-86% of rows, near "always". Growth collapses, banking, currency, sovereign and inflation crises almost never alert (hit rate 0%), so they cost what "never" costs.
- **Growth collapses are the costliest kind the model never alerts on** (0.424 per row, all of it misses). An onset rate of 0.106 is below 0.2 overall, but 2007, 2018 and 2019 run at 0.24-0.56 while the year node stays at 0.07-0.08. These are the outliers 10-13 already name.
- **Inflation crises lose against t53-noclim** (-0.0125 LOSS): 1.3% of rows alert against a rate of 1.2%. This is the regional lock of 12 (loss 2) seen through the alert layer.
- **Market crashes:** t65-gauges is cheaper than t53-noclim by 0.107 per row (not significant at 90%). The saving is in misses: it alerts at 91.5% of onsets against 74.6%, with false alarms about the same (80.8% against 79.1%). Both runs cost more than "always alert" (0.712).
- None of this changes acceptance, which stays on proper scores and the fold rule. The alert table is reported beside them.

## 15. The lookahead fix, the food node and the second food set: t66-fix, t66-food (bnm-a1e, bnm-11y, bnm-5rn)

Three changes, each behind an option that is off by default. The base of record (t65-gauges) and 2026-Q4-r1 are untouched: with every option off, food, market crashes and growth collapses come back byte for byte from t65-gauges' command at 7c3d4c1 and at 8513f61 (data/runs/logs/repro-t66-default.txt, repro-t66-default2.txt; the other kinds do not reach the changed code). The suite is green (1382 tests, data/runs/logs/suite-t66.log).

1. **The lookahead (bnm-a1e, `--real-lags true`).** slow-normalize dates every monthly FRED series at +1 month. ALFRED's vintages (data/raw/alfred) show that the IMF commodity prices, among them t65's food gauge, reach FRED at +2 in about one release in six, and four US series later still. The audit table is in docs/LEADING.md, "Lookahead audit". bnm.named/real-lags holds the real lags, and the GSCPI, OECD CLI, STLFSI and NFCI are refused without a named vintage. TDSP's dating was fixed in slow-normalize.
2. **The food year node (bnm-11y, `--lead-std history`).** Traced below. The node's standardizer counts rows, not origins, so a world gauge was standardized against two or three Decembers.
3. **The second food set (bnm-5rn, `--leading-food2 true`).** Fertilizer 12m, and FAO food, cereals and oils 6m. **They were chosen after inspecting 2020-21, so their 2020-22 scores are in-sample for that choice.** Their priors (+0.1 to +0.15, sd 0.25) come from the pass-through literature and were committed before the code (docs/LEADING.md, "The second food set", 50fff6c).

Runs (data/runs/t66-*/README.txt):
- **t66-fix** = t65-gauges + `--real-lags true`. It measures what the leak was worth.
- **t66-food** = t66-fix + `--lead-std history --leading-food2 true`, at 448322e.
- **t66-food-v1** is superseded: its first history standardizer began at 1990 and counted rows (loss 2 below).

### Tests

- bnm-a1e-imf-prices-are-not-read-before-their-publication: the leading food gauge does not read January's jump at February under real lags, and does at March. The base reads it at February.
- bnm-a1e-every-monthly-fred-series-has-an-audited-lag, bnm-a1e-the-world-food-gauge-at-its-true-publication, bnm-a1e-revised-series-are-not-read-without-a-vintage, bnm-a1e-real-lags-option.
- bnm-11y-leading-gauges-standardized-against-their-own-history, bnm-11y-the-history-counts-quarter-ends-not-rows-and-starts-with-the-data, bnm-11y-lead-std-option.
- bnm-5rn-food2-series-are-read-a-month-after-their-month, bnm-5rn-the-second-food-set-and-its-priors, bnm-5rn-leading-food2-option.

### Scores

Year nodes, Brier, 2003-22 (+ = better, 90% intervals; data/runs/logs/score-t66-*.txt):

| fold | t66-fix vs t65-gauges | t66-fix vs t53-noclim | t66-food vs t65-gauges | t66-food vs t66-fix | t66-food vs t53-noclim |
|---|---|---|---|---|---|
| 2003-06 | -0.0003 [-0.0011, +0.0003] | +0.0035 [+0.0012, +0.0061] | -0.0003 [-0.0024, +0.0026] | +0.0001 [-0.0024, +0.0026] | +0.0036 [-0.0012, +0.0083] |
| 2007-09 | +0.0001 [-0.0001, +0.0002] | +0.0032 [-0.0041, +0.0075] | -0.0070 [-0.0098, +0.0012] | -0.0071 [-0.0100, +0.0012] | -0.0040 [-0.0061, -0.0014] LOSS |
| 2010-12 | +0.0002 [-0.0000, +0.0003] | +0.0010 [-0.0015, +0.0033] | -0.0007 [-0.0015, +0.0000] | -0.0008 [-0.0016, -0.0001] LOSS | +0.0002 [-0.0028, +0.0028] |
| 2013-19 | -0.0002 [-0.0005, +0.0000] | +0.0067 [+0.0017, +0.0111] | -0.0006 [-0.0010, -0.0003] LOSS | -0.0004 [-0.0008, +0.0000] | +0.0063 [+0.0011, +0.0108] |
| 2020-22 | +0.0011 [+0.0005, +0.0016] GAIN | +0.0026 [-0.0026, +0.0070] | +0.0002 [-0.0011, +0.0013] | -0.0009 [-0.0026, +0.0004] | +0.0017 [-0.0030, +0.0056] |
| pooled | +0.00003 [-0.0002, +0.0003] | +0.0040 [+0.0019, +0.0063] GAIN | -0.0010 [-0.0021, +0.0001] | -0.0010 [-0.0021, +0.0001] | +0.0030 [+0.0004, +0.0059] GAIN |

By kind (bnm.world-score):
- t66-fix against t65-gauges: food +0.0002 [-0.0032, +0.0039], inflation crises +0.0002 GAIN. Every other kind is within ±0.00001, and banking, currency, fiscal and market crashes are identical.
- t66-food against t66-fix: food +0.0024 [-0.0036, +0.0088], market crashes -0.0152 [-0.0288, -0.0048], growth +0.00001.

**t66-fix loses no fold against either bench and gains 2020-22 against t65-gauges.** The leak was worth nothing to the score. Read on time, the food gauge does slightly better in 2020-22. **t66-food fails the fold rule:** it loses 2013-19 against t65-gauges, 2010-12 against t66-fix and 2007-09 against t53-noclim. All of it is market crashes (loss 2). Food gains, but not significantly.

Calibration, country year, mean p / Brier (t53-noclim → t65-gauges → t66-fix → t66-food):

| kind | span | rate | mean p | Brier |
|---|---|---|---|---|
| food | 2003-22 | 0.154 | 0.183 → 0.237 → 0.238 → 0.242 | 0.1560 → 0.1521 → 0.1519 → **0.1495** |
| food | 2010-12 | 0.060 | 0.206 → 0.214 → 0.209 → 0.217 | 0.0844 → 0.0868 → 0.0850 → 0.0881 |
| food | 2020-22 | 0.415 | 0.060 → 0.142 → 0.162 → 0.145 | 0.3716 → 0.3275 → **0.3135** → 0.3223 |
| market crashes | 2003-22 | 0.178 | 0.326 → 0.277 → 0.277 → 0.278 | 0.1996 → 0.1520 → 0.1520 → 0.1668 |
| market crashes | 2006-07 | 0.957 | 0.285 → 0.318 → 0.318 → 0.247 | 0.5060 → 0.4505 → 0.4505 → 0.5453 |
| growth collapses | 2003-22 | 0.106 | 0.087 → 0.084 → 0.084 → 0.084 | 0.0990 → 0.0965 → 0.0965 → 0.0965 |

The outliers (mean p at the December origin, rate in brackets; caught at p ≥ 0.2):

| episode | origin (rate) | t53-noclim | t65-gauges | t66-fix | t66-food |
|---|---|---|---|---|---|
| food 2006 | 2006 (0.418) | 0.500 | 0.550 | 0.530 | 0.524 |
| food 2007 | 2007 (0.313) | 0.102 | 0.236 | 0.243 | 0.268 |
| food 2010 | 2010 (0.114) | 0.229 | 0.210 | 0.210 | 0.210 |
| food 2020 | 2020 (0.493) | 0.053 | 0.121 | 0.143 | 0.161 |
| food 2021 | 2021 (0.500) | 0.057 | 0.154 | **0.189** | 0.126 |
| market crashes 2006 | 2006 (0.957) | 0.281 | 0.287 | 0.287 | 0.250 |
| market crashes 2007 | 2007 (0.957) | 0.289 | 0.348 | 0.348 | 0.251 |
| growth 2007 | 2007 (0.235) | 0.051 | 0.073 | 0.073 | 0.074 |
| growth 2008 | 2008 (0.202) | 0.061 | 0.073 | 0.073 | 0.073 |
| growth 2019 | 2019 (0.554) | 0.068 | 0.078 | 0.078 | 0.078 |

Alerts at 4:1 (`bnm.alerts`, data/runs/logs/alerts-t66-*.txt). Cost per row, Δ = bench − run:

| kind | t65-gauges | t66-fix | t66-food | t66-fix vs t65 | t66-food vs t65 |
|---|---|---|---|---|---|
| food | 0.653 | 0.632 | 0.647 | +0.021 [-0.004, +0.054] | +0.006 [-0.025, +0.039] |
| market crashes | 0.724 | 0.724 | 0.769 | 0 | -0.045 [-0.127, +0.000] |
| pooled | 0.304 | 0.302 | 0.307 | +0.0016 [-0.0003, +0.0043] | -0.0031 [-0.0100, +0.0019] |

Episodes caught: 6 of 22 in every run. Food 2020 and 2021 stay missed in all three: t66-fix issues 0.143 and 0.189 (0.011 short of the threshold at Dec 2021), t66-food 0.161 and 0.126. Growth 2007, 2018 and 2019 are missed in all runs.

Food weights at the year country node (Decembers, score-t66-weights.txt): the same in all three runs. The models are even to 2008, `economy` holds 0.54-1.00 in 2010-13, and the all-inputs model holds 0.99-1.00 from 2015. `:leading` is 0.000 from 2010 in every run. At the food quarter-lead of t66-food, the second set's `:leading` group is under 0.03 from 2007, while all-inputs (which reads it) holds 0.64-1.00 to 2016, then fragility takes over.

### Why: the losses traced (PLAN principle 9)

Traced by replaying the food year node with each model's evidence, weight, issued p and coefficient means at every December (scratchpad t66/foodtrace.clj). Logs: foodtrace-t65.log, foodtrace-t66food2.log, and the market-crash node under both standardizers (mctrace-node.log, mctrace-hist.log).

1. **Food year node: why the gauges' model had zero weight (bnm-11y). A bug, then a mechanism.**
   - *The bug.* bnm.lattice-banking's standardizer reads a column once more than 20 rows are in. A world gauge is one value per origin, repeated at about 165 countries, so it was read from the second origin on, against the spread of two or three Decembers. At Dec 2006, world food12 stood at z +4.91 and food12 × imports at +4.33 against 2003-05 alone. Against its own quarter ends since 1990 the same reading is +1.09. The leading model issued 0.857 against a rate of 0.418 (all-inputs 0.824, the rest 0.5). When that origin resolved (June 2010) the leading model scored -110.0 nats against economy's -97.5, a 12-nat loss in one origin, and its weight went from 0.09 to 0.000. Fixed behind `--lead-std history`, test first: every leading gauge is standardized against its own values at every quarter end from the node's start, read once 20 quarter ends are in.
   - *The mechanism, which the fix does not change.* With the fix the leading model issues 0.68 at Dec 2006 and still loses 9 nats to economy at that origin, and 3-9 nats at most later ones (2014: -70.6 against -58.1; 2021: -43.0 against -34.1). The gauges are world-level, so the model reading them can move the level of every country but cannot rank countries. The all-inputs model reads the same gauges plus domestic inflation, history and the rest, and wins nearly every ordinary origin by ranking. Under `:evidence-at :issue-joint` one origin is one observation, and there are three common-shock origins on record (2006-08). Averaging picks a model, it does not combine the level of one with the ranking of another, so the gauges reach the forecast only through the all-inputs model's coefficients. Those learn from one episode. World food12 falls from the prior's +0.30 to +0.13-0.23. Food12 × imports goes to about 0: 2008 is the counterexample, imports-weighted z -0.7 at a rate of 0.01 after +1.2 at 0.31. LEADING.md's refitted ceiling (0.26-0.28 at Dec 2021) is a static logistic with its own intercept fitted on every row. The node is anchored instead at the current climatology (`:clim-learn :current`, about 0.08 in 2015-19), from which a +1.6 sd gauge at +0.13 cannot reach 0.2. So the ceiling is not reachable by fixing a bug in this node. Verdict: **sequencing**. It needs a weighting that can pool a level model with a ranking model (a log pool or stacking, bnm-56c.2's `:evidence` rule) or a world-level node that learns the common shock's level. Recorded, not done.
2. **Market crashes in t66-food (-0.0152 [-0.0288, -0.0048]; 2007-09 -93 ×1000 per row against t66-fix). The fix exposed a fragility in t65.** `--lead-std` changes market crashes, not just food, because t65's market-crash gains rested on the same standardizer defect.
   - Under the node standardizer the US curve was read from 1983, against the spread of two to four Decembers, and BAA12 from 1989. With those z values the leading model scored 1.6 nats better than in the history run at the origin resolved in 1987, and 1.1 better at 1988's. By 1989 its weight was 0.45, against 0.03 with the history standardizer. Over 1990-2005 the two runs score the leading model within a nat of each other at every origin. But by 2005 the cumulative gap to `down` is 2.0 nats in t65 and 4.6 with the history standardizer. After the forgetting, that leaves the leading model at 0.096 against 0.011 entering 2006.
   - From that difference alone, t65 gives the leading model 0.40-0.57 at 2006-08 and issues 0.348 at Dec 2007; the history run gives it 0.06-0.10 and issues 0.251.
   - The gauges' z values at 2006-08 are the same under both standardizers to ±0.3 (BAA12 Dec 2007 +2.12 against +1.72, VIX12 +2.50 against +2.84).
   - So the 2007-08 forecast turns on about 2.6 nats of evidence earned in 1983-88 from z values standardized against 2-4 Decembers. This is the averaging's weight lock (LOOP-AUDIT 6-7, §12): a weight earned or lost by a few nats decades earlier decides an episode.
   - Verdict: the standardizer fix is right (an implementation bug, fixed test first). The market-crash loss is **sequencing**: the weight lock that makes the 2006-08 catch depend on 1980s noise. It belongs to the weighting step, not to this one.
   - t66-food-v1, whose standardizer began at 1990 and counted rows, lost more (2007-09 -101 ×1000). It also dropped the curve's 1982-89 history, the second bug, fixed in 448322e.
3. **Food Dec 2021 lower in t66-food (0.126 against t66-fix's 0.189). The second set's fertilizer gauge learned the wrong sign from one episode.**
   - Fertilizer prices peaked in 2008, after food: fertilizer z +1.7 at Dec 2008, where the food rate was 0.01. They fell with food in 2009-10.
   - The all-inputs model's fert12 coefficient moved from the prior's +0.10 to -0.12 by 2011 and -0.20 by 2021 (sd 0.16). At Dec 2021, fertilizer z +3.42 therefore lowers the forecast by about 0.7 in log odds.
   - Dec 2020 rises to 0.161 (t66-fix 0.143) through vegetable oils (z +2.64) and the FAO 6-month change (+1.54). That is the in-sample signal the set was chosen for, and it still does not reach 0.2.
   - Verdict: **approach wrong for fertilizer at the year node in this sample.** The one episode on record has fertilizer lagging food, so a 12-month fertilizer change is concurrent-to-lagging there, not leading, and its learned slope is negative. The momentum gauges move 2020 in the right direction but not to the threshold. 2010 is unchanged (0.210), so the feared Dec 2010 false alarm did not happen.
4. **t66-fix's small 2003-06 and 2013-19 differences against t65 (-0.0003, -0.0002, neither significant)** come from the IMF gauge read a month later at the political kinds' quarter and the food year. Nothing to fix: this is the cost of reading on time.

Bugs found and fixed:
- (a) the IMF and US series' availability (bnm-a1e);
- (b) USA finance/dsr_households held two sources, the Fed's dated 4-5 months early (slow-normalize, bnm-a1e);
- (c) the standardizer reading a world gauge from two origins (bnm-11y);
- (d) the first history standardizer's 1990 start and row count (448322e).

Not fixed:
- The IMF index's 30-month staleness. It never binds on disk; LEADING.md "Lookahead audit".
- The IMF index's revisions: first-release 12-month changes differ by up to 0.05.

Verdict:
- **The lookahead fix costs nothing and helps 2020-22.** t66-fix loses no fold, against t65-gauges or t53-noclim, and its alert cost is lower at food (0.632 against 0.653). It is ready for the user's adoption decision. As a correction of a leak it is the honest base; `--real-lags true` is off until adopted.
- **The food-node standardizer fix is correct, but it cannot be adopted alone.** It removes t65's market-crash catch at 2006-08, because that catch rests on 1980s noise through the weight lock. Adopting it needs the weighting step first.
- **The second food set does not pay.** Food +0.0024 is not significant. Fertilizer learned against its prior, and Dec 2020 rises to 0.161 without reaching 0.2. Its 2020-22 numbers are in-sample for the choice of gauges in any case.
- Food 2020-21 and growth 2007-08 and 2019 remain missed in every run.

## 16. Level from world gauges, ranking from places: t69-pool, t69-pool-nolead (bnm-v1y)

§15 loss 1 found that a world gauge can move every country's level but cannot rank countries. The averaging picks a model; it does not combine one model's level with another's ranking. So the gauges reached the forecast only through the coefficients of whichever model held the weight, and that model was chosen for its ranking. §15 loss 2 found that t65's market-crash catch at 2006-08 rested on about 2.6 nats earned in 1983-88 under the defective node standardizer. The correct standardizer (`--lead-std history`) lost the catch to the weight lock.

### Design

docs/RECURSIVE.md step 5a has the design and the comparison, committed in 8737b0f before the code and before any world run. In short:
- **(A), chosen.** The world gauges set the mean of the common shock: the rows of origin t see z_t + β·g_t. β is a static state with the theory priors of LEADING.md "Priors". It is filtered jointly with z once per origin (3d's vector state, augur's `:shared` and `:load`, scored under `:issue-joint`). The gauges leave every model, so every model issues the same level and the averaging chooses among rankers.
- **(B), rejected:** a log pool of a level model and a ranking model.
  - Its pool weight is learned from the same few episodes, so the lock moves from the models to the pool.
  - It counts the level twice, once in each model's intercept.
  - It needs a new pooled predictive in augur.

Two guards came from the design:
- **No σ = 0 candidate where the gauges are.** Without a shock of its own, β·g would be all an origin's rows share, and β would learn once per place (§12's bug).
- **The state is filtered against the all-inputs model's predictive (`:gauges-from :all`), not the base's.** This was found in the planted test, before any real run.
  - The base omits what ranks the places. Against it, a common shift is attenuated on the average log odds (the logistic's omitted-heterogeneity bias).
  - In the planted world β then learned 0.56, against a truth of 0.8 and a prior of 0.6. Against the ranking model it learned 0.69-0.70, and the late log score improved by 0.002.
  - The all-inputs model reads no weight, so C7 holds.

Code: bnm.lattice `:frailty {:gauges {scale [names]} :gauges-from g}`. `bnm.world-run --frailty-gauges true` moves the year node's world gauges into the frailty:
- market crashes and growth collapses: `[:baa12 :us-curve :vix12]`;
- food: `[:world-food12]`.

`:food12-x-imports` varies by country, so it stays a model input. The quarter-lead keeps its gauges as model inputs. Commits 40e872a, 32a9867 and 819ef67.

### Tests (lattice_test bnm-v1y-*, world_run_test bnm-v1y-*)

The planted world:
- 150 places over 41 years;
- a quiet world gauge (sd 0.3) with four episodes (+2.5 three times, -2 once) that moves the level by 0.8 g;
- a transient the gauge does not see (sd 0.4);
- a place input that ranks the places (1.2 per sd), and 20 noise inputs;
- the theory prior on the gauge: 0.6, sd 0.25.

What the tests show:
- **The combination against the averaging.** With models base, world and place, the averaging must choose between level and ranking. Late log score per row:

  | forecast | log score |
  |---|---|
  | the combination | -0.479 |
  | the averaging (place model at 1.00, no level) | -0.506 |
  | the world model alone | -0.568 |
  | the place model alone | -0.508 |

  At the episodes of years 22 and 32, the combination issues 0.468 and 0.477 against rates of 0.52 and 0.61. The averaging stays at 0.25.
- **With an all-inputs model** (it reads both, and is the right model in this world): the combination does as well (-0.479 against -0.479) and beats the averaging (-0.488).
- **Once per origin.** β's variance at the end is 0.00986 at 30 places and 0.00979 at 150 (ratio 1.007).
- **The theory prior holds at the first data.** The shock issued at the first origin is 0.6 g less the transient's half variance, to 1e-9.
- **Off is unchanged.** `:gauges {}` issues the same forecasts as no option. With the option on, the gauges leave the models: the world group's weight is 0.
- **Bad values are refused:** an unknown name, a gauge without a theory prior, σ = 0 only, `:prior :parent`, an empty list, a non-keyword `:gauges-from`.
- **The world-run option** needs `--leading-priors true` and `:frailty`, and moves only the world gauges.

### Runs

Both were run for the three kinds the gauges reach. Under `--without-relations true` the kinds are independent and these options reach no other kind, so the other ten kinds are t66-fix's, copied (data/runs/t69-*/README.txt).
- **t69-pool** = t65-gauges + `--real-lags true --lead-std history --frailty-gauges true`, at 819ef67.
- **t69-pool-nolead** = the same without `--lead-std history`. It shows whether the old standardizer is still needed.

Logs: data/runs/logs/score-t69-*.txt and alerts-t69-*.txt. The scores cover folds, world, calibration and outliers. The paths file has the weights, z, β and the per-kind folds.

Year nodes, Brier (+ = better, 90% intervals):

| fold | t69-pool vs t65-gauges | vs t66-fix | vs t53-noclim | nolead vs t65-gauges | nolead vs t66-fix | nolead vs t53-noclim |
|---|---|---|---|---|---|---|
| 2003-06 | +0.0034 [+0.0002, +0.0069] | +0.0037 [+0.0011, +0.0067] | +0.0073 [+0.0046, +0.0097] | +0.0036 [-0.0003, +0.0074] | +0.0039 [+0.0006, +0.0071] | +0.0074 [+0.0032, +0.0111] |
| 2007-09 | +0.0119 [-0.0023, +0.0176] | +0.0118 [-0.0023, +0.0175] | +0.0150 [-0.0062, +0.0241] | +0.0076 [-0.0016, +0.0113] | +0.0075 [-0.0016, +0.0111] | +0.0107 [-0.0053, +0.0179] |
| 2010-12 | -0.0003 [-0.0017, +0.0010] | -0.0004 [-0.0017, +0.0007] | +0.0006 [-0.0030, +0.0039] | +0.0011 [-0.0004, +0.0026] | +0.0009 [-0.0006, +0.0025] | +0.0019 [-0.0007, +0.0044] |
| 2013-19 | +0.0002 [-0.0007, +0.0014] | +0.0004 [-0.0005, +0.0016] | +0.0071 [+0.0027, +0.0112] | +0.0010 [-0.0003, +0.0028] | +0.0012 [-0.0001, +0.0030] | +0.0079 [+0.0036, +0.0118] |
| 2020-22 | +0.0002 [-0.0021, +0.0024] | -0.0009 [-0.0027, +0.0008] | +0.0018 [-0.0043, +0.0070] | +0.0007 [-0.0022, +0.0033] | -0.0004 [-0.0028, +0.0019] | +0.0023 [-0.0050, +0.0082] |
| pooled | +0.0018 [-0.0000, +0.0039] | +0.0017 [+0.0001, +0.0038] GAIN | +0.0057 [+0.0028, +0.0090] GAIN | +0.0020 [+0.0005, +0.0036] GAIN | +0.0020 [+0.0005, +0.0034] GAIN | +0.0060 [+0.0033, +0.0086] GAIN |

- **Neither run loses a fold against any bench.**
- **Log pooled, all GAIN:**

  | run | vs t65-gauges | vs t66-fix | vs t53-noclim |
  |---|---|---|---|
  | t69-pool | +0.0058 [+0.0012, +0.0108] | +0.0050 [+0.0006, +0.0102] | +0.0154 [+0.0075, +0.0227] |
  | t69-pool-nolead | +0.0069 [+0.0024, +0.0114] | +0.0061 [+0.0018, +0.0101] | +0.0164 [+0.0089, +0.0234] |

- **Per kind against t66-fix (bnm.world-score).** The other ten kinds are identical.

  | kind | t69-pool | t69-pool-nolead |
  |---|---|---|
  | food | +0.0070 [+0.0020, +0.0132] GAIN (log +0.022 GAIN) | +0.0051 [-0.0002, +0.0103] (log GAIN) |
  | market crashes | +0.0105 [-0.0114, +0.0363] | +0.0188 [+0.0004, +0.0384] GAIN |
  | growth collapses | +0.0051 [-0.0031, +0.0173] | +0.0023 [-0.0055, +0.0124] |

Calibration, country year, mean p / Brier (t53-noclim → t65-gauges → t66-fix → t69-pool → t69-pool-nolead; score-t69-calibration.txt):

| kind | span | rate | mean p | Brier |
|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.326 → 0.277 → 0.277 → 0.304 → 0.278 | 0.1996 → 0.1520 → 0.1520 → 0.1415 → **0.1332** |
| market crashes | 1980-2002 | 0.312 | 0.259 → 0.256 → 0.256 → 0.286 → 0.320 | 0.2395 → 0.2335 → 0.2335 → 0.2264 → 0.2265 |
| market crashes | 2006-07 | 0.957 | 0.285 → 0.318 → 0.318 → 0.463 → 0.431 | 0.5060 → 0.4505 → 0.4505 → 0.2917 → 0.3203 |
| market crashes | 2010-12 | 0.080 | 0.244 → 0.245 → 0.245 → 0.237 → 0.227 | 0.1234 → 0.1128 → 0.1128 → 0.1125 → 0.1054 |
| market crashes | 2018-22 | 0.123 | 0.252 → 0.325 → 0.325 → 0.346 → 0.330 | 0.1296 → 0.1552 → 0.1552 → 0.1741 → 0.1588 |
| food | 2003-22 | 0.154 | 0.183 → 0.237 → 0.238 → 0.229 → 0.224 | 0.1560 → 0.1521 → 0.1519 → **0.1449** → 0.1468 |
| food | 2010-12 | 0.060 | 0.206 → 0.214 → 0.209 → 0.224 → 0.194 | 0.0844 → 0.0868 → 0.0850 → 0.0903 → 0.0793 |
| food | 2020-22 | 0.415 | 0.060 → 0.142 → 0.162 → 0.174 → 0.183 | 0.3716 → 0.3275 → 0.3135 → 0.3040 → 0.3014 |
| growth collapses | 2003-22 | 0.106 | 0.087 → 0.084 → 0.084 → 0.091 → 0.094 | 0.0990 → 0.0965 → 0.0965 → **0.0914** → 0.0942 |
| growth collapses | 2007-08 | 0.219 | 0.056 → 0.073 → 0.073 → 0.118 → 0.070 | 0.1978 → 0.1914 → 0.1914 → 0.1790 → 0.1914 |

Market crashes over 2003-22 now reach LEADING.md 2's stress ceiling (0.1416-0.1456, from a revised series): t69-pool is at it (0.1415) and t69-pool-nolead below it (0.1332). Both are below t62-fixes-seq (0.1363), the best before.

**The outliers** (mean p at the December origin, rate in brackets; caught at p ≥ 0.2; score-t69-outliers.txt):

| episode | origin (rate) | t53-noclim | t65-gauges | t66-fix | t69-pool | t69-pool-nolead |
|---|---|---|---|---|---|---|
| food 2006 | 2006 (0.418) | 0.500 | 0.550 | 0.530 | 0.547 | 0.615 |
| food 2007 | 2007 (0.313) | 0.102 | 0.236 | 0.243 | **0.310** | 0.332 |
| food 2010 | 2010 (0.114) | 0.229 | 0.210 | 0.210 | 0.264 | 0.253 |
| food 2020 | 2020 (0.493) | 0.053 | 0.121 | 0.143 | 0.153 | 0.133 |
| food 2021 | 2021 (0.500) | 0.057 | 0.154 | 0.189 | **0.227 caught** | **0.258 caught** |
| market crashes 2006 | 2006 (0.957) | 0.281 | 0.287 | 0.287 | 0.382 | 0.395 |
| market crashes 2007 | 2007 (0.957) | 0.289 | 0.348 | 0.348 | **0.542** | 0.465 |
| growth 2007 | 2007 (0.235) | 0.051 | 0.073 | 0.073 | 0.108 | 0.079 |
| growth 2008 | 2008 (0.202) | 0.061 | 0.073 | 0.073 | 0.128 | 0.060 |
| growth 2019 | 2019 (0.554) | 0.068 | 0.078 | 0.078 | 0.055 | 0.064 |

**The weights at the year country node** (Decembers; score-t69-paths.txt). The `:leading` group is dormant (weight 0) wherever all its gauges moved to the frailty: market crashes and growth. The rankers hold the node, as before:
- market crashes: `:down` 0.57-0.84 (2000-06), then fiscal 0.64-0.86 (2007-22);
- food: even weights to 2008, economy 0.99 (2010), then all-inputs 0.64-1.00 (2015-22);
- growth: economy 0.83-1.00.

What has changed is that every one of them now issues the level.

**β and the level** at the year country node, filtered at each origin, t69-pool (score-t69-paths.txt):

- **Market crashes.** β learned:

  | gauge | prior | learned |
  |---|---|---|
  | `:baa12` | 0.5 | 0.06-0.17 |
  | `:us-curve` | -0.4 | -0.33 to -0.47 |
  | `:vix12` | 0.25 | 0.37-0.59 |

  β·g at the origin:

  | origin | β·g | reading |
  |---|---|---|
  | 2006 | +0.85 | curve -1.9 |
  | 2007 | +2.07 | BAA +2.1, VIX +2.5 |
  | 2008 | +1.76 | |
  | 2018 | +1.71 | VIX +2.7 |
  | 2020 | +0.81 | |
  | 2022 | +0.79 | curve -2.2 |

- **Food.** β started at its prior of 0.3. It was 0.18 after 2007, 0.50 after 2008 and 0.33 after 2010, and 0.58-0.71 by 2017-21. β·g: +0.34 (2007), +0.51 (2010) and +1.14 (2021, food12 +1.6 sd).
- **Growth.** β learned: `:baa12` 0.0-0.16, `:us-curve` -0.11 to -0.25, `:vix12` 0.23-0.52. β·g: +1.43 (2007), +1.71 (2008) and +1.60 (2018).
- **σ.** The chosen σ is 1.0 almost everywhere. That is the edge of the grid, as in t66-fix at the same nodes (loss 4).

### Alerts at 4:1 (`bnm.alerts`, alerts-t69-*.txt)

Cost per row; Δ = t66-fix's cost − the run's (+ = run cheaper):

| kind | t66-fix | t69-pool | Δ | t69-pool-nolead | Δ |
|---|---|---|---|---|---|
| food | 0.632 | 0.598 | +0.034 [+0.001, +0.067] GAIN | 0.579 | +0.052 [+0.013, +0.098] GAIN |
| growth collapses | 0.424 | 0.377 | +0.047 [-0.079, +0.214] | 0.407 | +0.017 [-0.089, +0.144] |
| market crashes | 0.724 | 0.752 | -0.028 [-0.120, +0.042] | 0.649 | +0.075 [-0.021, +0.169] |
| pooled | 0.302 | 0.298 | +0.004 [-0.009, +0.019] | 0.291 | +0.011 [-0.001, +0.024] |

- **Against t65-gauges, pooled:** t69-pool +0.0057 [-0.0071, +0.0201]; t69-pool-nolead +0.0127 [+0.0007, +0.0256] GAIN.
- **Episodes caught.** t69-pool catches 8 of 22 (11 of them with a rate of at least 0.2), against 6 for t65-gauges and t66-fix. The new catches are food 2021 and growth collapses 2018. t69-pool-nolead catches 7 (food 2021).
- **Still missed:** food 2020 (0.153), growth 2007 (0.108) and growth 2019 (0.055).
- **Growth collapses** now alert at 8.0% of rows, against 0.1% before: 23.9% of onsets, at a 6.1% false-alarm rate.
- **The world node's growth alert cost rises** from 0.150 to 0.350 (three false alarms). These come from the same post-crash and curve readings as loss 1.

### Why: the losses traced (PLAN principle 9)

Per kind and fold against t66-fix, ×1000 Brier per row (score-t69-paths.txt):

| run | market crashes 2013-19 | food 2003-06 | food 2010-12 | growth 2020-22 |
|---|---|---|---|---|
| t69-pool | -15.7 | | -5.3 | -16.8 |
| t69-pool-nolead | | -3.9 | | -20.0 |

None is significant at the fold level.

1. **Market crashes 2013-19 and growth 2020-22: the gauges' known post-crash and 2022 readings, now reaching every model.**

   | node, origin | reading | β·g | issued (t66-fix) | rate |
   |---|---|---|---|---|
   | market crashes, Dec 2018 | VIX12 +2.7 sd, after the Q4 2018 fall | +1.7 | 0.597 (0.490) | 0.213 |
   | growth, Dec 2022 | curve -2.2 sd, the deepest inversion since 1982, then no recession | | 0.226 (0.107) | 0.006 |
   | growth, Dec 2020 | VIX12 +1.3 sd, after the COVID crash | | 0.130 | 0.063 |

   This is §13 loss 1's mechanism. Before, it reached the forecast only through the model that held the weight; now it reaches every model. The sign of every term is theory's. VIX12's β rose from its prior on 2007-08: 0.25 → 0.4-0.6 at market crashes.

   Verdict: **approach, partly.**
   - VIX12 read after a crash is the crash already counted; LEADING.md's prior already called it partly concurrent.
   - The 2022 curve is that gauge's known out-of-sample failure.

   The fix theory suggests was named in §13: VIX12 controlled by the crash's own state, or read only before a crash. It would be chosen after seeing these scores, so it is recorded, not made.

   The same readings give growth collapses 2018 its catch (0.218 against a rate of 0.560). But that window covers 2019-20, the COVID collapses, which no Dec 2018 gauge foresaw. **The catch is a coincidence, not skill**, and it is not counted as evidence for the design.
2. **Food 2010-12 in t69-pool (-5.3).**
   - At Dec 2010, world food12 read +1.5 sd (the 2010-11 spike). β·g was +0.5, and the node issued 0.264 against a rate of 0.114 (t66-fix 0.210). The world spike passed through to few countries' food inflation, as 2008's did after 2007 (§15 loss 1's counterexample).
   - β had moved from 0.50 to 0.33 by the end of 2010 on that origin. It returned to 0.58-0.71 by 2017-21, as 2011 and 2020-21 resolved.
   - Verdict: **approach.** A world food gauge's pass-through differs by episode, and one β per node cannot know which episode will pass through. The country-varying part (food12 × imports) stays with the rankers.
   - Under the node standardizer (nolead) food 2010-12 gains instead (+5.7). Its Dec 2010 gauge reads +1.8 sd at a smaller β (0.38), and it issues lower at Dec 2011 (0.182 against 0.238).
3. **Food 2003-06 in t69-pool-nolead (-3.9).** This is Dec 2006 alone: 0.615 against a rate of 0.418. The node had no resolved outcome, so it issued the uninformed 0.5 lifted by the prior's β·g. The node standardizer reads food12 higher than the history standardizer does (+2.25 against +1.90 at Dec 2007; Dec 2006 is not filtered, so it is not in frailty.tsv). The prior acted as designed, as in §13 loss 3. The 0.5 is the uninformed start (bnm-56c.26).
4. **The frailty's σ at the edge of its grid (not a loss of this step).** The year nodes of all three kinds choose σ = 1.0, the largest value in `:sds`, in t66-fix as here. A wider grid might choose more, and it would move every frailty run since t57, the base of record included. It is recorded, not changed here (bead bnm-02u).

**Is the old standardizer still needed?** No. Under the history standardizer (t69-pool) the 2006-08 market-crash catch no longer depends on the weight lock:
- Dec 2007 issues 0.542, against 0.251 in t66-food and 0.348 in t65-gauges.
- The level comes from β·g whichever model holds the weight (fiscal, 0.73).

t69-pool-nolead scores slightly better pooled (+0.0003 Brier, not significant), with more weight on the curve and less on VIX12. But the node standardizer is the defect §15 fixed, and its scores are no reason to keep it. **t69-pool is the honest candidate.**

Bugs found and fixed in this step:
- (a) Jolt evaluates the argument of `(repeat 0 (f nil))`, so a vector candidate without a slope failed in `slope-diag`. The first test found it, and it is guarded.
- (b) β attenuated under the base reference: a design defect, fixed by `:gauges-from :all` before any real run.

The runs showed no bugs.

### Reproduction

With every option off, the code at 819ef67 reproduces both records:
- **2026-Q4-r1** byte for byte through `bnm.ledger/reproduce-issue!` (data/runs/logs/repro-r1-pool.log, forecasts sha bdb361c8…).
- **The base of record, t65-gauges.** Its command, run for food, market crashes and growth collapses (t65-repro-pool), gives forecasts, weights, preds and frailty identical to t65-gauges' (data/runs/logs/repro-t69-default.txt). The other kinds do not reach the changed code.

The ledger and golden tests pass, and the whole suite is green: 1388 tests, 0 failures (data/runs/logs/suite-t69.log).

### Verdict

- **The combination does what §15 asked.**
  - The level from the world gauges reaches every model, learned once per origin from the theory priors.
  - The averaging chooses among rankers.
  - In the planted world it beats the averaging and each single model by 0.027 nats a row or more.
- **On the world.**
  - t69-pool loses no fold against t65-gauges, t66-fix or t53-noclim.
  - It gains pooled against t66-fix (Brier +0.0017 [+0.0001, +0.0038]; log +0.0050 GAIN) and against t53-noclim (+0.0057).
  - Against t65-gauges it gains on the log score (+0.0058 GAIN), with Brier +0.0018 [-0.0000, +0.0039].
  - It gains food (+0.0070 GAIN).
- **The episodes.**
  - It reaches market crashes 2006-07 under the correct standardizer: 0.382 and 0.542, Brier 0.4505 → 0.2917.
  - It catches food 2021 (0.227), and issues 0.310 for food 2007 at a rate of 0.313.
  - It moves growth 2007-08 from 0.073 to 0.108 and 0.128, short of 0.2.
- **The cost.** False alarms from VIX12 after crashes and from the 2022 curve (loss 1). These are §13's known approach limits, now reaching every model.
- **`--lead-std history` can now be adopted with it:** the weight lock no longer decides the 2008 crash.
- **Adoption** is the user's decision. Every option stays off by default.
