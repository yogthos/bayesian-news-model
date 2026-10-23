(ns bnm.cell-profiles-test
  (:require [bnm.cell-profiles :as cp]
            [bnm.cells :as cells]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- tmp [content]
  (let [f (java.io.File/createTempFile "cprof" ".csv")]
    (spit f content)
    (str f)))

(def header
  "cell,iso,admin1,asset_type,commodity,capacity,unit,start_year,status,source,asset_id,share,area_km2,known_from,year_flag,dup_of,xw_iso,end_year,end_flag,end_known\n")

(def berlin (cells/cell 13.405 52.52))
(def munich (cells/cell 11.58 48.14))
(def warsaw (cells/cell 21.01 52.23))

(defn- a [m]
  (merge {:cell berlin :iso "DEU" :admin1 "DEU.16" :share 1.0 :known_from 1990 :year_flag "start"} m))

(deftest reading-assets
  (let [f (tmp (str header
                    berlin ",DEU,DEU.16,steel_plant,steel_bf,2.0,Mt/y,1990,operating,sfi,sfi:1,1.0,,1990,start,,,,,\n"
                    berlin ",DEU,DEU.16,processing_plant,iron and steel,,,,assumed active,usgs-minfac,usgs:1,1.0,,2021,snapshot,sfi:1,,,,\n"
                    berlin ",DEU,DEU.16,mine,iron,,,1900,past producer,mrds,mrds:7,1.0,,1900,start,,,1950,last-production,2011\n"))
        rs (cp/read-assets f)]
    (testing "rows naming a duplicate are skipped"
      (is (= ["sfi:1" "mrds:7"] (mapv :asset_id rs))))
    (testing "numbers and end years parsed"
      (is (= 2.0 (:capacity (first rs))))
      (is (= [1950 "last-production" 2011] ((juxt :end_year :end_flag :end_known) (second rs)))))
    (testing "a file without the end columns reads, ends blank"
      (let [old (tmp (str "cell,iso,admin1,asset_type,commodity,capacity,unit,start_year,status,source,asset_id,share,area_km2,known_from,year_flag,dup_of,xw_iso\n"
                          berlin ",DEU,DEU.16,mine,iron,,,,producer,mrds,mrds:8,1.0,,2011,snapshot,,\n"))]
        (is (nil? (:end_known (first (cp/read-assets old)))))))))

(deftest present-in-a-year
  (testing "known from its known year, not before"
    (is (not (cp/present? (a {:known_from 2000}) 1999)))
    (is (cp/present? (a {:known_from 2000}) 2000)))
  (testing "a last production year: absent after it, once the closure is reported"
    (let [m (a {:known_from 1900 :end_year 1950 :end_flag "last-production" :end_known 2011})]
      (is (cp/present? m 1949))
      (is (cp/present? m 1990) "the closure is not known until MRDS reports it: never backdated")
      (is (not (cp/present? m 2011)))
      (is (not (cp/present? m 2020)))))
  (testing "a last year after the report: present until it"
    (let [m (a {:known_from 1900 :end_year 2015 :end_flag "last-production" :end_known 2011})]
      (is (cp/present? m 2015))
      (is (not (cp/present? m 2016)))))
  (testing "closed with no year: absent from the snapshot on"
    (let [m (a {:known_from 1980 :end_flag "closed-unknown" :end_known 2021})]
      (is (cp/present? m 2020))
      (is (not (cp/present? m 2021))))))

(deftest backcast-presence
  (testing "a snapshot asset read back to its start year (descriptive, not causal)"
    (is (cp/present-backcast? (a {:start_year 1980 :known_from 2021 :year_flag "start"}) 1990))
    (is (not (cp/present-backcast? (a {:start_year 1995 :known_from 2021}) 1990)))
    (is (cp/present-backcast? (a {:start_year nil :known_from 2026 :year_flag "snapshot"}) 1990)
        "undated: present throughout"))
  (testing "ended by its last year, or from the report when it has none"
    (is (not (cp/present-backcast? (a {:start_year 1900 :end_year 1950 :end_known 2011}) 1960)))
    (is (cp/present-backcast? (a {:start_year 1900 :end_year 1950 :end_known 2011}) 1950))
    (is (cp/present-backcast? (a {:start_year nil :end_flag "closed-unknown" :end_known 2021}) 2020))
    (is (not (cp/present-backcast? (a {:start_year nil :end_flag "closed-unknown" :end_known 2021}) 2021)))))

(deftest asset-keys-and-capacity
  (is (= "steel" (cp/asset-key (a {:asset_type "steel_plant" :commodity "steel_bf"}))))
  (is (= "refinery" (cp/asset-key (a {:asset_type "refinery" :commodity "oil"}))))
  (is (= "alumina-plant" (cp/asset-key (a {:asset_type "refinery" :commodity "alumina"}))))
  (is (= "aluminum-plant" (cp/asset-key (a {:asset_type "processing_plant" :commodity "aluminum"}))))
  (is (= "power-coal" (cp/asset-key (a {:asset_type "power_plant" :commodity "coal"}))))
  (is (= "mine-iron" (cp/asset-key (a {:asset_type "mine" :commodity "iron"}))))
  (is (= "field" (cp/asset-key (a {:asset_type "field" :commodity "oil_gas"}))))
  (is (nil? (cp/asset-key (a {:asset_type "deposit" :commodity "iron"}))) "a deposit is not an asset in use")
  (is (= :extraction (cp/group-of "mine-iron")))
  (is (= :processing (cp/group-of "steel")))
  (is (= :power (cp/group-of "power-gas")))
  (testing "capacity in the key's unit: tonnes for materials"
    (is (= 2.0e6 (cp/capacity (a {:asset_type "steel_plant" :commodity "steel" :capacity 2.0 :unit "Mt/y"}))))
    (is (= 5000.0 (cp/capacity (a {:asset_type "processing_plant" :commodity "copper" :capacity 5.0 :unit "Thousand metric tons"}))))
    (is (= 3.0 (cp/capacity (a {:asset_type "processing_plant" :commodity "copper" :capacity 3000.0 :unit "kg"}))))
    (is (nil? (cp/capacity (a {:asset_type "processing_plant" :commodity "copper" :capacity 3.0 :unit "carats"}))))
    (is (= 100.0 (cp/capacity (a {:asset_type "refinery" :commodity "oil" :capacity 100.0 :unit "bbl/d"}))))))

(deftest hhi-arithmetic
  (let [rs [(a {:asset_type "steel_plant" :commodity "steel" :capacity 3.0 :unit "Mt/y"})
            (a {:cell munich :admin1 "DEU.02" :asset_type "steel_plant" :commodity "steel" :capacity 1.0 :unit "Mt/y"})
            (a {:cell warsaw :iso "POL" :admin1 "POL.78" :asset_type "steel_plant" :commodity "steel" :capacity 4.0 :unit "Mt/y"})]
        c (cp/concentration rs "steel" 2000)]
    (testing "worldwide over cells"
      (is (< (Math/abs (- (+ (* 0.375 0.375) (* 0.125 0.125) (* 0.5 0.5)) (get-in c [:world :hhi]))) 1e-12))
      (is (= [warsaw 0.5] ((juxt :top :top-share) (:world c))))
      (is (= 3 (get-in c [:world :cells]))))
    (testing "within a country"
      (is (< (Math/abs (- 0.625 (get-in c [:countries "DEU" :hhi]))) 1e-12))
      (is (= 1.0 (get-in c [:countries "POL" :hhi]))))
    (testing "a plant not yet known is left out"
      (is (= 1.0 (get-in (cp/concentration (conj (vec (take 1 rs)) (a {:asset_type "steel_plant" :commodity "steel"
                                                                     :capacity 1.0 :unit "Mt/y" :cell munich
                                                                     :known_from 2005}))
                                          "steel" 2000)
                         [:world :hhi]))))
    (testing "two parts of one cell count as one cell worldwide"
      (let [rs [(a {:asset_type "cement_plant" :commodity "cement" :capacity 1.0 :unit "Mt/y"})
                (a {:iso "POL" :asset_type "cement_plant" :commodity "cement" :capacity 1.0 :unit "Mt/y"})]]
        (is (= 1.0 (get-in (cp/concentration rs "cement" 2000) [:world :hhi])))))
    (testing "as stock rows"
      (let [out (cp/hhi-stocks rs ["steel"] [2000])]
        (is (some #(= {:iso "WLD" :year 2000 :variable "cells/steel-hhi"} (select-keys % [:iso :year :variable])) out))
        (is (= warsaw (:top (first (filter #(and (= "WLD" (:iso %)) (= "cells/steel-top-share" (:variable %))) out)))))))))

(deftest exiobase-regions
  (is (= "DE" (cp/exio-region "DEU")))
  (is (= "WF" (cp/exio-region "NGA")))
  (is (= "WM" (cp/exio-region "SAU")))
  (is (= "WA" (cp/exio-region "TWN")) "every country no region takes")
  (is (= "WA" (cp/exio-region "VNM"))))

(deftest cell-cuts
  (let [rs [(a {:asset_type "steel_plant" :commodity "steel" :capacity 3.0 :unit "Mt/y"})
            (a {:cell munich :admin1 "DEU.02" :asset_type "steel_plant" :commodity "steel" :capacity 1.0 :unit "Mt/y"})
            (a {:cell warsaw :iso "POL" :admin1 "POL.78" :asset_type "steel_plant" :commodity "steel" :capacity 4.0 :unit "Mt/y"})
            (a {:asset_type "power_plant" :commodity "coal" :capacity 100.0 :unit "MW"})
            (a {:cell munich :admin1 "DEU.02" :asset_type "power_plant" :commodity "coal" :capacity 300.0 :unit "MW"})]]
    (testing "the share of each region's capacity of each product in the cut cells"
      (let [cut (cp/cell-cut rs #{berlin} {:year 2000})]
        (is (< (Math/abs (- 0.75 (cut ["DE" "C_STEL"]))) 1e-12))
        (is (< (Math/abs (- 0.25 (cut ["DE" "C_POWC"]))) 1e-12))
        (is (nil? (cut ["PL" "C_STEL"])) "no capacity cut in Poland")))
    (testing "a whole region cut is 1"
      (is (= 1.0 ((cp/cell-cut rs #{warsaw} {:year 2000}) ["PL" "C_STEL"]))))
    (testing "a cell x country key cuts only that country's part"
      (let [rs (conj rs (a {:iso "POL" :admin1 "POL.78" :asset_type "steel_plant" :commodity "steel" :capacity 4.0 :unit "Mt/y"}))
            cut (cp/cell-cut rs #{(cells/cell-key berlin "POL")} {:year 2000})]
        (is (= 0.5 (cut ["PL" "C_STEL"])))
        (is (nil? (cut ["DE" "C_STEL"])))))
    (testing "missing capacity takes the key's median"
      (let [rs (conj rs (a {:cell munich :admin1 "DEU.02" :asset_type "steel_plant" :commodity "steel" :capacity nil}))
            ;; steel capacities 3, 1, 4 Mt: median 3; DE has 3 + 1 + 3
            cut (cp/cell-cut rs #{munich} {:year 2000})]
        (is (< (Math/abs (- (/ 4.0 7.0) (cut ["DE" "C_STEL"]))) 1e-12))))
    (testing "a capacity far above its key's median (ore throughput, a unit slip) counts as the median"
      (let [rs (vec (concat (for [i (range 5)] (a {:cell munich :admin1 "DEU.02" :asset_type "processing_plant"
                                                   :commodity "gold" :capacity 3.0 :unit "kg" :asset_id (str "g" i)}))
                            [(a {:asset_type "processing_plant" :commodity "gold" :capacity 2.0
                                 :unit "Thousand metric tons" :asset_id "g9"})]))
            c (cp/concentration rs "gold-plant" 2000)]
        (is (= munich (get-in c [:world :top])))
        (is (< (Math/abs (- (/ 5.0 6.0) (get-in c [:world :top-share]))) 1e-12)
            "the 2,000 t gold plant weighs as a 3 kg one")
        (let [ps (vec (concat (for [i (range 5)] (a {:cell munich :asset_type "power_plant" :commodity "hydro"
                                                     :capacity 1.0 :unit "MW"}))
                              [(a {:asset_type "power_plant" :commodity "hydro" :capacity 22500.0 :unit "MW"})]))]
          (is (= berlin (get-in (cp/concentration ps "power-hydro" 2000) [:world :top]))
              "a big dam is not an outlier"))))
    (testing "an asset ended by the year is not capacity"
      (let [rs (conj rs (a {:cell munich :admin1 "DEU.02" :asset_type "steel_plant" :commodity "steel" :capacity 4.0
                            :unit "Mt/y" :end_flag "closed-unknown" :end_known 1999}))]
        (is (< (Math/abs (- 0.75 ((cp/cell-cut rs #{berlin} {:year 2000}) ["DE" "C_STEL"]))) 1e-12))))))

;; --- grids and profiles ---------------------------------------------------------------

(def grid-csv
  (str "cell,iso,year,variable,value,known\n"
       berlin ",DEU,1998,ntl/dn,100,1999\n"
       berlin ",DEU,1999,ntl/dn,200,2000\n"
       berlin ",DEU,2000,ntl/dn,400,2001\n"
       munich ",DEU,1999,ntl/dn,50,2000\n"
       berlin ",DEU,1995,ghsl/pop,1000,1998\n"
       berlin ",DEU,2000,ghsl/pop,2000,2003\n"))

(deftest grids-as-known
  (let [g (cp/read-grids (tmp grid-csv))]
    (testing "the latest year known by Y, never one known later"
      (is (= [1999 200.0] (cp/known-value g "ntl/dn" (cells/cell-key berlin "DEU") 2000)))
      (is (= [1998 100.0] (cp/known-value g "ntl/dn" (cells/cell-key berlin "DEU") 1999)))
      (is (nil? (cp/known-value g "ntl/dn" (cells/cell-key berlin "DEU") 1998)))
      (is (= [1995 1000.0] (cp/known-value g "ghsl/pop" (cells/cell-key berlin "DEU") 2002))
          "the 2000 epoch is known only in 2003"))
    (testing "a value a year before the latest known (for growth)"
      (is (= [1998 100.0] (cp/known-value g "ntl/dn" (cells/cell-key berlin "DEU") 2000 1))))))

(deftest intensities
  (let [k (cells/cell-key berlin "DEU")
        rs [(a {:asset_type "mine" :commodity "iron" :known_from 1900 :end_year 1950 :end_flag "last-production" :end_known 2011})
            (a {:asset_type "mine" :commodity "gold" :known_from 1990})
            (a {:asset_type "mine" :commodity "sand and gravel" :known_from 1990})
            (a {:asset_type "steel_plant" :commodity "steel" :capacity 1.0 :unit "Mt/y" :known_from 1995})]]
    (testing "counts of assets present in the year, by part"
      (is (= 2.0 (get-in (cp/asset-counts rs 2000) [k :extraction])) "the iron mine counts until its closure is known")
      (is (= 1.0 (get-in (cp/asset-counts rs 2015) [k :extraction])) "absent after its last year, once reported")
      (is (= 1.0 (get-in (cp/asset-counts rs 2000) [k :processing])))
      (is (nil? (get-in (cp/asset-counts rs 1992) [k :processing])))
      (is (= 1.0 (get-in (cp/asset-counts rs 2015) [k :lootable-gold]))))))

(deftest kmeans-recovers-groups
  (let [xs (vec (concat (for [i (range 20)] [(+ 0.0 (* 0.01 i)) 0.0])
                        (for [i (range 20)] [(+ 5.0 (* 0.01 i)) 5.0])))
        {:keys [centroids labels]} (cp/kmeans xs 2 {:seed 1})]
    (is (= 2 (count centroids)))
    (is (apply = (take 20 labels)))
    (is (apply = (drop 20 labels)))
    (is (not= (first labels) (last labels)))
    (testing "a warm start keeps the labels"
      (let [r2 (cp/kmeans xs 2 {:init centroids})]
        (is (= labels (:labels r2)))))))

(deftest standardising
  (let [s (cp/scaler [[1.0 10.0] [3.0 10.0]])]
    (is (= [-1.0 0.0] (cp/scale s [1.0 10.0])) "a constant column scales to 0, not NaN")))

(deftest transitions
  (let [types {[2000 "a"] 0 [2005 "a"] 1 [2000 "b"] 0 [2005 "b"] 0 [2000 "c"] 1}
        m (cp/transition-counts types [2000 2005])]
    (is (= {[0 1] 1 [0 0] 1} m) "c has no 2005 type: no transition")))

(deftest displacement-on-cells
  (testing "processing in the mine cells: D 0, distance 0"
    (let [r (cp/cell-displacement {"x" 1.0 "y" 1.0} {"x" 2.0 "y" 2.0} {"x" [0.0 0.0] "y" [10.0 0.0]})]
      (is (< (Math/abs (:d r)) 1e-12))
      (is (< (:km r) 1e-9))))
  (testing "processing elsewhere: D 1, the distance to the nearest plant"
    (let [r (cp/cell-displacement {"x" 1.0} {"y" 1.0} {"x" [0.0 0.0] "y" [1.0 0.0]})]
      (is (= 1.0 (:d r)))
      (is (< (Math/abs (- 111.2 (:km r))) 0.5)))))

(deftest province-split
  (testing "a cell x country value over its provinces by land share, conserving the total"
    (let [xw {:by-cell {"c1" [{:cell "c1" :iso "DEU" :admin1 "DEU.01" :share 0.3 :flag "land"}
                              {:cell "c1" :iso "DEU" :admin1 "DEU.02" :share 0.1 :flag "land"}
                              {:cell "c1" :iso "DEU" :admin1 nil :share 0.2 :flag "eez"}
                              {:cell "c1" :iso "POL" :admin1 "POL.01" :share 0.4 :flag "land"}]}}
          p (cp/to-provinces xw {"DEU@c1" 8.0 "POL@c1" 2.0})]
      (is (= {"DEU.01" 6.0 "DEU.02" 2.0 "POL.01" 2.0} (update-vals p #(/ (Math/round (* 1e9 %)) 1e9)))))))

;; --- review fixes ---------------------------------------------------------------------

(deftest medians-over-whole-assets
  (testing "a polygon's pieces sum to the asset before the median: a field of 3
            pieces is one 90 km2 field, not three 30 km2 ones"
    (let [rs (concat (for [i (range 3)] (a {:cell (nth [berlin munich warsaw] i) :asset_type "field" :commodity "oil"
                                            :capacity 30.0 :unit "km2" :share (/ 1.0 3) :asset_id "f1"}))
                     [(a {:asset_type "field" :commodity "oil" :capacity 90.0 :unit "km2" :asset_id "f2"})
                      (a {:asset_type "field" :commodity "oil" :capacity 90.0 :unit "km2" :asset_id "f3"})])]
      (is (= 90.0 ((cp/medians rs) "field"))))))

(deftest cuts-of-polygons-across-cells
  (let [rs [(a {:cell berlin :asset_type "field" :commodity "oil" :capacity 60.0 :unit "km2" :share 0.6 :asset_id "f1"})
            (a {:cell munich :asset_type "field" :commodity "oil" :capacity 40.0 :unit "km2" :share 0.4 :asset_id "f1"})
            (a {:cell munich :asset_type "field" :commodity "oil" :capacity 100.0 :unit "km2" :asset_id "f2"})]]
    (testing "a field across two cells is cut by the piece in the cut cell"
      (is (< (Math/abs (- 0.3 ((cp/cell-cut rs #{berlin} {:year 2000}) ["DE" "C_COIL"]))) 1e-12)))
    (testing "a piece with no area takes the whole-asset median by its share"
      (let [rs (conj rs (a {:cell berlin :asset_type "field" :commodity "oil" :capacity nil :share 0.5 :asset_id "f3"}))]
        ;; medians over assets f1 100 and f2 100: f3's piece weighs 50
        (is (< (Math/abs (- (/ 110.0 250.0) ((cp/cell-cut rs #{berlin} {:year 2000}) ["DE" "C_COIL"]))) 1e-12))))))

(deftest lng-cuts-against-the-region's-gas
  (let [rs [(a {:cell warsaw :iso "QAT" :asset_type "lng" :commodity "lng_export" :capacity 1000.0 :unit "mmcf/d"})
            (a {:cell munich :iso "OMN" :asset_type "lng" :commodity "lng_export" :capacity 500.0 :unit "mmcf/d"})
            (a {:cell berlin :iso "QAT" :asset_type "field" :commodity "gas" :capacity 10.0 :unit "km2"})
            (a {:cell munich :iso "IRN" :asset_type "field" :commodity "gas" :capacity 90.0 :unit "km2"})]
        prod {["WM" "C_GASE"] 2.0e7}]
    (testing "without the region's output an LNG terminal cuts nothing"
      (is (nil? ((cp/cell-cut rs #{warsaw} {:year 2000}) ["WM" "C_GASE"]))))
    (testing "an export terminal cuts the gas it ships, over the region's gas output"
      (is (< (Math/abs (- (/ (* 1000.0 cp/lng-tonnes-per-mmcfd) 2.0e7)
                          ((cp/cell-cut rs #{warsaw} {:year 2000 :production prod}) ["WM" "C_GASE"])))
             1e-12)))
    (testing "fields and terminals cut together: the larger loss binds"
      (let [x ((cp/cell-cut rs #{warsaw berlin} {:year 2000 :production prod}) ["WM" "C_GASE"])]
        (is (< (Math/abs (- (max 0.1 (/ (* 1000.0 cp/lng-tonnes-per-mmcfd) 2.0e7)) x)) 1e-12))))
    (testing "capped at the whole output"
      (is (= 1.0 ((cp/cell-cut rs #{warsaw} {:year 2000 :production {["WM" "C_GASE"] 1.0}}) ["WM" "C_GASE"]))))))

(deftest data-entry
  (let [snap (a {:asset_type "mine" :commodity "gold" :known_from 2021 :year_flag "snapshot" :start_year nil})
        opened (a {:asset_type "mine" :commodity "gold" :known_from 2022 :year_flag "start" :start_year 2022})
        old-close (a {:asset_type "mine" :commodity "iron" :known_from 1900 :end_year 1950
                      :end_flag "last-production" :end_known 2011})
        status-close (a {:asset_type "mine" :commodity "iron" :known_from 1990 :end_flag "closed-unknown" :end_known 2021})
        dated-close (a {:asset_type "mine" :commodity "iron" :known_from 1990 :end_year 2012
                        :end_flag "last-production" :end_known 2013})
        pres (cp/without-entry 2010)
        pres20 (cp/without-entry 2020)]
    (testing "an asset entering the data at its publication is left out of the later year"
      (is (cp/present? snap 2025))
      (is (not (pres20 snap 2025))))
    (testing "a dated opening is a change, kept"
      (is (pres20 opened 2025)))
    (testing "a closure reported long after it happened is a data event: kept as present"
      (is (not (cp/present? old-close 2015)))
      (is (pres old-close 2015))
      (is (pres20 status-close 2025)))
    (testing "a closure dated inside the step is a change"
      (is (not (pres dated-close 2015))))
    (testing "an asset known before the step is untouched"
      (is (= (cp/present? snap 2025) ((cp/without-entry 2021) snap 2025)))))
  (testing "a type change the entry caused is flagged and left out of the counts"
    (let [types {[2020 "a"] 0 [2025 "a"] 1 [2020 "b"] 0 [2025 "b"] 1 [2020 "c"] 0 [2025 "c"] 0}
          entry #{[2025 "a"]}]
      (is (= {[0 1] 1 [0 0] 1} (cp/transition-counts types [2020 2025] entry)))
      (is (= {[0 1] 2 [0 0] 1} (cp/transition-counts types [2020 2025]))))))

(deftest entry-flags-from-fits
  (let [fits {2025 {:scaler {:mu [0.0 0.0] :sd [1.0 1.0]} :z-centroids [[0.0 0.0] [5.0 5.0]]
                    :labels {"a" 1 "b" 1}}}
        cf {[2025 "a"] [0.1 0.0] [2025 "b"] [4.9 5.0]}]
    (is (= #{[2025 "a"]} (cp/entry-flags fits cf)) "a is type 1 only with the new assets")))

(deftest lights-across-the-sensor-join
  (let [k1 "DEU@x1" k2 "DEU@x2" k3 "DEU@x3" k4 "DEU@x4"
        g {"ntl/dn" {2012 {:known 2013 :values {k1 400.0 k2 300.0 k3 5.0 k4 100.0}}
                     2013 {:known 2014 :values {k1 400.0 k2 300.0 k3 5.0 k4 100.0}}
                     2014 {:known 2015 :values {k1 200.0 k3 2.0 k4 50.0}}
                     2015 {:known 2016 :values {k1 220.0 k3 2.0 k4 60.0}}}
           "ghsl/pop" {2015 {:known 2018 :values {k1 1.0}}}}
        {:keys [grids break]} (cp/splice-lights g {:min-dn 50.0})]
    (testing "the VIIRS years are put on the DMSP scale part by part, from the overlap known in 2015"
      (is (= 400.0 (get-in grids ["ntl/dn" 2014 :values k1])))
      (is (< (Math/abs (- 440.0 (get-in grids ["ntl/dn" 2015 :values k1]))) 1e-9)))
    (testing "the DMSP years and other grids are untouched"
      (is (= 300.0 (get-in grids ["ntl/dn" 2013 :values k2])))
      (is (= (g "ghsl/pop") (grids "ghsl/pop"))))
    (testing "a part lit under DMSP and dark under VIIRS cannot be spliced: flagged"
      (is (= #{k2} break)))
    (testing "a dim part takes its country's factor"
      ;; DEU: (400 + 100) / (200 + 50) over the parts spliced alone
      (is (< (Math/abs (- (* 2.0 2.0) (get-in grids ["ntl/dn" 2014 :values k3]))) 1e-9)))
    (testing "a transition across the join is flagged for a part that cannot be spliced"
      (is (= #{[2015 k2]} (cp/sensor-flags grids break [2014 2015 2020] "ntl/dn"))))))

(deftest labels-matched-across-fits
  (testing "the new centroids are matched to the last fit's, whatever order k-means gave"
    (is (= [2 0 1] (cp/match-centroids [[0.0 0.0] [5.0 5.0] [9.0 0.0]] [[5.1 5.0] [9.0 0.2] [0.1 0.0]]))))
  (testing "fit-types keeps a type's index from one year to the next"
    (let [ps (into {} (concat (for [i (range 30)] [[2000 (str "a" i)] [(* 0.01 i) 0.0]])
                              (for [i (range 30)] [[2000 (str "b" i)] [5.0 (+ 5.0 (* 0.01 i))]])
                              (for [i (range 30)] [[2005 (str "a" i)] [(* 0.01 i) 0.0]])
                              (for [i (range 30)] [[2005 (str "b" i)] [5.0 (+ 5.0 (* 0.01 i))]])))
          fits (cp/fit-types ps [2000 2005] 2 {})]
      (is (= (get-in fits [2000 :labels "a3"]) (get-in fits [2005 :labels "a3"])))
      (is (contains? (fits 2005) :moved)))))

(deftest type-names-from-centroids
  (let [z (fn [m] (mapv #(get m % 0.0) cp/features))]
    (is (= ["sparse" "lit-sparse" "urban" "industrial" "extraction"]
           (cp/name-types [(z {:serv-gdppc -0.6 :serv-lights-pc -0.8 :serv-density -0.3})
                           (z {:serv-gdppc 0.7 :serv-lights-pc 1.0 :serv-density -0.6 :serv-built -0.4})
                           (z {:serv-density 1.3 :serv-built 1.1 :serv-gdppc 0.1})
                           (z {:proc-sites 3.0 :proc-power 3.3 :man-nres 4.2 :serv-built 3.5 :serv-density 1.9})
                           (z {:ext-sites 5.3 :ext-mining-area 2.9})])))))
