(ns bnm.credit-run-test
  (:require [bnm.credit-run :as cr]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-6))

(deftest the-hp-trend-of-a-line-is-the-line
  ;; a line has no second differences: nothing to smooth
  (let [ys (mapv #(+ 2.0 (* 0.5 %)) (range 30))]
    (is (every? true? (map close? ys (cr/hp-trend ys 1600.0))))))

(deftest with-no-penalty-the-trend-is-the-series
  (let [ys [1.0 3.0 2.0 5.0 4.0 4.5]]
    (is (every? true? (map close? ys (cr/hp-trend ys 0.0))))))

(deftest the-trend-solves-the-hp-problem
  ;; (I + lambda D'D) tau = y: the first-order condition of
  ;; sum (y - tau)^2 + lambda sum (second difference of tau)^2
  (let [ys [1.0 4.0 2.0 7.0 3.0 8.0 6.0 9.0]
        lam 10.0
        tau (cr/hp-trend ys lam)
        n (count ys)
        d2 (fn [i] (+ (tau i) (* -2.0 (tau (inc i))) (tau (+ i 2))))
        ;; (D'D tau)_k = sum over i of D[i,k] * (D tau)_i
        dtd (fn [k] (reduce + (for [i (range (- n 2)) :let [c (case (- k i) 0 1.0 1 -2.0 2 1.0 0.0)]] (* c (d2 i)))))]
    (doseq [k (range n)]
      (is (close? (ys k) (+ (tau k) (* lam (dtd k))))))))

(deftest the-gap-is-one-sided
  ;; the gap at a year end uses only data years before it, published by
  ;; it: later data, and later publication of earlier data, change nothing
  (let [series (into (sorted-map) (for [y (range 1960 1990)] [y [(inc y) (+ 0.5 (* 0.01 (- y 1960)) (if (odd? y) 0.02 0.0))]]))
        later (into series (for [y (range 1990 2000)] [y [(inc y) 3.0]]))
        g (cr/gap-at {:d-bis series} 1985 {})
        g' (cr/gap-at {:d-bis later} 1985 {})]
    (is (some? g))
    (is (= g g') "data after the year end are not read")
    (is (= 1984 (:data-year g)) "data years before 1985, published by its end")
    (testing "a value published late is not known on time"
      (let [late (assoc series 1984 [1987 9.0])]
        (is (= 1983 (:data-year (cr/gap-at {:d-bis late} 1985 {}))))))))

(deftest a-boom-opens-a-positive-gap
  (let [flat (into (sorted-map) (for [y (range 1960 1980)] [y [(inc y) 1.0]]))
        boom (into flat (for [y (range 1980 1985)] [y [(inc y) (+ 1.0 (* 0.08 (- y 1979)))]]))
        g (:gap (cr/gap-at {:d-bis boom} 1986 {}))]
    (is (> g 10.0) "in percentage points of GDP: 40 above a trend that lags it")
    (is (< g 40.0))))

(deftest too-short-a-series-has-no-gap
  (let [s (into (sorted-map) (for [y (range 1990 1995)] [y [(inc y) 1.0]]))]
    (is (nil? (cr/gap-at {:d-bis s} 1996 {:min-years 10})))))

(deftest one-source-at-a-time
  ;; the source as keen chooses it (bnm.keen/choose-d: the first with 20
  ;; data years known); the gap from its own series, never spliced
  (let [jst (into (sorted-map) (for [y (range 1950 1990)] [y [(inc y) 0.5]]))
        bis (into (sorted-map) (for [y (range 1975 1990)] [y [(inc y) 1.5]]))
        g80 (cr/gap-at {:d-bis bis :d-jst jst} 1990 {})
        g00 (cr/gap-at {:d-bis (into bis (for [y (range 1990 2000)] [y [(inc y) 1.5]])) :d-jst jst} 2000 {})]
    (is (= 2 (:source g80)) "BIS has 15 years known in 1990: JST's 40")
    (is (close? 0.0 (:gap g80)))
    (is (= 1 (:source g00)) "by 2000 BIS has 25")
    (is (close? 0.0 (:gap g00)) "a flat series, no jump from the other source's level")))
