(ns bnm.links-test
  "bnm-56c.2: the weighting candidates are opt-in. The default run still
  reproduces t48-world-norel byte for byte (market crashes, the kind a
  group with nothing in it cost most) on t48's own inputs, read from the
  first ledger issue's archive (data/ledger-inputs/2026-Q4/): the data on
  disk were rebuilt after it (bnm-8ul)."
  (:require [bnm.fast-run :as fr]
            [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.ledger :as lg]
            [bnm.links :as links]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(def bench "data/runs/t48-world-norel")
(def archive "t48's inputs, as archived with the first ledger issue." "data/ledger-inputs/2026-Q4")

(defn- written
  "{file text} as bnm.world-run writes a kind's result."
  [kind r]
  (let [dir (str (System/getProperty "java.io.tmpdir") "/bnm-links-test-" kind)]
    (#'wr/write-kind dir kind r)
    (into {} (for [f ["forecasts.tsv" "preds.tsv" "weights.tsv"]] [f (slurp (str dir "/" f))]))))

(deftest the-default-run-is-the-base-of-record
  (if-not (and (.exists (io/file bench "market_crash" "forecasts.tsv")) (.exists (io/file archive)))
    (println "skipped: no" bench "or" archive)
    (let [kind "market_crash"
          end (+ (* 12 2025) 11)
          [r] (lg/recording #(lt/run (get (wr/build-specs [kind] end (kinds/catalogues)) kind))
                            (lg/redirect-to archive lg/inputs))
          got (written kind r)]
      (doseq [[f text] got]
        (testing f
          (is (= (slurp (io/file bench kind f)) text) (str kind "/" f " differs from " bench)))))))

(deftest set-merges-and-removes
  (is (= {:a 1 :c 3} (fr/with-set {:a 1 :b 2} {:b nil :c 3}))))

(deftest the-acceptance-rule
  ;; bnm-56c.2: the year nodes' Brier cost of a noise group, each draw less
  ;; its weighting's base; accepted when the mean is within 0.0003 of zero;
  ;; the interval is the v2 rule's (A3) for a fresh draw
  (let [{:keys [mean sd lo hi accept]} (links/noise-acceptance [0.0001 0.0002 0.0003 0.0002])]
    (is (< (Math/abs (- mean 0.0002)) 1e-12))
    (is (< (Math/abs (- sd (Math/sqrt (/ 0.00000002 3)))) 1e-12))
    ;; t(0.95, 3) = 2.3534
    (is (< (Math/abs (- hi (+ 0.0002 (* 2.3534 sd (Math/sqrt 1.25))))) 1e-7))
    (is (< lo mean hi))
    (is accept))
  (is (not (:accept (links/noise-acceptance [-0.0020 -0.0017 -0.0016 -0.0021]))))
  (is (not (:accept (links/noise-acceptance [0.0004 0.0005 0.0003 0.0006]))) "within of zero, either way"))
