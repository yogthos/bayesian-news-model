# Relations across dimensions: toward a unified world model (research, October 2026)

The lattice so far has two axes, place and time scale, and runs one kind of
event at a time. Each kind's model sees the others only as counts of their
past onsets (the political year nodes) or not at all (banking reads no
political event, no political kind reads a financial one). But coups and
unrest grow out of economic destabilization, currency and sovereign crises
follow banking crises, and violence builds before wars. This note surveys
how to model those relations and proposes a structure.

## What the evidence says the relations are

- **Financial to financial.** Currency and sovereign crises coincide with
  or follow banking crises, currency crises peaking a year after (Laeven &
  Valencia 2018); crises mutate from one type into another, banking to
  currency and back being common (Candelon, Dumitrescu, Hurlin & Palm
  2013, a multivariate dynamic probit); the order within a twin episode
  matters for its drivers and costs (Balteanu & Erce 2018). In our
  anatomy, currency and sovereign crises stand +0.3 above the calm before
  banking crises.
- **Financial to political.** After financial crises, and not after
  ordinary recessions, polarization rises, majorities shrink and the far
  right's vote grows about 30% (Funke, Schularick & Trebesch 2016, 1870-
  2014).
- **Economic to political.** Spending cuts drive riots, demonstrations and
  political violence, far more than tax rises, less where the executive is
  constrained (Ponticelli & Voth 2020, Europe 1919-2008); output
  contractions bring forward regime change (Burke & Leigh 2010); commodity
  price falls sparked civil wars in Sub-Saharan Africa (Ciccone 2018,
  against Bazzi & Blattman 2014, the difference in how exposure is
  weighted). The economic channel is real but runs through exposure and
  institutions, which is why it is weak when estimated as a single slope.
- **Political to political.** In our anatomy, coups stand +0.5 above the
  calm in the ten years before conflicts and conflicts +0.4 before coups;
  violence surges build from zero to +0.5 before conflicts. Events of one
  type excite others (multivariate Hawkes processes of political violence:
  Jun & Cook 2024; protest contagion conditional on triggers: Subramanian
  & Titus 2017).
- **Stress plus trigger.** A global crisis arises when fast-moving trigger
  events combine with slow-moving underlying stresses, and crises in one
  system enable and limit those in others (Lawrence, Homer-Dixon et al.
  2024, the causal mechanisms of polycrisis; the Cascade Institute's
  Polycrisis Core Model integrates the causal links among eleven global
  systems by cross-impact balance).

## How others structure it

1. **Decouple and recouple** (West and coauthors). Simultaneous graphical
   dynamic linear models (Gruber & West 2016, 2017; review Vrotsos & West
   2024) and dynamic dependence networks (Zhao, Xie & West 2016): each
   series is its own univariate dynamic model whose regressors include its
   *parents* among the other series, through a sparse, time-varying graph.
   Each model learns on its own, in parallel and fast (decouple); the joint
   distribution is rebuilt from the graph by importance sampling (recouple),
   so forecasts are coherent across series. Parent sets are given, chosen
   by a heuristic, or given prior probabilities and averaged over. Berry &
   West 2020 add the top-down form: an aggregate model's predicted factor,
   sampled, enters every series' model. **This is the lattice's own
   machinery (dynamic GLMs, model averaging, the year carried down) with a
   third axis.**
2. **Causal discovery for the graph.** PCMCI and PCMCI+ (Runge et al. 2019,
   Runge 2020): conditional-independence tests on lagged and
   contemporaneous links in autocorrelated, nonlinear time series, with
   false-positive control, to find which dimensions lead which.
3. **Mutually exciting events.** Multivariate Hawkes processes: each event
   type's intensity rises after events of its own type and of the others,
   with decaying kernels; the cross-excitation matrix is the relation.
   Discretely observed versions exist for counts by period (Lambe et al.
   2025). Our own experience (bd memory: the branching ratio is not
   identified from the level) says the baseline must be a discounted local
   level with excitation as excursions above it.
4. **Joint crisis models.** Multivariate dynamic probits (Candelon et al.
   2013) estimate the crisis types together, each type's lagged state in
   the others' equations; Large Bayesian VARs for binary and censored
   variables (2025) and multinomial logits for concurrent recessions (Poon
   & Zhu 2024) do the same in a VAR.
