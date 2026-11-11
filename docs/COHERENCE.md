# Coherence of the base's book (recursive step 1)

A diagnostic, 11 November 2026 (bead bnm-56c.18, docs/RECURSIVE.md §4 and §7 step 1). Nothing is fitted and no forecast changes: `tools/bnm/coherence_report.clj` reads a run's `forecasts.tsv` (the forecasts as issued; without `:state-from` they are also the states) and measures how far the book is from the identities the recursive model will hold exactly.

    jolt -A:tools -M -m bnm.coherence-report --run t50-world-norel --out <file>

**What is measured.** A gap is a node's p less the composition of its parts, so it is positive when the node says more than its parts do.
- **Place tree** (C1 for rates, C4 at v = 0). Each parent at each scale and origin is compared with its children at the same origin:
  - its p against `1 − Π(1 − p_c)`, computed with `compose/any-onset` with no frailty;
  - its rate per month, `−log(1 − p)/w`, against `Σ λ_c` (`compose/rate-sum`), reported as the median of `log(λ_P / Σ λ_c)`.
  - Countries sit under their subregion, subregions under their region, and regions under the world. These are the same identity on two scales: with v = 0, `P_any = 1 − exp(−w Σ λ_c)`.
- **Time tree** (C5). Each year node's p for (t, t+24] is compared with `compose/survival-chain` over its quarters' per-month rates. The pieces are:
  - the lead quarter issued at t−3, for (t, t+3];
  - the lead quarter issued at t, for (t+3, t+12];
  - the w2 quarter issued at t, for (t+12, t+24].
  The pieces do not overlap. The quarters' own window probabilities do overlap, and the chain refuses them. In the time table, the "log(λ/Σλ)" column is `log(Λ_year / Λ_chain)`.
- C1 for stocks has nothing to measure on a lattice book, which holds no stocks. It applies from step 6, when nodes carry bnm.buffers stocks.

Periods are by the year of the origin. The development period is 2003–2022.

## Headline (t50-world-norel, the base of record; 2003–2022)

- **Parents say less than their children.** The year nodes fall below the independent composition of their children:
  - mean gap −0.104 at the subregion (median rate ratio e^−0.45 ≈ 0.64), −0.057 at the region and −0.032 at the world;
  - 71% of parent-year rows are below the composition.
  The gap is smaller at the quarters: −0.046 at the subregion lead, −0.019 at the region lead.
- **The sign is the one a shared frailty gives.** `compose_test` C4 shows the direction: at a fixed expected count, frailty variance lowers P(any onset), because the onsets bunch in the bad draws. Positive dependence among the children is therefore one reading of parents below independence. The other reading is that the countries' small p are too high in sum: the parent's own catalogue rate is what it learns from, and the countries' errors add. The report cannot tell these apart. Step 3 (the frailty z per node) can, because it estimates v directly.
- **The largest place gaps are in the kinds with common shocks**:
  - food price shocks: region −0.239, subregion −0.164;
  - growth collapses: subregion −0.219;
  - market crashes: subregion −0.191;
  - banking crises: subregion −0.162.
  Inflation crises, war onsets and currency crises at the world node go the other way: the parent says more than its children.
- **Time tree: the year runs above its quarters at the country.** The rate ratio median is +0.28 (the year's cumulative hazard is about 1.3 × the quarters'), but the mean p gap is only +0.007. At the subregion the two agree (−0.011; median log ratio −0.02). At the region and world the year is below its quarters (−0.047, −0.027).
  - By kind, the country-level excess is largest in protest surges (+0.68), war onsets (+0.42), coups (+0.41) and violence surges (+0.38).
  - The quarters carry the year's forecast as their offset, pooled 0.5 with named's (the downscale). So this gap is what the quarters' own models take back from the year, not two independent views.
  - Food price shocks and market crashes have the largest absolute time gaps (mean |gap| 0.111 and 0.181; p90 0.24 and 0.39). These are the kinds whose quarters read world prices that the year node does not read: audit issue 3, step 2.
- **t48 against t50.** No pooled mean gap moves by more than 0.016 between the two bases (the largest is the subregion quarters after 2023; in 2003–2022 the largest is 0.013, region year). For example, the subregion year mean gap is −0.095 in t48 against −0.104 in t50, and the country time gap is +0.008 against +0.007. The incoherence belongs to the design, not to the input change.

