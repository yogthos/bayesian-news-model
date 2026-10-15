(ns bnm.core
  "The library's surface, for a host like newsroom.

    (def m (bnm/create))                         ; or (bnm/load-rows meta nodes)
    (bnm/add-events m \"newsroom\" events)         ; any time, in any amounts
    (bnm/outlook m {:horizon 52})                ; what is likely next
    (bnm/->rows m)                               ; persist

  Events are normalized maps (see `bnm.events`):
    {:date \"20261014\" :a1 \"ISR\" :a2 \"LBN\" :type :assault :mode :did :n 1}
  with places as ISO alpha-3 codes. `bnm.extract` builds the LLM prompt that
  codes articles into this shape.

  The model learns in weekly bins. Events are buffered until their bin is
  over (a later bin's events arrive), then learned from; so a bin's data can
  trickle in over its week. Events older than the last learned bin are
  dropped: the model has moved on.

  The structure (which dyads have leaves of their own, who neighbours whom)
  is rebuilt from recent volumes by `restructure`; call it now and then (say
  monthly), or let `add-events` do it every `:restructure-every` bins."
  (:require [bnm.events :as ev]
            [bnm.eval :as eval]
            [bnm.geo :as geo]
            [bnm.model :as model]
            [bnm.ontology :as ont]
            [bnm.series :as series]
            [bnm.store :as store]
            [bnm.structure :as structure]
            [bnm.time :as t]))

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
        m (reduce (fn [m {:keys [date a1 a2 n] :as e}]
                    (let [b (t/bin-of (t/day date) bd)
                          c (ev/channel e)]
                      (if (or (nil? c) (nil? a1) (and last-bin (<= b last-bin)))
                        m
                        (update-in m [:pending b source] (fnil conj [])
                                   [a1 a2 c (double (or n 1))]))))
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

(defn outlook
  "A forecast summarized for reading: for each node and channel the expected
  total over the horizon beside the last `horizon` bins' actual rate, and the
  probability of escalating (see `bnm.eval/escalated?`) relative to it.
  Options: :horizon (bins, default 13: a quarter), :sims, :seed, :levels
  (default #{1 2 3 4}), :min-rate (skip quieter nodes)."
  [m {:keys [horizon sims seed levels min-rate]
      :or {horizon 13 sims 50 seed 1 levels #{1 2 3 4} min-rate 0.5}}]
  (let [fc (model/forecast m {:horizon horizon :sims sims :seed seed})
        nodes (:nodes m)]
    (vec
     (for [[i id] (map-indexed vector (:ids fc))
           :let [node (nodes i)]
           :when (levels (:level node))
           [c ch] (map-indexed vector ont/channels)
           :let [recent (* horizon (aget ^doubles (:ybar node) c))
                 xs (sort (for [s (range sims)] (model/path-sum fc i s c 0 horizon)))
                 mean (/ (reduce + xs) sims)]
           :when (>= (max recent mean) min-rate)]
       {:node id
        :name (when (geo/country? id) (geo/country-name id))
        :level (geo/level-names (:level node))
        :channel ch
        :recent recent
        :expected mean
        :low (nth xs (int (* 0.1 (dec sims))))
        :high (nth xs (int (* 0.9 (dec sims))))
        :p-escalate (/ (count (filter #(eval/escalated? % recent) xs)) (double sims))}))))

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

(defn ->rows
  "{:meta map :nodes [{:node :level :data}]}, with buffered events in meta."
  [m]
  (assoc-in (store/->rows m) [:meta :pending] (:pending m)))

(defn load-rows [meta nodes]
  (-> (store/rows->model (dissoc meta :pending) nodes)
      (assoc :pending (:pending meta {}))
      series/with-routes))
