# T4: total-effect attribution of the stock-flow states

Bead bnm-3qu.5. Written 22 October 2026 for trial T4 of
docs/LIT-NEXT-STEPS.md ("Total-effect attribution of the stock-flow
states"). Not registered yet. Only the design years (outcomes 1960-2002)
were looked at. No outcome after 2002 was read or scored.

The question: how much of the event forecasts' skill can the stock-flow
states account for, as against what they add over a base that already
knows each country's history? T1 found that the slow states say "where"
and that the base absorbs it. An increment test cannot see a driver whose
footprint the base already carries. This trial measures the states'
total effect.

## Design

tools/bnm/attribution.clj (`bnm.attribution`).

**Rows.** The year node of t44-world-norel: year-end origins t, country
rows, the outcome an onset within the node's 24 months (years t + 1 and
t + 2), the base its forecast p. All kinds with five fitted onsets.

**Models.** Each is a logit with a fixed effect per origin year, fitted on
the same rows:

| Model | Inputs past the year effects |
|---|---|
| M0 | none: each kind's yearly rate, no history |
| M_S | the states (value and missing indicator each), ridge |
| M_P | the placebo states: each country reads the next country's (sorted order, a fixed derangement as in bnm.state-diag) |
| M_B' (primary base) | a + b logit p, b fitted on the fitted era |
| M_B'S, M_B'P | M_B' plus the states, plus the placebo |
| M_B, M_BS, M_BP (record) | logit p as a raw offset (b = 1) |

The recalibrated base is primary. On the design years the raw base's
slope b is far below 1 for most kinds (currency 0.08, inflation 0.15,
growth collapses and market crashes negative), and its levels are off.
With the raw offset, the attribution would mix the base's miscalibration
into "history". The raw-offset numbers are in the tsv (base `offset`,
group `all`).

**Out of sample by era.** Fit on one era and score the other: 1960-1980
and 1981-2002, each way. An era [a, b] holds the origins a to b − 2,
whose windows lie inside it. So no fitted row's outcome year is a scored
row's, and origins 1979-1980 are in neither era. The tool refuses a split
whose fitted era touches a scored outcome year.

**The year level in a scored year.** A scored year has no fitted
effect, and the trial reads no outcome of a scored year. Two modes:

- **within (primary):** each model's forecasts for a scored kind-year
  are shifted by one log-odds constant to M0's level, the fitted era's
  rate. Every model then forecasts the same count in each year. Only
  where the onsets fall differs, and that is the "where" question the
  year effects isolate;
- **raw (record):** the year effects are replaced by the constant that
  reproduces the fitted era's rate, so timing skill counts as well. The
  recalibration slope b is identified within years (the year effects
  take the rest), so applying it to the base's timing is an
  extrapolation. In raw mode the base's skill is level.

**Scores.** Skill is M0's mean loss less the model's, for log score and
Brier, per kind and pooled with kinds weighted equally:

