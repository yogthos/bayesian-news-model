# A quarterly growth-collapse outcome (feasibility)

Research only, no model change (13 November 2026). Follows LEADING.md, "Indicators for food 2020-21 and growth 2020": the growth catalogue is annual and dates each onset at January of the collapse year (tools/bnm/economic_events.clj:171-173, LEADING bug 4), so no origin inside 2020 can target the COVID collapse. Question: can open quarterly GDP data carry a sub-annual growth-collapse outcome that agrees with the annual catalogue, and how many countries would it cover?

Scripts are in the session scratchpad (`gq/load.py`, `coverage.py`, `rule.py`, `revisions.py`, `imflag.py`, `gemcheck.py`), with outputs `gq/*.txt`. Downloads, each with a SOURCE.txt:

- data/raw/imf-qnea: IMF National Economic Accounts, quarterly (IMF.STA:QNEA, the successor of IFS quarterly national accounts), real GDP SA and NSA
- data/raw/imf-qnea-vintages-2026: QNEA's January, February and April 2026 vintages (the May dataflow returned nothing)
- data/raw/oecd-qna: OECD QNA (DSD_NAMAIN1@DF_QNA), GDP, adjusted, every unit
- data/raw/oecd-stes-revisions: OECD's real-time database (DSD_STES_REVISIONS@DF_STES_REVISIONS), real GDP in every monthly edition, quarters 2006-10 and 2018-21
- data/raw/eurostat-namq-10-gdp and eurostat-naidq-10-gdp: Eurostat quarterly GDP, chain-linked volumes
- data/raw/dbnomics-imf-ifs-gdp: IMF IFS quarterly real GDP as frozen by DBnomics (August 2025)
- data/raw/dbnomics-wb-gem-gdp: World Bank Global Economic Monitor, quarterly real GDP (api.worldbank.org returned a WAF block, so it came through DBnomics)

## The annual outcome

`growth-collapses` (economic_events.clj:56-68) works on output per head: Maddison's levels, with WDI's per-capita growth chained on after Maddison ends. A year is a collapse when its log growth is negative and more than two standard deviations below the mean of the previous twenty years (at least fifteen of them), at most once in five years. Onsets are dated January of the year, busy for twelve months, and known 18 months after the onset. The scratchpad rebuild matches the catalogue exactly (171 units, 0 differ). There are 303 episodes since 1980, and 90 of the 171 units had one in 2020.

## 1. Sources

Coverage counts the 171 lattice units. An episode is covered when the source has year-on-year growth for all four quarters of the onset year.

| source | units (lattice) | median first quarter | units with data by 1980 / 1995 / 2007 | 2020 onsets covered | 1980+ episodes covered | lag | vintages | licence |
|---|---|---|---|---|---|---|---|---|
| OECD QNA | 50 (48) | 1960Q1 | 27 / 33 / 47 | 37 / 90 | 94 / 303 | 2-3 months (flash about 1) | OECD STES revisions: monthly editions since 1999, 46 areas | CC BY 4.0 |
| IMF QNEA | 109 (103) | 2000Q1 | 7 / 20 / 69 | 69 / 90 | 134 / 303 | about 4 months, 8-20 for about 20 countries | 2026 vintages only | IMF terms, free reuse with attribution |
| IMF IFS (DBnomics) | 110 (102) | 1996Q1 | 7 / 23 / 69 | 70 / 90 | 134 / 303 | frozen in 2025 (IFS discontinued) | none open | IMF terms |
| Eurostat namq_10_gdp | 37 (36) | 1995Q1 | 1 / 5 / 36 | 26 / 90 | 54 / 303 | about 2 months (flash 1) | none (no real-time archive in the API) | CC BY 4.0 |
| Eurostat naidq_10_gdp | 52 | 1995 | - | adds no lattice unit | - | - | - | CC BY 4.0 |
| WB GEM (DBnomics) | 220 (91) | 1995Q1 | 0 / 37 / 81 | 64 / 90 | 128 / 303 | the DBnomics copy ends 2024Q2 | none | CC BY 4.0 |
| **combined** (OECD, then IMF QNEA, Eurostat, IFS; GEM left out) | **104** | | 25 by 1970, 29 more in the 1990s, 16 in the 2000s | **71 / 90** | **151 / 303** | | | |

