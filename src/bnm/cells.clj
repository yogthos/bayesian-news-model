(ns bnm.cells
  "Space (docs/PRINCIPLES.md, principle 11): every located quantity is keyed
  by its equal-area A5 cell (dodeca), at resolution 6 (about 8,300 km2) unless
  said otherwise, coarser and finer by parent and children. Countries,
  provinces and regions are aggregations of cells, never the storage. A cell
  across a border is one entry per country: its cell x country key,
  \"DEU@63fe800000000000\".

  Cells are lowercase hex strings (A5's spelling); dodeca's longs stay inside
  this namespace.

  A5's hierarchy is not nested (A5 says so: its index nests cells by id,
  but a cell's children do not tile it): a cell's own descendants cover
  about 64-70% of it, the rest lying in its neighbours, and for about 35-37%
  of points the parent of the point's finer cell is not the cell holding the
  point. `coarsen` and `children` are the id hierarchy, for ids only. To move
  a quantity to a coarser resolution, use `supercell` (the coarser cell
  holding a cell's centre: A5's cellToSupercell), `subcells` and `coarser`,
  or re-locate it from its coordinates with `cell`; never aggregate by
  `coarsen`.

  The crosswalk (scripts/cells.py, data/derived/cells/) gives each cell's
  parts: the country, the province (bnm.geo's GeoNames admin1 id) and the
  share of the cell's area, flagged land, disputed, eez, eez-shared or coast;
  for a cell it splits, which part each finer cell lies in (`locate`).

  Borders are current (Natural Earth 5.1.2), and a cell's country is the
  same in every year: the model's nodes are today's countries, and an event
  or asset located in a cell counts for the cell's country whatever the
  year. Sources that keep historical states (the USSR as SUN, Yugoslavia as
  YUG; data/derived/material/succession.csv) are joined through
  `country-in-year`, which gives the state a cell's country belonged to in a
  year. bnm.geo's code aliases, which file a coordinate-less event coded SUN
  under RUS, are for events without a place; a placed one goes by its cell."
  (:require [bnm.geo :as geo]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [dodeca.core :as a5]
            [dodeca.core.cell :as a5-cell]))

(def resolution
  "The default resolution: 61,440 cells of about 8,300 km2."
  6)

;; --- cells ----------------------------------------------------------------------------

(defn id
  "dodeca's id (a long) for a hex cell."
  [hex]
  (a5/hex->cell hex))

(defn hex
  "The hex spelling of dodeca's id."
  [id]
  (a5/cell->hex id))

(defn cell
  "The cell at `res` (default 6) holding the point `lon` `lat`, in degrees."
  ([lon lat] (cell lon lat resolution))
  ([lon lat res] (hex (a5/lon-lat->cell [(double lon) (double lat)] res))))

(defn res
  "A cell's resolution."
  [c]
  (a5/resolution (id c)))

(defn coarsen
  "The id ancestor of cell `c` at the coarser resolution `r`. Not where `c`
  lies: for about a third of cells it is a neighbour of the coarser cell
  holding them (`supercell` is that one). Ids only; never aggregate by it."
  [c r]
  (hex (a5/cell->parent (id c) r)))

(defn children
  "The id descendants of `c` one resolution finer, or at resolution `r`:
  about two thirds of them lie in `c` (`subcells` are the ones that do)."
  ([c] (mapv hex (a5/cell->children (id c))))
  ([c r] (mapv hex (a5/cell->children (id c) r))))

(defn supercell
  "The cell at the coarser resolution `r` holding the centre of `c` (A5's
  cellToSupercell): where `c` lies, so aggregating by it keeps every fine
  cell in the coarse cell it is in, and the supercells at one resolution
  partition every finer one."
  [c r]
  (let [i (id c)
        cr (a5/resolution i)]
    (when (> r cr)
      (throw (ex-info (str "supercell: resolution " r " is finer than the cell's " cr) {:cell c :res r})))
    (if (= r cr) c (hex (a5-cell/cell-center->cell i r)))))

(defn subcells
  "The cells at the finer resolution `r` whose centres lie in `c` (A5's
  cellToSubcells, the inverse of `supercell`): they come from the id
  descendants of `c` and of the cells round it."
  [c r]
  (let [cr (res c)]
    (if (= r cr)
      [c]
      (->> (a5/uncompact (a5/grid-disk-vertex (id c) 1))
           (remove a5/compaction-marker?)
           (mapcat #(a5/cell->children % r))
           (keep (fn [k] (let [h (hex k)] (when (= c (supercell h cr)) h))))
           vec))))

(defn centre
  "A cell's centre, [lon lat]."
  [c]
  (vec (a5/cell->lon-lat (id c))))

(defn boundary
  "A cell's boundary, [[lon lat] ...], counter-clockwise and closed."
  [c]
  (mapv vec (a5/cell->boundary (id c))))

(defn disk
  "Every cell within `k` edge-sharing steps of `c`, `c` included, at its
  resolution."
  [c k]
  (->> (a5/uncompact (a5/grid-disk (id c) k))
       (remove a5/compaction-marker?)
       (mapv hex)))

(defn neighbours
  "The cells within `k` steps of `c` (default 1, those sharing an edge),
  without `c`."
  ([c] (neighbours c 1))
  ([c k] (filterv #(not= c %) (disk c k))))

(defn area-km2
  "The area of a cell at `r` (default 6), km2: the same for every cell."
  ([] (area-km2 resolution))
  ([r] (/ (a5/cell-area r) 1e6)))

(defn cell-area-km2 [c] (area-km2 (res c)))

(def ^:private earth-km
  "The radius of the sphere A5's areas are on, km."
  (Math/sqrt (/ (* 12 (area-km2 0)) (* 4 Math/PI))))

(defn lon-lat-box-km2
  "The area of the box between two meridians and two parallels, km2."
  [lon1 lat1 lon2 lat2]
  (* earth-km earth-km (Math/abs (Math/toRadians (- lon2 lon1)))
     (Math/abs (- (Math/sin (Math/toRadians lat2)) (Math/sin (Math/toRadians lat1))))))

(defn cell-key
  "The cell x country key: one entry per country of a cell across a border."
  [c iso]
  (str iso "@" c))

(defn key-iso [k] (subs k 0 (str/index-of k "@")))
(defn key-cell [k] (subs k (inc (str/index-of k "@"))))

;; --- the crosswalk ---------------------------------------------------------------------

(def default-dir "data/derived/cells")

(defn- blank->nil [s] (when-not (str/blank? s) s))

(defn- csv-rows [path]
  (with-open [r (io/reader path)]
    (mapv #(str/split % #"," -1) (rest (line-seq r)))))

(defn read-crosswalk
  "The crosswalk at `path` (cell,iso,admin1,share,flag) and, when given, the
  fine one (cell,part,iso,admin1,flag,res: res the resolution the cell was
  sampled at, 10 when absent): {:rows [{:cell :iso :admin1 :share :flag}]
  :by-cell {cell rows} :fine {part-id {:owner cell :iso :admin1 :flag}}
  :sample-res [res ...] (finest first)}, part ids as dodeca's longs."
  ([path] (read-crosswalk path nil))
  ([path fine-path]
   (let [rows (mapv (fn [[c iso a1 share flag]]
                      {:cell c :iso iso :admin1 (blank->nil a1) :share (parse-double share) :flag flag})
                    (csv-rows path))
         by-cell (group-by :cell rows)
         ress (volatile! #{})
         fine (if (and fine-path (.exists (io/file fine-path)))
                (persistent!
                 (reduce (fn [m [c part iso a1 flag r]]
                           (vswap! ress conj (if (str/blank? r) 10 (parse-long r)))
                           (assoc! m (a5/hex->cell part)
                                   {:owner (:cell (first (by-cell c)) c)
                                    :iso (blank->nil iso) :admin1 (blank->nil a1) :flag flag}))
                         (transient {}) (csv-rows fine-path)))
                {})]
     {:rows rows
      :by-cell by-cell
      :fine fine
      :sample-res (vec (sort > (into #{10 9} @ress)))})))

(def ^:private loaded (atom {}))

(defn load-crosswalk
  "The crosswalk in `dir` (default data/derived/cells), read once."
  ([] (load-crosswalk default-dir))
  ([dir]
   (or (@loaded dir)
       (let [x (read-crosswalk (str dir "/crosswalk.csv") (str dir "/crosswalk-fine.csv"))]
         (swap! loaded assoc dir x)
         x))))

(defn countries-of
  "The countries cell `c` lies in."
  [xw c]
  (vec (distinct (map :iso (get-in xw [:by-cell c])))))

(defn- largest [rows]
  (select-keys (apply max-key :share rows) [:cell :iso :admin1 :flag]))

(defn- sample-part
  "The fine crosswalk's part for the point's sample at resolution `s`: the
  point's cell there, if the cell holding that one's centre (its owner) was
  sampled at `s`, in the part holding it (parts are compacted: an id
  ancestor stands for its descendants). [owner part], part nil for the
  owner's largest part (left out of the fine crosswalk)."
  [fine ll s]
  (let [f (a5/lon-lat->cell ll s)
        o (hex (a5-cell/cell-center->cell f resolution))]
    (loop [r s]
      (when (>= r 7)
        (let [p (get fine (if (= r s) f (a5/cell->parent f r)))]
          (if (and p (= o (:owner p)))
            [o p]
            (recur (dec r))))))))

(defn locate
  "Where the point lies: {:cell :iso :admin1 :flag}, its cell at resolution 6
  and the part of it holding the point, or nil on the high seas and on land
  of no country. A cell wholly in one part is that part. Otherwise the part
  is the fine crosswalk's for the point's sample (its cell at the sampling
  resolution, to about 6 km at 10), else the largest part of the cell its
  sample lies in, else of its own cell; never a country the point's cell
  does not reach."
  [xw lon lat]
  (let [c (cell lon lat)
        rows (get-in xw [:by-cell c])]
    (when (seq rows)
      (if (and (= 1 (count rows)) (>= (:share (first rows)) 0.9999))
        (largest rows)
        (let [fine (:fine xw)
              ll [(double lon) (double lat)]
              isos (into #{} (map :iso) rows)
              [_ part] (when (seq fine) (some #(sample-part fine ll %) (:sample-res xw [10 9])))]
          (cond
            (and part (= "none" (:flag part))) nil
            (and part (isos (:iso part))) (assoc (dissoc part :owner) :cell c)
            part (largest rows)
            :else (let [o (hex (a5-cell/cell-center->cell (a5/lon-lat->cell ll 10) resolution))
                        orows (get-in xw [:by-cell o])
                        big (when (seq orows) (largest orows))]
                    (if (and big (isos (:iso big)))
                      (assoc big :cell c)
                      (largest rows)))))))))

(defn locator
  "`locate` on the crosswalk `xw` as a function of lon and lat that
  remembers what it has located: event data repeats a few places (a city's
  centroid) millions of times."
  [xw]
  (let [m (java.util.HashMap.)]
    (fn [lon lat]
      (let [k [(double lon) (double lat)]]
        (if (.containsKey m k)
          (.get m k)
          (let [v (locate xw lon lat)]
            (.put m k v)
            v))))))

;; --- aggregation -----------------------------------------------------------------------

(defn coarser
  "Cell values {cell v} at finer resolutions on the cells at `r` holding
  them (`supercell`), never by id parent: :sum (stocks; the total is kept),
  :mean (intensities; cells are equal in area, so the plain mean of the
  fine cells is the area-weighted one), :max, :count (the fine cells)."
  [values r how]
  (let [acc (reduce-kv (fn [m c v]
                         (if (nil? v)
                           m
                           (let [u (supercell c r) v (double v)]
                             (update m u (fn [{:keys [sum n mx] :or {sum 0.0 n 0}}]
                                           {:sum (+ sum v) :n (inc n) :mx (if mx (max mx v) v)})))))
                       {} values)]
    (into {} (map (fn [[u {:keys [sum n mx]}]]
                    [u (case how :sum sum :mean (/ sum n) :max mx :count n)]))
          acc)))

(defn unit-of
  "The unit at `level` (:cell-country :province :country :subregion :region
  :world) a part of a cell (a row {:cell :iso :admin1}) counts in; nil when
  it has none there (an offshore part has no province)."
  [level {:keys [cell iso admin1]}]
  (case level
    :cell-country (cell-key cell iso)
    :province admin1
    :country iso
    :subregion (when (geo/country? iso) (first (geo/parents iso)))
    :region (when (geo/country? iso) (some-> (first (geo/parents iso)) geo/parents first))
    :world geo/world))

(defn- combine [how acc]
  (case how
    :sum (into {} (map (fn [[u {:keys [sum]}]] [u sum])) acc)
    :mean (into {} (keep (fn [[u {:keys [num den]}]] (when (pos? den) [u (/ num den)]))) acc)
    :max (into {} (map (fn [[u {:keys [mx]}]] [u mx])) acc)
    :count (into {} (map (fn [[u {:keys [cells]}]] [u (count cells)])) acc)))

(defn- accumulate [acc u c v w]
  (update acc u (fn [{:keys [sum num den mx cells] :or {sum 0.0 num 0.0 den 0.0}}]
                  {:sum (+ sum (* v w)) :num (+ num (* v w)) :den (+ den w)
                   :mx (if mx (max mx v) v) :cells (conj (or cells #{}) c)})))

(defn aggregate
  "Cell values `values` ({cell v}, cells at resolution 6) by the units at
  `level` (see `unit-of`), through the crosswalk's area shares:

    :sum    stocks (capacity, people, output): v times each part's share,
            so the total is kept wherever a cell's shares sum to 1
    :mean   intensities (density, a rate): the area-weighted mean of the
            values over the unit's parts
    :max    the largest value in a cell reaching the unit
    :count  the cells reaching the unit

  Cells finer than resolution 6 are first put on the cells holding them
  (`coarser`: summed for :sum and :count, averaged for :mean, the largest
  for :max), so :count then counts resolution-6 cells.

  Options: :land-only? true uses only the land parts of each cell, their
  shares renormalised to 1 (for values that live on land, such as people or
  night lights); cells without land are dropped."
  ([xw values level how] (aggregate xw values level how {}))
  ([xw values level how {:keys [land-only?]}]
   (let [values (if (every? #(= resolution (res %)) (keys values))
                  values
                  (coarser values resolution (case how (:sum :count) :sum :mean :mean :max :max)))]
    (combine how
            (reduce-kv
             (fn [acc c v]
               (if (nil? v)
                 acc
                 (let [rows (get-in xw [:by-cell c])
                       rows (if land-only? (filter #(#{"land" "disputed"} (:flag %)) rows) rows)
                       tot (if land-only? (reduce + 0.0 (map :share rows)) 1.0)]
                   (reduce (fn [acc row]
                             (if-let [u (unit-of level row)]
                               (accumulate acc u c (double v) (/ (:share row) tot))
                               acc))
                           acc (filter #(pos? (:share %)) rows)))))
             {} values)))))

(defn rollup
  "Located records ({:cell :iso :admin1 :value}, an asset or an event in one
  country's part of a cell) by the units at `level`: :sum, :mean (of the
  records), :max or :count (of the records)."
  [records level how]
  (let [acc (reduce (fn [acc {:keys [value] :as r}]
                      (if-let [u (and (some? value) (unit-of level r))]
                        (update acc u (fn [{:keys [sum n mx] :or {sum 0.0 n 0}}]
                                        {:sum (+ sum value) :n (inc n) :mx (if mx (max mx value) value)}))
                        acc))
                    {} records)]
    (into {} (map (fn [[u {:keys [sum n mx]}]]
                    [u (case how :sum sum :mean (/ sum n) :max mx :count n)]))
          acc)))

;; --- ingest: points --------------------------------------------------------------------

(defn place-points
  "Points ({:lon :lat :iso ...}) on cells: each gets :cell and :admin1, and
  keeps its source's :iso. Sources geocode to the wrong side of a border, so
  where the crosswalk puts the point in another country the source's country
  is kept and the disagreement counted: :near-border when the source's
  country is in the point's cell, :not-in-cell when it is not. A point with
  no country takes the crosswalk's (:from-crosswalk); its :iso-from says
  which. The province is the crosswalk's when the countries agree, else the
  largest part of the source's country in the cell, else nil.
  {:points [...] :disagree {:points :from-crosswalk :near-border :not-in-cell}}"
  [xw points]
  (let [n (volatile! {:points 0 :from-crosswalk 0 :near-border 0 :not-in-cell 0})
        tick (fn [k] (vswap! n update k inc))
        out (mapv (fn [{:keys [lon lat iso] :as p}]
                    (tick :points)
                    (let [c (cell lon lat)
                          at (locate xw lon lat)
                          rows (get-in xw [:by-cell c])
                          main-admin1 (fn [iso] (some->> rows (filter #(and (= iso (:iso %)) (:admin1 %)))
                                                         seq (apply max-key :share) :admin1))]
                      (cond
                        (str/blank? iso)
                        (do (when at (tick :from-crosswalk))
                            (assoc p :cell c :iso (:iso at) :admin1 (:admin1 at) :iso-from (if at "crosswalk" "none")))
                        (or (nil? at) (= iso (:iso at)))
                        (assoc p :cell c :admin1 (if at (:admin1 at) (main-admin1 iso)) :iso-from "source")
                        :else
                        (let [in-cell? (some #(= iso (:iso %)) rows)]
                          (tick (if in-cell? :near-border :not-in-cell))
                          (assoc p :cell c :admin1 (main-admin1 iso) :iso-from "source"
                                 :crosswalk-iso (:iso at))))))
                  points)]
    {:points out :disagree @n}))

;; --- ingest: rasters -------------------------------------------------------------------

(defn raster->cells
  "A lon/lat grid on cells by sampling. `grid` {:lon0 :lat0 :dlon :dlat :ncol
  :nrow :value}, (lon0, lat0) the outer corner of pixel (0, 0) and dlat
  negative for a north-up raster (as a GeoTIFF's transform), (value col row)
  the pixel's value or nil for no data. Each pixel is split into sub x sub
  parts (option :sub, default enough for parts under 0.1 degree), each part
  counted in the cell (option :res, default 6) holding its centre.

  {cell {:area :sum :mean}}: the sampled area (km2), the sum and the
  area-weighted mean. With :kind :density (the default) a value is per km2,
  so the sum is value times area; with :kind :count it is the pixel's total
  (people, output), shared out by area."
  ([grid] (raster->cells grid {}))
  ([{:keys [lon0 lat0 dlon dlat ncol nrow value]} {:keys [sub res kind] :or {res resolution kind :density}}]
   (let [sub (or sub (max 1 (long (Math/ceil (/ (max (Math/abs (double dlon)) (Math/abs (double dlat))) 0.1)))))
         slon (/ dlon sub) slat (/ dlat sub)
         acc (volatile! (transient {}))]
     (dotimes [row nrow]
       (dotimes [col ncol]
         (when-let [v (value col row)]
           (let [v (double v)
                 x0 (+ lon0 (* col dlon)) y0 (+ lat0 (* row dlat))
                 parea (lon-lat-box-km2 x0 y0 (+ x0 dlon) (+ y0 dlat))]
             (dotimes [i sub]
               (dotimes [j sub]
                 (let [x (+ x0 (* i slon)) y (+ y0 (* j slat))
                       a (lon-lat-box-km2 x y (+ x slon) (+ y slat))
                       c (cell (+ x (/ slon 2)) (+ y (/ slat 2)) res)
                       s (if (= kind :count) (* v (/ a parea)) (* v a))
                       {:keys [area sum num] :or {area 0.0 sum 0.0 num 0.0}} (get @acc c)]
                   (vswap! acc assoc! c {:area (+ area a) :sum (+ sum s) :num (+ num (* v a))}))))))))
     (into {} (map (fn [[c {:keys [area sum num]}]] [c {:area area :sum sum :mean (/ num area)}]))
           (persistent! @acc)))))

;; --- ingest: polygons ------------------------------------------------------------------

(defn- unwrap
  "Rings across the antimeridian with their western longitudes moved past 180."
  [rings]
  (let [lons (map first (apply concat rings))]
    (if (> (- (apply max lons) (apply min lons)) 180)
      (mapv (fn [ring] (mapv (fn [[x y]] [(if (neg? x) (+ x 360.0) x) y]) ring)) rings)
      rings)))

(defn- inside?
  "Even-odd: inside an odd number of `rings` (holes are rings too)."
  [rings x y]
  (odd? (reduce (fn [n ring]
                  (loop [i 0 j (dec (count ring)) n n]
                    (if (= i (count ring))
                      n
                      (let [[xi yi] (nth ring i) [xj yj] (nth ring j)]
                        (recur (inc i) i
                               (if (and (not= (> yi y) (> yj y))
                                        (< x (+ xi (/ (* (- xj xi) (- y yi)) (- yj yi)))))
                                 (inc n) n))))))
                0 rings)))

(defn- ring-area-km2
  "A ring's area on the sphere, km2: the shoelace in the sinusoidal (equal
  area) projection about longitude `lon-c`, edges cut to 0.1 degree."
  [ring lon-c]
  (let [k (/ (* Math/PI earth-km) 180.0)
        proj (fn [[x y]] [(* k (- x lon-c) (Math/cos (Math/toRadians y))) (* k y)])
        dense (mapcat (fn [[x1 y1] [x2 y2]]
                        (let [m (max 1 (long (Math/ceil (/ (max (Math/abs (- x2 x1)) (Math/abs (- y2 y1))) 0.1))))]
                          (for [t (range m)] [(+ x1 (* (- x2 x1) (/ t m))) (+ y1 (* (- y2 y1) (/ t m)))])))
                      ring (concat (rest ring) [(first ring)]))
        ps (mapv proj dense)
        n (count ps)]
    (Math/abs (/ (reduce + 0.0 (for [i (range n)
                                     :let [[x1 y1] (ps i) [x2 y2] (ps (mod (inc i) n))]]
                                 (- (* x1 y2) (* x2 y1))))
                 2.0))))

(defn polygon-area-km2
  "A polygon's area, km2: its first ring less the others (holes)."
  [rings]
  (let [rings (unwrap rings)
        lon-c (let [xs (map first (first rings))] (/ (+ (apply min xs) (apply max xs)) 2.0))
        [outer & holes] (map #(ring-area-km2 % lon-c) rings)]
    (max 0.0 (- outer (reduce + 0.0 holes)))))

(defn polygon-samples
  "Points inside the polygon `rings` on a regular grid, [[lon lat w] ...],
  each weighted by its grid box's area (km2): about 400 boxes over the
  polygon's bounding box, but at least 0.5 km apart (locating is good to
  about 6 km, so a small mine needs a few samples, not hundreds) and at most
  5.7 km (a resolution-10 cell's width). A polygon too thin for any falls
  back to its vertices' mean."
  [rings]
  (let [rings (unwrap rings)
        pts (apply concat rings)
        xs (map first pts) ys (map second pts)
        [x0 x1 y0 y1] [(apply min xs) (apply max xs) (apply min ys) (apply max ys)]
        box (max 1e-12 (lon-lat-box-km2 x0 y0 x1 y1))
        km (min 5.7 (max 0.5 (Math/sqrt (/ box 400.0))))
        dy (max 1e-6 (/ km 111.195))
        dx (max 1e-6 (/ dy (max 0.05 (Math/cos (Math/toRadians (/ (+ y0 y1) 2.0))))))
        nx (max 1 (long (Math/ceil (/ (- x1 x0) dx))))
        ny (max 1 (long (Math/ceil (/ (- y1 y0) dy))))
        dx (/ (- x1 x0) nx) dy (/ (- y1 y0) ny)
        norm (fn [x] (if (> x 180.0) (- x 360.0) x))
        hits (for [i (range nx) j (range ny)
                   :let [x (+ x0 (* (+ i 0.5) dx)) y (+ y0 (* (+ j 0.5) dy))]
                   :when (inside? rings x y)]
               [(norm x) y (lon-lat-box-km2 (- x (/ dx 2)) (- y (/ dy 2)) (+ x (/ dx 2)) (+ y (/ dy 2)))])]
    (if (seq hits)
      (vec hits)
      [[(norm (/ (reduce + xs) (count xs))) (/ (reduce + ys) (count ys)) 1.0]])))

(defn- one-cell
  "The cell holding every vertex, if one does: the polygon is then inside it
  (cells are convex at this scale)."
  [rings r]
  (let [cs (into #{} (map (fn [[x y]] (cell x y r))) (apply concat rings))]
    (when (= 1 (count cs)) (first cs))))

(defn polygon->cells
  "A polygon (`rings`: the outer ring, then holes, each [[lon lat] ...]) on
  cells at resolution `r` (default 6) by area overlap: {:area km2 :shares
  {cell share}}, the shares summing to 1."
  ([rings] (polygon->cells rings resolution))
  ([rings r]
   (let [area (polygon-area-km2 rings)]
     (if-let [c (one-cell rings r)]
       {:area area :shares {c 1.0}}
       (let [ss (polygon-samples rings)
             tot (reduce + (map #(nth % 2) ss))]
         {:area area
          :shares (reduce (fn [m [x y w]] (update m (cell x y r) (fnil + 0.0) (/ w tot))) {} ss)})))))

(defn place-polygon
  "A polygon ({:rings :iso ...}) or a multipolygon ({:polygons [rings ...]
  :iso ...}) on the crosswalk: {:area km2 :elsewhere :moved :parts [{:cell
  :iso :admin1 :share :area :xw-iso}]}, one part per cell, country and
  province it covers, where the crosswalk puts it, the shares summing to 1.

  The source's :iso, when given, is kept where the source's country reaches
  the part's cell (a border drawn a little differently; :xw-iso then names
  the crosswalk's country and the province is the cell's largest of the
  source's), and given up where it does not: a field or a mine across a
  border is in the other country there. :elsewhere is the share of the area
  the crosswalk puts in another country than the source's, :moved the
  share given to it."
  [xw {:keys [rings polygons iso]}]
  (let [polys (or polygons [rings])
        areas (mapv polygon-area-km2 polys)
        area (reduce + 0.0 areas)
        located (vec (mapcat (fn [rings a]
                               (let [c (one-cell rings resolution)
                                     ;; inside one cell the crosswalk does not split: one sample
                                     ss (if (and c (<= (count (get-in xw [:by-cell c])) 1))
                                          (let [[x y] (first (first rings))] [[x y 1.0]])
                                          (polygon-samples rings))
                                     tot (reduce + (map #(nth % 2) ss))
                                     wt (if (pos? area) (/ a area) (/ 1.0 (count polys)))]
                                 (for [[x y w] ss]
                                   [(or (locate xw x y) {:cell (cell x y) :iso nil :admin1 nil}) (* wt (/ w tot))])))
                             polys areas))
        source? (not (str/blank? iso))
        reaches? (fn [c] (some #(= iso (:iso %)) (get-in xw [:by-cell c])))
        main-admin1 (fn [c] (some->> (get-in xw [:by-cell c]) (filter #(and (= iso (:iso %)) (:admin1 %)))
                                     seq (apply max-key :share) :admin1))
        other? (fn [at] (and source? (:iso at) (not= iso (:iso at))))
        elsewhere (reduce + 0.0 (keep (fn [[at w]] (when (other? at) w)) located))
        moved (reduce + 0.0 (keep (fn [[at w]] (when (and (other? at) (not (reaches? (:cell at)))) w)) located))
        parts (reduce (fn [m [at w]]
                        (let [k (cond
                                  (not (other? at)) [(:cell at) (if source? iso (:iso at)) (:admin1 at) nil]
                                  (reaches? (:cell at)) [(:cell at) iso (main-admin1 (:cell at)) (:iso at)]
                                  :else [(:cell at) (:iso at) (:admin1 at) nil])]
                          (update m k (fnil + 0.0) w)))
                      {} located)]
    {:area area :elsewhere elsewhere :moved moved
     :parts (vec (for [[[c i a x] w] (sort-by (comp - val) parts)]
                   {:cell c :iso i :admin1 a :share w :area (* w area) :xw-iso x}))}))

;; --- borders over time -----------------------------------------------------------------

(def succession
  "States that split, with the last year they existed and their successors
  (as data/derived/material/succession.csv): a year up to `:until` belongs
  to the predecessor. Belgium-Luxembourg (BLX), a trade-reporting union
  rather than a state, is left out."
  {"SUN" {:until 1991 :successors #{"ARM" "AZE" "BLR" "EST" "GEO" "KAZ" "KGZ" "LTU" "LVA" "MDA"
                                     "RUS" "TJK" "TKM" "UKR" "UZB"}}
   "YUG" {:until 1991 :successors #{"BIH" "HRV" "MKD" "SCG" "SVN"}}
   "SCG" {:until 2005 :successors #{"SRB" "MNE" "XKX"}}
   "CSK" {:until 1992 :successors #{"CZE" "SVK"}}
   "ETP" {:until 1992 :successors #{"ETH" "ERI"}}
   "SDF" {:until 2011 :successors #{"SDN" "SSD"}}
   "ANT" {:until 2010 :successors #{"CUW" "SXM" "BES"}}})

(def earlier-splits
  "Parts that left a state before it split, which succession.csv does not
  list: Aruba left the Netherlands Antilles on 1 January 1986; Kosovo was
  in Serbia from 2006 until it declared independence in February 2008 (in
  Serbia and Montenegro and Yugoslavia before that, through Serbia)."
  {"ABW" ["ANT" 1985]
   "XKX" ["SRB" 2007]})

(def ^:private predecessor
  (merge (into {} (for [[p {:keys [until successors]}] succession s successors] [s [p until]]))
         earlier-splits))

(def east-german-provinces
  "Brandenburg, Mecklenburg-Vorpommern, Saxony, Saxony-Anhalt, Thuringia
  (GeoNames' DEU.11 to .15). Berlin (DEU.16) is one province, so all of it
  counts with the West: East Berlin (about 1.3 of 3.4 million people in
  1989, and the GDR's capital) is read as DEW, a known error a province key
  cannot avoid."
  #{"DEU.11" "DEU.12" "DEU.13" "DEU.14" "DEU.15"})

(def south-yemen-provinces
  "The People's Democratic Republic of Yemen (YMD) to its union with the
  Yemen Arab Republic (YAR) on 22 May 1990: Aden, Lahij, Abyan, Shabwah,
  Hadramawt, Al Mahrah, Socotra and Ad Dali' (most of which was in the
  South)."
  #{"YEM.02" "YEM.24" "YEM.01" "YEM.05" "YEM.04" "YEM.03" "YEM.28" "YEM.18"})

(defn country-in-year
  "The state a part of a cell (country `iso`, province `admin1`) belonged to
  in `year`, for joining sources that keep historical states: the USSR
  (SUN) to 1991, Yugoslavia (YUG) to 1991 and then Serbia and Montenegro
  (SCG) to 2005, Czechoslovakia (CSK) to 1992, Ethiopia PDR (ETP) to 1992,
  Sudan (SDF) to 2011, the Netherlands Antilles (ANT) to 2010 (Aruba to
  1985), Kosovo in Serbia to 2007 (`earlier-splits`), East (DDR) or West
  (DEW) Germany to 1990 by province (an unknown province, and Berlin, count
  with the West), and South (YMD) or North (YAR) Yemen to 1989 by province
  (an unknown province counts with the North). A year is the state's whole
  through the year it ended (`:until`), as succession.csv's sources keep it.
  The codes are succession.csv's (DDR and DEW as Comtrade's 278 and 280);
  bnm.geo's code aliases (FRG, DDR -> DEU) are for coded events without a
  place."
  [iso admin1 year]
  (cond
    (and (= "DEU" iso) (<= year 1990)) (if (east-german-provinces admin1) "DDR" "DEW")
    (and (= "YEM" iso) (<= year 1989)) (if (south-yemen-provinces admin1) "YMD" "YAR")
    :else (if-let [[p until] (predecessor iso)]
            (if (<= year until) (country-in-year p nil year) iso)
            iso)))
