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

## Pre-registered, 21 October 2026: banking crises on the lattice (trial 36)

Registered before the run. The framing is docs/LATTICE.md: a model per
place (country, subregion, region, world) and per time scale (the quarter's
lead and w2 windows, the year, the half-decade), each a dynamic-model
average (augur.dynamic) over its own gauge groups, what finer scales and
its children passed up, and what coarser scales and its parent passed
down a step before, learning online from 1870 (`bnm.lattice-banking`).

- **Quarter nodes**: bnm.named's financial inputs in groups (credit, asset
  prices, the curve, global conditions).
- **Year node**: the Macrohistory stretches of fourteen stocks, the
  displacement invariants, the crisis process, the stabilizers, booms, and
  the catalogue's own history for every country.
- **Half-decade node**: long stretches, long displacement and resonance,
  and memory (time since the country's and the world's last crises).
- Priors from the literature (credit booms, credit with asset booms, the
  credit gap, breadth of displacement), everything else from zero; gauges
  standardized by the origins before each; outcomes known a year after
  their window closes. Settings fixed here, not tuned on scored years.

**Test**: `lattice` against `named` (r6-at) on banking_crisis, by the
amended paired test (bnm.paired, hard rows, lead and w2, 2003-14 and
2015-22), and by the original test, Bonferroni over 36. Exploratory, not
counted: the year and half-decade nodes' own scores, and the weights the
model averaging gave each group through time.

**Results, 21 October 2026 (trial 36).** Two runs, both negative against
`named` on the hard rows, primary Brier:
- as registered: -0.00100 [-0.00178 -0.00040]. The lattice's labels were
  the joined catalogues in every year, with countries already in a crisis
  counted at risk: its base rate was about 5% where the scored labels run
  about 0.4%;
- with the labels matched to bnm.onsets (the modern catalogue from 1974,
  the long ones before; the at-risk rule): -0.00064 [-0.00136 -0.00014],
  too high after 2008 (timing error 0.00045-0.00165 against
  0.00015-0.00023 in 2015-22).
A review then found world credit never known at the quarter nodes (keyed
to the wrong month), so both runs had half the global group empty.

## Pre-registered, 21 October 2026: the lattice as a dialectic (trial 37)

Registered before the run. Trial 36's lattice with the labels matched and
the world-credit bug fixed, and with the dialectic and learning across
levels (docs/LATTICE.md, "The system as a dialectic"):
- contradictions between levels (each place against its parent and
  against each coarser scale; gap, size, accumulated size), tension from
  finer scales re-accumulated at coarser ones, and opposed gauges within a
  place fixed from theory (`bnm.lattice-banking/tensions`): credit against
  investment, house prices against the wage share, equity against
  investment, credit against the wage share; credit growth against the
  curve, the country's credit cycle against the world's;
- resolution: a country's tensions reset once per crisis, a year after
  it, when it is known;
- down-flows through exposure (trade openness, capital openness two years
  back);
- errors fed across levels, and coupled learning between adjacent levels
  (strength 0.1).
The settings are fixed here. Test as trial 36: `lattice` against `named`
on banking_crisis by the amended paired test and the original test,
Bonferroni over 37; exploratory, each group's weight through time and the
ablations without the contradictions and without the learning across
levels.

**Results, 21 October 2026 (trial 37).** Negative: `lattice` against
`named`, primary Brier -0.00150 [-0.00260 -0.00068], log score -0.0130.
Exploratory runs (r11-*):
- without the contradictions: -0.00108 [-0.00193 -0.00038];
- without the learning across levels: -0.00111 [-0.00212 -0.00042];
- the quarter node on its own gauges alone, no flows at all: -0.00062
  [-0.00129 -0.00018].
