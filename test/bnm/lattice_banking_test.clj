(ns bnm.lattice-banking-test
  (:require [bnm.lattice-banking :as lb]
            [clojure.test :refer [deftest is]]))

(deftest one-crisis-dated-twice-counts-once
  ;; Reinhart-Rogoff and the Macrohistory panel date some crises a year apart
  (is (= #{1890 1907 1931} (lb/join-years #{1890 1907} #{1891 1931})))
  (is (= #{1890 1892} (lb/join-years #{1890} #{1892})) "two years apart: two crises"))

(deftest a-flat-past-does-not-explode
  ;; imputed series held flat for years: the stretch is floored and capped
  (let [ys (merge (zipmap (range 1990 2000) (repeat 0.40)) {2000 0.41})]
    (is (<= (#'lb/stretch-at ys 2000) 5.0))
    (is (< 0.0 (#'lb/stretch-at ys 2000) 2.0) "a one-point rise on a 40% share is modest"))
  (let [ys (into {} (for [y (range 1990 2001)] [y (if (even? y) 1.0 3.0)]))]
    (is (< (Math/abs (- (#'lb/stretch-at ys 2000) (/ (- 1.0 2.0) (Math/sqrt (/ 10.0 9.0))))) 1e-9)
        "a moving series is untouched")))
