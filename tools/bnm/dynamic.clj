(ns bnm.dynamic
  "Adds multilevel dynamic forecasts to a run: for each of `--models`, its
  forecasts scaled by latent states over the tree of world, regions,
  subregions and countries (bnm.onset/dynamic-frailty), as dyn-<model>.
  Each node's state is an AR(1) on the log scale of the onsets that came
  against those the model expected, its evidence the onsets of every
  country under it (bottom up), its state lifting every country under it
  (top down), each level's persistence and variance learned each year from
  the onsets known by then. A row reads the states as of its quarter,
  less the kind's reporting lag, and runs them forward over its window.

  With --levels, also the chance of an onset in any country of each
  subregion or region with the states shared (bnm.onset/dynamic-groups),
  written to preds-<level>.tsv, which bnm.anticipation --level reads beside
  the independent aggregate of the countries' forecasts.

    jolt -A:tools -M -m bnm.dynamic --run r6-pooled --models hedge,named --out r7-dyn [--levels subregion,region]

  Writes data/runs/<out>/preds.tsv: the run's rows and the dynamic ones."
  (:require [bnm.onset :as o]
            [bnm.onsets :as on]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def opts
  "Starting persistence and stationary sd of the world, region, subregion
  and country states, and the grids each year's search chooses them from
  (by the states' one-step-ahead forecasts of the onsets known by the
  year's start; size zero drops a level), warm from the year before. Set
  in advance, not tuned on scored years: half-lives from about a season
  to four years, sizes up to doubling or halving the rate."
  {:rho [0.8 0.8 0.8 0.8] :sd [0.0 0.0 0.0 0.0] :rhos [0.5 0.8 0.95] :sds [0.0 0.15 0.3 0.6] :passes 1 :penalty 2.0})

(def draw-opts {:draws 400 :seed 1})

(defn paths
  "{unit [world region subregion unit]} for the units in the M49 tree."
  [units]
  (into {} (for [u units :let [p (on/hier-path u)] :when (= 3 (count p))] [u (into ["world"] p)])))

(defn -main [& args]
  (let [{:keys [run models out levels]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        models (set (str/split models #","))
        levels (when levels (str/split levels #","))
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
        sets (on/read-event-sets "data/slow/events.tsv")
        units (set (for [[_ rows] picked [_ _ _ u] rows] u))
        tree (paths units)
        groups (on/unit-groups units)
        ;; the states depend on the kind and model, not the window
        fits (memoize (fn [kind m] (o/dynamic-fits (get picked [kind "w1" m]) (get-in sets [:onsets kind]) tree
                                                   (get-in sets [:lags kind] 0) opts)))]
    (.mkdirs (io/file dir))
    (with-open [w (io/writer (str dir "/preds.tsv"))]
      (with-open [r (io/reader src)]
        (doseq [line (line-seq r)] (.write w (str line "\n"))))
      (doseq [[[kind wname m] rows] (sort-by key picked)
              :let [w1 (get picked [kind "w1" m])]
              :when w1]
        (println kind wname m) (flush)
        (doseq [[q p y unit] (o/dynamic-frailty rows (fits kind m) tree (on/windows (keyword wname)) (get-in sets [:lags kind] 0))]
          (.write w (str kind "\t" wname "\t" "dyn-" m "\t" q "\t" unit "\t" p "\t" (str y) "\n")))
        (.flush w)))
    (doseq [level levels
            :let [group-of (get groups (keyword level))]]
      (with-open [w (io/writer (str dir "/preds-" level ".tsv"))]
        (doseq [[[kind wname m] rows] (sort-by key picked)
                :let [w1 (get picked [kind "w1" m])]
                :when w1]
          (println level kind wname m) (flush)
          (doseq [[q p y g] (o/dynamic-groups rows (fits kind m) tree group-of (on/windows (keyword wname))
                                              (get-in sets [:lags kind] 0) draw-opts)]
            (.write w (str kind "\t" wname "\t" "dyn-" m "\t" q "\t" g "\t" p "\t" (str y) "\n")))
          (.flush w))))))
