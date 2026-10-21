(ns bnm.lattice-banking-test
  (:require [bnm.lattice-banking :as lb]
            [clojure.test :refer [deftest is]]))

(deftest one-crisis-dated-twice-counts-once
  ;; Reinhart-Rogoff and the Macrohistory panel date some crises a year apart
  (is (= #{1890 1907 1931} (lb/join-years #{1890 1907} #{1891 1931})))
  (is (= #{1890 1892} (lb/join-years #{1890} #{1892})) "two years apart: two crises"))
