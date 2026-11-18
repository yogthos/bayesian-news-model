(ns bnm.stat
  "Small statistics shared by the model and the tools. Each function keeps
  the exact arithmetic of the copies it replaced (denominators, guards, the
  type of what comes back), so swapping one in changes no number.")

(defn mean
  "The mean of `xs` as a double: NaN when empty."
  [xs]
  (/ (reduce + xs) (double (count xs))))

(defn- sum-sq-dev [xs m] (reduce + (map #(let [d (- % m)] (* d d)) xs)))

(defn sd
  "The sample standard deviation of `xs` (n - 1)."
  [xs]
  (let [m (mean xs)] (Math/sqrt (/ (sum-sq-dev xs m) (dec (count xs))))))

(defn sd-pop
  "The population standard deviation of `xs` (n)."
  [xs]
  (let [m (mean xs)] (Math/sqrt (/ (sum-sq-dev xs m) (count xs)))))

(defn median
  "The median of `xs`, the middle value itself when the count is odd and
  the mean of the middle two (a double) when even; nil when empty."
  [xs]
  (let [v (vec (sort xs)) n (count v) h (quot n 2)]
    (when (pos? n)
      (if (odd? n) (v h) (/ (+ (v (dec h)) (v h)) 2.0)))))

(defn median-double
  "The median of `xs` as a double (the mean of the middle two when even),
  nil when empty."
  [xs]
  (let [v (vec (sort xs)) n (count v) h (quot n 2)]
    (when (pos? n)
      (if (odd? n)
        (double (v h))
        (/ (+ (double (v (dec h))) (double (v h))) 2.0)))))

(defn pearson
  "The Pearson correlation of `xs` and `ys`, unguarded: a flat series
  divides by zero."
  [xs ys]
  (let [n (count xs)
        mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sxx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
        syy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
    (/ sxy (Math/sqrt (* sxx syy)))))

(defn pearson-or-nil
  "The Pearson correlation of `xs` and `ys`, nil with fewer than three
  points or a flat series."
  [xs ys]
  (let [n (count xs)]
    (when (> n 2)
      (let [mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
            sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
            sxx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
            syy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
        (when (and (pos? sxx) (pos? syy)) (/ sxy (Math/sqrt (* sxx syy))))))))

(defn brier-one
  "The squared error of the probability `p` for the boolean outcome `y`."
  ^double [p y]
  (let [d (- (double p) (if y 1.0 0.0))] (* d d)))

(defn mean-log-score
  "The mean log probability of the outcomes of `preds` ([_ p y] each, p
  clamped to [1e-6, 1 - 1e-6]); nil when empty."
  [preds]
  (when (seq preds)
    (/ (reduce + (for [[_ p y] preds
                       :let [p (min (max (double p) 1e-6) (- 1.0 1e-6))]]
                   (Math/log (if y p (- 1.0 p)))))
       (double (count preds)))))

(defn rows-mean-log
  "The mean log probability of `ps` for the outcomes (:y) of `rows`,
  unclamped; 0 when empty."
  [rows ps]
  (/ (reduce + (map (fn [r p] (Math/log (if (:y r) p (- 1.0 p)))) rows ps)) (max 1 (count rows))))

(defn rows-brier
  "The mean squared error of `ps` for the outcomes (:y) of `rows`; 0 when
  empty."
  [rows ps]
  (/ (reduce + (map (fn [r p] (Math/pow (- (if (:y r) 1.0 0.0) p) 2)) rows ps)) (max 1 (count rows))))
