(ns bnm.invariants
  "Measures of a system's displacement that do not depend on which
  dimension carries it (docs/INVARIANTS.md). Each crisis has its own
  concrete expression (credit here, housing there, an oil shock, a wage
  squeeze), so a model of the specific channels flips between eras. These
  ask instead how far the system's state is from its own recent history,
  in how many dimensions at once, and whether its variability is rising.")

(defn- mean [xs] (/ (reduce + xs) (count xs)))

(defn- sd [xs]
  (let [m (mean xs)]
    (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (dec (count xs))))))

(defn stretch
  "x_i as a z-score against the known values in the `window` before it,
  given at least n of them; nil without x_i or with a flat history."
  [xs i window n]
  (when-let [x (get xs i)]
    (let [prev (keep #(get xs %) (range (max 0 (- i window)) i))]
      (when (>= (count prev) (max n 2))
        (let [s (sd prev)]
          (when (> s 1e-12) (/ (- x (mean prev)) s)))))))

(defn aggregate
  "Across dimensions' stretches (nils skipped, at least min-n known):
  :max the largest absolute one, :breadth the share past `threshold`,
  :norm the root mean square, :n how many were known."
  [zs {:keys [threshold min-n]}]
  (let [zs (keep identity zs)
        n (count zs)]
    (when (>= n min-n)
      {:max (apply max (map #(Math/abs (double %)) zs))
       :breadth (/ (count (filter #(> (Math/abs (double %)) threshold) zs)) (double n))
       :norm (Math/sqrt (/ (reduce + (map #(* % %) zs)) n))
       :n n})))

(defn variance-ratio
  "log of the variance of successive changes over the last `short` steps to
  that over the last `long`, at i; nil without enough history."
  [xs i short long]
  (when (>= i long)
    (let [diffs (fn [k] (keep (fn [j] (let [a (get xs (dec j)) b (get xs j)] (when (and a b) (- b a))))
                              (range (inc (- i k)) (inc i))))
          s (diffs short) l (diffs long)]
      (when (and (>= (count s) 3) (>= (count l) 6))
        (let [vs (sd s) vl (sd l)]
          (when (and (> vs 1e-12) (> vl 1e-12)) (* 2.0 (Math/log (/ vs vl)))))))))

(defn kernel
  "Self-exciting memory of past events at time t: the sum of the weights of
  events up to `through`, each decayed by exp(-age/tau). Several taus give
  a kernel whose shape a hazard can learn: a short one for contagion while
  a wave spreads, long ones for the memory that protects and then fades."
  [events t tau {:keys [through]}]
  (reduce-kv (fn [acc s w] (if (<= s through) (+ acc (* w (Math/exp (/ (- s t) tau)))) acc))
             0.0 events))
