(ns bnm.concordance-test
  "bnm.concordance on a toy table, then checks on the real one
  (data/derived/supply/concordance.csv and subnodes.csv, written by
  scripts/concordance.py); those are skipped when the files are missing."
  (:require [bnm.concordance :as cc]
            [bnm.csv :as csv]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))
(defn- total [m] (reduce + 0.0 (vals m)))

;; HS92 100000 is split in HS17 into 100010 (to A) and 100020 (to B);
;; 200000 is unchanged and goes to B; 300000 is shared by A and C.
(def toy
  (cc/index
   [{:edition "HS92" :code "100000" :hs6 "100000" :cpa "x" :product "A" :weight 0.6}
    {:edition "HS92" :code "100000" :hs6 "100000" :cpa "y" :product "B" :weight 0.4}
    {:edition "HS92" :code "200000" :hs6 "200000" :cpa "y" :product "B" :weight 1.0}
    {:edition "HS92" :code "300000" :hs6 "300000" :cpa "x" :product "A" :weight 0.5}
    {:edition "HS92" :code "300000" :hs6 "300000" :cpa "z" :product "C" :weight 0.5}
    {:edition "HS17" :code "100010" :hs6 "100000" :cpa "x" :product "A" :weight 1.0}
    {:edition "HS17" :code "100020" :hs6 "100000" :cpa "y" :product "B" :weight 1.0}
    {:edition "HS17" :code "200000" :hs6 "200000" :cpa "y" :product "B" :weight 1.0}]))

(def toy-subnodes
  [{:subnode "s1" :name "one" :edition "HS92" :code "200000" :hs6 "200000" :parent "B" :kind "exact"}
   {:subnode "s2" :name "two" :edition "HS92" :code "300000" :hs6 "300000" :parent "C" :kind "pooled"}
   {:subnode "s1" :name "one" :edition "HS17" :code "200000" :hs6 "200000" :parent "B" :kind "exact"}
   {:subnode "s3" :name "three" :edition "HS17" :code "100020" :hs6 "100000" :parent "B" :kind "exact"}])

(deftest mapping-conserves-totals
  (let [vals {"100000" 10.0 "200000" 5.0 "300000" 4.0 "999999" 1.0}
        {:keys [products unmapped]} (cc/to-products toy "HS92" vals)]
    (is (close? 20.0 (+ (total products) (total unmapped))))
    (is (= {"999999" 1.0} unmapped))
    (is (close? 8.0 (products "A")) "6 from 100000, 2 from 300000")
    (is (close? 9.0 (products "B")))
    (is (close? 2.0 (products "C")))))

(deftest chaining-to-hs92
  (let [vals {"100010" 3.0 "100020" 2.0 "200000" 7.0 "555555" 1.0}
        {:keys [hs92 unmapped]} (cc/to-hs92 toy "HS17" vals)]
    (is (= {"100000" 5.0 "200000" 7.0} hs92))
    (is (= {"555555" 1.0} unmapped))
    (is (close? 13.0 (+ (total hs92) (total unmapped))))
    (testing "HS92 values chain to themselves"
      (is (= {"100000" 1.0} (:hs92 (cc/to-hs92 toy "HS92" {"100000" 1.0})))))))

