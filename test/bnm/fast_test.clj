(ns bnm.fast-test
  (:require [bnm.fast :as f]
            [bnm.lattice :as lt]
            [bnm.stocks :as sk]
            [clojure.test :refer [deftest is testing]]))

(defn- monthly
  "A series of `xs` from month `from`, each available `lag` months after
  its period."
  [from xs lag]
  (sk/series (map-indexed (fn [i x] [(+ from i) (+ from i lag) (double x)]) xs)))

(defn- close? [a b] (and a b (< (Math/abs (- (double a) (double b))) 1e-9)))

(deftest only-what-is-published-by-the-origin
  ;; a value published a month after its period is not read at its own
  ;; month's end: at t the latest is t-1's
  (let [s (monthly 0 (range 40) 1)]
    (is (close? 3.0 (f/change s 30 3)) "29 less 26")
    (is (= 29.0 (sk/value s 30)))
    (is (nil? (f/change s 0 3)) "nothing published yet")
    (testing "stale: nothing when the latest is more than six months old"
      (is (some? (f/change s 45 3)) "39's value, six months old")
      (is (nil? (f/change s 46 3))))))

(deftest a-surprise-is-a-break-from-trend
  (let [line (monthly 0 (map #(* 0.5 %) (range 40)) 0)
        jump (monthly 0 (concat (map #(* 0.5 %) (range 39)) [25.0]) 0)]
    (is (close? 0.0 (f/surprise line 39 24)) "on the trend: no surprise")
    (is (close? (- 25.0 19.5) (f/surprise jump 39 24)) "the jump above the line carried to its month")
    (testing "the trend uses only the w months before the latest"
      (is (close? (- 25.0 19.5) (f/surprise jump 39 4))))
    (testing "too few months before it: nothing"
      (is (nil? (f/surprise (monthly 0 [1 2 3] 0) 2 24))))
    (testing "what is not yet published does not move it"
      (let [late (sk/series (concat (map (fn [i] [i i (* 0.5 i)]) (range 39)) [[39 41 25.0]]))]
        (is (close? 0.0 (f/surprise late 40 24)) "at 40 the latest known is 38's, on its line")))))

(deftest volatility-of-monthly-changes
  (let [s (monthly 0 (map #(if (even? %) 0.0 1.0) (range 30)) 0)]
    ;; the twelve months to the latest hold eleven changes, six of one sign
    (is (close? (Math/sqrt (- 1.0 (/ 1.0 121))) (f/volatility s 29)) "changes +1, -1")
    (is (close? 0.0 (f/volatility (monthly 0 (range 30) 0) 29)))))

(deftest a-seasonal-change-against-a-year-before
  (let [s (monthly 0 (for [i (range 30)] (+ (if (< (mod i 12) 3) 5.0 0.0) (* 0.1 i))) 0)]
    ;; months 24-26 against 12-14: the same season, 1.2 of trend apart
    (is (close? 1.2 (f/year-on-year s 26)))
    (is (nil? (f/year-on-year (monthly 0 (range 10) 0) 9)) "no year before")))

(deftest commodities-land-through-exposure
  (let [food (monthly 0 (map #(* 0.01 %) (range 60)) 0)
        fast {["world" "fast/food"] food}
        exposure (fn [c _] ({"AAA" {:food 0.2 :energy -0.1 :fertiliser 0.3} "BBB" {}} c))
        inputs (f/family-inputs :commodities fast exposure)
        a (inputs "AAA" 50) b (inputs "BBB" 50)]
    (is (= 9 (count a) (count (:commodities f/families))))
    (is (close? (* 0.2 0.03) (first a)) "food's three-month change times food imports")
    (is (close? 0.0 (second a)) "on its trend")
    (is (nil? (nth a 3)) "no energy prices: unknown")
    (is (every? nil? b) "no exposure: unknown, not zero")))

(deftest fx-and-spreads-per-country
  (let [fast {["AAA" "fast/fx"] (monthly 0 (range 40) 1) ["AAA" "fast/spread"] (monthly 0 (repeat 40 2.0) 1)}
        xs ((f/family-inputs :fx fast (constantly {})) "AAA" 30)]
    (is (= 6 (count xs)))
    (is (close? 3.0 (first xs)))
    (is (close? 0.0 (nth xs 3)) "a flat spread")
    (is (every? nil? ((f/family-inputs :fx fast (constantly {})) "BBB" 30)))))

(deftest ports-read-their-own-series
  (let [fast {["AAA" "fast/portcalls"] (monthly 0 (map #(* 0.1 %) (range 30)) 1)
              ["world" "fast/chokepoints"] (monthly 0 (repeat 30 5.0) 1)}
        xs ((f/family-inputs :ports fast (constantly {:open 0.5})) "AAA" 29)]
    (is (= 4 (count xs)))
    (is (close? 1.2 (first xs)))
    (is (close? 0.0 (peek xs)) "flat transits")))

(def ^:private tree {"AAA" "Reg" "BBB" "Reg" "CCC" "Reg2" "Reg" "world" "Reg2" "world"})

(def ^:private sp
  {:scales [{:name :quarter-lead} {:name :quarter-w2} {:name :year}]
   :parent tree
   :without-at {:quarter-lead #{:up :relations} :quarter-w2 #{:relations}}})

(deftest a-nested-group-at-the-quarters-only
  (let [inputs (fn [c t] ({"AAA" [1.0 nil] "BBB" [3.0 nil] "CCC" [5.0 2.0]} c))
        s (f/with-fast sp inputs 2 :fast/x identity)
        rel (:relations s)]
    (is (= {:quarter-lead #{:up} :quarter-w2 #{}} (:without-at s)) "the quarters read relations, nothing else changes")
    (is (= {:quarter-lead 2 :quarter-w2 2 :year 0} (:relation-width s)))
    (is (= {:quarter-lead {:fast/x [0 1]} :quarter-w2 {:fast/x [0 1]}} (:relation-groups s)))
    (is (:relation-nest s))
    (is (= [1.0 nil] (:x (rel :quarter-lead "AAA" 5))))
    (is (= [] (:x (rel :year "AAA" 11))) "the year reads nothing new")
    (is (= [2.0 nil] (:x (rel :quarter-w2 "Reg" 5))) "a region: its countries' mean over those known")
    (is (= [3.0 2.0] (:x (rel :quarter-lead "world" 5))))
    (testing "the layout: one nested group at the quarter, none at the year"
      (let [q (lt/layout-of (assoc s :gauge-width {:quarter-lead 3}) :quarter-lead 0)
            y (lt/layout-of s :year 0)]
        (is (contains? (:groups q) :fast/x))
        (is (= #{:fast/x} (:nested q)))
        (is (not (contains? (:groups y) :fast/x)))))))

(deftest the-placebo-reads-another-country
  (let [inputs (fn [c _] ({"AAA" [1.0] "BBB" [3.0] "CCC" [5.0]} c))
        src {"AAA" "BBB" "BBB" "CCC" "CCC" "AAA"}
        s (f/with-fast sp inputs 1 :fast/x #(get src % %))]
    (is (= [3.0] (:x ((:relations s) :quarter-lead "AAA" 5))))
    (is (= [4.0] (:x ((:relations s) :quarter-lead "Reg" 5))) "a region through its members' sources")))

(deftest no-spec-with-relations
  (is (thrown? clojure.lang.ExceptionInfo (f/with-fast (assoc sp :relations identity) (constantly []) 0 :fast/x identity))))
