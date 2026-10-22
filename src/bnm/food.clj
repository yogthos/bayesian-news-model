(ns bnm.food
  "The food system's stocks and flows per country and year (bead
  bnm-62z.7; docs/STOCK-FLOW.md): a country's grain stock filled by its
  harvest and imports and drained by its use and exports, how much of its
  supply it buys abroad and from how few sellers, and the world's stocks
  against use, whose fall toward a floor is when prices spike (competitive
  storage: Wright 2011; Bobenrieth, Wright & Zeng 2013).

  Items: wheat, maize, rice (milled), and grains, their sum (in tonnes, so
  dominated by maize and wheat). Inputs, each keyed [iso data-year item]:
    :fbs     FAOSTAT Food Balance Sheets {:production :imports :exports
             :use}, :use the domestic supply quantity (food, feed, seed,
             processing, losses, other uses);
    :imports the bilateral trade matrix {:total :by {exporter tonnes}}
             (imports-of: the importer's report, else its partners');
    :exports the same matrix by exporter (exports-of);
    :production (optional) FAOSTAT crop production, read only where a
             country has no balance sheet that year (Japan, Sudan and a
             few others are missing from the 2010- balances).

  Known when known (principle 7): FAOSTAT's annual data come a year or
  two late; the rule here is that a data year's values are known by the
  end of the next year (known-at), and every row's year is the year it is
  known at. A value at year y therefore reads data years before y only;
  the stock's anchor and the pressure's reference are running minima and
  means over what was known by then.

  Variables (per country iso; <item> each of wheat, maize, rice, grains):
    <item>_production         t, FBS production (none where a balance sheet
                              has none), else the crop statistics' (rice
                              milled) where a country has no balance sheet
    <item>_use                t, FBS domestic supply quantity (consumption)
    <item>_stock_change       t, the balance: production + imports - exports
                              - use (FBS flows; FAO's stock variation)
    <item>_stock              t, the grain stock (bnm.stockflow: the builds
                              cumulated) above its lowest level known so far:
                              a lower bound, since FAOSTAT gives changes only
    <item>_stocks_to_use      stock / use
    <item>_imports            t, the trade matrix, mirror-filled
    <item>_exports            t, the trade matrix, as partners report
    <item>_import_dependence  imports / (production + imports - exports),
                              FAO's import dependency ratio (matrix trade,
                              FBS production); above 1 for a re-exporter
    <item>_supplier_hhi       Herfindahl index of the import partners'
                              shares, 0-1 (1: one seller)
  and for the world (iso WLD, the sum of the countries):
    <item>_production, _use, _stock_change, _stock, _stocks_to_use as above
    <item>_storage_pressure   price-spike pressure from world stocks-to-use,
                              (1 + c) / (s / s_ref + c): 1 at the reference
                              (the mean of the world's stocks-to-use known
                              so far), convex and rising toward the floor.

  Accounting closes (principle 8): the stock changes only by its four
  flows, checked by bnm.stockflow/imbalance."
  (:require [bnm.stockflow :as sf]))

(def lag
  "Years from a data year to the year its values are known: FAOSTAT's
  balances and trade matrix for y-1 are taken as known by the end of y."
  1)

(defn known-at
  "The year a value for `data-year` is known."
  ^long [data-year]
  (+ (long data-year) lag))

(def items ["wheat" "maize" "rice"])

(def world "WLD")

(def pressure-c
  "The curvature of the storage floor: pressure (1 + c) / (s/s_ref + c), so
  (1 + c)/c at empty stocks. A prior (0.25: five times the reference
  pressure at the floor), to be learned against prices (principle 2)."
  0.25)

;; ---------------------------------------------------------------- trade

