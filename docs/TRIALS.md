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
registered intervals. Rerun on it (bnm-82j.7): T1(b), SS, FSa, T8b, T9,
T4, T6 and H4, no verdict changed; the rest recorded as unaffected
(docs/PHASE-CHANGES.md, under t48-world-norel).

Corrected base, 11 November 2026 (bnm-8ul, not a trial and not counted):
the base of record is t50-world-norel, t48's command on the catalogues
rebuilt after the gazetteer fix with its quarters pooled with r6-at-8ul
(`--known r6-at-8ul`), the onsets run refit on those catalogues with
lapsed sources kept (a source that stops keeps its last values and a
lapse flag; before, its presence indicator stepped every forecast from
2025q3). Against t49-world-norel on 2003-2022 it is the same to about
1e-5 pooled and on the watch list; against t48 it moves as t49 did
(year Brier +0.0003 [+0.0002 +0.0005] pooled, market crashes most; hard
top-10 recall 0.284 against 0.273). No verdict below is rerun; trials
from now on bench against t50-world-norel (docs/PHASE-CHANGES.md, note
to amendment L1).

Corrected candidate, 12 November 2026 (bnm-56c.23, not a trial and not
counted; not adopted): t51b-world-norel, t50's command on the
leak-corrected data with `--leak-fixes true --pwt-stale true` and
`:learn-offsets :mixture`. Against t50: year Brier -0.0013 [-0.0026
+0.0002] pooled, no fold lost; quarter-lead -0.0006. The leak fixes
alone (fx-leak): -0.0001 year, -0.0008 quarter-lead. Fails the noise rule
(-0.0008; the base's weighting fails it too). docs/PHASE-CHANGES.md.

Corrected base, 12 November 2026 (bnm-56c.23, bnm-h8d, bnm-56c.22; not
a trial and not counted): the base of record is t52-world-norel,
t51b's command with `--known r6-at-fx` (r6-at-8ul's onsets run on the
corrected series.tsv, the same as r6-at-8ul to December 2025). t52 is
byte for byte t51b on 2003-2022, so its scores are t51b's above: the
lower quarter-lead score is the honest one, not a loss of the fix. The
noise-rule failure is the base's weighting, inherited. No verdict below
is rerun; trials from now on bench against t52-world-norel
(docs/PHASE-CHANGES.md, note to amendment L1 of 12 November). Tried and
not adopted: `--clim-prior kinds` for bnm-56c.26 (t52a-climprior, three
folds lost, bnm-56c.27).

Corrected base, 12 November 2026, later (bnm-56c.31, .32, .33; not a
trial and not counted): the base of record is t53-noclim, t52's command
with `--known r7-at-fx` and `:flow-std-from :resolved` on the regenerated
series.tsv and econ-events.csv. Against t52: year Brier -0.0009 [-0.0036
+0.0018] pooled, no fold lost; quarter-lead -0.0001. Trials from now on
bench against t53-noclim (docs/PHASE-CHANGES.md, second note to amendment
L1). Not adopted: `:clim-learn :current` (t53, bnm-56c.27).

