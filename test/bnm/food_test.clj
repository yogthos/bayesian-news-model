(ns bnm.food-test
  (:require [bnm.food :as food]
            [bnm.stockflow :as sf]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (and a b (< (Math/abs (- (double a) (double b))) tol)))

(defn- value [rows iso year variable]
  (some #(when (and (= iso (:iso %)) (= year (:year %)) (= variable (:variable %))) (:value %)) rows))

;; ---------------------------------------------------------------- causality

(deftest values-are-known-a-year-after-their-data-year
  (is (= 1 food/lag))
  (is (= 2008 (food/known-at 2007)) "FAOSTAT's year y-1 is known by the end of y"))

;; ---------------------------------------------------------------- trade

(def trade-rows
  [{:importer "AAA" :exporter "XXX" :year 2000 :item "wheat" :tonnes 10.0 :source "reported"}
   {:importer "AAA" :exporter "YYY" :year 2000 :item "wheat" :tonnes 5.0 :source "mirror_pair"}
   {:importer "BBB" :exporter "XXX" :year 2000 :item "wheat" :tonnes 7.0 :source "mirror"}
   {:importer "CCC" :exporter "XXX" :year 2000 :item "wheat" :tonnes 3.0 :source "mirror"}])

(def coverage
  {["AAA" 2000 "wheat"] "reported" ["BBB" 2000 "wheat"] "mirror"
   ["CCC" 2000 "wheat"] "none" ["DDD" 2000 "wheat"] "reported"
   ["XXX" 2000 "wheat"] "reported" ["YYY" 2000 "wheat"] "none"})

(deftest imports-with-mirror-fill
  (let [m (food/imports-of trade-rows coverage {})]
    (testing "an importer's own report, plus the pairs it left out that exporters report"
      (is (= 15.0 (get-in m [["AAA" 2000 "wheat"] :total])))
      (is (= {"XXX" 10.0 "YYY" 5.0} (get-in m [["AAA" 2000 "wheat"] :by]))))
    (testing "an importer that did not report: its exporters' figures"
      (is (= 7.0 (get-in m [["BBB" 2000 "wheat"] :total]))))
    (testing "where coverage says none the flow is unknown, not zero"
      (is (nil? (get m ["CCC" 2000 "wheat"]))))
    (testing "a reporter with no partners imported nothing"
      (is (= 0.0 (get-in m [["DDD" 2000 "wheat"] :total]))))
    (testing "a country absent from coverage is unknown"
      (is (nil? (get m ["EEE" 2000 "wheat"])))))
  (testing "without the pairs, an importer's own total stands"
    (is (= 10.0 (get-in (food/imports-of trade-rows coverage {:pairs false}) [["AAA" 2000 "wheat"] :total])))))

(deftest exports-as-partners-report-them
  (let [e (food/exports-of trade-rows coverage {})]
    ;; XXX's sales to AAA and BBB; CCC's are unknown (coverage none) and not counted
    (is (= 17.0 (get e ["XXX" 2000 "wheat"])))
    (is (= 5.0 (get e ["YYY" 2000 "wheat"])) "seen through a mirror pair")
    (is (= 0.0 (get e ["DDD" 2000 "wheat"])) "a covered country nobody bought from")))

(deftest herfindahl-of-suppliers
  (is (close? 0.5 (food/hhi {"X" 50.0 "Y" 50.0}) 1e-12))
  (is (= 1.0 (food/hhi {"X" 100.0})))
  (is (close? (/ 1.0 3) (food/hhi {"X" 1.0 "Y" 1.0 "Z" 1.0}) 1e-12))
  (is (close? (+ 0.64 (* 4 0.0025)) (food/hhi {"A" 80.0 "B" 5.0 "C" 5.0 "D" 5.0 "E" 5.0}) 1e-12))
  (is (nil? (food/hhi {})) "no imports: no concentration")
  (is (nil? (food/hhi {"X" 0.0}))))

(deftest import-dependence-is-imports-over-supply
  ;; FAO's import dependency ratio: imports / (production + imports - exports)
  (is (close? (/ 10.0 18.0) (food/import-dependence 8.0 10.0 0.0) 1e-12))
  (is (= 0.0 (food/import-dependence 50.0 0.0 20.0)))
  (is (nil? (food/import-dependence 1.0 1.0 5.0)) "no supply left: undefined")
  (is (nil? (food/import-dependence nil 1.0 0.0))))

(deftest production-for-dependence
  (let [imports {["AAA" 2000 "wheat"] {:total 10.0 :by {"XXX" 10.0}}
                 ["BBB" 2000 "wheat"] {:total 10.0 :by {"XXX" 10.0}}
                 ["CCC" 2000 "wheat"] {:total 10.0 :by {"XXX" 10.0}}}
        exports {["AAA" 2000 "wheat"] 0.0 ["BBB" 2000 "wheat"] 0.0 ["CCC" 2000 "wheat"] 0.0}
        rows (food/food-rows {:fbs {["AAA" 2000 "wheat"] {:imports 10.0 :use 10.0}}
                              :production {["BBB" 2000 "wheat"] 30.0 ["AAA" 2000 "wheat"] 99.0}
                              :imports imports :exports exports}
                             {})]
    (testing "a balance sheet without production: none grown"
      (is (= 1.0 (value rows "AAA" 2001 "wheat_import_dependence")))
      (is (= 0.0 (value rows "AAA" 2001 "wheat_production"))))
    (testing "no balance sheet: the crop statistics' production"
      (is (close? 0.25 (value rows "BBB" 2001 "wheat_import_dependence") 1e-12))
      (is (= 30.0 (value rows "BBB" 2001 "wheat_production")))
      (is (nil? (value rows "BBB" 2001 "wheat_use")) "but no balance"))
    (testing "neither: unknown"
      (is (nil? (value rows "CCC" 2001 "wheat_import_dependence")))
      (is (= 10.0 (value rows "CCC" 2001 "wheat_imports"))))))

;; ---------------------------------------------------------------- balance and stock

(deftest the-balance-closes
  (testing "stock change = production + imports - exports - use"
    (is (= 5.0 (food/balance {:production 100.0 :imports 20.0 :exports 30.0 :use 85.0})))
    (is (= -10.0 (food/balance {:production 50.0 :use 60.0})) "a missing flow is none"))
  (let [years {2000 {:production 100.0 :imports 10.0 :exports 5.0 :use 95.0}
               2001 {:production 80.0 :imports 10.0 :exports 5.0 :use 95.0}
               2002 {:production 120.0 :imports 0.0 :exports 10.0 :use 100.0}}
        r (food/stock-run years)]
    (testing "the grain stock as a bnm.stockflow model: its accounts close"
      (is (< (sf/imbalance r) 1e-9)))
    (testing "the level at t is the stock at the end of year t-1"
      (is (= {2000 0.0 2001 10.0 2002 0.0 2003 10.0} (food/stock-path years))))))

(deftest a-stock-is-its-level-above-the-lowest-known
  ;; the true stock is unknown up to a constant; it is at least the
  ;; cumulated builds less their lowest point so far
  (is (= {2000 0.0 2001 10.0 2002 0.0 2003 0.0 2004 5.0}
         (food/anchored (sorted-map 2000 0.0 2001 10.0 2002 -5.0 2003 -20.0 2004 -15.0)))))

(def synthetic
  ;; one country, steady harvests of 100 against use of 90, a drought in 2003
  (into {} (for [y (range 1995 2011)]
             [["AAA" y "wheat"] {:production (if (= y 2003) 40.0 100.0) :imports 0.0 :exports 0.0 :use 90.0}])))

(deftest synthetic-stock-behaviour
  (let [rows (food/food-rows {:fbs synthetic :imports {} :exports {}} {})
        su (fn [y] (value rows "AAA" y "wheat_stocks_to_use"))
        world (fn [y] (value rows food/world y "wheat_stocks_to_use"))
        pressure (fn [y] (value rows food/world y "wheat_storage_pressure"))]
    (testing "the stock builds 10 a year, so stocks-to-use rises"
      (is (close? (/ 10.0 90.0) (su 1996) 1e-12) "known in 1996: the end of 1995")
      (is (close? (/ 80.0 90.0) (su 2003) 1e-12)))
    (testing "the drought draws it down by 50, seen a year later"
      (is (close? (/ 30.0 90.0) (su 2004) 1e-12))
      (is (< (su 2004) (su 2003))))
    (testing "the world is the sum of its countries"
      (is (close? (su 2004) (world 2004) 1e-12)))
    (testing "price-spike pressure rises as stocks-to-use falls"
      (is (> (pressure 2004) (pressure 2003)))
      (is (> (pressure 2004) (pressure 2005)) "and eases as stocks rebuild"))
    (testing "stock change and use are as the balance says"
      (is (= -50.0 (value rows "AAA" 2004 "wheat_stock_change")))
      (is (= 90.0 (value rows "AAA" 2004 "wheat_use"))))))

(deftest storage-pressure-is-convex-in-stocks-to-use
  (let [f #(food/storage-pressure % 0.2 0.25)]
    (is (close? 1.0 (f 0.2) 1e-12) "one at the reference")
    (is (> (f 0.1) (f 0.2) (f 0.3)) "falling in stocks")
    (is (> (- (f 0.05) (f 0.1)) (- (f 0.1) (f 0.15)) (- (f 0.15) (f 0.2))) "convex: steeper near the floor")
    (is (close? 5.0 (f 0.0) 1e-12) "finite at the floor: (1 + c) / c")))

;; ---------------------------------------------------------------- causality, end to end

(defn- pseudo
  "A deterministic jumble in [0, 1) for synthetic data."
  [& xs] (let [h (hash (vec xs))] (/ (double (mod h 1000)) 1000.0)))

(def jumbled-fbs
  (into {} (for [iso ["AAA" "BBB"] y (range 1990 2006) item food/items]
             [[iso y item] {:production (* 100.0 (+ 0.5 (pseudo iso y item :p)))
                            :imports (* 30.0 (pseudo iso y item :i))
                            :exports (* 20.0 (pseudo iso y item :e))
                            :use (* 110.0 (+ 0.5 (pseudo iso y item :u)))}])))

(def jumbled-trade
  (vec (for [iso ["AAA" "BBB"] y (range 1990 2006) item food/items ex ["XXX" "YYY" "ZZZ"]]
         {:importer iso :exporter ex :year y :item item :tonnes (* 10.0 (pseudo iso y item ex)) :source "reported"})))

(def jumbled-coverage
  (into {} (for [iso ["AAA" "BBB" "XXX" "YYY" "ZZZ"] y (range 1990 2006) item food/items] [[iso y item] "reported"])))

(defn- before [y m] (into {} (filter (fn [[[_ yr _] _]] (< yr y)) m)))

(deftest a-value-at-y-uses-no-data-from-y-on
  (let [inputs (fn [y] {:fbs (before y jumbled-fbs)
                        :imports (before y (food/imports-of jumbled-trade jumbled-coverage {}))
                        :exports (before y (food/exports-of jumbled-trade jumbled-coverage {}))})
        full (food/food-rows (inputs 3000) {})
        at (fn [rows y] (set (filter #(<= (:year %) y) rows)))]
    (is (seq full))
    (is (= 1991 (apply min (map :year full))) "the first data year, 1990, is known in 1991")
    (doseq [y [1993 1998 2003]]
      (testing (str "rows known by " y " from the data before " y " alone")
        (is (= (at full y) (at (food/food-rows (inputs y) {}) y)))))))

;; ---------------------------------------------------------------- the derived file

(def derived "data/derived/stocks/food.csv")

(defn- read-derived []
  (with-open [r (io/reader derived)]
    (let [[header & lines] (line-seq r)]
      (is (= "iso,year,variable,value" header))
      (into {} (for [l lines :let [[iso y v x] (str/split l #",")]]
                 [[iso (parse-long y) v] (parse-double x)])))))

(deftest spot-checks-against-known-facts
  (if-not (.exists (io/file derived))
    (println "bnm.food-test: no" derived "- spot checks skipped (jolt -A:tools -M -m bnm.food-run)")
    (let [d (read-derived)
          g (fn [iso y v] (get d [iso y v]))]
      (testing "Egypt imports roughly half its wheat (FAO: 40-65% since the 1990s)"
        (doseq [y (range 2000 2023) :let [x (g "EGY" y "wheat_import_dependence")] :when x]
          (is (< 0.3 x 0.75) (str "EGY " y " " x))))
      (testing "the United States, Russia and Argentina import little wheat"
        (doseq [iso ["USA" "ARG"] y [2005 2015] :let [x (g iso y "wheat_import_dependence")] :when x]
          (is (< x 0.25) (str iso " " y " " x))))
      (testing "Japan buys almost all its maize, mostly from one supplier"
        (is (< 0.95 (g "JPN" 2008 "maize_import_dependence") 1.01))
        (is (> (g "JPN" 2008 "maize_supplier_hhi") 0.6) "the United States"))
      (testing "world wheat stocks-to-use: low after 2007 (the 2007-08 spike), higher after 2010"
        (is (< (g "WLD" 2008 "wheat_stocks_to_use") (g "WLD" 2011 "wheat_stocks_to_use")))
        (is (< (g "WLD" 2008 "wheat_stocks_to_use") (g "WLD" 2002 "wheat_stocks_to_use"))
            "drawn down from 2001 to 2007")
        (is (> (g "WLD" 2008 "wheat_storage_pressure") (g "WLD" 2011 "wheat_storage_pressure"))))
      (testing "every value finite, ratios in range"
        (is (empty? (for [[k x] d :when (or (Double/isNaN x) (Double/isInfinite x))] k)))
        (is (empty? (for [[[_ _ v :as k] x] d :when (and (str/ends-with? v "_hhi") (not (<= 0.0 x 1.0000001)))] k)))
        (is (empty? (for [[[_ _ v :as k] x] d :when (and (str/ends-with? v "_stocks_to_use") (neg? x))] k)))))))
