(ns bnm.chokepoints
  "Chokepoint exposure of imports (bead bnm-1pq.10, docs/ESSENTIALS.md
  section 8 step 9): for each product, importer and year, the share of its
  imports whose sea route passes each strait.

  Routes come from scripts/routes.py (data/derived/supply/routes.csv): for
  each ordered country pair and ship class (tanker, bulk, general), whether
  the pair trades by sea (a land border or a short land route makes it
  non-maritime), the share of the pair's port-to-port weight that has a
  route (`routed`), and the share of the routed weight whose route passes
  each chokepoint. Flows are BACI HS92 (V202601), 1995-2024, by value:

    hs2-pairs.csv.gz          every HS2 chapter
    hs6-essentials.csv.gz     the sub-nodes of subnodes.csv (HS92 codes)
    hs6-energy-pairs.csv.gz   crude (270900), LNG (271111), oil products (271000),
                              iron ore (260111, 260112), coal (270111, 270112, 270119)

  For importer i, product p and data year t, with v the value of the
  flow from exporter e and r = routes[e i class(p)]:

    seaborne(e)   = v x maritime(r) x routed(r)    maritime: the product's mode share, else the pair flag
    through_c(e)  = seaborne(e) x share_c(r)
    chokepoint/<c>-<p>        = sum_e through_c(e) / sum_e v
    chokepoint/seaborne-<p>   = sum_e seaborne(e) / sum_e v

  so a strait's share is of all the importer's imports of the product
  (land and pipeline included), never above the seaborne share. WLD is the
  same over all importers (the world's trade). The product `all` is the sum
  of the HS2 chapters. Written by the year known: BACI's data year t is
  first published in January t+2 (scripts/baci.py), so a row's year is t+2
  and nothing of a later data year enters it.

  Structure. Tanker routes (crude, LNG, oil products, HS chapter 27) are
  per data year (routes-years.csv): export ports by that year's PortWatch
  tanker tonnes and a gravity model on that year's buyers. PortWatch starts
  in 2019, so data years 1995-2018 use 2019's ports: a look-ahead in the
  port structure for exposure years 1997-2020 (US crude loaded at Corpus
  Christi before its export terminals existed; Yanbu's share fixed at
  2019's). Bulk and general routes are fixed across years: the network
  (c.2000 lanes), their port weights (PortWatch, AIS 2019-) and the land
  borders (current) apply to every year, a look-ahead in the route
  structure, not in the flows. A product's mode can override its pair's
  land flag (routes-modes.csv): LNG between neighbours, Russian crude to
  China beyond the pipelines.

  Value, not tonnes: BACI's value is complete where its tonnes are not
  (about 1% of value has none). For one HS6 product the two shares differ
  only by unit-value differences across suppliers."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def lag
  "Years from BACI's data year to the year it is known."
  2)

(def straits
  "The chokepoints scripts/routes.py resolves, with PortWatch's id."
  {"suez" "chokepoint1" "panama" "chokepoint2" "bosporus" "chokepoint3"
   "bab-el-mandeb" "chokepoint4" "malacca" "chokepoint5" "hormuz" "chokepoint6"
   "cape-of-good-hope" "chokepoint7" "gibraltar" "chokepoint8" "dover" "chokepoint9"
   "oresund" "chokepoint10" "taiwan" "chokepoint11" "korea" "chokepoint12"
   "tsugaru" "chokepoint13" "luzon" "chokepoint14" "lombok" "chokepoint15"
   "sunda" "chokepoint19" "makassar" "chokepoint20" "magellan" "chokepoint21"
   "bering" "chokepoint26" "kerch" "chokepoint28"})

;; ---------------------------------------------------------------- products and ship classes

(def ^:private tanker-hs2 #{"27"})
(def ^:private bulk-hs2 #{"10" "12" "23" "25" "26" "31" "44" "47"})

(defn hs2-class
  "The ship class of an HS2 chapter: tanker (mineral fuels), bulk (cereals,
  oilseeds, feed, salt and stone, ores, fertilisers, wood, pulp) or general
  (container, general cargo, ro-ro: everything else)."
  [hs2]
  (cond (tanker-hs2 hs2) "tanker" (bulk-hs2 hs2) "bulk" :else "general"))

(defn hs6-class
  "The ship class of an HS6 code: crude its own (VLCC lanes on long hauls,
  scripts/routes.py VESSELS); oil products and LNG tankers; iron ore
  (2601 11, 12) and coal (2701) capesize (no Panama on long hauls); other
  ores, graphite and minerals of chapters 25-26 bulk; the rest as its
  chapter (chemicals, metals and goods general). HS2 chapters keep their
  chapter class (hs2-class): chapter 26 and 27 flows are not split."
  [hs6]
  (cond (re-find #"^2709" hs6) "crude"
        (re-find #"^(2710|2711)" hs6) "tanker"
        (re-find #"^(26011|2701)" hs6) "capesize"
        (re-find #"^(25|26)" hs6) "bulk"
        :else (let [c (hs2-class (subs hs6 0 2))] (if (= c "tanker") "general" c))))

(def energy-products
  "The products of hs6-energy-pairs.csv.gz: crude, LNG, oil products, and the
  Capesize cargoes iron ore and coal."
  {"270900" "crude-oil" "271111" "lng" "271000" "oil-products"
   "260111" "iron-ore" "260112" "iron-ore"
   "270111" "coal" "270112" "coal" "270119" "coal"})

(defn product-name
  "Stock-name form of a sub-node name: lower case, hyphens."
  [s]
  (str/replace (str/lower-case s) #"[_\s]+" "-"))

;; ---------------------------------------------------------------- routes

(defn parse-chokepoints
  "{strait share} from routes.csv's \"name=share;...\"."
  [s]
  (if (str/blank? s)
    {}
    (into {} (for [kv (str/split s #";") :let [[k v] (str/split kv #"=")]] [k (parse-double v)]))))

(defn- route-row
  [g]
  {:maritime? (= "1" (g "maritime"))
   :reason (g "reason")
   :routed (or (parse-double (g "routed")) 0.0)
   :km (parse-double (g "distance_km"))
   :through (parse-chokepoints (g "chokepoints"))})

(defn- read-csv
  "(f acc g) over a CSV's rows, g the column getter."
  [path f]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (reduce (fn [acc l] (let [x (str/split l #"," -1)] (f acc #(nth x (ix %) ""))))
              {} ls))))

(defn read-routes
  "{exporter {importer {class {:maritime? :routed :km :through {strait share}}}}}
  of routes.csv (tanker rows: the 2019-23 pooled structure)."
  [path]
  (read-csv path (fn [acc g] (assoc-in acc [(g "exporter") (g "importer") (g "class")] (route-row g)))))

(defn read-year-routes
  "{data-year {exporter {importer {class route}}}} of routes-years.csv: the
  tanker routes of each BACI data year, export ports by that year's PortWatch
  tonnes (2019's for 1995-2018) and the year's buyers."
  [path]
  (read-csv path (fn [acc g] (assoc-in acc [(parse-long (g "year")) (g "exporter") (g "importer") (g "class")]
                                       (route-row g)))))

(defn read-modes
  "{[exporter importer product data-year] seaborne share} of routes-modes.csv:
  where a product's mode differs from its pair's land flag (LNG between
  neighbours, Russian crude to China beyond the pipelines)."
  [path]
  (read-csv path (fn [acc g] (assoc acc [(g "exporter") (g "importer") (g "product") (parse-long (g "year"))]
                                    (parse-double (g "maritime"))))))

(defn with-structure
  "routes with the year routes and the modes attached (see route-of)."
  [routes years modes]
  (assoc routes ::years years ::modes modes))

(defn route-of
  "The route of a flow {:year :exporter :importer :class :product}: the
  year's own route where routes-years.csv has one, else the pooled one; a
  product mode sets :maritime-share."
  [routes {:keys [year exporter importer class product]}]
  (let [r (or (get-in routes [::years year exporter importer class])
              (get-in routes [exporter importer class]))
        m (get-in routes [::modes [exporter importer product year]])]
    (cond-> r (and r m) (assoc :maritime-share m))))

(defn seaborne-share
  "The share of a pair's flow that goes by sea on a routed lane: the
  product's mode share or the pair flag (0 or 1), times routed."
  [route]
  (if route
    (* (or (:maritime-share route) (if (:maritime? route) 1.0 0.0)) (:routed route))
    0.0))

;; ---------------------------------------------------------------- aggregation

(defn add-flow
  "Adds one flow (value v from exporter e) into an importer's accumulator
  {:total :sea :tonnes :sea-tonnes :through {strait value} :through-tonnes {strait t}}."
  [acc route v t]
  (let [s (seaborne-share route)
        sv (* v s)
        st (when t (* t s))
        acc (-> acc
                (update :total (fnil + 0.0) v)
                (update :sea (fnil + 0.0) sv))
        acc (if t (-> acc (update :tonnes (fnil + 0.0) t) (update :sea-tonnes (fnil + 0.0) st)) acc)]
    (if (pos? s)
      (reduce-kv (fn [a c x]
                   (cond-> (update-in a [:through c] (fnil + 0.0) (* sv x))
                     t (update-in [:through-tonnes c] (fnil + 0.0) (* st x))))
                 acc (:through route))
      acc)))

(defn add-to
  "Adds one flow {:product :class :exporter :importer :value :tonnes} to an
  aggregate {[product importer] accumulator}, and to the world (\"WLD\")."
  [routes m {:keys [year product class exporter importer value tonnes]}]
  (let [r (route-of routes {:year year :exporter exporter :importer importer :class class :product product})]
    (-> m
        (update [product importer] add-flow r value tonnes)
        (update [product "WLD"] add-flow r value tonnes))))

(defn aggregate
  "{[product importer] accumulator} of one data year's flows."
  [routes flows]
  (reduce (partial add-to routes) {} flows))

(defn shares
  "{:seaborne s strait share} of an accumulator, of its total value."
  [{:keys [total sea through]}]
  (when (and total (pos? total))
    (into {:seaborne (/ sea total)}
          (for [[c x] through] [c (/ x total)]))))

(defn rows
  "Stock rows {:iso :year :variable :value} of one data year's aggregate:
  year = data year + lag; strait shares below `floor` are left out (an
  absent strait is zero), the seaborne share is always written."
  ([agg data-year] (rows agg data-year 0.0))
  ([agg data-year floor]
   (for [[[product iso] acc] (sort-by key agg)
         :let [sh (shares acc)]
         :when sh
         [k x] (sort-by (comp str key) sh)
         :when (or (= k :seaborne) (>= x floor))]
     {:iso iso :year (+ data-year lag)
      :variable (str "chokepoint/" (if (= k :seaborne) "seaborne" k) "-" product)
      :value x})))

;; ---------------------------------------------------------------- reading flows

(defn gz-lines
  "(f lines) over a gzip CSV's lines, header dropped."
  [path f]
  (with-open [r (io/reader (java.util.zip.GZIPInputStream. (io/input-stream path)))]
    (f (rest (line-seq r)))))

(defn read-subnodes
  "{hs6 sub-node} of the HS92 rows of subnodes.csv."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (into {} (for [l ls :let [f (str/split l #"," -1) g #(nth f (ix %) "")]
                     :when (= "HS92" (g "hs_edition"))]
                 [(g "hs6") (product-name (g "subnode"))])))))

(defn hs2-flow
  "A flow of an hs2-pairs line (HS92 only), or nil."
  [l]
  (when (str/starts-with? l "HS92,")
    (let [[_ y _ hs2 e i v t] (str/split l #"," -1)]
      {:year (parse-long y) :product (str "hs" hs2) :class (hs2-class hs2)
       :exporter e :importer i :value (parse-double v) :tonnes (when (seq t) (parse-double t))})))

(defn hs6-flow
  "A flow of an hs6 bilateral line (HS92 only) whose code is in `products`
  ({hs6 product}), or nil."
  [products l]
  (when (str/starts-with? l "HS92,")
    (let [[_ y _ hs6 e i v t] (str/split l #"," -1)]
      (when-let [p (products hs6)]
        {:year (parse-long y) :product p :class (hs6-class hs6)
         :exporter e :importer i :value (parse-double v) :tonnes (when (seq t) (parse-double t))}))))

(defn by-year
  "Calls (f year aggregate) for each data year of a line seq, in order,
  aggregating as it reads and holding one year at a time (the files are
  sorted by edition and year). parse: line -> flow or nil."
  [routes lines parse f]
  (loop [ls lines y nil agg {}]
    (if-let [l (first ls)]
      (if-let [x (parse l)]
        (if (= (:year x) y)
          (recur (rest ls) y (add-to routes agg x))
          (do (when y (f y agg)) (recur (rest ls) (:year x) (add-to routes {} x))))
        (recur (rest ls) y agg))
      (when y (f y agg)))))

(defn all-product
  "The `all` accumulators of a year: the HS2 chapters summed per importer."
  [agg]
  (reduce-kv (fn [m [product iso] acc]
               (if (str/starts-with? product "hs")
                 (update m ["all" iso]
                         (fn [a] (merge-with (fn [x y] (if (map? x) (merge-with + x y) (+ x y))) a acc)))
                 m))
             {} agg))

;; ---------------------------------------------------------------- output

(defn write-rows!
  "Writes rows as iso,year,variable,value lines to an open writer."
  [w rows]
  (doseq [{:keys [iso year variable value]} rows]
    (.write w (str iso "," year "," variable "," (format "%.4g" (double value)) "\n"))))

(defn write-csv
  "iso,year,variable,value (the year known)."
  [path rows]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value\n")
    (write-rows! w rows)))