- attribution share = skill(S) / skill(B');
- Shapley split of skill(B'S) between the states and the base's history:
  states = ½[skill(S) + skill(B'S) − skill(B')], history = skill(B'S) −
  states. The two add up to skill(B'S) (tested);
- increment = skill(B'S) − skill(B'): what T1 and trial 45 measured;
- states less placebo = skill(S) − skill(P): LIT-NEXT-STEPS' falsifier;
- the same numbers for the placebo (P, B'P).

Intervals are 90%, from 1,000 bootstrap draws of blocks of two
consecutive origin years (overlapping windows), seed 1. The fits are
held fixed, so the intervals cover the scored years' sampling and not
the refit. Per-kind ratios are unstable where the base has no skill
there (the financial kinds). Read the pooled ratios and the per-kind
skills.

## States

Thirteen. T1's ten (docs/T1-STATES.md, the same readers,
bnm.horizon-profile/state-panels) and the three material levels trial 45
kept (arable land, grain use and power capacity). Each is standardized
but not demeaned: here the cross-section is the point, and the year
effects remove the world part.

| State | Group | Countries | Country-years | Between-country share | |
|---|---|---|---|---|---|
| credit-gap | financial | 121 | 3256 | 0.12 | when |
| keen-debt-gap | financial | 89 | 814 | 1.00 | where |
| debt-service | financial | 89 | 814 | 0.50 | when |
| reserves | financial | 146 | 3138 | 0.64 | where |
| youth-bulge | social | 180 | 5760 | 0.88 | where |
| elite-overproduction | social | 44 | 1118 | 0.31 | when |
| anocracy | social | 174 | 6773 | 0.61 | where |
| food-import-exposure | material | 173 | 3787 | 0.22 | when |
| arable-land | material | 177 | 6254 | 0.99 | where |
| grain-use | material | 162 | 5657 | 0.99 | where |
| power-capacity | material | 177 | 3399 | 0.99 | where |
| fuel-price-gap | material | 170 | 4212 | 0.07 | when |
| technology-gap | technology | 146 | 5611 | 0.92 | where |

Countries and country-years are counted on the design years, among the
countries in the rows. The between-country share is the variance of the
country means after each year's mean is removed, as a share of that
residual variance, on the fitted span's years (1960-2002, for the design
and for the registered run alike). A share of at least 0.5 makes a
"where" state, below it a "when" state. The base's history should absorb
the first group and not the second. Debt service sits on the line
(0.499).

**Reading and causality.** Every state is read as known at the origin:
the T1 states by their T1 conventions; arable land (FAOSTAT) and power
capacity (EIA) two years after their data year, as trial 45 read them;
grain use by the year the food table knows it. The three material levels
are carried forward at most three years. A test checks that no reader
uses a value from after its origin, the missing-value fill included.

**Long tails.** The material levels are sizes (thousand hectares, MW,
tonnes) spread over five orders of magnitude, so they enter as
log(1 + x). Every state is then clipped at the fitted rows' 1st and 99th
percentiles and standardized on them. The clip bounds and the scale come
from the fitted era only.

**Gaps.** A state missing at a country-year enters at its running mean
(the mean of every value known by the origin, over the countries in the
rows), with a missing indicator. The fill uses nothing after the origin.
The indicators carry coverage, which follows income (WID, JST and BIS
cover rich countries), so part of a state's "where" is its coverage. The
placebo reads the other country's value and indicator alike, so it has
the same structure.

**Ridge.** Each state-bearing model has one precision for all its state
coefficients, on the standardized scale. The year effects have a weak
prior (0.01) and the base slope is free. The precision is chosen from
1, 3, ..., 10,000 by the rotation's pooled within log score, separately
for each model and group, so the placebo also gets its own best
penalty. Chosen: S/all 300, B'S/all 1,000, P/all and B'P/all 10,000 (the
grid's top: the placebo is shrunk to nothing). Per group they run from 30
(technology, one state) to 3,000. Written to
data/runs/sequencing/attribution-t4-penalties.edn and fixed from it for
the registered run.

**Kinds.** Protest and violence surges start at origin 1979 on this run,
so the 1960-1980 era has no rows for them and the rotation cannot score
them. Food price shocks have no design-year rows. The design table
therefore pools ten kinds. A run with --fit-from includes protest and
violence (their 1979-2000 rows give the fit). Pass --kinds to keep the
design's ten.

## Design-year results

data/runs/sequencing/attribution-t4-design.tsv (columns: mode, base,
group, kind, score, stat, value, lo, hi). Unless stated otherwise:
within mode, the recalibrated base, pooled over ten kinds, 90%
intervals.

### All thirteen states

| | log score | Brier |
|---|---|---|
| skill(B'), base recalibrated | +0.0074 [+0.0062, +0.0086] | +0.00113 [+0.00080, +0.00145] |
| skill(S), states, no history | +0.0037 [+0.0023, +0.0049] | +0.00022 [−0.00016, +0.00056] |
| skill(P), placebo | +0.0000 [−0.0000, +0.0000] | +0.00000 [−0.00001, +0.00001] |
| skill(B'S) | +0.0082 [+0.0072, +0.0093] | +0.00118 [+0.00086, +0.00147] |
| **share**, S / B' | **0.50 [0.30, 0.71]** | 0.19 [−0.14, 0.54] |
| placebo share | 0.00 [−0.00, 0.00] | 0.00 [−0.00, 0.01] |
| Shapley: states | +0.0023 [+0.0014, +0.0031] (27% of B'S) | +0.00013 [−0.00013, +0.00037] (11%) |
| Shapley: history | +0.0060 [+0.0047, +0.0073] | +0.00104 [+0.00072, +0.00137] |
| states less placebo | +0.0037 [+0.0023, +0.0049] | +0.00022 [−0.00017, +0.00056] |
| increment, B'S − B' | +0.0008 [+0.0003, +0.0013] | +0.00005 [−0.00010, +0.00018] |

### Per group

| Group | share, log | share, Brier | Shapley states / B'S, log | S − P, log | increment, log | increment, Brier |
|---|---|---|---|---|---|---|
| financial | 0.10 [0.06, 0.16] | 0.05 [−0.04, 0.15] | 0.06 | +0.0008 [+0.0004, +0.0011] | +0.0001 [−0.0000, +0.0002] | −0.00001 [−0.00004, +0.00002] |
| social | 0.19 [0.08, 0.31] | 0.17 [−0.02, 0.38] | 0.10 | +0.0014 [+0.0006, +0.0021] | +0.0001 [−0.0001, +0.0003] | −0.00001 [−0.00008, +0.00005] |
| material | 0.30 [0.17, 0.44] | 0.36 [0.11, 0.65] | 0.21 | +0.0021 [+0.0013, +0.0030] | +0.0013 [+0.0005, +0.0021] | +0.00028 [+0.00005, +0.00049] |
| technology | 0.63 [0.46, 0.81] | 0.44 [0.13, 0.73] | 0.33 | +0.0046 [+0.0032, +0.0059] | +0.0009 [+0.0003, +0.0015] | +0.00004 [−0.00015, +0.00023] |
| where (8) | 0.54 [0.38, 0.72] | 0.32 [0.06, 0.64] | 0.29 | +0.0039 [+0.0029, +0.0049] | +0.0008 [−0.0000, +0.0017] | −0.00014 [−0.00039, +0.00012] |
| when (5) | 0.12 [0.02, 0.22] | 0.02 [−0.15, 0.20] | 0.07 | +0.0009 [+0.0002, +0.0016] | +0.0002 [−0.0000, +0.0004] | +0.00002 [−0.00004, +0.00008] |

The placebo share is within ±0.03 for every group.

### Per kind (all states, log score)

| Kind | skill(B') | skill(S) | Shapley states / B'S | increment |
|---|---|---|---|---|
| banking crises | −0.0002 [−0.0017, +0.0014] | +0.0003 [−0.0010, +0.0016] | (B'S ≈ 0) | +0.0002 [−0.0002, +0.0007] |
| conflict onsets | +0.0258 [+0.0198, +0.0312] | +0.0136 [+0.0089, +0.0179] | 0.28 [0.20, 0.36] | +0.0019 [+0.0009, +0.0029] |
| coups | +0.0333 [+0.0259, +0.0397] | +0.0114 [+0.0086, +0.0138] | 0.17 [0.14, 0.20] | −0.0000 [−0.0007, +0.0006] |
| currency crises | +0.0031 [+0.0016, +0.0047] | +0.0071 [+0.0054, +0.0088] | 0.85 [0.64, 1.10] | +0.0027 [+0.0021, +0.0034] |
| fiscal consolidations | +0.0029 [−0.0028, +0.0074] | −0.0010 [−0.0107, +0.0075] | (unstable) | +0.0007 [−0.0028, +0.0038] |
| growth collapses | −0.0005 [−0.0011, −0.0000] | −0.0016 [−0.0023, −0.0009] | (B'S < 0) | −0.0006 [−0.0009, −0.0004] |
| inflation crises | +0.0022 [−0.0008, +0.0051] | +0.0018 [+0.0013, +0.0023] | 0.43 [−0.57, 2.19] | +0.0005 [+0.0003, +0.0007] |
| market crashes | −0.0029 [−0.0079, +0.0018] | −0.0100 [−0.0156, −0.0040] | (B'S < 0) | −0.0024 [−0.0045, −0.0001] |
| sovereign defaults | +0.0013 [+0.0003, +0.0024] | +0.0102 [+0.0080, +0.0128] | 1.25 [1.11, 1.43] | +0.0046 [+0.0037, +0.0056] |
| war onsets | +0.0091 [+0.0048, +0.0135] | +0.0047 [+0.0033, +0.0061] | 0.28 [0.18, 0.47] | +0.0007 [+0.0003, +0.0010] |

### Raw mode (timing kept, record)

Pooled log score: skill(B') +0.0026 [−0.0015, +0.0060], skill(S)
+0.0038 [+0.0024, +0.0051], increment +0.0012 [+0.0004, +0.0020]. The
base's slope, fitted within years, does not carry its timing, so the
base is level here and the share is undefined ([−6.2, 7.1]).

## Reading

These are design-year numbers, for development.

- **On their own, the states account for about half of the base's
  "where" skill on log score** (0.50 [0.30, 0.71]). Another country's
  states account for none (0.00). The Shapley split gives the states 27%
  of the joint model's skill and the base's history 73%. On Brier the
  share is 0.19 [−0.14, 0.54]. Brier weighs the many near-zero rows, and
  the states' gain is in the ranking of the riskier countries.
- **Increment against attribution, as T1 found.** Over the recalibrated
  base the states add +0.0008 log [+0.0003, +0.0013] and +0.00005 Brier
  [−0.00010, +0.00018]. The Brier increment's upper bound is under the
  power audit's +0.0006. Most of what the states say, the base's history
  already carries.
- **"Where" states carry it, "when" states don't.** The eight "where"
  states take a share of 0.54. The five "when" states (credit gap, debt
  service, elite overproduction, food import exposure, fuel price gap)
  take 0.12 on log score and 0.02 on Brier. That is what one expects if
  the base's history holds the cross-section and the year-node design has
  little room for within-country timing.
- **The technology gap alone takes 0.63**, more than all thirteen
  together (0.50). One ridge precision for thirteen states shrinks the
  strong one with the weak ones. It is income and state capacity, and it
  works for conflict, coups and wars.
- **The political kinds carry the pooled share.** Conflict onsets, coups
  and wars have most of the base's skill, and the states take 17-28% of
  it in Shapley terms. For currency crises and sovereign defaults the
  states beat the base: skill(S) is above skill(B'), the increment is
  +0.003 to +0.005 log, and reserves and coverage are the likely
  carriers. Banking crises, growth collapses, market crashes and fiscal
  consolidations have no "where" skill in either model. Note that the
  banking base already reads JST house prices and a stock-flow term for
  the JST countries (tools/bnm/macrohistory.clj), so a financial state's
  footprint there may be in the base already.
- **Caveats.** The missing indicators carry coverage, which follows
  income, so "where" includes "where the data are". The fits are held in
  the bootstrap, so the intervals are too narrow by the refit's
  variance. The penalties were chosen on the same rotation they are
  scored on, which flatters S, P, B'S and B'P alike. All grid choices for
  the placebo hit the top (shrink to nothing).

## For a registered run

    jolt -A:tools -M -m bnm.attribution --fit-from 1960 --fit-to 2002 \
      --from 2002 --to 2022 \
      --penalties data/runs/sequencing/attribution-t4-penalties.edn \
      --reps 1000 --seed 1 \
      --out data/runs/sequencing/attribution-t4-registered.tsv

This fits on origins 1960-2000 (outcomes to 2002) and scores origins
2002-2020, so the scored outcome years are 2003-2022 exactly. Origins
2001 would cover 2002 and are refused. `--from` is the first scored
origin and `--to` the last outcome year, as in bnm.horizon-profile. The
between-country groups are computed on the fitted span (1960-2002), so
they match the table above. Protest and violence surges enter this run
(12 kinds pooled). Add
`--kinds banking_crisis,conflict_onset,coup,currency_crisis,fiscal_consolidation,growth_collapse,inflation_crisis,market_crash,sovereign_default,war_onset`
to keep the design's ten.
