# Point-in-time audit of t162 (bnm-structure @ 018d6c5)

Read-only. Nothing edited or run except read-only python/awk over data files. t162 = `bnm.ledger/command` (tools/bnm/ledger.clj:563-577).
V = code path read; S = suspected (relies on outside knowledge of real release dates or magnitude unmeasured).

## Headline

No two-sided filter, centred window, full-sample z-score, full-sample quantile threshold or backfill survives on the t162 path (item 1/3 clean). Catalogue thresholds are all fixed or trailing (item 2 definitions clean). The leaks that remain are availability-dating leaks: synthesized lags that are too short, flags that fix one reader but not another, and the precomputed `--known r7-at-fx` run that predates the newer lag conventions.

## Findings

| # | file:line | What leaks | Kinds / inputs | t162 uses? | Sev | One-line fix | V/S |
|---|---|---|---|---|---|---|---|
| 1 | scripts/slow-normalize.py:972-980 (exact-dated UCDP onsets written with known = onset date); src/bnm/onset.clj:24-30 `history-features`; tools/bnm/onsets.clj:546 | The named model in r7-at-fx builds its own-history features (h1 count, h2 recency) from events.tsv's per-event known date. 647 of 759 conflict onsets (UCDP ACD with start_prec2 < 5) and all 50 ESRB banking onsets have known = onset date (lag 0). UCDP publishes about June of the following year, so the feature sees an onset 6-18 months early. The lattice itself uses 18 months or the release calendar for the same events. | `--known r7-at-fx` preds (model "named" = cols [0 1 2] + named, onsets.clj:322), pooled at 0.5 into the quarter-lead/w2 offsets: conflict_onset (own history), banking_crisis (ESRB) | Yes | High (conflict quarter nodes), medium (banking) | Write known = release date for exact-dated UCDP onsets (Oct of year+1, or onset+18), ESRB likewise, then rebuild the known run (r8) | V (data, code path); size S |
| 2 | scripts/slow-normalize.py:326-355 (WDI_LAG 9 months); named.clj:170 (`--wdi-editions`, off in t162) | Every WDI annual var is available at period end + 9 months. WDI archives show data years ≤2003 first came out 16 months on, and about 10% of country-years 19-40 months on. The project documents this (docs/CALIBRATION.md:2369, "lookahead finding 1") but t162 doesn't pass `--wdi-editions` or `--fresh-inputs`. | Kind theories for sovereign_default, currency, inflation@monthly, coup, fiscal; political/economic year rows. Training rows up to the Dec 2004 origin plus late country-years throughout | Yes | High | Add `--wdi-editions true` to t162 (and apply it to derived tables, see 4) and rerun | V |
| 3 | tools/bnm/lattice_banking.clj:674 (via scripts/lattice-data.py:72-74) | The EPWT 7.0 profit rate (built from PWT) is read at y-1 at December y+1 with no release gate. PWT's own columns are gated under `--leak-fixes`/`--pwt-stale`, so this partly undoes that fix. | banking year node :profitability cols 56-57, tensions [54 56] [62 56] | Yes | Med-High | Add an "epwt" row to resources/bnm/releases.tsv and gate it with rel/read-year (or read EPWT at PWT's read year) | V code / S release dates |
| 4 | scripts/rer-annual.py:44; world_run.clj:936-941 | derived/rer_usd inherits the synthesized WDI +9 month dates of fx_per_usd and inflation. `--real-lags` and `--wdi-editions` are applied only to slow/series.tsv, never to derived tables. | currency_crisis theory :misalignment, :misalignment-x-peg | Yes | Medium | Date rer-annual from wdi-editions.tsv, or apply the load-series opts to derived tables | V |
| 5 | slow-normalize.py:587-608 (BIS quarterly at quarter end + 4 months) vs lattice_banking.clj:499 (`bis-lag` 6) | BIS credit, credit gap, DSR, house prices and gov credit are stored at 4 months. The code's own BIS lag is 6, but only `--dated-credit`'s banking year row honours it. BIS long series were first published in 2013, so earlier values are backcasts. | banking quarter nodes (nm/financial, world-credit3); inflation@monthly :government-debt-change24 | Yes | Medium | Store BIS rows at 6 months, or add the BIS vars to `real-lags` | V |
| 6 | slow-normalize.py:425-472 (IRR at 12 months) | IRR fx_peg/fx_regime are read 12 months on. IRR's releases were years apart and the de facto coding is set after the fact (the code says so). | currency theory misalignment-x-peg; market-crash global layer usd6-x-peg | Yes | High (S) | Date each year by its IRR release (releases.tsv row), or drop fx_peg from the theories | V lag / S size |
| 7 | tools/bnm/kinds.clj:43, lattice_banking.clj:51 (`known-lag` 12) | Banking onsets are known 12 months after the onset month, but events.tsv dates Laeven-Valencia onsets known at onset + 18 (its own convention; L-V dates crises after the fact). That is 6 months early for 124 L-V onsets. | banking own history (lb/history), other kinds' counts (lattice_political.clj:162-175 count-known), aftermath kernels; banking target known-at hi+12 (lattice_banking.clj:880) | Yes | Medium | Give the banking catalogue :known-lag 18 (or per-event known dates) | V |
| 8 | tools/bnm/aftermath.clj:35 with world_run.clj:1585 | The aftermath kernels gate onsets at m + :known-lag (18 for conflict/war) and ignore `--release-calendar`'s October release. All 112 year-only conflict and 270 war onsets are dated January, so they enter 3 months before UCDP's release; exact-dated Jan-Mar onsets enter 1-3 months early. | growth_collapse year node, others-fast column | Yes | Medium (one column, ≤3 months) | Use `(<= (lb/known-month (cats k) m) t)` in known-row | V |
| 9 | lattice_political.clj:375 (target known-at hi + known-lag); world_run.clj:1151 | Under `--release-calendar` the training labels stay at hi + 18. A window ending Jan-Mar of Y with an onset in Jan..hi of Y is resolved 1-3 months before the October Y+1 release. | conflict_onset, war_onset labels | Yes | Low-Med | known-at = max(hi + lag, release month of hi's year) | V |
| 10 | tools/bnm/onsets.clj:834 | r7-at-fx loads series.tsv without :real-lags, so world food/price_index is read at +1 month, not its real +2 (named.clj:73). | named-model features pooled into the quarter offsets of six kinds | Yes | Medium | Rebuild the known run with real-lags (same rebuild as 1) | V |
| 11 | scripts/fiscal-series.py:30-33; fiscal.csv | GMD years are hard-coded available 30 Sep of year+1, from a single 2025/26 vintage that splices in WEO estimates. GMD is also the source of the fiscal target. | fiscal theory; fiscal.csv in every political/economic year row | Yes | Medium (vintage) | Date years by WEO/GMD edition where known (bead bnm-09d) | V / S size |
| 12 | V-Dem v16 (3 months), WID, Maddison 2023, JST R6, BVX, MV: one revised vintage each | V-Dem refits its whole history each release; WID interpolates and nowcasts. JST was first released in 2016 but is read at y-1 at every origin; `--dated-credit` replaces only the credit family from Dec 2003 (CALIBRATION.md:2370, bnm-9vm). | political year rows, coup theory, banking year groups | Yes | Medium (known) | List them in named/not-real-time with a declared vintage; finish bnm-9vm for the other JST columns | V (documented) |
| 13 | releases.clj:45-49 | read-year falls back to the fixed offset before a source's first release, so Maddison before Dec 1995, NMC before Apr 1993 and PWT before May 1991 are read although unpublished. | early learning, carried forward by the state | Yes | Low-Med | Return nil before the first release | V |
| 14 | lattice_banking.clj:836-840 | OECD house price-to-income/rent is read one quarter back (about 3 months). The release comes 4-6 months on and its income base is revised. | banking quarter nodes cols 18-19 | Yes | Low-Med | Read q-2 | V code / S lag |
| 15 | lattice_banking.clj:1112-1113 (q4-annual) | `--recession-hold rows` takes its recession sign from the latest revised gdp_growth, not the first print (timing itself is fine). | banking year table held ratios | Yes | Low-Med | Take the flag from first-release growth / sk/known | V |
| 16 | benchmarks.clj:55-56,69-70,95-96; watchlist.clj:150-165 | The benchmarks (last12, persist, pers, wclim) use :known-lag (18) and ignore the release calendar, so Jan-Mar conflict/war onsets are seen 1-3 months early (all year-only ones are January). watchlist `:hard` uses no lag at all. | ledger benchmark columns / claim baselines for conflict, war; hard-row report lines | Yes (scoring) | Low-Med | Route through lb/known-month; give :hard the lag | V |
| 17 | lattice_banking.clj:147-172 (`onset-in?`, `possible-onset?`), kinds.clj:81-83 (:barred) | The risk set is defined with information after the origin: busy months in (t, lo], refractory from onsets ≤ lo, inflation :barred from year-Y inflation. These are label definitions only, resolved at hi+lag, and not features. They select which rows are scored, not the forecasts. | all kinds, scoring denominators | Yes (scoring) | Low | None needed; document it as an ex-post risk set | V |
| 18 | scripts/global-monthly.py:58-68 | The partial last month is averaged and dated as the full month (VIX Oct 2026 uses closes to 15 Oct). | market_crash global layer, live issues only | Live only | Low | Drop the incomplete final month | V |
| 19 | tools/bnm/onsets.clj:781 (`combine-rows`) | pool/stack/hedge are fit on rows whose window closed by q0, ignoring the kind's reporting lag (evaluate-kind uses resolves-at). | r7-at-fx combinations | No (t162 reads "named" only) | Low | Use resolves-at with the kind's lag | V |
| 20 | rer-annual.py:35-37; political_gauges.clj:164; economic_events.clj:443-471 | Row existence or source choice depends on later availability: rer keeps the latest unbroken run; NMC/WDI urban choice tests year p, not pn; econ :through is the last year in today's vintage. Values are not affected. | currency theory, year rows, econ scoring | Yes | Low | Cut at breaks / test pn / date coverage | V |
| 21 | F4: all t162 options and hyperparameters (ledger.clj:487-577) | Each option was adopted by comparing full-history backtests (t162's level option even over a fold-rule fail). This is selection on the evaluation period, not a data leak. Leading priors and theory signs were set from post-2008 literature. | all 13 kinds' backtest scores | Yes | Medium (backtest only) | Treat backtest skill as in-sample; judge on forward ledger issues | V (docs) |

## Already-known items: status

- **D2 output gap**: one-sided now (economic_events.clj:127-141, last point of an HP(100) fit up to each year, ten-year minimum). D2 is used only under `--fiscal-outcomes discretionary`, which t162 doesn't pass. The case for D2 in docs/studies/fiscal_discretionary.py:24-55 still uses the two-sided gap and ignores available-date; rerun it before citing it (low).
- **`--known r7-at-fx`**: fixes FAOSTAT CPI at 9 months in the named run's series.tsv. It does not fix 1 (UCDP/ESRB known-at-onset in events.tsv) or 10 (no real-lags). Its refit is out of sample per year (onsets.clj:592, resolves-at).
- **`--leak-fixes`**: dates Maddison, NMC and PWT (political rows and banking's PWT columns) by releases.tsv, and puts urban growth in one unit. It leaves out EPWT (3), the pre-first-release fallback (13), WID/GDD/MV/BVX.
- **`--pwt-stale` / `--lag-stale`**: staleness caps (max age), not availability; they touch only PWT vars and tertiary enrolment.
- **`--real-lags`**: 15 world IMF/FRED commodity series to 2 months, 5 US series to 2-3 months; slow/series.tsv only. It doesn't reach the derived tables or the r7 run (10).
- **`--dated-credit`**: banking year row credit family from BIS at 6 months from Dec 2003. It leaves out BIS at 4 months elsewhere (5) and the other JST columns (12).
- **`--release-calendar conflict_onset,war_onset`**: lattice inputs through lb/known-month (own history, others' counts, world share). It doesn't reach the aftermath kernels (8), the labels (9), the benchmarks (16) or the r7 named model (1).
- **Not in t162**: `--wdi-editions`, `--fresh-inputs` (2), `--catalogue-through` (an uncovered catalogue tail is read as calm, not unknown; low).

## Verified causal (no action)

- impute (last earlier-origin value decayed to the peer mean, no backfill)
- place-z / running-z / history-standardizer: strictly earlier origins
- shapes: fixed hinges at z = -1, 0, 1
- theory fits: rows with known-at ≤ t
- theory-half-life-level: "full history" means rows known by t, not the whole sample
- theory-decay; recalibrate-intercept: origins ≤ o-48, needs ≤ o-42
- direction gauges: 12-month peak, 3-month change, at availability
- leading gauges at availability
- frailty gauges; flow-std-from :resolved; clim-learn/evidence-at/discount-from (lattice.clj:1970 gate, reads after the origin throw at :656/:2149)
- jst-credit-gap: one-sided HP (scripts/jst-credit-gap.py:26-31, macrohistory.clj:161-181)
- onset-history.tsv; global-monthly lags
- catalogue thresholds: growth collapse (trailing 20 years), food (trailing 20 quarters / 60 months), mechanical market/protest/violence (trailing 24/36 months), inflation 40%, L-V currency rule (fixed)
- docs/studies reference.py, scorecard.py climatology (origins ≤ t-48), recalibrate_intercept.py

## Series availability (item 4): summary

188 of 192 slow vars, all fast vars, and every derived series table have a constant lag, so their dates are synthesized. Only the four PWT vars carry real release dates. Synthesized lags by source: WDI 9 months (tertiary enrolment 21), V-Dem 3, IMF CTOT 2, IRR 12, BIS quarterly 4 (policy rate 0, CPI/REER 1), OECD 1, OWID 9, JODI 2, FAOSTAT CPI 9, FAOSTAT production 12, FRED 1 (daily 0, TDSP 4), GDELT 0, EM-DAT 3, fast tables 1, GMD 30 Sep of year+1.

Too short against real publication: WDI pre-2004 and late country-years (2), WDI/IDS external debt (about 12 months real; S), BIS 4 against 6 (5), IRR (6), OECD house valuation (14). Old FRED TDSP rows (finance/dsr_households) are dated 1 month from the quarter's first month, but nothing reads them.

## Suggested order

1. Rebuild the known run with UCDP/ESRB known dates fixed and real-lags (1, 10).
2. Add `--wdi-editions` and extend it to derived tables (2, 4).
3. Gate EPWT (3).
4. Banking :known-lag 18 (7).
5. Route aftermath, labels and benchmarks through lb/known-month (8, 9, 16).
6. BIS at 6 (5); IRR release dating (6).
