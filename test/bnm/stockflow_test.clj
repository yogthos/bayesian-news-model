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
                        (sf/delay-n :out :in 4.0 n {:init 0.0}))
          r (sf/run m {:from 0.0 :to 60.0 :dt 0.01})]
      (is (close? 10.0 (last (get-in r [:stocks :sink])) 0.05) (str "order " n ": all of it arrives"))
      (is (= 0.0 (first (get-in r [:aux :out]))) "started in equilibrium with an input of 0")
      (is (< (sf/imbalance r) 1e-6))
      (testing "the mean delay is tau"
        (let [ts (:t r) outs (get-in r [:aux :out])
              mass (reduce + outs)]
          (is (close? 4.5 (/ (reduce + (map * ts outs)) mass) 0.1) (str "order " n)))))))

(deftest a-third-order-delay-is-s-shaped
  (let [step-resp (fn [n] (let [m (sf/combine (sf/model {:stocks {} :aux {:in (fn [v] (if (pos? (v :t)) 1.0 0.0))}}) (sf/delay-n :out :in 3.0 n))]
                            (get-in (sf/run m {:from 0.0 :to 9.0 :dt 0.01}) [:aux :out])))
        d1 (step-resp 1) d3 (step-resp 3)]
    (is (close? (- 1.0 (Math/exp -1.0)) (nth d1 300) 0.01) "DELAY1: 1 - e^(-t/tau)")
    (is (< (nth d3 50) (nth d1 50)) "slower to start")
    (is (< (- (nth d3 60) (nth d3 30)) (- (nth d3 210) (nth d3 180))) "then steeper")))