Two separate shortfalls. The node learner (online dynamic logistic from
1870, model averaging over gauge groups) is about 0.0006 worse than the
batch logit on the same financial inputs, and too high on its rows (3.0%
forecast against 2.6%); its averaging tends to put nearly all weight on
one group. And the lattice's flows and contradictions, each a block of
loosely-priored inputs, add about 0.0009 of noise on about a hundred
crises. In the synthetic worlds built to need them they carried the
signal; on banking crises as measured here they do not yet.

## Pre-registered, 21 October 2026: the lattice rebuilt from the anatomy (trial 38)

Registered before the run. Trial 37's lattice, rebuilt from what the known
crises before 2003 share (docs/ANATOMY.md):
- **what is known as the offset**: each quarter node learns departures from
  named's out-of-sample forecast (from 2000), climatology before it and at
  the coarser scales; the all-inputs model shrunk (prior variance 0.1);
- **directional contradictions**: opposed gauges accumulate only their
  strain (the positive part), a place's disagreement with its parent each
  way apart, reset once per crisis when known; each node passes its strain
  (its tensions and its disagreement with its parent) to finer scales;
- **the turn**: a boom's drawdown from its peak while the stock is
  stretched (house prices, the household boom, equity, bank equity, house
  valuation);
- **more dimensions**: the Macrohistory panel's unused columns, the full
  Penn World Table, WDI credit, and the downloaded sources (bank equity,
  sectoral credit, profit rates, income and wealth shares, IMF debt,
  financial distress, OECD house valuation), with the tensions listed in
  `bnm.lattice-banking/tensions` and the turns in `turns`.
The tensions, turns and settings are fixed here from the anatomy of crises
before 2003 and from theory, not tuned on scored years.

**Test** as trials 36-37: `lattice` against `named` on banking_crisis, the
amended paired test and the original test (Bonferroni over 38).
Exploratory: the ablations without the contradictions, without the turn,
and with the quarter node's own gauges only, and each group's weight
through time.

**Results, 21 October 2026 (trial 38).** Negative, but a fifth of trial
37's loss: `lattice` against `named`, primary Brier -0.00032 [-0.00051
-0.00013], log score -0.0025. Exploratory ablations (r13-*):
- without the turn: -0.00117 [-0.00182 -0.00057]: the turn carries most of
  what the lattice adds, about 0.0009;
- without the contradictions: -0.00029 [-0.00076 +0.00031]: measured
  directionally they no longer cost anything, but do not yet add;
- the quarter node's own gauges with the offset: -0.00015 [-0.00051
  +0.00027], level with named.
The lattice now starts where named stands and the turn earns its place;
what still costs (about 0.0002, the flows between nodes besides the turn)
shows mostly in 2003-14 (timing error 0.00120 against 0.00092), the years
of the global wave of 2007-09.

## Pre-registered, 21 October 2026: the lattice with its flows standardized, chosen on 1980-2002 (trial 39)

Registered before the run. Since trial 38:
- **the node's own inputs standardized**: strain, gaps, turns and feedback
  had entered raw, so the models reading them made extreme forecasts and
  the averaging dropped them (the year node's contradiction and
  all-inputs models held weights near 1e-8); each node now standardizes
  them by their running mean and spread at earlier origins;
- **contradictions summarized**: the place's mean and largest strain over
  the tensions it can measure, their change, its disagreement with its
  parent, the strain carried down and built up, with a prior that more
  strain means more risk (mean 0.3, variance 0.25);
- **data-flow fixes from review**: Romer distress for the US, UK and New
  Zealand, sectoral credit's combined sectors, crash counts unknown where
  uncovered, credit by the year it describes (BIS where it has the
  country), the world's quantities for every country, WID's Namibia and
  Kosovo, the slower sources a further year back;
- **the design chosen on 1980-2002 alone**, from the validation of each
  node on its own outcomes in those years (data/runs/w-*): the full lattice
  at the year and the half-decade, the quarters on their own gauges with
  named's forecast as their offset. On those years the chosen design beat
  the year node without contradictions by Brier +0.00087 [-0.00000
  +0.00233] and the full lattice by +0.00280 [-0.00022 +0.00786].
