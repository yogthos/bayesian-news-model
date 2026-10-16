(ns bnm.hazard-test
  (:require [bnm.hazard :as hz]
            [bnm.math :as m]
            [clojure.test :refer [deftest is testing]]))

(defn- synthetic [n seed]
  ;; onsets more likely where x1 is high, unrelated to x2
  (let [g (m/rng seed)]
    (vec (for [_ (range n)]
           (let [x1 (m/normal! g) x2 (m/normal! g)
                 p (m/expit (+ -3.0 (* 1.5 x1)))]
             {:x (double-array [1.0 x1 x2]) :y (< (m/next-double! g) p) :w 1.0})))))

(deftest fits-a-logistic-hazard
  (let [rows (synthetic 4000 1)
        beta (hz/fit rows 3 {:prior 1.0})]
    (is (< 1.2 (aget beta 1) 1.8) (vec beta))
    (is (< (Math/abs (aget beta 2)) 0.2) (vec beta))
    (testing "a warm start converges to the same place"
      (let [b2 (hz/fit rows 3 {:prior 1.0 :start beta :iterations 2})]
        (is (< (Math/abs (- (aget b2 1) (aget beta 1))) 1e-3))))))

(deftest scores-rare-event-forecasts
  (let [ys [true false false true false false false false]
        good [0.9 0.1 0.2 0.8 0.1 0.3 0.2 0.1]
        flat (repeat 8 0.25)]
    (is (= 1.0 (hz/auc good ys)))
    (is (= 0.5 (hz/auc flat ys)))
    (is (= 1.0 (hz/avg-precision good ys)))
    (testing "recall when flagging the top quarter of each period"
      (is (= 1.0 (hz/recall-at-budget (map vector (repeat 0) good ys) 0.25)))
      (is (= 0.0 (hz/recall-at-budget (map vector (repeat 0) (reverse good) ys) 0.25))))))
