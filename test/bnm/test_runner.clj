(ns bnm.test-runner
  (:require [clojure.test :as t]
            [bnm.time-test]
            [bnm.calibrate-test]
            [bnm.core-test]
            [bnm.model-test]
            [bnm.store-test]
            [bnm.slow-test]
            [bnm.stocks-test]
            [bnm.trade-test]
            [bnm.polecon-test]
            [bnm.invariants-test]
            [bnm.pit-test]
            [bnm.mix-test]
            [bnm.eval-test]
            [bnm.ties-test]
            [bnm.onset-test]
            [bnm.onsets-test]
            [bnm.precursors-test]
            [bnm.paired-test]
            [bnm.lattice-test]
            [bnm.lattice-banking-test]
            [bnm.political-gauges-test]
            [bnm.reconcile-test]
            [bnm.economic-events-test]
            [bnm.anatomy-test]
            [bnm.calm-test]
            [bnm.sync-test]
            [bnm.ingest-test]
            [bnm.fstats-test]
            [bnm.vol-test]
            [bnm.normal-test]))

(def namespaces
  '[bnm.time-test
    bnm.model-test
    bnm.core-test
    bnm.calibrate-test
    bnm.store-test
    bnm.slow-test
    bnm.stocks-test
    bnm.trade-test
    bnm.polecon-test
    bnm.invariants-test
    bnm.pit-test
    bnm.mix-test
    bnm.eval-test
    bnm.ties-test
    bnm.onset-test
    bnm.onsets-test
    bnm.precursors-test
    bnm.paired-test
    bnm.lattice-test
    bnm.lattice-banking-test
    bnm.political-gauges-test
    bnm.reconcile-test
    bnm.economic-events-test
    bnm.anatomy-test
    bnm.calm-test
    bnm.sync-test
    bnm.ingest-test
    bnm.fstats-test
    bnm.vol-test
    bnm.normal-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply t/run-tests namespaces)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
