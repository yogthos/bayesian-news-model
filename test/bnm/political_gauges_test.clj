(ns bnm.political-gauges-test
  (:require [bnm.political-gauges :as pg]
            [clojure.test :refer [deftest is]]))

(def sets
  {:onsets {"coup" {"AAA" (sorted-set (* 4 1960) (* 4 1990))}}
   :spans {"coup" {"AAA" (sorted-set (* 4 1960) (inc (* 4 1960)) (* 4 1990))}}
   :lags {"coup" 1}})

(deftest the-long-catalogue-fills-the-years-before-the-modern
  (let [cat (pg/catalogue {:kind "coup" :sets sets :coverage-q (* 4 2025)
                           :long {"AAA" #{1900 1950} "BBB" #{1930}}
                           :states {"AAA" [1880 2026] "BBB" [1920 2026] "CCC" [1990 2026]}
                           :long-from 1875 :modern 1945})]
    (is (= [(* 12 1900) (* 12 1960) (* 12 1990)] (vec (get-in cat [:onsets "AAA"])))
        "1950 is the modern catalogue's to date: the long one's onset after 1945 is dropped")
    (is (= (* 12 1880) (get-in cat [:from "AAA"])) "from when the state entered the system")
    (is (= (* 12 1920) (get-in cat [:from "BBB"])))
    (is (= (* 12 1990) (get-in cat [:from "CCC"])) "a state that entered late, with no onsets, is still covered")
    (is (= (* 3 (inc (* 4 2025))) (get-in cat [:through "AAA"])))
    (is (contains? (get-in cat [:busy "AAA"]) (+ 3 (* 12 1960))) "the modern spans are busy months")
    (is (= 6 (:known-lag cat)) "known a quarter late, plus the quarter itself")))

(deftest a-catalogue-without-long-years-starts-at-the-modern
  (let [cat (pg/catalogue {:kind "coup" :sets sets :coverage-q (* 4 2025) :long {}
                           :states {"AAA" [1880 2026]} :long-from 1980 :modern 1980})]
    (is (= (* 12 1980) (get-in cat [:from "AAA"])))))

(deftest middle-regimes-are-the-anocracies
  (is (= 1.0 (pg/anocracy 0.5)))
  (is (= 0.0 (pg/anocracy 1.0)))
  (is (= 0.0 (pg/anocracy 0.0)))
  (is (nil? (pg/anocracy nil))))

(deftest the-year-row-reads-only-the-past
  (let [annual {"vdem_polyarchy" {"AAA" {1997 0.2 2000 0.5 2001 0.9}}
                "mad_gdppc" {"AAA" {1996 100.0 1999 121.0 2000 150.0}}}
        row (pg/year-row annual (fn [_ _ _] nil) "AAA" 2000)
        ix (zipmap pg/year-names (range))]
    (is (= (count pg/year-names) (count row)))
    (is (= 0.5 (nth row (ix :polyarchy))) "V-Dem's year is published by the next origin")
    (is (< (Math/abs (- (nth row (ix :polyarchy3)) 0.3)) 1e-12) "against three years before")
    (is (< (Math/abs (- (nth row (ix :growth3)) (Math/log 1.21))) 1e-12)
        "national accounts a year further back: 1999 against 1996")
    (is (nil? (nth row (ix :infant-mortality))) "no WDI: nil, not zero")))
