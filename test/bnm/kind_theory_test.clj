(ns bnm.kind-theory-test
  "bnm.kind-theory (bnm-1dw): a kind's year node reads a theory forecast
  from stocks and flows at the origin as its offset, refit on what is known
  (bnm.theory), as banking's does."
  (:require [augur.math :as m]
            [bnm.kind-theory :as kt]
            [bnm.theory :as th]
            [clojure.test :refer [deftest is testing]]))

(def countries ["a1" "a2" "b1" "b2" "b3"])
(def parent {"a1" "A" "a2" "A" "b1" "B" "b2" "B" "b3" "B" "A" "world" "B" "world"})
(def g (m/rng 9))
(def x (into {} (for [u countries t (range 11 600 12)] [[u t] (m/normal! g)])))
(def y (into {} (for [[k v] x] [k (< (m/next-double! g) (m/expit (- (* 1.2 v) 2.0)))])))

(def sp {:scales [{:name :quarter :per 3 :horizon 3} {:name :year :per 12 :horizon 12}]
         :levels [[:country countries] [:region ["A" "B"]] [:world ["world"]]]
         :parent parent :start 0 :end 599
         :target (fn [scale place t]
                   (when (and (= :year scale) (contains? y [place t]))
                     {:y (y [place t]) :known-at (+ t 24)}))
         :offset (fn [scale _ _] (when (= :quarter scale) -3.0))})

(def row (fn [u t] [(x [u t]) nil]))

(deftest the-year-node-reads-the-refit-theory
  (let [sp2 (kt/with-theory sp row 2)
        os (range 11 600 12)
        z0 (th/place-z (fn [u t] (x [u t])) countries os 20)
        off (th/offsets {:places countries :origins os :prior 1.0
                         :inputs (fn [u t] [(z0 u t) nil])
                         :target (fn [u t] ((:target sp) :year u t))})
        t (nth os 30)]
    (testing "a country: the theory's log odds"
      (is (some? (get off ["a1" t])))
      (is (< (Math/abs (- (get off ["a1" t]) ((:offset sp2) :year "a1" t))) 1e-12)))
    (testing "a region or the world: the chance any member has one"
      (is (< (Math/abs (- (th/any-of [(off ["b1" t]) (off ["b2" t]) (off ["b3" t])]) ((:offset sp2) :year "B" t))) 1e-12))
      (is (< (Math/abs (- (th/any-of (map #(off [% t]) countries)) ((:offset sp2) :year "world" t))) 1e-12)))
    (testing "other scales keep their offsets"
      (is (= -3.0 ((:offset sp2) :quarter "a1" t)))
      (is (nil? ((:offset sp2) :half "a1" t))))
    (testing "it learns the planted sign: higher input, higher log odds"
      (let [tl (last os)]
        (is (= (sort-by #(x [% tl]) countries) (sort-by #((:offset sp2) :year % tl) countries)))))
    (is (map? (:kind-theory sp2)) "kept for diagnosis")))

(deftest declared-for-sovereign-defaults
  (is (= [:external-debt :reserves :inflation :growth :us-rate-change24]
         (mapv :name (kt/decls "sovereign_default")))))

(deftest the-flag-names-declared-kinds
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/kind-theory-choice)]
    (is (nil? (choice {})))
    (is (= ["sovereign_default"] (choice {:kind-theory "sovereign_default"})))
    (is (thrown? Exception (choice {:kind-theory "coup"})) "no theory declared")
    (is (thrown? Exception (choice {:kind-theory "banking_crisis"})) "banking has its own, --theory-offset")))
