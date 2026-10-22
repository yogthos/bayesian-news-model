(ns bnm.relation-diag-test
  (:require [bnm.relation-diag :as rd]
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
