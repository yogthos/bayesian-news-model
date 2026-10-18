# Ties between countries, economic structure, and country categories

October 2026, epic bnm-g5t. The question: should a country's forecast
depend on who it is aligned with and dependent on (weighted, fluid ties,
never a hardcoded bloc list), on how its economy is built (services,
manufacturing, agriculture, extraction), and on categories of countries
that behave alike? Citations marked (unverified) were not confirmed against
a primary source or abstract.

## What the model has now

- The event model's excitation network is learned: each node is excited by
  its children and its six same-level neighbours by shared events, so ties
  that show up in the news get weight. The hierarchy above the country is
  geographic only; a node has one parent.
- No power weights (GDP, military capability), alignment data (UN votes,
  alliances, trade, arms), or bloc level.
- Sector shares (WDI agriculture, manufacturing, services % of GDP; fuel,
  food, metal, high-tech export shares) are in the slow data, but the onset
  models see them only averaged into the economy domain's summary; only
  fuel import and export shares enter alone, times the oil-price change.

## Ties

- No published test of exactly this: whether partners' activity, weighted
  by ties, predicts a quiet country's surges beyond its geographic
  neighbours.
- A learned connectivity matrix beat fixed spatial lags out of sample for
  subnational conflict (Dorff, Gallop & Minhas, ISQ 2022; JOP 2020).
  Latent-factor dyadic models (AME) beat ERGMs and latent space models held
  out (Minhas, Hoff & Ward, Political Analysis 2019).
- The structure of the dependence predicts; individual tie covariates mostly
  do not. Trade and IGO ties added almost nothing to dispute prediction once
  geography and network dependence were modelled (Ward, Siverson & Cao,
  AJPS 2007).
- Protests spread within one or two weeks, more between big trade partners
  (Magee & Massoud 2022, in sample). Coups do not spread, through any
  pathway tried (Miller, Joseph & Ohl, JCR 2018). Contagion is weaker into
  high-capacity states (Braithwaite, JPR 2010).
- No study found that weighting partners by GDP or military capability
  improves prediction. A state's size raises its own risk (Hegre, JCR
  2008); the capability ratio barely beats chance on dispute outcomes
  (Carroll & Kenkel, AJPS 2019). Power is one candidate weighting to
  test, not a default.
- Emergent blocs: Bayesian Poisson Tucker decomposition of ICEWS and GDELT
  finds communities (mostly geographic; the US, China, Russia with Belarus
  apart) and beat its rivals on held-out months for the busiest countries
  (Schein et al., ICML 2016), but takes thousands of MCMC sweeps.
  Poisson-gamma dynamical systems forecast GDELT dyads better than linear
  dynamical systems (Schein et al., NIPS 2016). A sparse plus low-rank
  Hawkes excitation (Zhou, Zha & Song, AISTATS 2013) is cheap enough to
  refit weekly; not yet tested on international events.
- Tie data back to 1979: UNGA votes (Voeten, yearly from 1946), ATOP
  alliances (to 2018), COW trade (to 2014) and IMF DOTS (monthly, from
  1980), SIPRI arms transfers (1950-2024). GDELT's own dyad counts need
  nothing new.

## Economic structure

- Static structure says where, not when. Most structural covariates of the
  classic civil-war models add little out of sample beyond income and
  population (Ward, Greenhill & Bakke, JPR 2010); structure predicts where
  conflict is but little of when (Mueller & Rauh, APSR 2018). Only oil
  survives the fragility checks on resource dependence (Fearon, JCR 2005;
  Ross 2006).
- What carries is structure times a shock, by commodity type:
  - labour-intensive price falls (coffee) raise violence through wages,
    capital-intensive price rises (oil) through contestable revenue (Dube &
    Vargas, REStud 2013);
  - over 46 natural experiments the pooled price effect is null because the
    signs differ: agricultural price rises lower conflict, oil and artisanal
    minerals raise it (Blair, Christensen & Rudkin, APSR 2021);
  - mineral price rises raise riots and battles near mines, up to 21% of
    average country violence in Africa (Berman, Couttenier, Rohner &
    Thoenig, AER 2017);
  - exposure weights must be fixed and lagged: with time-varying weights
    export-price shocks show no effect (Bazzi & Blattman, AEJ Macro 2014),
    with fixed weights downturns raise civil-war risk (Ciccone 2018).
