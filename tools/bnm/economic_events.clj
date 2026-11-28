(ns bnm.economic-events
  "Economic destabilization as kinds of event the world model forecasts
  (docs/WORLD-MODEL.md), by rules fixed in advance:

  - currency_crisis: Laeven & Valencia's rule (bnm.lattice-banking/
    currency-crises) on WDI's exchange rates against the dollar;
  - inflation_crisis: consumer prices up 40% or more in the year, the first
    year of a run (WDI);
  - growth_collapse: growth of output per head more than two standard
    deviations below the country's own previous twenty years, and
    negative, once in five years (Maddison, WDI after);
  - sovereign_default: default or restructuring, Laeven & Valencia 2018
    (1970-2017) joined with Reinhart & Rogoff (1800-2016);
  - food_price_shock: twelve-month food inflation of 15% or more and 10
    points above its own previous five years' mean, by quarter, once a
    year (FAOSTAT, from 2000);
  - fiscal_consolidation: the general government balance up 1.5 points of
    GDP or more and its expenditure down a point or more in the year
    (Alesina & Ardagna's threshold; spending cuts, which Ponticelli & Voth
    find drive unrest), once in three years (Global Macro Database).
  The sovereign catalogue also takes the Global Macro Database's crisis
  years (their runs' first years), which reach 2023.

    jolt -A:tools -M -m bnm.economic-events

  Writes data/derived/econ-events.csv (kind,iso,month,busy: the onset's
  month and how many months it is under way) and data/derived/
  econ-coverage.csv (kind,iso,from,through, in months)."
  (:require [bnm.lattice-banking :as lb]
            [bnm.named :as nm]
            [bnm.political-gauges :as pg]
            [bnm.slow-train :as st]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def kinds ["currency_crisis" "inflation_crisis" "growth_collapse" "sovereign_default" "food_price_shock"
            "fiscal_consolidation"])

(defn- runs-start
  "The first year of each run of consecutive years in `ys`."
  [ys]
  (set (filter #(not (contains? ys (dec %))) ys)))

(defn inflation-crises
  "Years starting a run of consumer price inflation of 40% or more, from
  {year percent}."
  [infl]
  (runs-start (set (keep (fn [[y x]] (when (>= x 40.0) y)) infl))))

(defn inflation-barred
  "The months at which `inflation-crises` cannot date an onset, from {year
  percent}: the January after every year of 40% or more (a run's later
  years, and the year after it, start no run; bnm-96j)."
  [infl]
  (set (keep (fn [[y x]] (when (>= x 40.0) (* 12 (inc y)))) infl)))

(defn- once-in [n years]
  (loop [[y & ys] (sort years) last-y nil out #{}]
    (if-not y out
      (let [ok (or (nil? last-y) (>= (- y last-y) n))]
        (recur ys (if ok y last-y) (if ok (conj out y) out))))))

(defn log-growth
  "{year log growth} of {year level}, each year on the one before."
  [levels]
  (into {} (for [[y x] levels :let [b (get levels (dec y))] :when (and b (pos? b) (pos? x))] [y (Math/log (/ x b))])))

(defn growth-collapses
  "Years whose growth of output per head ({year level}) is negative and more
  than two standard deviations below the mean of the previous twenty
  years' (at least fifteen of them), once in five years."
  [levels]
  (let [g (log-growth levels)]
    (once-in 5 (for [[y x] g
                     :let [past (keep #(get g %) (range (- y 20) y))]
                     :when (>= (count past) 15)
                     :let [m (/ (reduce + past) (count past))
                           sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) past)) (dec (count past))))]
                     :when (and (neg? x) (< x (- m (* 2.0 sd))))]
                 y))))

(defn join
  "Onset years of two catalogues, a year of the second dropped when the
  first has one within a year of it."
  [a b]
  (lb/join-years a b))

(defn consolidations
  "Years of spending-led fiscal consolidation from the balance and
  expenditure ({year % of GDP}): the balance up 1.5 points or more and
  expenditure down a point or more, once in three years."
  [bal exp]
  (once-in 3 (for [[y b] bal
                   :let [b0 (get bal (dec y)) e (get exp y) e0 (get exp (dec y))]
                   :when (and b0 e e0 (>= (- b b0) 1.5) (<= (- e e0) -1.0))]
               y)))

(defn hp-trend
  "The Hodrick-Prescott trend of `xs` at smoothing `lam`: (I + lam D'D)
  tau = x, D the second difference, solved by banded Gaussian elimination."
  [xs lam]
  (let [n (count xs)]
    (if (< n 3)
      (vec xs)
      (let [a (vec (for [_ (range n)] (double-array n)))
            b (double-array (map double xs))
            d [1.0 -2.0 1.0]]
        (dotimes [i n] (aset ^doubles (a i) i 1.0))
        (dotimes [r (- n 2)]
          (doseq [p (range 3) q (range 3)]
            (let [i (+ r p) j (+ r q)]
              (aset ^doubles (a i) j (+ (aget ^doubles (a i) j) (* lam (d p) (d q)))))))
        (dotimes [k n]
          (let [piv (aget ^doubles (a k) k)]
            (doseq [i (range (inc k) (min n (+ k 3)))]
              (let [f (/ (aget ^doubles (a i) k) piv)]
                (when-not (zero? f)
                  (doseq [j (range k (min n (+ k 3)))]
                    (aset ^doubles (a i) j (- (aget ^doubles (a i) j) (* f (aget ^doubles (a k) j)))))
                  (aset b i (- (aget b i) (* f (aget b k)))))))))
        (let [x (double-array n)]
          (doseq [i (range (dec n) -1 -1)]
            (aset x i (/ (- (aget b i) (reduce + 0.0 (for [j (range (inc i) (min n (+ i 3)))] (* (aget ^doubles (a i) j) (aget x j)))))
                         (aget ^doubles (a i) i))))
          (vec x))))))

(defn output-gap
  "{year gap, % of trend} from {year real GDP growth %}: 100 x the log
  level less its HP(100) trend, one-sided (bnm-7yg.8.1): each year's gap is
  the last point of the trend fitted to its run of consecutive years up to
  it, so later data never move it; a year needs ten such years. (t163 used
  the two-sided trend over each run; it reproduces from 8faaa2c.)"
  [growth]
  (let [ys (sort (keys growth))
        runs (reduce (fn [acc y] (if (and (seq acc) (= y (inc (peek (peek acc))))) (conj (pop acc) (conj (peek acc) y)) (conj acc [y])))
                     [] ys)]
    (into {} (for [r runs :when (>= (count r) 10)
                   :let [lv (vec (reductions + (map #(Math/log (+ 1.0 (/ (double (growth %)) 100.0))) r)))]
                   i (range 9 (count r))
                   :let [upto (subvec lv 0 (inc i))]]
               [(r i) (* 100.0 (- (peek upto) (peek (hp-trend upto 100.0))))]))))

(def ^:private cyclical-elasticity "The budget semi-elasticity (Price, Dang & Botev 2014)." 0.5)

(defn discretionary-consolidations
  "Years of discretionary, spending-led consolidation (D2, t163): the
  cyclically adjusted balance (balance - 0.5 x gap) up 1.5 points or more
  and real expenditure not above the year before, once in three years.
  `bal` and `gap` in % of GDP, `rexp` real spending, each {year value}."
  [bal gap rexp]
  (let [cab (fn [y] (when-let [b (get bal y)] (when-let [g (get gap y)] (- b (* cyclical-elasticity g)))))]
    (once-in 3 (for [y (keys bal)
                     :let [c (cab y) c0 (cab (dec y)) e (get rexp y) e0 (get rexp (dec y))]
                     :when (and c c0 e e0 (>= (- c c0) 1.5) (<= e e0))]
                 y))))

(defn discretionary-fiscal-catalogue
  "The D2 catalogue from {unit {:growth :balance :real-expenditure}}: onsets
  in January of the year, busy twelve months, each unit covered over the
  years D2 can judge (a year and the one before with the adjusted balance
  and real spending), known 18 months on, as the annual catalogues."
  [by-unit]
  (let [per (into {} (for [[u {:keys [growth balance real-expenditure]}] by-unit
                           :let [gap (output-gap growth)
                                 ok (fn [y] (and (get balance y) (get gap y) (get real-expenditure y)))
                                 ys (filter #(and (ok %) (ok (dec %))) (keys balance))]
                           :when (seq ys)]
                       [u {:ys ys :on (discretionary-consolidations balance gap real-expenditure)}]))]
    {:onsets (into {} (for [[u {:keys [on]}] per] [u (into (sorted-set) (map #(* 12 %) on))]))
     :busy (into {} (for [[u {:keys [on]}] per] [u (into (sorted-set) (mapcat #(range (* 12 %) (+ 12 (* 12 %))) on))]))
     :from (into {} (for [[u {:keys [ys]}] per] [u (* 12 (apply min ys))]))
     :through (into {} (for [[u {:keys [ys]}] per] [u (+ 11 (* 12 (apply max ys)))]))
     :known-lag 18}))

(defn with-discretionary-fiscal
  "`cats` with fiscal_consolidation the D2 catalogue `d` (world-run
  --fiscal-outcomes discretionary, t163) at the units the current catalogue
  covers; every other kind as it was."
  [cats d]
  (let [us (set (keys (get-in cats ["fiscal_consolidation" :from])))]
    (assoc cats "fiscal_consolidation"
           (reduce (fn [c k] (update c k #(select-keys % us))) d [:onsets :busy :from :through]))))

(defn onsets-of-runs "The first year of each run of years." [ys] (runs-start (set ys)))

(defn food-shocks
  "Quarters of food price shocks from {quarter twelve-month food inflation}:
  15% or more and 10 points above the mean of the previous twenty quarters
  (at least twelve of them), once in four quarters."
  [infl]
  (once-in 4 (for [[q x] infl
                   :let [past (keep #(get infl %) (range (- q 20) q))]
                   :when (and (>= (count past) 12) (>= x 15.0) (>= (- x (/ (reduce + past) (count past))) 10.0))]
               q)))

(defn quarterly-food-onsets
  "Months of food price shocks by the quarterly rule (`food-shocks`) from
  {month twelve-month food inflation}: each quarter reads its last month
  with data, and its onset is dated at that month (bnm-56c.32: it was the
  quarter's first month, up to two months before the data showing it)."
  [by-month]
  (let [last-month (reduce (fn [acc m] (assoc acc (quot m 3) m)) {} (sort (keys by-month)))
        infl (update-vals last-month by-month)]
    (set (map last-month (food-shocks infl)))))

(defn food-shocks-monthly
  "Months of food price shocks from {month twelve-month food inflation}
  (bnm-56c.30): the quarterly rule by month, 15% or more and 10 points
  above the mean of the previous sixty months (at least thirty-six of
  them), once in twelve months, dated at the month the rule first fires.
  It reads only that month and the ones before it."
  [infl]
  (once-in 12 (for [[m x] infl
                    :let [past (keep #(get infl %) (range (- m 60) m))]
                    :when (and (>= (count past) 36) (>= x 15.0) (>= (- x (/ (reduce + past) (count past))) 10.0))]
                m)))

(def food-publication-lag
  "Months after its month before a country's food CPI is published
  (bnm-56c.30). FAOSTAT publishes its monthly consumer price indices
  quarterly (resources/bnm/releases.tsv): the first month of a quarter
  waits two months for the quarter, then three to four months for the
  release (March 2023's in July, March 2025's in June), and the release
  of March 2026 came late (FAO's calendar: delayed; April 2026 was still
  out 6.5 months on). Nine months covers all three with a little to
  spare; vintages are not archived (bnm-09d)."
  9)

(defn food-known-at
  "The month a food CPI month `m` is read from: no earlier than published."
  [m]
  (+ m food-publication-lag))

(defn monthly-food-catalogue
  "The lattice's catalogue of food price shocks by month from {iso {month
  twelve-month food inflation}}: onsets at their month, busy three months,
  covered from the first month the rule can judge (36 months of past) to
  the last with data, known at the food CPI's publication lag."
  [by-iso]
  (let [by-iso (into {} (filter (comp seq val) by-iso))]
    {:onsets (into {} (for [[u infl] by-iso] [u (into (sorted-set) (food-shocks-monthly infl))]))
     :busy (into {} (for [[u infl] by-iso] [u (into (sorted-set) (mapcat #(range % (+ % 3)) (food-shocks-monthly infl)))]))
     :from (into {} (for [[u infl] by-iso] [u (+ 36 (apply min (keys infl)))]))
     :through (into {} (for [[u infl] by-iso] [u (apply max (keys infl))]))
     :known-lag food-publication-lag}))

(defn- by-month [db u v]
  (if-let [{:keys [period value]} (db [u v])]
    (reduce (fn [acc i] (assoc acc (long (nth period i)) (double (nth value i)))) {} (range (count period)))
    {}))

(defn with-monthly-food
  "`cats` with food price shocks by month from the db's monthly food
  inflation (FAOSTAT, food/food_inflation), at its publication lag
  (world-run --food-outcomes monthly); every other kind as it was."
  [cats db]
  (let [units (sort (set (keep (fn [[u v]] (when (= v "food/food_inflation") u)) (keys db))))]
    (assoc cats "food_price_shock"
           (monthly-food-catalogue (into {} (for [u units] [u (by-month db u "food/food_inflation")]))))))

(defn catalogue
  "The lattice's catalogue form for annual onsets, {iso {:onsets #{years}
  :from year :through year}}: onsets at the year's first month, busy for
  the year, known 18 months after the onset (the year's data published in
  the next). With :months true, onsets are already months, busy for :busy
  months each."
  ([by-iso] (catalogue by-iso {}))
  ([by-iso {:keys [months busy] :or {busy 12}}]
   (let [m (fn [x] (if months x (* 12 x)))]
     {:onsets (into {} (for [[u {:keys [onsets]}] by-iso] [u (into (sorted-set) (map m onsets))]))
      :busy (into {} (for [[u {:keys [onsets]}] by-iso] [u (into (sorted-set) (mapcat #(range (m %) (+ (m %) busy)) onsets))]))
      :from (into {} (for [[u {:keys [from]}] by-iso] [u (m from)]))
      :through (into {} (for [[u {:keys [through]}] by-iso] [u (if months through (+ 11 (* 12 through)))]))
      :known-lag 18})))

(defn read-catalogues
  "{kind lattice catalogue} from data/derived/econ-events.csv and
  econ-coverage.csv."
  []
  (let [ev (with-open [r (io/reader "data/derived/econ-events.csv")]
             (reduce (fn [acc l] (let [[k u m b] (str/split l #",")] (update-in acc [k u] (fnil conj []) [(parse-long m) (parse-long b)])))
                     {} (rest (line-seq r))))
        cov (with-open [r (io/reader "data/derived/econ-coverage.csv")]
              (reduce (fn [acc l] (let [[k u a b] (str/split l #",")] (assoc-in acc [k u] [(parse-long a) (parse-long b)])))
                      {} (rest (line-seq r))))]
    (into {} (for [k kinds]
               [k {:onsets (into {} (for [[u _] (cov k)] [u (into (sorted-set) (map first (get-in ev [k u])))]))
                   :busy (into {} (for [[u _] (cov k)] [u (into (sorted-set) (mapcat (fn [[m b]] (range m (+ m b))) (get-in ev [k u])))]))
                   :from (into {} (for [[u [a _]] (cov k)] [u a]))
                   :through (into {} (for [[u [_ b]] (cov k)] [u b]))
                   :known-lag 18}]))))

(defn- by-year
  "{year value} of a db series, each year's last observation, in the series'
  own units (exp for logged variables)."
  [db u v logged?]
  (if-let [{:keys [period value]} (db [u v])]
    (reduce (fn [acc i] (assoc acc (quot (long (nth period i)) 12) (let [x (double (nth value i))] (if logged? (Math/exp x) x))))
            {} (range (count period)))
    {}))

(defn by-year-inflation
  "{year percent} of a unit's consumer price inflation in the db, as -main
  reads it for the catalogue."
  [db u]
  (by-year db u "economy/inflation" false))

(defn per-head-levels
  "{year output per head} at `u`: Maddison's levels (`mad`, {year level}),
  WDI's growth of GDP per head chained on after Maddison ends; WDI's alone
  without Maddison. The annual growth-collapse catalogue's levels."
  [mad db u]
  (let [wdi-g (by-year db u "economy/gdp_per_capita" true)]
    (if (seq mad)
      (let [last-y (apply max (keys mad))]
        (reduce (fn [acc y] (if-let [g (and (get wdi-g y) (get wdi-g (dec y)) (/ (wdi-g y) (wdi-g (dec y))))]
                              (if-let [b (get acc (dec y))] (assoc acc y (* b g)) acc) acc))
                mad (range (inc last-y) 2026)))
      wdi-g)))

;; --- quarterly growth collapses (bnm-56c.37, docs/GROWTH-QUARTERLY.md) ---------

(def quarterly-growth-lag
  "Months after a quarter's last month before its GDP is read: OECD's first
  releases come 2-4 months on (5 at most, its real-time editions), the
  IMF's about 4 (docs/GROWTH-QUARTERLY.md 4)."
  5)

(defn quarterly-growth-collapses
  "Quarters (quarter index, 4 x year + quarter - 1) of growth collapses from
  {quarter per-head growth on the same quarter a year before} and the
  annual catalogue's {year log growth of output per head}: a quarter's
  growth negative and more than 2.5 standard deviations below the mean of
  the twenty years before the quarter's (at least fifteen of them), once in
  twenty quarters (the annual rule's once in five years)."
  [g annual-g]
  (once-in 20 (for [[i x] g
                    :let [past (keep #(get annual-g %) (range (- (quot i 4) 20) (quot i 4)))]
                    :when (and (neg? x) (>= (count past) 15))
                    :let [m (/ (reduce + past) (count past))
                          sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) past)) (dec (count past))))]
                    :when (< x (- m (* 2.5 sd)))]
                i)))

(defn quarterly-growth-catalogue
  "The lattice's catalogue of growth collapses by quarter from {iso {quarter
  per-head growth}} and {iso {year annual log growth}}: onsets at the
  quarter's last month (the month its data close, as quarterly-food-onsets),
  busy twelve months, covered from the first quarter the rule can judge (a
  growth and fifteen years of annual growth before it) to the last with
  data, known five months after the quarter. A unit the rule never judges
  is left out."
  [by-iso annual-by-iso]
  (let [judged (fn [g ag] (filter (fn [i] (>= (count (keep #(get ag %) (range (- (quot i 4) 20) (quot i 4)))) 15)) (keys g)))
        units (for [[u g] by-iso :let [ag (get annual-by-iso u {}) js (judged g ag)] :when (seq js)]
                [u (map #(+ 2 (* 3 %)) (quarterly-growth-collapses g ag)) (* 3 (apply min js)) (+ 2 (* 3 (apply max (keys g))))])]
    {:onsets (into {} (for [[u os] units] [u (into (sorted-set) os)]))
     :busy (into {} (for [[u os] units] [u (into (sorted-set) (mapcat #(range % (+ % 12)) os))]))
     :from (into {} (for [[u _ a _] units] [u a]))
     :through (into {} (for [[u _ _ b] units] [u b]))
     :known-lag quarterly-growth-lag}))

(defn read-quarterly-growth
  "{iso {quarter per-head growth on the year before}} from data/derived/
  gdp-quarterly.csv (scripts/quarterly-gdp.py)."
  ([] (read-quarterly-growth "data/derived/gdp-quarterly.csv"))
  ([path]
   (with-open [r (io/reader path)]
     (let [[h & ls] (line-seq r)
           ix (zipmap (str/split h #",") (range))]
       (reduce (fn [acc l]
                 (let [f (str/split l #",")
                       [y k] (str/split (f (ix "quarter")) #"Q")]
                   (assoc-in acc [(f (ix "iso")) (+ (* 4 (parse-long y)) (dec (parse-long k)))] (parse-double (f (ix "yoy_pc"))))))
               {} ls)))))

(defn with-quarterly-growth
  "`cats` with growth_collapse the quarterly catalogue `q` (world-run
  --growth-outcomes quarterly, bnm-56c.37): the units it covers only, every
  other kind as it was. A unit the annual catalogue does not cover is
  refused: the annual rule's line is what it reads."
  [cats q]
  (when-let [bad (seq (remove (set (keys (get-in cats ["growth_collapse" :from]))) (keys (:from q))))]
    (throw (ex-info "bnm.economic-events: quarterly growth at units the annual catalogue does not cover" {:units (vec bad)})))
  (assoc cats "growth_collapse" q))

(defn quarterly-growth
  "The quarterly growth-collapse catalogue at the annual catalogue's units
  (`cats`), from data/derived/gdp-quarterly.csv and the annual per-head
  levels (Maddison, then WDI, from the db)."
  [cats db]
  (let [units (keys (get-in cats ["growth_collapse" :from]))
        annual (pg/read-annual)]
    (quarterly-growth-catalogue (select-keys (read-quarterly-growth) units)
                                (into {} (for [u units] [u (log-growth (per-head-levels (get-in annual ["mad_gdppc" u] {}) db u))])))))

(defn discretionary-fiscal
  "The D2 catalogue (t163) at the current fiscal catalogue's units (`cats`),
  from GMD's balance and real spending (data/derived/fiscal.csv) and WDI's
  real GDP growth in `db`, read as -main reads the catalogues' series (no
  publication lags: an outcome's definition, on revised data)."
  [cats db]
  (let [fiscal (pg/read-annual "data/derived/fiscal.csv")
        units (keys (get-in cats ["fiscal_consolidation" :from]))]
    (discretionary-fiscal-catalogue
     (into {} (for [u units]
                [u {:growth (by-year db u "economy/gdp_growth" false)
                    :balance (get-in fiscal ["gov_bal_gdp" u] {})
                    :real-expenditure (get-in fiscal ["gov_exp_real" u] {})}])))))

(defn- read-rr-defaults
  "{iso #{years}} of sovereign default onsets and the years each country is
  covered, Reinhart & Rogoff (data/raw/slow/rr/onsets.csv)."
  []
  (with-open [r (io/reader "data/raw/slow/rr/onsets.csv")]
    (let [[h & ls] (line-seq r)
          cols (zipmap (str/split h #",") (range))
          rows (for [l ls :let [f (str/split l #",")]] [(f 0) (parse-long (f 1)) (= "1" (f (cols "default"))) (= "1" (f (cols "default_known")))])]
      [(reduce (fn [acc [u y d]] (if d (update acc u (fnil conj #{}) y) acc)) {} rows)
       (reduce (fn [acc [u y _ k]] (if k (update acc u (fnil conj #{}) y) acc)) {} rows)])))

(defn- lv [kind]
  (with-open [r (io/reader "data/derived/lv2018.csv")]
    (reduce (fn [acc l] (let [[u k y] (str/split l #",")] (if (= k kind) (update acc u (fnil conj #{}) (parse-long y)) acc)))
            {} (rest (line-seq r)))))

(defn -main [& _]
  (let [db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))
        units (sort (keys (pg/read-states)))
        annual (pg/read-annual)
        fiscal (pg/read-annual "data/derived/fiscal.csv")
        span (fn [ys lead] (when (seq ys) [(+ lead (apply min ys)) (apply max ys)]))
        out (atom []) cov (atom [])
        add! (fn [k u onsets [a b] & [months busy]]
               (when (and a b (<= a b))
                 (swap! cov conj [k u a b months])
                 (doseq [o onsets :when (<= a o b)] (swap! out conj [k u o (or busy 12) months]))))]
    (doseq [u units]
      (let [fx (by-year db u "finance/fx_per_usd" true)
            infl (by-year db u "economy/inflation" false)
            levels (per-head-levels (get-in annual ["mad_gdppc" u] {}) db u)
            [rr rr-known] (read-rr-defaults)
            sov-lv (merge-with into (lv "sovereign") (lv "restructuring"))
            food (by-month db u "food/food_inflation")
            bal (get-in fiscal ["gov_bal_gdp" u] {})
            gexp (get-in fiscal ["gov_exp_gdp" u] {})
            gmd-sov (onsets-of-runs (keys (get-in fiscal ["gmd_sovereign" u] {})))]
        (add! "currency_crisis" u (lb/currency-crises fx) (span (keys fx) 2))
        (add! "inflation_crisis" u (inflation-crises infl) (span (keys infl) 1))
        (add! "growth_collapse" u (growth-collapses levels) (span (keys levels) 16))
        ;; Laeven & Valencia cover every IMF member 1970-2017, the Global Macro
        ;; Database to 2023; Reinhart & Rogoff reach further back for their seventy
        (let [known (get rr-known u #{})]
          (add! "sovereign_default" u (join (join (get sov-lv u #{}) (get rr u #{})) gmd-sov)
                [(if (seq known) (min 1970 (apply min known)) 1970) 2023]))
        (let [ys (filter #(and (get bal %) (get bal (dec %)) (get gexp %) (get gexp (dec %))) (keys bal))]
          (add! "fiscal_consolidation" u (consolidations bal gexp) (span ys 0)))
        (when (seq food)
          (let [qs (map #(quot % 3) (keys food))]
            (add! "food_price_shock" u (quarterly-food-onsets food) [(* 3 (+ 12 (apply min qs))) (+ 2 (* 3 (apply max qs)))] true 3)))))
    (with-open [w (io/writer "data/derived/econ-events.csv")]
      (.write w "kind,iso,month,busy\n")
      (doseq [[k u o busy months] (sort @out)]
        (.write w (str k "," u "," (if months o (* 12 o)) "," busy "\n"))))
    (with-open [w (io/writer "data/derived/econ-coverage.csv")]
      (.write w "kind,iso,from,through\n")
      (doseq [[k u a b months] (sort @cov)]
        (.write w (str k "," u "," (if months a (* 12 a)) "," (if months b (+ 11 (* 12 b))) "\n"))))
    (doseq [k kinds]
      (println (format "%-18s %4d onsets in %3d countries" k (count (filter #(= k (first %)) @out))
                       (count (set (map second (filter #(= k (first %)) @out)))))))))
