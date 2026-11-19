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
  (is (= :funding (wr/theory-world-choice {:theory-world "funding"})) "t100: the panel's bank funding only")
  (is (= :all (wr/theory-world-choice {:theory-world "true"})) "funding and the world signals (reserves)")
  (is (thrown? Exception (wr/theory-world-choice {:theory-world "yes"}))))

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

;; --theory-world (bnm-5lr) outside the Macrohistory panel: the world mean
;; of the countries' reserves, as known, against its own past
;; (docs/studies/calm_em.py)
(deftest the-world-signals-include-reserves
  (is (= [[:world-reserves {:var "debt/reserves_months" :read [:value] :stale 30}]] lb/theory-world-signals)))

(deftest a-world-level-is-the-mean-against-its-own-past
  (let [origins (vec (range 11 (* 12 20) 12))
        v (fn [u t] (case u "A" (double t) "B" (when (> t 100) (+ 2.0 t)) nil))
        f (lb/world-level v ["A" "B" "C"] origins 5)
        means (fn [t] (if (> t 100) (+ 1.0 t) (double t)))]
    (is (nil? (f (origins 4))) "fewer than 5 earlier origins: none")
    (let [t (origins 12)
          past (map means (take 12 origins))
          m (/ (reduce + past) (count past))
          sd (Math/sqrt (/ (reduce + (map #(Math/pow (- % m) 2) past)) (count past)))]
      (is (< (Math/abs (- (f t) (/ (- (means t) m) sd))) 1e-9) "the mean over countries with a value, against the earlier origins' means"))
    (is (nil? ((lb/world-level (constantly nil) ["A"] origins 5) (origins 12))) "no values: none")))

(deftest a-row-adds-extra-world-levels
  (let [z {["A" 11] [1.0 2.0]}
        row (lb/theory-row (fn [u t] (get z [u t])) ["A"] [[:c 0]] nil [(fn [t] (* 2.0 t)) (constantly nil)])]
    (is (= [1.0 22.0 nil] (row "A" 11)))))
