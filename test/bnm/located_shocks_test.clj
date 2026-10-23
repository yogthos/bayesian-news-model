(ns bnm.located-shocks-test
  (:require [bnm.cells :as cells]
            [bnm.located-shocks :as ls]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(def shocks
  [{:shock "eq-a" :hazard "earthquake" :year 2000 :cell "c1" :coverage 0.9}
   {:shock "eq-a" :hazard "earthquake" :year 2000 :cell "c2" :coverage 0.3}
   {:shock "fl-b" :hazard "flood" :year 2000 :cell "c3" :coverage 0.5}
   {:shock "tc-c" :hazard "cyclone" :year 2001 :cell "c4" :coverage 1.0}])

(deftest hit-cells-by-year
  (is (= {2000 #{"c1" "c3"} 2001 #{"c4"}} (ls/hit-cells shocks {})) "coverage 0.5 or more")
  (is (= {2000 #{"c1"}} (ls/hit-cells shocks {:hazards #{"earthquake"}})))
  (is (= {1999 #{"c1" "c3"} 2000 #{"c4"}} (ls/hit-cells shocks {:shift -1})) "the time placebo dates each a year early")
  (is (= {2000 #{"c1" "c2" "c3"} 2001 #{"c4"}} (ls/hit-cells shocks {:min-coverage 0.2}))))

(def land {"AAA@c1" 100.0 "AAA@c2" 300.0 "BBB@c3" 50.0 "AAA@c3" 50.0 "BBB@c4" 200.0})

(deftest hits-go-to-every-country-with-land-in-the-cell
  (let [idx (ls/land-index land)]
    (is (= {["AAA" 2000] #{"c1" "c3"} ["BBB" 2000] #{"c3"} ["BBB" 2001] #{"c4"}}
           (ls/country-hits idx {2000 #{"c1" "c3"} 2001 #{"c4"}})))
    (testing "country exposure is the hit share of the country's land"
      (is (close? (/ 150.0 450.0) (ls/land-share idx "AAA" #{"c1" "c3"})))
      (is (close? 0.2 (ls/land-share idx "BBB" #{"c3"})))
      (is (= 0.0 (ls/land-share idx "CCC" #{"c3"}))))))

(defn- plant [cell iso type cap from]
  {:cell cell :iso iso :asset_type type :commodity (if (= type "refinery") "oil" "cement") :unit (if (= type "refinery") "bbl/d" "Mt/y")
   :capacity cap :share 1.0 :known_from from :asset_id (str cell type cap from)})

(deftest cell-exposure-is-the-hit-share-of-known-capacity
  (let [rows [(plant "c1" "AAA" "cement_plant" 3.0 1990)
              (plant "c2" "AAA" "cement_plant" 1.0 1990)
              (plant "c3" "AAA" "cement_plant" nil 1990)  ; the world median: 2 Mt
              (plant "c1" "AAA" "cement_plant" 4.0 2005)  ; not known before 2005
              (plant "c9" "BBB" "cement_plant" 0.5 1990)
              (plant "c1" "AAA" "refinery" 100.0 1990)
              (plant "c2" "AAA" "refinery" 300.0 1990)]
        by-iso (ls/by-iso (ls/weigh rows))]
    (testing "missing capacity weighs as the world median, not the country's"
      (is (close? (/ 2.0 6.0) (get (ls/cell-exposure by-iso "AAA" 2000 #{"c3"}) "cement"))))
    (is (close? (/ 3.0 6.0) (get (ls/cell-exposure by-iso "AAA" 2000 #{"c1"}) "cement")))
    (is (close? 0.25 (get (ls/cell-exposure by-iso "AAA" 2000 #{"c1"}) "refinery")))
    (testing "assets present the year before the shock only"
      (is (close? (/ 3.0 6.0) (get (ls/cell-exposure by-iso "AAA" 2005 #{"c1"}) "cement")))
      (is (close? (/ 7.0 10.0) (get (ls/cell-exposure by-iso "AAA" 2006 #{"c1"}) "cement"))))
    (is (= 0.0 (get (ls/cell-exposure by-iso "AAA" 2000 #{"c7"}) "cement" 0.0)) "no plant in the hit cells")
    (is (= #{"cement" "refinery"} (ls/known-keys by-iso "AAA" 2000)))
    (is (= #{"cement"} (ls/known-keys by-iso "BBB" 2000)))
    (is (= #{} (ls/known-keys by-iso "BBB" 1990)) "known from 1990, so not the year before")))

(deftest backcast-reads-snapshots-into-the-past
  (let [[a b] (ls/backcast [{:known_from 2021 :start_year 1975 :year_flag "snapshot"}
                            {:known_from 2021 :start_year nil :year_flag "snapshot"}])]
    (is (= 1975 (:known_from a)) "from its start year")
    (is (= ls/backcast-from (:known_from b)) "always, when it has none")))

(deftest the-outcome-is-two-years-against-three
  (let [q {1997 100.0 1998 100.0 1999 100.0 2000 50.0 2001 150.0}]
    (is (close? 0.0 (ls/log-change q 2000)))
    (is (close? (Math/log 0.5) (ls/log-change (assoc q 2001 50.0) 2000)))
    (is (nil? (ls/log-change q 2001)) "2002 missing")
    (is (nil? (ls/log-change (assoc q 1998 0.0) 2000)) "a zero year has no log")
    (testing "clipped at plus or minus clip"
      (is (= -1.0 (ls/clip-outcome -3.1 1.0)))
      (is (= 0.4 (ls/clip-outcome 0.4 1.0)))
      (is (= 2.0 (ls/clip-outcome 2.0 nil)) "no clip"))))

(defn- units [f]
  (vec (for [yr (range 2000 2010) i (range 6)
             :let [cell (if (< i 2) (* 0.1 (inc i) (mod yr 3)) 0.0)
                   country (* 0.05 (mod (+ i yr) 4))]]
         {:key "k" :iso (str "C" i) :year yr :cell cell :country country :y (f cell country i yr)})))

(deftest leave-one-year-out-prefers-the-exposure-that-predicts
  (let [us (units (fn [cell _ _ _] (* -2.0 cell)))
        r (ls/compare-exposures us {:draws 200 :seed 1})]
    (is (< (:mse-cell r) 1e-12) "the cell exposure predicts it exactly")
    (is (> (:mse-country r) (:mse-cell r)))
    (is (neg? (:diff r)))
    (is (neg? (second (:interval r))) "the whole interval below zero")
    (is (= :cell (:verdict r)))
    (is (close? -2.0 (:slope-cell r)))
    (is (= 10 (count (:years r)))))
  (testing "key-year effects absorb a shock common to the key's year"
    (let [r (ls/compare-exposures (units (fn [cell _ _ yr] (+ (* -2.0 cell) (* 0.3 (- yr 2000))))) {:draws 50 :seed 1})]
      (is (< (:mse-cell r) 1e-12))))
  (testing "neither predicts: no verdict for cell"
    (let [r (ls/compare-exposures (units (fn [_ _ i yr] (* 0.1 (mod (* (inc i) (+ yr 7)) 5)))) {:draws 200 :seed 1})]
      (is (not= :cell (:verdict r))))))

(deftest unit-effects-absorb-a-country's-own-trend
  (let [us (units (fn [cell _ i _] (+ (* -2.0 cell) (* 0.2 i))))
        plain (ls/compare-exposures us {:draws 50 :seed 1})
        fe (ls/compare-exposures us {:draws 50 :seed 1 :unit-effects true})]
    (is (> (:mse-cell plain) 1e-4) "a country's own growth is noise to key-year effects alone")
    (is (< (:mse-cell fe) 1e-12))
    (is (close? -2.0 (:slope-cell fe)))))

(deftest the-bootstrap-is-fixed-by-its-seed
  (let [us (units (fn [cell country i _] (+ (* -1.0 cell) (* 0.5 country) (* 0.02 i))))]
    (is (= (:interval (ls/compare-exposures us {:draws 100 :seed 3}))
           (:interval (ls/compare-exposures us {:draws 100 :seed 3}))))
    (is (not= (:interval (ls/compare-exposures us {:draws 100 :seed 3}))
              (:interval (ls/compare-exposures us {:draws 100 :seed 4}))))))

(deftest exposed-country-years-count-each-country-year-once
  (is (= 2 (ls/exposed-country-years [{:key "a" :iso "X" :year 2000 :cell 0.2}
                                      {:key "b" :iso "X" :year 2000 :cell 0.06}
                                      {:key "a" :iso "Y" :year 2000 :cell 0.04}
                                      {:key "a" :iso "Y" :year 2001 :cell 0.05}]
                                     0.05))))

(deftest the-test-window-is-guarded
  (is (nil? (ls/check-window 1991 2009 false)) "outputs to 2010")
  (is (thrown? Exception (ls/check-window 1991 2010 false)) "2011's output is the test's")
  (is (nil? (ls/check-window 2011 2023 true))))

(deftest the-space-placebo-stays-on-the-country's-land
  (let [c0 (cells/cell 29.86 40.75)
        country (set (cells/disk c0 6))
        hit (set (cells/disk c0 1))
        moved (ls/move-footprint hit (vec (sort country)) 7)]
    (is (seq moved))
    (is (every? country moved) "never off the country's land")
    (is (<= (count moved) (count hit)))
    (is (= moved (ls/move-footprint hit (vec (sort country)) 7)) "the seed fixes it")
    (is (not= moved (ls/move-footprint hit (vec (sort country)) 8)))))
