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
  (let [rows (fn [pb] (vec (for [u (range 200) q (range 40)] {:unit (str "u" u) :q q :pb pb})))
        cells {["k" "lead" "e"] (rows 0.05) ["k" "w2" "e"] (rows 0.06) ["j" "lead" "e"] (rows 0.05)}
        sim (pd/simulate cells 1.0 (m/rng 1) true)
        rate (fn [rs] (/ (count (filter :y rs)) (double (count rs))))
        lo (fn [p] (- (Math/log p) (Math/log (- 1.0 p))))]
    (testing "the model's probability carries the signal the bench lacks"
      (let [rs (sim ["k" "lead" "e"])
            hi (filter #(> (:pm %) 0.1) rs) low (filter #(< (:pm %) 0.02) rs)]
        (is (> (rate hi) (* 3 (rate low))))))
    (testing "one signal per kind, unit and year, whatever the window"
      (let [shift (fn [r] (- (lo (:pm r)) (lo (:pb r))))
            by (fn [c] (into {} (map (juxt (juxt :unit :q) shift) (sim c))))
            a (by ["k" "lead" "e"]) b (by ["k" "w2" "e"]) c (by ["j" "lead" "e"])]
        (is (every? #(< (Math/abs (- (a %) (b %))) 1e-9) (keys a)) "windows of a kind share it")
        (is (not-every? #(< (Math/abs (- (a %) (c %))) 1e-9) (keys a)) "kinds do not")
        (is (every? (fn [[_ rs]] (apply = (map shift rs))) (group-by (juxt :unit #(quot (:q %) 4)) (sim ["k" "lead" "e"]))))))
    (testing "windows of a kind draw their outcomes together"
      (let [ya (into {} (map (juxt (juxt :unit :q) :y) (sim ["k" "lead" "e"])))
            yb (into {} (map (juxt (juxt :unit :q) :y) (sim ["k" "w2" "e"])))
            agree (/ (count (filter #(= (ya %) (yb %)) (keys ya))) (double (count ya)))]
        (is (> agree 0.97) agree)))
    (testing "the null: outcomes from the bench, the model's shift is noise"
      (let [null (pd/simulate cells 1.0 (m/rng 2) false)
            rs (null ["k" "lead" "e"])
            hi (filter #(> (:pm %) 0.1) rs) low (filter #(< (:pm %) 0.02) rs)]
        (is (< (Math/abs (- (rate hi) (rate low))) 0.03))))))
