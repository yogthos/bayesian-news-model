(ns bnm.named-decl-test
  "bnm.named's builders moved onto declarations (docs/SIGNALS.md step 3):
  each must give, on the slow table itself, exactly what the hand-built
  version gave (kept here verbatim as the reference)."
  (:require [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(def ^:private quote-stale
  {"finance/spread_baa" 3 "finance/curve_10y3m" 3 "finance/vix" 3
   "fao/food" 4 "fao/cereals" 4 "fao/oils" 4 "pinksheet/fertiliser" 4})

(defn- getter
  ([db unit] (getter db unit {}))
  ([db unit stale]
   (fn
     ([v t] (some-> (db [unit v]) (sk/value t {:stale (get stale v 30)})))
     ([v t months] (some-> (db [unit v]) (sk/change t months {:stale (get stale v 30)}))))))

(defn- x* [a b] (when (and a b) (* (double a) (double b))))

(defn- ref-leading
  "bnm.named/leading's reader before the move, verbatim."
  [db]
  (fn [unit t]
    (let [us (getter db "USA" quote-stale)
          wq (getter db "world" quote-stale)
          w (getter db "world")
          g (getter db unit)
          food12 (w "food/price_index" t 12)]
      [(us "finance/spread_baa" t 12) (us "finance/curve_10y3m" t) (wq "finance/vix" t 12)
       food12 (x* food12 (g "food/imports_share" t))
       (wq "pinksheet/fertiliser" t 12) (wq "fao/food" t 6) (wq "fao/cereals" t 6) (wq "fao/oils" t 6)])))

(def ^:private series-path "data/slow/series.tsv")

(deftest leading-from-declarations-is-the-hand-built-reader
  (if-not (.exists (io/file series-path))
    (println "leading-from-declarations-is-the-hand-built-reader: no" series-path ", skipped")
    (let [db (merge (nm/load-series series-path (st/read-variables "data/slow/variables.tsv") {:real-lags true})
                    (nm/load-food2))
          old (ref-leading db)
          [names new] (nm/leading db)
          units (sort (distinct (map first (filter #(= "food/imports_share" (second %)) (keys db)))))]
      (is (= nm/leading-names names))
      (is (< 50 (count units)))
      (doseq [u (cons "XXX" units) t (range (* 12 1990) (* 12 2026) 3)]
        (is (= (old u t) (new u t)) [u t])))))

;; --- financial, terms of trade, fx, structure: the hand-built readers -------

(defn- minus [a b] (when (and a b) (- (double a) (double b))))
(defn- mean-of [xs] (when (seq xs) (/ (reduce + xs) (count xs))))

(defn- ref-financial [db wc]
  (fn [unit t]
    (let [g (getter db unit)
          w (getter db "world")
          us (getter db "USA")
          credit3 (g "finance/credit_gdp" t 36)
          hh3 (g "finance/household_credit_gdp" t 36)
          house3 (g "finance/real_house_prices" t 36)
          equity3 (g "markets/share_prices" t 36)
          runup2 (g "markets/share_prices" t 24)
          vol (some-> (db [unit "markets/share_prices"]) (sk/volatility t 12 {:stale 6}))
          open (some-> (g "trade/openness" t) (/ 100.0))
          vix (w "finance/vix" t)
          us-curve (us "finance/curve_10y3m" t)]
      [credit3 hh3 (g "finance/credit_gap" t) (g "finance/dsr_private" t 36) house3
       (x* hh3 house3) (x* credit3 equity3)
       equity3 runup2 vol (x* runup2 vol)
       (minus (g "finance/yield_long" t) (g "finance/rate_3m" t))
       (wc t) vix us-curve
       (x* (wc t) open) (x* (some-> vix -) credit3) (x* us-curve open)])))

(defn- ref-tot [db]
  (fn [unit t]
    (let [g (getter db unit)
          tot12 (g "trade/commodity_tot" t 12)
          vol (some-> (db [unit "trade/commodity_tot"]) (sk/volatility t 12 {:stale 6}))
          primary (let [a (g "economy/agriculture_share" (- t 24)) r (g "energy/resource_rents_gdp" (- t 24))]
                    (when (or a r) (/ (+ (or a 0.0) (or r 0.0)) 100.0)))]
      [tot12 (g "trade/commodity_tot" t 3) (some-> tot12 (Math/min 0.0)) vol (x* vol primary)])))

(defn- ref-fx [db]
  (fn [unit t]
    (let [g (getter db unit)
          peg (g "finance/fx_peg" t)
          vol (some-> (db [unit "trade/commodity_tot"]) (sk/volatility t 12 {:stale 6}))
          primary (let [a (g "economy/agriculture_share" (- t 24)) r (g "energy/resource_rents_gdp" (- t 24))]
                    (when (or a r) (/ (+ (or a 0.0) (or r 0.0)) 100.0)))]
      [peg (g "finance/fx_falling" t) (g "finance/anchor_usd" t) (g "finance/anchor_euro" t)
       (g "finance/anchor_gbp" t) (g "finance/anchor_cny" t) (g "finance/anchor_rub" t)
       (x* vol (x* peg primary))])))

(defn- ref-structure [db units months]
  (let [lag 24
        median-yield (into {} (for [t months]
                                [t (let [ys (sort (keep #(some-> (db [% "food/cereal_yield"]) (sk/value (- t lag) {:stale 30})) units))]
                                     (when (seq ys) (nth ys (quot (count ys) 2))))]))]
    (fn [unit t]
      (let [g (getter db unit)
            w (getter db "world")
            share #(some-> (g % (- t lag)) (/ 100.0))
            food12 (w "food/price_index" t 12)
            oil12 (w "energy/oil_brent" t 12)
            metals12 (mean-of (keep #(w % t 12) ["commodities/copper" "commodities/aluminium" "commodities/nickel"]))
            agri (share "economy/agriculture_share") manuf (share "economy/manufacturing_share")
            metal-x (share "trade/metal_exports_share") food-x (share "food/exports_share")
            rents (share "energy/resource_rents_gdp")
            low-yield (when-let [y (g "food/cereal_yield" (- t lag))]
                        (when-let [m (median-yield t)] (- m y)))]
        [agri manuf (share "economy/services_share") (share "energy/fuel_exports_share") food-x metal-x
         (share "technology/hightech_exports_share")
         (x* food12 agri) (x* food12 food-x) (x* metals12 metal-x) (x* oil12 rents) (x* oil12 manuf)
         (x* (x* food12 (share "food/imports_share")) low-yield)]))))

(deftest the-other-builders-from-declarations
  (if-not (.exists (io/file series-path))
    (println "the-other-builders-from-declarations: no" series-path ", skipped")
    (let [db (nm/load-series series-path (st/read-variables "data/slow/variables.tsv") {:real-lags true})
          units (sort (distinct (map first (filter #(= "trade/commodity_tot" (second %)) (keys db)))))
          months (vec (range (+ (* 12 1985) 2) (* 12 2026) 3))
          wc (nm/world-credit db (set units) months)
          check (fn [label old [names new] expect-names]
                  (is (= expect-names names) label)
                  (doseq [u (cons "XXX" units) t months]
                    (is (= (old u t) (new u t)) [label u t])))]
      (is (< 50 (count units)))
      (check :financial (ref-financial db wc) (nm/financial db wc) (first (nm/financial nil nil)))
      (check :tot (ref-tot db) (nm/terms-of-trade db units months) nm/tot-names)
      (check :fx (ref-fx db) (nm/fx db units months) nm/fx-names)
      (check :structure (ref-structure db units months) (nm/structure db units months) nm/structure-names))))

;; --- political, political-economy, institutions, youth ----------------------

(defn- ref-political [db]
  (fn [unit t]
    (let [g (getter db unit)
          w (getter db "world")
          food12 (w "food/price_index" t 12)
          oil12 (w "energy/oil_brent" t 12)
          fragility (g "people/infant_mortality" t)]
      [(g "food/food_inflation" t) food12 (x* food12 (g "food/imports_share" t)) (x* food12 fragility) fragility
       (x* oil12 (g "energy/fuel_imports_share" t)) (x* oil12 (g "energy/fuel_exports_share" t))
       (g "economy/gdp_growth" t) (g "economy/inflation" t) (g "people/youth_unemployment" t)
       (g "people/population" t) (g "climate/temperature_anomaly" t) (g "climate/precipitation_anomaly" t)
       (g "politics/protest" t 3) (g "politics/protest" t 12) (g "politics/violence" t 3)
       (g "politics/violence" t 12) (g "politics/coercion" t 12)])))

(defn- ref-political-economy [db units months {:keys [stale]}]
  (let [world-growth (into {} (for [t months]
                                [t (mean-of (keep #(some-> (db [% "economy/gdp_per_capita"]) (sk/change t 60 {:stale 30})) units))]))
        core-profit (into {} (for [t months]
                               [t (mean-of (keep #(some-> (db [% "economy/profit_rate"]) (sk/change t 36 {:stale (get stale "economy/profit_rate" 30)})) nm/core))]))]
    (fn [unit t]
      (let [g (getter db unit stale)
            growth (g "economy/gdp_per_capita" t 60)
            cp (core-profit t)
            rents (some-> (g "energy/resource_rents_gdp" t) (/ 100.0))]
        [(g "economy/profit_rate" t) (g "economy/profit_rate" t 36) (g "economy/labor_share" t 36)
         (minus growth (world-growth t)) cp (x* rents (some-> cp -))]))))

(defn- ref-institutions [db]
  (fn [unit t]
    (let [g (getter db unit)
          w (getter db "world")
          ge (g "state/government_effectiveness" (- t 12))
          weak (when ge (Math/max 0.0 (- (double ge))))
          oil (some-> (g "energy/oil_rents_gdp" (- t 24)) (/ 100.0))
          mineral (some-> (g "energy/mineral_rents_gdp" (- t 24)) (/ 100.0))
          oil12 (w "energy/oil_brent" t 12)
          metals12 (mean-of (keep #(w % t 12) ["commodities/copper" "commodities/aluminium" "commodities/nickel"]))]
      [ge (x* oil weak) (x* mineral weak) (x* oil12 (x* oil weak)) (x* metals12 (x* mineral weak))])))

(defn- ref-youth [db]
  (fn [unit t]
    (let [g (getter db unit)
          tert (some-> (g "people/tertiary_enrolment" (- t 24)) (/ 100.0))
          tert5 (some-> (g "people/tertiary_enrolment" (- t 24) 60) (/ 100.0))
          yu (some-> (g "people/youth_unemployment" (- t 12)) (/ 100.0))
          urban (some-> (g "people/urban_share" (- t 12)) (/ 100.0))]
      [tert tert5 (x* yu urban) (x* (some-> tert5 (Math/max 0.0)) (x* yu urban))])))

(deftest the-political-builders-from-declarations
  (if-not (.exists (io/file series-path))
    (println "the-political-builders-from-declarations: no" series-path ", skipped")
    (let [db (nm/load-series series-path (st/read-variables "data/slow/variables.tsv") {:real-lags true})
          units (sort (distinct (map first (filter #(= "people/infant_mortality" (second %)) (keys db)))))
          months (vec (range (+ (* 12 1985) 2) (* 12 2026) 3))
          check (fn [label old [names new] expect-names]
                  (is (= expect-names names) label)
                  (doseq [u (cons "XXX" units) t months]
                    (is (= (old u t) (new u t)) [label u t])))]
      (is (< 50 (count units)))
      (check :political (ref-political db) (nm/political db) (first (nm/political nil)))
      (doseq [opts [{} {:stale nm/publication-stale} {:stale (merge nm/publication-stale (select-keys nm/lag-stale ["economy/profit_rate" "economy/labor_share"]))}]]
        (check [:political-economy opts] (ref-political-economy db units months opts) (nm/political-economy db units months opts) nm/political-economy-names))
      (check :institutions (ref-institutions db) (nm/institutions db units months) nm/institutions-names)
      (check :youth (ref-youth db) (nm/youth db units months) nm/youth-names))))
