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
