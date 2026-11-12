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

Commands, noted 10 November 2026: the two runs passed no --graph, and
bnm.world-run then read data/runs/sequencing/graph-t43.edn by default.
A run now has to name its model (--without-relations true, or --graph),
so they are

    jolt -A:tools -M -m bnm.world-run --out t43-world --graph data/runs/sequencing/graph-t43.edn
    jolt -A:tools -M -m bnm.world-run --out t43-world-placebo --graph data/runs/sequencing/graph-t43.edn --placebo true

and the bench is --without-relations true. Trials 44 and 45 passed
their graphs (below); the bases t44 to t48-world-norel passed
--without-relations true.

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

**Amended before any training, 22 October 2026 (trial 44).** Reviews of the
design found and fixed: unknown inputs entered as 0, not neutral once
standardized (they now enter as the input's running mean and do not
move it); trend indicators computed from as few as two points; placebo
rows not matched to the real rows; raw variances heavy-tailed (now log);
the best of many candidates kept at a nominal threshold (now corrected,
chi-squared at 5%/k for k candidates, Bonferroni); and, in the base, the
year gauges reading WDI a year staler than published (the publication
lag applied twice). The last changes the base, so trial 44 has its own
bench, **t44-world-norel** (trial 43's design without relations, the
gauges fixed), and both diagnostics were rerun on it (origins
1960-2002):
- links: seven of 22 edges (data/runs/sequencing/graph-t44.edn):
  conflict onsets to violence surges and war onsets (post-crisis regime),
  currency to inflation crises (its own part), growth collapses to
  protest surges (destabilization), protest surges to coups, violence
  surges to conflict and war onsets (post-crisis regime). The links to
  coups seen before the correction do not pass it;
- warnings: five kinds (data/runs/sequencing/ews-t44.edn, window 12),
  all lowering risk: banking crises and fiscal consolidations (the log
  variance of inflation's own part), currency crises (its autocorrelation
  trend), growth collapses (the log variance of growth's own part), war
  onsets (the trend of inflation's log variance).
Links and warnings apply at the year and half-decade nodes (the quarters
drop relations). The tests stand as registered with t44-world-norel as
the bench; the linked kinds are those with a parent or a warning. For
the record, the gauge fix alone: t44-world-norel against t42-world-norel.

**Results, 22 October 2026 (trial 44).**
1. **Links and warnings against none** (primary), the ten linked kinds'
   year nodes, 2003-2022: Brier -0.00001 [-0.00024 +0.00025], log
   +0.00050 [-0.00073 +0.00200]: level. Per kind all are level on both
   scores; the nearest to a loss is conflict onsets (Brier -0.00016
   [-0.00041 +0.00004]). Food price shocks, market crashes and sovereign
   defaults, with neither parent nor warning, match the bench exactly.
2. **Joint forecasts against independence**, seven edges: Brier -0.00000
   [-0.00006 +0.00006], level; log -0.00266 [-0.00504 -0.00095], a loss, as
   in trial 43.
3. **Against the placebo**: Brier +0.00011 [-0.00004 +0.00026], log
   +0.00070 [-0.00009 +0.00147]: level, leaning to the real links.
4. For the record, against t43-world on the six kinds both link: Brier
   +0.00019 [-0.00017 +0.00054], level (protest surges +0.00123 [-0.00008
   +0.00274]). The kinds unlinked in one of them differ, as they should:
   the bench changed with the gauge fix.

The gauge fix alone (t44-world-norel against t42-world-norel, all
kinds): Brier -0.00048 [-0.00127 +0.00039], level; war onsets lose a
little on Brier (-0.00021 [-0.00048 -0.00001]) and currency crises on log
score (-0.00327 [-0.00659 -0.00016]). Reading WDI at its true date costs
what the stale read had leaked.

Taking own parts made the links honest (the placebo no longer matches
them) but not useful: what one kind's onset says about another's at the
place, beyond the base's own gauges, is too little to show in twenty
years. The relations between events are not where the signal is; trial
45 asks the slow stocks underneath them instead (docs/STOCK-FLOW.md).

## Pre-registered, 22 October 2026: stock-flow states (trial 45)

Registered before the training runs. Trial 44's diagnostics counted
country-years as independent: a year's rows share its shocks, and a world
series is the same at every country, so forty design years passed for
thousands of observations (world series and their ten-year-shifted
placebos alike reached likelihood ratios over 100). Links, warnings and
states are now chosen by a score statistic whose variance sums within each
year (bnm.relation-diag/clustered-test), the threshold Bonferroni-corrected
for the candidates tried, each at least twice its placebo's. Rerun on
t44-world-norel (origins 1960-2002):
- links: five edges (data/runs/sequencing/graph-t45.edn): conflict onsets
  to violence surges and war onsets, violence surges to war onsets (the
  post-crisis regime), inflation crises to banking crises (inhibiting,
  the year's excursion), growth collapses to protest surges (theory's
  edge, its own part);
- warnings: one (data/runs/sequencing/ews-t45.edn): growth collapses, the
  log variance of GDP growth, lowering risk;
- states (bead bnm-62z.13, data/runs/sequencing/states-t45.edn): of 4,530
  kind-state candidates from the stock-flow models (food, Keen-Goodwin,
  social tension, energy, technology, commodity cycles) and the material
  base (read two years after its data year), three pass, all lowering
  risk: coups, arable land's own part; fiscal consolidations, grain use's
  and total power capacity's own parts. No world series passes.
Each state is one more nested group at the year and half-decade nodes,
its prior mean the design years' sign.

Runs: **t45-world** (--graph graph-t45 --ews ews-t45 --states
states-t45), **t45-world-placebo** (each country reading its parents, its
warning and its states at the next country), bench **t44-world-norel**.

**Tests**, one-sided 5%, 2003-2022:
1. (primary) the linked kinds' year nodes (a parent, a warning or a
   state: banking crises, coups, fiscal consolidations, growth collapses,
   protest surges, violence surges, war onsets), t45-world against
   t44-world-norel, pooled with equal weights; per kind for the record.
   The unlinked kinds must match the bench exactly (a check);
2. joint forecasts against independence over the five edges;
3. t45-world against t45-world-placebo, pooled as 1;
4. for the record, t45-world against t44-world on the kinds both link,
   and coups and fiscal consolidations (the kinds with states) per kind.

**Results, 22 October 2026 (trial 45).**
1. **Links, warning and states against none** (primary), the seven linked
   kinds' year nodes, 2003-2022: Brier +0.00012 [-0.00021 +0.00050], log
   +0.00107 [-0.00069 +0.00325]: level. Per kind, for the record:
   banking crises lose (Brier -0.00059 [-0.00132 -0.00003]; their only
   input is the inhibiting edge from inflation crises), not past Holm's
   correction over seven kinds; the rest are level, coups (a state) at
   -0.00004 [-0.00011 +0.00004] and fiscal consolidations (two states) at
   +0.00044 [-0.00023 +0.00122]. The six unlinked kinds match the bench
   exactly.
2. **Joint forecasts against independence**, five edges: Brier -0.00002
   [-0.00008 +0.00002], level; log -0.00399 [-0.00834 -0.00117], a loss,
   as in trials 43 and 44.
3. **Against the placebo**: Brier +0.00014 [-0.00009 +0.00035], log
   +0.00088 [-0.00012 +0.00190]: level.
4. For the record, against t44-world on the kinds trial 45 links: Brier
   +0.00010 [-0.00010 +0.00034], level; fiscal consolidations +0.00059
   [+0.00000 +0.00131], at the edge of a gain and not past Holm; banking
   crises -0.00059 [-0.00147 -0.00001]. Violence surges and growth
   collapses are identical (the same edge and the same warning).

The stock-flow states do not show in the forecasts. Of the six models'
outputs none passed the design years once years, not country-years, were
counted; the three that did are slow levels (land, grain use, power
capacity), whose own parts the event models' gauges largely hold already.
The models describe the drivers (debt against its critical level, the
commodity pipeline, technology gaps, food storage), and on their own
terms they track them (docs/STOCK-FLOW.md); what they do not do, at a
year's resolution over forty design years, is time the onsets past what
the base already knows. Next (bnm-62z.13): the states as gauges of the
base's contradictions rather than as inputs to the year node, and the
half-decade node, where slow stocks should matter most.

## Pre-registered, 22 October 2026: the horizon profile of named states (trial T1 of bnm-3qu)

Registered before any outcome after 2002 is read for these states. The
power audit (trial 0) showed trial 45's screen could not find the effects
the literature reports (OR 1.2 per SD found one time in seven); a named
list of at most ten, pooled across kinds, finds OR 1.35 about 87% of the
time. The question: do slow stocks' effects grow with the lead and fast
triggers' fade (docs/LIT-NEXT-STEPS.md)?

States (docs/T1-STATES.md): five of the ten examined, cut by the order
fixed for coverage and proxy quality (debt service, elite overproduction,
the fuel price gap, anocracy, the Keen debt gap out):
- slow: the credit-to-GDP gap (banking crises, growth collapses; +), the
  youth bulge (protest, violence and conflict onsets; +), the historical
  technology gap (conflict onsets, coups; +);
- fast: food import exposure (protest surges; +), reserves in months of
  imports (currency crises, sovereign defaults; -).
Each state is demeaned by year (the within-year cross-section: country
drivers, not the world's) and standardized; its effect on the kind's
onset in year t+h exactly, h = 1..5, pooled over its kinds, by the
year-clustered score (tools/bnm/horizon-profile), with the base's year
forecast (t44-world-norel) as offset.

**Tests**, origins 2003-2022 (outcomes to 2022, so the last origin at lead
h is 2022-h), one-sided 5%, nulls from 200 permutations of country labels:
1. (primary) (a) slow: the mean over the slow states of s(c45 - c12) > 0,
   s the expected sign, c12 and c45 the mean coefficient at leads 1-2
   and 4-5;
2. (b) fast: the mean over the fast states of s c12 > 0;
3. for the record: each state's s c12 and s(c45 - c12), Holm over the
   five; the same statistics with no offset (the total effect); the
   placebos (the next country's state).
On the design years (1960-2002) the same statistics over these five
states are reported alongside. There, over all ten, (a) was level (p
0.36) and (b) passed (p 0.02, food import exposure most).

**Results, 22 October 2026 (trial T1).** Origins 2003-2021, the five
states year-demeaned, 200 permutations:
1. **(a) slow states' skill rising with the lead** (primary): +0.100,
   null +0.006 (sd 0.094), p 0.154: level. With no offset (the total
   effect) +0.131, p 0.070. On the design years over the same five it
   went the other way: -0.093 (p 1.0).
2. **(b) fast states right-signed at short leads**: +0.078, null -0.007
   (sd 0.069), p 0.109: level (design years +0.359, p 0.010).
3. Per state (s c12 / s(c45 - c12), against the base): the credit gap
   +0.127 / -0.095, largest at leads 1-3, banking crises carrying it
   (z 1.3-1.5 at every lead); the youth bulge +0.115 / +0.035, flat;
   the technology gap +0.409 / +0.359, its rise on only four origin years
   (CHAT ends 2006: 577 country-years); food import exposure -0.027 /
   +0.063, gone (it carried (b) on the design years at +0.50); reserves
   +0.183, fading to nothing at lead 5, as a trigger should. None passes
   Holm on its own.

Neither hypothesis holds out of sample. Slow states do not gain with the
lead: where they carry anything it is about as much at every lead, the
total effect (no offset) larger than the increment over the base. They
say where, not when (Ward, Greenhill & Bakke 2010), and the base, which
knows each country's history, already holds most of where. Of the fast
states reserves behaved as a trigger in both periods; food import
exposure did not survive the change of period. Next (bnm-3qu): the
total-effect attribution (T4), which asks the drivers' question the
increment cannot, and the base's calibration by stock regime (T2).

**Correction after the outcome, 28 October 2026 (trial T1, bead
bnm-0cf).** A fix made after the result was seen; the registered result
above stands as run. The test rows held the base's warm-up rows as
offsets: before the year node has read a published outcome of a kind it sits
at its prior, p near 0.5 for every country (the origins whose fit-cut
is before the kind's first origin: currency crises 1961-1964, inflation
crises 1960-1963, market crashes 1961-1963, protest and violence surges
1979-1981, food price shocks 2003-2006). bnm.warm-up now keeps those
rows out of every fit and recalibration. Here that is food price shocks, food import exposure's
second kind, at origins 2003-2006. Rerun on the fixed code
(data/runs/sequencing/horizon-t1-registered-0cf-fix.tsv; the unfixed code
reproduces the registered file exactly):
1. (a) slow states: unchanged, +0.100, p 0.154: level.
2. **(b) fast states: +0.143 (null -0.006, sd 0.067), p 0.015: passes**
   (registered +0.078, p 0.109, level). Food import exposure's s c12 is
   +0.102 (was -0.027), reserves' unchanged at +0.183; with no offset
   +0.069, p 0.154 (was +0.060, p 0.179).
**The verdict on (b) changes from level to pass**, through a fix made
after the outcome, so it is read as that, not as a registered pass. Food
import exposure keeps its design-year sign out of sample, at a fifth of
its design-year size. The design-year numbers quoted in the registration
were not rerun.

**Rerun after a base correction, 29 October 2026 (trial T1, bead
bnm-82j.7).** The offset base moved: t48-world-norel (bnm-wns, unknown
errors nil, groups without inputs out) in place of t44-world-norel, and
food price shocks, food import exposure's second kind, moved most. The
registered result and the warm-up correction above stand as run. The
same command with --run t48-world-norel on 7d5740b
(data/runs/sequencing/horizon-t1-registered-t48.tsv; the same code on
t44-world-norel reproduces the 0cf-fix file exactly):
1. (a) slow states: +0.100 (null +0.006, sd 0.094), p 0.154: level,
   unchanged.
2. **(b) fast states: +0.124 (null -0.007, sd 0.070), p 0.025: still
   passes** (registered +0.078, p 0.109, level; after bnm-0cf +0.143, p
   0.015). Food import exposure's s c12 +0.088 (+0.102 after bnm-0cf),
   reserves' +0.160 (+0.183); with no offset +0.069, p 0.154, unchanged
   (it does not read the base).
The pass survives the corrected base, a little smaller. It is still a
pass reached through a fix made after the outcome, a candidate that
needs data the test has not seen (the years after 2022, locked) to be
confirmed, not a registered pass. The watch-list rule v2 does not fit
the trial's shape (an offline fit of five states with permutation
nulls, no top-k list), so no v2 reading is given.

## Pre-registered, 22 October 2026: the base's calibration by stock regime (trial T2) and its plain recalibration (trial T8)

Registered before any outcome after 2002 is read for these tests. On the
design years the base's year-node calibration slope is far below 1 for
most kinds (logit(y) = a + b logit(p): banking 0.30, currency 0.08,
growth collapses -0.17; conflict, coups and wars about 0.75), its levels
off (currency 0.128 against 0.069). T2 asks whether the miscalibration
depends on the stock regime (the states as gauges of the base); T8
whether a plain recalibration helps at all. tools/bnm/calibration-regime,
the year node of t44-world-norel, outcome an onset within (t, t+2],
origins 2003-2020; every parameter, threshold and centre from 1960-2002:
logit(y) = a_k + b_k (L - m_k) + c R + d R (L - m_k), R = 1 above the 80th
percentile (design years) of the year-demeaned signed state: financial
group (banking, defaults, growth collapses, fiscal consolidations: the
credit gap; currency crises: low reserves), social group (protest,
violence, conflict, war: the youth bulge; coups: anocracy); c and d
shared within a group, a_k and b_k per kind.

**Tests**, one-sided 5%, permutation nulls of whole country series (200):
1. (T2 primary) d > 0 in each group, Holm over the two; c and the joint
   two-parameter p beside it, with the placebo (the next country's state).
2. (T2) the regime recalibration against the plain recalibration on
   log score per row, the lower end of the 90% year-bootstrap interval
   above 0 (on the design rotation it lost: -0.0003 and -0.0002).
3. (T8 primary) the plain recalibration (a_k, b_k from 1960-2002) against
   the raw base on log score per row, pooled over both groups, the lower
   end of the 90% interval above 0; Brier and top-10% recall for the
   record. Equivalence bound for "no gain": Brier +0.0006 (the power
   audit). On the design rotation it gained +0.013 (financial) and +0.021
   (social).
4. For the record: terciles (d_top - d_bottom), per-kind c and d, credit
   gap x house prices (banking; directional, c > 0, about 30 onsets) and
   food import share x the world grain price gap (protest).

## Pre-registered, 22 October 2026: what the stock-flow states explain on their own (trial T4)

Registered before any outcome after 2002 is read for this test. T1 found
the slow states say where rather than when, and the base, which knows each
country's history, holds most of where. T4 asks the drivers' question:
how much of the forecasts' skill the states explain on their own, against
placebo states that keep each country's own data coverage (coverage
follows income, so where the data are is not credited to the states).
tools/bnm/attribution (docs/T4-ATTRIBUTION.md): logits at the year node
(an onset within (t, t+2]), fitted on origins 1960-2000, scored on
origins 2002-2020 (outcome years 2003-2022), the design's ten kinds
(banking, conflict, coups, currency, fiscal consolidations, growth
collapses, inflation, market crashes, sovereign defaults, war), within
mode (each kind-year at M0's level), the base recalibrated per kind,
ridge penalties fixed on the design rotation, 1,000 two-year block
bootstrap draws with the fits held fixed. The 13 states: credit gap, Keen
debt gap, debt service, reserves; youth bulge, elite overproduction,
anocracy; food import exposure, arable land, grain use, power capacity,
the fuel price gap; the technology gap.

**Tests**, pooled with kinds equally weighted, one-sided 5% (the lower
end of the 90% interval):
1. (primary) skill(S) - skill(P'), log score, P' the coverage-matched
   placebo: > 0 (design years +0.0021 [+0.0012, +0.0031]);
2. where minus when, of S - P', log score: > 0 (design +0.0020
   [+0.0013, +0.0027]);
3. the states over the recalibrated base, skill(B'S) - skill(B'): equivalent
   to nothing if the upper ends are below +0.0006 Brier and +0.002 log
   (design +0.00005 Brier, +0.0008 log);
4. for the record: S - P' per group (financial, social, material,
   technology), Holm over the four; the shares of the base's skill (S,
   P', the indicators alone); the Shapley split; the raw-offset base.

**Results, 22 October 2026 (trial T4).** Fitted 1960-2002, scored on
outcome years 2003-2022, ten kinds, within mode, recalibrated base, 90%
intervals:
1. **The states against coverage-matched placebos** (primary), log:
   +0.0010 [-0.0007, +0.0027]: level (design years +0.0021).
2. **Where over when**, of S - P', log: +0.0026 [+0.0013, +0.0037]:
   passes.
3. **Over the recalibrated base**: log +0.0001 [-0.0007, +0.0009], Brier
   -0.00005 [-0.0003, +0.00018]: both upper ends inside the bounds
   (+0.002, +0.0006): equivalent to nothing.
4. Per group, S - P', log (Holm over four): material +0.0035 [+0.0027,
   +0.0043] and social +0.0022 [+0.0009, +0.0034] pass; technology
   +0.0003 (its historical gap ends in 2006, so the test years carry it
   only at the start); financial -0.0001. Per kind the states help the
   political kinds' cross-section (conflict onsets +0.022, wars +0.005,
   coups +0.005) and currency and inflation crises a little, and hurt
   market crashes (-0.016), fiscal consolidations (-0.005), sovereign
   defaults and banking crises (-0.003): where the economic kinds strike
   is not where the levels point. The recalibrated base's own skill over
   year effects alone: +0.0103 log.

Out of sample the states still say where, more than when, and the land,
grain and power levels and the social states keep their edge over
placebos with the same coverage; pooled, the economic kinds' losses
cancel the political kinds' gains. Over a base that knows each
country's history they add nothing, as the design years said. The
drivers are real for where conflict happens; for when anything happens,
and for the economic kinds at all, these states do not carry it.

**Correction after the outcome, 28 October 2026 (trial T4, bead
bnm-0cf).** A fix made after the result was seen; the registered result
above stands as run. The fits on 1960-2000 held the base's warm-up rows:
before the year node has read a published outcome of a kind it sits
at its prior, p near 0.5 for every country (the origins whose fit-cut
is before the kind's first origin: currency crises 1961-1964, inflation
crises 1960-1963, market crashes 1961-1963, protest and violence surges
1979-1981, food price shocks 2003-2006). bnm.warm-up now keeps those
rows out of every fit and recalibration. Rerun on the fixed code with the registered penalties and the
same ten kinds (data/runs/sequencing/attribution-t4-registered-0cf-fix.tsv;
the unfixed code reproduces the registered file exactly): (1) S - P', log,
+0.0011 [-0.0006, +0.0027] (was +0.0010 [-0.0007, +0.0027]): level; (2)
where over when +0.0026 [+0.0013, +0.0037]: passes; (3) over the
recalibrated base, log +0.0001 [-0.0007, +0.0008], Brier -0.00005
[-0.0003, +0.00018]: equivalent to nothing; (4) per group material
+0.0035 and social +0.0022 pass, technology +0.0003 and financial -0.0001
do not; the base's own skill +0.0103. No verdict changes.

**Rerun after a base correction, 29 October 2026 (trial T4, bead
bnm-82j.7).** The base moved: t48-world-norel (bnm-wns) in place of
t44-world-norel; of T4's ten kinds, market crashes moved most. The
registered result and the warm-up correction above stand as run. The
same command with --run t48-world-norel on 2e9a074
(data/runs/sequencing/attribution-t4-registered-t48.tsv; the same code on
t44-world-norel reproduces the 0cf-fix file exactly). M0, M_S and M_P'
do not read the base, and the rows are the same, so tests 1 and 2 and
4's per-group S - P' are byte-identical: (1) S - P', log, +0.0011
[-0.0006, +0.0027]: level; (2) where over when +0.0026 [+0.0013,
+0.0037]: passes; (4) material and social pass, technology and financial
do not. Only what reads the base moves (the shares of its skill and the
Shapley split with it): (3) over the recalibrated base, log +0.0004 [-0.0002,
+0.0009] (was +0.0001 [-0.0007, +0.0008]), Brier -0.00001 [-0.0002,
+0.0002] (was -0.00005): both upper ends still inside +0.002 and
+0.0006, equivalent to nothing; market crashes' increment -0.0005
[-0.0043, +0.0030] (was -0.0027); the base's own skill +0.0098 (was
+0.0103). **No verdict changes.** The watch-list rule v2 does not fit
the trial's shape (offline ridged fits, no top-k list), so no v2 reading
is given.

**Results, 22 October 2026 (trials T2 and T8).** Origins 2003-2020,
everything fitted on 1960-2002, 200 permutations:
1. **T2 primary, the regime's slope shift d > 0**: financial +0.146 (p
   0.124), social +0.124 (p 0.134): neither passes. The financial
   group's level shift c (record) is large, +0.420 (z 5.6, permutation p
   0.005): in the top fifth of credit gaps (and the bottom of reserves)
   onsets come more often than the recalibrated base says, but its
   placebo moves too (+0.286, z 2.2), so part of it is not the state.
2. **T2, the regime against the plain recalibration**, log per row:
   financial -0.00050 [-0.00070, -0.00031], social +0.00024 [+0.00017,
   +0.00031]: passes for the social group only, and by little.
3. **T8, the plain recalibration against the raw base**, log per row:
   financial +0.0036 [-0.0046, +0.0120], social -0.0089 [-0.0106,
   -0.0068]; pooled by rows about -0.0036: fails. What the base got
   wrong in 1960-2002 it does not get wrong the same way in 2003-2022;
   the social kinds' raw base is better calibrated now than the
   recalibration fitted on its early years (their slopes were set in
   1960-80, when the base had little history to learn from).
4. For the record: credit gap x house prices raises banking crises
   beyond the base (zone c +1.14, z 4.8, p 0.050, 36 onsets, the sign
   Greenwood et al. report); food import share x grain prices level.
   Terciles: financial d_top > d_bottom p 0.045, social 0.20.

The states do not act as gauges of the base's calibration, and a fixed
recalibration does not transfer across eras: the base's miscalibration
is not stable, so any recalibration has to be learned as the base runs
(online, from past origins only: bnm-3qu.9). The one state effect that
keeps showing, the credit gap's level shift with house prices, is the
financial cycle the literature names, on too few onsets to stand alone.

**Correction after the outcome, 28 October 2026 (trials T2 and T8, bead
bnm-0cf).** A fix made after the result was seen; the registered results
above stand as run. The recalibrations fitted on 1960-2002 held the
base's warm-up rows: before the year node has read a published outcome of a kind it sits
at its prior, p near 0.5 for every country (the origins whose fit-cut
is before the kind's first origin: currency crises 1961-1964, inflation
crises 1960-1963, market crashes 1961-1963, protest and violence surges
1979-1981, food price shocks 2003-2006). bnm.warm-up now keeps those
rows out of every fit and recalibration. Here that is currency crises (financial) and
protest and violence surges (social). Rerun on the fixed code
(data/runs/sequencing/calibration-t2-registered-0cf-fix.tsv; the unfixed
code reproduces the registered file exactly):
1. T2 primary, d > 0: financial +0.155 (p 0.104), social +0.095 (p
   0.199): neither passes, as registered. The financial level shift c
   +0.416 (z 5.5, permutation p 0.010), its placebo +0.291.
2. T2, the regime against the plain recalibration, log per row:
   financial -0.00051 [-0.00071, -0.00032], social +0.00020 [+0.00012,
   +0.00028]: passes for the social group only, as registered.
3. T8, the plain recalibration against the raw base: financial +0.0037
   [-0.0045, +0.0121], social -0.0086 [-0.0103, -0.0066]: fails, as
   registered: without the warm-up rows the fixed recalibration still
   loses for the social kinds.
4. Record: terciles financial p 0.040, social 0.30; credit gap x house
   prices unchanged; food import share x grain prices level.
No verdict changes.

## Pre-registered, 23 October 2026: the base recalibrated online (trial T8, second form)

Registered before any outcome after 2002 is read for this test. The fixed
recalibration (T8, first form) failed: the base's miscalibration drifts.
tools/bnm/recalibrate-online learns, per kind, logit(y) = a_t + b_t L (L
the base's log odds, t44-world-norel's country year node) as it runs: a
logistic DLM (augur.dynamic), DMA over discounts {0.9, 0.95, 0.98, 1}
(forget 0.99), the state reverting at 0.95 a year toward the identity
pulled halfway to the learned kinds' mean, learning from 1960 only from
origins whose window has closed and whose outcome is published (each
catalogue's known-lag: one year for banking, coups, market crashes,
protest and violence, two for the rest); a kind is the base unchanged
until its first outcome. On the design years (scored 1981-2000) it
gained +0.0102 log per row [+0.0054, +0.0156]; tuned on 1960-78 alone
the gain was +0.0085, so about a sixth of it is selection.

**Tests**, origins 2003-2020 (windows to 2022), 1,000 two-year block
bootstrap draws, one-sided 5%:
1. (primary) online minus raw base, log per row, pooled over the ten
   kinds of the financial and social groups: lower end of the 90%
   interval above 0;
2. Brier, pooled: no harm if the upper end is below +0.0006;
3. for the record: per group and per kind, all kinds pooled, top-10%
   recall, no reversion (phi 1) paired against the primary, unpooled.
If it passes, the recalibrated country year node is the base for the
next world run.

**Results, 23 October 2026 (trial T8b).** Origins 2003-2020:
1. **Online against the raw base** (primary), log per row, financial and
   social pooled: +0.0001 [-0.0016, +0.0019]: level (design years
   +0.0102).
2. **Brier**: -0.0002 [-0.0007, +0.0003]: no harm (upper end under
   +0.0006).
3. Per kind all intervals cross zero but inflation crises (-0.0011) and
   wars (-0.0014), both small losses; no reversion (phi 1) +0.0010
   [-0.0021, +0.0051], unpooled +0.0005: level.

The base's miscalibration on the design years was its early learning: by
2003 it has forty years of history and is about as calibrated as a
recalibration can make it. Neither a fixed nor an online recalibration
improves it in the test years, and the online one does no harm. The base
stays as it is.

**Correction after the outcome, 28 October 2026 (trial T8b, bead
bnm-0cf).** A fix made after the result was seen; the registered result
above stands as run. The online recalibration learned from the base's
warm-up rows: before the year node has read a published outcome of a kind it sits
at its prior, p near 0.5 for every country (the origins whose fit-cut
is before the kind's first origin: currency crises 1961-1964, inflation
crises 1960-1963, market crashes 1961-1963, protest and violence surges
1979-1981, food price shocks 2003-2006). bnm.warm-up now keeps those
rows out of every fit and recalibration; a kind now waits for its first real forecast's
outcome. Rerun on the fixed code
(data/runs/sequencing/recalibrate-online-2003-2022-0cf-fix.tsv; the
unfixed code reproduces the registered file exactly): (1) online against
the raw base, financial and social pooled, log +0.0002 [-0.0015, +0.0019]
(was +0.0001 [-0.0016, +0.0019]): level; (2) Brier -0.0002 [-0.0007,
+0.0003]: no harm; (3) per kind, inflation crises -0.0012, wars -0.0013
and now sovereign defaults -0.0022 [-0.0045, -0.00001] exclude zero, all
small losses; no reversion +0.0011, unpooled +0.0005: level. For the
record, food price shocks (outside the primary) turn from +0.006 to
-0.018 log [-0.050, +0.009], and their Brier from -0.004 to +0.009
[+0.0002, +0.024], a small harm. No verdict changes.

**Rerun after a base correction, 29 October 2026 (trial T8b, bead
bnm-82j.7).** The base moved: t48-world-norel (bnm-wns) in place of
t44-world-norel. The registered result and the warm-up correction above
stand as run. The same command with --run t48-world-norel on 7d5740b
(data/runs/sequencing/recalibrate-online-2003-2022-t48.tsv, run
t48-world-norel-online-registered; the same code on t44-world-norel
reproduces the 0cf-fix file exactly): (1) online against the raw base,
financial and social pooled, log -0.0003 [-0.0020, +0.0012]: level
(registered +0.0001, after bnm-0cf +0.0002); (2) Brier -0.0001 [-0.0006,
+0.0004]: no harm; (3) per kind, inflation crises -0.0010 and wars
-0.0013 exclude zero, sovereign defaults -0.0026 [-0.0061, +0.0004] no
longer does; no reversion +0.0005, unpooled +0.0003: level. Food price
shocks and market crashes, the two kinds the correction moved, are
outside the primary: food price shocks +0.0095 log [-0.0010, +0.0270],
Brier -0.0003 (the small harm after bnm-0cf is gone); market crashes
-0.035 [-0.239, +0.105]. **No verdict changes: level, no harm.**
Watch-list reading (bnm-82j.1, descriptive; v2 does not fit a
recalibration, which adds no group to draw nulls of): hard top 10
+0.000 [-0.001, +0.001] against t48-world-norel.

## Pre-registered, 23 October 2026: named stock x trigger interactions and the principal contradiction (trial T3)

Registered before any outcome after 2002 is read for this test. T7
(docs/T3-PAIRS.md) fitted shallow boosted trees on the design years: out
of era they never beat a logit with the same inputs, and their stable
interactions were mostly not theory's. The pairs, chosen on the design
years (three by T7's stated rule, the first named in advance from
Greenwood et al. 2022): credit gap x house-price growth (banking, +);
youth bulge x world grain gap (financial kinds, -); youth bulge x
neighbourhood conflict (financial kinds, +); technology gap x grain gap
(political kinds, +). tools/bnm/interactions, the year node over the
recalibrated base, origins 2003-2020, every scale, centre and fit from
1960-2002; on the design years none passed Holm (lowest p 0.055) and
the argmax lost to the mean (tau-hat 0).

