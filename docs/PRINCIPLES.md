# Principles every addition keeps

What the project has learned to work, written down so new mechanisms
(stocks and flows, delays, archetypes; docs/STOCK-FLOW.md) add fidelity
without losing it. A design that fails one of these is not merged. The
mechanical ones are smoke checks (bnm.lattice-smoke).

1. **On the lattice.** Every quantity lives at a node: a place (country,
   subregion, region, world), a time scale (quarter, year, half-decade)
   and a dimension (an event kind, a sector, a stock). docs/LATTICE.md,
   docs/WORLD-MODEL.md.

2. **Learned, not set.** Parameters (coefficients, delays, adjustment
   times, table points, elasticities) are learned by Bayesian dynamic
   models from what was known at each origin, pooled up the place tree
   where data are thin. Nothing is tuned by hand to a history the model
   is then scored on. Smoke: no read or learning from after the origin.

3. **Models feed models.** A node passes its state down (lagged), its
   strain up, its resolved errors across, and a coarser forecast down as
   the offset a finer one departs from, with its uncertainty
   (docs/DOWNSCALE.md). Each read is from before t and recorded.

4. **Contradictions.** A node's imbalances are pairs of opposed aspects
   (credit against wages, coverage against desired coverage, debt service
   against income, observed against expected progress, a place against
   its parent); their gaps accumulate as strain, which resolves at a leap
   (an onset) where theory says it does, and the leading one is recorded
   as the principal contradiction. A negative relation is a signal, its
   sign stated as a prior and checked on the design years. docs/ANATOMY.md.

5. **Selection pressure.** Nothing enters a forecast by assertion. Model
   averaging weighs each group of inputs by how it forecasts; a new input
   is weighed as the node with it against the node without it (nested),
   and a link or source is checked against a placebo (the same input from
   another country or period). An input whose gain its placebo matches is
   the period's or the place's, not the mechanism's.

6. **Within, not level.** What one node passes another should be what
   changed at the place (an onset known, a state against its own past, a
   stock against its target), not a level that carries the place's lasting
   risk the receiver already sees.

7. **Known when known.** Every source enters with its publication lag:
   onsets after their known-lag, annual data a year late, input-output
   tables three years late. Excursions and baselines discount for the
   lag.

8. **Accounting closes.** A stock changes only by its flows; each flow
   leaves one stock and enters another (or a stated outside); units
   agree. Extreme inputs give sane outputs (Barlas 1996).

9. **Test first, register first.** Code is written test-first; the
   engine is checked against reference trajectories where they exist
   (World3-03, Earth4All). Designs are chosen on the design years
   (to 2002); a trial's tests are registered in docs/PHASE-CHANGES.md
   before any test year is scored, and results are reported as they
   fall, losses included.

10. **Bugs before conclusions.** A mechanism that does nothing or costs is
    first suspected of a bug: trace each read, check it is live, compare a
    case that must match exactly (a kind with no parent against the world
    without links), and diagnose on the design years before redesigning.
