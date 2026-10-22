(ns bnm.network-test
  (:require [bnm.network :as nw]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

(defn- net-of [es] (reduce (fn [n [i j w]] (nw/edge n i j w)) (nw/network) es))

(def net
  ;; two layers: sectors (energy feeds steel feeds cars) and kinds (a
  ;; growth collapse feeds protest), and an inter-layer edge (a car
  ;; industry's slump at country A feeds A's growth collapse)
  (-> (nw/network)
      (nw/edge [:sector "A" :energy] [:sector "A" :steel] 0.5)
      (nw/edge [:sector "A" :steel] [:sector "A" :cars] 0.4)
      (nw/edge [:sector "A" :cars] [:kind "A" :growth_collapse] 0.3)
      (nw/edge [:kind "A" :growth_collapse] [:kind "A" :protest] 0.2)))

(def circulant
  ;; every row sums to 0.6, so the radius is 0.6; a circulant's other
  ;; eigenvalues are complex
  (net-of [[:a :a 0.1] [:a :b 0.2] [:a :c 0.3]
           [:b :a 0.3] [:b :b 0.1] [:b :c 0.2]
           [:c :a 0.2] [:c :b 0.3] [:c :c 0.1]]))

(deftest layers-nodes-and-edges
  (is (= 5 (count (nw/nodes net))))
  (is (= #{:sector :kind} (set (nw/layers net))))
  (is (= 1 (count (nw/inter-layer net))) "one edge between layers")
  (is (= 0.5 (nw/weight net [:sector "A" :energy] [:sector "A" :steel])))
  (is (= 0.75 (nw/weight (nw/edge (nw/edge (nw/network) :a :b 0.5) :a :b 0.25) :a :b)) "a repeated edge adds"))

(deftest who-transmits
  ;; Katz influence: everything a node reaches downstream, attenuated by
  ;; alpha per step; energy reaches most
  (let [inf (nw/influence net 0.9)]
    (is (= [:sector "A" :energy] (key (apply max-key val inf))))
    (is (close? (get inf [:kind "A" :growth_collapse]) (* 0.9 0.2) 1e-9) "one step on, weight 0.2")
    (is (zero? (get inf [:kind "A" :protest])) "reaches nothing"))
  (testing "on a cycle, the closed form: row sums of (I - alpha W)^-1 less 1"
    (let [alpha 0.9]
      (doseq [[_ v] (nw/influence circulant alpha)]
        (is (close? v (- (/ 1.0 (- 1.0 (* alpha 0.6))) 1.0) 1e-9))))
    (let [a 0.8 b 0.2 alpha 0.9 det (- 1.0 (* alpha alpha a b))
          inf (nw/influence (net-of [[:a :b a] [:b :a b]]) alpha)]
      (is (close? (:a inf) (- (/ (+ 1.0 (* alpha a)) det) 1.0) 1e-9))
      (is (close? (:b inf) (- (/ (+ 1.0 (* alpha b)) det) 1.0) 1e-9))))
  (testing "signed walks that cancel in one step still count later"
    (is (close? 0.25 (:a (nw/influence (net-of [[:a :b 1.0] [:a :c -1.0] [:b :d 1.0]]) 0.5)) 1e-12)))
  (testing "a sum that diverges throws instead of truncating"
    (is (thrown? clojure.lang.ExceptionInfo (nw/influence (net-of [[:a :b 1.0] [:b :a 1.0]]) 1.0)))
    (is (thrown? clojure.lang.ExceptionInfo (nw/influence (net-of [[:a :b 2.0] [:b :a 2.0]]) 0.6)))))

(deftest the-spectral-radius
  (testing "shocks die out below 1, grow above"
    (is (< (nw/spectral-radius net) 1e-9) "no cycle: nothing feeds back")
    (is (zero? (nw/spectral-radius (nw/network))))
    (is (close? 0.5 (nw/spectral-radius (net-of [[:a :b 0.5] [:b :a 0.5]])) 1e-9)))
  (testing "periodic matrices, where plain power iteration oscillates"
    (is (close? 0.4 (nw/spectral-radius (net-of [[:a :b 0.8] [:b :a 0.2]])) 1e-9) "sqrt(0.8 x 0.2)")
    (is (close? 0.5 (nw/spectral-radius (net-of [[:a :b 1.0] [:b :c 0.5] [:c :a 0.25]])) 1e-9) "cube root of 0.125")
    (is (close? 0.3 (nw/spectral-radius (net-of [[:a :b 0.9] [:b :a 0.1] [:b :c 1.0]])) 1e-9) "a cycle with a tail"))
  (testing "reducible: two equal cycles in series (a defective root) and a lone self-loop"
    (is (close? 0.5 (nw/spectral-radius (net-of [[:a :b 0.5] [:b :a 0.5] [:a :c 0.1] [:c :d 0.5] [:d :c 0.5]])) 1e-9))
    (is (close? 0.7 (nw/spectral-radius (net-of [[:a :b 0.5] [:b :b 0.7]])) 1e-12)))
  (testing "complex eigenvalues"
    (is (close? 0.6 (nw/spectral-radius circulant) 1e-9))
    (is (close? 0.5 (nw/spectral-radius (net-of [[:a :b 0.5] [:b :a -0.5]])) 1e-9) "eigenvalues +-0.5i; |W| has the same radius"))
  (testing "a primitive matrix: the root of l^3 - 0.1 l^2 - 0.125"
    (let [r (nw/spectral-radius (net-of [[:a :b 1.0] [:b :c 0.5] [:c :a 0.25] [:a :a 0.1]]))]
      (is (close? 0.0 (- (* r r r) (* 0.1 r r) 0.125) 1e-9)))))

(deftest a-shocks-reach
  (testing "what one node's unit shock adds downstream, (I - W)^-1 less I"
    (let [r (nw/reach net [:sector "A" :energy])]
      (is (close? (get r [:kind "A" :protest]) (* 0.5 0.4 0.3 0.2) 1e-12))))
  (testing "on a cycle, the closed form"
    (let [a 0.8 b 0.5 r (nw/reach (net-of [[:a :b a] [:b :a b]]) :a)]
      (is (close? (:a r) (/ (* a b) (- 1.0 (* a b))) 1e-9))
      (is (close? (:b r) (/ a (- 1.0 (* a b))) 1e-9))))
  (testing "no reach where shocks do not die out"
    (is (thrown? clojure.lang.ExceptionInfo (nw/reach (net-of [[:a :b 1.0] [:b :a 1.0]]) :a)))
    (is (thrown? clojure.lang.ExceptionInfo (nw/reach (net-of [[:a :b 2.0] [:b :a 2.0]]) :a)))))

(deftest from-input-output
  (let [flows {[:energy :steel] 20.0 [:steel :cars] 30.0 [:energy :energy] 5.0}
        go {:energy 50.0 :steel 100.0 :cars 150.0}
        a (nw/cost-shares flows go)
        b (nw/sales-shares flows go)]
    (testing "A: the share of the buyer's output paid to the seller"
      (is (= 0.2 (a [:energy :steel])))
      (is (= 0.2 (a [:steel :cars]))))
    (testing "B: the share of the seller's output sold to the buyer"
      (is (= 0.4 (b [:energy :steel])))
      (is (= 0.3 (b [:steel :cars]))))
    (let [na (nw/from-io (nw/network) "A" a)
          nb (nw/from-io-supply (nw/network) "A" b)]
      (is (= 0.2 (nw/weight na [:sector "A" :energy] [:sector "A" :steel])))
      (is (= 0.4 (nw/weight nb [:sector "A" :energy] [:sector "A" :steel])))
      (is (close? (nw/spectral-radius na) (nw/spectral-radius nb) 1e-9) "B is similar to A"))))

(deftest from-the-event-graph
  (let [g {["bank" "infl"] {:sign 1 :rr-lead (Math/exp 0.7) :form :post3}
           ["infl" "coup"] {:sign -1 :coef [-1.0 0.2]}
           ["coup" "war"] {:sign 1}}]
    (testing "weights from the graph's data: |log rr-lead|, else |sum coef|, signed"
      (let [n (nw/from-graph (nw/network) g ["A" "B"])]
        (is (close? 0.7 (nw/weight n [:kind "B" "bank"] [:kind "B" "infl"]) 1e-12))
        (is (close? -0.8 (nw/weight n [:kind "A" "infl"] [:kind "A" "coup"]) 1e-12))
        (is (= 1.0 (nw/weight n [:kind "A" "coup"] [:kind "A" "war"])))))
    (testing "topology only: the sign alone, scaled"
      (let [n (nw/from-graph (nw/network) g ["A"] {:topology? true :scale 0.5})]
        (is (= 0.5 (nw/weight n [:kind "A" "bank"] [:kind "A" "infl"])))
        (is (= -0.5 (nw/weight n [:kind "A" "infl"] [:kind "A" "coup"])))))))

(deftest coupling-layers
  (let [n (nw/couple (nw/network) "A" :sector :kind {[:cars "growth_collapse"] 0.3})]
    (is (= 0.3 (nw/weight n [:sector "A" :cars] [:kind "A" "growth_collapse"])))
    (is (= 1 (count (nw/inter-layer n))))
    (is (thrown? clojure.lang.ExceptionInfo (nw/couple (nw/network) "A" :kind :kind {[:a :b] 1.0})))))

(deftest from-the-lattice-s-reads
  ;; every read a node recorded is an edge from what it read to it, its
  ;; layer the kind, weighted by the share of the reader's reads
  (let [trace [{:scale :year :place "a1" :reads [{:dir :down :scale :half-decade :place "a1" :origin 11}
                                                  {:dir :up :scale :month :place "a1" :origin 11}
                                                  {:dir :feedback :scale :year :place "a1" :known-at 10}]}
               {:scale :year :place "a1" :reads [{:dir :up :scale :month :place "a1" :origin 23}]}
               {:scale :year :place "A" :reads [{:dir :relation :kind "bank" :scale :year :place "A" :origin 22}]}]
        n (nw/from-reads (nw/network) "coup" trace)]
    (is (close? (/ 2.0 3.0) (nw/weight n ["coup" "a1" :month] ["coup" "a1" :year]) 1e-12))
    (is (close? (/ 1.0 3.0) (nw/weight n ["coup" "a1" :half-decade] ["coup" "a1" :year]) 1e-12))
    (is (zero? (nw/weight n ["coup" "a1" :year] ["coup" "a1" :year])) "feedback dropped")
    (is (= 1.0 (nw/weight n ["bank" "A" :year] ["coup" "A" :year])) "another kind's read")
    (is (= 1 (count (nw/inter-layer n))) "a read across kinds crosses layers")
    (let [kept (nw/from-reads (nw/network) "coup" trace {:feedback? true})]
      (is (close? 0.25 (nw/weight kept ["coup" "a1" :year] ["coup" "a1" :year]) 1e-12)))))
