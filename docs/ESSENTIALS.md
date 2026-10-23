# Essentials and their supply chains (research and design, October 2026)

Epic bnm-1pq (material supply chains). The goal is to find which products the
modern economy cannot do without, where they come from, and the chains
around them. The model derives these from the data as it runs; nothing is
written down as "essential". The list changes with technology. Chips, solar
panels and electric vehicles raised the demand for lithium, cobalt, nickel,
graphite, rare earths, gallium, germanium, silicon and copper. Renewables and
nuclear displace fossil fuels in power. Haber-Bosch, fracking, LEDs and the
container reshaped demand before them. Strong priors are set only where
physics or biology fixes a recipe: food needs nitrogen, phosphorus and
potassium; people need calories, water, shelter and heat; steel needs iron
and a reductant. Everything else is learned (docs/PRINCIPLES.md 2), and every
read is from before the origin (7).

This document covers the literature, the data, the priors, some exploratory
numbers and a design with ranked steps. No code has been written yet. The
exploratory scripts were run outside the repository on
data/raw/material/{baci,usgs-myb,oecd-export-restrictions-irm,owid-energy}.

## Summary

- **Trade data alone picks out lithium and cobalt, but late and with noise.**
  The 1995-2024 trade data (BACI HS92) were scored with a causal rise
  measure. For each product and year it takes the robust 8-year trend
  (Theil-Sen) of the product's share of world trade value, and of its
  tonnes, and keeps the smaller of the two. Steps at HS revisions are
  removed. On that measure, of about 3,800 products:
  - lithium hydroxide ranks 24th in 2016 and 2nd to 3rd in 2018-2020;
  - lithium carbonate ranks 17th in 2019;
  - cobalt oxides rank 6th in 2018;
  - nickel sulphate ranks 14th in 2019;
  - "other accumulators" (lithium-ion in HS92) rank 7th to 4th in 2022-24;
  - "cars n.e.s." (electric and hybrid cars in HS92) rank 1st to 3rd in
    2021-24;
  - e-bikes (motorcycles n.e.s.) rank 2nd to 3rd in 2017-20.

  Before 2015 the rise is not visible. Lithium sat between 500th and 2,000th
  in 2010-14. The same top-20 lists also hold cotton yarn, rubber mixtures,
  skipjack tuna and leftovers of the HS revisions. So the rise score finds
  candidates, not essentials. What turns a candidate into an essential is
  its place in the graph (it is an input to a growing technology) and how
  concentrated its supply is.
- **Rare earths are a concentration story, not a demand-growth story, in
  trade.** China mined about 88-91% of rare earths in 2000-02 (the US still mined 5 kt at Mountain Pass) and 93-98% in 2003-05 (HHI 0.88-0.95,
  USGS). It has restricted their export since at least 2009 (OECD). Trade in
  rare-earth compounds barely ranks until 2024 (35th). Permanent magnets,
  where most rare-earth value now goes, rise from about 700th (2012) to 69th
  (2023). Gallium and germanium cannot be seen in HS92 at all (8112.91 lumps
  gallium with indium, niobium and rhenium). USGS production shows China
  with 92-98% of primary gallium in 2015-22 (HHI 0.84-0.97). Concentration
  flags these; growth does not.
- **Processing is where concentration grew.** China's share of refined
  cobalt went from 22% (2005) to 80% (2022), while the Congo's share of
  mined cobalt went from 38% to 73%. The two stages need separate nodes.
- **Detection must come from the data; the recipes are where physics comes
  in.** EXIOBASE's 200 products hide the new essentials. Lithium, cobalt,
  rare earths and gallium sit inside "other non-ferrous metal ores" and
  "other non-ferrous metal products"; chips and batteries sit inside
  "electrical machinery". The graph needs sub-nodes split out by HS6 trade
  and by USGS/BGS production. Where no table gives the coefficients, priors
  from engineering supply them (IEA 2021: 8.9 kg of lithium, 13.3 kg of
  cobalt, 66 kg of graphite and 53 kg of copper per electric car).
