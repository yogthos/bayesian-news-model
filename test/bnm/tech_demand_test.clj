(ns bnm.tech-demand-test
  (:require [bnm.tech-demand :as td]
            [bnm.technology :as tech]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (and a b (< (Math/abs (- (double a) (double b))) tol)))

(defn- logistic-entries
  "Entries {:t :known :x} of K/(1+exp(-s(t-tau))), known `lag` years on."
  [K s tau years lag]
  (vec (for [t years] {:t t :known (+ t lag) :x (tech/logistic K s tau t)})))

;; ---------------------------------------------------------------- inputs

(deftest tonnes-scaled-by-cover
  (is (close? 200.0 (td/cover-tonnes 100.0 1000.0 500.0 0.5) 1e-9) "half the value has tonnes: doubled")
  (is (nil? (td/cover-tonnes 100.0 1000.0 400.0 0.5)) "under the cover floor: unknown")
  (is (close? 100.0 (td/cover-tonnes 100.0 1000.0 1000.0 0.5) 1e-9))
  (is (nil? (td/cover-tonnes nil 1000.0 nil 0.5))))

(deftest additions-from-a-stock
  (let [st [{:t 2000 :known 2002 :x 10.0} {:t 2001 :known 2003 :x 15.0}
            {:t 2002 :known 2004 :x 14.0} {:t 2004 :known 2006 :x 20.0}]]
    (is (= [{:t 2001 :known 2003 :x 5.0} {:t 2002 :known 2004 :x 0.0}]
           (td/additions st))
        "consecutive years only, a fall adds nothing (retirements are not negative demand)")))

(deftest cumulative-sums
  (is (= [{:t 2000 :known 2002 :x 1.0} {:t 2001 :known 2003 :x 3.0}]
         (td/cumulative [{:t 2001 :known 2003 :x 2.0} {:t 2000 :known 2002 :x 1.0}]))))

;; ---------------------------------------------------------------- arithmetic and units

(def ^:private ints
  {"ev/battery" {2020 50.0} "li-ion/lithium" {2020 0.1} "li-ion/cobalt" {2020 0.2}
   "li-ion/nickel" {2020 0.5} "li-ion/graphite" {2020 1.0} "ev/copper" {2020 60.0}
   "pv/silver" {2020 15.0} "pv/silicon" {2020 3000.0} "pv/copper" {2020 2800.0} "pv/steel" {2020 50000.0}
   "wind-onshore/steel" {2020 130000.0} "wind-onshore/copper" {2020 2900.0}
   "wind-onshore/rare-earths" {2020 14.0}})

(deftest demand-arithmetic-and-units
  (testing "EVs: cars x kWh/car x kg/kWh, in tonnes"
    (let [d (td/material-demand :ev 1000.0 ints 2020)]
      (is (close? 5.0 (d "lithium") 1e-9) "1000 cars x 50 kWh x 0.1 kg/kWh = 5 t")
      (is (close? 10.0 (d "cobalt") 1e-9))
      (is (close? 50.0 (d "graphite") 1e-9))
      (is (close? 60.0 (d "copper") 1e-9) "1000 x 60 kg = 60 t")))
  (testing "PV: GW x t/GW"
    (let [d (td/material-demand :pv 2.0 ints 2020)]
      (is (close? 30.0 (d "silver") 1e-9))
      (is (close? 6000.0 (d "silicon") 1e-9))
      (is (close? 100000.0 (d "steel") 1e-9))))
  (testing "wind uses the onshore coefficients"
    (is (close? 28.0 ((td/material-demand :wind 2.0 ints 2020) "rare-earths") 1e-9)))
  (testing "Li-ion trade: tonnes of accumulators x kWh/t x kg/kWh"
    (let [d (td/material-demand :li-ion 100.0 ints 2020)]
      (is (close? (* 100.0 td/kwh-per-tonne 0.1 1e-3) (d "lithium") 1e-9))))
  (testing "a coefficient unknown that year: no demand, not zero"
    (is (nil? ((td/material-demand :pv 2.0 ints 2019) "silver")))))

(deftest ev-units-from-tonnes
  (is (close? 1000.0 (td/cars 1800.0) 1e-9) "1.8 t a car"))

;; ---------------------------------------------------------------- growth

(deftest growth-of-a-known-logistic
  (let [f {:ceiling 100.0 :speed 0.4 :midpoint 2020.0}
        L #(tech/logistic 100.0 0.4 2020.0 %)
        dL #(let [l (L %)] (* 0.4 l (- 1.0 (/ l 100.0))))]
    (is (close? (Math/log (/ (L 2015) (L 2010))) (td/growth f :level 2010 5) 1e-12) "a flow tracks the level")
    (is (close? (Math/log (/ (dL 2015) (dL 2010))) (td/growth f :stock 2010 5) 1e-12) "additions track the derivative")
    (is (neg? (td/growth f :stock 2022 5)) "past the midpoint additions fall")
    (is (nil? (td/growth nil :level 2010 5)))))

(deftest drift-from-the-prior
  (let [p {:id "pv/silver" :mean 15.0 :lo 10.0 :hi 20.0 :drift -0.12 :drift-from 2008 :ref-year 2020}]
    (is (close? -0.6 (td/drift p 2010 5) 1e-12) "five years of -12%")
    (is (close? -0.24 (td/drift p 2005 5) 1e-12) "only the years from 2008 drift")
    (is (close? 0.0 (td/drift {:mean 1.0 :lo 0.5 :hi 2.0} 2010 5) 1e-12) "no drift"))
  (is (close? 0.0 (td/drift nil 2010 5) 1e-12)))

(deftest logistic-recovery
  (testing "fit-as-of recovers a share's logistic and its projected growth"
    (let [es (logistic-entries 60.0 0.35 2018.0 (range 2000 2026) 1)
          f (td/fit-as-of es 2025 {:cap 100.0})
          L #(tech/logistic 60.0 0.35 2018.0 %)]
      (is (close? 0.35 (:speed f) 0.01))
      (is (close? 2018.0 (:midpoint f) 0.2))
      (is (close? 60.0 (:ceiling f) 1.0))
      (is (close? (Math/log (/ (L 2029) (L 2024))) (td/growth f :level 2024 5) 0.02))))
  (testing "too little data: no fit, no projection"
    (is (nil? (td/fit-as-of (logistic-entries 60.0 0.35 2018.0 (range 2000 2026) 1) 2004 {:cap 100.0})))))

;; ---------------------------------------------------------------- rows and causality

(defn- data []
  {:adds {:pv {"AAA" (vec (for [t (range 2005 2024)] {:t t :known (+ t 2) :x (* 0.1 (- t 2000))}))}
          :ev {"AAA" (vec (for [t (range 2017 2024)] {:t t :known (+ t 2) :x (* 100.0 (- t 2015))}))}}
   :adopt {:pv {"AAA" (logistic-entries 30.0 0.3 2022.0 (range 2000 2024) 1)}
           :ev {"AAA" (logistic-entries 50.0 0.5 2024.0 (range 2017 2024) 2)}}
   :ints (into {} (for [[k v] ints] [k (into {} (for [y (range 1995 2026)] [y (val (first v))]))]))
   :priors {"pv/silver" {:id "pv/silver" :mean 15.0 :lo 10.0 :hi 20.0 :drift -0.12 :drift-from 2008 :ref-year 2020}}})

(defn- at [rows v] (some #(when (= v (:variable %)) (:value %)) rows))

(deftest rows-carry-their-known-year
  (let [rs (td/rows-at (data) "AAA" 2015)]
    (is (= 2013.0 (at rs "tech-demand/state-year")) "known 2015: data year 2013")
    (is (close? (* 1.3 15.0) (at rs "tech-demand/silver") 1e-9) "1.3 GW x 15 t/GW")
    (is (nil? (at rs "tech-demand/lithium")) "no EVs known in 2015")
    (is (every? #(= 2015 (:year %)) rs))))

(deftest growth-uses-fits-and-the-drift
  (let [d (data)
        rs (td/rows-at d "AAA" 2015)
        f (td/fit-as-of (get-in d [:adopt :pv "AAA"]) 2015 (td/fit-opts :pv))]
    (is (close? (+ (td/growth f :stock 2013 5) -0.6) (at rs "tech-demand/silver-growth5") 1e-9)
        "the fit known in 2015, five years of silver drift")
    (is (close? (td/growth f :stock 2013 5) (at rs "tech-demand/copper-growth5") 1e-9)
        "no drift on copper")))

(deftest no-fit-reads-after-the-year
  (let [d (data)
        poison (fn [es] (mapv #(if (> (:known %) 2016) (update % :x * 7.0) %) es))
        d' (-> d
               (update-in [:adds :pv "AAA"] poison) (update-in [:adopt :pv "AAA"] poison)
               (update-in [:adds :ev "AAA"] poison) (update-in [:adopt :ev "AAA"] poison)
               (update :ints #(update-vals % (fn [by] (into {} (for [[y v] by] [y (if (> y 2016) (* 3.0 v) v)]))))))]
    (doseq [y [2010 2014 2016]]
      (is (= (td/rows-at d "AAA" y) (td/rows-at d' "AAA" y)) (str "rows known in " y " ignore later data")))
    (is (not= (td/rows-at d "AAA" 2021) (td/rows-at d' "AAA" 2021)) "the poison is visible once known")))

(deftest totals-sum-the-technologies
  (let [rs (td/rows-at (data) "AAA" 2023)]
    (is (close? (+ (at rs "tech-demand/copper-ev") (at rs "tech-demand/copper-pv"))
                (at rs "tech-demand/copper") 1e-9))
    (is (= 2.0 (at rs "tech-demand/copper-count")))))

(deftest world-sums-countries
  (let [w (td/world-sum {"AAA" [{:t 2001 :known 2003 :x 1.0}] "BBB" [{:t 2001 :known 2004 :x 2.0}
                                                                     {:t 2002 :known 2004 :x 5.0}]})]
    (is (= [{:t 2001 :known 2004 :x 3.0} {:t 2002 :known 2004 :x 5.0}] w)
        "known when every part is")))
