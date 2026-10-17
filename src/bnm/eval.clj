(ns bnm.eval
  "Scoring forecasts against what happened, the way the early-warning
  literature does it (see docs/RESEARCH.md): most escalation is continuation,
  so a score pooled over everything mostly rewards persistence. Forecasts are
  scored by stratum, against benchmarks that know only a node's history.

  A forecast (from `bnm.model/forecast`) gives, per node and channel, a
  sample of totals over its horizon. The context it is scored in holds what
  was known at its origin: each node's totals over the last
  `history-length` periods, and the climatology of escalation learned from
  the periods scored before.

  - Strata, by a node's peak rate over its history: quiet (below
    `quiet-rate` per bin: an escalation from here is an onset), low, and
    active.
  - Totals: mean absolute error in log1p space of the forecast mean, beside
    persistence (the last period) and the mean of the last three periods;
    CRPS of the forecast sample in log1p space, beside the CRPS of the
    node's own history taken as a sample (the conflict literature's
    bootstrap benchmark); 80% interval coverage.
  - Escalation: a channel's total reaches `escalation-ratio` times the last
    period's, and grows by at least `escalation-floor` per bin. Its forecast
    probability is scored beside two benchmarks: climatology (the rate of
    escalation in the node's level, channel and stratum over earlier
    periods) and history (the node's own rate of escalating, shrunk to
    climatology). Brier skill against climatology, ROC AUC, average
    precision (PR AUC, which suits rare onsets), and precision and recall
    among the top `top-k` per period."
  (:require [bnm.calibrate :as cal]
            [bnm.geo :as geo]
            [bnm.math :as m]
            [bnm.model :as model]
            [bnm.ontology :as ont]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def escalation-ratio 1.5)

