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

(deftest on-top-of-a-base-it-learns-the-departures
  ;; t112: logit p = base + a + b.x, the base held fixed (augur.logit/fit's row offset)
  (let [g (m/rng 4)
        base (into {} (for [u places t origins] [[u t] (- (* 0.8 (m/normal! g)) 2.0)]))
        x (into {} (for [u places t origins] [[u t] (m/normal! g)]))
        y (into {} (for [k (keys x)] [k (< (m/next-double! g) (m/expit (+ (base k) (* 1.0 (x k)))))]))
        tgt (fn [u t] {:y (y [u t]) :known-at (+ t 24)})
        bf (fn [u t] (when (not= u "f") (base [u t])))
        off (th/offsets {:places places :origins origins :inputs (fn [u t] [(x [u t])]) :target tgt :prior 1.0 :base bf})
        t (origins 20)]
    (testing "the fit holds the base as each row's offset, and adds it back"
      (let [train (for [u places t' origins :when (and (bf u t') (<= (+ t' 24) t))]
                    {:x (double-array [1.0 (x [u t'])]) :y (y [u t']) :w 1.0 :o (base [u t'])})
            beta (lgt/fit train 2 {:prior 1.0})]
        (is (< (Math/abs (- (m/logit (lgt/predict beta (double-array [1.0 (x ["c" t])]) (base ["c" t])))
                            (get off ["c" t])))
               1e-9))))
    (testing "a place with no base neither trains it nor takes an offset"
      (is (nil? (get off ["f" t]))))
    (testing "the departure is linear in the input, the planted slope's sign"
      (let [tl (peek origins) d (fn [u] (- (get off [u tl]) (base [u tl])))]
        (is (= (sort-by #(x [% tl]) (remove #{"f"} places)) (sort-by d (remove #{"f"} places))))))))

(deftest raw-priors-hold-sparse-columns
  ;; t121: :raw true puts every slope's prior on its input's own scale
  ;; (augur.logit/fit's :raw), so a column present in a handful of rows
  ;; (0 elsewhere, the mean) is held near zero until outcomes accumulate
  (let [w (world 2)
        sparse (fn [u t] [(get (:x w) [u t]) (when (and (= u "a") (< t 120)) 3.0)])
        args {:places places :origins origins :inputs sparse :target (target w) :prior 1.0}
        t (peek origins)
        b0 (get (th/fits args) t)
        b1 (get (th/fits (assoc args :raw true)) t)
        train (for [u places t' origins :let [tg ((target w) u t')] :when (and tg (<= (:known-at tg) t))]
                {:x (double-array (cons 1.0 (map #(double (or % 0.0)) (sparse u t')))) :y (:y tg) :w 1.0})]
    (is (= (vec (lgt/fit train 3 {:prior 1.0 :raw #{1 2}})) (vec b1)) "augur's :raw on every slope")
    (is (< (Math/abs (aget ^doubles b1 2)) (Math/abs (aget ^doubles b0 2))) "the sparse column's slope is held nearer zero")
    (is (= (vec b0) (vec (get (th/fits (assoc args :raw false)) t))) "without it, as before")))

(deftest per-slope-precision
  ;; t124: :penalties, one precision multiplier per column (x[0] the
  ;; intercept's, ignored), passed to augur.logit/fit
  (let [w (world 3)
        args {:places places :origins origins :inputs (inputs w) :target (target w) :prior 1.0}
        t (peek origins)
        b0 (get (th/fits args) t)
        b1 (get (th/fits (assoc args :penalties [1.0 1000.0])) t)]
    (is (< (Math/abs (aget ^doubles b1 1)) (* 0.5 (Math/abs (aget ^doubles b0 1)))) "a strong prior holds the slope toward zero")
    (is (= (vec b0) (vec (get (th/fits (assoc args :penalties [1.0 1.0])) t))) "multipliers of 1: as before")))

(deftest theory-signs-pin-wrong-signed-slopes-at-zero
  ;; bnm-7yg.6: :signs, one of -1, 0, +1 per column (x[0] the intercept's,
  ;; ignored): a slope fit against its sign is held at zero (an active set:
  ;; refit with its precision at 1e9 until no slope is wrong-signed); 0 or
  ;; nil leaves it free
  (let [w (world 4)
        args {:places places :origins origins :inputs (inputs w) :target (target w) :prior 1.0}
        t (peek origins)
        b0 (get (th/fits args) t)]
    (is (pos? (aget ^doubles b0 1)) "the world's slope is positive")
    (is (= (vec b0) (vec (get (th/fits (assoc args :signs [0 1])) t))) "a slope already of its sign: unchanged")
    (is (= (vec b0) (vec (get (th/fits (assoc args :signs [0 0])) t))) "sign 0: free")
    (let [b (get (th/fits (assoc args :signs [0 -1])) t)]
      (is (< (Math/abs (aget ^doubles b 1)) 1e-6) "a slope against its sign is held at zero"))
    (testing "with :penalties, the pinned column's precision goes to 1e9 and the others keep theirs"
      (let [b (get (th/fits (assoc args :signs [0 -1] :penalties [1.0 2.0])) t)]
        (is (< (Math/abs (aget ^doubles b 1)) 1e-6))))
    (testing "offsets read the constrained fit"
      (let [off (th/offsets (assoc args :signs [0 -1]))
            vals (set (map #(Math/round (* 1e6 (double %))) (keep (fn [u] (get off [u t])) places)))]
        (is (= 1 (count vals)) "a zero slope: every place the same offset")))))
