(ns bnm.benchmarks-test
  (:require [bnm.benchmarks :as bm]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(def cat0
  "Months: onsets of A at 100 and 200, B at 150; known 12 months late."
  {:onsets {"A" (sorted-set 100 200) "B" (sorted-set 150) "C" (sorted-set)}
   :known-lag 12})

(deftest the-last-12-months-are-the-last-12-published
  (testing "the window is the 12 months ending at the newest month known at the origin"
    (is (= [88 100] (bm/published-12 cat0 112))))
  (testing "an onset counts only once it is known"
    (is (true? (bm/last-12? cat0 "A" 112)) "A's onset at 100 is known at 112")
    (is (false? (bm/last-12? cat0 "A" 111)) "not yet known at 111")
    (is (false? (bm/last-12? cat0 "A" 124)) "out of the window by 124")
    (is (false? (bm/last-12? cat0 "C" 300)))
    (is (false? (bm/last-12? cat0 "Z" 300)) "a place without a catalogue entry is quiet")))

(deftest past-counts-read-only-resolved-rows
  ;; rows resolve at :t + ends + lag; ends 24, lag 12
  (let [rows [{:t 0 :y true :key :a} {:t 12 :y false :key :a} {:t 24 :y true :key :a} {:t 0 :y false :key :b}]
        f (bm/past-counts rows :key 36)]
    (is (= [0 0] (f :a 35)) "nothing resolved before 36")
    (is (= [1 1] (f :a 36)) "the row at 0 resolves at 36")
    (is (= [1 2] (f :a 48)))
    (is (= [2 3] (f :a 1000)))
    (is (= [0 1] (f :b 36)))
    (is (= [0 0] (f :c 1000)) "an unseen key")))

(deftest smoothing-pulls-a-thin-history-to-the-prior
  (is (close? 0.1 (bm/smoothed 0 0 0.1)) "no history: the prior")
  (is (close? (/ 1.1 2) (bm/smoothed 1 1 0.1)) "one row and one pseudo-row at the prior")
  (is (close? (/ 50.1 101) (bm/smoothed 50 100 0.1))))

(deftest long-run-share-of-the-parent's-known-onsets
  (let [members ["A" "B" "C"]]
    (testing "the place's share of its parent's members' onsets known by t"
      (is (close? (/ 2.0 3) (bm/long-run-share cat0 members "A" 300)))
      (is (close? (/ 1.0 3) (bm/long-run-share cat0 members "B" 300)))
      (is (close? 0.0 (bm/long-run-share cat0 members "C" 300))))
    (testing "only what is known: at 140 A's onset at 100 is the only one"
      (is (close? 1.0 (bm/long-run-share cat0 members "A" 140)))
      (is (close? 0.0 (bm/long-run-share cat0 members "B" 140))))
    (testing "no known onsets among the members: an equal share"
      (is (close? (/ 1.0 3) (bm/long-run-share cat0 members "C" 100))))))

(deftest benchmark-probabilities-from-the-past-only
  ;; two countries, one parent; year rows (ends 24) at each year end, lag 12
  (let [cat {:onsets {"A" (sorted-set 30) "B" (sorted-set)} :known-lag 12}
        rows (vec (for [c ["A" "B"] t [11 23 35 47 59 71]]
                    {:place c :t t :p 0.2 :y (and (= c "A") (< t 30 (+ t 25)))}))
        parent-p {["P" 71] 0.5}
        out (bm/with-benchmarks rows {:cat cat :ends 24 :parent (constantly "P") :members {"P" ["A" "B"]}
                                      :parent-p (fn [place t] (parent-p [place t]))})
        at (fn [c t] (first (filter #(and (= c (:place %)) (= t (:t %))) out)))]
    (testing "climatology: the place's own resolved rows, smoothed to the level's rate"
      ;; at 71, rows with t + 36 <= 71 are resolved: t 11, 23, 35 per country
      ;; A: t 11 (window 11-35 holds 30) yes, 23 yes, 35 no -> 2 of 3; level 2 of 6
      (is (close? (/ (+ 2 (/ 2.0 6)) 4) (:clim (at "A" 71))))
      (is (close? (/ (+ 0 (/ 2.0 6)) 4) (:clim (at "B" 71)))))
    (testing "before anything resolves the benchmarks fall back to nothing known"
      (is (nil? (:clim (at "A" 11))) "no level rate yet: no benchmark")
      (is (nil? (:last12 (at "A" 11)))))
    (testing "last 12 months: the level's past rate among rows in the same state"
      ;; A's onset at 30 is known at 42, in A's published window (t-24, t-12] for t in [42, 54)
      (is (true? (bm/last-12? cat "A" 47)))
      (is (false? (bm/last-12? cat "A" 71)) "(47, 59] holds nothing")
      ;; at 71 the resolved rows (t 11, 23, 35) were all quiet then: 2 of 6
      (is (close? (/ (+ 2 (/ 2.0 6)) 7) (:last12 (at "A" 71)))))
    (testing "the constant: the level's observed rate over rows resolved by the origin"
      (is (close? (/ 2.0 6) (:rate (at "A" 71))))
      (is (close? (/ 2.0 6) (:rate (at "B" 71))) "the same for every place")
      (is (close? 0.5 (:rate (at "A" 59))) "t 11 and 23 resolved: A's two onsets of four rows")
      (is (nil? (:rate (at "A" 11)))))
    (testing "persistence: the level's rate among rows in the same ten-year state"
      ;; A's onset at 30 is known at 42; the state is an onset in (t - 120, t - lag]
      (is (false? (bm/ten-years? cat "A" 41)))
      (is (true? (bm/ten-years? cat "A" 42)))
      (is (true? (bm/ten-years? cat "A" 149)))
      (is (false? (bm/ten-years? cat "A" 150)) "ten years on, (30, 138] no longer holds it")
      ;; every resolved row by 59 was in the quiet state: A at 59 (state on) has
      ;; no history in its state and takes the prior; B the quiet rows' 2 of 4
      (is (close? 0.5 (:persist (at "A" 59))))
      (is (close? (/ (+ 2 0.5) 5) (:persist (at "B" 59))))
      (is (nil? (:persist (at "A" 11)))))
    (testing "parent probability times the long-run share"
      (is (close? 0.5 (:share (at "A" 71))) "A has every known onset")
      (is (close? 0.0 (:share (at "B" 71))))
      (is (nil? (:share (at "A" 59))) "no parent forecast, no benchmark"))))

(deftest skill-is-one-less-the-loss-ratio
  (let [rows [{:y true :p 0.8 :b 0.5} {:y false :p 0.1 :b 0.5}]
        s (bm/skill rows :p :b bm/brier)]
    (is (close? (- 1.0 (/ (/ (+ 0.04 0.01) 2) 0.25)) s))
    (is (close? 0.0 (bm/skill rows :b :b bm/brier)) "a benchmark against itself")))
