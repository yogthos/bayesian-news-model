(ns bnm.control-test
  (:require [bnm.control :as c]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [tol a b] (and a b (< (Math/abs (- (double a) (double b))) tol)))

(defn- noise [& ks]
  ;; a fixed pseudo-random value in [-0.5, 0.5)
  (let [h (Math/abs (long (hash (vec ks))))]
    (- (/ (double (mod h 10007)) 10007.0) 0.5)))

;; A deterministic two-factor world. Country i has a state level s_i and a
;; capture level k_i (not correlated with s); indicator j reads
;; 10 + l_j . [s k] + noise. "own" loads only on the state axis.
(def truth {"own" [1.0 0.0] "bank" [0.8 0.1] "open" [-0.7 0.0] "pubk" [0.6 -0.1]
            "corr" [0.2 0.9] "top1" [0.0 0.8]})
(def countries (mapv #(str "C" %) (range 60)))
(defn- s-level [i] (- (* 0.06 i) 1.8))
(defn- k-level [i] (* 1.5 (Math/sin (* 1.7 i))))

(def rows
  (vec (for [[i c] (map-indexed vector countries)
             y (range 1990 2011)
             [j [a b]] truth
             :let [s (+ (s-level i) (* 0.01 (- y 2000)))
                   k (k-level i)]]
         {:iso c :year y :known (+ y 1) :indicator j
          :value (+ 10.0 (* a s) (* b k) (* 0.5 (noise i j y)))})))

(def spec2
  {:indicators (vec (keys truth))
   :axes [{:name "state" :signs {"own" 1 "bank" 1 "open" -1 "pubk" 1} :min-k 1}
          {:name "capture" :signs {"corr" 1 "top1" 1} :zero #{"own"} :min-k 1}]})

(def spec1
  {:indicators ["own" "bank" "open" "pubk"]
   :axes [{:name "state" :signs {"own" 1 "bank" 1 "open" -1 "pubk" 1} :min-k 1}]})

(defn- corr [xs ys]
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sxx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
        syy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
    (/ sxy (Math/sqrt (* sxx syy)))))

(defn- x-of [rs iso y]
  (into {} (for [r rs :when (and (= iso (:iso r)) (= y (:year r)))] [(:indicator r) (:value r)])))

(deftest two-factor-recovery
  (let [fit (c/fit-rows rows spec2)
        l (:loading fit)]
    (testing "every prior sign holds and the structural zero is exact"
      (is (pos? (first (l "own"))))
      (is (neg? (first (l "open"))))
      (is (pos? (second (l "corr"))))
      (is (pos? (second (l "top1"))))
      (is (= 0.0 (second (l "own")))))
    (testing "the loadings carry the true pattern (Tucker congruence per axis)"
      (doseq [k [0 1]]
        (is (> (c/congruence (mapv #(nth (l %) k) (keys truth)) (mapv #(nth (truth %) k) (keys truth))) 0.95) k)))
    (testing "scores order countries on each axis"
      (let [ss (mapv #(:f (c/score fit (x-of rows (countries %) 2000))) (range 60))]
        (is (> (corr (map first ss) (map s-level (range 60))) 0.95))
        (is (> (corr (map second ss) (map k-level (range 60))) 0.9))))))

(deftest sign-prior-binds
  (testing "an indicator whose data contradict its prior sign is held at zero, not flipped"
    (let [bad (mapv #(if (= "pubk" (:indicator %)) (update % :value (fn [v] (- 20.0 v))) %) rows)
          fit (c/fit-rows bad spec1)]
      (is (close? 1e-9 0.0 (first ((:loading fit) "pubk"))))
      (is (contains? (set (c/pinned fit spec1)) ["pubk" "state"]))
      (is (pos? (first ((:loading fit) "own")))))))

(deftest missing-data-and-scoring
  (let [holes (vec (remove (fn [{:keys [iso indicator year]}]
                             (let [i (parse-long (subs iso 1))]
                               (or (and (even? i) (#{"bank" "top1"} indicator))
                                   (and (= indicator "own") (zero? (mod (+ i year) 3))))))
                           rows))
        full (c/fit-rows rows spec2)
        part (c/fit-rows holes spec2)]
    (testing "the fit with holes is close to the full fit"
      (doseq [j (keys truth) k [0 1]]
        (is (close? 0.1 (nth ((:loading full) j) k) (nth ((:loading part) j) k)) [j k])))
    (testing "fewer indicators, wider posterior; k says how many"
      (let [x (x-of rows "C30" 2000)
            s6 (c/score full x)
            s1 (c/score full (select-keys x ["own"]))]
        (is (= 6 (:k s6)))
        (is (= 1 (:k s1)))
        (is (< (first (:sd s6)) (first (:sd s1))))))
    (testing "an axis is scored only with :min-k of its signed indicators"
      (let [s (c/score full (select-keys (x-of rows "C30" 2000) ["own" "open"]) spec2)]
        (is (= [true false] (:ok s)))))
    (testing "nothing known, no score"
      (is (nil? (c/score full {})))
      (is (nil? (c/score full {"zzz" 1.0}))))))

(deftest expanding-is-causal
  (let [years [2000 2005 2011]
        res (c/expanding rows years spec2)
        cut (c/expanding (filterv #(<= (:known %) 2005) rows) [2000 2005] spec2)
        at (fn [r y] (into {} (for [s (:scores r) :when (= y (:year s))] [(:iso s) (:f s)])))]
    (testing "scores at 2005 do not change when everything known later is removed"
      (is (= (at res 2005) (at cut 2005)))
      (is (= (get-in res [:fits 2005 :loading]) (get-in cut [:fits 2005 :loading]))))
    (testing "no score uses a value not known by its year"
      (is (every? #(<= (:data-year %) (dec (:year %))) (:scores res))))))

(deftest congruence-and-stability
  (is (close? 1e-12 1.0 (c/congruence [1 2 3] [2 4 6])))
  (is (close? 1e-12 -1.0 (c/congruence [1 0] [-1 0])))
  (let [st (c/stability rows spec2 {:years [2000 2011] :ref 2011})]
    (testing "a well-identified synthetic axis is stable across fits and halves"
      (is (every? #(> % 0.9) (vals (:split-half st))))
      (is (every? #(> % 0.9) (map :min (vals (:over-time st))))))))

(deftest stock-rows
  (let [res (c/expanding rows [2005] spec2)
        out (c/stock-rows res spec2)
        by (group-by :variable out)]
    (testing "axes, their SDs and the count, dated by the year known with the data year"
      (is (= #{"control/state" "control/state-sd" "control/capture" "control/capture-sd" "control/n"}
             (set (keys by))))
      (is (every? #(and (= 2005 (:year %)) (<= (:data-year %) 2004)) out))))
  (testing "indicator stocks: year known, the latest data year known by then"
    (let [in [{:iso "X" :year 2011 :known 2019 :indicator "bank" :value 0.3}
              {:iso "X" :year 2016 :known 2019 :indicator "bank" :value 0.4}
              {:iso "X" :year 2017 :known 2019 :indicator "zzz" :value 1.0}]
          out (c/indicator-rows in #{"bank"})]
      (is (= [{:iso "X" :year 2019 :variable "control/bank" :value 0.4 :data-year 2016}] out)))))

(deftest stock-names-do-not-collide
  (testing "an axis's stock names are not an indicator's"
    (let [axis-vars (set (for [{:keys [name]} (:axes c/spec-2) v [name (str name "-sd")]] v))]
      (is (empty? (filter (conj axis-vars "n") c/indicator-stocks))))))

(deftest row-loglik-matches-the-direct-density
  (testing "Woodbury's form equals log N(x; 0, l l' + Psi) computed directly (K = 1, two indicators)"
    (let [l [0.8 -0.5] psi [0.3 0.6] x [0.4 -1.1]
          [a b] l [p q] psi
          s11 (+ (* a a) p) s22 (+ (* b b) q) s12 (* a b)
          dt (- (* s11 s22) (* s12 s12))
          [x1 x2] x
          quad (/ (+ (* s22 x1 x1) (* -2.0 s12 x1 x2) (* s11 x2 x2)) dt)
          direct (* -0.5 (+ (* 2 (Math/log (* 2 Math/PI))) (Math/log dt) quad))
          got (#'bnm.control/row-loglik [(int-array [0 1]) (double-array x)] (double-array l) (double-array psi) 1)]
      (is (close? 1e-10 direct got)))))

(deftest a-bad-warm-start-does-not-stick
  (testing "the fit from a wrong earlier fit equals the fit from the prior's start"
    (let [cold (c/fit-rows rows spec2)
          wrong {:loading {"own" [0.1 0.0] "bank" [0.0 0.9] "open" [0.0 -0.9] "pubk" [0.9 0.0]
                           "corr" [0.0 0.1] "top1" [0.9 0.1]}
                 :psi (zipmap (keys truth) (repeat 0.9))}
          warm (c/fit-rows rows spec2 {:start wrong})]
      (doseq [j (keys truth) k [0 1]]
        (is (close? 0.02 (nth ((:loading cold) j) k) (nth ((:loading warm) j) k)) [j k])))))

(deftest composites-join-one-stock
  (testing "two measures of one stock enter as one indicator: the mean of their z"
    (let [rs (into rows (for [r rows :when (= "pubk" (:indicator r))]
                         (-> r (assoc :indicator "pubk2") (update :value #(+ 3.0 (* 2.0 %) (* 0.3 (noise (:iso r) (:year r))))))))
          spec (assoc spec1 :indicators ["own" "bank" "open" "pubk" "pubk2"]
                      :composites {"pub" ["pubk" "pubk2"]}
                      :axes [{:name "state" :signs {"own" 1 "bank" 1 "open" -1 "pub" 1} :min-k 1}])
          fit (c/fit-rows rs spec)
          x (x-of rs "C30" 2000)]
      (is (= #{"own" "bank" "open" "pub"} (set (keys (:loading fit)))))
      (is (pos? (first ((:loading fit) "pub"))))
      (is (= 4 (:k (c/score fit x spec))))
      (testing "either component alone scores the composite"
        (is (= 2 (:k (c/score fit (select-keys x ["own" "pubk2"]) spec))))))))

;; A near-Heywood world: "own" reads the factor almost without noise, the
;; rest weakly, so ML drives own's uniqueness to its bound.
(def heywood-rows
  (vec (for [[i c] (map-indexed vector countries)
             y (range 1990 2011)
             [j a e] [["own" 1.0 0.02] ["bank" 0.4 1.0] ["open" -0.4 1.0] ["pubk" 0.3 1.0]]
             :let [s (s-level i)]]
         {:iso c :year y :known (+ y 1) :indicator j
          :value (+ 10.0 (* a s) (* e (noise i j y)))})))

(deftest a-uniqueness-bound-holds
  (let [ml (c/fit-rows heywood-rows spec1)
        bounded (c/fit-rows heywood-rows (assoc spec1 :psi-floor 0.2))]
    (testing "ML runs own's uniqueness under 0.2: improper by the registered rule"
      (is (< (get-in ml [:psi "own"]) 0.2))
      (is (c/improper? ml 0.2)))
    (testing "the spec's bound holds every uniqueness at 0.2 or more, and the fit is not improper below it"
      (is (every? #(>= % (- 0.2 1e-12)) (vals (:psi bounded))))
      (is (not (c/improper? bounded 0.19))))
    (testing "the bound moves weight off the one indicator: the others' loadings rise"
      (is (< (first (get-in bounded [:loading "own"])) (first (get-in ml [:loading "own"]))))
      (is (> (Math/abs (double (first (get-in bounded [:loading "open"]))))
             (Math/abs (double (first (get-in ml [:loading "open"])))))))
    (testing "an explicit option overrides the spec's bound"
      (is (< (get-in (c/fit-rows heywood-rows (assoc spec1 :psi-floor 0.2) {:psi-floor 0.005}) [:psi "own"]) 0.2)))
    (testing "expanding keeps the spec's bound"
      (let [res (c/expanding heywood-rows [2005] (assoc spec1 :psi-floor 0.2))]
        (is (every? #(>= % (- 0.2 1e-12)) (vals (get-in res [:fits 2005 :psi]))))))))

(deftest equal-weight-composite
  (let [fit (c/fit-rows rows spec1)
        z (#'bnm.control/standardise (x-of rows "C30" 2000) (:stats fit) 4.0)]
    (testing "the mean of the signed z a row holds"
      (is (close? 1e-12 (/ (- (z "own") (z "open")) 2.0)
                  (first (:f (c/equal-score fit (select-keys (x-of rows "C30" 2000) ["own" "open"]) spec1))))))
    (testing "fewer than the axis's :min-k signed indicators, no score"
      (is (nil? (c/equal-score fit (select-keys (x-of rows "C30" 2000) ["own"]) (assoc-in spec1 [:axes 0 :min-k] 2)))))
    (testing "it orders countries as the state level does"
      (let [es (map #(first (:f (c/equal-score fit (x-of rows (countries %) 2000) spec1))) (range 60))]
        (is (> (corr es (map s-level (range 60))) 0.95))))
    (testing "expanding takes it as the scorer"
      (let [res (c/expanding rows [2005] spec1 {:scorer c/equal-score})
            s (first (filter #(= "C30" (:iso %)) (:scores res)))]
        (is (= 4 (:k s)))
        (is (close? 1e-12 (first (:f s))
                    (first (:f (c/equal-score (get-in res [:fits 2005]) (x-of rows "C30" 2004) spec1)))))))))

(deftest the-specs
  (testing "the repression gap is a state indicator, signed +; the stock spec bounds uniqueness at 0.2"
    (is (= 1 (get-in c/spec-1 [:axes 0 :signs "repression"])))
    (is (some #{"repression"} (:indicators c/spec-1)))
    (is (= 0.2 (:psi-floor c/spec-1) (:psi-floor c/spec-2))))
  (testing "without drops an indicator from the indicators and the signs"
    (let [s (c/without c/spec-1 "state-own")]
      (is (not (some #{"state-own"} (:indicators s))))
      (is (nil? (get-in s [:axes 0 :signs "state-own"])))
      (is (= 0.2 (:psi-floor s))))))
