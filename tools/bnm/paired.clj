(ns bnm.paired
  "The acceptance test as amended in October 2026 (docs/PHASE-CHANGES.md):
  every model against a benchmark on the hard rows (no onset of the kind in
  ten years) of the counted cells (lead and w2, eras 2003-14 and 2015-22).

  - Primary: the paired difference in mean Brier score, pooled over the
    cells with each kind weighted equally, its interval resampling
    countries and blocks of years both (augur.score/paired-loss-2way); a
    gain when the one-sided 95% bound is above zero. The log score, floored
    at 1e-4, beside it.
  - Per cell: Romano-Wolf stepdown over the cells' Brier differences
    (family-wise 5%).
  - Timing, secondary: recall at a 10% alert budget pooled over every
    quarter of a cell, which rewards knowing when as well as who, and the
    between-quarter calibration error (each quarter's mean forecast
    against its rate).

  The recall-within-quarter test (bnm.anticipation, scripts/scoreboard.py)
  is still run and reported for every trial.

    jolt -A:tools -M -m bnm.paired --run r9 --bench named [--models a,b] [--kinds a,b]
                                   [--reps 400] [--simulate 0.25,0.5,1.0 --sims 20]
    jolt -A:tools -M -m bnm.paired --run lattice-a --bench lattice-b --scale year [--from 2003 --to 2022] [--alpha 0.1]

  --simulate replaces the outcomes with draws from the benchmark's
  probabilities shifted by a signal of size beta that only the model knows
  (shared within a country's year), and reports how often each test finds
  it: the power of the old and new tests, from no real outcome.

  Writes paired-vs-<bench>.txt (or power-vs-<bench>.txt) beside the run."
  (:require [augur.math :as m]
            [augur.score :as sc]
            [bnm.anticipation :as an]
            [bnm.onset :as o]
            [bnm.onsets :as onsets]
            [clojure.string :as str]))

(def counted-windows #{"lead" "w2"})

(def floor "Log-score floor on probabilities." 1e-4)

(defn brier ^double [^double p y] (let [d (- p (if y 1.0 0.0))] (* d d)))

(defn log-loss ^double [^double p y]
  (- (Math/log (Math/max floor (if y p (- 1.0 p))))))

(defn timing-error
  "The between-quarter calibration error of rows [q p y]: each quarter's
  mean forecast against its onset rate, squared, weighted by its rows."
  [rows]
  (let [by (group-by first rows)
        n (count rows)]
    (/ (reduce + (for [[_ rs] by
                       :let [k (count rs)
                             p (/ (reduce + (map second rs)) k)
                             r (/ (count (filter #(nth % 2) rs)) (double k))]]
                   (* k (let [d (- p r)] (* d d)))))
       (double n))))

(defn pooled-recall
  "The share of onsets among the top `budget` of rows [q p y] by
  probability, pooled over quarters: ties at the cutoff share the slots."
  [rows budget]
  (let [rs (vec (map (fn [[_ p y]] [0 p y]) rows))
        {:keys [hits pos]} (let [[h p] (first (vals (sc/recall-parts rs budget)))] {:hits h :pos p})]
    (when (pos? pos) (/ hits (double pos)))))

(defn simulate
  "Cells {[kind window era] [{:unit :q :pb}]} with synthetic outcomes: a
  signal z shared by a kind, unit and year (whatever the window) shifts the
  benchmark's log odds by beta z, and the model knows it (:pm). One uniform
  draw per kind, unit and quarter decides every window's outcome (:y), so
  a kind's windows move together as their overlapping labels do. With
  `informative?` the outcome follows the model; without, it follows the
  bench and the shift is noise (the test's size)."
  [cells beta g informative?]
  (let [zs (atom {}) us (atom {})
        draw (fn [a k f] (or (@a k) (let [v (f)] (swap! a assoc k v) v)))]
    (into {} (for [[[kind :as cell] rows] (sort-by key cells)]
               [cell (vec (for [{:keys [unit q pb] :as r} rows
                                :let [z (draw zs [kind unit (quot q 4)] #(m/normal! g))
                                      pm (if (zero? beta) pb (m/expit (+ (m/logit pb) (* beta z))))
                                      u (draw us [kind unit q] #(m/next-double! g))]]
                            (assoc r :pm pm :y (< u (if informative? pm pb)))))]))))

(defn- hard-rows
  "{[kind window era] [{:unit :q :pm :pb :y}]} on the counted cells' hard
  rows for one model against the bench."
  [preds model bench events lags]
  (into {} (for [[[kind w] by-model] preds
                 :when (counted-windows w)
                 :let [a (get by-model model) b (get by-model bench)]
                 :when (and a b)
                 [era lo hi] an/eras
                 :let [window (onsets/windows (keyword w))
                       lag (get lags kind 0)
                       rows (vec (for [[[unit q] [pb y]] b
                                       :let [[pm] (a [unit q])]
                                       :when (and pm (o/in-era? q window lag [lo hi] an/lock false)
                                                  (onsets/hard-row? (get-in events [kind unit] (sorted-set)) q))]
                                   {:unit unit :q q :pm pm :pb pb :y y}))]
                 :when (some :y rows)]
             [[kind w era] rows])))

(defn- primary [cells loss reps]
  (sc/paired-loss-2way (for [[[kind] rows] cells, {:keys [unit q pm pb y]} rows] [q pm pb y unit kind])
                       loss {:reps reps :seed 1 :alpha 0.1 :per-block 4}))

(defn- per-cell [cells reps]
  (sc/romano-wolf (for [[cell rows] cells, {:keys [unit q pm pb y]} rows] [q pm pb y unit cell])
                  brier {:reps reps :seed 2 :alpha 0.05 :per-block 4 :cell #(nth % 5)}))

(defn- old-test
  "Whether any cell passes the recall-within-quarter gain at 10% with the
  Bonferroni interval over `tests` (the original test without its
  leave-one-region-out and matched checks, so if anything kinder)."
  [cells tests]
  (let [alpha (/ 0.1 tests)]
    (some (fn [[_ rows]]
            (pos? (:lo (sc/paired-recall-gain-2way (for [{:keys [unit q pm pb y]} rows] [q pm pb y unit])
                                                   0.10 {:reps (max 200 (long (Math/ceil (/ 10.0 (/ alpha 2))))) :seed 3 :alpha alpha :per-block 4}))))
          cells)))

(defn read-lattice
  "{[place origin] [p y]} of a lattice run's country forecasts at `scale`,
  resolved only, from origins in the years `from` to `to`: the quarter nodes'
  at every quarter end, the year's and coarser nodes' at the end of each
  year."
  [run scale from to]
  (let [quarter? (str/starts-with? scale "quarter")]
    (with-open [r (clojure.java.io/reader (str "data/runs/" run "/forecasts.tsv"))]
      (reduce (fn [acc line]
                (let [[s place o p y] (str/split line #"\t")
                      o (parse-long o)]
                  (if (and (= s scale) (= 3 (count place)) (#{"true" "false"} y)
                           (= (if quarter? 2 11) (mod o (if quarter? 3 12))) (<= from (quot o 12) to))
                    (assoc acc [place o] [(parse-double p) (= y "true")])
                    acc)))
              {} (line-seq r)))))

(defn lattice-pair
  "Paired Brier and log score of two lattice runs' country forecasts at one
  scale on their own outcomes, years resampled with countries."
  [model bench scale from to reps & [alpha]]
  (let [alpha (or alpha 0.1)
        a (read-lattice model scale from to)
        b (read-lattice bench scale from to)
        ;; resampled by the origin's year with the countries
        rows (for [[k [pa y]] a :let [[pb _] (b k)] :when pb] [(quot (second k) 12) pa pb y (first k) scale])]
    {:n (count rows) :pos (count (filter #(nth % 3) rows))
     :brier (sc/paired-loss-2way rows brier {:reps reps :seed 1 :alpha alpha :per-block 1})
     :log (sc/paired-loss-2way rows log-loss {:reps reps :seed 1 :alpha alpha :per-block 1})}))

(defn- fmt [{:keys [diff lo hi]}] (format "%+.5f [%+.5f %+.5f]%s" diff lo hi (if (pos? lo) " GAIN" "")))

(defn -main [& args]
  (let [{:keys [run bench models kinds reps sims tests scale from to alpha] betas-arg :simulate} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        dir (str "data/runs/" run)
        reps (or (some-> reps parse-long) 400)
        kinds (when kinds (set (str/split kinds #",")))
        preds (an/read-preds (str dir "/preds.tsv") kinds)
        events (onsets/read-events "data/slow/events.tsv")
        lags (:lags (onsets/read-event-sets "data/slow/events.tsv"))
        all-models (sort (disj (set (for [[_ by-model] preds m (keys by-model)] m)) bench))
        models (if models (str/split models #",") all-models)
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (.append out (str s "\n"))))]
    (cond
      ;; two lattice runs at one scale: --run model-run --bench bench-run --scale year
      scale
      (let [{:keys [n pos brier log]} (lattice-pair run bench scale (or (some-> from parse-long) 2003) (or (some-> to parse-long) 2022) reps
                                                 (some-> alpha parse-double))]
        (say (format "%s against %s, %s nodes' country forecasts on their own outcomes, %s-%s: n=%d pos=%d"
                     run bench scale (or from "2003") (or to "2022") n pos))
        (say "  Brier     " (fmt brier))
        (say "  log score " (fmt log)))
      betas-arg
      (let [betas (map parse-double (str/split betas-arg #","))
            sims (or (some-> sims parse-long) 20)
            tests (or (some-> tests parse-long) 33)
            cells (hard-rows preds bench bench events lags)]
        (say (format "Power against %s on %d cells' hard rows, %d simulations per beta: the share finding the signal." bench (count cells) sims))
        (say "Null rows: the outcome ignores the model's shift, so the share is the test's false-positive rate.")
        (say "beta        new primary (Brier)   new per-cell (any, Romano-Wolf)   old (any cell, recall within quarter, Bonferroni" tests ")")
        (doseq [beta betas
                informative? [false true]]
          (let [g (m/rng 42)
                res (vec (for [_ (range sims)]
                           (let [sim (simulate cells beta g informative?)]
                             [(pos? (:lo (primary sim brier 200)))
                              (boolean (some :reject (vals (per-cell sim 200))))
                              (boolean (old-test sim tests))])))
                share (fn [i] (/ (count (filter #(nth % i) res)) (double sims)))]
            (say (format "%.2f %-6s %.2f                  %.2f                              %.2f"
                         beta (if informative? "signal" "null") (share 0) (share 1) (share 2)))))
        (spit (str dir "/power-vs-" bench ".txt") (str out)))
      :else
      (do
        (say (format "Paired scores against %s on hard rows of the counted cells (lead, w2; 2003-14, 2015-22)." bench))
        (say "Differences are bench loss minus model loss: positive favours the model. Primary: Brier, kinds weighted")
        (say "equally, 90% two-way interval (GAIN when its lower bound, the one-sided 95% bound, is above zero).")
        (doseq [model models
                :let [cells (hard-rows preds model bench events lags)]
                :when (seq cells)]
          (say)
          (say model)
          (say "  primary Brier " (fmt (primary cells brier reps)))
          (say "  log score     " (fmt (primary cells log-loss reps)))
          (let [rw (per-cell cells reps)]
            (doseq [[[kind w era :as cell] rows] (sort-by key cells)
                    :let [{:keys [diff p-adj reject]} (rw cell)
                          r (fn [k] (pooled-recall (map (fn [row] [(:q row) (k row) (:y row)]) rows) 0.10))
                          te (fn [k] (timing-error (map (fn [row] [(:q row) (k row) (:y row)]) rows)))]]
              (say (format "  %-15s %-4s %s n=%6d pos=%4d  Brier %+.5f p-adj %.3f%s  pooled r@10%% %.3f vs %.3f  timing err %.5f vs %.5f"
                           kind w era (count rows) (count (filter :y rows)) diff p-adj (if reject " *" "  ")
                           (double (or (r :pm) 0.0)) (double (or (r :pb) 0.0)) (te :pm) (te :pb))))))
        (spit (str dir "/paired-vs-" bench ".txt") (str out))))))
