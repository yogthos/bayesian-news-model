(ns bnm.normal-test
  (:require [bnm.math :as m]
            [bnm.normal :as nr]
            [bnm.fstats :as fs]
            [clojure.test :refer [deftest is testing]]))

(defn- synthetic
  "Weekly-like periods over 60 years: returns partly predicted by the first
  input, variance by the second, and a hazard that is high in a few years."
  [n]
  (let [g (m/rng 21)]
    (vec (for [i (range n)
               :let [yr (+ 1930 (quot i 52))
                     x1 (m/normal! g) x2 (Math/abs (m/normal! g))
                     sd (* 0.01 (+ 0.5 x2))]]
           {:year yr :x [x1 x2] :p (if (#{1960 1975 1999} yr) 0.6 (* 0.1 x2))
            :ret (+ 0.001 (* sd (m/normal! g))) :rf 0.0001 :rv (* sd sd (+ 0.5 (Math/abs (m/normal! g))))}))))

(def opts {:signs [1 nil] :har-lags [1 4 13] :tune [1950 1989] :from 1950 :periods 52})

(deftest forecasts-never-look-ahead
  (let [ps (synthetic 3120)
        full (nr/forecasts ps opts)
        cut 2000
        part (nr/forecasts (subvec ps 0 (inc cut)) opts)]
    (testing "every forecast made by period `cut` is the same without the periods after it"
      (doseq [k [:hm :combo :combo-raw :ridge :ewma :garch :har :model-var :nu]]
        (is (= (mapv k (subvec full 0 cut)) (mapv k (subvec part 0 cut))) (str k))))))

(deftest the-regime-is-cut-on-the-tuning-years
  (let [ps (synthetic 3120)
        cut (nr/regime-cut ps [1950 1989])
        tuned (sort (keep #(when (<= 1950 (:year %) 1989) (:p %)) ps))]
    (is (= cut (nth tuned (long (* 0.8 (count tuned))))))
    (testing "the years after the tuning ones do not move it"
      (is (= cut (nr/regime-cut (mapv #(if (> (:year %) 1989) (assoc % :p 0.99) %) ps) [1950 1989]))))))

(deftest one-input-forecasts-respect-their-signs
  (testing "a slope against the input's sign falls back to the historical mean"
    (is (= 0.002 (nr/one-input-forecast {:a 0.01 :b -0.5} 1 2.0 0.002)))
    (is (= (+ 0.01 (* 0.5 2.0)) (nr/one-input-forecast {:a 0.01 :b 0.5} 1 2.0 0.002)))
    (is (= (+ 0.01 (* -0.5 2.0)) (nr/one-input-forecast {:a 0.01 :b -0.5} nil 2.0 0.002))))
  (testing "a missing input gives the historical mean"
    (is (= 0.002 (nr/one-input-forecast {:a 0.01 :b 0.5} 1 nil 0.002)))))

(deftest exposures
  (is (= 1.5 (nr/mean-variance 0.05 0.001)))
  (is (= 0.0 (nr/mean-variance -0.01 0.001)))
  (is (< (Math/abs (- (/ 0.01 (* 3 0.004)) (nr/mean-variance 0.01 0.004))) 1e-12))
  (is (= 1.5 (nr/vol-target 0.02 0.0001)))
  (is (< (Math/abs (- 0.5 (nr/vol-target 0.02 0.0016))) 1e-12)))

(deftest the-model-finds-what-is-there
  ;; returns follow the first input of the period before: on held-out years
  ;; the combination beats the historical mean
  (let [base (synthetic 3120)
        g (m/rng 4)
        ;; the forecast at i is for period i + 1, whose driver is known at i
        ps (mapv (fn [i p]
                   (let [x1 (if (pos? i) (first (:x (base (dec i)))) 0.0)]
                     (assoc p :ret (+ 0.001 (* 0.004 x1) (* (Math/sqrt (:rv p)) (m/normal! g))))))
                 (range) base)
        fc (nr/forecasts ps opts)
        held (filter #(>= (:year (ps %)) 1980) (range (dec (count ps))))
        y (map #(:ret (ps (inc %))) held)]
    (is (pos? (fs/oos-r2 y (map #(:combo-raw (fc %)) held) (map #(:hm (fc %)) held))))))
