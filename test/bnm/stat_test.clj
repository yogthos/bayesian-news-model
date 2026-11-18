(ns bnm.stat-test
  "Pins bnm.stat to the outputs of the copies it replaced, edge cases
  included (which of them throw, and the type of what comes back)."
  (:require [clojure.test :refer [deftest is testing]]
            [bnm.stat :as s]))

(defn- throws? [f] (try (f) false (catch Throwable _ true)))
(defn- nan? [x] (and (number? x) (Double/isNaN (double x))))

(def ^:private xs [0.31 -1.2 4.4 2.2 9.1 0.0 3.3 3.3])

(deftest mean-test
  (is (= 2.67625 (s/mean xs)))
  (is (= 2.0 (s/mean [1 2 3])) "a double from integers")
  (is (nan? (s/mean [])) "NaN when empty"))

(deftest sd-test
  (testing "sample (n - 1)"
    (is (= 3.2299400149228776 (s/sd xs)))
    (is (= 1.0 (s/sd [1 2 3])))
    (is (= 0.0 (s/sd [])) "0/-1 on the empty sum")
    (is (throws? #(s/sd [3.0])) "one value divides by zero"))
  (testing "population (n)"
    (is (= 3.02133222891823 (s/sd-pop xs)))
    (is (= 0.0 (s/sd-pop [2.0 2.0])))
    (is (throws? #(s/sd-pop [])) "empty divides by zero")))

(deftest median-test
  (is (= 2.75 (s/median xs)))
  (is (= 3 (s/median [5 1 3])) "odd: the middle value itself")
  (is (= 1.5 (s/median [1 2])) "even: a double")
  (is (= 3.5 (s/median [3.5])))
  (is (nil? (s/median [])))
  (testing "as a double"
    (is (= 3.0 (s/median-double [5 1 3])))
    (is (= 2.5 (s/median-double [4 1 3 2])))
    (is (= 0.5 (s/median-double [1/3 2/3])))
    (is (nil? (s/median-double [])))))

(deftest pearson-test
  (let [ps [[0.1 0.2] [0.4 0.1] [0.3 0.3] [0.9 0.7] [0.5 0.5] [0.2 0.8] [0.7 0.6]]]
    (is (= 0.4322839481882354 (s/pearson (map first ps) (map second ps))))
    (is (= 0.4322839481882354 (s/pearson-or-nil (map first ps) (map second ps)))))
  (is (= 0.9933992677987828 (s/pearson [1 2 3] [2 4 7])))
  (is (throws? #(s/pearson [1 1 1] [1 2 3])) "unguarded: a flat integer series divides by zero")
  (is (nil? (s/pearson-or-nil [1 1 1] [1 2 3])) "nil when flat")
  (is (nil? (s/pearson-or-nil [1 2] [2 4])) "nil with two points")
  (is (nil? (s/pearson-or-nil [] []))))

(deftest brier-one-test
  (is (= 0.48999999999999994 (s/brier-one 0.3 true)))
  (is (= 0.0 (s/brier-one 1 true)))
  (is (= 1.0 (s/brier-one 1.0 nil)))
  (is (nan? (s/brier-one ##NaN false))))

(deftest log-score-test
  (is (= -1.5350567286626973 (s/mean-log-score [[0 0.2 true] [1 0.9 false] [2 0.5 true]])))
  (is (= -6.907755778982387 (s/mean-log-score [[0 0 true] [1 1 true]])) "clamped at 1e-6")
  (is (nil? (s/mean-log-score []))))

(deftest rows-scores-test
  (let [rows [{:y true} {:y false} {:y true}] qs [0.2 0.9 0.5]]
    (is (= -1.5350567286626973 (s/rows-mean-log rows qs)))
    (is (= 0.5666666666666668 (s/rows-brier rows qs))))
  (is (= 0 (s/rows-brier [] [])))
  (is (= 0 (s/rows-mean-log [] []))))
