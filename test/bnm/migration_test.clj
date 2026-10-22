(ns bnm.migration-test
  (:require [bnm.migration :as mig]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (and a b (< (Math/abs (- (double a) (double b))) 1e-9)))

(def k {"P_HI" 0.30 "P_MID" 0.15 "P_LO" 0.05})

(defn- r [p y iso v]
  {:product p :year y :known (+ y 2) :exporter iso :value v
   :cap-intensity (k p) :cap-class (mig/classify (k p) [0.1 0.2])})

(def rows
  (vec (concat
        ;; USA loses the capital-intensive product to CHN over ten years
        (for [y (range 1995 2006)
              :let [us (- 60.0 (* 4.0 (- y 1995))) cn (- 100.0 us 20.0)]
              row [(r "P_HI" y "USA" us) (r "P_HI" y "CHN" cn) (r "P_HI" y "DEU" 20.0)
                   (r "P_LO" y "USA" 50.0) (r "P_LO" y "CHN" 50.0)
                   (r "P_MID" y "DEU" 10.0)]]
          row))))

(deftest classification
  (is (= "low" (mig/classify 0.05 [0.1 0.2])))
  (is (= "low" (mig/classify 0.1 [0.1 0.2])) "a cut belongs to the class below")
  (is (= "mid" (mig/classify 0.15 [0.1 0.2])))
  (is (= "high" (mig/classify 0.25 [0.1 0.2])))
  (is (nil? (mig/classify nil [0.1 0.2])))
  (testing "terciles: a third of the values at or below each cut"
    (let [[lo hi] (mig/tercile-cuts (map double (range 1 10)))]
      (is (= 3 (count (filter #(<= % lo) (range 1 10)))))
      (is (= 6 (count (filter #(<= % hi) (range 1 10))))))))

(deftest shares-sum-to-one
  (let [s (mig/world-shares rows)]
    (doseq [[_ g] (group-by (juxt :product :year) s)]
      (is (close? 1.0 (reduce + (map :share g)))))
    (is (close? 0.6 (:share (first (filter #(and (= "USA" (:exporter %)) (= "P_HI" (:product %)) (= 1995 (:year %))) s)))))))

(deftest country-aggregates
  (let [a (mig/country-years rows)]
    (testing "share of world exports in capital-intensive products"
      (is (close? 0.6 (get-in a [["USA" 1995] :ci-share])))
      (is (close? 0.2 (get-in a [["USA" 2005] :ci-share])))
      (is (close? 0.2 (get-in a [["DEU" 2005] :ci-share]))))
    (testing "share-weighted capital intensity of exports"
      (is (close? (/ (+ (* 60 0.3) (* 50 0.05)) 110.0) (get-in a [["USA" 1995] :cap-intensity])))
      (is (close? (/ (+ (* 20 0.3) (* 10 0.15)) 30.0) (get-in a [["DEU" 2000] :cap-intensity]))))))

(defn- val-of [out iso y v]
  (:value (first (filter #(and (= iso (:iso %)) (= y (:year %)) (= v (:variable %))) out))))

(deftest stock-rows
  (let [out (mig/stock-rows rows (range 1990 2010))]
    (is (every? #(.startsWith ^String (:variable %) "migration/") out))
    (testing "rows are dated by the year known, two years after the data"
      (is (nil? (val-of out "USA" 1996 "migration/ci-share")))
      (is (close? 0.6 (val-of out "USA" 1997 "migration/ci-share"))))
    (testing "changes over 5 and 10 years"
      (is (close? -0.2 (val-of out "USA" 2002 "migration/ci-share-d5")))
      (is (nil? (val-of out "USA" 2006 "migration/ci-share-d10")))
      (is (close? -0.4 (val-of out "USA" 2007 "migration/ci-share-d10"))))
    (testing "after the data end the last known value carries, changes too"
      (is (close? 0.2 (val-of out "USA" 2009 "migration/ci-share"))))))

(deftest causality
  (testing "a row known later never changes an earlier output"
    (let [late (r "P_HI" 2005 "USA" 1000.0)
          years (range 1995 2010)
          base (mig/stock-rows rows years)
          more (mig/stock-rows (conj (vec (remove #(and (= 2005 (:year %)) (= "USA" (:exporter %)) (= "P_HI" (:product %))) rows)) late) years)
          upto (fn [out] (set (filter #(< (:year %) 2007) out)))]
      (is (= (upto base) (upto more)))
      (is (not= (set base) (set more)))))
  (testing "no output row is dated before its inputs are known"
    (let [out (mig/stock-rows rows (range 1990 2010))]
      (is (= 1997 (apply min (map :year out)))))))
