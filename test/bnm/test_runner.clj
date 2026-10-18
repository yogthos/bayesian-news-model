(ns bnm.test-runner
  (:require [clojure.test :as t]
            [bnm.math-test]
            [bnm.calibrate-test]
            [bnm.core-test]
            [bnm.model-test]
            [bnm.store-test]
            [bnm.slow-test]
            [bnm.stocks-test]
            [bnm.trade-test]
            [bnm.hazard-test]
            [bnm.polecon-test]
            [bnm.invariants-test]
            [bnm.pit-test]
            [bnm.mix-test]
            [bnm.eval-test]))

(def namespaces
  '[bnm.math-test
    bnm.model-test
    bnm.core-test
    bnm.calibrate-test
    bnm.store-test
    bnm.slow-test
    bnm.stocks-test
    bnm.trade-test
    bnm.hazard-test
    bnm.polecon-test
    bnm.invariants-test
    bnm.pit-test
    bnm.mix-test
    bnm.eval-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply t/run-tests namespaces)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
