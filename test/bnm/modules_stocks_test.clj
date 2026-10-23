(ns bnm.modules-stocks-test
  (:require [bnm.engine :as eng]
            [bnm.factors :as fac]
            [bnm.modules.stocks :as ms]
            [bnm.states :as states]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- tmp-dir []
  (let [f (java.io.File/createTempFile "coupled" "")]
    (.delete f)
    (.mkdirs f)
    (.getPath f)))

(defn- fixture
  "Two stock tables: one by year known, one with a data year (the
  econ convention), a duplicate key (the later row wins), empty values,
  bookkeeping and world rows."
  [dir]
  (let [a (str dir "/stocks/aa.csv") b (str dir "/stocks/bb.csv")]
    (io/make-parents a)
    (spit a (str "iso,year,variable,value\n"
                 "USA,2001,aa/x,1.5\nUSA,2000,aa/x,1.0\nDEU,2000,aa/x,\nWLD,2000,aa/w,3\nWLD,2001,aa/w,4\n"
                 "USA,2001,aa/x-sd,0.1\nDEU,2001,aa/x,2.0\nDEU,2001,aa/x,2.5\n"
                 (str/join (for [iso ["FRA" "ITA" "ESP" "GBR" "JPN"] y [2000 2001 2002]] (str iso "," y ",aa/x," (+ y (count iso) 0.25) "\n")))))
    (spit b (str "iso,year,variable,value,known,data_year\n"
                 "USA,2002,bb/gap,0.3,2002,2000\nUSA,2001,bb/gap,0.2,2001,1999\nDEU,2002,bb/gap,-0.1,2002,2000\n"))
    [a b]))

(defn- modules-of [a b]
  [(ms/table-module {:module :aa :group :R :place :country :table a :tool 'fixture
                     :emits [{:var "aa/x" :unit "u" :channel :R6} {:var "aa/*" :unit "mixed"}]})
   (ms/table-module {:module :bb :group :F :place :country :table b :tool 'fixture
                     :emits [{:var "bb/gap" :unit "log gap" :known {:data-year 2} :channel :F4}]})])

