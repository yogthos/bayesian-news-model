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
  (:require [bnm.geo :as geo]
            [bnm.mix :as mix]
            [bnm.ties :as ties]
            [bnm.stocks :as sk]
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
    "politics/protest" "politics/violence" "politics/coercion"
    "economy/profit_rate" "economy/labor_share" "economy/gdp_per_capita" "energy/resource_rents_gdp"
    ;; structure, institutions, youth, nonlinear (docs/STRUCTURE.md)
    "economy/agriculture_share" "economy/manufacturing_share" "economy/services_share"
    "trade/metal_exports_share" "food/exports_share" "technology/hightech_exports_share"
    "food/cereal_yield" "commodities/copper" "commodities/aluminium" "commodities/nickel"
    "state/government_effectiveness" "energy/oil_rents_gdp" "energy/mineral_rents_gdp"
    "people/tertiary_enrolment" "people/urban_share" "people/gini"
    "regime/electoral_democracy" "regime/liberal_democracy" "regime/civil_society"
    "regime/physical_integrity" "regime/corruption" "trade/commodity_tot"
    "finance/fx_peg" "finance/fx_falling" "finance/anchor_usd" "finance/anchor_euro"
    "finance/anchor_gbp" "finance/anchor_cny" "finance/anchor_rub"})

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

(def core
  "The old industrial centres, whose capital's profitability the theory of
  imperialism ties to pressure on resource-rich peripheries."
  ["USA" "GBR" "FRA" "DEU" "JPN" "ITA" "CAN"])

(defn- mean-of [xs] (when (seq xs) (/ (reduce + xs) (count xs))))

(def political-economy-names
  [:profit-rate :profit-rate3 :labor-share3 :uneven-growth :core-profit3 :rents-x-core-profit])

