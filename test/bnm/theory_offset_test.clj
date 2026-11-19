(ns bnm.theory-offset-test
  "--theory-offset true (bnm-ris): banking's year node reads a theory
  forecast as its offset, a logit on its own credit stretch and three-year
  credit growth refit at each origin on what is known (bnm.theory)."
  (:require [bnm.lattice-banking :as lb]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is]]))

(deftest the-option
  (is (false? (wr/theory-offset-choice {})))
  (is (true? (wr/theory-offset-choice {:theory-offset "true"})))
  (is (thrown? Exception (wr/theory-offset-choice {:theory-offset "yes"}))))

(deftest the-theory-reads-credit-stretch-and-growth
  (is (= [:credit :boom-credit3] (mapv first lb/theory-inputs)))
  (is (= [(first (:stretches lb/year-groups)) (first (:booms lb/year-groups))] (mapv second lb/theory-inputs))
      "the year row's credit stretch and three-year credit growth"))

;; --theory-world true (bnm-5lr): the theory also reads the world's bank
;; funding, the mean over countries of the loans-to-deposits stretch, so
;; the level falls in a calm the stocks show (docs/studies/calm_liquidity.py)
(deftest the-world-option
  (is (false? (wr/theory-world-choice {})))
  (is (true? (wr/theory-world-choice {:theory-world "true"}))))

(deftest the-world-input-is-loans-to-deposits
  (is (= [[:world-loans-to-deposits (first (:funding lb/year-groups))]] lb/theory-world-inputs))
  (is (= 41 (first (:funding lb/year-groups)))))

(deftest a-row-adds-the-world-mean-at-the-origin
  (let [z {["A" 11] [1.0 2.0 3.0] ["B" 11] [5.0 6.0 nil] ["C" 11] [0.0 0.0 1.0] ["A" 23] [9.0 9.0 9.0]}
        row (lb/theory-row (fn [u t] (get z [u t])) ["A" "B" "C"] [[:c 0] [:b 1]] [[:w 2]])]
    (is (= [1.0 2.0 2.0] (row "A" 11)) "own columns, then the mean of column 2 over the countries that have it")
    (is (= [5.0 6.0 2.0] (row "B" 11)) "a country without the column still reads the world mean")
    (is (= [9.0 9.0 9.0] (row "A" 23)))
    (is (nil? (row "D" 11)) "no row, no inputs")
    (is (= [1.0 2.0] ((lb/theory-row (fn [u t] (get z [u t])) ["A"] [[:c 0] [:b 1]] nil) "A" 11)) "no world inputs: as before")))
