# Alignment data

`data/derived/alignment-annual.csv` holds country-year measures of how a
country is tied to Russia, China, the US and the EU: trade shares, a US
defence pact and US troops. It is long format,
`iso3,year,var,value,known_from_year`. `known_from_year` is the first year
a forecast may read the value. Nothing in the base of record reads the file
yet. It is a new input, not a change to any hash-locked one.

Build it with:

    python3 -I scripts/fetch-dots.py data/raw/slow/imf-dots        # into an empty directory
    python3 -I scripts/alignment-data.py \
      --dots data/raw/slow/imf-dots \
      --atop "data/raw/slow/atop/extracted/ATOP 5.1 (.csv)" \
      --troops data/raw/slow/troopdata/troopdata-rebuild-country-year.csv \
      --pop data/raw/slow/worldbank/SP.POP.TOTL.json \
      --countries resources/bnm/countries.edn \
      --cow data/derived/cow-iso3.csv \
      --out data/derived/alignment-annual.csv
    python3 -I scripts/alignment-check-2021.py data/derived/alignment-annual.csv

`data/derived/cow-iso3.csv` maps COW codes to ISO3 codes. It is
`bnm.named/load-cow-codes` applied to `data/raw/slow/unga/COW-country-codes.csv`
and written out once with:

    jolt -A:tools -M -e "(require '[bnm.named :as n]) (doseq [[c i] (sort-by (comp parse-long key) (n/load-cow-codes \"data/raw/slow/unga/COW-country-codes.csv\"))] (println (str c \",\" i)))"

The script only reads the downloads as data (JSON, CSV, and EDN read by
regex). It runs nothing from them. Each raw directory's `SOURCE.txt` gives
the URL, date, licence and SHA-256 hashes.

## Variables

| var | definition | years | countries | rows |
|---|---|---|---|---|
| dep_russia | (exports to + imports from Russia) / (exports + imports with the world) | 1992-2024 | 204 | 5343 |
| dep_ussr | the same with the former USSR (DOTS `SUH`), up to 1991 only | 1980-1991 | 105 | 954 |
| dep_china | the same with China | 1980-2024 | 210 | 7502 |
| dep_us | the same with the US | 1980-2024 | 208 | 8062 |
| dep_eu | the same with the EU as it was each year (DOTS `B0`, changing composition); members' trade includes intra-EU trade | 1980-2024 | 211 | 8351 |
| trade_change_russia | log(bilateral trade with Russia in t / in t-1) | 1993-2024 | 197 | 4977 |
| trade_change_china | the same with China | 1981-2024 | 210 | 7186 |
| us_defence_pact | 1 if an ATOP defence obligation with the US is in force at any point in the year, else 0 | 1980-2025 | 186 | 7709 |
| us_troops_per_million | US active-duty troops in the country / population in millions | 1980-2024 | 191 | 8102 |

The file has 58,186 rows. Russia, China and the US get no share against
themselves. The US gets no pact or troop rows.

## Sources

**IMF Direction of Trade Statistics (DOTS)**, annual, from DBnomics' copy
(`IMF/DOT`, last indexed by DBnomics on 31 August 2025). The IMF has since
moved DOTS into IMTS on its new portal. This copy runs from 1948 to 2024.
Exports are FOB and imports CIF, both in millions of US dollars. I fetched
two slices:

- every reporter's trade with `RU`, `CN`, `US`, `B0` (the EU), `W00` (the
  world) and `SUH` (the former USSR), 2,679 series;
- Russia's, China's and the US's own reports against every partner, 1,439
  series. These supply mirror data.

So this is not the full reporter-by-partner matrix (about 470,000 series),
only the pairs the table needs. A wider matrix needs a new fetch.

**ATOP 5.1** (Leeds et al.; August 2022 release). The script reads the
dyad-year file's `defense` flag for dyads that include the US (COW 2), and
takes the set of states from the state-year file. Coverage ends in 2018.

