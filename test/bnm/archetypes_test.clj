(ns bnm.archetypes-test
  (:require [bnm.archetypes :as ar]
            [bnm.world3]
            [bnm.stockflow :as sf]
            [clojure.test :refer [deftest is testing]]))

(def limits
  ;; growth and limits: population feeds births (reinforcing), crowding
  ;; feeds deaths (balancing); a resource drained by consumption
  (sf/model {:params {:b 0.05 :k 100.0}
             :stocks {:pop {:init 10.0} :res {:init 1000.0}}
             :flows {:births {:to :pop :rate (fn [v] (* (v :b) (v :pop)))}
                     :deaths {:from :pop :rate (fn [v] (* (v :b) (v :pop) (/ (v :pop) (v :k))))}
                     :use {:from :res :rate (fn [v] (* 0.01 (v :pop)))}}}))

(deftest the-jacobian-of-the-stocks
  ;; d(dS_i/dt)/dS_j by finite differences at a state: at pop 10, k 100,
  ;; dpop/dt = b pop (1 - pop/k), its derivative b (1 - 2 pop/k) = 0.04
  (let [j (ar/jacobian limits 0.0 {:pop 10.0 :res 1000.0})]
    (is (< (Math/abs (- 0.04 (j [:pop :pop]))) 1e-6))
    (is (< (Math/abs (+ 0.01 (j [:pop :res]))) 1e-6) "pop drains the resource")
    (is (< (Math/abs (j [:res :pop])) 1e-9) "the resource does not feed back")))

(deftest loops-and-their-polarity
  ;; past half the limit the self-loop on pop turns from reinforcing to
  ;; balancing: loop dominance shifts
  (let [early (ar/loops (ar/jacobian limits 0.0 {:pop 10.0 :res 1000.0}))
        late (ar/loops (ar/jacobian limits 0.0 {:pop 80.0 :res 1000.0}))]
    (is (= :reinforcing (:polarity (first (filter #(= [:pop] (:stocks %)) early)))))
    (is (= :balancing (:polarity (first (filter #(= [:pop] (:stocks %)) late)))))))

(deftest archetypes-from-loops
  (testing "escalation: two stocks each raising the other, the loop reinforcing"
    (let [arms (sf/model {:stocks {:a {:init 1.0} :b {:init 1.0}}
                          :flows {:fa {:to :a :rate (fn [v] (- (* 1.1 (v :b)) (v :a)))}
                                  :fb {:to :b :rate (fn [v] (- (* 1.1 (v :a)) (v :b)))}}})
          ls (ar/loops (ar/flow-jacobian arms 0.0 {:a 1.0 :b 1.0}))]
      (is (some #{:escalation} (ar/archetypes ls)))))
  (testing "limits to growth: a stock with a reinforcing loop met by a balancing one"
    (let [ls (ar/loops (ar/flow-jacobian (sf/model {:params {} :stocks {:p {:init 1.0}}
                                               :flows {:g {:to :p :rate (fn [v] (* 0.1 (v :p)))}
                                                       :lim {:from :p :rate (fn [v] (* 0.001 (v :p) (v :p)))}}})
                                    0.0 {:p 1.0}))]
      (is (some #{:limits-to-growth} (ar/archetypes ls))))))

(deftest leverage-by-class
  ;; each parameter's elasticity on an outcome, grouped by Meadows' class
  (let [r (ar/leverage limits {:b :gain :k :buffer} {:from 0.0 :to 50.0 :dt 0.5} (fn [run] (last (get-in run [:stocks :pop]))))]
    (is (pos? (get-in r [:by-param :k])) "a larger limit, more people")
    (is (= #{:gain :buffer} (set (keys (:by-class r)))))))

(deftest world3-s-loops
  ;; read from World3 at 1900: the population's reinforcing loop (the young
  ;; mature into parents who bear the young) and its balancing ones
  ;; (deaths): limits to growth, found in the model rather than assumed
  (let [m (bnm.world3/model {})
        levels (:stocks (sf/at m 1900.0))
        pop-stocks #{:p1 :p2 :p3 :p4}
        ls (filter #(every? pop-stocks (:stocks %)) (ar/loops (ar/flow-jacobian m 1900.0 (select-keys levels (keys levels)))))]
    (is (some #(and (= :reinforcing (:polarity %)) (some #{:p1} (:stocks %)) (some #{:p2} (:stocks %))) ls))
    (is (some #(= :balancing (:polarity %)) ls))
    (is (some #{:limits-to-growth} (ar/archetypes ls)))))
