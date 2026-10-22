(ns bnm.econ-structure
  "Finance and manufacturing value-added shares per country-year, the I-O
  spine spliced to the national accounts (bead bnm-3ka.1, Econ 0;
  docs/STOCK-FLOW.md \"Finance rows\").

  Input: scripts/econ-structure.py's rows {:iso :year :source :fin :man},
  shares of total value added from the spine's segments (lrwiod to 1999
  and in 2000 for the join, wiod16 2000-2014, icio2023 2015-2020) and the
  benchmarks (bea, oecd, euklems).

  Per country, variable and data year:
    - a benchmark year takes the benchmark (BEA, then OECD, then EU KLEMS),
      known `lags` years on;
    - a spine year without one takes the spine share times the ratio of
      benchmark to that spine segment over the overlap years nearest it
      (at most `nearest`), using only the overlap years published by the
      value's known year: the spine's own lag, or later, the year the first
      overlap year is published (both the benchmark's and the spine's for
      that year);
    - a segment with no overlap is chained to one with: its ratio times
      the other segment's share over its own in a year both cover (LR-WIOD
      and WIOD 2016 in 2000);
    - with no benchmark at all, the spine share as it is.
  :source says which: the benchmark, \"<segment>*<benchmark>\" for a
  spliced value, or the segment.

  The benchmarks are current vintages (BEA's historical NAICS series is the
  2021 vintage, its current release 2026, OECD's 2026): the lag dates a
  value, it does not undo the revisions since.")

(def lags {"bea" 1 "oecd" 2 "euklems" 2 "lrwiod" 3 "wiod16" 3 "icio2023" 3})
(def benchmarks ["bea" "oecd" "euklems"])
(def segments ["lrwiod" "wiod16" "icio2023"])
(def variables {:fin "econ/finance-va-share" :man "econ/manufacturing-va-share"})
(def nearest 5)

(defn- positive [x] (when (and x (pos? (double x))) (double x)))

(defn- country-series
  "{:bench {year [source value]} :seg {segment {year value}}} for one
  country and variable k."
  [rows k]
  (let [by (reduce (fn [m {:keys [source year] :as r}]
                     (if-let [v (positive (get r k))] (assoc-in m [source year] v) m))
                   {} rows)]
    {:bench (reduce (fn [m b] (reduce-kv (fn [m y v] (if (contains? m y) m (assoc m y [b v]))) m (get by b)))
                    {} benchmarks)
     :seg (select-keys by segments)}))

(defn- points
  "Overlap points of segment g: [{:year :num :den :pub}], the benchmark
  and segment shares and the year both are published."
  [{:keys [bench seg]} g]
  (for [[y s] (get seg g) :let [[b v] (get bench y)] :when v]
    {:year y :num v :den s :pub (+ y (max (lags b) (lags g)))}))

(defn- ratio-at
  "The ratio of benchmark to segment from `pts` near data year y, at known
  year k0 or later: [ratio known] or nil."
  [pts y k0]
  (when (seq pts)
    (let [k (max k0 (apply min (map :pub pts)))
          use (->> pts (filter #(<= (:pub %) k)) (sort-by #(Math/abs (long (- (:year %) y)))) (take nearest))]
      [(/ (reduce + (map :num use)) (reduce + (map :den use))) k])))

(defn- segment-ratio
  "[ratio known bench] for segment g at data year y, chained through a
  neighbouring segment where g has no overlap; nil where neither has."
  [cs g y]
  (let [k0 (+ y (lags g))
        bench-of (fn [pts] (first (get-in cs [:bench (:year (first pts))])))
        own (points cs g)]
    (if (seq own)
      (conj (ratio-at own y k0) (bench-of own))
      (first
       (for [h (sort-by #(Math/abs (long (- (.indexOf ^java.util.List segments %) (.indexOf ^java.util.List segments g)))) segments)
             :when (not= h g)
             :let [pts (points cs h)
                   sg (get-in cs [:seg g]) sh (get-in cs [:seg h])
                   join (first (sort-by #(Math/abs (long (- % y))) (filter #(contains? sh %) (keys sg))))]
             :when (and (seq pts) join)
             :let [[r k] (ratio-at pts y (max k0 (+ join (lags h))))]]
         [(* r (/ (get sh join) (get sg join))) k (bench-of pts)])))))

(defn- spine-at
  "[segment share] of the latest segment covering year y."
  [cs y]
  (first (for [g (reverse segments) :let [v (get-in cs [:seg g y])] :when v] [g v])))

(defn series
  "Rows {:iso :year :variable :value :known :source}, one per country,
  data year and variable."
  [rows]
  (for [[iso rs] (sort-by key (group-by :iso rows))
        [k variable] (sort-by val variables)
        :let [cs (country-series rs k)
              years (sort (into (set (keys (:bench cs))) (mapcat keys (vals (:seg cs)))))]
        y years
        :let [[b v] (get-in cs [:bench y])
              [g s] (spine-at cs y)
              row (cond
                    v {:value v :known (+ y (lags b)) :source b}
                    g (if-let [[r k bb] (segment-ratio cs g y)]
                        {:value (* s r) :known k :source (str g "*" bb)}
                        {:value s :known (+ y (lags g)) :source g}))]
        :when row]
    (assoc row :iso iso :year y :variable variable)))
