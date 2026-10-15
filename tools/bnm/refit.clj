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
  "{:actual {id double[C]} :last {...}} from a run's rows file."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[id _ ch last actual] (str/split line #"\t")
                    c (.indexOf ^java.util.List bnm.ontology/channels (keyword ch))
                    put (fn [m v] (let [^doubles a (or (m id) (double-array model/C))]
                                    (aset a c (parse-double v)) (assoc m id a)))]
                (-> acc (update :actual put actual) (update :last put last))))
            {:actual {} :last {}} (line-seq r))))

(defn -main [run overrides & [sims]]
  (let [dir (str "data/runs/" run)
        overrides (edn/read-string overrides)
        sims (parse-long (or sims "30"))
        years (->> (.listFiles (io/file dir))
                   (keep #(second (re-matches #"origin-(\d+)\.edn" (.getName %))))
                   (map parse-long) sort)
        results (vec (for [y years
                           :let [rows (io/file dir "rows" (str y ".tsv"))]
                           :when (.exists rows)]
                       (let [mdl (update (store/read-file (str dir "/origin-" y ".edn")) :config merge overrides)
                             fc (assoc (model/forecast mdl {:horizon 52 :sims sims :seed y})
                                       :year y :levels (mapv :level (:nodes mdl)))
                             {:keys [actual last]} (recorded rows)
                             r (ev/score fc actual last)]
                         (println (ev/summary-line r))
                         r)))]
    (println (ev/report-text results))))