(deftest subnodes
  (testing "aggregation by edition"
    (is (= {"s1" 5.0 "s2" 4.0} (cc/aggregate-subnodes toy-subnodes "HS92" {"200000" 5.0 "300000" 4.0 "100000" 9.0})))
    (is (= {"s1" 1.0 "s3" 2.0} (cc/aggregate-subnodes toy-subnodes "HS17" {"200000" 1.0 "100020" 2.0}))))
  (testing "which sub-nodes a product holds"
    (is (= #{"s1" "s3"} (cc/subnodes-of toy-subnodes "B")))
    (is (= #{"s2"} (cc/subnodes-of toy-subnodes "C")))
    (is (= #{} (cc/subnodes-of toy-subnodes "A"))))
  (testing "a parent split into its sub-nodes and a rest adds back up"
    ;; B's trade: 0.4 * 10 from 100000 and 5 from 200000 = 9; s1 is 5/9 of it
    (let [vals {"100000" 10.0 "200000" 5.0 "300000" 4.0}
          {:keys [subnodes rest]} (cc/split-parent toy toy-subnodes "HS92" vals "B" 90.0)]
      (is (close? 50.0 (subnodes "s1")))
      (is (close? 40.0 rest))
      (is (close? 90.0 (+ rest (total subnodes)))))
    (testing "C takes only its share of a shared code"
      (let [{:keys [subnodes rest]} (cc/split-parent toy toy-subnodes "HS92" {"300000" 4.0} "C" 1.0)]
        (is (close? 1.0 (subnodes "s2")))
        (is (close? 0.0 rest))))
    (testing "no trade: the parent stays whole"
      (let [{:keys [subnodes rest]} (cc/split-parent toy toy-subnodes "HS92" {} "B" 3.0)]
        (is (empty? subnodes))
        (is (close? 3.0 rest))))))

;; ---------------------------------------------------------------- the real table

(def conc-path "data/derived/supply/concordance.csv")
(def subs-path "data/derived/supply/subnodes.csv")
(def products-path "data/derived/supply/products.csv")

(def real (delay (when (.exists (io/file conc-path))
                   {:rows (cc/load-concordance conc-path)
                    :subs (cc/load-subnodes subs-path)})))

(defn- products-of [idx ed code] (into {} (map (juxt :product :weight)) (get idx [ed code])))

(deftest the-real-concordance
  (if-not @real
    (println "bnm.concordance-test: no" conc-path "- skipped (python -I scripts/concordance.py)")
    (let [{:keys [rows subs]} @real
          idx (cc/index rows)
          products (set (map :code (cc/load-products products-path)))]
      (testing "weights of every code sum to one"
        (is (empty? (for [[k rs] idx :let [s (reduce + (map :weight rs))]
                          :when (not (< (Math/abs (- s 1.0)) 1e-4))] [k s]))))
      (testing "every product is an EXIOBASE product"
        (is (empty? (remove products (set (map :product rows))))))
      (testing "spot checks"
        (is (close? 1.0 (get (products-of idx "HS92" "270900") "C_COIL" 0)) "crude")
        (is (close? 1.0 (get (products-of idx "HS92" "310210") "C_NFER" 0)) "urea")
        (is (close? 1.0 (get (products-of idx "HS92" "260300") "C_COPO" 0)) "copper ore")
        (is (close? 1.0 (get (products-of idx "HS92" "740311") "C_COPP" 0)) "copper cathode")
        (is (close? 1.0 (get (products-of idx "HS92" "310420") "C_PFER" 0)) "potassium chloride")
        (is (close? 1.0 (get (products-of idx "HS92" "281410") "C_NFER" 0)) "ammonia")
        (is (close? 1.0 (get (products-of idx "HS92" "270111") "C_ANTH" 0)) "anthracite")
        (is (close? 1.0 (get (products-of idx "HS17" "850760") "C_ELMA" 0)) "Li-ion")
        (is (close? 1.0 (get (products-of idx "HS17" "870380") "C_MOTO" 0)) "EVs")
        (is (< 0.9 (reduce + (vals (select-keys (products-of idx "HS92" "271000")
                                                 ["C_MGSL" "C_AGSL" "C_GJET" "C_KJET" "C_KERO" "C_DOIL"
                                                  "C_FOIL" "C_NAPT" "C_WHSP" "C_LUBR"]))))
            "HS92 petroleum oils go mostly to the refinery products"))
      (testing "edition chaining"
        (let [hs92 (set (for [r rows :when (= "HS92" (:edition r))] (:code r)))]
          (testing "every code of a later edition chains to an HS92 code of the table"
            (is (empty? (for [r rows :when (not (contains? hs92 (:hs6 r)))] r))))
          (testing "each later-edition code chains to a single HS92 code"
            (is (every? #(= 1 (count (set (map :hs6 %)))) (vals idx))))
          (testing "known chains"
            (is (= {"850780" 1.0} (:hs92 (cc/to-hs92 idx "HS12" {"850760" 1.0}))))
            (is (= {"870390" 1.0} (:hs92 (cc/to-hs92 idx "HS17" {"870380" 1.0}))))
            (is (= {"854140" 2.0} (:hs92 (cc/to-hs92 idx "HS22" {"854142" 1.0 "854143" 1.0})))))
          (testing "an HS17 code converting to itself sends its value to products its HS92 code also reaches"
            (let [bad (for [[[ed code] _] idx
                            :when (and (= ed "HS17") (contains? hs92 code)
                                       (= code (:hs6 (first (idx [ed code])))))
                            :let [p17 (set (keys (products-of idx "HS17" code)))
                                  p92 (set (keys (products-of idx "HS92" code)))]
                            :when (not (every? p92 p17))]
                        code)]
              (is (empty? bad))))
          (testing "values chained to HS92 then mapped keep their total"
            (let [vals (into {} (for [[[ed code] _] idx :when (= ed "HS17")] [code 1.0]))
                  {:keys [hs92 unmapped]} (cc/to-hs92 idx "HS17" vals)
                  {:keys [products]} (cc/to-products idx "HS92" hs92)]
              (is (empty? unmapped))
              (is (< (Math/abs (- (count vals) (total products))) 1e-6))))))
      (testing "BACI world trade reaches a product in every edition and year"
        (let [cov (csv/read-maps "data/derived/supply/concordance-coverage.csv" \,)]
          (is (seq cov))
          (is (every? #(< 0.99 (parse-double (:share %))) cov))))
      (testing "sub-nodes"
        (testing "every sub-node maps to an EXIOBASE parent that its code reaches"
          (is (seq subs))
          (is (empty? (for [s subs :when (not (and (products (:parent s))
                                                   (pos? (get (products-of idx (:edition s) (:code s)) (:parent s) 0))))]
                        s))))
        (testing "no code stands for two sub-nodes in one edition"
          (is (every? #(= 1 (count %)) (vals (group-by (juxt :edition :code) subs)))))
        (testing "the essentials are there"
          (is (every? (set (map :subnode subs))
                      ["lithium_carbonate" "lithium_hydroxide" "cobalt_oxides" "cobalt_unwrought"
                       "rare_earth_compounds" "rare_earth_metals" "permanent_magnets" "polysilicon"
                       "li_ion_batteries" "pv_cells" "integrated_circuits" "evs" "graphite_natural"
                       "graphite_synthetic" "nickel_sulphate" "manganese_ore" "copper_cathode"])))
        (testing "parents"
          (let [parent (into {} (for [s subs :when (= "HS92" (:edition s))] [(:subnode s) (:parent s)]))]
            (is (= "C_CHEM" (parent "lithium_carbonate")))
            (is (= "C_ONFO" (parent "cobalt_ore")))
            (is (= "C_ONFM" (parent "cobalt_unwrought")))
            (is (= "C_CHMF" (parent "graphite_natural")))
            (is (= "C_ELMA" (parent "li_ion_batteries")))
            (is (= "C_MOTO" (parent "evs")))
            (is (= "C_COPP" (parent "copper_cathode")))))
        (is (contains? (cc/subnodes-of subs "C_CHEM") "lithium_hydroxide"))))))
