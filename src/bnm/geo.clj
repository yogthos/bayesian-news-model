(ns bnm.geo
  "Places, the actors in them, and the hierarchy above them.

  Node ids are strings, so they key a database row as easily as a map:

    \"world\"                  the root
    \"r/asia\"                 a region (UN M49)
    \"s/western-asia\"         a subregion (M49 intermediate region where it has one)
    \"UKR\"                    a country, by ISO 3166 alpha-3
    \"UKR:MIL\"                an actor: a sector of a country (see `sectors`)
    \"UKR.12\"                 a province: a first-level subdivision, by its
                               GeoNames admin1 code (FIPS 10-4 for most countries)
    \"RUS|UKR\"                a dyad: the events between two countries, codes sorted
    \"UKR:GOV|UKR:OPP\"        a pair: the events between two actors of one country
    \"UKR:*\"                  the rest of a country's events: those of no tracked
                               dyad or pair

  Levels, bottom-up: leaves (dyads, pairs, rest leaves), local nodes (actors
  and provinces), countries, subregions, regions, the world. A dyad's parents
  are its two countries and a pair's its two actors, so the hierarchy is a DAG
  rather than a tree. Every event touching a country falls into exactly one of
  that country's leaves; actors and provinces are further views of the same
  events, by who acted and where."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def registry
  (delay (edn/read-string (slurp (io/resource "bnm/countries.edn")))))

(def province-registry
  (delay (edn/read-string (slurp (io/resource "bnm/provinces.edn")))))

(def world "world")

(def sectors
  "The kinds of actor within a country, the codes actor ids use."
  {"GOV" "government: executive, legislature, courts, ruling elites and parties"
   "MIL" "military and other state armed forces"
   "POL" "police, internal security and intelligence"
   "OPP" "opposition: opposition parties, dissidents, protesters"
   "REB" "armed non-state groups: rebels, insurgents, separatists, militias, gangs"
   "IDN" "ethnic and religious groups"
   "SOC" "civil society: civilians, labour, media, education, health, NGOs, refugees"
   "BUS" "business"})

(defn- countries-by-iso3 []
  (into {} (map (juxt :iso3 identity)) (:countries @registry)))

(def ^:private by-iso3 (delay (countries-by-iso3)))

(defn country? [id] (contains? @by-iso3 id))
(defn region? [id] (str/starts-with? id "r/"))
(defn subregion? [id] (str/starts-with? id "s/"))
(defn rest-leaf? [id] (str/ends-with? id ":*"))
(defn actor? [id] (boolean (re-matches #"[A-Z]{3}:[A-Z]{3}" id)))
(defn province? [id] (boolean (re-matches #"[A-Z]{3}\.[A-Z0-9]+" id)))
(defn pair? [id] (boolean (re-matches #"[A-Z]{3}:[A-Z]{3}\|[A-Z]{3}:[A-Z]{3}" id)))
(defn dyad? [id] (boolean (re-matches #"[A-Z]{3}\|[A-Z]{3}" id)))

(def levels
  "Level numbers by name, bottom-up."
  {:leaf 0 :local 1 :country 2 :subregion 3 :region 4 :world 5})

(def level-names (into {} (map (fn [[k v]] [v k])) levels))

(defn level
  "The level number of node `id` (see `levels`)."
  [id]
  (cond (= id world) 5
        (region? id) 4
        (subregion? id) 3
        (or (dyad? id) (pair? id) (rest-leaf? id)) 0
        (or (actor? id) (province? id)) 1
        :else 2))

(defn kind
  "What node `id` is: :dyad :pair :rest :actor :province :country :subregion
  :region or :world."
  [id]
  (cond (= id world) :world
        (region? id) :region
        (subregion? id) :subregion
        (dyad? id) :dyad
        (pair? id) :pair
        (rest-leaf? id) :rest
        (actor? id) :actor
        (province? id) :province
        :else :country))

(defn dyad
  "The leaf between two countries or two actors: their ids, sorted."
  [a b]
  (if (neg? (compare a b)) (str a "|" b) (str b "|" a)))

(defn dyad-countries [id] (str/split id #"\|"))

(def pair-actors dyad-countries)

(defn rest-leaf [iso3] (str iso3 ":*"))

(defn actor [iso3 sector] (str iso3 ":" sector))

(defn country-of
  "The country an actor, province, pair or rest leaf belongs to."
  [id]
  (subs id 0 3))

(defn parents
  "The nodes directly above `id`."
  [id]
  (cond (= id world) []
        (region? id) [world]
        (subregion? id) [(get-in @registry [:subregions id :region])]
        (pair? id) (vec (distinct (pair-actors id)))
        (dyad? id) (dyad-countries id)
        (or (rest-leaf? id) (actor? id) (province? id)) [(country-of id)]
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

(defn province-name [id]
  (get-in @province-registry [:names (country-of id) (subs id 4)]))

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
  "The ISO alpha-3 code for a country name, matched loosely, with or without
  a leading or trailing \"the\" (\"Netherlands\" for GeoNames' \"The
  Netherlands\", \"Bahamas, The\"). nil when unknown."
  [s]
  (when (and s (not (str/blank? s)))
    (let [names (:names @registry)
          k (name-key s)
          bare (-> k (str/replace #"^the " "") (str/replace #" the$" ""))]
      (or (names k) (names bare) (names (str "the " bare))))))

;; --- provinces --------------------------------------------------------------------------

(def ^:private province-words
  "Words that name the kind of subdivision rather than which one."
  #{"province" "provincia" "region" "oblast" "state" "governorate" "prefecture"
    "department" "departement" "county" "district" "territory" "municipality" "city"
    "capital" "national" "federal" "autonomous" "republic" "of" "the" "special"
    "administrative" "metropolitan" "krai" "kray" "voivodeship" "wilayah" "vilayet"
    "muhafazah" "muhafazat" "ostan" "division" "zone" "emirate" "canton" "land"
    "de" "del" "tinh" "changwat" "judetul" "kraj" "ken" "rayon" "wilaya"
    "obl" "regiao" "estado" "departamento" "shi" "sheng" "fu" "e"})

(defn province-key
  "The form a province name is matched in: `name-key` without the words that
  say what kind of subdivision it is."
  [s]
  (str/join " " (remove province-words (str/split (name-key s) #" "))))

(defn resolve-province
  "The province of country `iso3` named `s`, matched loosely. nil when unknown."
  [iso3 s]
  (when (and iso3 s (not (str/blank? s)))
    (let [k (province-key s)]
      (when (seq k)
        (when-let [adm (get-in @province-registry [:keys iso3 k])]
          (str iso3 "." adm))))))

(defn resolve-adm1
  "The province for a GDELT ADM1 code (FIPS country and subdivision, as
  \"UP12\"), when GeoNames knows it."
  [code]
  (when (and code (> (count code) 2))
    (when-let [iso3 (resolve-fips (subs code 0 2))]
      (let [adm (subs code 2)]
        (when (get-in @province-registry [:names iso3 adm])
          (str iso3 "." adm))))))
