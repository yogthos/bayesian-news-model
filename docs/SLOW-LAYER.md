# The slow layer: many domains, many time scales

The event model sees only political events. Shocks start everywhere: an
energy supply cut, a harvest failure in a hot year, a credit boom, a
technology that makes an industry obsolete. They spread across domains
before politics shows them. The slow layer tracks those domains, learns how
they drive each other and the events, and gives the hazard layer the stocks
that culminating events build on. docs/PHASE-CHANGES.md argues why it is
needed. docs/RESEARCH.md has the literature.

## Domains and data

All sources are free and need no key. `scripts/fetch-slow.sh` downloads them
into `data/raw/slow/<source>/`.

| domain | series | source, frequency |
|---|---|---|
| economy | GDP growth and level, inflation, unemployment (youth too), sector shares | World Bank WDI (a), FRED US (m), OECD CLI (m) |
| finance | credit-to-GDP gap, total credit, debt service ratio, property prices, policy rates, exchange rates, yield curve, spreads, volatility, equity indices, financial conditions | BIS (q/m), FRED (d/m), OECD (m) |
| debt and external | government and external debt, current account, FDI, remittances, reserves | WDI (a) |
| energy | oil, gas and coal prices; production, consumption and stocks of oil and gas by country; energy mix, import dependence | FRED (m), JODI (m), OWID (a), WDI (a) |
| food and agriculture | food, grain and fertilizer prices; food CPI by country; crop production and yields; undernourishment | FRED (m), FAOSTAT (m/a), WDI (a) |
| trade, logistics, supply chains | freight, rail carloads, import and export prices, trade balance, openness and structure (the NY Fed's GSCPI is a legacy .xls, not read yet) | FRED (m), WDI (a) |
| technology and science | research output by field (AI, semiconductors, batteries, solar, genome editing, vaccines, quantum, robotics), patents, R&D, internet and mobile, high-tech exports, semiconductor production | OpenAlex (a), WDI (a), FRED (m) |
| climate | global temperature anomaly, ENSO, temperature and precipitation by country, emissions, disaster-affected population | GISTEMP (m), NOAA ONI (m), CRU TS via World Bank (m), OWID (a), WDI (a) |
| people and state | population, age structure, urbanization, infant mortality, inequality, military spending, tax take | WDI (a) |
| uncertainty | economic policy uncertainty, global and by country | Baker, Bloom & Davis (m) |
| politics | the event channels per country, per 1000 events reported that month (`scripts/politics-monthly.py`) | GDELT bins (m) |
| ground truth | conflict and war onsets, European systemic crises; and, defined from the data, market crashes (30% below the 24-month high) and protest and violence surges (three times the 36-month mean) | UCDP (a), ESRB (q), the normalizer |

**Limitations.**
- Most macro data are revised, and free real-time vintages exist only for some
  US series (ALFRED). The layer approximates point-in-time data by making a
  value usable only after a publication lag per source and frequency (below).
  The remaining revision bias is a known limitation, to be measured on the
  ALFRED series.
- Country climate series (CRU TS 4.07) end in 2022. GISTEMP and ONI are
  current.
- OpenAlex is annual and its field tagging is retrospective.

## Normalized form

`scripts/politics-monthly.py` first sums the weekly event bins into monthly
counts per country. `scripts/slow-normalize.py` then turns the raw files into
one long table,
`data/slow/series.tsv`:

    unit  variable  freq  period_end  available  value

- `unit` is an ISO3 country code, `world`, or `US` for US-only series held as
  global drivers.
- `variable` is `<domain>/<name>`.
- `freq` is d, m, q or a. Daily series are averaged to months.
- `available` is when the value would have been known: period end plus a
  lag. Daily and market data: 0. Monthly: 1 month. Quarterly: 3 months
  (BIS: 4). Annual: 6 months (WDI and OWID: 9).

Each variable's domain and transform (log for positive levels, identity for
rates, shares, gaps and anomalies) are in `data/slow/variables.tsv`, written
by the normalizer. Catalogue and mechanical events are in
`data/slow/events.tsv`.

