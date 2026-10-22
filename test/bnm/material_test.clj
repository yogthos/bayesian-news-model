(ns bnm.material-test
  (:require [bnm.material :as mat]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def plants
  ;; two gas plants in one place, one far away; a coal plant
  [{:iso "AAA" :fuel "gas" :mw 300.0 :lon 10.0 :lat 50.0}
   {:iso "AAA" :fuel "gas" :mw 100.0 :lon 10.001 :lat 50.001}
   {:iso "AAA" :fuel "gas" :mw 100.0 :lon 20.0 :lat 40.0}
   {:iso "AAA" :fuel "coal" :mw 500.0 :lon 12.0 :lat 48.0}])

(defn- close? [a b] (< (Math/abs (- a b)) 1e-12))

(deftest assets-to-cells
  (let [cells (mat/cells plants 6)
        shared (first (filter #(= 400.0 (get-in % [:fuels "gas"])) cells))]
    (is (= 3 (count cells)) "the two gas plants share a cell")
    (is (some? shared))
    (is (= 2 (get-in shared [:plants "gas"])) "plants counted per fuel")
    (is (= 1000.0 (reduce + (map :mw cells))) "nothing lost")))

(deftest cross-border-cell
  ;; Strasbourg and Kehl: one cell, two countries; each keeps its own
  (let [ps [{:iso "FRA" :fuel "gas" :mw 100.0 :lon 7.75 :lat 48.58}
            {:iso "DEU" :fuel "gas" :mw 50.0 :lon 7.80 :lat 48.60}]
        cs (mat/cells ps 6)]
    (is (= 1 (count (distinct (map :cell cs)))) "the same cell")
    (is (= #{"FRA" "DEU"} (set (map :iso cs))) "counted for each country")
    (is (= {"FRA" 100.0 "DEU" 50.0} (into {} (map (juxt :iso :mw) cs))))))

(deftest concentration-of-capacity
  ;; the Herfindahl index of a country's capacity across cells, by fuel,
  ;; and the largest cell's share: how much one local shock takes
  (let [c (mat/concentration (mat/cells plants 6))]
    (is (close? (get-in c ["AAA" "gas" :hhi]) (+ (* 0.8 0.8) (* 0.2 0.2))))
    (is (= 0.8 (get-in c ["AAA" "gas" :top-share])))
    (is (= 1.0 (get-in c ["AAA" "coal" :hhi])) "one cell: all of it")
    (is (= 2 (get-in c ["AAA" "gas" :cells])))
    (is (= 3 (get-in c ["AAA" "gas" :plants])))
    (testing "normalised, (H - 1/N) / (1 - 1/N): 0 when even, 1 when all in one cell"
      (is (close? (get-in c ["AAA" "gas" :hhi-norm]) (/ (- 0.68 0.5) 0.5)))
      (is (= 1.0 (get-in c ["AAA" "coal" :hhi-norm])) "one cell"))))

(deftest zero-capacity-cells-not-counted
  (let [c (mat/concentration
           (mat/cells (conj plants {:iso "AAA" :fuel "gas" :mw 0.0 :lon -60.0 :lat -10.0}) 6))]
    (is (= 2 (get-in c ["AAA" "gas" :cells])) "an empty cell is not a site")
    (is (= 3 (get-in c ["AAA" "gas" :plants])) "nor an empty plant")
    (is (close? (get-in c ["AAA" "gas" :hhi]) 0.68))))

(deftest csv-lines-with-quoted-commas
  (is (= ["AFG" "Kajaki, Helmand" "33.0" ""] (mat/split-csv "AFG,\"Kajaki, Helmand\",33.0,")))
  (is (= ["a" "say \"hi\"" "b"] (mat/split-csv "a,\"say \"\"hi\"\"\",b"))))

(deftest reading-plants
  (let [f (java.io.File/createTempFile "plants" ".csv")]
    (try
      (spit f (str/join "\n" ["country,name,capacity_mw,latitude,longitude,primary_fuel,commissioning_year"
                              "AFG,\"Kajaki, Helmand\",33.0,32.322,65.119,Hydro,1975.5"
                              "KOS,Kosovo A,450.0,42.68,21.08,Coal,"
                              "USA,No place,10.0,,,Gas,2000"
                              "USA,Empty,,40.0,-100.0,Gas,2000"
                              "USA,Zero,0,40.0,-100.0,Gas,2000"]))
      (let [ps (mat/read-plants (str f))]
        (is (= 2 (count ps)) "no coordinates, no capacity or zero capacity: left out")
        (is (= {:iso "AFG" :fuel "hydro" :mw 33.0 :lon 65.119 :lat 32.322 :year 1976} (first ps))
            "quoted comma kept; fractional year rounded")
        (is (= "XKX" (:iso (second ps))) "Kosovo under one code")
        (is (nil? (:year (second ps)))))
      (finally (.delete f)))))
