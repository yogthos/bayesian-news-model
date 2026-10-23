(ns bnm.cell-assets-test
  (:require [bnm.cell-assets :as ca]
            [bnm.cells :as cells]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(deftest source-countries
  (is (= "AGO" (ca/resolve-country "AGO" "iso3")))
  (is (= "XKX" (ca/resolve-country "KOS" "iso3")) "Kosovo under one code")
  (is (= "ARG" (ca/resolve-country "AR" "iso2")))
  (is (= "UKR" (ca/resolve-country "UP" "fips")))
  (is (= "USA" (ca/resolve-country "UNITED STATES OF AMERICA" "name")))
  (is (= "RUS" (ca/resolve-country "Russian Federation" "name")))
  (is (nil? (ca/resolve-country "Atlantis" "name")))
  (is (nil? (ca/resolve-country "" "iso3")))
  (is (= "ARE" (ca/resolve-country "TC" "fips")) "PRIO's FIPS for the Emirates, before GEC's AE")
  (is (= "SRB" (ca/resolve-country "SR" "fips")) "PRIO's for Serbia")
  (is (= "VIR" (ca/resolve-country "US VIRGIN ISLANDS" "name"))))

(def berlin (cells/cell 13.405 52.52))

(defn- tmp [content]
  (let [f (java.io.File/createTempFile "xwalk" ".csv")]
    (spit f content)
    (str f)))

(def xw (cells/read-crosswalk (tmp (str "cell,iso,admin1,share,flag\n" berlin ",DEU,DEU.16,1,land\n"))))

(deftest asset-rows
  (testing "a point: one row, the whole capacity"
    (let [[r] (ca/point-rows xw [{:source "sfi" :id "sfi:1" :asset_type "steel_plant" :commodity "steel_eaf"
                                  :capacity 2.0 :unit "Mt/y" :start_year 1990 :status "operating"
                                  :known_from 1990 :year_flag "start" :iso "DEU" :lon 13.405 :lat 52.52}])]
      (is (= {:cell berlin :iso "DEU" :admin1 "DEU.16" :asset_type "steel_plant" :commodity "steel_eaf"
              :capacity 2.0 :unit "Mt/y" :start_year 1990 :status "operating" :source "sfi" :asset_id "sfi:1"
              :share 1.0 :known_from 1990 :year_flag "start" :dup_of nil :xw_iso nil}
             (dissoc r :area_km2)))))
  (testing "a duplicate keeps its row, naming the asset it repeats"
    (let [[r] (ca/point-rows xw [{:source "usgs-minfac" :id "usgs:9" :asset_type "processing_plant"
                                  :commodity "iron and steel" :known_from 2021 :year_flag "snapshot"
                                  :iso "DEU" :lon 13.405 :lat 52.52 :dup_of "sfi:1"}])]
      (is (= "sfi:1" (:dup_of r)))))
  (testing "the crosswalk's country, where the source's differs"
    (let [[r] (ca/point-rows xw [{:source "sfi" :id "sfi:2" :asset_type "steel_plant" :known_from 2021
                                  :year_flag "snapshot" :iso "POL" :lon 13.405 :lat 52.52}])]
      (is (= ["POL" "DEU"] ((juxt :iso :xw_iso) r)) "the source's kept, the crosswalk's beside it")))
  (testing "a polygon: capacity shared by area, the part's area"
    (let [sq [[[13.40 52.50] [13.41 52.50] [13.41 52.51] [13.40 52.51] [13.40 52.50]]]
          rs (ca/polygon-rows xw [{:source "maus" :id "maus:1" :asset_type "mining_area" :commodity ""
                                   :capacity 1.2 :unit "km2" :known_from 2022 :year_flag "snapshot"
                                   :iso "DEU" :polygons [sq]}])]
      (is (= 1 (count rs)))
      (is (= 1.2 (:capacity (first rs))))
      (is (< (Math/abs (- (:area_km2 (first rs)) (cells/polygon-area-km2 sq))) 1e-9))
      (is (= "snapshot" (:year_flag (first rs)))))))

(deftest gppd-known-years
  (let [[dated undated] (ca/gppd-points [{:iso "DEU" :fuel "coal" :mw 500.0 :lon 13.4 :lat 52.5 :year 1975}
                                         {:iso "DEU" :fuel "gas" :mw 50.0 :lon 13.4 :lat 52.5 :year nil}])]
    (is (= [1975 1975 "start"] ((juxt :start_year :known_from :year_flag) dated)))
    (is (= [nil 2021 "snapshot"] ((juxt :start_year :known_from :year_flag) undated))
        "no commissioning year: known from the snapshot's publication only")))

(deftest the-columns
  (is (= [:cell :iso :admin1 :asset_type :commodity :capacity :unit :start_year :status :source
          :asset_id :share :area_km2 :known_from :year_flag :dup_of :xw_iso]
         ca/columns)))

(deftest csv-out
  (is (= "a,\"b,c\",,1.5" (ca/csv-line ["a" "b,c" nil 1.5]))))
