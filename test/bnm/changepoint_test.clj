(ns bnm.changepoint-test
  (:require [bnm.changepoint :as cp]
            [bnm.math :as m]
            [clojure.test :refer [deftest is]]))

(defn- series [g n f] (vec (for [t (range n)] (+ (f t) (* 0.3 (m/normal! g))))))

(deftest a-shift-in-level-is-a-recent-break
  (let [g (m/rng 3)
        xs (series g 60 #(if (< % 40) 0.0 2.0))
        out (cp/run xs {:hazard (/ 1.0 20) :recent 2})]
    (is (= 60 (count out)))
    (is (> (:recent (out 41)) 0.5) "just after the shift")
    (is (< (:recent (out 38)) 0.2) "in the long calm before it")
    (is (< (:run (out 41)) (:run (out 38))) "the expected run length restarts")))

(deftest a-steady-series-rarely-breaks
  (let [g (m/rng 4)
        out (cp/run (series g 80 (constantly 1.0)) {:hazard (/ 1.0 20) :recent 2})]
    (is (< (/ (reduce + (map :recent (drop 10 out))) 70.0) 0.2))
    (is (> (:run (last out)) 20.0))))
