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
