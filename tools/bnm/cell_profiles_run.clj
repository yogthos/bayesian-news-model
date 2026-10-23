(ns bnm.cell-profiles-run
  "Cell profiles, development paths, displacement and concentration on A5
  cells (beads bnm-3ka.6, bnm-1pq.11; bnm.cell-profiles). Reads
  data/derived/cells/{crosswalk,assets,cell-grids}.csv,
  data/derived/stocks/{econ-factors,stages}.csv. Writes

    data/derived/stocks/cells.csv        cells/<key>-hhi, -top-share (top
                                         cell in top), -cells; WLD and per
                                         country, 1990-2026
    data/derived/cells/profiles.csv      part,year,type,<features>: each
                                         part's profile as known in the
                                         year and its type
    data/derived/cells/features.csv      unit_kind,unit,iso,year,<features>:
                                         per cell and per province, for the
                                         cells-vs-provinces rerun (bead
                                         bnm-3ka.11), each from data known
                                         by the year before
    data/derived/cells/profiles-report.edn  types, transitions, correlates,
                                         displacement, top cells
    data/derived/cells/cut-ras-laffan.edn   with the argument cut: the Ras
                                         Laffan cell cut (2011 assets)
                                         through bnm.supply/outage

    python -I scripts/cell-grids.py
    jolt -A:tools -M -m bnm.cell-profiles-run [cut]"
  (:require [bnm.cell-profiles :as cp]
            [bnm.cells :as cells]
            [bnm.supply :as sup]
            [bnm.stages :as stages]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(def dir "data/derived/cells")
(def profile-years [1995 2000 2005 2010 2015 2020 2025])
(def k-types 5)

(defn- log [& xs] (apply println xs) (flush))

(defn- read-stock
  "{iso {year value}} of one variable of a stocks CSV."
  [path variable]
  (with-open [r (io/reader path)]
    (reduce (fn [m l]
              (let [[iso y v x] (str/split l #",")]
                (if (= v variable) (assoc-in m [iso (parse-long y)] (parse-double x)) m)))
            {} (rest (line-seq r)))))

(defn- as-of [series y]
  (some->> series (filter (fn [[yr _]] (<= yr y))) seq (apply max-key key) val))

(defn- round [x d] (when x (let [p (Math/pow 10 d)] (/ (Math/round (* p (double x))) p))))

(defn- fmt [x] (cond (nil? x) "" (string? x) x (integer? x) (str x) :else (str (round x 6))))

(defn- write-csv [path header rows]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w (str (str/join "," header) "\n"))
    (doseq [r rows] (.write w (str (str/join "," (map fmt r)) "\n")))))

;; ------------------------------------------------------------------ profiles

(defn profiles
  ([g assets land pres] (profiles g assets land pres profile-years))
  ([g assets land pres ys]
   (into {} (for [y ys
                  :let [ac (cp/asset-counts assets y pres)]
                  [k a] land :when (>= a cp/min-land-km2)
                  :let [p (cp/profile g (get ac k {}) k a y)] :when p]
              [[y k] (mapv p cp/features)]))))

(defn entry-flags
  "#{[year part]}: the steps whose end type is the data entry's
  (cp/entry-flags on profiles built with cp/without-entry for each step)."
  [g assets land fits]
  (into #{} (mapcat (fn [[a b]] (cp/entry-flags fits (profiles g assets land (cp/without-entry a) [b])))
                    (partition 2 1 profile-years))))

(def ^:private cg-cache (atom {}))
(defn country-growth-memo
  "Country light growth between the years known at `b` and at `a`."
  [g var b a]
  (or (@cg-cache [var b a])
      (let [y1 (cp/known-year g var b) y0 (cp/known-year g var a)
            s (fn [yr] (reduce-kv (fn [m k v] (update m (cells/key-iso k) (fnil + 0.0) v)) {}
                                  (get-in g [var yr :values])))
            r (if (and y1 y0)
                (let [x0 (s y0) x1 (s y1)]
                  (into {} (for [[iso x] x1 :let [z (x0 iso)] :when (and z (pos? z) (pos? x))] [iso (Math/log (/ x z))])))
                {})]
        (swap! cg-cache assoc [var b a] r)
        r)))

(defn part-growth-years [g var k a b]
  (let [[_ x1] (cp/known-value g var k b) [_ x0] (cp/known-value g var k a)]
    (when (and x0 x1) (Math/log (/ (+ 1.0 x1) (+ 1.0 x0))))))

(defn report-types [fits ys]
  (let [last-fit (fits (last ys))
        names (cp/name-types (:z-centroids last-fit))]
    {:names names
     :centroids-z (into {} (map-indexed (fn [i c] [(names i) (zipmap cp/features (map #(round % 2) c))])
                                        (:z-centroids last-fit)))
     :centroids-raw (into {} (map-indexed (fn [i c] [(names i) (zipmap cp/features (map #(round % 3) c))])
                                          (:centroids last-fit)))
     :fit-n (into (sorted-map) (for [y ys] [y (:n (fits y))]))
     :moved (into (sorted-map) (for [y ys :when (:moved (fits y))]
                                 [y {:z (zipmap names (map #(round % 3) (:moved (fits y)))) :permuted (:permuted (fits y))}]))
     :counts (into (sorted-map) (for [y ys] [y (into (sorted-map) (frequencies (map names (vals (:labels (fits y))))))]))}))

(defn- matrix-of [names counts]
  (into (sorted-map)
        (for [[from cs] (group-by ffirst counts)
              :let [tot (reduce + (map val cs))]]
          [(names from) (into (sorted-map) (for [[[_ to] n] cs] [(names to) {:n n :share (round (/ n (double tot)) 3)}]))])))

(defn transitions-report
  "Transitions between consecutive profile years, the steps in `excluded`
  ({:entry #{[year part]} :sensor #{...}}) left out of the matrix and the
  correlates; :matrix-all keeps them, :excluded counts them per step."
  [g fits names ys regime excluded]
  (let [types (into {} (for [y ys [k t] (:labels (fits y))] [[y k] t]))
        ex (into #{} cat (vals excluded))
        prev (into {} (map (fn [[a b]] [b a]) (partition 2 1 ys)))
        stepped (fn [s] (filter (fn [[b k]] (and (types [b k]) (prev b) (types [(prev b) k]))) s))
        changed? (fn [[b k]] (not= (types [b k]) (types [(prev b) k])))
        counts (cp/transition-counts types ys ex)
        matrix (matrix-of names counts)
        matrix-all (update-vals (matrix-of names (cp/transition-counts types ys)) #(update-vals % :n))
        ;; correlates: the country's investment regime at the start, the
        ;; part's light growth against its country's over the five years
        obs (for [[a b] (partition 2 1 ys)
                  [[yr k] t] types :when (= yr a)
                  :let [u (types [b k])] :when (and u (not (ex [b k])))
                  :let [iso (cells/key-iso k)
                        cg (get (country-growth-memo g "ntl/dn" b a) iso)
                        pg (part-growth-years g "ntl/dn" k a b)]]
              {:from (names t) :to (names u) :regime (as-of (regime iso) a)
               :light-within (when (and cg pg) (- pg cg))})
        mean (fn [xs] (let [xs (remove nil? xs)] (when (seq xs) (round (/ (reduce + xs) (count xs)) 3))))]
    {:matrix matrix
     :matrix-all matrix-all
     :excluded (into (sorted-map)
                     (for [[kind s] excluded]
                       [kind (into (sorted-map)
                                   (for [[b fs] (group-by first (stepped s))]
                                     [b {:steps (count fs) :type-changed (count (filter changed? fs))}]))]))
     :changes {:all (reduce + (for [[[f t] n] (cp/transition-counts types ys) :when (not= f t)] n))
               :kept (reduce + (for [[[f t] n] counts :when (not= f t)] n))}
     :correlates (into (sorted-map)
                       (for [[[f t] os] (group-by (juxt :from :to) obs)]
                         [(str f " -> " t) {:n (count os) :investment-regime (mean (map :regime os))
                                            :light-growth-within (mean (map :light-within os))}]))}))

;; -------------------------------------------------------------- displacement

(defn displacement-report [assets stage-d]
  (let [meds (cp/medians assets)]
    (into (sorted-map)
          (for [[c ch] cp/chains [mode pres] [[:causal cp/present?] [:backcast cp/present-backcast?]]]
            [[c mode] (into (sorted-map)
                     (for [y (conj (vec (range 1990 2026 5)) 2026)
                           :let [{:keys [mine proc mine-iso proc-iso]} (cp/chain-weights assets meds ch y pres)
                                 r (cp/cell-displacement mine proc)]
                           :when r]
                       [y (assoc (update-vals (select-keys r [:d :km]) #(round % 3))
                                 :mine-cells (:mine-cells r) :proc-cells (:proc-cells r)
                                 :d-country-assets (round (stages/displacement mine-iso proc-iso) 3)
                                 :d-stages (round (as-of (get-in stage-d [c "WLD"]) y) 3))]))]))))

;; ------------------------------------------------------------------ features

(def feature-cols
  [:light-growth-within :gdppc-growth-within :extraction-within :mining-km2 :lootable-gold :lootable-gems
   :lootable-oil :processing-sites :openings])

(defn- sum-by [f m] (reduce-kv (fn [acc k v] (if-let [u (f k)] (update acc u (fnil + 0.0) v) acc)) {} m))

(defn unit-features
  "Rows [kind unit iso year & features] for year `y` from data known by y-1."
  [g assets xw main-iso y]
  (let [z (dec y)
        ac (cp/asset-counts assets z)
        q (fn [var back] (when (cp/known-year g var z back)
                           (get-in g [var (cp/known-year g var z back) :values])))
        base {:ntl1 (q "ntl/dn" 0) :ntl0 (q "ntl/dn" 1) :gdp1 (q "kummu/gdp" 0) :gdp0 (q "kummu/gdp" 1)
              :pop1 (q "kummu/pop" 0) :pop0 (q "kummu/pop" 1)}
        counts (fn [f] (into {} (keep (fn [[k m]] (when-let [v (get m f)] [k v]))) ac))
        parts {:ntl1 (:ntl1 base) :ntl0 (:ntl0 base) :gdp1 (:gdp1 base) :gdp0 (:gdp0 base)
               :pop1 (:pop1 base) :pop0 (:pop0 base)
               :ext (counts :extraction) :mining-km2 (counts :mining-km2) :lootable-gold (counts :lootable-gold)
               :lootable-gems (counts :lootable-gems) :lootable-oil (counts :lootable-oil)
               :processing-sites (counts :processing) :openings (counts :openings)}
        country (update-vals parts #(sum-by cells/key-iso (or % {})))
        growth (fn [m u a b] (let [x0 (get-in m [a u]) x1 (get-in m [b u])]
                               (when (and x0 x1 (pos? x0) (pos? x1)) (Math/log (/ x1 x0)))))
        gpc (fn [m u] (let [g1 (get-in m [:gdp1 u]) g0 (get-in m [:gdp0 u]) p1 (get-in m [:pop1 u]) p0 (get-in m [:pop0 u])]
                        (when (and g1 g0 p1 p0 (every? pos? [g1 g0 p1 p0])) (Math/log (/ (/ g1 p1) (/ g0 p0))))))
        rows (fn [kind agg iso-of]
               (let [m (update-vals parts #(agg (or % {})))
                     units (into #{} (mapcat keys) (vals (select-keys m [:ntl1 :ext :processing-sites :lootable-oil])))
                     ext (into {} (for [u units] [u (Math/log (+ 1.0 (get-in m [:ext u] 0.0)))]))
                     ext-mean (update-vals (group-by (comp iso-of key) ext) #(/ (reduce + (map val %)) (count %)))]
                 (for [u (sort units) :let [iso (iso-of u)] :when iso]
                   (let [lg (growth m u :ntl0 :ntl1) cl (growth country iso :ntl0 :ntl1)
                         gg (gpc m u) cg (gpc country iso)]
                     (into [kind u iso y]
                           [(when (and lg cl) (- lg cl)) (when (and gg cg) (- gg cg))
                            (- (ext u) (ext-mean iso 0.0))
                            (get-in m [:mining-km2 u]) (get-in m [:lootable-gold u]) (get-in m [:lootable-gems u])
                            (get-in m [:lootable-oil u]) (get-in m [:processing-sites u]) (get-in m [:openings u])])))))]
    (concat (rows "cell" #(sum-by cells/key-cell %) main-iso)
            (rows "province" #(cp/to-provinces xw %) (fn [a1] (when a1 (first (str/split a1 #"\."))))))))

;; ----------------------------------------------------------------- a cell cut

(defn- read-production
  "{[region product] tonnes} of data/derived/supply/production.csv."
  []
  (with-open [r (io/reader "data/derived/supply/production.csv")]
    (into {} (for [l (rest (line-seq r)) :let [[reg p q] (str/split l #",")]] [[reg p] (parse-double q)]))))

(defn cut-report
  "The cell holding Ras Laffan (51.55E 25.90N) cut whole with the assets
  present in 2011 (the product graph's year), through bnm.supply/outage:
  the cut and the products and industries losing 1% or more."
  [assets]
  (let [c (cells/cell 51.55 25.90)
        cut (cp/cell-cut assets #{c} {:year 2011 :production (read-production)})
        g (sup/load-graph "data/derived/supply")
        o (sup/outage g cut)
        loss (fn [m] (into (sorted-map) (for [[k v] m :when (< v 0.99)] [(str/join " " k) (round (- 1.0 v) 4)])))]
    {:cell c
     :assets-2011 (into (sorted-map) (frequencies (keep #(when (and (= c (:cell %)) (cp/present? % 2011)) (cp/asset-key %)) assets)))
     :cut (into (sorted-map) (for [[k v] cut] [(str/join " " k) (round v 4)]))
     :product-loss (loss (:products o))
     :industry-loss (loss (:industries o))
     :settled (:settled o)}))

;; ---------------------------------------------------------------------- main

(defn -main [& args]
  (when (= ["cut"] args)
    (let [r (cut-report (cp/read-assets (str dir "/assets.csv")))]
      (spit (str dir "/cut-ras-laffan.edn") (with-out-str (pp/pprint r)))
      (pp/pprint (select-keys r [:cell :assets-2011 :cut :settled]))
      (System/exit 0)))
  (let [xw (cells/load-crosswalk)
        land (cp/land-km2 xw)
        main-iso (into {} (for [[c rows] (:by-cell xw)
                                :let [l (filter #(#{"land" "disputed"} (:flag %)) rows)
                                      rs (if (seq l) l rows)]]
                            [c (:iso (apply max-key :share rs))]))
        assets (cp/read-assets (str dir "/assets.csv"))
        _ (log "assets (dup_of blank):" (count assets))
        g0 (cp/read-grids (str dir "/cell-grids.csv"))
        _ (log "grids:" (into (sorted-map) (update-vals g0 count)))
        {g :grids break :break lf :factors lc :country} (cp/splice-lights g0)
        world (fn [g y] (round (reduce + (vals (get-in g ["ntl/dn" y :values]))) 0))
        join {:world-raw (into (sorted-map) (for [y [2012 2013 2014 2015]] [y (world g0 y)]))
              :world-spliced (into (sorted-map) (for [y [2012 2013 2014 2015]] [y (world g y)]))
              :parts-own-ratio (count lf) :parts-break (count break)
              :ratio-quantiles (let [v (vec (sort (vals lf))) q #(round (v (int (* % (dec (count v))))) 3)]
                                 (when (seq v) {:p10 (q 0.1) :p50 (q 0.5) :p90 (q 0.9)}))
              :country-ratio {:CHN (round (lc "CHN") 3) :USA (round (lc "USA") 3) :IND (round (lc "IND") 3)
                              :NGA (round (lc "NGA") 3) :RUS (round (lc "RUS") 3)}}
        _ (log "sensor join:" join)
        ;; concentration
        ks (cp/concentration-keys assets)
        hhi (cp/hhi-stocks assets ks (range 1990 2027))
        _ (stages/write-stocks "data/derived/stocks/cells.csv" hhi)
        _ (log "cells.csv:" (count hhi) "rows," (count ks) "keys")
        by-key (group-by cp/asset-key assets)
        top (into (sorted-map) (for [k ks] [k (mapv #(update % :share round 3) (cp/top-cells (by-key k) k 2026 5))]))
        world-hhi (into (sorted-map) (for [k ks]
                                       [k (into (sorted-map) (for [r hhi :when (and (= "WLD" (:iso r)) (= (:variable r) (str "cells/" k "-hhi"))
                                                                                     (#{1990 2000 2010 2020 2025 2026} (:year r)))]
                                                               [(:year r) (round (:value r) 4)]))]))
        ;; profiles and types
        ps (profiles g assets land cp/present?)
        _ (log "profiles:" (count ps))
        fits (cp/fit-types ps profile-years k-types {})
        tr (report-types fits profile-years)
        names (:names tr)
        sensor (cp/sensor-flags g break profile-years "ntl/dn")
        entry (entry-flags g assets land fits)
        ;; the same read back from the snapshots (descriptive, not causal)
        ps-b (profiles g assets land cp/present-backcast?)
        fits-b (cp/fit-types ps-b profile-years k-types {})
        tr-b (report-types fits-b profile-years)
        _ (write-csv (str dir "/profiles.csv") (into ["part" "year" "type"] (map name cp/features))
                     (for [[[y k] x] (sort-by key ps)] (into [k y (names (get-in fits [y :labels k]))] x)))
        regime (read-stock "data/derived/stocks/econ-factors.csv" "econ/investment-regime")
        paths (transitions-report g fits names profile-years regime {:entry entry :sensor sensor})
        paths-b (transitions-report g fits-b (:names tr-b) profile-years regime {:sensor sensor})
        ;; displacement
        stage-d (into {} (for [c ["iron" "aluminium"]]
                           [c (read-stock "data/derived/stocks/stages.csv" (str "stages/" c "-d"))]))
        disp (displacement-report assets stage-d)
        ;; features
        frows (vec (mapcat #(unit-features g assets xw main-iso %) (range 1994 2027)))
        _ (write-csv (str dir "/features.csv") (into ["unit_kind" "unit" "iso" "year"] (map name feature-cols)) frows)
        _ (log "features.csv:" (count frows) "rows")
        cover {:parts-profiled (count (distinct (map second (keys ps))))
               :end-flags (into (sorted-map) (frequencies (map (juxt :source :end_flag) (filter :end_flag assets))))}
        report {:sensor-join join :types tr :paths paths :backcast {:types tr-b :paths paths-b} :displacement disp :top-cells-2026 top :world-hhi world-hhi :coverage cover}]
    (spit (str dir "/profiles-report.edn") (with-out-str (pp/pprint report)))
    (pp/pprint (dissoc report :top-cells-2026))))
