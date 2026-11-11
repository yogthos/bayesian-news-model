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
- The composition counts only the children whose own target is known (a child outside its coverage or not at risk is not in the parent's target either). Each table has the observed rate beside the mean p, and the share of rows below their composition; a calibration table sets every node's mean p against its observed rate.
- C1 for stocks has nothing to measure on a lattice book, which holds no stocks. It applies from step 6, when nodes carry bnm.buffers stocks.

Periods are by the year of the origin. The development period is 2003–2022.

## Headline (t50-world-norel, the base of record; 2003–2022)

Corrected 11 November 2026 after the review of step 1: the first version composed children whose own target was unknown, had no observed rates, and read the parents' shortfall as possibly dependence. The figures below are from the corrected report.

- **Parents say less than the independent composition of their children.** At the year nodes the mean gap is −0.086 at the subregion (median rate ratio e^−0.35 ≈ 0.70), −0.057 at the region and −0.031 at the world; 68% of parent-year rows are below their composition (subregion 68%, region 66%, world 80%). Before the correction the subregion gap was −0.104 and 71% of rows were below.
- **Most of it is the countries' p running above their outcomes, not dependence.** The calibration table shows it:
  - country year p is above the observed rate in banking crises (0.062 against 0.032), market crashes (0.339 against 0.178), food price shocks (0.190 against 0.157), violence surges (0.102 against 0.080), coups (0.055 against 0.045) and war onsets (0.033 against 0.025);
  - the parents track their own observed rates much more closely (banking subregion 0.163 against 0.108, while its composition is 0.320; coup subregion 0.279 against 0.278);
  - so a parent below its composition is, in these kinds, mostly the children's errors adding up. Shared frailty (dependence among the children) lowers P(any onset) too (compose_test C4), but it can only be read where the countries are calibrated.
- **Where the countries are calibrated, the gap that remains is the dependence signal.** Fiscal consolidation: country 0.268 against 0.272 observed, and the subregion still −0.084 below its composition. Growth collapses: the countries run below their outcomes (0.074 against 0.106) and the subregion is still −0.201 below its composition. These are the gaps step 3's frailty variance has to explain. The financial kinds' miscalibration is bead bnm-56c.15's.
- **The largest place gaps by kind** (subregion year): growth collapses −0.201, market crashes −0.166, banking crises −0.158, food price shocks −0.125 (region −0.238). Inflation crises, war onsets and currency crises at the region or world go the other way.
- **Time tree: the year runs above its quarters at the country.** The rate ratio median is +0.28 (the year's cumulative hazard is about 1.3 × the quarters'), but the mean p gap is only +0.007. At the subregion the two agree (−0.011; median log ratio −0.02). At the region and world the year is below its quarters (−0.047, −0.027).
  - By kind, the country-level excess is largest in protest surges, war onsets, coups and violence surges.
  - The quarters carry the year's forecast as their offset, pooled 0.5 with named's (the downscale), so this gap is what the quarters' own models take back from the year, not two independent views.
  - Food price shocks and market crashes have the largest absolute time gaps. These are the kinds whose quarters read world prices that the year node does not read: audit issue 3, step 2.
- **t48 against t50.** No pooled mean gap moves by more than about 0.02 between the two bases (subregion year −0.076 in t48 against −0.086 in t50). The incoherence belongs to the design, not to the input change.

What this means for the build plan:
- The year's λ built from its quarters' λ (§6.1, step 4) and the parent's rate built from its children's (§2.1) would each move the base's level by these amounts. They are therefore model changes to score, not bookkeeping.
- Before the parents' shortfall is read as frailty variance (step 3), the countries' calibration has to be accounted for; the frailty should be estimated against calibrated children, or with a calibration term beside it.


## Tables

### t50-world-norel

Place tree (C1 rates, C4 at v = 0), all kinds pooled: parent's p against 1 - Π(1 - p_c)

| period | scale | level | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1946-2002 | quarter-lead | region | 11349 | 0.3388 | 0.3403 | 0.4040 | -0.0653 | 0.6455 | 0.1202 | 0.2853 | -0.2379 |
| 1946-2002 | quarter-lead | subregion | 41164 | 0.1701 | 0.1535 | 0.2104 | -0.0403 | 0.5444 | 0.1065 | 0.2839 | -0.1032 |
| 1946-2002 | quarter-lead | world | 2286 | 0.6495 | 0.7003 | 0.7943 | -0.1448 | 0.8740 | 0.1802 | 0.3063 | -0.6226 |
| 1946-2002 | quarter-w2 | region | 11393 | 0.4042 | 0.4131 | 0.4710 | -0.0668 | 0.6522 | 0.1305 | 0.2973 | -0.2610 |
| 1946-2002 | quarter-w2 | subregion | 41343 | 0.2130 | 0.1864 | 0.2596 | -0.0466 | 0.5443 | 0.1305 | 0.3483 | -0.1084 |
| 1946-2002 | quarter-w2 | world | 2302 | 0.7404 | 0.8310 | 0.8494 | -0.1090 | 0.8449 | 0.1435 | 0.2947 | -0.5715 |
| 1946-2002 | year | region | 2855 | 0.5134 | 0.5671 | 0.5912 | -0.0778 | 0.7163 | 0.1333 | 0.2902 | -0.3525 |
| 1946-2002 | year | subregion | 10373 | 0.3057 | 0.3200 | 0.3774 | -0.0717 | 0.6346 | 0.1411 | 0.3389 | -0.2802 |
| 1946-2002 | year | world | 574 | 0.8653 | 0.9251 | 0.9316 | -0.0663 | 0.8519 | 0.0901 | 0.2428 | -0.5846 |
| 2003-2022 | quarter-lead | region | 5168 | 0.3587 | 0.3142 | 0.3749 | -0.0161 | 0.4907 | 0.0961 | 0.2256 | +0.0128 |
| 2003-2022 | quarter-lead | subregion | 19989 | 0.1469 | 0.1416 | 0.1702 | -0.0233 | 0.4688 | 0.0688 | 0.1783 | +0.0491 |
| 2003-2022 | quarter-lead | world | 1038 | 0.7539 | 0.7264 | 0.8322 | -0.0783 | 0.7900 | 0.1138 | 0.2297 | -0.3001 |
| 2003-2022 | quarter-w2 | region | 5146 | 0.4230 | 0.3776 | 0.4284 | -0.0054 | 0.4909 | 0.1072 | 0.2565 | +0.0106 |
| 2003-2022 | quarter-w2 | subregion | 19875 | 0.1788 | 0.1706 | 0.1984 | -0.0196 | 0.4643 | 0.0797 | 0.2055 | +0.0596 |
| 2003-2022 | quarter-w2 | world | 1036 | 0.8425 | 0.8407 | 0.8827 | -0.0402 | 0.7577 | 0.0926 | 0.2313 | -0.2764 |
| 2003-2022 | year | region | 1291 | 0.5431 | 0.5275 | 0.5996 | -0.0565 | 0.6576 | 0.1160 | 0.2644 | -0.2283 |
| 2003-2022 | year | subregion | 5001 | 0.2913 | 0.2915 | 0.3778 | -0.0864 | 0.6809 | 0.1392 | 0.3303 | -0.3513 |
| 2003-2022 | year | world | 259 | 0.9270 | 0.9459 | 0.9580 | -0.0309 | 0.8031 | 0.0665 | 0.1591 | -0.4726 |
| 2023- | quarter-lead | region | 490 | 0.3656 | 0.2735 | 0.3793 | -0.0137 | 0.5592 | 0.0853 | 0.2372 | -0.0328 |
| 2023- | quarter-lead | subregion | 1875 | 0.1456 | 0.1024 | 0.1553 | -0.0096 | 0.4128 | 0.0583 | 0.1587 | +0.1589 |
| 2023- | quarter-lead | world | 98 | 0.7551 | 0.6837 | 0.8577 | -0.1026 | 0.8571 | 0.1146 | 0.2302 | -0.3719 |
| 2023- | quarter-w2 | region | 250 | 0.4379 | 0.2880 | 0.4511 | -0.0132 | 0.6000 | 0.1030 | 0.2352 | -0.1379 |
| 2023- | quarter-w2 | subregion | 947 | 0.1836 | 0.1045 | 0.1849 | -0.0013 | 0.3928 | 0.0720 | 0.1818 | +0.1799 |
| 2023- | quarter-w2 | world | 50 | 0.8311 | 0.7200 | 0.9011 | -0.0700 | 0.8000 | 0.0767 | 0.2086 | -0.3234 |
| 2023- | year | region | 50 | 0.6022 | 0.4000 | 0.6519 | -0.0497 | 0.7400 | 0.1043 | 0.2509 | -0.2974 |
| 2023- | year | subregion | 192 | 0.3360 | 0.1823 | 0.3851 | -0.0491 | 0.5990 | 0.1162 | 0.2662 | -0.1490 |
| 2023- | year | world | 10 | 0.9214 | 0.7000 | 0.9784 | -0.0570 | 0.9000 | 0.0574 | 0.2270 | -0.4896 |

Time tree (C5): year p against its quarters' per-month rates, all kinds pooled

| period | level | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1946-2002 | country | 118047 | 0.1917 | 0.0845 | 0.2485 | -0.0567 | 0.5647 | 0.0858 | 0.2188 | -0.0862 |
| 1946-2002 | region | 3705 | 0.5083 | 0.5671 | 0.6053 | -0.0970 | 0.7800 | 0.1288 | 0.2308 | -0.3848 |
| 1946-2002 | subregion | 14364 | 0.3452 | 0.3200 | 0.4241 | -0.0789 | 0.6864 | 0.1187 | 0.2239 | -0.2984 |
| 1946-2002 | world | 741 | 0.7829 | 0.9251 | 0.8474 | -0.0644 | 0.8057 | 0.0889 | 0.2158 | -0.2974 |
| 2003-2022 | country | 41420 | 0.0891 | 0.0759 | 0.0820 | +0.0071 | 0.2642 | 0.0321 | 0.0762 | +0.2787 |
| 2003-2022 | region | 1300 | 0.5431 | 0.5275 | 0.5901 | -0.0471 | 0.6662 | 0.1118 | 0.2452 | -0.2104 |
| 2003-2022 | subregion | 5040 | 0.2910 | 0.2915 | 0.3016 | -0.0106 | 0.5202 | 0.0785 | 0.1883 | -0.0248 |
| 2003-2022 | world | 260 | 0.9268 | 0.9459 | 0.9535 | -0.0266 | 0.8000 | 0.0480 | 0.1133 | -0.2968 |
| 2023- | country | 6213 | 0.0832 | 0.0402 | 0.0737 | +0.0095 | 0.3745 | 0.0318 | 0.0754 | +0.2279 |
| 2023- | region | 195 | 0.5696 | 0.4000 | 0.6257 | -0.0561 | 0.7231 | 0.1172 | 0.2819 | -0.2172 |
| 2023- | subregion | 756 | 0.3101 | 0.1823 | 0.3188 | -0.0087 | 0.5476 | 0.0753 | 0.1715 | -0.0597 |
| 2023- | world | 39 | 0.9116 | 0.7000 | 0.9208 | -0.0092 | 0.7949 | 0.0380 | 0.0909 | -0.2816 |

Place tree, 2003-2022, year scale, by kind

| kind | level | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|---|
| banking_crisis | region | 100 | 0.3715 | 0.2700 | 0.4464 | -0.0749 | 0.6600 | 0.1205 | 0.2559 | -0.2481 |
| banking_crisis | subregion | 380 | 0.1627 | 0.1079 | 0.3203 | -0.1576 | 0.9211 | 0.1682 | 0.3361 | -0.8854 |
| banking_crisis | world | 20 | 0.8149 | 0.6500 | 0.9021 | -0.0872 | 0.9000 | 0.0912 | 0.1714 | -0.3591 |
| conflict_onset | region | 100 | 0.6195 | 0.5500 | 0.6212 | -0.0017 | 0.5500 | 0.0690 | 0.1518 | -0.0481 |
| conflict_onset | subregion | 400 | 0.3068 | 0.3575 | 0.4120 | -0.1052 | 0.7525 | 0.1282 | 0.2912 | -0.4298 |
| conflict_onset | world | 20 | 0.9726 | 1.0000 | 0.9991 | -0.0265 | 1.0000 | 0.0265 | 0.0349 | -0.6486 |
| coup | region | 100 | 0.5920 | 0.5900 | 0.6253 | -0.0334 | 0.7200 | 0.0707 | 0.1550 | -0.1830 |
| coup | subregion | 400 | 0.2790 | 0.2775 | 0.3265 | -0.0475 | 0.6325 | 0.0906 | 0.2218 | -0.1847 |
| coup | world | 20 | 0.9266 | 1.0000 | 0.9973 | -0.0706 | 1.0000 | 0.0706 | 0.1318 | -0.7576 |
| currency_crisis | region | 100 | 0.4441 | 0.5000 | 0.4834 | -0.0393 | 0.6700 | 0.0919 | 0.1890 | -0.1644 |
| currency_crisis | subregion | 400 | 0.1861 | 0.2250 | 0.2193 | -0.0332 | 0.5250 | 0.0899 | 0.2319 | -0.0350 |
| currency_crisis | world | 20 | 0.9710 | 0.9500 | 0.9151 | +0.0560 | 0.4500 | 0.0656 | 0.1610 | +0.0783 |
| fiscal_consolidation | region | 100 | 0.9169 | 0.9400 | 0.9471 | -0.0302 | 0.8800 | 0.0460 | 0.1085 | -0.5761 |
| fiscal_consolidation | subregion | 398 | 0.7197 | 0.7462 | 0.8040 | -0.0843 | 0.8367 | 0.1228 | 0.2462 | -0.4183 |
| fiscal_consolidation | world | 20 | 0.9680 | 1.0000 | 1.0000 | -0.0320 | 1.0000 | 0.0320 | 0.0351 | -1.4825 |
| food_price_shock | region | 100 | 0.5730 | 0.6700 | 0.8108 | -0.2377 | 0.9100 | 0.2485 | 0.4621 | -0.7690 |
| food_price_shock | subregion | 399 | 0.4292 | 0.4511 | 0.5541 | -0.1249 | 0.6291 | 0.2525 | 0.4934 | -0.4464 |
| food_price_shock | world | 20 | 0.8623 | 1.0000 | 0.9712 | -0.1089 | 0.7500 | 0.1386 | 0.4659 | -0.6637 |
| growth_collapse | region | 100 | 0.4544 | 0.5200 | 0.5985 | -0.1441 | 0.8900 | 0.1521 | 0.2704 | -0.4311 |
| growth_collapse | subregion | 398 | 0.2300 | 0.3166 | 0.4309 | -0.2009 | 0.8643 | 0.2179 | 0.4257 | -0.7977 |
| growth_collapse | world | 20 | 0.8277 | 0.8500 | 0.9516 | -0.1239 | 1.0000 | 0.1239 | 0.1838 | -0.5278 |
| inflation_crisis | region | 100 | 0.2932 | 0.3300 | 0.2495 | +0.0436 | 0.2400 | 0.0719 | 0.1534 | +0.2641 |
| inflation_crisis | subregion | 400 | 0.0739 | 0.0950 | 0.0939 | -0.0200 | 0.5075 | 0.0474 | 0.1205 | -0.0239 |
| inflation_crisis | world | 20 | 0.9532 | 0.9500 | 0.8160 | +0.1372 | 0.0500 | 0.1372 | 0.2573 | +0.6725 |
| market_crash | region | 96 | 0.5515 | 0.4063 | 0.6519 | -0.1004 | 0.6563 | 0.2038 | 0.3518 | -0.4259 |
| market_crash | subregion | 246 | 0.4138 | 0.2602 | 0.5798 | -0.1659 | 0.6992 | 0.2496 | 0.5131 | -0.4901 |
| market_crash | world | 20 | 0.8987 | 0.9000 | 0.9720 | -0.0733 | 0.9500 | 0.0845 | 0.1069 | -0.5927 |
| protest_surge | region | 100 | 0.4687 | 0.4500 | 0.5445 | -0.0758 | 0.7700 | 0.1097 | 0.2694 | -0.3247 |
| protest_surge | subregion | 400 | 0.2220 | 0.1850 | 0.2204 | +0.0016 | 0.4150 | 0.1045 | 0.2228 | +0.2371 |
| protest_surge | world | 20 | 0.9923 | 1.0000 | 0.9833 | +0.0091 | 0.3500 | 0.0108 | 0.0310 | +0.1259 |
| sovereign_default | region | 95 | 0.4638 | 0.4947 | 0.4936 | -0.0298 | 0.6211 | 0.0959 | 0.1691 | -0.0824 |
| sovereign_default | subregion | 380 | 0.1778 | 0.1895 | 0.2556 | -0.0779 | 0.7395 | 0.1032 | 0.2438 | -0.4619 |
| sovereign_default | world | 19 | 0.9120 | 1.0000 | 0.9582 | -0.0462 | 1.0000 | 0.0462 | 0.0538 | -0.3186 |
| violence_surge | region | 100 | 0.7550 | 0.7000 | 0.8388 | -0.0838 | 0.7500 | 0.1271 | 0.3122 | -0.3672 |
| violence_surge | subregion | 400 | 0.4526 | 0.3900 | 0.5405 | -0.0879 | 0.7050 | 0.1801 | 0.3720 | -0.3054 |
| violence_surge | world | 20 | 0.9915 | 1.0000 | 0.9993 | -0.0078 | 1.0000 | 0.0078 | 0.0107 | -0.5828 |
| war_onset | region | 100 | 0.5528 | 0.4300 | 0.4799 | +0.0729 | 0.2300 | 0.1029 | 0.1888 | +0.3012 |
| war_onset | subregion | 400 | 0.1707 | 0.1650 | 0.2251 | -0.0544 | 0.6475 | 0.0978 | 0.2467 | -0.2584 |
| war_onset | world | 20 | 0.9599 | 1.0000 | 0.9883 | -0.0284 | 1.0000 | 0.0284 | 0.0379 | -0.3791 |

Time tree, 2003-2022, by kind (every level)

| kind | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|
| banking_crisis | 3100 | 0.0892 | 0.0533 | 0.0841 | +0.0051 | 0.2842 | 0.0350 | 0.0802 | +0.2548 |
| conflict_onset | 4020 | 0.1264 | 0.1277 | 0.1244 | +0.0020 | 0.2833 | 0.0328 | 0.0860 | +0.2704 |
| coup | 4100 | 0.0966 | 0.0867 | 0.0760 | +0.0206 | 0.1824 | 0.0319 | 0.0804 | +0.4118 |
| currency_crisis | 3920 | 0.0659 | 0.0730 | 0.0656 | +0.0003 | 0.3492 | 0.0172 | 0.0419 | +0.1588 |
| fiscal_consolidation | 3940 | 0.3335 | 0.3501 | 0.3445 | -0.0110 | 0.5266 | 0.0387 | 0.0893 | -0.0113 |
| food_price_shock | 3820 | 0.2299 | 0.2101 | 0.2507 | -0.0208 | 0.4196 | 0.1114 | 0.2415 | +0.2461 |
| growth_collapse | 3940 | 0.1032 | 0.1434 | 0.1035 | -0.0004 | 0.4244 | 0.0269 | 0.0612 | +0.0540 |
| inflation_crisis | 3840 | 0.0319 | 0.0347 | 0.0304 | +0.0015 | 0.2232 | 0.0084 | 0.0194 | +0.3007 |
| market_crash | 1320 | 0.3620 | 0.2267 | 0.3403 | +0.0217 | 0.4038 | 0.1812 | 0.3870 | +0.2157 |
| protest_surge | 4000 | 0.0710 | 0.0618 | 0.0571 | +0.0139 | 0.1765 | 0.0328 | 0.0909 | +0.6838 |
| sovereign_default | 3960 | 0.0677 | 0.0644 | 0.0700 | -0.0023 | 0.3793 | 0.0186 | 0.0436 | +0.1234 |
| violence_surge | 4080 | 0.1605 | 0.1323 | 0.1440 | +0.0165 | 0.2350 | 0.0481 | 0.0977 | +0.3837 |
| war_onset | 3980 | 0.0651 | 0.0546 | 0.0556 | +0.0095 | 0.1500 | 0.0216 | 0.0531 | +0.4196 |

Calibration, 2003-2022, year scale: every node's mean p against its observed rate

| kind | level | n | mean p | mean observed | p less observed |
|---|---|---|---|---|---|
| banking_crisis | country | 2558 | 0.0620 | 0.0321 | +0.0300 |
| banking_crisis | region | 100 | 0.3715 | 0.2700 | +0.1015 |
| banking_crisis | subregion | 380 | 0.1627 | 0.1079 | +0.0548 |
| banking_crisis | world | 20 | 0.8149 | 0.6500 | +0.1649 |
| conflict_onset | country | 3308 | 0.0796 | 0.0819 | -0.0023 |
| conflict_onset | region | 100 | 0.6195 | 0.5500 | +0.0695 |
| conflict_onset | subregion | 400 | 0.3068 | 0.3575 | -0.0507 |
| conflict_onset | world | 20 | 0.9726 | 1.0000 | -0.0274 |
| coup | country | 3481 | 0.0553 | 0.0451 | +0.0102 |
| coup | region | 100 | 0.5920 | 0.5900 | +0.0020 |
| coup | subregion | 400 | 0.2790 | 0.2775 | +0.0015 |
| coup | world | 20 | 0.9266 | 1.0000 | -0.0734 |
| currency_crisis | country | 3262 | 0.0345 | 0.0359 | -0.0014 |
| currency_crisis | region | 100 | 0.4441 | 0.5000 | -0.0559 |
| currency_crisis | subregion | 400 | 0.1861 | 0.2250 | -0.0389 |
| currency_crisis | world | 20 | 0.9710 | 0.9500 | +0.0210 |
| fiscal_consolidation | country | 2944 | 0.2677 | 0.2721 | -0.0044 |
| fiscal_consolidation | region | 100 | 0.9169 | 0.9400 | -0.0231 |
| fiscal_consolidation | subregion | 398 | 0.7197 | 0.7462 | -0.0265 |
| fiscal_consolidation | world | 20 | 0.9680 | 1.0000 | -0.0320 |
| food_price_shock | country | 2950 | 0.1902 | 0.1566 | +0.0336 |
| food_price_shock | region | 100 | 0.5730 | 0.6700 | -0.0970 |
| food_price_shock | subregion | 399 | 0.4292 | 0.4511 | -0.0219 |
| food_price_shock | world | 20 | 0.8623 | 1.0000 | -0.1377 |
| growth_collapse | country | 3205 | 0.0744 | 0.1058 | -0.0313 |
| growth_collapse | region | 100 | 0.4544 | 0.5200 | -0.0656 |
| growth_collapse | subregion | 398 | 0.2300 | 0.3166 | -0.0866 |
| growth_collapse | world | 20 | 0.8277 | 0.8500 | -0.0223 |
| inflation_crisis | country | 3197 | 0.0129 | 0.0122 | +0.0007 |
| inflation_crisis | region | 100 | 0.2932 | 0.3300 | -0.0368 |
| inflation_crisis | subregion | 400 | 0.0739 | 0.0950 | -0.0211 |
| inflation_crisis | world | 20 | 0.9532 | 0.9500 | +0.0032 |
| market_crash | country | 798 | 0.3393 | 0.1779 | +0.1614 |
| market_crash | region | 96 | 0.5515 | 0.4063 | +0.1452 |
| market_crash | subregion | 246 | 0.4138 | 0.2602 | +0.1537 |
| market_crash | world | 20 | 0.8987 | 0.9000 | -0.0013 |
| protest_surge | country | 3411 | 0.0342 | 0.0305 | +0.0037 |
| protest_surge | region | 100 | 0.4687 | 0.4500 | +0.0187 |
| protest_surge | subregion | 400 | 0.2220 | 0.1850 | +0.0370 |
| protest_surge | world | 20 | 0.9923 | 1.0000 | -0.0077 |
| sovereign_default | country | 3217 | 0.0379 | 0.0314 | +0.0065 |
| sovereign_default | region | 95 | 0.4638 | 0.4947 | -0.0309 |
| sovereign_default | subregion | 380 | 0.1778 | 0.1895 | -0.0117 |
| sovereign_default | world | 19 | 0.9120 | 1.0000 | -0.0880 |
| violence_surge | country | 3387 | 0.1024 | 0.0800 | +0.0224 |
| violence_surge | region | 100 | 0.7550 | 0.7000 | +0.0550 |
| violence_surge | subregion | 400 | 0.4526 | 0.3900 | +0.0626 |
| violence_surge | world | 20 | 0.9915 | 1.0000 | -0.0085 |
| war_onset | country | 3402 | 0.0327 | 0.0250 | +0.0077 |
| war_onset | region | 100 | 0.5528 | 0.4300 | +0.1228 |
| war_onset | subregion | 400 | 0.1707 | 0.1650 | +0.0057 |
| war_onset | world | 20 | 0.9599 | 1.0000 | -0.0401 |

### t48-world-norel

Place tree (C1 rates, C4 at v = 0), all kinds pooled: parent's p against 1 - Π(1 - p_c)

| period | scale | level | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1946-2002 | quarter-lead | region | 11349 | 0.3394 | 0.3404 | 0.4037 | -0.0643 | 0.6555 | 0.1198 | 0.2818 | -0.2437 |
| 1946-2002 | quarter-lead | subregion | 41164 | 0.1702 | 0.1536 | 0.2101 | -0.0398 | 0.5396 | 0.1040 | 0.2761 | -0.0874 |
| 1946-2002 | quarter-lead | world | 2286 | 0.6469 | 0.6995 | 0.7932 | -0.1462 | 0.8766 | 0.1814 | 0.3108 | -0.6392 |
| 1946-2002 | quarter-w2 | region | 11393 | 0.4036 | 0.4131 | 0.4686 | -0.0650 | 0.6543 | 0.1286 | 0.2924 | -0.2547 |
| 1946-2002 | quarter-w2 | subregion | 41343 | 0.2119 | 0.1864 | 0.2588 | -0.0469 | 0.5450 | 0.1275 | 0.3442 | -0.1034 |
| 1946-2002 | quarter-w2 | world | 2302 | 0.7376 | 0.8301 | 0.8478 | -0.1102 | 0.8566 | 0.1442 | 0.3041 | -0.5780 |
| 1946-2002 | year | region | 2855 | 0.5163 | 0.5678 | 0.5945 | -0.0782 | 0.7275 | 0.1332 | 0.2929 | -0.3528 |
| 1946-2002 | year | subregion | 10373 | 0.3086 | 0.3204 | 0.3791 | -0.0705 | 0.6371 | 0.1394 | 0.3356 | -0.2697 |
| 1946-2002 | year | world | 574 | 0.8636 | 0.9233 | 0.9313 | -0.0677 | 0.8589 | 0.0922 | 0.2433 | -0.6018 |
| 2003-2022 | quarter-lead | region | 5168 | 0.3593 | 0.3171 | 0.3805 | -0.0211 | 0.5045 | 0.1009 | 0.2446 | -0.0049 |
| 2003-2022 | quarter-lead | subregion | 19989 | 0.1486 | 0.1417 | 0.1675 | -0.0189 | 0.4634 | 0.0660 | 0.1699 | +0.0565 |
| 2003-2022 | quarter-lead | world | 1038 | 0.7510 | 0.7264 | 0.8310 | -0.0800 | 0.7987 | 0.1134 | 0.2316 | -0.2909 |
| 2003-2022 | quarter-w2 | region | 5146 | 0.4211 | 0.3805 | 0.4356 | -0.0146 | 0.4961 | 0.1102 | 0.2623 | +0.0064 |
| 2003-2022 | quarter-w2 | subregion | 19875 | 0.1817 | 0.1710 | 0.1950 | -0.0133 | 0.4534 | 0.0766 | 0.1920 | +0.0730 |
| 2003-2022 | quarter-w2 | world | 1036 | 0.8368 | 0.8388 | 0.8784 | -0.0416 | 0.7683 | 0.0920 | 0.2259 | -0.2572 |
| 2003-2022 | year | region | 1291 | 0.5430 | 0.5306 | 0.6124 | -0.0695 | 0.6685 | 0.1285 | 0.2925 | -0.2774 |
| 2003-2022 | year | subregion | 5001 | 0.2994 | 0.2929 | 0.3755 | -0.0761 | 0.6825 | 0.1333 | 0.3072 | -0.3334 |
| 2003-2022 | year | world | 259 | 0.9208 | 0.9421 | 0.9550 | -0.0342 | 0.7992 | 0.0699 | 0.1756 | -0.4806 |
| 2023- | quarter-lead | region | 490 | 0.3667 | 0.2755 | 0.3772 | -0.0105 | 0.5755 | 0.0774 | 0.1700 | -0.0577 |
| 2023- | quarter-lead | subregion | 1875 | 0.1456 | 0.1024 | 0.1716 | -0.0259 | 0.4464 | 0.0706 | 0.2031 | +0.1095 |
| 2023- | quarter-lead | world | 98 | 0.7547 | 0.6837 | 0.8576 | -0.1029 | 0.8367 | 0.1158 | 0.2297 | -0.3438 |
| 2023- | quarter-w2 | region | 250 | 0.4341 | 0.2920 | 0.4494 | -0.0153 | 0.5840 | 0.1077 | 0.2328 | -0.1511 |
| 2023- | quarter-w2 | subregion | 947 | 0.1834 | 0.1045 | 0.2070 | -0.0236 | 0.4192 | 0.0863 | 0.2373 | +0.1418 |
| 2023- | quarter-w2 | world | 50 | 0.8321 | 0.7200 | 0.8964 | -0.0644 | 0.7800 | 0.0734 | 0.1846 | -0.3060 |
| 2023- | year | region | 50 | 0.6016 | 0.4200 | 0.6516 | -0.0500 | 0.7600 | 0.0962 | 0.2168 | -0.3134 |
| 2023- | year | subregion | 192 | 0.3416 | 0.1875 | 0.4016 | -0.0600 | 0.6510 | 0.1239 | 0.2634 | -0.2418 |
| 2023- | year | world | 10 | 0.9202 | 0.7000 | 0.9757 | -0.0555 | 0.9000 | 0.0567 | 0.1791 | -0.5155 |

Time tree (C5): year p against its quarters' per-month rates, all kinds pooled

| period | level | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1946-2002 | country | 117078 | 0.1933 | 0.0848 | 0.2496 | -0.0563 | 0.5526 | 0.0858 | 0.2186 | -0.0748 |
| 1946-2002 | region | 3705 | 0.5106 | 0.5678 | 0.6050 | -0.0944 | 0.7827 | 0.1276 | 0.2308 | -0.3707 |
| 1946-2002 | subregion | 14364 | 0.3474 | 0.3204 | 0.4240 | -0.0766 | 0.6853 | 0.1171 | 0.2234 | -0.2788 |
| 1946-2002 | world | 741 | 0.7816 | 0.9233 | 0.8452 | -0.0636 | 0.8043 | 0.0902 | 0.2151 | -0.3017 |
| 2003-2022 | country | 41080 | 0.0888 | 0.0761 | 0.0810 | +0.0078 | 0.2500 | 0.0311 | 0.0725 | +0.2850 |
| 2003-2022 | region | 1300 | 0.5429 | 0.5306 | 0.5903 | -0.0474 | 0.6762 | 0.1113 | 0.2469 | -0.2101 |
| 2003-2022 | subregion | 5040 | 0.2989 | 0.2929 | 0.3065 | -0.0076 | 0.4996 | 0.0755 | 0.1879 | +0.0003 |
| 2003-2022 | world | 260 | 0.9205 | 0.9421 | 0.9511 | -0.0306 | 0.8038 | 0.0536 | 0.1282 | -0.3061 |
| 2023- | country | 6162 | 0.0878 | 0.0406 | 0.0810 | +0.0069 | 0.3773 | 0.0317 | 0.0739 | +0.1945 |
| 2023- | region | 195 | 0.5680 | 0.4200 | 0.6266 | -0.0586 | 0.7231 | 0.1171 | 0.2841 | -0.2462 |
| 2023- | subregion | 756 | 0.3144 | 0.1875 | 0.3183 | -0.0039 | 0.5384 | 0.0745 | 0.1821 | -0.0393 |
| 2023- | world | 39 | 0.9110 | 0.7000 | 0.9218 | -0.0109 | 0.7692 | 0.0421 | 0.1000 | -0.2808 |

Place tree, 2003-2022, year scale, by kind

| kind | level | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|---|
| banking_crisis | region | 100 | 0.3332 | 0.2700 | 0.4518 | -0.1186 | 0.7300 | 0.1516 | 0.3146 | -0.4491 |
| banking_crisis | subregion | 380 | 0.1638 | 0.1079 | 0.3140 | -0.1502 | 0.9289 | 0.1572 | 0.3098 | -0.8015 |
| banking_crisis | world | 20 | 0.7970 | 0.6500 | 0.8624 | -0.0654 | 0.6500 | 0.0975 | 0.1815 | -0.2694 |
| conflict_onset | region | 100 | 0.6137 | 0.5600 | 0.6393 | -0.0256 | 0.6200 | 0.0724 | 0.1631 | -0.1682 |
| conflict_onset | subregion | 400 | 0.3169 | 0.3675 | 0.4260 | -0.1092 | 0.7875 | 0.1286 | 0.2723 | -0.4409 |
| conflict_onset | world | 20 | 0.9712 | 1.0000 | 0.9989 | -0.0277 | 1.0000 | 0.0277 | 0.0405 | -0.6766 |
| coup | region | 100 | 0.6248 | 0.6100 | 0.6504 | -0.0256 | 0.6400 | 0.0800 | 0.1992 | -0.1238 |
| coup | subregion | 400 | 0.2849 | 0.2875 | 0.3367 | -0.0518 | 0.6350 | 0.0985 | 0.2323 | -0.1975 |
| coup | world | 20 | 0.9280 | 1.0000 | 0.9976 | -0.0696 | 1.0000 | 0.0696 | 0.1266 | -0.7975 |
| currency_crisis | region | 100 | 0.4531 | 0.5000 | 0.4769 | -0.0238 | 0.6500 | 0.0858 | 0.1879 | -0.1184 |
| currency_crisis | subregion | 400 | 0.1835 | 0.2250 | 0.2173 | -0.0338 | 0.5475 | 0.0917 | 0.2447 | -0.0871 |
| currency_crisis | world | 20 | 0.9711 | 0.9500 | 0.9249 | +0.0462 | 0.4500 | 0.0561 | 0.1487 | +0.0931 |
| fiscal_consolidation | region | 100 | 0.9230 | 0.9400 | 0.9504 | -0.0274 | 0.8700 | 0.0465 | 0.1199 | -0.5615 |
| fiscal_consolidation | subregion | 398 | 0.7203 | 0.7412 | 0.8096 | -0.0893 | 0.8518 | 0.1236 | 0.2355 | -0.4497 |
| fiscal_consolidation | world | 20 | 0.9679 | 1.0000 | 1.0000 | -0.0321 | 1.0000 | 0.0321 | 0.0352 | -1.5109 |
| food_price_shock | region | 100 | 0.5787 | 0.6600 | 0.8285 | -0.2498 | 0.9400 | 0.2554 | 0.4620 | -0.8663 |
| food_price_shock | subregion | 399 | 0.4426 | 0.4486 | 0.5208 | -0.0782 | 0.5489 | 0.2499 | 0.4888 | -0.1994 |
| food_price_shock | world | 20 | 0.8123 | 0.9500 | 0.9792 | -0.1669 | 1.0000 | 0.1669 | 0.4659 | -0.8050 |
| growth_collapse | region | 100 | 0.4464 | 0.5200 | 0.6980 | -0.2517 | 0.9200 | 0.2605 | 0.4674 | -0.7733 |
| growth_collapse | subregion | 398 | 0.2995 | 0.3166 | 0.4193 | -0.1198 | 0.8166 | 0.1461 | 0.2925 | -0.4312 |
| growth_collapse | world | 20 | 0.8119 | 0.8500 | 0.9450 | -0.1331 | 1.0000 | 0.1331 | 0.2668 | -0.6473 |
| inflation_crisis | region | 100 | 0.2844 | 0.3300 | 0.2491 | +0.0353 | 0.3100 | 0.0691 | 0.1539 | +0.2301 |
| inflation_crisis | subregion | 400 | 0.0735 | 0.0950 | 0.0920 | -0.0186 | 0.5100 | 0.0460 | 0.1171 | -0.0085 |
| inflation_crisis | world | 20 | 0.9541 | 0.9500 | 0.8049 | +0.1491 | 0.0000 | 0.1491 | 0.2751 | +0.7084 |
| market_crash | region | 96 | 0.5493 | 0.4063 | 0.6519 | -0.1026 | 0.6667 | 0.2024 | 0.3492 | -0.3467 |
| market_crash | subregion | 246 | 0.4133 | 0.2602 | 0.5776 | -0.1644 | 0.6951 | 0.2480 | 0.5048 | -0.4824 |
| market_crash | world | 20 | 0.8986 | 0.9000 | 0.9714 | -0.0728 | 0.9500 | 0.0841 | 0.1065 | -0.5926 |
| protest_surge | region | 100 | 0.4689 | 0.4500 | 0.5473 | -0.0784 | 0.8000 | 0.1100 | 0.2664 | -0.3215 |
| protest_surge | subregion | 400 | 0.2241 | 0.1850 | 0.2194 | +0.0047 | 0.4175 | 0.1044 | 0.2196 | +0.2409 |
| protest_surge | world | 20 | 0.9924 | 1.0000 | 0.9841 | +0.0083 | 0.3500 | 0.0104 | 0.0280 | +0.1392 |
| sovereign_default | region | 95 | 0.4633 | 0.4947 | 0.4841 | -0.0209 | 0.5263 | 0.1062 | 0.1881 | -0.0560 |
| sovereign_default | subregion | 380 | 0.1749 | 0.1895 | 0.2491 | -0.0742 | 0.7684 | 0.1035 | 0.2291 | -0.4916 |
| sovereign_default | world | 19 | 0.9134 | 1.0000 | 0.9577 | -0.0443 | 1.0000 | 0.0443 | 0.0533 | -0.2938 |
| violence_surge | region | 100 | 0.7544 | 0.7000 | 0.8382 | -0.0838 | 0.7600 | 0.1269 | 0.3058 | -0.3586 |
| violence_surge | subregion | 400 | 0.4529 | 0.3900 | 0.5409 | -0.0880 | 0.7050 | 0.1801 | 0.3809 | -0.2998 |
| violence_surge | world | 20 | 0.9915 | 1.0000 | 0.9993 | -0.0078 | 1.0000 | 0.0078 | 0.0106 | -0.5832 |
| war_onset | region | 100 | 0.5616 | 0.4500 | 0.4907 | +0.0710 | 0.2500 | 0.1051 | 0.1928 | +0.2993 |
| war_onset | subregion | 400 | 0.1748 | 0.1700 | 0.2299 | -0.0551 | 0.6825 | 0.0997 | 0.2361 | -0.2899 |
| war_onset | world | 20 | 0.9603 | 1.0000 | 0.9892 | -0.0289 | 1.0000 | 0.0289 | 0.0389 | -0.4023 |

Time tree, 2003-2022, by kind (every level)

| kind | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |
|---|---|---|---|---|---|---|---|---|---|
| banking_crisis | 3080 | 0.0865 | 0.0537 | 0.0811 | +0.0054 | 0.2721 | 0.0343 | 0.0780 | +0.2713 |
| conflict_onset | 4020 | 0.1290 | 0.1294 | 0.1244 | +0.0046 | 0.2667 | 0.0333 | 0.0847 | +0.2864 |
| coup | 4080 | 0.0993 | 0.0887 | 0.0783 | +0.0210 | 0.1640 | 0.0327 | 0.0812 | +0.4336 |
| currency_crisis | 3880 | 0.0661 | 0.0738 | 0.0650 | +0.0010 | 0.3492 | 0.0170 | 0.0413 | +0.1580 |
| fiscal_consolidation | 3900 | 0.3400 | 0.3508 | 0.3443 | -0.0043 | 0.4895 | 0.0319 | 0.0759 | +0.0025 |
| food_price_shock | 3780 | 0.2241 | 0.2091 | 0.2471 | -0.0229 | 0.4265 | 0.1080 | 0.2412 | +0.2436 |
| growth_collapse | 3900 | 0.1099 | 0.1432 | 0.1076 | +0.0024 | 0.3572 | 0.0230 | 0.0504 | +0.0798 |
| inflation_crisis | 3800 | 0.0317 | 0.0351 | 0.0300 | +0.0017 | 0.2195 | 0.0083 | 0.0191 | +0.3015 |
| market_crash | 1320 | 0.3567 | 0.2267 | 0.3363 | +0.0204 | 0.4114 | 0.1803 | 0.3972 | +0.2145 |
| protest_surge | 3960 | 0.0716 | 0.0625 | 0.0573 | +0.0143 | 0.1654 | 0.0332 | 0.0926 | +0.7452 |
| sovereign_default | 3920 | 0.0660 | 0.0651 | 0.0707 | -0.0046 | 0.3839 | 0.0178 | 0.0380 | +0.1218 |
| violence_surge | 4080 | 0.1606 | 0.1323 | 0.1440 | +0.0166 | 0.2348 | 0.0486 | 0.0981 | +0.3872 |
| war_onset | 3960 | 0.0664 | 0.0564 | 0.0558 | +0.0106 | 0.1348 | 0.0221 | 0.0530 | +0.4628 |

Calibration, 2003-2022, year scale: every node's mean p against its observed rate

| kind | level | n | mean p | mean observed | p less observed |
|---|---|---|---|---|---|
| banking_crisis | country | 2538 | 0.0601 | 0.0323 | +0.0278 |
| banking_crisis | region | 100 | 0.3332 | 0.2700 | +0.0632 |
| banking_crisis | subregion | 380 | 0.1638 | 0.1079 | +0.0559 |
| banking_crisis | world | 20 | 0.7970 | 0.6500 | +0.1470 |
| conflict_onset | country | 3306 | 0.0814 | 0.0823 | -0.0008 |
| conflict_onset | region | 100 | 0.6137 | 0.5600 | +0.0537 |
| conflict_onset | subregion | 400 | 0.3169 | 0.3675 | -0.0506 |
| conflict_onset | world | 20 | 0.9712 | 1.0000 | -0.0288 |
| coup | country | 3461 | 0.0567 | 0.0454 | +0.0113 |
| coup | region | 100 | 0.6248 | 0.6100 | +0.0148 |
| coup | subregion | 400 | 0.2849 | 0.2875 | -0.0026 |
| coup | world | 20 | 0.9280 | 1.0000 | -0.0720 |
| currency_crisis | country | 3222 | 0.0343 | 0.0363 | -0.0020 |
| currency_crisis | region | 100 | 0.4531 | 0.5000 | -0.0469 |
| currency_crisis | subregion | 400 | 0.1835 | 0.2250 | -0.0415 |
| currency_crisis | world | 20 | 0.9711 | 0.9500 | +0.0211 |
| fiscal_consolidation | country | 2908 | 0.2738 | 0.2727 | +0.0012 |
| fiscal_consolidation | region | 100 | 0.9230 | 0.9400 | -0.0170 |
| fiscal_consolidation | subregion | 398 | 0.7203 | 0.7412 | -0.0209 |
| fiscal_consolidation | world | 20 | 0.9679 | 1.0000 | -0.0321 |
| food_price_shock | country | 2914 | 0.1814 | 0.1558 | +0.0256 |
| food_price_shock | region | 100 | 0.5787 | 0.6600 | -0.0813 |
| food_price_shock | subregion | 399 | 0.4426 | 0.4486 | -0.0060 |
| food_price_shock | world | 20 | 0.8123 | 0.9500 | -0.1377 |
| growth_collapse | country | 3168 | 0.0732 | 0.1051 | -0.0319 |
| growth_collapse | region | 100 | 0.4464 | 0.5200 | -0.0736 |
| growth_collapse | subregion | 398 | 0.2995 | 0.3166 | -0.0171 |
| growth_collapse | world | 20 | 0.8119 | 0.8500 | -0.0381 |
| inflation_crisis | country | 3157 | 0.0128 | 0.0124 | +0.0004 |
| inflation_crisis | region | 100 | 0.2844 | 0.3300 | -0.0456 |
| inflation_crisis | subregion | 400 | 0.0735 | 0.0950 | -0.0215 |
| inflation_crisis | world | 20 | 0.9541 | 0.9500 | +0.0041 |
| market_crash | country | 798 | 0.3321 | 0.1779 | +0.1542 |
| market_crash | region | 96 | 0.5493 | 0.4063 | +0.1430 |
| market_crash | subregion | 246 | 0.4133 | 0.2602 | +0.1531 |
| market_crash | world | 20 | 0.8986 | 0.9000 | -0.0014 |
| protest_surge | country | 3371 | 0.0343 | 0.0309 | +0.0034 |
| protest_surge | region | 100 | 0.4689 | 0.4500 | +0.0189 |
| protest_surge | subregion | 400 | 0.2241 | 0.1850 | +0.0391 |
| protest_surge | world | 20 | 0.9924 | 1.0000 | -0.0076 |
| sovereign_default | country | 3179 | 0.0365 | 0.0318 | +0.0048 |
| sovereign_default | region | 95 | 0.4633 | 0.4947 | -0.0315 |
| sovereign_default | subregion | 380 | 0.1749 | 0.1895 | -0.0146 |
| sovereign_default | world | 19 | 0.9134 | 1.0000 | -0.0866 |
| violence_surge | country | 3387 | 0.1025 | 0.0800 | +0.0225 |
| violence_surge | region | 100 | 0.7544 | 0.7000 | +0.0544 |
| violence_surge | subregion | 400 | 0.4529 | 0.3900 | +0.0629 |
| violence_surge | world | 20 | 0.9915 | 1.0000 | -0.0085 |
| war_onset | country | 3381 | 0.0334 | 0.0257 | +0.0076 |
| war_onset | region | 100 | 0.5616 | 0.4500 | +0.1116 |
| war_onset | subregion | 400 | 0.1748 | 0.1700 | +0.0048 |
| war_onset | world | 20 | 0.9603 | 1.0000 | -0.0397 |