(defn imports-of
  "{[importer year item] {:total t :by {exporter t}}} from the trade
  matrix `rows` ({:importer :exporter :year :item :tonnes :source}) and
  `coverage` ({[iso year item] status}): the importer's own report
  (\"reported\"), else its partners' (\"mirror\"); with :pairs (true) also
  the pairs a reporting importer left out that exporters report
  (\"mirror_pair\"). Where coverage is \"none\" or absent the imports are
  unknown (no entry), not zero; a covered importer with no rows imported
  nothing."
  [rows coverage {:keys [pairs] :or {pairs true}}]
  (let [known (into {} (for [[k s] coverage :when (not= s "none")] [k {:total 0.0 :by {}}]))]
    (reduce (fn [acc {:keys [importer exporter year item tonnes source]}]
              (let [k [importer year item]]
                (if (and (contains? acc k) (or pairs (not= source "mirror_pair")))
                  (-> acc
                      (update-in [k :total] + tonnes)
                      (update-in [k :by exporter] (fnil + 0.0) tonnes))
                  acc)))
            known rows)))

(defn exports-of
  "{[exporter year item] t}: what importers' accounts (imports-of, so
  mirror-filled, with `opts`) show each country selling; zero for a
  country covered that year and item that nobody bought from."
  [rows coverage opts]
  (let [base (into {} (for [[k s] coverage :when (not= s "none")] [k 0.0]))]
    (reduce (fn [acc [[_ year item] {:keys [by]}]]
              (reduce (fn [a [ex t]] (update a [ex year item] (fnil + 0.0) t)) acc by))
            base (imports-of rows coverage opts))))

(defn hhi
  "The Herfindahl index of `by` ({partner tonnes}): the sum of squared
  shares, 1 for a single partner; nil without imports."
  [by]
  (let [total (reduce + 0.0 (vals by))]
    (when (pos? total)
      (reduce + 0.0 (for [x (vals by) :let [s (/ x total)]] (* s s))))))

(defn import-dependence
  "imports / (production + imports - exports); nil where production is
  unknown or nothing is left for use."
  [production imports exports]
  (when (and production imports exports)
    (let [supply (- (+ (double production) (double imports)) (double exports))]
      (when (pos? supply) (/ (double imports) supply)))))

;; ---------------------------------------------------------------- the stock

(defn balance
  "The stock change of a year's flows: production + imports - exports -
  use, a missing flow none."
  [{:keys [production imports exports use]}]
  (- (+ (or production 0.0) (or imports 0.0)) (or exports 0.0) (or use 0.0)))

(defn stock-model
  "The grain stock as a bnm.stockflow model: `years` {year {:production
  :imports :exports :use}} as its flows (none in a year without data),
  from 0 (the level is known only up to a constant)."
  [years]
  (let [flow (fn [k] (fn [v] (double (or (get-in years [(long (Math/round (double (v :t)))) k]) 0.0))))]
    (sf/model {:stocks {:grain {:init 0.0 :unit "t"}}
               :flows {:production {:to :grain :rate (flow :production) :unit "t/year"}
                       :imports {:to :grain :rate (flow :imports) :unit "t/year"}
                       :exports {:from :grain :rate (flow :exports) :unit "t/year"}
                       :use {:from :grain :rate (flow :use) :unit "t/year"}}})))

(defn stock-run
  "The stock model's run (bnm.stockflow/run) over `years`, yearly, from the
  first data year to the year after the last."
  [years]
  (let [ys (keys years) from (apply min ys) to (inc (apply max ys))]
    (sf/run (stock-model years) {:from from :to to :dt 1.0})))

(defn stock-path
  "{t level}: the grain stock at the start of each year t (the end of t-1),
  from 0 at the first data year."
  [years]
  (let [r (stock-run years)]
    (into (sorted-map) (map (fn [t l] [(long (Math/round (double t))) l]) (:t r) (get-in r [:stocks :grain])))))

(defn anchored
  "{t level}: each level of `path` (sorted by t) above the lowest level
  up to t, its starting level included: the least the stock can be."
  [path]
  (loop [[[t l] & more] (seq path) lo Double/POSITIVE_INFINITY out (sorted-map)]
    (if (nil? t)
      out
      (let [lo (min lo l)] (recur more lo (assoc out t (- l lo)))))))

(defn storage-pressure
  "Price-spike pressure at stocks-to-use `s` against the reference `ref`:
  (1 + c) / (s/ref + c), 1 at the reference, convex, (1 + c)/c at the
  floor (competitive storage: Wright 2011)."
  [s ref c]
  (/ (+ 1.0 c) (+ (/ (double s) (double ref)) c)))

;; ---------------------------------------------------------------- series

