# Why the combination misses transitions: weighting diagnostic (bnm-56c.2)

Development diagnostic, read only. Nothing under data/ was written and no run directory was touched.

## Method

- Code: origin/structure at e79d91a (augur v0.19.0), exported with `git archive` into a scratch directory; the instrumentation helper was not kept.
- Each kind's lattice runs in memory on t48's archived inputs (`data/ledger-inputs/2026-Q4`, through `bnm.ledger/recording` with `redirect-to`, as `bnm.fast-run --archive` does). There are no relations, so each kind runs on its own.
- The runner steps `bnm.lattice/step` by hand with `:trace :inputs`. After each step it records every model's own predictive (`d/predict`, `d/predictive`) on each new country row at the year and quarter-lead nodes, using the model state that issued the row. It also records the averaging's weights, the lattice's own learn-time evidence (`[:st :evidence]`), A's gains, and the all-inputs model's coefficients.
- Reproduction check: the recomputed combined forecasts match the on-disk files exactly (max abs diff 0.0). This holds for t48-world-norel food price shocks, conflict onsets and market crashes, and for rk13-A-none food price shocks. The weighted per-model forecasts also reproduce t48's p.
- Rows are country nodes, scored where the outcome is known. A year row with origin Y (t = 12Y+11) forecasts an onset within the next two years. It becomes known, and is learned, about 3 to 4 years later.
- "Forecast-time" scores are each model's score of the forecast it actually issued. "Learn-time" evidence is what the averaging uses: the model's log score of a row when the row becomes known, scored by the model's state at that later date.

## Headline

1. **The 2020–21 food shock was a common level shock that no model discriminated.**
   - Year-node event rate: 0.49 at origin 2020 and 0.50 at origin 2021. Over 2015–19 it was 0.06–0.09.
   - Every model's AUC in 2020–21 was 0.44–0.58.
   - Mean forecasts: lattice 0.083 and 0.102; `down` 0.129 and 0.119; `all` 0.083 and 0.102; base 0.052.
   - The Brier ranking in those years follows each model's level, not its discrimination. `down` "won" (0.377 and 0.390, against `all` 0.421 and 0.415) because it over-forecast in every calm year (0.11–0.14 against rates of 0.06–0.09).
   - t48's edge over A is all in level. Its mean p was 0.095 against A's 0.047, the gain sits entirely on event rows (+0.037 on event rows, −0.008 on non-event rows), and 59% of it comes from 10 countries: BDI, COM, NGA, LAO, MMR, SYR, KAZ, LBR, AZE, HND.
2. **Most of A's 2020–22 food loss is not the weighting itself but the lattice's feedback.** The weighting changes the node's own state. That state comes back to the models as inputs: its own forecast's log odds in the contradictions, its errors, the quarters' offset and their states read back through `:up`, and the ancestors' states through `:down`. So under A the all-inputs model is a different, worse model.
   - Food, 2020–22, year nodes, 383 rows. Brier:
     - t48 lattice 0.3472 (its all-inputs model alone: 0.3472);
     - A-run all-inputs model 0.3720, and A-run base 0.3726;
     - A combined 0.3760.
   - Of A's −0.0289, 0.0249 (86%) is that the all-inputs model got worse, and 0.0040 (14%) is the combination.
   - The models that read only gauges are identical in the two runs (mean |Δp| = 0.0000). The `all`, `down` and `up` models are not: in 2009–2014 the A-run `all` had mean |Δp| 0.07–0.32, and its Brier was 0.155 against 0.093 in 2009 and 0.158 against 0.050 in 2011.
   - That is why A's learn-time gain for `all` turned negative from 2015 (−0.042 per row), and why A gave it weight 0 from 2015 to 2024.
3. **The ceiling from reweighting is small next to the ceiling from level.** Food, 2020–22, Brier:
   - lattice 0.3472;
   - per-year oracle convex weights over the 18 models 0.3196 (0.028 on the table);
   - a constant at each year's realized rate 0.2189 (0.128 on the table).
4. **State-dependence exists but does not rescue 2021.**
   - Before 2020, in stressed system states (world food price 12-month change in its top quintile), every model gains strongly over the base: `all` +0.171 nats per row, history +0.239, world +0.238, up +0.210 (290 rows from two episodes).
   - In calm states the gains are about zero or negative: `all` −0.032, `down` −0.48.
   - The winner of 2020–21, `down`, had no stressed-state evidence for it: +0.014 per row with no discount, −0.137 at a discount of 0.8.
   - Weighting by state (gain-proportional, top model, or softmax), using out-of-sample scores, gives 0.367–0.375 on food 2020–22. That is worse than t48's 0.347.
   - A state-conditional recalibration fitted online from world food prices gives 0.3489, no better than the lattice.
   - Buffer drawdowns (FX reserves in months of imports, JODI crude stocks, cereal production, over 12, 24 or 36 months) do not predict events or the edge: |corr| ≤ 0.12 at the row level.
   - Across years, the world food price momentum does track the event rate (r = 0.65) and `all`'s edge over the base (r = 0.68). But the year node has no world food price input. The `world` group is world3, the share of states with a recent onset.

## Q1. Food price shocks, 2015–2022: per-model forecasts, scores, redundancy, gains

### Year node (country), t48-world-norel (reproduced)

Event rate, lattice mean p, and mean p of selected models, by origin:

| origin | rate | lattice | all | base | down | up | feedback | world | history | economy |
|---|---|---|---|---|---|---|---|---|---|---|
| 2012 | 0.013 | 0.030 | 0.030 | 0.099 | 0.027 | 0.226 | 0.126 | 0.064 | 0.131 | 0.111 |
| 2013 | 0.051 | 0.164 | 0.173 | 0.095 | 0.670 | 0.189 | 0.068 | 0.051 | 0.114 | 0.094 |
| 2014 | 0.095 | 0.049 | 0.034 | 0.090 | 0.060 | 0.125 | 0.112 | 0.047 | 0.099 | 0.079 |
| 2015 | 0.073 | 0.037 | 0.037 | 0.077 | 0.065 | 0.126 | 0.074 | 0.036 | 0.080 | 0.062 |
| 2016 | 0.060 | 0.025 | 0.025 | 0.063 | 0.110 | 0.093 | 0.063 | 0.029 | 0.065 | 0.048 |
| 2017 | 0.086 | 0.034 | 0.034 | 0.057 | 0.125 | 0.081 | 0.050 | 0.030 | 0.061 | 0.046 |
| 2018 | 0.078 | 0.073 | 0.073 | 0.056 | 0.142 | 0.071 | 0.052 | 0.035 | 0.064 | 0.053 |
| 2019 | 0.088 | 0.096 | 0.096 | 0.054 | 0.143 | 0.060 | 0.055 | 0.037 | 0.066 | 0.045 |
| 2020 | 0.490 | 0.083 | 0.083 | 0.052 | 0.129 | 0.051 | 0.053 | 0.036 | 0.057 | 0.045 |
| 2021 | 0.500 | 0.102 | 0.102 | 0.052 | 0.119 | 0.046 | 0.054 | 0.036 | 0.064 | 0.051 |
| 2022 | 0.128 | 0.104 | 0.104 | 0.051 | 0.080 | 0.049 | 0.054 | 0.038 | 0.062 | 0.060 |

Brier by model (lattice = t48 combined):

| origin | n | events | lattice | all | base | contradiction | demography | down | economy | exposure | feedback | fiscal | history | inequality | military | others | patronage | regime | society | up | violence | world |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2015 | 151 | 11 | 0.0680 | 0.0680 | 0.0676 | 0.0680 | 0.0678 | 0.0697 | 0.0637 | 0.0696 | 0.0673 | 0.0678 | 0.0651 | 0.0668 | 0.0644 | 0.0672 | 0.0631 | 0.0670 | 0.0630 | 0.0720 | 0.0626 | 0.0689 |
| 2016 | 149 | 9 | 0.0581 | 0.0581 | 0.0568 | 0.0575 | 0.0526 | 0.0726 | 0.0550 | 0.0576 | 0.0566 | 0.0574 | 0.0571 | 0.0574 | 0.0564 | 0.0565 | 0.0546 | 0.0565 | 0.0552 | 0.0592 | 0.0535 | 0.0577 |
| 2017 | 152 | 13 | 0.0741 | 0.0741 | 0.0790 | 0.0804 | 0.0730 | 0.0832 | 0.0757 | 0.0803 | 0.0798 | 0.0788 | 0.0722 | 0.0790 | 0.0791 | 0.0781 | 0.0766 | 0.0772 | 0.0764 | 0.0796 | 0.0747 | 0.0813 |
| 2018 | 153 | 12 | 0.0675 | 0.0675 | 0.0728 | 0.0729 | 0.0710 | 0.0807 | 0.0658 | 0.0739 | 0.0733 | 0.0722 | 0.0642 | 0.0725 | 0.0727 | 0.0729 | 0.0707 | 0.0703 | 0.0699 | 0.0736 | 0.0693 | 0.0742 |
| 2019 | 147 | 13 | 0.0854 | 0.0854 | 0.0818 | 0.0817 | 0.0804 | 0.0869 | 0.0825 | 0.0829 | 0.0813 | 0.0824 | 0.0844 | 0.0810 | 0.0819 | 0.0815 | 0.0816 | 0.0795 | 0.0827 | 0.0828 | 0.0832 | 0.0833 |
| 2020 | 151 | 74 | 0.4213 | 0.4213 | 0.4421 | 0.4459 | 0.4455 | **0.3770** | 0.4488 | 0.4541 | 0.4409 | 0.4431 | 0.4383 | 0.4447 | 0.4425 | 0.4445 | 0.4409 | 0.4388 | 0.4432 | 0.4432 | 0.4406 | 0.4564 |
| 2021 | 146 | 73 | 0.4151 | 0.4151 | 0.4511 | 0.4561 | 0.4561 | **0.3898** | 0.4533 | 0.4635 | 0.4492 | 0.4509 | 0.4399 | 0.4541 | 0.4486 | 0.4490 | 0.4493 | 0.4456 | 0.4502 | 0.4566 | 0.4449 | 0.4651 |
| 2022 | 86 | 11 | 0.1015 | **0.1015** | 0.1174 | 0.1185 | 0.1098 | 0.1169 | 0.1027 | 0.1176 | 0.1169 | 0.1193 | 0.1143 | 0.1160 | 0.1130 | 0.1114 | 0.1159 | 0.1144 | 0.1157 | 0.1190 | 0.1154 | 0.1197 |

AUC within each origin year (no model discriminates in 2020–21):

| origin | lattice | all | base | down | up | feedback | world | history | economy | demography | violence |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2015 | 0.79 | 0.79 | 0.50 | 0.31 | 0.36 | 0.60 | 0.50 | 0.61 | 0.77 | 0.66 | 0.77 |
| 2016 | 0.73 | 0.73 | 0.50 | 0.34 | 0.24 | 0.62 | 0.50 | 0.59 | 0.72 | 0.73 | 0.71 |
| 2017 | 0.72 | 0.72 | 0.50 | 0.33 | 0.32 | 0.38 | 0.50 | 0.72 | 0.72 | 0.77 | 0.72 |
| 2018 | 0.70 | 0.70 | 0.50 | 0.42 | 0.36 | 0.44 | 0.50 | 0.80 | 0.81 | 0.68 | 0.72 |
| 2019 | 0.64 | 0.64 | 0.50 | 0.50 | 0.33 | 0.58 | 0.50 | 0.57 | 0.66 | 0.65 | 0.61 |
| 2020 | 0.49 | 0.49 | 0.50 | 0.53 | 0.47 | 0.50 | 0.50 | 0.56 | 0.58 | 0.46 | 0.44 |
| 2021 | 0.52 | 0.52 | 0.50 | 0.54 | 0.48 | 0.52 | 0.50 | 0.58 | 0.57 | 0.46 | 0.46 |
| 2022 | 0.76 | 0.76 | 0.50 | 0.52 | 0.30 | 0.48 | 0.50 | 0.69 | 0.80 | 0.77 | 0.61 |

