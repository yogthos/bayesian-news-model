(ns bnm.aftermath
  "What a kind's past onsets do to its next one (reports/Crisis aftermath
  and trend reversal.md, rank 3; docs/CALIBRATION.md t133): onset
  kernels, summed over past onsets as a Hawkes process sums them, read by
  the year node as a gauge group of its own. A country's own onsets
  halving each year (fast) and every four (slow), each net of the
  countries' mean so a global wave is not read as the country's own; the
  world's fast mean; the other kinds' onsets, fast. Only onsets known at
  the origin, each kind after its catalogue's publication lag. The node
  fits the group with its other gauges, each averaged model with its own
  intercept, so whether a recent onset raises the next one's chance
  (relapse, the coup trap) or lowers it (a refractory spell) is the
  data's to say, kind by kind, and what the node already knows from its
  history gauges is not counted twice.

  t130-t132's layer, fit beside the node and added through :offset-add,
  is withdrawn (bnm.world-run/withdrawn-check)."
  (:require [bnm.signals :as sg]))

(def ^:private fast-months 12.0)
(def ^:private slow-months 48.0)

(defn- kernel
  "Sum over the onsets at or before t of 2^(-(t - m)/h)."
  [ms t h]
  (reduce (fn [acc m] (if (<= m t) (+ acc (Math/pow 0.5 (/ (- t m) h))) acc)) 0.0 ms))

(defn known-row
  "(fn [unit origin]) -> [own fast - world fast, own slow - world slow,
  world fast, others fast] over the onsets known at the
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
