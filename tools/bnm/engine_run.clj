(ns bnm.engine-run
  "The coupled engine, phase A (bead bnm-4hv.9; docs/COUPLED-SYSTEMS.md
  6e): today's models as modules (bnm.modules.stocks) stepped causally
  year by year from their stock tables, written as one table, and the
  golden check that the table reproduces every stock table exactly.

    jolt -A:tools -M -m bnm.engine-run [--out data/derived/coupled] [--check-only]

  Writes <out>/state.csv (iso,year,module,variable,value,sd,data_year,
  known,source; year the year known), <out>/graph.edn (the declarations,
  edges and orders) and <out>/graph.txt (the same as text), then prints
  the causal step order, the rows per module and, per stock table, the
  mismatches between bnm.states reading the file and reading the coupled
  table (each a bug in the wrapper). --check-only skips the run and checks
  an existing <out>. Exits 1 on any mismatch or row left unstepped."
  (:require [bnm.engine :as eng]
            [bnm.modules.stocks :as ms]
            [clojure.java.io :as io]))

(defn -main [& args]
  (let [opts (into {} (for [[k v] (partition-all 2 args)] [k v]))
        out (get opts "--out" "data/derived/coupled")
        check-only (contains? (set args) "--check-only")
        mods (filter #(.exists (io/file (:table %))) (ms/modules))
        missing (remove (set (map :module mods)) (map :module (ms/modules)))
        t0 (System/currentTimeMillis)
        secs #(quot (- (System/currentTimeMillis) t0) 1000)]
    (when (seq missing) (println "tables not on disk, left out:" (vec missing)))
    (let [co (eng/causal-order mods)]
      (println "causal step order:" (:text co))
      (doseq [[g ids] (:groups co)] (println (str "  " (name g) ": " (clojure.string/join ", " (map name ids)))))
      (doseq [v (:violations co)] (println "  VIOLATION:" v)))
    (println "edges:" (count (eng/edges mods)))
    (doseq [e (eng/edges mods)] (println (str "  " (name (:from e)) " -> " (name (:to e)) "  " (:var e) "  lag " (:lag e))))
    (let [res (when-not check-only (ms/run! mods out {:from 1800 :to 2030}))
          _ (when res
              (println "wrote" (str out "/state.csv") "in" (secs) "s")
              (doseq [[id n] (sort-by key (:rows res))] (println (format "  %-16s %9d rows, %d left" (name id) n (get-in res [:leftover id])))))
          gold (ms/golden mods out)]
      (println "golden: bnm.states from the files against from the coupled table")
      (doseq [{:keys [table vars mismatches first]} gold]
        (println (format "  %-44s %4d variables  %d mismatches%s" table vars mismatches (if (seq first) (str " e.g. " first) ""))))
      (println "checked in" (secs) "s")
      (shutdown-agents)
      (System/exit (if (and (every? (comp zero? :mismatches) gold) (every? zero? (vals (:leftover res)))) 0 1)))))
