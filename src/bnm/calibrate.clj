(ns bnm.calibrate
  "Turning the model's raw probabilities into calibrated ones, learned from how
  its past forecasts turned out.

  A simulation's escalation probability reflects only the uncertainty the
  model knows about; it is typically overconfident. Raw probabilities are
  binned per key (a channel, or a channel and stratum: whatever the caller
  keys them by), and each bin keeps forgetting-weighted counts of how often
  the outcome followed. The calibrated probability is a Beta
  posterior mean: the bin's observed rate, shrunk toward the raw probability
  when the bin has seen little. It is plain data in the model, and learns
  whenever forecasts resolve."
  (:require [bnm.math :as m]))

(def edges
  "Upper edges of the probability bins."
  [0.02 0.05 0.1 0.15 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 0.95 1.0])

(def shrink
  "Pseudo-observations at the raw probability each bin starts with."
  10.0)

(def forget
  "Discount applied to old counts each time a batch of outcomes is learned
  (one batch per forecast round: per year in a backtest)."
  0.9)

(defn- bin-of [p]
  (or (first (keep-indexed (fn [i e] (when (<= p e) i)) edges))
      (dec (count edges))))

(defn isotonic
  "The weighted least-squares nondecreasing fit to ys: pool adjacent
  violators, in one pass with backtracking (a port of scikit-learn's
  _inplace_contiguous_isotonic_regression). The CORP reliability diagram
  (Dimitriadis, Gneiting & Jordan 2021) is this fit of outcomes on forecasts."
  [ys ws]
  (let [n (count ys)
        y (double-array ys) w (double-array ws)
        target (long-array (range n))]
    (loop [i 0]
      (when (< i n)
        (let [k (inc (aget target i))]
          (when (< k n)
            (if (< (aget y i) (aget y k))
              (recur k)
              (let [i' (loop [k k swy (* (aget w i) (aget y i)) sw (aget w i)]
                         (let [prev (aget y k)
                               swy (+ swy (* (aget w k) (aget y k)))
                               sw (+ sw (aget w k))
                               k (inc (aget target k))]
                           (if (or (= k n) (< prev (aget y k)))
                             (do (aset y i (/ swy sw))
                                 (aset w i sw)
                                 (aset target i (dec k))
                                 (aset target (dec k) i)
                                 (if (pos? i) (aget target (dec i)) i))
                             (recur k swy sw))))]
                (recur i')))))))
    (loop [i 0]
      (when (< i n)
        (let [k (inc (aget target i))]
          (doseq [j (range (inc i) k)] (aset y j (aget y i)))
          (recur k))))
    (vec y)))

(defn- bin-center [i]
  (let [lo (if (zero? i) 0.0 (nth edges (dec i)))]
    (/ (+ lo (nth edges i)) 2.0)))

(defn calibrate
  "The calibrated probability for raw `p` in `channel` under `cal`: each
  bin's observed rate shrunk toward its raw probability (`p` itself for p's
  bin), then made nondecreasing across bins by pooling adjacent violators,
  so a higher forecast is never calibrated lower."
  [cal channel p]
  (if-let [{:keys [pos n]} (get cal channel)]
    (let [b (bin-of p)
          raw (fn [i] (if (= i b) p (bin-center i)))
          est (mapv (fn [i] (/ (+ (nth pos i) (* shrink (raw i))) (+ (nth n i) shrink))) (range (count edges)))
          ws (mapv #(+ % shrink) n)]
      (nth (isotonic est ws) b))
    p))

(defn learn
  "`cal` updated with resolved forecasts: a seq of {:channel :p :outcome}."
  [cal outcomes]
  (let [nb (count edges)
        empty-bins {:pos (vec (repeat nb 0.0)) :n (vec (repeat nb 0.0))}
        decayed (into {} (for [[ch b] cal]
                           [ch {:pos (mapv #(* forget %) (:pos b))
                                :n (mapv #(* forget %) (:n b))}]))]
    (reduce (fn [cal {:keys [channel p outcome]}]
              (let [i (bin-of p)]
                (-> cal
                    (update channel #(or % empty-bins))
                    (update-in [channel :n i] + 1.0)
                    (update-in [channel :pos i] + (if outcome 1.0 0.0)))))
            decayed outcomes)))

;; --- spread ------------------------------------------------------------------------------
;; The simulations' spread misses the shocks the model has no way to see coming,
;; so their ranges are too narrow. Per key ([kind channel stratum]), the spread
;; is widened by the factor that makes the forecasts' log errors match it,
;; learned from forecasts that have resolved: a recalibration of the
;; predictive distribution around its median (Gneiting & Ranjan 2013).

(def ^:private z90
  "The 0.9 quantile of the standard normal: an 80% range is 2 z90 sd wide."
  1.2815515655446004)

(def spread-shrink
  "Pseudo-forecasts whose errors match their spread, each key starts with."
  10.0)

(def spread-limits [0.5 5.0])

(defn spread-factor
  "How much to widen forecasts for `key` under `spread`: the root of the
  ratio of squared log errors to the simulations' log variance."
  [spread key]
  (if-let [{:keys [e2 v n]} (get spread key)]
    (let [vbar (/ v (max n 1e-9))
          k (Math/sqrt (/ (+ e2 (* spread-shrink vbar)) (max 1e-12 (+ v (* spread-shrink vbar)))))]
      (min (second spread-limits) (max (first spread-limits) k)))
    1.0))

(defn spread-factors
  "The skewed recalibration for `key` under `spread`: {:lo :hi :shift}, the
  widening below and above the median and the shift of the median, in log1p
  units. Each side's factor makes that side's squared log errors match the
  simulations' variance (for a calibrated forecast, half the outcomes fall
  each side, with mean square the variance); the shift moves the median to
  where the share of outcomes below it is a half. Keys learned before the
  sides were tracked get the symmetric factor and no shift."
  [spread key]
  (if-let [{:keys [e2-up v-up n-up e2-lo v-lo n-lo v n]} (get spread key)]
    (if (nil? n-up)
      (let [k (spread-factor spread key)] {:lo k :hi k :shift 0.0})
      (let [vbar (/ v (max n 1e-9))
            half (* 0.5 spread-shrink vbar)
            side (fn [e2 vv] (min (second spread-limits)
                                  (max (first spread-limits) (Math/sqrt (/ (+ e2 half) (max 1e-12 (+ vv half)))))))
            klo (side e2-lo v-lo) khi (side e2-up v-up)
            below (/ (+ n-lo (* 0.5 spread-shrink)) (+ n spread-shrink))
            z (m/normal-quantile (min 0.99 (max 0.01 below)))]
        {:lo klo :hi khi
         :shift (* -1.0 z (Math/sqrt vbar) (if (pos? z) klo khi))}))
    {:lo 1.0 :hi 1.0 :shift 0.0}))

(defn learn-spread
  "`spread` updated with resolved forecasts: rows with :level, :channel,
  :stratum, :q10, :q50, :q90 and :actual, their quantiles as forecast, after
  the recalibration `spread` as it stands. The errors are taken against the
  simulations' own median, and kept by side. Rows whose range is empty say
  nothing about their spread and are skipped."
  [spread rows]
  (let [decayed (into {} (for [[k s] spread] [k (update-vals s #(* forget %))]))]
    (reduce (fn [acc {:keys [level channel stratum q10 q50 q90 actual]}]
              (let [lo (Math/log1p (max 0.0 q10)) hi (Math/log1p (max 0.0 q90))]
                (if (> hi lo)
                  (let [key [level channel stratum]
                        {klo :lo khi :hi shift :shift} (spread-factors spread key)
                        sd (/ (- hi lo) (* z90 (+ klo khi)))
                        e (- (Math/log1p (max 0.0 actual)) (- (Math/log1p (max 0.0 q50)) shift))
                        up? (pos? e)]
                    (update acc key (fn [s] (merge-with + (merge {:e2 0.0 :v 0.0 :n 0.0 :e2-up 0.0 :v-up 0.0 :n-up 0.0
                                                                  :e2-lo 0.0 :v-lo 0.0 :n-lo 0.0} s)
                                                        {:e2 (* e e) :v (* sd sd) :n 1.0}
                                                        (if up?
                                                          {:e2-up (* e e) :v-up (* sd sd) :n-up 1.0}
                                                          {:e2-lo (* e e) :v-lo (* sd sd) :n-lo 1.0})))))
                  acc)))
            decayed rows)))

(defn widen
  "The sorted sample `xs` (a double array) with its log1p spread around its
  median scaled by `k`, then rescaled to the mean it had, as a new sorted
  array, floored at zero: wider by shocks that are as likely to lower a
  total as to raise it, which leaves its expected value where it was."
  ^doubles [k ^doubles xs]
  (let [n (alength xs)
        out (double-array n)]
    (when (pos? n)
      (let [m (Math/log1p (max 0.0 (m/quantile xs 0.5)))]
        (dotimes [i n]
          (aset out i (max 0.0 (Math/expm1 (+ m (* k (- (Math/log1p (max 0.0 (aget xs i))) m)))))))
        (let [before (areduce xs i t 0.0 (+ t (aget xs i)))
              after (areduce out i t 0.0 (+ t (aget out i)))]
          (when (pos? after)
            (let [f (/ before after)]
              (dotimes [i n] (aset out i (* f (aget out i)))))))))
    out))

(defn widen-skewed
  "The sorted sample `xs` (a double array) recalibrated by `spread-factors`:
  in log1p units, each side of the median stretched by its own factor and
  the median moved by the shift, as a new sorted array floored at zero. The
  mean moves with it: the shift and the skew are what the errors asked for."
  ^doubles [{:keys [lo hi shift]} ^doubles xs]
  (let [n (alength xs)
        out (double-array n)]
    (when (pos? n)
      (let [m (Math/log1p (max 0.0 (m/quantile xs 0.5)))]
        (dotimes [i n]
          (let [d (- (Math/log1p (max 0.0 (aget xs i))) m)]
            (aset out i (max 0.0 (Math/expm1 (+ m shift (* (if (pos? d) hi lo) d)))))))))
    out))

(defn isotonic-map
  "Recalibration learned from past forecasts ps and their outcomes ys: the
  isotonic fit of outcome on forecast (CORP), as a step function of a new
  forecast (the step at or below it; the lowest step below the range)."
  [ps ys]
  (let [pairs (sort-by first (map vector ps ys))
        xs (double-array (map first pairs))
        fit (double-array (isotonic (map #(if (second %) 1.0 0.0) pairs) (repeat (count pairs) 1.0)))
        n (alength xs)]
    (fn [p]
      ;; the last index with x <= p, by bisection
      (let [j (loop [lo -1 hi n]
                (if (<= (- hi lo) 1)
                  lo
                  (let [mid (quot (+ lo hi) 2)]
                    (if (<= (aget xs mid) p) (recur mid hi) (recur lo mid)))))]
        (aget fit (max 0 (min (dec n) j)))))))

(defn logistic-map
  "Recalibration learned from past forecasts ps and outcomes ys: a logistic
  regression of the outcome on the forecast's logit (Cox 1958), so a
  forecast too extreme is pulled in by a slope below one. Strictly
  monotone, so it keeps the forecasts' order, where the isotonic fit's
  flat steps tie them."
  [ps ys]
  (let [logit (fn [p] (let [p (min (max p 1e-6) (- 1.0 1e-6))] (Math/log (/ p (- 1.0 p)))))
        ;; Newton's method on two parameters, from the identity map
        rows (map (fn [p y] [(logit p) (if y 1.0 0.0)]) ps ys)]
    (loop [a 0.0 b 1.0 it 0]
      (let [[ga gb haa hab hbb]
            (reduce (fn [[ga gb haa hab hbb] [x y]]
                      (let [q (/ 1.0 (+ 1.0 (Math/exp (- (+ a (* b x))))))
                            r (- y q) v (* q (- 1.0 q))]
                        [(+ ga r) (+ gb (* r x)) (+ haa v) (+ hab (* v x)) (+ hbb (* v x x))]))
                    [0.0 0.0 1e-6 0.0 1e-6] rows)
            det (- (* haa hbb) (* hab hab))
            da (/ (- (* hbb ga) (* hab gb)) det)
            db (/ (- (* haa gb) (* hab ga)) det)]
        (if (or (>= it 50) (< (+ (Math/abs da) (Math/abs db)) 1e-9))
          (fn [p] (/ 1.0 (+ 1.0 (Math/exp (- (+ a (* b (logit p))))))))
          (recur (+ a da) (+ b db) (inc it)))))))
