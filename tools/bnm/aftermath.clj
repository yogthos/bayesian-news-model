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
  (:require [bnm.signals :as sg]
            [bnm.theory :as th]))

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

(defn net-row
  "(fn [unit origin]) -> [own fast - world fast, own slow - world slow,
  world fast, others fast], the world's kernels the mean of `countries`'
  own-kind kernels (docs/CALIBRATION.md t132). For kinds that come in
  global waves (food prices, market crashes, protest) a country's own
  onset is mostly the world's; net of the mean, the own columns say what
  is the country's, and the world's column what the wave does to the
  next. No ever flag: a country with no onset in the catalogue is not
  read as safe."
  [onsets kind kinds countries]
  (let [others (remove #{kind} kinds)
        n (double (count countries))
        world (memoize (fn [t h] (/ (reduce + 0.0 (map #(kernel (get-in onsets [kind %]) t h) countries)) n)))]
    (fn [u t]
      (let [own (get-in onsets [kind u])
            wf (world t fast-months)
            ws (world t slow-months)]
        [(- (kernel own t fast-months) wf)
         (- (kernel own t slow-months) ws)
         wf
         (reduce + 0.0 (map #(kernel (get-in onsets [% u]) t fast-months) others))]))))

(defn known-row
  "(fn [unit origin]) -> [own fast - world fast, own slow - world slow,
  world fast, others fast], as net-row, over the onsets known at the
  origin: an onset dated m is known at m plus its kind's publication lag
  (`lags`, {kind months}), the rule the year node's own history gauges
  read (docs/CALIBRATION.md t133)."
  [onsets lags kind kinds countries]
  (let [known (fn [k ms t] (let [c (- t (get lags k 0))] (filter #(<= % c) ms)))
        others (remove #{kind} kinds)
        n (double (count countries))
        world (memoize (fn [t h] (/ (reduce + 0.0 (map #(kernel (known kind (get-in onsets [kind %]) t) t h) countries)) n)))]
    (fn [u t]
      (let [own (known kind (get-in onsets [kind u]) t)
            wf (world t fast-months)
            ws (world t slow-months)]
        [(- (kernel own t fast-months) wf)
         (- (kernel own t slow-months) ws)
         wf
         (reduce + 0.0 (map #(kernel (known % (get-in onsets [% u]) t) t fast-months) others))]))))

(def added-names "The year gauges `with-gauges` adds." [:own-fast-net :own-slow-net :world-fast :others-fast])

(defn with-gauges
  "`sp` with `row`'s columns (`known-row`) after its year node's gauges, a
  gauge group of their own (:aftermath), so the node fits them with the
  rest: each averaged model with its own intercept, against the node's own
  outcomes, beside its history, frailty and climatology, the averaging
  weighting the group by what it adds (t133). Standardized as the node's
  gauges are (bnm.signals/running-z on the countries' values at earlier
  origins, `gauge-std` :stable or nil); an aggregate reads its countries'
  mean. A spec without named year groups keeps its gauges as one group."
  [{:keys [scales levels parent start end gauges gauge-width gauge-groups gauge-names] :as sp} row gauge-std]
  (let [countries (second (first levels))
        per (:per (first (filter #(= :year (:name %)) scales)))
        os (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
        std (sg/running-z (for [t os u countries] [t (row u t)])
                          {:count-by :values :min-exclusive 20 :stable (= :stable gauge-std)})
        cs (set countries)
        ancestors (fn [u] (take-while some? (iterate #(get parent %) u)))
        members (memoize (fn [place] (if (cs place) [place] (filter #(some #{place} (ancestors %)) countries))))
        z (memoize (fn [u t] (std t (row u t))))
        w (get gauge-width :year 0)
        add (vec (range w (+ w (count added-names))))
        groups (or (:year gauge-groups) {:gauges (vec (range w))})]
    (cond-> (assoc sp
                   :gauges (fn [scale place t]
                             (let [g (gauges scale place t)]
                               (if (= :year scale)
                                 (let [vs (map #(z % t) (members place))]
                                   (into (vec g) (if (seq vs)
                                                   (apply mapv (fn [& xs] (/ (reduce + xs) (count xs))) vs)
                                                   (repeat (count add) 0.0))))
                                 g)))
                   :gauge-width (assoc gauge-width :year (+ w (count add)))
                   :gauge-groups (assoc gauge-groups :year (assoc groups :aftermath add)))
      (get gauge-names :year) (update-in [:gauge-names :year] #(into (vec %) added-names)))))

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
