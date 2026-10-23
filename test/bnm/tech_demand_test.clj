(ns bnm.tech-demand-test
  (:require [bnm.tech-demand :as td]
            [bnm.tech-demand-run :as run]
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
   "wind-onshore/rare-earths" {2020 14.0}
   "wind-offshore/copper" {2020 8000.0} "wind-offshore/rare-earths" {2020 239.0}})

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
  (testing "offshore wind: its own copper and rare earths, onshore steel (no offshore prior)"
    (let [d (td/material-demand :wind-offshore 2.0 ints 2020)]
      (is (close? 478.0 (d "rare-earths") 1e-9) "2 GW x 239 t/GW")
      (is (close? 16000.0 (d "copper") 1e-9))
      (is (close? 260000.0 (d "steel") 1e-9))))
  (testing "PHEVs: a BEV's battery scaled by phev-battery, no copper"
    (let [d (td/material-demand :phev 1000.0 ints 2020)]
      (is (close? (* 5.0 td/phev-battery) (d "lithium") 1e-9))
      (is (close? (* 50.0 td/phev-battery) (d "graphite") 1e-9))
      (is (not (contains? d "copper")))))
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
  (let [p {:id "pv/silver" :mean 15.0 :lo 10.0 :hi 20.0 :drift -0.12 :drift-from 2008 :ref-year 2020 :drift-cite [:itrpv]}]
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
   :priors {"pv/silver" {:id "pv/silver" :mean 15.0 :lo 10.0 :hi 20.0 :drift -0.12 :drift-from 2008 :ref-year 2020 :drift-cite [:itrpv]}}})

