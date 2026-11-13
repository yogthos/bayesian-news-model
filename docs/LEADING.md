# Leading information at the origin (outlier episodes, t57-frailty)

Diagnostic, no model change. Question: could the information the model holds at a December origin t have anticipated the outlier episodes, and if it could, why did the forecast not use it? Steps 3-4 (CALIBRATION.md 7-10, RECURSIVE.md 3-4a) showed that a common shock filtered from resolved outcomes always arrives after the onset. What is left is information read at t.

Base run: t57-frailty, replayed for food price shocks, market crashes and growth collapses with its exact command (no relations, so the kinds are independent). The replay's issued p equals data/runs/t57-frailty's forecasts.tsv exactly at every December year and quarter-lead country row (21,155 rows, max |diff| 0). The replay dumped each December's issued rows (the standardized x, offset, every model's issued predictive) and the year and quarter-lead country models (coefficient means and variances, weights). Scripts and dumps are in the session scratchpad (`lead/replay.clj`, `lead.py`, `ceiling.py`); nothing in src/ or tools/ changed.

Episodes are year country nodes: December origin, 24-month window. The rate is the realized share of at-risk countries.

## 1. What the node could read at t

The year country node of these three kinds reads no price, spread or index directly. Its inputs (layout, 58 columns) are the political year row (pg/year-row: V-Dem, Maddison growth, WDI inflation and food inflation, 1-2 years old), the kind's own history, four other kinds' counts, `world3` (the share of countries with an onset known in the last three years: lagging), `:up` (the quarter-lead and quarter-w2 states, last and mean over the year), `:down` (the subregion, region and world year states one origin back), contradiction, exposure (`:down` times trade and capital openness) and feedback. Leading information reaches the year node only through `:up`, i.e. through the quarter nodes, which read:
- food and growth (political quarter inputs, tools/bnm/named.clj:128): world food price index 12-month change (IMF PFOODINDEXM), times food imports and times infant mortality, country food inflation, Brent 12-month change times net fuel imports;
- market crashes (financial quarter inputs, named.clj:99): VIX level, US curve 10y-3m, world credit 3-year change, the country's equity run-up, credit and house prices.

Not read anywhere in the lattice although on disk in data/slow/series.tsv: the US financial stress index (`USA finance/stress`, from 1994), the BAA spread (from 1986), OECD composite leading indicators (17 countries), the VIX change, grain prices, the supply-pressure index.

World gauges at the episode origins, z against their own monthly history known by t (since 1990; food from 1992):

| origin | food12 | food24 | wheat12 | maize12 | brent12 | VIX | VIX12 | BAA12 | stress | US curve | US policy 12m |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Dec 2005 | -0.24 | -0.34 | +0.42 | +0.13 | +0.97 | -1.64 | -0.23 | -0.16 | -1.44 | -1.10 | +1.57 |
| Dec 2006 | **+1.52** (+14.5%) | +1.02 | +1.40 | **+2.53** (+54%) | +0.07 | **-1.62** | +0.04 | -0.45 | **-1.60** | **-1.73** (-0.41 pt, inverted) | +0.80 |
| Dec 2007 | **+1.74** (+17.3%) | **+2.53** (+32%) | **+2.05** | +0.06 | +1.03 | +0.58 | **+2.50** | **+2.00** (+0.89 pt) | **+3.13** | -0.45 | -0.51 |
| Dec 2008 | -1.84 | -0.28 | -1.80 | -0.38 | -2.97 | +3.09 | +2.76 | +5.27 | +5.19 | +0.64 | -2.32 |
| Dec 2018 | -0.78 | -0.72 | +1.00 | +0.25 | -0.49 | +0.94 | +2.62 | +0.61 | +0.31 | -1.24 | +0.91 |
| Dec 2019 | +0.23 | -0.44 | -0.55 | +0.06 | +0.35 | -0.81 | -1.69 | -0.39 | -0.31 | -1.27 | -0.38 |
| Dec 2020 | +0.56 (+8%) | +0.62 | +1.09 | +0.51 | -0.94 | +0.59 | +1.32 | +0.24 | -0.49 | -0.75 | -0.89 |
| Dec 2021 | **+1.44** (+19%) | **+1.58** | +1.48 | +0.95 | +0.99 | +0.45 | -0.11 | -0.53 | -0.60 | -0.25 | +0.21 |

