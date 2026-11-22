(ns bnm.aftermath-test
  "bnm.aftermath (docs/CALIBRATION.md t133): onset kernels of known onsets
  as the year node's gauges, fit with the node's other gauges."
  (:require [bnm.aftermath :as af]
            [clojure.test :refer [deftest is testing]]))

(def ^:private close? #(< (Math/abs (- (double %1) (double %2))) 1e-9))

(deftest the-node's-kernels-read-only-known-onsets
  ;; t133: an onset dated m is known at m + its kind's publication lag; the
  ;; year node's own history gauges read the same rule
  (let [ons {"food" {"A" [100] "B" [100] "C" [40]} "coup" {"A" [150]}}
        lags {"food" 18 "coup" 3}
        row (af/known-row ons lags "food" ["coup" "food"] ["A" "B" "C" "D"])
        k (fn [d h] (Math/pow 0.5 (/ d h)))]
    (testing "at 117 food's onsets at 100 are not yet known, C's at 40 is"
      (let [[f s w o] (row "A" 117)
            wf (/ (k 77 12.0) 4)]
        (is (close? f (- wf)))
        (is (close? w wf))
        (is (= 0.0 o))))
    (testing "at 172 food's at 100 are known (decayed from 100), coup's at 150 too"
      (let [[f s w o] (row "A" 172)
            wf (/ (+ (k 72 12.0) (k 72 12.0) (k 132 12.0)) 4)
            ws (/ (+ (k 72 48.0) (k 72 48.0) (k 132 48.0)) 4)]
        (is (close? f (- (k 72 12.0) wf)))
        (is (close? s (- (k 72 48.0) ws)))
        (is (close? w wf))
        (is (close? o (k 22 12.0)))))
    (is (close? 0.0 (last (row "A" 152))) "coup's at 150 not known until 153")
    (is (= (count af/added-names) (count (row "A" 172))))))

(defn- gauge-spec []
  {:scales [{:name :quarter :per 3 :horizon 3} {:name :year :per 12 :horizon 12}]
   :levels [[:country ["A" "B"]] [:world ["world"]]]
   :parent {"A" "world" "B" "world"}
   :start 0 :end 1199
   :gauges (fn [scale place t] (if (= :year scale) [1.0 2.0] [3.0]))
   :gauge-width {:quarter 1 :year 2}
   :gauge-groups {:year {:a [0] :b [1]}}
   :gauge-names {:year [:x :y]}})

(deftest the-kernels-join-the-year-node's-gauges
  (let [row (fn [u t] (if (= u "A") [(double t) 0.0 1.0 0.0] [0.0 (double t) 1.0 0.0]))
        sp (af/with-gauges (gauge-spec) row :stable)]
    (is (= 6 (get-in sp [:gauge-width :year])))
    (is (= 1 (get-in sp [:gauge-width :quarter])) "other scales untouched")
    (is (= [2 3 4 5] (get-in sp [:gauge-groups :year :aftermath])))
    (is (= [0] (get-in sp [:gauge-groups :year :a])))
    (is (= [:x :y :own-fast-net :own-slow-net :world-fast :others-fast] (get-in sp [:gauge-names :year])))
    (is (= [3.0] ((:gauges sp) :quarter "A" 600)))
    (let [g ((:gauges sp) :year "A" 1199)]
      (is (= 6 (count g)))
      (is (= [1.0 2.0] (subvec g 0 2)) "the node's own gauges first, as they were")
      (is (pos? (nth g 2)) "standardized on the past: A's rising column sits above its mean")
      (is (every? #(<= (Math/abs (double %)) 5.0) (subvec g 2))))
    (testing "an aggregate reads its countries' mean"
      (let [a ((:gauges sp) :year "A" 1199) b ((:gauges sp) :year "B" 1199) w ((:gauges sp) :year "world" 1199)]
        (is (close? (nth w 2) (/ (+ (nth a 2) (nth b 2)) 2)))))
    (testing "early origins: too few values seen, z 0"
      (is (= [0.0 0.0 0.0 0.0] (subvec ((:gauges sp) :year "A" 11) 2))))))

(deftest a-spec-without-named-groups-keeps-its-gauges-as-one
  (let [sp (af/with-gauges (dissoc (gauge-spec) :gauge-groups :gauge-names) (constantly [0.0 0.0 0.0 0.0]) :stable)]
    (is (= {:gauges [0 1] :aftermath [2 3 4 5]} (get-in sp [:gauge-groups :year])))
    (is (nil? (:gauge-names sp)) "no names where the spec had none")))