- The same global shock hits differently by structure: food price rises
  raise demonstrations, riots and civil conflict in low-income countries
  and not in high-income ones (Arezki & Brückner, IMF WP 11/62); food price
  levels, not volatility, drive unrest (Bellemare, AJAE 2015); an oil
  supply shock cuts importers' output and raises exporters' (Cashin,
  Mohaddes & Raissi 2014).
- Banking crises: in low-income countries commodity price volatility
  predicts them and credit booms do not, the reverse of advanced economies,
  and mostly under fixed pegs with a large primary sector (Eberhardt &
  Presbitero, JIE 2021). The right predictors depend on the category.
- No study links economic complexity or premature deindustrialization to
  conflict or coups.
- Data: IMF commodity terms of trade by country (Gruss & Kebhaj, WP 19/21;
  182 economies, monthly from 1980, fixed and time-varying weights) is the
  best single exposure measure. WDI sector shares (yearly, revised with GDP
  rebasings: lag two years) and UN national accounts (ISIC, with mining
  apart) back to 1970. ILO employment by sector is modelled and
  re-estimated backwards from 1991: not backtestable.

## Categories

- Learned groups beat pooling all or none in macro forecasting: Bayesian
  mixture clustering of time series (Frühwirth-Schnatter & Kaufmann, JBES
  2008), grouped fixed effects with membership estimated from data and
  free to differ by period (Bonhomme & Manresa, Econometrica 2015),
  hierarchical priors choosing what to pool across countries (Koop &
  Korobilis, OBES 2019). Evidence for conflict is thin.
- So categories enter as a pooling level: coefficients, above all the shock
  interactions, shrink toward those of structurally similar countries,
  crossed with the geography. A quiet country then borrows from similar
  countries that have had onsets, which is what the hard cases need.

## Cross-reference: Chinese and Soviet/Russian research

**Already covered here.** The naive benchmark (next year as this year)
beat every published instability index (Korotayev, Medvedev, Zinkina,
Comparative Sociology 2022), as VIEWS found: our bar is own history and
persistence. Their break at 2011 falls between our scoring eras
(2003-14, 2015-26), and the onset stack refits yearly on discounted
outcomes. Spillover between dyads sharing an actor (MILSTM, ACM SAC 2020)
is in the event model: a dyad's neighbours are the dyads sharing one of
its countries.

**Gaps.**
- Non-linear effects: unarmed revolution follows an inverted U in income,
  armed revolution falls with it; inequality and regime type are inverted
  U too (Ustyuzhanin, Fain & Korotayev 2025-26). Our hazards are linear
  in their inputs.
- Interactions by place and outcome: low cereal yields matter only in
  Africa, low state effectiveness predicts armed destabilization only in
  Asia; non-violent destabilization goes with long incumbent tenure and
  middling democracy (Korotayev, Vostok 2024). Easily seized gold and oil
  raise risk where institutions are weak; resource abundance hurts only
  with weak institutions (Polterovich, Popov & Tonis 2007).
- The educated-youth mechanism: youth bulges raise non-violent protest only
  with income, urbanization and education controlled; Egypt's was fast
  growth in higher education with educated unemployed youth in the capital
  (Korotayev & Zinkina 2022).
- Alignment asymmetry: China's country-risk rating (CASS CROIC, yearly,
  57-114 countries, unvalidated) makes relations with and dependence on a
  patron a pillar of its own. Structural power as centrality in trade and
  value-chain networks (Wang Jinbo 2023); weaponized interdependence.
