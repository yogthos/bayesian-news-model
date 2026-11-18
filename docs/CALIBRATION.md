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

## 16. At-risk sets and growth discrimination (bnm-96j)

### The bug

A row (kind, place, origin t, window (lo, hi]) is at risk when bnm.lattice-banking/onset-in? returns a value. It left out only rows with a busy month in [t-11, lo]. The catalogues have rules that make more rows impossible:

- once in n years: growth collapses and currency crises once in five, fiscal consolidations once in three (tools/bnm/economic_events.clj, bnm.lattice-banking/currency-crises). The year after an onset is busy, the next ones are not, so a year row whose whole 24-month window falls within five years of the last onset was forecast, trained on and scored though it cannot be positive.
- annual dating: the economic catalogues (growth, currency, inflation, fiscal, sovereign) date every onset in January. The quarter-lead window from a December origin is April to December of the next year, with no January in it, so every December quarter-lead row of these kinds is a certain 0.
- inflation's run rule: an onset is the first year of a run of 40% or more, so a run's later years, and the year after it, cannot start one. The catalogue marks only the onset year busy.

The quarterly food catalogue (once in four quarters, busy three months) and the mechanical catalogues (market crashes, protest and violence surges: 12 months' cool-off, busy the onset's quarter) have rules too, but no window at any scale fits inside them past the busy months. Banking, conflict, coups, wars and sovereign defaults have no rule beyond their busy months: two onsets may come a quarter apart (war onsets are per conflict).

Audit, country rows at December origins (year) and quarter ends (quarter scales), 2003-2022 (all origins 1870-2022 in brackets). No left-out row is positive at any kind or scale, which checks the rules against the catalogues:

| kind | catalogue rule | busy (at-risk exclusion) | year rows left out | quarter-lead | quarter-w2 | half-decade |
|---|---|---|---|---|---|---|
| growth collapses | annual, once in 5 years | 12 months | 341 of 3205 (1027) | 3801 of 12152 | 854 of 11633 | 0 |
| currency crises | annual, once in 5 years | 12 months | 113 of 3262 (499) | 3548 of 12850 | 281 of 12646 | 0 |
| fiscal consolidations | annual, once in 3 years | 12 months | 0 | 2538 of 10127 | 0 | 0 |
| inflation crises | annual, first year of a run of 40%+ | 12 months (not the run) | 15 of 3197 (226) | 3220 of 12710 | 36 of 12667 | 0 (102) |
| sovereign defaults | annual, none | 12 months | 0 | 3327 of 13314 | 0 | 0 |
| food price shocks | quarter ends, once in 4 quarters | 3 months | 0 | 0 | 0 | 0 |
| market crashes, protest and violence surges | quarter starts, 12 months' cool-off | the onset's quarter | 0 | 0 | 0 | 0 |
| banking, conflict, coups, wars | none | spans | 0 | 0 | 0 | 0 |

The quarter-lead counts are mostly the December rows (annual dating); the rest is the refractory.

### The fix

`--at-risk catalogue` (bnm.world-run/at-risk, off by default) gives each catalogue its rule (bnm.kinds/at-risk-rules): the :grid of months it dates onsets on, the :refractory months after an onset, and for inflation the :barred Januaries after every year of 40% or more (bnm.economic-events/inflation-barred, from the same series the catalogue was built from). onset-in? then also leaves out a row unless some month of its window is on the grid, at least the refractory after the last onset at or before lo, and not barred (bnm.lattice-banking/possible-onset?). The at-risk set then is the set of rows the catalogue could make positive. Fixing the catalogues instead (dropping once-in-n) would change what the kinds are: the rules are the sources' (Laeven and Valencia's five-year window) or declustering chosen in advance, and a second collapse within five years is the same episode.

Tests:
- bnm-96j-the-at-risk-set-follows-the-catalogue-s-rule (bnm.lattice-banking-test): without the keys the rows stay as before (the bug, kept); with them the five-year rule, the grid and a barred month each leave out the rows they should and keep the rest.
- bnm-96j-inflation-barred-months: the barred months, and no onset of the rule on one.
- bnm-96j-at-risk-option: nil leaves the catalogues as they were, catalogue adds the rules (the grid by month under --food-outcomes monthly), anything else is refused.
- bnm-96j-every-catalogue-keeps-its-declared-rule (on the data): no onset off its grid, inside its refractory or on a barred month.

### Why the growth-collapse year node does not discriminate (t65-gauges)

Country year rows, December origins 2003-2022 (session scratchpad gdiag.clj, lag.clj, replay.clj):

| p bin | rows | mean p | rate | of them refractory |
|---|---|---|---|---|
| < 0.05 | 180 | 0.034 | 0.050 | 9 |
| 0.05-0.10 | 2389 | 0.078 | 0.131 | 221 |
| 0.10-0.20 | 633 | 0.120 | 0.025 | 111 |
| 0.20-0.30 | 3 | 0.208 | 0.000 | 0 |

Without the 341 refractory rows the 0.10-0.20 bin still realizes 0.031 (522 rows) against 0.145 below it. The refractory rows are a small part of the inversion.

1. **The refractory rows.** By years since the last onset at the origin: 1 year, p 0.078, rate 0 (170 rows); 2 years, p 0.103, rate 0 (171); 10 or more, p 0.082, rate 0.122. The node raises p right after an episode, where the five-year rule forbids an onset. The `history` group (log years since the last onset, counts in 10 and 30 years), which could carry this, holds weight 0.000 from 2003.
2. **No discrimination within an origin.** The mean within-origin AUC is 0.524 over the 17 origins with both outcomes (0.542 without the refractory rows); pooled it is 0.464. The spread of p within an origin is 0.005-0.034 (sd). The 2007-08 and 2018-19 waves are global: 33 of the 2009 onsets and 90 of 2020's.
3. **The level lags the waves.** The mean p per origin correlates with the realized rate at the same origin at -0.19, one and two origins back at -0.35 and -0.38, and four origins back at +0.46. A year row resolves 42 months after its origin (a 24-month window, known 18 months after), so the node learns a wave's level about four years late and issues it in the calm years after: 0.10 at Dec 2003-05, 0.09-0.10 at Dec 2011-12 and 0.107 at Dec 2022, against 0.073-0.081 in the four wave years. Currency crises do the same (+0.85 at four origins back, -0.48 at one); food price shocks lag too (+0.64 at five back).
4. **Which model holds the weight.** `economy` holds 0.43-1.00 of the year country node from 2003 (1.00 from 2011); `leading` holds at most 0.04. A replay of the kind alone with t65-gauges' options (scratchpad replay.clj; its year forecasts equal the run's at all 3205 rows, max |dp| 0) dumps the models' posterior means at each December (standardized gauges, sd in brackets):
   - `economy`: log GDP per head +0.10 (0.04) in 2003, +0.14-0.18 from 2012; growth3 +0.14-0.18 (0.04) throughout; inflation +0.02-0.07 (0.06); food inflation -0.30 to +0.16 (0.11-1.1). Richer countries and faster past growth raise p a little, and the slopes hardly move over twenty years. growth3 and log GDP per head come from Maddison read at its release (--leak-fixes): at Dec 2003-09 the latest published year is 2001, at Dec 2010-12 it is 2008, at Dec 2013-17 2010, at Dec 2018-19 2016 and at Dec 2020-22 2018. The national accounts the node reads are two to eight years old, so they cannot date a collapse; with slopes near 0.15 on a standardized input they spread p within an origin by about ±0.01.
   - `leading`: BAA12 +0.09 to +0.22 (0.11-0.15), curve -0.12 to -0.20, VIX12 +0.02 to +0.16: theory's signs, but the model's weight stays at 0.00-0.04, so the 2007-08 gauges (BAA12 +2.1 and VIX12 +2.5 sd at Dec 2007, §13) do not reach the forecast. This is §12's lock: one origin is one draw, and the leading model has had only the 1990s episodes to earn weight with.
   - `history`: log years since the last onset -0.25 to -0.13 (0.07), onsets in ten years -0.11 to -0.08. The model reads a recent onset as more risk, the opposite of the five-year rule; it holds no weight.

**Verdict.** The growth year node is a lagged climatology. Its level follows the realized rate about four years back, and nothing within an origin separates countries. The refractory rows add to the inverted calibration but do not make it: the inversion is between origins. No bug was found in the node beyond the at-risk set. What would help is a current input, such as WDI's growth per head read at its own publication (the catalogue already chains it after Maddison) or the leading gauges taking weight. Either is a change to the model, recorded here and not made, since it would be chosen after seeing these scores. There is therefore no t68-growth.

### Run: t68-atrisk

t65-gauges' command plus `--at-risk catalogue`, at 2d05fe8 (data/runs/t68-atrisk/README.txt). The suite is green with the change (1387 tests, data/runs/logs/suite-t68-atrisk.log). With the option off, 2026-Q4-r1 reproduces byte for byte (data/runs/logs/repro-r1-atrisk.log, sha bdb361c8…), and so does t65-gauges' own command (t65-gauges-repro-atrisk, every run file identical; run-t65-gauges-repro-atrisk.log). In t68-atrisk, the eight kinds whose rules never bind past their busy months (banking, conflict, coups, food, market crashes, protest and violence surges, wars) are byte-identical to t65-gauges. Only the five annual economic kinds change.

**Comparability.** The fix changes the scored set: 341 growth, 113 currency and 15 inflation year rows leave it (none of them positive), and so do the December quarter-lead rows of the annual kinds. The paired scores below are on the rows both runs issued, which are t68-atrisk's. On its full set, t65-gauges' year Brier is lower than on the shared rows wherever rows were dropped (growth 0.0965 against 0.1069, currency 0.0352 against 0.0362), because it is credited for forecasting certain zeros. Full-set figures are therefore not comparable across the two runs. Logs: data/runs/logs/score-t68-*.txt and alerts-t68-atrisk-vs-t65-gauges.txt.

Year nodes, Brier (+ = t68-atrisk better, 90% intervals resampling countries and years):

| fold | vs t65-gauges | vs t53-noclim |
|---|---|---|
| 2003-06 | +0.0002 [-0.0001, +0.0005] | +0.0040 [+0.0013, +0.0065] |
| 2007-09 | +0.0009 [+0.0001, +0.0019] | +0.0040 [-0.0031, +0.0079] |
| 2010-12 | +0.0000 [-0.0001, +0.0001] | +0.0008 [-0.0015, +0.0030] |
| 2013-19 | +0.0002 [-0.0001, +0.0005] | +0.0071 [+0.0022, +0.0115] |
| 2020-22 | +0.0007 [+0.0002, +0.0013] | +0.0022 [-0.0028, +0.0064] |
| pooled | **+0.0003 [+0.0001, +0.0006] GAIN** | +0.0043 [+0.0022, +0.0067] GAIN |

Log pooled: +0.0012 [-0.0001, +0.0026] against t65-gauges and +0.0109 [+0.0049, +0.0166] against t53-noclim. **No fold loses against either bench.** Quarter nodes against t65-gauges: quarter-lead -0.00005 [-0.0003, +0.0002], quarter-w2 -0.00002 [-0.0001, +0.0000].

Per kind against t65-gauges (bnm.world-score):
- inflation crises +0.0034 [+0.0007, +0.0080] GAIN (log +0.015 GAIN);
- currency crises +0.0011 [-0.0002, +0.0028] (log +0.007 GAIN);
- fiscal consolidations +0.0003 [+0.0000, +0.0006] GAIN;
- growth collapses -0.0003 [-0.0024, +0.0015];
- sovereign defaults -0.0001 [-0.0002, +0.0000] (log -0.0008 [-0.0017, -0.0001], a loss).

By fold (×1000, score-t68-kind-folds.txt):

| kind | 2003-06 | 2007-09 | 2010-12 | 2013-19 | 2020-22 |
|---|---|---|---|---|---|
| currency crises | -1.36 | +0.01 | -0.08 | +0.62 | +8.02 |
| fiscal consolidations | +0.20 | +0.80 | +0.04 | +0.07 | +0.60 |
| growth collapses | +2.62 | **-8.32** | -0.45 | +1.32 | -0.62 |
| inflation crises | +1.00 | **+19.63** | +0.54 | +0.07 | +0.83 |
| sovereign defaults | -0.34 | -0.04 | -0.02 | +0.01 | -0.00 |

Inflation's 2007-09 gain undoes most of the -20.7 against t53-noclim that §12 and §13 carried (the regional lock). Its mean p over 2003-22 falls from 0.030 to 0.022, against a rate of 0.012.

Calibration, country year nodes (each run on its own rows), t65-gauges → t68-atrisk:

| kind | span | rate | mean p | Brier |
|---|---|---|---|---|
| growth collapses | 2003-22 | 0.106 → 0.118 | 0.084 → 0.079 | 0.0965 → 0.1073 |
| growth collapses | 2007-08 | 0.219 | 0.073 → 0.040 | 0.1914 → 0.2030 |
| growth collapses | 2018-19 | 0.557 → 0.580 | 0.080 → 0.087 | 0.4718 → 0.4838 |
| currency crises | 2003-22 | 0.036 → 0.037 | 0.049 → 0.051 | 0.0352 → 0.0351 |
| inflation crises | 2003-22 | 0.012 | 0.030 → 0.022 | 0.0161 → 0.0127 |
| fiscal consolidations | 2003-22 | 0.272 | 0.252 → 0.253 | 0.1974 → 0.1972 |

Market crashes and food are unchanged at every span.

Growth year node in t68-atrisk: pooled AUC 0.464 → 0.493, within-origin AUC 0.524 → 0.549 (17 origins). Mean p correlates with the rate at the same origin at -0.11 and four and five origins back at +0.52 and +0.53 (score-t68-growth-diag.txt). It still does not discriminate.

**Alerts** (p ≥ 0.2, a miss costing 4; against t65-gauges on the shared rows): pooled dcost +0.0008 [-0.0007, +0.0026].
- Inflation crises: +0.0101 [+0.0006, +0.0245] GAIN. Hits 2.6% of onsets against 0%, false alarms 0.4% against 1.3%.
- Currency crises: +0.0060 [-0.0061, +0.0205]. Hits 8.5% against 0%.
- Fiscal consolidations: -0.0048 [-0.0145, +0.0075].
- Growth collapses: -0.0003 [-0.0028, +0.0014], still with no hits.

Episodes: 6 of 21 caught, the same six as t65-gauges.

**The fixed outliers** (mean p at the December origin, rate in brackets), t65-gauges → t68-atrisk:
- Growth: 2007 (0.235) 0.073 → 0.041; 2008 (0.202) 0.073 → 0.038; 2019 (0.571) 0.078 → 0.085. All missed.
- Food, unchanged: 2020 (0.493) 0.121 and 2021 (0.500) 0.154, both missed.
- Market crashes, unchanged: 2006 (0.957) 0.287 and 2007 0.348, both caught.

### Losses traced (PLAN principle 9)

1. **Growth collapses 2007-09, -8.3 ×1000: the lagged frailty, made more persistent.** The issued p at Dec 2006-08 falls from 0.073-0.080 to 0.038-0.052. The year country node's frailty chooses ρ = 0.8 from Dec 2000 in t68-atrisk; t65-gauges kept 0.5 until 2005 (frailty.tsv). So the filtered shocks of the calm origins 2002-05 (-1.4 to -2.3) carry further into the 2007-08 forecasts. A shock is issued only once it is known, 42 months after its origin, so a more persistent ρ carries the calm of 2003-05 into the eve of the 2008 wave. With the refractory rows gone, the calm origins' evidence is no longer diluted by certain zeros, and the persistence that fits the rest of the record wins. This is the lag found in the diagnosis above, not a bug in the fix: every dropped row was a negative the rule forbids. The economy model's weight also falls (0.58 → 0.30 at Dec 2007), to base and world.
2. **Sovereign defaults, log -0.0008.** No year row of the kind leaves the set. The year country node's `down` group (the coarser scale and the region and world ancestors) held 0.08-0.13 of the weight at Dec 2003 and 2013 in t65-gauges and holds 0 in t68-atrisk. The kind's December quarter-lead rows (3327 from 2003) left training and scoring, and they feed the aggregates that group reads. This was not replayed. Its size is a tenth of the gains elsewhere.

Bugs found: the at-risk set (this section). None in the growth node beyond it, and none in the runs.

### Verdict

- **The at-risk set now matches the catalogues.** No row the catalogue could make positive is lost (no positive is left out at any kind or scale, 1870-2022), and the kinds whose rules never bind are byte-identical.
- **On the world, against the base of record:** +0.0003 [+0.0001, +0.0006] GAIN pooled on the shared rows, with no fold loss against t65-gauges or t53-noclim. Inflation and fiscal consolidations gain, and currency crises gain on log. Growth 2007-09 loses (loss 1), and sovereign defaults lose slightly on log.
- **Growth discrimination is not fixed by it,** and was not expected to be. The node's level lags the waves by about four years, and its inputs are years old. The refractory rows were a small part of the inverted calibration.
- **Adoption** is the user's decision. The option is off by default, and the base and the ledger are unchanged.

## 17. Level from world gauges, ranking from places: t69-pool, t69-pool-nolead (bnm-v1y)

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

## 18. Frailty grid and climatology prior: t74-sigma, t75-clim (bnm-02u, bnm-56c.26)

Two tests on the base of record, t71-stack, both approved before any run. Neither passes, so the combined run (t76-both) was not made.

### A. The σ grid past 1 (bnm-02u)

Every year node's frailty chose σ = 1.0, the top of the default `:sds` (§16 loss 4, §17 loss 4). The design is docs/RECURSIVE.md step 3e, written and committed (941f650) before the run:
- `:frailty {:sds :wide}` = [0, 0.25, 0.5, 1, 1.5, 2, 3], every non-zero σ at the same 2 nats. The default is unchanged.
- Above σ = 1 the five-point Hermite rule errs by up to 10% in the forecast at σ = 3, and Laplace's marginal by up to 0.07 nats an origin, toward the larger σ on quiet origins. So a state above 1 is integrated by twenty points and its candidate scored by adaptive Gauss-Hermite. At σ ≤ 1 both are as before.
- Tests (lattice_test bnm-02u-*): the grid; the twenty-point rule's moments; the forecast at σ = 3 within 0.2% of a 4000-step trapezoid from base log odds −8 to 2; the score within 0.002 nats on one row, quiet and busy batches and q > 0; Laplace and five points unchanged at σ ≤ 1; a planted shock of sd 3 chooses 1.0 on the default grid and above 1 on the wide one; no shock still chooses 0. Suite: 1398 tests, 0 failures (data/runs/logs/suite-t74.log).

**t74-sigma** = t71-stack's command with `:frailty {:sds :wide}`, all 13 kinds (data/runs/logs/run-t74-sigma.log).

**σ chosen** at the year country node, 2003-22 (t71-stack: 1.0 at all of these; score-t74-sigma-by-node.txt has every node and scale):

| kind | t74-sigma (ρ) |
|---|---|
| growth collapses | 3.0 at every origin since 1874 (ρ 0.8) |
| food | 3.0 (2008-19, ρ 0 to 2017), 2.0 (2020-21) |
| market crashes | 2.0 (ρ 0) |
| banking crises | 1.5 (ρ 0.8; 2.0 in 1995-98) |
| coup | 1.0 since 1979; 3.0 in 1877-1939, 2.0 and 1.5 after |
| sovereign defaults, inflation | 1.0 |
| fiscal consolidation, conflict, war, protest, violence, currency | as t71-stack (0-1.0) |

The quarter country nodes moved to 1.5-2.0 almost everywhere. So the grid did truncate. But growth collapses and food now sit at the new top, 3.

**Scores against t71-stack**, year nodes (90% intervals; score-t74-folds/world-vs-t71-stack.txt):

| fold | Brier | log |
|---|---|---|
| 2003-06 | -0.0006 [-0.0058, +0.0033] | -0.0001 [-0.0119, +0.0102] |
| 2007-09 | -0.0073 [-0.0104, +0.0016] | -0.0148 [-0.0204, +0.0029] |
| 2010-12 | +0.0021 [-0.0002, +0.0041] | +0.0050 [-0.0025, +0.0117] |
| 2013-19 | +0.0029 [+0.0016, +0.0048] | +0.0085 [+0.0035, +0.0149] |
| 2020-22 | -0.0026 [-0.0064, +0.0010] | **-0.0222 [-0.0350, -0.0068]** |
| pooled | +0.0006 [-0.0015, +0.0023] | +0.0005 [-0.0054, +0.0063] |

No pooled gain, and the log score loses 2020-22. **Not accepted.** By kind (Brier): market crashes +0.0138 [-0.0114, +0.0363], food -0.0078 [-0.0201, +0.0013], sovereign +0.0005 GAIN, inflation +0.0003 GAIN, banking -0.0002 (log -0.0027, a loss), growth +0.0004 (score-t74-kind-folds.txt).

**Alerts at 4:1** (cost per row, + = t74 cheaper): market crashes +0.234 [+0.108, +0.379] GAIN, food -0.057 [-0.153, +0.018], growth -0.015, fiscal -0.018, pooled +0.0114 [-0.0028, +0.0255]. Episodes caught: 7 of 21, against 8. Lost: food 2021. Market crashes now alert on 50.8% of rows (82.3% before), at a 44.7% false-alarm rate.

### B. The climatology prior (bnm-56c.26)

`--clim-prior kinds` (7ad4263, opt-in): a node with nothing of its own resolved takes the resolved counts of the other kinds at its scale and level, instead of an even 0.5. It reads the other kinds' state, so all 13 kinds were run. **t75-clim** = t71-stack's command + `--clim-prior kinds`. The forecasts of banking, conflict, fiscal, growth, sovereign and war are byte-identical to t71-stack's. Their catalogues start in the 1870s with every other kind's, so their nodes never issue before a resolved outcome while another kind has one. Coup, currency, food, inflation, market crashes, protest and violence change.

t52a-climprior's runaway (food year p 0.98 at 2012) does not recur. It was the intercept learned against the low prior offset, and `:clim-learn :current` (bnm-56c.27, in the base since) re-anchors those rows.

| fold | Brier | log |
|---|---|---|
| 2003-06 | +0.0081 [+0.0041, +0.0120] | +0.0182 [+0.0085, +0.0283] |
| 2007-09 | -0.0007 [-0.0015, +0.0009] | -0.0017 [-0.0042, +0.0023] |
| 2010-12 | **-0.0005 [-0.0008, -0.0001] LOSS** | -0.0013 [-0.0022, -0.0002] |
| 2013-19 | -0.0003 [-0.0007, +0.0001] | -0.0012 [-0.0025, +0.0002] |
| 2020-22 | **-0.0031 [-0.0041, -0.0020] LOSS** | -0.0119 [-0.0169, -0.0060] |
| pooled | +0.0011 [-0.0005, +0.0030] | +0.0015 [-0.0024, +0.0065] |

**Fails the fold rule** (2010-12, 2020-22). By kind (Brier): food +0.0130 [-0.0029, +0.0303], market crashes +0.0023, violence -0.0009, inflation -0.0004 (log -0.0044), others within ±0.0002 (score-t75-kind-folds.txt). Alerts: pooled +0.0035 [-0.0055, +0.0140]; 7 episodes caught, against 8. Lost: food 2006, whose catch at 0.547 was the uninformed 0.5.

