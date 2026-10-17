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
        ;; the standardized inputs, row-major in one array
        ^doubles z (double-array (* n k))
        ^doubles ys (double-array n)
        ^doubles ws (double-array n)
        ^doubles mean (double-array k) ^doubles sd (double-array k 1.0)
        _ (dotimes [r n]
            (let [{:keys [^doubles x y w]} (rows r)]
              (System/arraycopy x 0 z (* r k) k)
              (aset ys r (if y 1.0 0.0))
              (aset ws r (double w))))
        wsum (Math/max 1e-12 (loop [r 0 acc 0.0] (if (< r n) (recur (inc r) (+ acc (aget ws r))) acc)))
        _ (dotimes [r n]
            (let [w (aget ws r) o (* r k)]
              (dotimes [j k] (aset mean j (+ (aget mean j) (* w (aget z (+ o j))))))))
        _ (dotimes [j k] (aset mean j (/ (aget mean j) wsum)))
        _ (let [^doubles v (double-array k)]
            (dotimes [r n]
              (let [w (aget ws r) o (* r k)]
                (dotimes [j k] (let [d (- (aget z (+ o j)) (aget mean j))] (aset v j (+ (aget v j) (* w d d)))))))
            (dotimes [j k]
              (let [s (Math/sqrt (/ (aget v j) wsum))]
                (aset sd j (if (> s 1e-9) s 1.0)))))
        _ (do (aset mean 0 0.0) (aset sd 0 1.0))
        _ (dotimes [r n]
            (let [o (* r k)]
              (aset z o 1.0)
              (loop [j 1] (when (< j k) (aset z (+ o j) (/ (- (aget z (+ o j)) (aget mean j)) (aget sd j))) (recur (inc j))))))
        ;; the start, on the standardized scale
        ^doubles b (double-array k)
        _ (when start
            (let [^doubles st start]
              (aset b 0 (+ (aget st 0) (loop [j 1 acc 0.0] (if (< j k) (recur (inc j) (+ acc (* (aget st j) (aget mean j)))) acc))))
              (loop [j 1] (when (< j k) (aset b j (* (aget st j) (aget sd j))) (recur (inc j))))))
        ^doubles pen (double-array k prior)
        _ (aset pen 0 1e-4)
        eta (fn ^double [^doubles bb ^long r]
              (let [o (* r k)]
                (loop [i 0 acc 0.0] (if (< i k) (recur (inc i) (+ acc (* (aget bb i) (aget z (+ o i))))) acc))))
        objective (fn ^double [^doubles bb]
                    (- (loop [r 0 acc 0.0]
                         (if (< r n)
                           (let [e (eta bb r)]
                             (recur (inc r) (+ acc (* (aget ws r) (- (* (aget ys r) e) (softplus e))))))
                           acc))
                       (* 0.5 (loop [j 0 acc 0.0] (if (< j k) (recur (inc j) (+ acc (* (aget pen j) (aget bb j) (aget bb j)))) acc)))))]
    (loop [it 0 obj (objective b)]
      (when (< it iterations)
        (let [^doubles h (double-array (* k k))
              ^doubles g (double-array k)]
          (dotimes [r n]
            (let [o (* r k)
                  p (m/expit (eta b r))
                  w (aget ws r)
                  rr (* w (- (aget ys r) p))
                  s (* w (Math/max 1e-9 (* p (- 1.0 p))))]
              (dotimes [i k]
                (let [sxi (* s (aget z (+ o i)))
                      hi (* i k)]
                  (aset g i (+ (aget g i) (* rr (aget z (+ o i)))))
                  ;; upper triangle only
                  (loop [j i]
                    (when (< j k)
                      (aset h (+ hi j) (+ (aget h (+ hi j)) (* sxi (aget z (+ o j)))))
                      (recur (inc j))))))))
          (dotimes [i k]
            (loop [j (inc i)] (when (< j k) (aset h (+ (* j k) i) (aget h (+ (* i k) j))) (recur (inc j))))
            (aset g i (- (aget g i) (* (aget pen i) (aget b i))))
            (aset h (+ (* i k) i) (+ (aget h (+ (* i k) i)) (aget pen i))))
          (let [^doubles step (m/solve-spd h g k)
                ;; halve the step until the objective improves
                [nb nobj] (loop [t 1.0 tries 0]
                            (let [^doubles cand (double-array k)]
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
    (let [^doubles beta (double-array k)]
      (loop [j 1] (when (< j k) (aset beta j (/ (aget b j) (aget sd j))) (recur (inc j))))
      (aset beta 0 (- (aget b 0) (loop [j 1 acc 0.0] (if (< j k) (recur (inc j) (+ acc (* (aget beta j) (aget mean j)))) acc))))
      beta)))

(defn predict ^double [^doubles beta ^doubles x]
  (m/expit (loop [i 0 acc 0.0] (if (< i (alength beta)) (recur (inc i) (+ acc (* (aget beta i) (aget x i)))) acc))))

;; --- combining forecasters ------------------------------------------------------------------

(defn pool-weights
  "Weights of a linear pool of forecasters that maximize the weighted log
  score: `ps` gives each row's probabilities, one per forecaster. EM over the
  mixture weights, from equal ones."
  ^doubles [ps ys ws & {:keys [iterations] :or {iterations 200}}]
  (let [ps (vec ps) ys (vec ys) ws (vec ws)
        m (count (first ps))
        ^doubles w (double-array m (/ 1.0 m))]
    (dotimes [_ iterations]
      (let [^doubles acc (double-array m)
            tot (loop [r 0 tot 0.0]
                  (if (< r (count ps))
                    (let [row (ps r) y (ys r) wr (double (ws r))
                          lik (fn ^double [j] (let [p (double (nth row j))] (if y p (- 1.0 p))))
                          mix (loop [j 0 s 0.0] (if (< j m) (recur (inc j) (+ s (* (aget w j) (lik j)))) s))]
                      (when (> mix 0.0)
                        (dotimes [j m] (aset acc j (+ (aget acc j) (/ (* wr (aget w j) (lik j)) mix)))))
                      (recur (inc r) (+ tot wr)))
                    tot))]
        (dotimes [j m] (aset w j (/ (aget acc j) tot)))))
    w))

(defn pool ^double [^doubles w ps]
  (loop [j 0 s 0.0 [p & more] (seq ps)]
    (if p (recur (inc j) (+ s (* (aget w j) (double p))) more) s)))

(defn- logit ^double [^double p]
  (let [p (Math/min (Math/max p 1e-6) (- 1.0 1e-6))]
    (Math/log (/ p (- 1.0 p)))))

(defn- stack-x ^doubles [ps]
  (double-array (cons 1.0 (map #(logit (double %)) ps))))

(defn fit-stack
  "A log-linear stack: a logistic regression on the forecasters' logits,
  which can combine one that knows where with one that knows when."
  ^doubles [ps ys ws opts]
  (let [rows (map (fn [p y w] {:x (stack-x p) :y y :w w}) ps ys ws)]
    (fit rows (inc (count (first ps))) opts)))

(defn predict-stack ^double [^doubles beta ps]
  (predict beta (stack-x ps)))

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

(defn usefulness
  "Sarlin's (2013) relative usefulness of probabilities ps for outcomes ys,
  for a policymaker who weighs missed events by mu and false alarms by
  1 - mu: the share of the loss of ignoring the forecast (always or never
  acting, whichever is cheaper) that the best threshold saves. 1 is a
  perfect forecast, 0 one worth nothing."
  [ps ys mu]
  (let [n (count ys)
        pos (count (filter true? ys))
        neg (- n pos)
        p1 (/ pos (double n)) p2 (- 1.0 p1)
        pairs (map vector ps ys)
        loss (fn [thr]
               (let [alarm (filter #(>= (first %) thr) pairs)
                     tp (count (filter #(true? (second %)) alarm))
                     fp (- (count alarm) tp)
                     t1 (if (pos? pos) (/ (- pos tp) (double pos)) 0.0)
                     t2 (if (pos? neg) (/ fp (double neg)) 0.0)]
                 (+ (* mu p1 t1) (* (- 1.0 mu) p2 t2))))
        best (apply min (loss Double/POSITIVE_INFINITY) (map loss (distinct ps)))
        ignore (min (* mu p1) (* (- 1.0 mu) p2))]
    (if (pos? ignore) (/ (- ignore best) ignore) 0.0)))

(defn block-bootstrap-auc
  "A 90% interval for the pooled AUC of rows [period p y], resampling whole
  periods with replacement `reps` times (seeded), so that the correlation
  within a period is kept."
  [rows reps seed]
  (let [blocks (vec (vals (group-by first rows)))
        k (count blocks)
        g (m/rng seed)
        aucs (sort (for [_ (range reps)
                         :let [rs (mapcat (fn [_] (blocks (min (dec k) (long (* k (m/next-double! g)))))) (range k))
                               ys (map #(nth % 2) rs)]
                         :when (and (some true? ys) (some false? ys))]
                     (auc (map second rs) ys)))
        at (fn [q] (nth aucs (min (dec (count aucs)) (long (* q (count aucs))))))]
    [(at 0.05) (at 0.95)]))
