(ns bnm.marginal
  "augur.hier/shared-marginal for a state of several dimensions and
  logistic rows, on flat double arrays (bnm-2oc). It is augur v0.24.0's
  shared-marginal-n operation for operation, so it returns the same
  numbers to the last bit (test/bnm/marginal_test.clj), several times
  faster: the frailty's regional and slope candidates call it twice per
  candidate and origin, and on persistent vectors it was all of a world
  run's time."
  (:require [augur.hier :as hier]))

(defn- row-terms!
  "Fills g (d) and w (d x d, row major) with the rows' gradient and minus
  Hessian at s, as augur.hier's shared-terms per row; returns the rows'
  log likelihood. With `g` nil only the log likelihood."
  ^double [^long d ^long n ^doubles ks ^doubles fs ^doubles ys ^doubles ws ^objects loads ^doubles s g w]
  (when g
    (java.util.Arrays/fill ^doubles g 0.0)
    (java.util.Arrays/fill ^doubles w 0.0))
  (loop [i 0 l 0.0]
    (if (= i n)
      l
      (let [^doubles a (aget loads i)
            z (loop [j 0 acc 0.0] (if (< j d) (recur (inc j) (+ acc (* (aget a j) (aget s j)))) acc))
            k (aget ks i) f (aget fs i) yy (aget ys i) wt (aget ws i)
            e (* k (+ f z))
            lp (- (* yy e) (if (> e 0.0) (+ e (Math/log1p (Math/exp (- e)))) (Math/log1p (Math/exp e))))]
        (when g
          (let [^doubles g g ^doubles w w
                p (/ 1.0 (+ 1.0 (Math/exp (- e))))
                y (* (* wt k) (- yy p))
                u (* (* (* (* wt k) k) p) (- 1.0 p))]
            (dotimes [j d]
              (aset g j (+ (aget g j) (* y (aget a j))))
              (dotimes [c d]
                (let [ix (+ (* j d) c)]
                  (aset w ix (+ (aget w ix) (* (* u (aget a j)) (aget a c)))))))))
        (recur (inc i) (+ l (* wt lp)))))))

(defn- dot ^double [^doubles a ^long off ^doubles x ^long d]
  (loop [j 0 acc 0.0] (if (< j d) (recur (inc j) (+ acc (* (aget a (+ off j)) (aget x j)))) acc)))

(defn- mat-vec
  "a x, a d x d row major."
  ^doubles [^doubles a ^doubles x ^long d]
  (let [out (double-array d)]
    (dotimes [i d] (aset out i (dot a (* i d) x d)))
    out))

(defn- plus-eye
  "a b + I, both d x d row major (each entry summed from 0.0 in k order)."
  ^doubles [^doubles a ^doubles b ^long d]
  (let [out (double-array (* d d))]
    (dotimes [i d]
      (dotimes [j d]
        (aset out (+ (* i d) j)
              (+ (loop [k 0 acc 0.0] (if (< k d) (recur (inc k) (+ acc (* (aget a (+ (* i d) k)) (aget b (+ (* k d) j))))) acc))
                 (if (= i j) 1.0 0.0)))))
    out))

(defn- rows-of ^objects [^doubles a ^long d]
  (let [r (object-array d)]
    (dotimes [i d] (aset r i (java.util.Arrays/copyOfRange a (int (* i d)) (int (* (inc i) d)))))
    r))

(defn- solve
  "x with a x = b, as augur.hier's solve (partial pivoting, the pivot
  chosen by max-key over the rows left, elimination over the augmented row)."
  ^doubles [^doubles a ^doubles b ^long n]
  (let [aug (object-array n)]
    (dotimes [i n]
      (let [r (double-array (inc n))]
        (System/arraycopy a (* i n) r 0 n)
        (aset r n (aget b i))
        (aset aug i r)))
    (dotimes [col n]
      (let [piv (apply max-key #(Math/abs (aget ^doubles (aget aug %) col)) (range col n))
            tmp (aget aug col)]
        (aset aug col (aget aug piv))
        (aset aug piv tmp)
        (let [^doubles pr (aget aug col) d (aget pr col)]
          (doseq [r (range (inc col) n)]
            (let [^doubles rr (aget aug r) f (/ (aget rr col) d)]
              (dotimes [j (inc n)] (aset rr j (- (aget rr j) (* f (aget pr j))))))))))
    (let [x (double-array n)]
      (doseq [i (reverse (range n))]
        (let [^doubles ri (aget aug i)]
          (aset x i (/ (- (aget ri n) (loop [j (inc i) acc 0.0] (if (< j n) (recur (inc j) (+ acc (* (aget ri j) (aget x j)))) acc)))
                       (aget ri i)))))
      x)))

