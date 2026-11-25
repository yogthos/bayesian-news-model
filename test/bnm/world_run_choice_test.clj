(ns bnm.world-run-choice-test
  "Pins the world run's true/false and enum options (flag-choice,
  enum-choice): values, refusals and their messages."
  (:require [bnm.world-run :as wr]
            [clojure.test :refer [deftest is]]))

(defn- refusal [f opts] (try (f opts) nil (catch clojure.lang.ExceptionInfo e [(ex-message e) (ex-data e)])))

(deftest flags
  (doseq [[f k] [[wr/real-lags-choice :real-lags] [wr/dated-credit-choice :dated-credit] [wr/regime-choice :regime]
                 [wr/fresh-inputs-choice :fresh-inputs] [wr/wdi-editions-choice :wdi-editions]
                 [wr/banking-spans-choice :banking-spans]]]
    (is (false? (f {})))
    (is (false? (f {k "false"})))
    (is (false? (f {k false})) "a false value is absent")
    (is (true? (f {k "true"})))
    (is (= [(str "bnm.world-run: --" (name k) " takes true or false, not yes") {}] (refusal f {k "yes"})))
    (is (= [(str "bnm.world-run: --" (name k) " takes true or false, not ") {}] (refusal f {k ""})))))

(deftest flags-with-dependencies
  (is (true? (wr/leading-choice {:leading "true" :leading-priors "true"})))
  (is (= ["bnm.world-run: --leading-priors true sets the priors of --leading true's gauges" {}]
         (refusal wr/leading-choice {:leading "x" :leading-priors "true"})) "the dependency is checked first")
  (is (= ["bnm.world-run: --leading-food2 takes true or false, not x" {}]
         (refusal wr/leading-food2-choice {:leading-food2 "x"})) "the value is checked first")
  (is (= ["bnm.world-run: --leading-food2 true adds to --leading true's gauges" {}]
         (refusal wr/leading-food2-choice {:leading-food2 "true"})))
  (is (true? (wr/frailty-gauges-choice {:frailty-gauges "true" :leading "true" :leading-priors "true"})))
  (is (= ["bnm.world-run: --next-quarter true replaces --fresh-frailty's quarter-now" {}]
         (refusal wr/next-quarter-choice {:next-quarter "true" :fresh-frailty "quarter-now"})))
  (is (false? (wr/next-quarter-choice {:next-quarter "false" :fresh-frailty "quarter-now"})))
  (is (some? (refusal wr/horizon-priors-choice {:horizon-priors "true" :next-quarter "true"}))))

(deftest enums
  (is (false? (wr/regime-clim-choice {})))
  (is (false? (wr/regime-clim-choice {:regime-clim "false" :regime "true"})))
  (is (true? (wr/regime-clim-choice {:regime-clim "true"})))
  (is (= :uncentred (wr/regime-clim-choice {:regime-clim "uncentred"})))
  (is (= ["bnm.world-run: --regime-clim takes true, uncentred or false, not on" {}]
         (refusal wr/regime-clim-choice {:regime-clim "on"})))
  (is (= ["bnm.world-run: --regime-clim and --regime true read the same regime twice" {}]
         (refusal wr/regime-clim-choice {:regime-clim "uncentred" :regime "true"})))
  (is (= :bank (wr/wide-credit-choice {:wide-credit "bank" :dated-credit "true"})))
  (is (= ["bnm.world-run: --wide-credit bank needs --dated-credit true" {}]
         (refusal wr/wide-credit-choice {:wide-credit "bank"})))
  (is (= ["bnm.world-run: --wide-credit takes true, bank or false, not all" {}]
         (refusal wr/wide-credit-choice {:wide-credit "all"}))))

(deftest banking-spans-reach-the-run's-catalogue
  ;; bnm-suj: with the flag, Ireland's 2012 (ESRB: 2008-09 to 2013-12) is busy
  (when (.exists (java.io.File. "data/derived/banking-spans.tsv"))
    (let [irl12 (+ (* 12 2012) 5)
          busy (fn [opts] (contains? (get-in (wr/run-catalogues opts) ["banking_crisis" :busy "IRL"]) irl12))]
      (is (false? (busy {})))
      (is (true? (busy {:banking-spans "true"}))))))

