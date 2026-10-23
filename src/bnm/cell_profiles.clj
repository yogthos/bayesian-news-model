(ns bnm.cell-profiles
  "What is in a cell (beads bnm-3ka.6 and bnm-1pq.11; docs/ECONOMIC-CHARACTER.md
  8c, docs/ESSENTIALS.md section 8 step 10): profiles of A5 cells from the
  grids (scripts/cell-grids.py: data/derived/cells/cell-grids.csv) and the
  assets (data/derived/cells/assets.csv), their types and development
  paths, processing displacement on cells, and concentration by cell with
  the cell cuts bnm.supply/outage takes.

  The unit is a cell's country part, its cell x country key (bnm.cells/
  cell-key, \"DEU@63fe...\"): the grids and the assets are both keyed so.

  Assets. Rows whose dup_of is set repeat another asset and are skipped
  (`read-assets`). An asset is present in year Y (`present?`) from its
  known_from, and absent once its end is known: after end_year (MRDS's last
  production year) from end_known, the source's publication, or, with no
  end year (closed-unknown, idle-unknown, cancelled), from end_known on.
  A closure is never backdated: a mine MRDS reports closed in 1950 counts
  until 2011, when MRDS says so.

  Causality. A grid value is known in its row's known year (cell-grids.py
  gives each grid's lag: GDP 2, lights 1, GHSL 3, EDGAR 1), so the profile
  of a part at year Y reads, per variable, the latest year known by Y
  (`known-value`). The types are fitted at each profile year Y on the
  part-years up to Y only (an expanding window), each fit started from the
  previous one's centroids and matched to them (`match-centroids`), so a
  type keeps its index and name.

  Paths. A five-year step whose end type is due to the data, not the
  place, is left out of the transitions: assets entering at a snapshot's
  publication, or closures reported long after they happened
  (`without-entry`, `entry-flags`), and parts whose lights cannot be
  carried across the 2013/14 DMSP-VIIRS join (`splice-lights`,
  `sensor-flags`)."
  (:require [bnm.cells :as cells]
            [bnm.csv :as csv]
            [bnm.stages :as stages]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ------------------------------------------------------------------ assets

(defn- num [s] (when-not (str/blank? s) (parse-double s)))
(defn- year [s] (some-> (num s) Math/round long))

(defn read-assets
  "The asset rows of `path` (data/derived/cells/assets.csv) not marked as
  another's duplicate, numbers parsed. A file written before the end
  columns reads with blank ends."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & rows] (csv/records r \,)
          ks (mapv keyword h)]
      (into []
            (comp (map #(zipmap ks %))
                  (filter #(str/blank? (:dup_of %)))
                  (map (fn [m]
                         (-> m
                             (select-keys [:cell :iso :admin1 :asset_type :commodity :unit :status :source
                                           :asset_id :year_flag :end_flag])
                             (assoc :capacity (num (:capacity m)) :share (or (num (:share m)) 1.0)
                                    :area_km2 (num (:area_km2 m)) :start_year (year (:start_year m))
                                    :known_from (year (:known_from m)) :end_year (year (:end_year m))
                                    :end_known (year (:end_known m)))
                             (update :end_flag not-empty)))))
            rows))))

(defn present?
  "Is the asset there in year `y`, as known in `y`?"
  [{:keys [known_from end_year end_known]} y]
  (and known_from (<= known_from y)
       (not (and end_known (<= end_known y)
                 (or (nil? end_year) (> y end_year))))))

(defn present-backcast?
  "Is the asset there in year `y`, reading the snapshots back: from its
  start year (always, when it has none), until its end year, or, with no
  end year, until the source reported it closed. Not causal (a snapshot
  asset is read into years before it was known): for describing the past,
  flagged as such wherever used."
  [{:keys [start_year end_year end_known]} y]
  (and (or (nil? start_year) (<= start_year y))
       (cond end_year (<= y end_year)
             end_known (< y end_known)
             :else true)))

(def quarry
  "Construction minerals: quarries, not the extraction a profile reads."
  #{"sand and gravel" "stone" "clay" "limestone" "granite" "fire clay (refractory)" "gypsum-anhydrite"
    "feldspar" "silica" "marble" "sandstone" "dolomite" "pumice" "shale" "perlite" "basalt" "slate"
    "crushed stone" "dimension stone" "sand" "gravel" "kaolin" "bentonite"})

