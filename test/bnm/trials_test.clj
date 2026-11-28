(ns bnm.trials-test
  "docs/TRIALS.md is the multiplicity ledger (bnm-7yg.8.4): every run
  registered in docs/CALIBRATION.md (\"Run tNNN\") has a line there, so the
  count of configurations tried on 2003-2022 cannot fall behind again."
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is]]))

(def ^:private id "t\\d{2,3}[a-z]?(?:-[a-z0-9]+)*")

(defn registered-runs
  "The run ids a text registers as \"Run tNNN\" or \"Runs tNNN\"."
  [text]
  (set (map second (re-seq (re-pattern (str "(?<![A-Za-z0-9_-])[Rr]uns? (" id ")")) text))))

(defn named-ids
  "Every whole run id a text names."
  [text]
  (set (re-seq (re-pattern (str "(?<![A-Za-z0-9_-])" id "(?![A-Za-z0-9_-])")) text)))

(deftest the-extraction-reads-whole-ids
  (is (= #{"t163" "t157-recal"} (registered-runs "Run t163 = x. Runs t157-recal and s2d-t51ref.")))
  (is (= #{"t51b-world-norel"} (named-ids "| t51b-world-norel | from s2d-t51ref |")) "s2d-t51ref is not t51r"))

(deftest every-registered-run-is-in-the-trials-ledger
  (let [missing (set/difference (registered-runs (slurp "docs/CALIBRATION.md"))
                                (named-ids (slurp "docs/TRIALS.md")))]
    (is (empty? missing) (str "add these to docs/TRIALS.md: " (sort missing)))))
