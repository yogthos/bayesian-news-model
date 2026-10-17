(ns bnm.invariants-test
  (:require [bnm.invariants :as iv]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest stretch-against-own-history
  (let [xs [1.0 2.0 3.0 nil 10.0]]
    (testing "z of x_i against the window before it (sample sd), nils skipped"
      ;; before index 4: 1 2 3 -> mean 2, sd 1
      (is (close? 8.0 (iv/stretch xs 4 10 3))))
    (testing "too little history"
      (is (nil? (iv/stretch xs 4 10 4)))
      (is (nil? (iv/stretch xs 3 10 2)) "no value at i"))
    (testing "window limits the history"
      ;; before index 4 within 2: [3 nil] -> one value
      (is (nil? (iv/stretch xs 4 2 2))))
    (testing "flat history gives nil, not infinity"
      (is (nil? (iv/stretch [1.0 1.0 1.0 2.0] 3 10 3))))))

(deftest aggregates-across-dimensions
  (let [a (iv/aggregate [2.0 -3.0 nil 0.5] {:threshold 1.5 :min-n 2})]
    (is (close? 3.0 (:max a)) "largest displacement, either direction")
    (is (close? (/ 2.0 3.0) (:breadth a)) "share of known dimensions past the threshold")
    (is (close? (Math/sqrt (/ (+ 4.0 9.0 0.25) 3.0)) (:norm a)) "root mean square displacement")
    (is (= 3 (:n a))))
  (is (nil? (iv/aggregate [2.0 nil] {:threshold 1.5 :min-n 2}))))

(deftest variability-rising
  (testing "log ratio of recent to longer variance of the changes"
    (let [xs [0.0 1.0 0.0 1.0 0.0 1.0 0.0 3.0 0.0 3.0]]
      (is (pos? (iv/variance-ratio xs 9 4 9)))
      (is (nil? (iv/variance-ratio xs 3 4 9))))))

(deftest kernels-over-past-events
  (let [events {2000 1.0 2005 0.5 2010 2.0}]
    (testing "sum of past weights decayed by exp(-age/tau), the year itself included"
      (is (close? (+ (Math/exp -1.0) (* 0.5 (Math/exp -0.5))) (iv/kernel events 2010 10.0 {:through 2009})))
      (is (close? (+ (Math/exp -1.0) (* 0.5 (Math/exp -0.5)) 2.0) (iv/kernel events 2010 10.0 {:through 2010}))))
    (testing "nothing before the first event"
      (is (zero? (iv/kernel events 1999 10.0 {:through 1999}))))))
