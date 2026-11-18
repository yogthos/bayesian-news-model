(ns bnm.theory-test
  "bnm.theory: a theory forecast refit at each origin on what is known by
  then, to set a lattice node's offset (known priors as the base, the
  lattice learning departures from it)."
  (:require [augur.logit :as lgt]
            [augur.math :as m]
            [bnm.theory :as th]
            [clojure.test :refer [deftest is testing]]))

(def places ["a" "b" "c" "d" "e" "f"])
(def origins (vec (range 11 (* 12 40) 12)))

(defn- world [seed]
  (let [g (m/rng seed)
        x (into {} (for [u places t origins] [[u t] (m/normal! g)]))
        y (into {} (for [[k v] x] [k (< (m/next-double! g) (m/expit (- (* 1.5 v) 2.0)))]))]
    {:x x :y y}))

(defn- target [w] (fn [u t] (when (contains? (:y w) [u t]) {:y (get (:y w) [u t]) :known-at (+ t 24)})))
(defn- inputs [w] (fn [u t] [(get (:x w) [u t])]))

(deftest the-offset-is-refit-on-what-is-known
  (let [w (world 1)
        off (th/offsets {:places places :origins origins :inputs (inputs w) :target (target w) :prior 1.0})]
    (testing "an origin before any outcome is known has no offset"
      (is (nil? (get off ["a" 11])))
      (is (nil? (get off ["a" 23]))))
    (testing "at origin t: the logit fit on the rows known by t (known-at <= t), applied to t's inputs"
      (let [t (origins 20)
            train (for [u places t' origins :let [tg ((target w) u t')] :when (and tg (<= (:known-at tg) t))]
                    {:x (double-array [1.0 (get (:x w) [u t'])]) :y (:y tg) :w 1.0})
            beta (lgt/fit train 2 {:prior 1.0})]
        (is (< (Math/abs (- (m/logit (lgt/predict beta (double-array [1.0 (get (:x w) ["c" t])])))
                            (get off ["c" t])))
               1e-9))))
    (testing "it learns the planted slope"
      (let [t (peek origins) hi (get off ["a" t]) xs (get (:x w) ["a" t])]
        (is (some? hi))
        (is (= (pos? xs) (> hi (m/logit (m/expit -2.0)))) "above the base rate where the input is high")))
    (testing "a missing input reads 0, the standardized mean"
      (let [off2 (th/offsets {:places places :origins origins :inputs (fn [u t] [(when (not= u "f") (get (:x w) [u t]))]) :target (target w) :prior 1.0})
            t (peek origins)]
        (is (some? (get off2 ["f" t])))))))

(deftest aggregates-take-the-chance-any-member-has-one
  (is (< (Math/abs (- (th/any-of [(m/logit 0.1) (m/logit 0.2)]) (m/logit (- 1.0 (* 0.9 0.8))))) 1e-12))
  (is (nil? (th/any-of [])))
  (testing "many likely members: finite, as the banking node's offsets are (p kept within 1e-6 of 0 and 1)"
    (is (< (Math/abs (th/any-of (repeat 60 (m/logit 0.6)))) 100.0))
    (is (< (Math/abs (- (th/any-of (repeat 60 (m/logit 0.6))) (m/logit (- 1.0 1e-6)))) 1e-9))))
