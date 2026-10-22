(ns bnm.stockflow-test
  (:require [bnm.stockflow :as sf]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

(deftest table-functions-as-dynamo-s
  ;; TABHL: linear between points over an even grid, flat beyond the ends
  ;; (the cases WorldDynamics.jl tests)
  (let [f #(sf/table % [1.0 2.0 3.0] [-1.0 1.0])]
    (is (= 1.0 (f -1.1))) (is (= 1.0 (f -1.0))) (is (= 1.5 (f -0.5)))
    (is (= 2.0 (f 0.0))) (is (= 2.5 (f 0.5))) (is (= 3.0 (f 1.0))) (is (= 3.0 (f 1.1))))
  (testing "clip, step, switch"
    (is (= -1.0 (sf/clip 1.0 -1.0 -1.0 0.0))) (is (= 1.0 (sf/clip 1.0 -1.0 0.0 0.0)))
    (is (= 0.0 (sf/step -1.0 1.0 0.0))) (is (= 1.0 (sf/step 0.0 1.0 0.0)))
    (is (= 1.0 (sf/switch 1.0 -1.0 0.0))) (is (= -1.0 (sf/switch 1.0 -1.0 0.1)))))

(deftest a-stock-integrates-its-flows
  (let [m (sf/model {:stocks {:s {:init 10.0}}
                     :flows {:in {:to :s :rate (fn [_] 2.0)}
                             :out {:from :s :rate (fn [v] (* 0.1 (v :s)))}}})
        r (sf/run m {:from 0.0 :to 10.0 :dt 0.01})]
    (testing "dS/dt = 2 - 0.1 S: toward 20 as 20 - 10 e^(-0.1 t)"
      (is (close? (last (get-in r [:stocks :s])) (- 20.0 (* 10.0 (Math/exp -1.0))) 0.02)))
    (is (= 1001 (count (:t r))))
    (testing "the accounts close: what is in the stocks is what came in less what left"
      (is (< (sf/imbalance r) 1e-9)))))

(deftest flows-between-stocks-conserve
  (let [m (sf/model {:stocks {:a {:init 100.0} :b {:init 0.0}}
                     :flows {:ab {:from :a :to :b :rate (fn [v] (* 0.3 (v :a)))}}})
        r (sf/run m {:from 0.0 :to 5.0 :dt 0.1})]
    (is (every? #(close? 100.0 % 1e-9) (map + (get-in r [:stocks :a]) (get-in r [:stocks :b]))))))

(deftest auxiliaries-resolve-in-any-order
  (let [m (sf/model {:stocks {:k {:init 3.0}}
                     :aux {:y (fn [v] (/ (v :x) 2.0)) :x (fn [v] (* (v :k) (v :p))) :p (fn [_] 4.0)}
                     :flows {:g {:to :k :rate (fn [v] (v :y))}}})
        r (sf/run m {:from 0.0 :to 1.0 :dt 1.0})]
    (is (= [12.0 36.0] (get-in r [:aux :x])) "x = k p: 3 x 4, then 9 x 4")
    (is (= 9.0 (last (get-in r [:stocks :k]))))
    (testing "a cycle among auxiliaries is an error"
      (is (thrown? clojure.lang.ExceptionInfo
                   (sf/run (sf/model {:stocks {} :aux {:a (fn [v] (v :b)) :b (fn [v] (v :a))}}) {:from 0.0 :to 1.0 :dt 1.0}))))
    (testing "time is :t"
      (is (= [0.0 1.0] (get-in (sf/run (sf/model {:stocks {} :aux {:now (fn [v] (v :t))}}) {:from 0.0 :to 1.0 :dt 1.0}) [:aux :now]))))))

(deftest material-delays
  ;; DELAYN: n stocks in series, each draining at L/(tau/n); what goes in
  ;; comes out, later
  (doseq [n [1 3]]
    (let [m (sf/combine (sf/model {:stocks {:sink {:init 0.0}}
                                   :aux {:in (fn [v] (if (< (v :t) 1.0) 10.0 0.0))}
                                   :flows {:arrive {:to :sink :rate (fn [v] (v :out))}}})
                        (sf/delay-n :out :in 4.0 n))
          r (sf/run m {:from 0.0 :to 60.0 :dt 0.01})]
      (is (close? 10.0 (last (get-in r [:stocks :sink])) 0.05) (str "order " n ": all of it arrives"))
      (is (< (sf/imbalance r) 1e-6))
      (testing "the mean delay is tau"
        (let [ts (:t r) outs (get-in r [:aux :out])
              mass (reduce + outs)]
          (is (close? 4.5 (/ (reduce + (map * ts outs)) mass) 0.1) (str "order " n)))))))

(deftest a-third-order-delay-is-s-shaped
  (let [step-resp (fn [n] (let [m (sf/combine (sf/model {:stocks {} :aux {:in (fn [_] 1.0)}}) (sf/delay-n :out :in 3.0 n))]
                            (get-in (sf/run m {:from 0.0 :to 9.0 :dt 0.01}) [:aux :out])))
        d1 (step-resp 1) d3 (step-resp 3)]
    (is (close? (- 1.0 (Math/exp -1.0)) (nth d1 300) 0.01) "DELAY1: 1 - e^(-t/tau)")
    (is (< (nth d3 50) (nth d1 50)) "slower to start")
    (is (< (- (nth d3 60) (nth d3 30)) (- (nth d3 210) (nth d3 180))) "then steeper")))

(deftest perception-and-trend
  ;; SMOOTH: dP/dt = (X - P)/tau, nothing conserved; TREND: the smoothed
  ;; value's growth against its own smoothed past
  (let [m (sf/combine (sf/model {:stocks {} :aux {:x (fn [v] (Math/exp (* 0.05 (v :t))))}})
                      (sf/smooth :p :x 2.0 1.0)
                      (sf/trend :g :x 1.0 5.0 0.05))
        r (sf/run m {:from 0.0 :to 80.0 :dt 0.01})]
    (is (close? (last (get-in r [:aux :x])) (* (last (get-in r [:stocks :p])) 1.105) 0.5) "lags its input")
    (is (close? 0.05 (last (get-in r [:aux :g])) 0.01) "the trend finds the growth rate")))

(deftest a-stock-that-cannot-go-negative
  ;; an outflow larger than what is there takes only what is there
  (let [m (sf/model {:stocks {:s {:init 1.0 :nonneg true}}
                     :flows {:out {:from :s :rate (fn [_] 5.0)}}})
        r (sf/run m {:from 0.0 :to 2.0 :dt 0.5})]
    (is (every? #(>= % 0.0) (get-in r [:stocks :s])))
    (is (< (sf/imbalance r) 1e-9))))

(deftest units-agree
  ;; a flow is its stocks' unit per unit of time
  (is (empty? (sf/unit-errors (sf/model {:stocks {:s {:init 0.0 :unit "t"}}
                                        :flows {:f {:to :s :unit "t/y" :rate (fn [_] 1.0)}}}) "y")))
  (is (= [:f] (sf/unit-errors (sf/model {:stocks {:s {:init 0.0 :unit "t"}}
                                        :flows {:f {:to :s :unit "$/y" :rate (fn [_] 1.0)}}}) "y"))))
