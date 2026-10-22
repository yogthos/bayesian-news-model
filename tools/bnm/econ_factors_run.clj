(ns bnm.econ-factors-run
  "Fits the economic-character factors (bnm.econ-factors) on
  scripts/econ-factors.py's indicators and writes
    data/derived/stocks/econ-factors.csv   iso,year,variable,value (year known)
    data/derived/econ/econ-factors-loadings.csv
                                           year,concept,indicator,loading,psi,n
  the expanding-window loadings for each year (beads bnm-3ka.4, bnm-3ka.5).
  The validation is scripts/econ-factors-check.py.

    jolt -A:tools -M -m bnm.econ-factors-run"
  (:require [bnm.csv :as csv]
            [bnm.econ-factors :as ef]
            [clojure.java.io :as io]))

(def in-path "data/derived/econ/econ-indicators.csv")
(def out-path "data/derived/stocks/econ-factors.csv")
(def loadings-path "data/derived/econ/econ-factors-loadings.csv")
(def years (range 1975 2028))

(defn- read-rows [path]
  (with-open [r (io/reader path)]
    (let [[header & rows] (csv/records r \,)
          ix (zipmap header (range))
          f (fn [row k] (nth row (ix k)))]
      (mapv (fn [row]
              {:iso (f row "iso") :year (parse-long (f row "year")) :known (parse-long (f row "known"))
               :concept (f row "concept") :indicator (f row "indicator") :value (parse-double (f row "value"))})
            rows))))

(defn -main [& _]
  (let [rows (read-rows in-path)
        results (into {} (for [[concept {:keys [indicators anchors control]}] ef/concepts
                               :let [inds (set indicators)
                                     rs (filterv #(inds (:indicator %)) rows)
                                     t0 (System/currentTimeMillis)
                                     res (ef/expanding rs years {:anchors anchors :control control})]]
                           (do (println concept (count rs) "rows," (count (:fits res)) "fits,"
                                        (count (:scores res)) "scores," (- (System/currentTimeMillis) t0) "ms")
                               [concept res])))
        out (sort-by (juxt :iso :year :variable) (mapcat (fn [[c res]] (ef/stock-rows c res)) results))]
    (with-open [w (io/writer out-path)]
      (.write w "iso,year,variable,value\n")
      (doseq [{:keys [iso year variable value]} out]
        (.write w (str iso "," year "," variable "," value "\n"))))
    (println (count out) "rows to" out-path)
    (with-open [w (io/writer loadings-path)]
      (.write w "year,concept,indicator,loading,psi,n,iter\n")
      (doseq [[c res] (sort-by key results)
              [y {:keys [loading psi stats iter]}] (sort-by key (:fits res))
              j (sort (keys loading))]
        (.write w (str y "," c "," j "," (loading j) "," (psi j) "," (:n (stats j)) "," iter "\n"))))
    (println "loadings to" loadings-path)
    (doseq [[c res] (sort-by key results)]
      (println)
      (println c "loadings (psi) by year known; anchors" (:anchors (ef/concepts c)))
      (doseq [y [1990 2000 2009 2015 2020 2027]
              :let [{:keys [loading psi n iter flipped anchor]} (get (:fits res) y)]
              :when loading]
        (println " " y "n" n "iter" iter "anchor" anchor (if flipped "(flipped)" "")
                 (apply str (for [j (sort (keys loading))]
                              (format " %s %+.2f (%.2f)" j (loading j) (psi j)))))))))
