(ns bnm.hypergraph-test
  (:require [augur.math :as m]
            [bnm.hypergraph :as hg]
            [bnm.polycrisis :as pc]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))
(defn- finite? [x] (and (number? x) (not (Double/isNaN (double x))) (not (Double/isInfinite (double x)))))

(deftest a-recipe-is-a-hyperedge
  ;; steel needs energy AND ore (critical, Leontief): output is capped by
  ;; the scarcer; cars draw on steel and plastics, perfect substitutes with
  ;; no reallocation (linear, the CES limit sigma -> infinity)
  (let [h (-> (hg/hypergraph)
              (hg/recipe :steel {:energy 0.4 :ore 0.6} :critical)
              (hg/recipe :cars {:steel 0.7 :plastics 0.3} :substitutable))
        avail (hg/availability h {:energy 0.5 :ore 1.0 :plastics 1.0})]
    (is (= 0.5 (avail :steel)) "half the energy, half the steel, whatever the ore")
    (is (close? (avail :cars) (+ (* 0.7 0.5) (* 0.3 1.0)) 1e-12) "the shortfall passed on in proportion")
    (is (= 1.0 (avail :ore)) "a primary input as given")))

(deftest a-shortage-passes-down-a-chain
  ;; critical -> substitutable -> critical: energy halves steel, steel is
  ;; 70% of cars, and transport needs cars AND fuel
  (let [h (-> (hg/hypergraph)
              (hg/recipe :steel {:energy 0.4 :ore 0.6} :critical)
              (hg/recipe :cars {:steel 0.7 :plastics 0.3} :substitutable)
              (hg/recipe :transport {:cars 0.5 :fuel 0.5} :critical))
        avail (hg/availability h {:energy 0.5 :fuel 0.9})]
    (is (close? 0.65 (avail :cars) 1e-12))
    (is (close? 0.65 (avail :transport) 1e-12) "the scarcer of cars (0.65) and fuel (0.9)")
    (is (= 0.9 (avail :fuel)))))

(deftest ces-nests-leontief-cobb-douglas-and-linear
  (let [mk (fn [kind & [sigma]] (hg/availability (if sigma
                                                    (hg/recipe (hg/hypergraph) :y {:a 0.5 :b 0.5} kind sigma)
                                                    (hg/recipe (hg/hypergraph) :y {:a 0.5 :b 0.5} kind))
                                                  {:a 0.25 :b 1.0}))]
    (is (close? 0.5 ((mk :ces 1.0) :y) 1e-9) "sigma 1: the weighted geometric mean, sqrt(0.25 x 1)")
    (is (close? 0.25 ((mk :ces 0.01) :y) 1e-2) "sigma -> 0: Leontief, the minimum")
    (is (close? 0.625 ((mk :ces 1e6) :y) 1e-4) "sigma -> infinity: linear, the weighted mean")
    (is (< ((mk :critical) :y) ((mk :ces 0.5) :y) ((mk :ces 1.0) :y) ((mk :ces 2.0) :y) ((mk :substitutable) :y))
        "more substitution, less loss")
    (is (= 0.0 ((hg/availability (hg/recipe (hg/hypergraph) :y {:a 0.5 :b 0.5} :ces 0.5) {:a 0.0}) :y))
        "complements (sigma < 1): a missing input stops output")))

(deftest a-given-value-caps-an-intermediate-node
  ;; a steel plant fire: steel's own outage, whatever its inputs
  (let [h (hg/recipe (hg/hypergraph) :steel {:energy 0.4 :ore 0.6} :critical)]
    (is (= 0.3 ((hg/availability h {:steel 0.3}) :steel)) "the outage, below the recipe")
    (is (= 0.5 ((hg/availability h {:steel 0.8 :energy 0.5}) :steel)) "the recipe, below the outage")))

