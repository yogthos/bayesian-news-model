(ns bnm.node-test
  "The node contract (docs/RECURSIVE.md §1.2) and the coherence invariants
  that live at the node layer: C6 (no node reads its own issued forecast)
  and C11 (one way per edge per step: within an origin the reads form no
  cycle)."
  (:require [augur.math :as m]
            [bnm.buffers :as b]
            [bnm.lattice :as lt]
            [bnm.lattice-test]
            [bnm.node :as node]
            [bnm.sheaf :as sh]
            [clojure.test :refer [deftest is testing]]))

(def ^:private loop-run @#'bnm.lattice-test/loop-run)
(def ^:private spec-of @#'bnm.lattice-test/spec)
(def ^:private world-of @#'bnm.lattice-test/world)

(def ^:private levels {"a1" :country "a2" :country "a3" :country "b1" :country "b2" :country "b3" :country
                       "A" :region "B" :region})
(def ^:private windows {:month 1 :year 12})

(defn- states [r] (node/split-book (:book r) {:kind :crisis :level-of levels :window-of windows}))

(defn- close? [a b tol] (<= (Math/abs (- (double a) (double b))) tol))

;; -- the schema ------------------------------------------------------------------

(def ^:private minimal
  {:id [:country "KEN" :year] :t 24300
   :stocks {} :flows {} :buffers {}
   :hazard {:coup {:z 0.0 :z-var 0.0 :rate 0.001 :window 24 :p (- (Math/expm1 (* -0.001 24)))}}
   :reads [] :known 24300})

(deftest the-schema
  (is (= minimal (node/validate minimal)))
  (is (empty? (node/problems minimal)))
  (testing "an issued forecast is not state, anywhere in the node"
    (is (seq (node/problems (assoc minimal :issued {:p 0.1}))))
    (is (seq (node/problems (assoc minimal :forecast {:coup {:p 0.1}}))))
    (is (seq (node/problems (assoc-in minimal [:hazard :coup :issued] {:p 0.1}))))
    (is (thrown? clojure.lang.ExceptionInfo (node/validate (assoc minimal :forecast {})))))
  (testing "a hazard's p is computed from its rate and frailty, not learned"
    (is (seq (node/problems (assoc-in minimal [:hazard :coup :p] 0.5))))
    (let [z {:z 0.3 :z-var 0.2 :rate 0.001 :window 24}]
      (is (empty? (node/problems (assoc-in minimal [:hazard :coup] (assoc z :p (node/hazard-p z))))))))
  (testing "ids are [level place scale]"
    (is (seq (node/problems (assoc minimal :id ["KEN" :year]))))
    (is (seq (node/problems (assoc minimal :id [:country :KEN :year])))))
  (testing "reads: none after the node's origin, none of an issued forecast"
    (is (seq (node/problems (assoc minimal :reads [{:dir :down :scale :year :place "s/eastern-africa" :origin 24301}]))))
    (is (seq (node/problems (assoc minimal :reads [{:dir :issued :scale :year :place "KEN" :origin 24288}]))))
    (is (empty? (node/problems (assoc minimal :reads [{:dir :self :scale :year :place "KEN" :origin 24288}])))))
  (testing "stocks, buffers and regime are checked"
    (is (seq (node/problems (assoc minimal :stocks {:grain {:level "x"}}))))
    (is (seq (node/problems (assoc minimal :stocks {:grain {:level 1.0 :sd -1.0}}))))
    (is (seq (node/problems (assoc minimal :buffers {:grain {:band :fragile}}))))
    (is (seq (node/problems (assoc minimal :regime {:probs {:calm 0.5 :strained 0.2}}))))
    (is (empty? (node/problems (assoc minimal :regime {:probs {:calm 0.5 :strained 0.3 :depleted 0.2}}))))))

(deftest a-node-from-a-lattice-entry
  (let [e {:p 0.2 :s (lt/logit-of 0.2) :f -1.4 :q 0.1 :strain 0.7 :issued {:p 0.25 :f -1.1 :q 0.2}}
        n (node/from-lattice-entry [:country "KEN" :year] 24300 :coup e 24)]
    (is (empty? (node/problems n)) (node/problems n))
    (is (close? 0.2 (get-in n [:hazard :coup :p]) 1e-12) "the state's p, not the issued one")
    (is (close? (node/p->rate 0.2 24) (get-in n [:hazard :coup :rate]) 1e-15))
    (is (not (contains? (set (keys n)) :issued)))
    (is (= {:id [:country "KEN" :year] :t 24300 :forecast {:coup {:p 0.25 :f -1.1 :q 0.2}}}
           (node/issued-from-lattice-entry [:country "KEN" :year] 24300 :coup e)))
    (testing "a second kind joins the same node"
      (let [n2 (node/with-kind n :war {:p 0.05 :s (lt/logit-of 0.05) :f -3.0 :q 0.0} 24)]
        (is (= #{:coup :war} (set (keys (:hazard n2)))))
        (is (contains? (:flags n2) :state-is-issued) "an entry without :issued: its state is its forecast (the loop)")))))

;; -- C6 ------------------------------------------------------------------------

(deftest c6-the-node-layer-never-holds-an-issued-forecast
  ;; Round 1's test, one layer up: with the issued forecasts as different
  ;; as the weighting can make them, the node states built from the books
  ;; are the same, none holds an issued forecast, and a reader over the
  ;; node book cannot reach the ledger
  (let [a (loop-run {:state-from :reference})
        b (loop-run {:state-from :reference :weighting {:rule :temper :power 1e-6}})
        sa (states a) sb (states b)
        flat (fn [{:keys [states]}] (for [[cell byo] states [t n] byo] [[cell t] n]))]
    (is (= (set (keys (:states sa))) (set (keys (:states sb)))))
    (is (every? empty? (map (comp node/problems second) (flat sa))))
    (is (every? (fn [[k n]] (let [n' (get-in sb [:states (first k) (second k)])]
                              (every? (fn [kind] (close? (get-in n [:hazard kind :p]) (get-in n' [:hazard kind :p]) 1e-12))
                                      (keys (:hazard n)))))
                (flat sa))
        "the states do not depend on the weighting")
    (is (some (fn [[cell byo]] (some (fn [[t f]] (> (Math/abs (- (get-in f [:forecast :crisis :p])
                                                                  (get-in sb [:issued cell t :forecast :crisis :p])))
                                                    0.01))
                                     byo))
              (:issued sa))
        "the issued forecasts differ")
    (testing "nothing a node book's reader returns carries an issued forecast"
      (let [r (sh/reader (:states sa) {} {:origin 1000})
            seen (for [cell (keys (:states sa)) :let [[_ n] (sh/latest-before r :self cell 1000)] :when n] n)]
        (is (seq seen))
        (is (not-any? #(node/holds-issued? %) seen))
        (is (not-any? #(= :issued (:dir %)) (sh/reads r)))))
    (testing "with the loop (no :state-from), each node is flagged: its state is its issued forecast"
      (let [s (states (loop-run {:end 60}))]
        (is (every? #(contains? (:flags %) :state-is-issued) (for [[_ byo] (:states s) [_ n] byo] n)))))))

;; -- C11 -------------------------------------------------------------------------

(deftest c11-one-way-per-edge-per-step
  (let [r (lt/run (assoc (spec-of (world-of 2)) :end 200 :trace true))
        g (node/read-graph (:trace r))]
    (is (seq g))
    (testing "within each origin, the reads form no cycle"
      (is (every? (fn [[_ edges]] (empty? (node/cycle-of edges))) g)))
    (testing "every read across origins is from before: down, self and parent at t - per"
      (doseq [{:keys [origin reads]} (:trace r) {:keys [dir] :as rd} reads]
        (case dir
          (:down :self) (is (< (:origin rd) origin))
          :feedback (is (<= (:known-at rd) origin))
          :up (is (<= (:origin rd) origin)))))
    (testing "children read up at the same origin, so the graph has same-origin edges"
      (is (some (fn [[_ edges]] (some seq (vals edges))) g))))
  (testing "a cycle is found"
    (is (seq (node/cycle-of {[:year "a"] #{[:year "b"]} [:year "b"] #{[:year "c"]} [:year "c"] #{[:year "a"]}})))
    (is (empty? (node/cycle-of {[:year "a"] #{[:year "b"]} [:year "b"] #{[:month "b"]}}))))
  (testing "a node read back at its own origin is a cycle of one"
    (is (seq (node/cycle-of {[:year "a"] #{[:year "a"]}})))))

;; -- random trees of node states (property style) -------------------------------------

(deftest random-node-trees-validate-and-compose
  (let [g (m/rng 5)]
    (dotimes [_ 30]
      (let [n (+ 2 (long (* 5 (m/next-double! g))))
            kids (vec (for [i (range n)]
                        (node/from-lattice-entry [:country (str "c" i) :year] 24300 :coup
                                                 (let [p (* 0.5 (m/next-double! g))] {:p p :s (lt/logit-of p) :f 0.0 :q 0.0 :issued {:p p}})
                                                 24)))]
        (is (every? empty? (map node/problems kids)))
        (testing "the rates a node carries give back its p"
          (is (every? #(close? (get-in % [:hazard :coup :p]) (node/hazard-p (get-in % [:hazard :coup])) 1e-12) kids)))))))

;; -- stocks and buffers from bnm.buffers records --------------------------------------------

(deftest stocks-and-buffers-from-records
  (let [rec (b/record {:id "test/X/tank" :name "tank" :unit "barrels" :time-unit "year"
                       :holder {:unit "refiner" :place "X"} :measure {:frequency 1 :lag 0}
                       :usable {:share 0.8 :sd 0.0} :restore {:time 2.0 :sd 0.5}
                       :claims {:rate 0.05 :maturity 5}})
        series {:stock (mapv (fn [t] {:t t :v (max 0.0 (- 100.0 (* 10.0 t)))}) (range 0 21))
                :claims [{:t 20 :v 30.0}] :income [{:t 20 :v 10.0}]}
        {:keys [stocks buffers]} (node/from-buffer-rows [{:record rec :series series}] 20)
        s (stocks "test/X/tank")
        bf (buffers "test/X/tank")
        rows (b/invariants rec series 20 {})
        row (fn [inv w] (first (filter #(and (= inv (:invariant %)) (= w (:window %))) rows)))]
    (is (= 0.0 (:level s)) "the latest known level")
    (is (= "barrels" (:unit s)))
    (is (= {:share 0.8 :sd 0.0} (:usable s)))
    (is (= rec (:record s)))
    (is (= {1 (:value (row :cover 1)) 3 (:value (row :cover 3)) 5 (:value (row :cover 5))} (:cover bf)))
    (is (= (:value (row :restoration 1)) (:restoration bf)))
    (is (= (:value (row :claim-gap nil)) (:claim-gap bf)))
    (is (= (:band (row :claim-gap nil)) (:band bf)))
    (is (= {5 (:value (row :displacement 5)) 10 (:value (row :displacement 10)) 20 (:value (row :displacement 20))}
           (:displacement bf)))
    (is (= :constrained (:regime bf)) "an empty stock: the switch to min(wanted, available)")
    (testing "a stock still holding is buffered; one with no reading unknown"
      (let [{:keys [buffers]} (node/from-buffer-rows [{:record rec :series series}] 5)]
        (is (= :buffered (:regime (buffers "test/X/tank")))))
      (let [{:keys [buffers]} (node/from-buffer-rows [{:record rec :series {}}] 5)]
        (is (= :unknown (:regime (buffers "test/X/tank"))))))
    (is (empty? (node/problems (merge minimal (node/from-buffer-rows [{:record rec :series series}] 20)))))))
