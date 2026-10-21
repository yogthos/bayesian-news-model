# Phase changes: what can be anticipated, and how far the model is from it

October 2026. The goal this note measures the model against is anticipating
dramatic events that culminate long-term trends: crashes like 2000 and 2008,
and their political analogues (the Arab uprisings, Euromaidan, the invasion
of Ukraine, Sri Lanka 2022, the Myanmar coup). The intended mechanism follows
Meadows' *Thinking in Systems*. Small events accumulate into stocks, feedback
loops reinforce or balance them, and delays hide the build-up until a
threshold gives way. docs/RESEARCH.md covers the general plan; this note goes
deeper on phase changes and on evaluation. Citations marked (unverified) were
not confirmed against a primary source.

## What the record says is possible

**Hazard, not dates.** Every approach that has worked out of sample gives an
elevated hazard over one to five years, not a date.

- *Financial crises* have the best record.
  - The credit-to-GDP gap leads by two to five years. The debt service ratio
    dominates in the last one to two years (Drehmann & Juselius, BIS WP 421,
    IJF 2014).
  - Household, cross-border and FX debt and property-price gaps add to that
    (Aldasoro, Borio & Drehmann, BIS QR 2018).
  - Credit growth with a flat yield curve is most dangerous when rates are low
    (Bluwstein et al., JIE 2023).
  - Credit plus asset-price growth over three years gives about a 40% crisis
    probability against a 7% base rate (Greenwood et al. 2022).
  - Logit models on credit would have flagged 2007-08 out of sample in many
    countries (Beutel, List & von Schweinitz, Bundesbank DP 48/2018).
- *Equity bubbles.* A run-up of more than 100% over two years sharply raises
  the probability of a 40% crash (Greenwood, Shleifer & You, JFE 2019). The
  Phillips-Shi-Yu explosive-root tests date NASDAQ exuberance from about 1995,
  years early: they find the regime, not the timing.
- *Sornette's LPPLS / dragon kings.*
  - Real-time hits: the US housing turn, called mid-2006 in a 2005 preprint,
    and oil in 2008.
  - Misses: the 2002 call for an S&P slide into 2004.
  - The critical time t_c is unstable across fit windows.
  - What survives is a *multiscale confidence indicator*: the share of
    qualifying fits across short, medium and long windows. That is a hazard
    state read across resolutions, which is exactly the "trends across
    temporal resolutions" idea.
  - No aggregate out-of-sample scorecard of the Financial Bubble Experiment
    was found (unverified).
- *Political instability.*
  - Structural models (Goldstone et al., AJPS 2010: regime type and
    factionalism, infant mortality, neighbouring conflict, discrimination) get
    about 80% accuracy two years ahead. Their skill decayed after 2004
    (Bowlsby et al., BJPS 2020), so the drivers drift.
  - Process data add timing at weeks-to-months horizons. Examples:
    - escalation models on event data (Blair & Sambanis, JCR 2020);
    - news topics for onsets in peaceful countries (Mueller & Rauh, APSR
      2018, JEEA 2022);
    - war-talk in the news before wars (Chadefaux, JPR 2014);
    - EMBERS protest forecasts, days to weeks ahead.
- *Turchin's structural-demographic theory* builds an explicit stock product,
  the political stress index (mass mobilization potential × elite
  mobilization potential × fiscal distress). It gives decade-scale hazard,
  not timing. Its main forecast (US instability around 2020) is scored a hit
  only by its authors.

**What is not possible.**

- *Generic early-warning statistics are weak.* Crashes show rising variance
  but not rising autocorrelation (Guttal et al., PLOS ONE 2016). That makes
  them noise-triggered transitions, not bifurcations, and noise-triggered
  transitions carry no warning by construction (Ashwin et al. 2012; Boettiger
  & Hastings 2013).
- *Hindsight inflates every warning signal.* Choosing cases because they
  tipped makes noise look predictive (Boettiger & Hastings, Proc R Soc B
  2012).
- *Trigger dates and cascade sizes behind preference falsification are
  intrinsically opaque* (Kuran 1991; Granovetter 1978).
  - Cascades near a threshold come out either small or global (Watts, PNAS
    2002). So the honest predictive distribution there has two modes, not
    one.
  - Nothing found anticipated the Arab uprisings prospectively from event
    data. ICEWS missed Egypt; Leetaru's news-tone low was found after the
    fact.
  - The one real advance warning was global and structural: the food-price
    letter Lagi, Bertrand & Bar-Yam sent on 13 December 2010.
