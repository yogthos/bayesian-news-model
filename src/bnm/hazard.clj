(ns bnm.hazard
  "Hazards of culminating events (conflict onsets, crises, crashes, surges)
  over a window ahead, as a Bayesian logistic regression pooled over
  countries: Gaussian priors centred on zero (ridge), fit by Newton's method
  on weighted rows. And the scores the onset evaluation reports (see
  docs/PHASE-CHANGES.md): ROC AUC, average precision, recall at an alert
  budget."
  (:require [bnm.math :as m]))

(set! *unchecked-math* true)

(defn- softplus ^double [^double e]
  (if (> e 0.0) (+ e (Math/log1p (Math/exp (- e)))) (Math/log1p (Math/exp e))))

(defn fit
  "Coefficients for `rows` ({:x double[k] :y bool :w weight}), x[0] the
  intercept, which is left free; the others have prior precision :prior on
  the scale of their standard deviation in the rows, so the prior does not
  depend on units. Damped Newton steps, each halved until the penalized
  likelihood improves, until they are small or :iterations (default 25) have
  run. :start gives coefficients to start from."
  ^doubles [rows ^long k {:keys [prior start iterations] :or {prior 1.0 iterations 25}}]
  (let [rows (vec rows)
        n (count rows)
        prior (double prior)
        ;; weighted mean and sd of each input
        mean (double-array k) sd (double-array k 1.0)
        wsum (reduce + (map #(double (:w %)) rows))
        _ (doseq [{:keys [^doubles x w]} rows]
            (dotimes [j k] (aset mean j (+ (aget mean j) (* (double w) (aget x j))))))
        _ (dotimes [j k] (aset mean j (/ (aget mean j) (Math/max wsum 1e-12))))
        _ (let [v (double-array k)]
            (doseq [{:keys [^doubles x w]} rows]
              (dotimes [j k] (let [d (- (aget x j) (aget mean j))] (aset v j (+ (aget v j) (* (double w) d d))))))
            (dotimes [j k]
              (let [s (Math/sqrt (/ (aget v j) (Math/max wsum 1e-12)))]
                (aset sd j (if (> s 1e-9) s 1.0)))))
        _ (do (aset mean 0 0.0) (aset sd 0 1.0))
        z (mapv (fn [{:keys [^doubles x y w]}]
                  (let [zx (double-array k)]
                    (aset zx 0 1.0)
                    (loop [j 1] (when (< j k) (aset zx j (/ (- (aget x j) (aget mean j)) (aget sd j))) (recur (inc j))))
                    [zx (if y 1.0 0.0) (double w)]))
                rows)
        ;; the start, on the standardized scale
        b (double-array k)
        _ (when start
            (let [^doubles st start]
              (aset b 0 (+ (aget st 0) (loop [j 1 acc 0.0] (if (< j k) (recur (inc j) (+ acc (* (aget st j) (aget mean j)))) acc))))
              (loop [j 1] (when (< j k) (aset b j (* (aget st j) (aget sd j))) (recur (inc j))))))
        pen (fn ^double [j] (if (zero? (long j)) 1e-4 prior))
        objective (fn ^double [^doubles bb]
                    (- (reduce + (for [[^doubles zx y w] z]
                                   (let [e (loop [i 0 acc 0.0] (if (< i k) (recur (inc i) (+ acc (* (aget bb i) (aget zx i)))) acc))]
                                     (* (double w) (- (* (double y) e) (softplus e))))))
                       (* 0.5 (loop [j 0 acc 0.0] (if (< j k) (recur (inc j) (+ acc (* (pen j) (aget bb j) (aget bb j)))) acc)))))]
    (loop [it 0 obj (objective b)]
      (when (< it iterations)
        (let [h (double-array (* k k))
              g (double-array k)]
          (doseq [[^doubles zx y w] z]
            (let [e (loop [i 0 acc 0.0] (if (< i k) (recur (inc i) (+ acc (* (aget b i) (aget zx i)))) acc))
                  p (m/expit e)
                  r (* (double w) (- (double y) p))
                  s (* (double w) (Math/max 1e-9 (* p (- 1.0 p))))]
              (dotimes [i k]
                (let [xi (aget zx i)]
                  (aset g i (+ (aget g i) (* r xi)))
                  (dotimes [j k]
                    (aset h (+ (* i k) j) (+ (aget h (+ (* i k) j)) (* s xi (aget zx j)))))))))
          (dotimes [i k]
            (aset g i (- (aget g i) (* (pen i) (aget b i))))
            (aset h (+ (* i k) i) (+ (aget h (+ (* i k) i)) (pen i))))
          (let [step (m/solve-spd h g k)
                ;; halve the step until the objective improves
                [nb nobj] (loop [t 1.0 tries 0]
                            (let [cand (double-array k)]
                              (dotimes [i k] (aset cand i (+ (aget b i) (* t (aget step i)))))
                              (let [o (objective cand)]
                                (if (or (> o obj) (>= tries 30))
                                  [cand o]
                                  (recur (* 0.5 t) (inc tries))))))
                moved (loop [i 0 acc 0.0] (if (< i k) (recur (inc i) (Math/max acc (Math/abs (- (aget ^doubles nb i) (aget b i))))) acc))]
            (when (> nobj obj)
              (System/arraycopy nb 0 b 0 k))
            (when (and (> nobj obj) (> moved 1e-6))
              (recur (inc it) (Math/max obj nobj)))))))
    ;; back to the inputs' own scale
    (let [beta (double-array k)]
      (loop [j 1] (when (< j k) (aset beta j (/ (aget b j) (aget sd j))) (recur (inc j))))
      (aset beta 0 (- (aget b 0) (loop [j 1 acc 0.0] (if (< j k) (recur (inc j) (+ acc (* (aget beta j) (aget mean j)))) acc))))
      beta)))

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

(defn auc-within
  "AUC within each period, averaged with weights by positives, so it scores
  ranking across units only, not how rates move between periods: `rows` are
  [period score label]. nil when no period has both outcomes."
  [rows]
  (let [per (for [[_ g] (group-by first rows)
                  :let [a (auc (map second g) (map #(nth % 2) g))]
                  :when a]
              [a (count (filter #(nth % 2) g))])
        w (reduce + (map second per))]
    (when (pos? w)
      (/ (reduce + (map (fn [[a n]] (* a n)) per)) (double w)))))

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
