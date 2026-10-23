(ns bnm.cells-vs-provinces
  "Cells against provinces, with what is in the cells (bead bnm-3ka.11;
  docs/RESEARCH.md, the A5 pilot; docs/PRINCIPLES.md 11).

  The pilot's event runs stay as they were: data/runs/a5p-gdelt (province
  nodes) and data/runs/a5p-gdelt-a5 (cell nodes), events from 1979, scored
  2015-26 every 13 weeks, 100 simulations. Their scored rows hold, per
  sub-national node and period, the event model's violence escalation
  probability and the outcome. A stage on top reads each node's features as
  gauges: a bnm.lattice node, one level, one step per 13-week period, pooled
  over the level's places, the event model's log odds as its offset (what is
  known), learning online from the rows already resolved. Arms:

    provinces, no features   the pilot (the raw event model)
    provinces, features      province features: cells aggregated by the crosswalk
    cells, features          the cell's own features
    cells, shuffled          each cell reads another cell's features, the
                             cells permuted within each country (the placebo;
                             --placebos n seeds, seed 1 the registered one)

  and, as diagnostics, the stage with its gauges zeroed (provinces and
  cells): what the stage alone does.

  Gauges (`gauge-names`), each for year Y from data known by Y-1:
  the eleven profile measures of bnm.cell-profiles (extraction and
  processing intensity, industrial CO2 per person, built-up, lights per
  person, GDP per person, density), a cell's from its cell x country part,
  a province's from its cells' stocks summed onto it through the crosswalk
  (bnm.cell-profiles/to-provinces); and the nine columns of
  data/derived/cells/features.csv (light and GDP-per-person growth against
  the country, extraction against the country, mining area, lootable
  resources, processing sites, openings), counts as log1p. The lights are
  spliced across the DMSP-VIIRS join as bnm.cell-profiles-run splices
  them; the lights growth of the join year (`join-year`, 2016) is left out
  for every node, and both lights gauges from then on for a part the
  splice could not join (or a province most of whose lights are in such
  parts) (`mask-lights`). Each is
  standardized within the year over the arm's nodes, capped at 5, a missing
  value at 0. Every model also reads two core inputs: the event model's log
  odds over 4 (a learned recalibration) and a quiet flag. Training rows are
  all of a level's violence rows; the scores are those of the quiet ones.

  Measures (chosen before the pilot): violence escalation from quiet at the
  sub-national level, ROC AUC and Brier skill against the climatology
  (bnm.eval/escalation-scores), with a bootstrap over periods, paired.

    jolt -A:tools -M -m bnm.cells-vs-provinces --provinces data/runs/a5p-gdelt \\
      --cells data/runs/a5p-gdelt-a5 --name cvp [--placebos 19] [--boot 500] \\
      [--registered true]

  Refuses rows of the test window (2015 on) without --registered true.
  Writes data/runs/<name>/report.txt, scores.edn and rows-<arm>.tsv."
  (:require [augur.math :as m]
            [bnm.cell-profiles :as cp]
            [bnm.cells :as cells]
            [augur.score :as sc]
            [bnm.eval :as ev]
            [bnm.lattice :as lt]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

;; --- nodes and periods ----------------------------------------------------------------

(defn cell-part
  "The cell x country part of a cell node (ISO.A<HEX>, bnm.ingest's
  cell-node), nil for anything else."
  [node]
  (when-let [[_ iso hex] (re-matches #"([A-Z]{3})\.A([0-9A-F]{10,})" node)]
    (cells/cell-key (str/lower-case hex) iso)))

(defn period-year [label] (parse-long (subs label 0 4)))

(def test-from
  "The first year of the test window."
  2015)

(defn test-window? [labels] (boolean (some #(>= (period-year %) test-from) labels)))

;; --- gauges -----------------------------------------------------------------------------

(def feature-cols
  "data/derived/cells/features.csv's columns, in gauge order."
  [:light-growth-within :gdppc-growth-within :extraction-within :mining-km2 :lootable-gold :lootable-gems
   :lootable-oil :processing-sites :openings])

(def ^:private raw-cols #{:light-growth-within :gdppc-growth-within :extraction-within})

(def gauge-names (vec (concat cp/features feature-cols)))

(def gauge-groups
  "Gauge indices (into `gauge-names`) by group."
  (let [ix (zipmap gauge-names (range))
        group (merge cp/feature-group
                     {:light-growth-within :growth :gdppc-growth-within :growth}
                     (zipmap [:extraction-within :mining-km2 :lootable-gold :lootable-gems :lootable-oil
                              :processing-sites :openings]
                             (repeat :assets)))]
    (into (sorted-map) (update-vals (group-by group gauge-names) #(mapv ix %)))))

(defn- l1p [x] (Math/log (+ 1.0 (max 0.0 (double x)))))

(defn unit-vector
  "A unit's gauges, `gauge-names` order, nil where unknown: `profile` a
  bnm.cell-profiles/profile map, `row` a features.csv row {column value}."
  [profile row]
  (vec (concat (map #(get profile %) cp/features)
               (map (fn [c] (when-let [v (get row c)] (if (raw-cols c) (double v) (l1p v)))) feature-cols))))

(defn standardize
  "{node [x...]} with nils to z scores per column over the nodes that have
  one, capped at 5, nil and constant columns read as 0."
  [m]
  (if (empty? m)
    {}
    (let [d (count (first (vals m)))
          stats (vec (for [j (range d)
                           :let [xs (keep #(nth % j) (vals m))
                                 n (count xs)]]
                       (when (pos? n)
                         (let [mu (/ (reduce + xs) n)
                               sd (Math/sqrt (/ (reduce + (map #(let [e (- % mu)] (* e e)) xs)) n))]
                           [mu sd]))))]
      (update-vals m (fn [v] (vec (for [j (range d)
                                        :let [x (nth v j) [mu sd] (stats j)]]
                                    (if (and x sd (pos? sd))
                                      (max -5.0 (min 5.0 (/ (- x mu) sd)))
                                      0.0))))))))

(defn donors
  "The placebo: {node donor}, each country's nodes permuted among
  themselves (Fisher-Yates, seeded), `iso-of` a node's country."
  [nodes iso-of seed]
  (let [r (m/rng seed)]
    (into {} (mapcat (fn [[_ ns]]
                       (let [ns (vec (sort ns))
                             a (object-array ns)]
                         (doseq [i (range (dec (count ns)) 0 -1)]
                           (let [j (long (* (m/next-double! r) (inc i)))
                                 t (aget a i)]
                             (aset a i (aget a j))
                             (aset a j t)))
                         (map vector ns (vec a))))
                     (sort-by key (group-by iso-of (distinct nodes)))))))

;; --- the sensor join ------------------------------------------------------------------

(def join-year
  "The first gauge year whose lights are VIIRS's: the last DMSP year is
  2013, the first VIIRS year 2014 is known in 2015 (lights lag a year),
  and a gauge for year Y reads what is known by Y-1."
  (+ cp/last-dmsp 3))

(defn mask-lights
  "Gauges `v` of year `y` with the lights read across the sensor join left
  out: the lights growth of the join year for every node (spliced, its
  first VIIRS year equals its last DMSP one, a year's change lost), and
  from the join year on both lights gauges of a node the splice could not
  put on one scale (`broken?`, bnm.cell-profiles/splice-lights's :break)."
  [v y broken?]
  (let [li (.indexOf ^java.util.List gauge-names :serv-lights-pc)
        gi (.indexOf ^java.util.List gauge-names :light-growth-within)]
    (cond-> v
      (= y join-year) (assoc gi nil)
      (and broken? (>= y join-year)) (assoc gi nil li nil))))

(defn broken-provinces
  "The provinces most of whose last-DMSP-year lights (`dmsp`, {part dn})
  are in parts the splice could not put on one scale (`break`)."
  [xw dmsp break]
  (let [all (cp/to-provinces xw dmsp)
        bad (cp/to-provinces xw (select-keys dmsp break))]
    (into #{} (for [[u x] bad :when (> x (* 0.5 (get all u 0.0)))] u))))

;; --- provinces from cells -----------------------------------------------------------------

(defn province-grids
  "Grids `g` (bnm.cell-profiles/read-grids) on provinces: each variable's
  part values summed onto provinces (bnm.cell-profiles/to-provinces)."
  [xw g]
  (update-vals g (fn [years] (update-vals years #(update % :values (fn [vs] (cp/to-provinces xw vs)))))))

(defn province-counts
  "Asset counts {part {measure v}} on provinces: {province {measure v}}."
  [xw ac]
  (let [by-measure (reduce-kv (fn [acc k ms] (reduce-kv (fn [acc f v] (assoc-in acc [f k] v)) acc ms)) {} ac)]
    (reduce-kv (fn [acc f vs] (reduce-kv (fn [acc u v] (assoc-in acc [u f] v)) acc (cp/to-provinces xw vs)))
               {} by-measure)))

;; --- the stage ---------------------------------------------------------------------------

(def defaults
  {:discount 0.99 :forget 0.99 :floor 1e-4 :prior-var 0.1})

(defn- clamp [p floor] (max floor (min (- 1.0 floor) (double p))))

(defn stage
  "The violence rows `rows` ({:id :period :stratum :p-escalate :escalated},
  one level) with :p-stage, the lattice's forecast for each, made at the
  period's start from the rows resolved before it. `gauges-of` (fn [node
  year]) gives a node's standardized gauges or nil (all 0)."
  [rows gauges-of opts]
  (let [{:keys [discount forget floor prior-var]} (merge defaults opts)
        labels (vec (sort (distinct (map :period rows))))
        t-of (zipmap labels (range))
        places (vec (sort (distinct (map :id rows))))
        by (into {} (for [r rows] [[(:id r) (t-of (:period r))] r]))
        zeros (vec (repeat (count gauge-names) 0.0))
        offset (fn [r] (lt/logit-of (clamp (:p-escalate r) floor)))
        width (+ 2 (count gauge-names))
        spec {:scales [{:name :period :per 1 :horizon 1 :discount discount}]
              :levels [[:node places]] :parent {}
              :start 0 :end (dec (count labels))
              :forget forget
              :without #{:contradiction}
              :target (fn [_ place t] (when-let [r (by [place t])] {:y (boolean (:escalated r)) :known-at (inc t)}))
              :offset (fn [_ place t] (if-let [r (by [place t])] (offset r) 0.0))
              :gauges (fn [_ place t]
                        (let [r (by [place t])]
                          (into [(if r (/ (offset r) 4.0) 0.0) (if (= :quiet (:stratum r)) 1.0 0.0)]
                                (or (gauges-of place (period-year (labels t))) zeros))))
              :gauge-width {:period width}
              :gauge-groups {:period (update-vals gauge-groups #(mapv (partial + 2) %))}
              :core {:period [0 1]}
              ;; the intercept and the core inputs start tight: until it has
              ;; learned, the stage forecasts the event model
              :prior (fn [_ _ _] {0 [0.0 prior-var] 1 [0.0 prior-var] 2 [0.0 prior-var]})}
        out (lt/run spec)
        p (into {} (for [{:keys [place origin p]} (:forecasts out)] [[place origin] p]))]
    (vary-meta (mapv #(assoc % :p-stage (p [(:id %) (t-of (:period %))])) rows)
               assoc :weights (second (last (get (:weights out) [:period :node]))))))

;; --- scores -------------------------------------------------------------------------------

(defn- brier [ps ys] (/ (reduce + (map (fn [p y] (let [d (- p (if y 1.0 0.0))] (* d d))) ps ys)) (count ps)))

(defn scores
  "AUC and Brier skill against the climatology (:p-clim, as
  bnm.eval/escalation-scores) of the quiet rows of `rows`, the probability
  under `k` (default :p-stage)."
  ([rows] (scores rows :p-stage))
  ([rows k]
   (let [g (filterv #(= :quiet (:stratum %)) rows)
         ys (mapv #(boolean (:escalated %)) g)
         ps (mapv #(double (k %)) g)
         bc (when (seq g) (brier (map :p-clim g) ys))]
     {:n (count g) :positives (count (filter true? ys))
      :auc (when (seq g) (sc/auc ps ys))
      :bss (when (and bc (pos? bc)) (- 1.0 (/ (brier ps ys) bc)))})))

(defn bootstrap
  "95% intervals [lo hi] of the differences a - b in AUC and Brier skill
  (quiet rows, :p-stage), periods resampled with replacement, the same
  periods for both."
  [a b {:keys [reps seed ka kb] :or {reps 500 seed 1 ka :p-stage kb :p-stage}}]
  (let [ga (group-by :period a) gb (group-by :period b)
        labels (vec (sort (distinct (concat (keys ga) (keys gb)))))
        r (m/rng seed)
        draws (for [_ (range reps)]
                (let [ls (repeatedly (count labels) #(labels (long (* (m/next-double! r) (count labels)))))
                      sa (scores (mapcat ga ls) ka) sb (scores (mapcat gb ls) kb)]
                  [(when (and (:auc sa) (:auc sb)) (- (:auc sa) (:auc sb)))
                   (when (and (:bss sa) (:bss sb)) (- (:bss sa) (:bss sb)))]))
        draws (vec draws)
        ci (fn [xs] (let [xs (vec (sort (remove nil? xs))) n (count xs)]
                      (when (pos? n) [(xs (long (* 0.025 (dec n)))) (xs (long (Math/ceil (* 0.975 (dec n)))))])))]
    {:auc (ci (map first draws)) :bss (ci (map second draws)) :reps reps}))

;; --- inputs from disk ------------------------------------------------------------------------

(def dir "data/derived/cells")

(defn- num [s] (when-not (str/blank? s) (parse-double s)))

(defn read-features
  "features.csv rows of `kind` (\"cell\" or \"province\") whose unit is in
  `units` and year in `years`: {[unit year] {column value}}."
  [path kind units years]
  (with-open [r (io/reader path)]
    (let [[head & lines] (line-seq r)
          cols (mapv keyword (drop 4 (str/split head #",")))]
      (into {} (keep (fn [line]
                       (when (str/starts-with? line kind)
                         (let [[k u _ y & vs] (str/split line #"," -1)
                               y (parse-long y)]
                           (when (and (= k kind) (units u) (years y))
                             [[u y] (zipmap cols (map num vs))])))))
            lines))))

(defn unit-gauges
  "{year {node [gauge...]}} raw, for the province nodes or the cell nodes
  of `nodes` (`kind` :province or :cell) over `years`."
  [{:keys [xw g pg assets land pland break pbreak]} kind nodes years]
  (let [cell? (= kind :cell)
        broken (if cell? break pbreak)
        unit-of (if cell? cell-part identity)
        nodes (filter unit-of nodes)
        frow-unit (if cell? #(cells/key-cell (cell-part %)) identity)
        fr (read-features (str dir "/features.csv") (if cell? "cell" "province")
                          (set (map frow-unit nodes)) (set years))]
    (into (sorted-map)
          (for [y years
                :let [ac (cp/asset-counts assets (dec y))
                      ac (if cell? ac (province-counts xw ac))
                      grids (if cell? g pg)
                      lands (if cell? land pland)]]
            [y (into {} (for [n nodes
                              :let [u (unit-of n)
                                    l (get lands u 0.0)
                                    prof (when (and (pos? l) (or (not cell?) (>= l cp/min-land-km2)))
                                           (cp/profile grids (get ac u {}) u l (dec y)))
                                    v (mask-lights (unit-vector prof (fr [(frow-unit n) y])) y (contains? broken u))]
                              :when (some some? v)]
                          [n v]))]))))

(defn- load-inputs
  "The crosswalk, the grids with the lights spliced across the sensor join
  (as bnm.cell-profiles-run reads them), on parts and on provinces, land,
  the parts and provinces whose lights the splice could not join, and the
  assets."
  []
  (let [xw (cells/load-crosswalk)
        g0 (cp/read-grids (str dir "/cell-grids.csv"))
        {g :grids break :break} (cp/splice-lights g0)
        land (cp/land-km2 xw)]
    {:xw xw :g g :pg (province-grids xw g) :land land :pland (cp/to-provinces xw land)
     :break break
     :pbreak (broken-provinces xw (get-in g0 ["ntl/dn" cp/last-dmsp :values] {}) break)
     :assets (cp/read-assets (str dir "/assets.csv"))}))

(defn gauges-fn
  "(fn [node year]) over `raw` ({year {node v}}) standardized within each
  year, a node reading `donor`'s gauges when given."
  ([raw] (gauges-fn raw identity))
  ([raw donor]
   (let [z (update-vals raw standardize)]
     (fn [node y] (get-in z [y (donor node)])))))

;; --- main ---------------------------------------------------------------------------------

(defn- parse-args [args]
  (loop [[k v & more] args opts {}]
    (if k (recur more (assoc opts (keyword (subs k 2)) v)) opts)))

(defn- write-rows! [path rows]
  (spit path (apply str (for [r rows]
                          (str (str/join "\t" ((juxt :period :id (comp name :stratum) :p-escalate :p-stage :p-clim :escalated) r)) "\n")))))

(defn- fmt [x] (if (number? x) (format "%.3f" (double x)) "-"))
(defn- fmt-ci [[lo hi]] (if lo (format "[%+.3f, %+.3f]" (double lo) (double hi)) "-"))

(defn run
  [{:keys [provinces cells name placebos boot registered seed]
    :or {placebos "19" boot "500" seed "1"}}]
  (let [t0 (System/currentTimeMillis)
        log (fn [& xs] (println (format "[%5ds]" (quot (- (System/currentTimeMillis) t0) 1000)) (str/join " " xs)) (flush))
        prov (ev/read-scored (str provinces "/scored.edn") :province :violence)
        cell (ev/read-scored (str cells "/scored.edn") :province :violence)
        labels (sort (distinct (map :period (concat prov cell))))
        _ (when (and (test-window? labels) (not= registered "true"))
            (throw (ex-info (str "rows from " test-from " on are the registered test: pass --registered true") {:first (first labels) :last (last labels)})))
        _ (log "rows: provinces" (count prov) "cells" (count cell) "periods" (count labels) (first labels) "-" (last labels))
        years (vec (sort (distinct (map period-year labels))))
        in (load-inputs)
        _ (log "inputs loaded")
        prov-raw (unit-gauges in :province (distinct (map :id prov)) years)
        cell-nodes (vec (sort (distinct (map :id cell))))
        cell-raw (unit-gauges in :cell cell-nodes years)
        cover (fn [raw nodes] (into (sorted-map) (for [[y m] raw] [y (format "%d/%d" (count m) (count nodes))])))
        _ (log "gauges: provinces" (cover prov-raw (distinct (map :id prov))) "cells" (cover cell-raw cell-nodes))
        iso-of #(subs % 0 3)
        none (constantly nil)
        run-arm (fn [arm rows gf] (let [rs (stage rows gf {})] (log "arm" arm "done") [arm rs]))
        seeds (range (parse-long seed) (+ (parse-long seed) (parse-long placebos)))
        arms (into {} [(run-arm :provinces-stage prov none)
                       (run-arm :provinces-features prov (gauges-fn prov-raw))
                       (run-arm :cells-stage cell none)
                       (run-arm :cells-features cell (gauges-fn cell-raw))])
        shuffles (vec (for [s seeds] (second (run-arm (keyword (str "cells-shuffled-" s)) cell
                                                     (gauges-fn cell-raw (donors cell-nodes iso-of s))))))
        sc (merge {:provinces-none (scores prov :p-escalate) :cells-none (scores cell :p-escalate)}
                  (update-vals arms scores))
        shuf-scores (mapv scores shuffles)
        reps (parse-long boot)
        cf (:cells-features arms)
        cmp {:cells-vs-provinces (bootstrap cf (:provinces-features arms) {:reps reps})
             :cells-vs-placebo (when (seq shuffles) (bootstrap cf (first shuffles) {:reps reps}))
             :provinces-features-vs-none (bootstrap (:provinces-features arms) prov {:reps reps :kb :p-escalate})}
        beats? (fn [a b] (and (:auc a) (:auc b) (:bss a) (:bss b) (> (:auc a) (:auc b)) (> (:bss a) (:bss b))))
        c (:cells-features sc)
        verdict {:cells-beat-provinces (beats? c (:provinces-features sc))
                 :cells-beat-placebo (when (seq shuf-scores) (beats? c (first shuf-scores)))
                 :cells-beat-every-shuffle (when (seq shuf-scores) (every? #(beats? c %) shuf-scores))
                 :shuffles-beaten {:auc (count (filter #(> (:auc c) (:auc %)) shuf-scores))
                                   :bss (count (filter #(> (:bss c) (:bss %)) shuf-scores))
                                   :of (count shuf-scores)}}
        out (str "data/runs/" name)
        weights (into {} (for [[k rs] arms] [k (meta rs)]))
        report (str "Cells against provinces with features (bnm-3ka.11): " provinces " / " cells "\n"
                    "periods " (count labels) " " (first labels) " - " (last labels) "\n\n"
                    "violence escalation from quiet, sub-national    n   pos   AUC    BSS\n"
                    (apply str (for [k [:provinces-none :provinces-stage :provinces-features :cells-none :cells-stage :cells-features]
                                     :let [s (sc k)]]
                                 (format "  %-22s %8d %5d  %s  %s\n" (clojure.core/name k) (:n s) (:positives s) (fmt (:auc s)) (fmt (:bss s)))))
                    (apply str (for [[s x] (map vector seeds shuf-scores)]
                                 (format "  %-22s %8d %5d  %s  %s\n" (str "cells-shuffled-" s) (:n x) (:positives x) (fmt (:auc x)) (fmt (:bss x)))))
                    "\ndifferences, 95% period bootstrap (AUC, BSS)\n"
                    (apply str (for [[k v] cmp :when v]
                                 (format "  %-28s %s %s\n" (clojure.core/name k) (fmt-ci (:auc v)) (fmt-ci (:bss v)))))
                    "\nverdict " (pr-str verdict) "\n")]
    (io/make-parents (str out "/x"))
    (doseq [[k rs] arms] (write-rows! (str out "/rows-" (clojure.core/name k) ".tsv") rs))
    (when (seq shuffles) (write-rows! (str out "/rows-cells-shuffled-" (first seeds) ".tsv") (first shuffles)))
    (spit (str out "/scores.edn") (with-out-str (pp/pprint {:scores sc :shuffles (zipmap seeds shuf-scores) :bootstrap cmp
                                                            :verdict verdict :weights weights
                                                            :elapsed-s (quot (- (System/currentTimeMillis) t0) 1000)})))
    (spit (str out "/report.txt") report)
    (println report)
    (log "done")
    verdict))

(defn -main [& args]
  (run (parse-args args))
  (shutdown-agents))