(deftest a-cycle-of-recipes-is-refused
  (let [h (-> (hg/hypergraph) (hg/recipe :a {:b 1.0} :critical) (hg/recipe :b {:c 1.0} :critical) (hg/recipe :c {:a 1.0} :critical))
        e (try (hg/availability h {}) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (some? e) "an ex-info, not a stack or memory blow-up")
    (is (= #{:a :b :c} (set (:cycle (ex-data e)))) "naming the cycle")))

(deftest propagate-agrees-with-availability-where-there-is-no-cycle
  (let [h (-> (hg/hypergraph)
              (hg/recipe :steel {:energy 0.4 :ore 0.6} :critical)
              (hg/recipe :cars {:steel 0.7 :plastics 0.3} :substitutable)
              (hg/recipe :transport {:cars 0.5 :fuel 0.5} :critical)
              (hg/recipe :parts {:steel 0.5 :labour 0.5} :ces 0.5))
        given {:energy 0.5 :fuel 0.9 :steel 0.8}
        a (hg/availability h given)
        p (hg/propagate h given)]
    (is (= (set (keys a)) (set (keys p))) "the same nodes")
    (doseq [[n x] a]
      (is (close? x (p n) 1e-9) (str n)))))

(deftest propagate-settles-a-cycle-at-its-largest-fixed-point
  ;; coal mines need electricity, coal plants need coal: a cut in coal
  ;; caps both at the cut, it does not spiral to zero
  (let [h (-> (hg/hypergraph)
              (hg/recipe :power {:coal 0.6 :labour 0.4} :critical)
              (hg/recipe :coal {:power 0.3 :machines 0.7} :critical))
        p (hg/propagate h {:coal 0.6})]
    (is (close? 0.6 (p :coal) 1e-9))
    (is (close? 0.6 (p :power) 1e-9))
    (is (= 1.0 (p :labour)) "an untouched primary input"))
  (testing "complements round a loop converge to the cut"
    (let [h (-> (hg/hypergraph)
                (hg/recipe :a {:b 0.5 :c 0.5} :ces 0.5)
                (hg/recipe :c {:a 1.0} :substitutable))
          p (hg/propagate h {:b 0.5})]
      (is (close? 0.5 (p :a) 1e-6))
      (is (close? 0.5 (p :c) 1e-6))))
  (testing "nothing given, nothing lost"
    (let [h (-> (hg/hypergraph) (hg/recipe :a {:b 1.0} :critical) (hg/recipe :b {:a 1.0} :critical))]
      (is (= {:a 1.0 :b 1.0} (hg/propagate h {}))))))

(defn- uniforms
  "A lazy stream of uniforms in (0, 1) from `seed` (Park-Miller)."
  [seed]
  (map #(/ (double %) 2147483647.0) (rest (iterate #(mod (* 16807 %) 2147483647) (inc seed)))))

(defn- random-economy
  "n recipe nodes and three primaries over a random graph with loops:
  each node draws two or three inputs from every node, its kind and
  sigma at random; a cut to some primaries and one recipe node."
  [seed n]
  (let [u (atom (uniforms seed))
        r! (fn [] (let [x (first @u)] (swap! u rest) x))
        pick (fn [xs] (nth xs (min (dec (count xs)) (long (* (r!) (count xs))))))
        nodes (mapv #(keyword (str "n" %)) (range n))
        prims [:p0 :p1 :p2]
        h (reduce (fn [h node]
                    (let [k (+ 2 (long (* 2 (r!))))
                          ins (into {} (for [_ (range k)] [(pick (into nodes prims)) (+ 0.1 (r!))]))
                          kind (pick [:critical :substitutable :ces :ces])]
                      (if (= kind :ces)
                        (hg/recipe h node ins :ces (pick [0.2 0.5 1.0 2.0]))
                        (hg/recipe h node ins kind))))
                  (hg/hypergraph) nodes)
        given (merge (into {} (for [p prims :when (< (r!) 0.7)] [p (r!)]))
                     {(pick nodes) (+ 0.3 (* 0.7 (r!)))})]
    [h given]))

(defn- jacobi
  "The greatest fixed point by synchronous iteration from the top: every
  recipe node recomputed from the previous sweep, until nothing moves."
  [h given]
  (let [top (reduce max 1.0 (vals given))
        x0 (into {} (for [n (keys (hg/propagate h given))] [n (if ((:recipes h) n) top (get given n 1.0))]))]
    (loop [x x0 i 0]
      (let [x' (into x (for [n (keys (:recipes h))] [n (hg/node-value h given x n)]))]
        (if (or (> i 20000) (< (reduce max 0.0 (for [[n v] x'] (Math/abs (- v (x n))))) 1e-14))
          x'
          (recur x' (inc i)))))))

(deftest prepared-recipes-and-their-rule
  ;; the pieces propagate is built from, public for solvers that run their
  ;; own iteration over the same rules (bnm.supply/outage-path)
  (let [h (-> (hg/hypergraph)
              (hg/recipe :steel {:energy 1.0 :ore 3.0} :critical)
              (hg/recipe :cars {:steel 3.0 :plastics 1.0} :substitutable)
              (hg/recipe :bread {:wheat 1.0 :labour 1.0} :ces 0.5))
        p (hg/prepared (:recipes h))
        x {:energy 0.5 :ore 1.0 :steel 0.5 :plastics 1.0 :wheat 0.25 :labour 1.0}]
    (testing "inputs in a vector, shares normalised to sum to 1"
      (is (= [:energy :ore] (:ins (p :steel))))
      (is (= [0.25 0.75] (:ws (p :steel))))
      (is (= :critical (:kind (p :steel))))
      (is (= 0.5 (:sigma (p :bread)))))
    (testing "the rule: minimum, share-weighted mean, CES"
      (is (= 0.5 (hg/rule (p :steel) x nil)))
      (is (close? (+ (* 0.75 0.5) 0.25) (hg/rule (p :cars) x nil) 1e-12))
      (is (close? (Math/pow (* 0.5 (+ (/ 1 0.25) 1.0)) -1.0) (hg/rule (p :bread) x nil) 1e-12)
          "sigma 0.5: rho -1, the weighted harmonic mean"))
    (testing "capped by the node's given value"
      (is (= 0.3 (hg/rule (p :steel) x 0.3)))
      (is (= 0.5 (hg/rule (p :steel) x 0.9))))
    (testing "node-value and propagate run the same rule"
      (is (= (hg/rule (p :cars) x nil) (hg/node-value h {} x :cars)))
      (let [a (hg/propagate h {:energy 0.5 :wheat 0.25})]
        (doseq [n [:steel :cars :bread]]
          (is (close? (a n) (hg/rule (p n) a nil) 1e-9) (str n)))))))

(deftest propagate-reaches-the-same-point-in-any-order
  ;; every rule is monotone, so iteration from the top falls to the
  ;; greatest fixed point whatever order the nodes are recomputed in:
  ;; first in first out, last in first out, or all at once
  (doseq [seed (range 1 31)]
    (let [[h given] (random-economy seed 10)
          fifo (hg/propagate h given {:tol 1e-13})
          lifo (hg/propagate h given {:tol 1e-13 :order :lifo})
          ref (jacobi h given)]
      (is (= :lifo (:order (meta lifo))) "the order asked for")
      (doseq [[n v] ref]
        (is (close? v (fifo n) 1e-7) (str "seed " seed " " n " fifo"))
        (is (close? v (lifo n) 1e-7) (str "seed " seed " " n " lifo"))))))

(deftest propagate-settles-every-node-within-tol
  (doseq [seed (range 1 11)]
    (let [[h given] (random-economy seed 12)
          p (hg/propagate h given {:tol 1e-9})]
      (is (<= (hg/residual h given p) 1e-9) (str "seed " seed ": every node within tol of its recipe"))
      (is (= (:residual (meta p)) (hg/residual h given p)) "the residual reported")
      (is (pos? (:updates (meta p))) "and the work it took")))
  (testing "never below the greatest fixed point: a slow loop stops above it"
    ;; a feeds itself 0.9: the greatest fixed point is 0, approached as 0.9^k
    (let [h (hg/recipe (hg/hypergraph) :a {:a 0.9 :b 0.1} :substitutable)
          p (hg/propagate h {:b 0.0} {:tol 1e-6})]
      (is (<= 0.0 (p :a) (/ 1e-6 (- 1 0.9))) "above it by at most tol / (1 - the loop's gain)"))))

(deftest propagate-reports-what-does-not-settle
  (let [h (hg/recipe (hg/hypergraph) :a {:a 0.999 :b 0.001} :substitutable)
        e (try (hg/propagate h {:b 0.0} {:tol 1e-12 :max-updates 50}) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (some? e) "throws, does not return a half-settled answer")
    (is (= 50 (:updates (ex-data e))))
    (is (< 1e-12 (:residual (ex-data e))) "how far off it was")))

(deftest propagate-starts-from-the-top
  (testing "a surge (given above 1) passes on as availability passes it"
    (let [h (-> (hg/hypergraph)
                (hg/recipe :y {:a 0.5 :b 0.5} :substitutable)
                (hg/recipe :z {:y 0.5 :c 0.5} :ces 0.5))
          given {:a 1.5 :c 0.8}
          a (hg/availability h given)
          p (hg/propagate h given)]
      (doseq [[n x] a] (is (close? x (p n) 1e-9) (str n)))))
  (testing "givens are availabilities: finite and not negative"
    (doseq [bad [##NaN -0.5 ##Inf]]
      (is (thrown? clojure.lang.ExceptionInfo
                   (hg/propagate (hg/recipe (hg/hypergraph) :y {:a 1.0} :critical) {:a bad}))
          (str bad)))))

(deftest recipes-are-validated
  (doseq [[why f] [["unknown kind" #(hg/recipe (hg/hypergraph) :y {:a 1.0} :magic)]
                   ["no inputs" #(hg/recipe (hg/hypergraph) :y {} :critical)]
                   ["a zero share" #(hg/recipe (hg/hypergraph) :y {:a 0.0 :b 1.0} :substitutable)]
                   ["a negative share" #(hg/recipe (hg/hypergraph) :y {:a -1.0} :critical)]
                   ["ces without sigma" #(hg/recipe (hg/hypergraph) :y {:a 1.0} :ces)]
                   ["a non-positive sigma" #(hg/recipe (hg/hypergraph) :y {:a 1.0} :ces 0.0)]]]
    (is (thrown? clojure.lang.ExceptionInfo (f)) why)))

;; ---- joint crises

(defn- sample-loglinear
  "n draws from p(a,b,c) ∝ exp(main·x + pairs·xy + three·abc), as sets."
  [g n [ua ub uc] [uab ubc uac] uabc]
  (let [ks (vec (for [a [0 1] b [0 1] c [0 1]] [a b c]))
        w (for [[a b c] ks] (Math/exp (+ (* ua a) (* ub b) (* uc c) (* uab a b) (* ubc b c) (* uac a c) (* uabc a b c))))
        z (reduce + w)
        cum (vec (reductions + (map #(/ % z) w)))]
    (vec (for [_ (range n)]
           (let [u (m/next-double! g)
                 [a b c] (or (some (fn [i] (when (< u (cum i)) (ks i))) (range 8)) (ks 7))]
             (cond-> #{} (= 1 a) (conj :a) (= 1 b) (conj :b) (= 1 c) (conj :c)))))))

(defn- independence-lift
  "The triple's observed share against the product of single margins."
  [obs]
  (let [n (double (count obs)) p (fn [k] (/ (count (filter #(% k) obs)) n))]
    (/ (/ (count (filter #(every? % [:a :b :c]) obs)) n) (* (p :a) (p :b) (p :c)))))

(deftest joint-crises-beyond-the-pairs
  (let [g (m/rng 1)
        draw (fn [p] (< (m/next-double! g) p))
        indep (vec (for [_ (range 40000)] (set (filter (fn [_] (draw 0.2)) [:a :b :c]))))
        pairs-only (sample-loglinear g 40000 [-2.0 -2.0 -2.0] [1.5 1.5 1.5] 0.0)
        synergy (vec (for [_ (range 40000)]
                       (let [a (draw 0.3) b (draw 0.3) c (if (and a b) (draw 0.9) (draw 0.05))]
                         (cond-> #{} a (conj :a) b (conj :b) c (conj :c)))))]
    (testing "independent: about one"
      (is (close? 1.0 (:lift (hg/lift indep [:a :b :c])) 0.1))
      (is (> (:p (hg/lift indep [:a :b :c])) 0.01)))
    (testing "associated only in pairs: the fit absorbs them, independence does not"
      (let [r (hg/lift pairs-only [:a :b :c])]
        (is (close? 1.0 (:lift r) 0.1) "no three-way term: lift about one")
        (is (close? 0.0 (:log-ror r) 0.25) "and the three-way log ratio of odds ratios about zero")
        (is (> (:p r) 0.01) "G² does not reject")
        (is (> (independence-lift pairs-only) 2.0) "while the product of single margins is far off")))
    (testing "the third only when the first two meet: a three-way term"
      (let [r (hg/lift synergy [:a :b :c])]
        (is (> (:lift r) 1.2) "the pairs absorb part of it")
        (is (> (:log-ror r) 2.0) "log(odds ratio of c given a and b) is ~log(171), against ~0 without the term")
        (is (< (:p r) 1e-10) "G² rejects")))))

(def ^:private table {[1 1 1] 30 [1 1 0] 10 [1 0 1] 5 [0 1 1] 7 [1 0 0] 20 [0 1 0] 25 [0 0 1] 12 [0 0 0] 91})

(deftest the-fit-keeps-every-pair-and-drops-the-triple
  (let [e (hg/no-three-way table)
        mg (fn [x f v] (reduce + (for [[c n] x :when (= (f c) v)] n)))]
    (doseq [f [(fn [[a b _]] [a b]) (fn [[_ b c]] [b c]) (fn [[a _ c]] [a c])] v [[1 1] [0 1] [1 0] [0 0]]]
      (is (close? (mg table f v) (mg e f v) 1e-6)))
    (is (close? 0.0 (hg/log-ror e) 1e-8) "the fitted table has no three-way term (the observed table returned would fail)")
    (is (close? (Math/log (/ (* 30.0 20 25 12) (* 10.0 5 7 91))) (hg/log-ror table) 1e-12) "the observed one, by hand")
    (is (close? (* 2 (reduce + (for [[c o] table] (* o (Math/log (/ o (e c))))))) (hg/g2 table e) 1e-9) "G² = 2 Σ O ln(O/E)")))

(deftest zero-cells
  (testing "a zero cell: the ratio of odds ratios takes 0.5 in every cell, so stays finite"
    (let [t (assoc table [1 1 0] 0)]
      (is (finite? (hg/log-ror t)))
      (is (close? (Math/log (/ (* 30.5 20.5 25.5 12.5) (* 0.5 5.5 7.5 91.5))) (hg/log-ror t) 1e-12))
      (is (finite? (:lift (hg/lift-table t))))))
  (testing "no joint windows at all for a pair: no expected triple, no lift, but a test"
    (let [t {[1 0 1] 5 [0 1 1] 7 [1 0 0] 20 [0 1 0] 25 [0 0 1] 12 [0 0 0] 91}
          r (hg/lift-table t)]
      (is (nil? (:lift r)))
      (is (zero? (:expected r)))
      (is (finite? (:g2 r)))))
  (testing "a fit with zeros converges and keeps the margins"
    (let [t {[1 1 1] 0 [1 1 0] 10 [1 0 1] 10 [0 1 1] 10 [1 0 0] 30 [0 1 0] 30 [0 0 1] 30 [0 0 0] 1000}
          e (hg/no-three-way t)]
      (is (close? 10.0 (+ (e [1 1 1]) (e [1 1 0])) 1e-6)))))

(deftest benjamini-hochberg
  (is (= [0.02 0.04 0.04 0.02] (mapv #(/ (Math/round (* % 1e6)) 1e6) (hg/bh [0.01 0.04 0.03 0.005]))))
  (is (= [1.0] (hg/bh [1.0]))))

(deftest simplices-of-a-place
  ;; the kinds with an onset at a place within a window: its simplex
  (is (= #{"bank" "currency"} (hg/simplex {"bank" #{1997} "currency" #{1998} "coup" #{2005}} 1997 3)))
  (is (= #{} (hg/simplex {"bank" #{1990}} 1997 3))))

(deftest windows-of-a-country
  ;; three kinds covering X over 1960-1965; windows wholly inside the cover
  (let [cats {"a" {:cover {"X" [1960 1965]} :onsets {"X" #{1961 1964}}}
              "b" {:cover {"X" [1960 1965]} :onsets {"X" #{1962}}}
              "c" {:cover {"X" [1960 1965] "Y" [1960 1965]} :onsets {"X" #{1966}}}
              "d" {:cover {"X" [1961 1965]} :onsets {}}}]
    (is (= [{:covered #{"a" "b" "c"} :hit #{"a" "b"}} {:covered #{"a" "b" "c" "d"} :hit #{"a"}}]
           (get (pc/windows cats 1960 1965) "X"))
        "1960-62 (d not yet covered) and 1963-65; 1966 is past the cover")
    (is (= [{:covered #{"a" "b" "c" "d"} :hit #{"a" "b"}}] (get (pc/windows cats 1961 1965) "X"))
        "a later phase: 1961-63 only, 1964-66 runs past the cover")
    (is (= [] (get (pc/windows cats 1960 1965) "Y")) "fewer than three kinds covered")))

(deftest nested-pairs
  ;; b comes with a in 10 of a's 14 onset windows; c rarely with anything;
  ;; d always with a, but only twice: too few to read
  (let [w (fn [& hit] {:covered #{"a" "b" "c" "d"} :hit (set hit)})
        ws {"X" (vec (concat (repeat 10 (w "a" "b")) (repeat 2 (w "a" "c")) (repeat 5 (w "b")) (repeat 20 (w "c")) (repeat 2 (w "a" "d"))))}
        n (pc/nesting ws ["a" "b" "c" "d"])]
    (is (< (Math/abs (- (/ 10.0 14) (n #{"a" "b"}))) 1e-12) "the larger conditional share: 10 of a's 14 (b's is 10 of 15)")
    (is (nil? (n #{"a" "d"})) "two joint onsets: not read")
    (is (nil? (n #{"a" "c"})))))
