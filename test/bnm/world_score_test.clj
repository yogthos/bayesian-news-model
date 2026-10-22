(ns bnm.world-score-test
  (:require [bnm.world-score :as ws]
            [clojure.test :refer [deftest is]]))

(deftest both-onsets-within-three-years
  (let [cats {"a" {:onsets {"P" #{2005}} :cover {"P" [1990 2020]}}
              "b" {:onsets {"P" #{2007}} :cover {"P" [1990 2009]}}}
        rows (ws/joint-rows ["a\tb\tP\t2004\t0.2\t0.1\t0.05" "a\tb\tP\t2005\t0.2\t0.1\t0.05" "a\tb\tP\t2008\t0.2\t0.1\t0.05"] cats)]
    (is (= 2 (count rows)) "2008 + 3 is past b's coverage")
    (is (= [2004 0.05 (* 0.2 0.1) true "P" "a>b"] (vec (first rows))) "2005 and 2007 within (2004, 2007]")
    (is (false? (nth (second rows) 3)) "from 2005, a's onset is not after it")))

(deftest joint-path-only-when-the-run-wrote-one
  ;; a run without relations writes no joint forecasts: the joint test is skipped, not a crash
  (is (nil? (ws/joint-path "no-such-run-anywhere"))))
