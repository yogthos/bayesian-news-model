# Research: multidimensional trends, slow stocks and explosive events

This is a literature review, done in October 2026, to check the plan before extending the model. It extends the model in four ways:
- from six political event channels to economic, market, technology and science trends;
- to the relationships between those trends;
- to the link between slowly building trends ("stocks") and the explosive events they culminate in.

## What is predictable

- **Where and when.** The literature agrees on where explosive events happen. It does not agree on when they start.
  - Every serious early-warning system finds hot spots and high-risk countries well, and new outbreaks poorly (Bazzi et al. 2022 REStat; Hegre et al. 2022, 2025 on the ViEWS competitions; Mueller & Rauh 2018 APSR).
  - Structural variables say where. News-text and event dynamics carry most of the timing.
- **Slow stocks give hazard multipliers over horizons of 1–3 years, not dates.**
  - The best case is financial crises. Credit growth together with asset-price growth over 3 years gives about a 40% chance of a crisis within 3 years, against a 7% base rate (Greenwood, Hanson, Shleifer & Sørensen 2022 JF).
  - Credit growth and the slope of the yield curve dominate. Nonlinear models beat logit (Bluwstein et al. 2023 JIE).
  - Out-of-sample AUC is about 0.7–0.8.
- **Weeks-level timing comes from the fast layer.** That means news intensity, self-excitation and precursor events.
  - EMBERS protest forecasts reached recall 0.82 and precision 0.69, with lead times of 6–12 days (Ramakrishnan et al. 2014 KDD).
  - A surge in war-talk in the news precedes war onset (Chadefaux 2014 JPR).
- **Some timing is intrinsically unpredictable.**
  - Preference falsification hides the thresholds at which revolutions cascade (Kuran 1991; Granovetter 1978).
  - So the forecasts should be calibrated hazards, not alarms.
