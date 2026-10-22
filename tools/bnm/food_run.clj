(ns bnm.food-run
  "Writes data/derived/stocks/food.csv (iso,year,variable,value; year the
  year a value is known at, a data year's values known the year after;
  variables in bnm.food's docstring) from

    data/raw/material/faostat-fbs/fbs-cereals.csv   FAOSTAT Food Balance
        Sheets, wheat, maize, rice (scripts/food-data.py; FAO. FAOSTAT,
        CC BY 4.0)
    data/derived/material/cereal-trade.csv, cereal-trade-coverage.csv
        the FAOSTAT detailed trade matrix, mirror-filled (scripts/material.py)

  with FAOSTAT crop production from data/slow/series.tsv where a country
  has no balance sheet, and prints spot checks, and as a check of sign (not a fit), how each
  cereal's world storage pressure moved with its price the year it was
  known (data/slow/series.tsv, FRED's IMF prices, 1992-).

    jolt -A:tools -M -m bnm.food-run"
  (:require [bnm.food :as food]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def fbs-path "data/raw/material/faostat-fbs/fbs-cereals.csv")
(def trade-path "data/derived/material/cereal-trade.csv")
(def coverage-path "data/derived/material/cereal-trade-coverage.csv")
(def series-path "data/slow/series.tsv")
(def out-path "data/derived/stocks/food.csv")

(def ^:private fbs-field
  {"production" :production "imports" :imports "exports" :exports "supply" :use})

(defn- lines [path]
  (with-open [r (io/reader path)] (vec (rest (line-seq r)))))

(defn read-fbs
  "{[iso year item] {:production :imports :exports :use}}; the world's own
  rows (WLD) left out, the world being the countries' sum."
  [path]
  (reduce (fn [acc l]
            (let [[iso y item el v] (str/split l #"," -1)]
              (if-let [k (fbs-field el)]
                (if (= iso food/world) acc (assoc-in acc [[iso (parse-long y) item] k] (parse-double v)))
                acc)))
          {} (lines path)))

(defn read-trade [path]
  (mapv (fn [l] (let [[imp ex y item t source] (str/split l #"," -1)]
                  {:importer imp :exporter ex :year (parse-long y) :item item :tonnes (parse-double t) :source source}))
        (lines path)))

(defn read-coverage [path]
  (into {} (map (fn [l] (let [[iso y item status] (str/split l #"," -1)] [[iso (parse-long y) item] status])))
        (lines path)))

(defn read-crops
  "{[iso year item] t}: FAOSTAT crop production from the slow store (rice
  paddy, converted to milled at 0.667 as the balance sheets' new method),
  each value's year its period's."
  [path]
  (let [want {"food/wheat_production" ["wheat" 1.0] "food/maize_production" ["maize" 1.0]
              "food/rice_production" ["rice" 0.667]}]
    (with-open [r (io/reader path)]
      (reduce (fn [acc l]
                (if (str/includes? l "_production\t")
                  (let [[unit v _ period _ x] (str/split l #"\t" -1)]
                    (if-let [[item k] (want v)]
                      (if (and (re-matches #"[A-Z]{3}" unit) (not= unit food/world))
                        (assoc acc [unit (parse-long (subs period 0 4)) item] (* k (parse-double x)))
                        acc)
                      acc))
                  acc))
              {} (line-seq r)))))

(defn write-rows [path rows]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value\n")
    (doseq [{:keys [iso year variable value]} rows]
      (.write w (str iso "," year "," variable "," value "\n")))))

(defn- annual-log-prices
  "{item {year mean log price}} of the world's monthly cereal prices."
  [path]
  (let [want {"food/wheat" "wheat" "food/maize" "maize" "food/rice" "rice"}
        acc (with-open [r (io/reader path)]
              (reduce (fn [acc l]
                        (if (str/starts-with? l "world\tfood/")
                          (let [[_ v _ period _ x] (str/split l #"\t" -1)]
                            (if-let [item (want v)]
                              (update-in acc [item (parse-long (subs period 0 4))] (fnil conj []) (Math/log (parse-double x)))
                              acc))
                          acc))
                      {} (line-seq r)))]
    (update-vals acc (fn [ys] (into {} (for [[y xs] ys :when (= 12 (count xs))] [y (/ (reduce + xs) 12.0)]))))))

(defn- corr [pairs]
  (let [n (count pairs)]
    (when (> n 2)
      (let [xs (map first pairs) ys (map second pairs)
            mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
            sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
            sxx (reduce + (map #(* (- % mx) (- % mx)) xs))
            syy (reduce + (map #(* (- % my) (- % my)) ys))]
        (/ sxy (Math/sqrt (* sxx syy)))))))

(defn- report [rows]
  (let [idx (into {} (map (juxt (juxt :iso :year :variable) :value)) rows)
        g (fn [iso y v] (idx [iso y v]))
        f (fn [x] (if x (format "%.3f" (double x)) "-"))
        mt (fn [x] (if x (format "%.1f" (/ (double x) 1e6)) "-"))]
    (println (count rows) "rows," (count (distinct (map :iso rows))) "places,"
             (apply min (map :year rows)) "-" (apply max (map :year rows)) "->" out-path)
    (println "\nworld (year known; data year one before):  stocks-to-use  pressure  stock Mt  use Mt")
    (doseq [item ["wheat" "maize" "rice" "grains"]]
      (println " " item)
      (doseq [y [1975 1990 2000 2002 2005 2007 2008 2009 2010 2011 2012 2015 2020 2024]]
        (println "   " y (f (g "WLD" y (str item "_stocks_to_use"))) (f (g "WLD" y (str item "_storage_pressure")))
                 (mt (g "WLD" y (str item "_stock"))) (mt (g "WLD" y (str item "_use"))))))
    (println "\nwheat import dependence / supplier HHI by year known:")
    (doseq [iso ["EGY" "DZA" "YEM" "LBN" "TUN" "IDN" "BGD" "JPN" "CHN" "IND" "USA" "RUS"]]
      (println " " iso (str/join " " (for [y [1995 2000 2005 2008 2011 2015 2020 2024]]
                                      (str y ":" (f (g iso y "wheat_import_dependence")) "/" (f (g iso y "wheat_supplier_hhi")))))))
    (println "\ngrains import dependence by year known:")
    (doseq [iso ["EGY" "SAU" "JPN" "KOR" "NGA" "MEX" "BRA" "CHN"]]
      (println " " iso (str/join " " (for [y [1995 2005 2011 2020]] (str y ":" (f (g iso y "grains_import_dependence")))))))
    (when (.exists (io/file series-path))
      (println "\nsign check: world storage pressure known at y against the change in the mean log price from y-1 to y")
      (let [prices (annual-log-prices series-path)]
        (doseq [item ["wheat" "maize" "rice"]
                :let [p (prices item)
                      pairs (for [y (range 1993 2030)
                                  :let [x (g "WLD" y (str item "_storage_pressure"))]
                                  :when (and x (p y) (p (dec y)))]
                              [x (- (p y) (p (dec y)))])
                      level (for [y (range 1992 2030)
                                  :let [x (g "WLD" y (str item "_storage_pressure"))]
                                  :when (and x (p y))]
                              [x (p y)])]]
          (println " " item "n" (count pairs) "corr(pressure, d log price)" (f (corr pairs))
                   " corr(pressure, log price)" (f (corr level))))))))

(defn -main [& _]
  (let [fbs (read-fbs fbs-path)
        trade (read-trade trade-path)
        coverage (read-coverage coverage-path)
        rows (food/food-rows {:fbs fbs
                              :imports (food/imports-of trade coverage {})
                              :exports (food/exports-of trade coverage {})
                              :production (read-crops series-path)}
                             {})]
    (write-rows out-path rows)
    (report rows)))
