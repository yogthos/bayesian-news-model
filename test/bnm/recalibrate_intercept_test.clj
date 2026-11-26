(ns bnm.recalibrate-intercept-test
  "bnm-7yg.4 (adopted 26 Nov 2026): --recalibrate-intercept, the year
  country nodes' log odds moved by one intercept per December origin, fitted
  on rows resolved 48 months before within the trailing ten years
  (docs/studies/recalibrate_intercept.py, t157-recal)."
  (:require [bnm.recalibrate-intercept :as ri]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- o [y] (+ (* 12 y) 11))
(defn- near [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest the-intercept
  (testing "an under-forecast moves up to the realized rate"
    (let [rows (for [i (range 5000)] [(ri/logit 0.01) (zero? (mod i 10))])]
      (is (< (Math/abs (- (ri/intercept rows) (- (ri/logit 0.1) (ri/logit 0.01)))) 0.05))))
  (testing "no rows, no shift"
    (is (= 0.0 (ri/intercept [])))))

(deftest shifts-read-only-resolved-origins
  (let [fs (for [yr (range 1990 2023) i (range 50)]
             {:scale :year :place (str "P" (char (+ 65 (quot i 26))) (char (+ 65 (mod i 26)))) :origin (o yr) :p 0.01
              :y (if (>= yr 2000) (< i 5) (and (zero? i) (even? yr)))})
        a (ri/shifts fs 2003)]
    (is (< (a (o 2003)) 1.5) "2003 reads 1990-1999")
    (is (> (a (o 2012)) 2.0) "2012 reads 1999-2008")
    (is (contains? a (o 2022)))
    (is (not (contains? a (o 2002))))))

(deftest recalibrate-moves-only-year-country-rows
  (let [fs (concat (for [yr (range 1990 2010) [i p] (map-indexed vector [0.01 0.02 0.05])]
                     {:scale :year :place (str "P" i "X") :origin (o yr) :p p :y (= i 2)})
                   [{:scale :quarter-lead :place "P0X" :origin (o 2005) :p 0.3 :y false}
                    {:scale :year :place "world" :origin (o 2005) :p 0.4 :y true}
                    {:scale :year :place "P0X" :origin (- (o 2008) 3) :p 0.02 :y false}])
        out (ri/recalibrate fs 2003)
        by (group-by (juxt :scale :place :origin) out)]
    (is (= (count fs) (count out)))
    (is (= 0.3 (:p (first (by [:quarter-lead "P0X" (o 2005)])))))
    (is (= 0.4 (:p (first (by [:year "world" (o 2005)])))) "aggregates as issued")
    (is (= 0.02 (:p (first (by [:year "P0X" (- (o 2008) 3)])))) "non-December origins as issued")
    (is (= 0.01 (:p (first (by [:year "P0X" (o 2001)])))) "before the first origin as issued")
    (let [ps (mapv #(:p (first (by [:year % (o 2008)]))) ["P0X" "P1X" "P2X"])]
      (is (= ps (sort ps)) "ranking within an origin unchanged")
      (is (not= 0.05 (ps 2))))))

(deftest the-option
  (is (nil? (wr/recalibrate-intercept-choice {})))
  (is (= #{"inflation_crisis"} (wr/recalibrate-intercept-choice {:recalibrate-intercept "inflation_crisis"}))))

(deftest matches-the-study-on-the-base
  ;; docs/studies/recalibrate_intercept.py wrote t157-recal from t150-base (2003-2022)
  (let [f (fn [run] (str "data/runs/" run "/inflation_crisis/forecasts.tsv"))]
    (when (and (.exists (io/file (f "t150-base"))) (.exists (io/file (f "t157-recal"))))
      (let [read (fn [run] (vec (for [l (str/split-lines (slurp (f run)))
                                      :let [[s u o p y] (str/split l #"\t")]]
                                  {:scale (keyword s) :place u :origin (parse-long o) :p (parse-double p)
                                   :y (case y "true" true "false" false nil)})))
            ours (ri/recalibrate (read "t150-base") 2003)
            study (read "t157-recal")
            in-study (fn [{:keys [origin]}] (<= 2003 (quot origin 12) 2022))]
        (is (= (count ours) (count study)))
        (is (every? true? (map (fn [a b] (or (not (in-study a)) (near (:p a) (:p b)))) ours study)))))))
