(ns bnm.rescore
  "Rebuilds a training run's scored.edn and report from the rows it wrote
  for each period (rows/<period>.tsv) and its periods.edn, for a run that
  stopped before writing its report.

    jolt -A:tools -M -m bnm.rescore v2-q

  The calibrated probabilities are replayed: each period's forecast is
  calibrated by what the periods before it taught, as in training."
  (:require [bnm.calibrate :as cal]
            [bnm.eval :as ev]
            [bnm.model :as model]
            [bnm.train :as train]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- horizon
  "Bins in the period labelled `lbl`: `every`, or the bins of the year."
  [lbl every bin-days]
  (or every
      (let [y (parse-long lbl)
            mid (quot (* (- y 1970) 36525) (* 100 bin-days))]
        (count (filter #(= y (train/bin-year % bin-days)) (range (- mid 60) (+ mid 60)))))))

(defn- read-rows
  "The escalation rows of a rows file, as maps."
  [path h sims period]
  (with-open [r (io/reader path)]
    (into [] (keep (fn [line]
                     (let [[id level ch st last actual _ _ _ _ p p-hist p-clim] (str/split line #"\t")
                           ch (keyword ch)]
                       (when (some #{ch} ev/escalation-channels)
                         (let [last (parse-double last) actual (parse-double actual)]
                           {:id id :level (keyword (subs level 1)) :channel ch :stratum (keyword st)
                            :last last :actual actual :p-escalate (parse-double p) :sims sims
                            :p-hist (parse-double p-hist) :p-clim (parse-double p-clim)
                            :escalated (ev/escalated? actual last h) :period period})))))
          (line-seq r))))

(defn -main [run]
  (let [dir (str "data/runs/" run)
        {:keys [every sims config]} (edn/read-string (slurp (str dir "/args.edn")))
        bin-days (:bin-days (merge model/defaults config))
        periods (edn/read-string (slurp (str dir "/periods.edn")))
        scored-path (str dir "/scored.edn")
        out (ev/open-scored scored-path)
        results (loop [[r & more] periods calibration nil acc []]
                  (if-not r
                    acc
                    (let [lbl (:period r)
                          rows (read-rows (str dir "/rows/" lbl ".tsv") (horizon lbl every bin-days) sims lbl)
                          scored (vec (for [x rows :when (ev/scored-kinds (:level x))]
                                        (assoc x :p-cal (cal/calibrate calibration [(:channel x) (:stratum x)]
                                                                       (:p-escalate x)))))
                          outcomes (for [x rows :when (not (#{:region :world} (:level x)))]
                                     {:channel [(:channel x) (:stratum x)] :p (:p-escalate x)
                                      :outcome (:escalated x)})
                          summary (ev/summary-line (assoc r :scored (filter #(= :country (:level %)) scored)))]
                      (ev/write-scored! out scored)
                      (println summary)
                      (recur more (cal/learn calibration outcomes) (conj acc (assoc r :summary summary))))))]
    (ev/close-scored! out)
    (ev/write-report! dir results scored-path)))
