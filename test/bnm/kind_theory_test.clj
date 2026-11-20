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
    (is (thrown? Exception (kt/with-theory (assoc sp :offset-add (fn [_ _ _] 0.1)) row 2 {:add true})))))

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
    (is (thrown? Exception (choice {:kind-theory "banking_crisis"})) "banking has its own, --theory-offset")))

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
          "data/derived/trade-partners.tsv" "data/derived/onset-history.tsv"]
         kt/series-paths)))


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
  (let [out (get (kt/with-global-theory-row {"market_crash" sp} ["market_crash"] (fn [u t] (vec (repeat 12 (x [u t])))) {:shapes #{"market_crash"}}) "market_crash")]
    (is (fn? (:offset-add out)))
    (is (= 60 (count ((:inputs (:kind-theory out)) "a1" 371))) "twelve inputs, five columns each")))

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
