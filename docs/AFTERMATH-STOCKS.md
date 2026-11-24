# What an onset leaves behind: the theory for the decay at the kinds without a theory

Written 23 November 2026, before any run, at the user's request to set out a theory before extending t137's decay (docs/CALIBRATION.md t137-t140) to the kinds whose year node has no kind theory. Fixed here; a run tests it, it is not tuned on the result.

## The claim

An onset changes the stocks that measure the conditions which produced it, and those stocks stay changed for years: a war leaves soldiers under arms and rights abused, a banking crisis leaves credit high against output while it is worked down, a food price shock leaves the price level high after the rise has stopped. A model with one slope per stock reads the leftover as it reads the same level in calm years. Whether the leftover means more risk or less depends on the kind:

- **Recurrence (a trap).** The onset did not resolve what caused it, and the stock it raised keeps the risk high: the conflict trap. The stock's slope should be larger, and the aftermath's own level higher, soon after an onset.
- **Refractory (a discharged stock).** The onset discharged the pressure; the raised stock is a residue, not pressure building. The slope should be smaller soon after an onset (the interaction's sign opposite the main effect's) and the aftermath's level lower. Bussière and Fratzscher (ECB WP 145, 2006) call the error of reading recovery years as tranquil ones "post-crisis bias".

The data choose the sign kind by kind. The theory says which stocks are the residues, so that the model is offered those and not every gauge.

## The encoding, for every kind below

The year node reads a gauge group of its own, `:decay` (bnm.year-gauges/with-group, t133's design, so the averaging gives it its own intercept and weighs it against the node's other groups): d = 2^(-(t - m)/48), m the country's latest own-kind onset known at t (the catalogue's publication lag, t133's rule), 0 when none is known; and each listed stock's standardized value (the node's own gauge, as it reads it) times d. The half-life of four years is t137's, fixed before t137 so that d reads a quarter at eight years, the old gate's length.

d itself, the aftermath's level, is in the group except where the year node already reads its own onsets decaying (growth collapses: t133's own kernels; banking: its process group's own-k3, own-k10, own-k30), so the level is not added twice. t137 and t139 showed the level is the useful part where a kind recurs (coups, inflation crises); t140 showed a change can move the averaging's weights between models, so each kind's weights are read beside its score.

## The stocks, kind by kind

The political year node (conflict and war onsets, protest and violence surges, food price shocks, growth collapses) reads the same 37 base gauges (bnm.lattice-political year-names), from which the stocks are taken by name.

| kind | stocks (year gauges) | mechanism | expected | rank 0 (t125-base, p / rate) |
|---|---|---|---|---|
| conflict onset | physical-integrity, repression, soldiers-per-capita, military-executive | the conflict trap: a war's armies, abuses and military rule outlast it, and the first post-war decade carries the highest risk of a new war (Collier et al., *Breaking the Conflict Trap*, World Bank 2003; Walter, "Does Conflict Beget Conflict?", JPR 2004); post-conflict military spending raises the risk of relapse (Collier and Hoeffler, *Economics of Governance* 2006) | recurrence | under just after: 0-1 yr 0.253 / 0.352, 2-4 0.240 / 0.267 |
| war onset | the same four | the same trap, at the larger threshold | recurrence | 0-1 0.210 / 0.222, 2-4 0.180 / 0.130: weaker or none |
| violence surge | physical-integrity, repression | state violence after a surge is the surge's residue as well as its cause (Davenport, "State Repression and Political Order", *Annual Review of Political Science* 2007) | either; small | calibrated after an onset (0-1 0.207 / 0.208) |
| protest surge | repression, participation, assembly | cycles of contention end in demobilization: repression and exhaustion after a wave (Tarrow, *Power in Movement*, 1998) | refractory | over just after: 0-1 0.267 / 0.208, 5-8 0.126 / 0.093 |
| food price shock | food-inflation, inflation | the shock is a rise; after it the price level and inflation stay high while the rise has passed | refractory | 2-4 0.258 / 0.209, 9+ 0.143 / 0.236, never 0.250 / 0.130 |
| growth collapse | growth3, inflation | after a collapse three-year growth is low by construction, and output does not regain its old path (Cerra and Saxena, "Growth Dynamics: The Myth of Economic Recovery", AER 2008): the low reading is the collapse itself, not a new one coming | refractory (no level: t133's kernels) | 2-4 0.100 / 0.068, 5-8 0.095 / 0.057 |
| banking crisis | the credit, mortgage and house-price stretches, public debt (stretches 0, 1, 2, 6), loans to deposits (stabilizers) | credit stays high against output for years after a crisis while it is worked down, house prices fall over about six years, public debt rises (Reinhart and Rogoff, "The Aftermath of Financial Crises", AER P&P 2009; Jordà, Schularick and Taylor, "When Credit Bites Back", JMCB 2013): the stretches read the overhang as a boom | refractory (no level: its own kernels) | over across 0-8 years (0.043, 0.047, 0.038 / 0.023, 0.022, 0.021) |

**Market crashes are left out.** Their year node gives 0.99 of its weight to what the quarter nodes pass up (t135), and the year node reads no country market stock; their aftermath was taken up at the quarter nodes by t136's direction gauges.

## What would refute it

Per kind, against the base: the interaction slopes take the sign the table expects (read from the node; the run's score does not show signs), rank 0's bands move toward their rates after an onset without loss where there was none, and the score passes the fold rule. A kind whose score passes while its band moves away from its rate, or the other way, is a kind the theory does not explain.
