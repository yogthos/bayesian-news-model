(ns bnm.pit-test
  (:require [bnm.eval :as ev]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-12))

(deftest non-randomized-pit
  (testing "the predictive cdf just below and at the outcome, from sorted samples"
    (is (= [0.25 0.75] (ev/pit-bounds (double-array [0.0 1.0 1.0 2.0]) 1.0)))
    (is (= [1.0 1.0] (ev/pit-bounds (double-array [0.0 1.0]) 5.0)))
    (is (= [0.0 0.0] (ev/pit-bounds (double-array [3.0 4.0]) 1.0))))
  (testing "the histogram spreads each outcome's mass uniformly over its interval (Czado et al. 2009)"
    (let [h (ev/pit-histogram [[0.0 0.5] [0.5 1.0]] 2)]
      (is (close? 0.5 (first h)))
      (is (close? 0.5 (second h))))
    (let [h (ev/pit-histogram [[0.0 1.0]] 4)]
      (is (every? #(close? 0.25 %) h)))
    (testing "a point mass at a bin edge falls in the bin below"
      (is (= [1.0 0.0] (mapv double (ev/pit-histogram [[0.5 0.5]] 2)))))))

(deftest escalation-probability-is-a-smooth-tail
  ;; from a hundred draws a counted frequency is zero for most nodes, and the
  ;; ties throw away the ranking; the log-normal tail of the sample keeps it
  (let [h 13
        draws (fn [m] (double-array (map #(* m (Math/exp (* 0.2 (- (/ % 50.0) 1.0)))) (range 100))))
        lo (ev/escalation-p (draws 1.0) 1.0 h)
        hi (ev/escalation-p (draws 1.2) 1.0 h)]
    (is (< 0.0 lo hi 1.0) "no draw reaches the bar, yet closer is likelier")
    (is (> (ev/escalation-p (draws 3.0) 1.0 h) 0.9) "far above the bar: nearly certain")
    (is (= 0.0 (ev/escalation-p (double-array 100) 1.0 h)) "nothing at all: zero")))
