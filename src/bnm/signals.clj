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
  (test/bnm/signals_test.clj).

  And signals declared as data (docs/SIGNALS.md): `read-signal` reads one
  point in time from the slow table's series, `assemble` builds a node's
  row, names, groups and priors from declarations."
  (:require [bnm.stocks :as sk]))

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

;; --- signals declared as data (docs/SIGNALS.md) ------------------------------

(def reads
  "The reads a declaration may name."
  #{:value :change :volatility :stretch :turn})

(defn- fresh?
  "Whether the latest published period `p` is within :stale months of t."
  [stale t p]
  (or (nil? stale) (<= (- t p) (long stale))))

(defn- stretch-of-obs
  "The stretch of observation i of `vs` (published values, oldest first)
  against the :window observations before it."
  [vs i opts]
  (when (>= i 0) (stretch vs i opts)))

(defn- read-series
  "One read of series `s` at origin t, nil when nothing usable is published."
  [s [kind arg] stale t]
  (case kind
    :value (sk/value s t {:stale stale})
    :change (sk/change s t (long arg) {:stale stale})
    :volatility (sk/volatility s t (long arg) {:stale stale})
    (:stretch :turn)
    (let [known (sk/known s t)
          vs (mapv second known)
          i (dec (count vs))]
      (when (and (>= i 0) (fresh? stale t (first (peek known))))
        (case kind
          :stretch (stretch-of-obs vs i arg)
          :turn (let [z1 (stretch-of-obs vs i arg)
                      z0 (stretch-of-obs vs (dec i) arg)
                      th (double (:threshold arg 1.0))]
                  (when (and z0 z1)
                    (if (and (>= (Math/abs (double z0)) th) (neg? (* (Math/signum (double z0)) (- z1 z0))))
                      (Math/abs (- (double z1) (double z0)))
                      0.0))))))))

(defn read-signal
  "Declaration `decl`'s value for `unit` at origin t from `db` ({[unit var]
  series}, bnm.named/load-series), nil when unknown. :lag reads it as at
  t less that many months (an exposure fixed before the shock)."
  [db {:keys [var unit read stale times lag]} row-unit t]
  (let [u (if (or (nil? unit) (= :own unit)) row-unit unit)
        t' (if lag (- t (long lag)) t)
        x (some-> (db [u var]) (read-series read stale t'))]
    (if (and x times)
      (some-> (read-signal db (assoc times :unit (or (:unit times) unit)) row-unit t) (* x))
      x)))

(def ops
  "The operators a signal expression may use."
  #{:mul :minus :div :neg :min :sum-or-zero :mean-of :fn})

(defn eval-signal
  "A signal expression for `unit` at origin t: a read (a map, `read-signal`)
  or [op & args]: :mul and :minus of two signals (nil if either is), :div
  by a constant, :neg, :min with a constant, :sum-or-zero of two signals
  (a missing one counts 0, nil when both are), :mean-of the known ones
  among several (nil when none is), :fn (fn [db unit t]) for a quantity
  across units (a world mean, a median)."
  [db e unit t]
  (if (map? e)
    (read-signal db e unit t)
    (let [[op & args] e
          ev #(eval-signal db % unit t)]
      (case op
        :mul (let [[a b] (map ev args)] (when (and a b) (* (double a) (double b))))
        :minus (let [[a b] (map ev args)] (when (and a b) (- (double a) (double b))))
        :div (some-> (ev (first args)) (/ (second args)))
        :neg (some-> (ev (first args)) -)
        :min (some-> (ev (first args)) (Math/min (double (second args))))
        :sum-or-zero (let [[a b] (map ev args)] (when (or a b) (+ (or a 0.0) (or b 0.0))))
        :mean-of (let [xs (keep ev args)] (when (seq xs) (/ (reduce + xs) (count xs))))
        :fn ((first args) db unit t)))))

(defn- signal-of
  "A declaration's expression: its :expr, or the declaration itself as a read."
  [decl]
  (or (:expr decl) decl))

(defn- check-decls [decls]
  (let [names (map :name decls)]
    (when-not (apply distinct? names)
      (throw (ex-info "bnm.signals: declaration names must be unique" {:names names})))
    (doseq [{:keys [name var read times] :as d} decls]
      (when-not (and name (or var (:expr d)))
        (throw (ex-info "bnm.signals: a declaration needs :name and :var or :expr" {:decl d})))
      (letfn [(walk [e]
                (if (map? e)
                  (doseq [r (cons (:read e) (some-> (:times e) :read list))]
                    (when-not (contains? reads (first r))
                      (throw (ex-info (str "bnm.signals: unknown read " (first r)) {:decl d}))))
                  (do (when-not (contains? ops (first e))
                        (throw (ex-info (str "bnm.signals: unknown operator " (first e)) {:decl d})))
                      (when-not (= :fn (first e)) (doseq [a (rest e) :when (or (map? a) (vector? a))] (walk a))))))]
        (walk (signal-of d))))))

(defn assemble
  "A node's inputs from declarations: {:row (fn [unit t] vector) :names
  :groups {domain [index ...]} :named-prior {name [mean variance]}}, in
  declaration order."
  [db decls]
  (check-decls decls)
  (let [decls (vec decls)]
    {:row (fn [unit t] (mapv #(eval-signal db (signal-of %) unit t) decls))
     :names (mapv :name decls)
     :groups (reduce (fn [g [i {:keys [domain]}]] (if domain (update g domain (fnil conj []) i) g))
                     {} (map-indexed vector decls))
     :named-prior (into {} (for [{:keys [name prior]} decls :when prior] [name prior]))}))
