(ns bnm.cell-assets
  "Plants and resources on A5 cells (docs/PRINCIPLES.md, principle 11; bead
  bnm-3ka.10): the point and polygon sources scripts/cell-assets.py reads,
  and the WRI power plants (bnm.material), placed on the cell crosswalk
  (bnm.cells). Writes data/derived/cells/assets.csv

    cell,iso,admin1,asset_type,commodity,capacity,unit,start_year,status,
    source,asset_id,share,area_km2,known_from,year_flag

  one row per asset and cell x country x province it covers: a point is one
  row with share 1; a polygon one row per part, its share of the polygon's
  area (area_km2) and capacity (a field's or mine's area, when that is its
  capacity) apportioned by it. The source's country is kept where the
  crosswalk puts the asset in another (bnm.cells/place-points); the counts
  go to data/derived/cells/assets-report.edn.

  known_from is when the asset is known to exist: its start year (year_flag
  start), its discovery year (discovery), or, with neither, the year its
  snapshot was published (snapshot), never earlier: a snapshot asset read
  into earlier years must carry that flag.

    python -I scripts/cells.py && python -I scripts/cell-assets.py
    jolt -A:tools -M -m bnm.cell-assets"
  (:require [bnm.cells :as cells]
            [bnm.csv :as csv]
            [bnm.geo :as geo]
            [bnm.material :as mat]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(def gppd-path "data/raw/material/gppd/x/global_power_plant_database.csv")

(def columns
  [:cell :iso :admin1 :asset_type :commodity :capacity :unit :start_year :status :source
   :asset_id :share :area_km2 :known_from :year_flag])

(def ^:private iso2->iso3
  (delay (into {} (keep (fn [{:keys [iso2 iso3]}] (when (seq iso2) [iso2 iso3]))) (:countries @geo/registry))))

(defn resolve-country
  "The ISO alpha-3 code bnm.geo uses for a source's country `s`, given as
  `kind` iso3, iso2, fips or name; nil when unknown."
  [s kind]
  (when-not (str/blank? s)
    (let [s (str/trim s)
          iso (case kind
                "iso3" (geo/resolve-code s)
                "iso2" (@iso2->iso3 s)
                "fips" (geo/resolve-fips s)
                "name" (or (geo/resolve-name s) (geo/resolve-name (str/replace s #"(?i)\s*\(.*\)" ""))))]
      (when (and iso (geo/country? iso)) iso))))

(defn gppd-points
  "WRI power plants (bnm.material/read-plants) as asset points: the
  commissioning year when given, else known from the database's publication
  (2021)."
  [plants]
  (vec (map-indexed (fn [i {:keys [iso fuel mw lon lat year]}]
                      {:source "gppd" :id (str "gppd:" i) :asset_type "power_plant" :commodity fuel
                       :capacity mw :unit "MW" :start_year year :status "operating"
                       :known_from (or year mat/vintage) :year_flag (if year "start" "snapshot")
                       :iso iso :lon lon :lat lat})
                    plants)))

(defn- row [m]
  (select-keys m columns))

(defn point-rows
  "Asset points ({:lon :lat :iso ...}) as asset rows on the crosswalk."
  [xw points]
  (let [{:keys [points]} (cells/place-points xw points)]
    (mapv (fn [p] (row (assoc p :asset_id (:id p) :share 1.0 :area_km2 nil))) points)))

(defn polygon-rows
  "Asset polygons ({:polygons [rings ...] :iso ...}) as asset rows, one per
  part, capacity apportioned by area."
  [xw polys]
  (vec (mapcat (fn [p]
                 (let [{:keys [parts]} (cells/place-polygon xw p)]
                   (for [{:keys [cell iso admin1 share area]} parts]
                     (row (assoc p :cell cell :iso iso :admin1 admin1 :asset_id (:id p) :share share
                                 :area_km2 area :capacity (some-> (:capacity p) (* share)))))))
               polys)))

(defn csv-line [fields]
  (str/join "," (map (fn [v]
                       (let [s (if (nil? v) "" (str v))]
                         (if (re-find #"[\",\n]" s) (str "\"" (str/replace s "\"" "\"\"") "\"") s)))
                     fields)))

(defn- number [s] (when-not (str/blank? s) (parse-double s)))
(defn- int-year [s] (some-> (number s) Math/round long))

(defn read-points
  "data/derived/cells/asset-points.csv (scripts/cell-assets.py), those with
  coordinates."
  [path]
  (filterv #(and (:lon %) (:lat %) (<= -180 (:lon %) 180) (<= -90 (:lat %) 90))
   (map (fn [{:keys [country country_kind lon lat capacity start_year known_from] :as m}]
          (assoc m :iso (resolve-country country country_kind) :lon (number lon) :lat (number lat)
                 :capacity (number capacity) :start_year (int-year start_year)
                 :known_from (int-year known_from)))
        (csv/read-maps path \,))))

(defn read-polygons
  "data/derived/cells/asset-polygons.edn, one map a line."
  [path]
  (with-open [r (io/reader path)]
    (mapv (fn [l] (let [{:keys [country country_kind] :as m} (edn/read-string l)]
                    (assoc m :iso (resolve-country country country_kind))))
          (line-seq r))))

(defn -main [& _]
  (let [xw (cells/load-crosswalk)
        _ (println "crosswalk:" (count (:rows xw)) "rows," (count (:fine xw)) "fine parts")
        pts (into (gppd-points (mat/read-plants gppd-path)) (read-points "data/derived/cells/asset-points.csv"))
        polys (read-polygons "data/derived/cells/asset-polygons.edn")
        unresolved (frequencies (keep (fn [{:keys [source iso country]}] (when (nil? iso) [source country]))
                                      (concat pts polys)))
        by-source (group-by :source pts)
        placed (into {} (for [[s ps] by-source] [s (cells/place-points xw ps)]))
        prows (vec (mapcat (fn [[_ {:keys [points]}]]
                             (map (fn [p] (row (assoc p :asset_id (:id p) :share 1.0 :area_km2 nil))) points))
                           placed))
        _ (do (println (count prows) "point rows") (flush))
        poly-placed (vec (map-indexed (fn [i p]
                                        (when (zero? (mod i 5000)) (println "polygon" i) (flush))
                                        [p (cells/place-polygon xw p)])
                                      polys))
        lrows (vec (for [[p {:keys [parts]}] poly-placed
                         {:keys [cell iso admin1 share area]} parts]
                     (row (assoc p :cell cell :iso iso :admin1 admin1 :asset_id (:id p) :share share
                                 :area_km2 area :capacity (some-> (:capacity p) (* share))))))
        rows (sort-by (juxt :source :asset_id :cell :iso) (concat prows lrows))
        report {:assets (into (sorted-map)
                              (frequencies (map (juxt :source :asset_type) (concat pts polys))))
                :rows (count rows)
                :rows-without-country (count (filter #(str/blank? (:iso %)) rows))
                :point-disagreements (into (sorted-map) (for [[s {:keys [disagree]}] placed] [s disagree]))
                :polygon-elsewhere (into (sorted-map)
                                         (for [[s ps] (group-by (comp :source first) poly-placed)]
                                           [s {:polygons (count ps)
                                               :with-source-country (count (filter (comp :iso first) ps))
                                               :mostly-elsewhere (count (filter #(> (:elsewhere (second %)) 0.5) ps))
                                               :partly-elsewhere (count (filter #(< 0.0 (:elsewhere (second %)) 0.5) ps))}]))
                :unresolved-countries (into (sorted-map) (for [[s m] (group-by ffirst unresolved)]
                                                            [s (into (sorted-map) (map (fn [[[_ c] n]] [c n])) m)]))
                :by-year-flag (into (sorted-map) (frequencies (map (juxt :source :year_flag) (concat pts polys))))}]
    (with-open [w (io/writer "data/derived/cells/assets.csv")]
      (.write w (str (str/join "," (map name columns)) "\n"))
      (doseq [r rows] (.write w (str (csv-line (map r columns)) "\n"))))
    (spit "data/derived/cells/assets-report.edn" (with-out-str (pp/pprint report)))
    (pp/pprint (dissoc report :unresolved-countries))))
