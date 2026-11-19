(ns bnm.recession-hold-test
  "--recession-hold true (bnm-5lr): banking's theory reads credit from
  before a recession. Credit that grows while output falls (2009, 2020's
  guaranteed loans) is distress or policy lending, not the expansion's
  boom that precedes crises, so at an origin whose row is a year of
  falling real GDP the theory reads the row of the origin before, up to
  `recession-hold-max` years back."
  (:require [bnm.lattice-banking :as lb]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(deftest the-option
  (is (false? (wr/recession-hold-choice {})))
  (is (true? (wr/recession-hold-choice {:recession-hold "true"}))))

(deftest a-recession-row-reads-the-origin-before
  (let [rec #{["A" 35] ["B" 35] ["B" 23] ["C" 35] ["C" 23] ["C" 11]}
        h (fn [u t] (lb/held-origin (fn [u t] (contains? rec [u t])) u t))]
    (is (= 23 (h "A" 35)))
    (is (= 47 (h "A" 47)) "no recession: its own")
    (is (= 11 (h "B" 35)) "two recession years: two back")
    (is (= 2 lb/recession-hold-max))
    (is (= 11 (h "C" 35)) "never more than recession-hold-max back")))

(deftest a-recession-is-falling-real-gdp-in-the-row-year
  (let [r (lb/recession-of {"A" {2020 -7.4 2019 2.0}})]
    (is (true? (r "A" 2020)))
    (is (false? (r "A" 2019)))
    (is (false? (r "A" 2018)) "unknown: not a recession")
    (is (false? (r "B" 2020)))))

;; --recession-hold rows: the year row's ratio columns (stocks over GDP:
;; the stretches, booms, household and debt groups, wide credit) held at
;; the pre-recession origin for every group, not only the theory
(deftest the-rows-option
  (is (= :rows (wr/recession-hold-choice {:recession-hold "rows"})))
  (is (true? (wr/recession-hold-choice {:recession-hold "true"})) "t103: the theory only")
  (is (thrown? Exception (wr/recession-hold-choice {:recession-hold "yes"}))))

(deftest the-held-columns-are-the-ratios
  (is (= (vec (sort (distinct (concat (:stretches lb/year-groups) (:booms lb/year-groups) (:household lb/year-groups)
                                      (:debt lb/year-groups) (:credit-wide lb/year-groups)))))
         lb/recession-held-columns)))

(deftest a-recession-row-holds-its-ratio-columns
  (let [tb {["A" 11] [1.0 10.0] ["A" 23] [2.0 20.0] ["A" 35] [3.0 30.0] ["B" 23] [5.0 50.0]}
        held (lb/hold-columns tb (fn [u t] (= [u t] ["A" 35])) [0])]
    (is (= [2.0 30.0] (held ["A" 35])) "column 0 from the origin before, column 1 its own")
    (is (= [2.0 20.0] (held ["A" 23])))
    (is (= [5.0 50.0] (held ["B" 23])))
    (testing "the origin before missing: its own row"
      (is (= [5.0 50.0] ((lb/hold-columns tb (fn [u t] (= [u t] ["B" 23])) [0]) ["B" 23]))))))

(deftest under-rows-a-recession-covers-the-year-before-too
  (let [r (lb/recession-of {"A" {2020 -7.4}})
        ry (fn [t] (quot t 12))]
    (is (true? ((lb/recession-at r ry :rows) "A" (+ 11 (* 12 2021)))) "the debt columns read 2020 from the 2021 row")
    (is (false? ((lb/recession-at r ry true) "A" (+ 11 (* 12 2021)))) "the theory's rule: the row year alone")
    (is (true? ((lb/recession-at r ry true) "A" (+ 11 (* 12 2020)))))
    (is (false? ((lb/recession-at r ry :rows) "A" (+ 11 (* 12 2022)))))))

;; --recession-hold debt: only the IMF debt group's ratios held (the Dec
;; 2022 spike's group), the credit stretches and the theory's row as read
(deftest the-debt-option
  (is (= :debt (wr/recession-hold-choice {:recession-hold "debt"})))
  (is (= (:debt lb/year-groups) (lb/held-columns-for :debt)))
  (is (= lb/recession-held-columns (lb/held-columns-for :rows)))
  (is (nil? (lb/held-columns-for true)) "t103: the theory's row only")
  (is (false? (lb/theory-held? :debt)))
  (is (true? (lb/theory-held? :rows)))
  (is (true? (lb/theory-held? true)))
  (let [r (lb/recession-of {"A" {2020 -7.4}})]
    (is (true? ((lb/recession-at r #(quot % 12) :debt) "A" (+ 11 (* 12 2021)))) "the debt columns read the year before")))
