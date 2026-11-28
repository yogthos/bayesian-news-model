(ns bnm.stack
  "R1's causal non-negative stack of a node's group models (bnm-7yg.8.8),
  ported from docs/studies/combination.py for the 2027-Q1 shadow:

    logit p = offset + a + sum_g b_g e_g,  b_g >= 0

  e_g a live group model's log odds beyond the node's mean offset (0 where
  the group is not live), the offset the log odds of the frailty's offsets'
  mixture; ridge 1 on each b, a free; fitted by Newton's method with an
  active set (pin the negative weights to 0, refit the rest), on the rows
  known by the origin from 1975 on, and only with at least five onsets
  known. The forecast is integrated over the row's offsets, as the node's."
  (:require [augur.math :as am]))

(def ^:private eps 1e-6)
(def ^:private train-from (* 12 1975))
(def ^:private min-onsets 5)
(def ^:private lam 1.0)

(defn- expit ^double [^double z] (/ 1.0 (+ 1.0 (Math/exp (- z)))))

(defn- logit-clipped ^double [^double p]
  (let [p (Math/min (- 1.0 eps) (Math/max eps p))]
    (Math/log (/ p (- 1.0 p)))))

(defn mean-offset
  "The offset whose probability is the offsets' average, clipped as the
  study's (p in [1e-6, 1 - 1e-6])."
  ^double [osz]
  (logit-clipped (reduce + (for [[w o] osz] (* (double w) (expit (double o)))))))

(defn deviations
  "Each group's log odds beyond the mean offset `o`, in `groups`' order;
  0 for a group with no weight or no forecast."
  [groups f w o]
  (mapv (fn [g]
          (let [fg (get f g) wg (double (get w g 0.0))]
            (if (and (pos? wg) (some? fg) (not (Double/isNaN (double fg))))
              (- (double fg) (double o))
              0.0)))
        groups))

(defn- ridge-logit
  "b for logit p = off + Z b, ridge `pen` per coefficient: Newton's method,
  60 steps at most, stopping when no step exceeds 1e-8."
  [zs ys offs pen]
  (let [k (count pen)
        n (count ys)]
    (loop [b (double-array k) it 0]
      (if (= it 60)
        (vec b)
        (let [g (double-array k)
              h (double-array (* k k))]
          (dotimes [i n]
            (let [^doubles z (nth zs i)
                  eta (loop [j 0 s (double (nth offs i))] (if (< j k) (recur (inc j) (+ s (* (aget z j) (aget b j)))) s))
                  p (expit eta)
                  r (- p (double (nth ys i)))
                  v (* p (- 1.0 p))]
              (dotimes [j k]
                (aset g j (+ (aget g j) (* (aget z j) r)))
                (dotimes [l k]
                  (aset h (+ (* j k) l) (+ (aget h (+ (* j k) l)) (* v (aget z j) (aget z l))))))))
          (dotimes [j k]
            (aset g j (+ (aget g j) (* (double (nth pen j)) (aget b j))))
            (aset h (+ (* j k) j) (+ (aget h (+ (* j k) j)) (double (nth pen j)) 1e-9)))
          (let [step (am/solve-spd h g k)
                nb (double-array k)]
            (dotimes [j k] (aset nb j (- (aget b j) (aget step j))))
            (if (< (reduce max (map #(Math/abs (double %)) step)) 1e-8)
              (vec nb)
              (recur nb (inc it)))))))))

(defn fit
  "{:a :b}: the stack fitted to rows of deviations `es` (vectors), outcomes
  `ys` (1.0 or 0.0) and offsets `offs`."
  [es ys offs]
  (let [k (count (first es))]
    (loop [active (vec (repeat k true))]
      (if (some true? active)
        (let [ix (vec (keep-indexed (fn [j a] (when a j)) active))
              zs (mapv (fn [e] (double-array (cons 1.0 (map #(nth e %) ix)))) es)
              b (ridge-logit zs ys offs (vec (cons 0.0 (repeat (count ix) lam))))
              w (reduce (fn [w [j bj]] (assoc w j bj)) (vec (repeat k 0.0)) (map vector ix (rest b)))]
          (if (every? #(>= % 0.0) w)
            {:a (first b) :b w}
            (recur (mapv #(and %1 (> %2 0.0)) active w))))
        {:a (first (ridge-logit (mapv (fn [_] (double-array [1.0])) es) ys offs [0.0])) :b (vec (repeat k 0.0))}))))

(defn fit-at
  "The stack at origin `t` from `rows` ({:e :off :y :origin :known-at}): the
  rows known by t issued from month `from` on (1975's January without);
  nil with fewer than five onsets among them."
  ([rows t] (fit-at rows t train-from))
  ([rows t from]
  (let [tr (filterv #(and (<= (long (:known-at %)) (long t)) (>= (long (:origin %)) (long from))) rows)]
    (when (>= (reduce + (map :y tr)) min-onsets)
      (fit (mapv :e tr) (mapv :y tr) (mapv :off tr))))))

(defn forecast
  "p for deviations `e` under the stack `{:a :b}`, integrated over `osz`."
  ^double [{:keys [a b]} e osz]
  (let [eta (+ (double a) (reduce + (map * b e)))]
    (reduce + (for [[w o] osz] (* (double w) (expit (+ (double o) eta)))))))
