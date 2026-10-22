(ns bnm.world-test
  (:require [bnm.world :as w]
            [clojure.test :refer [deftest is testing]]))

(def world
  ;; two kinds at two places: "bank" drives "coup" along one edge, its
  ;; post-crisis regime (3.0 on the log odds while a banking onset known
  ;; within three years); "coup" does not drive "bank"
  {"bank" {:f {"P" -2.0 "Q" -2.0} :q {"P" 0.1 "Q" 0.1} :window 24
           :parents {}}
   "coup" {:f {"P" -3.0 "Q" -3.0} :q {"P" 0.1 "Q" 0.1} :window 24
           :parents {"bank" {:form :post3 :coef 3.0 :lag 6}}}})

(deftest what-an-edge-passes-in-the-simulation
  ;; at the origin starting year h (month 12(h - 1)), onsets drawn in a
  ;; year at its middle, known `lag` months later; the change in the
  ;; edge's input from what the node last read
  (is (= 0.0 (w/edge-change {:form :post3 :lag 6} 1 nil 0.0)) "no onset")
  (is (= 0.0 (w/edge-change {:form :post3 :lag 12} 2 1 0.0)) "not known by the next origin")
  (is (= 1.0 (w/edge-change {:form :post3 :lag 6} 2 1 0.0)))
  (is (= 1.0 (w/edge-change {:form :post3 :lag 6} 4 1 0.0)))
  (is (= 0.0 (w/edge-change {:form :post1 :lag 6} 3 1 0.0)) "the one-year regime over")
  (is (< 0.5 (w/edge-change {:form :e1 :lag 0} 2 1 0.0) 0.7) "exp(-6/12)")
  (is (= 0.25 (w/edge-change {:form :destab :lag 6} 2 nil 0.25)) "the parent's state less its last"))

(deftest a-shock-travels-along-the-graph
  (let [base (w/simulate world {:horizon 4 :draws 4000 :seed 1})
        hit (w/simulate world {:horizon 4 :draws 4000 :seed 1 :do {["bank" "P"] 1}})]
    (testing "baseline: each kind's chance of an onset within the horizon"
      (is (< 0.15 (get-in base [:p "bank" "P"]) 0.45))
      (is (< (get-in base [:p "coup" "P"]) (get-in base [:p "bank" "P"]))))
    (testing "the scenario: a banking crisis at P in the first year"
      (is (= 1.0 (get-in hit [:p "bank" "P"])))
      (is (> (get-in hit [:p "coup" "P"]) (+ 0.03 (get-in base [:p "coup" "P"]))) "raises P's coups")
      (is (< (Math/abs (- (get-in hit [:p "coup" "Q"]) (get-in base [:p "coup" "Q"]))) 0.03) "not Q's"))
    (testing "joint: coups and banking crises together more often than if independent"
      (is (> (get-in base [:joint ["bank" "coup"] "P"])
             (* (get-in base [:p "bank" "P"]) (get-in base [:p "coup" "P"])))))
    (testing "who transmits to whom: the impulse of each kind's onset on the others"
      (let [imp (w/impulse world {:horizon 4 :draws 2000 :seed 2})]
        (is (> (get imp ["bank" "coup"]) 0.03))
        (is (< (Math/abs (get imp ["coup" "bank"])) 0.01))))))

(deftest joint-forecasts-for-the-graph-s-edges
  ;; for each edge, each place's chance of both onsets within the horizon
  ;; from the simulation, beside the product of the two marginals
  (let [rows (w/joints world [["bank" "coup"]] {:horizon 4 :draws 4000 :seed 1})]
    (is (= 2 (count rows)) "one per place")
    (is (every? #(> (:joint %) (* (:pj %) (:pk %))) rows) "the relation makes them co-occur")
    (is (every? #(<= (:joint %) (min (:pj %) (:pk %))) rows))))

(deftest every-link-form-can-be-simulated
  ;; whatever form the rule may choose (bnm.relation-diag/link-forms), the
  ;; artificial world can move it: an own part moves as its raw form does
  ;; (a simulated onset is the place's own, not what the region shares)
  (doseq [f [:post1 :post3 :e1 :e5 :destab :level-own :destab-own :e1-own :e5-own :post3-own]]
    (is (number? (w/edge-change {:form f :lag 6} 2 1 0.1)) (str f)))
  (is (= (w/edge-change {:form :post3 :lag 6} 2 1 0.0) (w/edge-change {:form :post3-own :lag 6} 2 1 0.0))))
