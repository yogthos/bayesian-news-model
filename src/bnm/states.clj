(ns bnm.states
  "Stock-flow states as country-year tables (data/derived/stocks/*.csv,
  data/derived/material/*.csv: iso,year,variable,value, the year the value
  is known), read for the event lattice (bead bnm-62z.13): each state's own
  part at each country (bnm.factors), causal."
  (:require [bnm.factors :as fac]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn bookkeeping?
  "A variable that describes a model's estimate, not the system: when it
  was calibrated, the year its state refers to, posterior spreads, counts
  and set changes behind an index."
  [v]
  (boolean (re-find #"(?:^|/|-)(?:calibrated|state-year)$|-(?:sd|count|break)$" v)))

(defn read-states
  "{variable {iso (sorted-map year value)}} from long tables
  iso,year,variable,value[,...]; empty values and bookkeeping left out.
  A path is a table by the year its values are known, or [path lag] for
  one by data year (the material base): a value known lag years after its
  year."
  [paths]
  (reduce (fn [acc entry]
            (let [[path lag] (if (vector? entry) entry [entry 0])]
            (with-open [r (io/reader path)]
              (let [[h & ls] (line-seq r)
                    ix (zipmap (str/split h #",") (range))]
                (reduce (fn [acc l]
                          (let [f (str/split l #"," -1)
                                g #(nth f (ix %) "")
                                v (g "value")]
                            (if (and (seq v) (seq (g "iso")) (not (bookkeeping? (g "variable"))))
                              (update-in acc [(g "variable") (g "iso")] (fnil assoc (sorted-map)) (+ lag (parse-long (g "year"))) (parse-double v))
                              acc)))
                        acc ls)))))
          {} paths))

(defn own-parts
  "{variable {iso {year own part}}}: each state's own part at each country
  (bnm.factors), causal."
  [states tree]
  (into {} (pmap (fn [[v panel]]
                   (let [panel (into {} (filter (comp tree key) panel))
                         parts (when (> (count panel) 3) (fac/decompose panel tree {:discount 0.99}))]
                     [v (update-vals (or parts {}) #(update-vals % :own))]))
                 states)))


(defn world-series
  "{variable (sorted-map year value)} of the states held only for the
  world (iso WLD): a time signal common to every country."
  [states]
  (into {} (for [[v by-iso] states :when (and (get by-iso "WLD") (= 1 (count by-iso)))] [v (get by-iso "WLD")])))

(defn world-z
  "{variable {year z}}: each world series against its own past (an
  expanding mean and spread, the year included), once five years are
  known; causal."
  [world]
  (into {} (for [[v byy] world]
             [v (loop [[[y x] & more] (seq byy) n 0 s 0.0 ss 0.0 out {}]
                  (if (nil? y)
                    out
                    (let [n (inc n) s (+ s x) ss (+ ss (* x x))
                          m (/ s n) sd (Math/sqrt (max 0.0 (- (/ ss n) (* m m))))]
                      (recur more n s ss (cond-> out (and (>= n 5) (> sd 1e-12)) (assoc y (/ (- x m) sd)))))))])))

(defn exposures
  "{\"world*country\" {iso (sorted-map year value)}}: for each pair [world
  country] of state names, the world series times the country's own (its
  exposure to it: storage pressure times import dependence, say), a
  country series."
  [states pairs]
  (into {} (for [[w c] pairs :let [ws (get-in states [w "WLD"])] :when ws]
             [(str w "*" c) (into {} (for [[iso byy] (get states c) :when (not= iso "WLD")]
                                       [iso (into (sorted-map) (for [[y x] byy :let [g (get ws y)] :when g] [y (* g x)]))]))])))

(defn- item [name] (first (str/split (last (str/split name #"/")) #"[_-]")))

(defn- module [name] (when (str/includes? name "/") (first (str/split name #"/"))))

(defn default-pairs
  "[[world country] ...]: each world series with the country series of its
  item, from the same model, that measure dependence on imports (a
  country's exposure to a world pressure)."
  [states]
  (let [world (set (keys (world-series states)))]
    (vec (for [w world c (keys states)
               :when (and (not (world c)) (= (item w) (item c)) (= (module w) (module c)) (re-find #"import" c) (re-find #"depend" c))]
           [w c]))))

(defn readers
  "{variable (fn [iso year])}: how each state is read as known at a year:
  a world series as its causal z-score (the same at every country), any
  other its own part at the country (bnm.factors), exposures (`pairs`,
  world times country) included."
  [states tree pairs]
  (let [world (world-series states)
        wz (world-z world)
        country (merge (apply dissoc states (keys world)) (exposures states pairs))
        own (own-parts country tree)]
    (merge (into {} (for [[v byy] wz] [v (fn [_ y] (get byy y))]))
           (into {} (for [[v by-iso] own] [v (fn [c y] (get-in by-iso [c y]))])))))
