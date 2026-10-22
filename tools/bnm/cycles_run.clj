(ns bnm.cycles-run
  "Writes data/derived/stocks/cycles.csv (iso,year,variable,value; the year
  a value is known by its end, from data years before it; variables
  cycles/<commodity>-<name>, in bnm.cycles's docstring) from

    data/raw/market/pinksheet/pinksheet-monthly.csv  the World Bank's
        commodity prices (the Pink Sheet, CMO Historical Data Monthly,
        CC BY 4.0; scripts/pinksheet.py from the xlsx, SOURCE.txt beside
        it), monthly from 1960 (coal 1970, Japan LNG 1977): every
        commodity's price but WTI's and the index's. A current vintage
        (its history rarely revised), each month known the month after,
        as WTISPLC. Before 1976 its Brent is Saudi Light, as its Dubai
        to 1984; its iron ore is the annual contract price (cents per dry
        metric ton unit) before 2006, the China spot price ($/t) after.
    data/slow/series.tsv          world monthly prices (FRED's IMF and EIA
        series from 1987-1997: commodities/*, energy/oil_brent, gas_*,
        coal, food/*; only all-index's price is read from here now, the
        rest compared with the Pink Sheet over their overlap), world oil,
        gas and coal production and consumption (Our World in Data, from
        the Energy Institute), and countries' export shares (World Bank
        WDI: fuel, ores and metals, food)
    data/raw/slow/market/WTISPLC.csv  WTI spot, monthly from 1946 (FRED;
        posted prices before the 1980s), oil-wti's price (longer than the
        Pink Sheet's WTI, from 1982)
    data/raw/slow/market/CPIAUCNS.csv US CPI (not seasonally adjusted,
        never revised), monthly from 1913: every price is deflated by the
        year's mean CPI, known the year after
    data/derived/stocks/food.csv  world wheat, maize and rice production
        and use (bnm.food-run; FAOSTAT; data year bnm.food/lag before the
        year known). Not its stocks-to-use: FAO gives stock changes only,
        and the cumulated level drifts (bnm.food), so it is no coverage.

  each commodity calibrated on an expanding window every few years
  (bnm.cycles/commodity-rows), and producers' exposures: the export share
  in fuel (times WTI's price gap, the longest series), ores and metals (the
  four metals' mean gap) and food (wheat, maize, rice, soybeans and
  sugar's mean gap). Prints spot checks.

  One source per commodity over its whole history (as bnm.keen for
  credit), so the level does not jump where sources meet: the Pink Sheet
  for all but oil-wti (WTISPLC) and all-index (the IMF's, 1992 on); the
  report prints each against the other source over their overlap (the
  correlation of log annual means, the level ratio).

  Design years: rows start eight years after a price does, so 1968 for
  the Pink Sheet's (coal 1978, gas-asia 1985), 1954 for oil-wti; all-index
  from 2000.

    jolt -A:tools -M -m bnm.cycles-run ['{:only [\"copper\"] :members 40 :every 5}']

  (options over bnm.cycles/run-defaults, :from 1950; :only reports
  without writing; :parallel runs the commodities in parallel)"
  (:require [bnm.cycles :as cy]
            [bnm.food :as food]
            [bnm.named :as named]
            [bnm.slow-train :as st]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def series-path "data/slow/series.tsv")
(def variables-path "data/slow/variables.tsv")
(def food-path "data/derived/stocks/food.csv")
(def out-path "data/derived/stocks/cycles.csv")
(def wti-path "data/raw/slow/market/WTISPLC.csv")
(def cpi-path "data/raw/slow/market/CPIAUCNS.csv")
(def pink-path "data/raw/market/pinksheet/pinksheet-monthly.csv")

(def commodities
  "commodity -> {:price its one source: [:pink column] (pinksheet-monthly
  .csv), :wti (WTISPLC.csv) or a store variable; :alt the other sources
  compared over the overlap, not used; :output/:demand world quantity
  variables in the store, :food the item in food.csv}."
  (array-map
   "oil-brent" {:price [:pink "oil-brent"] :alt ["energy/oil_brent"] :output "energy/oil_production" :demand "energy/oil_consumption"}
   "oil-dubai" {:price [:pink "oil-dubai"] :output "energy/oil_production" :demand "energy/oil_consumption"}
   "oil-wti" {:price :wti :alt [[:pink "oil-wti"]] :output "energy/oil_production" :demand "energy/oil_consumption"}
   "gas-europe" {:price [:pink "gas-europe"] :alt ["energy/gas_europe"] :output "energy/gas_production" :demand "energy/gas_consumption"}
   "gas-asia" {:price [:pink "gas-asia"] :alt ["energy/gas_asia"] :output "energy/gas_production" :demand "energy/gas_consumption"}
   "gas-us" {:price [:pink "gas-us"] :alt ["energy/gas_us"] :output "energy/gas_production" :demand "energy/gas_consumption"}
   "coal" {:price [:pink "coal"] :alt ["energy/coal"] :output "energy/coal_production"}
   "aluminium" {:price [:pink "aluminium"] :alt ["commodities/aluminium"]}
   "copper" {:price [:pink "copper"] :alt ["commodities/copper"]}
   "iron-ore" {:price [:pink "iron-ore"] :alt ["commodities/iron_ore"]}
   "nickel" {:price [:pink "nickel"] :alt ["commodities/nickel"]}
   "all-index" {:price "commodities/all_index"}
   "wheat" {:price [:pink "wheat"] :alt ["food/wheat"] :food "wheat"}
   "maize" {:price [:pink "maize"] :alt ["food/maize"] :food "maize"}
   "rice" {:price [:pink "rice"] :alt ["food/rice"] :food "rice"}
   "soybeans" {:price [:pink "soybeans"] :alt ["food/soybeans"]}
   "sugar" {:price [:pink "sugar"] :alt ["food/sugar"]}))

(def exposures
  "exposure variable -> [export share in the store, commodities whose mean
  price gap is the group's phase (all of them known that year)]."
  {"cycles/fuel-exposure" ["energy/fuel_exports_share" ["oil-wti"]]
   "cycles/metal-exposure" ["trade/metal_exports_share" ["aluminium" "copper" "iron-ore" "nickel"]]
   "cycles/food-exposure" ["food/exports_share" ["wheat" "maize" "rice" "soybeans" "sugar"]]})

(defn- var-of [c v] (str "cycles/" c "-" v))

(def store-vars
  (into (set (mapcat (fn [c] (filter string? (concat (keep c [:price :output :demand]) (:alt c)))) (vals commodities)))
        (map first (vals exposures))))

(defn- untransform [tr x]
  (case tr "log" (Math/exp x) "log1p" (Math/expm1 x) x))

(defn- entries
  "[[period-month available-month value]] of a bnm.stocks series, the value
  back in its natural units."
  [tr {:keys [period available value]}]
  (map (fn [p a x] [p a (untransform tr x)]) period available value))

(defn annual-prices
  "{data-year [year-known mean price]}: years with all twelve months, or
  at least `min-months`."
  ([es] (annual-prices es 12))
  ([es min-months]
   (let [by (group-by #(quot (long (first %)) 12) es)]
     (into (sorted-map)
           (for [[y xs] by :when (<= min-months (count (distinct (map first xs))))]
             [y [(apply max (map #(quot (long (second %)) 12) xs)) (/ (reduce + (map #(nth % 2) xs)) (count xs))]])))))

(defn read-monthly-csv
  "[[period-month available-month value]] of a FRED csv (a date, a value),
  each month's value out the month after."
  [path]
  (with-open [r (io/reader path)]
    (vec (for [l (rest (line-seq r))
               :let [[d x] (str/split l #"," -1)
                     x (some-> x str/trim not-empty parse-double)]
               :when (and x (re-matches #"\d{4}-\d{2}.*" d))
               :let [m (+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 5 7))))]]
           [m (inc m) x]))))

(defn read-wide-monthly-csv
  "{column [[period-month available-month value]]} of a csv whose first
  column is the month (YYYY-MM) and each other a series (scripts/
  pinksheet.py's), each month's value out the month after, blank cells
  left out; nil without the file."
  [path]
  (when (.exists (io/file path))
    (with-open [r (io/reader path)]
      (let [[header & ls] (line-seq r)
            cols (vec (rest (str/split header #",")))]
        (reduce (fn [acc l]
                  (let [[d & xs] (str/split l #"," -1)]
                    (if-not (re-matches #"\d{4}-\d{2}" (str d))
                      acc
                      (let [m (+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 5 7))))]
                        (reduce (fn [acc [c x]]
                                  (if-let [x (some-> x str/trim not-empty parse-double)]
                                    (update acc c (fnil conj []) [m (inc m) x])
                                    acc))
                                acc (map vector cols xs))))))
                {} ls)))))

(defn annual
  "{data-year [year-known value]} of an annual series."
  [es]
  (into (sorted-map) (for [[p a x] es] [(quot (long p) 12) [(quot (long a) 12) x]])))

(defn read-food
  "{item {:output :demand}} each {data-year [year-known value]} of the
  world's rows in food.csv (food/<item>-production and -use; its year the
  year known, the data year bnm.food/lag before)."
  [path]
  (if-not (.exists (io/file path))
    {}
    (with-open [r (io/reader path)]
      (reduce (fn [acc l]
                (if (str/starts-with? l "WLD,")
                  (let [[_ y v x] (str/split l #"," -1)
                        [_ item what] (re-matches #"food/(wheat|maize|rice)-(production|use)" v)
                        k ({"production" :output "use" :demand} what)]
                    (if (and k (seq x))
                      (let [y (parse-long y)] (assoc-in acc [item k (- y food/lag)] [y (parse-double x)]))
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

(defn agreement
  "{:n :from :to :corr :ratio} of two annual series ({data-year
  [year-known value]}) over the data years both have: the correlation of
  their log values and the geometric mean of a over b; nil with fewer than
  three."
  [a b]
  (let [ys (sort (filter #(and (get b %) (pos? (double (second (get a %)))) (pos? (double (second (get b %))))) (keys a)))
        lg (fn [s y] (Math/log (double (second (get s y)))))]
    (when (> (count ys) 2)
      {:n (count ys) :from (first ys) :to (last ys)
       :corr (corr (map (juxt #(lg a %) #(lg b %)) ys))
       :ratio (Math/exp (mean (map #(- (lg a %) (lg b %)) ys)))})))

(defn- source-name [price]
  (cond (= :wti price) "FRED WTISPLC"
        (vector? price) (str "Pink Sheet " (second price))
        :else (str "store " price)))

(defn- report-sources [agreements]
  (println "\nsources: each commodity's price (first year), and against the others over their overlap: years,\ncorrelation of log annual means, level ratio (chosen / other)")
  (doseq [[c {:keys [price first-year others]}] agreements]
    (println (format "  %-11s %-22s %s" c (source-name price) (or first-year "-")))
    (doseq [[alt {:keys [n from to corr ratio]} alt-first] others]
      (println (format "      vs %-28s from %s  %s" (source-name alt) (or alt-first "-")
                       (if n (format "%d-%d n %2d corr %.3f ratio %.3f" from to n corr ratio) "no overlap"))))))

(defn- report [rows]
  (let [idx (into {} (map (juxt (juxt :iso :year :variable) :value)) rows)
        g (fn [c y v] (idx ["WLD" y (var-of c v)]))
        f (fn [x] (if x (format "%6.2f" (double x)) "     -"))]
    (println (count rows) "rows," (count (distinct (map :iso rows))) "places,"
             (count (distinct (map :variable rows))) "variables,"
             (apply min (map :year rows)) "-" (apply max (map :year rows)) "->" out-path)
    (println "\ncoverage: commodity, years with rows")
    (doseq [c (keys commodities)
            :let [ys (sort (distinct (for [r rows :when (= (:variable r) (var-of c "price-gap"))] (:year r))))]]
      (println (format "  %-11s %s" c (if (seq ys) (str (first ys) "-" (last ys) " (" (count ys) ")") "none"))))
    (println "\nhow the model's phase follows the observed one: corr(price-gap, model-price-gap); the observed\nlog real price change over the next three years against dir-3y (rows whose outcome is known), and\nagainst the gap's reversion alone (-price-gap), all rows and in the design years (to 2002)")
    (doseq [c (keys commodities)
            :let [ys (sort (distinct (for [r rows :when (= (:variable r) (var-of c "price-gap"))] (:year r))))
                  pairs (keep #(let [a (g c % "price-gap") b (g c % "model-price-gap")] (when (and a b) [a b])) ys)
                  fwd (fn [ys v s] (keep #(let [a (g c % "log-price") b (g c (+ % 3) "log-price") d (g c % v)]
                                            (when (and a b d) [(- b a) (* s d)])) ys))
                  early (filter #(<= % 2002) ys)]]
      (println (format "  %-11s n %2d corr %s   dir n %2d corr %s  -gap %s   to 2002: n %2d dir %s -gap %s"
                       c (count pairs) (f (corr pairs)) (count (fwd ys "dir-3y" 1.0)) (f (corr (fwd ys "dir-3y" 1.0)))
                       (f (corr (fwd ys "price-gap" -1.0)))
                       (count (fwd early "dir-3y" 1.0)) (f (corr (fwd early "dir-3y" 1.0))) (f (corr (fwd early "price-gap" -1.0))))))
    (println "\nestimates at each calibration: year beta (log sd) construction time (log sd) own period")
    (doseq [c (keys commodities)]
      (println " " c)
      (doseq [y (sort (distinct (for [r rows :when (= (:variable r) (var-of c "calibrated"))] (long (:value r)))))]
        (println (format "    %d  beta %s (%s)  tau-c %s (%s)  period %s" y (f (g c y "beta")) (f (g c y "beta-sd"))
                         (f (g c y "construction-time")) (f (g c y "construction-time-sd")) (f (g c y "period"))))))
    (println "\nyear by year: price gap, model gap, coverage, pipeline, capacity gap, expected change 1y 3y")
    (doseq [[c ys] [["oil-wti" [1960 1970 1973 1974 1975 1979 1980 1981 1982 1986 1990 1998 1999 2000]]
                    ["oil-brent" (range 1996 2012)] ["oil-brent" (range 2013 2018)] ["oil-wti" [2008 2009 2014 2015 2016]]
                    ["copper" [1970 1974 1975 1980 1982 1986 1989 1995 1999]] ["copper" (range 2002 2014)] ["nickel" [2006 2007 2008 2009]] ["iron-ore" (range 2004 2016)]
                    ["aluminium" [2004 2008 2009 2012]] ["all-index" [2004 2008 2009 2012 2016 2021 2023]]
                    ["wheat" [2006 2008 2009 2011 2012 2021 2023]] ["maize" [2008 2011 2013 2023]]
                    ["rice" [2019 2021 2023]] ["gas-europe" [2010 2015 2021 2022 2023 2024]]
                    ["coal" [2008 2009 2016 2022 2023]]]]
      (println " " c)
      (doseq [y ys :when (g c y "price-gap")]
        (println "   " y (f (g c y "price-gap")) (f (g c y "model-price-gap")) (f (g c y "coverage"))
                 (f (g c y "pipeline")) (f (g c y "capacity-gap")) (f (g c y "dir-1y")) (f (g c y "dir-3y")))))
    (println "\nexposures (years with rows; latest year's largest):")
    (doseq [v (keys exposures)
            :let [ys (for [r rows :when (= v (:variable r))] (:year r))]
            :when (seq ys)
            :let [y (apply max ys)
                  _ (println " " v (apply min ys) "-" y)
                  top (take 6 (sort-by #(- (Math/abs (double (:value %)))) (filter #(and (= v (:variable %)) (= y (:year %))) rows)))]]
      (println " " v y (count (filter #(= v (:variable %)) rows)) "rows;" (str/join " " (for [r top] (str (:iso r) ":" (f (:value r)))))))))

(defn -main [& args]
  (let [opts (merge {:from 1950 :to 2026} (some-> (first args) read-string))
        variables (st/read-variables variables-path)
        tr (into {} (map (juxt :name :transform)) variables)
        db (with-redefs [named/vars store-vars] (named/load-series series-path variables))
        world (fn [v] (some->> (db ["world" v]) (entries (tr v))))
        food (read-food food-path)
        ;; October 2025's CPI was never published (the shutdown)
        cpi (annual-prices (read-monthly-csv cpi-path) 11)
        pink (delay (or (read-wide-monthly-csv pink-path)
                        (throw (ex-info (str "bnm.cycles-run: no " pink-path " (scripts/pinksheet.py)") {}))))
        nominal (memoize (fn [price] (annual-prices (cond (= :wti price) (read-monthly-csv wti-path)
                                                          (vector? price) (get @pink (second price))
                                                          :else (world price)))))
        prices (fn [price] (cy/deflate (nominal price) cpi))
        agreements (for [[c {:keys [price alt]}] commodities
                         :when (or (nil? (:only opts)) ((set (:only opts)) c))
                         :let [a (nominal price)]]
                     [c {:price price :first-year (first (keys a))
                         :others (for [o alt :let [b (nominal o)]] [o (agreement a b) (first (keys b))])}])
        panels (into (array-map)
                     (for [[c {:keys [price output demand] :as spec}] commodities
                           :when (or (nil? (:only opts)) ((set (:only opts)) c))]
                       [c (merge (cond-> {:price (prices price)}
                                   output (assoc :output (annual (world output)))
                                   demand (assoc :demand (annual (world demand))))
                                 (when-let [item (:food spec)] (food item)))]))
        t0 (System/currentTimeMillis)
        each (fn [[c panel]] [c (cy/commodity-rows c panel opts)])
        by (into {} (if (:parallel opts) (pmap each panels) (map each panels)))
        _ (println "calibrated in" (quot (- (System/currentTimeMillis) t0) 1000) "s")
        gaps (fn [c] (into {} (for [r (by c) :when (= (:variable r) (var-of c "price-gap"))] [(:year r) (:value r)])))
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
    (report-sources agreements)
    (report rows)
    (shutdown-agents)))
