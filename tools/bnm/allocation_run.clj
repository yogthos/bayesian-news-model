(ns bnm.allocation-run
  "Runs the allocation channel's modules (bnm.modules.allocation) over the
  panel from scripts/allocation-data.py and writes

    data/derived/coupled/allocation.csv        the coupled table's rows
                                               (bnm.engine/table-header)
    data/derived/coupled/allocation-graph.txt  the modules, edges and order

  The declarations are validated first (bnm.engine/check!), and every row
  is checked against its known year as it is emitted.

  Usage: jolt -A:tools -M -m bnm.allocation-run [from to]"
  (:require [bnm.allocation :as al]
            [bnm.engine :as eng]
            [bnm.modules.allocation :as mal]))

(def panel-path "data/derived/allocation/panel.csv")
(def out-path "data/derived/coupled/allocation.csv")
(def graph-path "data/derived/coupled/allocation-graph.txt")

(defn -main [& [from to]]
  (let [from (or (some-> from parse-long) 1950)
        to (or (some-> to parse-long) 2026)
        idx (al/index (al/read-panel panel-path))
        links (al/load-links "data/derived/supply/concordance.csv" "data/derived/supply/stages.csv")
        ms (eng/check! (mal/modules idx links))
        w (eng/table-writer out-path ms)
        n (atom 0)]
    (spit graph-path (str (eng/dump ms) "\n"))
    (eng/run ms {:from from :to to :keep false
                 :on-step (fn [id t e] (swap! n + (count (eng/table-rows (first (filter #(= id (:module %)) ms)) t e))) ((:write w) id t e))})
    ((:close w))
    (println (str "allocation: " (count (:products links)) " products and " (count (:stages links)) " stages linked; "
                  @n " rows " from "-" to " -> " out-path))))
