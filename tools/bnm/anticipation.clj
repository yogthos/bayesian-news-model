(ns bnm.anticipation
  "Whether the onset models anticipate, scored as the epic's acceptance
  test fixes it in advance (docs/PHASE-CHANGES.md): every model against the
  country's own onset history at fixed alert budgets, out of sample, in
  2003-14 and 2015-26 separately, over all country-quarters and over the
  hard ones (no onset of the kind in ten years), with 90% intervals
  resampling whole years, and matched-control concordance (each onset
  against the four non-onsets of its quarter that history rates closest: 0.5
  is knowing only what history knows).

    jolt -A:tools -M -m bnm.anticipation [--run onsets-live] [--kinds a,b] [--bench history]
                                         [--level country|subregion|region]
                                         [--tests m] [--confirm true] [--split world] [--threads n]

  --bench sets the model every other is scored against: a group's placebo
  (ties-placebo) asks whether the group knows anything about the country.
  --level scores forecasts for groups of countries, the chance of an onset
  in any of them (bnm.onset/aggregate, as if independent), and the
  forecasts made for the groups themselves in preds-<level>.tsv beside the
  run's (bnm.dynamic, with states the members share): methods for waves
  and contagion may work for a region before they work for a country. --tests m widens the
  recall intervals for m comparisons (Bonferroni). Eras are 2003-14 and
  2015-22; 2023 on is locked for confirmation (--confirm true). --split
  world scores calm and busy world quarters apart.

  Reads data/runs/<run>/preds.tsv; writes anticipation.txt beside it."
  (:require [augur.score :as sc]
            [bnm.geo :as geo]
            [bnm.onset :as o]
            [bnm.onsets :as onsets]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def eras
  "The eras adoption decisions are scored on: rows from these years whose
  labels resolve before `lock` (window and reporting lag, bnm.onset/in-era?).
  The rest are the confirmation period, locked from October 2026: scored
  once, with --confirm, for the record chosen on these."
  [["03-14" 2003 2014] ["15-22" 2015 2022]])

(def confirmation [["23-26" 2023 2026]])

(def lock "The first year whose outcomes are held for confirmation." 2023)

(def default-bench "history")

(defn- read-preds
  "{[kind window] {model {[unit q] [p y]}}}, resolved rows only."
  [path kinds]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[kind w m q unit p y] (str/split line #"\t")]
                (if (and (#{"true" "false"} y) (or (nil? kinds) (kinds kind)) (not= m "base"))
                  (assoc-in acc [[kind w] m [unit (parse-long q)]] [(parse-double p) (= "true" y)])
                  acc)))
            {} (line-seq r))))

(defn- f3 [x] (if x (format "%+.3f" (double x)) "   -  "))

