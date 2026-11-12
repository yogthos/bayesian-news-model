(ns bnm.lattice-banking-test
  (:require [bnm.lattice-banking :as lb]
            [bnm.releases]
            [clojure.test :refer [deftest is]]))

(deftest one-crisis-dated-twice-counts-once
  ;; Reinhart-Rogoff and the Macrohistory panel date some crises a year apart
  (is (= #{1890 1907 1931} (lb/join-years #{1890 1907} #{1891 1931})))
  (is (= #{1890 1892} (lb/join-years #{1890} #{1892})) "two years apart: two crises"))

(deftest a-flat-past-does-not-explode
  ;; imputed series held flat for years: the stretch is floored and capped
  (let [ys (merge (zipmap (range 1990 2000) (repeat 0.40)) {2000 0.41})]
    (is (<= (#'lb/stretch-at ys 2000) 5.0))
    (is (< 0.0 (#'lb/stretch-at ys 2000) 2.0) "a one-point rise on a 40% share is modest"))
  (let [ys (into {} (for [y (range 1990 2001)] [y (if (even? y) 1.0 3.0)]))]
    (is (< (Math/abs (- (#'lb/stretch-at ys 2000) (/ (- 1.0 2.0) (Math/sqrt (/ 10.0 9.0))))) 1e-9)
        "a moving series is untouched")))

(deftest counts-need-their-window-covered
  ;; no data is not no crashes
  (let [ys (merge (zipmap (range 2000 2010) (repeat 0.0)) {2008 1.0})]
    (is (= 1.0 (#'lb/count10 ys 2009)))
    (is (nil? (#'lb/count10 ys 2015)) "six of ten years missing: unknown")
    (is (nil? (#'lb/count10 {} 2009)))))

(deftest currency-crises-by-laeven-and-valencia-s-rule
  ;; a depreciation against the dollar of 30% or more, at least 10 points
  ;; above the year before's; the first year of each five-year window
  (let [fx {2000 1.0 2001 1.05 2002 1.5 2003 2.1 2004 2.2 2005 2.3 2006 2.4 2007 2.5 2008 3.5 2009 3.6}]
    (is (= #{2002 2008} (lb/currency-crises fx)) "2003 (+40%) is not 10 points above 2002 (+43%); 2008 is six years on"))
  (is (= #{2003} (lb/currency-crises {2001 1.0 2002 1.25 2003 1.7})) "+36%, 11 points above +25%")
  (is (= #{} (lb/currency-crises {2001 1.0 2003 2.0})) "no year before: unknown, not a crisis"))

(deftest bnm-56c-23-banking-reads-the-penn-world-table-by-its-releases
  ;; row y is read at December of y + 1 and read PWT at y - 1; the latest
  ;; published at Dec 2015 was 2011 (PWT 8.0/8.1), and labour's share, the
  ;; capital stock and TFP did not exist before 8.0 (July 2013)
  (let [rs (bnm.releases/read-releases)
        ys (into {} (for [y (range 1990 2020)] [y (+ 0.5 (* 0.001 (- y 1990) (- y 1990)))]))
        pwt {"labsh" {"AAA" ys} "csh_i" {"AAA" ys} "rtfpna" {"AAA" ys} "rkna" {"AAA" ys} "rgdpna" {"AAA" ys}}
        ix (zipmap lb/extended-names (range))
        base (lb/extended-table [] pwt {} #{"AAA"} {})
        fixed (lb/extended-table [] pwt {} #{"AAA"} {} {:releases rs})
        at (fn [tb y k] (nth (tb ["AAA" y]) (ix k)))
        late (lb/extended-table [] (update-vals pwt (fn [m] (update-vals m (fn [ys] (into {} (for [[y x] ys] [(+ y 2) x])))))) {} #{"AAA"} {})]
    (is (some? (at base 2014 :labour-share)))
    (is (= (at late 2014 :labour-share) (at fixed 2014 :labour-share)) "2011's value, two years staler")
    (is (= (at late 2014 :investment-share) (at fixed 2014 :investment-share)))
    (is (= (at late 2014 :tfp3) (at fixed 2014 :tfp3)))
    (is (some? (at base 2010 :labour-share)))
    (is (nil? (at fixed 2010 :labour-share)) "Dec 2011: no labour share published yet")
    (is (nil? (at fixed 2010 :tfp3)))
    (is (some? (at fixed 2010 :investment-share)) "the investment share was")
    (is (= (at base 2014 :credit-wide) (at fixed 2014 :credit-wide)) "other columns untouched")))