- **World Bank.** The only quarterly GDP is GEM. GEM fills gaps from national sources and the IMF, and its quarterly provenance is not documented per country, so it is left out as an outcome. It adds 9 units that no other source has (AZE, BHR, BLZ, CMR, IRN, KWT, TUN, TWN, VNM). Five of them had a 2020 onset, and most of their series stop early (AZE 2017, IRN 2018, BLZ 2015).
- **UN.** UNSD publishes annual national accounts only. There is no open quarterly series by country.
- **DBnomics.** It mirrors IMF IFS, OECD, Eurostat and WB GEM. A search turned up no national provider with quarterly GDP for the uncovered countries (GEO, TUN, PAN).
- **IMF QNEA.** The new portal has short or empty history for several countries that IFS had (AGO, BGD, CMR, DZA, KGZ, MDG, MOZ, TUN, TZA, UZB, VNM), and GEO starts in 2020Q1. That is why the IFS copy is kept as a fill.
- **ALFRED** has US vintages only, and the OECD revisions database covers the US anyway.
- **History.** Only 27 units have quarterly data by 1980. Most emerging markets start in 1995-2005, so a rule that needs fifteen years of quarterly history cannot judge most countries until about 2010-20.

2020 onsets that no quarterly source covers (19): BHR BLZ BRB BTN CMR CUB FJI GEO HTI IRQ KGZ KHM KWT LBN MDG PAN SUR SWZ TUN. Some publish quarterly GDP nationally (GEO, KGZ, TUN, PAN, KWT, BHR, BRB, BLZ, SWZ, HTI). Their histories are short, there is no open API, and each would be scraped by hand.

Combined coverage of the 1980+ episodes, by decade: 1980s 16/70, 1990s 14/54, 2000s 36/49, 2010s 12/34, 2020s 73/96.

## 2. A quarterly rule consistent with the annual one

g(q) is per-capita year-on-year growth: log(GDP_q / GDP_{q-4}) minus the year's population growth, which comes from WDI as GDP growth less per-capita growth. Sources are spliced by growth rate, never by level, and a |g| ≥ 0.7 is dropped as a level break (one rebased Jamaica series). All rules fire at most once in 20 quarters, matching the annual once-in-five-years. The rules:

- **P (pure quarterly).** g(q) < 0 and below the mean minus 2 sd of the previous 80 quarters of g (at least 60).
- **Hk (hybrid).** g(q) < 0 and below the mean minus k sd of the previous 20 years of annual per-capita growth (at least 15). This is the annual catalogue's own baseline, from the same Maddison/WDI levels, so it needs only five quarters of quarterly data. H2c and H2.5c require two quarters in a row below the line.
- **R4.** Trailing four-quarter growth (sum of the last four quarters over the four before), against the annual line with k = 2. At Q4 it equals the calendar year's growth.

Agreement with the annual catalogue (1980+). An annual onset in year y is matched when a quarterly onset falls in y-1 Q3 to y Q4. "No annual onset" counts quarterly onsets with no annual onset in y-1..y+1 inside the annual coverage.

| rule | annual onsets testable | matched (recall) | quarterly onsets | no annual onset (share) | 2008-09 matched | 2020 matched |
|---|---|---|---|---|---|---|
| P | 104 | 88 (0.85) | 126 | 37 (0.29) | 20/22 | 50/52 |
| H2 | 151 | 132 (0.87) | 222 | 84 (0.38) | 29/33 | 64/71 |
| **H2.5** | 151 | **132 (0.87)** | 177 | **42 (0.24)** | **31/33** | **64/71** |
| H2c | 151 | 129 (0.85) | 173 | 41 (0.24) | 30/33 | 62/71 |
| H2.5c | 151 | 110 (0.73) | 134 | 21 (0.16) | 31/33 | 51/71 |
| R4 | 151 | 131 (0.87) | 165 | 31 (0.19) | 31/33 | 61/71 |

Onset quarters:

| rule | 2008-10 | 2019Q4-2021 |
|---|---|---|
| H2.5 | 2008Q3 4, 2008Q4 9, 2009Q1 17, 2009Q2 5 | 2019Q4 2, 2020Q1 11, **2020Q2 59**, 2020Q3 2, 2020Q4 2 |
| H2 | 2008Q2 4, Q3 6, Q4 16, 2009Q1 16, Q2 3 | 2019Q4 2, 2020Q1 20, 2020Q2 55, Q3 3, Q4 1, 2021 2 |
| R4 | 2008Q2 2, Q4 6, 2009Q1 11, Q2 10, Q3 8, Q4 4 | 2020Q2 37, Q3 16, Q4 9, 2021Q1 1 |

