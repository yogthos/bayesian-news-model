(ns bnm.sheaf-test
  (:require [bnm.sheaf :as sh]
            [clojure.test :refer [deftest is testing]]))

(def spec
  {:scales [{:name :month :per 1} {:name :year :per 12}]
   :levels [[:country ["a1" "a2" "b1"]] [:region ["A" "B"]] [:world ["W"]]]
   :parent {"a1" "A" "a2" "A" "b1" "B" "A" "W" "B" "W"}})

(deftest the-base
  ;; cells are [scale place]; a cell lies under its parent place at its
  ;; scale and under the next coarser scale at its place
  (let [b (sh/base spec)]
    (is (= [:month] (sh/finer b :year)))
    (is (= [:year] (sh/coarser b :month)))
    (is (= [] (sh/coarser b :year)))
    (is (= ["a1" "a2"] (sh/children b 1 "A")))
    (is (nil? (sh/children b 0 "a1")))
    (is (= ["A" "W"] (sh/ancestors b "a1")))
    (is (= #{[:month "A"] [:year "a1"]} (set (sh/covers b [:month "a1"]))) "what lies just above a cell")
    (is (= 12 (count (sh/cells b))) "two scales by six places")))

(def book
  {[:month "a1"] (sorted-map 10 {:s 1.0} 11 {:s 2.0} 12 {:s 3.0})
   [:year "a1"] (sorted-map 11 {:s -1.0} 23 {:s -2.0})
   [:month "a2"] (sorted-map 12 {:s 5.0})})

(deftest restrictions-read-from-before-and-are-recorded
  (let [r (sh/reader book)]
    (testing "the latest section strictly before t"
      (is (= [11 {:s -1.0}] (sh/latest-before r :down [:year "a1"] 23)))
      (is (nil? (sh/latest-before r :down [:year "a1"] 11))))
    (testing "over a period, oldest first"
      (is (= [[11 {:s 2.0}] [12 {:s 3.0}]] (vec (sh/over r :up [:month "a1"] 11 12)))))
    (testing "at an origin"
      (is (= {:s 5.0} (sh/at r :up [:month "a2"] 12)))
      (is (nil? (sh/at r :up [:month "a2"] 11))))
    (is (= [{:dir :down :scale :year :place "a1" :origin 11}
            {:dir :up :scale :month :place "a1" :origin 11}
            {:dir :up :scale :month :place "a1" :origin 12}
            {:dir :up :scale :month :place "a2" :origin 12}]
           (sh/reads r))
        "every read, in order")))

(deftest the-coboundary
  ;; a section against what each neighbour says: the gaps the lattice calls
  ;; its contradictions; a neighbour without a section, no gap
  (is (= {:parent 1.5 :year -0.5} (sh/coboundary 2.0 {:parent 0.5 :year 2.5})))
  (is (= {:parent 0.0} (sh/coboundary 2.0 {:parent nil})))
  (testing "its size: the inconsistency of the section with its neighbours"
    (is (= 2.5 (sh/inconsistency {:a 1.5 :b -1.0}))
        "the sum of the gaps' sizes")))

(deftest errors-known-by-t
  ;; the errors a cell's forecasts made, by when each became known: the
  ;; latest known by t (inclusive), recorded as feedback
  (let [errors {[:year "a1"] (sorted-map 20 0.5 30 -0.25)}
        r (sh/reader book errors)]
    (is (= [20 0.5] (sh/latest-known r [:year "a1"] 29)))
    (is (= [30 -0.25] (sh/latest-known r [:year "a1"] 30)) "known at t: readable at t")
    (is (nil? (sh/latest-known r [:year "a1"] 19)))
    (is (= [{:dir :feedback :scale :year :place "a1" :known-at 20}
            {:dir :feedback :scale :year :place "a1" :known-at 30}] (sh/reads r)))))

(deftest edges-of-the-base-and-empty-reads
  (let [b (sh/base spec) r (sh/reader book)]
    (is (empty? (sh/children b 1 "nowhere")))
    (is (empty? (sh/over r :up [:month "a1"] 12 11)) "an empty period")
    (is (empty? (sh/reads r)))))

(deftest a-kind-s-reads-carry-the-kind
  ;; another kind's book read through the sheaf: its reads name the kind
  (let [r (sh/reader book {} {:kind "bank"})]
    (sh/latest-before r :relation [:year "a1"] 23)
    (is (= [{:dir :relation :kind "bank" :scale :year :place "a1" :origin 11}] (sh/reads r)))))

(deftest a-reader-refuses-the-future
  ;; given the node's origin, a read after it throws: the causality guard
  ;; in the one place every read passes
  (let [r (sh/reader book {[:year "a1"] (sorted-map 20 0.5 30 -0.25)} {:origin 11})]
    (is (some? (sh/at r :up [:month "a1"] 11)) "at the origin")
    (is (thrown? clojure.lang.ExceptionInfo (sh/over r :up [:month "a1"] 10 12)))
    (is (thrown? clojure.lang.ExceptionInfo (sh/latest-known (sh/reader {} {[:year "a1"] (sorted-map 20 0.5)} {:origin 11}) [:year "a1"] 25)))))

(deftest coboundary-through-edge-maps
  ;; a neighbour's value carried to the section's terms by its edge's map
  ;; (identity by default): a loading, a stock's integration
  (is (= {:parent 1.0} (sh/coboundary 2.0 {:parent 0.5} {:parent #(* 2.0 %)}))))

(deftest the-latest-n-before
  ;; the n latest sections strictly before t, newest first, each recorded
  (let [r (sh/reader book)]
    (is (= [[11 {:s 2.0}] [10 {:s 1.0}]] (vec (sh/latest-n-before r :relation [:month "a1"] 12 2))))
    (is (= [11 10] (map :origin (sh/reads r))))))
