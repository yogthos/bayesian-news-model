(ns bnm.events
  "Normalized events, and which nodes of the hierarchy an event counts for.

  A normalized event is what every source (and newsroom's LLM coder) reduces to:

    {:date \"20231007\" :a1 \"ISR\" :a2 \"PSE\" :type :assault :mode :did :n 3}

  `:a1` and `:a2` are node ids from `bnm.geo/resolve-code`: a country, or a
  region or subregion when the source names only that. `:n` is the number of
  events the record stands for.

  On disk, a normalized file is TSV with one record per line:
  date a1 a2 type mode n"
  (:require [bnm.geo :as geo]
            [bnm.ontology :as ont]
            [clojure.string :as str]))

(defn actors
  "The places an event involves: its actors' places, falling back on where it
  happened. An actor in one country acting in another (a strike abroad, say)
  involves both. At most two."
  [a1 a2 location]
  (let [xs (distinct (remove nil? [a1 a2]))]
    (vec (take 2 (if (and location (< (count xs) 2) (not (some #{location} xs)))
                   (conj (vec xs) location)
                   xs)))))

(defn ->line [{:keys [date a1 a2 type mode n]}]
  (str date "\t" (or a1 "") "\t" (or a2 "") "\t" (name type) "\t" (name mode) "\t" n))

(defn parse-line [line]
  (let [[date a1 a2 type mode n] (str/split line #"\t" -1)]
    {:date date
     :a1 (not-empty a1)
     :a2 (not-empty a2)
     :type (keyword type)
     :mode (keyword mode)
     :n (parse-double n)}))

(defn leaf
  "The leaf an event between places `a` and `b` (either may be nil) falls into,
  given the set of `tracked` dyads, or nil when it touches no country. An event
  naming only regions has no leaf; it still counts for those regions."
  [a b tracked]
  (let [ca (when (and a (geo/country? a)) a)
        cb (when (and b (geo/country? b)) b)]
    (cond
      (and ca cb (not= ca cb))
      (let [d (geo/dyad ca cb)]
        (if (contains? tracked d) d (geo/rest-leaf ca)))
      ca (geo/rest-leaf ca)
      cb (geo/rest-leaf cb)
      :else nil)))

(defn nodes
  "Every node an event between `a` and `b` counts for, each once: its leaf
  (dyad or rest leaf), and every place above the places it names.

  An event between two tracked countries lands in their dyad. An event between
  two countries whose dyad is not tracked lands in both countries' rest leaves
  (it is part of the rest of each country's events)."
  [a b tracked]
  (let [ca (when (and a (geo/country? a)) a)
        cb (when (and b (geo/country? b)) b)
        leaves (cond
                 (and ca cb (not= ca cb))
                 (let [d (geo/dyad ca cb)]
                   (if (contains? tracked d) [d] [(geo/rest-leaf ca) (geo/rest-leaf cb)]))
                 ca [(geo/rest-leaf ca)]
                 cb [(geo/rest-leaf cb)]
                 :else [])
        places (distinct (remove nil? [a b]))]
    (into (set leaves)
          (concat places (mapcat geo/ancestors-of places)))))

(defn channel [{:keys [type mode]}]
  ;; spoken intentions and threats are verbal, whatever they threaten
  (let [c (ont/channel-of type)]
    (cond
      (nil? c) nil
      (and (#{:intend :threaten} mode) (#{:assault :coerce :mobilize :sanction} type))
      (ont/channel-index :verbal-conflict)
      (and (= mode :intend) (#{:cooperate :aid :concede :retreat} type))
      (ont/channel-index :verbal-coop)
      (= mode :negate) nil
      :else c)))
