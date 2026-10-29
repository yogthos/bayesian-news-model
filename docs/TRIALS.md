# Trials ledger

Every configuration scored on the decision eras (2003-14, 2015-22) while
choosing the onset record, for the Bonferroni count of `bnm.anticipation
--tests` and a look-elsewhere correction (Gross & Vitells 2010). 2023 on
is locked (docs/PHASE-CHANGES.md). Add a line before scoring anything new;
the count is the number of lines below.

Corrected base, 28 October 2026 (bnm-as1, not a trial and not counted):
the trials from 42 to FSc read WDI missing values published as 0
(Venezuela's trade and sector shares 1991-2011, Brazil's sector shares
1960-80, and others; docs/PHASE-CHANGES.md). The base rebuilt without
them, t47-world-norel, matches t44-world-norel to about 1e-5 on the
pooled scores and exactly on the watch list (hard top-10 recall 0.274),
so no verdict below is rerun. Trials from now on bench against
t47-world-norel.

Corrected base, 29 October 2026 (bnm-wns, not a trial and not counted):
the lattice fed back errors not yet known as 0 and kept weight on groups
with no input anywhere (Romer & Romer's distress after 2013, food price
feedback before 2008). The base rebuilt with both fixed, t48-world-norel,
moves against t47-world-norel by Brier +0.002 [+0.000 +0.004], log
+0.004 [-0.001 +0.009] pooled (food price shocks and market crashes most)
and hard top-10 recall 0.271 against 0.274. That is the size of the
registered intervals, so the trials from 42 to FSc are listed in
docs/PHASE-CHANGES.md and await a decision on rerunning.

