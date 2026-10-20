(ns bnm.eval-test
  (:require [bnm.eval :as ev]
            [clojure.test :refer [deftest is]]))

(deftest surges-from-a-quiet-base
  ;; a quarter at least 3x its node's trailing-year mean, from a node that
  ;; did not escalate in the four quarters before; nil until a year is known
  (let [rows (fn [actuals escalated]
               (map-indexed (fn [i [a e]] {:id "X" :channel :protest :period (format "2000%02d01" (inc i)) :actual a :escalated e})
                            (map vector actuals escalated)))
        surge #(mapv :surge (ev/with-surges %))]
    (is (= [nil nil nil nil true] (surge (rows [1.0 1.0 1.0 1.0 3.5] (repeat false)))))
    (is (= [nil nil nil nil false] (surge (rows [1.0 1.0 1.0 1.0 2.5] (repeat false)))) "under 3x")
    (is (= [nil nil nil nil false] (surge (rows [1.0 1.0 1.0 1.0 3.5] [false false true false true])))
        "not from a quiet base")
    (is (= [nil nil nil nil false] (surge (rows [0.0 0.0 0.0 0.0 1.0] (repeat false)))) "nothing to multiply")
    (is (= [nil nil nil nil true nil nil nil nil true]
           (mapv :surge (ev/with-surges (concat (rows [1.0 1.0 1.0 1.0 3.5] (repeat false))
                                                (map #(assoc % :id "Y") (rows [2.0 2.0 2.0 2.0 7.0] (repeat false)))))))
        "each node and channel its own series")))
