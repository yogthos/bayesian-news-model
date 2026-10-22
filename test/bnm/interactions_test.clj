(ns bnm.interactions-test
  (:require [augur.math :as m]
            [bnm.interactions :as ix]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(defn- expit [e] (/ 1.0 (+ 1.0 (Math/exp (- e)))))

;; --- readers of monthly series as known at a year end -------------------------

(def obs
  ;; [period-month avail-year value]: months of 1999-2001, each published
  ;; the year after its own year except 2001's, published in 2001
  (vec (for [p (range (* 12 1999) (* 12 2002))
             :let [y (quot p 12)]]
         [p (if (= y 2001) 2001 (inc y)) (double (- p (* 12 1999)))])))

(deftest latest-known-reads-only-what-was-published
  (is (= [(+ (* 12 1999) 11) 11.0] (ix/latest-known obs 2000 24))
      "at the end of 2000 only 1999 is out")
  (is (= [(+ (* 12 2001) 11) 35.0] (ix/latest-known obs 2001 24)))
  (is (nil? (ix/latest-known obs 1999 24)) "nothing published by 1999")
  (is (nil? (ix/latest-known obs 2003 6)) "the latest known is too old"))

(deftest changes-drawdowns-and-window-means
  (is (= 12.0 (ix/monthly-change obs 2001 12 24)) "35 - 23")
  (is (nil? (ix/monthly-change obs 2000 12 24)) "no month 12 before 1999-12")
  (is (= 29.5 (ix/window-mean obs 2001 12 24)) "the mean of months 24..35")
  (let [up-down [[0 0 100.0] [1 0 200.0] [2 0 50.0]]]
    (is (close? (Math/log 4.0) (ix/drawdown up-down 0 24 24) 1e-12) "log peak less log now")
    (is (= 0.0 (ix/drawdown [[0 0 1.0] [1 0 2.0]] 0 24 24)) "at a peak")))

(deftest peers-onsets-are-counted-when-known
  (let [onsets {"A" (sorted-set 100) "B" (sorted-set 90 110) "C" (sorted-set 95)}
        peers {"A" ["A" "B" "C"] "B" ["A" "B" "C"] "C" ["A" "B" "C"]}]
    ;; window (o - lag - 36, o - lag] with o 120 and lag 12: (72, 108]
    (is (= 2 (ix/peer-count onsets peers "A" 120 12 36)) "B's 90 and C's 95, not its own")
    (is (= 2 (ix/peer-count onsets peers "B" 120 12 36)) "A's 100 and C's 95; B's 110 not yet known")
    (is (= 0 (ix/peer-count onsets {} "A" 120 12 36)))))

;; --- the principal contradiction -----------------------------------------------

(deftest softmax-runs-from-the-mean-to-the-argmax
  (is (close? 1.0 (ix/softmax-agg [0.0 1.0 2.0] 0.0) 1e-12) "tau 0: the mean")
  (is (= 2.0 (ix/softmax-agg [0.0 1.0 2.0] ##Inf)) "tau infinite: the largest")
  (is (close? 2.0 (ix/softmax-agg [0.0 1.0 2.0] 50.0) 1e-6))
  (is (< 1.0 (ix/softmax-agg [0.0 1.0 2.0] 1.0) 2.0))
  (is (= 3.0 (ix/softmax-agg [nil 3.0] 1.0)) "unknown gauges are left out")
  (is (nil? (ix/softmax-agg [nil nil] 1.0))))

(defn- gauge-rows
  "Rows whose onsets follow the largest of three gauges (argmax? true) or
  their mean."
  [seed argmax?]
  (let [g (m/rng seed)]
    (vec (for [t (range 1960 2000) i (range 150)
               :let [cs (vec (repeatedly 3 #(m/normal! g)))
                     a (if argmax? (apply max cs) (/ (reduce + cs) 3.0))
                     L (+ -3.0 (m/normal! g))]]
           {:kind "a" :c (str "C" i) :t t :g t :L L :gauges cs :n []
            :y (< (m/next-double! g) (expit (+ L (* 1.2 a))))}))))

(deftest the-temperature-is-learned
  (let [taus [0.0 0.5 1.0 2.0 4.0 8.0 ##Inf]
        arg (ix/tau-profile (gauge-rows 3 true) ["a"] {"a" -3.0} taus)
        mean (ix/tau-profile (gauge-rows 4 false) ["a"] {"a" -3.0} taus)]
    (is (>= (:tau arg) 2.0) "argmax data: a high temperature")
    (is (> (:lr-vs-mean arg) 6.0) "and the mean is rejected")
    (is (<= (:tau mean) 1.0) "mean data: a low one")
    (is (> (:lr-vs-max mean) 6.0) "and the argmax is rejected")))

;; --- the pair tests -------------------------------------------------------------

(deftest holm-adjusts-in-order
  (is (= [0.06 0.06 0.06] (mapv #(/ (Math/round (* 1e6 %)) 1e6) (ix/holm [0.02 0.03 0.04]))))
  (is (= [0.9 0.03] (ix/holm [0.9 0.015]))))

(deftest the-pair-design-holds-recalibration-and-main-effects
  (let [rows (ix/pair-design [{:kind "a" :L -2.0 :s 1.0 :tr 2.0} {:kind "b" :L -3.0 :s -1.0 :tr 0.5}] ["a" "b"] {"a" -2.5 "b" -2.5})]
    (is (= [1.0 0.0 0.5 0.0 1.0 2.0] (:x (first rows))) "1, b's dummy, L - m, slope by b, s, trigger")
    (is (= [2.0] (:z (first rows))) "s x trigger")
    (is (= [1.0 1.0 -0.5 -0.5 -1.0 0.5] (:x (second rows))))
    (is (= [-0.5] (:z (second rows))))))

(defn- pair-rows
  [seed gamma]
  (let [g (m/rng seed)]
    (vec (for [kind ["a" "b"] t (range 1960 2000) i (range 100)
               :let [s (m/normal! g) tr (m/normal! g) L (+ -2.5 (m/normal! g))]]
           {:kind kind :c (str "C" i) :t t :g t :L L :s s :tr tr
            :y (< (m/next-double! g) (expit (+ -0.5 (* 0.6 L) (* 0.2 s) (* 0.3 tr) (* gamma s tr))))}))))

(deftest a-synthetic-interaction-is-found
  (let [hit (ix/pair-test (pair-rows 5 0.4) ["a" "b"] {"a" -2.5 "b" -2.5})
        none (ix/pair-test (pair-rows 6 0.0) ["a" "b"] {"a" -2.5 "b" -2.5})]
    (is (close? 0.4 (:coef hit) 0.15) "gamma by maximum likelihood")
    (is (> (:z hit) 3.0) "the year-clustered score z")
    (is (< (Math/abs (double (:z none))) 3.0))))

(deftest standardizing-uses-the-design-years-only
  (let [panel {"A" {2000 1.0 2001 3.0 2010 1000.0} "B" {2000 -1.0 2001 -3.0 2010 -1000.0}}
        z (ix/standardize panel 2000 2002)]
    (is (close? (/ 1.0 (Math/sqrt 5.0)) (get-in z ["A" 2000]) 1e-12) "sd over 2000-2001 only: sqrt 5")
    (is (close? (/ 1000.0 (Math/sqrt 5.0)) (get-in z ["A" 2010]) 1e-9) "later years scaled, never scaling")))

(deftest softmax-design-nests-the-mean
  (let [rows [{:kind "a" :L -2.0 :gauges [1.0 3.0] :n [0.5]}]
        d0 (ix/gauge-design rows ["a"] {"a" -2.0} 0.0)
        di (ix/gauge-design rows ["a"] {"a" -2.0} ##Inf)]
    (is (= [1.0 0.0 0.5 2.0] (:x (first d0))) "1, L - m, nuisance, then the aggregate: the mean")
    (is (= 3.0 (peek (:x (first di)))) "the largest")))

(deftest linear-design-enters-the-gauges-one-by-one
  (let [rows (ix/linear-design [{:kind "a" :L -2.0 :gauges [nil 1.5] :n [0.5 0.0]}] ["a"] {"a" -2.5})]
    (is (= [1.0 0.5 0.5 0.0 0.0 1.5] (:x (first rows))) "1, L - m, the levels, each gauge (0 unknown)")))

(deftest gauge-rows-sign-scale-and-flag
  (let [fr {"a" [{:kind "a" :c "X" :t 2000 :L -2.0 :y true} {:kind "a" :c "Y" :t 2000 :L -2.0 :y false}]
            "b" [{:kind "b" :c "X" :t 2000 :L -2.0 :y false}]}
        sr {"s1" (fn [c _] (when (= c "X") 2.0)) "s2" (fn [_ _] 1.0)}
        tr {"g1" (fn [_ _] 3.0)}
        gp [{:name "p1" :state "s1" :trigger "g1" :sign -1 :kinds ["a" "b"]}
            {:name "p2" :state "s2" :trigger "g1" :sign 1 :kinds ["a"]}]
        rows (ix/gauge-rows fr ["a" "b"] gp sr tr {"p1" 2.0 "p2" 1.0})]
    (is (= 3 (count rows)))
    (is (= [-3.0 3.0] (:gauges (first rows))) "-1 x 2 x 3 / 2, and 1 x 1 x 3 / 1")
    (is (= [2.0 0.0 1.0 0.0 3.0 0.0] (:n (first rows))) "s1, s2, g1 each with an unknown flag")
    (is (= [nil 3.0] (:gauges (second rows))) "Y has no s1")
    (is (= [0.0 1.0 1.0 0.0 3.0 0.0] (:n (second rows))))
    (is (= [-3.0 nil] (:gauges (nth rows 2))) "p2 is not b's")))