What this means for the build plan:
- The year's λ built from its quarters' λ (§6.1, step 4) and the parent's rate built from its children's (§2.1) would each move the base's level by these amounts. They are therefore model changes to score, not bookkeeping.
- The parents' shortfall against independence is the quantity the frailty variance in step 3 has to explain.


## Tables

### t50-world-norel

Place tree (C1 rates, C4 at v = 0), all kinds pooled: parent's p against 1 - Π(1 - p_c)

| period | scale | level | n | mean p | mean composed | mean gap | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|
| 1946-2002 | quarter-lead | region | 14820 | 0.3522 | 0.5113 | -0.1590 | 0.1970 | 0.4768 | -0.5330 |
| 1946-2002 | quarter-lead | subregion | 57456 | 0.2199 | 0.4015 | -0.1816 | 0.2175 | 0.5873 | -0.6980 |
| 1946-2002 | quarter-lead | world | 2964 | 0.5928 | 0.8253 | -0.2325 | 0.2584 | 0.5213 | -0.8251 |
| 1946-2002 | quarter-w2 | region | 14820 | 0.4085 | 0.5665 | -0.1580 | 0.2025 | 0.4725 | -0.5572 |
| 1946-2002 | quarter-w2 | subregion | 57456 | 0.2574 | 0.4474 | -0.1900 | 0.2331 | 0.5677 | -0.7430 |
| 1946-2002 | quarter-w2 | world | 2964 | 0.6704 | 0.8718 | -0.2014 | 0.2266 | 0.5109 | -0.8016 |
| 1946-2002 | year | region | 3705 | 0.5083 | 0.6723 | -0.1639 | 0.2021 | 0.4561 | -0.6020 |
| 1946-2002 | year | subregion | 14364 | 0.3452 | 0.5456 | -0.2004 | 0.2364 | 0.5050 | -0.7782 |
| 1946-2002 | year | world | 741 | 0.7829 | 0.9405 | -0.1576 | 0.1750 | 0.4658 | -0.7610 |
| 2003-2022 | quarter-lead | region | 5200 | 0.3592 | 0.3781 | -0.0189 | 0.0981 | 0.2307 | +0.0088 |
| 2003-2022 | quarter-lead | subregion | 20160 | 0.1485 | 0.1943 | -0.0458 | 0.0796 | 0.2167 | -0.0869 |
| 2003-2022 | quarter-lead | world | 1040 | 0.7533 | 0.8348 | -0.0815 | 0.1141 | 0.2291 | -0.3032 |
| 2003-2022 | quarter-w2 | region | 5200 | 0.4226 | 0.4307 | -0.0081 | 0.1082 | 0.2573 | +0.0037 |
| 2003-2022 | quarter-w2 | subregion | 20160 | 0.1791 | 0.2318 | -0.0528 | 0.0919 | 0.2456 | -0.1497 |
| 2003-2022 | quarter-w2 | world | 1040 | 0.8425 | 0.8867 | -0.0442 | 0.0919 | 0.2278 | -0.2862 |
| 2003-2022 | year | region | 1300 | 0.5431 | 0.6000 | -0.0570 | 0.1165 | 0.2654 | -0.2283 |
| 2003-2022 | year | subregion | 5040 | 0.2910 | 0.3954 | -0.1044 | 0.1469 | 0.3454 | -0.4452 |
| 2003-2022 | year | world | 260 | 0.9268 | 0.9588 | -0.0320 | 0.0666 | 0.1591 | -0.4786 |
| 2023- | quarter-lead | region | 780 | 0.3863 | 0.3831 | +0.0031 | 0.0950 | 0.2535 | -0.0084 |
| 2023- | quarter-lead | subregion | 3024 | 0.1485 | 0.1762 | -0.0277 | 0.0653 | 0.1788 | +0.0154 |
| 2023- | quarter-lead | world | 156 | 0.7459 | 0.8683 | -0.1224 | 0.1306 | 0.3186 | -0.4160 |
| 2023- | quarter-w2 | region | 780 | 0.4719 | 0.4561 | +0.0158 | 0.1013 | 0.2339 | -0.0150 |
| 2023- | quarter-w2 | subregion | 3024 | 0.1940 | 0.2310 | -0.0371 | 0.0832 | 0.2268 | -0.0444 |
| 2023- | quarter-w2 | world | 156 | 0.8276 | 0.9078 | -0.0802 | 0.0838 | 0.1720 | -0.4899 |
| 2023- | year | region | 195 | 0.5696 | 0.6127 | -0.0432 | 0.1046 | 0.2338 | -0.2316 |
| 2023- | year | subregion | 756 | 0.3101 | 0.3840 | -0.0739 | 0.1292 | 0.2942 | -0.2893 |
| 2023- | year | world | 39 | 0.9116 | 0.9693 | -0.0577 | 0.0580 | 0.1203 | -0.4896 |