(defn political-economy
  "[names f] for the political economy inputs (docs/REGIMES.md), from the
  Penn World Table: the country's profit rate (real return on capital) and
  its change over three years, the change in labour's share (a squeeze on
  profits, or on wages); growth over five years relative to the world's
  (uneven development, Lenin); and the core's profit rate falling times the
  country's resource rents (capital looking outward for raw materials)."
  [db units months]
  (let [names political-economy-names
        world-growth (into {} (for [t months]
                                [t (mean-of (keep #(some-> (db [% "economy/gdp_per_capita"]) (sk/change t 60 {:stale 30})) units))]))
        core-profit (into {} (for [t months]
                               [t (mean-of (keep #(some-> (db [% "economy/profit_rate"]) (sk/change t 36 {:stale 30})) core))]))]
    [names
     (fn [unit t]
       (let [g (getter db unit)
             growth (g "economy/gdp_per_capita" t 60)
             cp (core-profit t)
             rents (some-> (g "energy/resource_rents_gdp" t) (/ 100.0))]
         [(g "economy/profit_rate" t) (g "economy/profit_rate" t 36) (g "economy/labor_share" t 36)
          (minus growth (world-growth t)) cp (x* rents (some-> cp -))]))]))

(def structure-names
  [:agriculture :manufacturing :services :fuel-exports :food-exports :metal-exports :hightech-exports
   :food12-x-agriculture :food12-x-food-exports :metals12-x-metal-exports :oil12-x-rents
   :oil12-x-manufacturing :food12-x-imports-x-low-yield])

(defn structure
  "[names f] for economic structure and the shocks that reach a country
  through it (docs/STRUCTURE.md, plan step 2): the shares of agriculture,
  manufacturing and services in output and of fuel, food, metals and high
  tech in exports, and world price changes over 12 months times the
  exposure each passes through, split by commodity type because their signs
  differ (Blair, Christensen & Rudkin 2021). Exposures are read two years
  back, fixed before the shock and past WDI's revisions (Ciccone 2018)."
  [db units months]
  (let [names structure-names
        lag 24
        median-yield (into {} (for [t months]
                                [t (let [ys (sort (keep #(some-> (db [% "food/cereal_yield"]) (sk/value (- t lag) {:stale 30})) units))]
                                     (when (seq ys) (nth ys (quot (count ys) 2))))]))]
    [names
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
             ;; yields are stored as logs: how far below the world's median
             low-yield (when-let [y (g "food/cereal_yield" (- t lag))]
                         (when-let [m (median-yield t)] (- m y)))]
         [agri manuf (share "economy/services_share") (share "energy/fuel_exports_share") food-x metal-x
          (share "technology/hightech_exports_share")
          (x* food12 agri) (x* food12 food-x) (x* metals12 metal-x) (x* oil12 rents) (x* oil12 manuf)
          (x* (x* food12 (share "food/imports_share")) low-yield)]))]))

(defn- quantiles-by-month
  "{month [q...]} of `f` (unit, month -> value or nil) over `units` at the
  probabilities `ps`: knots that move with the data, never fixed by hand."
  [units months f ps]
  (into {} (for [t months
                 :let [xs (vec (sort (keep #(f % t) units)))]]
             [t (when (>= (count xs) 10) (mapv #(xs (min (dec (count xs)) (long (* % (count xs))))) ps))])))

(defn- hinge [x k] (when (and x k) (Math/max 0.0 (- (double x) (double k)))))

(def institutions-names
  [:effectiveness :oil-rents-x-weak :mineral-rents-x-weak :oil12-x-oil-rents-x-weak :metals12-x-mineral-rents-x-weak])

(defn institutions
  "[names f]: government effectiveness (WGI, from 1996) and resource rents
  where the state is weak, alone and times the world price change of the
  resource: easily seized oil and minerals raise risk only where
  institutions are weak (Polterovich, Popov & Tonis 2007; Korotayev et al.)."
  [db _units _months]
  [institutions-names
   (fn [unit t]
     (let [g (getter db unit)
           w (getter db "world")
           ge (g "state/government_effectiveness" (- t 12))
           weak (when ge (Math/max 0.0 (- (double ge))))
           oil (some-> (g "energy/oil_rents_gdp" (- t 24)) (/ 100.0))
           mineral (some-> (g "energy/mineral_rents_gdp" (- t 24)) (/ 100.0))
           oil12 (w "energy/oil_brent" t 12)
           metals12 (mean-of (keep #(w % t 12) ["commodities/copper" "commodities/aluminium" "commodities/nickel"]))]
       [ge (x* oil weak) (x* mineral weak) (x* oil12 (x* oil weak)) (x* metals12 (x* mineral weak))]))])

(def youth-names [:tertiary :tertiary5 :youth-unemployment-x-urban :educated-idle])

(defn youth
  "[names f]: the educated-youth mechanism (Korotayev & Zinkina 2022): fast
  growth in tertiary enrolment with young people out of work in the cities;
  tertiary enrolment, its five-year change, youth unemployment times the
  urban share, and all three together."
  [db _units _months]
  [youth-names
   (fn [unit t]
     (let [g (getter db unit)
           tert (some-> (g "people/tertiary_enrolment" (- t 24)) (/ 100.0))
           tert5 (some-> (g "people/tertiary_enrolment" (- t 24) 60) (/ 100.0))
           yu (some-> (g "people/youth_unemployment" (- t 12)) (/ 100.0))
           urban (some-> (g "people/urban_share" (- t 12)) (/ 100.0))]
       [tert tert5 (x* yu urban) (x* (some-> tert5 (Math/max 0.0)) (x* yu urban))]))])

(def nonlinear-names [:log-income :income-above-low :income-above-high :gini :gini-above-median])

(defn nonlinear
  "[names f]: income and inequality as piecewise-linear curves, so a risk
  can rise and then fall (unarmed revolution peaks at middle incomes,
  Ustyuzhanin, Fain & Korotayev): log income per head with hinges at the
  month's tertiles across countries, and the Gini index with a hinge at its
  median."
  [db units months]
  ;; income per head is stored as its log (variables.tsv)
  (let [income (fn [u t] (some-> (db [u "economy/gdp_per_capita"]) (sk/value (- t 12) {:stale 30})))
        gini (fn [u t] (some-> (db [u "people/gini"]) (sk/value (- t 12) {:stale 60})))
        ik (quantiles-by-month units months income [(/ 1.0 3.0) (/ 2.0 3.0)])
        gk (quantiles-by-month units months gini [0.5])]
    [nonlinear-names
     (fn [unit t]
       (let [y (income unit t) gi (gini unit t)]
         [y (hinge y (first (ik t))) (hinge y (second (ik t))) (some-> gi (/ 100.0)) (some-> (hinge gi (first (gk t))) (/ 100.0))]))]))

(def regime-names
  [:democracy :democracy-above-median :middling :liberal-gap :civil-society :physical-integrity :corruption])

(defn regime
  "[names f] from V-Dem: electoral democracy, as a curve (a hinge at the
  month's median across countries, and democracy times its distance from
  full democracy, which peaks in the middle: non-violent destabilization
  goes with middling democracy, Korotayev 2024), the gap between liberal and
  electoral democracy (elections without constraints), civil society,
  freedom from political violence and corruption."
  [db units months]
  (let [dem (fn [u t] (some-> (db [u "regime/electoral_democracy"]) (sk/value t {:stale 30})))
        mk (quantiles-by-month units months dem [0.5])]
    [regime-names
     (fn [unit t]
       (let [g (getter db unit)
             d (dem unit t)]
         [d (hinge d (first (mk t))) (when d (* d (- 1.0 d)))
          (minus (g "regime/electoral_democracy" t) (g "regime/liberal_democracy" t))
          (g "regime/civil_society" t) (g "regime/physical_integrity" t) (g "regime/corruption" t)]))]))

(def tot-names [:tot12 :tot3 :tot12-fall :tot-vol :tot-vol-x-primary])

(defn terms-of-trade
  "[names f] from the IMF's commodity terms of trade by country (fixed
  weights, net exports to GDP): its change over 12 and 3 months, the fall
  alone (downturns raise civil-war risk, Brückner & Ciccone 2010), and its
  volatility over 12 months, alone and times the primary share of output
  (agriculture and rents): commodity volatility, not credit, predicts
  banking crises where the primary sector is large (Eberhardt & Presbitero
  2021)."
  [db _units _months]
  [tot-names
   (fn [unit t]
     (let [g (getter db unit)
           tot12 (g "trade/commodity_tot" t 12)
           vol (some-> (db [unit "trade/commodity_tot"]) (sk/volatility t 12 {:stale 6}))
           primary (let [a (g "economy/agriculture_share" (- t 24)) r (g "energy/resource_rents_gdp" (- t 24))]
                     (when (or a r) (/ (+ (or a 0.0) (or r 0.0)) 100.0)))]
       [tot12 (g "trade/commodity_tot" t 3) (some-> tot12 (Math/min 0.0)) vol (x* vol primary)]))])

(def fx-names [:peg :falling :anchor-usd :anchor-euro :anchor-gbp :anchor-cny :anchor-rub :tot-vol-x-peg-x-primary])

(defn fx
  "[names f] from the de facto exchange-rate regimes (Ilzetzki, Reinhart &
  Rogoff): pegged, freely falling, the currency anchored to, and commodity
  terms-of-trade volatility where the currency is pegged and the primary
  sector large, where commodity volatility rather than credit predicts
  banking crises (Eberhardt & Presbitero 2021)."
  [db _units _months]
  [fx-names
   (fn [unit t]
     (let [g (getter db unit)
           peg (g "finance/fx_peg" t)
           vol (some-> (db [unit "trade/commodity_tot"]) (sk/volatility t 12 {:stale 6}))
           primary (let [a (g "economy/agriculture_share" (- t 24)) r (g "energy/resource_rents_gdp" (- t 24))]
                     (when (or a r) (/ (+ (or a 0.0) (or r 0.0)) 100.0)))]
       [peg (g "finance/fx_falling" t) (g "finance/anchor_usd" t) (g "finance/anchor_euro" t)
        (g "finance/anchor_gbp" t) (g "finance/anchor_cny" t) (g "finance/anchor_rub" t)
        (x* vol (x* peg primary))]))])

(def presence
  "Groups of inputs that share a source: one indicator each. A source that
  starts or stops at a date (world prices, the VIX, the Penn World Table's
  core) has its own, or its zeros would stand in for the period."
  {:financial [[:credit3 :hh-credit3 :credit-gap :dsr3] [:house3] [:equity3 :runup2 :equity-vol] [:curve]
               [:vix :vix-x-credit3]]
   :political [[:food-inflation] [:fragility] [:gdp-growth :inflation] [:youth-unemployment]
               [:temperature :precipitation] [:protest3 :violence3]
               [:world-food12 :food12-x-imports :food12-x-fragility] [:oil12-x-imports :oil12-x-exports]]
   :political-economy [[:profit-rate :profit-rate3 :labor-share3] [:uneven-growth] [:rents-x-core-profit] [:core-profit3]]
   :structure [[:agriculture :manufacturing :services] [:fuel-exports :food-exports :metal-exports] [:hightech-exports]
               [:food12-x-agriculture :food12-x-food-exports :food12-x-imports-x-low-yield]
               [:metals12-x-metal-exports] [:oil12-x-rents :oil12-x-manufacturing]]
   :institutions [[:effectiveness] [:oil-rents-x-weak :mineral-rents-x-weak]
                  [:oil12-x-oil-rents-x-weak] [:metals12-x-mineral-rents-x-weak]]
   :youth [[:tertiary :tertiary5] [:youth-unemployment-x-urban]]
   :nonlinear [[:log-income] [:gini]]
   :regime [[:democracy :liberal-gap :civil-society :physical-integrity :corruption]]
   :terms-of-trade [[:tot12 :tot3 :tot-vol]]
   :fx [[:peg :falling] [:anchor-usd :anchor-euro :anchor-gbp :anchor-cny :anchor-rub]]})

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

;; --- the event-code mix ------------------------------------------------------------------------

(defn load-event-counts
  "scripts/event-mix.sh's table as {[unit quarter] {type count}}."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[unit q t n] (str/split line #"\t")]
                (assoc-in acc [[unit (parse-long q)] t] (parse-double n))))
            {} (line-seq r))))

(defn load-event-dyads
  "scripts/event-dyads.sh's table as {quarter {[a b] {:coop :conflict}}},
  countries only."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[a b q k n] (str/split line #"\t")]
                (if (and (geo/country? a) (geo/country? b))
                  (assoc-in acc [(parse-long q) [a b] (keyword k)] (parse-double n))
                  acc)))
            {} (line-seq r))))

(defn load-ties
  "Tie features per country and quarter (bnm.ties), partners near when they
  share a subregion: {[unit quarter] double[]}, a quarter's known at its end."
  [dyads-path mix-path]
  (ties/features (load-event-dyads dyads-path) (load-event-counts mix-path)
                 (fn [a b] (= (geo/parents a) (geo/parents b)))))

(def cow-overrides
  "Correlates of War codes whose names bnm.geo does not resolve, for states
  that exist now."
  {"56" "LCA" "57" "VCT" "58" "ATG" "60" "KNA" "210" "NLD" "260" "DEU" "678" "YEM" "987" "FSM"})

(defn load-cow-codes
  "{cow-code iso3} from the Correlates of War state list."
  [path]
  (into {} (for [line (rest (str/split (slurp path) #"\r\n?|\n"))
                 :let [[_ c n] (str/split line #",")
                       iso (or (cow-overrides c) (geo/resolve-name n))]
                 :when iso]
             [c iso])))

(defn load-un-agreement
  "{year {[a b] agreement}} (pairs sorted, ISO3) from Voeten's UN General
  Assembly agreement scores (Harvard Dataverse doi:10.7910/DVN/LEJUQZ, CC0)."
  [path codes-path]
  (let [iso (load-cow-codes codes-path)]
    (with-open [r (io/reader path)]
      (reduce (fn [acc line]
                (let [f (mapv #(str/replace % "\"" "") (str/split line #","))
                      a (iso (f 2)) b (iso (f 3)) agree (parse-double (f 4)) year (parse-long (f 5))]
                  (if (and a b agree year (not= a b))
                    (assoc-in acc [year (if (neg? (compare a b)) [a b] [b a])] agree)
                    acc)))
              {} (rest (line-seq r))))))

(defn un-weights
  "A function of the quarter giving {country {partner weight}}: each
  country's `k` partners that voted most like it in the UN General Assembly
  the year before, weighted by agreement; past the data's last year, its
  last year's."
  [agreement k]
  (memoize
   (fn [q]
     (let [year (min (dec (quot q 4)) (apply max (keys agreement)))
           by (reduce-kv (fn [acc [a b] v] (-> acc (assoc-in [a b] v) (assoc-in [b a] v))) {} (get agreement year {}))]
       (into {} (for [[c ps] by
                      :let [top (take k (sort-by (comp - val) ps))
                            total (reduce + (map val top))]
                      :when (pos? total)]
                  [c (into {} (for [[p v] top] [p (/ v total)]))]))))))

(defn load-ties-un
  "Tie features (bnm.ties) with partners weighted by UN voting agreement."
  [dyads-path mix-path un-path codes-path]
  (ties/features (load-event-dyads dyads-path) (load-event-counts mix-path)
                 (fn [a b] (= (geo/parents a) (geo/parents b)))
                 {:weights-of (un-weights (load-un-agreement un-path codes-path) ties/top-k)}))

(defn load-event-mix
  "The event-code mix of each country at each quarter (bnm.mix) from
  scripts/event-mix.sh's table (country, quarter, type, count): {[unit
  quarter] double[]}, a quarter's row known at its end."
  [path]
  (let [by-unit (with-open [r (io/reader path)]
                  (reduce (fn [acc line]
                            (let [[unit q t n] (str/split line #"\t")]
                              (update-in acc [unit (parse-long q)] (fnil assoc {}) t (parse-double n))))
                          {} (line-seq r)))]
    (into {} (for [[unit qs] by-unit
                   [q x] (mix/features (into (sorted-map) qs))]
               [[unit q] x]))))
