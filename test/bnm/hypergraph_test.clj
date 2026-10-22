(ns bnm.hypergraph-test
  (:require [augur.math :as m]
            [bnm.hypergraph :as hg]
            [clojure.test :refer [deftest is testing]]))

(deftest a-recipe-is-a-hyperedge
  ;; steel needs energy AND ore (critical, Leontief): output is capped by
  ;; the scarcer; cars draw on steel and plastics, substitutable in part
  (let [h (-> (hg/hypergraph)
              (hg/recipe :steel {:energy 0.4 :ore 0.6} :critical)
              (hg/recipe :cars {:steel 0.7 :plastics 0.3} :substitutable))
        avail (hg/availability h {:energy 0.5 :ore 1.0 :plastics 1.0})]
    (is (= 0.5 (avail :steel)) "half the energy, half the steel, whatever the ore")
    (is (< (Math/abs (- (avail :cars) (+ (* 0.7 0.5) (* 0.3 1.0)))) 1e-12) "the shortfall passed on in proportion")
    (is (= 1.0 (avail :ore)) "a primary input as given")))

(deftest joint-crises-beyond-the-pairs
  ;; the triple against what its pairs imply with no three-way term: about
  ;; one when the kinds are independent, or associated only in pairs; above
  ;; one when the third follows only when the first two meet (synergy)
  (let [g (m/rng 1)
        draw (fn [p] (< (m/next-double! g) p))
        indep (vec (for [_ (range 40000)] (set (filter (fn [_] (draw 0.2)) [:a :b :c]))))
        synergy (vec (for [_ (range 40000)]
                       (let [a (draw 0.3) b (draw 0.3) c (if (and a b) (draw 0.9) (draw 0.05))]
                         (cond-> #{} a (conj :a) b (conj :b) c (conj :c)))))]
    (is (< (Math/abs (- 1.0 (:lift (hg/lift indep [:a :b :c])))) 0.15) "independent: about one")
    (is (> (:lift (hg/lift synergy [:a :b :c])) 1.2) "the third only when the first two meet (the pairs absorb part of it)")))

(deftest the-fit-keeps-every-pair
  (let [t {[1 1 1] 30 [1 1 0] 10 [1 0 1] 5 [0 1 1] 7 [1 0 0] 20 [0 1 0] 25 [0 0 1] 12 [0 0 0] 91}
        e (hg/no-three-way t)
        m (fn [x f v] (reduce + (for [[c n] x :when (= (f c) v)] n)))]
    (doseq [f [(fn [[a b _]] [a b]) (fn [[_ b c]] [b c]) (fn [[a _ c]] [a c])] v [[1 1] [0 1] [1 0] [0 0]]]
      (is (< (Math/abs (- (m t f v) (m e f v))) 1e-6)))))

(deftest simplices-of-a-place
  ;; the kinds with an onset at a place within a window: its simplex
  (is (= #{"bank" "currency"} (hg/simplex {"bank" #{1997} "currency" #{1998} "coup" #{2005}} 1997 3)))
  (is (= #{} (hg/simplex {"bank" #{1990}} 1997 3))))
