# The lattice: a model per place and per time scale

Trials 31-35 tried to make the quarterly country hazard better by adding
structure to it: intercepts over the geography, states shared by regions,
precursors from the event streams, slopes for calm countries. None helped
on the hard rows. The reason is the framing. A crisis is quantity turning
into quality: stocks build for years, the small crises of each decade feed
the large ones of a century, and the turn comes when enough of it has
accumulated. A quarterly model of one country cannot see that. It sees one
scale, one place, and weights learned from two decades.

This document sets the framing the model is rebuilt toward (bnm-969).

## Two dimensions

The system is hierarchical in two separate dimensions:

- **space**: country, subregion, region, world (the M49 tree, bnm.geo);
- **time scale**: week, month, quarter, year, decade (and century, as a
  prior only).

Each region evolves through time, and the system as a whole does too. A
**node** is one place at one scale: Kenya by the quarter, East Africa by
the year, the world by the decade. Every node is its own model, predicting
what happens in its place at its scale, with its own inputs and its own
weights: what matters to a week in one country need not matter to a
decade in a region. The invariants can differ by scale.

## The lattice as a sheaf

The reads between nodes go through bnm.sheaf. A node's book is a sheaf
over time: its sections by origin, which a read restricts to an interval
(the latest before t, a period, an origin), each recorded and refused if
after the reading node's origin. The places and scales form the base, a
poset of cells [scale place] (a country under its region, a month under
its year): coarser and ancestor sections come down by restriction, finer
and child sections come up aggregated, the coarser forecast comes down as
an offset, resolved errors once known. Reconciliation is the gluing; the
contradiction gaps are the coboundary, a section against the comparable
cells above it, each neighbour carried by its edge's map (identity for
now; a loading or a stock's integration later) (Robinson 2017; Hansen &
Ghrist 2019). Another kind's book is read through a reader that names the
kind (bnm.relations), so every read has one shape; the kinds as a
further axis of the base come with the multilayer network (bead
bnm-62z.16). A golden snapshot (test/bnm/lattice_golden_test) pins the
lattice's outputs and reads exactly, in full and with groups dropped.

## What each node does

