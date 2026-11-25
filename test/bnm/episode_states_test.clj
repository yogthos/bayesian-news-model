(ns bnm.episode-states-test
  (:require [bnm.episode-states :as es]
            [clojure.test :refer [deftest is testing]]))

(deftest a-surge-lasts-while-the-rule-holds
  ;; 36 months at 0.5, then 2.0 for three months, then back to 0.5: the
  ;; rule (>= 1, >= 3x the mean of the 36 months before, at least 24 of
  ;; them) holds in all three, without the catalogue's cool-off
  (let [xs (merge (into {} (for [m (range 100 136)] [m 0.5]))
                  {136 2.0 137 2.0 138 2.0 139 0.5 140 0.5})]
    (is (= #{136 137 138} (es/surge-months xs))))
  (testing "under 24 months of past: no surge"
    (is (= #{} (es/surge-months (merge (into {} (for [m (range 100 120)] [m 0.5])) {120 5.0})))))
  (testing "a gap reaches back only 36 months"
    (is (= #{} (es/surge-months (merge (into {} (for [m (range 0 36)] [m 0.5])) {80 5.0}))))))

(deftest a-food-shock-lasts-while-the-rule-holds
  ;; 60 months at 3%, then 20%, 25%, then 8%
  (let [xs (merge (into {} (for [m (range 0 60)] [m 3.0])) {60 20.0 61 25.0 62 8.0})]
    (is (= #{60 61} (es/food-shock-months xs))))
  (testing "15% alone is not enough when the past was high"
    (is (= #{} (es/food-shock-months (merge (into {} (for [m (range 0 60)] [m 12.0])) {60 16.0}))))))

(deftest marks-carry-when-each-month-is-known
  (is (= [[23891 23898] [23903 23910]] (es/annual-marks [1990 1991] 18)))
  (is (= [[100 103] [101 104]] (es/monthly-marks [100 101] 3))))

(deftest the-clock-runs-from-the-latest-known-month-inside-an-episode
  (let [f (es/end-decay {"A" [[100 103] [101 104] [102 105]] "B" [[50 68]]} 48)]
    (testing "nothing known yet"
      (is (= 0.0 (f "A" 102)))
      (is (= 0.0 (f "C" 500))))
    (testing "while the episode is known to go on, d stays near 1"
      (is (== (Math/pow 0.5 (/ 3 48.0)) (f "A" 103)))
      (is (== (Math/pow 0.5 (/ 3 48.0)) (f "A" 104))))
    (testing "after it ends, d decays from its last month"
      (is (== (Math/pow 0.5 (/ 48 48.0)) (f "A" 150)))
      (is (== 0.5 (f "B" 98))))))

(deftest annual-states-read-by-kind
  (let [p (str (System/getProperty "java.io.tmpdir") "/episode-states-test.csv")]
    (spit p "kind,iso,year,source\nwar_onset,SYR,2012,UCDP 1\nwar_onset,SYR,2013,UCDP 1\nconflict_onset,SYR,2011,UCDP 2\n")
    (is (= {"war_onset" {"SYR" #{2012 2013}} "conflict_onset" {"SYR" #{2011}}} (es/read-annual p)))))