### Calibration and the outliers

Country year, December origins, mean p / Brier (score-t74-t75-outliers-calibration.txt):

| kind | span | rate | t71-stack | t74-sigma | t75-clim |
|---|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.304 / 0.1415 | 0.216 / **0.1277** | 0.303 / 0.1392 |
| market crashes | 2006-07 | 0.957 | 0.463 / 0.2917 | 0.337 / 0.4287 | 0.472 / 0.2798 |
| market crashes | 2010-12 | 0.080 | 0.237 / 0.1125 | 0.167 / 0.0906 | 0.246 / 0.1167 |
| market crashes | 2018-22 | 0.123 | 0.346 / 0.1741 | 0.247 / 0.1262 | 0.361 / 0.1813 |
| food | 2003-22 | 0.154 | 0.229 / 0.1449 | 0.189 / 0.1527 | 0.152 / **0.1319** |
| food | 2003-06 | 0.209 | 0.493 / 0.2390 | 0.493 / 0.2390 | 0.186 / **0.1590** |
| food | 2010-12 | 0.060 | 0.224 / 0.0903 | 0.183 / 0.0798 | 0.224 / 0.0903 |
| food (carried calm) | 2020-22 | 0.415 | 0.174 / 0.3040 | 0.060 / 0.3697 | 0.114 / 0.3343 |
| growth | 2003-22 | 0.118 | 0.092 / 0.0999 | 0.119 / 0.0995 | = t71 |
| growth (carried calm) | 2007-08 | 0.219 | 0.119 / 0.1790 | 0.179 / 0.1707 | = t71 |
| banking | 2003-22 | 0.032 | 0.045 / 0.0322 | 0.046 / 0.0324 | = t71 |
| banking (carried calm) | 2006-07 | 0.158 | 0.024 / 0.1496 | 0.021 / 0.1506 | = t71 |

| episode (rate) | t71-stack | t74-sigma | t75-clim |
|---|---|---|---|
| food 2006 (0.418) | 0.547 | 0.547 | 0.238 |
| food 2007 (0.313) | 0.310 | 0.310 | 0.310 |
| food 2010 (0.114) | 0.264 | 0.264 | 0.264 |
| food 2020 (0.493) | 0.153 | 0.053 | 0.076 |
| food 2021 (0.500) | 0.227 | 0.060 | 0.167 |
| market crashes 2006 (0.957) | 0.382 | 0.276 | 0.416 |
| market crashes 2007 (0.957) | 0.542 | 0.398 | 0.528 |
| growth 2007 (0.235) | 0.111 | 0.165 | 0.111 |
| growth 2008 (0.202) | 0.129 | 0.193 | 0.129 |
| growth 2019 (0.571) | 0.055 | 0.108 | 0.055 |

The carried calms: t74 moves growth 2007-08 toward the rate (0.119 → 0.179), but it carries food's calm deeper into 2020-22 and leaves banking 2006-07 as it was. t75 leaves growth and banking alone (identical) and also carries food's calm further.

### Why: the losses traced (PLAN principle 9)

1. **t74: σ is a level knob through the half variance (food 2020-22 -0.066, market crashes 2007-09 -0.102, and the calm-year gains).** Step 3e's table predicted this. The shift applied is z − σ²/2, so the expected odds are the base's, but in probability a non-rare base is deflated: 0.20 → 0.169 at σ = 1, 0.102 at σ = 2 and 0.046 at σ = 3.
   - The year nodes issue 24 months plus the lag after their last filtered origin. Where the chosen ρ is 0 (market crashes at every origin since 2003, food in 2008-17), the issued prior is the stationary N(−σ²/2, σ²) whatever was filtered. The frailty then brings no information to the forecast, only the deflation and the spread. In t71-stack this is already true at σ = 1 (market crashes ρ 0 throughout), only milder.
   - The score prefers the larger σ because the deflation fits the calm years, which are most of the origins. Market crashes 2010-12 0.1125 → 0.0906 and 2013-19 +0.036, against the 2006-07 crash: 0.382/0.542 → 0.276/0.398, Brier 0.29 → 0.43.
   - Food: σ = 3 from 2008. The filtered state sits at m ≈ +3 for 2013-19 (applied m − 4.5 ≈ −1.5: a persistent calm level that a ρ = 0 transient cannot carry). By Dec 2020 the node issues from σ = 3, ρ = 0.8, the 2018 state run forward: shift about −2.7 with sd 2.3, so 0.053 against a rate of 0.493.
   - Growth's year country node at σ = 3 from the first origin is the opposite case. Growth is rare at the year (0.1), so the deflation is small, and the spread lifts 2007-08 and 2019 (0.111 → 0.165, 0.055 → 0.108). But it loses 2003-06, 2010-12 and 2020-22 (Brier LOSS in each, -0.003 to -0.008).
   - Banking (log -0.0027): σ 1.5 with ρ 0.8 carries the 2003-04 calm into 2006-07 harder (0.024 → 0.021), §7's persistence.

   Verdict: **not a code bug; a defect of the mean-one rule that the wide grid exposes** (bead bnm-0fe). With σ ≤ 1 the knob is small. Past 1 the grid chooses σ for its level, not for the shock, and a top at 3 is chosen again. The fix is a shift with mean one in probability (place-dependent, through the quadrature), or a level state for the calm, or both. It changes the default at every σ, so it needs a decision and is not made here.
2. **t75, food 2020-22 (-0.030) and 2013-19: the year-quarter loop.** The prior lowers the year node's 2003-06 forecasts from 0.5 to 0.17-0.24 (food 2003-06 +0.080 GAIN, the bug's fix). The quarter-lead node takes its offset from the year's downscaled forecast, so its intercept is now learned against a 0.15-0.2 offset instead of 0.4-0.5, and it is less negative.
   - With 2007's quarter shock (rate 0.26) in it, quarter-lead issues 0.11-0.13 through the 2008-13 calm (rates 0.01-0.06), against t71-stack's 0.03-0.06.
   - t71-stack's calm quarter was right for a wrong reason: an intercept pushed down by the uninformed year 0.5.
   - The year node's all-inputs model reads the quarter state (`:up`), so it issues higher in the calm. It loses weight to economy (2019: all-inputs 0.19, against 0.64 in t71-stack). Its year frailty chooses ρ 0.8 with a lower state in 2015-21, and Dec 2020 issues 0.076 against 0.153.

   Verdict: **approach.** The quarter node learns a level against a downscale it cannot separate from its own intercept (bnm-56c.26's note named this loop). No bug.
3. **t75, market crashes 2010-12 and 2020-22 (-0.004, -0.010), inflation 2007-09 (-0.004), currency 2003-06: the averaging's path.** These kinds' catalogues start in the 1960s-80s, so their nodes issued decades of rows before their first outcome. The other kinds' prior changes those rows' offsets, so it changes the models' evidence at those rows and hence the weights for decades.
   - Market crashes 2005: `:down` 0.51 and fiscal 0.43, against 0.75 and 0.21.
   - Inflation 2008: exposure 0.48 and all-inputs 0.40, against all-inputs 0.97; 2007-08 issues 0.066/0.052 against 0.038/0.024 at a rate of 0.01.
   - The same path also gives market crashes 2003-06 +0.025 GAIN.

   Verdict: **sequencing.** The weight-lock path (§7) amplifies any early change. No bug.

Bugs: none found in either option. The forecast's integral and the candidate's score were both checked against fine quadrature, and the vector candidate's one-dimensional marginal matches augur's vector Laplace to 1e-15 (and departs from quadrature by up to 0.07 nats at σ = 3, as the scalar does).

### Reproduction

- **t71-stack.** t75-clim was run on this code with the default grid. Its six unchanged kinds are byte-identical to t71-stack, banking (the scalar frailty) and growth collapses (the gauged vector frailty at the year node) among them. The new code is reached only through a state above σ = 1, which the default grid cannot hold.
- **2026-Q4-r1.** Its command has no `:frailty`, so `with-frailty` and `shared-ll` are unreachable.
- **The suite,** with the ledger and golden tests: 1398 tests, 0 failures.

### Verdict

- **A (t74-sigma).** The grid did truncate: the year nodes move to 1.5-3. But the larger σ is chosen as a calm-level knob through the half variance, not for the shocks. It gains the calm years and market crashes' alerts, and loses the 2006-07 crash, food 2020-22 and the log score in 2020-22, with no pooled gain. Not accepted. The finding for the base of record: at ρ = 0 the year frailty adds no information to the forecast, only a deflation (σ = 1: 0.20 → 0.17). The mean-one fix (bnm-0fe) comes before a wider grid can be judged.
- **B (t75-clim).** It removes the uninformed 0.5 (food 2003-06 Brier 0.239 → 0.159, food 2003-22 0.1449 → 0.1319). It fails the fold rule in 2010-12 and 2020-22, through the year-quarter loop at food and the averaging's path at the late-catalogue kinds. Not accepted.
- **t76-both** was not run: neither passes.
- Both options stay off by default. Adoption is the user's decision.

## 19. Frailty centred on the rate: t77-pmean, t78-pmean-wide (bnm-0fe, bnm-02u)

§18 found that the frailty's shift z − σ²/2 keeps the odds multiplier at one, so it deflates a common kind's probability, and that a large σ was being chosen as a calm-level knob. The frailty's design (docs/RECURSIVE.md 3b) says the shock leaves the expected rate as it is, so this was reclassed as a bug (bnm-0fe). The fix is docs/RECURSIVE.md step 3f, written and committed (ac6cba6) before any run.
- `:frailty {:centre :rate}`. Each place's shift is the state's mean plus c, where c solves E[F(r + c + √(r_v + v_z) Z)] = E[F(r + √r_v Z)] on the row's own probability curve F. v_z is the transient's stationary variance. N(r, r_v) is the part that is not centred: a level, or the gauges' β·g with its uncertainty. The same c is applied in the issued forecast (F the node's model average), in the learning rows' offsets (the shared effect is the state's own mean), and in the filter and candidate scores (F the `:from` model's predictive, with each candidate's own v_z). If only the forecast were centred, the filter would carry the old correction in its mean. The default `:odds` is unchanged.
- The rule changes the shift at σ = 1 too. Rows at p = 0.03, 0.10 and 0.26 (q = 0) take c = −0.45, −0.37 and −0.21, against −0.5 under the odds rule. With the model's own predictive variance q = 2 they take −0.35, −0.29 and −0.16, since MacKay's probit scales the shock by k < 1. At σ = 1.5 the same rows take −0.97, −0.76 and −0.41, against −1.125.
- Tests (lattice_test bnm-0fe-*):
  - The rate shift's predictive equals the base to 1e-6 for bases 0.01, 0.12, 0.5 and 0.9 at σ 0.25, 1 and 3, also with q > 0 and a part not centred, and is within 0.2% of a fine trapezoid.
  - With nothing filtered, the forecasts equal those without the frailty to 1e-6, for the scalar state and the slope at every base and σ, and for the gauges' state (unit level, g in {−1.5, 0, 2}). The odds rule fails this for a common base.
  - The prior's entries carry no half variance: a level is added as it stands, and a slope's place takes v_z = σ² + σ_u²|e|².
  - `:centre :odds` is byte-identical to the default; bad values are refused.
  - Every form (scalar, level, slope, parent, finer, gauges) runs and still finds its planted world.
  - A planted common kind (base 0.4, no shock, σ held at 2): under the odds rule the base's intercept learns +1.4 to undo the half variance (−2), against −0.4 under the rate rule. So the odds rule's deflation is not always visible as a level: it moves into the intercept, which then measures the chosen candidate's size and not the departure from the climatology.
  - Suite: 1404 tests, 0 failures (data/runs/logs/suite-t77.log).
- Reproduction with the option off: 2026-Q4-r1 byte for byte through `bnm.ledger/reproduce-issue!` (data/runs/logs/repro-r1-pmean.log, forecasts sha bdb361c8…). t71-stack's command rerun for market crashes, food and banking (t71-repro-pmean) gives forecasts.tsv, frailty.tsv and weights.tsv byte for byte.

**Runs.** The frailty is at every node of every kind, so all 13 kinds were run. Both runs use t71-stack's command plus the frailty option:
- **t77-pmean**: `:frailty {:centre :rate}` (22 min).
- **t78-pmean-wide**: `:frailty {:centre :rate :sds :wide}` (40 min).

**σ chosen at the year country node, 2003-22** (score-t77-t78-outliers-calibration.txt):

| kind | t71-stack | t74-sigma (odds, wide) | t77-pmean | t78-pmean-wide |
|---|---|---|---|---|
| growth collapses | 1.0 ρ 0.8 | 3.0 | 1.0 ρ 0.8 | 1.5 (1.0 in 2008, 2017-18) ρ 0.8 |
| food | 1.0, ρ 0-0.8 | 3.0 then 2.0 | 1.0, ρ 0.8 from 2012 | 3.0 (2008-13), 2.0 (2014-19), 1.5 (2020-21), ρ 0.8 from 2012 |
| market crashes | 1.0 ρ 0 | 2.0 ρ 0 | 1.0 ρ 0 | 1.5 ρ 0 |
| banking | 1.0 ρ 0.8 | 1.5 | 1.0 ρ 0.8 | 1.5 ρ 0.8 |
| inflation | 1.0 ρ 0.95 | 1.5 in 2006-09 | as t71 | 1.5 in 2008 |
| the other eight | | as t71 | as t71 | as t71 |

Under the rate rule the wide grid no longer runs to its top. Growth stops at 1.5 instead of 3 and market crashes at 1.5 instead of 2, and food leaves 3 after 2013. So t74's σ = 3 was the half-variance knob, as §18 traced. Some year nodes still choose above 1, so the default grid's top of 1 still truncates them.

**Scores against t71-stack**, year nodes (90% intervals; score-t77/t78-folds/world-vs-t71-stack.txt):

| fold | t77 Brier | t77 log | t78 Brier | t78 log |
|---|---|---|---|---|
| 2003-06 | +0.0010 [-0.0014, +0.0031] | +0.0018 [-0.0038, +0.0069] | +0.0002 [-0.0017, +0.0019] | -0.0014 [-0.0063, +0.0029] |
| 2007-09 | +0.0003 [-0.0003, +0.0007] | +0.0005 [-0.0012, +0.0025] | -0.0000 [-0.0011, +0.0006] | -0.0015 [-0.0052, +0.0015] |
| 2010-12 | **-0.0015 [-0.0023, -0.0006] LOSS** | -0.0041 [-0.0065, -0.0013] | **-0.0021 [-0.0033, -0.0006] LOSS** | -0.0062 [-0.0099, -0.0016] |
| 2013-19 | -0.0002 [-0.0006, +0.0003] | -0.0002 [-0.0016, +0.0017] | -0.0003 [-0.0012, +0.0010] | -0.0014 [-0.0042, +0.0023] |
| 2020-22 | -0.0009 [-0.0024, +0.0005] | -0.0025 [-0.0074, +0.0024] | +0.0000 [-0.0013, +0.0013] | +0.0043 [-0.0019, +0.0105] |
| pooled | -0.0002 [-0.0009, +0.0005] | -0.0006 [-0.0025, +0.0014] | -0.0005 [-0.0011, +0.0003] | -0.0016 [-0.0038, +0.0009] |

Both runs fail the fold rule in 2010-12 and have no pooled gain. **Neither is accepted.** By kind (Brier, score-t77/t78-kind-folds.txt):
- t77: market crashes -0.0021 (LOSS in 2010-12, 2013-19 and 2020-22; +0.016 in 2003-06 and +0.007 in 2007-09), food -0.0020 (LOSS in 2003-06 and 2010-12), growth +0.0009 (LOSS in 2003-12 and 2020-22, +0.0056 in 2013-19), sovereign -0.0002 (LOSS), banking -0.0001 (LOSS in 2010-22), fiscal +0.0008.
- t78: food -0.0025, growth -0.0022 (log -0.0135), market crashes -0.0019, banking -0.0005 (LOSS in every fold from 2007), sovereign -0.0003 (LOSS), fiscal +0.0011, coup +0.0003.

**Alerts at 4:1** (cost per row, + = cheaper than t71-stack; alerts-t77/t78-vs-t71-stack.txt):
- t77 pooled -0.0031 [-0.0067, +0.0006]: market crashes -0.021, growth -0.013, food -0.010, fiscal +0.008.
- t78 pooled **-0.0087 [-0.0147, -0.0022] LOSS**: growth -0.046 LOSS, market crashes -0.045 LOSS, food -0.024, coup +0.006.
- Both catch the same 8 of 21 episodes as t71-stack. Food 2020 and growth 2019 are still missed.

**Calibration and outliers**, country year, December origins, mean p / Brier:

| kind | span | rate | t71-stack | t77-pmean | t78-pmean-wide |
|---|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.304 / 0.1415 | 0.320 / 0.1436 | 0.318 / 0.1434 |
| market crashes | 2006-07 | 0.957 | 0.463 / 0.2917 | 0.493 / **0.2605** | 0.476 / 0.2795 |
| market crashes | 2010-12 | 0.080 | 0.237 / 0.1125 | 0.259 / 0.1220 | 0.270 / 0.1228 |
| market crashes | 2018-22 | 0.123 | 0.346 / 0.1741 | 0.366 / 0.1863 | 0.358 / 0.1766 |
| food | 2003-06 | 0.209 | 0.493 / 0.2390 | 0.497 / 0.2416 | 0.497 / 0.2416 |
| food | 2010-12 | 0.060 | 0.224 / 0.0903 | 0.236 / 0.0965 | 0.236 / 0.0967 |
| food (carried calm) | 2020-22 | 0.415 | 0.174 / 0.3040 | 0.184 / 0.3068 | 0.156 / 0.3043 |
| growth | 2003-22 | 0.118 | 0.092 / 0.0999 | 0.102 / 0.0990 | 0.111 / 0.1021 |
| growth (carried calm) | 2007-08 | 0.219 | 0.119 / 0.1790 | 0.112 / 0.1816 | 0.088 / 0.1870 |
| growth | 2010-12 | 0.052 | 0.084 / 0.0518 | 0.098 / 0.0540 | 0.127 / 0.0587 |
| growth | 2020-22 | 0.047 | 0.141 / 0.0590 | 0.166 / 0.0659 | 0.148 / 0.0593 |
| banking | 2003-22 | 0.032 | 0.045 / 0.0322 | 0.046 / 0.0323 | 0.050 / 0.0327 |
| banking (carried calm) | 2006-07 | 0.158 | 0.024 / 0.1496 | 0.024 / 0.1492 | 0.023 / 0.1495 |
| banking | 2008-10 | 0.024 | 0.086 / 0.0283 | 0.088 / 0.0286 | 0.097 / 0.0298 |

| episode (rate) | t71-stack | t77-pmean | t78-pmean-wide |
|---|---|---|---|
| food 2006 (0.418) | 0.547 | 0.552 | 0.552 |
| food 2007 (0.313) | 0.310 | 0.308 | 0.308 |
| food 2010 (0.114) | 0.264 | 0.262 | 0.262 |
| food 2020 (0.493) | 0.153 | 0.117 | 0.131 |
| food 2021 (0.500) | 0.227 | 0.269 | 0.215 |
| market crashes 2006 (0.957) | 0.382 | 0.433 | 0.397 |
| market crashes 2007 (0.957) | 0.542 | 0.553 | 0.552 |
| growth 2007 (0.235) | 0.111 | 0.111 | 0.101 |
| growth 2008 (0.202) | 0.129 | 0.113 | 0.075 |
| growth 2019 (0.571) | 0.055 | 0.059 | 0.060 |

The carried calms do not lift. Growth 2007-08 falls further (0.119 → 0.112 → 0.088). Banking 2006-07 does not move. Food 2020-22 rises slightly in t77 (0.174 → 0.184) and falls in t78 (0.156).

### Why: the losses traced (PLAN principle 9)

1. **Calm years at market crashes, food and growth (2010-12 in both runs; market crashes 2013-22).** These are rows the models already over-forecast. Market crashes 2010-12 issue 0.237 against a rate of 0.080 in t71-stack, food 2010-12 0.224 against 0.060, growth 2020-22 0.141 against 0.047. The odds rule's −σ²/2 was a fixed downward correction applied at every origin, whatever the frailty had filtered. The rate rule removes it, so these forecasts rise by about 0.01-0.025.
   - The frailty itself sees the calm. Market crashes' filtered z is −1.4 to −1.7 at every origin from 2012 to 2018 in both rules (frailty.tsv). But the year node chooses ρ = 0, so the forecast discards it. It issues 24 months plus the lag after its last filtered origin, and at ρ = 0 the issued prior is the stationary N(0, σ²) whatever was filtered.
   - Food 2008-10 is the same case: ρ = 0, σ = 1.
   - Growth's carried shocks at ρ 0.8 (the 2009 and 2019 episodes, z +0.6 and +2.6) lift the following calm years. Under the rate rule a carried shock is not offset by a deflation.
   - Verdict: **not a bug.** The rate rule does what the design says, and the tests pin it. The odds rule's deflation was hiding a calm-level gap, as §18 found at large σ. The gap is a persistent level that an AR(1) shock, filtered 24-36 months behind the forecast, cannot carry. That is step 3c's level, which failed to carry calms for the same lag (§8), or a regime (step 5). The calm years were scored better under the odds rule for a wrong reason.
2. **Food 2003-06 (LOSS in both, -0.0026).** These are the uninformed year forecasts at 0.5 (§18 B). With nothing filtered, the odds rule issued 0.493 and the rate rule issues the 0.5 the models give, so this is the deflation's removal on an uninformed start. Verdict: **not a bug.** The fix is the climatology prior (bnm-56c.26), already a separate option.
3. **Banking (t78, every fold from 2007, log -0.004) and sovereign defaults (both, -0.0002).** These kinds are rare, so the rows sit where the two rules nearly agree. But the year models' predictive variance is large (q around 2), MacKay's probit scales the shock by k < 1, and the rate shift is about −k σ²/2: −0.35 against −0.5 at p = 0.03 and q = 2. Banking at σ 1.5 and ρ 0.8 also carries its shocks further (2008-10: 0.086 → 0.097 against a rate of 0.024). Verdict: **not a bug.** The rate rule centres the predictive the node actually issues. The odds rule's extra deflation at large q had been working as a calm correction there too.
4. **t78's alerts (growth -0.046, market crashes -0.045).** Higher calm forecasts cross 0.2 more often. Market crashes alert on 90% of rows (86% in t77, 82% in t71-stack) at a 90% false-alarm rate, and growth's false alarms rise from 6.4% to 10.6%. These are the same calm rows as loss 1. No bug.
5. **Gains.** Market crashes 2006-07 (t77: Brier 0.2917 → 0.2605; 2006 0.382 → 0.433) and the 2003-09 folds at market crashes, and food 2021 (0.227 → 0.269). An episode after a calm is no longer deflated.

### Verdict