Corrected base, 13 November 2026 (bnm-x6o, bnm-jnw, bnm-9fg, bnm-e1y,
bnm-56c.27, bnm-56c.36; not a trial and not counted): the base of record
is t65-gauges, t63-issue's command with `--leading true --leading-priors
true` at 7dc9281. Against t53-noclim: year Brier +0.0040 [+0.0018,
+0.0065] pooled, no fold lost against t53-noclim, t62-fixes or t63-issue
(docs/CALIBRATION.md 13). Trials from now on bench against t65-gauges
(docs/PHASE-CHANGES.md, third note to amendment L1). Known open issue:
market-crash false alarms 2018-22 from the 2022 curve inversion and vix12,
an approach limit not tuned post hoc.

Corrected base, 13 November 2026, later (bnm-a1e, bnm-96j, bnm-11y,
bnm-v1y; not a trial and not counted): the base of record is t71-stack,
t65-gauges's command with `--real-lags true --at-risk catalogue
--lead-std history --frailty-gauges true` at 5f7dd70. Against t65-gauges:
year Brier +0.0023 [+0.0004, +0.0046] pooled, log +0.0080; against
t53-noclim +0.0062; no fold lost against either (docs/CALIBRATION.md
15-17). Alerts at 4:1: pooled cost 0.298 against 0.310, 8 of 21 episodes
caught against 6. Trials from now on bench against t71-stack
(docs/PHASE-CHANGES.md, fourth note to amendment L1). Known open issues:
market-crash false alarms from vix12 and the 2022 curve; food 2020, growth
2007-08 and 2019, banking and sovereign defaults never alert; σ at the
grid edge (bnm-02u).

Corrected base, 14 November 2026 (bnm-9vm option A; not a trial and not
counted): the base of record is t80-base, t71-stack's command with
`--dated-credit true`: the banking node's credit, household credit and
credit gap from the BIS from the December 2003 origin, each source read
once published. A data-vintage correction, adopted neutral: banking year
Brier +0.00001 [-0.00023, +0.00029] pooled, no fold gains or loses; all
13 kinds +0.00000 (docs/CALIBRATION.md "Dated credit for banking"). Only
banking changes. Trials from now on bench against t80-base
(docs/PHASE-CHANGES.md, fifth note to amendment L1).

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
| T1 | horizon profile of named states | 2026-10 | five named states (credit gap, youth bulge, technology gap; food import exposure, reserves), year-demeaned, onset at t+h for h 1-5, slow rising (a) and fast at short leads (b), permutation nulls; result: (a) level, (b) level (+0.078, p 0.109); after the warm-up fix made after the outcome (bnm-0cf): (a) level, (b) passes (+0.143, p 0.015); rerun after a base correction on t48-world-norel (bnm-82j.7): (a) level (+0.100, p 0.154), (b) still passes (+0.124, p 0.025), a candidate after a fix, not a registered pass |
| T2 | calibration by stock regime | 2026-10 | the base recalibrated per kind with a regime level and slope shift shared within the financial (credit gap, reserves) and social (youth bulge, anocracy) groups; result: d > 0 fails in both groups; the same after the warm-up fix made after the outcome (bnm-0cf) |
| T8 | plain recalibration | 2026-10 | the base recalibrated per kind (a_k, b_k from 1960-2002) against the raw base on 2003-2020; result: fails (log financial +0.0036, social -0.0089); after the warm-up fix made after the outcome (bnm-0cf): fails (+0.0037, -0.0086) |
| T4 | total-effect attribution | 2026-10 | 13 states on their own against coverage-matched placebo states, where vs when, and their increment over the recalibrated base; result: S - P' level, +0.0010 [-0.0007, +0.0027]; after the warm-up fix made after the outcome (bnm-0cf): level, +0.0011 [-0.0006, +0.0027]; rerun after a base correction on t48-world-norel (bnm-82j.7): S - P' byte-identical (it does not read the base), still equivalent to nothing over the recalibrated base (log +0.0004 [-0.0002, +0.0009]), verdicts unchanged |
| T8b | online recalibration | 2026-10 | per-kind logistic DLM on the base log odds, DMA discounts, reversion and pooling, per-kind publication lags, learned from 1960; result: level, +0.0001 [-0.0016, +0.0019], no harm; after the warm-up fix made after the outcome (bnm-0cf): level, +0.0002 [-0.0015, +0.0019], no harm; rerun after a base correction on t48-world-norel (bnm-82j.7): level, -0.0003 [-0.0020, +0.0012], no harm |
| T3 | stock x trigger interactions | 2026-10 | credit gap x house prices, youth x grain gap, youth x neighbour conflict, technology gap x grain gap; argmax of gauges against their mean; result: no pair passes Holm, the argmax loses to the mean; the same after the warm-up fix made after the outcome (bnm-0cf) |
| T6 | early warnings reframed | 2026-10 | post-crisis rows dropped; calm duration with a + prior; five named indicators pooled by group; result: fails (calm duration, financial, post-crisis out +0.106, p 0.94), financial log variance positive with a placebo as strong; rerun after a base correction on t48-world-norel (bnm-82j.7), with bnm-as1's private credit, which calm duration reads directly: fails (+0.098, p 0.950), nothing passes Holm |
| T5 | slow states over five years, JST 1870-2020 | 2026-10 | credit gap, house and equity growth, current account, credit x house against history alone; developed by leaving eras out, tested on 1996-2015 origins; result: the joint model passes over history alone (log +0.0455 [-0.0383, +0.1786], permutation p 0.015, placebo -0.0228); house-price growth passes Holm over five terms (+0.0268, p 0.005), the credit gap does not (+0.0515, Holm 0.100); the lead profile does not rise (-0.0287, p 0.67); the test era is mostly 2007-08, and it does not carry to the lattice, whose banking base already reads these states |
| T9 | robustness of the extra dimensions | 2026-10 | input dropout (slow gauges, countries dark, lags doubled), calibration drift and worst era, the 13 states against coverage-matched placebos; result: not kept (slow and dark dropout pass, the worst-era harm bound crossed, -0.00053 [-0.0047, -0.0002]); after the warm-up fix made after the outcome (bnm-0cf): not kept, the same tests pass and the bound is still crossed (-0.00051 [-0.0047, -0.0002]); dark dropout rerun with mixed seeds after the outcome (bnm-0z5): -0.186 [-0.416, -0.100], still passes, the rest byte-identical, not kept; rerun after a base correction on t48-world-norel (bnm-82j.7): dropout unchanged, harm bound still crossed (-0.00054 [-0.0035, +0.00007]), not kept |
| CVP | cells against provinces, with features | 2026-10 | the pilot's event runs plus a feature stage (assets, grids, lights, emissions); cells vs provinces and vs within-country shuffles; result: fails both primaries (violence escalation from quiet, 2015-26: cells with features AUC 0.744 against provinces 0.810 [-0.105, -0.022], BSS 0.023 against 0.039; above only 16 of 19 shuffles on AUC and 13 on BSS); provinces stay the unit inference reads |
| H4 | asset-growth divergence | 2026-10 | equity, valuation, house and wealth gaps against their own past, composite timing for banking crises and market crashes, coverage-matched placebos; result: fails, -0.0051 [-0.0143, +0.0090]; after the warm-up fix made after the outcome (bnm-0cf): fails, -0.0053 [-0.0144, +0.0086]; rerun after a base correction on t48-world-norel (bnm-82j.7): both tests byte-identical (they do not read the base), fails |
| LS | located shocks, cell vs country exposure | 2026-10 | quakes, cyclones, floods, conflict onsets on cells; plant output loss; cell-cut vs uniform country spread; result: cell does not win (cell less country squared error +0.00026 [-0.0000, +0.0005], shock years 2011-2023), countries stay the unit; space placebos rerun with mixed seeds after the outcome (bnm-0z5): 19 of 19 at or below (was 18 of 19), verdict unchanged |
| SS | supply shocks as event triggers | 2026-10 | food, fertiliser and energy supply lost to suppliers' shortfalls and disrupted straits, through each country's exposure; food price shocks, protest and violence surges; exposure and time placebos; result: fail, equivalent to nothing (top-10 hard recall -0.024 [-0.065, +0.012]; -0.021 [-0.064, +0.016] after the review fixed the warm-up rows in the fits); power check +0.051, not the registered +0.047, after the warm-up fix (bnm-0cf); rerun after a base correction on t48-world-norel (bnm-82j.7): fail, equivalent to nothing (+0.006 [-0.015, +0.032]) |
| FSa | fast signals: commodity prices | 2026-10 | food, energy, fertiliser price change, surprise and volatility through exposure, a nested group at the quarter nodes of all 13 kinds; watch-list primary, country-shift placebo; result: fail, equivalent to nothing (top-10 hard recall -0.006 [-0.015, +0.001]), harm bound crossed (quarters lose); rerun after the review's fixes, unchanged (-0.009); a noise group crosses the harm bound too; rerun after a base correction on t48-world-norel (bnm-82j.7): fail, equivalent to nothing (-0.006 [-0.016, +0.002]), harm bound crossed, no longer beats its placebo |
| FSb | fast signals: exchange rates and sovereign spreads | 2026-10 | dollar rate and 10-year spread over the US, change, surprise and volatility, a nested group at the quarter nodes of all 13 kinds; as FSa; result: fail, equivalent to nothing (-0.005 [-0.018, +0.007]), harm bound crossed; rerun after the review's fixes, unchanged (-0.004) |
| FSc | fast signals: port traffic | 2026-10 | PortWatch port calls (year-on-year, surprise, volatility) and chokepoint transits through openness, a nested group at the quarter nodes of all 13 kinds; origins 2020-2022 only; result: fail, equivalent to nothing (+0.000 on 2020-2022), harm (year nodes); rerun after the review's fixes, unchanged (-0.012 [-0.014, +0.000]) |

**Trial 3 (ties-un) and look-ahead, 18 November 2026 (bnm-e69).** Trial 3 read a session's UN agreement from January of the next year, months before Voeten's release, and carried the last year forward without limit. bnm.named/un-weights now reads a session only from the third quarter of the next year and carries it at most two years. Trial 3 lost with the look-ahead in its favour, so its verdict stands; the file's ideal points (re-estimated over all years each release) are not read.

## Lattice era, November 2026: runs and diagnostics on the 2003-2022 development origins

Every world run and derived copy scored on the December origins 2003-2022
(some also on 1990-2002) from 11 to 27 November 2026, none of them listed
above before this section. All of 2003-2022 is development data that every
decision since 11 November has read (docs/PHASE-CHANGES.md, twenty-sixth
note to amendment L1). Dates are the day the result was written (git date
of the section where the document gives none). "The rule" is the fold rule
of the run's registration: no development fold lost with its 90% interval
below zero and a pooled Brier gain; from 18 November a fold with fewer than
5 onsets cannot veto alone (decided after seeing t97); from 20 November a
candidate that gains on pooled Brier, the alert cost at 4:1 and pooled AUC
passes despite a fold loss ("dominance", decided after seeing t124). Multi-kind
runs are judged kind by kind; the verdict cell gives each. "Where" is a
docs/CALIBRATION.md heading unless another file is named.

The corrected bases already noted above as "not a trial and not counted"
(t49-world-norel, t50-world-norel, t52-world-norel, t53-noclim, t80-base),
the adoption builds that reproduce a scored run byte for byte, the off-path
and reproduction checks, and superseded first runs are listed after the
table and not counted in it. t51b-world-norel, t52a-climprior, t53,
t65-gauges and t71-stack are named in the notes above but were scored
candidates, so they are counted here; whether to count them is a decision
for this file.

### Scored runs

| id | date | what changed (one line) | kinds scored | verdict under its rule | adopted? | where |
|---|---|---|---|---|---|---|
| t51-world-norel | 2026-11-11 | t50 plus the world node and world state at the year (step 2) | 13 | fail (rule 1 per fold, rule 4; planted and false-move rules met) | no | PHASE-CHANGES.md "Registered, 11 November 2026: the world node and the world state at the year" |
| t51b-world-norel | 2026-11-12 | t50 plus `--leak-fixes --pwt-stale`, `:learn-offsets :mixture` | 13 | fail (noise rule -0.0008; no fold lost; pooled -0.0013 [-0.0026, +0.0002]) | not as a candidate; its forecasts are t52-world-norel's (correction, note of 12 November) | PHASE-CHANGES.md "Look-ahead fixes and the corrected candidate t51b-world-norel" |
| t52a-climprior | 2026-11-12 | t51b/t52 plus `--clim-prior kinds` | 13 | fail (three folds lost, pooled -0.0081 against t51b) | no | PHASE-CHANGES.md "Note to amendment L1, 12 November 2026"; LOOP-AUDIT.md 5 |
| t52c-climlearn | 2026-11-12 | t52 plus `:clim-learn :current` | 13 | fail (2007-09 lost; pooled -0.0004) | no | LOOP-AUDIT.md "5. Diagnosis of bnm-56c.27", Rescore |
| t52d-climlearn-prior | 2026-11-12 | t52c plus `--clim-prior kinds` | 13 | fail (2007-09, 2010-12 lost) | no | LOOP-AUDIT.md 5, Rescore |
| t52-fx31 | 2026-11-12 | t52 plus the FAOSTAT lag fix alone (one part of t53-noclim) | 13 | diagnostic attribution (-0.0009 pooled against t52), not a candidate | no (part of the t53-noclim correction) | PHASE-CHANGES.md "Second note to amendment L1, 12 November 2026" |
| t53 | 2026-11-12 | t53-noclim plus `:clim-learn :current` | 13 | fail (pooled +0.0003, 2010-12 lost) | no | PHASE-CHANGES.md "Second note to amendment L1, 12 November 2026" |
| t54-cand | 2026-11-12 | t53-noclim plus `:flow-std-min-origins 10` | 13 | fail (2020-22 lost, -0.0018 [-0.0031, -0.0007]) | no | LOOP-AUDIT.md "7. Diagnosis of the market-crash folds under bnm-56c.34" |
| t55-cand | 2026-11-12 | t53-noclim plus `--clim-prior kinds` and `:clim-learn :current` | 13 | fail (2010-12) | no | 4. Candidate t55-cand |
| t56-temper-only | 2026-11-12 | t53-noclim plus the averaging tempered once per origin | 13 | pass (pooled +0.00231 GAIN, no fold lost) | no | 6. The averaging tempered per origin |
| t56-cand | 2026-11-12 | t56-temper-only plus `:clim-learn :current` | 13 | fail (2010-12) | no | 6. The averaging tempered per origin |
| t57-frailty | 2026-11-12 | t53-noclim plus `:clim-learn :current` and `:frailty {}` | 13 | fail (2007-09 against t53-noclim) | no (both options later in t65-gauges' command) | 7. The frailty |
| t58-level | 2026-11-12 | t57 plus a persistent random-walk level | 13 | fail (2007-09 against t53-noclim) | no | 8. The persistent level |
| t59-exposure | 2026-11-13 | t57 plus exposure loading and `:evidence-at :sequential` | 13 | fail (2007-09 against t53-noclim, 2003-06 against t57) | no | 9. The loading on exposure |
| t59-slope | 2026-11-13 | t57 plus the exposure slope alone | 13 | fail (2013-19 LOSS against t57; no verdict line, from the table at L297-301) | no | 9. The loading on exposure |
| t59-seq | 2026-11-13 | t57 plus sequential evidence alone | 13 | fail (2003-06 LOSS against t57; from the table at L297-301) | no | 9. The loading on exposure |
| t60-fresh | 2026-11-13 | t57 plus monthly food outcomes and a fresh quarter-now frailty scale | 13 (12 without food) | fail (2020-22 against t60-monthly, 2007-09 against t53-noclim) | no | 10. The frailty from a fresh quarter scale |
| t60-monthly | 2026-11-13 | t57 plus `--food-outcomes monthly` | 13 | neutral (+0.0001 against t57; a target change, L495) | no | 10. The frailty from a fresh quarter scale |
| t61-regional | 2026-11-13 | t57 plus regional shared states and sequential evidence | 13 | fail (2003-06 against t57; 2007-09, 2010-12 against t53-noclim) | no | Regional shared states: t61-regional |
| t61-regional-indep | 2026-11-13 | t57 plus regional states alone | 13 | fail (loses 2010-12 and 2020-22, L3200) | no | Regional shared states: t61-regional |
| t61-mc-sub | 2026-11-13 | market crashes only, subregion states chosen by the grid | 1 | diagnostic, not a candidate | no | Regional shared states: t61-regional |
| t61-mc-p0 | 2026-11-13 | market crashes only, regional sd forced to 0.5 | 1 | diagnostic, not a candidate | no | Regional shared states: t61-regional |
| t62-fixes | 2026-11-13 | t57 plus LEADING.md 4's three fixes | 13 | pass (+0.0031 GAIN against t53-noclim, no fold lost against either bench) | no (fixes carried into t63 and t65-gauges) | 11. The three bugs of LEADING.md 4 fixed |
| t62-fixes-seq | 2026-11-13 | t62-fixes plus `:evidence-at :sequential` | 13 | fail (food -0.0145) | no | 11 |
| t62-fixes-min | 2026-11-13 | t62-fixes plus `:flow-std-min-origins 10` | 13 | fail (2007-09 against t53-noclim; passes against t57) | no | 11 |
| t63-issue | 2026-11-13 | t62-fixes plus `:evidence-at :issue-joint` | 13 | fail (2007-09 against t53-noclim) | no (option carried into t65-gauges) | 12. Evidence as the joint predictive issued per origin |
| t63-issue-f | 2026-11-13 | t63-issue plus `:forget-half-life 10` | 13 | fail (2007-09 against t53-noclim, 2013-19 against t62) | no | 12 |
| t64-regional | 2026-11-13 | regional states with own persistence, issue-joint evidence | 13 | fail (2007-09 against t53-noclim) | no | Second round: own persistence (t64) |
| t64-regional-noij | 2026-11-13 | the same with t62's evidence | 13 | fail (2007-09 against t62, 2010-12 against t65) | no | Second round (t64) |
| t65-gauges | 2026-11-13 | t63-issue plus `--leading --leading-priors` | 13 | pass (+0.0040 against t53-noclim; no fold lost against t53, t62, t63) | yes: base of record (third note to L1, 13 November) | 13. Leading gauges with priors from theory |
| t65-gauges-flat | 2026-11-13 | the same gauges at default priors | 13 | fail (2007-09 against t53; 2003-06, 2020-22 against t63) | no | 13 |
| t66-fix | 2026-11-13 | t65 plus `--real-lags` (ALFRED publication dates) | 13 | neutral: no fold lost, "worth nothing to the score" (L989) | yes, in t71-stack | 15. The lookahead fix, the food node and the second food set |
| t66-food | 2026-11-13 | t66-fix plus `--lead-std history --leading-food2` | 13 | fail (2013-19 against t65, 2010-12 against t66-fix, 2007-09) | no (`--lead-std history` later in t71-stack) | 15 |
| t67-growthq | 2026-11-13 | t65 plus a quarterly-dated growth-collapse catalogue | growth (own target) | fail (year -0.0275 LOSS against t67-annual103; quarter-lead flat) | no | GROWTH-QUARTERLY.md |
| t67-annual103 | 2026-11-13 | t65 with the annual catalogue restricted to the 103 covered units | growth | comparison base for t67-growthq, not a candidate | no | GROWTH-QUARTERLY.md |
| t68-atrisk | 2026-11-13 | t65 plus `--at-risk catalogue` | 13 | pass (+0.0003 GAIN, no fold lost) | yes, in t71-stack | 16. At-risk sets and growth discrimination |
| t69-pool | 2026-11-13 | t65 plus real lags, `--lead-std history`, `--frailty-gauges` | 3 (food, market, growth) | pass (no fold lost; +0.0017 GAIN against t66-fix) | yes, in t71-stack | 17. Level from world gauges, ranking from places |
| t69-pool-nolead | 2026-11-13 | t69-pool without `--lead-std history` | 3 | pass on scores (no fold lost, GAIN) but not proposed: keeps the standardizer defect (L1415) | no | 17 |
| t71-stack | 2026-11-13 | t65 plus real lags, at-risk, lead-std history, frailty gauges | 13 | pass (+0.0023 against t65, +0.0062 against t53-noclim, no fold lost) | yes: base of record (fourth note, 13 November) | PHASE-CHANGES.md fourth note to L1 (CALIBRATION 15-17) |
| t72-fresh | 2026-11-13 | t71 plus `--fresh-inputs` (WDI and Maddison at publication) | 12 (banking copied) | neutral (pooled -0.00001, no fold lost) | no | 18. Fresh inputs: t72-fresh |
| t73-nq | 2026-11-13 | t71 plus an issuing next-quarter scale | 3 | pass trivially, no year row changes (unclear as a verdict, L2528) | no | 18. Issuing next-quarter scale |
| t74-sigma | 2026-11-13 | t71 plus `:frailty {:sds :wide}` | 13 | fail (no pooled gain; "not accepted", L1576) | no | 18. Frailty grid and climatology prior |
| t75-clim | 2026-11-13 | t71 plus `--clim-prior kinds` | 13 | fail (2010-12, 2020-22) | no | 18. Frailty grid and climatology prior |
| t76-nq-hp | 2026-11-13 | t73 plus next-quarter priors chosen after seeing 2008, 2020, 2022 | 3 | neutral, in-sample (gains not significant) | no | Horizon priors: t76-nq-hp |
| t77-pmean | 2026-11-13 | t71 plus `:frailty {:centre :rate}` | 13 | fail (2010-12) | no | 19. Frailty centred on the rate |
| t78-pmean-wide | 2026-11-13 | t77 plus the wide σ grid | 13 | fail (2010-12; alert cost loses) | no | 19 |
| t79-bis | 2026-11-13 | t71 plus `--dated-credit` (BIS credit by release) | banking | neutral (+0.00001, no fold gains or loses) | yes, as a correction: t80-base (fifth note, 14 November), "not under the acceptance rule" | Dated credit for banking: t79-bis |
| t81-widebank | 2026-11-13 | t79-bis plus `--wide-credit true` | banking | fail (2013-19, 2020-22) | no | Banking credit beyond JST |
| t82-widebank | 2026-11-13 | t79-bis plus `--wide-credit bank` | banking | fail (2013-19, 2020-22) | no | Banking credit beyond JST |
| t83-regime | 2026-11-18 | t80 plus `--regime` | 4 | neutral: byte for byte t80-base, the layer switched itself off (L1978) | no | Regime layer |
| t83-regime-rate | 2026-11-18 | t83-regime plus `:centre :rate` | 13 | fail (2010-12) | no | Regime layer |
| t83-diag-free | 2026-11-18 | the regime's price set to 0, chosen after t83's result | 4 | diagnostic, in-sample, not a candidate | no | Regime layer |
| t84-rclim | 2026-11-18 | t80 plus `--regime-clim true` | 4 | fail (three folds, pooled -0.00145) | no | Regime climatology |
| t84-rclim-nc | 2026-11-18 | t80 plus `--regime-clim uncentred` | 4 | neutral (no fold won or lost, pooled -0.00021); not accepted | no | Regime climatology |
| t85-bgauges | 2026-11-18 | t80 plus four world gauges at banking, theory priors | banking | fail | no | Banking gauges |
| t85-bgauges-flat | 2026-11-18 | the same at flat priors | banking | fail | no | Banking gauges |
| t88-rho0 | 2026-11-18 | t80, banking frailty at ρ 0 | banking | fail (2020-22) | no (ρ 0 later in t104-base) | Banking's frailty out of phase |
| t88-nofrail | 2026-11-18 | t80, no frailty at banking | banking | fail (2020-22) | no | Banking's frailty out of phase |
| t92-cov | 2026-11-18 | t80 plus `--banking-coverage` (nine declared signals) | banking | no change (every fold under 1e-5), rule moot (L2839) | no | Banking coverage from declared signals |
| t92-cov-rho0 | 2026-11-18 | t92-cov with ρ 0 | banking | no change against t88-rho0, rule moot | no | Banking coverage from declared signals |
| t93-entry | 2026-11-18 | t80 plus `:late-entry :prior` | banking | neutral (pooled -0.00004) | no | Late entry and the coverage, together |
| t93-entry-cov | 2026-11-18 | t93-entry plus the coverage | banking | neutral (within 0.00002 of t93-entry) | no | Late entry and the coverage, together |
| t93-entry-cov-rho0 | 2026-11-18 | t93-entry-cov with ρ 0 | banking | fail (2020-22) | no | Late entry and the coverage, together |
| t94-long60 | 2026-11-18 | t80 plus `--long-baseline 60` | banking | fail (2010-12) | no | Long baselines at banking |
| t94-long60-entry | 2026-11-18 | t94-long60 plus late entry | banking | fail (pooled and 2010-12) | no | Long baselines at banking |
| t94-long60-entry-rho0 | 2026-11-18 | t94-long60-entry with ρ 0 | banking | fail (2020-22) | no | Long baselines at banking |
| t95-trace | 2026-11-18 | t80 plus `--trace-groups` (byte for byte t80) | banking | diagnostic, not a candidate | no | Where the banking signal is lost |
| t96-bank | 2026-11-18 | t80 plus `--splice-credit bank` | banking | neutral (+0.00010, no fold lost) | no | The banking fixes together |
| t96-pp | 2026-11-18 | t80 plus `:presence :per-place` and the per-place temper | banking | fail (four folds and pooled) | no | The banking fixes together |
| t96-all | 2026-11-18 | bank splice, per-place, long baseline, ρ 0 | banking | fail (2020-22) | no | The banking fixes together |
| t97-theory | 2026-11-18 | t80 plus `--theory-offset` (credit logit as banking's year offset) | banking | fail (2020-22) | no | A theory forecast as banking's year offset |
| t97-theory-rho0 | 2026-11-18 | t97-theory with ρ 0 | banking | fail as registered (2020-22, 0 onsets); passes the rule as amended 18 November, an amendment decided after seeing it | no (its offset and ρ 0 carried into t104-base) | A theory forecast as banking's year offset |
| t97-theory-long-rho0 | 2026-11-18 | t97-theory-rho0 plus `--long-baseline 60` | banking | fail (2020-22) | no | A theory forecast as banking's year offset |
| t98-theory-rho05 | 2026-11-18 | t97-theory-rho0 with ρ in {0, 0.5}, chosen after t97 | banking | fail as reported (2020-22 LOSS); not rejudged under the 18 November amendment (unclear, L2981) | no | Theory offset with a short persistence |
| t99-twospeed | 2026-11-18 | ρ 0 plus a slow random-walk level | banking | fail (no pooled gain) | no | Two speeds |
| t99-theory-twospeed | 2026-11-18 | t99-twospeed plus the theory offset | banking | fail (2010-12) | no | Two speeds |
| t100-world-rho0 | 2026-11-18 | t97-theory-rho0 plus `--theory-world funding` | banking | pass (amended rule) | no (built on by t101) | The calm from banks' funding |
| t101-reserves-rho0 | 2026-11-18 | t100 plus world reserves outside the panel | banking | pass (both rules; pooled +0.00112 GAIN) | no (built on by t104) | The level outside the panel: reserves |
| t102-denominator-rho0 | 2026-11-18 | t101 plus `--credit-denominator smooth` | banking | unclear: equals t101 within noise, "not adopted"; no verdict against t80-base stated (L3034) | no | The panel's 2020 credit jump |
| t103-recession-rho0 | 2026-11-18 | t101 plus `--recession-hold true` | banking | pass (both rules) | no (built on by t104) | Credit in a recession is not a boom |
| t103-trace | 2026-11-18 | group trace of t103 | banking | diagnostic, not a candidate | no | Credit in a recession is not a boom |
| t104-rows-rho0 | 2026-11-18 | t101 plus `--recession-hold rows` | banking | pass (no fold lost by either rule, pooled +0.00119 GAIN) | yes: t104-base (sixth note, 18 November), "with the user's reading" (within-year AUC 0.575 below t100's 0.653) | Recession rows held for every group |
| t105-debt-rho0 | 2026-11-18 | t104-base with `--recession-hold debt` | banking | pass (amended rule) on a +0.00001 point gain; "recorded, not proposed" | no | Hold only the debt group |
| t106-rate | 2026-11-19 | t104-base plus `:centre :rate` at every kind | 13 | fail | no | Regional states on the current base |
| t106-regional | 2026-11-19 | t106-rate plus regional states | 13 | fail | no | Regional states on the current base |
| t107 | 2026-11-19 | t104-base plus `--bloc-theory growth_collapse=8` | growth, pooled 13 | fail (harm test) | no | Bloc exposure offset on real data |
| t108 | 2026-11-19 | t104-base plus `--theory-contagion` | banking | fail (no pooled gain) | no | Exposure to banking stress abroad |
| t109 | 2026-11-19 | t104-base plus `--kind-theory sovereign_default` | sovereign | fail (2003-06); read only 2 of its 5 inputs (L3480), rerun as registered in t110 | no | Sovereign defaults' level from stocks and flows |
| t110 | 2026-11-19 | stocks-and-flows theories at sovereign, currency, inflation | 3 | sovereign pass, inflation pass, currency fail | yes: sovereign and inflation as t111-base (seventh note, 19 November), gains not significant, within-year AUC falls | Currency and inflation crises' level ..., and t109 as registered: t110 |
| t112 | 2026-11-19 | currency theory added on top of the offset | currency | fail | no | Currency crises' theory on top of the offset |
| t113 | 2026-11-19 | global layer at the 8 economic kinds | 8 | market crashes pass; banking, currency, sovereign, inflation, growth, fiscal, food fail | yes: market crashes in t116-base (eighth note, 19 November) | The global layer: t113 |
| t114 | 2026-11-19 | `--impute` at sovereign and inflation | 2 | inflation pass; sovereign fail (2010-12) | yes: inflation in t116-base | Missing inputs filled: t114 |
| t115 | 2026-11-19 | currency re-specified, every input shaped | 3 | currency pass; inflation (shaped) pass; sovereign fail | yes: currency in t116-base (inflation's shapes not taken) | Currency crises re-specified, and shapes per input |
| t117 | 2026-11-19 | inflation@monthly, currency@partners, shaped market global layer | 3 | inflation pass; currency fail; market fail | yes: inflation@monthly in t118-base (ninth note, 19 November) | Timelier inflation, trade-weighted partner falls, a shaped global layer |
| t119 | 2026-11-19 | coup and war theories from the literature, shaped | 2 | coup pass; war fail | yes: coup in t119-base (tenth note, 20 November) | Coups and war onsets from their literature, shaped |
| t120 | 2026-11-20 | fiscal, growth and banking literature theories, shaped | 3 | fiscal pass; growth fail; banking fail | yes: fiscal in t120-base (eleventh note, 20 November) | Banking, fiscal consolidations and growth collapses from their literature |
| t121 | 2026-11-20 | banking literature theory unshaped, raw-scale priors | banking | fail | no | Banking's literature theory without missing flags |
| t122 | 2026-11-20 | growth literature theory unshaped, raw-scale priors | growth | fail | no | Growth collapses' literature theory without missing flags |
| t123 | 2026-11-20 | market global layer on raw-scale priors | market | fail | no | The market-crash global layer on raw-scale priors |
| t124 | 2026-11-20 | market global drivers' precision 10 | market | fail as registered (2010-12, 2020-22 lost); pass by dominance under the 20 November amendment, decided after this result | yes: t124-base (twelfth note, 20 November) | The market-crash global layer with its world drivers held tighter |
| t125 | 2026-11-20 | `--impute` at sovereign on t124-base | sovereign | fail on the folds (2010-12); pass by dominance, every interval crossing zero | yes: t125-base (thirteenth note, 20 November) | Sovereign defaults on filled inputs, rerun |
| t126 | 2026-11-21 | banking@banks theory | banking | fail (folds and dominance) | no | Banking on its banks' health and the commodity bust |
| t127 | 2026-11-21 | `--banking-spans true` (crisis years busy to their end) | banking | fail (2010-12; no dominance); first run void | no | Banking crises busy to their end |
| t128 | 2026-11-21 | banking@boom-bust on the spanned target | banking | fail (folds and dominance) | no | Banking's commodity boom and bust, on the spanned target |
| t129 | 2026-11-21 | banking@aftermath on the spanned target | banking | fail (folds and dominance) | no | Banking's aftermath not read as risk |
| t130 | 2026-11-21 | `--aftermath all` (onset kernels at every kind) | 13 | conflict, coup, fiscal, inflation, sovereign, war pass; banking, currency, food, growth, market, protest, violence fail | yes: the six as t131-base (fourteenth note, 21 November); withdrawn 22 November (fifteenth note: kernels read onsets before publication) | What past onsets do to the next |
| t132 | 2026-11-22 | `--aftermath-net` at the seven kinds t130 failed | 7 | all seven fail | no (option withdrawn) | The kernels net of the world's onsets |
| t133 | 2026-11-22 | `--aftermath-gauges all` (kernels in the year node, known onsets) | 13 | growth pass by dominance (2020-22 lost); sovereign, violence pass; coup, protest pass unchanged; 8 fail | yes: growth only, t133-base (sixteenth note, 22 November) | The kernels in the year node, from known onsets |
| t134 | 2026-11-22 | market global drivers moved into the year node | market | fail | no | The global drivers in the year node |
| t135 | 2026-11-22 | `--direction-gauges market_crash` at the year | market | fail (pooled worse, no dominance) | no | The world's stress by its direction |
| t136 | 2026-11-22 | direction gauges at the quarter nodes | market | pass (no fold lost; intervals cross zero, alert cost worse) | yes: t136-base (seventeenth note, 22 November) | The world's stress by its direction, at the quarter nodes |
| t137 | 2026-11-23 | `--theory-decay` at 5 theory kinds | 5 | coup, currency, inflation pass; fiscal, sovereign fail | yes: coup and inflation in t137-base (eighteenth note, 23 November) | Legacy stocks read against time since the onset |
| t138 | 2026-11-23 | t137 with decay precision 10 | 5 | currency pass; coup, fiscal, inflation, sovereign fail | no | The decay's interactions held tighter |
| t139 | 2026-11-23 | t137 without the decay's level | 5 | inflation pass; coup, currency, fiscal, sovereign fail | no | The decay's interactions without its level |
| t140 | 2026-11-23 | currency decay plus a 10-year averaging half-life | currency | pass (no fold lost) | yes: t140-base (nineteenth note, 23 November) | Currency crises' decay with a shorter memory |
| t141 | 2026-11-23 | `--year-decay` at the 7 kinds without a theory | 7 | protest pass (nothing moved); 6 fail | no | What an onset leaves behind, at the kinds without a theory |
| t142 | 2026-11-24 | t141 with the decay in the core | 7 | war pass; 6 fail | no | The decay read by every model |
| t143 | 2026-11-25 | the aftermath's level form, in the core | 5 | war pass; conflict, food, protest, violence fail | no | The aftermath's own level, read by every model |
| t144 | 2026-11-25 | sovereign decay plus a 10-year averaging half-life | sovereign | fail (2020-22) | no | Sovereign defaults' decay with a shorter memory |
| t145 | 2026-11-25 | t143 plus 10-year half-lives | 5 | violence, war pass; conflict, food, protest fail | no (violence's memory taken via t149) | The aftermath's level learned on recent decades |
| t146 | 2026-11-25 | t143 with the clock from the episode's end | 5 | war pass; 4 fail | no | The aftermath's level from the episode's end |
| t147 | 2026-11-25 | food aftermath net of the world's | food | fail | no | Food price shocks' aftermath net of the world's |
| t148 | 2026-11-25 | GDELT violence in conflict onsets' year node | conflict | fail (2007-09) | no | GDELT violence in conflict onsets' year node |
| t149 | 2026-11-25 | violence surges' 10-year averaging half-life alone | violence | pass | yes: t149-base (twentieth note, 25 November) | Violence surges' memory alone |
| t150 | 2026-11-25 | `--release-calendar conflict_onset,war_onset` | 13 (12 change) | conflict, war pass; 5 kinds pass within noise; currency, food, growth, market, protest fail within noise | yes: adopted as run, t150-base on t149-base (twenty-first note, 25 November) | Conflict and war onsets known at UCDP's release |
| t151 | 2026-11-25 | the calendar on the own history alone | conflict, war | fail (no gain) | no | The calendar on the own history alone |
| t152 | 2026-11-26 | `--clim-prior food_price_shock` | food | fail (2010-12, 2020-22) | no | Food's prior before its first outcome |
| t153 | 2026-11-26 | food 10-year averaging half-life | food | fail (2010-12; "in effect neutral") | no | Food's averaging with a finite memory |
| t154 | 2026-11-26 | t153 plus the clim prior | food | fail (2020-22) | no | Food's averaging with a finite memory |
| t155 | 2026-11-26 | banking without the theory offset | banking | fail (no pooled gain) | no | Banking without the theory offset |
| t156 | 2026-11-26 | `--theory-outside-panel` (no offset at JST countries) | banking | pass by dominance (2013-19 lost), every interval crossing zero | yes, in t157-base (twenty-second note, 26 November) | Banking's theory offset outside the panel only; result under "The 26 November review" |
| t157-recal | 2026-11-26 | inflation year rows shifted by a rolling intercept | inflation | pass (no fold lost) | yes, in t157-base | The 26 November review (step 4) |
| t157-recal-all | 2026-11-26 | every kind recalibrated the same way, for information | 13 | information only (banking loss; coup, violence, sovereign, market gain) | no | The 26 November review (step 4) |
| t158 | 2026-11-26 | `:forget-half-life 5` in every year averaging | 13 (11 change) | conflict pass; ten fail | yes: conflict only, t158-base (built; the ledger moved to t160 instead) | Step 3, the year averaging's memory |
| t159 | 2026-11-26 | `:up-net-of-downscale true` at every kind (echo cut) | 13 | banking, conflict, coup, violence, war pass; seven fail | yes: the five kinds, via t160 | Step 5, the year's echo through the quarters removed |
| t160 | 2026-11-26 | t158-base plus the echo cut at five kinds | conflict (other four checked byte for byte against t159) | pass | yes: base of record (twenty-third note, 26 November) | The echo cut at five kinds on t158-base |
| t161 | 2026-11-27 | sovereign theory with a 10-year half-life | sovereign | pass (by the level; pooled AUC 0.596 -> 0.569) | yes: base of record (twenty-fourth note, 27 November) | The sovereign theory's fit with a ten-year memory |
| t162 | 2026-11-27 | t161's slopes at the full history's level | sovereign | fail against t161, its base (2007-09, 5 onsets, clear of zero; alert cost worse); pass against t160 | yes: base of record (twenty-fifth note, 27 November), a judgment override | Sovereign defaults' half-life slopes at the full history's level |
| t163 | 2026-11-27 | fiscal consolidations on the discretionary label D2 | fiscal (own target), growth | fail (rules 1 and 3; "not a candidate") | no | t163: fiscal consolidations on the discretionary label D2 |
| t163-recal | 2026-11-27 | t163 with a rolling intercept at fiscal | fiscal (D2) | fail (rule 1) | no | t163-recal |
| t162-recal-fiscal | 2026-11-27 | t162 with a rolling intercept at fiscal on the current label, for information | fiscal | information only | no | t163-recal |
| R1-combination | 2026-11-28 | study on t162's tap: pools, causal stack, reference logit against the averaging's choice | 13 | combination at conflict, war, growth; selection holds at eight | no (diagnosis) | R1 result (bnm-7yg.8.6) |
| R2-hierarchical | 2026-11-28 | study: the hierarchical challenger, judged once by R3 | conflict, war, growth | conflict pass, war fail, growth fail (badly); the stack carried at conflict and war | shadow only: the stack at conflict and war in 2027-Q1 | R2 and R3 result |
| t165-informed | 2026-11-28 | `:evidence-from :informed` at every kind (uninformed rows not scored for the weights; augur v0.25.0) | 13 (all change) | fail: pooled 2010-12 and 2013-19 lost; banking, violence, war lose | no | Food's uninformed rows out of the weights' evidence |
| t166-food-prior | 2026-11-28 | t165-informed plus `--clim-prior food_price_shock` | 13 | registered | pending | Food's uninformed rows out of the weights' evidence |

### Registered and not run

- t76-both, 13 November: t74-sigma and t75-clim together; not made because neither passed (18. Frailty grid and climatology prior).
- t86-ldelay, t86-nolead, t86-ydelay, 18 November: the delay test on t80-base, registered with its rule; no result and no run directory (Delay test on the base of record: t86).
- t164-food, 27 November: food's prior as a shadow against t162; dropped before running because it would reproduce t152 exactly (Combining evidence instead of selecting a channel, R0.2).

### Corrected bases, adoption builds, checks and superseded runs (not counted)

- Corrected bases, already in the notes above: t49-world-norel (11 November, the base at amendment L1), t50-world-norel (11 November), t52-world-norel (12 November; t51b's forecasts, the noise rule failed and inherited), t53-noclim (12 November; pooled -0.0009 [-0.0036, +0.0018] against t52, no fold lost, adopted as a correction), t80-base (14 November; t79-bis's option, neutral).
- Adoption builds, each checked byte for byte against the scored run(s) it carries: t104-base, t111-base, t116-base (t113 market, t114 inflation, t115 currency), t118-base, t119-base, t120-base, t124-base, t125-base, t131-base (withdrawn), t133-base, t136-base, t137-base, t140-base, t149-base, t150-base (t150 on t149-base; violence surges scored for the record, +0.00011), t157-base (t156 and t157-recal), t158-base.
- Off-path and reproduction checks (byte for byte their base): t53-noclim-repro, t65-gauges-repro, t65-gauges-repro-atrisk, t65-gauges-repro-gq, t65-repro-pool, t71-repro-fresh, t71-repro-pmean, t71-stack-repro, t79-off, t81-off, t83-off, t84-off, t85-off, t88-off, t92-off, t94-off, t96-off, t97-off; augur v0.23.1 reruns t57-v231 and t62-fixes-v231 (within 1e-5 of t57-frailty and t62-fixes).
- Superseded first runs, scored and replaced: t57-frailty-v1 (rare kinds issued too high), t66-food-v1 (standardizer from 1990, lost more), t72-fresh-v1 (dated only the archive's years; its +0.00045 GAIN was a WDI lookahead).
- On disk under data/runs but not in the documents: t150b-ref, t150b-refown, t53-noclim-online, t66-repro, t66-repro2, t89-repro, t90-repro, t91-repro.

### Unnumbered harness runs, 11-12 November (docs/PHASE-CHANGES.md)

| runs | date | question | outcome |
|---|---|---|---|
| noise-group weightings (bnm-56c.2) | 2026-11-11 | do candidate weightings let a noise group cost nothing? | noise rule met, 2020-22 lost by 0.002; not adopted ("Development result, 11 November 2026: links that cost nothing") |
| lp1, lp1b (bnm-56c.17) | 2026-11-11 | weightings with the self-loop cut and issue-time evidence | no weighting passes; not adopted ("Development, 11 November 2026: weightings with the self-loop cut") |
| s2-ref, s2-noise1, s2-noise2, s2-planted, s2-false (with t51-world-norel) | 2026-11-11 | step 2's world node harness | t51 fails rules 1 and 4 (row t51-world-norel above) |
| s2-delay1, s2-nogauge (bnm-pjg.6) | 2026-11-11 | does the year gauges' gain survive a year's delay? | no: all the Brier gain and half the log gain go ("Round 4's delay test") |
| fx-leak, fx-noise1, fx-noise2, fx-planted (with t51b) | 2026-11-12 | the look-ahead fixes alone, noise and planted harness | fx-leak -0.0001 year, -0.0008 quarter-lead; noise rule fails on fx-leak too |
| fs-s2-cand, fs-s2-cand-ref, fs-s2-noise1, fs-s2-planted, fs-s2-false, s2d-t51ref | 2026-11-12 | step 2 rescored after fixes 2 and 3 | 2007-09 lost (-0.0316); not adopted ("Step 2 rescored after fixes 2 and 3") |
| gw-cand, gw-noise1, gw-noise2, gw-planted, gw-false | 2026-11-12 | the world term gated per kind by the lead test | market crashes 2003-06 -0.110; not adopted ("The world term gated per kind by the lead test") |
| c15-mc-temper, c15-mc-w1n | 2026-11-12 | single-kind probes of the averaging's weight lock | tempering to 1/47 cuts market crashes' Brier 0.1996 -> 0.1707 (CALIBRATION 2) |

### Diagnostics and studies on 2003-2022

| study | date | question | script | outcome |
|---|---|---|---|---|
| development figure against new benchmarks (bnm-56c.1) | 2026-11-11 | the base's skill against last12 and other causal benchmarks | bnm.benchmarks | reported, not a trial (PHASE-CHANGES.md "Development figure, 11 November 2026") |
| pooled hazard and scanner, as registered (bnm-56c.6) | 2026-11-11 | do buffer invariants time breaks? | bnm.hazard-run | 2 of 7 families pass (banking, sovereign debt just); a registered test of another model, perhaps a row of its own (PHASE-CHANGES.md "Results, 11 November 2026") |
| calibration of t53-noclim, where the excess comes from | 2026-11-12 | why the base is miscalibrated, by kind | single-kind captures | common shock counted per place (weight lock), food's uninformed start; led to t55-t57 (CALIBRATION 1-3) |
| loop gain and the intercept against the offset (bnm-56c.27) | 2026-11-12 | why the base is unstable to initial conditions | REPL replays | food intercept learned against a low prior offset; t52c/t52d rescored (LOOP-AUDIT.md 5) |
| market-crash 2007-09 under bnm-56c.33 | 2026-11-12 | which fix in t53-noclim costs 2007-09 | replays | one origin, December 2007, weight lock (LOOP-AUDIT.md 6) |
| alerts at the 4:1 cost ratio | 2026-11-13 | decisions at p >= 0.2 against never/always | bnm.alerts | t65-gauges 0.304 against the constant rule's 0.306; reported beside acceptance from then on (CALIBRATION 14) |
| growth year node discrimination | 2026-11-13 | why growth collapses do not rank | traces of t65-gauges | a lagged climatology, nothing ranks within an origin (CALIBRATION 16) |
| what sets the level, four checks | 2026-11-18 | level against rate across kinds; JST turn; learned combinations; coverage | kinds_level.py, turn_study.py, turn_dims.py, learn_combos*.clj | level runs against the rate where learned from outcomes; two thirds of banking onsets outside JST (in-sample for design) |
| reference that knows only that crises cluster | 2026-11-18 | does each kind beat a regional/world clustering logit? | clustering_reference.clj | banking and sovereign defaults lose to it; every other kind beats it |
| banking: target or pipeline | 2026-11-18 | does a plain credit logit rank the lattice's banking rows? | banking_credit_check.clj | yes, AUC 0.805 (1990-2002), 0.699 (2003-2020) where the lattice does not |
| each banking group's own forecast (t95-trace) | 2026-11-18 | where the banking signal is lost | banking_groups.py | averaging does not weigh what groups know; the credit splice degrades the stretch from 2003 |
| calm level and reserves outside the panel | 2026-11-18 | what tracks banking's world level at the origin | calm_level.clj, calm_liquidity.py, calm_em.py | banks' funding and world reserves; studied before t100/t101 were registered (in-sample) |
| the Dec 2022 banking spike | 2026-11-18 | theory or groups? | theory_diag.clj | the groups' departure from the offset (debt group), not the offset |
| where the within-year ranking went | 2026-11-19 | why banking within-year AUC moves | banking_rank_years.py | gaps between countries tiny (sd 0.17-0.27 log odds); credit booms' ceiling about 0.70 |
| the level bug across kinds | 2026-11-19 | why the level runs against the rate | level_lag.py, level_fresh.clj | mean p follows the rate 3-4 origins back; fresher outcomes and a held long-run level do not fix it |
| oracle study (bnm-yc2) | 2026-11-19 | information or extraction? | oracle_arms.clj | information present, linear slopes do not extract it (currency: logit 0.634, stumps 0.694, lattice 0.693) |
| breadth on top of shapes | 2026-11-19 | does graded breadth add to shapes? | oracle_arms.clj arm A7 | no (AUC falls); declined offline, no run |
| rank 0 of the aftermath plan | 2026-11-21 | risk by years since the last onset | aftermath_strata.py | recurrence under-forecast after onsets, refractory year over-forecast |
| after t132 | 2026-11-22 | why the net kernels fail | aftermath/issued.clj | the dropped intercept lifts every row; with the publication-lag bug, `--aftermath` withdrawn |
| after t134: market crashes' over-forecast | 2026-11-22 | where the 2010-22 over-forecast comes from | market_crash/mc_years.py, mc_scales.py, mc_compose.py, mc_recal.py | an era, shared by every scale; a faster level helps little and costs the wave |
| after t137: currency AUC | 2026-11-23 | why currency AUC fell | currency/auc_change.py, currency/slopes.clj | the loss is in long-quiet countries' crises |
| after t138 | 2026-11-23 | where currency AUC goes | currency/slopes.clj, currency/decay_contrib.clj | the year node counts the decay's level twice |
| after t139 | 2026-11-23 | currency's year node | currency/offset_change.clj | weights unchanged, contradiction group 0.92-1.00 |
| the year node row by row | 2026-11-23 | model or averaging? | currency/node_tap.clj, node_tap.py, forget_sim.py | the averaging delays the move to the best-ranking model; led to t140 |
| after t142: sovereign and fiscal decay | 2026-11-25 | why the decay costs those two kinds | aftermath/node_tap.clj, node_tap.py | currency's mechanism: the decay holds the averaging on a worse model |
| before rank 5: no episode ends | 2026-11-25 | do catalogues hold episode ends? | catalogue check | no; busy months are short |
| after t146: recent rows by year | 2026-11-25 | food and conflict aftermath by year | aftermath/recent_by_year.py | food is the world wave; conflict recurrence real and under-forecast; led to t147 |
| conflict onsets' slope on d | 2026-11-25 | sign of the aftermath slope | aftermath/decay_slope.clj | positive from 1964; duplicates the history inputs |
| conflict onsets' recent rows | 2026-11-25 | why recent rows are under-forecast | aftermath/history_tap.clj | the 18-month publication lag hides 41% of recent onsets |
| conflict publication lag and a fast signal | 2026-11-25 | release calendar and GDELT | aftermath/conflict_release_calendar.clj, conflict_fast_signal.clj | a calendar would show all 72 scored rows; led to t148 and t150 |
| banking's year node layer by layer (banking node_tap) | 2026-11-26 | which layer buries banking's ranking | banking/node_tap.clj, node_tap.py | the theory offset ranks at chance at JST (0.492) and buries the groups' signal; led to t155, t156 |
| 26 November review, step 1: scorecard | 2026-11-26 | per-kind AUC, level, skill against causal baselines | scorecard.py | the measuring tool used from then on |
| step 2: reference logits | 2026-11-26 | does a ridge logit on the node's inputs beat the lattice? | reference_tap.clj, reference.py | only at conflict and war onsets, slightly |
| step 4: recalibrate_intercept study | 2026-11-26 | a rolling intercept per kind | recalibrate_intercept.py | inflation passes (t157-recal); every kind together would lose (t157-recal-all) |
| held-back block 1990-2002 (bnm-7yg.7) | 2026-11-26 | do the 26 November adoptions hold on earlier origins? | bnm.links --from 1990 --to 2002, bnm.alerts | consistent (t160 against t150-base +0.00013); only coups and banking actually tested |
| food split (bnm-7yg.6) | 2026-11-26 | global wave against domestic part | food_split.py | wave not forecastable from world prices a year ahead; closed |
| sovereign liquidity against solvency, on t160 | 2026-11-26 | does a split theory beat a pooled one? | sovereign_split.py | no; refit logits rank 2003-22's defaults backwards (0.442) |
| why the sovereign inputs rank backwards | 2026-11-26 | which input or group breaks | sovereign_backwards.py | the inputs do not; the refit combination does |
| sovereign theory coefficients | 2026-11-27 | which slopes take the wrong sign | sovereign_theory_fits.clj | none; the fit is set by pre-2003 decades; led to t161 (`--theory-signs` built, changes nothing) |
| sovereign forecast by part | 2026-11-27 | which part of t161 ranks worse | sovereign_parts.clj, sovereign_parts.py | all of t161's AUC cost is in the theory offset |
| sovereign theory rows, ablations | 2026-11-27 | which term costs t161 | sovereign_theory_rows.py | the intercept; led to t162 |
| sovereign own-history coding | 2026-11-27 | does since-last's coding cause the 9+ band? | sovereign_history.clj, sovereign_history.py | no; not a bug, nothing registered |
| growth-at-risk | 2026-11-27 | do financial conditions move the lower tail out of sample? | growth_at_risk.py | F does not beat B at h 4; closed |
| discretionary fiscal consolidation | 2026-11-27 | is the label cyclical; is D2 forecastable? | fiscal_discretionary.py | D2 passes its three conditions (one by a point); led to t163 |
| liquidity/solvency split under the 10-year half-life (bnm-7yg.6.2) | 2026-11-27 | the split re-asked with t162's fix | sovereign_split.py --half-life 10 | split loses on log and Brier; closed |

### Counts

- Scored runs in the table above: 141 rows, of which 132 candidates and 9 diagnostic or information runs (t52-fx31, t61-mc-sub, t61-mc-p0, t67-annual103, t83-diag-free, t95-trace, t103-trace, t157-recal-all, t162-recal-fiscal). 30 of the rows were adopted in whole or for some kinds.
- Registered and not run: 5 (t76-both, t86-ldelay, t86-nolead, t86-ydelay, t164-food).
- Not counted: 5 corrected bases, 17 adoption builds, 20 off-path, reproduction and version checks, 3 superseded first runs.
- Unnumbered harness run families, 11-12 November: 8.
- Diagnostics and studies: 45.
- Adoptions (base-of-record changes by notes to L1, third to twenty-fifth, the fifteenth being a withdrawal): 22, one withdrawn (t131-base), so 21 stand; t80-base among them is a correction adopted neutral. Before them four corrections (t49, t50, t52, t53-noclim) already in the notes above.
- Adoptions that passed only by dominance or by judgment against a failed rule: 5. Dominance with every interval crossing zero: t124-base (the dominance rule was written after seeing t124), t125-base, t133-base, t157-base (t156). Judgment against a failed rule: t162 (failed against t161). Close to these, recorded in docs/CALIBRATION.md "How each base change since t104 passed": t104-base and t111-base adopted "with the user's reading" on non-significant gains or a ranking loss (t104 passed only under the 18 November amendment, written after t97); and before t104, t52-world-norel (noise rule failed, inherited), t53-noclim (no pooled gain) and t80-base (neutral) adopted as corrections, not under the acceptance rule; t150 adopted as run with five kinds failing within noise (counted as a pass by R0.4).

### Extraction

Run ids were taken from docs/CALIBRATION.md, docs/PHASE-CHANGES.md, docs/LOOP-AUDIT.md and docs/GROWTH-QUARTERLY.md with

    grep -ohE '(^|[^A-Za-z0-9_-])t[0-9]{2,3}[a-z]?(-[a-z0-9]+)*' <docs> | sed -E 's/^[^t]//' | sort -u

kept when the id is also a directory under data/runs/ (`ls -d data/runs/t*/`) and its number is 49 or more, then classified by hand into the table, the corrected bases, adoption builds, checks and superseded runs. Registered ids with no run directory (t76-both, t86-*, t164-food) were added from the text. The leading-character guard matters: without it `s2d-t51ref` yields a false `t51r`. That gives 186 ids: 141 table rows, 5 corrected bases, 17 adoption builds, 20 checks and reruns and 3 superseded runs. With the 5 registered ids the list below has 191, ordered by number. test/bnm/trials_test.clj checks that every run docs/CALIBRATION.md registers ("Run tNNN") is named here; add the line when registering, before the run.

