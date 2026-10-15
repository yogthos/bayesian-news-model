(ns bnm.events
  "Normalized events, and which nodes of the hierarchy an event counts for.

  A normalized event is what every source (and newsroom's LLM coder) reduces to:

    {:date \"20231007\" :a1 \"ISR\" :s1 \"MIL\" :a2 \"PSE\" :s2 \"REB\" :adm \"ISR.D\"
     :type :assault :mode :did :n 3}

  `:a1` and `:a2` are places from `bnm.geo/resolve-code`: a country, or a
  region or subregion when the source names only that; they may be the same
  country, for an event within it. `:s1` and `:s2` are the actors' sectors
  (see `bnm.geo/sectors`), nil when unknown. `:adm` is the province where it
  happened, a `bnm.geo` province id, nil when unknown. `:n` is the number of
  events the record stands for.

  On disk, a normalized file is TSV with one record per line:
  date a1 s1 a2 s2 adm type mode n"
  (:require [bnm.geo :as geo]
            [bnm.ontology :as ont]
            [clojure.string :as str]))

(defn actors
  "The actors an event involves, as [place sector] pairs: its two actors, or
  where it happened standing in for a missing one. An actor with a sector but
  no country (GDELT's \"police\", say) is taken to be of the country where it
  acted. An actor in one country acting in another (a strike abroad) involves
  both. At most two; the two may be in the same country."
  [p1 s1 p2 s2 location]
  (let [p1 (or p1 (when s1 location))
        p2 (or p2 (when s2 location))
        xs (cond-> []
             p1 (conj [p1 (when (geo/country? p1) s1)])
             p2 (conj [p2 (when (geo/country? p2) s2)]))]
    (if (and location (< (count xs) 2) (not (some #(= location (first %)) xs)))
      (conj xs [location nil])
      xs)))

(defn ->line [{:keys [date a1 s1 a2 s2 adm type mode n]}]
  (str/join "\t" [date (or a1 "") (or s1 "") (or a2 "") (or s2 "") (or adm "")
                  (name type) (name mode) n]))

(defn parse-line [line]
  (let [[date a1 s1 a2 s2 adm type mode n] (str/split line #"\t" -1)]
    {:date date
     :a1 (not-empty a1) :s1 (not-empty s1)
     :a2 (not-empty a2) :s2 (not-empty s2)
     :adm (not-empty adm)
     :type (keyword type)
     :mode (keyword mode)
     :n (parse-double n)}))

(defn units
  "Every unit an event adds to the volumes the structure is built from,
  tracked or not: its places, the dyad of two countries, its actors, the
  pair of two actors of one country, and its province."
  [a s b t adm]
  (let [ca (when (and a (geo/country? a)) a)
        cb (when (and b (geo/country? b)) b)
        xa (when (and ca s) (geo/actor ca s))
        xb (when (and cb t) (geo/actor cb t))]
    (cond-> (vec (distinct (remove nil? [a b xa xb])))
      (and ca cb (not= ca cb)) (conj (geo/dyad ca cb))
      (and xa xb (= ca cb)) (conj (geo/dyad xa xb))
      (and adm (let [c (geo/country-of adm)] (or (= c ca) (= c cb)))) (conj adm))))

(defn nodes
  "Every node an event counts for, each once: its leaf, the tracked actors
  and province it involves, and every place above the places it names.
  `tracked` is {:dyads :pairs :actors :provinces}, sets of the ids the
  structure gives nodes of their own.

  The leaf: an event between two countries lands in their dyad when tracked,
  else in both countries' rest leaves. An event within a country lands in the
  pair of its two actors when both are tracked actors and the pair is
  tracked, else in the country's rest leaf. A province counts when it is in
  one of the event's countries."
  [a s b t adm tracked]
  (let [{:keys [dyads pairs actors provinces]} tracked
        ca (when (and a (geo/country? a)) a)
        cb (when (and b (geo/country? b)) b)
        actor (fn [c sector]
                (when (and c sector)
                  (let [x (geo/actor c sector)] (when (contains? actors x) x))))
        xa (actor ca s)
        xb (actor cb t)
        leaves (cond
                 (and ca cb (not= ca cb))
                 (let [d (geo/dyad ca cb)]
                   (if (contains? dyads d) [d] [(geo/rest-leaf ca) (geo/rest-leaf cb)]))
                 (or ca cb)
                 (let [p (when (and xa xb) (geo/dyad xa xb))]
                   (if (contains? pairs p) [p] [(geo/rest-leaf (or ca cb))]))
                 :else [])
        prov (when (and adm (contains? provinces adm)
                        (let [c (geo/country-of adm)] (or (= c ca) (= c cb))))
               adm)
        places (distinct (remove nil? [a b]))]
    (into (set (concat leaves (remove nil? [xa xb prov])))
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
