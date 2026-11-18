(ns bnm.nq-score-test
  (:require [bnm.alerts :as al]
            [bnm.economic-events :as ee]
            [bnm.kinds]
            [bnm.nq-score :as nq]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(deftest bnm-56c-38-causal-climatology
  ;; at origin t the rate among the rows already resolved: issued at s,
  ;; known at s + 3 + lag <= t; (n1 + 0.5) / (n + 1), 0.5 before any
  (let [rows {["AAA" 2] [0.1 true] ["BBB" 2] [0.1 false] ["AAA" 5] [0.1 false] ["AAA" 8] [0.1 false]
              ["AAA" 11] [0.1 true] ["AAA" 14] [0.1 false]}
        c (nq/causal-clim rows 3)]
    (is (= 0.5 (c ["AAA" 2])) "nothing known yet")
    (is (= 0.5 (c ["AAA" 5])))
    (is (= (/ 1.5 3.0) (c ["AAA" 8])) "origin 2's two rows, known at 8")
    (is (= (/ 1.5 4.0) (c ["AAA" 11])) "and 5's, known at 11")
    (is (= (/ 1.5 5.0) (c ["AAA" 14])) "and 8's; 11's not until 17")))

(deftest bnm-56c-38-the-quarter-lead-s-implied-hazard
  ;; the quarter-lead issued at t - 3 for (t, t + 9]: its hazard for (t, t + 3]
  (let [ql {["AAA" 2] 0.271 ["AAA" 5] 0.5}
        imp (nq/implied ql)]
    (is (< (Math/abs (- (- 1.0 (Math/pow 0.729 (/ 1.0 3.0))) (imp ["AAA" 5]))) 1e-12))
    (is (nil? (imp ["AAA" 2])) "no quarter-lead issued at t - 3")))

(deftest bnm-56c-38-the-lags-are-the-catalogues'
  (is (= ee/food-publication-lag (nq/lags "food_price_shock")) "the monthly food catalogue's")
  (is (= ee/quarterly-growth-lag (nq/lags "growth_collapse")) "the quarterly growth catalogue's")
  (when (.exists (java.io.File. "data/slow/events.tsv"))
    (let [cats (bnm.kinds/catalogues)]
      (doseq [k ["market_crash" "coup" "protest_surge" "violence_surge"]]
        (is (= (:known-lag (cats k)) (nq/lags k)) k)))))

(deftest bnm-56c-38-alerts-read-a-scale-at-every-origin
  ;; the year at December origins as before; the next quarter at every one
  (let [f (java.io.File/createTempFile "nq-fc" ".tsv")]
    (spit f (str "year\tAAA\t" (+ 11 (* 12 2005)) "\t0.3\ttrue\n"
                 "year\tAAA\t" (+ 2 (* 12 2005)) "\t0.3\ttrue\n"
                 "quarter-next\tAAA\t" (+ 2 (* 12 2005)) "\t0.25\tfalse\n"
                 "quarter-next\tAAA\t" (+ 5 (* 12 2005)) "\t0.25\t\n"
                 "quarter-next\tworld\t" (+ 5 (* 12 2005)) "\t0.25\ttrue\n"))
    (is (= {["AAA" (+ 11 (* 12 2005))] [0.3 true]} (al/read-node (str f) :country 2003 2022)))
    (is (= {["AAA" (+ 2 (* 12 2005))] [0.25 false]} (al/read-node (str f) :country 2003 2022 "quarter-next")))
    (io/delete-file f)))
