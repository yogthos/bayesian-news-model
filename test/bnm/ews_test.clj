(ns bnm.ews-test
  (:require [augur.math :as m]
            [bnm.ews :as ews]
            [clojure.test :refer [deftest is testing]]))

(defn- ar1
  "An AR(1) series whose persistence rises from phi0 to phi1: a system
  losing resilience as it nears a tipping point."
  [seed n phi0 phi1]
  (let [g (m/rng seed)]
    (vec (reductions (fn [x i] (+ (* (+ phi0 (* (- phi1 phi0) (/ i (double n)))) x) (m/normal! g))) 0.0 (range 1 n)))))

(deftest slowing-down-shows-before-the-tip
  (let [xs (ar1 1 600 0.2 0.95)
        sig (ews/indicators xs {:window 100})]
    (testing "lag-1 autocorrelation and variance rise as resilience falls"
      (is (< (:ac1 (nth sig 150)) (:ac1 (nth sig 590))))
      (is (< (:variance (nth sig 150)) (:variance (nth sig 590))))
      (is (< 0.0 (:ac1 (nth sig 590)) 1.0)))
    (testing "the return rate: how fast a departure decays, falling"
      (is (> (:return-rate (nth sig 150)) (:return-rate (nth sig 590)))))
    (testing "the trend of a signal over the window (Kendall's tau), positive"
      (is (> (:ac1-trend (nth sig 590)) 0.5)))))

(deftest causal-windows
  ;; each indicator at i from the window ending at i: nothing after it
  (let [xs (ar1 2 300 0.3 0.3)
        a (ews/indicators xs {:window 50})
        b (ews/indicators (into (subvec xs 0 200) (repeat 100 100.0)) {:window 50})]
    (is (= (subvec a 0 200) (subvec b 0 200)))
    (is (every? nil? (map :ac1 (take 49 a))) "not before a full window")))

(deftest a-steady-system-shows-nothing
  (let [sig (ews/indicators (ar1 3 600 0.3 0.3) {:window 100})]
    (is (< (Math/abs (- (:ac1 (nth sig 300)) (:ac1 (nth sig 590)))) 0.25))
    (is (< (Math/abs (:ac1-trend (nth sig 590))) 0.6))))

(deftest detrending
  ;; a trend is not slowing down: the window's linear trend is removed first
  (let [xs (mapv + (ar1 4 400 0.3 0.3) (map #(* 0.5 %) (range 400)))
        sig (ews/indicators xs {:window 100})]
    (is (< (:ac1 (nth sig 390)) 0.6))))
