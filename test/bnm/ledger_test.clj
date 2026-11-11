(ns bnm.ledger-test
  (:require [bnm.ledger :as lg]
            [bnm.sfc-rates-confirm :as sfc]
            [clojure.java.io :as io]
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
               :clim 0.01 :last12 0.02 :share nil :pers -3.5 :wclim 0.05}]
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
    (is (= lg/base-files (lg/check-base (str "data/runs/" lg/base) lg/base-files)) "the files on disk are t48's")))

(deftest every-registered-issue-is-intact
  (doseq [[q {:keys [files]}] lg/issued
          [n sha] files]
    (is (= sha (sfc/sha256-file (str "registered/ledger/" q "/" n))) (str q "/" n))))

(deftest every-registered-scanner-ranking-is-intact
  (doseq [[q files] lg/scanners
          [n sha] files]
    (is (= sha (sfc/sha256-file (str "registered/ledger/" q "/" n))) (str q "/" n))))

(deftest a-window-is-scored-only-once-published
  (let [cat {:through {"AAA" 24347 "BBB" 24340}}]
    (is (true? (lg/published? cat 24347 24360)) "covered through the window's end, and it has closed")
    (is (false? (lg/published? cat 24348 24360)) "the catalogue stops short")
    (is (false? (lg/published? cat 24347 24347)) "the window has not closed by the scoring month")
    (is (false? (lg/published? {:through {}} 24300 24400)) "no coverage")))

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