The base and world models give every country the same forecast within a year, so their AUC is 0.50.

Each model's forecast-time log-score gain over the base, nats per row, and its pooled gain over the all-inputs model and over the best other model (2015–2022):

| model | 2015 | 2016 | 2017 | 2018 | 2019 | 2020 | 2021 | 2022 | pooled over base | pooled over all | pooled over best other |
|---|---|---|---|---|---|---|---|---|---|---|---|
| all | +0.010 | -0.012 | +0.001 | +0.025 | +0.002 | -0.054 | +0.031 | +0.090 | +0.0072 | 0 | -0.0750 |
| down | -0.016 | -0.067 | -0.015 | -0.030 | -0.014 | +0.389 | +0.375 | +0.012 | +0.0821 | +0.0750 | +0.0736 |
| history | +0.011 | +0.000 | +0.034 | +0.047 | -0.012 | -0.036 | +0.003 | +0.027 | +0.0085 | +0.0014 | -0.0736 |
| economy | +0.030 | +0.016 | +0.018 | +0.043 | -0.000 | -0.166 | -0.161 | +0.075 | -0.0227 | -0.0299 | -0.1049 |
| violence | +0.028 | +0.019 | +0.028 | +0.022 | -0.007 | -0.062 | -0.044 | +0.008 | -0.0011 | -0.0083 | -0.0833 |
| patronage | +0.027 | +0.018 | +0.021 | +0.016 | -0.000 | -0.087 | -0.090 | +0.009 | -0.0115 | -0.0187 | -0.0936 |
| demography | +0.010 | +0.026 | +0.041 | +0.016 | +0.010 | -0.168 | -0.207 | +0.048 | -0.0316 | -0.0387 | -0.1137 |
| regime | +0.009 | +0.010 | +0.017 | +0.021 | +0.019 | -0.087 | -0.074 | +0.020 | -0.0096 | -0.0167 | -0.0917 |
| society | +0.029 | +0.012 | +0.019 | +0.022 | -0.002 | -0.070 | -0.063 | +0.005 | -0.0064 | -0.0135 | -0.0885 |
| up | -0.023 | -0.016 | -0.005 | -0.008 | -0.009 | -0.019 | -0.070 | -0.018 | -0.0211 | -0.0282 | -0.1032 |
| world | -0.015 | -0.013 | -0.029 | -0.017 | -0.017 | -0.174 | -0.169 | -0.027 | -0.0590 | -0.0662 | -0.1412 |
| exposure | -0.007 | -0.003 | -0.012 | -0.011 | -0.011 | -0.193 | -0.195 | -0.006 | -0.0573 | -0.0645 | -0.1394 |
| contradiction | -0.004 | -0.009 | -0.021 | -0.003 | -0.004 | -0.069 | -0.066 | -0.011 | -0.0240 | -0.0312 | -0.1061 |
| feedback | +0.002 | +0.001 | -0.007 | -0.005 | +0.004 | +0.005 | +0.016 | +0.005 | +0.0024 | -0.0048 | -0.0798 |
| others | +0.004 | +0.002 | +0.006 | +0.003 | +0.007 | -0.030 | -0.010 | +0.038 | +0.0006 | -0.0066 | -0.0815 |
| inequality | +0.007 | -0.001 | +0.003 | +0.004 | +0.006 | -0.070 | -0.073 | +0.010 | -0.0154 | -0.0226 | -0.0975 |
| military | +0.020 | +0.005 | +0.001 | +0.002 | -0.001 | -0.048 | -0.034 | +0.033 | -0.0047 | -0.0119 | -0.0868 |
| fiscal | -0.001 | -0.005 | +0.003 | +0.005 | -0.005 | -0.012 | -0.001 | -0.025 | -0.0042 | -0.0113 | -0.0863 |

In calm years (2015–19) the gauge groups (economy, violence, patronage, society, demography) each gain +0.01 to +0.04 over the base, as much as or more than `all`. In 2020–21 all of them lose, by −0.06 to −0.21. Only `down` gains in those years (+0.39), and in calm years it loses (−0.015 to −0.067).

**Redundancy.** Correlation of the models' linear predictors within each year (each year demeaned), 2015–2022:
- Year nodes: mean pairwise 0.15 (median 0.18, range −0.76 to 0.93), so the models are not redundant. Correlation with `all`: economy 0.67, regime 0.67, demography 0.66, patronage 0.63, history 0.53, `down` −0.43, `up` −0.76.
- Quarter-lead nodes: mean pairwise 0.97 (min 0.94). They are nearly copies, because the forecast is dominated by the offset passed down from the year node.

### Quarter-lead node (country), t48

Brier by model:

| year | n | events | lattice | all | base | climate | economy | energy | events | food | fragility | pol-econ |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2015 | 613 | 23 | 0.0360 | 0.0361 | 0.0369 | 0.0369 | 0.0355 | 0.0370 | 0.0369 | 0.0372 | 0.0368 | 0.0370 |
| 2016 | 593 | 17 | 0.0288 | 0.0284 | 0.0284 | 0.0284 | 0.0290 | 0.0284 | 0.0284 | 0.0285 | 0.0283 | 0.0283 |
| 2017 | 592 | 15 | 0.0269 | 0.0258 | 0.0252 | 0.0251 | 0.0274 | 0.0252 | 0.0252 | 0.0252 | 0.0252 | 0.0251 |
| 2018 | 611 | 17 | 0.0266 | 0.0260 | 0.0272 | 0.0271 | 0.0271 | 0.0271 | 0.0271 | 0.0272 | 0.0268 | 0.0271 |
| 2019 | 596 | 20 | 0.0303 | 0.0304 | 0.0320 | 0.0317 | 0.0304 | 0.0319 | 0.0321 | 0.0322 | 0.0314 | 0.0320 |
| 2020 | 589 | 21 | 0.0348 | 0.0349 | 0.0347 | 0.0347 | 0.0349 | 0.0348 | 0.0344 | 0.0348 | 0.0347 | 0.0348 |
| 2021 | 571 | 171 | 0.2879 | 0.2880 | 0.2919 | 0.2914 | 0.2925 | 0.2920 | 0.2915 | 0.2896 | 0.2906 | 0.2912 |
| 2022 | 365 | 55 | 0.1466 | 0.1468 | 0.1466 | 0.1468 | 0.1475 | 0.1467 | 0.1468 | 0.1460 | 0.1461 | 0.1471 |