**Tests**, one-sided 5%, 200 permutations of whole country series:
1. (primary) each pair's gamma in its stated sign, Holm over the four;
2. the principal contradiction: in the financial group, the argmax of
   the gauges against their plain mean, log per test row, the lower end
   of the 90% interval above 0; if not, the principal contradiction (one
   dominant gauge) is falsified at this resolution;
3. for the record: tau's profile on the test rows, the placebos, the
   gauges against the states' and triggers' levels alone.

**Results, 23 October 2026 (trial T3).** Origins 2003-2020:
1. **The four pairs** (primary, Holm): credit gap x house prices +0.004
   (z 0.14, p 0.46; on the design years +0.196, p 0.060); youth x grain
   gap +0.079 against its stated sign (p 0.77); youth x neighbour
   conflict +0.018 (p 0.34); technology gap x grain gap +0.576 (z 1.3, p
   0.22). None passes.
2. **The principal contradiction**: argmax against the mean, financial
   group, log per row: -0.0013 [-0.0017, -0.0008]. The single dominant
   gauge loses to the mean of the gauges: falsified at this resolution.
3. For the record: the mean of the financial gauges over the states'
   and triggers' levels alone +0.0012 [+0.0008, +0.0016]; tau-hat 0.5,
   chosen out of sample 0 (the mean); the political group has one gauge
   per row, tau not identified.

The credit-house interaction that kept appearing on the design years
(T2's record, T3's design) is gone in 2003-2022, on 36 banking onsets.
Interactions as named do not carry onsets past the recalibrated base;
the gauges help a little only averaged together, which is the opposite
of one principal contradiction steering the rest.

**Correction after the outcome, 28 October 2026 (trial T3, bead
bnm-0cf).** A fix made after the result was seen; the registered result
above stands as run. The recalibrations and scales fitted on 1960-2002
held the base's warm-up rows: before the year node has read a published outcome of a kind it sits
at its prior, p near 0.5 for every country (the origins whose fit-cut
is before the kind's first origin: currency crises 1961-1964, inflation
crises 1960-1963, market crashes 1961-1963, protest and violence surges
1979-1981, food price shocks 2003-2006). bnm.warm-up now keeps those
rows out of every fit and recalibration. Rerun on the fixed code
(data/runs/sequencing/interactions-t3-test-0cf-fix.tsv; the unfixed code
reproduces the registered file exactly): the four pairs and the financial
group's principal contradiction are unchanged to the digit (none passes
Holm; argmax against the mean -0.0013 [-0.0017, -0.0008]: falsified).
Only the political group's record moves: its gauges' levels over the
recalibration -0.0011 [-0.0021, +0.0008] (was -0.0004), the pairs over
the levels -0.0007 (was -0.0003). No verdict changes.

**Erratum, 23 October 2026 (trials 44, 45 and T0).** The design-year
diagnostics that chose trial 44's links and warnings and trial 45's
states (bnm.relation-diag, bnm.ews-diag, bnm.state-diag) kept year-end
origins through 2002, whose outcomes (an onset within (t, t+2]) run to
2004: origins 2001-2002 read two years of the test era. The power audit's
year-shock spreads read the same rows. Fixed (bnm.relation-diag/
design-origin?: the window must close by the end of `to`); the selections
recorded above stand as run. Both trials were level against their
benches, so the leak bought them nothing visible; it is recorded so their
selections are not read as clean. Found by the T6 tool's author.

**Correction, 28 October 2026 (trials 44 and 45, bead bnm-0cf).** The
same diagnostics read the base's warm-up rows as offsets: before the
year node has read a published outcome of a kind it sits at its prior,
p near 0.5 for every country (currency crises 1961-1964, inflation
crises 1960-1963, market crashes 1961-1963, protest and violence surges
1979-1981). bnm.warm-up now leaves them out. Rerun on t44-world-norel
(origins 1960-2000, as the erratum above fixed them), unfixed against
fixed code (data/runs/sequencing/graph-t45-0cf-{base,fix}.edn,
ews-t45-0cf-*, states-t45-0cf-*): the warnings (growth collapses, log
variance of growth) and the states (coups: arable land; fiscal
consolidations: grain and rice use; wars: tension's expected progress;
not trial 45's three, for reasons outside this fix) are the same either
way. The links are not: of the four edges the
current code keeps without the fix (conflict onsets to violence surges
and to war onsets, growth collapses to protest surges, inflation crises
to banking crises), the two into protest and violence surges, whose
warm-up rows are 1979-1981, do not pass with it. Trial 45's world run
read graph-t45 with five edges, three of them now unsupported (violence
surges to war onsets already fails on the current code without the
fix); trial 44's links were
chosen by the earlier, unclustered test and cannot be rerun as chosen.
Both trials were level against their benches; their world runs were not
rerun (hours each), so whether their scores move is open.

**Correction after the outcome, 28 October 2026 (trial 45, beads bnm-xxe
and bnm-5r4).** Fixes made after the result was seen; the registered
result above stands as run. Trial 45's world runs were rerun as
registered (the same graph, warning and states files, the same
commands) on the fixed code, in a clean worktree: first at 421a080 (the
warm-up fix, bnm-0cf), then at c5a5480 (also the coupling fix, bnm-5r4:
bnm.lattice/couple-levels no longer couples nested relation groups
across levels). The bench rebuilt on the current code
(data/runs/t45fix-norel) matches t44-world-norel byte for byte, so the
registered bench stands.
- (a) **As registered** (graph-t45, five edges; data/runs/t45fix-world,
  t45cpl-world and their placebos): every forecast, weight, relation and
  joint file is byte-identical to t45-world and t45-world-placebo, under
  either fix. The warm-up fix changes the diagnostics that chose the
  links, not the world run; and the coupling bug never fired here. At
  the year and half-decade nodes, where the links nest, a nested model
  reads the :down inputs, whose width (coarser scales plus ancestors)
  differs at every level, and couple-levels couples only models of equal
  width; the quarters, where widths match, drop relations. The tests
  reproduce exactly: (1) Brier +0.00012 [-0.00021 +0.00050], log +0.00107
  [-0.00069 +0.00325]; (2) Brier -0.00002 [-0.00008 +0.00002], log
  -0.00399 [-0.00834 -0.00117]; (3) Brier +0.00014 [-0.00009 +0.00035],
  log +0.00088 [-0.00012 +0.00190]; (4) Brier +0.00010 [-0.00010
  +0.00034]. Watch-list rule (bnm-82j.1), hard onsets, top 10, the seven
  kinds: +0.005 [-0.020 +0.032] against the bench, +0.009 [-0.006 +0.026]
  against the placebo.
- (b) **Sensitivity, reported only: the two edges the fixed diagnostic
  keeps** (graph-t45-0cf-fix: conflict onsets to war onsets, inflation
  crises to banking crises; the warning and states as registered;
  data/runs/t45cpl2-world and its placebo, the same under the warm-up fix
  alone, t45fix2-*). Five kinds linked; protest and violence surges, now
  unlinked, match the bench exactly. (1) Brier +0.00007 [-0.00042
  +0.00061], log +0.00150 [-0.00114 +0.00474]: level (over the
  registered seven kinds +0.00005 [-0.00030 +0.00042]). Per kind only war
  onsets move (one parent fewer): Brier +0.00029 [-0.00014 +0.00083]; the
  banking loss stays, -0.00059 [-0.00132 -0.00003], its edge kept. (2)
  Joint forecasts, two edges: Brier -0.00002 [-0.00009 +0.00005], log
  -0.00199 [-0.00543 +0.00048], level: the registered log-score loss came
  with the dropped edges. (3) Against its placebo: Brier +0.00007
  [-0.00022 +0.00038], log +0.00111 [-0.00034 +0.00276]: level. (4)
  Against t44-world: Brier +0.00003 [-0.00020 +0.00027]. Watch-list rule,
  hard onsets, top 10, its five kinds: +0.020 [-0.012 +0.052] against the
  bench, +0.023 [-0.001 +0.051] against the placebo; neither a gain nor
  equivalent to nothing (upper end above +0.03).

The verdict does not change: trial 45 is level, as registered and with
only the supported links. What the fix removes is test 2's log-score
loss, which belonged to the unsupported edges.

Trial 44 cannot be rerun as chosen. Its seven edges came from the
earlier, unclustered test on rows that held the base's warm-up years;
that test is gone, and the fixed, clustered diagnostic keeps only two
edges, of which trial 44 has one (conflict onsets to war onsets). Its
registered result (level; joint log score a loss) stands as run, read
with links that the current diagnostic does not support. Trial 45's
sensitivity bounds what fewer links could have done: cutting five edges
to two leaves the verdict level. The coupling fix does not touch trial 44
or 43: their world runs nested the links at the same year and
half-decade nodes with the same widths rule (b8fdcfd, e796238), so the
nested models were never coupled there either.

## Pre-registered, 23 October 2026: early warnings reframed (trial T6)

Registered before any outcome after 2002 is read for this test. The
critical-slowing-down warnings mostly lowered risk on the design years;
the literature's explanations are post-crisis composition (high variance
marks countries just after a crisis) and stability breeding fragility
(Danielsson, Valenzuela & Zer 2018). tools/bnm/ews-reframed: five named
indicators with a + prior (ac1, log variance, their trends, own-part,
12-year windows over GDP growth, inflation and food inflation; and calm
duration, the years the countries' own-part volatility of growth,
inflation and credit has stayed below its own running median), pooled
within the financial and political groups, the year node of
t44-world-norel as offset, rows within three years after an onset of the
same kind at the country dropped (K = 3, fixed beforehand). On the design
years nothing passed Holm, and dropping the post-crisis rows made log
variance more negative (financial z -2.13 to -2.73), not less.

**Tests**, origins 2003-2020, 200 permutations of whole country series:
1. (primary) calm duration, + prior, pooled over the financial group,
   post-crisis rows dropped: one-sided permutation p < 0.05, Holm over
   the five indicators;
2. the composition reading: financial log variance negative on all rows;
   if it stays negative after the exclusion, composition is falsified
   again as the explanation of the wrong sign;
3. for the record: the other indicators, the political group, every kind
   with and without the exclusion; calm duration for banking crises and
   market crashes alone (directional, underpowered).

## Pre-registered, 23 October 2026: slow states over five years on 150 years of crises (trial T5)

Registered before any outcome from 1996 on is read (the tool strips every
JST crisis flag from 1996 as it reads the data, and refuses development
rows whose windows reach 1996). tools/bnm/half-decade, docs/T5-ERAS.md:
a JST crisis (crisisJST) starting in t+1..t+5 at year-end origins, 18
countries; five named terms read to t-1 (credit gap by one-sided HP on
JST loans, 3-year real house-price and equity growth, the current
account, credit x house as the product of positive parts) against a base
of history alone (the country's crisis rate so far, years since its last
crisis, the era's running world rate, the share of countries in crisis).
Development, leave one era out over pre-WWI, interwar, Bretton Woods and
1972-1995: the states lost to history in every era (pooled -0.0316 log
per row, permutation p 0.93), with every term in its expected sign when
fitted on all of it; the gain does not rise with the lead. Two changes
were made after seeing development results: states clipped at 3 SD and
the interaction as positive parts (a Weimar artefact), and the
permutation p as the criterion (the block bootstrap passed a null state
14% of the time on twenty origin years).

**Tests**, fitted on all development rows, scored on origins 1996-2015:
1. (primary) the joint model's log gain over history, one-sided
   permutation p <= 0.05;
2. equivalence: 80% power needs OR about 2.3 per SD (a gain of about
   +0.03 log per row); a level result rules out only effects that large,
   not the literature's 1.2-1.5;
3. for the record: Holm over the five terms, the rise from leads 1-2 to
   4-5, Brier, the debt-service proxy, the year-demeaned variant.
Prediction from development: level or negative.

**Results, 23 October 2026 (trial T5).** Fitted on 1,715 development rows,
scored on 318 rows (origins 1996-2015, 66 onsets):
1. **The joint model over history** (primary): log gain +0.0455 [-0.0383,
   +0.1786], Brier +0.0108; permutation p 0.015 (null mean -0.0168),
   placebo -0.0228: **passes**.
2. Equivalence does not arise; the gain is above the +0.03 the test era
   could resolve, but its interval is wide.
3. Terms, Holm over five: house-price growth +0.0268 (p 0.005, Holm
   0.025) passes; the credit gap +0.0515 (p 0.025, Holm 0.100) does not
   after Holm; equity, the current account and credit x house level. The
   lead profile does not rise (h4-5 less h1-2 -0.0287, p 0.67): the
   credit gap is flat-to-falling with the lead, house prices rising.

What it is and is not. The test era's crises are mostly one global
event: of the 66 onset windows most sit around 2007-08, so this is close
to one draw of the world, and the permutation of country series does not
resolve that. Within it, the countries with credit and house-price booms
were the ones that fell, the Schularick-Taylor and Jorda-Schularick-
Taylor finding, against a base of history alone; after losing on every
earlier era out of sample. It does not say slow states gain with the
horizon, and it does not carry to the lattice: the lattice's banking base
already reads JST house prices and the stock-flow term (bnm.macrohistory),
which is why the same states added nothing over it at the year node (T1,
T2, T3, T45). The financial cycle is real in the record where it is
measured; the event model already has it.

**Results, 23 October 2026 (trial T6).** Origins 2003-2020: (primary)
calm duration, financial group, post-crisis rows out: +0.106 (z 0.90,
placebo z 1.02, permutation p 0.94): fails. Financial log variance is
positive in the test years (all rows +0.294, z 1.91; post-crisis out
+0.282, p 0.35), the sign the design years lacked; with its placebo as
strong (z 1.71), it is not specific to the country. Early warnings, as
reframed, do not carry onsets in either period.

**Rerun after a base correction, 29 October 2026 (trial T6, bead
bnm-82j.7).** The offset base moved: t48-world-norel (bnm-wns) in place
of t44-world-norel; of the financial group's kinds, market crashes moved
most. The registered result stands as run. The current code on
t44-world-norel does not reproduce data/runs/sequencing/ews-t6-test.tsv:
calm duration reads private credit straight from data/slow/series.tsv,
and bnm-as1 dropped its WDI zeros (ROU 1993-95, GNB 2002), which reach
every country's own part through the common factors. Only the
calm-duration lines differ, with the same rows; on the old file
(data/slow/series.tsv.pre-as1) the current code reproduces the
registered output exactly. The t47 entry below lists T6 as reading the
zeros only through its offset; it also read them through this input.
The same command (--run t48-world-norel --from 2003 --to 2022) on
2e9a074, the corrected series, first the corrected series alone on
t44-world-norel (data/runs/sequencing/ews-t6-test-as1.tsv), then t48
(data/runs/sequencing/ews-t6-test-t48.tsv):
1. (primary) calm duration, financial group, post-crisis rows out:
   +0.114 (z 0.94, placebo z 1.04), p 0.935 / **+0.098 (z 0.83, placebo
   z 0.94), p 0.950: fails** (registered +0.106, p 0.94); nothing passes
   Holm in either group, with or without the exclusion;
2. financial log variance on all rows +0.300 (z 1.95, placebo z 1.76),
   post-crisis out +0.288 (z 1.75), p 0.33 (registered +0.294 and
   +0.282): positive, the composition reading has no wrong sign to
   explain, and the placebo is as strong;
3. calm duration alone, post-crisis rows out: banking crises -0.068 (z
   -0.68, p 0.31; registered -0.019, p 0.19), market crashes -0.286 (z
   -0.93, p 0.20; registered -0.299, p 0.23).
**No verdict changes.** The watch-list rule v2 does not fit the trial's
shape (score tests with permutation nulls, no top-k list), so no v2
reading is given.

## Pre-registered, 23 October 2026: robustness of the extra dimensions (trial T9)

Registered before any outcome after 2002 is read for this test. Trials
45 and T1-T4 measured the mean and found the stock-flow states level or
equivalent to nothing over the base; extra dimensions may still hold
skill up when other inputs fail or regimes shift (docs/T9-ROBUSTNESS.md,
tools/bnm/robustness). B is T4's recalibrated base, B+D adds T4's 13
states, B+P' their coverage-matched placebo; dropout is approximated by
a ridged logit on the lattice year node's named gauges (G), knocked-out
inputs entering as unknown, the states never knocked out. On the design
years (four eras left out in turn) the states cut the loss when the slow
gauges, a random fifth of countries' data, or publication timeliness
were knocked out (degradation ratio 0.74-0.83, about 0.10 below the
placebo), and steadied the base's calibration across eras (slope SD
0.148 against 0.199, 0.024 below the placebo).

**Tests**, fitted on 1960-2002, scored on origins 2002-2020 in four eras
(2002, 2007, 2012, 2017), log score, one-sided 5%, Holm over five:
1. dropout, slow gauges: ratio(D) - ratio(P') < 0;
2. dropout, a fifth of countries dark: the same;
3. dropout, publication lags doubled: the same;
4. calibration drift: drift(B+D) - drift(B+P') < 0;
5. worst era: worst-era(D) - worst-era(P') > 0.
**Keep the dimensions** if at least one of 1-4 passes after Holm and none
shows harm (the worst era's lower end below -0.002 log); 5 alone is not
enough. Passing dropout keeps them as a fallback against failing inputs,
passing calibration as a stabiliser; neither claims a higher mean. For
the record: ratio(D) < 1 per scenario, the event-stream scenario, the
cell minimax and decile, the worst kind, the era spread, Brier.

**Results, 23 October 2026 (trial T9).** Fitted on 1960-2002, scored on
origins 2002-2020, log score, 90% intervals (the one-sided 5% bound):
1. **Dropout, slow gauges**: ratio(D) 0.715 against the placebo's 0.910,
   D - P' -0.196 [-0.545, -0.094]: passes (bootstrap z about 3.2, inside
   Holm's 0.01).
2. **Dropout, a fifth of countries dark**: 0.721 against 0.894, D - P'
   -0.173 [-0.351, -0.098]: passes (z about 3.8).
3. **Dropout, lags doubled**: -0.315 [-1.18, +1.10]: fails (the base
   barely loses when lags double in these years).
4. **Calibration drift**: D - P' +0.004 [-0.073, +0.058]: fails; the
   design years' steadying does not recur.
5. **Worst era**: D - P' -0.00007 [-0.0025, +0.0006]: fails.
Harm check: the states' worst era against the base, -0.00053 [-0.0047,
-0.0002], has its lower end below -0.002 log. **By the registered rule
the dimensions are not kept**: two dropout tests pass after Holm, but
the harm bound is crossed.

What it shows. When the base's slow gauges go dark or a fifth of the
countries lose their data, the stock-flow states hold back about a
quarter of the loss, about a fifth better than placebo states with the
same coverage, out of sample as on the design years: the robustness the
extra dimensions were thought to give is real for failing inputs. It is
bought with a small cost in the worst era that the test years cannot
rule out (the interval is wide; the placebo's cost is similar). They
stay out of the base; held ready as a fallback when inputs fail, which
is a use the event model can switch on when a source goes dark.

**Correction after the outcome, 28 October 2026 (trial T9, bead
bnm-0cf).** A fix made after the result was seen; the registered result
above stands as run. The fits on 1960-2000 held the base's warm-up rows:
before the year node has read a published outcome of a kind it sits at
its prior, p near 0.5 for every country (here currency crises 1961-1964,
inflation crises 1960-1963, market crashes 1961-1963). bnm.warm-up now
keeps them out of every fit. Rerun on the fixed code with the registered
penalties and kinds (data/runs/sequencing/robustness-t9-registered-0cf-fix.tsv;
the unfixed code reproduces the registered file exactly):
1. dropout, slow gauges: ratio(D) 0.717 against 0.915, D - P' -0.198
   [-0.556, -0.096]: passes;
2. dropout, a fifth of countries dark: 0.723 against 0.897, -0.175
   [-0.361, -0.098]: passes;
3. dropout, lags doubled: -0.349 [-1.12, +1.12]: fails;
4. calibration drift: +0.004 [-0.073, +0.058]: fails;
5. worst era: -0.00005 [-0.0025, +0.0006]: fails.
Harm check: the states' worst era against the base -0.00051 [-0.0047,
-0.0002], the lower end still below -0.002 log. **The verdict stands:
the dimensions are not kept**; the two dropout passes and the harm
crossing are both unchanged by the fix.

**Correction after the outcome, 29 October 2026 (trial T9, bead
bnm-0z5).** The dark draws were seeded at 1000003 (seed + d) + t, and
augur.math/rng fills its state linearly from the seed, so each country's
key across years was a Weyl walk: its dark years were not independent
(dark-year counts per country spread 1.65 to 2.60 over draws, against
2.19 for independent years). robustness/dark-countries now seeds from
augur.math/rng-mixed (augur v0.13.0). Rerun on the fixed code as above
(data/runs/sequencing/robustness-t9-registered-0z5-mixed.tsv): every
line but the dark scenario's is byte-identical to the 0cf-fix file.
2. dropout, a fifth of countries dark: ratio(D) 0.702 against 0.888,
   D - P' -0.186 [-0.416, -0.100]: passes (was -0.175 [-0.361, -0.098]).
The worst era and the harm check do not use the dark sets and are
unchanged (-0.00051 [-0.0047, -0.0002]). **The verdict stands: the
dimensions are not kept.**

**Rerun after a base correction, 29 October 2026 (trial T9, bead
bnm-82j.7).** The base moved: t48-world-norel (bnm-wns) in place of
t44-world-norel; of T9's ten kinds, market crashes moved most. The
registered result and the corrections above stand as run. The same
command with --run t48-world-norel on 7d5740b, dark draws from mixed
seeds (data/runs/sequencing/robustness-t9-registered-t48.tsv):
1-3. the dropout tests read the gauges route, which does not read the
   base's forecast, and are byte-identical to the 0z5-mixed file: slow
   gauges -0.198 [-0.556, -0.096] passes, dark -0.186 [-0.416, -0.100]
   passes, lags doubled -0.349 [-1.12, +1.12] fails;
4. calibration drift: +0.006 [-0.065, +0.055] (was +0.004): fails;
5. worst era: -0.00007 [-0.0013, +0.0010] (was -0.00005): fails.
Harm check: the states' worst era against the base -0.00054 [-0.0035,
+0.00007] (was -0.00051 [-0.0047, -0.0002]); the lower end is still
below -0.002 log, so the bound is still crossed. **The verdict stands:
the dimensions are not kept.** The watch-list rule v2 does not fit the
trial's shape (offline ridged fits on the lattice and gauge routes, no
group at a node), so no v2 reading is given.

## Pre-registered, 23 October 2026: cells against provinces, with what is in the cells (bead bnm-3ka.11)

Registered before any row from 2015 on is scored by the new stage. The
October 2026 pilot (docs/RESEARCH.md) had only GDELT event coordinates,
and cells lost to provinces on both measures chosen then. Now the cells
carry assets, extraction and processing intensity, gridded GDP, night
lights (spliced at 2013/14), built-up area, population and EDGAR sector
emissions (data/derived/cells/features.csv, each year from data known by
the year before). tools/bnm/cells-vs-provinces reuses the pilot's event
runs (a5p-gdelt for provinces, a5p-gdelt-a5 for cells: events from 1979,
scored 2015-26 every 13 weeks, 100 simulations) and adds a lattice stage
that reads 20 gauges per node (the 11 cell-profile measures and the 9
feature columns, z-scored within the year over the arm's nodes, capped at
5, missing as 0), offset by the event model's log odds, with a learned
recalibration and a quiet flag; discount 0.99, forget 0.99, prior
variance 0.1; online, from resolved rows only; trained on all of a
level's violence rows, scored on the quiet ones. Lights growth is left out
in 2016 (the splice year), both lights gauges from 2016 for the parts the
splice cannot join. Arms: provinces without features (the pilot's raw
numbers), provinces with features, cells with features, cells with
features shuffled among a country's cells (seed 1 the placebo, 19 seeds
the permutation distribution).

**Tests**, violence escalation from quiet at the sub-national level,
ROC AUC and Brier skill against climatology, 2015-26:
1. (primary) cells with features beat provinces with features on both
   measures (point estimates, as in the pilot; 95% paired bootstrap over
   periods reported);
2. (primary) cells with features beat the seed-1 placebo on both, and
   all 19 shuffles on both (permutation p <= 0.05);
3. for the record: provinces with features against the pilot baseline,
   the stage with gauges zeroed (recalibration alone), the group weights.
Cells and provinces are scored on different rows, as in the pilot. Cells
are adopted as the unit inference reads only if both primaries hold.

## Pre-registered, 23 October 2026: asset-growth divergence (H4 of docs/ECONOMIC-CHARACTER.md)

Registered before any outcome after 2002 is read for this test.
tools/bnm/divergence-attribution (T4's method): the divergence stock
(log equity, valuation, house-price and wealth levels less each country's
trailing 25-year mean; and their composite, econ/divergence) for banking
crises and market crashes, against coverage-matched placebo states,
fitted on origins 1960-2000, scored on 2002-2020 (outcomes 2003-2022),
ridge penalties fixed on the design rotation. On the design years the
four gaps said nothing; the composite's timing (raw mode) gained over its
placebo, +0.0031 log [+0.0007, +0.0061], mostly for market crashes, with
its penalty chosen on the rotation it was scored on.

**Tests**, log score, kinds pooled, one-sided 5%:
1. (primary) the composite, raw mode (timing): skill(S) - skill(P') > 0;
2. the composite, within mode (where): the same;
3. for the record: per kind, the four gaps, the credit and Keen gaps, Brier.

**Results, 23 October 2026 (H4).** Origins 2002-2020: (1, primary) the
composite's timing over its placebo, log -0.0051 [-0.0143, +0.0090]:
fails (market crashes -0.0100, banking crises -0.0003); (2) its "where"
-0.00002 [-0.0011, +0.0010]: level. The design years' timing signal does
not recur: divergence of asset values from their own past does not time
crashes or banking crises out of sample, as an attribution over placebos
with the same coverage.

**Correction after the outcome, 28 October 2026 (H4, bead bnm-0cf).** A
fix made after the result was seen; the registered result above stands
as run. The fits on 1960-2000 held the base's warm-up rows: before the year node has read a published outcome of a kind it sits
at its prior, p near 0.5 for every country (the origins whose fit-cut
is before the kind's first origin: currency crises 1961-1964, inflation
crises 1960-1963, market crashes 1961-1963, protest and violence surges
1979-1981, food price shocks 2003-2006). bnm.warm-up now keeps those
rows out of every fit and recalibration. Here
that is market crashes at 1961-1963. Rerun on the fixed code
(data/runs/sequencing/divergence-h4-registered-0cf-fix.tsv; the unfixed
code reproduces the registered file exactly): (1) the composite's timing
over its placebo, log -0.0053 [-0.0144, +0.0086]: fails; (2) its "where"
-0.00002 [-0.0011, +0.0010]: level. No verdict changes.

**Rerun after a base correction, 29 October 2026 (H4, bead bnm-82j.7).**
The base moved: t48-world-norel (bnm-wns) in place of t44-world-norel,
and market crashes, one of H4's two kinds, moved most. The registered
result and the warm-up correction above stand as run. The same command
with --run t48-world-norel on 2e9a074
(data/runs/sequencing/divergence-h4-registered-t48.tsv; the same code on
t44-world-norel reproduces the 0cf-fix file exactly). Both tests compare
M_S with M_P', which do not read the base, on the same rows, so they are
byte-identical: (1) the composite's timing over its placebo, log -0.0053
[-0.0144, +0.0086]: fails; (2) its "where" -0.00002 [-0.0011, +0.0010]:
level. Only the record with the base moves: in raw mode the recalibrated
base's own skill on market crashes is -0.022 [-0.141, +0.064] (was
+0.014), and the composite over it, coverage-matched, -0.0022 [-0.0057,
+0.0005] pooled (was -0.0012). **No verdict changes.** The watch-list
rule v2 does not fit the trial's shape (offline ridged fits, no top-k
list), so no v2 reading is given.

**Results, 23 October 2026 (cells against provinces, bnm-3ka.11).**
Violence escalation from quiet, 2015-26, 46 periods:

| arm | AUC | BSS |
|---|---|---|
| provinces, pilot | 0.859 | 0.024 |
| provinces, stage only | 0.807 | 0.039 |
| provinces, features | 0.810 | 0.039 |
| cells, pilot | 0.764 | 0.006 |
| cells, stage only | 0.718 | 0.024 |
| cells, features | 0.744 | 0.023 |
| cells, shuffled (19) | 0.734-0.748 | 0.021-0.023 |

1. **Cells against provinces, both with features** (primary): AUC
   0.744 against 0.810 ([-0.105, -0.022]), BSS 0.023 against 0.039: fails.
2. **Cells against their shuffles** (primary): above the seed-1 placebo
   on both, but above only 16 of 19 shuffles on AUC and 13 on BSS: fails.
