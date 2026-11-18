(ns bnm.testbed-test
  "docs/TESTBED.md: the synthetic crisis world, its lattice spec and its
  scores."
  (:require [augur.math]
            [bnm.stocks :as sk]
            [bnm.testbed :as tb]
            [clojure.test :refer [deftest is testing]]))

(def small {:seed 3 :countries 24 :regions 4 :years 60})

(deftest the-world-is-reproducible-and-published-with-a-lag
  (let [a (tb/world small) b (tb/world small) c (tb/world (assoc small :seed 4))]
    (is (= (:onsets a) (:onsets b)) "the same seed, the same world")
    (is (not= (:onsets a) (:onsets c)))
    (is (= 24 (count (:countries a))))
    (testing "every stock is a slow-table series, read as of a month"
      (let [u (first (:countries a))
            s (get (:db a) [u "credit"])
            y (+ tb/epoch 30) dec (+ (* 12 y) 11)]
        (is (some? s))
        (is (nil? (sk/value s (- (* 12 tb/epoch) 1))) "nothing before the first year is published")
        (is (= (sk/value s (+ dec (:lag-months tb/defaults)))
               (get-in a [:observed u "credit" y]))
            "the year's value, published its lag after the year ends")
        (is (not= (sk/value s dec) (get-in a [:observed u "credit" y])) "not yet at the year's end")))))

(deftest crises-follow-the-planted-hazard
  (let [w (tb/world (assoc small :countries 60 :years 80))
        rows (for [[[u y] p] (:hazard w) :when (tb/at-risk? w u y)] [p (contains? (get-in w [:onsets u] #{}) y)])
        no-signal (augur.math/expit (:base tb/defaults))
        hi (filter #(> (first %) 0.1) rows) lo (filter #(<= (first %) (+ no-signal 1e-12)) rows)
        rate (fn [rs] (/ (count (filter second rs)) (double (max 1 (count rs)))))]
    (is (seq hi)) (is (seq lo))
    (is (> (rate hi) (* 3 (rate lo))) [(rate hi) (rate lo)])
    (testing "a crisis lasts its spell: no new onset while one is under way"
      (doseq [u (:countries w) :let [os (sort (get-in w [:onsets u] #{}))]]
        (is (every? (fn [[a b]] (>= (- b a) (:spell tb/defaults))) (partition 2 1 os)) u)))))

(deftest the-null-world-has-no-signal
  (let [w (tb/world (assoc small :scenario :null :countries 60 :years 80))]
    (is (apply = (vals (:hazard w))) "one rate everywhere")
    (is (pos? (count (mapcat val (:onsets w)))))))

(deftest scores-on-hand-made-rows
  (let [rows [{:year 1 :p 0.9 :y true} {:year 1 :p 0.1 :y false} {:year 1 :p 0.2 :y false}
              {:year 2 :p 0.3 :y true} {:year 2 :p 0.4 :y false}]]
    (is (= 2.5 (* 3 (tb/auc rows))) "ranks: 5 of 6 pairs ordered, the 0.3 onset below the 0.4 quiet")
    (is (= 0.75 (tb/within-year-auc [{:year 2 :p 0.3 :y true} {:year 2 :p 0.4 :y false} {:year 2 :p 0.5 :y true} {:year 2 :p 0.1 :y false}])))
    (is (= 0.0 (tb/alert-cost [{:p 0.9 :y true} {:p 0.1 :y false}] 0.2 4.0)))
    (is (= (/ 4.0 2) (tb/alert-cost [{:p 0.1 :y true} {:p 0.1 :y false}] 0.2 4.0)) "a miss costs 4")
    (is (< (Math/abs (- 0.5 (tb/molchan-area (for [i (range 400)] {:p (Math/sin (* 7.1 i)) :y (zero? (mod i 5))})))) 0.1)
        "a forecast unrelated to the outcomes: about 0.5")
    (is (< (tb/molchan-area [{:p 0.9 :y true} {:p 0.8 :y true} {:p 0.1 :y false} {:p 0.2 :y false}]) 0.3)
        "a perfect ranking: the misses fall away with few alarms")))

(deftest a-scenario-runs-end-to-end
  (let [r (tb/evaluate (assoc small :scenario :credit-waves :countries 16 :years 40))]
    (is (every? #(contains? r %) [:auc :within-auc :level-r :brier :log-skill :oracle-log-skill :ece :alert-cost :constant-cost :molchan]))
    (is (<= 0.0 (:auc r) 1.0))
    (is (< (:oracle-log-skill r) 1.0))))
