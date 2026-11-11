(ns bnm.onsets-test
  (:require [augur.hier :as hier]
            [augur.logit :as lgt]
            [augur.math :as m]
            [bnm.named :as nm]
            [bnm.onsets :as on]
            [bnm.stocks :as sk]
            [clojure.test :refer [deftest is testing]]))

(deftest the-tree-above-a-country
  (is (= ["r/asia" "s/western-asia" "SYR"] (on/hier-path "SYR")))
  (is (= ["XXX"] (on/hier-path "XXX")) "a unit outside the tree is its own"))

(deftest nested-intercepts-beat-one-intercept-where-regions-differ
  ;; two regions of four subregions of five countries; onsets depend on an
  ;; input and on the region, which the input does not carry
  (let [g (m/rng 8)
        paths (for [r (range 2) s (range 4) c (range 5)] [(str "r" r) (str "r" r "s" s) (str "r" r "s" s "c" c)])
        draw (fn [n] (vec (for [path paths _ (range n)
                                :let [x (m/normal! g)
                                      eta (+ -3.0 (* 0.8 x) (if (= "r0" (first path)) 1.2 -0.4))]]
                            {:x (double-array [1.0 x]) :y (< (m/next-double! g) (m/expit eta)) :w 1.0 :path path})))
        train (draw 60)
        test (draw 60)
        flat (lgt/fit train 2 {:prior 1.0})
        {:keys [beta fx prior]} (on/fit-hier train 2 {} on/hier-opts)
        loss (fn [f] (/ (reduce + (for [{:keys [y] :as r} test :let [p (f r)]] (- (Math/log (if y p (- 1.0 p)))))) (count test)))]
    (is (< (loss #(lgt/predict beta (:x %) (hier/effect fx (:path %))))
           (loss #(lgt/predict flat (:x %)))))
    (testing "the slope is still found"
      (is (< 0.6 (aget ^doubles beta 1) 1.0) (vec beta)))
    (testing "the coefficients' prior precision is estimated with the variances"
      (is (< 0.0 prior 1e12) prior))))

(deftest parallel-fits-keep-their-order
  (let [ran (atom 0)
        thunks (for [i (range 23)] #(do (swap! ran inc) (* i i)))]
    (is (= (mapv #(* % %) (range 23)) (on/in-parallel 4 thunks)))
    (is (= 23 @ran) "each once")
    (is (= (on/in-parallel 1 thunks) (on/in-parallel 8 thunks)))
    (is (= [] (on/in-parallel 3 [])))))

(deftest non-finite-forecasts-stop-the-run
  (is (= 0.25 (on/checked 0.25 {:model :history})))
  (let [e (try (on/checked ##NaN {:model :hier-history :year 2003}) nil
               (catch Exception e e))]
    (is e "NaN throws")
    (is (= 2003 (:year (ex-data e)))))
  (is (thrown? Exception (on/checked ##Inf {}))))

(deftest country-means-look-only-backwards
  ;; the Mundlak (within-between) terms: each country's mean of the inputs
  ;; past the constant and the two history columns, from rows before the cut
  (let [row (fn [u q a b] {:unit u :q q :x (double-array [1.0 0.0 0.0 a b])})
        rows [(row "A" 0 1.0 10.0) (row "A" 1 3.0 20.0) (row "A" 8 100.0 100.0) (row "B" 0 5.0 0.0)]
        means (on/country-means rows 3 8)
        extend (on/with-means means)]
    (is (= [2.0 15.0] (vec (get-in means [:by "A"]))) "A's rows before quarter 8 only")
    (is (= [1.0 0.0 0.0 7.0 9.0 2.0 15.0] (vec (extend "A" (double-array [1.0 0.0 0.0 7.0 9.0]))))
        "the row's own inputs, then its country's means")
    (is (= [3.0 10.0] (drop 5 (vec (extend "C" (double-array [1.0 0.0 0.0 0.0 0.0])))))
        "a country with no earlier rows takes the mean of all rows before the cut")))

(deftest hier-fits-stop-once-settled
  (let [g (m/rng 9)
        paths (for [r (range 2) s (range 3) c (range 4)] [(str "r" r) (str "r" r "s" s) (str "r" r "s" s "c" c)])
        train (vec (for [path paths _ (range 40)
                         :let [x (m/normal! g)]]
                     {:x (double-array [1.0 x]) :y (< (m/next-double! g) (m/expit (+ -2.5 (* 0.8 x)))) :w 1.0 :path path}))
        capped (on/fit-hier train 2 {} on/hier-opts)
        cold (on/fit-hier train 2 {} (assoc on/hier-opts :rounds 100))
        warm (on/fit-hier train 2 cold on/hier-opts)]
    (is (<= (:rounds capped) (:rounds on/hier-opts)))
    (is (< (:rounds cold) 100) "it settles")
    (is (<= (:rounds warm) 2) "started where it settled, it stops at once")))

(deftest calm-slopes-double-the-inputs
  ;; cs-<inputs>: the inputs, then each times the calm flag (no known onset
  ;; in ten years), so a calm country gets its own intercept and slopes
  (is (= [1.0 2.0 3.0 1.0 2.0 3.0] (vec (on/with-calm (double-array [1.0 2.0 3.0]) 1.0))))
  (is (= [1.0 2.0 3.0 0.0 0.0 0.0] (vec (on/with-calm (double-array [1.0 2.0 3.0]) 0.0)))))

(deftest hier-fits-report-the-prior-their-coefficients-used
  ;; stopped by the round limit before settling, the coefficients are the
  ;; fit at :prior; the next step's estimate is :next-prior
  (let [g (m/rng 10)
        paths (for [r (range 2) s (range 3) c (range 4)] [(str "r" r) (str "r" r "s" s) (str "r" r "s" s "c" c)])
        train (vec (for [path paths _ (range 40)
                         :let [x (m/normal! g)]]
                     {:x (double-array [1.0 x]) :y (< (m/next-double! g) (m/expit (+ -2.5 (* 0.8 x)))) :w 1.0 :path path}))
        f (on/fit-hier train 2 {} (assoc on/hier-opts :rounds 1))]
    (is (= (:prior on/hier-opts) (:prior f)) "one round: fit at the starting prior")
    (is (not= (:prior f) (:next-prior f)))))

;; --- a source that stops, resumes or lapses in training (bead bnm-8ul) ---

(defn- lapse-world
  "Eighty units over 1990-2026 with one named source, input :a, monthly:
  units 0-19 never have it, and onsets are likelier where :a is high and
  where the source is had at all (so its presence coefficient is large).
  `months-of` gives, per unit, the months the source publishes; `extra`
  adds to the true log odds at a unit and quarter. Returns the named
  model's predictions {[unit q] log-odds} and the rows' named vectors."
  [months-of extra & [held]]
  (let [g (m/rng 11)
        held (or held (fn [_ t] t))
        units (mapv #(format "u%02d" %) (range 80))
        q0 (* 4 1990) q1 (+ 3 (* 4 2026))
        value (into {} (for [u units t (range (* 12 1985) (* 12 2027))] [[u t] (m/normal! g)]))
        db (into {} (for [u (drop 20 units)
                          :let [ms (months-of u)]
                          :when (seq ms)]
                      [[u "s"] (sk/series (for [t ms] [t (inc t) (value [u (held u t)])]))]))
        build (fn [] [[:a] (fn [u t] [(some-> (db [u "s"]) (sk/value t {:stale 6}))])])
        [labels vf] (nm/vectorizer build [[:a]])
        rows (vec (for [q (range q0 (inc q1)) u units]
                    {:unit u :q q :x (double-array 0) :named {:political (vf u (+ 2 (* 3 q)))}}))
        named (into {} (for [{:keys [unit q named]} rows] [[unit q] (:political named)]))
        onsets (into {} (for [u units]
                          [u (into (sorted-set)
                                   (for [q (range (+ q0 3) (inc q1))
                                         :let [^doubles x (named [u (- q 3)])
                                               eta (+ -4.5 (* 1.0 (aget x 0)) (* 1.0 (aget x 1)) (extra u q))]
                                         :when (< (m/next-double! g) (m/expit eta))]
                                     q))]))
        sets {:onsets onsets :spans onsets :known onsets :lags 0}
        fs (#'on/feature-sets [] (count labels) 0 [] labels)
        run ((on/evaluate-kind rows sets (set units) [2 4] 2000 q1 [:named] :political []) fs)
        preds (:preds (:named run))]
    {:p (into {} (for [[q p _ u] preds] [[u q] (m/logit p)]))
     :named named :units units :onsets onsets}))

(defn- both
  "The units with a forecast at both quarters and no onset between them
  (which would move their history)."
  [{:keys [p onsets]} units qa qb]
  (filter #(and (p [% qa]) (p [% qb]) (empty? (subseq (onsets %) > qa <= qb))) units))

(defn- mean [xs] (/ (reduce + xs) (double (count xs))))

(deftest a-source-that-stops-leaves-the-forecasts-where-they-were
  ;; the source's last month is March 2024: fresh to September, lapsed from
  ;; the December row on, for every unit at once
  ;; against the same world with the source still publishing its last value
  (let [stop (+ 2 (* 12 2024))
        {:keys [p named units] :as w} (lapse-world (fn [_] (range (* 12 1985) (inc stop))) (fn [_ _] 0.0))
        {held :p} (lapse-world (fn [_] (range (* 12 1985) (* 12 2027))) (fn [_ _] 0.0) (fn [_ t] (min t stop)))
        covered (drop 20 units)
        q3 (+ 2 (* 4 2024)) q4 (inc q3)]
    (is (= 1.0 (aget ^doubles (named ["u30" q4]) 2)) "lapsed")
    (is (= 1.0 (aget ^doubles (named ["u30" q4]) 1)) "still present")
    (testing "within the year's fit the lapse moves nothing"
      (let [us (filter #(p [% q4]) units)]
        (is (> (count us) 50))
        (is (every? #(< (Math/abs (- (p [% q4]) (held [% q4]))) 1e-9) us))))
    ;; the flag learns from the lapsed rows' outcomes as they resolve: noise
    ;; of a few dozen onsets here, against a presence step of about 1.0
    (testing "after refits on lapsed rows with outcomes, the level barely moves"
      (let [later (+ 2 (* 4 2026))
            us (filter #(p [% later]) covered)]
        (is (< (Math/abs (- (mean (map #(p [% later]) us)) (mean (map #(held [% later]) us)))) 0.3))))))

(deftest a-source-that-resumes-is-read-again
  (let [stop (+ 2 (* 12 2023)) back (+ 5 (* 12 2025))
        {:keys [p named units]} (lapse-world (fn [_] (concat (range (* 12 1985) (inc stop)) (range back (* 12 2027))))
                                             (fn [_ _] 0.0))
        q (+ 3 (* 4 2025))
        covered (filter #(p [% q]) (drop 20 units))
        xs (map #(aget ^doubles (named [% q]) 0) covered)
        ps (map #(p [% q]) covered)
        r (let [mx (mean xs) mp (mean ps)
                cov (reduce + (map #(* (- %1 mx) (- %2 mp)) xs ps))]
            (/ cov (Math/sqrt (* (reduce + (map #(let [d (- % mx)] (* d d)) xs))
                                 (reduce + (map #(let [d (- % mp)] (* d d)) ps))))))]
    (is (= 0.0 (aget ^doubles (named ["u30" q]) 2)) "fresh")
    (is (> r 0.5) "its values move the forecasts through their own coefficient")))

(deftest a-lapse-seen-in-training-is-learned
  ;; half the covered units' source pauses for 2000-2009, and onsets are
  ;; likelier while it does
  (let [paused? (fn [u] (odd? (parse-long (subs u 1))))
        gap (set (range (* 12 2000) (* 12 2010)))
        {:keys [p named units]} (lapse-world (fn [u] (remove #(and (paused? u) (gap %)) (range (* 12 1985) (* 12 2027))))
                                             (fn [u q] (if (and (paused? u) (<= (* 4 2000) q) (< q (* 4 2010))) 1.5 0.0)))
        q (+ 2 (* 4 2009))
        covered (drop 20 units)
        lapsed (filter #(= 1.0 (aget ^doubles (named [% q]) 2)) covered)
        fresh (filter #(p [% q]) (remove (set lapsed) covered))
        lapsed (filter #(p [% q]) lapsed)]
    (is (= 30 (count (filter #(= 1.0 (aget ^doubles (named [% q]) 2)) covered))))
    ;; slowly: the prior is on the flag's own scale, so a decade of lapses
    ;; moves it some way toward the true 1.5, not all of it
    (is (> (- (mean (map #(p [% q]) lapsed)) (mean (map #(p [% q]) fresh))) 0.05))))
