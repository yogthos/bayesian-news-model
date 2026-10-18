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

(defn hedge-weights
  "Exponential weights of forecasters by their own record (Hedge; the online
  expert weighting of Fouliard, Howell, Rey & Stavrakeva for crisis
  forecasts): each weight proportional to exp(eta x its weighted log score
  per weighted outcome), so a forecaster a nat per onset ahead of another
  gets e^eta times its weight, however many rows there are."
  ^doubles [ps ys ws eta]
  (let [m (count (first ps))
        ls (double-array m)
        pos (reduce + (map (fn [y w] (if y (double w) 0.0)) ys ws))]
    (doseq [[row y w] (map vector ps ys ws)
            j (range m)
            :let [p (min (max (double (nth row j)) 1e-6) (- 1.0 1e-6))]]
      (aset ls j (+ (aget ls j) (* (double w) (Math/log (if y p (- 1.0 p)))))))
    (let [scale (/ (double eta) (max pos 1.0))
          top (apply max (seq ls))
          e (double-array (map #(Math/exp (* scale (- % top))) ls))
          tot (reduce + (seq e))]
      (double-array (map #(/ % tot) e)))))

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

(defn recall-parts
  "{period [hits positives]} at the top `budget` fraction of each period's
  rows ([period score label]) by score; rows tied at the cutoff share the
  slots left, so the order they come in does not matter."
  [rows budget]
  (into {} (for [[q g] (group-by first rows)
                 :let [k (long (Math/ceil (* budget (count g))))]]
             [q [(loop [[[_ tied] & more] (sort-by #(- (double (key %))) (group-by #(double (second %)) g))
                        left k acc 0.0]
                   (if (or (nil? tied) (<= left 0))
                     acc
                     (let [n (count tied) hits (count (filter #(nth % 2) tied))
                           t (min n left)]
                       (recur more (- left t) (+ acc (* hits (/ t (double n))))))))
                 (count (filter #(nth % 2) g))]])))

(defn recall-at-budget
  "Of the positives, the share among the top `budget` fraction of rows by
  score in their period: `rows` are [period score label]. Rows tied at the
  cutoff share the slots left (`recall-parts`)."
  [rows budget]
  (let [parts (vals (recall-parts rows budget))
        pos (reduce + (map second parts))]
    (when (pos? pos)
      (/ (reduce + (map first parts)) (double pos)))))

(defn paired-recall-gain
  "`paired-gain` for recall at `budget` on rows [period p-model p-bench
  outcome], from per-period hits, so each resample sums years instead of
  rescoring rows."
  [rows budget reps seed]
  (let [pa (recall-parts (map (fn [[q a _ y]] [q a y]) rows) budget)
        pb (recall-parts (map (fn [[q _ b y]] [q b y]) rows) budget)
        ;; per year: [model hits, bench hits, positives]
        blocks (vec (for [[_ qs] (group-by #(quot % 4) (keys pa))]
                      (reduce (fn [[x y z] q] [(+ x (first (pa q))) (+ y (first (pb q))) (+ z (second (pa q)))])
                              [0.0 0.0 0.0] qs)))
        score (fn [bs] (let [[x y z] (reduce #(mapv + %1 %2) [0.0 0.0 0.0] bs)]
                         (if (pos? z) (/ (- x y) z) 0.0)))
        k (count blocks)
        g (m/rng seed)
        diffs (vec (sort (for [_ (range reps)]
                           (score (map (fn [_] (blocks (min (dec k) (long (* k (m/next-double! g)))))) (range k))))))
        at (fn [q] (nth diffs (min (dec (count diffs)) (long (* q (count diffs))))))]
    {:diff (score blocks) :lo (at 0.05) :hi (at 0.95)}))

(defn brier [ps ys]
  (/ (reduce + (map (fn [p y] (let [d (- (double p) (if y 1.0 0.0))] (* d d))) ps ys)) (double (count ps))))

(defn usefulness
  "Sarlin's (2013) relative usefulness of probabilities ps for outcomes ys,
  for a policymaker who weighs missed events by mu and false alarms by
  1 - mu: the share of the loss of ignoring the forecast (always or never
  acting, whichever is cheaper) that the best threshold saves. 1 is a
  perfect forecast, 0 one worth nothing. Every threshold is tried in one
  sweep down the sorted forecasts."
  [ps ys mu]
  (let [n (count ys)
        pos (count (filter true? ys))
        neg (- n pos)
        p1 (/ pos (double n)) p2 (- 1.0 p1)
        loss (fn [tp fp]
               (+ (* mu p1 (if (pos? pos) (/ (- pos tp) (double pos)) 0.0))
                  (* (- 1.0 mu) p2 (if (pos? neg) (/ fp (double neg)) 0.0))))
        ;; alarms at threshold p are all forecasts >= p: walk the groups of
        ;; equal forecasts from the highest down
        groups (partition-by first (sort-by #(- (double (first %))) (map vector ps ys)))
        best (loop [[g & more] groups tp 0 fp 0 best (loss 0 0)]
               (if-not g
                 best
                 (let [t (count (filter #(true? (second %)) g))
                       tp (+ tp t) fp (+ fp (- (count g) t))]
                   (recur more tp fp (min best (loss tp fp))))))
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

(defn cusum
  "A risk-adjusted Bernoulli CUSUM (Steiner, Cook, Farewell & Treasure 2000)
  watching forecasts ps against outcomes ys, in order, for a drift of the
  odds by `ratio` (above 1: events more frequent than forecast; below 1:
  less): S <- max(0, S + log-likelihood ratio of the outcome). Sequential, so
  it watches calibration continuously without the false alarms of repeated
  tests. {:max the largest S, :last S now, :signal the index where S first
  crossed h (default 4.0), or nil}. When outcomes overlap (each event inside
  `overlap` successive forecast windows), each increment counts 1/overlap,
  so the evidence is counted about once."
  [ps ys ratio & {:keys [h overlap] :or {h 4.0 overlap 1}}]
  (let [lr (Math/log ratio)
        k (/ 1.0 (double overlap))]
    (loop [[p & ps] ps [y & ys] ys i 0 s 0.0 mx 0.0 sig nil]
      (if (nil? p)
        {:max mx :last s :signal sig}
        (let [p (min (max (double p) 1e-6) (- 1.0 1e-6))
              ;; log of P(y | odds x ratio) / P(y | p)
              w (* k (- (if (true? y) lr 0.0) (Math/log (+ 1.0 (* p (- ratio 1.0))))))
              s (max 0.0 (+ s w))]
          (recur ps ys (inc i) s (max mx s) (or sig (when (> s h) i))))))))

(def recalibration-discount
  "Yearly discount of the outcomes a drift check refits on: a half-life of
  about three years, quick enough to follow a lasting shift."
  0.8)

(defn recalibration-gain
  "Whether a drift is worth refitting for, out of sample: the mean log score,
  over rows [quarter p outcome] with quarter >= `from`, of shifting each
  year's forecasts by an intercept on the logit refit on the outcomes known
  by the year's start (a row is known `overlap` quarters on), discounted
  yearly, minus that of the forecasts as given. Positive when the shift has
  lasted; negative when outcomes come in waves a refit only chases. nil
  without outcomes to score."
  [rows from {:keys [overlap discount] :or {overlap 1 discount recalibration-discount}}]
  (let [clip #(min (max (double %) 1e-6) (- 1.0 1e-6))
        logit #(let [p (clip %)] (Math/log (/ p (- 1.0 p))))
        sig #(/ 1.0 (+ 1.0 (Math/exp (- %))))
        ll (fn [p y] (Math/log (if y p (- 1.0 p))))
        intercept (fn [known q0]
                    ;; Newton on the weighted log likelihood, with a unit prior at 0
                    (loop [a 0.0 it 0]
                      (let [[g h] (reduce (fn [[g h] [q p y]]
                                            (let [w (Math/pow discount (/ (- q0 q) 4.0))
                                                  r (sig (+ a (logit p)))]
                                              [(+ g (* w (- (if y 1.0 0.0) r))) (+ h (* w r (- 1.0 r)))]))
                                          [(- a) 1.0] known)
                            d (/ g h)]
                        (if (or (> it 30) (< (Math/abs d) 1e-9)) (+ a d) (recur (+ a d) (inc it))))))
        scored (filter #(>= (first %) from) rows)]
    (when (seq scored)
      (let [diffs (for [[yr this] (group-by #(quot (first %) 4) scored)
                        :let [q0 (* 4 yr)
                              a (intercept (filter #(<= (+ (first %) overlap) q0) rows) q0)]
                        [_ p y] this]
                    (- (ll (clip (sig (+ a (logit p)))) y) (ll (clip p) y)))]
        (/ (reduce + diffs) (double (count diffs)))))))

(defn stack-out-of-sample
  "Rows [quarter ps outcome], ps the forecasters' probabilities, forecast
  out of sample by a stack (`fit-stack`): each year from quarter `from` on
  by one fit on the rows known by its start (a row is known `overlap`
  quarters on), discounted yearly by `discount`. Returns [quarter p outcome]
  for those years, with whatever else each row carried after its outcome.
  With :by, a function of a row, each group it makes gets its own stack."
  [rows from {:keys [overlap discount prior by] :or {overlap 1 discount 1.0 prior 1.0}}]
  (if by
    (vec (mapcat #(stack-out-of-sample % from {:overlap overlap :discount discount :prior prior})
                 (vals (group-by by rows))))
    (vec (for [[yr this] (sort-by key (group-by #(quot (first %) 4) (filter #(>= (first %) from) rows)))
               :let [q0 (* 4 yr)
                     known (filter #(<= (+ (first %) overlap) q0) rows)
                     beta (fit-stack (map second known) (map #(nth % 2) known)
                                     (map #(Math/pow discount (/ (- q0 (first %)) 4.0)) known)
                                     {:prior prior})]
               [q ps y & more] this]
           (into [q (predict-stack beta ps) y] more)))))

(defn pick-model
  "The model whose forecasts, {model [[quarter p outcome] ...]}, have the best
  mean log score over the years lo-hi: chosen before the years held out, as
  the members' priors are. nil when none has rows there."
  [preds lo hi]
  (let [score (fn [rows]
                (let [in (filter #(<= lo (quot (first %) 4) hi) rows)]
                  (when (seq in)
                    (/ (reduce + (for [[_ p y] in
                                       :let [p (min (max (double p) 1e-6) (- 1.0 1e-6))]]
                                   (Math/log (if y p (- 1.0 p)))))
                       (double (count in))))))
        scored (keep (fn [[m rows]] (when-let [s (score rows)] [m s])) preds)]
    (when (seq scored) (key (apply max-key val (into {} scored))))))

(defn paired-gain
  "How much better a model scores than a benchmark on the same rows [period
  p-model p-bench outcome] by `metric` (a function of rows [period p
  outcome], higher better): {:diff :lo :hi}, the 90% interval resampling
  whole years (four periods) `reps` times (seeded), which keeps the
  correlation of countries within a year and of a year's quarters."
  [rows metric reps seed]
  (let [score (fn [rs] (- (or (metric (map (fn [[q a _ y]] [q a y]) rs)) 0.0)
                          (or (metric (map (fn [[q _ b y]] [q b y]) rs)) 0.0)))
        blocks (vec (vals (group-by #(quot (first %) 4) rows)))
        k (count blocks)
        g (m/rng seed)
        diffs (vec (sort (for [_ (range reps)]
                           (score (mapcat (fn [_] (blocks (min (dec k) (long (* k (m/next-double! g)))))) (range k))))))
        at (fn [q] (nth diffs (min (dec (count diffs)) (long (* q (count diffs))))))]
    {:diff (score rows) :lo (at 0.05) :hi (at 0.95)}))

(defn matched-concordance
  "Signal beyond a benchmark: each event (rows {:period :p-model :p-bench
  :y}) is set against the `k` non-events of its period whose benchmark
  probabilities are closest to its own, and the share of those pairs the
  model ranks the event above is returned (ties count a half). A model that
  knows only what the benchmark knows scores about 0.5. nil without events."
  [rows k]
  (let [pairs (for [[_ g] (group-by :period rows)
                    :let [neg (filter (complement :y) g)]
                    e (filter :y g)
                    c (take k (sort-by #(Math/abs (- (double (:p-bench %)) (double (:p-bench e)))) neg))]
                (let [a (double (:p-model e)) b (double (:p-model c))]
                  (cond (> a b) 1.0 (= a b) 0.5 :else 0.0)))]
    (when (seq pairs) (/ (reduce + pairs) (double (count pairs))))))
