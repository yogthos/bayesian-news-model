(ns bnm.lead-test-test
  (:require [augur.math :as m]
            [bnm.lead-test :as lt]
            [clojure.test :refer [deftest is testing]]))

;; A rate over the 24 months after each origin that follows a world series'
;; mean over those months. A gauge read at the origin that runs ahead of
;; the series leads; the series itself, read at the origin, is only
;; concurrent: its value then says little about the next two years.
(deftest a-leading-gauge-passes-a-concurrent-one-fails
  (let [g (m/rng 3)
        n 2400
        ;; the world series: a fresh level every 24 months
        level (vec (repeatedly (inc (quot n 24)) #(m/normal! g)))
        series (mapv #(level (quot % 24)) (range n))
        window (fn [t] (let [vs (subvec series (inc t) (+ t 25))] (/ (reduce + vs) 24.0)))
        rate (fn [t] (when (< (+ t 25) n) {:rate (m/expit (+ -2.0 (window t))) :known-at (+ t 42)}))
        origins (range 23 (- n 30) 24)
        ;; a lead: the next block's level, known at its origin, with noise
        lead (into {} (for [t origins] [t (+ (level (quot (inc t) 24)) (* 0.3 (m/normal! g)))]))
        leading (lt/lead-test {:gauge lead :window-mean window :rate rate :origins origins})
        concurrent (lt/lead-test {:gauge #(series %) :window-mean window :rate rate :origins origins})]
    (is (:pass leading) leading)
    (is (> (:lead leading) 0.8) leading)
    (testing "the series at the origin is the block that just ended: concurrent, not leading"
      (is (not (:pass concurrent)) concurrent)
      (is (> (:concurrent concurrent) 0.9))
      (is (< (Math/abs (:lead concurrent)) 0.4) concurrent))
    (testing "only rows known at each origin are fitted"
      (let [late (lt/lead-test {:gauge lead :window-mean window :origins origins
                                :rate (fn [t] (some-> (rate t) (assoc :known-at 1e9)))})]
        (is (nil? (:online late)))
        (is (not (:pass late)))))))
