(ns bnm.test-runner
  (:require [clojure.test :as t]
            [bnm.math-test]
            [bnm.calibrate-test]
            [bnm.core-test]
            [bnm.model-test]
            [bnm.store-test]))

(def namespaces
  '[bnm.math-test
    bnm.model-test
    bnm.core-test
    bnm.calibrate-test
    bnm.store-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply t/run-tests namespaces)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