(deftest banking-spans-reach-the-spec-options
  ;; t127: the flag changed only run-catalogues' cats, which banking's
  ;; lattice never reads (lb/build makes its own); it must reach build-specs
  (is (true? (:banking-spans (wr/spec-opts {:banking-spans "true"} {}))))
  (is (not (:banking-spans (wr/spec-opts {} {}))))
  (is (true? (:leak-fixes (wr/spec-opts {:leak-fixes "true"} {}))))
  (is (= #{"coup"} (:fresh-frailty (wr/spec-opts {} {:fresh #{"coup"}})))))

(deftest the-fitted-aftermath-layers-are-withdrawn
  ;; t133: --aftermath and --aftermath-net read onsets before their
  ;; publication lag, dropped the fit's intercept and fit without the
  ;; node's base; --aftermath-gauges replaces them
  (is (nil? (wr/withdrawn-check {})))
  (doseq [flag [:aftermath :aftermath-net]]
    (is (= [(str "bnm.world-run: --" (name flag) " is withdrawn: its kernels read onsets before their publication lag (docs/CALIBRATION.md t133); use --aftermath-gauges") {}]
           (refusal wr/withdrawn-check {flag "coup"})))))

(deftest the-node's-aftermath-gauges
  ;; --aftermath-gauges (t133): the kernels as the year node's gauges
  (is (nil? (wr/aftermath-gauges-choice {} ["coup"])))
  (is (= ["coup" "war_onset"] (wr/aftermath-gauges-choice {:aftermath-gauges "all"} ["coup" "war_onset"])))
  (is (= ["bnm.world-run: --aftermath-gauges names a kind not run: nope" {}]
         (refusal #(wr/aftermath-gauges-choice % ["coup"]) {:aftermath-gauges "nope"})))
  (is (= ["coup"] (wr/aftermath-gauges-choice {:aftermath-gauges "coup"} ["coup" "war_onset"]))))

(deftest the-global-drivers-as-the-node's-gauges
  ;; --global-gauges (t134): the global layer's inputs as year gauges
  (is (nil? (wr/global-gauges-choice {} ["market_crash"])))
  (is (= ["market_crash"] (wr/global-gauges-choice {:global-gauges "market_crash"} ["market_crash" "coup"])))
  (is (= ["bnm.world-run: --global-gauges: the economic kinds only, not coup" {:kind "coup"}]
         (refusal #(wr/global-gauges-choice % ["coup"]) {:global-gauges "coup"})))
  (is (= ["bnm.world-run: --global-gauges names a kind not run: market_crash" {}]
         (refusal #(wr/global-gauges-choice % ["coup"]) {:global-gauges "market_crash"})))
  (is (= ["bnm.world-run: --global-gauges and --global-theory both read the global inputs at market_crash" {:kind "market_crash"}]
         (refusal #(wr/global-gauges-choice % ["market_crash"]) {:global-gauges "market_crash" :global-theory "market_crash"}))))

(deftest the-global-gauges-join-the-named-kinds
  (let [sp {:scales [{:name :year :per 12 :horizon 12}] :levels [[:country ["A" "B"]] [:world ["world"]]]
            :parent {"A" "world" "B" "world"} :start 0 :end 23
            :gauges (fn [_ _ _] [0.0]) :gauge-width {:year 1}}
        out (wr/with-global-gauges {"market_crash" sp "coup" sp} ["market_crash"] (fn [_ _] [1.0 nil]) 2 :stable)]
    (is (= 3 (get-in out ["market_crash" :gauge-width :year])))
    (is (= [1 2] (get-in out ["market_crash" :gauge-groups :year :global])))
    (is (= sp (out "coup")))
    (is (= {"coup" 1} (wr/with-global-gauges {"coup" 1} nil nil 0 :stable)))))

(deftest the-world's-stress-by-its-direction
  ;; --direction-gauges (t135): kinds whose year node reads BAA and the VIX
  (is (nil? (wr/direction-gauges-choice {} ["market_crash"])))
  (is (= ["market_crash"] (wr/direction-gauges-choice {:direction-gauges "market_crash"} ["market_crash" "coup"])))
  (is (= ["bnm.world-run: --direction-gauges: only kinds whose year node reads BAA and the VIX, not coup" {:kind "coup"}]
         (refusal #(wr/direction-gauges-choice % ["coup"]) {:direction-gauges "coup"})))
  (is (= ["bnm.world-run: --direction-gauges names a kind not run: growth_collapse" {}]
         (refusal #(wr/direction-gauges-choice % ["market_crash"]) {:direction-gauges "growth_collapse"}))))

(deftest the-direction-gauges'-scales
  ;; --direction-at (t136): the scales that read them, the year by default
  (is (= [:year] (wr/direction-at-choice {})))
  (is (= [:quarter-lead :quarter-w2] (wr/direction-at-choice {:direction-at "quarter-lead,quarter-w2" :direction-gauges "market_crash"})))
  (is (= ["bnm.world-run: --direction-at needs --direction-gauges" {}]
         (refusal wr/direction-at-choice {:direction-at "quarter-lead"}))))

(deftest the-theories'-stocks-decay-from-known-onsets
  ;; --theory-decay (t137)
  (is (nil? (wr/theory-decay-choice {})))
  (is (= ["coup"] (wr/theory-decay-choice {:theory-decay "coup" :kind-theory "coup,fiscal_consolidation"})))
  (is (= ["bnm.world-run: --theory-decay names a kind without a theory in --kind-theory: war_onset" {}]
         (refusal wr/theory-decay-choice {:theory-decay "war_onset" :kind-theory "coup"})))
  (is (= ["bnm.world-run: --theory-decay: no stocks declared for war_onset" {:kind "war_onset"}]
         (refusal wr/theory-decay-choice {:theory-decay "war_onset" :kind-theory "war_onset"})))
  (let [d (wr/onset-decay {"A" (sorted-set 100 200)} 18)]
    (is (= 0.0 (d "A" 117)) "the onset at 100 known at 118")
    (is (< (Math/abs (- (Math/pow 0.5 (/ 18 48.0)) (d "A" 118))) 1e-12) "time since the onset, once known")
    (is (< (Math/abs (- 0.5 (d "A" 148))) 1e-12) "halved 48 months after the onset")
    (is (< (Math/abs (- (Math/pow 0.5 (/ 117 48.0)) (d "A" 217))) 1e-12) "the onset at 200 not known until 218")
    (is (< (Math/abs (- (Math/pow 0.5 (/ 18 48.0)) (d "A" 218))) 1e-12) "then the latest known")
    (is (= 0.0 (d "B" 300)) "never")))

(deftest the-decay's-interaction-precision
  ;; --theory-decay-precision (t138)
  (is (nil? (wr/theory-decay-precision-choice {})))
  (is (= 10.0 (wr/theory-decay-precision-choice {:theory-decay "coup" :theory-decay-precision "10"})))
  (is (= ["bnm.world-run: --theory-decay-precision needs --theory-decay" {}]
         (refusal wr/theory-decay-precision-choice {:theory-decay-precision "10"})))
  (is (= ["bnm.world-run: --theory-decay-precision must be positive" {:p 0.0}]
         (refusal wr/theory-decay-precision-choice {:theory-decay "coup" :theory-decay-precision "0"}))))

(deftest the-decay's-level
  ;; --theory-decay-level (t139): true by default (t137's)
  (is (true? (wr/theory-decay-level-choice {})))
  (is (true? (wr/theory-decay-level-choice {:theory-decay "coup" :theory-decay-level "true"})))
  (is (false? (wr/theory-decay-level-choice {:theory-decay "coup" :theory-decay-level "false"})))
  (is (= ["bnm.world-run: --theory-decay-level needs --theory-decay" {}]
         (refusal wr/theory-decay-level-choice {:theory-decay-level "false"})))
  (is (= ["bnm.world-run: --theory-decay-level takes true or false, not no" {}]
         (refusal wr/theory-decay-level-choice {:theory-decay "coup" :theory-decay-level "no"}))))

(deftest the-year-decay's-stocks
  ;; --year-decay (docs/AFTERMATH-STOCKS.md): the stocks each kind's year
  ;; node reads against time since its last known onset
  (is (nil? (wr/year-decay-choice {} ["coup"])))
  (is (= ["conflict_onset"] (wr/year-decay-choice {:year-decay "conflict_onset"} ["conflict_onset" "coup"])))
  (is (= ["bnm.world-run: --year-decay: no stocks declared for market_crash" {:kind "market_crash"}]
         (refusal #(wr/year-decay-choice % ["market_crash"]) {:year-decay "market_crash"})))
  (is (= ["bnm.world-run: --year-decay names a kind not run: war_onset" {}]
         (refusal #(wr/year-decay-choice % ["coup"]) {:year-decay "war_onset"})))
  (is (= #{"banking_crisis" "conflict_onset" "war_onset" "violence_surge" "protest_surge" "food_price_shock" "growth_collapse"}
         (set (keys wr/year-decay-stocks))))
  (is (= {:ix [3 5 18 8] :level true} (wr/year-decay-layout "conflict_onset")) "physical-integrity, repression, soldiers-per-capita, military-executive")
  (is (= {:ix [24 23] :level true} (wr/year-decay-layout "food_price_shock")))
  (is (= {:ix [15 23] :level false} (wr/year-decay-layout "growth_collapse")) "t133's kernels carry the level")
  (is (= {:ix [0 1 2 6 31] :level false} (wr/year-decay-layout "banking_crisis")) "the stretches and loans to deposits; its own kernels carry the level"))

(deftest the-year-decay-joins-the-year-node
  (let [sp {:scales [{:name :year :per 12 :horizon 12}] :levels [[:country ["A" "B"]] [:world ["world"]]]
            :parent {"A" "world" "B" "world"} :start 0 :end 23
            :gauges (fn [_ u _] [2.0 (if (= u "A") 3.0 nil)]) :gauge-width {:year 2}}
        d (fn [u _] (if (= u "A") 0.5 0.0))
        out (wr/with-year-decay {"x" sp "y" sp} ["x"] {"x" {:ix [1] :level true}} {"x" d} :stable)
        row (wr/year-decay-row sp {:ix [1] :level true} d)]
    (is (= [0.5 1.5] (row "A" 23)) "d, then the stock times d")
    (is (= [0.0 0.0] (row "B" 23)) "a missing stock reads 0")
    (is (= [1.5] ((wr/year-decay-row sp {:ix [1] :level false} d) "A" 23)))
    (is (= [2 3] (get-in out ["x" :gauge-groups :year :decay])))
    (is (= sp (out "y")))))

(deftest the-year-decay-read-by-every-model
  ;; t142: --year-decay-core puts the decay columns in the year node's :core
  (let [sp {:scales [{:name :year :per 12 :horizon 12}] :levels [[:country ["A" "B"]] [:world ["world"]]]
            :parent {"A" "world" "B" "world"} :start 0 :end 23
            :gauges (fn [_ _ _] [2.0 3.0]) :gauge-width {:year 2} :core {:year [0]}}
        d (fn [_ _] 0.5)
        lay {"x" {:ix [1] :level true}}]
    (is (= [0 2 3] (get-in (wr/with-year-decay {"x" sp} ["x"] lay {"x" d} :stable true) ["x" :core :year])))
    (is (= [2 3] (get-in (wr/with-year-decay {"x" (dissoc sp :core)} ["x"] lay {"x" d} :stable true) ["x" :core :year])))
    (is (= [0] (get-in (wr/with-year-decay {"x" sp} ["x"] lay {"x" d} :stable) ["x" :core :year])) "t141's: not core"))
  (is (false? (wr/year-decay-core-choice {})))
  (is (true? (wr/year-decay-core-choice {:year-decay "coup" :year-decay-core "true"})))
  (is (= ["bnm.world-run: --year-decay-core needs --year-decay" {}]
         (refusal wr/year-decay-core-choice {:year-decay-core "true"}))))

(deftest the-year-decay's-level-alone
  ;; t143: --year-decay-form level, the aftermath's own level only (d), no
  ;; stock interactions; refused where the node already reads its onsets
  ;; decaying (growth collapses, banking)
  (is (= :stocks (wr/year-decay-form-choice {})))
  (is (= :level (wr/year-decay-form-choice {:year-decay "war_onset" :year-decay-form "level"})))
  (is (= ["bnm.world-run: --year-decay-form takes stocks or level, not x" {}]
         (refusal wr/year-decay-form-choice {:year-decay "war_onset" :year-decay-form "x"})))
  (is (= ["bnm.world-run: --year-decay-form needs --year-decay" {}]
         (refusal wr/year-decay-form-choice {:year-decay-form "level"})))
  (is (= {:ix [] :level true} (wr/year-decay-layout "conflict_onset" :level)))
  (is (= {:ix [3 5 18 8] :level true} (wr/year-decay-layout "conflict_onset" :stocks)))
  (is (= ["bnm.world-run: --year-decay-form level: growth_collapse's year node already reads its onsets decaying" {:kind "growth_collapse"}]
         (refusal #(wr/year-decay-layout % :level) "growth_collapse"))))

(deftest the-node's-gauges-take-each-catalogue's-lag
  (let [cats {"coup" {:onsets {"A" (sorted-set 100)} :known-lag 3} "food" {:onsets {"A" (sorted-set 100)} :known-lag 18}}
        sp {:scales [{:name :year :per 12 :horizon 12}] :levels [[:country ["A" "B"]] [:world ["world"]]]
            :parent {"A" "world" "B" "world"} :start 0 :end 23
            :gauges (fn [_ _ _] [0.0]) :gauge-width {:year 1}}
        out (wr/with-aftermath-gauges {"coup" sp "food" sp} ["food"] cats)]
    (is (= 5 (get-in out ["food" :gauge-width :year])))
    (is (= sp (out "coup")))
    (is (= {"coup" 1} (wr/with-aftermath-gauges {"coup" 1} nil cats)))))
