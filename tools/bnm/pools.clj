(ns bnm.pools
  "Pools onset models fitted in separate runs on the same rows, and what each
  member adds to the pool: the pool of every member, the pool without each
  one (pool-minus-<member>), and the pool of record (history, named, mix,
  named+mix). Members are the non-placebo models of the runs; a model in
  several runs is the same fit (same rows, same inputs) and is read once.
  No model is refit, only the combinations (bnm.onsets/combine).

    jolt -A:tools -M -m bnm.pools --runs onsets-mixties,onsets-regime,... --out onsets-pooled [--drop ties-un,named+ties-un]

  Writes data/runs/<out>/preds.tsv, scored by bnm.anticipation (--bench pool:
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
                                              (#{"pool" "stack" "hedge"} m) (seen [[kind w] m]))
                                        a
                                        (update-in a [[kind w] m] (fnil conj [])
                                                   [(parse-long q) (parse-double p)
                                                    (case y "true" true "false" false nil) unit]))))
                                  {} (line-seq r)))]
              (merge-with merge acc fresh)))
          {} runs))

(defn- pooled
  "The pool's predictions over `members` of `models` (or the combination
  `which`, :pool :stack or :hedge)."
  ([models members window] (pooled models members window :pool))
  ([models members window which]
   (:preds (which (on/combine (into {} (for [m members] [(keyword m) {:preds (models m)}])) window #{which})))))

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
        (let [all (on/combine (into {} (for [m members] [(keyword m) {:preds (models m)}])) window)]
          (doseq [c on/combined] (emit (name c) (:preds (all c)))))
        (when (every? models record) (emit "pool-record" (pooled models record window)))
        (doseq [m members]
          (emit (str "pool-minus-" m) (pooled models (remove #{m} members) window)))
        (.flush w)))))
