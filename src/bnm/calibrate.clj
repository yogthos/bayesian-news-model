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

(defn calibrate
  "The calibrated probability for raw `p` in `channel` under `cal`."
  [cal channel p]
  (if-let [{:keys [pos n]} (get cal channel)]
    (let [i (bin-of p)]
      (/ (+ (nth pos i) (* shrink p)) (+ (nth n i) shrink)))
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

(defn learn-spread
  "`spread` updated with resolved forecasts: rows with :level, :channel,
  :stratum, :q10, :q50, :q90 and :actual, their quantiles as forecast, after
  widening by `spread` as it stands. Rows whose range is empty say nothing
  about their spread and are skipped."
  [spread rows]
  (let [decayed (into {} (for [[k s] spread] [k (update-vals s #(* forget %))]))]
    (reduce (fn [acc {:keys [level channel stratum q10 q50 q90 actual]}]
              (let [lo (Math/log1p (max 0.0 q10)) hi (Math/log1p (max 0.0 q90))]
                (if (> hi lo)
                  (let [key [level channel stratum]
                        f (spread-factor spread key)
                        sd (/ (- hi lo) (* 2.0 z90 f))
                        e (- (Math/log1p (max 0.0 actual)) (Math/log1p (max 0.0 q50)))]
                    (update acc key (fn [s] (merge-with + (or s {:e2 0.0 :v 0.0 :n 0.0})
                                                        {:e2 (* e e) :v (* sd sd) :n 1.0}))))
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
