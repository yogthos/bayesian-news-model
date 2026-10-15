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
            [clojure.pprint :as pp]
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

(defn- history-rate
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
          :let [xs (sample-totals fc i c)
                n (alength xs)
                a (aget act c)
                series (mapv #(aget ^doubles % c) past)
                b (peek series)
                st (stratum (reduce max series) h)
                p (/ (count (filter #(escalated? % b h) xs)) (double n))
                pc (climate-rate clim level ch st)]]
      {:id id :level level :channel ch :stratum st
       :actual a :last b
       :clim3 (mean (take-last 3 series))
       :mean (/ (areduce xs k s 0.0 (+ s (aget xs k))) n)
       :q10 (m/quantile xs 0.1) :q50 (m/quantile xs 0.5) :q90 (m/quantile xs 0.9)
       :crps (crps (log-sorted xs) (Math/log1p a))
       :crps-hist (crps (log-sorted series) (Math/log1p a))
       :p-escalate p
       :p-cal (cal/calibrate (:calibration fc) [ch st] p)
       :p-clim pc
       :p-hist (history-rate series h pc)
       :escalated (escalated? a b h)})))

(defn- log-err [x a] (Math/abs (- (Math/log1p (max 0.0 x)) (Math/log1p a))))

(defn- total-scores [rs]
  {:n (count rs)
   :err-model (mean (map #(log-err (:mean %) (:actual %)) rs))
   :err-persist (mean (map #(log-err (:last %) (:actual %)) rs))
   :err-clim (mean (map #(log-err (:clim3 %) (:actual %)) rs))
   :crps-model (mean (map :crps rs))
   :crps-hist (mean (map :crps-hist rs))
   :coverage80 (mean (map #(if (<= (:q10 %) (:actual %) (:q90 %)) 1.0 0.0) rs))})

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
                                        :p-escalate :p-cal :p-hist :p-clim :escalated])
                        (assoc :period period))))
     :totals (into (sorted-map)
                   (for [[k g] (group-by (juxt :level :channel) rs)]
                     [k (total-scores g)]))}))

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
     :top-hist (top g :p-hist top-k)}))

(defn aggregate
  "Scores over several periods' results: totals averaged over periods,
  escalation pooled by level, channel and stratum (and :all strata)."
  [results]
  (let [scored (mapcat :scored results)
        totals (into (sorted-map)
                     (for [key (distinct (mapcat (comp keys :totals) results))
                           :let [xs (keep #(get-in % [:totals key]) results)]]
                       [key (into {} (for [f [:n :err-model :err-persist :err-clim :crps-model
                                              :crps-hist :coverage80]
                                           :let [vs (keep f xs)]
                                           :when (seq vs)]
                                       [f (mean vs)]))]))
        groups (concat (group-by (juxt :level :channel :stratum) scored)
                       (group-by (juxt :level :channel (constantly :all)) scored))]
    {:periods (mapv :period results)
     :totals totals
     :escalation (into {} (for [[k g] groups] [k (escalation-scores g)]))}))

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

(defn report-text [results]
  (let [{:keys [periods totals escalation]} (aggregate results)
        level-name name
        by-kind (fn [m] (sort-by (fn [[[l ch]]] [(.indexOf ^java.util.List kind-order l) (name ch)]) m))]
    (with-out-str
      (println "Backtest over" (count periods) "periods:" (first periods) "-" (last periods))
      (println)
      (println "Totals: mean |log error| of the forecast mean (model, persistence, mean of last 3),")
      (println "skill against persistence, CRPS in log space (model, own history as a sample), 80% coverage")
      (doseq [[[l ch] s] (by-kind totals)]
        (println (format "  %-9s %-16s n=%5.0f  err %s %s %s  skill %s  crps %s %s  cover80 %s"
                         (level-name l) (name ch) (:n s) (f3 (:err-model s)) (f3 (:err-persist s))
                         (f3 (:err-clim s)) (pct (skill (:err-model s) (:err-persist s)))
                         (f3 (:crps-model s)) (f3 (:crps-hist s)) (f2 (:coverage80 s)))))
      (println)
      (println (str "Escalation (>= " escalation-ratio "x the last period), by the stratum of the node's history."))
      (println "Brier skill against climatology (raw model, calibrated, own history); ROC AUC and average")
      (println (str "precision (model / history); precision and recall in the top " top-k " per period (model / history)"))
      (doseq [l [:country :actor :province :subregion]
              ch escalation-channels
              st (cons :all strata)
              :let [s (escalation [l ch st])]
              :when s]
        (println (format "  %-9s %-16s %-6s n=%6d pos=%5d rate %.3f  BSS %s %s %s  auc %s/%s  ap %s/%s  P@%d %s/%s  R@%d %s/%s"
                         (level-name l) (name ch) (name st) (:n s) (:positives s) (:rate s)
                         (pct (skill (:brier-model s) (:brier-clim s)))
                         (pct (skill (:brier-cal s) (:brier-clim s)))
                         (pct (skill (:brier-hist s) (:brier-clim s)))
                         (f3 (:auc-model s)) (f3 (:auc-hist s)) (f3 (:ap-model s)) (f3 (:ap-hist s))
                         top-k (f2 (get-in s [:top-model :precision])) (f2 (get-in s [:top-hist :precision]))
                         top-k (f2 (get-in s [:top-model :recall])) (f2 (get-in s [:top-hist :recall])))))
      (println)
      (println "Per period:")
      (doseq [r results] (println " " (summary-line r))))))

(defn write-report! [dir results]
  (spit (str dir "/periods.edn")
        (with-out-str (pp/pprint (mapv #(dissoc % :rows :scored :outcomes) results))))
  (spit (str dir "/scored.edn") (with-out-str (pp/pprint (vec (mapcat :scored results)))))
  (let [text (report-text results)]
    (spit (str dir "/report.txt") text)
    (println text)))
