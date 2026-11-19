(ns bnm.impute-test
  "bnm.impute (bnm-s6m): a missing input is the country's last value read
  at an earlier origin, decayed toward its peers' mean at the origin (half
  of the way every 36 months), its peers' mean when it never had one, nil
  when no peer has one either; and a column for the share of inputs
  missing, read before any is filled."
  (:require [bnm.impute :as im]
            [clojure.test :refer [deftest is testing]]))

(def parent {"a" "R" "b" "R" "c" "R" "d" "S"})
(def os [11 23 35 47])
(def raw {["a" 11] [1.0 nil] ["a" 23] [nil nil] ["a" 35] [nil 5.0] ["a" 47] [nil nil]
          ["b" 11] [3.0 nil] ["b" 23] [4.0 nil] ["b" 35] [6.0 nil] ["b" 47] [8.0 nil]
          ["c" 11] [nil nil] ["c" 23] [2.0 nil] ["c" 35] [nil nil] ["c" 47] [nil nil]
          ["d" 11] [nil nil] ["d" 23] [nil nil] ["d" 35] [nil nil] ["d" 47] [9.0 nil]})
(def row (fn [u t] (raw [u t])))
(def f (im/impute row 2 ["a" "b" "c" "d"] os parent))

(deftest filled-from-the-last-value-toward-the-peers
  (testing "a value read at the origin is kept"
    (is (= 4.0 (first (f "b" 23)))))
  (testing "missing: the last value 12 months back, decayed toward the peers' mean (b's 4.0; c's 2.0)"
    (let [g (Math/pow 0.5 (/ 12.0 36.0))]
      (is (< (Math/abs (- (+ (* g 1.0) (* (- 1 g) 3.0)) (first (f "a" 23)))) 1e-12))))
  (testing "24 months back decays further"
    (let [g (Math/pow 0.5 (/ 24.0 36.0))]
      (is (< (Math/abs (- (+ (* g 1.0) (* (- 1 g) 6.0)) (first (f "a" 35)))) 1e-12))))
  (testing "never read: the peers' mean at the origin"
    (is (= 2.0 (first (f "c" 11))) "a's 1.0 and b's 3.0"))
  (testing "no peer has one: the mean of every country's, else nil"
    (is (= 6.0 (first (f "d" 35))) "region S has none at 35; every country's at 35: b's 6.0")
    (is (nil? (second (f "b" 11))) "no country has the second input at 11"))
  (testing "a last value but no peer's at the origin: the last value as it was"
    (is (= 5.0 (second (f "a" 47))))))

(deftest the-share-missing
  (is (= [0.5 1.0 0.5 1.0] (mapv #(peek (f "a" %)) os)))
  (is (= 3 (count (f "a" 11))) "one column more"))

(deftest only-at-the-origins
  (is (nil? (f "a" 12)) "a month not among the origins: nil, never a row one column short"))

