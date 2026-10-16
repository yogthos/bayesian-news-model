(ns bnm.refit
  "Re-forecasts saved origins of a training run with different forecast
  settings, and scores them against the actuals the run recorded. For tuning
  what happens at forecast time without retraining.

    jolt -A:tools -M -m bnm.refit v2 '{:max-branching 0.9}' [sims]"
  (:require [bnm.eval :as ev]
            [bnm.model :as model]
            [bnm.store :as store]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- recorded
  "The actual totals {id double[C]} from a run's rows file."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[id _ ch _ _ actual] (str/split line #"\t")
                    c (.indexOf ^java.util.List bnm.ontology/channels (keyword ch))
                    ^doubles a (or (acc id) (double-array model/C))]
                (aset a c (parse-double actual))
                (assoc acc id a)))
            {} (line-seq r))))

(defn- context
  "The scoring context saved with an origin, its histories as arrays again."
  [path]
  (-> (edn/read-string (slurp path))
      (update :hist (fn [h] (into {} (for [[id v] h] [id (mapv double-array v)]))))))

(defn -main [run overrides & [sims]]
  (let [dir (str "data/runs/" run)
        overrides (edn/read-string overrides)
        sims (parse-long (or sims "100"))
        every (:every (edn/read-string (slurp (str dir "/args.edn"))))
        labels (->> (.listFiles (io/file dir))
                    (keep #(second (re-matches #"origin-(\d+)\.edn" (.getName %))))
                    sort)
        results (vec (for [lbl labels
                           :let [rows (io/file dir "rows" (str lbl ".tsv"))]
                           :when (.exists rows)]
                       (let [mdl (update (store/read-file (str dir "/origin-" lbl ".edn")) :config merge overrides)
                             horizon (or every 52)
                             fc (assoc (model/forecast mdl {:horizon horizon :sims sims :seed (parse-long lbl)})
                                       :period lbl :levels (mapv :level (:nodes mdl))
                                       :calibration (:calibration mdl))
                             ctx (assoc (context (str dir "/context-" lbl ".edn"))
                                        :actual (recorded rows))
                             r (dissoc (ev/score fc ctx) :rows)]
                         (println (ev/summary-line r))
                         r)))]
    (println (ev/report-text results))))
