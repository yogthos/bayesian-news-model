(ns bnm.frail
  "Adds regionally frail forecasts to a run: for each of `--models`, its
  forecasts with a shared regional frailty (bnm.onset/frailty), as
  frail-<model>. Onsets of banking, violence and conflict cluster within
  regions beyond the country forecasts (docs/STRUCTURE.md); the frailty
  lifts a region's hazards while its resolved onsets run above them. Its
  discount (0.8 a year, the hedge's) and prior (five expected onsets) are
  set, not tuned on scored years.

    jolt -A:tools -M -m bnm.frail --run r3-pooled --models hedge,pool --out r3-frail

  Writes data/runs/<out>/preds.tsv: the run's rows and the frail ones."
  (:require [bnm.onset :as o]
            [bnm.onsets :as on]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def opts {:discount 0.8 :prior 5.0})

(defn -main [& args]
  (let [{:keys [run models out]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        models (set (str/split models #","))
        src (str "data/runs/" run "/preds.tsv")
        dir (str "data/runs/" out)
        picked (with-open [r (io/reader src)]
                 (reduce (fn [acc line]
                           (let [[kind w m q unit p y] (str/split line #"\t")]
                             (if (models m)
                               (update acc [kind w m] (fnil conj [])
                                       [(parse-long q) (parse-double p) (case y "true" true "false" false nil) unit])
                               acc)))
                         {} (line-seq r)))
        lags (:lags (on/read-event-sets "data/slow/events.tsv"))
        region-of (:region (on/unit-groups (set (for [[_ rows] picked [_ _ _ u] rows] u))))]
    (.mkdirs (io/file dir))
    (with-open [w (io/writer (str dir "/preds.tsv"))]
      (with-open [r (io/reader src)]
        (doseq [line (line-seq r)] (.write w (str line "\n"))))
      (doseq [[[kind wname m] rows] (sort-by key picked)]
        (println kind wname m)
        (doseq [[q p y unit] (o/frailty rows region-of (on/lagged (on/windows (keyword wname)) (get lags kind 0)) opts)]
          (.write w (str kind "\t" wname "\t" "frail-" m "\t" q "\t" unit "\t" p "\t" (str y) "\n")))))))
