(ns bnm.train
  "Trains a model on the binned historical record and backtests it.

  One pass through time, week by week, as the model would have lived it:
  learning online from each bin, reshaping itself every year from the
  volumes it has seen, and from `--eval-from` on, forecasting each period at
  its start: a calendar year, or with `--every N` the next N bins (13 for
  quarters). Each forecast is scored (see `bnm.eval`) when the period it
  covers has been observed. No forecast sees anything after its origin.

    jolt -A:tools -M -m bnm.train --sources gdelt --eval-from 2000 --name base
    jolt -A:tools -M -m bnm.train --sources gdelt --eval-from 2000 --every 13 --name base-q

  Writes data/runs/<name>/: report.txt, periods.edn (per-origin scores),
  scored.edn (the escalation rows the report pools), rows/<period>.tsv,
  forecasts/<period>.edn (what was predicted, for inspection) and model.edn
  (the model at the end, as `bnm.store` saves it). With --save-origins, the
  model and the scoring context at each origin, for `bnm.refit`. With
  --ppc true, ppc.txt: posterior predictive checks of the weekly series of a
  sample of nodes in every scored period (see `bnm.eval/ppc`)."
  (:require [bnm.calibrate :as cal]
            [bnm.eval :as ev]
            [bnm.geo :as geo]
            [bnm.model :as model]
            [bnm.series :as series]
            [bnm.store :as store]
            [bnm.track :as track]
            [bnm.structure :as structure]
            [bnm.time :as t]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(defn- bin-records
  "A lazy seq of [bin records] from a binned file, records
  [a1 s1 a2 s2 adm channel n]."
  [^java.io.BufferedReader rdr]
  (letfn [(parse [line]
            (let [[b a1 s1 a2 s2 adm c n] (str/split line #"\t" -1)]
              [(parse-long b) a1 (not-empty s1) (not-empty a2) (not-empty s2) (not-empty adm)
               (parse-long c) (parse-double n)]))
          (step [lines]
            (lazy-seq
             (when-let [s (seq lines)]
               (let [b (first (parse (first s)))
                     [these more] (split-with #(str/starts-with? % (str b "\t")) s)]
                 (cons [b (mapv (comp vec rest parse) these)] (step more))))))]
    (step (line-seq rdr))))

(defn- merged
  "Merges several sources' [bin records] seqs into [bin {source records}],
  one entry per bin from the first to the last, empty bins included."
  [by-source]
  (letfn [(step [b streams]
            (lazy-seq
             (let [live (filter (comp seq val) streams)]
               (when (seq live)
                 (let [here (into {} (for [[s xs] live
                                           :let [[b0 recs] (first xs)]
                                           :when (= b0 b)]
                                       [s recs]))
                       streams (into {} (for [[s xs] live]
                                          [s (if (= b (ffirst xs)) (rest xs) xs)]))]
                   (cons [b here] (step (inc b) streams)))))))]
    (let [start (apply min (keep (comp ffirst val) by-source))]
      (step start by-source))))

(defn bin-year
  "The year a bin belongs to: the year of its middle day."
  [b bin-days]
  (t/year-of-day (+ (* b bin-days) (quot bin-days 2))))

(defn- parse-args [args]
  (loop [[k v & more] args opts {}]
    (if k
      (recur more (assoc opts (keyword (subs k 2)) v))
      opts)))

(defn- write-rows! [path rows]
  (spit path (apply str (for [x rows]
                          (str (str/join "\t" ((juxt :id :level (comp name :channel) (comp name :stratum)
                                                     :last :actual :mean :q10 :q50 :q90
                                                     :p-escalate :p-hist :p-clim)
                                               x))
                               "\n")))))

(defn- history->edn [hist]
  (into {} (for [[id v] hist] [id (mapv vec v)])))

(def ppc-sample
  "Nodes of each kind below the country checked per period, at most."
  200)

(defn- ppc-nodes
  "The node indices whose paths a forecast keeps for the posterior predictive
  checks: every country and the levels above, and an evenly spaced sample of
  at most `ppc-sample` of each other kind."
  [mdl]
  (let [by-kind (group-by (comp geo/kind :id second) (map-indexed vector (:nodes mdl)))]
    (set (for [[kind xs] by-kind
               :let [xs (vec xs)
                     step (if (#{:country :subregion :region :world} kind)
                            1
                            (max 1 (quot (count xs) ppc-sample)))]
               j (range 0 (count xs) step)]
           (first (xs j))))))

(defn- add-ppc [acc rows]
  (reduce (fn [acc r]
            (reduce (fn [acc k]
                      (let [p (k r)]
                        (update acc [(:kind r) k]
                                (fn [[n sum low high]]
                                  [(inc (or n 0)) (+ (or sum 0.0) p)
                                   (+ (or low 0) (if (< p 0.05) 1 0)) (+ (or high 0) (if (> p 0.95) 1 0))]))))
                    acc ev/ppc-stats))
          acc rows))

(defn- ppc-report [acc]
  (into {} (for [[k [n sum low high]] acc]
             [k {:n n :mean (/ sum n) :low (/ low (double n)) :high (/ high (double n))}])))

(defn run
  [{:keys [sources eval-from eval-to sims from config to save-origins every ppc] run-name :name
    :or {sims "100" from "1979"}}]
  (let [sources (str/split sources #",")
        eval-from (parse-long eval-from)
        eval-to (if eval-to (parse-long eval-to) 9999)
        from (parse-long from)
        sims (parse-long sims)
        to (if to (parse-long to) 9999)
        every (some-> every parse-long)
        ppc? (= ppc "true")
        config (if config (edn/read-string config) {})
        bin-days (:bin-days (merge model/defaults config))
        year-of #(bin-year % bin-days)
        ;; a period is a calendar year, or `every` bins
        period-of (if every #(quot % every) year-of)
        label-of (fn [b] (if every (t/date-str (* b bin-days)) (str (year-of b))))
        dir (str "data/runs/" run-name)
        open-stream (fn []
                      (let [readers (into {} (for [s sources] [s (io/reader (str "data/bins/" s ".tsv"))]))]
                        [readers
                         (->> (merged (into {} (for [[s r] readers] [s (bin-records r)])))
                              (drop-while #(< (year-of (first %)) from)))]))
        ;; the first year's volumes shape the initial structure; read it on its
        ;; own so the main pass holds no reference to the head of its stream
        vols (let [[readers stream] (open-stream)
                   y0 (year-of (ffirst stream))
                   vols (reduce (fn [v [_ by-source]]
                                  (reduce (fn [v [a s b t adm _ n]] (structure/tally v a s b t adm n))
                                          v (mapcat val by-source)))
                                (structure/volumes)
                                (take-while #(= y0 (year-of (first %))) stream))]
               (doseq [r (vals readers)] (.close ^java.io.Reader r))
               vols)
        [readers stream] (open-stream)
        mdl (model/create (structure/build vols (:structure-opts config {})) config)
        started (System/currentTimeMillis)
        scored-path (str dir "/scored.edn")
        _ (io/make-parents (str dir "/rows/x"))
        scored-out (ev/open-scored scored-path)]
    (io/make-parents (str dir "/forecasts/x"))
    (spit (str dir "/args.edn") (pr-str {:sources sources :eval-from eval-from :from from :every every
                                         :sims sims :config (:config mdl)}))
    (loop [mdl mdl
           [[b by-source] & more] stream
           period nil
           start nil            ; the current period's first bin
           actual {}            ; this period's observed totals {id double[C]}
           hist {}              ; earlier periods' totals {id [double[C] ...]}, oldest first
           clim {}              ; escalation climatology from the periods scored so far
           pending nil          ; the forecast for this period, made at its start
           results []
           weeks {}             ; this period's weekly observations of the checked nodes
           ppc-acc {}]          ; posterior predictive check counts, by kind and statistic
      (if (or (nil? b) (> (year-of b) to))
        (do (when ppc?
              (spit (str dir "/ppc.txt") (ev/ppc-text (ppc-report ppc-acc)))
              (println (ev/ppc-text (ppc-report ppc-acc))))
            (store/write-file (cond-> mdl
                                ;; quarters as live scoring cuts them: the model
                                ;; ships the history and climatology it was
                                ;; scored with, for a live model to carry on
                                (= every (track/period-bins mdl))
                                (assoc :track {:period period
                                               :actual (into {} (for [[id a] actual] [id (vec a)]))
                                               :hist hist :clim clim}))
                              (str dir "/model.edn"))
            (ev/close-scored! scored-out)
            (ev/write-report! dir results scored-path)
            (doseq [r readers] (.close ^java.io.Reader (val r)))
            results)
        (let [p (period-of b)]
          (if (and period (not= p period))
            ;; a period has ended: score its forecast, reshape, forecast the next
            (let [r (when pending (ev/score pending {:actual actual :hist hist :clim clim}))
                  scored (when r
                           (let [lbl (:period pending)]
                             (println (ev/summary-line r))
                             (write-rows! (str dir "/rows/" lbl ".tsv") (:rows r))
                             (spit (str dir "/forecasts/" lbl ".edn")
                                   (with-out-str (pp/pprint (ev/inspect r))))
                             (ev/write-scored! scored-out (:scored r))
                             (dissoc r :rows)))
                  ppc-acc (if (and ppc? pending)
                            (add-ppc ppc-acc (ev/ppc pending (into {} (for [[id v] weeks] [id (persistent! v)]))))
                            ppc-acc)
                  ;; a run's scored rows are on disk; it keeps only their summaries
                  results (cond-> results scored (conj (ev/slim scored)))
                  ;; the calibration and the climatology learn from the period just scored
                  mdl (cond-> mdl
                        scored (-> (update :calibration cal/learn (:outcomes scored))
                                   (update :spread cal/learn-spread (:rows r))))
                  clim (cond-> clim scored (ev/learn-climate (:scored scored)))
                  hist (ev/add-history hist actual)
                  mdl (if (or (not every) (not= (year-of b) (year-of start)))
                        (-> (model/restructure mdl (structure/build (series/volumes mdl)
                                                                    (:structure-opts config {})))
                            series/with-routes)
                        mdl)
                  horizon (count (take-while #(= p (period-of %)) (iterate inc b)))
                  lbl (label-of b)
                  live? (<= eval-from (year-of b) eval-to)
                  _ (when (and save-origins live?)
                      (store/write-file mdl (str dir "/origin-" lbl ".edn"))
                      (spit (str dir "/context-" lbl ".edn")
                            (pr-str {:hist (history->edn hist) :clim clim})))
                  pending (when live?
                            (let [t0 (System/currentTimeMillis)
                                  keep (when ppc? (ppc-nodes mdl))
                                  fc (model/forecast mdl (cond-> {:horizon horizon :sims sims :seed p}
                                                           keep (assoc :keep-paths keep)))]
                              (println "forecast" lbl (count (:ids fc)) "nodes"
                                       (- (System/currentTimeMillis) t0) "ms")
                              (assoc fc :period lbl :levels (mapv :level (:nodes mdl))
                                     :checked (when keep (set (map (comp :id (:nodes mdl)) keep)))
                                     :calibration (:calibration mdl)
                                     :spread (:spread mdl)
                                     :spread-mode (get-in mdl [:config :spread-mode]))))]
              (println "period" (label-of start) "done;" (count (:nodes mdl)) "nodes;"
                       (quot (- (System/currentTimeMillis) started) 1000) "s"
                       (model/skill mdl))
              (flush)
              (recur mdl (cons [b by-source] more) p b {} hist clim pending results {} ppc-acc))
            (let [[mdl obs] (series/observe-bin! mdl b by-source)
                  actual (reduce-kv (fn [acc id ^doubles arr]
                                      (let [^doubles a (or (acc id) (double-array model/C))]
                                        (dotimes [c model/C] (aset a c (+ (aget a c) (aget arr c))))
                                        (assoc acc id a)))
                                    actual obs)]
              (recur mdl more p (or start b) actual hist clim pending results
                     (if-let [checked (:checked pending)]
                       (let [zero (double-array model/C)]
                         (reduce (fn [w id] (assoc w id (conj! (get w id (transient [])) (get obs id zero))))
                                 weeks checked))
                       weeks)
                     ppc-acc))))))))

(defn -main [& args]
  (run (parse-args args)))