Smoke checks (bnm.lattice-smoke) to 2001 and to 2008 pass: probabilities,
live inputs in every group, weights that move, strain and the year's log
odds positively related (r 0.06), nothing read from after an origin,
quarter rows aligned with the scoring tools.

**Tests**, both at one-sided 5%:
1. as trials 36-38: the quarter nodes, `lattice` against `named` on
   banking_crisis, the amended paired test (primary) and the original test
   (Bonferroni over 39);
2. the dialectic itself: the year node of this design against the same
   design without the contradiction group, on the year nodes' own
   outcomes for origins 2003-2022 (bnm.paired --scale year), paired Brier.

**Results, 21 October 2026 (trial 39).**
1. The quarter nodes against `named` (primary, paired): Brier -0.00014
   [-0.00052 +0.00030], log score -0.0018 [-0.0052 +0.0021]: level, no
   gain. As the validation chose, the quarters read their own gauges with
   named's forecast as their offset; the lattice's structure sits above
   them.
2. **The dialectic, out of sample: a gain.** The year node against the
   same design without the contradiction group, origins 2003-2022, on its
   own outcomes (2538 country-years, 82 crises): Brier +0.00143 [+0.00029
   +0.00326], log score +0.0067 [+0.0017 +0.0141]. The accumulated,
   directional contradictions (strain over the tensions a place can
   measure, its disagreement with its parent, the strain carried down and
   built up, reset at resolution) improve the year-scale forecast of
   banking crises in years no design choice saw.
The first registered gain of the hierarchy and dialectic (trials 31-39).
On the original test (anticipation within the quarter, Bonferroni over 39)
the quarter nodes pass 0/4 counted cells, as their paired result says:
level with named.

## Pre-registered, 21 October 2026: the lattice for the political kinds (trial 40)

Registered before any run past 2008. bnm.lattice-political carries trial
39's design, chosen on banking's 1980-2002, over unchanged (the full
lattice at the year and half-decade, the quarters on their own gauges with
named's forecast as their offset) to conflict_onset, war_onset, coup,
protest_surge and violence_surge. What is new is per kind and fixed before
any outcome after 2002 was looked at:
- **long catalogues** before the modern ones: Correlates of War intra-state
  wars from 1816 (conflict and war), Archigos removals by the military and
  other government actors from 1875 (coups); protest and violence surges
  start in 1980 and have no half-decade node;
- **year gauges** (bnm.political-gauges) from V-Dem, Maddison, Correlates of
  War capabilities and WDI; the kind's own history, the other kinds'
  recent onsets in the place, the world's share of states with one;
- **tensions, turn and priors from the anatomy** of onsets before 2003
  (docs/ANATOMY-POLITICAL.md);
- **no resolution**: no political gap closes after an onset, so strain is
  never reset, only decays.
Smoke checks to 2008 pass for every kind (and for banking with the trial 39
design): probabilities, live inputs, no group's own model broken (each
within 0.1 nats per outcome of the intercept-only model, the groups that
lose their weight within 0.01), a country's own strain rising with its
outcomes (r +0.04 to +0.12), nothing read from after an origin, quarter
rows aligned with the scoring tools.

**Tests**, one-sided 5%:
1. **the dialectic, per kind** (primary): the year node against the same
   design without the contradiction group (`--without-at
   year:contradiction` added), on the year nodes' own outcomes for origins
   2003-2022 (bnm.paired --scale year), paired Brier; Holm over the five
   kinds. Reported with the log score.
2. the quarter nodes, `lattice` against `named`, pooled over the five kinds
   with equal weights on the amended paired test (bnm.paired), and the
   original test for the record.

