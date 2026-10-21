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

(defn- once-in [n years]
  (loop [[y & ys] (sort years) last-y nil out #{}]
    (if-not y out
      (let [ok (or (nil? last-y) (>= (- y last-y) n))]
        (recur ys (if ok y last-y) (if ok (conj out y) out))))))

(defn growth-collapses
  "Years whose growth of output per head ({year level}) is negative and more
  than two standard deviations below the mean of the previous twenty
  years' (at least fifteen of them), once in five years."
  [levels]
  (let [g (into {} (for [[y x] levels :let [b (get levels (dec y))] :when (and b (pos? b) (pos? x))] [y (Math/log (/ x b))]))]
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

(defn- by-quarter [db u v]
  (if-let [{:keys [period value]} (db [u v])]
    (reduce (fn [acc i] (assoc acc (quot (long (nth period i)) 3) (double (nth value i)))) {} (range (count period)))
    {}))

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
            mad (get-in annual ["mad_gdppc" u] {})
            wdi-g (by-year db u "economy/gdp_per_capita" true)
            ;; Maddison's levels, WDI's growth chained on after Maddison ends
            levels (if (seq mad)
                     (let [last-y (apply max (keys mad))]
                       (reduce (fn [acc y] (if-let [g (and (get wdi-g y) (get wdi-g (dec y)) (/ (wdi-g y) (wdi-g (dec y))))]
                                             (if-let [b (get acc (dec y))] (assoc acc y (* b g)) acc) acc))
                               mad (range (inc last-y) 2026)))
                     wdi-g)
            [rr rr-known] (read-rr-defaults)
            sov-lv (merge-with into (lv "sovereign") (lv "restructuring"))
            food (by-quarter db u "food/food_inflation")
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
          (let [qs (keys food)]
            (add! "food_price_shock" u (map #(* 3 %) (food-shocks food)) [(* 3 (+ 12 (apply min qs))) (+ 2 (* 3 (apply max qs)))] true 3)))))
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
