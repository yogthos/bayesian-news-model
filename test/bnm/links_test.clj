(ns bnm.links-test
  "bnm-56c.2: the weighting candidates are opt-in. The default run still
  reproduces the base of record, t48-world-norel, byte for byte (market
  crashes, the kind a group with nothing in it cost most)."
  (:require [bnm.fast-run :as fr]
            [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(def bench "data/runs/t48-world-norel")

(defn- written
  "{file text} as bnm.world-run writes a kind's result."
  [kind r]
  (let [dir (str (System/getProperty "java.io.tmpdir") "/bnm-links-test-" kind)]
    (#'wr/write-kind dir kind r)
    (into {} (for [f ["forecasts.tsv" "preds.tsv" "weights.tsv"]] [f (slurp (str dir "/" f))]))))

(deftest the-default-run-is-the-base-of-record
  (if-not (.exists (io/file bench "market_crash" "forecasts.tsv"))
    (println "skipped: no" bench)
    (let [kind "market_crash"
          end (+ (* 12 2025) 11)
          sp (get (wr/build-specs [kind] end (kinds/catalogues)) kind)
          got (written kind (lt/run sp))]
      (doseq [[f text] got]
        (testing f
          (is (= (slurp (io/file bench kind f)) text) (str kind "/" f " differs from " bench)))))))

(deftest set-merges-and-removes
  (is (= {:a 1 :c 3} (fr/with-set {:a 1 :b 2} {:b nil :c 3}))))
