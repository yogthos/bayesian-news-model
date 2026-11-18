# Synthetic crisis testbed (bnm-3j8)

Written 18 November 2026, before the code. From reports/Signal detection for crisis forecasting.md and reports/Cross domain detection transfer.md (in the bayesian-news-model workspace): disease surveillance tunes and compares detectors on synthetic outbreaks injected at known sizes, so the real outbreaks are not used up in tuning; the world has about ten crisis waves, and every design chosen on them is in-sample for the next.

## What it is

A world with known truth, run through the real signal layer (bnm.signals declarations) and the real lattice (bnm.lattice/run), scored the way the base is scored. A run takes seconds to minutes, not the half hour of a banking world run, so a change to the signal layer or the averaging can be tried on dozens of worlds before it is tried once on the data.

## The world (bnm.testbed/world)

- **Countries** in regions, over years; each country has a set of stocks (credit, house prices, reserves, terms of trade, ...), each an AR(1) around a slow trend, plus a common world cycle, plus country noise with heavy tails (Student t) at a level that differs by country (the CFAR problem: a country's own noise).
- **Published with a lag:** each stock's year is published a set number of months after it ends, and the testbed's db is the slow table's shape (bnm.stocks series of [period available value]), so every read is point in time exactly as on the data.
- **Crises from a known hazard:** log odds = base + the episode's channel loading times a stretched stock meeting a turning flow (docs/INVARIANTS.md), + a world wave term, + a refractory term after the country's own crisis (its sign is a parameter: the banking finding was no refractory effect, and the model over-forecast recent-crisis countries), then a crisis lasts a set number of years (its spell: not at risk again until it ends). Which stock drives an episode is drawn per wave from a set of channels, so combinations differ each time and the structure is common.
- **Truth kept:** each country-year's true hazard and which channel drove it, so a detector's skill can be measured against the true probability, not only the outcomes.

## Scenarios (bnm.testbed/scenarios)

Each isolates a failure the reports or the base runs named:
1. **credit waves:** two or three world waves driven by credit stretch and a house-price turn (2008-like). The base's anti-phase frailty should show; a fix should remove it.
2. **commodity bust:** crises in exporters after a terms-of-trade fall, few of them with a credit history (the first-time crises the base under-forecasts 2x).
3. **mixed channels:** each wave a different channel; tests learned combinations and breadth.
4. **late data:** one informative stock published only from year 60 of 100 (the lock-out, bnm-1hu).
5. **null:** stocks and crises independent. Every detector must stay at the base rate: its alerts at 4:1 must cost no more than the constant rule, and its level must not track anything.
6. **synchronized wave:** a common shock with many countries stretched at once (cell-averaging's masking: peers as a reference hide the wave).

## Scores (bnm.testbed/score)

Per run: AUC pooled and within year; the yearly level's correlation with the realized rate; Brier and log score against climatology and against the true hazard (the oracle); expected calibration error by decile; Molchan area (alarm fraction against miss rate, 0.5 for random); the 4:1 alert cost against the better constant rule; the delay from a wave's first crisis to the first origin whose world-level p exceeds its long-run mean. Across worlds (seeds): means and the spread, so a design is chosen on dozens of draws, not one.

## How it is used

A change is tried on the testbed first, registered with its expected effect per scenario, then registered and run on the data under the fold rule as before. The testbed never replaces the data's rule; it decides what is worth a data run.

## First results (18 November 2026)

Six scenarios x 3 seeds at full size (60 countries, 100 years) run in 39 seconds; scored over each world's last 40% of years (the data are scored after a century of learning too).

- **The null world stays flat.** Log skill against climatology about 0, alert cost at the constant rule's. Its AUC (0.44) and level correlation (-0.26) look inverted, but the forecast is nearly constant (0.0287-0.0288 in every band of years since the last crisis): correlations of a flat p are noise, so the scores now need p's spread beside them. The band of countries that never had a crisis has the lowest rate there (0.014): conditioning on no past crisis selects calm draws. Banking's never-crisis countries show 3x the usual rate against that bias, so that finding stands.
- **The lattice recovers little of a planted signal.** Credit waves: AUC 0.60 against the oracle's 0.80, log skill against climatology -0.010 (5 seeds), the oracle +0.12. The averaging is not the bottleneck: late entry, tempered weights (power 0.3) and faster forgetting (0.99) move nothing beyond the third decimal.
- **Nor does a plain pooled logit on the same declared signals** (docs/studies/testbed_learnability.clj; fit on the first 60% of years, scored on the rest): 0-15% of the oracle's skill per row, the lattice in the same range and sometimes far below it (credit waves seed 2: -0.079 against the logit's -0.008). The signals, stretch against a ten-year window and the turn past 1, recover little of a latent turn from noisy annual observations. The oracle reads the latent state, so 100% is not reachable; a filtered estimate of the state is the research's answer (reports: state-space filtering, Kalman/DLM signal-to-noise).

Next on the testbed: a filtered read (each stock's latent level by a Kalman filter, its stretch and turn on the filtered state), registered with its expected effect per scenario, then on the data.

### The baseline absorbs the boom (18 November 2026)

- **An achievable oracle** (the planted rule, stretched past 1 then turning, computed on the observed values, detrended): 37-66% of the oracle's skill in five of six worlds (credit waves and synchronized, 3 seeds each; 6% in one). So most of the signal is recoverable from the published data, and the signal layer lost it: 0-15%.
- **Kalman filtering does not recover it** (bnm.signals/kalman-level, the `:filtered` read): within 0.03 of the raw shares.
- **A long baseline does.** The stretch and turn against 60 years (at least 10) in place of 10 (decls :long): the lattice's share 0.35, 0.01, 0.19 (credit waves) and 0.22, 0.23, 0.48 (synchronized), against 0.21, -0.41, -0.32 and 0.11, 0.07, 0.15; a pooled logit's 0.40, -0.05, 0.39 and 0.36, 0.11, 0.18. A ten-year window adapts to a boom lasting about five years (AR 0.8) and takes it into its own baseline: the cross-domain report's guardrail, the reference must not adapt so fast that it absorbs a slow boom.
- **On the data** every stretch the banking node reads is against ten years: bnm.macrohistory's specific dimensions (bnm.invariants/stretch, 10 years, at least 6), the extended stretches (lattice-banking/stretch-at), and the coverage declarations. The 20-year long-specific stretches exist for credit, mortgages and house prices only, at the half-decade node. A data run with long baselines at the year node is the next test (registered first).

### Long baselines on every scenario (5 seeds)

Mean log skill against climatology per row, ten-year window -> 60-year baseline (alert cost at 4:1 beside it; the better constant rule in brackets): credit waves -0.0099 -> +0.0092 (0.308 -> 0.285 [0.342]); commodity bust +0.0054 -> +0.0125 (0.198 -> 0.191 [0.209]); mixed channels -0.0198 -> -0.0121 (0.325 -> 0.296 [0.328]); late data -0.0017 -> +0.0050 (0.299 -> 0.296 [0.344]); synchronized +0.0172 -> +0.0412 (0.370 -> 0.347 [0.442]); null -0.0004 -> -0.0005, the forecast flat (p's spread 0.001-0.002). The long baseline helps wherever there is a signal and costs the null nothing. Mixed channels stays below climatology: when the driving channel switches every 20 years the learned weights still point at the old one, the "combinations differ each time" problem (era-pooled weights, bnm-7bh step 2). The skill-share means are dominated by worlds whose oracle skill is near zero; per-row log skill is the column to read. On the data: t94 (CALIBRATION "Long baselines at banking").

### A guarded baseline does not help (5 seeds, long baselines)

The standardizer's baseline without the country-years in or within two years after an onset, each left out once known (Farrington's down-weighting; `--guard true`): credit waves log skill +0.0092 -> +0.0045, synchronized +0.0412 -> +0.0263, commodity bust +0.0125 -> +0.0143, null unchanged. The node's standardizer pools over countries and years, where crisis rows are few; the guard costs more by delaying every sample to when its outcome is known than it gains. Not carried to the data.

### Per-place presence and overconfident weights (bnm-ris, 5 seeds, long baselines)

`:presence :per-place` (a group with no input at a place out of that place's mixture, its record there the base's) on the new partial-coverage scenario (the driving stock at 30% of countries): within the covered countries the lattice ranks at 0.48-0.65 against the oracle's 0.58-0.77, but the credit group's weight is 0.000-0.001 with or without it. The weights lurch to one group at a time (seed 1, year country node: reserves, pure noise here, 0.99 in 1950; down 0.94 in 1980; all-inputs 1.00 by 1998): each period's evidence is summed over every country's row, about 60 here and 130 at banking, so a small edge in one period decides. Over all countries, log skill per row: plain -0.0038; tempered once per origin (`:weighting {:rule :temper :power :per-place}`) +0.0024; with per-place presence +0.0029; with late entry too +0.0029. Tempering across scenarios is mixed (credit waves +0.0092 -> +0.0110, partial coverage -0.0038 -> +0.0024; synchronized +0.0412 -> +0.0367, commodity bust +0.0125 -> +0.0098). Per-place presence works as built and adds little on its own; the averaging's overconfidence is the larger fault, and no setting tried here fixes it.
