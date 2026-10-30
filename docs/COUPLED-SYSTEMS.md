# Coupled systems: the financial, real and political economies (research and design, October 2026)

Bead bnm-4hv.1, the first step of epic bnm-4hv; principle 12 of
docs/PRINCIPLES.md. The user's direction:

- The financial economy (markets and money flows), the real economy
  (resources, food, energy, manufacturing) and politics are coupled
  subsystems. Each one's outputs are the others' inputs, every period.
- Finance is a virtual layer over the real economy. It decides where
  labour and resources go.
- Who controls finance decides what money is allocated to, and so the shape
  of the real economy: state-owned banks in China, financial institutions
  and oligarchs in the West.
- The nature of the economy shapes politics, and the political system
  decides who controls the means of production.

Today the coupling is loose. Each model writes its own stock file
(data/derived/stocks/*.csv), bnm.states reads them all as unrelated
country-year columns, and the few links between them are written by hand:
Keen's debt feeds the credit gap, food import dependence is multiplied by
world storage pressure, and so on. No module reads another's output as a
declared input. Nothing checks that the money one module lends is the money
another one spends.

This document covers:
- the literature on coupling real and financial economies, on who controls
  credit, on the economy's effect on politics and back, and on how coupled
  models are built and validated;
- exploratory numbers on the data on disk;
- a system map with every channel typed;
- a module interface and an engine;
- the mapping of today's namespaces onto modules;
- the gaps, a validation plan and a ranked plan.

No project code was written. The exploratory scripts are in the session
scratchpad (coupled/: explore1.py to explore5.py).

## Summary

- **What the trials say this is for.** Trials 45 and T1-T9
  (docs/PHASE-CHANGES.md) found three things:
  - The slow stock-flow states do not time onsets: over the base they are
    equivalent to nothing on the mean (T4).
  - They do say where. They help the political kinds' cross-section (T4:
    material +0.0035, social +0.0022 log over coverage-matched placebos).
  - They cushion failing inputs. When the base's slow gauges go dark they
    hold back about a quarter of the loss (T9 dropout, passing after Holm).
    They were not kept because of a small worst-era cost.

  A coupled engine will not be judged by whether it makes the year node
  sharper. It is judged on four things:
  - (a) Accounting and consistency: the money and the goods add up.
  - (b) Hindcasts of paths: China's investment path, US
    deindustrialisation, the 1973 cascade, 2008.
  - (c) Attribution: which channel carried a change.
  - (d) The two uses the trials found: where, and robustness when inputs
    fail.

  The event test (bnm-4hv.8) is registered on T9's criteria and the
  use-case rule (bnm-82j.1), not on mean Brier.
- **Four subsystems, not three.** Control is its own layer between politics
  and finance. Its stocks are measured ownership and direction:
  - the state's share of capital and of banking;
  - state ownership of the economy;
  - capital-account openness;
  - financial repression;
  - private wealth concentration.

  Its output is a small control vector, learned as a latent factor with
  sign priors. It is not a label. bnm.econ-factors' econ/investment-regime
  is its first draft.
- **The core is stock-flow consistent.** It is a five-sector Godley-Lavoie
  accounting frame per country-year: households, non-financial
  corporations, financial corporations, government and the rest of the
  world. Every flow leaves one sector and enters another, which is
  principle 8 at the scale of the economy.
  - On disk: OECD Table 14 (non-financial accounts by sector) gives every
    sector's net lending for 40 countries, China included. The four
    domestic sectors plus the rest of the world close to within 0.3% of
    value added in nine country-years out of ten (median 4e-8%).
  - The financial balance sheets that would close the stocks are not on
    disk. BIS credit, JST and the Fed's Z.1 cover part of them.
  - Keen-Goodwin (bnm.keen) becomes the behavioural core of the household
    and firm sectors inside this frame, so its wage share, employment and
    debt are read from the frame, not from three unrelated sources.
- **Allocation is the channel to build.** Credit by sector (Müller-Verner,
  on disk, 117 countries to 2014) shows the user's thesis in the data:
  - Japan's credit to manufacturing fell from 53% of private credit (1960)
    to 13% (2014) as households rose from 2% to 29%.
  - Korea's manufacturing share went from 54% (1980) to 19% (2007).
  - Britain's went from 21% (1970) to 1% (2014).
  - Spain's construction and real estate went from 14% to 26% of credit
    (2000-2007).

  The gaps in that dataset are China and the US, which have no sector
  detail. Allocation is modelled as shares of new credit, a Dirichlet DLM
  whose prior mean the control vector moves and whose returns term (house
  prices, sector profit rates) the financial system responds to. Credit to
  a sector, with retained profits, becomes that sector's investment and
  then its capacity in the real module.
- **The engine.** It steps annually, in causal order:

      control -> finance -> real -> distribution -> politics

  Every back edge is lagged a year, and the engine checks that the
  contemporaneous edges form a DAG.
  - Modules declare their reads and emits as data, with units and known
    years.
  - It runs as an ensemble (members carry parameter uncertainty from
    bnm.assimilate/calibrate).
  - It assimilates weakly coupled at first: each module's observations
    update that module only. Strong coupling is adopted only if a
    registered test shows it gains.
  - Every channel whose two ends are both measured is a sheaf edge, and
    its coboundary (bnm.sheaf) is a consistency check and a candidate
    contradiction.
  - It writes one coupled state table with provenance, which bnm.states
    reads in place of the separate stock files.
- **Gaps.** These sources are not on disk:
  - the World Bank BRSS (government-owned bank share);
  - V-Dem's full dataset (v2clstown, state ownership of the economy; only
    the Core file is on disk);
  - the OECD financial balance sheets;
  - China's loans by industry (PBoC);
  - capital-control measures by asset class (Fernández et al.);
  - expropriation and privatisation events (Kobrin, Minor, Hajzler, the
    Privatization Barometer);
  - the OECD SOE dataset.

  These models are not built: the SFC core, the control layer, sectoral
  credit allocation, capacity by sector from investment, typed
  real-to-political inputs, and the political-to-control hazards.
- **Plan.**
  1. The interface and engine, wrapping today's modules, which must
     reproduce today's stock files exactly.
  2. The SFC core.
  3. The control layer.
  4. Allocation and capacity.
  5. Real to political.
  6. Coupled stepping with assimilation.
  7. Political to control.
  8. Hindcasts.
  9. The registered event test.

  Five new beads are proposed (section 10).

## 1. What the trials since 44 say this layer is for

docs/PHASE-CHANGES.md, trials 44, 45 and T1-T9:

- **Trial 44.** Links between event kinds, taken as own parts, were honest
  but level.
- **Trial 45.** Of 4,530 kind-state candidates from the stock-flow models,
  three passed once years rather than country-years were counted. All
  three were slow levels (land, grain use, power capacity), and the
  forecasts were level.
- **T4.** The thirteen states (credit gap, Keen debt gap, debt service,
  reserves, youth bulge, elite overproduction, anocracy, food import
  exposure, arable land, grain use, power capacity, fuel price gap,
  technology gap) say where more than when:
  - where over when, +0.0026 log [+0.0013, +0.0037];
  - material and social groups pass against coverage-matched placebos;
  - over the recalibrated base they are equivalent to nothing.
- **T5.** On 150 years of JST crises the financial cycle is real (house
  prices and the credit gap, 2007-08 mostly). The lattice already holds it.
- **T9.** When slow gauges go dark or a fifth of countries lose data, the
  states hold back about a quarter of the loss, about a fifth better than
  placebos. Calibration steadying did not recur, and a small worst-era
  cost kept them out of the base.

Three consequences for the design:
1. **The coupled state is a structural model first and a forecasting input
   second.** Its first products are:
   - consistent accounts;
   - attributions (how much of the change in Korea's manufacturing
     capacity ran through directed credit);
   - counterfactuals (China's 2008 stimulus with Western allocation).

   None of these can be scored on onsets with the power the event panels
   have (the power audit: OR about 1.3 per SD detectable at best for a
   country state).
2. **Where it enters the event model, it enters as T9 found useful.** It is
   a cross-sectional where and a fallback when inputs fail. Its registered
   test uses T9's criteria and the use-case rule of bnm-82j.1 (top-k
   recall and precision on hard onsets, lead time), not mean log score.
3. **The tests with power are on paths, not events.** Credit allocation by
   sector has about 3,200 country-years with total, household and
   manufacturing credit (Müller-Verner). Sector accounts have about 1,300
   (OECD Table 14). Capacity by product has thousands of rows (BGS, BACI).
   Hypotheses about channels are tested there, with country and year
   effects, as ECONOMIC-CHARACTER did for migration.

## 2. Literature

DOIs and URLs were checked against Crossref or the publisher's site
(2026-10-23).

### 2a. Stock-flow consistent models: how they join money and goods

- Godley, W. & Lavoie, M. (2007). *Monetary Economics: An Integrated
  Approach to Credit, Money, Income, Production and Wealth*. Palgrave
  Macmillan. https://doi.org/10.1057/9780230626546.
  - Two matrices define the model. The balance-sheet matrix gives every
    sector's financial assets and liabilities, each one some other sector's
    counterpart, so the columns sum to net worth and the rows to zero. The
    transaction-flow matrix gives every flow (wages, consumption,
    investment, interest, taxes, the changes in each asset) as a row
    summing to zero and a column summing to zero.
  - The real economy is in the matrix: output, investment and wages are
    flows between firms and households, with inventories and fixed
    capital. The financial economy is in it too: loans and deposits,
    bonds, equity and their changes. A loan to a firm is the deposit it
    spends on investment goods.
  - Behaviour is added on top: consumption from income and wealth,
    portfolio choice (Tobin), firms' pricing and investment, banks'
    lending. The accounts then pin down the remaining variables, one flow
    being the residual that closes them (the "redundant equation", checked
    as a test of the code).
  - Their models SIM, PC, LP, INSOUT and BMW grow from the simplest to
    open economies with banks and inventories.
- Caverzasi, E. & Godin, A. (2015). "Post-Keynesian stock-flow-consistent
  modelling: a survey." *Cambridge Journal of Economics* 39(1): 157-187.
  https://doi.org/10.1093/cje/beu021. The extensions are
  financialisation (shareholder value, household debt), open economies and
  growth with distribution (Kaleckian investment functions). Calibration
  is the weak point: most models are calibrated to a steady state and run
  as scenarios, not estimated.
- Nikiforos, M. & Zezza, G. (2017). "Stock-flow consistent macroeconomic
  models: a survey." *Journal of Economic Surveys* 31(5): 1204-1239.
  https://doi.org/10.1111/joes.12221. Lineage, method, empirical
  models. The empirical ones (the Levy model, the Danish and Greek
  models) are small and estimated equation by equation, with the
  accounting as identities.
- Levy Institute macro model (Godley's, continued by Papadimitriou and
  Zezza): the Strategic Analysis series,
  https://www.levyinstitute.org/publications/?type=strategic-analysis;
  the Greek model at
  https://www.levyinstitute.org/publications/a-levy-institute-model-for-greece.
  - It is built on the three sectoral balances: private, government and
    foreign net lending sum to zero.
  - It projects medium-term paths from that identity plus a few estimated
    stock-flow norms (households' wealth to income).
  - It is known for warning in 1999-2007 that the US private deficit was
    unsustainable. That is a structural warning with no date attached:
    the T1/T4 lesson in another form.
- Dafermos, Y., Nikolaidi, M. & Galanis, G. (2017). "A stock-flow-fund
  ecological macroeconomic model." *Ecological Economics* 131: 191-207.
  https://doi.org/10.1016/j.ecolecon.2016.08.013. The DEFINE model,
  https://define-model.org.
  - An SFC monetary core is joined to Georgescu-Roegen's flow-fund
    accounts of matter and energy: material extraction and energy use per
    unit of output, recycling, reserves that deplete, emissions, climate.
  - The coupling runs both ways. Output needs matter and energy. Climate
    damage destroys capital and lowers firms' profits, which raises their
    default rate. Banks then tighten credit, which lowers investment.
  - Green and conventional loans are separate stocks with their own rates,
    which is allocation by sector in its simplest form.
- Jackson, T. & Victor, P. A. (2020). "The transition to a sustainable
  prosperity: a stock-flow-consistent ecological macroeconomic model for
  Canada." *Ecological Economics* 177: 106787.
  https://doi.org/10.1016/j.ecolecon.2020.106787. LowGrow SFC,
  extending Victor (2008), *Managing Without Growth*, Edward Elgar.
  - A full SFC model for Canada is calibrated to the national accounts and
    the national balance sheets (Statistics Canada's sector matrices).
  - Industries are split (a green and a conventional capital stock).
  - It is run as scenarios. Validation is a fit to 1990-2017 history,
    not an out-of-sample test.

**What an SFC core is on our data.** Five sectors per country-year:
households and NPISH (S14_S15, "S1M" in the OECD file), non-financial
corporations (S11), financial corporations (S12), general government (S13)
and the rest of the world (S2).

- **Transactions (flows), on disk.** OECD Table 14 (DF_TABLE14, the
  successor of SNA Table 720's non-financial accounts) has, by sector:
  - value added (B1G);
  - compensation of employees (D1);
  - operating surplus (B2G);
  - property income paid and received (D4, D41 interest, D42 dividends);
  - taxes (D5) and social contributions and benefits (D6);
  - saving (B8G);
  - gross fixed capital formation (P51G);
  - net lending (B9).

  That covers 42 countries, mostly from 1995, some from 1950. Eurostat
  nasa_10_nf_tr duplicates it for the EU. The Fed's Z.1 has the US to
  1946.
- **Balance sheets (stocks), partly on disk.**
  - BIS total credit gives credit to households, NFCs and government
    (WS_TC; about 44 economies, quarterly, some from 1940).
  - JST has bank loans, mortgages and public debt (18 countries from 1870).
  - The IMF Global Debt Database has the DataMapper series.
  - Z.1 is the full US matrix.
  - WID has private and public wealth over national income.
  - Missing: the OECD financial balance sheets by sector and instrument
    (Table 710/720), which would close the stocks the way Table 14 closes
    the flows.
- **Real side.** The I-O spine (LR-WIOD 1965-2000, WIOD 2016, ICIO 2023)
  and EXIOBASE (1995-2022, eight years on disk, plus 2011 hybrid) give
  inter-industry flows. EU KLEMS 2023 capital and labour accounts give
  capital stocks, investment and hours by industry. PWT has capital and
  labour share.

The core is the Godley-Lavoie frame with a learned closure. The identities
are imposed as they are. The behavioural equations are the existing
modules' (Keen's wage bargaining and investment; household saving from
income and wealth) or are new and learned: allocation, portfolio and
government balance. The frame does not replace Keen. It is the bookkeeping
Keen lacks: in bnm.keen, d is private debt to output from whichever source
is longest, and nothing says the interest paid on it is someone's income.

**The core as built (bnm-4hv.10), and what it shows (October 2026).**
scripts/sfc-data.py builds the panel. Flows come from OECD Table 14 and
stocks from Table 720 by instrument. EWN supplies the rest of the world
where Table 720 has none. Ten countries get stocks imputed from property
income, with their debt measured where BIS or the IMF has it (below).
bnm.sfc books the transaction-flow matrix and the flow of funds,
and bnm.modules.sfc runs them as F, R and D engine modules.
bnm.sfc-run (validation.txt) scores the core and bnm.sfc-audit (audit.txt,
audit-revised.txt) audits its assumptions. Both write to
data/derived/sfc/.

The core is consistent. It does not forecast better than persistence.

- *Accounting.* In simulation the transaction-flow matrix, the balance
  sheet and the flow of funds close to 4e-14 of GDP. This holds in the
  data-driven one-year steps as well as in free runs. The redundant
  equation's buffer is at most 9e-14 of GDP, so it hides no error. The
  measured balance sheets are another matter, because they do not close:
  - Each instrument's gap is moved to the rest of the world at the start.
    The median gap is 2e-6 of GDP, the 90th percentile 0.63 and the
    largest 2.9 (New Zealand). It is over 0.1 of GDP in 214 of 931
    origins: BRA CHL CHN COL CRI FIN FRA IRL NZL SWE USA ZAF.
  - Imputed stocks never close, by construction.
  - validation.txt now reports this closure. It belongs to the data, not
    the model, and it is not part of the buffer.
- *Review fixes.* These are all in the "bug fixes" numbers below.
  - Chile's S1 B1G is at basic prices, so y now adds D21X31. Before,
    Y - C - I was 11% of GDP below X - M, and nominal growth was
    over-forecast by 16 points a year.
  - Exports were learned from the trade data, whose balance is not the
    frame's Y - C - I. Each first step jumped by that gap.
  - Rates and positions were learned on the raw balance sheet but run on
    the closed one, so the closure earned interest. Chile's rest of the
    world received 1.5% of GDP that the data never paid.
  - An implicit rate was the weighted mean of yearly ratios. One year
    with a near-empty stock wrecked it: Bulgaria's household rate in
    2005 was 22% (true 4%), and China's financial dividend yield was 219.
    It is now a ratio of weighted means.
  - keen.csv was read at origin + 1, whose state is origin - 1. Employment
    therefore lagged a year, and so did the employment persistence it was
    scored against. It is now read at the panel's vintage, origin + 2,
    whose state is the origin.
  - Imputation took the nearest year's pooled rate, which is a later year
    for France and Sweden in 1950-69. It now takes that year's rate or an
    earlier one, and years before any measured rate are dropped. That
    cuts 217 imputed country-years to 177.
  - Imputed debt (bead bnm-a0s, a fix made after the result). Interest
    over the pooled implicit rate overstated debt wherever lending rates
    sit above the pool's. Private debt came out at 2.5 of GDP for South
    Africa, 4.4 for Costa Rica and 2.6 for Brazil (BIS: 0.69, 0.56 and
    0.49). Government debt was overstated too: Brazil 2.3 against 0.67.
    Households', NFCs' and government's debt now come from BIS total
    credit (year-end, national currency, known y + 1), else from the IMF
    Global Debt Database (Costa Rica). Where both sources cover a Table 720
    year, BIS's private debt matches Table 720's within 1% in 24 of 30
    countries. The private sector's debt at the pooled rates, divided by
    the measured debt, gives a country rate factor (median 1.2, Brazil 5.4,
    South Africa 3.3). The other interest-bearing positions are divided by
    that factor; equity is not. With neither source, the factor comes from
    the WDI lending rate (a three-year mean, times the pool's ratio of
    implicit to lending rates). Run on the measured countries, each left out
    of the pool, that path misses Table 720's private debt by a median of
    0.35 of GDP (90th percentile 1.2; 316 country-years, 15 countries). The
    pooled rate misses by 0.58 (3.1), and that is in sample. The
    three-year mean was chosen after this year's rate alone missed by
    0.353. No imputed row needs this path today. A row with no source at
    all is dropped. All 177 rows keep their stocks: 167 from BIS and 10
    from the GDD. The flow-of-funds residual had also been taken
    across an imputed opening balance sheet (FRA, SWE and FIN in 1995,
    IRL, NZL, BRA and COL). It now needs both years measured.
  - The US 2009 direction is now also scored against the model's own 2008.
- *Leakage.* Data are the latest OECD and EWN vintages, revised. Model
  and persistence both see them, so persistence is not favoured. Keen's
  calibration as of origin + 2 ends at origin - 1 or earlier. The
  half-life (1 of 1, 2, 3, 5), the rate half-life and the dividend rule
  rank the same on the design and the test origins, so there is no sign
  of tuning on the test origins.
- *Signs.* New Zealand's rest-of-world B9 is flipped from 1998 because it
  is recorded from the nation's side. After the flip it is minus S1's,
  S1's equals the four domestic sectors' sum, and the persistent deficits
  appear as rest-of-world lending, which is right. No other country's
  B9, D41 or D42 is out of balance, except China's property income, off
  by 1.3% (D41) and 2.7% (D42) of GDP in the data.
- *US 2009 financial sector.* The data's swing is +2.7 points of GDP.
  It comes from:
  - financial value added, +1.0 (FISIM: margins widened);
  - other flows, +0.7 (crisis capital transfers);
  - lower dividends paid, +0.5;
  - lower compensation, +0.4;
  - the interest margin, +0.3.

  The core holds financial value added as a fixed share of output and
  has no capital-transfer rule, and investment, the one thing given, moves
  none of these. This is a missing channel, not a bug. The miss is the
  same under the revised rules and against the model's own 2008.
- *China.* Signs are right in 27 of 27 one-year forecasts and 10 of 10
  free-run years. The magnitudes are low: households 0.06-0.07 against
  0.10-0.14 in the free run from 2009. That is the consumption and
  other-flow rules, not the frame.
- *Engine.* validate is empty. The balance sheet is a level from
  :observe, which integrates last year's flow of funds through a lag-1
  read. That is allowed, since it is not a same-year rate. Everything
  else is a rate from :step. The engine reproduces bnm.sfc/step's output,
  net lending and balance sheets to 1e-12. It is now tested under the
  revised rules too.

*Assumptions.* "Removed" is how much of the model's excess one-year error
over persistence goes away when that rule alone is replaced. "oracle"
uses next year's observed ratio and "persist" uses the origin's own
ratio. The design origins run to 2002 (n 223), after the bug fixes. The
model's MAE is 0.0186 against persistence's 0.0120. With every block
given, the model's MAE is 0.0005, so the harness reproduces the data.

| assumption | source | removed (oracle / persist) | the data |
|---|---|---|---|
| consumption C = a1 YD + a2 V(-1) | Godley-Lavoie; ridge fit, 10-year half-life | 0.61 / 0.29 | worst rule. Fitted propensity misses next year's by 0.027, last year's by 0.016 |
| NFC investment kappa(pi) | Keen (G&CL form), fitted per country | 0.36 / 0.21 | the level misses by 0.024 against 0.010 for last year's share. dkappa on dpi has slope 0.29 (Keen's 0.55) and R2 0.10, but keeping the slope did worse on design |
| interest: implicit rate x opening stock | accounting, rates fitted | 0.29 / 0.24 | stock revaluations and reclassifications pass straight into interest |
| other flows (transfers, capital transfers, discrepancies) | held shares | 0.13 / -0.01 | already persistence |
| wage share by Keen's Phillips curve | keen-run calibration | 0.11 / 0.08 | annual dlog w on Phi(lambda) - alpha: slope 0.03, R2 0.003. No Phillips curve at an annual step |
| government grows at trend n | fiscal rule (prior) | 0.10 / 0.00 | identical to holding its share of expected output |
| tax and contribution rates | fitted | 0.09 / 0.04 | |
| production structure (v, omega, tp) | fitted shares | 0.05 / 0.02 | |
| household and financial investment shares | fitted shares | 0.04 / 0.00 | |
| exports share, import share mu | fitted (0.3 default) | -0.01 / -0.01 | the 0.3 default applies to no country-year. mu runs from 0.12 (US) to 1.95 (LUX); above 1 it turned the multiplier's sign, and mu is now over absorption plus exports (9h, bnm-4hv.17) |
| expected growth n | fitted | -0.03 / -0.02 | |
| dividends as shares of output | prior (the dividend fix) | -0.05 / 0.00 | helps on design and test alike |
| employment dl/l = (kappa - kappa-bar)/nu | Keen | not in one-step net lending | slope 1.32, R2 0.16: supported. One year ahead, MAE 0.0098 against persistence 0.0094 |
| equity repriced at n; buffer to government deposits | accounting choices | none on net lending | |
| wage-share and employment guards | numerical | none | one-step: 1 of 931 each. Free runs, Keen rules: w 8 of 350 (BGR, DEU, EST, POL), lambda 1. Revised rules: 0 |

*The revision.* This was made after the first result had been seen.
Replacements were tried test-first, as learn options, and chosen on the
design origins only:
- Godley-Lavoie consumption anchored on the observed propensity, with
  reversion of saving to a norm (Levy Institute style);
- the wage share reverting to a norm;
- investment in differences with Keen's slope plus reversion;
- interest taking a share theta of the stock change.

In every case design chose zero reversion, no Keen slope and theta 0.
Shorter half-lives (0.5) also helped. The selected set is bnm.sfc/revised.
It amounts to holding each behavioural ratio, and the frame's identities
still bind. As a ceiling, damped mean reversion of net lending itself
(phi 0.2 to a 5-year norm) gets skill +0.03 (design) and +0.035 (test).

| four domestic sectors, skill vs persistence | all | design (to 2002) | test (2003 on) |
|---|---|---|---|
| as committed (980561c), n 963 | -0.489 | -0.592 | -0.461 |
| bug fixes, Keen rules, n 931 | -0.438 | -0.552 | -0.408 |
| bug fixes, intercept-corrected | -0.185 | -0.260 | -0.166 |
| revised rules (post hoc), n 931 | -0.056 | -0.045 | -0.059 |
| imputed-debt fix (bnm-a0s, after the result), Keen rules | -0.441 | -0.557 | -0.411 |
| imputed-debt fix, intercept-corrected | -0.188 | -0.269 | -0.168 |
| imputed-debt fix, revised rules | -0.056 | -0.046 | -0.059 |

The imputed-debt fix moves the one-year skill by at most 0.01. Imputed
stocks are 142 of 931 origins, and the rules mostly hold ratios. On
imputed origins alone, skill goes from -0.629 to -0.612 under Keen's rules.

Under the revised rules on the test origins, households are at +0.003,
NFCs -0.001, government -0.04 and the financial sector -0.35, which is
the residual margin. The core is consistent. Its behavioural rules,
Keen's included, carry no one-year forecast information beyond holding
ratios. Keen's rules stay the default as the theory the coupled model is
built on, and bnm.sfc/revised is the forecasting baseline (superseded:
see the status line after the estimated rules below, and 9g's
Decision).

**Rules estimated from data (bead bnm-4hv.14, October 2026).** The goal
is rules that forecast sector net lending and private debt better than
persistence and damped reversion, at one year and at three to five,
with the identities still binding. Everything below was chosen on the
design origins (origins to 2002) before the registered test of 9g.
The test origins (2003 on) were not scored for any of it. The fits
(the slopes) use data years to the origin only, but the design origins
were scored at targets up to o + 5, so the choices among rule sets read
outcomes in data years 2003 to 2007 too (review note in 9g).

- *The harness.* bnm.sfc-rules-run design scores the restart (9f's: one
  member, learned at o on the panel as known at o + 2, started from o)
  at horizons 1, 3 and 5. It uses 215 design origins in 28 countries,
  measured or imputed stocks, with five years of flows; the
  hyperinflation rule of 9f leaves out BGR and ROU. The skill is
  1 - SSE / SSE of the baseline, pooled over the four domestic sectors'
  net lending, with private debt (measured) apart. Intervals are 90%
  country bootstraps.
- *What drifts.* Under the revised rules the restart lost to persistence
  by 0.14 / 0.21 / 0.35 on net lending at 1 / 3 / 5 years, almost all of
  it in the financial sector (-1.4 to -3.0). Splitting the five-year
  error by flow put the cause in interest. It was computed as the
  sectors' implicit rates times the model's own stocks, and the
  financial sector takes what the others pay less what they receive, so
  every drift in a modelled stock moved its margin. Interest was worse
  than holding its share in every sector (five-year RMSE: financial
  0.027 against 0.021, government 0.016 against 0.011, NFCs 0.019
  against 0.014).
- *Estimation.* Each candidate rule is an error correction of a ratio,
  dx_t = sum_j b_j z_j(t-1). The z are gaps to norms, each norm the
  country's own weighted mean (half-life 10 years) of its years to
  t - 1, so the norms are causal and country-specific (random
  intercepts). The slopes b are pooled over every country's data years
  to the origin (bnm.sfc/estimate-rule, refitted at each origin on the
  panel as known). Shrinking each country's slopes toward the pool
  (empirical Bayes, method of moments: DerSimonian & Laird 1986,
  Controlled Clinical Trials 7(3): 177-188) gave country slopes 8 to 30
  years can't pin down. Fiscal reaction ran from -0.38 to 1.16, and the
  five-year interval on net lending went down to -0.07. Common slopes
  with country norms beat it on the design origins: +0.22 [+0.12, +0.28]
  against persistence at five years, against +0.16 [-0.08, +0.28]. That
  is the partial pooling used (augur.hier has only logistic intercepts
  and Poisson states, no linear slopes). A rule is adopted only if it
  beats holding its ratio on the design origins; otherwise the ratio is
  held.

Pooled fits on data years to 2002 (bnm.sfc-rules-run estimates; loco is
the one-year skill against holding the ratio with each country left out
of the fit):

| rule | form | slopes (se) | loco | design-origin restart | kept |
|---|---|---|---|---|---|
| interest and dividends received | held as shares of output | - | - | net lending 1/3/5 years -0.14/-0.21/-0.35 -> 0.00/+0.04/+0.04; financial sector -1.4/-2.8/-2.9 -> +0.10/+0.04/+0.07 | yes |
| government spending (consumption, investment, benefits) | fiscal reaction: d(spend/Y) = b1 (norm - spend/Y) + b2 (NL_gov/Y - its norm) | b1 -0.112 (0.025), b2 0.238 (0.040) | +0.111 | with the rest held: 3/5 years +0.04/+0.04 -> +0.13/+0.15 | yes |
| NFC investment | d(I/Y) = b1 gap + b2 (profit share - norm) + b3 (interest paid / Y - norm) | 0.214 (0.052), 0.118 (0.046), -0.216 (0.058) | +0.082 | +0.04/+0.04 -> +0.13/+0.14 | yes |
| consumption | Godley-Lavoie wealth target: d(C/YD) = b1 gap + b2 (V/YD - norm) | 0.403 (0.068), 0.004 (0.005) | +0.028 | with the two rules above: 1/3/5 years +0.037/+0.204/+0.220 -> +0.009/+0.178/+0.188 | no: held at the origin's propensity |
| wage share | reversion to its norm | 0.000 (0.035) | -0.013 | - | no: held (an employment term needs a reversion to sit in; the Phillips curve has R2 0.003) |
| interest rates | partial adjustment of each sector's implicit rate, less the policy rate (BIS, the ECB's for euro members, known y + 1), to its norm, the policy rate held at the origin's | lambda 0.149 (0.030), n 768 | - | +0.204/+0.220 -> +0.161/+0.160; financial sector +0.05 -> -0.95/-1.39 at 3/5 years | no |
| financial value added | reversion | 0.115 (0.046) | -0.013 | - | no |
| other flows (capital transfers) | reversion of the financial sector's other flows | -0.051 (0.047) | -0.069 | phi 0.3 on all sectors: 3/5 years +0.204/+0.220 -> +0.164/+0.181 | no |
| household / NFC borrowing | reversion of debt to income | -0.123 (0.033) / -0.056 (0.040) | +0.012 / -0.004 | - | no: debt moves away from its norm, and the learned trend target stays |

- *Interest and dividends.* The financial sector's margin now moves only
  with output. The remaining 1-year miss in that sector (+0.11 against
  persistence at 1 year) is still the US 2009 channel: a widening
  value-added margin and crisis capital transfers. Both were tried as
  reversions and neither forecasts. A capital transfer is a one-off, and
  holding last year's (half-life 0.5) is as good as a reversion to a
  norm. The 2009 miss stays a missing channel, now on record as not
  forecastable from these accounts.
- *Interest rates and the policy rate.* The implicit rates do adjust to
  the policy rate, by 15% a year (de Bondt 2005, "Interest rate
  pass-through: empirical results for the euro area", German Economic
  Review 6(1): 37-78, finds slow pass-through for the stock of loans).
  Adjusting each sector's paid and received interest on its own,
  however, moves the financial sector's residual margin, which the data
  hold steady, and that loses. It stays held. A version that kept the
  banks' margin, as Godley-Lavoie's banks set deposit rates to it
  (ch. 10), would be the next thing to try.
- *Government.* Spending responds to the balance: a deficit 1 point of
  GDP wider than its norm cuts spending by 0.24 points the next year
  (Bohn 1998, "The behavior of U.S. public debt and deficits",
  Quarterly Journal of Economics 113(3): 949-963, for the primary
  balance's response to debt). The negative own-gap term is momentum
  against a 10-year norm: spending shares trended over the sample.
  Revenue's response (-0.07 of the gap) is left to the tax rates on
  their bases. The observed path is no forecast (9f's conditional runs),
  so the choice is a rule against holding the share, and the rule wins.
- *Investment.* Kalecki-Steindl, with debt service as the financial
  constraint (Fazzari, Hubbard & Petersen 1988; Lavoie 2014,
  *Post-Keynesian Economics: New Foundations*, Edward Elgar, ch. 6).
  Investment closes a fifth of its gap a year and rises with profits
  above their norm. A point of output more in interest paid cuts it by
  0.2. Keen's kappa(pi) in levels missed by 0.024 against 0.010 for
  holding the share (the audit), and this error-correction form beats
  holding.
- *Consumption.* The wealth term of the Godley-Lavoie norm is nil (0.004
  per unit of wealth to income). The propensity reverts by 0.4 a year
  one year ahead, but iterating it costs at three to five years. So the
  propensity is held at the origin's (half-life 0.1), which alone moved
  households' one-year skill from -0.10 to -0.02. The Levy Institute's
  stock-flow norm (Godley 1999, "Seven unsustainable processes", Levy
  Institute Special Report) describes where the sector balances must go
  eventually. In these data it doesn't date the move.
- *An add factor* (Clements & Hendry 1996, "Intercept corrections and
  structural change", Journal of Applied Econometrics 11(5): 475-494)
  set the other flows so that the model stepped from o - 1 reproduces o.
  It was worse at 1 year (-0.86): o's own shock is carried forward.
- *The ablation* (bnm.sfc-audit design estimated, audit-estimated.txt):
  one year ahead, the model's MAE is 0.0118 against persistence's 0.0120.
  Holding each block's ratio instead of its rule raises the MAE for NFC
  investment (+0.00007), government (+0.00004), other flows (+0.00029) and
  dividends (+0.00015), and changes it by under 0.00004 elsewhere. The
  rules matter at three to five years more than at one, as below.

The selected set, bnm.sfc/estimated (bnm.sfc-rules-run design, the
restart on the design origins):

| design origins, skill [90%] | 1 year | 3 years | 5 years |
|---|---|---|---|
| net lending vs persistence: Keen (9f's restart) | -1.82 [-3.63, -0.90] | -0.56 [-1.43, -0.18] | -0.86 [-2.29, -0.25] |
| revised | -0.14 [-0.32, -0.04] | -0.21 [-0.30, -0.12] | -0.35 [-0.43, -0.21] |
| everything held, interest as a share | +0.02 [-0.01, +0.05] | +0.04 [+0.03, +0.07] | +0.04 [+0.02, +0.06] |
| estimated | +0.04 [-0.02, +0.08] | +0.20 [+0.12, +0.26] | +0.22 [+0.12, +0.28] |
| net lending vs damped reversion: estimated | +0.05 [-0.02, +0.10] | +0.14 [+0.03, +0.20] | +0.09 [+0.02, +0.13] |
| private debt vs persistence: estimated | +0.16 [-0.05, +0.40] | +0.36 [+0.23, +0.45] | +0.20 [+0.15, +0.26] |
| private debt vs damped: estimated | +0.43 [+0.23, +0.63] | +0.61 [+0.51, +0.68] | +0.43 [+0.35, +0.51] |

The two rules cost a little on debt (everything held: +0.21 / +0.39 /
+0.22 against persistence). Most of the debt skill is the learned debt
trend, the old rule. These are in-sample choices: hyperparameters (the
half-lives, which rules) were picked on these origins, so the numbers
are optimistic. The one-year skill is within its interval of zero.

Status after the registered test (9g, decided 29 October 2026 after the
outcome): damped reversion is the net-lending forecast; estimated is the
best structural forecaster (it beats persistence and the everything-held
core on the test origins) but is not adopted and is not for scenarios;
the registered fallback to holding every ratio is not followed; scenario
runs keep Keen's rules (learn's defaults) until bnm-4hv.15 gives
interest a rate channel. It did (below): bnm.sfc/rates was non-inferior
to estimated in its registered test (9h), so scenario runs that move
rates or debt use it; learn's defaults are unchanged.

**Interest on the stocks, with a rate channel (bead bnm-4hv.15, October
2026).** estimated holds interest as a share of output, so scenarios have
no interest cost of debt and no rate shock (9g, review). The goal is
interest that is a rate times a stock, rates that follow the policy
rate, and a banks' margin that is its own behaviour rather than the
residual, with the core no worse than estimated on the design origins.
bnm.sfc/rates is estimated with interest-hold replaced by :rates
(bnm.sfc/rates-at, property-income). Everything below was chosen on the
design origins with targets to 2002 only (design --target-to 2002), so
the 2003-2007 overlap the review found is not in it. The test origins
(2003 on) were not scored for any of it.

- *Lending rates.* For households, NFCs and government, the implicit
  rate on money and debt liabilities (interest paid over the closed
  balance sheet a year before) moves as
  di_t = a dr_t + lambda (r_{t-1} + s - i_{t-1}), r the BIS policy rate
  (the ECB's for euro members, a full year's mean), s the country's
  spread over it (weighted mean of i - r, half-life 10). a is the
  immediate pass-through and lambda the closing of the gap to policy
  plus spread (de Bondt 2005, German Economic Review 6(1): 37-78: slow and
  incomplete for the stock of loans, faster for short-term rates). The
  slopes are pooled over every country's data years to the origin, one
  regression per sector (bnm.sfc/rate-rows). Fitted on years to 2002
  (155 rows each, 23 countries; se in brackets): households a 0.66
  (0.08), lambda 0.17 (0.07); NFCs 0.44 (0.05), 0.12 (0.06); government
  0.19 (0.06), 0.21 (0.05). Government's immediate pass-through is the
  smallest, as a stock of long bonds reprices slowly. In a forecast the
  policy rate is the origin's, held: the origin's mean is known a year
  on, and the forecast is made at origin + 2 on the panel as known then,
  which carries no later policy rate.
- *Interest = rate x stock.* Households', NFCs' and government's
  interest paid is the year's rate times the opening stock. The first
  year's rate is anchored: the effective rate that gives the held share
  of expected output on the origin's stock (what estimated forecasts for
  that year), with the whole path and its target shifted by the same
  amount, so the gap that drives pass-through is the data's. The
  measured stocks are at market value and carry reclassifications, so
  interest over the stock is a noisy rate, and the anchor stops that
  noise from setting the level. After the first year, interest moves
  with the rates and with the model's own stocks.
- *The banks' margin.* The financial sector's net interest (the others'
  interest paid less what they receive, its entry in the interest row)
  over output moves as dm_t = phi (norm - m_{t-1}) + beta dr_t
  (bnm.sfc/nim-rows). On years to 2002 (219 rows, 22 countries): phi
  0.031 (0.045), beta 0.038 (0.019). A policy rate a point higher widens
  the margin by 0.04% of output, and the margin barely reverts. Borio,
  Gambacorta & Hofmann (2017, "The influence of monetary policy on bank
  profitability", International Finance 20(1): 48-63) find net interest
  margins rise with the level of short rates; Claessens, Coleman &
  Donnelly (2018, "'Low-for-long' interest rates and banks' interest
  margins and profitability", Journal of Financial Intermediation 35:
  1-16) find low rates compress them; Ho & Saunders (1981, "The
  determinants of bank interest margins", Journal of Financial and
  Quantitative Analysis 16(4): 581-600) model the margin as the banks'
  own choice. In these accounts D41 is interest after FISIM, so the
  service margin sits in the financial sector's value added, which stays
  a held share (its reversion didn't forecast, table above).
- *Deposits close it.* What the domestic sectors receive is what
  households, NFCs, government and the rest of the world pay, less the
  rest of the world's receipts and the margin, split among households,
  NFCs and government by their held shares of output. The banks set
  deposit rates to earn the margin (Godley & Lavoie 2007, ch. 10), so a
  higher policy rate reaches depositors less the margin's response. The
  rest of the world's interest paid and received are held shares of
  output.
- *No policy rate* (Bulgaria, Costa Rica, and years before a series
  starts): lending rates held, the margin reverting.

The search, net lending (four sectors) against estimated on the design
origins, targets to 2002 (rates-design.txt; 858, 593 and 437 rows at 1,
3 and 5 years). Each line changes the one above; the margin of the
first form is on the financial sector's interest-bearing assets:

| form | 1 year | 3 years | 5 years |
|---|---|---|---|
| first: margin on assets, rate x opening stock, receipts by rate x deposits | -0.225 [-0.596, -0.038] | -0.097 | -0.104 |
| margin a share of output | -0.056 | -0.090 | -0.102 |
| (instead) interest on last year's stock grown at n | -0.031 | -0.086 | -0.106 |
| anchored first-year rates (opening stock) | -0.007 | -0.052 | -0.065 |
| receipts split by held shares | -0.007 | -0.035 | -0.059 |
| rest of the world's interest held (selected) | -0.007 [-0.019, +0.008] | -0.020 [-0.030, +0.002] | -0.014 [-0.028, +0.006] |
| selected, lending rates held (no pass-through) | -0.000 | -0.011 [-0.023, +0.004] | -0.015 [-0.028, -0.000] |

- The first form lost the financial sector again (-2.36 at one year): a
  margin on the banks' measured assets jumps with every revaluation of
  them. On output it holds.
- Rate x stock lost on government (-0.18 at one year) until anchored.
- Receipts by rate x deposits moved NFCs' receipts with the model's
  buffer deposits (+0.6% of output at five years against +0.2% in the
  data), and the rest of the world's interest moved with its stock.
- The criterion, set before the search began: no horizon or outcome
  (net lending, private debt) whose interval lies below 0. The selected form meets it; the
  version without pass-through just fails at five years. Eleven forms
  were scored on these rows, so the numbers are optimistic.

The selected core on the design origins, targets to 2002 (skill [90%]):

| | 1 year | 3 years | 5 years |
|---|---|---|---|
| net lending vs estimated | -0.007 [-0.019, +0.008] | -0.020 [-0.030, +0.002] | -0.014 [-0.028, +0.006] |
| net lending vs persistence | +0.030 [-0.016, +0.068] | +0.204 [+0.072, +0.257] | +0.281 [+0.114, +0.309] |
| net lending vs damped reversion | +0.040 [-0.023, +0.079] | +0.182 [-0.007, +0.233] | +0.144 [-0.035, +0.193] |
| private debt vs estimated | +0.004 [-0.000, +0.011] | +0.007 [+0.001, +0.015] | -0.000 [-0.017, +0.003] |
| government's net lending vs estimated | -0.017 [-0.041, +0.017] | -0.070 [-0.089, -0.008] | -0.056 [-0.082, -0.008] |
| NFCs' net lending vs estimated | -0.009 [-0.021, +0.002] | +0.013 [-0.024, +0.043] | +0.039 [+0.015, +0.079] |

It does not lose to estimated pooled, but it is not better: every net
lending point is a little below, and government's is below at three and
five years with its interval under 0. Interest that follows the stocks
costs government's forecast; the NFCs gain.

*Scenarios* (reported only; bnm.sfc-rules-run scenario,
rates-scenario.txt). For the 30 test-mask countries with a policy rate,
from each one's latest measured year, ten years: the base, the policy
rate 100bp higher from the first year and held, and government
consumption 1% of expected output higher in years 1-5, borrowed. The
median across countries of the difference from the base, over output:

| scenario, quantity | year 1 | 3 | 5 | 10 |
|---|---|---|---|---|
| +100bp: government's interest | +0.13% | +0.25% | +0.38% | +0.60% |
| households' interest | +0.26% | +0.28% | +0.30% | +0.35% |
| NFCs' interest | +0.28% | +0.37% | +0.45% | +0.57% |
| the banks' margin | +0.06% | +0.04% | +0.03% | +0.01% |
| government debt | +0.02% | +0.19% | +0.44% | +1.05% |
| spending +1%, years 1-5: government debt | +0.76% | +1.38% | +1.40% | -0.40% |
| government's interest | -0.01% | +0.03% | +0.05% | -0.01% |

- The identities hold in every run: the largest matrix, flow-of-funds or
  balance-sheet gap is 5e-14 of output.
- The rate shock passes through as fitted: within a year households'
  rates rise by two-thirds of the point and NFCs' by nearly half,
  government's by a fifth, and each then moves towards the full point
  as the stocks reprice. Its higher interest raises its debt.
- Borrowed spending raises the debt and, a year later, the interest on
  it. The fiscal rule then cuts spending against the wider deficit, so
  by year 10 the debt is back below the base. estimated gives the same
  debt path with no interest response (zero to 1e-5 of output), and has
  no response at all to the rate shock; neither do Keen's rules, whose
  rates are held.

### 2b. Who controls credit, and what it is allocated to

State against private banking:
- La Porta, R., Lopez-de-Silanes, F. & Shleifer, A. (2002). "Government
  ownership of banks." *Journal of Finance* 57(1): 265-301.
  https://doi.org/10.1111/1540-6261.00422.
  - They measure the government's share of the top ten banks' assets in 92
    countries in 1970 and 1995.
  - It is high and widespread (about 42% in 1995, far higher in poorer and
    less financially developed countries).
  - A higher 1970 share predicts slower later financial development and
    productivity growth. They read it as the political view (state banks
    serve politicians), against the development view (state banks fill
    market failures).
  - The two 1970 and 1995 snapshots are the long anchor for a control
    stock.
- Sapienza, P. (2004). "The effects of government ownership on bank
  lending." *Journal of Financial Economics* 72(2): 357-384.
  https://doi.org/10.1016/j.jfineco.2002.10.002. Italian state banks
  charged lower rates to similar firms, and their lending followed the
  electoral strength of the party controlling the bank: politics to
  allocation, measured at the loan.
- Dinç, I. S. (2005). "Politicians and banks: political influences on
  government-owned banks in emerging markets." *Journal of Financial
  Economics* 77(2): 453-479. https://doi.org/10.1016/j.jfineco.2004.06.011.
  State-owned banks raise lending in election years relative to
  private banks (19 countries): a political lending cycle, which is a
  sign prior for the politics-to-allocation edge.
- World Bank Bank Regulation and Supervision Survey (BRSS),
  https://www.worldbank.org/en/research/brief/BRSS. The share of banking
  assets in government-owned banks, five waves 1999-2019 (the 2019 wave
  covers 160 jurisdictions for 2011-2016). Not on disk.

Directed credit and development:
- Stiglitz, J. E. (1994). "The role of the state in financial markets."
  *Proceedings of the World Bank Annual Conference on Development
  Economics 1993*: 19-52. https://web.williams.edu/Economics/macro/stiglitz.pdf.
  Imperfect information makes credit markets ration and misallocate.
  Mild financial repression and directed credit can raise welfare when
  they come with monitoring. Theory for the development view.
- Amsden, A. (1989). *Asia's Next Giant: South Korea and Late
  Industrialization*. Oxford UP. https://doi.org/10.1093/0195076036.001.0001 (the
  DOI is of the 1992 edition). Wade, R. (1990). *Governing the Market*.
  Princeton UP. Studwell, J. (2013). *How Asia Works*. Profile/Grove.
  - Their common mechanism is reciprocal control. The state, through banks
    it owns or commands, gives cheap credit, protection and licences to
    manufacturers in exchange for export performance, and withdraws them
    when targets are missed.
  - Studwell's three steps are land reform, then manufacturing with export
    discipline, then finance directed to both. Where finance was liberated
    early (the Philippines, Thailand, Malaysia in his reading), credit went
    to property and conglomerate rents instead.
  - That is the allocation channel with a sign: state direction under
    export discipline raises the tradable share of credit.
- Pettis, M. (2013). *The Great Rebalancing*. Princeton UP. Klein, M. C. &
  Pettis, M. (2020). *Trade Wars Are Class Wars*. Yale UP.
  - Financial repression (deposit rates held below growth), an
    undervalued currency and suppressed wages transfer income from
    households to producers and the state.
  - Household consumption is held down (China's below 40% of GDP), and
    investment and the external surplus absorb the rest.
  - When investment runs out of productive uses, debt grows faster than
    debt-servicing capacity.
  - The sector-account version is household surplus against corporate
    deficit, which is on disk (section 3).
- Naughton, B. (2018). *The Chinese Economy: Adaptation and Growth*, 2nd
  ed. MIT Press. The institutional facts:
  - the big four state banks;
  - local-government financing vehicles;
  - the shift from the plan to the bank-credit system after 1994;
  - the 2009 stimulus run through bank lending and LGFVs;
  - SOE retrenchment in 1997-2003 ("grasp the large, let go of the
    small").

  The ICSD numbers in section 3 show part of this, with a caveat.

Finance in the West:
- Krippner, G. (2005). "The financialization of the American economy."
  *Socio-Economic Review* 3(2): 173-208.
  https://doi.org/10.1093/SER/mwi008. Krippner (2011). *Capitalizing
  on Crisis*. Harvard UP. Measured by where profits are made, US
  accumulation moved to financial channels from the 1970s. Non-financial
  firms' portfolio income rose against their productive income. The 2011
  book reads it as the state's response to the 1970s distributional
  crisis: deregulating credit let the state stop allocating. That is
  politics to control.
- Lazonick, W. & O'Sullivan, M. (2000). "Maximizing shareholder value: a
  new ideology for corporate governance." *Economy and Society* 29(1):
  13-35. https://doi.org/10.1080/030851400360541. Lazonick, W.
  (2014). "Profits without prosperity." *Harvard Business Review*,
  September, https://hbr.org/2014/09/profits-without-prosperity. "Retain
  and reinvest" gave way to "downsize and distribute" through buybacks.
  The NFC payout measures of econ/financialisation come from here.
- Jordà, Ò., Schularick, M. & Taylor, A. M. (2016). "The great
  mortgaging: housing finance, crises and business cycles." *Economic
  Policy* 31(85): 107-152. https://doi.org/10.1093/epolic/eiv017.
  Database: https://www.macrohistory.net/database/ (on disk).
  - In 17 advanced economies, mortgage credit rose from about a third of
    bank lending in 1900 to about two thirds by 2010.
  - Banks became real-estate lenders to households rather than lenders to
    business.
  - Mortgage booms predict deeper recessions and crises.
  - This is the long-run allocation drift in the West.
- Mian, A., Sufi, A. & Verner, E. (2017). "Household debt and business
  cycles worldwide." *Quarterly Journal of Economics* 132(4): 1755-1817.
  https://doi.org/10.1093/qje/qjx017. A rise in household debt to
  GDP predicts lower growth three to four years on (30 countries).
  Firm-debt rises do not. The mechanism is credit-supply shocks that raise
  consumption, then force deleveraging.
- Müller, K. & Verner, E. (2024). "Credit allocation and macroeconomic
  fluctuations." *Review of Economic Studies* 91(6): 3645-3676.
  https://doi.org/10.1093/restud/rdad112. The sectoral credit database:
  117 countries, 1940-2014, credit by ISIC sector and households. On disk:
  data/raw/slow/mullerverner, Build/MuellerVerner.dta.
  - Credit booms to households and to the non-tradable sectors
    (construction, real estate, trade, hospitality) predict slowdowns and
    crises.
  - Credit to tradables (agriculture, mining, manufacturing) predicts
    neither, and goes with productivity growth.
  - This is the strongest evidence that what credit goes to matters, not
    only how much.

**Can sector-level credit allocation be measured across countries and
years from open data?** Yes, with holes:

- Müller-Verner covers 98 countries with total, household and
  manufacturing credit (about 3,200 country-years; 31 countries in the
  1960s, 95 by 2000). It stops in 2014. Its US has only households
  against business, and its China has no manufacturing split.
- After 2014, BIS total credit splits households from NFCs (about 44
  economies, to now) but not NFCs by industry.
- National sources give industry splits: the ECB's MFI loans by NACE,
  Japan's BoJ loans by industry, Korea's BoK, the PBoC's loans by use and
  industry.
- For the US, Z.1 splits households (mortgage, consumer), non-financial
  corporate and non-corporate business, and the FDIC call reports split
  real estate from commercial and industrial loans.
- Investment by industry is the other side of the flow: EU KLEMS capital
  accounts (on disk) and EXIOBASE's final demand by product (GFCF column,
  on disk).

So allocation shares can be built for most of the sample to 2014, and
extended household-against-business to now. Industry detail after 2014 is
a download per country.

### 2c. The economy to politics

- Acemoglu, D. & Robinson, J. A. (2006). *Economic Origins of Dictatorship
  and Democracy*. Cambridge UP. https://doi.org/10.1017/CBO9780511510809.
  - Regimes are set by distribution and the threat of revolt. Elites
    concede democracy when the masses' threat is credible and repression
    is costly.
  - Elites mount coups when redistribution under democracy is costly to
    them.
  - Inequality is non-monotone: too low and the masses don't push, too
    high and elites resist.
  - The composition of elite wealth matters: land is easy to tax and so
    resists democracy, while mobile capital eases it.
  - Business cycles and crises create windows where the threat is
    credible.
- Boix, C. (2003). *Democracy and Redistribution*. Cambridge UP.
  https://doi.org/10.1017/CBO9780511804960. Democracy is likelier
  where inequality is low and assets are mobile (capital that can leave
  cannot be heavily taxed). So the real economy's composition (land and
  oil against manufacturing and finance) moves the regime. That gives the
  edge from real-economy structure to politics a sign.
- Turchin, P. (2016). *Ages of Discord*. Beresta Books. Goldstone, J. A.
  (1991). *Revolution and Rebellion in the Early Modern World*. U.
  California Press.
  - Structural-demographic theory: popular immiseration (wages against
    output, with urbanisation and youth), elite overproduction and state
    fiscal distress combine into the instability index Psi.
  - Already in bnm.tension (MMP, EMP, SFD, measured not simulated).
  - In a coupled model, SFD's government debt and deficit come from the SFC
    government sector, MMP's wage from the distribution block, and EMP's
    top share from the wealth stocks. These are typed inputs rather than
    WDI columns.
- Food prices and unrest:
  - Bellemare, M. F. (2015). "Rising food prices, food price volatility,
    and social unrest." *American Journal of Agricultural Economics*
    97(1): 1-21. https://doi.org/10.1093/ajae/aau038. Food price
    levels, not their volatility, cause unrest (instrumented, 1990-2011).
  - Arezki, R. & Brückner, M. (2011). "Food prices and political
    instability." IMF WP 11/62. https://doi.org/10.5089/9781455221066.001.
    International food price rises raise riots and civil conflict and
    weaken democratic institutions in low-income countries, not in
    high-income ones.
  - Hendrix, C. S. & Haggard, S. (2015). "Global food prices, regime type,
    and urban unrest in the developing world." *Journal of Peace Research*
    52(2): 143-157. https://doi.org/10.1177/0022343314561599. The
    effect is stronger in democracies and hybrid regimes.

  The channel runs through exposure (import dependence, the food share of
  spending) and through regime type. bnm.food already has the exposure
  (storage pressure times import dependence).
- Regime and economy, the evidence docs/WORLD-MODEL.md already cites:
  - financial crises raise polarisation and the far right's vote about 30%
    (Funke, Schularick & Trebesch 2016);
  - spending cuts drive riots (Ponticelli & Voth 2020);
  - output contractions bring forward regime change (Burke & Leigh 2010);
  - commodity price falls spark civil wars (Ciccone 2018).

### 2d. Politics to control

- Kobrin, S. J. (1984). "Expropriation as an attempt to control foreign
  firms in LDCs: trends from 1960 to 1979." *International Studies
  Quarterly* 28(3): 329-348 (DOI not verified).
  - 563 expropriation acts, 1960-1979, peaking in 1975.
  - Concentrated in extraction (oil, mining) and banking.
  - Selective, not wholesale: mostly acts to gain control of particular
    sectors, often after regime change.
- Minor, M. S. (1994). "The demise of expropriation as an instrument of
  LDC policy, 1980-1992." *Journal of International Business Studies*
  25(1): 177-188. Expropriations fell almost to zero in the 1980s, which is
  the regime of the debt crisis, the IMF programmes and privatisation.
- Hajzler, C. (2012). "Expropriation of foreign direct investment: sectoral
  patterns from 1993 to 2006." *Review of World Economics* 148(1):
  119-149. https://doi.org/10.1007/s10290-011-0103-0. The data run
  1960-2006. A second wave came in the 2000s (Venezuela, Bolivia, Russia),
  again in resources, and again when commodity prices were high.
- Guriev, S., Kolotilin, A. & Sonin, K. (2011). "Determinants of
  nationalization in the oil sector: a theory and evidence from panel
  data." *Journal of Law, Economics, and Organization* 27(2): 301-323.
  https://doi.org/10.1093/jleo/ewp011. Nationalisation is likelier
  when oil prices are high and when constraints on the executive are weak
  (1960-2006). That makes a hazard with two typed inputs: world price
  (bnm.cycles) and institutions (V-Dem). It is the cleanest
  politics-to-control channel in the literature.
- Privatisation waves:
  - the World Bank privatization database (developing countries,
    1988-2008; its landing page is gone);
  - the Privatization Barometer (FEEM/KPMG, EU and OECD from 1977,
    https://www.feem.it/en/?p=40423).

  Privatisations cluster after fiscal crises, under IMF programmes and
  after transitions to democracy or out of communism (Megginson & Netter
  2001, JEL, for the survey).
- Capital controls:
  - Chinn, M. D. & Ito, H. (2006). "What matters for financial
    development? Capital controls, institutions, and interactions."
    *Journal of Development Economics* 81(1): 163-192.
    https://doi.org/10.1016/j.jdeveco.2005.05.010. KAOPEN,
    https://web.pdx.edu/~ito/Chinn-Ito_website.htm, on disk (185
    countries, 1970-2023).
  - Fernández, A., Klein, M. W., Rebucci, A., Schindler, M. & Uribe, M.
    (2016). "Capital control measures: a new dataset." *IMF Economic
    Review* 64(3): 548-574. https://doi.org/10.1057/imfer.2016.11. By
    asset class and direction, 100 countries, 1995-, updated. Not on disk.
  - Controls tighten after crises (Malaysia 1998, Iceland 2008, Cyprus
    2013). They are a control lever that decides whether domestic credit
    can be allocated against world returns.
- State capture: Hellman, J. S., Jones, G. & Kaufmann, D. (2003). "Seize
  the state, seize the day: state capture and influence in transition."
  *Journal of Comparative Economics* 31(4): 751-773.
  https://doi.org/10.1016/j.jce.2003.09.006. Firms that shape the
  rules gain at the expense of the rest. This is the feedback from
  economic concentration to political control. V-Dem's corruption indices
  (v2x_corr, on disk) are the standard proxy.
- Varieties of capitalism:
  - Hall, P. A. & Soskice, D. (2001). *Varieties of Capitalism*. Oxford UP.
    https://doi.org/10.1093/0199247757.001.0001. Liberal against
    coordinated market economies, with complementary institutions
    (finance, labour, training).
  - Milanović, B. (2019). *Capitalism, Alone*. Harvard UP. Liberal
    meritocratic against political capitalism, where a bureaucracy with
    discretionary law directs capital and growth is its legitimacy.

  As in docs/ECONOMIC-CHARACTER.md section 3, these are validation labels
  for a learned factor, not inputs.

### 2e. Integrated and coupled modelling practice

- **World3** (Meadows et al. 1972, *The Limits to Growth*, Universe Books;
  Meadows et al. 1974, *Dynamics of Growth in a Finite World*,
  Wright-Allen; World3-03 on disk as bnm.world3).
  - The sectors are population, capital, agriculture, nonrenewable
    resources and pollution. They couple through a few auxiliaries, and the
    key coupling is allocation.
  - Industrial output IO is split into agriculture (FIOAA), services
    (FIOAS), consumption (FIOAC) and, as the residual, reinvestment in
    industry (FIOAI = 1 - FIOAA - FIOAS - FIOAC).
  - Each fraction is a table function of a perceived shortfall: food per
    head against indicated food per head, services per head against
    indicated. This is World3's control layer, a fixed planner's rule.
  - The resource sector couples back through the fraction of capital
    allocated to obtaining resources (FCAOR), which rises as resources
    deplete.
  - World3's lesson for us: allocation is where the sectors meet. In
    World3 it is set by hand; here it is to be learned and to depend on who
    controls finance.
- **Integrated assessment models** (IAMs) couple modules with defined
  interfaces.
  - DICE: Nordhaus, W. (2017). "Revisiting the social cost of carbon."
    *PNAS* 114(7): 1518-1523. https://doi.org/10.1073/pnas.1609244114.
    A single optimisation over economy and climate, hard-linked.
  - IMAGE 3.0: Stehfest, E. et al. (2014), PBL,
    https://www.pbl.nl/en/publications/integrated-assessment-of-global-environmental-change-with-image-30-model-description-and-policy-applications.
    Modules for energy, land, agriculture, climate and water. Each one
    declares the variables it passes, and they exchange them at fixed time
    steps (one to five years).
  - GCAM, https://jgcri.github.io/gcam-doc/. Energy, land, water and
    economy solved jointly for market-clearing prices every five years.
  - MESSAGEix, https://docs.messageix.org. An optimisation core with
    soft-linked MACRO.
  - Practice on coupling:
    - Krook-Riekkola, A., Berg, C., Ahlgren, E. O. & Söderholm, P. (2017).
      "Challenges in top-down and bottom-up soft-linking: lessons from
      linking a Swedish energy system model with a CGE model." *Energy*
      141: 803-817. https://doi.org/10.1016/j.energy.2017.09.107.
      Soft links (models run apart, exchanging named variables) need each
      connection point declared with its direction, unit and timing. The
      hard part is keeping the same concept defined the same way on both
      sides: in our terms, a sheaf edge's map.
    - Helgesen, P. I. & Tomasgard, A. (2018). *Energy* 159: 1218-1233.
      Iterating hard-linked models converges to the integrated solution.
      So within a year, one pass in causal order is an approximation whose
      error can be measured by iterating.
  - Validation in IAMs is mostly hindcasts of observed paths and
    model intercomparison: the same scenario run across models, the spread
    read as structural uncertainty. Rarely out of sample.
- **EUROMOD.** Sutherland, H. & Figari, F. (2013). "EUROMOD: the European
  Union tax-benefit microsimulation model." *International Journal of
  Microsimulation* 6(1): 4-26. https://doi.org/10.34196/ijm.00075.
  - A static microsimulation of taxes and benefits on survey households.
  - Coupled to macro models one way: macro paths re-weight and uprate the
    households, and the micro model returns distribution.
  - It is the pattern for a distribution block that turns aggregate wage,
    profit and transfer flows into the measures politics reads (the bottom
    half's income, the top share). We have no micro data; WID's
    distributional national accounts are its aggregate stand-in.
- **Agent-based macro, as contrast.**
  - Dosi, G., Fagiolo, G. & Roventini, A. (2010). "Schumpeter meeting
    Keynes." *JEDC* 34(9): 1748-1767.
    https://doi.org/10.1016/j.jedc.2010.06.018. The K+S model.
  - Cincotti, S., Raberto, M. & Teglio, A. (2010). "Credit money and
    macroeconomic instability in the agent-based model and simulator
    Eurace." *Economics e-journal* 4: 2010-26.
    https://doi.org/10.5018/economics-ejournal.ja.2010-26.

  Both are SFC at the aggregate by construction (every agent's flows have a
  counterpart). They are validated by reproducing stylised facts:
  - Fagiolo, G., Guerini, M., Lamperti, F., Moneta, A. & Roventini, A.
    (2019). "Validation of agent-based models in economics and finance."
    In Beisbart & Saam (eds.), *Computer Simulation Validation*, Springer:
    763-787. https://doi.org/10.1007/978-3-319-70766-2_31.
  - Windrum, P., Fagiolo, G. & Moneta, A. (2007). "Empirical validation of
    agent-based models." *JASSS* 10(2): 8.
    https://www.jasss.org/10/2/8.html.

  We do not take the agent route. Principle 2 needs parameters learned per
  country from what was known, and ABMs have hundreds of parameters
  calibrated by moments. Their validation taxonomy is useful:
  - stylised facts, which our hindcasts are;
  - calibration and estimation;
  - parameter-space exploration.
- **Coupled data assimilation.** Earth-system models assimilate across
  ocean, atmosphere and land (Penny, S. G. et al. 2017, "Coupled data
  assimilation for integrated Earth system analysis and prediction: goals,
  challenges and recommendations," report for the WMO,
  https://ntrs.nasa.gov/citations/20170007430; summarised in Penny, S. G. &
  Hamill, T. M. 2017, *BAMS* 98(7): ES169-ES172,
  https://doi.org/10.1175/BAMS-D-17-0036.1).
  - The distinction they draw is "weakly coupled" (each component
    assimilates its own observations, coupling through the forecast)
    against "strongly coupled" (an observation in one component updates
    the others through cross-covariances).
  - Strong coupling is better in principle but fragile: spurious
    cross-covariances from small ensembles do harm unless they are
    localised.
  - That is the choice the engine faces (section 6).

What the practice says, in short:
- declare every interface as named variables with units and timing;
- step modules in a fixed order with a stated time step;
- make the accounting identities hard constraints;
- validate couplings by hindcasting paths, by running with a link cut,
  and by intercomparison.

Only the second and third are routinely done, and almost no one tests a
coupling out of sample against a placebo. Principle 5 asks for that.

## 3. Exploratory numbers

Scratchpad coupled/, run on what is on disk.

**Sectoral balances close (explore4.py).** OECD Table 14, net lending B9
in current prices:
- 1,331 country-years have all four domestic sectors, in 42 areas, 40 of
  them countries (with EA20 and EU). The years run 1950-2025, mostly from
  1995.
- The sectors sum to the economy's total exactly.
- With the rest of the world, the sum is zero to within 0.28% of value
  added in 90% of 1,251 country-years (median 4e-8%).

The flow side of an SFC core is on disk and closes. Net lending as % of
gross value added:

| | NFC | financial | government | households |
|---|---|---|---|---|
| USA 2007 | -1.3 | +1.1 | -4.0 | -1.1 |
| USA 2009 | +1.3 | +3.7 | -13.1 | +4.1 |
| ESP 2007 | -6.7 | +1.9 | +1.9 | -6.0 |
| ESP 2009 | +5.1 | +1.8 | -11.2 | +0.7 |
| CHN 2000 | -1.4 | +0.2 | -8.8 | +12.0 |
| CHN 2019 | -11.5 | +2.0 | -3.7 | +13.8 |
| CHN 2022 | -11.7 | +2.6 | -4.6 | +16.1 |
| DEU 2007 | +0.0 | +1.0 | +0.2 | +5.4 |
| KOR 2022 | -6.6 | +0.9 | -0.0 | +6.7 |

- 2008 is visible as the private sectors swinging into surplus and the
  government into deficit, as the Levy model's identity says it must.
- China's household surplus of 12-16% of value added, against a corporate
  deficit of 11-12%, is Pettis's transfer in the accounts.
- Public corporations are split out (S11001, S12001) for eight countries
  only (Australia, the Czech Republic, Costa Rica, Britain, Japan, Mexico,
  New Zealand, Slovenia): a control measure for a few.

**Credit allocation (explore3.py).** Müller-Verner, shares of total
private credit:

| | 1960 | 1970 | 1980 | 1990 | 2000 | 2007 | 2014 |
|---|---|---|---|---|---|---|---|
| JPN households | 0.02 | 0.05 | 0.12 | 0.16 | 0.20 | 0.27 | 0.29 |
| JPN manufacturing | 0.53 | 0.47 | 0.33 | 0.17 | 0.15 | 0.13 | 0.13 |
| KOR households | 0.03 | 0.08 | 0.10 | 0.20 | 0.35 | 0.45 | 0.42 |
| KOR manufacturing | 0.30 | 0.46 | 0.54 | 0.42 | 0.28 | 0.19 | 0.23 |
| GBR manufacturing | 0.17 | 0.21 | 0.12 | 0.06 | 0.04 | 0.02 | 0.01 |
| DEU manufacturing | 0.18 | 0.17 | 0.11 | 0.11 | 0.05 | 0.04 | 0.04 |
| IND manufacturing | | | 0.47 | 0.45 | 0.40 | 0.27 | |
| ESP construction + real estate | | | | | 0.14 | 0.26 | 0.14 |
| USA households | 0.59 | 0.55 | 0.60 | 0.67 | 0.60 | 0.65 | 0.59 |

- Korea's state-directed decades show directed credit at its peak in
  1980. After the financial liberalisation of the 1990s and the 1997
  crisis, credit moved to households.
- Japan moved the same way a decade earlier.
- 98 countries have total, household and manufacturing credit (31 in the
  1960s, 95 by 2000): enough for a within-country test of control on
  allocation.
- The holes are the two most important cases. China has households only
  from 2000 (0.05 to 0.19 by 2014) and a tradable share that is mostly
  agriculture. The US has households against business only.

**Public capital (explore2.py).** IMF ICSD, general government's share of
the capital stock and of investment, current prices:

| | 1970 | 1990 | 2000 | 2010 | 2019 |
|---|---|---|---|---|---|
| CHN capital / investment | 0.87 / 0.84 | 0.87 / 0.84 | 0.84 / 0.75 | 0.63 / 0.45 | 0.47 / 0.36 |
| USA | 0.44 / 0.30 | 0.33 / 0.27 | 0.30 / 0.19 | 0.28 / 0.24 | 0.26 / 0.17 |
| GBR | 0.32 / 0.27 | 0.23 / 0.14 | 0.21 / 0.10 | 0.20 / 0.20 | 0.21 / 0.15 |
| JPN | 0.42 / 0.32 | 0.39 / 0.25 | 0.38 / 0.30 | 0.36 / 0.24 | 0.34 / 0.21 |
| KOR | 0.30 / 0.29 | 0.24 / 0.16 | 0.21 / 0.19 | 0.22 / 0.18 | 0.21 / 0.17 |

Two caveats make ICSD a weak control measure on its own:
- **It holds a split constant where it has no data.** China's 0.87/0.84
  and Vietnam's 0.23/0.19 are flat for decades.
- **Its "private" includes public corporations, and so SOEs.** China's
  falling public share after 2000 is general government's falling share,
  not the state's: SOEs and LGFVs sit on the private side.

Britain's privatisation shows (0.32 to 0.21). China's state control does
not. This is why the control layer needs:
- BRSS (state banks);
- V-Dem's v2clstown (state ownership of the economy, expert-coded,
  annual, long);
- WID's net public wealth (already in econ/public-capital).

These are needed beside ICSD, and a latent factor rather than any one of
them.

**Imputed series (explore5.py).** PWT 11.0's labour share for China is 0.59
in every year from 1970 to 2000, an imputation: Keen's w for China before
2000 reads a constant. The KAOPEN file covers 185 countries, 1970-2023.

## 4. The system map

### 4a. The subsystems: stocks, flows, outputs

Each subsystem is a module group per country-year (the place tree pools
parameters; principle 1), plus world nodes where the quantity is global.

**C, control: who decides what money and production are for.**
- Stocks:
  - c-bank: the state's share of banking assets;
  - c-own: state ownership of the economy (V-Dem v2clstown);
  - c-pubk: the public share of capital (ICSD) and net public wealth over
    national income (WID);
  - c-open: capital-account openness (KAOPEN; controls by asset class);
  - c-repr: financial repression (real deposit rate less real growth);
  - c-conc: private wealth concentration (WID top 1% and 10% wealth
    shares);
  - c-capt: state capture (V-Dem corruption, executive constraints).
- Flows: nationalisation and expropriation into c-own and c-bank;
  privatisation out of them; liberalisation and control tightening on
  c-open; capture and its reversal.
- Outputs: the control vector. Its main parts:
  - state direction: a latent factor on c-bank, c-own, c-pubk and c-repr;
    the econ/investment-regime factor generalised;
  - private financial power: c-conc, the financial sector's share of
    profits, market size;
  - openness.

**F, finance: the virtual layer that allocates.**
- Stocks (the SFC balance sheet):
  - credit to households (mortgage, consumer);
  - credit to NFCs by sector group (tradable, non-tradable,
    real-estate-related);
  - credit to government;
  - deposits and money;
  - equity and house values;
  - external assets and liabilities;
  - banks' capital and bad loans.
- Flows:
  - new lending by sector, which is allocation (shares of new credit);
  - repayment and write-offs;
  - interest paid and received;
  - dividends and buybacks;
  - asset revaluation;
  - cross-border flows.
- Outputs:
  - investment finance by sector group;
  - the interest burden by sector;
  - the credit gap and debt-service ratios;
  - asset-price gaps (econ/divergence);
  - crisis states (Keen's distance to the bad basin; the banking crisis
    probability from the lattice).

**R, real: what is produced, where, from what.**
- Stocks:
  - productive capacity by sector group and by product stage (bnm.stages:
    mines, smelters, refineries, fabs, plants);
  - capital by industry;
  - inventories of grain, oil and gas (bnm.food, the supply stocks);
  - endowments (reserves, land);
  - technology adoption (bnm.technology);
  - employment by sector.
- Flows:
  - investment completing into capacity (a delay; bnm.cycles' DELAY3);
  - depreciation and retirement;
  - production;
  - trade between places (BACI, the product graph);
  - depletion and discovery;
  - diffusion of technologies.
- Outputs:
  - output and employment by sector;
  - capacity shares of the world by stage;
  - the material base (econ/manufacturing-va-share, migration/ci-share);
  - shortages and outage paths (bnm.supply);
  - prices relative to equilibrium (bnm.cycles);
  - food and energy import costs;
  - productivity inflows.

**D, distribution: the bridge from the real economy to people.**
Formally part of the SFC core, kept apart because politics reads it.
- Stocks: none of its own beyond the SFC's. It turns flows into shares.
- Outputs:
  - the wage share and employment rate (Keen's w and l);
  - household disposable income and its growth (tension's perceived
    progress);
  - the top 10% and bottom 50% shares (WID);
  - the government balance and debt (the SFC government sector);
  - the real cost of food and fuel to households.

**P, politics: how the material conditions turn into pressure and events.**
- Stocks:
  - tension: perceived and expected progress, the progress gap, trust;
  - structural-demographic stress: MMP, EMP, SFD, Psi;
  - regime: V-Dem electoral democracy, liberal component, executive
    constraints, regime type;
  - elite composition.
- Flows: the stocks' smoothing and accumulation; regime transitions;
  events (onsets of coups, protest surges, conflicts, wars, crises from the
  event lattice) as discrete flows.
- Outputs:
  - hazards of the political kinds (the lattice's year node);
  - the regime state;
  - the probability of a regime change, coup or revolution in the coming
    year;
  - the fiscal stance (austerity or expansion) as a political decision
    that feeds back to F.

**W, the world (exogenous at the country, endogenous at the world node).**
- World prices (oil, grains, metals: bnm.cycles).
- World storage pressure (bnm.food).
- The world credit cycle and US policy rate, and global risk (the VIX).
- Chokepoint states.

The world node's own C, F, R and P are the sums of the countries' where
the quantity sums (output, credit, capacity) and their own models where it
does not (prices).

### 4b. The diagram

```
                 regime, events, capture (P, lagged a year)
        +-----------------------------------------------------+
        |                                                     |
        v                                                     |
  +-----------+  control vector   +-----------+  investment   +-----------+
  |     C     | ----------------> |     F     | -- finance -> |     R     |
  |  control  |  (state direction,|  finance  |  by sector    |   real    |
  |           |   private power,  |  (SFC     |               | (capacity,|
  |           |   openness)       |  balance  | <- profits,-- |  output,  |
  +-----------+                   |  sheets)  |  capacity use |  supply)  |
        ^   ^                     +-----------+   (lagged)    +-----------+
        |   |                       |     ^                         |
        |   |  crisis states        |     |  wages, employment,     |
        |   +-- (F -> C, lagged) ---+     |  government balance     |
        |                                 |                         |
        |                           +-----------+  output, prices,  |
        |                           |     D     | <-- employment ---+
        |                           | distrib-  |    by sector
        |                           |  ution    |
        |                           +-----------+
        |                                 |
        |                                 | wage share, income growth,
        |                                 | top shares, fiscal distress,
        |                                 | food and fuel cost
        |                                 v
        |                           +-----------+   hazards    +-----------+
        +------ regime change, ---- |     P     | -----------> |  event    |
          nationalisation,          | politics  | <- onsets -- |  lattice  |
          privatisation, controls   | (tension, |   (known     |  (bnm.    |
                                    |  regime)  |    lag)      |  lattice) |
                                    +-----------+              +-----------+

  W (world prices, storage, global credit) -> F, R, D, P; countries sum into W.
  Solid arrows within the year run left to right and top to bottom:
  C -> F -> R -> D -> P. Every arrow back up is read from the year before.
```

### 4c. The channels

Every edge is typed: from-module output, to-module input, the variable, its
unit, its known-year rule (when a value for data year y can first be read),
the source that measures it, and whether that source is on disk. "Model"
in the source column means the edge is a model's output, measured where a
source is given. "Lag 0" edges are read the same year (within the causal
order), "lag 1" from the year before.

| id | from -> to | variable | unit | known | measured by | on disk |
|---|---|---|---|---|---|---|
| C1 | C -> F | state direction | latent, SD units | latest part +1 to +2 | BRSS, V-Dem v2clstown, ICSD, WID public wealth, repression gap | ICSD, WID, WDI rates yes; BRSS, v2clstown no |
| C2 | C -> F | private financial power | latent | +2 | WID top wealth shares, finance profit share (OECD T14 S12 B2G), market cap (GFDD) | yes |
| C3 | C -> F | capital-account openness | index 0-1 | y+1 | KAOPEN; Fernández et al. | KAOPEN yes |
| C4 | C -> R | public share of investment | share of GFCF | y+2 | ICSD; OECD T14 S13 and S11001 P51G | yes (8 countries for public corps) |
| F1 | F -> R | new credit by sector group | % GDP per year | y+1 (BIS), y+2 (national) | Müller-Verner to 2014; BIS HH/NFC; ECB, BoJ, BoK, PBoC by industry | M-V and BIS yes; industry after 2014 no |
| F2 | F -> R | cost of credit to a sector | % real | y+1 | WDI lending rate, JST rates, BIS DSR | yes |
| F3 | F -> D | interest paid by sector | % GDP | y+2 | OECD T14 D41 by sector; Z.1 | yes |
| F4 | F -> D, P | asset prices against fundamentals | log gap | y+1 | econ/divergence (JST, WID, WDI) | yes |
| F5 | F -> P | financial crisis state | probability; onset | onset +1 | lattice banking node; LV, JST, RR catalogues | yes |
| F6 | F -> C | crisis, bailout, nationalised banks | event | onset +1 | Laeven-Valencia 2018 (nationalisations column) | dates yes; xlsx detail no (PDF/txt on disk) |
| R1 | R -> F | profits by sector | % VA | y+2 | OECD T14 S11 B2G, D42; EU KLEMS | yes |
| R2 | R -> F | capacity utilisation, output gap | share | y+1 | WDI/UNIDO industrial production; bnm.cycles coverage | partly |
| R3 | R -> D | output and employment by sector | VA, hours | y+2 | EU KLEMS labour accounts, I-O spine, WDI | yes |
| R4 | R -> D, P | food import cost | % of supply, price x dependence | y+2 | bnm.food (FAOSTAT) | yes |
| R5 | R -> D, P | energy shock pass-through | % of output | y+3 (I-O) | bnm.energy-pilot (I-O spine, prices) | yes |
| R6 | R -> P | material base and its migration | world share, ci-share | y+2 | econ-structure, migration, stages | yes |
| R7 | R -> C | resource rents | % GDP | y+1 | WDI NY.GDP.TOTL.RT.ZS, petroleum rents | yes |
| R8 | R -> F | supply outage losses | VA lost | as shock known | bnm.supply outage-path; essentiality | yes |
| R9 | R -> W, R | material demand by technology | log growth over 5 years | y+2 | bnm.tech-demand (BACI, capacity, OWID) | yes |
| R10 | R -> D, F | technology adoption, frontier gap, productivity inflow | share, years | y+1 | bnm.technology (WDI, CHAT) | yes |
| D1 | D -> P | wage share, employment | share | y+2 | PWT labsh, OECD T14 D1/B1G, Keen w and l | yes |
| D2 | D -> P | real income per head, its trend | log growth | y+1 | WDI, Maddison | yes |
| D3 | D -> P | top 10%, bottom 50% income shares | share | y+2 | WID | yes |
| D4 | D -> P, F | government balance and debt | % GDP | y+1 | OECD T14 S13 B9; IMF GDD, GMD | yes |
| D5 | D -> F | household saving rate | % disposable income | y+2 | OECD T14 S1M B8G/B6G | yes |
| P1 | P -> C | regime type and constraints | V-Dem indices | y+1 | V-Dem Core | yes |
| P2 | P -> C | regime change, coup, revolution | event | onset +1 | Archigos, Powell-Thyne coups, V-Dem episodes, lattice | yes |
| P3 | P -> C | nationalisation, expropriation | event, sector | +1 | Kobrin, Minor, Hajzler; LV for banks | banks only |
| P4 | P -> C | privatisation | event, value | +1 | Privatization Barometer; World Bank PPI | no |
| P5 | P -> F | fiscal stance (austerity) | change in structural balance | y+1 | IMF WEO, GMD; fiscal consolidation catalogue | yes |
| P6 | P -> F, R | conflict and sanctions | event, intensity | +1 | UCDP, lattice; GSDB | UCDP yes; GSDB no |
| W1 | W -> R, D | world commodity prices | real, log gap | month | bnm.cycles, FRED/IMF, Pink Sheet | yes |
| W2 | W -> D, P | world storage pressure | index | y+2 | bnm.food | yes |
| W3 | W -> F | global credit, US rate, VIX | rate, index | month | FRED, BIS | yes |
| W4 | W -> R | trade through each chokepoint | share of trade value | y+2 | bnm.chokepoints (BACI routed, PortWatch) | yes |
| X1 | F <-> F abroad | bilateral claims against surplus | % GDP | +2 | econ-coupling (PIP, DIP, BACI) | yes |

The coupling graph has more edges than the table: each module's internal
edges (Keen's three stocks, tension's smoothing). The table lists the
cross-module ones. The engine builds the graph from the modules'
declarations (section 6), so the table is a check on it, not its source.

### 4d. The control layer as built (bnm-4hv.2)

**Specification rule for control/state** (registered 2026-10-23, after
the maximum-likelihood fit was found to run to the uniqueness bound and
before any event check, income regression or country placement of the
alternatives was computed):

1. The one-axis ML fit, run to convergence (tolerance 1e-7 on the
   loadings), is improper if any indicator's uniqueness ends at 0.2 or
   less. An improper fit is not adopted: the axis would be that
   indicator, not a latent over the stocks.
2. The stock is then the one-axis fit with every uniqueness bounded below
   at 0.2 (no single indicator's reliability above 0.8), the usual remedy
   for a Heywood case; the loadings stay learned (principle 2).
3. The equal-weight composite (the mean of the signed standardised
   indicators a country-year holds, at least two) and the bounded fit
   without state-own are sensitivity checks, reported beside it, not
   candidates.
4. The event checks and the income regression do not change 1-3. If the
   stock's rank correlation with state-own alone is 0.95 or more, this
   section says the axis is state-own.

The same bound applies to the two-axis fit the second-axis rule tests.

**What was built.** scripts/control.py assembles the indicators with
known years; bnm.control fits a factor with sign priors by EM over an
expanding window (the fit for year Y on rows known by Y, standardised on
them, warm-started from Y-1's fit and from the prior's start, the better
log posterior kept); bnm.control-run writes data/derived/stocks/control.csv
(control/state, -sd, control/n and the indicators as control/<name>) and
data/derived/control/ (loadings, the variants, the fixed-fit diagnostic).
Control is an engine module (group C, emits C1 and C3; all known at +1 or
later, ka-open and the ICSD, WID, PMR and BRSS stocks at +2). Run on the
indicators below it reproduces its table exactly through the engine.

Indicators of the state axis (sign): state bank share (+; Panizza SOE3,
BRSS where Panizza has nothing, Germany from the Bundesbank's banking
groups), V-Dem state ownership (+), public capital (+; the mean z of ICSD's
public share of capital and WID's net public wealth), expropriation acts
in ten years (+), OECD PMR public ownership (+), the financial repression
gap (+) and KAOPEN (-).

- Germany. Panizza codes the Landesbanken and savings banks only in part
  (its share falls from 0.20 in 1999 to 0.015 by 2012; mean 0.08 over
  1995-2020), while BRSS reports 0.37-0.40. Germany's series is now the
  Bundesbank's Landesbanken plus savings banks over all banks (balance
  sheet totals, December; data/raw/econ/bbk-banking-groups): 0.38 in
  1995, 0.35 in 2001, 0.29 in 2013, 0.23 in 2025. It starts in 1995 with
  Panizza: earlier it would be the indicator's only country.
- Japan is unchanged. Japan Post Bank (the postal savings system until
  2007) is in neither source, and the open series that has it (the Bank of
  Japan's flow of funds, sector "postal savings") ends at 2007Q1, after
  which Japan Post Bank is inside "banks"; a series across the
  privatisation would need its annual reports, and whether it counts as
  state-owned after the 2015 listing depends on reading ownership through
  Japan Post Holdings.
- The repression gap (c-repr): real GDP growth less the real deposit rate
  (Fisher), the five-year mean to y (three years at least, y among them),
  WDI FR.INR.DPST, FP.CPI.TOTL.ZG and NY.GDP.MKTP.KD.ZG, +1. 151
  countries, 4,265 country-years; none for the United States, India or
  the euro area after 1998 (WDI has no deposit rate for them). China runs
  at 6-15 points, Japan near 0 since the 1990s.
- Known years corrected: expropriation acts +1 (was 0; the channel table's
  P3), KAOPEN and FKRSU +2 (the 2023 KAOPEN and the 2021 FKRSU update came
  out two years after their last year), BRSS's 2019 wave 2021 (it is read
  from the April 2021 corrected file).
- Expropriation data end in 2014 and are carried at most five data years
  (to the stock of 2020), as every indicator is. With the bound its
  loading is 0.20 and its uniqueness 0.96, so its weight is small and its
  dropping out moves scores little; the counts are developing countries
  only (zero elsewhere by assumption).

**The near-Heywood fit.** The one-axis ML fit is a Heywood case: run to
convergence (31,049 EM steps) state-own's uniqueness goes to 0.015 with a
loading of 0.99. The 0.03 first reported was EM stopping on its way down
(tolerance 1e-5). Under that fit a country's score is its V-Dem state
ownership: the posterior weight on state-own (loading over uniqueness) is
about 140 times the next indicator's. By the registered rule the stock is the bounded fit. Its
loadings at 2027: state-own 0.89 (uniqueness at the bound), ka-open -0.46,
state bank share 0.38, public capital 0.36, repression 0.31, PMR 0.27,
expropriation 0.20; state-own's weight is now about 9 times the next.
The bound costs 90 log-posterior units over 14,907 country-years.

Variants (data/derived/control/control-check.txt; Spearman pooled over
country-years 1995-2025):

| | stock (bounded) | ML | equal weight | without state-own | state-own alone |
|---|---|---|---|---|---|
| rank r with the stock | 1 | 0.98 | 0.83 | 0.56 | 0.97 |
| rank r with state-own alone | 0.97 | 0.99 | 0.72 | 0.43 | 1 |
| 5-year changes, rank r with the stock's | 1 | 0.92 | 0.73 | 0.42 | 0.81 |
| events with the right sign | 10/10 | 10/10 | 8/10 | 6/10 | 11/11 |
| R² on log income (1995-2022) | 0.03-0.05 | 0.01-0.03 | 0.09-0.13 | 0.06-0.16 | 0.02-0.04 |
| rank of DEU / JPN / KOR in 2015 (of 201) | 197 / 201 / 127 | 197 / 201 / 134 | 193 / 196 / 108 | 151 / 188 / 111 | 172 / 176 / 109 (of 176) |

The stock's rank correlation with state-own alone is 0.97, over the
rule's 0.95: **control/state is V-Dem's state ownership with small
corrections**. The other indicators share little with it or with each
other. Without state-own the axis is another thing (rank r 0.56 with the
stock) that gets 6 of 10 events right. The event checks and the low income
R² are V-Dem's coders' judgements; they do not show that the bank,
capital, openness and repression stocks measure one state direction.
Germany and Japan stay with the United States under every variant that
holds state-own. The Bundesbank series moves Germany only where state-own
weighs little: under equal weights it is 155th of 196 in 2025 against
186th in the stock, and without state-own 117th.

Second axis: still not stable (capture's congruence over time 0.54 at
1995 against 2027, split-half 0.61; threshold 0.90). The stocks carry the
one-axis fit; at 2010 and 2025 its scores correlate 0.997 with the
two-axis fit's state axis.

Missing indicators drop out of a row's likelihood (missing at random) and
the posterior mean shrinks toward 0 as fewer are held: a row with V-Dem
has a mean posterior SD of 0.43 and a mean |score| of 0.73, a row without
it 0.79 and 0.42 (2,162 of 10,918 rows 1995-2025, the 30 countries V-Dem
does not code among them). Scores with different control/n are not
comparable as levels; a reader should weight by control/state-sd, and a
change in a country's control/n can move its score when nothing changed.

Not done: a second measured state source independent of V-Dem
(La Porta's government ownership of banks, the OECD SOE dataset) is what
the axis would need to be a latent rather than one series.

## 5. The loop: control -> allocation -> production -> distribution -> politics -> control

Each link: its mechanism, the literature, how it is measured, the sign
prior (principle 4: a relation's sign stated before the design years
check it), and what would show it is wrong.

**5.1 Control -> allocation (C1-C3 -> F1).**
- *Mechanism.* Whoever controls lending chooses its shares across sectors.
  - State-directed systems lend to capacity in tradables and
    infrastructure at below-market rates and hold the deposit rate down to
    pay for it (Amsden, Wade, Studwell, Pettis).
  - Private systems lend where the expected risk-adjusted return to the
    lender is highest. With mortgage collateral, deposit insurance and
    house-price expectations, that is real estate and households (JST
    2016, Mian-Sufi).
  - Openness lets domestic savings leave and foreign credit enter, which
    in practice funds non-tradables (Müller-Verner).
- *Measurement.* The share of new credit to each sector group, from
  Müller-Verner's stocks (first differences, revaluation-free since loans
  are book values; write-offs are the noise). Five groups:
  - households, mortgage and consumer (M-V HH);
  - tradables (A, B, C);
  - non-tradable business (F, G, I);
  - real estate (L);
  - other.
- *Model.* A Dirichlet (logistic-normal) DLM per country for the share
  vector a_t: log(a_k/a_ref) = mu_k,country + theta_k . control_{t-1} +
  gamma_k . returns_{t-1} + noise. The returns are house-price growth (JST,
  BIS SPP) and sector profit rates. Coefficients are pooled up the place
  tree and learned online.
- *Sign priors.*
  - State direction raises tradables against households and real estate.
  - Private financial power raises real estate's sensitivity to house
    prices (gamma_RE larger where c-conc is high).
  - Openness raises non-tradables.
- *Falsified if* the within-country effect of state direction on the
  tradable share is not distinguishable from its placebo. The placebo is
  another country's control vector, the principle 5 rule.

**5.2 Allocation -> production (F1, F2 -> R).**
- *Mechanism.* Investment in a sector is financed by retained profits plus
  new credit (plus equity and the state's budget). Investment completes
  into capacity after a construction delay (bnm.cycles' DELAY3; Sterman's
  supply line), and capacity bounds output (bnm.supply recipes bind
  critical inputs). Credit that goes to real estate raises house prices
  and construction, not tradable capacity. Credit that goes to households
  raises consumption and imports.
- *Literature.* World3's FIOAI is the same allocation with a fixed rule.
  Fazzari, Hubbard & Petersen (1988) show investment sensitive to internal
  finance. Müller-Verner find tradable credit goes with productivity
  growth.
- *Measurement.*
  - Investment by industry: EU KLEMS capital accounts (I_GFCF by NACE,
    1995-2021, EU, US, UK, Japan); OECD Table 6/STAN; EXIOBASE final
    demand GFCF by product (1995-2022).
  - Capacity by stage: BGS/MYB production, plant capacities (stages,
    GEM, GPPD, worldsteel).
  - World output shares: econ-structure, migration.
- *Model.* K_s(t+1) = K_s(t)(1 - delta_s) + completions_s(t), where
  completions are the DELAY3 of starts and starts_s = phi_s (credit flow
  to s + retained profits of s). phi_s is the share of financing that
  becomes fixed capital, learned. Output Y_s = min(u K_s, critical-input
  limit), with u from the cycle model. The product graph's capacity per
  [region product] is scaled by the region's sector capital.
- *Sign prior.* Capacity growth in a sector rises with its credit share,
  at a lag of 1-4 years.
- *Falsified if* the tradable credit share does not lead tradable capacity
  growth within countries beyond what the sector's own past growth and
  world demand give.

**5.2a Capacity from investment, as built (bnm-4hv.11, October 2026).**
The allocation test (9d) found control/state equivalent to nothing for
where credit goes, so the control -> allocation link is dropped. The
engine and the accounting stay, and capacity is driven by the credit and
investment observed, not by control.
- *Investment by industry* (bnm.capacity/financed-investment, in the
  investment module of bnm.modules.allocation). An industry's financed
  investment is its retained profits plus its external finance:
  - retained: the SFC core's NFC gross saving (S11 B8G, from its panel,
    data/derived/sfc/panel.csv), split by each industry's share of gross
    operating surplus (EU KLEMS VA_CP - COMP);
  - external: NFC net borrowing (-B9), split by Müller-Verner's credit
    shares (a section's share of NFC credit, split within the section by
    surplus; sections without a measured share take the rest by surplus;
    real estate's share leaves, since L's surplus is mostly imputed rent).
    Without credit shares (after 2014, Müller-Verner's end) it follows
    surplus too.
  - The NFC industries are EU KLEMS's less C (the aggregate), K, L and
    O-Q. Observed GFCF by industry (KLEMS I_GFCF) is the other measure;
    over 1995-2021 the two rates have the same median (0.10 a year for
    basic metals), and B8G - B9 is 0.89-1.07 of NFC capital formation by
    country.
- *Time to build* (bnm.capacity/build-time). A prior over the whole years
  from the first spending to first output, per industry, with its range
  and sources:

  | industry | p(T years) | mean | sources |
  |---|---|---|---|
  | default | 1: .3, 2: .5, 3: .2 | 1.9 | Koeva (2000, IMF WP/00/138): new plants about two years in most industries, unaffected by the cycle; Kydland & Prescott (1982): four quarters |
  | B mining | 2: .2, 3: .35, 4: .25, 5: .2 | 3.45 | IEA (2021) lead times 2010-2019: 16.5 years from discovery, of which about 1.8 construction planning and 2.6 construction (the split as reproduced by Statista); S&P Global (2024): 17.9 years for mines started 2020-23 |
  | C19 refining | 2: .15, 3: .35, 4: .3, 5: .1, 6: .1 | 3.65 | greenfield: Irving and BP's Eider Rock (New Brunswick), construction 2011 to start-up 2015 planned, re-planned at 8 years in 2008, then shelved (one project, never built); expansions nearer two |
  | C20 chemicals | 1: .2, 2: .5, 3: .3 | 2.1 | Koeva (2000) |
  | C24-C25 metals | 1: .2, 2: .45, 3: .25, 4: .1 | 2.25 | Koeva (2000); electric arc furnaces 14-36 months (ArcelorMittal Calvert 24, US Steel Fairfield about 24, Hebei Yongyang 14-15, Tata Port Talbot 36 and more); Alcoa Fjardaal smelter 2004-2007 |
  | D-E power | 2: .1, 3: .25, 4: .3, 5: .15, 6: .1, 7: .1 | 4.2 | EIA AEO2022 EMM Table 3: 2 (PV, turbines), 3 (combined cycle, onshore wind), 4 (coal, hydro, offshore wind), 6 (nuclear); observed (GEM, Little's law, scripts/time-to-build.py): all 4.4, coal 4.2, gas 4.4, wind 3.6, solar 3.1, nuclear 12.9, hydro 13.7, which read long because the pipelines grew; adjusted for growth, all 2.6, coal 2.5, gas 3.1, wind 3.1, solar 1.2 |

  Spending is spread evenly over the build, so the share of a year's
  spending that completes L years later is w_L = sum_T p(T) [L < T] / T.
  The priors are priors: learning them from the capacity data (principle
  2) waits on the check below.
- *Completions.* The completion rate of an industry in year y is the
  investment already made that completes in y, over the capital stock:
  sum over L >= 1 of w_L I(y-L) / K(y-L-1). Its own year's spending is not
  in it, so it is known at y + 1 (once y - 1's investment is, at +2).
- *Emits* (all rates from :step; no level, so no :observe; every row
  checked against its known year):
  - investment/klems/investment-rate-<industry> (+2),
    investment/klems/completion-rate-<industry> (+1);
  - investment/financed-<industry> (LCU m), investment/financed-rate-
    <industry> (+2), investment/financed-completion-rate-<industry> (+1);
  - capacity/completion-rate/<node> and capacity/financed-completion-
    rate/<node> (+1): the industry's completion rates carried to the
    product graph's products and the stage table's stages, as
    capacity/growth/* already is. The investment module is the only
    emitter of capacity/* (a test checks the modules' sources).
- *Reads* (sources at their lags): EU KLEMS capital accounts (I_GFCF,
  K_GFCF) and national accounts (VA_CP, COMP), +2; the SFC panel's S11
  B8G, B9 and P5, +2; Müller-Verner's credit shares, +2; the concordance
  and the stage table for the links.

**5.3 Production -> distribution (R3 -> D).**
- *Mechanism.* Output and employment set the wage bill and profits.
  - Keen's Phillips curve sets the wage share from employment.
  - Interest on debt moves income from borrowers to lenders.
  - Taxes and transfers move it through the government.
  - Profits split between reinvestment, payouts (financialisation) and
    taxes.
  - Sector composition matters. Manufacturing employs more per unit of
    capital than finance or real estate, and a shift of credit away from
    tradables lowers labour's share (Lazonick; the K5 reallocation in
    ECONOMIC-CHARACTER).
- *Measurement.*
  - OECD Table 14 by sector: D1, B2G, D4, D5, D6, B6G.
  - PWT labour share.
  - WID distributional national accounts (top 10, bottom 50).
  - Sector-account closure as a check.
- *Model.* The SFC core's flows, with Keen's behavioural equations for w,
  l and investment, and the household saving rate learned. The
  distribution of household income (top and bottom shares) is a reduced
  form on the factor shares and the government's transfers, learned from
  WID, since we have no micro data to do it EUROMOD's way.
- *Sign priors.* Higher interest burden lowers the bottom 50% share. A
  higher wage share raises it.

**5.4 Distribution -> politics (D1-D5, R4, R5 -> P).**
- *Mechanism.* Turchin's MMP (wages against output, urbanisation, youth),
  EMP (elite overproduction, top share) and SFD (fiscal distress). Davies'
  J-curve: a reversal after a long rise opens the progress gap. Food and
  fuel shocks hit the poor's budgets (Bellemare; Arezki-Brückner), through
  exposure and regime type (Hendrix-Haggard). Acemoglu-Robinson: inequality
  and crises make the masses' threat credible and the elites' repression
  costly.
- *Measurement.*
  - bnm.tension's stocks, now fed by typed inputs: perceived progress from
    D2; the wage from D1; the top share from D3; SFD from D4 (the SFC
    government sector, not WDI debt alone).
  - bnm.food's import exposure times storage pressure.
  - bnm.energy's pass-through.
  - V-Dem regime type as the moderator.
- *Sign priors.* All raise the political kinds' hazards. The food channel
  is stronger in low-income, import-dependent and hybrid regimes.
- *What it can show.* T4 says these states help where, for the political
  kinds, against placebos. That is the expectation, not timing.

**5.5 Politics -> control (P1-P4 -> C).**
- *Mechanism.*
  - Regime changes reallocate control. Revolutions and leftist turns
    nationalise; transitions from communism and IMF programmes privatise.
  - Executive constraints set how far a ruler can seize assets (Guriev,
    Kolotilin & Sonin).
  - Resource rents raise the prize: oil nationalisations come when prices
    are high.
  - Crises nationalise banks (Laeven-Valencia record nationalisations in
    many of 151 episodes) and tighten capital controls.
  - Elites with economic power capture the state (Hellman et al.), and
    capture raises private financial power and lowers state direction or
    turns it to rents.
  - Krippner's account: deregulation as the state's way out of
    distributional conflict.
- *Measurement.*
  - Events: Kobrin, Minor and Hajzler (expropriations, 1960-2006),
    Laeven-Valencia (bank nationalisations), Privatization Barometer and
    World Bank (privatisations), Fernández et al. and KAOPEN changes
    (controls).
  - Changes in c-own (V-Dem v2clstown, annual) and c-bank (BRSS waves, La
    Porta 1970/1995).
- *Model.* A hazard per control flow (nationalisation in sector s,
  privatisation, control tightening). Its inputs:
  - world prices for s (bnm.cycles);
  - executive constraints and regime type (V-Dem);
  - a regime change in the past two years (P2);
  - a crisis onset (F5);
  - IMF programme presence;
  - resource rents (R7).

  An event moves the stock by the sector's share of capital (expropriating
  oil in a petrostate moves c-own by oil's share of capital).
- *Sign priors.* High prices and weak constraints raise nationalisation.
  Crises raise bank nationalisation and controls. Democratic transitions
  and IMF programmes raise privatisation.
- *Power.* Kobrin's 563 acts and Hajzler's later ones are hundreds of
  events. The GKS hazard is estimable. Generic regime-to-ownership effects
  rest on few transitions and are descriptive.

**5.6 The loop closed, and its time scales.** One turn of the loop takes
years:
- control shifts take a year or more to move allocation;
- allocation takes 1-4 years to become capacity;
- capacity takes years to move the wage share and employment;
- these take years to move tension;
- tension takes years to change regimes;
- regimes take years to change control.

So the engine's annual step is fine. Every link has at least one year's
lag except within F -> R -> D, where the SFC accounts tie the same year's
lending, spending, income and saving together. That is why the engine's
contemporaneous edges run only along C -> F -> R -> D -> P (section 6).

## 6. Module interface and the coupled engine

### 6a. What a module declares

A module is data plus a step function, like bnm.stockflow's models (which
are data already). Its declaration:

```clojure
{:module   :keen
 :group    :distribution                  ; C F R D P W
 :place    :country                       ; or :world, :cell, :region
 :reads    [{:var :sfc/nfc-debt   :unit "ratio to GDP"  :lag 0}
            {:var :real/employment :unit "rate"         :lag 0}
            {:var :sfc/real-rate  :unit "per year"      :lag 1}]
 :emits    [{:var :keen/w         :unit "share of VA"   :known {:data-year 2}}
            {:var :keen/l         :unit "rate"          :known {:data-year 1}}
            {:var :keen/critical-debt :unit "ratio to GDP" :known {:data-year 2}}]
 :stocks   {:w {:unit "share" :info false} :l {...} :d {...}}
 :params   {:phi-slope {:prior [:lognormal 0 0.5] :pool :place-tree} ...}
 :observes [{:var :keen/w :source "PWT labsh; OECD T14 D1/B1G" :known {:data-year 2}}]
 :step     (fn [state inputs params t dt] ...)  ; returns the new state and the emits
 :dt       0.25}                           ; Euler substeps within the year
```

- **reads.** Each read is a variable another module emits (or a data
  source the engine supplies, :source instead of a module) with its unit
  and its lag in years. Lag 0 means this year's value, which is allowed
  only if the emitter comes earlier in the causal order.
- **emits.** Each emitted variable has a unit and a known-year rule: the
  year a value for data year y can first be read, given the data its
  assimilation uses. A module's emitted value at year t is computed from
  reads all known by t and its own state. The engine checks this, as
  bnm.sheaf's readers already refuse reads after the origin.
- **stocks.** The module's own state, with units. :info marks perceptions
  (principle 8: no material crosses between an info and a material stock).
- **params.** Priors and the pooling level (principle 2). Learned by
  bnm.assimilate/calibrate on what was known by each recalibration year.
- **observes.** Which of its stocks or emits are measured, by which
  source, with which publication lag: what assimilation updates it with.

The engine refuses a declaration whose units do not match its emitter's,
whose reads have no emitter or source, or whose lag-0 reads form a cycle.

### 6b. The causal order and the time step

The engine steps annually. Within year t, the modules run in topological
order of the lag-0 edges, which by design are:

    W -> C -> F -> R -> D -> P

Each reads lag-0 inputs from modules before it and lag-1 inputs from the
end of t - 1 (anything).

1. W: world prices, storage pressure and global credit for t, observed as
   known by the end of t (prices are monthly and known at once), or the
   world models' forecasts when run forward.
2. C: control at t. It reads last year's regime, events and crisis states
   (P, F lagged) and this year's world prices (for nationalisation
   hazards).
3. F: the SFC financial block at t. It reads C(t), last year's profits,
   capacity use and fiscal stance, and this year's world credit. It emits
   new credit by sector, interest flows, asset-price gaps and crisis
   states.
4. R: capacity, output, trade and stocks at t. It reads F(t)'s investment
   finance, W(t)'s prices and supply shocks.
5. D: distribution at t. It reads R(t) and F(t), closes the SFC accounts
   (one flow as the residual, the redundant one checked), and emits wage
   share, incomes, top shares and the government balance.
6. P: tension and regime stocks at t. They read D(t), R(t)'s food and
   energy costs and F(t)'s crisis states, and emit the political hazards
   and states. The event lattice reads these at the year-end origin (its
   year and half-decade nodes), and the onsets it observes are P2 and F5
   for t + 1.

Within the year there is one pass, no iteration. Helgesen-Tomasgard show
iteration converges to the joint solution. The engine has a check mode
that iterates F -> R -> D to a fixed point and reports the gap against one
pass. If the gap matters, the step becomes iterative for those three.

Some modules need finer steps. Keen's ODEs and the cycle model step at
dt = 0.25 within the year, as they do now. The quarter nodes of the event
lattice read the year's coupled state as an offset carried down
(docs/DOWNSCALE.md, Berry & West), not as their own inputs. The engine
does not run quarterly: the finance module's fast variables (credit gap,
asset prices) are already read quarterly by bnm.named for the banking
quarter nodes, and stay there.

**Known years.** At year t the engine's state for t uses only what was
known by the end of t. A data year's value enters when its source
publishes:
- BIS: y + 1;
- OECD sector accounts: y + 2;
- WID: y + 2;
- FAOSTAT: y + 2;
- I-O tables: y + 3;
- V-Dem: y + 1.

Between publication and the current year the module's own dynamics carry
the state forward, which is the point of having a model. Revisions:
current vintages are read as if published at those dates, the caveat every
stock file already carries.

### 6c. Ensemble, assimilation and parameters

- **Members.** The engine runs N members (64 to start). Each member draws
  its parameters from each module's posterior (bnm.assimilate/calibrate,
  ES-MDA, refitted every few years on what was known, as keen and cycles
  do now) and steps the whole coupled system. Uncertainty passes through
  the channels member by member: a member whose allocation is more
  tradable has more tradable capacity two years later. A module's output
  is a distribution, and the coupled state is reported as its median and
  spread.
- **Assimilation each year.** When data for a past year become known, the
  members' states are updated by the EnKF analysis
  (bnm.assimilate/assimilate on the augmented state).
  - **Weakly coupled first.** An observation of a module's variable
    updates that module's stocks and parameters only. The coupling acts
    through the next year's forecasts. This is robust with 64 members,
    which cannot estimate cross-module covariances reliably.
  - **Strong coupling only by registered test.** An observation may update
    the modules one edge away, localised by graph distance (Gaspari-Cohn
    taper on the coupling graph's hop count). It is adopted only if a
    registered comparison on the design years shows it forecasts the
    held-back observations better than weak coupling.
- **Parameter learning respects principle 2.** Coefficients of the
  channels (theta, gamma, phi) are learned per module with pooling up the
  place tree, from data known at each origin. Nothing is fitted to the
  hindcast periods it is scored on (section 9: hindcasts fit with the
  episode's era left out).

### 6d. Consistency: accounting, sheaf and contradictions

Three kinds of check, each a smoke test or a diagnostic written every year:

1. **Accounting closes (principle 8).** The SFC matrices' rows and columns
   sum to zero in every member every year, through bnm.stockflow/imbalance.
   These hold:
   - the sum of the sectors' net lending equals zero with the rest of the
     world;
   - each sector's change in net financial assets equals its net lending,
     less revaluation and other changes;
   - credit created in F equals the borrowing recorded by the borrowers in
     D.

   A failure is a bug (principle 10).
2. **Sheaf consistency across channels.** Every channel whose two ends
   both see the quantity is an edge with a map, and its coboundary
   (bnm.sheaf/coboundary) is the gap between what the emitter says and
   what the receiver's own measurement says:
   - F's credit to NFCs against R's NFC investment less retained earnings
     (T14 S11 P51G - B8G);
   - Keen's w against T14's D1 over B1G;
   - tension's SFD debt against the SFC government's debt;
   - bnm.supply's outage-path loss against observed industrial production
     after a known shock;
   - econ/divergence's wealth gap against F's asset revaluation;
   - the world node's output against the sum of the countries'.

   The coboundary's size is the edge's inconsistency, written per year.
3. **Contradictions (principle 4).** A persistent sheaf gap is information,
   not only error. For example, credit to real estate rising while the
   real economy's construction capacity does not is a gap between finance
   and production: Krein's K5 and the Müller-Verner predictor in one
   number. Such gaps accumulate as strain and are candidate inputs to the
   event lattice. They enter only through selection pressure (principle
   5: nested, against a placebo), like every other input.

### 6e. What the engine writes, and how it replaces the stock files

- **One table**, data/derived/coupled/state.csv:

      iso, year, module, variable, value, sd, data_year, known, source

  - year is the year known;
  - value and sd are the members' median and spread;
  - source says whether the value is an assimilated observation, a model
    carry-forward or a channel's output.
- **Compatibility.** bnm.states reads it as it reads any stock table, the
  variable names kept (keen/*, tension/*, food/*). The trials' state sets
  (states-t45.edn, T4's thirteen) keep working.
- **The graph.** data/derived/coupled/graph.edn: the declarations, the
  edges built from them, and per year each edge's inconsistency.
  bnm.network builds its layer view from it, with `couple` taking the
  edges, not hand weights.
- **Phase A** (first step of bnm-4hv.6) wraps today's modules without
  changing their behaviour. The engine must then reproduce today's stock
  files exactly: a golden test, as bnm.sheaf's introduction was pinned.
  Only after that do the channels change what modules read.
  - Done (October 2026): bnm.engine (the interface, the graph, the
    runner), bnm.modules.stocks (19 modules over the 18 stock tables;
    food split into its countries, R, and the world, W) and
    bnm.engine-run. The coupled table reproduces every stock table with
    0 mismatches; bnm.states reads it with read-states' :coupled (not
    the default yet). Each step runs levels before rates, as system
    dynamics orders its equations, so modules coupled within the instant
    need no iteration: World3-03 as six modules (bnm.modules.world3)
    matches bnm.world3's run exactly.
- **Phase B** (bnm-4hv.6, October 2026): bnm.coupled, bnm.sfc-analysis,
  bnm.modules.consistency and bnm.coupled-run. Every subsystem runs in
  one causal run: the 19 stock-table modules, allocation and investment,
  the SFC core per country (35 countries, ids sfc-finance-<iso>), World3-03
  and a consistency module, 134 modules from 1800 to 2100 at World3's half
  year. validate is empty and the order is W -> C -> F -> R -> D -> P.
  Control does not feed allocation (9d found nothing there).
  - *The runner.* bnm.engine now steps a run one step at a time, so an
    ensemble's members go in lockstep. Between steps an analysis may
    replace their states and parameters, never within a step. Modules
    shared by every member (the tables) are stepped once and given to
    the others. A :span limits the years a module steps. The table is
    the members' median, with their spread in sd.
  - *Assimilation.* Each year, the data that became known update the SFC
    members: the balance sheet by position, sector net lending, the wage
    share (all two years on, Tables 720 and 14) and Keen's employment
    (one year on). They are past years, so the state updated is a window
    from the oldest year observed to now. It holds that window's
    opening, each year's flow of funds, closing balance sheet, wage share
    and employment. The update is the ensemble smoother (augur.enkf),
    and the members' spread is inflated to match the innovations
    (at most 3). The identities in the window are linear:
    - the closing balance sheet equals the opening, revalued, plus the
      transactions;
    - each sector's transactions sum to its net lending;
    - each instrument's transactions sum to zero;
    - the opening is closed.

    The update is projected onto these identities (augur v0.10-0.12:
    project, update-constrained). After every update, in every member,
    the balance sheet, flow of funds and TFM close to below 1e-13 of
    output (tested at 1e-10). Levels are observed against each member's
    own output, so the update stays linear. As 6c asks, the parameters
    are relearned at each analysis on what is known; the state alone
    cannot move the positions' learned targets.
  - *Results.* On 32 countries with measured stocks (32 members), the
    update cuts the misfit to the data. The mean |innovation| falls from
    6-190 sd units to 3-18, and the identities hold to at most 5e-14.
    Free runs of five data years after the last assimilated year beat
    persistence on net lending in 10 of 32 countries. This is the
    one-year result again: the rules amount to holding ratios. After the
    imputed-debt fix (bnm-a0s), a fix made after this result, the count
    is 9 of 32, with innovations 6-177 -> 3-17. Measured countries are
    assimilated from their first measured year, but they are learned on
    the imputed years before it. France's free-run error went from 0.023
    to 0.142 (persistence 0.025). With France's imputed years removed
    altogether, it is 0.144. So the 0.023 rested on the old, overstated
    sheets, and the free run is sensitive to years that a half-life of
    one should make negligible.
    data/derived/coupled/assimilation.txt has the figures.
  - *Review* (October 2026; fixes made after the results above):
    - Causality. Relearning read every data year up to t - 2 whatever
      its publication year; it now reads the panel as known at t
      (bnm.sfc-analysis/known-panel, relearn-at). Every year in today's
      panel is published two years on, so no figure moves. A test
      changes everything first known after a free run's origin, and a
      data year published late, and the free run does not move.
    - Spans. A module's last emits were read after its span ended, so
      the consistency module wrote 15 gaps from stale SFC values (BRA
      and BGR 2022-23). The engine no longer carries them; each Keen
      edge's rows fall from 344 to 339, the golden checks are
      unchanged (rerun, all same).
    - The projection is Euclidean, not weighted by the ensemble's
      covariance. It makes no difference here: every member's identities
      have the same right-hand side (the buffers are below 1e-13 of
      output), so the update keeps them and the projection moves a member
      by 1e-14 of the update (DEU, ITA, USA, NLD). augur v0.12.1 says so
      and tests it at 1e7 and with zero and repeated rows.
    - Relearning gives every member the same parameters, so the trend
      growth spread of perturb is gone after the first analysis. Keeping
      each member's draw was tried and tripled the innovations (a growth
      no observation corrects compounds), so the members share them and
      their spread is the state's.
    - The inflation binds: at the cap of 3 in most analyses, and the
      misfit after the update stays 3-18 sd units. A cap of 10 or 100
      makes the innovations larger (to 2,000 sd units) and the free runs
      no better (AUT, DEU, FRA, ITA, USA, BEL). The members' spread in
      private debt is 0.2-0.5% of its level against misfits of many sd:
      the ensemble is underdispersed for model error, not for sampling.
    - Against a restart (one member, learned on what is known at the
      origin, started from the latest known data year, phase A's free
      run), the assimilated free run loses in 25 of 32 countries (median
      net lending error 0.067 against 0.030; persistence 0.027; 16
      members). It also moves with the seed: FRA 0.035, 0.098 and 0.153
      on seeds 1, 3 and 5. The 9 of 32 above is one seed. bnm-cvs should
      score the restart, not the smoother, until the analysis carries
      model error.
    - France (bnm-2zk). The channel is the government's consumption,
      investment and benefits: levels the accounts carry from the start
      year grown at trend, never analysed or relearned. By 2018 FRA's
      members had consumption 0.18 and benefits 0.15 of output against
      the data's 0.24 and 0.22, and the pre-1995 sheets set that path.
      Setting them to the latest known shares at each analysis removes
      the dependence (old and new sheets both 0.035 on seed 1), but takes
      the count to 3 of 32 and the innovations to 2,093 sd units: a jump
      of points of GDP in spending each year is a shock. The shares need
      to be in the analysed state; that is left open.
  - *Fixes for bnm-2zk and bnm-h0i* (October 2026; all made after the
    results above). Scored by bnm.sfc-horizon-run's review: per country
    with eight or more measured years, origin o its last data year less
    five, the start its first measured year (FRA 1995). The ensemble (32
    members) is analysed through engine year o + 2 and run free to o + 5;
    the restart and persistence as above; mean |error| of net lending
    (households, NFCs, government) over data years o + 1 to o + 5, on
    seeds 1, 3 and 5.
    - Seeding. augur.math/rng fills its state linearly from the seed, and
      members were drawn from seed + 7919 i: neighbouring members' draws
      were correlated by 0.71, -0.50, 0.90, -0.84 on the first four (a
      lattice, not an ensemble), and runs under seeds 1, 3, 5 were not
      independent either. Seeds are now mixed (sfc-analysis/seed-of),
      |r| < 0.03. The other call sites of augur.math/rng are bead bnm-0z5.
    - The ratio observations. A level observed as a ratio to output was
      h = level - ratio x Y, so the members' spread in output, which the
      update cannot move, counted as spread in what is observed and the
      gain went to the wrong places. It is now h = level / Y, each member's
      own output its coefficient, still linear in the state.
    - The fiscal levels in the state (the decision on bnm-2zk). Government
      consumption, investment and benefits are in the window, observed
      over output from Table 14 (known y + 2, sd 0.003). Each member
      draws their model error (lognormal, sd learned as the root mean
      square yearly change of each log share, bnm.sfc/learn's :fiscal-sd;
      bnm.modules.sfc/fiscal-error) from its own generator, and starts
      with its levels drawn at that sd. They are updated by the fiscal
      observations alone, and those update nothing else: with 32 members
      and some 300 other observations, a joint update moved them through
      spurious covariances (cumulative seed-1 runs below).
    - Output observed. Output was never observed, so a member's nominal
      scale could run away: POL's and LUX's members went to negative
      output. GDP (known y + 2, sd 2%) now updates the output entries,
      and each member's money levels (balance sheets, flows of funds,
      output, fiscal levels; bnm.engine/update-emits for the output its
      readers see) are scaled by one factor, at most 5 and at least 0.2 a
      year, which keeps every ratio and identity. The accounts' emits are
      read only at lag 1, so no reader has consumed them when the analysis
      rescales them; update-emits now throws if one had (review below 9f).
    - Collapsed members. A member with output at or below zero, or a
      balance sheet not finite, has left the model's domain; at the
      analysis it is replaced by the next healthy member, its own
      model-error stream kept (LUX on seed 5 had broken the update).

    The identities still hold to 1e-10 after every update (tests), and a
    free run still moves with nothing known after its origin, a late
    publication included.

    | cumulative, seed 1 | median error | mean | beats restart | beats persistence | median innovation |
    |---|---|---|---|---|---|
    | before | 0.093 | 0.094 | 8 | 9 | 24 |
    | seeding | 0.095 | 0.099 | 6 | 5 | 24 |
    | + ratio observations | 0.037 | 0.069 | 14 | 11 | 6 |
    | + fiscal levels, joint update | 0.063 | 0.079 | 5 | 4 | 9 |
    | + fiscal update localised | 0.037 | 0.056 | 9 | 8 | 9 |
    | + output observed, resampling | 0.031 | 0.284 (BGR 7.8) | 15 | 12 | 8 |

    Free-run error by country, mean over seeds 1, 3, 5 (their range), the
    restart and persistence (one each), and the mean |innovation| before
    the update in sd units:

    | country | before | after | restart | persistence | innovation, before -> after |
    |---|---|---|---|---|---|
    | AUT | 0.136 (0.022-0.197) | 0.021 (0.019-0.022) | 0.030 | 0.033 | 702 -> 4 |
    | BEL | 0.083 (0.047-0.152) | 0.027 (0.026-0.027) | 0.028 | 0.021 | 41 -> 4 |
    | BGR | 0.095 (0.088-0.103) | 2.879 (0.039-7.771) | 0.052 | 0.028 | 52 -> 1627 |
    | BRA | 0.140 (0.114-0.176) | 0.046 (0.027-0.077) | 0.022 | 0.019 | 15 -> 31 |
    | CHL | 0.029 (0.028-0.029) | 0.025 (0.024-0.026) | 0.027 | 0.031 | 7 -> 5 |
    | COL | 0.039 (0.028-0.046) | 0.029 (0.027-0.030) | 0.027 | 0.026 | 24 -> 12 |
    | CZE | 0.202 (0.134-0.266) | 0.056 (0.052-0.061) | 0.118 | 0.020 | 36 -> 6 |
    | DEU | 0.102 (0.065-0.129) | 0.027 (0.026-0.028) | 0.025 | 0.016 | 7 -> 4 |
    | DNK | 0.040 (0.021-0.059) | 0.029 (0.028-0.030) | 0.029 | 0.034 | 9 -> 4 |
    | ESP | 0.032 (0.021-0.049) | 0.026 (0.025-0.028) | 0.025 | 0.020 | 23 -> 5 |
    | EST | 0.195 (0.193-0.197) | 0.062 (0.051-0.073) | 0.123 | 0.026 | 25 -> 8 |
    | FIN | 0.022 (0.018-0.029) | 0.021 (0.019-0.023) | 0.020 | 0.023 | 16 -> 10 |
    | FRA | 0.145 (0.142-0.150) | 0.020 (0.019-0.021) | 0.021 | 0.025 | 13 -> 4 |
    | GBR | 0.059 (0.024-0.117) | 0.021 (0.020-0.021) | 0.018 | 0.048 | 22 -> 4 |
    | GRC | 0.050 (0.030-0.077) | 0.047 (0.029-0.080) | 0.034 | 0.032 | 125 -> 39 |
    | HRV | 0.023 (0.022-0.024) | 0.020 (0.019-0.020) | 0.023 | 0.021 | 28 -> 10 |
    | HUN | 0.117 (0.110-0.121) | 0.088 (0.063-0.119) | 0.105 | 0.029 | 38 -> 8 |
    | IRL | 0.088 (0.081-0.101) | 0.109 (0.078-0.146) | 0.080 | 0.135 | 36 -> 12 |
    | ITA | 0.081 (0.017-0.203) | 0.022 (0.021-0.022) | 0.013 | 0.030 | 66 -> 4 |
    | LTU | 0.147 (0.066-0.281) | 0.055 (0.031-0.099) | 0.047 | 0.040 | 65 -> 11 |
    | LUX | 0.159 (0.145-0.177) | 0.031 (0.028-0.038) | 0.017 | 0.025 | 75 -> 9 |
    | LVA | 0.066 (0.023-0.091) | 0.061 (0.036-0.100) | 0.044 | 0.029 | 48 -> 21 |
    | MEX | 0.112 (0.091-0.126) | 0.068 (0.068-0.069) | 0.066 | 0.016 | 33 -> 7 |
    | NLD | 0.119 (0.037-0.263) | 0.021 (0.020-0.023) | 0.015 | 0.026 | 65 -> 4 |
    | NZL | 0.127 (0.120-0.131) | 0.100 (0.066-0.120) | 0.036 | 0.025 | 121 -> 46 |
    | POL | 0.192 (0.172-0.218) | 0.124 (0.080-0.211) | 0.160 | 0.029 | 34 -> 20 |
    | PRT | 0.128 (0.015-0.212) | 0.033 (0.031-0.035) | 0.026 | 0.031 | 56 -> 5 |
    | ROU | 0.161 (0.089-0.217) | 0.126 (0.073-0.204) | 0.065 | 0.027 | 108 -> 49 |
    | SVK | 0.136 (0.074-0.172) | 0.109 (0.083-0.159) | 0.142 | 0.030 | 54 -> 9 |
    | SVN | 0.149 (0.093-0.179) | 0.032 (0.031-0.036) | 0.054 | 0.027 | 14 -> 6 |
    | SWE | 0.060 (0.019-0.141) | 0.022 (0.021-0.023) | 0.016 | 0.018 | 22 -> 6 |
    | USA | 0.070 (0.041-0.103) | 0.031 (0.030-0.033) | 0.085 | 0.025 | 11 -> 5 |

    - France. 0.145 -> 0.020, the restart's 0.021, on every seed. Most of
      the old error was the seeding: with seeding fixed alone it is 0.026.
      Its free run no longer moves with its 1970-94 sheets: dropping them,
      or raising their debt by half, changes its free-run net lending by
      under 0.01 of GDP (test).
    - Across seeds, the median range of a country's error is 0.004 (was
      0.054). The median |innovation| is 7 sd units (35), and the
      inflation no longer sits at its cap.
    - Without BGR, the ensemble's median error is 0.031 (mean 0.049),
      the restart's 0.029 (0.050), persistence's 0.026 (0.030). It beats
      the restart in 12 of 31 and persistence in 10 of 31: the free runs
      are now as good as the restart, not better, and neither beats
      persistence at these horizons.
    - BGR is left (bead bnm-rr7): its 1997 hyperinflation (nominal output
      x8.7) makes relearned trend growth 4.1 in 1999, and expected output
      compounds faster than the observed output can rescale it (7.8,
      0.83, 0.04 on seeds 1, 3, 5). NZL, ROU, POL and LVA keep
      innovations of 20-50 sd units.
  - *Golden checks* (bnm.coupled-run): with one member and no
    assimilation, the run reproduces phase A exactly. The stock modules'
    rows match bnm.engine-run's table line for line (7,963,260 rows), and
    bnm.states reads all 19 tables the same both ways (50,807 variables).
    Allocation matches bnm.allocation-run's table line for line
    (586,843). The SFC rows match bnm.sfc-run's free runs as sorted rows
    (9,450). World3 matches the engine's World3 alone (37,989 rows).
    The state inputs of the default paths and of states-t45, read by
    bnm.world-run/states-of from the files and from the coupled table,
    are byte-identical (1,770,823 bytes). The t44 and t46 world-norel
    runs read no stock tables, so their byte identity is unaffected by
    construction.
  - *Sheaf gaps* (data/derived/coupled/sheaf.txt), written as
    diagnostics and not reconciled:
    - Private debt: the SFC core's household and NFC debt against Keen's
      d. Measured countries agree (USA 1.47 against 1.49, DEU 1.37
      against 1.41). Imputed stocks did not: ZAF was out by 5.5 of GDP and
      CRI by 4.4, because interest at a pooled rate overstated them where
      lending rates are high. Since bead bnm-a0s (2a), imputed debt comes
      from BIS or the IMF GDD. The median gap is now 0.17 (was 0.26) and
      the largest 1.6 (was 5.5). Median |gap| by imputed country, before
      and after: ZAF 5.45 -> 0.05, CRI 4.35 -> 0.11, COL 1.18 -> 0.02,
      BRA 0.67 -> 0.11, CHN 0.46 -> 0.19, NZL 0.45 -> 0.52, FRA 0.22,
      FIN 0.09 and SWE 0.07 unchanged, IRL 0.80 unchanged. Keen's d is
      BIS's private credit where BIS has the country, so for ZAF, COL,
      BRA and CHN the agreement is partly by construction. CRI's d is
      WDI's private credit, so CRI's agreement is not. The largest gaps
      left are IRL 0.89, BGR 0.88, HRV 0.80, LUX 0.73 and LVA 0.60, all
      measured stocks (BGR 0.95 with the stale rows the review removed).
    - Wage share against Keen's omega: the SFC share is on average 0.10
      lower (D1/GDP against PWT's labour share, which counts mixed
      income), most of all in GRC, BGR, PRT and SVN.
    - Employment: median 0.015, with BGR 0.26 off (0.33 with stale rows).
    - NFC investment against KLEMS's NFC industries: median 0.013 of
      GDP. On the data alone (Table 14 S11 P5 against KLEMS) the median
      is 0.014; IRL is 0.16 (multinationals).
  - *Free runs* for bnm-cvs: bnm.coupled/free-run takes any modules, and
    bnm.sfc-analysis/free-run takes a country. Each starts from a data
    year, assimilates and relearns through an origin, and then runs N
    years with nothing known after the origin. Both return the members'
    median and spread per variable.

## 7. Mapping today's code onto modules

Today's namespaces as modules: what each emits, what it should read and
does not, and what is missing.

| namespace | module | emits now | should read, does not | missing |
|---|---|---|---|---|
| bnm.keen (tools keen-run) | D, with F's debt | keen/w, l, d, critical debt, blow-up year, good/bad equilibrium | d from the SFC core (NFC and HH split); r from F; investment kappa as the sum of F1 across sectors; employment from R3 | the household sector (G&CL's d is firms'); interest as someone's income; government |
| bnm.credit-run | F | credit/gap | the SFC credit stocks; by sector | gaps by sector (household and non-tradable credit gaps; the banking lattice reads M-V's unproductive ratio, but no stock does) |
| bnm.econ-factors | C (investment-regime, public-capital), F (financialisation, depth, market size) | econ/* factors | BRSS, v2clstown, KAOPEN, repression gap | the control vector as a latent with sign priors over the new measures; private financial power |
| bnm.econ-structure | R | finance and manufacturing VA shares | (fine: measurement) | shares by more sector groups for the allocation test |
| bnm.econ-divergence | F | divergence gaps | F's asset stocks | revaluation as an SFC flow |
| bnm.econ-coupling | F (external) | coupling liability share and slope | the rest-of-world sector's flows | reserves channel (SEFER, TIC) |
| bnm.migration | R | ci-share, export share, capital intensity | capacity from investment (5.2) | the link from allocation to the export shares |
| bnm.geo-gap | P (interstate), from R and F | material share, financial share, gap | R's and F's world shares as emitted | none beyond the dyad test |
| bnm.supply | R | outage, outage-path, upstream, downstream | capacity per [region product] scaled by sector capital; recipes by year (bnm.recipes) | capacity change; recipes are 2011 only |
| bnm.stages | R | stage HHI, shares, displacement D | capacity from investment and from control (nationalised mines) | plant entry from finance |
| bnm.recipes, bnm.tech-demand | R | recipe coefficients, material demand by technology | technology adoption from bnm.technology as an emitted input (now read from files) | |
| bnm.food | R (and D, P exposure) | stocks, use, import dependence, supplier HHI, storage pressure | world prices from W; income from D (food demand) | the household cost share (food in spending) |
| bnm.cycles | W (world prices), R (capacity) | price gap, coverage, pipeline, capacity gap, beta | world demand from the sum of countries' R | capacity starts financed by F (now from expected profit only) |
| bnm.energy-pilot | R -> D | energy exposure, pass-through, shock | | an energy module (stocks of oil and gas, power capacity) beyond the pilot |
| bnm.technology | R | adoption, lag, gap, productivity inflow | investment from F as a driver of adoption speed | |
| bnm.cells, bnm.cell-profiles, bnm.chokepoints, bnm.essentiality, bnm.located-shocks | R (located), W (chokepoints) | cell profiles, exposure, expected loss | P's event hazards (essentiality already reads the onset forecasts: a P -> R channel that exists) | the cell as the place of capacity (principle 11: when the trials say so) |
| bnm.tension (tension-run) | P | perceived and expected progress, trust, MMP, EMP, SFD, Psi | D1-D4 as typed inputs (now WDI, WID, GDD columns); R4, R5 | food and fuel shocks into MMP; regime as moderator |
| bnm.polecon | D | profit rate, wage share, investment share | the SFC core (it is the same thing measured) | merged into D |
| bnm.lattice, bnm.world-run, bnm.lattice-political, bnm.lattice-banking | P (events) | onsets, forecasts, principal contradictions | the coupled state as a where and a fallback (T9), not as more year-node inputs | the P2/F5 feed back to C |
| bnm.states | glue | the readers of the stock tables | the coupled table | retired to a reader of one table |
| bnm.sheaf | glue | reads, coboundary | the channel edges | maps per edge (loadings, unit conversions) |
| bnm.network | glue | multilayer network, couple | the graph from declarations | |
| bnm.assimilate | glue | calibrate (ES-MDA), assimilate (EnKF) | | localisation by graph distance; the coupled ensemble |
| bnm.stockflow | glue | the model form, imbalance | | the SFC matrix form (sector x instrument) |
| bnm.world3 | reference | World3-03 standard run | | (a test of the engine: World3's sectors declared as modules must reproduce the monolithic run) |

**What is missing entirely:**
- the SFC core;
- the control layer as a stock model with flows;
- allocation by sector;
- capacity from investment by sector;
- the household distribution block (top and bottom shares from factor
  shares and transfers);
- the politics-to-control hazards;
- the government as a sector with a fiscal rule.

World3 gives the engine a cheap first test: split World3-03 into its five
sectors, declare them as modules with their cross-sector auxiliaries as
channels, and require the coupled run to match bnm.world3's monolithic one
to its 7e-6 tolerance. That tests the stepping order, the lags and the
substeps on a known answer (principle 9).

## 8. Gaps

### 8a. Data not on disk

| what | source | coverage | for |
|---|---|---|---|
| government-owned bank share | World Bank BRSS, https://www.worldbank.org/en/research/brief/BRSS | five waves 1999-2019, about 160 jurisdictions | C c-bank |
| state ownership of the economy (v2clstown), and v2stfisccap, v2clprptyw | V-Dem full Country-Year (not Core), https://v-dem.net/data/ | 1789-2025, annual | C c-own |
| La Porta et al. state bank shares 1970, 1995 | paper's data appendix (Shleifer's site) | 92 countries | C anchor |
| financial balance sheets by sector and instrument | OECD Table 710/720 via data-explorer.oecd.org | OECD and partners, mostly 1995- | F SFC stocks |
| loans by industry, total social financing | PBoC statistics, http://www.pbc.gov.cn/diaochatongjisi/116219/index.html; NBS yearbooks | China 2002- (TSF), loans by industry from yearbooks | F1 for China |
| MFI loans by NACE sector | ECB Data Portal | euro area 2003- | F1 after 2014 |
| capital controls by asset class | Fernández et al. 2016, NBER data catalogue | 100 countries 1995- | C c-open |
| expropriations | Kobrin 1984, Minor 1994, Hajzler 2012 (papers; data on request) | 1960-2006 | P3 |
| privatisations | Privatization Barometer (https://www.feem.it/en/?p=40423); World Bank privatization database (archived) | EU/OECD 1977-; developing 1988-2008 | P4 |
| SOE data | OECD SOE dataset; IMF Fiscal Monitor April 2020 ch. 3 | snapshots | C validation |
| Laeven-Valencia 2018 detail (nationalisations, fiscal cost) | IMF WP/18/206 xlsx (IMF blocks scripts: a browser download; PENDING.md) | 151 episodes 1970-2017 | F6 (the PDF and text are on disk) |
| sanctions | Global Sanctions Data Base (request form) | 1950-2023 | P6 |
| reserves by holder | IMF SEFER, US TIC | | X1 reserves channel |

### 8b. On disk and not yet used

- OECD Table 14 (the SFC flows): used only for the NFC financialisation
  measures. Its sector net lending, interest, saving and public
  corporations are unused.
- EU KLEMS 2023 capital and labour accounts: investment, capital and hours
  by industry. Unused.
- Müller-Verner by sector: used only as one ratio, the banking lattice's
  mv_unproductive (household and non-tradable credit over tradable;
  scripts/lattice-data.py). The shares themselves are unused.
- ICSD: in econ/public-capital, with the imputation caveat above.
- KAOPEN, WID wealth and the Fed's Z.1: partly used.
- EXIOBASE pxp 1995-2022: used for the product panel and capital
  intensity, not for GFCF by product.

### 8c. Models not built

1. **The SFC core** (new bead). Five sectors per country-year, the
   transaction-flow matrix from Table 14 (and Z.1, Eurostat), stocks from
   BIS, JST, GDD and WID. Identities as hard constraints. Keen as the
   behaviour of firms and households. A learned household saving function
   and government fiscal rule.
2. **The control layer** (bnm-4hv.2). Stocks with flows: nationalisation,
   privatisation, liberalisation. A latent control vector with sign
   priors.
3. **Sectoral credit allocation** (bnm-4hv.3). The Dirichlet DLM on
   Müller-Verner and BIS.
4. **Capacity from investment** (new bead, part of bnm-4hv.3). Sector
   capital stocks from investment by industry (EU KLEMS, EXIOBASE GFCF),
   linked to bnm.supply's regions and products and to bnm.stages' plants.
5. **Typed real-to-political inputs** (bnm-4hv.4). bnm.tension reading
   D1-D4, R4 and R5 from the engine.
6. **Politics-to-control hazards** (bnm-4hv.5).
7. **The engine** (bnm-4hv.6). Declarations, the graph, stepping,
   ensemble, assimilation, sheaf checks, the coupled table.

## 9. Validation plan

### 9a. Principles

- **Hindcasts are registered before the engine runs on the episode**
  (principle 9). The design years run to 2002 and include 1973, so the
  four hindcasts are not out of sample in the event model's sense.
- **Fit with the episode's era left out.** As T5 did, each hindcast's
  parameters are fitted with its era left out:
  - 1965-1980 for 1973;
  - 1995-2015 for China and 2008;
  - 1975-2007 for the US.

  Then the engine runs the episode free (no assimilation after its
  start), from the state assimilated to its start.
- **Every hindcast has two comparisons** (principle 5):
  - **Uncoupled.** The modules run on their own: each reads data where the
    coupled run reads another module. This tests whether coupling adds
    anything.
  - **Placebo.** The coupled run with the channel of interest fed another
    country's input of the same kind (a swapped control vector, a swapped
    allocation). This tests whether the gain is the mechanism's or the
    structure's.
- **Scoring.** The score is the error on the observable paths: the CRPS
  of the ensemble against each observed series, standardised by the
  series' own variability, averaged over the paths named for the
  episode. The direction of each path's change over the episode is
  reported beside it.

### 9b. The four hindcasts

**China's state-led investment path, 1995-2019** (start state 1995, free
from 2000).
- *Observable paths:*
  - the investment share (PWT csh_i: 0.29 in 2000, 0.46 in 2010, 0.43 in
    2019);
  - the household and NFC net lending (T14: households +12 to +16% of VA,
    NFCs -1 to -12%);
  - the household share of credit (M-V: 0.05 in 2000, 0.19 in 2014; BIS
    after);
  - the capital-intensive export share (migration/ci-share);
  - China's world shares in crude steel, aluminium and copper refining
    (stages);
  - the credit gap.
- *Falsification.*
  - The coupled run with China's control vector must get the direction of
    at least four of the six paths right and beat the uncoupled run's
    CRPS.
  - The placebo, China with the US control vector, must do worse on the
    investment share, household net lending and ci-share.
  - If the placebo does as well, the control channel carries nothing for
    the case the epic is about, and that is reported.

**US financialisation and deindustrialisation, 1980-2007** (start 1980,
free from 1985).
- *Observable paths:*
  - the household share of credit (M-V and Z.1: 0.60 in 1980, 0.67 in
    1990, 0.65 in 2007);
  - the manufacturing VA share (econ-structure, BEA: about 21% to 12%);
  - the labour share (PWT: 0.62 to 0.59);
  - the ci-share;
  - the financialisation index (from 1997);
  - the household and NFC net lending.
- *Falsification.* The allocation channel must explain more of the US
  manufacturing decline than of Germany's and Japan's over the same years.
  Germany and Japan are the control ECONOMIC-CHARACTER found necessary:
  they lost as much in chips and ships without being financialised. If the
  allocation share of the US decline is no larger, the finance-to-real
  story does not separate the US from the rich-country trend (as the 7b
  cross-section already suggested), and that is the result.

**The 1973 oil shock cascade, 1970-1985** (start 1970, free from 1972; oil
price fed as observed, W).
- *Observable paths and events:*
  - the oil-producer nationalisations of 1971-1976 (Algeria 1971, Libya
    1971-73, Iraq 1972, Kuwait and Saudi participation 1974-76, Venezuela
    1976);
  - the importers' energy pass-through and inflation crises (1973-75,
    1979-80);
  - the wage-share squeeze;
  - the petrodollar recycling into developing-country borrowing (BIS LBS,
    external debt);
  - the Latin American sovereign defaults of 1982.
- *Falsification.* The P -> C hazard (GKS: price times weak constraints),
  fitted without 1965-1980, must raise the nationalisation probability of
  oil producers in 1971-1976 above their baseline. The pass-through must
  rank importers' output losses better than a uniform shock. A
  coupled-only test also applies: does the F channel (producer surplus to
  importer credit) put the 1982 defaults in the countries that borrowed
  most in 1974-1981? It is descriptive at n of about 15 defaults.

**2008, 2000-2014** (start 2000, free from 2003).
- *Observable paths:*
  - the non-tradable and household credit shares (M-V: Spain construction
    and real estate 0.14 to 0.26);
  - house prices;
  - the banking crisis onsets of 2007-2008;
  - the sector balance swing of 2009 (US government -4.0 to -13.1% of VA,
    households -1.1 to +4.1);
  - unemployment;
  - tension (the progress gap) in 2009-2014.
- *Falsification.*
  - The coupled run must rank the 2007-08 crisis countries by their
    2002-2006 non-tradable credit growth above the others. This is
    Müller-Verner's result, replicated with the era left out.
  - Given the crisis, the SFC closure must give the 2009 swing's direction
    in each sector.
  - Tension must rise more in the crisis countries than in their placebo
    (the same countries in the 1990s).

### 9c. The coupled state against the event model (bnm-4hv.8)

Registered after the engine and the hindcasts, on the test years 2003-2022.
B is T4's recalibrated base, the same rows and machinery
(tools/bnm/robustness).

- **D_c, the coupled state**, against D_s (today's thirteen stand-alone
  states) and P' (coverage-matched placebos of D_c):
  - **T9's criteria.** Dropout of slow gauges, dark countries and doubled
    lags (degradation ratio against the placebo), calibration drift, and
    the worst-era harm bound (lower end below -0.002 log).
  - **Keep rule.** As T9's: at least one of dropout and drift passes after
    Holm, and no harm.
  - **The added contrast.** D_c against D_s. The coupled state should
    cushion at least as well as the separate states, since it is the same
    information made consistent; if it does worse, the consistency costs
    information.
- **The use-case rule (bnm-82j.1).** Top-k recall and precision of hard
  onsets ("which countries to watch next year", k = 10 and 20 per kind
  group per year) and the alert lead time:
  - B against B + D_c;
  - B + D_c against B + P';
  - pooled over the political kinds (where T4 found the states help) and
    over the economic kinds separately.
- **Where over when** (T4's test 2) for D_c, as a check that the coupled
  state keeps the cross-sectional edge the stand-alone states had.
- **Prediction from the trials.**
  - Mean log score: level.
  - Dropout: pass.
  - Top-k for the political kinds: a small gain in recall at k = 20.
  - Economic kinds: level or a loss (T4: where the economic kinds strike
    is not where the levels point).

  Registered as predictions before the run.

### 9d. The allocation test (bnm-4hv.12)

**Registered 24 October 2026**, before any allocation outcome was
computed: no change in a credit share from Müller-Verner has been read,
alone or against control/state. The power check below reads only which
country-years hold total, household and manufacturing credit, never
their values. The control layer's specification is still under review
(4d). The test names its regressor as `control/state`, the state axis of
bnm.control as that review settles it (data/derived/stocks/control.csv,
signed so that more state direction is higher). Nothing below is chosen
after that review.

**Question.** Does who controls finance predict where new credit goes
within a country (5.1)? If it does, a country's control/state should
lead its manufacturing share of private credit, beyond what that share's
own recent change and the country's income give. It should also beat
the same regressor taken from another country (principle 5).

**Data.**
- Müller-Verner (data/raw/slow/mullerverner, Build/MuellerVerner.dta)
  gives the country-years with total private credit (Total), households
  (HH) and manufacturing (C) at year end: 3,175 country-years in 98
  countries, 1950-2014. Known at +2 (channel F1, national sources).
- s[c,t] = C / Total. The **outcome** is
  y[c,t] = 100 (s[c,t] - s[c,t-1]), the change in the manufacturing share
  of private credit in percentage points.
  - It is the allocation of the year's new credit measured against the
    stock's. New credit to manufacturing above the stock's share raises
    s, and below it lowers s.
  - It is bounded and defined in years when total credit falls. Loans
    are at book value, so it is revaluation-free.
  - A row needs s at t, t-1 and t-2, so that its own lag exists: 2,950
    rows in 98 countries.
- **Income** is log real GDP per head (Maddison Project 2023, gdppc),
  known at +1.
- **The regressor** is control/state, each country-year's value as its
  stock table dates it (the year known, with its data year).

**Design.** One-step-ahead forecasts at yearly origins, with an expanding
window. At origin `to`:
- every input is read at data years up to to-2: Müller-Verner's last
  known year at to is to-2, and control/state and income are taken at
  their latest data year at or before to-2, as known by to;
- the target is y for data year t = to-1, the first year not yet
  published;
- the models are fitted on the rows whose target year is at most to-2,
  using only what was known at the origin for each row's inputs.

The three models are OLS with country intercepts (the within estimator):
- **B (baseline: persistence and income).**
  y[c,t] = a_c + rho y[c,t-1] + lambda ln(income[c,t-1]) + e.
- **M (control).** B + beta control/state[c,t-1].
- **P (placebo).** B + beta control/state[c',t-1]. c' is a fixed
  derangement of the countries, drawn once with seed 20261024 among the
  countries that have control/state. It is the same series at the same
  years from another country, so it keeps the panel's common timing and
  moves the place.

A country with fewer than five training rows takes the pooled intercept.
A row enters only where all three models can score it (the regressor and
the placebo's regressor both present), so the comparisons are paired.

**Split.**
- Design years are targets up to 2002. They are fitted, never scored.
- Test years are targets 2003-2014 (origins 2004-2015), the 1,075 test
  rows of the mask in 97 countries (fewer where control/state is
  missing).
- Müller-Verner ends in 2014, so nothing later exists to test.

**Primary metric.** Skill against X = 1 - SSE_M / SSE_X, pooled over the
test rows, for X = B and X = P. Its interval comes from resampling
countries with replacement (2,000 draws), 90% two-sided.

**Pass.** Both lower ends are above 0, against B and against P: one-sided
5% each, an intersection-union test, so no multiplicity correction. If
the gain over B passes and the gain over P does not, the result is the
period's or the panel's, not the mechanism's. It is reported as a
failure of the primary.

**Equivalence bound.** The result is equivalent to nothing for allocation
when the upper end of the skill against B is below +0.025.

**Power** (scripts/allocation-power.py, on the mask above; 400
simulations, 1,000 bootstrap draws). A standardised within-country AR(1)
regressor leads an outcome whose noise has SD 1. The effect is the
partial R^2 the regressor explains.

| persistence of regressor, outcome | partial R^2 at 80% pass | skill against B there | null pass rate |
|---|---|---|---|
| 0.9, 0.2 | about 0.035 | about +0.024 | 1.3% |
| 0.7, 0.0 | about 0.026 | about +0.023 | 1.0% |

So the test finds an effect of about 3% of the outcome's variance four
times in five, which is the equivalence bound's +0.025 in skill. Below
about 1% it is blind. The bound is recomputed by the same script on the
rows actually scored (control/state's coverage of the mask) before the
outcome is read. If the recomputed 80%-power skill is larger, that is
the bound, and it is reported.

**Sign prior** (principle 4, 5.1). beta > 0: more state direction, more
of new credit to manufacturing.

**For the record (not tests).**
- The full-sample within-country beta on design and test rows, with
  country-clustered standard errors, and its sign.
- The same design on the tradable share (A + B + C, with the BC
  combination; prior beta > 0) and on the household share (HH; prior
  beta < 0).
- The skill by era (2003-2007, 2008-2014).
- Skill against persistence alone (B without income).

**Not changed after the outcome is read:** the outcome, the three models,
the split, the derangement's seed, the metric, the interval, the pass
rule and the bound (other than its recomputation above). The test is
outside the event model's decision eras, so it is not a line in
docs/TRIALS.md.

**Addendum, 24 October 2026.** Written after the control review landed
(bf49f19, 7e6c964, 32a104e) and before any outcome was read. Since
e65340a, nothing from Müller-Verner has been computed against any
control series.

*What the regressor is.* In the review's specification, control/state is
the one-axis fit with every uniqueness bounded at 0.2. Its rank
correlation with V-Dem's state ownership of the economy alone is 0.97
(4d), over the rule's 0.95. So the primary regressor is, in effect,
V-Dem's v2clstown with small corrections from the bank, capital,
openness, repression, PMR and expropriation stocks. This changes what
the test can say.
- *A pass shows* that, within countries, expert-coded state ownership of
  the economy leads the manufacturing share of new credit, beyond
  persistence, income and the same series from another country.
- *A pass does not show* that a latent state direction, measured jointly
  by state banks, public capital, capital controls and financial
  repression, moves allocation. Those stocks share little with
  state-own (4d), and their own test is the without-state-own variant
  below.
- *Hindsight.* V-Dem's coders rate past years with later knowledge, so a
  rating for year t-1 may reflect credit policy the coder saw afterwards.
  Its known rule (+1) does not cover that. A pass on the primary alone
  cannot rule out that the coders read allocation into ownership. The
  without-state-own variant, built from measured stocks, is not exposed
  to this.
- *A failure* says that expert-coded ownership does not lead allocation
  within countries at this power. It does not speak for the other stocks.

*Secondary regressors* (pre-specified; reported, not primary). They come
from data/derived/control/control-variants.csv, as written by the
reviewed bnm.control-run:
- **S1, the equal-weight composite** (variant `equal`): the mean of the
  signed standardised indicators.
- **S2, the bounded fit without state-own** (variant `no-own`).

Each enters exactly as control/state does in M and P: the same models,
split, derangement seed, metric, bootstrap and pass rule (both lower ends
above 0, against B and against P). The variants file has no data-year
column. Its year is the year of the causal fit, so at origin `to` the
row for year to-1 is read, the last fit made before the origin. The
primary is also reported under that alignment, beside its registered
one, so the three can be compared on the same reading.

Each variant is scored on its own paired rows. For each, the one-sided
p-value of a comparison is the share of the 2,000 country-resampled
draws whose skill is at most 0. Its p is the larger of its two p-values
(against B and against P), which is the intersection-union test. Holm's
procedure runs over S1 and S2 at a familywise 5%. The primary is not in
that family: it keeps its own 5% test as registered. Each variant's
equivalence bound is recomputed by scripts/allocation-power.py on its
own rows, by the rule above, before its outcome is read.

The sign prior is beta > 0 for both. If S2 passes after Holm and the
primary does not, the measured stocks carry the allocation signal and
the coded ownership does not. That is reported as such, not as a pass of
the primary.

**Second addendum, 24 October 2026.** Written before any outcome was
read. It pins mechanics the registration left open; none of it is chosen
from data.
- *Placebo map.* `derange(sorted countries, numpy default_rng(20261024))`,
  with `derange` as in scripts/allocation-power.py (e5ca51a), computed
  once. The countries are those with any control/state value, read as
  registered, on a row of the scored panel (the mask's outcome rows with
  income). The same map applies to S1 and S2; it is not redrawn on their
  countries. A row whose partner has no value at that reading drops from
  all three models.
- *Correction.* The mask starts in 1940, not 1950: 3,175 country-years
  1940-2014, the first outcome row in 1942. The other counts stand.
- *Rows.* Each row's inputs are read once, at its own origin (target year
  + 1). The paired rows (all three models present) are both the training
  and the scored rows, for each regressor on its own.
- *Bootstrap.* For each regressor, a fresh `default_rng(20261024)` draws
  a 2,000 x n matrix of indices into the n sorted scored countries. The
  same draws serve the comparison against B and against P. The interval
  is the 5% and 95% quantiles of the draws' skill.
- *Bound.* From each of the two power settings, the first partial R^2 on
  the script's grid that passes in at least 80% of simulations, and its
  median skill against B. The bound is the larger of the two, or +0.025
  if that is larger.

**Power on the scored rows, 24 October 2026**, before the outcome was
read (scripts/allocation-test.py --rows, then allocation-power.py --rows;
400 simulations, 1,000 draws). control/state's coverage leaves 2,096
paired rows in 86 countries, 931 test rows in 85 (S2: 2,092 and 931).
The placebo map runs over 86 countries.

| regressor | phi .9, rho .2: 80% at | phi .7, rho 0: 80% at | bound |
|---|---|---|---|
| primary (and S1, the same rows) | R^2 0.05, skill +0.0363 | R^2 0.04, skill +0.0374 | +0.0374 |
| S2 | R^2 0.05, skill +0.0363 | R^2 0.04, skill +0.0375 | +0.0375 |

Nulls pass 0.5% of the time. Losing 144 test rows costs power: the
detectable effect is now about 4-5% of the outcome's variance, not 3%,
and the bound rises from +0.025 to about +0.037.

**Results, 24 October 2026** (scripts/allocation-test.py, run once after
the power above; 931 test rows in 85 countries, 2,000 draws).

The primary is **equivalent to nothing**. control/state does not forecast
the manufacturing share of new credit beyond persistence and income.

| comparison | skill | 90% interval |
|---|---|---|
| M vs B | -0.0003 | [-0.0025, +0.0019] |
| M vs P | +0.0070 | [+0.0025, +0.0117] |

The lower end against B is below 0, so the intersection-union test fails.
The upper end against B, +0.0019, is below the bound of +0.0374, and
below the registered +0.025 too. M beats the placebo only because the
placebo's borrowed series adds noise to B; it does not beat B. Under the
addendum, control/state is in effect V-Dem's state ownership, so this
says that expert-coded state ownership does not lead allocation within
countries at this power. It says nothing for the other control stocks,
which are S2's test.

Secondaries, Holm over S1 and S2: neither passes.

| | M vs B | M vs P | p (IU) | Holm | verdict |
|---|---|---|---|---|---|
| S1 equal | +0.0013 [-0.0016, +0.0042] | -0.0023 [-0.0084, +0.0025] | 0.77 | 1.00 | equivalent to nothing |
| S2 no-own | -0.0071 [-0.0176, +0.0024] | -0.0159 [-0.0281, -0.0056] | 1.00 | 1.00 | equivalent to nothing |

S2 does worse than its placebo. The measured stocks without state-own
carry no allocation signal either.

For the record (not tests):
- Full-sample within beta on all 2,096 paired rows: control/state
  +0.137 (clustered SE 0.159), S1 +0.161 (0.165), S2 -0.143 (0.118). The
  primary's sign matches the prior; none is distinguishable from 0.
- The primary at fit year to-1 gives the same rows and the same numbers:
  on the scored rows the two readings pick the same value everywhere.
- By era: 2003-2007, M vs B -0.0038 [-0.0069, -0.0013]; 2008-2014, M vs B
  +0.0040 [+0.0006, +0.0074] and M vs P +0.0087 [+0.0036, +0.0139]. The
  later era's small gain is not registered and is not a result.
- Against persistence alone (B without income): -0.0457 [-0.0746,
  -0.0221]. Income itself adds error out of sample.
- Tradable share (A + B + C, 845 test rows): M vs B +0.0099 [-0.0002,
  +0.0204], M vs P +0.0094 [-0.0004, +0.0196], beta +0.49 (SE 0.30).
  Household share (994 test rows): M vs B +0.0073 [-0.0025, +0.0163],
  M vs P +0.0082 [-0.0016, +0.0172], beta -0.56 (SE 0.18), the prior's
  sign. Neither interval clears 0.

### 9e. The capacity test (bnm-4hv.11)

**Registered 28 October 2026**, before any capacity outcome was
computed: no observed capacity series has been read against any
completion rate. The completion rates exist (data/derived/coupled/
allocation.csv); the targets below have not been built.

**Question.** Does investment already made, completing through the time
to build (5.2a), predict next year's capacity growth by industry better
than persistence, better than the growth's mean, and better than the same
completion rate from another country?

**Targets** (observed capacity, a node n is a country and a series; the
30 EU KLEMS countries):
- *power* (industry D-E): UNSD Energy Statistics, commodity EC,
  transaction 133, net installed capacity, MW (data/raw/material/
  unsd-energy);
- *refining* (C19): UNSD commodity 4100, transaction 131, refinery
  capacity;
- *stages*: the stage table (data/derived/supply/stages.csv, BGS rows
  only), its smelter and refinery stages (C24-C25) and its mines (B). These
  are output, not capacity: utilisation moves them too. Of a data year's
  several editions, the last.

All are known at +2 (the stage table's known column; UNSD by rule).
g[n,y] = ln(Q[y] / Q[y-1]) with both positive, clipped to [-1, 1].

**Predictor.** x[c,i,y] = investment/klems/completion-rate-<i> for the
node's country and industry and data year y, as the engine emits it (known
at y + 1).

**Design.** One-step-ahead at yearly origins, expanding window, as 9d. At
origin `to` the target is g for data year y = to - 1; the inputs are x
for y (from investment up to to - 2) and g for y - 1 (data years up to
to - 2). The models are fitted on the rows whose target year is at most
to - 2. Models, per target family (power, refining, stages):
- **B (persistence)**: g-hat = g[n, y-1].
- **A (mean)**: the family's training mean of g.
- **M (completions)**: OLS g = a_f + b_f x.
- **P (placebo)**: M with x taken from country pi(c), the same industry
  and year. pi = derange(sorted countries with any completion-rate row,
  numpy default_rng(20261028)), derange as in scripts/allocation-power.py.

A row enters (training and scoring) only where x, the placebo's x and
g[n, y-1] all exist, so the comparisons are paired. A family with fewer
than 20 training rows at an origin takes the pooled fit over all families
(one intercept and slope; A the pooled mean); with fewer than 20 pooled,
the row is not scored.

**Split.** Design years are targets up to 2002, fitted, never scored.
Test years are targets 2003 on, as far as the data reach.

**Metric.** Skill against X = 1 - SSE_M / SSE_X over the test rows, X in
{B, A, P}. Its interval: 2,000 resamples of the test rows' countries with
replacement, a fresh default_rng(20261028), the 5% and 95% quantiles.

**Pass.** All three lower ends above 0 (an intersection-union test, no
multiplicity correction). Beating B alone does not pass: on yearly growth
the mean beats persistence, so a model with no information beats B. If
M beats A and not P, the gain is the period's or the panel's, not the
country's investment.

**Equivalence bound.** No power analysis was run. The result is called
equivalent to nothing when the upper end against A is below +0.02.

**Sign prior.** b > 0: more investment completing, faster capacity growth.

**Secondary (reported, not primary).** S1: x = investment/financed-
completion-rate-<i> (credit plus retained profits), the same design on its
own paired rows (the placebo map unchanged), with its IU p-value (the
largest of its three bootstrap shares of skill at most 0).

**For the record (not tests).** Skill by family; b by family on all
paired rows with country-clustered standard errors; skill by era
(2003-2007, 2008-2014, 2015 on); M against a zero-growth forecast.

**Not changed after the outcome is read:** the targets, the clip, the
predictor and its priors, the models, the fallback, the split, the seed,
the metric, the interval, the pass rule and the bound.

**Results, 28 October 2026** (scripts/capacity-test.py, data/derived/
capacity/test.txt; 3,149 test rows in 27 countries, targets 2003-2022,
2,000 draws). The placebo map covers the 27 KLEMS countries with
completion rates.

The primary **fails and is equivalent to nothing**. Investment already
made, completing through the priors, does not forecast next year's
capacity growth beyond the growth's own mean.

| comparison | skill | 90% interval |
|---|---|---|
| M vs B (persistence) | +0.526 | [+0.502, +0.549] |
| M vs A (mean) | -0.008 | [-0.014, -0.002] |
| M vs P (placebo) | -0.010 | [-0.018, -0.003] |

M beats persistence by half its error, as registered it would without
information: yearly capacity and output growth reverts, so last year's
growth is a poor forecast and any mean beats it. Against the mean and the
placebo M loses, both intervals below 0. The upper end against A,
-0.002, is below the +0.02 bound.

*Bug found after the first run, fixed, rerun.* UNSD codes France as 251
(with Monaco) and Italy as 382 (with San Marino), which the M49 map
dropped, so France had no power or refining rows and Italy none. With
them (the table above) the numbers barely move: as first run, 3,069 test
rows, M vs A -0.008 [-0.014, -0.003], M vs P -0.010 [-0.018, -0.003].

Secondary S1 (financed completion rate, 3,007 test rows in 25 countries):
M vs B +0.528 [+0.501, +0.552], M vs A -0.006 [-0.011, -0.002], M vs P
+0.009 [-0.016, +0.038]; IU p 0.998, equivalent to nothing.

For the record (not tests):
- By family (primary): power 448 test rows, M vs A -0.026, b +0.016
  (clustered SE 0.104); refining 233, M vs A -0.024, b +0.064 (0.054);
  stages 2,468, M vs A -0.008, b +0.274 (0.191). Every slope has the
  prior's sign and none is distinguishable from 0.
- By era (M vs A): 2003-2007 -0.026 [-0.058, -0.000]; 2008-2014 -0.006
  [-0.012, +0.000]; 2015-2022 -0.002 [-0.006, +0.002].
- M against a zero-growth forecast: -0.012.
- S1's refining rows lose badly to the mean (-0.20) when the pooled
  slope meets Germany's financed completion rate for refining (0.25-0.35
  of capital; its financed rate was 0.22-0.57 a year in 2014-2020 against
  a KLEMS investment rate of 0.13-0.21): splitting retained profits by
  operating surplus over-finances high-margin industries.

*What this says.* At the yearly step and at these priors, an industry's
investment rate (or its financed rate) carries no information about the
next year's capacity growth of its plants beyond the mean, in the EU,
the UK, the US and Japan, 2003-2022. Capacity growth of a country's power
system, refineries and smelters is lumpy (one plant is a large share) and
the industry's investment is spread over many plants, most of them not
these. The completion rates stay in the engine as the accounting link
from investment to capacity; nothing reads them as a forecast. Learning
the time to build from the data (principle 2) and reading capacity at a
coarser step (half-decade) are the next designs, each to be registered
before it is scored.

*Review, 28 October 2026 (after the result; nothing registered is
changed, and the primary and S1 stand as reported).*
- Known years. The completion rate for year y is built from KLEMS data
  year y - 1 at its +2 and emitted at y + 1; every engine row is emitted at
  its known year and the declarations validate. No leak. UNSD, though, is
  not +2 for these countries: in the October 2026 download no KLEMS
  country has 2024 power capacity and 2 of 22 have 2024 refining, so it is
  +3. The registered +2 gives persistence g[n, y-1] and the newest
  training year a year early for power and refining. Power and refining
  are 0.7% of the test's error (below), so the pooled skills cannot move
  by more than about 0.007.
- What the test measures. Against the mean, the stage rows carry 99.3% of
  the pooled squared error (mines 71%, metal refineries 17%, smelters
  11%), power 0.4% and refining 0.3%. The 81 rows clipped at +-1 (2.6%, all
  stages) carry 44% of it, the 201 with |g| >= 0.5 carry 72%. The pooled
  skill is the skill on stage output, not on capacity: had power's
  completion rates moved its capacity one for one (b = 1), power's own
  skill against the mean would be about +0.32, and the pooled skill would
  gain about 0.001. The family slopes are the test of capacity proper, and
  they reject one for one: power b +0.016 (SE 0.104), refining +0.064
  (0.054), both nine or more standard errors below 1. Half of refining's
  growth rows are 0 (capacity reported unchanged).
- The financed rate. Splitting B8G by operating surplus is the model, not
  a bug: it is where retained profits are earned, and an industry that
  earns more than it invests (German refining, surplus about its whole
  capital stock a year) lends the rest, which the credit-share split of
  -B9 does not see. Splitting by capital or last year's investment would
  make S1 the KLEMS rate again. The measure is funds available, and by
  industry it agrees with investment only loosely (correlation 0.45; F
  and G at 1.7 times, since surplus includes mixed income).
- The priors. The C19 prior's two greenfield examples were one project,
  Eider Rock, planned and never built (now cited as such). The GEM
  Little's-law times read long because the pipelines grew (the script's
  note had the direction backwards); adjusted for growth they are about
  2.6 years for all power, against the D-E prior's mean of 4.2. Learning
  the time to build from the data, the next design, should start there.

### 9f. The SFC core at three and five years (bnm-cvs)

Registered on 28 October 2026, before any of its outcomes was computed.
One-year net lending is near a random walk (2a), so this asks whether the
accounting helps where it should: over several years, and given an
observed shock. The model is as of commit fbab3d0 (the phase B fixes of
6e); nothing in it is chosen on this test. The financial sector's missing
channels (2a, the US 2009 miss) are not added: they would have had to be
built and tested on the design origins before this registration, and are
not.

**Rows.** tools/bnm/sfc_horizon_run.clj (mask):
- countries with eight or more years of measured stocks (Table 720) and
  flows: the 32 of phase B's check. Left out by rule, a country with a
  measured year whose nominal output is over twice the year before's: the
  core's trend growth extrapolates a hyperinflation and its ensemble's
  output runs away (6e; bead bnm-rr7). That is BGR (1997, x8.7) and ROU
  (1997, x2.24). 30 countries are left. The results with them are
  reported as a sensitivity, not scored;
- origins o: measured years at least five after the country's first
  (the ensemble has five years of analyses behind it). The forecast is
  made at engine year o + 2, when o's data are known (the design rules'
  origin <= to - 2: nothing later than o is read);
- horizons h = 3 and 5: the target is data year o + h, one and three
  years after the forecast is made;
- outcomes: net lending over GDP of the four domestic sectors
  (households, NFCs, financial corporations, government; Table 14 B9),
  and private debt over GDP (households' and NFCs' F3 + F4 liabilities,
  Table 720, measured only). A row needs the target and the origin's own
  value in the data (latest vintage);
- split: design origins up to 2002, test origins from 2003. Only the test
  origins are scored for the verdicts. Test rows: net lending 2,100 (h 3)
  and 1,860 (h 5), private debt 526 and 466; 30 countries, 525 origins.
  Design rows: 388 + 388 and 97 + 97, in 24 countries.

**Models.** At each row:
- *the ensemble* (sfc-analysis/free-runs): 32 members from the country's
  first measured year, analysed every year through engine year o + 2
  (6e), then free to o + 5; the members' median. It is run on seeds 1, 2
  and 3, and its skill is the mean of the three seeds' skills;
- *the restart* (sfc-analysis/restart), the main comparator: phase A's
  free run, one member, parameters learned at o on the panel as known at
  o + 2, started from o's data;
- *persistence*: o's value;
- *damped reversion*: o's value moving 0.2 a year towards its mean over
  o - 4 to o (three or more years of them), m + 0.8^h (x_o - m); 2a's
  ceiling;
- *conditional* (secondary): the ensemble and the restart given the
  observed path of government consumption, investment and benefits and of
  NFC investment, each as a share of output, for the data years after the
  forecast is made (the restart o + 1 to o + 5; the ensemble's free run
  from o + 3, its years to o + 2 being analysed). These are realised
  values, so the conditional runs are not forecasts: they ask whether the
  accounts turn a known spending and investment path into the sectors'
  balances and debt better than the unconditional run and persistence.
A row is scored only where every model gives a value (paired).

**Metric.** Skill of model M against X = 1 - SSE_M / SSE_X, pooled over
the test rows of an outcome (net lending's four sectors together), both
horizons pooled for the verdicts and each horizon reported. The
ensemble's skill is the mean of its three seeds' skills. Its 90%
two-sided interval is the percentile interval of 2,000 draws of
countries with replacement (seed 20261028), the seed mean taken in each
draw.

**Verdicts.** For a comparison over both outcomes, each with its
interval [lo, hi] and its bound b:
- *pass*: lo > 0 for both outcomes (an intersection-union test, so no
  multiplicity correction);
- *worse*: hi < 0 for either;
- *equivalent to nothing*: not pass or worse, and hi < b for both;
- *inconclusive*: otherwise.

There are two verdicts.
1. *Adoption*: the ensemble against the restart. The ensemble is adopted
   for the coupled model's SFC forecasts only on a pass. Otherwise the
   restart is the SFC forecast and the smoother stays a nowcast.
2. *The core at three to five years*: the adopted one (the ensemble on a
   pass of 1, else the restart) against persistence and against damped
   reversion. Pass if it passes against both, worse if worse against
   either, equivalent if equivalent against both, else inconclusive.

The conditional comparisons are reported, with no verdict.

**Equivalence bounds and power** (sfc-horizon-run power, on the test
rows above; 300 simulations, 500 bootstrap draws each). The comparator's
errors are simulated per country and outcome series with a country scale
(lognormal, sd 0.5) and an AR(1) over origins (rho 0.8: consecutive
origins share most of their five-year paths); the model's errors are
correlated 0.7 with the comparator's and scaled to a true skill s. The
probability that lo > 0:

| true skill | 0 | 0.05 | 0.10 | 0.15 | 0.20 | 0.25 | 0.30 |
|---|---|---|---|---|---|---|---|
| net lending (3,960 rows) | 0.04 | 0.24 | 0.53 | 0.83 | 0.98 | 0.99 | 1.00 |
| private debt (992 rows) | 0.12 | 0.19 | 0.26 | 0.42 | 0.63 | 0.76 | 0.90 |

So the bounds, the skill found four times in five, are b = 0.15 for net
lending and b = 0.27 for private debt. The test is weak: below a skill of
about 0.1 on net lending it rarely passes, and on debt (one series per
country, 30 countries) it needs a quarter of the comparator's squared
error removed. Its bootstrap is also loose at zero skill: 12% false
passes on debt, against the nominal 5%, so a debt pass near its lower end
is weak evidence. The assumptions (rho, the correlation, the country
scale) are not fitted to any outcome.

**What would change the plan.** A pass of 1 makes the ensemble the SFC
forecast and the restart its check. A fail of 2 under both models says
the frame's accounting adds nothing at three to five years over holding
or reverting the ratios, which is the one-year result again, and the SFC
core stays the coupled model's bookkeeping, not a forecaster.

**Run.**

    jolt -A:tools -M -m bnm.sfc-horizon-run test --seeds 1,2,3 --out data/derived/coupled/horizon-test.edn
    jolt -A:tools -M -m bnm.sfc-horizon-run score --in data/derived/coupled/horizon-test.edn \
         --seeds 1,2,3 --bound-nl 0.15 --bound-debt 0.27 --out data/derived/coupled/horizon-test.txt

and with --all true for the sensitivity with BGR and ROU.

**Result** (run once, 28 October 2026, on f7111ad;
data/derived/coupled/horizon-test.txt). 5,922 test rows, every model
present at each. Skill [90% interval], both horizons pooled:

| test origins | net lending | private debt |
|---|---|---|
| ensemble vs restart | -0.47 [-1.61, +0.14] | -0.06 [-0.17, +0.04] |
| ensemble vs persistence | -3.13 [-7.22, -1.06] | -0.02 [-0.10, +0.07] |
| ensemble vs damped | -3.96 [-8.72, -1.48] | +0.25 [+0.19, +0.32] |
| restart vs persistence | -1.80 [-3.56, -0.80] | +0.04 [-0.03, +0.09] |
| restart vs damped | -2.36 [-4.39, -1.20] | +0.29 [+0.24, +0.33] |
| conditional ensemble vs ensemble | -0.09 [-0.40, +0.22] | -0.10 [-0.25, -0.01] |
| conditional restart vs restart | +0.26 [+0.15, +0.34] | -0.13 [-0.21, -0.04] |
| conditional restart vs persistence | -1.09 [-2.18, -0.44] | -0.08 [-0.22, +0.02] |

- *Adoption: equivalent to nothing.* Neither interval is above 0, and
  both upper ends are under their bounds (0.14 < 0.15, 0.04 < 0.27). The
  ensemble is not adopted: the restart is the SFC forecast, the smoother
  a nowcast.
- *The core at three to five years: worse.* The restart is worse than
  persistence on net lending, interval below 0, so it fails against both
  baselines whatever debt does. On debt it is level with persistence
  (+0.04) and beats damped reversion (+0.29), which reverts debt ratios
  that trend.
- Each horizon alone gives the same signs (horizon-test.txt).
- Conditional (no verdict): given the observed government and investment
  path, the restart's net lending improves by a quarter of its squared
  error (+0.26), and is still far worse than persistence (-1.09); its
  debt gets worse (-0.13). The ensemble gains nothing from the path.
- Sensitivity with BGR and ROU (horizon-test-all.txt): the ensemble
  against the restart on net lending is -201 [-578, +0.01], their runaway
  (bnm-rr7); the restart's verdict is unchanged (-1.86 [-3.37, -0.91]
  against persistence).

Diagnostics, after the result: the restart is worse than persistence in
every sector's net lending, least in households (-0.32 [-0.93, +0.11]),
then government (-1.36), NFCs (-2.01) and the financial sector (-5.41;
its RMSE 0.054 against persistence's 0.021, the residual margin of 2a).
It is still worse without the three countries with the largest squared
errors (NZL, POL, ESP: -0.89 [-1.78, -0.39]). Over three to five years
the core's rules drift further from the data than holding the ratios
does, as they did one year ahead, and the accounting does not pull them
back. The SFC core stays the coupled model's bookkeeping, not a
forecaster of the sector balances.

*Review* (October 2026; the registered specification is unchanged).
- Causality holds. Observations of a data year enter only at its
  publication year (fiscal shares and output at y + 2), :fiscal-sd is
  relearned from the panel as known, and the rescaled emits are read
  only at lag 1. A new test changes the ratios, not only the scale, of
  everything published after the origin, a late-published year among
  them. It covers free-run, the branched free runs and the restart, and
  nothing moves. It fails when the known-panel filter or the
  observations' publication rule is broken on purpose.
- Corrections to the text above: 5,922 is every row (test 4,952,
  design 970), not the test rows, and the test has 526 origins, not 525.
  Not every horizon has the pooled sign: ensemble vs persistence on debt
  is +0.04 at h 3 and -0.05 at h 5. Before the registration, 6e's review
  had scored the ensemble, the restart and persistence on net lending at
  one origin per country (its last data year less five). Those origins
  hold 290 of the 4,952 test rows. The hyperinflation rule was written
  after that review saw BGR's ensemble run away. Its threshold sits in a
  wide gap (ROU 2.24, then IRL 1.36), and the sensitivity leaves both
  verdicts unchanged.
- Debt, as registered: there is no verdict per outcome. The core's
  verdict is worse, from net lending alone. Debt against persistence,
  +0.04 [-0.03, +0.09], is not a pass. Its upper end is under its bound,
  but the bootstrap's 12% false-pass rate means the interval is too
  narrow. Debt against damped reversion, +0.29 [+0.24, +0.33], clears 0
  by far more than that miscalibration. It is a secondary reading, not
  a registered claim, and it is against a baseline that reverts ratios
  that trend. No claim that the core forecasts debt is allowed.
- A seeding bug, fixed after the result was seen: free-run and
  free-runs seeded the analysis generator at (seed-of seed 2), which is
  member 2's own seed, so the two drew the same numbers. Rerun with the
  fix (data/derived/coupled/horizon-test-fix.txt, -fix-all.txt):
  ensemble vs restart is -3.61 [-9.25, -0.18] on net lending and -0.76
  [-1.62, -0.02] on debt. *Adoption: worse* (it was equivalent). The
  restart, persistence and damped reversion draw no random numbers, so
  their rows are identical and *the core is still worse*. Both runs
  give the same decision: the restart stays the SFC forecast.
- What moved the ensemble is its tails, not the bug. Under some seeds
  the assimilated pass diverges in every member: on USA under seed 3
  from 1981, the output rescale hits its caps (0.2, then 5), the wage
  share sits at its floor, the innovations reach 1e2-1e4 sd units, and
  the median government net lending reaches 60 times output. LUX, LVA
  and HUN diverge under other seeds, and LUX under seed 2 before the
  fix. A few such runs set the pooled skill, so the original -0.47 was
  partly a draw. 6e's median range over seeds of 0.004 is at one origin
  per country and misses these tails. Bead bnm-tvk.
- *Divergence guard* (bnm-tvk stays parked: the restart is the SFC
  forecast, the analysis is not redesigned). bnm.sfc-analysis/divergence
  reads the analysis log and marks a pass diverged at the first analysis
  where any of these holds:
  - over a fifth of the members have their output rescale at a cap (0.2
    or 5) or undefined;
  - the mean |innovation| before the update is over 100 sd units
    (healthy passes stay under about 50);
  - over half the members have the analysed wage share at or beyond its
    bounds (0.05, 0.95) in two consecutive analyses.

  It marks and does not stop. A horizon test runs hundreds of origins
  per country in one pass, so an error would lose the other countries'
  runs, and a fork made before the divergence is still good. free-run
  and each origin of free-runs carry :diverged (its year, its reasons,
  its log entry) when an analysis up to that origin tripped. Their
  medians are kept for diagnosis, but sfc-horizon-run/path-at gives no
  value for a diverged run. The ensemble seed then has no forecast at
  that row, and because scoring is paired the row is dropped for every
  model. The report counts these rows and names the country, seeds and
  first origin. The restart and the baselines run no analysis and are
  unchanged. On USA under seed 3 the analysed wage share is negative in
  most members from 1979, so the guard trips at 1980. The rescale hits its
  caps in 1981, and the innovations pass 100 sd units in 1983.
  A survey of full passes (every country of the test, seeds 1 to 3, 32
  members, from the first measured year to the last) finds 21 of 96
  passes trip:
  - seed 1: BGR, BRA, CZE, LVA, NZL, ROU;
  - seed 2: BGR, EST, HRV, LTU, NZL, ROU, SVK;
  - seed 3: BGR, DNK, HRV, HUN, LUX, NZL, ROU, USA.

  Some trip only after the last test origin (DNK 2027, CZE 2023, LTU
  2023). A fixed ensemble needs no trips on design origins (bnm-tvk's
  test plan). The registered results above are not rescored.

### 9g. The estimated rules at one, three and five years (bnm-4hv.14)

Registered on 29 October 2026, at commit 376b7b5, before any of this
test's outcomes was computed. The model is bnm.sfc/estimated (2a, "Rules
estimated from data"), chosen on the design origins only. 376b7b5 left
learn's docstring unterminated, so the namespace didn't load. The fix
commit after this registration closes the string and changes no code,
and the test runs on it. This note was added then, before any outcome.

**What has been seen.** The 2003+ test origins are not fresh. 9f scored
them at three and five years under Keen's rules: the restart, persistence
and damped reversion, by sector. The one-year validation (2a) scored
them under Keen's and the revised rules. So persistence's, damped
reversion's and the current restart's errors on those rows are known.
9f's diagnostics (the restart worst in the financial sector) were read
before this work began. The rules were then chosen on the design origins
alone, but that knowledge pointed the search. A result on the test
origins is therefore secondary: seen twice, with the search informed by
it. The primary is a holdout no earlier test touched.

**The holdout.** Japan and Iceland. Table 14 lacks one domestic sector's
item for them: Japan's NFC value added and compensation, Iceland's
government compensation and interest paid. The 2a panel therefore had
no complete year for either, and every earlier SFC test, one-year
included, left them out. scripts/sfc-data.py holdout fills an item
missing in exactly one domestic sector as the total economy's (S1) less
the other three, from the same table. It writes
data/derived/sfc/panel-holdout.csv, so panel.csv is unchanged. Stocks are
Table 720's, measured: Japan 2005-2024, Iceland 2003-2024, 20 and 22
years. Nothing of theirs was looked at but this coverage and the accounting
check (net lending sums to zero: median 0.0003 of GDP in Japan, 0 in
Iceland). Australia, Canada, Korea, Israel, Turkey and Russia still lack
more than one sector's items, or Table 720, and stay out. No other open
source on disk fills them.

**Rows** (bnm.sfc-rules-run mask, the merged panel):
- as 9f: every country with eight or more measured years, origins its
  measured years at least five after its first, the target and the
  origin's own value in the data (latest vintage). Outcomes are the four
  domestic sectors' net lending over GDP and private debt over GDP
  (measured);
- horizons h = 1, 3 and 5: the target is data year o + h, the forecast
  made at o + 2 (one year back, and one and three years ahead);
- splits: holdout (JPN, ISL, every origin; the primary), test (the
  other countries' origins from 2003; secondary), design (to 2002;
  in-sample, reported). Rows:
  - holdout: 150, 130 and 110 at h 1, 3 and 5, from 30 origins;
  - test: 2,926, 2,626 and 2,326, in 30 countries, 586 origins;
  - design: 485 at each horizon, in 24 countries;
- Bulgaria and Romania: 9f's hyperinflation rule (a measured year with
  nominal output over twice the year before's) leaves them out of every
  split; the result with them (--all true) is a sensitivity, not scored.
  The holdout has no such year.

**Models**, each scored only where all four give a value (paired):
- *estimated*: the restart under bnm.sfc/estimated
  (bnm.sfc-analysis/restart through the engine). It is learned at o on
  the panel as known at o + 2, the pooled slopes refitted on every
  country's data years to o (holdout countries' own years included), and
  started from o's data;
- *persistence*: o's value;
- *damped reversion*: 9f's, m + 0.8^h (x_o - m), m the mean of o - 4 to o;
- *the current restart*: 9f's, learn's defaults (Keen's rules).

**Metric.** Skill of estimated against baseline X = 1 - SSE / SSE_X,
pooled over the rows of an outcome (net lending's four sectors
together), in two horizon groups: h1, and h3 with h5 pooled. Intervals
are 90%:
- *test and design*: 2,000 draws of countries with replacement (seed
  20261029, 9f's bootstrap);
- *holdout*: a bootstrap over two countries, or over blocks of their
  origins, isn't calibrated. In simulation the block bootstrap passed a
  true skill of 0 a quarter of the time. The holdout's interval is
  therefore [p - q95, p - q05], where q05 and q95 come from the
  distribution of the skill on rows of the holdout's shape when the true
  skill is 0. That distribution is simulated under 9f's error model (an
  AR(1) over origins with rho 0.8, a lognormal country scale with sd 0.5,
  the model's errors correlated 0.7 with the baseline's; 1,000
  simulations, augur.math/rng-mixed). That model is an assumption, not
  fitted to any outcome. Its false-pass rate at zero skill is 4-5%.

**Verdicts.** One cell per baseline (three) and horizon group (two),
each over both outcomes, by 9f's rule:
- pass: lo > 0 for net lending and for debt;
- worse: hi < 0 for either;
- equivalent: hi < b for both;
- else inconclusive.

A split's verdict is pass if every one of its six cells passes, worse
if any cell is worse, equivalent if all six are, else inconclusive.
- *Primary*: the holdout's verdict. A pass says the estimated rules
  forecast both outcomes better than persistence, damped reversion and
  the current restart at one year and at three to five, in countries
  never used.
- *Secondary*: the test origins' verdict, read as seen twice (above).
- Per sector and per horizon: reported, no verdict.

**Bounds and power** (bnm.sfc-rules-run power; data/derived/sfc/
rules-power.txt). The probability that lo > 0 at each true skill:

| split, group, outcome | rows | 0 | 0.1 | 0.2 | 0.3 | 0.4 | 0.5 | b |
|---|---|---|---|---|---|---|---|---|
| holdout h1 net lending | 120 | 0.04 | 0.09 | 0.22 | 0.33 | 0.53 | 0.77 | 0.50 |
| holdout h1 debt | 30 | 0.04 | 0.09 | 0.10 | 0.15 | 0.24 | 0.31 | 0.50 |
| holdout h3-5 net lending | 192 | 0.05 | 0.13 | 0.27 | 0.49 | 0.75 | 0.93 | 0.43 |
| holdout h3-5 debt | 48 | 0.05 | 0.09 | 0.13 | 0.21 | 0.29 | 0.45 | 0.50 |
| test h1 net lending | 2,340 | 0.07 | 0.47 | 0.84 | 1.00 | 0.99 | 1.00 | 0.19 |
| test h1 debt | 586 | 0.09 | 0.20 | 0.48 | 0.84 | 0.92 | 0.99 | 0.29 |
| test h3-5 net lending | 3,960 | 0.09 | 0.57 | 0.96 | 1.00 | 1.00 | 1.00 | 0.14 |
| test h3-5 debt | 992 | 0.08 | 0.25 | 0.63 | 0.91 | 0.98 | 1.00 | 0.26 |

b is the skill found four times in five, interpolated, and 0.5 (the
largest simulated) where none is.
- *The holdout is weak.* Two countries and 30 overlapping origins pass a
  net-lending skill of 0.2 one time in five, and debt hardly ever. Its
  six cells all need a pass, so it passes only on skills far above the
  design origins' (0.04 at one year, 0.2 at three to five). It can't show
  equivalence either: bounds of 0.43-0.5 are no claim of "nothing".
  Expect inconclusive unless the effect is large. That outcome says the
  holdout couldn't tell, not that the rules failed.
- *The test origins* have 9f's power (bounds 0.14 and 0.26 at three to
  five years, as 9f's 0.15 and 0.27). Their country bootstrap passes a
  zero skill 7-9% of the time, against the nominal 5%, as in 9f.

    holdout {:h1 {:nl 0.5 :debt 0.5} :h35 {:nl 0.43 :debt 0.5}}
    test    {:h1 {:nl 0.19 :debt 0.29} :h35 {:nl 0.14 :debt 0.26}}

**What would change the plan.**
- A primary pass makes bnm.sfc/estimated the SFC forecast (the restart
  run under it). It also reopens the parked ensemble (bnm-tvk), whose
  ceiling is the restart.
- A secondary pass with an inconclusive primary leaves the rules as the
  forecasting baseline, recorded as resting on origins seen twice. It
  needs fresh years (data year 2025 on, known from 2027) to confirm.
- A worse on either says the error corrections overfit the design years,
  and holding every ratio, with interest as a share, is the baseline.

**Run** (once):

    jolt -A:tools -M -m bnm.sfc-rules-run test --out data/derived/sfc/rules-test.edn
    jolt -A:tools -M -m bnm.sfc-rules-run score --in data/derived/sfc/rules-test.edn \
         --bounds '{:holdout {:h1 {:nl 0.5 :debt 0.5} :h35 {:nl 0.43 :debt 0.5}} :test {:h1 {:nl 0.19 :debt 0.29} :h35 {:nl 0.14 :debt 0.26}}}' \
         --out data/derived/sfc/rules-test.txt

and with --all true (rules-test-all.edn, .txt) for the sensitivity with
Bulgaria and Romania.

**Result** (run once, 29 October 2026, on 484670b in a clean worktree;
data/derived/sfc/rules-test.txt). Every model gave a value at all 9,723
rows. The estimated core's skill [90%] against each baseline, net
lending (four sectors) / private debt:

| split | baseline | 1 year | 3-5 years |
|---|---|---|---|
| holdout (JPN, ISL) | persistence | +0.19 [-0.19, +0.77] / -0.63 [-1.23, +0.92] | +0.20 [-0.10, +0.62] / -0.38 [-0.89, +0.69] |
| | damped reversion | +0.11 [-0.26, +0.69] / -0.39 [-0.98, +1.17] | +0.01 [-0.30, +0.43] / -0.28 [-0.78, +0.80] |
| | current restart | +0.38 [+0.01, +0.96] / -0.07 [-0.67, +1.48] | +0.39 [+0.09, +0.81] / +0.04 [-0.47, +1.11] |
| test (2003 on) | persistence | +0.040 [+0.018, +0.068] / -0.04 [-0.20, +0.18] | +0.099 [+0.044, +0.130] / +0.14 [+0.09, +0.20] |
| | damped reversion | -0.028 [-0.040, -0.011] / +0.18 [-0.04, +0.42] | -0.082 [-0.138, -0.042] / +0.37 [+0.32, +0.42] |
| | current restart | +0.41 [+0.31, +0.53] / -0.04 [-0.16, +0.15] | +0.68 [+0.51, +0.80] / +0.11 [+0.02, +0.20] |

- *Primary, the holdout: inconclusive.* No cell passes, none is worse,
  and the bounds are too wide for equivalence, as the power table said
  they would be. The points run the same way as the test origins on net
  lending: +0.2 against persistence, about 0 against damped reversion.
  On debt they go the other way, -0.4 to -0.6 against persistence, from
  30 origins in two countries. Debt is where the holdout is weakest (30
  and 48 rows; a pass at a skill of 0.5 one time in three).
- *Secondary, the test origins: worse.* Against damped reversion, net
  lending is below zero at both horizon groups (-0.03 and -0.08,
  intervals below 0). That alone makes the verdict worse. The other cells:
  - against persistence: equivalent at one year (net lending +0.04
    [+0.02, +0.07], debt -0.04 [-0.20, +0.18], both under their bounds),
    and a pass at three to five years (+0.10 and +0.14, both lower ends
    above 0);
  - against the current restart: a pass at three to five years and
    inconclusive at one year (debt).

  These origins were seen twice before (above).
- Per sector on the test origins, against persistence at three to five
  years: government +0.20 [+0.12, +0.25], NFCs +0.06 [-0.00, +0.12], the
  financial sector +0.06 [-0.19, +0.16], households +0.02 [+0.00, +0.04].
  9f's restart was -1.36, -2.01, -5.41 and -0.32 there. Holding interest
  as a share took away the financial sector's runaway, and the fiscal
  rule is the largest gain.
- With Bulgaria and Romania (rules-test-all.txt) the verdicts are the
  same: inconclusive and worse. On the test origins, net lending is +0.10
  against persistence and -0.06 [-0.11, -0.02] against damped reversion
  at three to five years.

What it means.
- The estimated rules fix the core's drift. Against 9f's restart they
  gain 0.4 to 0.7 of the squared error on net lending. Against
  persistence they pass at three to five years on both outcomes on the
  test origins, and debt (+0.14) is above 9f's +0.04.
- They don't beat damped reversion of net lending itself. Moving each
  sector's balance a fifth of the way a year to its five-year mean still
  forecasts better than the accounts with these rules (-0.08 at three to
  five years). On debt the core beats damped reversion by a wide margin,
  as 9f's did, because debt ratios trend.
- On the design origins (rules-test.txt, design split) the same gap is
  near zero (-0.01 [-0.11, +0.06] at three to five years). The rules
  were chosen where they did best against it.
- By the registration: estimated is not adopted as the SFC forecast, and
  the ensemble (bnm-tvk) stays parked. The registered reading of a
  worse ("the error corrections overfit, and holding every ratio with
  interest as a share is the baseline") does not fit what failed. The
  worse is against damped reversion, which "everything held" also lost
  to on the design origins (-0.03 and -0.12 at three and five years).
  Against persistence and the restart the error corrections gained on
  the test origins. Which forecasting baseline to keep (estimated or
  everything held) needs a decision; nothing here tested "everything
  held" out of sample.

**Review** (29 October 2026, after the result; everything below was
done with the outcomes known and changes no registered rule).

- *Design and test overlap in data years.* The split is by origin. The
  fits use data years to the origin, but the design harness scored
  origins to 2002 at o + 1, o + 3 and o + 5, so the rule sets were chosen
  partly on outcomes in data years 2003 to 2007: 2 of 828 net-lending
  rows at one year, 267 at three and 423 at five (in the registered
  mask's design split, 120, 360 and 370 of 485). 2a said "data years to
  2002", and "What has been seen" above doesn't list this. Scoring only
  the targets to 2002 (rules-design-review.txt, design --target-to
  2002) gives the same choices on net lending: interest as a share over
  the revised rates, the fiscal rule and the investment rule each over
  holding, estimated against persistence +0.04 / +0.22 / +0.30 at 1 / 3
  / 5 years. Two rejections become ties: country slopes (+0.23 / +0.30)
  and the reverting propensity (+0.21 / +0.31 at 3 / 5). The selection
  doesn't rest on the 2003-2007 targets, but its margin over damped
  reversion does shift with the period: estimated against damped is
  +0.20 / +0.16 at 3 / 5 years on the early targets, +0.14 / +0.09 on all
  of the harness's, -0.01 on the registered design split (mostly
  targets after 2002) and -0.08 on the test origins.
- *A bug, fixed after the result was seen.* bnm.sfc/init started the
  fiscal rule from the data's government B9, while the rule's norm, its
  slopes and every later step use the booked balance that closes the
  matrix. They differ by the data's discrepancy (median 0, 90th
  percentile 0.2% of GDP, largest 3.4%). Fixed (aa0ba7f) and rerun
  (rules-test-fix.txt, -fix-all.txt): test origins, net lending against
  persistence +0.041 [+0.021, +0.068] at one year (was +0.040), against
  damped -0.026 [-0.037, -0.011] and -0.081 [-0.138, -0.042] (were
  -0.028, -0.082); the holdout moves in the third decimal. *The
  verdicts are unchanged: holdout inconclusive, test origins worse.*
- *The holdout's fills* are not circular. The filled items are Japan's
  NFC value added and compensation and Iceland's government
  compensation and interest paid, all inputs. Net lending, the outcome,
  is the reported B9 in every sector (the sectors' sum differs from S1's
  by up to 0.3% of GDP, which it would not if filled). The total is in
  the same table, published at the same time, so nothing is read early.
- *The holdout's interval.* The block bootstrap's false-pass rate at
  zero skill under 9f's error model is 21-29% (200 simulations, blocks of
  3 and 5 origins), as registered. The null-spread interval reproduces
  (q95 0.39 against the registered 0.38 for one-year net lending), but
  9f's error model doesn't match the test origins' errors: the model's
  errors correlate 0.85-0.96 with the baselines' (assumed 0.7), one-year
  errors barely persist across origins (lag-one correlation -0.1 to
  0.45; assumed 0.8), three- and five-year errors from one origin
  correlate 0.5-0.9 (assumed independent), and the sectors' errors correlate up to
  -0.6 (household with government). Refitted to those (reported only;
  scripts/sfc-rules-null.py, not a registered rule), the registered interval
  is conservative in seven of eight cells (false-pass 0-1.4%) and
  anti-conservative for debt against damped reversion at three to five
  years (11%). Under the refitted model the holdout's one-year debt
  against persistence, -0.63, would sit below its null's 5% point
  (interval [-0.84, -0.36]): the holdout leans against the rules on debt
  more than the registered interval shows. The upper end [p - q05] is
  only a test of zero skill; it runs above 1, which no skill can.
- *Interest as a share of output.* The identities still bind: the flows
  are booked and the stocks follow them. What goes is the link from
  stocks to interest: the implied rate (interest over the opening
  liabilities) becomes a residual. Over five years on the test origins
  (interest-drift.txt) it stays near the rate the revised rule would
  charge, median 0.97-0.98 of it, 10-90% 0.80-1.16 for households and
  NFCs and 0.65-1.31 for government, because the model's own stocks
  hardly move (households' and NFCs' liabilities over output change by
  -0.04 to +0.07 at the 10th and 90th percentiles, against -0.17 to
  +0.32 in the data). The data's implicit rates fell over the same
  years (median 0.68-0.78 of the origin's), which neither form follows.
  For forecasting the cost is small. For scenarios it is the point:
  debt-financed spending raises no interest bill, a rate shock has no
  channel (the policy-rate pass-through was not kept), the banks'
  margin is a fixed share of output, and the investment rule's
  debt-service term is a constant, because debt service is the held
  share. Scenario runs that move debt or rates need interest on the
  stocks (Keen's or revised), not estimated.
- *The verdict and the fallback.* Worse is derived correctly from the
  registered cells (damped at one year: net lending's upper end -0.011).
  The registration's consequence of a worse is unconditional: "holding
  every ratio, with interest as a share, is the baseline". Reading it as
  not fitting what failed (above) is a judgement after the outcome. As
  written, the held core is the baseline. A reported-only run of it on
  the same rows (rules-held.txt) is no better a choice:
  - on the design rows it loses to damped reversion at three to five
    years, -0.13 [-0.22, -0.06] (the harness's -0.03 and -0.12 confirmed);
  - on the test origins, net lending: against persistence +0.004
    [-0.022, +0.037] and +0.013 [-0.005, +0.037]; against damped
    reversion -0.066 [-0.081, -0.045] and -0.184 [-0.230, -0.125], twice
    the estimated core's loss; estimated against held +0.036 [+0.025,
    +0.043] and +0.087 [+0.015, +0.128]. Debt: estimated against held
    +0.003 and -0.009, intervals across 0;
  - holdout: estimated against held +0.05 / +0.09 on net lending and
    -0.11 / -0.21 on debt, intervals wide.

  So the fallback as written swaps a core that loses to damped
  reversion for one that loses to it by more and gains nothing on debt.
  That is for the bead's decision, recorded here, not a rescoring.
- *Smaller points.* The pooled fits include Bulgaria and Romania, which
  the hyperinflation rule keeps out of every score (ratios to output,
  so it matters little). The norms are frozen at the origin over the
  horizon, while the fits update them each year (half-life 10). The
  audit's "the rule" column describes learn's defaults, not the
  estimated rules' (now said in its header). Policy rates averaged
  part-years (the current year, a series' first); only full years are
  kept now (dcabc9e) and policy.csv is regenerated (2,406 rows, was
  2,501). Only the policy pass-through candidate read it, and it was not
  kept; its design row is not rerun. data/raw/slow/bis had no
  SOURCE.txt; one now records the files, their hashes and the
  publication lag.
- *Tests written after the rules.* Added: the error-correction rows'
  gap definitions and signs against a series built with known slopes
  (the step's convention), the order learn takes the pooled slopes in,
  the country norms (booked balance, spending, investment, profit share,
  debt service, half-life 10), the shrunk slopes without :pool
  :complete, that the pooled refit reads no other country's years after
  the origin, and the booked start of the fiscal gap (failed before
  aa0ba7f).

**Decision** (29 October 2026, by the user, after the result (829ff1b)
and the review above; made with every outcome known).
- *Net-lending forecasts: damped reversion.* The registered baseline,
  each sector's balance moving 0.2 a year towards its mean over o - 4 to
  o, is the net-lending forecast. It beats every structural version of
  the core on the test origins: estimated by 0.026 at one year and 0.081
  at three to five (rules-test-fix.txt), everything held by 0.066 and
  0.184 (rules-held.txt).
- *bnm.sfc/estimated: the best structural forecaster, not adopted.* On
  the test origins it beats persistence (net lending +0.041 at one year,
  +0.099 at three to five; debt +0.14 at three to five) and the
  everything-held core (+0.087 at three to five). It is recorded as the
  best of the structural cores and is not the forecast. It is not for
  scenarios: holding interest as a share of output removes the interest
  cost of debt, the rate-shock channel and the investment rule's
  debt-service term (review, "Interest as a share of output").
- *A departure from the registration.* The registered consequence of a
  worse was "holding every ratio, with interest as a share, is the
  baseline". That is not followed. The everything-held core is strictly
  worse than estimated on net lending (against damped reversion -0.184
  against -0.081 at three to five years), ties it on debt, and neither
  is used for scenarios, so making it the baseline buys nothing. This is
  a decision made after the outcome, not a registered reading.
- *Scenario runs keep Keen's rules.* learn's defaults are still Keen's
  rules (bnm.sfc/learn with no options: the Phillips curve, kappa(pi),
  Godley-Lavoie consumption, interest at implicit rates on the stocks),
  and the engine run (bnm.sfc-analysis) uses them when given no options.
  They stay the rules for scenario runs, because they at least carry
  interest on the stocks, until bnm-4hv.15 (policy-rate pass-through
  with an explicit bank margin) gives the core a rate channel. No code
  default changes with this decision.

### 9h. The rate channel at one, three and five years (bnm-4hv.15)

Registered on 29 October 2026, at commit bbf2c5c, before any of this
test's outcomes was computed. The model is bnm.sfc/rates (2a, "Interest
on the stocks, with a rate channel"), chosen on the design origins with
targets to 2002 only. The claim is narrow: interest on the stocks with a
rate channel forecasts no worse than estimated, so the core that has the
channel can be used for scenarios without giving up forecast skill. It
is not a claim to beat damped reversion, which stays the net-lending
forecast (9g, Decision).

**What has been seen.**
- The 2003+ test origins have been seen three times: by 9f (three and
  five years), the one-year validation (2a) and 9g (with the review's
  reruns). The rate forms were scored on targets to 2002 only, but the
  search knew 9g's results (government the largest gain of the fiscal
  rule, the financial sector's drift under rate x stock). A result there
  is secondary.
- Japan and Iceland were 9g's holdout and are seen once. They are in no
  split of this test.
- Korea has never been in an SFC panel. Table 14 lacks one core item for
  it, the rest of the world's B9, which is minus the total economy's B9
  by the accounts' identity. scripts/sfc-data.py holdout2 fills it so
  (fill_row_from_total) and writes data/derived/sfc/panel-holdout2.csv;
  panel.csv and panel-holdout.csv are unchanged. Nothing else is filled
  in Korea's years used here (2008-2024): every domestic sector's items,
  net lending included, are as published. Only the coverage was looked
  at: flows 2000-2024, Table 720 stocks 2008-2025, measured years
  2008-2024, no year of doubled nominal output. 9g said Korea lacked more
  than one sector's items; it lacked only that one. Turkey would need
  government consumption as the total less households' and would then
  fall to the hyperinflation rule (nominal output 2.06 times 2021's in
  2022), so it is not built. Australia, Canada, Israel and Russia still
  lack several items, and no open source on disk fills them.
- So the primary is Korea: one country, untouched.

**Rows** (bnm.sfc-rules-run mask on panel.csv with panel-holdout2.csv;
rates-shape):
- 9g's mask: countries with eight or more measured years, origins at
  least five after the first, horizons 1, 3 and 5, the four domestic
  sectors' net lending over output and private debt (measured), the
  forecast made at o + 2;
- holdout: Korea, every origin: 56, 46 and 36 rows at h 1, 3 and 5 (net
  lending 44, 36 and 28; debt 12, 10 and 8), origins 2013-2024;
- test: the other countries' origins from 2003: 2,926, 2,626 and 2,326
  rows, 30 countries; design (to 2002): 485 at each horizon, reported;
- Bulgaria and Romania out (the hyperinflation rule), as in 9g.

**Models**, each scored only where all give a value: *rates* (the
restart, bnm.sfc-analysis/restart through the engine, under
bnm.sfc/rates, learned at o on the panel as known at o + 2); *estimated*
(the same under bnm.sfc/estimated); *persistence*; *damped reversion*
(9f's); *Keen's restart* (learn's defaults).

**Metric.** Skill of rates against X = 1 - SSE / SSE_X, pooled over an
outcome's rows (net lending's four sectors together), at h1 and at h3
with h5 pooled. Intervals are 90%:
- test and design: 2,000 draws of countries (seed 20261029);
- holdout: [p - q95, p - q05], q05 and q95 the spread of the skill when
  its true value is 0 on the holdout's rows, simulated under an error
  model fitted to the test split's errors of rates and estimated in the
  same run (scripts/sfc-rates-null.py, the review's refit made a rule):
  per outcome, the comparator's correlation across origins at each
  horizon, between an origin's three- and five-year errors, across the
  four sectors and their relative scales, the spread of country scales,
  and the correlation of rates' errors with estimated's (kappa); 2,000
  simulations from numpy SeedSequence [20261029, cell, 0]. It reads the
  test split's error structure, not its skill. The fitted parameters are
  reported with the result.

**Verdict: non-inferiority against estimated**, margin delta 0.05 of
estimated's squared error (bnm.sfc-rules-run/non-inferior). Four cells:
net lending and private debt, at h1 and at h3-5.
- non-inferior: every cell's lower end above -0.05;
- inferior: any cell's upper end below -0.05;
- else inconclusive.
- *Primary*: the holdout's verdict. *Secondary*: the test origins',
  read as seen three times.
- Reported, no verdict: against persistence, damped reversion and Keen's
  restart (test and design with intervals, the holdout's points), and
  per sector.

**Power** (rates-power.txt; the null fitted to the design rows' errors,
since the test split's are not computed before the run): kappa 0.997 for
net lending and 0.9999 for debt, so the null is narrow (net lending
q05/q95 -0.055/+0.056 at h1, -0.085/+0.078 at h3-5; debt within
+-0.02). P(lower end > -0.05) / P(upper end < -0.05) by true skill:

| holdout cell | rows | -0.10 | -0.05 | 0 | +0.05 | +0.10 |
|---|---|---|---|---|---|---|
| h1 net lending | 44 | 0.00 / 0.46 | 0.04 / 0.05 | 0.45 / 0.00 | 0.92 / 0.00 | 1.00 / 0.00 |
| h3-5 net lending | 64 | 0.01 / 0.25 | 0.05 / 0.06 | 0.26 / 0.01 | 0.69 / 0.00 | 0.94 / 0.00 |
| h1 debt | 12 | 0.00 / 1.00 | 0.07 / 0.05 | 1.00 / 0.00 | 1.00 / 0.00 | 1.00 / 0.00 |
| h3-5 debt | 18 | 0.00 / 0.99 | 0.05 / 0.05 | 0.99 / 0.00 | 1.00 / 0.00 | 1.00 / 0.00 |

- The debt cells decide almost surely: rates and estimated differ in
  debt only through output.
- The net-lending cells pass at a true skill of 0 less than half the
  time (0.45 and 0.26), all four together about one time in five. On
  the design origins the skill was -0.007 and about -0.02. Expect
  inconclusive unless rates does as well as estimated in Korea or
  better. That would say one country can't tell, not that the channel
  failed. At a true -0.10 the holdout calls inferior about half the
  time at one year.
- Its false non-inferior rate at the margin (true -0.05) is 4-7% per
  cell.

**Later confirmation** (registered now, scored later). bnm.sfc-rules-run
rates-freeze, run once with this test on the data as known today,
writes data/derived/sfc/rates-frozen.edn: each country's forecasts
(every model) from its latest measured origin (panel.csv and both
holdout panels) at horizons 1 to 5 whose target year is after its last
data year with flows. Its SHA-256 goes in the result. Scored once, on or
after 1 November 2027, against the vintage then published (data year
2025 for most countries, known 2027), from the frozen file only:
nothing is re-run, so the origins are the data as known now. The same
non-inferiority rule; intervals from 2,000 draws of countries (seed
20271101); a cell with fewer than five countries is reported, not
judged. Bead: deferred to 2027-11-01.

**What would change the plan** (proposed; the bead's decision):
- Primary non-inferior: bnm.sfc/rates is the core for scenario runs in
  place of Keen's rules. Net-lending forecasts stay damped reversion.
- Primary inconclusive, secondary non-inferior: rates can be used for
  scenario runs, stated as resting on the design origins and on origins
  seen three times; the later confirmation decides.
- Inferior on either: the anchored form overfit the design years;
  scenario runs keep Keen's rules and the channel goes back to design.

**Run** (once):

    jolt -A:tools -M -m bnm.sfc-rules-run rates-test --out data/derived/sfc/rates-test.edn
    python -I scripts/sfc-rates-null.py data/derived/sfc/rates-test.edn data/derived/sfc/rates-holdout-shape.edn \
        --split test --m rates --x estimated --observed data/derived/sfc/rates-test.edn \
        --edn-out data/derived/sfc/rates-null.edn
    jolt -A:tools -M -m bnm.sfc-rules-run rates-score --in data/derived/sfc/rates-test.edn \
        --null data/derived/sfc/rates-null.edn --out data/derived/sfc/rates-test.txt
    jolt -A:tools -M -m bnm.sfc-rules-run rates-freeze --out data/derived/sfc/rates-frozen.edn

**Result** (run once, 29 October 2026, on 6566f6d in a clean worktree;
data/derived/sfc/rates-test.edn, rates-null.txt, rates-test.txt).
Every model gave a value at all 9,471 rows. The registration commit also
made the null script read only the holdout split of --observed, before
any outcome (the text above names bbf2c5c, the code it registers). One
change after the outcome: the script wrote its EDN with numpy's float
repr, which the scorer can't read; it now writes plain floats, and the
rerun printed identical quantiles (same seeds). It is in the result
commit.

The null fitted to the test split (rates against estimated): kappa
0.9985 for net lending and 1.0000 for debt; rho 0.03, 0.57 and 0.68 at
h 1, 3 and 5 (debt 0.0, 0.65, 0.84); c35 0.57 (0.87); country-scale sd
0.36 (0.52); sector scales 0.83, 1.33, 0.61, 1.08. It is a little
narrower than the design-fitted one the power table used (h1 net lending
q05/q95 -0.036/+0.037 against -0.055/+0.056).

Skill of rates [90%], net lending (four sectors) / private debt:

| split | against | 1 year | 3-5 years |
|---|---|---|---|
| holdout (KOR) | estimated | +0.004 [-0.033, +0.040] / +0.001 [-0.008, +0.010] | +0.001 [-0.042, +0.049] / +0.007 [-0.008, +0.023] |
| test (2003 on) | estimated | +0.001 [-0.001, +0.003] / -0.001 [-0.004, +0.000] | -0.008 [-0.014, -0.002] / +0.004 [+0.001, +0.007] |
| | persistence | +0.044 [+0.023, +0.072] / -0.041 [-0.197, +0.180] | +0.101 [+0.045, +0.133] / +0.146 [+0.094, +0.200] |
| | damped reversion | -0.023 [-0.034, -0.008] / +0.174 [-0.037, +0.419] | -0.079 [-0.134, -0.041] / +0.371 [+0.320, +0.419] |
| | Keen's restart | +0.414 [+0.310, +0.527] / -0.039 [-0.164, +0.148] | +0.679 [+0.510, +0.800] / +0.111 [+0.025, +0.198] |
| design (to 2002) | estimated | -0.001 [-0.023, +0.018] / +0.001 [-0.003, +0.007] | -0.034 [-0.061, -0.011] / +0.003 [+0.001, +0.007] |

Holdout points against the others (no intervals registered): net lending
against persistence +0.027 and +0.099, against damped reversion -0.003
and -0.073, against Keen's restart +0.44 and +0.15; debt +0.08 / -0.08,
+0.33 / +0.11, +0.38 / -0.14.

- *Primary, Korea: non-inferior.* All four lower ends are above -0.05
  (the lowest -0.042). The points are at zero: rates forecasts Korea as
  well as estimated does, on a country no test had read.
- *Secondary, the test origins: non-inferior* (seen three times). Net
  lending is level at one year and 0.008 below at three to five years,
  the interval under 0 but far inside the margin. Debt is level.
- Per sector (rates-test-sectors.txt, reported): on the test origins
  government loses at three to five years (-0.027 [-0.040, -0.016]) and
  the others are within +-0.005; in Korea every sector's point is within
  -0.007 to +0.009, the financial sector's +0.03 and +0.09. Government
  is where interest on the stocks costs, as on the design origins.
- Against the baselines nothing changes from 9g: rates, like estimated,
  beats persistence at three to five years and loses to damped
  reversion of net lending (-0.08).

What it means.
- The registered consequence of a primary non-inferior: bnm.sfc/rates is
  the core for scenario runs in place of Keen's rules, and damped
  reversion stays the net-lending forecast. learn's defaults are not
  changed here (Keen's rules are still what learn gives with no
  options); a scenario run passes bnm.sfc/rates. Whether to make it the
  default is the bead's decision.
- The result rests on one country, on a non-inferiority margin of 5% of
  estimated's squared error, and on a design chosen from eleven forms.
  The power table expected one pass in five at a true skill of 0; this
  one passed with the points at 0.
- The later confirmation is frozen: rates-frozen.edn, 875 rows (35
  countries, targets 2022-2030 where each country's data stop), SHA-256
  0339880a359941334046d651966facd9d2473811fd205c77143111997eb7ccaf.
  It holds Bulgaria and Romania too; scoring leaves them out by the
  mask's hyperinflation rule, as every split here does. Bead
  bnm-4hv.16, deferred to 2027-11-01.

**Review** (29 October 2026, after the result; changes no registered
rule, the frozen file or any number above).

- *The test could hardly fail.* rates and estimated forecast almost the
  same numbers. In Korea the root mean square of their difference is
  1.4% of estimated's error RMS for net lending at one year, 2.2% at
  three to five and 0.5% for debt (test origins 3.4%, 6.0%, about 1%).
  With a difference d and comparator errors e the skill is at least
  -(2 rms(d)/rms(e) + (rms(d)/rms(e))^2), so Korea's points could not
  go below -0.03 (h1 net lending), -0.045 (h3-5) or -0.01 (debt),
  whatever the data; only the null's width could carry a lower end past
  -0.05. The margin is also as large as estimated's whole gain over
  persistence at one year (+0.04 on the test origins, +0.02 in Korea).
  Non-inferior says the channel costs the forecasts nothing; it is no
  evidence for the channel. Every forecast holds the policy rate, so
  the immediate pass-through a never acts: what is scored is interest
  on the model's stocks and the closing of the origin's gap.
- *Power.* The registered null treats the h1 and h3-5 cells as
  independent, so all four cells at a true skill of 0 pass with 0.45 x
  0.26 x 1.00 x 0.99 = 0.12, about one in eight, not one in five (0.43
  under the null fitted to the test split).
- *Design.* "Level with estimated" holds for targets to 2002. On every
  design target (origins to 2002, targets to 2007, printed in the same
  rates-design.txt) net lending loses: -0.030 [-0.049, -0.014] at three
  years and -0.019 [-0.030, -0.006] at five, as 9h's design split shows
  (-0.034 at three to five). The form without pass-through fails the
  criterion at one year as well as five (upper end -0.000) and has the
  better points (-0.000 and -0.011 against -0.007 and -0.020): an
  interval that must reach 0 favours the noisier form. Keeping
  pass-through was the bead's aim, not the data's choice. Government's
  three-to-five-year loss is there without pass-through too (-0.058,
  -0.061): it comes from interest on the stocks, not the policy rate.
- *The scenarios' parameters are not the design's.* At each country's
  latest origin (30 countries) the margin's phi is 0.17 and beta 0.057
  (medians), not 0.031 and 0.038 as on years to 2002: the margin reverts
  with a half-life of about four years and is not a random walk, and the
  +100bp margin response decays (+0.06% to +0.01% of output by year
  10). The margin starts 0.32% of output below its norm (median), so
  every base run moves that much from depositors to the banks. a and
  lambda are close to the design's (households 0.64/0.13, NFCs
  0.40/0.12, government 0.22/0.14).
- *The anchor.* Its shift persists (the target moves with it) but is
  small: the effective rate less the implicit one at the origin is, by
  median, 0.08, 0.08 and -0.05 points (households, NFCs, government),
  at most 0.90 (Brazil's government). It does not set an arbitrary
  level. With lambda above 0 the first year's rate is the anchor plus
  one step of the gap closing, not exactly the held share.
- *Stock-flow checks over 30 years* (base, +100bp and borrowed spending,
  30 countries): identities within 5.4e-14 of output; the receipts'
  floor at zero is never reached while output is positive. Ireland's
  output falls 20% by 2031 and turns negative in 2036 (estimated 2037,
  Keen's rules 2034), with NFCs' money assets negative from 2033-34 and
  government debt 17 times output in 2035. It is a fault of the core,
  not of the rate channel, but it lies inside the 10-year window and in
  the scenario medians. Luxembourg's output turns negative under
  estimated (2043) and Keen's rules (2033), not under rates. France's
  government debt passes twice output by 2046 in the base.
- *Scenario sizes.* +100bp raises government's gross interest by 0.13
  points of output in the first year and 0.60 by year 10 (median; mean
  0.15 and 0.69), the arithmetic of a debt ratio near 60-100% repricing
  over a decade. It is gross: government gets 0.07 to 0.17 points back
  through its share of the domestic receipts. Its debt is 1.05 points
  higher at year 10 and still rising (2.7 at 20, 4.7 at 30): the fiscal
  rule does not stop interest compounding.
- *Borrowed spending.* The debt is back below the base by year 10
  because the fiscal rule cuts the rule's spending against the wider
  deficit during the shock and the cut stays in the base spending grows
  from: net lending is 0.7 points above the base in year 6 and output
  below it from year 10. Estimated does the same. It is the fiscal
  rule's ratchet, not the rate channel.
- *The frozen file* reproduces at 16e7ae9 in a clean worktree (same
  SHA-256) on the untracked data/ as of today. Two of its debt rows
  have targets already published (Korea 2025, New Zealand 2024): the
  registered rule dates targets by the flows, and debt comes from the
  stocks. The scoring rule leaves other choices for 2027 (bead
  bnm-4hv.16's notes).
- *Fix.* rates-report failed on rows without data, which the frozen
  rows will have where the vintage lacks a target; it now leaves them
  out. No number above changes (rates-score reprints rates-test.txt
  byte for byte). The null script's float fix was cosmetic: the old
  writer wrapped the same digits in np.float64(...), and the quantiles
  rerun identical.

**Amendment to the later confirmation** (29 October 2026, approved by
the user the same day; committed on its own, before any target of data
year 2025 or later was looked at). It settles the choices the review
left open (bead bnm-4hv.16's notes) and replaces "Later confirmation"
above where the two differ. The frozen file is unchanged (SHA-256 as
above, checked before and after).

- *Vintage.* OECD Table 14 (DF_TABLE14) and Table 720 (DF_T720R_A), the
  full dataflows from sdmx.oecd.org with the queries in their
  SOURCE.txt, downloaded once, on the first day on or after 1 November
  2027 on which both downloads complete. That download is the vintage:
  the date, the dataflows' last-update time where the API reports one,
  and both files' SHA-256 go in data/raw/econ/vintage-2027/SOURCE.txt
  and in the result. Nothing is downloaded again; later releases and
  revisions are ignored. The scorer refuses a vintage date before
  2027-11-01. The other inputs of scripts/sfc-data.py (BIS, EWN, WDI,
  GDD, Z.1) stay as on disk; none of them enters a target.
- *:data.* Rebuilt from the vintage only: scripts/sfc-data.py, then its
  holdout and holdout2, with SFC_T14 and SFC_T720 set to the vintage's
  two files and SFC_OUT to data/derived/sfc/vintage-2027 (panel.csv,
  panel-holdout.csv, panel-holdout2.csv there), the three merged as
  rates-freeze merges them. A row's target is
  bnm.sfc-horizon-run/nl-data (net lending) or debt-data (private debt)
  at o + h on that panel.
- *A missing country or target.* A row whose target the vintage lacks is
  not scored. Nothing is filled from another source or an older vintage.
  A country with no scored row is listed as absent. Cells count
  countries after this.
- *Excluded rows.* Bulgaria and Romania, by the hyperinflation rule on
  the panels as frozen (as registered). And every row whose target was
  already published when the forecasts were frozen. All 875 rows were
  checked, for presence only, against panel.csv, panel-holdout.csv and
  panel-holdout2.csv as of 29 October 2026 (SHA-256
  e2b5b6e6f0a063d6773d4f671b49b1ff577f0732b25f053f146bebcf72dbb02e,
  8aa3de00ffaeb972c5f74ba95e18bd6bf1c6d2c26ab1a33a54f64b93bc87177b,
  4ce2eaa559d180e45bfb4fcbe93475ac800b298ce0ec74a9958add8e39fcd7e4).
  Two rows, the two the review named: Korea, origin 2024, h1, private
  debt (target 2025); New Zealand, origin 2023, h1, private debt (target
  2024). No net-lending row. The dating rule: a target is published when
  the freeze-time panels give the outcome's value in its year, net
  lending by the flows (complete flows and output that year), private
  debt by the stocks (a measured Table 720 balance sheet and output that
  year). rates-freeze dated every target by the flows, and Korea's and
  New Zealand's stocks run a year past their flows.
- *Cells.* As 9h: h1, and h3-5 (horizons 3 and 5 pooled), each for net
  lending (four sectors pooled) and private debt. Rows at h2 and h4 are
  dropped, neither scored nor reported: 9h's horizons are 1, 3 and 5.
- *Metric and baselines* (9h's). Skill of rates against estimated, 1 -
  SSE_rates / SSE_estimated, pooled over a cell's scored rows, on rows
  where every frozen model (rates, estimated, persistence, damped
  reversion, Keen's restart) has a value. Margin delta 0.05. Reported,
  no verdict: the same skill against persistence, damped reversion and
  Keen's restart, with the countries bootstrap (2,000 draws, seed
  20271101) in cells of five or more countries, the point only below.
- *Interval.* 90%, [p - q95, p - q05], q05 and q95 the quantiles of the
  skill when its true value is 0, simulated on the cell's scored rows
  (countries, horizons, outcomes; no values) by scripts/sfc-rates-null.py
  under the error model fitted to the test split of
  data/derived/sfc/rates-test.edn (SHA-256
  226f71e10ff406aa3f7e87af1231c383b2f3227ac0acdbd4c58d835aa402d52b;
  rates against estimated, the fit 9h's result reports), 2,000
  simulations, numpy SeedSequence [20271101, cell, 0]. This is 9h's
  holdout interval, in place of the countries bootstrap 9h named here.
  With one origin per country the bootstrap would draw from 5 to about
  30 countries, and under 9f's error model, at a true skill of -0.05,
  its lower end clears the margin 12% of the time at five countries, 9%
  at ten and 8% at twenty-five, against 5% nominal (1,000 simulations,
  500 draws; the upper end falls below it as often). The null-shift
  interval is calibrated under the fitted model by construction. The
  script prints P(lower end > -0.05) and P(upper end < -0.05) at true
  skills -0.2 to +0.1 on the cell's rows, reported with the result as
  the check (about 0.05 each at -0.05). The fitted model is the
  assumption, as it was for Korea. The countries bootstrap against
  estimated is printed too, reported only. The user accepted this
  change from the registered bootstrap on 29 October 2026, before any
  2025+ target was read.
- *Minimum count.* A cell with fewer than five countries with a scored
  row is inconclusive for that cell (too few countries), whatever its
  interval. From the frozen file's target years, the h1 cells should
  hold about 22 to 33 countries and the h3-5 cells about two (Brazil,
  New Zealand).
- *Verdicts.* A cell with five or more countries: non-inferior when its
  lower end is above -0.05, inferior when its upper end is below -0.05,
  else inconclusive. Overall: inferior when any cell is inferior; else
  non-inferior when both h1 cells are non-inferior and every other cell
  is non-inferior or too few; else inconclusive. The result names the
  cells the verdict rests on: with the h3-5 cells too few, a
  non-inferior covers one year only.
- *Scorer.* bnm.sfc-rates-confirm. It checks rates-frozen.edn's SHA-256
  against the one above and refuses on a mismatch, leaves out the rows
  above, and computes the cells and verdicts as written here. Tested on
  synthetic vintages only; it is not run on 2025+ data before the
  vintage date.

**Run** (once, on the vintage date; V the vintage's directory):

    shasum -a 256 data/derived/sfc/rates-frozen.edn data/derived/sfc/rates-test.edn   # as above
    SFC_T14=$V/DF_TABLE14_all.csv.gz SFC_T720=$V/DF_T720R_A_all.csv.gz SFC_OUT=data/derived/sfc/vintage-2027 \
        python -I scripts/sfc-data.py              # then the same with holdout, and with holdout2
    jolt -A:tools -M -m bnm.sfc-rates-confirm shape --vintage data/derived/sfc/vintage-2027 \
        --vintage-date YYYY-MM-DD --out data/derived/sfc/rates-confirm-shape.edn
    python -I scripts/sfc-rates-null.py data/derived/sfc/rates-test.edn data/derived/sfc/rates-confirm-shape.edn \
        --split test --m rates --x estimated --sims 2000 --seed 20271101 \
        --edn-out data/derived/sfc/rates-confirm-null.edn
    jolt -A:tools -M -m bnm.sfc-rates-confirm score --vintage data/derived/sfc/vintage-2027 \
        --vintage-date YYYY-MM-DD --shape data/derived/sfc/rates-confirm-shape.edn \
        --null data/derived/sfc/rates-confirm-null.edn --out data/derived/sfc/rates-confirm.txt

**Fix after the outcome: the import share (bnm-4hv.17, 29 October
2026).** The review's 30-year base runs found Ireland's output negative
by 2034-37 and Luxembourg's by 2033-44. The cause, found by tracing
Ireland's run year by year:
- *Imports were a share mu of absorption, and Ireland's imports exceed
  its absorption.* Much of what it imports is exported again (contract
  manufacturing, multinationals' intellectual property), so learned mu
  was 1.72 (Luxembourg 2.44 to 2.53; next highest Slovakia, 0.87). In
  Y = ((1 - mu)(a1 k0 + a2 V + G + I) + X) / (1 - (1 - mu) a1 k1), 1 - mu
  below 0 turns the multiplier on domestic demand negative: every unit
  of spending lowers output. Ireland's investment rule raised NFC
  investment towards its norm (0.13 to 0.20 of output), government
  spending grew at trend (6.6%), and output fell 2% in 2028, 7% in
  2030 and 39% in 2034, so every share of output rose and spending
  rose with it. With mu set to imports over absorption plus exports
  and nothing else changed, Ireland's and Luxembourg's runs stay
  positive under every rule set. The other suspects were not the cause:
  the norms from the distorted years (investment norm 0.20, profit share
  0.55) only move output the wrong way through the negative multiplier;
  the deposits going negative (2033) and the debt (17 times output in
  2035) followed the fall in output; the fiscal rule's cuts were
  bounded (at most half a year's spending).
- *Fix.* mu is imports over absorption plus exports (the import content
  of total final demand), below 1 while output is positive. The step is
  unchanged in form: learned x is exports net of their import content,
  (1 - mu) X, still set so X - mu A gives the origin's net exports. mu
  falls for every country (US 0.14 to 0.12, Belgium 0.81 to 0.45,
  Ireland 1.72 to 0.51), so the domestic multiplier rises everywhere.
  No country is excluded and GNI* is not needed: the fault was the
  specification, not the scale of Ireland's output.
- *A second fault the fix exposed.* With mu fixed, Lithuania under
  Keen's rules went negative in 2051: dividends received were split by
  yield-weighted equity holdings, households' and firms' holdings had
  turned negative (they are shares of total assets, negative once net
  worth is), the signed total of weights neared zero and households
  received -4.9 of output. The split is now over the total of the
  weights' absolute values: a negative holding still pays its share (as
  a short position does), no share exceeds what is paid, and nothing
  changes while every holding is positive (every data origin). Giving a
  negative holding nothing, tried first, broke the simulated panel's
  one-year test, whose own data pay on negative holdings. It touches
  only rule sets that split dividends by holdings (Keen's and revised,
  not estimated or rates). The same split was part of bnm-tvk's USA
  1981 runaway: that ensemble still trips the guard in 1980 (the wage
  share at its bound), but members at the rescale's caps in 1981 fall
  from 9 to 1 and the 1983 innovations from 109 to 37 sd units, so its
  repro test no longer expects those two criteria to trip.
- *Sweep* (35 countries with a measured year: panel.csv and both holdout
  panels; Bulgaria and Romania included; latest measured origin; rates,
  estimated and Keen's rules; 30 years). Before: five runs with output
  at or below zero (Ireland 2036 / 2037 / 2034 under rates / estimated /
  Keen; Luxembourg 2044 / 2033 under estimated / Keen). After: none, and
  no government or private debt ratio past 5 or twice its first year's.
  Ireland's government debt at 10 / 30 years is 0.00 / -0.30 under rates
  (was 3.24 / -1.46, through negative output). Still there, not
  collapses: under Keen's rules, which have no fiscal rule, government
  becomes a net creditor of up to 4.5 times output by year 30 (Germany,
  Czechia, Estonia, Poland, Slovakia, Slovenia, Lithuania, Ireland) or,
  in New Zealand, a debtor of 3.7 times, and
  households' or firms' deposits, the buffer, go negative in some years
  in 19 runs, seven of them under rates or estimated (Bulgaria, Greece,
  Mexico, Romania). Tests: sfc-test's imports-above-absorption and
  dividends-received-stay-bounded tests (synthetic) and sfc-rules-run-test's Ireland
  and all-country 30-year runs (on the data, skipped without it).
- *Reruns* (on a85c6cc, both fixes, in a clean worktree; new files,
  nothing registered overwritten):
  - rates-scenario.txt, rerun in place (reported only). The rate shock
    is about the same: government's interest +0.13% / +0.25% / +0.38% /
    +0.58% of output at years 1 / 3 / 5 / 10 (was +0.60% at 10), its debt
    +1.27% at year 10 (was +1.05%). Borrowed spending raises debt about
    half as much (rates: +0.40% / +0.72% / +0.77% / -0.36%, was +0.76% /
    +1.38% / +1.40% / -0.40%): with less leaking abroad the multiplier
    is larger and more of the spending returns as taxes. Identities
    within 5.3e-14.
  - Design (rules-design-fix17.txt, rates-design-fix17.txt). The choices
    stand: estimated is still the best rule set against persistence
    (+0.05 / +0.20 / +0.22 at 1 / 3 / 5 years, was +0.04 / +0.20 /
    +0.22) and damped reversion, and every rejected candidate still
    below it; in the rate search (targets to 2002) rates is level with
    estimated (-0.007 / -0.019 / -0.013, was -0.007 / -0.020 / -0.014),
    no interval below 0, and the ranking of forms is unchanged.
  - 9g (rules-test-fix17.txt, against rules-test-fix.txt): verdicts
    unchanged, holdout inconclusive and test origins worse. Test
    origins, net lending against persistence +0.043 / +0.099 (was +0.041
    / +0.099), against damped reversion -0.024 / -0.081 (was -0.026 /
    -0.081). One reported cell moves: test h3-5 against Keen's restart,
    pass to inconclusive (debt +0.067 [-0.018, +0.153], was +0.108
    [+0.018, +0.199]); the restart is Keen's rules, which the fix moves
    most.
  - 9h (rates-test-fix17.txt, rates-null-fix17.txt, against
    rates-test.txt): verdicts unchanged, Korea non-inferior and test
    origins non-inferior. Korea against estimated: net lending +0.004
    [-0.033, +0.040] / +0.002 [-0.041, +0.049], debt +0.002 [-0.007,
    +0.010] / +0.008 [-0.006, +0.022] (was +0.004, +0.001, +0.001,
    +0.007). Test origins: net lending +0.001 / -0.009 (was +0.001 /
    -0.008), debt -0.003 / +0.001 (was -0.001 / +0.004).
  These are fixes made after the outcomes; the registered numbers above
  stand as run.
- *The frozen file* (rates-frozen.edn) is not regenerated and its SHA-256
  is unchanged; what the fix would have changed in it is in bead
  bnm-4hv.16's notes.

**Amendment A2 to the later confirmation** (29 October 2026, decided by
the user the same day; committed on its own, before any target of data
year 2025 or later was read, the two rows already excluded aside). It
changes which frozen file is primary and nothing else.

- *Why.* rates-frozen.edn was produced by code with two faults found
  after the freeze (bnm-4hv.17, above): imports were a share of
  absorption, which goes above 1 for Ireland and Luxembourg and turns
  the domestic multiplier negative (fixed in 56249f9), and dividends
  were split over the signed total of the holdings' weights, unbounded
  once a holding turns negative (fixed in a85c6cc). Scoring only the
  pre-fix forecasts would score code the project no longer runs.
- *Primary: rates-frozen-v2.edn.* data/derived/sfc/rates-frozen-v2.edn,
  SHA-256 6fa02f4f4d827d6c038dcbd7a1d21bfa3dcd47c5869f1ddcc44f18d8cad32446,
  written by rates-freeze at 9b42691 (both fixes in) in a clean worktree
  on the same inputs as the first freeze: panel.csv, panel-holdout.csv
  and panel-holdout2.csv with the SHA-256s above, and the same
  policy.csv and keen.csv. The same inputs rerun at 16e7ae9 reproduce
  rates-frozen.edn byte for byte, and rates-freeze at a85c6cc gave the
  same 6fa02f4f... (bead notes), so the two files differ by the two
  fixes only. The cells, the overall verdict and the result's consequence
  ("What would change the plan") are v2's.
- *Reported: rates-frozen.edn* (SHA-256 0339880a..., unchanged, checked
  before and after this amendment). Scored by the same rule on the same
  vintage, its intervals printed beside v2's, with no verdict.
- *v1 against v2* (frozen values only, no target). The same 875 rows in
  the same order (35 countries, the same origins and target years, every
  field but the forecasts equal, no missing value in either). Persistence
  and damped reversion are identical. Median / max |v2 - v1|, net lending
  and debt rows: rates 0.0001 / 0.098 and 0.0010 / 0.167, estimated
  0.0001 / 0.087 and 0.0010 / 0.144 (both maxima Ireland, h5), Keen's
  restart 0.0005 / 0.93 and 0.0037 / 0.79 (New Zealand h4 net lending,
  Luxembourg h5 debt). The scored quantity, rates less estimated, moves
  by median 0.00003 and at most 0.104 (Luxembourg h5 debt); 33 rows by
  more than 0.001 (Ireland 14, Luxembourg 14, Estonia 4, Belgium 1), 20
  of them at horizons 1, 3 or 5. Every other country's gap moves by under
  0.001.
- *Excluded rows.* Unchanged: Bulgaria and Romania, and the two debt
  rows published at the freeze. The published check (scorer mode
  published, presence only) on rates-frozen-v2.edn against the
  freeze-time panels lists the same two rows, Korea origin 2024 h1 debt
  and New Zealand origin 2023 h1 debt, and no other.
- Everything else in the amendment above stands: the vintage and its
  date, :data, missing targets, the cells, metric, baselines, interval,
  null fit, minimum count and verdicts. The scorer checks both files'
  SHA-256s and refuses a mismatch on either.

**Run under A2** (once, on the vintage date; replaces the run above).
bnm.sfc-rates-confirm reads both frozen files in every mode and refuses
unless each has its SHA-256. score prints v2's cells and verdict, then
v1's cells under "v1 (rates-frozen.edn, pre-fix code): reported, no
verdict". The two files have the same rows, so they score the same rows
and share one shape and one null; score checks each file's shape
regardless (with a different one, add shape --for v1 and the null on
it, and pass --shape-v1 and --null-v1).

    shasum -a 256 data/derived/sfc/rates-frozen-v2.edn data/derived/sfc/rates-frozen.edn data/derived/sfc/rates-test.edn
    SFC_T14=$V/DF_TABLE14_all.csv.gz SFC_T720=$V/DF_T720R_A_all.csv.gz SFC_OUT=data/derived/sfc/vintage-2027 \
        python -I scripts/sfc-data.py              # then the same with holdout, and with holdout2
    jolt -A:tools -M -m bnm.sfc-rates-confirm shape --vintage data/derived/sfc/vintage-2027 \
        --vintage-date YYYY-MM-DD --out data/derived/sfc/rates-confirm-shape.edn
    python -I scripts/sfc-rates-null.py data/derived/sfc/rates-test.edn data/derived/sfc/rates-confirm-shape.edn \
        --split test --m rates --x estimated --sims 2000 --seed 20271101 \
        --edn-out data/derived/sfc/rates-confirm-null.edn
    jolt -A:tools -M -m bnm.sfc-rates-confirm score --vintage data/derived/sfc/vintage-2027 \
        --vintage-date YYYY-MM-DD --shape data/derived/sfc/rates-confirm-shape.edn \
        --null data/derived/sfc/rates-confirm-null.edn --out data/derived/sfc/rates-confirm.txt

### 9i. Running scenarios (bnm-4hv.19)

Scenarios that move rates or debt run under bnm.sfc/rates, the only rule
set with a rate channel. learn with no options still gives Keen's rules;
the entry point passes rates itself.

    jolt -A:tools -M -m bnm.sfc-rules-run run-scenario \
      [--countries DEU,FRA] [--years 10] \
      [--rate '{1 0.01}'] [--fiscal '{1 0.01 2 0.01}'] [--investment '{1 0.005}'] \
      [--spec '{:rate {1 0.01} :fiscal {1 0.01}}'] [--out FILE] [--edn FILE]

The function is bnm.sfc-rules-run/scenario. Each path is keyed by the
year after the origin (1 is o + 1). The rate path is the policy rate's
move from the origin's rate, and the last move is held. The fiscal path
is government consumption as a share of expected output, borrowed. The
investment path is NFC investment as a share of expected output. It
lowers NFCs' net lending and so their net financial worth; their
borrowing does not respond (new credit follows the debt target). Both
are added on top of the rules in the given years only, and neither
enters the base the rules move from next year. Every country in panel.csv and the two holdout panels
runs from its latest measured year, base and shocked, with the same
learned parameters (the pooled slopes are then fitted with the holdout
countries in, unlike the scenario mode's rates-scenario.txt). The output
opens with the caveats. The returned map carries them under :caveats,
with per-country flags under :flags.

The caveats, as printed:

- Response sizes come from fitted parameters, not from forecast
  validation. The registered test (9h) held the policy rate in every
  forecast, so it never exercised the immediate pass-through a.
- The bank margin reverts with a half-life of about 4 years from its
  current gap. Every base run therefore moves that gap between
  depositors and banks. The run prints each country's phi, half-life
  and gap.
- Debt-to-interest feedback is the least supported part. Government
  loses to estimated at 3-5 years (9h).
- The fiscal rule ratchets: spending cuts made during a shock persist
  in the base.
- Interest compounds over long horizons. Years beyond 10 are
  illustrative, and a longer run says so.
- Household or NFC deposits go negative in some years in some countries
  (bnm-4hv.18, open). The run lists every country and year where this
  happens.
- NFCs' gross investment below zero, debt/output above 5, and
  debt/output above 1 and more than twice its first year's: each listed
  by country and year.
- The divergence guard applies only to the parked ensemble (bnm-tvk),
  not to these restart runs.
- Countries the mask's hyperinflation rule leaves out of every scored
  split (Bulgaria, Romania) are named when they run.

The flags are computed from the paths, not from a list of countries. A
year is flagged when household or NFC money assets are below 0, when
output is not positive and finite, when NFCs' gross investment is below
0, when government, household or NFC debt is more than 5 times output,
or when such a ratio is above 1 and more than twice its first year's. A
country without a policy rate at its origin is named, because the rate
path does nothing there (Bulgaria), and so are countries the
hyperinflation rule leaves out of the scored splits (Bulgaria, Romania).
Ireland and Luxembourg are not excluded, because bnm-4hv.17 fixed their
collapse. Two runs on the data (35 countries, base and shocked) bear
this out. One is 30 years with +100bp: no output at or below 0 and no
debt above 5 times output anywhere. Households' deposits go negative in
Bulgaria (2041-52), Greece (2052-54) and Romania (2040-55), and the
margin's half-life is 4.0 years (median; gap -0.23% of output). The
other is 10 years with +100bp and three years of borrowed spending at
1% of output: no flags. Identities hold within 4.4e-14 of output. The
+100bp medians match rates-scenario.txt's closely: government interest
+0.15 / +0.27 / +0.38 / +0.59 points at years 1 / 3 / 5 / 10, and its
debt +1.2 points at 10.

Two flags were added in review, both from the 30-year run. Under +100bp
Luxembourg's output is 2.9% below its base at year 10 and 13% at 30.
The investment rule's debt-service term drives it. Luxembourg's NFC debt
target is 4.4 times output, and the term reads gross NFC interest (its
NFCs receive most of what they pay). The term is -0.165 times debt
service over its norm. As the NFC rate closes on policy plus spread,
debt service rises from 0.11 to 0.22 of output in the base and to 0.26
under the shock, so gross investment falls below zero from 2037 in the
shocked path and from 2046 in the base. Nothing floors it. The
investment flag lists those years. France's 12% fall has a different
cause. Its government debt runs from 1.1 to 2.6 times output in the base
and to 3.2 under the shock, interest is 0.18 of output by year 30, and
the fiscal rule's cuts leave spending 16% below the base's. The
debt-growth flag lists France, Finland, Mexico and New Zealand
(government, first flagged in 2043-50 under the shock). Germany's
output falls 4% by year 30 the same way, unflagged. The gross debt-service term and the missing floor
belong to the investment rule, which 9g scored. Changing them is a
model change (bead notes), not part of this entry point.

What rates is not validated for: the size of any response to a policy
rate move (a was never tested); paths for debt and government interest
as forecasts; horizons beyond the 5 years scored; the margin's response
to the policy rate (beta, fitted only); investment shocks (the
investment rule's error correction was scored, an added path was not);
countries the mask leaves out (Bulgaria and Romania, by the
hyperinflation rule) and countries without a policy rate. Net lending
forecasts are damped reversion's, not the scenario runs'.

## 10. Ranked plan

By payoff over cost, mapped onto beads bnm-4hv.2 to .8.

1. **Engine phase A: interface and golden equality** (bnm-4hv.6, first
   part; proposed as its own bead).
   - The declaration format (6a) and the coupling graph built from
     declarations, with unit, lag and cycle checks.
   - Wrap keen, credit, food, cycles, technology, tension, econ-factors,
     econ-structure, econ-divergence, migration, stages and energy as
     declared modules reading what they read now (data sources).
   - The engine must reproduce today's stock files exactly.
   - The World3 split test (section 7) checks the stepping.
   - Cheap, and it replaces the ad hoc glue before anything changes
     behaviour.
2. **The SFC core** (new bead).
   - The sector matrix per country-year from Table 14, Z.1 and Eurostat,
     the stocks from BIS, JST, GDD and WID.
   - Closure as smoke tests.
   - Keen reading d, r and investment from it.
   - It turns principle 8 into numbers for the whole economy and is the
     frame every channel books into.
   - Data on disk for 40 countries; the OECD financial balance sheets are
     a download.
3. **The control layer** (bnm-4hv.2).
   - First on disk: ICSD (with its caveat), WID public wealth and top
     wealth shares, KAOPEN, the repression gap from WDI rates, OECD public
     corporations.
   - Then downloads: V-Dem full (v2clstown), BRSS, La Porta, Fernández et
     al.
   - The latent control vector with sign priors, checked against
     Milanović and Hall-Soskice labels (not fitted to them).
4. **Allocation and capacity** (bnm-4hv.3, with a new bead for capacity).
   - The Dirichlet DLM on Müller-Verner, with the within-country test of
     5.1 registered first. This is the test with power: about 3,200
     country-years.
   - Then capacity from investment (EU KLEMS, EXIOBASE GFCF) into
     bnm.supply and bnm.stages. Built (5.2a); its registered test (9e)
     is equivalent to nothing at the yearly step.
   - The China gap needs PBoC loans by industry.
5. **Real to political** (bnm-4hv.4).
   - bnm.tension reads D1-D4, R4 and R5 from the engine.
   - The food and energy shocks enter MMP.
   - Regime type is the moderator.
   - Small, and it is where T4 found the states help.
6. **Engine phase B: coupled stepping** (bnm-4hv.6, second part).
   - Lag-0 channels live.
   - The ensemble (64 members).
   - Weakly coupled assimilation.
   - Sheaf gaps written per edge and year.
   - The F -> R -> D iteration check.
   - Strong coupling as a registered comparison.
7. **Politics to control** (bnm-4hv.5).
   - The GKS nationalisation hazard first: oil and mining, data from
     Kobrin, Minor and Hajzler (by request) or rebuilt from their
     published lists.
   - Then privatisation and controls.
   - The hazards' power is in the hundreds of events; the generic
     regime-to-ownership effects are descriptive.
8. **Hindcasts** (bnm-4hv.7). Registered before running (9b), each with
   uncoupled and placebo comparisons.
9. **The registered event test** (bnm-4hv.8). T9's criteria and the
   use-case rule (9c), after bnm-82j.1 has defined and rescored the rule.

Proposed new beads (listed, not created):
- **bnm-4hv.9: engine phase A.** The module interface, the coupling graph
  from declarations, today's modules wrapped, golden equality with today's
  stock files, and the World3 split test. Blocks .6 phase B.
- **bnm-4hv.10: the SFC core.** Five-sector transaction-flow and balance
  matrices per country-year from OECD Table 14, Z.1, Eurostat, BIS, JST,
  GDD and WID; closure smoke tests; Keen reading from it. Blocks .3, .4
  and .6.
- **bnm-4hv.11: capacity from investment.** Sector capital stocks from EU
  KLEMS and EXIOBASE GFCF, investment completing into capacity through a
  delay, linked to bnm.supply's [region product] nodes and bnm.stages.
  Part of the allocation channel; blocks .7's China and US hindcasts.
- **bnm-4hv.12: register the allocation test.** Does the control vector
  move credit shares within countries (Müller-Verner, 1950-2014), against
  a swapped-country placebo? Registered on the design years before the
  test years are read. It is the epic's one test with power on its central
  claim.
- **bnm-4hv.13: downloads for the coupled model.** V-Dem full, BRSS, La
  Porta's 1970/1995 shares, the OECD financial balance sheets, the PBoC
  loans by industry and TSF, ECB loans by NACE, Fernández et al.,
  Kobrin/Minor/Hajzler, the Privatization Barometer, the OECD SOE data,
  the Laeven-Valencia 2018 xlsx and the GSDB, each with a SOURCE note as
  data/raw does.

## References not cited inline

- Fazzari, S. M., Hubbard, R. G. & Petersen, B. C. (1988). "Financing
  constraints and corporate investment." *Brookings Papers on Economic
  Activity* 1988(1): 141-206.
- Funke, M., Schularick, M. & Trebesch, C. (2016). "Going to extremes:
  politics after financial crises, 1870-2014." *European Economic Review*
  88: 227-260.
- Megginson, W. L. & Netter, J. M. (2001). "From state to market: a survey
  of empirical studies on privatization." *Journal of Economic Literature*
  39(2): 321-389.
- Grasselli, M. R. & Costa Lima, B. (2012). "An analysis of the Keen model
  for credit expansion, asset price bubbles and financial fragility."
  *Mathematics and Financial Economics* 6: 191-210.
- Keen, S. (1995). "Finance and economic breakdown: modeling Minsky's
  'financial instability hypothesis'." *Journal of Post Keynesian
  Economics* 17(4): 607-635.
- Sterman, J. D. (2000). *Business Dynamics*. McGraw-Hill.