3. Record: the stage recalibrates (BSS up at both levels) at a cost in
   ranking (AUC down from the event model's), since it learns only inside
   the window; within cells the features recover part of that (0.718 to
   0.744), little of it specific to the cell (the shuffles reach 0.748).

Provinces stay the unit inference reads. With what is now in the cells
they still lose: a province carries a place's political identity and the
event geocoding resolves to it, and the material and economic features,
which say where things are, do not add to the timing of violence that
the event streams carry. Cells stay the storage and the material layer
(principle 11); they are not the event model's node.

## Registered, 23 October 2026: the base rebuilt on current code (run t46-world-norel)

Not a trial of a change: no mechanism tested since trial 44 earned a
place in the base (45, T1-T9, H4, the cells pilot). This rebuilds trial
44's base, the world without relations (as t44-world-norel), on the
code as it stands after the session's work (shared code touched:
bnm.train's calibration call, bnm.geo, bnm.hypergraph, the diagnostics),
as a regression check. Expected: identical forecasts to t44-world-norel;
any difference is explained before anything else is read. Smoke first
(bnm.world-run --smoke true), then the full run, then bnm.world-score
against t44-world-norel. (Noted 10 November 2026: the smoke's other
flags were not recorded. A smoke with no model flag read trial 43's
graph; bnm.world-run now refuses one, so the base's smoke is
--without-relations true --smoke true, and the old bare smoke is --graph
data/runs/sequencing/graph-t43.edn --smoke true.)

**Result, 23 October 2026 (t46-world-norel).** Smoke: every kind's checks
passed. Full run on 02f980a: all thirteen kinds' forecasts.tsv are
byte-identical to t44-world-norel. The session's changes to shared code
left the event model's base exactly as it was; the base for the next
trial is t44-world-norel unchanged.

## Pre-registered, 23 October 2026: cell against country concentration under located shocks (bead bnm-1pq.27)

Registered before any output after 2010 is read. Does knowing where a
country's plants sit (A5 cells) predict its output loss from a located
shock better than spreading the shock over its land? tools/bnm/located-
shocks: USGS M6+ ShakeMaps, IBTrACS hurricane-force tracks, Dartmouth
flood polygons and the project's GDELT conflict onsets, mapped to cells
(a cell hit when half covered); exposure by bnm.cell-profiles/cell-cut
on the plants known the year before (steel, cement, refining dominate);
outcome the shock year and the next against the three years before, log,
clipped at 1; key-year effects; exposure threshold 0.05. On the design
shock years (1991-2009, 263 exposed country-years, floods most) neither
cell nor country beat no exposure; cell less country +0.00026 [-0.0002,
+0.0007].

**Test**, shock years 2011-2023, all hazards, leave-one-year-out squared
error, 1,000 bootstrap draws by year (seed 1), read only with at least 50
exposed country-years: cell-level exposure beats country-level (cell less
country below 0 at the upper end of the 90% interval); time and space
placebos and each hazard alone for the record. If not, countries stay the
unit for located supply shocks.

**Result, 23 October 2026 (bnm-1pq.27).** Shock years 2011-2023, 1,372
units, 225 exposed country-years: squared error no exposure 0.0545, cell
0.0546, country 0.0543; cell less country +0.00026 [-0.0000, +0.0005]:
cell does not win; countries stay the unit for located supply shocks.
Floods alone (207 exposed country-years) favour country exposure (+0.00037
[+0.0001, +0.0006]); quakes, cyclones and conflict too few to read. Space
placebos: 18 of 19 at or below the real footprints. With the plant lists
dated as they are (most plants only known from their snapshot years),
where a shock lands within a country does not yet tell its output loss
better than how much of the country it covers.

**Correction after the outcome, 29 October 2026 (bnm-1pq.27, bead
bnm-0z5).** The space placebos were not 19 independent placements: the
first draw of bnm.located-shocks/rng (Park-Miller) is linear in its seed,
and place-seed was linear in the run's seed and the year, so the
placebos for seeds 1-19 sat on an arithmetic progression of land cells
(France 2005, 400 cells: steps of 149) and a country's placebo moved 7
cells a year. Placement now draws from augur.math/rng-mixed on a mixed
place-seed; the bootstrap keeps its generator. Rerun as registered
(data/runs/located-test-0z5): every arm but the space placebos is
identical; space placebos 19 of 19 at or below the real footprints' +0.00026,
mean +0.00008 (was 18 of 19, mean +0.00007). The verdict stands.

## Registered, 23 October 2026: the watch-list rule (bead bnm-82j.1)

From the next trial on, every event-model trial reports, beside the
paired Brier and log scores, the watch-list rule of bnm.watchlist. For
each kind and year-end origin 2003-2022, the countries are ranked by the
year node's probability of an onset in (t, t+2], using only the forecast
made at the origin, with ties at the cutoff sharing the slots left.
**Primary for the use:** top-10 recall on hard onsets (no onset of the
kind in the ten years to the origin, the hard rows ranked among
themselves), pooled over origins within a kind and over the trial's
linked kinds with equal weights, the trial's run against its bench,
paired on the rows both forecast. **Interval:** a paired bootstrap of
origin years in blocks of two, 1,000 draws, 90% two-sided; a gain when
the lower end is above zero (one-sided 5%). **Equivalence:** equivalent
to nothing for the watch list when the upper end is below +0.03, the gain
the power check (bnm.watchlist --power) finds 80% of the time (+0.027
with thirteen kinds, +0.032 with seven); its null passes in at most 1% of
draws. The check found Brier no less powerful: the rule measures the use,
it is not more sensitive. **For the record:** k = 5, 20 and 10% of
countries; all rows; precision; mean lead and share of onsets caught; the
run against its placebo; persistence and climatology.

The base's standing (t44-world-norel, hard onsets, top 10): recall 0.274
against persistence 0.203 (+0.071 [+0.049, +0.092]) and climatology 0.237
(+0.037 [+0.021, +0.058]); it trails only on sovereign defaults and
fiscal consolidations. Rescoring trials 42-45, the gauge fix and T8b on
this rule (a record, not a test): none gains on the primary; trials 44
and 45 gain against their placebos on short lists, which says the
scrambled inputs harm the ranking, not that the real ones help.

## Registered, 29 October 2026: the watch-list rule, second form (bead bnm-82j.6)

Supersedes the rule above (bnm-82j.1) for every trial registered from
now on; trials already run keep their registered verdicts. Why: the
fast-signal review (trials FSa-FSc) found that any change at a node
reshuffles the year node's top ten, and that the rule's interval, which
resamples origins with both models fixed, cannot see it: a group of
nine noise inputs "gained" +0.014 [+0.005, +0.021] (p 0.001). With the
noise drawn independently (the correction under the fast-signal review)
the reshuffle is still there: groups of one shape, holding nothing,
spread by more than the origin bootstrap allows for. A gain has to beat
groups of the candidate's own shape, not the bare base; and the harm
bound, crossed by every group, measures the cost of adding one.

**The rule** (bnm.watchlist --v2; offline refits, bnm.shock-trial
--nulls). The use and its primary are as before: top-10 recall on hard
onsets of the year node's forecasts, origins 2003-2022 (or the trial's),
the trial's kinds weighed equally, on the rows every run forecasts.
1. **Null draws.** R = 8 runs of the candidate's shape (the same inputs
   at the same nodes) holding nothing, on the candidate's code and
   bench: its own inputs with the link to place or time broken, four
   with the countries deranged (bnm.fast/derange-source, seeds 1-4, a
   uniform derangement from augur.math/rng-mixed) and four read whole
   years earlier (bnm.fast-run/shift-months: 24, 36, 48 and 60 months).
   An input with no country dimension takes eight time shifts; an
   offline refit takes eight derangements (the registered placebo's seed
   and seeds from augur.math/seed-of).
2. **Gain.** G, the candidate's recall less the draws' mean, with the
   interval G +/- t(0.95, R - 1) x sqrt(sd0^2 (1 + 1/R) + vb): sd0 the
   draws' spread (a fresh draw differs from the mean of R by that much),
   vb the variance of G over 1,000 draws of two-year blocks of origins
   (seed 1). A gain when the lower end is above 0 (one-sided 5%).
3. **Equivalence.** Equivalent to nothing when the upper end is below the
   gain the test finds 80% of the time at the trial's own spread
   (sqrt(sd0^2 + vb) of its draws, bnm.watchlist/null-mde): +0.015 for
   thirteen kinds on t48-world-norel (spread 0.0050).
4. **Harm.** The year nodes' pooled paired Brier against the bench
   (bnm.paired/world-pair), the candidate less the draws' mean, in the
   same interval (vb from the candidate's own interval). Harm when the
   upper end is below 0: the candidate costs more than groups of its
   shape that hold nothing. No harm shown when the lower end is at or
   above -0.0003. Adopted when 2 holds and 4 finds no harm.
5. **For the record**: the bnm-82j.1 reading against the bench; k = 5,
   20, 10%, all rows, precision and lead; noise draws of the same shape
   (bnm.fast-run --family noise --seed s). The FS trials' placebo test is
   subsumed: the derangement draws are eight placebos.

**Why shuffled, not noise.** On t48-world-norel the four noise draws (9
standard normals at the quarters) average +0.002 recall (sd 0.006) and
-0.0019 Brier; the eight shuffled commodity draws -0.007 (sd 0.003) and
-0.0003. Noise that keeps none of the inputs' distribution, coverage or
persistence is biased one way on recall and the other on Brier. Noise
draws are reported, not the null.

**Why R = 8.** Each draw is a world run: 15-17 minutes on this machine
two at a time (thirteen runs in 102 minutes), so eight cost about an hour
a trial. More buy little: the detectable gain at spread 0.0050 is +0.019
with R = 4, +0.015 with 8, +0.014 with 12, +0.0135 with 16 (the floor,
spread x (z0.95 + z0.80), is +0.012). A cheap approximation was tried and
is not used: the bench's year-node log odds perturbed by independent
noise the size a draw moves them (sd 0.07-0.22) does not track full runs
(on six pre-t48 draws a mean of -0.008 to -0.023 where the runs gave
-0.018 to +0.014): what a group does is learned over sixty years at
every quarter node and reaches the year only through the states passed
up, so no partial rerun is the same object either.

**Power** (bnm.watchlist/null-power: R normal draws and a candidate of
true gain g over their mean, each of spread s, the rule applied as
written; 10,000 simulations): at s = 0.0050 and R = 8, power 0.52 at
+0.01, 0.96 at +0.02, 80% at +0.015; at s = 0.0079 (noise and shuffled
pooled, the widest seen) 0.29, 0.69 and 0.94 at +0.01, +0.02, +0.03,
80% at +0.023. bnm-82j.1's +0.024 was a gain over the bench with
outcomes redrawn; this is a gain over groups of the same shape.

**Calibration** (data/runs/sequencing/watchlist-v2-calibration-
{shuffled,noise,all}.txt). Base **t48-world-norel**, which bnm.fast-run
--family none reproduces byte for byte; code 3009fba with this rule's
changes (draws identical under augur v0.13.0's rng-mixed, checked);
data/slow/series.tsv after bnm-as1; thirteen kinds, origins 2003-2022.
- The shuffled draws less the bench: derangements -0.0096, -0.0071,
  -0.0052, -0.0039; time shifts -0.0088, -0.0024, -0.0112, -0.0090. Mean
  -0.0071, between-draw sd 0.0031, origins' sd 0.0039. Every one loses
  recall: what bnm-82j.1 would call a loss is the cost of the shape.
- Size, each draw in turn the candidate against R of the others: 1 of
  280 at R = 4, 0 of 56 at R = 6 (the eight shuffled); 21 of 1,980 at
  R = 8 with the four noise draws added (0.011), though noise and
  shuffled draws are not exchangeable; on trial SS's refits, 40
  derangements from mixed seeds, 34 of 12,000 at R = 8 (0.003). The
  origins' term is what holds the size: without it 0.054-0.071 within
  the shuffled draws and 0.10-0.13 with noise mixed in. False passes are
  at most 1.1% wherever measured: the rule is conservative.

**Descriptive readings, not verdicts** (data/runs/sequencing/
watchlist-v2-readings.txt). Past candidates against their own benches,
their bnm-82j.1 interval as vb, against the eight shuffled draws on
t48-world-norel: a different base, and FSb (six inputs), FSc (four,
2020-2022) and trial 45 (other nodes) are other shapes, so the null is
approximate. Gain over the null mean, 90%:
- FSa +0.002 [-0.009, +0.013], rerun -0.002 [-0.018, +0.014]; FSb
  +0.002 [-0.014, +0.018], rerun +0.003; FSc 2020-2022 +0.004 [-0.007,
  +0.014], rerun -0.008; trial 45 (seven kinds) +0.009 [-0.022, +0.040]:
  none gains, as registered. Harm (Brier over the null mean): none costs
  more than the draws; trial 45 shows no harm.
- Placebos: FSa -0.005, FSb +0.007 [-0.002, +0.016], trial 45 -0.000;
  FSc's placebo "gains" +0.016 [+0.005, +0.027] on three origins in two
  blocks, the coarse record FSc's registration warned of.
- Noise: fs-noise and fs-noise-fix (the lattice) "gain" +0.019 and
  +0.021 over the shuffled draws, fs-noise-mixed +0.013 [-0.003,
  +0.029] does not: noise costs less recall than shuffled inputs, the
  reason it is not the null.
- Trial SS, refitted offline on its own base, against forty of its own
  derangements (data/runs/sequencing/shock-trial-test-v2.txt): S less the
  base -0.021, the draws -0.014 (sd 0.015: most refits with scrambled
  losses loses too), gain over their mean -0.007 [-0.041, +0.028], p
  0.63; log per row over B' -0.0045 [-0.0147, +0.0057] over the draws'
  mean: no harm beyond their cost. As registered, nothing.

**Amendment, 29 October 2026, before any trial is scored on this rule
(review of bnm-82j.6).** Two changes to the rule, two to the code that
reads it, and corrections to the numbers above. The descriptive
readings above stand: none used a time shift of a short series. Trial
reruns read on v2 from now on take A1 and A2.
- **A1, time shifts need history.** A shifted draw reads the input
  `m` months before the origin. Where that is before the series'
  first value the group has no input at any place and gets weight 0
  (bnm-wns), so at those origins the "null draw" is the bench, not a
  group of the candidate's shape. Port calls and chokepoint transits
  start in January 2019: for FSc's origins (2020-2022) a 24-60 month
  shift reads almost nothing. A time-shift draw is used only when the
  input is known at every shifted read over the scored origins (its
  operators' window included); otherwise the eight draws are
  derangements (seeds 1-8). An input with neither a country dimension
  nor that much history has no shuffled null and is reported as such.
  For 2003-2022 the commodities qualify (the Pink Sheet from 1960);
  the fx family does not (11 of the 33 countries' spreads start after
  1995) and takes derangements, as ports do (bnm.fast-run refuses
  --null shift for both).
- **A2, the adoption line.** "4 finds no harm" means the harm verdict
  is not harm (the upper end of the Brier over the null mean at or
  above 0); "no harm shown" (the lower end at or above -0.0003) is
  reported, not required. The year nodes' Brier against the bare bench
  is printed on the verdict line beside it, for the record: the
  relative test asks whether the inputs cost more than any group of
  their shape, not what adopting them costs, and a candidate as costly
  as noise (about -0.002) passes it.
- **Code.** bnm.watchlist --v2 now prints step 3 (equivalence, at the
  trial's own spread from its draws) and the verdict, which it did not.
  The calibrations (--calibrate, shock-trial --nulls) computed each
  draw's origins' variance against all the other draws and used it for
  every subset of R; it is now computed against the subset tested.
- **Size, recomputed** from the eight shuffled draws with the
  variance against the subset: 1 of 280 at R = 4, 0 of 56 at R = 6, 0
  of 8 at R = 7 (each draw against the other seven); all twelve
  (noise added) at R = 8, 1 of 720 (0.001), not 21 of 1,980 (0.011),
  which came from the variance against all eleven (trial SS's 34 of
  12,000 was computed the same way and is not rerun here). The rule's size is
  about 0-0.4%, not 5%. The prediction interval alone (sd0, t with R -
  1 df) is at its nominal size within the shuffled draws (0.057 at R =
  4, 0.054 at R = 6); the origins' term adds the part of a draw's noise
  that varies origin by origin a second time, since sd0 is measured on
  the same origins and already holds it. "The origins' term is what
  holds the size" is true only of noise and shuffled draws pooled,
  which are not exchangeable. The rule is valid and conservative; it
  is left as registered (a choice: without the origins' term the size
  is 5% and the detectable gain about +0.010).
- **Power, by planting gains** in the existing draws (each draw
  against the other seven, a gain g added): spread evenly over origins,
  0.25 at +0.010 and 1.00 at +0.015; added as whole onsets caught in
  random years (the origins' variance grows to 0.0046-0.0058), 0.29 at
  +0.010, 0.75 at +0.015, 0.96 at +0.020. 80% falls near +0.016, close
  to the registered +0.015.
- **Harm.** Each shuffled draw against the other seven: none is
  harm (0 of 8). The intervals of the Brier over the null mean are
  +/-0.0012 to +/-0.0026, so the test flags only a cost of about 0.002
  or more beyond the null, and no draw's lower end reaches -0.0003, so
  "no harm shown" is out of reach at this precision. Judged against the
  null, a candidate that costs as much as every draw passes. The
  shuffled draws cost little against the bench (-0.0014 to +0.0002,
  mean -0.0003), so this matters little with this null; it would with
  a costly one (noise costs about -0.002). Hence A2's bench line.
- **The correction under the fast-signal review** says draws of one
  shape "differ by more than the origin bootstrap allows for". Without
  the lattice they do not: the shuffled draws' sd is 0.0031 against
  their origins' sd of 0.0039, the noise draws' 0.0058 against 0.0053.
  What the null corrects is the shape's cost, -0.007 on the primary
  for every shuffled draw against the bench, not an excess of spread.
- **The null, otherwise.** Shifts by whole years keep the series'
  autocorrelation and season and read only the past. Commodity cycles
  run several years, so a 24-month shift may keep some of the signal;
  that biases the null mean up and the test against a gain. A
  derangement moves a country's inputs and their coverage together, so
  the set of countries with inputs is kept, not which country has
  them; for the commodities only the exposures move and the world
  prices keep their timing, so derangements test the place and shifts
  the timing. Shuffled over noise was argued from the null draws (the
  calibration files precede the readings, 00:42 and 00:55, registered
  together); the FS trials' results against the bench were known. The
  choice cuts both ways (the shuffled null's mean is 0.009 lower on
  recall, which helps a candidate, and 0.0016 higher on Brier, which
  does not) and no reading of a candidate turns on it.

**Amendment A3, 29 October 2026, before any trial is scored on this
rule: the origins' term is dropped.** No trial has a v2 verdict: the
FSa and SS readings under the rerun on t48-world-norel (2e9a074) and the
readings above are descriptive. A3 replaces step 2's interval, and step
4's with it; everything else stands, A1 and A2 included.
- **The gain.** G +/- t(0.95, R - 1) x sd0 x sqrt(1 + 1/R): sd0 the
  draws' spread, R - 1 degrees of freedom, a gain when the lower end is
  above 0 (one-sided 5%). vb, the origins' bootstrap variance of G, is
  no longer in the interval; bnm.watchlist --v2 prints it for the
  record.
- **Why.** The review of the amendment above (0d00b26, 70eb95f) found the
  interval counts a draw's noise twice. sd0 is the spread between null
  draws scored on the same origins, so the part of a draw's noise that
  varies origin by origin is already in it; vb adds that part again. A
  candidate is one more draw of its shape plus whatever it holds, and
  under the null it is exchangeable with the R draws, so sd0 sqrt(1 +
  1/R) is the whole spread of G. With both terms the size was 0-0.4%
  where 5% was registered, and the detectable gain +0.015 where the
  draws allow about +0.010.
- **The harm, by the same argument.** The Brier draws are scored on the
  same origins as the candidate, so their spread holds the origins'
  noise too, and the candidate's own bootstrap variance (step 4's vb)
  counts it twice the same way. The harm interval is the Brier over the
  null mean +/- t(0.95, R - 1) x sd0 x sqrt(1 + 1/R), sd0 the Brier
  draws' spread; harm when the upper end is below 0. Its size on the
  eight shuffled draws (each against R of the others) is 35 of 280 at R
  = 4, 7 of 56 at R = 6, 1 of 8 at R = 7: all of it one draw,
  wl2h-derange-4, whose Brier against the bench (-0.0014) is three times
  any other's, which reads as harm against every subset of the rest.
  That is 12.5%, above 5%, from one draw in eight (eight exchangeable
  normal draws give rates up to 0.13-0.14 one time in twenty); a false
  harm keeps a candidate out, the safe side. "No harm shown" (lower end
  at or above -0.0003) stays reported, not required (A2); at this
  spread (+/-0.001) it is now within reach.
- **The measured size** (data/runs/sequencing/watchlist-v2-calibration-
  a3-{shuffled,all}.txt, bnm.watchlist --calibrate on the existing runs,
  no new world runs), each draw in turn the candidate against R of the
  others: on the eight shuffled draws 16 of 280 at R = 4 (0.057), 3 of
  56 at R = 6 (0.054), 1 of 8 at R = 7 (0.125); R = 8 needs nine. With
  the four noise draws added (twelve; 300 random subsets a draw where
  there are more): 0.114, 0.103, 0.113 and 0.106 at R = 4, 6, 7 and 8,
  but noise draws gain +0.006 and +0.008 where every shuffled draw
  loses, so the twelve are not exchangeable and this is not the rule's
  size.
- **The critical value: t with R - 1 df, kept.** The size within the
  shuffled draws is above 5%: 5.7% at R = 4 and 5.4% at R = 6. The
  280 tests come from eight draws, so they are far from independent: on
  eight exchangeable normal draws, where t is exact, the same count gives
  a mean of 0.05 with 5-95% range 0-0.13 at R = 4 (0-0.14 at R = 6), and
  0.054 or more about 40% of the time. 0.057 is what t gives at its
  nominal size; a wider value (t at 0.955, 14 of 280; at 0.96, 12 of
  280) would be fitted to these eight draws. Decided on the null draws
  alone; no candidate's reading was computed under A3 before this.
- **Power** (planting gains in the shuffled draws, each against R of the
  others; bnm.watchlist/plant-onsets): spread evenly over origins, 0.61
  at +0.010, 0.84 at +0.012, 1.00 at +0.015 and +0.020 with R = 4;
  0.875 at +0.010 and 1.00 from +0.012 with R = 7 (8 tests). Added as
  whole onsets caught in random years the power is the same, test for
  test: pooled recall is linear in hits, so G moves by the gain wherever
  the onsets land, and with vb gone nothing in the test sees where.
  Simulated (bnm.watchlist/null-power, normal draws at the shuffled
  spread 0.0031): 0.71 / 0.94 / 1.00 at +0.010 / +0.015 / +0.020 with R
  = 4, 0.87 / 0.99 / 1.00 with R = 8.
- **The detectable gain** (80%): +0.009 at R = 8 (+0.0091 simulated;
  +0.0094 at R = 7, +0.0115 at R = 4), against the registered +0.015.
  Step 3's equivalence line is this at the trial's own spread, now sd0
  of its draws (0.0031 on t48-world-norel, thirteen kinds), not sqrt(sd0^2
  + vb) (0.0050). R = 8 stays: R = 12 buys +0.0086, R = 16 +0.0083.
- **Code.** bnm.watchlist/null-test takes no origins' variance;
  false-pass, the calibration (--calibrate) and bnm.shock-trial --nulls
  use the same interval for gain and harm, and the calibrations print the
  harm's size and the planted power.
- **Recalibrated with the code** (bnm.watchlist --calibrate, committed
  with these lines; the files named above; no world runs): the numbers
  above, and with the twelve draws (sd 0.0060) planted power 0.30-0.35 at
  +0.010, 0.50-0.62 at +0.015, 0.60-0.92 at +0.020 (R = 4 to 8) and a
  simulated detectable gain of +0.018 at R = 8: what a null of mixed
  shapes would cost. Harm's size with the twelve 0.094, 0.093, 0.079
  and 0.048 at R = 4, 6, 7, 8. On trial SS's forty refitted
  derangements (bnm.shock-trial --nulls 40, data/runs/sequencing/
  shock-trial-test-v2-a3-t48.txt), each against R of the others, 300
  subsets a draw: gain 0.066, 0.064, 0.060, 0.057 at R = 4, 8, 12, 16;
  log harm 0.048, 0.048, 0.046, 0.043. Above 5% on the gain, as on the
  shuffled draws; forty exchangeable normal draws give 0.026-0.085
  (5-95%) at R = 4 and 8 and 0.064 or more 22% of the time, and the
  forty are skewed (one draw at -0.054, 2.7 sd below the mean, pulls the
  mean down for the others). t with R - 1 df stands.

**Descriptive readings under A3, not verdicts** (no trial was scored
on v2; these are the rerun readings above recomputed). FSa on
t48-world-norel (bnm.watchlist --v2, data/runs/fs-a-t48/
watchlist-v2-a3.txt), against wl2h-derange-1..4 and wl2h-shift-1..4:
gain over the null mean +0.0012 [-0.0050, +0.0074] (was [-0.0115,
+0.0139]), p 0.36, no gain; the upper end is below the detectable gain
at its own spread, +0.0091 (spread 0.0031), so equivalent to nothing;
Brier over the null mean +0.00018 [-0.00083, +0.00119] (was [-0.00195,
+0.00232]), not harm, no harm not shown; year nodes' Brier against the
bare bench -0.00012 [-0.00182, +0.00144]; not adopted. Its placebo
(fs-a-placebo-t48): -0.0003 [-0.0064, +0.0059], equivalent to nothing;
Brier -0.00079 [-0.00180, +0.00022], not harm. Trial SS on
t48-world-norel against its forty derangements (bnm.shock-trial
--nulls 40, data/runs/sequencing/shock-trial-test-v2-a3-t48.txt): S
less the base +0.006, the draws -0.009 (sd 0.016), gain over their mean
+0.0151 [-0.0128, +0.0431] (was [-0.020, +0.050]), p 0.18, no gain; log
per row over B' against the draws' mean +0.0199 [-0.0229, +0.0628] (was
[-0.096, +0.136]), not harm. The readings move as the intervals narrow
and no conclusion changes: no gain, no harm, as before.

## Pre-registered, 28 October 2026: supply shocks as event triggers (trial SS, bead bnm-82j.3)

Registered before any outcome after 2002 is read for this test. Do
country-level supply shocks tell which countries to watch for food price
shocks, protest surges and violence surges beyond the base? The inputs
(bnm.supply-shocks, scripts/supply-shocks-data.py, nothing downloaded:
every source was already under data/raw) are, per country and year end
t, the share of its supply lost in three families, food (wheat, rice,
maize), fertiliser (N + P2O5 + K2O) and energy (oil, gas):
sum over products of the product's share of the family's use x the
country's import dependence x (sum over suppliers of the supplier's share
of its imports x the supplier's shortfall + sum over straits of the
strait's share of its imports x the strait's disruption). A shortfall is
1 less the supplier's latest value over its mean in the three years
before, counted only from 10%; a disruption is a strait's transits
against its own years before, relative to all straits'. Every value is
read only once known at t:

| input | source | known |
|---|---|---|
| food shortfalls | USDA PSD exports by market year | in the year (WASDE; current vintage, revisions not undone) |
| energy shortfalls | EI production (OWID) | a year after |
| fertiliser shortfalls | FAOSTAT nutrient exports | two years after |
| strait disruptions | PortWatch dry-bulk and tanker deadweight | in the year, from 2020 (2019 the first base) |
| supplier shares | FAOSTAT cereal matrix, BACI HS 31, 270900, 271111, 271121 | two years after, latest three data years |
| import dependence, use shares | FAOSTAT balances and fertilisers; EI | two years after; EI one |
| strait shares | bnm.chokepoints (BACI routed) | two years after (port structure from 2019, a look-ahead it records) |

Coverage of the base's countries, 2003-2022: food 170 (a loss above 1%
in 56% of country-years; non-zero from 1988, when the supplier shares
begin), fertiliser 174 (74%; from 1998), energy 78, the Review's
countries (29%; from 1998); export bans enter only
through the exporters' export shortfalls (no open ban record before 2022);
the OECD inventory's few fertiliser restrictions are not used. In the
scored years strait disruptions are non-zero only for 2020-2022 (the
Bosporus in 2022 -27% against all straits).

tools/bnm/shock-trial: the year node of t44-world-norel, refitted at each
origin on the rows whose outcome was published by then (origins to t - 2
- the catalogue's known-lag in years rounded up), from 1980, with a fixed
effect per fitted year and the scored year at the fitted rate's level:
B' (a + b logit p, which ranks as the base), S (B' plus the three
losses, log(1 + L / 0.01), standardized, ridge 10 per coefficient, fixed
in advance, not tuned), and two placebos: P (exposure reassigned: each
country reads another's losses, a random derangement, seed 1, keeping its
own coverage) and T (dates shifted: the losses of t - 6 at t). A kind
whose fitted rows hold fewer than five onsets is the base unchanged (food
price shocks start in 2004 with an 18-month lag, so they are refitted
from origin 2007).

On the design years (protest and violence surges, origins 1993-2000,
data/runs/sequencing/shock-trial-design.txt): S over the base, top-10
recall on hard onsets, +0.008 [+0.000, +0.019]; S over P +0.030 [-0.014,
+0.070]; log per row S less B' -0.0096 [-0.020, -0.003] (harm), Brier
-0.0010. A first design pass without year effects lost -0.10 to the base,
and so did its placebo: the losses' coverage grows with the years and the
fit read the onset rate's drift from it, so year effects went in before
anything else was looked at.

**Tests**, origins 2003-2022 (the watch-list rule's), kinds food price
shocks, protest surges, violence surges, the kinds equally weighed,
1,000 draws of two-year blocks of origins (seed 1), 90% intervals:
1. (primary) S against the base, top-10 recall on hard onsets: a gain
   when the lower end is above 0;
2. the gain is the shocks' only if S also beats P (lower end above 0),
   and the shocks do no harm: log per row S less B' with its lower end
   at or above -0.002;
3. pass when 1 and 2 hold; fail (equivalent to nothing) when the upper
   end of 1 is below +0.047, the gain the power check finds 80% of the
   time on these three kinds' hard rows (+0.041 at beta 0.5 with power
   0.76, +0.072 at 0.75 with 0.97; the null never passes; the rule's
   +0.03 is the bound for seven to thirteen kinds and is reported too);
   level otherwise;
4. per kind, the one-sided bootstrap p of S over the base, Holm over the
   three at 5%;
5. for the record: k = 5, 20, 10%; all rows; precision and lead; S
   against T; P and T against the base; Brier; log of S against the raw
   base; AUC per kind, all and hard rows; the base against persistence
   and climatology.

**Results, 28 October 2026 (trial SS).** Origins 2003-2022, run once
(data/runs/sequencing/shock-trial-test.txt), 90% intervals:
1. **S against the base** (primary), top-10 recall on hard onsets:
   0.230 against 0.254, -0.024 [-0.065, +0.012]. The upper end is below
   +0.047 (and below the rule's +0.03): **fail, equivalent to nothing**
   for the watch list.
2. S against the exposure placebo P +0.012 [-0.021, +0.039]; log per row
   S less B' -0.0024 [-0.020, +0.016]: no gain, and the no-harm bound
   (lower end at or above -0.002) is not met either; Brier -0.005
   [-0.021, +0.007].
3. Per kind, S less the base (Holm over three, none rejects): food price
   shocks -0.011 [-0.037, +0.003] (p 0.89), protest surges -0.020
   [-0.115, +0.059] (p 0.72), violence surges -0.040 [-0.102, +0.023]
   (p 0.89).
4. For the record: S over the time placebo T -0.021 [-0.050, +0.000]; at
   k = 5 S gains +0.022 [+0.000, +0.042] and beats P by +0.027, at k = 20
   it loses -0.025 [-0.049, -0.006], all rows k = 10 -0.025 [-0.041,
   -0.010]: of the eight list readings only hard k = 5 lies above zero
   and two lie below, no consistent sign. The base keeps its edge over persistence (+0.080) and
   climatology (+0.071). AUC, hard rows, base / S: food price shocks
   0.532 / 0.519, protest 0.743 / 0.752, violence 0.717 / 0.713. B' (the
   base refitted with year effects, scored at the fitted rate) is far
   worse than the raw base on log, -0.33 [-0.87, -0.04]: a constant level
   cannot follow the 2008 and 2022 waves of food price shocks; S and the
   placebos share it, so the paired S less B' is the clean contrast.

Supply shocks mapped through each country's import dependence, supplier
shares and strait shares do not tell which countries to watch for food
price shocks, protest or violence beyond the base. What they could carry
is limited by when it is known: fertiliser shortfalls arrive two years
late and energy one, strait disruptions exist only from 2020, and the
food shortfalls that are timely are mostly ordinary export swings. The
loss shares stay as inputs (data/derived/stocks/supply-shocks.csv), not
adopted into the event model; the timely route for the same shocks is
prices and traffic (bead bnm-82j.2).

**Review, 28 October 2026 (trial SS): two fixes made after the outcome
was seen.** The registered specification is unchanged; the run above
stands as the registered result.
1. The base's warm-up rows entered the refits. Before the year node has
   read any published outcome it sits at its prior, p near 0.5 for every
   country (food price shocks at origins 2003-2006, sd of logit p 0.005;
   protest and violence 1979-1981). For food price shocks these rows were
   all or most of the fitted rows at origins 2007-2012: the slope on
   logit p came out at -65 at 2007 (B' near 1 for every country, 17.8
   nats a row worse than the base) and negative at 2011-2012, and the
   pooled level they dragged down left B' at about half the base's rate
   for protest surges in every year. That, not the year effects, is most
   of B''s -0.33 on log. The fits now leave out rows whose origin's
   fit-cut is before the kind's first origin
   (data/runs/sequencing/shock-trial-test-fix.txt): primary -0.021
   [-0.064, +0.016] (was -0.024 [-0.065, +0.012]); S over P +0.009
   [-0.027, +0.039]; log S less B' -0.0077 [-0.018, +0.001] (the no-harm
   bound still not met); B' less the base on log -0.011 [-0.026, +0.002]
   (was -0.33); per kind food price shocks -0.003 [-0.012, +0.000],
   protest and violence unchanged; Holm rejects none. Same verdict: fail,
   equivalent to nothing. With real forecasts in the fit, food's slope is
   still negative at 2011-2012 (fitted on origins 2007-2008, when the
   base ranked food price shocks against their outcomes within the year),
   so B' does not rank as the base in those two years.
2. The verdict's bound was the rule's +0.03, not the registered +0.047.
   It now uses +0.047 and reports both; the verdict here is the same
   either way.

Known-year notes. USDA PSD's "Exports" is the local marketing year
(India's rice October to September, corn October to September or March
to February), so the value read at the end of t is mostly trade in t+1,
and in the current vintage it is the realized figure, not December's
WASDE forecast: a leak into the outcome window that favours S. Read a
year late instead (an unregistered sensitivity, post-fix code,
data/runs/sequencing/shock-trial-sens-psd-lag.txt), the primary is
-0.021 [-0.058, +0.015] and the one positive reading, hard k = 5, falls
from +0.021 [+0.002, +0.041] to +0.007 [-0.016, +0.029]: that gain was
the leak's. The verdict, a fail with a leak favouring S, stands. The
chokepoint structure's 2019 look-ahead changes nothing scored: strait
terms are non-zero only from 2020, after it. The Bosporus's 2022
disruption is 34% for dry bulk (20% for tankers) against all straits,
not the 27% the registration quotes (27% is its fall from 2021 alone).
Of the spot checks, the 2010 Russian wheat ban (Russia 76%, Ukraine 45%,
Kazakhstan 35% shortfalls) and India's rice ban (58% at 2008, nothing at
2007: its October 2007 ban falls in marketing year 2007/08, whose
exports fell only 7%) light up in their year; Vietnam's 2008 ban does
not (its exports rose). The 2022 shock reaches Egypt, Lebanon and
Turkey through the Bosporus, not through export shortfalls (Ukraine's
2022/23 wheat and maize exports are within 10% of the years before);
the 2022 fertiliser and gas cuts arrive only at 2024 and 2023, outside
the scored years, and the European gas cut never shows as a production
shortfall (Russia's 2022 gas output fell under 10%).

**Correction, 28 October 2026 (trial SS's power check, bead bnm-0cf).**
The power check that set the registered bound drew its outcomes from the
base's hard rows, food price shocks' warm-up rows (2003-2006, p near 0.5)
among them: coin flips on four years of a kind's rows. With them left
out (bnm.warm-up; data/runs/sequencing/shock-trial-power-0cf-fix.txt; the
unfixed code reproduces shock-trial-power.txt exactly) the gain found 80%
of the time is +0.051, not +0.047 (beta 0.5: power 0.79, gain +0.049).
The registered bound stays +0.047; the primary's upper end, +0.012
registered and +0.016 after the review, is below either. The same
correction moves the watch list's thirteen-kind check from +0.027 to
+0.024 (data/runs/sequencing/watchlist-power-0cf-fix.txt), under the
rule's +0.03 either way. The shock trial's own warm-up fix (review item
1) now lives in bnm.warm-up, shared with every other fit; its output is
unchanged.

**Rerun after a base correction, 29 October 2026 (trial SS, bead
bnm-82j.7).** The base moved: t48-world-norel (bnm-wns) in place of
t44-world-norel, and food price shocks, one of the trial's three kinds,
moved most. The registered result and the review's rerun above stand as
run. bnm.shock-trial took its base as a constant and wrote over the
registered file; it now takes --run and --out (the defaults are the
registered ones, and they reproduce shock-trial-test-fix.txt exactly).
Rerun with --run t48-world-norel
(data/runs/sequencing/shock-trial-test-t48.txt), the registered result
first, then the review's:
1. primary, S against the base, top-10 recall on hard onsets: -0.024
   [-0.065, +0.012] / -0.021 [-0.064, +0.016] / **+0.006 [-0.015,
   +0.032]**. The upper end is below the registered +0.047 (not below
   the rule's +0.03): **fail, equivalent to nothing, verdict unchanged**;
2. S against P +0.012 / +0.009 / +0.027 [-0.011, +0.069]; log per row S
   less B' -0.0024 / -0.0077 / +0.057 [-0.020, +0.190], the no-harm
   bound still not met; Brier S less B' +0.005 [-0.001, +0.015];
3. per kind (Holm over three, none rejects): food price shocks +0.000
   [-0.018, +0.013], protest surges +0.039 [+0.000, +0.095] (p 0.11),
   violence surges -0.020 [-0.075, +0.049];
4. for the record: k = 5 +0.011, k = 20 -0.007, 10% +0.021, all rows
   k = 10 -0.011; the base against persistence +0.069 and climatology
   +0.060; AUC on hard rows, base / S, food price shocks 0.601 / 0.524
   (t44 0.532 / 0.512), protest 0.770 / 0.781, violence 0.699 / 0.693.
B' against the base on log is -0.148 [-0.429, -0.001] (after the review
-0.011). It is food price shocks at origins 2011 and 2012, fitted on
origins 2007 and 2007-2008 only: the slope on logit p comes out
negative, as the review found, and t48's lower base forecasts in those
years (mean p 0.061 and 0.030, t44's 0.412 and 0.141) carry it to a mean
B' of 0.98 and 0.93 (log 4.9 and 3.8 a row against the base's 0.20 and
0.07). S shares the fit, which is why S less B' looks like a gain on log.
That is the registered specification (a refit once five onsets are
fitted), not a code fault, and it leaves the ranking of the other years
alone; the primary moved from -0.021 to +0.006 mostly through the
base's own food ranking (AUC 0.53 to 0.60) and protest surges.
Watch-list rule v2, descriptive (bnm.shock-trial --nulls 40 on 70eb95f,
the subset variance fixed in review; data/runs/sequencing/
shock-trial-test-v2-t48.txt): S +0.006, the forty derangements -0.009
(sd 0.016), gain over their mean +0.015 [-0.020, +0.050], p 0.23: no
gain; log over B' against the draws' mean +0.020 [-0.096, +0.136]: not
harm. Size at R = 8 on these draws 146 of 12,000 (0.012).

## Pre-registered, 28 October 2026: fast signals at the quarter node, commodity prices (trial FSa, bead bnm-82j.2)

Registered before any run with the family. The slow drivers did not time
onsets (trials 45, T1-T9); what timing the event model has came from fast
signals. Bead bnm-82j.2 adds fast market and shipping series at the
quarter node, one family at a time, each a trial of its own: FSa
commodity prices, FSb exchange rates and sovereign spreads, FSc port
traffic (registered after this one, each before its run). News intensity,
the fourth family the bead names, is already in: the quarter nodes of the
eight kinds on bnm.named's political inputs read the protest, violence and
coercion channels' three- and twelve-month rises (GDELT counts per 1,000
reported, monthly); the five financial kinds' quarters read the financial
inputs instead. It is not a trial here.

**The inputs** (bnm.fast, scripts/fast-data.py, data/fast/series.tsv).
World Bank Pink Sheet monthly indices, nominal dollars, 1960-2026
(data/raw/fast-pinksheet): energy, food and fertilisers, logs. Each is
read at a quarter-end origin t only from months published by then (a
month is known one month after it: the sheet comes out on the second
business day after; one current vintage, the history rarely revised, no
real-time vintages exist), the latest no more than six months old, and
summarized by three operators fixed here, not tuned:
- change: the latest month less three months before;
- surprise: the latest month less the least-squares line through the 24
  months before it (at least 12 known), carried to its month;
- volatility: the standard deviation of the monthly changes over the
  twelve months to the latest.
A world price lands where the country is exposed to it, so each of the
nine is multiplied by the country's exposure, read two years before t
(WDI, as bnm.named/structure reads exposures: fixed before the shock and
past WDI's revisions), as a fraction: food by food imports' share of
merchandise imports, energy by fuel imports' share less fuel exports'
share, fertiliser by agriculture's share of output. Nine inputs, unknown
(nil, the lattice's running mean) where the exposure is: of the 183
countries in the catalogues, known in 2003 for 155 (food), 145 (energy)
and 166 (fertiliser), in 2022 for 162, 159 and 175. Above the countries
a place reads its countries' mean of each input over those known.

**The model.** The world without relations (bnm.world-run, the base
t44-world-norel) with the nine as one nested group at both quarter nodes
(lead and w2) of all thirteen kinds (bnm.fast/with-fast,
bnm.fast-run --family commodities): the averaging sets the node with the
group against the node without it, the group's coefficients at prior mean
0 with the spec's shrinkage variance (0.1), as trials 43-45 nested their
links. Nothing else changes: the year and half-decade nodes read nothing
new and see the group only through the quarters' states passed up (each
finer scale's last and mean state over the year, the :up group). Kinds
run on their own, as without relations they do not read each other.
Runs: **fs-a** and **fs-a-placebo** (each country reads the nine at the
next country in sorted order, bnm.world-run/placebo-source: the world
prices' timing kept, the exposure another country's). Bench:
t44-world-norel, rebuilt on this code by bnm.fast-run --family none as
**fs-norel**: all thirteen kinds' forecasts.tsv byte-identical to
t44-world-norel's, so the runner adds nothing but the group.

**Design and test.** Nothing is chosen on data: the operators, windows,
exposures and the group are fixed above. The design years (origins
1980-2002) are scored first by the same rule, for the record. Test:
origins 2003-2022 (outcomes to 2024; 2023 on stays locked).

**Tests** (bnm.fast-score), all thirteen kinds weighed equally, 1,000
draws of two-year blocks of origins (seed 1), 90% intervals:
1. (primary) the watch-list rule (bnm-82j.1): top-10 recall on hard
   onsets of the year node's forecasts, fs-a less the bench. A gain when
   the lower end is above 0 and the one-sided bootstrap p (the share of
   draws at or below 0) passes Holm at 5% across the three families
   (FSa, FSb, FSc);
2. placebo: the gain is the prices' only if fs-a also beats fs-a-placebo
   on 1 (lower end above 0);
3. harm bound: no harm while the year nodes' pooled paired Brier (bench
   loss less run loss, 2003-2022, bnm.paired/world-pair) has its lower
   end at or above -0.0003 (about a quarter of trial 42's loss) and the
   primary's lower end is at or above -0.03;
4. the family is adopted when 1, 2 and 3 hold; equivalent to nothing
   when the primary's upper end is below +0.03 (the rule's bound); level
   otherwise;
5. for the record: k = 5, 20, 10%, all rows, precision, lead and share
   caught, against the bench and the placebo; the run and the base
   against persistence and climatology; paired Brier and log of the year
   and of the quarter nodes on their own outcomes, against the bench and
   the placebo, per kind.

**Power** (bnm.watchlist power on t44-world-norel's hard rows, a signal
of log-odds size beta per row known only to the model, 100 draws each,
data/runs/fast-power/watchlist-power.txt): on origins 2003-2022 and
thirteen kinds, the recall test finds a mean gain of +0.033 80% of the
time (between beta 0.25, +0.015 found 60%, and 0.5, +0.049 found 99%), and its null passes
in at most 1% of draws. The family can only reach the year node's
ranking through the quarters' states, so its effect there is diluted;
the quarter nodes' own scores (5) say whether it times onsets at all.

**Result, 28 October 2026 (trial FSa).** Runs fs-a, fs-a-placebo and
fs-norel on b4ab000 (data/runs/fs-a/fast-score.txt). fs-norel's
forecasts are byte-identical to t44-world-norel's for all thirteen kinds.
1. **Primary**, top-10 recall on hard onsets, origins 2003-2022: 0.268
   against the bench's 0.274, **-0.006 [-0.015, +0.001]**, one-sided p
   0.92: no gain, and **equivalent to nothing** (upper end below +0.03).
   Holm across the three families (p 0.92, 0.73, 1.00): none rejects.
   Design years 1980-2002, for the record: -0.004 [-0.010, -0.000].
2. Against its placebo: +0.006 [+0.001, +0.012]. The real exposures rank
   better than another country's, but neither beats the base: the
   scrambled inputs cost more than the real ones, as in trials 44 and 45.
3. **Harm**: the year nodes' pooled Brier -0.00077 [-0.00229, +0.00040]
   has its lower end below -0.0003, so harm is not ruled out; the quarter
   nodes lose outright, Brier -0.00043 [-0.00086, -0.00006], log -0.00220
   [-0.00415, -0.00061], most of it food price shocks (-0.00259) and
   fiscal consolidations (-0.00179) at the quarter, the same two at the
   year (-0.01065, -0.00282). Currency crises' year node gains +0.00018
   [+0.00005, +0.00034], not past Holm over thirteen kinds.
4. **Not adopted**: equivalent to nothing on the watch list, and the harm
   bound is crossed.
5. Record: hard rows k = 5 +0.000, k = 20 +0.002, 10% -0.004, all level;
   all rows k = 5 +0.004 [+0.001, +0.008] (a gain on the shortest list),
   k = 10 -0.004; lead 3.4 against 3.5 origins. The run against
   persistence +0.065 [+0.041, +0.085] and climatology +0.031 [+0.012,
   +0.054], the bench's standing (+0.071 and +0.037) slightly reduced.

World commodity prices through exposure do not tell which countries to
watch beyond the base, and at the quarter they cost: the nested group,
learning online over sixty years of mostly quiet price moves, adds noise
to the quarters' timing of the two kinds whose quarters already read
world food and oil prices (bnm.named).

**Rerun after a base correction, 29 October 2026 (trial FSa, bead
bnm-82j.7).** The base moved: t48-world-norel (bnm-wns) in place of
t44-world-norel, and FSa's loss was mostly food price shocks, the kind
the correction moved most. The registered result and the rerun after
the review (fs-a-fix, below under FSc) stand as run. fs-a-t48 and
fs-a-placebo-t48, bnm.fast-run --family commodities (with and without
--placebo true) on 7d5740b, whose lattice is t48's (bnm.fast-run
--family none reproduces t48-world-norel byte for byte, the watch-list
v2 calibration), scored by bnm.fast-score against t48-world-norel
(data/runs/fs-a-t48/fast-score.txt), the registered result first:
1. primary, top-10 recall on hard onsets: -0.006 [-0.015, +0.001]
   (fs-a-fix -0.009 [-0.024, +0.001]) / **-0.006 [-0.016, +0.002]**, p
   0.89: no gain, equivalent to nothing;
2. against its placebo: +0.006 [+0.001, +0.012] / +0.001 [-0.010,
   +0.011]: the real exposures no longer rank better than another
   country's;
3. harm: year nodes' pooled Brier -0.00077 [-0.00229, +0.00040] /
   -0.00012 [-0.00182, +0.00144], the lower end still below -0.0003, so
   the bound is crossed; food price shocks' year node -0.01065 /
   -0.00328 [-0.00675, +0.00009]; quarters -0.00043 / -0.00045
   [-0.00092, +0.00002];
4. **not adopted, verdict unchanged**: equivalent to nothing, harm bound
   crossed. Most of the year nodes' loss to food price shocks went with
   the base's correction.
Watch-list rule v2, descriptive (bnm.watchlist --v2 on 70eb95f, after
amendments A1-A2; the null is FSa's own shape, wl2h-derange-1..4 and
wl2h-shift-1..4 on t48-world-norel; the commodities qualify for time
shifts under A1): gain over the null mean +0.0012 [-0.0115, +0.0139], p
0.43, no gain; harm, Brier over the null mean +0.00018 [-0.00195,
+0.00232], not harm; equivalent to nothing (detectable gain +0.0147);
year nodes' Brier against the bare bench -0.00012 [-0.00182, +0.00144];
not adopted. The placebo the same way: -0.0003 [-0.0078, +0.0073], not
harm (-0.00079 [-0.00250, +0.00091]).

## Pre-registered, 28 October 2026: fast signals at the quarter node, exchange rates and sovereign spreads (trial FSb, bead bnm-82j.2)

Registered before any run with the family, after trial FSa's registration
and before any of its runs. Everything as FSa (the model, runs, bench,
design and test years, tests 1-5, Holm across FSa, FSb and FSc, the harm
bound, power) but the inputs, six per country, by FSa's three operators
(change over three months, surprise against the 24 months' line,
volatility over twelve), from months known one month after they end:
- the exchange rate: BIS US dollar rates (WS_XRU), monthly, end of
  period, log local currency per dollar (data/raw/fast-fx): 188 areas,
  182 of them covering 2001-2022, euro members in euros throughout; the
  US has none;
- the sovereign spread: the 10-year government yield (OECD Main Economic
  Indicators via FRED, IRLTLT01<ISO2>M156N, monthly averages) less the
  US's, points (data/raw/fast-spreads): 33 countries, 28 covering
  2001-2022, the OECD members with a series plus South Africa (Russia to
  2018). Emerging-market spreads proper (J.P. Morgan EMBI) are paid and
  behind an account, so the spread is unknown (the lattice's mean) for
  most of the countries where a sovereign default is likeliest; the
  family's spread half speaks mainly for the advanced economies.
Market rates and yields are not revised; one current download each
(ALFRED vintages exist for the yields but would be the same values).
Runs **fs-b** and **fs-b-placebo** (each country reads the six at the
next country in sorted order). A country reads its own series, so no
exposure enters.

**Result, 28 October 2026 (trial FSb).** Runs fs-b and fs-b-placebo
(data/runs/fs-b/fast-score.txt), bench fs-norel.
1. **Primary**, top-10 recall on hard onsets, origins 2003-2022: 0.269
   against 0.274, **-0.005 [-0.018, +0.007]**, one-sided p 0.73: no gain,
   **equivalent to nothing**; Holm: none of the three rejects. Design
   years: -0.008 [-0.016, +0.000].
2. Against its placebo: -0.005 [-0.018, +0.005]; on hard rows the placebo
   is ahead at k = 5 (-0.011), 20 (-0.012) and 10% (-0.017), all with
   upper ends below 0.
3. **Harm**: year nodes' pooled Brier +0.00015 [-0.00058, +0.00084]: no
   loss, but the lower end is below -0.0003, so by the bound harm is not
   ruled out; quarter nodes -0.00015 [-0.00048, +0.00015], level, with
   currency crises (-0.00016 [-0.00033, -0.00002]) and food price shocks
   (-0.00106) losing at the quarter. Protest surges' year node gains
   +0.00047 [+0.00004, +0.00105], not past Holm.
4. **Not adopted**: equivalent to nothing, and the harm bound is crossed.
5. Record: hard rows k = 5 -0.009 [-0.018, -0.001] (a loss), k = 20
   -0.007, 10% -0.012; all rows level; lead 3.3 against 3.5. Sovereign
   defaults' hard recall rises 0.065 to 0.104 (77 onsets) and currency
   crises' falls 0.282 to 0.268.

Exchange-rate moves and the spreads that exist openly add nothing to
the watch list. Where the family should matter most, sovereign defaults
and currency crises, the spread is unknown for nearly every emerging
market (EMBI is paid), and the currency's own fall is mostly already in
the financial quarters' credit and asset inputs.

## Pre-registered, 28 October 2026: fast signals at the quarter node, port traffic (trial FSc, bead bnm-82j.2)

Registered before any run with the family, after FSa's and FSb's
registrations and before any of their runs. Everything as FSa but the
inputs and the test years. IMF PortWatch (open, no account; the public
ArcGIS service, data/raw/fast-portwatch), daily port calls by country and
daily transits through 28 chokepoints from January 2019, summed by month
(a month known one month after it ends; the current, partial month
dropped; one current download, re-estimated by PortWatch when its method
changes, no vintages). Port calls are seasonal, so FSa's change becomes a
change against a year before, and with seven years of data the trend
window is shorter. Four inputs per country:
- port calls: the mean of the latest three months' log calls less the
  same three months a year before; the latest month's log calls less the
  line through the 12 months before it (at least 6 known); the standard
  deviation of the monthly changes over twelve months;
- chokepoints: total transits, the latest three months against the same
  three a year before (logs), times the country's trade over output (WDI,
  read two years back, as FSa's exposures).
Coverage: port calls for 180 areas (countries with no port, the
landlocked ones, unknown: the lattice's mean). The inputs exist from the
origin of June 2020 (fifteen months of data needed); before that the
group is unknown throughout and the nested model is the node's own.
Runs **fs-c** and **fs-c-placebo** (each country reads the four at the
next country in sorted order).

**Test years**: origins 2020-2022 (the year-end origins with the inputs
known and outcomes published; 2023 on stays locked). No design years. The
tests are FSa's 1-5 on these origins, the primary in Holm's family with
FSa and FSb. **Power** on 2020-2022 (data/runs/fast-power): the recall
test finds +0.056 80% of the time (+0.019 found 48%, +0.105 found 91%);
its null passes in at most 2% of draws. With three origins in two blocks
the bootstrap is coarse; the family is equivalent to nothing when the
primary's upper end is below +0.056 (its detectable gain, not the rule's
+0.03, which these years cannot reach), and its result is mainly a record
for when more years have closed.

**Result, 28 October 2026 (trial FSc).** Runs fs-c and fs-c-placebo
(data/runs/fs-c/fast-score.txt), bench fs-norel, origins 2020-2022.
1. **Primary**, top-10 recall on hard onsets: 0.246 against 0.246,
   **+0.000 [+0.000, +0.000]** (no hard top-10 list changed at the three
   origins), one-sided p 1.00: **equivalent to nothing** (upper end below
   +0.056); Holm: none rejects. Design years 1980-2002 (no port data, so
   only the learning path differs): +0.002 [-0.002, +0.004].
2. Against its placebo: -0.013 [-0.017, -0.013], the placebo ahead.
3. **Harm**: year nodes' pooled Brier -0.00059 [-0.00111, -0.00010], a
   loss past the bound (market crashes -0.00667 on 9 onsets); quarter
   nodes Brier -0.00022 [-0.00125, +0.00086], log +0.01647 [+0.00276,
   +0.03036]: food price shocks' quarters gain (+0.01221 [+0.00179,
   +0.02306], 553 onsets) while violence surges (-0.00375), growth
   collapses (-0.00787), conflict onsets and protest surges lose.
