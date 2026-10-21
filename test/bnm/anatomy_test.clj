(ns bnm.anatomy-test
  (:require [bnm.anatomy :as an]
            [clojure.test :refer [deftest is]]))

(deftest superposed-epochs
  ;; a series that climbs over the three years before each crisis
  (let [series {"A" (into {} (for [y (range 1900 1950)] [y (cond (<= 1917 y 1919) (- y 1916.0) :else 0.0)]))
                "B" (into {} (for [y (range 1900 1950)] [y (cond (<= 1937 y 1939) (- y 1936.0) :else 0.0)]))}
        crises {"A" #{1920} "B" #{1940}}
        r (an/epoch series crises {:lags (range -5 3) :calm 5})]
    (is (= 3.0 (get-in r [-1 :mean])) "the year before: the top of the climb")
    (is (= 1.0 (get-in r [-3 :mean])))
    (is (= 0.0 (get-in r [-5 :mean])))
    (is (= 1.0 (get-in r [-1 :share-up])) "every crisis")
    (is (= -3 (an/turn r 0.5)) "it first stands out three years before")))