5. **Shared latent states.** Joint models of longitudinal markers and
   events link processes through shared latent effects; for count data,
   Pólya-gamma augmentation makes dynamic logistic and negative binomial
   models conjugate (Polson, Scott & Windle 2013), and Poisson-gamma
   dynamical systems and Poisson tensor decompositions learn latent
   structure in event counts between countries (Schein et al. 2016, 2019).
6. **Connectedness networks.** Global VARs (Pesaran et al. 2004) link
   countries' models through foreign aggregates; Diebold & Yilmaz read the
   variance decomposition as a directed, weighted network of spillovers,
   used in the IMF's and ECB's financial stability reports.

## Proposal: dimensions as a third axis, relations as their own models

Keep the lattice's nodes (place × scale) and add the **dimension** (or
kind): each node becomes a set of per-dimension models at the same place
and scale, and a layer of **relation models** links them.

- **Dimensions.** Financial (banking, currency, sovereign, market crash),
  economic (growth collapse, inflation surge, food and energy shocks,
  austerity), political (coups, protest, violence, conflict, war), and the
  slow structural states (regime, demography, inequality). Economic events
  are added as kinds of their own, by rules fixed in advance (Laeven &
  Valencia's currency rule; inflation past 40%; growth more than two
  standard deviations below the country's own trend; spending cuts past a
  threshold), so the economic destabilization the theory names is a thing
  the model sees and forecasts, not only an input.
- **Each dimension's state.** The node's linear predictor and its
  predictive variance (augur.dynamic/predictive, already exposed) are its
  latent stress: a continuous state per place, scale and dimension, which
  is what other dimensions read, rather than the yes-or-no onsets.
- **Relation models, decoupled.** Each dimension's model reads, as a new
  input group, the **lagged states of its parent dimensions** at the same
  place and scale (SGDLM/DDNM parents), their recent onsets through decaying
  kernels at several scales (the Hawkes cross-excitation, as excursions
  above a local level), and **cross-dimension tensions** (one dimension's
  stress running ahead of another's capacity: economic stress ahead of
  political accommodation, financial strain ahead of fiscal room). The
  parent sets are sparse and directed, with priors from theory and the
  sequencing table, and averaged over by the existing dynamic model
  averaging, so each relation earns its weight by predictive likelihood
  and the weights through time are the model's own account of what drives
  what, where and when.
- **Recoupling: the world model.** Forecasts are propagated through the
  dimension graph by simulation (sample a parent's state from its
  predictive, pass it to its children, as Berry & West do top-down), so
  the joint forecast is coherent: the chance of a banking crisis followed
  by a coup in the same country, scenarios conditioned on one dimension's
  shock. Across places and scales the lattice's flows remain; the
  dimension graph adds the third direction.
- **Exposure and institutions as moderators.** The evidence says economic
  shocks reach politics through exposure (export structure, food and fuel
  imports) and are buffered by constraints on the executive; the relation
  inputs enter times those moderators, as the lattice's shocks from above
  already do through exposure.

## Order of work

1. **Sequencing, before any training** (the anatomy across dimensions): on
   onsets before 2003, a lead-lag table of every kind against every other
   (including the new economic kinds), at year lags -10 to +3, in calm
   deviations, and a PCMCI-style test of the lagged links conditional on
   each kind's own past, to fix the graph's candidate edges and their signs.
2. **One run for all kinds**: the lattice runs every dimension together so
   their states can be read across, each dimension still its own model.
3. **The relation group**: parent states, cross-excitation kernels,
   cross-dimension tensions, with priors from step 1; smoke checks extended
   (every relation input live, read only from before the origin).
4. **Recoupling** by simulation for joint and conditional forecasts.
5. **Evaluation**, registered: each dimension with relations against
   without, at its own scale; the joint forecasts' calibration; the quarter
   nodes against named.

## Sources

