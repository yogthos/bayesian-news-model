(ns bnm.world-test
  (:require [bnm.world :as w]
            [clojure.test :refer [deftest is testing]]))

(def world
  ;; two kinds at two places: "bank" drives "coup" through its state (0.8
  ;; per unit of bank's linear predictor) and its onsets (3.0 on the
  ;; year kernel); "coup" does not drive "bank"
  {"bank" {:f {"P" -2.0 "Q" -2.0} :q {"P" 0.1 "Q" 0.1} :window 24
           :parents {}}
   "coup" {:f {"P" -3.0 "Q" -3.0} :q {"P" 0.1 "Q" 0.1} :window 24
           :parents {"bank" [0.8 3.0 0.0 0.0]} :moderator {"P" 0.0 "Q" 0.0}}})

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
