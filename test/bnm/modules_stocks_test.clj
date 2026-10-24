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
        (is (some #{"USA,2002,bb,bb/gap,0.3,,2000,2002,model"} ls))
        (is (some #{"USA,2001,aa,aa/x,1.5,0.1,,2001,model"} ls) "a value carries its spread where the model gives one")
        (is (some #{"USA,2000,aa,aa/x,1.0,,,2000,model"} ls) "and none where it does not")))))

(deftest a-table-changed-since-the-run-is-read-from-its-file
  (let [dir (tmp-dir) [a b] (fixture dir) mods (modules-of a b)
        out (str dir "/coupled")]
    (ms/run! mods out {:from 1990 :to 2010})
    (spit a "JPN,2003,aa/x,9.0\n" :append true)
    (is (= 9.0 (get-in (states/read-states [a] {:coupled out}) ["aa/x" "JPN" 2003])) "a stale coupled table is not read")
    (is (= (states/read-states [a b]) (states/read-states [a b] {:coupled out})))))

(deftest lagged-tables-outside-the-coupled-table-read-the-same
  ;; the material base: by data year, known two years on, read from its file
  (let [dir (tmp-dir) [a b] (fixture dir) mods (modules-of a b)
        out (str dir "/coupled")
        c (str dir "/material/cc.csv")]
    (io/make-parents c)
    (spit c "iso,year,variable,value\nUSA,1999,arable_land,10\nUSA,2000,arable_land,11\nDEU,2000,arable_land,7\n")
    (ms/run! mods out {:from 1990 :to 2010})
    (let [paths [a b [c 2]]
          s1 (states/read-states paths) s2 (states/read-states paths {:coupled out})]
      (is (= s1 s2))
      (is (= 11.0 (get-in s2 ["arable_land" "USA" 2002])) "known two years after its data year"))))

(deftest the-run-streams-the-tables
  ;; run! steps each table from a copy sorted by year, read as it goes,
  ;; not from the tables held in memory: the same coupled table as the
  ;; modules' own in-memory steps, and nothing left behind
  (let [dir (tmp-dir) [a b] (fixture dir) mods (modules-of a b)
        out (str dir "/coupled")
        mem (atom [])
        _ (eng/run mods {:from 1990 :to 2010 :keep false
                         :on-step (fn [id t e] (swap! mem into (eng/table-rows (first (filter #(= id (:module %)) mods)) t e)))})
        res (ms/run! mods out {:from 1990 :to 2010})
        ls (rest (str/split-lines (slurp (str out "/state.csv"))))]
    (is (= (sort (map #(str/join "," (map (fn [x] (if (keyword? x) (name x) (str x))) %)) @mem)) (sort ls)))
    (is (= {:aa 23 :bb 3} (:rows res)))
    (is (= #{"state.csv" "graph.edn" "graph.txt" "tables.edn"} (set (.list (io/file out)))) "no spill left")
    (testing "rows before the first year run are left over, not lost silently"
      (is (= {:aa 18 :bb 1} (:leftover (ms/run! mods (str dir "/c3") {:from 2002 :to 2010})))))))

(deftest the-golden-check-in-shards-is-the-same
  ;; a table over the budget is checked a share of its variables at a time
  (let [dir (tmp-dir) [a b] (fixture dir) mods (modules-of a b)
        out (str dir "/coupled")]
    (ms/run! mods out {:from 1990 :to 2010})
    (is (= {"aa/w" {"WLD" {2000 3.0 2001 4.0}}}
           (first (states/read-each [a] {:coupled out :var? #{"aa/w"}}))
           (first (states/read-each [a] {:var? #{"aa/w"}}))))
    (is (= (ms/golden mods out) (ms/golden mods out {:budget 10})))
    (spit (str out "/state.csv") (str/join "\n" (remove #(str/starts-with? % "USA,2001,aa,aa/x,") (str/split-lines (slurp (str out "/state.csv"))))))
    (is (= [1 0] (map :mismatches (ms/golden mods out {:budget 10}))) "a mismatch is still found")))

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
             :essential :tech-demand :control}
           ids))
    (is (= (set (map #(str "data/derived/stocks/" % ".csv")
                     ["keen" "credit" "food" "tension" "energy" "technology" "cycles" "stages" "migration"
                      "econ-factors" "econ-structure" "econ-divergence" "econ-coupling" "geo-capability"
                      "cells" "chokepoints" "essential" "tech-demand" "control"]))
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
      (is (every? (fn [m] (some :channel (:emits m))) mods) "every model has a channel")
      (is (= {:technology #{:R10} :tech-demand #{:R9} :chokepoints #{:W4} :control #{:C1 :C3}}
             (into {} (for [m mods :when (#{:technology :tech-demand :chokepoints :control} (:module m))]
                        [(:module m) (set (keep :channel (:emits m)))])))))))

(deftest the-default-states-read-the-same-from-the-coupled-table
  ;; bnm.state-diag's default paths, the lagged material tables among
  ;; them, over the small stock tables on disk run through the engine: the
  ;; same states and the same readers (the tool bnm.engine-run checks the
  ;; full default paths against the full coupled table)
  (let [small (filter #(#{:credit :energy :econ-coupling} (:module %)) (ms/modules))
        material (for [f ["endowments" "capacity" "production"] :let [p (str "data/derived/material/" f ".csv")] :when (.exists (io/file p))] [p 2])]
    (if-not (and (every? #(.exists (io/file (:table %))) small) (seq material))
      (println "the-default-states-read-the-same-from-the-coupled-table: tables not on disk, skipped")
      (let [out (str (tmp-dir) "/coupled")
            _ (ms/run! small out {:from 1800 :to 2035})
            paths (concat (map :table small) material)
            s1 (states/read-states paths) s2 (states/read-states paths {:coupled out})]
        (is (= s1 s2))
        (is (ms/same-readers? s1 s2 {:sample 40}))))))

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
