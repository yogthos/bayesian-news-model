(ns bnm.structure
  "The shape of the model: which nodes exist and how they connect.

  Built from event volumes: the dyads with the most events are tracked as
  leaves of their own, and each node's same-level neighbours are the nodes it
  shares the most events with. Nodes are ordered bottom-up (leaves first, the
  world last) so a pass in order sees every node's children before the node."
  (:require [bnm.geo :as geo]))

(def defaults
  {:max-dyads 1500        ; dyads tracked as leaves of their own
   :min-dyad-volume 50.0  ; ...and only those with at least this many events
   :neighbors 6           ; same-level neighbours per node
   :min-country-volume 20.0})

(defn- top-k [k scored]
  (->> scored (sort-by (comp - val)) (take k) (mapv key)))

(defn volumes
  "An empty volume tally: total events per dyad and per place."
  []
  {:dyads {} :places {}})

(defn tally
  "Adds `n` events between places `a` and `b` (b may be nil) to `vols`."
  [vols a b n]
  (let [vols (update-in vols [:places a] (fnil + 0.0) n)
        vols (if b (update-in vols [:places b] (fnil + 0.0) n) vols)]
    (if (and b (not= a b) (geo/country? a) (geo/country? b))
      (update-in vols [:dyads (geo/dyad a b)] (fnil + 0.0) n)
      vols)))

(defn- group-volumes
  "Volume between groups of countries, given `group-of` mapping a country to
  its group: {group {other-group n}}."
  [dyads group-of]
  (reduce-kv (fn [acc d n]
               (let [[a b] (geo/dyad-countries d)
                     ga (group-of a) gb (group-of b)]
                 (if (and ga gb (not= ga gb))
                   (-> acc
                       (update-in [ga gb] (fnil + 0.0) n)
                       (update-in [gb ga] (fnil + 0.0) n))
                   acc)))
             {} dyads))

(defn build
  "The structure for `vols` (see `tally`) under `opts` (see `defaults`)."
  ([vols] (build vols {}))
  ([vols opts]
   (let [{:keys [max-dyads min-dyad-volume neighbors min-country-volume]} (merge defaults opts)
         {:keys [dyads places]} vols
         countries (->> places
                        (filter (fn [[p n]] (and (geo/country? p) (>= n min-country-volume))))
                        (map key) sort vec)
         country? (set countries)
         dyads (into {} (filter (fn [[d _]] (every? country? (geo/dyad-countries d)))) dyads)
         tracked (->> dyads
                      (filter (fn [[_ n]] (>= n min-dyad-volume)))
                      (top-k max-dyads)
                      set)
         subregion-of (fn [c] (first (geo/parents c)))
         region-of (fn [s] (first (geo/parents s)))
         subregions (->> countries (map subregion-of) distinct sort vec)
         regions (->> subregions (map region-of) distinct sort vec)
         partner (reduce-kv (fn [acc d n]
                              (let [[a b] (geo/dyad-countries d)]
                                (-> acc (assoc-in [a b] n) (assoc-in [b a] n))))
                            {} dyads)
         by-country (group-by identity [])
         tracked-of (reduce (fn [acc d]
                              (let [[a b] (geo/dyad-countries d)]
                                (-> acc (update a (fnil conj []) d) (update b (fnil conj []) d))))
                            by-country (sort tracked))
         sub-vol (group-volumes dyads subregion-of)
         reg-vol (group-volumes dyads (comp region-of subregion-of))
         node (fn [id level parents nbrs children]
                {:id id :level level :parents (vec parents)
                 :neighbors (vec (remove #{id} nbrs)) :children (vec children)})
         dyad-nodes (for [d (sort tracked)]
                      (let [[a b] (geo/dyad-countries d)]
                        (node d 0 [a b]
                              (top-k neighbors
                                     (into {} (for [x [a b] e (tracked-of x) :when (not= e d)]
                                                [e (dyads e)])))
                              [])))
         ;; a country's other events are driven by its partners' other events
         rest-nodes (for [c countries]
                      (node (geo/rest-leaf c) 0 [c]
                            (mapv geo/rest-leaf (top-k neighbors (select-keys (partner c) countries)))
                            []))
         country-nodes (for [c countries]
                         (node c 1 [(subregion-of c)]
                               (top-k neighbors (select-keys (partner c) countries))
                               (into [(geo/rest-leaf c)] (tracked-of c))))
         subregion-nodes (for [s subregions]
                           (node s 2 [(region-of s)]
                                 (top-k neighbors (get sub-vol s {}))
                                 (filterv #(= s (subregion-of %)) countries)))
         region-nodes (for [r regions]
                        (node r 3 [geo/world]
                              (top-k neighbors (get reg-vol r {}))
                              (filterv #(= r (region-of %)) subregions)))
         world-node [(node geo/world 4 [] [] regions)]]
     {:tracked tracked
      :nodes (vec (concat dyad-nodes rest-nodes country-nodes subregion-nodes
                          region-nodes world-node))})))
