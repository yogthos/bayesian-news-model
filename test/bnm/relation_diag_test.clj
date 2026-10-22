(ns bnm.relation-diag-test
  (:require [augur.math]
            [bnm.relation-diag :as rd]
            [clojure.test :refer [deftest is testing]]))

(deftest an-edge-s-form-from-the-design-years
  ;; a form qualifies when it gains past the child's forecast (LR >= 3.84)
  ;; in the edge's direction and at least twice its placebo's gain; the
  ;; post-crisis regime when it qualifies, else the qualifying form that
  ;; gains most; no form, no edge. Never the level or the surprise (which
  ;; the lattice cannot read)
  (let [r (fn [lr coef plr] {:lr lr :coef coef :placebo-lr plr})]
    (is (= :post3 (rd/choose-form {:post3 (r 9.0 0.1 1.0) :e1 (r 20.0 0.2 1.0)} 1)))
    (is (= :e1 (rd/choose-form {:post3 (r 9.0 0.1 5.0) :e1 (r 20.0 0.2 1.0) :e5 (r 10.0 0.2 1.0)} 1)))
    (is (= :e1 (rd/choose-form {:e1 (r 10.0 -0.2 1.0)} -1)) "an inhibiting edge, a negative coefficient")
    (is (nil? (rd/choose-form {:e1 (r 10.0 -0.2 1.0)} 1)) "the wrong direction")
    (is (nil? (rd/choose-form {:post3 (r 3.0 0.1 0.0)} 1)) "too little")
    (is (nil? (rd/choose-form {:level (r 90.0 0.5 0.0) :surprise (r 30.0 0.5 0.0)} 1)))
    (testing "own parts (bnm.factors) are forms too; the post-crisis regime is preferred in either"
      (is (= :post3-own (rd/choose-form {:post3 (r 4.0 0.1 3.0) :post3-own (r 9.0 0.1 1.0) :level-own (r 20.0 0.2 1.0)} 1)))
      (is (= :post3 (rd/choose-form {:post3 (r 12.0 0.1 1.0) :post3-own (r 9.0 0.1 1.0)} 1)) "the larger of the two")
      (is (= :level-own (rd/choose-form {:level-own (r 20.0 0.2 1.0) :destab-own (r 10.0 0.2 1.0)} 1))))))

(deftest the-threshold-is-corrected-for-the-candidates
  ;; the best of k candidates kept: chi-squared at 5%/k
  (is (< (Math/abs (- (rd/critical 1) 3.8415)) 1e-3))
  (is (< (Math/abs (- (rd/critical 10) 7.8794)) 1e-3))
  (is (rd/passes? {:lr 8.0 :placebo-lr 1.0} 10))
  (is (not (rd/passes? {:lr 7.0 :placebo-lr 1.0} 10)))
  (is (not (rd/passes? {:lr 9.0 :placebo-lr 5.0} 10)) "half its placebo's: not enough"))

(defn- panel
  "Rows for 40 years x 80 places, outcomes driven by a year effect the
  offset does not hold; x from (fn [g year place]) given the year effects."
  [seed xf]
  (let [g (augur.math/rng seed)
        u (vec (repeatedly 40 #(augur.math/normal! g)))
        walk (vec (reductions + (repeatedly 40 #(augur.math/normal! g))))]
    (vec (for [t (range 40) c (range 80)]
           {:y (> (+ -2.0 (u t) (* 1.7 (augur.math/normal! g))) 0.0)
            :o 0.0 :w 1.0 :g t :x [1.0 (double (xf g t c walk))]}))))

(deftest a-world-series-counts-its-years-not-its-rows
  ;; a series common to every place in a year, unrelated to the outcome,
  ;; meets the year effects by chance: rows as independent find it, years
  ;; as the units do not
  (let [runs (for [s (range 1 13)] (panel s (fn [_ t _ walk] (walk t))))
        naive (map #(:lr (rd/lr-test %)) runs)
        clustered (map #(:lr (rd/clustered-test %)) runs)
        mean #(/ (reduce + %) (count %))]
    (is (> (mean naive) 5.0) "pseudo-replication inflates the likelihood ratio (chi-squared(1) has mean 1)")
    (is (< (mean clustered) 3.0) "about chi-squared on one degree of freedom")))

(deftest a-place-signal-still-shows
  ;; an input that moves the outcome at the place passes when clustered by year
  (let [rows (mapv (fn [r] (let [x (nth (:x r) 1)] (assoc r :y (or (:y r) (> x 1.5)))))
                   (panel 7 (fn [g _ _ _] (augur.math/normal! g))))]
    (is (> (:lr (rd/clustered-test rows)) 20.0))))
