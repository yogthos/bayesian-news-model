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
