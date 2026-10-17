(ns bnm.polecon-test
  (:require [bnm.polecon :as pe]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-12))

(deftest ratios-by-year
  (testing "profit rate is the year's profits over the previous year's capital"
    (let [r (pe/profit-rate {2000 10.0 2001 12.0} {1999 100.0 2000 120.0 2001 200.0})]
      (is (close? 0.1 (r 2000)))
      (is (close? 0.1 (r 2001)))
      (is (= 2 (count r)))))
  (testing "a ratio of two annual series where both have the year"
    (is (= {2001 0.5} (pe/ratio {2000 1.0 2001 2.0} {2001 4.0 2002 5.0})))))

(deftest point-in-time
  (let [s (pe/annual-series {2000 1.0 2001 2.0 2002 4.0} 3)]
    (testing "year Y is known from month lag of Y+1"
      (is (nil? (pe/known s (+ (* 12 2001) 2))))
      (is (= [2000 1.0] (pe/known s (+ (* 12 2001) 3))))
      (is (= [2002 4.0] (pe/known s (* 12 2010)))))
    (testing "change over k years of the latest known value"
      (is (close? 3.0 (pe/change s (* 12 2004) 2)))
      (is (close? 2.0 (pe/change s (* 12 2004) 1)))
      (is (nil? (pe/change s (+ 3 (* 12 2001)) 1))))
    (testing "deviation from the mean of the known years before it, given at least n of them"
      (is (close? (- 4.0 1.5) (pe/deviation s (* 12 2004) 10 2)))
      (is (nil? (pe/deviation s (* 12 2004) 10 3)))
      (testing "within a window of years"
        (is (close? (- 4.0 2.0) (pe/deviation s (* 12 2004) 1 1)))))))
