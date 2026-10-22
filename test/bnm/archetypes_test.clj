(ns bnm.archetypes-test
  (:require [bnm.archetypes :as ar]
            [bnm.world3]
            [bnm.stockflow :as sf]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

(def limits
  ;; growth and limits: population feeds births (reinforcing), crowding
  ;; feeds deaths (balancing); a resource drained by consumption
  (sf/model {:params {:b 0.05 :k 100.0}
             :stocks {:pop {:init 10.0} :res {:init 1000.0}}
             :flows {:births {:to :pop :rate (fn [v] (* (v :b) (v :pop)))}
                     :deaths {:from :pop :rate (fn [v] (* (v :b) (v :pop) (/ (v :pop) (v :k))))}
                     :use {:from :res :rate (fn [v] (* 0.01 (v :pop)))}}}))

(defn- found [m levels] (set (map :archetype (ar/archetypes (ar/structure m 0.0 levels)))))

(deftest the-jacobian-of-the-stocks
  ;; {[i j] d(dS_j/dt)/dS_i} by central differences at a state: at pop 10,
  ;; k 100, dpop/dt = b pop (1 - pop/k), its derivative b (1 - 2 pop/k) = 0.04
  (let [j (ar/jacobian limits 0.0 {:pop 10.0 :res 1000.0})]
    (is (close? 0.04 (j [:pop :pop]) 1e-6))
    (is (close? -0.01 (j [:pop :res]) 1e-6) "pop drains the resource")
    (is (nil? (j [:res :pop])) "the resource does not feed back: no edge")))

(deftest the-jacobian-at-a-state
  (testing "a non-negative stock's clamp does not bind: the rate's own slope"
    (let [m (sf/model {:stocks {:a {:init 1.0 :nonneg true}}
                       :flows {:out {:from :a :rate (fn [v] (* 5.0 (v :a)))}}})]
      (is (close? -5.0 ((ar/jacobian m 0.0 {:a 1.0}) [:a :a]) 1e-6))
      (is (close? -5.0 ((ar/jacobian m 0.0 {:a 0.0}) [:a :a]) 1e-6) "one-sided at the bound")))
  (testing "parameters override the model's"
    (is (close? 0.16 ((ar/jacobian limits 0.0 {:pop 10.0 :res 1000.0} {:params {:b 0.2}}) [:pop :pop]) 1e-6)))
  (testing "stocks not given are held at the unperturbed state"
    (let [m (sf/model {:stocks {:a {:init 1.0} :b {:init (fn [v] (* 2.0 (v :a)))}}
                       :flows {:fb {:to :b :rate (fn [v] (- (v :a) (v :b)))}}})
          j (ar/jacobian m 0.0 {:a 1.0})]
      (is (close? 1.0 (j [:a :b]) 1e-6) "b's init does not move with a")
      (is (close? -1.0 (j [:b :b]) 1e-6))))
  (testing "edges are thresholded by their size relative to the flow, not in absolute units"
    (let [m (sf/model {:stocks {:big {:init 1e12} :x {:init 1.0}}
                       :flows {:f {:to :x :rate (fn [v] (* 1e-15 (v :big)))}}})]
      (is (close? 1e-15 ((ar/flow-jacobian m 0.0 {:big 1e12 :x 1.0}) [:big :x :f]) 1e-20)
          "a sensitivity of 1e-15 that carries the whole flow is kept"))))

(deftest flow-jacobian-signs
  ;; a transfer A -> B that depends on A: out of A (-), into B (+)
  (let [m (sf/model {:stocks {:a {:init 1.0} :b {:init 1.0}}
                     :flows {:ab {:from :a :to :b :rate (fn [v] (* 0.3 (v :a)))}}})
        j (ar/flow-jacobian m 0.0 {:a 1.0 :b 1.0})]
    (is (close? -0.3 (j [:a :a :ab]) 1e-6))
    (is (close? 0.3 (j [:a :b :ab]) 1e-6))
    (is (= 2 (count j)))))

(deftest loop-enumeration
  (let [tri (sf/model {:stocks {:x {:init 1.0} :y {:init 1.0} :z {:init 1.0} :w {:init 1.0} :u {:init 1.0}}
                       :flows {:fy {:to :y :rate (fn [v] (v :x))}
                               :fy2 {:to :y :rate (fn [v] (* 2 (v :x)))}
                               :fz {:to :z :rate (fn [v] (v :y))}
                               :fx {:to :x :rate (fn [v] (v :z))}
                               ;; a 5-cycle x -> y -> z -> w -> u -> x through other flows
                               :fw {:to :w :rate (fn [v] (v :z))}
                               :fu {:to :u :rate (fn [v] (v :w))}
                               :fx2 {:to :x :rate (fn [v] (v :u))}}})
        j (ar/flow-jacobian tri 0.0 {:x 1.0 :y 1.0 :z 1.0 :w 1.0 :u 1.0})
        {:keys [loops truncated]} (ar/loops j)]
    (testing "each cycle once, parallel flows as separate loops"
      (is (= #{[:fy :fz :fx] [:fy2 :fz :fx]} (set (map :flows loops))))
      (is (every? #(= [:x :y :z] (:stocks %)) loops)))
    (testing "the length cap is reported"
      (is (true? truncated))
      (is (= 4 (count (:loops (ar/loops j {:max-length 5})))) "the 5-cycles found when allowed")
      (is (false? (:truncated (ar/loops j {:max-length 5}))))))
  (testing "a delay chain collapses into one node, its loops kept"
    (let [m (sf/combine (sf/model {:stocks {:s {:init 1.0}}
                                   :flows {:drain {:from :s :rate (fn [v] (* 0.1 (v :s)))}}
                                   :aux {:gen (fn [v] (* 0.2 (v :s)))}})
                        (sf/delay-n :d :gen 3.0 3 {:to :s}))
          lv (:stocks (sf/at m 0.0))
          {:keys [loops groups]} (ar/loops (ar/flow-jacobian m 0.0 lv))
          through (first (filter #(= #{:s :d} (set (:stocks %))) loops))]
      (is (= {:d [:d-1 :d-2 :d-3]} groups))
      (is (some? through) "s -> delay -> s is a loop of two nodes")
      (is (= :reinforcing (:polarity through)))
      (is (= 4 (count (:edges through))) "its edges are the model's, through each stage")
      (is (not-any? #(some #{:d-1 :d-2 :d-3} (:stocks %)) loops) "the stages' own loops are internal"))))

(deftest nothing-where-there-is-nothing
  ;; the archetypes must not fire on structures that are not them
  (testing "exponential growth"
    (is (empty? (found (sf/model {:stocks {:p {:init 1.0}} :flows {:b {:to :p :rate (fn [v] (* 0.03 (v :p)))}}}) {:p 1.0}))))
  (testing "linear decay"
    (is (empty? (found (sf/model {:stocks {:p {:init 1.0}} :flows {:d {:from :p :rate (fn [v] (* 0.1 (v :p)))}}}) {:p 1.0}))))
  (testing "linear births and deaths"
    (is (empty? (found (sf/model {:stocks {:p {:init 1.0}}
                                  :flows {:b {:to :p :rate (fn [v] (* 0.03 (v :p)))}
                                          :d {:from :p :rate (fn [v] (* 0.01 (v :p)))}}}) {:p 1.0}))))
  (testing "an aging chain"
    (is (empty? (found (sf/model {:stocks {:a {:init 1.0} :b {:init 1.0} :c {:init 1.0}}
                                  :flows {:in {:to :a :rate (fn [_] 1.0)}
                                          :ab {:from :a :to :b :rate (fn [v] (* 0.1 (v :a)))}
                                          :bc {:from :b :to :c :rate (fn [v] (* 0.1 (v :b)))}
                                          :out {:from :c :rate (fn [v] (* 0.1 (v :c)))}}})
                       {:a 1.0 :b 1.0 :c 1.0}))))
  (testing "diffusion between two stocks"
    (is (empty? (found (sf/model {:stocks {:a {:init 1.0} :b {:init 1.0}}
                                  :flows {:ab {:from :a :to :b :rate (fn [v] (* 0.1 (v :a)))}
                                          :ba {:from :b :to :a :rate (fn [v] (* 0.1 (v :b)))}}})
                       {:a 1.0 :b 1.0})))))

(deftest archetypes-from-loops
  (testing "escalation: each actor's stock set against the other's, its own balancing loop"
    (let [arms (sf/model {:stocks {:a {:init 1.0} :b {:init 1.0}}
                          :flows {:fa {:to :a :rate (fn [v] (- (* 1.1 (v :b)) (v :a)))}
                                  :fb {:to :b :rate (fn [v] (- (* 1.1 (v :a)) (v :b)))}}})
          es (ar/archetypes (ar/structure arms 0.0 {:a 1.0 :b 1.0}))
          e (first (filter #(= :escalation (:archetype %)) es))]
      (is (= #{:escalation} (set (map :archetype es))))
      (is (= #{:a :b} (set (:stocks e))) "the evidence names the stocks")
      (is (some #(= :reinforcing (:polarity %)) (:loops e)) "and the loops")))
  (testing "limits to growth: the balancing loop strengthens as the stock grows"
    (let [m (sf/model {:stocks {:p {:init 1.0}}
                       :flows {:g {:to :p :rate (fn [v] (* 0.1 (v :p)))}
                               :lim {:from :p :rate (fn [v] (* 0.001 (v :p) (v :p)))}}})]
      (is (= #{:limits-to-growth} (found m {:p 1.0})))))
  (testing "policy resistance: two balancing loops pulling one stock toward different goals"
    (let [m (sf/model {:stocks {:s {:init 5.0}}
                       :flows {:up {:to :s :rate (fn [v] (/ (- 10.0 (v :s)) 2.0))}
                               :down {:to :s :rate (fn [v] (/ (- 2.0 (v :s)) 4.0))}}})
          e (first (ar/archetypes (ar/structure m 0.0 {:s 5.0})))]
      (is (= :policy-resistance (:archetype e)))
      (is (close? 10.0 (get-in e [:goals :up]) 1e-6))
      (is (close? 2.0 (get-in e [:goals :down]) 1e-6))))
  (testing "success to the successful: each gains at the other's expense"
    (let [m (sf/model {:stocks {:a {:init 2.0} :b {:init 1.0}}
                       :flows {:fa {:to :a :rate (fn [v] (let [a2 (* (v :a) (v :a)) b2 (* (v :b) (v :b))] (/ (* 10.0 a2) (+ a2 b2))))}
                               :fb {:to :b :rate (fn [v] (let [a2 (* (v :a) (v :a)) b2 (* (v :b) (v :b))] (/ (* 10.0 b2) (+ a2 b2))))}}})]
      (is (= #{:success-to-the-successful} (found m {:a 2.0 :b 1.0}))))))

(deftest loops-that-matter
  ;; logistic growth: the births loop dominates below half the limit, the
  ;; crowding loop above it (the known LTM result for the logistic)
  (let [m (sf/model {:params {:r 0.1 :k 100.0}
                     :stocks {:p {:init 2.0}}
                     :flows {:births {:to :p :rate (fn [v] (* (v :r) (v :p)))}
                             :crowding {:from :p :rate (fn [v] (/ (* (v :r) (v :p) (v :p)) (v :k)))}}})
        d (ar/dominance m {:from 0.0 :to 100.0 :dt 0.25 :every 4})
        idx (fn [f] (first (keep-indexed #(when (= [f] (:flows %2)) %1) (:loops d))))
        r (idx :births) b (idx :crowding)
        p (get-in (sf/run m {:from 0.0 :to 100.0 :dt 0.25}) [:stocks :p])
        ix (fn [t] (first (keep-indexed #(when (close? t %2 1e-9) %1) (:t d))))]
    (is (pos? (nth (nth (:scores d) 0) r)) "reinforcing scores are positive")
    (is (neg? (nth (nth (:scores d) 0) b)))
    (testing "the shift at half the limit"
      (let [early (nth (:relative d) (ix 10.0)) late (nth (:relative d) (ix 80.0))]
        (is (< (nth p 40) 50.0))
        (is (> (Math/abs (nth early r)) (Math/abs (nth early b))))
        (is (> (nth p 320) 50.0))
        (is (> (Math/abs (nth late b)) (Math/abs (nth late r))))))
    (testing "limits to growth read from the shift; linear births and deaths show none"
      (is (some #(= :limits-to-growth (:archetype %)) (ar/archetypes (ar/structure m 0.0 {:p 2.0}) d)))
      (let [lin (sf/model {:stocks {:p {:init 1.0}}
                           :flows {:b {:to :p :rate (fn [v] (* 0.03 (v :p)))}
                                   :d {:from :p :rate (fn [v] (* 0.01 (v :p)))}}})]
        (is (empty? (ar/archetypes (ar/structure lin 0.0 {:p 1.0})
                                   (ar/dominance lin {:from 0.0 :to 50.0 :dt 0.5 :every 4}))))))))

(deftest leverage-by-class
  ;; each parameter's elasticity on an outcome, grouped by Meadows' class
  (let [outcome (fn [run] (last (get-in run [:stocks :pop])))
        opts {:from 0.0 :to 50.0 :dt 0.5}
        r (ar/leverage limits {:b :gain :k :buffer} opts outcome)]
    (is (pos? (get-in r [:by-param :k])) "a larger limit, more people")
    (is (= #{:gain :buffer} (set (keys (:by-class r)))))
    (testing "the run's parameter overrides are kept in every run"
      (let [hi (ar/leverage limits {:b :gain :k :buffer} (assoc opts :params {:b 0.2}) outcome)]
        ;; with b 0.2 the population sits near k by t 50: elasticity of k near 1
        (is (close? 1.0 (get-in hi [:by-param :k]) 0.05))
        (is (< (Math/abs (get-in hi [:by-param :b])) 0.1))))
    (testing "a zero outcome or parameter gives no elasticity, with a reason"
      (let [z (ar/leverage limits {:b :gain :k :buffer} opts (fn [_] 0.0))]
        (is (nil? (get-in z [:by-param :b])))
        (is (= :zero-outcome (get-in z [:reasons :b])))
        (is (nil? (get-in z [:by-class :gain]))))
      (let [z (ar/leverage limits {:b :gain} (assoc opts :params {:b 0.0}) outcome)]
        (is (= :zero-param (get-in z [:reasons :b])))))
    (testing "classes name the model's parameters"
      (is (thrown? Exception (ar/leverage limits {:nope :gain} opts outcome))))))

(def ^:private w3 (delay (bnm.world3/model {})))
(def ^:private w3-dominance (delay (ar/dominance @w3 {:from 1900.0 :to 2100.0 :dt 0.5 :every 10})))

(deftest world3-s-loops
  (let [m @w3
        s (ar/structure m 1900.0 (:stocks (sf/at m 1900.0)))
        pops #{:p1 :p2 :p3 :p4}]
    (testing "the population's reinforcing loop: the young mature into parents who bear the young"
      (is (some #(and (= :reinforcing (:polarity %)) (= #{:p1 :p2} (set (:stocks %))) (= [:mat1 :br] (:flows %))) (:loops s))))
    (testing "the delays collapse, so the pollution loop through the appearance delay is found"
      (is (= [:ppapr-1 :ppapr-2 :ppapr-3] (get-in s [:groups :ppapr])))
      (is (some #(and (some #{:ppol} (:stocks %)) (some #{:ppapr} (:stocks %))) (:loops @w3-dominance))))
    (testing "no escalation in an aging chain: maturation is material, not a reaction"
      (is (not-any? #(and (= :escalation (:archetype %)) (some pops (:stocks %))) (ar/archetypes s))))
    (testing "the loop scores account for the population's change in rate"
      ;; LTM's identity: the in-links' signed contributions sum to the
      ;; change in the stock's net rate (here within 5%, away from turning points)
      (let [r (sf/run m {:from 1900.0 :to 2100.0 :dt 0.5})
            lv (fn [i] (into {} (for [k (keys (:stocks m))] [k (nth (get-in r [:stocks k]) i)])))
            rate (fn [i j] (reduce + 0.0 (for [[k {:keys [from to]}] (:flows m) :when (or (= from j) (= to j))]
                                           (* (if (= to j) 1.0 -1.0) (nth (get-in r [:flows k]) i)))))]
        (doseq [i [20 100 200] j [:p2 :p3 :p4]]
          (let [jac (ar/flow-jacobian m (nth (:t r) i) (lv i))
                pred (reduce + 0.0 (for [[[a b _] w] jac :when (= b j)] (* w (- ((lv (inc i)) a) ((lv i) a)))))]
            (is (close? 1.0 (/ pred (- (rate (inc i) j) (rate i j))) 0.05) (str j " at " (nth (:t r) i)))))))
    (testing "the reinforcing loops through the young adults dominate their growth, 1940-2025"
      (let [d @w3-dominance
            ix (keep-indexed (fn [i l] (when (some #{:p2} (:stocks l)) i)) (:loops d))
            share (fn [ss] (let [xs (map #(nth ss %) ix) r (reduce + 0.0 (filter pos? xs)) b (- (reduce + 0.0 (filter neg? xs)))]
                             (/ r (+ r b))))
            growth (keep (fn [[t ss]] (when (<= 1940.0 t 2025.0) (share ss))) (map vector (:t d) (:scores d)))]
        (is (> (/ (reduce + growth) (count growth)) 0.7))))
    (testing "and the scores show no lasting shift to balancing after the peak: the decline runs on
              reinforcing loops in reverse, so limits to growth is not claimed for the population"
      (is (not-any? #(and (= :limits-to-growth (:archetype %)) (some pops (:stocks %)))
                    (ar/archetypes s @w3-dominance))))))