- Gruber & West, GPU-accelerated Bayesian learning in simultaneous graphical dynamic linear models, Bayesian Analysis 2016; Vrotsos & West, Simultaneous graphical dynamic modeling, arXiv 2410.06125
- Zhao, Xie & West, Dynamic dependence networks, ASMBI 32(3) 2016 (arXiv 1606.08339)
- West, Bayesian forecasting of multivariate time series: scalability, structure uncertainty and decisions, AISM 2020 (arXiv 1911.09656); Berry & West, JBES 2020
- Runge et al., Detecting and quantifying causal associations in large nonlinear time series, Science Advances 2019; Runge, PCMCI+, UAI 2020 (arXiv 2003.03685)
- Jun & Cook, Flexible multivariate spatiotemporal Hawkes process models of terrorism, AoAS 18(2) 2024; Lambe et al., arXiv 2503.18351; Subramanian & Titus, WSC 2017
- Candelon, Dumitrescu, Hurlin & Palm, Multivariate dynamic probit models: an application to financial crises mutation, Advances in Econometrics 32, 2013; Balteanu & Erce, IMF Economic Review 2018
- Laeven & Valencia, Systemic banking crises revisited, IMF WP/18/206
- Funke, Schularick & Trebesch, Going to extremes, EER 2016 (CEPR DP10884)
- Ponticelli & Voth, Austerity and anarchy, JCE 2020 (CEPR DP8513); Burke & Leigh, AEJ: Macro 2010
- Bazzi & Blattman, AEJ: Macro 2014; Ciccone, International commodity prices and civil war outbreak, CEPR DP12625
- Lawrence, Homer-Dixon, Janzwood, Rockström, Renn & Donges, Global polycrisis: the causal mechanisms of crisis entanglement, Global Sustainability 2024; Cascade Institute, Polycrisis Core Model v2.5, 2025
- Pesaran, Schuermann & Weiner, JBES 2004 (GVAR); Diebold & Yilmaz, connectedness
- Polson, Scott & Windle, Bayesian inference for logistic models using Pólya-gamma latent variables, JASA 2013; Schein et al., Bayesian Poisson Tucker decomposition for international relations, ICML 2016; Poisson-randomized gamma dynamical systems, NeurIPS 2019

## Chinese approaches, and what they change

- **Open complex giant systems and meta-synthesis** (Qian Xuesen; Yu
  Jingyuan, Dai Ruwei 2001). A society is an open complex giant system:
  many kinds of subsystems, many levels, open to its surroundings, with
  people in it. No single model of it is adequate; the method is
  "from qualitative to quantitative": experts' hypotheses, data and models
  integrated and revised in a loop (the hall for workshop of
  metasynthetic engineering). *For us*: every relation edge is an explicit
  hypothesis with its source (theory, anatomy, sequencing), its prior and
  its learned weight through time, kept in one registry and reviewed; the
  model's record of which hypotheses earned weight is the quantitative
  half of the loop.
- **Wuli-Shili-Renli** (Gu Jifa & Zhu Zhichang 1994): a system is known
  through its material order (wuli), the logic of its processes and
  institutions (shili) and its human relations (renli). *For us*: the
  dimensions fall into three tiers, matching base and superstructure: the
  material base (output, prices, food and energy, demography, sectoral
  balance sheets), institutions and processes (regime, fiscal and
  financial systems, the military's place) and actors (protest,
  violence, coups, elites, public mood). The graph's prior runs base to
  institutions to actors, with feedback edges back (protest to regime
  change, war to output), so its sparsity has a structure rather than
  being a list.
