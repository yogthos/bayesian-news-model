(ns bnm.shock-trial-test
  (:require [augur.math :as m]
            [bnm.shock-trial :as st]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (and (some? a) (< (Math/abs (- (double a) (double b))) 1e-9)))

(deftest the-last-origin-a-fit-may-read
  (is (= 2007 (st/fit-cut 2010 3)) "window closed at the end of 2009, published within a year")
  (is (= 2006 (st/fit-cut 2010 18)) "eighteen months to publish: two more years")
  (is (= 2008 (st/fit-cut 2010 0))))

(deftest feature-scale
  (is (close? 0.0 (st/feature nil)) "missing is no shock")
  (is (close? 0.0 (st/feature 0.0)))
  (is (close? (Math/log 2.0) (st/feature 0.01)))
  (is (< (st/feature 0.1) (st/feature 0.5))))

(deftest exposure-placebo-keeps-coverage
  (let [panel {"A" {2000 0.1} "B" {2000 0.3} "C" {2001 0.5}}
        perm {"A" "B" "B" "C" "C" "A"}
        p (st/swap-panel panel perm)]
    (is (= 0.3 (get-in p ["A" 2000])) "A reads B's")
    (is (close? 0.2 (get-in p ["B" 2000])) "C has none in 2000: the year's mean")
    (is (nil? (get-in p ["C" 2000])) "C had none: still none")
    (is (close? 0.5 (get-in p ["C" 2001])) "A has none in 2001: the year's mean, C's own being the only one")))

(deftest holm-step-down
  (is (= [true true false] (st/holm [0.01 0.02 0.20] 0.05)))
  (is (= [false false false] (st/holm [0.03 0.04 0.05] 0.05)) "0.03 > 0.05/3 stops it")
  (is (= [true false true] (st/holm [0.001 0.5 0.02] 0.05)) "in the p-values' own order"))

(defn- synthetic
  "Rows of one kind 1990-2010, 60 countries: the base knows half, the
  feature x the other half."
  [seed]
  (let [g (m/rng seed)]
    (vec (for [t (range 1990 2011) c (range 60)
               :let [b (m/normal! g) x (if (< (m/next-double! g) 0.2) (m/next-double! g) 0.0)
                     eta (+ -2.5 (* 0.8 b) (* 6.0 x))]]
           {:c (str "C" c) :t t :p (m/expit (+ -2.5 (* 0.8 b))) :y (< (m/next-double! g) (m/expit eta))
            :x {:real [(st/feature x) 0.0 0.0] :exposure [0.0 0.0 0.0] :time [0.0 0.0 0.0]}}))))

(deftest expanding-forecasts-read-only-closed-windows
  (let [rows (synthetic 3)
        out (st/forecasts rows {:lag 3 :from 2003 :to 2010 :fit-from 1990})
        by (group-by :t out)]
    (is (= (set (range 2003 2011)) (set (keys by))))
    (is (every? #(every? (fn [k] (< 0.0 (get-in % [:p k]) 1.0)) [:base :bp :s :plc :tim]) out))
    (testing "the shocks' model ranks better where the shocks matter"
      (let [ll (fn [k] (reduce + (map #(let [p (get-in % [:p k])] (- (Math/log (if (:y %) p (- 1.0 p))))) out)))]
        (is (< (ll :s) (ll :bp)))))
    (testing "outcomes after the cut change nothing at the origin"
      (let [flip (mapv #(if (> (:t %) 2007) (update % :y not) %) rows)
            a (filter #(= 2010 (:t %)) out)
            b (filter #(= 2010 (:t %)) (st/forecasts flip {:lag 3 :from 2003 :to 2010 :fit-from 1990}))]
        (is (= (map #(get-in % [:p :s]) a) (map #(get-in % [:p :s]) b)))))
    (testing "too few onsets to fit: the base"
      (let [quiet (mapv #(assoc % :y (and (= 2009 (:t %)) (= "C1" (:c %)))) rows)
            o (st/forecasts quiet {:lag 3 :from 2003 :to 2004 :fit-from 1990})]
        (is (every? #(= (get-in % [:p :base]) (get-in % [:p :s]) (get-in % [:p :bp])) o))))))