- In 2021 the quarter rate was 0.30, while every model forecast 0.012–0.024 on average. The models are almost the same; t48's quarter weights were on `all` at 0.91–1.00 from 2018.
- Log-score gain over the base, pooled 2015–22: `all` +0.012 (and +0.006 over the best other model), climate +0.007, pol-econ +0.004, food +0.001. In 2021 food gained +0.035 and `all` +0.062.

## Q2. Where the all-inputs model's edge comes from

- **The edge in 2020–21 is level, not discrimination.** t48's `all` had AUC 0.49 and 0.52. Its Brier gain over A is concentrated:

  | measure | value |
  |---|---|
  | rows | 383 |
  | A − t48 Brier (positive means t48 better) | +0.0289 |
  | from event rows (158) | +0.0371 |
  | from non-event rows (225) | −0.0082 |
  | share from the top 10 countries | 59% (BDI, COM, NGA, LAO, MMR, SYR, KAZ, LBR, AZE, HND) |
  | share from the top 20 rows | 69% |
  | countries where t48 is better | 86 of 158 |
  | mean p, t48 / A | 0.095 / 0.047 |

  In the top rows, t48 forecast BDI 2021 at 0.72 (A 0.10), BDI 2020 at 0.53 (A 0.06), SYR 2021 at 0.42, and COM 2021, TUR 2020, NGA 2022 and SSD 2022 at 0.31–0.34. All of these were events.
- **What moved.** Below is each input group's mean contribution to the all-inputs model's log odds (coefficient × input), year rows.

  t48 run:

  | origin | intercept | history | economy | up | down | contradiction | exposure | world | feedback |
  |---|---|---|---|---|---|---|---|---|---|
  | 2015 | -1.23 | +0.12 | -0.29 | +1.04 | +0.35 | -1.41 | -0.82 | -0.18 | -0.06 |
  | 2017 | -1.23 | +0.08 | -0.21 | +0.98 | +0.42 | -0.59 | -1.38 | -0.25 | -0.25 |
  | 2018 | -1.14 | +0.07 | -0.06 | +0.96 | +0.91 | -0.45 | -1.58 | -0.10 | -0.14 |
  | 2019 | -1.18 | +0.19 | -0.11 | +0.86 | +1.31 | -0.40 | -1.78 | -0.04 | +0.00 |
  | 2020 | -1.23 | +0.15 | -0.15 | +0.68 | +1.37 | -0.23 | -1.77 | -0.06 | +0.01 |
  | 2021 | -1.18 | +0.24 | -0.14 | +0.69 | +1.58 | -0.47 | -1.62 | -0.07 | +0.01 |
  | 2022 | -1.21 | +0.25 | -0.02 | +0.68 | +1.30 | -0.57 | -1.27 | -0.05 | -0.01 |

  A run:

  | origin | intercept | history | economy | up | down | contradiction | exposure |
  |---|---|---|---|---|---|---|---|
  | 2019 | -1.52 | +0.44 | -0.09 | +0.98 | -0.71 | -0.03 | -1.13 |
  | 2020 | -1.51 | +0.41 | -0.15 | +0.96 | -0.42 | +0.01 | -1.23 |
  | 2021 | -1.46 | +0.56 | -0.15 | +0.98 | +0.21 | -0.32 | -1.16 |

  - The group that moved is **`down`**: the subregion, region and world year nodes' states, read lagged. In t48 its contribution rose from +0.35 (2015) to +1.58 (2021). In A it was −0.42 to +0.21.
  - These are not stock or price inputs that turned decisive after years of quiet. The coarser nodes' states (the world node's p rose from 0.89 to 0.95 over 2015–21, and its target is "some onset somewhere", true every year) are a slow trend. Its coefficient differs by run, because the ancestors' states depend on the weighting too.
  - Among the top 20 rows, what sets them apart is `history` (own past food shocks, +0.63 against +0.18 in the other rows), `economy` (inflation and food inflation, +0.29 against −0.14), and a smaller negative `exposure`. The repeat offenders get the high p.
- **Is there an input that had been quiet for years and turned decisive?** The year node has no stock, reserve or world-price input. The food and energy price inputs are only at the quarters: world food 12-month change, food inflation, food × imports, food × fragility, and oil × imports and exports. There, the quarter food group gained only in 2021 (+0.035 nats per row), and the quarters are 97% correlated copies of the year's offset.
- **The weighting changes the models.** In the two runs the gauge-only models are identical (|Δp| = 0). The `all`, `down` and `up` models differ a lot:

  | origin | mean abs Δp, all | Δp, down | Δp, up | Brier all, t48 run | Brier all, A run |
  |---|---|---|---|---|---|
  | 2009 | 0.187 | 0.043 | 0.523 | 0.093 | 0.155 |
  | 2011 | 0.318 | 0.537 | 0.186 | 0.050 | 0.158 |
  | 2012 | 0.245 | 0.389 | 0.125 | 0.014 | 0.084 |
  | 2014 | 0.294 | 0.257 | 0.015 | 0.088 | 0.167 |
  | 2020 | 0.043 | 0.076 | 0.012 | 0.421 | 0.454 |
  | 2021 | 0.042 | 0.068 | 0.011 | 0.415 | 0.446 |
  | 2022 | 0.027 | 0.027 | 0.011 | 0.1015 | 0.1017 |

  Food, 2020–22 pooled Brier:

  | model | t48 run | A run |
  |---|---|---|
  | combined | 0.3472 | 0.3760 |
  | all | 0.3472 | 0.3720 |
  | down | 0.3235 | 0.3731 |
  | base | 0.3726 | 0.3726 |

  A's loss is therefore 0.025 from the models (86%) and 0.004 from the combination. In 2022 the two runs' `all` are level (0.1015 against 0.1017), so A's 2022 deficit (0.1135) is the combination: all of A's weight was on other models.

