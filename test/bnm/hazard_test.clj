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
    (testing "AUC within periods ignores how rates move between them"
      ;; period 1 has the higher scores and most positives, but within each
      ;; period the ranking is reversed
      (let [rows [[0 0.1 true] [0 0.2 false] [1 0.8 true] [1 0.9 false] [1 0.85 true]]]
        (is (> (hz/auc (map second rows) (map #(nth % 2) rows)) 0.3))
        (is (= 0.0 (hz/auc-within rows))))
      (is (nil? (hz/auc-within [[0 0.3 true] [1 0.2 false]])) "no period has both outcomes"))
    (testing "recall when flagging the top quarter of each period"
      (is (= 1.0 (hz/recall-at-budget (map vector (repeat 0) good ys) 0.25)))
      (is (= 0.0 (hz/recall-at-budget (map vector (repeat 0) (reverse good) ys) 0.25))))))

(deftest stays-finite-on-rare-separable-events
  ;; few positives, many inputs on large scales, one that separates the
  ;; positives: the fit must not run away, even from a bad start
  (let [g (m/rng 4)
        rows (vec (for [i (range 3000)]
                    (let [y (< i 30)
                          xs (concat [1.0 (if y 3.0 0.0)]
                                     (for [_ (range 60)] (* 50.0 (m/normal! g))))]
                      {:x (double-array xs) :y y :w (if y 1.0 6.7)})))
        cold (hz/fit rows 62 {:prior 2.0})
        ;; warm-started far from the answer, as a yearly refit can be
        warm (hz/fit rows 62 {:prior 2.0 :start (double-array 62 3.0)})
        ps (map #(hz/predict warm (:x %)) rows)]
    (is (every? #(not (Double/isNaN %)) warm))
    (is (every? #(<= 0.0 % 1.0) ps))
    (is (> (hz/auc ps (map :y rows)) 0.9))
    (is (< (Math/abs (- (hz/predict cold (:x (rows 0))) (hz/predict warm (:x (rows 0))))) 1e-3)
        "a bad start ends where a cold one does")))

(deftest combines-forecasters
  ;; one forecaster knows which unit (a fixed risk), another knows when (a
  ;; common shock); onsets need both
  (let [g (m/rng 7)
        rows (vec (for [t (range 200) u (range 20)]
                    (let [risky (< u 4) shock (zero? (rem t 5))
                          p (m/expit (+ -5.0 (if risky 2.5 0.0) (if shock 2.5 0.0)))]
                      {:who (m/expit (+ -4.0 (if risky 2.0 0.0)))
                       :when (m/expit (+ -4.0 (if shock 2.0 0.0)))
                       :noise (m/next-double! g)
                       :y (< (m/next-double! g) p)})))
        ys (map :y rows)
        ws (repeat (count rows) 1.0)]
    (testing "a linear pool puts its weight on forecasters that help"
      (let [w (hz/pool-weights (map (juxt :who :noise) rows) ys ws)]
        (is (> (first w) 0.8) (vec w))
        (is (< (Math/abs (- 1.0 (reduce + w))) 1e-9))))
    (testing "a log-linear stack beats each forecaster when they know different things"
      (let [st (hz/fit-stack (map (juxt :who :when) rows) ys ws {:prior 1.0})
            ps (map #(hz/predict-stack st [(:who %) (:when %)]) rows)
            ll (fn [ps] (reduce + (map (fn [p y] (Math/log (if y p (- 1.0 p)))) ps ys)))]
        (is (> (ll ps) (ll (map :who rows))))
        (is (> (ll ps) (ll (map :when rows))))))))
