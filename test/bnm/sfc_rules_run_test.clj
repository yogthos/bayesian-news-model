(ns bnm.sfc-rules-run-test
  (:require [bnm.engine :as eng]
            [bnm.modules.sfc :as msfc]
            [bnm.sfc :as sfc]
            [bnm.sfc-analysis :as sa]
            [bnm.sfc-rules-run :as r]
            [bnm.sfc-test :as fx]
            [augur.math]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private sim-panel
  (delay {"SIM" (update-vals (@#'fx/sim-panel 24) #(assoc % :stocks-source :oecd-t720))}))

(deftest the-harness-restart-is-the-engine-restart
  ;; the design harness steps bnm.sfc/step; the registered test runs
  ;; bnm.sfc-analysis/restart through the engine: the same numbers
  (let [panel @sim-panel kn {}
        path (r/restart-path panel kn "SIM" 2012 5 sfc/estimated)
        rs (sa/restart panel kn "SIM" 2012 5 nil sfc/estimated)
        at (fn [v y] (get-in rs [:vars v :median (.indexOf ^java.util.List (vec (:t rs)) y)]))]
    (doseq [y (range 2013 2018)]
      (is (< (Math/abs (- (get-in path [y :nl :gov]) (at "sfc/net-lending-gov" y))) 1e-10) (str y))
      (is (< (Math/abs (- (get-in path [y :debt]) (at "sfc/private-debt" y))) 1e-10) (str y)))
    (is (not= (get-in path [2017 :nl :gov]) (get-in (r/restart-path panel kn "SIM" 2012 5 {}) [2017 :nl :gov]))
        "the rules change the forecast")))

(deftest rows-are-split-into-design-test-and-holdout
  (let [mk (fn [ys] (into {} (for [y ys] [y {:flows-ok true :y 100.0 :b {:hh {:debt {:l 50.0}} :nfc {:debt {:l 60.0}}}
                                              :stocks-source :oecd-t720 :f {:hh {:nl 1.0} :nfc {:nl -1.0} :fin {:nl 0.1} :gov {:nl -0.1}}}])))
        panel {"OLD" (mk (range 1990 2015)) "NEW" (mk (range 2003 2015))}
        rows (r/mask panel #{"NEW"} false)
        split (fn [iso o] (set (map :split (filter #(and (= iso (:iso %)) (= o (:o %))) rows))))]
    (is (= #{:design} (split "OLD" 2002)))
    (is (= #{:test} (split "OLD" 2003)))
    (is (= #{:holdout} (split "NEW" 2008)) "a holdout country's origins, whatever the year")
    (is (empty? (split "NEW" 2007)) "five measured years before the first origin")
    (is (= #{1 3 5} (set (map :h (filter #(and (= "OLD" (:iso %)) (= 2005 (:o %))) rows)))))
    (is (empty? (filter #(and (= "OLD" (:iso %)) (= 2012 (:o %)) (= 3 (:h %))) rows)) "a target past the data")))

(deftest the-holdout-interval-resamples-blocks-of-origins
  (let [rows (for [iso ["A" "B"] o (range 2008 2020) h [1 3]] {:iso iso :o o :h h :data 0.0 :f {:x 2.0 :m 1.0}})
        [p lo hi] (r/block-bootstrap rows :m :x 300 7 5)]
    (is (= [0.75 0.75 0.75] [p lo hi]) "every draw the same skill when every row has it"))
  (let [rows (vec (for [iso ["A" "B"] o (range 2008 2020)] {:iso iso :o o :h 1 :data 0.0 :f {:x (if (even? o) 2.0 1.0) :m 1.0}}))
        a (r/block-bootstrap rows :m :x 300 7 5) b (r/block-bootstrap rows :m :x 300 7 5)]
    (is (= a b) "repeatable from its seed")
    (is (< (nth a 1) (first a) (nth a 2)))))

(deftest the-registered-verdict-needs-every-baseline-horizon-and-outcome
  (let [b {:nl 0.1 :debt 0.2}
        ok {:nl [0.2 0.05 0.3] :debt [0.3 0.1 0.5]}
        cis (fn [f] (into {} (for [base [:persistence :damped :restart] hg [:h1 :h35]] [[base hg] (f base hg)])))]
    (is (= :pass (r/overall (cis (constantly ok)) b)))
    (is (= :inconclusive (r/overall (cis (fn [base hg] (if (= [base hg] [:damped :h1]) {:nl [0.05 -0.05 0.15] :debt [0.3 0.1 0.5]} ok))) b)))
    (is (= :worse (r/overall (cis (fn [base hg] (if (= hg :h35) {:nl [-0.3 -0.5 -0.1] :debt [0.3 0.1 0.5]} ok))) b)))
    (is (= :equivalent (r/overall (cis (constantly {:nl [0.0 -0.05 0.05] :debt [0.0 -0.1 0.1]})) b)))))

(deftest the-holdout-interval-is-calibrated-under-its-error-model
  (let [rows (vec (for [iso ["A" "B"] o (range 2008 2020) h [1 3] out [[:nl :hh] [:nl :gov]]] {:iso iso :o o :h h :outcome out}))
        passes (count (filter (fn [k] (let [sim (r/simulate rows 0.0 (augur.math/rng-mixed 99 k) {})
                                            [_ lo _] (r/interval :holdout sim :m :x 0 5)]
                                        (> lo 0.0)))
                              (range 200)))]
    (is (<= passes 20) (str passes " of 200 false passes at zero skill")))
  (let [rows (vec (for [iso ["A" "B"] o (range 2008 2020)] {:iso iso :o o :h 1 :outcome [:nl :hh] :data 0.0 :f {:x 2.0 :m 1.0}}))
        [p lo hi] (r/interval :holdout rows :m :x 0 5)]
    (is (= 0.75 p))
    (is (< lo p hi))))

(deftest the-held-fallback-holds-every-rule
  ;; 9g's fallback (the review's reported-only diagnostic): no fiscal rule,
  ;; investment held, interest and dividends as shares
  (let [p (sfc/learn @sim-panel {} "SIM" 2020 r/held)]
    (is (nil? (:fiscal-rule p)))
    (is (= [0.0 0.0 0.0 0.0] (mapv #(double (get-in p [:investment-rule %])) [:phi :slope :b-pi :b-ds])))
    (is (map? (:int-share p)))
    (is (map? (:div-recv-share p))))
  (let [rows (for [split [:test :design] iso ["A" "B" "C"] o (range 2000 2006) h [1 3] out [[:nl :hh] [:debt]]]
               {:iso iso :o o :h h :outcome out :split split :data 0.0
                :f {:estimated 1.0 :hold 2.0 :persistence 4.0 :damped 2.0 :restart 8.0}})
        ls (r/held-report rows)]
    (is (= "rows with every model: 144 of 144" (first ls)))
    (is (some #(and (str/includes? % "test") (str/includes? % "estimated vs hold") (str/includes? % "h1 +0.750")) ls)
        "estimated's squared error a quarter of the held's")))
