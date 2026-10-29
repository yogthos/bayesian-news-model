(ns bnm.watchlist-test
  (:require [augur.math :as m]
            [bnm.watchlist :as wl]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest top-k-shares-split-ties-at-the-cutoff
  (testing "no ties: the k highest get the slots"
    (is (= [1.0 0.0 1.0 0.0] (wl/top-k-shares [0.9 0.1 0.5 0.2] 2))))
  (testing "rows tied at the cutoff share what is left, whatever their order"
    ;; one slot above, three tied for the one left
    (let [s (wl/top-k-shares [0.9 0.3 0.3 0.3 0.1] 2)]
      (is (= 1.0 (first s)))
      (is (every? #(close? (/ 1.0 3) %) (subvec s 1 4)))
      (is (= 0.0 (peek s)))
      (is (close? 2.0 (reduce + s)) "exactly k slots")))
  (testing "a tie wholly inside the list is fully in it"
    (is (= [1.0 1.0 0.0] (wl/top-k-shares [0.5 0.5 0.1] 2))))
  (testing "k past the rows: every row is listed"
    (is (= [1.0 1.0] (wl/top-k-shares [0.2 0.1] 5)))))

(deftest k-for-fixed-and-share-of-countries
  (is (= 10 (wl/k-for 10 170)))
  (is (= 4 (wl/k-for 10 4)) "never more slots than rows")
  (is (= 17 (wl/k-for :p10 170)))
  (is (= 16 (wl/k-for :p10 151)) "10% rounded up"))

(deftest recall-and-precision-arithmetic
  ;; ten countries, onsets at ranks 1, 3 and 8
  (let [ps [0.9 0.8 0.7 0.6 0.5 0.4 0.3 0.2 0.1 0.05]
        ys [true false true false false false false true false false]
        {:keys [hits pos slots]} (wl/cell-hits ps ys 5)]
    (is (close? 2.0 hits))
    (is (= 3 pos))
    (is (= 5 slots))
    (let [cells {"a" {2003 {:hits 2.0 :pos 3 :slots 5} 2004 {:hits 1.0 :pos 1 :slots 5}}
                 "b" {2003 {:hits 0.0 :pos 2 :slots 5} 2004 {:hits 0.0 :pos 0 :slots 5}}}
          r (wl/pooled-rates cells)]
      (is (close? 0.75 (get-in r [:per-kind "a" :recall])) "3 of 4 onsets, pooled over years")
      (is (close? 0.3 (get-in r [:per-kind "a" :precision])) "3 hits in 10 slots")
      (is (close? 0.0 (get-in r [:per-kind "b" :recall])))
      (is (close? 0.375 (:recall r)) "kinds weighed equally")
      (is (close? 0.15 (:precision r)))))
  (testing "a kind-year without onsets adds slots, not recall"
    (is (= 0 (:pos (wl/cell-hits [0.5 0.4] [false false] 1))))))

(deftest weighted-stat-is-a-mean-over-kinds-of-ratios
  (let [cells {"a" {1 [1.0 2.0] 2 [3.0 2.0]} "b" {1 [0.0 4.0] 2 [2.0 0.0]}}]
    (is (close? 0.75 (wl/weighted-stat cells {1 1 2 1})) "a: 4/4, b: 2/4")
    (is (close? 1.5 (wl/weighted-stat cells {1 0 2 2}))
        "a year drawn twice counts twice; b, with nothing to divide by in the draw, drops out")))

(deftest the-bootstrap-resamples-blocks-of-years
  (let [g (m/rng 1)
        w (wl/year-weights (range 2003 2023) 2 g)]
    (is (= 20 (reduce + (vals w))) "as many years as there are")
    (is (every? #(= (w %) (w (inc %))) (range 2003 2023 2)) "a block's years drawn together"))
  (testing "identical models: a zero difference and a zero interval"
    (let [cells {"a" (into {} (for [y (range 2003 2023)] [y [0.0 3.0]]))}
          {:keys [diff lo hi]} (wl/paired-boot cells (range 2003 2023) {:reps 200 :seed 1 :alpha 0.1 :block 2})]
      (is (= [0.0 0.0 0.0] [diff lo hi]))))
  (testing "a model catching every onset the bench misses: the interval sits above zero"
    (let [cells {"a" (into {} (for [y (range 2003 2023)] [y [(if (even? (quot (- y 2003) 2)) 2.0 1.0) 3.0]]))}
          {:keys [diff lo hi]} (wl/paired-boot cells (range 2003 2023) {:reps 400 :seed 1 :alpha 0.1 :block 2})]
      (is (close? 0.5 diff))
      (is (< 0.0 lo))
      (is (< lo diff hi))))
  (testing "seeded: the same draws twice"
    (let [cells {"a" (into {} (for [y (range 2003 2023)] [y [(double (mod y 3)) 3.0]]))}
          f #(wl/paired-boot cells (range 2003 2023) {:reps 100 :seed 7 :alpha 0.1 :block 2})]
      (is (= (f) (f))))))

(deftest history-scores-use-only-onsets-known-at-the-origin
  ;; months: an onset in Jan 2000 (24000), lag 18 months; origin Dec 2000 (24011)
  (let [onsets (sorted-set 23400 24000)
        o 24011]
    (testing "an onset not yet known does not count"
      (is (= [23400] (vec (wl/known onsets 18 o))))
      (is (close? (wl/persistence onsets 18 o) (wl/persistence (sorted-set 23400) 18 o)))
      (is (close? (wl/climatology onsets 22800 18 o) (wl/climatology (sorted-set 23400) 22800 18 o))))
    (testing "once known it does"
      (is (> (wl/persistence onsets 18 (+ o 12)) (wl/persistence (sorted-set 23400) 18 (+ o 12))))
      (is (> (wl/climatology onsets 22800 18 (+ o 12)) (wl/climatology (sorted-set 23400) 22800 18 (+ o 12)))))
    (testing "persistence ranks the more recent onset higher, and any onset above none"
      (is (> (wl/persistence (sorted-set 23900) 0 o) (wl/persistence (sorted-set 23400) 0 o)
             (wl/persistence (sorted-set) 0 o))))
    (testing "climatology is the rate per covered year"
      (is (close? (/ 2.0 (/ (- o 22800) 12.0)) (wl/climatology (sorted-set 23000 23400) 22800 0 o))))))

(deftest hard-means-no-onset-in-the-ten-years-to-the-origin
  (let [o 24011]
    (is (wl/hard? (sorted-set) o))
    (is (wl/hard? (sorted-set (- o 120)) o) "exactly ten years before: out of the window")
    (is (not (wl/hard? (sorted-set (- o 119)) o)))
    (is (not (wl/hard? (sorted-set o) o)))
    (is (wl/hard? (sorted-set (inc o)) o) "an onset after the origin does not make it easy")))

(deftest lead-time-counts-the-streak-before-the-onset
  ;; onset in year 2010: caught from the 2008 or 2009 origin
  (testing "listed at 2005-2009: first entered five origins before"
    (is (= 5 (wl/lead-time #(<= 2005 % 2009) 2010))))
  (testing "a break in the listing restarts the streak"
    (is (= 2 (wl/lead-time #{2003 2004 2008 2009} 2010))))
  (testing "listed only at 2008, the last origin whose window holds it"
    (is (= 2 (wl/lead-time #{2008} 2010))))
  (testing "listed at 2009 only"
    (is (= 1 (wl/lead-time #{2009} 2010))))
  (testing "missed"
    (is (nil? (wl/lead-time #{2005 2006} 2010)) "listed earlier, but not when the window held the onset")
    (is (nil? (wl/lead-time #{} 2010))))
  (testing "the lookback is capped"
    (is (= 10 (wl/lead-time (constantly true) 2010)))))

(deftest ranking-ignores-the-outcomes
  ;; causality: the list at an origin is the same whatever happens after it
  (let [rows (vec (for [i (range 30)] {:c (str "c" i) :t 2005 :p {:m (/ i 30.0)} :y (even? i)}))
        flip (mapv #(update % :y not) rows)
        ranked (fn [rs] (map #(get-in % [:share [:m 5]]) (wl/rank-rows rs [:m] [5])))]
    (is (= (ranked rows) (ranked flip)))
    (is (= 5.0 (reduce + (ranked rows))))))

(deftest events-and-leads-from-ranked-rows
  ;; country A: listed 2006-2008, onset in 2009 (positive at origins 2007 and 2008)
  (let [rows (for [t (range 2003 2011)
                   c ["A" "B" "C"]]
               {:c c :t t :y (and (= c "A") (#{2007 2008} t))
                :share {[:m 1] (if (and (= c "A") (<= 2006 t 2008)) 1.0 0.0)}})
        evs (wl/events rows {"A" (sorted-set (+ 11 (* 12 2008) 3))})]
    (is (= [["A" 2009]] (vec evs)))
    (is (= [3] (wl/leads rows evs :m 1)))))

(deftest minimum-detectable-gain-interpolates-between-betas
  (let [res [{:beta 0.0 :signal true :recall-power 0.05 :gain 0.0}
             {:beta 0.0 :signal false :recall-power 0.9 :gain 0.0}
             {:beta 0.5 :signal true :recall-power 0.6 :gain 0.04}
             {:beta 1.0 :signal true :recall-power 1.0 :gain 0.12}]]
    (is (close? 0.08 (wl/mde res 0.8)) "halfway from 0.6 to 1.0, halfway from 0.04 to 0.12")
    (is (nil? (wl/mde (take 2 res) 0.8)))))

;; --- v2: against a null of same-shape groups (bead bnm-82j.6) --------------------

(deftest t-quantiles
  (is (< (Math/abs (- 1.8946 (wl/t-quantile 0.95 7))) 1e-3))
  (is (< (Math/abs (- 2.3534 (wl/t-quantile 0.95 3))) 1e-3))
  (is (< (Math/abs (- 1.6449 (wl/t-quantile 0.95 1e6))) 1e-3) "the normal's in the limit")
  (is (< (Math/abs (+ 1.8946 (wl/t-quantile 0.05 7))) 1e-3) "symmetric"))

(deftest the-null-test-adds-the-nulls-spread
  (let [nulls [0.010 0.014 0.018 0.012 0.016 0.011 0.015 0.013]
        mu (/ (reduce + nulls) 8.0)
        sd0 (wl/sd nulls)]
    (testing "a candidate at the null's mean gains nothing"
      (let [r (wl/null-test mu nulls 0.0 0.1)]
        (is (close? 0.0 (:g r)))
        (is (not (:pass r)))))
    (testing "the interval holds the null's spread, (1 + 1/R) of it, and the origins'"
      (let [r (wl/null-test (+ mu 0.01) nulls 1e-6 0.1)]
        (is (close? (:se r) (Math/sqrt (+ (* sd0 sd0 (+ 1 (/ 1.0 8))) 1e-6))))
        (is (= 7 (:df r)))
        (is (close? (:lo r) (- 0.01 (* (wl/t-quantile 0.95 7) (:se r)))))
        (is (:pass r) "well clear of eight draws this tight")))
    (testing "the noise gain the 82j.1 rule passed fails when the null holds noise like it"
      (is (not (:pass (wl/null-test 0.0136 [0.0117 -0.0121 0.0 -0.0015 0.0136] 0.0 0.1))))))
  (testing "fewer than two null draws: no spread, no test"
    (is (nil? (wl/null-test 0.05 [0.01] 0.0 0.1)))))

(deftest gain-over-the-null-mean-from-cells
  ;; per kind and year [hits pos] of the candidate, the bench and two nulls
  (let [hits {:c {"a" {1 2.0 2 1.0} "b" {1 1.0 2 0.0}}
              :b {"a" {1 1.0 2 1.0} "b" {1 0.0 2 0.0}}
              :n0 {"a" {1 1.0 2 2.0} "b" {1 1.0 2 0.0}}
              :n1 {"a" {1 1.0 2 0.0} "b" {1 0.0 2 1.0}}}
        pos {"a" {1 2 2 2} "b" {1 2 2 2}}
        cells (fn [s] (into {} (for [[k byy] (hits s)] [k (into {} (for [[y h] byy] [y {:hits h :pos (get-in pos [k y]) :slots 10}]))])))
        r (wl/null-gain {:c (cells :c) :b (cells :b) :nulls [(cells :n0) (cells :n1)]} [1 2] {:reps 200 :seed 1 :block 1})]
    ;; recall: c a 3/4 b 1/4 -> 0.5; n0 a 3/4 b 1/4 -> 0.5; n1 a 1/4 b 1/4 -> 0.25; bench a 2/4 b 0 -> 0.25
    (is (close? 0.125 (:g r)) "the candidate less the nulls' mean recall")
    (is (close? 0.25 (:cand r)) "the candidate less the bench")
    (is (every? true? (map close? [0.25 0.0] (:nulls r))) "each null less the bench")
    (is (<= 0.0 (:vb r)) "the origins' variance of the gain")))

(deftest calibration-from-null-draws-alone
  ;; each draw in turn the candidate, the null R of the others: with
  ;; exchangeable draws the share passing is the rule's size
  (let [g (m/rng 3)
        draws (vec (repeatedly 12 #(* 0.01 (m/normal! g))))
        r (wl/false-pass draws (fn [_] 0.0) 8 0.1 {:subsets 200 :seed 1})]
    (is (<= 0.0 (:rate r) 0.2))
    (is (pos? (:tests r))))
  (testing "a draw far from the rest passes"
    (let [draws (conj (vec (repeat 9 0.0)) 0.5)
          draws (mapv + draws (map #(* 1e-3 %) (range 10)))
          r (wl/false-pass draws (fn [_] 0.0) 8 0.1 {:subsets 50 :seed 1})]
      (is (pos? (:rate r))))))

(deftest power-of-the-null-test
  (let [p0 (wl/null-power 0.0 0.01 8 0.1 {:sims 4000 :seed 1})
        p1 (wl/null-power 0.05 0.01 8 0.1 {:sims 4000 :seed 1})]
    (is (< (Math/abs (- 0.05 p0)) 0.015) "its size at no gain")
    (is (> p1 0.95) "five standard errors")
    (is (< (wl/null-mde 0.01 4 0.1 {:sims 2000 :seed 1}) 0.06))
    (is (> (wl/null-mde 0.01 4 0.1 {:sims 2000 :seed 1}) (wl/null-mde 0.01 16 0.1 {:sims 2000 :seed 1})) "more draws, smaller detectable gain")))

(deftest harm-is-judged-against-the-nulls-cost
  ;; Brier gains over the bench: harm only when the candidate costs more
  ;; than groups of its shape holding nothing (the upper end below 0)
  (is (= :harm (wl/harm-verdict {:lo -0.004 :hi -0.0001})))
  (is (= :no-harm (wl/harm-verdict {:lo -0.0002 :hi 0.001})) "lower end within the bound")
  (is (= :not-shown (wl/harm-verdict {:lo -0.002 :hi 0.001})))
  (is (= :not-shown (wl/harm-verdict nil)) "no null draws, no verdict"))