- **Generic early-warning statistics are weak in social and financial systems.**
  - Rising autocorrelation and variance (critical slowing down) give many false positives.
  - Choosing past cases after the fact makes them look better than they are (the prosecutor's fallacy): Boettiger & Hastings 2012, 2013.
  - Diks, Hommes & Wang 2019 found warning signals only before the 1987 crash.
  - Use them as features whose weight is learned, never as alarms.
- **The Hawkes branching ratio as a "distance to criticality" gauge is fragile.** Estimates are dominated by how the non-stationary baseline is handled (Filimonov & Sornette 2012 vs Hardiman, Bercot & Bouchaud 2013 vs Wheatley, Wehrli & Sornette 2019). The baseline must be flexible and driven by covariates. Otherwise slow trends masquerade as excitation and look like spurious near-criticality.

## Many interrelated series

- **The core model.** The best-supported model for 50–300 macro and financial series is a Bayesian VAR with Minnesota-type adaptive hierarchical shrinkage. Chan 2021 (IJF) gives a normal-gamma variant with Gamma mixing.
  - Supporting work: Bańbura, Giannone & Reichlin 2010; Giannone, Lenza & Primiceri 2015.
  - Deep learning and time-series foundation models have not credibly beaten it on macro data. Their margins are 1–3%, in preprints with possible pretraining leakage.
  - Combinations beat single models (M4, M6; Wang et al. 2023 IJF).
- **Factors plus sparse links.** Macro information is spread across many weak predictors (Giannone, Lenza & Primiceri 2021, "illusion of sparsity").
  - A few common factors absorb the shared movement. A sparse VAR on what remains gives a stable, interpretable graph (FarmPredict: Fan, Masini & Medeiros 2023; NETS: Barigozzi & Brownlees 2019).
  - A sparse VAR on the raw series gives unstable graphs.
  - Forecast with the dense posterior and display a sparsified summary (Hahn & Carvalho 2015).
- **Streaming estimation is closed-form.** Kalman / recursive least squares with forgetting factors, EWMA volatility, and dynamic model averaging over a grid of hyperparameters (Raftery, Kárný & Ettler 2010; Koop & Korobilis 2012, 2013).
  - It can run equation by equation, which decouples and recouples the system (Gruber & West 2016).
  - Dynamic count models handle count series (Berry & West 2020).
  - None of this needs MCMC.
- **Pooling across countries is the most important design choice** for short annual panels. Country coefficients shrink toward the subregion, then the region, then the world (Canova & Ciccarelli 2013). Foreign effects enter through trade- or adjacency-weighted aggregates (the GVAR's "star" variables; network autoregression, Zhu et al. 2017).
- **Mixed frequency.**
  - Use a quarterly base.
  - Treat annual series as latent quarterly states under an aggregation constraint (Mariano & Murasawa 2003; Koop, McIntyre, Mitchell & Poon 2020).
  - Summarize daily market data per period: returns, realized volatility, drawdown.
- **Slow effects.** Use 1-, 3-, 5- and 10-year moving averages of the drivers, with their own slower forgetting. A single forgetting rate for everything forgets decade-scale effects.
- **The Hawkes bridge.**
  - A Hawkes excitation matrix is a Granger graph (Eichler, Dahlhaus & Dueck 2017).
  - A discretized Hawkes process is an INAR(∞) count autoregression (Kirchner 2016).
  - Coupling Hawkes processes with continuous series exists only in theory preprints, so that part is new.

## Linking stocks to explosive events

No published model combines all of the following, though each component exists:
- a latent-stock state space;
- a baseline driven by covariates;
- excitation that depends on the state;
- a geographic hierarchy.

Templates:
- state-dependent Hawkes (Morariu-Patrichi & Pakkanen 2022);
- the dynamic contagion process (Dassios & Zhao 2011);
- Markov-switching Hawkes;
- threshold and smooth-transition VARs;
- Bayesian online changepoint detection (Adams & MacKay 2007);
- system dynamics written as a stochastic state space (Andrade & Duggan 2021).

Recommended form:

    stock:      S[t+1] = S[t] + B·flows[t] − D·S[t] + Γ·g(events[t]) + noise
    baseline:   log μ(t) = βᵀS(t) + flexible level
    excitation: α(t) = α₀ · exp(γᵀS(t))
    branching ratio n(t) = ρ(α(S(t))) is an output, with a posterior

- **Link stocks to the baseline first.** Add the link to excitation only once the baseline link shows out-of-sample gain on onsets.
- **Model interactions** such as credit × asset prices and factional regime × neighbouring conflict.
- **Add an exhaustion stock for mobilization** (Tarrow's protest cycles), so waves end.
- **Prefer a saturating nonlinear link to a hard cap on excitation.** This captures complex contagion (Centola & Macy 2007).
- **Feed events back into the stocks.** For example, repression builds grievance (Bahid et al. 2024 on Euromaidan).
- **Define adjacency by political affinity and trade,** not only by distance.

## Relationships named as goals: what the evidence says

| Relationship | Evidence | Lag |
|---|---|---|
| Credit boom (+ asset prices) → crisis, deeper recession | strong (Schularick & Taylor 2012; Jordà, Schularick & Taylor 2013; Greenwood et al. 2022) | 1–5 y |
| Household debt run-up → lower growth | strong (Mian, Sufi & Verner 2017) | 3 y |
| Patents/breakthroughs → firm value | strong (Kogan et al. 2017) | days |
| Capital-inflow surge → manufacturing shrinks, then bust | medium (Benigno, Converse & Fornaro 2015) | 1–4 y |
| Targeted industrial policy → targeted sector output and exports | medium–strong, sector-level, little effect on productivity (Lane 2025; Criscuolo et al. 2019; Juhász, Lane & Rodrik 2024) | 3–10+ y |
| Public R&D → productivity | plausible, too slow to learn; keep it as a prior (Fieldhouse & Mertens 2025) | 8–15 y |
| Private credit > ~100% of GDP → lower growth | contested (Arcand, Berkes & Panizza 2015 vs meta-analyses) | — |
| Financialization → deindustrialization | weakest: theory and firm-level correlation (Krippner 2005; Tori & Onaran). Rodrik 2016 attributes premature deindustrialization to globalization and technology | — |

A strong, sign-stable financialization → deindustrialization edge learned from annual data should be read as a sign of confounding, not as a discovery. Possible confounders are the China shock, globalization and the shift to services. Common global drivers must be modeled explicitly.

## Discovering relationships

- **Online model:** sparse coupling with a soft inclusion prior per edge.
- **Offline step:** PCMCI+ / J-PCMCI+ (Runge et al. 2019; Günther, Ninad & Runge 2023), with countries as datasets, proposes candidate edges.
- **Avoid at annual frequency:** convergent cross mapping (it fails under common forcing), transfer entropy, neural Granger, and DYNOTEARS (which exploits differences in variance; Reisach et al. 2021).
- **Annual data invents same-year links.** Aggregating to annual data creates spurious contemporaneous links (Breitung & Swanson 2002). So fast edges are learned from fast data and slow edges from slow data.
- **Priors per edge combine three sources:**
  - literature claim graphs (Garg & Fetzer 2025, about 45k economics papers, with each edge tagged by how well it is identified);
  - LLM judgements of direction, sign and lag;
  - a curated list from theory.

  The reliability of each source is learned. LLMs repeat their training text (Zečević et al. 2023), so priors are soft and never fix an edge.
- **Validate policy effects offline** with panel local projections and LP-DiD (Jordà 2005; Dube, Girardi, Jordà & Taylor 2023).

## Role of LLMs

- **Coding and measurement:** strong evidence (Chen, Kelly & Xiu; Lopez-Lira & Tang; Garg & Fetzer).
- **Causal priors:** plausible, so use them as soft priors.
- **Direct forecasting:** still behind top humans on live benchmarks (ForecastBench, Metaculus). An LLM can be at most one member of an ensemble, weighted by its live record.
- **Time-series engine:** no (Tan et al. 2024).
- **Backtests must be point-in-time.** LLMs memorize history (Lopez-Lira, Tang & Zhu 2025; Paleka et al. 2026). Re-coding past news with a newer LLM, or using LLM priors drawn from after the split date, leaks the future into the backtest.

## Technology and science

- **Technology costs and performance are the most forecastable trends.** The model is a correlated geometric random walk with drift (Wright's / Moore's law), validated by hindcasts on 50+ technologies (Nagy et al. 2013; Farmer & Lafond 2016; Way et al. 2022).
- **Science:** link prediction on concept graphs with hand-crafted network features (Krenn et al. 2023 Nat. Mach. Intell.). It gives a leading indicator of research attention, not of outcomes.

## Honest evaluation

- **Report results by stratum,** never only a pooled AUC:
  - onset: nothing above threshold in the prior 6 or 12 months;
  - escalation from a low level;
  - continuation.
- **Required benchmarks:**
  - all zero;
  - last value;
  - a 12-month historical bootstrap;
  - recurrence (Gastinger et al. 2024);
  - a structural-only logit;
  - a pure Hawkes model without stocks. The stocks must beat this one on onsets.
- **Metrics:**
  - CRPS and log score;
  - Brier skill against the base rate;
  - PR-AUC for rare onsets;
  - reliability diagrams;
  - precision at top-k;
  - recall at a fixed false-alarm budget;
  - recall conditional on lead time.
- **Test warning signals against matched controls,** and check calibration in the tails.
- **Data must be point-in-time** (vintages, publication lags). Live forecasts are logged append-only.

## Data (free)

- **Credit, macro and crises:**
  - BIS total credit, quarterly, 44 economies;
  - JST Macrohistory, 18 economies, 1870–2020, with crisis dates;
  - Laeven–Valencia crisis dates;
  - World Bank WDI API, 1960–;
  - Penn World Table 11.
- **Industry structure:** GGDC ETD (sectoral value added and employment, 51 economies); UNIDO INDSTAT; OECD STAN.
- **Policy:** Global Trade Alert and NIPO for industrial policy (2008–); V-Dem for political regime.
- **News-based indices:** GPR and EPU.
- **Prices:** the FAO food price index.
- **Science and patents:** the OpenAlex snapshot; USPTO Open Data Portal patents (formerly PatentsView, key required since March 2026); KPSS patent values; the Kelly et al. breakthrough index.
- **Markets:** FRED and Stooq.
- **Literature priors:** Garg–Fetzer claim graphs.