4. **Not adopted**: equivalent to nothing, and harm.
5. Record: hard rows k = 5 +0.001, k = 20 +0.009 [+0.009, +0.015], 10%
   -0.011; all rows k = 10 +0.019 [+0.012, +0.045], k = 5 -0.010. Three
   origins in two blocks make these intervals near-degenerate; they are a
   record, not evidence.

Port traffic has three scored years. The one clear signal, food price
shocks' quarters in 2020-2022 (the pandemic's shipping disruption and
2022), is what a single episode can give and would need the years after
2022, locked now, to be tested.

**Review and corrections after the outcome, 28 October 2026 (trials
FSa-FSc, bead bnm-82j.2).** Fixes made after the results were seen; the
registered results above stand as run, and nothing in the registrations
changes.
- Known-year honesty: a test now perturbs every fast series after an
  origin, and every exposure after the origin less two years, and checks
  that each family's inputs and the lattice's forecasts to that origin
  do not move (bnm.fast-test). They do not. The spot checks light up
  where they should: food's three-month change at the March 2008 origin
  (+0.19), energy times net fuel exports at December 2008 and through
  the 2014-15 collapse (Saudi Arabia +0.57, +0.20, +0.25), fertiliser
  at December 2021 and June 2022, the dollar rate at ARG March 2002 and
  June-September 2018, TUR September 2018, RUS December 2014, EGY
  December 2016 (never the quarter before).
- The coupling (a bug, bnm.lattice): the levels' coupling shared the
  nested group's coefficients between adjacent levels. A nested group's
  model reads all the node reads, flows included, and the all-inputs
  model it is weighed against is never coupled, so the averaging set
  the node with the group and the coupling against the node without
  either. FSc showed it: with no port data before 2019 its group was
  all unknown, yet it held 0.96 of the protest quarters' weight in
  2002. The coupling now skips nested groups. Trials 43-45 nested their
  links the same way and are affected too (bead bnm-5r4).
- Exchange-rate redenominations (a bug, scripts/fast-data.py): BIS
  rates carry new zeros as a fall in the rate (the bolivar in 2021, -13.8
  in a month; Zimbabwe 2008 and 2019; San Marino's lira; Laos;
  Macedonia), which doubled the panel's spread of the three-month change
  (0.106 to 0.194 by 2022). A twentyfold monthly appreciation is now
  read as a redenomination and spliced. data/fast/series-fs-trials.tsv
  keeps what the trials read.
- Power: data/runs/fast-power was computed before the base's warm-up
  rows were left out of the simulation (bead bnm-0cf); rerun, the
  detectable gain on 2003-2022 is +0.034 (registered +0.033); 2020-2022
  unchanged at +0.056. The primary with the warm-up rows (food price
  shocks 2003-2006) left out: FSa -0.005 [-0.015, +0.002], FSb -0.003
  [-0.018, +0.009].
Rerun with the fixes (placebos not rerun), each against fs-norel, the
registered result first:
- FSa (coupling): primary -0.006 [-0.015, +0.001] / **-0.009 [-0.024,
  +0.001]**; year nodes' Brier -0.00077 [-0.00229, +0.00040] / -0.00106
  [-0.00239, +0.00004]; quarters -0.00043 / -0.00076 [-0.00125,
  -0.00029] (fs-a-fix);
- FSb (coupling, then also the redenominations): -0.005 [-0.018,
  +0.007] / -0.005 [-0.020, +0.007] / **-0.004 [-0.018, +0.006]**; year
  Brier +0.00015 [-0.00058, +0.00084] / +0.00066 [-0.00037, +0.00182] /
  +0.00067 [-0.00037, +0.00181] (fs-b-fix, fs-b-fix2);
- FSc (coupling), 2020-2022: +0.000 [+0.000, +0.000] / **-0.012
  [-0.014, +0.000]**; year Brier -0.00059 [-0.00111, -0.00010] /
  -0.00013 [-0.00065, +0.00045] (fs-c-fix); design years +0.002 /
  -0.004.
No verdict changes: each is equivalent to nothing, each lower Brier end
is below -0.0003, none is adopted.

**The cost of a group (reported only).** A group of nine standard
normal draws per country and origin at the quarter nodes, FSa's width,
nothing in it (bnm.fast-run --family noise; fs-noise on the trials'
code, fs-noise-fix with the coupling fixed):
- primary +0.012 [+0.001, +0.023], p 0.026 / **+0.014 [+0.005, +0.021],
  p 0.001**: noise "gains" on the watch list;
- year nodes' Brier -0.00220 [-0.00425, -0.00048] / -0.00215 [-0.00406,
  -0.00050], log -0.0050 / -0.0054: a loss twice FSa's, past the harm
  bound; quarters level (-0.00013).
Two things follow. The harm bound, as registered, is crossed by any
group: the year nodes lose more to noise than to the commodity prices,
so crossing it says the group costs what a group costs, not that the
signal harms. And the primary's interval draws origins, not models: any
change at the quarters reshuffles the year node's top ten, and a
reshuffle can clear the bound by chance (here with p 0.001), so a
primary gain means little without the placebo test (2), which is what
guards it. FSa beating its placebo (+0.006) while losing to the base
fits the same reading: the scrambled exposure costs a little more than
the real one, both cost what nine added coefficients cost.

**Correction, 29 October 2026 (the noise group, beads bnm-0z5 and
bnm-82j.6).** The noise was not independent. bnm.fast/noise-inputs
seeded augur's generator with 1000003 seed + 7919 t + the country's
code, and augur.math/rng fills its state linearly from the seed, so
every quarter's draws and neighbouring countries' draws were correlated
(each column autocorrelated in t at 0.71, -0.51, 0.90, ...; FRA and DEU
at -0.66). fs-noise and fs-noise-fix read that lattice, not noise. The
noise is now drawn from augur.math/rng-mixed (seed, country, t), tested
for near-zero correlation across quarters, countries, columns and seeds.
Rerun on the current code and base (bench t48-world-norel, which
bnm.fast-run --family none reproduces byte for byte), seed 1 as
**fs-noise-mixed** and three more seeds (wl2h-noise-2 to -4), each
against t48-world-norel, 2003-2022:
- primary +0.006 [-0.007, +0.019] (p 0.24), +0.008 [-0.002, +0.019],
  -0.003 [-0.013, +0.007], -0.003 [-0.014, +0.006]: mean +0.002, sd
  0.006; none passes the bnm-82j.1 rule. The +0.014 (p 0.001) was the
  lattice's draw, not a property of noise;
- year nodes' Brier -0.00234 [-0.00506, +0.00025], -0.00168, -0.00161,
  -0.00211; log -0.0073 [-0.0133, -0.0014] for fs-noise-mixed. The cost
  stands: noise costs the year nodes about 0.002 Brier, more than the
  shuffled commodity groups of the v2 registration (-0.0003), so the bnm-82j.1 harm bound
  still measures what adding a group costs.
The second point above holds without the lattice: draws of one shape
differ by more than the origin bootstrap allows for (the watch-list
rule's second form, bnm-82j.6).

**Against T1(b).** T1(b) passed after the warm-up fix, food import
exposure +0.102 per SD. It is a different object from FSa's food input:
T1's state is the food import share times the world grain price gap
(wheat, maize and rice against their cycle equilibria: a level, slow),
demeaned by year, so it is the cross-section of exposure in the years
grain is dear; it is fitted offline with the base as offset, at the
year node, on two kinds (protest surges and food price shocks, the code
pools both though the registration names protest surges). FSa's input is
the share times the Pink Sheet food index's three-month change, surprise
and volatility, not demeaned, read online at the quarter among nine
coefficients and reaching the year only through the quarters' states,
scored over thirteen kinds; when world food prices are quiet it is near
zero for every country, so the level of exposure, T1's signal, is not in
it. The political kinds' quarters already read world food prices' twelve
-month change times the import share (bnm.named). FSa's food price
shocks loss is one origin, 2011 (year node mean p 0.62 against the
base's 0.41, outcome rate 0.05), where the 2010-11 price spike did not
bring 2007-08's onsets back. No bug was found on either side beyond
those above.

## Corrected base, 28 October 2026: WDI zeros that were missing values (run t47-world-norel, bead bnm-as1)

Not a trial. The World Bank API publishes some missing values as 0, not
null, and scripts/slow-normalize.py took every non-null value, so
data/slow/series.tsv held, among others, Venezuela's trade openness and
agriculture, manufacturing and services shares at 0 for 1991-2011 and
Brazil's sector shares at 0 for 1960-1980 (the raw files on disk,
data/raw/slow/worldbank, have the 0s; nothing was downloaded). An audit
of every WDI indicator for exact zeros (332 values dropped in all):
- impossible zeros, now gaps: trade, % GDP (VEN 1991-2011); agriculture,
  % GDP (BRA 1960-80, VEN 1991-2011); manufacturing (BRA 1960-78, VEN
  1991-2011, COD 1991); services (BRA 1960-77, VEN 1991-2011); private
  credit (ROU 1993-95, GNB 2002); broad money (CHL 1960); external debt,
  % GNI (GNB 1970-74); FDI inflows (115 values: the planned economies
  in the 1980s, ALB, BGR, HUN, ROU, MNG, ETH, and small states' years
  before they report);
- leading zeros of a count, now gaps: scientific articles before a
  country reports (SRB, MNE, XKX to 2006-07, counted under Serbia and
  Montenegro; SSD before 2011; TLS before 2003);
- zeros kept as measurements: oil, mineral and total rents, fuel, metal,
  food and high-tech export shares, military spending (Costa Rica,
  Iceland, Panama since 2000), renewables' share in the Gulf, internet
  and mobile use before they existed, tertiary enrolment in a few
  1970s years, and single-year zeros of growth, inflation and
  population growth (Greece's 1990 growth sits between source values
  rounded to a tenth). Logged variables' non-positive values were
  already dropped.
WDI stays known nine months after its year (the existing lag); a dropped
value goes stale as any gap does. scripts/slow-normalize-test.py tests
the rule; the old file is kept as data/slow/series.tsv.pre-as1.

**t47-world-norel**, the base rebuilt on the corrected series with
t44-world-norel's command (bnm.world-run --without-relations true, as
t46 and t45fix-norel), against t44-world-norel, origins 2003-2022:
- smoke (to 2008, in three batches of kinds: all thirteen at once runs
  out of a 16 GB heap): twelve kinds pass; food price shocks fail one
  check, the country year node's feedback group dead in 1999-2008, which
  fails the same way on the old series and on t46's commit 02f980a, so
  it is not this change (bnm-wns). A smoke to 2025 fails banking's
  distress group the same way: Romer & Romer's measure ends in 2017;