Time tree (C5): year p against its quarters' per-month rates, all kinds pooled

| period | level | n | mean p | mean composed | mean gap | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|
| 1946-2002 | country | 118047 | 0.1917 | 0.2485 | -0.0567 | 0.0858 | 0.2188 | -0.0862 |
| 1946-2002 | region | 3705 | 0.5083 | 0.6053 | -0.0970 | 0.1288 | 0.2308 | -0.3848 |
| 1946-2002 | subregion | 14364 | 0.3452 | 0.4241 | -0.0789 | 0.1187 | 0.2239 | -0.2984 |
| 1946-2002 | world | 741 | 0.7829 | 0.8474 | -0.0644 | 0.0889 | 0.2158 | -0.2974 |
| 2003-2022 | country | 41420 | 0.0891 | 0.0820 | +0.0071 | 0.0321 | 0.0762 | +0.2787 |
| 2003-2022 | region | 1300 | 0.5431 | 0.5901 | -0.0471 | 0.1118 | 0.2452 | -0.2104 |
| 2003-2022 | subregion | 5040 | 0.2910 | 0.3016 | -0.0106 | 0.0785 | 0.1883 | -0.0248 |
| 2003-2022 | world | 260 | 0.9268 | 0.9535 | -0.0266 | 0.0480 | 0.1133 | -0.2968 |
| 2023- | country | 6213 | 0.0832 | 0.0737 | +0.0095 | 0.0318 | 0.0754 | +0.2279 |
| 2023- | region | 195 | 0.5696 | 0.6257 | -0.0561 | 0.1172 | 0.2819 | -0.2172 |
| 2023- | subregion | 756 | 0.3101 | 0.3188 | -0.0087 | 0.0753 | 0.1715 | -0.0597 |
| 2023- | world | 39 | 0.9116 | 0.9208 | -0.0092 | 0.0380 | 0.0909 | -0.2816 |

Place tree, 2003-2022, year scale, by kind

