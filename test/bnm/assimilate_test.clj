(ns bnm.assimilate-test
  (:require [augur.math :as m]
            [bnm.assimilate :as as]
            [bnm.stockflow :as sf]
            [bnm.world3 :as w3]
            [clojure.test :refer [deftest is testing]]))

(def logistic
  (sf/model {:params {:r 0.3 :k 10.0}
             :stocks {:s {:init 0.5 :nonneg true}}
             :flows {:g {:to :s :rate (fn [v] (* (v :r) (v :s) (- 1.0 (/ (v :s) (v :k)))))}}}))

(defn- observed [model params names times noise seed]
  (let [g (m/rng seed)
        r (sf/run model {:from 0.0 :to (double (last times)) :dt 0.25 :params params})
        at (fn [series t] (nth series (long (Math/round (/ t 0.25)))))]
    (into (sorted-map) (for [t times]
                         [t (into {} (for [n names :let [s (or (get-in r [:stocks n]) (get-in r [:aux n]))]]
                                       [n (+ (at s t) (* noise (m/normal! g)))]))]))))

(deftest a-growth-rate-from-noisy-counts
  (let [obs (observed logistic {:r 0.3} [:s] (range 1.0 31.0 1.0) 0.2 1)
        post (as/assimilate logistic {:from 0.0 :dt 0.25 :members 200 :seed 2
                                      :priors {:r {:mean 0.1 :sd 1.0 :log true}}
                                      :observations obs :noise {:s 0.2}})]
    (is (< (Math/abs (- 0.3 (get-in post [:params :r :mean]))) 0.03) (str (:params post)))
    (is (< (get-in post [:params :r :sd]) 0.05) "and how sure")
    (testing "the stocks are tracked too"
      (is (< (Math/abs (- (get-in post [:stocks :s :mean]) (get-in obs [30.0 :s]))) 0.5)))))

(deftest a-parameter-the-data-cannot-see
  ;; K never binds while s stays far below it: its posterior stays near
  ;; its prior (identification shows in the spread)
  (let [obs (observed logistic {:r 0.3 :k 1000.0} [:s] (range 1.0 6.0 1.0) 0.05 3)
        post (as/assimilate logistic {:from 0.0 :dt 0.25 :members 200 :seed 4
                                      :priors {:r {:mean 0.2 :sd 0.5 :log true} :k {:mean 1000.0 :sd 0.5 :log true}}
                                      :observations obs :noise {:s 0.05}})]
    (is (> (get-in post [:params :k :sd]) 200.0) "still wide")
    (is (< (Math/abs (- 0.3 (get-in post [:params :r :mean]))) 0.05))))

(deftest world3-s-capital-lifetime-from-its-history
  ;; a synthetic World3 history with the average lifetime of industrial
  ;; capital at 14 years: from noisy industrial output and population every
  ;; five years, a prior centred at 10 learns 14
  (let [truth (w3/run {})
        idx (fn [t] (long (Math/round (/ (- t 1900.0) 0.5))))
        g (m/rng 5)
        obs (into (sorted-map) (for [t (range 1905.0 1975.0 5.0)]
                                 [t {:io (* (nth (get-in truth [:aux :io]) (idx t)) (+ 1.0 (* 0.02 (m/normal! g))))}]))
        noise (fn [t] {:io (* 0.02 (get-in obs [t :io]))})
        post (as/assimilate (w3/model {}) {:from 1900.0 :dt 0.5 :members 40 :seed 6
                                           :priors {:alic1 {:mean 10.0 :sd 0.4 :log true}}
                                           :observations obs :noise-at noise})]
    (is (< (Math/abs (- 14.0 (get-in post [:params :alic1 :mean]))) 1.5) (str (:params post)))))
