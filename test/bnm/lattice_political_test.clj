(ns bnm.lattice-political-test
  (:require [bnm.lattice-political :as lp]
            [bnm.named :as nm]
            [bnm.releases]
            [bnm.stocks :as sk]
            [clojure.test :refer [deftest is testing]]))

(deftest bnm-h8d-the-lattice-reads-the-penn-world-table-at-its-publication-lag
  ;; the quarters' political economy (PWT profit rate, labour share) went
  ;; missing at every September origin under the default 30 months'
  ;; staleness; with :pwt-stale the lattice reads bnm.named/publication-stale
  ;; (36), as bnm.onsets does. Off, the base of record's reading
  (let [pwt (sk/series (for [y (range 2000 2024)] [(+ 11 (* 12 y)) (+ 11 (* 12 (+ y 2))) 0.1]))
        db {["X" "economy/profit_rate"] pwt ["X" "economy/labor_share"] pwt}
        sep (+ 8 (* 12 2024))
        [_ base] (lp/political-economy-of db ["X"] [sep] {})
        [_ fixed] (lp/political-economy-of db ["X"] [sep] {:pwt-stale true})]
    (is (nil? (first (base "X" sep))))
    (is (= 0.1 (first (fixed "X" sep))))))

(deftest bnm-pjg-6-the-delay-test-reads-the-year-row-a-year-later
  ;; the year gauges' annual rows are current revised downloads with an
  ;; assumed one-year lag; the delay test reads them `delay` years later
  (let [dec2010 (+ 11 (* 12 2010))]
    (is (= 2009 (lp/year-row-year dec2010 0)) "the base: the year before the one ended")
    (is (= 2008 (lp/year-row-year dec2010 1)))))

(deftest bnm-56c-23-leak-fixes-read-the-year-row-by-release
  ;; --leak-fixes passes the release table and the origin's year to the
  ;; year row; off, the base's reading (no options)
  (let [dec2014 (+ 11 (* 12 2014))]
    (is (= {} (lp/year-row-opts nil dec2014 nil)))
    (let [o (lp/year-row-opts true dec2014 nil)]
      (is (= 2014 (:origin-year o)))
      (is (= 2010 (bnm.releases/published (:releases o) "maddison" dec2014))))
    (is (= 2014 (:origin-year (lp/year-row-opts true dec2014 1))) "the delay test's origin is still the origin")))

(deftest bnm-56c-23-leak-fixes-carry-urban-growth-in-one-unit
  (is (true? (:urban-log (lp/year-row-opts true (+ 11 (* 12 2014)) nil))))
  (is (nil? (:urban-log (lp/year-row-opts nil (+ 11 (* 12 2014)) nil)))))

(deftest bnm-x6o-each-slow-series-is-read-at-its-own-publication-lag
  ;; WDI's tertiary enrolment comes out 21 months after its year, so at a
  ;; December origin its newest value is 24 months old and {:stale 18}
  ;; refused it at every country and origin: :tertiary5 and the protest and
  ;; violence tensions on it were dead (docs/LEADING.md 4.1). With
  ;; :lag-stale each series is read at its own lag (bnm.named/lag-stale);
  ;; off, the base of record's 18
  (let [annual (fn [lag] (sk/series (for [y (range 1990 2024)] [(+ 11 (* 12 y)) (+ 11 (* 12 y) lag) (double y)])))
        db {["X" "people/tertiary_enrolment"] (annual 21) ["X" "people/infant_mortality"] (annual 9)}
        base (lp/wdi-reader db {})
        fixed (lp/wdi-reader db {:lag-stale true})]
    (is (nil? (base "X" "people/tertiary_enrolment" 2010)) "the bug, kept off")
    (is (= 2008.0 (fixed "X" "people/tertiary_enrolment" 2010)) "the year published by December 2010")
    (is (= 2009.0 (base "X" "people/infant_mortality" 2010) (fixed "X" "people/infant_mortality" 2010)) "nine months' lag: unchanged")
    (is (nil? (fixed "X" "people/tertiary_enrolment" 2030)) "a series that stopped still goes stale"))
  (testing "the Penn World Table between editions"
    ;; editions years apart: in 2023-24 the newest year, 2019, was four and
    ;; five years old, past 36 months, and the political economy went missing
    (let [pwt (sk/series (for [y (range 2000 2020)] [(+ 11 (* 12 y)) (+ 0 (* 12 2021)) 0.1]))
          db {["X" "economy/profit_rate"] pwt ["X" "economy/labor_share"] pwt}
          dec24 (+ 11 (* 12 2024))
          [_ pwt36] (lp/political-economy-of db ["X"] [dec24] {:pwt-stale true})
          [_ fixed] (lp/political-economy-of db ["X"] [dec24] {:pwt-stale true :lag-stale true})]
      (is (nil? (first (pwt36 "X" dec24))))
      (is (= 0.1 (first (fixed "X" dec24)))))))

