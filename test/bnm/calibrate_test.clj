(ns bnm.calibrate-test
  (:require [bnm.calibrate :as cal]
            [clojure.test :refer [deftest is testing]]))

(deftest overconfidence-is-learned-away
  ;; forecasts of 0.9 that come true half the time
  (let [outcomes (for [i (range 200)] {:channel :violence :p 0.9 :outcome (even? i)})
        c (cal/learn {} outcomes)]
    (is (< 0.45 (cal/calibrate c :violence 0.9) 0.55))
    (is (= 0.3 (cal/calibrate c :protest 0.3)) "an unseen channel passes through")
    (is (= 0.03 (cal/calibrate c :violence 0.03)) "an empty bin keeps the raw value")))

(deftest spread-is-learned-from-errors
  ;; forecasts whose 80% range is half as wide as their errors need
  (let [sd 0.2
        z1 1.2815515655446004
        rows (fn [f]
               (for [i (range 400)]
                 (let [e (* (if (even? i) 1.0 -1.0) 2.0 sd)]
                   {:level :country :channel :violence :stratum :active
                    :q10 (Math/expm1 (- 3.0 (* f z1 sd))) :q50 (Math/expm1 3.0)
                    :q90 (Math/expm1 (+ 3.0 (* f z1 sd))) :actual (Math/expm1 (+ 3.0 e))})))
        s1 (cal/learn-spread {} (rows 1.0))
        ;; the next forecasts come out widened by what was learned
        s (cal/learn-spread s1 (rows (cal/spread-factor s1 [:country :violence :active])))
        k (cal/spread-factor s [:country :violence :active])]
    (is (< 1.8 k 2.2) k)
    (is (= 1.0 (cal/spread-factor s [:actor :violence :active])) "an unseen key keeps the spread")
    (testing "widening keeps the median and scales the log spread"
      (let [xs (double-array [0.0 (Math/expm1 2.9) (Math/expm1 3.0) (Math/expm1 3.1) (Math/expm1 3.2)])
            w (cal/widen 2.0 xs)]
        (is (< (Math/abs (- (aget w 2) (aget xs 2))) 1e-9))
        (is (< (Math/abs (- (Math/log1p (aget w 3)) 3.2)) 1e-9))
        (is (< (Math/abs (- (Math/log1p (aget w 1)) 2.8)) 1e-9))
        (is (= 0.0 (aget w 0)) "never below zero")))))