## Model

Monthly steps. Everything is online, closed form and point in time, like the
event model.

1. **Multiscale trends per series.** Exponential averages of the
   transformed value at 3, 12, 36 and 120 months, updated when a value
   becomes available (decay raised to the months elapsed, so frequencies mix).
   From them, standardized by the series' own volatility:
   - the gaps (value minus the 36- and 120-month averages: the stock's
     deviation from its long-run path);
   - the trends (12 against 36 months, 36 against 120);
   - the acceleration (the short trend minus the long one: faster than its
     own trend, the multiscale analogue of LPPLS's super-exponential
     growth);
   - volatility at two scales.

   These are the "trends across temporal resolutions".
2. **Cross-domain links.** Each variable's next change is a dynamic
   Bayesian regression on features:
   - its own;
   - per-domain summaries for the same country (the mean gap, trend and
     acceleration of the domain's variables);
   - global drivers: oil, gas, food and fertilizer prices; US rates and the
     curve; credit spreads, financial conditions, VIX; supply chain pressure;
     ENSO; global temperature; global uncertainty;
   - the country's event channels.

   The estimates are conjugate Normal-inverse-Gamma with discounting:
   recursive least squares with forgetting, as in West & Harrison. The prior
   pools across countries per variable, with a shrinkage that grows with
   distance from the country's own lags (Minnesota).

   A regression on the series' own features runs beside it. The two are
   averaged by their discounted predictive records (dynamic model averaging:
   Raftery, Kárný & Ettler 2010). Other domains get weight where they have
   been helping that variable, and none where they haven't.

   The full regression's coefficients are the
   influence graph across domains. They can be read like the event model's
   drivers ("what moves Egypt's food prices"), shown sparsified while the
   forecast uses all of them.
3. **Simulation.** All series and the event model are simulated forward
   together, with parameter draws. The same machinery answers scenarios:
   cut oil supply by a third, a strong El Niño, a doubling of credit growth.
   The effect is traced through the learned links.
4. **Hazard.** For each kind of culminating event (conflict onset, coup,
   banking crisis, market crash, protest wave), a Bayesian logistic
   regression pooled across countries. It gives the probability of onset
   within 1-4 and 5-8 quarters from the slow features, their interactions
   (credit gap × flat curve, food prices × regime) and the event model's
   state. It is refit each quarter on outcomes known by then.
5. **Coupling to the event model.** The event baseline gets βᵀS from the slow
   features (log link). Once that shows gain on onsets, excitation gets it
   too (docs/PHASE-CHANGES.md, L1-L3).

## Training regime

One pass through time, as each piece would have lived it. No step sees a
value before its `available` date.

1. `scripts/fetch-slow.sh all`, then `scripts/slow-normalize.py`.
2. Slow layer, monthly from 1960 (warm-up) to now: trends, links and
   pooled priors learned online.
3. Event model, weekly from 1979, as now, reading the slow features from
   the month before each bin.
4. Hazard layer, quarterly from 1990, refit on catalogue outcomes resolved
   by each origin.
5. Evaluation (docs/PHASE-CHANGES.md):
   - onsets on the full catalogues, P(1-4q) and P(5-8q), with post-event
     exclusion;
   - recall at alert budgets, usefulness, Molchan diagrams, matched controls;
   - benchmarks (base rate, persistence, structural logit, slow layer only,
     event model only);
   - era split at the GDELT 2.0 break (2015), and leave one region out.
6. Hyperparameters (time scales, discounts, prior weights) chosen on
   1990-2007 only. 2008-2026 is scored once they are fixed, with the 2008
   crisis, the euro crisis, the Arab uprisings, 2014, COVID's supply shock,
   the 2021-22 energy and food price shock, and 2022 all in the held-out
   period.
