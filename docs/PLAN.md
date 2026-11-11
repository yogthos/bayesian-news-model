# Plan: one simulation, tested forward

Draft for review, 10 November 2026. Nothing here is started. It follows a
stock-take of the code against six expectations and a literature search
covering Soviet and Russian, Chinese, other non-Western and Marxist work,
and the current forecasting benchmark.

## 1. Where the system stands

There are three systems, not one simulation, and they barely connect:

- **The forecast model** (the lattice): 13 event kinds over country,
  country group, region and world, at quarter, year and half-decade.
- **The weekly event model** (Hawkes): event counts in six channels, with
  provinces. This is what runs operationally.
- **The material side**: the A5 map, the supply-chain model and the
  coupled engine (stock-flow macro, allocation, capacity, World3).

| # | Expectation | Verdict | Main gap |
|---|---|---|---|
| 1 | A5 map, supply chains, map informs politics | Partly | Built, but no forecast reads it. Four tests of the link failed (CVP, LS, SS, FSc). |
| 2 | Countries split into subregion models that feed each other | No | The lattice stops at the country. Provinces exist only in the weekly model and nothing reads them. Neighbours are picked by event volume, not adjacency. |
| 3 | Nested models of economy, politics and so on, influencing each other | Partly | Nesting works inside each kind. Kinds don't feed each other (trials 42-45 level or worse). The engine reaches no forecast. |
| 4 | Dialectics plus stocks and flows, anticipating transitions | Partly | Strain and the turn are live inputs with two registered gains (trials 39, 40). The principal contradiction was falsified (T3). Stocks say where, not when. |
| 5 | Daily to century scales, small feeding large | Partly | Only quarter, year and half-decade in the lattice. |
| 6 | Real data, 80/20 split | Partly | Real data. The split is by time (design to 2000, test 2003-2022, about 59/41 by scored rows), not 80/20. The test years have been scored about 60 times. |

Tested and failed: links between kinds, cells as the unit, supply shocks
and port traffic as triggers, fast price signals, stock-flow states as
inputs, early-warning statistics. Never built or never tried:
sub-national lattice levels, geographic adjacency, the engine's output as
an input, the cell, chokepoint, stage and essentiality tables as
candidates, events as threshold crossings of stocks.

## 2. What the literature says

The four searches agree on more than they disagree. Two of them (Chinese,
other non-Western) rest mostly on abstracts and summaries, not full texts.

**Supported by evidence**

1. **Skill is "where", not "when".** In the ViEWS 2024/25 challenge, scored
   on the true future, "the last 12 months of the same country" beat every
   entrant on the main score at country and grid level. Sub-district
   models in Colombia and Indonesia get their skill from which places are
   at risk, not when. Quarter-level onset timing is not shown anywhere.
2. **Windows work.** Financial crises are predictable over two-to-three-year
   windows: 45% within three years inside the business credit "red zone"
   (Greenwood et al.). That is a threshold on stocks giving a window, not
   a date.
3. **Growth of a stock beats its level.** Korotayev's group ties instability
   to five-year growth of urban youth plus triggers (retrospective
   correlation 0.59). Credit growth and asset prices reach AUC 0.7-0.8 a
   year ahead.
4. **Slopes differ by region and by sign.** Commodity price shocks have no
   pooled effect on new conflict (Bazzi-Blattman), but coffee busts and oil
   booms both raise violence inside Colombia by opposite channels
   (Dube-Vargas). Korotayev's group finds no predictor that works across
   continents. A single pooled slope averages these to zero. Our lattice
   pools one coefficient set across every country of a level.
5. **Pressures multiply.** Turchin's stress indicator is a product of mass
   mobilisation, elite competition and state fiscal distress. One forward
   forecast (2010 for the 2020s) held, scored by its author, for two
   countries.
6. **Shocks propagate through supply networks, conditionally.** After the
   2011 earthquake, losses reached firms two and three links away
   (Carvalho et al.). A dynamic input-output model forecast UK GDP for Q2
   2020 at -21.5% against -22.1% actual (Pichler et al.), given a known
   shock. No study shows supply features predicting crisis onset.
7. **News text is the one fast signal with a record.** AUC 0.75 on hard
   onsets, against about 0.5 for commodity prices (Mueller-Rauh).
8. **Only forward forecasts count.** The Goldstone instability model's 85%
   accuracy peaked in its own validation window. Chen Xikang's grain
   forecast has run every April since 1980 as one dated forecast a year
   (accuracy self-reported). Random 80/20 splits leak: the same
   financial-crisis model scores AUC 0.91 on random folds and 0.77 once
   folds are grouped by year and crisis (Bluwstein et al.).
