(ns bnm.math-test
  (:require [bnm.math :as m]
            [bnm.time :as t]
            [clojure.test :refer [deftest is]]))

(defn- close? [a b tol] (< (Math/abs (- a b)) tol))

(deftest log-gamma-matches-factorials
  (doseq [n (range 1 20)]
    (is (close? (m/log-gamma (double n))
                (Math/log (double (reduce * 1N (range 1 n)))) 1e-9)))
  (is (close? (m/log-gamma 0.5) (Math/log (Math/sqrt Math/PI)) 1e-12)))

(deftest samplers-have-the-right-moments
  (let [g (m/rng 123) n 20000
        mean (fn [xs] (/ (reduce + xs) (double n)))]
    (is (close? (mean (repeatedly n #(m/gamma! g 2.5 2.0))) 5.0 0.15))
    (is (close? (mean (repeatedly n #(m/gamma! g 0.4 1.0))) 0.4 0.03))
    (is (close? (mean (repeatedly n #(double (m/poisson! g 4.0)))) 4.0 0.1))
    (is (close? (mean (repeatedly n #(m/beta! g 2.0 6.0))) 0.25 0.01))))

(deftest generator-is-reproducible
  (is (= (repeatedly 5 #(m/next-double! (m/rng 9)))
         (repeatedly 5 #(m/next-double! (m/rng 9))))))

(deftest calendar-round-trips
  (doseq [d ["19700101" "19790101" "20000229" "20231007" "21001231"]]
    (is (= d (t/date-str (t/day d)))))
  (is (= 19637 (t/day "20231007"))))
