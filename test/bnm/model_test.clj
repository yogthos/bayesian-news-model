(ns bnm.model-test
  (:require [bnm.math :as m]
            [bnm.model :as model]
            [bnm.ontology :as ont]
            [bnm.series :as series]
            [bnm.structure :as structure]
            [clojure.test :refer [deftest is testing]]))

(def violence (ont/channel-index :violence))
(def protest (ont/channel-index :protest))

(defn- toy-structure []
  (-> (structure/volumes)
      (structure/tally "RUS" "UKR" 500.0)
      (structure/tally "RUS" "BLR" 200.0)
      (structure/tally "UKR" "POL" 300.0)
      (structure/tally "FRA" nil 400.0)
      (structure/build {:min-dyad-volume 100.0})))

(deftest structure-is-bottom-up
  (let [s (toy-structure)
        levels (map :level (:nodes s))]
    (is (= levels (sort levels)))
    (is (contains? (:tracked s) "RUS|UKR"))
    (is (some #(= "world" (:id %)) (:nodes s)))
    (testing "a country's children are its rest leaf and its tracked dyads"
      (is (= #{"UKR:*" "RUS|UKR" "POL|UKR"}
             (set (:children (first (filter #(= "UKR" (:id %)) (:nodes s))))))))))

(defn- simulate-world
  "Weekly records where RUS:* violence spikes at random and UKR:* violence
  follows a week later; FRA protests are steady noise."
  [weeks seed]
  (let [g (m/rng seed)]
    (loop [t 0 prev 0.0 out []]
      (if (< t weeks)
        (let [rus (if (< (m/next-double! g) 0.2) (+ 20.0 (m/poisson! g 30.0)) (double (m/poisson! g 2.0)))
              ukr (double (m/poisson! g (+ 1.0 (* 0.8 prev))))
              fra (double (m/poisson! g 10.0))
              background (double (m/poisson! g 200.0))]
          (recur (inc t) rus
                 (conj out {"toy" [["RUS" nil violence rus]
                                   ["UKR" nil violence ukr]
                                   ["FRA" nil protest fra]
                                   ["FRA" nil 0 background]]})))
        out))))

(deftest learns-a-lagged-connection
  (let [mdl (series/with-routes (model/create (toy-structure) {:decays [0.0 0.9]}))
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    mdl (map-indexed vector (simulate-world 400 7)))
        {:keys [model persistence]} (model/skill mdl)]
    (testing "UKR violence is explained by last week's RUS violence"
      (doseq [[id src] [["UKR" "RUS"] ["UKR:*" "RUS:*"]]]
        (let [top (first (model/connections mdl id violence))]
          (is (= [src :violence] [(:source top) (:channel top)]))
          (is (> (:share top) 0.3)))))
    (testing "alphas stay bounded for sources that never fire"
      (is (every? #(< % 100.0) (:alpha (model/node mdl "UKR")))))
    (testing "one-step predictions beat carrying the last week forward"
      (is (< model persistence)))))

(deftest forecast-is-deterministic-and-shaped
  (let [mdl (series/with-routes (model/create (toy-structure)))
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    mdl (map-indexed vector (simulate-world 100 3)))
        a (model/forecast mdl {:horizon 8 :sims 5 :seed 11})
        b (model/forecast mdl {:horizon 8 :sims 5 :seed 11})
        i ((:index mdl) "world")]
    (is (= (vec (aget (:paths a) i)) (vec (aget (:paths b) i))))
    (is (= (* 5 8 model/C) (alength ^doubles (aget (:paths a) i))))
    (is (every? #(and (not (Double/isNaN %)) (>= % 0.0)) (aget (:paths a) i)))))