H2.5's matched onsets, by quarter relative to Q1 of the annual onset year: two quarters earlier 4, one earlier 11, Q1 39, Q2 66, Q3 6, Q4 6. R4 is later: 14 at Q1, 58 at Q2, 34 at Q3, 21 at Q4.

- **Every rule agrees about as well with the annual catalogue (recall 0.85-0.87).** They differ in false onsets and in timing. H2.5 keeps H2's recall and halves its unmatched onsets. R4 has the fewest unmatched onsets but dates a quarter or two later (2020 spread over Q2-Q4), which gives back much of the point of a quarterly outcome. P can judge only the 104 testable episodes with 15 years of quarterly history, mostly OECD.
- **Most unmatched H2.5 onsets are real recessions** that the annual average smooths out. A single quarter's year-on-year fall is deeper than the year's: Europe in 1993, Turkey, Hungary and Czechia in 2009, and DNK, SWE, FIN, BRA, TUR, SRB, LUX, PRY and CHN in 2020. Some are noise in small or volatile series (MDV 2005, the tsunami; BGR 1997, a real crisis whose annual baseline was missing).
- **Misses (H2.5, 2009 and 2020).** BHS, BWA, ECU, IRL, LKA, NAM, UGA 2020; DNK, MDV 2009. Ireland's GDP is distorted by multinationals, and a few series are short or noisy.
- **2020 is dated 2020Q2 at 59 of 76 onsets in 2019Q4-2021, and 2020Q1 at 11 (China among them).** 2008-09 is dated 2008Q4-2009Q1 at 26 of 35.

## 3. Coverage and the uncovered countries

- 2020: 71 of the 90 onsets (0.79) have quarterly growth for all of 2020, and H2.5 dates 64 of them.
- 1980+: 151 of 303 episodes (0.50) are covered, against 36/49 in the 2000s and 73/96 in the 2020s. The 1980s and 1990s are mostly not, because quarterly history outside the OECD starts in 1995-2005. Since the hybrid baseline is annual, an episode is judgeable as soon as a unit has five quarters of data.
- Uncovered units (67 of 171; 19 of the 2020 onsets) keep the annual target. Most of Africa, the Caribbean, the Gulf, the small islands, Central Asia and the conflict states are among them.

## 4. Revisions and the lag for `known-at`

OECD real-time editions (43 lattice areas). The lag is months from the quarter's last month to the first monthly edition that holds the quarter: 2 months in 193 cases, 3 in 434, 4 in 113, 5 in 16, more in 2. By area the median is 2 at 10 areas, 2.5 at 2, 3 at 24, 4 at 6 (CRI, IND, IRL, LUX, NZL, ZAF) and 5 at 1 (RUS). For the IMF, the 2026 vintages show the latest quarter at 2 months after the quarter for 26 countries and at 4 months for 53. With no March vintage that bound is coarse. About 20 countries run 7 to 22 months behind. In November 2026, 52 of the 103 QNEA units had 2026Q2 as their latest quarter and 15 had 2026Q3. Two countries show a quarter in a vintage dated before the quarter ended, so the vintages' labels or contents are not exactly real time.

Year-on-year growth, first release (an edition within 6 months of the quarter) against the November 2026 edition:

| quarter | areas | mean first yoy % | mean latest yoy % | mean abs revision (pp) | max abs revision | sign flips |
|---|---|---|---|---|---|---|
| 2008Q4 | 35 | -0.9 | -1.5 | 1.15 | 3.2 | 7 |
| 2009Q1 | 35 | -3.9 | -4.6 | 1.50 | 7.0 | 1 |
| 2009Q2 | 34 | -4.4 | -4.4 | 1.01 | 7.9 | 1 |
| 2009Q3 | 35 | -3.4 | -3.1 | 0.95 | 4.3 | 1 |
| 2020Q1 | 40 | -0.6 | -0.2 | 1.00 | 5.0 | 8 |
| 2020Q2 | 40 | -12.0 | -11.3 | 1.29 | 5.6 | 1 |
| 2020Q3 | 41 | -4.0 | -3.2 | 1.04 | 3.7 | 2 |

H2.5's onset quarter, first release against latest:

