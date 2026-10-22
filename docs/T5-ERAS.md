# T5: slow stocks at the half-decade, on JST with rotated eras

Bead bnm-3qu.6. Written 22 October 2026 for trial 5 of
docs/LIT-NEXT-STEPS.md ("Half-decade node on long samples, rotated
eras"). This is the design and its development run. It is not
registered. No outcome from 1996 on was read: the tool removes every
crisis flag from 1996 on as it reads the data, and refuses to build a
development row whose window reaches 1996.

Tool: tools/bnm/half_decade.clj (`bnm.half-decade`). Tests:
test/bnm/half_decade_test.clj.

## Question

Do slow financial stocks predict systemic banking crises over five-year
horizons, against each country's own history, with enough eras to tell?
T1 could not answer on 2003-2022: four half-decades. JST gives 18
countries from 1870 and four development eras.

## Design

**Sample.** JST Macrohistory, 18 countries, 1870-2020. Ireland's crisis
flags start in 1920; its earlier years are uncovered. Origins are year
ends inside an era. Wartime years (1914-18, 1939-45) are never origins,
but their data feed the states and the history.

**Outcome.** A JST systemic banking crisis (crisisJST) starting in
years t+1 to t+5. A row is not at risk while a crisis is under way (an
onset in t-2 to t). The window must be covered in full, or the row is
dropped.

**States.** Read at the end of year t from data years up to t-1. A JST
year is out at the end of the next, as bnm.keen-run reads it. Each is
standardized on the training rows of its fold and clipped at 3 SD. A
missing state reads 0, the training mean.

| term | construction | sign |
|---|---|---|
| credit gap | loans/GDP less its one-sided HP trend, lambda 1,562.5 (`bnm.credit-run/gap-at` on JST's series alone), pp of GDP | + |
| house | three-year growth of real house prices, log(hpnom/cpi) | + |
| equity | three-year real equity growth, sum of log(1+eq_capgain) less CPI inflation | + |
| current account | CA/GDP, mean of the last three years | - |
| credit x house | max(z credit gap, 0) x max(z house, 0), both booming (Greenwood et al. 2022's zone), with both main effects in its model | + |
| debt service (record) | loans/GDP x i/(1-(1+i)^-15), i the long rate, less its mean over the 20 years before (Drehmann & Juselius 2014's deviation) | + |

At most five terms are tested, so the debt-service proxy is for the
record, outside Holm. It has the weakest proxy: a government bond rate on
bank loans, with no maturity data. Drehmann & Juselius also find it a
one-to-two-year signal, not a half-decade one, and T1 dropped it for
proxy quality.

**Base: the country's history and the world's.** Every input is known at
the end of t:
- the country's crisis rate up to t-1, shrunk to 3% a year with ten
  years' weight (log);
- log years since its last crisis;
- the world's crisis rate so far in the current era, an era-specific
  world rate as known, shrunk to 3% with one panel-year's weight (log);
- the share of countries with a crisis in the last five years.

Year fixed effects cannot be scored out of era: a held-out era's years
have no fitted effects. The era's running world rate takes their place.
The lattice base does not exist before 1960, so this is states against
history.

**Eras.**

| era | origins | rows | windows with an onset | onset country-years |
|---|---|---|---|---|
| pre-WWI | 1870-1913 | 642 | 152 | 34 |
| interwar | 1919-1938 | 281 | 83 | 27 |
| Bretton Woods | 1946-1971 | 468 | 3 | 1 |
| 1972-1995 | 1972-1990 | 324 | 52 | 11 |
| test, unread | 1996-2015 | | | |

Development rotates over the first four, leaving one out at a time: fit
on the other three and score the one left out. A training row is dropped
when its window reaches the held-out era's outcome years. 1972-1995
contributes origins up to 1990 only, since a later window would read
1996. Bretton Woods has one crisis (three windows). Holding it out scores
only false alarms, so it is reported but carries little.

**Scores.**
- Per row: the log score and Brier, as the gain of the states' model
  over the base (the interaction over its main effects). Positive means
  better.
- Intervals: 90%, from a moving-block bootstrap over origin years, block
  5 (the window's length), 1,000 draws.
- Null: 200 permutations of whole country series. Each country reads
  another's states at the same years, and every fold is refit.
- Placebo: each state read at the next country (sorted, cyclic) that
  has it that year.
- Holm over the five terms. Logit ridge prior precision 1 on the
  standardized scale; the intercept is free.

**Horizon profile.** The same rows, with the outcome an onset in year
t+h exactly, h = 1 to 5. Two statistics:
- the joint model's log gain at each h, and the rise: the mean gain at
  h 4-5 less h 1-2, against the permutation null;
- each term's signed coefficient per SD at each h, from a fit on all
  development rows, and s(c45 - c12).

**Power.** A synthetic own state (AR(1) at 0.9 within each country)
drives simulated annual onsets, with year shocks of sd 0.76 (banking's
in the power audit). These replace the crisis flags over the data's
coverage, the test years included. No real flag from 1996 on is read.
The level is the development years' annual onset rate. The state enters
the model as the real ones do, the year before the origin. Power is the
share of draws whose pooled log gain has its 90% interval above 0, in
development (leave one era out) and in a test on the 1996-2015 origins.

## What the tests check

- Era exclusion. The development rows, labels and scores are identical
  when every 1996-2020 crisis flag is 1 or 0. No development window
  reaches 1996. `check-dev!` refuses a row that does. Test mode will not
  run without `--confirm-registered yes`. Its training rows are exactly
  the development rows.
- Causality. Nothing in the origin's year or later changes a state, and
  the year before does.
- The HP gap equals 100 x (x - trend) at the last data year. The trend
  comes from a dense solve of (I + lambda D'D) tau = x on data years
  before the origin, and the banded solver agrees with it. A straight
  line has no gap. Fewer than ten years have none.
- The fold embargo, the labels and at-risk rule, clipping, the
  interaction's zone, the permutation, the placebo and Holm. A planted
  state is recovered out of era.

## Development changes, made after seeing development results

The first development run used unclipped states and the raw product of
the credit gap and house growth. Germany's hyperinflation (house growth
-5 SD, credit gap -38 pp) made the product a +20 SD "boom" and the
probability 1.000 in 1922-23. Two changes followed, both from the
literature's own definitions and decided on development eras only:
clipping at 3 SD, and the interaction as Greenwood et al.'s zone (both
positive). Nothing else was tuned. After the power check, the proposed
test criterion moved from the bootstrap interval to the permutation p
(see Power below). No development result entered that choice.

## Development results (22 October 2026)

Command: `jolt -A:tools -M -m bnm.half-decade --draws 200 --boot 1000`
(data/runs/sequencing/t5-dev.txt). Rows: 1,715 at origins 1870-1990, 290
with an onset within five years, 18 countries.

**Joint model (five terms) against the base, leave one era out**, gain
per row (positive is better):

| held out | rows | windows with onset | base log | log gain [90%] | Brier gain [90%] |
|---|---|---|---|---|---|
| pre-WWI | 642 | 152 | -0.565 | -0.0107 [-0.0217, -0.0011] | -0.0036 [-0.0077, +0.0004] |
| interwar | 281 | 83 | -0.648 | -0.1193 [-0.2001, +0.0163] | -0.0199 [-0.0445, +0.0114] |
| Bretton Woods | 468 | 3 | -0.149 | -0.0257 [-0.0438, -0.0175] | -0.0093 [-0.0144, -0.0068] |
| 1972-1995 | 324 | 52 | -0.484 | -0.0056 [-0.0353, +0.0045] | -0.0003 [-0.0036, +0.0007] |
| pooled | 1,715 | 290 | -0.450 | -0.0316 [-0.0600, -0.0114] | -0.0072 [-0.0127, -0.0029] |

Permutation p 0.93 (null mean -0.0158, 200 draws). Placebo (the next
country's states) -0.0237.

**Terms**, pooled over the four held-out eras. The coefficient is per SD,
fitted on all development rows; ! marks the wrong sign.

| term | log gain [90%] | Brier | perm p | Holm | coef | placebo |
|---|---|---|---|---|---|---|
| credit gap | -0.0099 [-0.0271, +0.0001] | -0.0034 | 0.990 | 1.000 | +0.47 | -0.0040 |
| house | -0.0035 [-0.0122, +0.0032] | -0.0009 | 0.791 | 1.000 | +0.38 | +0.0002 |
| equity | -0.0132 [-0.0315, +0.0013] | -0.0022 | 0.771 | 1.000 | +0.31 | -0.0138 |
| current account | -0.0015 [-0.0043, +0.0013] | -0.0006 | 0.507 | 1.000 | -0.10 | -0.0014 |
| credit x house | -0.0019 [-0.0051, +0.0045] | -0.0007 | 0.597 | 1.000 | +0.46 | -0.0051 |
| debt service (record) | +0.0083 [-0.0072, +0.0181] | +0.0014 | 0.040 | | +0.28 | -0.0268 |

**Horizon profile.** An onset in year t+h exactly, on the same rows. The
joint log gain, then s x coef per SD by term:

| h | onsets | joint log gain [90%] | credit gap | house | equity | CA | credit x house |
|---|---|---|---|---|---|---|---|
| 1 | 70 | -0.0060 [-0.0210, +0.0021] | +0.44 | +0.20 | -0.21 | +0.23 | +0.74 |
| 2 | 65 | -0.0085 [-0.0219, +0.0005] | +0.22 | -0.04 | +0.21 | +0.18 | +0.61 |
| 3 | 59 | -0.0008 [-0.0061, +0.0034] | +0.44 | +0.06 | +0.58 | +0.00 | -0.33 |
| 4 | 52 | -0.0061 [-0.0109, -0.0027] | +0.34 | +0.20 | +0.31 | +0.01 | -0.81 |
| 5 | 48 | -0.0093 [-0.0148, -0.0051] | +0.11 | +0.37 | +0.23 | -0.00 | -1.70 |

The rise (mean gain at h 4-5 less h 1-2) is -0.0005, permutation p 0.47.
s(c45 - c12) by term: credit gap -0.11, house +0.21, equity +0.26,
current account -0.20, interaction -1.93.

**Record: the states demeaned by year** (`--demean yes`,
data/runs/sequencing/t5-dev-demeaned.txt): the country part only.
- Joint pooled -0.0129 [-0.0220, -0.0049], p 0.90.
- By era: pre-WWI -0.0006, interwar -0.0527, Bretton Woods -0.0164,
  1972-1995 +0.0022.
- Terms: house +0.0002 (p 0.17, Holm 0.87), equity -0.0000 (its
  coefficient goes to +0.00: equity growth's signal is all world
  component), credit gap -0.0088 (p 0.98), interaction -0.0051 (wrong
  sign).
- Rise -0.0015, p 0.55.

**Power** (data/runs/sequencing/t5-power.txt). A single synthetic own
state, 50 draws per OR (Monte Carlo error up to 0.07), 40 permutations.
Each cell is the share of draws passing: bootstrap (90% interval above
0) / permutation (p <= 0.05) / mean log gain.

| OR per SD | development (4 eras, LOEO) | test era (origins 1996-2015) |
|---|---|---|
| 1.0 | .06 / .10 / -0.0022 | .14 / .04 / -0.0037 |
| 1.35 | .18 / .36 / +0.0027 | .34 / .32 / +0.0020 |
| 1.5 | .32 / .60 / +0.0057 | .40 / .50 / +0.0061 |
| 2.0 | .52 / .94 / +0.0192 | .52 / .74 / +0.0189 |
| 2.5 | .82 / .98 / +0.0331 | .54 / .82 / +0.0301 |
| 3.0 | .94 / 1.00 / +0.0434 | .58 / .86 / +0.0423 |

**Reading.**
- *The states fit in sample but do not transfer across eras.* Fitted on
  all development rows, every term has its expected sign. The credit gap
  is +0.47 per SD and the interaction +0.46 (Greenwood et al.'s sign,
  also seen in T2). Out of era, the joint model loses to history alone
  in every era. Pooled, it loses more than its placebo does (-0.0316
  against -0.0237).
- *The interwar era is the largest loss.* The early-1920s Nordic crises
  (Sweden, Denmark, Norway, Finland) came after wartime inflation had
  made real house and equity growth deeply negative, so the states said
  "safe" just before them.
- *There is no rise with lead.* The joint gain is about flat (and
  negative) from h = 1 to 5. The credit gap's coefficient falls with
  the lead, and the interaction's turns negative by h = 4-5. That is a
  trigger-like profile, the opposite of the slow-stock hypothesis.
- *The debt-service proxy is the only positive term* (p 0.04 unadjusted,
  for the record). It is the term Drehmann & Juselius call a short-horizon
  signal, and it was kept out of Holm before the run.
- *Power.* The permutation test holds its size in the test era (0.04).
  The bootstrap does not: 0.14, since four five-year blocks out of 20
  origin years are too few. With four eras, development finds one state
  at OR 1.5 per SD 60% of the time and OR 2 94%. The test era alone finds
  OR 2 74% and OR 2.5 82%, about a +0.03 log gain per row. The effects
  the literature reports (OR 1.2-1.5 per SD) are below what 1996-2020 can
  show.

## The registered test

Not run. The test era's outcomes are unread. After registration:

    jolt -A:tools -M -m bnm.half-decade --mode test --confirm-registered yes --draws 200 --boot 1000 --seed 1 --out data/runs/sequencing/t5-test

Every development row (origins 1870-1990) is used to fit. Origins
1996-2015 are scored, with outcome windows to 2020. Origins 1991-1995
are never scored: their windows straddle the boundary. The output has
the same sections as development, with the test era as the one era. The
record variant adds `--demean yes --out data/runs/sequencing/t5-test-demeaned`.
Different eras: `--eras name:from-to,...` and `--test-era name:from-to`.

## What I would pre-register

- **Hypothesis.** The five slow states improve the log score of banking
  crisis onsets within five years over the country's and the world's
  history.
- **Primary.** The joint five-term model, fitted on all development
  rows. Its log gain per row on origins 1996-2015 must have a one-sided
  permutation p <= 0.05 (200 permutations of whole country series, every
  fit redone). The 90% block-bootstrap interval and the Brier gain are
  reported, but they are not the criterion: in the power check the
  bootstrap passed a null state 14% of the time on 20 origin years.
- **Equivalence bound.** A level result means a gain under about +0.03
  log per row, the expected gain at OR 2.3-2.5 per SD,
  where the test era has 80% power. Nothing smaller can be ruled out.
  Development already says the gain is negative out of era.
- **Secondary, one-sided 5%.**
  - Each term's gain, Holm over the five.
  - The rise of the joint gain from h 1-2 to h 4-5 (the slow-stock
    profile), permutation p.
- **For the record.** The placebo, the year-demeaned states, the
  debt-service proxy, and per-h coefficients.
- **Prediction from development:** level or negative on all three.

The power table is the main thing to weigh before registering.
1996-2020 can confirm only an effect of OR 2.3 or more per SD, and the
four development eras already show the states losing out of era.
Registering it as specified would test whether 1996-2020 behaves like
the eras before, not whether the literature's effect sizes exist. Two
alternatives with more power:
1. Fold the test era into the rotation (five eras, leave one out). This
   gives up the untouched test.
2. Pool banking crises with currency crises and growth collapses through
   one coefficient per state (LIT-NEXT-STEPS 3c). JST has no currency
   crisis dates, so this needs the catalogue.