- Expert-coded dyad relations: Tsinghua's monthly scores of China's
  relations with the great powers from 1950 (Yan Xuetong et al. 2010;
  forecasts three months ahead reported over 70% accurate, test not
  described). A low-noise check on GDELT-learned ties, not a predictor.
- Rival groupings to test learned categories against: MGIMO's Political
  Atlas (five indices over 192 countries), the Afrasian instability zone,
  world-system zones, hub-centred neighbourhoods.
- Not taken up: Soviet situational analysis, global models and balance-of-
  forces scoring (never evaluated), long waves (descriptive; the forecasts
  checked missed), log-periodic bubble timing (gold 2011 called months
  early), mass-incident warning systems (tiny samples), composite risk
  indices (unvalidated). Turchin's 2010 decade forecast of Western
  instability held, graded by its authors.

None of the Chinese or Russian indices has a published out-of-sample
test; their value is as ideas and data.

## Plan

One harness for all of it: inputs added to the onset rows by group, scored
by `bnm.anticipation` (both eras, hard cases, lead window, budgets, matched
controls) and stacked onto the event model by `bnm.coupling` (quiet-base
surges). Every group also runs against a placebo (its values permuted
across countries within a year), and is dropped if it does not beat it.
Inputs are lagged as published (WDI two years for revisions).

1. **Ties, learned from the data.** Each country's partners weighted by
   lagged trailing-year GDELT dyad volume, later UN-vote agreement and
   trade shares (IMF DOTS), contiguous and same-subregion partners apart
   from the rest. Inputs: partners' weighted surge and conflict intensity;
   cross-pressure, the weighted conflict between a country's own partners
   (sum of w_i w_j times the i-j conflict intensity), which is how tension
   between patrons reaches a client without naming any patron; and the
   asymmetry of its dependence across its top partners. Power weighting
   (partner GDP share) as an ablation. Placebo: degree-preserving permuted
   ties.
2. **Structure times shocks.** Lagged sector shares (agriculture,
   manufacturing, services, mining) as their own inputs; commodity terms-of-
   trade change (IMF, Gruss & Kebhaj) by type: food and agriculture, fuel,
   metals; world food price times food-import dependence times cereal yield;
   resource rents times state effectiveness times the price change; for
   banking crises, commodity volatility times a fixed peg times primary
   share. Strong shrinkage.
3. **Non-linear terms.** Splines in income, regime and inequality, read
   separately for protest (unarmed) and violence (armed).
4. **The educated-youth mechanism.** Tertiary enrolment growth times youth
   unemployment times urban share, for protest.
5. **Categories as a pooling level**, if 2-4 help: soft memberships from a
   mixture on lagged structure vectors, refit yearly; hazard coefficients
   pooled within category beside the geography. Scored against pooling by
   geography and by the expert and hub groupings.
6. **Blocs and waves in the event model**, if 1 helps: low-rank community-
   structured cross-excitation beside the geographic hierarchy, memberships
   refit weekly from trailing dyad counts; a global wave intensity at the
   root.
7. **Structural centrality**, last: whether shocks in central economies
   (trade and finance network centrality) raise partners' banking-crisis
   and drawdown hazards.

Data to fetch: IMF commodity terms of trade, IMF DOTS, UNGA votes
(Voeten), WDI tertiary enrolment, urban share, cereal yield, food imports,
WGI government effectiveness (from 1996 only), exchange-rate regime
(Ilzetzki, Reinhart & Rogoff). Already in hand: GDELT dyads, sector shares,
export shares, resource rents, youth unemployment, V-Dem politics.

## Results

**Ties, step 1** (`bnm.onsets --groups ties`, then `bnm.anticipation
--bench ties-placebo`). Against the same inputs shuffled across countries
within each quarter, on hard cases (no onset in ten years):
- Conflict onsets: recall in the top 10% a year ahead 0.68 against the
  placebo's 0.54 (history 0.55); with the named inputs 0.67 against 0.60.
  Gains with intervals over years above zero in both eras, at quarters 2-4
  and in the year after; none below. In 2015-26 named plus ties gains +0.17
  at 10% and +0.18 at 5% over its placebo, and usefulness +0.13.