(Dec 2021 also: commodities 12m z +2.13, supply pressure z +5.0.)

What reached the year node (standardized x as t57 issued it, mean over countries / share with |x| > 1.5):

| kind, origin | up: qlead-last | qw2-mean | world3 | quarter's own key input |
|---|---|---|---|---|
| market crash Dec 2006 | +1.32 / 0.26 | +1.30 / 0.33 | -1.51 / 1.00 | us-curve -1.80 (all), world-credit3 +2.05 (all), VIX -1.59 |
| market crash Dec 2007 | +0.79 / 0.00 | +1.36 / 0.26 | -1.51 / 1.00 | world-credit3 +3.39, VIX +0.55 |
| food Dec 2006 | 0 (node uninformed) | 0 | 0 | world-food12 +1.40, food12 x imports +0.81 |
| food Dec 2007 | 0 (uninformed) | 0 | **+5.00 / 1.00 (cap)** | world-food12 +1.67 (all) |
| food Dec 2008 | **-4.77 / 0.99 (cap)** | **-5.00 / 1.00** | +2.01 | world-food12 -2.76 |
| food Dec 2020 | +0.08 / 0.04 | +0.38 / 0.09 | -0.21 | world-food12 +0.47 |
| food Dec 2021 | -0.85 / 0.26 | +1.23 / 0.24 | -0.29 | world-food12 +1.22 |
| growth Dec 2007 | -0.28 / 0.00 | -0.02 / 0.00 | -1.16 / 0.00 | world-food12 +1.67; no financial input |
| growth Dec 2008 | -0.38 / 0.00 | -0.17 / 0.00 | -1.15 / 0.00 | world-food12 -2.76 |
| growth Dec 2019 | +1.12 / 0.01 | +1.41 / 0.11 | -0.22 / 0.00 | (the quarter's food slope, section 3) |

Leading signals present before the onset: food 2006-07 yes (world food, maize, wheat); food 2020 weak (grains and supply pressure only); food 2021 yes; food 2019 no; market crashes 2006 yes (inverted curve, credit boom, VIX at its floor) and 2007 strongly (stress, VIX and BAA changes); growth 2007 and 2008 yes (the same financial signals); growth 2019 (COVID, onsets dated January 2020) no: policy uncertainty z +2.1 and a flat curve are all there is.

## 2. Honest ceilings

A causal logistic per kind on the same country-year rows t57 scores: at origin t it is fitted only on rows whose outcome is known by t (origin + 24 months + the catalogue lag: 18 months for food and growth, 3 for market crashes), ridge prior N(0, 1) on standardized slopes, missing inputs at the training mean. "row" counts every country-year, "origin" weighs each origin's rows as one observation (the frailty's count). Inputs fixed in advance from theory, 1-3 of them. When no outcome is known yet the ceiling issues what t57 issued (food 2003-06: 0.5). "Clim" is the same fit with no inputs (the rate of known outcomes). Brier, country-year:

| kind (n 2003-22) | t57-frailty | clim (causal) | ceiling on leading gauges | ceiling on the year node's own `:up` inputs |
|---|---|---|---|---|
| food (2913) | 0.1580 | 0.1524 | food12 0.1464 (origin 0.1473); food12 + food12 x food imports 0.1452 (origin 0.1460) | 4 up inputs 0.1441 (origin 0.1479); qw2-mean 0.1457 |
| market crash (798) | 0.1701 | 0.1716 (from 1985), 0.1604 (all history) | stress 0.1456 (origin 0.1446); curve 0.1624 (0.1610); VIX12 0.1667 (0.1498); curve + stress 0.1457 | 4 up inputs 0.1453 (origin 0.1456; all history 0.1368); qw2-mean 0.1424 |
| growth (3205) | 0.0966 | 0.0974 | VIX12 + BAA12 x openness 0.0936; stress 0.0981; BAA12 0.0972 | 4 up inputs 0.0987 (origin 0.0982) |

