(ns bnm.buffers-run
  "The buffer model's schema and invariants on what is on disk (bead
  bnm-56c.3, docs/BUFFERS.md), descriptive only, nothing fitted:

  1. the stocks the engine declares (data/derived/coupled/graph.edn, with
     World3's stock-flow sectors for their flows) and the mapping file's
     extensions: how many, by module, and which have no declared flows;
  2. world grain cover from USDA PSD around 2007-08: ending stocks over
     use, and over net drawdown (use less production), world and world
     without China (whose stocks are off the market);
  3. Sri Lanka's foreign reserves (EWN, scripts/ewn-reserves.py) up to
     2022: cover over net drawdown, and WDI's months of imports.

  Writes data/derived/buffers/report.txt.

    jolt -A:tools -M -m bnm.buffers-run"
  (:require [bnm.buffers :as b]
            [bnm.world3 :as w3]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def out-path "data/derived/buffers/report.txt")

(defn split-csv
  "The fields of one CSV line, double-quoted fields allowed to hold commas."
  [^String l]
  (loop [i 0 cur (StringBuilder.) q false out []]
    (if (>= i (count l))
      (conj out (str cur))
      (let [c (.charAt l i)]
        (cond (= c \") (recur (inc i) cur (not q) out)
              (and (= c \,) (not q)) (recur (inc i) (StringBuilder.) q (conj out (str cur)))
              :else (recur (inc i) (.append cur c) q out))))))

(defn- fmt [x] (cond (nil? x) "-" (number? x) (format "%.2f" (double x)) :else (str x)))

;; -- 1. the engine's stocks ----------------------------------------------------------

(defn engine-section []
  (let [mods (b/read-graph "data/derived/coupled/graph.edn")
        {:keys [records levels]} (b/engine-stocks mods {:models (w3/sectors)})
        {:keys [extensions]} (b/read-mappings "resources/buffers/stocks.edn")
        records (b/apply-extensions records extensions)
        rep (b/report records)
        mat (filter #(= :material (:kind %)) records)
        by-label (fn [rs] (frequencies (map #(get-in % [:holder :unit]) rs)))
        flowed (by-label (remove #(and (empty? (:inflows %)) (empty? (:outflows %))) mat))]
    (concat
     ["# 1. Stocks declared by the engine's modules (graph.edn; World3 flows from bnm.world3/sectors)"
      (str "modules " (count mods) ", stock records " (:stocks rep) " (material " (:material rep)
           ", information " (- (:stocks rep) (:material rep)) "), level emits " (count levels))
      "module: records (material, with flows)"]
     (for [[k n] (sort-by (comp str key) (by-label records))]
       (str "  " k ": " n " (" (get (by-label mat) k 0) ", " (get flowed k 0) ")"))
     [(str "material stocks with no declared flows: " (count (:no-flows rep)))]
     (for [[k ids] (sort-by key (group-by #(first (str/split % #"/")) (:no-flows rep)))]
       (str "  " k " (" (count ids) "): " (str/join ", " (distinct (map #(last (str/split % #"/")) ids)))))
     [""])))

;; -- 2. world grain ---------------------------------------------------------------------

(def psd-items {"Wheat" "wheat" "Corn" "maize" "Rice, Milled" "rice"})
(def psd-attrs {"Ending Stocks" :stock "Domestic Consumption" :outflow "Production" :inflow})

(defn read-psd
  "{[item attr ex-china?] {market-year 1000 MT}}: world sums, with and
  without China."
  []
  (with-open [z (java.util.zip.ZipInputStream. (io/input-stream "data/raw/material/usda-psd/psd_grains_pulses_csv.zip"))]
    (.getNextEntry z)
    (reduce (fn [acc l]
              (let [f (split-csv l) item (psd-items (nth f 1 nil)) attr (psd-attrs (nth f 8 nil))]
                (if (and item attr)
                  (let [y (parse-long (nth f 4)) v (parse-double (nth f 11)) cn? (= "CH" (nth f 2))]
                    (cond-> (update-in acc [[item attr false] y] (fnil + 0.0) v)
                      (not cn?) (update-in [[item attr true] y] (fnil + 0.0) v)))
                  acc)))
            {} (rest (line-seq (io/reader z))))))

(defn grain-section []
  (let [psd (read-psd)
        recs (into {} (map (juxt :id identity)) (:records (b/read-mappings "resources/buffers/stocks.edn")))]
    (concat
     ["# 2. World grain, USDA PSD (current vintage, revised since; known a year after the market year)"
      "stocks-to-use in months; cover over net drawdown (use less production, flows) in months at windows of 1 and 3 years;"
      "'growing' where production covered use over the window (cover long, at the cap)"]
     (for [item ["wheat" "maize" "rice"] ex [false true]
           :let [rec (b/record (assoc (recs (str "grain/WLD/" item)) :usable {:share 1.0 :sd 0.0}))
                 ser (into {} (for [[a k] [[:stock :stock] [:outflow :outflow] [:inflow :inflow]]]
                                [k (vec (for [[y v] (sort (psd [item a ex]))] {:t y :v v}))]))]
           line (cons (str item (if ex " (world less China)" " (world)"))
                      (for [as-of (range 2003 2013)
                            :let [st (b/latest rec (:stock ser) as-of) use (b/latest rec (:outflow ser) as-of)
                                  cv (b/cover rec ser as-of {:windows [1 3] :cap 100.0})
                                  c (fn [w] (let [r (first (filter #(and (= :cover (:invariant %)) (= w (:window %))) cv))]
                                              (cond (contains? (:flags r) :not-draining) "growing" (contains? (:flags r) :capped) "over 100y" :else (fmt (some-> (:value r) (* 12))))))]]
                        (str "  as of " as-of " (data " (:t st) "): stocks-to-use " (fmt (* 12 (/ (:v st) (:v use))))
                             "  cover 1y " (c 1) "  3y " (c 3))))]
       line)
     [""])))

;; -- 3. Sri Lanka's reserves -----------------------------------------------------------------

(defn read-reserves [country]
  (with-open [r (io/reader "data/derived/buffers/reserves.csv")]
    (vec (for [l (rest (line-seq r)) :let [f (split-csv l)] :when (= country (nth f 1))]
           {:t (parse-long (nth f 2)) :v (parse-double (nth f 4)) :known (parse-long (nth f 3))}))))

(defn read-slow [iso v]
  (with-open [r (io/reader "data/slow/series.tsv")]
    (vec (for [l (line-seq r) :let [f (str/split l #"\t")] :when (and (= iso (first f)) (= v (second f)))]
           {:t (parse-long (subs (nth f 3) 0 4)) :v (parse-double (nth f 5))
            :known (parse-long (subs (nth f 4) 0 4))}))))

(defn reserves-section []
  (if-not (.exists (io/file "data/derived/buffers/reserves.csv"))
    ["# 3. Reserves: data/derived/buffers/reserves.csv missing (python -I scripts/ewn-reserves.py)" ""]
    (let [rec (b/record {:id "reserves/LKA/fx" :name "fx reserves" :unit "USD m" :time-unit "year"
                         :holder {:unit "monetary authority" :place "LKA"} :measure {:frequency 1 :lag 1}
                         :usable {:share 1.0 :sd 0.0}})
          ser {:stock (read-reserves "Sri Lanka")}
          months (read-slow "LKA" "debt/reserves_months")]
      (concat
       ["# 3. Sri Lanka's foreign reserves (EWN, FX reserves less gold, USD m; known a year after)"
        "cover over net drawdown (the stock's own trend) in years at windows of 1 and 3 years; WDI months of imports as known"]
       (for [as-of (range 2015 2025)
             :let [st (b/latest rec (:stock ser) as-of)
                   cv (b/cover rec ser as-of {:windows [1 3] :cap 100.0})
                   c (fn [w] (let [r (first (filter #(and (= :cover (:invariant %)) (= w (:window %))) cv))]
                               (cond (contains? (:flags r) :not-draining) "growing" (contains? (:flags r) :capped) "over 100y"
                                     :else (str (fmt (:value r)) (when (:exhaustion r) (str " (out " (fmt (:exhaustion r)) ")"))))))
                   m (b/latest rec months as-of)]]
         (str "  as of " as-of " (data " (:t st) "): reserves " (fmt (:v st)) "  cover 1y " (c 1) "  3y " (c 3)
              "  months of imports " (fmt (:v m)) " (" (:t m) ")"))
       ["  usable share: PLAN's Sri Lanka figure, about $50m usable of $1.8bn official in May 2022 (0.03), would cut any cover 36-fold;"
        "  no usable-share series is on disk, so none is applied above." ""]))))

(defn -main [& _]
  (let [lines (concat (engine-section) (grain-section) (reserves-section))
        text (str (str/join "\n" lines) "\n")]
    (io/make-parents out-path)
    (spit out-path text)
    (print text)
    (flush)))