(defn- at [rows v] (some #(when (= v (:variable %)) (:value %)) rows))

(deftest rows-carry-their-known-year
  (let [rs (td/rows-at (data) "AAA" 2015)]
    (is (= 2013.0 (at rs "tech-demand/state-year")) "known 2015: data year 2013")
    (is (close? (* 1.3 15.0) (at rs "tech-demand/silver") 1e-9) "1.3 GW x 15 t/GW")
    (is (nil? (at rs "tech-demand/lithium")) "no EVs known in 2015")
    (is (every? #(= 2015 (:year %)) rs))))

(deftest growth-uses-the-rule-and-the-drift
  (let [d (data)
        rs (td/rows-at d "AAA" 2015)
        es (get-in d [:adopt :pv "AAA"])
        f (td/fit-as-of es 2015 (td/fit-opts :pv))
        g (td/project-growth f (tech/as-of es 2015) :stock 100.0 2013 5)]
    (is (false? (:identified f)) "a 30% ceiling seen at 3%: not identified")
    (is (close? (* 5 (td/exp-slope (tech/as-of es 2015))) g 1e-12) "so the exponential arm")
    (is (close? (+ g -0.6) (at rs "tech-demand/silver-growth5") 1e-9)
        "the rule's growth known in 2015, five years of silver drift")
    (is (close? g (at rs "tech-demand/copper-growth5") 1e-9) "no drift on copper")
    (is (= 0.0 (at rs "tech-demand/pv-identified")))
    (is (= 0.0 (at rs "tech-demand/pv-logistic")))
    (is (close? (td/exp-slope (tech/as-of es 2015)) (at rs "tech-demand/pv-slope") 1e-12))))

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

(deftest totals-at-a-common-data-year
  (testing "sources known at different lags: the total is at the latest data year every technology has"
    (let [d (-> (data)
                (assoc-in [:adds :ev "AAA"] (vec (for [t (range 2017 2024)] {:t t :known (+ t 1) :x (* 100.0 (- t 2015))}))))
          rs (td/rows-at d "AAA" 2023)]
      (is (= 2021.0 (at rs "tech-demand/state-year")) "PV known two years on, EVs one: 2021")
      (is (close? (* 600.0 50.0 0.1 1e-3) (at rs "tech-demand/lithium") 1e-9) "EVs of 2021, not 2022")
      (is (= 2.0 (at rs "tech-demand/copper-count")) "both in the total")))
  (testing "a technology more than stale-gap years behind the newest is left out"
    (let [d (assoc-in (data) [:adds :ev "AAA"] [{:t 2017 :known 2018 :x 1000.0}])
          rs (td/rows-at d "AAA" 2023)]
      (is (= 2021.0 (at rs "tech-demand/state-year")))
      (is (nil? (at rs "tech-demand/lithium")))
      (is (= 1.0 (at rs "tech-demand/copper-count"))))))

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

;; ---------------------------------------------------------------- the projection rule (bnm-1pq.19)

(deftest exp-slope-of-the-last-window
  (let [obs (vec (for [t (range 2000 2021)] [t (* 2.0 (Math/exp (* 0.25 (- t 2000))))]))]
    (is (close? 0.25 (td/exp-slope obs) 1e-12) "exact on an exponential")
    (is (close? 0.25 (td/exp-slope (into [[1990 1e6] [1995 1e-9]] obs)) 1e-12)
        "only the last window's data years count")
    (is (close? 0.25 (td/exp-slope (conj (vec (butlast obs)) [2019 0.0] [2020 (second (last obs))])) 1e-12)
        "zeros are left out")
    (is (nil? (td/exp-slope [[2019 1.0] [2020 2.0]])) "under three points")))

(deftest the-rule-identified-logistic
  (let [es (logistic-entries 60.0 0.35 2018.0 (range 2000 2026) 1)
        obs (tech/as-of es 2025)
        f (td/fit-as-of es 2025 {:cap 100.0})]
    (is (:identified f))
    (is (= (td/growth f :stock 2023 5) (td/project-growth f obs :stock 100.0 2023 5))
        "an identified ceiling: the logistic")
    (is (= (td/growth f :level 2023 5) (td/project-growth f obs :level 100.0 2023 5)))
    (is (td/logistic? f obs 100.0) "data past half the cap")
    (is (not (td/logistic? f obs nil)) "no cap: no a priori ceiling to identify against")
    (is (close? (* 5 (td/exp-slope obs)) (td/project-growth f obs :stock nil 2023 5) 1e-12))))

(deftest the-rule-a-ceiling-far-below-the-cap
  (let [es (logistic-entries 5.0 0.5 2015.0 (range 2000 2026) 1)
        obs (tech/as-of es 2025)
        f (td/fit-as-of es 2025 {:cap 100.0})]
    (is (:identified f) "bnm.technology's test passes on a plateau at 5%")
    (is (not (td/logistic? f obs 100.0)) "but a share under half its cap: not a ceiling to project on")
    (is (close? (* 5 (td/exp-slope obs)) (td/project-growth f obs :stock 100.0 2023 5) 1e-12)
        "the exponential arm")))

(deftest the-rule-unidentified-exponential
  (let [es (logistic-entries 100.0 0.3 2040.0 (range 2000 2021) 1)
        obs (tech/as-of es 2021)
        f (td/fit-as-of es 2021 {:cap 100.0})
        s (td/exp-slope obs)]
    (is (false? (:identified f)) "1% of a 100% ceiling: not identified")
    (is (close? 0.3 s 0.01) "the exponential arm's slope is the logistic's speed")
    (is (close? (* 5 s) (td/project-growth f obs :stock 100.0 2018 5) 1e-12)
        "additions grow at the slope")
    (is (close? (* 5 s) (td/project-growth f obs :level 100.0 2018 5) 1e-12)
        "so does a level far from the cap"))
  (testing "the cap"
    (let [obs (vec (for [t (range 2010 2021)] [t (* 50.0 (Math/exp (* 0.1 (- t 2020))))]))
          f {:ceiling 100.0 :speed 0.2 :midpoint 2020.0 :identified false}
          E #(* 50.0 (Math/exp (* 0.1 (- % 2020))))]
      (is (close? (Math/log (/ (min 100.0 (E 2025)) (E 2020))) (td/project-growth f obs :level 100.0 2020 5) 1e-9)
          "below the cap: the exponential through the last point")
      (is (close? (Math/log (/ 60.0 (E 2018))) (td/project-growth f obs :level 60.0 2018 5) 1e-9)
          "a level stops at the cap")
      (is (nil? (td/project-growth f obs :stock 60.0 2018 5))
          "additions when the exponential meets the cap within the horizon: no projection")
      (is (close? 0.5 (td/project-growth f obs :stock nil 2018 5) 1e-9) "no cap: the slope")))
  (testing "a falling share"
    (let [obs (vec (for [t (range 2010 2021)] [t (Math/exp (* -0.1 (- t 2000)))]))
          f {:ceiling 100.0 :speed 0.2 :midpoint 2030.0 :identified false}]
      (is (nil? (td/project-growth f obs :stock 100.0 2018 5)) "additions do not follow a falling share")
      (is (close? -0.5 (td/project-growth f obs :level 100.0 2018 5) 1e-9) "a level does")))
  (is (nil? (td/project-growth nil [[2019 1.0]] :level 100.0 2018 5)) "no fit, no projection"))

;; ---------------------------------------------------------------- worked unit examples, prior means

(def ^:private means
  "The priors' means (resources/bnm/recipe-priors.edn), as the filter would
  give them without data."
  {"ev/battery" 55.0 "ev/copper" 53.0
   "li-ion/lithium" 0.12 "li-ion/cobalt" 0.25 "li-ion/nickel" 0.55 "li-ion/graphite" 1.0
   "pv/silicon" 3200.0 "pv/silver" 15.0 "pv/copper" 2800.0 "pv/steel" 50000.0
   "wind-onshore/copper" 2900.0 "wind-onshore/steel" 130000.0 "wind-onshore/rare-earths" 14.0
   "wind-offshore/rare-earths" 239.0})

(def ^:private ints-2020 (update-vals means (fn [x] {2020 x})))

(defn- tmp [content]
  (let [f (java.io.File/createTempFile "tech-demand" ".csv")]
    (spit f content)
    (.deleteOnExit f)
    (.getPath f)))

(deftest worked-ev
  (let [trade (td/read-trade
               (tmp (str "hs,year,known,hs6,iso,imports_t,imports_v,imports_vt,exports_t,exports_v,exports_vt\n"
                         "HS17,2020,2022,870380,AAA,900,2000,1000,,,\n")))
        [e] (get-in trade [:ev "AAA"])
        d (td/material-demand :ev (:x e) ints-2020 (:t e))]
    (is (= [2020 2022] [(:t e) (:known e)]))
    (is (close? 1000.0 (:x e) 1e-9) "900 t on half the value: 1800 t, / 1.8 t a car = 1000 cars")
    (is (close? 6.6 (d "lithium") 1e-9) "1000 cars x 55 kWh x 0.12 kg/kWh = 6600 kg")
    (is (close? 13.75 (d "cobalt") 1e-9) "x 0.25 kg/kWh")
    (is (close? 30.25 (d "nickel") 1e-9) "x 0.55 kg/kWh")
    (is (close? 55.0 (d "graphite") 1e-9) "x 1.0 kg/kWh")
    (is (close? 53.0 (d "copper") 1e-9) "1000 cars x 53 kg")))

(deftest worked-li-ion
  (let [trade (td/read-trade
               (tmp (str "hs,year,known,hs6,iso,imports_t,imports_v,imports_vt,exports_t,exports_v,exports_vt\n"
                         "HS92,2020,2022,850780,AAA,1000,500,500,,,\n")))
        [e] (get-in trade [:li-ion "AAA"])
        d (td/material-demand :li-ion (:x e) ints-2020 (:t e))]
    (is (close? 1000.0 (:x e) 1e-9) "tonnes of accumulators")
    (is (close? 18.0 (d "lithium") 1e-9) "1000 t x 150 kWh/t = 150 MWh, x 0.12 kg/kWh = 18 t")
    (is (close? 150.0 (d "graphite") 1e-9))
    (is (close? 37.5 (d "cobalt") 1e-9))
    (is (close? 82.5 (d "nickel") 1e-9))))

(deftest worked-installs
  (let [ins (td/read-installs
             (tmp (str "iso,year,series,value\n"
                       "AAA,2020,bev-sales,1000\nAAA,2020,bev-share,4.5\nAAA,2020,phev-sales,500\n"
                       "AAA,2019,wind-offshore,1000\nAAA,2020,wind-offshore,3000\n"
                       "BBB,2020,wind-offshore,500\nAAA,2020,wind-onshore,7000\n"))
             1)]
    (is (= [{:t 2020 :known 2021 :x 1000.0}] (get-in ins [:bev-sales "AAA"])) "cars, known a year on")
    (is (= [{:t 2020 :known 2021 :x 4.5}] (get-in ins [:bev-share "AAA"])))
    (is (= [{:t 2019 :known 2020 :x 1.0} {:t 2020 :known 2021 :x 3.0}] (get-in ins [:wind-offshore "AAA"]))
        "MW to GW, sorted")
    (let [d (td/load-installs ins)]
      (is (= [{:t 2020 :known 2021 :x 2.0}] (get-in d [:adds :wind-offshore "AAA"])) "2 GW added")
      (is (= [{:t 2020 :known 2021 :x 2.0}] (get-in d [:adds :wind-offshore "WLD"])) "the world sums countries' additions")
      (is (= (get-in ins [:wind-offshore "AAA"]) (get-in d [:adopt :wind-offshore "AAA"])) "offshore adoption: installed GW")
      (is (= [{:t 2020 :known 2021 :x 1000.0}] (get-in d [:adds :ev "AAA"])))
      (is (= [{:t 2020 :known 2021 :x 500.0}] (get-in d [:adds :phev "AAA"]))))))

(deftest world-ev-is-iea-s-own
  (let [d (td/load-installs {:bev-sales {"AAA" [{:t 2020 :known 2021 :x 10.0}] "WLD" [{:t 2020 :known 2021 :x 100.0}]}})]
    (is (= [{:t 2020 :known 2021 :x 100.0}] (get-in d [:adds :ev "WLD"]))
        "IEA's world total (rest of world included), not the sum of the countries listed")))

(deftest worked-pv-and-wind
  (let [cap (td/read-capacity
             (tmp (str "iso,year,variable,value,unit,flag\n"
                       "AAA,2019,power_solar,1000.0,MW,\nAAA,2020,power_solar,3500.0,MW,\n"
                       "AAA,2019,power_wind,10000.0,MW,\nAAA,2020,power_wind,12000.0,MW,\n"))
             2)
        [pv] (td/additions (get-in cap [:pv "AAA"]))
        [wd] (td/additions (get-in cap [:wind "AAA"]))
        p (td/material-demand :pv (:x pv) ints-2020 (:t pv))
        w (td/material-demand :wind (:x wd) ints-2020 (:t wd))]
    (is (= {:t 2020 :known 2022 :x 2.5} pv) "3500 - 1000 MW = 2.5 GW, known two years on")
    (is (close? 8000.0 (p "silicon") 1e-9) "2.5 GW x 3200 t/GW (3.2 g/W)")
    (is (close? 37.5 (p "silver") 1e-9) "2.5 GW x 15 t/GW (15 mg/W)")
    (is (close? 7000.0 (p "copper") 1e-9))
    (is (close? 125000.0 (p "steel") 1e-9))
    (is (close? 2.0 (:x wd) 1e-9))
    (is (close? 5800.0 (w "copper") 1e-9) "2 GW x 2900 t/GW onshore")
    (is (close? 260000.0 (w "steel") 1e-9))
    (is (close? 28.0 (w "rare-earths") 1e-9) "onshore wind: 14 t/GW (offshore is :wind-offshore)")))

(deftest constants-hang-together
  (testing "a 55 kWh pack at 150 Wh/kg is about a fifth of a 1.8 t car"
    (let [pack-t (/ 55.0 td/kwh-per-tonne)]
      (is (close? 0.367 pack-t 0.001))
      (is (< 0.15 (/ pack-t td/tonnes-per-car) 0.25))))
  (testing "150 kWh/t against the priors: Li 1.8% and graphite 15% of an accumulator's mass"
    (is (close? 0.018 (* 0.12 td/kwh-per-tonne 1e-3) 1e-12))
    (is (close? 0.15 (* 1.0 td/kwh-per-tonne 1e-3) 1e-12))))

;; ---------------------------------------------------------------- validation pairs

(deftest projection-pairs-hold-the-technologies
  (let [rows (concat
              (for [a (range 2010 2016)]
                {:iso "WLD" :year a :variable "tech-demand/state-year" :value (double (- a 2))})
              [{:iso "WLD" :year 2010 :variable "tech-demand/copper" :value 10.0}
               {:iso "WLD" :year 2010 :variable "tech-demand/copper-count" :value 1.0}
               {:iso "WLD" :year 2010 :variable "tech-demand/copper-growth5" :value 0.5}
               {:iso "WLD" :year 2015 :variable "tech-demand/copper" :value 40.0}
               {:iso "WLD" :year 2015 :variable "tech-demand/copper-count" :value 2.0}
               {:iso "WLD" :year 2011 :variable "tech-demand/copper" :value 10.0}
               {:iso "WLD" :year 2011 :variable "tech-demand/copper-count" :value 1.0}
               {:iso "WLD" :year 2011 :variable "tech-demand/copper-growth5" :value 0.4}])
        rows (concat rows [{:iso "WLD" :year 2016 :variable "tech-demand/state-year" :value 2014.0}
                           {:iso "WLD" :year 2016 :variable "tech-demand/copper" :value 20.0}
                           {:iso "WLD" :year 2016 :variable "tech-demand/copper-count" :value 1.0}])]
    (is (= [[0.4 (Math/log 2.0)]] (vec (run/projection-pairs rows "copper")))
        "2010's pair is dropped: a technology joined the total by 2015")
    (is (= [[0.3 (Math/log 3.0)]]
           (vec (run/projection-pairs
                 (concat rows [{:iso "WLD" :year 2010 :variable "tech-demand/lithium-li-ion" :value 1.0}
                               {:iso "WLD" :year 2010 :variable "tech-demand/lithium-li-ion-growth5" :value 0.3}
                               {:iso "WLD" :year 2015 :variable "tech-demand/lithium-li-ion" :value 3.0}])
                 "lithium" "-li-ion")))
        "the trade route's pairs")))
