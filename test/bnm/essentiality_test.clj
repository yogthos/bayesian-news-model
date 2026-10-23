(ns bnm.essentiality-test
  (:require [bnm.essentiality :as es]
            [bnm.essentiality-run :as run]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

;; ---------------------------------------------------------------- hazards

(deftest annual-hazard-from-a-window
  (testing "a 24-month window's probability as a constant yearly hazard"
    (is (close? 0.5 (es/annual 0.75 24) 1e-12))
    (is (close? 0.2 (es/annual 0.2 12) 1e-12))
    (is (= 0.0 (es/annual 0.0 24))))
  (testing "a horizon of H months from a yearly hazard"
    (is (close? (- 1.0 (Math/pow 0.5 0.25)) (es/over-months 0.5 3) 1e-12))))

(deftest union-of-independent-hazards
  (is (close? 0.0 (es/union []) 1e-12))
  (is (close? 0.3 (es/union [0.3]) 1e-12))
  (is (close? (- 1.0 (* 0.9 0.8)) (es/union [0.1 0.2]) 1e-12))
  (testing "never above 1, never below the largest"
    (let [u (es/union [0.6 0.7 0.9])]
      (is (<= 0.9 u 1.0)))))

(deftest strait-rate-is-gamma-poisson
  (let [prior {:a 0.5 :b 25.0}]
    (testing "no data: the prior mean"
      (is (close? 0.02 (:mean (es/strait-rate 0 0.0 prior)) 1e-12)))
    (testing "events raise it, exposure without events lowers it"
      (is (> (:mean (es/strait-rate 1 5.0 prior)) 0.02))
      (is (< (:mean (es/strait-rate 0 5.0 prior)) 0.02)))
    (testing "the sd shrinks as exposure accrues"
      (is (< (:sd (es/strait-rate 1 50.0 prior)) (:sd (es/strait-rate 1 5.0 prior)))))))

(def portwatch
  [{:strait "suez" :year 2023} {:strait "bab-el-mandeb" :year 2023}
   {:strait "panama" :year 2023} {:strait "hormuz" :year 2026}])

(deftest strait-rates-read-only-known-events
  (let [prior {:a 0.5 :b 25.0}
        r22 (es/strait-rates portwatch 2022 {:prior prior :from 2019})
        r23 (es/strait-rates portwatch 2023 {:prior prior :from 2019})
        r18 (es/strait-rates portwatch 2018 {:prior prior :from 2019})]
    (testing "before the record starts: the prior for every strait"
      (is (close? 0.02 (:mean (r18 "suez")) 1e-12))
      (is (close? 0.02 (:mean (r18 "malacca")) 1e-12)))
    (testing "2023's events count from 2023, not before"
      (is (close? (:mean (r22 "suez")) (:mean (r22 "malacca")) 1e-12))
      (is (> (:mean (r23 "suez")) (:mean (r23 "malacca"))))
      (is (close? (:mean (r23 "hormuz")) (:mean (r23 "malacca")) 1e-12)))))

(def restrictions
  ;; country A restricts code 1 from 2010 and tightens it in 2012; B
  ;; enters code 2 in 2012; C is listed with nothing new
  [{:iso "A" :hs6 "1" :year 2010 :known 2011 :in-force 1 :onsets 1}
   {:iso "A" :hs6 "1" :year 2011 :known 2012 :in-force 1 :onsets 0}
   {:iso "A" :hs6 "1" :year 2012 :known 2013 :in-force 1 :onsets 1}
   {:iso "B" :hs6 "2" :year 2012 :known 2013 :in-force 1 :onsets 1}
   {:iso "C" :hs6 "3" :year 2010 :known 2011 :in-force 1 :onsets 0}
   {:iso "C" :hs6 "3" :year 2011 :known 2012 :in-force 1 :onsets 0}
   {:iso "C" :hs6 "3" :year 2012 :known 2013 :in-force 1 :onsets 0}])

(deftest restriction-hazard
  (let [rates (es/restriction-rates restrictions 2013 {:codes 10 :strength 0.0})]
    (testing "tightening: onsets on measures in force the year before"
      ;; in force the year before and seen the next year: A 2011 (0), A
      ;; 2012 (1), C 2011 (0), C 2012 (0): 1 of 4
      (is (close? 0.25 (:tighten rates) 1e-12)))
    (testing "a product in force gets the tightening rate"
      (is (close? 0.25 (es/restriction-p rates "A" "1") 1e-12)))
    (testing "a product not in force gets its country's entry rate"
      (is (pos? (es/restriction-p rates "B" "9")))
      (is (> (es/restriction-p rates "B" "9") (es/restriction-p rates "C" "9")))))
  (testing "rows known after K are not read"
    (let [a (es/restriction-rates restrictions 2012 {:codes 10 :strength 0.0})
          b (es/restriction-rates (conj restrictions {:iso "A" :hs6 "1" :year 2013 :known 2014 :in-force 1 :onsets 1})
                                  2012 {:codes 10 :strength 0.0})]
      (is (= a b)))))

;; ---------------------------------------------------------------- reallocation

(deftest reallocation-bounds
  (testing "a monopolist cannot be replaced"
    (is (= 0.0 (es/reallocation 1.0 0.5 0.3))))
  (testing "a tiny supplier is replaced whole"
    (is (= 1.0 (es/reallocation 0.001 0.5 0.3))))
  (testing "otherwise the others' spare capacity over the lost supply"
    ;; s 0.5, x 0.5: lost 0.25 of world supply; others 0.5 x e 0.2 = 0.1
    (is (close? 0.4 (es/reallocation 0.5 0.5 0.2) 1e-12)))
  (testing "always within [0, 1], never above the others' share times e over the loss"
    (doseq [s [0.0 0.01 0.1 0.3 0.6 0.9 1.0] x [0.1 0.5 1.0] e [0.0 0.05 0.3 1.0]]
      (let [r (es/reallocation s x e)]
        (is (<= 0.0 r 1.0))
        (when (pos? s)
          (is (<= (* r x s) (+ 1e-12 (* e (- 1.0 s)))))))))
  (testing "falls with the supplier's share and the cut, rises with e"
    (is (>= (es/reallocation 0.2 0.5 0.2) (es/reallocation 0.4 0.5 0.2)))
    (is (>= (es/reallocation 0.4 0.1 0.2) (es/reallocation 0.4 0.5 0.2)))
    (is (<= (es/reallocation 0.4 0.5 0.1) (es/reallocation 0.4 0.5 0.2)))))

;; ---------------------------------------------------------------- loss tables

(deftest loss-interpolation
  (let [t {:cuts [0.1 0.5 1.0] :loss [1.0 6.0 15.0]}]
    (testing "zero at zero, the table at its cuts, linear between"
      (is (close? 0.0 (es/interp-loss t 0.0) 1e-12))
      (is (close? 0.5 (es/interp-loss t 0.05) 1e-12))
      (is (close? 6.0 (es/interp-loss t 0.5) 1e-12))
      (is (close? 3.5 (es/interp-loss t 0.3) 1e-12)))
    (testing "a cut above the last is the last"
      (is (close? 15.0 (es/interp-loss t 1.5) 1e-12)))))

;; ---------------------------------------------------------------- risk

(def toy
  ;; one product: exporters X (60%) and Y (40%) of 100; importers RA and
  ;; RB; loss per unit of shortfall 2 in RA, 1 in RB, linear
  {:world 100.0
   :exporters {"X" 60.0 "Y" 40.0}
   :importers {"RA" 70.0 "RB" 30.0}
   :region-of {"X" "RX" "Y" "RY"}
   :p {"X" 0.1 "Y" 0.2}
   :use {"RA" 700.0 "RB" 300.0}
   :lambda (fn [r _f] (if (= r "RA") 2.0 1.0))
   :x 0.5 :e 0.0})

(deftest risk-arithmetic
  (let [{:keys [world regions suppliers]} (es/product-risk toy)]
    ;; X: shortfall RA 0.5 x 70 x 0.6 = 21 (loss 42), RB 9 (9): 51 at P 0.1
    ;; Y: RA 14 (28), RB 6 (6): 34 at P 0.2
    (is (close? (+ (* 0.1 51.0) (* 0.2 34.0)) world 1e-9))
    (is (close? (+ (* 0.1 42.0) (* 0.2 28.0)) (regions "RA") 1e-9))
    (is (close? (+ (* 0.1 9.0) (* 0.2 6.0)) (regions "RB") 1e-9))
    (is (close? world (reduce + (vals regions)) 1e-9))
    (is (close? 5.1 (:risk (suppliers "X")) 1e-9))))

(deftest risk-weighs-the-loss-by-the-models-use
  ;; :model-use is each region's use of the parent in the loss model (the
  ;; units of the result): the cut f = shortfall / use is a share of the
  ;; region's use whatever the trade data say, and the loss is
  ;; lambda(f) x f x the model's use, so a region the loss model sees
  ;; using none of the parent loses nothing however much it imports
  (testing "the loss is lambda x f x the model's use"
    ;; X: RA f 21/700 = 0.03, loss 2 x 0.03 x 7000 = 420; RB f 0.03, 1 x 0.03 x 300 = 9
    ;; Y: RA f 0.02, 2 x 0.02 x 7000 = 280; RB 6
    (let [{:keys [world regions]} (es/product-risk (assoc toy :model-use {"RA" 7000.0 "RB" 300.0}))]
      (is (close? (+ (* 0.1 420.0) (* 0.2 280.0)) (regions "RA") 1e-9))
      (is (close? (+ (* 0.1 9.0) (* 0.2 6.0)) (regions "RB") 1e-9))
      (is (close? world (reduce + (vals regions)) 1e-9))))
  (testing "a region the loss model sees using none of the parent loses nothing"
    (let [{:keys [regions]} (es/product-risk (assoc toy :model-use {"RB" 300.0}))]
      (is (zero? (get regions "RA" 0.0)))
      (is (close? (+ (* 0.1 9.0) (* 0.2 6.0)) (regions "RB") 1e-9))))
  (testing "without :model-use the trade data's use is the weight: the loss as before"
    (is (close? (:world (es/product-risk toy))
                (:world (es/product-risk (assoc toy :model-use (:use toy)))) 1e-9))))

(deftest model-use-is-the-loss-models-use-in-kusd
  ;; region B uses 10 t of P from A (unit value 2 EUR M/t) and 5 t of its
  ;; own (unit value 4): 40 EUR M; A uses 1 t of its own: 2 EUR M
  (let [trade [{:product "P" :origin "A" :dest "B" :quantity 10.0}
               {:product "P" :origin "B" :dest "B" :quantity 5.0}
               {:product "P" :origin "A" :dest "A" :quantity 1.0}]
        production [{:region "A" :product "P" :quantity "100" :value_2011 "200"}
                    {:region "B" :product "P" :quantity "10" :value_2011 "40"}]]
    (is (= {["B" "P"] 40.0 ["A" "P"] 2.0} (es/use-values trade production)))
    (is (= {"P" {"B" (* 40.0 run/kusd-per-eur-m) "A" (* 2.0 run/kusd-per-eur-m)}}
           (run/model-uses trade production)))))

(deftest risk-never-exceeds-losing-all-use
  ;; a shortfall above the region's use is the whole use lost, no more:
  ;; RA uses 10 but would be short 21 of X (f capped at 1)
  (let [t (assoc toy :use {"RA" 10.0 "RB" 300.0} :p {"X" 1.0 "Y" 0.0})
        {:keys [regions]} (es/product-risk t)]
    (is (close? (* 2.0 10.0) (regions "RA") 1e-9))))

(deftest risk-leaves-out-own-imports
  (testing "a country region does not import from itself"
    (let [r (es/product-risk (assoc toy :region-of {"X" "RA" "Y" "RY"}))]
      ;; X's RA term drops: X 9 at 0.1, Y 34 at 0.2
      (is (close? (+ 0.9 6.8) (:world r) 1e-9)))))

(deftest risk-straits
  (let [base (:world (es/product-risk toy))
        r (es/product-risk (assoc toy :straits {"RA" {"suez" 0.5}} :q {"suez" 0.1} :d 0.5))]
    ;; closure: 0.5 x 0.5 x 70 = 17.5 short in RA, loss 35, at 0.1
    (is (close? (+ base 3.5) (:world r) 1e-9))
    (is (close? 3.5 (get-in r [:straits "suez"]) 1e-9))))

(deftest strait-closures-reroute-like-suppliers
  (testing "a strait carrying a small share of world trade is routed around"
    (let [base (:world (es/product-risk toy))
          r (es/product-risk (assoc toy :straits {"RA" {"suez" 0.5}} :q {"suez" 0.1} :d 0.5
                                    :strait-world {"suez" 0.1} :e 0.3))]
      (is (close? (:world (es/product-risk (assoc toy :e 0.3))) (:world r) 1e-9))
      (is (zero? (get-in r [:straits "suez"] 0.0)))
      (is (some? base))))
  (testing "a strait carrying most of it is not: the others' spare over the loss"
    ;; world share 0.8, d 0.5, e 0.2: replaced 0.2 x 0.2 / 0.4 = 0.1
    (let [r (es/product-risk (assoc toy :straits {"RA" {"suez" 0.5}} :q {"suez" 0.1} :d 0.5
                                    :strait-world {"suez" 0.8} :e 0.2))]
      (is (close? (* 0.9 3.5) (get-in r [:straits "suez"]) 1e-9)))))

(deftest risk-monotone
  (let [r (comp :world es/product-risk)
        base (r toy)]
    (testing "in P"
      (is (> (r (assoc-in toy [:p "X"] 0.3)) base))
      (is (= 0.0 (r (assoc toy :p {"X" 0.0 "Y" 0.0})))))
    (testing "in the loss"
      (is (> (r (assoc toy :lambda (fn [r _] (if (= r "RA") 3.0 1.0)))) base)))
    (testing "in the cut"
      (is (> (r (assoc toy :x 0.8)) base)))
    (testing "reallocation lowers it"
      (is (< (r (assoc toy :e 0.2)) base)))))

(deftest risk-reallocation-applied-per-supplier
  ;; e 0.2, x 0.5: X (0.6) replaced 0.2 x 0.4 / 0.3 = 0.2667; Y (0.4) 0.6
  (let [{:keys [suppliers]} (es/product-risk (assoc toy :e 0.2))]
    (is (close? (* 0.1 51.0 (- 1.0 (/ 0.08 0.3))) (:risk (suppliers "X")) 1e-9))
    (is (close? (* 0.2 34.0 0.4) (:risk (suppliers "Y")) 1e-9))))

;; ---------------------------------------------------------------- bilateral slices

(def toy-flows
  ;; RA buys 40 from Y and 30 from X, RB 30 from X: X's 60 and Y's 40 of
  ;; world exports, the importers' 70 and 30
  {"RA" {"X" 30.0 "Y" 40.0} "RB" {"X" 30.0}})

(deftest bilateral-slices-sum-to-the-importers-imports
  (let [sl (es/slices (assoc toy :flows toy-flows))]
    (testing "each importer's slices add up to its imports"
      (doseq [[r m] (:importers toy)]
        (is (close? m (reduce + 0.0 (vals (get sl r))) 1e-9))))
    (testing "a supplier gets what the importer bought from it, not its world share"
      (is (close? 30.0 (get-in sl ["RA" "X"]) 1e-12))
      (is (close? 0.0 (get-in sl ["RB" "Y"] 0.0) 1e-12))))
  (testing "parsed from essentiality-trade.csv.gz, every exporter's slice included"
    (let [{:keys [importers flows bilateral]}
          (run/parse-trade-line "2020,2022,270210,100,X:60;Y:40,RA:70;RB:30,bilateral,RA/X:30;RA/Y:40;RB/X:29;RB/Z:1")]
      (is (true? bilateral))
      (doseq [[r m] importers]
        (is (close? m (reduce + 0.0 (vals (get flows r))) 1e-9)))
      (is (close? 29.0 (get-in flows ["RB" "X"]) 1e-12)))))

(deftest bilateral-slices-decide-who-is-hit
  ;; X: RA 0.5 x 30 = 15 (loss 30), RB 15 (15): 45 at P 0.1
  ;; Y: RA 0.5 x 40 = 20 (40): 40 at P 0.2
  (let [{:keys [world regions suppliers]} (es/product-risk (assoc toy :flows toy-flows))]
    (is (close? (+ 4.5 8.0) world 1e-9))
    (is (close? (+ 3.0 8.0) (regions "RA") 1e-9))
    (is (close? 1.5 (regions "RB") 1e-9))
    (is (close? 4.5 (:risk (suppliers "X")) 1e-9)))
  (testing "an importer that buys nothing from a supplier loses nothing when it is cut"
    (let [r (es/product-risk (assoc toy :flows {"RA" {"Y" 70.0} "RB" {"Y" 30.0}} :p {"X" 1.0 "Y" 0.0}))]
      (is (zero? (:world r)))))
  (testing "a supplier's own region still does not import from it"
    (let [r (es/product-risk (assoc toy :flows toy-flows :region-of {"X" "RA" "Y" "RY"}))]
      ;; X's RA term drops: X 15 at 0.1, Y 40 at 0.2
      (is (close? (+ 1.5 8.0) (:world r) 1e-9)))))

(deftest world-shares-are-the-fallback
  (testing "without bilateral flows the slice is the importer's imports x the world share"
    (let [sl (es/slices toy)]
      (is (close? (* 70.0 0.6) (get-in sl ["RA" "X"]) 1e-12))
      (is (close? (* 30.0 0.4) (get-in sl ["RB" "Y"]) 1e-12))))
  (testing "flows in proportion to the world shares give the fallback's R"
    (let [prop {"RA" {"X" 42.0 "Y" 28.0} "RB" {"X" 18.0 "Y" 12.0}}]
      (is (close? (:world (es/product-risk toy)) (:world (es/product-risk (assoc toy :flows prop))) 1e-9))))
  (testing "a code without bilateral data is flagged"
    (is (false? (:bilateral (run/parse-trade-line "2020,2022,010111,100,X:60;Y:40,RA:70;RB:30,world,"))))))

;; ---------------------------------------------------------------- causality

(deftest event-hazard-reads-forecasts-made-by-the-year-end
  (let [fc {"coup" {"AAA" [[(+ (* 12 2019) 11) 0.19] [(+ (* 12 2020) 11) 0.36]]
                    "BBB" [[(+ (* 12 2020) 11) 0.0]]}
            "war_onset" {"AAA" [[(+ (* 12 2020) 11) 0.0] [(+ (* 12 2021) 11) 0.99]]}}
        h20 (es/event-hazard fc 2020)
        h19 (es/event-hazard fc 2019)]
    (testing "the origin at the end of the year, never a later one"
      (is (close? (es/annual 0.36 24) (h20 "AAA") 1e-12))
      (is (close? (es/annual 0.19 24) (h19 "AAA") 1e-12)))
    (testing "a country with no forecast yet is left to the default"
      (is (nil? (h19 "BBB")))
      (is (number? (:default h19))))))

(deftest trade-is-read-as-known
  ;; BACI's data year Y is known at Y + 2: at K the latest data year known
  ;; by K, bilateral flows included, and never a row known later
  (let [rows [{:year 2019 :known 2021 :hs6 "a" :world 1.0 :flows {"RA" {"X" 1.0}}}
              {:year 2020 :known 2022 :hs6 "a" :world 2.0 :flows {"RA" {"Y" 2.0}}}
              {:year 2020 :known 2022 :hs6 "b" :world 3.0}
              {:year 2021 :known 2023 :hs6 "a" :world 4.0 :flows {"RA" {"Z" 4.0}}}]]
    (is (= {"a" 2.0 "b" 3.0} (update-vals (run/trade-as-known rows 2022) :world)))
    (is (= {"RA" {"Y" 2.0}} (:flows ((run/trade-as-known rows 2022) "a"))))
    (is (= {"a" 4.0} (update-vals (run/trade-as-known rows 2023) :world)))
    (is (empty? (run/trade-as-known rows 2020)))))

(deftest known-by-filters-every-source
  (let [rows [{:known 2010 :v 1} {:known 2011 :v 2} {:known 2012 :v 3}]]
    (is (= [1 2] (map :v (es/known-by rows 2011))))))

;; ---------------------------------------------------------------- supplier shares from production stages

(def stage-rows
  ;; separation: data 2020 known 2022 (China 0.9), a revised edition of
  ;; 2020 and the 2021 data known 2023; the mine stage known 2010 only
  [{:commodity "re" :stage "separation" :iso "CHN" :year 2020 :known 2022 :quantity 90.0 :source "A"}
   {:commodity "re" :stage "separation" :iso "JPN" :year 2020 :known 2022 :quantity 10.0 :source "A"}
   {:commodity "re" :stage "separation" :iso "CHN" :year 2021 :known 2023 :quantity 60.0 :source "B"}
   {:commodity "re" :stage "separation" :iso "MYS" :year 2021 :known 2023 :quantity 40.0 :source "B"}
   {:commodity "re" :stage "mine" :iso "CHN" :year 2008 :known 2010 :quantity 1.0 :source "C"}])

(deftest stage-shares-are-read-as-known
  (testing "the latest data year known by K, as shares of the stage's output"
    (is (= {["re" "separation"] {"CHN" 0.9 "JPN" 0.1}} (dissoc (es/stage-shares stage-rows 2022) ["re" "mine"])))
    (is (= {"CHN" 0.6 "MYS" 0.4} (get (es/stage-shares stage-rows 2023) ["re" "separation"]))))
  (testing "nothing known after K is read"
    (is (nil? (get (es/stage-shares stage-rows 2021) ["re" "separation"]))))
  (testing "data known more than three years before K are stale"
    (is (= {"CHN" 1.0} (get (es/stage-shares stage-rows 2013) ["re" "mine"])))
    (is (nil? (get (es/stage-shares stage-rows 2014) ["re" "mine"]))))
  (testing "the stages keep their units apart: shares sum to 1 within each"
    (doseq [[_ sh] (es/stage-shares stage-rows 2023)]
      (is (close? 1.0 (reduce + (vals sh)) 1e-12)))))

(deftest stage-shares-replace-export-shares
  (let [tr {:world 100.0 :exporters {"JPN" 60.0 "FRA" 40.0} :importers {"RA" 70.0 "RB" 30.0}
            :flows {"RA" {"JPN" 40.0 "FRA" 30.0} "RB" {"JPN" 30.0}}}
        stage {"CHN" 0.9 "JPN" 0.1}]
    (testing "where a stage exists: its producers are the suppliers, with their production shares"
      (let [b (es/supplier-basis tr stage)]
        (is (= :stage (:basis b)))
        (is (nil? (:flows b)))
        (is (close? 0.9 (/ (get-in b [:exporters "CHN"]) (:world b)) 1e-12))
        (is (= (:importers tr) (:importers b)))
        (testing "and each importer's imports are split by them"
          (is (close? (* 70.0 0.9) (get-in (es/slices b) ["RA" "CHN"]) 1e-12)))))
    (testing "no stage: bilateral flows where there are, else world export shares"
      (is (= :bilateral (:basis (es/supplier-basis tr nil))))
      (is (= (:flows tr) (:flows (es/supplier-basis tr nil))))
      (is (= :world (:basis (es/supplier-basis (dissoc tr :flows) nil))))
      (is (= :world (:basis (es/supplier-basis (dissoc tr :flows) {}))))))
  (testing "a concentrated stage is not reallocated away the way scattered exporters are"
    ;; exporters 0.3 each: replaced at e 0.3 (threshold 0.375); a 0.9 producer is not
    (let [m {:world 100.0 :exporters {"JPN" 30.0 "FRA" 30.0 "USA" 30.0 "CHN" 10.0} :importers {"RA" 100.0}
             :region-of {} :p {"JPN" 0.1 "FRA" 0.1 "USA" 0.1 "CHN" 0.1} :use {"RA" 1000.0}
             :lambda (fn [_ _] 1.0) :x 0.5 :e 0.3}]
      (is (zero? (:world (es/product-risk m))))
      (is (pos? (:world (es/product-risk (merge m (es/supplier-basis m {"CHN" 0.9 "JPN" 0.1})))))))))

(deftest hs-codes-map-to-their-stage
  (testing "ores to the mine, refined metal to the refinery or smelter, compounds to the chemical or separation stage"
    (is (= ["iron" "mine"] (run/supply-stage "260111")))
    (is (= ["copper" "refinery"] (run/supply-stage "740311")))
    (is (= ["aluminium" "smelter"] (run/supply-stage "760110")))
    (is (= ["lithium" "chemical"] (run/supply-stage "282520")))
    (is (= ["rare-earths" "separation"] (run/supply-stage "284610"))))
  (testing "pooled codes and codes without a stage keep export shares"
    (is (nil? (run/supply-stage "253090")))
    (is (nil? (run/supply-stage "811291")))
    (is (nil? (run/supply-stage "120100")))))

;; ---------------------------------------------------------------- rare-earth sub-nodes

(def subnode-rows
  [{:subnode "rare_earth_compounds" :edition "HS92" :code "284610" :hs6 "284610" :parent "C_CHEM"}
   {:subnode "rare_earth_compounds" :edition "HS12" :code "284610" :hs6 "284610" :parent "C_CHEM"}
   {:subnode "rare_earth_compounds" :edition "HS92" :code "284690" :hs6 "284690" :parent "C_CHEM"}
   {:subnode "rare_earth_metals" :edition "HS92" :code "280530" :hs6 "280530" :parent "C_CHEM"}
   {:subnode "permanent_magnets" :edition "HS92" :code "850511" :hs6 "850511" :parent "C_FABM"}
   {:subnode "rare_gases" :edition "HS92" :code "280429" :hs6 "280429" :parent "C_CHEM"}])

(deftest rare-earth-codes-map-to-their-sub-nodes
  (testing "the HS92 codes of subnodes.csv go to the graph's sub-nodes, not their parents"
    (is (= {"284610" "C_REOX" "284690" "C_REOX" "280530" "C_REMT" "850511" "C_NDFB"}
           (run/subnode-parents subnode-rows))))
  (testing "they override the concordance's parent; other codes keep theirs"
    (let [conc [{:edition "HS92" :code "284610" :product "C_CHEM" :weight 1.0}
                {:edition "HS92" :code "280429" :product "C_CHEM" :weight 1.0}
                {:edition "HS92" :code "850511" :product "C_FABM" :weight 1.0}]]
      (is (= {"284610" "C_REOX" "284690" "C_REOX" "280530" "C_REMT" "280429" "C_CHEM" "850511" "C_NDFB"}
             (run/parents-of conc subnode-rows)))
      (testing "and the sub-nodes get loss tables"
        (is (every? (set (run/hs92-parents conc)) ["C_REOX" "C_REMT" "C_NDFB" "C_CHEM" "C_FABM"]))))))

(deftest loss-model-values-take-numbers-or-strings
  ;; bnm.supply/load-rows may give the split sub-nodes' production as numbers
  (let [trade [{:product "P" :origin "A" :dest "B" :quantity 10.0}]
        as-str [{:region "A" :product "P" :quantity "100" :value_2011 "200"}]
        as-num [{:region "A" :product "P" :quantity 100.0 :value_2011 200.0}]]
    (is (= {["B" "P"] 20.0} (es/use-values trade as-str) (es/use-values trade as-num)))))

(deftest report-reads-the-split-graph
  (when (.exists (java.io.File. "data/derived/supply/recipes.csv"))
    (let [{:keys [trade production]} (run/graph-rows)]
      (testing "the rare-earth sub-nodes are in the rows the report weighs losses by"
        (is (some #(= "C_REOX" (:product %)) production))
        (is (pos? (reduce + 0.0 (vals (get (run/model-uses trade production) "C_REOX" {})))))))))

;; ---------------------------------------------------------------- ranking and flags

(deftest ranks-by-risk
  (let [rs (es/rank-risk [{:hs6 "a" :risk 1.0} {:hs6 "b" :risk 3.0} {:hs6 "c" :risk 2.0} {:hs6 "d" :risk 0.0}])]
    (is (= {"b" 1 "c" 2 "a" 3 "d" 4} (into {} (map (juxt :hs6 :rank) rs))))
    (is (close? 0.25 (:pct (first (filter #(= "b" (:hs6 %)) rs))) 1e-12))))

(deftest risk-up-against-own-past
  (is (true? (es/risk-up? 2.0 [1.0 1.0 1.0])))
  (is (false? (es/risk-up? 1.0 [1.0 2.0 1.5])))
  (testing "too little past: no call"
    (is (nil? (es/risk-up? 2.0 [1.0])))))

(deftest four-signal-flag
  (let [all {:rising true :linked true :concentrated true :risk-up true}]
    (is (true? (es/flag? all)))
    (doseq [k (keys all)]
      (is (false? (es/flag? (assoc all k false))))
      (is (false? (es/flag? (dissoc all k)))))))

(deftest placebo-shuffles-within-chapter
  (let [rows (for [c ["01" "02"] i (range 10)] {:hs6 (str c (format "%04d" i)) :risk (double i)})
        sh (es/shuffle-within rows (java.util.Random. 7))]
    (testing "the same risks within each chapter, moved between its products"
      (doseq [c ["01" "02"]]
        (is (= (sort (map :risk (filter #(= c (subs (:hs6 %) 0 2)) rows)))
               (sort (map :risk (filter #(= c (subs (:hs6 %) 0 2)) sh)))))))
    (is (not= (map :risk rows) (map :risk sh)))))
