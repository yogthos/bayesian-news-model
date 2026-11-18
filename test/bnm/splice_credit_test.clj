(ns bnm.splice-credit-test
  "--splice-credit bank (bnm-ris): under --dated-credit, JST's bank loans
  spliced with the BIS's bank credit (JST's concept) in place of the
  BIS's credit from all lenders, whose stretch fell from 0.70 to 0.49 in
  ranking JST's countries from 2003 (CALIBRATION 'Where the banking signal
  is lost')."
  (:require [bnm.lattice-banking :as lb]
            [bnm.stocks :as sk]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is]]))

(deftest the-option
  (is (nil? (wr/splice-credit-choice {:dated-credit "true"})))
  (is (= :bank (wr/splice-credit-choice {:dated-credit "true" :splice-credit "bank"})))
  (is (thrown? Exception (wr/splice-credit-choice {:splice-credit "bank"})) "a splice needs --dated-credit")
  (is (thrown? Exception (wr/splice-credit-choice {:dated-credit "true" :splice-credit "all"}))))

(deftest the-bank-splice-reads-bank-credit
  (let [q4 (fn [ys] (sk/series (for [[y v] ys] [(+ (* 12 y) 11) (+ (* 12 y) 17) v])))
        db {["AAA" "finance/credit_gdp"] (q4 {2001 150.0 2002 155.0})
            ["AAA" "finance/household_credit_gdp"] (q4 {2001 60.0 2002 62.0})}
        bank {"AAA" {2001 90.0 2002 93.0}}]
    (is (= {2001 150.0 2002 155.0} (get-in (#'lb/jst-bis db #{"AAA"}) ["AAA" :credit])))
    (is (= {2001 90.0 2002 93.0} (get-in (#'lb/jst-bis db #{"AAA"} bank) ["AAA" :credit])) "bank credit for the credit input")
    (is (= (get-in (#'lb/jst-bis db #{"AAA"}) ["AAA" :mortgages]) (get-in (#'lb/jst-bis db #{"AAA"} bank) ["AAA" :mortgages]))
        "household credit as before")))
