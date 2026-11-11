# Weighting and combining signals for transition forecasting (bnm-56c.2)

Research only, 11 November 2026. Covers what the notes in bnm-56c.2 and the "Ranking signals" section (docs/PHASE-CHANGES.md, commit e79d91a on origin/structure) did not. Where a claim rests only on an abstract or catalogue record, it is marked [abstract].

Correction to the bead's notes: arXiv 1803.01984 is Johnson & West, "Bayesian predictive synthesis with outcome-dependent pools". McAlinn & West's dynamic BPS (J. Econometrics 2019) is arXiv 1601.07463. Both are cited below.

## 0. Diagnosis in one paragraph

(A) failed on food price shocks for a structural reason, not because of tuning. A model's gain over the base is mostly information every model shares. The forecasting literature has a name for this: shared versus private information (Palley & Soll; Satopää et al.; Clemen & Winkler). When the weights are proportional to shared gain they come out even. A slightly better model can then override only if its weight comes from what it adds beyond the models already in the forecast. That is a sequential, encompassing quantity, not a gain over the base. Shapley weights do not fix this. Their symmetry axiom splits shared credit equally among models that carry it, so they would reproduce (A)'s even weights. The user's direction, that the weight should be a stock of evidence conditional on state, is supported by three lines of work: specialist ("sleeping") experts from online learning, regime-dependent and generalised density pools in econometrics, and the early-warning practice of thresholding. The support is mostly indirect for rare binary events. Section 6 says where.

## 1. Weighting by what a model adds beyond the others

**Shared versus private information.**
- Satopää, Pemantle & Ungar (JASA 2016), "Modeling probability forecasts via information diversity", https://arxiv.org/abs/1406.2148. Each forecaster sees part of a common pool of information. The right aggregate depends on how much the parts overlap: with full overlap you average, and with disjoint parts you add the log odds, which extremizes the result. Extremizing a plain average is the special case. The general form is the "partial information framework", https://arxiv.org/abs/1505.06472.
  - For the lattice, the base is the shared part, and each increment `logit p_m - logit p_0` mixes shared and private parts. Adding the increments double-counts the shared part, while proportional weights spread it evenly. Neither keeps only the private part.
- Palley & Soll (Management Science 2019), "Extracting the wisdom of crowds when information is shared", https://pubsonline.informs.org/doi/10.1287/mnsc.2018.3047. Their "pivoting" method splits each forecast into a shared component and a private one, then recombines them. It works with few judges. The follow-up on peer-prediction weights is https://ideas.repec.org/a/inm/ormnsc/v69y2023i9p5128-5146.html.
- Clemen & Winkler (Operations Research 1985), https://econpapers.repec.org/RePEc:inm:oropre:v:33:y:1985:i:2:p:427-442. Positively dependent sources are worth only a few independent ones. Adding correlated sources gives diminishing value that levels off well short of perfect information. This explains why nine to twenty nearly collinear group models carry little more than two or three [abstract].
- Baron, Mellers, Tetlock, Stone & Ungar (Decision Analysis 2014), "Two reasons to make aggregated probability forecasts more extreme", https://pubsonline.informs.org/doi/10.1287/deca.2014.0293.

**Encompassing, used online.**
- Clements & Harvey (J. Applied Econometrics 2010), "Forecast encompassing tests and probability forecasts", https://econpapers.repec.org/RePEc:jae:japmet:v:25:y:2010:i:6:p:1028-1062. A working paper version is at https://ageconsearch.umn.edu/record/269744/files/twerp_774.pdf.
  - They give encompassing tests for probability forecasts under both the quadratic and the log score, including a logistic regression of the outcome on two forecasts' logits.
  - They check small numbers of forecasts by simulation and apply the tests to recession-probability logits [abstract].
  - The test is pairwise: does B add to A? It has one or two parameters, which is why it survives few events where a twenty-regressor synthesis does not.
- Giacomini & White (Econometrica 2006), "Tests of conditional predictive ability", https://econpapers.repec.org/RePEc:ecm:emetrp:v:74:y:2006:i:6:p:1545-1578. Working paper: http://fmwww.bc.edu/EC-P/wp572.pdf.
  - They test whether one method beats another conditional on information at the forecast origin, and give a two-step rule that picks the method according to that information.
  - This is the formal version of "this signal is better when buffers are low".

