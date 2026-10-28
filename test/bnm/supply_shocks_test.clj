(ns bnm.supply-shocks-test
  (:require [bnm.supply-shocks :as ss]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (and (some? a) (< (Math/abs (- (double a) (double b))) 1e-9)))

(deftest shortfall-against-the-three-years-before
  (is (close? 0.3 (ss/shortfall {2000 100.0 2001 100.0 2002 100.0 2003 70.0} 2003 10.0)))
  (is (close? 0.0 (ss/shortfall {2000 100.0 2001 100.0 2002 100.0 2003 130.0} 2003 10.0)) "a rise is no shortfall")
  (is (close? 1.0 (ss/shortfall {2001 100.0 2002 100.0 2003 0.0} 2003 10.0)) "two years before are enough; capped at 1")
  (is (nil? (ss/shortfall {2002 100.0 2003 0.0} 2003 10.0)) "one year before is not")
  (is (nil? (ss/shortfall {2000 5.0 2001 5.0 2002 5.0 2003 0.0} 2003 10.0)) "below the minimum base")
  (is (nil? (ss/shortfall {2000 100.0 2001 100.0 2002 100.0} 2003 10.0)) "no value for the year"))

(deftest known-values-only
  (let [s (sorted-map 2000 [2002 1.0] 2001 [2003 2.0] 2002 [2004 3.0])]
    (is (= {2000 1.0 2001 2.0} (ss/known-series s 2003)))
    (is (= 2.0 (ss/value-at s 2003 3)) "the latest data year known by then")
    (is (nil? (ss/value-at s 2001 3)) "nothing known yet")
    (is (nil? (ss/value-at s 2008 3)) "too old to carry")))

(deftest supplier-shares-from-the-latest-three-known-years
  (let [fl (sorted-map 1995 [1997 {"A" 100.0}]
                       1996 [1998 {"A" 50.0 "B" 50.0}]
                       1997 [1999 {"B" 100.0}]
                       1998 [2000 {"C" 100.0}]
                       1999 [2001 {"C" 1000.0}])]
    (is (= {"A" 0.75 "B" 0.25} (ss/supplier-shares fl 1998)) "1995-96, known by 1998: tonnes summed, then shares")
    (let [s (ss/supplier-shares fl 2000)]
      (is (= #{"A" "B" "C"} (set (keys s))) "the latest three data years known, 1996-98")
      (is (close? (/ 1.0 6) (s "A")))
      (is (close? 0.5 (s "B")))
      (is (close? (/ 1.0 3) (s "C"))))
    (is (= {} (ss/supplier-shares fl 2010)) "too old to carry")
    (is (= {} (ss/supplier-shares fl 1996)))))

(deftest strait-disruption-relative-to-all-straits
  (let [cap {"suez" (sorted-map 2019 [2019 100.0] 2020 [2020 100.0] 2021 [2021 60.0])
             "WLD" (sorted-map 2019 [2019 1000.0] 2020 [2020 1000.0] 2021 [2021 1200.0])}]
    (is (close? 0.5 (ss/strait-disruption cap "suez" 2021)) "0.6 of its level while all straits rose to 1.2")
    (is (close? 0.0 (ss/strait-disruption cap "suez" 2020)))
    (is (nil? (ss/strait-disruption cap "suez" 2019)) "no year before")
    (is (nil? (ss/strait-disruption cap "suez" 2022)) "not known yet")))

(def rows
  [;; supplier X's wheat exports fall by half in 2010 (known in the market year)
   {:source "psd" :iso "X" :product "wheat" :year 2007 :known 2007 :variable "exports" :value 1e6}
   {:source "psd" :iso "X" :product "wheat" :year 2008 :known 2008 :variable "exports" :value 1e6}
   {:source "psd" :iso "X" :product "wheat" :year 2009 :known 2009 :variable "exports" :value 1e6}
   {:source "psd" :iso "X" :product "wheat" :year 2010 :known 2010 :variable "exports" :value 5e5}
   ;; the EU's
   {:source "psd" :iso "EUN" :product "wheat" :year 2007 :known 2007 :variable "exports" :value 1e6}
   {:source "psd" :iso "EUN" :product "wheat" :year 2008 :known 2008 :variable "exports" :value 1e6}
   {:source "psd" :iso "EUN" :product "wheat" :year 2009 :known 2009 :variable "exports" :value 1e6}
   {:source "psd" :iso "EUN" :product "wheat" :year 2010 :known 2010 :variable "exports" :value 8e5}
   ;; importer C buys half its wheat abroad; wheat is all its staple use
   {:source "fbs" :iso "C" :product "wheat" :year 2009 :known 2009 :variable "import-dependence" :value 0.5}
   {:source "fbs" :iso "C" :product "wheat" :year 2009 :known 2009 :variable "use" :value 2e6}
   ;; a fifth of C's wheat imports pass Suez
   {:source "choke" :iso "C" :product "food" :year 2009 :known 2009 :variable "suez" :value 0.2}
   {:source "portwatch" :iso "suez" :product "bulk" :year 2008 :known 2008 :variable "capacity" :value 100.0}
   {:source "portwatch" :iso "suez" :product "bulk" :year 2009 :known 2009 :variable "capacity" :value 100.0}
   {:source "portwatch" :iso "suez" :product "bulk" :year 2010 :known 2010 :variable "capacity" :value 50.0}
   {:source "portwatch" :iso "WLD" :product "bulk" :year 2008 :known 2008 :variable "capacity" :value 1000.0}
   {:source "portwatch" :iso "WLD" :product "bulk" :year 2009 :known 2009 :variable "capacity" :value 1000.0}
   {:source "portwatch" :iso "WLD" :product "bulk" :year 2010 :known 2010 :variable "capacity" :value 1000.0}])

(def flows
  [{:product "wheat" :importer "C" :exporter "X" :year 2006 :known 2008 :tonnes 600.0}
   {:product "wheat" :importer "C" :exporter "FRA" :year 2006 :known 2008 :tonnes 400.0}])

(deftest the-food-loss-of-an-importer
  (let [db (ss/index rows flows)]
    (testing "exporter shortfalls weighed by supplier shares, times dependence"
      ;; X: 0.6 x 0.5; FRA reads the EU's 0.2: 0.4 x 0.2; dependence 0.5 -> 0.19
      ;; Suez: 0.2 of imports x a disruption of 0.5 x dependence 0.5 -> 0.05
      (is (close? (+ 0.19 0.05) (ss/family-loss db "food" "C" 2010)))
      (is (close? 0.19 (:exporters (ss/family-parts db "food" "C" 2010))))
      (is (close? 0.05 (:straits (ss/family-parts db "food" "C" 2010)))))
    (testing "no shortfall the year before"
      (is (close? 0.0 (ss/family-loss db "food" "C" 2009))))
    (testing "unknown exposure is missing, not zero"
      (is (nil? (ss/family-loss db "food" "Z" 2010)))
      (is (nil? (ss/family-loss db "energy" "C" 2010))))))

(deftest nothing-known-later-enters
  (let [x2010? #(and (= "X" (:iso %)) (= 2010 (:year %)))
        db1 (ss/index (remove x2010? rows) flows)
        db2 (ss/index (map #(if (x2010? %) (assoc % :known 2011) %) rows) flows)]
    (is (= (ss/family-loss db1 "food" "C" 2010) (ss/family-loss db2 "food" "C" 2010))
        "a value known only in 2011 is as if absent in 2010")
    (is (< (ss/family-loss db2 "food" "C" 2010) (ss/family-loss (ss/index rows flows) "food" "C" 2010))
        "and known in 2010 it counts")))

(deftest placebos
  (let [panel {"A" {2000 1.0} "B" {2000 2.0} "C" {2000 3.0} "D" {2001 4.0}}]
    (testing "exposure reassigned: a derangement, fixed by its seed"
      (let [p (ss/derangement ["A" "B" "C" "D"] 1)]
        (is (= #{"A" "B" "C" "D"} (set (keys p)) (set (vals p))))
        (is (every? (fn [[a b]] (not= a b)) p))
        (is (= p (ss/derangement ["D" "C" "B" "A"] 1)) "the order given does not matter")))
    (testing "dates shifted back: the value of `lag` years before, still known by then"
      (is (= {"A" {2006 1.0} "B" {2006 2.0} "C" {2006 3.0} "D" {2007 4.0}} (ss/shift-panel panel 6))))))

(deftest ordinary-variation-is-no-shock
  (let [db (ss/index (concat (remove #(and (= "X" (:iso %)) (= 2010 (:year %))) rows)
                             [{:source "psd" :iso "X" :product "wheat" :year 2010 :known 2010 :variable "exports" :value 9.5e5}])
                     flows)]
    (testing "X's 5% drop is under the 10% floor; the EU's 20% counts"
      (is (close? 0.0 (ss/exporter-shortfall db (first (ss/families "food")) "X" 2010)))
      (is (close? (+ (* 0.5 0.4 0.2) 0.05) (ss/family-loss db "food" "C" 2010))))))

(deftest energy-dependence-without-production
  (let [db (ss/index [{:source "owid" :iso "C" :product "oil" :year 2009 :known 2010 :variable "use" :value 100.0}
                      {:source "owid" :iso "C" :product "oil" :year 2009 :known 2010 :variable "share" :value 0.4}
                      {:source "owid" :iso "D" :product "oil" :year 2009 :known 2010 :variable "use" :value 100.0}
                      {:source "owid" :iso "D" :product "oil" :year 2009 :known 2010 :variable "production" :value 75.0}]
                     [])]
    (is (close? 1.0 (ss/dependence db "energy" "oil" "C" 2010)) "a country with use and no production series imports it all")
    (is (close? 0.25 (ss/dependence db "energy" "oil" "D" 2010)))
    (is (nil? (ss/dependence db "energy" "gas" "C" 2010)) "no use known")))
