(ns bnm.network-test
  (:require [bnm.network :as nw]
            [clojure.test :refer [deftest is testing]]))

(def net
  ;; two layers: sectors (energy feeds steel feeds cars) and kinds (a
  ;; growth collapse feeds protest), and an inter-layer edge (a car
  ;; industry's slump at country A feeds A's growth collapse)
  (-> (nw/network)
      (nw/edge [:sector "A" :energy] [:sector "A" :steel] 0.5)
      (nw/edge [:sector "A" :steel] [:sector "A" :cars] 0.4)
      (nw/edge [:sector "A" :cars] [:kind "A" :growth_collapse] 0.3)
      (nw/edge [:kind "A" :growth_collapse] [:kind "A" :protest] 0.2)))

(deftest layers-nodes-and-edges
  (is (= 5 (count (nw/nodes net))))
  (is (= #{:sector :kind} (set (nw/layers net))))
  (is (= 1 (count (nw/inter-layer net))) "one edge between layers")
  (is (= 0.5 (nw/weight net [:sector "A" :energy] [:sector "A" :steel]))))

(deftest who-transmits
  ;; Katz influence: everything a node reaches downstream, attenuated by
  ;; alpha per step; energy reaches most
  (let [inf (nw/influence net 0.9)]
    (is (= [:sector "A" :energy] (key (apply max-key val inf))))
    (is (< (Math/abs (- (get inf [:kind "A" :growth_collapse]) (* 0.9 0.2))) 1e-9) "one step on, weight 0.2")
    (is (zero? (get inf [:kind "A" :protest])) "reaches nothing")))

(deftest propagation-and-paths
  (testing "the spectral radius: shocks die out below 1, grow above"
    (is (< (nw/spectral-radius net) 1e-9) "no cycle: nothing feeds back")
    (is (< (Math/abs (- 0.5 (nw/spectral-radius (nw/edge (nw/edge (nw/network) :a :b 0.5) :b :a 0.5)))) 1e-6)))
  (testing "a shock's reach: what one node's unit shock adds downstream (I - W')^-1"
    (let [r (nw/reach net [:sector "A" :energy])]
      (is (< (Math/abs (- (get r [:kind "A" :protest]) (* 0.5 0.4 0.3 0.2))) 1e-12)))))

(deftest from-the-models
  ;; an influence map built from what the system holds: a graph of kinds
  ;; and input-output coefficients
  (let [n (nw/from-io (nw/network) "A" {[:energy :steel] 0.2 [:steel :cars] 0.3})]
    (is (= 0.2 (nw/weight n [:sector "A" :energy] [:sector "A" :steel]))))
  (let [n (nw/from-graph (nw/network) {["bank" "infl"] {:sign 1 :form :post3}} ["A" "B"] 1.0)]
    (is (= 1.0 (nw/weight n [:kind "B" "bank"] [:kind "B" "infl"])))))

(deftest from-the-lattice-s-reads
  ;; every read a node recorded is an edge from what it read to it (layer
  ;; :lattice, place [scale place]), weighted by how often
  (let [trace [{:scale :year :place "a1" :reads [{:dir :down :scale :half-decade :place "a1" :origin 11}
                                                  {:dir :up :scale :month :place "a1" :origin 11}]}
               {:scale :year :place "a1" :reads [{:dir :up :scale :month :place "a1" :origin 23}]}
               {:scale :year :place "A" :reads [{:dir :relation :kind "bank" :scale :year :place "A" :origin 22}]}]
        n (nw/from-reads (nw/network) "coup" trace)]
    (is (= 2.0 (nw/weight n [:lattice "coup" [:month "a1"]] [:lattice "coup" [:year "a1"]])))
    (is (= 1.0 (nw/weight n [:lattice "bank" [:year "A"]] [:lattice "coup" [:year "A"]])) "another kind's read")))