(def escalation-floor
  "An escalation must add at least this much per bin of the horizon
  (normalized units: about two over a year, two thousandths of a typical
  week's reports), so noise in tiny counts does not count."
  0.04)

(defn escalated? [a b horizon]
  (and (>= a (* escalation-ratio b)) (>= (- a b) (* escalation-floor horizon))))

(def escalation-channels [:violence :protest :coercion :verbal-conflict])

(def history-length
  "Periods of history kept per node for the strata and the benchmarks."
  6)

(def quiet-rate
  "Below this peak rate per bin over its history a node is quiet."
  0.1)

(def active-rate
  "From this peak rate per bin a node is active."
  1.0)

(def strata [:quiet :low :active])

(def top-k 10)

(defn stratum [peak horizon]
  (let [r (/ peak (double horizon))]
    (cond (< r quiet-rate) :quiet
          (< r active-rate) :low
          :else :active)))

;; --- benchmarks --------------------------------------------------------------------------

(defn climate-rate
  "The rate of escalation seen in `level`, `channel` and `stratum` over the
  periods scored so far, starting from 0.1."
  [clim level channel st]
  (let [[pos n] (get clim [level channel st] [0.0 0.0])]
    (/ (+ pos 0.1) (+ n 1.0))))

(defn learn-climate
  "`clim` updated with a period's scored rows."
  [clim scored]
  (reduce (fn [clim {:keys [level channel stratum escalated]}]
            (update clim [level channel stratum]
                    (fn [[pos n]] [(+ (or pos 0.0) (if escalated 1.0 0.0)) (+ (or n 0.0) 1.0)])))
          clim scored))

(defn history-rate
  "How often `series` (oldest first) escalated from one period to the next,
  with two pseudo-periods at `prior`."
  [series horizon prior]
  (let [k (count (filter true? (map (fn [b a] (escalated? a b horizon)) series (rest series))))]
    (/ (+ k (* 2.0 prior)) (+ (dec (count series)) 2.0))))

(defn add-history
  "`hist` with a period's `actual` totals appended for every node in either,
  zeros for a node with no events, keeping the last `history-length`."
  [hist actual]
  (let [zero (double-array model/C)]
    (reduce (fn [h id]
              (let [v (conj (get h id []) (get actual id zero))]
                (assoc h id (if (> (count v) history-length) (subvec v 1) v))))
            hist (distinct (concat (keys hist) (keys actual))))))

;; --- scores ------------------------------------------------------------------------------

(defn- crps
  "CRPS of the sorted sample `xs` for the outcome `a`."
  [^doubles xs a]
  (let [n (alength xs)
        s1 (areduce xs i s 0.0 (+ s (Math/abs (- (aget xs i) a))))
        s2 (areduce xs i s 0.0 (+ s (* (aget xs i) (- (* 2 i) n -1))))]
    (- (/ s1 n) (/ s2 (* n (double n))))))

(defn pit-bounds
  "[F(a-) F(a)]: the predictive cdf just below and at the outcome a, from
  the sorted sample xs. For counts the PIT is an interval, not a point; its
  non-randomized histogram spreads each outcome uniformly over it (Czado,
  Gneiting & Held 2009)."
  [^doubles xs a]
  (let [n (alength xs)
        below (areduce xs i c 0 (if (< (aget xs i) a) (inc c) c))
        at (areduce xs i c 0 (if (<= (aget xs i) a) (inc c) c))]
    [(/ below (double n)) (/ at (double n))]))

(defn pit-histogram
  "The non-randomized PIT histogram over `bins` equal bins: the mean over
  outcomes of the mass each [lo hi] puts in each bin. Flat when the
  forecasts are calibrated; a U when they are too narrow; a hump when too
  wide."
  [bounds bins]
  (let [cdf (fn [[lo hi] u] (cond (>= u hi) 1.0 (<= u lo) 0.0 :else (/ (- u lo) (- hi lo))))
        n (count bounds)]
    (for [j (range 1 (inc bins))]
      (/ (reduce + (map #(- (cdf % (/ j (double bins))) (cdf % (/ (dec j) (double bins)))) bounds)) n))))

(defn- log-sorted [xs]
  (let [arr (double-array (map #(Math/log1p (max 0.0 %)) xs))]
    (java.util.Arrays/sort arr)
    arr))

(defn- sample-totals
  "The forecast totals of node i, channel c, one per simulation, sorted."
  [fc i c]
  (let [xs (double-array (for [s (range (:sims fc))]
                           (model/total fc i s c)))]
    (java.util.Arrays/sort xs)
    xs))

(defn- mean [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))

(defn rows
  "One row per node with a history, and channel: what was forecast, what
  happened, and what the benchmarks said. `ctx` is {:actual :hist :clim}.
  :p-cal is the escalation probability after calibration by the forecast's
  :calibration, if any."
  [fc {:keys [actual hist clim]}]
  (let [h (:horizon fc)
        zero (double-array model/C)]
    (for [[i id] (map-indexed vector (:ids fc))
          :let [past (get hist id)]
          :when (seq past)
          :let [level (geo/kind id)
                ^doubles act (get actual id zero)]
          [c ch] (map-indexed vector ont/channels)
          :let [series (mapv #(aget ^doubles % c) past)
                b (peek series)
                st (stratum (reduce max series) h)
                xs (cal/widen-skewed (cal/spread-factors (:spread fc) [level ch st]) (sample-totals fc i c))
                n (alength xs)
                a (aget act c)
                p (/ (count (filter #(escalated? % b h) xs)) (double n))
                pc (climate-rate clim level ch st)]]
      {:id id :level level :channel ch :stratum st
       :actual a :last b
       :clim3 (mean (take-last 3 series))
       :mean (/ (areduce xs k s 0.0 (+ s (aget xs k))) n)
       :q10 (m/quantile xs 0.1) :q50 (m/quantile xs 0.5) :q90 (m/quantile xs 0.9)
       :crps (crps (log-sorted xs) (Math/log1p a))
       :pit (let [ys (aclone xs)] (java.util.Arrays/sort ys) (pit-bounds ys a))
       :crps-hist (crps (log-sorted series) (Math/log1p a))
       :p-escalate p
       :sims n
       :p-cal (cal/calibrate (:calibration fc) [ch st] p)
       :p-clim pc
       :p-hist (history-rate series h pc)
       :escalated (escalated? a b h)})))

(defn- log-err [x a] (Math/abs (- (Math/log1p (max 0.0 x)) (Math/log1p a))))

(defn- total-scores [rs]
  {:n (count rs)
   :err-model (mean (map #(log-err (:mean %) (:actual %)) rs))
   :err-median (mean (map #(log-err (:q50 %) (:actual %)) rs))
   :err-persist (mean (map #(log-err (:last %) (:actual %)) rs))
   :err-clim (mean (map #(log-err (:clim3 %) (:actual %)) rs))
   :crps-model (mean (map :crps rs))
   :crps-hist (mean (map :crps-hist rs))
   :coverage80 (mean (map #(if (<= (:q10 %) (:actual %) (:q90 %)) 1.0 0.0) rs))
   :pit (let [bs (keep :pit rs)] (when (seq bs) (vec (pit-histogram bs 10))))})

(defn- escalation-row? [r]
  (some #{(:channel r)} escalation-channels))

(defn outcomes
  "Resolved escalation forecasts, for `bnm.calibrate/learn`, keyed by
  channel and stratum."
  [rs]
  (for [r rs
        :when (and (not (#{:region :world} (:level r))) (escalation-row? r))]
    {:channel [(:channel r) (:stratum r)] :p (:p-escalate r) :outcome (:escalated r)}))

(def scored-kinds
  "The kinds of node whose escalations are pooled and reported."
  #{:actor :province :country :subregion})

(def kind-order [:dyad :pair :rest :actor :province :country :subregion :region :world])

(defn score
  "Scores forecast `fc` in context `ctx` ({:actual :hist :clim}, see
  `rows`). Keeps the rows (:rows), and the country and subregion escalation
  rows of `scored-kinds` (:scored) that `aggregate` pools over periods. A
  row's :level is its node's kind (see `bnm.geo/kind`)."
  [fc ctx]
  (let [rs (vec (rows fc ctx))
        period (:period fc)]
    {:period period
     :rows rs
     :outcomes (vec (outcomes rs))
     :scored (vec (for [r rs :when (and (scored-kinds (:level r)) (escalation-row? r))]
                    (-> (select-keys r [:id :level :channel :stratum :last :actual
                                        :p-escalate :sims :p-cal :p-hist :p-clim :escalated])
                        (assoc :period period))))
     :totals (into (sorted-map)
                   (for [[k g] (group-by (juxt :level :channel) rs)]
                     [k (total-scores g)]))}))

;; --- scored rows on disk -----------------------------------------------------------------

;; A run's scored rows are too many to hold or print at once (millions, with
;; actors and provinces): they go to scored.edn as each period is scored, one
;; row per line inside a vector, so the file reads whole as EDN or line by line.

(defn open-scored ^java.io.Writer [path]
  (doto ^java.io.Writer (io/writer path) (.write "[\n")))

(defn write-scored! [^java.io.Writer w rows]
  (doseq [r rows] (.write w (pr-str r)) (.write w "\n"))
  (.flush w))

(defn close-scored! [^java.io.Writer w]
  (.write w "]\n")
  (.close w))

(defn read-scored
  "The rows of a scored file, or only those of `level` and `channel`."
  ([path] (read-scored path nil nil))
  ([path level channel]
   (let [want (fn [line] (and (str/starts-with? line "{")
                              (or (nil? level) (str/includes? line (str ":level " level)))
                              (or (nil? channel) (str/includes? line (str ":channel " channel)))))]
     (with-open [r (io/reader path)]
       (into [] (comp (filter want)
                      (map edn/read-string)
                      (filter #(and (or (nil? level) (= level (:level %)))
                                    (or (nil? channel) (= channel (:channel %))))))
             (line-seq r))))))

;; --- pooled escalation scores --------------------------------------------------------------

(defn- auc
  "Area under the ROC curve of `scores` for boolean `labels` (Mann-Whitney)."
  [scores labels]
  (let [pairs (sort-by first (map vector scores labels))
        n-pos (count (filter true? labels))
        n-neg (- (count labels) n-pos)]
    (when (and (pos? n-pos) (pos? n-neg))
      ;; average ranks over ties
      (let [ranked (loop [xs (vec pairs) i 0 acc []]
                     (if (>= i (count xs))
                       acc
                       (let [s (first (xs i))
                             j (loop [j i] (if (and (< j (count xs)) (= s (first (xs j)))) (recur (inc j)) j))
                             r (/ (+ (inc i) j) 2.0)]
                         (recur xs j (into acc (map (fn [[_ l]] [r l]) (subvec xs i j)))))))
            rank-sum (reduce + (keep (fn [[r l]] (when l r)) ranked))]
        (/ (- rank-sum (/ (* n-pos (inc n-pos)) 2.0)) (* n-pos n-neg))))))

(defn- avg-precision
  "Average precision (area under the precision-recall curve) of `scores`."
  [scores labels]
  (let [n-pos (count (filter true? labels))]
    (when (pos? n-pos)
      (loop [xs (sort-by (comp - first) (map vector scores labels)) i 1 hits 0 acc 0.0]
        (if-let [[[_ l] & more] (seq xs)]
          (if l
            (recur more (inc i) (inc hits) (+ acc (/ (inc hits) (double i))))
            (recur more (inc i) hits acc))
          (/ acc n-pos))))))

(defn- brier [ps ys]
  (mean (map (fn [p y] (let [d (- p (if y 1.0 0.0))] (* d d))) ps ys)))

(defn- top
  "Precision and recall among the `k` rows with the highest `f` per period."
  [g f k]
  (let [periods (vals (group-by :period g))
        hits (reduce + (for [rs periods] (count (filter :escalated (take k (sort-by (comp - f) rs))))))
        slots (reduce + (for [rs periods] (min k (count rs))))
        pos (count (filter :escalated g))]
    {:precision (when (pos? slots) (/ hits (double slots)))
     :recall (when (pos? pos) (/ hits (double pos)))}))

(defn escalation-scores
  "Scores of the escalation rows `g`, pooled."
  [g]
  (let [ys (mapv :escalated g)]
    {:n (count g)
     :positives (count (filter true? ys))
     :rate (mean (map #(if % 1.0 0.0) ys))
     :brier-model (brier (map :p-escalate g) ys)
     :brier-cal (brier (map :p-cal g) ys)
     :brier-hist (brier (map :p-hist g) ys)
     :brier-clim (brier (map :p-clim g) ys)
     :auc-model (auc (map :p-escalate g) ys)
     :auc-hist (auc (map :p-hist g) ys)
     :ap-model (avg-precision (map :p-escalate g) ys)
     :ap-hist (avg-precision (map :p-hist g) ys)
     :top-model (top g :p-escalate top-k)
     :top-hist (top g :p-hist top-k)
     ;; Monte Carlo error: a probability from n simulations has variance
     ;; p(1-p)/n, which is also what it adds to the expected Brier score
     :mc-se (mean (for [r g :when (:sims r)]
                    (Math/sqrt (/ (* (:p-escalate r) (- 1.0 (:p-escalate r))) (:sims r)))))
     :brier-mc (mean (for [r g :when (:sims r)]
                       (/ (* (:p-escalate r) (- 1.0 (:p-escalate r))) (:sims r))))}))

(defn- pooled-escalation
  "Escalation scores of `scored` rows (all of one level and channel, or any
  mix), pooled by level, channel and stratum, and over :all strata."
  [scored]
  (let [groups (concat (group-by (juxt :level :channel :stratum) scored)
                       (group-by (juxt :level :channel (constantly :all)) scored))]
    (into {} (for [[k g] groups] [k (escalation-scores g)]))))

(defn- pooled-totals [results]
  (into (sorted-map)
        (for [key (distinct (mapcat (comp keys :totals) results))
              :let [xs (keep #(get-in % [:totals key]) results)]]
          [key (merge (into {} (for [f [:n :err-model :err-median :err-persist :err-clim :crps-model
                                        :crps-hist :coverage80]
                                     :let [vs (keep f xs)]
                                     :when (seq vs)]
                                 [f (mean vs)]))
                      (let [ps (keep :pit xs)]
                        (when (seq ps) {:pit (apply mapv (fn [& v] (mean v)) ps)})))])))

(defn aggregate
  "Scores over several periods' results: totals averaged over periods,
  escalation pooled by level, channel and stratum (and :all strata). The
  escalation rows are the results' :scored, or those of a scored file (see
  `write-scored!`), read one level and channel at a time."
  ([results]
   {:periods (mapv :period results)
    :totals (pooled-totals results)
    :escalation (pooled-escalation (mapcat :scored results))})
  ([results scored-path]
   {:periods (mapv :period results)
    :totals (pooled-totals results)
    :escalation (into {} (for [l (sort-by #(.indexOf ^java.util.List kind-order %) scored-kinds)
                               ch escalation-channels]
                           (pooled-escalation (read-scored scored-path l ch))))}))

;; --- reports ---------------------------------------------------------------------------

(defn- label [id]
  (if (geo/country? id) (str id " " (geo/country-name id)) id))

(defn inspect
  "What a forecast said about violence in countries and dyads, beside what
  happened: the 25 it rated most likely to escalate, the 25 biggest real
  escalations, and the onsets (escalations from quiet)."
  [{:keys [period rows]}]
  (let [rs (filter #(and (= :violence (:channel %)) (#{:dyad :pair :actor :province :country} (:level %)))
                   rows)
        show (fn [r] (select-keys (update r :id label)
                                  [:id :stratum :last :q10 :q50 :q90 :actual :p-escalate :p-hist :escalated]))
        growth #(- (/ (+ 1.0 (:actual %)) (+ 1.0 (:last %))))]
    {:period period
     :most-expected (mapv show (take 25 (sort-by (comp - :p-escalate) rs)))
     :biggest-escalations (mapv show (take 25 (sort-by growth (filter :escalated rs))))
     :onsets (mapv show (sort-by growth (filter #(and (:escalated %) (= :quiet (:stratum %))) rs)))}))

(defn- f3 [x] (if x (format "%.3f" (double x)) "  -  "))
(defn- f2 [x] (if x (format "%.2f" (double x)) " - "))

(defn- skill [x ref] (when (and x ref (pos? ref)) (* 100 (- 1 (/ x ref)))))
(defn- pct [x] (if x (format "%+6.1f%%" (double x)) "    -  "))

(defn summary-line [{:keys [period totals scored]}]
  (let [t (totals [:country :violence])
        g (filter #(and (= :country (:level %)) (= :violence (:channel %))) scored)
        e (escalation-scores g)
        q (escalation-scores (filter #(= :quiet (:stratum %)) g))]
    (format "%s country violence: err %s (persist %s) crps %s (hist %s) cover80 %s | AP %s (hist %s) | onsets %d, AP %s (hist %s)"
            period (f3 (:err-model t)) (f3 (:err-persist t)) (f3 (:crps-model t)) (f3 (:crps-hist t))
            (f2 (:coverage80 t)) (f3 (:ap-model e)) (f3 (:ap-hist e))
            (:positives q) (f3 (:ap-model q)) (f3 (:ap-hist q)))))

(defn report-text
  "The report of `results`, pooled by `aggregate` (from the results' rows, or
  a scored file's). A result's :summary, if it has one, is its line."
  ([results] (report-text results (aggregate results)))
  ([results agg]
   (let [{:keys [periods totals escalation]} agg
         level-name name
         by-kind (fn [m] (sort-by (fn [[[l ch]]] [(.indexOf ^java.util.List kind-order l) (name ch)]) m))]
     (with-out-str
       (println "Backtest over" (count periods) "periods:" (first periods) "-" (last periods))
       (println)
       (println "Totals: mean |log error| of the forecast mean (model, persistence, mean of last 3),")
       (println "skill against persistence; the same of the forecast median; CRPS in log space (model,")
       (println "own history as a sample), 80% coverage; the non-randomized PIT histogram in tenths, times")
       (println "ten (all 1.0 when calibrated; high ends when the ranges are too narrow, a hump when too wide)")
       (doseq [[[l ch] s] (by-kind totals)]
         (println (format "  %-9s %-16s n=%5.0f  err %s %s %s  skill %s  median %s %s  crps %s %s  cover80 %s"
                          (level-name l) (name ch) (:n s) (f3 (:err-model s)) (f3 (:err-persist s))
                          (f3 (:err-clim s)) (pct (skill (:err-model s) (:err-persist s)))
                          (f3 (:err-median s)) (pct (skill (:err-median s) (:err-persist s)))
                          (f3 (:crps-model s)) (f3 (:crps-hist s)) (f2 (:coverage80 s))))
         (when-let [h (:pit s)]
           (println (format "  %-9s %-16s pit %s" "" "" (str/join " " (map #(format "%.1f" (* 10.0 %)) h))))))
       (println)
       (println (str "Escalation (>= " escalation-ratio "x the last period), by the stratum of the node's history."))
       (println "Brier skill against climatology (raw model, calibrated, own history); ROC AUC and average")
       (println (str "precision (model / history); precision and recall in the top " top-k " per period (model / history);"))
       (println "and the Monte Carlo standard error of the model's probabilities (mc), with what that noise adds to its Brier score")
       (doseq [l [:country :actor :province :subregion]
               ch escalation-channels
               st (cons :all strata)
               :let [s (escalation [l ch st])]
               :when s]
         (println (format "  %-9s %-16s %-6s n=%6d pos=%5d rate %.3f  BSS %s %s %s  auc %s/%s  ap %s/%s  P@%d %s/%s  R@%d %s/%s  mc %s (+%s)"
                          (level-name l) (name ch) (name st) (:n s) (:positives s) (:rate s)
                          (pct (skill (:brier-model s) (:brier-clim s)))
                          (pct (skill (:brier-cal s) (:brier-clim s)))
                          (pct (skill (:brier-hist s) (:brier-clim s)))
                          (f3 (:auc-model s)) (f3 (:auc-hist s)) (f3 (:ap-model s)) (f3 (:ap-hist s))
                          top-k (f2 (get-in s [:top-model :precision])) (f2 (get-in s [:top-hist :precision]))
                          top-k (f2 (get-in s [:top-model :recall])) (f2 (get-in s [:top-hist :recall]))
                          (f3 (:mc-se s)) (f3 (:brier-mc s)))))
       (println)
       (println "Per period:")
       (doseq [r results] (println " " (or (:summary r) (summary-line r))))))))

(defn slim
  "A scored result without its rows, for keeping over a whole run: what the
  report needs once its scored rows are on disk."
  [r]
  (-> (dissoc r :rows :scored :outcomes)
      (assoc :summary (summary-line r))))

(defn write-report!
  "Writes periods.edn and report.txt for `results`, whose scored rows are in
  `scored-path`; prints the report."
  [dir results scored-path]
  (with-open [w (io/writer (str dir "/periods.edn"))]
    (.write w "[\n")
    (doseq [r results] (.write w (pr-str (dissoc r :rows :scored :outcomes :summary))) (.write w "\n"))
    (.write w "]\n"))
  (let [text (report-text results (aggregate results scored-path))]
    (spit (str dir "/report.txt") text)
    (println text)))

;; --- posterior predictive checks ---------------------------------------------------------

(def ppc-stats
  "Features of a node's weekly series a posterior predictive check compares,
  simulated against observed."
  [:dispersion :burst :quiet :ac1])

(defn- series-stats
  "The `ppc-stats` of a weekly series `xs` (doubles)."
  [xs]
  (let [n (count xs)
        mean (/ (reduce + xs) (double n))
        var (/ (reduce + (map #(let [d (- % mean)] (* d d)) xs)) (double n))
        ac (if (and (> n 2) (pos? var))
             (/ (reduce + (map (fn [a b] (* (- a mean) (- b mean))) xs (rest xs)))
                (* (dec n) var))
             0.0)]
    {:dispersion (/ var (+ mean 1e-9))
     :burst (/ (reduce max xs) (+ mean 1e-9))
     :quiet (/ (count (filter #(< % 1e-9) xs)) (double n))
     :ac1 ac}))

(defn ppc
  "Posterior predictive p-values for the nodes whose paths forecast `fc`
  kept: per node and channel with any activity, for each of `ppc-stats`, the
  share of simulations whose statistic exceeds the observed one (ties
  counted half). `observed` is {id [double[C] per bin]}, the period's weekly
  observations. A model that reproduces the data gives p-values spread
  evenly over [0, 1]."
  [fc observed]
  (let [h (:horizon fc)
        sims (:sims fc)
        ^objects paths (:paths fc)]
    (for [[i id] (map-indexed vector (:ids fc))
          :let [^doubles p (when paths (aget paths i))
                weeks (get observed id)]
          :when (and p (= h (count weeks)))
          c (range model/C)
          :let [obs (mapv #(aget ^doubles % c) weeks)
                simulated (for [s (range sims)]
                            (mapv #(aget p (+ (* s h model/C) (* % model/C) c)) (range h)))]
          :when (or (some pos? obs) (some #(some pos? %) simulated))
          :let [o (series-stats obs)
                ss (map series-stats simulated)]]
      (into {:id id :kind (geo/kind id) :channel (ont/channels c)}
            (for [k ppc-stats
                  :let [ov (k o)
                        above (count (filter #(> (k %) ov) ss))
                        ties (count (filter #(= (k %) ov) ss))]]
              [k (/ (+ above (* 0.5 ties)) (double sims))])))))

(defn ppc-summary
  "Per kind of node and statistic: how many checks, the mean p-value, and the
  shares below 0.05 and above 0.95 (0.5, 0.05 and 0.05 for a model that
  reproduces the data)."
  [rows]
  (into (sorted-map)
        (for [[kind g] (group-by :kind rows)
              k ppc-stats
              :let [ps (map k g)]]
          [[kind k] {:n (count ps)
                     :mean (mean ps)
                     :low (mean (map #(if (< % 0.05) 1.0 0.0) ps))
                     :high (mean (map #(if (> % 0.95) 1.0 0.0) ps))}])))

(defn ppc-text [summary]
  (with-out-str
    (println "Posterior predictive checks: p-values of simulated weekly features against the observed")
    (println "(dispersion = variance/mean, burst = peak/mean, quiet = share of weeks with nothing, ac1 =")
    (println "lag-1 autocorrelation). Well specified: mean 0.50, and 0.05 below 0.05 and above 0.95.")
    (println "A high mean says the model simulates more of the feature than happens; a low mean, less.")
    (doseq [[[kind k] {:keys [n mean low high]}] (sort-by (fn [[[kind k]]] [(.indexOf ^java.util.List kind-order kind) (name k)]) summary)]
      (println (format "  %-9s %-10s n=%6d  mean p %.2f  p<0.05 %.2f  p>0.95 %.2f" (name kind) (name k) n mean low high)))))
