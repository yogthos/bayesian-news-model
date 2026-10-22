(ns bnm.cells-test
  (:require [bnm.cells :as cells]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- close? ([a b] (close? a b 1e-9)) ([a b eps] (< (Math/abs (- (double a) (double b))) eps)))

(def berlin [13.405 52.52])

;; --- cells ----------------------------------------------------------------------------

(deftest ids-and-round-trips
  (let [c (apply cells/cell berlin)]
    (is (string? c) "a hex id")
    (is (= 6 cells/resolution (cells/res c)) "resolution 6 by default")
    (is (= c (cells/hex (cells/id c))) "hex -> id -> hex")
    (is (= c (apply cells/cell (cells/centre c))) "a cell's centre lies in it")
    (is (= 9 (cells/res (apply cells/cell (conj berlin 9)))))
    (testing "children"
      (let [ks (cells/children c)]
        (is (= 4 (count ks)) "one level finer by default")
        (is (every? #(= c (cells/coarsen % 6)) ks) "each child's parent is the cell")
        (is (= 64 (count (cells/children c 9))))
        (is (every? #(= c (cells/coarsen % 6)) (cells/children c 9)))))
    (testing "A5's children are not inside their parent"
      ;; Berlin's resolution-9 cell descends from the cell next to Berlin's:
      ;; coarsening ids is the hierarchy, locating a point is geometry
      (is (not= c (cells/coarsen (apply cells/cell (conj berlin 9)) 6)))
      (is (some #{(cells/coarsen (apply cells/cell (conj berlin 9)) 6)} (cells/neighbours c))))
    (testing "the cell x country key"
      (let [k (cells/cell-key c "DEU")]
        (is (= ["DEU" c] [(cells/key-iso k) (cells/key-cell k)]))
        (is (= "DEU" (subs k 0 3)) "the country first, as bnm.geo's ids")))))

(deftest neighbours-and-measures
  (let [c (apply cells/cell berlin)
        ring (cells/neighbours c)
        disk (cells/disk c 1)]
    (is (= 5 (count ring)) "a pentagon has five edge neighbours")
    (is (= (inc (count ring)) (count disk)) "the disk is the centre and its ring")
    (is (not (some #{c} ring)) "the centre is not its own neighbour")
    (is (every? #(= 6 (cells/res %)) disk))
    (is (< (count disk) (count (cells/disk c 2))))
    (is (close? 8301.85 (cells/area-km2) 0.01) "about 8,300 km2 at resolution 6")
    (is (close? (/ (cells/area-km2) 4) (cells/area-km2 7)) "equal area: four children")
    (is (close? (cells/area-km2) (cells/cell-area-km2 c)))))

;; --- a synthetic crosswalk --------------------------------------------------------------

(defn- tmp [content]
  (let [f (java.io.File/createTempFile "xwalk" ".csv")]
    (spit f content)
    (str f)))

(def a (apply cells/cell berlin))
(def b (cells/cell 2.35 48.86))                 ; Paris
(def s (cells/cell -30.0 0.0))                  ; open Atlantic

(def xw-csv
  (str "cell,iso,admin1,share,flag\n"
       a ",AAA,AAA.1,0.5,land\n"
       a ",AAA,AAA.2,0.25,land\n"
       a ",BBB,BBB.1,0.25,land\n"
       b ",BBB,BBB.1,1,land\n"
       s ",BBB,,0.5,eez\n"))

(defn- xw [] (cells/read-crosswalk (tmp xw-csv)))

(deftest reading-the-crosswalk
  (let [x (xw)]
    (is (= 5 (count (:rows x))))
    (is (= {:cell a :iso "AAA" :admin1 "AAA.1" :share 0.5 :flag "land"} (first (:rows x))))
    (is (= 3 (count (get-in x [:by-cell a]))))
    (is (nil? (:admin1 (last (:rows x)))) "a blank province is nil")
    (is (= #{"AAA" "BBB"} (set (cells/countries-of x a))))))

(deftest aggregation-conserves
  (let [x (xw)
        v {a 100.0 b 10.0 s 4.0}]
    (testing "sums: a stock split by the area shares"
      (let [c (cells/aggregate x v :country :sum)]
        (is (close? 75.0 (c "AAA")))
        (is (close? (+ 25.0 10.0 2.0) (c "BBB")))
        (is (close? (+ 100.0 10.0 2.0) (reduce + (vals c))) "conserved where shares sum to 1"))
      (let [p (cells/aggregate x v :province :sum)]
        (is (= {"AAA.1" 50.0 "AAA.2" 25.0 "BBB.1" 35.0} p) "offshore parts have no province"))
      (let [w (cells/aggregate x v :world :sum)]
        (is (close? 112.0 (w "world")))))
    (testing "land only: offshore parts dropped, a cell's land renormalised"
      (let [c (cells/aggregate x v :country :sum {:land-only? true})]
        (is (close? 110.0 (reduce + (vals c))) "the open-sea cell has no land")))
    (testing "intensities: area-weighted mean"
      (let [m (cells/aggregate x {a 1.0 b 3.0} :country :mean)]
        (is (close? 1.0 (m "AAA")))
        ;; BBB holds a quarter of cell a and all of cell b
        (is (close? (/ (+ (* 0.25 1.0) (* 1.0 3.0)) 1.25) (m "BBB")))))
    (testing "max and count"
      (is (= {"AAA" 100.0 "BBB" 100.0} (cells/aggregate x {a 100.0 b 10.0} :country :max)))
      (is (= {"AAA" 1 "BBB" 2} (cells/aggregate x {a 100.0 b 10.0} :country :count))))))

(deftest rollup-of-located-records
  ;; records already on (cell, iso, admin1): assets, events
  (let [rs [{:cell a :iso "DEU" :admin1 "DEU.16" :value 3.0}
            {:cell a :iso "DEU" :admin1 nil :value 1.0}
            {:cell b :iso "FRA" :admin1 "FRA.11" :value 2.0}]]
    (is (= {"DEU" 4.0 "FRA" 2.0} (cells/rollup rs :country :sum)))
    (is (= {"DEU.16" 3.0 "FRA.11" 2.0} (cells/rollup rs :province :sum)))
    (is (= {"s/western-europe" 6.0} (cells/rollup rs :subregion :sum)))
    (is (= {"r/europe" 6.0} (cells/rollup rs :region :sum)))
    (is (= {"world" 6.0} (cells/rollup rs :world :sum)))
    (is (= {"DEU" 2 "FRA" 1} (cells/rollup rs :country :count)))
    (is (= {"DEU" 3.0 "FRA" 2.0} (cells/rollup rs :country :max)))))

;; --- ingest helpers --------------------------------------------------------------------

(deftest points-to-cells
  (let [x (xw)
        {:keys [points disagree]}
        (cells/place-points x [{:lon (first berlin) :lat (second berlin) :iso "AAA" :mw 1.0}
                               {:lon (first berlin) :lat (second berlin) :iso "BBB" :mw 2.0}
                               {:lon (first berlin) :lat (second berlin) :iso "CCC" :mw 3.0}
                               {:lon 2.35 :lat 48.86 :iso nil :mw 4.0}])]
    (is (= [a a a b] (map :cell points)))
    (is (= ["AAA" "BBB" "CCC" "BBB"] (map :iso points)) "the source's country is kept; the crosswalk's when it has none")
    (is (= "AAA.1" (:admin1 (first points))) "the cell's largest province of that country")
    (is (= "BBB.1" (:admin1 (second points))))
    (is (nil? (:admin1 (nth points 2))) "a country the cell does not reach has no province")
    (is (= {:points 4 :from-crosswalk 1 :near-border 1 :not-in-cell 1} disagree)
        "BBB is in the cell but not where the crosswalk puts the point; CCC is not in the cell at all")))

(deftest rasters-to-cells
  ;; a 1-degree grid of constant density 2 per km2 over a 4 x 4 degree block
  (let [g {:lon0 10.0 :lat0 54.0 :dlon 1.0 :dlat -1.0 :ncol 4 :nrow 4
           :value (fn [_ _] 2.0)}
        r (cells/raster->cells g {:sub 8})
        total-area (reduce + (map :area (vals r)))]
    (is (close? 1.0 (/ total-area (cells/lon-lat-box-km2 10.0 50.0 14.0 54.0)) 1e-6)
        "the samples cover the block's area")
    (is (every? #(close? 2.0 (:mean %)) (vals r)) "a constant mean is constant")
    (is (close? (reduce + (map :sum (vals r))) (* 2.0 total-area) 1e-6)
        "sums are the value times the sampled area: conserved")
    (testing "no data is skipped"
      (let [r (cells/raster->cells (assoc g :value (fn [c _] (when (< c 2) 1.0))) {:sub 4})]
        (is (close? (reduce + (map :area (vals r))) (cells/lon-lat-box-km2 10.0 50.0 12.0 54.0) 1e-6))))))

(deftest polygons-to-cells
  (testing "a small square inside one cell: all of it there"
    (let [sq [[[13.40 52.50] [13.41 52.50] [13.41 52.51] [13.40 52.51] [13.40 52.50]]]
          {:keys [area shares]} (cells/polygon->cells sq)]
      (is (close? 1.0 (reduce + (vals shares))))
      (is (= #{(apply cells/cell berlin)} (set (keys shares))))
      (is (close? (cells/lon-lat-box-km2 13.40 52.50 13.41 52.51) area 1e-3) "its area, km2")))
  (testing "a large polygon over many cells: shares sum to one, area matches"
    (let [sq [[[10.0 50.0] [14.0 50.0] [14.0 54.0] [10.0 54.0] [10.0 50.0]]]
          {:keys [area shares]} (cells/polygon->cells sq)]
      (is (< 20 (count shares)))
      (is (close? 1.0 (reduce + (vals shares)) 1e-9))
      (is (close? 1.0 (/ area (cells/lon-lat-box-km2 10.0 50.0 14.0 54.0)) 1e-3))))
  (testing "a hole is not part of it"
    (let [outer [[10.0 50.0] [14.0 50.0] [14.0 54.0] [10.0 54.0] [10.0 50.0]]
          hole [[11.0 51.0] [13.0 51.0] [13.0 53.0] [11.0 53.0] [11.0 51.0]]
          {:keys [area]} (cells/polygon->cells [outer hole])]
      (is (close? 1.0 (/ area (- (cells/lon-lat-box-km2 10.0 50.0 14.0 54.0)
                                 (cells/lon-lat-box-km2 11.0 51.0 13.0 53.0))) 1e-3)))))

(deftest polygons-on-the-crosswalk
  (let [x (xw)
        near-berlin [[[13.40 52.50] [13.41 52.50] [13.41 52.51] [13.40 52.51] [13.40 52.50]]]
        near-paris [[[2.35 48.86] [2.37 48.86] [2.37 48.88] [2.35 48.88] [2.35 48.86]]]]
    (testing "one polygon in one cell, the source's country kept"
      (let [{:keys [parts elsewhere area]} (cells/place-polygon x {:rings near-berlin :iso "BBB"})]
        (is (= [{:cell a :iso "BBB" :admin1 "BBB.1"}] (map #(select-keys % [:cell :iso :admin1]) parts)))
        (is (close? 1.0 (:share (first parts))))
        (is (close? area (:area (first parts))))
        (is (close? 1.0 elsewhere) "the crosswalk puts it in AAA")))
    (testing "a multipolygon: parts weighted by their areas"
      (let [{:keys [parts area]} (cells/place-polygon x {:polygons [near-berlin near-paris]})
            by-cell (into {} (map (juxt :cell :share)) parts)]
        (is (close? area (+ (cells/polygon-area-km2 near-berlin) (cells/polygon-area-km2 near-paris))))
        (is (close? 1.0 (reduce + (vals by-cell))))
        (is (close? (by-cell b) (/ (cells/polygon-area-km2 near-paris) area)))
        (is (= "AAA" (:iso (first (filter #(= a (:cell %)) parts)))) "no source country: the crosswalk's")))))

;; --- borders over time ------------------------------------------------------------------

(deftest countries-in-past-years
  (is (= "SUN" (cells/country-in-year "UKR" nil 1985)))
  (is (= "UKR" (cells/country-in-year "UKR" nil 1992)))
  (is (= "SUN" (cells/country-in-year "UKR" nil 1991)) "the year it ended counts to it")
  (is (= "YUG" (cells/country-in-year "SRB" nil 1980)) "through Serbia and Montenegro to Yugoslavia")
  (is (= "SCG" (cells/country-in-year "XKX" nil 2000)))
  (is (= "SDF" (cells/country-in-year "SSD" nil 2005)))
  (is (= "DDR" (cells/country-in-year "DEU" "DEU.13" 1980)) "Saxony was in the East")
  (is (= "DEW" (cells/country-in-year "DEU" "DEU.02" 1980)) "Bavaria in the West")
  (is (= "DEU" (cells/country-in-year "DEU" "DEU.13" 1991)))
  (is (= "FRA" (cells/country-in-year "FRA" nil 1950)) "no change"))

;; --- the real crosswalk (data/derived/cells, scripts/cells.py) ---------------------------

(def derived "data/derived/cells/crosswalk.csv")

(deftest real-crosswalk
  (if-not (.exists (io/file derived))
    (println "bnm.cells-test: no" derived "- crosswalk checks skipped (python -I scripts/cells.py)")
    (let [x (cells/load-crosswalk)]
      (testing "shares sum to 1 in every cell with land"
        (let [bad (for [[c rs] (:by-cell x)
                        :when (some #(#{"land" "disputed"} (:flag %)) rs)
                        :let [t (reduce + (map :share rs))]
                        :when (> (Math/abs (- t 1.0)) 1e-4)]
                    [c t])]
          (is (empty? bad) (str (count bad) " land cells off: " (vec (take 5 bad))))))
      (testing "known points"
        (is (= {:iso "DEU" :admin1 "DEU.16"} (select-keys (apply cells/locate x berlin) [:iso :admin1])) "Berlin")
        (is (= {:iso "CHE" :admin1 "CHE.BS"} (select-keys (cells/locate x 7.588 47.559) [:iso :admin1])) "Basel")
        (is (= "DEU" (:iso (cells/locate x 7.661 47.615))) "Lörrach, across the border from Basel")
        (is (= "FRA" (:iso (cells/locate x 7.545 47.586))) "Saint-Louis, by Basel's airport")
        (is (= {:iso "UKR" :admin1 "UKR.12"} (select-keys (cells/locate x 30.523 50.450) [:iso :admin1])) "Kyiv")
        (is (= "USA" (:iso (cells/locate x -74.006 40.713))) "New York")
        (is (nil? (cells/locate x -45.0 25.0)) "the open Atlantic")
        (is (= #{"CHE" "DEU" "FRA"} (set (cells/countries-of x (cells/cell 7.588 47.559)))) "Basel's cell has three countries"))
      (testing "aggregation keeps the total: one per cell with land, by country and up"
        (let [land (into {} (for [[c rs] (:by-cell x) :when (some #(= "land" (:flag %)) rs)] [c 1.0]))
              n (count land)]
          (doseq [level [:country :subregion :region :world]]
            (let [t (reduce + (vals (cells/aggregate x land level :sum {:land-only? true})))]
              (is (< (Math/abs (- t n)) (* n 0.002)) (str level ": " t " of " n))))))
      (testing "the dodeca cell ids match the script's (pya5)"
        (is (seq (get-in x [:by-cell (apply cells/cell berlin)])))))))
