(ns bnm.ledger-test
  (:require [bnm.ledger :as lg]
            [bnm.sfc-rates-confirm :as sfc]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [bnm.benchmarks]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(defn- tmp-dir []
  (let [d (io/file (System/getProperty "java.io.tmpdir") (str "ledger-test-" (System/nanoTime)))]
    (.mkdirs d)
    (str d)))

(deftest quarters-and-dates-are-months
  (is (= (+ (* 12 2026) 11) (lg/quarter-end "2026-Q4")) "December 2026")
  (is (= (+ (* 12 2027) 2) (lg/quarter-end "2027-Q1")))
  (is (thrown? Exception (lg/quarter-end "2026-Q5")))
  (is (thrown? Exception (lg/quarter-end "2026Q4")))
  (is (= (+ (* 12 2026) 10) (lg/date-month "2026-11-11")) "November 2026")
  (is (thrown? Exception (lg/date-month "2026-13-01"))))

(deftest a-scale's-latest-origin-at-or-before-the-end
  (let [end (lg/quarter-end "2026-Q4")]
    (is (= end (lg/latest-origin :quarter-lead end)))
    (is (= end (lg/latest-origin :year end)))
    (is (= (+ (* 12 2024) 11) (lg/latest-origin :half-decade end)) "half-decades end in 1874, ..., 2024, 2029")
    (is (= (+ (* 12 2026) 8) (lg/latest-origin :quarter-w2 (+ (* 12 2026) 10))))
    (is (= (+ (* 12 2025) 11) (lg/latest-origin :year (+ (* 12 2026) 10))))))

(def lines
  ["quarter-lead\tAAA\t24320\t0.1\t"
   "quarter-lead\tAAA\t24323\t0.2\t"
   "quarter-w2\tAAA\t24323\t0.3\t"
   "year\tAAA\t24311\t0.4\tfalse"
   "year\tAAA\t24323\t0.5\t"
   "year\ts/x\t24323\t0.6\t"
   "half-decade\tAAA\t24299\t0.7\t"])

(deftest an-issue-holds-every-place-and-window-at-the-latest-origin
  (let [rows (lg/issue-rows "k" (map lg/parse-line lines) {:end 24323 :issue-month 24322})]
    (is (= 5 (count rows)) "one row per scale and place")
    (is (= #{24323 24299} (set (map :origin rows))))
    (testing "the window and whether it opened before the issue"
      (let [y (first (filter #(and (= :year (:scale %)) (= "AAA" (:place %))) rows))
            h (first (filter #(= :half-decade (:scale %)) rows))
            q (first (filter #(= :quarter-lead (:scale %)) rows))]
        (is (= [24323 24347] [(:lo y) (:hi y)]) "(Dec 2026, Dec 2028]")
        (is (false? (:opened y)))
        (is (= [24326 24335] [(:lo q) (:hi q)]) "quarters 2-4 after the origin")
        (is (true? (:opened h)) "the half-decade from Dec 2024 opened before the issue")
        (is (= "k" (:kind y)))
        (is (close? 0.5 (:p y)))))))

(deftest the-issue-file-round-trips
  (let [rows [{:kind "coup" :scale :year :place "AAA" :origin 24323 :lo 24323 :hi 24347 :opened false :p 0.125
               :clim 0.01 :last12 0.02 :share nil :pers -3.5 :wclim 0.05 :rate 0.03 :persist nil}]
        text (lg/rows->tsv rows)]
    (is (str/starts-with? text (str (str/join "\t" lg/columns) "\n")) "a header")
    (is (= rows (lg/tsv->rows text)))))

(deftest the-base-is-reproduced-or-refused
  (let [base ["year\tAAA\t24299\t0.1\tfalse" "year\tAAA\t24311\t0.2\t"]]
    (is (= {:rows 2 :differing 0} (lg/reproduction base (conj base "year\tAAA\t24323\t0.3\t") 24311)))
    (is (= {:rows 2 :differing 1} (lg/reproduction base ["year\tAAA\t24299\t0.1\tfalse" "year\tAAA\t24311\t0.21\t"] 24311)))
    (is (= {:rows 2 :differing 1} (lg/reproduction base ["year\tAAA\t24299\t0.1\tfalse"] 24311))
        "a base row the run lacks counts as differing")))

(deftest a-tampered-issue-is-refused
  (let [d (tmp-dir)
        f (str d "/forecasts.tsv")]
    (spit f "a\tb\n")
    (let [sha (sfc/sha256-file f)]
      (is (= sha (lg/check-file f sha)))
      (spit f "a\tc\n")
      (is (thrown-with-msg? Exception #"refusing" (lg/check-file f sha)))
      (is (thrown-with-msg? Exception #"refusing" (lg/check-file (str d "/missing.tsv") sha))))))

(deftest an-issue-is-dated-in-its-quarter-s-last-two-months
  ;; the registration: dated inside the quarter, refused outside its last two months
  (is (thrown? Exception (lg/check-issue-date "2026-Q4" "2026-10-31")) "October is the quarter's first month")
  (is (= 24322 (lg/check-issue-date "2026-Q4" "2026-11-11")))
  (is (= 24323 (lg/check-issue-date "2026-Q4" "2026-12-31")))
  (is (thrown? Exception (lg/check-issue-date "2026-Q4" "2027-01-02")))
  (is (thrown? Exception (lg/check-issue-date "2027-Q1" "2027-01-15"))))

(deftest the-code-checked-clean-is-everything-the-run-loads
  ;; resources are on the classpath (countries, provinces): an uncommitted
  ;; change there moves the run as surely as one in src
  (let [deps (read-string (slurp "deps.edn"))
        loaded (concat (:paths deps) (get-in deps [:aliases :tools :extra-paths]) ["deps.edn"])]
    (is (every? (set lg/code-paths) loaded) (str "not checked: " (vec (remove (set lg/code-paths) loaded))))))

(deftest every-read-inside-the-run-is-recorded
  (let [d (tmp-dir) a (str d "/a.txt") b (str d "/b.txt") c (str d "/c.txt")]
    (spit a "1") (spit b "2") (spit c "3")
    (let [[v opened] (lg/recording (fn [] (+ (count (slurp a)) (with-open [r (io/reader b)] (count (line-seq r)))
                                             (with-open [s (io/input-stream c)] (.read s) 0))))]
      (is (= 2 v))
      (is (= #{a b c} opened) "slurp, io/reader and io/input-stream (the gzip readers)"))
    (is (= "1" (slurp a)) "slurp is itself again after")))

(deftest the-base-of-record-is-the-pinned-one
  (is (= 13 (count lg/base-files)))
  (is (every? #(re-matches #"[0-9a-f]{64}" %) (vals lg/base-files)))
  (let [d (tmp-dir)]
    (.mkdirs (io/file d "coup"))
    (spit (str d "/coup/forecasts.tsv") "year\tAAA\t24299\t0.1\tfalse\n")
    (let [sha (sfc/sha256-file (str d "/coup/forecasts.tsv"))]
      (is (= {"coup" sha} (lg/check-base d {"coup" sha})))
      (is (thrown-with-msg? Exception #"refusing" (lg/check-base d {"coup" (apply str (repeat 64 "0"))})))
      (is (thrown-with-msg? Exception #"refusing" (lg/check-base d {"war_onset" sha})) "missing")))
  (when (.exists (io/file "data/runs" lg/base "coup" "forecasts.tsv"))
    (is (= lg/base-files (lg/check-base (str "data/runs/" lg/base) lg/base-files)) "the files on disk are the base's")))

(deftest the-base's-inputs-are-the-ones-the-issue-reads
  ;; t80 (as t53, t65 and t71) reads the onsets run r7-at-fx, r6-at-fx's command on the
  ;; series.tsv with FAOSTAT's prices at their publication lag (bnm-56c.31);
  ;; a command naming a run whose preds are not an input would be refused
  ;; as a stray read at issue time
  (is (= (set lg/inputs) (set (keys lg/base-inputs))))
  (let [args (lg/command "ledger-x" 24323)
        known (second (drop-while #(not= "--known" %) args))]
    (is (= lg/known known))
    (is (some #{(str "data/runs/" known "/preds.tsv")} lg/inputs))
    (is (not-any? #{"data/runs/r6-at/preds.tsv" "data/runs/r6-at-8ul/preds.tsv" "data/runs/r6-at-fx/preds.tsv"} lg/inputs)
        "r6-at was fit on the pre-fix catalogues, r6-at-8ul and r6-at-fx on leaky series.tsv")))

(deftest the-base-of-record-is-t161-with-sovereign-defaults'-theory-memory
  ;; twenty-fourth note to L1, 27 November 2026: t160's command with
  ;; sovereign defaults' theory rows weighted at a ten-year half-life
  ;; (--theory-half-life sovereign_default:10, t161, a pass on the fold
  ;; rule at a pooled AUC cost of 0.027); the other twelve t160's.
  (is (= "t161" lg/base))
  (is (= "c660445e082e0cbce423750894072aad09e06dbc0909a31f1d993fdfab760f83" (lg/base-files "sovereign_default")) "t161's sovereign defaults")
  (is (not= "2ec6b77fabecda209925d4a573a6b5492c17dff86721cada36c504f388c2199f" (lg/base-files "sovereign_default")) "t160's")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "sovereign_default:10" (opt "--theory-half-life")))
    (is (nil? (opt "--theory-signs")) "signs not run")))

(deftest the-base-before-was-t160-with-the-echo-cut
  ;; twenty-third note to L1, 26 November 2026: t157-base's command with
  ;; conflict onsets' year averaging at a five-year half-life (t158) and
  ;; the year's echo through the quarters cut (:up-net-of-downscale, t159)
  ;; at banking crises, conflict onsets, coups, violence surges and war
  ;; onsets (t160); the other eight t157-base's. The earlier notes' pins on
  ;; those five read not= since.
  (is (str/starts-with? lg/base "t1"))
  (is (= "a8efdfe199ead101071d334b15bafb76d561f3245b265c9647e3c5080952e227" (lg/base-files "banking_crisis")) "t159's banking crises")
  (is (= "0cd01bef5d08f184ef9b47f740fb37f9cbbca08e214f269b789627e4cf4bbbbf" (lg/base-files "conflict_onset")) "t160's conflict onsets")
  (is (= "b17a80aabf5a01ce0a1c0b4ae3a75312a5e4ed0915f42e323551b50894bd751d" (lg/base-files "coup")) "t159's coups")
  (is (= "4ad439a5f0ad1e05c3aa4d29836a7d14839d708b1eeaba64d7653f38a38b15ea" (lg/base-files "violence_surge")) "t159's violence surges")
  (is (= "9c6f24bb523e9ce0743a951fccd5af0d191b9a939ea8e650064a023514ecbd5a" (lg/base-files "war_onset")) "t159's war onsets")
  (is (= "0013aef32c3b6e35da62abedffdd17bff3ad60ff61d46ce2394f6199adf72b3c" (lg/base-files "inflation_crisis")) "inflation crises t157-base's")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))
        ks (read-string (opt "--kind-set"))]
    (is (not (:up-net-of-downscale (read-string (opt "--set")))) "the cut per kind, not lattice-wide")
    (is (= #{"banking_crisis" "conflict_onset" "coup" "violence_surge" "war_onset"}
           (set (keep (fn [[k m]] (when (:up-net-of-downscale m) k)) ks))))
    (is (= 5 (get-in ks ["conflict_onset" :forget-half-life])))
    (is (= 10 (get-in ks ["violence_surge" :forget-half-life])))
    (is (= {:rhos [0.0]} (get-in ks ["banking_crisis" :frailty])))))

(deftest the-base-before-was-t157-with-banking's-offset-outside-the-panel
  ;; twenty-second note to L1, 26 November 2026: t150-base's command with
  ;; banking's theory offset outside the JST panel only (t156, a pass by
  ;; dominance) and inflation crises' year country rows recalibrated by a
  ;; rolling intercept (t157-recal, a pass); the other eleven t150-base's.
  ;; The earlier notes' pins on banking read not= since.
  (is (str/starts-with? lg/base "t1"))
  (is (not= "9f0219af0c38f1d0735e9e221607c1a1e5ae6698ff12a071f0b10957d9eb232a" (lg/base-files "banking_crisis")) "t156's banking crises")
  (is (= "0013aef32c3b6e35da62abedffdd17bff3ad60ff61d46ce2394f6199adf72b3c" (lg/base-files "inflation_crisis")) "t157-recal's inflation crises")
  (is (not= "7c9ab7cc0785543cb0ec8c27f58f71e8ed9491c33239a8e5eb4be68d84c8f649" (lg/base-files "conflict_onset")) "conflict onsets t150-base's")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "true" (opt "--theory-outside-panel")))
    (is (= "true" (opt "--theory-offset")))
    (is (= "inflation_crisis" (opt "--recalibrate-intercept")))))

(deftest the-base-before-was-t150-with-ucdp's-release-calendar
  ;; twenty-first note to L1, 25 November 2026: t149-base's command with
  ;; conflict and war onsets known at UCDP's annual release (t150, a pass at
  ;; both; it reaches every kind but banking through the others' counts).
  ;; The earlier notes' pins on kinds other than banking read not= since:
  ;; the calendar replaced every one of them.
  (is (str/starts-with? lg/base "t1"))
  (is (not= "7c9ab7cc0785543cb0ec8c27f58f71e8ed9491c33239a8e5eb4be68d84c8f649" (lg/base-files "conflict_onset")) "t150's conflict onsets")
  (is (not= "678b8a6f7c839cafaf3eb859386393d145d840fb02330f085c818f1de46d2478" (lg/base-files "war_onset")) "t150's war onsets")
  (is (not= "0cf68a981f20cdf1cebc40afb0e37752bd109ce81ee4a22760002737ffeb77c1" (lg/base-files "violence_surge")) "t149's memory with t150's calendar")
  (is (not= "d3bb74dc08a93f37b62cd50587ece6ac5dd524e9349a0817fcdb1b50f6be097b" (lg/base-files "banking_crisis")) "banking unchanged")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "conflict_onset,war_onset" (opt "--release-calendar")))
    (is (nil? (opt "--release-calendar-scope")) "t151's scope not adopted")))

(deftest the-base-before-was-t149-with-violence-surges'-memory
  ;; twentieth note to L1, 25 November 2026: t140-base's command with
  ;; violence surges' averaging memory at a ten-year half-life (t149, a pass
  ;; on the fold rule; t145's post-onset level not adopted)
  (is (str/starts-with? lg/base "t1"))
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "coup,inflation_crisis,currency_crisis" (opt "--theory-decay")))
    (is (nil? (opt "--year-decay")) "t145's level not adopted")
    (is (= {"banking_crisis" {:rhos [0.0]} "currency_crisis" 10 "violence_surge" 10}
           (let [ks (read-string (opt "--kind-set"))]
             {"banking_crisis" (get-in ks ["banking_crisis" :frailty])
              "currency_crisis" (get-in ks ["currency_crisis" :forget-half-life])
              "violence_surge" (get-in ks ["violence_surge" :forget-half-life])})) "kept since; t160 adds the cut")))

(deftest the-base-before-was-t140-with-currency-crises'-decay-and-memory
  ;; nineteenth note to L1, 23 November 2026: t137-base's command with
  ;; currency crises' theory stocks decaying and their averaging's memory at
  ;; a ten-year half-life (t140, a pass on the fold rule)
  (is (str/starts-with? lg/base "t1"))
  (is (not= "e5cf1d125c978744c7bd6df9f8432ebd709f1fff6dfed27e2ba057825c5c1ead" (lg/base-files "currency_crisis")) "t140's currency crises")
  (is (not= "ae0ab8ce83321e0b36aac3897fb74875816116aea8af237b3304974ef46cb420" (lg/base-files "coup")) "coups t137-base's")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "coup,inflation_crisis,currency_crisis" (opt "--theory-decay")))
    (is (= {:forget-half-life 10} (get (read-string (opt "--kind-set")) "currency_crisis")))))

(deftest the-base-before-was-t137-with-coups'-and-inflation's-stocks-decaying
  ;; eighteenth note to L1, 23 November 2026: t136-base's command with
  ;; coups' and inflation crises' theory stocks read against time since
  ;; the last known onset (t137, each a pass on the fold rule)
  (is (str/starts-with? lg/base "t1"))
  (is (not= "ae0ab8ce83321e0b36aac3897fb74875816116aea8af237b3304974ef46cb420" (lg/base-files "coup")) "t137's coups")
  (is (not= "7882321e27116b1c13e39e3c02e72cbec622f59c2179ce013792232df2cce904" (lg/base-files "inflation_crisis")) "t137's inflation crises")
  (is (not= "4c85afea2f6881c3303b9f48f506ea0f907c518e2a6f1d12025e8321a3432e69" (lg/base-files "market_crash")) "market crashes t136-base's")
  (is (not= "4a964aa054f0ba3b867c6fa01bf15d6e107a97721141c685e8a82b8ea5f59111" (lg/base-files "sovereign_default")) "sovereign defaults unchanged (failed t137)")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (re-find #"^coup,inflation_crisis" (opt "--theory-decay")))))

(deftest the-base-before-was-t136-with-market-crashes'-direction-gauges
  ;; seventeenth note to L1, 22 November 2026: t133-base's command with
  ;; the world's stress by its direction in market crashes' quarter nodes
  ;; (t136, a pass on the fold rule)
  (is (str/starts-with? lg/base "t1"))
  (is (not= "4c85afea2f6881c3303b9f48f506ea0f907c518e2a6f1d12025e8321a3432e69" (lg/base-files "market_crash")) "t136's market crashes")
  (is (not= "2a6ff8f0ed9c14c6ec94be0e2157d0b1df7559dc0c7b3cd3bed3a6744105d421" (lg/base-files "growth_collapse")) "growth collapses t133-base's")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "market_crash" (opt "--direction-gauges")))
    (is (= "quarter-lead,quarter-w2" (opt "--direction-at")))
    (is (= "market_crash" (opt "--global-theory")) "the global layer stays (t134)")))

(deftest the-base-before-was-t133-with-growth-collapses'-onset-gauges
  ;; sixteenth note to L1, 22 November 2026: t125-base's command with the
  ;; onset kernels from known onsets as year-node gauges at growth
  ;; collapses only (t133, a pass by dominance)
  (is (str/starts-with? lg/base "t1"))
  (is (not= "2a6ff8f0ed9c14c6ec94be0e2157d0b1df7559dc0c7b3cd3bed3a6744105d421" (lg/base-files "growth_collapse")) "t133's growth collapses")
  (is (not= "4a964aa054f0ba3b867c6fa01bf15d6e107a97721141c685e8a82b8ea5f59111" (lg/base-files "sovereign_default")) "sovereign defaults t125-base's")
  (is (not= "fe93fb09db109974a5b79a2f549279ee4a85b9f26123befff3daae98c1c35f42" (lg/base-files "market_crash")) "market crashes unchanged, then t136's (seventeenth note)")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "growth_collapse" (opt "--aftermath-gauges")))))

(deftest t131-base-is-withdrawn
  ;; fifteenth note to L1, 22 November 2026: t131-base's onset kernels read
  ;; onsets before their catalogues' publication lag (docs/CALIBRATION.md
  ;; t133); the base of record was t125-base again
  (is (not= "t131-base" lg/base))
  (is (not-any? #{"--aftermath" "--aftermath-net"} (lg/command "ledger-x" 24323)))
  (is (not= "e3828c32699e9f4acb84d2e9c2fd92e890f419ee4e302d52ef4ced39c0987442" (lg/base-files "conflict_onset")) "t125-base's, not t130's"))

(deftest the-base-before-was-t125-with-sovereign-defaults-on-filled-inputs
  ;; note to L1, 20 November 2026 (thirteenth): t124-base's command with
  ;; sovereign defaults' theory on filled inputs (t125, a pass by dominance)
  (is (str/starts-with? lg/base "t1"))
  (is (not= "4a964aa054f0ba3b867c6fa01bf15d6e107a97721141c685e8a82b8ea5f59111" (lg/base-files "sovereign_default")) "t125's sovereign defaults")
  (is (not= "fe93fb09db109974a5b79a2f549279ee4a85b9f26123befff3daae98c1c35f42" (lg/base-files "market_crash")) "market crashes unchanged, then t136's (seventeenth note)")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "inflation_crisis,sovereign_default" (opt "--impute")))))

(deftest the-base-before-was-t124-with-tighter-driver-priors
  ;; note to L1, 20 November 2026 (twelfth): t120-base's command with the
  ;; market-crash global layer's world drivers at 10x prior precision
  ;; (t124, a pass by dominance under the fold rule as amended that day)
  (is (str/starts-with? lg/base "t1"))
  (is (not= "fe93fb09db109974a5b79a2f549279ee4a85b9f26123befff3daae98c1c35f42" (lg/base-files "market_crash")) "t124's market crashes, replaced by t136's (seventeenth note)")
  (is (not= "11ec93fe0c0b8820b7d05bca7b99d3e346c0214a5c0630dd02101a4826ceca4d" (lg/base-files "fiscal_consolidation")) "fiscal unchanged")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "market_crash" (opt "--global-theory")))
    (is (= "10" (opt "--global-driver-precision")))))

(deftest the-base-before-was-t120-with-the-fiscal-theory
  ;; note to L1, 20 November 2026 (eleventh): t119-base's command with
  ;; fiscal consolidations' shaped theory (t120); growth collapses' and
  ;; banking's, which failed, left out
  (is (not= "11ec93fe0c0b8820b7d05bca7b99d3e346c0214a5c0630dd02101a4826ceca4d" (lg/base-files "fiscal_consolidation")) "t120's fiscal consolidations")
  (is (not= "147e529a629df82a2f3205b7e1de464ec260c1dea9c8f893e1042a285066da1c" (lg/base-files "growth_collapse")) "growth collapses unchanged, then t133's (sixteenth note)")
  (is (not= "d3bb74dc08a93f37b62cd50587ece6ac5dd524e9349a0817fcdb1b50f6be097b" (lg/base-files "banking_crisis")) "banking unchanged")
  (is (= "a9f63ae68d5a32dc6da723da1de8f1591576a620ba5750379c12f546444f960b" (lg/base-inputs "data/derived/fiscal-series.tsv")))
  (is (some #{"data/derived/fiscal-series.tsv"} lg/inputs))
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "sovereign_default,inflation_crisis@monthly,currency_crisis,coup,fiscal_consolidation" (opt "--kind-theory")))
    (is (= "currency_crisis,coup,fiscal_consolidation" (opt "--shapes")))))

(deftest the-base-before-was-t119-with-the-coup-theory
  ;; note to L1, 20 November 2026 (tenth): t118-base's command with coups'
  ;; shaped theory (t119); war onsets' theory, which failed, left out
  (is (not= "ec86c1a3afc93bf2aaf44e9c3a1aaa41c06868ab00a5a362970eddbff2c72f13" (lg/base-files "coup")) "t119's coups, replaced by t137's (eighteenth note)")
  (is (not= "25fafb6577491beb364e3809352ea2fb38bec4cc416b71596abba0f77e9c70b1" (lg/base-files "war_onset")) "war onsets unchanged")
  (is (= "f6aa7491f6d6da849e1d97e227c0d3c621aed64e617352520b38f8d1bdf274b6" (lg/base-inputs "data/derived/onset-history.tsv")))
  (is (some #{"data/derived/onset-history.tsv"} lg/inputs))
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (str/starts-with? (opt "--kind-theory") "sovereign_default,inflation_crisis@monthly,currency_crisis,coup"))
    (is (str/starts-with? (opt "--shapes") "currency_crisis,coup"))))

(deftest the-base-before-was-t118-with-fresher-inflation-reads
  ;; note to L1, 19 November 2026 (ninth): t116-base's command with
  ;; inflation crises' theory on the freshest inflation read (t117)
  (is (not= "d5f9ba93199f0028ec4a98706a486cab25ac684d2961767d4e00964def634aa4" (lg/base-files "inflation_crisis")) "t117's inflation crises, replaced by t137's (eighteenth note)")
  (is (str/starts-with? (second (drop-while #(not= "--kind-theory" %) (lg/command "ledger-x" 24323)))
                        "sovereign_default,inflation_crisis@monthly,currency_crisis")))

(deftest the-base-before-was-t116-with-the-global-layer-and-filled-inputs
  ;; note to L1, 19 November 2026 (eighth): t111-base's command with the
  ;; global layer for market crashes (--global-theory, t113) and inflation
  ;; crises' theory reading filled inputs (--impute, t114), and currency
  ;; crises' re-specified, shaped theory (--shapes, t115)
  (is (not= "fec3f60f1738a3787a09b8c058a954f02d6a11126807775e41d0d10542bbc105" (lg/base-files "currency_crisis")) "t115's currency crises, replaced by t140's (nineteenth note)")
  (is (some #{"data/derived/rer-annual.tsv"} lg/inputs))
  (is (some #{"data/fast/series.tsv"} lg/inputs))
  (is (not= "b93e9f952d9c1312d6c11ce7d9c593e23e34633f4b2932ff00bd8dff01f1a36e" (lg/base-files "market_crash")) "t113's market crashes, replaced by t124's (twelfth note)")
  (is (not= "6ac298a0d84075e52a5e20533a4665895d7655447ff50e807fb73edf85eee72b" (lg/base-files "sovereign_default")) "t111's, replaced by t125's (thirteenth note)")
  (is (some #{"data/derived/global-monthly.tsv"} lg/inputs) "the global drivers are an input of record")
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (str/starts-with? (opt "--shapes") "currency_crisis"))
    (is (= "market_crash" (opt "--global-theory")))
    (is (str/starts-with? (opt "--impute") "inflation_crisis"))))

(deftest the-base-before-was-t111-with-two-kinds'-theories
  ;; note to L1, 19 November 2026 (seventh): t104-base's command with the
  ;; theory offsets of sovereign defaults and inflation crises
  ;; (--kind-theory, docs/CALIBRATION.md t110, bnm-1dw)
  (is (not= "6ac298a0d84075e52a5e20533a4665895d7655447ff50e807fb73edf85eee72b" (lg/base-files "sovereign_default"))
      "t110's sovereign defaults, replaced by t125's (thirteenth note)")
  (let [args (lg/command "ledger-x" 24323)]
    (is (str/starts-with? (second (drop-while #(not= "--kind-theory" %) args)) "sovereign_default,inflation_crisis")
        "t111's two theories stay in the command")))

(deftest the-base-before-was-t104-with-banking's-theory
  ;; note to L1, 18 November 2026 (sixth): t80-base's command with banking's
  ;; theory offset reading the world's bank funding and reserves, the year
  ;; row's ratios held through recessions, and banking's frailty at rho 0
  ;; (docs/CALIBRATION.md t100-t104, bnm-ris, bnm-5lr). t80-base (fifth):
  ;; t71-stack's with the banking node's credit from the BIS (--dated-credit)
  (is (not= "d3bb74dc08a93f37b62cd50587ece6ac5dd524e9349a0817fcdb1b50f6be097b" (lg/base-files "banking_crisis"))
      "banking is t104-rows-rho0's, the only kind the options reach")
  (is (not= "e3828c32699e9f4acb84d2e9c2fd92e890f419ee4e302d52ef4ced39c0987442" (lg/base-files "conflict_onset"))
      "the other twelve are t80-base's (t71-stack's)")
  (is (= "r7-at-fx" lg/known))
  (let [args (lg/command "ledger-x" 24323)
        opt (fn [k] (second (drop-while #(not= k %) args)))]
    (is (= "true" (opt "--without-relations")))
    (is (= "true" (opt "--leak-fixes")))
    (is (= "true" (opt "--pwt-stale")))
    (is (= "true" (opt "--lag-stale")))
    (is (= "stable" (opt "--gauge-std")))
    (is (= "true" (opt "--leading")))
    (is (= "true" (opt "--leading-priors")))
    (is (= "true" (opt "--real-lags")))
    (is (= "catalogue" (opt "--at-risk")))
    (is (= "history" (opt "--lead-std")))
    (is (= "true" (opt "--frailty-gauges")))
    (is (= "true" (opt "--dated-credit")))
    (is (= "true" (opt "--theory-offset")))
    (is (= "true" (opt "--theory-world")))
    (is (= "rows" (opt "--recession-hold")))
    (is (= {:rhos [0.0]} (get-in (read-string (opt "--kind-set")) ["banking_crisis" :frailty])) "banking's, with currency crises' memory since t140 and the cut since t160")
    (is (nil? (opt "--clim-prior")) "bnm-56c.26 not adopted")
    (is (= {:learn-offsets :mixture :flow-std-from :resolved :clim-learn :current :frailty {}
            :discount-from :data :flow-std :stable :evidence-at :issue-joint}
           (read-string (opt "--set")))
        "t80-base's options, t71-stack's and t65-gauges's; the lattice's default is the registered issues' code")
    (testing "the command written to a manifest reads back as the same arguments"
      (is (= (assoc args 1 "r") (lg/manifest-args (lg/command-line args) "r"))))))

(deftest the-previous-issue-is-rerun-with-its-own-command
  ;; the first issue ran without --known (r6-at); the corrected issue names
  ;; r6-at-8ul: each is rerun with the command in its own manifest
  (is (= ["--out" "ledger-2026-Q4-repro" "--without-relations" "true" "--end" "24323"]
         (lg/manifest-args "jolt -A:tools -M -m bnm.world-run --out ledger-2026-Q4 --without-relations true --end 24323"
                           "ledger-2026-Q4-repro")))
  (is (= ["--out" "r" "--without-relations" "true" "--end" "24323" "--known" "r6-at-8ul"]
         (lg/manifest-args "jolt -A:tools -M -m bnm.world-run --out ledger-2026-Q4-r1 --without-relations true --end 24323 --known r6-at-8ul"
                           "r")))
  (is (= ["--out" "r" "--set" "{:learn-offsets :mixture}" "--known" "r6-at-fx"]
         (lg/manifest-args "jolt -A:tools -M -m bnm.world-run --out x --set '{:learn-offsets :mixture}' --known r6-at-fx" "r"))
      "a quoted argument with spaces is one argument")
  (is (thrown-with-msg? Exception #"refusing" (lg/manifest-args "jolt -A:tools -M -m bnm.world-run --out x --set '{:a 1}" "r"))
      "an unclosed quote")
  (is (thrown-with-msg? Exception #"refusing" (lg/manifest-args "jolt -A:tools -M -m bnm.onsets --name x" "r"))
      "not a world run")
  (is (thrown-with-msg? Exception #"refusing" (lg/manifest-args "jolt -A:tools -M -m bnm.world-run --without-relations true" "r"))
      "no --out to replace")
  (when (.exists (io/file "registered/ledger/2026-Q4/manifest.edn"))
    (is (= ["--out" "r" "--without-relations" "true" "--end" "24323"]
           (lg/manifest-args (:command (lg/read-manifest "2026-Q4")) "r")))))

(deftest every-registered-issue-is-intact
  (doseq [[q {:keys [files]}] lg/issued
          [n sha] files]
    (is (= sha (sfc/sha256-file (str "registered/ledger/" q "/" n))) (str q "/" n))))

(deftest a-quarter's-windows-count-once
  ;; revisions of a quarter forecast the same windows: one carries the
  ;; verdicts, the others are reported beside it (sixth note to L1)
  (doseq [[q ids] (group-by lg/issue-quarter (keys lg/issued))]
    (is (= 1 (count (filter #(= :primary (:role (lg/issued %))) ids))) q))
  (is (= :primary (:role (lg/issued "2026-Q4-r2"))))
  (is (= "2026-Q4-r2" (lg/previous-issue lg/issued "2027-Q1"))))

(deftest every-registered-scanner-ranking-is-intact
  (doseq [[q files] lg/scanners
          [n sha] files]
    (is (= sha (sfc/sha256-file (str "registered/ledger/" q "/" n))) (str q "/" n))))

(deftest a-window-is-scored-only-once-published
  ;; amendment L1: published when closed and covered to the window's end at
  ;; every country with a counted row the catalogue follows past the origin
  (let [rows [{:place "AAA" :origin 24323 :hi 24347} {:place "BBB" :origin 24323 :hi 24347}
              {:place "CCC" :origin 24323 :hi 24347}]
        cat {:through {"AAA" 24347 "BBB" 24350 "CCC" 24000}}]
    (is (true? (lg/published? cat rows 24360)) "CCC stopped before the origin: left out, not waited for")
    (is (false? (lg/published? cat rows 24347)) "the window has not closed by the scoring month")
    (is (false? (lg/published? (assoc-in cat [:through "AAA"] 24340) rows 24360))
        "a partial update: AAA followed past the origin but not to the window's end")
    (is (false? (lg/published? (assoc-in cat [:through "AAA"] 24323) rows 24360))
        "followed to the origin itself counts as followed")
    (is (false? (lg/published? {:through {}} rows 24400)) "no coverage")
    (is (false? (lg/published? cat [] 24400)) "no rows")))

(deftest a-kind-not-in-the-issue-is-refused-as-such
  (is (= ["coup"] (lg/check-kinds ["coup"] ["coup" "war_onset"])))
  (is (thrown-with-msg? Exception #"not in the issue" (lg/check-kinds ["coups"] ["coup" "war_onset"])))
  (is (= ["coup" "war_onset"] (lg/check-kinds nil ["coup" "war_onset"])) "all of them by default"))

(deftest scoring-a-kind-and-a-scale
  ;; four countries; AAA has the onset; hard rows only where no onset in ten years
  (let [rows [{:place "AAA" :p 0.4 :clim 0.1 :last12 0.1 :share 0.2 :pers -1.0 :wclim 0.1 :y true :hard true}
              {:place "BBB" :p 0.1 :clim 0.2 :last12 0.1 :share 0.1 :pers -2.0 :wclim 0.2 :y false :hard true}
              {:place "CCC" :p 0.1 :clim 0.1 :last12 0.1 :share nil :pers -3.0 :wclim 0.3 :y false :hard true}
              {:place "DDD" :p 0.2 :clim 0.1 :last12 0.1 :share 0.1 :pers -1000.0 :wclim 0.0 :y false :hard false}]
        s (lg/score-cell rows {:k 1})]
    (is (= 4 (:n s)))
    (is (= 1 (:onsets s)))
    (is (close? (/ (+ 0.36 0.01 0.01 0.04) 4) (get-in s [:brier :p])))
    (is (close? (- 1.0 (/ (get-in s [:brier :p]) (get-in s [:brier :clim]))) (get-in s [:skill :brier :clim])))
    (testing "a benchmark missing on a row is compared on the rows it has"
      (is (= 3 (get-in s [:n-of :share]))))
    (testing "the watch list: top-k recall on hard rows, the list from each source's own ranking"
      (is (close? 1.0 (get-in s [:recall :hard :p])) "AAA tops the base's hard list")
      (is (close? 0.0 (get-in s [:recall :hard :wclim])) "climatology lists CCC")
      (is (close? 1.0 (get-in s [:recall :hard :pers])))
      (is (close? (/ 1.0 3) (get-in s [:recall :hard :last12])) "three tied for one slot"))))

(deftest an-issue-is-scored-on-the-rows-its-command-holds-at-risk
  ;; t71-stack's --at-risk catalogue (bnm-96j): the base is scored only where
  ;; the catalogue's rule could date an onset, so an issue is scored with the
  ;; catalogues its own command built, and an earlier issue with its own
  (is (= "catalogue" (:at-risk (lg/issue-options (lg/command-line (lg/command "r" 24323))))))
  (is (nil? (:at-risk (lg/issue-options "jolt -A:tools -M -m bnm.world-run --out ledger-2026-Q4 --without-relations true --end 24323"))))
  (is (= "24323" (:end (lg/issue-options "jolt -A:tools -M -m bnm.world-run --out x --end 24323"))))
  (let [cat {:onsets {"AAA" (sorted-set 24300) "BBB" (sorted-set)} :busy {}
             :from {"AAA" 20000 "BBB" 20000} :through {"AAA" 24400 "BBB" 24400}}
        rows [{:place "AAA" :origin 24323 :lo 24323 :hi 24335} {:place "BBB" :origin 24323 :lo 24323 :hi 24335}]]
    (is (= ["AAA" "BBB"] (map :place (lg/score-rows cat rows))) "no rule: both rows at risk")
    (is (= ["BBB"] (map :place (lg/score-rows (assoc cat :grid [12 0] :refractory 60) rows)))
        "AAA's onset 23 months before: the rule cannot date another within 60")
    (is (every? #(false? (:y %)) (lg/score-rows cat rows)))
    (is (= [false true] (map :hard (lg/score-rows cat rows))) "AAA had an onset in the ten years to the origin")))

;; --- amendment L1: reproduction on the previous issue's archived inputs ----------------

(deftest issue-ids-name-their-quarter
  (is (= "2026-Q4" (lg/issue-quarter "2026-Q4")))
  (is (= "2026-Q4" (lg/issue-quarter "2026-Q4-r1")) "a corrected issue")
  (is (thrown? Exception (lg/issue-quarter "2026-Q4-x")))
  (is (thrown? Exception (lg/issue-quarter "2026-Q4r1"))))

(deftest the-previous-issue-is-the-latest-primary-one-before
  (let [one {"2026-Q4" {:role :reported}}
        two (assoc one "2026-Q4-r1" {:role :primary})]
    (is (nil? (lg/previous-issue {} "2026-Q4")) "the first issue has none")
    (is (= "2026-Q4" (lg/previous-issue one "2026-Q4-r1")) "the corrected issue reproduces the first")
    (is (= "2026-Q4-r1" (lg/previous-issue two "2027-Q1")) "the next quarter reproduces the primary one")
    (is (= "2027-Q1" (lg/previous-issue (assoc two "2027-Q1" {:role :primary}) "2027-Q2")))
    (is (= "2026-Q4-r1" (lg/previous-issue (assoc two "2027-Q1" {:role :primary}) "2026-Q4-r2"))
        "an issue never reproduces a later quarter")))

(defn- archive-fixture
  "A live input with `v`, archived under a fresh root: [live root shas]."
  [v]
  (let [d (tmp-dir) live (str d "/live/in.txt") root (str d "/archive")]
    (io/make-parents live)
    (spit live v)
    [live root (lg/archive! root [live])]))

(deftest an-issue's-inputs-are-archived-read-only-and-hashed
  (let [[live root shas] (archive-fixture "1")
        copy (io/file (str root "/" live))]
    (is (= {live (sfc/sha256-file live)} shas))
    (is (= "1" (slurp copy)))
    (is (not (.canWrite copy)) "read-only")
    (is (= shas (lg/check-archive root shas)))
    (is (= shas (lg/archive! root [live])) "archiving the same files again is a no-op")
    (spit live "2")
    (is (thrown-with-msg? Exception #"refusing" (lg/archive! root [live]))
        "an archive is never overwritten with other data")))

(deftest a-tampered-archive-is-refused
  (let [[live root shas] (archive-fixture "1")
        copy (io/file (str root "/" live))]
    (.setWritable copy true)
    (spit copy "9")
    (is (thrown-with-msg? Exception #"refusing" (lg/check-archive root shas)))
    (.setWritable (.getParentFile copy) true)
    (.delete copy)
    (is (thrown-with-msg? Exception #"missing; refusing" (lg/check-archive root shas)))))

(deftest the-previous-issue-is-reproduced-on-its-own-inputs
  (let [[live root shas] (archive-fixture "1")
        code (fn [] (str "out:" (slurp live) "\n"))
        expected (code)
        reproduce (fn [run!] (lg/reproduce {:issue "2026-Q4" :root root :inputs shas :expected expected :run! run!}))]
    (testing "a data refresh since is allowed: the run reads the archive"
      (spit live "2")
      (is (= "out:2\n" (code)) "the live file moved")
      (let [r (reproduce code)]
        (is (:reproduced r))
        (is (= 1 (:lines r))))
      (is (= {live {:old (shas live) :new (sfc/sha256-file live)}}
             (lg/data-changes shas {live (sfc/sha256-file live)}))
          "and recorded: which input changed, its old and new hash"))
    (testing "a code change that moves the previous issue refuses"
      (is (thrown-with-msg? Exception #"does not reproduce" (reproduce (fn [] (str "out!" (slurp live) "\n"))))))
    (testing "a read outside the inputs refuses"
      (let [other (str (tmp-dir) "/other.txt")]
        (spit other "x")
        (is (thrown-with-msg? Exception #"not in" (reproduce (fn [] (slurp other) (code)))))))
    (testing "a tampered archive refuses before anything runs"
      (let [copy (io/file (str root "/" live)) ran (atom false)]
        (.setWritable copy true)
        (spit copy "3")
        (is (thrown-with-msg? Exception #"refusing" (reproduce (fn [] (reset! ran true) (code)))))
        (is (false? @ran))))))

(deftest data-changes-record-changed-added-and-dropped-inputs
  (is (= {} (lg/data-changes {"a" "1"} {"a" "1"})))
  (is (= {"a" {:old "1" :new "2"} "b" {:old nil :new "3"} "c" {:old "4" :new nil}}
         (lg/data-changes {"a" "1" "c" "4"} {"a" "2" "b" "3"}))))

;; --- amendment L1: scoring guards ----------------------------------------------------

(defn- git! [dir & args] (apply sh/sh "git" "-C" dir args))

(deftest a-score-is-recorded-once-even-if-its-file-is-deleted
  (let [repo (tmp-dir)
        rel "registered/ledger/2026-Q4-r1/scores/year/coup.edn"
        f (str repo "/" rel)
        opts {:scored {} :issue "2026-Q4-r1" :scale :year :kinds ["coup"] :repo repo
              :path-of (fn [k] (str "registered/ledger/2026-Q4-r1/scores/year/" k ".edn"))}]
    (git! repo "init" "-q")
    (git! repo "config" "user.email" "t@example.com")
    (git! repo "config" "user.name" "t")
    (is (= ["coup"] (lg/check-unscored opts)) "nothing scored yet")
    (io/make-parents f)
    (spit f "{:n 1}")
    (is (thrown-with-msg? Exception #"already scored" (lg/check-unscored opts)) "the file on disk")
    (git! repo "add" rel)
    (git! repo "commit" "-q" "-m" "score")
    (.delete (io/file f))
    (is (not (.exists (io/file f))))
    (is (thrown-with-msg? Exception #"already scored" (lg/check-unscored opts))
        "deleted, but committed once: git's history keeps it")
    (is (thrown-with-msg? Exception #"already scored"
                          (lg/check-unscored (assoc opts :scored {["2026-Q4-r1" :year "coup"] "ab"} :repo (tmp-dir))))
        "registered in bnm.ledger/scored")))

(deftest every-registered-score-is-intact
  (doseq [[[issue scale kind] sha] lg/scored]
    (is (= sha (sfc/sha256-file (str "registered/ledger/" issue "/scores/" (name scale) "/" kind ".edn")))
        (str issue " " scale " " kind))))

;; --- amendment L1: the claim ---------------------------------------------------------

(defn- rows-of [n p b y-of]
  (vec (for [i (range n)] {:place (format "C%02d" i) :p p :rate b :persist b :last12 b :y (y-of i)})))

(deftest pooled-skill-weighs-the-kinds-equally
  (let [k1 [{:place "A" :p 0.5 :rate 0.5 :y true} {:place "B" :p 0.5 :rate 0.5 :y false}]
        k2 [{:place "A" :p 1.0 :rate 0.5 :y true} {:place "B" :p 0.0 :rate 0.5 :y false}]]
    (is (close? 0.5 (lg/pooled-skill {"k1" k1 "k2" k2} :rate bnm.benchmarks/brier))
        "skill 0 and 1, the mean of the two")))

(deftest the-claim-needs-three-year-node-issues-and-an-interval-above-zero
  ;; the base knows who has the onset, the baselines do not
  (let [ys #(zero? (mod % 4))
        good {"k" (mapv #(assoc % :p (if (:y %) 0.6 0.1)) (rows-of 80 0.0 0.25 ys))}
        bad {"k" (mapv #(assoc % :p (if (:y %) 0.1 0.6)) (rows-of 80 0.0 0.25 ys))}
        three [good good good]
        c (lg/claim three {:draws 200 :seed 1})]
    (is (= 3 (:issues c)))
    (is (every? #(= :better (get-in c [:brier % :verdict])) [:rate :persist :last12]))
    (is (pos? (get-in c [:brier :rate :lo])))
    (is (true? (:claimed c)) "beats all three on Brier, three issues")
    (is (false? (:claimed (lg/claim [good good] {:draws 200 :seed 1}))) "two issues: reported, not claimed")
    (is (= :worse (get-in (lg/claim [bad bad bad] {:draws 200 :seed 1}) [:brier :rate :verdict])))
    (is (= (lg/claim three {:draws 200 :seed 1}) c) "the same seed, the same interval")))

(deftest the-interval's-size-under-the-null
  (let [rows (fn [s] (vec (for [i (range 60)] {:place (format "C%02d" i) :p (+ 0.05 (* 0.002 i)) :rate 0.1 :persist 0.1 :last12 0.1 :y false})))
        issues [{"k" (rows 1)} {"k" (rows 2)} {"k" (rows 3)}]]
    (is (= 0.0 (lg/null-claim-rate (mapv #(update-vals % (fn [rs] (mapv (fn [r] (assoc r :p 0.1)) rs))) issues)
                                   :rate {:sims 20 :draws 100 :seed 1}))
        "identical forecasts never claim")
    (let [r (lg/null-claim-rate issues :rate {:sims 40 :draws 100 :seed 1})]
      (is (<= 0.0 r 0.25) "outcomes drawn at the midpoint: few claims"))))