- **Parallel systems, ACP** (Wang Fei-Yue, from 2004): artificial
  societies to represent the system, computational experiments to
  evaluate and predict, parallel execution in which the artificial system
  runs beside the real one and learns from their divergence. *For us*:
  the recoupled world model is the artificial system; scenarios are its
  computational experiments (set a parent's shock, follow its children);
  its divergence from what happens is tracked continuously and fed back
  (the lattice's feedback group, now across dimensions).
- **Event logic graphs** (Ding, Li, Liu & Liao 2019, Harbin Institute of
  Technology): events as nodes, with sequential, causal, conditional and
  is-a edges, mined from text, to predict what follows. *For us*: the
  fast scales' event streams (GDELT's coded event types, weeks and
  months) give a data-driven graph of which event types follow which;
  the is-a edges are our tiers (specific event types roll up into
  kinds and kinds into dimensions); its edges are candidates for the
  fast scales' relations, as the sequencing table is for the slow ones.
- **Risk coupling** (the N-K model and coupling-coordination degree in
  Chinese safety and systems engineering): risk rises with the number of
  factors coupled at once, measured by the information in their joint
  occurrence, beyond each factor's own. *For us*: a coupling input, how
  many dimensions are stressed together at a place and the interaction
  their pre-2003 co-occurrence earned (the polycrisis), generalizing the
  breadth invariant from stocks to dimensions.
- **Sectoral transmission** (CASS's national balance sheets, Li Yang and
  colleagues; time-varying spillover networks of local government debt,
  real estate and banks): risk passes between sectors through their
  balance sheets' mismatches, and which sector transmits and which
  receives changes over time (local government debt a net transmitter
  until 2020, a receiver after). *For us*: the financial dimension is
  sectors (households, firms, government, banks, external) linked by
  their stocks, and the world model reports connectedness: each
  dimension's share of every other's forecast variance, by period, so
  the transmitters and receivers through time are an output.
- **Integration for forecasting** (TEI@I, Wang Shouyang): text mining,
  econometrics and learned models integrated, decomposing a complex
  series into parts, modelling each and recombining. The decouple and
  recouple design is this in Bayesian form.
- **Societal risk perception** (Tang Xijin): search terms and forum
  posts measure how the public perceives risk, and negative moods predict
  it. *For us*: the actors' tier needs perception as well as events: the
  event streams' tone and, where available, search interest.
- **The principal contradiction** (Mao, On Contradiction, 1937): of a
  process's many contradictions one at each stage is principal and
  determines the others, and which one it is shifts. No formal
  quantitative version turned up. *For us* it can be one: at each place
  and origin, the tension or relation contributing most to the forecast
  (its weight times its strain) is the principal contradiction; its
  identity and a recent shift in it are inputs, and its history is how
  the model explains a place.
- **Grey systems** (Deng Julong): models for small samples and poor
  information (GM(1,1)), grey relational analysis ranking factors by the
  similarity of their curves to an outcome's. Their critique is that they
  drop the statistical inference; informative priors do the small-sample
  work here. Grey relational degree is kept only as a robustness check on
  the sequencing of the short catalogues (protest from 1980).

## The concrete design

### The model, in one equation

For kind (dimension) k at place p, scale s and origin t, the node's linear
predictor is today's lattice node plus a relation term:

    eta[k,p,s,t] = o[k,p,s,t]                       offset: named, or the coarser scale carried down
                 + x[k,p,s,t]' b[k,s,t]              its own gauges and flows (the lattice as now)
                 + sum over j in Pa(k) of
                     g[kj,s,t] z(f[j,p,s,t-])         the parent's state: its linear predictor at its
                                                      latest origin before t, standardized
                   + d[kj,s,t] e[j,p,t]               the parent's recent onsets, as an excursion
                                                      above the place's own rate of that kind
                   + h[kj,s,t] z(f[j,p,s,t-]) m[p,t]  the same state through a moderator (exposure,
                                                      executive constraints)
                 + c[k,s,t]' strain(cross tensions)   one dimension's stress ahead of another's capacity

Every coefficient is a dynamic state (augur.dynamic: discounted, linear-
Bayes updates). Dynamic model averaging runs over groups as now, plus one
group per parent kind, so the weight on "coups read banking" through time
is the model's own estimate of that relation.

### Four choices that make it tractable

1. **Parents are read lagged, never at the same origin.** Every relation
   input comes from the parent's latest forecast issued before t. Within
   a time step the kinds are then independent: they can run in any order,
   in parallel, and the graph may have cycles (coups and conflicts lead
   each other; banking and currency crises mutate both ways) with no
   simultaneity to untangle. This is the dynamic dependence network's
   form without its contemporaneous (Cholesky) part; what that part
   would add, the same-quarter co-movement of kinds, the recoupling in 4
   supplies by simulation. The smoke check that every read is from
   before the origin already covers it.
2. **What one dimension reads of another is its state, not its label.**
   f and q, the parent node's linear predictor and its variance (now in
   every book entry, from augur.dynamic/predictive), carry the parent's
   whole stress: its gauges, flows and contradictions, already weighted by
   what the parent learned. Onsets enter as well but through kernels, as
   excursions: e[j,p,t] = sum over known onsets of j at p of
   exp(-(t - t_i)/tau) minus the place's long-run rate times tau, at tau
   of one and five years. Without the subtraction the kernel only
   re-learns the level (bd memory: the branching ratio is not identified
   from the level).