9. **Stocks belong inside the flow coefficients.** Chen's
   input-occupancy-output analysis puts land, capital and labour stocks
   into the production structure. Planning mathematics offers shadow
   prices as scarcity signals and regions linked through transport
   (Kantorovich, the Novosibirsk inter-regional model). These were
   planning tools with no forecast record.

**No forecasting record; not to be built on**

Kondratiev phases, technological orders, blow-up dates, log-periodic crash
timing, catastrophe theory, critical slowing down (matches our failed T6),
Goodwin cycle phase, the profit rate as a timer (US only, in-sample),
metasynthesis halls, parallel systems, national power indices, the large
global models (FUGI, GLOBUS, Bariloche, system-dynamics national models).
No computable model of the principal contradiction was found in the
Chinese literature; our own test falsified argmax against the mean.

**Named mechanism, no test yet**

Niu Wenyuan's social combustion: contradictions as fuel, opinion as
accelerant, an incident as ignition. It is the closest published
statement of slow accumulation turning into a break. Treat it as a
hypothesis: a hazard where a stock sets the baseline and accelerant and
trigger terms multiply it.

## 3. Principles for the plan

1. **One place tree and one time tree.** Every system hangs on the same
   places (world, region, country group, country, province; cells as the
   storage and measurement layer under provinces) and the same times.
2. **Wire everything, let evidence set the weights.** The simulation has
   the links the framing calls for. A link that carries nothing must cost
   nothing. Today it does cost: a noise group loses about 0.002 Brier.
   Fixing that comes first.
3. **Buffers explain when.** While a stock can cover a flow imbalance,
   nothing changes on the surface, which is why "the last 12 months"
   wins in general. The break comes when the buffer runs out and the flow
   cannot be restored. So the quantity to forecast is the runway: a
   stock over its net drawdown, as a date with uncertainty, plus the
   chance the flow is restored. Our tests so far used stock levels as
   plain inputs; the runway was never tested. Where no buffer is
   measured, onset forecasts stay hazards over one-to-five-year windows.
4. **The dialectic is in the form of the model.** Contradictions are
   multiplied pressures and strain between levels; quantity into quality
   is a red zone on stocks and their growth; uneven development is slopes
   that differ by place and structure. The principal contradiction stays a
   description, not a forecasting device.
5. **Every claim is scored forward.** 2003-2022 is development data from now
   on. New results count when a dated, frozen forecast meets its outcome.

## 4. Phases

Estimates are agent working days, one or two agents at a time.

### Phase 0: foundations (about 5 days)

- **0.1 Forward ledger.** Freeze the base (t48). Each quarter, write dated
  forecasts for all kinds and windows to `registered/`, hash them, and
  score them when outcomes are published. Never refit between issue and
  outcome. First issue: 2026 Q4.
- **0.2 Development protocol.** Rolling-origin evaluation with folds grouped
  by year and by episode, and a gap at least as long as the window. Report
  the drop from naive folds. Report a split by time with the last 20% of
  origins held out as a standard diagnostic, labelled as development, not
  validation.
- **0.3 Benchmarks.** Add "last 12 months of the same place" and "country
  probability times the place's historical share" to every score, beside
  climatology and persistence.
- **0.4 Links that cost nothing.** Replace the collapse onto one model
  (weights near 1) with shrinkage priors or stacking, so an empty group is
  ignored. Accept when a noise group's Brier cost is within 0.0003 of zero
  under watch-list v2. Needs hierarchical slopes in augur (new release).
- **0.5 Housekeeping** (running): registered 2027 files in git, the
  `world-run` default, stale docs, banking coverage 2020-25.

### Phase 1: buffers and runways (about 12 days) - expectations 4, 5

Added 10 November 2026 on the user's direction: the stock-flow model is
the key to unexpected events, because a buffer hides the imbalance until
it is gone.

- **A buffer registry.** Each buffer is a stock with its inflow and
  outflow, who holds it, how often it is measured, and what breaks when it
  is exhausted. First set:
  - foreign reserves against external financing need (import cover,
    reserves over short-term debt): currency crises, defaults;
  - oil and gas stocks in days of cover, strategic and commercial: energy
    price breaks;
  - grain stocks-to-use: food price shocks;
  - debt service against income, bank liquidity, fiscal space: banking
    and fiscal crises.
- **The runway.** Stock over net drawdown, as a posterior for the
  exhaustion date. Flows are read at the week and month, trends at the
  year and decade, so the time scales meet here.
