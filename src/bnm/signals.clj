(ns bnm.signals
  "The shared transforms that turn a stock or a flow into a signal
  (bnm-cnf): one implementation each, with the choices that used to differ
  between copies as options.

  - `z-against`, `stretch`: a value against its own trailing window (the
    stretch of docs/INVARIANTS.md), with a minimum history, a spread
    floored absolutely or as a share of the level, a cap and a flatness
    guard.
  - `aggregate`: across dimensions' stretches, the largest, the share past
    a threshold (breadth) and the root mean square (norm).
  - `running-z`, `history-standardizer`: a value against everything
    observed at origins before t (Welford's running mean and spread, or
    the one-pass sums), read once enough values or periods are seen.

  Every option reproduces a copy it replaced to the bit
  (test/bnm/signals_test.clj).")

(defn- mean [xs] (/ (reduce + xs) (count xs)))

(defn- sample-sd [xs m]
  (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (dec (count xs)))))

(defn z-against
  "x against the values `past`: (x - mean) / spread, nil without x, with
  fewer than (max :min 2) values, or with a spread at or under :eps. The
  spread is the sample deviation, floored at :floor and at :floor-frac
  times the absolute mean when given, the result capped at +-:cap when
  given."
  [x past {:keys [min floor floor-frac cap eps] :or {min 2 eps 0.0}}]
  (when (and x (>= (count past) (max min 2)))
    (let [m (mean past)
          s (sample-sd past m)
          s (if floor-frac (Math/max s (* (double floor-frac) (Math/abs (double m)))) s)
          s (if floor (Math/max (double floor) s) s)]
      (when (> s (double eps))
        (let [z (/ (- x m) s)]
          (if cap (Math/max (- (double cap)) (Math/min (double cap) z)) z))))))

(defn stretch
  "The value of `series` (anything `get` reads: a vector by index, a map by
  time) at `at` against its known values in the :window before it, by
  `z-against`'s options."
  [series at {:keys [window] :as opts}]
  (when-let [x (get series at)]
    (z-against x (keep #(get series %) (range (- at window) at)) opts)))

(defn aggregate
  "Across dimensions' stretches (nils skipped, at least :min-n known):
  :max the largest absolute one, :breadth the share past :threshold,
  :norm the root mean square, :n how many were known."
  [zs {:keys [threshold min-n]}]
  (let [zs (keep identity zs)
        n (count zs)]
    (when (>= n min-n)
      {:max (apply max (map #(Math/abs (double %)) zs))
       :breadth (/ (count (filter #(> (Math/abs (double %)) threshold) zs)) (double n))
       :norm (Math/sqrt (/ (reduce + (map #(* % %) zs)) n))
       :n n})))

(defn- snapshots
  "sorted-map t -> [n s ss q] (double arrays, q a long array): the counts,
  the running means and squared deviations (:stable, Welford) or the sums
  and sums of squares, and the periods with a value, of everything at
  times before t. `by-t` is a sorted seq of [t rows], each row a vector of
  `width` entries, nil unknown."
  [by-t width stable]
  (loop [[[t rows] & more] by-t n (double-array width) s (double-array width) ss (double-array width) q (long-array width) acc (sorted-map)]
    (if-not t
      acc
      (let [acc (assoc acc t [(aclone n) (aclone s) (aclone ss) (aclone q)])
            seen (boolean-array width)]
        (doseq [v rows]
          (dotimes [j width]
            (when-let [x (nth v j)]
              (aset seen j true)
              (aset n j (inc (aget n j)))
              (if stable
                (let [m (aget s j) m' (+ m (/ (- x m) (aget n j)))]
                  (aset s j m') (aset ss j (+ (aget ss j) (* (- x m) (- x m')))))
                (do (aset s j (+ (aget s j) x)) (aset ss j (+ (aget ss j) (* x x))))))))
        (dotimes [j width] (when (aget seen j) (aset q j (inc (aget q j)))))
        (recur more n s ss q acc)))))

(defn- reader
  "(fn [t v]) reading `stats` (`snapshots`): each entry against the values
  before t, capped at 5, once it has more than :min-exclusive values
  (:count-by :values) or at least :min periods (:count-by :periods), else
  :missing (0.0); under :stable a spread under 1e-9 or under 1e-8 of the
  level reads :missing too."
  [stats width {:keys [count-by min min-exclusive stable missing] :or {missing 0.0}}]
  (fn [t v]
    (let [[^doubles n ^doubles s ^doubles ss ^longs q] (or (some-> (rsubseq stats <= t) first val)
                                                           [(double-array width) (double-array width) (double-array width) (long-array width)])]
      (vec (for [j (range width)
                 :let [x (nth v j)
                       k (aget n j)
                       ready? (case count-by
                                :values (> k (double min-exclusive))
                                :periods (>= (aget q j) (long min)))]]
             (if (and x ready?)
               (if stable
                 (let [mu (aget s j)
                       sd (Math/sqrt (/ (aget ss j) k))]
                   (if (> sd (Math/max 1e-9 (* 1e-8 (Math/abs mu))))
                     (Math/max -5.0 (Math/min 5.0 (/ (- x mu) sd)))
                     missing))
                 (let [mu (/ (aget s j) k)
                       sd (Math/sqrt (Math/max 1e-12 (- (/ (aget ss j) k) (* mu mu))))]
                   (Math/max -5.0 (Math/min 5.0 (/ (- x mu) sd)))))
               missing))))))

(defn running-z
  "A standardizer over `samples`, [t vector] pairs: (fn [t v]) giving v
  against the values at times before t (`reader`'s options)."
  [samples opts]
  (let [width (count (second (first samples)))
        by-t (sort-by key (group-by first samples))]
    (reader (snapshots (map (fn [[t xs]] [t (map second xs)]) by-t) width (:stable opts)) width opts)))

(defn history-standardizer
  "bnm-11y, :lead-std :history: (fn [t v]) standardizing the leading
  columns `v` (lead-f's entries at positions `ix`) against the gauges' own
  values at every quarter end from month `from` (the node's start) to
  `end` over `units`, each as known then, through the quarter ends before
  t; capped at 5, 0 for a missing value. A column is read once it has 20
  quarter ends of history: the node's standardizer counts rows, so a
  world gauge, one value per origin repeated at every country, was
  standardized against two or three origins. `missing` (0.0 by default) is
  what an unread value, or one with too short a history, reads as: the
  regime filter (bnm-56c.40) takes nil, so it drops out."
  ([lead-f units ix from end] (history-standardizer lead-f units ix from end 0.0))
  ([lead-f units ix from end missing]
   (let [w (count ix)
         ts (range (+ from (mod (- 2 from) 3)) (inc end) 3)
         by-t (for [t ts] [t (for [u units :let [l (lead-f u t)]] (mapv #(nth l %) ix))])]
     (reader (snapshots by-t w true) w {:count-by :periods :min 20 :stable true :missing missing}))))