| kind | level | n | mean p | mean composed | mean gap | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|
| banking_crisis | region | 100 | 0.3715 | 0.4464 | -0.0749 | 0.1205 | 0.2559 | -0.2481 |
| banking_crisis | subregion | 380 | 0.1627 | 0.3245 | -0.1618 | 0.1691 | 0.3361 | -0.8914 |
| banking_crisis | world | 20 | 0.8149 | 0.9021 | -0.0872 | 0.0912 | 0.1714 | -0.3591 |
| conflict_onset | region | 100 | 0.6195 | 0.6212 | -0.0017 | 0.0690 | 0.1518 | -0.0481 |
| conflict_onset | subregion | 400 | 0.3068 | 0.4393 | -0.1325 | 0.1485 | 0.3120 | -0.5592 |
| conflict_onset | world | 20 | 0.9726 | 0.9991 | -0.0265 | 0.0265 | 0.0349 | -0.6486 |
| coup | region | 100 | 0.5920 | 0.6253 | -0.0334 | 0.0707 | 0.1550 | -0.1830 |
| coup | subregion | 400 | 0.2790 | 0.3390 | -0.0600 | 0.0957 | 0.2321 | -0.2368 |
| coup | world | 20 | 0.9266 | 0.9973 | -0.0706 | 0.0706 | 0.1318 | -0.7576 |
| currency_crisis | region | 100 | 0.4441 | 0.4834 | -0.0393 | 0.0919 | 0.1890 | -0.1644 |
| currency_crisis | subregion | 400 | 0.1861 | 0.2325 | -0.0464 | 0.0910 | 0.2402 | -0.1246 |
| currency_crisis | world | 20 | 0.9710 | 0.9151 | +0.0560 | 0.0656 | 0.1610 | +0.0783 |
| fiscal_consolidation | region | 100 | 0.9169 | 0.9481 | -0.0312 | 0.0454 | 0.1059 | -0.5971 |
| fiscal_consolidation | subregion | 400 | 0.7173 | 0.8407 | -0.1234 | 0.1357 | 0.2660 | -0.5361 |
| fiscal_consolidation | world | 20 | 0.9680 | 1.0000 | -0.0320 | 0.0320 | 0.0351 | -1.4825 |
| food_price_shock | region | 100 | 0.5730 | 0.8118 | -0.2388 | 0.2496 | 0.4621 | -0.7690 |
| food_price_shock | subregion | 400 | 0.4286 | 0.5922 | -0.1637 | 0.2666 | 0.4950 | -0.6125 |
| food_price_shock | world | 20 | 0.8623 | 0.9712 | -0.1089 | 0.1386 | 0.4659 | -0.6637 |
| growth_collapse | region | 100 | 0.4544 | 0.5997 | -0.1454 | 0.1521 | 0.2704 | -0.4475 |
| growth_collapse | subregion | 400 | 0.2295 | 0.4488 | -0.2193 | 0.2305 | 0.4415 | -0.8455 |
| growth_collapse | world | 20 | 0.8277 | 0.9516 | -0.1239 | 0.1239 | 0.1838 | -0.5278 |
| inflation_crisis | region | 100 | 0.2932 | 0.2495 | +0.0436 | 0.0719 | 0.1534 | +0.2641 |
| inflation_crisis | subregion | 400 | 0.0739 | 0.1006 | -0.0267 | 0.0496 | 0.1279 | -0.0449 |
| inflation_crisis | world | 20 | 0.9532 | 0.8160 | +0.1372 | 0.1372 | 0.2573 | +0.6725 |
| market_crash | region | 100 | 0.5523 | 0.6530 | -0.1007 | 0.2058 | 0.3555 | -0.4259 |
| market_crash | subregion | 260 | 0.4084 | 0.5990 | -0.1906 | 0.2578 | 0.5210 | -0.5826 |
| market_crash | world | 20 | 0.8987 | 0.9820 | -0.0833 | 0.0833 | 0.1050 | -0.6019 |
| protest_surge | region | 100 | 0.4687 | 0.5445 | -0.0758 | 0.1097 | 0.2694 | -0.3247 |
| protest_surge | subregion | 400 | 0.2220 | 0.2348 | -0.0129 | 0.1082 | 0.2365 | +0.1317 |
| protest_surge | world | 20 | 0.9923 | 0.9833 | +0.0091 | 0.0108 | 0.0310 | +0.1259 |
| sovereign_default | region | 100 | 0.4665 | 0.4985 | -0.0320 | 0.0976 | 0.1769 | -0.0824 |
| sovereign_default | subregion | 400 | 0.1803 | 0.2597 | -0.0794 | 0.1038 | 0.2456 | -0.4535 |
| sovereign_default | world | 20 | 0.9101 | 0.9593 | -0.0492 | 0.0492 | 0.0743 | -0.3186 |
| violence_surge | region | 100 | 0.7550 | 0.8388 | -0.0838 | 0.1271 | 0.3122 | -0.3672 |
| violence_surge | subregion | 400 | 0.4526 | 0.5640 | -0.1113 | 0.1906 | 0.3934 | -0.3880 |
| violence_surge | world | 20 | 0.9915 | 0.9993 | -0.0078 | 0.0078 | 0.0107 | -0.5828 |
| war_onset | region | 100 | 0.5528 | 0.4799 | +0.0729 | 0.1029 | 0.1888 | +0.3012 |
| war_onset | subregion | 400 | 0.1707 | 0.2328 | -0.0621 | 0.1019 | 0.2645 | -0.2894 |
| war_onset | world | 20 | 0.9599 | 0.9883 | -0.0284 | 0.0284 | 0.0379 | -0.3791 |

