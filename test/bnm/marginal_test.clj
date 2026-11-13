(ns bnm.marginal-test
  (:require [augur.hier :as hier]
            [augur.math :as m]
            [bnm.marginal :as mg]
            [clojure.test :refer [deftest is testing]]))

(defn- random-case
  "A random vector-marginal problem: d dimensions, n logistic rows with
  loads [1 x ..], a correlated prior, sometimes far from the mode and
  sometimes with a dimension known exactly; some rows with :q or :w."
  [g d n]
  (let [L (vec (for [i (range d)] (vec (for [j (range d)] (if (<= j i) (* (if (= i j) 1.0 0.3) (m/normal! g)) 0.0)))))
        v (vec (for [i (range d)] (vec (for [j (range d)] (reduce + 0.0 (map * (L i) (L j)))))))
        v (if (and (> d 1) (< (m/next-double! g) 0.3))
            (vec (for [i (range d)] (vec (for [j (range d)] (if (or (= i 1) (= j 1)) 0.0 (get-in v [i j]))))))
            v)
        far (if (< (m/next-double! g) 0.3) 6.0 1.0)
        mu (vec (repeatedly d #(* far (m/normal! g))))]
    [(vec (for [_ (range n)]
            (cond-> {:f (* 2.0 (m/normal! g))
                     :load (vec (cons 1.0 (repeatedly (dec d) #(m/normal! g))))
                     :y (< (m/next-double! g) 0.3)}
              (< (m/next-double! g) 0.5) (assoc :q (* 0.5 (m/next-double! g)))
              (< (m/next-double! g) 0.3) (assoc :w (+ 0.5 (m/next-double! g))))))
     mu v]))

(deftest bnm-2oc-the-array-marginal-is-augur-s-to-the-last-bit
  (let [g (m/rng 7)]
    (doseq [d [1 2 3 6 9] n [1 5 40 150] _ (range 4)]
      (let [[rows mu v] (random-case g d n)]
        (is (= (hier/shared-marginal :logistic rows mu v) (mg/shared-marginal-n rows mu v))
            (pr-str [d n]))))
    (testing "the planted region world's rows: centred loads on six dimensions"
      (let [rows (vec (for [i (range 100)]
                        {:f -1.5 :q 0.05 :y (zero? (mod i 4))
                         :load (vec (cons 1.0 (for [gi (range 5)] (- (if (= gi (mod i 5)) 1.0 0.0) 0.2))))}))
            mu [0.3 -0.2 0.1 0.0 0.4 -0.3]
            v (vec (for [i (range 6)] (vec (for [j (range 6)] (if (= i j) (if (zero? i) 1.0 0.25) 0.0)))))]
        (is (= (hier/shared-marginal :logistic rows mu v) (mg/shared-marginal-n rows mu v)))))))
