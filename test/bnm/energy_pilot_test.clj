(ns bnm.energy-pilot-test
  (:require [augur.math :as m]
            [bnm.energy-pilot :as ep]
            [clojure.test :refer [deftest is testing]]))

(deftest the-leontief-inverse
  ;; L = (I - A)^-1 by its Neumann series; for A = [[0.1 0.2] [0.3 0.4]]
  ;; (a_rs: r's share of s's output), (I - A)^-1 = [[1.2 0.4] [0.6 1.8]]/0.48... computed exactly
  (let [a {["r" "r"] 0.1 ["r" "s"] 0.2 ["s" "r"] 0.3 ["s" "s"] 0.4}
        l (ep/leontief a ["r" "s"])
        det (- (* 0.9 0.6) (* 0.2 0.3))]
    (is (< (Math/abs (- (l ["r" "r"]) (/ 0.6 det))) 1e-9))
    (is (< (Math/abs (- (l ["r" "s"]) (/ 0.2 det))) 1e-9))
    (is (< (Math/abs (- (l ["s" "s"]) (/ 0.9 det))) 1e-9))))

(deftest the-energy-shock
  ;; refining's log price change less the output-weighted mean's
  (let [pc {"REF" 1.5 "AGR" 1.0 "BUS" 1.0}
        go {"REF" 10.0 "AGR" 45.0 "BUS" 45.0}
        mean (+ (* 0.1 1.5) (* 0.9 1.0))]
    (is (< (Math/abs (- (ep/shock pc go) (- (Math/log 1.5) (Math/log mean)))) 1e-12))))

(deftest least-squares-without-intercept
  (let [g (m/rng 1)
        rows (vec (for [_ (range 500)] (let [x1 (m/normal! g) x2 (m/normal! g)] {:x [x1 x2] :y (+ (* 2.0 x1) (* -1.0 x2) (* 0.1 (m/normal! g)))})))
        b (ep/ols rows)]
    (is (< (Math/abs (- 2.0 (first b))) 0.02))
    (is (< (Math/abs (+ 1.0 (second b))) 0.02))))

(deftest a-world-where-the-supply-chain-matters
  ;; synthetic sectors whose output falls with their energy exposure times
  ;; the shock: the network model finds it, the trend cannot
  (let [g (m/rng 2)
        rows (vec (for [c (range 20) s (range 10) t (range 1970 2010)
                        :let [a (* 0.02 (inc s)) ell (* 1.5 a) e (if (#{1974 1979 1990 2008} t) 0.4 (* 0.05 (m/normal! g)))]
                        h [0]]
                    {:c (str "C" c) :s (str "S" s) :t t :h h :a a :ell ell :e e :lag (* 0.02 (m/normal! g))
                     :g (+ 0.02 (* -1.0 ell e) (* 0.02 (m/normal! g)))}))
        r (ep/evaluate rows [1974 1979 1990 2008])]
    (is (< (get-in r [:mse :network]) (get-in r [:mse :trend])))
    (is (< (get-in r [:mse :network]) (* 0.6 (get-in r [:mse :trend]))))))