(deftest bnm-56c-36-leading-gauges-are-read-at-their-availability-date
  ;; the leading gauges (docs/LEADING.md "Priors") read each value only once
  ;; it is published: a December value out in January is not read at the
  ;; December origin, which reads November's against the November before
  (let [m (fn [y mo] (+ (dec mo) (* 12 y)))
        monthly (fn [lag f] (sk/series (for [y (range 2000 2010) mo (range 1 13)] [(m y mo) (+ (m y mo) lag) (f y mo)])))
        db {["USA" "finance/spread_baa"] (monthly 1 (fn [y mo] (+ y (/ mo 100.0))))
            ["USA" "finance/curve_10y3m"] (monthly 0 (fn [y mo] (- mo)))
            ["world" "finance/vix"] (monthly 0 (fn [y _] (* 0.1 y)))
            ["world" "food/price_index"] (monthly 1 (fn [y _] (* 0.2 y)))
            ["X" "food/imports_share"] (sk/series [[(m 2005 12) (m 2006 9) 10.0]])}
        [names f] (nm/leading db)
        dec07 (m 2007 12)
        v (zipmap names (f "X" dec07))]
    (is (< (Math/abs (- 1.0 (:baa12 v))) 1e-9) "Nov 2007 less Nov 2006, not December's")
    (is (= -12.0 (:us-curve v)) "December's curve, published that month")
    (is (< (Math/abs (- 0.1 (:vix12 v))) 1e-9))
    (is (< (Math/abs (- 0.2 (:world-food12 v))) 1e-9))
    (is (< (Math/abs (- 2.0 (:food12-x-imports v))) 1e-9))
    (testing "nothing published after the origin, and a stopped quote series goes stale"
      (is (nil? (:baa12 (zipmap names (f "X" (m 2000 6))))) "no year of history yet")
      (is (nil? (:us-curve (zipmap names (f "X" (m 2010 6)))))))))

(deftest bnm-56c-36-leading-gauges-and-priors-by-kind
  ;; which kind reads which gauge where, and the priors fixed from theory
  ;; before any run (docs/LEADING.md "Priors")
  (is (= {:quarter-lead [:baa12 :vix12] :year [:baa12 :us-curve :vix12]} (lp/leading-sets "market_crash")))
  (is (= [:baa12 :us-curve :vix12] (get-in lp/leading-sets ["growth_collapse" :quarter-lead])))
  (is (= [:world-food12 :food12-x-imports] (get-in lp/leading-sets ["food_price_shock" :year])))
  (is (nil? (lp/leading-sets "coup")))
  (is (= {:baa12 [0.5 0.0625] :us-curve [-0.4 0.0625] :vix12 [0.25 0.0625]} (lp/leading-priors "market_crash")))
  (is (= {:baa12 [0.4 0.0625] :us-curve [-0.3 0.0625] :vix12 [0.3 0.0625]} (lp/leading-priors "growth_collapse")))
  (is (= {:food12-x-imports [0.5 0.0625] :world-food12 [0.3 0.0625]} (lp/leading-priors "food_price_shock")))
  (testing "the priors at a scale: those of the names it reads"
    (is (= {:us-curve [-0.4 0.0625] :baa12 [0.5 0.0625] :vix12 [0.25 0.0625]}
           (lp/named-prior-at "market_crash" [:credit3 :us-curve :baa12 :vix12])))
    (is (= {:world-food12 [0.3 0.0625] :food12-x-imports [0.5 0.0625]}
           (lp/named-prior-at "food_price_shock" [:food-inflation :world-food12 :food12-x-imports])))
    (is (nil? (lp/named-prior-at "coup" [:world-food12])))))

