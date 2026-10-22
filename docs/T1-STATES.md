# T1: the named slow states and their horizon profile

Bead bnm-3qu.2. Written 22 October 2026 for trial T1 of
docs/LIT-NEXT-STEPS.md ("Horizon profile of a pre-registered short list,
pooled across kinds"). This is the development list. It is not
registered yet. The final list size waits on the power audit (T0,
tools/bnm/power_audit.clj). Only the design years (1960-2002) were
looked at. No outcome after 2002 was read.

The hypothesis: the skill of slow stocks rises with lead time (1 to 5
years), and the skill of fast triggers falls.

## The list

Each state is read as known at the origin t, the end of year t. "Known"
means published by then: each table's year is the year its value is
known, or the slow store's own availability date. Values are the latest
vintages, as everywhere in the project, so later revisions are read as if
they had been published on time.

Sign is the expected sign of the coefficient. Speed: slow means the
coefficient should grow in its expected sign with lead; fast means it
should shrink. The expected lead is in years.

| # | State | Variable | Kinds | Sign | Speed, expected lead | Source |
|---|---|---|---|---|---|---|
| 1 | credit gap | `credit/gap` (data/derived/stocks/credit.csv, new) | banking crises, growth collapses | + | slow, 2-5 | Drehmann & Juselius 2014; Schularick & Taylor 2012; Mian, Sufi & Verner 2017 (growth) |
| 2 | Keen debt over equilibrium | `keen/d-gap` (keen.csv): d less the calibrated model's equilibrium d | banking crises, sovereign defaults, growth collapses, fiscal consolidations | + | slow, 3-5 | Keen-Goodwin core (docs/STOCK-FLOW.md); Jordà, Schularick & Taylor 2013; Mian, Sufi & Verner 2017 |
| 3 | debt service (proxy) | `keen/r` × `keen/d` (keen.csv): the real long rate times private debt to GDP | banking crises, growth collapses | + | fast, 1-2 | Drehmann & Juselius 2014 (the debt-service ratio dominates at 1-2 years) |
| 4 | food import exposure | WDI food imports share (`food/imports_share`, fraction) × the world grain price gap (mean of `cycles/{wheat,maize,rice}-price-gap`, world rows) | protest surges (food price shocks have no design-year rows) | + | fast, 1 | Hendrix & Haggard 2015; Bellemare 2015 |
| 5 | youth bulge (proxy) | 100 less WDI's working-age share (`people/working_age_share`) ten data years earlier: the children then, the 10-24s now | protest surges, violence surges, conflict onsets | + | slow, 3-10 | Urdal 2006; Goldstone 1991 |
| 6 | elite overproduction | `tension/emp` (tension.csv) | protest surges, violence surges, coups | + | slow, 5-10 | Turchin 2016; Turchin & Nefedov 2009 |
| 7 | technology gap | `technology/gap-historical` (technology.csv): the frontier's adoption less the country's | conflict onsets, coups | + | slow, location more than timing | Fearon & Laitin 2003 (income); Powell 2012 (coups, low income) |
| 8 | commodity price gap, fuel exporters | `cycles/fuel-exposure` (cycles.csv): fuel export share × the oil price's gap to its cycle equilibrium | growth collapses, sovereign defaults, currency crises | + | slow, 2-4 | LIT-NEXT-STEPS ranked trial 3 (commodity price gap × terms-of-trade fall); no direct main-effect paper |
| 9 | reserves | log of WDI reserves in months of imports (`debt/reserves_months`) | currency crises, sovereign defaults | − | fast, 1-2 | Kaminsky & Reinhart 1999; Frankel & Saravelos 2012 |
| 10 | anocracy | 4p(1 − p), p V-Dem's electoral democracy index (`regime/electoral_democracy`, v2x_polyarchy): highest in the middle | coups, conflict onsets, violence surges | + | slow, 2-5 | Hegre et al. 2001; Goldstone et al. 2010; Vreeland 2008 (caveat) |

### Definitions and known-year conventions

- **Credit gap** (tools/bnm/credit_run.clj). Private non-financial credit
  to GDP from keen's three sources. At each year end, one source is
  chosen as keen chooses d (bnm.keen/choose-d): BIS total credit if 20 of
  its data years are known, else JST bank loans over GDP if 20 are, else
  the longest series, World Bank domestic credit to the private sector
  included. The gap is the last known data year less its one-sided
  Hodrick-Prescott trend, in percentage points. The filter is refitted
  at each year end on the chosen source's own series (never spliced), on
  data years before Y that were published by Y. It needs at least 10
  contiguous years, and the latest must be no more than 3 years old.
  lambda is 1,562.5. That is BIS's 400,000 for quarterly data carried to
  annual data (400,000 / 4^4, Ravn & Uhlig 2002): the trend is fitted to
  annual means, because JST and the World Bank are annual. Where the
  source is BIS, the gap at Y correlates 0.9995 with BIS's own published
  gap for data year Y − 1 (1,615 country-years). For the World Bank source
  the concept is domestic bank credit, not total credit: the same gap, a
  narrower aggregate.
- **Keen debt gap.** `keen/d-gap` = d less d-bar, the equilibrium debt of
  the model calibrated on a window ending at Y − 1. Keen's debt against
  *critical* debt (`keen/d-distance` = d-critical − d) is the closer
  match to the theory. But d-critical exists only where the model has a
  finite blow-up: 32 countries and 188 country-years in 1960-2002, about
  10 a year before 1990. It is too thin to test, so d-gap stands in.
- **Debt service.** BIS's debt-service ratios (`finance/dsr_private`)
  cover 32 countries, mostly from 1999, so they can't be tested on the
  design years. r·d is the interest half of a debt-service ratio, with
  the real long rate and no amortization. `keen/r` exists only in years
  where the model is calibrated. This is the weakest proxy on the list.
- **Food import exposure.** The country's food share of merchandise
  imports (WDI, data year d, available in September of d + 1, at most 3
  years old) times the world grain price gap at Y (each cycle model's
  log price less its equilibrium, as known at Y). `food/grains-import-
  dependence` × storage pressure is the closer match, but import
  dependence starts in 1988 (FAOSTAT trade). Food price shocks (FAOSTAT
  2000-, WDI before) have no year-node rows with outcomes by 2002 in
  t44-world-norel, so in practice the state is tested on protest surges
  only.
- **Youth bulge.** No 15-24 or 0-14 share is in the store. The proxy is
  tension's (bnm.tension, mmp-youth), as a level, not a change: the share
  outside working age ten data years before, known when data year d's
  figure is. It includes the over-65s, which adds noise in rich countries.
  `tension/mmp-youth` itself (a five-year change, z-scored on the
  country's own past) starts only in 1985 (216 countries, 3,888
  country-years), too short for leads of 5 years.
- **Elite overproduction.** `tension/emp`, the mean of credentials
  (tertiary enrolment growth against income growth) and the top decile's
  income share, each over five years and standardized on the country's
  own past; known only where the top share is (WID): 44 countries.
- **Technology gap.** By year known. Bigger means further behind the
  frontier: a proxy for income and state capacity.
- **Fuel price gap.** The fuel export share (latest data year before Y
  known by Y, at most 5 years old) times the WTI cycle's log price gap at
  Y. The sign assumes reversion: exporters whose price is far above its
  equilibrium face the fall.
- **Reserves.** WDI, data year d available in September of d + 1, at most
  3 years old; log months, floored at 0.1.
- **Anocracy.** V-Dem v16, data year d available in March of d + 1, at
  most 3 years old. Vreeland (2008) showed that Polity's middle categories
  partly code political violence itself (PARREG, PARCOMP), so part of
  the anocracy effect is circular. The electoral democracy index has no
  factionalism component, so the circularity is smaller. But V-Dem's
  expert coding is retrospective, and an election held in a civil war
  still moves it.

### Coverage on the design years (origins 1960-2002)

From `horizon-t1-design.tsv`'s header: countries and country-years with
the state, then the rows and onsets the h = 1 and h = 5 fits use (the
state, the base forecast and the outcome all known; kinds pooled).

| State | Countries | Country-years | Years | Rows, onsets h=1 | Rows, onsets h=5 |
|---|---|---|---|---|---|
| credit-gap | 129 | 3365 | 1960-2002 | 5205, 191 | 3817, 158 |
| keen-debt-gap | 91 | 816 | 1961-2002 | 2691, 77 | 2142, 65 |
| debt-service | 91 | 816 | 1961-2002 | 1373, 36 | 1096, 35 |
| food-import-exposure | 192 | 4124 | 1968-2002 | 2268, 63 | 2003, 41 |
| youth-bulge | 216 | 6912 | 1971-2002 | 11532, 639 | 9166, 363 |
| elite-overproduction | 44 | 1118 | 1960-2002 | 2319, 101 | 1726, 62 |
| technology-gap | 148 | 5691 | 1960-2002 | 9848, 562 | 7234, 281 |
| fuel-price-gap | 186 | 4503 | 1963-2002 | 10968, 404 | 8157, 323 |
| reserves | 154 | 3300 | 1961-2002 | 5487, 235 | 3698, 154 |
| anocracy | 175 | 6816 | 1960-2002 | 14616, 937 | 10850, 497 |

### Considered and dropped

- `keen/d-distance` (debt against critical): 32 countries, 188
  country-years. Replaced by d-gap.
- BIS debt-service ratios: 32 countries, from about 1999.
- `food/grains-import-dependence` and its storage-pressure exposure:
  from 1988.
- `tension/mmp-youth`: from 1985.
- Food price shocks as a kind: no design-year rows.

## The tool

tools/bnm/horizon_profile.clj (`bnm.horizon-profile`). For each state,
each of its kinds and the kinds pooled, and each lead h = 1..5:

- the outcome is the kind's onset in year t + h exactly: months
  (12(t+h−1)+11, 12(t+h)+11], not at risk while a crisis runs (the
  lattice's target, bnm.lattice-banking/onset-in?). Origins run from 1960
  to 2002 − h, so no outcome after 2002 is read;
- the state is read at t (as-known; the tests check that it is never
  read after t, and that no outcome after `to` is touched);
- the fit is bnm.relation-diag/clustered-test: a score statistic with the
  outcome years as clusters, and the coefficient per SD (log odds ratio)
  from the fitted logit;
- **offset base**: t44-world-norel's year-node forecast at t. That node
  forecasts an onset within 24 months, (t, t + 2]. (The 3-year figure in
  the brief is world-baseline.tsv's simulation window, not the node's.)
  It enters as the yearly hazard a constant hazard gives,
  1 − (1 − p)^(1/2), and the same offset is used at every h. Past h = 2
  it is an extrapolation of a forecast that does not cover that year.
  forecasts.tsv also holds the quarter nodes (lead: months 4-12 after
  the origin; w2: months 13-24, which is year t + 2 exactly) and the
  half-decade node (every fifth year end only). None of them reaches
  t + 3..5, and switching nodes across h would make the offset change
  with h, so the profile would partly measure the offset. So: one base
  for every h, with this caveat;
- **offset none**: each kind's own rate in the rows. This is the total
  effect, with no history and no gauges. Base against none shows
  increment against attribution (LIT-NEXT-STEPS 3b);
- **placebo**: the same state read at the next country, in sorted order,
  of those with the state that year, on the same rows.

**The pooled slope test (specified before the run):**

- For each state, pool its kinds' rows into one coefficient at each h
  (the table's `pooled` rows).
- The state's slope is the mean coefficient at h = 4, 5 less the mean at
  h = 1, 2.
- The statistic is the mean over states of slope × direction. Direction
  is the expected sign × (+1 for a slow state, −1 for a fast one).
- The null permutes country labels within each state's countries, so
  each country reads another's whole series, and recomputes the
  statistic. 200 draws, seed 1. One-sided p = (1 + #{null ≥ observed}) /
  201.
- For the record, the statistic on the placebo reads.
- The base-offset statistic is the primary one. The total-effect
  statistic is reported beside it.

    jolt -A:tools -M -m bnm.credit-run
    jolt -A:tools -M -m bnm.horizon-profile --draws 200 --seed 1

## Design-year profile

**Offset base (increment over t44-world-norel).** Pooled coefficient (log odds ratio per SD) with its signed clustered statistic z in brackets; the placebo's z last.

| State | h=1 | h=2 | h=3 | h=4 | h=5 | placebo z, h=1..5 |
|---|---|---|---|---|---|---|
| credit-gap | +0.156 (+1.29) | +0.117 (+1.02) | +0.086 (+0.98) | +0.127 (+1.36) | +0.130 (+1.50) | +0.08 -0.38 -0.56 +0.22 -0.62 |
| keen-debt-gap | +0.044 (+0.40) | +0.030 (+0.28) | +0.109 (+0.94) | +0.139 (+1.15) | +0.162 (+1.34) | +1.05 +1.37 +1.68 +1.34 +0.77 |
| debt-service | -0.123 (-0.82) | -0.128 (-0.82) | -0.137 (-0.92) | -0.242 (-1.56) | -0.238 (-1.49) | -0.18 -0.40 -0.20 -0.42 -0.73 |
| food-import-exposure | -0.107 (-0.27) | +0.240 (+0.55) | -0.115 (-0.58) | -0.006 (-0.06) | -0.060 (-0.52) | -1.45 -0.38 -1.33 -1.33 -1.19 |
| youth-bulge | -0.019 (-0.31) | -0.034 (-0.50) | -0.070 (-1.03) | -0.098 (-1.40) | -0.112 (-1.92) | -1.24 -1.54 -1.79 -1.49 -1.12 |
| elite-overproduction | -0.044 (-0.26) | +0.155 (+0.82) | +0.110 (+0.67) | +0.144 (+0.99) | +0.331 (+2.17) | +2.57 +3.34 +2.16 +1.26 +0.84 |
| technology-gap | +0.073 (+0.76) | +0.018 (+0.18) | +0.009 (+0.07) | -0.044 (-0.37) | -0.078 (-0.57) | -1.55 -1.44 -0.90 -0.84 -0.48 |
| fuel-price-gap | +0.137 (+1.14) | +0.124 (+1.40) | +0.127 (+1.77) | +0.167 (+2.04) | +0.214 (+2.21) | +1.38 +1.59 +1.59 +2.20 +1.75 |
| reserves | -0.265 (-2.30) | -0.178 (-1.58) | -0.178 (-1.45) | -0.240 (-1.89) | -0.238 (-1.71) | -1.17 -0.86 -1.14 -0.95 -0.23 |
| anocracy | +0.007 (+0.18) | -0.016 (-0.34) | +0.023 (+0.46) | +0.004 (+0.09) | +0.032 (+0.61) | +0.03 +0.01 -0.30 -0.98 -1.04 |

**Offset none (total effect).** Pooled coefficient (log odds ratio per SD) with its signed clustered statistic z in brackets; the placebo's z last.

| State | h=1 | h=2 | h=3 | h=4 | h=5 | placebo z, h=1..5 |
|---|---|---|---|---|---|---|
| credit-gap | +0.139 (+1.13) | +0.087 (+0.76) | +0.045 (+0.53) | +0.077 (+0.85) | +0.073 (+0.83) | -0.18 -0.74 -1.03 -0.35 -1.27 |
| keen-debt-gap | -0.021 (-0.17) | -0.022 (-0.20) | +0.077 (+0.63) | +0.122 (+0.97) | +0.156 (+1.25) | +0.52 +0.86 +1.37 +1.13 +0.65 |
| debt-service | +0.024 (+0.18) | +0.013 (+0.10) | -0.000 (-0.00) | -0.106 (-0.72) | -0.096 (-0.67) | +0.56 +0.30 +0.55 +0.37 +0.02 |
| food-import-exposure | +0.237 (+1.91) | +0.323 (+2.48) | +0.303 (+2.00) | +0.419 (+2.10) | +0.347 (+2.10) | -0.75 +0.25 +0.18 +1.33 +1.69 |
| youth-bulge | +0.192 (+2.97) | +0.179 (+2.79) | +0.147 (+2.34) | +0.117 (+1.92) | +0.093 (+1.78) | +0.24 +0.34 +0.31 +0.44 +0.72 |
| elite-overproduction | -0.236 (-1.63) | -0.107 (-0.72) | -0.192 (-1.23) | -0.164 (-1.14) | -0.008 (-0.07) | +1.51 +2.81 +0.29 -0.73 -1.10 |
| technology-gap | +0.754 (+5.80) | +0.695 (+5.61) | +0.684 (+5.44) | +0.626 (+5.23) | +0.590 (+4.68) | +0.33 +0.52 +1.08 +0.89 +1.00 |
| fuel-price-gap | +0.054 (+0.65) | +0.044 (+0.71) | +0.044 (+0.83) | +0.066 (+1.05) | +0.093 (+1.38) | +1.10 +1.00 +0.87 +1.63 +1.17 |
| reserves | -0.259 (-2.41) | -0.192 (-1.86) | -0.199 (-1.73) | -0.262 (-2.11) | -0.271 (-2.04) | -1.43 -1.14 -1.30 -1.21 -0.40 |
| anocracy | -0.041 (-1.21) | -0.065 (-1.66) | -0.029 (-0.63) | -0.044 (-0.94) | -0.017 (-0.37) | -0.58 -0.63 -0.93 -1.47 -1.55 |

**Slope per state** (mean coef at h = 4, 5 less at h = 1, 2; the
direction is the expected sign × speed, so slope × direction > 0 is the
hypothesis):

| State | Direction | Slope, base | Slope, none | Placebo slope, base |
|---|---|---|---|---|
| credit-gap | +1 | -0.008 | -0.038 | -0.002 |
| keen-debt-gap | +1 | +0.113 | +0.160 | -0.037 |
| debt-service | -1 | -0.114 | -0.120 | -0.053 |
| food-import-exposure | -1 | -0.100 | +0.103 | +0.114 |
| youth-bulge | +1 | -0.078 | -0.080 | +0.010 |
| elite-overproduction | +1 | +0.181 | +0.085 | -0.298 |
| technology-gap | +1 | -0.107 | -0.117 | +0.029 |
| fuel-price-gap | +1 | +0.060 | +0.030 | +0.049 |
| reserves | +1 | -0.017 | -0.041 | +0.048 |
| anocracy | +1 | +0.023 | +0.023 | -0.054 |

**Pooled slope test** (200 permutations of country labels, seed 1):

| Offset | Statistic | Placebo | Null mean (sd) | One-sided p |
|---|---|---|---|---|
| base (primary) | +0.038 | -0.032 | +0.008 (0.033) | 0.189 |
| none | +0.004 | -0.051 | -0.014 (0.027) | 0.234 |

Full table, per kind as well: data/runs/sequencing/horizon-t1-design.tsv.

## Reading

These are design years, for development: the readings below shape the
list, they are not the test.

- **No rising profile overall.** The pooled slope statistic leans the
  hypothesis's way against the base (+0.038, p 0.19) and is flat for the
  total effect (p 0.23). Six of ten states lean the right way against
  the base. One of the six is the debt-service proxy, and it only "falls"
  because its wrong-signed coefficient grows more negative with lead:
  the statistic's direction does not check the sign. Forty design years cannot separate a
  slope of a few hundredths of a log odds ratio per SD from none.
- **Rising, as theory says:** Keen's debt gap (both offsets: about 0 at
  h = 1-2 to +0.16 at h = 5) and the fuel price gap (+0.14 to +0.21
  against the base). The fuel gap's placebo rises as much: the oil
  cycle is common to exporters, so its profile is the world's, not the
  country's.
- **Level across h, the right sign:** the credit gap (+0.09 to +0.16 per
  SD, z ≤ 1.5, placebo flat) and reserves (about −0.2 to −0.27, z −1.5 to
  −2.3 at every h, the clearest country signal on the list, placebo about
  half). Neither falls off with lead the way a trigger would. On these
  data the credit gap is not the long-lead indicator Drehmann & Juselius
  find, nor are reserves the short-lead one Kaminsky & Reinhart find. Both
  read more like a level.
- **Attribution against increment.** The technology gap is the largest
  total effect on the list (+0.75 per SD, z 5.8, flat in h, placebo
  null), and it is zero over the base. The base's country history already
  holds where conflict and coups happen; this is the mediation case of
  LIT-NEXT-STEPS 3b. The youth bulge is the same, smaller (+0.19, z 3.0,
  falling with h, then slightly negative over the base). Food import
  exposure has a total effect (+0.24 to +0.42, z 2-2.5) and none over the
  base.
- **Wrong-signed or noise:** the debt-service proxy is negative (more
  r·d, fewer crises), on 36 onsets. Elite overproduction's placebo beats
  it at short leads. Anocracy is null against both offsets.

The base offset is the 24-month forecast at t at every h, so at h ≥ 3
the "increment" is over a forecast that does not cover the year. A state
that predicts the base's own later forecast would show a rising increment
for that reason alone. The total-effect profile does not carry this
caveat, and there only the debt gap rises clearly.

## Which states to cut first

If the power audit (T0) says fewer than ten, cut in this order. The
order follows coverage and how good the proxy is, not the design-year
results, so the list stays theory-led:

1. **debt-service (r·d)**: a weak proxy (no amortization; `keen/r` only
   in calibrated years); 1,373 rows and 36 onsets at h = 1; real BIS
   debt-service ratios don't reach the design years.
2. **elite-overproduction**: 44 countries (WID top shares), 101 onsets;
   rich countries dominate it, so coverage confounds it with the base rate.
3. **fuel-price-gap**: there is no main-effect paper behind it (the
   literature is the interaction with a terms-of-trade fall, ranked trial
   3), and its signal is the world oil cycle, as the placebo shows.
   Better tested as an interaction.
4. **anocracy**: Vreeland's circularity, and the regime is mostly in the
   base already (political gauges); null here.
5. **keen-debt-gap**: about 18 countries a year before 2000 (JST and BIS
   countries), 77 onsets. Keep it if the trial adds 1870-2002 for the
   financial kinds, where JST gives it 150 years.

That leaves five, roughly one per domain: credit gap, reserves, food
import exposure, youth bulge, technology gap. Two of them (youth,
technology) are location variables whose increment the base absorbs.
They belong in the attribution test (ranked trial 4) more than in a
horizon profile against the base. If the list must shrink further, the
profile against the base rests on the credit gap, reserves and food
import exposure.
