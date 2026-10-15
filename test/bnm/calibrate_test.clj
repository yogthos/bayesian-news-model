(ns bnm.calibrate-test
  (:require [bnm.calibrate :as cal]
            [clojure.test :refer [deftest is]]))

(deftest overconfidence-is-learned-away
  ;; forecasts of 0.9 that come true half the time
  (let [outcomes (for [i (range 200)] {:channel :violence :p 0.9 :outcome (even? i)})
        c (cal/learn {} outcomes)]
    (is (< 0.45 (cal/calibrate c :violence 0.9) 0.55))
    (is (= 0.3 (cal/calibrate c :protest 0.3)) "an unseen channel passes through")
    (is (= 0.03 (cal/calibrate c :violence 0.03)) "an empty bin keeps the raw value")))