(defn- grains
  "The three items summed per [iso year]: fields present in any, and the
  matrix imports only where all three are known."
  [fbs imports exports]
  (let [isos-years (distinct (map (fn [[iso y _]] [iso y]) (keys fbs)))
        sum-by (fn [maps] (reduce (fn [a m] (merge-with + a m)) {} maps))]
    {:fbs (into {} (for [[iso y] isos-years
                         :let [ms (keep #(get fbs [iso y %]) items)]]
                     [[iso y "grains"] (reduce (fn [a m] (merge-with + a (into {} (filter (comp some? val) m)))) {} ms)]))
     :imports (into {} (for [[iso y] (distinct (map (fn [[iso y _]] [iso y]) (keys imports)))
                             :let [ms (map #(get imports [iso y %]) items)]
                             :when (every? some? ms)]
                         [[iso y "grains"] {:total (reduce + 0.0 (map :total ms)) :by (sum-by (map :by ms))}]))
     :exports (into {} (for [[iso y] (distinct (map (fn [[iso y _]] [iso y]) (keys exports)))
                             :let [xs (map #(get exports [iso y %]) items)]
                             :when (every? some? xs)]
                         [[iso y "grains"] (reduce + 0.0 xs)]))}))

(defn- row [iso data-year item variable x]
  (when (and (some? x) (let [x (double x)] (not (or (Double/isNaN x) (Double/isInfinite x)))))
    {:iso iso :year (known-at data-year) :variable (str item "_" variable) :value (double x)}))

(defn- stock-rows
  "Use, stock change, stock and stocks-to-use for one place and item from
  its balances `years` ({data-year flows})."
  [iso item years]
  (let [lv (anchored (stock-path years))]
    (for [[y f] (sort-by key years)
          :let [s (lv (inc y)) u (:use f)]
          r [(row iso y item "use" u)
             (row iso y item "stock_change" (balance f))
             (row iso y item "stock" s)
             (row iso y item "stocks_to_use" (when (and u (pos? u)) (/ s u)))]
          :when r]
      r)))

(defn- trade-rows [production imports exports]
  (for [[[iso y item] {:keys [total by]}] imports
        :let [ex (get exports [iso y item])
              p (get production [iso y item])]
        r [(row iso y item "imports" total)
           (row iso y item "exports" ex)
           (row iso y item "import_dependence" (import-dependence p total ex))
           (row iso y item "supplier_hhi" (hhi by))]
        :when r]
    r))

(defn- by-place-item [fbs]
  (reduce (fn [acc [[iso y item] f]] (assoc-in acc [[iso item] y] f)) {} fbs))

(defn- world-balances
  "{[WLD item] {year flows}}: the countries' balances summed, the stock
  change the sum of theirs (so trade that does not net out stays in it, as
  in FAO's world stock variation)."
  [fbs]
  (reduce (fn [acc [[iso y item] f]]
            (if (= iso world)
              acc
              (update-in acc [[world item] y]
                         (fn [w] (merge-with + (or w {}) (select-keys (into {} (filter (comp some? val) f))
                                                                       [:production :imports :exports :use]))))))
          {} fbs))

(defn- pressure-rows
  "Each item's storage pressure from the world's stocks-to-use rows, the
  reference the mean of those known so far."
  [world-rows c]
  (for [[item rs] (group-by #(subs (:variable %) 0 (- (count (:variable %)) (count "_stocks_to_use")))
                            (filter #(.endsWith ^String (:variable %) "_stocks_to_use") world-rows))
        :let [rs (sort-by :year rs)
              refs (reductions + (map :value rs))]
        [n r total] (map vector (range 1 (inc (count rs))) rs refs)
        :let [ref (/ total n)]
        :when (pos? ref)]
    {:iso world :year (:year r) :variable (str item "_storage_pressure")
     :value (storage-pressure (:value r) ref c)}))

(defn- production-of
  "{[iso year item] t}: a balance sheet's production (none where it has
  none), else the crop statistics' `crops`; grains the items' sum."
  [fbs crops]
  (let [p (merge (into {} (remove (fn [[[iso _ item] _]] (or (= iso world) (= item "grains"))) crops))
                 (into {} (for [[k f] fbs] [k (:production f)])))]
    (merge p (into {} (for [[iso y] (distinct (map (fn [[iso y _]] [iso y]) (keys p)))]
                        [[iso y "grains"] (reduce + 0.0 (keep #(get p [iso y %]) items))])))))

(defn food-rows
  "Every row {:iso :year :variable :value} (year: when known, see
  known-at) of the food system from {:fbs :imports :exports :production}
  (see the namespace; :production, optional, the crop statistics'
  {[iso year item] t}, rice milled, read where a country has no balance
  sheet), sorted. Options: :pressure-c (pressure-c)."
  [{:keys [fbs imports exports production]} {c :pressure-c :or {c pressure-c}}]
  (let [fbs (into {} (for [[[iso :as k] f] fbs :when (not= iso world)] [k (update f :production #(or % 0.0))]))
        g (grains fbs imports exports)
        fbs (merge fbs (:fbs g))
        prod (production-of fbs production)
        imports (merge imports (:imports g))
        exports (merge exports (:exports g))
        countries (mapcat (fn [[[iso item] years]] (stock-rows iso item years)) (by-place-item fbs))
        wb (world-balances fbs)
        worlds (mapcat (fn [[[iso item] years]] (stock-rows iso item years)) wb)
        world-prod (for [[[iso item] years] wb [y f] years] (row iso y item "production" (:production f)))]
    (vec (sort-by (juxt :iso :year :variable)
                  (concat countries (keep (fn [[[iso y item] x]] (row iso y item "production" x)) prod)
                          (trade-rows prod imports exports) worlds (remove nil? world-prod)
                          (pressure-rows worlds c))))))
