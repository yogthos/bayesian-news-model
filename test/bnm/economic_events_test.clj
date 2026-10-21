(ns bnm.economic-events-test
  (:require [bnm.economic-events :as ee]
            [clojure.test :refer [deftest is testing]]))

(deftest inflation-crises
  ;; consumer prices up 40% or more in the year; a run of such years is one
  ;; crisis, from its first year
  (is (= #{1990 1999} (ee/inflation-crises {1988 10.0 1989 25.0 1990 45.0 1991 300.0 1992 60.0 1993 12.0 1999 41.0})))
  (is (= #{} (ee/inflation-crises {2000 39.9}))))

(deftest growth-collapses
  ;; growth of output per head more than two standard deviations below the
  ;; country's own previous twenty years (at least fifteen of them), and
  ;; negative; once in five years
  (let [steady (into {} (for [y (range 1960 1981)] [y (* 100.0 (Math/exp (* 0.02 (- y 1960))) (if (odd? y) 1.003 0.997))]))
        hit (assoc steady 1981 (* (steady 1980) 0.95) 1982 (* (steady 1980) 0.93))]
    (is (= #{1981} (ee/growth-collapses hit)) "a fall of 5% after steady 2% growth; 1982 is the same episode")
    (is (= #{} (ee/growth-collapses steady)))
    (is (= #{} (ee/growth-collapses (select-keys hit (range 1975 1983)))) "too short a past to judge")))

(deftest sovereign-crises-joined
  ;; Laeven-Valencia's and Reinhart-Rogoff's dates, the same default dated a
  ;; year apart counted once
  (is (= #{1982 2001} (ee/join #{1982 2001} #{1983 2002})))
  (is (= #{1982 1990} (ee/join #{1982} #{1990}))))

(deftest food-price-shocks
  ;; twelve-month food inflation of 15% or more and 10 points above its
  ;; own previous five years' mean, by quarter; once a year
  (let [calm (into {} (for [q (range 8000 8020)] [q 3.0]))
        shock (assoc calm 8020 20.0 8021 22.0)]
    (is (= #{8020} (ee/food-shocks shock)))
    (is (= #{} (ee/food-shocks calm)))
    (is (= #{} (ee/food-shocks (assoc (into {} (for [q (range 8000 8020)] [q 12.0])) 8020 18.0)))
        "only 6 points above its own mean")))

(deftest a-catalogue-in-the-lattice-s-form
  ;; annual onsets at the year's first month, busy that year, covered from
  ;; the first year the rule can judge to the last with data, known 18
  ;; months on
  (let [cat (ee/catalogue {"AAA" {:onsets #{1990} :from 1975 :through 2020}})]
    (is (= [(* 12 1990)] (vec (get-in cat [:onsets "AAA"]))))
    (is (= (* 12 1975) (get-in cat [:from "AAA"])))
    (is (= (+ 11 (* 12 2020)) (get-in cat [:through "AAA"])))
    (is (contains? (get-in cat [:busy "AAA"]) (+ 5 (* 12 1990))))
    (is (= 18 (:known-lag cat)))))

(deftest spending-led-consolidations
  ;; the balance improves by 1.5 points of GDP or more and spending falls by
  ;; a point or more: the spending cuts that drive unrest (Ponticelli &
  ;; Voth), at Alesina & Ardagna's threshold; once in three years
  (let [bal {2009 -15.4 2010 -11.4 2011 -10.5 2012 -9.3 2013 -13.6 2014 -3.8}
        exp {2009 54.8 2010 53.1 2011 55.1 2012 57.9 2013 63.9 2014 51.5}]
    (is (= #{2010 2014} (ee/consolidations bal exp)) "Greece: 2010 (+4.0, -1.7) and 2014 (+9.8, -12.4)")
    (is (= #{} (ee/consolidations {2000 -5.0 2001 -3.0} {2000 40.0 2001 40.5})) "a tax-led one is not")))

(deftest runs-start-a-crisis
  (is (= #{1982 1990} (ee/onsets-of-runs #{1982 1983 1984 1990}))))
