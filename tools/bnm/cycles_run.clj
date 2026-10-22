(ns bnm.cycles-run
  "Writes data/derived/stocks/cycles.csv (iso,year,variable,value; the year
  a value is known by its end, from data years before it; variables in
  bnm.cycles's docstring) from

    data/slow/series.tsv          world monthly prices (FRED's IMF and EIA
        series: commodities/*, energy/oil_*, gas_*, coal, food/*), world
        oil, gas and coal production and consumption (Our World in Data,
        from the Energy Institute), and countries' export shares (World
        Bank WDI: fuel, ores and metals, food)
    data/derived/stocks/food.csv  world wheat, maize and rice production,
        use and stocks-to-use (bnm.food-run; FAOSTAT)

  each commodity calibrated on an expanding window every few years
  (bnm.cycles/commodity-rows), and producers' exposures: the export share
  in fuel (times oil's price gap), ores and metals (the four metals' mean
  gap) and food (the five crops' mean gap). Prints spot checks.

    jolt -A:tools -M -m bnm.cycles-run ['{:only [\"copper\"] :members 40 :every 5}']

  (options over bnm.cycles/run-defaults, :from 1990; :only reports
  without writing; :parallel runs the commodities in parallel)"
  (:require [bnm.cycles :as cy]
            [bnm.named :as named]
            [bnm.slow-train :as st]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def series-path "data/slow/series.tsv")
(def variables-path "data/slow/variables.tsv")
(def food-path "data/derived/stocks/food.csv")
(def out-path "data/derived/stocks/cycles.csv")

(def commodities
  "commodity -> {:price store variable, :output/:demand world quantity
  variables in the store, :food the item in food.csv}."
  (array-map
   "oil_brent" {:price "energy/oil_brent" :output "energy/oil_production" :demand "energy/oil_consumption"}
   "oil_wti" {:price "energy/oil_wti" :output "energy/oil_production" :demand "energy/oil_consumption"}
   "gas_europe" {:price "energy/gas_europe" :output "energy/gas_production" :demand "energy/gas_consumption"}
   "gas_asia" {:price "energy/gas_asia" :output "energy/gas_production" :demand "energy/gas_consumption"}
   "gas_us" {:price "energy/gas_us" :output "energy/gas_production" :demand "energy/gas_consumption"}
   "coal" {:price "energy/coal" :output "energy/coal_production"}
   "aluminium" {:price "commodities/aluminium"}
   "copper" {:price "commodities/copper"}
   "iron_ore" {:price "commodities/iron_ore"}
   "nickel" {:price "commodities/nickel"}
   "all_index" {:price "commodities/all_index"}
   "wheat" {:price "food/wheat" :food "wheat"}
   "maize" {:price "food/maize" :food "maize"}
   "rice" {:price "food/rice" :food "rice"}
   "soybeans" {:price "food/soybeans"}
   "sugar" {:price "food/sugar"}))

(def exposures
  "exposure variable -> [export share in the store, commodities whose mean
  price gap is the group's phase]."
  {"fuel_exposure" ["energy/fuel_exports_share" ["oil_brent"]]
   "metal_exposure" ["trade/metal_exports_share" ["aluminium" "copper" "iron_ore" "nickel"]]
   "food_exposure" ["food/exports_share" ["wheat" "maize" "rice" "soybeans" "sugar"]]})

(def store-vars
  (into (set (mapcat (fn [c] (keep c [:price :output :demand])) (vals commodities)))
        (map first (vals exposures))))

(defn- untransform [tr x]
  (case tr "log" (Math/exp x) "log1p" (Math/expm1 x) x))

(defn- entries
  "[[period-month available-month value]] of a bnm.stocks series, the value
  back in its natural units."
  [tr {:keys [period available value]}]
  (map (fn [p a x] [p a (untransform tr x)]) period available value))

(defn annual-prices
  "{data-year [year-known mean price]}: years with all twelve months."
  [es]
  (let [by (group-by #(quot (long (first %)) 12) es)]
    (into (sorted-map)
          (for [[y xs] by :when (= 12 (count (distinct (map first xs))))]
            [y [(apply max (map #(quot (long (second %)) 12) xs)) (/ (reduce + (map #(nth % 2) xs)) (count xs))]]))))

(defn annual
  "{data-year [year-known value]} of an annual series."
  [es]
  (into (sorted-map) (for [[p a x] es] [(quot (long p) 12) [(quot (long a) 12) x]])))

(defn read-food
  "{item {:output :demand :coverage}} each {data-year [year-known value]}
  of the world's rows in food.csv (its year the year known, the data year
  one before)."
  [path]
  (if-not (.exists (io/file path))
    {}
    (with-open [r (io/reader path)]
      (reduce (fn [acc l]
                (if (str/starts-with? l "WLD,")
                  (let [[_ y v x] (str/split l #"," -1)
                        [_ item what] (re-matches #"(wheat|maize|rice)_(production|use|stocks_to_use)" v)
                        k ({"production" :output "use" :demand "stocks_to_use" :coverage} what)]
                    (if (and k (seq x))
                      (let [y (parse-long y)] (assoc-in acc [item k (dec y)] [y (parse-double x)]))
                      acc))
                  acc))
              {} (line-seq r)))))

(defn- mean [xs] (when (seq xs) (/ (reduce + 0.0 xs) (count xs))))

(defn- corr [pairs]
  (let [n (count pairs)]
    (when (> n 2)
      (let [xs (map first pairs) ys (map second pairs)
            mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
            sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
            sxx (reduce + (map #(* (- % mx) (- % mx)) xs))
            syy (reduce + (map #(* (- % my) (- % my)) ys))]
        (when (and (pos? sxx) (pos? syy)) (/ sxy (Math/sqrt (* sxx syy))))))))

(defn- report [rows]
  (let [idx (into {} (map (juxt (juxt :iso :year :variable) :value)) rows)
        g (fn [c y v] (idx ["WLD" y (str c "_" v)]))
        f (fn [x] (if x (format "%6.2f" (double x)) "     -"))]
    (println (count rows) "rows," (count (distinct (map :iso rows))) "places,"
             (count (distinct (map :variable rows))) "variables,"
             (apply min (map :year rows)) "-" (apply max (map :year rows)) "->" out-path)
    (println "\ncoverage: commodity, years with rows")
    (doseq [c (keys commodities)
            :let [ys (sort (distinct (for [r rows :when (= (:variable r) (str c "_price_gap"))] (:year r))))]]
      (println (format "  %-11s %s" c (if (seq ys) (str (first ys) "-" (last ys) " (" (count ys) ")") "none"))))
    (println "\nhow the model's phase follows the observed one: corr(price_gap, model_price_gap), and the observed\nlog price change over the next three years against dir_3y (rows whose outcome is known)")
    (doseq [c (keys commodities)
            :let [ys (sort (distinct (for [r rows :when (= (:variable r) (str c "_price_gap"))] (:year r))))
                  pairs (keep #(let [a (g c % "price_gap") b (g c % "model_price_gap")] (when (and a b) [a b])) ys)
                  fwd (keep #(let [a (g c % "log_price") b (g c (+ % 3) "log_price") d (g c % "dir_3y")]
                               (when (and a b d) [(- b a) d])) ys)]]
      (println (format "  %-11s n %2d corr %s   n %2d corr %s" c (count pairs) (f (corr pairs)) (count fwd) (f (corr fwd)))))
    (println "\nestimates at each calibration: year beta (log sd) construction time (log sd) own period")
    (doseq [c (keys commodities)]
      (println " " c)
      (doseq [y (sort (distinct (for [r rows :when (= (:variable r) (str c "_calibrated"))] (long (:value r)))))]
        (println (format "    %d  beta %s (%s)  tau-c %s (%s)  period %s" y (f (g c y "beta")) (f (g c y "beta_sd"))
                         (f (g c y "construction_time")) (f (g c y "construction_time_sd")) (f (g c y "period"))))))
    (println "\nyear by year: price gap, model gap, coverage, pipeline, capacity gap, expected change 1y 3y")
    (doseq [[c ys] [["oil_brent" (range 1996 2012)] ["oil_brent" (range 2013 2018)] ["oil_wti" [2008 2009 2014 2015 2016]]
                    ["copper" (range 2002 2014)] ["nickel" [2006 2007 2008 2009]] ["iron_ore" (range 2004 2016)]
                    ["aluminium" [2004 2008 2009 2012]] ["all_index" [2004 2008 2009 2012 2016 2021 2023]]
                    ["wheat" [2006 2008 2009 2011 2012 2021 2023]] ["maize" [2008 2011 2013 2023]]
                    ["rice" [2008 2009 2012]] ["gas_europe" [2010 2015 2021 2022 2023 2024]]
                    ["coal" [2008 2009 2016 2022 2023]]]]
      (println " " c)
      (doseq [y ys :when (g c y "price_gap")]
        (println "   " y (f (g c y "price_gap")) (f (g c y "model_price_gap")) (f (g c y "coverage"))
                 (f (g c y "pipeline")) (f (g c y "capacity_gap")) (f (g c y "dir_1y")) (f (g c y "dir_3y")))))
    (println "\nexposures (latest year):")
    (doseq [v (keys exposures)
            :let [ys (for [r rows :when (= v (:variable r))] (:year r))]
            :when (seq ys)
            :let [y (apply max ys)
                  top (take 6 (sort-by #(- (Math/abs (double (:value %)))) (filter #(and (= v (:variable %)) (= y (:year %))) rows)))]]
      (println " " v y (count (filter #(= v (:variable %)) rows)) "rows;" (str/join " " (for [r top] (str (:iso r) ":" (f (:value r)))))))))

(defn -main [& args]
  (let [opts (merge {:from 1990 :to 2026} (some-> (first args) read-string))
        variables (st/read-variables variables-path)
        tr (into {} (map (juxt :name :transform)) variables)
        db (with-redefs [named/vars store-vars] (named/load-series series-path variables))
        world (fn [v] (some->> (db ["world" v]) (entries (tr v))))
        food (read-food food-path)
        panels (into (array-map)
                     (for [[c {:keys [price output demand] :as spec}] commodities
                           :when (or (nil? (:only opts)) ((set (:only opts)) c))]
                       [c (merge (cond-> {:price (annual-prices (world price))}
                                   output (assoc :output (annual (world output)))
                                   demand (assoc :demand (annual (world demand))))
                                 (when-let [item (:food spec)] (food item)))]))
        t0 (System/currentTimeMillis)
        each (fn [[c panel]] [c (cy/commodity-rows c panel opts)])
        by (into {} (if (:parallel opts) (pmap each panels) (map each panels)))
        _ (println "calibrated in" (quot (- (System/currentTimeMillis) t0) 1000) "s")
        gaps (fn [c] (into {} (for [r (by c) :when (= (:variable r) (str c "_price_gap"))] [(:year r) (:value r)])))
        phase (fn [cs] (let [gs (map gaps cs)]
                         (into {} (for [y (distinct (mapcat keys gs))
                                        :let [xs (keep #(get % y) gs)]
                                        :when (= (count xs) (count cs))]
                                    [y (mean xs)]))))
        shares (fn [v] (into {} (for [[[unit var] s] db
                                      :when (and (= var v) (re-matches #"[A-Z]{3}" unit))]
                                  [unit (annual (entries (tr v) s))])))
        exposure (mapcat (fn [[ev [sv cs]]] (cy/exposure-rows (phase cs) (shares sv) ev {})) exposures)
        rows (vec (concat (mapcat by (keys panels)) exposure))]
    ;; a partial run ({:only [...]}) reports without writing
    (when-not (:only opts)
      (io/make-parents out-path)
      (with-open [w (io/writer out-path)]
        (doseq [l (cy/csv-lines rows)] (.write w (str l "\n")))))
    (report rows)
    (shutdown-agents)))