(defn- log-det
  "As augur.hier's log-det (LU, the same pivots)."
  ^double [^doubles a ^long n]
  (let [m (rows-of a n)]
    (loop [col 0 acc 0.0]
      (if (= col n)
        acc
        (let [piv (apply max-key #(Math/abs (aget ^doubles (aget m %) col)) (range col n))
              tmp (aget m col)]
          (aset m col (aget m piv))
          (aset m piv tmp)
          (let [^doubles pr (aget m col) d (aget pr col)]
            (doseq [r (range (inc col) n)]
              (let [^doubles rr (aget m r) f (/ (aget rr col) d)]
                (dotimes [j n] (aset rr j (- (aget rr j) (* f (aget pr j)))))))
            (recur (inc col) (+ acc (Math/log (Math/abs d))))))))))

(defn shared-marginal-n
  "augur.hier/shared-marginal :logistic for rows sharing a state of
  several dimensions, s ~ N(m, v), each row adding its :load times s:
  {:z the mode, :v its variance, :ll the log marginal} (Laplace), the
  same numbers as augur v0.24.0's."
  [rows m v]
  (@#'hier/check-shared-n rows m v)
  (let [d (count m)
        rows (vec rows)
        n (count rows)
        loads (object-array (map #(double-array (map double (:load %))) rows))
        ks (double-array (map #(let [q (double (or (:q %) 0.0))] (/ 1.0 (Math/sqrt (+ 1.0 (/ (* Math/PI q) 8.0))))) rows))
        fs (double-array (map #(double (:f %)) rows))
        ys (double-array (map #(let [y (:y %)] (if (if (number? y) (pos? (double y)) (boolean y)) 1.0 0.0)) rows))
        ws (double-array (map #(double (or (:w %) 1.0)) rows))
        ma (double-array (map double m))
        va (double-array (for [r v x r] (double x)))
        g (double-array d) w (double-array (* d d))
        post-ll (fn ^double [^double l ^doubles u]
                  (let [vu (mat-vec va u d)]
                    (- l (* 0.5 (loop [j 0 acc 0.0] (if (< j d) (recur (inc j) (+ acc (* (aget u j) (aget vu j)))) acc))))))
        ;; Newton in the form (I + V W) delta = V g - (s - m), each step cut to
        ;; a length of 5 and halved until the log posterior does not fall, the
        ;; prior's quadratic u'Vu carried along (as augur)
        s (loop [i 0 ^doubles s ma ^doubles u (double-array d)]
            (let [l (row-terms! d n ks fs ys ws loads s g w)
                  vg (mat-vec va g d)
                  rhs (double-array d)
                  _ (dotimes [j d] (aset rhs j (- (aget vg j) (- (aget s j) (aget ma j)))))
                  ^doubles delta (solve (plus-eye va w d) rhs d)
                  big (double (reduce max 0.0 (map #(Math/abs (double %)) delta)))
                  c (if (> big 5.0) (/ 5.0 big) 1.0)
                  _ (when (< c 1.0) (dotimes [j d] (aset delta j (* c (aget delta j)))))
                  gu (double-array d)
                  _ (dotimes [j d] (aset gu j (- (aget g j) (aget u j))))
                  ^doubles du (solve (plus-eye w va d) gu d)
                  _ (dotimes [j d] (aset du j (* c (aget du j))))
                  f0 (post-ll l u)
                  tol (* 1e-10 (+ 1.0 (Math/abs f0)))
                  step (loop [t 1.0]
                         (let [s' (double-array d) u' (double-array d)]
                           (dotimes [j d]
                             (aset s' j (+ (aget s j) (* t (aget delta j))))
                             (aset u' j (+ (aget u j) (* t (aget du j)))))
                           (cond (>= (post-ll (row-terms! d n ks fs ys ws loads s' nil nil) u') (- f0 tol)) [t s' u']
                                 (< t 1e-6) nil
                                 :else (recur (* 0.5 t)))))]
              (if-let [[t s' u'] step]
                (if (or (>= i 100) (< (* (double t) (min big 5.0)) 1e-10)) s' (recur (inc i) s' u'))
                s)))
        ^doubles s s
        l (row-terms! d n ks fs ys ws loads s g w)
        a (plus-eye va w d)
        ;; the posterior variance (I + V W)^-1 V, column by column, symmetrized
        cols (object-array (for [j (range d)]
                             (let [b (double-array d)]
                               (dotimes [i d] (aset b i (aget va (+ (* i d) j))))
                               (solve a b d))))
        post (vec (for [i (range d)]
                    (vec (for [j (range d)
                               :let [x (* 0.5 (+ (aget ^doubles (aget cols j) i) (aget ^doubles (aget cols i) j)))]]
                           (if (= i j) (Math/max 0.0 x) x)))))]
    {:z (vec s) :v post
     :ll (- l (* 0.5 (loop [j 0 acc 0.0] (if (< j d) (recur (inc j) (+ acc (* (- (aget s j) (aget ma j)) (aget g j)))) acc)))
            (* 0.5 (log-det a d)))}))
