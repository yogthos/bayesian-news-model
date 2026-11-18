(ns bnm.lead-delay-test
  "bnm-pjg.6, --lead-delay n: the leading gauges read n months late, so a
  gain that survives a quarter's delay is not same-quarter leakage."
  (:require [bnm.lattice-political :as lp]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is]]))

(deftest lead-delay-option
  (is (nil? (wr/lead-delay-choice {})))
  (is (= 3 (wr/lead-delay-choice {:lead-delay "3" :leading "true"})))
  (is (thrown? Exception (wr/lead-delay-choice {:lead-delay "3"})) "needs --leading true")
  (is (thrown? Exception (wr/lead-delay-choice {:lead-delay "-1" :leading "true"})))
  (is (thrown? Exception (wr/lead-delay-choice {:lead-delay "q" :leading "true"}))))

(deftest the-gauges-are-read-as-they-were-n-months-before
  (let [f (fn [u t] [u t])]
    (is (identical? f (lp/delayed-lead f nil)) "off: the gauges as before")
    (is (identical? f (lp/delayed-lead f 0)))
    (is (= ["USA" 24308] ((lp/delayed-lead f 3) "USA" 24311)))))