- **Restoration.** Whether the flow can be restored: other suppliers and
  routes, substitution, and the lead time of new capacity, from the
  supply model. The inventory and outage-path code is the start.
- **Anticipation.** Breaks come before zero: holders run, prices rise as
  stocks fall. The hazard is a function of the runway with a learned
  threshold, not a step at exhaustion.
- **Cascade.** One exhausted buffer becomes a drawdown on the next:
  energy to the trade balance to reserves to default to unrest. The
  engine runs the chain.
- **What our record already says.** T1(b)'s pass after the warm-up fix
  was carried by reserves (+0.160) and food import exposure (+0.088).
  Stock levels as inputs were level (trial 45, T4, T9).
- **Test.** Hindcasts with parameters fixed beforehand: the 1997 Asian
  crisis (reserves), the 2007-08 food price spike (stocks-to-use), Sri
  Lanka 2022 (reserves to default to unrest), European gas 2021-22
  (storage). Scored on hard onsets, where persistence fails, against the
  levels-only model. Forward: the runway of current energy stocks is the
  first entry in the ledger.
- **Literature to verify before registration:** competitive storage
  (price spikes at stockouts), first-generation currency crisis models
  (the attack is timed by reserve depletion), reserve adequacy as a
  leading indicator, oil inventories and price.

### Phase 2: sub-national level (about 12 days) - expectation 2

- Province nodes in the lattice for protest and violence surges and
  conflict, from GDELT and UCDP GED (open).
- Geographic adjacency from `bnm.cells/neighbours`, as a spatial prior
  (BYM2/ICAR) on province effects. Replaces neighbours by event volume.
- Top-down allocation of the country forecast by long-run share, then
  reconciliation so provinces sum to the country.
- The weekly event model's province intensity becomes the province node's
  fast input, replacing its side view.
- China as the pilot for sub-national economy: open 31-province
  input-output tables (2012, 2015, 2017) and provincial series.
- **Test:** against "country probability times historical share" and
  against the weekly model's own ranking (AUC 0.86). Expect the gain to be
  in where; say so in the registration.

### Phase 3: uneven slopes, multiplied pressures, red zones (about 10 days) - expectations 3, 4

- Slopes partially pooled by region and by structure type. Structure types
  come from the map: cell profiles and export mix (labour-intensive
  against capital-intensive commodities). This is how the map informs
  politics in a testable way.
- Product terms for mass, elite and state pressure, tested against the
  additive form.
- Growth and acceleration of stocks beside levels: five-year urban youth
  growth (UN population data), credit and asset-price growth, debt
  service.
- Red zones: joint thresholds on stock growth that define a multi-year
  window of raised hazard, for banking first, then instability. This is
  STOCK-FLOW step 4 (events as threshold crossings) in the form the
  evidence supports.
- Social combustion as a registered hypothesis: baseline from the stock,
  multiplied by an accelerant (news attention) and a trigger.
- **Test:** each against the pooled, additive, levels-only base on
  development folds, then on the forward ledger.

### Phase 4: one time tree (about 12 days) - expectation 5

- Add month and decade nodes. The decade node is driven by cohort
  demography, which has a real 20-year lead.
- The week level is the event model's intensity, for count kinds only.
- Reconcile forecasts across week, month, quarter, year, half-decade and
  decade so they agree (annual hazard equals one minus the product of
  quarterly survival).
- A news-topic fast layer from GDELT themes, tested on hard onsets.
- The century level is era base rates from the long catalogues (1800 on),
  as a prior only. No cycle-phase predictors.
- **Test:** reconciled against unreconciled at each level; news layer on
  hard-onset recall under watch-list v2.

### Phase 5: coupling as conditional impact (about 15 days) - expectations 1, 3

- The supply model as a shock engine: given a strait closure, export ban
  or disaster, propagate through the product graph and report output loss
  by country and province.
- **Hindcast three shocks with parameters fixed beforehand:** the 2011
  Tohoku earthquake, the 2021 Suez blockage, the 2022 Black Sea grain and
  gas cut. This is the material side's first honest validation.
- Shadow prices (bottleneck duals) from the supply model as scarcity
  inputs to food price shock hazards and severity.
