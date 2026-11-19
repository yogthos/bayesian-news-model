(ns bnm.testbed-test
  "docs/TESTBED.md: the synthetic crisis world, its lattice spec and its
  scores."
  (:require [augur.math :as m]
            [bnm.stocks :as sk]
            [bnm.testbed :as tb]
            [clojure.test :refer [deftest is testing]]))

(def small {:seed 3 :countries 24 :regions 4 :years 60})

(deftest the-world-is-reproducible-and-published-with-a-lag
  (let [a (tb/world small) b (tb/world small) c (tb/world (assoc small :seed 4))]
    (is (= (:onsets a) (:onsets b)) "the same seed, the same world")
    (is (not= (:onsets a) (:onsets c)))
    (is (= 24 (count (:countries a))))
    (testing "every stock is a slow-table series, read as of a month"
      (let [u (first (:countries a))
            s (get (:db a) [u "credit"])
            y (+ tb/epoch 30) dec (+ (* 12 y) 11)]
        (is (some? s))
        (is (nil? (sk/value s (- (* 12 tb/epoch) 1))) "nothing before the first year is published")
        (is (= (sk/value s (+ dec (:lag-months tb/defaults)))
               (get-in a [:observed u "credit" y]))
            "the year's value, published its lag after the year ends")
        (is (not= (sk/value s dec) (get-in a [:observed u "credit" y])) "not yet at the year's end")))))