Per episode, ceiling p and Brier against t57 on the same rows (best fixed ceiling of section 2's kind, causal):

| episode | origin | rate | t57 p | ceiling inputs | ceiling p | Brier ceiling / t57 | node `:up` ceiling p (Brier) |
|---|---|---|---|---|---|---|---|
| food 2007-08 | 2006 | 0.418 | 0.500 | none learnable: no food outcome known before mid-2007 | 0.500 | 0.250 / 0.250 | 0.50 (0.250) |
| | 2007 | 0.313 | 0.238 | food12 + x imports (one origin of history, slope unidentified) | 0.20 | 0.227 / 0.221 | 0.14 (0.244) |
| food 2019 | 2019 | 0.087 | 0.112 | food12 | 0.12 | 0.081 / 0.081 | 0.16 (0.087) |
| food 2020-22 | 2020 | 0.493 | 0.109 | food12 + x imports | 0.14 | 0.373 / 0.402 | 0.21 (0.338) |
| | 2021 | 0.500 | 0.109 | food12 + x imports | 0.26 | 0.305 / 0.408 | 0.37 (0.267) |
| market crashes 2008 | 2006 | 0.957 | 0.269 | curve | 0.52 (origin 0.45) | 0.234 / 0.514 | 0.43 (0.318) |
| | 2007 | 0.957 | 0.252 | stress | 0.81 (origin 0.69) | 0.063 / 0.542 | 0.48 (0.274) |
| growth 2008-09 | 2007 | 0.235 | 0.071 | stress / VIX12 + BAA12 x open | 0.04-0.06 | 0.211-0.218 / 0.207 | 0.06 (0.211) |
| | 2008 | 0.202 | 0.068 | BAA12 + CLI12 + comm12 | 0.08 | 0.173 / 0.179 | 0.05 (0.184) |
| growth 2020 | 2019 | 0.554 | 0.078 | any | 0.02-0.05 | 0.50-0.53 / 0.472 | 0.03 (0.521) |

Non-causal check (leave the origin and its overlapping neighbours out, fit on 1985-2022): growth 2007 and 2008 reach p 0.16-0.21 on stress (Brier 0.186 / 0.162 against t57's 0.207 / 0.179), food 2007 0.27. So the growth signal exists, but before 2007 the gauges' history (VIX from 1990, stress from 1994) holds no global growth collapse to learn it from; a causal fit learns a slope near 0 (stress 0.07 at Dec 2008).

Selecting the inputs from data instead (causal forward selection over 34 world, country and exposure-product candidates, BIC on origins) loses everywhere: food 0.1580 (1 input) and 0.1740 (3), market crashes 0.1841 and 0.2072, growth 0.0941. With 15-20 resolved origins, a world series fitted on every country row is picked for noise (food chose the US policy rate from 2014, market crashes chose food24 x food imports to 2008). The leading inputs need theory's signs and the per-origin count; they cannot be found from the outcomes.

## 3. What t57 did with it

**Market crashes.** The `:up` group's weight at the year country node is under 0.005 at every December from 1968 to 2021 (at most 0.06 in 1965-67, the node's first years). The weights sit on political groups: regime 0.94 (2003), 0.36 (2006); violence 0.23 (2006), 0.48 (2007), 0.65 (2008); `world` (world3) 0.47-0.84 in 2010-15. The `:up` model's coefficients at Dec 2006 (mean (sd)): qlead-last -0.07 (0.26), qlead-mean +0.13 (0.27), qw2-last +0.40 (0.27), qw2-mean -0.33 (0.28). They are flat from 1995 to 2015 and nearly cancel: with all four inputs at +1.3 the model moves the log odds by about +0.17. In the all-inputs model they are -0.01, +0.09, +0.19, -0.09 (prior 0.1). So the coefficients were not double counted from one episode; they were never learned (shrunk to near nothing and collinear), and the model that carried them had no weight. Its issued predictive beat the weighted leaders at 2006 and 2007 (log score -44.8 and -51.5 nats per origin against regime -49.6/-54.1 and violence -52.2/-56.4), though the world3 model did better still (-36.2/-38.5), and those rows resolved only in 2009-10. It lost its weight at the node's first informed origins: in 1965-67 its predictive variance was q = 95, 3 and 11 (flows on the standardizer's cap times unlearned coefficients, bugs 2-3), 6.4 nats behind the base model in three origins, and it then tied the base for decades without catching the leaders. The quarter-lead read the right signs: us-curve -0.59 (0.20), world credit +0.43 (0.10), VIX +0.18 (0.12) in its all-inputs model (weight 0.95 at Dec 2006). It issued 0.23 at Dec 2006, too early for its own window (rate 0 in April-December 2007), and 0.15 at Dec 2007 (rate 0.93). world3's coefficient (-0.27) read the 2003-05 calm as a reason for more crashes, which helped a little.

**Food.** The year node had no resolved outcome until 2007 (first year origin 2003, known mid-2007), so 2005-08 it issued with equal weights on unlearned models, flows at 0 until informed (`:flow-std-from :resolved`). Its first informed flows landed on the standardizer's cap: world3 +5.00 at every country at Dec 2007, `:up` -4.8 to -5.0 at 89% of countries at Dec 2008. With the up coefficients still at their prior (sd 1.15, see bug 2) the `:up` model's predictive variance on the 2008 rows was q = 123; scored when they resolved it lost 36 nats against patronage on that one origin, and 99 nats over 2008-12. It won 39 back in 2013-18, too little: from 2012 the weight sat on patronage (neopatrimonial index, a structural gauge, coefficient +0.53 to +0.63), 0.90-1.00 in every year 2012-21. By 2019-21 the `:up` model had learned a usable signal (qw2-mean +0.91 to +1.00 (0.24), qlead-last -0.34 to -0.55, qlead-mean +0.26 to +0.40) and its issued predictive was the best of all 19 models in 2020 and 2021 (-137 and -101 nats summed over the origin's rows, against patronage's -174 and -172 and the next best model's -154 and -151), but its weight was 0.00. In the quarter-lead, world-food12's coefficient was unlearned (sd 3.9) until 2005, then learned negative from the first few calm quarters: -0.47 (0.24) at Dec 2006, -0.82 (0.20) at Dec 2007 (the all-inputs model -0.82 (0.38) and -0.30 (0.20)), and the food-group model kept it negative (-0.36 to -0.40 in 2019-21; the all-inputs model's is about 0). So at Dec 2006, with world food +1.4 sd, the quarter issued 0.08 against a quarter rate of 0.15-0.30. That is a slope learned from one calm stretch, counted at every place, the wrong way.

**Growth collapses.** The growth nodes read no financial gauge: the quarters take the political inputs. The quarter states fell into 2007-08 (`:up` -0.3 at Dec 2007 and 2008), so even a perfect year node would have read nothing from them; the node-input ceiling (0.0987) is worse than t57. The year's weights: economy 0.83-0.86 (2004-05), then base 0.34-0.42, economy 0.14-0.34, world3 0.22-0.31 (2006-09), economy 0.98-1.00 (2017-21); `:up` at most 0.006 since 1966, its coefficients unlearned (qlead-last +0.22 (0.34), qw2-mean -0.14 (0.37)). The double count shows in the quarter: the all-inputs model's world-food12 coefficient was -0.75 (0.25) through Dec 2008, flipped to +0.95 (0.16) at Dec 2010 once the 2008 quarters resolved (the food boom followed by the 2009 collapses, learned at every place from one episode; weight 0.95-0.99 in 2010-11) and stood at +1.24 (0.15) by 2018 (weight 0.94). That slope is why the quarter states rose at Dec 2019 (+1.1 to +1.4), for reasons unrelated to what came.

## 4. Bugs and defects found

1. **tertiary5 is never read.** tools/bnm/lattice_political.clj:352 reads WDI through `(sk/value (+ 11 (* 12 y)) {:stale 18})`; people/tertiary_enrolment is published 21 months after its year (every row in series.tsv), so at December of year q the newest value is 24 months old and refused. `:tertiary5` (tools/bnm/political_gauges.clj:149) is nil at every country and origin (standardized 0.00 at every origin dumped), so the year gauge and the tensions that use it (protest and violence's `[:tertiary5 :growth3]`, lattice_political.clj:101, :104) are dead. Infant mortality, working age, urban share, inflation and food inflation (lag 9) are fine.
2. **Discounting before the first data inflates the declared priors.** augur.dynamic/step discounts the covariance every period whether or not there are rows (augur dynamic.clj:515-528, `discounted`), and the lattice steps every node every period from 1870 (src/bnm/lattice.clj:1294). A node whose catalogue starts late reaches its first data with its prior variance multiplied by 1/δ^n: the food quarter-lead's coefficients by 0.995^-537 ≈ 15 (sd 1 → 3.9, the all-inputs model's `:shrink` 0.1 → sd 1.24) in 2004, the year's by 1.31. The declared priors (`:shrink`, `:prior`, the contradiction prior) are void at the first resolved rows, and the first few origins set the slope alone: the food quarter's negative world-food12 slope in 2006. Market crashes (quarters from 1961) are inflated about 6x.
3. **The flow standardizer puts a time-common input on the cap after one origin.** `standardize` (src/bnm/lattice.clj:185-198) starts once ten rows are seen, i.e. after the first informed origin's countries. A time-common input (world3, and nearly so the up states) is then constant across everything seen, and its spread, computed one-pass as sqrt(ss/n - m²), is rounding noise of about 1e-8 times its level (checked: a constant 0.2 over 153 rows gives 1.5e-8), above the 1e-9 guard, so the next origin's value lands at ±5. Food: world3 +5.00 at every country at Dec 2007, `:up` -5.00 at Dec 2008; market crashes the same in 1965. `:flow-std-min-origins` (bnm-56c.34, not in t57) delays it; a guard relative to the level, or a two-pass variance, removes it. With bug 2's wide coefficients this cost the `:up` model 36 nats on one food origin and locked it out of the food year node until at least 2021.
4. **The evidence counts coefficient uncertainty per row** (known, bnm-56c.20.2; `:evidence-at :sequential` exists, off in t57). Bugs 2 and 3 are what it amplifies: a 150-country origin scored 150 times against one unlearned coefficient.

No lookahead found: every gauge read above was taken at its availability date (world food index one month after its month; Brent, VIX, BAA, stress, curve the same month; WDI food inflation nine months), and the replay reproduces t57 exactly. The ceilings' stress series is FRED's current STLFSI vintage, not real time (a caveat for the ceiling, not the model).

## 5. Verdict

- **Market crashes 2006-07: a lattice-usage problem.** The leading information was in the data and reached the year node through `:up` (quarter states at +1.3 sd at a third of countries), and a 1-4 input causal logistic on those same inputs scores 0.142-0.155 against t57's 0.1701 and issues 0.38-0.48 at the two origins against 0.25-0.27 (rate 0.96). Stress or the curve read directly do as well or better (0.1416-0.1624; stress issues 0.69-0.81 at Dec 2007). t57 gave `:up` no weight for fifty years and never learned its coefficients. Also missing wiring: the stress index and the BAA spread are on disk and read nowhere.
- **Food 2020-21: a lattice-usage problem.** The `:up` model learned the right signal and was the best model at both origins, but the averaging had locked on patronage after bugs 2-4 cost `:up` about 99 nats in 2008-12. Node-input ceiling 0.338 and 0.267 against 0.402 and 0.408; world food12 directly 0.373 and 0.305.
- **Food 2006-07: information present, outcomes missing.** World food and grain prices were up 1.5-2.5 sd at both origins, but no food outcome was resolved before mid-2007, so nothing could learn the slope causally, and the quarter learned it wrong. Only a prior from theory (sign and scale on world food x imports) or a longer food outcome history could act on it.
- **Growth 2007-08: information present, no precedent in the gauges' span.** The financial signals were extreme by Dec 2007 and Dec 2008 (stress +3.1 and +5.2 sd, BAA change +2.0 and +5.3), and a non-causal fit uses them (p 0.16-0.21 against 0.07). The causal fit cannot: from 1990 to 2006 no global growth collapse coincides with them. Longer gauge history (BAA and the curve go back further than VIX and stress) or transfer from market crashes is needed. The growth nodes also read no financial gauge at all (named.clj:128), so the route does not exist in the lattice.
- **Growth 2019 (COVID) and food 2019: no leading information in our data.** Nothing at Dec 2019 was unusual enough to move a forecast; these are not anticipatable from what we hold.

Most promising next steps:
1. **Step 5's gated weighting must be able to reopen a group** — and the lock's causes fixed first, since they are cheap: start each node's model at its prior when its first data arrive (no discount before data, bug 2), `:flow-std-min-origins` at the year nodes (bug 3) and `:evidence-at :sequential` (4). Evidence: the `:up` inputs alone give market crashes 0.1424-0.1546 against 0.1701 and food 0.1441-0.1546 against 0.1580, with weight 0.00 in t57; the `:up` model was the best-scoring model in food 2020 and 2021 by 37 and 70 nats.
2. **Wire the missing financial gauges with theory-signed priors, counted per origin**: the US financial stress index, the BAA spread change and the curve, at the market-crash (and growth) year and world nodes, as step 5's confidence-weighted world inputs. Evidence: stress alone 0.1416-0.1456 (Dec 2007 p 0.69-0.81 against 0.25), curve alone 0.1602-0.1624 (Dec 2006 p 0.45-0.52); and food12 x food imports with a positive prior would act at 2006-07, where no outcome history exists to learn its sign.

## Priors (bnm-56c.36)

Written 13 November 2026, before any run with these gauges and before any outcome score of them; the git history of this section dates it. The gauges are wired behind `--leading true`, the priors behind `--leading-priors true` (bnm.lattice `:named-prior`); both are off by default.

### The gauges and why these

| name | series (data/slow/series.tsv) | read at origin t | theory |
|---|---|---|---|
| `:baa12` | USA finance/spread_baa (FRED BAA10Y, Moody's Baa yield less the 10-year Treasury, monthly mean of daily, from 1986) | 12-month change in points | credit and equity are claims on the same firms (Merton 1974): a widening spread is a rise in the price of default risk and a fall in risk-bearing capacity, and it predicts falling stock prices and output over the next year (Gilchrist & Zakrajšek 2012); the US risk price moves risky asset prices everywhere (the global financial cycle: Rey 2013, Miranda-Agrippino & Rey 2020) and drives capital-flow stops in emerging markets (Forbes & Warnock 2012) |
| `:us-curve` | USA finance/curve_10y3m (FRED T10Y3M, from 1982) | level, points | a flat or inverted curve precedes US recessions by 2-6 quarters (Estrella & Hardouvelis 1991, Estrella & Mishkin 1998); bear markets accompany recessions (Chauvet & Potter 2000), and a US recession is a fall in world demand (Kose, Otrok & Prasad 2003); the global curve is among the strongest crisis predictors (Bluwstein et al. 2023) |
| `:vix12` | world finance/vix (FRED VIXCLS, log, from 1990) | 12-month change of the log | an uncertainty shock is followed by a fall in output within a year (Bloom 2009); global risk aversion is the main common driver of capital-flow stops (Forbes & Warnock 2012); volatility clusters and rises before and through declines (Schwert 1989) |
| `:world-food12` | world food/price_index (IMF PFOODINDEXM, log, from 1992, published a month after its month) | 12-month change | world food prices pass through to domestic food prices within a year (Furceri, Loungani, Simon & Wachter 2016) |
| `:food12-x-imports` | the same times the country's food imports share (WDI, read at its publication lag) | product | pass-through is larger where imports supply more of what is eaten (Ianchovichina, Loening & Wood 2014; Furceri et al. 2016) |

Where they enter (the `:leading` group, a gauge group as `:world` is for world3: the same value at every country, a place above the countries the mean of its countries, the year country node also through `:up`):
- market crashes: year `[:baa12 :us-curve :vix12]`; quarter-lead `[:baa12 :vix12]` beside the quarter's own `:us-curve` (and VIX level), which gets the same prior;
- growth collapses: year and quarter-lead `[:baa12 :us-curve :vix12]` (the political quarter reads no financial input otherwise, LEADING.md 1);
- food price shocks: year `[:world-food12 :food12-x-imports]`; the quarter-lead already reads both (its `:food` group), and they get the priors there.

Left out, with reasons:
- **The St. Louis Fed stress index and the Chicago Fed NFCI** (section 2's best single ceiling): both are estimated factor indexes whose weights are re-estimated on the full sample, so the history on disk is not what was known at the origin. Checked (data/raw/fred-vintages/SOURCE.txt): fetched again on 13 November, four weeks after the copies in data/raw/slow/fred, STLFSI4 differs at 1689 of 1711 past weeks (max 0.046) and NFCI at 2619 of 2910 (max 0.065); STLFSI version 1 (discontinued 2020) against version 4 over 1993-2020 correlates 0.70, max difference 4.7, Dec 2007 1.08-1.41 against 1.34-2.09. The market series are not revised: BAA10Y, T10Y3M and VIXCLS are identical at every overlapping day in the two fetches (they are quotes, published the same day).
- **The BAA spread's level**: its sign depends on the horizon. A high spread is stress now, but a low spread with fast credit growth precedes crises two to three years on (López-Salido, Stein & Zakrajšek 2017; Krishnamurthy & Muir 2017), and the year window spans both. No sign can be fixed in advance, so it is not read.
- **Equity drawdown**: at market crashes it is the outcome itself (a crash under way at the origin), at growth collapses it is concurrent with the VIX change.

Timing: every gauge is read through bnm.stocks at the origin, so a value enters only once its `available` month is at or before t (the monthly mean of daily quotes the same month, the food index a month later, WDI imports at its own lag); tested in bnm-56c-36-leading-gauges-are-read-at-their-availability-date. The gauges are standardized like every quarter and year gauge (bnm.lattice-banking's standardizer, `--gauge-std stable`): against their own values at the node's earlier origins, so a coefficient is in log odds per standard deviation of the gauge's own history as known then.

### The priors

Coefficient prior mean and variance on the standardized gauge, at the year and quarter-lead nodes of every level, in every model that reads the input (its group's and the all-inputs model, where it replaces `:shrink`). The rest of each node keeps its priors. Sign from the theory above; size by one rule: a gauge whose mechanism is direct and established moves the log odds about 0.5 per standard deviation (odds x1.6), one with a weaker or partly concurrent mechanism 0.25-0.3, and a US gauge read at every country at about two thirds to a half of its US effect. Prior sd 0.25 for all (variance 0.0625): the sign is held at two sd, the size is the data's to set. The quarter-lead uses the year's priors (a shorter window, but the quarters' rate is lower as well).

| kind | input | mean | sd | reasoning |
|---|---|---|---|---|
| market crashes | `:baa12` | +0.5 | 0.25 | direct: credit and equity price the same risk (Merton; Gilchrist & Zakrajšek), transmitted to every market (Rey) |
| | `:us-curve` | -0.4 | 0.25 | inversion before US recessions (Estrella & Mishkin), bear markets with them; less than direct for a non-US market |
| | `:vix12` | +0.25 | 0.25 | volatility rises before and through declines (Schwert), but partly concurrent with a fall already under way |
| growth collapses | `:baa12` | +0.4 | 0.25 | credit spreads predict output (Gilchrist & Zakrajšek); stops in emerging markets (Forbes & Warnock); collapses are mostly outside the US, so less than at crashes |
| | `:us-curve` | -0.3 | 0.25 | US recession as a world demand shock, partial transmission |
| | `:vix12` | +0.3 | 0.25 | uncertainty shocks cut output within a year (Bloom), global risk drives stops (Forbes & Warnock) |
| food price shocks | `:food12-x-imports` | +0.5 | 0.25 | pass-through scales with import dependence (Ianchovichina et al.; Furceri et al.) |
| | `:world-food12` | +0.3 | 0.25 | some pass-through everywhere, damped where markets are insulated |

These are the numbers `bnm.lattice-political/leading-priors` holds; t65-gauges reads them, t65-gauges-flat reads the same gauges at the node's default priors (variance 1, the all-inputs model's `:shrink` 0.1, mean 0), so the two runs separate the priors from the gauges.