- **Target**, at its own scale and horizon:
  - week: event counts by channel (the event model, bnm.model);
  - month: surges in the protest, violence and coercion channels;
  - quarter: onsets within one to two years (bnm.onsets' windows);
  - year: onsets and crises within one to three years, on the long
    catalogues (Reinhart-Rogoff banking crises from 1800, Correlates of War
    disputes from 1816, the Macrohistory crises from 1870, UCDP from 1946);
  - decade: crises and conflicts within five to ten years;
  - century: not validatable (a handful of events); a prior over the
    decade nodes, never scored.
- **Inputs**:
  - its own scale's gauges: stocks at the slow scales, flows at the fast
    ones;
  - **bottom-up in time**: the finer scale's forecasts for the same place,
    as issued at or before the node's origin, passed as distributions
    (mean and spread, or the probability of exceeding a threshold), not
    as points. A year node reads its quarters, a decade node its years.
    This is "revolt" (Holling): small fast turns feeding large slow ones;
  - **bottom-up in space**: its children's forecasts at the same scale
    (the chance that any of them has an onset, and how many);
  - **top-down**, lagged one step: the coarser scale's and the larger
    region's latent stress, forecast from what was known before the
    origin (Berry & West 2020's decouple and recouple). This is
    "remember": the slow level constraining the fast.
  Each quantity flows in one direction only and always lagged, so nothing
  is counted twice and the weights stay identifiable.
- **Learning from experience**: a dynamic generalized linear model (West,
  Harrison & Migon 1985): logistic for onsets, Poisson for counts. The
  coefficients are a state updated after each period's outcome, with a
  discount factor that lets them drift (near 0.97 at the week, near 1 at the
  decade, tuned online by predictive likelihood, McCormick et al. 2012).
  Nothing is refit: each node carries what it has learned forward.
- **Starting from what is known**: each gauge enters with an informative
  prior, its mean the sign and size the literature gives (credit gaps,
  debt burdens, wealth gaps, education, regime type) and its variance how
  far that is trusted; unknown inputs start at zero.
- **Discovering what matters**: dynamic model averaging (Raftery, Kárný &
  Ettler 2010) over groups of gauges at every node. The posterior weight
  of each group through time is the model's own account of what matters
  at that place and scale, and of when that changed. A group with no
  input at any of the node's places at an origin (a source that has
  ended, a signal gone stale, errors not yet known) is an intercept-only
  model under another name: it drops out of that origin's forecasts,
  weight 0, and comes back when its source resumes (bnm-wns).
- **Pooling**: sibling nodes (the countries of a subregion at one scale)
  share a prior on their slopes, centred on their parent's; slopes, not
  levels, since trials 31-33 found that levels learned from a country's
  own onsets cannot help the countries without any.
- **Coherence**: counts are reconciled across scales and places after the
  base forecasts (Corani & Zambon's bottom-up importance sampling), and
  binary onsets read off as the chance of at least one.

## The system as a dialectic

The lattice is not only a tree of forecasts passed up and down. A society
or an economy is a unity of opposed tendencies, and its crises are where
their contradictions, built up by quantity, turn into a change of quality.
The model carries that in four ways.

- **Contradictions between levels.** Every node has a state (its
  forecast's log odds, or its gauges where the spec gives one, as for a
  world energy gauge). Each place is set against its parent at its own
  scale, and against itself at each coarser scale: a calm country in a
  region under strain, a quarter that looks quiet inside a year that does
  not. Each gap, its size, and its size accumulated through time are
  inputs. Tension built at finer scales is passed up and accumulated again
  at the coarser node's own pace, so months of local strain become years
  of it. Like is compared with like: a place against its parent, a scale
  against a coarser one, never a mean of unlike signals.
- **Contradictions within a place.** Pairs of opposed aspects, fixed from
  theory before any run: credit against investment (credit growing without
  productive accumulation), asset prices against the wage share, credit
  against the curve, a country's credit cycle against the world's. Each
  gap and its accumulation.
- **Resolution.** When a crisis at a place becomes known, the tensions
  accumulated there reset: the leap negates the contradiction that built
  it, and the place starts accumulating anew. This is what lets risk fall
  after a crisis instead of chasing the last wave, and it uses only what is
  known when it is known.
- **Shocks from above, through exposure.** What comes down from coarser
  nodes (a world energy shock, a global credit cycle) acts through each
  place's exposure to it (trade and capital openness, fuel imports), so
  the same shock lands hard where a place is open and lightly where it is
  not.

And the levels learn from each other:

- **Errors across levels.** Each node reads the last resolved errors of its
  parent, its coarser scales and its children: a region's surprise is
  information for its countries, and theirs for it.
- **Coupled learning.** Adjacent levels reading the same gauge group pull
  their coefficients toward each other's, weighted by how certain each is
  (augur.dynamic/couple), means only, so no evidence is counted twice:
  what the regions learn about credit informs the countries, and the
  countries' experience informs the regions.

On synthetic worlds built to need them, removing the contradictions cost
the year node 0.29 in log score; the errors fed across levels added 0.08,
and only on top of the contradictions (test/bnm/lattice_test.clj).

## The five gaps this closes

1. **Invariants on the slow layer.** The displacement invariants that held
   across 150 years (docs/INVARIANTS.md: norm, breadth, resonance; Dalio's
   share of measures past one standard deviation) are built from the slow
   stocks at the year and decade nodes, against each country's own past
   at 10, 20 and 30 years, not from quarterly event counts (trial 34).
2. **Horizon.** Each node is scored at its own horizon: the slow stocks are
   judged on whether a crisis follows within years, not on which quarter.
3. **Nested cycles.** Small crises feed large ones through the bottom-up
   flow in time; where a place is in its long cycle (time since its last
   systemic crisis or deleveraging, the world's wave intensity from
   bnm.waves) comes down as the top-down factor.
4. **Sequence.** The decade nodes carry each gauge's turn (its ten-year
   trend changing sign) and which gauges have turned, so the order in
   which they turn (education, then innovation and competitiveness, then
   output and trade, then the financial centre and the currency, in
   Dalio's archetype) can be learned rather than assumed.
5. **Depth.** The year and decade nodes learn from the long panels (from
   1789, 1800, 1816, 1870) before the evaluation years: by 2003 they carry
   two centuries of experience, without any scored year in it.

## Evaluation

Every node is scored at its own cadence, rolling forward, on forecasts
issued from what was known at the time, by the paired Brier and log scores
pooled across the places of a scale (bnm.paired, registered October 2026),
with reliability per scale. Decade nodes have few outcomes: they are
reported with wide intervals, and the century is not scored at all. The
quarter node is also scored against the current record (`named+mix`) on
the hard rows, the test every trial so far has failed.

## Order of work

1. The node: a dynamic logistic and Poisson model with discounting,
   informative priors and model averaging over groups (augur).
2. The lattice: nodes, their schedule through time (a coarse node at t
   reads only what finer nodes issued by t), the messages up and down, and
   a synthetic test where a coarse outcome depends on accumulated fine
   signal and only the lattice can see it.
3. Scales wired in: the week from the event model's forecasts, the quarter
   from the onset rows, the year and decade from the long panels and slow
   stocks, each across country, subregion, region and world.
4. Gauges: Dalio-style composites, their displacement, breadth and turns.
5. Evaluation per scale, registered before it is run.
