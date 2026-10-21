# Which kinds lead which: the sequencing before any training

`bnm.sequencing` on onsets before 2003 only, by year, every ordered pair of
the thirteen kinds (bnm.kinds): the rate of j's onsets at each lag around
k's against j's calm rate (lead-lag), and whether j's past predicts k given
k's own past (a logit of k's onsets on its own one- and five-year
excursion kernels with and without j's; each kernel an excursion above
the country's own rate so far, so it reads only the past). Full tables:
data/runs/sequencing/lead-lag.tsv and tests.tsv; the graph:
data/runs/sequencing/graph.edn.

The rule for an edge was fixed before any table was read: the lagged
test's likelihood ratio 13.8 or more (p < 0.001, two degrees of freedom)
with j's coefficients summing positive (**data**), or an edge the
literature names with j above its calm rate in the three years before k
(**theory**); an edge from a higher tier to a lower one needs the test.
On a synthetic world the test finds a true link at LR 160-240 and gives
0.1-2.3 for an unrelated kind (test/bnm/sequencing_test.clj); with 156
pairs at p < 0.001 about 0.16 false edges are expected.

## The candidate graph: 21 edges

| edge | tiers | source | LR | rate before k (3 years) |
|---|---|---|---|---|
| growth_collapse → conflict_onset | wuli → renli | theory | 1.2 | 1.07 |
| growth_collapse → coup | wuli → renli | theory | 2.4 | 1.96 |
| growth_collapse → protest_surge | wuli → renli | theory | 3.7 | 2.33 |
| growth_collapse → sovereign_default | wuli → shili | data | 21.0 | 2.10 |
| inflation_crisis → coup | wuli → renli | theory | 10.5 | 3.75 |
| banking_crisis → currency_crisis | shili → shili | data | 26.3 | 3.08 |
| banking_crisis → growth_collapse | shili → wuli | data | 22.4 | 2.11 |
| banking_crisis → inflation_crisis | shili → wuli | data | 35.4 | 4.69 |
| banking_crisis → sovereign_default | shili → shili | data | 24.9 | 2.29 |
| currency_crisis → banking_crisis | shili → shili | theory | 0.0 | 2.00 |
| currency_crisis → inflation_crisis | shili → wuli | data | 19.5 | 5.37 |
| sovereign_default → banking_crisis | shili → shili | theory | 1.1 | 1.55 |
| sovereign_default → fiscal_consolidation | shili → shili | theory | 4.5 | 2.30 |
| sovereign_default → inflation_crisis | shili → wuli | data | 19.6 | 5.62 |
| conflict_onset → coup | renli → renli | data | 15.7 | 3.43 |
| conflict_onset → violence_surge | renli → renli | data | 20.1 | 3.07 |
| conflict_onset → war_onset | renli → renli | data | 17.9 | 12.71 |
| coup → conflict_onset | renli → renli | theory | 7.3 | 3.46 |
| protest_surge → coup | renli → renli | theory | 6.8 | 1.75 |
| violence_surge → conflict_onset | renli → renli | theory | 4.3 | 2.28 |
| violence_surge → war_onset | renli → renli | data | 15.4 | 3.01 |

## What it says

- **Finance moves first, into the material base and the state.** Banking
  crises lead currency crises, sovereign defaults, inflation crises and
  growth collapses (LR 22-35, rates 2-5 times calm); currency and
  sovereign crises lead inflation crises; growth collapses lead
  sovereign defaults. The crisis mutations Laeven & Valencia and Candelon
  et al. describe, in our catalogues.
- **Conflict is its own engine.** Conflict onsets lead violence surges,
  wars and coups; violence surges lead wars.
- **The economy reaches the actors weakly at the level of onsets.** No
  economic or financial kind's past onsets predict a political kind's
  given its own past at p < 0.001; the economic-to-political edges are
  theory's (growth collapses to coups, protest and conflict; inflation
  crises to coups, LR 10.5), admitted because the economic onsets stand
  above their calm rate before them (1.1-3.8 times). The strongest near
  misses: banking crises to coups (LR 12.1), inflation crises to
  conflicts (11.0). This is what the literature leads one to expect: the
  channel runs through exposure and institutions, and through stress
  that does not always become a dated crisis. The relation layer
  therefore reads parent *states* (each parent's linear predictor, its
  stress whether or not it crosses into an onset), moderated by exposure
  and executive constraints, not only parents' onsets.
- **Austerity and food: not supported here.** Spending-led consolidations
  stand below their calm rate before protest and violence surges (0.66,
  0.86) and the lagged tests are null; food price shocks cannot be judged
  before 2003 (FAOSTAT from 2000). Neither enters the graph; food prices
  reach the political nodes through their gauges (world food prices
  through import exposure), and consolidations remain a kind of their
  own, forecast and available as a parent if a later sequencing on more
  data supports it.