(deftest crises-follow-the-planted-hazard
  (let [w (tb/world (assoc small :countries 60 :years 80))
        rows (for [[[u y] p] (:hazard w) :when (tb/at-risk? w u y)] [p (contains? (get-in w [:onsets u] #{}) y)])
        no-signal (augur.math/expit (:base tb/defaults))
        hi (filter #(> (first %) 0.1) rows) lo (filter #(<= (first %) (+ no-signal 1e-12)) rows)
        rate (fn [rs] (/ (count (filter second rs)) (double (max 1 (count rs)))))]
    (is (seq hi)) (is (seq lo))
    (is (> (rate hi) (* 3 (rate lo))) [(rate hi) (rate lo)])
    (testing "a crisis lasts its spell: no new onset while one is under way"
      (doseq [u (:countries w) :let [os (sort (get-in w [:onsets u] #{}))]]
        (is (every? (fn [[a b]] (>= (- b a) (:spell tb/defaults))) (partition 2 1 os)) u)))))

(deftest the-null-world-has-no-signal
  (let [w (tb/world (assoc small :scenario :null :countries 60 :years 80))]
    (is (apply = (vals (:hazard w))) "one rate everywhere")
    (is (pos? (count (mapcat val (:onsets w)))))))

(deftest scores-on-hand-made-rows
  (let [rows [{:year 1 :p 0.9 :y true} {:year 1 :p 0.1 :y false} {:year 1 :p 0.2 :y false}
              {:year 2 :p 0.3 :y true} {:year 2 :p 0.4 :y false}]]
    (is (= 2.5 (* 3 (tb/auc rows))) "ranks: 5 of 6 pairs ordered, the 0.3 onset below the 0.4 quiet")
    (is (= 0.75 (tb/within-year-auc [{:year 2 :p 0.3 :y true} {:year 2 :p 0.4 :y false} {:year 2 :p 0.5 :y true} {:year 2 :p 0.1 :y false}])))
    (is (= 0.0 (tb/alert-cost [{:p 0.9 :y true} {:p 0.1 :y false}] 0.2 4.0)))
    (is (= (/ 4.0 2) (tb/alert-cost [{:p 0.1 :y true} {:p 0.1 :y false}] 0.2 4.0)) "a miss costs 4")
    (is (< (Math/abs (- 0.5 (tb/molchan-area (for [i (range 400)] {:p (Math/sin (* 7.1 i)) :y (zero? (mod i 5))})))) 0.1)
        "a forecast unrelated to the outcomes: about 0.5")
    (is (< (tb/molchan-area [{:p 0.9 :y true} {:p 0.8 :y true} {:p 0.1 :y false} {:p 0.2 :y false}]) 0.3)
        "a perfect ranking: the misses fall away with few alarms")))

(deftest a-scenario-runs-end-to-end
  (let [r (tb/evaluate (assoc small :scenario :credit-waves :countries 16 :years 40))]
    (is (every? #(contains? r %) [:auc :within-auc :level-r :brier :log-skill :oracle-log-skill :ece :alert-cost :constant-cost :molchan]))
    (is (<= 0.0 (:auc r) 1.0))
    (is (< (:oracle-log-skill r) 1.0))))

(deftest partial-coverage-leaves-most-countries-without-the-driver
  (let [w (tb/world {:seed 1 :scenario :partial-coverage})
        has (count (filter #(get (:db w) [% "credit"]) (:countries w)))]
    (is (< 10 has 25) has)
    (is (= 60 (count (filter #(get (:db w) [% "house"]) (:countries w)))) "the other stocks everywhere")))

;; --- bnm-58f: the bloc-rupture world (docs/TESTBED.md) ---------------------------

(def bloc {:seed 5 :countries 40 :regions 4 :years 100 :scenario :bloc-rupture})

(deftest the-bloc-world-plants-the-cost-on-the-aligned-dependent
  (let [w (tb/world bloc)
        {:keys [blocs ruptures dependence west-tie]} (:bloc w)]
    (is (= (:onsets w) (:onsets (tb/world bloc))) "reproducible")
    (is (seq ruptures) "ruptures at set years")
    (is (= #{"W" "R" "C" "N"} (set (vals blocs))))
    (testing "after a rupture at a power, the hazard rises with dependence on it times the western tie, and not for its own bloc"
      (let [[r g] (first ruptures)
            y (+ tb/epoch r 1)
            lift (fn [c] (- (m/logit (get-in w [:hazard [c y]])) (m/logit (get-in w [:hazard [c (+ tb/epoch r -3)]]))))
            west (filter #(= "W" (blocs %)) (:countries w))
            own (filter #(= g (blocs %)) (:countries w))
            expected (fn [c] (* (:bloc-strength tb/defaults) (get-in dependence [c g (+ r -1)]) (get-in west-tie [c (+ r -1)])))]
        (is (every? #(< (Math/abs (- (expected %) (- (m/logit (get-in w [:hazard [% y]])) (m/logit (tb/bloc-free-hazard w % y))))) 1e-9) (:countries w))
            "the bloc term is exactly strength x dependence x tie, read the year before")
        (let [per-dep (fn [cs] (tb/mean (map #(get-in west-tie [% (+ r -1)]) cs)))]
          (is (> (per-dep west) (* 5 (per-dep own))) "per unit of dependence (the tie) the west pays, the power's own bloc hardly"))))
    (testing "the published signals: dependence and the tie a year late, the rupture at once"
      (let [u (first (:countries w)) [r g] (first ruptures)]
        (is (some? (get (:db w) [u (str "dep-" g)])))
        (is (some? (get (:db w) [u "west-tie"])))
        (is (= 1.0 (sk/value (get (:db w) [u (str "rupture-" g)]) (+ (* 12 (+ tb/epoch r)) 11))) "known the December of the rupture")))))

(deftest bloc-signals-by-mode
  (let [names (fn [mode] (set (map :name (tb/bloc-decls mode))))]
    (is (= #{:dep-R-x-rupture :dep-C-x-rupture} (names :dependence)))
    (is (= #{:dep-R-x-rupture :dep-C-x-rupture :dep-R-x-rupture-x-west :dep-C-x-rupture-x-west} (names :alignment)))
    (is (= (names :alignment) (names :alignment-placebo)) "the placebo reads the same names, the tie shuffled")
    (is (every? #(= :bloc (:domain %)) (tb/bloc-decls :alignment)))))

(deftest the-placebo-shuffles-the-tie-across-countries
  (let [w (tb/world (assoc bloc :blocs-mode :alignment-placebo))
        w0 (tb/world (assoc bloc :blocs-mode :alignment))
        u (first (:countries w))]
    (is (= (:onsets w) (:onsets w0)) "the same world")
    (is (not= (sk/value (get (:db w) [u "west-tie"]) (+ (* 12 (+ tb/epoch 50)) 11 6))
              (sk/value (get (:db w0) [u "west-tie"]) (+ (* 12 (+ tb/epoch 50)) 11 6)))
        "the published tie is another country's")))

(deftest a-bloc-theory-adds-its-fixed-cost-to-the-offset
  (let [w (tb/world (assoc bloc :blocs-mode :alignment))
        [r g] (first (get-in w [:bloc :ruptures]))
        t (+ (* 12 (+ tb/epoch r)) 11)
        c (first (filter #(= "W" (get-in w [:bloc :blocs %])) (:countries w)))
        off (fn [k] ((:offset (tb/spec w {} {:bloc-theory k})) :year c t))
        x (tb/bloc-exposure w c t)]
    (is (pos? x) "a western country after the rupture is exposed")
    (is (< (Math/abs (- (- (off 3.0) (off 0.0)) (* 3.0 x))) 1e-9) "the offset moves by k x the published exposure")
    (is (< (Math/abs (- (off 0.0) (tb/known-base-logit w t))) 1e-9) "k 0: the base rate known by then")))
