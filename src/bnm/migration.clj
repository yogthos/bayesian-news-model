(ns bnm.migration
  "Production migration by capital intensity, per country (bead
  bnm-3ka.2; docs/ECONOMIC-CHARACTER.md 8b, H1-H2).

  Input: the EXIOBASE-product panel of scripts/migration.py
  (data/derived/econ/migration.csv), rows {:product :year :known :exporter
  :value :cap-intensity :cap-class}: BACI HS92 export values spread over
  EXIOBASE products by the concordance, known two years after the data
  year. cap-intensity is the product's consumption of fixed capital over
  value added in EXIOBASE's 1995 table, summed over its 49 regions; the
  class is its tercile among the traded products (low, mid, high; high is
  capital-intensive). The intensity is a fixed classification taken from a
  2021 vintage of the 1995 table: a product attribute that the migration
  after 1995 does not cause, but not a value anyone held in 1997.

  Per country and data year (country-years):
    :ci-share       its share of world exports of the high-class products
    :export-share   its share of world exports of all mapped goods
    :ci-rca         ci-share / export-share (above 1: specialised in
                    capital-intensive goods)
    :cap-intensity  the export-value-weighted capital intensity of its
                    exports (the same at HS6 or EXIOBASE level, since an HS6
                    code's intensity is the weighted mean of its products')

  stock-rows writes them by the year known (iso,year,variable,value), each
  year Y reading the latest data year known by Y (at most `stale` years
  old), with the changes over 5 and 10 data years:
    migration/ci-share, migration/ci-share-d5, migration/ci-share-d10,
    migration/export-share, migration/ci-rca, migration/cap-intensity,
    migration/cap-intensity-d10.")

(defn classify
  "The class of intensity `x` under tercile cuts [lo hi]: low (at most lo),
  mid (at most hi) or high; nil where x is unknown. scripts/migration.py
  applies the same rule."
  [x [lo hi]]
  (when x
    (cond (<= x lo) "low" (<= x hi) "mid" :else "high")))

(defn tercile-cuts
  "The 1/3 and 2/3 quantiles of `xs` (linear interpolation, numpy's and
  pandas' default)."
  [xs]
  (let [v (vec (sort xs)) n (count v)
        q (fn [p] (let [h (* p (dec n)) i (long (Math/floor h)) f (- h i)]
                    (if (>= (inc i) n) (v i) (+ (v i) (* f (- (v (inc i)) (v i)))))))]
    [(q (/ 1.0 3)) (q (/ 2.0 3))]))

(defn world-shares
  "`rows` with :share, each exporter's share of the product-year's world
  exports."
  [rows]
  (let [tot (reduce (fn [m {:keys [product year value]}] (update m [product year] (fnil + 0.0) value)) {} rows)]
    (mapv #(assoc % :share (/ (:value %) (tot [(:product %) (:year %)]))) rows)))

(defn- acc [m k v] (assoc! m k (+ (double (get m k 0.0)) (double v))))

(defn country-years
  "{[iso data-year] {:known :ci-share :export-share :ci-rca :cap-intensity}}
  from the panel `rows` (any seq; read once)."
  [rows]
  (let [{:keys [whi wtot chi ctot kv kw known]}
        (reduce (fn [a {:keys [year known exporter value cap-intensity cap-class]}]
                  (let [k [exporter year] hi? (= "high" cap-class)]
                    (cond-> (-> a
                                (update :wtot acc year value)
                                (update :ctot acc k value)
                                (update :known assoc! k known))
                      hi? (-> (update :whi acc year value) (update :chi acc k value))
                      cap-intensity (-> (update :kv acc k (* value cap-intensity)) (update :kw acc k value)))))
                (zipmap [:whi :wtot :chi :ctot :kv :kw :known] (repeatedly #(transient {})))
                rows)
        [whi wtot chi ctot kv kw known] (map persistent! [whi wtot chi ctot kv kw known])]
    (into {}
          (for [[[_ y :as k] tot] ctot
                :let [ci (when (pos? (get whi y 0.0)) (/ (get chi k 0.0) (whi y)))
                      ex (/ tot (wtot y))]]
            [k {:known (known k) :ci-share ci :export-share ex
                :ci-rca (when (and ci (pos? ex)) (/ ci ex))
                :cap-intensity (when (pos? (get kw k 0.0)) (/ (kv k) (kw k)))}]))))

(defn- row [iso year variable value]
  (when (and value (< (Math/abs (double value)) Double/POSITIVE_INFINITY))
    {:iso iso :year year :variable (str "migration/" variable) :value (double value)}))

(defn- diff [a b] (when (and a b) (- a b)))

(defn stock-rows
  "Rows {:iso :year :variable :value} for each year in `years` (the year
  known), from the panel `rows` or from country-years already computed
  (pass {:aggregates a}). A year reads the latest data year whose :known is
  at most it and which is at most `stale` years older than it (default 4: two
  years past the usual lag)."
  ([rows years] (stock-rows rows years 4))
  ([rows years stale]
   (let [a (if (map? rows) (:aggregates rows) (country-years rows))
         by-iso (group-by ffirst a)]
     (->> (for [[iso entries] by-iso
                :let [m (into {} (map (fn [[[_ t] v]] [t v])) entries)
                      ts (sort (keys m))]
                y years
                :let [t (last (filter #(and (<= (:known (m %)) y) (<= (- y %) stale)) ts))]
                :when t
                :let [c (m t) c5 (m (- t 5)) c10 (m (- t 10))]
                r [(row iso y "ci-share" (:ci-share c))
                   (row iso y "ci-share-d5" (diff (:ci-share c) (:ci-share c5)))
                   (row iso y "ci-share-d10" (diff (:ci-share c) (:ci-share c10)))
                   (row iso y "export-share" (:export-share c))
                   (row iso y "ci-rca" (:ci-rca c))
                   (row iso y "cap-intensity" (:cap-intensity c))
                   (row iso y "cap-intensity-d10" (diff (:cap-intensity c) (:cap-intensity c10)))]
                :when r]
            r)
          (sort-by (juxt :iso :year :variable))
          vec))))
