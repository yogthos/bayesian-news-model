(ns bnm.series
  "From raw event counts to the model's observations.

  A bin of input is, per source, a seq of records [a1 s1 a2 s2 adm channel n]:
  n events of a channel between places a1 and a2 (a2 may be nil), with the
  actors' sectors and the province where they happened when known (nil when
  not). Each record counts for every node it touches (see `bnm.events/nodes`).

  Counts are then normalized by the source's exposure: a slowly moving
  average of the source's total events per bin. A source whose coverage
  doubles over a decade (GDELT's did, several times) then reads the same for
  the same world, and sources of different size can be averaged."
  (:require [bnm.events :as ev]
            [bnm.model :as model]))

(set! *unchecked-math* true)

(def C model/C)

(def exposure-rate
  "Weight of the newest bin in a source's exposure (a half-life of about two
  months of weekly bins)."
  0.08)

(defn- routes
  "The node indices a record counts for, cached."
  [model a s b t adm]
  (let [^java.util.HashMap cache (or (:routes model) (java.util.HashMap.))
        k (str a "\t" s "\t" b "\t" t "\t" adm)]
    (or (.get cache k)
        (let [index (:index model)
              v (int-array (keep index (ev/nodes a s b t adm (get-in model [:structure :tracked]))))]
          (.put cache k v)
          v))))

(defn with-routes
  "The model with a fresh route cache (not part of its saved state)."
  [model]
  (assoc model :routes (java.util.HashMap.)))

(defn- volume-weight
  "Volumes are kept with weights that grow by 1/forget per bin, so that old
  events fade without touching every entry each bin; `volumes` undoes it."
  ^double [model bin]
  (Math/exp (* (- (double bin) (double (get-in model [:volumes :epoch] bin)))
               (- (Math/log (double (get-in model [:config :forget])))))))

(defn- tally! [^java.util.HashMap m k ^double v]
  (.put m k (+ v (or (.get m k) 0.0))))

(defn with-volumes
  "The model with volume tallies, starting at `bin` when it has none."
  [model bin]
  (if (get-in model [:volumes :units])
    model
    (assoc model :volumes {:epoch bin :units (java.util.HashMap.)})))

(defn volumes
  "Recent event volumes per unit (see `bnm.events/units`), as
  `bnm.structure/build` takes them: each event weighted by forget^(age in
  bins)."
  [model]
  (let [^java.util.HashMap units (get-in model [:volumes :units])
        f (/ 1.0 (volume-weight model (:bin model)))]
    (persistent! (reduce (fn [acc k] (assoc! acc k (* f (.get units k))))
                         (transient {}) (keys units)))))

(defn- source-counts
  "Raw counts per node index for one source's records: an object array of
  per-channel double arrays, nil where nothing was seen. Also the total.
  Tallies volumes as it goes."
  [model bin records]
  (let [n (count (:nodes model))
        out (object-array n)
        wt (volume-weight model bin)
        ^java.util.HashMap vu (get-in model [:volumes :units])
        unit-cache (java.util.HashMap.)
        total (loop [rs (seq records) total 0.0]
                (if-let [[a s b t adm c cnt] (first rs)]
                  (let [^ints idx (routes model a s b t adm)
                        cnt (double cnt)
                        v (* wt cnt)
                        k (str a "\t" s "\t" b "\t" t "\t" adm)
                        us (or (.get unit-cache k)
                               (let [us (ev/units a s b t adm)] (.put unit-cache k us) us))]
                    (doseq [u us] (tally! vu u v))
                    (dotimes [j (alength idx)]
                      (let [i (aget idx j)
                            ^doubles arr (or (aget out i)
                                             (let [x (double-array C)] (aset out i x) x))]
                        (aset arr (int c) (+ (aget arr (int c)) cnt))))
                    (recur (next rs) (+ total cnt)))
                  total))]
    [out total]))

(defn update-exposure
  "The source's exposure after a bin with `total` events."
  [exposure total]
  (if (or (nil? exposure) (zero? exposure))
    total
    (+ (* (- 1.0 exposure-rate) exposure) (* exposure-rate total))))

(defn observations
  "Normalized observations {node-id double-array} for one bin of input
  `by-source` ({source records}), and the model with updated exposures.
  Sources that report nothing in the bin are taken to be absent rather than to
  have seen a quiet world; the others are averaged."
  [model bin by-source]
  (let [scale (double (get-in model [:config :scale]))
        ids (mapv :id (:nodes model))
        n (count ids)
        acc (object-array n)
        [model active]
        (reduce (fn [[model active] [source records]]
                  (let [[counts total] (source-counts model bin records)]
                    (if (pos? total)
                      (let [e (update-exposure (get-in model [:exposure source]) total)
                            f (/ scale e)]
                        (dotimes [i n]
                          (when-let [^doubles arr (aget counts i)]
                            (let [^doubles a (or (aget acc i)
                                                 (let [x (double-array C)] (aset acc i x) x))]
                              (dotimes [c C] (aset a c (+ (aget a c) (* f (aget arr c))))))))
                        [(assoc-in model [:exposure source] e) (inc active)])
                      [model active])))
                [model 0]
                by-source)]
    [model
     (if (zero? active)
       {}
       (persistent!
        (reduce (fn [m i]
                  (if-let [^doubles a (aget acc i)]
                    (do (when (> active 1)
                          (dotimes [c C] (aset a c (/ (aget a c) active))))
                        (assoc! m (ids i) a))
                    m))
                (transient {}) (range n))))]))

(defn observe-bin!
  "Feeds one bin of raw input ({source records}) to the model: normalizes it,
  then `bnm.model/observe!`. Returns [model observations]."
  [model bin by-source]
  (let [model (cond-> (with-volumes model bin)
                (not (:routes model)) with-routes)
        [model obs] (observations model bin by-source)]
    [(model/observe! model bin obs) obs]))
