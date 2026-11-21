(ns bnm.aftermath
  "What a kind's past onsets do to its next one, learned (reports/Crisis
  aftermath and trend reversal.md, rank 3): own-kind onset kernels, fast
  (halving each year) and slow (every four), a flag for any past onset,
  and a fast kernel on the other kinds' onsets, summed over past onsets as
  a Hawkes process sums them. Each slope's prior is centred on zero, so
  whether a recent onset raises the next one's chance (conflict relapse,
  the coup trap, inflation's relapse) or lowers it (a refractory year,
  fiscal consolidations, defaults) is the data's to say, kind by kind.
  Onsets only, known when they happen.

  The slopes are fit as departures from the log odds the year node already
  has (its offset plus any :offset-add, bnm.theory/fits :base), refit at
  each origin on the rows known by then, and added through :offset-add."
  (:require [bnm.theory :as th]))

(def width "Columns of `kernel-row`." 4)

(def ^:private fast-months 12.0)
(def ^:private slow-months 48.0)

(defn- kernel
  "Sum over the onsets at or before t of 2^(-(t - m)/h)."
  [ms t h]
  (reduce (fn [acc m] (if (<= m t) (+ acc (Math/pow 0.5 (/ (- t m) h))) acc)) 0.0 ms))

(defn kernel-row
  "(fn [unit origin]) -> [own fast, own slow, own ever, others fast] from
  `onsets` ({kind {unit [month ...]}}) for `kind` among `kinds`."
  [onsets kind kinds]
  (let [others (remove #{kind} kinds)]
    (fn [u t]
      (let [own (get-in onsets [kind u])]
        [(kernel own t fast-months)
         (kernel own t slow-months)
         (if (some #(<= % t) own) 1.0 0.0)
         (reduce + 0.0 (map #(kernel (get-in onsets [% u]) t fast-months) others))]))))

(defn- year-base
  "(fn [unit origin]) -> the year node's log odds before this layer, nil
  where the spec has no year offset there."
  [{:keys [offset offset-add]}]
  (fn [u t]
    (when-let [o (when offset (offset :year u t))]
      (+ o (or (when offset-add (offset-add :year u t)) 0.0)))))

(defn with-aftermath
  "`sp` with the slopes of `row`'s columns (`kernel-row`) added to its
  countries' year log odds through :offset-add (summed with one already
  there), fit at each origin on the base the node already has; a spec with
  no year offset fits them alone, intercept and all, and adds the slopes.
  Each slope's prior on its column's own scale (bnm.theory/fits :raw)."
  [{:keys [scales levels start end target offset-add] :as sp} row]
  (let [countries (second (first levels))
        per (:per (first (filter #(= :year (:name %)) scales)))
        os (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
        base (year-base sp)
        has-base (some (fn [u] (some #(base u %) os)) countries)
        fits (th/fits (cond-> {:places countries :origins os :prior 1.0 :raw true
                               :inputs row :target (fn [u t] (target :year u t))}
                        has-base (assoc :base base)))
        cs (set countries)
        add (fn [u t]
              (when-let [^doubles b (get fits t)]
                (reduce + 0.0 (map-indexed (fn [i x] (* (aget b (inc i)) (double x))) (row u t)))))]
    (assoc sp
           :offset-add (fn [scale place t]
                         (let [o (when offset-add (offset-add scale place t))
                               a (when (and (= :year scale) (cs place)) (add place t))]
                           (if (and o a) (+ o a) (or a o))))
           :aftermath {:fits fits :width width})))
