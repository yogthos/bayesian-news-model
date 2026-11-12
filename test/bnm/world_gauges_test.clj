(ns bnm.world-gauges-test
  (:require [augur.math]
            [bnm.world-gauges :as wg]
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

;; fix 3: the world gauges once per origin
(def ^:private state3
  (ws/world-state {:food {:entries (for [p (range t0 (+ t0 120))] [p (inc p) (Math/log (+ 100.0 (* 10 (Math/sin (/ p 7.0)))))]) :lag 1}
                   :brent {:entries (for [p (range t0 (+ t0 120))] [p p (Math/log (+ 50.0 (* 5 (Math/cos (/ p 5.0)))))]) :lag 0}
                   :vix {:entries (for [p (range t0 (+ t0 120))] [p p 3.0]) :lag 0}}))

(def ^:private sp3
  (assoc sp :start (* 12 1870) :end (+ t0 119)
         :scales [{:name :quarter-lead :per 3} {:name :year :per 12} {:name :half-decade :per 60}]
         :target (fn [scale c t] (when (and (= scale :year) (#{"KEN" "TZA"} c) (< t (+ t0 96)))
                                   {:y (odd? (+ (quot t 12) (count c))) :known-at (+ t 30)}))))

(deftest the-world-once-per-origin
  (let [s (wg/with-world-once sp3 state3 exposure {:gate nil})
        t (+ t0 107)
        x (fn [place] (:x ((:world-gauges s) :year place t)))]
    (testing "the group holds only the products with the place's exposure: food and energy"
      (is (= {:year 2 :half-decade 2} (:world-width s)))
      (is (= wg/once-products (:names ((:world-gauges s) :year "KEN" t))))
      (let [g (zipmap ws/gauge-names (:x (ws/gauges state3 t {:food 0.2 :energy 0.1 :open 0.4})))]
        (is (= [(:food12-x-food g) (:brent12-x-energy g)] (x "KEN")))))
    (testing "the world term is an offset at the countries only, the same at each"
      (let [o (fn [place] ((:world-offset s) :year place t))]
        (is (number? (:x (o "KEN"))))
        (is (= (:x (o "KEN")) (:x (o "TZA"))))
        (is (nil? (o "s/eastern-africa")))
        (is (nil? ((:world-offset s) :quarter-lead "KEN" t)) "not at the quarters")
        (is (every? #(<= (:known-at %) t) (:reads (o "KEN"))))))
    (testing "the VIX, credit, the dollar and the policy rate are not read"
      (is (= #{:food :brent} (set (map :series (:reads ((:world-gauges s) :year "KEN" t))))))
      (is (= #{:food :brent} (set (map :series (:reads ((:world-offset s) :year "KEN" t)))))))
    (testing "the harness column enters the term, not the group"
      (let [s2 (wg/with-world-once sp3 state3 exposure {:gate nil :extra (fn [t] (Math/sin t))})]
        (is (= {:year 2 :half-decade 2} (:world-width s2)))
        (is (not= (:x ((:world-offset s) :year "KEN" t)) (:x ((:world-offset s2) :year "KEN" t))))))))

;; bnm-56c.28: the lead test gates the term per kind. Thirty years in which
;; every country's year onset follows world food momentum at the origin and
;; Brent carries nothing: food passes, Brent and the harness's noise do not,
;; and a gauge that fails is read nowhere for the kind, neither in the term
;; nor in its product with the place's exposure
(def ^:private t1 (* 12 1980))

(def ^:private state4
  (let [g (augur.math/rng 7)]
    (ws/world-state {:food {:entries (for [p (range t1 (+ t1 360))] [p (inc p) (Math/log (+ 100.0 (* 10 (Math/sin (/ p 7.0)))))]) :lag 1}
                     :brent {:entries (for [p (range t1 (+ t1 360))] [p p (Math/log (+ 50.0 (* 5 (augur.math/normal! g))))]) :lag 0}})))

(def ^:private sp4
  (assoc sp :start (* 12 1870) :end (+ t1 359)
         :scales [{:name :quarter-lead :per 3} {:name :year :per 12} {:name :half-decade :per 60}]
         :target (fn [scale c t]
                   (when (and (= scale :year) (#{"KEN" "TZA"} c) (< (* 12 1981) t (+ t1 330)))
                     (let [f (ws/change (ws/reader state4 t) :food 12)]
                       {:y (> (+ f (if (= c "KEN") 0.02 -0.02)) 0.0) :known-at (+ t 30)})))))

(deftest the-lead-test-gates-the-term-per-kind
  (let [s (wg/with-world-once sp4 state4 exposure)
        t (+ t1 323)
        o ((:world-offset s) :year "KEN" t)]
    (testing "food momentum leads the onsets and passes; Brent fails and is not wired"
      (is (= [:food12] (:passed o)))
      (let [[food brent] (:x ((:world-gauges s) :year "KEN" t))]
        (is (number? food))
        (is (nil? brent) "Brent's product column reads unknown")))
    (testing "the term is food's alone: the same as with Brent never offered"
      (let [food-only (wg/with-world-once sp4 (dissoc state4 :brent) exposure)
            ungated (wg/with-world-once sp4 state4 exposure {:gate nil})]
        (is (= (:x o) (:x ((:world-offset food-only) :year "KEN" t))))
        (is (not= (:x o) (:x ((:world-offset ungated) :year "KEN" t))) "ungated, Brent moves it")))
    (testing "the harness's noise enters as an ordinary gauge and is dropped by the same test"
      (let [noisy (wg/with-world-once sp4 state4 exposure {:extra (wg/extra-column {:kind :noise :seed 1})})
            on ((:world-offset noisy) :year "KEN" t)]
        (is (= [:food12] (:passed on)))
        (is (= (:x o) (:x on)))))
    (testing "early, before the test has five online errors, nothing is wired"
      (let [early (+ t1 71)]
        (is (= [] (:passed ((:world-offset s) :year "KEN" early))))
        (is (= 0.0 (:x ((:world-offset s) :year "KEN" early))))
        (is (= [nil nil] (:x ((:world-gauges s) :year "KEN" early))))))))
