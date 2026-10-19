(ns bnm.pools
  "Pools onset models fitted in separate runs on the same rows, and what each
  member adds to the pool: the pools of every member, the converged pool
  (mlpool) without each one (pool-minus-<member>), and the pool of record (history, named, mix,
  named+mix). Members are the non-placebo models of the runs; a model in
  several runs is the same fit (same rows, same inputs) and is read once.
  No model is refit, only the combinations (bnm.onsets/combine).

    jolt -A:tools -M -m bnm.pools --runs onsets-mixties,onsets-regime,... --out onsets-pooled [--drop ties-un,named+ties-un]

  Writes data/runs/<out>/preds.tsv, scored by bnm.anticipation (--bench mlpool:
  a member whose removal loses recall carries something the rest don't)."
  (:require [bnm.onsets :as on]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def record ["history" "named" "mix" "named+mix"])

(defn- read-runs
  "{[kind window] {model [[q p y unit] ...]}} over `runs`, the first run's
  copy of a model kept; base and placebos dropped."
  [runs drop?]
  (reduce (fn [acc run]
            (let [seen (set (for [[kw ms] acc, m (keys ms)] [kw m]))
                  fresh (with-open [r (io/reader (str "data/runs/" run "/preds.tsv"))]
                          (reduce (fn [a line]
                                    (let [[kind w m q unit p y] (str/split line #"\t")]
                                      (if (or (= m "base") (str/ends-with? m "-placebo") (drop? m)
                                              ((set (map name on/combined)) m) (seen [[kind w] m]))
                                        a
                                        (update-in a [[kind w] m] (fnil conj [])
                                                   [(parse-long q) (parse-double p)
                                                    (case y "true" true "false" false nil) unit]))))
                                  {} (line-seq r)))]
              (merge-with merge acc fresh)))
          {} runs))

(defn -main [& args]
  (let [{:keys [runs out drop]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        runs (str/split runs #",")
        drop? (set (when drop (str/split drop #",")))
        by-kw (read-runs runs drop?)
        dir (str "data/runs/" out)]
    (.mkdirs (io/file dir))
    (with-open [w (io/writer (str dir "/preds.tsv"))]
      (doseq [[[kind wname :as kw] models] (sort by-kw)
              :let [window (on/windows (keyword wname))
                    members (sort (keys models))
                    emit (fn [label preds]
                           (doseq [[q p y unit] preds]
                             (.write w (str kind "\t" wname "\t" label "\t" q "\t" unit "\t" p "\t" (str y) "\n"))))]]
        (println kind wname (count members) "members:" (str/join " " members))
        (doseq [m members] (emit m (models m)))
        ;; the members' forecasts aligned once; a sub-pool takes their columns
        (let [[ms rows] (on/aligned (into {} (for [m members] [(keyword m) {:preds (models m)}])))
              col (zipmap (map name ms) (range))
              sub (fn [names] (let [ix (mapv col names)] (mapv (fn [r] (update r :ps #(mapv % ix))) rows)))
              all (on/combine-rows rows window (set on/combined))]
          (doseq [c on/combined] (emit (name c) (:preds (all c))))
          (when (every? models record)
            (emit "pool-record" (:preds (:pool (on/combine-rows (sub record) window #{:pool})))))
          ;; the converged pools without each member, eight at a time
          (doseq [batch (partition-all 8 members)
                  [m preds] (mapv (fn [[m f]] [m @f])
                                  (doall (for [m batch]
                                           [m (future (:preds (:mlpool (on/combine-rows (sub (remove #{m} members)) window #{:mlpool}))))])))]
            (emit (str "pool-minus-" m) preds)))
        (.flush w)))))
