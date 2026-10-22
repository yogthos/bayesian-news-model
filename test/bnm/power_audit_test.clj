(ns bnm.power-audit-test
  (:require [augur.logit]
            [augur.math :as m]
            [augur.score]
            [bnm.paired]
            [bnm.power-audit :as pa]
            [bnm.relation-diag :as rd]
            [clojure.test :refer [deftest is testing]]))

(defn- corr [xs ys]
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sxx (reduce + (map #(* (- % mx) (- % mx)) xs))
        syy (reduce + (map #(* (- % my) (- % my)) ys))]
    (/ sxy (Math/sqrt (* sxx syy)))))

(defn- panel-rows
  "Synthetic rows: `nc` countries over 1960-2022, every year end, base
  forecast p (a country rate), outcomes from p and year shocks of sd `s`."
  [nc s seed]
  (let [g (m/rng seed)
        cs (mapv #(format "C%02d" %) (range nc))
        rate (zipmap cs (map #(+ 0.02 (* 0.06 (/ % (double nc)))) (range nc)))
        u (into {} (for [t (range 1960 2023)] [t (* s (m/normal! g))]))]
    {:cs cs
     :rows (vec (for [c cs t (range 1960 2023)
                      :let [p (rate c) q (m/expit (+ (m/logit p) (u t)))]]
                  {:c c :t t :p p :o (m/logit p) :y (< (m/next-double! g) q)}))}))

(deftest rows-are-year-end-origins-in-range
  ;; {country (sorted-map origin [p y])} -> rows: year ends only, resolved,
  ;; the countries given, origins' years within [from, to]
  (let [years {"AAA" (sorted-map (+ 11 (* 12 1959)) [0.1 true] (+ 11 (* 12 1960)) [0.1 false]
                                 (+ 5 (* 12 1961)) [0.2 true] (+ 11 (* 12 1961)) [0.2 true]
                                 (+ 11 (* 12 1962)) [0.3 nil] (+ 11 (* 12 2003)) [0.3 true])
               "BBB" (sorted-map (+ 11 (* 12 1961)) [0.4 false])}
        rs (pa/year-rows years ["AAA"] 1960 2002)]
    (is (= [["AAA" 1960 false] ["AAA" 1961 true]] (mapv (juxt :c :t :y) rs)))
    (is (< (Math/abs (- (:o (second rs)) (m/logit 0.2))) 1e-12) "the base's log odds, the offset")
    (is (= [2003] (map :t (pa/year-rows years ["AAA" "BBB"] 2003 2022))))))

(deftest the-year-shock-from-overdispersed-counts
  ;; the sd of a year shock on the log odds, by the method of moments on
  ;; yearly counts against the base's; 0 when the counts are not overdispersed
  (is (< (pa/year-shock-sd (:rows (panel-rows 150 0.0 3))) 0.15))
  (let [s (pa/year-shock-sd (:rows (panel-rows 150 0.6 4)))]
    (is (< 0.35 s 0.9) (str "estimated " s)))
  (testing "underdispersed: every year exactly as expected"
    (let [rows (for [t (range 1960 2000) i (range 10)] {:c (str i) :t t :p 0.1 :o (m/logit 0.1) :y (zero? i)})]
      (is (= 0.0 (pa/year-shock-sd rows))))))

(deftest three-kinds-of-state
  (let [cs (mapv #(format "C%02d" %) (range 80))
        yrs (range 1940 2023)
        at (fn [panel c] (mapv #(get-in panel [c %]) yrs))]
    (testing "own part: AR(1) within a country, independent across countries"
      (let [x (pa/state-panel (m/rng 1) :own cs 1940 2022)
            r1 (/ (reduce + (for [c cs :let [v (at x c)]] (corr (butlast v) (rest v)))) (count cs))
            rx (/ (reduce + (for [[a b] (partition 2 cs)] (corr (at x a) (at x b)))) 40)]
        (is (< 0.75 r1 0.95) (str "lag-1 " r1))
        (is (< (Math/abs rx) 0.1) (str "across " rx))))
    (testing "level: mostly between countries"
      (let [x (pa/state-panel (m/rng 2) :level cs 1940 2022)
            means (map #(let [v (at x %)] (/ (reduce + v) (count v))) cs)
            all (mapcat #(at x %) cs)
            tot (let [mu (/ (reduce + all) (count all))] (/ (reduce + (map #(* (- % mu) (- % mu)) all)) (count all)))
            btw (let [mu (/ (reduce + means) (count means))] (/ (reduce + (map #(* (- % mu) (- % mu)) means)) (count means)))]
        (is (> (/ btw tot) 0.8))))
    (testing "world: one series, the same at every country"
      (let [x (pa/state-panel (m/rng 3) :world cs 1940 2022)]
        (is (= (at x "C00") (at x "C41")))
        (is (> (corr (butlast (at x "C00")) (rest (at x "C00"))) 0.6))))))

(deftest the-placebo-is-another-country-or-ten-years-before
  (let [cs ["A" "B" "C"]
        x {"A" {2000 1.0 1990 9.0} "B" {2000 2.0} "C" {2000 3.0}}]
    (is (= 2.0 ((pa/placebo :own cs x) "A" 2000)))
    (is (= 1.0 ((pa/placebo :level cs x) "C" 2000)) "the rotation wraps")
    (is (= 9.0 ((pa/placebo :world cs x) "A" 2000)))))

(deftest the-score-pair-is-relation-diag-s-clustered-test
  ;; one null fit for the real input and its placebo, the same statistic
  (let [{:keys [rows]} (panel-rows 40 0.3 7)
        g (m/rng 8)
        xa (mapv (fn [_] (m/normal! g)) rows)
        xb (mapv (fn [r] (+ (* 0.1 (m/normal! g)) (if (:y r) 0.4 0.0))) rows)
        [la lb] (pa/score-pair rows (mapv :y rows) xa xb)
        ref (fn [xs] (:lr (rd/clustered-test (mapv (fn [r x] {:y (:y r) :o (:o r) :w 1.0 :g (:t r) :x [1.0 x]}) rows xs))))]
    (is (< (Math/abs (- la (ref xa))) 1e-6))
    (is (< (Math/abs (- lb (ref xb))) 1e-6))
    (is (> lb 10.0))))

(deftest standardized-on-the-design-rows
  (let [x {"A" {2000 1.0 2001 3.0 2010 100.0} "B" {2000 5.0}}
        z (pa/standardize x [{:c "A" :t 2000} {:c "A" :t 2001} {:c "B" :t 2000}])]
    (is (< (Math/abs (reduce + (map #(get-in z %) [["A" 2000] ["A" 2001] ["B" 2000]]))) 1e-9))
    (is (some? (get-in z ["A" 2010])) "test years scaled the same way")))

(deftest no-effect-passes-at-or-below-nominal
  ;; false-positive control: OR 1, year shocks in the outcomes, 200 draws;
  ;; the share passing at k = 1 within nominal 5% plus Monte Carlo error
  (let [{:keys [rows cs]} (panel-rows 60 0.4 11)
        design (filterv #(<= (:t %) 2002) rows)]
    (doseq [kind [:own :world]]
      (let [res (pa/diag-sims design cs kind 0.0 0.4 200 21)
            share (pa/pass-share res 1)]
        (is (<= share (+ 0.05 (* 2 (pa/mc-error 0.05 200)))) (str kind " passes " share))))))

(deftest a-large-effect-is-found
  (let [{:keys [rows cs]} (panel-rows 60 0.2 12)
        design (filterv #(<= (:t %) 2002) rows)
        res (pa/diag-sims design cs :own (Math/log 2.5) 0.2 40 22)]
    (is (>= (pa/pass-share res 1) 0.8))
    (is (>= (pa/pass-share res 1) (pa/pass-share res 10) (pa/pass-share res 348)) "a longer list, less power")))

(deftest out-of-sample-gain
  ;; the oracle knows beta; the realistic model fits it on the design years;
  ;; both against the base on the test years, paired by country and year
  (let [{:keys [rows cs]} (panel-rows 60 0.0 13)
        null (pa/oos-sim {"k" rows} cs :own 0.0 {"k" 0.0} 1960 2002 2003 2022 (m/rng 5) 100)
        big (pa/oos-sim {"k" rows} cs :own (Math/log 3.0) {"k" 0.0} 1960 2002 2003 2022 (m/rng 6) 100)]
    (is (< (Math/abs (get-in null [:per-kind "k" :oracle :brier :diff])) 1e-12) "beta 0: the oracle is the base")
    (is (pos? (get-in big [:per-kind "k" :oracle :brier :diff])))
    (is (pos? (get-in big [:pooled :realistic :brier :diff])))
    (is (contains? #{true false} (get-in big [:per-kind "k" :realistic :brier :gain?])))))

(deftest minimum-detectable-gain
  ;; the expected gain where power crosses 80%, interpolated on log OR; nil
  ;; when no OR in the grid reaches it
  (let [grid [{:or 1.0 :power 0.05 :gain 0.0} {:or 1.5 :power 0.6 :gain 0.001} {:or 2.0 :power 1.0 :gain 0.003}]
        r (pa/min-detectable grid 0.8)]
    (is (< 1.5 (:or r) 2.0))
    (is (< 0.001 (:gain r) 0.003))
    (is (nil? (pa/min-detectable (take 2 grid) 0.8)))))

(deftest one-bootstrap-for-several-losses
  ;; augur.score/paired-loss-2way's draws, the same weights shared by
  ;; several loss differences: each column's result is that function's
  (let [g (m/rng 9)
        rows (vec (for [c ["A" "B" "C" "D" "E"] t (range 2003 2023) k ["k1" "k2"]
                        :let [pb (* 0.2 (m/next-double! g)) pm (min 0.99 (* pb (+ 0.8 (* 0.4 (m/next-double! g)))))]]
                    [t pm pb (< (m/next-double! g) pb) c k]))
        opts {:reps 200 :seed 1 :alpha 0.1 :per-block 1}
        [rb rl] (pa/paired-losses rows [bnm.paired/brier bnm.paired/log-loss] opts)
        eb (augur.score/paired-loss-2way rows bnm.paired/brier opts)
        el (augur.score/paired-loss-2way rows bnm.paired/log-loss opts)]
    (doseq [[a e] [[rb eb] [rl el]] k [:diff :lo :hi]]
      (is (< (Math/abs (- (k a) (k e))) 1e-12) (str k)))))

(deftest the-offset-fit
  ;; the maximum-likelihood slope on one input with the base as offset, no
  ;; intercept: its score is zero; with no input, augur.logit/fit's intercept
  (let [{:keys [rows]} (panel-rows 30 0.2 14)
        g (m/rng 15)
        xs (mapv (fn [_] (m/normal! g)) rows)
        ys (mapv #(< (m/next-double! g) (m/expit (+ (:o %1) (* 0.5 %2)))) rows xs)
        b (pa/fit-offset (mapv :o rows) xs ys)
        score (reduce + (map (fn [r x y] (* x (- (if y 1.0 0.0) (m/expit (+ (:o r) (* b x)))))) rows xs ys))
        e (aget ^doubles (augur.logit/fit (mapv (fn [r y] {:x [1.0] :y y :o (:o r) :w 1.0}) rows ys) 1 {:prior 1e-4}) 0)]
    (is (< (Math/abs score) 1e-6))
    (is (< 0.3 b 0.7) (str b))
    (is (< (Math/abs (- e (pa/fit-offset (mapv :o rows) nil ys))) 1e-6))))
