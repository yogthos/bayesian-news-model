(ns bnm.located-shocks
  "Does cell-level concentration predict the output loss from located
  shocks better than country-level? (bead bnm-1pq.27; docs/ESSENTIALS.md
  section 8 step 10, registered before the test years are read.)

  Shocks are data/derived/hazards/shocks.csv (scripts/located-shocks.py):
  earthquakes (ShakeMap MMI 7 or more), tropical cyclones (hurricane-force
  wind radius), floods (DFO's affected areas) and conflict onsets (GDELT on
  cells), each a set of A5 cells with the share of each its footprint
  covers. A cell is hit when the footprint covers half of it or more
  (`hit-cells`); a country's hits in a year are the hit cells where it has
  land (`country-hits`), whatever the hazard.

  Units are (key, country, year) for the `outcome-keys`: the bnm.stages
  commodities with plants on cells (stages.csv; cement from BGS, which
  stages.csv does not hold) and refinery throughput (UNSD crude oil into
  refineries, oil-products.csv). A unit counts only where the country has a
  plant of the key present the year before (bnm.cell-profiles/present?: a
  plant must be known at the time of the shock), so both exposures are read
  where a cell model could know where the plants are.

  Exposures, of the country's hit cells in the year:
    cell     the share of the country's capacity of the key in the hit
             cells (bnm.cell-profiles/cell-cut on the country's assets
             present the year before; capacity as in cells.csv, a missing
             one the key's world median, `weigh`)
    country  the hit share of the country's land: the same share were the
             capacity spread evenly over its land, what a country-level
             model can know
  The outcome is the log of the mean output of the shock year and the next
  against the mean of the three years before (`log-change`).

  `compare-exposures`: outcome on exposure with key-year effects (each
  demeaned within its key and year), one slope per exposure, scored by
  leave-one-year-out squared error; the cell-minus-country difference has a
  block bootstrap by year. Cell wins when the difference is below zero and
  its 90% interval excludes zero. Fewer than 50 country-years with a cell
  exposure of 0.05 or more (`exposed-country-years`) is underpowered, not
  read. Placebos: the shocks dated a year early (`hit-cells` :shift), and
  each country-year's hit cells moved to a random land position in the same
  country (`move-footprint`)."
  (:require [bnm.cell-profiles :as cp]
            [bnm.cells :as cells]
            [bnm.csv :as csv]
            [bnm.stages :as stages]
            [clojure.string :as str]))

(def shocks-path "data/derived/hazards/shocks.csv")
(def cement-path "data/derived/hazards/cement.csv")
(def oil-path "data/derived/supply/oil-products.csv")

(def last-development-year
  "The last output year development may read: 2011 on is the registered
  test's."
  2010)

(def outcome-keys
  "Output keys: the bnm.cell-profiles asset keys whose capacity makes them
  and the output series ([commodity stage] of stages.csv, cement.csv's
  cement plant, or refinery intake)."
  {"steel" {:assets #{"steel" "iron-and-steel-plant"} :series ["iron" "refinery"]}
   "aluminium" {:assets #{"aluminum-plant"} :series ["aluminium" "smelter"]}
   "alumina" {:assets #{"alumina-plant"} :series ["aluminium" "refinery"]}
   "copper" {:assets #{"copper-plant"} :series ["copper" "refinery"]}
   "nickel" {:assets #{"nickel-plant"} :series ["nickel" "refinery"]}
   "zinc" {:assets #{"zinc-plant"} :series ["zinc" "smelter"]}
   "cobalt" {:assets #{"cobalt-plant"} :series ["cobalt" "refinery"]}
   "cement" {:assets #{"cement" "cement-plant"} :series ["cement" "plant"]}
   "refinery" {:assets #{"refinery"} :series :refinery-intake}})

(def asset->key
  (into {} (for [[k {:keys [assets]}] outcome-keys a assets] [a k])))

;; ------------------------------------------------------------------- shocks

(defn read-shocks
  "shocks.csv as [{:shock :hazard :date :year :cell :coverage :intensity}]."
  [path]
  (vec (for [m (csv/read-maps path \,)]
         {:shock (:shock_id m) :hazard (:hazard m) :date (:date m) :year (parse-long (:year m))
          :cell (:cell m) :coverage (parse-double (:coverage m))
          :intensity (when-not (str/blank? (:intensity m)) (parse-double (:intensity m)))})))

(defn hit-cells
  "{year #{cell}}: the cells shocks cover at :min-coverage (default 0.5) or
  more, of the :hazards given (default all), each dated :shift years
  (default 0) from its own year."
  [shocks {:keys [min-coverage hazards shift] :or {min-coverage 0.5 shift 0}}]
  (reduce (fn [m {:keys [hazard year cell coverage]}]
            (if (and (>= coverage min-coverage) (or (nil? hazards) (hazards hazard)))
              (update m (+ year shift) (fnil conj #{}) cell)
              m))
          {} shocks))

(defn land-index
  "{:by-cell {cell {iso km2}} :total {iso km2}} from bnm.cell-profiles/
  land-km2's {part km2}."
  [land]
  (reduce-kv (fn [m k km2]
               (if (pos? km2)
                 (-> m
                     (assoc-in [:by-cell (cells/key-cell k) (cells/key-iso k)] km2)
                     (update-in [:total (cells/key-iso k)] (fnil + 0.0) km2))
                 m))
             {:by-cell {} :total {}} land))

(defn country-hits
  "{[iso year] #{cell}}: each year's hit cells, to every country with land
  in them."
  [idx by-year]
  (reduce-kv (fn [m y cs]
               (reduce (fn [m c]
                         (reduce (fn [m iso] (update m [iso y] (fnil conj #{}) c))
                                 m (keys (get-in idx [:by-cell c]))))
                       m cs))
             {} by-year))

(defn land-share
  "The share of the country's land in cells `cs`."
  [idx iso cs]
  (let [tot (get-in idx [:total iso] 0.0)]
    (if (pos? tot)
      (/ (reduce + 0.0 (keep #(get-in idx [:by-cell % iso]) cs)) tot)
      0.0)))

(defn country-land-cells
  "The cells where the country has land, sorted."
  [idx iso]
  (vec (sort (keep (fn [[c m]] (when (get m iso) c)) (:by-cell idx)))))

;; ------------------------------------------------------------------- plants

(defn- weight
  "bnm.cell-profiles' weight of a row (its capacity; the key's median when
  missing, or when a <commodity>-plant's is over outlier-ratio times it)."
  [meds r]
  (let [k (cp/asset-key r) m (meds k) c (cp/capacity r)]
    (cond
      (and c m (str/ends-with? k "-plant") (> c (* cp/outlier-ratio m))) (* m (:share r 1.0))
      c c
      m (* m (:share r 1.0)))))

(defn weigh
  "The asset rows of the outcome keys, each with its capacity weight as in
  cells.csv (the world median of its key when missing) written in as its
  capacity, in the unit bnm.cell-profiles/capacity reads (t, bbl/d for
  crude refineries): the country's rows can then be cut alone without
  their missing capacities taking the country's median."
  [rows]
  (let [rows (filter #(asset->key (cp/asset-key %)) rows)
        meds (cp/medians rows)]
    (vec (keep (fn [r]
                 (when-let [w (weight meds r)]
                   (assoc r :capacity w :unit (if (= "refinery" (cp/asset-key r)) "bbl/d" "t"))))
               rows))))

(defn by-iso [rows] (group-by :iso rows))

(def backcast-from "The year a backcast asset with no start year is read from." 1900)

(defn backcast
  "The rows read back from their start year (`backcast-from` without one),
  as bnm.cell-profiles/present-backcast? reads them: not causal (a plant
  first listed in 2021 is placed in 1995), a sensitivity only."
  [rows]
  (mapv #(assoc % :known_from (or (:start_year %) backcast-from)) rows))

(def ^:private products (into {} (for [[a k] asset->key] [a [k]])))

(defn cell-exposure
  "{key share}: the share of the country's capacity of each key, present
  the year before `year`, in cells `cut` (bnm.cell-profiles/cell-cut on the
  country's weighed rows, `by-iso`)."
  [by-iso iso year cut]
  (if (empty? cut)
    {}
    (into {} (for [[[_ k] x] (cp/cell-cut (get by-iso iso []) cut {:year (dec year) :products products})]
               [k x]))))

(defn known-keys
  "The keys the country has a plant of, present the year before `year`."
  [by-iso iso year]
  (into #{} (keep #(when (cp/present? % (dec year)) (asset->key (cp/asset-key %)))) (get by-iso iso [])))

;; ------------------------------------------------------------------ outputs

(defn- latest-known
  "{[commodity stage iso year] quantity} of each data year's latest-known
  source."
  [rows]
  (let [best (reduce (fn [m {:keys [commodity stage iso year known] :as r}]
                       (let [k [commodity stage iso year]]
                         (if (or (nil? (m k)) (> known (:known (m k)))) (assoc m k r) m)))
                     {} rows)]
    (into {} (for [[k r] best :when (:quantity r)] [k (:quantity r)]))))

(defn read-outputs
  "{[key iso] {year quantity}} from stages.csv, cement.csv and
  oil-products.csv (crude oil, 4100, into refineries, flow 086, kt)."
  [stages-path cement-path oil-path]
  (let [q (latest-known (concat (stages/read-rows stages-path) (stages/read-rows cement-path)))
        by-series (into {} (for [[k {:keys [series]}] outcome-keys :when (vector? series)] [series k]))
        from-stages (reduce-kv (fn [m [c s iso y] x]
                                 (if-let [k (by-series [c s])] (assoc-in m [[k iso] y] x) m))
                               {} q)]
    (reduce (fn [m r]
              (if (and (= "4100" (:product r)) (= "086" (:flow r)))
                (if-let [v (parse-double (:value r))]
                  (assoc-in m [["refinery" (:iso r)] (parse-long (:year r))] v)
                  m)
                m))
            from-stages (csv/read-maps oil-path \,))))

(defn log-change
  "The log of the mean output of `y` and the year after against the mean of
  the three years before; nil when any is missing or not positive."
  [q y]
  (let [now (map q [y (inc y)]) before (map q (range (- y 3) y))]
    (when (every? #(and % (pos? %)) (concat now before))
      (- (Math/log (/ (reduce + now) 2.0)) (Math/log (/ (reduce + before) 3.0))))))

(defn clip-outcome
  "The outcome held to [-clip, clip] (none when clip is nil): a small
  producer's output can halve or double in a year for reasons no shock
  explains, and one such unit would outweigh the rest in squared error."
  [y clip]
  (if clip (max (- clip) (min clip y)) y))

(defn output-years
  "The output years the outcomes of shock years `from`-`to` read."
  [from to]
  [(- from 3) (inc to)])

(defn check-window
  "nil, or throws when the shock years `from`-`to` would read an output year
  after `last-development-year` without `registered?`."
  [from to registered?]
  (let [[_ hi] (output-years from to)]
    (when (and (> hi last-development-year) (not registered?))
      (throw (ex-info (str "shock years " from "-" to " read output to " hi
                           ": the registered test's years; pass --registered true")
                      {:from from :to to})))))

(defn panel
  "[{:key :iso :year :y :cell :country}] for shock years `from`-`to`: every
  (key, country, year) with an outcome (clipped at :clip, `clip-outcome`)
  and a plant of the key known the year before; exposures 0 where the
  country has no hit."
  [{:keys [outputs by-iso land-idx hits from to clip]}]
  (let [cache (volatile! {})
        cell-x (fn [iso y cut]
                 (let [k [iso y cut]]
                   (or (@cache k) (let [v (cell-exposure by-iso iso y cut)] (vswap! cache assoc k v) v))))]
    (vec (for [[[k iso] q] (sort-by key outputs)
               y (range from (inc to))
               :let [yv (log-change q y)]
               :when (and yv ((known-keys by-iso iso y) k))
               :let [cut (get hits [iso y] #{})]]
           {:key k :iso iso :year y :y (clip-outcome yv clip)
            :cell (if (seq cut) (get (cell-x iso y cut) k 0.0) 0.0)
            :country (land-share land-idx iso cut)}))))

(defn exposed-country-years
  "Distinct country-years with a unit whose cell exposure is `thr` or more."
  [units thr]
  (count (into #{} (keep #(when (>= (:cell %) thr) [(:iso %) (:year %)])) units)))

;; ------------------------------------------------------------------ scoring

(defn rng
  "A seeded generator (Park-Miller): a function of n giving 0..n-1."
  [seed]
  (let [s (volatile! (inc (mod (Math/abs (long seed)) 2147483646)))]
    (fn [n]
      (vswap! s #(mod (* 16807 %) 2147483647))
      (mod @s n))))

(defn- demean
  "The units with :y, :cell and :country less their key-year means (as :ty
  :tc :tl)."
  [units]
  (vec (mapcat (fn [[_ us]]
                 (let [n (double (count us))
                       mean (fn [f] (/ (reduce + 0.0 (map f us)) n))
                       my (mean :y) mc (mean :cell) ml (mean :country)]
                   (map #(assoc % :ty (- (:y %) my) :tc (- (:cell %) mc) :tl (- (:country %) ml)) us)))
               (group-by (juxt :key :year) units))))

(defn- slope [us x]
  (let [sxx (reduce + 0.0 (map #(* (x %) (x %)) us))
        sxy (reduce + 0.0 (map #(* (x %) (:ty %)) us))]
    (if (pos? sxx) (/ sxy sxx) 0.0)))

(defn- quantile [xs p]
  (let [v (vec (sort xs)) n (count v)]
    (when (pos? n) (v (min (dec n) (long (Math/floor (* p (dec n)))))))))

(defn- unit-demean
  "The units less their (key, country) means over `train` (units of other
  years); those with no training year dropped."
  [units train]
  (let [means (into {} (for [[k us] (group-by (juxt :key :iso) train)
                             :let [n (double (count us)) m (fn [f] (/ (reduce + 0.0 (map f us)) n))]]
                         [k [(m :y) (m :cell) (m :country)]]))]
    (vec (keep (fn [u] (when-let [[my mc ml] (means [(:key u) (:iso u)])]
                         (assoc u :y (- (:y u) my) :cell (- (:cell u) mc) :country (- (:country u) ml))))
               units))))

(defn compare-exposures
  "Leave-one-year-out squared error of the outcome on each exposure, with
  key-year effects (and, with :unit-effects, (key, country) effects taken
  from the training years first): {:mse-cell :mse-country :mse-null :diff
  (cell less country) :interval (the 5% and 95% points of :diff over
  :draws block bootstrap resamples of the years, seed :seed) :verdict
  (:cell, :country or :none) :slope-cell :slope-country (all years) :n
  :years :per-year}."
  [units {:keys [draws seed unit-effects] :or {draws 1000 seed 1}}]
  (let [plain (when-not unit-effects (demean units))
        years (vec (sort (distinct (map :year units))))
        per-year (into (sorted-map)
                       (for [t years
                             :let [us (or plain (demean (unit-demean units (remove #(= t (:year %)) units))))
                                   train (remove #(= t (:year %)) us)
                                   test (filter #(= t (:year %)) us)
                                   bc (slope train :tc) bl (slope train :tl)
                                   se (fn [b x] (reduce + 0.0 (map #(let [e (- (:ty %) (* b (x %)))] (* e e)) test)))]]
                         [t {:n (count test) :se-cell (se bc :tc) :se-country (se bl :tl) :se-null (se 0.0 :tc)
                             :slope-cell bc :slope-country bl}]))
        tot (fn [ys f] (reduce + 0.0 (map #(f (per-year %)) ys)))
        n (tot years :n)
        mse (fn [f] (if (pos? n) (/ (tot years f) n) 0.0))
        diff-of (fn [ys] (let [m (tot ys :n)] (if (pos? m) (/ (- (tot ys :se-cell) (tot ys :se-country)) m) 0.0)))
        r (rng seed)
        boots (when (seq years)
                (vec (repeatedly draws #(diff-of (repeatedly (count years) (fn [] (years (r (count years)))))))))
        lo (quantile boots 0.05) hi (quantile boots 0.95)
        diff (diff-of years)
        all (or plain (demean (unit-demean units units)))]
    {:mse-cell (mse :se-cell) :mse-country (mse :se-country) :mse-null (mse :se-null)
     :diff diff :interval [lo hi]
     :verdict (cond (and lo (neg? diff) (neg? hi)) :cell
                    (and lo (pos? diff) (pos? lo)) :country
                    :else :none)
     :slope-cell (slope all :tc) :slope-country (slope all :tl)
     :n (long n) :years years :per-year per-year}))

;; ----------------------------------------------------------------- placebos

(defn move-footprint
  "The cells `hit` moved as one to a random land position of the country
  (`land-cells`, its land cells, sorted; seed `seed`): the hit cell nearest
  the footprint's mean centre goes to a random land cell's centre, every
  other cell by the same shift in longitude and latitude, and the cells off
  the country's land are dropped."
  [hit land-cells seed]
  (if (or (empty? hit) (empty? land-cells))
    #{}
    (let [r (rng seed)
          cs (vec (sort hit))
          cents (mapv cells/centre cs)
          mx (/ (reduce + (map first cents)) (count cents))
          my (/ (reduce + (map second cents)) (count cents))
          [ax ay] (apply min-key (fn [[x y]] (+ (* (- x mx) (- x mx)) (* (- y my) (- y my)))) cents)
          [bx by] (cells/centre (land-cells (r (count land-cells))))
          land (set land-cells)
          wrap (fn [x] (cond (> x 180.0) (- x 360.0) (< x -180.0) (+ x 360.0) :else x))]
      (into #{} (comp (map (fn [[x y]] (cells/cell (wrap (+ x (- bx ax))) (max -89.9 (min 89.9 (+ y (- by ay)))))))
                      (filter land))
            cents))))

(defn place-seed
  "A seed for a country-year from the run's seed."
  [seed iso y]
  (reduce (fn [h c] (mod (+ (* h 31) (int c)) 2147483647)) (long seed) (str iso y)))

(defn move-hits
  "Every country-year's hit cells moved (`move-footprint`), seed `seed`."
  [idx hits seed]
  (let [lands (memoize #(country-land-cells idx %))]
    (into {} (for [[[iso y] cs] hits
                   :let [m (move-footprint cs (lands iso) (place-seed seed iso y))]
                   :when (seq m)]
               [[iso y] m]))))
