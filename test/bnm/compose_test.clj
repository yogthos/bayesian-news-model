(ns bnm.compose-test
  "Coherence invariants C3-C5 (docs/RECURSIVE.md §4): identity quantities
  survive the zoom up and down both trees exactly; hazards compose over a
  shared frailty; windows compose by survival only when they do not
  overlap."
  (:require [augur.math :as m]
            [bnm.compose :as c]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (<= (Math/abs (- (double a) (double b))) tol))

;; -- random trees, seeded ------------------------------------------------------

(defn- random-tree
  "A place tree of `depth` levels below a root, 2-4 children per node:
  {:id [level place scale] :children [...]} with leaves carrying a stock,
  a hazard rate and their flows."
  [g depth]
  (letfn [(node [level path d]
            (let [id [level (apply str (interpose "." path)) :year]]
              (if (zero? d)
                {:id id
                 :stocks {:grain {:level (* 100.0 (m/next-double! g)) :sd (* 5.0 (m/next-double! g)) :unit "t"}}
                 :hazard {:coup {:z 0.0 :z-var 0.0 :rate (* 0.01 (m/next-double! g)) :window 24}}}
                {:id id
                 :children (vec (for [i (range (+ 2 (long (* 3 (m/next-double! g)))))]
                                  (node (keyword (str "l" (dec d))) (conj path i) (dec d))))})))]
    (node (keyword (str "l" depth)) ["w"] depth)))

(defn- leaves [t] (if-let [cs (:children t)] (mapcat leaves cs) [t]))

(defn- up
  "Each inner node's stock and rate summed from its children, bottom-up."
  [t]
  (if-let [cs (seq (:children t))]
    (let [cs (mapv up cs)
          {:keys [level sd]} (c/sum-stocks cs :grain)]
      (assoc t :children cs
             :stocks {:grain {:level level :sd sd :unit "t"}}
             :hazard {:coup {:z 0.0 :z-var 0.0 :rate (:rate (c/rate-sum cs :coup)) :window 24}}))
    t))

(deftest c3-zoom-is-lossless-for-identities
  (let [g (m/rng 42)]
    (dotimes [_ 25]
      (let [t (up (random-tree g 3))
            ls (leaves t)
            total (get-in t [:stocks :grain :level])]
        (testing "a stock summed up three levels is the sum of its leaves"
          (is (close? total (reduce + (map #(get-in % [:stocks :grain :level]) ls)) 1e-9)))
        (testing "rates add up the tree exactly"
          (is (close? (get-in t [:hazard :coup :rate]) (reduce + (map #(get-in % [:hazard :coup :rate]) ls)) 1e-12)))
        (testing "(aggregate (allocate x shares)) = x"
          (let [shares (vec (repeatedly (count ls) #(m/next-double! g)))]
            (is (close? (c/aggregate (c/allocate total shares)) total (* 1e-12 (Math/abs total))))))
        (testing "(allocate (aggregate xs) (shares xs)) = xs"
          (let [xs (mapv #(get-in % [:stocks :grain :level]) ls)
                back (c/allocate (c/aggregate xs) (c/shares xs))]
            (is (every? true? (map #(close? %1 %2 1e-12) xs back)))))
        (testing "survival-chain after cumhaz-split is the identity"
          (let [p (* 0.9 (m/next-double! g))
                cuts (vec (sort (distinct (map long (repeatedly 4 #(* 24 (m/next-double! g)))))))
                bounds (vec (distinct (concat [0] (remove #{0 24} cuts) [24])))
                parts (mapv (fn [a b] {:from a :to b :share (+ 0.01 (m/next-double! g))}) bounds (rest bounds))
                fine (c/cumhaz-split {:p p :from 0 :to 24} parts)
                back (c/survival-chain fine)]
            (is (close? (:p back) p 1e-12) [p back])
            (is (= [0 24] [(:from back) (:to back)]))
            (testing "and on cumulative hazards, the split sums back"
              (let [lam (- (Math/log1p (- p)))]
                (is (close? (reduce + (c/cumhaz-split lam (map :share parts))) lam 1e-12))))))))))

(deftest sum-stocks-reports-what-it-could-not-sum
  (let [cs [{:id [:country "a" :year] :stocks {:grain {:level 10.0 :sd 3.0}}}
            {:id [:country "b" :year] :stocks {:grain {:level 5.0 :sd 4.0}}}
            {:id [:country "c" :year] :stocks {}}]
        s (c/sum-stocks cs :grain)]
    (is (= 15.0 (:level s)))
    (is (close? 5.0 (:sd s) 1e-12) "sd from independence")
    (is (= 2 (:n s)))
    (is (= [[:country "c" :year]] (:missing s)))
    (is (contains? (:flags s) :independence-assumed))
    (testing "with a covariance the sd uses it"
      (is (close? (Math/sqrt (+ 9.0 16.0 (* 2 6.0)))
                  (:sd (c/sum-stocks (pop cs) :grain {:cov {[[:country "a" :year] [:country "b" :year]] 6.0}})) 1e-12)))))

(deftest c1-internal-flows-cancel-at-the-parent
  (let [a [:country "a" :year] b [:country "b" :year]
        trade {:rate 7.0 :from [a :grain] :to [b :grain]}
        imp {:rate 3.0 :from :outside :to :grain}
        exp {:rate 2.0 :from :grain :to :outside}
        cs [{:id a :flows {:trade-ab trade :import imp}}
            {:id b :flows {:trade-ab trade :export exp}}]
        r (c/net-flows cs)]
    (is (close? 1.0 (:external-net r) 1e-12) "3 in, 2 out")
    (is (close? 1.0 (:net r) 1e-12) "the children's nets sum to it")
    (is (close? 0.0 (:residual r) 1e-12))
    (is (= #{:import :export} (set (keys (:external r)))))
    (is (= {:trade-ab 7.0} (:internal r)))
    (testing "an internal flow booked by one side only is a residual, not an external flow"
      (let [r (c/net-flows [(first cs) (update (second cs) :flows dissoc :trade-ab)])]
        (is (close? 1.0 (:external-net r) 1e-12))
        (is (close? -7.0 (:residual r) 1e-12))))
    (testing "a caller's internal? decides what is inside"
      (let [r (c/net-flows cs (constantly false))]
        (is (contains? (:external r) :trade-ab))))))

(deftest c1-random-flow-networks-close
  (let [g (m/rng 7)]
    (dotimes [_ 50]
      (let [n (+ 2 (long (* 6 (m/next-double! g))))
            ids (mapv #(vector :country (str "c" %) :year) (range n))
            pick #(nth ids (long (* n (m/next-double! g))))
            internal (vec (for [k (range 10) :let [i (pick) j (pick)] :when (not= i j)]
                            [(keyword (str "f" k)) {:rate (m/next-double! g) :from [i :s] :to [j :s]}]))
            external (vec (for [k (range 6) :let [i (pick) in? (< (m/next-double! g) 0.5)]]
                            [[(keyword (str "x" k)) i] (if in? {:rate (m/next-double! g) :from :outside :to :s}
                                                           {:rate (m/next-double! g) :from :s :to :outside})]))
            cs (for [id ids]
                 {:id id :flows (merge (into {} (for [[fid f] internal :when (or (= id (first (:from f))) (= id (first (:to f))))] [fid f]))
                                       (into {} (for [[[fid i] f] external :when (= i id)] [fid f])))})
            r (c/net-flows cs)
            ext (reduce + (for [[_ f] external] (if (= :outside (:from f)) (:rate f) (- (:rate f)))))]
        (is (close? ext (:external-net r) 1e-12))
        (is (close? ext (:net r) 1e-12))
        (is (close? 0.0 (:residual r) 1e-12))))))

(deftest c4-hazard-composition
  (let [g (m/rng 3)
        kids (fn [] (vec (for [i (range 5)] {:id [:country (str i) :year]
                                            :hazard {:war {:rate (* 0.004 (m/next-double! g))}}})))]
    (testing "λ_P = Σ λ_c exactly"
      (dotimes [_ 20]
        (let [cs (kids)]
          (is (= (reduce + (map #(get-in % [:hazard :war :rate]) cs)) (:rate (c/rate-sum cs :war)))))))
    (testing "missing rates are reported, not read as 0"
      (is (= [[:country "x" :year]] (:missing (c/rate-sum [{:id [:country "x" :year] :hazard {}}] :war)))))
    (testing "with v = 0, P_any = 1 - Π(1 - p_c)"
      (dotimes [_ 20]
        (let [cs (kids) w 24
              ps (map #(c/rate->p (get-in % [:hazard :war :rate]) w) cs)]
          (is (close? (- 1.0 (reduce * (map #(- 1.0 %) ps)))
                      (c/any-onset cs :war {:z 0.0 :z-var 0.0} w) 1e-12)))))
    (testing "a shared frailty z shifts every child's log rate"
      (let [cs (kids)]
        (is (close? (c/any-onset (mapv #(update-in % [:hazard :war :rate] * (Math/exp 0.7)) cs) :war {:z 0.0 :z-var 0.0} 24)
                    (c/any-onset cs :war {:z 0.7 :z-var 0.0} 24) 1e-12))))
    ;; The sign (computed, then asserted). P_any = 1 - E[exp(-Λ w e^z)],
    ;; concave in the rate multiplier e^z: at a fixed expected count
    ;; (E[e^z] = 1, so z = -v/2) frailty variance LOWERS the chance of any
    ;; onset (the onsets bunch in the bad draws), whatever the rate. At a
    ;; fixed mean log rate (z = 0) the multiplier's mean rises with v
    ;; (e^{v/2}) and, for rare events (Λw well under 1), that wins: P_any
    ;; RISES with v. The design note's "increases at a fixed mean" holds only
    ;; for the mean of z, not of the rate.
    (testing "frailty variance: the computed direction"
      (let [cs (kids) w 24
            lam (:rate (c/rate-sum cs :war))
            at (fn [z v] (c/any-onset cs :war {:z z :z-var v} w))]
        (is (< (* lam w) 1.0) "rare: the regime the lattice lives in")
        (testing "at a fixed expected count, P_any falls as v grows"
          (let [ps (mapv #(at (* -0.5 %) %) [0.0 0.1 0.25 0.5 1.0])]
            (is (apply > ps) ps)))
        (testing "at a fixed mean log rate, P_any rises as v grows (rare events)"
          (let [ps (mapv #(at 0.0 %) [0.0 0.1 0.25 0.5 1.0])]
            (is (apply < ps) ps)))
        (testing "the quadrature's expected count is the lognormal mean"
          (is (close? (c/frailty-mean {:z -0.25 :z-var 0.5}) 1.0 1e-3)))))))

(deftest c5-time-composition
  (testing "a linearly rising hazard over 24 months: monthly pieces compose to the window"
    (let [a 0.002 b 0.0005
          cum (fn [t0 t1] (+ (* a (- t1 t0)) (* 0.5 b (- (* t1 t1) (* t0 t0)))))
          analytic (- (Math/expm1 (- (cum 0 24))))
          months (mapv (fn [k] {:from k :to (inc k) :p (- (Math/expm1 (- (cum k (inc k)))))}) (range 24))
          quarters (mapv (fn [k] {:from (* 3 k) :to (* 3 (inc k)) :rate (/ (cum (* 3 k) (* 3 (inc k))) 3.0)}) (range 8))]
      (is (close? analytic (:p (c/survival-chain months)) 1e-9))
      (is (close? analytic (:p (c/survival-chain quarters)) 1e-9) "per-month rates over quarters")
      (testing "a constant hazard at the mean rate is not the same quantity over a sub-window"
        (is (> (Math/abs (- (c/rate->p (/ (cum 0 24) 24.0) 12) (- (Math/expm1 (- (cum 0 12)))))) 1e-3)))))
  (testing "overlapping windows are refused"
    (is (thrown? clojure.lang.ExceptionInfo
                 (c/survival-chain [{:from 0 :to 12 :p 0.1} {:from 3 :to 15 :p 0.1}])))
    (is (thrown? clojure.lang.ExceptionInfo
                 (c/survival-chain [{:from 0 :to 12 :p 0.1} {:from 0 :to 12 :p 0.1}]))))
  (testing "a gap is refused unless allowed"
    (is (thrown? clojure.lang.ExceptionInfo (c/survival-chain [{:from 0 :to 3 :p 0.1} {:from 6 :to 9 :p 0.1}])))
    (is (close? 0.19 (:p (c/survival-chain [{:from 0 :to 3 :p 0.1} {:from 6 :to 9 :p 0.1}] {:gaps :allow})) 1e-12)))
  (testing "a window without its bounds is refused: it cannot be checked"
    (is (thrown? clojure.lang.ExceptionInfo (c/survival-chain [{:p 0.1} {:p 0.2}])))))

(deftest reconcile-project-meets-linear-identities
  (let [ids [:a :b :c :p]
        ;; P = a + b + c
        ident [{:coefs {:a 1.0 :b 1.0 :c 1.0 :p -1.0} :b 0.0}]]
    (testing "Euclidean (augur.enkf/project) without sds"
      (let [{:keys [values residual]} (c/reconcile-project {:a 1.0 :b 2.0 :c 3.0 :p 7.0} ident)]
        (is (close? 0.0 (- (+ (:a values) (:b values) (:c values)) (:p values)) 1e-12))
        (is (close? -1.0 (first residual) 1e-12) "the gap before")
        (is (close? (- (:a values) 1.0) (- (:b values) 2.0) 1e-12) "the gap shared evenly")))
    (testing "weighted by variance: a value known exactly does not move"
      (let [{:keys [values]} (c/reconcile-project {:a {:level 1.0 :sd 1.0} :b {:level 2.0 :sd 1.0}
                                                   :c {:level 3.0 :sd 1.0} :p {:level 7.0 :sd 0.0}} ident)]
        (is (= 7.0 (:p values)))
        (is (close? 7.0 (+ (:a values) (:b values) (:c values)) 1e-12))))
    (testing "random identities hold after projection"
      (let [g (m/rng 11)]
        (dotimes [_ 20]
          (let [vals (zipmap ids (repeatedly #(* 10 (m/next-double! g))))
                ident [{:coefs {:a 1.0 :b 1.0 :c 1.0 :p -1.0} :b 0.0}
                       {:coefs {:a 2.0 :c -1.0} :b 1.0}]
                {:keys [values]} (c/reconcile-project vals ident)]
            (doseq [{:keys [coefs b]} ident]
              (is (close? b (reduce + (for [[k a] coefs] (* a (values k)))) 1e-9)))))))))
