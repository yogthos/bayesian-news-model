(ns bnm.hazard-test
  (:require [bnm.hazard :as hz]
            [bnm.math :as m]
            [clojure.test :refer [deftest is testing]]))

(defn- synthetic [n seed]
  ;; onsets more likely where x1 is high, unrelated to x2
  (let [g (m/rng seed)]
    (vec (for [_ (range n)]
           (let [x1 (m/normal! g) x2 (m/normal! g)
                 p (m/expit (+ -3.0 (* 1.5 x1)))]
             {:x (double-array [1.0 x1 x2]) :y (< (m/next-double! g) p) :w 1.0})))))

(deftest fits-a-logistic-hazard
  (let [rows (synthetic 4000 1)
        beta (hz/fit rows 3 {:prior 1.0})]
    (is (< 1.2 (aget beta 1) 1.8) (vec beta))
    (is (< (Math/abs (aget beta 2)) 0.2) (vec beta))
    (testing "a warm start converges to the same place"
      (let [b2 (hz/fit rows 3 {:prior 1.0 :start beta :iterations 2})]
        (is (< (Math/abs (- (aget b2 1) (aget beta 1))) 1e-3))))))

(deftest scores-rare-event-forecasts
  (let [ys [true false false true false false false false]
        good [0.9 0.1 0.2 0.8 0.1 0.3 0.2 0.1]
        flat (repeat 8 0.25)]
    (is (= 1.0 (hz/auc good ys)))
    (is (= 0.5 (hz/auc flat ys)))
    (is (= 1.0 (hz/avg-precision good ys)))
    (testing "AUC within periods ignores how rates move between them"
      ;; period 1 has the higher scores and most positives, but within each
      ;; period the ranking is reversed
      (let [rows [[0 0.1 true] [0 0.2 false] [1 0.8 true] [1 0.9 false] [1 0.85 true]]]
        (is (> (hz/auc (map second rows) (map #(nth % 2) rows)) 0.3))
        (is (= 0.0 (hz/auc-within rows))))
      (is (nil? (hz/auc-within [[0 0.3 true] [1 0.2 false]])) "no period has both outcomes"))
    (testing "recall when flagging the top quarter of each period"
      (is (= 1.0 (hz/recall-at-budget (map vector (repeat 0) good ys) 0.25)))
      (is (= 0.0 (hz/recall-at-budget (map vector (repeat 0) (reverse good) ys) 0.25))))))

(deftest stays-finite-on-rare-separable-events
  ;; few positives, many inputs on large scales, one that separates the
  ;; positives: the fit must not run away, even from a bad start
  (let [g (m/rng 4)
        rows (vec (for [i (range 3000)]
                    (let [y (< i 30)
                          xs (concat [1.0 (if y 3.0 0.0)]
                                     (for [_ (range 60)] (* 50.0 (m/normal! g))))]
                      {:x (double-array xs) :y y :w (if y 1.0 6.7)})))
        cold (hz/fit rows 62 {:prior 2.0})
        ;; warm-started far from the answer, as a yearly refit can be
        warm (hz/fit rows 62 {:prior 2.0 :start (double-array 62 3.0)})
        ps (map #(hz/predict warm (:x %)) rows)]
    (is (every? #(not (Double/isNaN %)) warm))
    (is (every? #(<= 0.0 % 1.0) ps))
    (is (> (hz/auc ps (map :y rows)) 0.9))
    (is (< (Math/abs (- (hz/predict cold (:x (rows 0))) (hz/predict warm (:x (rows 0))))) 1e-3)
        "a bad start ends where a cold one does")))

(deftest combines-forecasters
  ;; one forecaster knows which unit (a fixed risk), another knows when (a
  ;; common shock); onsets need both
  (let [g (m/rng 7)
        rows (vec (for [t (range 200) u (range 20)]
                    (let [risky (< u 4) shock (zero? (rem t 5))
                          p (m/expit (+ -5.0 (if risky 2.5 0.0) (if shock 2.5 0.0)))]
                      {:who (m/expit (+ -4.0 (if risky 2.0 0.0)))
                       :when (m/expit (+ -4.0 (if shock 2.0 0.0)))
                       :noise (m/next-double! g)
                       :y (< (m/next-double! g) p)})))
        ys (map :y rows)
        ws (repeat (count rows) 1.0)]
    (testing "a linear pool puts its weight on forecasters that help"
      (let [w (hz/pool-weights (map (juxt :who :noise) rows) ys ws)]
        (is (> (first w) 0.8) (vec w))
        (is (< (Math/abs (- 1.0 (reduce + w))) 1e-9))))
    (testing "a log-linear stack beats each forecaster when they know different things"
      (let [st (hz/fit-stack (map (juxt :who :when) rows) ys ws {:prior 1.0})
            ps (map #(hz/predict-stack st [(:who %) (:when %)]) rows)
            ll (fn [ps] (reduce + (map (fn [p y] (Math/log (if y p (- 1.0 p)))) ps ys)))]
        (is (> (ll ps) (ll (map :who rows))))
        (is (> (ll ps) (ll (map :when rows))))))))

(deftest policy-usefulness
  ;; Sarlin (2013): the share of the loss of ignoring the forecast that it saves
  (let [ys [true true false false false false false false false false]]
    (testing "a perfect forecast saves all of it"
      (is (< (Math/abs (- 1.0 (hz/usefulness [0.9 0.8 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1] ys 0.5))) 1e-12)))
    (testing "a constant forecast saves none"
      (is (< (Math/abs (hz/usefulness (repeat 10 0.3) ys 0.5)) 1e-12)))))

(deftest bootstrap-intervals
  (let [rows (for [yr (range 20) i (range 10)] [yr (if (< i 3) (+ 0.5 (* 0.01 i)) (* 0.01 i)) (< i 3)])
        [lo hi] (hz/block-bootstrap-auc rows 200 7)]
    (testing "resamples whole periods, and the interval holds the estimate"
      (is (<= lo (hz/auc (map second rows) (map #(nth % 2) rows)) hi))
      (is (<= 0.0 lo hi 1.0)))))

(deftest calibration-cusum
  ;; risk-adjusted Bernoulli CUSUM (Steiner et al. 2000): accumulates the log
  ;; likelihood ratio of the outcomes under odds scaled by `ratio` against the
  ;; forecasts as given, reset at zero
  (let [ps (repeat 400 0.1)]
    (testing "calibrated forecasts stay quiet"
      (let [ys (map #(zero? (mod % 10)) (range 400))]
        (is (< (:max (hz/cusum ps ys 2.0)) 4.0))
        (is (< (:max (hz/cusum ps ys 0.5)) 4.0))))
    (testing "events twice as frequent as forecast raise the upward chart"
      (let [ys (map #(zero? (mod % 5)) (range 400))
            up (hz/cusum ps ys 2.0)]
        (is (> (:max up) 4.0))
        (is (some? (:signal up)) "and it says when")))
    (testing "events half as frequent raise the downward chart"
      (let [ys (map #(zero? (mod % 20)) (range 400))]
        (is (> (:max (hz/cusum ps ys 0.5)) 4.0))))
    (testing "overlapping windows count each increment once over the overlap"
      (let [ys (map #(zero? (mod % 5)) (range 400))]
        (is (< (Math/abs (- (:max (hz/cusum ps ys 2.0 :overlap 4)) (/ (:max (hz/cusum ps ys 2.0)) 4.0))) 1e-9))))))

(deftest recalibration-gain-says-whether-refitting-helps
  ;; [quarter p outcome] rows; the gain is the mean log score, over quarters
  ;; from `from`, of refitting an intercept on outcomes known each year minus
  ;; that of the forecasts as given
  (let [rows (fn [rate-of] (for [q (range 160) u (range 40)]
                             [q 0.05 (< (mod (hash [q u]) 1000) (* 1000 (rate-of q)))]))]
    (testing "a lasting shift is worth refitting for"
      (is (pos? (hz/recalibration-gain (rows (constantly 0.15)) 120 {:overlap 4}))))
    (testing "calibrated forecasts gain nothing"
      (is (< (hz/recalibration-gain (rows (constantly 0.05)) 120 {:overlap 4}) 0.002)))
    (testing "waves that come and go: refitting chases them and loses"
      (is (neg? (hz/recalibration-gain (rows #(if (< (mod % 24) 4) 0.25 0.012)) 120 {:overlap 4}))))
    (testing "nil without outcomes to score"
      (is (nil? (hz/recalibration-gain (rows (constantly 0.05)) 500 {:overlap 4}))))))

(deftest the-model-of-record-is-chosen-on-the-tuning-years
  ;; {model [[quarter p outcome] ...]}: the best mean log score over the
  ;; tuning years wins, whatever happens after them
  (let [ys (fn [q] (zero? (mod q 10)))
        good (for [q (range 0 160)] [q (if (ys q) 0.5 0.05) (ys q)])
        bad (for [q (range 0 160)] [q (if (< q 40) 0.1 (if (ys q) 0.9 0.01)) (ys q)])]
    (is (= :good (hz/pick-model {:good good :bad bad} 0 9)))
    (is (= :bad (hz/pick-model {:good good :bad bad} 10 39)) "later years favour the other")
    (is (nil? (hz/pick-model {:good good} 50 60)) "nil without rows in the years")))

(deftest paired-gain-over-a-benchmark
  ;; rows [period p-model p-bench outcome]: the metric's difference, with a
  ;; 90% interval resampling whole years (4 periods each)
  (let [g (bnm.math/rng 3)
        rows (vec (for [q (range 120) _ (range 30)
                        :let [y (< (bnm.math/next-double! g) 0.05)
                              noise (bnm.math/next-double! g)]]
                    [q (if y (+ 0.3 (* 0.1 noise)) (* 0.3 noise)) (* 0.4 (bnm.math/next-double! g)) y]))
        auc-of (fn [rs] (hz/auc (map second rs) (map #(nth % 2) rs)))
        {:keys [diff lo hi]} (hz/paired-gain rows auc-of 200 1)]
    (is (> diff 0.3) "the model separates, the benchmark is noise")
    (is (< 0.0 lo diff hi))
    (testing "a model against itself gains nothing"
      (let [same (mapv (fn [[q p _ y]] [q p p y]) rows)
            {:keys [diff lo hi]} (hz/paired-gain same auc-of 50 1)]
        (is (= 0.0 diff lo hi))))))

(deftest matched-controls-measure-signal-beyond-the-benchmark
  ;; each event against the k non-events of its period the benchmark rates
  ;; closest: the share of pairs the model orders right (ties a half)
  (let [rows (for [q (range 40) u (range 50)
                   :let [hist (/ (mod u 10) 10.0)
                         y (and (= 0 (mod (+ q u) 7)) (> hist 0.4))]]
               {:period q :p-bench hist :p-model (if y 0.95 hist) :y y})]
    (is (> (hz/matched-concordance rows 3) 0.95) "it knows which of the alike will tip")
    (is (< (Math/abs (- 0.5 (hz/matched-concordance (map #(assoc % :p-model (:p-bench %)) rows) 3))) 0.1)
        "the benchmark against its own matches is a coin")))

(deftest usefulness-matches-every-threshold-tried
  ;; the sweep gives what trying each threshold against every row gives
  (let [g (bnm.math/rng 11)
        ps (vec (repeatedly 500 #(Math/floor (* 50 (bnm.math/next-double! g)))))
        ys (mapv #(< (bnm.math/next-double! g) (/ % 100.0)) ps)
        brute (fn [ps ys mu]
                (let [n (count ys) pos (count (filter true? ys)) neg (- n pos)
                      p1 (/ pos (double n)) p2 (- 1.0 p1)
                      loss (fn [thr] (let [alarm (filter #(>= (first %) thr) (map vector ps ys))
                                           tp (count (filter #(true? (second %)) alarm))
                                           fp (- (count alarm) tp)]
                                       (+ (* mu p1 (/ (- pos tp) (double pos))) (* (- 1.0 mu) p2 (/ fp (double neg))))))
                      best (apply min (loss Double/POSITIVE_INFINITY) (map loss (distinct ps)))
                      ignore (min (* mu p1) (* (- 1.0 mu) p2))]
                  (/ (- ignore best) ignore)))]
    (doseq [mu [0.5 0.9 0.95]]
      (is (< (Math/abs (- (brute ps ys mu) (hz/usefulness ps ys mu))) 1e-12)))))

(deftest a-stack-refit-each-year-out-of-sample
  ;; rows [quarter [p1 p2] outcome]: each year from `from` on is forecast by
  ;; a stack fit only on the rows known by its start, so a second forecaster
  ;; that knows something the first does not raises the score held out
  (let [g (m/rng 11)
        rows (vec (for [q (range 40 120) _ (range 60)]
                    (let [a (m/normal! g) b (m/normal! g)
                          p (m/expit (+ -2.5 a b))]
                      [q [(m/expit (+ -2.5 a)) (m/expit (+ -2.5 b))] (< (m/next-double! g) p)])))
        ll (fn [rs] (/ (reduce + (for [[_ p y] rs] (Math/log (if y p (- 1.0 p))))) (double (count rs))))
        both (hz/stack-out-of-sample rows 80 {})
        first-only (hz/stack-out-of-sample (mapv (fn [[q ps y]] [q [(first ps)] y]) rows) 80 {})]
    (is (= (count (filter #(>= (first %) 80) rows)) (count both)) "every row from `from` on, once")
    (is (every? #(>= (first %) 80) both))
    (is (> (ll both) (+ 0.02 (ll first-only))) "the second forecaster's information is used")
    (testing "a year's stack sees nothing from that year"
      (let [poisoned (mapv (fn [[q ps y]] (if (>= q 100) [q ps (not y)] [q ps y])) rows)
            a (filter #(< (first %) 100) (hz/stack-out-of-sample rows 80 {}))
            b (filter #(< (first %) 100) (hz/stack-out-of-sample poisoned 80 {}))]
        (is (= (map second a) (map second b)))))))

(deftest a-stack-per-group-carries-the-rows-along
  ;; the second forecaster helps only in group :b; a stack per group finds it
  (let [g (m/rng 12)
        rows (vec (for [q (range 40 120) i (range 60)]
                    (let [grp (if (even? i) :a :b) a (m/normal! g) b (m/normal! g)
                          p (m/expit (+ -2.5 a (if (= grp :b) (* 2.0 b) 0.0)))]
                      [q [(m/expit (+ -2.5 a)) (m/expit (+ -2.5 b))] (< (m/next-double! g) p) grp [q i]])))
        out (hz/stack-out-of-sample rows 80 {:by #(nth % 3)})]
    (is (= (set (map #(nth % 4) (filter #(>= (first %) 80) rows))) (set (map #(nth % 4) out)))
        "every held-out row comes back once, with what it carried")
    (is (= (count out) (count (set (map #(nth % 4) out)))))
    (let [ll (fn [rs] (/ (reduce + (for [[_ p y] rs] (Math/log (if y p (- 1.0 p))))) (double (count rs))))
          pooled (hz/stack-out-of-sample rows 80 {})]
      (is (> (ll out) (ll pooled)) "a stack per group beats one for all"))))

(deftest recall-at-a-budget-shares-ties-at-the-cutoff
  ;; ten rows in one period, a budget of 2: one clear top row, then four tied,
  ;; one of them a positive; the second slot holds a quarter of the tie
  (let [rows (concat [[1 0.9 true]]
                     (for [y [true false false false]] [1 0.5 y])
                     (for [_ (range 5)] [1 0.1 false]))]
    (is (< (Math/abs (- (/ 1.25 2.0) (hz/recall-at-budget rows 0.2))) 1e-12))
    (is (= (hz/recall-at-budget rows 0.2) (hz/recall-at-budget (reverse rows) 0.2))
        "the order of the rows does not matter")))

(deftest recall-gain-from-per-period-parts
  ;; recall at a budget is hits over positives summed by period, so the
  ;; bootstrap resamples per-year sums instead of rescoring every row
  (let [g (m/rng 21)
        rows (vec (for [q (range 40 80) i (range 50)]
                    (let [a (m/normal! g) y (< (m/next-double! g) (m/expit (+ -3.0 (* 1.5 a))))]
                      [q (m/expit a) (m/expit (+ a (m/normal! g))) y])))
        parts (hz/recall-parts (map (fn [[q a _ y]] [q a y]) rows) 0.1)
        r (hz/recall-at-budget (map (fn [[q a _ y]] [q a y]) rows) 0.1)
        fast (hz/paired-recall-gain rows 0.1 200 1)
        slow (hz/paired-gain rows #(hz/recall-at-budget % 0.1) 200 1)]
    (is (< (Math/abs (- r (/ (reduce + (map first (vals parts))) (reduce + (map second (vals parts)))))) 1e-12))
    (is (< (Math/abs (- (:diff fast) (:diff slow))) 1e-12) "the same gain")
    (is (<= (:lo fast) (:diff fast) (:hi fast)))
    (is (< (Math/abs (- (:lo fast) (:lo slow))) 0.05) "about the same interval")
    (is (= {:diff 0.0 :lo 0.0 :hi 0.0} (hz/paired-recall-gain (mapv (fn [[q a _ y]] [q a a y]) rows) 0.1 50 1)))))
