(ns bnm.recession-hold-test
  "--recession-hold true (bnm-5lr): banking's theory reads credit from
  before a recession. Credit that grows while output falls (2009, 2020's
  guaranteed loans) is distress or policy lending, not the expansion's
  boom that precedes crises, so at an origin whose row is a year of
  falling real GDP the theory reads the row of the origin before, up to
  `recession-hold-max` years back."
  (:require [bnm.lattice-banking :as lb]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is]]))

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
