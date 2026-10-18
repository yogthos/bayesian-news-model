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

(defn correlation
  "Pearson correlation of paired values; nil when either is flat."
  [xs ys]
  (let [n (count xs) mx (mean xs) my (mean ys)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sxx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
        syy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
    (when (and (> n 1) (> sxx 1e-12) (> syy 1e-12)) (/ sxy (Math/sqrt (* sxx syy))))))

(defn synchrony
  "How much the series move together: the mean absolute correlation of
  their changes over the `window` steps ending at i, over the pairs with at
  least n changes in common (Kritzman et al.'s absorption ratio in simple
  form; China's CISS weights stress by the same co-movement)."
  [series i window n]
  (let [changes (fn [xs] (into {} (keep (fn [j] (let [a (get xs (dec j)) b (get xs j)] (when (and a b) [j (- b a)])))
                                        (range (max 1 (inc (- i window))) (inc i)))))
        cs (filterv #(>= (count %) n) (map changes series))
        rs (for [p (range (count cs)) q (range (inc p) (count cs))
                 :let [js (filter (cs q) (keys (cs p)))]
                 :when (>= (count js) n)
                 :let [r (correlation (map (cs p) js) (map (cs q) js))]
                 :when r]
             (Math/abs (double r)))]
    (when (seq rs) (mean rs))))

(defn autocorrelation
  "Lag-1 autocorrelation of xs over the `window` steps ending at i, from the
  pairs where both values are known (at least n). Deviations that linger
  mean a weak restoring force: critical slowing down (Scheffer et al. 2009;
  Veraart et al. 2012), which needs no knowledge of which loop restores."
  [xs i window n]
  (let [pairs (keep (fn [j] (let [a (get xs (dec j)) b (get xs j)] (when (and a b) [a b])))
                    (range (max 1 (inc (- i window))) (inc i)))]
    (when (>= (count pairs) n)
      (correlation (map first pairs) (map second pairs)))))

(defn boom
  "A credit-financed asset boom (Greenwood, Hanson, Shleifer & Sorensen, JF
  2022: credit and asset growth together give about a 40% chance of a crisis
  within three years against 7%; either alone forecasts modestly), judged
  against the growth the panel had seen before: {:zone 1.0 when credit growth
  is in the top fifth of `past-credit` and asset growth in the top third of
  `past-asset`, else 0.0; :interaction the product of their standardized
  excesses above the past means, zero when either is below}. nil with fewer
  than 30 past values of either."
  [credit asset past-credit past-asset]
  (when (and credit asset (>= (count past-credit) 30) (>= (count past-asset) 30))
    (let [q (fn [xs p] (let [v (vec (sort xs))] (v (min (dec (count v)) (long (* p (count v)))))))
          z (fn [x xs] (let [n (count xs) mu (/ (reduce + xs) n)
                             sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) (dec n)))]
                         (if (> sd 1e-12) (/ (- x mu) sd) 0.0)))]
      {:zone (if (and (>= credit (q past-credit 0.8)) (>= asset (q past-asset (/ 2.0 3.0)))) 1.0 0.0)
       :interaction (* (max 0.0 (z credit past-credit)) (max 0.0 (z asset past-asset)))})))
