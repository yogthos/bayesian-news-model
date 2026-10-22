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

(defn- grid-posterior
  "The exact posterior of log r for the logistic, prior N(log m, sd),
  observations of s with noise sd `noise`: its mean and sd in r, on a
  coarse grid to find the mode and a fine one around it (the posterior
  is far narrower than the prior)."
  [obs m sd noise]
  (let [lp (fn [lr]
             (let [r (sf/run logistic {:from 0.0 :to (double (last (keys obs))) :dt 0.25 :params {:r (Math/exp lr)}})
                   at (fn [t] (nth (get-in r [:stocks :s]) (long (Math/round (/ t 0.25)))))]
               (- (- (/ (Math/pow (/ (- lr (Math/log m)) sd) 2) 2.0))
                  (reduce + (for [[t o] obs] (/ (Math/pow (/ (- (:s o) (at t)) noise) 2) 2.0))))))
        coarse (map #(+ (Math/log m) (* sd (/ % 25.0))) (range -100 101))
        mode (apply max-key lp coarse)
        fine (map #(+ mode (* 0.001 %)) (range -100 101))
        lps (map lp fine)
        top (apply max lps) ws (map #(Math/exp (- % top)) lps) z (reduce + ws)
        rs (map #(Math/exp %) fine)
        mu (/ (reduce + (map * ws rs)) z)]
    {:mean mu :sd (Math/sqrt (/ (reduce + (map #(* %1 (Math/pow (- %2 mu) 2)) ws rs)) z))}))

(deftest calibration-matches-the-exact-posterior
  ;; the ensemble smoother with multiple data assimilation, parameters
  ;; only: a deterministic model's posterior, against the exact one
  (let [obs (observed logistic {:r 0.3} [:s] (range 1.0 31.0 1.0) 0.2 1)
        exact (grid-posterior obs 0.1 1.0 0.2)
        post (as/calibrate logistic {:from 0.0 :dt 0.25 :members 200 :seed 2 :iterations 4
                                     :priors {:r {:mean 0.1 :sd 1.0 :log true}}
                                     :observations obs :noise {:s 0.2}})]
    (is (< (Math/abs (- (:mean exact) (get-in post [:params :r :mean]))) (* 1.0 (:sd exact))) (str exact (:params post)))
    (is (< 0.7 (/ (get-in post [:params :r :sd]) (:sd exact)) 1.6) (str exact (:params post)))))

(deftest calibration-leaves-the-unseen-alone
  (let [obs (observed logistic {:r 0.3 :k 1000.0} [:s] (range 1.0 6.0 1.0) 0.05 3)
        post (as/calibrate logistic {:from 0.0 :dt 0.25 :members 200 :seed 4 :iterations 4
                                     :priors {:r {:mean 0.2 :sd 0.5 :log true} :k {:mean 1000.0 :sd 0.5 :log true}}
                                     :observations obs :noise {:s 0.05}})]
    (is (> (/ (get-in post [:params :k :log-sd]) 0.5) 0.8) "k's spread in the log, near its prior's")
    (is (< (Math/abs (- 0.3 (get-in post [:params :r :median]))) 0.02))))

(deftest world3-calibrated
  (let [truth (w3/run {})
        idx (fn [t] (long (Math/round (/ (- t 1900.0) 0.5))))
        g (m/rng 5)
        obs (into (sorted-map) (for [t (range 1905.0 1975.0 5.0)]
                                 [t {:io (* (nth (get-in truth [:aux :io]) (idx t)) (+ 1.0 (* 0.02 (m/normal! g))))}]))
        post (as/calibrate (w3/model {}) {:from 1900.0 :dt 0.5 :members 40 :seed 6 :iterations 4
                                          :priors {:alic1 {:mean 10.0 :sd 0.4 :log true}}
                                          :observations obs :noise-at (fn [t] {:io (* 0.02 (get-in obs [t :io]))})})]
    (is (< (Math/abs (- 14.0 (get-in post [:params :alic1 :median]))) 0.3) (str (:params post)))))

(deftest observations-in-any-order
  ;; a hash map of observation times is walked in time order
  (let [obs (observed logistic {:r 0.3} [:s] (range 1.0 31.0 1.0) 0.2 1)
        post (as/assimilate logistic {:from 0.0 :dt 0.25 :members 100 :seed 2
                                      :priors {:r {:mean 0.1 :sd 1.0 :log true}}
                                      :observations (into {} obs) :noise {:s 0.2}})]
    (is (< (Math/abs (- 0.3 (get-in post [:params :r :mean]))) 0.03)))
  (is (thrown? clojure.lang.ExceptionInfo
               (as/calibrate logistic {:from 0.0 :dt 0.25 :members 10 :priors {:r {:mean 0.1 :sd 1.0 :log true}}
                                       :observations {1.0 {:s 1.0}}}))
      "an observed name without its noise"))
