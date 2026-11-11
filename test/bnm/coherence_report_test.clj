(ns bnm.coherence-report-test
  (:require [bnm.coherence-report :as cr]
            [bnm.compose :as c]
            [clojure.test :refer [deftest is testing]]))

;; a book coherent by construction: constant rates per country, parents
;; at the composition of their children, quarters at the year's rate
(def ^:private rates {"KEN" 0.002 "TZA" 0.001 "UGA" 0.003})

(defn- book [bump]
  (let [ts (range (+ (* 12 2005) 11) (* 12 2010) 12)
        sub (reduce + (vals rates))
        cell (fn [scale place lam] [[scale place] (into (sorted-map) (for [t (concat ts (map #(- % 3) ts))]
                                                                         [t (c/rate->p lam (:w (cr/windows scale)))]))])]
    (into {} (concat (for [[pl lam] rates scale ["year" "quarter-lead" "quarter-w2"]] (cell scale pl lam))
                     (for [scale ["year" "quarter-lead" "quarter-w2"]] (cell scale "s/eastern-africa" (* bump sub)))))))

(deftest a-coherent-book-has-no-gaps
  (let [b (book 1.0)
        pg (cr/place-gaps b)
        tg (cr/time-gaps b)]
    (is (seq pg))
    (is (every? #(< (Math/abs (:gap %)) 1e-12) pg))
    (is (every? #(< (Math/abs (:log-ratio %)) 1e-9) pg))
    (is (= 20 (count tg)) "four places, five years")
    (is (every? #(< (Math/abs (:gap %)) 1e-12) tg)))
  (testing "a parent saying twice its children's rate"
    (let [pg (cr/place-gaps (book 2.0))]
      (is (every? #(< (Math/abs (- (:log-ratio %) (Math/log 2.0))) 1e-9) pg))
      (is (every? #(pos? (:gap %)) pg))))
  (testing "the tree from the places present"
    (is (= {"s/eastern-africa" ["KEN" "TZA" "UGA"]} (cr/tree ["KEN" "TZA" "UGA" "s/eastern-africa"])))))
