(ns bnm.kind-theory
  "A theory forecast as a kind's year-node offset (bnm-1dw), banking's
  design (bnm.lattice-banking --theory-offset) for the kinds built by
  bnm.lattice-political: a pooled logit on stocks and flows read at the
  origin, refit at each December origin on the rows known by then
  (bnm.theory), its inputs standardized against every country's values at
  earlier origins. The level then comes from what is read at the origin,
  not from lagged outcomes (docs/INVARIANTS.md point 4)."
  (:require [bnm.geo :as geo]
            [bnm.impute :as im]
            [bnm.lattice-banking :as lb]
            [bnm.macrohistory :as mh]
            [bnm.signals :as sg]
            [bnm.theory :as th]
            [clojure.string :as str]))

(def ^:private sovereign
  "Sovereign defaults, fixed before any run (docs/CALIBRATION.md t109):
  the early-warning literature's country inputs (Manasse and Roubini 2009,
  rules of thumb: external debt, reserves, inflation, growth; Reinhart and
  Rogoff 2009) and the global push, US monetary tightening (the Volcker
  defaults of 1982-83, 2022's). :sign, each input's direction in that
  literature, read only by --theory-signs (bnm-7yg.6)."
  [{:name :external-debt :var "debt/external_gni" :read [:value] :stale 30 :sign 1}
   {:name :reserves :var "debt/reserves_months" :read [:value] :stale 30 :sign -1}
   {:name :inflation :var "economy/inflation" :read [:value] :stale 30 :sign 1}
   {:name :growth :var "economy/gdp_growth" :read [:value] :stale 30 :sign -1}
   {:name :us-rate-change24 :var "finance/policy_rate" :unit "USA" :read [:change 24] :stale 3 :sign 1}])

(def ^:private currency-t110
  "Currency crises, fixed before any run (docs/CALIBRATION.md t110): the
  early-warning literature's leading indicators (Kaminsky, Lizondo and
  Reinhart 1998: reserves, real overvaluation, the current account,
  inflation; Frankel and Rose 1996: external debt, foreign interest rates)."
  [{:name :reserves :var "debt/reserves_months" :read [:value] :stale 30}
   {:name :reer-change24 :var "finance/reer" :read [:change 24] :stale 3}
   {:name :current-account :var "debt/current_account_gdp" :read [:value] :stale 30}
   {:name :inflation :var "economy/inflation" :read [:value] :stale 30 :log1p true}
   {:name :external-debt :var "debt/external_gni" :read [:value] :stale 30}
   {:name :us-rate-change24 :var "finance/policy_rate" :unit "USA" :read [:change 24] :stale 3}])

(def ^:private currency
  "Currency crises re-specified (bnm-mn8, docs/CALIBRATION.md t115; reports/
  Crisis mechanisms data and signals.md): t110's REER change fitted the
  wrong sign, a change where theory names a level, with no regime term and
  BIS coverage of 63 countries. Here overvaluation is a level, the real
  rate against the dollar (data/derived/rer-annual.tsv, 191 countries)
  stretched against its own last ten years, and times the de facto peg
  (overvaluation bites under pegs); momentum (the currency's 12-month
  fall, monthly) and exchange-market pressure (its 3-month fall, the
  reserves' 12-month change) are their own inputs; reserves, the current
  account, inflation, external debt and the US rate as before."
  [{:name :reserves :var "debt/reserves_months" :read [:value] :stale 30}
   {:name :misalignment :var "derived/rer_usd" :read [:stretch {:window 10 :min 6 :cap 5.0 :eps 1e-9}] :stale 30}
   {:name :misalignment-x-peg :expr [:mul {:var "derived/rer_usd" :read [:stretch {:window 10 :min 6 :cap 5.0 :eps 1e-9}] :stale 30}
                                     {:var "finance/fx_peg" :read [:value] :stale 30}]}
   {:name :momentum12 :var "fast/fx" :read [:change 12] :stale 3}
   {:name :depreciation3 :var "fast/fx" :read [:change 3] :stale 3}
   {:name :reserves12 :var "debt/reserves_months" :read [:change 12] :stale 30}
   {:name :current-account :var "debt/current_account_gdp" :read [:value] :stale 30}
   {:name :inflation :var "economy/inflation" :read [:value] :stale 30 :log1p true}
   {:name :external-debt :var "debt/external_gni" :read [:value] :stale 30}
   {:name :us-rate-change24 :var "finance/policy_rate" :unit "USA" :read [:change 24] :stale 3}])

(def series-paths
  "The tables the theory layers read: the slow table, the global drivers
  (scripts/global-monthly.py), the real rates (scripts/rer-annual.py), the
  fast table's monthly exchange rates."
  ["data/slow/series.tsv" "data/derived/global-monthly.tsv" "data/derived/rer-annual.tsv" "data/fast/series.tsv"
   "data/derived/trade-partners.tsv" "data/derived/onset-history.tsv" "data/derived/fiscal-series.tsv"
   "data/derived/banking-series.tsv"])

