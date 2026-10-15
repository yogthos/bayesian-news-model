(ns bnm.eval
  "Scoring forecasts against what happened.

  A forecast (from `bnm.model/forecast`) gives, per node and channel, a sample
  of totals over its horizon. Against the observed totals it is scored on:

  - totals: mean absolute error in log1p space of the forecast mean, beside
    the same error for persistence (next year = this year), and how often the
    observed total falls in the forecast's 80% interval;
  - escalation: the forecast probability that a channel's total rises to at
    least `escalation-ratio` times last year's (and by at least
    `escalation-floor`), scored by Brier score against a base-rate forecaster
    and by AUC;
  - the places it most expected to escalate, beside those that did."
  (:require [bnm.calibrate :as cal]
            [bnm.geo :as geo]
            [bnm.math :as m]
            [bnm.model :as model]
            [bnm.ontology :as ont]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(def escalation-ratio 1.5)

(def escalation-floor
  "An escalation must add at least this much (normalized units over the year:
  about two thousandths of a typical week's reports) so noise in tiny counts
  does not count."
  2.0)

(def escalation-channels [:violence :protest :coercion :verbal-conflict])

(defn escalated? [^double a ^double b]
  (and (>= a (* escalation-ratio b)) (>= (- a b) escalation-floor)))

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

(defn- sample-totals
  "The forecast totals of node i, channel c, one per simulation, sorted."
  [fc i c]
  (let [xs (double-array (for [s (range (:sims fc))]
                           (model/path-sum fc i s c 0 (:horizon fc))))]
    (java.util.Arrays/sort xs)
    xs))

(defn rows
  "One row per node and channel with a baseline: what was forecast, what
  happened, and what persistence would have said. :p-cal is the escalation
  probability after calibration by the forecast's :calibration, if any."
  [fc actual last-actual]
  (for [[i id] (map-indexed vector (:ids fc))
        :when (contains? last-actual id)
        [c ch] (map-indexed vector ont/channels)
        :let [xs (sample-totals fc i c)
              a (double (aget ^doubles (get actual id (double-array model/C)) c))
              b (double (aget ^doubles (last-actual id) c))
              p-esc (/ (count (filter #(escalated? % b) xs)) (double (alength xs)))]]
    {:id id :level ((:levels fc) i) :channel ch
     :actual a :last b
     :mean (/ (areduce xs k s 0.0 (+ s (aget xs k))) (alength xs))
     :q10 (m/quantile xs 0.1) :q50 (m/quantile xs 0.5) :q90 (m/quantile xs 0.9)
     :p-escalate p-esc
     :p-cal (cal/calibrate (:calibration fc) ch p-esc)
     :escalated (escalated? a b)}))

(defn- mean [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))

(defn- log-err [x a] (Math/abs (- (Math/log1p (max 0.0 x)) (Math/log1p a))))

(defn- total-scores [rs]
  {:n (count rs)
   :err-model (mean (map #(log-err (:mean %) (:actual %)) rs))
   :err-persist (mean (map #(log-err (:last %) (:actual %)) rs))
   :coverage80 (mean (map #(if (<= (:q10 %) (:actual %) (:q90 %)) 1.0 0.0) rs))})

(defn- escalation-scores [rs]
  (let [rs (filter #(>= (+ (:last %) (:actual %)) escalation-floor) rs)
        labels (map :escalated rs)
        base (mean (map #(if % 1.0 0.0) labels))
        brier (fn [p l] (let [d (- p (if l 1.0 0.0))] (* d d)))]
    (when (seq rs)
      {:n (count rs)
       :rate base
       :brier-model (mean (map #(brier (:p-escalate %) (:escalated %)) rs))
       :brier-cal (mean (map #(brier (:p-cal %) (:escalated %)) rs))
       :brier-base (mean (map #(brier base %) labels))
       :auc (auc (map :p-escalate rs) labels)})))

(defn outcomes
  "Resolved escalation forecasts, for `bnm.calibrate/learn`."
  [rs]
  (for [r rs
        :when (and (#{0 1 2} (:level r))
                   (some #{(:channel r)} escalation-channels)
                   (>= (+ (:last r) (:actual r)) escalation-floor))]
    {:channel (:channel r) :p (:p-escalate r) :outcome (:escalated r)}))

(defn score
  "Scores forecast `fc` against the year's `actual` totals, with `last-actual`
  (the year before) as the persistence baseline."
  [fc actual last-actual]
  (let [rs (vec (rows fc actual last-actual))]
    {:year (:year fc)
     :outcomes (vec (outcomes rs))
     :totals (into (sorted-map)
                   (for [[[level ch] g] (group-by (juxt :level :channel) rs)]
                     [[level ch] (total-scores g)]))
     :escalation (into (sorted-map)
                       (for [ch escalation-channels
                             level [0 1 2]
                             :let [s (escalation-scores (filter #(and (= ch (:channel %)) (= level (:level %))) rs))]
                             :when s]
                         [[level ch] s]))}))

(defn- label [id]
  (if (geo/country? id) (str id " " (geo/country-name id)) id))

(defn inspect
  "What a forecast said about violence in countries and dyads, beside what
  happened: the 25 it rated most likely to escalate and the 25 biggest real
  escalations."
  [fc actual last-actual]
  (let [rs (filter #(and (= :violence (:channel %)) (#{0 1} (:level %))
                         (not (str/ends-with? (:id %) ":*")))
                   (rows fc actual last-actual))
        show (fn [r] (select-keys (update r :id label)
                                  [:id :last :q10 :q50 :q90 :actual :p-escalate :escalated]))]
    {:year (:year fc)
     :most-expected (mapv show (take 25 (sort-by (comp - :p-escalate) rs)))
     :biggest-escalations (mapv show (take 25 (sort-by #(- (/ (+ 1.0 (:actual %)) (+ 1.0 (:last %))))
                                                      (filter :escalated rs))))}))

(defn summary-line [{:keys [year totals escalation]}]
  (let [t (totals [1 :violence])
        e (escalation [1 :violence])]
    (format "%d country violence: err %.3f vs persistence %.3f, cover80 %.2f | escalation brier %.4f cal %.4f vs base %.4f auc %s"
            year (double (:err-model t 0.0)) (double (:err-persist t 0.0)) (double (:coverage80 t 0.0))
            (double (:brier-model e 0.0)) (double (:brier-cal e 0.0)) (double (:brier-base e 0.0))
            (some->> (:auc e) (format "%.3f")))))

(defn aggregate
  "Mean scores over several years' results."
  [results]
  (let [avg (fn [k sub] (into (sorted-map)
                              (for [key (distinct (mapcat (comp keys k) results))
                                    :let [xs (keep #(get-in % [k key]) results)]]
                                [key (into {} (for [f sub
                                                    :let [vs (keep f xs)]
                                                    :when (seq vs)]
                                                [f (mean vs)]))])))]
    {:years (mapv :year results)
     :totals (avg :totals [:n :err-model :err-persist :coverage80])
     :escalation (avg :escalation [:n :rate :brier-model :brier-cal :brier-base :auc])}))

(defn report-text [results]
  (let [{:keys [years totals escalation]} (aggregate results)
        level-name (fn [l] (name (geo/level-names l)))]
    (with-out-str
      (println "Backtest over" (count years) "years:" (first years) "-" (last years))
      (println)
      (println "Annual totals: mean |log error| (model vs persistence), 80% interval coverage")
      (doseq [[[l ch] s] totals]
        (println (format "  %-9s %-16s n=%6.0f  model %.3f  persist %.3f  skill %+.1f%%  cover80 %.2f"
                         (level-name l) (name ch) (:n s) (:err-model s) (:err-persist s)
                         (* 100 (- 1 (/ (:err-model s) (max 1e-9 (:err-persist s)))))
                         (:coverage80 s))))
      (println)
      (println "Escalation (>= 1.5x last year): Brier (raw model, calibrated, base rate), skill of calibrated, AUC")
      (doseq [[[l ch] s] escalation]
        (println (format "  %-9s %-16s n=%6.0f  rate %.3f  brier %.4f cal %.4f base %.4f  skill %+.1f%%  auc %s"
                         (level-name l) (name ch) (:n s) (:rate s) (:brier-model s) (:brier-cal s 0.0)
                         (:brier-base s)
                         (* 100 (- 1 (/ (:brier-cal s 0.0) (max 1e-9 (:brier-base s)))))
                         (some->> (:auc s) (format "%.3f")))))
      (println)
      (println "Per year:")
      (doseq [r results] (println " " (summary-line r))))))

(defn write-report! [dir results]
  (spit (str dir "/years.edn") (with-out-str (pp/pprint (mapv #(dissoc % :outcomes) results))))
  (let [text (report-text results)]
    (spit (str dir "/report.txt") text)
    (println text)))
