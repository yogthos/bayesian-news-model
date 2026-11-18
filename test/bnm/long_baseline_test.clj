(ns bnm.long-baseline-test
  "--long-baseline N (bnm-3j8, docs/TESTBED.md \"The baseline absorbs the
  boom\"): banking's level stretches against N years (at least 10) in place
  of ten (at least six). Off, every stretch is what it was."
  (:require [bnm.invariants :as iv]
            [bnm.lattice-banking :as lb]
            [bnm.macrohistory :as mh]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(defn- wiggle [seed i] (Math/sin (+ (* 0.7 i) (* 1.3 seed))))

(defn- jst-rows [iso]
  (vec (for [y (range 1900 2021) :let [i (- y 1900)]]
         {:iso iso :year y :gdp 100.0 :pop 1.0 :cpi 100.0 :crisisJST 0.0
          :tloans (+ 60.0 (* 0.5 i) (* 5.0 (wiggle 1 i)) (* 8.0 (wiggle 4 (* 0.2 i)))) :tmort (+ 30.0 (* 0.4 i) (* 3.0 (wiggle 2 i)))
          :hpnom (+ 100.0 i (* 4.0 (wiggle 3 i))) :iy (+ 0.2 (* 0.02 (wiggle 4 i)))
          :debtgdp (+ 50.0 (* 5.0 (wiggle 5 i))) :stir (+ 4.0 (wiggle 6 i)) :unemp (+ 6.0 (wiggle 7 i))})))

(deftest the-option
  (is (nil? (wr/long-baseline-choice {})))
  (is (= 40 (wr/long-baseline-choice {:long-baseline "40"})))
  (is (thrown? Exception (wr/long-baseline-choice {:long-baseline "8"})) "a baseline shorter than the default is not one")
  (is (thrown? Exception (wr/long-baseline-choice {:long-baseline "x"}))))

(deftest macrohistory's-stretches-follow-the-window
  (let [rows (jst-rows "AAA")
        by (fn [built] (into {} (for [r built] [(:year r) r])))
        plain (by (mh/build rows))
        ten (by (mh/build rows nil nil nil nil {:stretch [10 6]}))
        long (by (mh/build rows nil nil nil nil {:stretch [40 10]}))
        credit (mapv #(/ (:tloans %) (:gdp %)) rows)]
    (testing "the default window is the ten years it always was"
      (is (= (map :specific (vals plain)) (map :specific (vals ten)))))
    (testing "a long window: the credit stretch against the 40 years before"
      (is (= (iv/stretch credit 80 40 10) (get-in long [1980 :specific :credit])))
      (is (not= (get-in plain [1980 :specific :credit]) (get-in long [1980 :specific :credit]))))
    (testing "only the level stretches move: the flows keep ten years"
      (is (= (map #(get-in % [:invariant :flow-norm]) (vals plain)) (map #(get-in % [:invariant :flow-norm]) (vals long)))))))

(deftest the-splice-follows-the-window
  (let [rows (jst-rows "AAA")
        bis {"AAA" {:credit (into {} (for [r rows] [(:year r) (* 1.7 (/ (:tloans r) (:gdp r)) 100.0)]))}}]
    (is (= (mh/dated-inputs rows bis 2002) (mh/dated-inputs rows bis 2002 {:stretch [10 6]})))
    (is (not= (get-in (mh/dated-inputs rows bis 2002) [["AAA" 2010] :specific :credit])
              (get-in (mh/dated-inputs rows bis 2002 {:stretch [40 10]}) [["AAA" 2010] :specific :credit])))))

(deftest banking's-own-stretches-follow-the-window
  (let [ys (into {} (for [y (range 1950 2021)] [y (+ 10.0 (* 0.1 (- y 1950)) (wiggle 2 y))]))]
    (is (= (#'lb/stretch-at ys 2010) (#'lb/stretch-at ys 2010 nil)))
    (is (= (#'lb/stretch-at ys 2010 [10 6]) (#'lb/stretch-at ys 2010)))
    (is (not= (#'lb/stretch-at ys 2010) (#'lb/stretch-at ys 2010 [40 10])))))
