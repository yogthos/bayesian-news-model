(ns bnm.pit-test
  (:require [bnm.eval :as ev]
            [clojure.test :refer [deftest is]]))

(deftest escalation-probability-is-a-smooth-tail
  ;; from a hundred draws a counted frequency is zero for most nodes, and the
  ;; ties throw away the ranking; the log-normal tail of the sample keeps it
  (let [h 13
        draws (fn [m] (double-array (map #(* m (Math/exp (* 0.2 (- (/ % 50.0) 1.0)))) (range 100))))
        lo (ev/escalation-p (draws 1.0) 1.0 h)
        hi (ev/escalation-p (draws 1.2) 1.0 h)]
    (is (< 0.0 lo hi 1.0) "no draw reaches the bar, yet closer is likelier")
    (is (> (ev/escalation-p (draws 3.0) 1.0 h) 0.9) "far above the bar: nearly certain")
    (is (= 0.0 (ev/escalation-p (double-array 100) 1.0 h)) "nothing at all: zero")))