- The bug is fixed behind `:centre :rate`, tested, and byte-identical when off. The design (docs/RECURSIVE.md step 3f) applies the centring to the forecast, the learning rows and the filter alike.
- Neither run is accepted: both fail the fold rule in 2010-12, with no pooled gain, and t78 loses the alert cost.
- The traced losses are calm years whose over-forecast the odds rule's deflation had masked. They are not a defect of the centring.
- For bnm-02u: centred on the rate, the wide grid no longer runs to its top (growth 1.5, market crashes 1.5, food leaving 3). The wide grid's σ = 3 was the half-variance knob. A wider grid is now a fair test, but it does not pay on its own.
- What the calm years need is a level that reaches the forecast within the information lag: step 3c's level with a fresher source, or step 5's regime. `:centre :rate` should come with it, so that the level is not counted against a hidden deflation. The option stays off by default; adoption is the user's decision.

## Dated credit for banking: t79-bis (bnm-9vm option A)

13 November 2026, branch bis-credit. The check on bnm-9vm found JST's credit-family inputs far from the agencies' own series (corr 0.69-0.91, 14-46% of country-origins more than 0.5 sd apart), because of JST's compilation, not revisions. House prices agree and stay on JST. Option A, the user's choice: take those inputs from the BIS from the December 2003 origin, behind `--dated-credit true` (off by default).

### Design

- **Series.** data/slow/series.tsv's `finance/credit_gdp` is the BIS's credit to the private non-financial sector from all lenders, % GDP (WS_CREDIT_GAP, the series its credit-to-GDP gap is built on). It is not bank credit, as JST's `tloans` is. `finance/household_credit_gdp` is the BIS's total credit to households (WS_TC, all lenders, % GDP), mostly mortgages. Credit replaces JST's credit (tloans/gdp). Household credit replaces JST's mortgages (tmort/gdp) and its household credit (thh/gdp, the extended dimension). Business credit (tbus) is not in the option and stays JST's.
- **Publication.** series.tsv dates BIS quarters four months after they end. ALFRED's vintages of the BIS credit series (data/raw/alfred-bis, 2016-26) show the quarterly releases in early March, June, September and December, each carrying the quarter that ended five to six months before: the fourth quarter in June, the second in December (December 2018 was skipped). `bis-lag` is 6 months. Each year's value is its December quarter (`q4-annual`), so December Y reads Q4 of Y-1, out since June. Q2 of Y is also out at most December origins but is not read: the transforms are annual, and the December release comes only days before the origin.
- **The JST rows (the timing fix).** A row is read at origin t only once every source in it is published (`read-year`, `row-year`). `jst-lag` is 12 months, the slowest of the national sources JST compiles: market prices and the CPI within a month; credit and house price indices within a quarter; annual national accounts by spring; bank balance sheets and fiscal accounts within ten months; compensation (for the wage share) by the autumn. So December Y reads row Y-1 as before. The rule is in the code and tested, and it moves no December value. It dates by the sources, not by JST's releases (from 2016): dating by JST's releases would leave the node without its gauges before 2016, which is bnm-9vm's original question and a model change.
- **The splice, in transformed space.** Every input is computed on the BIS series alone (`bnm.macrohistory/dated-inputs`). Its own history is the baseline, so a level difference from JST cancels:
  - stretches against 10 years (min 6), 5 (min 4) and 20 (min 12), and the one-year change against its own past changes: z-scores, free of scale and level;
  - the credit gap: scripts/jst-credit-gap.py's one-sided HP filter (lambda 1562.5, from the tenth value) on the BIS's annual credit to GDP, in units of the spread of its own past gaps (`gap-z`, at least ten), so the gap's scale cancels. The Clojure filter (`hp-trend`) reproduces the script's statsmodels output for the US to 1e-6;
  - three-year credit growth is not scale free: it is the BIS's change times the ratio of JST's mean level to the BIS's over 1993-2002 (both known by then), so it stays in JST's units. BIS total credit runs 1.3-1.7 times JST's bank loans.
  - From row 2002 on (the December 2003 origin) each input takes the BIS value. Everything the node reads that is built from these inputs is rebuilt from the substituted stretches: the displacement (norm, breadth, largest) at 5, 10 and 20 years, resonance, the flows and stock-times-flow, and the world's mean displacement.
- **No BIS value: JST kept.** All 18 JST countries have both BIS series. Household credit starts late for Switzerland (1999), Denmark (1994), the Netherlands (1990) and Ireland (2002). Where the BIS's own history is too short for a transform, JST's value is kept:
  - Switzerland's household stretch to 2004, Ireland's to 2007;
  - their long household stretches to 2010 and 2013, Denmark's to 2005.

  Dropping the dimension instead would set the gauge to the panel mean (0) and take a dimension out of the norm. JST's value correlates 0.7-0.8 with the BIS's. Ireland's mortgage boom (stretch +3.4 at 2005) is exactly the warning that a miss costing four false alarms can least afford to lose.
- **Past the panel.** JST ends in 2020, so from the December 2022 origin t71-stack's year node has no credit inputs for the JST countries. Under the option they get the BIS's credit, household credit, growth, long stretches and gap. The other JST dimensions and the displacement stay unknown.
- **Not real time.** The BIS first published its long credit series in 2013 (Dembiermont, Drehmann & Muksakunratana). Its values at the 2003-12 origins are a backcast. They are closer to the official statistics than JST's compilation, and revised little (bnm-9vm: real time against latest, credit stretch corr 0.97), but they are not what a forecaster had then.

### Tests (test/bnm/dated_credit_test.clj)

- The one-sided gap is the script's (USA, to 1e-6).
- A level difference cancels at the splice: a BIS series at 1.7 times JST's (households at 2.0) leaves every input, the invariants and the world's displacement unchanged at every year.
- From the splice on, each input is the BIS series' own transform (stretches, long stretch, growth in JST's units, gap z); before it, JST's.
- Too short a BIS history keeps JST's value.
- Off is the base: `build` gives the same result with and without `dated` nil.
- Each year's BIS value is its December quarter; a year without one has none.
- A value is read only once published: from the June release, not in May; a JST row not before the December a year on. This holds at the year node too.
- Past the panel, the node reads the BIS's inputs alone, and nothing with the option off.
- Household credit switches to the BIS from the splice.
- `--dated-credit` takes true or false.

### Runs

