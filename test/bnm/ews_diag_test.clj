(ns bnm.ews-diag-test
  (:require [augur.math]
            [bnm.ews]
            [bnm.ews-diag :as ed]
            [clojure.test :refer [deftest is]]))

(deftest one-warning-per-kind-from-the-design-years
  ;; of a kind's (series, indicator) results, those gaining past its
  ;; forecast (LR >= 3.84) and at least twice their placebo's; the one that
  ;; gains most, its sign the design years'; none, no warning
  (let [r (fn [lr coef plr] {:lr lr :coef coef :placebo-lr plr})]
    (is (= ["economy/gdp_growth" :variance -1]
           (ed/choose {["economy/gdp_growth" :variance] (r 27.8 -1.0 0.6)
                       ["economy/inflation" :ac1] (r 6.3 -0.2 0.2)
                       ["economy/inflation" :variance] (r 11.8 -30.0 6.3)})))
    (is (nil? (ed/choose {["economy/inflation" :variance] (r 11.8 -30.0 6.3)})) "its placebo gains half as much")
    (is (nil? (ed/choose {["economy/inflation" :ac1] (r 3.0 0.2 0.0)})))))

(deftest rows-only-where-both-are-known
  (let [o (fn [y] (+ 11 (* 12 y)))
        years (sorted-map (o 1980) [0.1 true] (o 1981) [0.2 false] (o 1982) [0.3 nil])
        x {["A" (o 1980)] 1.0 ["B" (o 1980)] 2.0 ["A" (o 1981)] 3.0}
        rs (vec (ed/rows years ["A"] {"A" "B"} (fn [c o] (x [c o])) 2002))]
    (is (= 1 (count rs)) "1981's placebo unknown, 1982 unresolved")
    (is (= [[1.0 1.0] [1.0 2.0]] (map :x (first rs))))))

(deftest own-indicators-over-calendar-years
  ;; a gap is a missing year, and each country's first own part is left out
  (let [g (augur.math/rng 3)
        panel (into {} (for [c ["A" "B" "C" "D"]]
                         [c (into (sorted-map) (for [y (range 1960 2000) :when (not (and (= c "A") (= y 1980)))]
                                                 [y (augur.math/normal! g)]))]))
        tree {"A" "X" "B" "X" "C" "Y" "D" "Y" "X" "world" "Y" "world"}
        sig (bnm.ews/own-indicators panel tree 10)]
    (is (nil? (get-in sig ["A" 1985])) "a window over 1980, missing")
    (is (some? (get-in sig ["A" 1995])))
    (is (nil? (get-in sig ["B" 1969])) "1960 left out: the first full window ends in 1970")
    (is (some? (get-in sig ["B" 1970])))))
