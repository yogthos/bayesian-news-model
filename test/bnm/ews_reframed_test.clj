(ns bnm.ews-reframed-test
  (:require [augur.math :as m]
            [bnm.ews-reframed :as er]
            [clojure.test :refer [deftest is testing]]))

(defn- mo [y] (+ 11 (* 12 y)))

(deftest the-post-crisis-window-reads-only-the-past
  ;; an onset in (any month of) year s takes out origins s .. s + k; an
  ;; onset after the origin never does
  (let [ons (sorted-set (+ (* 12 1990) 4))]
    (is (= [false true true true true false]
           (mapv #(er/post-crisis? ons (mo %) 3 0) [1989 1990 1991 1992 1993 1994])))
    (is (false? (er/post-crisis? (sorted-set (+ (* 12 1995) 1)) (mo 1993) 3 0))
        "a later onset does not reach back")
    (is (false? (er/post-crisis? (sorted-set) (mo 1993) 3 0)))
    (testing "an onset not yet known at the origin does not count"
      (is (false? (er/post-crisis? ons (mo 1990) 3 12)) "known only in May 1991")
      (is (true? (er/post-crisis? ons (mo 1991) 3 12)))
      (is (true? (er/post-crisis? ons (mo 1993) 3 12)))
      (is (false? (er/post-crisis? ons (mo 1994) 3 12))))
    (testing "k = 0 drops the onset's own year only"
      (is (= [true false] (mapv #(er/post-crisis? ons (mo %) 0 0) [1990 1991]))))))

(deftest the-catalogue-is-cut-at-to
  (let [cat {:onsets {"AAA" (sorted-set (mo 1999) (+ 5 (* 12 2003)))}
             :busy {"AAA" (into (sorted-set) (range (* 12 2003) (* 12 2005)))}
             :from {"AAA" (* 12 1950)} :through {"AAA" (* 12 2020)} :known-lag 12}
        c (er/truncate cat 2002)]
    (is (= [(mo 1999)] (seq (get-in c [:onsets "AAA"]))))
    (is (empty? (get-in c [:busy "AAA"])))
    (is (= (mo 2002) (get-in c [:through "AAA"])))
    (is (= 12 (:known-lag c)))))

(deftest the-outcome-is-the-year-node-s-window
  ;; origin t: an onset in months (12t + 11, 12t + 35], the year node's
  ;; 24 months; nil outside the catalogue's cover
  (let [cat {:onsets {"AAA" (sorted-set (+ 3 (* 12 1980)))}
             :from {"AAA" (* 12 1950)} :through {"AAA" (mo 2002)}}]
    (is (= [false true true false] (mapv #(er/outcome cat "AAA" %) [1977 1978 1979 1980])))
    (is (false? (er/outcome cat "AAA" 2000)))
    (is (nil? (er/outcome cat "AAA" 2001)) "the window ends in 2003, past the cover")))

(deftest the-running-median-reads-only-the-past
  (let [xs [1.0 2.0 3.0 4.0 5.0 0.5 nil 9.0]
        d (er/running-median-dev xs 3)]
    (is (= [nil nil nil 2.0 2.5 -2.5 nil 6.5] d)
        "median of the values before, at least three of them")
    (testing "changing a later value leaves earlier outputs alone"
      (is (= (subvec d 0 5) (subvec (er/running-median-dev (assoc xs 5 100.0 7 -3.0) 3) 0 5))))))

(deftest the-rolling-log-spread
  (let [v (er/log-vol [1.0 3.0 1.0 3.0 nil 2.0 2.0 2.0] 2)]
    (is (nil? (v 0)) "before a full window")
    (is (< (Math/abs (- (v 1) (Math/log (Math/sqrt 2.0)))) 1e-12) "sample sd of 1, 3")
    (is (nil? (v 4)) "a missing value")
    (is (nil? (v 5)) "a window over a missing value")
    (is (nil? (v 6)) "a flat window")))

(deftest the-calm-run-resets-and-counts
  (is (= [1 2 0 1 nil 1 2 0] (er/calm-run [-1.0 -0.2 0.3 -1.0 nil -0.1 -0.5 0.0])))
  (is (= [nil 0 1] (er/calm-run [nil 2.0 -2.0]))))

(deftest calm-duration-over-the-series
  ;; per series the log spread of the own part over `vol-window` years
  ;; against the median of its own past; a year is calm when the mean
  ;; deviation over the series known that year is below zero
  (let [loud-then-calm (into (sorted-map) (for [y (range 1960 2000)]
                                            [y (if (< y 1980)
                                                  ;; louder every year: never below its past
                                                  (* (+ 1.0 (* 0.1 (- y 1960))) (if (even? y) 1 -1))
                                                  (if (even? y) 0.1 -0.1))]))
        d (er/calm-duration {"g" loud-then-calm} {:vol-window 3 :min-past 5})]
    (is (nil? (d 1963)) "too little past for a median")
    (is (zero? (d 1979)) "rising volatility is never calm")
    (is (pos? (d 1982)) "the run starts once the window is quiet")
    (is (= (+ 17 (d 1982)) (d 1999)) "and counts a year at a time")
    (testing "causal: the future does not move the past"
      (let [d2 (er/calm-duration {"g" (merge loud-then-calm (into {} (for [y (range 1990 2000)] [y (* 50.0 (if (even? y) 1 -1))])))}
                                 {:vol-window 3 :min-past 5})]
        (is (= (select-keys d (range 1960 1990)) (select-keys d2 (range 1960 1990))))
        (is (zero? (d2 1992)) "a loud spell resets the run")))
    (testing "two series: their mean deviation"
      (let [loud (into (sorted-map) (for [y (range 1960 2000)] [y (if (even? y) (+ 3.0 (* 0.01 y)) -3.0)]))
            d3 (er/calm-duration {"g" loud-then-calm "h" loud} {:vol-window 3 :min-past 5})]
        (is (pos? (d3 1999)) "one series far below its median carries the mean")))))

(deftest rows-never-read-an-outcome-after-to
  (let [cs ["AAA" "BBB"]
        mk (fn [late] {:onsets {"AAA" (into (sorted-set (+ 2 (* 12 1990))) late) "BBB" (sorted-set)}
                       :busy {} :from {"AAA" 0 "BBB" 0} :through {"AAA" (mo 2020) "BBB" (mo 2020)}})
        args (fn [cat] {:cat cat :kind "k" :cells (for [c cs t (range 1980 2015)] [c t])
                        :offset (fn [_ _] -3.0) :read (fn [c t] (if (= c "AAA") (double t) (- (double t))))
                        :from 1980 :to 2002 :k 3})
        a (er/kind-rows (args (mk [])))
        b (er/kind-rows (args (mk (map #(+ 5 (* 12 %)) (range 2003 2015)))))]
    (is (= a b) "onsets after --to change nothing")
    (is (= 2000 (apply max (map :t a))) "the last origin's window ends at --to")
    (is (every? #(<= (:t %) (- 2002 2)) a))
    (testing "the exclusion flag marks the post-crisis rows of the kind's own onsets"
      (is (= #{1990 1991 1992 1993} (set (map :t (filter :post a))))))
    (testing "an onset in --to's year is not read at an origin before it"
      (let [c (er/kind-rows (args (mk [(+ 5 (* 12 2002))])))]
        (is (some :y c))
        (is (= (map :t a) (map :t c)))))))

(deftest the-signed-clustered-score
  (let [g (m/rng 5)
        rows (vec (for [i (range 4000)
                        :let [x (m/normal! g)
                              p (/ 1.0 (+ 1.0 (Math/exp (- (+ -2.0 (* 0.5 x))))))]]
                    {:y (< (m/next-double! g) p) :o -2.0 :w 1.0 :g (mod i 40) :x [1.0 x]}))]
    (is (> (er/score-z rows) 3.0))
    (is (< (er/score-z (mapv #(update-in % [:x 1] -) rows)) -3.0))))

(deftest holm-adjusts-in-order
  (is (= [0.04 0.06 0.06 0.5] (mapv #(/ (Math/round (* 1000 %)) 1000.0) (er/holm [0.01 0.03 0.02 0.5]))))
  (is (= [0.7] (er/holm [0.7])))
  (is (= [1.0 1.0] (er/holm [0.6 0.7]))))

(deftest permutation-p-in-the-prior-direction
  (is (= (/ 1.0 5) (er/perm-p 2.0 [0.0 1.0 -1.0 0.5] 1)))
  (is (= (/ 5.0 5) (er/perm-p 2.0 [0.0 1.0 -1.0 0.5] -1)))
  (is (= (/ 2.0 5) (er/perm-p -2.0 [0.0 1.0 -3.0 0.5] -1))))
