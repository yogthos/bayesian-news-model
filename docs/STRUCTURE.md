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

## Plan

Each test is scored as the epic requires, on the hard cases and quiet
countries, against a placebo, and is dropped if it fails.

1. **Ties, no new data.** Each country's partners weighted by trailing-year
   GDELT dyad counts (lagged), less contiguous and same-subregion ones,
   row-normalised; the weighted lagged surge intensity of its partners into
   the onset hazards and the coupling, beside the geographic lags. Placebo:
   degree-preserving permuted ties. If the real ties do not beat the
   placebo, stop. Then UNGA agreement and trade shares as alternative
   weights, and power weighting as an ablation.
2. **Structure times shocks.** Lagged sector shares (agriculture,
   manufacturing, services, fuel, food, metal exports) as their own inputs,
   and commodity terms-of-trade change times exposure split by type (food
   and agriculture, fuel, metals), world food price times food dependence,
   with strong shrinkage.
3. **Categories as a pooling level**, if 2 helps: soft memberships from a
   mixture on lagged structure vectors, refit each year, with the hazard
   coefficients partially pooled within category.
4. **Blocs in the event model**, if 1 helps: a low-rank, community-
   structured cross-excitation beside the geographic hierarchy, memberships
   refit weekly from trailing dyad counts, warm-started.