(def ^:private path-prefix
  {"data/derived/global-monthly.tsv" "global/" "data/derived/rer-annual.tsv" "derived/rer_" "data/fast/series.tsv" "fast/"
   "data/derived/trade-partners.tsv" "trade/export_share_" "data/derived/onset-history.tsv" "history/"
   "data/derived/fiscal-series.tsv" "fiscal/" "data/derived/banking-series.tsv" "banks/"})

(defn paths-for
  "The tables in `series-paths` holding any of `vars`: the slow table
  always, the others only when a variable needs them (each is an input of
  record, docs/LEDGER.md)."
  [vars]
  (filterv (fn [p] (if-let [pre (path-prefix p)] (some #(clojure.string/starts-with? % pre) vars) true)) series-paths))

(def ^:private inflation
  "Inflation crises, fixed before any run (docs/CALIBRATION.md t110):
  high inflations follow inflation already running, the currency's fall
  and fiscal deficits (Fischer, Sahay and Vegh 2002; Catao and Terrones
  2005, government debt's rise standing in for the deficit), with reserves
  and output as the cushion."
  [{:name :inflation :var "economy/inflation" :read [:value] :stale 30 :log1p true}
   {:name :depreciation12 :var "finance/fx_per_usd" :read [:change 12] :stale 30}
   {:name :government-debt-change24 :var "debt/government_credit_gdp" :read [:change 24] :stale 12}
   {:name :reserves :var "debt/reserves_months" :read [:value] :stale 30}
   {:name :growth :var "economy/gdp_growth" :read [:value] :stale 30}])

(defn- exposure [v] {:var v :unit :own :read [:value] :stale 30 :lag 12})
(defn- world6 [v] {:var v :unit "world" :read [:change 6] :stale 3})
(def ^:private us-rate6 {:var "finance/policy_rate" :unit "USA" :read [:change 6] :stale 3})

(def global
  "The global layer (bnm-1ft; reports/Crisis mechanisms data and signals.md,
  fixed before any run, docs/CALIBRATION.md t113): the drivers that turn
  country fragility into waves, each its 6-month change at the origin
  (data/derived/global-monthly.tsv, scripts/global-monthly.py; the US
  policy rate from data/slow/series.tsv), and each signed channel times the
  country's exposure a year earlier (Obstfeld and Zhou 2022: dollar
  liabilities and pegs; net fuel exports for oil; food imports for food;
  external debt for US rates and risk spreads). Kept as separate signed
  channels: the commodity channel hit exporters in 2014-15 and importers in
  2022."
  [(assoc (world6 "global/usd_eer") :name :usd6)
   (assoc us-rate6 :name :us-rate6)
   (assoc (world6 "global/oil") :name :oil6)
   (assoc (world6 "global/food") :name :food6)
   (assoc (world6 "global/baa_spread") :name :baa6)
   (assoc (world6 "global/vix") :name :vix6)
   (assoc (world6 "global/usd_eer") :name :usd6-x-external-debt :times (exposure "debt/external_gni"))
   (assoc (world6 "global/usd_eer") :name :usd6-x-peg :times (exposure "finance/fx_peg"))
   (assoc us-rate6 :name :us-rate6-x-external-debt :times (exposure "debt/external_gni"))
   {:name :oil6-x-net-fuel :expr [:mul (world6 "global/oil")
                                  [:minus (exposure "energy/fuel_exports_share") (exposure "energy/fuel_imports_share")]]}
   (assoc (world6 "global/food") :name :food6-x-food-imports :times (exposure "food/imports_share"))
   (assoc (world6 "global/baa_spread") :name :baa6-x-external-debt :times (exposure "debt/external_gni"))])

(def global-kinds
  "The kinds the global layer may serve: the economic ones."
  #{"banking_crisis" "currency_crisis" "sovereign_default" "inflation_crisis" "growth_collapse"
    "fiscal_consolidation" "market_crash" "food_price_shock"})

(defn kind-of "A theory's kind: the name before any @variant." [k] (first (str/split k #"@")))

(def ^:private bis-inflation {:var "economy/inflation_monthly" :read [:value] :stale 6})
(def ^:private fao-cpi12 {:var "economy/cpi_general" :read [:change 12] :stale 18})
(def ^:private wdi-inflation {:var "economy/inflation" :read [:value] :stale 30})

(defn fresh-inflation
  "Consumer price inflation as log(1 + x/100), the freshest read at t
  (t117): BIS's monthly year-on-year (62 countries, a month behind), else
  FAOSTAT's 12-month change in the log index (207 countries from 2000,
  about nine months behind), else WDI's annual (about 21 months stale by
  December). Nil when none is known."
  [db u t]
  (let [rd #(sg/read-signal db % u t)]
    (if-let [x (rd bis-inflation)]
      (Math/log1p (Math/max -0.99 (/ (double x) 100.0)))
      (or (rd fao-cpi12)
          (some-> (rd wdi-inflation) double (/ 100.0) (Math/max -0.99) Math/log1p)))))

(def ^:private partners
  "The trade partners whose currency falls are read (t117): Russia, China,
  the euro area (DEU's fast/fx carries the euro from 1999)."
  {"trade/export_share_ru" "RUS" "trade/export_share_cn" "CHN" "trade/export_share_eu" "DEU"})

(defn partner-falls
  "Trade-weighted contagion (t117; Glick and Rose 1999): the sum over
  partners of the country's export share to each a year before times the
  partner currency's 12-month fall against the dollar (0 for a rise); nil
  when no share is known."
  [db u t]
  (let [terms (for [[sv pu] partners
                    :let [w (sg/read-signal db {:var sv :read [:value] :stale 30 :lag 12} u t)]
                    :when w]
                (* w (Math/max 0.0 (double (or (sg/read-signal db {:var "fast/fx" :unit pu :read [:change 12] :stale 3} u t) 0.0)))))]
    (when (seq terms) (reduce + terms))))

(def ^:private inflation-monthly
  "Inflation crises with timelier reads (t117): inflation the freshest of
  BIS, FAOSTAT and WDI (`fresh-inflation`), the currency's 12-month fall
  monthly (fast/fx) in place of WDI's annual average rate; the rest as
  `inflation`."
  [{:name :inflation :expr [:fn fresh-inflation] :reads [bis-inflation fao-cpi12 wdi-inflation]}
   {:name :depreciation12 :var "fast/fx" :read [:change 12] :stale 3}
   {:name :government-debt-change24 :var "debt/government_credit_gdp" :read [:change 24] :stale 12}
   {:name :reserves :var "debt/reserves_months" :read [:value] :stale 30}
   {:name :growth :var "economy/gdp_growth" :read [:value] :stale 30}])

(def ^:private currency-partners
  "Currency crises as t115's list plus trade-weighted partner falls (t117)."
  (conj currency {:name :partner-falls :expr [:fn partner-falls]
                  :reads (into [{:var "fast/fx" :unit "RUS"} {:var "fast/fx" :unit "CHN"} {:var "fast/fx" :unit "DEU"}]
                               (for [sv (keys partners)] {:var sv}))}))

(defn regional-mean
  "(fn [db u t]) the mean of `var` (its latest value) over the other
  countries of u's subregion (bnm.geo) that have it at t; nil when none."
  [var]
  (let [units (memoize (fn [db] (vec (distinct (for [[u v] (keys db) :when (= v var)] u)))))
        sub (memoize (fn [u] (first (geo/parents u))))]
    (fn [db u t]
      (let [s (sub u)
            xs (keep #(when (and (not= % u) s (= s (sub %))) (sg/read-signal db {:var var :read [:value] :stale 12} % t)) (units db))]
        (when (seq xs) (/ (reduce + xs) (count xs)))))))

(defn- hist [k n] {:var (str "history/" k "_" n) :read [:value] :stale 12})

(def ^:private coup
  "Coups (gap 1, docs/CALIBRATION.md t119): the IMF's coup model (2024) and
  Powell and Thyne: the country's coup history (years since the last, the
  count in ten years), the region's coups in three years (Bell and Powell:
  unsanctioned coups erode the norm against them), growth and inflation
  (growth 1-2 points lower the year before, inflation rising), the
  military's role in the executive, electoral democracy (shaped, so the
  middle can carry the risk), protest and its 6-month change (CoupCast:
  protests near power), infant mortality."
  [(assoc (hist "coup" "since") :name :coup-since)
   (assoc (hist "coup" "count10") :name :coup-count10)
   {:name :regional-coups3 :expr [:fn (regional-mean "history/coup_count3")] :reads [(hist "coup" "count3")]}
   {:name :growth :var "economy/gdp_growth" :read [:value] :stale 30}
   {:name :inflation :expr [:fn fresh-inflation] :reads [bis-inflation fao-cpi12 wdi-inflation]}
   {:name :military-executive :var "regime/military_executive" :read [:value] :stale 30}
   {:name :electoral-democracy :var "regime/electoral_democracy" :read [:value] :stale 30}
   {:name :protest :var "politics/protest" :read [:value] :stale 3}
   {:name :protest-change6 :var "politics/protest" :read [:change 6] :stale 3}
   {:name :infant-mortality :var "people/infant_mortality" :read [:value] :stale 30}])

(def ^:private war
  "War onsets (gap 1, t119): history (years since the last war and the last
  armed-conflict onset), the region's conflict onsets in three years,
  military spending and its rise, electoral democracy, hostile rhetoric and
  its acceleration (GDELT verbal conflict, the one documented leading
  indicator, news-based), violence's 6-month change, the world's
  geopolitical risk (Caldara and Iacoviello, newspapers) over 6 months,
  infant mortality."
  [(assoc (hist "war_onset" "since") :name :war-since)
   (assoc (hist "conflict_onset" "since") :name :conflict-since)
   {:name :regional-conflict3 :expr [:fn (regional-mean "history/conflict_onset_count3")] :reads [(hist "conflict_onset" "count3")]}
   {:name :military-gdp :var "state/military_gdp" :read [:value] :stale 30}
   {:name :military-change24 :var "state/military_gdp" :read [:change 24] :stale 30}
   {:name :electoral-democracy :var "regime/electoral_democracy" :read [:value] :stale 30}
   {:name :verbal-conflict :var "politics/verbal_conflict" :read [:value] :stale 3}
   {:name :verbal-conflict-change6 :var "politics/verbal_conflict" :read [:change 6] :stale 3}
   {:name :violence-change6 :var "politics/violence" :read [:change 6] :stale 3}
   {:name :world-risk6 :var "politics/geopolitical_risk" :unit "world" :read [:change 6] :stale 3}
   {:name :infant-mortality :var "people/infant_mortality" :read [:value] :stale 30}])

(def ^:private us-rate-change24 {:name :us-rate-change24 :var "finance/policy_rate" :unit "USA" :read [:change 24] :stale 3})

(def ^:private fiscal
  "Fiscal consolidations (gap 2, docs/CALIBRATION.md t120; reports/Crisis
  mechanisms data and signals.md: debt and financing needs the stock,
  a spread jump, a new mandate or a programme the trigger). The balance and
  its last change, spending's change (the catalogue's rule: the balance up
  1.5 points and spending down one, Alesina and Ardagna), government debt
  and its rise (Bohn's response of the balance to debt), the 10-year
  spread over the US and its 6-month change (market pressure; 33
  countries), an election in the last year (a new mandate front-loads
  cuts; Alesina, Perotti and Tavares), growth, inflation, the US rate's
  rise and external debt. GMD's balance and spending from data/derived/
  fiscal-series.tsv (scripts/fiscal-series.py)."
  [{:name :balance :var "fiscal/balance_gdp" :read [:value] :stale 30}
   {:name :balance-change12 :var "fiscal/balance_gdp" :read [:change 12] :stale 30}
   {:name :expenditure-change12 :var "fiscal/expenditure_gdp" :read [:change 12] :stale 30}
   {:name :government-debt :var "debt/government_gdp" :read [:value] :stale 30}
   {:name :government-debt-change24 :var "debt/government_gdp" :read [:change 24] :stale 30}
   {:name :spread :var "fast/spread" :read [:value] :stale 3}
   {:name :spread-change6 :var "fast/spread" :read [:change 6] :stale 3}
   {:name :election-legislative :var "regime/election_legislative" :read [:value] :stale 24}
   {:name :election-presidential :var "regime/election_presidential" :read [:value] :stale 24}
   {:name :growth :var "economy/gdp_growth" :read [:value] :stale 30}
   {:name :inflation :expr [:fn fresh-inflation] :reads [bis-inflation fao-cpi12 wdi-inflation]}
   us-rate-change24
   {:name :external-debt :var "debt/external_gni" :read [:value] :stale 30}])

(def ^:private growth
  "Growth collapses (gap 2, t120; reports/Crisis mechanisms data and
  signals.md: external shocks that arrive in late GDP data). The commodity
  terms of trade's fall over 12 and 24 months (the IMF's index weighted by
  net exports to GDP, so dependence is already in it), commodity exports'
  share (fuel plus ores and metals, a year before), reserves, the 3-year
  credit boom, external debt and the current account (the sudden stop's
  stock), the currency's 12-month fall, conflict history and the region's
  conflict onsets, growth, inflation and the US rate's rise."
  [{:name :tot-change12 :var "trade/commodity_tot" :read [:change 12] :stale 9}
   {:name :tot-change24 :var "trade/commodity_tot" :read [:change 24] :stale 9}
   {:name :commodity-exports :expr [:sum-or-zero (exposure "energy/fuel_exports_share") (exposure "trade/metal_exports_share")]}
   {:name :reserves :var "debt/reserves_months" :read [:value] :stale 30}
   {:name :private-credit-change36 :var "finance/private_credit_gdp" :read [:change 36] :stale 30}
   {:name :external-debt :var "debt/external_gni" :read [:value] :stale 30}
   {:name :current-account :var "debt/current_account_gdp" :read [:value] :stale 30}
   {:name :fx-momentum12 :var "fast/fx" :read [:change 12] :stale 3}
   (assoc (hist "conflict_onset" "since") :name :conflict-since)
   {:name :regional-conflict3 :expr [:fn (regional-mean "history/conflict_onset_count3")] :reads [(hist "conflict_onset" "count3")]}
   {:name :growth :var "economy/gdp_growth" :read [:value] :stale 30}
   {:name :inflation :expr [:fn fresh-inflation] :reads [bis-inflation fao-cpi12 wdi-inflation]}
   us-rate-change24])

(def ^:private banking-literature
  "Banking crises (gap 2, t120): added to --theory-offset's own inputs (the
  credit stretch, the 3-year credit boom, the world's loans to deposits and
  reserves; `with-own-inputs`), the research's: the private debt service
  ratio against its own ten years (Drehmann and Juselius: the best 1-2 year
  indicator), real house prices' 3-year rise and its product with the
  3-year credit rise (Greenwood, Hanson, Shleifer and Sorensen's R-zone),
  WDI's 3-year private credit rise (186 countries, beyond the BIS's 43),
  the short rate's 12-month rise (the trigger), the sovereign spread's
  6-month change (the doom loop), the current account and the currency's
  12-month fall (twin crises). Bank equity's drawdown has no country
  series here and is left out."
  [{:name :dsr-gap :var "finance/dsr_private" :read [:stretch {:window 40 :min 20 :cap 5.0 :eps 1e-9}] :stale 12}
   {:name :house-boom36 :var "finance/real_house_prices" :read [:change 36] :stale 12}
   {:name :credit-x-house :expr [:mul {:var "finance/credit_gdp" :read [:change 36] :stale 12}
                                 {:var "finance/real_house_prices" :read [:change 36] :stale 12}]}
   {:name :private-credit-change36 :var "finance/private_credit_gdp" :read [:change 36] :stale 30}
   {:name :short-rate-change12 :var "finance/rate_3m" :read [:change 12] :stale 3}
   {:name :spread-change6 :var "fast/spread" :read [:change 6] :stale 3}
   {:name :current-account :var "debt/current_account_gdp" :read [:value] :stale 30}
   {:name :fx-momentum12 :var "fast/fx" :read [:change 12] :stale 3}])