- **2020Q1-Q4.** Same quarter at 33 areas, one quarter apart at 4 (BEL, CRI, ISL, ITA), no onset lost or gained, 6 without an onset either way. A collapse the size of 2020's survives revision.
- **2008Q3-2009Q4.** Same quarter at 13, a different quarter at 10 (mostly one quarter, GRC three), real time only at 3 (AUS, KOR, ZAF), latest only at 5 (CHL, CRI, EST, IND, SVN), neither at 12. Real-time data understated the first quarters of 2008-09 (2008Q4 first release -0.9% against -1.5% now), so in real time a third of the onsets move or vanish.

The revisions are about 1-1.5 points of year-on-year growth. That matters at the 2.5-sd line for moderate recessions and not for 2020-sized ones. Open vintages exist only for OECD areas. For IMF-only countries, revisions cannot be measured, only assumed similar or larger.

`known-at` should be onset quarter end + **5 months**: OECD's 2-4 plus a month to spare, the IMF's about 4. A uniform 5 means 2020Q2 onsets are known in November 2020, against July 2021 for the annual catalogue (January + 18). A per-source lag (3 for OECD areas, 5 otherwise) would gain two months for 48 units, but `known-lag` is one number per catalogue today (economic_events.clj:173, :194; benchmarks.clj:54-112).

## 5. Recommendation

**Build it, as an option alongside the annual catalogue, not in place of it.** This mirrors `--food-outcomes monthly`.

