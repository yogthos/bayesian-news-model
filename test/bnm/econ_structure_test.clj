(ns bnm.econ-structure-test
  (:require [bnm.econ-structure :as es]
            [bnm.states :as states]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (and a b (< (Math/abs (- (double a) (double b))) 1e-9)))

(defn- r [iso y src fin man] {:iso iso :year y :source src :fin fin :man man})

;; AAA: the benchmark (OECD) starts in 1995; the spine's LR-WIOD finance is
;; half of it, WIOD 2016's equal. BBB: the benchmark starts in 2003, inside
;; WIOD 2016, so LR-WIOD's ratio is chained through 2000. CCC: no benchmark.
(def rows
  (vec (concat
        (for [y (range 1990 2000)] (r "AAA" y "lrwiod" 0.02 0.20))
        [(r "AAA" 2000 "lrwiod" 0.02 0.20)]
        (for [y (range 2000 2005)] (r "AAA" y "wiod16" 0.04 0.20))
        (for [y (range 1995 2003)] (r "AAA" y "oecd" 0.04 0.18))
        (for [y (range 1990 2000)] (r "BBB" y "lrwiod" 0.05 0.30))
        [(r "BBB" 2000 "lrwiod" 0.05 0.30)]
        (for [y (range 2000 2006)] (r "BBB" y "wiod16" 0.10 0.30))
        (for [y (range 2003 2006)] (r "BBB" y "oecd" 0.08 0.24))
        (for [y (range 1990 1993)] (r "CCC" y "lrwiod" 0.03 0.10))
        [(r "USA" 1990 "lrwiod" 0.03 0.18) (r "USA" 1990 "bea" 0.057 0.17)
         (r "USA" 1990 "oecd" 0.06 0.16) (r "USA" 1991 "lrwiod" 0.03 0.18)])))

(defn- at [out iso y v]
  (first (filter #(and (= iso (:iso %)) (= y (:year %)) (= v (:variable %))) out)))

(def out (es/series rows))
(def fin "econ/finance-va-share")
(def man "econ/manufacturing-va-share")

(deftest benchmark-years-take-the-benchmark
  (is (close? 0.04 (:value (at out "AAA" 1997 fin))))
  (is (close? 0.18 (:value (at out "AAA" 1997 man))))
  (is (= "oecd" (:source (at out "AAA" 1997 fin))))
  (is (= 1999 (:known (at out "AAA" 1997 fin))) "OECD known two years on")
  (testing "BEA first for the US, known a year on"
    (is (close? 0.057 (:value (at out "USA" 1990 fin))))
    (is (= "bea" (:source (at out "USA" 1990 fin))))
    (is (= 1991 (:known (at out "USA" 1990 fin))))))

(deftest spliced-before-the-benchmark
  (let [x (at out "AAA" 1990 fin)]
    (is (close? 0.04 (:value x)) "LR-WIOD's level doubled, the overlap ratio")
    (is (= "lrwiod*oecd" (:source x)))
    (testing "known when the first overlap year is published, not before"
      ;; overlap 1995: OECD known 1997, the spine 1998
      (is (= 1998 (:known x)))))
  (is (close? 0.18 (:value (at out "AAA" 1990 man))))
  (testing "a year after the benchmark ends: the ratio of the overlap known by then"
    (let [x (at out "AAA" 2004 fin)]
      (is (close? 0.04 (:value x)))
      (is (= "wiod16*oecd" (:source x)))
      (is (= 2007 (:known x))))))

(deftest only-published-overlap-years
  ;; USA 1991 has no benchmark; the overlap (1990) is published by 1993
  (let [x (at out "USA" 1991 fin)]
    (is (close? 0.057 (:value x)))
    (is (= 1994 (:known x)))))

(deftest chained-through-the-join
  (let [x (at out "BBB" 1995 fin)]
    ;; WIOD 2016 to OECD 0.8; LR-WIOD to WIOD 2016 at 2000: 2
    (is (close? 0.08 (:value x)))
    (is (= "lrwiod*oecd" (:source x)))
    ;; first overlap 2003 (OECD known 2005, WIOD 2016 2006)
    (is (= 2006 (:known x))))
  (is (close? 0.24 (:value (at out "BBB" 1995 man)))))

(deftest no-benchmark-keeps-the-spine
  (let [x (at out "CCC" 1991 fin)]
    (is (close? 0.03 (:value x)))
    (is (= "lrwiod" (:source x)))
    (is (= 1994 (:known x)))))

(deftest one-row-per-country-year-variable
  (is (= (count out) (count (distinct (map (juxt :iso :year :variable) out)))))
  (is (every? #(< (:year %) (:known %)) out))
  (is (every? #(< 0.0 (:value %) 1.0) out)))

(def stock (es/stock-rows out))

(deftest stock-rows-are-by-the-year-known
  (testing "one row per country, known year and variable"
    (is (= (count stock) (count (distinct (map (juxt :iso :year :variable) stock))))))
  (testing "each known year reads the latest data year known by then"
    ;; AAA: OECD 1995 known 1997; LR-WIOD's back-cast (known 1998) never
    ;; becomes the latest; 1998 reads OECD 1996
    (let [at (fn [y] (first (filter #(and (= "AAA" (:iso %)) (= y (:year %)) (= fin (:variable %))) stock)))]
      (is (nil? (at 1996)))
      (is (= 1995 (:data-year (at 1997))))
      (is (= 1996 (:data-year (at 1998))))
      (is (close? 0.04 (:value (at 1998))))
      (is (= 2002 (:data-year (at 2004))) "OECD 2002, known 2004")
      (is (= 2004 (:data-year (at 2007))) "WIOD 2016 2004 spliced, known 2007")))
  (is (every? #(<= (+ (:data-year %) 1) (:year %)) stock)))

(deftest read-states-sees-nothing-before-it-is-known
  (let [f (java.io.File/createTempFile "econ-structure" ".csv")]
    (es/write-stock! (.getPath f) stock)
    (let [s (states/read-states [(.getPath f)])
          first-known (reduce (fn [m r] (update m [(:variable r) (:iso r)] (fnil min Long/MAX_VALUE) (:known r))) {} out)]
      (is (seq s))
      (doseq [[v by-iso] s [iso byy] by-iso]
        (is (<= (get first-known [v iso]) (first (keys byy))) (str v " " iso)))
      (is (close? 0.057 (get-in s [fin "USA" 1991])) "BEA 1990, known 1991")
      (is (nil? (get-in s [fin "USA" 1990]))))))