| # | configuration | first scored | note |
|---|---|---|---|
| 1 | mix | 2026-10 | event-code mix |
| 2 | ties | 2026-10 | partner surges, cross-pressure |
| 3 | ties-un | 2026-10 | UN votes in place of GDELT ties |
| 4 | structure | 2026-10 | sectors x world prices |
| 5 | institutions | 2026-10 | rents x weak state |
| 6 | youth | 2026-10 | educated youth out of work |
| 7 | nonlinear | 2026-10 | income and inequality curves |
| 8 | regime | 2026-10 | V-Dem as curves |
| 9 | fx | 2026-10 | exchange-rate regime |
| 10 | tot | 2026-10 | commodity terms of trade |
| 11 | calm | 2026-10 | calm and breaks in event streams |
| 12 | volcalm | 2026-10 | calm in markets |
| 13 | histories | 2026-10 | every kind's onset history |
| 14 | neighbours | 2026-10 | onsets in subregion and region |
| 15 | leaders | 2026-10 | military executive, election cycle, regime change |
| 16 | waves | 2026-10 | multi-rate onset kernels |
| 17 | sync | 2026-10 | network biomarker, world one-factor share |
| 18 | departure | 2026-10 | departure from own channel structure |
| 19 | gbt (trees) | 2026-10 | boosted trees on named, +mix, +regime, +structure |
| 20 | at (flags) | 2026-10 | at-risk flags on named, +mix |
| 21 | pool | 2026-10 | linear pool, 200 EM steps |
| 22 | mlpool | 2026-10 | linear pool at its maximum |
| 23 | stack | 2026-10 | log-linear stack |
| 24 | hedge | 2026-10 | exponential weights on record |
| 25 | median | 2026-10 | untrained median of members |
| 26 | frail | 2026-10 | regional frailty on hedge (not adopted) |
| 27 | knn (analogs) | 2026-10 | onset share among the 100 most similar resolved rows |
| 28 | breaks | 2026-10 | online changepoints in each event channel |
| 29 | hs (shrinkage) | 2026-10 | horseshoe-like reweighted ridge |
| 30 | mt (multi-task) | 2026-10 | other political kinds' rows, weighted, shared coefficients |
| 31 | hier (nested intercepts) | 2026-10 | region, subregion and country intercepts, variances by empirical Bayes |
| 32 | dyn (multilevel states) | 2026-10 | AR(1) states over world, region, subregion, country, from onsets known by then; also scored for subregions and regions |
| 33 | hier, amended (r8) | 2026-10 | trial 31 with within-between (Mundlak) inputs, the ridge estimated with the variances, half-normal priors on level sds |
| 34 | precursors | 2026-10 | event streams against their own past: displacement at three scales, trigger, burstiness, co-movement with neighbours |
| 35 | cs (calm slopes) | 2026-10 | every input also times a no-onset-in-ten-years flag |
| 36 | lattice (banking) | 2026-10 | nodes per place and time scale, dynamic model averaging, flows up and lagged flows down, learning from 1870 |
| 37 | lattice as a dialectic (banking) | 2026-10 | trial 36 with contradictions between and within levels, resolution, exposure, errors across levels, coupled learning |
| 38 | lattice rebuilt from the anatomy (banking) | 2026-10 | known forecast as offset, directional strain, the turn, strain carried down, unused and downloaded dimensions |
| 39 | lattice, flows standardized, design chosen on 1980-2002 (banking) | 2026-10 | contradictions summarized with theory's sign, data-flow fixes, full lattice at the year and half-decade, quarters on their gauges |
| 40 | lattice for the political kinds | 2026-10 | trial 39's design carried over to conflict, war, coup, protest and violence onsets; long catalogues from 1816/1875, V-Dem/Maddison/COW year gauges, tensions from their anatomy, no resolution |
| 41 | the year carried down to the quarter | 2026-10 | the year's forecast as the quarters' offset (constant hazard), pooled with named, its uncertainty carried by quadrature, windows reconciled with the year; design chosen on 1980-2002 over all six kinds |
| 42 | the world model | 2026-10 | thirteen kinds stepped together, each reading its parents' states and onsets (the sequencing graph, exciting and inhibiting) and the coupling; the learned world run forward as an artificial system |
| 43 | links nested, one per edge | 2026-10 | three relation bugs fixed; each kept edge passes one input (post-crisis regime, excursion or destabilization, never the level), chosen on the design years against a placebo; each link nested in the node's own model |
| 44 | own parts and early warnings | 2026-10 | links pass a form's own part (common factors on the place tree removed), 7 edges after the amendment; early warnings for 5 kinds (critical slowing down, signed by the design years) as a nested group; links chosen with the base's warm-up rows in the diagnostic (bnm-0cf); cannot be rerun as chosen (the unclustered test is gone; the fixed diagnostic keeps 1 of its 7 edges); the coupling fix (bnm-5r4) does not touch it |
| 45 | stock-flow states | 2026-10 | diagnostics clustered by year (links, warnings, states rechosen: 5 edges, 1 warning); the stock-flow models' and material base's states that pass as nested groups (coups: arable land; fiscal consolidations: grain use, power capacity); with the warm-up fix (bnm-0cf) the link diagnostic keeps 2 of graph-t45's 5 edges, warnings and states unchanged; result: level; rerun after the outcome with the warm-up and coupling fixes (bnm-xxe, bnm-5r4): as registered byte-identical, level; with only the 2 supported edges (sensitivity): level, +0.00007 [-0.00042, +0.00061], the joint log-score loss gone |
| T1 | horizon profile of named states | 2026-10 | five named states (credit gap, youth bulge, technology gap; food import exposure, reserves), year-demeaned, onset at t+h for h 1-5, slow rising (a) and fast at short leads (b), permutation nulls; result: (a) level, (b) level (+0.078, p 0.109); after the warm-up fix made after the outcome (bnm-0cf): (a) level, (b) passes (+0.143, p 0.015) |
| T2 | calibration by stock regime | 2026-10 | the base recalibrated per kind with a regime level and slope shift shared within the financial (credit gap, reserves) and social (youth bulge, anocracy) groups; result: d > 0 fails in both groups; the same after the warm-up fix made after the outcome (bnm-0cf) |
| T8 | plain recalibration | 2026-10 | the base recalibrated per kind (a_k, b_k from 1960-2002) against the raw base on 2003-2020; result: fails (log financial +0.0036, social -0.0089); after the warm-up fix made after the outcome (bnm-0cf): fails (+0.0037, -0.0086) |
| T4 | total-effect attribution | 2026-10 | 13 states on their own against coverage-matched placebo states, where vs when, and their increment over the recalibrated base; result: S - P' level, +0.0010 [-0.0007, +0.0027]; after the warm-up fix made after the outcome (bnm-0cf): level, +0.0011 [-0.0006, +0.0027] |
| T8b | online recalibration | 2026-10 | per-kind logistic DLM on the base log odds, DMA discounts, reversion and pooling, per-kind publication lags, learned from 1960; result: level, +0.0001 [-0.0016, +0.0019], no harm; after the warm-up fix made after the outcome (bnm-0cf): level, +0.0002 [-0.0015, +0.0019], no harm |
| T3 | stock x trigger interactions | 2026-10 | credit gap x house prices, youth x grain gap, youth x neighbour conflict, technology gap x grain gap; argmax of gauges against their mean; result: no pair passes Holm, the argmax loses to the mean; the same after the warm-up fix made after the outcome (bnm-0cf) |
| T6 | early warnings reframed | 2026-10 | post-crisis rows dropped; calm duration with a + prior; five named indicators pooled by group |
| T5 | slow states over five years, JST 1870-2020 | 2026-10 | credit gap, house and equity growth, current account, credit x house against history alone; developed by leaving eras out, tested on 1996-2015 origins |
| T9 | robustness of the extra dimensions | 2026-10 | input dropout (slow gauges, countries dark, lags doubled), calibration drift and worst era, the 13 states against coverage-matched placebos; result: not kept (slow and dark dropout pass, the worst-era harm bound crossed, -0.00053 [-0.0047, -0.0002]); after the warm-up fix made after the outcome (bnm-0cf): not kept, the same tests pass and the bound is still crossed (-0.00051 [-0.0047, -0.0002]); dark dropout rerun with mixed seeds after the outcome (bnm-0z5): -0.186 [-0.416, -0.100], still passes, the rest byte-identical, not kept |
| CVP | cells against provinces, with features | 2026-10 | the pilot's event runs plus a feature stage (assets, grids, lights, emissions); cells vs provinces and vs within-country shuffles |
| H4 | asset-growth divergence | 2026-10 | equity, valuation, house and wealth gaps against their own past, composite timing for banking crises and market crashes, coverage-matched placebos; result: fails, -0.0051 [-0.0143, +0.0090]; after the warm-up fix made after the outcome (bnm-0cf): fails, -0.0053 [-0.0144, +0.0086] |
| LS | located shocks, cell vs country exposure | 2026-10 | quakes, cyclones, floods, conflict onsets on cells; plant output loss; cell-cut vs uniform country spread; space placebos rerun with mixed seeds after the outcome (bnm-0z5): 19 of 19 at or below (was 18 of 19), verdict unchanged |
| SS | supply shocks as event triggers | 2026-10 | food, fertiliser and energy supply lost to suppliers' shortfalls and disrupted straits, through each country's exposure; food price shocks, protest and violence surges; exposure and time placebos; result: fail, equivalent to nothing (top-10 hard recall -0.024 [-0.065, +0.012]; -0.021 [-0.064, +0.016] after the review fixed the warm-up rows in the fits); power check +0.051, not the registered +0.047, after the warm-up fix (bnm-0cf) |
| FSa | fast signals: commodity prices | 2026-10 | food, energy, fertiliser price change, surprise and volatility through exposure, a nested group at the quarter nodes of all 13 kinds; watch-list primary, country-shift placebo; result: fail, equivalent to nothing (top-10 hard recall -0.006 [-0.015, +0.001]), harm bound crossed (quarters lose); rerun after the review's fixes, unchanged (-0.009); a noise group crosses the harm bound too |
| FSb | fast signals: exchange rates and sovereign spreads | 2026-10 | dollar rate and 10-year spread over the US, change, surprise and volatility, a nested group at the quarter nodes of all 13 kinds; as FSa; result: fail, equivalent to nothing (-0.005 [-0.018, +0.007]), harm bound crossed; rerun after the review's fixes, unchanged (-0.004) |
| FSc | fast signals: port traffic | 2026-10 | PortWatch port calls (year-on-year, surprise, volatility) and chokepoint transits through openness, a nested group at the quarter nodes of all 13 kinds; origins 2020-2022 only; result: fail, equivalent to nothing (+0.000 on 2020-2022), harm (year nodes); rerun after the review's fixes, unchanged (-0.012 [-0.014, +0.000]) |
