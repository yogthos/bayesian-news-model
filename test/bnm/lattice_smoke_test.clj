(ns bnm.lattice-smoke-test
  (:require [bnm.lattice-smoke :as smoke]
            [clojure.test :refer [deftest is testing]]))

(defn- rows [vals-by-origin]
  (for [[o vs] vals-by-origin v vs] {:origin o :x [1.0 v]}))

(deftest a-group-is-live-dead-or-dropped-out
  (testing "live: nonzero and spread"
    (let [s (smoke/group-live (rows {10 [0.5 -0.5] 20 [1.0 0.0]}) [1] {10 0.2 20 0.3})]
      (is (= 0.75 (:live s)))
      (is (not (:dropped? s)))))
  (testing "no input anywhere in the window and no weight: dropped out, not dead"
    (let [s (smoke/group-live (rows {10 [0.0 0.0] 20 [0.0 0.0]}) [1] {10 0.0 20 0.0})]
      (is (:dropped? s))
      (is (empty? (:weighted s)))))
  (testing "no input but still weighted: the origins it kept weight at"
    (let [s (smoke/group-live (rows {10 [0.0 0.0] 20 [0.0 0.0]}) [1] {10 0.0 20 0.1})]
      (is (:dropped? s))
      (is (= [20] (:weighted s)))))
  (testing "an origin with no input anywhere must carry no weight even when others are live"
    (let [s (smoke/group-live (rows {10 [0.0 0.0] 20 [0.4 0.0]}) [1] {10 0.05 20 0.1})]
      (is (not (:dropped? s)))
      (is (= [10] (:weighted s))))))
