(ns bnm.trees-test
  (:require [bnm.hazard :as hz]
            [bnm.math :as m]
            [bnm.trees :as trees]
            [clojure.test :refer [deftest is testing]]))

(defn- rows [n seed f]
  (let [g (m/rng seed)]
    (vec (for [_ (range n)
               :let [a (- (m/next-double! g) 0.5) b (- (m/next-double! g) 0.5) c (m/next-double! g)]]
           {:x (double-array [1.0 a b c]) :y (f a b c g) :w 1.0}))))

(deftest learns-an-interaction-a-logit-cannot
  ;; onset when exactly one of two inputs is high: no linear term carries it
  (let [xor (fn [a b _ g] (if (not= (pos? a) (pos? b)) (< (m/next-double! g) 0.9) (< (m/next-double! g) 0.1)))
        train (rows 2000 1 xor) test (rows 1000 2 xor)
        model (trees/fit train {:rounds 50})
        logit (hz/fit train 4 {:prior 1.0})
        ys (map :y test)]
    (is (> (hz/auc (map #(trees/predict model (:x %)) test) ys) 0.85))
    (is (< (hz/auc (map #(hz/predict logit (:x %)) test) ys) 0.6))))

(deftest predictions-are-probabilities-near-the-rate
  (let [train (rows 500 3 (fn [_ _ _ g] (< (m/next-double! g) 0.2)))
        model (trees/fit train {:rounds 20})
        ps (map #(trees/predict model (:x %)) train)]
    (is (every? #(< 0.0 % 1.0) ps))
    (is (< (Math/abs (- 0.2 (/ (reduce + ps) (count ps)))) 0.05))))

(deftest weights-count
  (let [train (mapv (fn [r] (assoc r :w (if (:y r) 1.0 0.25))) (rows 1000 4 (fn [_ _ _ g] (< (m/next-double! g) 0.2))))
        model (trees/fit train {:rounds 0})]
    (testing "with no rounds, the weighted base rate: 0.2 / (0.2 + 0.8 x 0.25) = 0.5"
      (is (< (Math/abs (- 0.5 (trees/predict model (double-array [1.0 0.0 0.0 0.0])))) 0.05)))))

(deftest deterministic
  (let [train (rows 300 5 (fn [a _ _ g] (< (m/next-double! g) (if (pos? a) 0.6 0.2))))]
    (is (= (trees/fit train {:rounds 10}) (trees/fit train {:rounds 10})))))
