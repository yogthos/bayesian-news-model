(ns bnm.kind-theory
  "A theory forecast as a kind's year-node offset (bnm-1dw), banking's
  design (bnm.lattice-banking --theory-offset) for the kinds built by
  bnm.lattice-political: a pooled logit on stocks and flows read at the
  origin, refit at each December origin on the rows known by then
  (bnm.theory), its inputs standardized against every country's values at
  earlier origins. The level then comes from what is read at the origin,
  not from lagged outcomes (docs/INVARIANTS.md point 4)."
  (:require [bnm.impute :as im]
            [bnm.signals :as sg]
            [bnm.theory :as th]
            [clojure.string :as str]))

(def ^:private sovereign
  "Sovereign defaults, fixed before any run (docs/CALIBRATION.md t109):
  the early-warning literature's country inputs (Manasse and Roubini 2009,
  rules of thumb: external debt, reserves, inflation, growth; Reinhart and
  Rogoff 2009) and the global push, US monetary tightening (the Volcker
  defaults of 1982-83, 2022's)."
  [{:name :external-debt :var "debt/external_gni" :read [:value] :stale 30}
   {:name :reserves :var "debt/reserves_months" :read [:value] :stale 30}
   {:name :inflation :var "economy/inflation" :read [:value] :stale 30}
   {:name :growth :var "economy/gdp_growth" :read [:value] :stale 30}
   {:name :us-rate-change24 :var "finance/policy_rate" :unit "USA" :read [:change 24] :stale 3}])

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
  ["data/slow/series.tsv" "data/derived/global-monthly.tsv" "data/derived/rer-annual.tsv" "data/fast/series.tsv"])

(def ^:private path-prefix
  {"data/derived/global-monthly.tsv" "global/" "data/derived/rer-annual.tsv" "derived/rer_" "data/fast/series.tsv" "fast/"})

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

(def ^:private by-kind {"sovereign_default" sovereign "currency_crisis" currency "inflation_crisis" inflation})

(defn decls "A kind's theory signals, or nil." [kind] (get by-kind kind))

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
    (filter :var (walk (or (:expr d) d) nil))))

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
  :offset-add; the level stays the lattice's."
  ([sp row width] (with-theory sp row width {}))
  ([{:keys [scales levels start end target offset parent] :as sp} row width {:keys [add impute shapes]}]
  (let [countries (second (first levels))
        per (:per (first (filter #(= :year (:name %)) scales)))
        os (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
        ;; bnm-s6m: missing inputs filled (bnm.impute), the share missing a column more
        [row width] (if impute [(im/impute row width countries (vec os) parent) (inc width)] [row width])
        zs (vec (for [i (range width)] (th/place-z (fn [u t] (nth (row u t) i)) countries os 20)))
        inputs (if shapes
                 (fn [u t] (let [r (row u t)] (vec (mapcat (fn [i z] (shape (some-> r (nth i)) (z u t))) (range width) zs))))
                 (fn [u t] (mapv #(% u t) zs)))
        args {:places countries :origins os :prior 1.0 :inputs inputs
              :target (fn [u t] (target :year u t))}]
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
               :kind-theory {:fits fits :inputs inputs :add true}))
      (let [off (th/offsets args)
            members (members-fn sp)]
        (assoc sp
               :offset (fn [scale place t]
                         (if (= :year scale)
                           (th/any-of (keep #(get off [% t]) (members place)))
                           (when offset (offset scale place t))))
               :kind-theory {:offsets off :inputs inputs}))))))

(defn with-kind-theory
  "The named kinds' specs with their declared theory (`decls`) read from
  the slow table `db`; those in `add` on top of their offset (t112)."
  ([specs kinds db] (with-kind-theory specs kinds db #{}))
  ([specs kinds db add] (with-kind-theory specs kinds db add false))
  ([specs kinds db add impute] (with-kind-theory specs kinds db add impute false))
  ([specs kinds db add impute shapes]
  (reduce (fn [acc k]
            (let [ds (or (decls k) (throw (ex-info (str "bnm.kind-theory: no theory declared for " k) {:kind k})))]
              (check-loaded ds db)
              (if (contains? acc k)
                (update acc k with-theory (transformed ds (:row (sg/assemble db ds))) (count ds) {:add (contains? add k) :impute (if (set? impute) (contains? impute k) impute) :shapes shapes})
                acc)))
          specs kinds)))

(defn with-global-theory
  "The named kinds' specs with the global layer (`global`) added on top of
  their offset (with-theory :add): its slopes refit at each origin, the
  drivers moving every country's level together and the interactions
  moving the exposed ones. Goes with a kind's own theory, which sets the
  offset."
  ([specs kinds db] (with-global-theory specs kinds db false))
  ([specs kinds db impute]
  (check-loaded global db)
  (let [row (:row (sg/assemble db global))]
    (reduce (fn [acc k]
              (when-not (global-kinds k)
                (throw (ex-info (str "bnm.kind-theory: the global layer is for the economic kinds, not " k) {:kind k})))
              (if (contains? acc k) (update acc k with-theory row (count global) {:add true :impute (if (set? impute) (contains? impute k) impute)}) acc))
            specs kinds))))