**troopdata** (Flynn's R package, commit `972d0c7`, 6 October 2026). The
script reads `troops_ad` (active duty) from
`data-raw/troopdata-rebuild-country-year.csv`, which covers 1950-2024. The
counts come from the DMDC's quarterly reports and earlier DoD reports
(Kane). Population is WDI's `SP.POP.TOTL`, read from
`data/raw/slow/worldbank/` and not modified.

## Lags (known_from_year)

All lags are in whole years. I chose them conservatively, so a forecast
issued at any time in `known_from_year` could have had the value.

- **DOTS: t + 2.** The IMF's annual figures for year t are complete by
  about the second quarter of t+1. Monthly figures for December arrive
  around March. So a forecast in January of t+1 could not have read year t.
  t+2 is always safe. The cost is that the 2022 rupture first shows in
  forecasts for 2024. A model on monthly origins could use "t+1 from July"
  instead, but this file keeps only the year.
- **ATOP: t + 1.** Treaties are public when they are signed and enter into
  force. A pact in force during t is public record by the end of t. ATOP's
  own coding was released in 2022, after the fact. The lag follows the
  treaty record, not the dataset's release date. That is a judgement call:
  the coding itself, not just the events, is retrospective. The US
  defence pacts after 1980 have no secret ones to worry about.
- **ATOP after 2018: t + 1, hand-extended to 2025.** The 2018 pact set is
  carried forward and changed only by treaty events in force:
  - North Macedonia (NATO, March 2020), Finland (April 2023) and Sweden
    (March 2024) become 1.
  - Venezuela becomes 0 from 2020. Its OAS Charter withdrawal took effect
    in April 2019; Venezuela's US pact in ATOP is the OAS Charter/Rio
    alliance, 3150.
  - Nicaragua becomes 0 from 2024. Its OAS withdrawal took effect in
    November 2023.
  - Venezuela's case is contested, because the OAS seated an envoy from
    Guaidó's opposition. Remove `PACT_CHANGES` entries if you prefer ATOP
    only.
- **Troops per million: t + 2.** DMDC's year-end counts are out by early
  t+1. WDI population for t first appears in about June/July of t+1
  (`data/derived/wdi-first-published.tsv`). The ratio needs both, so t+2 is
  safe.

**Vintages.** DOTS and WDI are single downloads of the latest revision,
not real-time vintages. DOTS revises past years and replaces IMF estimates
with reports as they come in. Population revisions are small. The trade
shares are what a forecaster would have seen only up to these revisions.

## Known gaps

- **Russian and Chinese bases and deployments are not coded.** Troops cover
  only the US. Russia in Syria, Armenia, Tajikistan, Kyrgyzstan,
  Transnistria, Belarus and Abkhazia/South Ossetia is not in the table, nor
  are Chinese bases (Djibouti) or Wagner/Africa Corps deployments. Defence
  pacts likewise cover only the US. ATOP has CSTO and Sino-Russian
  commitments, so `us_defence_pact` can be extended to other powers from
  the same file. That would code commitments only, not deployments.
- **US troops are active-duty assigned strength.** Rotational deployments
  are mostly missing. Poland shows 176 troops in 2021, against several
  thousand rotating there. The Gulf in wartime and Afghanistan/Iraq depend
  on how DMDC reported contingency operations.
- **CIS trade before 1992.** DOTS has no Russia before 1992. Trade with the
  USSR (`SUH`) is given separately as `dep_ussr` up to 1991, and is not
  spliced into `dep_russia`. Doing so would put a false break in 1992,
  because the USSR includes Ukraine, Kazakhstan and the rest. Former Soviet
  republics have no rows before they report (from about 1992-94), and
  their trade with each other in the early 1990s is badly measured. There
  is no `trade_change_russia` for 1992, the first year of Russia data.
- **Mirror data.** Where a reporter's own figure is missing for Russia,
  China or the US, the partner's report fills it in: the partner's CIF
  imports divided by 1.1 give the reporter's exports, and the partner's
  FOB exports times 1.1 give its imports. That filled 1,596 of 30,534
  bilateral country-years. Most fills are for Russia (about 22-35
  reporters a year in 2021-24), for China and the US only 3-9. The EU has
  no mirror. Other caveats:
  - Since April 2022 Russia has published no trade by partner. DOTS
    figures for Russia from 2022 on, and any mirror taken from them, are
    IMF estimates built from partners' reports.
  - Rerouting through third countries (Armenia, Kyrgyzstan, Kazakhstan,
    the UAE, Turkey) shows in those countries' own flows. Armenia's
    `trade_change_russia` is +0.65 in 2022 and +0.45 in 2023 and 2024. It
    is not attributed back to the origin.
  - Hong Kong and Taiwan: DOTS has no Taiwan reporter, so Taiwan has no
    trade rows. China's trade through Hong Kong counts as trade with Hong
    Kong, so the China shares of countries that trade via Hong Kong are
    understated.
  - Shares above 1 (a partner's report against a tiny reported total) are
    dropped. There was one: Curaçao, 2016.
  - Ship registries (the Marshall Islands' "imports" from the EU) and
    microstates give extreme shares.
- **EU changing composition.** `dep_eu` measures the EU as it was each
  year, so it jumps at enlargements (1981, 1986, 1995, 2004, 2007, 2013)
  and at Brexit (2020). Shares for members include intra-EU trade.
- **ATOP's pact set is broad.** The OAS Charter/Rio alliance gives
  `us_defence_pact = 1` to most of Latin America and the Caribbean. ATOP
  also codes Pakistan, the Philippines and Thailand as US defence partners
  through bilateral agreements. Ireland, Austria, Switzerland, Sweden and
  Finland (before accession) are 0.
- **Coverage.** The table starts in 1980. DOTS ends in 2024 in this
  vintage, so there are no 2025 trade rows. Troop data ends in 2024.

## 2021 check: does 2021 data separate the 2022 "unfriendly" list?

The list is Russia's "unfriendly countries" list, Government order 430-r,
published 7 March 2022, as published:

- the 27 EU members;
- the UK (with Jersey, Anguilla, the British Virgin Islands and Gibraltar);
- the US, Canada, Ukraine, Montenegro, Switzerland, Albania, Andorra,
  Iceland, Liechtenstein, Monaco, Norway, San Marino, North Macedonia,
  Japan, South Korea, Australia, Micronesia, New Zealand, Singapore and
  Taiwan.

The check uses only 2021 values of `dep_russia` and `us_defence_pact`,
excludes Russia, and counts only countries with both values (170). Of
those, 42 are listed. Andorra, Micronesia, Liechtenstein, Monaco, Taiwan
and the US lack a value.

| group | n | dep_russia median | mean | 90th pct | max | pact share |
|---|---|---|---|---|---|---|
| listed | 42 | 0.0144 | 0.0246 | 0.0466 | 0.114 | 0.738 |
| not listed | 128 | 0.0070 | 0.0297 | 0.0591 | 0.490 | 0.258 |

AUC is the probability that a random listed country scores higher than a
random unlisted one; ties count as half. 0.5 means no separation:

- low dep_russia: 0.341. It points the wrong way. Read as high dep_russia,
  it is 0.659.
- us_defence_pact: 0.740.
- pact, then low dep_russia: 0.675.

Pact against listing:

| | listed | not listed |
|---|---|---|
| pact = 1 | 31: NATO's European members, Canada, the UK, Albania, North Macedonia, Montenegro, Japan, South Korea, Australia | 33: 29 OAS/Rio states, Pakistan, the Philippines, Thailand, and Turkey (NATO) |
| pact = 0 | 11: Austria, Switzerland, Cyprus, Finland, Ireland, Malta, New Zealand, Singapore, San Marino, Sweden, Ukraine | 95 |

The pact separates the coalition reasonably well. Nearly all its false
positives come from one ATOP alliance, the OAS/Rio coding of Latin
America. If that alliance were dropped, most of the 33 would move to the
0 row.

Low trade dependence on Russia does not separate the coalition. The listed
countries trade more with Russia than the median unlisted country does.
The list is European, and Europe's trade with Russia was large. The
unlisted countries are mostly distant states with almost no Russia trade.
Russia's former-Soviet neighbours sit at the top for both groups: Lithuania
0.114, Latvia 0.104, Finland 0.087 and Estonia 0.076 are listed, while
Belarus 0.49, Armenia, Kazakhstan and Kyrgyzstan (about 0.31) are not. A
threshold makes the point: dep_russia < 0.02 catches 27 of 42 listed
countries and 97 of 128 unlisted ones. On its own, 2021 dep_russia measures
exposure to a rupture with Russia, not which side a country took. It may
still help a crisis model: the listed countries with high dep_russia
(the Baltics, Finland) are where the 2022-23 trade collapse was largest.
