(ns bnm.train
  "Trains a model on the binned historical record and backtests it.

  One pass through time, week by week, as the model would have lived it:
  learning online from each bin, reshaping itself at every year end from the
  volumes it has seen, and forecasting the coming year at every year end from
  `--eval-from` on. Each forecast is scored when the year it covers has been
  observed. No forecast sees anything after its origin.

    jolt -A:tools -M -m bnm.train --sources gdelt --eval-from 2000 --name base

  Writes data/runs/<name>/: report.txt, years.edn (per-origin scores),
  forecasts/<year>.edn (what was predicted, for inspection) and model.edn
  (the model at the end, as `bnm.store` saves it)."
  (:require [bnm.calibrate :as cal]
            [bnm.eval :as ev]
            [bnm.model :as model]
            [bnm.series :as series]
            [bnm.store :as store]
            [bnm.structure :as structure]
            [bnm.time :as t]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(defn- bin-records
  "A lazy seq of [bin records] from a binned file, records [a1 a2 channel n]."
  [^java.io.BufferedReader rdr]
  (letfn [(parse [line]
            (let [[b a1 a2 c n] (str/split line #"\t" -1)]
              [(parse-long b) a1 (not-empty a2) (parse-long c) (parse-double n)]))
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

(defn run
  [{:keys [sources eval-from eval-to sims from config to save-origins] run-name :name
    :or {sims "30" from "1979"}}]
  (let [sources (str/split sources #",")
        eval-from (parse-long eval-from)
        eval-to (if eval-to (parse-long eval-to) 9999)
        from (parse-long from)
        sims (parse-long sims)
        to (if to (parse-long to) 9999)
        config (if config (edn/read-string config) {})
        bin-days (:bin-days (merge model/defaults config))
        dir (str "data/runs/" run-name)
        open-stream (fn []
                      (let [readers (into {} (for [s sources] [s (io/reader (str "data/bins/" s ".tsv"))]))]
                        [readers
                         (->> (merged (into {} (for [[s r] readers] [s (bin-records r)])))
                              (drop-while #(< (bin-year (first %) bin-days) from)))]))
        ;; the first year's volumes shape the initial structure; read it on its
        ;; own so the main pass holds no reference to the head of its stream
        vols (let [[readers stream] (open-stream)
                   y0 (bin-year (ffirst stream) bin-days)
                   vols (reduce (fn [v [_ by-source]]
                                  (reduce (fn [v [a b _ n]] (structure/tally v a b n))
                                          v (mapcat val by-source)))
                                (structure/volumes)
                                (take-while #(= y0 (bin-year (first %) bin-days)) stream))]
               (doseq [r (vals readers)] (.close ^java.io.Reader r))
               vols)
        [readers stream] (open-stream)
        mdl (model/create (structure/build vols) config)
        started (System/currentTimeMillis)]
    (io/make-parents (str dir "/forecasts/x"))
    (io/make-parents (str dir "/rows/x"))
    (spit (str dir "/args.edn") (pr-str {:sources sources :eval-from eval-from :from from
                                         :sims sims :config (:config mdl)}))
    (loop [mdl mdl
           [[b by-source] & more] stream
           year nil
           actual {}            ; this year's observed totals {id double[C]}
           last-actual {}       ; last year's
           pending nil          ; the forecast for this year, made at its start
           results []]
      (if (or (nil? b) (> (bin-year b bin-days) to))
        (do (store/write-file mdl (str dir "/model.edn"))
            (ev/write-report! dir results)
            (doseq [r readers] (.close ^java.io.Reader (val r)))
            results)
        (let [y (bin-year b bin-days)]
          (if (and year (not= y year))
            ;; a year has ended: score its forecast, reshape, forecast the next
            (let [results (if pending
                            (let [r (ev/score pending actual last-actual)]
                              (println (ev/summary-line r))
                              (spit (str dir "/rows/" year ".tsv")
                                    (apply str (for [x (ev/rows pending actual last-actual)]
                                                 (str (str/join "\t" ((juxt :id :level (comp name :channel) :last :actual
                                                                             :mean :q10 :q50 :q90 :p-escalate)
                                                                       x))
                                                      "\n"))))
                              (spit (str dir "/forecasts/" year ".edn")
                                    (with-out-str (pp/pprint (ev/inspect pending actual last-actual))))
                              (conj results r))
                            results)
                  ;; the calibration learns from the year just scored
                  mdl (cond-> mdl
                        (seq results) (update :calibration cal/learn (:outcomes (peek results))))
                  mdl (-> (model/restructure mdl (structure/build (series/volumes mdl)
                                                                  (:structure-opts config {})))
                          series/with-routes)
                  horizon (count (take-while #(= y (bin-year % bin-days)) (iterate inc b)))
                  _ (when (and save-origins (<= eval-from y eval-to))
                      (store/write-file mdl (str dir "/origin-" y ".edn")))
                  pending (when (<= eval-from y eval-to)
                            (let [t0 (System/currentTimeMillis)
                                  fc (model/forecast mdl {:horizon horizon :sims sims :seed y})]
                              (println "forecast" y (count (:ids fc)) "nodes"
                                       (- (System/currentTimeMillis) t0) "ms")
                              (assoc fc :year y :levels (mapv :level (:nodes mdl))
                                     :calibration (:calibration mdl))))]
              (println "year" year "done;" (count (:nodes mdl)) "nodes;"
                       (quot (- (System/currentTimeMillis) started) 1000) "s"
                       (model/skill mdl))
              (flush)
              (recur mdl (cons [b by-source] more) y {} actual pending results))
            (let [[mdl obs] (series/observe-bin! mdl b by-source)
                  actual (reduce-kv (fn [acc id ^doubles arr]
                                      (let [^doubles a (or (acc id) (double-array model/C))]
                                        (dotimes [c model/C] (aset a c (+ (aget a c) (aget arr c))))
                                        (assoc acc id a)))
                                    actual obs)]
              (recur mdl more y actual last-actual pending results))))))))

(defn -main [& args]
  (run (parse-args args)))