- year nodes, pooled over thirteen kinds: Brier +0.00000 [-0.00000
  +0.00001], log +0.00001 [-0.00001 +0.00002]; the largest per kind,
  banking crises, Brier +0.00003 [-0.00000 +0.00009], log +0.00011
  [-0.00003 +0.00030]; market crashes byte-identical;
- watch-list rule, hard onsets, top 10: base recall 0.274, unchanged
  (persistence +0.071 [+0.049 +0.092], climatology +0.037 [+0.021
  +0.058]); every kind's recall identical; t47 against t44 +0.000
  [+0.000 +0.000] (k = 20: -0.001);
- what moves: no country's year node in 2003-2022 moves by more than
  0.006 (Malaysia, banking, 2010). Venezuela's own nodes move most, in
  1992-2013 and mostly at the quarters: currency crises' second-window
  quarter by up to -0.081 (1997-2001), sovereign defaults' lead quarter
  by up to -0.037 (2003-2012), coups, inflation and currency crises'
  year node by up to -0.039 in 1992; region and subregion nodes in the
  Americas, and through the shared learning a few others, by up to
  0.04 at the quarters. Brazil's 1960-80 zeros change nothing (no
  forecast differs before 1992).

**Trials that used the old base.** Every trial since series.tsv was built
(19 October) read the zeros: trials 42-45 and their norel benches,
t44-world-norel as bench or offset for 44, 45, T1, T2, T8, T4, T8b, T3,
T6, T9, H4, SS, the watch-list rule's record, and fs-norel (the base
through bnm.fast-run) for FSa-FSc, whose exposures also read the
zeros (FSa's fertiliser exposure is agriculture's share; FSc's
chokepoints go through openness, but its origins, 2020-2022, read none
of the dropped years). The base moved by about 1e-5 on the
pooled scores and not at all on the watch list, against registered
intervals of 1e-4 to 1e-2; the countries affected are one of about 170
in the test era, and for Venezuela the change is a few hundredths at the
quarters. No registered verdict can plausibly move, so none is rerun.
t44-world (with relations) is not rebuilt: no trial benches against it
(trial 45 reads it for the record only). **The base for future trials is
t47-world-norel.**

## Corrected base, 29 October 2026: unknown errors and groups without inputs (run t48-world-norel, bead bnm-wns)

Not a trial. Two smoke failures, both in the lattice (src/bnm/lattice.clj):
- food price shocks' year feedback group dead in 1999-2008. The
  catalogue starts in 2004, a year window is 24 months and the lag 18,
  so the subregion's first error is known in mid 2007 and its country
  reads it at the 2008 year end. That much is structure. The bug: an
  error not yet known was fed back as 0.0, so every kind whose
  catalogue starts after 1870 gave the feedback standardizer decades of
  exact zeros, a spread near 0, and its first real errors landed at the
  cap of 5. An unknown error is now nil (the mean, left out of the
  spread);
- banking's distress group dead in 2016-2025: Romer & Romer's measure in
  data/derived/lattice-annual.csv ends in 2012, read through the 2013
  origin. Its inputs were 0 at every place from then on, and the group,
  an intercept-only model under another name, kept weight. No open
  successor is on disk. The rule now, for every node: a group with no
  input at any place at an origin (a source that ended, a fast signal
  gone stale past its 6 months, errors not yet known, a link never
  known) gets weight 0 at that origin, the others renormalized; the
  averaging keeps its record, so it comes back when its source resumes.
  The smoke check reports such a group as dropped out and fails one that
  keeps weight where it has no input.
Also found: a node's ancestors came from the parent map, not the spec's
levels, so a parent map running past them shifted every input after
:down by one. No production spec does this (the golden snapshot is
unchanged by the fix), but the lattice tests' fixture did, and one test
asserted a feedback gain that came from the shift; the input width is
now checked against the layout.

Smoke after the fix: food price shocks to 2008 and banking to 2025 pass,
their dead groups reported as dropped out with no weight.

**t48-world-norel**, the base rebuilt with t47's command (bnm.world-run
--without-relations true), against t47-world-norel, origins 2003-2022:
- year nodes, pooled over thirteen kinds: Brier +0.00198 [+0.00000
  +0.00412] (nominally a gain), log +0.00414 [-0.00051 +0.00874]. Most
  of it is two kinds with late catalogues: food price shocks Brier
  +0.01024 [-0.00033 +0.02239], log +0.02497 [-0.00962 +0.05735]; market
  crashes Brier +0.01564 [-0.00667 +0.04308], log +0.02838 [-0.02219
  +0.08802]. Every other kind within 0.0006 Brier and 0.006 log, none
  outside its interval;
- watch-list rule, hard onsets, top 10: recall 0.271 against 0.274,
  -0.003 [-0.021 +0.013] (k = 5 +0.000, k = 20 -0.003, all rows k = 10
  -0.003); against persistence +0.068 [+0.043 +0.090], climatology
  +0.034 [+0.009 +0.063] (t47: +0.071, +0.037). Per kind the largest
  moves are market crashes 0.684 against 0.737 (19 positives), currency
  crises 0.254 against 0.282 and sovereign defaults 0.091 against 0.065.

**Trials that used the old base.** Unlike t47, this moves the base by
1e-3 to 1e-2 on the pooled scores, the size of registered intervals.
Every lattice run since trial 37 read the zeroed errors, and every one
since trial 42 had dead groups weighted: trials 42-45 and their norel
benches, T1, T2, T8, T4, T8b, T3, T6, T9, H4, SS, the watch-list rule's
record, and FSa-FSc through fs-norel (whose fast groups at the quarters
now drop out when stale everywhere). Not rerun: left for a decision.

Decided 29 October 2026 (bead bnm-82j.7): rerun the trials that turn on
food price shocks or market crashes, or sit near a decision line: T1(b),
SS, FSa, T8b and T9, each with a correction under its result (no verdict
changes; T1(b)'s pass after bnm-0cf holds at +0.124, p 0.025). Then the
three cheap offline trials that read those kinds, T4, T6 and H4, each
with a correction under its result: no verdict changes (T4's and H4's
primaries do not read the base and are byte-identical; T6's calm
duration also moved with bnm-as1's private credit, recorded there). Not
rerun, recorded as unaffected: trials 42-45, T2, T8, T3, FSb and FSc.
For the kinds other than those two the base moved less than 0.0006
Brier, against registered intervals of 1e-4 to 1e-2; of these trials,
42, FSb and FSc also read market crashes or food price shocks, and are
left on the judgement that none sits near its decision line, not on the
size of the move.

## Pre-registered, 22 October 2026: the energy pilot (trial P1)

Registered before the pilot is run. docs/STOCK-FLOW.md, bead bnm-62z.6:
does a sector's place in the supply chain tell how an energy price shock
hits its output, beyond its own trend? On the input-output spine
(data/derived/io: LR-WIOD 1965-2000, WIOD 2016 2000-2014, 21 sectors,
real output from scripts/io-volumes.py, national-currency prices).

- The shock at country c in year t: the change in refined products'
  price relative to all sectors' (output-weighted), e = ln p_REF - ln p.
- Exposure of sector s, from the table three years before (as known): its
  direct energy cost share a_REF,s and its supply-chain share, the Leontief
  inverse's L_REF,s less a_REF,s (energy embodied in its other inputs).
- Outcome: the sector's real output growth in the shock year and the two
  after (horizons 0, 1, 2).
- Models, each estimated only on years before the shock tested:
  (A) the trend: the sector's own mean growth plus an AR(1) term pooled
  across sectors; (H) Hulten's benchmark: (A) less L_REF,s e (unit
  elasticity, the price passed to output one for one); (N) the network:
  (A) plus b1 a_REF,s e + b2 (L_REF,s - a_REF,s) e, b1 and b2 by least
  squares per horizon on the training years.
- Shocks: 1974, 1979, 1990, 2008 (each tested with the models fitted on
  the years before it: leave-the-future-out).

**Tests**, the squared error of predicted real output growth over the
countries, sectors and horizons of the four shocks, paired by
country-sector, intervals by resampling countries:
1. (primary) N against A, pooled over the shocks: does the supply chain
   add to the trend?
2. N against H: does an estimated pass-through beat unit elasticity?
3. for the record, per shock, and the energy-intensive sectors (METAL,
   CHEM, MINR, PAPER, TRANSP) alone.
The 2022 shock cannot be tested: the spine has no prices after 2014.

**Results, 22 October 2026 (trial P1).** 7,368 country-sector-horizon rows
over the four shocks; the models fitted on 7,875, 15,750, 33,075 and
65,393 earlier rows for 1974, 1979, 1990 and 2008 (the first write-up's
"83,717" counted every row, not the training rows). Intervals as
registered, countries resampled (the first write-up resampled shock
years too; those intervals are kept below as the extra):
1. **The network against the trend** (primary), pooled: -0.000032
   [-0.000396 +0.000413]: level (two-way: [-0.000916 +0.000659]).
2. **The network against Hulten's unit pass-through**: +0.000726
   [-0.000826 +0.001999]: level (two-way: [-0.001046 +0.002164]);
   Hulten's against the trend -0.000758 [-0.002271 +0.001196] (mse
   0.02286 against 0.02210).
3. For the record, per shock, the network against the trend: 1974 a
   loss (-0.00140 [-0.00235 -0.00047]), 1979 and 1990 level, 2008 a gain
   (+0.00081 [+0.00015 +0.00185]); the energy-intensive sectors a loss
   (-0.000182 [-0.000323 -0.000065]; level with shock years resampled).
The registered primary is level: the supply chain does not add to the
trend. Not adopted as a gauge.

**Deviations after review, 22 October 2026 (trial P1).** Not registered;
decided after the results, so they qualify the reading and do not
replace the registered test. The review found:
- Refining's own rows drive the per-shock results. L_REF,REF carries the
  Leontief identity (median about 1.08), so for s = REF the
  "supply-chain" share L - a is about 1 against 0.01-0.1 elsewhere: 98.6%
  of the b2 regressor's variation is refining's response to its own
  price, and Hulten's predicts refining's output falling by 1.08 e.
  Refining is 348 of the 7,368 test rows but about 35% of the squared
  error, and one of them (Latvia 2008, a refinery of 0.1-50 million US$
  with volume changes of x12, x0.002, x219) about 17%. On refining's rows
  alone the network loses 0.021 in 1974 and gains 0.016 in 2008; on the
  other sectors 0.0004 and 0.0001.
- LR-WIOD's tables before 1990 are mostly backcast (the share is 0 or 1
  by country-year; 1 for all but two to four countries), so the
  exposures are not as known at the time.
- In 1968-70, 11 of 25 countries have one deflator for every sector:
  the shock is 0 and the volumes are nominal output over one deflator.
- Four rows' table columns have inputs at or above output (Malta's
  mining, 2000-2014).
The corrected analysis (`bnm.energy-pilot`, printed after the registered
one in data/runs/energy-pilot/score.txt): refining left out; the
supply-chain share L_REF,s - delta_REF,s - a_REF,s; Hulten's pass-through
L_REF,s / L_REF,REF of the observed refined-products price (that price
already holds refining's own feedback); sectors under 0.1% of the
economy's output at t - 1, rows with a flat-price year (t - 1, t or
t + h) and columns with inputs at or above output dropped from training
and test. 7,017 test rows (training 4,800 / 12,300 / 28,800 / 59,574);
mse trend 0.015362, Hulten 0.015240, network 0.015573:
1. The network against the trend, pooled: -0.000211 [-0.000375
   -0.000048], a loss (two-way: [-0.000682 +0.000127], level).
2. The network against Hulten's: -0.000332 [-0.000572 -0.000111], a
   loss (two-way: [-0.000896 +0.000028], level). Hulten's against the
   trend +0.000122 [-0.000079 +0.000334]: level, a point gain.
3. Per shock, the network against the trend: 1974 a loss (-0.00113
   [-0.00187 -0.00053]), 1979, 1990 and 2008 level (2008 +0.00007
   [-0.00022 +0.00033]: the registered "gain" was refining's); Hulten's
   against the trend a point gain at every shock, significant in 1979
   (+0.000135 [+0.000029 +0.000289]). The energy-intensive sectors: the
   network a loss (-0.000373 [-0.000663 -0.000104]).
On the tables not backcast only (backcast share at most 0.5: Canada,
the Netherlands, the United States and Japan or China before 2000, all
countries in 2008; 3,177 test rows, training 460 / 1,500 / 4,820 /
25,377) the reading holds: the network against the trend -0.000260
[-0.000588 +0.000072], against Hulten's -0.000381 [-0.000744 +0.000036],
Hulten's against the trend +0.000120 [-0.000263 +0.000461]; 1974 the
network a loss (-0.0055 on 460 training rows), Hulten's a gain (+0.00016
[+0.00008 +0.00025]).