**Results, 21 October 2026 (trial 40).**
1. The dialectic per kind, year nodes against the same design without the
   contradiction group, origins 2003-2022 (paired Brier, one-sided 95%;
   Holm over the five kinds):

   | kind | n | onsets | Brier | log score |
   |---|---|---|---|---|
   | violence_surge | 3387 | 271 | **+0.00148 [+0.00057 +0.00269]** | **+0.00383 [+0.00106 +0.00711]** |
   | protest_surge | 3371 | 104 | -0.00010 [-0.00088 +0.00068] | -0.00045 [-0.00384 +0.00322] |
   | war_onset | 3381 | 87 | -0.00014 [-0.00065 +0.00028] | -0.00128 [-0.00365 +0.00068] |
   | conflict_onset | 3286 | 272 | -0.00039 [-0.00085 +0.00008] | -0.00191 [-0.00370 -0.00023] |
   | coup | 3461 | 157 | -0.00044 [-0.00127 +0.00042] | -0.00192 [-0.00611 +0.00307] |

   Violence surges gain and survive Holm (Brier at one-sided 1%: +0.00012
   lower bound, 2000 resamples); Holm stops at the next kind. The other
   four are level, conflict onsets slightly worse on the log score. The
   year nodes lean on their all-inputs model (weight near 1 from 2002;
   conflict on its history until about 2012), so the contradictions act
   through it.
2. The quarter nodes against `named`, pooled over the five kinds: Brier
   +0.00001 [-0.00010 +0.00010], log score +0.00002 [-0.00143 +0.00113]:
   level, no gain; no cell differs after Romano-Wolf (violence surges'
   best, +0.0005 to +0.0006 at w2). On the original test (Bonferroni over
   40) they pass 0/20 counted cells.

With trial 39: the accumulated contradictions improve the year-scale
forecast for banking crises and violence surges, the two kinds whose
anatomy showed strain building steadily (credit against wages;
democracy against the rule of law, inflation and population against
growth), and not for the kinds whose risk the traps and structure carry
(conflict, war, coups) or whose catalogue is short and rare (protest).

## Pre-registered, 21 October 2026: the year carried down to the quarter (trial 41)

Registered before any run. docs/DOWNSCALE.md's recommendations, for all
six kinds (banking_crisis and the five political kinds), each on the
trial 39/40 design otherwise:
- **A**, the trial 39/40 design: the quarters on their own gauges with
  named's forecast as their offset (climatology before named exists, 2000);
- **B**, the year carried down: the quarters' offset is the year node's
  latest forecast for the place, issued before the origin, scaled to the
  quarter's window under a constant hazard (1 - (1 - p)^(h/24)), the
  year's uncertainty carried by quadrature over its linear predictor;
- **C**, B pooled with named: the offset the equal-weight log-odds pool of
  B's and named's (B where named is absent);
- each also **reconciled** (bnm.reconcile): the lead and w2 windows and
  the first quarter scaled in hazard to meet the equal-weight pool of
  their own and the year's chance of an onset in two years.
