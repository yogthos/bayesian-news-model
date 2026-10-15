(ns bnm.bench
  "Times a forecast of a saved model: jolt -A:tools -M -m bnm.bench path [horizon sims]"
  (:require [bnm.model :as model]
            [bnm.store :as store]))

(defn -main [path & [horizon sims]]
  (let [mdl (store/read-file path)
        h (parse-long (or horizon "13"))
        s (parse-long (or sims "10"))
        t0 (System/nanoTime)
        fc (model/forecast mdl {:horizon h :sims s})]
    (println (count (:nodes mdl)) "nodes, horizon" h "sims" s ":"
             (quot (- (System/nanoTime) t0) 1000000) "ms"
             (model/total fc 0 0 0))))