(defn- slug [s] (-> (str/lower-case (str/trim (or s ""))) (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-|-$" "")))

(defn asset-key
  "The kind of asset a row is, for counting and concentration, or nil:
  steel, cement, ethylene, refinery (crude), lng-export, lng-import,
  <commodity>-plant (smelters, other refineries and processing plants),
  power-<fuel>, mine-<commodity>, field, mining-area. Deposits (not
  worked) are nil."
  [{:keys [asset_type commodity]}]
  (let [c (str/lower-case (str/trim (or commodity "")))]
    (case asset_type
      "steel_plant" "steel"
      "cement_plant" "cement"
      "ethylene_plant" "ethylene"
      "refinery" (if (#{"oil" "petroleum" ""} c) "refinery" (str (slug c) "-plant"))
      "lng" (if (str/includes? c "export") "lng-export" "lng-import")
      ("processing_plant" "smelter") (when (seq c) (str (slug c) "-plant"))
      "power_plant" (str "power-" (slug c))
      ("mine" "brine") (when (seq c) (str "mine-" (slug c)))
      "field" "field"
      "mining_area" "mining-area"
      nil)))

(defn group-of
  "The profile group of an asset key."
  [k]
  (cond
    (nil? k) nil
    (or (str/starts-with? k "mine-") (= k "field") (= k "mining-area")) :extraction
    (str/starts-with? k "power-") :power
    :else :processing))

(def tonnes
  "Tonnes per unit of the units the sources give processing capacity in."
  {"metric tons" 1.0 "mt" 1.0 "t" 1.0 "tonnes" 1.0
   "thousand metric tons" 1e3 "tmt" 1e3 "kmt" 1e3 "kt" 1e3 "kt/y" 1e3
   "million metric tons" 1e6 "million mt" 1e6 "mt/y" 1e6
   "kilograms" 1e-3 "kg" 1e-3})

(defn capacity
  "The row's capacity in its key's unit: tonnes a year for materials
  (steel, cement, ethylene, the plants), bbl/d for crude refineries, mmcf/d
  for LNG, MW for power, km2 for fields and mining areas; nil when absent
  or in a unit that does not convert."
  [{:keys [capacity unit] :as r}]
  (when capacity
    (let [k (asset-key r) u (str/lower-case (str/trim (or unit "")))]
      (cond
        (nil? k) nil
        (= k "refinery") (when (= u "bbl/d") capacity)
        (str/starts-with? k "lng") (when (= u "mmcf/d") capacity)
        (str/starts-with? k "power-") (when (= u "mw") capacity)
        (#{"field" "mining-area"} k) (when (= u "km2") capacity)
        (str/starts-with? k "mine-") nil
        :else (some->> (tonnes u) (* capacity))))))

(defn- median [xs]
  (when (seq xs)
    (let [v (vec (sort xs)) n (count v)]
      (if (odd? n) (v (quot n 2)) (/ (+ (v (dec (quot n 2))) (v (quot n 2))) 2.0)))))

(defn medians
  "{key median capacity} over the assets with one, for filling a missing
  capacity: a polygon's pieces (one row per cell, each with its piece's
  capacity) sum to the asset first, so the median is a whole asset's and a
  piece without one weighs it by its share."
  [rows]
  (into {} (for [[k rs] (group-by asset-key rows) :when k
                 :let [whole (vals (reduce (fn [m r] (if-let [c (capacity r)]
                                                       (update m (or (:asset_id r) (gensym)) (fnil + 0.0) c)
                                                       m))
                                           {} rs))
                       m (median whole)] :when m]
             [k m])))

(def outlier-ratio
  "A capacity this many times its key's median is not the product's
  capacity (USGS facilities give ore throughput for some plants, and a
  unit slips: a gold plant at 2,000 t a year against a median of 3): it
  weighs as the median. Only for the <commodity>-plant keys: power plants
  and SFI's plants span orders of magnitude and are kept."
  100.0)

(defn- weight
  "The row's capacity (the key's median when it is an outlier), else its
  key's median (by the row's share of the asset), else nil."
  [meds r]
  (let [m (meds (asset-key r)) c (capacity r)]
    (cond
      (and c m (str/ends-with? (asset-key r) "-plant") (> c (* outlier-ratio m))) (* m (:share r 1.0))
      c c
      m (* m (:share r 1.0)))))

;; ------------------------------------------------------------ concentration

(defn concentration
  "Concentration of capacity of asset key `k` across cells in year `y`
  (assets present then): {:world {:hhi :top :top-share :cells}
  :countries {iso {...}}}; worldwide a cell is one cell whatever its
  countries, within a country its part. Capacity missing is the key's
  median (`medians`)."
  ([rows k y] (concentration rows k y (medians rows)))
  ([rows k y meds]
   (let [rs (filter #(and (= k (asset-key %)) (present? % y)) rows)
         stat (fn [qs]
                (let [sh (stages/shares qs)]
                  (when (seq sh)
                    (let [[t s] (stages/top sh)]
                      {:hhi (stages/hhi sh) :top t :top-share s :cells (count sh)}))))
         world (reduce (fn [m r] (if-let [w (weight meds r)] (update m (:cell r) (fnil + 0.0) w) m)) {} rs)
         by-iso (reduce (fn [m r] (if-let [w (weight meds r)] (update-in m [(:iso r) (:cell r)] (fnil + 0.0) w) m))
                        {} rs)]
     {:world (stat world)
      :countries (into {} (keep (fn [[iso qs]] (when-let [s (stat qs)] [iso s]))) by-iso)})))

(defn concentration-keys
  "The keys worth a concentration: processing and power keys with at least
  `min-n` assets that carry a capacity."
  ([rows] (concentration-keys rows 10))
  ([rows min-n]
   (->> rows
        (filter capacity)
        (group-by asset-key)
        (keep (fn [[k rs]] (when (and k (#{:processing :power} (group-of k))
                                      (>= (count (distinct (map :asset_id rs))) min-n))
                             k)))
        sort vec)))

(defn hhi-stocks
  "Stock rows ({:iso :year :variable :value :top}, as bnm.stages writes)
  for keys `ks` and years `ys`: cells/<k>-hhi, cells/<k>-top-share (the top
  cell in :top) and cells/<k>-cells (bookkeeping), at WLD and per country."
  [rows ks ys]
  (let [meds (medians rows)
        by-key (group-by asset-key rows)]
    (vec (for [k ks y ys
               :let [{:keys [world countries]} (concentration (by-key k) k y meds)]
               [iso s] (cons ["WLD" world] countries)
               :when s
               [v x top] [["hhi" (:hhi s) nil] ["top-share" (:top-share s) (:top s)] ["cells" (double (:cells s)) nil]]]
           {:iso iso :year y :variable (str "cells/" k "-" v) :value x :top top}))))

(defn top-cells
  "The `n` cells holding most of key `k`'s capacity in year `y`:
  [{:cell :isos :share}], the share of world capacity."
  [rows k y n]
  (let [meds (medians rows)
        rs (filter #(and (= k (asset-key %)) (present? % y)) rows)
        qs (reduce (fn [m r] (if-let [w (weight meds r)] (update m (:cell r) (fnil + 0.0) w) m)) {} rs)
        isos (reduce (fn [m r] (update m (:cell r) (fnil conj #{}) (:iso r))) {} rs)
        sh (stages/shares qs)]
    (vec (for [[c s] (take n (sort-by (comp - val) sh))]
           {:cell c :isos (vec (sort (isos c))) :share s}))))

;; ---------------------------------------------------------------- cell cuts

(def row-members
  "EXIOBASE's rest-of-world regions (scripts/product-graph.py ROW_MEMBERS);
  WA takes every country no region does."
  {"WM" "ARE BHR IRN IRQ ISR JOR KWT LBN OMN PSE QAT SAU SYR YEM"
   "WF" (str "AGO BDI BEN BFA BWA CAF CIV CMR COD COG COM CPV DJI DZA EGY ERI ETH GAB GHA GIN GMB GNB GNQ KEN LBR "
             "LBY LSO MAR MDG MLI MOZ MRT MUS MWI NAM NER NGA RWA SDF SDN SSD SEN SHN SLE SOM STP SWZ SYC TCD TGO "
             "TUN TZA UGA ZMB ZWE")
   "WL" (str "ABW AIA ANT ARG ATG BHS BLM BLZ BMU BOL BRB CHL COL CRI CUB CUW CYM DMA DOM ECU FLK GRD GTM GUY HND "
             "HTI JAM KNA LCA MAF MSR NIC PAN PER PRI PRY SLV SPM SUR SXM TCA TTO URY VCT VEN VGB")
   "WE" "ALB AND BIH BLR FRO GGY GIB GRL IMN ISL JEY LIE MCO MDA MKD MNE SMR SRB UKR XKX"})

(def exio-iso
  "EXIOBASE's country regions (scripts/product-graph.py ISO3)."
  {"AU" "AUS" "AT" "AUT" "BE" "BEL" "BR" "BRA" "BG" "BGR" "CA" "CAN" "CN" "CHN" "CY" "CYP" "CZ" "CZE"
   "DK" "DNK" "EE" "EST" "FI" "FIN" "FR" "FRA" "DE" "DEU" "GR" "GRC" "HU" "HUN" "HR" "HRV" "IN" "IND"
   "ID" "IDN" "IE" "IRL" "IT" "ITA" "JP" "JPN" "LV" "LVA" "LT" "LTU" "LU" "LUX" "MT" "MLT" "MX" "MEX"
   "NL" "NLD" "NO" "NOR" "PL" "POL" "PT" "PRT" "RO" "ROU" "RU" "RUS" "SK" "SVK" "SI" "SVN" "ZA" "ZAF"
   "KR" "KOR" "ES" "ESP" "SE" "SWE" "CH" "CHE" "TR" "TUR" "GB" "GBR" "US" "USA"})

(def ^:private iso->exio
  (merge (into {} (for [[r ms] row-members m (str/split ms #" ")] [m r]))
         (into {} (map (fn [[r i]] [i r])) exio-iso)))

(defn exio-region
  "The bnm.supply region (EXIOBASE's) of a country."
  [iso]
  (when-not (str/blank? iso) (get iso->exio iso "WA")))

(def refined-oil
  ["C_MGSL" "C_AGSL" "C_GJET" "C_KJET" "C_KERO" "C_DOIL" "C_FOIL" "C_RGAS" "C_LPGA" "C_REFF" "C_ETHA"
   "C_NAPT" "C_WHSP" "C_LUBR" "C_BITU" "C_PARW" "C_PETC" "C_NSPP"])

(def products
  "The bnm.supply products an asset key makes. Alumina refineries count
  with aluminium (EXIOBASE's C_ALUM holds the chain from bauxite on);
  ethylene crackers with basic plastics; a refinery with every refined oil
  product."
  {"steel" ["C_STEL"] "iron-and-steel-plant" ["C_STEL"] "ferroalloys-plant" ["C_STEL"]
   "cement" ["C_CMNT"] "cement-plant" ["C_CMNT"] "lime-plant" ["C_CMNT"]
   "ethylene" ["C_PLAS"]
   "refinery" refined-oil
   "aluminum-plant" ["C_ALUM"] "alumina-plant" ["C_ALUM"]
   "copper-plant" ["C_COPP"] "lead-plant" ["C_LZTP"] "zinc-plant" ["C_LZTP"] "tin-plant" ["C_LZTP"]
   "nickel-plant" ["C_ONFM"] "cobalt-plant" ["C_ONFM"] "magnesium-plant" ["C_ONFM"] "titanium-plant" ["C_ONFM"]
   "gold-plant" ["C_PREM"] "silver-plant" ["C_PREM"] "platinum-plant" ["C_PREM"]
   "nitrogen-plant" ["C_NFER"] "phosphate-plant" ["C_PFER"]
   "power-coal" ["C_POWC"] "power-petcoke" ["C_POWC"] "power-gas" ["C_POWG"] "power-oil" ["C_POWP"]
   "power-nuclear" ["C_POWN"] "power-hydro" ["C_POWH"] "power-wind" ["C_POWW"] "power-solar" ["C_POWS"]
   "power-biomass" ["C_POWB"] "power-waste" ["C_POWB"] "power-geothermal" ["C_POWM"]
   "power-wave-and-tidal" ["C_POWO"]
   "field" ["C_COIL" "C_GASE"]
   "mine-iron" ["C_IRON"] "mine-copper" ["C_COPO"] "mine-aluminum" ["C_ALUO"] "mine-bauxite" ["C_ALUO"]
   "mine-nickel" ["C_NIKO"] "mine-lead" ["C_LZTO"] "mine-zinc" ["C_LZTO"] "mine-tin" ["C_LZTO"]
   "mine-gold" ["C_PREO"] "mine-silver" ["C_PREO"] "mine-platinum" ["C_PREO"] "mine-uranium" ["C_ORAN"]})

(def lng-tonnes-per-mmcfd
  "Tonnes of gas a year through an LNG train of 1 mmcf/d: 1 Mt a year of
  LNG is about 133 mmcf/d of feed gas (48.7 bcf a year)."
  (/ 1.0e6 133.0))

(def gas-basis
  "Asset keys cut against the region's output of a product (bnm.supply's
  production, tonnes) rather than against the region's listed capacity of
  the key: an export terminal ships part of a region's gas, and the
  terminals listed are not the region's gas. {key [product tonnes per
  capacity unit]}."
  {"lng-export" ["C_GASE" lng-tonnes-per-mmcfd]})

(defn- in-cut? [cut {:keys [cell iso]}]
  (or (contains? cut cell) (contains? cut (cells/cell-key cell iso))))

(defn cell-cut
  "The cut a set of cells makes ({[region product] fraction}, the input
  bnm.supply/outage takes): for each bnm.supply region and product, the
  share of the region's capacity present in `year` that lies in the cut
  cells. `cut` holds cells (every country part of them) or cell x country
  keys (that part only). Capacity is the asset's (`capacity`, in one unit
  per product), else its key's median; a product whose region has no
  capacity at all is weighed by asset count. Pairs not cut are left out.
  A polygon (a field or mining area across cells) is cut by its pieces in
  the cut cells: each piece carries its own area. The `gas-basis` keys (LNG
  export terminals) cut the tonnes they ship over the region's output of
  the product, from :production {[region product] tonnes} (bnm.supply's
  production.csv); without it they cut nothing. Where both cut one product
  (gas fields and a terminal), the larger loss binds: the terminal ships
  the fields' gas, so the two losses do not add.
  Options: :year (required), :products (default `products`), :production."
  [rows cut {:keys [year production] ps :products :or {ps products}}]
  (let [cut (set cut)
        meds (medians rows)
        basis (reduce (fn [m r]
                        (let [[p per] (gas-basis (asset-key r)) reg (exio-region (:iso r))]
                          (if (and p reg (present? r year) (in-cut? cut r) (capacity r))
                            (update m [reg p] (fnil + 0.0) (* per (capacity r)))
                            m)))
                      {} rows)
        absolute (into {} (keep (fn [[k t]] (when-let [q (get production k)]
                                              (when (and (pos? q) (pos? t)) [k (min 1.0 (/ t q))]))))
                       basis)
        rs (filter #(and (ps (asset-key %)) (present? % year) (exio-region (:iso %))) rows)
        acc (reduce (fn [m r]
                      (let [w (weight meds r) c? (in-cut? cut r)]
                        (reduce (fn [m p]
                                  (update m [(exio-region (:iso r)) p]
                                          (fn [{:keys [tot hit n nhit] :or {tot 0.0 hit 0.0 n 0 nhit 0}}]
                                            {:tot (+ tot (or w 0.0)) :hit (+ hit (if c? (or w 0.0) 0.0))
                                             :n (inc n) :nhit (+ nhit (if c? 1 0))})))
                                m (ps (asset-key r)))))
                    {} rs)]
    (merge-with max
                (into {} (keep (fn [[k {:keys [tot hit n nhit]}]]
                                 (let [x (if (pos? tot) (/ hit tot) (/ (double nhit) n))]
                                   (when (pos? x) [k (min 1.0 x)]))))
                      acc)
                absolute)))

;; ------------------------------------------------------------------- grids

(defn read-grids
  "data/derived/cells/cell-grids.csv as {variable {year {:known y :values
  {cell-key value}}}}."
  [path]
  (let [vals (java.util.HashMap.) known (java.util.HashMap.) keys (java.util.HashMap.)]
    (with-open [r (io/reader path)]
      (doseq [line (rest (line-seq r))]
        (let [[c iso y v x k] (str/split line #",")
              vy [v (parse-long y)]
              ck (str iso "@" c)
              ck (or (.get keys ck) (do (.put keys ck ck) ck))]
          (when-not (.containsKey known vy) (.put known vy (parse-long k)))
          (.put vals vy (assoc! (or (.get vals vy) (transient {})) ck (parse-double x))))))
    (reduce (fn [m [[v y] t]] (assoc-in m [v y] {:known (.get known [v y]) :values (persistent! t)}))
            {} (into {} vals))))

(defn known-year
  "The latest year of `variable` known by `y` (and `back` years before it,
  default 0), or nil."
  ([g variable y] (known-year g variable y 0))
  ([g variable y back]
   (let [ys (sort > (keep (fn [[yr {:keys [known]}]] (when (<= known y) yr)) (g variable)))]
     (nth ys back nil))))

(defn known-value
  "[year value] of `variable` for part `k`, the latest year known by `y`
  (`back` known years before it): nil when none is known, the value 0.0
  when the year is known but the part has none."
  ([g variable k y] (known-value g variable k y 0))
  ([g variable k y back]
   (when-let [yr (known-year g variable y back)]
     [yr (get-in g [variable yr :values k] 0.0)])))

;; ------------------------------------------------------------ intensities

(defn- lootable [k]
  (cond
    (#{"mine-gold"} k) :lootable-gold
    (and k (or (str/includes? k "diamond") (str/includes? k "gem"))) :lootable-gems
    (= k "field") :lootable-oil))

(defn asset-counts
  "{part {measure value}} of the assets present in year `y`: counts
  (:extraction mines and fields, quarries left out; :processing; :power
  plants; :lootable-gold, -gems, -oil; :openings, dated assets starting in
  `y`), and capacity sums (:power-mw, :field-km2, :mining-km2). A polygon
  counts by its part's share. `pres` (default `present?`) says which
  assets are there."
  ([rows y] (asset-counts rows y present?))
  ([rows y pres]
  (reduce
   (fn [m {:keys [cell iso commodity share year_flag start_year asset_type] :as r}]
     (let [k (asset-key r) g (group-of k) key (cells/cell-key cell iso) s (double (or share 1.0))
           add (fn [m f x] (if (and f x) (update-in m [key f] (fnil + 0.0) x) m))]
       (cond
         (and (= asset_type "deposit") (pres r y) (str/includes? (str commodity) "diamond"))
         (add m :lootable-gems s)
         (not (and k (pres r y))) m
         :else (-> m
                   (add (if (and (= g :extraction) (quarry (str/lower-case (str commodity)))) nil
                            (when (not= k "mining-area") g)) s)
                   (add (lootable k) s)
                   (add (when (and (= year_flag "start") (= start_year y)) :openings) s)
                   (add (when (= g :power) :power-mw) (capacity r))
                   (add (when (= k "field") :field-km2) (:area_km2 r))
                   (add (when (= k "mining-area") :mining-km2) (:area_km2 r))))))
   {} rows)))

(defn land-km2
  "{part km2} of land (land and disputed parts) in each cell x country."
  [xw]
  (reduce (fn [m {:keys [cell iso share flag]}]
            (if (#{"land" "disputed"} flag)
              (update m (cells/cell-key cell iso) (fnil + 0.0) (* share (cells/area-km2 cells/resolution)))
              m))
          {} (:rows xw)))

(def features
  "The profile's measures, each log1p of an intensity (GDP per capita
  log): extraction (mines and fields per 1000 km2 of land, field and
  mining area shares), processing (plants per 1000 km2, MW per km2),
  manufacturing (industrial CO2 per person, non-residential built-up
  share), services and urban (built-up share, lights per 1000 people, GDP
  per capita, people per km2)."
  [:ext-sites :ext-field-area :ext-mining-area :proc-sites :proc-power
   :man-co2pc :man-nres :serv-built :serv-lights-pc :serv-gdppc :serv-density])

(def feature-group
  {:ext-sites :extraction :ext-field-area :extraction :ext-mining-area :extraction
   :proc-sites :processing :proc-power :processing
   :man-co2pc :manufacturing :man-nres :manufacturing
   :serv-built :services :serv-lights-pc :services :serv-gdppc :services :serv-density :services})

(def industry-co2
  ["edgar/ind-co2" "edgar/iro-co2" "edgar/nfe-co2" "edgar/nmm-co2" "edgar/che-co2"])

(def min-land-km2
  "Parts with less land are not profiled (border and coast slivers)."
  200.0)

(defn- l1p [x] (Math/log (+ 1.0 (max 0.0 (double x)))))

(defn profile
  "The feature vector of part `k` (land `land` km2) as known in year `y`,
  from grids `g` and asset counts `ac` ({measure value} of the part): a
  map of `features`, nil without people (GHSL) known."
  [g ac k land y]
  (let [v (fn [var] (second (known-value g var k y)))
        pop (v "ghsl/pop")]
    (when (and pop (pos? pop) (pos? land))
      (let [a #(get ac % 0.0)
            gdp (v "kummu/gdp") kpop (v "kummu/pop")
            co2 (reduce + 0.0 (keep v industry-co2))]
        {:ext-sites (l1p (* 1000.0 (/ (a :extraction) land)))
         :ext-field-area (l1p (* 100.0 (/ (a :field-km2) land)))
         :ext-mining-area (l1p (* 100.0 (/ (a :mining-km2) land)))
         :proc-sites (l1p (* 1000.0 (/ (a :processing) land)))
         :proc-power (l1p (/ (a :power-mw) land))
         :man-co2pc (l1p (/ co2 pop))
         :man-nres (l1p (* 100.0 (/ (or (v "ghsl/nres-km2") 0.0) land)))
         :serv-built (l1p (* 100.0 (/ (or (v "ghsl/built-km2") 0.0) land)))
         :serv-lights-pc (l1p (/ (or (v "ntl/dn") 0.0) (/ pop 1000.0)))
         :serv-gdppc (if (and gdp kpop (pos? gdp) (pos? kpop)) (Math/log (/ gdp kpop)) 0.0)
         :serv-density (l1p (/ pop land))}))))

;; ------------------------------------------------------------ the types

(defn scaler
  "Means and standard deviations of the columns of `xs`."
  [xs]
  (let [n (count xs) d (count (first xs))
        mu (mapv (fn [j] (/ (reduce + 0.0 (map #(nth % j) xs)) n)) (range d))
        sd (mapv (fn [j] (Math/sqrt (/ (reduce + 0.0 (map #(let [e (- (nth % j) (mu j))] (* e e)) xs)) n)))
                 (range d))]
    {:mu mu :sd sd}))

(defn scale [{:keys [mu sd]} x]
  (mapv (fn [j] (if (pos? (sd j)) (/ (- (nth x j) (mu j)) (sd j)) 0.0)) (range (count mu))))

(defn unscale [{:keys [mu sd]} z]
  (mapv (fn [j] (+ (mu j) (* (sd j) (nth z j)))) (range (count mu))))

(defn- d2 [a b] (loop [i 0 s 0.0] (if (= i (count a)) s (let [e (- (nth a i) (nth b i))] (recur (inc i) (+ s (* e e)))))))

(defn- nearest [cs x]
  (loop [j 0 best 0 bd Double/MAX_VALUE]
    (if (= j (count cs)) best
        (let [d (d2 (nth cs j) x)] (if (< d bd) (recur (inc j) j d) (recur (inc j) best bd))))))

(defn- lcg [seed] (let [s (volatile! (long seed))]
                    (fn [] (vswap! s #(mod (+ (* % 6364136223846793005) 1442695040888963407) 9223372036854775807))
                      (/ (double (mod @s 1000003)) 1000003.0))))

(defn kmeans
  "Lloyd's k-means on vectors `xs`: {:centroids :labels :sse}. Options
  :init (centroids to start from), else k-means++ seeded by :seed."
  [xs k {:keys [init seed iters] :or {seed 42 iters 100}}]
  (let [xs (vec xs)
        rnd (lcg seed)
        init (or init
                 (loop [cs [(nth xs (long (* (rnd) (count xs))))]]
                   (if (= (count cs) k) cs
                       (let [ds (mapv #(d2 (nth cs (nearest cs %)) %) xs)
                             tot (reduce + 0.0 ds)
                             t (* (rnd) tot)
                             i (loop [i 0 acc 0.0] (let [acc (+ acc (ds i))] (if (or (>= acc t) (= i (dec (count xs)))) i (recur (inc i) acc))))]
                         (recur (conj cs (nth xs i)))))))]
    (loop [cs (vec init) it 0 prev nil]
      (let [labels (mapv #(nearest cs %) xs)]
        (if (or (= labels prev) (= it iters))
          {:centroids cs :labels labels :sse (reduce + 0.0 (map #(d2 (nth cs %2) %1) xs labels))}
          (let [groups (group-by first (map vector labels xs))
                cs (vec (for [j (range (count cs))]
                          (if-let [g (seq (map second (groups j)))]
                            (let [n (count g)] (mapv (fn [i] (/ (reduce + 0.0 (map #(nth % i) g)) n)) (range (count (first g)))))
                            (nth cs j))))]
            (recur cs (inc it) labels)))))))

(def name-group
  "The groups a type is named by: the services measures split into urban
  form (built-up share, density) and lights and GDP per head, which part
  ways (the lit types with few people are not urban); processing and
  manufacturing named together as industrial (no manufacturing type
  separates from the plants)."
  {:ext-sites "extraction" :ext-field-area "extraction" :ext-mining-area "extraction"
   :proc-sites "industrial" :proc-power "industrial"
   :man-co2pc "industrial" :man-nres "industrial"
   :serv-built "urban" :serv-density "urban"
   :serv-gdppc "lit-sparse" :serv-lights-pc "lit-sparse"})

(defn name-types
  "A name per type from its centroid in z units (`features` order): the
  measure group with the largest mean z (`name-group`; industrial by the
  larger of its processing and manufacturing means), or sparse when none
  is above 0; repeated names numbered. lit-sparse: lights and GDP per head
  above the mean without the density or built-up of a town."
  [centroids]
  (let [gs [["extraction" #{:ext-sites :ext-field-area :ext-mining-area}]
            ["industrial" #{:proc-sites :proc-power}]
            ["industrial" #{:man-co2pc :man-nres}]
            ["urban" #{:serv-built :serv-density}]
            ["lit-sparse" #{:serv-gdppc :serv-lights-pc}]]
        base (mapv (fn [c]
                     (let [m (fn [fs] (let [js (keep-indexed (fn [j f] (when (fs f) j)) features)]
                                        (/ (reduce + 0.0 (map #(nth c %) js)) (count js))))
                           [g z] (apply max-key second (map (fn [[g fs]] [g (m fs)]) gs))]
                       (if (pos? z) g "sparse")))
                   centroids)]
    (vec (map-indexed (fn [i b] (let [n (count (filter #{b} (take i base)))] (if (pos? n) (str b "-" (inc n)) b))) base))))

(defn- permutations [xs]
  (if (empty? xs) [[]] (for [x xs p (permutations (remove #{x} xs))] (cons x p))))

(defn match-centroids
  "The order of `cur` that matches `prev` (both in the same units): the
  permutation p minimising the summed squared distance from prev[i] to
  cur[p[i]] (all k! tried: k is small)."
  [prev cur]
  (vec (apply min-key (fn [p] (reduce + 0.0 (map-indexed (fn [i j] (d2 (nth prev i) (nth cur j))) p)))
              (permutations (range (count cur))))))

(defn fit-types
  "Types fitted causally: at each year in `ys`, k-means (k types) on the
  standardised profiles of the part-years up to that year (`profiles`
  {[year part] vector}), a sample of at most :max-n of them, started from
  the previous year's centroids. The fit's centroids are then matched to
  the previous ones (`match-centroids`, in the year's z units) and put in
  their order, so a type keeps its index (and name) even if k-means swapped
  two; :moved, the z distance each centroid moved from the previous fit's,
  and :permuted, whether the match reordered them. {year {:scaler
  :centroids (raw units) :z-centroids :labels {part type} :n :moved
  :permuted}}, the labels the year's own parts' types."
  [profiles ys k {:keys [max-n seed] :or {max-n 20000 seed 42}}]
  (loop [[y & more] ys prev nil out {}]
    (if-not y out
      (let [pool (vec (sort-by key (filter (fn [[[yr _] _]] (<= yr y)) profiles)))
            step (max 1 (quot (count pool) max-n))
            sample (mapv val (take-nth step pool))
            s (scaler sample)
            zs (mapv #(scale s %) sample)
            init (some->> prev (mapv #(scale s %)))
            {:keys [centroids]} (kmeans zs k {:init init :seed seed})
            order (if init (match-centroids init centroids) (vec (range (count centroids))))
            centroids (mapv centroids order)
            raw (mapv #(unscale s %) centroids)
            labels (into {} (for [[[yr part] x] profiles :when (= yr y)] [part (nearest centroids (scale s x))]))]
        (recur more raw (assoc out y (cond-> {:scaler s :centroids raw :z-centroids centroids :labels labels
                                              :n (count pool)}
                                       init (assoc :moved (mapv #(Math/sqrt (d2 %1 %2)) init centroids)
                                                   :permuted (not= order (vec (range (count order))))))))))))

(defn classify
  "The type fit `fit` gives the raw profile vector `x`."
  [fit x]
  (nearest (:z-centroids fit) (scale (:scaler fit) x)))

(defn transition-counts
  "{[from to] n}: parts typed in both of consecutive years `ys` (types
  {[year part] type}), leaving out the steps into [year part] in
  `excluded` (data entry, the sensor join)."
  ([types ys] (transition-counts types ys #{}))
  ([types ys excluded]
   (frequencies (for [[a b] (partition 2 1 ys)
                      [[yr part] t] types :when (= yr a)
                      :let [u (types [b part])] :when (and u (not (excluded [b part])))]
                  [t u]))))

(defn without-entry
  "Presence as `present?`, but for a step from year `a`: an asset entering
  the data after `a` at its source's publication (year_flag snapshot) is
  left out, and one whose closure was reported after `a` but happened by
  `a` or at no known year is kept: the data changed, not the place. A dated
  opening, and a closure dated inside the step, are changes and count."
  [a]
  (fn [{:keys [known_from year_flag end_year end_known] :as r} y]
    (cond
      (and known_from (> known_from a) (= year_flag "snapshot")) false
      (present? r y) true
      :else (boolean (and (present? r a) end_known (> end_known a)
                          (or (nil? end_year) (<= end_year a)))))))

(defn entry-flags
  "#{[year part]}: the parts whose type in a year differs from the type
  the same fit gives their profile without the step's data entry
  (`cf` {[year part] vector}, profiles built with `without-entry`): the
  type change, if any, is the data's."
  [fits cf]
  (into #{} (keep (fn [[[y part] x]]
                    (let [fit (fits y) t (get-in fit [:labels part])]
                      (when (and fit t (not= t (classify fit x))) [y part]))))
        cf))

;; ------------------------------------------------------- the sensor join

(def last-dmsp
  "The last year of the harmonised lights from DMSP; VIIRS (simulated DMSP
  numbers) from the year after."
  2013)

(defn splice-lights
  "The lights across the DMSP-VIIRS join (Li et al.'s calibrated break:
  the world's lights fall 17% from 2013 to 2014, dim pixels zeroed by
  VIIRS). Each VIIRS year of a part is put on the DMSP scale by the part's
  ratio of its last DMSP year to its first VIIRS year (so the first VIIRS
  year equals the last DMSP one: a year's change is lost, the break is
  not), known with the first VIIRS year, so no earlier year reads it.
  The ratio is the part's when both years have at least :min-dn
  (default 100) and it lies within :ratio (default [0.2 5.0]); a part
  dim under DMSP takes its country's ratio over the parts that have one
  (else 1); a part lit under DMSP whose own ratio fails is flagged: its
  lights after the join are not comparable with before. Options
  :variable (default \"ntl/dn\"). {:grids g' :break #{part} :factors {part
  f} :country {iso f}}."
  ([g] (splice-lights g {}))
  ([g {:keys [min-dn ratio variable] :or {min-dn 100.0 ratio [0.2 5.0] variable "ntl/dn"}}]
   (let [v0 (get-in g [variable last-dmsp :values])
         v1 (get-in g [variable (inc last-dmsp) :values])]
     (if-not (and v0 v1)
       {:grids g :break #{} :factors {} :country {}}
       (let [[lo hi] ratio
             own (into {} (for [[k x0] v0 :let [x1 (get v1 k 0.0)]
                                :when (and (>= x0 min-dn) (>= x1 min-dn) (<= lo (/ x0 x1) hi))]
                            [k (/ x0 x1)]))
             break (into #{} (for [[k x0] v0 :when (and (>= x0 min-dn) (not (own k)))] k))
             country (into {} (for [[iso ks] (group-by cells/key-iso (keys own))]
                                [iso (/ (reduce + (map v0 ks)) (reduce + (map v1 ks)))]))
             f (fn [k] (or (own k) (country (cells/key-iso k)) 1.0))]
         {:grids (update g variable
                         (fn [ys] (into {} (for [[y m] ys]
                                             [y (if (> y last-dmsp)
                                                  (update m :values #(into {} (for [[k x] %] [k (* x (f k))])))
                                                  m)]))))
          :break break :factors own :country country})))))

(defn sensor-flags
  "#{[year part]}: steps of `ys` whose ends read `variable` from either
  side of the join (the known year at the start a DMSP year, at the end a
  VIIRS one) for a part in `break`."
  [g break ys variable]
  (into #{} (for [[a b] (partition 2 1 ys)
                  :let [ya (known-year g variable a) yb (known-year g variable b)]
                  :when (and ya yb (<= ya last-dmsp) (> yb last-dmsp))
                  k break]
              [b k])))

;; ---------------------------------------------------------- displacement

(defn great-circle-km [[lon1 lat1] [lon2 lat2]]
  (let [r 6371.0088 p1 (Math/toRadians lat1) p2 (Math/toRadians lat2)
        h (+ (Math/pow (Math/sin (/ (- p2 p1) 2)) 2)
             (* (Math/cos p1) (Math/cos p2) (Math/pow (Math/sin (/ (Math/toRadians (- lon2 lon1)) 2)) 2)))]
    (* 2 r (Math/asin (Math/sqrt (min 1.0 h))))))

(defn cell-displacement
  "Displacement between mine cells `mines` and processing cells `procs`
  ({cell weight}): :d = 1 - sum of the smaller cell shares (bnm.stages/
  displacement on cells), :km the mine-weighted mean distance from a mine
  cell to the nearest processing cell (centres from `centre` {cell [lon
  lat]}, default bnm.cells/centre). nil when either is empty."
  ([mines procs] (cell-displacement mines procs nil))
  ([mines procs centre]
   (let [ms (stages/shares mines) ps (stages/shares procs)
         at (fn [c] (if centre (centre c) (cells/centre c)))]
     (when (and (seq ms) (seq ps))
       (let [pcs (mapv at (keys ps))]
         {:d (stages/displacement ms ps)
          :km (reduce + 0.0 (for [[c s] ms] (* s (apply min (map #(great-circle-km (at c) %) pcs)))))
          :mine-cells (count ms) :proc-cells (count ps)})))))

(def chains
  "Materials for displacement on cells: the mine rows and the processing
  rows (asset row predicates), and each weighed (:mine by count or field
  area, :proc by capacity, else median)."
  {"iron" {:mine #(= "mine-iron" (asset-key %))
           :proc #(or (and (= "steel" (asset-key %))
                           (#{"steel" "steel_bf" "steel_bf_eaf" "steel_dri" "steel_pellet" "steel_coke" "steel_bof"
                              "steel_ohf"} (:commodity %)))
                      (= "iron-and-steel-plant" (asset-key %)))}
   "aluminium" {:mine #(#{"mine-aluminum" "mine-bauxite"} (asset-key %))
                :proc #(#{"alumina-plant" "aluminum-plant"} (asset-key %))}
   "oil" {:mine #(and (= "field" (asset-key %)) (#{"oil" "oil_gas" "petroleum"} (:commodity %)))
          :proc #(= "refinery" (asset-key %))}})

(defn chain-weights
  "{:mine {cell w} :proc {cell w} :mine-iso {iso w} :proc-iso {iso w}} of
  a chain in year `y`, the assets `pres` (default `present?`) then."
  ([rows meds chain y] (chain-weights rows meds chain y present?))
  ([rows meds {:keys [mine proc]} y pres]
  (let [rs (filter #(pres % y) rows)
        add (fn [m k w] (if (and w (pos? w)) (update m k (fnil + 0.0) w) m))
        mw (fn [r] (if (= "field" (asset-key r)) (or (:area_km2 r) (:share r)) (:share r 1.0)))]
    (reduce (fn [acc r]
              (cond
                (mine r) (-> acc (update :mine add (:cell r) (mw r)) (update :mine-iso add (:iso r) (mw r)))
                (proc r) (let [w (or (weight meds r) (:share r 1.0))]
                           (-> acc (update :proc add (:cell r) w) (update :proc-iso add (:iso r) w)))
                :else acc))
            {:mine {} :proc {} :mine-iso {} :proc-iso {}} rs))))

;; ----------------------------------------------------------- provinces

(defn to-provinces
  "Values on cell x country parts ({part v}) on provinces: each part's
  value over the country's land parts of the cell by their shares (all of
  its parts with a province when it has no land part); nil-province parts
  drop out only when the country has no province in the cell."
  [xw values]
  (reduce-kv (fn [m k v]
               (let [c (cells/key-cell k) iso (cells/key-iso k)
                     rows (filter #(and (= iso (:iso %)) (:admin1 %)) (get-in xw [:by-cell c]))
                     land (filter #(#{"land" "disputed"} (:flag %)) rows)
                     rows (if (seq land) land rows)
                     tot (reduce + 0.0 (map :share rows))]
                 (if (pos? tot)
                   (reduce (fn [m r] (update m (:admin1 r) (fnil + 0.0) (* v (/ (:share r) tot)))) m rows)
                   m)))
             {} values))
