(ns bnm.world-gauges-test
  (:require [bnm.world-gauges :as wg]
            [bnm.world-state :as ws]
            [clojure.test :refer [deftest is testing]]))

(def ^:private t0 (* 12 2000))

(def ^:private state
  (ws/world-state {:food {:entries (for [p (range t0 (+ t0 60))] [p (inc p) (Math/log (+ 100.0 (- p t0)))]) :lag 1}
                   :credit {:entries (for [p (range t0 (+ t0 60))] [p p 2.0]) :lag 0}}))

(def ^:private sp
  {:scales [{:name :quarter-lead} {:name :year} {:name :half-decade}]
   :parent {"KEN" "s/eastern-africa" "TZA" "s/eastern-africa" "s/eastern-africa" "r/africa" "r/africa" "world"}})

(def ^:private exposure
  (fn [c _] ({"KEN" {:food 0.2 :energy 0.1 :open 0.4} "TZA" {:food 0.4 :energy nil :open 0.6}} c)))

(deftest the-world-group-at-the-year-and-half-decade
  (let [s (wg/with-world sp state exposure)
        t (+ t0 40)
        names ws/gauge-names
        at (fn [place] (zipmap names (:x ((:world-gauges s) :year place t))))]
    (is (= {:year 12 :half-decade 12} (:world-width s)) "not at the quarters")
    (testing "a country: the world's moves and their products with its exposure"
      (is (< (Math/abs (- (:food12-x-food (at "KEN")) (* 0.2 (:food12 (at "KEN"))))) 1e-12))
      (is (= (* 2.0 0.4) (:credit3-x-open (at "KEN")))))
    (testing "above the countries: their mean exposure over those known"
      (is (< (Math/abs (- (:food12-x-food (at "s/eastern-africa")) (* 0.3 (:food12 (at "KEN"))))) 1e-12))
      (is (< (Math/abs (- (:credit3-x-open (at "world")) (* 2.0 0.5))) 1e-12))
      (is (nil? (:brent12-x-energy (at "world"))) "no Brent in this state: unknown"))
    (testing "its reads, none after t"
      (is (every? #(<= (:known-at %) t) (:reads ((:world-gauges s) :year "KEN" t)))))))

(deftest the-harness-columns
  (let [noise (wg/extra-column {:kind :noise :seed 1})
        false-move (wg/extra-column {:kind :false :seed 1})
        share (fn [t] (if (even? (quot t 12)) 0.4 0.1))
        ys (range (+ 11 (* 12 1990)) (* 12 2020) 12)
        planted (wg/extra-column {:kind :planted :seed 1 :strength 1.0 :share share :year-origins ys})]
    (is (= (noise 24000) (noise 24000)) "seeded: the same at every place")
    (is (not= (noise 24000) (noise 24012)))
    (testing "the false move: the noise plus 3 in 2013 and 2014 only"
      (is (= (+ 3.0 (noise (+ 5 (* 12 2013)))) (false-move (+ 5 (* 12 2013)))))
      (is (= (noise (+ 5 (* 12 2015))) (false-move (+ 5 (* 12 2015))))))
    (testing "the planted column moves with the onset share"
      (let [hi (filter #(even? (quot % 12)) ys) lo (remove #(even? (quot % 12)) ys)
            mean (fn [ts] (/ (reduce + (map planted ts)) (count ts)))]
        (is (> (- (mean hi) (mean lo)) 1.5))))
    (is (nil? (wg/extra-column {:kind nil})))
    (testing "with an extra column the group is one wider"
      (let [s (wg/with-world sp state exposure {:extra noise})]
        (is (= 13 (count (:x ((:world-gauges s) :year "KEN" (+ t0 40))))))
        (is (= {:year 13 :half-decade 13} (:world-width s)))))))
