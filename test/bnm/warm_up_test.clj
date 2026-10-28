(ns bnm.warm-up-test
  (:require [bnm.warm-up :as wu]
            [clojure.test :refer [deftest is testing]]))

(deftest the-last-origin-a-fit-may-read
  (is (= 2007 (wu/fit-cut 2010 3)) "window closed at the end of 2009, published within a year")
  (is (= 2006 (wu/fit-cut 2010 18)) "eighteen months to publish: two more years")
  (is (= 2008 (wu/fit-cut 2010 0))))

(deftest a-warm-up-origin-came-before-any-published-outcome
  (is (wu/warm-up? 2003 18 2003))
  (is (wu/warm-up? 2006 18 2003) "fit-cut 2002, before the first origin")
  (is (not (wu/warm-up? 2007 18 2003)) "fit-cut 2003: the first origin's outcome is published")
  (is (wu/warm-up? 1981 3 1979))
  (is (not (wu/warm-up? 1982 3 1979))))

(def node
  "A year node as bnm.relation-diag/read-year gives it: forecasts from 1990
  (month 12t + 11), outcomes resolved from 1995, a mid-year origin."
  {"AAA" (into (sorted-map) (concat (for [t (range 1990 2006)] [(+ 11 (* 12 t)) [0.5 (when (>= t 1995) false)]])
                                    [[(+ 5 (* 12 1994)) [0.5 true]]]))
   "BBB" (into (sorted-map) (for [t (range 1996 2006)] [(+ 11 (* 12 t)) [0.1 (= t 1999)]]))})

(deftest the-first-origin-is-the-first-resolved-year-end
  (is (= 1995 (wu/first-origin node)) "forecasts before 1995 have no outcome; the mid-year 1994 row is not a year end")
  (is (nil? (wu/first-origin {"AAA" (sorted-map 23 [0.5 nil])}))))

(deftest the-warm-up-origins-of-a-node
  (is (= (set (range 1990 1998)) (wu/warm-up-origins node 3))
      "fit-cut of 1997 is 1994, before 1995; of 1998, 1995; the forecasts before the first origin are prior too")
  (is (= (set (range 1990 1999)) (wu/warm-up-origins node 18)))
  (testing "dropped from the node, every place"
    (let [d (wu/drop-node node 3)]
      (is (= (range 1998 2006) (map #(quot % 12) (keys (d "AAA")))) "the mid-year row goes with its year")
      (is (= (range 1998 2006) (map #(quot % 12) (keys (d "BBB")))))
      (is (= [0.1 true] (get-in d ["BBB" (+ 11 (* 12 1999))])) "the rest as they were"))))

(deftest rows-are-marked-and-the-fits-leave-them-out
  (let [rows (vec (for [t (range 1979 1990) c ["A" "B"]] {:c c :t t :y false :p 0.5}))
        marked (wu/mark rows 3 1979)]
    (is (= #{1979 1980 1981} (set (map :t (filter :warm-up marked)))))
    (is (= (set (range 1982 1990)) (set (map :t (wu/fit-rows marked)))))
    (is (= (count rows) (count marked)) "marking keeps every row, for scoring")
    (testing "the first origin is the kind's, not the first row passed"
      (is (= #{1980 1981} (set (map :t (filter :warm-up (wu/mark (filterv #(>= (:t %) 1980) rows) 3 1979)))))))
    (testing "rows never marked are all fitted"
      (is (= rows (wu/fit-rows rows))))))
