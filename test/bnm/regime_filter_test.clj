(ns bnm.regime-filter-test
  "bnm-56c.40, recursive step 5b: the calm/stress chain filtered from world
  gauges read at the origin (docs/RECURSIVE.md step 5b)."
  (:require [clojure.test :refer [deftest is testing]]
            [bnm.regime-filter :as rf]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

(deftest bnm-56c-40-the-theory-chains
  (testing "financial: p_cc 0.85, p_ss 0.6, stress share 0.27, persistence 0.45"
    (is (close? (rf/stationary rf/financial) (/ 0.15 0.55) 1e-12))
    (is (close? (rf/persistence rf/financial) 0.45 1e-12))
    (is (= [1.0 -1.0 1.0] (:signs rf/financial))))
  (testing "food: p_cc 0.9, p_ss 0.5"
    (is (close? (rf/stationary rf/food) (/ 0.1 0.6) 1e-12))
    (is (close? (rf/persistence rf/food) 0.4 1e-12)))
  (testing "the window factor is the chain's mean decay over W years"
    (is (close? (rf/window-factor rf/financial 2.0) (/ (- (* 0.45 0.45) 1.0) (* 2.0 (Math/log 0.45))) 1e-12))
    (is (close? (rf/window-factor rf/financial 2.0) 0.4994 1e-3))
    (is (close? (rf/window-factor rf/food 2.0) 0.458 1e-3))))

(deftest bnm-56c-40-the-emissions-make-a-standardized-gauge
  ;; the two regimes' gauge means, weighted by the stationary law, have mean
  ;; 0 and the mixture variance 1, and they sit two within-regime sds apart
  (doseq [p [rf/financial rf/food]]
    (let [{:keys [pbar sw delta]} (rf/emission p)
          calm (- (* pbar delta)) stress (* (- 1.0 pbar) delta)
          mean (+ (* (- 1.0 pbar) calm) (* pbar stress))
          var (+ (* sw sw) (* (- 1.0 pbar) calm calm) (* pbar stress stress))]
      (is (close? mean 0.0 1e-12) p)
      (is (close? var 1.0 1e-12) p)
      (is (close? delta (* 2.0 sw) 1e-12) p))))

(deftest bnm-56c-40-the-filter
  (let [p rf/financial pbar (rf/stationary p)]
    (testing "with nothing read the chain stays at its stationary law"
      (is (close? (rf/step p pbar [nil nil nil]) pbar 1e-12))
      (is (every? #(close? % pbar 1e-12) (rf/path p (repeat 10 [nil nil nil]))))
      (is (close? (rf/step p pbar []) pbar 1e-12)))
    (testing "the theory signs: a widening spread, an inverted curve, a rise in the VIX read as stress"
      (is (> (rf/step p pbar [2.0 nil nil]) 0.8))
      (is (> (rf/step p pbar [nil -2.0 nil]) 0.8))
      (is (> (rf/step p pbar [nil nil 2.0]) 0.8))
      (is (< (rf/step p pbar [-1.0 1.0 -1.0]) 0.1)))
    (testing "a run of benign readings accumulates into a calm; one benign reading in a stress spell does not end it"
      (let [calm (rf/path p (repeat 6 [-0.3 0.3 -0.3]))]
        (is (apply >= calm))
        (is (< (last calm) (first calm))))
      (let [[a b] (rf/path p [[2.5 -2.0 2.5] [0.0 0.0 0.0]])
            fresh (rf/step p pbar [0.0 0.0 0.0])]
        (is (> a 0.95))
        (is (> b fresh) [b fresh])))
    (testing "saturating: a 5 sd reading is a sure stress, not five times a 1 sd one"
      (is (< (rf/step p pbar [5.0 nil nil]) 1.0))
      (is (> (rf/step p pbar [5.0 nil nil]) 0.999)))
    (testing "correlated gauges are tempered: three agreeing gauges move the chain less than three independent ones would"
      (let [one (rf/step p pbar [1.0 nil nil])
            three (rf/step p pbar [1.0 -1.0 1.0])
            untempered (rf/step (assoc p :r 0.0) pbar [1.0 -1.0 1.0])]
        (is (< one three untempered) [one three untempered])))))

(deftest bnm-56c-40-the-reader-has-no-lookahead
  ;; pi at origin t reads the gauges of the origins up to t only: gauges after
  ;; a cut redrawn leave every pi before it unchanged
  (let [origins (vec (range 11 600 12))
        g1 (fn [t] [(Math/sin (* 0.37 t)) nil (Math/cos (* 0.11 t))])
        g2 (fn [t] (if (> t 300) [3.0 -3.0 3.0] (g1 t)))
        r1 (rf/reader rf/financial origins g1 2.0)
        r2 (rf/reader rf/financial origins g2 2.0)]
    (doseq [t origins :when (<= t 300)]
      (is (= (r1 t) (r2 t)) t))
    (is (not= (r1 323) (r2 323)))
    (testing "the loading is f (pi - pbar); an origin the node does not have reads nil"
      (let [[x pi] (r1 59)]
        (is (close? x (* (rf/window-factor rf/financial 2.0) (- pi (rf/stationary rf/financial))) 1e-12)))
      (is (nil? (r1 60))))))