## Q3. State dependence (and the user's question: system state setting the weights)

The system state is the world food price index's 12-month change, standardized and read at the origin from the quarter inputs. Its top quintile across 2004–2023 quarterly origins is at ≥ 0.79 sd. Stressed year-end origins: 2006, 2007, 2010 and 2021. Origin 2020 (0.47) is calm.

**Food, year nodes, 2007–2019, mean log score per row** (Brier in the second pair of lines):

| state | rows | rate | lattice | base | all | down | up | economy | world | history | demography | violence | patronage | exposure | contradiction | feedback |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| calm | 1635 | 0.065 | -0.2456 | -0.2509 | -0.2746 | -0.6481 | -0.3044 | -0.2430 | -0.2651 | -0.2495 | -0.2427 | -0.2396 | -0.2385 | -0.3644 | -0.2981 | -0.2515 |
| stressed | 290 | 0.214 | -0.5160 | -0.7409 | -0.5699 | -0.7267 | -0.5307 | -0.5773 | -0.5030 | -0.5016 | -0.6242 | -0.5960 | -0.6754 | -0.6226 | -0.5535 | -0.7362 |
| calm Brier | | | 0.0626 | 0.0624 | 0.0738 | 0.1930 | 0.0783 | 0.0624 | 0.0653 | 0.0645 | 0.0626 | 0.0608 | 0.0611 | 0.1067 | 0.0821 | 0.0631 |
| stressed Brier | | | 0.1679 | 0.1990 | 0.1946 | 0.2665 | 0.1715 | 0.1785 | 0.1623 | 0.1619 | 0.1896 | 0.1814 | 0.1922 | 0.2129 | 0.1819 | 0.1983 |

**Food, 2020–2022**:

| state | rows | rate | lattice | base | all | down | up | economy | world | history | demography | violence | patronage | exposure | contradiction | feedback |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| calm | 237 | 0.359 | -1.0986 | -1.0969 | -1.0987 | -0.8447 | -1.1157 | -1.1750 | -1.2174 | -1.1100 | -1.1862 | -1.1333 | -1.1493 | -1.2224 | -1.1449 | -1.0918 |
| stressed | 146 | 0.500 | -1.4775 | -1.5087 | -1.4775 | -1.1339 | -1.5784 | -1.6697 | -1.6779 | -1.5055 | -1.7155 | -1.5528 | -1.5984 | -1.7040 | -1.5751 | -1.4928 |

- **The rankings differ by state.**
  - Calm, before 2020: the gauge groups (patronage, violence, demography, economy) are best, and `all` and `down` are worse than the base.
  - Stressed, before 2020: history and world are best, followed by up and contradiction. `all` beats the base by +0.17 per row; `down` is near the base.
  - The stressed base rate is 0.214 against 0.065 in calm states, so stress triples it.
  - In 2020–21 `down` is best in both states, which matches no earlier state.
- **Country state.** Country food inflation in its top decile (≥ 0.74 sd):
  - Before 2020 the stressed rows' rate is 0.201 against 0.075. The world model is best in stressed rows (−0.509), then the lattice (−0.523), then `all` (−0.578), with the base at −0.675.
  - In 2020–22, in the 83 country-stressed rows, `all` is clearly best (−0.907 against the base's −1.246 and `down`'s −1.022). In calm rows `down` is best.
- **Two-by-two cells**, world stress × country food-inflation stress: rows, event rate, lattice mean p.

  | cell | 2007–2019 | 2020–2022 |
  |---|---|---|
  | calm / calm | 1485, 0.059, 0.060 | 180, 0.378, 0.075 |
  | calm / country stressed | 150, 0.127, 0.115 | 57, 0.298, 0.141 |
  | world stressed / calm | 246, 0.171, 0.267 | 120, 0.467, 0.081 |
  | both stressed | 44, 0.455, 0.283 | 26, 0.654, 0.201 |

