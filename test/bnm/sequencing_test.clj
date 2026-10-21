(ns bnm.sequencing-test
  (:require [augur.math :as m]
            [bnm.sequencing :as sq]
            [clojure.test :refer [deftest is testing]]))

(deftest the-excursion-kernel
  ;; onsets of the years before y, decaying with tau, less what the place's
  ;; own rate so far would give: an excursion above its own level, from the
  ;; past only
  (let [k (sq/kernel #{1990} 1.0 (range 1985 1996))]
    (is (= 0.0 (get k 1990)) "nothing before it yet")
    (is (pos? (get k 1991)) "an onset counts from the year after, above the level its rate implies")
    (is (< (get k 1995) (get k 1991)) "and fades")
    (is (= (get k 1988) 0.0) "a calm past is at its level"))
  (let [k (sq/kernel #{1990} 1.0 (range 1985 1996))
        k' (sq/kernel #{1990 1999} 1.0 (range 1985 2000))]
    (is (= (get k 1993) (get k' 1993)) "what comes later does not change it")))

(deftest a-cause-shows-before-and-in-the-lagged-test
  ;; j raises k's hazard in the two years after it; nothing else links them
  (let [g (m/rng 1)
        units (map #(str "U" %) (range 60))
        years (range 1900 2000)
        j (into {} (for [u units] [u (set (filter (fn [_] (< (m/next-double! g) 0.05)) years))]))
        k (into {} (for [u units]
                     [u (set (filter (fn [y] (< (m/next-double! g) (if (some #(<= 1 (- y %) 2) (j u)) 0.25 0.03))) years))]))
        cov (into {} (for [u units] [u [1900 1999]]))]
    (let [table (sq/lead-lag {:onsets j :cover cov} {:onsets k :cover cov} (range -5 4))]
      (is (> (get table -1) 2.0) "j stands well above its calm rate the year before k")
      (is (< (get table 2) 1.6) "and not two years after"))
    (let [{:keys [lr coef]} (sq/lagged-test {:onsets j :cover cov} {:onsets k :cover cov})
          rev (sq/lagged-test {:onsets k :cover cov} {:onsets j :cover cov})]
      (is (> lr 20.0) "j's past predicts k given k's own")
      (is (pos? (first coef)))
      (is (< (:lr rev) 6.0) "k's past does not predict j")
      (let [ind (into {} (for [u units] [u (set (filter (fn [_] (< (m/next-double! g) 0.05)) years))]))]
        (is (< (:lr (sq/lagged-test {:onsets ind :cover cov} {:onsets k :cover cov})) 6.0) "nor does an unrelated kind's")))))
