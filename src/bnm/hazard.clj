(ns bnm.hazard
  "Hazards of culminating events (conflict onsets, crises, crashes, surges)
  over a window ahead, as a Bayesian logistic regression pooled over
  countries: Gaussian priors centred on zero (ridge), fit by Newton's method
  on weighted rows. And the scores the onset evaluation reports (see
  docs/PHASE-CHANGES.md): ROC AUC, average precision, recall at an alert
  budget."
  (:require [bnm.math :as m]))

(set! *unchecked-math* true)

(defn fit
  "Coefficients for `rows` ({:x double[k] :y bool :w weight}), x[0] the
  intercept, which is left free; the others have prior precision :prior.
  Options :start (coefficients to start from) and :iterations (Newton steps,
  default 8)."
  ^doubles [rows ^long k {:keys [prior start iterations] :or {prior 1.0 iterations 8}}]
  (let [beta (if start (aclone ^doubles start) (double-array k))
        rows (vec rows)]
    (dotimes [_ iterations]
      (let [h (double-array (* k k))
            g (double-array k)]
        (doseq [{:keys [^doubles x y w]} rows]
          (let [eta (loop [i 0 acc 0.0] (if (< i k) (recur (inc i) (+ acc (* (aget beta i) (aget x i)))) acc))
                p (m/expit eta)
                w (double w)
                r (* w (- (if y 1.0 0.0) p))
                s (* w (Math/max 1e-9 (* p (- 1.0 p))))]
            (dotimes [i k]
              (let [xi (aget x i)]
                (when-not (zero? xi)
                  (aset g i (+ (aget g i) (* r xi)))
                  (dotimes [j k]
                    (aset h (+ (* i k) j) (+ (aget h (+ (* i k) j)) (* s xi (aget x j))))))))))
        ;; the prior's pull, then a Newton step
        (dotimes [i k]
          (let [pr (if (zero? i) 1e-4 (double prior))]
            (aset g i (- (aget g i) (* pr (aget beta i))))
            (aset h (+ (* i k) i) (+ (aget h (+ (* i k) i)) pr))))
        (let [step (m/solve-spd h g k)]
          (dotimes [i k] (aset beta i (+ (aget beta i) (aget step i)))))))
    beta))

(defn predict ^double [^doubles beta ^doubles x]
  (m/expit (loop [i 0 acc 0.0] (if (< i (alength beta)) (recur (inc i) (+ acc (* (aget beta i) (aget x i)))) acc))))

;; --- scores ------------------------------------------------------------------------------------

(defn auc
  "Area under the ROC curve (Mann-Whitney, ties averaged), or nil without
  both outcomes."
  [scores labels]
  (let [pairs (vec (sort-by first (map vector scores labels)))
        n (count pairs)
        n-pos (count (filter second pairs))
        n-neg (- n n-pos)]
    (when (and (pos? n-pos) (pos? n-neg))
      (let [rank-sum (loop [i 0 acc 0.0]
                       (if (>= i n)
                         acc
                         (let [s (first (pairs i))
                               j (loop [j i] (if (and (< j n) (= s (first (pairs j)))) (recur (inc j)) j))
                               r (/ (+ (inc i) j) 2.0)
                               pos (count (filter second (subvec pairs i j)))]
                           (recur j (+ acc (* r pos))))))]
        (/ (- rank-sum (/ (* n-pos (inc n-pos)) 2.0)) (* n-pos n-neg))))))

(defn avg-precision
  "Area under the precision-recall curve, or nil without positives."
  [scores labels]
  (let [n-pos (count (filter true? labels))]
    (when (pos? n-pos)
      (loop [xs (sort-by (comp - first) (map vector scores labels)) i 1 hits 0 acc 0.0]
        (if-let [[[_ l] & more] (seq xs)]
          (if l
            (recur more (inc i) (inc hits) (+ acc (/ (inc hits) (double i))))
            (recur more (inc i) hits acc))
          (/ acc n-pos))))))

(defn recall-at-budget
  "Of the positives, the share among the top `budget` fraction of rows by
  score in their period: `rows` are [period score label]."
  [rows budget]
  (let [pos (count (filter #(nth % 2) rows))]
    (when (pos? pos)
      (/ (reduce + (for [[_ g] (group-by first rows)
                         :let [k (long (Math/ceil (* budget (count g))))]]
                     (count (filter #(nth % 2) (take k (sort-by #(- (double (second %))) g))))))
         (double pos)))))

(defn brier [ps ys]
  (/ (reduce + (map (fn [p y] (let [d (- (double p) (if y 1.0 0.0))] (* d d))) ps ys)) (double (count ps))))
