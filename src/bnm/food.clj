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
             :use :stock-change :method}, :use the domestic supply quantity
             (food, feed, seed, processing, losses, other uses),
             :stock-change the sheet's stock variation with its sign made
             a build (optional: else the balance), :method \"historic\"
             (1961-2009) or \"new\" (2010-); iso WLD FAO's own world rows,
             read for the world where present;
    :imports the bilateral trade matrix {:total :by {exporter tonnes}}
             (imports-of: the importer's report, else its partners', with
             the pairs it left out);
    :hhi-imports the same without those pairs (imports-of :pairs false),
             for supplier concentration (optional: else :imports);
    :exports the same matrix by exporter (exports-from);
    :production (optional) FAOSTAT crop production, read only where a
             country has no balance sheet that year (Japan, Sudan and a
             few others are missing from the 2010- balances).

  Known when known (principle 7): FAOSTAT's balances and its detailed
  trade matrix for a year come out about two years later (historically,
  as printed triennial balances, later still), so a data year's values are
  known by the end of the second year after (known-at), and every row's
  year is the year it is known at. A value at year y reads data years
  before y - 1 only; anchors, trends and references are running minima,
  fits and means over what was known by then. The data are the current
  vintage: FAO's revisions are not undone.

  Variables, written food/<item>-<name> (item wheat, maize, rice, grains):
    production         t, FBS production (none where a balance sheet has
                       none), else the crop statistics' (rice milled)
    use                t, FBS domestic supply quantity (consumption)
    stock-change       t, the sheet's stock change (its stock variation, a
                       build positive), else production + imports -
                       exports - use
    stock-change-to-use  stock change / use: the stationary candidate
    stock-gap          the stock changes cumulated less their linear trend
                       fitted on the years so far (from five, within a
                       method: the cumulation restarts at the 2010 break),
                       over use: a stock level without the drift FAO's
                       stock variations carry
    stock              t, the grain stock (bnm.stockflow: the changes
                       cumulated, across the break) above its lowest level
                       known so far: a lower bound, since FAOSTAT gives
                       changes only; it drifts (world wheat ~470 Mt by 2024
                       against USDA's ~260), so read it against its own
                       recent past
    stocks-to-use      stock / use
    imports            t, the trade matrix, mirror-filled (from 1986)
    exports            t, the trade matrix, as partners report
    import-dependence  imports / (production + imports - exports), FAO's
                       import dependency ratio (matrix trade, FBS
                       production); above 1 for a re-exporter
    supplier-hhi       Herfindahl index of the import partners' shares,
                       0-1 (1: one seller), the importer's own partners
                       (no mirror pairs); none where imports are under 1%
                       of supply or 1000 t
  and food/fbs-method per country: 0 for the historic balances, 1 for the
  new (2010-), whose levels differ (world wheat use +6%, imports +27%).
  For the world (iso WLD: FAO's world rows, else the countries' sum):
    production, use, stock-change, stock-change-to-use, stock-gap, stock,
    stocks-to-use as above, and
    storage-pressure   price-spike pressure from world stocks-to-use,
                       (1 + c) / (s / s_ref + c): 1 at the reference (the
                       mean of the last ten years' stocks-to-use known,
                       from five), convex and rising toward the floor.

  Coverage: the trade matrix starts in 1986, so trade variables start in
  1988 (known). Unrecorded trade is not seen: Nigeria's rice through
  Benin, for one.

  Accounting closes (principle 8): the stock changes only by its four
  flows and the sheet's discrepancy, checked by bnm.stockflow/imbalance."
  (:require [bnm.stockflow :as sf]))

(def lag
  "Years from a data year to the year its values are known: FAOSTAT's
  balances and trade matrix for y-2 are taken as known by the end of y."
  2)

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

(def pressure-window "Years of world stocks-to-use in the reference." 10)

(def trend-min "Years before a stock trend is fitted (and a reference read)." 5)

(def hhi-min-share "Imports below this share of supply: no concentration read." 0.01)

(def hhi-min-tonnes 1000.0)

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

(defn exports-from
  "{[exporter year item] t} from an import map (imports-of): what
  importers' accounts show each country selling; zero for a country
  covered that year and item that nobody bought from."
  [imports coverage]
  (let [base (into {} (for [[k s] coverage :when (not= s "none")] [k 0.0]))]
    (reduce (fn [acc [[_ year item] {:keys [by]}]]
              (reduce (fn [a [ex t]] (update a [ex year item] (fnil + 0.0) t)) acc by))
            base imports)))

(defn exports-of
  "exports-from the imports of `rows` (imports-of with `opts`)."
  [rows coverage opts]
  (exports-from (imports-of rows coverage opts) coverage))

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

(defn stock-change
  "The sheet's stock change where given, else the balance."
  [f]
  (if-let [c (:stock-change f)] (double c) (balance f)))