**Stacking and optimal pools under redundancy.** Yao et al., Geweke–Amisano and Del Negro et al. are already in the notes. Additions:
- Breiman's stacked regressions (Machine Learning 1996) and LeBlanc & Tibshirani (1996): unconstrained stacking fails under collinearity, and constraining weights to be non-negative is what stabilises it.
  - This is the likely mechanism behind (C)'s fragility. (C) was a free regression on nine to twenty collinear logits.
  - No URL was verified for these two, so they are cited by name only.
- Diebold & Shin (IJF 2019), "partially-egalitarian LASSO", https://nber.org/system/files/working_papers/w24967/w24967.pdf.
  - Select a few forecasters, then shrink toward equal weights among the survivors. Ex post, the best rule is "discard most, average the rest".
  - With correlated forecasters, heavy trimming still holds according to an Erasmus thesis (https://thesis.eur.nl/pub/53642/Combining_Professional_Forecasts_A_Closer_Look_At_Partially-egalitarian_Lasso.pdf). That is a student thesis, not peer reviewed.

**Shapley or marginal-contribution weights.**
- Franses (J. Forecasting 2024), "Shapley-value-based forecast combination", https://pure.eur.nl/en/publications/shapley-value-based-forecast-combination/. For a stylised case, Shapley weights beat equal weights [abstract].
- Kozyrev links forecast combination to Shapley decomposition: https://econometricsociety.org/event_papers/download/278/367/1/Kozyrev_SVD_RS.pdf.
- Caveat for this lattice: Shapley's symmetry axiom gives two models that carry the same information the same credit. On food price shocks, where every model gains alike, Shapley weights would be about as even as (A)'s. An ordered (sequential) attribution is what lets the stronger model take the shared part and leaves the others only their private increments. Section 7, design 1, uses that.

**BPS with dependence among agents.**
- McAlinn & West, dynamic BPS, https://arxiv.org/abs/1601.07463. Dependence enters through the time-varying coefficients of a latent-factor synthesis.
- The multivariate version is McAlinn, Aastveit, Nakajima & West, https://arxiv.org/abs/1711.01667.
- Johnson & West, outcome-dependent pools, https://arxiv.org/abs/1803.01984: the pool weights depend on where the outcome falls, which is relevant to section 2.
- For a binary outcome, BPS's synthesis function is a logistic of the agents' logits, which is what (C) built. Dependence among agents shows up as near-singular coefficient posteriors. With few events this cannot be estimated without strong shrinkage or a lower-dimensional (ordered or orthogonalised) design.
- Copula pooling of dependent experts (Jouini & Clemen 1996) is reviewed in Wang, Hyndman, Li & Kang (IJF 2023), https://arxiv.org/abs/2205.04216.

**Which of these work with few events?** Ranked by how few parameters each estimates from the outcomes:
1. pairwise encompassing, or an ordered gate per model;
2. trimming followed by equal weights;
3. regularised stacking, peLASSO;
4. full optimal pools or BPS.

Several sources make the point:
- Claeskens et al. (2016) and Frazier et al. (2023) show estimated weights add variance and bias (section 4).
- Vahey & Nicolaisen (RBA 2007) found that convex pools of recession-probability models, weighted by recursive log scores, beat their components: https://www.rba.gov.au/publications/workshops/research/2007/vahey-nicolaisen.pdf.
- Lahiri & Yang propose a nonlinear combination for binary outcomes: https://www.cesifo.org/DocDL/cesifo1_wp5175.pdf.
- No source was found that tests estimated logistic combination weights on very few events.

## 2. Weights that depend on state or regime

**Weights that depend on covariates, a region, or a regime.**
- Kapetanios, Mitchell, Price & Fawcett (J. Econometrics 2015), "Generalised density forecast combinations", https://econpapers.repec.org/RePEc:eee:econom:v:188:y:2015:i:1:p:150-165. PDF: https://www.bankofengland.co.uk/-/media/boe/files/working-paper/2014/generalised-density-forecast-combinations.pdf.
  - The pool weights are piecewise-linear functions of the region of the outcome. They beat linear pools in theory, Monte Carlo and data [abstract].
- Elliott & Timmermann (IER 2005), "Optimal forecast combination under regime switching", https://ideas.repec.org/a/ier/iecrev/v46y2005i4p1081-1102.html.
  - Weights vary with a latent Markov state. Time-varying weights are optimal when the target and the predictors share a regime-switching common factor [abstract].
- Waggoner & Zha (J. Econometrics 2012), "Confronting model misspecification in macroeconomics", https://www.nber.org/papers/w17791.pdf.
  - A Markov-switching mixture of a DSGE and a BVAR with regime-dependent weights. It dominates both models, and the DSGE matters only in the late 1970s and early 1980s, a stress episode.
- Billio, Casarin, Ravazzolo & van Dijk (J. Econometrics 2013), "Time-varying combinations of predictive densities using nonlinear filtering", https://repub.eur.nl/pub/38198/2012-118.pdf.
  - The weights are a latent state that past predictive performance updates through a filter. In their words, the score history is the weights' state equation.
  - They find model incompleteness largest in the 1970s and in the 2008 crisis. Information on the whole predictive distribution "is very important, above all during turbulent times".
- Del Negro, Hasegawa & Schorfheide (2016) is already in the notes. It is the binary-pool special case with a latent AR weight.
- Aiolfi & Timmermann (J. Econometrics 2006), "Persistence in forecasting performance and conditional combination strategies", https://ideas.repec.org/a/eee/econom/v135y2006i1-2p31-53.html.
  - Cluster the models by past performance, pool within clusters, and shrink toward equal weights. Top and bottom performers persist.
  - A Journal of Risk replication on financial returns found no persistence at the top: https://www.risk.net/journal-of-risk/technical-paper/2463259/relative-performance-persistence-of-financial-forecasting-models-and-its-economic-implications.
- Gibbs & Vasnev (IJF 2024), "Conditionally optimal weights and forward-looking approaches to combining forecasts", https://ideas.repec.org/p/swe/wpaper/2017-10.html.
  - The weights minimise conditional mean squared error and track expected future performance rather than the past average. One replication did not hold up (https://thesis.eur.nl/pub/50341/Wal-van-der_380344.pdf).
- Yao et al.'s hierarchical stacking (already in the notes, arXiv 2101.08954) gives input-dependent stacking weights pooled across input cells. It is the closest Bayesian match to "weights by state bin, shrunk toward the pooled weight".

**Hidden states whose transitions depend on observables.**
- Filardo (JBES 1994), "Business-cycle phases and their transitional dynamics", https://ideas.repec.org/a/bes/jnlbes/v12y1994i3p299-308.html.
  - Markov switching with time-varying transition probabilities: a logistic in leading indicators. Many of those indicators help predict turning points [abstract].
  - This is the standard way to let a stock (buffer depletion) move the regime probability.

**Specialists: evidence counted only where a model speaks.**
- Freund, Schapire, Singer & Warmuth (STOC 1997), "Using and combining predictors that specialize", https://mwarmuth.bitbucket.io/pubs/C43.pdf.
  - A specialist is "awake" only on some rounds. Its weight is updated only on those rounds, so quiet rounds neither dilute nor penalise it.
- Koolen, Adamskiy & Warmuth (NIPS 2012), "Putting Bayes to sleep", https://proceedings.nips.cc/paper_files/paper/2012/file/43ec517d68b6edd3015b3edc9a11367b-Paper.pdf.
  - Gives specialists a Bayesian reading: a sparse composite model in which only a few models are relevant in each segment.
- Herbster & Warmuth (Machine Learning 1998), "Tracking the best expert" (fixed share), https://mlanthology.org/mlj/1998/herbster1998mlj-tracking.
  - Each round, every weight shares a small fraction with the others. A model's weight therefore never falls to zero and can recover quickly after a regime change. That is the "collapse onto one model" remedy, with a bound.
- This is the mechanism that matches a signal that is quiet until it is decisive. The model's evidence stock accumulates only on rows in its state, such as depleted buffers.

**Does it help for rare events or crises?** The evidence is real but indirect:
- Billio et al.: gains concentrate in turbulent periods.
- Waggoner & Zha: the second model matters only in a stress episode.
- Opschoor et al. (section 3): weights fitted to the tail beat other weightings on downside risk.
- Holopainen & Sarlin (Quantitative Finance 2017), "Toward robust early-warning models: a horse race, ensembles and model uncertainty", https://ecb.europa.eu/pub/pdf/scpwps/ecbwp1900.hr.pdf. Ensembles beat single early-warning models for European banking crises.
- Filardo's time-varying transition probabilities help predict recession turning points.

No source was found that tests state-gated combination weights on rare binary onsets across many places. The lattice would be the first test of its kind, so it needs a planted test (section 7).

## 3. Why pooled average scores hide signals that matter near transitions

**Weighted scoring rules.**
- Gneiting & Ranjan (JBES 2011), "Comparing density forecasts using threshold- and quantile-weighted scoring rules", https://econpapers.repec.org/RePEc:taf:jnlbes:v:29:y:2011:i:3:p:411-422.
  - Weighting by threshold or region keeps the score proper, while some weighted-likelihood tests can be hedged.
- Diks, Panchenko & van Dijk (J. Econometrics 2011), "Likelihood-based scoring rules for comparing density forecasts in tails", https://repub.eur.nl/pub/13975/2008-0504.pdf.
  - The censored likelihood is proper and has the most power for a region of interest.
- Lerch, Thorarinsdottir, Ravazzolo & Gneiting (Statistical Science 2017), "Forecaster's dilemma: extreme events and forecast evaluation", https://arxiv.org/abs/1512.09244.
  - Evaluating only the rows where an extreme happened is improper and rewards alarmism. Weight by thresholds, or by information known at the origin, instead.
  - For the lattice: weighting rows by a buffer-depletion index known at the origin keeps the score proper, while weighting by whether an onset followed does not.
- Opschoor, van Dijk & van der Wel (JAE 2017), "Combining density forecasts using focused scoring rules", https://repub.eur.nl/pub/100109/REPUB_100109-OA.pdf.
  - Pool weights fitted to the censored likelihood beat equal weights and log-score or CRPS weights on downside risk.
  - This is direct evidence that the scoring rule used to fit the weights should focus on the region that matters.

**Brier as one choice of emphasis.**
- For a binary event, every proper score is a mixture of elementary cost-loss scores over the decision threshold θ. Brier weights θ uniformly on (0, 1), and the log score weights it by 1/(θ(1−θ)).
  - Buja, Stuetzle & Shen, "Loss functions for binary class probability estimation and classification", https://sites.stat.washington.edu/wxs/Learning-papers/paper-proper-scoring.pdf.
  - Ehm, Gneiting, Jordan & Krüger (JRSSB 2016), Murphy diagrams, https://arxiv.org/abs/1503.08195.
- With base rates of a few percent, the decisions that matter sit at θ in roughly 0.02–0.3. Brier spends most of its weight on θ > 0.3, where nothing in this problem is decided.
- When neither forecast dominates on every θ, different proper scores rank them differently. A Murphy diagram shows that directly.
- Early-warning evaluation makes the same point from the policy side:
  - Sarlin (Economics Letters 2013), https://econpapers.repec.org/RePEc:eee:ecolet:v:119:y:2013:i:1:p:1-7, with the Alessi–Detken comment at https://econpapers.repec.org/RePEc:eee:ecolet:v:124:y:2014:i:3:p:338-340;
  - Alessi & Detken (EJPE 2011), https://ECB.eu/pub/pdf/scpwps/ecbwp1039.pdf.
  - Both make the trade-off between missed crises and false alarms explicit, and the results depend on the preference parameter.

**Pooling across places.**
- Hamill & Juras (QJRMS 2006), "Measuring forecast skill: is it real skill or is it the varying climatology?", https://webarchive.library.unt.edu/web/20090115024627/http:/www.cdc.noaa.gov/people/tom.hamill/skill_overforecast_QJ_v2.pdf.
  - Pooling places with different base rates inflates skill (a Simpson's-paradox effect). They recommend computing skill within strata of similar climatology.
  - A pooled Brier across 13 kinds and many countries mostly rewards getting the base rate right, which the base already does.

**Is our acceptance rule biased against transition signals?** It is proper, so in expectation it does not prefer a wrong forecast. Its emphasis and its tests work against a signal that is quiet until it is decisive, in four ways.
1. **The noise rule is an absolute bound on the pooled mean.** "Within 0.0003 of zero, pooled" lets a noise group cost up to 0.0003·N/N_s in a state stratum holding a share N_s/N of the rows. If depleted-buffer rows are 10% of the rows, that is 0.003 there. So the rule is lax exactly where transitions happen. A quiet signal's real gain is diluted by the same factor, so a pooled magnitude rule cannot tell a real one from noise either.
2. **Brier's uniform θ weighting** puts little weight on the low thresholds where rare-event decisions are made (above).
3. **"No fold lost" on 3-year folds tests one draw of the regime.** A transition signal will sometimes raise risk where the buffer does not run dry. Boettiger & Hastings show such false positives are intrinsic to early warning:
   - "Quantifying limits to detection of early warning for critical transitions", https://arxiv.org/abs/1204.6231;
   - "Early warning signals and the prosecutor's fallacy", https://arxiv.org/abs/1210.1204.
   With few events per fold, a fold loss is weak evidence, so the rule as written is lopsided against such signals.
4. **One mitigating point.** In a paired test, rows where the candidate equals the comparison contribute zero difference and no variance, so dilution by itself does not cut the test's power. It does once a combination moves its weights everywhere, as all of ours do. Gating the signal off in calm states (designs 1 and 4) keeps those differences near zero and keeps the power.

## 4. The forecast combination puzzle and its fixes

- Smith & Wallis (OBES 2009) showed by simulation that the error in estimating the weights can be large, which explains why simple averages win. Claeskens, Magnus, Vasnev & Wang (IJF 2016), https://lirias.kuleuven.be/retrieve/373254, show it in theory:
  - with random weights, the combination is biased even when the inputs are unbiased, and its variance is higher;
  - so "optimal" weights need not beat equal weights.
- Frazier, Covey, Martin & Poskitt (2023), "Solving the forecast combination puzzle", https://arxiv.org/abs/2308.05263.
  - The puzzle comes from two-step estimation, where the models are fitted first and the weights after. Estimating jointly removes it.
  - The lattice already learns its models online, so the cleanest version here is to judge every weight on predictive scores from before the outcome. All our candidates already do this. The remaining variance comes from the few events.
- The remaining fixes:
  - shrink toward equal weights (Aiolfi & Timmermann 2006; Stock & Watson 2004);
  - trim, then average (Diebold & Shin 2019);
  - review: Wang et al. 2023, https://arxiv.org/abs/2205.04216.
- **The trade-off with few events.**
  - Estimation variance grows with the number of free weights and shrinks with the number of informative rows. For a binary target, that is roughly the number of events, not the number of rows.
  - With tens of onsets per kind and node, anything beyond a handful of free weights loses to a fixed rule. This is the puzzle in its sharpest form.
- **What "equal weights" means here.**
  - Equal weights on nested increments double-count the shared part, so it is not the right anchor. The right one is a fixed pool that counts the shared part once: an even log pool of the models' logits, or the strongest model alone.
  - Shrinkage should pull toward that anchor.
  - t48's selection beat (A) in 2022 by a margin, 0.001 nats per row, of the size that the papers above (Smith & Wallis; Claeskens et al.) put inside estimation noise. Don't design to win that fold. Design so that a stronger model can override when its edge is real.

## 5. Early warning: how indicators are combined

**The signals approach.**
- Kaminsky, Lizondo & Reinhart (IMF Staff Papers 1998), https://www.imf.org/external/Pubs/FT/staffp/1998/03-98/kaminsky.htm.
  - Each indicator gets a threshold, a percentile of its own country's history, chosen to minimise its noise-to-signal ratio: false alarms over true signals within a 24-month window.
- Kaminsky (Fed IFDP 629 / IMF WP 99/178), "Currency and banking crises: the early warnings of distress", https://www.federalreserve.gov/econres/ifdp/currency-and-banking-crisis-the-early-warnings-of-distress.htm.
  - Proposes four composite indicators. One counts signals weighted by inverse noise-to-signal ratio, and its crisis probability is read off the historical frequency for each value of the composite.
  - The exact weighting was not verified beyond secondary descriptions.
- Berg & Pattillo (J. International Money and Finance 1999) found that a probit on the same indicators beats the thresholded composite out of sample. Thresholding throws away information. No URL was verified.
- Borio & Lowe (BIS WP 114, 2002), https://www.bis.org/publ/work114.pdf.
  - Credit and asset-price gaps signal far better jointly than alone. This is the joint "two stocks both stretched" signal.
- Drehmann & Juselius (IJF 2014) standardised early-warning evaluation on AUROC under policy requirements. No URL was verified.

**Composite stress indices.**
- Holló, Kremer & Lo Duca (ECB WP 1426, 2012), the composite indicator of systemic stress (CISS), https://ECB.eu/pub/pdf/scpwps/ecbwp1426.pdf.
  - Five sub-indices are aggregated with portfolio-style weights that use their time-varying cross-correlations. The composite rises more when several segments are stressed at once.
  - This is a published way to make the weight on stress signals grow near a transition: co-movement as the amplifier.
- Ensembles of early-warning models: Holopainen & Sarlin (above).
- China:
  - Official practice: the National Bureau of Statistics business-climate monitoring has composite leading, coincident and lagging indices, plus a five-light warning system (red, yellow, green, light blue, blue). Each indicator is scored by threshold band and the scores are summed. This is a counted-signal composite of the Kaminsky–Reinhart kind, of Japanese origin. See https://www.stats.gov.cn/zs/tjws/tjfx/202301/t20230101_1903945.html. The band thresholds were not verified.
  - Academic work builds composites from many indicators (https://econpapers.repec.org/RePEc:bpj:jossai:v:4:y:2016:i:6:p:505-518:n:2).
  - The People's Bank of China built a seven-layer systemic-risk composite (https://camlmac.pbc.gov.cn/en/3935690/3689029/3706788/2025080817543844415/index.html).
  - Recent work combines regime-switching models with a financial stress index (https://ideas.repec.org/a/kap/compec/v66y2025i6d10.1007_s10614-025-10873-9.html).
  - No distinct Chinese method for weighting the combination was found beyond threshold scoring and regime models.
- Soviet and Russian work:
  - The Moscow Conjuncture Institute (Kondratiev, 1920–28) built business barometers. Slutsky's 1927 "The summation of random causes as the source of cyclic processes" showed that smoothed noise produces convincing cycles: https://www.minneapolisfed.org/article/2009/the-meaning-of-slutsky and https://www.cambridge.org/core/services/aop-cambridge-core/content/view/710708C6644552B34E992EF1871B91AD/S1053837221000092a.pdf/beyond-random-causes-harmonic-analysis-of-business-cycles-at-the-moscow-conjuncture-institute.pdf. That is the earliest warning that indicators accumulated over time can manufacture signals, which is why the noise test matters.
  - Malinetskii's Keldysh Institute school (risk theory from synergetics; "channels and jokers": predictable regimes separated by unpredictable transitions), https://mathnet.ru/eng/ipmp1347 and https://spkurdyumov.ru/forecasting/nonlinear-dynamics/. This is conceptual support for regime-specific weights, not an aggregation method.
  - Korotayev and colleagues (HSE) assessed destabilisation-forecasting systems against what happened. The risk indices had near-zero correlation with outcomes in 2010–11 (the Arab Spring), and extrapolation did as well: https://social.hse.ru/data/2023/06/13/2073653762/CSRA_HSE_2023.pdf and https://publications.hse.ru/en/view/223293937. This is a caution about composite instability indices, not a method.
  - Nothing found on how indicators were weighted in these systems.

**Critical slowing down.**
- Scheffer et al. (Nature 2009), https://www.nature.com/articles/nature08227; Dakos et al. (PLoS ONE 2012), https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0041010.
- Composites:
  - Drake & Griffen (Nature 2010), https://www.nature.com/articles/nature09389;
  - Clements & Ozgul (Nature Comms 2016), https://pmc.ncbi.nlm.nih.gov/articles/PMC4820807. They built 127 composites from every combination of seven indicators; adding trait-based indicators helped, and a later preprint reports that weighting the components beat summing them.
- Limits:
  - Boettiger & Hastings (above). Transitions driven by noise have no warning signal: https://arxiv.org/abs/1307.4415.
  - In finance: Diks, Hommes & Wang (Empirical Economics 2019), https://rd.springer.com/content/pdf/10.1007/s00181-018-1527-3.pdf. Critical slowing down is found before 1987 but is mixed or absent for 1997, 2000 and 2008.
- Lesson: slowing-down indicators belong as one gated input, not as the backbone.

## 6. Where the user's state-dependent view is and isn't supported

Supported:
- weights as a state updated by past scores (Billio et al. 2013; Del Negro et al. 2016; fixed share);
- weights that depend on a regime or on a region of the outcome (Elliott & Timmermann; Waggoner & Zha; Kapetanios et al.; Johnson & West);
- evaluation and evidence conditional on information at the origin (Giacomini & White; Gneiting & Ranjan; Lerch et al.);
- evidence counted only where a model is relevant (specialists);
- weights that grow with co-movement of stress (CISS);
- regime probabilities driven by observables (Filardo).

Not supported or untested:
- No source tests state-gated weights on rare binary onsets.
- Time-varying transition parameters (β) estimated from a handful of transitions are unreliable. Every regime model here should fix them in advance or pool them heavily.
- The early-warning literature's own composites are mostly simple thresholds, and Berg & Pattillo show those lose to probits.
- Using accumulated scores as an input to the regime transition is our extension. Nothing found does exactly that. Billio et al. are closest.

## 7. Designs for the lattice, ranked

Notation, at one node and origin t:
- models m = 0 (base), 1..M, with logits ℓ_m;
- per-row log score S(p, y) = y log p + (1−y) log(1−p);
- z_t: a depletion or stress index known at the origin, built from the stock gauges and fixed before any run. Example: the share of a place's buffers past their own 80th percentile of depletion, in the spirit of Kaminsky–Reinhart and Borio–Lowe.
- Soft state memberships π_s(z_t), s ∈ {calm, strained, depleted}, summing to 1. Cut points are pre-registered percentiles.
- λ = the averaging's forgetting factor.

### Design 1 (top): state-conditional evidence stocks for ordered increments (specialist gates)

**Order.** Rank the non-base models by their pooled discounted evidence, defined below, recomputed slowly with hysteresis: swap two models only when the gap exceeds a margin. Write k = 1..M in that order.

**Formula (forward stagewise in log odds, each step gated):**
```
L(0) = ℓ_0
L(k) = L(k-1) + w_k(z_t) · (ℓ_{m_k} − L(k-1))      w_k ∈ [0, 1]
p    = σ(L(M))
w_k(z) = Σ_s π_s(z) · g_{k,s}
g_{k,s} = σ( η · E_{k,s} + log ρ )                  ρ = prior odds, e.g. 1/20
```
The increment of model k is measured against the combination of the stronger models, L(k−1). It is not measured against the base. This is the online encompassing step.

**Online update, after the outcome y_t:**
```
u_{k,t}   = S(σ(L(k-1) + h·(ℓ_{m_k} − L(k-1))), y_t) − S(σ(L(k-1)), y_t)
E_{k,s,t} = λ E_{k,s,t-1} + π_s(z_t) · u_{k,t}
```
- h is a fixed probe step, for example 0.5, so the evidence does not depend on the current weight.
- L(k−1) is the value issued before the outcome.
- E is a discounted log-likelihood ratio: "adding m_k on top of the stronger models" against "not adding it", counted only on the rows in state s. These are the specialist's awake rows.
- Pool E across sibling places (sum their u) before forming g_{k,s}. This gives each state bin enough events. It is the lattice's existing slope pooling applied to evidence.
- Add a fixed-share step: E_{k,s} ← (1−α) E_{k,s}, a small α, so a gate can reopen after a regime change.

**How it treats a quiet-until-decisive signal.**
- In calm rows its increment over the stronger models is near zero, so u ≈ 0. Its calm evidence neither grows nor is penalised, and its gate stays at the prior.
- In depleted rows it carries information on the few event rows. Its stock there grows, and its gate opens only when z_t is in that state.
- The stronger models keep the shared part. This is the override (A) lacked. On food price shocks the all-inputs model ranks first and takes the shared gain. The group models' increments over it are about zero, so their gates stay shut and the forecast follows the all-inputs model. A group with private information still enters.

**How it handles noise.**
- Adding a noise increment has negative expected log-likelihood ratio: the extra variance costs about half the increment's variance per row, so E drifts down and the gate closes.
- With prior odds 1/20 and η < 1 (tempering), the gate starts nearly shut.
- Each gate is its own in-or-out hypothesis. Models do not compete for one unit of mass, so the DMA-style collapse cannot happen, and a noise model cannot flip which model leads.
- Ordering noise is harmless to first order: whichever near-tied model ranks first takes the shared part, and the others are tested only on what they add beyond it.

**What would test it:**
1. The existing noise rule, plus the same rule within the depleted stratum.
2. The existing planted signal.
3. A new planted signal that is quiet until decisive: zero, or pure noise, except when z_t is in its top decile, where it predicts onsets. It should pass at the year nodes, its gate should open only in that stratum, and its pooled gain may be small.
4. Food price shocks 2020–22 against t48 and (A). The gate on the all-inputs model should be near 1 and the others near 0.
5. Brier weighted by state stratum, known at the origin, and a Murphy diagram for θ ≤ 0.3.

**Literature support:**
- specialists (Freund et al.; Koolen et al.);
- fixed share (Herbster & Warmuth);
- encompassing (Clements & Harvey 2010);
- conditional predictive ability (Giacomini & White);
- hierarchical stacking for weights by state, shrunk across bins;
- boosting in log odds (already in the notes).
The full combination, including use on rare binary events, is untested.

### Design 2: a hidden regime with transitions driven by observables, each regime with its own design-1 gates

**Formula:**
```
Two regimes r ∈ {calm, stress}; each has its own gates g_k^{(r)} and logit L^{(r)}.
p_t = Σ_r P(r_t | F_{t-1}) · σ(L^{(r)}_t)
P(calm → stress at t) = σ(a + b·z_t + c·C_t)
P(stress → calm at t) = σ(a' − b'·z_t)
```
- C_t is a CUSUM of the stress-regime increments' evidence: the stock of score history entering the transition probability, as the user directed.
- a, b and the other transition parameters are fixed in advance from the long panels, or pooled across kinds. They are not learned from 2003–2022.

**Online update.** A Hamilton filter:
- After y_t, the regime responsibilities are proportional to the prior regime probability times the likelihood of y_t under each regime's combination.
- Each regime's evidence stocks E^{(r)} are then updated with u weighted by those responsibilities, in place of π_s.
- The transition probability for t+1 comes from z_{t+1} and C_t.

**How it treats a quiet-until-decisive signal.** It accumulates evidence in the stress regime only. The regime probability rises as buffers drain (z_t) and as stress-regime signals keep being right (C_t). This is "evidence builds up while the buffer drains" written as a model.

**How it handles noise.** As in design 1 within each regime. The added risk is that a noise group nudges the responsibilities. Keep C_t built only from groups that have already passed their gates.

**What would test it.** Everything listed for design 1, plus:
- whether the filtered regime probability rises before onsets more often than before non-onsets (AUROC of P(stress) one to two years ahead);
- a check that b and c fixed in advance are not tuned on development folds.

**Literature support.** Elliott & Timmermann 2005; Waggoner & Zha 2012; Filardo 1994 (time-varying transitions); Billio et al. 2013 (score-driven weight state); Del Negro et al. 2016.
- It ranks below design 1 because, with few events, the regime posterior is identified mostly by z_t. In practice it behaves like design 1 with learned soft memberships, at the cost of more parameters.
- Using score stocks in the transition probability is our extension.

### Design 3: orthogonalised non-negative synthesis with state interaction (a constrained (C))

**Formula:**
```
logit p = ℓ_0 + Σ_k θ_{k,t}(z) · r_{k,t}
r_k     = residual of the increment (ℓ_{m_k} − ℓ_0) after a discounted online
          regression on the residuals r_1..r_{k-1}            (Gram–Schmidt in rank order)
θ_k(z)  = θ_k^0 + θ_k^1 · z,   θ ≥ 0
```
Each θ gets a spike-at-zero (or horseshoe) prior that the discount refreshes.

**Online update.** A dynamic logistic on the r's, which augur already has. A projection onto θ ≥ 0 follows each step.

**How it treats a quiet-until-decisive signal.** Through θ_k^1 · z. Its private part survives the orthogonalisation.

**How it handles noise.** The residualised regressors are no longer collinear, which was (C)'s failure. Non-negativity follows Breiman. Shrinkage follows Diebold & Shin's peLASSO idea.

**What would test it.** As for design 1. In addition, the noise cost should no longer swing between seeds, as it did for (C): -0.0004 and -0.0037.

**Literature support.** BPS (McAlinn & West), encompassing regressions (Clements & Harvey), stacking constraints (Breiman), peLASSO.
- It ranks third because it estimates 2M coefficients from few events. The puzzle literature (section 4) predicts it loses to design 1 unless the shrinkage is heavy.

### Design 4 (benchmark and fallback): threshold gates weighted by noise-to-signal ratio, with co-movement amplification

**Formula:**
```
logit p = ℓ_0 + Σ_k a_k · 1[x_k > τ_k] · (ℓ_{m_k} − ℓ_0) · (1 + γ · ρ_t)
a_k     = (1/NSR_k) / Σ_j (1/NSR_j)
```
- τ_k: a percentile of each group's own driver gauge, pre-registered;
- NSR_k: a discounted noise-to-signal ratio from earlier windows;
- ρ_t: the mean pairwise co-movement of the active groups' gauges (CISS-style); γ fixed in advance.

**Online update.** After each window resolves, update the discounted counts of signals that were followed by an onset and signals that were not.

**How it treats a quiet-until-decisive signal.** It is off by construction until its gauge crosses the threshold. Simultaneous crossings are amplified.

**How it handles noise.** A noise group's NSR is about 1 or more, so its weight is low. Its gate opens only when it crosses its threshold, which bounds its pooled effect.

**What would test it.** The same suite. The main role is a benchmark: design 1 should beat it. Berg & Pattillo found thresholding loses to a probit, and Kaminsky–Reinhart and the National Bureau of Statistics lights show it is what practitioners actually run.

Its threshold logic is also the recommended way to build π_s(z) for designs 1 and 2.

### Evaluation changes these designs need

All of these are proper, and all can be stated before the runs:
- Keep pooled Brier as the guard. Add a noise rule within each state stratum, scaled to the stratum's size.
- Score weighted by the state known at the origin, never by outcome (Lerch et al.).
- A Murphy diagram, or a Beta-weighted score, over θ ∈ (0.01, 0.3]. Also report log score next to Brier.
- Skill within strata of similar base rate (Hamill & Juras).
- Treat a single fold loss inside its interval as weak evidence. Ask for a loss in at least two folds, or one fold clear of zero on the log score as well.
