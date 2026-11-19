(ns bnm.kind-set-test
  "--kind-set EDN: lattice options for one kind, merged after --set, so one
  command can carry a kind's adopted setting (banking's frailty at rho 0,
  the base of 18 November 2026) and leave the others as they were."
  (:require [bnm.world-run :as wr]
            [clojure.test :refer [deftest is]]))

(deftest a-kind-set-overrides-the-set-for-its-kind-only
  (let [specs {"banking_crisis" {:a 1 :frailty {:x 1}} "coup" {:a 1}}
        out (wr/with-step2 specs {:set "{:frailty {} :b 2}" :kind-set "{\"banking_crisis\" {:frailty {:rhos [0.0]} :c nil}}"} 24311)]
    (is (= {:a 1 :frailty {:rhos [0.0]} :b 2} (out "banking_crisis")) "the kind's map after the set's; nil removes")
    (is (= {:a 1 :frailty {} :b 2} (out "coup")))
    (is (= {"banking_crisis" {:a 1 :frailty {} :b 2} "coup" {:a 1 :frailty {} :b 2}}
           (wr/with-step2 specs {:set "{:frailty {} :b 2}"} 24311))
        "without it: as before")))