(deftest perception-and-trend
  ;; SMOOTH: dP/dt = (X - P)/tau, starting at the input; TREND: the smoothed
  ;; value's growth against its own smoothed past, from the input's level
  (let [g 0.05
        m (sf/combine (sf/model {:aux {:x (fn [v] (* 100.0 (Math/exp (* g (v :t)))))}})
                      (sf/smooth :p :x 2.0)
                      (sf/trend :gr :x 1.0 5.0 g))
        r (sf/run m {:from 0.0 :to 80.0 :dt 0.01})
        xs (get-in r [:aux :x]) ps (get-in r [:stocks :p])]
    (is (close? 1.0 (/ (first xs) (first ps)) 1e-12) "the perception starts at the input")
    (is (close? (+ 1.0 (* g 2.0)) (/ (last xs) (last ps)) 0.01) "then lags it by 1 + g tau")
    (is (every? #(< 0.04 % 0.06) (get-in r [:aux :gr])) "the trend is the growth rate throughout")))

(deftest smoothing-of-order-n
  ;; SMOOTH3 (DLINF3): three first-order smoothings in series, from the input
  (let [m (sf/combine (sf/model {:aux {:x (fn [v] (if (< (v :t) 1.0) 0.0 1.0))}}) (sf/smooth-n :p :x 3.0 3))
        r (sf/run m {:from 0.0 :to 30.0 :dt 0.01})
        p (get-in r [:aux :p])]
    (is (= 0.0 (first p)))
    (is (< (nth p 150) 0.1) "slow to start")
    (is (close? 1.0 (last p) 1e-3))))

(deftest a-stock-that-cannot-go-negative
  ;; whatever drains it, an outflow or a negative inflow, takes at most what
  ;; is there; the other end gets what was taken; and a flow or auxiliary
  ;; reading a limited flow sees what moved
  (let [m (sf/model {:stocks {:s {:init 1.0 :nonneg true} :d {:init 0.0}}
                     :flows {:out {:from :s :to :d :rate (fn [_] 5.0)}}
                     :aux {:seen (fn [v] (v :out))}})
        r (sf/run m {:from 0.0 :to 2.0 :dt 0.5})]
    (is (every? #(>= % 0.0) (get-in r [:stocks :s])))
    (is (= [2.0 0.0 0.0 0.0 0.0] (get-in r [:flows :out])))
    (is (= (get-in r [:flows :out]) (get-in r [:aux :seen])))
    (is (= 1.0 (last (get-in r [:stocks :d]))) "the destination got what left"))
  (doseq [m [(sf/model {:stocks {:s {:init 1.0 :nonneg true}} :flows {:f {:to :s :rate (fn [_] -5.0)}}})
             (sf/model {:stocks {:a {:init 0.0} :s {:init 1.0 :nonneg true}} :flows {:f {:from :a :to :s :rate (fn [_] -5.0)}}})]]
    (is (every? #(>= % 0.0) (get-in (sf/run m {:from 0.0 :to 2.0 :dt 0.5}) [:stocks :s])) "a negative inflow drains it")))

(deftest initial-values-from-the-model
  ;; a stock's :init may be a function of the model at the start: an
  ;; equilibrium (a delay started full); :params are looked up like any name
  (let [m (sf/model {:params {:k 0.5 :inflow 2.0}
                     :stocks {:s {:init (fn [v] (/ (v :inflow) (v :k)))}}
                     :flows {:in {:to :s :rate (fn [v] (v :inflow))}
                             :out {:from :s :rate (fn [v] (* (v :k) (v :s)))}}})
        r (sf/run m {:from 0.0 :to 10.0 :dt 0.1})]
    (is (every? #(close? 4.0 % 1e-9) (get-in r [:stocks :s])) "in equilibrium from the start")
    (is (every? #(close? 6.0 % 1e-9) (get-in (sf/run m {:from 0.0 :to 1.0 :dt 0.1 :params {:inflow 3.0}}) [:stocks :s]))
        "a run may set the parameters"))
  (testing "a delay starts in equilibrium with its input, its time a name"
    (let [m (sf/combine (sf/model {:params {:tau 4.0} :aux {:in (fn [_] 2.0)}}) (sf/delay-n :out :in :tau 3))
          r (sf/run m {:from 0.0 :to 5.0 :dt 0.1})]
      (is (every? #(close? 2.0 % 1e-9) (get-in r [:aux :out]))))))

(deftest a-model-is-checked
  (is (thrown? clojure.lang.ExceptionInfo (sf/model {:stocks {:a {:init 0.0}} :flows {:f {:to :aa :rate (fn [_] 1.0)}}}))
      "a flow into a stock that is not there")
  (is (thrown? clojure.lang.ExceptionInfo (sf/model {:stocks {:x {:init 0.0}} :aux {:x (fn [_] 1.0)}})) "a name twice")
  (is (thrown? clojure.lang.ExceptionInfo (sf/model {:aux {:t (fn [_] 1.0)}})) ":t is time")
  (is (thrown? clojure.lang.ExceptionInfo (sf/model {:stocks {:a {:init 0.0} :p {:init 0.0 :info true}}
                                                     :flows {:f {:from :a :to :p :rate (fn [_] 1.0)}}}))
      "material into a perception")
  (is (thrown? clojure.lang.ExceptionInfo (sf/delay-n :o :i 1.0 0)))
  (doseq [o [{:from 0.0 :to 1.0 :dt 0.0} {:from 1.0 :to 0.0 :dt 0.1} {:from 0.0 :to 1.0 :dt 0.4}]]
    (is (thrown? clojure.lang.ExceptionInfo (sf/run (sf/model {}) o)) (str o)))
  (testing "tables"
    (is (= 2.0 (sf/table 0.5 [2.0] [0.0 1.0])))
    (is (thrown? Exception (sf/table 0.5 [] [0.0 1.0]))))
  (testing "a failed lookup caught is not taken for a cycle"
    (let [m (sf/model {:aux {:a (fn [v] (try (v :nope) (catch Exception _ (try (v :nope) (catch Exception e (ex-message e))) 1.0)))}})]
      (is (= [1.0 1.0] (get-in (sf/run m {:from 0.0 :to 1.0 :dt 1.0}) [:aux :a]))))))

(deftest units-agree
  ;; a flow is its stocks' unit per unit of time, at both ends
  (let [u (fn [fu tu] (sf/unit-errors (sf/model {:stocks {:s {:init 0.0 :unit "t"} :d {:init 0.0 :unit tu}}
                                                :flows {:f {:from :s :to :d :unit fu :rate (fn [_] 1.0)}}}) "y"))]
    (is (empty? (u "t/y" "t")))
    (is (empty? (u "t / y" "t")) "spacing")
    (is (= [:f] (u "$/y" "t")))
    (is (= [:f] (u "t/y" "$")) "the other end")
    (is (= [:f] (u nil "t")) "a unit missing")))

(deftest a-delay-into-a-stock-and-a-look-at-the-start
  ;; a delay's output may feed a stock (material accounted for); `at` is the
  ;; model at its start
  (let [m (sf/combine (sf/model {:stocks {:pool {:init 0.0}} :aux {:in (fn [_] 3.0)}})
                      (sf/delay-n :arrive :in 2.0 3 {:to :pool}))
        r (sf/run m {:from 0.0 :to 4.0 :dt 0.5})]
    (is (close? 12.0 (last (get-in r [:stocks :pool])) 1e-9) "in equilibrium: 3 a year for 4 years")
    (is (< (sf/imbalance r) 1e-9))
    (is (= 3.0 (get-in (sf/at m 0.0) [:flows :arrive])))))
