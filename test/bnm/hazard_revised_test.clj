(ns bnm.hazard-revised-test
  "Loop audit Test 12: the IMF IL reserves are a current revised download
  read with an assumed lag, so they are marked :revised, and the
  without-revised sensitivity drops them."
  (:require [bnm.hazard-data :as hd]
            [bnm.hazard-run :as hr]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]))

(def ^:private mapping (edn/read-string (slurp hd/mapping-path)))

(deftest reserves-are-a-revised-vintage
  (is (true? (get-in mapping [:sources :reserves :revised]))))

(deftest every-reserves-record-loses-its-stock-without-revised-vintages
  (let [tmpls (filter #(= :reserves (get-in % [:series :stock])) (:records mapping))
        series {:stock [{:t 2000.0 :v 1.0}] :outflow [{:t 2000.0 :v 1.0}] :claims [{:t 2000.0 :v 1.0}]
                :income [{:t 2000.0 :v 1.0}]}]
    (is (seq tmpls))
    (doseq [tmpl tmpls]
      (let [roles (hd/revised-roles (:sources mapping) tmpl series)]
        (is (contains? roles :stock) (:id tmpl))
        (is (not (contains? (#'hr/strip-revised {:revised-roles roles} series) :stock)) (:id tmpl))))))