3. **The graph is sparse and set before training.** Candidate edges j to k
   come from the sequencing step (pre-2003 lead-lag tables and lagged
   conditional-independence tests given k's own past) and from theory.
   An edge with support gets a prior coefficient of theory's sign (mean
   0.2, variance 0.1, as the gauges' priors); an edge without either is
   left out, not given a flat prior, so a dozen kinds do not mean a
   hundred and more noisy coefficients. Model averaging then weighs the
   parents it was given.
4. **The world model is a forward simulation.** No joint fitting: once
   every node has learned to t, draw each kind's linear predictor from
   N(f, q), its window's event from that, and pass the draws to the
   children's relation inputs for the next step, over the horizon, S
   times. The draws give joint and conditional forecasts (a banking
   crisis then a coup, a currency crisis given a sovereign default) and
   scenarios (set a parent's event and see what follows), coherent with
   every node's own forecast because they are made of them. This is
   Berry & West's top-down sampling run along the dimension graph.

### Where it lives

- **bnm.lattice**: node keys gain the kind, [scale place] to [kind scale
  place] in the book, the models and the tensions; a spec gains
  :kinds {kind kind-spec}, each kind-spec today's spec less what the kinds
  share (:scales, :levels, :parent, :start, :end); one more input group,
  :relations, assembled in `inputs` from the book; the run loop's inner
  step maps over kinds (in parallel: their inputs are all from before t).
  A one-kind spec runs as now.
- **bnm.relations** (new, src): the pure pieces, each tested: the excursion
  kernel, the parent-state reads with their read records, the cross-
  tension pairs across kinds, the moderated terms.
- **bnm.world** (new, src): the forward simulation over the learned nodes,
  joint and conditional probabilities, scenarios.
- **tools**: bnm.lattice-banking and bnm.lattice-political become kind-spec
  builders; a new bnm.economic-events defines the economic kinds;
  bnm.world-run composes them, writes every kind's forecasts, the
  relation weights through time and the simulated joint forecasts;
  bnm.sequencing writes the lead-lag tables and the candidate graph.

### The economic kinds

Defined by rules fixed in advance, on data already on disk, annual unless
marked:

| kind | rule | source, coverage |
|---|---|---|
| currency_crisis | Laeven & Valencia's: depreciation >= 30%, 10 points above the year before, once in five years | WDI exchange rates, 1960- |
| inflation_crisis | consumer prices up 40% or more in the year (Reinhart & Rogoff's high-inflation threshold, raised past the 20% that the 1970s cross everywhere) | WDI, BIS monthly where present |
| growth_collapse | real GDP per head growth more than two standard deviations below the country's own previous twenty years | Maddison, PWT, WDI |
| sovereign_default | default or restructuring | Laeven & Valencia 1970-2017, Reinhart & Rogoff 1800-2016; unknown after, so this kind is a parent only until 2017 |
| food_price_shock | food inflation over three times its own five-year mean (quarterly) | FAOSTAT 2000-, WDI before |
| market_crash | as now, a 30% fall from the 24-month high (monthly) | existing |

Austerity, the strongest economic-to-unrest link in the literature
(Ponticelli & Voth), needs primary balances for every country; the
Macrohistory panel has them for 18 and the IMF's public finances dataset
would extend it: a later download.

### What the first concrete step produces

`bnm.sequencing`, on onsets before 2003 only: for every ordered pair of
kinds (the six we forecast plus the economic kinds), the rate of j's
onsets at each year lag -10 to +3 around k's onsets against the calm, in
calm deviations (the anatomy's measure), and a lagged test of j's past
onsets on k's onsets given k's own past (a panel logit with the
excursion kernels; the PCMCI idea in our setting). The output is the
candidate graph, edges with signs and lags, written to
data/runs/sequencing/ and docs/SEQUENCING.md, reviewed before any model
reads it.

### What the Chinese approaches add to the design

1. **Tiers** (wuli, shili, renli): each kind is assigned to a tier; the
   graph's candidate edges default to base to institutions to actors, with
   feedback edges admitted only with sequencing support.
2. **A hypothesis registry** (meta-synthesis): docs/RELATIONS.md and a
   data file listing every edge, its source, prior and, after each run,
   its weight path; edges that never earn weight are reported, not
   silently kept.
3. **Coupling across dimensions**: the number of dimensions above their
   own calm at a place, and their pairwise co-stress, as inputs (the
   polycrisis term).
4. **The principal contradiction**: per place and origin, the dominant
   contribution among tensions and relations, its identity, and whether
   it shifted in the last year; written out for every place.
5. **Connectedness and parallel running**: the world simulation reports
   each dimension's share of the others' forecast variance by period, and
   its divergence from outcomes is fed back.
6. **Fast-scale event graph and perception**: from the event streams,
   which event types follow which (candidate fast edges) and their tone.

## Why the links cost in trial 42, and trial 43's design

Trial 42's relations made the year forecasts worse (docs/PHASE-CHANGES.md).
A review of the path from one kind's book to another's forecast, and a
diagnostic on the design years (bnm.relation-diag, origins 1960-2002),
found three bugs and three design faults.

Bugs, each fixed with a test:
- the onset excursion subtracted the place's rate over the whole kernel,
  ignoring that only onsets known by t - lag enter it: the level was
  overstated by a factor exp(lag/tau), 2.7 for the year kernel at a
  year's lag, so a place prone to the parent read as calm, and the
  excursion carried the place's rate with the wrong sign;
- the coupling's top fifth was cut across every place in a book, regions
  and the world among the countries;
- the artificial world ran onsets through without the lag before they are
  known, faster than the learned coefficients could carry them.

Design faults, shown by the diagnostic: a logit of the child's outcome
with the child's own forecast (without relations) as its offset, on one
candidate input at a time, and the same input read from another country
(a placebo):
- the parent's level gains as much from the placebo as from the country
  itself (currency to inflation crises: likelihood ratio 202 real, 265
  placebo; banking crises to growth collapses 77 and 103): what it carries
  is the period and the place's lasting risk, which the child's model
  already holds. Often its sign is against the edge's, so trial 42's prior
  pulled it the wrong way;
- four inputs per parent, and the coupling, in a model of some sixty;
- the links entered the all-inputs model, and their own groups (the
  intercept and the link, without the node's gauges) could never win
  weight: a link was never weighed as the node with it against the node
  without it.

What does carry past the child's forecast, well above its placebo, is a
parent's onsets known in the last three years: the post-crisis regime of
Bussiere & Fratzscher (2006). Protest surges to coups (LR 9.5, placebo
0.9), violence surges to war onsets (23.0 and 2.5), sovereign defaults to
inflation crises (13.1 and 0.7), and five more edges; for banking to
currency crises it is the parent destabilized against its own past (27.3
and 5.5).

The literature agrees. Decouple/recouple models (Zhao, Xie & West 2016;
Gruber & West 2016) give each series its full own model and add a few
parents as extra states; under model averaging the comparison must be
nested, the base against the base with the link (Clark & West 2007;
Lavine, Lindon & West 2021, who choose predictors on the forecasts they
make); a cross-variable level double-counts the unit's own effect (Mundlak
1978), so what passes should be within the unit; excitation estimated
against a fixed baseline absorbs heterogeneity (Filimonov & Sornette
2015), which the placebo checks; and other crises help mostly as a regime
after one (Bussiere & Fratzscher 2006; Reinhart & Rogoff 2011), with gains
small beside a series' own history (Mueller & Rauh 2022).

Trial 43's design, chosen on the design years:
1. a parent passes one input along each edge, in the edge's form:
   :post3, :post1, :e1, :e5 or :destab (bnm.relations/edge-input), never
   its level; 0 before its catalogue covers the place;
2. the form, and whether the edge is kept, by rule (bnm.relation-diag/
   choose-form): a form qualifies when it gains past the child's forecast
   (LR >= 3.84) in the edge's direction and at least twice its placebo's
   gain; :post3 when it qualifies, else the form that gains most. Twelve
   of the 22 edges are kept (data/runs/sequencing/graph-t43.edn);
3. each link nested in the node's own model (:relation-nest): its model
   reads all the all-inputs model reads and the link, the all-inputs model
   no link, so the averaging adopts a link only where the node forecasts
   better with it. No coupling, no moderator. A kind without a parent is
   then exactly the world without relations, a check on the plumbing.
