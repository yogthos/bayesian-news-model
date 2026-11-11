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
5. **Cases are data, not code.** A crash enters as an episode in a
   library and a set of data mappings. What the model learns is the form
   crashes share, so a scenario with no precedent in its own type can
   still be found.
6. **Priors from data, coefficients that evolve.** Added 11 November
   2026 on the user's direction. No structure is frozen: recipes, network
   weights, lags, pass-through, buffer thresholds and slopes start from
   heavy priors set by the best available data and theory (the input-output
   tables, published lags, the literature's ranges), and are updated online
   as each period's data arrive, with discounting so they can drift when
   patterns change. The model is a living one. Two guards: the prior is
   strong enough that a few events cannot overturn it (fitting point
   coefficients to a handful of past shocks is what failed in P1), and every
   update uses only data known at the time.
7. **Every claim is scored forward.** 2003-2022 is development data from now
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

### Phase 1: the general buffer model (about 20 days) - expectations 3, 4, 5

Revised 10 November 2026 on the user's direction. The stock-flow model
is the key to unexpected events, because a buffer hides an imbalance
until it is gone. And the model must find new scenarios from what
crashes have in common, not from a case written in by hand. Reserves,
energy stocks, grain and investment booms are entries in a library and
rows of data, not separate code.

docs/INVARIANTS.md already made the argument and a first test: no rule
covers every era, the channel differs each time, and measures that are
indifferent to which dimension is stretched held up where channels
flipped (US held-out AUC 0.808 to 0.831, and 2022 caught). Those
measures were stretches against a series' own past. This phase gives
them the structure of a buffer and applies them to every stock.

**1.1 One schema for every stock.** A record per stock: who holds it and
where, its inflows and outflows, whose stock each flow leaves or enters,
its unit, how often and how late it is measured, and the share that is
usable. The engine's modules already declare levels and rates, so the
schema is read off the engine's graph. Adding a stock is adding a data
mapping.

**1.2 Invariants, computed for every stock.** All are times or ratios, so
they compare across domains:

- **cover:** usable stock over net drawdown;
- **rigidity:** the share of the outflow that cannot be cut (debt
  service, depreciation, subsistence);
- **restoration:** time to restore the inflow, over cover. Above one, the
  buffer runs out first;
- **carry:** what the stock loses by sitting (decay, cost of capital)
  against what it earns;
- **claim gap:** claims on a flow against the flow itself: debt or
  valuation against the income that must service it (Minsky's hedge,
  speculative and Ponzi positions are this ratio in three bands);
- **idle share:** installed but not earning, with the complement that
  binds;
- **holder:** how concentrated and how leveraged the holder is, which
  says where the loss lands;
- **market reading:** the cover a price implies (futures basis, spread).

The existing ones stay: displacement, breadth, resonance across scales,
stock times flow.

**1.3 The break as a regime switch.** In the engine every flow becomes
the lesser of what is wanted and what the stock allows. A unit is either
buffered or constrained; the phase transition is the switch. One unit's
outflow is another's inflow, so cascades follow from the accounts. No
chain is written by hand.

**1.4 An episode library.** Known crashes encoded in the schema: which
stock, the path of its cover, what failed to restore, who held it, what
followed. It starts from the crash-by-crash audit in docs/INVARIANTS.md
and adds currency, food, energy and investment-boom episodes across
countries. This is the training data.

**1.5 One pooled hazard.** The chance of a break as a function of the
invariants, shared across every type of stock, with partial pooling for
what is specific to a type. Reserves then teach about fuel stocks, and
telecom teaches about data centres.

**1.6 The scanner.** Each period, every stock in the world is ranked by
the pooled hazard. The output is a watch list of buffers, including
types with no crisis on record.

**1.7 Hidden buffers.** When the accounts stop closing (identity
residuals, growing gaps between overlapping measures), something
unmeasured is absorbing a flow. The scanner flags the residual itself.

**1.8 A wider field of view.** The scanner only sees stocks that are
measured. So the data widen: sector by instrument balance sheets,
industry capital stocks and inventories, commodity and reserve stocks,
company filings aggregated by industry, and fast US sector series. Two
schema features come from the boom case: asset lives and idle stock.

**Tests**

- **Leave one family out.** Train with a whole family of episodes removed
  (all investment booms, or all energy breaks, or all reserve crises) and
  ask whether its episodes are flagged. This is the test of finding the
  new from the invariants.
- **Invariants against channels.** The pooled hazard against type-specific
  inputs, era by era, as in docs/INVARIANTS.md.
- **False alarms.** Precision of the top of the list against stocks with
  shuffled histories.
- **Forward.** The energy stocks and the AI build-out are not entered by
  hand. The registered question is whether the scanner ranks them near
  the top unprompted. If it does not, that is a finding.

**Limits**

- Unmeasured stocks cannot be ranked; 1.7 helps only in part.
- Episodes per family are few. Pooling across families is what makes
  learning possible, and it is itself a hypothesis.
- A shock from outside (2020) is not predictable. The model says how long
  the buffers last once it arrives.

**What our record already says.** T1(b)'s pass after the warm-up fix was
carried by reserves (+0.160) and food import exposure (+0.088). Stock
levels as inputs were level (trial 45, T4, T9). The capacity test was
null on industry investment data.

**What the literature says** (searched 10 November 2026; several items
  from search summaries only):
  - **Stock relative to flow beats the level** for most buffers. The best
    reserve indicators are ratios (broad money to reserves, reserves to
    short-term debt); reserves were the most consistent predictor across
    80+ studies and predicted who was hit in 2008-09 (Frankel and
    Saravelos). The debt service ratio, a flow over a flow, has the best
    timing record found: 1-2 years ahead of banking crises (BIS).
  - **Critical thresholds flag risk while the price is calm.** Grain
    stocks-to-use thresholds do this (Bobenrieth, Wright and Zeng), as
    competitive storage theory predicts: price is convex in availability.
  - **The break comes before exhaustion.** In the first-generation
    currency crisis models the attack happens with reserves still
    positive, timed by the drain rate. Oil prices rise on precautionary
    demand before a physical shortfall (Kilian and Murphy). The futures
    basis is a daily market reading of the runway (Gorton, Hayashi and
    Rouwenhorst).
  - **Usable, not headline, stocks.** Sri Lanka had about $50m usable
    against $1.8bn official in May 2022; Lebanon's net reserves were
    about -$55bn against $38bn gross in 2019.
  - **Flow loss can break without exhaustion.** European gas peaked in
    August 2022 while storage was filling; the 2008 rice spike came from
    export bans with stocks adequate. The model needs both paths: a
    runway running down, and a flow cut that the buffer cannot cover in
    time.
  - **Not found:** a validated model that dates a price break from
    inventory depletion; out-of-sample hit rates for 1997 or 2007-08.
    Early-warning models were weak out of sample (Berg and Pattillo).
- **Design changes from this:** the runway uses usable stocks with
  uncertainty on the unusable share; the hazard depends on the runway
  and on doubt that the flow returns; market prices (futures basis,
  forward curves) enter as fast readings of the runway.

The same three forms recur across these literatures: the break is convex
in cover, it comes before exhaustion, and usable differs from headline.
No source tests one pooled function across types of stock. That is this
phase's hypothesis.

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

### Phase 5b: the price network (about 8 days) - expectations 1, 3

Added 11 November 2026 (bnm-56c.16). A price rise in an input (steel,
energy, grain) moves the prices of everything made from it, with lags,
and squeezes margins where it is not passed on. Today the product graph
(bnm.supply, EXIOBASE hybrid 2011) carries quantity shocks and a static
cost-push share; nothing moves prices over time. The energy pilot (P1)
found fixed network weights a small point gain over each sector's trend,
and pass-through fitted on past shocks worse.

**What the literature says** (searched 11 November 2026):
- The Leontief price model ranks exposure well: p = (I - A')^-1 v; with
  commodities exogenous, dp_n = (I - A_nn')^-1 A_xn' dp_x. Sector price
  responses match its sensitivities (Luo and Villar 2023); the most
  exposed sectors found before 2020 were the ones hit in 2021-22 (Weber
  et al. 2024). It is wrong on timing, margins and substitution.
- Each production stage adds a lag: cumulative pass-through after h steps
  is the sum of (A')^k for k up to h. Measured lags: crude to pump 1-2
  months, wholesale to retail beef 2 months, flour to bread 2-4 months.
  How often each sector changes prices matters more than the network
  links (Pasten, Schoenle and Weber).
- Pass-through is incomplete and asymmetric: about half of the 2018 US
  steel tariff reached domestic steel prices; imported input costs pass
  70-100% to producer prices; fuel prices rise fast and fall slowly.
- Large shocks with little substitution amplify beyond the linear model
  (Baqaee and Farhi: nearly triple for the 1970s oil shocks).
- Fitting pass-through to past shocks does not forecast: producer prices
  lead consumer prices in-sample but do not improve forecasts out of
  sample (Clark), as in our P1. The one scored out-of-sample result
  (Monti and Van Keirsbilck, ECB 2025) shows input-output structure
  helps headline inflation forecasts but not during the 2021-22 surge.
  No study scores sector-level price forecasts out of sample.

**Build:**
1. Structure: keep the 2011 hybrid recipes for physical quantities;
   take each year's coefficients from a yearly multi-regional table,
   using the table that was published at the forecast date (about t-2).
   Long-run WIOD (CC BY 4.0) for tests before 1995.
2. Prices: commodity inputs from the World Bank Pink Sheet and IMF
   monthly series, USGS for metals; targets are US producer prices
   (BLS, by industry and stage of processing) and Eurostat producer
   prices, mapped to the graph's products; BACI unit values on trade
   edges.
3. Model: a dynamic Bayesian price network, no static weights.
   - Priors: pass-through centred on the Leontief network weights from
     the latest published table, each stage's lag centred on the
     measured lags, each sector's frequency of price changes from
     published data, margin absorption from the literature's 50-100%
     range. Heavy enough that one shock cannot move them far.
   - Updating: each period's observed producer prices update every
     coefficient online (augur's discounted dynamic models), so
     pass-through, lags and margins drift when behaviour changes, for
     example the faster price-setting of 2022. The yearly tables update
     the network weights' prior means as each is published.
   - Non-linearity: the large-shock amplification enters as a prior on
     how pass-through grows with shock size, updated like the rest.
   - Baselines in the test: each sector's own trend, and the same model
     with its priors frozen, so the value of letting it evolve is
     measured.
4. Feed margins into the buffers: cost rises not passed on lower carry
   and raise the claim gap; they drain cash buffers.

**Test** (registered before running): the events 1973, 1979, 1990,
2008, 2014-16, the 2018 steel tariff and 2021-22; real-time inputs; each
sector's own trend and static network weights as baselines; cumulative
3, 6, 12 and 24-month sector price changes scored by error, rank
correlation across sectors and sign; rises and falls reported apart.
The realistic target is ranking which sectors rise, with a small gain
in size and the least in timing.

**Decided 11 November 2026:** EXIOBASE 3.10 for the yearly
coefficients; the project is non-commercial, which its licence (3.9 and
later) requires.

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
2. **Order.** Phase 0, then 1 (the general buffer model), then 2 and 3 in
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
