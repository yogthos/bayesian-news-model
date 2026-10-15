(ns bnm.structure
  "The shape of the model: which nodes exist and how they connect.

  Built from event volumes per unit (see `bnm.events/units`): the busiest
  dyads, actors, pairs of actors and provinces get nodes of their own, and
  each node's same-level neighbours are the nodes it shares the most events
  with. Nodes are ordered bottom-up (leaves first, the world last) so a pass
  in order sees every node's children before the node.

  A country's children are its leaves, which partition its events: its
  tracked dyads, the tracked pairs of its actors, and its rest leaf. An
  actor's children are its pairs. Provinces are a view by place: their
  parent is their country, but they are not among its children, so a
  country's bottom-up prediction counts each event once."
  (:require [bnm.events :as ev]
            [bnm.geo :as geo]))

(def defaults
  {:max-dyads 2500          ; dyads tracked as leaves of their own
   :min-dyad-volume 50.0    ; ...and only those with at least this many events
   :max-actors 1500         ; actors (country sectors) tracked
   :min-actor-volume 50.0
   :max-pairs 1500          ; pairs of actors of one country tracked as leaves
   :min-pair-volume 50.0
   :max-provinces 1500      ; provinces tracked
   :min-province-volume 50.0
   :neighbors 6             ; same-level neighbours per node
   :min-country-volume 20.0})

(defn- top-k [k scored]
  (->> scored (sort-by (comp - val)) (take k) (mapv key)))

(defn volumes
  "An empty volume tally: total events per unit."
  []
  {})

(defn tally
  "Adds `n` events between `a` and `b` (sectors `s` and `t`, province `adm`;
  all but `a` may be nil) to `vols`."
  ([vols a b n] (tally vols a nil b nil nil n))
  ([vols a s b t adm n]
   (reduce (fn [v u] (update v u (fnil + 0.0) n)) vols (ev/units a s b t adm))))

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

(defn- index-by
  "{key [ids]} for the ids in `ids`, each under every key `f` gives it."
  [f ids]
  (reduce (fn [acc id] (reduce #(update %1 %2 (fnil conj []) id) acc (f id)))
          {} (sort ids)))

(defn build
  "The structure for `vols` (see `tally`) under `opts` (see `defaults`)."
  ([vols] (build vols {}))
  ([vols opts]
   (let [{:keys [max-dyads min-dyad-volume max-actors min-actor-volume max-pairs
                 min-pair-volume max-provinces min-province-volume neighbors
                 min-country-volume]}
         (merge defaults opts)
         countries (->> vols
                        (filter (fn [[p n]] (and (geo/country? p) (>= n min-country-volume))))
                        (map key) sort vec)
         country? (set countries)
         units (fn [pred] (into {} (filter (fn [[id _]] (pred id))) vols))
         pick (fn [m k min-vol] (set (top-k k (filter (fn [[_ n]] (>= n min-vol)) m))))
         dyads (units #(and (geo/dyad? %) (every? country? (geo/dyad-countries %))))
         tracked (pick dyads max-dyads min-dyad-volume)
         actors (pick (units #(and (geo/actor? %) (country? (geo/country-of %))))
                      max-actors min-actor-volume)
         all-pairs (units #(and (geo/pair? %) (every? actors (geo/pair-actors %))))
         pairs (pick all-pairs max-pairs min-pair-volume)
         provinces (pick (units #(and (geo/province? %) (country? (geo/country-of %))))
                         max-provinces min-province-volume)
         subregion-of (fn [c] (first (geo/parents c)))
         region-of (fn [s] (first (geo/parents s)))
         subregions (->> countries (map subregion-of) distinct sort vec)
         regions (->> subregions (map region-of) distinct sort vec)
         partner (reduce-kv (fn [acc d n]
                              (let [[a b] (geo/dyad-countries d)]
                                (-> acc (assoc-in [a b] n) (assoc-in [b a] n))))
                            {} dyads)
         dyads-of (index-by geo/dyad-countries tracked)
         pairs-of (index-by #(distinct (geo/pair-actors %)) pairs)
         country-pairs (index-by (comp vector geo/country-of) pairs)
         actors-of (index-by (comp vector geo/country-of) actors)
         provinces-of (index-by (comp vector geo/country-of) provinces)
         sub-vol (group-volumes dyads subregion-of)
         reg-vol (group-volumes dyads (comp region-of subregion-of))
         node (fn [id level parents nbrs children]
                {:id id :level (geo/levels level) :parents (vec parents)
                 :neighbors (vec (remove #{id} nbrs)) :children (vec children)})
         ;; the leaves sharing a country (dyads) or an actor (pairs), by volume
         sharing (fn [index id members vol]
                   (top-k neighbors (into {} (for [x members e (index x) :when (not= e id)]
                                               [e (vol e)]))))
         dyad-nodes (for [d (sort tracked)]
                      (let [cs (geo/dyad-countries d)]
                        (node d :leaf cs (sharing dyads-of d cs dyads) [])))
         pair-nodes (for [p (sort pairs)]
                      (let [xs (distinct (geo/pair-actors p))]
                        (node p :leaf xs (sharing pairs-of p xs all-pairs) [])))
         ;; a country's other events are driven by its partners' other events
         rest-nodes (for [c countries]
                      (node (geo/rest-leaf c) :leaf [c]
                            (mapv geo/rest-leaf (top-k neighbors (select-keys (partner c) countries)))
                            []))
         ;; an actor's neighbours are the actors of its country it deals with most
         actor-nodes (for [x (sort actors)]
                       (let [c (geo/country-of x)]
                         (node x :local [c]
                               (top-k neighbors (into {} (for [y (actors-of c)
                                                               :let [n (get all-pairs (geo/dyad x y))]
                                                               :when (and n (not= x y))]
                                                           [y n])))
                               (pairs-of x []))))
         province-nodes (for [p (sort provinces)]
                          (let [c (geo/country-of p)]
                            (node p :local [c]
                                  (top-k neighbors (select-keys vols (provinces-of c)))
                                  [])))
         country-nodes (for [c countries]
                         (node c :country [(subregion-of c)]
                               (top-k neighbors (select-keys (partner c) countries))
                               (concat [(geo/rest-leaf c)] (dyads-of c []) (country-pairs c []))))
         subregion-nodes (for [s subregions]
                           (node s :subregion [(region-of s)]
                                 (top-k neighbors (get sub-vol s {}))
                                 (filterv #(= s (subregion-of %)) countries)))
         region-nodes (for [r regions]
                        (node r :region [geo/world]
                              (top-k neighbors (get reg-vol r {}))
                              (filterv #(= r (region-of %)) subregions)))
         world-node [(node geo/world :world [] [] regions)]]
     {:tracked {:dyads tracked :pairs pairs :actors actors :provinces provinces}
      :nodes (vec (concat dyad-nodes pair-nodes rest-nodes actor-nodes province-nodes
                          country-nodes subregion-nodes region-nodes world-node))})))
