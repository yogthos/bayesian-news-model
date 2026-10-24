(ns bnm.control-run
  "Fits the control vector (bnm.control, bead bnm-4hv.2) on
  scripts/control.py's indicators and writes
    data/derived/stocks/control.csv     iso,year,variable,value,data_year
                                        (year known): control/<axis>,
                                        -sd, control/n, control/<indicator>
    data/derived/control/control-loadings.csv
                                        spec,year,indicator,axis,loading,psi,n,rows,iter,
                                        start,objective (the EM start kept)
    data/derived/control/control-fixed.csv
                                        iso,year,axis,value: every year's
                                        snapshot scored under the last fit,
                                        a diagnostic that separates the
                                        data's movement from refitting (not
                                        causal, not a stock)
  and prints the loadings, the pinned signs and the second-axis rule
  (bnm.control's docstring). The validation is
  python -I scripts/control.py check.

    jolt -A:tools -M -m bnm.control-run"
  (:require [bnm.control :as c]
            [bnm.csv :as csv]
            [clojure.java.io :as io]))

(def in-path "data/derived/control/control-indicators.csv")
(def out-path "data/derived/stocks/control.csv")
(def loadings-path "data/derived/control/control-loadings.csv")
(def fixed-path "data/derived/control/control-fixed.csv")
(def years (range 1975 2028))
(def stability-years [1995 2000 2005 2010 2015 2020 2027])
(def threshold 0.90)

(defn- read-rows [path]
  (with-open [r (io/reader path)]
    (let [[header & rows] (csv/records r \,)
          ix (zipmap header (range))
          f (fn [row k] (nth row (ix k)))]
      (mapv (fn [row]
              {:iso (f row "iso") :year (parse-long (f row "year")) :known (parse-long (f row "known"))
               :indicator (f row "indicator") :value (parse-double (f row "value"))})
            rows))))

(defn- show-fit [label spec fit]
  (println " " label "rows" (:n fit) "iter" (:iter fit) (if (:converged fit) "" "(not converged)")
           "pinned" (vec (c/pinned fit spec)) "start kept" (:start fit)
           "log posterior" (into {} (for [[s v] (:objectives fit)] [s (format "%.1f" v)])))
  (doseq [ind (sort (keys (:loading fit))) :let [l (get-in fit [:loading ind])] :when l]
    (println (format "    %-18s %s  psi %.2f" ind
                     (apply str (map #(format " %+.2f" %) l)) (get-in fit [:psi ind])))))

(defn- fixed-rows
  "Each year's snapshot scored under `fit`."
  [rows spec fit]
  (let [inds (set (:indicators spec))
        rs (filterv #(inds (:indicator %)) rows)]
    (for [y years
          :let [snap (#'bnm.econ-factors/snapshot (filter #(<= (:known %) y) rs) y 5)]
          [iso {:keys [x]}] (sort-by key snap)
          :let [s (c/score fit x spec)] :when s
          [a {:keys [name]}] (map-indexed vector (:axes spec)) :when (nth (:ok s) a)]
      [iso y name (nth (:f s) a)])))

(defn- corr [ps]
  (let [n (count ps) xs (map first ps) ys (map second ps)
        mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sxx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
        syy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
    (/ sxy (Math/sqrt (* sxx syy)))))

(defn -main [& _]
  (let [rows (read-rows in-path)
        t0 (System/currentTimeMillis)
        _ (println (count rows) "indicator rows")
        st (c/stability rows c/spec-2 {:years stability-years :ref 2027})
        _ (println "\nsecond-axis rule (Tucker congruence against the 2027 fit; halves by ISO hash), threshold" threshold)
        _ (doseq [[a {:keys [min by-year]}] (:over-time st)]
            (println (format "  %-8s over time min %.3f  %s  split-half %.3f" a min
                             (apply str (for [[y v] by-year] (format " %d:%.3f" y v)))
                             ((:split-half st) a))))
        keep2 (and (>= (get-in st [:over-time "capture" :min]) threshold)
                   (>= ((:split-half st) "capture") threshold))
        _ (println "  second axis" (if keep2 "kept: stocks from the two-axis fit" "not stable: stocks from the one-axis fit"))
        r1 (c/expanding rows years c/spec-1)
        _ (println "one-axis expanding fits" (count (:fits r1)) (- (System/currentTimeMillis) t0) "ms")
        r2 (c/expanding rows years c/spec-2)
        _ (println "two-axis expanding fits" (count (:fits r2)) (- (System/currentTimeMillis) t0) "ms")
        [spec res] (if keep2 [c/spec-2 r2] [c/spec-1 r1])
        out (concat (c/stock-rows res spec) (c/indicator-rows rows c/indicator-stocks))]
    (with-open [w (io/writer out-path)]
      (.write w "iso,year,variable,value,data_year\n")
      (doseq [{:keys [iso year variable value data-year]} (sort-by (juxt :iso :year :variable) out)]
        (.write w (str iso "," year "," variable "," value "," data-year "\n"))))
    (println (count out) "rows to" out-path)
    (with-open [w (io/writer loadings-path)]
      (.write w "spec,year,indicator,axis,loading,psi,n,rows,iter,start,objective\n")
      (doseq [[sname sp r] [["one-axis" c/spec-1 r1] ["two-axis" c/spec-2 r2]]
              [y fit] (sort-by key (:fits r))
              ind (sort (keys (:loading fit))) :let [l (get-in fit [:loading ind])] :when l
              [a ax] (map-indexed vector (:axes fit))]
        (.write w (str sname "," y "," ind "," ax "," (nth l a) "," (get-in fit [:psi ind]) ","
                       (get-in fit [:stats ind :n]) "," (:n fit) "," (:iter fit) "," (name (:start fit)) "," (:objective fit) "\n"))))
    (println "loadings to" loadings-path)
    (let [fit (get-in res [:fits 2027])]
      (with-open [w (io/writer fixed-path)]
        (.write w "iso,year,axis,value\n")
        (doseq [[iso y a v] (fixed-rows rows spec fit)]
          (.write w (str iso "," y "," a "," v "\n")))))
    (println "fixed-fit scores to" fixed-path)
    (doseq [[sname sp r] [["one-axis" c/spec-1 r1] ["two-axis" c/spec-2 r2]]]
      (println "\n" sname "loadings by year known:")
      (doseq [y [1985 1995 2005 2015 2027] :let [fit (get-in r [:fits y])] :when fit]
        (show-fit y sp fit)))
    (let [at (fn [r y] (into {} (for [s (:scores r) :when (and (= y (:year s)) (first (:ok s)))] [(:iso s) (first (:f s))])))]
      (doseq [y [1995 2010 2025]
              :let [a (at r1 y) b (at r2 y) ps (for [[i v] a :when (b i)] [v (b i)])]]
        (println (format "state axis, one-axis against two-axis scores at %d: r %.3f over %d countries"
                         y (corr ps) (count ps)))))))
