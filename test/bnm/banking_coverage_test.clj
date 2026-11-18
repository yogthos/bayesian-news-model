(ns bnm.banking-coverage-test
  "bnm-7bh step 1, --banking-coverage: nine signals declared through
  bnm.signals at banking's year node, with the priors of docs/CALIBRATION.md
  \"Banking coverage from declared signals\", fixed before any run."
  (:require [bnm.lattice-banking :as lb]
            [bnm.signals :as sg]
            [bnm.stocks :as sk]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(deftest banking-coverage-option
  (is (false? (wr/banking-coverage-choice {})))
  (is (false? (wr/banking-coverage-choice {:banking-coverage "false"})))
  (is (true? (wr/banking-coverage-choice {:banking-coverage "true"})))
  (is (thrown? Exception (wr/banking-coverage-choice {:banking-coverage "yes"})))
  (is (thrown? Exception (wr/banking-coverage-choice {:banking-coverage "true" :banking-gauges "true"}))
      "one extra group at a time: banking-gauges is parked"))

(deftest the-signals-and-priors-are-the-registered-ones
  (is (= [:tot12 :tot12-x-exporter :tot-vol12 :reserves :reserves12 :ca-stretch :pcredit-stretch :pcredit-turn :pcredit3]
         (mapv :name lb/coverage-decls)))
  (is (= {:tot12 [-0.2 0.0625] :tot12-x-exporter [-0.3 0.0625] :tot-vol12 [0.1 0.0625]
          :reserves [-0.2 0.0625] :reserves12 [-0.2 0.0625] :ca-stretch [-0.2 0.0625]
          :pcredit-stretch [0.3 0.0625] :pcredit-turn [0.3 0.0625] :pcredit3 [0.2 0.0625]}
         (:named-prior (sg/assemble {} lb/coverage-decls))))
  (is (= #{"trade/commodity_tot" "energy/fuel_exports_share" "trade/metal_exports_share"
           "debt/reserves_months" "debt/current_account_gdp" "finance/private_credit_gdp"}
         lb/coverage-vars)
      "every series the declarations read, and only those, is loaded under the option"))

(deftest the-year-node-names-every-column-and-groups-the-coverage
  (let [w (lb/coverage-wiring 61)]
    (is (= 70 (count (:names w))))
    (is (= (mapv :name lb/coverage-decls) (subvec (:names w) 61)))
    (is (apply distinct? (:names w)))
    (is (= (vec (range 61 70)) (:group w)))
    (is (= (:named-prior (sg/assemble {} lb/coverage-decls)) (:named-prior w)))))

(deftest the-row-is-the-node's-own-then-the-coverage
  (let [month (fn [y m] (+ (* 12 y) (dec m)))
        tot (sk/series (for [y (range 2000 2008) m (range 1 13)] [(month y m) (+ (month y m) 2) (+ 100.0 (* 0.5 (- y 2000)) (* 0.1 m))]))
        db {["AAA" "trade/commodity_tot"] tot}
        own (fn [u t] [1.0 2.0 u t])
        row (lb/with-coverage own (:row (sg/assemble db lb/coverage-decls)))
        t (month 2006 12)]
    (is (= [1.0 2.0 "AAA" t] (subvec (row "AAA" t) 0 4)))
    (is (= 13 (count (row "AAA" t))))
    (is (= (sg/eval-signal db (:expr (first lb/coverage-decls)) "AAA" t) (nth (row "AAA" t) 4)))
    (is (some? (nth (row "AAA" t) 4)) "the terms of trade read")
    (testing "a country without any of the series reads nil there, the mean once standardized"
      (is (every? nil? (subvec (row "BBB" t) 4))))))
