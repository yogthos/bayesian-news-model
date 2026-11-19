(ns bnm.blocs
  "A bloc rupture's cost on real data (bnm-58f, docs/ALIGNMENT-DATA.md):
  when the west breaks with a power, the cost falls on countries tied to
  the west that depended on trade with it. Exposure = dependence on the
  power x the western tie, each as known when the rupture came, for the 24
  months after it; a node's offset adds k x the exposure (testbed.clj's
  :bloc-theory, docs/TESTBED.md)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def ruptures
  "Dated from the public record, known at once: the US and EU sanctions on
  Russia of 17 March 2014 and of 24-26 February 2022 (months, year x 12 +
  month - 1)."
  [{:power "russia" :month (+ (* 12 2014) 2)}
   {:power "russia" :month (+ (* 12 2022) 1)}])

(def window "Months a rupture's exposure lasts." 24)

(defn read-data
  "{:dep {iso {year {:value :known}}} :tie {iso {year value}}} from the
  alignment files (dep_russia only, the one power with a rupture)."
  [annual tie]
  (let [rows (fn [p] (with-open [r (io/reader p)]
                       (let [[h & xs] (line-seq r)
                             ks (map keyword (str/split h #","))]
                         (mapv #(zipmap ks (str/split % #",")) xs))))]
    {:dep (reduce (fn [acc {:keys [iso3 year var value known_from_year]}]
                    (if (= var "dep_russia")
                      (assoc-in acc [iso3 (parse-long year)] {:value (parse-double value) :known (parse-long known_from_year)})
                      acc))
                  {} (rows annual))
     :tie (reduce (fn [acc {:keys [iso3 year var value]}]
                    (if (= var "western_tie") (assoc-in acc [iso3 (parse-long year)] (parse-double value)) acc))
                  {} (rows tie))}))

(defn- dep-known
  "The latest year's dependence known in `year`."
  [data iso year]
  (some->> (get-in data [:dep iso])
           (filter (fn [[_ {:keys [known]}]] (<= known year)))
           seq (apply max-key key) val :value))

(defn exposure
  "Country `iso`'s exposure at month t: over the ruptures within `window`
  months before t, dependence known in the rupture's year x the tie at the
  end of the year before; 0 where either is missing."
  [data iso t]
  (reduce + 0.0
          (for [{:keys [month]} ruptures
                :when (and (<= month t) (< t (+ month window)))
                :let [y (quot month 12)
                      d (dep-known data iso y)
                      w (get-in data [:tie iso (dec y)])]
                :when (and d w)]
            (* d w))))

(defn offset-add
  "bnm.lattice's :offset-add: k x a country's exposure, nil where it has
  none or for a place with no data (aggregates keep their own offsets)."
  [data k]
  (fn [_ place t]
    (let [e (exposure data place t)]
      (when (pos? e) (* k e)))))
