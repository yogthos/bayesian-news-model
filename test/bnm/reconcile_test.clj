(ns bnm.reconcile-test
  (:require [bnm.lattice :as lt]
            [bnm.reconcile :as rc]
            [clojure.test :refer [deftest is]]))

(deftest quarters-reconciled-with-the-latest-year
  (let [rows [{:scale :year :place "AAA" :origin 11 :p 0.5}
              {:scale :year :place "AAA" :origin 23 :p 0.05}
              {:scale :quarter-lead :place "AAA" :origin 14 :p 0.1 :y false}
              {:scale :quarter-w2 :place "AAA" :origin 14 :p 0.1 :y true}
              {:scale :quarter-lead :place "AAA" :origin 23 :p 0.1 :y nil}
              {:scale :quarter-w2 :place "AAA" :origin 23 :p 0.1 :y nil}
              {:scale :quarter-lead :place "AAA" :origin 5 :p 0.1 :y nil}
              {:scale :quarter-w2 :place "AAA" :origin 5 :p 0.1 :y nil}]
        out (rc/reconciled rows {:quarter-lead 9 :quarter-w2 12 :year 24})
        by (into {} (for [r out] [[(:scale r) (:origin r)] r]))
        r0 (- 1.0 (Math/pow 0.5 (/ 3.0 24.0)))
        [a b] (lt/reconcile 0.1 0.1 r0 0.5)]
    (is (< (Math/abs (- a (:p (by [:quarter-lead 14])))) 1e-12) "origin 14 reads the year issued at 11")
    (is (< (Math/abs (- b (:p (by [:quarter-w2 14])))) 1e-12))
    (is (true? (:y (by [:quarter-w2 14]))) "outcomes carried")
    (is (= (:p (by [:quarter-lead 14])) (:p (by [:quarter-lead 23]))) "origin 23 reads 11's, not its own origin's")
    (is (nil? (by [:quarter-lead 5])) "no year forecast yet: left out")))
