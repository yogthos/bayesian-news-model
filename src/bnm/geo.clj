(ns bnm.geo
  "Places and the hierarchy above them.

  Node ids are strings, so they key a database row as easily as a map:

    \"world\"                 the root
    \"r/asia\"                a region (UN M49)
    \"s/western-asia\"        a subregion (M49 intermediate region where it has one)
    \"UKR\"                   a country, by ISO 3166 alpha-3
    \"RUS|UKR\"               a dyad: the events between two countries, codes sorted
    \"UKR:*\"                 the rest of a country's events: domestic, or with a
                              partner whose dyad the model does not track

  A dyad has two parents, its two countries, so the hierarchy is a DAG rather
  than a tree. Every event touching a country falls into exactly one of that
  country's leaves."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def registry
  (delay (edn/read-string (slurp (io/resource "bnm/countries.edn")))))

(def world "world")

(defn- countries-by-iso3 []
  (into {} (map (juxt :iso3 identity)) (:countries @registry)))

(def ^:private by-iso3 (delay (countries-by-iso3)))

(defn country? [id] (contains? @by-iso3 id))
(defn region? [id] (str/starts-with? id "r/"))
(defn subregion? [id] (str/starts-with? id "s/"))
(defn dyad? [id] (str/includes? id "|"))
(defn rest-leaf? [id] (str/ends-with? id ":*"))

(defn level
  "0 for leaves (dyads and rest leaves), up to 4 for the world."
  [id]
  (cond (= id world) 4
        (region? id) 3
        (subregion? id) 2
        (or (dyad? id) (rest-leaf? id)) 0
        :else 1))

(def level-names {0 :leaf 1 :country 2 :subregion 3 :region 4 :world})

(defn dyad [a b]
  (if (neg? (compare a b)) (str a "|" b) (str b "|" a)))

(defn dyad-countries [id] (str/split id #"\|"))

(defn rest-leaf [iso3] (str iso3 ":*"))

(defn rest-leaf-country [id] (subs id 0 (- (count id) 2)))

(defn parents
  "The nodes directly above `id`."
  [id]
  (cond (= id world) []
        (region? id) [world]
        (subregion? id) [(get-in @registry [:subregions id :region])]
        (dyad? id) (dyad-countries id)
        (rest-leaf? id) [(rest-leaf-country id)]
        :else [(:subregion (@by-iso3 id))]))

(defn ancestors-of
  "Every node above `id`, nearest first, without repeats."
  [id]
  (loop [frontier (parents id) seen [] seen? #{}]
    (if-let [[x & more] (seq frontier)]
      (if (seen? x)
        (recur more seen seen?)
        (recur (concat more (parents x)) (conj seen x) (conj seen? x)))
      seen)))

(defn country-name [iso3] (:name (@by-iso3 iso3)))

(defn countries [] (mapv :iso3 (:countries @registry)))
(defn subregions [] (vec (keys (:subregions @registry))))
(defn regions [] (vec (keys (:regions @registry))))

;; --- resolving the codes and names event datasets use --------------------------

(defn resolve-code
  "The node a CAMEO or ISO alpha-3 actor code stands for: a country, or a
  region or subregion for CAMEO's regional codes. nil when unknown."
  [code]
  (when (and code (not= code ""))
    (let [{:keys [code-aliases cameo-regions]} @registry
          code (get code-aliases code code)]
      (cond (country? code) code
            :else (get cameo-regions code)))))

(defn resolve-fips
  "The ISO alpha-3 code for a FIPS 10-4 country code, as GDELT's geography uses."
  [fips]
  (when (and fips (not= fips ""))
    (get-in @registry [:fips fips])))

(defn name-key [s]
  (-> s str/lower-case (str/replace #"[^a-z0-9]+" " ") str/trim))

(defn resolve-name
  "The ISO alpha-3 code for a country name, matched loosely. nil when unknown."
  [s]
  (when (and s (not (str/blank? s)))
    (get-in @registry [:names (name-key s)])))
