(ns bnm.geo-gap-test
  (:require [bnm.geo-gap :as gg]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(def inputs
  ;; {indicator {iso {data-year [known value]}}}
  {"steel-share" {"A" {2000 [2002 0.4]} "B" {2000 [2002 0.1]}}
   "energy-share" {"A" {2000 [2001 0.2]} "B" {2000 [2001 0.3]}}
   "manuf-share" {"A" {2000 [2001 0.3]}}
   "mcap-share" {"A" {2000 [2001 0.05]} "B" {2000 [2001 0.4]}}
   "reserve-share" {"A" {2000 [2001 0.01]} "B" {2000 [2001 0.6]}}})

(deftest the-gap-is-material-less-financial-known-when-its-last-part-is
  (let [rows (gg/stock-rows inputs)
        at (fn [iso v] (first (filter #(and (= iso (:iso %)) (= v (:variable %))) rows)))]
    (is (close? 0.3 (:value (at "A" "geo/material-share")) 1e-12))
    (is (= 2002 (:known (at "A" "geo/material-share"))) "steel is published two years on")
    (is (close? 0.2 (:value (at "B" "geo/material-share")) 1e-12) "two of three parts are enough")
    (is (close? 0.03 (:value (at "A" "geo/financial-share")) 1e-12))
    (is (close? 0.27 (:value (at "A" "geo/capability-gap")) 1e-12))
    (is (close? -0.3 (:value (at "B" "geo/capability-gap")) 1e-12))
    (is (= 2002 (:known (at "A" "geo/capability-gap"))))
    (doseq [r rows] (is (= (:year r) (:known r))) (is (> (:known r) (:data-year r)))))
  (testing "one material part is not enough"
    (let [rows (gg/stock-rows (assoc inputs "steel-share" {}))]
      (is (nil? (first (filter #(and (= "B" (:iso %)) (= "geo/material-share" (:variable %))) rows))))))
  (testing "the financial share needs both parts"
    (let [rows (gg/stock-rows (assoc inputs "reserve-share" {}))]
      (is (empty? (filter #(= "geo/financial-share" (:variable %)) rows))))))

(deftest the-dyad-panel-reads-the-gap-as-known-and-the-outcome-after
  (let [gap {"A" {2000 [2002 0.5] 2001 [2003 0.6]} "B" {2000 [2002 0.1] 2001 [2003 0.0]}}
        dyads [{:a "A" :b "B" :year 2003 :mid-any 1.0} {:a "A" :b "B" :year 2004 :mid-any 0.0}]
        panel (gg/dyad-panel gap dyads :mid-any)]
    (is (= #{2002 2003} (set (map :t panel))) "origin t, outcome t + 1")
    (let [r2002 (first (filter #(= 2002 (:t %)) panel))
          r2003 (first (filter #(= 2003 (:t %)) panel))]
      (is (close? 0.4 (:level r2002) 1e-12) "at 2002 only data year 2000 is out")
      (is (close? 0.6 (:level r2003) 1e-12))
      (is (= 1.0 (:y r2002)))
      (is (= 0.0 (:y r2003))))
    (testing "a value published later changes nothing at an earlier origin"
      (let [later (assoc-in gap ["A" 2001] [2003 100.0])]
        (is (= (first (filter #(= 2002 (:t %)) panel))
               (first (filter #(= 2002 (:t %)) (gg/dyad-panel later dyads :mid-any)))))))))

(deftest the-permutation-is-reproducible-and-a-share
  (let [rows (vec (for [d (range 8) t (range 2000 2015)]
                    {:g d :t t :x [(double (+ d (* 0.1 t)))] :y (if (even? (+ d t)) 1.0 0.0) :level (double (+ d (* 0.1 t)))}))
        p1 (gg/permutation-p rows 99 7)
        p2 (gg/permutation-p rows 99 7)]
    (is (= p1 p2))
    (is (<= 0.0 p1 1.0))))
