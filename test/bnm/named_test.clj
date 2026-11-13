(ns bnm.named-test
  (:require [bnm.named :as nm]
            [bnm.stocks :as sk]
            [clojure.test :refer [deftest is testing]]))

(defn- monthly
  "A series of `v` from month a to b, each published `lag` months after."
  [a b lag v]
  (sk/series (for [t (range a (inc b))] [t (+ t lag) (v t)])))

(defn- build
  "A family over sources \"s\" (inputs :a, :b) and \"c\" (input :c), each
  stale after 6 months, and :d in no group."
  [db]
  (fn []
    [[:a :b :c :d]
     (fn [u t]
       (let [g #(some-> (db [u %]) (sk/value t {:stale 6}))]
         [(g "s") (some-> (g "s") (* 2.0)) (g "c") (g "d")]))]))

(def groups [[:a :b] [:c]])

(deftest a-lapsed-source-keeps-its-last-values-and-is-flagged
  (let [db {["X" "s"] (monthly 0 100 1 double)
            ["X" "c"] (monthly 0 300 1 #(- (double %)))
            ["X" "d"] (monthly 0 100 1 (constantly 7.0))
            ;; Y never had "s"
            ["Y" "c"] (monthly 0 300 1 (constantly 1.0))}
        [labels f] (nm/vectorizer (build db) groups)
        v #(vec (f %1 %2))]
    (is (= [:a :b :c :d :a-present :c-present :a-lapsed :c-lapsed] labels))
    (testing "fresh"
      (is (= [99.0 198.0 -99.0 7.0 1.0 1.0 0.0 0.0] (v "X" 100))))
    (testing "past its staleness: the last values, present, lapsed"
      (is (= [100.0 200.0 -200.0 7.0 1.0 1.0 1.0 0.0] (v "X" 201))))
    (testing "a unit the source never covered reads absent, as before"
      (is (= [0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0] (v "Y" 201))))
    (testing "an input in no group keeps its last value"
      (is (= 7.0 (nth (v "X" 250) 3))))))

(deftest a-resumed-source-is-read-fresh-again
  (let [db {["X" "s"] (sk/series (concat (for [t (range 0 101)] [t (inc t) 1.0])
                                         (for [t (range 150 201)] [t (inc t) 5.0])))
            ["X" "c"] (monthly 0 300 1 (constantly 1.0))}
        [_ f] (nm/vectorizer (build db) groups)]
    (is (= 1.0 (nth (vec (f "X" 130)) 6)) "lapsed between")
    (is (= [5.0 10.0] (subvec (vec (f "X" 160)) 0 2)))
    (is (= 0.0 (nth (vec (f "X" 160)) 6)) "fresh once it resumes")))

(deftest nothing-published-after-t-is-read
  ;; the value for month 100 is published at month 200: before then the
  ;; lapsed source carries month 99's, not it
  (let [db {["X" "s"] (sk/series (concat (for [t (range 0 100)] [t (inc t) 1.0]) [[100 200 9.0]]))
            ["X" "c"] (monthly 0 300 1 (constantly 1.0))}
        [_ f] (nm/vectorizer (build db) groups)]
    (is (= [1.0 2.0] (subvec (vec (f "X" 150)) 0 2)))
    (is (= 1.0 (nth (vec (f "X" 150)) 6)))
    (is (= [9.0 18.0] (subvec (vec (f "X" 200)) 0 2)) "read once published")
    (is (false? sk/*keep-stale*) "the binding does not leak")))

(deftest the-families-label-their-lapses
  (doseq [fam (keys nm/presence)]
    (let [p (count (nm/presence fam))]
      (is (= p (count (filter #(.endsWith (name %) "-lapsed") (nm/lapsed-labels fam)))) fam))))

(deftest the-penn-world-table-does-not-lapse-each-september
  ;; a year ending in December published two years later: under 30 months'
  ;; staleness the latest year was stale every September
  (let [pwt (sk/series (for [y (range 2000 2024)] [(+ 11 (* 12 y)) (+ 11 (* 12 (+ y 2))) 0.1]))
        db {["X" "economy/profit_rate"] pwt ["X" "economy/labor_share"] pwt}
        sep (+ 8 (* 12 2024))
        [_ f30] (nm/political-economy db ["X"] [sep])
        [_ f36] (nm/political-economy db ["X"] [sep] {:stale nm/publication-stale})]
    (is (nil? (first (f30 "X" sep))))
    (is (= 0.1 (first (f36 "X" sep))))))

;; bnm-a1e: slow-normalize.py dates every monthly FRED series +1 month after
;; its month; the IMF primary commodity prices reach FRED later (ALFRED,
;; data/raw/alfred). Under :real-lags a value is not read before its true
;; publication month; without it the base reads as before.

(defn- tsv-file
  "A series.tsv of `rows` [unit var pend avail value] in a temp file."
  [rows]
  (let [f (java.io.File/createTempFile "bnm-series" ".tsv")]
    (spit f (apply str (for [[u v pe av x] rows] (str u "\t" v "\tm\t" pe "\t" av "\t" x "\n"))))
    (.getPath f)))

(defn- ym [t] (format "%04d-%02d-28" (quot t 12) (inc (rem t 12))))

(defn- food-rows
  "world food/price_index as slow-normalize writes it (+1 month), Jan 2019
  to Oct 2020, the index 100 then 150 from Jan 2020 (bnm.slow-train/
  month-of reads the year and month only)."
  []
  (for [t (range (* 12 2019) (+ 10 (* 12 2020)))]
    ["world" "food/price_index" (ym t) (ym (inc t)) (if (< t (* 12 2020)) 100.0 150.0)]))

(def ^:private food-vars [{:name "food/price_index" :transform "log"}])

(deftest bnm-a1e-imf-prices-are-not-read-before-their-publication
  (let [path (tsv-file (food-rows))
        base (nm/load-series path food-vars)
        real (nm/load-series path food-vars {:real-lags true})
        oct (+ 9 (* 12 2020)) nov (inc oct)
        at (fn [db t] (sk/value (db ["world" "food/price_index"]) t))]
    (testing "the base: October's value read at the November origin"
      (is (= (Math/log 150.0) (at base nov))))
    (testing "real lags: published two months after its month"
      (is (= 2 (nm/real-lag ["world" "food/price_index"])))
      (is (= (Math/log 150.0) (at real nov)) "September's, also 150")
      (is (nil? (sk/value (real ["world" "food/price_index"]) (+ 1 (* 12 2019)))) "January 2019's not before March")
      (is (= (Math/log 100.0) (sk/value (real ["world" "food/price_index"]) (+ 2 (* 12 2019))))))
    (testing "the leading food gauge reads the published value only"
      (let [[_ fb] (nm/leading base) [_ fr] (nm/leading real)
            jan (* 12 2020) feb (inc jan) mar (+ 2 jan)]
        (is (< 0.4 (nth (fb "XXX" feb) 3)) "base: January's jump read at February")
        (is (nil? (nth (fr "XXX" feb) 3)) "real: at February December is the newest, and no December before it")
        (is (< 0.4 (nth (fr "XXX" mar) 3)) "real: read at March")))))

(deftest bnm-a1e-every-monthly-fred-series-has-an-audited-lag
  ;; the audit (data/raw/alfred): each series the lattice reads monthly
  ;; from FRED is published at least as late as slow-normalize's +1
  (doseq [k [["world" "food/price_index"] ["world" "commodities/copper"]
             ["world" "commodities/aluminium"] ["world" "commodities/nickel"]]]
    (is (<= 2 (nm/real-lag k)) k))
  (is (nil? (nm/real-lag ["world" "finance/vix"])) "daily quotes: read in their month"))

(deftest bnm-a1e-revised-series-are-not-read-without-a-vintage
  ;; the GSCPI (first published January 2022, re-estimated each release),
  ;; the OECD CLI (amplitude-adjusted and revised ex post), and the stress
  ;; and conditions indexes (factor weights re-estimated weekly)
  (doseq [v ["trade/supply_pressure" "economy/leading_indicator" "finance/stress" "finance/conditions"]]
    (is (contains? nm/not-real-time v) v)
    (is (not (contains? nm/vars v)) v)
    (let [path (tsv-file [["world" v "2020-01-31" "2020-02-29" 1.0]])]
      (is (thrown-with-msg? Exception #"not real time" (nm/load-series path [{:name v :transform "id"}] {:vars #{v}})) v)
      (is (some? (nm/load-series path [{:name v :transform "id"}] {:vars #{v} :vintage {v "2026-10-16 fetch, backcast before 2022"}})) v))))

;; bnm-5rn: the second food set (docs/LEADING.md "The second food set"):
;; the FAO indices and the Pink Sheet fertilizer index, each read a month
;; after its month

(deftest bnm-5rn-food2-series-are-read-a-month-after-their-month
  (let [fao (java.io.File/createTempFile "bnm-fao" ".csv")
        fast (java.io.File/createTempFile "bnm-fast" ".tsv")
        months (range (* 12 2018) (+ 6 (* 12 2020)))
        lvl (fn [t] (if (< t (* 12 2020)) 100.0 200.0))]
    (spit fao (apply str "﻿FAO Food Price Index,,,\n2014-2016=100,,,\nDate,Food Price Index,Meat,Dairy,Cereals,Oils,Sugar,,\n,,,,,,,\n"
                     (for [t months] (format "%04d-%02d,%.1f,1,1,%.1f,%.2f,1,,\n" (quot t 12) (inc (rem t 12)) (lvl t) (* 2 (lvl t)) (* 3 (lvl t))))))
    (spit fast (apply str (for [t months]
                            (str "world\tfast/fertiliser\tm\t" (ym t) "\t" (ym (inc t)) "\t" (Math/log (lvl t)) "\n"
                                 "world\tfast/food\tm\t" (ym t) "\t" (ym (inc t)) "\t9\n"))))
    (let [db (nm/load-food2 (.getPath fao) (.getPath fast))
          jan (* 12 2020) feb (inc jan)
          [names f] (nm/leading db)
          at (fn [t] (zipmap names (f "XXX" t)))]
      (is (= #{["world" "fao/food"] ["world" "fao/cereals"] ["world" "fao/oils"] ["world" "pinksheet/fertiliser"]} (set (keys db))))
      (is (= (Math/log 400.0) (sk/value (db ["world" "fao/cereals"]) feb)) "January's, read at February")
      (is (= (Math/log 200.0) (sk/value (db ["world" "fao/cereals"]) jan)) "December's at January")
      (testing "the gauges"
        (is (= 0.0 (:fpi6 (at jan))) "December over June: no jump yet")
        (is (< (Math/abs (- (Math/log 2.0) (:fpi6 (at feb)))) 1e-9))
        (is (< (Math/abs (- (Math/log 2.0) (:cereals6 (at feb)))) 1e-9))
        (is (< (Math/abs (- (Math/log 2.0) (:oils6 (at feb)))) 1e-6))
        (is (= 0.0 (:fert12 (at jan))))
        (is (< (Math/abs (- (Math/log 2.0) (:fert12 (at feb)))) 1e-9)))
      (testing "without the set loaded, the gauges are nil and t65's unchanged"
        (let [[n0 f0] (nm/leading {})]
          (is (= names n0))
          (is (every? nil? (f0 "XXX" feb))))))))