(deftest bnm-5rn-the-second-food-set-and-its-priors
  ;; behind --leading-food2 (docs/LEADING.md "The second food set"): the
  ;; food year node reads it beside t65's two gauges, the quarter-lead as a
  ;; group of its own; every other kind, and every kind without it, as t65
  (let [s2 (lp/leading-sets-for true)]
    (is (= lp/leading-sets (lp/leading-sets-for false)))
    (is (= [:world-food12 :food12-x-imports :fert12 :fpi6 :cereals6 :oils6] (get-in s2 ["food_price_shock" :year])))
    (is (= [:fert12 :fpi6 :cereals6 :oils6] (get-in s2 ["food_price_shock" :quarter-lead])))
    (is (= (lp/leading-sets "market_crash") (s2 "market_crash")))
    (is (= {:fert12 [0.1 0.0625] :fpi6 [0.15 0.0625] :cereals6 [0.15 0.0625] :oils6 [0.1 0.0625]} lp/food2-priors))
    (is (= {:world-food12 [0.3 0.0625] :fert12 [0.1 0.0625] :oils6 [0.1 0.0625]}
           (lp/named-prior-at "food_price_shock" [:food-inflation :world-food12 :fert12 :oils6] true)))
    (is (= {:world-food12 [0.3 0.0625]} (lp/named-prior-at "food_price_shock" [:world-food12 :fert12] false)))
    (is (nil? (lp/named-prior-at "coup" [:fert12] true)))))

(deftest bnm-11y-leading-gauges-standardized-against-their-own-history
  ;; the node's standardizer counts rows, so a world gauge (one value per
  ;; origin, repeated at every country) was standardized after two or three
  ;; origins: at the food year node's Dec 2006 origin world food12 stood at
  ;; z +4.9 against 2003-05 alone. Under :lead-std :history the leading
  ;; columns are standardized against the gauge's own values at every
  ;; quarter end since 1990, as known then.
  (let [m (fn [y mo] (+ (dec mo) (* 12 y)))
        ;; a world gauge alternating +-1 from 1990, then 2.0 from 2006
        g (fn [t] (if (>= t (m 2006 1)) 2.0 (if (even? (quot t 3)) 1.0 -1.0)))
        lead-f (fn [_ t] [(g t)])
        hist (lp/history-standardizer lead-f ["A" "B" "C"] [0] (m 1990 1) (m 2007 12))
        dec06 (m 2006 12)
        z (first (hist dec06 [(g dec06)]))]
    (is (< 1.5 z 2.5) (str "about 2 sd of its own history, not the cap: " z))
    (testing "nothing after the origin enters its spread"
      (is (= (hist (m 2005 12) [2.0]) (hist (m 2005 12) [2.0])))
      (is (< (Math/abs (- 2.0 (first (hist (m 2005 12) [2.0])))) 0.1)))
    (testing "the node's own standardizer sees three Decembers, all -1 here: no spread, z 0"
      (let [rows (for [y [2003 2004 2005 2006] u ["A" "B" "C" "D" "E" "F" "G" "H"]] [(m y 12) [(g (m y 12))]])
            node (#'bnm.lattice-banking/standardizer rows :stable)]
        (is (= 0.0 (first (node dec06 [(g dec06)]))))))))

(deftest bnm-11y-the-history-counts-quarter-ends-not-rows-and-starts-with-the-data
  ;; a gauge's spread is read from at least 20 quarter ends of its own
  ;; history (rows over many countries are one reading per quarter), and
  ;; from the first month the node could read it, not a fixed 1990
  (let [m (fn [y mo] (+ (dec mo) (* 12 y)))
        g (fn [t] (when (>= t (m 1983 1)) (if (even? (quot t 3)) 1.0 -1.0)))
        lead-f (fn [_ t] [(g t)])
        units (map str (range 150))
        hist (lp/history-standardizer lead-f units [0] (m 1982 1) (m 1995 12))]
    (is (= [0.0] (hist (m 1985 12) [2.0])) "12 quarter ends of history: none read yet")
    (is (< 1.5 (first (hist (m 1988 12) [2.0])) 2.5) "from 1983, 23 quarter ends by 1988")))

