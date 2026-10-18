(ns bnm.onset-test
  (:require [bnm.onset :as o]
            [clojure.test :refer [deftest is testing]]))

(def onsets (sorted-set 100))

(deftest labels-count-onsets-in-the-window
  (is (o/label onsets 99 [1 4]))
  (is (o/label onsets 96 [1 4]))
  (is (not (o/label onsets 95 [1 4])))
  (is (not (o/label onsets 99 [2 4])) "the onset falls before the lead window"))

(deftest a-row-is-at-risk-only-before-any-onset-it-could-not-see-coming
  (testing "not while an onset is recent"
    (is (not (o/at-risk? onsets 100 [1 4])))
    (is (not (o/at-risk? onsets 103 [1 4])))
    (is (o/at-risk? onsets 104 [1 4])))
  (testing "not when the onset falls between the origin and the window"
    (is (not (o/at-risk? onsets 99 [2 4])) "onset next quarter, window from two")
    (is (o/at-risk? onsets 98 [2 4]))
    (is (not (o/at-risk? onsets 96 [5 8])))
    (is (not (o/at-risk? onsets 99 [5 8])))
    (is (o/at-risk? onsets 95 [5 8]))
    (is (o/at-risk? onsets 99 [1 4]) "w1 has no gap")))

(deftest hard-rows-have-no-onset-in-ten-years
  (is (not (o/hard-row? onsets 139)))
  (is (o/hard-row? onsets 141))
  (is (o/hard-row? onsets 99)))

(deftest the-last-quarter-counted-is-the-last-complete-one
  ;; months as 12y + m - 1
  (let [month (fn [y m] (+ (* 12 y) (dec m)))]
    (is (= (+ (* 4 2026) 2) (o/last-complete-quarter (month 2026 9))) "September closes Q3")
    (is (= (+ (* 4 2026) 2) (o/last-complete-quarter (month 2026 10))) "October leaves Q4 open")
    (is (= (+ (* 4 2025) 3) (o/last-complete-quarter (month 2026 1))))))

(deftest groups-with-gaps-get-a-presence-column
  (let [rows [{:unit "A" :q 1} {:unit "B" :q 1}]
        [w xs] (o/with-presence 2 {["A" 1] (double-array [3.0 4.0])} rows)]
    (is (= 3 w))
    (is (= [3.0 4.0 1.0] (vec (xs ["A" 1]))))
    (is (nil? (xs ["B" 1])) "missing rows are filled with zeros downstream, presence 0"))
  (testing "a group covering every row is left alone"
    (let [xs {["A" 1] (double-array [3.0])}
          [w xs'] (o/with-presence 1 xs [{:unit "A" :q 1}])]
      (is (= 1 w))
      (is (identical? xs xs')))))

(deftest priors-are-chosen-each-year-on-what-was-known
  ;; run a is right in 2000, run b in 2001 and 2002; the window closes 4 quarters on
  (let [mk (fn [ps] (vec (for [[yr p y] ps] [(* 4 yr) p y "A"])))
        a {:prior 1.0 :preds (mk [[2000 0.9 true] [2001 0.1 true] [2002 0.1 true] [2003 0.5 true]])}
        b {:prior 2.0 :preds (mk [[2000 0.1 true] [2001 0.95 true] [2002 0.9 true] [2003 0.6 true]])}
        score (fn [ps] (when (seq ps) (/ (reduce + (map (fn [[_ p y]] (Math/log (if y p (- 1 p)))) ps)) (count ps))))
        {:keys [prior preds]} (o/choose-by-year [a b] score [1 4] a)]
    (is (= [0.9 0.1 0.9 0.6] (mapv second preds))
        "2000 takes the default; 2001 knows 2000, where a wins; 2002 and 2003 know 2001 too, where b wins more")
    (is (= 2.0 prior) "the choice of the last year")))
