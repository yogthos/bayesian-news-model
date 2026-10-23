(ns bnm.econ-divergence
  "Asset-growth divergence by country and year (bead bnm-3ka.7;
  docs/ECONOMIC-CHARACTER.md 8a div/*, H4): asset values against what
  produces them, each against the country's own history.

  Input: scripts/econ-divergence.py's long table (iso, year = data year,
  known, indicator, value), every value a log. For each indicator and
  country the gap at data year y is the value less the mean of the
  country's values at data years y-25..y-1 (at least `min-history` of
  them): a proportional deviation from the trailing quarter century, known
  when the latest of its inputs is. A trailing mean, not a trend: the
  rise of wealth over income is itself the divergence K5 names, and a
  trend would take it out.

  Variables (econ/divergence-*):
    equity-jst  JST equity price over GDP (17 countries from the 1890s)
    mcap        WDI/GFDD market capitalisation over GDP (from 1975)
    equity      equity-jst where held, else mcap (both log equity value
                over GDP)
    valuation   JST price over dividends
    house       JST house prices over GDP per head
    wealth      WID private wealth over national income
    cape        Shiller CAPE (US)
    z1          Fed Z.1 NFC equity over net operating surplus (US)
  and econ/divergence, the composite: the mean of equity, house and wealth
  (those present), each over its spread across every country's gaps known
  by the composite's known year (the latest of its parts').

  Rows {:iso :year :known :data-year :variable :value} with :year the year
  known (the stocks convention, so bnm.states reads them), :known the same
  and :data-year the data year."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def window "Trailing years a gap is measured against." 25)
(def min-history "Prior years needed for a gap." 10)
(def min-scale "Known gaps needed to scale a composite part." 30)

(def components
  "Stock variable suffix -> input indicator."
  {"equity-jst" "equity-gdp" "mcap" "mcap-gdp" "valuation" "price-dividend"
   "house" "house-income" "wealth" "wealth-income" "cape" "cape" "z1" "equity-surplus"})

(def composite-parts ["equity" "house" "wealth"])

(defn read-inputs
  "{indicator {iso (sorted-map data-year [known value])}}."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (reduce (fn [acc l]
                (let [f (str/split l #"," -1) g #(nth f (ix %))]
                  (update-in acc [(g "indicator") (g "iso")] (fnil assoc (sorted-map))
                             (parse-long (g "year")) [(parse-long (g "known")) (parse-double (g "value"))])))
              {} ls))))

(defn gap
  "[known gap] at data year y of `s` ({data-year [known value]}): the value
  less the mean over the trailing window, known when the latest input is;
  nil without the value or enough history."
  [s y]
  (when-let [[k v] (get s y)]
    (let [prior (keep #(get s %) (range (- y window) y))]
      (when (>= (count prior) min-history)
        [(reduce max k (map first prior))
         (- v (/ (reduce + (map second prior)) (count prior)))]))))

(defn gaps
  "{iso (sorted-map data-year [known gap])} of an indicator's series."
  [by-iso]
  (into {} (for [[iso s] by-iso
                 :let [g (into (sorted-map) (keep (fn [y] (when-let [x (gap s y)] [y x])) (keys s)))]
                 :when (seq g)]
             [iso g])))

(defn known-sd
  "The spread of every gap of `part` (in `gs`, {part {iso {data-year [known
  gap]}}}) known by year k, around their mean; nil under two (or `n`)."
  ([gs part k] (known-sd gs part k 2))
  ([gs part k n]
   (let [xs (for [[_ byy] (get gs part) [_ [kn x]] byy :when (<= kn k)] x)
         c (count xs)]
     (when (>= c (max 2 n))
       (let [mu (/ (reduce + xs) c)]
         (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) c)))))))

(defn- merge-equity
  "JST's equity gap where held, else the market cap one."
  [jst mcap]
  (merge-with (fn [m j] (merge m j)) mcap jst))

(defn component-gaps
  "{suffix {iso {data-year [known gap]}}} from the inputs."
  [inputs]
  (let [gs (into {} (for [[suffix ind] components :let [g (gaps (get inputs ind))] :when (seq g)] [suffix g]))]
    (assoc gs "equity" (merge-equity (get gs "equity-jst") (get gs "mcap")))))

(defn composite
  "{iso {data-year [known value]}}: the mean of the parts present, each
  over its spread known by the composite's known year."
  ([gs] (composite gs min-scale))
  ([gs n]
   (let [isos (distinct (mapcat #(keys (get gs %)) composite-parts))]
     (into {}
           (for [iso isos
                 :let [years (distinct (mapcat #(keys (get-in gs [% iso])) composite-parts))
                       byy (into (sorted-map)
                                 (for [d years
                                       :let [ps (keep #(when-let [[k x] (get-in gs [% iso d])] [% k x]) composite-parts)
                                             k (when (seq ps) (reduce max (map second ps)))
                                             zs (keep (fn [[p _ x]] (when-let [sd (known-sd gs p k n)] (when (pos? sd) (/ x sd)))) ps)]
                                       :when (seq zs)]
                                   [d [k (/ (reduce + zs) (count zs))]]))]
                 :when (seq byy)]
             [iso byy])))))

(defn stock-rows
  "The stock rows of every variable, sorted."
  ([inputs] (stock-rows inputs min-scale))
  ([inputs n]
   (let [gs (component-gaps inputs)
         all (assoc (update-keys gs #(str "econ/divergence-" %)) "econ/divergence" (composite gs n))]
     (->> (for [[v by-iso] all [iso byy] by-iso [d [k x]] byy]
            {:iso iso :year k :known k :data-year d :variable v :value x})
          (sort-by (juxt :variable :iso :data-year))
          vec))))

(defn write-stock
  "iso,year,variable,value,known,data_year (year = known)."
  [rows path]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value,known,data_year\n")
    (doseq [{:keys [iso year variable value known data-year]} rows]
      (.write w (str iso "," year "," variable "," (format "%.6f" (double value)) "," known "," data-year "\n")))))

(defn read-stock
  "{variable {iso (sorted-map data-year [known value])}} of a stock written
  by write-stock (or any stock with known and data_year columns)."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (reduce (fn [acc l]
                (let [f (str/split l #"," -1) g #(nth f (ix %))]
                  (update-in acc [(g "variable") (g "iso")] (fnil assoc (sorted-map))
                             (parse-long (g "data_year")) [(parse-long (g "known")) (parse-double (g "value"))])))
              {} ls))))

(def inputs-path "data/derived/econ/divergence-inputs.csv")
(def stock-path "data/derived/stocks/econ-divergence.csv")

(defn -main
  "jolt -A:tools -M -m bnm.econ-divergence: inputs to the stock, with a
  coverage line per variable."
  [& _]
  (let [rows (stock-rows (read-inputs inputs-path))]
    (write-stock rows stock-path)
    (doseq [[v rs] (sort-by key (group-by :variable rows))]
      (println (format "%-28s %4d countries  data %d-%d  %6d rows" v (count (distinct (map :iso rs)))
                       (apply min (map :data-year rs)) (apply max (map :data-year rs)) (count rs))))
    (println "wrote" stock-path)))
