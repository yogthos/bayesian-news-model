# Literature review: what to do after trial 45

Written 22 October 2026, before any further trial. It asks whether the two
proposed next steps are sound, why the early-warning statistics took the
wrong sign, whether the test we use can find slow drivers at all, and what
else is worth trying. It extends docs/RESEARCH.md ("Onset hazards: what the
literature says to try next") and does not repeat it.

Where a claim rests on one paper, or on in-sample work, it says so. Where I
could not confirm a detail (a DOI, a journal version), the reference gives
the working-paper link instead.

## The short answer

- **Step 2 (score the half-decade node) is sound in principle and weak in
  practice.** The literature puts slow financial stocks at 3-5 year
  horizons and fast triggers at 1-2 years. But 2003-2022 holds four
  half-decades, and 1960-2002 eight. A half-decade score on those windows
  will be "level" almost whatever the truth is. It needs longer samples
  (1870 for the financial kinds), several eras scored in rotation, and
  pre-registered effect sizes.
- **Step 1 (states as gauges of the base's contradictions) has indirect
  support.** The best-documented slow-variable effects are conjunctions
  and conditional effects, not main effects: credit and asset prices
  together, food prices in some regimes, drought where farming and
  exclusion meet. There is no empirical literature on selecting one
  dominant gauge (argmax). Interactions also cost far more power than main
  effects. Do it with a few theory-named pairs and signed priors, and nest
  the argmax inside a smooth combination so the data can reject it.
- **Our test is underpowered for the effects the literature reports.** A
  score test with year clusters, Bonferroni over 4,530 candidates and
  about 40 design years can only detect odds ratios near 2 per standard
  deviation, conditional on a base that already holds the country's
  history. Published effects of slow variables, conditional on history,
  are much smaller. "Level" in trial 45 means "not shown", not "absent".
- **The wrong-signed early-warning indicators match the literature.**
  Socio-economic crashes mostly do not show critical slowing down. Low
  volatility comes before banking crises ("stability is destabilizing").
  And high recent variance marks countries just after a crisis, when a new
  onset is less likely by definition.
- **Forecast gain and explanation are different questions.** The project's
  purpose is to find drivers. An incremental test with the base as an
  offset measures only the direct effect beyond the base. A driver whose
  effect runs through what the base already holds (country history, level
  gauges) will always score level there. That needs a separate,
  attribution-style test.

## 1. Are the two steps sound?

### 1a. At what horizon do slow variables predict?

**Financial crises: the evidence is strongest here, at 2-5 years.**

- Schularick & Taylor (2012, AER) predict crises from 1870-2008 with five
  annual lags of real credit growth. The lags carry the signal jointly:
  credit is a multi-year build-up, not a one-year trigger. Their AUROC is
  about 0.7, in-sample with country fixed effects.
- Jordà, Schularick & Taylor (2013, JMCB, "When credit bites back"): a
  bigger credit boom makes the recession after it deeper and longer. That
  is severity, not onset timing. This bears directly on us: slow stocks
  may predict how bad a crisis is better than when it starts.
- Greenwood, Hanson, Shleifer & Sørensen (2022, JF): the joint condition
  (3-year credit growth and asset-price growth both in their top tercile,
  the "R-zone") raises the three-year crisis probability from about 7%
  to about 40%. The predictability is mainly about the conjunction, and it
  holds out of sample in their split.
- Mian, Sufi & Verner (2017, QJE): a rise in household debt to GDP over 3
  years predicts lower GDP growth 3-5 years later, across 30 countries.
  The outcome is growth, which suits our growth collapses.
- Drehmann & Juselius (2014, IJF; BIS WP 421): the credit-to-GDP gap is the
  best indicator at long horizons (about 2-5 years before a crisis). The
  debt-service ratio dominates at 1-2 years. This is the cleanest
  published statement of "slow stock long, flow-like pressure short".
- Richter, Schularick & Wachtel (2021, JEEA, "When to lean against the
  wind"): credit booms that come with house-price booms and rising
  leverage end in crises. Booms without them mostly do not. Again a
  conjunction.
- Schularick, ter Steege & Ward (2021, AER: Insights): monetary tightening
  during a credit boom raises crisis risk. This is a measured
  stock × trigger interaction.
- Bluwstein et al. (2023, JIE): with Shapley values on tree models, credit
  growth and the yield-curve slope (domestic and global) dominate, and
  their effects are nonlinear and interacting.

Caveat: these samples are mostly the 17-18 advanced economies of the JST
macrohistory database, with roughly 90 crises. Emerging-market results
(Kaminsky & Reinhart 1999, AER; Frankel & Saravelos 2012, JIE) point to
reserves and the real exchange rate instead. Credit matters less there,
and the horizons are shorter (about 1-2 years).

**Conflict and instability: slow variables give location, not timing.**

- Fearon & Laitin (2003, APSR) is the canonical structural model: income,
  population, terrain, new states, political instability. They say
  plainly that these factors favour insurgency without timing it.
- Goldstone et al. (2010, AJPS, PITF) forecast onsets of instability two
  years ahead with four variables: regime type (partial democracy with
  factionalism), infant mortality, armed conflict in neighbours, and
  state-led discrimination. They report about 80% accuracy. The design is
  case-control with matched controls, so that figure is not a
  population-level out-of-sample rate. The regime-type finding is itself
  an interaction (partial democracy × factionalism).
- Ward, Greenhill & Bakke (2010, JPR, "The perils of policy by p-value"):
  the significant structural variables of the Fearon-Laitin and
  Collier-Hoeffler models add almost nothing to out-of-sample prediction.
  This is the closest published precedent to our trial 45 result.
- Hegre et al. (2019, JPR; 2021, JPR) on ViEWS: conflict history and its
  spatial and temporal lags carry most of the skill at 1-36 months.
  Structural themes add little on top of history, and the ensemble weights
  them low. I could not find a clean published result that structural
  themes gain weight at longer horizons, so treat that as a hypothesis.
- Mueller & Rauh (2018, APSR; 2022, JEEA "The hard problem of prediction
  for conflict prevention") and Bazzi et al. (2022, REStat): structure and
  history find where conflict will be. Onsets in countries without recent
  conflict (the "hard problem") are where any gain must come from. News
  text helps there, slow structure little.
- Cederman & Weidmann (2017, Science) argue that onset timing has a low
  ceiling: contingency and strategic interaction limit it.
- Chadefaux (2014, JPR): news war-talk rises months before wars. A fast
  signal.

**Coups.**

- Belkin & Schofer (2003, JCR) separate structural coup risk (weak civil
  society, legitimacy, past coups) from triggers (economic shocks,
  protest). They argue that triggers matter only where structural risk is
  high. This is the cleanest statement of the conditioning design for our
  kinds.
- Powell (2012, JCR) and Gassebner, Gutmann & Voigt (2016, Public Choice;
  extreme-bounds analysis): the robust coup predictors are coup history,
  low income, recent growth and regime type. Few structural stocks
  survive.
- Beger, Dorff & Ward (2016, IJF): irregular leadership changes, from an
  ensemble of split-population duration models. The split-population form
  separates "at risk at all" (structural) from "when" (hazard), which is a
  formal version of the fuel-and-spark idea.

**Structural-demographic theory (Turchin; Goldstone 1991).**

- Its tests are historical reconstructions: Turchin & Nefedov 2009,
  Secular Cycles; Turchin 2012 (JPR) on the United States 1780-2010;
  Turchin 2016, Ages of Discord. Its claims concern decadal to
  half-century cycles in instability frequency.
- I found no out-of-sample, country-year test of political-stress
  indicators against a strong base. The theory's own resolution (decades)
  is coarser than our half-decade node. A year-level null is what it
  predicts, not a refutation of it.
- Youth bulges have the best panel support of its components. Urdal
  (2006, ISQ) finds a main effect. Conditioning on unemployment or
  education is plausible but mixed across specifications.

**Summary for step 2.** The literature supports slow stocks at 2-5 years
for financial crises (strongly) and at decadal scales for instability
(weakly, from case work). That points to the half-decade node. The
catch is the number of independent periods, which section 3 covers.

### 1b. Conditioning: slow variables that modulate triggers

There is more support for this than for main effects, in every domain we
cover:

| Domain | Slow condition | Trigger | Source |
|---|---|---|---|
| banking | credit boom | asset-price boom | Greenwood et al. 2022; Richter et al. 2021 |
| banking | credit boom | monetary tightening | Schularick, ter Steege & Ward 2021 |
| banking (open economies) | openness, exposure | global credit, VIX | Cesa-Bianchi, Eguren Martin & Thwaites 2019 |
| urban unrest | regime type (hybrid, democratic) | world food price | Hendrix & Haggard 2015 (JPR) |
| civil conflict | farming dependence, political exclusion | growing-season drought | von Uexkull et al. 2016 (PNAS) |
| coups | structural coup risk | economic shock, protest | Belkin & Schofer 2003 |
| instability | partial democracy | factionalism | Goldstone et al. 2010 |

Three cautions.

1. **Power.** An interaction half the size of a main effect needs about 16
   times the sample to estimate with the same precision (Gelman 2018). We
   are already short of power for main effects (section 3). Interactions
   are viable only with informative priors: a theory-given sign and a
   plausible magnitude, pooled across related kinds and places.
2. **The principal contradiction (argmax) has no empirical precedent.**
   Selecting the dominant gauge throws away the others and adds a
   discrete, noisy choice. Composite and "breadth" indicators (the number
   of indicators signalling, as in Kaminsky, Lizondo & Reinhart 1998)
   usually do at least as well as any single indicator. The argmax should
   be one end of a family the data can choose from, for example a softmax
   over the gauges' contributions with a temperature. Infinite
   temperature is the mean, zero temperature is the argmax. Then "one
   contradiction is principal" becomes a testable claim, not an
   assumption.
3. **"Gauges of the base's contradictions" should be stated as a
   varying-coefficient model** (Hastie & Tibshirani 1993). The state
   changes the slope on a named trigger, or the calibration slope of the
   base's own log-odds. The state does not enter as a level. The second
   form is cheap and powerful: one parameter per state, testable on all
   kinds. It asks whether the base is over- or under-confident when the
   stock is stretched.

**Verdict on step 1:** sound as a direction, if it is restricted to a
handful of pre-named stock × trigger pairs with signed priors, and if the
argmax is nested rather than imposed. Run over all states and all
triggers, it would repeat trial 45's multiple-testing problem with
roughly a sixteenth of the power.

## 2. Why critical-slowing-down indicators carry the wrong sign

The theory (Scheffer et al. 2009, Nature; Dakos et al. 2008, PNAS) applies
to a system slowly approaching a fold bifurcation, with small noise, and
observed at a resolution fine enough relative to its recovery time. Our
panels meet none of these conditions well. Specific reasons, roughly in
order of likely importance:

1. **Post-crisis composition (probably dominant).** High variance and AR1
   of growth or inflation over the past window mostly mark a country that
   has just had a crisis. Onset definitions exclude years already in a
   crisis, and risk is lower just after one (the refractory effect our
   resolution mechanism already models). So the indicator is negatively
   correlated with onset by construction. Check: recompute the indicators
   on windows that exclude the last crisis and its aftermath, or condition
   on time since the last onset (Beck, Katz & Tucker 1998). If the sign
   flips or vanishes, this is the explanation.
2. **The volatility paradox.** Long calm periods precede banking crises:
   Danielsson, Valenzuela & Zer (2018, RFS), up to 211 years and 60
   countries, in and out of sample. Theory: Minsky's "stability is
   destabilizing"; Brunnermeier & Sannikov (2014, AER). Low variance then
   means risk-taking and leverage, and a negative sign on variance is the
   expected sign.
3. **No critical slowing down before crashes.** Guttal et al. (2016, PLoS
   ONE): five major stock indices showed rising variance before crashes
   but no rise in autocorrelation, consistent with noise-driven
   transitions. Diks, Hommes & Wang (2019, Empirical Economics) found
   warning signals before only one of several crashes (1987).
4. **Noise-induced transitions give no warning.** Ditlevsen & Johnsen
   (2010, GRL) show that when a large shock pushes the system across,
   there is no slowing down. Hastings & Wysham (2010, Ecology Letters)
   show regime shifts without warning. Most of our onsets (a currency
   attack, a coup, a food price spike from a world shock) look like
   shocks, not slow drifts to a bifurcation.
5. **Selection on the outcome (the prosecutor's fallacy).** Boettiger &
   Hastings (2012, Proc. R. Soc. B): picking known transitions after the
   fact makes indicators look informative. The panel test, which counts
   non-events too, is the honest one, and it is the one that fails.
   Burthe et al. (2016, J. Appl. Ecol.) find inconsistent and often
   wrong-signed indicators in long ecological series too.
6. **Resolution and window length.** Annual data with a 10-year window
   gives biased, noisy AR1 estimates, with downward small-sample (Kendall)
   bias. Detrending choices change the sign (Dakos et al. 2012, PLoS ONE).
   Recovery times of economies are around a year, close to the sampling
   interval.
7. **Measurement.** Measured variance is higher where statistics are
   poorer. That correlates with the base's own risk and leaves a residual
   of either sign.

**Implication.** Don't fight the sign. Either:
- reframe the indicator as theory says it behaves in finance: the
  duration of low volatility (Danielsson et al.), with a positive prior;
- or drop the generic indicators, and keep variance only as one input to
  a model of time since the last crisis.

Bury et al. (2021, PNAS) report fewer false positives from deep-learning
classifiers trained on simulated bifurcations. That has not been shown on
socio-economic panels, and it inherits assumption 4. Not recommended.

## 3. Is a logit with a strong base offset the right test?

### 3a. Power

A rough calculation, to be redone with the real numbers:

- One-sided α = 0.05 / 4,530 is about 1.1e-5, so z ≥ 4.3. With about 40
  year clusters, a t reference raises it to roughly 4.8.
- For a rare outcome, the standard error of a log odds ratio per standard
  deviation of x is about √(D/E). E is the number of onset windows and D
  the design effect from year clustering and persistent x.
- Take E = 300 and D = 4. The standard error is about 0.12. 80% power
  then needs β ≈ (4.3 + 0.84) × 0.12 ≈ 0.6, an odds ratio of about 1.8
  per SD, *conditional on the base*.
- Effects of slow stocks conditional on history are more plausibly odds
  ratios of 1.1-1.3 per SD. At β = 0.2, power is under 1%.
- With a theory-led list of 10 hypotheses (z ≥ 2.6), power at β = 0.3 is
  about 50%.

The out-of-sample test has the same problem. Trial 45's Brier intervals
are about ±0.0004 on a Brier score of order 0.03. Adding a moderate
predictor to a base with an AUC around 0.8 typically moves the Brier score
by 1e-4 or less. So the 2003-2022 test cannot tell a realistic gain from
none.

Recommendation:
- Report the smallest effect each test could rule out (equivalence
  bounds; Lakens 2017), so "level" is read as "under X", not "zero".
- Replace screening 4,530 candidates with a pre-registered short list
  from theory, as Greenwood et al., Drehmann & Juselius and Goldstone et
  al. each did.

### 3b. The base already absorbs it (mediation)

The offset test estimates the effect of a state beyond the base. That is a
controlled direct effect (Acharya, Blackwell & Sen 2016, APSR). Slow
levels such as arable land or power capacity barely move within a country
over 40 years. Their information is almost all cross-sectional, so a base
that holds the country's own rate and history absorbs it. Trial 45 found
exactly this: what passed was the states' "own parts", and they were tiny.

For the project's stated purpose (drivers), add a second, explanatory
test:
- the total effect, against a base without country history (year
  effects and region only);
- the share of the base's skill that the state accounts for (Shapley
  decomposition of log-score skill over input groups);
- for the state-to-history channel, whether the state predicts the
  country's long-run rate, which is the "where" question.

A state can be a real driver and add nothing to forecasts because history
already carries its footprint. Both results belong in the write-up.

### 3c. Design alternatives

- **Cross-section with year fixed effects.** This removes common shocks
  and identifies from differences between countries within a year.
  Persistent levels are identified there, so it answers "where". It
  doesn't help timing. It is the right design for the attribution test in
  3b.
- **Discrete-time hazard.** A logit with time since the last onset
  (splines or a cubic; Beck, Katz & Tucker 1998; Carter & Signorino 2010)
  is a hazard model and already close to what the lattice does.
  Split-population (cure) models (Beger, Dorff & Ward 2016) separate
  "ever at risk" from "when". They suit kinds where most countries never
  have an onset (coups and war onsets in rich countries). Gain: modest,
  mainly in calibration among low-risk countries.
- **Decompose skill by horizon.** Score the same input at lead years 1, 2,
  3, 4 and 5 (local-projection style). The literature predicts a profile:
  slow stocks' contribution rises with lead time, triggers' falls. A rising
  profile is evidence for slow stocks even when no single horizon is
  significant, and it is a sharper falsification than a level test.
- **Pool across kinds.** A state that theory ties to several kinds (debt
  over its critical level → banking, sovereign debt, growth collapse,
  fiscal consolidation) can share one hierarchical coefficient. That
  multiplies E by the number of kinds pooled.
- **Longer and rotated samples.** For the financial kinds, JST (1870-2020)
  gives about 30 half-decades. Score in leave-one-era-out rotation
  (pre-1914, the interwar years, Bretton Woods, after 1973) rather than
  one split. The political kinds have COW wars from 1816 and coups from
  1950.

### 3d. Scores for rare events

Brier and log score are proper and should stay primary (Gneiting & Raftery
2007, JASA). But they are dominated by the many near-zero country-years,
and gains in ranking the few high-risk ones barely move them. For the
record, add:
- precision-recall curves, AUPRC (Saito & Rehmsmeier 2015, PLoS ONE);
- recall in the top k% of country-years per year (alert lists);
- separation plots (Greenhill, Ward & Sacks 2011, AJPS);
- skill on hard onsets only: no onset of the kind in the past 10 years
  (Mueller & Rauh 2022).

These are more sensitive to the discrimination slow variables could add.
They are not proper, so they must not replace the primary score.

## 4. What else is worth trying

For each idea: the evidence, the expected gain, and a pre-registrable test
on 1960-2002 (or longer where data allow).

**Ensembles of systems (ViEWS competitions).**
- Evidence: Vesco et al. (2022, International Interactions) find that a
  simple ensemble of 15 teams' models beat the average entrant. The
  2023/24 challenge (Hegre et al. 2025, JPR) evaluates 24 models; its
  evaluation article was not available when I checked.
- The lattice's model averaging is already an ensemble. The gain from
  adding external systems is in diversity of method, not in more
  covariates.
- Expected gain: small over a good base.
- Test: add one structurally different model, for example a tree model
  on the same inputs, as a member of the averaging. The registered test
  is the averaged forecast against the current one.

**Leading indicators with demonstrated out-of-sample skill.**
- *Credit-to-GDP gap, house prices, debt service.* Strong for banking
  crises at 2-5 years (Drehmann & Juselius 2014). But Beutel, List & von
  Schweinitz (2019, J. Financial Stability) find a plain logit on these
  inputs hard to beat out of sample. First check whether the banking base
  already reads them. If it does, expect no gain. If not, this is the
  single most likely gain in the project.
- *Current account, FX reserves, real exchange rate.* Supported for
  currency crises and 2008-09 vulnerability (Kaminsky & Reinhart 1999;
  Frankel & Saravelos 2012). Expected gain: moderate for currency crises.
  Test: a pre-registered three-variable group on the currency node.
- *Food import bills and dependence.* World food prices raise unrest, but
  mostly through interactions: regime type (Hendrix & Haggard 2015),
  urban populations, import dependence (Bellemare 2015, AJAE, finds price
  levels matter more than volatility). Test: import dependence × world
  food price change on protest surges, with a positive prior. Our food
  stock-flow model already provides the stock half.
- *Youth bulge with unemployment.* Main effect moderately supported
  (Urdal 2006). Conditioning on unemployment is plausible, the evidence
  thin. Low priority.
- *Regime type and anocracy.* Strongly supported (Hegre et al. 2001,
  APSR; Goldstone et al. 2010). But Vreeland (2008, JCR) shows that
  Polity's middle categories partly code political violence itself, so
  part of the anocracy effect is circular. Use V-Dem or Polity without its
  factionalism components. Expected gain: real for coups and instability,
  probably already partly in the base.
- *Neighbourhood conflict.* Strongly supported (Buhaug & Gleditsch 2008,
  ISQ; ViEWS spatial lags). The lattice's region parent carries some of
  it. A contiguity-weighted lag adds what the region mean blurs. Expected
  gain: small to moderate for conflict onsets.

**Nonlinear thresholds and interactions (gradient boosting with SHAP).**
- Evidence is mixed. Muchlinski et al. (2016, Political Analysis) report
  random forests beat logit on civil-war onset. Neunhoeffer & Sternberg
  (2019, Political Analysis) show that part of that gain came from
  cross-validation errors. Bluwstein et al. (2023) favour trees for
  crises. Beutel et al. (2019) favour logit out of sample. Holopainen &
  Sarlin (2017, Quantitative Finance) find ensembles of methods the most
  robust.
- Use boosting as a diagnostic, not a forecaster: fit it on the design
  years only, read the SHAP interaction values (Lundberg & Lee 2017), and
  turn the top 2-3 interactions into named, signed groups for the
  lattice. Then pre-register those.
- Expected gain: small, but it points step 1 at the right pairs instead
  of a theory guess.

**Time-varying relationships.**
- The lattice's dynamic model averaging with forgetting (Raftery, Kárný &
  Ettler 2010) already allows this.
- The literature finds crisis predictors fairly stable across eras in JST
  (Schularick & Taylor 2012; Bluwstein et al. 2023), with a break around
  Bretton Woods.
- Low priority. Check by era rotation (3c) rather than by new machinery.

**Text and news signals.**
- Good evidence for conflict timing and hard onsets (Mueller & Rauh 2018,
  2022; Chadefaux 2014). Uncertainty and geopolitical-risk indices predict
  macro outcomes (Baker, Bloom & Davis 2016, QJE; Caldara & Iacoviello
  2022, AER).
- They are fast signals, so they belong at the quarter node.
- They do nothing for the slow-driver question. Mueller & Rauh's topics
  are mostly coded in the fast layer already.
- Medium priority, for forecasting only.

**Stock-flow state as a regime classifier.**
- This is step 1 in another form: does the base behave differently when
  a stock is stretched? Evidence: indirect, from the conditioning table in
  1b.
- Cleanest test: the base's calibration slope by regime. In design years,
  fit logit(onset) = a_r + b_r × base log-odds, with r the regime (state
  above or below its design-year 80th percentile). Pre-register b_high >
  b_low for each named state.
- Cheap, one parameter per state, uses all kinds. This is the most
  powerful way to ask "is the stock a gauge of the base's
  contradictions".

## Ranked next trials

Ordered by expected information per unit of effort and by power, not by
expected forecast gain alone.

**0. A power and equivalence audit (no new model; do it first).**
- *Hypothesis:* the screen and the 2003-2022 test can detect only effects
  larger than the literature reports.
- *Design:* from trial 45's data, compute the minimum detectable odds
  ratio per SD for the screen, and the equivalence bound on the Brier and
  log-score differences of the out-of-sample test. Use simulation:
  inject a known effect into one state and rerun.
- *Falsified if:* the injected odds ratio of 1.3 per SD is detected at
  least 80% of the time. Then trial 45's null is informative and the
  stock-flow states can be set aside as forecast inputs.

**1. Horizon profile of a pre-registered short list, pooled across kinds.**
- *Hypothesis:* the skill of the named slow states rises with lead time
  (1 → 5 years), and the skill of fast inputs falls.
- *Named states:*
  - Keen-Goodwin debt over critical debt → banking, sovereign debt,
    growth collapse, fiscal consolidation;
  - food import dependence → food price shock, protest;
  - social tension (elite overproduction, political stress) → protest,
    violence, coup;
  - credit gap → banking (if the base lacks it).
- *Design:* about 8-10 hypotheses, each with a signed prior and one
  coefficient shared across its kinds. Fit on 1960-2002, and on
  1870-2002 for the financial kinds with the JST countries. The outcome is
  an onset at lead years 1-5. Holm over the list.
- *Falsified if:* the pooled coefficient's slope over lead time is not
  positive (one-sided 5%), for a majority of the named states.

**2. Calibration slope by stock regime (step 1 in its cheapest form).**
- *Hypothesis:* when a named stock is stretched, the base is
  under-confident (its log-odds slope is larger).
- *Design:* as in section 4; the same named states and kinds as trial 1,
  with a regime defined by the state's design-year 80th percentile. Holm.
  Score out of sample by recalibrating the base per regime, on
  log score and on top-10% recall.
- *Falsified if:* b_high - b_low ≤ 0 on the design years for the named
  states, or the regime recalibration does not beat a single
  recalibration on the design-year rotation.

**3. Named stock × trigger interactions with the argmax nested.**
- *Hypothesis:* triggers raise risk more where the paired stock is
  stretched; and one gauge dominates (temperature → 0) more than a smooth
  combination does.
- *Pairs:*
  - credit boom × rate tightening → banking;
  - debt over critical debt × world rate shock → sovereign debt;
  - food import dependence × world food price → protest;
  - social tension × growth collapse → violence and coups;
  - commodity price gap × terms-of-trade fall → growth collapse.
- *Design:* each interaction a nested group with a positive prior. The
  contradiction aggregator is a softmax with temperature, chosen by
  design-year log score.
- *Falsified if:* the interaction coefficients' design-year intervals
  include zero for most pairs; or the chosen temperature is high (the mean
  wins), which refutes the "principal" selection.

**4. Total-effect attribution of the stock-flow states.**
- *Hypothesis:* the states explain where onsets happen, even if history
  absorbs their forecast value.
- *Design:* a cross-section with year fixed effects and no country
  history. Report the named states' coefficients, and the Shapley share of
  log-score skill against a base with year and region only; then the share
  of the full base's country effect that the states predict.
- *Falsified if:* the named states explain no more than placebo states
  (another country's values) in that design.

**5. Half-decade node on long samples, rotated eras (step 2).**
- *Hypothesis:* at the half-decade node, the slow states improve the log
  score over the base.
- *Design:* financial kinds 1870 onward (JST), political kinds from 1816
  or 1950 as covered. Leave-one-era-out scoring, with the eras fixed
  before the run. Equivalence bounds reported.
- *Falsified if:* the pooled era-rotated gain is below the pre-registered
  equivalence bound (for example 1% of the base's log score). Only run it
  after trial 0 shows the bound is reachable. Otherwise the result is
  uninformative by design.

**6. Early-warning indicators reframed.**
- *Hypothesis:* the wrong sign is post-crisis composition; and the
  duration of low volatility raises banking risk.
- *Design:* recompute AR1 and variance on windows that drop the last
  onset and three years after it, with time since onset in the base. Add
  low-volatility duration (Danielsson et al. 2018) for banking crises and
  market crashes, with a positive prior.
- *Falsified if:* the sign stays negative after the exclusion, or
  low-volatility duration does not take a positive design-year
  coefficient.

**7. Boosting with SHAP as an interaction finder (diagnostic only).**
- On the design years, to choose or veto trial 3's pairs. No forecast
  test of its own.
- *Falsified if:* the top SHAP interactions do not include any of the
  theory pairs. Then revise the pairs before trial 3 runs, and register
  the revision.

**Lower priority:**
- news and text at the quarter node (forecast value, not drivers);
- external-system ensembles;
- time-varying coefficients beyond what the forgetting already does;
- youth bulge × unemployment.

## References

- Acharya, A., Blackwell, M. & Sen, M. (2016). Explaining causal findings
  without bias: detecting and assessing direct effects. *American
  Political Science Review* 110(3). https://doi.org/10.1017/S0003055416000216
- Baker, S., Bloom, N. & Davis, S. (2016). Measuring economic policy
  uncertainty. *Quarterly Journal of Economics* 131(4).
  https://doi.org/10.1093/qje/qjw024
- Bazzi, S., Blair, R., Blattman, C., Dube, O., Gudgeon, M. & Peck, R.
  (2022). The promise and pitfalls of conflict prediction. *Review of
  Economics and Statistics* 104(4). https://doi.org/10.1162/rest_a_01016
- Beck, N., Katz, J. & Tucker, R. (1998). Taking time seriously.
  *American Journal of Political Science* 42(4).
  https://doi.org/10.2307/2991857
- Beger, A., Dorff, C. & Ward, M. (2016). Irregular leadership changes in
  2014: forecasts using ensemble, split-population duration models.
  *International Journal of Forecasting* 32(1). https://arxiv.org/abs/1409.7105
- Belkin, A. & Schofer, E. (2003). Toward a structural understanding of
  coup risk. *Journal of Conflict Resolution* 47(5).
  https://doi.org/10.1177/0022002703258197
- Bellemare, M. (2015). Rising food prices, food price volatility, and
  social unrest. *American Journal of Agricultural Economics* 97(1).
  https://doi.org/10.1093/ajae/aau038
- Beutel, J., List, S. & von Schweinitz, G. (2019). Does machine learning
  help us predict banking crises? *Journal of Financial Stability* 45.
  Working-paper version: https://www.bundesbank.de/en/publications/research/discussion-papers/an-evaluation-of-early-warning-models-for-systemic-banking-crises-does-machine-learning-improve-predictions--770992
- Bluwstein, K., Buckmann, M., Joseph, A., Kapadia, S. & Şimşek, Ö.
  (2023). Credit growth, the yield curve and financial crisis prediction:
  evidence from a machine learning approach. *Journal of International
  Economics* 145. https://doi.org/10.1016/j.jinteco.2023.103773
- Boettiger, C. & Hastings, A. (2012). Early warning signals and the
  prosecutor's fallacy. *Proceedings of the Royal Society B* 279.
  https://doi.org/10.1098/rspb.2012.2085
- Brunnermeier, M. & Sannikov, Y. (2014). A macroeconomic model with a
  financial sector. *American Economic Review* 104(2).
  https://doi.org/10.1257/aer.104.2.379
- Buhaug, H. & Gleditsch, K. S. (2008). Contagion or confusion? Why
  conflicts cluster in space. *International Studies Quarterly* 52(2).
  https://doi.org/10.1111/j.1468-2478.2008.00499.x
- Burthe, S. et al. (2016). Do early warning indicators consistently
  predict nonlinear change in long-term ecological data? *Journal of
  Applied Ecology* 53(3).
- Bury, T. et al. (2021). Deep learning for early warning signals of
  tipping points. *PNAS* 118(39). https://doi.org/10.1073/pnas.2106140118
- Caldara, D. & Iacoviello, M. (2022). Measuring geopolitical risk.
  *American Economic Review* 112(4). https://doi.org/10.1257/aer.20191823
- Carter, D. & Signorino, C. (2010). Back to the future: modeling time
  dependence in binary data. *Political Analysis* 18(3).
  https://doi.org/10.1093/pan/mpq013
- Cederman, L.-E. & Weidmann, N. (2017). Predicting armed conflict: time
  to adjust our expectations? *Science* 355.
  https://doi.org/10.1126/science.aal4483
- Cesa-Bianchi, A., Eguren Martin, F. & Thwaites, G. (2019). Foreign
  booms, domestic busts: the global dimension of banking crises. *Journal
  of Financial Intermediation* 37.
- Chadefaux, T. (2014). Early warning signals for war in the news.
  *Journal of Peace Research* 51(1). https://doi.org/10.1177/0022343313507302
- Dakos, V. et al. (2008). Slowing down as an early warning signal for
  abrupt climate change. *PNAS* 105(38). https://doi.org/10.1073/pnas.0802430105
- Dakos, V. et al. (2012). Methods for detecting early warnings of critical
  transitions in time series illustrated using simulated ecological data.
  *PLoS ONE* 7(7). https://doi.org/10.1371/journal.pone.0041010
- Danielsson, J., Valenzuela, M. & Zer, I. (2018). Learning from history:
  volatility and financial crises. *Review of Financial Studies* 31(7).
  https://researchonline.lse.ac.uk/id/eprint/91136
- Diks, C., Hommes, C. & Wang, J. (2019). Critical slowing down as an early
  warning signal for financial crises? *Empirical Economics* 57.
  https://doi.org/10.1007/s00181-018-1527-3
- Ditlevsen, P. & Johnsen, S. (2010). Tipping points: early warning and
  wishful thinking. *Geophysical Research Letters* 37.
  https://doi.org/10.1029/2010GL044486
- Drehmann, M. & Juselius, M. (2014). Evaluating early warning indicators
  of banking crises: satisfying policy requirements. *International
  Journal of Forecasting* 30(3). BIS WP 421: https://www.bis.org/publ/work421.htm
- Fearon, J. & Laitin, D. (2003). Ethnicity, insurgency, and civil war.
  *American Political Science Review* 97(1).
  https://doi.org/10.1017/S0003055403000534
- Frankel, J. & Saravelos, G. (2012). Can leading indicators assess
  country vulnerability? *Journal of International Economics* 87(2).
  https://doi.org/10.1016/j.jinteco.2011.12.009
- Gassebner, M., Gutmann, J. & Voigt, S. (2016). When to expect a coup
  d'état? An extreme bounds analysis of coup determinants. *Public Choice*
  169.
- Gelman, A. (2018). You need 16 times the sample size to estimate an
  interaction than to estimate a main effect. Blog post.
  https://statmodeling.stat.columbia.edu/2018/03/15/need-16-times-sample-size-estimate-interaction-estimate-main-effect/
- Gneiting, T. & Raftery, A. (2007). Strictly proper scoring rules,
  prediction, and estimation. *JASA* 102(477).
  https://doi.org/10.1198/016214506000001437
- Goldstone, J. et al. (2010). A global model for forecasting political
  instability. *American Journal of Political Science* 54(1).
  https://doi.org/10.1111/j.1540-5907.2009.00426.x
- Greenhill, B., Ward, M. & Sacks, A. (2011). The separation plot.
  *American Journal of Political Science* 55(4).
- Greenwood, R., Hanson, S., Shleifer, A. & Sørensen, J. A. (2022).
  Predictable financial crises. *Journal of Finance* 77(2).
  https://doi.org/10.1111/jofi.13105
- Guttal, V., Raghavendra, S., Goel, N. & Hoarau, Q. (2016). Lack of
  critical slowing down suggests that financial meltdowns are not critical
  transitions, yet rising variability could signal systemic risk. *PLoS
  ONE* 11(1). https://doi.org/10.1371/journal.pone.0144198
- Hastie, T. & Tibshirani, R. (1993). Varying-coefficient models. *JRSS B*
  55(4).
- Hastings, A. & Wysham, D. (2010). Regime shifts in ecological systems
  can occur with no warning. *Ecology Letters* 13(4).
  https://doi.org/10.1111/j.1461-0248.2010.01439.x
- Hegre, H., Ellingsen, T., Gates, S. & Gleditsch, N. P. (2001). Toward a
  democratic civil peace? *American Political Science Review* 95(1).
- Hegre, H. et al. (2019). ViEWS: a political violence early-warning
  system. *Journal of Peace Research* 56(2).
  https://doi.org/10.1177/0022343319823860
- Hegre, H. et al. (2021). ViEWS2020: revising and evaluating the ViEWS
  political violence early-warning system. *Journal of Peace Research*
  58(3). https://doi.org/10.1177/0022343320962157
- Hegre, H. et al. (2025). The 2023/24 VIEWS prediction challenge:
  predicting the number of fatalities in armed conflict, with uncertainty.
  *Journal of Peace Research* 62(6). https://arxiv.org/abs/2407.11045
- Hendrix, C. & Haggard, S. (2015). Global food prices, regime type, and
  urban unrest in the developing world. *Journal of Peace Research* 52(2).
  https://doi.org/10.1177/0022343314561599
- Holopainen, M. & Sarlin, P. (2017). Toward robust early-warning models:
  a horse race, ensembles and model uncertainty. *Quantitative Finance*
  17(12).
- Jordà, Ò., Schularick, M. & Taylor, A. (2013). When credit bites back.
  *Journal of Money, Credit and Banking* 45(s2).
  https://doi.org/10.1111/jmcb.12069
- Kaminsky, G., Lizondo, S. & Reinhart, C. (1998). Leading indicators of
  currency crises. *IMF Staff Papers* 45(1).
- Kaminsky, G. & Reinhart, C. (1999). The twin crises. *American Economic
  Review* 89(3). https://doi.org/10.1257/aer.89.3.473
- Lakens, D. (2017). Equivalence tests. *Social Psychological and
  Personality Science* 8(4).
- Lundberg, S. & Lee, S.-I. (2017). A unified approach to interpreting
  model predictions. *NeurIPS*. https://arxiv.org/abs/1705.07874
- Mian, A., Sufi, A. & Verner, E. (2017). Household debt and business
  cycles worldwide. *Quarterly Journal of Economics* 132(4).
  https://doi.org/10.1093/qje/qjx017
- Muchlinski, D., Siroky, D., He, J. & Kocher, M. (2016). Comparing random
  forest with logistic regression for predicting class-imbalanced civil
  war onset data. *Political Analysis* 24(1).
  https://doi.org/10.1093/pan/mpv024
- Mueller, H. & Rauh, C. (2018). Reading between the lines: prediction of
  political violence using newspaper text. *American Political Science
  Review* 112(2). https://doi.org/10.1017/S0003055417000570
- Mueller, H. & Rauh, C. (2022). The hard problem of prediction for
  conflict prevention. *Journal of the European Economic Association*
  20(6).
- Neunhoeffer, M. & Sternberg, S. (2019). How cross-validation can go
  wrong and what to do about it. *Political Analysis* 27(1).
- Powell, J. (2012). Determinants of the attempting and outcome of coups
  d'état. *Journal of Conflict Resolution* 56(6).
- Raftery, A., Kárný, M. & Ettler, P. (2010). Online prediction under
  model uncertainty via dynamic model averaging. *Technometrics* 52(1).
  https://doi.org/10.1198/TECH.2009.08104
- Richter, B., Schularick, M. & Wachtel, P. (2021). When to lean against
  the wind. *Journal of the European Economic Association* 19(3).
- Saito, T. & Rehmsmeier, M. (2015). The precision-recall plot is more
  informative than the ROC plot when evaluating binary classifiers on
  imbalanced datasets. *PLoS ONE* 10(3).
  https://doi.org/10.1371/journal.pone.0118432
- Scheffer, M. et al. (2009). Early-warning signals for critical
  transitions. *Nature* 461. https://doi.org/10.1038/nature08227
- Schularick, M. & Taylor, A. (2012). Credit booms gone bust. *American
  Economic Review* 102(2). https://doi.org/10.1257/aer.102.2.1029
- Schularick, M., ter Steege, L. & Ward, F. (2021). Leaning against the
  wind and crisis risk. *American Economic Review: Insights* 3(2).
  https://doi.org/10.1257/aeri.20200310
- Turchin, P. (2012). Dynamics of political instability in the United
  States, 1780-2010. *Journal of Peace Research* 49(4).
- Turchin, P. & Nefedov, S. (2009). *Secular Cycles*. Princeton University
  Press.
- Urdal, H. (2006). A clash of generations? Youth bulges and political
  violence. *International Studies Quarterly* 50(3).
  https://doi.org/10.1111/j.1468-2478.2006.00416.x
- Vesco, P. et al. (2022). United they stand: findings from an escalation
  prediction competition. *International Interactions* 48(4).
  https://www.prio.org/publications/13337
- von Uexkull, N., Croicu, M., Fjelde, H. & Buhaug, H. (2016). Civil
  conflict sensitivity to growing-season drought. *PNAS* 113(44).
  https://doi.org/10.1073/pnas.1607542113
- Vreeland, J. (2008). The effect of political regime on civil war:
  unpacking anocracy. *Journal of Conflict Resolution* 52(3).
- Ward, M., Greenhill, B. & Bakke, K. (2010). The perils of policy by
  p-value: predicting civil conflicts. *Journal of Peace Research* 47(4).
  https://doi.org/10.1177/0022343309356491
