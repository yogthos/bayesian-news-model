(ns bnm.kind-theory-test
  "bnm.kind-theory (bnm-1dw): a kind's year node reads a theory forecast
  from stocks and flows at the origin as its offset, refit on what is known
  (bnm.theory), as banking's does."
  (:require [augur.logit]
            [augur.math :as m]
            [bnm.kind-theory :as kt]
            [bnm.signals]
            [bnm.stocks]
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

(deftest on-top-of-the-level
  ;; t112 as amended: the theory refit as before, only its slopes b.z added
  ;; to the year node's log odds (:offset-add); the level stays the lattice's
  (let [sp2 (kt/with-theory sp row 2 {:add true})
        os (range 11 600 12)
        z0 (th/place-z (fn [u t] (x [u t])) countries os 20)
        fits (th/fits {:places countries :origins os :prior 1.0
                       :inputs (fn [u t] [(z0 u t) nil])
                       :target (fn [u t] ((:target sp) :year u t))})
        t (nth os 30)
        b (get fits t)]
    (is (some? b))
    (testing "a country: the slopes times its inputs, no intercept"
      (is (< (Math/abs (- (* (aget ^doubles b 1) (z0 "a1" t)) ((:offset-add sp2) :year "a1" t))) 1e-12)))
    (testing "the offset is the spec's own, untouched"
      (is (nil? ((:offset sp2) :year "a1" t)))
      (is (= -3.0 ((:offset sp2) :quarter "a1" t))))
    (testing "aggregates and other scales take nothing added"
      (is (nil? ((:offset-add sp2) :year "B" t)))
      (is (nil? ((:offset-add sp2) :quarter "a1" t))))
    (testing "the planted sign: higher input, more added"
      (let [tl (last os)]
        (is (= (sort-by #(x [% tl]) countries) (sort-by #((:offset-add sp2) :year % tl) countries))))))
  (testing "a spec that already adds to its offset refuses, rather than drop one"
    (is (thrown? Exception (kt/with-theory (assoc sp :offset-add (fn [_ _ _] 0.1)) row 2 {:add true}))))
  (testing "shapes refused: their columns are not centred, and with the intercept dropped every row lifts (t133 audit)"
    (is (= "bnm.kind-theory: :add takes no :shapes: the shape columns are not centred, so the dropped intercept lifts every row"
           (try (kt/with-theory sp row 2 {:add true :shapes true}) nil (catch clojure.lang.ExceptionInfo e (ex-message e)))))))

(deftest fits-are-what-offsets-apply
  (let [os (range 11 600 12)
        args {:places countries :origins os :prior 1.0 :inputs (fn [u t] [(x [u t])])
              :target (fn [u t] ((:target sp) :year u t))}
        off (th/offsets args) fits (th/fits args) t (nth os 30)]
    (is (< (Math/abs (- (get off ["a2" t]) (m/logit (augur.logit/predict (get fits t) (double-array [1.0 (x ["a2" t])]))))) 1e-12))))

(deftest declared-for-sovereign-defaults
  (is (= [:external-debt :reserves :inflation :growth :us-rate-change24]
         (mapv :name (kt/decls "sovereign_default")))))

(deftest declared-for-currency-and-inflation-crises
  ;; bnm-mn8 (t115): t110's list re-specified; t110's is kept as currency-t110
  (is (= [:reserves :misalignment :misalignment-x-peg :momentum12 :depreciation3 :reserves12
          :current-account :inflation :external-debt :us-rate-change24]
         (mapv :name (kt/decls "currency_crisis"))))
  (is (= [:reserves :reer-change24 :current-account :inflation :external-debt :us-rate-change24]
         (mapv :name @#'kt/currency-t110)))
  (is (contains? (kt/vars ["currency_crisis"]) "derived/rer_usd"))
  (is (contains? (kt/vars ["currency_crisis"]) "fast/fx"))
  (is (= [:inflation :depreciation12 :government-debt-change24 :reserves :growth]
         (mapv :name (kt/decls "inflation_crisis")))))

(deftest the-declared-vars-are-loaded
  (testing "every series a chosen kind declares, for load-series' :vars (bnm.named/vars lacks most)"
    (is (= #{"debt/external_gni" "debt/reserves_months" "economy/inflation" "economy/gdp_growth" "finance/policy_rate"}
           (kt/vars ["sovereign_default"])))
    (is (contains? (kt/vars ["sovereign_default" "currency_crisis"]) "derived/rer_usd")))
  (testing "a declared series missing from the table refuses, rather than reading as the mean everywhere"
    (let [db {["a1" "economy/inflation"] :s ["a1" "economy/gdp_growth"] :s}]
      (is (thrown-with-msg? Exception #"debt/external_gni"
                            (kt/with-kind-theory {"sovereign_default" sp} ["sovereign_default"] db))))))

(deftest inflation-is-read-as-log1p
  (let [ds [{:name :a :log1p true} {:name :b}]
        f (kt/transformed ds (fn [u _] (when (= u "x") [300.0 300.0])))]
    (is (= [(Math/log1p 3.0) 300.0] (f "x" 0)))
    (is (= (Math/log1p -0.99) (first ((kt/transformed ds (fn [_ _] [-150.0 nil])) "x" 0))) "floored at -99%")
    (is (= [nil nil] ((kt/transformed ds (fn [_ _] [nil nil])) "x" 0)))
    (is (nil? (f "y" 0)))))

(deftest the-flag-names-declared-kinds
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/kind-theory-choice)]
    (is (nil? (choice {})))
    (is (= ["sovereign_default"] (choice {:kind-theory "sovereign_default"})))
    (is (= ["currency_crisis" "inflation_crisis"] (choice {:kind-theory "currency_crisis,inflation_crisis"})))
    (is (= ["sovereign_default" "currency_crisis"] (choice {:kind-theory "sovereign_default,currency_crisis:add"})))
    (is (= #{"currency_crisis"} ((resolve 'bnm.world-run/kind-theory-add) {:kind-theory "sovereign_default,currency_crisis:add"})))
    (is (= #{} ((resolve 'bnm.world-run/kind-theory-add) {:kind-theory "sovereign_default"})))
    (is (thrown? Exception (choice {:kind-theory "currency_crisis:mix"})) "only :add")
    (is (thrown? Exception (choice {:kind-theory "protest_surge"})) "no theory declared")
    (is (thrown? Exception (choice {:kind-theory "banking_crisis" :theory-offset "true"})) "banking has its own, --theory-offset")))

(deftest every-read-in-a-declaration-counts
  ;; bnm-1ft: an interaction reads series inside :times and expressions;
  ;; vars and the loaded check must see them, or they read nil (the mean)
  (let [ds [{:name :a :var "w/x" :unit "world" :read [:change 6] :times {:var "c/e" :unit :own :read [:value]}}
            {:name :b :expr [:mul {:var "w/y" :unit "world" :read [:change 6]}
                             [:minus {:var "c/f" :read [:value]} {:var "c/g" :read [:value]}]]}]]
    (is (= #{"w/x" "c/e" "w/y" "c/f" "c/g"} (kt/decl-vars ds)))
    (is (thrown-with-msg? Exception #"c/g"
                          (kt/check-loaded ds {["world" "w/x"] :s ["a1" "c/e"] :s ["world" "w/y"] :s ["a1" "c/f"] :s})))
    (is (nil? (kt/check-loaded ds {["world" "w/x"] :s ["a1" "c/e"] :s ["world" "w/y"] :s ["a1" "c/f"] :s ["a1" "c/g"] :s})))))

(deftest the-global-layer
  (testing "six global drivers read at the origin, six of them times a country's exposure a year earlier"
    (is (= [:usd6 :us-rate6 :oil6 :food6 :baa6 :vix6
            :usd6-x-external-debt :usd6-x-peg :us-rate6-x-external-debt :oil6-x-net-fuel :food6-x-food-imports :baa6-x-external-debt]
           (mapv :name kt/global)))
    (is (every? #(contains? (kt/decl-vars kt/global) %)
                ["global/usd_eer" "global/oil" "global/food" "global/baa_spread" "global/vix" "finance/policy_rate"
                 "debt/external_gni" "finance/fx_peg" "energy/fuel_exports_share" "energy/fuel_imports_share" "food/imports_share"])))
  (testing "an interaction is the world's 6-month change times the country's exposure 12 months back"
    (let [st (fn [pairs] (bnm.stocks/series (for [[p v] pairs] [p p v])))
          db {["world" "global/usd_eer"] (st [[100 1.0] [106 1.5]])
              ["a1" "debt/external_gni"] (st [[90 40.0] [100 80.0]])}
          d (first (filter #(= :usd6-x-external-debt (:name %)) kt/global))]
      (is (< (Math/abs (- (* 0.5 40.0) (bnm.signals/eval-signal db (or (:expr d) d) "a1" 106))) 1e-12))))
  (testing "it is added on top of each kind's offset, and goes with a kind's own theory"
    (let [sp2 (kt/with-theory sp row 2)
          sp3 (kt/with-theory sp2 row 2 {:add true})]
      (is (fn? (:offset-add sp3)))
      (is (= ((:offset sp2) :year "a1" 300) ((:offset sp3) :year "a1" 300))))))

(deftest the-global-flag
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/global-theory-choice)]
    (is (nil? (choice {})))
    (is (= ["currency_crisis" "banking_crisis"] (choice {:global-theory "currency_crisis,banking_crisis"})))
    (is (thrown? Exception (choice {:global-theory "coup"})) "economic kinds only")))

(deftest the-global-shapes-flag
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/global-shapes-choice)]
    (is (= #{} (choice {})))
    (is (= #{"market_crash"} (choice {:global-shapes "market_crash" :global-theory "market_crash,currency_crisis"})))
    (is (thrown? Exception (choice {:global-shapes "market_crash"})))))

(deftest variant-flags
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/kind-theory-choice)]
    (is (= ["inflation_crisis@monthly" "currency_crisis@partners"] (choice {:kind-theory "inflation_crisis@monthly,currency_crisis@partners"})))
    (is (thrown? Exception (choice {:kind-theory "inflation_crisis@nope"})))
    (is (= #{"inflation_crisis"} ((resolve 'bnm.world-run/impute-choice) {:impute "inflation_crisis" :kind-theory "inflation_crisis@monthly"})))))


(deftest missing-inputs-filled
  ;; bnm-s6m: with :impute, the theory reads bnm.impute's filled inputs and
  ;; the share missing, one column more
  (let [sp2 (kt/with-theory sp (fn [u t] (if (= u "b2") [nil nil] [(x [u t]) nil])) 2 {:impute true})
        t (nth (range 11 600 12) 30)]
    (is (= 3 (count ((:inputs (:kind-theory sp2)) "a1" t))))
    (is (some? (first ((:inputs (:kind-theory sp2)) "b2" t))) "b2's missing input filled from its peers")
    (is (some? ((:offset sp2) :year "b2" t)))))

(deftest the-impute-flag
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/impute-choice)]
    (is (false? (choice {})))
    (is (true? (choice {:impute "true" :kind-theory "sovereign_default"})))
    (is (= #{"inflation_crisis"} (choice {:impute "inflation_crisis" :kind-theory "sovereign_default,inflation_crisis"}))
        "t116: only the kinds named")
    (is (thrown? Exception (choice {:impute "inflation_crisis"})) "nothing to fill without a theory layer")
    (is (thrown? Exception (choice {:impute "coup" :kind-theory "inflation_crisis"})) "a kind no layer serves")
    (is (thrown? Exception (choice {:impute "true"})) "nothing to fill without a theory layer")))

(deftest only-the-tables-a-layer-reads
  ;; t116: the ledger's inputs are the files a run reads
  (is (= ["data/slow/series.tsv"] (kt/paths-for #{"economy/inflation" "debt/reserves_months"})))
  (is (= ["data/slow/series.tsv" "data/derived/global-monthly.tsv"] (kt/paths-for (kt/decl-vars kt/global))))
  (is (= ["data/slow/series.tsv" "data/derived/rer-annual.tsv" "data/fast/series.tsv"] (kt/paths-for (kt/vars ["currency_crisis"])))))

(deftest shapes-per-input
  ;; bnm-asp: each standardized input z as [z, max(0,z+1), max(0,z), max(0,z-1), missing]
  (is (= [0.5 1.5 0.5 0.0 0.0] (kt/shape 1.0 0.5)))
  (is (= [-2.0 0.0 0.0 0.0 0.0] (kt/shape 1.0 -2.0)))
  (is (= [0.0 1.0 0.0 0.0 1.0] (kt/shape nil nil)) "missing: z read as 0, the flag on")
  (let [sp2 (kt/with-theory sp (fn [u t] (if (= u "b2") [nil nil] [(x [u t]) nil])) 2 {:shapes true})
        t (nth (range 11 600 12) 30)]
    (is (= 10 (count ((:inputs (:kind-theory sp2)) "a1" t))))
    (is (= 1.0 (nth ((:inputs (:kind-theory sp2)) "b2" t) 4)) "b2's first input missing")
    (is (some? ((:offset sp2) :year "a1" t)))))

(deftest the-shapes-flag
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/shapes-choice)]
    (is (false? (choice {})))
    (is (true? (choice {:shapes "true" :kind-theory "currency_crisis"})))
    (is (= #{"currency_crisis"} (choice {:shapes "currency_crisis" :kind-theory "inflation_crisis,currency_crisis"})))
    (is (thrown? Exception (choice {:shapes "coup" :kind-theory "currency_crisis"})))
    (is (thrown? Exception (choice {:shapes "true"})))))

(deftest the-tables-read
  (is (= ["data/slow/series.tsv" "data/derived/global-monthly.tsv" "data/derived/rer-annual.tsv" "data/fast/series.tsv"
          "data/derived/trade-partners.tsv" "data/derived/onset-history.tsv" "data/derived/fiscal-series.tsv"
          "data/derived/banking-series.tsv"]
         kt/series-paths))
  (is (not (some #{"data/derived/banking-series.tsv"}
                 (kt/paths-for (kt/vars ["sovereign_default" "inflation_crisis@monthly" "currency_crisis" "coup" "fiscal_consolidation"]))))
      "the base's kinds do not read t126's table"))


(deftest variants-keep-the-base-lists
  ;; t117: new lists under kind@variant, so the base's command keeps reading
  ;; the lists it was adopted with
  (is (= "inflation_crisis" (kt/kind-of "inflation_crisis@monthly")))
  (is (= "coup" (kt/kind-of "coup")))
  (is (= [:inflation :depreciation12 :government-debt-change24 :reserves :growth]
         (mapv :name (kt/decls "inflation_crisis@monthly"))))
  (is (= (conj (mapv :name (kt/decls "currency_crisis")) :partner-falls)
         (mapv :name (kt/decls "currency_crisis@partners"))))
  (is (every? (kt/vars ["inflation_crisis@monthly"]) ["economy/inflation_monthly" "economy/cpi_general" "economy/inflation" "fast/fx"]))
  (is (every? (kt/vars ["currency_crisis@partners"]) ["trade/export_share_ru" "trade/export_share_cn" "trade/export_share_eu" "fast/fx"])))

(deftest the-freshest-inflation
  (let [st (fn [pairs] (bnm.stocks/series (for [[p a v] pairs] [p a v])))
        bis {["a1" "economy/inflation_monthly"] (st [[100 101 50.0]])}
        fao {["a1" "economy/cpi_general"] (st [[80 90 1.0] [92 102 1.4]])}
        wdi {["a1" "economy/inflation"] (st [[95 104 30.0]])}]
    (testing "BIS's monthly year-on-year first, as log(1 + x/100)"
      (is (< (Math/abs (- (Math/log 1.5) (kt/fresh-inflation (merge bis fao wdi) "a1" 105))) 1e-12)))
    (testing "else FAOSTAT's 12-month change in the log index"
      (is (< (Math/abs (- 0.4 (kt/fresh-inflation (merge fao wdi) "a1" 105))) 1e-12)))
    (testing "else WDI's annual"
      (is (< (Math/abs (- (Math/log 1.3) (kt/fresh-inflation wdi "a1" 105))) 1e-12)))
    (is (nil? (kt/fresh-inflation {} "a1" 105)))))

(deftest partner-falls
  ;; each partner's 12-month fall against the dollar (0 for a rise), times the
  ;; country's export share to it a year before
  (let [st (fn [pairs] (bnm.stocks/series (for [[p v] pairs] [p p v])))
        db {["RUS" "fast/fx"] (st [[100 3.5] [112 4.2]])
            ["CHN" "fast/fx"] (st [[100 1.9] [112 1.85]])
            ["DEU" "fast/fx"] (st [[100 -0.3] [112 -0.2]])
            ["KAZ" "trade/export_share_ru"] (st [[95 0.1]])
            ["KAZ" "trade/export_share_cn"] (st [[95 0.2]])
            ["KAZ" "trade/export_share_eu"] (st [[95 0.5]])}]
    (is (< (Math/abs (- (+ (* 0.1 0.7) (* 0.2 0.0) (* 0.5 0.1)) (kt/partner-falls db "KAZ" 112))) 1e-9))
    (is (nil? (kt/partner-falls db "XXX" 112)) "no shares known: nil")))

(deftest shaped-global-layer
  ;; --global-shapes: the global layer adds through :add, which refuses
  ;; shapes since the t133 audit (uncentred columns, intercept dropped)
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #":add takes no :shapes"
        (kt/with-global-theory-row {"market_crash" sp} ["market_crash"] (fn [u t] (vec (repeat 12 (x [u t])))) {:shapes #{"market_crash"}}))))

(deftest raw-prior-global-layer
  ;; --global-raw-prior: the global layer's slopes on raw-scale priors (t121's treatment)
  (let [row (fn [u t] (vec (repeat 12 (x [u t]))))
        out (get (kt/with-global-theory-row {"market_crash" sp} ["market_crash"] row {:raw #{"market_crash"}}) "market_crash")
        plain (get (kt/with-global-theory-row {"market_crash" sp} ["market_crash"] row {}) "market_crash")]
    (is (true? (:raw (:kind-theory out))))
    (is (false? (:raw (:kind-theory plain))))
    (is (fn? (:offset-add out)))))

(deftest stronger-priors-on-the-world-drivers
  ;; t124: the global layer's six world drivers (no exposure) at a higher
  ;; precision, the six channels at the default
  (is (= [10.0 10.0 10.0 10.0 10.0 10.0 1.0 1.0 1.0 1.0 1.0 1.0] (kt/driver-penalties 10.0)))
  (is (= [1.0 10.0 10.0 10.0 10.0 10.0 1.0 1.0 1.0 1.0 1.0] (kt/column-penalties [10.0 1.0] {:shapes true})) "intercept, then five columns per input")
  (is (= [1.0 10.0 1.0 1.0] (kt/column-penalties [10.0 1.0] {:impute true})) "the share missing at the default")
  (let [row (fn [u t] (vec (repeat 12 (x [u t]))))
        out (get (kt/with-global-theory-row {"market_crash" sp} ["market_crash"] row {:driver-precision 10.0}) "market_crash")]
    (is (= (into [1.0] (kt/driver-penalties 10.0)) (:penalties (:kind-theory out))))
    (is (fn? (:offset-add out)))))

(deftest the-driver-precision-flag
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/global-driver-precision-choice)]
    (is (nil? (choice {})))
    (is (= 10.0 (choice {:global-driver-precision "10" :global-theory "market_crash"})))
    (is (thrown? Exception (choice {:global-driver-precision "10"})))
    (is (thrown? Exception (choice {:global-driver-precision "0" :global-theory "market_crash"})))))

(deftest the-global-raw-prior-flag
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/global-raw-prior-choice)]
    (is (= #{} (choice {})))
    (is (= #{"market_crash"} (choice {:global-raw-prior "market_crash" :global-theory "market_crash"})))
    (is (thrown? Exception (choice {:global-raw-prior "market_crash"})))))

(deftest declared-for-coups-and-war
  ;; gap 1 (t119): coups and war onsets from their literature, shaped
  (is (= [:coup-since :coup-count10 :regional-coups3 :growth :inflation :military-executive
          :electoral-democracy :protest :protest-change6 :infant-mortality]
         (mapv :name (kt/decls "coup"))))
  (is (= [:war-since :conflict-since :regional-conflict3 :military-gdp :military-change24
          :electoral-democracy :verbal-conflict :verbal-conflict-change6 :violence-change6 :world-risk6 :infant-mortality]
         (mapv :name (kt/decls "war_onset"))))
  (is (every? (kt/vars ["coup" "war_onset"]) ["history/coup_since" "history/coup_count3" "history/conflict_onset_count3"
                                              "politics/verbal_conflict" "politics/geopolitical_risk"]))
  (is (= "data/derived/onset-history.tsv" (some #{"data/derived/onset-history.tsv"} (kt/paths-for (kt/vars ["coup"]))))))

(deftest regional-means
  ;; the mean of the other countries in the subregion (bnm.geo) at t
  (let [st (fn [v] (bnm.stocks/series [[100 100 v]]))
        db {["MLI" "history/coup_count3"] (st 3.0) ["NER" "history/coup_count3"] (st 1.0)
            ["BFA" "history/coup_count3"] (st 2.0) ["FRA" "history/coup_count3"] (st 0.0)}]
    (is (= 1.5 ((kt/regional-mean "history/coup_count3") db "MLI" 105)) "Niger and Burkina Faso, not France, not Mali itself")))

(deftest declared-for-gap-2
  ;; gap 2 (t120): fiscal consolidations, growth collapses, banking's own
  ;; theory with the literature's inputs added
  (is (= [:balance :balance-change12 :expenditure-change12 :government-debt :government-debt-change24
          :spread :spread-change6 :election-legislative :election-presidential :growth :inflation
          :us-rate-change24 :external-debt]
         (mapv :name (kt/decls "fiscal_consolidation"))))
  (is (= [:tot-change12 :tot-change24 :commodity-exports :reserves :private-credit-change36 :external-debt
          :current-account :fx-momentum12 :conflict-since :regional-conflict3 :growth :inflation :us-rate-change24]
         (mapv :name (kt/decls "growth_collapse"))))
  (is (= [:dsr-gap :house-boom36 :credit-x-house :private-credit-change36 :short-rate-change12
          :spread-change6 :current-account :fx-momentum12]
         (mapv :name (kt/decls "banking_crisis@literature"))))
  (is (= [:npl :npl-change12 :capital :liquidity :tot-change12 :tot-change24 :reserves-change12
          :fx-momentum12 :private-credit-change36]
         (mapv :name (kt/decls "banking_crisis@banks"))) "t126: bank health and the commodity bust")
  ;; bnm-g5t.29: the commodity terms of trade read as a boom a year before
  ;; and a bust from its peak, not one symmetric change
  (let [bb (kt/decls "banking_crisis@boom-bust")
        by (into {} (map (juxt :name identity) bb))]
    (is (= [:npl :npl-change12 :capital :liquidity :tot-boom36 :tot-bust24 :reserves-change12
            :fx-momentum12 :private-credit-change36]
           (mapv :name bb)))
    (is (= {:var "trade/commodity_tot" :read [:change 36] :lag 12 :stale 9} (dissoc (by :tot-boom36) :name)))
    (is (= {:var "trade/commodity_tot" :read [:drawdown 24] :stale 9} (dissoc (by :tot-bust24) :name)))
    (is (= (remove (comp #{:tot-change12 :tot-change24} :name) (kt/decls "banking_crisis@banks"))
           (remove (comp #{:tot-boom36 :tot-bust24} :name) bb)) "the rest as t126's"))
  (is (every? (kt/vars ["banking_crisis@banks"]) ["banks/npl" "banks/capital_assets" "banks/liquid_reserves" "trade/commodity_tot"]))
  (is (some #{"data/derived/banking-series.tsv"} (kt/paths-for (kt/vars ["banking_crisis@banks"]))))
  (is (not (some #{"data/derived/banking-series.tsv"} (kt/paths-for (kt/vars ["fiscal_consolidation"])))))
  (is (nil? (kt/decls "banking_crisis")) "banking's base theory stays --theory-offset's")
  (is (every? (kt/vars ["fiscal_consolidation"]) ["fiscal/balance_gdp" "fiscal/expenditure_gdp" "fast/spread"]))
  (is (some #{"data/derived/fiscal-series.tsv"} (kt/paths-for (kt/vars ["fiscal_consolidation"]))))
  (is (not (some #{"data/derived/fiscal-series.tsv"} (kt/paths-for (kt/vars ["growth_collapse"])))))
  (is (= "data/derived/banking-series.tsv" (last kt/series-paths))))

(deftest banking-keeps-its-own-inputs
  ;; banking_crisis@literature: the --theory-offset theory's inputs first,
  ;; then the declared ones, all refit (and shaped) together
  (let [own (fn [u t] (when-not (= u "b3") [(x [u t]) 0.5]))
        bsp (assoc sp :theory {:inputs own :width 2})
        [r w] (kt/with-own-inputs bsp (fn [u t] [(- (x [u t])) nil]) 2)
        t 371]
    (is (= 4 w))
    (is (= [(x ["a1" t]) 0.5 (- (x ["a1" t])) nil] (r "a1" t)))
    (is (= [nil nil (- (x ["b3" t])) nil] (r "b3" t)) "no own row: its inputs missing")
    (is (thrown? Exception (kt/with-own-inputs sp (fn [_ _] [1.0]) 1)) "a spec without a theory to extend refuses"))
  (let [bsp (assoc sp :theory {:inputs (fn [u t] [(x [u t])]) :width 1})
        db {}]
    (with-redefs [kt/decls (fn [k] (when (= k "banking_crisis@literature") [{:name :q :var "q/q" :read [:value]}]))
                  kt/check-loaded (fn [_ _] nil)
                  bnm.signals/assemble (fn [_ _] {:row (fn [u t] [(x [u t])])})]
      (let [out (get (kt/with-kind-theory {"banking_crisis" bsp} ["banking_crisis@literature"] db #{} false #{"banking_crisis"}) "banking_crisis")]
        (is (= 10 (count ((:inputs (:kind-theory out)) "a1" 371))) "two inputs, five columns each")
        (is (some? ((:offset out) :year "a1" 371)))))))

(deftest raw-priors-per-kind
  ;; t121: --raw-prior kind,... fits that kind's theory with its priors on
  ;; each input's own scale
  (let [sp2 (kt/with-theory sp (fn [u t] [(x [u t]) (when (= u "a1") 2.0)]) 2 {:raw true})]
    (is (true? (:raw (:kind-theory sp2))))
    (is (some? ((:offset sp2) :year "a1" 371))))
  (let [bsp (assoc sp :theory {:inputs (fn [u t] [(x [u t])]) :width 1})]
    (with-redefs [kt/decls (fn [k] (when (= k "banking_crisis@literature") [{:name :q :var "q/q" :read [:value]}]))
                  kt/check-loaded (fn [_ _] nil)
                  bnm.signals/assemble (fn [_ _] {:row (fn [u t] [(x [u t])])})]
      (let [out (get (kt/with-kind-theory {"banking_crisis" bsp} ["banking_crisis@literature"] {} #{} false false #{"banking_crisis"}) "banking_crisis")]
        (is (true? (:raw (:kind-theory out))))
        (is (= 2 (count ((:inputs (:kind-theory out)) "a1" 371))) "unshaped: one column per input, no missing flags")))))

(deftest the-raw-prior-flag
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/raw-prior-choice)]
    (is (false? (choice {})))
    (is (= #{"banking_crisis"} (choice {:raw-prior "banking_crisis" :kind-theory "coup,banking_crisis@literature" :theory-offset "true"})))
    (is (thrown? Exception (choice {:raw-prior "coup" :kind-theory "currency_crisis"})))
    (is (thrown? Exception (choice {:raw-prior "coup"})))))

(deftest the-flag-takes-bankings-variant
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/kind-theory-choice)]
    (is (= ["banking_crisis@literature"] (choice {:kind-theory "banking_crisis@literature" :theory-offset "true"})))
    (is (= ["coup" "banking_crisis@banks"] (choice {:kind-theory "coup,banking_crisis@banks" :theory-offset "true"})) "t126")
    (is (thrown? Exception (choice {:kind-theory "banking_crisis@literature"})) "it extends --theory-offset's theory")
    (is (thrown? Exception (choice {:kind-theory "banking_crisis@literature:add" :theory-offset "true"})) "replace only")))

(deftest banking's-aftermath-is-not-risk
  ;; t128: NPLs stay high for years after a crisis and come out late, so the
  ;; bank-health inputs read the aftermath as risk. Within eight years of an
  ;; onset (Reinhart and Rogoff 2014, the recovery's length) they read 0
  ;; and a flag carries the aftermath; onsets alone, known when they happen
  (let [irl08 (+ (* 12 2008) 8)
        decls (kt/aftermath-decls {"IRL" #{irl08}})
        by (into {} (map (juxt :name identity) decls))
        npl (bnm.stocks/series (for [y (range 2000 2021)] [(+ (* 12 y) 11) (+ (* 12 y) 14) 10.0]))
        db {["IRL" "banks/npl"] npl ["FRA" "banks/npl"] npl}
        ev (fn [n u y] (bnm.signals/eval-signal db (:expr (by n)) u (+ (* 12 y) 11)))]
    (is (= [:npl :npl-change12 :capital :liquidity :tot-boom36 :tot-bust24 :reserves-change12
            :fx-momentum12 :private-credit-change36 :post-crisis]
           (mapv :name decls)))
    (is (= 1.0 (ev :post-crisis "IRL" 2012)))
    (is (= 0.0 (ev :post-crisis "IRL" 2007)) "before the onset")
    (is (= 0.0 (ev :post-crisis "IRL" 2016)) "Dec 2016 is 99 months on: past eight years")
    (is (= 1.0 (ev :post-crisis "IRL" 2015)) "Dec 2015, 87 months on: within")
    (is (= 0.0 (ev :post-crisis "FRA" 2012)) "no onset")
    (is (= 0.0 (ev :npl "IRL" 2012)) "the aftermath's NPLs read 0")
    (is (= 10.0 (ev :npl "IRL" 2017)))
    (is (= 10.0 (ev :npl "FRA" 2012)))
    (is (= (by :tot-bust24) (first (filter #(= :tot-bust24 (:name %)) (kt/decls "banking_crisis@boom-bust")))) "the rest as t128's")
    (is (= [:npl :npl-change12 :capital :liquidity :tot-boom36 :tot-bust24 :reserves-change12
            :fx-momentum12 :private-credit-change36 :post-crisis]
           (mapv :name (kt/decls "banking_crisis@aftermath"))))
    (is (every? (kt/vars ["banking_crisis@aftermath"]) ["banks/npl" "banks/capital_assets" "trade/commodity_tot"]))))

;; t137 (the aftermath plan's rank 2): a legacy stock's slope changes with
;; time since the kind's last known onset

(deftest a-stock-read-against-time-since-the-onset
  (let [d (fn [u t] (if (= u "a1") 0.5 0.0))
        sp2 (kt/with-theory sp row 2 {:decay {:d d :levels [0]}})
        os (range 11 600 12)
        z0 (th/place-z (fn [u t] (x [u t])) countries os 20)
        t (nth os 30)
        in (:inputs (:kind-theory sp2))]
    (is (= 4 (count (in "a1" t))) "the two inputs, then the decay and the stock times it")
    (is (= 0.5 (nth (in "a1" t) 2)))
    (is (< (Math/abs (- (* 0.5 (z0 "a1" t)) (nth (in "a1" t) 3))) 1e-12))
    (is (= [0.0 0.0] (subvec (in "b1" t) 2)) "no onset: both 0")
    (is (some? ((:offset sp2) :year "a1" t)))
    (is (= 12 (count ((:inputs (:kind-theory (kt/with-theory sp row 2 {:shapes true :decay {:d d :levels [0]}}))) "a1" t)))
        "after the shape columns")
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #":decay"
                          (kt/with-theory sp row 2 {:add true :decay {:d d :levels [0]}})))))

(deftest the-stocks-each-theory-decays
  (is (= {"sovereign_default" [:external-debt :reserves :inflation]
          "currency_crisis" [:reserves :external-debt :inflation]
          "inflation_crisis" [:inflation]
          "fiscal_consolidation" [:government-debt :external-debt :spread :balance]
          "coup" [:military-executive :electoral-democracy]}
         kt/decay-stocks))
  (is (= [0 1 2] (kt/decay-levels "sovereign_default")))
  (is (= [0] (kt/decay-levels "inflation_crisis@monthly")))
  (is (nil? (kt/decay-levels "war_onset"))))

(deftest the-decay's-interactions-held-tighter
  ;; t138: the stock-times-decay columns at a higher prior precision, the
  ;; decay itself and the inputs at the default; none without :precision
  (let [d (fn [u t] (if (= u "a1") 0.5 0.0))
        pen #(:penalties (:kind-theory (kt/with-theory sp row 2 %)))]
    (is (nil? (pen {:decay {:d d :levels [0]}})) "t137's: no penalties")
    (is (= [1.0 1.0 1.0 1.0 10.0] (pen {:decay {:d d :levels [0] :precision 10.0}})))
    (is (= (into (vec (repeat 11 1.0)) [1.0 10.0]) (pen {:shapes true :decay {:d d :levels [0] :precision 10.0}})))
    (is (= [1.0 1.0 1.0 1.0 1.0 10.0 10.0] (pen {:impute true :decay {:d d :levels [0 1] :precision 10.0}}))
        "the share missing, then d, then two interactions")))

(deftest the-decay's-interactions-without-its-level
  ;; t139: :level false leaves out the decay column, so the node's history
  ;; gauges keep the aftermath's level; the stocks times d stay
  (let [d (fn [u t] (if (= u "a1") 0.5 0.0))
        os (range 11 600 12)
        z0 (th/place-z (fn [u t] (x [u t])) countries os 20)
        t (nth os 30)
        sp2 (kt/with-theory sp row 2 {:decay {:d d :levels [0] :level false}})
        in (:inputs (:kind-theory sp2))]
    (is (= 3 (count (in "a1" t))))
    (is (< (Math/abs (- (* 0.5 (z0 "a1" t)) (nth (in "a1" t) 2))) 1e-12))
    (is (some? ((:offset sp2) :year "a1" t)))
    (is (= [1.0 1.0 1.0 10.0]
           (:penalties (:kind-theory (kt/with-theory sp row 2 {:decay {:d d :levels [0] :level false :precision 10.0}})))))))

(deftest theory-signs
  ;; bnm-7yg.6: :signs, one per raw input; a slope fit against its sign is
  ;; held at zero (bnm.theory/fits :signs); the imputed missing share and
  ;; the decay columns free
  (let [os (range 11 600 12) t (last os)]
    (testing "the planted slope is positive; signed against it, every country takes one offset"
      (let [sp2 (kt/with-theory sp row 2 {:signs [-1 0]})
            vals (set (map #(Math/round (* 1e6 ((:offset sp2) :year % t))) countries))]
        (is (= 1 (count vals)))
        (is (= [0 -1 0] (get-in sp2 [:kind-theory :signs])))))
    (testing "signed with it: as without"
      (is (= (map #((:offset (kt/with-theory sp row 2)) :year % t) countries)
             (map #((:offset (kt/with-theory sp row 2 {:signs [1 0]})) :year % t) countries))))
    (testing "the imputed missing share takes no sign"
      (is (= [0 1 0 0] (get-in (kt/with-theory sp row 2 {:signs [1 0] :impute true}) [:kind-theory :signs]))))
    (testing "refused with shapes, and when the signs do not match the inputs"
      (is (thrown? clojure.lang.ExceptionInfo (kt/with-theory sp row 2 {:signs [1 0] :shapes true})))
      (is (thrown? clojure.lang.ExceptionInfo (kt/with-theory sp row 2 {:signs [1]}))))))

(deftest sovereign-defaults-declare-their-signs
  ;; the theory's directions (Manasse and Roubini 2009; t109): debt,
  ;; inflation and US tightening raise the risk, reserves and growth lower it
  (is (= [1 -1 1 -1 1] (kt/signs "sovereign_default")))
  (is (nil? (kt/signs "war_onset"))))

(deftest theory-half-life
  ;; bnm-7yg.6: :half-life passed to bnm.theory/fits
  (let [os (range 11 600 12) t (last os)
        sp2 (kt/with-theory sp row 2 {:half-life 10})
        z0 (th/place-z (fn [u t] (x [u t])) countries os 20)
        off (th/offsets {:places countries :origins os :prior 1.0 :half-life 10
                         :inputs (fn [u t] [(z0 u t) nil])
                         :target (fn [u t] ((:target sp) :year u t))})]
    (is (< (Math/abs (- (get off ["a1" t]) ((:offset sp2) :year "a1" t))) 1e-12))
    (is (= 10 (get-in sp2 [:kind-theory :half-life])))
    (is (not= ((:offset sp2) :year "a1" t) ((:offset (kt/with-theory sp row 2)) :year "a1" t)))))

(deftest theory-half-life-level
  ;; bnm-7yg.6: :level :history passed to bnm.theory/fits with the half-life
  (let [os (range 11 600 12) t (last os)
        sp2 (kt/with-theory sp row 2 {:half-life 10 :level :history})
        z0 (th/place-z (fn [u t] (x [u t])) countries os 20)
        off (th/offsets {:places countries :origins os :prior 1.0 :half-life 10 :level :history
                         :inputs (fn [u t] [(z0 u t) nil])
                         :target (fn [u t] ((:target sp) :year u t))})]
    (is (< (Math/abs (- (get off ["a1" t]) ((:offset sp2) :year "a1" t))) 1e-12))
    (is (= :history (get-in sp2 [:kind-theory :level])))
    (is (not= ((:offset sp2) :year "a1" t) ((:offset (kt/with-theory sp row 2 {:half-life 10})) :year "a1" t)))))
