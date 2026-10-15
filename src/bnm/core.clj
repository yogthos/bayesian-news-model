(ns bnm.core
  "The library's surface, for a host like newsroom.

    (def m (or (bnm/load store) (bnm/create)))   ; store: a bnm.store/ModelStore
    (def m (bnm/add-events m \"newsroom\" events)) ; any time, in any amounts
    (def m (bnm/save! store m))                  ; writes only what changed
    (bnm/outlook m {:horizon 13})                ; what is likely next
    (bnm/drivers store \"UKR:OPP\" :protest)       ; one node, read from the store

  Events are normalized maps (see `bnm.events`):
    {:date \"20261014\" :a1 \"ISR\" :s1 \"MIL\" :a2 \"LBN\" :s2 \"REB\" :adm \"LBN.06\"
     :type :assault :mode :did :n 1}
  with places as ISO alpha-3 codes, sectors from `bnm.geo/sectors` and
  provinces as `bnm.geo` province ids (both optional). `bnm.extract` builds the
  LLM prompt that codes articles into this shape.

  The model learns in weekly bins. Events are buffered until their bin is
  over (a later bin's events arrive), then learned from; so a bin's data can
  trickle in over its week. Events older than the last learned bin are
  dropped: the model has moved on.

  The structure (which dyads have leaves of their own, who neighbours whom)
  is rebuilt from recent volumes by `restructure`; call it now and then (say
  monthly), or let `add-events` do it every `:restructure-every` bins."
  (:refer-clojure :exclude [load])
  (:require [bnm.events :as ev]
            [bnm.eval :as eval]
            [bnm.geo :as geo]
            [bnm.model :as model]
            [bnm.ontology :as ont]
            [bnm.series :as series]
            [bnm.store :as store]
            [bnm.structure :as structure]
            [bnm.time :as t]
            [clojure.string :as str]))

(defn create
  "An empty model. `config` overrides `bnm.model/defaults`, plus
  :structure-opts for `bnm.structure/build` and :restructure-every (bins)."
  ([] (create {}))
  ([config]
   (-> (model/create (structure/build (structure/volumes) (:structure-opts config {})) config)
       (assoc :pending {})
       series/with-routes)))

(defn- bin-days [m] (get-in m [:config :bin-days]))

(defn restructure
  "Rebuilds the structure from recent volumes, keeping what was learned."
  [m]
  (-> (model/restructure m (structure/build (series/volumes m)
                                            (get-in m [:config :structure-opts] {})))
      (assoc :pending (:pending m))
      series/with-routes))

(defn- learn-bin [m b]
  (let [by-source (get-in m [:pending b] {})
        [m _] (series/observe-bin! m b by-source)
        m (update m :pending dissoc b)
        every (get-in m [:config :restructure-every] 4)]
    (if (and (pos? every) (zero? (mod (inc (get-in m [:stats :bins])) every)))
      (restructure m)
      m)))

(defn- advance
  "Learns every complete bin before `b`, empty ones included."
  [m b]
  (let [start (if (:bin m)
                (inc (:bin m))
                (or (some-> (:pending m) keys seq (->> (apply min))) b))]
    (reduce learn-bin m (range start b))))

(defn add-events
  "Adds a batch of normalized events from `source`. Returns the model."
  [m source events]
  (let [bd (bin-days m)
        last-bin (:bin m)
        sector #(when (contains? geo/sectors %) %)
        m (reduce (fn [m {:keys [date a1 s1 a2 s2 adm n] :as e}]
                    (let [b (t/bin-of (t/day date) bd)
                          c (ev/channel e)]
                      (if (or (nil? c) (nil? a1) (and last-bin (<= b last-bin)))
                        m
                        (update-in m [:pending b source] (fnil conj [])
                                   [a1 (sector s1) a2 (sector s2)
                                    (when (and adm (geo/province? adm)) adm)
                                    c (double (or n 1))]))))
                  m events)
        newest (some->> (keys (:pending m)) seq (apply max))]
    (if newest (advance m newest) m)))

(defn flush-bins
  "Learns from every buffered bin, including the newest, incomplete one.
  For backfills that end on a bin boundary; live, prefer to let bins close."
  [m]
  (if-let [newest (some->> (keys (:pending m)) seq (apply max))]
    (advance m (inc newest))
    m))

;; --- what the model expects ---------------------------------------------------------------

(defn label
  "A readable name for node `id`: \"Ukraine\", \"Ukraine: opposition\",
  \"Ukraine: Kyiv City\", or the id itself."
  [id]
  (cond (geo/country? id) (geo/country-name id)
        (geo/actor? id) (str (geo/country-name (geo/country-of id)) ": "
                             (first (str/split (geo/sectors (subs id 4)) #":")))
        (geo/province? id) (str (geo/country-name (geo/country-of id)) ": "
                                (or (geo/province-name id) (subs id 4)))
        :else id))

(defn outlook
  "A forecast summarized for reading: for each node and channel the expected
  total over the horizon beside the last `horizon` bins' actual rate, and the
  probability of escalating (see `bnm.eval/escalated?`) relative to it.
  Options: :horizon (bins, default 13: a quarter), :sims, :seed, :levels
  (level names, default everything above the leaves), :min-rate (skip
  quieter nodes)."
  [m {:keys [horizon sims seed levels min-rate]
      :or {horizon 13 sims 50 seed 1 levels #{:local :country :subregion :region :world}
           min-rate 0.5}}]
  (let [fc (model/forecast m {:horizon horizon :sims sims :seed seed})
        nodes (:nodes m)]
    (vec
     (for [[i id] (map-indexed vector (:ids fc))
           :let [node (nodes i)]
           :when (levels (geo/level-names (:level node)))
           [c ch] (map-indexed vector ont/channels)
           :let [recent (* horizon (aget ^doubles (:ybar node) c))
                 xs (sort (for [s (range sims)] (model/total fc i s c)))
                 mean (/ (reduce + xs) sims)]
           :when (>= (max recent mean) min-rate)]
       {:node id
        :name (label id)
        :level (geo/level-names (:level node))
        :channel ch
        :recent recent
        :expected mean
        :low (nth xs (int (* 0.1 (dec sims))))
        :high (nth xs (int (* 0.9 (dec sims))))
        :p-escalate (/ (count (filter #(eval/escalated? % recent horizon) xs)) (double sims))}))))

(defn risks
  "The outlook's `n` likeliest escalations in `channel` (default violence)."
  [outlook & {:keys [channel n] :or {channel :violence n 20}}]
  (->> outlook
       (filter #(= channel (:channel %)))
       (sort-by (comp - :p-escalate))
       (take n)
       vec))

(defn connections
  "What drives node `id`'s activity in `channel`: the strongest sources and
  the share of its typical rate each accounts for."
  [m id channel & {:keys [n] :or {n 10}}]
  (vec (take n (model/connections m id (ont/channel-index channel)))))

;; --- persistence --------------------------------------------------------------------------

(defn load
  "The model kept in `store` (a `bnm.store/ModelStore`), or nil when it is
  empty. Buffered events come back with it."
  [store]
  (some-> (store/load-model store) series/with-routes))

(defn save!
  "Saves `m` to `store` and returns it marked saved. Cheap between closed
  bins (only the buffered events are written); after a bin closes, every
  node is."
  [store m]
  (store/save-model! store m))

(defn drivers
  "What drives node `id`'s activity in `channel`, read from `store` without
  loading the model: the strongest sources and the share of its typical rate
  each accounts for."
  [store id channel & {:keys [n] :or {n 10}}]
  (vec (take n (store/node-drivers store id (ont/channel-index channel)))))
