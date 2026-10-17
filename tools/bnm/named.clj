(ns bnm.named
  "Named inputs for the onset hazards: the specific stocks the literature
  finds before each kind of event (docs/RESEARCH.md, \"Onset hazards\"), read
  point in time from data/slow/series.tsv, in place of per-domain averages.

  financial (banking crises, market crashes)
    credit to GDP, household credit to GDP and real house prices over three
    years, and the R-zones they form together (Greenwood, Hanson, Shleifer &
    Sørensen 2022); the credit gap; the change in debt service; share prices
    over three and two years, their volatility and the run-up times its
    volatility (Greenwood, Shleifer & You 2019); the country's yield curve
    (Bluwstein et al. 2023); credit growth in the rest of the world, the VIX
    and the US curve (Cesa-Bianchi et al. 2017).

  political (conflict and war onsets, protest and violence surges)
    food inflation, world food prices over a year, alone and through food
    imports and fragility (infant mortality); oil prices through fuel imports
    and exports; growth, inflation, youth unemployment, population; the
    climate anomalies; and how fast the protest, violence and coercion
    channels have been rising.

  Missing values are 0, with an indicator per source so the hazard can tell
  absent from neutral."
  (:require [bnm.stocks :as sk]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [bnm.slow-train :as st]))

(def vars
  #{"finance/credit_gdp" "finance/household_credit_gdp" "finance/credit_gap" "finance/dsr_private"
    "finance/real_house_prices" "markets/share_prices" "finance/yield_long" "finance/rate_3m"
    "finance/vix" "finance/curve_10y3m" "trade/openness"
    "food/food_inflation" "food/price_index" "food/imports_share" "people/infant_mortality"
    "energy/oil_brent" "energy/fuel_imports_share" "energy/fuel_exports_share"
    "economy/gdp_growth" "economy/inflation" "people/youth_unemployment" "people/population"
    "climate/temperature_anomaly" "climate/precipitation_anomaly"
    "politics/protest" "politics/violence" "politics/coercion"})

(defn load-series
  "{[unit var] series} for `vars`, values transformed as data/slow/variables.tsv says."
  [series-path variables]
  (let [tr (into {} (for [v variables] [(:name v) (:transform v)]))
        acc (java.util.HashMap.)]
    (with-open [r (io/reader series-path)]
      (doseq [line (line-seq r)
              :let [[unit v _ pend avail value] (str/split line #"\t" -1)]
              :when (vars v)]
        (let [x (parse-double value)
              z (case (tr v) "log" (Math/log (Math/max x 1e-12)) "log1p" (Math/log1p (Math/max x 0.0)) x)
              k [unit v]]
          (.put acc k (conj (or (.get acc k) []) [(st/month-of pend) (st/month-of avail) z])))))
    (into {} (for [[k es] acc] [k (sk/series es)]))))

(defn- getter [db unit]
  (fn
    ([v t] (some-> (db [unit v]) (sk/value t {:stale 30})))
    ([v t months] (some-> (db [unit v]) (sk/change t months {:stale 30})))))

(defn- x* [a b] (when (and a b) (* (double a) (double b))))
(defn- minus [a b] (when (and a b) (- (double a) (double b))))

(defn world-credit
  "Mean three-year change in credit to GDP over the countries with data, per month."
  [db units months]
  (into {} (for [t months]
             (let [cs (keep #(some-> (db [% "finance/credit_gdp"]) (sk/change t 36 {:stale 12})) units)]
               [t (when (seq cs) (/ (reduce + cs) (count cs)))]))))

(defn financial
  "[names f]: f gives the financial inputs for a unit at month t, nils for missing."
  [db wc]
  (let [names [:credit3 :hh-credit3 :credit-gap :dsr3 :house3 :rzone-hh :rzone-biz
               :equity3 :runup2 :equity-vol :runup-x-vol :curve
               :world-credit3 :vix :us-curve
               ;; global conditions through exposure (Cesa-Bianchi et al. 2017)
               :world-credit-x-open :vix-x-credit3 :us-curve-x-open]]
    [names
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
          (x* (wc t) open) (x* (some-> vix -) credit3) (x* us-curve open)]))]))

(defn political
  "[names f] for the political kinds."
  [db]
  (let [names [:food-inflation :world-food12 :food12-x-imports :food12-x-fragility :fragility
               :oil12-x-imports :oil12-x-exports :gdp-growth :inflation :youth-unemployment :population
               :temperature :precipitation :protest3 :protest12 :violence3 :violence12 :coercion12]]
    [names
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
          (g "politics/violence" t 12) (g "politics/coercion" t 12)]))]))

(def presence
  "Groups of inputs that share a source: one indicator each."
  {:financial [[:credit3 :hh-credit3 :credit-gap :dsr3] [:house3] [:equity3 :runup2 :equity-vol] [:curve]]
   :political [[:food-inflation] [:fragility] [:gdp-growth :inflation] [:youth-unemployment]
               [:temperature :precipitation] [:protest3 :violence3]]})

(defn vectorize
  "Inputs as doubles, missing as 0, then one presence indicator per group:
  [labels f] with f of the raw values."
  [names family]
  (let [groups (presence family)
        pos (zipmap names (range))]
    [(into (vec names) (for [g groups] (keyword (str (name (first g)) "-present"))))
     (fn [vals]
       (double-array (concat (map #(if % (double %) 0.0) vals)
                             (for [g groups] (if (some #(nth vals (pos %)) g) 1.0 0.0)))))]))

(def family
  {"banking_crisis" :financial "market_crash" :financial})

(defn family-of [kind] (get family kind :political))

;; --- the event model's state -------------------------------------------------------------------

(def event-channels ["protest" "violence" "coercion" "verbal-conflict"])

(def event-labels
  (conj (vec (for [c event-channels f [:growth :excess]] (keyword (str c "-" (name f)))))
        :event-present))

(defn load-event-state
  "The event model's view of each country at each forecast origin of a
  training run (data/runs/<run>/rows): per channel the growth it forecasts
  for the next quarter, log((1+mean)/(1+last)), and its probability of
  escalation above the historical rate. Known at the origin, so a quarter's
  row uses the origin inside it. {[unit quarter] double[]}."
  [run]
  (let [dir (io/file "data/runs" run "rows")
        ci (zipmap event-channels (range))
        n (count event-labels)]
    (into {}
          (for [f (sort-by #(.getName ^java.io.File %) (.listFiles dir))
                :let [nm (.getName f)
                      q (quot (+ (* 12 (parse-long (subs nm 0 4))) (dec (parse-long (subs nm 4 6)))) 3)]
                [unit xs] (with-open [r (io/reader f)]
                            (reduce (fn [acc line]
                                      (let [[id level ch _ last _ mean _ _ _ p-esc p-hist] (str/split line #"\t" -1)]
                                        (if-let [i (and (= level ":country") (ci ch))]
                                          (let [^doubles a (or (acc id) (doto (double-array n) (aset (dec n) 1.0)))]
                                            (aset a (* 2 i) (Math/log (/ (+ 1.0 (parse-double mean)) (+ 1.0 (parse-double last)))))
                                            (aset a (inc (* 2 i)) (- (parse-double p-esc) (parse-double p-hist)))
                                            (assoc acc id a))
                                          acc)))
                                    {} (line-seq r)))]
            [[unit q] xs]))))