- *The fast layer contributes conditional updating.* Once Tunisia falls,
  Egypt's hazard should rise within days, and other countries' should not.

So success is defined as: calibrated hazard ramps that rise above the base
rate one to three years ahead, with an honest false-alarm count, plus fast
conditional updating once a trigger fires. Point dates are not a criterion.

## Where the model stands

**What the model observes.** Weekly counts of six political event channels
from GDELT, ICEWS and POLECAT, normalized by each source's volume. It sees
no prices, credit, debt, food prices, demography or regime data.

- The dot-com crash and 2008 are not in its observation space. It can see at
  most their political aftermath: Greek protests, Occupy, the 2011 uprisings
  through food prices.
- Normalizing by source volume, needed because GDELT's coverage grows,
  also removes genuine global trends.

**What it did before the political transitions it can see.** From the v2-q
quarterly backtest (before this month's fixes), escalation probability and
rank among about 240 countries in the quarter of onset:

| case | quarter of onset | rank (channel) |
|---|---|---|
| Tunisia | Q4 2010 | 127 (protest), p 0.08 |
| Egypt | Q4 2010 / Q1 2011 | 47 (protest), 10 (violence) |
| Syria | Q1 2011 | 19-36 |
| Ukraine (Euromaidan) | Q4 2013 | 20-39 |
| Myanmar coup | Q1 2021 | 62 (protest), 7 (violence) |
| Sri Lanka | Q1 2022 | 71 (protest), 23 |

The ranks rose once events were under way. This is the pattern the
literature predicts for a model built on event history alone: it finds
hot spots, not onsets.

**How the design diverges from what phase changes need:**

1. *No accumulating stocks.* Memory is a decaying sum of past events, about
   two years at most (forgetting 0.99, slow kernel about 33 weeks). Nothing
   builds up without events, and nothing accumulates over years.
2. *Only reinforcing loops.* Linear Hawkes excitation cannot inhibit. There
   is no repression backfire, no concession, no exhaustion and no fear, so no
   endogenous turning points; waves only fade with the kernel.
3. *Stationarity forbids the transition being forecast.* Branching is capped
   below one and forecasts revert to the node's running mean. This month's
   fixes made that reversion well calibrated, which is right for the
   quarterly counts and the opposite of a culmination.
4. *No slow exogenous drivers.* Slow trends get absorbed into a branching
   ratio that is biased toward one (Filimonov & Sornette 2015; our toy
   checks).
5. *Wrong target and horizon.* It forecasts 13-52-week counts scored by CRPS
   and quarter-on-quarter escalation, which reward persistence. Phase changes
   need hazards over one to three years, scored on onsets.
6. *Counts measure attention, not magnitude or participation*, and drift with
   media coverage. The GDELT 2.0 change in February 2015 is a structural
   break in the source.
7. *A predictive with one mode*, where cascades near a threshold have two.
8. *Cross-unit links are additive excitation along geographic neighbours.*
   There are no thresholds, no state-dependent susceptibility, and no
   affinity or trade network.
9. *Only exponential lags*, where system dynamics delays are hump-shaped
   (Erlang chains).
10. *No regime layer*, so a structural break is treated as noise.
11. *No output or evaluation for culminating events*: no hazard ramps, lead
    times, false-alarm rates or error diagrams. Anticipation is not even
    measured today.

What it does well, and what carries forward: a hierarchy over places and
actors, online Bayesian learning, calibrated count forecasts, and the fast
layer that conditional updating needs.

## The architecture the evidence points to

Four layers. The existing model becomes the fast one.

- **L0, slow drivers**, quarterly or annual, as latent monthly states
  (mixed-frequency):
  - financial: credit gap, debt service ratio, property-price gap, yield
    slope, spreads, two-year equity run-up and its acceleration;
  - political and social: food prices relative to income, youth share and
    educated unemployment, wages and inequality, fiscal distress, V-Dem regime
    type and factionalism;
  - global factors: US rates, the global credit cycle.
  - Sources are free: BIS, FRED, JST, Shiller, FAO, UN WPP, V-Dem, WID, Center
    for Systemic Peace.
- **L1, latent stocks per node**, pooled across the hierarchy, each with its
  own discounting:
  - grievance or pressure;
  - legitimacy and capacity;
  - mobilization capacity;
  - exhaustion, which ends waves;
  - coercive deterrence;
  - financial fragility (leverage × asset-price gap).

  Inflows come from L0 and from events. Events enter through saturating
  functions, so a burst of protest builds mobilization capacity with
  diminishing returns. Signs can depend on state: repression builds grievance
  where legitimacy is low (Epstein, PNAS 2002; Davenport 2007). Delays are
  Erlang chains, giving hump-shaped lags.

  Estimation: parameters calibrated offline with HMC on pooled history
  (Andrade & Duggan, System Dynamics Review 2021, PLOS CB 2022), and stocks
  filtered online with an unscented Kalman filter or particle filter.
- **L2, regime and hazard.**
  - Markov switching between normal, mobilized and crisis regimes, with
    transition probabilities driven by the stocks and their interactions:
    grievance × (1 − legitimacy), fragility × a flat curve, a neighbour in
    crisis.
  - Early-warning features with learned weights, never used as alarms:
    variance and low-frequency power of L3 residuals at several scales, an
    LPPLS-style multiscale acceleration indicator, explosive-root statistics
    on prices.
  - Output: hazard curves at 6, 12, 24 and 36 months with posterior bands.
    Near a threshold the predictive is a mixture over regimes.
- **L3, the fast event layer: today's model**, changed so it can couple to the
  slower layers:
  - baseline log μ = discounted local level + βᵀS;
  - excitation α₀·exp(γᵀS + regime), saturating, with inhibition from
    exhaustion and deterrence;
  - links over affinity and trade as well as distance;
  - online changepoint detection on its residuals, feeding L2;
  - events feeding back into L1 to close the loops.

**Sequence.** Each step must show out-of-sample gain on onsets before the
next is built (docs/RESEARCH.md says the same).

1. The evaluation harness (below). Without it nothing else can be judged.
2. Hazard as an output of today's model, and the benchmark logits.
3. L0 for the financial channel, where the evidence is strongest. The
   targets are banking crises and index drawdowns, with credit gap,
   debt service ratio, property gap and yield slope. This is the only route
   by which 2008-type events can be anticipated at all.
4. L1 stocks linked to the L3 baseline. Then, if that shows gain on onsets,
   to excitation.
5. L2 regimes and mixture predictives.

## How to evaluate it

Pooled scores reward persistence and hide the question (Bazzi et al.,
REStat 2022; Mueller & Rauh, JEEA 2022), so the protocol is built around
onsets.

- **Ground truth from catalogues, fixed in advance, never from memory.**
  - UCDP conflict onsets and escalations.
  - Powell & Thyne coup attempts, dated to the day.
  - Mass Mobilization Project protest waves, to 2020, and ACLED after about
    2016.
  - Systemic banking crises: Laeven & Valencia 1970-2025 (IMF WP/26/94),
    annual; ESRB European crises, quarterly.
  - Mechanical drawdowns: 40% within two years of a run-up, on MSCI country
    indices.
  - Mechanical event surges in the model's own channels: above the 99th
    percentile of a node's normalized history.

  A named list (the 2011 uprisings, Ukraine 2014 and 2022, Myanmar, Sri
  Lanka, Sudan, the Sahel coups, Chile and Hong Kong 2019, Kazakhstan and
  Iran 2022, Bangladesh 2024, 2008, Greece 2010, Lebanon 2019) is for
  illustration only. Claims rest on the full catalogue.
- **Unit and windows.** Country-quarter. At each origin, P(onset within 1-4
  quarters) and P(onset within 5-8 quarters).
  - The event quarter and the four after it are dropped (post-crisis bias:
    Bussière & Fratzscher 2006).
  - Lead time is the earliest origin from which the probability stays above
    the alert threshold until the event (stable signals: Drehmann &
    Juselius).
- **Metrics,** all recursive and real time:
  - AUROC and AUPRC by horizon, within region and regime strata so stable
    OECD countries don't inflate them;
  - recall at fixed alert budgets (top 5% and 10% of country-quarters);
  - relative usefulness (Alessi & Detken 2011; Sarlin 2013) at μ of 0.5,
    0.7 and 0.9;
  - Brier and log scores with reliability in the top decile;
  - Molchan error diagrams;
  - concordance against three to five matched non-event controls;
  - block-bootstrap intervals over countries and years. With 20-60 onsets,
    AUROC intervals are about ±0.07-0.1.
- **Benchmarks to beat on onsets:**
  - base rate;
  - persistence and a recent-events logit;
  - a structural logit (Goldstone/PITF variables; credit gap and debt
    service ratio for banking crises);
  - a raw news-volume surge;
  - published text-based forecasts (conflictforecast.org, ViEWS);
  - crowd forecasts where questions exist (Metaculus on Ukraine 2022).
- **Robustness:**
  - 2003-2014 and 2015-2026 separately, because of the GDELT 2.0 break;
  - leave one region out, so the 2011 uprisings don't carry the result;
  - hyperparameters frozen on data before each origin;
  - real-time vintages for macro data.
- **Counts as anticipation** only if:
  - at a fixed alert budget, recall and usefulness beat persistence and the
    structural logit, with intervals that exclude zero;
  - the gain holds two to four quarters ahead, not just one, which is mostly
    nowcasting;
  - matched controls rarely light up;
  - the top decile is calibrated.

  A plot of a hazard rising before one famous case does not count.
- **Amended October 2026, before the epic's adoption decisions.** Every
  forecast was out of sample, but the same 2003-26 years had been looked
  at while choosing among many groups and combinations, which overfits at
  the level of method choice. From now on:
  - adoption is scored on 2003-14 and 2015-22 only; 2023 on is locked and
    scored once (`bnm.anticipation --confirm true`) for the record chosen.
    It was seen in aggregate in earlier rounds, and conflict catalogues
    end in 2024, so the lock is thin: the chosen record is also frozen
    with a dated tag, and the live quarters after it are the clean test;
  - a gain must hold with each region left out (the lowest lower bound,
    `loro`), so one wave such as 2011 cannot carry it;
  - recall intervals are Bonferroni-corrected for the configurations
    tried, counted in docs/TRIALS.md (`--tests` with its line count);
  - hyperparameters of new methods come from the literature, not the
    scored years. `scripts/scoreboard.py` applies all of it.

**What to expect.** Event data give real but short warning for violence
onsets: months, occasionally a year for interstate war. They give little
warning for mass uprisings, and none for financial crises, where credit and
asset-price stocks carry the signal. A model that anticipates 2008-type
events needs L0. For political transitions, the realistic aim is hazard ramps
from slow stocks plus fast conditional updating, judged on the full
catalogue.

## Pre-registered, October 2026: the onset model as a hierarchy over time

Until now the onset hazards were one pooled logit across countries: the
tree above a country entered only as inputs (onsets among neighbours, wave
kernels) and as a regional frailty that scaled a region's hazards by its
resolved record. Two changes make it a hierarchy of models over places and
over time (bnm-969).

- **Partial pooling over places (`hier-<inputs>`, trial 31).** The same
  logit, with nested random intercepts for region, subregion and country
  (`augur.hier/intercepts`). Each level's variance is estimated each year
  from the rows resolved by then, so the data say how far a country stands
  from its subregion and a subregion from its region. A country with few
  onsets is pulled toward its neighbours' level rather than the world's.
- **States that evolve (`dyn-<model>`, trial 32).** Every node of the tree
  (world, region, subregion, country) carries a latent AR(1) state on the
  log scale of the onsets that came against those the model expected
  (`augur.hier/states`, `bnm.dynamic`). A node's evidence is the onsets of
  every country under it, so local onsets move their subregion's state,
  which moves its region's, and so on up. Its state scales the hazards of
  every country under it, so a region running hot lifts its members. A
  row reads the states filtered as of its quarter, less the kind's
  reporting lag, and runs them forward over its window: how long a wave
  persists, and when the system returns to normal, are each level's
  persistence, chosen with its size each year by the states' own
  one-step-ahead forecasts of the onsets known by then. A size of zero
  drops a level. The grids (half-lives of a season to four years, sizes
  up to a doubling of the rate) are set here, not tuned on scored years.
  The chance of an onset anywhere in a subregion or region comes from the
  shared states (`dynamic-groups`), not from multiplying independent
  country forecasts.

**Run.** The rows of r6 (`--rows onsets-3`), its kinds, and the same
inputs, so each comparison is paired on identical rows: `history`,
`named`, `named+mix` against `hier-history`, `hier-named`,
`hier-named+mix`, with `hier-named+mix-placebo`. `bnm.dynamic` then adds
`dyn-` versions of `named`, `named+mix`, `hier-named+mix` and, on
r6-pooled, of `hedge` and `mlpool`.

**Counts as a gain** under the amended rules above:
- **hier:** `hier-X` against `X`, as bench, on the decision eras, with
  intervals Bonferroni-corrected over 32 trials and holding with each
  region left out.
- **dyn:** `dyn-X` against `X`, at the country level and with `--level
  subregion` and `--level region`. A gain only at the group levels counts
  as a gain for waves, not for countries.

If neither gains, the pooled logit stays and the result is written up as a
negative one.

**Amended, 21 October 2026, before r8 (trial 33).** The first run of
`hier-` (r7b, augur v0.2.3) is kept and scored as registered above. Its
training summaries, seen before this amendment but before its paired
scores, showed `hier-named` falling to `hier-history`'s level where the
covariates had helped (protest_surge lead 2008+ AUC 0.818 against
`named`'s 0.865). Two causes, both known in the literature:

- The country intercepts span much the same space as slow country
  covariates and absorb them (Bafumi & Gelman 2006; Bell & Jones 2015;
  Hodges & Reich 2010).
- The ridge was picked from {10, 100, 1000} by log score, and with the
  intercepts in, it went to 1000 in 15 of 18 blocks (against 10-100 for
  the flat models), shrinking the covariates to nothing. A ridge and
  random-effect variances compete for the same signal and should be
  estimated together (Wood 2011).

So `hier-` becomes, for r8:
- **within-between inputs**: the inputs, then each country's means of
  those past the constant and history, over its rows before the year
  (Mundlak 1978);
- **the ridge estimated with the variances**: one Laplace EM step for each
  per round, ten rounds (`augur.logit/fit-evidence`), no grid;
- **half-normal(0, 1) priors on each level's sd** on the log odds, the
  weakly informative default (Gelman 2006; Chung et al. 2013).

The settings are the literature's defaults, not tuned on scored years. An
offline check on the training rows of protest_surge and violence_surge
(lead, years 2004-19) gave pooled AUC 0.869 and 0.817 against 0.848 and
0.804 for the flat logit with its ridge estimated the same way. r8 runs the
same models on the same rows, and is judged by the criteria above with the
Bonferroni count raised to 33.

**Results, 21 October 2026 (trials 31-33).** Every registered pair failed
the original test: `hier-history`, `hier-named` and `hier-named+mix`
against `history`, `named` and `named+mix` passed 0 of 24 counted cells in
r7b (trial 31) and again in r8 (trial 33), and `dyn-named` against `named`
passed 0 of 24 at the country level, 0 of 24 for subregions and 0 of 12
for regions (trial 32). Point gains on the hard rows were near zero and the
intervals about ±0.5 in recall. Country intercepts are learned from a
country's own onsets, and hard rows are the countries without any, so they
could not help there; a state shared by a region scales its members alike,
which ranking within a quarter cannot see.

## Pre-registered, 21 October 2026: precursors, calm slopes, and a test with power

Registered before any of r9 is scored. The original test stays, and is run
and reported for every trial, these included.

**Why the test is amended.** Recall within each quarter at 5% and 10%, with
99.7% intervals (Bonferroni) on 12-140 hard onsets a cell, has almost no
power, and is blind to timing by construction. A power simulation
(`bnm.paired --simulate`, on the hard rows of r6-at with the outcomes
replaced: a signal of log-odds size beta, shared within a country's year
and across a kind's windows, that only the model knows; 10 draws a size)
gave the share of draws each test found it:

| beta | new primary | new per-cell (any) | original (any cell) |
|---|---|---|---|
| 0.25, null | 0.00 | 0.00 | 0.00 |
| 0.25 | 0.70 | 0.00 | 0.00 |
| 0.50, null | 0.00 | 0.00 | 0.00 |
| 0.50 | 1.00 | 0.00 | 0.00 |
| 1.00, null | 0.00 | 0.00 | 0.00 |
| 1.00 | 1.00 | 0.00 | 0.90 |

The null rows draw outcomes from the benchmark, so they measure the false
positive rate. The per-cell Romano-Wolf test on Brier found nothing even at
beta 1, where the original test found it nine times in ten: a cell's Brier
difference for rare onsets is buried in the noise of thousands of
non-onsets (its t was 1-2), and only pooling over cells gives it power. So
per-cell claims rest on the original test and on Romano-Wolf together, and
the decision on the pooled primary. The choice follows from power and propriety, made on
synthetic outcomes, not from which test the results so far favour
(Gneiting & Raftery 2007; Diebold & Mariano 1995; Pepe et al. 2013; Lerch
et al. 2017 on outcome conditioning: the hard-row filter conditions on
past onsets only).

**The amended test** (`bnm.paired`), on the hard rows of the counted cells
(lead and w2, 2003-14 and 2015-22):
- **Primary:** the paired difference in mean Brier score against the
  benchmark, pooled over the cells with every kind weighted equally; a
  gain when the one-sided 95% bound of the two-way (countries and blocks
  of years) bootstrap is above zero. The log score, floored at 1e-4, is
  reported beside it.
- **Per cell:** Romano-Wolf stepdown on the cells' Brier differences,
  family-wise 5% (Romano & Wolf 2005), in place of Bonferroni.
- **Secondary, for timing:** recall at a 10% budget pooled over the
  quarters of a cell, and the between-quarter calibration error.
- Trials scored before this amendment (1-33) are not rescored as
  confirmatory; any rescoring of them is exploratory.

**Trial 34, `precursors`** (`bnm.precursors`): invariants of a country's
fifteen event streams against its own past, needing no onset history:
displacement (norm, breadth past 1.5, largest, at 20 quarters, and
resonance, the smallest norm of 8, 20 and 40), the sharpest one-quarter
rise (trigger), burstiness of the total (dispersion), and the shift in its
co-movement with its subregion's countries (range). Sources: the
Macrohistory invariants above (docs/INVARIANTS.md), the Keilis-Borok
school's premonitory activity, clustering and range of correlation,
spatial early warnings (Dakos et al.), the credit-gap principle of
measuring against one's own baseline (Drehmann & Yetman 2018). Spreads are
floored at 0.25 on the log scale and z-scores capped at 10, set before any
scored run. `named+precursors` against `named` and against
`named+precursors-placebo`.

**Trial 35, calm slopes (`cs-<inputs>`)**: every input also times a flag
for no known onset of the kind in ten years, so the calm countries, where
the test is counted, get their own intercept and slopes (Mueller & Rauh
2022: what predicts onsets out of peace differs). `cs-named`,
`cs-named+mix`, `cs-named+precursors` against `named`, `named+mix` and
`named+precursors`.

**Run r9**: the rows of r6 (`--rows onsets-3`), the benchmarks from r6-at
on the same rows. Bonferroni count for the original test: 35.

The registration closes here (21 October 2026, before r9 is scored).

**Results, 21 October 2026 (r9, trials 34-35).** Neither gains.

| model | bench | primary Brier (90% two-way) | log score | original test |
|---|---|---|---|---|
| named+precursors | named | -0.00002 [-0.00007 +0.00005] | worse | 0/24 |
| named+precursors | named+precursors-placebo | -0.00001 [-0.00004 +0.00001] | worse | - |
| named+precursors | named+mix | -0.00006 [-0.00014 +0.00002] | worse | 0/24 |
| cs-named | named | -0.00012 [-0.00027 +0.00001] | worse | 0/24 |
| cs-named+mix | named+mix | -0.00013 [-0.00028 +0.00000] | worse | 0/24 |
| cs-named+precursors | named | -0.00018 [-0.00035 -0.00004] | worse | 0/24 |

On the hard rows the precursors carry nothing beyond a shuffle of
themselves, and the calm slopes cost more in fitting than they return.
Exploratory, as trial 1 predates the amendment: `named+mix` against
`named` gains on the primary (+0.00004 [+0.00000 +0.00009]) and on the log
score (+0.00094 [+0.00050 +0.00139]), where the original test found
nothing: the amended test has power on real data, and every trial since
has found no signal it could detect.