What follows from these numbers: no estimated pass-through beats the
trend. Learned from earlier shocks, the network's two coefficients do
worse than the trend and than unit pass-through, worst when the history
is short (1974), and never significantly better. "It helps once it has
history" came from refining's own rows. Hulten's fixed pass-through, a
sector's L_REF,s / L_REF,REF times the shock, is the better
supply-chain prior: a point gain on the trend at every shock, small
(about 1% of the trend's squared error) and significant only in 1979.
Not adopted as a gauge. If the exposures are used, use them as fixed
Hulten weights, not as fitted coefficients. The energy/* states
(data/derived/stocks/energy.csv) now leave refining's own output, the
identity in L_REF,REF, out of the economy's exposure, as in the
corrected network term. That lowers the exposure by about 0.02 on
average; the shock is unchanged.

## Power audit, 22 October 2026 (trial 0 of bnm-3qu)

How large an effect could trial 45's design find? bnm.power-audit injects
synthetic states with a known odds ratio per SD into the real rows of
t44-world-norel (each kind's year-end country forecasts, design years
1960-2002, test years 2003-2022, as bnm.state-diag reads them) and redraws
the outcomes as y* ~ Bernoulli(sigmoid(logit p + u_year + beta x)). The
states, standardized on the design rows: *own*, an AR(1) at 0.9 within
each country, independent across countries; *level*, a persistent
country mean plus a little noise (90% of the variance between
countries, like arable land); *world*, one AR(1) at 0.9 over years, the
same at every country. The placebo is state-diag's: another country's x,
or for a world series x ten years before. Twelve kinds (food price
shocks have no design-year onsets and are not tried). Results in
data/runs/sequencing/power-audit.tsv.

**Year shocks.** The sd of u_year per kind, by the method of moments on
the design years' yearly onset counts against the base's (their
variance past binomial, matched to what a shock of sd s adds):

    market crashes 1.65   currency 1.06   inflation 0.92   banking 0.76
    protest 0.75   violence 0.75   sovereign default 0.75
    growth collapse 0.73   fiscal consolidation 0.38   conflict 0.24
    coup 0.18   war 0.18

The base leaves much of the year-to-year clustering of onsets
unexplained, most of all for the financial kinds.

**The diagnostic** (passes? on the clustered score test against the
placebo), share passing, mean over the twelve kinds, 200 draws per kind
and cell (Monte Carlo error of the mean at most 0.009); with year
shocks, and in brackets without:

    OR per SD        1.0      1.1      1.2      1.35     1.5      1.8      2.5
    own   k = 348    .00      .00      .14(.16) .54(.58) .72(.78) .86(.91) .93(.92)
          k = 10     .00      .13(.13) .54(.57) .86(.91) .96(.97) 1.0      1.0
          k = 1      .04(.04) .38(.40) .80(.83) .96(.98) .99      1.0      1.0
    level k = 348    .00      .01(.00) .15(.18) .55(.58) .75(.78) .87(.91) .92(.91)
          k = 10     .00      .13(.13) .57(.58) .88(.90) .96(.97) .98(.99) .98(.99)
          k = 1      .04(.04) .39(.39) .82(.82) .96(.97) .99      .99      .98(.99)
    world k = 348    .00      .00      .00(.02) .04(.13) .10(.23) .17(.31) .20(.37)
          k = 10     .00      .02(.07) .10(.32) .26(.64) .34(.72) .46(.76) .60(.75)
          k = 1      .04(.04) .14(.32) .31(.67) .49(.83) .58(.84) .71(.84) .81(.83)

At OR 1 the test holds its level (k = 1: 0.039-0.044 against 5%). The
kinds differ widely: at OR 1.2 and k = 348 an own state passes for coups
and fiscal consolidations 44% of the time and for banking crises,
protest surges, inflation crises and market crashes under 1%; at k = 1,
39-99%. A world series is hard to find at any list size: it is 43
observations, the year shocks compete with it, and its placebo (itself
ten years before, still correlated at 0.9^10) gains with it, so even a
strong world effect often fails the 2x-placebo rule. Year shocks cost the
country states little (a few points) and the world series half its power.

**Out of sample**, 2003-2022, with year shocks, the same effect in every
kind at once, 60 draws per cell (Monte Carlo error up to 0.065): an
oracle (beta known) and a realistic model (beta fitted on the draw's
design years with the base as offset) against the base, the paired
bootstrap of bnm.world-score (countries and years resampled, gain when
the 90% interval's lower end is above 0). Power, pooled over the kinds
with equal weights, realistic model, Brier (log score):

    OR per SD   1.0       1.1       1.2       1.35      1.5       1.8       2.5
    own         .00(.00)  .08(.13)  .50(.65)  .92(1.0)  1.0       1.0       1.0
    level       .00(.00)  .15(.12)  .50(.70)  .95(1.0)  1.0       1.0       1.0
    world       .02(.02)  .03(.05)  .23(.23)  .47(.50)  .60(.63)  .82(.88)  .97

**Minimum detectable effects at 80% power** (interpolated on log OR; the
expected gain over the base at that OR is the equivalence bound):

    pooled, own or level state   OR 1.30-1.31   Brier +0.00063-0.00065   log +0.0017-0.0020 (OR 1.25-1.26)
    pooled, world series         OR 1.77        Brier +0.0100            log +0.031 (OR 1.69)
    per kind, own state, Brier   OR 1.38 (fiscal consolidations), 1.65 (violence surges),
                                 1.9-2.45 (conflict, coup, growth, banking, currency, war),
                                 above 2.5 (inflation, market crashes, protest, sovereign default)

The pre-registered equivalence bounds for later trials: a pooled Brier
gain of +0.0006 (log score +0.002) for country states carried by every
kind; per kind, about +0.003 to +0.006 on Brier where reachable at all.
A "level" pooled result is read as "a pooled gain under 0.0006", not as
no effect; a per-kind "level" says almost nothing.

What follows:
- The literature review's rough figure (OR about 1.8 per SD at k =
  4,530) is too pessimistic for country states on our rows: the screen
  was per kind (k = 348), and a state with independent variation across
  178 countries is close to independent rows once years are clustered.
  At k = 348 the screen still finds OR 1.35 only about half the time and
  OR 1.2 one time in seven; at OR 1.1 never. The review's falsification
  test (OR 1.3 found 80% of the time) is not met: trial 45's null does
  not rule out effects of 1.1-1.3 per SD, nor any world series under
  OR 2.5.
- A theory-led list of about 10 states finds OR 1.35 86-88% of the time
  and OR 1.2 about 55%; one pre-registered hypothesis per kind finds OR
  1.2 80%. Trial 1 should name at most 10 states (the 8-10 of
  LIT-NEXT-STEPS fit), signed, with Holm over the list; it can then
  detect OR 1.3 or more per SD for country states on the better covered
  kinds, and should pool a state's coefficient across its kinds, since
  rare kinds (inflation, protest, market crashes) cannot carry a test
  alone.
- World series cannot be tested this way: drop the ten-year placebo rule
  for them (it is correlated with the series) or test them through
  their country exposures, and expect only OR 1.8 or more to show.
- Out of sample, 2003-2022 detects a pooled gain only when the effect is
  OR 1.3 or more in every kind; a state that helps two kinds (as trial
  45's did) needs a per-kind effect of OR 1.4-2.0. Later trials should
  score the pooled named states, not per kind, and state the bound
  above beforehand.
- These are best cases: the synthetic states are known at every row and
  independent of the base, where real states have gaps and are partly
  held by the base already (the mediation of LIT-NEXT-STEPS 3b).

**Correction, 28 October 2026 (trial 0, bead bnm-0cf).** The audit drew
its synthetic outcomes from the base's rows, its warm-up rows among
them: before the year node has read a published outcome of a kind it
sits at its prior, p near 0.5 for every country (currency crises
1961-1964, inflation crises 1960-1963, market crashes 1961-1963, protest
and violence surges 1979-1981 in the design years; food price shocks
2003-2006 in the test years), so those rows drew coin flips and padded
the year-shock spreads and the onsets. bnm.warm-up now leaves them out.
The current code without the fix does not reproduce the table above
exactly (the erratum of 23 October ended the design rows at 2000), so
both were rerun, 200 and 60 draws as above
(data/runs/sequencing/power-audit-0cf-{base,fix}.tsv), unfixed / fixed:
- year shocks: currency 1.09 / 0.95, protest 0.73 / 0.85, violence 0.78
  / 0.80, inflation 0.94 / 0.92, market crashes 1.69 / 1.67, the rest
  unchanged;
- the diagnostic, own state with shocks, OR 1.2: k = 348 .12 / .11,
  k = 10 .50 / .46, k = 1 .79 / .75; OR 1.35: .48 / .43, .84 / .79, .95
  / .93; world series unchanged;
- minimum detectable effects, pooled, realistic model: own or level
  state OR 1.30-1.31 / 1.32-1.33, Brier +0.00063-0.00068 / +0.00068-
  0.00075, log +0.0016-0.0019 / +0.0020-0.0021; world series OR 1.78 /
  1.86, Brier +0.0100 / +0.0119, log +0.031 / +0.039.
The audit's power is a few points lower than reported and the pooled
Brier bound it implies is about +0.0007, not +0.0006. The bounds the
later trials registered (+0.0006 Brier, +0.002 log) stay as registered;
they are slightly tighter than 80% power now supports, so an
"equivalent to nothing" read against them is, if anything, conservative.
No later verdict turns on the difference: T4's upper ends (+0.00018
Brier, +0.0008 log) and T8b's (+0.0003 Brier) are well inside either.

## Development figure, 11 November 2026: the base against the new benchmarks (bead bnm-56c.1, PLAN 0.3)

Not a trial and not a test: 2003-2022 is development data (PLAN.md 3.6),
and nothing is tuned on this. bnm.benchmarks adds two benchmarks beside
climatology and persistence, each a probability from what was known at
the origin:
- **last12**, the last 12 months of the same place: whether the place
  had an onset of the kind in the last 12 months published at the
  origin, (t - lag - 12, t - lag], made a probability by the level's
  past rate among rows in the same state (one pseudo-row at the level's
  rate). The literal 12 months to the origin cannot be used: with a
  known-lag of a year or more nothing in them is known, and a scored
  row is at risk only when no crisis was under way in them, so on the
  rows scored they are always quiet (0 of 97,188 year rows, 1960-2022).
- **share**, the parent's probability times the place's long-run share
  of the parent's members' known onsets (an equal share with none). It
  is meant for sub-national places under the country; here, at the
  country, the parent is the base's own subregion node.
- **clim**, the place's own past rate at the node, smoothed the same way.

The base (t48-world-norel), year nodes, country rows, origins
2003-2022; skill = 1 - loss(base) / loss(benchmark), above 0 the base
is better; the last column is the paired Brier gain over last12 per row
with its 90% interval (countries and years resampled, 400 draws).

| kind | n | onsets | Brier vs clim / last12 / share | log vs clim / last12 / share | Brier gain over last12 |
|---|---|---|---|---|---|
| banking_crisis | 2538 | 82 | -0.020 / **-0.065** / -0.054 | +0.102 / -0.053 / +0.149 | -0.0021 [-0.0047, -0.0001] |
| conflict_onset | 3306 | 272 | +0.019 / +0.130 / +0.113 | -0.006 / +0.181 / +0.162 | +0.0096 [+0.0049, +0.0160] |
| coup | 3461 | 157 | +0.121 / +0.022 / +0.012 | +0.157 / +0.104 / +0.166 | +0.0010 [-0.0009, +0.0028] |
| currency_crisis | 3222 | 117 | +0.030 / +0.026 / -0.011 | -0.023 / +0.062 / +0.061 | +0.0009 [-0.0004, +0.0025] |
| fiscal_consolidation | 2908 | 793 | +0.039 / +0.019 / +0.167 | +0.060 / +0.017 / +0.323 | +0.0037 [-0.0040, +0.0119] |
| food_price_shock | 2308 | 327 | +0.171 / +0.041 / +0.082 | +0.140 / -0.031 / +0.400 | +0.0053 [-0.0027, +0.0136] |
| growth_collapse | 3168 | 333 | +0.013 / **-0.009** / +0.022 | +0.113 / -0.024 / +0.210 | -0.0009 [-0.0022, +0.0006] |
| inflation_crisis | 3157 | 39 | +0.165 / +0.018 / +0.001 | +0.223 / +0.147 / +0.290 | +0.0002 [-0.0000, +0.0004] |
| market_crash | 798 | 142 | +0.066 / **-0.079** / -0.045 | +0.070 / -0.052 / +0.117 | -0.0127 [-0.0490, +0.0285] |
| protest_surge | 3371 | 104 | -0.028 / +0.064 / +0.043 | +0.069 / +0.185 / +0.218 | +0.0019 [-0.0011, +0.0061] |
| sovereign_default | 3179 | 101 | +0.037 / **-0.006** / +0.006 | +0.144 / -0.007 / +0.207 | -0.0002 [-0.0007, +0.0002] |
| violence_surge | 3387 | 271 | +0.166 / +0.117 / +0.053 | +0.129 / +0.152 / +0.128 | +0.0093 [+0.0073, +0.0114] |
| war_onset | 3381 | 87 | +0.075 / +0.041 / +0.068 | +0.094 / +0.158 / +0.178 | +0.0010 [-0.0001, +0.0025] |

Kinds weighed equally, the base's Brier skill is +0.066 over clim,
+0.025 over last12 and +0.035 over share; log skill +0.098, +0.065 and
+0.201.

**The base loses to the last 12 months on Brier for four kinds at the
year node: banking crises (-0.065, its interval below zero), market
crashes (-0.079), growth collapses (-0.009) and sovereign defaults
(-0.006); on log score it loses on those four and on food price
shocks.** On banking crises it loses to all three benchmarks on Brier.
At the quarter nodes it loses to last12 on Brier for banking crises
(lead -0.056), growth collapses (-0.022, -0.023), sovereign defaults
(-0.013, -0.007), currency crises (-0.002, -0.003), and at w2 also
protest surges (-0.031), coups (-0.023), war onsets (-0.009) and
inflation crises (-0.001); at the half-decade on sovereign defaults
(-0.162). Nothing is tuned in
response (data/runs/t48-world-norel/benchmarks-dev.txt has every scale).

On the watch list (top-10 recall on hard onsets), last12 ranks nothing:
a hard row has no onset in the ten years to the origin, so its last 12
months are always quiet and every row ties (recall 0.135 against the
base's 0.271, +0.136 [+0.094, +0.178]); on all rows 0.096 against 0.213
(data/runs/t48-world-norel/watchlist-base-last12.txt). bnm.watchlist's
base report prints it beside persistence and climatology from now on.

## Registered, 11 November 2026: the forward ledger (bead bnm-56c.1, PLAN 0.1)

From now on the years to 2024 are development data (PRINCIPLES.md 13):
2003-2022 has been scored about sixty times and every lattice trial
since 36 has read 2023-24 outcomes. A result counts only when a dated,
frozen forecast meets its outcome. This registers how; it is written and
committed before the first issue.

**What is issued.** The base of record, t48-world-norel (the world
without relations), run by `jolt -A:tools -M -m bnm.world-run --out
ledger-<quarter> --without-relations true --end <month>`. Every kind,
every place (countries, subregions, regions, the world) and every window
(quarter-lead, quarter-w2, year, half-decade) at each scale's latest
origin at or before the quarter's last month, with the base's
probability and, frozen beside it from what was known at the origin,
the benchmarks of bnm.benchmarks (clim, last12, share) and the watch
list's persistence and climatology scores. bnm.ledger issue writes it to
registered/ledger/<quarter>/ (forecasts.tsv, manifest.edn, README.md).

**When.** One issue a quarter, dated inside the quarter (the tool
refuses a date outside its last two months), its origin the quarter's
last month. The quarter nodes are new each issue; the year node is new
in the fourth quarter's issue (December origin); the half-decade in
December 2029, 2034 and so on. The first issue is 2026-Q4, dated 11
November 2026. The data are those on disk at the issue date: an origin
after the issue date reads nothing published after it.

**Frozen.** The manifest pins the code commit (the tool refuses
uncommitted code), the command, the jolt version, the pinned libraries
and the SHA-256 of every input the run reads (the tool records the
reads and refuses a file outside bnm.ledger/inputs or one that changes
during the run). The run must reproduce t48-world-norel byte for byte on
every row the base has (origins to December 2025), or the tool refuses:
a code change that moves the base cannot issue. Frozen means the code,
its settings and the command; the model's own online learning from data
published since is part of it, as at every origin of the base. **Nothing
is refit, retuned or re-issued between an issue and its outcome**; an
issue is never edited, and the scorer refuses a file whose SHA-256 is
not the one registered in bnm.ledger/issued (and the test suite hashes
them).

**What is counted.** Country rows whose window opens after the issue
date. Rows whose window opened before it (the 2026-Q4 issue's
half-decade, December 2024 to December 2029) and the rows above the
countries are issued for the record, not scored.

**How it is scored** (bnm.ledger score), once per issue, scale and kind:
- A window is scored when its outcomes are published: it has closed and
  the kind's catalogue, as then on disk, covers its end. The scorer
  refuses earlier, and refuses a second scoring. The outcome is the
  base's own: an onset of the kind in the window at a country at risk and
  covered (bnm.lattice-banking/onset-in?, the target the base learns
  from); rows not at risk or not covered are left out and counted.
- Brier and log loss (floor 1e-4) and skill, 1 - loss(base) /
  loss(benchmark), against clim, last12 and share, per kind and scale and
  pooled with the kinds weighed equally.
- The watch-list rule where it applies: the year node of the fourth
  quarter's issue, whose origin is the rule's year end and whose window is
  its (t, t+2]. **A hit** is an onset of the kind in the window at a
  country on the kind's top-10 list of hard rows (no onset in the ten
  years to the origin, ranked among themselves, ties at the cutoff
  sharing the slots left); recall is hits over the hard onsets, pooled
  with the kinds weighed equally, for the base and for persistence,
  climatology and the last 12 months ranked the same way. All rows,
  precision and k = 5, 20 and 10% are for the record.
- The v2 rule (bnm-82j.6 with A1-A3) applies to a change to the base: a
  candidate is issued beside the base in the same issue with its R = 8
  null draws of the same shape, and the rule decides from their forward
  scores. The first issue has no candidate.
- A claim is the result over the scored issues pooled, its interval from
  blocks of two issue years (as bnm-82j.1), with fewer than three year
  nodes scored a result is reported as it falls, not claimed. The first
  year node (2027-2028) is scorable from 2029, when the catalogues cover
  December 2028.

**Amendment L1 to the forward ledger** (11 November 2026, decided by the
user the same day; beads bnm-8ul, bnm-56c.13, bnm-56c.14). Committed on
its own, before the data are rebuilt and before any rebuilt file or
forecast is looked at. No outcome of the first issue has been read: its
first windows close in 2027 and nothing has been scored.

- *Why.* (1) The first issue read event and political files built
  through a country-name bug in scripts/slow-normalize.py: the gazetteer's
  regex ran from one entry of resources/bnm/countries.edn into the next,
  so Georgia was filed as GBR, Burkina Faso as BES, the Republic of Congo
  as COD and Svalbard as SHN (fixed in cca822d, with the ESRB's starred
  UK code and Reinhart & Rogoff's currency crashes coded 2; the episode
  library was rebuilt on the fix in 9de7195). data/slow/events.tsv,
  data/derived/econ-events.csv and political-*.csv, and data/raw/slow/rr/
  onsets.csv are still the pre-fix files, and they are inputs of the
  first issue. (2) The reproduction rule contradicts the online learning
  it freezes once the data are refreshed (bnm-56c.13): rows the base has
  with outcomes not yet known fill in when a catalogue extends, the model
  learns from them, and every later origin moves, so the next issue would
  refuse on refreshed data or issue forecasts that ignore everything
  published since. (3) What counts as a claim was left open: which
  measure, against which baseline, with which interval and decision rule,
  and published? let a partial catalogue update score a kind once with
  most rows unscored (bnm-56c.14).
- *The corrected issue is primary.* A corrected 2026-Q4 issue, issue
  2026-Q4-r1, is written to registered/ledger/2026-Q4-r1/, dated the day
  it is made, from the rebuilt data, with its inputs archived in
  data/ledger-inputs/2026-Q4-r1/. It carries the verdicts. The first
  issue, registered/ledger/2026-Q4/, stays on record unchanged: it is
  scored by the same rule on the same outcomes and reported beside the
  corrected issue, with no verdict (as rates-frozen.edn beside
  rates-frozen-v2.edn in the 2027 SFC confirmation, amendment A2 of
  docs/COUPLED-SYSTEMS.md). Its year node and the corrected issue's are
  the same windows, so they count once, as the corrected issue's.
- *The base of record* becomes t49-world-norel: t48's command
  (bnm.world-run --without-relations true) on the rebuilt data and the
  current code, compared with t48 before the corrected issue is made.
- *The reproduction rule, from the corrected issue on.* Replaces "The run
  must reproduce t48-world-norel byte for byte on every row the base
  has". Code identity is checked by rerunning the previous issue on its
  own archived inputs (data/ledger-inputs/<issue>/, whose SHA-256s must
  be the ones in that issue's manifest) and matching that issue's
  forecasts.tsv byte for byte; a code change that moves the previous
  issue's reproduction refuses. The new issue then runs on the data on
  disk at its date, refreshed, and its own inputs are archived read-only
  and hashed in its manifest. Data may change between issues: the
  manifest records each input that changed (old and new SHA-256), was
  added or was dropped. When a run's inputs are byte for byte those the
  base of record was built on, it must also reproduce the base on every
  row the base has, as before. For the corrected issue the previous issue
  is the first one (its inputs, data/ledger-inputs/2026-Q4/) and the
  base check is against t49-world-norel; for 2027-Q1 the reproduction is
  against the corrected issue's inputs, data/ledger-inputs/2026-Q4-r1/.
  Refreshing the catalogues before each issue is part of the procedure
  (bnm-56c.13: several stop 5-18 months short of what the model treats
  as known at a December origin).
- *The claim* (bnm-56c.14), fixed now, before any issue is scored:
  - **Measure.** Brier skill and log skill (floor 1e-4), 1 - loss(base)
    / loss(baseline), at the country year node, on the counted rows
    that are at risk and covered, pooled over the scored issues: per kind
    the rows of every scored issue together, then the kinds weighed
    equally. Brier is primary; log is reported beside it with the same
    interval and decision rule. Quarter and half-decade nodes are scored
    and reported, not claimed.
  - **Baselines,** each a probability from what was known at the issue
    (the run's own rows whose outcome was known by the origin, and the
    catalogue as archived with the issue): *rate*, a constant, the
    kind's observed rate over the node's country rows known by the
    origin (the level rate bnm.benchmarks smooths clim and last12
    towards); *persistence*, that rate among rows in the same state, the
    state being whether the place had an onset known in the ten years to
    the origin, smoothed with one pseudo-row at the rate (last12's
    form, a ten-year window instead of the last 12 months published);
    *last12*, as defined in bnm.benchmarks. clim and share stay for the
    record. The corrected issue freezes rate and persistence beside the
    others; for the first issue they are computed from its archived
    inputs and its run, and the scorer checks that the same computation
    gives the clim and last12 it froze.
  - **Watch list.** Top-10 recall on hard onsets at the year node as
    registered above, against persistence, climatology and last12
    ranked the same way; v2 with A1-A3 where a candidate is issued.
  - **Interval.** With one to a few issues, blocks of issue years cannot
    carry an interval (three year nodes give two blocks). Instead a
    country-cluster bootstrap: each draw resamples countries with
    replacement and takes every row of a drawn country, in every scored
    issue and kind, once per draw, so a country's rows across issues and
    kinds move together; the pooled skill (or recall gain) is recomputed
    on each draw; 2,000 draws, seed 1, the 90% two-sided percentile
    interval.
  - **Size check,** run before any issue is scored and recorded with the
    tool: on development data (t49-world-norel's year nodes at three
    year-end origins, 2016, 2018 and 2020, as three issues), outcomes
    are drawn under the null of equal expected Brier, each row's y from
    the mean of the base's and the baseline's probability, 1,000 sets
    per baseline, and the interval must claim a gain in at most 5% of
    them (one-sided; Monte Carlo error about 1.4 points). If it claims in
    more than 7.5% against any baseline, no claim is made on this
    interval until a further amendment, before the first year node is
    scorable.
  - **Decision.** The base is better than a baseline when the interval's
    lower end is above 0, worse when its upper end is below 0, else
    undecided; each baseline is reported. "The base beats the baselines"
    is claimed only when the Brier lower end is above 0 against all
    three. The watch list's gain the same way, against each of its
    baselines.
  - **Minimum.** No claim before three year-node issues are scored, as
    registered: the corrected 2026-Q4 issue's and the 2027-Q4 and 2028-Q4
    issues' year nodes, so 2031 at the earliest. Before that, results
    are reported as they fall.
  - **published?** A kind is published for scoring at a node only when
    the window has closed and the catalogue then on disk covers the
    window's end at every country with a counted row that it follows
    past the issue's origin (covered through a month at or after the
    origin): no share below all. Rows at countries it stopped following
    before the origin are left out and counted as unscored. A partial
    update can no longer score a kind once with most rows unscored.
  - The registration's sentence "(as bnm-82j.1), with fewer than three
    year nodes scored a result is reported as it falls" is a comma splice:
    a full stop was meant after "(as bnm-82j.1)". The registration is not
    edited; the interval it names is replaced above.
- *Scoring guards.* A score is recorded in a committed file
  (registered/ledger/<issue>/scores/<scale>/<kind>.edn) whose SHA-256 is
  registered in bnm.ledger/scored and checked by the test suite. The
  scorer refuses a kind and scale already registered, already on disk,
  or ever committed (in git's history), so deleting a score file does not
  allow a rescore. --kinds naming a kind the issue does not hold is
  refused as such, not as unpublished.
- Everything else in the registration stands: what is issued, when, what
  is counted, the outcome, the losses, the watch-list rule and v2.

**Note to amendment L1, 11 November 2026** (bead bnm-8ul; decided by the
user the same day, option 1 below; written before the corrected issue is
made, L1 itself unchanged). The base of record is **t50-world-norel**, not
t49-world-norel: t48's command with `--known r6-at-8ul`,
`bnm.world-run --out t50-world-norel --without-relations true --known
r6-at-8ul`. Wherever L1 names t49 as the base (the base check of the
corrected issue) read t50; the size check stays as recorded on t49.

- *Why not t49.* The base's quarter nodes pool the year with bnm.onsets'
  named model from an onsets run, and t48, t49 and the first issue read
  r6-at. (1) r6-at was fit on the catalogues with the country-code bug
  L1 names (Georgia as GBR, Burkina Faso as BES and the rest), so t49
  rebuilt everything but that input. (2) r6-at's named forecasts step at
  2025q3 for every country: CRU temperature and precipitation end in
  December 2022 (published December 2023) and pass their 30-month
  staleness in 2025q3, so the source's presence indicator went to 0 for
  every country. Its coefficient was learned from the 31 territories CRU
  never covers, then applied to every country at once: conflict x1.28
  (lead) and x1.30 (w2), coup w2 x1.72, war w2 x1.47, protest x0.75 and
  x0.73, violence x0.82 and x0.80. The factor is arbitrary (on the
  rebuilt catalogues the same code gives x0.64). The quarter nodes at
  2025-26 origins pooled with these, so **the first issue's 2025-26
  forecasts (registered/ledger/2026-Q4/) carried the step**; development
  rows (origins to 2022) did not.
- *The fix* (option 1, in the model, not for climate): a source that has
  published for a country but has nothing fresh at the origin keeps its
  last values, read only from what was published by then, and sets a
  lapse flag of its own (bnm.named/vectorizer); a country the source
  never covered reads absent as before. The flag's prior is centred on
  zero on its own scale (augur v0.18.0 logit :raw), so a pattern absent
  from the training rows moves nothing and one seen in a few rows moves
  little; it learns from outcomes as lapsed rows resolve, and a source
  that resumes is read fresh through its own coefficient. Also fixed: the
  Penn World Table inputs of bnm.onsets went stale every September (30
  months against a 24-month publication lag); they now allow 36. The
  lattice reads the same PWT inputs with 30 months, so its political-
  economy gauges are missing at September origins; fixing that would move
  the first issue's reproduction, so it is left for a decision (bead
  bnm-h8d).
- *Audit.* Of the named sources, the ones that stop for every country by
  2026: CRU climate (stale from 2025q3, political kinds), WDI resource
  rents (ends 2021, stale from 2024q3; the core's profit times rents),
  PWT profit rate and labour share (2023, stale from 2027q1 now; every
  September before), and the 24-month-lagged rents inputs of the
  institutions group (2026q3, no model of the base reads them). Only
  CRU stepped r6-at's forecasts beyond the yearly refit; the 2026q1 moves
  are the refit at the year's start, of the size seen every year. The
  lattice's gauges have no presence indicators: a missing input reads the
  unknown's mean, 0, and a group with no input anywhere drops out.
- *Control.* r6-at's command with the fix on the pre-rebuild catalogues
  (r6-at-8ul-control): the 2025q3 steps are gone (conflict x0.99 and
  x0.97, coup w2 x1.01, war w2 x0.98, protest x1.07 and x0.94, violence
  x0.98 and x0.96); the history model is r6-at's except NLD (d829c0c's
  name); the named model's development scores (2003-2022, lead and w2,
  the six kinds) are r6-at's to the fourth decimal (Brier 0.01267 and
  0.01527 against 0.01266 and 0.01522).
- *r6-at-8ul* (r6-at's command on the rebuilt data): the steps are gone
  as in the control; against the control, labels move only at GEO, GBR,
  BES and BFA, and the named model's development Brier is 0.01265 and
  0.01516.
- *t50 against t49* on development origins (2003-2022, country rows):
  Brier pooled over the 13 kinds, year -0.00000 [-0.00001 +0.00001],
  quarter-lead +0.00002 [-0.00001 +0.00006], quarter-w2 -0.00001 [-0.00002
  +0.00000]; the watch list (hard top-10) is the same on every kind; the
  kinds that move are the six with named offsets, by little (coup
  quarter-lead Brier +0.00023 [-0.00003 +0.00070] is the largest). At
  2025 origins the quarter nodes no longer step at September (coup w2
  -4.27 to -4.02 mean log odds in t49, -4.26 to -4.30 in t50). Against
  t48, t50 moves as t49 did (pooled year Brier +0.00032 [+0.00015
  +0.00049], log -0.00098 [-0.00167 -0.00030]; hard top-10 recall 0.284
  against 0.273).

**Note to amendment L1, 12 November 2026** (beads bnm-56c.23, bnm-h8d,
bnm-56c.22; decided by the user the same day; written before any issue
is made on it, L1 itself unchanged). The base of record is
**t52-world-norel**, not t50-world-norel: t51b's command with `--known
r6-at-fx`, `bnm.world-run --out t52-world-norel --without-relations true
--known r6-at-fx --leak-fixes true --pwt-stale true --set
'{:learn-offsets :mixture}'`. Wherever L1 or the note of 11 November
names t50 as the base, read t52; the size check stays as recorded on t49.
The next issue (2027-Q1) runs this command (bnm.ledger/command, with
`--end`), and still reproduces 2026-Q4-r1 on its archive with
2026-Q4-r1's own command.

- *What changed.* (1) The look-ahead fixes (bnm-56c.23, "Look-ahead
  fixes and the corrected candidate t51b-world-norel" above): in the data,
  PWT rows dated by the release that first published them (irr from PWT
  9.0, labour share and capital stock from 8.0), UIS tertiary enrolment at
  21 months, a daily FRED series' incomplete last month and EM-DAT's year
  to date left out; in the code, under `--leak-fixes`, Maddison, NMC and
  banking's PWT read no later than their releases and urban growth in one
  unit across 2016. (2) PWT read at 36 months by the lattice's political
  economy gauges, which went missing at every September origin
  (bnm-h8d, `--pwt-stale`). (3) Quarter nodes learn at the offsets they
  issued over, not their mean (bnm-56c.22, `:learn-offsets :mixture`).
  (4) The onsets run the quarters pool with is r6-at-fx, r6-at-8ul's
  command on the corrected data/slow/series.tsv (r6-at-8ul was fit on the
  leaky one). (5) bnm.ledger/manifest-args split a manifest's command at
  every space, so a command with `--set '{...}'` could not have been rerun;
  the command is now written with such arguments quoted and read back as
  one (both registered commands read as before).
- *r6-at-fx against r6-at-8ul.* The named model reads neither the PWT
  inputs (those are the polecon models') nor tertiary enrolment, so only
  the dropped partial FRED month and EM-DAT year reach it: no row added or
  dropped, no outcome changed, development scores (2003-2022, lead and
  w2, the six kinds) equal to the fifth decimal, the 2024q3, 2025q3 and
  2026q1 steps the same; named rows move only at 2026 quarters (1908 rows
  above 1e-4, largest 0.063, war lead MMR). Run kind by kind and spliced
  (data/runs/r6-at-fx/README.txt). So t52 is byte for byte t51b on every
  row to December 2025 (the run was made as t52b-noclim and renamed
  t52-world-norel); the 2027-Q1 issue's 2026 quarters are where r6-at-fx
  differs.
- *Why.* Correctness: each change removes a read of something not yet
  published or a mis-read of what was. None was chosen for its score.
- *The honest score.* t52 against t50 (year nodes, 2003-2022, thirteen
  kinds weighed equally, Brier, + = t52 better): 2003-06 -0.0014, 2007-09
  +0.0008, 2010-12 +0.0014, 2013-19 -0.0026, 2020-22 -0.0019, pooled
  -0.0013 [-0.0026, +0.0002], no fold lost (every 90% interval spans zero
  but 2010-12's, a gain); log -0.0051 [-0.0094, -0.0001]; quarter-lead
  -0.0006 [-0.0011, -0.0002]. The look-ahead fixes alone (fx-leak) are
  -0.0001 at the year and -0.0008 at the quarter-lead: t50's quarter skill
  was overstated by about that, and the lower score is the honest one.
  Market crashes carry most of every move (year -0.012 pooled).
- *Rules.* Planted (t51b's harness, which t52's equals to December 2025):
  +0.0102 [+0.0071, +0.0135], met. Noise: -0.00083 against 0.0003, not
  met; the same rule fails on fx-leak (-0.0019), without h8d or .22, so
  the failure is the base's posterior weighting (a noise group flips the
  year's selection, docs/LATTICE.md), inherited, not brought by these
  fixes: sequencing under PLAN principle 9 (the weighting is step 5).
- *Not in.* bnm-56c.26 (a node with no resolved outcome issues 0.5; food
  price shocks at 2001-06 origins) was tried as `--clim-prior kinds`
  (t52a-climprior): food's early p fell to 0.24 but its year node then ran
  to 0.98 at 2012, and three folds were lost (pooled -0.0081 against
  t51b). Not adopted; the option stays off, and the instability is bead
  bnm-56c.27.
- *Reproduction.* With this code 2026-Q4 and 2026-Q4-r1 each come back
  byte for byte from their archived inputs with their own commands.

## Registered, 11 November 2026: one pooled hazard over the buffer invariants, and the scanner (bead bnm-56c.6, PLAN 1.5-1.7)

Written and committed before any model is fitted to break labels. The
hypothesis (PLAN.md phase 1, principles 5 and 6): breaks of different
stocks share a form in the invariants, which are times or ratios, so a
hazard fitted on some families of breaks finds the breaks of a family it
has never seen. Nothing in the code names a stock type: families are
labels from resources/bnm/episodes/rules.csv and stocks are rows of a
data mapping. 2003-2022 (and 2023-24) are development data
(PRINCIPLES.md 13); every result below is a development result except
the forward ranking, which is a dated forecast.

**Inputs, frozen.** The episode library as of commit 9de7195 (rules.csv
SHA-256 66f96d17..., episodes.csv f46836e5...), its panel
data/derived/episodes/panel.csv (0a41934e070dd766d7be3d1c4f9375ee8a1043b696ccaea2d21d4742986cea86),
data/slow/series.tsv (4c02c698...), and data/derived/buffers/*.csv as on
disk today (README there); the run records every input's SHA-256.

**Training and scored rows.** A row is a panel place-year of one family.
Its forecast date (origin) is 1 January of the year; its label is an
onset of the family at the place in the window. Windows: **12 months**
(onset in the year; the window of every verdict) and 24 months (the
year or the next, reported beside it). A model used at origin T trains
only on rows whose label is known by T (panel `known`, and for 24 months
the next year's row too), with `ongoing` empty and `covered_by` not
empty (a break outside any catalogue's coverage has no non-events
beside it); the same rule selects scored rows. Features are computed
by bnm.buffers as of the origin from observations known by then
(series.tsv's own known dates, else the mapping's lag). A row with no
invariant measured is neither trained on nor scored (the scanner cannot
rank an unmeasured stock). Rows whose features come from a series the
mapping flags `:revised-vintage` (current revised data read with an
assumed lag, not real time) are marked; a sensitivity run sets those
invariants missing and repeats test (a).

**Families**, frozen as in rules.csv at 9de7195: asset_price_break,
banking_break, commodity_stockout, energy_break, investment_bust,
price_level_break, reserve_crisis, sovereign_debt_break. Reserve
crises: the main result labels as breaks only episodes with at least
one catalogue other than the IMF GRA drawing rule, and, for onsets from
1970, other than Reinhart-Rogoff's currency column; place-years whose
only breaks are GRA-only or RR-currency-only after 1970 are dropped
(neither break nor non-event). A secondary result counts them as breaks
(with and without GRA-only episodes reported separately). In panel
counts: 649 main episodes, 356 GRA-only, 161 RR-only after 1970; 300
main positive place-years with the stock measured.

**Thin families.** A family gets a pass or fail only with at least 30
positive place-years scored in test (a) (12-month window, main labels,
an invariant measured). energy_break (13 positive place-years measured
in the panel) gets no verdict whatever the count: each of its episodes'
rank is reported, and it is also left out together with
commodity_stockout as one physical-stocks fold, which gets a verdict
under the same rule.

**Stocks** (a data mapping, resources/buffers/hazard-stocks.edn; one
record per family and place, plus stocks with no family for the
scanner). Each record lists its series by bnm.buffers role:
- reserve_crisis, price_level_break: stock = IMF IL reserves less gold
  (monthly, lag 2 months); gross outflow = WDI imports of goods and
  services (annual, lag 1 year, revised); claims = WDI external debt
  against income = GNI (lag 1 year, revised).
- sovereign_debt_break: the same reserves stock; gross outflow = WDI
  external debt service (rigid 1.0); claims external debt against GNI.
- banking_break: claims = BIS credit to the private non-financial
  sector (series.tsv finance/credit_gdp, % of GDP, against income 100);
  rigidity = BIS debt service ratio (finance/dsr_private, the share of
  income committed to debt service); displacement measured on the
  claims.
- commodity_stockout: stock = USDA PSD ending stocks of wheat, maize
  and rice summed (the place's, or the world's), gross outflow = their
  domestic consumption (market year, lag 1 year, revised).
- energy_break: EUR: Eurostat gas in storage (EU27_2020, closing, TJ)
  against inland consumption; USA: EIA working gas against consumption;
  JPN: JODI crude closing stocks against refinery intake (monthly, lag 2
  months). WLD has no record (unmeasured).
- investment_bust: inflow = gross investment share of GDP
  (series.tsv economy/investment_share, revised), displacement measured
  on it.
- asset_price_break: price = share prices (series.tsv
  markets/share_prices), displacement measured on it.
- Scanner-only stocks (no family, no label): EIA US crude less SPR, the
  SPR, gasoline and distillate (against product supplied); JODI crude
  closing stocks of every reporting country (against refinery intake);
  Eurostat gas storage of each member reporting; world wheat, maize and
  rice (PSD); US investment by asset type (BEA NIPA quantity indexes:
  data centres, computers and peripherals, communication equipment,
  software, power, communication structures, petroleum and natural gas
  structures, mining and oilfield machinery; Census C30 data-centre
  construction, monthly); US industries' claims (SEC frames by two-digit
  SIC: long-term debt against operating cash flow, capex as the inflow).
  `:sector` marks energy and US investment records for test (d) only;
  the model never reads it.

Sub-annual series are read on an annual grid at the latest known month
(the same calendar month in earlier years), so that displacement
windows are years and seasonal stocks compare; flows are rates per year
(the mean of the last 12 months).

**Features** (the invariants of bnm.buffers, fixed now):
- cover over net drawdown, 1-year window: four cumulative bins, cover
  below 0.25, 0.5, 1 and 2 years (not draining or capped counts as above
  2). This is the non-linearity allowed in cover: the literature says
  the break is convex in cover and comes before exhaustion; the bins let
  the hazard rise faster near exhaustion, monotonicity is not imposed
  and is reported;
- log cover, 3-year window (capped at 100 years, floored at 0.01);
- drain rate, 1-year window, clipped to [-2, 2] per year;
- gross cover (usable stock over the gross outflow, years: stocks-to-use,
  reserves over imports; added to bnm.buffers as `gross-cover`): its log
  and three cumulative bins, below 0.125, 0.25 and 0.5 years;
- claim gap, log years of income;
- rigidity (share);
- restoration, carry, idle share, holder concentration and leverage,
  market reading: entered when measured; no series on disk measures
  them for these stocks today, and an invariant measured on fewer than
  1% of a fit's training rows is dropped from that fit (reported);
- displacement at 5, 10 and 20 observations (signed z, clipped to +-5),
  resonance, log(1 + stock x flow);
- one missing indicator per invariant; a missing value is set to the
  training mean.
Breadth (across stocks at a date) is not used: it is the same for every
row of a year.

**The model.** A discrete-time hazard: logit P(onset in the window |
x) = a + a_f + sum_j (b_j + d_fj) x_j, x standardized on the training
rows. Pooled slopes b_j ~ N(0, 1) on the standardized scale; a free.
Family deviations: a_f ~ N(0, t_0^2), d_fj ~ N(0, t_j^2), each t with a
half-normal prior (scale 1.0 for the intercepts, 0.5 for the slopes),
fitted by Laplace EM to the posterior mode as augur.hier/intercepts
does for intercepts (empirical Bayes partial pooling: a family with
little data stays near the pooled slopes). Fitted in augur (a new
function for grouped slopes, released and pinned). A family left out,
and any stock with no family, is scored by the pooled part alone (a_f =
d_f = 0). The scanner's score is the pooled hazard, a + b.x, for every
stock; family deviations are reported beside it.

**Development folds** (PRINCIPLES.md 13, bnm.dev-folds). Rolling
origin over test years 1980-2024 (12 months; 1980-2023 for 24 months),
refitted every five years (1980, 1985, ..., 2020), each block's fit
trained on rows whose origin is at least the gap before the block's
first origin, gap = window + 12 months (the catalogues' known-lag of up
to 11 months; 24 and 36 months), with labels known by that origin, and
rows whose label window touches a global episode
(bnm.dev-folds/global-episodes) that a scored row's window touches
dropped. As a development diagnostic the last 20% of origins (2016-2024)
are held out, trained on origins to 2014; labelled development, not
validation. Seeds: augur.math/rng-mixed, base seed 56, mixed with the
stream's index.

**Tests.**
- **(a) Leave one family out.** For each family (and the
  physical-stocks fold), each block's model is fitted with that family's
  rows removed and scores its rows. Pass when all three hold on the
  family's scored rows pooled over 1980-2024: the AUC's 90% interval
  from a country-cluster bootstrap (places resampled, 1000 draws) lies
  above 0.5; recall at the top 10% of its rows is above 0.10 (precision
  there above the family's base rate); and the AUC is above both
  baselines' AUCs: the family's own lagged stock level alone (a
  logistic on the log of the latest known value of the record's main
  series, fitted on the family's own rows in the same blocks), and the
  last 12 months (a break of any family at the place known in the 12
  months before the origin; the same-family form is reported, though
  after dropping ongoing rows it is nearly always quiet). The
  differences from the baselines get paired cluster-bootstrap intervals,
  reported, not required. Fail otherwise; no verdict under the thin
  rule. Reported for main and secondary reserve labels, and with the
  revised-vintage invariants removed.
- **(b) Invariants against channels.** The pooled model (all families,
  the scored family included) against a channel model per family: a
  logistic (augur.logit/fit, prior 1) on the family's raw series, the
  log of each one's latest known value and its change over a year, with
  missing indicators, fitted on that family's rows only; both on the
  same rolling blocks. AUC per family and era (1980-1994, 1995-2007,
  2008-2024) with the cluster-bootstrap interval of the difference. The
  invariants hold up if the pooled model's AUC is at least the channel
  model's in each era with the families weighed equally, and in a
  majority of family-era cells with at least 10 positives.
- **(c) False alarms.** Each record gets five decoys whose series'
  values are shuffled in time (augur.math/rng-mixed, seed 56 mixed with
  the decoy and record index; each series its own stream), carrying the
  original row's label. The rolling-origin pooled model (trained on real
  rows only) scores real and decoy rows; each origin's real and decoy
  rows are ranked together. Pass when the precision of real rows in each
  year's top decile, pooled over years, exceeds the decoys' with the
  cluster-bootstrap interval of the difference above zero. The decoys'
  share of the top decile is reported against their share of the rows
  (5/6).
- **(d) Forward.** The scanner as of the forward ledger's 2026-Q4 issue
  date (11 November 2026), from a model fitted on every row known then,
  ranks every stock record with an invariant measured, by the pooled
  hazard of an onset in 12 months. The registered questions: does any
  energy stock (EU or US gas storage, US crude or product stocks, JODI
  crude stocks) rank in the top decile, unprompted; and does any US
  investment build-out record (BEA data centres, computers, communication
  equipment, software, power; Census data-centre construction) rank in
  the top decile. Each yes or no; not entering them by hand is the
  test, and a no is a finding. The ranking is written to
  registered/ledger/2026-Q4/scanner.tsv, a new file beside the issue
  with its own manifest (scanner.edn: date, commit, command, inputs'
  SHA-256); the issue's existing files are not touched. It is scored
  when the 2027 catalogues are published.

**Illustrations** (not tests): the rank of each hindcast in
resources/bnm/episodes/hindcasts.csv among all stocks at origins before
its break, from the rolling-origin model (Asia 1997, food 2007-08, Sri
Lanka 2022, European gas 2021-22, telecom 2001, US shale 2015).

**Hidden buffers (1.7), flags only.** A stock is flagged when its
accounts stop closing: the SFC core's net-lending identity residual
(data/derived/sfc/flows.csv, the five sectors' B9 summed) or the gap
between two measures of one stock (IMF IL against WDI reserves, IRFCL
official reserve assets against IL, EIA against JODI US crude, PSD world
against the sum of countries) above 2% of the stock and stretched past 2
against its own past (bnm.invariants/stretch, 10 observations). Not a
scored test.

**Known limits.** The episode library is fixed for the gazetteer bug;
the features read series.tsv, built by scripts/slow-normalize.py with
the same name lookup, so the run reports how many feature rows fall on
the affected codes (GBR, GEO, COD, COG, BFA, BES). Unmeasured stocks
cannot be ranked. Episodes per family are few; pooling across families
is itself the hypothesis. A shock from outside (2020) is not
predictable.

## Development result, 11 November 2026: links that cost nothing (bead bnm-56c.2, PLAN 0.4)

Development, not a test: origins 2003-2022 (PLAN principle 6). The base
of record stays t48-world-norel; this gives a candidate weighting, opt-in,
not a new base. Acceptance was fixed before the full runs: with the noise
group at the quarter nodes (bnm.fast-run --family noise, nine standard
normals, seeds 1-4, the fast-signal trials' shape), the year nodes' mean
paired Brier against the same weighting without it within 0.0003 of
zero (bnm.links --noise, beside it the v2 interval for a fresh draw,
A3's form); and the candidate not losing to t48 on development folds.

**Where the cost comes from.** On t48 the four noise draws cost the
year nodes -0.00193 (sd 0.00035; -0.0023, -0.0017, -0.0016, -0.0021).
About 80% of it is two late-catalogue kinds: market crashes (-0.016 to
-0.025 per draw) and food price shocks (-0.002 to -0.008); coup, growth
collapses and protest surges add -0.0002 to -0.0005 each, the rest
nothing. Small runs on those four kinds (market crashes, food price
shocks, coup, growth collapses; noise seed 1, each against its own
setting without noise; cost with t48's settings -0.0077):
- the averaging's forgetting at 1.0: -0.0083; the coupling off: -0.0100;
  the noise not nested, so the all-inputs model reads it: -0.0069. None
  of these is the channel;
- the quarters' offset from the year (:downscale) off: +0.0007 [-0.0003,
  +0.0018]. The cost runs through a loop: the year's forecast comes down
  as the quarters' offset, their states go back up as the year's :up
  inputs, and a small change in them flips the year node's model
  average from one model to another. In market crashes the base puts
  0.99995 on the all-inputs model in 2010, the noise run 0.999999 on
  the world group, 2010-2016; growth collapses flip to exposure in 2012.
  The noise model's own weight at the quarters is incidental: the year
  selects rather than blends, so any perturbation reshuffles it.
Dropping the downscale is no candidate (its base loses -0.0046 on the
four kinds), nor is passing the quarters' states up net of what came
down (:up-net-of-downscale): its base loses -0.0134 [-0.028, -0.002]
and its noise draws swing +0.009 to +0.012. Reading its own forecast
back helps the year; what costs is the sharp selection.

**Candidates** (four kinds, noise seed 1, cost against the candidate's
own base; the base against t48):
- t48's posterior weights: -0.0077;
- link coefficients held out of the discount (:hold-links, augur's
  :hold): -0.0052, partial;
- tempered posterior weights (augur.dynamic :weighting {:rule :temper},
  each period's likelihoods to a power), 0.05: +0.00004 [-0.0005,
  +0.0005], base +0.0008; 0.01: +0.00017 [+0.0001, +0.0003], seed 2
  +0.00018, base +0.0002;
- stacked weights (exponentiated gradient on the mixture's log score,
  rate 0.01, floor 0.1): +0.00016, seed 2 +0.00017, base -0.0001;
- tempering at the year only (0.01): +0.00008, base +0.00001.
Stacking and tempering remove the cost alike. Tempered at 0.01
everywhere was chosen: the smaller change (one parameter, the weights
keep their posterior form and its penalty on a model's extra
uncertainty) and its base slightly the better. On synthetic data
(augur's tests) stacking keeps a noisy copy of a good model near half
the weight: blending is not ignoring.

**The candidate** (l04-t01, --set '{:weighting {:rule :temper :power
0.01}}', thirteen kinds, 1870-2025; noise draws l04-t01-noise-1..4):
- **noise cost +0.00005** (+0.00005, +0.00005, +0.00004, +0.00006; sd
  0.00001; fresh draw [+0.00002, +0.00008]): **within 0.0003 of zero,
  accepted.** The empty group now costs nothing; it nudges the year
  nodes very slightly up;
- against t48, year nodes, 2003-2022: Brier -0.00019 [-0.00276,
  +0.00214], log -0.0026 [-0.0091, +0.0037], level; quarters +0.00014
  [-0.00053, +0.00073]. Watch-list rule, top-10 recall on hard onsets
  0.261 against 0.271, -0.010 [-0.037, +0.023];
- by development fold (bnm.links --folds: blocks of origin years, each
  of bnm.dev-folds' global episodes in one; the forecasts are online,
  so rolling origin holds by construction): 2003-2006 -0.0019 [-0.0079,
  +0.0027], 2007-2009 -0.0096 [-0.0155, +0.0030], 2010-2012 +0.0032
  [-0.0044, +0.0100], 2013-2019 +0.0022 [-0.0008, +0.0056], **2020-2022
  -0.0020 [-0.0032, -0.0006], a loss**, carried by food price shocks
  (-0.021: the 2022 food crisis, which the base's sharp selection
  caught). Year-only tempering and stacking lose that fold too (-0.0058
  and -0.0052 on the four kinds); forgetting at 1.0 does not but keeps
  the cost.

**Verdict.** The noise rule is met; the fold rule is not cleanly met:
level pooled and in four folds, a loss of 0.002 in 2020-2022 (one of
five folds at one-sided 5% each; a false loss in some fold has about a
23% chance, so it may be noise, but it is recorded as a loss). Not
adopted; t48 stays the base. The weighting is opt-in
(bnm.lattice :weighting / :weighting-at; bnm.fast-run --set), and a test
(test/bnm/links_test.clj) reruns market crashes with the defaults
against t48-world-norel byte for byte. augur v0.14.0 adds slopes pooled
across groups of places (augur.dynamic :by), v0.15.0 the tempered and
stacked weights and held coefficients; the lattice does not pool slopes
by region yet (bnm-56c.12).

**Review** (same day, development). Tempered at 0.01 is close to equal
weights: at 2022 the quarter nodes' weights have normalized entropy
0.96-0.99 in all thirteen kinds, the year nodes' 0.91-1.00 in ten (coup
0.61, conflict onsets 0.76, banking crises 0.79); under t48 the year's are
0.06 or less in eleven. The four noise draws' cost is a small gain
(+0.00005, every draw's interval above zero): one more model in a near
even blend, not an ignored one. It does not ignore everything, though. A
planted signal (bnm.fast-run --family planted --strength 1: the noise
group with its first column moved by 1 where the quarter's outcome
happens, so it reads the future; four kinds, against each setting
without it):
- t48's posterior weights: the quarters +0.00097 [-0.00083, +0.00289],
  the year nodes -0.0056 [-0.0146, +0.0024] (market crashes -0.018): the
  quarters put 0.64-1.00 on it and the year's selection flips as it does
  for noise. Under posterior weights a real link at the quarters does
  not reach the year either;
- tempered 0.01 everywhere: quarters +0.0019 [+0.0006, +0.0036], year
  +0.00067 [+0.00034, +0.00100]; the planted group earns 0.18-0.63 at the
  quarters (1/n 0.10-0.14) where the noise group holds 0.06-0.24;
- tempered 0.01 at the year only: quarters +0.0033 [+0.0016, +0.0054],
  year +0.00087 [+0.00053, +0.00127]; the quarters select (0.73-1.00 on
  it) and the year blends. With its noise cost (+0.00008) and base
  (+0.00001) this is the better candidate; it loses the 2020-2022 fold
  alike (-0.0058 [-0.0095, -0.0024] on the four kinds).
Not better (four kinds, noise seed 1; cost against the same setting
without noise, base against t48's): tempered at the year only at 0.2,
cost -0.0039, base -0.0033; at 0.5, -0.0046, base -0.0013; the year
reading the quarters' states a year late (:up-lag {:year 1}), +0.0027
[-0.0005, +0.0064], base -0.0131 [-0.0247, -0.0019]; a prior of 0.5 on
the base model (:weight-prior, augur v0.17.0 :prior), -0.0025, base
-0.0078. A prior that strong does not stop a selection made on a
period's likelihood over every row.

The fold rule is not in the bead (its description has only the noise
rule); the blocks were fixed in bnm.links before the candidates ran.
The 2020-2022 loss is food price shocks alone (without them the fold is
-0.0004 [-0.0015, +0.0008]), at origins 2020, 2021 and 2022 (-0.019,
-0.030, -0.009): one episode, and an interval over three origin years.
Defaults: a 13-kind rerun with --family none matches t48-world-norel in
all 39 files. augur v0.17.0 also fixes the predictive mean of a model
with :hold or :by (scaled by 1/sqrt(discount)); c-hold-noise ran with it
and is slightly off.

**Ranking signals** (same day, development; the user's question: can a
weaker signal enter as a secondary input that stronger ones override,
rather than winner-take-all selection or an even blend?). Literature
(URLs in bead bnm-56c.2's notes): dynamic model averaging selects by
construction (Raftery, Karny & Ettler 2010; McCormick et al. 2012;
Minka's "BMA is not model combination"), and tempering it (Grunwald & van
Ommen 2017) slows but does not stop that; stacking and optimal pools
(Yao et al. 2018; Geweke & Amisano 2011; Del Negro et al. 2016) weigh a
model by its marginal gain to the pool; Bayesian predictive synthesis
(McAlinn & West 2019) regresses the outcome on the models' forecasts with
dynamic coefficients that also calibrate; horseshoe-type shrinkage
(Carvalho, Polson & Scott 2010; Kowal, Matteson & Ruppert 2019;
Cadonna et al. 2020) keeps every signal in one model and shrinks the weak
ones; boosting and encompassing tests (Buhlmann & Hothorn 2007; Ng 2014;
Harvey, Leybourne & Newbold 1998) add a weaker signal only for what the
stronger ones miss. Three candidates, built in augur v0.19.0 (opt-in):
- **(A) ranked increments** (bnm.lattice :weighting {:rule :evidence
  :scale c}): each model's discounted log score (each period's, before it
  learns, discounted by the averaging's :forget), less its parent's, per
  discounted row, is its gain g (the parent is the base model; a nested
  link's is the all-inputs model). Weight g+ / (c + sum of g+), the base
  c / (c + sum); the forecast's log odds are the base's plus each
  increment over its parent times its weight, and taken strongest first no
  increment moves it further than the largest stronger one. c = 0.005
  nats per row, set from the gains' size in a run without the fast group
  (the year node's base share in market crashes 0.07), before any score
  was read;
- **(B) group shrinkage, no averaging** (:only #{:all} :relation-nest nil
  :group-shrink {:tau 0.5}): one all-inputs model, each group of its
  coefficients pulled to zero by a pseudo-observation with variance tau^2
  u / (1 - discount), u the group's horseshoe scale (the mode of a
  half-Cauchy local scale given its coefficients' second moments), so the
  prior is refreshed as fast as the discount forgets it;
- **(C) a dynamic synthesis** (:weighting {:rule :synth :discount 0.99
  :var 0.05}): a dynamic logistic of the outcome on an intercept and each
  model's linear predictor, starting from an even log pool.

Acceptance, stated before the runs: the noise cost at the year nodes
within 0.0003 of zero (noise seeds 1 and 2, each against the same
setting without it); the planted signal (strength 1) gaining at the year
nodes with an interval above zero; no development fold lost with an
interval below zero. Four kinds (market crashes, food price shocks, coup,
growth collapses), every run on t48's archived inputs (bnm.fast-run
--archive data/ledger-inputs/2026-Q4: t48's base comes back byte for byte
on the four kinds; the data on disk were rebuilt). Brier, 2003-2022:

| setting | noise cost, year (seeds 1, 2) | noise, quarter | planted, year | planted, quarter | base against t48, year | 2020-2022 fold | folds lost |
|---|---|---|---|---|---|---|---|
| t48 (posterior) | -0.0077, -0.0055 | -0.0028, -0.0008 | -0.0056 [-0.0146, +0.0024] | +0.0010 [-0.0008, +0.0029] | - | - | - |
| tempered 0.01, year only | +0.00008, +0.00006 | -0.0001, 0.0000 | +0.00083 [+0.00047, +0.00118] | +0.0033 [+0.0015, +0.0055] | +0.00001 [-0.0084, +0.0081] | -0.0058 [-0.0095, -0.0024] | 1 |
| (A) ranked increments | -0.00002, -0.00000 | -0.00003, -0.00000 | +0.00106 [+0.00019, +0.00200] | +0.0013 [+0.0004, +0.0020] | -0.0024 [-0.0105, +0.0050] | -0.0077 [-0.0115, -0.0038] | 1 |
| (B) group shrinkage | +0.0008, +0.0018 | +0.0002, +0.0001 | +0.0034 [-0.0005, +0.0073] | +0.0028 [+0.0011, +0.0044] | -0.0026 [-0.0105, +0.0050] | -0.0101 [-0.0187, -0.0034] | 2 (also 2007-2009) |
| (C) synthesis | -0.0004, -0.0037 | -0.0001, -0.0002 | +0.0031 [+0.0003, +0.0058] | +0.0029 [+0.0007, +0.0056] | -0.0089 [-0.0169, -0.0013] | -0.0085 [-0.0125, -0.0047] | 3 and pooled |

- **(A)** meets the noise rule (-0.00001) and the planted rule; it loses
  2020-2022, food price shocks -0.029 there, as every blend does. Its
  quarters are slightly better than t48's (+0.0006 [-0.0014, +0.0024]); its
  year nodes lose food price shocks (-0.0097 over 2003-2022);
- **(B)** blends the groups and gains most from the planted signal, but a
  noise group in the one model moves it: +0.009 in food price shocks,
  -0.006 in market crashes, so the mean cost (+0.0013) fails;
- **(C)** learns the planted signal but its noise cost swings between
  draws (-0.0004, -0.0037; market crashes -0.011) and its base loses three
  folds: a regression on nine to twenty nearly collinear forecasts is
  itself fragile.

**How the weights move** (bnm.links --weights; country year nodes,
2003-2022, normalized entropy, rank agreement of consecutive years'
weights, and the year node's order with the noise group against without
it, the same year):
- t48: entropy 0.00-0.29, the leading group changing up to six times,
  rank agreement 0.53-0.91; with noise the order agrees 0.70 in market
  crashes, the leader different in 15 of 20 years;
- tempered at the year: entropy 0.68-1.00, agreement 0.70-1.00; with noise
  0.95-0.99, the leader different in up to 3 years;
- (A): entropy 0.50-0.88, agreement 0.87-1.00; **with noise the order is
  unchanged** (0.98-1.00, the leader the same every year, the weights
  moving 0.000-0.096 in sum). A ranks: in market crashes 2022 the
  all-inputs model 0.62, economy 0.16, fiscal 0.08, the base 0.07. The
  planted group leads the quarters at 0.60-0.72 in three kinds (food
  price shocks, coup, growth collapses; 0.25 in market crashes, where the
  all-inputs model leads); the noise group gets 0.000;
- (B) and (C): the order with noise agrees 0.85-0.99 but the leader
  differs in up to 9 years.
Why (A) loses food price shocks: there its year node's models all gain
about as much over the base (top weight 0.13, entropy 0.85 in 2022), so
weights proportional to the gain are near even; t48 put 1.000 on the
all-inputs model, which was about 0.001 nats a row better and happened to
be right in 2022. A gain measured from the base is mostly what every
model shares; proportional weights do not let a slightly stronger model
override. That is the open problem: measuring each increment against the
stronger models rather than the base (an online encompassing test, or
the synthesis with a shrinkage prior) is the next candidate, not tried.

**(A) on thirteen kinds** (rk13-A-*, archived inputs):
- noise cost +0.000005 (+0.00001 [-0.00004, +0.00005], +0.00000 [-0.00001,
  +0.00002]; quarters 0.00000; fresh draw [-0.00001, +0.00002]): **within
  0.0003 of zero, accepted**, the smallest of any weighting so far;
- planted: year **+0.00157 [+0.00108, +0.00211], accepted**, quarters
  +0.00150 [+0.00104, +0.00204]; no kind loses at either (fiscal
  consolidation most, +0.015 at the year); the planted group leads the
  quarter weights in 11 of 13 kinds (0.45-0.87);
- against t48, year nodes: Brier -0.00115 [-0.00367, +0.00120], log
  -0.0065 [-0.0144, +0.0001]; quarters +0.00025 [-0.00041, +0.00085]. By
  fold: 2003-2006 -0.0019 [-0.0073, +0.0025], 2007-2009 -0.0103 [-0.0187,
  +0.0029], 2010-2012 +0.0034 [-0.0014, +0.0079], 2013-2019 +0.0004
  [-0.0021, +0.0031], **2020-2022 -0.0030 [-0.0045, -0.0015], a loss**:
  food price shocks -0.029, conflict onsets -0.0068, currency crises
  -0.0035, market crashes -0.0038;
- year-node entropy 0.50-0.89 in every kind (t48: 0.00-0.29), the order
  stable (agreement 0.87-1.00).

**Verdict.** (A) answers the question in part: signals can be ranked by
accumulated out-of-sample evidence, a group that carries nothing gets no
weight and moves nothing, and a real one at the quarters earns weight and
reaches the year (the first weighting where both hold with intervals
clear of zero). It does not let a slightly stronger model override the
rest, and loses the 2020-2022 fold by 0.003, so by the rule fixed before
the runs it is not accepted. Not adopted; defaults unchanged (the default
run reproduces t48-world-norel byte for byte on market crashes and food
price shocks, test/bnm/links_test.clj). If adopted it enters by a ledger
amendment at the 2027-Q1 issue.

## Results, 11 November 2026: the pooled hazard and the scanner, as registered (bead bnm-56c.6)

Run once at commit d0abc90 (`jolt -A:tools -M -m bnm.hazard-run`, 54
minutes; data/derived/hazard/report.txt, preds.tsv, hidden.tsv). Before
it the code ran once on labels drawn at random (`--smoke true`, no
result read), which found two bugs, fixed in d0abc90 before the run: a
channel model's inputs had one length per record rather than per
family, and a stock reported as zero at every date (Eurostat gas storage
of countries with no storage site, PSD ending stocks of countries that
keep none) was read as an empty buffer; it is now not measured. Nothing
else changed from the registration. 1040 stock records (822 with a
family); 41,264 place-family-years with an invariant measured. All
results but (d) are development results.

Inputs. The registration said the run records every input's SHA-256;
the tool did not (scanner.edn has only its own file's hash), so they are
recorded here. The run read its inputs at its start (07:34; it ended at
08:27). data/slow/series.tsv was rebuilt in the shared data directory at
08:12 (bnm-8ul), after it was read: the run used the registered vintage
4c02c69815abe3e4d7563816d42d6d03949757c1e654979991cbccb00adbd3b2, which
is no longer on disk. panel.csv (0a41934e...), episodes.csv and rules.csv
at 9de7195, every data/derived/buffers/*.csv and data/derived/sfc/flows.csv
(1ee51d5e...) are the hashes listed in the registration and unchanged.
A rerun on the rebuilt series.tsv will not reproduce these numbers.

Gazetteer exposure (bnm-8ul): no feature reads events.tsv or
political-*.csv. 11,509 rows read data/slow/series.tsv (private credit,
investment, share prices), built with the same name lookup; 352 of them
are on the affected codes (GBR, GEO, COD, COG, BFA, BES), 17 positive.

**(a) Leave one family out** (12 months, main labels, 1980-2024; AUC
[90% country-cluster interval], recall at the top 10%, the baselines'
AUC: own lagged level, last 12 months of any family):

| fold | n | breaks | AUC | recall@10% | level | last12 | verdict |
|---|---|---|---|---|---|---|---|
| asset_price_break | 1534 | 167 | 0.528 [0.494, 0.563] | 0.150 | 0.470 | 0.541 | fail |
| banking_break | 1777 | 65 | 0.663 [0.600, 0.728] | 0.277 | 0.359 | 0.552 | **pass** |
| commodity_stockout | 2642 | 225 | 0.498 [0.462, 0.538] | 0.124 | 0.490 | 0.478 | fail |
| energy_break | 83 | 10 | 0.490 [0.389, 0.539] | 0.100 | 0.370 | 0.356 | no verdict (thin) |
| investment_bust | 5901 | 192 | 0.510 [0.480, 0.540] | 0.104 | 0.505 | 0.548 | fail |
| price_level_break | 6114 | 120 | 0.591 [0.540, 0.646] | 0.192 | 0.643 | 0.647 | fail (below both baselines) |
| reserve_crisis | 6927 | 239 | 0.544 [0.502, 0.584] | 0.184 | 0.556 | 0.527 | fail (below its level) |
| sovereign_debt_break | 7082 | 177 | 0.543 [0.501, 0.583] | 0.186 | 0.504 | 0.536 | **pass** |
| physical (commodity + energy) | 2725 | 235 | 0.506 [0.467, 0.540] | 0.123 | 0.498 | 0.473 | fail |

Two of seven families with a verdict pass, sovereign debt only just
(interval 0.501). Banking's pass is the clearest: 0.663 against 0.552
for the last 12 months (+0.111 [+0.033, +0.192]); its features are the
private-credit stretch and the debt service ratio, so what transfers is
largely the generic stretch learned from other families' stocks. The
physical stocks (grain, energy) are not found from the other families:
AUC 0.50. Reserve crises with every catalogue's breaks counted: 0.560
[0.535, 0.584] (543 breaks); without GRA-only breaks 0.558 [0.525,
0.591]. energy_break's ten scored episodes ranked 8th to 83rd of 83
(USA 2005 8th, USA 2014 14th, EUR 2021 50th, JPN 2021 last). 24-month
window: banking passes (0.693) and investment busts pass (0.548
[0.516, 0.580]); the rest fail. Without the revised-vintage invariants
(every banking, grain and investment series is revised, so those
families drop out): reserve crises 0.579 [0.545, 0.610] and sovereign
debt 0.607 [0.571, 0.644] pass; the real-time-ish IMF IL reserves do
better alone than with the revised WDI imports and debt.

**(b) Invariants against channels: no.** The pooled model beats each
family's own raw series in 10 of 20 family-era cells with ten breaks or
more; the era means of the difference (families equal) are positive
(+0.064, +0.026, +0.043) but the majority rule fails, so by the
registered rule the invariants do not hold up against the channels. The
channels win for reserves, sovereign debt and investment busts in most
eras (reserves 1995-2007 -0.073 [-0.149, -0.003]); the invariants win
for share prices and for banking after 2008 (+0.335 [+0.231, +0.438]).

**(c) False alarms: pass.** Precision in each year's top decile, real
rows 0.076 against decoys (series shuffled in time) 0.033, difference
+0.043 [+0.032, +0.052]. But decoys are 91% of the top decile against
83% of the rows: the pooled hazard ranks shuffled histories above real
ones more often than not, because shuffling makes series jumpy and the
displacement terms read jumps as stress.

**Development diagnostic** (origins 2016-2024, trained to 2014): AUC
0.709 pooled over families (much of it between families' base rates);
within families price level 0.701, reserves 0.675, sovereign debt
0.629, grain 0.516, share prices 0.497, investment 0.424, energy 0.380.

**The model** (2020 block): the shared slopes are small next to the
missing indicators and the family intercepts. Cover below two years and
gross cover below half a year raise the hazard (+0.32, +0.33); the lower
cover bins do not add to it (-0.22, -0.31), so the registered bins give
no sign of a hazard convex in cover. The debt service ratio is the
largest slope (+3.4 a unit share) and only banking measures it. Family
slope deviations are small (the half-normal kept them near zero).

**Hindcasts** (rank among every stock measured at the origin):
- Asia 1997: private credit of Thailand 28th of 765 at the 1996 origin,
  23rd in 1997 (top 4%); Korea and Malaysia top 8%; the Philippines'
  reserves 33rd. Thailand's and Indonesia's reserves were not near the top.
- Food 2007-08: world grain 304th of 871 (2006) and 552nd (2007): missed.
- Sri Lanka 2022: reserves 149th of 1002 at the 2021 origin, 10th at
  2022 (external debt 20th); the break came in 2022.
- European gas 2021-22: 612th of 1002 at the 2021 origin: missed (a flow
  cut, not a runway, as the literature notes said).
- Telecom 2001: communication structures 66th of 789 at the 2001 origin
  (top 8%), communication equipment 184th; at 2000, 208th.
- US shale 2015: gas storage 95th of 993 at 2015 (top 10%), mining
  machinery 117th, petroleum and gas structures 157th.

**(d) Forward**, as of 11 November 2026: 1004 stocks ranked, top decile
101 (registered/ledger/2026-Q4/scanner.tsv, SHA-256 e7f3bed9...,
scanner.edn dd6a4073...; bnm.ledger/scanners and the test suite hash
them; the issue's own files are unchanged).
- Energy stocks in the top decile: **yes**, 22 of 115, but they are
  JODI crude stocks of small producers and importers (Gabon 1st, Morocco
  2nd, Jamaica, Oman, Colombia, Papua New Guinea, Bolivia, Dominican
  Republic in the top 20) and Belgian, Norwegian and North Macedonian gas
  storage. The large ones are not: India's crude 75th, German gas 151st,
  the EU's 222nd, US gas 392nd, the SPR 418th, US commercial crude 883rd.
- The US investment build-out in the top decile: **yes**, 2 of 6:
  investment in computers 70th and data-centre construction (Census) 92nd;
  software 254th, communication equipment 339th, power 405th, BEA data
  centres 547th (six years of data). US software and services firms
  (SIC 73) rank 15th, by their capex stretch.
- Top 20 otherwise: grain stocks of Eswatini, Botswana, Venezuela,
  Lesotho, Bahrain, Morocco and Malawi; reserves of Gabon, Malawi,
  Bahrain and South Sudan.
Scored when the 2027 catalogues are published.

**Hidden buffers (1.7, flags).** 319 residual series checked (IMF IL
against WDI reserves in 180 countries, IRFCL against IL in 96, EIA
against JODI US crude, the SFC net-lending identity in 42); one
flagged: US crude stocks, EIA (with the SPR) against JODI, a gap of
+22.6% in mid-2026 stretched 2.8 against its last ten readings.

**Reading.** The hypothesis that one function of the invariants finds a
family it has not seen holds for banking and, barely, sovereign debt; it
fails for the physical stocks, investment busts, share prices, price
level breaks and reserve crises (whose own level does as well), and the
invariants do not beat each family's raw channels. What the model
learns across families is mostly where stocks are measured and how
often each family breaks, not a shared form. Not tuned in response.

## Development, 11 November 2026: weightings with the self-loop cut (bead bnm-56c.17, loop audit Round 1)

Development, origins 2003-2022, as bnm-56c.2's candidates were. The base
of record stays t48-world-norel. Written before any of the runs below
was scored.

**What changed.** The loop audit (bnm-56c) found that a node's combined
forecast was its state, read back by its own models five ways (its own
state, :up through the quarters' offset from the year, :down from its
ancestors, exposure, and the errors fed back), so every weighting
compared so far also changed the models it weighed; and that the
averaging scored a year row with the model's state when the row became
known, three to four years after it was issued (hindsight). Three
opt-in changes, inert when off (the golden snapshot's numbers are
unchanged; its reads gain the :self rows of the third):
- `:state-from` (bnm.lattice): the node's state and the errors it feeds
  back come from a reference, a function of the node's models and
  inputs that never reads the weights; the combination is only issued
  (the book keeps it as `:issued`). `:reference` is the all-inputs
  model's forecast. Chosen over the base model because the all-inputs
  model reads everything the node reads, so the states passed up, down
  and across still carry what the node knows; the base model reads only
  the intercept, the core gauges and the offset, and as the reference
  would turn every flow into climatology. It is also the model t48's
  posterior weights settle on (1.000 at the food, conflict and market
  crash year nodes, 2017-2022), so of the two it moves the states least
  from the base of record. The cost: a nested link (the fast families,
  the noise and planted groups) is not read by the all-inputs model, so
  under the reference it can no longer reach the year through the
  quarters' states. That is the loop being cut, not a side effect: the
  year read the quarters' combined forecast, which is what the noise
  moved.
- `:evidence-at :issue` (bnm.lattice, augur v0.20.0): every rule scores
  a row by the predictive each model issued for it (pending rows carry
  it as `:lp`).
- the node's read of its own state goes through the sheaf (`:self`).

**Runs.** Four kinds (market crashes, food price shocks, coup, growth
collapses), every run on t48's archived inputs (bnm.fast-run --archive
data/ledger-inputs/2026-Q4), each with `{:state-from :reference
:evidence-at :issue}`, one run directory per setting and family
(prefix lp1-):
- post: posterior weights (t48's rule);
- ty01: tempered at 0.01 at the year only;
- A: ranked increments, `:weighting {:rule :evidence :scale 0.005}`;
- B: group shrinkage, `:only #{:all} :relation-nest nil :group-shrink
  {:tau 0.5}` (one model: the reference is the forecast, so nothing is
  cut; its noise group is read by that model, so noise can still move
  it);
- C: synthesis, `:weighting {:rule :synth :discount 0.99 :var 0.05}`;
- and post with `:state-from` alone (learn-time evidence), to split the
  two changes' effects on the base.
Each setting with --family none, noise (seeds 1 and 2) and planted
(strength 1). The best setting on the four kinds is then run on all
thirteen.

**Acceptance, as in bnm-56c.2's notes:**
1. noise: the year nodes' mean paired Brier against the same setting
   without noise within 0.0003 of zero, pooled over seeds 1 and 2
   (bnm.links --noise), and no single draw outside it;
2. planted: the planted group (strength 1) gaining at the year nodes
   with an interval above zero; the quarters reported beside it;
3. base against t48-world-norel at the year nodes by development fold
   (bnm.links --folds), no fold lost with an interval below zero,
   2020-2022 included, and the food price shocks' share of 2020-2022
   reported.
A setting passes only if it meets all three. Expected before scoring,
from the construction: with the reference, the noise and planted
groups cannot reach the year nodes in A, C, post and ty01 (rule 1 met
exactly, rule 2 failed at the year by construction); the quarters'
planted gain is the test of whether a weighting lets a real link earn
weight where it is read.

**Results** (scored after the section above was committed; Brier, year
nodes, 2003-2022, four kinds unless said; bnm.links; files under
data/runs/lp1-*):

| setting | noise, year (seeds 1, 2) | noise, quarter | planted, year | planted, quarter | base vs t48, pooled | 2020-2022 vs t48 | food 2020-2022 vs t48 | folds lost |
|---|---|---|---|---|---|---|---|---|
| post | 0, 0 | -0.00001, -0.00001 | 0 | +0.0006 [-0.0006, +0.0017] | -0.0052 [-0.0151, +0.0036] | -0.0062 [-0.0101, -0.0025] | -0.0236 | 1 (2020-2022) |
| ty01 | 0, 0 | -0.00001, -0.00001 | 0 | +0.0006 [-0.0006, +0.0017] | -0.0006 [-0.0097, +0.0078] | -0.0054 [-0.0096, -0.0015] | -0.0240 | 1 (2020-2022) |
| A | 0, 0 | 0.00000, -0.00000 | 0 | +0.0022 [+0.0004, +0.0042] | -0.0025 [-0.0105, +0.0053] | -0.0043 [-0.0097, +0.0002] | -0.0269 | 0 |
| B | +0.0008, +0.0018 | +0.0002, +0.0001 | +0.0034 [-0.0005, +0.0073] | +0.0028 [+0.0011, +0.0044] | -0.0026 [-0.0105, +0.0050] | -0.0101 [-0.0187, -0.0034] | -0.0264 | 2 (2007-2009, 2020-2022) |
| C | 0, 0 | -0.0001, -0.0004 | 0 | +0.0042 [+0.0018, +0.0069] | -0.0107 [-0.0249, +0.0023] | -0.0011 [-0.0048, +0.0023] | -0.0121 | 1 (2013-2019) |
| state-from only (post, learn-time evidence) | - | - | - | - | -0.0076 [-0.0157, +0.0009] | -0.0075 [-0.0131, -0.0025] | -0.0217 | 1 (2020-2022) |

- **The noise rule is now met by construction** in post, ty01, A and C:
  the noise group is nested, the reference (the all-inputs model) does
  not read it, so the year nodes' forecasts are identical with and
  without it (0.00000 exactly). B's one model reads it and fails, as
  before (+0.0013).
- **The planted rule fails at the year for the same reason**: a link the
  reference does not read cannot reach the year through the quarters'
  states. At the quarters, where it is read, A and C let it earn weight
  with intervals above zero (+0.0022, +0.0042); posterior and ty01 do
  not (+0.0006 [-0.0006, +0.0017]).
- **With the inputs fixed, the rules alone** (each against post on the
  same states): ty01 +0.0046 [-0.0035, +0.0147], loses 2010-2012
  (-0.0123 [-0.0254, -0.0015]); A +0.0027 [-0.0030, +0.0092], no fold
  lost, 2020-2022 +0.0019 [-0.0019, +0.0064]; C -0.0055 [-0.0143,
  +0.0013], loses 2007-2009. A is the best rule once the comparison is
  clean, and no longer loses to the posterior anywhere.
- **Food price shocks 2020-2022: most of the loss remains** (-0.022 to
  -0.027 against t48 in every setting except C's -0.012; A had -0.029
  with the loop). It is not the weighting: t48's own posterior rule with
  the loop cut loses -0.024 there, and with the loop cut but learn-time
  evidence -0.022. t48's edge in that episode came through the loop
  itself (its combined forecast read back through the quarters, the
  ancestors and its own state), not through the weights, which is the
  level miss the audit's issue 3 puts down to the year node not reading
  world food prices.
- **Against t50-world-norel** (the base of record since this afternoon;
  these runs are on t48's archived inputs, so input changes are mixed
  in, t48 against t50 is +0.0012 pooled and -0.0007 in 2020-2022): post
  -0.0041 pooled, 2020-2022 -0.0069 (lost); ty01 +0.0005, -0.0061
  (lost); A -0.0014, -0.0050 [-0.0091, -0.0012] (lost); B -0.0015,
  -0.0108 (lost, and 2007-2009); C -0.0095, -0.0017 (2013-2019 lost).

**A on thirteen kinds** (lp13-A-*): base against t48 -0.0012 [-0.0038,
+0.0011] pooled, 2020-2022 -0.0022 [-0.0039, -0.0003], lost (food price
shocks -0.027, conflict onsets -0.0071, currency crises -0.0034); against
t50 -0.0009 pooled, 2020-2022 -0.0028 [-0.0042, -0.0013], lost.
Noise at the year 0.00000 in both draws (quarters -0.00000); planted at
the year 0, at the quarters +0.0018 [+0.0012, +0.0025].

**Verdict.** No weighting passes once the loop is cut. Noise is now met
by every rule but B, and for a structural reason, not the rule's; the
planted rule fails at the year for every rule for the same reason; and
every setting loses a development fold against t48 except A on the four
kinds, which loses 2020-2022 on thirteen. With the inputs held fixed A
is the best rule (+0.0027 over the posterior, no fold lost), but the
2020-2022 food price shocks loss stays at about 0.024 whatever the rule:
it was the loop, which happened to carry the year's level in that
episode, not the weighting. Not adopted; the base of record is
unchanged and every option is off by default. The next step is the
audit's Round 2 (world prices at the year node), and the reference is
the natural place for a state built up from the children (the
recursive design), so that a link read at a finer node can reach the
coarser one without the combined forecast going round.
Issue-time evidence was not checked against the weighting diagnostic's
Q4 table on t48's inputs (the audit's test 2b); the synthetic test that
the averaging's evidence is the issued forecasts' log score to 1e-9 is
in test/bnm/lattice_test.clj.

**Correction, 11 November 2026 (review of Round 1).** At the quarter nodes, which run with `:downscale-uncertainty`, each model's `:lp` scored the mean offset, not the offset mixture the node issued, so the quarters' issue-time evidence was not the issued forecast's score. The synthetic test passed vacuously, comparing the evidence with the same `:lp`. The fix is commit "Lattice: score the offset mixture each model issued", tested at month nodes. Its consequences:
- The year nodes are unaffected. Their forecasts are byte-identical in every rerun, so every year column above stands.
- The quarter-node runs were redone (data/runs/lp1b-*, four kinds; B is one model and unaffected; ty01 tempers only the year, so its quarters equal post's). Quarter-lead Brier, 2003–2022:

| setting | noise, quarter | planted, quarter | base quarters vs lp1 | base quarters vs t48 |
|---|---|---|---|---|
| post, ty01 | -0.00001, -0.00001 | +0.0003 [-0.0010, +0.0016] (was +0.0006) | +0.0002 [+0.0001, +0.0004] | -0.0021 (was -0.0023) |
| A | 0.00000, +0.00001 | +0.0025 [+0.0007, +0.0043] (was +0.0022) | -0.0003 [-0.0008, +0.0002] | -0.0014 (was -0.0011) |
| C | 0.0000, -0.0001 | +0.0078 [+0.0039, +0.0124] (was +0.0042) | -0.0073 [-0.0165, +0.0007] | -0.0216 (was -0.0143) |

- **What changes in the reading:**
  - The noise rule at the quarters still holds.
  - A's and C's planted gains at the quarters stand and grow.
  - C's base quarters get worse, mostly in food price shocks (-0.035 against lp1).
  - The verdict is unchanged: no weighting passes.
- The learning path still uses the mean offset (bnm-56c.22). Fixing it moves the base.

## Registered, 11 November 2026: the world node and the world state at the year (bead bnm-56c.19, recursive step 2, loop audit Round 2)

Development, origins 2003-2022. Written and committed before any run below
was scored. The base of record stays t50-world-norel; adoption is the
user's decision and, if taken, one dated L1 note covers the world node,
bnm-h8d and bnm-56c.22 together.

**What changed** (every option off by default; the golden snapshot and
t50's forecasts are reproduced byte for byte without them):
- `bnm.world-state` reads world series as of t with their publication lags
  and refuses any value published after t (Test 3b). The world gauges are
  world food prices over 12 and 24 months (the IMF food price index, FRED
  PFOODINDEXM, the repository's stand-in for the FAO index, published a
  month late), Brent over 12 months, the VIX, world credit (the mean
  three-year change in BIS credit to GDP over the countries with data, as
  known), the US policy rate over 12 months and the broad dollar over 12
  months (from 2006); and food 12 and 24 times food imports, Brent times
  net fuel imports, credit and the VIX times openness (bnm.fast-run's
  exposure, read two years before the origin; above the countries their
  mean). The world node's state is these series (issue 10), not the share
  of states with any onset.
- `bnm.lattice :world-gauges`: the group at the year and half-decade
  nodes, in the all-inputs model, so the `:state-from :reference` state
  carries it (Test 3a, planted on synthetic data).
- `:downscale-learn`: the quarters' pool weight on the year's offset is a
  learned coefficient (a core input), not a fixed 0.5 (Test 4).
- `:learn-offsets :mixture` (bnm-56c.22): a quarter row issued over the
  year's offset mixture is learned at each offset, weighted.
- `--pwt-stale` (bnm-h8d): the lattice reads PWT at 36 months, so the
  political-economy group no longer goes missing every September.
- `--catalogue-through` (Test 7): own history, others' counts and the world
  share are unknown past a catalogue's :through less its lag.
- Round 4 also fixed `bnm.stocks` (Test 18; no series the lattice reads is
  affected) and marked the IMF IL reserves :revised (Test 12; the hazard
  only).

**The candidate, t51-world-norel**, on the current data: t50's command
plus the new options,

    jolt -A:tools -M -m bnm.world-run --out t51-world-norel --without-relations true --known r6-at-8ul \
      --world-gauges true --pwt-stale true --catalogue-through true \
      --set '{:state-from :reference :evidence-at :issue :downscale-learn {:var 1.0} :learn-offsets :mixture}'

with the posterior weighting (t50's; no weighting rule has passed).

**The acceptance harness** (bnm-56c.19's notes: the planted signal and the
noise go where the reference reads, the year's own world group; one extra
column in that group, the same at every place). Runs, all thirteen kinds,
t51's command plus:
- `--world-extra noise --seed 1` and `--seed 2` (s2-noise1, s2-noise2): a
  standard normal per origin;
- `--world-extra planted --seed 1 --strength 1` (s2-planted): the seed-1
  noise plus the z-score of the share of countries with an onset in the
  year window (it reads the future);
- `--world-extra false --seed 1` (s2-false): the seed-1 noise plus 3 at the
  origins of 2013 and 2014, a world move that carries nothing.

**Acceptance rules** (Brier, year nodes, origins 2003-2022, thirteen kinds
weighed equally, bnm.links / bnm.paired as before; folds bnm.links/folds):
1. Noise, pooled: the mean of the two draws' paired Brier against t51
   within 0.0003 of zero (bnm.links --noise). Per fold: in each of the five
   folds the mean of the two draws' paired Brier within 0.0003 of zero.
2. Planted: s2-planted against t51 gains, with its 90% interval above zero.
3. False move: s2-false against s2-noise1 (the same draws but the move) not
   below -0.0003, pooled and in the 2013-2019 fold that holds the move.
4. Against t50-world-norel by fold (bnm.links --folds): no fold lost (90%
   interval below zero), 2020-2022 included. Reported beside it, not part
   of the rule: pooled; food price shocks and market crashes in each fold;
   the quarter-lead nodes; t51 against t48-world-norel (t48 is on its own
   inputs, so input changes are mixed in).
t51 passes only if all four hold. What the world group does alone is
reported from one more run, t50's command with `:state-from :reference
:evidence-at :issue` and nothing else (s2-ref), against which t51 differs
only by the world state, the learned pool, the mixture, h8d and Test 7.

**Results** (scored after the registration above was committed at
84b9b3b; Brier, year nodes, 2003-2022, thirteen kinds; bnm.links; run
directories data/runs/t51-world-norel and data/runs/s2-*):

| check | result | rule |
|---|---|---|
| 1. noise, pooled (s2-noise1, s2-noise2 vs t51) | +0.00012, +0.00029; mean +0.00020 (fresh draw [-0.00071, +0.00112]) | met |
| 1. noise, per fold (mean of the two draws) | 2003-06 -0.00035, 2007-09 -0.00040, 2010-12 +0.00222, 2013-19 +0.00035, 2020-22 -0.00063 | not met (all five outside 0.0003) |
| 2. planted (s2-planted vs t51) | +0.00530 [+0.00206, +0.00887]; market crashes +0.065, food price shocks +0.006 | met |
| 3. false move (s2-false vs s2-noise1) | pooled -0.00010 [-0.00027, +0.00006]; 2013-19 -0.00024 [-0.00064, +0.00006] | met |
| 4. t51 vs t50 by fold | 2003-06 -0.0042 [-0.0182, +0.0069]; 2007-09 -0.0234 [-0.0328, +0.0013]; 2010-12 -0.0016 [-0.0087, +0.0047]; 2013-19 -0.0020 [-0.0061, +0.0015]; **2020-22 -0.0044 [-0.0071, -0.0023], lost** | not met |

Reported beside the rules:
- t51 against t50, pooled -0.0046 [-0.0089, -0.0001] (a loss); food price
  shocks -0.023 pooled (by fold +0.003, -0.002, -0.097, -0.014, 2020-22
  -0.025), market crashes -0.037 (by fold -0.048, -0.305, +0.060, -0.015,
  2020-22 -0.027). Quarter-lead nodes -0.0015 [-0.0038, +0.0001].
- t51 against t48: pooled -0.0049 [-0.0097, -0.0006], 2020-22 -0.0038
  [-0.0058, -0.0019] (food -0.023, market crashes -0.026).
- **Decomposition.** s2-ref (t50 with `:state-from :reference
  :evidence-at :issue` only) against t50: pooled -0.0012 [-0.0036,
  +0.0014], 2020-22 -0.0023 [-0.0037, -0.0007] lost, food price shocks
  2020-22 -0.025: the cut loop alone accounts for all of the food
  2020-22 loss, as Round 1 found on t48's inputs. t51 against s2-ref (the
  world state, the learned pool, the offset mixture, h8d and Test 7):
  pooled -0.0034 [-0.0069, +0.0000], losing 2007-09 (-0.021), 2010-12
  (-0.007) and 2020-22 (-0.002, market crashes -0.025; food -0.0001).
- **Why the world state does not lift food 2020-22.** The world group's
  model earned no posterior weight at the food year node after 2010
  (0.056 in 2010, 0.000 from 2011 to 2022). The issued forecast is the
  posterior combination, so at the food year node it never read the
  world state; the reference, which does, is only the state passed on.
  (Corrected 12 November, step-2 diagnosis: this note first said the
  same of market crashes. There the world-gauges model held 0.77-1.00 of
  the year country node's weight from 2005 to 2008 and was the issued
  forecast in 2006-2007, p 0.15 and 0.12 against rates of 0.96: most of
  the -0.305 loss in 2007-09.)
  Its effect through the states was harmful: the food year node's mean p
  rose to 0.44, 0.36 and 0.38 in 2011-2013, when world food prices were
  high and the catalogue rate was 0.05, 0.01 and 0.05, and stayed at 0.06
  in 2020-2021 against rates of 0.50 and 0.51. Market crashes' mean p
  fell to 0.15 and 0.12 in 2006-2007 against rates of 0.96. Over the
  years the year node learned from, world food momentum did not track
  the food catalogue's rate (2011 is the counterexample), so the r = 0.65
  of the weighting diagnostic (origins 2005-2022, in hindsight) was not
  available to an online learner by 2020.

**Verdict.** t51-world-norel fails rule 1 per fold and rule 4; it passes
the planted and false-move rules. Not adopted; the base of record stays
t50-world-norel and no L1 note is written. Every option stays off by
default (t50 reproduces byte for byte on banking crises, food price
shocks and market crashes from this branch).

**Round 4's delay test (bnm-pjg.6, development).** The base's year and
half-decade gauges read annual rows (current, revised downloads) with an
assumed one-year lag. t50's command with the political and economic
kinds' rows read one year later (`--year-delay 1`, s2-delay1; banking's
own table is not delayed) and without the year and half-decade gauges at
all (s2-nogauge); year nodes, 2003-2022, thirteen kinds, against
s2-nogauge:
- the gauges as in t50: Brier +0.0013 [-0.0016, +0.0043], log +0.0076
  [+0.0006, +0.0147];
- the gauges a year late: Brier -0.00003 [-0.0031, +0.0032], log +0.0044
  [-0.0024, +0.0118].
The gauges' gain does not survive the delay: about half of the log gain
and all of the Brier gain go, most of it in market crashes (-0.002 to
-0.017) and fiscal consolidation (+0.0014 to +0.0001); food price shocks
and conflict keep theirs. Either the latest year carries real signal or
the revised rows carry what no one had at the time; the test cannot tell
which, but the base's year-gauge gain rests on the latest year's row.

## Look-ahead fixes and the corrected candidate t51b-world-norel (12 November 2026, bnm-56c.23)

The leak audit (bnm-pjg.6's notes) found inputs read before they were
published. Fixed test-first, one commit each (branch fix-leaks):
- PWT rows in data/slow/series.tsv dated by the first release covering
  them (resources/bnm/releases.tsv; 13-58 months, not 24), irr not before
  PWT 9.0 (June 2016), labsh and the capital stock not before 8.0 (July
  2013). Under these dates the profit rate is absent at every quarter
  origin before mid-2016.
- Maddison and NMC read no later than their releases (bnm.releases; Dec
  2014 now reads Maddison 2010 and NMC 2007, not 2012), banking's direct
  PWT read the same way, urban growth as a log change on both sides of
  2016 (it was share points over 100 after): opt-in, `bnm.world-run
  --leak-fixes true`.
- UIS tertiary enrolment at 21 months; a daily FRED series' incomplete
  last month left out (excluded rather than dated by its last
  observation: a partial mean is replaced once the month is out); EM-DAT's
  year to date left out.
- Not fixable by a lag: every input is today's revised vintage (V-Dem
  before 2016, WID before 2017, PWT, Maddison, NMC, WDI, GMD, JST and the
  research datasets). Bead bnm-09d. The quarters' offset still pools with
  r6-at-8ul, fit on the old series.tsv.

**Runs** (data/runs; all on the corrected data/slow):
- t51b-world-norel: t50's command plus `--leak-fixes true --pwt-stale
  true --set '{:learn-offsets :mixture}'` (h8d and 56c.22; no
  `:state-from`, no world gauges).
- fx-leak: t50's command plus `--leak-fixes true` only, to split the leak
  fixes (data and code) from h8d and .22.
- fx-noise1, fx-noise2, fx-planted: t51b's command plus `--world-extra
  noise --seed 1|2` and `planted --seed 1 --strength 1`, the harness
  column as a group of its own at the year and half-decade (no world
  state to sit in); fx-leak-noise1, fx-leak's command with noise seed 1.

**Results** (Brier, year nodes unless named, 2003-2022, thirteen kinds
weighed equally, bnm.links; + = better than the bench):

| comparison | 2003-06 | 2007-09 | 2010-12 | 2013-19 | 2020-22 | pooled | quarter-lead |
|---|---|---|---|---|---|---|---|
| t51b vs t50 | -0.0014 | +0.0008 | +0.0014 | -0.0026 | -0.0019 | -0.0013 [-0.0026, +0.0002] | -0.0006 [-0.0011, -0.0002] |
| fx-leak vs t50 (leak fixes) | +0.0000 | -0.0047 | +0.0045 | -0.0003 | -0.0015 | -0.0001 [-0.0015, +0.0014] | -0.0008 [-0.0015, -0.0001] |
| t51b vs fx-leak (h8d, .22) | -0.0014 | +0.0056 | **-0.0031 lost** | -0.0022 | -0.0003 | -0.0012 [-0.0027, +0.0002] | +0.0002 [-0.0002, +0.0006] |

- No fold of t51b against t50 is lost (every 90% interval spans zero:
  2003-06 [-0.0034, +0.0005], 2013-19 [-0.0056, +0.0004], 2020-22
  [-0.0051, +0.0010]); the log score is lost pooled (-0.0051 [-0.0094,
  -0.0001]).
- Market crashes carry most of every move (year: t51b vs t50 -0.012
  pooled, by fold -0.010, +0.009, +0.019, -0.027, -0.015; leak fixes
  alone +0.004, with -0.058 in 2007-09 and +0.059 in 2010-12), the global
  coin the audit described. Food price shocks: t51b vs t50 -0.0016 pooled,
  2020-22 -0.008 (leak fixes alone -0.009 there). Quarter-lead market
  crashes: leak fixes -0.008, mostly 2007-09.
- Noise (fx-noise1, fx-noise2 vs t51b): pooled -0.00089 and -0.00078,
  mean -0.00083: **not within 0.0003**. Per fold (mean of the two):
  -0.0012, -0.0062, +0.0037, -0.0013, -0.0002; only 2020-22 within.
  fx-leak-noise1 vs fx-leak (no h8d or .22): -0.0019 pooled, so the noise
  cost is the base's posterior weighting (docs/LATTICE.md: a noise group
  flips the year's selection), present without h8d and .22.
- Planted (fx-planted vs t51b): +0.0102 [+0.0071, +0.0135]; quarter-lead
  +0.0014 [+0.0004, +0.0027]: met.

**Reading.** The leak fixes are correctness fixes: fx-leak against t50 is
the honest score of t50's model, not a loss caused by the fix. It is
level pooled at the year (-0.0001) and loses 0.0008 [-0.0015, -0.0001]
at the quarter-lead (market crashes -0.008): the skill t50 reports on the
quarters is overstated by about that much.
h8d and .22 add -0.0012 pooled and lose 2010-12 (-0.0031, market crashes
-0.040). Under principle 9: the noise failure is sequencing (the
weighting, step 5; it fails on fx-leak too, by more); the 2010-12 loss
of h8d/.22 is in market crashes, whose year node moves by ±0.04-0.07 per
fold between any two runs, the selection instability already measured
(noise draws differ by up to 0.017 per fold there): not a defect of
either fix found here. Adoption, and any L1 note, is the user's call; no
base, ledger or forecast was changed.

## Step 2 rescored after fixes 2 and 3 (12 November 2026, bnm-56c.24, bnm-56c.25)

Development, origins 2003-2022, under the rules registered at 84b9b3b
(unchanged). The base of record stays t50-world-norel; adoption is the
user's call. Every option is off by default.

**Fix 3: the world gauges once per origin** (bnm.world-term,
`bnm.world-run --world-once true`). Step 2 put gauges that vary only by
year on about 150 country rows each, and twelve collinear columns
cancelled out. The world node now learns one term per kind and scale: a
logistic of the share of countries with an onset in the window on world
food prices and Brent over 12 months, one row per origin weighted by its
effective number of places (n / (1 + (n - 1) rho), rho the shares'
dispersion beyond the binomial), a gauge that is a linear function of
earlier ones dropped. The term is added to the countries' offset at the
year and half-decade, so every model reads it at a coefficient of 1. The
year group keeps only food12 x food imports and brent12 x net fuel
imports. The VIX, credit, the dollar and the policy rate are no longer
wired. `bnm.lead-test` is the test a gauge must pass first: its online
fit's squared error at most 0.9 of climatology's. On 2003-2022, run
before the rescoring runs: food price shocks, food12 passes (lead r
+0.61, 0.023 against 0.029), food24 passes, brent12, the VIX, credit,
policy and usd12 fail; market crashes, every gauge fails except usd12
(0.046 against 0.056, n 16; not wired).

**Fix 2: the reference.** The all-inputs reference swung: its linear
predictor's variance at the food year node was 20-58 in 2005-2010, and
its state moved 0.08 a year over a span of 0.54 (2007-2022); the down
model read the parents' swinging states into an issued 0.87 in 2011.
Compared before any rescoring run, on a synthetic world (twenty
uninformative place-invariant gauges, a planted world momentum; test
`fix-2-states-hold-under-many-inputs`) and on the real food states (no
outcome read):

| reference | synthetic: r with the planted log odds | distance | moved by a null column | food state: mean yearly move, span |
|---|---|---|---|---|
| all-inputs model (step 2) | 0.32 | 1.08 | 0.15 | 0.083, 0.54 |
| all-inputs, prior v/n per input (`:shrink-per-input 0.5`) | 0.35 | 0.97 | 0.06 | 0.099, 0.47 |
| equal pool of the models | 0.17 | | 0.05 | |
| all-inputs shrunk toward the base by its variance | 0.24 | | 0.06 | |
| **base model with the world term in its offset** | **0.94** | **0.56** | **0** (0.16 inside the term) | **0.038, 0.12** |

Chosen: `:state-from :base` with fix 3's term. Damping toward the parent
was not tried: a parent's state is the chance of any onset among its
children (0.94-0.999 at the food world node), not a child's level. C7
holds. `:shrink-per-input` stays as an option.

**Found on the way** (bnm-56c.26, not fixed, needs a decision): a node
with no resolved row uses a climatology of 0.5, so the food year node
issues 0.50 at origins 2001-2006 in t50, t51b and every run here (rate
0.10-0.42). Paired comparisons are unaffected.

**Runs** (data/runs/fs-s2-*): fs-s2-cand is t51b's command (`--leak-fixes
--pwt-stale`, `:learn-offsets :mixture`) plus step 2's
(`--world-gauges --catalogue-through`, `:evidence-at :issue
:downscale-learn {:var 1.0}`) plus `--world-once true` and `:state-from
:base`; fs-s2-cand-ref its reference's forecasts (`--write-reference`);
fs-s2-noise1/2, fs-s2-planted, fs-s2-false the harness's column, which
now enters the world term.

**Results** (Brier, year nodes, thirteen kinds weighed equally; + = the
candidate better):

| comparison | 2003-06 | 2007-09 | 2010-12 | 2013-19 | 2020-22 | pooled |
|---|---|---|---|---|---|---|
| cand vs t50 | -0.0065 | -0.0307 | +0.0054 | -0.0026 | **-0.0047 [-0.0094, -0.0004] lost** | -0.0051 [-0.0114, +0.0004] |
| cand vs t51b | -0.0051 | **-0.0316 lost** | +0.0040 | -0.0000 | -0.0029 | -0.0038 |
| cand vs fx-leak | -0.0065 | -0.0260 | +0.0009 | -0.0022 | -0.0032 | -0.0050 |
| reference vs t50 | -0.0084 | -0.0327 | -0.0012 | +0.0009 | -0.0040 lost | -0.0050 |
| food, cand vs t50 | +0.0002 | -0.0163 | -0.0043 | +0.0041 | +0.0019 | -0.0011 |
| food, cand vs t51b | +0.0003 | -0.0166 | -0.0068 | +0.0066 | +0.0099 | +0.0005 |
| market crashes, cand vs t50 | -0.075 | -0.361 | +0.061 | -0.035 | -0.065 | -0.061 |
| twelve kinds (no market crashes) vs t50 | -0.0008 | -0.0032 | +0.0008 | +0.0001 | +0.0003 | -0.0004 |

| rule | result | |
|---|---|---|
| 1. noise, pooled | -0.00026, -0.00063; mean -0.00044 | not met |
| 1. noise, per fold (mean of two) | -0.00084, -0.00007, -0.00029, +0.00008, -0.00121 | not met (2003-06, 2020-22) |
| 2. planted | +0.0125 [+0.0077, +0.0177] (market crashes +0.113, food +0.003) | met |
| 3. false move | pooled -0.00023; 2013-19 -0.00067 (market crashes -0.010) | not met (2013-19) |
| 4. vs t50 by fold | 2020-22 lost | not met |

Quarter-lead against t50: -0.0021 [-0.0044, -0.0005] (market crashes
-0.023). The step-2 diagnosis's all-inputs reference scored the same way
(s2d-t51ref) was -0.0060 pooled and -0.014 at food 2020-22; the new
reference is -0.0050 and +0.0019.

**Reading, under principle 9.**
- **Food price shocks: fixed, and the rest is sequencing.** 2020-22 moves
  from -0.025 (t51) to +0.0019 against t50 and +0.0099 against t51b, in
  the issued forecast and the reference alike: the term reaches what is
  issued through the offset, without waiting for step 5. The term was
  +0.34 and +0.99 at the 2020 and 2021 origins, and the issued mean p
  0.07 and 0.13 against rates of 0.50 and 0.51: the diagnosis's 30-45%
  foreseeable share, no more. The rest needs the frailty (step 3) or
  faster rows. 2007-09 (-0.016) is 2007, issued 0.14 against 0.33 where
  t50 still issued its cold-start 0.29-0.51.
- **Market crashes: approach wrong**, as the diagnosis found, and now
  forced in. Food and Brent momentum do not lead crashes (both fail the
  lead test there, run before the rescoring); in the offset every model
  reads them, so the issued p fell to 0.10 and 0.08 at the 2006-2007
  origins against rates of 0.96 (t50 0.46, 0.45). Market crashes carry
  rule 4's 2020-22 loss (without them +0.0003) and rule 3's 2013-19
  failure. The wiring did not apply the lead test per kind; doing so
  would leave the world term at food price shocks only. That is a change
  made after scoring, so it is the user's decision whether to rescore it.
- **Noise: implementation.** The harness column now enters the term, and
  through the offset every model, so no weighting can set it aside; its
  cost is the term's own estimation noise over a few origins (-0.0003
  without market crashes). A tighter prior on the term's gauges, or the
  term as a learned input rather than a fixed offset, is the fix to try.

Not adopted. No L1 note, no ledger base change, no forecast.

## The world term gated per kind by the lead test (12 November 2026, bnm-56c.28)

Development, origins 2003-2022, under the rules registered at 84b9b3b
(unchanged; rule 4 is against t50-world-norel as registered, and
t52-world-norel, the base of record since this day and byte for byte
t51b's forecasts, is reported beside it). Adoption is the user's call.

**What was wrong.** Fix 3's brief said a world gauge is wired only after
`bnm.lead-test` passes it. The wiring did not apply that per kind: food12
and brent12 entered every kind's term, and through the offset every model,
though both fail the test at market crashes and Brent fails it at every
kind. The harness's noise column entered the same term with no test, so
no evidence could drop it. Under principle 9 both are implementation bugs.

**The fix** (90eab71, test first; `bnm.world-term/gate`,
`bnm.world-gauges/with-world-once`, opt-out `bnm.world-run --world-gate
false`). At each origin t and for each kind and scale, a gauge enters the
term, and its product column with the place's exposure, only if the lead
test passes it on the origins from 1950 whose outcomes are known by t
(online error at most 0.9 of climatology's, over at least five online
errors). A gauge that fails is not read (term without it, product column
unknown). The harness's column passes the same test as food and Brent.
Chosen before any run: the expanding window, re-decided each origin,
rather than a gate fixed on pre-2003 data, because a kind's rate is known
only years after its origin (food price shocks: 3.5 years, and no food
rate before the 2003 origin at all), so a pre-2003 gate has nothing to
decide food on, and a fixed gate cannot drop a gauge that stops leading.
Checked before scoring: run with `--world-gate false`, market crashes
reproduce fs-s2-cand byte for byte.

**The lead test, all thirteen kinds** (`jolt -A:tools -M -m
bnm.lead-test`; hindsight = the test over 2003-2022 as fix 3 ran it; gate
= origins of 2003-2022 at which the expanding test passes):

| kind | food12 hindsight (lead r, online/clim) | food12 gate | brent12 hindsight | brent12 gate |
|---|---|---|---|---|
| banking crisis | +0.18, fail | 0/20 | +0.02, fail | 0/20 |
| conflict onset | -0.17, fail | 0/20 | -0.35, fail | 0/20 |
| coup | +0.31, fail | 0/20 | +0.10, fail | 0/20 |
| currency crisis | -0.12, fail | 0/20 | -0.46, fail | 5/20 (from 2003) |
| fiscal consolidation | +0.07, fail | 0/20 | -0.09, fail | 1/20 (2003) |
| food price shock | +0.61, 0.023/0.029 pass | **0/20** | +0.20, fail | 1/20 (2019) |
| growth collapse | -0.18, fail | 0/20 | -0.16, fail | 0/20 |
| inflation crisis | +0.02, fail | 0/20 | -0.15, fail | 0/20 |
| market crash | +0.36, 0.072/0.049 fail | **4/20 (2005-08)** | +0.05, fail | 0/20 |
| protest surge | +0.18, fail | 6/20 (from 2005) | +0.39, fail | 0/20 |
| sovereign default | +0.08, fail | 0/20 | +0.11, fail | 0/20 |
| violence surge | +0.29, fail | 0/20 | +0.14, fail | 1/20 (2003) |
| war onset | -0.06, fail | 3/20 (from 2010) | +0.02, fail | 0/20 |

The expected wiring (food12 at food price shocks only) does not come out
of a gate that does not look ahead. Food's rows are known 3.5 years late;
by the 2020 origin only 2003-2016 are known, and on them food12's online
error is 1.00-1.04 of climatology's (2013-2022). Its hindsight pass rests
on the 2020 and 2021 rows (rates 0.50, 0.51), known from mid-2024. At
market crashes food12 passes on 5-8 online errors from the 1990s at
2005-2008, so the term entered there: -1.38 and -1.45 at the 2006 and
2007 origins (checked in the REPL on the spec without
`--catalogue-through`).

**Runs** (data/runs/gw-*; fs-s2-cand's command plus the gate, the
default): gw-cand; gw-noise1, gw-noise2, gw-planted, gw-false with
`--world-extra noise --seed 1|2`, `planted --seed 1 --strength 1`,
`false --seed 1`.

    jolt -A:tools -M -m bnm.world-run --out gw-cand --without-relations true --known r6-at-8ul \
      --leak-fixes true --pwt-stale true --world-gauges true --catalogue-through true --world-once true \
      --set '{:learn-offsets :mixture :evidence-at :issue :downscale-learn {:var 1.0} :state-from :base}'

**Results** (Brier, year nodes, thirteen kinds weighed equally; + = gw-cand
better):

| comparison | 2003-06 | 2007-09 | 2010-12 | 2013-19 | 2020-22 | pooled |
|---|---|---|---|---|---|---|
| gw-cand vs t50 | -0.0086 | +0.0112 | +0.0075 | +0.0054 | **-0.0031 [-0.0045, -0.0016] lost** | +0.0016 [-0.0039, +0.0061] |
| gw-cand vs t52 | -0.0072 | +0.0103 | +0.0061 | +0.0079 | -0.0013 [-0.0040, +0.0024] | +0.0029 [-0.0028, +0.0073] |
| gw-cand vs fs-s2-cand | -0.0021 lost | +0.0419 | +0.0021 | +0.0079 | +0.0016 | +0.0067 [+0.0016, +0.0140] |
| food, vs t50 | -0.0000 | -0.0101 | -0.0036 | +0.0038 | -0.0222 | -0.0035 |
| food, vs t52 | +0.0001 | -0.0104 | -0.0061 | +0.0064 | -0.0142 | -0.0019 |
| market crashes, vs t50 | -0.110 | +0.174 | +0.085 | +0.064 | -0.014 | +0.025 |
| market crashes, vs t52 | -0.100 | +0.165 | +0.066 | +0.091 | +0.001 | +0.037 |
| twelve kinds (no market crashes), vs t50 | -0.0002 | -0.0024 | +0.0011 | +0.0005 | -0.0022 | -0.0003 |
| twelve kinds, vs t52 | +0.0005 | -0.0025 | +0.0012 | +0.0010 | -0.0015 | +0.0001 |

| rule | result | |
|---|---|---|
| 1. noise, pooled | +0.00000, +0.00000 (each under 0.000005) | met |
| 1. noise, per fold (each draw) | every fold at most 0.00001 from zero | met |
| 2. planted | +0.0050 [+0.0010, +0.0091] (market crashes +0.013, food +0.006) | met |
| 3. false move | 0 pooled and in 2013-19 (gw-false is gw-noise1 byte for byte) | met |
| 4. vs t50 by fold | 2020-22 -0.0031 lost | not met |

Quarter-lead against t50: -0.0022 [-0.0043, -0.0006] (market crashes
-0.025). The noise column passed the gate at a few origins of conflict
onsets, coups, market crashes and war onsets (seed 1) and of four other
kinds (seed 2), at a cost below 0.00002 a fold; the false move never
passed in 2013-2019.

**Reading, under principle 9.**
- **Noise and the false move: fixed** (implementation). Both now enter as
  an ordinary world gauge does and are dropped by the lead test.
- **Rule 4, 2020-22 against t50 (-0.0031): sequencing, and the leak
  fixes.** Food price shocks -0.022 there: the gate never admits food12
  (above), so food is back to the cut loop's cost (s2-ref -0.025, the
  step-5 weighting). Food12's lead can be shown online only once the
  2020-21 rows are known (2024-25). Market crashes -0.014 there is t51b's
  own difference from t50 (vs t52 +0.001): the leak fixes, the honest
  score. Against t52 no fold is lost.
- **Market crashes 2003-06 (-0.110 vs t50): implementation of the gate,
  not fixed here.** Five online errors let food12 through on 1990s rows at
  2005-2008, and the term took the 2006 issue to 0.07 against a rate of
  0.96 (t50 0.46). A stricter minimum, or a test of the lead's sign
  against climatology's spread, would close it; that is a change made
  after scoring, so it is the user's decision.
- The gate's opposite problem is food: requiring rows known before the
  origin, it cannot admit a lead it has not yet seen. Wiring food12 at
  food price shocks on the hindsight test would read the future.

Not adopted. No L1 note, no ledger change, no forecast.