- Stocks inside flow coefficients (Chen's occupancy), replacing the fixed
  2011 recipes where stock data exist.
- Coupling between kinds moves to severity and duration given onset, and
  to sign-specific interactions from phase 3. Main effects on onset stay
  off.
- Close the loop in the engine: event hazards set shock scenarios, the
  supply model propagates them, the stock-flow core takes the output
  loss, and stress states return as inputs to severity. Run the engine's
  event test (bnm-4hv.8).
- **Test:** conditional output forecasts for the three shocks against
  persistence; severity and duration scored by CRPS given onset.

### Phase 6: operations (about 5 days)

- One run produces all of it: the lattice with provinces, the event model
  as its week level, the engine as its scenario layer.
- Quarterly issue of the forward ledger, with a short public record of
  hits and misses.

## 5. What stops

- Chasing quarter-level onset timing with price, currency or port signals.
- Cells as the inference unit (they stay storage).
- Links between kinds as main effects on onset.
- The principal contradiction as a forecasting input.
- Early-warning statistics, long-cycle phase, blow-up dates.
- The stock-flow ensemble (parked, bnm-tvk).
- Claims of a result from 2003-2022 alone.

## 6. Decisions needed before work starts

1. **The split.** Keep the split by time and add the forward ledger
   (recommended), with the last-20%-by-time diagnostic reported, rather
   than a random 80/20.
2. **Order.** Phase 0, then 1 (buffers and runways), then 2 and 3 in
   parallel, then 4, then 5 (recommended). Phase 5's shock hindcasts share
   machinery with phase 1 and can start alongside it.
3. **Scope of expectation 3.** Accept that links between kinds are built
   but carry weight only where tested (recommended), rather than forcing
   them on.
4. **Data needing an account:** ACLED (events), CEADs (China provincial
   tables; Zenodo copies are open). UN population data, UCDP GED, GDELT
   themes and BIS debt service are open.

## 7. Sources

Benchmark
- ViEWS leaderboard: https://viewsforecasting.org/leaderboard/
- Bazzi et al., sub-national violence: https://nber.org/papers/w25980
- Mueller and Rauh, news topics: https://academic.oup.com/jeea/article/20/6/2440/6574413
- Bowlsby et al., instability models decay: https://www.hks.harvard.edu/publications/future-moving-target-predicting-political-instability
- Greenwood et al., predictable financial crises: https://www.nber.org/system/files/working_papers/w27396/w27396.pdf
- Bluwstein et al., crisis prediction and fold leakage: https://www.ecb.europa.eu/pub/pdf/scpwps/ecb.wp2614~6974517ac3.en.pdf
- Bazzi and Blattman, commodity shocks: https://www.aeaweb.org/articles?id=10.1257/mac.6.4.1
- Dube and Vargas, Colombia: https://econpapers.repec.org/RePEc:oup:restud:v:80:y:2013:i:4:p:1384-1421
- Carvalho et al., earthquake supply chains: https://www.rieti.go.jp/en/publications/summary/14060011.html
- Pichler et al., dynamic input-output forecast: https://www.inet.ox.ac.uk/publications/forecasting-the-propagation-of-pandemic-shocks-with-a-dynamic-input-output-model
- Athanasopoulos et al., temporal hierarchies: https://eprints.lancs.ac.uk/id/eprint/84905
- Corani et al., reconciliation for counts: https://arxiv.org/abs/1906.03105

Soviet and Russian
- Turchin and Korotayev, 2020 retrospective: https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0237458
- Korotayev, Malkov, Grinin, "A Trap at the Escape from the Trap?": https://sociostudies.org/almanac/articles/a_trap_at_the_escape_from_the_trap-_some_demographic_structural_factors_of_political_instability_in_
- Korotayev et al., benchmark of instability indices: https://publications.hse.ru/en/view/575371774
- Korotayev and Tsirel, spectral analysis of world GDP: https://escholarship.org/uc/item/9jv108xp
- Suslov et al., the Novosibirsk inter-regional model: https://www.iioa.org/conferences/25th/papers/files/2874_20170508031_Suslov2017.pdf

Chinese
- Chen Xikang's method and grain forecasts: https://news.sciencenet.cn/htmlnews/2021/5/457325.shtm
- CAS forecasting centre record: https://news.ucas.ac.cn/cmjj/d109cdc3b6b84d41a959825ca1cb46f7.htm
- Zheng et al., open provincial input-output tables: https://pmc.ncbi.nlm.nih.gov/articles/PMC8458474
- Dan and Gao on social combustion: https://qks.sufe.edu.cn/J/ArticleQuery/A160824002154/CN
- Greenhalgh on the population model: https://dx.doi.org/10.1017%2FS0305741005000184

Other
- Guttal et al., no critical slowing down before crashes: https://pmc.ncbi.nlm.nih.gov/articles/PMC4711996
- Tapia, profits before recessions: https://ideas.repec.org/a/sae/reorpe/v44y2012i4p484-493.html
- Meadows et al., Groping in the Dark: https://pure.iiasa.ac.at/1829
