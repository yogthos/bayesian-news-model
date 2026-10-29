(ns bnm.sfc-rules-run-test
  (:require [bnm.engine :as eng]
            [bnm.modules.sfc :as msfc]
            [bnm.sfc :as sfc]
            [bnm.sfc-analysis :as sa]
            [bnm.sfc-horizon-run :as h]
            [bnm.sfc-rules-run :as r]
            [bnm.sfc-test :as fx]
            [augur.math]
            [clojure.java.io :as io]
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

(deftest scenarios-run-a-rate-shock-and-a-fiscal-expansion
  ;; bnm-4hv.15's reported-only scenario check: the same origin and
  ;; parameters, the policy rate +100bp from o + 1, or spending +1% of
  ;; expected output from o + 1 to o + 5 financed by debt
  (let [rs (into {} (map-indexed (fn [i y] [y (+ 0.03 (* 0.005 (Math/sin i)))]) (range 2000 2025)))
        panel (update @sim-panel "SIM" (fn [rows] (into {} (for [[y ob] rows] [y (assoc ob :policy (rs y))]))))
        sc (r/scenario-paths panel {} "SIM" 2012 10 sfc/rates)
        gi (fn [k y] (get-in sc [k y :int-paid :gov]))]
    (is (= #{:base :rate :fiscal} (set (keys sc))))
    (is (= (range 2013 2023) (sort (keys (:base sc)))))
    (doseq [k [:base :rate :fiscal] [_ o] (get sc k)]
      (is (< (:identity o) 1e-10) (str k)))
    (is (every? #(> (gi :rate %) (gi :base %)) (range 2013 2023)) "a dearer policy rate raises government's interest bill")
    (is (> (get-in sc [:fiscal 2013 :gov-debt]) (get-in sc [:base 2013 :gov-debt])) "the first year's spending is borrowed")
    (is (> (gi :fiscal 2014) (gi :base 2014)) "and next year's interest bill is higher on it")
    (let [est (r/scenario-paths panel {} "SIM" 2012 10 sfc/estimated)]
      (is (< (Math/abs (- (get-in est [:rate 2015 :int-paid :gov]) (get-in est [:base 2015 :int-paid :gov]))) 1e-12)
          "estimated has no rate channel"))))

(deftest the-rate-test-verdict-is-non-inferiority-against-estimated
  ;; bnm-4hv.15 (9h): every cell's lower end above -0.05 passes; any upper
  ;; end below -0.05 is inferior
  (let [ok {[:h1 :nl] [0.0 -0.01 0.01] [:h1 :debt] [0.0 -0.04 0.02] [:h35 :nl] [0.01 -0.02 0.03] [:h35 :debt] [0.0 -0.03 0.03]}]
    (is (= :non-inferior (r/non-inferior ok 0.05)))
    (is (= :inconclusive (r/non-inferior (assoc ok [:h35 :debt] [-0.03 -0.2 0.1]) 0.05)))
    (is (= :inferior (r/non-inferior (assoc ok [:h1 :nl] [-0.2 -0.3 -0.1]) 0.05)))
    (is (= :inconclusive (r/non-inferior (dissoc ok [:h1 :nl]) 0.05)) "a missing cell can't pass")))

(deftest forecasts-of-runs-each-named-rule-set
  (let [panel @sim-panel
        rows (filter #(= 2012 (:o %)) (r/mask panel #{} false))
        fs (r/forecasts-of panel {} "SIM" rows {:rates sfc/rates :estimated sfc/estimated})]
    (is (seq rows))
    (doseq [{:keys [o h outcome]} rows :let [f (fs [o h outcome])]]
      (is (= #{:rates :estimated :persistence :damped} (set (keys f))))
      (is (number? (:rates f))))
    (let [f (fs [2012 1 [:nl :gov]]) path (r/restart-path panel {} "SIM" 2012 5 sfc/rates)]
      (is (< (Math/abs (- (:rates f) (get-in path [2013 :nl :gov]))) 1e-10) "the engine's restart, as the design harness's"))))

(deftest the-scenario-report-takes-medians-of-differences
  (let [path (fn [d] (into (sorted-map) (for [y (range 2013 2023)] [y {:int-paid {:hh 0.01 :nfc 0.01 :gov (+ 0.02 d)} :int-recv {:hh 0.01 :nfc 0.0 :gov 0.0}
                                                                        :margin 0.01 :gov-debt 0.5 :identity 1e-15}])))
        runs {"A" {:rates {:base (path 0.0) :rate (path 0.001) :fiscal (path 0.002)}}
              "B" {:rates {:base (path 0.0) :rate (path 0.003) :fiscal (path 0.002)}}
              "C" {:rates {:base (path 0.0) :rate (path 0.002) :fiscal (path 0.002)}}}
        ls (r/scenario-report runs {:rates {}})]
    (is (some #(and (str/includes? % "rate") (str/includes? % "gov interest") (str/includes? % "y1 +0.00200")) ls))
    (is (str/includes? (last ls) "1.00e-15"))))

(deftest frozen-rows-are-the-targets-not-yet-in-the-data
  (let [mk (fn [ys] (into {} (for [y ys] [y {:flows-ok true :y 100.0 :b {:hh {:debt {:l 50.0}} :nfc {:debt {:l 60.0}}}
                                              :stocks-source :oecd-t720 :f {:hh {:nl 1.0} :nfc {:nl -1.0} :fin {:nl 0.1} :gov {:nl -0.1}}}])))
        panel {"A" (mk (range 1995 2025)) "B" (mk (range 1995 2026))}
        rs (r/freeze-rows panel)]
    (is (= #{["A" 2024] ["B" 2025]} (set (map (juxt :iso :o) rs))) "each country's latest measured origin")
    (is (= (set (range 1 6)) (set (map :h (filter #(= "A" (:iso %)) rs)))))
    (is (every? #(> (+ (:o %) (:h %)) 2024) (filter #(= "A" (:iso %)) rs)) "targets after the last data year")
    (is (= 25 (count (filter #(= "A" (:iso %)) rs))) "five horizons, four sectors and debt")
    (is (every? #(nil? (:data %)) rs))))

(deftest the-rate-test-report-gives-the-registered-verdicts
  (let [rows (for [split [:holdout :test] iso (if (= split :holdout) ["K"] ["A" "B" "C" "D"]) o (range 2010 2016) h [1 3 5]
                   out [[:nl :hh] [:nl :gov] [:debt]]]
               {:iso iso :o o :h h :outcome out :split split :data 0.0
                :f {:rates (if (even? o) 1.0 1.01) :estimated 1.0 :persistence 2.0 :damped 1.5 :restart 3.0}})
        null {[:h1 :nl] [-0.01 0.01] [:h1 :debt] [-0.01 0.01] [:h35 :nl] [-0.01 0.01] [:h35 :debt] [-0.01 0.01]}
        {:keys [lines verdicts]} (r/rates-report rows null 0.05)]
    (is (= :non-inferior (:holdout verdicts)) "a skill near 0 with a narrow null")
    (is (= :non-inferior (:test verdicts)))
    (is (some #(str/includes? % "rates vs persistence") lines))
    (let [bad (map #(if (= :holdout (:split %)) (assoc-in % [:f :rates] 1.5) %) rows)]
      (is (= :inferior (:holdout (:verdicts (r/rates-report bad null 0.05))))))))

(deftest the-rate-test-report-scores-only-rows-with-data
  ;; the frozen rows (bnm-4hv.16) keep :data nil where the vintage lacks
  ;; the target: they are left out, not a crash
  (let [rows (vec (for [split [:holdout :test] iso (if (= split :holdout) ["K"] ["A" "B" "C" "D"]) o (range 2010 2016) h [1 3 5]
                        out [[:nl :hh] [:nl :gov] [:debt]]]
                    {:iso iso :o o :h h :outcome out :split split :data 0.0
                     :f {:rates (if (even? o) 1.0 1.01) :estimated 1.0 :persistence 2.0 :damped 1.5 :restart 3.0}}))
        null {[:h1 :nl] [-0.01 0.01] [:h1 :debt] [-0.01 0.01] [:h35 :nl] [-0.01 0.01] [:h35 :debt] [-0.01 0.01]}
        missing (map #(assoc % :data nil :f (assoc (:f %) :rates 9.0)) (take 30 rows))
        {:keys [lines verdicts]} (r/rates-report (concat rows missing) null 0.05)]
    (is (= (:verdicts (r/rates-report rows null 0.05)) verdicts) "the same verdicts as without them")
    (is (= (format "rows scored (every model present): %d of %d" (count rows) (+ (count rows) 30)) (first lines)))))

;; -- base runs from each country's latest year (bnm-4hv.17; data on disk) -------------------

(def ^:private rule-sets {:rates sfc/rates :estimated sfc/estimated :keen {}})

(def ^:private debt-bound
  "Government or private debt over output beyond which a base run has run
  away (Ireland's reached 17 before its output turned negative): 5, or
  twice the first year's where that is higher (Luxembourg's private debt
  starts near 5)."
  5.0)

(def ^:private data-panel
  (delay
    (when (every? #(.exists (io/file %)) [r/panel-path r/policy-path r/keen-path r/holdout-path r/holdout2-path])
      (let [pol (sfc/read-policy r/policy-path)]
        [(merge (sfc/with-policy (sfc/read-panel r/panel-path) pol)
                (sfc/with-policy (sfc/read-panel r/holdout-path) pol)
                (sfc/with-policy (sfc/read-panel r/holdout2-path) pol))
         (sfc/read-keen r/keen-path)]))))

(defn- runaway
  "The first year of `iso`'s base run from its latest measured year,
  `years` on under learn options `opts`, whose output is not positive and
  finite or whose government or private debt over output is beyond
  debt-bound: [year output gov-debt private-debt], or nil."
  [panel kn iso opts years]
  (let [o (last (h/measured-years panel iso))
        kp (sa/known-panel panel (+ o sfc/vintage))
        p (sfc/learn kp kn iso o opts)]
    (loop [st (sfc/init kp kn iso o p) i 0 bounds nil]
      (when (< i years)
        (let [{s :state out :out} (sfc/step st p)
              y (double (:Y out)) gd (double (:gov-debt out)) pd (double (:private-debt out))
              [bg bp] (or bounds [(max debt-bound (* 2.0 (Math/abs gd))) (max debt-bound (* 2.0 (Math/abs pd)))])]
          (if (or (not (< 0.0 y Double/POSITIVE_INFINITY))
                  (not (< (Math/abs gd) bg)) (not (< (Math/abs pd) bp)))
            [(:year out) y gd pd]
            (recur s (inc i) [bg bp])))))))

(deftest irelands-base-run-stays-positive-and-bounded
  ;; bnm-4hv.17: from 2024 Ireland's output turned negative in 2034-37
  ;; under every rule set, its government debt 17 times output first
  (when-let [[panel kn] @data-panel]
    (doseq [[k opts] rule-sets]
      (is (nil? (runaway panel kn "IRL" opts 30)) (str "IRL " k)))))

(deftest no-countrys-base-run-runs-away-in-thirty-years
  (when-let [[panel kn] @data-panel]
    (doseq [iso (sort (keys panel))
            :when (and (not= "IRL" iso) (seq (h/measured-years panel iso)))
            [k opts] rule-sets]
      (is (nil? (runaway panel kn iso opts 30)) (str iso " " k)))))
