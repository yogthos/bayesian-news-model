(ns bnm.state-diag-test
  (:require [bnm.state-diag :as sd]
            [bnm.states]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(deftest states-from-long-tables
  ;; iso,year,variable,value files: {variable {iso (sorted-map year value)}},
  ;; the year being when the value is known
  (let [f (java.io.File/createTempFile "states" ".csv")]
    (spit f "iso,year,variable,value\nAAA,2000,x,1.5\nAAA,2001,x,2.0\nBBB,2000,y,\n")
    (let [s (bnm.states/read-states [(.getPath f)])]
      (is (= 2.0 (get-in s ["x" "AAA" 2001])))
      (is (nil? (get-in s ["y" "BBB" 2000])) "an empty value is unknown, not zero"))))

(deftest up-to-k-states-per-kind
  ;; each kind keeps the states passing (corrected for all it tried), the
  ;; strongest first, at most k, each with the design years' sign
  (let [r (fn [lr coef plr] {:lr lr :coef coef :placebo-lr plr})
        results {"a" (r 30.0 -0.4 1.0) "b" (r 20.0 0.3 1.0) "c" (r 12.0 0.2 1.0) "d" (r 25.0 0.5 20.0)}]
    (is (= [["a" -1] ["b" 1]] (sd/choose results 2)))
    (is (= [["a" -1] ["b" 1] ["c" 1]] (sd/choose results 5)) "d: its placebo gains nearly as much")
    (is (empty? (sd/choose {"a" (r 3.0 0.1 0.0)} 2)))))

(deftest world-states-and-exposures
  ;; a world series (iso WLD) is read as its causal z-score; a product of a
  ;; world series and a country's own (exposure to it) is a country series
  (let [st {"w" {"WLD" (sorted-map 2000 1.0 2001 2.0 2002 3.0 2003 2.0 2004 1.0 2005 10.0)}
            "c" {"AAA" (sorted-map 2002 0.5 2003 0.5) "BBB" (sorted-map 2003 0.1)}}]
    (is (= #{"w"} (set (keys (bnm.states/world-series st)))))
    (let [z (get (bnm.states/world-z (bnm.states/world-series st)) "w")]
      (is (nil? (z 2000)) "too few to scale")
      (is (> (z 2005) 1.0) "a jump above its past"))
    (let [x (bnm.states/exposures st [["w" "c"]])]
      (is (= 1.0 (get-in x ["w*c" "AAA" 2003])) "2.0 times 0.5")
      (is (< (Math/abs (- 0.2 (get-in x ["w*c" "BBB" 2003]))) 1e-12)))))