(defn stock-model
  "The grain stock as a bnm.stockflow model: `years` {year {:production
  :imports :exports :use :stock-change}} as its flows (none in a year
  without data), the sheet's discrepancy (stock change less the balance)
  a flow from outside, from 0 (the level is known only up to a constant)."
  [years]
  (let [at (fn [v] (get years (long (Math/round (double (v :t))))))
        flow (fn [k] (fn [v] (double (or (get (at v) k) 0.0))))]
    (sf/model {:stocks {:grain {:init 0.0 :unit "t"}}
               :flows {:production {:to :grain :rate (flow :production) :unit "t/year"}
                       :imports {:to :grain :rate (flow :imports) :unit "t/year"}
                       :exports {:from :grain :rate (flow :exports) :unit "t/year"}
                       :use {:from :grain :rate (flow :use) :unit "t/year"}
                       :discrepancy {:to :grain :rate (fn [v] (if-let [f (at v)] (- (stock-change f) (balance f)) 0.0))
                                     :unit "t/year"}}})))

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

(defn detrended
  "{t residual}: each value of `xs` ({t x}, sorted) less the least-squares
  line through the values up to t, from `n-min` values on; causal."
  [xs n-min]
  (loop [[[t x] & more] (seq xs) n 0 st 0.0 sx 0.0 stt 0.0 stx 0.0 out (sorted-map)]
    (if (nil? t)
      out
      (let [t' (double t) n (inc n) st (+ st t') sx (+ sx x) stt (+ stt (* t' t')) stx (+ stx (* t' x))
            den (- (* n stt) (* st st))
            r (when (and (>= n n-min) (> den 1e-12))
                (let [b (/ (- (* n stx) (* st sx)) den) a (/ (- sx (* b st)) n)]
                  (- x (+ a (* b t')))))]
        (recur more n st sx stt stx (cond-> out r (assoc t r)))))))

(defn storage-pressure
  "Price-spike pressure at stocks-to-use `s` against the reference `ref`:
  (1 + c) / (s/ref + c), 1 at the reference, convex, (1 + c)/c at the
  floor (competitive storage: Wright 2011)."
  [s ref c]
  (/ (+ 1.0 c) (+ (/ (double s) (double ref)) c)))

;; ---------------------------------------------------------------- series

(defn- var-name [item v] (if item (str "food/" item "-" v) (str "food/" v)))

(defn- row [iso data-year item variable x]
  (when (and (some? x) (let [x (double x)] (not (or (Double/isNaN x) (Double/isInfinite x)))))
    {:iso iso :year (known-at data-year) :variable (var-name item variable) :value (double x)}))

(defn- grains
  "The three items summed per [iso year]: fields present in any (the
  method the wheat sheet's), and the matrix imports only where all three
  are known."
  [fbs imports hhi-imports exports]
  (let [pairs (fn [m] (distinct (map (fn [[iso y _]] [iso y]) (keys m))))
        sum-by (fn [maps] (reduce (fn [a m] (merge-with + a m)) {} maps))
        sum-imports (fn [m] (into {} (for [[iso y] (pairs m)
                                           :let [ms (map #(get m [iso y %]) items)]
                                           :when (every? some? ms)]
                                       [[iso y "grains"] {:total (reduce + 0.0 (map :total ms)) :by (sum-by (map :by ms))}])))]
    {:fbs (into {} (for [[iso y] (pairs fbs)
                         :let [ms (keep #(get fbs [iso y %]) items)
                               method (some :method ms)]]
                     [[iso y "grains"] (cond-> (assoc (reduce (fn [a m] (merge-with + a (into {} (filter (comp number? val) m)))) {} ms)
                                                      :stock-change (reduce + 0.0 (map stock-change ms)))
                                         method (assoc :method method))]))
     :imports (sum-imports imports)
     :hhi-imports (sum-imports hhi-imports)
     :exports (into {} (for [[iso y] (pairs exports)
                             :let [xs (map #(get exports [iso y %]) items)]
                             :when (every? some? xs)]
                         [[iso y "grains"] (reduce + 0.0 xs)]))}))

(defn- segments
  "`years` ({data-year flows}) split where the method changes."
  [years]
  (->> (sort-by key years)
       (partition-by (comp :method val))
       (map #(into (sorted-map) %))))

(defn- stock-rows
  "Use, stock change and its ratio to use, the stock gap, the stock and
  stocks-to-use for one place and item from its balances `years`
  ({data-year flows})."
  [iso item years]
  (let [lv (anchored (stock-path years))
        gap (into {} (for [seg (segments years)
                           :let [cum (into (sorted-map) (rest (reductions (fn [[_ c] [y f]] [y (+ c (stock-change f))]) [nil 0.0] seg)))]
                           [y r] (detrended cum trend-min)]
                       [y r]))]
    (for [[y f] (sort-by key years)
          :let [s (lv (inc y)) u (:use f) c (stock-change f)
                per-use (fn [x] (when (and x u (pos? u)) (/ x u)))]
          r [(row iso y item "use" u)
             (row iso y item "stock-change" c)
             (row iso y item "stock-change-to-use" (per-use c))
             (row iso y item "stock-gap" (per-use (gap y)))
             (row iso y item "stock" s)
             (row iso y item "stocks-to-use" (per-use s))]
          :when r]
      r)))

(defn- trade-rows [production imports hhi-imports exports]
  (for [[[iso y item :as k] {:keys [total by]}] imports
        :let [ex (get exports k)
              p (get production k)
              dep (import-dependence p total ex)
              supply (when (and p ex) (- (+ p total) ex))
              small? (or (< total hhi-min-tonnes) (and supply (pos? supply) (< (/ total supply) hhi-min-share)))
              by (:by (get hhi-imports k {:by by}))]
        r [(row iso y item "imports" total)
           (row iso y item "exports" ex)
           (row iso y item "import-dependence" dep)
           (row iso y item "supplier-hhi" (when-not small? (hhi by)))]
        :when r]
    r))

(defn- by-place-item [fbs]
  (reduce (fn [acc [[iso y item] f]] (assoc-in acc [[iso item] y] f)) {} fbs))

(defn- world-balances
  "{[WLD item] {year flows}}: FAO's world rows where given for an item,
  else the countries' balances summed (the stock change the sum of theirs,
  so trade that does not net out stays in it, as in FAO's world stock
  variation)."
  [fbs]
  (let [own (into {} (for [[[iso y item] f] fbs :when (= iso world)] [[item y] f]))
        summed (reduce (fn [acc [[iso y item] f]]
                         (if (= iso world)
                           acc
                           (update acc [item y]
                                   (fn [w] (merge-with + (or w {})
                                                       (assoc (select-keys (into {} (filter (comp number? val) f))
                                                                           [:production :imports :exports :use])
                                                              :stock-change (stock-change f)))))))
                       {} fbs)
        own-items (set (map first (keys own)))]
    (reduce (fn [acc [[item y] f]] (assoc-in acc [[world item] y] f))
            {} (concat (remove (fn [[[item _] _]] (own-items item)) summed) own))))

(defn pressure-rows
  "Each item's storage pressure from the world's stocks-to-use rows, the
  reference the mean of the last pressure-window years known (from
  trend-min)."
  [world-rows c]
  (let [suffix "-stocks-to-use"]
    (for [[v rs] (group-by :variable (filter #(.endsWith ^String (:variable %) suffix) world-rows))
          :let [rs (vec (sort-by :year rs))
                stem (subs v 0 (- (count v) (count suffix)))]
          i (range (count rs))
          :let [r (rs i)
                win (subvec rs (max 0 (- (inc i) pressure-window)) (inc i))
                ref (/ (reduce + (map :value win)) (count win))]
          :when (and (>= (count win) trend-min) (pos? ref))]
      {:iso world :year (:year r) :variable (str stem "-storage-pressure")
       :value (storage-pressure (:value r) ref c)})))

(defn- production-of
  "{[iso year item] t}: a balance sheet's production (none where it has
  none), else the crop statistics' `crops`; grains the items' sum."
  [fbs crops]
  (let [p (merge (into {} (remove (fn [[[iso _ item] _]] (or (= iso world) (= item "grains"))) crops))
                 (into {} (for [[[iso :as k] f] fbs :when (not= iso world)] [k (:production f)])))]
    (merge p (into {} (for [[iso y] (distinct (map (fn [[iso y _]] [iso y]) (keys p)))]
                        [[iso y "grains"] (reduce + 0.0 (keep #(get p [iso y %]) items))])))))

(defn food-rows
  "Every row {:iso :year :variable :value} (year: when known, see
  known-at) of the food system from {:fbs :imports :hhi-imports :exports
  :production} (see the namespace), sorted. Options: :pressure-c
  (pressure-c)."
  [{:keys [fbs imports hhi-imports exports production]} {c :pressure-c :or {c pressure-c}}]
  (let [fbs (into {} (for [[k f] fbs] [k (update f :production #(or % 0.0))]))
        world? (fn [[[iso] _]] (= iso world))
        hhi-imports (or hhi-imports imports)
        g (grains (into {} (remove world? fbs)) imports hhi-imports exports)
        g-world (grains (into {} (filter world? fbs)) {} {} {})
        fbs (merge fbs (:fbs g) (:fbs g-world))
        countries-fbs (into {} (remove world? fbs))
        prod (production-of countries-fbs production)
        imports (merge imports (:imports g))
        hhi-imports (merge hhi-imports (:hhi-imports g))
        exports (merge exports (:exports g))
        countries (mapcat (fn [[[iso item] years]] (stock-rows iso item years)) (by-place-item countries-fbs))
        methods (keep (fn [[[iso y item] f]] (when (and (= item "grains") (:method f))
                                               (row iso y nil "fbs-method" (if (= "new" (:method f)) 1.0 0.0))))
                      countries-fbs)
        wb (world-balances fbs)
        worlds (mapcat (fn [[[iso item] years]] (stock-rows iso item years)) wb)
        world-prod (for [[[iso item] years] wb [y f] years] (row iso y item "production" (:production f)))]
    (vec (sort-by (juxt :iso :year :variable)
                  (concat countries methods (keep (fn [[[iso y item] x]] (row iso y item "production" x)) prod)
                          (trade-rows prod imports hhi-imports exports) worlds (remove nil? world-prod)
                          (pressure-rows worlds c))))))