Changes since trial 40 in all three: the working-age share now reaches
the political year nodes (the loader's whitelist dropped it), the 1962
Dutch conflict onset is in the catalogue (the gazetteer dropped it), and
the banking year node reads currency crises in the last 3 and 10 years
(Laeven & Valencia's rule on WDI exchange rates).

Smoke checks to 2008 pass for every kind with A and with C, including a
new one: every gauge input live somewhere in the run.

**Design**, on 1980-2002 only: of the six (A, B, C, each raw or
reconciled), the one with the best mean over the six kinds of the paired
Brier difference against A, on the quarter nodes' own outcomes (lead and
w2 pooled, every quarter-end origin 1980-2002). Before 2000, B and C are
the same (named starts in 2000).

**Tests**, one-sided 5%, 2003-2022:
1. (primary) the chosen variant's quarter nodes against `named`, pooled
   over the six kinds with equal weights, on the amended paired test
   (bnm.paired), and the original test for the record;
2. the chosen variant against A on the quarter nodes' own outcomes, per
   kind (bnm.paired --scale), Holm over the six.

**Design chosen, 21 October 2026, before any 2003-2022 score (trial 41).**
Mean over the six kinds of the paired Brier difference against A, quarter
nodes' own outcomes 1980-2002, lead and w2 averaged: C reconciled
+0.00336, B reconciled +0.00334, A reconciled +0.00294, C +0.00191, B
+0.00190. **C reconciled** is the design tested. Every political kind
gains from the year (+0.0014 to +0.0078 by window); banking loses with the
year's offset alone (w2 -0.0064) and nearly recovers when reconciled
(-0.0010).

**Results, 21 October 2026 (trial 41).**
1. **Against named** (primary), the quarter nodes of C reconciled pooled
   over the six kinds: Brier +0.00004 [-0.00012 +0.00021], log score
   +0.00088 [-0.00051 +0.00218]: level, no gain. Violence surges' w2 cells
   gain most (+0.0012, +0.0018), banking crises' 2003-14 cells lose
   (-0.0005 lead, -0.0012 w2); no cell differs after Romano-Wolf. Pooled
   recall at 10% rises over named for coups, protest and violence surges
   in 2003-14. On the original test (Bonferroni over 41) 0/24 counted
   cells pass.
2. **Against A** (the trial 39/40 quarters), per kind, on the quarter
   nodes' own outcomes 2003-2022 (lead and w2 pooled):

   | kind | n | onsets | Brier | log score |
   |---|---|---|---|---|
   | coup | 27275 | 537 | **+0.00107 [+0.00053 +0.00190]** | **+0.00765 [+0.00323 +0.01317]** |
   | violence_surge | 26323 | 936 | +0.00073 [+0.00003 +0.00180] | +0.00690 [+0.00123 +0.01609] |
   | conflict_onset | 25698 | 903 | +0.00047 [-0.00024 +0.00136] | +0.00165 [-0.00200 +0.00548] |
   | war_onset | 26564 | 272 | +0.00036 [-0.00002 +0.00091] | +0.00175 [-0.00137 +0.00552] |
   | protest_surge | 26700 | 358 | +0.00016 [-0.00033 +0.00073] | +0.00239 [-0.00061 +0.00566] |
   | banking_crisis | 19967 | 286 | -0.00028 [-0.00102 +0.00024] | -0.00294 [-0.00831 +0.00120] |

   Holm over six: coups gain (one-sided 0.83%: Brier +0.00107 [+0.00029
   ...], 2000 resamples); violence surges stop it at the next step
   (one-sided 1%: [-0.00024 ...]). Five of six point estimates favour
   carrying the year down.

The year carried down improves the quarters over the trial 39/40 design
for coups, out of sample and after correction, with every political
kind's estimate on the same side; against named, it is level. Banking,
where the year node itself gains (trial 39), is the one kind where the
quarters do not: its year forecast for two years scaled to a quarter
blunts what named's financial inputs time within the year.

## Pre-registered, 21 October 2026: the world model (trial 42)

Registered before the training runs. docs/WORLD-MODEL.md, bnm.world-run:
thirteen kinds (banking, currency, sovereign and inflation crises, growth
collapses, fiscal consolidations, food price shocks, market crashes,
protest and violence surges, coups, conflict and war onsets), each its own
lattice built as on its own (banking's, or the political lattice's with
the financial kinds reading banking's quarterly gauges), all from 1870,
stepped together month by month. At the year and half-decade each node
reads, from before its origin, every parent in the sequencing graph
(docs/SEQUENCING.md: 22 edges, 21 exciting, 1 inhibiting): the parent's
state (its linear predictor), its onsets as one- and five-year excursions
above the place's own rate, its state through the place's executive
constraints, and how many kinds stand in their top fifth at the place; a
group per parent in the model averaging, the state's prior at the edge's
sign (0.2 or -0.2, variance 0.1). Each node records its principal
contradiction. The quarters keep trial 41's design. The half-decade only
for kinds covered from before 1930. Each kind's contradiction prior takes
the sign its design years show (negative for market crashes). Smoke checks
to 2008 pass for all thirteen (bnm.world-run --smoke).

Two runs: **world** (with relations) and **world-norel** (the same, no
relations: each kind on its own, as in trials 39-41 but all from 1870).

**Tests**, one-sided 5%, 2003-2022:
1. (primary) each kind's year node, world against world-norel, on its own
   outcomes, pooled over the kinds with equal weights (bnm.world-score);
   per kind for the record, Holm over thirteen;
2. the world's joint forecasts: for each edge, country and year end from
   2003, the simulated chance that both kinds have an onset within three
   years against the product of the same two marginals (independence), on
   whether both happened; paired Brier, edges weighed equally.
3. for the record: the quarter nodes against named on the six kinds named
   forecasts (bnm.paired), as trial 41.

**Results, 21 October 2026 (trial 42).**
1. **Relations against none** (primary), year nodes 2003-2022, pooled over
   the thirteen kinds: Brier -0.00133 [-0.00229 -0.00047], log score
   -0.00292 [-0.00535 -0.00052]: **a loss**. Per kind, only banking crises
   lean to a gain (+0.00087 [-0.00006 +0.00246]); market crashes (-0.0134),
   coups (-0.00094) and inflation crises (-0.00031) lose significantly;
   the rest are level. Coups lose on the design years as well (-0.0038,
   1980-2002), so the loss is the design's, not the period's.
2. **Joint forecasts against independence**: Brier -0.00005 [-0.00007
   -0.00002], log -0.0035: the simulated joints are slightly worse than the
   product of the marginals.
3. For the record, the quarters against named (six kinds): Brier -0.00002
   [-0.00019 +0.00015], level, as trial 41's.

What the world learned. The relation groups never win weight on their
own (their models' weights stay near zero); the relations act through the
all-inputs model, which carries every kind's year node, and there they
add 4 coefficients per parent plus the coupling. The artificial system
built from them gives plausible transmissions (an onset's effect on
another kind's chance of one within three years, averaged over
countries): banking crises on inflation crises +0.18, growth collapses on
protest surges +0.06, inflation crises on coups +0.05, protest surges on
coups +0.03, growth collapses on conflict +0.01; and the principal
contradiction of coups, protest surges and sovereign defaults in most
countries in 2022 is the growth-collapse relation. But a relation that
moves a forecast is not one that improves it: out of sample they cost.
Likely causes, to be designed against on 1980-2002 before another test:
the parent's state is a level that carries the parent's structural risk,
which the child's own gauges already hold (poverty, regime), so it
double-counts; the onset kernels and moderated terms quadruple the
coefficients per parent; and the relations enter the all-inputs model,
where a noisy coefficient cannot be refused.

## Pre-registered, 21 October 2026: links nested, one per edge (trial 43)

Registered before the training runs, after trial 42's links cost out of
sample. docs/WORLD-MODEL.md ("Why the links cost in trial 42"): three bugs
fixed (the excursion's level ignored the lag before onsets are known; the
coupling's cut took in regions; the artificial world ignored that lag),
and the design changed on the design years (bnm.relation-diag, origins
1960-2002, against trial 42's world without relations): each edge passes
one input in its form, never the parent's level; an edge is kept, and its
form chosen, by rule (a gain past the child's forecast in the edge's
direction, at least twice the placebo's; the post-crisis regime when it
qualifies); twelve of 22 edges kept; each link nested in the node's own
model, so the averaging weighs the node with it against the node without
it; no coupling, no moderator; above the countries a place reads the mean
of its countries' inputs (the smoke found regions reading zeros); a kind
without a parent gets no relations. All else as trial 42.

Three runs: **t43-world**, **t43-world-placebo** (each country reading
its parents at the next country in sorted order), and trial 42's
**t42-world-norel** as the bench (the code it ran is unchanged).

**Tests**, one-sided 5%, 2003-2022:
1. (primary) the eight linked kinds' year nodes (banking, currency and
   inflation crises, coups, conflict and war onsets, protest and violence
   surges), t43-world against t42-world-norel, on their own outcomes,
   pooled with equal weights (bnm.world-score --graph); per kind for the
   record, Holm over eight. The five kinds without a parent must match the
   bench exactly (a check, not a test);
2. the joint forecasts against independence, as trial 42's second test,
   over the twelve edges;
3. the links against their placebo: t43-world against t43-world-placebo,
   pooled as 1. A gain on 1 without one here would be the period's, not
   the links'.

**Results, 22 October 2026 (trial 43).**
1. **Links against none** (primary), the eight linked kinds' year nodes,
   2003-2022: Brier -0.00012 [-0.00030 +0.00005], log -0.00081 [-0.00161
   +0.00005]: level. Trial 42's loss (-0.00133) is gone, but nothing is
   gained. Per kind all are level on Brier; protest surges lose on log
   score (-0.00386 [-0.00716 -0.00051]). The five kinds without a parent
   match the bench exactly (largest difference 0): the plumbing is sound.
2. **Joint forecasts against independence**: Brier -0.00002 [-0.00004
   +0.00001], level; log -0.00301, a loss.
3. **The links against their placebo**: Brier -0.00006 [-0.00021
   +0.00009], level.

The bugs fixed and the links nested, they no longer cost; as passed (the
raw post-crisis regime, excursions, destabilization) they carry what the
period shares as much as what the parent did at the place: the
diagnostic after trial 43 (bnm.relation-diag with bnm.factors' own parts)
shows that taking each form's own part removes the placebo's gain and
uncovers within-country links (growth collapses and inflation crises to
coups among them). Trial 44 tests that.

## Pre-registered, 22 October 2026: own parts and early warnings (trial 44)

Registered before the training runs. Two changes on trial 43, chosen on
the design years (1960-2002) against trial 42's world without relations:
- links may pass a form's own part (bnm.factors: the parent's input at
  the country less what the world's and the region's countries share at
  t, loadings learned online); the rule (bnm.relation-diag/choose-form)
  now chooses among raw and own forms, preferring the post-crisis regime;
  sixteen of 22 edges kept (data/runs/sequencing/graph-t44.edn), three
  new: conflict onsets, growth collapses and inflation crises to coups;
- each kind may carry one early warning (bnm.ews: lag-1 autocorrelation,
  variance and their trends over the 12 data years before the origin, of
  the country's own part of GDP growth, inflation or food inflation),
  chosen by the same gain and placebo rule, its sign the design years'
  (theory's sign, more slowing down more risk, did not hold: the
  warnings that pass mostly lower risk); ten kinds
  (data/runs/sequencing/ews-t44.edn), one nested group each.
Above the countries an own edge passes its members' mean common part.
Also the code: the lattice's reads go through bnm.sheaf (outputs
unchanged, pinned by a golden snapshot), augur v0.8.0.

Runs: **t44-world** (--graph graph-t44 --ews ews-t44), **t44-world-
placebo** (each country reading its parents and its warning at the next
country), bench **t42-world-norel**.

**Tests**, one-sided 5%, 2003-2022:
1. (primary) the linked kinds' year nodes (every kind with a parent or a
   warning: all but food price shocks and market crashes), t44-world
   against t42-world-norel, pooled with equal weights; per kind for the
   record, Holm. Food price shocks and market crashes must match the
   bench exactly (a check);
2. joint forecasts against independence over the sixteen edges;
3. t44-world against t44-world-placebo, pooled as 1;
4. for the record, t44-world against t43-world on the kinds both link.
