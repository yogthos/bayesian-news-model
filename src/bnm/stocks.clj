(ns bnm.stocks
  "Named stocks for the hazard layer, read point in time from a series: the
  value known at a month, its change over a horizon, its volatility. The
  literature's crisis signals are of this kind: credit to GDP over three
  years, real house and share prices over three years, a price run-up and
  its volatility (docs/RESEARCH.md, \"Onset hazards\").

  A series is [period available value] entries, months as integers, sorted
  by period; values already transformed (logs for prices). One vintage per
  period, so a value's availability rises with its period.")

(set! *unchecked-math* true)

(defn series
  "A series from [period available value] entries."
  [entries]
  (let [es (vec (sort-by first entries))]
    {:period (long-array (map first es))
     :available (long-array (map second es))
     :value (double-array (map #(nth % 2) es))}))

(defn- last-index
  "The last index i with a[i] <= x, below `end`, or -1."
  ^long [^longs a ^long x ^long end]
  (loop [lo 0 hi (dec end) best -1]
    (if (> lo hi)
      best
      (let [mid (quot (+ lo hi) 2)]
        (if (<= (aget a mid) x)
          (recur (inc mid) hi mid)
          (recur lo (dec mid) best))))))

(defn- latest
  "Index of the latest value known at t, or -1 if none or staler than :stale
  months."
  ^long [{:keys [^longs available ^longs period]} ^long t stale]
  (let [i (last-index available t (alength available))]
    (if (and (>= i 0) (or (nil? stale) (<= (- t (aget period i)) (long stale))))
      i
      -1)))

(defn value
  ([s t] (value s t nil))
  ([s ^long t {:keys [stale]}]
   (let [i (latest s t stale)]
     (when (>= i 0) (aget ^doubles (:value s) i)))))

(defn change
  "The latest known value minus the one `months` periods before it (the
  latest at or before that period, no more than a year earlier still)."
  ([s t months] (change s t months nil))
  ([s ^long t ^long months {:keys [stale]}]
   (let [i (latest s t stale)]
     (when (>= i 0)
       (let [^longs ps (:period s)
             target (- (aget ps i) months)
             j (last-index ps target i)]
         (when (and (>= j 0) (> (aget ps j) (- target 12)))
           (- (aget ^doubles (:value s) i) (aget ^doubles (:value s) j))))))))

(defn volatility
  "Standard deviation of the changes between successive values in the
  `months` up to the latest known one, or nil with fewer than three."
  ([s t months] (volatility s t months nil))
  ([s ^long t ^long months {:keys [stale]}]
   (let [i (latest s t stale)]
     (when (>= i 0)
       (let [^longs ps (:period s)
             ^doubles vs (:value s)
             from (- (aget ps i) months)
             ds (loop [k i acc []]
                  (if (and (> k 0) (> (aget ps (dec k)) from))
                    (recur (dec k) (conj acc (- (aget vs k) (aget vs (dec k)))))
                    acc))
             n (count ds)]
         (when (>= n 3)
           (let [mu (/ (reduce + ds) n)]
             (Math/sqrt (/ (reduce + (map #(let [d (- % mu)] (* d d)) ds)) n)))))))))

(defn known
  "[period value] of every value published by t, oldest first."
  [{:keys [^longs available ^longs period ^doubles value]} ^long t]
  (let [i (last-index available t (alength available))]
    (mapv (fn [j] [(aget period j) (aget value j)]) (range (inc i)))))

(defn periods-where
  "Periods of the values published by t that satisfy `pred`."
  [s t pred]
  (vec (for [[p v] (known s t) :when (pred v)] p)))

(defn median-gap
  "The median gap between successive periods, nil with fewer than two."
  [periods]
  (let [gaps (vec (sort (map - (rest periods) periods)))
        n (count gaps)]
    (when (pos? n)
      (if (odd? n)
        (double (gaps (quot n 2)))
        (* 0.5 (+ (gaps (dec (quot n 2))) (gaps (quot n 2))))))))

(defn last-change
  "The period of the latest value published by t that differs from the one
  before it, nil if none."
  [s t]
  (some (fn [[[_ a] [p b]]] (when (not= a b) p))
        (reverse (partition 2 1 (known s t)))))