(deftest the-coupled-table-reproduces-the-stock-tables
  (let [dir (tmp-dir) [a b] (fixture dir) mods (modules-of a b)
        out (str dir "/coupled")
        res (ms/run! mods out {:from 1990 :to 2010})]
    (is (= {:aa 23 :bb 3} (:rows res)) "every row once")
    (is (every? zero? (vals (:leftover res))) "no row outside the years run")
    (is (.exists (io/file out "graph.edn")))
    (is (.exists (io/file out "graph.txt")))
    (testing "bnm.states reads the coupled table as it reads the files"
      (doseq [paths [[a] [b] [a b] [b a] [[b 2] a]]]
        (is (= (states/read-states paths) (states/read-states paths {:coupled out})) (str paths)))
      (is (= 2.5 (get-in (states/read-states [a] {:coupled out}) ["aa/x" "DEU" 2001])) "the later of two rows")
      (is (empty? (ms/mismatches (states/read-states [a b]) (states/read-states [a b] {:coupled out})))))
    (testing "the same readers"
      (let [s1 (states/read-states [a b]) s2 (states/read-states [a b] {:coupled out})
            tree (fac/place-tree ["USA" "DEU" "FRA" "ITA" "ESP" "GBR" "JPN"])
            r1 (states/readers s1 tree []) r2 (states/readers s2 tree [])]
        (is (= (set (keys r1)) (set (keys r2))))
        (doseq [v (keys r1) iso ["USA" "DEU" "FRA" "JPN"] y [2000 2001 2002]]
          (is (= ((r1 v) iso y) ((r2 v) iso y)) (str v " " iso " " y)))))
    (testing "golden: per table, per variable and known year"
      (is (= [{:table a :mismatches 0} {:table b :mismatches 0}]
             (map #(select-keys % [:table :mismatches]) (ms/golden mods out)))))
    (testing "the table carries the module, data year and source"
      (let [ls (str/split-lines (slurp (str out "/state.csv")))]
        (is (= (str/join "," eng/table-header) (first ls)))
        (is (some #{"USA,2002,bb,bb/gap,0.3,,2000,2002,model"} ls))))))

(deftest a-mismatch-is-found
  (let [dir (tmp-dir) [a b] (fixture dir) mods (modules-of a b)
        out (str dir "/coupled")]
    (ms/run! mods out {:from 1990 :to 2010})
    ;; a wrapper that lost a row
    (spit (str out "/state.csv") (str/join "\n" (remove #(str/starts-with? % "USA,2001,aa,aa/x,") (str/split-lines (slurp (str out "/state.csv"))))))
    (is (= [["aa/x" "USA" 2001 1.5 nil]] (ms/mismatches (states/read-states [a]) (states/read-states [a] {:coupled out}))))))

(deftest the-known-year-rule-is-enforced-on-the-tables
  (let [dir (tmp-dir) [a b] (fixture dir)
        strict (ms/table-module {:module :bb :group :F :place :country :table b :tool 'fixture
                                 :emits [{:var "bb/gap" :unit "log gap" :known {:data-year 3}}]})]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"known" (ms/run! [strict] (str dir "/c2") {:from 1990 :to 2010})))))

(deftest the-registry
  (let [mods (ms/modules)
        ids (set (map :module mods))]
    (is (empty? (eng/validate mods)))
    (is (= #{:keen :credit :food :food-world :tension :energy :technology :cycles :stages :migration
             :econ-factors :econ-structure :econ-divergence :econ-coupling :geo-gap :cells :chokepoints
             :essential :tech-demand}
           ids))
    (is (= (set (map #(str "data/derived/stocks/" % ".csv")
                     ["keen" "credit" "food" "tension" "energy" "technology" "cycles" "stages" "migration"
                      "econ-factors" "econ-structure" "econ-divergence" "econ-coupling" "geo-capability"
                      "cells" "chokepoints" "essential" "tech-demand"]))
           (set (map :table mods)))
        "every stock table bnm.states reads")
    (testing "the causal order"
      (let [co (eng/causal-order mods)]
        (is (= "W -> C -> F -> R -> D -> P" (:text co)))
        (is (empty? (:violations co)))
        (is (< (.indexOf ^java.util.List (:order co) :food-world) (.indexOf ^java.util.List (:order co) :cycles)) "world food before the cycles that read it")
        (is (every? #(< (.indexOf ^java.util.List (:order co) %) (.indexOf ^java.util.List (:order co) :essential)) [:stages :tech-demand :chokepoints]))))
    (testing "the edges between today's models"
      (is (= #{[:food-world :cycles] [:stages :essential] [:tech-demand :essential] [:chokepoints :essential]}
             (set (map (juxt :from :to) (eng/edges mods))))))
    (testing "emits map onto the typed channels (section 4c)"
      (is (every? #(or (nil? %) (ms/channels %)) (map :channel (mapcat :emits mods))))
      (is (every? (fn [m] (some :channel (:emits m))) (remove #(#{:technology :tech-demand :chokepoints} (:module %)) mods))
          "every model but three has a channel"))))

(deftest golden-on-the-small-tables-on-disk
  ;; the real stock tables, where present: the small ones, so the suite
  ;; stays quick (the tool bnm.engine-run checks all of them)
  (let [small (filter #(#{:credit :energy :econ-structure :econ-coupling :geo-gap} (:module %)) (ms/modules))]
    (if-not (every? #(.exists (io/file (:table %))) small)
      (println "golden-on-the-small-tables-on-disk: stock tables not on disk, skipped")
      (let [out (str (tmp-dir) "/coupled")
            res (ms/run! small out {:from 1800 :to 2035})]
        (is (every? zero? (vals (:leftover res))))
        (is (every? (comp zero? :mismatches) (ms/golden small out)))))))
