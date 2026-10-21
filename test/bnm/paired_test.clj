(ns bnm.paired-test
  (:require [augur.math :as m]
            [bnm.paired :as pd]
            [clojure.test :refer [deftest is testing]]))

(deftest timing-error-is-the-between-quarter-miscalibration
  ;; rows [q p y]: the error of each quarter's mean forecast against its
  ;; rate, weighted by its rows
  (let [right (concat (for [_ (range 10)] [1 0.2 false]) [[1 0.2 true] [1 0.2 true]]
                      (for [_ (range 10)] [2 0.0 false]))
        flat (map (fn [[q _ y]] [q 0.1 y]) right)]
    (is (< (pd/timing-error (concat (for [_ (range 8)] [1 0.2 false]) [[1 0.2 true] [1 0.2 true]]
                                    (for [_ (range 10)] [2 0.0 false])))
           1e-12)
        "each quarter's mean forecast is its rate")
    (is (> (pd/timing-error flat) (pd/timing-error right)))))

(deftest pooled-recall-ranks-across-quarters
  ;; all of a hot quarter's rows outrank a calm quarter's, which the
  ;; within-quarter recall cannot see
  (let [rows [[1 0.9 true] [1 0.8 true] [1 0.7 false] [1 0.6 false]
              [2 0.1 false] [2 0.05 false] [2 0.04 false] [2 0.03 false] [2 0.02 false] [2 0.01 false]]]
    (is (= 1.0 (pd/pooled-recall rows 0.2)) "the top two of ten are both onsets")
    (is (= 0.5 (pd/pooled-recall rows 0.1)))))

(deftest simulated-outcomes-follow-the-model-not-the-data
  (let [rows (vec (for [u (range 200) q (range 40)] {:unit (str "u" u) :q q :pb 0.05}))
        sim (pd/simulate rows 1.0 (m/rng 1))
        rate (fn [rs] (/ (count (filter :y rs)) (double (count rs))))]
    (testing "the model's probability carries the signal the bench lacks"
      (is (every? #(and (< 0.0 (:pm %) 1.0)) sim))
      (let [hi (filter #(> (:pm %) 0.1) sim) lo (filter #(< (:pm %) 0.02) sim)]
        (is (> (rate hi) (* 3 (rate lo))))))
    (testing "the signal is shared within a unit's year"
      (let [by (group-by (juxt :unit #(quot (:q %) 4)) sim)]
        (is (every? #(apply = (map :pm %)) (vals by)))))
    (testing "no signal, no difference"
      (is (every? #(= (:pm %) (:pb %)) (pd/simulate rows 0.0 (m/rng 2)))))))
