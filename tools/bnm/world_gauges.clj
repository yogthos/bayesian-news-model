(ns bnm.world-gauges
  "The world state at the lattice's year and half-decade nodes (recursive
  step 2, loop audit Round 2, bead bnm-56c.19): bnm.world-state's series
  built from data/slow/series.tsv and entered as each node's :world-gauges
  group, directly and times the place's exposure (bnm.fast-run/
  exposure-fn, `exposure-fn` here: food imports, net fuel imports,
  openness, read two years before the origin; above the countries, their
  mean).

  Series (data/slow/series.tsv; lag = the data's own publication lag):
    :food    world food/price_index, the IMF food price index (FRED
             PFOODINDEXM, from 1992; the repository's stand-in for the
             FAO food price index), log, published a month after its month
    :brent   world energy/oil_brent (FRED DCOILBRENTEU, from 1987), log
    :vix     world finance/vix (FRED VIXCLS, from 1990), log
    :usd     world finance/usd_broad (FRED DTWEXBGS, from 2006), log
    :policy  USA finance/policy_rate (BIS; FRED for the US), points
    :credit  the mean three-year change in finance/credit_gdp (BIS total
             credit) over the countries with data, as known at each month
             (bnm.named/world-credit's rule), its own month

    jolt -A:tools -M -m bnm.world-gauges [--out data/derived/world-state/series.tsv]

  writes the series as read (a new derived table; nothing else reads it).

  For the acceptance harness (bnm-56c.19's notes: the planted signal and
  the noise go where the reference reads, the year's own world group), one
  extra world column (`extra-column`), the same at every place:
    :noise    a standard normal per origin (seeded);
    :planted  the noise plus `strength` times the z-score of the share of
              countries with an onset in the year node's window (it reads
              the future: a diagnostic only);
    :false    the noise plus 3 at the origins of 2013 and 2014, a world
              move that carries nothing."
  (:require [augur.math :as m]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [bnm.world-state :as ws]
            [bnm.world-term :as wtm]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def sources
  "id -> [unit variable transform]."
  {:food ["world" "food/price_index" :log]
   :brent ["world" "energy/oil_brent" :log]
   :vix ["world" "finance/vix" :log]
   :usd ["world" "finance/usd_broad" :log]
   :policy ["USA" "finance/policy_rate" :id]})

(defn read-raw
  "{id [[period available value] ...]} of `sources` from a series.tsv."
  [path]
  (let [want (into {} (for [[id [u v tr]] sources] [[u v] [id tr]]))]
    (with-open [r (io/reader path)]
      (reduce (fn [acc line]
                (let [[unit v _ pend avail value] (str/split line #"\t" -1)]
                  (if-let [[id tr] (want [unit v])]
                    (let [x (parse-double value)]
                      (update acc id (fnil conj []) [(st/month-of pend) (st/month-of avail)
                                                     (if (= :log tr) (Math/log (Math/max x 1e-12)) x)]))
                    acc)))
              {} (line-seq r)))))

(defn credit-entries
  "[[month month value] ...]: the world's mean three-year change in credit
  to GDP as known at each month of `months` (computed from what was
  published by then, so known at that month)."
  [db units months]
  (vec (keep (fn [[t v]] (when v [t t (double v)])) (sort-by key (nm/world-credit db units months)))))

(defn lag-of
  "The publication lag a series' data declare: the largest available less
  period, so no read of a value is ever refused for being early."
  [entries]
  (reduce max 0 (map (fn [[p a]] (- (long a) (long p))) entries)))

(defn load-state
  "The world state from data/slow (series.tsv, variables.tsv) and the
  loaded series `db` (bnm.named/load-series) for the credit series over
  `units`, months from 1950 to `end`."
  [db units end]
  (let [raw (read-raw "data/slow/series.tsv")
        credit (credit-entries db units (range (* 12 1950) (inc end)))]
    (ws/world-state (into {:credit {:entries credit :lag 0}}
                          (for [[id es] raw] [id {:entries es :lag (lag-of es)}])))))

(defn- seeded-normal [seed t] (m/normal! (m/rng (+ (* 1000003 (long seed)) (long t)))))

(defn extra-column
  "(fn [t]) -> the harness's extra world column at origin t (see the ns
  doc), or nil without one. `share` (fn [t]) the year window's onset share
  among the countries scored (for :planted)."
  [{:keys [kind seed strength share year-origins]}]
  (case kind
    nil nil
    :noise (fn [t] (seeded-normal seed t))
    :false (fn [t] (+ (seeded-normal seed t) (if (<= 2013 (quot t 12) 2014) 3.0 0.0)))
    :planted (let [rs (keep share year-origins)
                   n (count rs)
                   mu (/ (reduce + rs) n)
                   sd (Math/sqrt (/ (reduce + (map #(let [d (- % mu)] (* d d)) rs)) n))
                   s (double (or strength 1.0))]
               (fn [t] (let [r (share t)]
                         (+ (seeded-normal seed t) (if r (* s (/ (- r mu) sd)) 0.0)))))))

(defn onset-share
  "(fn [t]) -> the share of `countries` whose year target at t is an onset,
  over those scored, from spec `sp`'s :target; nil when none is."
  [sp countries]
  (memoize (fn [t]
             (let [ys (keep #(:y ((:target sp) :year % t)) countries)]
               (when (seq ys) (/ (double (count (filter true? ys))) (count ys)))))))

(defn with-world
  "`sp` with the :world-gauges group at its year and half-decade nodes:
  the world state `state` as known at t, and its moves times the place's
  exposure (`exposure` (fn [country t]) -> {:food :energy :open}; above
  the countries the mean of its countries' known values). opts :extra,
  (fn [t]) one more column (the harness's), the same at every place."
  ([sp state exposure] (with-world sp state exposure {}))
  ([sp state exposure {:keys [extra]}]
   (let [par (:parent sp)
         leaves (remove (set (vals par)) (keys par))
         below (reduce (fn [m c] (reduce #(update %1 %2 (fnil conj []) c) m (take-while some? (iterate par c))))
                       {} leaves)
         members (fn [place] (get below place [place]))
         ex (memoize (fn [c t] (exposure c t)))
         place-exposure (memoize (fn [place t]
                                   (let [es (map #(ex % t) (members place))
                                         avg (fn [k] (let [vs (keep k es)] (when (seq vs) (/ (reduce + vs) (double (count vs))))))]
                                     {:food (avg :food) :energy (avg :energy) :open (avg :open)})))
         scales (set (filter #{:year :half-decade} (map :name (:scales sp))))
         width (+ (count ws/gauge-names) (if extra 1 0))]
     (assoc sp
            :world-gauges (fn [scale place t]
                            (let [{:keys [x reads]} (ws/gauges state t (place-exposure place t))]
                              {:x (cond-> x extra (conj (extra t))) :reads reads}))
            :world-width (into {} (for [s scales] [s width]))))))

(def once-products
  "fix 3: the world gauges a node reads with its own exposure, the only
  world inputs left as columns on a place's row: they differ by place."
  [:food12-x-food :brent12-x-energy])

(defn- once-read
  "{:g {:food12 :brent12} :reads}: the two world gauges kept (fix 3) at
  origin t, read through bnm.world-state; the VIX, credit, the dollar and
  the policy rate are not read (concurrent, not leading: the step-2
  diagnosis; a gauge comes back only after bnm.lead-test passes it)."
  [state t]
  (let [r (ws/reader state t)
        g {:food12 (ws/change r :food 12) :brent12 (ws/change r :brent 12)}]
    {:g g :reads (ws/reads r)}))

(defn with-world-once
  "`sp` with the world gauges entered once per origin (fix 3 of the step-2
  rescore, docs/RECURSIVE.md §3): the world node's term (bnm.world-term),
  learned from one row per origin, the share of countries with an onset in
  the node's window on world food prices and Brent over 12 months, is
  added to each country node's offset at the year and half-decade
  (:world-offset); the :world-gauges group holds only food12 times the
  place's food imports and brent12 times its net fuel imports (above the
  countries their mean exposure). opts :extra, the harness's column (fn
  [t]), enters the term as a third world gauge."
  ([sp state exposure] (with-world-once sp state exposure {}))
  ([sp state exposure {:keys [extra]}]
   (let [par (:parent sp)
         leaves (vec (remove (set (vals par)) (keys par)))
         leaf? (set leaves)
         below (reduce (fn [m c] (reduce #(update %1 %2 (fnil conj []) c) m (take-while some? (iterate par c))))
                       {} leaves)
         members (fn [place] (get below place [place]))
         ex (memoize (fn [c t] (exposure c t)))
         place-exposure (memoize (fn [place t]
                                   (let [es (map #(ex % t) (members place))
                                         avg (fn [k] (let [vs (keep k es)] (when (seq vs) (/ (reduce + vs) (double (count vs))))))]
                                     {:food (avg :food) :energy (avg :energy)})))
         read (memoize (fn [t] (once-read state t)))
         scales (filter #(#{:year :half-decade} (:name %)) (:scales sp))
         start (long (or (:start sp) 0))
         end (long (or (:end sp) (+ 11 (* 12 2030))))
         gauges (fn [t] (let [{:keys [food12 brent12]} (:g (read t))]
                          (cond-> [food12 brent12] extra (conj (extra t)))))
         term (into {} (for [{:keys [name per]} scales]
                         [name (wtm/term-fn
                                {:gauges gauges
                                 :origins (vec (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end))))
                                 :outcome (memoize (fn [t] (let [ys (filter (comp some? :y) (keep #((:target sp) name % t) leaves))]
                                                    (when (seq ys)
                                                      {:share (/ (count (filter :y ys)) (double (count ys)))
                                                       :n (count ys)
                                                       :known-at (reduce max (map :known-at ys))}))))})]))
         x* (fn [a b] (when (and a b) (* (double a) (double b))))]
     (assoc sp
            :world-offset (fn [scale place t]
                            (when-let [f (and (leaf? place) (term scale))]
                              {:x (:x (f t)) :reads (:reads (read t))}))
            :world-gauges (fn [_scale place t]
                            (let [{:keys [g reads]} (read t)
                                  {:keys [food energy]} (place-exposure place t)]
                              {:x [(x* (:food12 g) food) (x* (:brent12 g) energy)]
                               :names once-products :reads reads}))
            :world-width (into {} (for [{:keys [name]} scales] [name 2]))))))

(defn exposure-fn
  "(fn [country t]) -> {:food :energy :fertiliser :open}: food imports, fuel
  imports less fuel exports (shares of merchandise trade), agriculture's
  share of output and trade over output, as fractions, read two years
  before the origin (bnm.named/structure's rule: fixed before the shock,
  past WDI's revisions); nil where unknown. bnm.fast-run's exposure."
  [db]
  (fn [c t]
    (let [g (fn [v] (some-> (db [c v]) (sk/value (- t 24) {:stale 30}) (/ 100.0)))
          imp (g "energy/fuel_imports_share") exp (g "energy/fuel_exports_share")]
      {:food (g "food/imports_share")
       :energy (when (and imp exp) (- imp exp))
       :fertiliser (g "economy/agriculture_share")
       :open (g "trade/openness")})))

(defn exposure-from
  "`exposure-fn` from data/slow."
  []
  (exposure-fn (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))))

(defn -main [& args]
  (let [{:keys [out]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        out (or out "data/derived/world-state/series.tsv")
        db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))
        units (set (filter #(= 3 (count %)) (map first (keys db))))
        state (load-state db units (+ 11 (* 12 2026)))]
    (io/make-parents out)
    (with-open [w (io/writer out)]
      (.write w "series\tperiod\tavailable\tvalue\tlag\n")
      (doseq [[id {:keys [by lag]}] (sort-by key state) [p [a v]] by]
        (.write w (str (name id) "\t" (st/month-str p) "\t" (st/month-str a) "\t" v "\t" lag "\n"))))
    (println "wrote" out)))