- **Essentiality as expected output at risk.** The model should not use a
  weighted index of four scores. It should use the probability that supply
  of a product is disrupted (from concentration, export restrictions,
  chokepoints and the project's own event forecasts for the supplying
  places) times the output lost if it is (bnm.supply's nonlinear outage,
  which already carries Baqaee-Farhi's complementarity). Projected demand
  growth enters through the diffusion model. This is the EU and Graedel
  "supply risk × importance" product, but each factor is computed rather
  than scored.
- **EXIOBASE.** Download five more years of v3.8.2 pxp (2000, 2005, 2008,
  2015 and 2019, about 3.5 GB), not all 25: the years after the last
  benchmark are mostly extrapolated. Also download BACI HS12 (2012-2024,
  open), for lithium-ion cells (850760) and finer electronics.

## 1. Measuring essentiality from data

### Criticality frameworks (policy)

All of these cross an importance axis with a supply-risk axis.

- **US National Research Council (2008).** *Minerals, Critical Minerals, and
  the U.S. Economy.* National Academies Press,
  https://doi.org/10.17226/12034. This is the original two-axis matrix: the
  impact of a supply restriction (importance in use, substitutability)
  against the likelihood of a restriction (geological, technical,
  environmental, social, political and economic availability, plus import
  dependence).
- **Graedel et al.**
  - Graedel, T.E., Barr, R., Chandler, C. et al. (2012). Methodology of metal
    criticality determination. *Environmental Science & Technology*
    46(2):1063-1070, https://doi.org/10.1021/es203534z.
  - Graedel, T.E., Harper, E.M., Nassar, N.T., Nuss, P. & Reck, B.K. (2015).
    Criticality of metals and metalloids. *PNAS* 112(14):4257-4262,
    https://doi.org/10.1073/pnas.1500415112.

  These score three axes for 62 metals: supply risk, environmental
  implications and vulnerability to supply restriction. Vulnerability
  includes substitute performance and availability, from expert scoring.
  The 2015 paper finds that no metal has a substitute that performs equally
  well in all its major uses. That is a usable prior: σ is small for
  specialty metals.
- **European Commission CRM methodology.** Economic importance (EI) is the
  value added of the using sectors, weighted by end-use shares and scaled
  by a substitution index. Supply risk (SR) is the HHI of global and EU
  sourcing, weighted by WGI governance, then adjusted for import reliance,
  end-of-life recycling and substitution. A material is critical above
  thresholds on both.
  - Blengini, G.A. et al. (2017). *Methodology for establishing the EU list
    of critical raw materials: Guidelines.* JRC/European Commission,
    https://doi.org/10.2873/769526.
  - Blengini, G.A., Nuss, P., Dewulf, J. et al. (2017). EU methodology for
    critical raw materials assessment: policy needs and proposed solutions
    for incremental improvements. *Resources Policy* 53:12-19,
    https://doi.org/10.1016/j.resourpol.2017.05.008.
  - The lists of 2011, 2014, 2017, 2020 and 2023, and the CRM Act
    (Regulation (EU) 2024/1252), are held-out labels for validation, never
    inputs.
- **US Department of Energy.** *Critical Materials Strategy* (2010, 2011)
  and *Critical Materials Assessment* (July 2023),
  https://www.energy.gov/cmm/critical-materials-assessment. These use a
  two-axis matrix (importance to energy, supply risk) over short and medium
  horizons. The 2023 assessment treats demand growth from clean-energy
  deployment explicitly, which is our "demand growth from diffusion" term.
- **USGS 2022 list.**
  - Nassar, N.T. & Fortier, S.M. (2021). *Methodology and technical input
    for the 2021 review and revision of the U.S. critical minerals list.*
    USGS Open-File Report 2021-1045, https://doi.org/10.3133/ofr20211045.
  - Nassar, N.T., Brainard, J., Gulley, A. et al. (2020). Evaluating the
    mineral commodity supply risk of the U.S. manufacturing sector.
    *Science Advances* 6(8):eaay8647,
    https://doi.org/10.1126/sciadv.aay8647.

  Supply risk here is the expected disruption: the probability of a foreign
  disruption (from production concentration and governance) times US import
  reliance times economic vulnerability. It is the closest of the
  frameworks to the probability × loss design below, and it is
  data-driven.

What the frameworks share is a product of risk and impact. What they lack,
for our purposes, is time and causality. Each list is a snapshot of expert
scores, and lists are revised every three years after the fact.

### Network and input-output measures (macro importance)

- **Domar weights and Hulten.** To first order, the aggregate effect of a
  shock to a sector equals its sales over GDP (Hulten 1978). Domar weights
  ignore the network beyond that and make small sectors irrelevant. Our
  data give them directly: output value over value added, by region and
  year, from EXIOBASE.
- **Baqaee & Farhi (2019).** The macroeconomic impact of microeconomic
  shocks: beyond Hulten's theorem. *Econometrica* 87(4):1155-1203,
  https://doi.org/10.3982/ECTA15202. With complementarity (σ < 1) the
  second-order term grows as the shock grows, so a small sector with no
  substitute (they use energy and oil) is macro-critical. In a large shock
  the loss is far above its Domar weight. bnm.supply's nested
  Leontief-on-critical / CES-on-the-rest recipes, propagated by
  hypergraph/propagate, compute this nonlinear loss directly. The outage
  loss is the macro-importance measure: no linearisation is needed.
- **Hypothetical extraction.**
  - Strassert, G. (1968). Zur Bestimmung strategischer Sektoren mit Hilfe
    von Input-Output-Modellen. *Jahrbücher für Nationalökonomie und
    Statistik* 182:211-215.
  - Dietzenbacher, E., van der Linden, J.A. & Steenge, A.E. (1993). The
    regional extraction method. *Journal of Regional Science* 33(4):485-503.
  - Miller, R.E. & Lahr, M.L. (2001). A taxonomy of extractions. In
    *Regional Science Perspectives in Economic Analysis*, Elsevier.

  Remove a sector and measure the output lost. This is the linear (σ = 0
  everywhere) limit of our outage with x = 1. It overstates, as the German
  gas case shows (below).
- **Ghosh forward linkages.** Ghosh, A. (1958). Input-output approach in an
  allocation system. *Economica* 25(97):58-64,
  https://doi.org/10.2307/2550694. The supply-side inverse: how much
  downstream output a unit of primary input enables. Read it as a cost-push
  price model, not as quantities (Dietzenbacher 1997, *Journal of Regional
  Science* 37(4):629-651). bnm.supply's "dependence share" is exactly this
  price reading.
- **Upstreamness.** Antràs, P., Chor, D., Fally, T. & Hillberry, R. (2012).
  Measuring the upstreamness of production and trade flows. *American
  Economic Review P&P* 102(3):412-416,
  https://doi.org/10.1257/aer.102.3.412. This is the average number of
  stages between a product and final demand. In our graph it is the depth
  over the recipes, weighted by use shares. It locates a product in the
  chain: ore, refined, component, final. It is not importance.
- **Network centrality.**
  - Acemoglu, D., Carvalho, V.M., Ozdaglar, A. & Tahbaz-Salehi, A. (2012).
    The network origins of aggregate fluctuations. *Econometrica*
    80(5):1977-2016, https://doi.org/10.3982/ECTA9623.
  - Carvalho, V.M. (2014). From micro to macro via production networks.
    *Journal of Economic Perspectives* 28(4):23-48,
    https://doi.org/10.1257/jep.28.4.23.
  - Carvalho, V.M. & Tahbaz-Salehi, A. (2019). Production networks: a
    primer. *Annual Review of Economics* 11:635-663,
    https://doi.org/10.1146/annurev-economics-080218-030212.

  The influence vector (Bonacich centrality of the Leontief inverse) equals
  the Domar weights in their Cobb-Douglas economy. A heavy-tailed
  distribution of influence makes idiosyncratic shocks aggregate. It is
  useful as a cheap screen, but it inherits Hulten's first-order blind
  spot.
- **The essential-inputs literature**, which our critical rule already
  draws on:
  - Pichler, A., Pangallo, M., del Rio-Chanona, R.M., Lafond, F. & Farmer,
    J.D. (2022). Forecasting the propagation of pandemic shocks with a
    dynamic input-output model. *Journal of Economic Dynamics and Control*
    144:104527, https://doi.org/10.1016/j.jedc.2022.104527. Industry
    analysts (the IHS Markit survey, per the working paper) rated each of
    55 × 55 WIOD inputs critical or not. A critical input binds as
    Leontief; non-critical ones do not bind in the short run. Inventories
    in days of use buffer the shock. This is a one-off expert labelling of
    what we infer from the physical rows.
  - Bachmann, R., Baqaee, D., Bayer, C., Kuhn, M., Löschel, A., Moll, B.,
    Peichl, A., Pittel, K. & Schularick, M. (2022). What if? The economic
    effects for Germany of a stop of energy imports from Russia.
    ECONtribute Policy Brief 028,
    https://www.econtribute.de/RePEc/ajk/ajkpbs/ECONtribute_PB_028_2022.pdf.
    Using Baqaee-Farhi with a gas elasticity of about 0.1-0.2, they put the
    loss from a gas stop at 0.5-3% of GNE.
  - Moll, B., Schularick, M. & Zachmann, G. (2023). The power of
    substitution: the great German gas debate in retrospect. *Brookings
    Papers on Economic Activity*, Fall 2023,
    https://www.brookings.edu/articles/the-power-of-substitution-the-great-german-gas-debate-in-retrospect/.
    Germany cut gas use by about 20% in 2022 with a small output loss.
    Ammonia and other gas-intensive industry fell sharply, and the gap was
    made up with imports of the gas-intensive products (ammonia, urea).
    Substitution happened at the level of the product, through trade, more
    than in the recipe. That is the lesson for the outage: let imports of
    the downstream product reallocate (bnm.supply does not yet; see
    STOCK-FLOW "Imports are not reallocated").

### Which can be computed causally, year by year, from what we hold

| measure | inputs | yearly? | notes |
|---|---|---|---|
| Domar weight | EXIOBASE output and value added | 1995, 2011, 2022 now; every year with more downloads | value data; ok with a 3-year lag |
| nonlinear outage loss (Baqaee-Farhi) | recipes + trade + σ | each year a recipe set exists | already in bnm.supply; recipes must be updated yearly (section 2) |
| hypothetical extraction | the same, σ = 0, x = 1 | yes | the upper bound; report it beside the outage loss |
| Ghosh / dependence share | value shares, trade shares | yes | the price channel |
| upstreamness | use shares | yes | stage labels for chain mapping |
| centrality | Leontief inverse | yes | a screen only |
| product-level rise | BACI HS6 value and tonnes | yes, 1995-2024, known +2 | section 6 |
| production concentration (mine, refinery) | USGS MYB 1990-2022, MCS 2022-26, BGS 1970-2024 | yes, known +1-2 | section 5 |
| export concentration | BACI | yes | |
| export restrictions | OECD inventory 2009-2024 | yes | starts 2009 |
| substitutability σ | BACI unit values and quantities; priors | rolling | section 2 |
| chokepoint exposure | BACI bilateral + sea routes; PortWatch 2019+ | yes (routed), daily from 2019 | section 5 |

Expert criticality scores (Graedel, the EU and DOE lists, Pichler's survey)
are kept as validation labels only.

## 2. Recipes that change over time

### Updating coefficients

- **RAS and GRAS.**
  - Stone, R. (1961). *Input-Output and National Accounts.* OEEC, Paris.
  - Bacharach, M. (1970). *Biproportional Matrices and Input-Output Change.*
    Cambridge University Press.
  - Junius, T. & Oosterhaven, J. (2003). The solution of updating or
    regionalizing a matrix with both positive and negative entries.
    *Economic Systems Research* 15(1):87-96,
    https://doi.org/10.1080/0953531032000056954.
  - Lenzen, M., Wood, R. & Gallego, B. (2007). Some comments on the GRAS
    method. *Economic Systems Research* 19(4):461-465,
    https://doi.org/10.1080/09535310701698613.
  - Temurshoev, U., Miller, R.E. & Bouwmeester, M.C. (2013). A note on the
    GRAS method. *Economic Systems Research* 25(3):361-367,
    https://doi.org/10.1080/09535314.2012.746645.

  These scale rows and columns of an old table until its margins match the
  new ones: minimum information change. They project the table; they do not
  learn why a coefficient moves. They are the right final step to make a
  year's table close (PRINCIPLES 8), given margins and a prior table.
- **Bayesian and entropy estimation.**
  - Golan, A., Judge, G. & Robinson, S. (1994). Recovering information from
    incomplete or partial multisectoral economic data. *Review of Economics
    and Statistics* 76(3):541-549, https://doi.org/10.2307/2109978.
  - Rodrigues, J.F.D. (2014). A Bayesian approach to the balancing of
    statistical economic data. *Entropy* 16(3):1243-1271,
    https://doi.org/10.3390/e16031243.
  - Lenzen, M., Kanemoto, K., Moran, D. & Geschke, A. (2012). Mapping the
    structure of the world economy. *Environmental Science & Technology*
    46(15):8374-8381, https://doi.org/10.1021/es300171x. Eora's
    constrained optimisation with reliability weights per source.

  Rodrigues shows that RAS is the posterior mode under a particular prior.
  A Bayesian balance with explicit variances per source is the general
  form. That is what we want: priors from physics on some cells, data with
  stated errors on others, and closure as a constraint.
- **Dynamic estimation.** Treat each coefficient as a state, log a(t) =
  log a(t-1) + drift + ε, observed with error by each source (a dynamic
  linear model: West, M. & Harrison, J. (1997), *Bayesian Forecasting and
  Dynamic Models*, Springer). It moves with learning-by-doing (below) and
  with technology shares (the steel route mix, the power mix). This is how
  bnm.assimilate already treats parameters.

### Technology driving input demand

- **Diffusion.** Comin, D. & Hobijn, B. (2010). An exploration of technology
  diffusion. *American Economic Review* 100(5):2031-2059,
  https://doi.org/10.1257/aer.100.5.2031. bnm.technology fits logistic
  adoption per country on CHAT and WDI/ITU. New technologies need series:
  - EV sales share: IEA Global EV Outlook data, or EV-flagged BACI codes;
  - PV and wind capacity: EIA INTL and OWID, both held;
  - LED share: no open yearly series; a prior from US DOE SSL reports, or
    BACI 854140 as a proxy;
  - battery GWh: no open series; derive it from lithium-ion trade tonnes
    with a Wh/kg prior.
- **Material intensity of technologies.**
  - IEA (2021). *The Role of Critical Minerals in Clean Energy
    Transitions.* Paris,
    https://www.iea.org/reports/the-role-of-critical-minerals-in-clean-energy-transitions.
    Plus the yearly *Global Critical Minerals Outlook* 2023-2025 and the
    Critical Minerals Data Explorer.
  - Hund, K., La Porta, D., Fabregas, T.P., Laing, T. & Drexhage, J. (2020).
    *Minerals for Climate Action: The Mineral Intensity of the Clean Energy
    Transition.* World Bank,
    https://pubdocs.worldbank.org/en/961711588875536384/Minerals-for-Climate-Action-The-Mineral-Intensity-of-the-Clean-Energy-Transition.pdf.
  - Watari, T., McLellan, B.C., Giurco, D., Dominish, E., Yamasue, E. &
    Nansai, K. (2019). Total material requirement for the global energy
    transition to 2050: a focus on transport and electricity. *Resources,
    Conservation and Recycling* 148:91-103,
    https://doi.org/10.1016/j.resconrec.2019.05.015.
  - Watari, T., Nansai, K. & Nakajima, K. (2020). Review of critical metal
    dynamics to 2050 for 48 elements. *Resources, Conservation and
    Recycling* 155:104669, https://doi.org/10.1016/j.resconrec.2019.104669.
  - Valero, A., Valero, A., Calvo, G. & Ortego, A. (2018). Material
    bottlenecks in the future development of green technologies.
    *Renewable and Sustainable Energy Reviews* 93:178-200,
    https://doi.org/10.1016/j.rser.2018.05.041.

  These give kg of each element per vehicle, per MW, per GWh, with ranges
  across chemistries and designs (section 4).
- **Intensities fall with cumulative production.** Wright's law: per
  doubling, intensities fall by a stable fraction. PV silicon went from
  about 16 g/W (2004) to about 3 g/W, and silver paste fell several-fold
  (ITRPV yearly roadmaps).
  - Nagy, B., Farmer, J.D., Bui, Q.M. & Trancik, J.E. (2013). Statistical
    basis for predicting technological progress. *PLOS ONE* 8(2):e52669,
    https://doi.org/10.1371/journal.pone.0052669.
  - Way, R., Ives, M.C., Mealy, P. & Farmer, J.D. (2022). Empirically
    grounded technology forecasts and the energy transition. *Joule*
    6(9):2057-2082, https://doi.org/10.1016/j.joule.2022.08.009.

  So each intensity prior is a learning curve with an uncertain exponent,
  not a constant.
- **Dematerialisation and rebound.**
  - Magee, C.L. & Devezas, T.C. (2017). A simple extension of
    dematerialization theory: incorporation of technical progress and the
    rebound effect. *Technological Forecasting and Social Change*
    117:196-205, https://doi.org/10.1016/j.techfore.2016.12.001. Across 57
    cases, demand growth outran intensity decline: no absolute
    dematerialisation, even in information technology.
  - Pfaff, M., Glöser-Chahoud, S., Chrubasik, L. & Walz, R. (2018). Resource
    efficiency in the German copper cycle: analysis of stock and flow
    dynamics resulting from different efficiency measures. *Resources,
    Conservation and Recycling* 139:205-218,
    https://doi.org/10.1016/j.resconrec.2018.08.017. This is the
    Pfaff-group material-flow work on efficiency measures; a better match
    for "Pfaff et al." on dematerialisation was not found and should be
    checked.
  - Sorrell, S. (2009). Jevons' paradox revisited. *Energy Policy*
    37(4):1456-1469, https://doi.org/10.1016/j.enpol.2008.12.003.
  - Gillingham, K., Rapson, D. & Wagner, G. (2016). The rebound effect and
    energy efficiency policy. *Review of Environmental Economics and
    Policy* 10(1):68-88, https://doi.org/10.1093/reep/rev017.

  For the model: a falling intensity is no prior for falling total demand.
  Demand is adoption × intensity, and both are learned.

### Detecting a new essential without being told

Three signals have to agree. Each one can be computed from the data at the
time.

1. **Rise.** The product's share of world trade, and its tonnes, trend up.
   Use a robust trend (Theil-Sen over 8 years), the smaller of the two
   trends (which guards against price spikes and quantity artefacts), and a
   filter for HS revision steps (section 6). The rise is ranked against all
   HS6 products.
2. **Position.** The product is upstream of something that is itself rising,
   or of something large. Recipes give that link where they exist. For new
   products, which EXIOBASE does not split, a data-derived candidate edge:
   - the importers of input X overlap with the exporters of product Y (the
     countries that import lithium hydroxide are the ones that export
     cells);
   - the two trends co-move within those countries;
   - the edge's prior comes from the intensity tables (section 4).

   This is a link proposal, weighed against a placebo (another country's or
   period's flows, PRINCIPLES 5).
3. **Concentration.** Few countries, or few plants, supply it at some stage.

A new essential is a product that rises, sits upstream of a rising or large
node, and whose supply is concentrated. Rise alone gives cotton yarn and
tuna.

## 3. Data for the time dimension

### What gives yearly structure

| source | years | resolution | lag | notes |
|---|---|---|---|---|
| BACI HS92 (held) | 1995-2024 | HS6 × exporter × importer, value (kUSD) and tonnes | known +2 | the richest yearly signal; mirror-reconciled; tonnes partly imputed (854140 shows 29.8 Mt in 2020 against 3.3 in 2019) |
| USGS MYB world production (held) | 1990-2022 | commodity × form (mine, refinery, plant) × country | +2 | editions overlap: take the latest edition per country-year-form |
| USGS MYB vol. III 2024 (held) | 2020-24 production; facilities | facility × commodity × capacity, by province | +1-2 | no coordinates; province-level |
| USGS MCS (held) | 2 years per edition, 2015-2026 | country × commodity, reserves | +1 | prices for the US |
| BGS World Mineral Statistics (held) | 1970-2024 | country × commodity, production and (to 2002) trade | +1-2 | local research use only |
| UNSD energy (held) | 1990-2024 | country × product × flow, including non-energy use by chemicals | +2 | ammonia's gas feedstock appears as non-energy use |
| EXIOBASE v3.8.2 pxp (1995, 2011, 2022 held) | 1995-2022 | 200 products × 49 regions, monetary | +3 | later years extrapolated |
| EXIOBASE hybrid 2011 (held) | 2011 | physical | | the only physical year |
| OWID and EIA power (held) | 1965/1980-2024 | power by source | +1 | |
| OECD export restrictions (held) | 2009-2024 | country × HS6 (HS07) × measure | +1 | |
| GEM summary tables (held) | 2000-2025 | country × year capacity added and retired | +1 | |
| FAOSTAT fertiliser by nutrient (not held) | 1961-2023 | country × N, P2O5, K2O use and production | +1-2 | needed for the food priors |

### EXIOBASE: download the rest?

The full v3.8.2 pxp set is 28 years and 19.5 GB; each year is a 0.54-0.74 GB
zip (Zenodo 5589597, CC BY-SA 4.0). We hold 1995, 2011 and 2022.

Recommendation: add **2000, 2005, 2008, 2015 and 2019** (about 3.5 GB). Do
not download all 25 now.

- The detailed national supply-use data behind EXIOBASE 3 run to about 2011.
  Later years are extrapolated from macro aggregates and trade, and the
  latest years are nowcasts. Stadler, K. et al. (2018). EXIOBASE 3.
  *Journal of Industrial Ecology* 22(3):502-515,
  https://doi.org/10.1111/jiec.12715.
- Consecutive years add little independent recipe information. Five
  well-spaced years let the coefficient model (section 2) estimate drift
  and its variance per coefficient.
- One vintage is used for every year: the 2021 release revised the early
  years with data from after them. That is a known look-ahead, as with
  UNSD. Read each year at year + 3 and accept the revisions.
- v3.9 and later carry a non-commercial licence; see the EXIOBASE
  SOURCE.txt. Stay on v3.8.2.

If the drift estimated from five years turns out to forecast the 2022 table
better than a constant 2011 does (a registered test, step 5), download the
rest.

### HS6 codes for materials and technologies

HS92 is the longest series, but newer products sit in residual codes. The
HS revisions (2002, 2007, 2012, 2017, 2022) are converted back to HS92, which
makes artefacts. For example, the HS92 digital and non-digital integrated
circuits (854211, 854219) swap their values at 2012 and 2017, and 854280
drops to zero.

| product | HS92 (held) | finer code (vintage) |
|---|---|---|
| lithium carbonate | 283691 | the same |
| lithium oxide and hydroxide | 282520 | the same |
| lithium-ion batteries | inside 850780 "other accumulators" | 850760 (HS2012+) |
| electric and hybrid cars | inside 870390 "n.e.s." | 870340-870380 (HS2017+); 870380 is battery-electric |
| e-bikes | inside 871190 | 871160 (HS2017+) |
| PV cells and modules, LEDs | together in 854140 | 854142/854143 PV and 854141 LED (HS2022+) |
| integrated circuits | 854211-854280 (broken at revisions) | 854231-854239 (HS2007+) |
| rare-earth metals | 280530 | the same |
| rare-earth compounds | 284610 cerium, 284690 other | not split further in HS2022 (still 284610, 284690) |
| permanent magnets | 850511 | the same |
| cobalt | 260500 ore, 282200 oxides, 810510 mattes and unwrought | 810520/810530 (HS2002+) |
| nickel | 750110 matte, 750120 intermediates (HPAL), 283324 sulphate | |
| graphite | 250410, 250490 natural; 380110 artificial | |
| polysilicon | 280461 (Si ≥ 99.99%) | |
| gallium, germanium | 8112.91 (with In, Nb, Re); 811230 germanium only in HS92-HS02 | gallium not separable in any HS edition; use USGS |
| N, P, K | 281410 ammonia, 310210 urea, 251010 phosphate rock, 310420 KCl | |
| copper | 260300 ore, 740311 cathode, 741022 foil (battery anodes) | |

Download BACI **HS12** (2012-2024; open; about 2 GB zipped by analogy with
HS07's 1.68 GB, to be checked). It splits out lithium-ion cells and has a
cleaner IC series. HS17 (2017-2024) adds battery-electric cars. Keep HS92
for the long series; use the newer files for splits and to correct HS92
residual codes after their start years.

### Concordances

- **HS revisions to each other, HS to SITC, BEC, ISIC and CPC:** UNSD
  correspondence tables,
  https://unstats.un.org/unsd/trade/classifications/correspondence-tables.asp
  (open). BEC rev. 5 labels HS6 as intermediate, capital or consumption,
  which is a cheap stage label. Pierce, J.R. & Schott, P.K. (2012),
  *Journal of Economic and Social Measurement* 37(1-2):61-96, give HS
  over-time concordances (open).
- **HS to CPA 2008:** Eurostat CN/HS-CPA correspondence (RAMON / ShowVoc;
  open).
- **CPA to EXIOBASE:** EXIOBASE's products are CPA-based aggregates. The
  correspondence is in Classifications_v_3_3_18.xlsx (held). Chain HS to
  CPA to EXIOBASE. No published direct HS-to-EXIOBASE table was found.
- **The splits:** HS6 sub-nodes of an EXIOBASE product keep the parent's
  balance. A sub-node's share of the parent is its share of the parent's
  HS6 trade value, or of USGS production where trade misses domestic use
  (polysilicon after China became self-sufficient: 280461 trade fell from
  430 kt in 2015 to 164 kt in 2024 while production grew).

## 4. Priors from physics and biology

Each prior is a distribution on a recipe coefficient, written as a central
value and a 90% range, with a floor where thermodynamics sets one. The data
then update it (section 7a). A coefficient the data push outside its prior
range is a flag for a bug or a unit error (PRINCIPLES 10) before it is
believed.

### Food

- **Nitrogen in grain.**
  - Wheat removes about 20-25 kg N/t (grain protein of about 12%), maize
    13-16, rice 11-13.
  - Nitrogen use efficiency (NUE: N in harvest over N applied) is 0.3-0.7.
    The world mean is about 0.4-0.47, falling with intensification in China
    and India and rising in the US and EU.
  - So applied N per tonne of grain is about 30-60 kg.
  - Sources:
    - Cassman, K.G., Dobermann, A. & Walters, D.T. (2002). Agroecosystems,
      nitrogen-use efficiency, and nitrogen management. *Ambio*
      31(2):132-140, https://doi.org/10.1579/0044-7447-31.2.132.
    - Lassaletta, L., Billen, G., Grizzetti, B., Anglade, J. & Garnier, J.
      (2014). 50 year trends in nitrogen use efficiency of world cropping
      systems. *Environmental Research Letters* 9:105011,
      https://doi.org/10.1088/1748-9326/9/10/105011.
    - Zhang, X., Davidson, E.A., Mauzerall, D.L. et al. (2015). Managing
      nitrogen for sustainable development. *Nature* 528:51-59,
      https://doi.org/10.1038/nature15743.
- **Synthetic nitrogen is Haber-Bosch.** About half of humanity's protein
  nitrogen comes from synthetic N. The yield response is concave (a
  Mitscherlich curve), so the recipe is critical at the margin, not
  Leontief in total.
  - Smil, V. (2001). *Enriching the Earth.* MIT Press.
  - Erisman, J.W., Sutton, M.A., Galloway, J., Klimont, Z. & Winiwarter, W.
    (2008). How a century of ammonia synthesis changed the world. *Nature
    Geoscience* 1:636-639, https://doi.org/10.1038/ngeo325.
- **Ammonia's feedstock.**
  - Steam methane reforming: best practice about 28 GJ (LHV) of gas per t
    NH3; average gas plants 32-36; coal gasification about 45-55.
  - The thermodynamic floor is about 20-21 GJ/t (the LHV of ammonia is 18.6
    GJ/t, plus the separation work).
  - So 0.55-0.75 t of natural gas per t NH3 (82% N). This matches
    EXIOBASE's 0.64 t of gas per t of German N fertiliser and 0.83 for the
    US. China's 0.33 t of gas beside coal matches its coal route.
  - Sources: IEA (2021) *Ammonia Technology Roadmap*,
    https://www.iea.org/reports/ammonia-technology-roadmap; Worrell, E.,
    Price, L., Neelis, M., Galitsky, C. & Zhou, N. (2008). *World Best
    Practice Energy Intensity Values for Selected Industrial Sectors.*
    LBNL-62806, https://doi.org/10.2172/927032.
- **Phosphorus.**
  - Grain removes about 3-4 kg P/t (wheat about 3.5; maize 2.5-3).
  - Phosphate rock is 28-32% P2O5 (12-14% P).
  - The few-country concentration of rock reserves is the physical root of
    P criticality: Morocco and Western Sahara hold about 70% of reserves
    (USGS MCS).
  - Cordell, D., Drangert, J.-O. & White, S. (2009). The story of
    phosphorus: global food security and food for thought. *Global
    Environmental Change* 19(2):292-305,
    https://doi.org/10.1016/j.gloenvcha.2008.10.009.
- **Potassium.** Grain removes about 4-6 kg K/t; straw removal adds much
  more. Potash ore (KCl, 60% K2O) comes mostly from Canada, Russia, Belarus
  and China. Our MYB numbers give Canada 29-37% of marketable potash
  (HHI 0.17-0.20).
- **Calories.**
  - Mean dietary energy requirement is about 2,100-2,400 kcal per person
    per day, by age, sex and activity. FAO's minimum (MDER) is about
    1,700-2,000.
  - Supply per person in the food balance sheets (held for cereals) runs
    2,400-3,500.
  - Recipe: final demand for calories = population × requirement × (1 +
    losses of about 0.3), with a hard floor at the MDER. Below it,
    famine-type onsets.
  - FAO/WHO/UNU (2004). *Human Energy Requirements.* FAO Food and Nutrition
    Technical Report Series 1, https://www.fao.org/3/y5686e/y5686e00.htm.
- **Water.**
  - Crop water footprints, global means per tonne (green + blue + grey):
    wheat 1,827 m3, maize 1,222, rice 1,673, soybean 2,145.
  - Blue (irrigation) is the part that competes, and it varies tenfold by
    place.
  - Basic human need is about 50 L per person per day (Gleick 1996). WRI
    Aqueduct (held) gives baseline stress by province.
  - Mekonnen, M.M. & Hoekstra, A.Y. (2011). The green, blue and grey water
    footprint of crops and derived crop products. *Hydrology and Earth
    System Sciences* 15:1577-1600,
    https://doi.org/10.5194/hess-15-1577-2011.
  - Gleick, P.H. (1996). Basic water requirements for human activities.
    *Water International* 21(2):83-92,
    https://doi.org/10.1080/02508069608686494.
- **Shelter and heat.** Heat demand is proportional to heating degree-days ×
  floor area × U-value. The fuel mix is learned; the degree-days are
  physical (ERA5). Cement and steel per m2 of building are 0.2-0.5 t of
  cement and 30-60 kg of steel per m2 of residential floor area in
  concrete-frame stock (wide range; the data update it).

### Materials and energy

- **Steel, blast furnace route.**
  - Hot metal: the absolute minimum is 9.8 GJ/t and the practical minimum
    10.4; actual use is 13-14 (Fruehan, R.J., Fortini, O., Paxton, H.W. &
    Brindle, R. (2000), *Theoretical Minimum Energies to Produce Steel for
    Selected Conditions*, US DOE / Energetics, https://doi.org/10.2172/769470).
  - World best practice for the integrated route is about 15-16 GJ/t of
    crude steel (Worrell et al. 2008).
  - Per tonne of hot metal: 1.5-1.65 t of iron ore (at 60-65% Fe) and
    0.35-0.5 t of coke, which is about 0.45-0.65 t of coking coal.
- **Steel, other routes.**
  - Scrap electric arc furnace: 0.35-0.5 MWh/t of electricity plus about
    1.05-1.1 t of scrap.
  - Hydrogen direct reduction: about 50-55 kg of H2 per t of steel, which
    is about 2.6-3.0 MWh/t of electrolysis, plus about 1.5 t of ore. Vogl,
    V., Åhman, M. & Nilsson, L.J. (2018). Assessment of hydrogen direct
    reduction for fossil-free steelmaking. *Journal of Cleaner Production*
    203:736-745, https://doi.org/10.1016/j.jclepro.2018.08.279.
  - The route mix (BF/BOF, EAF, DRI) is in SFI steel and GEM (held). It is
    the main driver of steel's recipe drift.
- **Cement.**
  - Clinker's thermodynamic minimum is about 1.7-1.8 GJ/t of clinker.
    Best-practice dry kilns with preheaters use 2.9-3.3 GJ/t; the world
    mean is about 3.5.
  - Per tonne of clinker: 1.5-1.6 t of limestone.
  - The clinker-to-cement ratio is 0.65-0.95.
  - Electricity is 90-120 kWh/t of cement.
  - IEA & WBCSD-CSI (2018). *Technology Roadmap: Low-Carbon Transition in
    the Cement Industry*, https://www.iea.org/reports/technology-roadmap-low-carbon-transition-in-the-cement-industry.
- **Aluminium.**
  - Hall-Héroult's thermodynamic minimum with a carbon anode is about
    6.3 kWh/kg. Best practice is about 12.5-13 and the world mean about
    14-15 (IAI statistics).
  - Per tonne of aluminium: 1.92-1.95 t of alumina, which needs 2.2-3.0 t
    of bauxite per t of alumina, so 4-5.5 t of bauxite in all; and about
    0.4-0.45 t of carbon anode.
  - Electricity is the critical input. Its source mix is learned
    (hydropower in Norway and Iceland, coal in China).
- **Refining.** Already handled by the hybrid table and UNSD. Crude in is
  1.0-1.05 t per t of products out, and fuel and losses are about 4-8%.
- **Electric car (IEA 2021).**
  - About 207 kg of minerals per car excluding steel and aluminium, against
    about 34 kg for an internal-combustion car.
  - Of that: copper 53, lithium 8.9, nickel 40, manganese 24.5, cobalt
    13.3, graphite 66 and rare earths 0.5.
  - It varies by battery chemistry. NMC811 against LFP changes cobalt and
    nickel by 5-10×.
  - Prior: lithium 0.1-0.15 kg per kWh of NMC cells, 60-75 kWh per car,
    falling with Wright's law.
- **Wind and solar.**
  - Per MW of capacity, minerals excluding steel: offshore wind about 15 t,
    onshore wind about 10 t, solar PV about 6.8 t (IEA 2021). For
    comparison, nuclear is 5.3, coal 2.5 and gas 1.2.
  - Direct-drive permanent-magnet turbines use about 0.6-0.7 t of NdFeB per
    MW, about 30% of which is rare earth. Pavel, C.C., Lacal-Arántegui, R.,
    Marmier, A., Schüler, D., Tzimas, E., Buchert, M., Jenseit, W. &
    Blagoeva, D. (2017). Substitution strategies for reducing the use of
    rare earths in wind turbines. *Resources Policy* 52:349-357,
    https://doi.org/10.1016/j.resourpol.2017.04.010.
  - PV silicon is 2.5-4 g/W now (about 16 in 2004); silver is about 10-20
    mg/W (ITRPV).
- **Data centres and chips.** Data centres need copper of order tens of t
  per MW of IT load, and power. Chips need ultra-pure silicon, gallium (for
  RF and power), germanium (fibre optics, infrared), neon and
  fluoropolymers. Intensities per unit of compute are poorly published.
  Here the prior should be wide and the data (trade of HS 8542 and
  semiconductor-grade inputs) should decide.

## 5. Supply concentration and chokepoints

### From what we hold

- **HHI by country and stage (USGS MYB).** Exploratory numbers for HHI
  (top country, its share), 2000 → 2010 → 2022:

  | product | 2000 | 2010 | 2022 |
  |---|---|---|---|
  | cobalt, mine | 0.17 (Congo 30%) | 0.33 (56%) | 0.54 (73%) |
  | cobalt, refinery | 0.12 (Finland 21%) | 0.23 (China 44%) | 0.65 (China 80%) |
  | rare earths (oxide equivalent) | 0.88 (China 93%) | 0.81 (90%) | 0.53 (71%) |
  | natural graphite | 0.30 (China 51%) | 0.48 (67%) | 0.69 (83%) |
  | primary gallium | | | 0.97 (China 98%); 0.84 in 2015 |
  | ammonia N | 0.10 (China 26%) | 0.13 (31%) | 0.12 (30%) |
  | potash | 0.18 (Canada 34%) | 0.17 (29%) | 0.20 (36%) |

  Rare earths deconcentrated as US, Australian and Myanmar mining grew.
  Separation and magnet-making stayed Chinese, but no table we hold shows
  the separation stage. That is a gap; use MCS narrative tables or the IEA
  data explorer.
- **By A5 cell.** HHI across cells comes from plant and mine locations:
  - SFI (steel, cement, ethylene: coordinates and capacity);
  - GPPD (power, already in plant-cells.csv);
  - OGIM (refineries and LNG);
  - USGS GIS compilations (Africa, Latin America, China, SW Asia,
    Indo-Pacific: mines, smelters and refineries with commodity);
  - MRDS (deposits);
  - Maus et al. 2022 mining polygons.

  MYB vol. III 2024 facilities give capacity by province but no
  coordinates; join them to the GIS compilations by name.

  The A5 pilot (docs/RESEARCH.md) lost to provinces for events, because
  event geocodes are province centroids. Plants are points, so that result
  does not carry over. Cells are the natural unit for a physical cut
  (flood, drought, conflict near the plant). Report HHI at both country
  and cell, and test which predicts output losses better.
- **Export restrictions (OECD inventory, 2009-2024).**
  - About 4,000-6,100 country × HS6 × measure entries a year, rising.
  - Licensing (28k measure-years) and export taxes (18k) dominate.
  - Cobalt ores are restricted by the Congo, Zambia, Madagascar and the
    Philippines throughout, by Indonesia from 2012, and by Russia from
    2023. China drops out after 2017, after the WTO raw-materials rulings.
  - Rare-earth metals: China, India, Brazil and Madagascar from 2009;
    Myanmar from 2012.
  - Lithium carbonate: Argentina and Zimbabwe.
  - Natural graphite: China, India, Zimbabwe.
  - A restriction is an observed raise in the probability of a
    disruption. Its onset year is the useful signal (Indonesia's nickel ore
    ban of 2014/2020 shows in BACI as nickel matte and intermediates moving
    to Indonesian exports).
- **Processing-step concentration.** Compare each stage's HHI, along a
  chain read from the upstreamness ordering:
  - ore: HS 26;
  - intermediate: mattes, hydroxides;
  - refined chemical: HS 28;
  - metal: HS 72-81;
  - component: HS 85.

  China's share rises along the chain in cobalt (mine 0 → refinery 80%),
  graphite (natural 83%, artificial export 52%, anode material not
  separable in HS) and lithium (hydroxide exports 76% in 2024, from a US
  lead of 52% in 2000).

### Chokepoints

- **IMF PortWatch** (IMF with Oxford). Daily transit calls and estimated
  tonnage through 28 chokepoints from AIS.
  - Public ArcGIS FeatureServer (services9.arcgis.com/weJ1QsnbMYJlCHdG/.../Daily_Chokepoints_Data).
  - Checked: 79,744 rows, 2019-01-01 to 2026-10-18, updated weekly.
  - Terms: IMF copyright and usage terms (https://www.imf.org/external/terms.htm).
    Reuse and derivatives are allowed with attribution; commercial reuse
    needs permission from copyright@imf.org. So it is open for this
    research, but like BGS, do not commit raw extracts if the repository
    is public.
  - It starts in 2019, so it cannot drive a causal model before then. Use
    it to check the routed exposures and as a near-real-time monitor.
- **For 1995 onwards**, route BACI's seaborne bilateral flows over a
  shortest-path sea network (e.g. the open searoute network). Count each
  pair's share through Hormuz, Malacca, Bab-el-Mandeb, Suez, Panama,
  Bosporus, Gibraltar and the Cape. Verschuur, J., Koks, E.E. & Hall, J.W.
  (2022). Ports' criticality in international trade and global supply
  chains. *Nature Communications* 13:4351,
  https://doi.org/10.1038/s41467-022-32070-0. This is the
  method and a validation reference. Check against PortWatch for 2019-24
  and against the known roughly 20% of world oil through Hormuz.

## 6. Exploratory numbers

### BACI HS92, 1995-2024

The scan covered 30 years of HS6 world totals and exporter shares. The rise
score is the Theil-Sen slope over 8 years of log(share of world trade value)
and of log(tonnes), taking the smaller of the two. Eligibility: at least 5
ppm of world trade (3-year mean). Products are excluded when one revision
year (2002, 2007, 2012, 2017, 2022) carries more than half of the window's
change and more than 0.5 in log terms. About 3,780 products are ranked each
year. Rank 1 is the fastest riser; "-" means not eligible.

| HS92 | product | 2008 | 2010 | 2012 | 2014 | 2016 | 2018 | 2019 | 2020 | 2022 | 2024 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 282520 | lithium hydroxide | 424 | 684 | 609 | 569 | 24 | 3 | 2 | 2 | 4 | - |
| 283691 | lithium carbonate | 169 | 1263 | 482 | 2106 | 476 | 68 | 17 | 13 | - | - |
| 282200 | cobalt oxides | - | 1311 | 662 | 1719 | 148 | 6 | - | - | - | - |
| 283324 | nickel sulphate | - | 546 | 2929 | 3871 | 2746 | 66 | 14 | 10 | 24 | 11 |
| 850780 | accumulators n.e.s. (Li-ion) | - | - | - | 3274 | 3850 | 3620 | 2939 | 1213 | 7 | 4 |
| 870390 | cars n.e.s. (EV, hybrid) | - | 715 | - | 1077 | 1973 | - | - | - | 2 | 3 |
| 871190 | motorcycles n.e.s. (e-bike) | 2182 | 3046 | 1566 | 973 | 28 | 2 | 3 | 3 | 8 | 16 |
| 850511 | permanent magnets | 2060 | 1582 | 702 | 1233 | 2349 | 2050 | 1345 | 1116 | 134 | 100 |
| 284690 | rare-earth compounds | 609 | 3032 | - | - | - | - | - | - | - | 35 |
| 280461 | polysilicon | 11 | 28 | 12 | 2681 | 3548 | 2998 | 3107 | 3701 | 3523 | 3513 |
| 854140 | PV cells and LEDs | - | 22 | 9 | 523 | 2528 | 2552 | 1809 | 1075 | 616 | 108 |
| 720310 | direct-reduced iron | 199 | 747 | 195 | 1298 | 3719 | 3182 | 1797 | 144 | 12 | 252 |
| 750110 | nickel matte | - | - | - | 3868 | 3938 | 3660 | 3490 | 1474 | 309 | 34 |
| 870332 | diesel cars 1.5-2.5 l | 1101 | 2856 | 2575 | 1837 | 685 | 1174 | 2247 | 3466 | 3609 | 3724 |

Where a code goes ineligible ("-") in the middle of a run, a revision step
carried the window's change (870390 in 2017-20: HS2017 moved the electric
cars into it). That is the filter working as intended; the run picks up
again once the revision year has left the 8-year window.

What these numbers say:

1. **Lithium and cobalt are detected without being told, from 2016-2018,
   not 2010.**
   - Trade in lithium chemicals was flat as a share of world trade until
     2015 (13-25 ppm). It then rose to 423 ppm for the carbonate in 2022.
   - Tonnes: carbonate 67 kt (2010) → 330 kt (2024); hydroxide 18 → 183 kt.
   - The 2010-15 demand came from consumer electronics. It was too small to
     stand out against 3,800 products.
   - A test of "detected from 2010" will fail on trade alone. The
     registered target should be "top 1% by the data year 2018". Earlier
     detection can only come from the diffusion side: EV adoption ×
     intensity, flagged once EV sales become identifiable.
2. **The substitution is visible.** Diesel cars (870332) fall to the bottom
   of the ranking as cars n.e.s. (electric) reach the top. Direct-reduced
   iron (hydrogen-ready steel) rises in 2020-22.
3. **Trade can lose a rising essential when the user starts to make it.**
   Polysilicon led in 2007-12 (rank 11-28) and then fell to the bottom as
   China made its own. Production data (USGS silicon, BGS) and the
   importer's own output must carry it after that.
4. **Precision is low on rise alone.** The top 20 also hold:
   - cotton yarn, rubber mixtures, women's dresses;
   - skipjack tuna (an HS2022 reclassification the filter missed);
   - lignite;
   - roasted iron pyrites (a 2024 jump).

   Of the top 20 in 2018-2024, between 3 and 7 a year are battery, EV or
   e-mobility products. The graph-position and concentration filters are
   needed.
5. **Gallium and germanium are invisible in HS92** (8112.91 lumps them with
   others; 811230 germanium disappears after the 2017 conversion).
   Concentration from USGS production must carry them.

Exporter concentration in BACI corroborates the production numbers:

| product | 2000 | 2024 |
|---|---|---|
| lithium hydroxide | US 52% (HHI 0.31) | China 76% (0.59) |
| lithium carbonate | Chile 61% | Chile 75% (0.60) |
| cobalt ore | Congo 52% | Congo 70% |
| cobalt intermediates | | Congo 63% (from 13% Canada) |
| artificial graphite | | China 52% (from 19% US) |
| nickel matte | | Indonesia 44% (from Canada 43%) |

### OWID power: is the fossil share falling?

World fossil share of electricity:

| year | fossil | nuclear | wind | solar |
|---|---|---|---|---|
| 1990 | 63.7% | 16.7% | | |
| 2000 | 64.7% | | | |
| 2010 | 67.5% | 12.8% | 1.6% | 0.2% |
| 2015 | 66.5% | | | |
| 2020 | 62.1% | | | |
| 2024 | 59.1% | 9.0% | 8.1% | 6.9% |

By country, 1990 → 2023:
- Denmark 97% → 14%;
- the UK 78% → 40%;
- Germany 68% → 44%;
- China 80% → 65%;
- France 11% → 8% (its fall came in the 1980s with nuclear).

So the pooled electricity recipe's source weights must be yearly. Gas's
share of world power stayed at 22-24% while coal's fell from 40% to 34%.

## 7. Design

A yearly causal loop on the lattice (PRINCIPLES 1): products × places
(country, A5 cell) × year. At each year end Y it uses only data with known ≤
Y. The four parts feed each other in this order.

### (a) Recipe coefficients, from priors and data

- **State.** Each recipe row (region r, output j, input i) carries log a_rij
  as a state in a dynamic linear model:
  - a level, with a prior from section 4 where physics sets one, else from
    the latest EXIOBASE table known;
  - a drift, with prior mean from learning curves (intensity per doubling
    of cumulative output, for technologies with diffusion series) and zero
    otherwise;
  - a variance per year, learned per product group and pooled up the place
    tree (PRINCIPLES 2).
- **Observations**, each with its own error variance and known-lag:
  - EXIOBASE monetary shares at Y-3, converted at unit values;
  - the hybrid 2011 physical coefficient (once);
  - UNSD energy flows: refinery intake per product output; non-energy gas
    use by chemicals per tonne of ammonia;
  - USGS stage ratios: ore to refined metal by country;
  - implied apparent-consumption ratios: production + imports - exports of
    input i in r, over production of j in r, where j is i's dominant user.
- **Technology shares as mixtures.** Steel = shares × route recipes (BF/BOF,
  EAF, DRI), from SFI and GEM. Power = shares × source recipes, from
  OWID/EIA. Cars = ICE/EV shares, from BACI codes and diffusion. Most recipe
  drift is a mix shift, and mixes are observed. Per-route coefficients then
  move slowly and stay near physics.
- **Closure.** A Bayesian balance (Rodrigues 2014) or GRAS, with the posterior
  as prior and the year's known margins: production by USGS/BGS/UNSD;
  trade by BACI.
- **Splitting.** Aggregates (C_ONFO, C_ONFM, C_CHMF, C_CHEM, C_ELMA,
  C_MOTO) are split into HS6 sub-nodes when a sub-node passes the
  rise-and-position test in (c). Shares come from trade or production, and
  input coefficients from section 4 priors. The parent's balance is kept.
- **Substitutability σ.** For each critical/substitutable class and product
  group, σ is learned from how relative input quantities respond to
  relative unit values (BACI and UNSD prices) after 1, 2 and 5 years. The
  prior is about 0.1-0.2 at a year (Atalay 2017; Boehm et al. 2019) and
  near 0 for feedstocks.

### (b) An essentiality score from data

The score is expected output at risk over a horizon H:

  R_i(Y, H) = Σ_s P(disruption of i at stage s within H | data ≤ Y) ×
              L_i(s, x, Y + H)

- **L, the loss.** L is the world (and per-place) output lost when stage s
  of i is cut by x, given the recipes projected to Y + H. It is computed by
  supply/outage over the nested recipes, which is Baqaee-Farhi's nonlinear
  importance with σ from (a). Imports of downstream products are allowed to
  reallocate (Moll et al.). Use several cut sizes (x = 0.1, 0.5) because
  the loss is convex.
- **P, the probability of disruption.** It comes from:
  - stage concentration: HHI and top shares by country and cell;
  - export restrictions in force, and onsets of restrictions in the supplying
    countries;
  - the project's own forecasts of conflict, coup and crisis onsets for those
    places: P(disruption) = 1 - Π_c (1 - share_c · p_c);
  - chokepoint shares × chokepoint disruption rates;
  - inventories (days of use) where known.

  The p_c come from models the project already scores. This is "models feed
  models" (PRINCIPLES 3).
- **The forward term.** Projected demand at Y + H is adoption(Y + H) ×
  intensity(Y + H) from the diffusion model and the learning curves. A
  product whose user is diffusing gains weight before its trade rises.
- **Reporting.** Report R_i in output units, both raw and divided by world
  output. There are no hand weights: importance and risk multiply because
  that is what an expectation is. Each component is kept, so the leading
  term (concentration, restriction, chokepoint or loss) can be read off,
  in the style of the principal contradiction (PRINCIPLES 4).

### (c) Flagging a rising essential

A product is flagged at Y when all four hold:

1. Its rise score is in the top q% (posterior probability that the 8-year
   Theil-Sen slope of both value share and tonnes exceeds zero by margin m,
   with the revision filter).
2. It has a link, from recipes or a proposed edge, to a node whose demand
   is rising or large.
3. Its stage concentration is above a level, or rising.
4. Its R_i has risen against its own past (PRINCIPLES 6: within, not
   level).

q, m and the concentration level are learned on the design years (to
2002, per PRINCIPLES 9) as far as the data allow. The design years are
thin for BACI (1995-2002), so q and m are set on 1995-2010 with no peeking
past 2010, and the 2011+ flags are the registered test.

Proposed edges are checked against a placebo. The candidate input's
importers are compared with the exporters of an unrelated rising product,
or of the same product in another period.

### (d) Mapping the chain to places

For each flagged product, the stages are read from upstreamness and the HS
chapter: ore (26), intermediate (mattes, hydroxides), chemical (28), metal
(72-81), component (85), final (87, 85).

- **Extraction** comes from USGS/BGS production by country, then to cells
  by mines, deposits and polygons (USGS GIS, MRDS, Maus).
- **Processing** comes from USGS refinery forms and facilities, SFI and
  GEM.
- **Trade** comes from BACI bilateral flows by stage, routed by sea for
  chokepoints.
- **Policy** comes from OECD restrictions by country and HS6.

The result is a per-product chain graph with country and cell shares per
stage and the stage HHI. It feeds bnm.supply as sub-nodes and outages as
cell-level cuts.

## 8. Implementation steps (ranked), tests and validation targets

Each step is test-first. Registered targets go into docs/PHASE-CHANGES.md
before any test year is scored (PRINCIPLES 9).

1. **BACI panel** (scripts/baci.py → data/derived/supply/hs6.csv). HS6 ×
   year world value, tonnes, unit value, exporter and importer HHI, top
   shares, with known = year + 2. Also a revision-step flag per code-year
   and the HS4 heading sums.
   - Tests: the world total matches the sum of BACI flows. 854211/854219 are
     flagged at 2012 and 2017. 850780's 2024 tonnes are 3.8 Mt and its
     value share 5,365 ppm.
2. **Rise detector** (bnm.essentials/rise). Theil-Sen of the two logs,
   minimum, eligibility, revision filter, rank and posterior probability.
   - Tests: no read after Y (smoke). Ranks reproduce section 6 for the
     listed codes.
   - Registered targets:
     - lithium hydroxide in the top 1% by data year 2018 (observed rank 3);
     - lithium carbonate in the top 1% by 2019 (17);
     - cobalt oxides in the top 1% in 2018 (6);
     - Li-ion accumulators and cars n.e.s. in the top 1% by 2022 (7 and 2).
   - The aim "lithium rising from 2010" is recorded as not met by trade
     alone.
3. **Stage concentration** (scripts/minerals.py →
   data/derived/supply/production-stage.csv). USGS MYB 1990-2022
   deduplicated by latest edition, MCS 2022-2026, BGS 1970-2024 for gaps.
   Mine against refinery or plant, by country, with HHI.
   - Tests: Congo's 2022 share of mined cobalt is 0.73 and China's of
     refined cobalt 0.80; China's rare earths in 2005 are 0.97 and gallium
     in 2022 0.98 (within 0.02).
   - Then to cells: GIS compilations, SFI and MYB facilities joined by
     name. The test is that cell shares sum to the country.
4. **Concordance and splits.** HS → CPA → EXIOBASE through
   Classifications_v_3_3_18.xlsx, and HS6 sub-nodes for C_ONFO, C_ONFM,
   C_CHMF, C_CHEM, C_ELMA and C_MOTO.
   - Tests: sub-nodes sum to the parent in every region-year, and balances
     still close (median residual below 1e-6).
5. **Priors and the recipe filter** (resources/bnm/recipe-priors.edn; bnm.recipes).
   The section 4 table as distributions, and the DLM per coefficient with
   mixtures for steel routes, power sources and car drivetrains.
   - Tests:
     - the posterior gas per t of NH3 stays within 0.5-0.9 in gas-route
       regions;
     - the learned steel ore coefficient stays within 1.4-1.7;
     - extreme inputs (zero ore, tenfold gas) give sane outputs (Barlas,
       PRINCIPLES 8).
   - Registered: the 2011 recipes plus learned drift predict the 2022
     EXIOBASE shares better than the 2011 shares held constant (mean
     absolute log error). Only if they do, download the remaining
     EXIOBASE years.
   - Result (October 2026): not met. Learned drift predicted 2022 worse
     than no drift (0.345 log per cell, worse in every recipe but one), so
     the remaining years were not downloaded and the filter is a local
     level without trend; only cited technology trajectories drift (PV
     silicon and silver, battery cobalt, polysilicon electricity). Ammonia
     gas is observed as UNSD non-energy gas over USGS ammonia output, not
     EXIOBASE value shares (which route fertiliser through imported
     ammonia); the gas-route posterior sits in 0.55-0.75 t/t in 23 of 26
     regions with data.
6. **Technology demand** (extends bnm.technology). Add EV share (from
   BACI 870390/850780, later HS17 870380), PV and wind capacity
   (EIA/OWID) and DRI share (GEM) as diffusion stocks. Implied mineral
   demand = adoption × intensity prior with a learning exponent.
   - Test: the year-on-year growth of implied lithium demand correlates
     with the growth of USGS lithium production over 2012-2022, beating a
     placebo technology (e.g. mobile subscriptions).
   - Built (bnm.tech-demand, October 2026): EVs from IEA sales via OWID
     (BEV, PHEV for batteries), wind from IRENA split onshore and offshore,
     PV from EIA capacity; intensities from bnm.recipes. EV battery
     materials now agree within 6% with the separate Li-ion trade route
     (2021-23); 5-year projections hold the recent slope unless the
     logistic ceiling is identified and half reached (a rule chosen after
     one failure). The registered lithium test could not run: USGS
     lithium mine output exists only from 2021.
7. **Edge proposal.** For each rising candidate, the overlap of importers
   with exporters of rising downstream products, weighed against a placebo.
   - Registered targets: lithium hydroxide's top proposed downstream is
     850780 (or 850760 in HS12); cobalt oxides' and nickel sulphate's is
     850780; polysilicon's is 854140. Each beats its placebo.
   - Result (bnm.essentials/edges, tools/bnm/edges_check.clj, HS92
     2008-2024 and HS12 2019-2024, 39 like-sized placebos, p <= 0.05):
     not met. No target is a top proposal in a year its input is a
     candidate. Polysilicon -> 854140 is proposed in 2010 and 2012 (rank 5
     and 2, top in 2013); Li-ion -> 870390 in 2021-22 (rank 6 and 5);
     cobalt oxides -> 850780 in 2018 (rank 56). Lithium hydroxide never
     proposes 850780 (rank 57-147), nor 850760 in HS12. About 5% of pairs
     are proposed, the placebo's false-positive rate. Against recipe links
     (median share_2011 >= 0.005) the proposals' precision is 0.248 against
     a base of 0.213 (lift 1.16, from 2012-18; below 1 in 2008-11 and
     2019-22), and the top proposals are mostly noise (lithium hydroxide's
     is 370120 or 284190). Importer-exporter overlap of big trading
     countries does not single out recipes; the edges stay with the
     recipes and the sub-node parents.
   - Bug hunt before accepting that (PRINCIPLES 10). Lithium carbonate ->
     850780 in 2019 traced end to end: the overlap is the carbonate's
     importers against 850780's exporters (Korea, China and Japan carry
     0.47 of the 0.61), the windows are 2012-14 against 2017-19, and
     850780 is in the downstream set. It ranks 33rd because 32 outputs
     exported by the same three countries score higher (tankers 890120
     first); its overlap fell from the early window (-0.02), so it is not
     proposed. Its 39 placebos are unrelated goods of the same value
     (yarn, nuts, horses, lamps; overlap 0.12-0.56), not substitutes, so
     the test is not too strict. One bug: the downstream set dropped
     products for a revision step in tonnes although it ranks value share
     alone (850780 has a tonnes jump at 2007 and was out 2008-13). Fixed
     (the set now filters on the value-share step); it changes little:
     HS92 precision 0.248 -> 0.243 against a base of 0.213 -> 0.209, lift
     1.16 either way, targets as before. recipes.csv has no diagonal, so
     a pair within one parent (lithium hydroxide -> cathode salts 284190,
     both C_CHEM) can never count; leaving those pairs out leaves the lift
     at 1.16. The negative stands.
   - Robustness. Placebos without the candidate's whole HS2 chapter:
     lift 1.16, targets unchanged. Specialisation instead of value (each
     country's flow over its total trade, countries under 1 bn USD out;
     chosen after the failure above, PRINCIPLES 9): Li-ion -> 870390
     becomes the top proposal in 2022-23 and lithium hydroxide proposes
     850780 in 2017 (rank 8), but precision against the recipes falls
     below base (0.185 vs 0.209, lift 0.88), so it is not adopted and its
     hit is not evidence.
   - At the sub-node level (pairs of sub-node codes, known when the
     recipe priors link them: li-ion from lithium, cobalt, nickel and
     graphite, cars from batteries and copper, PV from polysilicon and
     copper), HS92 proposals hit 8 of 61 against a base of 0.038 (lift
     3.4, p 0.001; 7 of 56 before the fix), but HS12 hits 3 of 42 (lift
     1.6, p 0.29) and no target input's top proposal is a recipe link.
     Lithium carbonate's and hydroxide's top outputs include cathode
     salts 284190, accumulator parts 850790 and fluoride salts 282690
     (LiPF6), one step upstream of the cell, which the targets (chemical
     -> cell) skip; that is a reading of the targets, not a pass.
8. **Essentiality R_i(Y, H).** Outage loss (bnm.supply, σ learned, import
   reallocation added) × disruption probability (stage concentration, OECD
   restrictions, the project's onset forecasts for supplying places,
   chokepoints).
   - Registered targets:
     - natural gas ranks among the top inputs of N fertiliser in every
       gas-route region, with exposure ≥ 0.3 (Germany 0.64 t/t). Coal ranks
       there in China.
     - the fossil share of the pooled power recipe tracks OWID (world 67.5%
       in 2010, 59.1% in 2024; Denmark, the UK and Germany fall by more than
       20 points), and fossil fuels' R_i for power falls in those countries.
     - rare earths are flagged by concentration, from 2000, before the
       2010-11 quota episode. Gallium is flagged by concentration (HHI >
       0.8) and not by rise.
     - precision: among the 20 highest-R_i rising products a year, the share
       on the contemporaneous EU CRM list (held out) beats a random 20
       drawn from the eligible products, and beats the rise score alone.
     - back-test: high-R_i products see more frequent export restrictions
       (OECD, onsets after Y) and larger unit-value spikes in Y+1..Y+3 than
       low-R_i ones, against a placebo shuffling R_i across products within
       HS chapter.
   - Operational form (bead bnm-1pq.9, written before any R_i was
     computed; bnm.essentiality, tools/bnm/essentiality_run.clj). Products
     are the HS92 codes the rise score ranks; R_i is the world's steady
     R in value added, at known year K (BACI data year K - 2), from 2014
     (EXIOBASE 2011 is known 2014, so earlier years would read the
     structure before it was published).
     - Lithium (282520, 283691), cobalt (260500, 282200, 810510) and rare
       earths (280530, 284610, 284690) rank high: each material's best
       code in the top 10% of R_i in at least one of K = 2020-2022 (data
       2018-2020).
     - Gas for European fertiliser: in DE, FR, IT, NL, PL and GB, of the
       inputs of N fertiliser (C_NFER) with a value share of at least 1%
       or critical, a cut of half the region's use of natural gas
       (C_GASE) costs C_NFER more than a cut of any other, and at least
       0.3 of the cut (bnm.supply outage, steady).
     - Precision: the 20 highest-R_i of the rising products (the rise
       score's top 10%) at K = 2023 (data 2021) against the EU 2023 list
       and at K = 2022 (data 2020) against the USGS 2022 list, broad
       mapping (tools/bnm/essentials_check.clj; strict reported too):
       beats a random 20 of the ranked products (hypergeometric p <=
       0.05) and the rise score's own top 20 (higher precision).
     - Back-test, descriptive: K = 2014-2021, top decile of R_i against
       the bottom half; new OECD restrictions on the code in K+1..K+3
       (codes the inventory covers), and the largest rise in log world
       unit value over data years Y+1..Y+3 (all codes); 999 shuffles of
       R_i within HS2 chapter as the placebo.
   - Result (October 2026; bnm.essentiality, tools/bnm/essentiality_run.clj;
     stocks essential/<hs6>-risk, -risk-3m, -risk-12m, -risk-rank, -p,
     -flag and the four signals in data/derived/stocks/essential.csv, kUSD
     of value added a year; WLD for every ranked code, per importing
     region for the sub-nodes, the energy codes and each year's top 50).
     About 3,800 HS92 codes a year, K = 2014-2026. Construction:
     - P per supplier: the union of the t44-world-norel yearly hazards of
       conflict, war, coup and violence onsets (China 0.10 at K = 2022,
       Congo 0.55, Russia 0.17, Indonesia 0.17, the median 0.07) and 0.2 x
       the restriction hazard (tightening 0.089 a year on a code under an
       OECD measure, new listings 0.003 per unrestricted code-year).
       Straits: Gamma(0.5, 25) closures a strait-year, updated by the four
       PortWatch strait events (Red Sea 2023 on Suez and Bab-el-Mandeb,
       Panama 2023, Hormuz 2026): 0.045 a year for those four at K = 2026,
       0.015 for the rest.
     - Loss: bnm.supply outages of every region's use of each EXIOBASE
       product at once (cuts 0.1 and 0.5; steady, and months 3 and 12
       with the data stocks; data/derived/supply/essentiality-loss.csv,
       an hour of outages), as value added lost per unit of use lost.
       Cheap critical inputs carry huge multipliers: sub-bituminous coal
       350, lignite 300, chemical minerals 236, bauxite 145, iron ore 94,
       gas 50, chemicals n.e.c. 5, garments 0.2. Reallocation leaves only
       suppliers with more than e / (x + e) of world exports in the steady
       R (0.375 at x = 0.5, e = 0.3): the steady ranking is a ranking of
       concentrated codes times their parent's multiplier.
     - Magnitudes are not credible: Indonesian "lignite" (270210, really
       low-rank coal for Chinese and Indian power) carries 443 bn USD of
       expected loss a year at K = 2026, because it takes the world
       lignite multiplier (German and Polish lignite power, no fuel
       switching in a critical recipe) and Indonesia's onset hazard. One
       multiplier per product for every region (a cut per region would be
       6,000 outages each way) is the main simplification; the ranks are
       what the tests read.
     - Top R (steady), K = 2023: lignite (Indonesia), iron ore
       (Australia), sub-bituminous coal (Indonesia), soybeans (Brazil),
       anthracite (Russia), petroleum coke (USA), feldspar (Turkey), cobalt
       oxides (Congo, rank 8), magnesia (China), asbestos (Russia), borates
       (Turkey), toys (China), ferro-nickel (Indonesia). K = 2026: lignite,
       iron ore, bauxite (Guinea), soybeans, propane (USA), computer
       storage units and mobile phones (China), petroleum coke, Li-ion
       accumulators (China, rank 9), ferro-nickel and unwrought nickel
       alloys (Indonesia), natural uranium (Kazakhstan), unwrought cobalt
       (Congo).
     - Lithium and cobalt: met. Lithium hydroxide is in the top 3-4% at K =
       2021-2022 (rank 156 and 121 of about 3,850), lithium carbonate 6%
       at 2020; cobalt oxides rank 11 at 2020, cobalt ore 31 at 2022.
     - Rare earths: not met. Cerium compounds (284610) rank about 1,000
       (26-29%), rare-earth metals (280530) 1,789 at 2022, other compounds
       (284690) unranked by the rise score in those years: China's export
       share stays under the reallocation threshold, and C_CHEM's
       multiplier is 5. Their concentration signal is on from 2000 (mine
       HHI 0.83 or more from K = 2000, 0.97 in 2011), before the quota
       episode; gallium's refinery HHI is above 0.8 from K = 2019 (0.61-0.76
       in 2013-18) and 811291 never rises (rise percentile 0.85-0.99):
       flagged by concentration and not by rise, as registered, from 2019.
     - Gas for European fertiliser: half of each region's gas cut passes
       whole to N fertiliser in all six regions (1.00 of the cut), but gas
       is strictly first only in France and Great Britain: in Germany it
       ties with stone (lime for calcium ammonium nitrate, 0.28 t/t), in
       the Netherlands and Poland with stone, crude oil and chemicals, in
       Italy with crude oil. Not met as written. Crude oil's tie is
       suspect: bnm.supply classifies C_COIL critical for C_NFER in IT, NL
       and PL on its 2011 value share (0.06-0.10) although its physical
       coefficient is 0.
     - Precision: beats random, not rise; not met. EU 2023 (K = 2023, broad):
       2 of the 20 highest-R rising codes listed (cobalt oxides, natural
       graphite), against a base of 0.014 (p 0.03), and the rise top 20 has
       4. USGS 2022 (K = 2022): 2 (ferro-nickel, natural graphite), p 0.04,
       against 3 for rise alone. Strict mapping: 2 and 1 against 4 and 3.
       Descriptively, over K = 2016-2026 (the lists are not held out from
       the later years in the same way) R beats rise in 6 years of 11 on
       the EU list (ties 3, losses 2) and in 3 on the USGS list (ties 2,
       losses 6).
     - Back-test (K 2014-2021, 30,606 product-years): new OECD measures in
       K+1..K+3 on 51% of the top decile's covered codes against 43% of the
       bottom half's (2,404 covered product-years), a difference of 0.084
       that shuffling R within chapter reproduces (placebo mean 0.081, p
       0.44); unit-value spikes 0.098 against 0.092 (placebo 0.015, p
       0.99). R carries no information on later restrictions or price
       spikes beyond its HS chapter.
     - Sensitivity (K 2022-23): the cut x = 0.1 instead of 0.5 moves the
       ranking a lot (Spearman 0.56-0.57; with the threshold e / (x + e)
       only suppliers above 75% count); without the restriction term
       nothing moves (0.9999+); without straits 0.93-0.94.
     - The four-signal flag (rise top 10%; linked; exporter or stage HHI
       0.25 or up 0.05 in five years; R above its mean of the last three
       years) flags 47-87 codes a year in K = 2017-2026. Among them:
       lithium carbonate (2020), natural graphite (2023-25), permanent
       magnets (2023-25), lithium hydroxide (2024), Li-ion accumulators
       (2024-26), PV cells, synthetic graphite (2025), nickel mattes
       (2025-26), ferro-nickel (2022-26); and as many spices, furniture,
       lamps and plastics. Signal 2 (linked) is true for 85% of the codes
       and decides little; q and the HHI level were set a priori, not
       learned on 1995-2010.
   - Corrected record (October 2026, after the result above; the result
     stands as it was run). The product graph changed after the loss
     tables were computed: power stations defer their upkeep and stocks
     are capped and filled (2fa5f97, 6f8e353), and the hybrid table's
     physical flows decide criticality (da0cc6c, 3,469 recipe rows
     turned substitutable). The tables were rerun on that graph (about
     two hours), then the report, first with the code as registered and
     then with the loss weighting below.
     - Multipliers fall but stay large for cheap critical inputs (steady,
       cut 0.1): sub-bituminous coal 351 to 267, lignite 300 to 215,
       chemical minerals 236 to 217, coke-oven coke 216 to 187, bauxite
       145 to 123, iron ore 94 to 83, gas 50 to 45; the products crude
       had wrongly reached fall most (refinery gas 256 to 62, other
       non-ferrous ores 25 to 2, oilseeds 44 to 13, biodiesel 155 to 10).
     - Hand check, Indonesian "lignite" 270210 at K = 2026 (registered
       tables): Indonesia has 0.867 of world exports, reallocation 0.092,
       P 0.241. China imports 10.5 bn USD of the code; its EXIOBASE
       lignite use is EUR 1.4 M, all imported, so the trade data's use
       is the 10.5 bn: shortfall 0.5 x 0.908 x 10.5 x 0.867 = 4.15 bn, f
       0.39, lambda 418, loss 1.7 trillion USD, x P = 419 bn of the
       code's 443. The arithmetic is as specified; the specification was
       wrong. The code in China is Indonesian low-rank coal for power,
       which EXIOBASE files under the other coals, and a cut of China's
       lignite in the loss model costs nothing (one-region outages,
       cut 0.1: China 0, Germany lambda 72, Poland 445, the US 454).
     - Fixed (d102b45): a region's loss is lambda(f) x f x its use of the
       parent in the loss model (EXIOBASE 2011, at 1.392 USD per EUR), f
       still the shortfall over its use in the trade data: the region's
       share of the world's use times the world's loss at f, so the
       regions' losses at one cut add to the world's. A shortfall above
       a region's use costs no more than all of it (it had cost lambda x
       the shortfall). Ranks barely move (Spearman 0.999 with the old
       weighting at K = 2022-23); the top changes. Lignite falls from
       307 bn USD at K = 2026 (rerun, old weighting) to 17 bn (Czechia,
       Russia: the proportional supplier split gives Indonesia 87% of
       every importer's lignite, Czech imports from Germany included);
       Indonesian sub-bituminous coal from 153 to 8 at K = 2023. Top R,
       K = 2026: bauxite (Guinea, 65 bn), iron ore (Australia, 41),
       lignite, propane (USA), phones (China), petroleum coke residues
       (Indonesia), ferro-nickel, computer units, soybeans, Li-ion
       accumulators (China, rank 10).
     - Why the multipliers are large, not fixed here: bnm.supply makes
       each coal a power station burns at 0.1 TJ per TJ or more critical
       on its own, so a cut of 10% of the US's lignite (5% of its coal
       heat) stops 10% of US coal power, and the electricity pool then
       passes it to everything critical on electricity. Pooling a
       station's coals (a share-weighted mean, as electricity by source
       is pooled) gives lambda 7 for the US's lignite, 11 for Poland's,
       14 for Germany's, and 180 for US sub-bituminous coal against 293:
       the rest is the electricity cascade with no other source ramping
       up, a modelling choice. The pooling is a change to bnm.supply's
       rule and every outage that reads it, so it is left for a decision.
       Value added is not double counted (each node's own, output less
       inputs) and the world loss stays below world value added (EUR 63
       trillion; the largest, a half cut of crude oil, 19%).
     - Targets, registered -> rerun (old weighting) -> fixed:
       lithium met -> met (hydroxide 138 and 113 at 2021-22) -> met (125,
       102); cobalt met -> met (oxides 11 at 2020, ore 169 at 2022) ->
       met (12, 481); rare earths not met -> not met (cerium compounds
       1,003-1,107) -> not met (988-1,108); precision unchanged in both
       (EU 2 against rise 4, p 0.03; USGS 2 against 3, p 0.04): beats
       random, not rise.
     - Gas for European fertiliser after da0cc6c: crude's ties are gone
       (crude is no longer critical where its physical flow is 0), so gas
       is strictly first in France, Italy and Great Britain (1.00 of the
       cut). It still ties with stone in Germany and with stone and
       chemicals in the Netherlands and Poland: all are critical, and a
       Leontief recipe passes a half cut of any critical input whole.
       Not met (3 of 6, from 2 of 6); under Leontief the target can only
       be met where gas is the sole critical input.
     - Rare earths: the codes map as they should (284610, 284690 and
       280530 to C_CHEM, magnets 850511 to C_FABM), and a sub-node's cut
       is already its parent's cut at its share of the parent's use,
       which is what wiring it through bnm.concordance/split-parent would
       give. The miss has two causes. Trade shares: China has 0.17 of
       cerium compound exports at K = 2022 (Japan 0.39, France 0.21),
       0.22 of rare-earth metals (Vietnam 0.32) and 0.17 of other
       compounds (Myanmar 0.30), so every supplier is under the
       reallocation threshold and fully replaced; China's separation
       share (0.85 and more) shows in stages.csv, not in exports, and the
       Japanese and French processors' feed from China is not in the
       graph. And the parent: C_CHEM's multiplier is 5, the average of
       chemicals n.e.c., with no magnet-motor-turbine chain under it.
       Fixing either needs a sub-node with its own recipe and users
       (section 7a's splitting, coefficients from section 4) and supply
       shares from production, not a change to R's arithmetic.
     - Back-test after the rerun: restrictions in K+1..K+3 on 54% of the
       top decile's covered codes against 43% (difference 0.113, placebo
       0.085, p 0.18), with the fix 0.102 against 0.072 (p 0.18);
       unit-value spikes 0.005 and 0.000 above the bottom half (placebo
       0.014 and 0.009, p 0.99). R still carries nothing beyond its HS
       chapter.
   - Bilateral supplier slices (bead bnm-1pq.30, after the record above).
     A region's slice of a supplier had been the supplier's share of
     world exports. It is now what the region's countries bought from
     the supplier in BACI (scripts/essentiality-trade.py from
     hs6-essentials, read as known by K), so a region's slices add up to
     its imports. Codes without bilateral flows fall back to world shares
     and are flagged in the report. scripts/baci.py now also keeps
     bilaterally the 195 HS92 codes that ranked in the top 100 at any K
     under world shares (RANKED_CODES; its other outputs are byte for
     byte unchanged). That puts 241-275 codes a year on bilateral
     slices, including every code in each year's top 50. The code list
     was chosen from rankings at every K, so it is a look-ahead in
     coverage, though not in the data.
     - Slices alone, on the earlier loss tables: the ranks barely move
       (Spearman 0.99999 at K = 2022-23), but the top changes. Lignite
       drops from rank 3 (16.7 bn USD, Czechia 5.9 and Russia 4.9) to
       rank 22 (1.8 bn) at K = 2026, and from 3 to 36 at K = 2023. Czech
       imports come from Germany, and what Indonesia ships goes mostly
       to the rest of Asia (WA). Bauxite falls from 65 to 13 bn at K =
       2026 (rank 1 to 3), because Guinea's ore goes to China.
       Anthracite rises from 39 to 61 bn at K = 2023, because Russia
       ships it to heavy users. Iron ore falls from 40 to 30 bn.
     - Final rerun. It uses the slices plus two changed inputs. First,
       the graph: the hybrid overrides (3df0389, gas into N fertiliser in
       8 regions, electricity in 138 mining recipes) and the pooled
       power-station coals (ad1f21e), with the loss tables recomputed (41
       minutes). Second, technology demand (8fb630b, IEA EV sales and
       IRENA's onshore/offshore wind split; world rare-earths growth5
       0.32 to 0.51). Only the sign of growth5 enters the flag, so the
       second leaves R and the flags as they were. Steady multipliers
       at cut 0.1: lignite 215 to 25, sub-bituminous coal 267 to 86,
       anthracite 110 to 91. Coke, bauxite, iron ore and gas are
       unchanged. Lignite ends at rank 137 at K = 2026 (0.21 bn) and
       197 at K = 2023. Sub-bituminous coal goes from rank 5 to 27 at
       K = 2023. Top R, K = 2026: iron ore (Australia, 30 bn), propane
       (USA, 16), bauxite (Guinea, 13), computer units and phones
       (China), ferro-nickel (Indonesia), Li-ion accumulators (China,
       rank 7), soybeans, petroleum coke, granite (India). K = 2023:
       anthracite (Russia, 51 bn), iron ore, granite, soybeans, marble.
     - Targets, registered -> fixed (above) -> slices and the final
       inputs:
       - lithium: met -> met -> met (hydroxide 115 and 101 at 2021-22,
         carbonate 218 at 2020).
       - cobalt: met -> met -> met (oxides 12 at 2020, ore 495 at 2022).
       - rare earths: not met -> not met -> not met (cerium compounds
         992-1,107).
       - precision: unchanged (EU 2 against rise 4, p 0.03; USGS 2
         against 3, p 0.04). R beats random but not rise.
       - back-test: restrictions 0.543 against 0.428 (difference 0.115,
         placebo 0.074, p 0.10); unit-value spikes 0.000 (p 0.98). Still
         nothing beyond the chapter.
       - gas for European fertiliser: as after da0cc6c, 3 of 6 (FR, IT
         and GB strictly first; DE ties with stone; NL and PL tie with
         stone and chemicals). The overrides leave the registered six
         as they were. Outside them, gas is strictly first in LT,
         WA and WL and ties with stone in TR.
9. **Chokepoints.** Route BACI bilateral seaborne flows on a sea network
   and compute each chokepoint's share by product and year.
   - Tests: Hormuz carries about 20% of world crude and LNG in 2019-23.
     PortWatch tonnage through each chokepoint correlates with the routed
     estimate (2019-2024).
   - Result (October 2026; scripts/routes.py, bnm.chokepoints,
     tools/bnm/chokepoints_run.clj; stocks chokepoint/<strait>-<product>
     in data/derived/stocks/chokepoints.csv). MARNET with 20 of PortWatch's
     28 straits resolved (Kerch added: one Azov lane, gate across the
     narrows); landlocked countries through a transit port; pairs with a
     land border or a short land route are non-maritime, except pairs
     whose land route no corridor carries (Turkey with Egypt, Israel and
     Ukraine, Finland-Estonia, China with Bangladesh and Thailand ...).
     Bulk and general cargo: port weights from PortWatch per ship class
     with a 2,000 km distance decay across port pairs. Tankers: export
     ports by PortWatch tanker export tonnes per year (Kozmino added from
     Transneft, Valdez dropped as domestic), a gravity model on each year's
     buyers deciding which port serves whom, and the decay only among the
     importer's ports. Of 2019-23 BACI pairs with flows, 35,338 of 38,141
     are seaborne and routed (72.0% of value), 1,741 land (28.0%), 1,062
     small ones without a port (0.01%).
     - US tankers to East Asia, before the tonnage weights: 93% from
       Valdez across the Pacific (the decay preferred Alaska's 6% of calls
       to the Gulf Coast, 11,000 km further), Panama 0.07 for Japan, Korea
       and China. After: all from the Gulf Coast, Panama 1.00 for every
       tanker cargo (bnm-1pq.10).
     - Ship size (bnm-1pq.26). A laden VLCC fits neither the Neopanamax
       locks (Panama Canal Authority: beam 51.25 m, draught 15.2 m, about
       120,000 dwt) nor Suez (Suez Canal Authority: 66 ft, 20.1 m), and
       avoids the shallow Sunda strait; a Capesize fits Suez but not
       Panama. No vessel mix by route is on disk, so the rule: crude port
       pairs of 8,000 km or more go VLCC (no Panama, Suez or Sunda), iron
       ore and coal Capesize (no Panama), unless a port is behind the
       Danish straits, the Bosporus or Kerch (Aframax and Suezmax ports),
       set before any route was computed. SUMED is not modelled: long-haul
       VLCC crude to Europe goes round the Cape, over-routing the Cape by
       what SUMED carries. Oil products, LNG and HS chapters 26 and 27 keep
       the ordinary lanes.
       - US crude to Japan, Korea and China: Panama 1.00, Cape 0.00 before;
         Panama 0.00, Cape 1.00 after, then Malacca (all years).
       - Iron ore to China, Capesize: Brazil Panama 0.36 and Cape 0.64
         before, Panama 0.00 and Cape 1.00 after; Australia unchanged
         (Lombok 0.83, Makassar 0.71; no canal on the way). China's iron
         ore imports in 2023: Lombok 0.55, Sunda 0.21, Cape 0.19, Panama 0.
       - Saudi crude to the Netherlands: the Gulf terminals' VLCCs (0.34)
         round the Cape, Yanbu's Suezmaxes keep Suez (0.66).
       - Crude Malacca shares rise where US crude went by Panama: Korea
         0.74 to 0.91, China 0.60 to 0.83, Taiwan 0.66 to 0.90.
     - Gulf bypasses, from PortWatch tonnes by year: the Red Sea ports
       (Yanbu, Rabigh, Jeddah) 18-25% of Saudi tanker tonnes in 2019-24,
       Fujairah 35-43% of the UAE's (a storage hub too: its export tonnes
       overstate the pipeline crude; EIA gives the line's 1.8 mb/d
       capacity, not its use). Routed Hormuz share of Saudi tankers to
       Korea 0.90-0.93 by year (Yanbu's cargo goes mostly to Europe), UAE
       0.56-0.65 (before: 0.89 and 0.51, fixed).
     - Hormuz, 2019-23: 31-34% of world crude imports by value (the "20%" is
       of world oil use; of seaborne crude trade it is about 40%), 20% of
       LNG. Met for LNG; crude is consistent once the base is trade.
     - Hormuz share of crude imports, 2019-23: Japan 0.71-0.77 (before
       0.69-0.71), Korea 0.55-0.65 (0.53-0.62), India 0.44-0.55, China
       0.35-0.44. The Korean 70-85% target has no source in this repo and
       is probably the Middle East share of Korea's crude imports (around
       70% in recent years in EIA's country brief), which is not Hormuz
       transit: it counts Oman (outside Hormuz) and Saudi crude loaded at
       Yanbu and UAE crude at Fujairah. BACI's Gulf-supplier share of Korea's crude is
       0.59-0.72 in 2019-23 (US, Kazakh, Mexican and Australian crude rose),
       an upper bound; routing puts 0.90-0.92 of it through Hormuz.
       Restated test: the routed Hormuz share is the Gulf-supplier share
       times one minus the bypass share. Korea meets that by construction;
       there is no independent Korean Hormuz-transit figure to check it
       against, and routing is not tuned to the old target.
     - Malacca, crude: Japan 0.96, Korea 0.91, Taiwan 0.90, China 0.83.
       LNG splits between Malacca and Lombok/Makassar (Australian LNG).
     - Russia-China: a land pair, but LNG is shipped (any pair with LNG
       and ports on both sides) and crude beyond the ESPO spur and the
       Kazakh transit is shipped (44% in 2019, 60% in 2023), from Kozmino
       and, after 2022, the Baltic. Coal, oil products, iron ore, timber and
       fertiliser stay non-maritime (rail and Pacific ports both; no
       source for the split), as do Canadian crude to the US and Yamal's
       summer cargoes over the Northern Sea Route (Arctic passages removed).
     - Look-ahead: tanker ports use 2019's PortWatch tonnes for data years
       1995-2018, exposure years 1997-2020 (US crude exports were near zero
       before the 2015 ban ended; Yanbu's share held at 2019's); bulk and
       general port weights and all borders and lanes are fixed for every
       year. Russian ports to Japan and Korea: Sakhalin's terminals are
       missing from PortWatch, so 21-38% of those tankers are routed from
       the Baltic or the Black Sea.
     - Unresolved: Bohai, Ombai, Torres, Balabac, Mindoro, Yucatan, Windward
       and Mona. None is the only route for a major importer's essentials
       (Bohai is inside China's coastal trade; the Caribbean passages have
       alternatives a day away; the four Indonesian and Philippine ones are
       bypasses of resolved straits). Kerch carries 9 Mt of routed trade
       (PortWatch capacity 27 Mt dwt): Rostov, Azov, Taganrog, Mariupol and
       Berdyansk grain, steel and oil products.
     - Asia to Europe, container chapters: Suez and Bab-el-Mandeb 1.00.
     - PortWatch: routed tonnes against observed capacity across 19
       straits, Spearman 0.73 (Pearson of logs 0.80; 0.72 and 0.78 with
       crude, ore and coal on the ordinary tanker and bulk lanes). Panama's
       routed tonnes fall from 506 to 292 Mt (observed 318 Mt dwt), the
       Cape's rise from 807 to 1,140 Mt (1,050), Suez falls from 1,350 to
       1,140 Mt (1,010). Oresund is over-routed
       (the gate is the whole Kattegat) and Luzon under. Observed shocks
       line up with the routed exposure: Bab-el-Mandeb -56% and Suez -46%
       transits in 2024 against early 2023, the Cape +78% (routed: 15% of
       world trade by value through each, a Cape detour of about 5,800 km
       for China-Netherlands); Panama -30% in the 2023-24 drought; Suez
       -85% during the Ever Given week.
10. **Cells.** Cell-level HHI and cell cuts in supply/outage.
    - Registered (open): cell-level concentration predicts the output
      loss from cell-located shocks better than country-level. If it does
      not, keep countries (as in the A5 pilot). Design, to be registered
      before any outcome is read:
      - Shocks: located, dated events chosen without looking at output:
        floods (Global Flood Database footprints), tropical cyclones
        (IBTrACS tracks, hurricane-force wind radius), earthquakes (USGS
        ShakeMap MMI 7 or more) and conflict onsets (UCDP GED events within
        25 km of a plant, a cell's first in five years), 1995-2022. Each
        shock is a set of cells: those its footprint covers.
      - Units: (key, country, year) for the keys with production series:
        the stages commodities (bnm.stages: steel, aluminium, alumina,
        copper, nickel, zinc, cobalt, cement) and refinery throughput.
      - Exposures, from assets present the year before (bnm.cell-profiles/
        present?, no backcast) and capacity weights as in cells.csv: cell,
        the share of the country's capacity of the key in the hit cells
        (cell-cut on the footprint); country, the same share if capacity
        were spread evenly over the country's land (the hit land share),
        which is what a country-level model can know.
      - Outcome: the log change of the country's output of the key in the
        shock year against the mean of the three years before (bnm.stages
        quantities), country-years with no shock as controls.
      - Model: outcome on exposure with key-year effects, one regression
        per exposure. Cell wins if its leave-one-year-out squared error
        is lower than country's and the difference's block bootstrap
        (by year, 1,000 draws) 90% interval excludes zero; with fewer than
        50 country-years whose cell exposure is 0.05 or more, the test is
        reported as underpowered, not read.
      - Placebos: the footprint moved to a random land position in the
        same country (cell exposure should lose its edge), and the shock
        dated a year early (both exposures should predict nothing).
    - Prepared (bead bnm-1pq.27; bnm.located-shocks, tools/bnm/
      located_shocks_run.clj), developed on shock years 1991-2009 only
      (outputs to 2010; the tool refuses later outputs without
      --registered true):
      - Catalogue (scripts/located-shocks.py, data/derived/hazards/, each
        source in data/raw/hazards/ with its SOURCE.txt): USGS ComCat M6
        and above 1990-2024 with ShakeMap MMI grids (2,694 of 5,259
        events; those deeper than 300 km or over 250 km from land not
        fetched), 371 reaching land at MMI 7; IBTrACS since 1980, 1,210
        storms at hurricane force over land (R64 the mean of the quadrant
        radii, else the median of the point's wind band, 37-79 km); DFO
        Global Flood Records 1985-2023 (Zenodo; the Global Flood Database
        is on Earth Engine only), 5,470 on land; and conflict onsets from
        the project's own GDELT catalogue on cells (data/norm/gdelt-a5) in
        place of UCDP GED, which is not held: a cell-year with 5 or more
        assault events (CAMEO 18-20) and 2 or more per million of the
        year's located events, the five years before each under a quarter
        of that, 2,661 in 1990-2024. A cell is hit when the footprint
        covers half of it. EM-DAT is registration-gated and on the manual
        list (section 9).
      - Changes to the design above, fixed on development years: the
        outcome is the shock year and the next against the three years
        before (a shock late in a year shows in the next); a unit counts
        only where the country has a plant of the key known the year
        before; the outcome is held to plus or minus 1 (small producers'
        output halves or doubles for reasons no shock explains); cement
        output is BGS's finished cement (33 countries, from 1998; stages.
        csv has none); shocks span 1990-2024.
      - Known plants: before 2011 only SFI's dated steel and cement plants
        and 7 OGIM refineries are known (SFI's 3,397 undated plants count
        from 2021, USGS's from 2016-2024, OGIM's 680 refineries from 2026).
        The development units are steel 865 (52 countries), cement 248
        (28) and refinery 41 (3).
      - Development (data/runs/located-dev): 1,154 units, 263 exposed
        country-years (234 of them from floods). Neither exposure beats no
        exposure (squared error 0.0506; cell 0.0508, country 0.0506);
        cell less country +0.00026, 90% interval [-0.0002, +0.0007], no
        verdict. Slopes: cell -0.02, country +0.06. Only floods have 50
        exposed country-years alone (no verdict); earthquakes 2, cyclones
        29, conflict 13. Time placebo -0.00014 [-0.0008, +0.0004]; all 19
        space placebos at or below the real footprints (mean -0.00001).
        Coverage 0.25, (key, country) effects, no clip, and assets read
        back to their start years (not causal: 4,435 units, all nine keys)
        give the same: no verdict, cell no better.
      - To register: the all arm as run in development (coverage 0.5,
        exposure 0.05, clip 1, key-year effects, 1,000 draws, seed 1, at
        least 50 exposed country-years), shock years 2011-2023 (outputs
        2008-2024; 2010, whose outputs straddle the split, is in
        neither). Without reading outputs, the window has at most 405
        exposed country-years (`--preview true`). Expected from
        development: cell does not win, keep countries.
        `jolt -A:tools -M -m bnm.located-shocks-run --from 2011 --to 2023
        --name located-test --registered true`
    - Built (bead bnm-1pq.11): data/derived/stocks/cells.csv,
      cells/<key>-hhi, -top-share (top cell in top) and -cells for 57
      processing and power keys, WLD and per country, 1990-2026, over the
      assets present in the year (bnm.cell-profiles/present?). Capacity
      missing is the key's median (over whole assets: a polygon's pieces
      summed first); a USGS plant capacity over 100 times its key's median
      weighs as the median. That guard hits 11 rows (10 plants), all USGS:
      gold 30 kt (India, twice), silver 460 kt (India), 46 kt (Korea Zinc)
      and 30 kt, indium 100 and 30 kt (China), cadmium 700 kt (Peru),
      copper 130 Mt (Egypt) and barite 10 Mt (China), each more than the
      world's output or near it: unit slips or ore throughput. Only one may
      be real, a 2.4 Mt stone plant in Togo against a median of 17 kt.
      Crude refineries, power plants and SFI's plants are not guarded, so
      no real large refinery is touched. Hand check: the world cobalt
      refining HHI in 2026 recomputed from assets.csv in Python is
      0.169649 over 46 cells, top cell 4aa18 (Copperbelt) 37.89%, as in
      cells.csv.
      Top cells 2026: Shandong (118E 37N) holds 18% of listed alumina and
      12% of aluminium smelting capacity; Zambia's Copperbelt 38% of
      cobalt refining; Baotou 16% of rare-earth processing; Ras Laffan 19%
      of LNG export. World cell HHI is low for steel (0.004), cement
      (0.001) and refineries (0.005), higher for cobalt (0.17), alumina
      (0.05) and the minor metals. bnm.cell-profiles/cell-cut gives the
      share of each bnm.supply region's capacity of each product in a set
      of cells ({[region product] fraction}), the input outage takes. A
      polygon (field or mining area) is cut by its pieces in the cut
      cells. LNG export terminals were in no product, so a cut of Ras
      Laffan left Qatar's gas whole; they now cut the gas they ship (1
      Mt a year per 133 mmcf/d) over the region's gas output (bnm.supply
      production), the larger of that and the fields' loss binding.
      Ras Laffan (cell 73fe8, 2011 assets: 14 LNG trains, 10,204 mmcf/d,
      an ethylene cracker, 3 fields; tools/bnm/cell_profiles_run.clj cut):
      the cut is 16.4% of the Middle East's (WM) gas, 6.1% of its
      plastics, 0.09% of its crude. Through outage: WM gas 16.4% down;
      fertiliser, gas power, LPG, ethane, refinery gas and plastics 15.9%;
      steel 13.9% (DRI); chemicals 13%; Japan's plastics and refinery
      feedstock 11.8%. Not yet run against located shocks, so the
      registered test is open.

## 9. Downloads needed

| data | use | size | licence | how |
|---|---|---|---|---|
| EXIOBASE v3.8.2 pxp 2000, 2005, 2008, 2015, 2019 | coefficient drift | about 3.5 GB (0.54-0.74 GB each) | CC BY-SA 4.0 | open, Zenodo 5589597 |
| EXIOBASE v3.8.2 pxp, other 20 years | only if step 5 passes | about 14 GB more (19.5 GB for all 28) | CC BY-SA 4.0 | open |
| BACI HS12 (2012-2024) | Li-ion 850760, IC series, finer splits | about 2 GB zipped (estimate; HS07 is 1.68 GB) | Etalab 2.0 | open, CEPII |
| BACI HS17 (2017-2024) | BEV 870380, e-bikes 871160 | about 1.5 GB zipped (estimate) | Etalab 2.0 | open |
| UNSD HS correspondence tables (HS92-HS22, HS-BEC5, HS-CPC/ISIC) | concordance | under 10 MB | open (UN) | open |
| Eurostat CN/HS-CPA 2008 correspondence | HS → EXIOBASE | under 5 MB | Eurostat reuse (CC BY 4.0) | open |
| FAOSTAT Inputs_FertilizersNutrient (and Production_Crops if not held) | N/P/K use per country-year; yield response | about 5-30 MB | CC BY 4.0 | open |
| USGS Historical Statistics for Mineral and Material Commodities (DS 140) | world production and US prices 1900-, for spikes | about 20 MB | public domain | open |
| IMF PortWatch daily chokepoints | chokepoint check, 2019+ | about 10-20 MB (80k rows) | IMF terms: reuse with attribution, commercial by permission | open API |
| a sea-route network (e.g. searoute / Eurostat SeaRoute) | routing BACI | under 50 MB | open (EUPL / MIT, check) | open |
| EU CRM lists 2011-2023, USGS 2022 list, DOE 2023 | validation labels only | PDFs | open | open |
| IEA Critical Minerals Data Explorer / Global EV Outlook data | intensities, EV sales | small | IEA terms (account) | manual |
| GEM plant-level trackers (steel, chemicals, cement, refineries) | plants by cell with dates | about 50-200 MB | CC BY 4.0 | manual (form) |
| ERA5 heating degree-days | heat demand prior | small if pre-aggregated | Copernicus licence | manual (account) |
| USGS ComCat M6+ 1990-2024 and ShakeMap MMI grids | located shocks (step 10) | 132 MB | public domain | held: scripts/located-shocks.py fetch |
| IBTrACS v04r01 since 1980 | located shocks | 145 MB | public domain | held |
| DFO Global Flood Records 1985-2023 | located shocks | 6 MB | CC BY 4.0 (Zenodo 19288171) | held |
| EM-DAT | disaster losses by event, a check on the catalogue | small | non-commercial, registration | manual (account) |

Not needed: UN Comtrade (BACI covers it), and v3.9 and later EXIOBASE
(licence).

## References not cited inline

- Atalay, E. (2017). How important are sectoral shocks? *American Economic
  Journal: Macroeconomics* 9(4):254-280, https://doi.org/10.1257/mac.20160353.
- Boehm, C.E., Flaaen, A. & Pandalai-Nayar, N. (2019). Input linkages and
  the transmission of shocks: firm-level evidence from the 2011 Tōhoku
  earthquake. *Review of Economics and Statistics* 101(1):60-75,
  https://doi.org/10.1162/rest_a_00750.
- Dietzenbacher, E. (1997). In vindication of the Ghosh model: a
  reinterpretation as a price model. *Journal of Regional Science*
  37(4):629-651, https://doi.org/10.1111/0022-4146.00073.
- Gaulier, G. & Zignago, S. (2010). BACI: international trade database at
  the product level. CEPII Working Paper 2010-23.
- Hulten, C.R. (1978). Growth accounting with intermediate inputs. *Review
  of Economic Studies* 45(3):511-518, https://doi.org/10.2307/2297252.
- Pierce, J.R. & Schott, P.K. (2012). A concordance between ten-digit U.S.
  Harmonized System codes and SIC/NAICS product classes and industries.
  *Journal of Economic and Social Measurement* 37(1-2):61-96.
