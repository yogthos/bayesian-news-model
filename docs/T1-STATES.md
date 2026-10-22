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

**The horizon statistics (specified before the run).** Skill is s ×
coef, where s is the state's expected sign. c12 and c45 are the pooled
coefficient's means at h = 1, 2 and at h = 4, 5.

- For each state, pool its kinds' rows into one coefficient at each h
  (the table's `pooled` rows).
- **(a) slow states:** the mean over the seven slow states of
  s × (c45 − c12). Theory says > 0: skill rises with lead.
- **(b) fast states:** the mean over the three fast states (debt service,
  food import exposure, reserves) of s × c12. Theory says > 0: the
  coefficient is right-signed at short leads.
- **The fast states' decline**, s × (c12 − c45), is reported for the
  record. It counts as consistent with theory only when (b) > 0 as well,
  so a wrong-signed coefficient that grows with lead counts against the
  fast hypothesis, never for it. (The first version of this test used a
  single statistic, slope × sign × speed, which counted exactly that case
  in favour. This replaces it.)
- Primary design: each state demeaned by year (see below); the raw
  states for the record.
- The null permutes country labels within each state's countries, so
  each country reads another's whole series, and recomputes all three.
  200 draws, seed 1. One-sided p = (1 + #{null ≥ observed}) / 201.
- For the record, the statistics on the placebo reads.
- The base-offset statistics are primary. The total-effect ones are
  reported beside them.

    jolt -A:tools -M -m bnm.credit-run
    jolt -A:tools -M -m bnm.horizon-profile --draws 200 --seed 1

Options: `--from` sets the first origin year (default 1960). `--to` sets
the last outcome year (default 2002), so at lead h the last origin is
to − h. `--states` takes a comma-separated subset of the names above
(default all ten); (a), (b) and their null are computed over that subset
only. `--out` sets the output file. For the registered trial, for
example:

    jolt -A:tools -M -m bnm.horizon-profile --from 2003 --to 2022 \
      --states <the registered subset> --draws 200 --seed 1 \
      --out data/runs/sequencing/horizon-t1-registered.tsv

## Design-year profile

Two designs, on the same rows and with the same null:

- **demeaned (primary):** each state less its year's cross-country mean,
  taken over the countries that have it that year, then scaled to unit
  spread. T1 asks about country-specific drivers. A world component
  (world real rates, world prices) belongs to the world-series tests,
  and permuting country labels cannot remove it: it shifted the raw
  design's (b) null to −0.18. The demeaning is causal, because the mean
  at t uses only values known at t. A test checks that it centres the
  permutation null on synthetic data with a world component;
- **raw (for the record):** the states as read.

### Demeaned (primary)

**Offset base.** Pooled coefficient (log odds ratio per SD) with its signed clustered z in brackets; the placebo's z last.

| State | h=1 | h=2 | h=3 | h=4 | h=5 | placebo z, h=1..5 |
|---|---|---|---|---|---|---|
| credit-gap | +0.171 (+1.47) | +0.139 (+1.22) | +0.086 (+1.02) | +0.111 (+1.26) | +0.107 (+1.27) | +0.25 -0.10 -0.59 -0.03 -1.03 |
| keen-debt-gap | -0.071 (-0.78) | -0.111 (-1.27) | -0.057 (-0.62) | -0.045 (-0.45) | -0.011 (-0.12) | +0.42 +0.19 +0.37 -0.19 -0.59 |
| debt-service | +0.102 (+0.79) | +0.086 (+0.69) | +0.080 (+0.67) | +0.008 (+0.07) | +0.017 (+0.20) | +1.02 +0.68 +1.06 +1.03 +0.70 |
| food-import-exposure | +0.571 (+2.17) | +0.426 (+1.49) | +0.399 (+2.16) | +0.250 (+1.58) | +0.116 (+1.92) | -0.76 -0.14 -1.23 -1.54 -1.18 |
| youth-bulge | +0.090 (+1.34) | +0.075 (+1.08) | +0.040 (+0.55) | +0.006 (+0.08) | -0.019 (-0.31) | +0.03 -0.18 -0.27 -0.13 +0.18 |
| elite-overproduction | -0.190 (-1.15) | -0.045 (-0.26) | -0.028 (-0.17) | +0.071 (+0.41) | +0.205 (+1.44) | +1.88 +3.20 +1.74 +0.83 -0.01 |
| technology-gap | +0.184 (+1.73) | +0.148 (+1.25) | +0.106 (+0.92) | +0.060 (+0.55) | -0.013 (-0.11) | -1.33 -0.89 -0.40 -0.20 -0.05 |
| fuel-price-gap | -0.106 (-1.76) | -0.121 (-1.96) | -0.121 (-1.70) | -0.111 (-1.56) | -0.068 (-0.94) | -1.33 -1.24 -1.28 -0.84 -1.66 |
| reserves | -0.255 (-2.22) | -0.186 (-1.73) | -0.190 (-1.68) | -0.252 (-1.99) | -0.265 (-1.98) | -1.02 -1.02 -1.30 -1.19 -0.65 |
| anocracy | -0.029 (-0.80) | -0.055 (-1.24) | -0.013 (-0.28) | -0.029 (-0.63) | -0.001 (-0.02) | -0.81 -0.82 -1.08 -1.52 -1.62 |

**Offset none (total effect).** Pooled coefficient (log odds ratio per SD) with its signed clustered z in brackets; the placebo's z last.

| State | h=1 | h=2 | h=3 | h=4 | h=5 | placebo z, h=1..5 |
|---|---|---|---|---|---|---|
| credit-gap | +0.177 (+1.45) | +0.135 (+1.15) | +0.076 (+0.88) | +0.098 (+1.09) | +0.093 (+1.04) | +0.26 -0.10 -0.64 -0.10 -1.11 |
| keen-debt-gap | -0.036 (-0.35) | -0.078 (-0.82) | -0.015 (-0.14) | -0.001 (-0.01) | +0.026 (+0.26) | +0.70 +0.54 +0.73 +0.08 -0.36 |
| debt-service | +0.077 (+0.50) | +0.058 (+0.38) | +0.057 (+0.39) | -0.032 (-0.25) | -0.016 (-0.14) | +0.86 +0.52 +0.94 +0.89 +0.51 |
| food-import-exposure | +0.369 (+2.44) | +0.301 (+1.96) | +0.282 (+2.69) | +0.232 (+1.54) | +0.115 (+1.44) | -1.20 -0.50 -1.53 -1.77 -1.34 |
| youth-bulge | +0.160 (+2.78) | +0.137 (+2.38) | +0.105 (+1.89) | +0.074 (+1.34) | +0.049 (+1.05) | -0.34 -0.43 -0.42 -0.25 +0.05 |
| elite-overproduction | -0.211 (-1.45) | -0.083 (-0.56) | -0.066 (-0.47) | +0.026 (+0.20) | +0.129 (+1.15) | +1.82 +3.15 +1.56 +0.56 -0.31 |
| technology-gap | +0.851 (+5.90) | +0.811 (+5.66) | +0.771 (+5.55) | +0.724 (+5.46) | +0.646 (+5.11) | -1.09 -0.33 +0.29 +0.51 +0.66 |
| fuel-price-gap | -0.095 (-1.89) | -0.106 (-1.95) | -0.106 (-1.78) | -0.101 (-1.68) | -0.071 (-1.16) | -1.18 -1.00 -1.19 -0.74 -1.60 |
| reserves | -0.225 (-2.12) | -0.173 (-1.72) | -0.185 (-1.69) | -0.248 (-2.02) | -0.271 (-2.05) | -0.90 -0.94 -1.17 -0.97 -0.38 |
| anocracy | -0.000 (-0.01) | -0.023 (-0.58) | +0.014 (+0.30) | +0.000 (+0.01) | +0.024 (+0.48) | +0.47 +0.38 +0.05 -0.61 -0.85 |

**Skill per state** (s × coef; c12, c45 the means at h = 1, 2 and 4, 5):

| State | Speed | s | s·c12, base | s·(c45 − c12), base | s·c12, none | s·(c45 − c12), none | placebo s·c12, base | placebo s·(c45 − c12), base |
|---|---|---|---|---|---|---|---|---|
| credit-gap | slow | + | +0.155 | -0.046 | +0.156 | -0.061 | +0.007 | -0.038 |
| keen-debt-gap | slow | + | -0.091 | +0.063 | -0.057 | +0.069 | +0.024 | -0.055 |
| youth-bulge | slow | + | +0.083 | -0.089 | +0.149 | -0.087 | -0.004 | +0.005 |
| elite-overproduction | slow | + | -0.118 | +0.255 | -0.147 | +0.225 | +0.353 | -0.275 |
| technology-gap | slow | + | +0.166 | -0.142 | +0.831 | -0.147 | -0.045 | +0.038 |
| fuel-price-gap | slow | + | -0.113 | +0.024 | -0.100 | +0.015 | -0.100 | +0.007 |
| anocracy | slow | + | -0.042 | +0.027 | -0.012 | +0.024 | -0.037 | -0.052 |
| debt-service | fast | + | +0.094 | -0.081 | +0.068 | -0.092 | +0.176 | -0.018 |
| food-import-exposure | fast | + | +0.498 | -0.315 | +0.335 | -0.162 | -0.060 | -0.145 |
| reserves | fast | − | +0.221 | +0.038 | +0.199 | +0.061 | +0.091 | -0.024 |

**The statistics** (200 permutations of country labels, seed 1). The
fast decline counts as consistent with theory only when (b) > 0 as well:

| Offset | Statistic | Observed | Placebo | Null mean (sd) | One-sided p |
|---|---|---|---|---|---|
| base (primary) | (a) slow | +0.0130 | -0.0529 | +0.0031 (0.0345) | 0.363 |
| base (primary) | (b) fast | +0.2709 | +0.0691 | +0.0016 (0.0969) | 0.020 |
| base (primary) | fast decline (record) | +0.1195 | +0.0625 | +0.0128 (0.0829) | 0.114, counted |
| none | (a) slow | +0.0055 | -0.0483 | +0.0051 (0.0289) | 0.468 |
| none | (b) fast | +0.2007 | +0.0556 | -0.0033 (0.0862) | 0.020 |
| none | fast decline (record) | +0.0644 | +0.0618 | +0.0062 (0.0688) | 0.194, counted |

### Raw (for the record)

**The statistics:**

| Offset | Statistic | Observed | Placebo | Null mean (sd) | One-sided p |
|---|---|---|---|---|---|
| base | (a) slow | +0.0264 | -0.0434 | +0.0176 (0.0341) | 0.403 |
| base | (b) fast | +0.0542 | -0.0993 | -0.1755 (0.0918) | 0.015 |
| base | fast decline (record) | +0.0656 | -0.0044 | -0.0140 (0.0770) | 0.164, counted |
| none | (a) slow | +0.0091 | -0.0492 | +0.0077 (0.0296) | 0.448 |
| none | (b) fast | +0.1746 | +0.0531 | -0.0013 (0.0757) | 0.005 |
| none | fast decline (record) | -0.0082 | -0.0555 | -0.0646 (0.0515) | 0.144, not counted |

Under the base offset the raw (b) null is centred at −0.18. Debt
service and food import exposure take negative coefficients on their
own in permutations, through their world components. The demeaned
design removes that, and its nulls centre within 0.015 of zero.

**Skill per state, raw:**

| State | Speed | s | s·c12, base | s·(c45 − c12), base | s·c12, none | s·(c45 − c12), none | placebo s·c12, base | placebo s·(c45 − c12), base |
|---|---|---|---|---|---|---|---|---|
| credit-gap | slow | + | +0.137 | -0.008 | +0.113 | -0.038 | -0.010 | -0.002 |
| keen-debt-gap | slow | + | +0.037 | +0.113 | -0.021 | +0.160 | +0.144 | -0.037 |
| youth-bulge | slow | + | -0.027 | -0.078 | +0.186 | -0.080 | -0.096 | +0.010 |
| elite-overproduction | slow | + | +0.056 | +0.181 | -0.172 | +0.085 | +0.485 | -0.298 |
| technology-gap | slow | + | +0.046 | -0.107 | +0.725 | -0.117 | -0.075 | +0.029 |
| fuel-price-gap | slow | + | +0.130 | +0.060 | +0.049 | +0.030 | +0.155 | +0.049 |
| anocracy | slow | + | -0.004 | +0.023 | -0.053 | +0.023 | +0.001 | -0.054 |
| debt-service | fast | + | -0.126 | -0.114 | +0.018 | -0.120 | -0.062 | -0.053 |
| food-import-exposure | fast | + | +0.067 | -0.100 | +0.280 | +0.103 | -0.329 | +0.114 |
| reserves | fast | − | +0.222 | +0.017 | +0.225 | +0.041 | +0.093 | -0.048 |

The raw per-h tables are in the tsv (design `raw`).

Full table, per kind as well: data/runs/sequencing/horizon-t1-design.tsv
(first column the design).

## Reading

These are design years, for development. The readings below shape the
list; they are not the test. They read the demeaned design.

- **Slow states: no rising profile.** (a) is +0.013 against the base
  (p 0.36; null mean +0.003) and +0.006 for the total effect (p 0.47).
  Only the Keen debt gap (+0.06, from a negative start), elite
  overproduction (+0.26, on 101 onsets, with a placebo as strong) and
  anocracy (+0.03) rise. The credit gap, the youth bulge and the
  technology gap all fall with lead: largest at h = 1, as triggers
  would be. The fuel price gap is wrong-signed once demeaned (about
  −0.11 at every h). Its raw positive profile was the world oil cycle.
- **Fast states: right-signed at short leads.** (b) is +0.271 against
  the base (p 0.020; null mean +0.002) and +0.201 for the total effect
  (p 0.020). Food import exposure carries it: +0.57 at h = 1, falling
  to +0.12 at h = 5, the trigger profile theory expects. Reserves add
  +0.22, flat in h. Once demeaned, debt service is right-signed, if
  small (+0.09, 36 onsets). The decline, counted because (b) > 0, is
  +0.12 against the base (p 0.11). It comes from food exposure and debt
  service; reserves don't fade.
- **Increment against attribution.** The technology gap's total effect
  is +0.85 per SD (z 5.9). Against the base it is +0.18 at h = 1, fading
  to zero by h = 5. The youth bulge is the same, smaller (+0.16 total,
  +0.09 against the base). Country history in the base holds most of
  where these states point.
- **Credit gap:** right-signed and level to falling (+0.17 at h = 1,
  +0.11 at h = 5, z ≤ 1.5). On these data it does not have the long lead
  of Drehmann & Juselius.

The base offset is the 24-month forecast at t at every h, so at h ≥ 3
the increment is over a forecast that does not cover the year. The
total-effect profile does not carry this caveat. Its reading is the
same.

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
   3), and its raw signal was the world oil cycle: demeaned by year it is
   wrong-signed.
   Better tested as an interaction.
4. **anocracy**: Vreeland's circularity, and the regime is mostly in the
   base already (political gauges); null here.
5. **keen-debt-gap**: about 18 countries a year before 2000 (JST and BIS
   countries), 77 onsets. Keep it if the trial adds 1870-2002 for the
   financial kinds, where JST gives it 150 years.

That leaves five, roughly one per domain: credit gap, reserves, food
import exposure, youth bulge, technology gap. Two of them (youth,
technology) are location variables; most of what they say, the base
already holds (demeaned, a small increment at h = 1 that fades).
They belong in the attribution test (ranked trial 4) more than in a
horizon profile against the base. If the list must shrink further, the
profile against the base rests on the credit gap, reserves and food
import exposure.
