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
