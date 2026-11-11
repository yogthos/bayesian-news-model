(ns bnm.sync-test
  (:require [augur.math :as m]
            [bnm.sync :as sync]
            [clojure.test :refer [deftest is testing]]))

(deftest correlation
  (is (< (Math/abs (- 1.0 (sync/corr [1.0 2.0 3.0] [2.0 4.0 6.5]))) 0.01))
  (is (< (Math/abs (+ 1.0 (sync/corr [1.0 2.0 3.0] [3.0 2.0 1.0]))) 1e-12))
  (is (zero? (sync/corr [1.0 1.0 1.0] [1.0 2.0 3.0])) "a flat series correlates with nothing"))

(deftest one-factor-share
  (let [g (m/rng 3)
        common (vec (repeatedly 12 #(m/normal! g)))]
    (is (> (sync/first-share (vec (repeat 30 common))) 0.99) "everyone moves together")
    (is (< (sync/first-share (vec (repeatedly 30 (fn [] (vec (repeatedly 12 #(m/normal! g))))))) 0.4)
        "independent series spread over many factors")
    (testing "the first factor holds at least an even share of the twelve"
      (is (>= (sync/first-share (vec (repeatedly 30 (fn [] (vec (repeatedly 12 #(m/normal! g))))))) (/ 1.0 12))))))

(deftest network-biomarker
  ;; members moving together, widely, and apart from the rest score above
  ;; a group of members as independent as the outsiders
  (let [g (m/rng 5)
        noise #(vec (repeatedly 12 (fn [] (m/normal! g))))
        common (noise)
        together (vec (for [_ (range 6)] (mapv #(+ (* 3.0 %1) %2) common (noise))))
        apart (vec (repeatedly 6 noise))
        outside (vec (repeatedly 20 noise))]
    (is (> (sync/biomarker together outside) (* 3 (sync/biomarker apart outside))))
    (is (nil? (sync/biomarker [(noise)] outside)) "one member has no correlations within")))

(deftest departure-from-own-baseline
  (let [g (m/rng 7)
        calm (fn [n s] (vec (repeatedly n (fn [] [(* s (m/normal! g)) (* s (m/normal! g))]))))
        steady (into (calm 40 1.0) (calm 12 1.0))
        wild (into (calm 40 1.0) (calm 12 4.0))
        [_ sd-steady] (sync/departure steady 12)
        [_ sd-wild] (sync/departure wild 12)]
    (is (< (Math/abs sd-steady) 0.35))
    (is (> sd-wild 1.0) "log of the spread against its own baseline")
    (is (nil? (sync/departure (calm 20 1.0) 12)) "too short for a baseline")))
