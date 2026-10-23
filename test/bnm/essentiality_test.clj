(ns bnm.essentiality-test
  (:require [bnm.essentiality :as es]
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

;; ---------------------------------------------------------------- causality

(deftest known-by-filters-every-source
  (let [rows [{:known 2010 :v 1} {:known 2011 :v 2} {:known 2012 :v 3}]]
    (is (= [1 2] (map :v (es/known-by rows 2011))))))

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
