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

(deftest the-aftermath-layer's-kinds
  ;; --aftermath (reports/Crisis aftermath and trend reversal.md, rank 3)
  (is (nil? (wr/aftermath-choice {} ["coup" "war_onset"])))
  (is (= ["coup" "war_onset"] (wr/aftermath-choice {:aftermath "all"} ["coup" "war_onset"])))
  (is (= ["coup"] (wr/aftermath-choice {:aftermath "coup"} ["coup" "war_onset"])))
  (is (= [(str "bnm.world-run: --aftermath names a kind not run: nope") {}]
         (refusal #(wr/aftermath-choice % ["coup"]) {:aftermath "coup,nope"}))))

(deftest the-aftermath-layer-reads-every-kind's-onsets
  (let [cats {"coup" {:onsets {"A" (sorted-set 100 160)}} "war_onset" {:onsets {"A" (sorted-set 150)}}}]
    (is (= {"coup" {"A" [100 160]} "war_onset" {"A" [150]}} (wr/catalogue-onsets cats)))
    (let [sp {:scales [{:name :year :per 12 :horizon 12}] :levels [[:country ["A"]]] :start 0 :end 23
              :target (constantly nil) :offset (constantly nil)}
          out (wr/with-aftermath {"coup" sp "war_onset" sp} ["coup"] cats)]
      (is (contains? (out "coup") :aftermath))
      (is (not (contains? (out "war_onset") :aftermath)) "only the kinds named")
      (is (= sp (out "war_onset"))))
    (is (= {"coup" 1} (wr/with-aftermath {"coup" 1} nil cats)) "off: the specs as they were")))
