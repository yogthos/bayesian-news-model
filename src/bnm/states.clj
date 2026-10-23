(ns bnm.states
  "Stock-flow states as country-year tables (data/derived/stocks/*.csv,
  data/derived/material/*.csv: iso,year,variable,value, the year the value
  is known), read for the event lattice (bead bnm-62z.13): each state's own
  part at each country (bnm.factors), causal."
  (:require [bnm.factors :as fac]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn bookkeeping?
  "A variable that describes a model's estimate, not the system: when it
  was calibrated, the year its state refers to, posterior spreads, counts
  and set changes behind an index."
  [v]
  (boolean (re-find #"(?:^|/|-)(?:calibrated|state-year)$|-(?:sd|count|break)$" v)))

(defn- add-line
  "acc with line `l`'s value (split as f, header `ix`), known `lag` years
  after its year; empty values and bookkeeping left out."
  [acc ix f lag]
  (let [g #(nth f (ix %) "")
        v (g "value")]
    (if (and (seq v) (seq (g "iso")) (not (bookkeeping? (g "variable"))))
      (update-in acc [(g "variable") (g "iso")] (fnil assoc (sorted-map)) (+ lag (parse-long (g "year"))) (parse-double v))
      acc)))

(defn- read-file [path lag]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (reduce (fn [acc l] (add-line acc ix (str/split l #"," -1) lag)) {} ls))))

(defn- coupled-tables
  "{table path #{module}}: which modules of the coupled table in `dir`
  (its graph.edn) reproduce each stock table."
  [dir]
  (reduce (fn [m {:keys [module table]}] (if table (update m table (fnil conj #{}) (name module)) m))
          {} (:modules (edn/read-string (slurp (str dir "/graph.edn"))))))

(defn- read-coupled
  "{entry index states}: one pass over dir/state.csv, each line to every
  entry (`take`: {index [modules lag]}) that takes its module."
  [dir take]
  (let [by-module (reduce (fn [m [i [ms lag]]] (reduce #(update %1 %2 (fnil conj []) [i lag]) m ms)) {} take)]
    (with-open [r (io/reader (str dir "/state.csv"))]
      (let [[h & ls] (line-seq r)
            ix (zipmap (str/split h #",") (range))
            mi (ix "module")]
        (reduce (fn [accs l]
                  (let [f (str/split l #"," -1)]
                    (reduce (fn [accs [i lag]] (update accs i add-line ix f lag)) accs (by-module (nth f mi "")))))
                {} ls)))))

(defn read-states
  "{variable {iso (sorted-map year value)}} from long tables
  iso,year,variable,value[,...]; empty values and bookkeeping left out.
  A path is a table by the year its values are known, or [path lag] for
  one by data year (the material base): a value known lag years after its
  year. A later row (or table) wins a key.

  opts :coupled, the engine's output directory (data/derived/coupled,
  bnm.engine): a stock table the coupled table reproduces (by its
  graph.edn) is read from the coupled table's rows of its modules
  instead, in one pass; any other path from its file. Not the default yet
  (phase A, bead bnm-4hv.9)."
  ([paths] (read-states paths {}))
  ([paths {:keys [coupled]}]
   (let [entries (mapv #(if (vector? %) % [% 0]) paths)
         tables (if coupled (coupled-tables coupled) {})
         take (into {} (keep-indexed (fn [i [p lag]] (when-let [ms (tables (str/replace p #"^\./" ""))] [i [ms lag]])) entries))
         coupled-states (when (seq take) (read-coupled coupled take))]
     (reduce (fn [acc [i [path lag]]]
               (let [st (if (contains? take i) (get coupled-states i {}) (read-file path lag))]
                 (merge-with (fn [x y] (merge-with merge x y)) acc st)))
             {} (map-indexed vector entries)))))

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