(def ^:private banking-banks
  "Banking crises (t126, bnm-ris): added to --theory-offset's own inputs
  (`with-own-inputs`), the banks' health where the literature's inputs are
  absent (WDI's NPLs, capital and liquid reserves, data/derived/
  banking-series.tsv, each year known at its first WDI edition) and the
  commodity bust that turned exporters' banking crises in 2014-16 (the
  country's commodity terms of trade, weighted by net exports to GDP, over
  12 and 24 months; reserves' and the currency's fall after it), with the
  credit boom before it (WDI's private credit, 186 countries)."
  [{:name :npl :var "banks/npl" :read [:value] :stale 36}
   {:name :npl-change12 :var "banks/npl" :read [:change 12] :stale 36}
   {:name :capital :var "banks/capital_assets" :read [:value] :stale 36}
   {:name :liquidity :var "banks/liquid_reserves" :read [:value] :stale 36}
   {:name :tot-change12 :var "trade/commodity_tot" :read [:change 12] :stale 9}
   {:name :tot-change24 :var "trade/commodity_tot" :read [:change 24] :stale 9}
   {:name :reserves-change12 :var "debt/reserves_months" :read [:change 12] :stale 30}
   {:name :fx-momentum12 :var "fast/fx" :read [:change 12] :stale 3}
   {:name :private-credit-change36 :var "finance/private_credit_gdp" :read [:change 36] :stale 30}])