- **t79-off**: t71-stack's command for banking only. Its forecasts, preds, weights and frailty are identical byte for byte to t71-stack's (forecasts sha ec1ae917…, the base's).
- **t79-bis**: the same with `--dated-credit true`, at 92f2e4b. Under `--without-relations true` the kinds are independent and the option reaches no other kind, so t79-bis's other twelve kinds are t71-stack's, copied.

Year nodes, banking, against t71-stack (`bnm.links --folds --kinds banking_crisis`; + = t79-bis better, 90% intervals):

| fold | Brier | log |
|---|---|---|
| 2003-06 | -0.00005 [-0.00025, +0.00008] | -0.00052 [-0.00298, +0.00136] |
| 2007-09 | -0.00010 [-0.00035, +0.00011] | -0.00120 [-0.00415, +0.00045] |
| 2010-12 | -0.00031 [-0.00100, +0.00039] | -0.00133 [-0.00412, +0.00175] |
| 2013-19 | +0.00000 [-0.00009, +0.00010] | -0.00037 [-0.00111, +0.00037] |
| 2020-22 | +0.00055 [-0.00003, +0.00160] | +0.00206 [-0.00036, +0.00606] |
| pooled | +0.00001 [-0.00023, +0.00029] | -0.00029 [-0.00150, +0.00092] |

No fold gains or loses. Over all 13 kinds (`bnm.links --folds`, `bnm.world-score`): pooled Brier +0.00000 [-0.00002, +0.00002], log -0.00002 [-0.00010, +0.00007].

The half-decade country nodes are not in the fold rule. Resampling countries (400 reps):
- all countries: Brier +0.00070 [-0.00006, +0.00154], log +0.00314 [+0.00023, +0.00641];
- the JST countries alone: Brier +0.0042 [-0.0008, +0.0099], log +0.0199 [+0.0015, +0.0412].

Within-origin AUC rises from 0.64 to 0.82 at the JST countries, and from 0.45 to 0.51 over all countries. The gain comes from the December 2004 origin, where four countries that had a crisis by 2009 moved up: Belgium 0.121 → 0.212, Germany 0.143 → 0.201, Norway 0.154 → 0.190, Ireland 0.141 → 0.165.

Calibration, banking, country year node, December origins (mean p / Brier, t71-stack → t79-bis):

| span | rate | mean p | Brier |
|---|---|---|---|
| 2003-22 | 0.032 | 0.045 → 0.045 | 0.0322 → 0.0322 |
| 2006 | 0.169 | 0.025 → 0.025 | 0.1600 → 0.1602 |
| 2007 | 0.146 | 0.022 → 0.022 | 0.1385 → 0.1387 |
| 2008 | 0.035 | 0.047 → 0.047 | 0.0328 → 0.0329 |
| 2008-10 | 0.024 | 0.086 → 0.086 | 0.0283 → 0.0285 |
| 2020-22 | 0.000 | 0.030 → 0.028 | 0.0014 → 0.0008 |

### Alerts at 4:1 (`bnm.alerts`)

- **Cost.** Banking cost per row goes from 0.131 (t71-stack) to 0.132 (t79-bis), Δ -0.0012 [-0.0044, +0.0023]. Pooled over kinds, Δ -0.0001 [-0.0003, +0.0002].
- **Hits** stay at 0 of 82. The extra cost is false alarms at Ireland, Sweden and Belgium in 2009-11 (loss 1).
- **The 2006 and 2007 episodes** stay missed: mean p 0.025 at a rate of 0.169, and 0.022 at 0.146.
- **The world node's** cost is unchanged (0.350).

### Banking 2006-08: which inputs moved

At the December 2006-08 origins (rows 2005-07), the BIS moved some JST countries' inputs a long way (raw value, JST → BIS):
- credit stretch: Belgium 2005 -0.98 → +1.08, Switzerland 2007 -2.45 → +1.44, Germany 2007 -2.21 → -0.19, Sweden 2005 +4.52 → +1.63, France 2006 +3.19 → +1.97;
- household credit in place of mortgages: Japan 2005-07 about +2.1 → -2.0 to -3.4 (JST has mortgages rising while BIS household credit falls), Germany 2007 +0.27 → -3.49, Canada 2005 +0.50 → +2.93;
- the gap z: Spain 2006 +5.64 → +3.12, the Netherlands 2006 +0.31 → -1.52, the USA 2007 +1.04 → +2.46;
- three-year growth and the norm moved by under 0.15.

Yet the issued p moved by under 0.012 at every onset row: Ireland 2006 0.077 → 0.066, Belgium 2006 0.033 → 0.039, Denmark 2007 0.045 → 0.052, Spain 2008 0.109 → 0.104. **The 2006-07 miss does not come from JST's credit:**
- 10 of the 22 onsets at the 2006 origin, and 9 of 18 at 2007, are outside JST's panel. There the year node has no credit stretches and issues a flat 0.021-0.024.
- At the JST countries the credit groups hold the weight (booms 0.43, stretches 0.38) and rank the boom countries (Ireland, Spain, Denmark) at two to three times the rest. But the level they start from is 0.02. That level comes from the crisis process and the climatology, after two quiet decades since 1990-92.

### Why: the losses traced (PLAN principle 9)

1. **2010-12 (-0.00031, not significant): the BIS's ratio rose as GDP fell.** The largest losses are false alarms after the crisis:

   | country, origin | t71-stack → t79-bis |
   |---|---|
   | Ireland, Dec 2010 | 0.158 → 0.292 |
   | Ireland, Dec 2011 | 0.014 → 0.209 |
   | Sweden, Dec 2010 | 0.187 → 0.282 |
   | Belgium, Dec 2010 | 0.156 → 0.220 |
   | Switzerland, Dec 2010 | 0.102 → 0.156 |

   At Ireland's row 2010, JST shows deleveraging (growth -0.24, gap z -1.82) where the BIS shows growth +0.56 and gap z +2.49. At Switzerland's rows 2009-10 the BIS credit stretch is +4.2 and +2.4, against JST's +0.4 and -1.8. Two things drive this. Total credit from all lenders includes the corporate and cross-border debt that JST's bank loans leave out (Ireland's multinationals). And a ratio to GDP rises when GDP falls, a known weakness of the credit-to-GDP gap after a crisis.

   Verdict: **approach.** It comes from the series' concept, not a bug. The BIS's bank-credit series (closer to JST's concept, and what bnm-9vm checked against) would remove the non-bank part, though not the denominator effect. It is not in series.tsv, so it is recorded, not changed here.
2. **2003-09 (-0.00005, -0.00010): small moves at onset rows.** Spain 2007-08 and the Netherlands 2007 are lower by 0.003-0.005; Spain's gap z falls from JST's +4.9 to the BIS's +2.7. Verdict: the data, not the code.
3. **Japan.** JST's mortgages stretch is about +2 at 2005-10 while BIS household credit falls (-1 to -3.4). No Japanese crisis followed, so the move costs nothing here, but it shows how far JST's mortgage series is from the agencies'.

The runs showed no bugs.

### Reproduction

With the option off:
- t71-stack's command for banking (t79-off) gives its forecasts, preds, weights and frailty byte for byte.
- 2026-Q4-r1 reproduces byte for byte through `bnm.ledger/reproduce-issue!` (8556 lines, forecasts sha bdb361c8…).

The whole suite is green: 1403 tests, 0 failures.

### Verdict

- **Neutral on the fold rule.** No year-node fold gains or loses at banking (pooled Brier +0.00001 [-0.00023, +0.00029]), so it is not an improvement under the acceptance rule.
- **The half-decade node gains** on the log score (+0.0031 [+0.0002, +0.0064]), from the 2004 origin's ranking. That node is not in the fold rule.
- **The 2006-08 miss does not come from JST's credit.** The level is set by the crisis process, and half the onsets are outside the panel.
- **The cost** is post-crisis false alarms where the BIS's ratio rose as GDP fell (loss 1).
- The inputs it gives are the agencies' own series, dated by their releases. Adoption is the user's decision; the option stays off by default.

## Banking gauges: t85-bgauges, t85-bgauges-flat (bnm-cpv)

18 November 2026, branch banking-gauges. Banking's lattice had no `:gauge-names`, so `--leading` and `--frailty-gauges` never reached it. `--banking-gauges true|flat` adds four world gauges at its year node: `:baa12`, `:us-curve`, `:vix12` and `:world-credit3`. The priors were committed before any run (docs/LEADING.md "Banking gauges", deb3191). Under `--frailty-gauges` the gauges set the common shock's mean. Off by default: t85-off, t80-base's command for banking alone, reproduces t80-base's banking forecasts, preds, weights and frailty byte for byte.

Runs (banking only; the other twelve kinds copied from t80-base): **t85-bgauges** (theory priors) and **t85-bgauges-flat** (mean 0, variance 1). Logs: data/runs/logs/score-t85-*.txt, alerts-t85-vs-t80-base.txt.

Year nodes, banking, against t80-base (+ = candidate better, 90% intervals):

| fold | t85-bgauges Brier | log | t85-bgauges-flat Brier | log |
|---|---|---|---|---|
| 2003-06 | -0.00168 [-0.00330, -0.00023] | -0.0311 [-0.0689, -0.0022] | -0.00167 [-0.00349, -0.00006] | -0.0345 [-0.0795, +0.0004] |
| 2007-09 | +0.00094 [-0.00214, +0.00474] | -0.0003 [-0.0332, +0.0319] | +0.00085 [-0.00307, +0.00486] | -0.0067 [-0.0530, +0.0315] |
| 2010-12 | +0.00075 [-0.00278, +0.00503] | +0.0036 [-0.0194, +0.0309] | -0.00030 [-0.00628, +0.00606] | -0.0005 [-0.0358, +0.0376] |
| 2013-19 | -0.00564 [-0.01087, -0.00148] | -0.0353 [-0.0594, -0.0154] | -0.00236 [-0.00523, +0.00016] | -0.0169 [-0.0366, +0.0011] |
| 2020-22 | -0.00041 [-0.00112, +0.00027] | -0.0038 [-0.0136, +0.0058] | -0.00010 [-0.00067, +0.00046] | +0.0006 [-0.0082, +0.0095] |
| pooled | -0.00213 [-0.00443, +0.00001] | -0.0188 [-0.0348, -0.0048] | -0.00111 [-0.00266, +0.00057] | -0.0138 [-0.0299, -0.0008] |

Both lose by the fold rule. Not adopted.

Country year node, December origins (mean p; rate):

| origin | rate | t80-base | t85-bgauges | t85-bgauges-flat |
|---|---|---|---|---|
| 2006 | 0.169 | 0.025 | 0.014 | 0.012 |
| 2007 | 0.146 | 0.022 | 0.018 | 0.015 |
| 2009 | 0.024 | 0.101 | 0.054 | 0.052 |
| 2018 | 0.008 | 0.051 | 0.168 | 0.137 |

Alerts at 4:1: banking cost 0.132 → 0.138, Δ -0.0066 [-0.0203, +0.0059]. The 2006 and 2007 episodes are still missed. Pooled over kinds, Δ -0.0005 [-0.0019, +0.0004].

### Why: the losses traced (PLAN principle 9)

1. **The slopes leave theory before 2003.** The gauges' coefficients are learned in the frailty filter from 1986 (BAA's start) on. By Dec 1998, `:world-credit3` is at -0.09 (prior +0.4) and `:baa12` at -0.03 (prior +0.3). Only the VIX (+0.2) and the curve (-0.1 to -0.17) keep theory's sign. At Dec 2006-07, world credit reads +1.5 and +3.7 sd, and with its learned slope near zero or below it lowers the level instead of raising it.
2. **World credit to GDP reads a crisis's aftermath as a boom.** It is +3.6 sd at Dec 1998 and at its cap (+5.0) at Dec 2008-09, when GDP fell under the credit stock. That is the denominator effect noted under "Dated credit for banking", here at the world's scale. Before 2003 the gauge stood high after crises and before calm, which is what the filter learned. This was knowable before the run and should have kept the gauge out of the set. Recorded, not refit: a different gauge chosen now would be chosen on these scores.
3. **The changed level reshuffles the node's models.** With the gauges carrying part of the shock, the residual shock picks ρ 0.95 (t80-base 0.8) and runs at -1.2 to -3.4 throughout. Every model's issue-time evidence moves with it. At Dec 2006-07 the boom and stretch models, which rank Ireland, Spain and Denmark, fall from 0.81 of the weight to about 0.10, spread over process, household and nine others. At Dec 2017-18 the exposure model takes 0.67-0.89 (t80-base: booms 0.99), which is the 2018 false alarm (0.168 at a rate of 0.008).
4. **The flat run loses the same way.** The loss is not the priors: wiring world gauges into banking's frailty is what lowers the level and spreads the weights.

The miss at 2006-07 stays where "Dated credit for banking" left it: a level of 0.02 from two quiet decades. A world gauge in the frailty mean does not lift it, because the slopes must be learned from the 1990-92 and 1997-98 waves, where these gauges lag the crises.

Parked 18 November 2026 by the user's decision. `--banking-gauges` stays off by default and in the code; banking's 2006-07 miss is a recorded gap.
## Regime layer: t83-regime, t83-regime-rate (bnm-56c.40, recursive step 5b)

18 November 2026, branch regime (origin/structure 099345d with origin/prob-mean merged, 84dba9a). The design is docs/RECURSIVE.md step 5b, committed (df4e9f8) before any code or run.

### Design, in short

- A hidden calm/stress chain per world family: financial (market crashes, growth collapses, banking crises; gauges `:baa12` +, `:us-curve` −, `:vix12` +) and food (food price shocks; `:world-food12` +). Filtered once per December origin (Hamilton's forward step) from the gauges read at the origin, standardized against their own history as known then. No outcome enters the chain.
- Transitions from theory, fixed before any score: financial p_cc 0.85, p_ss 0.6 (stress about 2.5 years, calm 6-7: Claessens, Kose & Terrones; Reinhart & Rogoff; Kose, Sugawara & Terrones; Drehmann, Borio & Tsatsaronis), stationary stress share 0.27; food p_cc 0.9, p_ss 0.5 (Deaton & Laroque; Cashin, McDermott & Scott), 0.17. Emissions: the regimes two within-regime sds apart (Hamilton 1989's separation), placed so the mixture is a standardized gauge; the three financial gauges tempered to 1.5 gauges' worth (an assumed correlation of 0.5).
- The level: the rows of origin t see z_t + β·g_t + δ x_t, x_t = f(π_t − π̄), f the chain's mean decay over the 24-month window (0.50 financial, 0.46 food). δ is a static state appended to 5a's [z β] (banking: [z δ]), prior N(1.0, 0.5²) at market crashes and banking, N(0.5, 0.5²) at growth and food, filtered jointly once per origin (augur `:shared`/`:load`). Candidates carrying δ (σ > 0 only) pay 2 nats, after those without, so the layer is off unless it earns it.
- Built on prob-mean: the rate centring removes the odds deflation that was hiding the calm-year over-forecast (§19), the job the regime should do on purpose. So two runs: t83-regime (default centring) and t83-regime-rate (`:centre :rate`).

### Tests

- bnm.regime-filter (test/bnm/regime_filter_test.clj): the chains' stationary law and persistence, the window factor, the emissions make a standardized gauge, missing gauges leave the stationary law, the theory signs, accumulation in a calm and stickiness in a stress, saturation, tempering, and no lookahead in the reader.
- lattice_test bnm-56c-40-*, a planted world of 120 places over 60 years, a chain with the financial transitions moving the level by 2 log odds, a gauge that is a noisy emission of it:
  - (i) **where no gauge reads the level** (banking's case) the frailty alone does not move within the lag and the layer does: late log score −0.5186 → −0.4968; at stress origins mean p 0.197 → 0.248 (rate 0.524); δ chosen, learned 1.86. **On top of 5a's gauge mean the test as first written ("does better still") failed**: the regime candidates gained 1.24 nats over 40 origins, under the 2-nat price, and the forecasts were identical (−0.5002 both). Where the gauge is the regime's only emission, the linear gauge mean already carries the regime's level, and the chain's persistence (λ = 0.45) adds little. The assertion as first written is restored (19 November 2026) as a known gap, `bnm-56c-40-on-top-of-the-gauge-mean-known-gap-bnm-56c-40` (`bnm.known-gap/defgap`: run after the suite and printed as OPEN while it fails; if it ever passes it is printed CLOSED and fails the suite until made a deftest again). Rewriting it to "costs nothing" had loosened the acceptance rule; "costs nothing" stays as an additional check in the deftest.
  - (ii) a world without a regime: the layer stays off at every origin, forecasts equal to 1e-12;
  - (iii) gauge and outcomes redrawn after year 30: every forecast and book entry before it unchanged;
  - (iv) off unchanged, bad values refused (no frailty, `:prior :parent`, σ = 0 only, a level, a negative price, a bad prior).
- Candidates with δ held at 0 (prior variance 1e-12) score as the ones without to 2e-12 nats: the vector path is the scalar one, so the layer's verdict is not a bookkeeping bug.
- world_run_test bnm-56c-40-regime-option. Suite: 1423 tests, 0 failures (data/runs/logs/suite-t83.log).

### Reproduction

- **2026-Q4-r1** through `bnm.ledger/reproduce-issue!` on this code, byte for byte (forecasts sha bdb361c8…; data/runs/logs/repro-r1-regime.log).
- **t80-base**: its command with `--regime false` for banking, market crashes and food (t83-off) gives forecasts, preds, weights and frailty identical byte for byte.

### Runs

- **t83-regime** = t80-base's command + `--regime true`, for the four kinds the option reaches; the other nine are t80-base's, copied (kinds independent under `--without-relations true`).
- **t83-regime-rate** = t83-regime + `:frailty {:centre :rate}`, all 13 kinds.
- **t83-diag-free**: a diagnostic, **chosen after seeing t83-regime's result and so in-sample**: the regime's price set to 0, four kinds. Not a candidate.

**No node chose the layer, in either run.** frailty.tsv's rgain column (the best candidate with δ less the best without, before prices) at the year country node:

| kind | t83-regime rgain (nats) | t83-regime-rate |
|---|---|---|
| banking crises | −0.54 (2000) → +1.07 (2007) → +1.77 (2011-17) → +1.14 (2022) | max +1.78 (2012) |
| food price shocks | −0.19 to +0.43 | max +0.47 (2021) |
| growth collapses | −0.75 (2003) to −0.13, +0.07 in 2018 only | max +0.31 |
| market crashes | −0.92 (2003) to −0.05, +0.11 in 2017 only | max +0.08 |

The world nodes gain under 0.7. So t83-regime's four kinds are t80-base's forecasts byte for byte, and t83-regime-rate is the rate centring alone on the new base.

### Scores against t80-base (year nodes, 90% intervals)

t83-regime: every fold +0.00000 [+0.00000, +0.00000] (score-t83-regime-folds.txt); alerts identical.

t83-regime-rate (score-t83-rate-folds.txt, score-t83-rate-world.txt):

| fold | Brier | log |
|---|---|---|
| 2003-06 | +0.00095 [-0.00143, +0.00308] | +0.00182 [-0.00374, +0.00687] |
| 2007-09 | +0.00032 [-0.00034, +0.00070] | +0.00052 [-0.00123, +0.00242] |
| 2010-12 | **-0.00149 [-0.00232, -0.00057] LOSS** | -0.00411 [-0.00653, -0.00132] |
| 2013-19 | -0.00021 [-0.00058, +0.00032] | -0.00022 [-0.00164, +0.00169] |
| 2020-22 | -0.00091 [-0.00241, +0.00053] | -0.00241 [-0.00730, +0.00251] |
| pooled | -0.00020 [-0.00091, +0.00052] | -0.00060 [-0.00249, +0.00134] |

By kind (Brier): market crashes -0.0021 [-0.0106, +0.0067], food -0.0020 [-0.0044, +0.0003], sovereign defaults -0.00018 [-0.00028, -0.00008] (LOSS), banking -0.00008, growth +0.0009, fiscal +0.0008. These are §19's t77-pmean numbers again, on the new base.

### Alerts at 4:1 (`bnm.alerts`, alerts-t83-*.txt)

- t83-regime: identical to t80-base (pooled cost 0.298, Δ +0.0000).
- t83-regime-rate: pooled Δ -0.0030 [-0.0067, +0.0006]; market crashes -0.021, growth -0.013, food -0.010. 8 of 21 episodes caught, as t80-base. Banking 2006 (0.026 at a rate of 0.169) and 2007 (0.023 / 0.146), food 2020 (0.117 / 0.493), growth 2007, 2008 and 2019 still missed.

### Calibration and outliers

Country year, December origins, mean p / Brier (t83-regime = t80-base):

| kind | span | rate | t80-base | t83-regime-rate | t83-diag-free (in-sample) |
|---|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.304 / 0.1415 | 0.320 / 0.1436 | = t80 |
| market crashes | 2006-07 | 0.957 | 0.463 / 0.2917 | 0.493 / 0.2605 | = t80 |
| market crashes (calm) | 2010-12 | 0.080 | 0.237 / 0.1125 | 0.259 / 0.1220 | = t80 |
| market crashes (calm) | 2013-19 | 0.088 | 0.292 / 0.1283 | 0.309 / 0.1371 | = t80 |
| food | 2010-12 | 0.060 | 0.224 / 0.0903 | 0.236 / 0.0965 | 0.223 / 0.0899 |
| food (carried calm) | 2020-22 | 0.415 | 0.174 / 0.3040 | 0.184 / 0.3068 | 0.167 / 0.3103 |
| growth (carried calm) | 2007-08 | 0.219 | 0.119 / 0.1790 | 0.112 / 0.1816 | = t80 |
| growth | 2020-22 | 0.047 | 0.141 / 0.0590 | 0.166 / 0.0659 | = t80 |
| banking | 2003-22 | 0.032 | 0.045 / 0.0322 | 0.046 / 0.0323 | 0.045 / 0.0319 |
| banking (carried calm) | 2006-07 | 0.158 | 0.024 / 0.1497 | 0.024 / 0.1494 | 0.027 / 0.1486 |
| banking | 2008-10 | 0.024 | 0.086 / 0.0285 | 0.089 / 0.0289 | 0.081 / 0.0269 |

| episode (rate) | t80-base | t83-regime-rate | t83-diag-free |
|---|---|---|---|
| food 2006 (0.418) | 0.547 | 0.552 | 0.547 |
| food 2007 (0.313) | 0.310 | 0.308 | 0.310 |
| food 2010 (0.114) | 0.264 | 0.262 | 0.264 |
| food 2020 (0.493) | 0.153 | 0.117 | 0.121 |
| food 2021 (0.500) | 0.227 | 0.269 | 0.239 |
| market crashes 2006 (0.957) | 0.382 | 0.433 | = t80 |
| market crashes 2007 (0.957) | 0.542 | 0.553 | = t80 |
| growth 2007 (0.235) | 0.111 | 0.111 | = t80 |
| growth 2008 (0.202) | 0.129 | 0.113 | = t80 |
| growth 2019 (0.571) | 0.055 | 0.059 | = t80 |
| banking 2006 (0.169) | 0.025 | 0.026 | 0.025 |
| banking 2007 (0.146) | 0.022 | 0.023 | 0.030 |

### The regime path (π, stress probability at the December origin; data/runs/t83-regime/*/regime.tsv)

| | 2005 | 2006 | 2007 | 2008 | 2009 | 2010 | 2011 | 2012 | 2013 | 2014 | 2015 | 2016 | 2017 | 2018 | 2019 | 2020 | 2021 | 2022 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| financial | 0.13 | 0.36 | 0.99 | 1.00 | 0.00 | 0.00 | 0.25 | 0.01 | 0.00 | 0.06 | 0.19 | 0.00 | 0.02 | 0.96 | 0.12 | 0.61 | 0.11 | 0.64 |
| food | 0.05 | 0.36 | 0.91 | 0.02 | 0.01 | 0.58 | 0.11 | 0.03 | 0.00 | 0.01 | 0.00 | 0.04 | 0.02 | 0.01 | 0.01 | 0.13 | 0.73 | 0.26 |

The chain reads the episodes the gauges read, and the same false ones: financial stress at 2018 (VIX12 +2.7 after the Q4 2018 fall) and 2022 (the curve), §17's loss 1 again. 2006 is read only as 0.36 (the curve at −1.9 alone). Food 2020 is 0.13 (food12 +0.65 sd at Dec 2020): the gauge had not yet moved. Earlier: financial stress 1988-90 (the curve), 1995-98, 2000; food 2002-03.

### Why: the losses traced (PLAN principle 9)

1. **Nothing was adopted because nothing earned its price.** Not a bug: candidates with δ ≡ 0 score as those without to 2e-12; the planted world finds the layer where it should.
   - **Market crashes and growth collapses (gain negative throughout).** The chain is a function of the same gauges that 5a's β·g already reads at the year node, and with λ = 0.45 its memory is about a year. At these nodes δ x_t adds a second, nonlinear copy of β·g with its own uncertainty (prior sd 0.5): it pays for that variance at every origin and gains little where β·g already sits. The planted test showed the same (1.2 nats in 40 origins). Approach.
   - **Banking (+1.77 at best, under 2).** No gauge reaches banking otherwise, so here the layer carries information: it gains about a nat when the 2007 origin resolves (π 0.99 before the 2008 wave) and climbs to +1.77 by 2011. It falls back after 2018 (π 0.96 and 2020-22's readings with no crises). Approach: the price is the design's, fixed in advance, and not changed after seeing this.
   - **Food (≤ 0.47).** As at market crashes: `:world-food12` is already in the frailty's mean.
2. **The registered loading is too small to move the gaps it was meant for.** This is the main finding, and it holds even in the diagnostic run where the price is 0 (in-sample):
   - x = f(π − π̄) lies in [−f π̄, f(1 − π̄)] = [−0.14, +0.36] (financial), [−0.08, +0.38] (food). With δ near its theory prior (banking learned 0.78-0.86 in the diagnostic, food 0.5-0.8) the level moves by at most about −0.13 in a calm and +0.3 in a stress.
   - The gaps are larger: market crashes 2010-12 issue 0.237 at a rate of 0.08 (about −1.3 log odds needed); banking 2006-07 0.024 at 0.158 (about +2); food 2020 0.153 at 0.493 (+1.7). In the diagnostic banking 2007 moves 0.022 → 0.030 and 2006 not at all (π 0.36, x +0.04).
   - Verdict: **design, not bug.** δ's prior (1.0 for a window wholly in stress against one wholly calm) is about the size theory gives, but the loading multiplies it by f ≈ 0.5 and by π − π̄, which never exceeds 0.73. The chain carries the timing; nothing in the design lets the data set a level of the size the gaps need, except δ moving far from its prior, which at once per origin takes decades.
3. **The carried calms.** Banking 2006: the chain itself reads 2006 as half calm (only the curve was in stress; spreads and the VIX were low), so no level can come from it then; 2007 is read as stress. Growth 2007-08: π 0.99 and 1.00, but growth's node gains nothing from δ beyond β·g (+1.43 and +1.71 already from 5a, §17). Food 2020: the chain reads calm (0.13) at Dec 2020; the gauge moved in 2021. These are limits of what the gauges read at the origin, not of the filter.
4. **t83-regime-rate's losses (2010-12, sovereign defaults, the calm years).** The rate centring alone: §19's losses 1-3, the calm-year over-forecast the odds deflation had been masking, unchanged because the layer that was to take that job stayed off. No bug.

Bugs found: none in the runs. Two test defects found while writing them, both in the planted harness (the last origin read one year past the drawn chain; the gauges' reference `:all` absent from the planted models), fixed before any result.

### Verdict

- The regime layer is built, tested and byte-identical when off (2026-Q4-r1 and t80-base reproduce). It does what the planted world asks where no gauge reads the level.
- On the world it switches itself off at every node, so t83-regime **is** t80-base, and t83-regime-rate is the rate centring alone, which fails 2010-12 as in §19. Neither is accepted.
- What it shows: a regime filtered from the gauges already in the frailty adds little beyond 5a's gauge mean, and a theory-sized loading on a regime probability cannot move a level by the 1.3-2 log odds the calm years and banking 2006-07 need. Banking, where nothing else reads the origin, is where it came nearest (+1.77 nats).
- Next, to be designed and registered before any run: the level per regime from the data rather than one coefficient on π, e.g. each regime's own climatology (the node's resolved rate within origins the chain classed calm, and within stress), weighted by π at issue; and for banking, gauges of its own at the origin (world credit, house prices) for the chain to read. The option stays off by default; adoption is the user's decision.

## Regime climatology: t84-rclim, t84-rclim-nc (bnm-56c.40, recursive step 5c)

19 November 2026, branch regime-clim (from origin/regime ca042ca). The design is docs/RECURSIVE.md step 5c, committed (6965fd4) before any code or run. All three runs are at a32931f.

### Design, in short

- At each year node of market crashes, growth collapses, banking crises (financial chain) and food price shocks (food chain), the climatology is split by regime.
- Each resolved row counts with weight π_o in the stress rate and 1 − π_o in the calm rate. π_o is the stress probability the 5b chain issued at the row's own origin: a forward filter, with no outcome and no hindsight.
- Only rows of origins the chain had read are classed: financial from 1987, food from 1998.
- Each regime's rate has a beta prior centred on the overall climatology, with the strength of one origin's rows. This is a unit-information prior: the origin is the unit of information about a common level.
- The offset at t is logit(π_t r_s + (1 − π_t) r_c), the mixture taken in probability. It replaces `:clim-learn :current`'s overall rate. Pending rows are re-anchored at their own π_o.
- It is in place from the first origin, with no price. With no classed rows it is the overall climatology exactly.
- The frailty z is filtered against the reference predictive, so z explains departures from the regime rate.
- To avoid counting the regime twice, 5a's gauge mean reads each family gauge net of its regime mean, g − sΔ(π − π̄).
- It cannot be combined with 5b's δ.

### Tests

The tests:
- regime_filter_test covers `pi-reader` (nil before the first gauge read, then 5b's π; no lookahead) and `emission-mean`.
- lattice_test bnm-56c-40-rclim-* covers:
  - the level: the mixture in probability, and the overall rate when no rows are classed;
  - off unchanged;
  - bad values refused: no `:clim-learn :current`, no `:offset`, together with `:regime`, a reader that is not a function;
  - no lookahead: gauge and outcomes redrawn after year 30.
- world_run_test bnm-56c-40-regime-clim-option covers the `--regime-clim` option.

**No regime in the world.** Within the registered tolerance of `:clim-learn :current` (mean |Δp| < 0.005, late log within 0.002). Passes.

**Two planted checks failed as written.** Both are kept, as known gaps the suite reports OPEN (`bnm.known-gap`).

1. **Sharp precursor, banking's node (no frailty gauge).**
   - Calm origins reach the calm rate: 0.195 at a rate of 0.169.
   - Stress origins issue 0.42 at a rate of 0.524. The registered check was within 0.05. The frailty alone gives 0.231.
   - The offset itself reaches 0.50, so the shortfall comes after it. The climatology is a marginal rate. The models add their ranking on the conditional scale, with one intercept (−0.19) learned mostly from calm rows.
   - The regimes are 1.39 apart in marginal log odds but 2.0 apart in conditional log odds. The frailty's odds deflation takes another 0.15.
2. **5b's noisy-gauge world, on 5a's stack.**
   - Late log score −0.4958, against the stack's −0.4939. The registered check was "not worse".
   - Without the gauge centring it scores −0.4919.
   - In that world the within-regime part of the gauge is noise. The uncentred β·g was supplying part of the stress level that (1) misses.
   - So the centring was also run without it (t84-rclim-nc). That choice was made on a planted result, not on a world score.

**A test bug, found and fixed.** 5b's no-lookahead test compared `(lt/book a)` on run results. `lt/book` reads a state, so it returned nil on both sides and the book check could never fail. It now compares `(:book a)`, guards that the book is non-empty, and passes.

**Known gaps.** Part 1 of this round restored 5b's first-written assertion ("on top of the gauge mean it does better still") as the known gap `bnm-56c-40-on-top-of-the-gauge-mean-known-gap-bnm-56c-40`. The rewritten "costs nothing" assertion stays as an additional check.
- clojure.test has no expected-failure marker. So `bnm.known-gap/defgap` defines a plain function with `:known-gap` metadata, which holds its reason.
- `run-tests` skips these functions. The runner runs them after the suite and prints each one OPEN, with its failing assertions.
- A gap that passes is printed CLOSED and fails the suite, until it is made a deftest again.

**Suite.** 1434 tests, 0 failures, 3 known gaps open (data/runs/logs/suite-t84.log).

### Reproduction

- **2026-Q4-r1** reproduces byte for byte through `reproduce-issue!` (8556 lines, sha bdb361c8…; data/runs/logs/repro-r1-rclim.log).
- **t84-off** (t80-base's command with `--regime-clim false`, four kinds): forecasts, preds, weights and frailty equal t80-base's byte for byte.

### Runs

- **t84-rclim** = t80-base + `--regime-clim true`, for the four kinds it reaches. The other nine kinds are t80-base's, copied.
- **t84-rclim-nc** = the same with `--regime-clim uncentred`: the gauge mean reads the gauges as read. Its banking is t84-rclim's, because banking reads no frailty gauge.

### Scores against t80-base (year nodes, 90% intervals)

| fold | t84-rclim Brier | t84-rclim log | t84-rclim-nc Brier | t84-rclim-nc log |
|---|---|---|---|---|
| 2003-06 | -0.00226 [-0.00653, +0.00199] | -0.00495 [-0.01483, +0.00464] | -0.00041 [-0.00165, +0.00099] | -0.00157 [-0.00430, +0.00259] |
| 2007-09 | **-0.00625 [-0.00864, -0.00016] LOSS** | -0.01628 [-0.02149, -0.00376] | +0.00256 [-0.00186, +0.00406] | +0.00403 [-0.00621, +0.00770] |
| 2010-12 | **-0.00148 [-0.00220, -0.00035] LOSS** | -0.00442 [-0.00617, -0.00103] | +0.00035 [-0.00042, +0.00105] | +0.00091 [-0.00186, +0.00337] |
| 2013-19 | -0.00005 [-0.00061, +0.00072] | -0.00081 [-0.00195, +0.00068] | -0.00005 [-0.00187, +0.00123] | +0.00013 [-0.00475, +0.00363] |
| 2020-22 | **-0.00129 [-0.00188, -0.00058] LOSS** | -0.00870 [-0.01216, -0.00413] | -0.00226 [-0.00505, +0.00044] | -0.00397 [-0.01194, +0.00428] |
| pooled | **-0.00145 [-0.00268, -0.00024] LOSS** | -0.00483 [-0.00776, -0.00198] | -0.00021 [-0.00118, +0.00070] | -0.00054 [-0.00291, +0.00193] |

By kind, Brier (`bnm.world-score`; score-t84-rclim-*.txt):

| kind | t84-rclim | t84-rclim-nc |
|---|---|---|
| market crashes | -0.01369 [-0.02905, +0.00033] | -0.00329 [-0.01656, +0.00950] |
| growth collapses | **-0.00277 [-0.00481, -0.00091]** | +0.00013 [-0.00263, +0.00377] |
| food price shocks | -0.00188 [-0.00605, +0.00264] | +0.00088 [-0.00054, +0.00275] |
| banking crises | **-0.00049 [-0.00084, -0.00013]** | **-0.00049 [-0.00084, -0.00013]** |

### Alerts at 4:1 (`bnm.alerts`, alerts-t84-rclim*.txt)

**t84-rclim**
- Pooled cost 0.300 against 0.298, Δ -0.0020 [-0.0079, +0.0058].
- Food is a significant loss, -0.0292 [-0.0646, -0.0007]: food 2021 falls to 0.179 and is missed.
- Growth -0.0140 and market crashes +0.0175, neither significant.
- 7 of 21 episodes caught, against 8.

**t84-rclim-nc**
- Pooled cost 0.288, Δ **+0.0099 [+0.0019, +0.0188] GAIN**.
- The gain is all market crashes, +0.1328 [+0.0397, +0.2506]. False alarms fall from 81% to 61% of quiet rows, and hits from 88% to 84%.
- The same 8 of 21 episodes are caught as by t80-base. Banking 2006-07, food 2020 and growth 2019 are still missed.

### Calibration and outliers

Country year node, December origins, mean p / Brier:

| kind | span | rate | t80-base | t84-rclim | t84-rclim-nc |
|---|---|---|---|---|---|
| market crashes | 2003-22 | 0.178 | 0.304 / 0.1415 | 0.314 / 0.1552 | 0.310 / 0.1448 |
| market crashes | 2006-07 | 0.957 | 0.463 / 0.2917 | 0.417 / 0.3333 | 0.512 / 0.2501 |
| market crashes (calm) | 2010-12 | 0.080 | 0.237 / 0.1125 | 0.277 / 0.1273 | 0.221 / 0.1098 |
| market crashes (calm) | 2013-19 | 0.088 | 0.292 / 0.1283 | 0.299 / 0.1274 | 0.277 / 0.1329 |
| food | 2003-22 | 0.154 | 0.229 / 0.1449 | 0.226 / 0.1468 | 0.225 / 0.1440 |
| food | 2010-12 | 0.060 | 0.224 / 0.0903 | 0.225 / 0.0906 | 0.218 / 0.0880 |
| food (carried calm) | 2020-22 | 0.415 | 0.174 / 0.3040 | 0.139 / 0.3239 | 0.185 / 0.3001 |
| growth | 2003-22 | 0.118 | 0.092 / 0.0999 | 0.089 / 0.1027 | 0.086 / 0.0998 |
| growth (carried calm) | 2007-08 | 0.219 | 0.119 / 0.1790 | 0.069 / 0.1927 | 0.086 / 0.1873 |
| growth | 2020-22 | 0.047 | 0.141 / 0.0590 | 0.144 / 0.0618 | 0.160 / 0.0671 |
| banking | 2003-22 | 0.032 | 0.045 / 0.0322 | 0.052 / 0.0327 | = t84-rclim |
| banking (carried calm) | 2006-07 | 0.158 | 0.024 / 0.1497 | 0.028 / 0.1487 | = t84-rclim |
| banking | 2008-10 | 0.024 | 0.086 / 0.0285 | 0.097 / 0.0299 | = t84-rclim |

| episode (rate) | t80-base | t84-rclim | t84-rclim-nc |
|---|---|---|---|
| food 2006 (0.418) | 0.547 | 0.535 | 0.547 |
| food 2007 (0.313) | 0.310 | 0.296 | 0.310 |
| food 2010 (0.114) | 0.264 | 0.252 | 0.269 |
| food 2020 (0.493) | 0.153 | 0.112 | 0.140 |
| food 2021 (0.500) | 0.227 | 0.179 | 0.266 |
| market crashes 2006 (0.957) | 0.382 | 0.392 | 0.405 |
| market crashes 2007 (0.957) | 0.542 | 0.442 | 0.618 |
| growth 2007 (0.235) | 0.111 | 0.071 | 0.084 |
| growth 2008 (0.202) | 0.129 | 0.067 | 0.087 |
| growth 2019 (0.571) | 0.055 | 0.053 | 0.051 |
| banking 2006 (0.169) | 0.025 | 0.028 | 0.028 |
| banking 2007 (0.146) | 0.022 | 0.027 | 0.027 |

### The regime rates the world gave

Country year node, rows known by the origin. They were recomputed offline from forecasts.tsv and regime.tsv, and match the issued level in rclim.tsv to within the rows' resolution timing.

| kind, origin (π) | overall | stress rate (weighted rows) | calm rate (weighted rows) | issued |
|---|---|---|---|---|
| market crashes, 2007 (0.99) | 0.252 | 0.344 (212) | 0.189 (317) | 0.344 |
| market crashes, 2010 (0.00) | 0.315 | 0.483 (279) | 0.255 (346) | 0.260 |
| market crashes, 2018 (0.96) | 0.258 | 0.452 (300) | 0.169 (630) | 0.429 |
| banking, 2006 (0.36) | 0.050 | 0.088 (959) | 0.076 (1279) | 0.083 |
| banking, 2007 (0.99) | 0.050 | 0.088 (976) | 0.074 (1392) | 0.085 |
| growth, 2007 (0.99) | 0.056 | 0.065 (1227) | 0.033 (1702) | 0.065 |
| food, 2020 (0.13) | 0.118 | 0.209 (444) | 0.096 (1942) | 0.113 |
| food, 2021 (0.73) | 0.116 | 0.209 (445) | 0.096 (2090) | 0.163 |

### Why: the losses traced (PLAN principle 9)

1. **t84-rclim's fold losses (2007-09, 2010-12, 2020-22): the centring took away a level that the climatology did not give back.**
   - At market crashes and growth, 5a's β·g was carrying the regime's level.
   - In stress, the centring removes sΔ(1 − π̄) = 1.09 sd from each gauge. At market crashes' learned β (0.17, −0.44, 0.59) that is about 1.3 log odds. In calm it adds back the 0.40 sd that had been lowering the level.
   - The climatology put back at most about +0.4 log odds (2007: 0.344 against an overall 0.252). At 2010 its calm rate (0.255) was above the realized 0.08.
   - Results: market crashes 2007 fell 0.542 → 0.442, growth 2007-08 fell 0.12 → 0.07, food 2021 fell 0.227 → 0.179 (missed), and the calm years 2010-12 rose 0.237 → 0.277.
   - Verdict: design. The centring is right only if the regime rates carry the level, and on this history they do not (see 2).
   - Not a bug. The node's gauges match the chain's to 1e-14 (t80-base's frailty.tsv). The rates recomputed offline match the issued level.
2. **The regime rates barely separate, because the classed history does not line up with the regimes.**
   - **Market crashes.** The 2000-02 crashes fall in the windows of origins the chain read as calm (Dec 1999 π 0.06, Dec 2001 0.04). So the calm rate is 0.19-0.26, two to three times the calm years' 0.08. The calm years are calmer than calm history.
   - **Banking.** The 1990s waves fall at origins classed calm (1991-94: the Nordic countries, Japan, Mexico) and at origins classed stress (1995-98: Asia). The two rates are 0.088 and 0.074-0.076.
   - **Growth** separates (0.065 against 0.033), but both stay far below the 0.22 of 2007-08.
   - **Food** separates most (0.209 against 0.096). But the chain reads Dec 2020 as calm (π 0.13), and at 2021 the mixture gives 0.163 at a rate of 0.50.
   - Why: a gauge-classed regime predicts the next 24 months' rate poorly on this history. A stress reading often coincides with a crisis already under way and is followed by recovery (Dec 2008: π 1.00, window 2009-10). The soft weights then pull the two rates together.
   - Verdict: approach. This is the data, not the code.
3. **Banking's Brier loss (-0.00049, in both runs): the classed era's rate replaced the long history's.**
   - Only rows from 1987 on are classed. Banking's rate since 1987 (0.075-0.088 in both regimes) is above its overall rate since 1870 (0.050).
   - So the level rose in both regimes. 2008-10 went 0.086 → 0.097, with more false alarms after the crisis.
   - The gain at 2006-07 is only +0.004 (0.024 → 0.028), at rates of 0.15-0.17.
   - Verdict: design. Classing only post-1987 rows turns the regime climatology into a recent-era climatology wherever the recent era differs from the long one.
4. **t84-rclim-nc: neutral on the fold rule; alerts gain at market crashes.**
   - Uncentred, the climatology adds its small gap on top of β·g. That is the double count the design meant to remove. β is learned once per origin, so it is partly trimmed.
   - Market crashes 2006-07 rise to 0.512 (2007: 0.618). The calm years 2013-19 fall 0.292 → 0.277, and false alarms fall at the 0.2 threshold.
   - Growth 2020-22 rises 0.141 → 0.160: π is 0.61 and 0.64 at 2020 and 2022, the curve's false readings (5b's loss 1).
   - Proper scores: no fold won or lost, and banking loses as in 3.
5. **The two planted gaps** (marginal against conditional scale; centring in a noisy world) are the mechanism of 1 on a small scale. They stay open in the suite.

Bugs found: the vacuous book check in 5b's no-lookahead test, fixed above. None in the runs.

### Verdict

- **t84-rclim, the registered design, is rejected.**
  - It loses three of five folds, and pooled: Brier -0.00145 [-0.00268, -0.00024].
  - Alerts at 4:1 do not improve (Δ -0.0020), and food 2021 is missed.
- **t84-rclim-nc is neutral on proper scores, and not accepted.**
  - Pooled Brier -0.00021 [-0.00118, +0.00070], no fold won or lost. Banking loses: -0.00049 [-0.00084, -0.00013].
  - Its alert gain (+0.0099 [+0.0019, +0.0188], all from market crashes) comes from a variant chosen after the planted test.
  - By the design's own argument, it counts the regime twice.
- **What it shows.** On 1987-2022, the regime read from the gauges does not separate the outcome rates enough to set the level.
  - The moves the gaps need (-1.3 log odds in the calm years, +2 at banking 2006-07) are not in the classed history.
  - The calm years are calmer than calm history, and banking's history is as crisis-prone in calm as in stress.
- **5b's finding stands, for a different reason.** There the loading capped the move. Here the data do not hold the move.
- **Next, not run:**
  - an anchor on the conditional scale (planted gap 1);
  - for banking, gauges of its own at the origin (world credit, house prices), since the financial chain does not class banking's history.
- The option stays off by default. Adoption is the user's decision.
## Banking credit beyond JST: t81-widebank, t82-widebank (bnm-56c.39)

14 November 2026, branch wide-banking (from bis-credit 834376f). t79-bis found that half the banking onsets at the 2006 and 2007 origins (10 of 22, 9 of 18) are outside JST's 18 countries. There the year node has no credit stretches, house prices or booms, and issues a flat 0.021-0.024. Here those countries get the same inputs from the BIS, behind `--wide-credit` (needs `--dated-credit true`; off by default).

### Coverage

The lattice has 130 countries, 18 in JST. Of the other 112, data/slow/series.tsv has BIS credit to GDP (all lenders) and household credit for 23, and real residential property prices for 36. WS_TC.zip adds no country (the same 44 economies) but holds bank credit to the private non-financial sector, which series.tsv lacks (43 economies; scripts/bis-bank-credit.py writes data/derived/bis-bank-credit.csv). WS_SPP.zip adds Malta and Serbia, neither in the lattice.

| country | credit (all lenders) | bank credit | household credit | real house prices | onset rows (origin) |
|---|---|---|---|---|---|
| ARG | 1984 | 1984 | 1994 | - | |
| AUT | 1960 | 1960 | 1995 | 1986 | 2005, 2006 |
| BGR | - | - | - | 1998 | |
| BRA | 1996 | 1996 | 1996 | 2001 | |
| CHL | 1983 | 1983 | 2002 | 2002 | |
| CHN | 1985 | 1985 | 2006 | 2005 | |
| COL | 1996 | 1994 | 1996 | 1988 | |
| CYP | - | - | - | 2002 | 2009, 2010 |
| CZE | 1993 | 1993 | 1995 | 2008 | |
| EST | - | - | - | 2005 | |
| GRC | 1970 | 1970 | 1994 | 1997 | 2008, 2009 |
| HRV | - | - | - | 1997 | 2005, 2006 |
| HUN | 1970 | 1970 | 1970 | 1992 | 2006, 2007 |
| IDN | 1976 | 1976 | 2001 | 2002 | |
| IND | 1951 | 1951 | 2007 | 2009 | |
| ISL | - | - | - | 1981 | 2006, 2007 |
| ISR | 1990 | 1990 | 1992 | 1994 | |
| KOR | 1962 | 1962 | 1962 | 1975 | |
| LTU | - | - | - | 1998 | 2006, 2007 |
| LUX | 1995 | 2003 | 1995 | 2007 | 2006, 2007 |
| LVA | - | - | - | 2006 | 2006, 2007 |
| MAR, MKD, PER, PHL, SVK | - | - | - | 2007, 2000, 1998, 2008, 2006 | |
| MEX | 1980 | 1980 | 1994 | 2005 | |
| MYS | 1964 | 1964 | 2006 | 1988 | |
| NZL | 1960 | 1960 | 1990 | 1962 | |
| POL | 1992 | 1992 | 1995 | 2010 | |
| ROU | - | - | - | 2009 | 2005, 2006 |
| RUS | 1995 | 1995 | 1998 | 2001 | |
| SGP | 1970 | 1970 | 1991 | 1998 | |
| SVN | - | - | - | 2007 | 2007, 2008 |
| THA | 1970 | 1970 | 1991 | 1991 | |
| TUR | 1986 | 1986 | 1986 | 2010 | |
| ZAF | 1965 | 1965 | 2008 | 1966 | |

First year of each annual (fourth-quarter) series. Onset rows are the year node's December origins 2003-22 with an onset in the window.

- **2003-22:** 82 onset rows, 28 at JST countries, 54 outside. With an input usable at the row the origin reads (enough history for the transform): credit stretch 6 (bank credit; 8 with all lenders), household credit 8, house prices 14, any input 16 of 54. The other 38 have no usable BIS input, mostly countries the BIS does not cover (Ukraine, Kazakhstan, Nigeria, Mongolia, Moldova, Azerbaijan, Angola, Ghana, Lebanon and others).
- **2006-08:** at 2006, 6 of the 10 outside the panel get an input (Austria, Hungary, Luxembourg from credit; Croatia, Iceland, Lithuania from house prices alone); at 2007, 4 of 9 (Hungary, Iceland, Lithuania, Luxembourg); at 2008, Greece. Latvia's house prices start in 2006, too late; Romania, Slovenia, Mongolia, Ukraine and Nigeria have nothing.

### Design

- **Same transforms, same lag, same splice.** Each input outside the panel is `bnm.macrohistory/dated-inputs` on the BIS series, exactly as for the JST countries after the splice: stretches against 10 (min 6), 5 (min 4) and 20 years (min 12), the flows, the one-sided HP gap in units of its own past gaps (at least ten, so nineteen years of credit). Each year's December quarter, read at `bis-lag` 6, from row 2002 (the December 2003 origin). Before the splice these countries keep what they had.
- **House prices.** finance/real_house_prices (BIS, real index, log) in place of JST's log of nominal house prices over the CPI. A log index's stretch is free of its base; three-year growth is its three-year log change, as JST's.
- **Growth needs a scale.** JST countries' three-year credit growth is in JST's units (loans over GDP), via each country's ratio of JST's mean level to the BIS's over 1993-2002. Outside the panel there is no JST level, so `bnm.macrohistory/panel-scale` takes the median of the JST countries' ratios, known in 2002:
  - against all-lender credit the ratios run 0.0038-0.0087, median 0.0059: total credit at 1.2-2.6 times bank loans, so the median assumes a typical bank share;
  - against BIS bank credit (`--wide-credit bank`) the median is 0.0105: JST's loans and the BIS's bank credit are the same thing at the same level, so growth is the BIS's own units, a percentage made a ratio.
  A scale-free growth (relative to the country's own level) was considered and rejected: it gives Luxembourg's 2006-09 jump (all-lender credit 209% to 346% of GDP) 66%, as alarming as in pp.
- **Too little history: unknown.** An input whose own history is too short for its transform is nil, and the gauge reads it as the panel mean (0), as before. The displacement invariants (norm, breadth, resonance, flows) need five dimensions and these rows have three, so they stay unknown: a three-dimension norm would be a different quantity under the JST norm's coefficient.
- **Household credit in the extended dimensions** (slot 39) also comes from the BIS for these countries, as `--dated-credit` does for the JST countries.
- **One model, one standardization.** The rows join the same year and half-decade models and the same standardizer. The inputs are z-scores against each country's own past (or growth in JST's units), so a coefficient has the same meaning in both, and the lattice has one model per scale and level. A side effect: the pooled standardizer now includes these countries' values from 2003, which moves the JST rows' gauges slightly (JST rows in t82: Brier +0.00026 [+0.00002, +0.00047] against t79-bis).
- **Two credit series.** `--wide-credit true` uses all-lender credit, the series the JST rows use under `--dated-credit`. `--wide-credit bank` uses BIS bank credit for the credit input, JST's own concept (household credit and house prices are the same in both). Bank credit was added after t81's losses traced to Luxembourg (loss 1 below).

### Tests (test/bnm/wide_credit_test.clj)

- The panel scale is the median of the JST countries' ratios.
- Outside the panel, a country with a JST country's BIS series gets the same stretches, short and long stretches, flows and gap z at every year from the splice. Its growth is the BIS change times the panel scale. House prices are the log index's own stretches and three-year log change. Inside the panel nothing gains house inputs.
- Too short a history stays unknown: no stretch before six past years, no long stretch before twelve, no gap before ten past gaps.
- A value is read only once published: at December 2008 the node reads row 2007 (the June release), in November row 2006. Nothing is read before the splice. The other stretches and the displacement stay unknown.
- Off is t79-bis: no row for a country outside the panel, and a JST country's row past the panel reads the same.
- The wide values cover only countries outside the panel, nothing before the splice.
- `--wide-credit bank` takes the credit input from bank credit, growth in its own units, household credit unchanged.
- `--wide-credit` takes true, bank or false, and refuses true or bank without `--dated-credit true`.

### Runs

t71-stack's command with `--dated-credit true`, banking only (the other twelve kinds copied from t71-stack: under `--without-relations true` the kinds are independent):
- **t81-off**: `--wide-credit false`. Its forecasts, preds, weights and frailty are byte for byte t79-bis's (forecasts sha c61913e6…).
- **t81-widebank**: `--wide-credit true` (171c37d).
- **t82-widebank**: `--wide-credit bank` (70bceeb).

Year nodes, banking (`bnm.links --folds --kinds banking_crisis`; + = the run better; 90% intervals):

| fold | t81 vs t79-bis, Brier | t81 vs t79-bis, log | t82 vs t79-bis, Brier | t82 vs t79-bis, log |
|---|---|---|---|---|
| 2003-06 | +0.00009 [-0.00003, +0.00029] | +0.00193 [-0.00027, +0.00570] | +0.00008 [-0.00002, +0.00024] | +0.00167 [-0.00015, +0.00473] |
| 2007-09 | -0.00005 [-0.00092, +0.00081] | +0.00223 [-0.00215, +0.00757] | +0.00026 [-0.00063, +0.00172] | +0.00258 [-0.00219, +0.00962] |
| 2010-12 | -0.00054 [-0.00162, +0.00009] | -0.00126 [-0.00498, +0.00156] | -0.00020 [-0.00076, +0.00024] | -0.00070 [-0.00323, +0.00166] |
| 2013-19 | **-0.00013 [-0.00028, -0.00002]** | -0.00084 [-0.00212, +0.00017] | **-0.00010 [-0.00021, -0.00002]** | -0.00068 [-0.00163, +0.00013] |
| 2020-22 | **-0.00015 [-0.00042, -0.00000]** | -0.00124 [-0.00300, +0.00006] | **-0.00006 [-0.00014, -0.00000]** | -0.00076 [-0.00174, +0.00016] |
| pooled | -0.00014 [-0.00042, +0.00008] | +0.00003 [-0.00132, +0.00180] | -0.00002 [-0.00022, +0.00022] | +0.00025 [-0.00104, +0.00214] |

Against t71-stack, t82 pooled Brier -0.00001 [-0.00031, +0.00034], log -0.00004 [-0.00176, +0.00206], no fold significant (2013-19 log -0.00105 [-0.00234, +0.00007]). t82 against t81: every fold's Brier point estimate better, pooled +0.00012 [-0.00003, +0.00037]. Over all 13 kinds (`bnm.world-score`), t82 against t79-bis: Brier -0.00000 [-0.00002, +0.00002], log +0.00002 [-0.00009, +0.00015].

Half-decade country nodes (not in the fold rule; countries resampled, 400 reps):
- t81 vs t79-bis: Brier +0.00030 [-0.00021, +0.00092], log +0.00249 [+0.00061, +0.00468];
- t82 vs t79-bis: Brier +0.00008 [-0.00035, +0.00056], log +0.00107 [-0.00065, +0.00305]; vs t71-stack log +0.00421 [+0.00094, +0.00787].

Within-origin AUC, year node, December origins 2003-22:

| rows | t71-stack | t79-bis | t81 | t82 |
|---|---|---|---|---|
| all countries | 0.654 | 0.645 | 0.696 | 0.682 |
| JST | 0.587 | 0.583 | 0.581 | 0.581 |
| outside JST | 0.578 | 0.572 | 0.661 | 0.640 |
| outside JST with a BIS input (673 rows, 16 onsets) | 0.576 | 0.576 | 0.752 | 0.695 |

Half-decade, all countries: 0.447, 0.503, 0.558, 0.527.

Calibration, banking, country year node (rate; mean p and Brier for t71-stack, t79-bis, t81, t82):

| span | rate | mean p | Brier |
|---|---|---|---|
| 2003-22 | 0.032 | 0.045, 0.045, 0.046, 0.046 | 0.0322, 0.0322, 0.0324, 0.0322 |
| 2006 | 0.169 | 0.025, 0.025, 0.025, 0.026 | 0.1600, 0.1602, 0.1598, 0.1599 |
| 2007 | 0.146 | 0.022, 0.022, 0.022, 0.023 | 0.1385, 0.1387, 0.1384, 0.1385 |
| 2008 | 0.035 | 0.047, 0.047, 0.048, 0.048 | 0.0328, 0.0329, 0.0327, 0.0326 |
| 2010-12 | 0.016 | 0.070, 0.072, 0.073, 0.073 | 0.0192, 0.0195, 0.0200, 0.0197 |
| 2013-19 | 0.025 | 0.042, 0.043, 0.043, 0.043 | 0.0246, 0.0246, 0.0247, 0.0247 |
| 2020-22 | 0.000 | 0.030, 0.028, 0.029, 0.029 | 0.0014, 0.0008, 0.0010, 0.0009 |

The rows outside JST with a BIS input: rate 0.024 over 2003-22, mean p 0.044 (t79-bis) and 0.048 (t82); 2013-22, rate 0, mean p 0.038 to 0.041.

### Alerts at 4:1 (`bnm.alerts`)

- **Cost per row, banking:** t79-bis 0.132; t81 0.132, Δ -0.0004 [-0.0023, +0.0012]; t82 0.131, Δ **+0.0008 [+0.0000, +0.0024]** (cheaper; against t71-stack -0.0004 [-0.0032, +0.0027]). Pooled over kinds, t82 against t79-bis +0.0001 [+0.0000, +0.0002].
- **Hits** stay 0 of 82. False alarms 0.4% of quiet rows in t81 (Luxembourg 2010 at 0.379, 2015-21 at 0.10-0.15), 0.3% in t82.
- **The 2006 and 2007 episodes** stay missed: mean p 0.026 at a rate of 0.169, 0.023 at 0.146 (t82).
- **The world node** is unchanged (0.350).

### The 2006-08 onsets

Issued p, t79-bis → t81 / t82:
- outside the panel: Hungary 2006 0.024 → 0.033 / 0.032, 2007 0.021 → 0.026 / 0.025; Lithuania 2006 0.024 → 0.030 / 0.030, 2007 0.021 → 0.026 / 0.026; Luxembourg 2006 0.024 → 0.031 / 0.025, 2007 0.021 → 0.028 / 0.022; Austria 2006 0.024 → 0.027 / 0.029; Iceland 2006 0.024 → 0.026; Croatia 2006 0.024 → 0.025; Greece 2008 0.046 → 0.061 / 0.073. Latvia, Romania, Slovenia, Mongolia, Ukraine and Nigeria unchanged (no input).
- inside: Ireland 2006 0.066 → 0.065, Spain 2007 0.056, 2008 0.104, the USA 2006 0.038, Britain 2006 0.028: unchanged within 0.001 (the standardizer's pooling).

The inputs rank the boom countries up (Hungary's credit stretch +2.8 and gap z +2.6 at 2005, Lithuania's house stretch +3.7, Greece's credit stretch +2.1 to +2.3 and gap z +2.4 to +2.7 at 2005-06) by 10-60%, but from the same level of 0.02. As at the JST countries in t79-bis, the miss is the level, not the ranking.

### Why: the losses traced (PLAN principle 9)

1. **Luxembourg (t81): all-lender credit at a financial centre.** Of t81's 2010-22 loss against t79-bis, Luxembourg is the largest part: its rows lose 0.22 of the summed Brier over 2010-22, and without it 2020-22 goes from -0.00015 to -0.00003 and 2010-12 from -0.00054 to -0.00020. Its all-lender credit is 450% of GDP, mostly the intra-group and cross-border debt of holding companies, and rose from 209% to 346% in 2006-09; its three-year growth (0.81 in JST units) sat at the standardizer's cap of 5, where Spain 2007 is 4.6. Its bank credit is 66-108% of GDP. Verdict: **approach** (the series' concept), fixed by `--wide-credit bank`. It is the same non-bank debt that drove t79-bis's Irish false alarms (Ireland's bank credit falls from 169% in 2009 to 51% in 2015 while all-lender credit stays above 300%). With bank credit, Luxembourg's growth is 0.24 at 2009 and its 2010 p 0.189 (from 0.379); t82 is better than t81 on every fold's point estimate.
2. **2013-22 (t82: -0.00010, -0.00006): booms in the covered emerging markets that did not end in crises.** The largest losses are China (-0.030 summed Brier), Singapore, Luxembourg, Turkey, Thailand, Chile and Malaysia: credit stretches of +2 and more and growth in the top fifth, with no crisis in the catalogue. The rows with a BIS input have no onset in 2013-22 (0 of 370) while the countries without one have all 22. The BIS covers the larger, financially deeper economies, and after 2012 their crisis rate fell to zero while frontier and post-Soviet economies, which the BIS does not cover, had the crises. The node shares one level across countries, so positive credit evidence at the covered rows raises p exactly where the rate is lowest. Verdict: **approach** (coverage selects on a lower rate, and the shared coefficients carry JST's boom-to-crisis link to booms that did not break). A coverage indicator in the gauge set, so the node could learn the covered countries' own rate, is the obvious next step; separate coefficients for these rows would remove the 2006-08 ranking gain, which needs the JST coefficients from the start.
3. **2010-12 (t82: -0.00020, not significant): the denominator after the crisis.** At the December 2010 origin every country outside the panel stood at 0.098 in t79-bis; covered ones rise to 0.13-0.20 (Brazil 0.197, Singapore 0.174, China 0.153, Poland 0.153), where credit to GDP rose as GDP fell in 2009. Verdict: **approach**, t79-bis's loss 1 again.

The runs showed no bugs.

### Reproduction

- `--wide-credit false` (t81-off) gives t79-bis's banking files byte for byte.
- 2026-Q4-r1 reproduces byte for byte through `bnm.ledger/reproduce-issue!` at 70bceeb (8556 lines, forecasts sha bdb361c8…).
- The suite is green at 70bceeb: 1411 tests, 0 failures.

### Verdict

- **Fails the fold rule** in both forms: 2013-19 and 2020-22 lose on Brier against t79-bis (t82: -0.00010 [-0.00021, -0.00002], -0.00006 [-0.00014, -0.00000]), and no fold gains. Not an improvement under the acceptance rule.
- **The ranking improves where the inputs reach:** within-origin AUC outside JST with a BIS input 0.58 → 0.70 (t82; 0.75 in t81), all countries 0.645 → 0.682; half-decade log +0.0011 (t82, n.s.) and +0.0025 [+0.0006, +0.0047] (t81).
- **Alerts:** t82 is cheaper than t79-bis at 4:1 (+0.0008 [+0.0000, +0.0024]) through fewer false alarms, but catches nothing: 0 of 82 hits; 2006 and 2007 still missed.
- **The 2006-08 miss is still the level.** The new inputs lift Hungary, Lithuania, Greece and Austria by 10-60%, from 0.02. Most onsets outside the panel are in countries without BIS data.
- If credit beyond JST is wanted, `bank` is the form: JST's concept, no share guess, and better than all-lender credit at every fold. The option stays off by default.
## 18. Fresh inputs: t72-fresh (bnm-1d8)

§16 found that the growth-collapse year node's main model, `economy`, reads GDP per head and its three-year growth from Maddison at Maddison's releases (`--leak-fixes`), two to eight years behind the origin. WDI's growth per head is on disk and comes out within a year or two. This section audits every input for staleness, adds the fresher source where it measures the same quantity, and scores it.

### Audit

Age is months from the end of the data period to the December origin that reads it, at origins 2003-22: the median over countries and origins, with the range. The year row is bnm.political-gauges/year-row (every kind but banking crises; the half-decade reads six of its columns), the quarter rows bnm.named (political for conflict, wars, coups, protest, violence, growth, inflation and food; financial for currency, sovereign, fiscal and market crashes), the leading gauges bnm.named/leading. Session scratchpad: the ages from `nm/load-series` with `:real-lags` and from bnm.releases.

| kind (scale) | group | input | source as read | age read | fresher on-disk source | its age | adopted |
|---|---|---|---|---|---|---|---|
| all but banking (year, half-decade) | economy | log GDP per head, growth3 | Maddison at its releases | 48 [24-96] | WDI NY.GDP.PCAP.KD (series.tsv economy/gdp_per_capita), dated by the WDI archives | 12 (24 at Dec 2003-04) | **yes**, growth spliced |
| all but banking (year) | demography | population growth3 | Maddison at its releases | 48 [24-96] | WDI SP.POP.TOTL (people/population), archive-dated | 12 (24 at Dec 2003-04) | **yes**, growth spliced |
| all but banking (year) | demography | urban growth5 | NMC urban/total population at releases to 2016, WDI urban share after | 72 [36-228] to 2018, then 12 | WDI SP.URB.TOTL.IN.ZS | 12 | no: national urban definitions, not NMC's cities of 100,000 |
| all but banking (year) | military | soldiers per head, spending per soldier3 | NMC at its releases | 72 [36-228] | WDI MS.MIL.XPND.CD (spending only, raw, not in series.tsv) | 12 | no: no fresher personnel source on disk |
| all but banking (year) | regime, violence, society, patronage | V-Dem | V-Dem, data year Y-1 | 12 | none | | |
| all but banking (year) | demography | infant mortality, working-age share | WDI | 12 | none | | |
| all but banking (year) | demography | tertiary enrolment5 | WDI (UIS) | 24 | none | | |
| all but banking (year) | economy | inflation | WDI FP.CPI.TOTL.ZG (annual average) | 12 | BIS monthly CPI (62 units), FAOSTAT CPI (207) | 1, 9 | no: a twelve-month change at a month is not the annual average |
| all but banking (year) | economy | food inflation | FAOSTAT | 9 | none | | |
| all but banking (year) | inequality | top 10%, bottom 50% | WID at Y-2 | 24 | none | | |
| all but banking (year) | fiscal | real spending growth, balance change | GMD 2026_09 at Y-2 | 24 | none (the WEO files in data/raw/slow/fiscal are 403 pages) | | no |
| all but banking (year) | history, others, world | onsets | the catalogues at their known lag | the outcome's lag | | | |
| growth, market crashes, food (year, quarter-lead) | leading | BAA12, US curve, VIX12, world food12 | FRED, IMF via FRED (`--real-lags`) | 0-2 | none | | |
| political quarters | food, energy | food inflation, world food12, oil12, import shares | FAOSTAT, IMF, WDI | 9, 2, 0, 12 | none | | |
| political quarters | economy | GDP growth | WDI NY.GDP.MKTP.KD.ZG | 12 [12-216] | quarterly GDP (OECD QNA, IMF QNEA; docs/GROWTH-QUARTERLY.md) | 2-4, 7-22 for about 20 countries | no: a four-quarter change is another quantity, and outside the OECD there is no record of when each quarter came out |
| political quarters | economy | inflation | WDI FP.CPI.TOTL.ZG | 12 | BIS monthly CPI, FAOSTAT CPI | 1, 9 | no (as at the year) |
| political quarters | fragility, demography | infant mortality, youth unemployment, population | WDI | 12 | none | | |
| political quarters | climate | temperature, precipitation anomalies | series.tsv | 12 | none | | |
| political quarters | events | protest, violence, coercion | GDELT | 0 | | | |
| political quarters | political economy | profit rate, labour share | PWT at its releases (`--pwt-stale --lag-stale`) | 36 [24-48] | none | | |
| political quarters | political economy | growth per head5 | WDI | 12 | none | | |
| financial quarters | credit, assets, curve, global | BIS credit, house prices, debt service; share prices, yields, VIX, curve; WDI openness | BIS, OECD, FRED, WDI | 6, 6, 6; 1, 1, 0, 0; 12 | none | | |
| banking (year, half-decade) | macrohistory, extended | JST row of Y-1; PWT at releases; WID, GDD, MV, EPWT at Y-1 | JST R6, PWT, ... | 12 (JST, see below), PWT 36 [24-48] | none | | |

Only the two Maddison rows have a fresher source on disk that measures the same quantity. NMC is staler still (19 years at Dec 2003-04, when NMC 1.0's 1985 was the newest published), but no open personnel series is on disk to replace it.

Two lookahead findings came out of the audit (the class of bnm-a1e and bnm-56c.23):

1. **WDI at Dec 2003-04.** series.tsv dates every WDI year nine months after it ends. The WDI Database Archives (data/raw/wdi-archives) show that before 2005 a year first came out in the April edition sixteen months later: 2002 in April 2004, 2003 in April 2005. From September 2005 the September edition holds the year before, and nine months holds (2008 waited for October 2009, 2009 for December 2010), and from 2011 the June or July edition holds it. So at the Dec 2003 and Dec 2004 origins the WDI inputs are a year ahead of publication: 4,249 of the 4,906 WDI annual values series.tsv makes readable at Dec 2003 (4,261 of 4,941 at Dec 2004) were not out; none from Dec 2005. `--fresh-inputs` dates its two WDI series by the archives. The other WDI reads are fixed behind `--wdi-editions` (below), not scored here.
2. **JST in the banking node.** bnm.lattice-banking reads the Macrohistory row of the year before the origin at every origin. JST's first release was 2016. Unlike PWT, Maddison and NMC, JST has no release dating. Dating it would leave the banking node without its main gauges before 2016, a model change rather than a hygiene fix: bead bnm-9vm, for the user's decision.

### The fix

`bnm.world-run --fresh-inputs true` (off by default). The year row's log GDP per head, growth3 and population growth3 are carried past Maddison's read year by WDI's growth per head (NY.GDP.PCAP.KD, series.tsv economy/gdp_per_capita) and population (SP.POP.TOTL, people/population), through the newest WDI year published by the origin (bnm.political-gauges/splice):

- **Growth rates are spliced, not levels.** Each year after Maddison's read year adds WDI's log change for that year to Maddison's log level. The level stays in Maddison's 2011 PPP dollars; WDI's constant-2015-dollar level is never read. growth3 is the change over the three years to the newest spliced year, so it may span Maddison's years and WDI's. This is how the growth-collapse catalogue itself chains WDI on after Maddison (bnm.economic-events). Where Maddison has no value at its read year, or WDI nothing newer, the row is the base's.
- **Each WDI year at its true publication.** scripts/wdi-first-published.py dates every country's year by the first edition of the WDI Database Archives that holds it (data/derived/wdi-first-published.tsv, 42,273 rows; 125 editions, April 2001 to July 2026). bnm.named/load-series `:first-published` makes a value unreadable before that month; bnm.lattice-political/year-row-opts reads the values known at the origin. An edition is dated by the later of its id's and its label's month, so the dating errs late. A year no fetched edition holds is dated after the first edition four years on, or after the last edition. The editions before 2001 hold no values under these series codes, so a year before the earliest edition that does (2002 for growth per head, 2001 for population) is dated to the April edition two years on: the sixteen months the 2002-2004 editions show. The training years matter here: read at series.tsv's nine months, the 1960-2000 rows would have used each year's figures a year before they came out (t72-fresh-v1 did, see below).
- **What is read.** At Dec 2003 the newest WDI year is 2001 (the 2002 figures came out in April 2004), at Dec 2004 2002, and from Dec 2005 the year before the origin for 147-162 of the 169 countries with a Maddison anchor (the rest a year or more further back, where WDI lags). Maddison's read year is 2-8 years back (median 4). The 13 lattice countries without Maddison (BHS, BLZ, BRN, BTN, ERI, FJI, GUY, MDV, PNG, SLB, SOM, SUR, TLS) keep the base's missing value.

`--wdi-editions true` (off by default; lookahead finding 1) reads every WDI annual series (variables.tsv's source "World Bank WDI ...") no earlier than the first archived edition holding its year for 100 countries or more (data/derived/wdi-editions.tsv: years to 2000 in the April edition two years on, 2002 in April 2004, 2003 in April 2005, 2004 in September 2005, then each September to 2008 in October 2009, 2009 in December 2010, then June or July). The archive's inflation series (FP.CPI.TOTL.ZG, eleven editions 2004-10 checked) comes out in the same editions. It is not part of t72-fresh.

Tests:
- bnm.political-gauges-test/fresh-inputs-carry-maddison-on-with-wdi-growth: WDI known through 2013 or 2012 against Maddison's 2010; the level stays Maddison's; nothing newer, or no Maddison anchor, gives the base's row; only the three columns change. It fails on the code before the change (5 assertions).
- bnm.named-test/fresh-inputs-wdi-is-not-read-before-its-first-edition: a year is not readable before its first edition; a year the table lacks keeps series.tsv's date; the values are unchanged.
- bnm.lattice-political-test/fresh-inputs-the-year-row-reads-wdi-as-published-at-the-origin: at Dec 2008 a year first published in April 2009 is not read; off, no :fresh.
- bnm.named-test/wdi-editions-no-wdi-year-before-its-edition: WDI series only, by edition; other sources unchanged.
- bnm.world-run-test/fresh-inputs-option, wdi-editions-option: true, false or absent; anything else refused.

### Run: t72-fresh

t71-stack's command plus `--fresh-inputs true`, at 1ebec06 (data/runs/t72-fresh/README.txt), for the twelve kinds that read the year row. Banking crises read no year row (bnm.lattice-banking builds its own), and without relations (and without `--clim-prior kinds`) the kinds of a world run do not read each other, so banking_crisis/ is copied from t71-stack. Logs: data/runs/logs/run-t72-fresh.log, score-t72-*.txt, alerts-t72-vs-t71.txt.

**t72-fresh-v1, superseded.** The first run (d87e466, data/runs/t72-fresh-v1, logs `*-v1*`) dated only the years the archive holds; years to 2000 kept series.tsv's nine months, a year ahead of their publication. It scored +0.00045 [+0.00001, +0.00101] pooled against t71-stack, most of it market crashes (+0.0059 GAIN, 2003-06 +19.3 ×1000), and its growth node's economy model held 0.79-0.97 of the weight in 1985-95 against t71-stack's 0.37-0.88. With the training years dated (1ebec06) all of that goes: the year row in 1985-95 is then exactly t71-stack's (WDI's newest year at a December origin was the year before last, the year Maddison's fixed offset already read), the economy weights are identical to t71-stack's to 1999, and the market-crash gain is +0.00002. The v1 gain was the lookahead.

**Reproduction with the option off.**
- t71-stack's command for the same twelve kinds at 06b5f27 (t71-repro-fresh): forecasts, weights, preds and frailty identical to t71-stack's for every kind, 48 files (data/runs/logs/repro-t71-fresh.txt).
- **2026-Q4-r1** byte for byte through `bnm.ledger/reproduce-issue!` at d87e466, the tools code of t72-fresh, `--wdi-editions` included (data/runs/logs/repro-r1-fresh.log, 8556 lines, forecasts sha bdb361c8…).
- The suite is green: 1398 tests, 0 failures (data/runs/logs/suite-fresh2.log).

**Year nodes, Brier** (+ = t72-fresh better, 90% intervals resampling countries and years):

| fold | vs t71-stack | vs t53-noclim |
|---|---|---|
| 2003-06 | +0.0004 [-0.0003, +0.0011] | +0.0076 [+0.0048, +0.0103] |
| 2007-09 | +0.0003 [-0.0003, +0.0008] | +0.0167 [-0.0042, +0.0257] |
| 2010-12 | -0.0001 [-0.0003, +0.0002] | +0.0006 [-0.0031, +0.0038] |
| 2013-19 | -0.0002 [-0.0004, +0.0001] | +0.0071 [+0.0027, +0.0112] |
| 2020-22 | -0.0004 [-0.0009, +0.0001] | +0.0024 [-0.0036, +0.0075] |
| pooled | -0.00001 [-0.00026, +0.00026] | +0.0062 [+0.0034, +0.0093] GAIN |

Log pooled: +0.0001 [-0.0009, +0.0010] against t71-stack (2003-06 +0.0018 GAIN, 2020-22 -0.0023 [-0.0053, +0.0004]); +0.0178 [+0.0103, +0.0254] against t53-noclim (t71-stack's: +0.0177). **No fold's interval is below 0 against either bench**: the fold rule passes. On proper scores t72-fresh ties t71-stack.

Per kind against t71-stack (bnm.world-score; Brier, log), every interval spanning 0:
- growth collapses -0.00026 [-0.00087, +0.00038], log -0.0004 [-0.0036, +0.0032];
- currency crises -0.00005, log -0.0008 [-0.0027, +0.0008];
- inflation crises +0.00009, log +0.0012 [-0.0000, +0.0025]; sovereign defaults +0.00008, log +0.0005; violence surges +0.00005;
- food price shocks +0.00005, market crashes +0.00002; the rest within ±0.00002.

By fold (×1000; intervals in data/runs/logs/score-t72-kind-folds.txt; bold where the interval is wholly below 0):

| kind | 2003-06 | 2007-09 | 2010-12 | 2013-19 | 2020-22 |
|---|---|---|---|---|---|
| growth collapses | +1.33 | **-1.87** | +0.52 | **-0.95** | +0.27 |
| market crashes | +2.50 | -0.02 | -1.09 | -0.37 | **-1.42** |
| currency crises | +0.46 | -0.01 | +0.20 | -0.31 | -0.45 |
| fiscal consolidations | +0.04 | -0.05 | -0.06 | +0.01 | -0.10 |
| coups | +0.06 | +0.08 | -0.11 | +0.11 | **-0.35** |
| food price shocks | -0.00 | +5.12 | -0.18 | -0.48 | -3.27 |
| inflation crises | +0.19 | +0.04 | +0.04 | +0.13 | -0.05 |
| sovereign defaults | +0.27 | +0.16 | -0.02 | -0.00 | -0.00 |
| violence surges | +0.00 | +0.00 | +0.00 | -0.00 | +0.32 |

**The growth node** (score-t72-growth-diag.txt), t71-stack → t72-fresh:
- within-origin AUC 0.544 → 0.512 (17 origins); pooled 0.634 → 0.632;
- the spread of p within an origin falls: sd 0.011-0.014 → 0.002-0.004 at Dec 2006-08;
- mean p against the realized rate: +0.34 → +0.35 at the same origin, +0.56 → +0.53 four origins back. The level still lags by about four years;
- calibration: 2003-22 rate 0.118, mean p 0.092 → 0.090, Brier 0.0999 → 0.1002; 2007-08 p 0.119 → 0.117 (rate 0.219), Brier 0.1790 → 0.1816; 2018-19 p 0.138 in both (rate 0.580), Brier 0.4386 → 0.4403.

The fresh growth3 is a different input. Across the 2654 scored rows it correlates with Maddison's growth3 at 0.43; Greece at Dec 2012 reads -1.86 sd against +0.38. As a raw ranker its within-origin AUC is 0.51 (lower growth, higher risk) against Maddison's 0.45: 0.63-0.76 at Dec 2006-09, 0.27-0.42 at Dec 2010-15, where the recoveries after 2009 rank the other way.

**Alerts** (p ≥ 0.2, a miss costing 4; against t71-stack): pooled dcost +0.0033 [-0.0005, +0.0074], no kind's interval below 0. Market crashes +0.0263 [-0.0104, +0.0812] (false alarms 81.1% → 77.3%, hits 88.0% → 87.3%), food +0.0165, growth +0.0035 (hits 25.7% → 26.5%, false alarms 3.4% → 3.5%), fiscal +0.0020; conflict onsets -0.0030 [-0.0079, +0.0000], coups -0.0020. Episodes: the same 8 of 21 caught.

**The outliers** (mean p at the December origin, rate in brackets), t71-stack → t72-fresh:
- Growth: 2007 (0.235) 0.111 → 0.107; 2008 (0.202) 0.129 → 0.129; 2019 (0.571) 0.055 → 0.056. All missed.
- Food: 2020 (0.493) 0.153 → 0.133, missed; 2021 (0.500) 0.227 → 0.224, caught.
- Market crashes: 2006 (0.957) 0.382 → 0.371; 2007 0.542 → 0.543. Both caught.

### Losses traced (PLAN principle 9)

A replay of the growth kind with each run's options (session scratchpad fresh/replay.clj; its year forecasts equal both runs' at all 2864 rows, max |dp| 0) dumps the year country node's models at each December.

1. **Growth collapses 2007-09 (-1.87 ×1000) and 2013-19 (-0.95): the economy model loses its weight because the fresh growth3 ranks the waves with the sign history taught.** The economy model reads log GDP per head, growth3, inflation and food inflation. In both runs it learns growth3 with a positive slope, a boom before a collapse: +0.15-0.16 with Maddison's growth3, +0.11-0.13 with the fresh one to 2010. That slope ranked the late-1990s collapses well with either input: the raw within-origin AUC of growth3 (higher, riskier) is 0.77-0.86 with Maddison's at Dec 1996-2000 and 0.49-0.76 with the fresh one. At Dec 2001-02 it is 0.45 and 0.63 with Maddison's and 0.22 and 0.09 with the fresh one: the countries that collapsed next had grown slowly. These rows resolve 42 months on, so from Dec 2003 the economy model holds 0.40-0.50 of the year country node's weight against 0.71-0.90, and 0.07-0.23 at Dec 2006-09 against 0.44-0.78. The rest goes to the base and world models. At Dec 2007-08 the node therefore reads less of log GDP per head, which ranked the 2009 wave (rich countries collapsed): within-origin AUC 0.72-0.75 → 0.65-0.67, sd(p) 0.011-0.013 → 0.002-0.003. The fresh growth3 ranks 2006-08 the wrong way for the learned slope too (0.24-0.37), while Maddison's 2001 figures were neutral (0.51-0.52). After the 2009 rows resolve the slope falls to +0.06-0.08 (Maddison's: +0.13-0.17), and at Dec 2017-21 the economy model holds 0.16-0.91 against 0.99-1.00, so the 2018-19 ranking reads less of the level too. Neither is a bug: the sign of recent growth before a collapse differs between the 1990s waves and 2009, and Maddison's stale growth3, nearly constant within an origin for years, carried no signal that could be wrong. The level the node issues is about the same (0.107 and 0.129 at Dec 2007-08, against 0.111 and 0.129).
2. **Market crashes 2020-22 (-1.42).** The year country node's `down` model (the coarser scales and the region and world year nodes one origin back, whose gauges are means of the same year rows) holds 0-0.09 at Dec 2017-22 against 0.06-0.20, and `fiscal` 0.77-0.93 against 0.68-0.90. `fiscal` carries the VIX12 false alarms after 2020 (§17 loss 1): mean p 0.411 → 0.416 at Dec 2020 and 0.216 → 0.220 at Dec 2021, at rates of 0.081 and 0.085. The averaging's choice moved with the inputs; no bug.
3. **Coups 2020-22 (-0.35).** The all-inputs model holds the node in both runs; its growth3 and population-growth3 coefficients (and the tensions on growth3) read the new columns. Mean p at Dec 2021 0.050 → 0.052 at a rate of 0.065, AUC 0.71 → 0.70. Small; no bug.

Bugs found in this work:
- **The fresh inputs' training years** (t72-fresh-v1, above): fixed in 1ebec06 before the scores reported here.
- **WDI at Dec 2003-04** (lookahead finding 1): every WDI annual value series.tsv makes readable at those origins came out a year later. Fixed behind `--wdi-editions true` (70e9011), not scored. The same nine months also date WDI's training years before 2001, where the archive shows sixteen; `--wdi-editions` dates them too.
- **JST in the banking node** (lookahead finding 2): bead bnm-9vm, for the user's decision.

### Verdict

- **The audit:** of every input of every kind, only the year row's GDP per head, growth3 and population growth have a fresher open source on disk that measures the same quantity. Maddison is read 24-96 months old (median 48); WDI's growth per head and population, dated by the WDI Database Archives, 12 months from Dec 2005 and 24 at Dec 2003-04.
- **The fix** (`--fresh-inputs true`, off by default) reads them, with growth rates spliced on Maddison's level and each WDI year at its first archived edition. The base and the ledger reproduce byte for byte with it off.
- **On the world it ties t71-stack:** pooled Brier -0.00001 [-0.00026, +0.00026], log +0.0001, no fold lost against t71-stack or t53-noclim, alerts +0.0033 [-0.0005, +0.0074], the same eight episodes caught.
- **It does not make the growth node discriminate.** Within-origin AUC 0.544 → 0.512; the level still lags the waves by about four years. A current growth input does not help a model whose slope was learned on one kind of wave and is applied to another. The growth collapses that matter (2009, 2020) were not visible in the previous year's annual accounts.
- **The lookaheads it uncovered matter more than the staleness.** WDI read at nine months before 2005 is a year early; in the fresh inputs' training years that lookahead alone made a pooled GAIN (t72-fresh-v1). `--wdi-editions` fixes it for every WDI series and should be scored before any base adopts a WDI input change.
- **Adoption** is the user's decision. Every option stays off by default.

## 18. Issuing next-quarter scale: t73-nq (bnm-56c.38)

No issuing scale covered (t, t+3]. The year is (t, t+24], quarter-lead (t+3, t+12], quarter-w2 (t+12, t+24], and quarter-now (step 4a) issues nothing. An onset in the next quarter could only be targeted from origins at least three months before it, before the gauges that lead it had moved.

### Design

docs/RECURSIVE.md step 4b, committed before the code and before any run. In short:
- **`:quarter-next`.** Window (t, t+3], issued at every quarter end. It has its own models and averaging over the quarter-lead's input groups, trial 41's quarter design, its own book rows, errors, weights and frailty. It is written as scale `quarter-next`.
- **A leaf (`:leaf true` in bnm.lattice).** No other node reads it. It is not in the sheaf's base, so the year's `:up` group does not grow and every other row is unchanged byte for byte. It replaces the frailty-only quarter-now (same window and known-at); `--next-quarter` and `--fresh-frailty` are refused together.
- **Its outcome.** The scale reads its own catalogue:
  - food price shocks by month (lag 9);
  - growth collapses by quarter (the GROWTH-QUARTERLY catalogue, merged from origin/growth-q; lag 5, 103 covered units);
  - the other kinds on the run's catalogue.

  Under `--at-risk catalogue` each carries its rule: monthly food grid [1 0], refractory 12; quarterly growth grid [3 2], refractory 60. The other scales keep the run's catalogues, so t71's targets are unchanged.
- **Kinds.** Those whose scale catalogue is sub-annual: coups, protest and violence surges, market crashes, food and growth. Not banking, conflict, war or the annual economic kinds.
- **Known-at.** Window end plus the catalogue's lag: t+6 at market crashes (and coups, protest, violence), t+8 at growth, t+12 at food. Nothing reads past t (planted test).
- **Offset.** The year's forecast carried down to the quarter, pooled 0.5 with named's w1 carried to the quarter. For food, market crashes and growth, r7-at-fx has no named w1 forecast (nor a lead one). The offset is therefore the year's alone, as at their quarter-lead.
- **Frailty.** With `--frailty-gauges true`, the world gauges the scale reads that carry a theory prior set its shock's mean, at the year's priors:
  - market crashes `[:us-curve :baa12 :vix12]`;
  - growth `[:baa12 :us-curve :vix12]`;
  - food `[:world-food12]`.

Code:
- bnm.lattice `:leaf`, and bnm.sheaf/base;
- bnm.lattice-political `quarter-next`, `scales-of`, `known-log-odds`, `target-fn`;
- bnm.world-run `--next-quarter`, `nq-catalogues`, `nq-kinds`, `design-for`;
- bnm.alerts `--scale quarter-next`;
- bnm.nq-score.

### Tests

- **lattice_test bnm-56c-38-*.** The planted world has 100 places. A world gauge read at each quarter end is noise (sd 0.4), or +2.5 at six March origins, and moves the next quarter's monthly log odds by 1.2 times its value. The realized rate is 0.21-0.35 at the episodes, against about 0.02 otherwise.
  - The next-quarter scale issues at least 0.2 at every episode origin. The quarter-lead and the year issued three months earlier, whose windows hold the same quarter, imply under 0.08 for it. Calm quarters average under 0.06.
  - The leaf leaves every other scale's forecasts, book, weights and frailty as they are without it. The same scale without the leaf mark moves the year.
  - No lookahead: events redrawn after a cut change nothing issued by it.
  - A scale cannot be both a leaf and frailty-only.
- **lattice_political_test.** The scale and its place in the order; the w1 offset carried to the quarter; the target read from the scale's own catalogue at its own lag.
- **world_run_test.**
  - The option, and its refusal beside `--fresh-frailty`.
  - The scale's catalogues, kinds and at-risk rules.
  - Its design.
  - Its frailty gauges: only those with a theory prior (world food12 has none at growth).
- **nq_score_test.** The causal climatology, the quarter-lead's implied hazard, the lags, and the alert layer reading the scale at every origin.
- **Bug fixed on the way.** Merging origin/growth-q dropped `--growth-outcomes` from `run-catalogues` (structure had moved catalogue building there), so the option was silently ignored. Separately, `--at-risk catalogue` gave the quarterly growth catalogue the annual rule (a January grid). Both are fixed, with tests (bnm-56c-38-run-catalogues-reads-growth-outcomes, -at-risk-knows-the-quarterly-growth-catalogue).
- **Suite.** Green: 1411 tests, 0 failures (data/runs/logs/suite-t73.log).
- **Reproduction.**
  - t73-nq against t71-stack, for food, growth and market crashes: forecasts.tsv, weights.tsv and frailty.tsv are byte-identical once the quarter-next lines are dropped, and preds.tsv is byte-identical (data/runs/logs/repro-t73-nq.txt).
  - 2026-Q4-r1 reproduces byte for byte through `bnm.ledger/reproduce-issue!` (data/runs/logs/repro-r1-nq.log; forecasts sha bdb361c8…). That run builds all 13 kinds through the refactored scale, target and offset code with the option off.
  - t71-stack was not rerun in full with the option off. Its three changed kinds are covered by t73-nq's identity above, and the other ten by the 2026-Q4-r1 reproduction's code path.

### Run

t73-nq = t71-stack's command + `--next-quarter true`, at b05d3e1, for food, growth collapses and market crashes (15 minutes). The other ten kinds are copied from t71-stack, since they are independent under `--without-relations true`. Coups, protest and violence would get the scale but were not run (data/runs/t73-nq/README.txt).

**Year nodes against t71-stack:**
- +0.00000 in every fold, Brier and log (score-t73-folds-vs-t71-stack.txt).
- Year alerts identical: cost 0.298, 8 of 21 episodes caught (alerts-t73-vs-t71-stack.txt).
- The fold rule holds trivially: the scale adds rows and changes none.

### The scale's own scores

Country nodes, quarter-end origins 2003-22, resolved rows (score-t73-nq.txt). Brier and log differences, + = the next quarter's loss is lower; 90% intervals resampling countries and years.

| kind | rows | onsets | mean p | Brier | clim | ql-imp | Brier vs clim | log vs clim | Brier vs ql-imp | log vs ql-imp |
|---|---|---|---|---|---|---|---|---|---|---|
| food | 11142 | 264 | 0.025 | 0.0250 | 0.0360 | 0.0243 | +0.0110 [+0.0002, +0.0300] | +0.0258 [+0.0005, +0.0675] | -0.0007 [-0.0029, +0.0007] | +0.0004 [-0.0085, +0.0081] |
| growth | 4659 | 136 | 0.017 | 0.0285 | 0.0286 | 0.0283 | +0.0002 [-0.0010, +0.0017] | +0.0009 [-0.0220, +0.0229] | -0.0002 [-0.0021, +0.0017] | -0.0047 [-0.0493, +0.0347] |
| market crashes | 3156 | 79 | 0.036 | 0.0223 | 0.0247 | 0.0238 | +0.0024 [-0.0011, +0.0077] | +0.0218 [-0.0013, +0.0553] | +0.0015 [-0.0009, +0.0053] | +0.0085 [-0.0043, +0.0272] |
| pooled (kinds equal) | | | | | | | +0.0045 [+0.0005, +0.0107] | +0.0162 [-0.0004, +0.0376] | +0.0002 [-0.0013, +0.0019] | +0.0014 [-0.0158, +0.0194] |

- **The references.**
  - clim: the kind's rate among the scale's country rows resolved by t.
  - ql-imp: the quarter-lead's forecast at t-3 for (t, t+9], carried to (t, t+3] as 1 - (1 - p)^(1/3) and scored on the scale's outcome. For food and growth the quarter-lead's own target is a different catalogue.
- **The food gain over climatology is 2004 alone.** From 2005 (score-t73-nq-from2005.txt):
  - food: +0.0002 [-0.0001, +0.0006] against climatology, +0.0005 [-0.0001, +0.0013] against ql-imp;
  - pooled: +0.0010 [-0.0005, +0.0035] and +0.0007 [-0.0008, +0.0025].

  In 2004 both the node and the climatology start uninformed (loss 1).
- **Market crashes** are the one kind with an edge, and it is not significant: +0.0024 Brier and +0.022 log against climatology.

Alerts at 4:1 (p ≥ 0.2), cost per row (alerts-t73-nq-quarter-next.txt, score-t73-nq.txt):

| kind | next quarter (hit / FA) | clim | ql-imp | dcost vs clim | dcost vs ql-imp |
|---|---|---|---|---|---|
| food | 0.118 (2.3% / 2.6%) | 0.144 | 0.095 | +0.026 [+0.000, +0.065] | -0.023 [-0.073, +0.001] |
| growth | 0.122 (2.2% / 0.8%) | 0.117 | 0.117 | -0.005 [-0.014, +0.000] | -0.005 [-0.014, +0.000] |
| market crashes | 0.092 (15.2% / 0.7%) | 0.100 | 0.102 | +0.009 [-0.006, +0.035] | +0.010 [-0.003, +0.036] |
| pooled | | | | +0.010 [-0.002, +0.023] | -0.006 [-0.025, +0.005] |

**Episodes.** There are 26 quarter-end origins with a rate at least twice the kind's; 5 of them have a rate of at least 0.2. None is caught.

**Where the alerts fire** (rows alerted, onsets among them):

| kind | origin | alerts | onsets | note |
|---|---|---|---|---|
| food | Jun and Sep 2004 | 293 | 6 | loss 1 |
| growth | Dec 2008 | 23 | 3 | |
| growth | Dec 2020 | 16 | 0 | loss 4 |
| market crashes | Mar and Jun 2008 | 23 | 11 | |
| market crashes | 2009 | 7 | 0 | |

**The outlier quarters** (rows, realized rate, mean p issued; caught at 0.2):

| kind | origin | rows | rate | next quarter | clim | ql-imp |
|---|---|---|---|---|---|---|
| food | Sep 2021 | 146 | 0.048 | 0.017 | 0.018 | 0.010 |
| | Dec 2021 | 142 | 0.099 | 0.014 | 0.018 | 0.013 |
| | Mar 2022 | 130 | 0.215 | 0.023 | 0.018 | 0.022 |
| | Jun 2022 | 102 | 0.137 | 0.028 | 0.018 | 0.026 |
| market crashes | Jun 2007 | 47 | 0.000 | 0.036 | 0.037 | 0.054 |
| | Sep 2007 | 47 | 0.000 | 0.054 | 0.036 | 0.047 |
| | Dec 2007 | 47 | 0.128 | 0.082 | 0.036 | 0.049 |
| | Mar 2008 | 41 | 0.146 | 0.190 | 0.036 | 0.048 |
| | Jun 2008 | 35 | 0.429 | 0.187 | 0.037 | 0.071 |
| | Sep 2008 | 20 | 0.850 | 0.150 | 0.038 | 0.076 |
| | Dec 2008 | 3 | 0.333 | 0.362 caught | 0.041 | 0.067 |
| growth | Sep 2008 | 66 | 0.136 | 0.018 | 0.011 | 0.005 |
| | Dec 2008 | 57 | 0.298 | 0.184 | 0.011 | 0.004 |
| | Mar 2009 | 40 | 0.125 | 0.124 | 0.011 | 0.004 |
| | Dec 2019 | 81 | 0.136 | 0.021 | 0.013 | 0.029 |
| | Mar 2020 | 71 | 0.831 | 0.007 | 0.013 | 0.031 |
| | Jun 2020 | 15 | 0.133 | 0.004 | 0.013 | 0.007 |

Where its gauges move, the scale moves the right way:
- market crashes in 2008: four to five times climatology, and 2.5 times the quarter-lead's implied hazard;
- growth at Dec 2008: 0.184, against 0.004 for the quarter-lead.

It reaches 0.2 nowhere that matters.

### Why: the losses traced (PLAN principle 9)

β at the country nodes (frailty.tsv; the gauges in the order listed above):

| kind | gauge | prior | learned 1999-2023 |
|---|---|---|---|
| market crashes | `:us-curve` | -0.4 | +0.03 to +0.11 |
| | `:baa12` | +0.5 | +0.05 (1999), +0.19 to +0.28 (2002-23) |
| | `:vix12` | +0.25 | -0.05 to -0.12 |
| growth | `:baa12` | +0.4 | +0.09 to +0.32 |
| | `:us-curve` | -0.3 | -0.14 (1999) to +0.10 (2023) |
| | `:vix12` | +0.3 | +0.07 (1999), -0.02 to -0.10 (2008-19), +0.29 (2020-23) |
| food | `:world-food12` | +0.3 | +0.21 to +0.42 |

1. **Food 2004: the uninformed start.**
   - Food's monthly catalogue covers places from January 2004. Until the node's first row resolves, twelve months later, it issues an even 0.5, and so does the causal climatology.
   - That gives 293 alerts at Jun and Sep 2004, for 6 onsets.
   - This is bnm-56c.26's uninformed start. `--clim-prior kinds` addresses it, but the base does not run that option.
   - It accounts for all of food's Brier gain over climatology and its alert loss against the quarter-lead.
   - Not a bug of the scale. Not changed, since the base's command is fixed.
2. **Growth Mar 2020 (rate 0.831, issued 0.007): a carried calm, and gauges that had learned nothing.**
   - Rows are known 8 months on, so at Mar 2020 the last filtered origin was Jun 2019. There z = -1.39 at ρ = 0.95 (σ = 1.0, the grid's edge, bnm-02u): the 2015-19 calm, carried. Run forward it is -1.19, less the half variance 0.5.
   - The gauges read VIX12 +4.0 sd, BAA12 +1.6 and the curve -1.1. But β had moved to [0.28, 0.03, -0.02], so β·g = +0.34.
   - The shock issued was about -1.35, which puts the forecast below climatology.
   - The VIX's β fell from its prior +0.3 to about 0 over 2008-19. There a high 12-month VIX change mostly followed collapses already counted, at units that were then busy or refractory.
   - Even at the theory priors with z = 0, the issue would have been about 0.065. One precedent (2008-09) in the gauges' span cannot support a +5 log-odds move.
   - **Approach and information, not a bug.** The carried calm is the year's mechanism (sections 7 and 8), now at three months.
3. **Market crashes 2008 (rate 0.43-0.85 at Jun-Sep 2008, issued 0.15-0.19): theory priors that the 1980s-90s quarters overturned.**
   - By 1999 the next quarter's VIX12 β was -0.07 and the curve's +0.05, against priors of +0.25 and -0.4. Only BAA12 kept its sign, at half its prior.
   - At Sep 2008 BAA12 read +3.1 sd (β·g +0.65), and the node issued about four times climatology.
   - The curve's sign is a horizon mismatch. Inversion leads recessions and bear markets by 2-6 quarters (LEADING.md "Priors"). At a three-month horizon the curve is already steep again, with the Fed cutting, when the crashes come. The year's prior does not fit this horizon.
   - **Approach.** Step 4b fixed the priors for the year and took them unchanged; a horizon-specific prior chosen after these scores would be post hoc. Recorded, not changed.
4. **Growth Dec 2020 (16 alerts, no onset): a carried shock.**
   - The Mar 2020 origin's rows, known in Nov 2020, filtered z to +3.4, and the grid moved to ρ = 0.8.
   - At Dec 2020 that state was run forward three quarters (+1.7, less 0.5), at the units still at risk, which did not collapse.
   - This mirrors loss 2: an AR(1) shock carries a one-quarter wave forward. Approach.
5. **Food 2022 (rate 0.10-0.22, issued 0.014-0.028): the information limit LEADING.md found.**
   - World food12 read +1.4 to +2.6 sd from Sep 2021 to Mar 2022. At β 0.30-0.35 that is +0.4 to +0.8, about doubling a 0.018 base.
   - Food's own z was 12 months stale (lag 9 + 3) and read -0.24 to +0.32 when issued from Sep 2021 to Mar 2022.
   - The 2022 surge followed the invasion of February 2022. No gauge read at Dec 2021 or Mar 2022 supports a tenfold move.
   - **Information limit.**

**Bugs found:** only the growth-q merge's, fixed above. None in the runs:
- the leaf reproduces t71-stack exactly;
- the β and z read from frailty.tsv account for each issue;
- the gauge columns are read at t.

### Verdict

- **The scale works as designed.**
  - In the planted world it issues the gauge-announced shock that the coarser scales cannot.
  - On the world it changes no row of any other scale, so the fold rule is met with zero change.
  - It issues for the quarter at hand.
- **On its own rows it is level** with a causal climatology and with the quarter-lead's implied hazard, once the 2004 uninformed start is set aside. From 2005, pooled Brier is +0.0010 [-0.0005, +0.0035] and +0.0007 [-0.0008, +0.0025]. Market crashes are the only kind with an edge, and it is not significant.
- **No outlier quarter is caught** except Dec 2008 at market crashes (3 rows). The 2008 crash, growth at Dec 2008 and growth at Mar 2020 move the right way, but far short of 0.2.
- **What limits it:**
  - z carried at ρ 0.95 (growth) through calms and waves (losses 2 and 4);
  - theory priors set for a 24-month window, overturned at three months for the curve and the VIX (loss 3);
  - the size of 2020 and 2022, beyond anything one or two precedents can teach (losses 2 and 5).
- **Where to act next.** This scale is where a horizon-specific prior, or a regime state that switches on the gauges (step 5), would act. By itself it does not reach the episodes.
- **Not merged into structure.** Adoption is the user's decision, and the option stays off by default.

### Horizon priors: t76-nq-hp (in-sample for the choice)

The user allowed priors specific to the next quarter, on condition that they are flagged as in-sample: they were chosen after seeing 2008, 2020 and 2022 at the three-month horizon in t73-nq. They are in docs/LEADING.md "Priors at the next quarter", committed (653221a) before the code and the run:
- the curve centred at 0 at both kinds (its 2-6 quarter lead);
- VIX12 +0.4 at market crashes (clustering) and +0.5 at growth (Bloom's 4-6 month trough);
- BAA12 kept;
- world food12 +0.2 and food12 × imports +0.35 (a quarter's share of a year's pass-through).

Every 2003-22 number below is in-sample for that choice.

- **Code.** `bnm.lattice-political/horizon-priors` and `named-prior-for`; `world-run --horizon-priors true`, which needs `--next-quarter true` and `--leading-priors true` (4e54ae1). Only the next-quarter scale reads them, in its models and its frailty's β. Off by default.
- **Tests.** The priors, the scales they reach, the option and its refusals (lattice_political_test, world_run_test bnm-56c-38-horizon-priors-*).
- **Run.** t76-nq-hp = t73-nq's command + `--horizon-priors true`, the same three kinds; the other ten copied from t71-stack.
- **Reproduction.**
  - Without its quarter-next lines, every file of the three kinds is byte-identical to t71-stack (repro-t76-nq-hp.txt).
  - Year folds against t71-stack are +0.00000 (score-t76-folds-vs-t71-stack.txt).
  - 2026-Q4-r1 reproduces byte for byte at 4e54ae1 (repro-r1-nq-hp.log, sha bdb361c8…).

**Against t73-nq**, on the scale's rows (score-t76-vs-t73-quarter-next.txt; + = t76's loss lower):

| kind | Brier | log |
|---|---|---|
| food | +0.00003 [-0.00003, +0.00010] | -0.00011 [-0.00080, +0.00061] |
| growth | +0.00003 [-0.00019, +0.00031] | +0.00231 [-0.00034, +0.00632] |
| market crashes | +0.00016 [-0.00008, +0.00050] | +0.00073 [-0.00037, +0.00214] |
| pooled | +0.00007 [-0.00005, +0.00021] | +0.00098 [-0.00019, +0.00226] |

**Against the references** (score-t76-nq-hp.txt and -from2005.txt):
- 2003-22, against climatology: Brier +0.0046 [+0.0005, +0.0108], log +0.0172 [+0.0006, +0.0397]. As in t73, both rest on the 2004 uninformed start.
- From 2005: +0.0011 [-0.0006, +0.0038] against climatology and +0.0007 [-0.0009, +0.0028] against ql-imp, pooled.
- Market crashes: +0.0029 [-0.0011, +0.0093] against climatology from 2005, the edge a little larger and still not significant.

**Alerts at 4:1** (alerts-t76-nq-hp-quarter-next.txt):

| | t76-nq-hp | t73-nq |
|---|---|---|
| cost, pooled | 0.109 | 0.111 |
| dcost against t73-nq | +0.0014 [-0.0005, +0.0052] | |
| market crashes: cost (hits) | 0.088 (22.8%) | 0.092 (15.2%) |
| growth: cost (hits) | 0.121 (4.4%) | 0.122 (2.2%) |
| food: cost | 0.119 | 0.118 |
| episodes caught, of 26 | 3 | 0 |

The three episodes caught are market crashes Mar 2008 and Jun 2008, and growth Dec 2008. Against climatology and ql-imp from 2005 the alert cost is level: +0.003 [-0.004, +0.016] and +0.003 [-0.003, +0.017].

**Outlier quarters**, mean p in t76-nq-hp (t73-nq) / rate:

| kind | origin | t76 (t73) | rate |
|---|---|---|---|
| market crashes | Dec 2007 | 0.094 (0.082) | 0.128 |
| | Mar 2008 | **0.214 caught** (0.190) | 0.146 |
| | Jun 2008 | **0.202 caught** (0.187) | 0.429 |
| | Sep 2008 | 0.162 (0.150) | 0.850 |
| | Dec 2008 | 0.386 caught (0.362) | 0.333 |
| growth | Dec 2008 | **0.222 caught** (0.184) | 0.298 |
| | Mar 2009 | 0.150 (0.124) | 0.125 |
| | Mar 2020 | 0.008 (0.007) | 0.831 |
| | Jun 2020 | 0.005 (0.004) | 0.133 |
| food | Sep 2021-Jun 2022 | 0.014-0.027 (0.014-0.028) | 0.05-0.22 |

**Why so little: the data overturned the new priors as they did the old** (frailty.tsv, β at the country nodes):

| kind | gauge | horizon prior | learned 1999 | learned 2005-19 |
|---|---|---|---|---|
| market crashes | `:us-curve` | 0.0 | +0.22 | +0.19 to +0.25 |
| | `:baa12` | +0.5 | +0.05 | +0.18 to +0.26 |
| | `:vix12` | +0.4 | +0.03 | +0.08 to -0.08 |
| growth | `:baa12` | +0.4 | +0.11 | +0.13 to +0.29 |
| | `:us-curve` | 0.0 | +0.03 | +0.06 to +0.15 |
| | `:vix12` | +0.5 | +0.19 | +0.13 to -0.04 (+0.33 after 2020) |
| food | `:world-food12` | +0.2 | +0.22 | +0.21 to +0.40 |

1. **The curve.** From 0 the market-crash β went straight to +0.2: a steep curve goes with a crash in the next quarter. This is the re-steepening before and in bear markets that motivated the 0 prior, and the data give it a sign. At growth the curve's β stays small. The 0 prior removed t73's pull in the wrong direction, so the curve now adds to the 2008 level: at Mar and Jun 2008 it read +0.5 sd.
2. **The VIX.** Theory's short-horizon prior (+0.4, +0.5) is overturned again: β was about 0 at both kinds by 2005-08. A high 12-month VIX change comes mostly after a crash or a collapse has happened, when the units that crashed or collapsed are busy or refractory and leave the at-risk set (as in t73, loss 2). Within the at-risk set the gauge then reads high in quiet quarters, such as 2002, 2009 Q2-Q4 and 2011. The 12-month change is the wrong transform at this horizon: it measures the crash already under way. The prior cannot fix that, and is overturned within a decade of quarters. A level or 3-month change read before the event (LEADING.md "Growth 2020") would be a different gauge. That is recorded, not built: choosing it now would be a second in-sample choice.
3. **Growth Mar 2020 still 0.008.**
   - At Jun 2019, the last origin known by Mar 2020: β = [0.26, 0.13, 0.03] and z = -1.31 at ρ 0.95.
   - The gauges then read BAA12 +1.58, the curve -1.07 and VIX12 +4.01, so β·g is about +0.41 - 0.14 + 0.14 = +0.41. The carried calm takes it back below climatology.
   - With β at the horizon priors and z = 0, the issue would have been about 0.1. The prior was gone before 2020.
4. **Food.** World food12's β stayed near its prior (+0.21 to +0.40), so lowering the prior changed little (Brier +0.00003). 2022 is the same information limit as in t73 (loss 5).

**Bugs:** none. The option touches only the next quarter, its β start where LEADING.md says (market crashes and growth: the first filtered rows of frailty.tsv hold exactly the priors, before the gauges have history), and the other scales reproduce.

**Verdict on the horizon priors.**
- In-sample, they move the scale the intended way at 2008: three episodes caught against none, market-crash hits 15% → 23%, Brier and log a hair better. None of these is significant.
- They do not reach Mar 2020 or 2022.
- Given that they were chosen after seeing these episodes, and that the gain is within noise even in-sample, they are no evidence for the scale.
- The limit is the gauge's transform and the carried calm, not the prior: sd 0.25 lets twenty years of quarters overturn any prior on VIX12.
- Not adopted. The option stays off by default.
## Delay test on the base of record: t86 (bnm-pjg.6)

Written 18 November 2026, before any run. The s2 test (docs/PHASE-CHANGES.md, "Round 4's delay test") delayed t50's year rows; the base has since adopted the leak fixes, the leading gauges at the quarter-lead and year nodes (`--leading`, `--leading-priors`, `--lead-std history`) and those gauges in the frailty mean (`--frailty-gauges`). This tests the base's adopted groups as they stand.

Runs, each t80-base's command (data/runs/t80-base/README.txt) with one change, thirteen kinds, one at a time:
- **t86-ldelay**: `--lead-delay 3`, every leading gauge read three months late at every node and in the frailty mean (bnm.lattice-political/delayed-lead).
- **t86-nolead**: without `--leading`, `--leading-priors`, `--lead-std` and `--frailty-gauges` (with no leading gauge the frailty mean has none to read), the reference for the leading gauges' gain.
- **t86-ydelay**: `--year-delay 1`, the political and economic kinds' year rows a year late (banking's own table not delayed, as in s2).

Scores (bnm.links): `--runs` against the bench for the pooled year and quarter-lead nodes, `--folds` for the year nodes by fold, and the alert table.

Rule, fixed now:
1. **Leading gauges.** G = t80-base against t86-nolead, G_d = t86-ldelay against t86-nolead, pooled log, year and quarter-lead nodes each. If G's interval does not lie above zero at a scale, the gauges have no gain there to test, and that is the result at that scale. Where it does, the gain survives the delay if G_d ≥ G/2 and t86-ldelay loses no fold against t80-base with a 90% interval below zero. Otherwise the gain rests on the latest quarter and is flagged for review; nothing is removed without the user's decision.
2. **Year rows.** No run without the year gauges exists for this base, so t86-ydelay is read against t80-base alone: a pooled log loss with its interval below zero, or a fold lost that way, means the year gauges' gain rests on the latest year's row, as s2 found for t50. The leak fixes date Maddison, NMC and PWT by release, so a loss here no longer points to those sources.

## Banking's frailty out of phase: t88 (bnm-g5t.29)

Written 18 November 2026, before any run.

**The finding.** t80-base's banking year country node has a pooled AUC of 0.371 over 2003-2022 (82 onsets in 2558 rows), below chance, while its AUC within each December origin is 0.641. The ranking within a year is moderate; the level across years runs against the rate. It dates from the frailty: t53-noclim, without it, has a pooled AUC of 0.553 (within-year 0.625); t57-frailty, the first run with it, 0.374 (0.593), and every base since keeps that (t65-gauges 0.379, t71-stack 0.379, t80-base 0.371). The year node's frailty takes ρ 0.8, sd 1.0 at every origin from 2000. Its filtered state by origin: -1.3 to -1.6 at 2003-04, +1.6 at 2006-07, -0.5 to -1.1 at 2010-11. Each is known about two years after its origin, so the state issued at Dec 2006 carries 2003-04's calm and the one issued at 2009-11 carries the wave: with a lag near the waves' spacing, the persistent shock is in anti-phase. Mean issued p is 0.025 at Dec 2006 (rate 0.169) and IRL, DNK, SWE and BEL issue 0.20-0.29 at Dec 2009-10, when few new crises began.

Checked and not the cause: crises under way. data/slow/events.tsv has no episode durations (its fifth column is the latest month an onset could be dated), so banking's at-risk set drops only a crisis's onset quarter. With the ESRB's end-of-crisis-management dates and Laeven-Valencia's end years, 162 of the 2558 rows are mid-crisis; without them t80-base's pooled AUC is 0.352 and within-year 0.628. A gap in the target, recorded (bnm bead to follow), not the inversion.

Runs, banking only (`--kinds banking_crisis`; the other twelve kinds copied from t80-base, independent under `--without-relations`), at the integrate branch, one at a time:
- **t88-off**: t80-base's command. Its banking forecasts must equal t80-base's byte for byte (the off path of every option merged into integrate).
- **t88-rho0**: the same with banking's frailty at ρ 0 only (`:frailty {:rhos [0.0]}`): a common shock with no persistence carried across origins.
- **t88-nofrail**: the same without a frailty at banking (`:frailty nil`).

Rule, fixed now: against t80-base, banking year nodes, `bnm.links --folds` (no fold lost with its 90% interval below zero, pooled Brier gain) and the alert table at 4:1. Pooled and within-year AUC reported beside them, not as the rule. A run that passes is a candidate; adoption is the user's decision.

### Results (18 November 2026)

t88-off's banking forecasts equal t80-base's byte for byte: the integrate branch reproduces banking with every option off.

Banking year nodes against t80-base (`bnm.links --folds`; logs data/runs/logs/score-t88-*.txt, alerts-t88-*-vs-t80-base.txt):

| fold | t88-rho0 Brier | t88-nofrail Brier |
|---|---|---|
| 2003-06 | +0.00137 [-0.00124, +0.00504] | +0.00083 [-0.00166, +0.00432] |
| 2007-09 | +0.00459 [+0.00082, +0.00912] GAIN | +0.00310 [-0.00024, +0.00623] |
| 2010-12 | +0.00254 [+0.00002, +0.00583] GAIN | +0.00191 [-0.00040, +0.00483] |
| 2013-19 | +0.00002 [-0.00042, +0.00054] | -0.00006 [-0.00053, +0.00049] |
| 2020-22 | -0.00164 [-0.00183, -0.00145] LOSS | -0.00172 [-0.00196, -0.00149] LOSS |
| pooled | +0.00107 [-0.00005, +0.00242] | +0.00062 [-0.00032, +0.00180] |

- Pooled AUC: t80-base 0.371, t88-rho0 0.578, t88-nofrail 0.495; within-year 0.641, 0.629, 0.602.
- Alert cost, banking: t80-base 0.132, t88-rho0 0.128 (+0.0035 [+0.0000, +0.0094]), t88-nofrail 0.129. Episodes caught: 0 of 2 in all three; mean p at Dec 2006-07 is 0.052 and 0.051 (rate 0.169, 0.146), against t80-base's 0.025 and 0.022.
- **Verdict: both fail the fold rule** (2020-22, the calm years after COVID's exogenous rows are left out: a level near 0.05 where t80-base's carried calm issued lower). Not adopted.
- **What it shows.** The persistence is what inverts the level: without it the pooled AUC goes from 0.37 to 0.58 and 2007-12 gain. But no variant lifts the level before the wave (0.05 at a rate of 0.17): with the frailty neutral, nothing in the node moves the level with the build-up. Removing the lagged carrier restores neutrality, not anticipation. The level needs a state built from the stocks and flows (the redesign proposed with this result: stretched stocks meeting turning flows, aggregated across countries, as the shock's mean, learned from 1870 with theory's signs).

### What sets the level: four checks (18 November 2026, after t88)

Scripts in docs/studies/ (exploratory, written after seeing 2008: in-sample for design, not a test).

1. **The pattern is general** (kinds_level.py). The yearly mean p's correlation with the yearly rate, year country nodes 2003-2022, t53-noclim → t80-base: sovereign defaults -0.51 → -0.64, inflation -0.41 → -0.57, currency -0.50 → -0.47, coups -0.35 → -0.41, banking -0.19 → -0.32, war -0.24 → -0.28; market crashes -0.21 → +0.48, growth -0.40 → +0.34, food +0.10 → +0.34. The level runs against the rate wherever it is learned from outcomes (climatology, history counts, the frailty); the three kinds that track it read a state at the origin (the leading gauges). Within-year AUC is 0.73-0.88 at the political kinds: ranking is not the failure, timing is.
2. **A world turn on JST 1880-2016** (turn_study.py, turn_dims.py). The share of countries with credit stretched and house-price growth slowing leads the share starting a crisis in the next two years (r +0.23; +0.29 since 1950; 0.35 → 0.65 before 1990-92, 0.39 → 0.67 before 2007-08), where the count of recent crises carries nothing (+0.04). The same turn with every dimension weighed equally carries nothing (+0.00): the dimensions that matter differ, and an equal weighting drowns them.
3. **Learned combinations, out of sample by era** (learn_combos*.clj; each era predicted from models fit on the years before it). 2000-2016: credit alone AUC 0.714, level r +0.76, log skill +0.045; credit plus every dimension's stretch and turn, slopes shared across eras with each era's deviation partially pooled (augur.hier/grouped-slopes), AUC 0.746, r +0.75, +0.040; the same with world aggregates of each dimension, AUC 0.620, r -0.10, -0.064. World-level weights learned from about ten waves invert the level out of sample (t85's failure again); country-level stocks aggregate to the level bottom-up; era pooling beats ridge and horseshoe; the learned combinations add ranking, not log score, over credit on JST's 18 countries.
4. **Coverage** (t80-base and t88-rho0, banking year rows 2003-2022). JST's 18 countries (28 onsets): AUC 0.394 → 0.664, level r -0.15 → +0.30. The others (54 onsets, two thirds of them): AUC 0.356 → 0.441, level r -0.33 → -0.18. Where the stocks exist the approach works; most crises are where the node has none (the commodity and emerging-market waves of 2013-16).

## Banking coverage from declared signals: t92 (bnm-7bh step 1)

Written 18 November 2026, before any run. Chosen after "What sets the level" (two thirds of banking onsets outside JST's 18 countries, where the year node reads almost nothing) and t88: an in-sample choice, scored on every fold all the same.

**Design.** `--banking-coverage true` appends to banking's year country row nine signals declared through bnm.signals (docs/SIGNALS.md), read point in time for every country, from series loaded only under the option (the off path reads the same database as before):

| name | series, read | prior mean (sd 0.25) | why |
|---|---|---|---|
| :tot12 | trade/commodity_tot, change over 12 months | -0.2 | a terms-of-trade fall precedes commodity-bust crises |
| :tot12-x-exporter | the same times fuel and metal export shares (two years back, /100) | -0.3 | the fall matters where commodities are the exports (Eberhardt & Presbitero 2021) |
| :tot-vol12 | trade/commodity_tot, volatility over 12 months | +0.1 | volatility, not credit, predicts crises where the primary sector is large |
| :reserves | debt/reserves_months, value | -0.2 | the buffer against a sudden stop |
| :reserves12 | debt/reserves_months, change over 12 months | -0.2 | a draining buffer |
| :ca-stretch | debt/current_account_gdp, stretch against its previous 10 published years (at least 6), capped at 5 | -0.2 | a widening deficit is the external side of a boom |
| :pcredit-stretch | finance/private_credit_gdp (WDI), stretch as above, spread floored at 2% of the level | +0.3 | the credit boom, for every country (Schularick & Taylor 2012) |
| :pcredit-turn | the same series, the turn (a stretch past 1 moving back) | +0.3 | the stretched stock meeting a turning flow (docs/INVARIANTS.md) |
| :pcredit3 | finance/private_credit_gdp, change over 36 months | +0.2 | credit growth |

They form one group, `:coverage`, at the year node, standardized by the node's own standardizer (pooled over countries and the origins before t), with the priors above by name. Nothing else changes.

**Runs**, banking only (the other twelve kinds copied from t80-base), one at a time:
- **t92-off**: t80-base's command on this branch: banking byte for byte t80-base's.
- **t92-cov**: `--banking-coverage true`.
- **t92-cov-rho0**: the same with banking's frailty at ρ 0 (`:frailty {:rhos [0.0]}`), t88-rho0 plus the coverage.

**Rule, fixed now.** Against t80-base, banking year nodes: the fold rule (no fold lost with its 90% interval below zero, pooled Brier gain), and the alert table at 4:1. Reported beside it, not as the rule: t92-cov-rho0 against t88-rho0 (what the coverage adds to the neutral frailty), pooled and within-year AUC split JST / outside JST, and the yearly level's correlation with the rate.

### Results (18 November 2026): the coverage never enters; late dimensions are locked out of the averaging

- t92-off's banking forecasts equal t80-base's byte for byte.
- t92-cov against t80-base and t92-cov-rho0 against t88-rho0: every fold's difference under 1e-5 (Brier and log). The coverage changed nothing, so the rule is moot: not adopted, not a test of the coverage.
- **Why.** The coverage group's weight at banking's year country node is 0 at every origin. Its signals are read (Dec 2013: Angola's reserves 6.6 months, Iceland's private-credit stretch -1.05), but the group enters the averaging in 1965 at 2.2e-7. Every group does the same: while its inputs are absent it is dormant, an intercept-only model under another name, and it carries that record; at its first live origin its weight equals the base model's then (t80-base, weights.tsv): 0.17 for groups live by 1880, 3.5e-5 for household (1888), 2.1e-7 to 4.7e-7 for profitability (1956), production, debt, exposure, sectoral, external, credit-wide (1965) and distress (1976). By then the leaders have a century of evidence over the intercept, and with forgetting at 0.995 a year a late group must beat them by about 15 nats before it carries weight. Every dimension whose data starts after about 1900 is locked out of the year node's averaging, whatever it says: most of the extended dimensions, and every coverage signal outside the JST panel.
- This is a design property of the averaging, not a code error: each model's record is right for what it predicted. It bears on the thesis directly (new dimensions must be able to earn weight). Bead to follow; the fix is a registered design choice, not part of t92.

## Late entry and the coverage, together: t93 (bnm-1hu, bnm-7bh)

Written 18 November 2026, before any run. `:late-entry :prior` (9d67c29): a group live for the first time at a node that has issued before enters at its prior share (equal shares here), the others renormalized; from then on its record is its own. Planted (test/bnm/late_entry_test.clj): a group reading the true signal from month 240 carries 3e-4 of the weight ten years on without it (the all-inputs model carries the signal meanwhile) and over 0.05 from its first year with it; a late noise group enters at 1/7, decays, and costs the log score nothing. The rule is uniform: the error-based groups, dormant at a node's first origin, enter at their prior shares a period later too, so the base moves from its first origins on.

Runs, banking only (other kinds from t80-base), one at a time, with `--set` adding `:late-entry :prior`:
- **t93-entry**: t80-base's command with `:late-entry :prior`. What unlocking the existing late groups (household, inequality, profitability, production, debt, exposure, sectoral, external, credit-wide, distress) does alone.
- **t93-entry-cov**: the same with `--banking-coverage true` (t92's signals, which can now enter).
- **t93-entry-cov-rho0**: the same with banking's frailty at ρ 0.

Rule, fixed now: each against t80-base, banking year nodes: the fold rule (no fold lost with its 90% interval below zero, pooled Brier gain) and the alert table at 4:1. Reported beside it: each against the one before it (what each step adds), the weights the late groups and the coverage reach, AUC pooled and within year split JST / outside JST, the level's correlation with the rate, calibration by decile, and the split by years since the country's last crisis (first-time crises: rate 0.084 at a mean p of 0.044 in t80-base).

## Long baselines at banking: t94 (bnm-3j8)

Written 18 November 2026, before any run. From docs/TESTBED.md "The baseline absorbs the boom": against 60 years (at least 10) in place of ten (at least six), the planted turn's recovered share of the oracle's skill went from 0.21, -0.41, -0.32 to 0.35, 0.01, 0.19 (credit waves, the lattice) and from 0.11, 0.07, 0.15 to 0.22, 0.23, 0.48 (synchronized). `--long-baseline N` (this commit) sets every level stretch banking's year node reads to N years (at least 10): bnm.macrohistory's specific dimensions, the BIS splice under --dated-credit, and the extended stretches; the flows keep ten years, so one thing changes. The displacement invariants (norm, breadth, resonance) are built from the specific stretches, so they move with it.

Runs, banking only (other kinds from t80-base), one at a time:
- **t94-off**: t80-base's command on this commit: banking byte for byte t80-base's.
- **t94-long60**: `--long-baseline 60`.
- **t94-long60-entry**: the same with `:late-entry :prior`.
- **t94-long60-entry-rho0**: the same with banking's frailty at ρ 0.

Rule, fixed now: each against t80-base, banking year nodes, the fold rule and the alert table at 4:1. Reported beside it: each against the one before it, pooled and within-year AUC (JST / outside), the level's correlation with the rate, calibration by decile.

### t93 results (18 November 2026)

Banking year nodes (`bnm.links --folds`; logs data/runs/logs/run-t93-*.txt):
- **t93-entry** against t80-base: no fold moves past 0.0003 Brier (2013-19 +0.00001 [+0.00000, +0.00002], the rest within noise), pooled -0.00004 [-0.00013, +0.00003]: neutral, not a pooled gain. The late groups do enter (year country weights at 1990: distress 0.89, debt 0.05, sectoral 0.03; at 2006: stretches 0.55, booms 0.16, distress 0.08, sectoral 0.07, funding 0.06, household 0.05), so the lock-out is gone, but what they carry is about what the early groups carried. Pooled AUC 0.360, within-year 0.598.
- **t93-entry-cov** against t93-entry: every fold within 0.00002: the coverage still adds nothing when it can enter (its group does not reach 0.03 of the weight at 1990, 2006 or 2015).
- **t93-entry-cov-rho0** against t80-base: t88-rho0's pattern (2007-09 +0.00369 [+0.00018, +0.00691], 2010-12 +0.00254 [+0.00001, +0.00583], 2020-22 -0.00164 [-0.00186, -0.00143] LOSS); against t88-rho0, pooled -0.00017 [-0.00047, +0.00001], log -0.00113. Fails the fold rule. Dec 2006-07 p 0.051 and 0.050 (rates 0.169, 0.146).
- **Verdict:** none adopted. Late entry is right in principle and removes the lock-out (bnm-1hu), but on banking it changes nothing worth a base change, and the coverage carries nothing through ten-year stretches. The testbed's finding (a ten-year baseline absorbs the boom) is the next test, t94.

## Against a reference that knows only that crises cluster (bnm-3j8)

From reports/Cross domain detection transfer.md (earthquake forecasting, Luen & Stark: a rule that alarms after every large event beats a shuffle at P < 0.001). docs/studies/clustering_reference.clj: at each December origin, a logit refit each year on the rows before it, reading the share of the country's region and of the world with an onset in the two years whose outcomes are known (origins t-24 and t-36). t80-base's year country rows, 2003-2022, log score per row (run less reference) and AUC (run / reference):

| kind | run - reference | AUC |
|---|---|---|
| banking crises | **-0.0065** | 0.371 / 0.426 |
| sovereign defaults | **-0.0064** | 0.517 / 0.570 |
| inflation crises | +0.0013 | 0.704 / 0.490 |
| currency crises | +0.0124 | 0.693 / 0.443 |
| coups | +0.0131 | 0.764 / 0.607 |
| war onsets | +0.0193 | 0.866 / 0.613 |
| protest surges | +0.0206 | 0.785 / 0.684 |
| violence surges | +0.0338 | 0.719 / 0.640 |
| fiscal consolidations | +0.0374 | 0.553 / 0.390 |
| conflict onsets | +0.0446 | 0.842 / 0.712 |
| growth collapses | +0.0616 | 0.634 / 0.442 |
| market crashes | +0.0670 | 0.755 / 0.391 |
| food price shocks | +0.0964 | 0.664 / 0.343 |

Banking and sovereign defaults lose to it: both financial, both with a level against the rate. Every other kind beats it.

**Banking: target or pipeline** (docs/studies/banking_credit_check.clj). On the lattice's own banking year rows and labels at JST's 18 countries, a logit on the published JST row's credit stretch and three-year credit growth, refit each year on rows whose outcomes are known (three years after the origin): 1990-2002 AUC 0.805 (lattice 0.501), 2003-2020 AUC 0.699 and log skill against climatology +0.0227 (lattice 0.367 and -0.0509); on JST's own crisis flags the same (0.701 / 0.316). The labels mostly agree (1990-2020: 38 lattice onsets, 30 JST, 26 both). The lattice reads these very inputs (its year gauge for credit is JST's stretch of the year before, Spearman 1.00 at every December checked, without --dated-credit), and the groups that read them hold most of the weight. So the target and the inputs are not the fault: the lattice loses the signal between its inputs and its forecast. Under --dated-credit the gauge follows the BIS from 2003 (Spearman 0.58-0.83 with JST's stretch, some countries opposite in sign: BIS credit from all lenders against JST's bank loans), but the inversion predates it (t71-stack's pooled AUC 0.379).

### t94 results (18 November 2026)

t94-off's banking forecasts equal t80-base's byte for byte. Banking year nodes against t80-base:
- **t94-long60**: pooled +0.00001 [-0.00011, +0.00015], 2010-12 -0.00033 [-0.00055, -0.00008] LOSS. Within-year AUC 0.641 -> 0.707 (the testbed's prediction: the ranking within a year recovers), pooled AUC 0.382 (the level still the frailty's).
- **t94-long60-entry**: pooled -0.00024 [-0.00052, -0.00004] LOSS, 2010-12 -0.00103 LOSS; within-year 0.713, pooled 0.352.
- **t94-long60-entry-rho0**: 2007-09 +0.00351 [+0.00017, +0.00665], 2020-22 -0.00163 [-0.00193, -0.00135] LOSS; pooled AUC 0.566, within-year 0.667; against t88-rho0 pooled -0.00066 [-0.00127, -0.00020] LOSS (2010-12 -0.00274). Calibration still inverted at the top (decile 10: p 0.088, rate 0.031). Dec 2006-07 p 0.051, 0.050.
- **Verdict:** none adopted. The long baseline restores the ranking within years, as planted, but no setting moves banking's proper scores: the lattice's combination loses what its groups read (bnm-ris; next, t95's trace of each group's issued forecast).
