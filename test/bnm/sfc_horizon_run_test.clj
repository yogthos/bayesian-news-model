(ns bnm.sfc-horizon-run-test
  (:require [bnm.sfc-horizon-run :as h]
            [clojure.test :refer [deftest is testing]]))

(deftest the-registered-verdicts
  (let [b {:nl 0.15 :debt 0.27}]
    (is (= :pass (h/verdict {:nl [0.2 0.05 0.3] :debt [0.3 0.01 0.5]} b)))
    (is (= :worse (h/verdict {:nl [-1.8 -3.5 -0.8] :debt [0.3 0.24 0.33]} b)) "one outcome below 0 is enough")
    (is (= :equivalent (h/verdict {:nl [-0.4 -1.6 0.14] :debt [-0.06 -0.17 0.04]} b)))
    (is (= :inconclusive (h/verdict {:nl [-0.4 -1.6 0.14] :debt [0.1 -0.05 0.3]} b)) "an upper end over its bound")
    (testing "a pass on one outcome alone is not a pass"
      (is (= :equivalent (h/verdict {:nl [0.0 -0.1 0.1] :debt [0.29 0.24 0.26]} b))))))

(deftest damped-reversion-moves-a-fifth-a-year-to-the-five-year-mean
  (let [panel {"X" (into {} (for [[y nl] {2000 0.0 2001 0.01 2002 0.02 2003 0.03 2004 0.09}]
                              [y {:flows-ok true :y 100.0 :f {:hh {:nl (* 100.0 nl)}}}]))}
        m (/ (+ 0.0 0.01 0.02 0.03 0.09) 5)]
    (is (< (Math/abs (- (+ m (* (Math/pow 0.8 3) (- 0.09 m))) (h/damped panel "X" [:nl :hh] 2004 3))) 1e-12))
    (is (nil? (h/damped panel "X" [:nl :hh] 2001 3)) "fewer than three years")))

(deftest skill-averages-the-seeds-and-the-bootstrap-resamples-countries
  (let [cs {"A" {:x 4.0 [:m 1] 1.0 [:m 2] 3.0} "B" {:x 4.0 [:m 1] 1.0 [:m 2] 3.0}}]
    (is (< (Math/abs (- (/ (+ 0.75 0.25) 2) (h/skill-of cs ["A" "B"] #{[:m 1] [:m 2]} :x))) 1e-12)))
  (let [rows (for [iso ["A" "B" "C"] i (range 4)] {:iso iso :data 0.0 :f {:x 2.0 :m 1.0}})
        [p lo hi] (h/bootstrap rows :m :x 200 1)]
    (is (= [0.75 0.75 0.75] [p lo hi]) "every draw the same skill when every country has it")))

(deftest the-hyperinflation-rule
  (let [mk (fn [ys] {"X" (into {} (for [[y v] ys] [y {:flows-ok true :b {} :stocks-source :oecd-t720 :y v}]))})]
    (is (h/hyperinflation? (mk {1996 1.0 1997 2.1}) "X"))
    (is (not (h/hyperinflation? (mk {1996 1.0 1997 1.9}) "X")))))