Time tree, 2003-2022, by kind (every level)

| kind | n | mean p | mean composed | mean gap | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|
| banking_crisis | 3100 | 0.0892 | 0.0841 | +0.0051 | 0.0350 | 0.0802 | +0.2548 |
| conflict_onset | 4020 | 0.1264 | 0.1244 | +0.0020 | 0.0328 | 0.0860 | +0.2704 |
| coup | 4100 | 0.0966 | 0.0760 | +0.0206 | 0.0319 | 0.0804 | +0.4118 |
| currency_crisis | 3920 | 0.0659 | 0.0656 | +0.0003 | 0.0172 | 0.0419 | +0.1588 |
| fiscal_consolidation | 3940 | 0.3335 | 0.3445 | -0.0110 | 0.0387 | 0.0893 | -0.0113 |
| food_price_shock | 3820 | 0.2299 | 0.2507 | -0.0208 | 0.1114 | 0.2415 | +0.2461 |
| growth_collapse | 3940 | 0.1032 | 0.1035 | -0.0004 | 0.0269 | 0.0612 | +0.0540 |
| inflation_crisis | 3840 | 0.0319 | 0.0304 | +0.0015 | 0.0084 | 0.0194 | +0.3007 |
| market_crash | 1320 | 0.3620 | 0.3403 | +0.0217 | 0.1812 | 0.3870 | +0.2157 |
| protest_surge | 4000 | 0.0710 | 0.0571 | +0.0139 | 0.0328 | 0.0909 | +0.6838 |
| sovereign_default | 3960 | 0.0677 | 0.0700 | -0.0023 | 0.0186 | 0.0436 | +0.1234 |
| violence_surge | 4080 | 0.1605 | 0.1440 | +0.0165 | 0.0481 | 0.0977 | +0.3837 |
| war_onset | 3980 | 0.0651 | 0.0556 | +0.0095 | 0.0216 | 0.0531 | +0.4196 |

### t48-world-norel

Place tree (C1 rates, C4 at v = 0), all kinds pooled: parent's p against 1 - Π(1 - p_c)