(defn- line-for
  "One model's scores against the bench on `keys` ([unit q]). Recall gains'
  intervals are 1 - alpha; loro is the lowest of their lower bounds with
  each region left out in turn (`region-of` units, nil to skip)."
  [model-rows bench-rows keys alpha region-of]
  (let [keyed (vec (for [k keys
                         :let [[pa y] (model-rows k) [pb _] (bench-rows k)]
                         :when (and pa pb)]
                     [(first k) [(second k) pa pb y (first k)]]))
        rows (mapv second keyed)
        pos (count (filter #(nth % 3) rows))
        reps (max 200 (long (Math/ceil (/ 10.0 (/ alpha 2)))))
        ;; countries and years resampled both: a country's overlapping
        ;; windows and a year's shared shocks are not independent draws
        gain (fn [rs budget] (sc/paired-recall-gain-2way rs budget {:reps reps :seed 1 :alpha alpha :per-block 4}))]
    (when (and (pos? pos) (< pos (count rows)))
      (let [r5 #(sc/recall-at-budget % 0.05)
            r10 #(sc/recall-at-budget % 0.10)
            u (fn [rs] (sc/usefulness (map second rs) (map #(nth % 2) rs) 0.9))
            loro (when region-of
                   (let [regions (distinct (keep (comp region-of first) keyed))
                         without (fn [r] (vec (for [[unit row] keyed :when (not= r (region-of unit))] row)))
                         lows (for [r regions :let [rs (without r)] :when (some #(nth % 3) rs)]
                                [(:lo (gain rs 0.05)) (:lo (gain rs 0.10))])]
                     (when (seq lows) [(apply min (map first lows)) (apply min (map second lows))])))]
        {:n (count rows) :pos pos :loro loro
         :r5 (r5 (map (fn [[q a _ y]] [q a y]) rows)) :r5-gain (gain rows 0.05)
         :r10 (r10 (map (fn [[q a _ y]] [q a y]) rows)) :r10-gain (gain rows 0.10)
         :u (u (map (fn [[q a _ y]] [q a y]) rows)) :u-gain (sc/paired-usefulness-gain rows 0.9 {:reps 200 :seed 1 :per-block 4})
         ;; error diagrams: alarm volume by rows, and by the benchmark's
         ;; probabilities (0.5 = no better than the benchmark)
         :ass (sc/area-skill (map (fn [[q a _ y]] [q a y 1.0]) rows))
         :ass-bench (sc/area-skill (map (fn [[q a b y]] [q a y b]) rows))
         :matched (sc/matched-concordance (map (fn [[q a b y]] {:period q :p-model a :p-bench b :y y}) rows) 4)}))))

(defn- group-map
  "{unit group} at `level` (subregion or region, the M49 groups of
  bnm.geo), nil at the country level."
  [level units]
  (case level
    "country" nil
    "subregion" (into {} (for [u units :let [s (first (geo/parents u))] :when s] [u s]))
    "region" (into {} (for [u units :let [s (first (geo/parents u))] :when s] [u (first (geo/parents s))]))))

(defn -main [& args]
  (let [{:keys [run kinds bench level tests confirm split threads]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        threads (or (some-> threads parse-long) 4)
        ;; Bonferroni over the groups and models tried for the same cells
        alpha (/ 0.1 (double (or (some-> tests parse-long) 1)))
        eras (if (= confirm "true") confirmation eras)
        bench (or bench default-bench)
        level (or level "country")
        dir (str "data/runs/" (or run "onsets-live"))
        kinds (when kinds (set (str/split kinds #",")))
        group-of (group-map level (set (for [[_ by-model] (read-preds (str dir "/preds.tsv") kinds)
                                              [_ rows] by-model [unit _] (keys rows)] unit)))
        region-of (group-map "region" (set (for [[_ by-model] (read-preds (str dir "/preds.tsv") kinds)
                                                 [_ rows] by-model [unit _] (keys rows)] unit)))
        group-file (io/file (str dir "/preds-" level ".tsv"))
        preds (cond->> (read-preds (str dir "/preds.tsv") kinds)
                group-of (into {} (map (fn [[kw by-model]] [kw (update-vals by-model #(o/aggregate % group-of))])))
                ;; forecasts made for the groups themselves (bnm.dynamic --levels)
                (and group-of (.exists group-file))
                (merge-with merge (read-preds (str group-file) kinds)))
        lags (:lags (onsets/read-event-sets "data/slow/events.tsv"))
        events (cond->> (onsets/read-events "data/slow/events.tsv")
                 group-of (into {} (map (fn [[kind by-unit]]
                                          [kind (reduce-kv (fn [acc unit qs] (update acc (get group-of unit unit) (fnil into (sorted-set)) qs))
                                                           {} by-unit)]))))
        ;; --split world: each block for calm and busy world quarters, busy
        ;; when more onsets of every kind were known worldwide over the
        ;; last year than in the median quarter (weather's windows of
        ;; opportunity: skill that comes with the state)
        state-of (when (= split "world")
                   (let [known (:known (onsets/read-event-sets "data/slow/events.tsv"))
                         ts (sort (for [[_ by-unit] known [_ qs] by-unit t qs] t))
                         count-at (fn [q] (- (count (take-while #(<= % q) ts)) (count (take-while #(<= % (- q 4)) ts))))
                         qs (range (* 4 2000) (* 4 2027))
                         counts (zipmap qs (map count-at qs))
                         median (nth (sort (vals counts)) (quot (count counts) 2))]
                     (fn [q] (if (> (get counts q 0) median) "busy" "calm"))))
        gain (fn [{:keys [diff lo hi]}] (format "%s [%s %s]%s" (f3 diff) (f3 lo) (f3 hi) (if (pos? lo) "*" " ")))
        header (str (format "Anticipation: each model against %s (out of sample).\n" bench)
                    "Recall in the top 5% and 10% of each quarter and Sarlin usefulness (mu 0.9), each with its gain over\n"
                    "history and the gain's interval resampling countries and years (* when it excludes zero); matched: the share\n"
                    "of onsets ranked above the four non-onsets of their quarter that history rates closest (0.5 = no more\n"
                    "than history); ass, the area skill of the error diagram (0.5 a random order), and ass/b with alarm\n"
                    "volume counted in the benchmark's probabilities (0.5 = no better than the benchmark); loro, the\n"
                    "lowest lower bound of the r@5% and r@10% gains with each region left out. hard: no onset of the\n"
                    "kind in the ten years before.\n"
                    (format "Recall intervals at %.1f%%%s.\n" (* 100 (- 1.0 alpha))
                            (if tests (str ", Bonferroni over " tests " tests") ""))
                    (if (= confirm "true") "Confirmation period (locked; scored once).\n" ""))
        ;; each block (kind, window, era, hard or all) a task, run on up to
        ;; --threads threads (4) and printed in order as they finish: a
        ;; thread per block starves every other job on the machine
        tasks (vec (for [[[kind w] by-model] (sort-by key preds)
                     :let [b (get by-model bench)]
                     :when b
                     [era lo hi] eras
                     state (if state-of ["calm" "busy"] [nil])
                     hard? [false true]]
                 (fn []
                   (let [window (onsets/windows (keyword w))
                         lag (get lags kind 0)
                         in (filter (fn [[unit q]] (and (o/in-era? q window lag [lo hi] lock (= confirm "true"))
                                                        (or (nil? state) (= state (state-of q)))
                                                        (or (not hard?)
                                                            (onsets/hard-row? (get-in events [kind unit] (sorted-set)) q))))
                                    (keys b))]
                     (with-out-str
                       (println (format "%s %s %s%s%s" kind w era (if state (str "/" state) "") (if hard? " hard" "")))
                       (doseq [[m rows] (sort-by key by-model)
                               :when (not= m bench)
                               :let [s (line-for rows b in alpha (when-not group-of region-of))]
                               :when s]
                         (println (str (format "  %-8s n=%6d pos=%4d  r@5%% %.3f %s  r@10%% %.3f %s  U %.3f %s  matched %.2f  ass %.3f ass/b %.3f"
                                          m (:n s) (:pos s) (double (:r5 s)) (gain (:r5-gain s)) (double (:r10 s)) (gain (:r10-gain s))
                                          (double (:u s)) (gain (:u-gain s)) (double (or (:matched s) 0.5))
                                          (double (:ass s)) (double (:ass-bench s)))
                                  (if-let [[a b] (:loro s)] (format "  loro %s %s" (f3 a) (f3 b)) "")))))))))
        blocks (vec (repeatedly (count tasks) promise))
        next-i (atom -1)
        _ (dotimes [_ (min threads (count tasks))]
            (future (loop []
                      (let [i (swap! next-i inc)]
                        (when (< i (count tasks))
                          (deliver (blocks i) (try ((tasks i)) (catch Throwable e (str "block failed: " e "\n"))))
                          (recur))))))
        out (StringBuilder. header)
        _ (print header)
        _ (doseq [blk blocks]
            (let [s @blk] (print s) (flush) (.append out s)))
        out (str out)
        file (str "anticipation" (when (not= bench default-bench) (str "-vs-" bench))
                  (when group-of (str "-" level))
                  (when (= confirm "true") "-confirm")
                  (when tests (str "-tests" tests))
                  (when split (str "-" split)) ".txt")]
    (print out)
    (spit (str dir "/" file) out)))
