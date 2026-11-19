(ns bnm.kind-theory
  "A theory forecast as a kind's year-node offset (bnm-1dw), banking's
  design (bnm.lattice-banking --theory-offset) for the kinds built by
  bnm.lattice-political: a pooled logit on stocks and flows read at the
  origin, refit at each December origin on the rows known by then
  (bnm.theory), its inputs standardized against every country's values at
  earlier origins. The level then comes from what is read at the origin,
  not from lagged outcomes (docs/INVARIANTS.md point 4)."
  (:require [bnm.signals :as sg]
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

(def ^:private currency
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

(def ^:private by-kind {"sovereign_default" sovereign "currency_crisis" currency "inflation_crisis" inflation})

(defn decls "A kind's theory signals, or nil." [kind] (get by-kind kind))

(defn vars
  "The series the named kinds declare, for bnm.named/load-series' :vars."
  [kinds]
  (set (for [k kinds d (decls k) v (cons (:var d) (some-> (:times d) :var list)) :when v] v)))

(defn transformed
  "`row` with each :log1p declaration's entry read as log(1 + x/100), x a
  percentage floored at -99 (inflation's thousands would otherwise set
  the standardizer's spread)."
  [ds row]
  (fn [u t]
    (when-let [r (row u t)]
      (mapv (fn [d x] (if (and x (:log1p d)) (Math/log1p (Math/max -0.99 (/ (double x) 100.0))) x)) ds r))))

(defn- check-loaded [ds db]
  (let [have (set (map second (keys db)))
        fixed (set (keys db))
        missing (for [d ds :let [u (:unit d)]
                      :when (if (string? u) (not (contains? fixed [u (:var d)])) (not (contains? have (:var d))))]
                  (:var d))]
    (when (seq missing)
      (throw (ex-info (str "bnm.kind-theory: declared series not loaded: " (str/join ", " missing)) {:missing missing})))))

(defn- members-fn [{:keys [levels parent]}]
  (let [countries (second (first levels))
        ancestors (fn [c] (set (take-while some? (iterate parent c))))
        anc (into {} (for [c countries] [c (ancestors c)]))]
    (memoize (fn [place] (if (= "world" place) countries (filter #(contains? (anc %) place) countries))))))

(defn with-theory
  "`sp` with its year node's offset the theory's: `row` (fn [unit origin])
  -> `width` raw inputs (nil for missing), each standardized by
  bnm.theory/place-z (at least 20 earlier values), the logit refit by
  bnm.theory/offsets on the spec's year target; an aggregate takes the
  chance any member has one. Other scales keep the spec's offset.
  opts :add true (t112): the theory on top of the year offset, refit
  holding the country's offset fixed (bnm.theory/offsets :base)."
  ([sp row width] (with-theory sp row width {}))
  ([{:keys [scales levels start end target offset] :as sp} row width {:keys [add]}]
  (let [countries (second (first levels))
        per (:per (first (filter #(= :year (:name %)) scales)))
        os (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
        zs (vec (for [i (range width)] (th/place-z (fn [u t] (nth (row u t) i)) countries os 20)))
        off (th/offsets {:places countries :origins os :prior 1.0
                         :inputs (fn [u t] (mapv #(% u t) zs))
                         :target (fn [u t] (target :year u t))
                         :base (when add (fn [u t] (when offset (offset :year u t))))})
        _ (when (and add (empty? off))
            (throw (ex-info "bnm.kind-theory: :add, but the spec has no year offset to add to" {})))
        members (members-fn sp)]
    (assoc sp
           :offset (fn [scale place t]
                     (if (= :year scale)
                       (th/any-of (keep #(get off [% t]) (members place)))
                       (when offset (offset scale place t))))
           :kind-theory {:offsets off :inputs (fn [u t] (mapv #(% u t) zs)) :add (boolean add)}))))

(defn with-kind-theory
  "The named kinds' specs with their declared theory (`decls`) read from
  the slow table `db`; those in `add` on top of their offset (t112)."
  ([specs kinds db] (with-kind-theory specs kinds db #{}))
  ([specs kinds db add]
  (reduce (fn [acc k]
            (let [ds (or (decls k) (throw (ex-info (str "bnm.kind-theory: no theory declared for " k) {:kind k})))]
              (check-loaded ds db)
              (if (contains? acc k)
                (update acc k with-theory (transformed ds (:row (sg/assemble db ds))) (count ds) {:add (contains? add k)})
                acc)))
          specs kinds)))