| period | scale | level | n | mean p | mean composed | mean gap | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|
| 1946-2002 | quarter-lead | region | 14820 | 0.3527 | 0.5112 | -0.1585 | 0.1970 | 0.4763 | -0.5460 |
| 1946-2002 | quarter-lead | subregion | 57456 | 0.2201 | 0.4018 | -0.1817 | 0.2159 | 0.5875 | -0.6962 |
| 1946-2002 | quarter-lead | world | 2964 | 0.5908 | 0.8244 | -0.2336 | 0.2593 | 0.5213 | -0.8341 |
| 1946-2002 | quarter-w2 | region | 14820 | 0.4080 | 0.5647 | -0.1567 | 0.2013 | 0.4729 | -0.5542 |
| 1946-2002 | quarter-w2 | subregion | 57456 | 0.2567 | 0.4475 | -0.1908 | 0.2319 | 0.5675 | -0.7447 |
| 1946-2002 | quarter-w2 | world | 2964 | 0.6683 | 0.8705 | -0.2023 | 0.2271 | 0.5109 | -0.7968 |
| 1946-2002 | year | region | 3705 | 0.5106 | 0.6751 | -0.1645 | 0.2023 | 0.4586 | -0.6064 |
| 1946-2002 | year | subregion | 14364 | 0.3474 | 0.5484 | -0.2010 | 0.2370 | 0.5054 | -0.7740 |
| 1946-2002 | year | world | 741 | 0.7816 | 0.9402 | -0.1586 | 0.1766 | 0.4658 | -0.7933 |
| 2003-2022 | quarter-lead | region | 5200 | 0.3598 | 0.3836 | -0.0238 | 0.1029 | 0.2479 | -0.0094 |
| 2003-2022 | quarter-lead | subregion | 20160 | 0.1501 | 0.1915 | -0.0414 | 0.0763 | 0.2070 | -0.0755 |
| 2003-2022 | quarter-lead | world | 1040 | 0.7503 | 0.8335 | -0.0832 | 0.1137 | 0.2313 | -0.2978 |
| 2003-2022 | quarter-w2 | region | 5200 | 0.4207 | 0.4377 | -0.0170 | 0.1110 | 0.2629 | +0.0012 |
| 2003-2022 | quarter-w2 | subregion | 20160 | 0.1819 | 0.2283 | -0.0464 | 0.0878 | 0.2276 | -0.1173 |
| 2003-2022 | quarter-w2 | world | 1040 | 0.8368 | 0.8824 | -0.0456 | 0.0913 | 0.2244 | -0.2700 |
| 2003-2022 | year | region | 1300 | 0.5429 | 0.6127 | -0.0698 | 0.1288 | 0.2934 | -0.2764 |
| 2003-2022 | year | subregion | 5040 | 0.2989 | 0.3935 | -0.0945 | 0.1407 | 0.3246 | -0.4186 |
| 2003-2022 | year | world | 260 | 0.9205 | 0.9558 | -0.0353 | 0.0700 | 0.1756 | -0.4888 |
| 2023- | quarter-lead | region | 780 | 0.3881 | 0.3811 | +0.0070 | 0.0895 | 0.2104 | -0.0236 |
| 2023- | quarter-lead | subregion | 3024 | 0.1488 | 0.1920 | -0.0432 | 0.0770 | 0.2308 | -0.0461 |
| 2023- | quarter-lead | world | 156 | 0.7439 | 0.8685 | -0.1246 | 0.1335 | 0.3293 | -0.4188 |
| 2023- | quarter-w2 | region | 780 | 0.4702 | 0.4535 | +0.0167 | 0.1000 | 0.2251 | -0.0130 |
| 2023- | quarter-w2 | subregion | 3024 | 0.1940 | 0.2465 | -0.0526 | 0.0936 | 0.2593 | -0.0748 |
| 2023- | quarter-w2 | world | 156 | 0.8289 | 0.9056 | -0.0767 | 0.0812 | 0.1753 | -0.4748 |
| 2023- | year | region | 195 | 0.5680 | 0.6152 | -0.0472 | 0.1046 | 0.2296 | -0.2651 |
| 2023- | year | subregion | 756 | 0.3144 | 0.3983 | -0.0838 | 0.1348 | 0.2992 | -0.3562 |
| 2023- | year | world | 39 | 0.9110 | 0.9614 | -0.0505 | 0.0509 | 0.1300 | -0.5145 |

Time tree (C5): year p against its quarters' per-month rates, all kinds pooled

| period | level | n | mean p | mean composed | mean gap | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|
| 1946-2002 | country | 117078 | 0.1933 | 0.2496 | -0.0563 | 0.0858 | 0.2186 | -0.0748 |
| 1946-2002 | region | 3705 | 0.5106 | 0.6050 | -0.0944 | 0.1276 | 0.2308 | -0.3707 |
| 1946-2002 | subregion | 14364 | 0.3474 | 0.4240 | -0.0766 | 0.1171 | 0.2234 | -0.2788 |
| 1946-2002 | world | 741 | 0.7816 | 0.8452 | -0.0636 | 0.0902 | 0.2151 | -0.3017 |
| 2003-2022 | country | 41080 | 0.0888 | 0.0810 | +0.0078 | 0.0311 | 0.0725 | +0.2850 |
| 2003-2022 | region | 1300 | 0.5429 | 0.5903 | -0.0474 | 0.1113 | 0.2469 | -0.2101 |
| 2003-2022 | subregion | 5040 | 0.2989 | 0.3065 | -0.0076 | 0.0755 | 0.1879 | +0.0003 |
| 2003-2022 | world | 260 | 0.9205 | 0.9511 | -0.0306 | 0.0536 | 0.1282 | -0.3061 |
| 2023- | country | 6162 | 0.0878 | 0.0810 | +0.0069 | 0.0317 | 0.0739 | +0.1945 |
| 2023- | region | 195 | 0.5680 | 0.6266 | -0.0586 | 0.1171 | 0.2841 | -0.2462 |
| 2023- | subregion | 756 | 0.3144 | 0.3183 | -0.0039 | 0.0745 | 0.1821 | -0.0393 |
| 2023- | world | 39 | 0.9110 | 0.9218 | -0.0109 | 0.0421 | 0.1000 | -0.2808 |

