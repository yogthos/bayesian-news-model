(ns bnm.econ-factors-run
  "Fits the economic-character factors (bnm.econ-factors) on
  scripts/econ-factors.py's indicators and writes
    data/derived/stocks/econ-factors.csv   iso,year,variable,value (year known)
    data/derived/econ/econ-factors-loadings.csv
                                           year,concept,indicator,loading,psi,n,iter,rows
  the expanding-window loadings for each year (beads bnm-3ka.4, bnm-3ka.5),
  and those of the balanced-panel diagnostics (bnm.econ-factors/diagnostics;
  an index's loading is its sign, with no psi). n is the indicator's
  observations, rows the fit's country-years.
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

(defn- opts-of [spec]
  (select-keys spec [:anchors :control :balanced :method :signs :min-k]))

(defn- loading-rows
  "[indicator loading psi n] of a fit; an index's loading is its sign."
  [{:keys [method loading signs psi stats]}]
  (if (= :index method)
    (for [j (sort (keys signs))] [j (double (signs j)) nil (:n (stats j))])
    (for [j (sort (keys loading))] [j (loading j) (psi j) (:n (stats j))])))

(defn- run [rows specs years]
  (into (sorted-map)
        (for [[concept {:keys [indicators] :as spec}] specs
              :let [inds (set indicators)
                    rs (filterv #(inds (:indicator %)) rows)
                    t0 (System/currentTimeMillis)
                    res (ef/expanding rs years (opts-of spec))]]
          (do (println concept (count rs) "rows," (count (:fits res)) "fits,"
                       (count (:scores res)) "scores," (- (System/currentTimeMillis) t0) "ms")
              [concept res]))))

(defn- show [specs results]
  (doseq [[c res] results]
    (println)
    (println c (if (= :index (:method (specs c))) "signs (index)" "loadings (psi) by year known;")
             "balanced" (:balanced (specs c)) "anchors" (:anchors (specs c)))
    (doseq [y [1990 2000 2009 2015 2020 2027]
            :let [{:keys [n iter flipped anchor] :as fit} (get (:fits res) y)]
            :when fit]
      (println " " y "n" n "iter" iter "anchor" anchor (if flipped "(flipped)" "")
               (apply str (for [[j l p] (loading-rows fit)]
                            (if p (format " %s %+.2f (%.2f)" j l p) (format " %s %+.0f" j l))))))))

(defn -main [& _]
  (let [rows (read-rows in-path)
        results (run rows ef/concepts years)
        diag (run rows ef/diagnostics [2000 2009 2019 2027])
        out (sort-by (juxt :iso :year :variable) (mapcat (fn [[c res]] (ef/stock-rows c res)) results))]
    (with-open [w (io/writer out-path)]
      (.write w "iso,year,variable,value\n")
      (doseq [{:keys [iso year variable value]} out]
        (.write w (str iso "," year "," variable "," value "\n"))))
    (println (count out) "rows to" out-path)
    (with-open [w (io/writer loadings-path)]
      (.write w "year,concept,indicator,loading,psi,n,iter,rows\n")
      (doseq [[c res] (concat results diag)
              [y fit] (sort-by key (:fits res))
              [j l p n] (loading-rows fit)]
        (.write w (str y "," c "," j "," l "," (if p p "") "," n "," (or (:iter fit) "") "," (:n fit) "\n"))))
    (println "loadings to" loadings-path)
    (show ef/concepts results)
    (println)
    (println "diagnostics (loadings only)")
    (show ef/diagnostics diag)))