- **Evidence tracked separately by state** (the user's question). Forecast-time log-score gain over the base per row, from rows known by each decision year, origins from 2007. Decision year 2021:

  | state, discount | effective rows | all | down | up | economy | world | history | demography | violence | contradiction | feedback |
  |---|---|---|---|---|---|---|---|---|---|---|---|
  | calm, λ=1 | 1335 | -0.032 | -0.482 | -0.064 | +0.005 | -0.014 | -0.002 | +0.007 | +0.012 | -0.057 | -0.001 |
  | stressed, λ=1 | 290 | **+0.171** | +0.014 | +0.210 | +0.164 | +0.238 | +0.239 | +0.117 | +0.145 | +0.187 | +0.005 |
  | calm, λ=0.8 | 264 | -0.010 | -0.307 | -0.043 | +0.012 | -0.014 | +0.005 | +0.018 | +0.019 | -0.038 | -0.002 |
  | stressed, λ=0.8 | 19 | +0.098 | -0.137 | +0.142 | +0.122 | +0.151 | +0.175 | +0.069 | +0.106 | +0.089 | +0.006 |

  - Pooled over states, `all`'s forecast-time gain at 2021 is +0.004 (λ=1), −0.000 (0.9), −0.002 (0.8) and −0.003 (0.5).
  - **For `all`**, the model t48 chose: the stressed-state evidence (+0.17 per row × 290 rows ≈ +50 nats) was strong while its pooled edge was about zero. A state-conditioned rule would have trusted it in stressed years.
  - **For `down`**, the actual 2020–21 winner: the stressed-state evidence was nil or negative, so no rule built from past scores would have selected it.
  - The stressed set is only two episodes (origins 2007 and 2010, learned by about 2011–14), and no stressed rows were added between 2011 and 2024.
- **Simulated rules on the t48 run's models.** Food, Brier. Lattice: 0.3472 for 2020–22 and 0.1169 for 2008–22. These rules use forecast-time scores, while the lattice uses learn-time evidence.

  | rule | 2020–22, pooled | 2020–22, by state | 2008–22, pooled | 2008–22, by state |
  |---|---|---|---|---|
  | gain-proportional (A-like, c = 0.005) | 0.3751 | 0.3746 | 0.1211 | 0.1196 |
  | top model | 0.3675 | 0.3681 | 0.1193 | 0.1182 |
  | softmax of total evidence (posterior-like) | 0.3675 | 0.3729 | 0.1379 | 0.1193 |
  | softmax, power 0.1 | 0.3738 | 0.3754 | 0.1301 | 0.1198 |

  - The A-like rule on t48's models reproduces A's actual numbers: 0.4477 in 2020, as A had; 0.4543 in 2021 against A's 0.4566.
  - Conditioning on state helps a little over 2008–22 for every rule (by 0.0012–0.019). But no rule fed by out-of-sample scores reaches t48 in 2020–22.
  - Recalibrating the lattice's level online by state:

    | recalibration | 2020 | 2021 | 2022 |
    |---|---|---|---|
    | lattice | 0.4213 | 0.4151 | 0.1015 |
    | global | 0.4294 | 0.4208 | 0.1021 |
    | world-food state | 0.4260 | 0.4293 | 0.1014 |
    | 2×2 cell | 0.4259 | 0.4203 | 0.1015 |

  - A logistic level `a + b · world food12` fitted online on the lattice's log odds: 2020–22 0.3472 → 0.3489; 2009–19 0.0710 → 0.0707. Its slope was +0.15 by 2021.
  - The history (2007 over-forecast, 2008 nothing) teaches the wrong level shift.
- **Buffers and drawdowns.** The test is the decline over the prior 12, 24 or 36 months in FX reserves (months of imports, WDI), crude stocks (JODI, country) and cereal production (FAOSTAT), read from the archived series. Year rows:

  | buffer | months | corr with event, 2007–19 | corr with all−base edge | corr with down−all edge | 2020–22: event rate, top-quintile drawdown / rest |
  |---|---|---|---|---|---|
  | reserves (months) | 12 | +0.04 | +0.05 | -0.01 | 0.129 / 0.485 |
  | reserves (months) | 24 | +0.05 | +0.00 | +0.11 | 0.391 / 0.419 |
  | reserves (months) | 36 | +0.04 | -0.00 | +0.04 | 0.362 / 0.426 |
  | crude stocks | 12 | -0.09 | +0.12 | -0.06 | 0.276 / 0.384 |
  | crude stocks | 24 | -0.09 | +0.07 | -0.04 | 0.207 / 0.409 |
  | cereal production | 24 | -0.03 | -0.03 | +0.05 | 0.395 / 0.430 |

  - No buffer drawdown predicts the events or the edge. In 2020–22 the countries with the deepest reserve drawdown had *fewer* shocks.
  - At the world level, across origins 2005–2022, the yearly event rate correlates −0.20 with mean reserve drawdown, 0.18 with crude-stock drawdown, and **0.65 with world food price momentum**. `all`'s yearly edge over the base correlates **0.68** with world food momentum and −0.01 with reserves.
  - The system-state signal that tracks when the edge appears is the world food price, a price, not a buffer, and the year node does not read it.

## Q4. How fast the evidence built up

This is the lattice's own learn-time evidence at the year node (t48): each year-end's newly learned rows (roughly origin Y−4), per row, and the discounted cumulative gap.

| year-end | rows learned | all − base per row | down − all per row | gap all−base (0.995) | gap down−all (0.995) | gap down−all (0.8) | gap down−all (0.5) | weight on all | weight on down |
|---|---|---|---|---|---|---|---|---|---|
| 2007 | 160 | +0.016 | -0.016 | +2.5 | -2.5 | -2.5 | -2.5 | 0.257 | 0.020 |
| 2008 | 148 | -0.073 | +0.074 | -8.2 | +8.4 | +8.9 | +9.7 | 0.000 | 0.072 |
| 2009 | 146 | -0.151 | +0.138 | -30.2 | +28.6 | +27.3 | +25.0 | 0.000 | 0.009 |
| 2010 | 152 | +0.262 | -0.241 | +9.7 | -8.2 | -14.8 | -24.1 | 0.999 | 0.000 |
| 2011 | 133 | +0.434 | -0.033 | +67.5 | -12.5 | -16.1 | -16.4 | 1.000 | 0.000 |
| 2012 | 105 | +0.010 | -1.934 | +68.2 | -215.5 | -216.0 | -211.3 | 1.000 | 0.000 |
| 2013 | 159 | -0.044 | -0.253 | +60.8 | -254.6 | -213.0 | -145.8 | 0.868 | 0.000 |
| 2015 | 149 | +0.062 | -0.128 | +73.9 | -299.1 | -177.8 | -69.6 | 1.000 | 0.000 |
| 2017 | 158 | +0.010 | -0.266 | +78.0 | -345.9 | -162.1 | -63.3 | 1.000 | 0.000 |
| 2019 | 151 | +0.027 | -0.033 | +79.1 | -348.4 | -109.5 | -21.3 | 1.000 | 0.000 |
| 2020 | 149 | +0.022 | -0.044 | +82.0 | -353.2 | -94.1 | -17.1 | 1.000 | 0.000 |
| 2021 | 152 | +0.044 | -0.042 | +88.2 | -357.9 | -81.7 | -15.0 | 1.000 | 0.000 |
| 2022 | 153 | +0.046 | -0.054 | +94.7 | -364.3 | -73.6 | -15.7 | 1.000 | 0.000 |
| 2023 | 147 | +0.000 | +0.004 | +94.3 | -361.9 | -58.3 | -7.2 | 1.000 | 0.000 |
| 2024 | 151 | -0.033 | **+0.301** | +88.8 | -314.6 | -1.1 | +41.9 | 0.000 | **1.000** |
| 2025 | 146 | +0.613 | -0.218 | +177.8 | -344.8 | -32.7 | -10.8 | 1.000 | 0.000 |

- **`all` was trusted from 2010 on.** Its learn-time evidence came from the 2007–08 episode, learned at year-ends 2010–11: +0.26 and +0.43 nats per row, +67 nats by 2011.
  - That evidence holds at any discount. The gap of `all` over the base at 2021 is +88 (discount 0.995), +43 (0.9), +22 (0.8), +9 (0.5) and +8 (0.3) nats.
  - Its gap over every other group at 2021 (0.995) is +43 to +117 nats: economy +43, demography +54, world +54, violence +54, history +68, up +117.
  - So under t48 no discount was needed: `all` was already selected with weight 1.000.
- **Measured on forecast-time scores instead, `all`'s pooled edge at 2021 was about zero** (+0.004 to −0.003 per row for λ from 1 to 0.5). The same model has a large learn-time edge and none out of sample. The learn-time model rescores old rows after learning later-known earlier origins, and for `all` that matters.
- **Under A the learn-time gain of A's own `all` was negative from 2015** (−0.042 per row at 2015, −0.020 at 2021, −0.015 at 2022). A's highest-gain models at 2021 were economy +0.021, up +0.018, patronage +0.017, demography +0.016, violence +0.016 and world +0.016. That gives near-even weights; the top weight was 0.13.
- **`down`, the 2020–21 winner, could not have been trusted in time.** The outcomes of origins 2020–21 are learned only at year-ends 2024–25. Its prior learn-time record was catastrophic: −1.93 per row at 2012, a gap of −358 nats at 2021 (discount 0.995) and −15 nats even at 0.5. It flipped to weight 1.000 at 2024, after the event, and back to 0 at 2025.
- **The lag is structural.** A year row is learned about 3–4 years after its origin, so evidence from a transition cannot reach the weights of the origins that need it. A two-year episode is over before any of its rows is scored.

## Q5. Does the pattern generalise?

### Conflict onsets (year nodes, t48)

| origin | n | events | lattice | all | base | down | up | history | economy | world | contradiction | t48 top weight |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2018 | 164 | 16 | 0.0644 | 0.0644 | 0.0881 | 0.0794 | 0.0740 | 0.0656 | 0.0837 | 0.0882 | 0.0789 | all 1.00 |
| 2019 | 163 | 15 | 0.0637 | 0.0637 | 0.0836 | 0.0737 | 0.0700 | 0.0644 | 0.0796 | 0.0837 | 0.0753 | all 1.00 |
| 2020 | 164 | 14 | 0.0652 | 0.0652 | 0.0782 | 0.0709 | 0.0675 | 0.0629 | 0.0757 | 0.0783 | 0.0721 | all 1.00 |
| 2021 | 167 | 13 | 0.0552 | 0.0552 | 0.0721 | 0.0622 | 0.0632 | 0.0554 | 0.0668 | 0.0722 | 0.0663 | all 1.00 |
| 2022 | 166 | 14 | 0.0576 | 0.0576 | 0.0773 | 0.0659 | 0.0642 | 0.0565 | 0.0739 | 0.0775 | 0.0690 | all 1.00 |

- **There is no transition.** The rate is a steady 0.08–0.10. `all` and `history` are the two best models in every year, and the models are moderately redundant (within-year correlation 0.47).
- **A's −0.0068 is dilution.** A was worse than t48 in every year from 2010 to 2022, by 0.001 to 0.012 Brier (checked on the on-disk forecasts). `all` is best, but `history`, `up` and `down` also gain over the base, so proportional weights spread the mass.
- **No state dependence with world food stress.** Stressed rate 0.079 against 0.089 calm, and the same ranking in both states (`all` > `history` > `up`):

  | state | lattice | base | all | history | up |
  |---|---|---|---|---|---|
  | calm, 2007–19 | -0.240 | -0.300 | -0.236 | -0.247 | -0.257 |
  | stressed, 2007–19 | -0.222 | -0.279 | -0.207 | -0.222 | -0.237 |

- **Oracle:**

  | period | lattice | oracle convex weights | constant at realized rate |
  |---|---|---|---|
  | 2020–22 | 0.0593 | 0.0579 | 0.0757 |
  | 2003–22 | 0.0637 | 0.0622 | 0.0749 |

  Little is on the table, and the oracle mixes `all` and `history`.

### Market crashes (year nodes, t48)

| origin | n | events | lattice | all | base | down | economy | world | history | t48 top weight |
|---|---|---|---|---|---|---|---|---|---|---|
| 2015 | 44 | 2 | 0.3103 | 0.1604 | 0.1212 | 0.1116 | 0.0948 | 0.3094 | 0.1430 | world 1.00 |
| 2017 | 47 | 2 | 0.1566 | 0.1566 | 0.0989 | 0.0946 | 0.0879 | 0.1885 | 0.1295 | all 1.00 |
| 2018 | 47 | 10 | 0.1766 | 0.1766 | 0.1704 | 0.1647 | 0.1840 | 0.1949 | 0.1780 | all 1.00 |
| 2019 | 45 | 8 | 0.1883 | 0.1883 | 0.1512 | 0.1458 | 0.1781 | 0.1722 | 0.1530 | all 1.00 |
| 2020 | 37 | 3 | 0.1094 | 0.1094 | 0.0979 | 0.0941 | 0.1088 | 0.1113 | 0.1029 | all 1.00 |
| 2021 | 47 | 4 | 0.1066 | 0.1066 | 0.0994 | 0.0969 | 0.0909 | 0.1104 | 0.0994 | all 1.00 |
| 2022 | 44 | 2 | 0.0644 | 0.0644 | 0.0767 | 0.0761 | 0.1144 | 0.0851 | 0.0742 | all 1.00 |

- **There is strong state dependence.** The state is the VIX at the origin (standardized, ≥ 0.5 sd: year-ends 2007, 2008, 2011, 2018, 2020):

  | state, 2007–19 | rows | rate | lattice | base | all | world | down | economy | history |
  |---|---|---|---|---|---|---|---|---|---|
  | calm | 362 | 0.077 | -0.553 | -0.461 | -0.513 | -0.617 | -0.452 | -0.447 | -0.478 |
  | stressed | 132 | 0.432 | -0.595 | -0.781 | -0.600 | -0.601 | -0.804 | -0.755 | -0.757 |

  - In calm states the base, `down` and economy beat `all`. In stressed states `all` and world beat the base by 0.18 nats per row.
  - The selection on `all` costs the calm years: 2015 Brier 0.310 (with world selected) against economy's 0.095; 2017 0.157 against 0.088; 2023 0.161 against 0.068.
  - In 2020–22 the high-VIX rows had a rate of only 0.081, and the gap between models narrows.
- **Oracle, and the models are least redundant here** (within-year correlation 0.09):

  | period | lattice | oracle convex | constant at realized rate |
  |---|---|---|---|
  | 2020–22 | 0.0929 | 0.0760 | 0.0650 |
  | 2003–22 | 0.1737 | 0.1237 | 0.0619 |

  The lattice is worse than a constant.

So:
- Food price shocks are a level shock that no model sees.
- Conflict onsets are a stable case where selecting `all` is right and proportional weights dilute it.
- Market crashes are the real state-dependent case: the right model differs between calm and stressed states, and the posterior's selection of `all` fits the stressed years and costs the calm ones.

## Q6. Oracle weights per year (ceiling)

Oracle convex weights on probabilities, minimising each year's Brier on that year's rows (exponentiated gradient, 400–600 iterations). Food, year nodes:

| origin | n | rate | lattice | best single model | oracle convex | oracle's top weights | constant at the year's rate |
|---|---|---|---|---|---|---|---|
| 2013 | 158 | 0.051 | 0.0637 | world 0.0481 | 0.0467 | world 0.53, regime 0.25, economy 0.10 | 0.0481 |
| 2014 | 158 | 0.095 | 0.0857 | violence 0.0813 | 0.0804 | military 0.33, violence 0.29, economy 0.20 | 0.0859 |
| 2015 | 151 | 0.073 | 0.0680 | violence 0.0626 | 0.0622 | violence 0.37, economy 0.26, patronage 0.15 | 0.0675 |
| 2016 | 149 | 0.060 | 0.0581 | demography 0.0526 | 0.0525 | demography 0.70, violence 0.19 | 0.0568 |
| 2017 | 152 | 0.086 | 0.0741 | history 0.0722 | 0.0718 | history 0.55, demography 0.29, all 0.15 | 0.0782 |
| 2018 | 153 | 0.078 | 0.0675 | history 0.0642 | 0.0638 | history 0.68, all 0.23, economy 0.10 | 0.0723 |
| 2019 | 147 | 0.088 | 0.0854 | regime 0.0795 | 0.0791 | regime 0.35, all 0.19, down 0.16 | 0.0806 |
| 2020 | 151 | 0.490 | 0.4213 | down 0.3770 | 0.3770 | down 1.00 | 0.2499 |
| 2021 | 146 | 0.500 | 0.4151 | down 0.3898 | 0.3898 | down 1.00 | 0.2500 |
| 2022 | 86 | 0.128 | 0.1015 | all 0.1015 | 0.0995 | all 0.56, economy 0.44 | 0.1115 |
| 2023 | 131 | 0.053 | 0.0447 | all 0.0447 | 0.0425 | all 0.52, history 0.28, others 0.19 | 0.0506 |

Pooled ceilings (Brier):

| kind, period | lattice (t48) | A | `all` alone | oracle convex per year | constant at realized rate |
|---|---|---|---|---|---|
| food, 2020–22 | 0.3472 | 0.3760 | 0.3472 | 0.3196 | 0.2189 |
| food, 2003–22 | 0.1503 | 0.1600 | 0.1628 | 0.1400 | 0.1093 |
| food, 2013–19 | 0.0718 | – | 0.0726 | 0.0651 | 0.0699 |
| conflict, 2020–22 | 0.0593 | – | 0.0593 | 0.0579 | 0.0757 |
| market crash, 2020–22 | 0.0929 | – | 0.0929 | 0.0760 | 0.0650 |
| market crash, 2003–22 | 0.1737 | – | 0.1642 | 0.1237 | 0.0619 |

- At the food quarter nodes the oracle gains nothing: within 0.0003 of the lattice in every year. In 2021 the constant at the realized rate is 0.210 against the lattice's 0.288.
- In food price shocks the weights can recover at most about 0.028 in 2020–22, and about 0.007 per year in calm years. The 2020–21 miss is mostly level: 0.13 is recoverable only by knowing the year's rate.

## What this says for the weighting rule

1. **Evaluate weightings with the models held fixed, or cut the loop.** The loop runs from the node's combined forecast back into the inputs of its own models: own state, contradiction gaps, errors, the quarters' offset back through `:up`, and the ancestors back through `:down`.
   - Today a weighting rule is judged together with what it does to the `all`, `down` and `up` models. In food that is 86% of A's 2020–22 loss.
   - A rule that reads states net of the weighting would make the comparison clean. Candidates: states from a fixed reference combination, or `:up-net-of-downscale`-style netting extended to `:down` and the own-state reads.
2. **Gains measured from the base are mostly shared, but the bigger issue is which evidence is used.** The averaging's learn-time evidence is decisive for `all` in food (+88 nats). Forecast-time out-of-sample scores show about zero. A rule should be checked on both.
3. **State-conditioned evidence is real, but thin and episode-bound.** Before 2020, stressed food states give `all` +0.17 per row while its calm-state edge is −0.03. Market crashes show the same split. A rule that keeps evidence by system state can therefore weigh `all` in stressed years without letting its calm-year losses erase that.
   - It cannot find a model that has never been right in any state, as `down` in 2020–21 had not.
   - The stressed set is two food episodes. Shrink it toward the pooled evidence.
4. **Transitions need a level input, not a weight.** The year node does not read the system variable that tracks the 2020–22 food rate: world food price momentum, r = 0.65 with the yearly rate. Buffers (FX reserves, crude stocks, cereal output) carry nothing at the 12–36 month drawdowns tested.

## Files

- Report: this file.
- Code snapshot: origin/structure e79d91a, exported to a scratch directory (tools/ copied into src/ there so the nREPL could load them; data is a symlink, read only).
