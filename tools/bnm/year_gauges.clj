(ns bnm.year-gauges
  "A row of inputs read by the year node as a gauge group of its own
  (docs/CALIBRATION.md t133, t134). The node fits the group with its other
  gauges: each averaged model with its own intercept, against the node's
  own outcomes, beside its history, frailty and climatology, the averaging
  weighting the group by what it adds. A layer fit beside the node and
  added to its log odds without the intercept (with-theory :add) lifts
  every row and double counts what the node already reads; this does not."
  (:require [bnm.signals :as sg]))

(defn with-group
  "`sp` with `row`'s `width` columns ((fn [unit origin]) -> vector, nil
  for a missing value or row) after its year node's gauges, as the group
  `group`, named `names` where the spec names its year gauges.
  Standardized as the node's gauges are (bnm.signals/running-z on the
  countries' values at earlier origins, `gauge-std` :stable or nil), a
  missing value reading 0; an aggregate reads its countries' mean. A spec
  without named year groups keeps its gauges as one group.
  opts {:every n :min-periods m} (t135, for a world column, one value per
  origin repeated at every country): standardized on the row at every
  n-th month from the start, read once m of those months had a value,
  instead of on the rows at year origins counted by value."
  ([sp row width group names gauge-std] (with-group sp row width group names gauge-std nil))
  ([{:keys [scales levels parent start end gauges gauge-width gauge-groups gauge-names] :as sp} row width group names gauge-std {:keys [every min-periods]}]
  (when-not (= width (count names))
    (throw (ex-info "bnm.year-gauges: the names are not the row's width" {:width width :names names})))
  (let [countries (second (first levels))
        per (:per (first (filter #(= :year (:name %)) scales)))
        os (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
        blank (vec (repeat width nil))
        row' (fn [u t] (or (row u t) blank))
        std (if every
              (sg/running-z (for [t (range start (inc end) every) u countries] [t (row' u t)])
                            {:count-by :periods :min min-periods :stable (= :stable gauge-std)})
              (sg/running-z (for [t os u countries] [t (row' u t)])
                            {:count-by :values :min-exclusive 20 :stable (= :stable gauge-std)}))
        cs (set countries)
        ancestors (fn [u] (take-while some? (iterate #(get parent %) u)))
        members (memoize (fn [place] (if (cs place) [place] (filter #(some #{place} (ancestors %)) countries))))
        z (memoize (fn [u t] (std t (row' u t))))
        w (get gauge-width :year 0)
        add (vec (range w (+ w width)))
        groups (or (:year gauge-groups) {:gauges (vec (range w))})]
    (cond-> (assoc sp
                   :gauges (fn [scale place t]
                             (let [g (gauges scale place t)]
                               (if (= :year scale)
                                 (let [vs (map #(z % t) (members place))]
                                   (into (vec g) (if (seq vs)
                                                   (apply mapv (fn [& xs] (/ (reduce + xs) (count xs))) vs)
                                                   (repeat width 0.0))))
                                 g)))
                   :gauge-width (assoc gauge-width :year (+ w width))
                   :gauge-groups (assoc gauge-groups :year (assoc groups group add)))
      (get gauge-names :year) (update-in [:gauge-names :year] #(into (vec %) names))))))