(def ^:private banking-boom-bust
  "Banking crises (bnm-g5t.29): t126's inputs with the commodity terms of
  trade read as a boom and a bust apart. t126's one symmetric change fitted
  a rise as the risk (the 2003-08 booms before crises), so the 2014-15 fall
  read as safety for the exporters. The boom: the 36-month rise read a year
  before the origin; the bust: the fall from the 24 months' peak
  (bnm.stocks/drawdown), 0 when at or above it."
  (into [] (mapcat (fn [{:keys [name] :as d}]
                     (case name
                       :tot-change12 [{:name :tot-boom36 :var "trade/commodity_tot" :read [:change 36] :lag 12 :stale 9}]
                       :tot-change24 [{:name :tot-bust24 :var "trade/commodity_tot" :read [:drawdown 24] :stale 9}]
                       [d])))
        banking-banks))

(def aftermath-months
  "How long a banking crisis's aftermath lasts from its onset: eight years,
  Reinhart and Rogoff's (2014) mean time to recover pre-crisis income
  across 100 systemic crises."
  96)

(defn aftermath-decls
  "Banking crises (t129): `banking-boom-bust` with the bank-health inputs
  (NPLs, their change, capital, liquidity) read as 0 within
  `aftermath-months` of an onset in `onsets` (fn [unit] -> its onset
  months; a {unit #{month}} map does), and a flag
  for those months. t128's highest forecasts fell just after crises: NPLs
  stay high for years after one and are published late. Onsets only, known
  when they happen (the spans' ends are dated after the fact)."
  [onsets]
  (let [post? (fn [u t] (boolean (some #(and (<= % t) (< (- t %) aftermath-months)) (onsets u))))
        health #{:npl :npl-change12 :capital :liquidity}]
    (conj (mapv (fn [{:keys [name] :as d}]
                  (if (health name)
                    {:name name :expr [:mul (dissoc d :name) [:fn (fn [_ u t] (if (post? u t) 0.0 1.0))]]}
                    d))
                banking-boom-bust)
          {:name :post-crisis :expr [:fn (fn [_ u t] (if (post? u t) 1.0 0.0))]})))

(def ^:private banking-onsets
  (delay (:onsets (lb/catalogue (mh/read-jst "data/raw/slow/jst/jst.csv")))))

(def ^:private banking-aftermath
  (aftermath-decls (fn [u] (get @banking-onsets u))))

(def ^:private by-kind {"sovereign_default" sovereign "currency_crisis" currency "inflation_crisis" inflation
                        "inflation_crisis@monthly" inflation-monthly "currency_crisis@partners" currency-partners
                        "coup" coup "war_onset" war "fiscal_consolidation" fiscal "growth_collapse" growth
                        "banking_crisis@literature" banking-literature "banking_crisis@banks" banking-banks
                        "banking_crisis@boom-bust" banking-boom-bust
                        "banking_crisis@aftermath" banking-aftermath})

(defn decls "A kind's theory signals, or nil." [kind] (get by-kind kind))

(defn signs
  "A theory's declared sign per input (bnm-7yg.6, --theory-signs), or nil
  unless every input declares one."
  [kind]
  (let [ds (decls kind)]
    (when (and (seq ds) (every? :sign ds)) (mapv :sign ds))))

(def decay-stocks
  "t137 (reports/Crisis aftermath and trend reversal.md, rank 2): each
  theory's legacy stocks, the levels an onset leaves raised for years
  (debt, reserves, inflation, the spread and balance, a military
  executive), fixed before any run. Their slopes may change with time
  since the kind's last onset (with-theory :decay)."
  {"sovereign_default" [:external-debt :reserves :inflation]
   "currency_crisis" [:reserves :external-debt :inflation]
   "inflation_crisis" [:inflation]
   "fiscal_consolidation" [:government-debt :external-debt :spread :balance]
   "coup" [:military-executive :electoral-democracy]})

(defn decay-levels
  "The raw column indices of `decay-stocks` in theory `k`'s declarations,
  or nil."
  [k]
  (when-let [names (decay-stocks (kind-of k))]
    (let [ix (into {} (map-indexed (fn [i d] [(:name d) i]) (decls k)))]
      (mapv #(or (ix %) (throw (ex-info (str "bnm.kind-theory: " k " declares no " %) {:kind k :name %}))) names))))

(declare decl-vars)

(defn vars
  "The series the named kinds declare, for bnm.named/load-series' :vars."
  [kinds]
  (decl-vars (mapcat decls kinds)))

(defn- reads
  "Every read in a declaration: its own, its :times, and those inside an
  :expr, each {:var :unit} (a :times with no :unit takes the outer one)."
  [d]
  (letfn [(walk [e outer]
            (cond (map? e) (let [u (or (:unit e) outer)]
                             (cons {:var (:var e) :unit u} (when-let [t (:times e)] (walk t u))))
                  (vector? e) (when-not (= :fn (first e)) (mapcat #(walk % outer) (rest e)))
                  :else nil))]
    (filter :var (concat (walk (or (:expr d) d) nil) (mapcat #(walk % nil) (:reads d))))))

(defn decl-vars "The series declarations `ds` read." [ds] (set (map :var (mapcat reads ds))))

(defn transformed
  "`row` with each :log1p declaration's entry read as log(1 + x/100), x a
  percentage floored at -99 (inflation's thousands would otherwise set
  the standardizer's spread)."
  [ds row]
  (fn [u t]
    (when-let [r (row u t)]
      (mapv (fn [d x] (if (and x (:log1p d)) (Math/log1p (Math/max -0.99 (/ (double x) 100.0))) x)) ds r))))

(defn check-loaded
  "Throws naming every series `ds` read that the table `db` lacks (a fixed
  unit's own series, or any unit's for :own)."
  [ds db]
  (let [have (set (map second (keys db)))
        fixed (set (keys db))
        missing (distinct (for [{:keys [var unit]} (mapcat reads ds)
                                :when (if (string? unit) (not (contains? fixed [unit var])) (not (contains? have var)))]
                            var))]
    (when (seq missing)
      (throw (ex-info (str "bnm.kind-theory: declared series not loaded: " (str/join ", " missing)) {:missing missing})))))

(defn- members-fn [{:keys [levels parent]}]
  (let [countries (second (first levels))
        ancestors (fn [c] (set (take-while some? (iterate parent c))))
        anc (into {} (for [c countries] [c (ancestors c)]))]
    (memoize (fn [place] (if (= "world" place) countries (filter #(contains? (anc %) place) countries))))))

(defn shape
  "One input's shape columns (bnm-asp; the oracle study's A6): its
  standardized value z (0 when missing) and hinges max(0, z - k) at
  k = -1, 0, 1, so the fit can bend its response, and a flag that the raw
  value `x` was missing, so missing is its own category."
  [x z]
  (let [v (double (or z 0.0))]
    [v (max 0.0 (+ v 1.0)) (max 0.0 v) (max 0.0 (- v 1.0)) (if (nil? x) 1.0 0.0)]))

(defn column-penalties
  "The fit's precision multipliers (bnm.theory/fits :penalties) from one
  per raw input: the intercept's 1 first, each input's repeated over its
  five shape columns when shaped, and the share missing at 1 when imputed."
  [per-input {:keys [shapes impute]}]
  (let [ps (cond-> (vec per-input) impute (conj 1.0))]
    (into [1.0] (mapcat #(repeat (if shapes 5 1) (double %)) ps))))

(defn driver-penalties
  "The global layer's multipliers, one per input (t124): `p` for a world
  driver (no exposure), 1 for a channel times an exposure."
  [p]
  (mapv #(if (or (:times %) (:expr %)) 1.0 (double p)) global))

(defn with-theory
  "`sp` with its year node's offset the theory's: `row` (fn [unit origin])
  -> `width` raw inputs (nil for missing), each standardized by
  bnm.theory/place-z (at least 20 earlier values), the logit refit by
  bnm.theory/offsets on the spec's year target; an aggregate takes the
  chance any member has one. Other scales keep the spec's offset.
  opts :shapes true (bnm-asp): each input as `shape`'s five columns.
  :impute true (bnm-s6m): missing inputs filled by bnm.impute (the
  last value decayed toward the region's mean) with the share missing as
  one more input. :add true (t112): the offset kept and only the theory's slopes,
  b.z past the intercept, added to a country's year log odds through
  :offset-add; the level stays the lattice's. :raw true (t121): the
  slopes' priors on each column's own scale (bnm.theory/fits).
  :penalties (t124): a precision multiplier per raw input.
  :decay {:d (fn [unit origin]) :levels [raw index]} (t137): after the
  inputs, d (the decay since the kind's last known onset, 0 when none) and
  each listed input's standardized value times d, so the slope on a legacy
  stock changes with time since the onset (a non-proportional hazard) and
  the aftermath has its own level. :precision p (t138): the interactions'
  prior precision p times the default's, so a few post-onset rows cannot
  give them a large slope; d and the inputs at the default. :level false
  (t139): no d column, only the stocks times d, so the year node's history
  gauges keep the aftermath's level and it is not counted twice.
  :signs (bnm-7yg.6): one of -1, 0, +1 per raw input; a slope fit against
  its sign is held at zero (bnm.theory/fits :signs); the imputed missing
  share and the decay columns are free. Not with :shapes. :half-life h
  (bnm-7yg.6): the fit weights each row by 0.5^(age / h years)
  (bnm.theory/fits)."
  ([sp row width] (with-theory sp row width {}))
  ([{:keys [scales levels start end target offset parent] :as sp} row width {:keys [add impute shapes raw penalties decay signs half-life]}]
  (when (and signs shapes)
    (throw (ex-info "bnm.kind-theory: :signs takes no :shapes: each input is five shape columns" {})))
  (when (and signs (not= width (count signs)))
    (throw (ex-info "bnm.kind-theory: one sign per raw input" {:width width :signs signs})))
  (when (and add decay)
    (throw (ex-info "bnm.kind-theory: :decay goes with a theory that sets the offset, not :add" {})))
  (when (and add shapes)
    (throw (ex-info "bnm.kind-theory: :add takes no :shapes: the shape columns are not centred, so the dropped intercept lifts every row" {})))
  (let [countries (second (first levels))
        per (:per (first (filter #(= :year (:name %)) scales)))
        os (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
        ;; bnm-s6m: missing inputs filled (bnm.impute), the share missing a column more
        pens (cond penalties (column-penalties penalties {:shapes shapes :impute impute})
                   ;; t138: the decay's interactions held tighter, all else at the default
                   (:precision decay) (column-penalties (repeat width 1.0) {:shapes shapes :impute impute}))
        [row width] (if impute [(im/impute row width countries (vec os) parent) (inc width)] [row width])
        zs (vec (for [i (range width)] (th/place-z (fn [u t] (nth (row u t) i)) countries os 20)))
        inputs (if shapes
                 (fn [u t] (let [r (row u t)] (vec (mapcat (fn [i z] (shape (some-> r (nth i)) (z u t))) (range width) zs))))
                 (fn [u t] (mapv #(% u t) zs)))
        level? (not (false? (:level decay)))
        inputs (if-let [{:keys [d levels]} decay]
                 (fn [u t] (let [dv (double (or (d u t) 0.0))
                                 xs (map #(* dv (double (or ((zs %) u t) 0.0))) levels)]
                             (into (inputs u t) (if level? (cons dv xs) xs))))
                 inputs)
        pens (if (and pens decay)
               (into (cond-> pens level? (conj 1.0)) (repeat (count (:levels decay)) (double (or (:precision decay) 1.0))))
               pens)
        ;; x[0] the intercept's; the missing share and the decay's columns free
        sv (when signs
             (vec (concat [0] signs (when impute [0])
                          (when-let [{:keys [levels]} decay] (repeat (+ (count levels) (if level? 1 0)) 0)))))
        args (cond-> {:places countries :origins os :prior 1.0 :inputs inputs :raw (boolean raw) :penalties pens
                      :target (fn [u t] (target :year u t))}
               sv (assoc :signs sv)
               half-life (assoc :half-life half-life))]
    (if add
      (let [_ (when (:offset-add sp)
                (throw (ex-info "bnm.kind-theory: :add, but the spec already has an :offset-add" {})))
            fits (th/fits args)
            cs (set countries)]
        (assoc sp
               :offset-add (fn [scale place t]
                             (when (and (= :year scale) (cs place))
                               (when-let [^doubles b (get fits t)]
                                 (reduce + 0.0 (map-indexed (fn [i z] (* (aget b (inc i)) (double (or z 0.0)))) (inputs place t))))))
               :kind-theory {:fits fits :inputs inputs :add true :raw (boolean raw) :penalties pens :signs sv :half-life half-life}))
      (let [off (th/offsets args)
            members (members-fn sp)]
        (assoc sp
               :offset (fn [scale place t]
                         (if (= :year scale)
                           (th/any-of (keep #(get off [% t]) (members place)))
                           (when offset (offset scale place t))))
               :kind-theory {:offsets off :inputs inputs :raw (boolean raw) :penalties pens :signs sv :half-life half-life}))))))

(defn with-own-inputs
  "[row width] for a spec whose year node already reads a theory
  (banking's --theory-offset, its :theory {:inputs :width}): that theory's
  inputs first (nil each where it has none), then `row`'s `width`."
  [sp row width]
  (let [{own :inputs w :width} (:theory sp)]
    (when-not (and own w)
      (throw (ex-info "bnm.kind-theory: no theory to extend (banking's needs --theory-offset)" {})))
    [(fn [u t] (let [o (own u t) r (row u t)]
                 (when (and o (not= w (count o)))
                   (throw (ex-info "bnm.kind-theory: the theory's inputs are not its recorded width" {:width w :got (count o)})))
                 (when (or o r)
                   (into (if o (vec o) (vec (repeat w nil))) (or r (repeat width nil))))))
     (+ w width)]))

(defn with-kind-theory
  "The named kinds' specs with their declared theory (`decls`) read from
  the slow table `db`; those in `add` on top of their offset (t112). The
  last argument, a map: :signed, the kinds whose slopes keep their
  declared signs; :half-lives, {kind years} for the theory fit's memory
  (bnm-7yg.6)."
  ([specs kinds db] (with-kind-theory specs kinds db #{}))
  ([specs kinds db add] (with-kind-theory specs kinds db add false))
  ([specs kinds db add impute] (with-kind-theory specs kinds db add impute false))
  ([specs kinds db add impute shapes] (with-kind-theory specs kinds db add impute shapes false))
  ([specs kinds db add impute shapes raw] (with-kind-theory specs kinds db add impute shapes raw nil))
  ([specs kinds db add impute shapes raw decay] (with-kind-theory specs kinds db add impute shapes raw decay nil))
  ([specs kinds db add impute shapes raw decay decay-precision] (with-kind-theory specs kinds db add impute shapes raw decay decay-precision true))
  ([specs kinds db add impute shapes raw decay decay-precision decay-level] (with-kind-theory specs kinds db add impute shapes raw decay decay-precision decay-level {}))
  ([specs kinds db add impute shapes raw decay decay-precision decay-level {:keys [signed half-lives] :or {signed #{} half-lives {}}}]
  (reduce (fn [acc k]
            (let [ds (or (decls k) (throw (ex-info (str "bnm.kind-theory: no theory declared for " k) {:kind k})))]
              (check-loaded ds db)
              (let [kd (kind-of k)]
                (if (contains? acc kd)
                  (let [sp (get acc kd)
                        row (transformed ds (:row (sg/assemble db ds)))
                        ;; banking's variant extends --theory-offset's theory
                        [row width] (if (= "banking_crisis" kd) (with-own-inputs sp row (count ds)) [row (count ds)])]
                    (assoc acc kd (with-theory sp row width
                                    {:add (contains? add kd) :impute (if (set? impute) (contains? impute kd) impute)
                                     :shapes (if (set? shapes) (contains? shapes kd) shapes)
                                     :raw (if (set? raw) (contains? raw kd) raw)
                                     :decay (when-let [d (get decay kd)]
                                              (cond-> {:d d :levels (decay-levels k)}
                                                decay-precision (assoc :precision decay-precision)
                                                (false? decay-level) (assoc :level false)))
                                     :signs (when (contains? signed kd)
                                              (or (signs k) (throw (ex-info (str "bnm.kind-theory: no signs declared for " k) {:kind k}))))
                                     :half-life (get half-lives kd)})))
                  acc))))
          specs kinds)))

(defn with-global-theory-row
  "`with-global-theory`'s core on a given row: opts :impute, :shapes and
  :raw (raw-scale priors, t123), each true or a set of kinds;
  :driver-precision (t124), the world drivers' precision multiplier."
  [specs kinds row {:keys [impute shapes raw driver-precision]}]
  (let [on (fn [o k] (if (set? o) (contains? o k) (boolean o)))]
    (reduce (fn [acc k]
              (when-not (global-kinds k)
                (throw (ex-info (str "bnm.kind-theory: the global layer is for the economic kinds, not " k) {:kind k})))
              (if (contains? acc k)
                (update acc k with-theory row (count global) (cond-> {:add true :impute (on impute k) :shapes (on shapes k) :raw (on raw k)}
                                                         driver-precision (assoc :penalties (driver-penalties driver-precision))))
                acc))
            specs kinds)))

(defn with-global-theory
  "The named kinds' specs with the global layer (`global`) added on top of
  their offset (with-theory :add): its slopes refit at each origin, the
  drivers moving every country's level together and the interactions
  moving the exposed ones. Goes with a kind's own theory, which sets the
  offset. impute, shapes and raw: true, false or a set of kinds."
  ([specs kinds db] (with-global-theory specs kinds db false))
  ([specs kinds db impute] (with-global-theory specs kinds db impute false))
  ([specs kinds db impute shapes] (with-global-theory specs kinds db impute shapes false))
  ([specs kinds db impute shapes raw] (with-global-theory specs kinds db impute shapes raw nil))
  ([specs kinds db impute shapes raw driver-precision]
   (check-loaded global db)
   (with-global-theory-row specs kinds (:row (sg/assemble db global)) {:impute impute :shapes shapes :raw raw :driver-precision driver-precision})))