1. **Data.** A new script (scripts/quarterly-gdp.py) fetches OECD QNA (XDC, LR, T0102), IMF QNEA (B1GQ.Q, SA else NSA, XDC) and the IFS DBnomics copy as a fill for truncated QNEA histories. It splices them by year-on-year growth (OECD first, then QNEA, Eurostat, IFS), drops |g| ≥ 0.7 breaks, and writes data/derived/gdp-quarterly.csv (iso, quarter, yoy, source). Leave out GEM and naidq. Eurostat adds nothing beyond OECD and QNEA, so it is optional.
2. **Definition (H2.5).** Per-capita year-on-year growth of a quarter, below zero and below the mean minus 2.5 sd of the previous 20 years of annual per-capita growth (the annual rule's own Maddison/WDI baseline, at least 15 years), at most once in 20 quarters. Population growth is the previous year's (WDI), which is enough in real time. Date the onset at the quarter's last month, as `quarterly-food-onsets` does. Busy 12 months. Coverage runs from the unit's first judgeable quarter to its last quarter with data. Recall against the annual catalogue is 0.87 (31/33 in 2008-09, 64/71 in 2020); 24% of its onsets have no annual onset nearby, mostly real recessions the annual average hides. If a mixed catalogue must agree with the annual one at least as well, use R4, which has fewer unmatched onsets but dates a quarter or two later.
3. **Lag.** `known-lag` 5 months.
4. **How it enters.** `bnm.economic-events/with-quarterly-growth cats` sets growth_collapse to the quarterly catalogue at the 104 covered units, with the annual onsets kept at the other 67 (annual dates, :from/:through as now). It is selected by `world-run --growth-outcomes quarterly`. A mixed catalogue needs `known-lag` per unit, quarterly 5 and annual 18 (a map in place of the scalar), in benchmarks.clj, `wu/known-lag`, dev_folds and the calibration readers. The cheaper first step is a covered-units-only catalogue with scalar lag 5, scored against the annual run on the same 104 units. If it pays (the June-2020-origin ceilings in LEADING.md reach 0.21-0.29 only once the outcome is in-year), add the per-unit lag and the mixed form. Do not replace the year target outright: half the 1980+ episodes, most of the training history before 2000, and 19 of the 2020 onsets have no quarterly data.
5. **Effort.**
   - Fetch and normalize script with a test: 1 day.
   - Rule, catalogue and flag in economic_events.clj and world_run.clj, tests first, as with the food outcome: 1-1.5 days.
   - Covered-only run and comparison: 1 day.
   - Per-unit `known-lag` for the mixed catalogue: 1-2 days more, since it touches every reader of `:known-lag`.

   About 3-4 days for the covered-only version, 5-6 with the mixed catalogue.

Caveats:

- **No real-time data outside the OECD.** IMF vintages start in 2026, so the outcome for 56 IMF-only units is latest-vintage.
- **The hybrid baseline is annual,** so the rule fires more readily in volatile economies, where one quarter's year-on-year swing is larger than the year's.
- **Ireland's GDP** (and to a lesser degree LUX) is distorted by multinationals. Modified GNI* would be better but is annual only.

## Results: the covered-only step (bnm-56c.37)

Built 13 November 2026 on branch growth-q.

### What was built

- **Data.** `scripts/quarterly-gdp.py` (test: `scripts/quarterly-gdp-test.py`) reads the three downloads as they are and writes data/derived/gdp-quarterly.csv: iso, quarter, yoy, pop_year, pop_growth, yoy_pc, source. Sources are spliced by year-on-year growth in the order OECD QNA, IMF QNEA, IFS, and a |yoy| ≥ 0.7 is a level break that the next source fills. Each row records its source and the population year it used. Eurostat and GEM are not read. At the 171 lattice units there are 13,657 unit-quarters: 9,618 from OECD (48 units), 3,795 from QNEA (56 units) and 244 from IFS (6 units). The script does not fetch; the URLs are in each download's SOURCE.txt.
- **Population.** The annual catalogue's population is WDI's: GDP growth less per-capita growth. The quarter uses its own year's population growth, or the latest earlier year where that one is missing (174 rows, mostly 2026). This departs from section 5, which said the previous year's. With the previous year's, recall falls to 0.84 (127/151), 2008-09 to 30/33 and 2020 to 63/71. Most of the lost onsets are per-capita collapses caused by a population shock (Jordan 2013, Saudi Arabia 2020); the rest are marginal (SVN 2009, USA 1982, NLD 1981). The year's population growth is a WDI figure published about mid-year after. Every outcome here is a latest-vintage figure anyway (GDP is too), so this is a revision of the outcome and not a lookahead in any predictor. It is a caveat on how "real time" the target is.
- **Rule and catalogue.** `bnm.economic-events/quarterly-growth-collapses` is H2.5: per-head year-on-year growth below zero and below the mean minus 2.5 sd of the previous 20 years of annual per-head growth (at least 15), at most once in 20 quarters. The annual line comes from `per-head-levels` (Maddison, then WDI), which was split out of `-main` unchanged. `quarterly-growth-catalogue` dates each onset at the quarter's last month, marks it busy for 12 months, covers each unit from its first judgeable quarter to its last quarter with data, and sets `known-lag` to 5. A unit the rule never judges is left out.
- **Flag.** `world-run --growth-outcomes quarterly` (`bnm.world-run/growth-outcomes`, `bnm.economic-events/with-quarterly-growth`) is off by default.
- **Covered-only design.** This mirrors `--food-outcomes monthly`: the kind's catalogue is replaced outright. `lattice-political/build` builds a kind over its catalogue's units, so growth_collapse is forecast at the 103 covered units only, with their subregions, regions and world. The other 68 units drop out of the kind; they do not keep the annual target. No other kind reads growth_collapse's catalogue in the t65 command (no relations, no `--clim-prior`), and every other kind's forecasts in t67-growthq are byte-identical to t65-gauges.
- **Base for the comparison.** `--growth-outcomes annual-covered` restricts the annual catalogue to the same 103 units, so the base trains on the same countries. This is run t67-annual103.
- **104 or 103 units.** 104 lattice units have quarterly data. Kosovo's data end in 2021Q4, before its annual line (from 2009) has 15 years, so it is never judged and 103 units are covered.
- **Half-decade scale.** Because the quarterly catalogue's first covered year is 1961, `lattice-political/scales-for` drops the half-decade scale for the kind (the rule for catalogues starting in 1930 or later). The annual catalogue keeps it. t67-growthq and t67-annual103 therefore also differ in that scale.

### Tests

- Python: 8 tests. They cover the splice working by growth and not by level, a level break filled by the next source, the population year, and the three readers.
- bnm.economic-events-test:
  - the rule, including that the line reads only the years before the quarter;
  - the catalogue's form;
  - the flag swapping only this kind;
  - on the data (skipped without the CSV):
    - the annual rebuild from `per-head-levels` gives the existing catalogue exactly (171 units);
    - recall 0.87 (132/151 testable annual onsets since 1980), 2008-09 31/33, 104 units with data and 103 covered;
    - more than 70% of 2019-21 onsets in 2020Q2 (59 of 77);
    - no lookahead: onsets fall on a quarter's last month; the catalogue built from data cut at each quarter of 2008-09 has the full catalogue's onsets up to that quarter; `count-known` sees an onset at quarter end + 5 and not at + 4.
- bnm.world-run-test: the option's nil, quarterly, annual-covered and refused values.
- The full suite is green: 1370 tests.

The catalogue matches the feasibility study exactly: 196 onsets (177 since 1980), 2008-10 dated 2008Q3 4, Q4 9, 2009Q1 17, Q2 5, and 2019-21 dated 2019Q1 1, 2019Q4 2, 2020Q1 11, 2020Q2 59, Q3 2, Q4 2. The unmatched annual onsets are BHS, BWA, ECU, IRL, LKA, NAM, UGA 2020; DNK, MDV 2009; BLR 2022; DEU 1982, 1990; ESP 1981; FRA 1993; ISL 1992; NOR 2013; PAK 2019; ROU 2019; TUR 1999.

### Runs

- t67-growthq: t65-gauges' command plus `--growth-outcomes quarterly`.
- t67-annual103: t65-gauges' command plus `--growth-outcomes annual-covered`.

Commands are in each run's README.txt. Scores are in data/runs/logs/score-t67-growthq-vs-annual103.txt and score-t67-growthq-vs-t65.txt; the scoring code is data/runs/t67-growthq/score.clj. t67-annual103 scores the same as t65-gauges restricted to the 103 units: year Brier -0.0004 [-0.0013 +0.0006], quarter-lead +0.0002 [-0.0003 +0.0008]. Restricting the training units changes nothing material, so the figures below are against t67-annual103, and the ones against t65 are within 0.001 of them.

### Comparability

The two runs forecast different outcomes. The quarterly target fires more often (year rows: 266 positives against 216 on the same 1407 rows) and at different dates: in-year, rather than in January of the year. Each run is scored on its own outcome, so a Brier difference mixes forecast quality with how hard the target is. The constant-forecast Brier (rate × (1 − rate)) and the skill against it are the fairer cross-target numbers. The question this comparison can answer is timeliness: whether a quarterly-dated, faster-known outcome lets the lattice see a collapse sooner. It cannot say whether one target is "better forecast" in an absolute sense.

### Scores (103 units, country nodes, origins 2003-2022, 90% intervals resampling countries and years)

| scale | rows | run | positives | mean p | rate | Brier | constant | skill |
|---|---|---|---|---|---|---|---|---|
| year | 1407 | t67-growthq | 266 | 0.133 | 0.189 | 0.1648 | 0.1533 | -0.075 |
| year | 1407 | t67-annual103 | 216 | 0.094 | 0.154 | 0.1373 | 0.1300 | -0.057 |
| quarter-lead | 5330 | t67-growthq | 322 | 0.026 | 0.060 | 0.0580 | 0.0568 | -0.022 |
| quarter-lead | 5330 | t67-annual103 | 326 | 0.026 | 0.061 | 0.0590 | 0.0574 | -0.028 |

Paired differences (base less t67-growthq; positive means the quarterly run's loss is lower):

| scale | Brier | log score |
|---|---|---|
| year | -0.0275 [-0.0437 -0.0126], LOSS | -0.0634 [-0.1121 -0.0135], LOSS |
| quarter-lead | +0.0010 [-0.0120 +0.0164] | +0.0168 [-0.0329 +0.0759] |

Calibration (year nodes; both runs on their own outcome):

| p bin | t67-growthq n / mean p / rate | t67-annual103 n / mean p / rate |
|---|---|---|
| < 0.05 | 15 / 0.043 / 0.067 | 22 / 0.047 / 0.136 |
| 0.05-0.10 | 305 / 0.082 / 0.233 | 915 / 0.080 / 0.205 |
| 0.10-0.20 | 937 / 0.134 / 0.201 | 451 / 0.121 / 0.055 |
| 0.20-0.30 | 138 / 0.233 / 0.043 | 19 / 0.223 / 0.000 |
| 0.30-0.50 | 12 / 0.326 / 0.000 | - |

Neither year node discriminates: the rate falls as p rises in both. Quarter-lead is the same: t67-growthq has p < 0.05 at rate 0.064 and 0.05-0.10 at 0.024.

Alerts: p ≥ 0.2, a miss costs 4 false alarms, year nodes, December origins 2003-2022. `bnm.alerts` is not on origin/structure, so this table is computed the same way in score.clj.

| run | rows | positives | hit | FA | alerts | const | cost |
|---|---|---|---|---|---|---|---|
| t67-growthq | 1407 | 266 | 2.3% | 12.6% | 10.7% | 0.756 | 0.842 |
| t67-annual103 | 1407 | 216 | 0.0% | 1.6% | 1.4% | 0.614 | 0.628 |

dcost (base less run): -0.214 [-0.312 -0.126], LOSS.

### Issued p at chosen origins (mean over the covered countries) against the realized rate, each on its own outcome

| scale, origin | t67-growthq p / rate (pos/rows) | t67-annual103 p / rate |
|---|---|---|
| quarter-lead Sep 2008 | 0.023 / 0.379 (22/58) | 0.005 / 0.306 (30/98) |
| quarter-lead Dec 2008 | 0.028 / 0.122 (5/41) | 0.005 / 0.000 (0/68) |
| quarter-lead Sep 2019 | 0.032 / 0.783 (72/92) | 0.033 / 0.720 (72/100) |
| quarter-lead Dec 2019 | 0.013 / 0.778 (63/81) | 0.018 / 0.000 (0/29) |
| quarter-lead Mar 2020 | 0.090 / 0.174 (4/23) | 0.068 / 0.000 (0/29) |
| quarter-lead Jun 2020 | 0.037 / 0.087 (2/23) | 0.037 / 0.000 (0/29) |
| year Dec 2008 | 0.107 / 0.379 (22/58) | 0.078 / 0.306 (30/98) |
| year Dec 2019 | 0.112 / 0.804 (74/92) | 0.080 / 0.713 (72/101) |

Year nodes are issued only at December origins, so Sep 2008, Sep 2019, Mar 2020 and Jun 2020 have no year row. Under the quarterly target, fewer rows resolve at Sep 2008: 29 of the 103 units are covered only later, and 16 had already collapsed in 2008Q3-Q4 and are busy.

### Losses traced

1. **The year loss is mostly the target, not the timing.** By origin year, the Brier gap is largest where both runs miss a wave: Dec 2006-2008 and Dec 2018-2019, where the quarterly target has more positives (2007: 35 against 30; 2018: 77 against 71), each forecast at about 0.1. The rest comes from higher p in calm years: Dec 2003-05, 2009-12 and 2022, where t67-growthq issues 0.12-0.21 against 0.03-0.12. Of the 150 year rows with p ≥ 0.2, 127 are in 2010-12 and 2022, right after the two waves, and the 150 realize at 4%. Outside the five years after an onset in either catalogue the gap stands at -0.030 [-0.049 -0.014]. Inside them it is -0.020 [-0.056 +0.035]. So the refractory period alone does not explain it.
2. **The once-in-20-quarters rule and the 12-month busy mark.** After an onset, the rule forbids another for five years, but the at-risk check only excludes 12 busy months. In the five years after an onset, 19 of 282 rows are positive, and t67-growthq forecasts them at 0.166 against 0.100 for the base. The annual catalogue has the same mismatch (once in five years, busy 12 months). Marking busy for the refractory period, or letting the history inputs know it, is a design question for both catalogues. It is not fixed here.
3. **The quarterly outcome cannot reach the COVID collapse from an origin that knew about COVID.** The 2020Q2 onsets are dated June 2020. The quarter-lead window is (t+3, t+12], so it holds them only from origins up to Dec 2019 (rates 0.78 at Sep and Dec 2019), before any signal. At Mar 2020 the June onsets fall at the window's lower edge. Those units are busy there and leave the at-risk set: 23 rows remain, 4 positive. Year nodes are issued only in December. No issuing scale covers (t, t+3]: `quarter-now` is frailty-only. A quarterly outcome on its own therefore cannot turn the 2020 gauges (LEADING.md) into a timely forecast. It needs an issuing next-quarter scale, or a mid-year year origin.
4. **No bug found** in the new path. All other kinds are byte-identical to t65-gauges. t67-annual103 matches t65 restricted to the same units. The catalogue matches the study. The lookahead tests pass.

### Reading

On the same 103 units, the quarterly outcome does not pay as a drop-in target. Year-node Brier is worse by 0.027 [0.013, 0.044] and skill against each target's constant is -0.075 against -0.057. Quarter-lead is level: +0.001 [-0.012 +0.016], with skill -0.022 against -0.028. Alerts cost more: the quarterly run issues 10.7% alerts and catches 2.3%. Its 2020 timeliness is bounded by the lattice's scales rather than the data. Don't take the mixed catalogue (section 5's next step) until an issuing (t, t+3] scale or an in-year year origin exists, and the busy/refractory mismatch is settled.