- Protest and violence surges: nothing beyond the placebo. Coups were not
  tried, as the literature found no coup contagion.
So learned ties carry real signal for conflict onsets in quiet countries.

**Before the review (October 2026), now void.** Regime (V-Dem) gained on
hard cases in 2003-14 only; exchange-rate regimes on coups only; commodity
terms of trade and UN-vote ties nothing. These ran on the labels the review
below found biased, and are rerun.

## Review of the onset pipeline (October 2026)

Fixed, each a bias in what the scores measured:
- **Rows just before an onset.** A row whose window opens after an onset
  (the lead window, quarters 2-4, with an onset next quarter) was scored a
  negative: the model that saw it coming lost. Such rows are now not at risk
  (`bnm.onset/at-risk?`).
- **Banking crises** were scored only over countries that have one in the
  catalogue (118, 73 with exactly one), so every hard row was a future
  crisis country. Now every country.
- **Onsets known only to the year** were dated 1 July (wars, Laeven-Valencia)
  or 31 December (UCDP, precision 5 or worse), so rows after the real onset
  could count as before it, and a model reacting to the onset scored as
  anticipating. Now 1 January.
- **Priors** were chosen on 2000-07, inside the scored era 2003-14. Now each
  year's prior is chosen on the years before it.
- **Incomplete quarters.** A catalogue covering through October counted the
  fourth quarter as observed. Now only complete quarters.
- **Zero share prices** (Brazil 1980-88) were logged to -28 and made eight
  crashes; non-positive values of logged series are now gaps.
- **Zeros standing in for time.** World food prices (from 1991), the VIX (from
  1990), high-tech exports (from 2007) and the PWT core profit rate (to its
  last year) were zero outside their span under a presence flag that said
  present. Each now has its own flag.
- **Placebos** shuffled the presence flags with the values, so a group's gain
  over its placebo could be who has data. Placebos now shuffle only among
  rows with the same sources present, and groups with gaps (mix, ties) carry
  a presence column.
Not fixed: V-Dem, the IRR regimes and the IMF terms-of-trade weights are one
vintage, revised with hindsight; WDI's annual series often appear later
than the lag assumed. The groups' extra 12-24 month lags soften this.

## Next directions (literature, October 2026)

- Rich structural data still miss new outbreaks (Bazzi et al., REStat 2022);
  news composition is the one replicated edge on hard onsets (Mueller &
  Rauh, JEEA 2022), as the mix found here. Process beats structure at short
  horizons (Blair & Sambanis, JCR 2020).
- Regime type matters non-linearly (factional partial democracy, Goldstone
  et al., AJPS 2010), which linear terms cannot learn: shallow boosted trees
  as one more stacked member, refit yearly with the same placebo test, would
  learn regime x economy interactions without hand-written terms. Trees win
  when inputs are rich and lose otherwise (Muchlinski et al. 2016 against
  Wang 2019; for banking, Bluwstein et al., JIE 2023, against Beutel et al.,
  Bundesbank 2018).
- Long calm, not rising variance, predicts banking crises (Danielsson,
  Valenzuela & Zer, RFS 2018); critical slowing down is inconsistent out of
  sample. Jumps in the mean and variance of attention series forecast
  protest (Timoneda & Wibbels, PA 2022). Candidate features: quarters since a
  spike, a low trailing coefficient of variation, breaks in mix shares.
- Banking: the debt service ratio one to two years ahead (Drehmann &
  Juselius, IJF 2014), credit with asset-price growth (Greenwood et al., JF
  2022), and the rest of the world's credit growth (Cesa-Bianchi et al.).
- Learned networks beat fixed contiguity (Dorff, Gallop & Minhas, ISQ 2022),
  as GDELT ties beat UN votes here; coups do not diffuse (Miller et al., JCR
  2018).
- Evaluation: Sarlin usefulness and recall at a budget are standard;
  TADDA rewards timid forecasts (Bracher et al., 2023) and is not used.
