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
    (testing "supercells: the spatial parent"
      (let [f (apply cells/cell (conj berlin 9))]
        (is (= c (cells/supercell f 6)) "Berlin's fine cell lies in Berlin's cell")
        (is (not= (cells/coarsen f 6) (cells/supercell f 6)) "while its id's parent is a neighbour")
        (doseq [k (take 40 (cells/children c 9))]
          (is (= (apply cells/cell (conj (cells/centre k) 6)) (cells/supercell k 6))
              "a cell's supercell holds its centre"))
        (is (= c (cells/supercell c 6)) "at its own resolution, the cell")))
    (testing "subcells: the finer cells whose centres a cell holds"
      (let [ks (cells/subcells c 8)
            ring (cells/neighbours c)]
        (is (< 12 (count ks) 20) "about 16 at two resolutions finer")
        (is (every? #(= c (cells/supercell % 6)) ks))
        (is (not-every? #(= c (cells/coarsen % 6)) ks) "some descend from a neighbour")
        (is (empty? (filter (set ks) (mapcat #(cells/subcells % 8) ring))) "a neighbour's are its own")))
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

(deftest coarser-cells-keep-totals
  ;; resolution-9 values over a block coarsened to resolution 6: each fine
  ;; cell's value goes to the coarse cell holding it, so totals are kept
  ;; and every coarse cell gets exactly what lies in it
  (let [c (apply cells/cell berlin)
        fine (vec (mapcat #(cells/children % 9) (cells/disk c 1)))
        v (into {} (map-indexed (fn [i k] [k (double (inc (mod i 7)))])) fine)
        total (reduce + (vals v))
        up (cells/coarser v 6 :sum)]
    (is (close? total (reduce + (vals up)) 1e-6) "the sum is kept")
    (is (every? #(= 6 (cells/res %)) (keys up)))
    (is (close? (reduce + (map v (filter #(= c (cells/supercell % 6)) fine))) (up c) 1e-9)
        "a coarse cell holds the fine cells whose centres it holds")
    (let [by-parent (reduce-kv (fn [m k x] (update m (cells/coarsen k 6) (fnil + 0.0) x)) {} v)]
      (is (not= (up c) (by-parent c)) "coarsening ids would misplace about a third"))
    (is (= (count fine) (reduce + (vals (cells/coarser v 6 :count)))))
    (is (close? 7.0 (reduce max (vals (cells/coarser v 6 :max)))))
    (is (every? #(<= 1.0 % 7.0) (vals (cells/coarser v 6 :mean))))
    (testing "aggregate takes finer cells, through their supercells"
      (let [x (cells/read-crosswalk (tmp (str "cell,iso,admin1,share,flag\n" c ",AAA,AAA.1,0.5,land\n" c ",BBB,BBB.1,0.5,land\n")))
            mine (into {} (filter #(= c (cells/supercell (key %) 6))) v)]
        (is (close? (reduce + (vals mine)) (reduce + (vals (cells/aggregate x mine :country :sum)))))
        (is (close? (/ (reduce + (vals mine)) 2) ((cells/aggregate x mine :country :sum) "AAA")))))
    (is (thrown? Exception (cells/coarser {c 1.0} 7 :sum)) "not to a finer resolution")))

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

(deftest locating-in-split-cells
  ;; cell a split AAA 3/4, BBB 1/4; the fine crosswalk names the samples
  ;; (resolution-10 cells whose centres a holds) outside a's largest part
  (let [subs (cells/subcells a 10)
        f (first subs)
        ;; a resolution-9 cell whose four id children are all a's samples: compacted
        x (some (fn [k] (let [p (cells/coarsen k 9)]
                          (when (and (not= f k) (every? (set subs) (cells/children p))) p)))
                subs)
        sibs (set (cells/children x))
        g (some #(when-not (or (= f %) (sibs %)) %) subs)
        h (some #(when-not (or (= f %) (= g %) (sibs %)) %) subs)
        x' (cells/read-crosswalk
            (tmp (str "cell,iso,admin1,share,flag\n" a ",AAA,AAA.1,0.75,land\n" a ",BBB,BBB.1,0.25,land\n"
                      b ",BBB,BBB.1,1,land\n"))
            (tmp (str "cell,part,iso,admin1,flag,res\n" a "," f ",BBB,BBB.1,land,10\n" a "," x ",BBB,BBB.1,land,10\n"
                      a "," g ",CCC,CCC.1,land,10\n" a "," h ",,,none,10\n")))
        at (fn [k] (apply cells/locate x' (cells/centre k)))]
    (is (= [10 9] (:sample-res x')))
    (is (= {:cell a :iso "BBB" :admin1 "BBB.1" :flag "land"} (at f)) "a named sample")
    (is (every? #(= "BBB" (:iso (at %))) (cells/children x)) "a compacted part stands for its children")
    (is (= "AAA" (:iso (at (some #(when-not (#{f g h} %) (when-not (sibs %) %)) subs)))) "the rest: the largest part")
    (is (= "AAA" (:iso (at g))) "never a country the cell does not reach")
    (is (nil? (at h)) "the high seas")
    (is (= {:cell b :iso "BBB" :admin1 "BBB.1" :flag "land"} (cells/locate x' 2.35 48.86)) "a cell in one part")
    (testing "a locator remembers the places it has located"
      (let [loc (cells/locator x')
            pts (map cells/centre (take 20 subs))]
        (is (= (map #(apply cells/locate x' %) pts) (map #(apply loc %) pts) (map #(apply loc %) pts)))))))

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
  (testing "a small polygon takes a few samples, no closer than 0.5 km"
    (let [sq [[[13.40 52.50] [13.415 52.50] [13.415 52.509] [13.40 52.509] [13.40 52.50]]]
          ss (cells/polygon-samples sq)]
      (is (<= 1 (count ss) 9) "about 1 km2: a 3 x 3 grid at most")
      (is (close? (cells/polygon-area-km2 sq) (reduce + (map #(nth % 2) ss)) 0.3))))
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
        (is (close? 1.0 elsewhere) "the crosswalk puts it in AAA")
        (is (close? 0.0 (:moved (cells/place-polygon x {:rings near-berlin :iso "BBB"}))))))
    (testing "a part where the source's country does not reach goes to the crosswalk's"
      (let [{:keys [parts elsewhere moved]} (cells/place-polygon x {:rings near-paris :iso "AAA"})]
        (is (= [{:cell b :iso "BBB" :admin1 "BBB.1" :xw-iso nil}]
               (map #(select-keys % [:cell :iso :admin1 :xw-iso]) parts)) "AAA is not in Paris's cell")
        (is (close? 1.0 elsewhere))
        (is (close? 1.0 moved))))
    (testing "a part kept under the source's country names the crosswalk's"
      (is (= "AAA" (:xw-iso (first (:parts (cells/place-polygon x {:rings near-berlin :iso "BBB"})))))))
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
  (is (= "FRA" (cells/country-in-year "FRA" nil 1950)) "no change")
  (testing "Kosovo was in Serbia until it declared independence in 2008"
    (is (= "SRB" (cells/country-in-year "XKX" nil 2007)))
    (is (= "XKX" (cells/country-in-year "XKX" nil 2008)))
    (is (= "SCG" (cells/country-in-year "XKX" nil 2005)))
    (is (= "YUG" (cells/country-in-year "XKX" nil 1985))))
  (testing "Montenegro split in 2006, Serbia and Montenegro formed in 1992"
    (is (= "SCG" (cells/country-in-year "MNE" nil 2005)))
    (is (= "MNE" (cells/country-in-year "MNE" nil 2006)))
    (is (= "SCG" (cells/country-in-year "SRB" nil 1992))))
  (testing "Aruba left the Netherlands Antilles in 1986"
    (is (= "ANT" (cells/country-in-year "ABW" nil 1985)))
    (is (= "ABW" (cells/country-in-year "ABW" nil 1986)))
    (is (= "ANT" (cells/country-in-year "CUW" nil 2010)))
    (is (= "CUW" (cells/country-in-year "CUW" nil 2011))))
  (testing "the Yemens to 1989, by province"
    (is (= "YMD" (cells/country-in-year "YEM" "YEM.02" 1980)) "Aden in the South")
    (is (= "YAR" (cells/country-in-year "YEM" "YEM.26" 1980)) "Sanaa in the North")
    (is (= "YEM" (cells/country-in-year "YEM" "YEM.02" 1990)) "unified in May 1990"))
  (testing "splits take effect the year after the last year of the old state"
    (is (= ["CSK" "SVK"] (map #(cells/country-in-year "SVK" nil %) [1992 1993])))
    (is (= ["ETP" "ERI"] (map #(cells/country-in-year "ERI" nil %) [1992 1993])))
    (is (= ["SDF" "SSD"] (map #(cells/country-in-year "SSD" nil %) [2011 2012])))
    (is (= ["DEW" "DEU"] (map #(cells/country-in-year "DEU" "DEU.16" %) [1990 1991])) "Berlin with the West")))

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
      (testing "small states and territories (sampled finer)"
        (doseq [[iso lon lat what] [["VAT" 12.4534 41.9029 "the Vatican"] ["MAC" 113.55 22.19 "Macau"]
                                    ["SMR" 12.4578 43.9424 "San Marino"] ["MCO" 7.4246 43.7384 "Monaco"]
                                    ["LIE" 9.5215 47.1410 "Vaduz"] ["SGP" 103.8198 1.3521 "Singapore"]
                                    ["LSO" 27.4833 -29.3167 "Maseru"] ["GIB" -5.3463 36.1268 "Gibraltar"]
                                    ["REU" 55.45 -20.88 "Réunion, not France"] ["GUF" -52.33 4.93 "Cayenne"]
                                    ["SJM" 15.63 78.22 "Longyearbyen"] ["CXR" 105.68 -10.45 "Christmas Island"]]]
          (is (= iso (:iso (cells/locate x lon lat))) what))
        (let [land (fn [iso] (reduce + (for [r (:rows x) :when (and (= iso (:iso r)) (= "land" (:flag r)))]
                                         (* (:share r) (cells/area-km2)))))]
          (is (< 0.001 (land "VAT") 0.1) "the Vatican: Natural Earth's 0.016 km2 outline")
          (is (< 2 (land "GIB") 8) "Gibraltar: Natural Earth's 4.5 km2, not a whole 32 km2 sample")))
      (testing "provinces where Natural Earth's units are a level down"
        (is (= "IRL.L" (:admin1 (cells/locate x -6.26 53.35))) "Dublin in Leinster")
        (is (= "LKA.36" (:admin1 (cells/locate x 79.86 6.93))) "Colombo in the Western Province"))
      (testing "overlapping claims go to the nearest claimant, not the first named"
        (is (not= "CHN" (:iso (cells/locate x 114.0 10.0)))
            "the Spratlys' waters, which Marine Regions' South China Sea claim names China alone for")
        (is (= "JPN" (:iso (cells/locate x 123.5 25.75))) "by the Senkakus (Japan's in Natural Earth), not Taiwan, named first"))
      (testing "aggregation keeps the total: one per cell with land, by country and up"
        (let [land (into {} (for [[c rs] (:by-cell x) :when (some #(= "land" (:flag %)) rs)] [c 1.0]))
              n (count land)]
          (doseq [level [:country :subregion :region :world]]
            (let [t (reduce + (vals (cells/aggregate x land level :sum {:land-only? true})))]
              (is (< (Math/abs (- t n)) (* n 0.002)) (str level ": " t " of " n))))))
      (testing "the dodeca cell ids match the script's (pya5)"
        (is (seq (get-in x [:by-cell (apply cells/cell berlin)])))))))
