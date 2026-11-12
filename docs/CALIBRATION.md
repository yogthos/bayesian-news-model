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
| A period's rows counted as independent evidence in the averaging: one common shock at n places moves the weights n times (the weight lock of LOOP-AUDIT 6, 7) | market crashes, growth collapses, food | market crashes 0.1996 -> 0.1707 tempered to 1/n; growth 0.0990 -> 0.0962 | sequencing: step 5 (the weighting). Tempering is augur's existing `:temper`; a per-node power 1/places is the first thing step 5 should score |
| Time-common terms (intercept, world3, parent states) learned from n rows per origin: a slope from one episode | market crashes (world3 -0.21 -> -0.58 off 2006-07), growth collapses, food 2010-11 | market crashes 0.1707 -> 0.1645 with each period counted once | sequencing: step 3. This is what the frailty's per-origin common shock is for, so here the frailty is the fix, not something that would hide a calibration error |
| Year node issues 0.5 before its first resolved outcome | food 2003-06 (612 rows) | all of food's excess mean p | implementation bug bnm-56c.26, fixed behind `--clim-prior kinds` |
| Offset and intercept both take a change of climatology | food 2007 (p 0.03, rate 0.31), market crashes 2009-11 | | implementation bug bnm-56c.27, fixed behind `:clim-learn :current` |
| Climatology is the rate since the first outcome; 2003-22 was calmer | banking, coups, wars, violence, inflation, currency | causal climatology loses to the in-sample constant by as much as the base (banking 0.0314 vs 0.0310) | not a bug and not causal-fixable: hindsight. A faster level would chase the shocks (recalibration with discounts 0.9-1.0 loses on banking) |
| A monotone recalibration | all | pooled -0.0004 Brier, banking and food worse | not the fix: the error changes sign with each shock |

No new implementation bug was found. The two that bear on calibration have their fixes and tests already (lattice_test bnm-56c-27-a-change-of-climatology-decays, bnm-56c-26-a-node-with-nothing-resolved-reads-the-prior-rate; world_run_test bnm-56c-26-the-prior-rate-is-the-other-kinds'); they had not been scored together on the bnm-56c.33 base.

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
