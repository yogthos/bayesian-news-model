(ns bnm.credit-denominator-test
  "--credit-denominator smooth (bnm-5lr): the BIS's credit over GDP with
  the part of a year's rise that comes from GDP falling taken out, so a
  recession (2009, 2020) does not read as a credit boom (the ESRB's and
  the ECB's adjusted credit gaps)."
  (:require [bnm.lattice-banking :as lb]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(deftest the-option
  (is (nil? (wr/credit-denominator-choice {})))
  (is (= :smooth (wr/credit-denominator-choice {:credit-denominator "smooth" :dated-credit "true"})))
  (is (thrown? Exception (wr/credit-denominator-choice {:credit-denominator "smooth"})) "needs --dated-credit")
  (is (thrown? Exception (wr/credit-denominator-choice {:credit-denominator "trend" :dated-credit "true"}))))

(deftest a-fall-in-nominal-gdp-is-taken-out
  (let [adj (lb/gdp-fall-adjusted {2019 212.0 2020 240.0 2021 228.0} {2020 -7.5 2021 6.0} {2020 0.5 2021 1.6})]
    (testing "nominal GDP fell 7.04% in 2020: the ratio times 1 - 0.0704"
      (is (< (Math/abs (- (adj 2020) (* 240.0 (* 0.925 1.005)))) 1e-9)))
    (is (= 212.0 (adj 2019)) "no growth known: unchanged")
    (is (= 228.0 (adj 2021)) "nominal GDP grew: unchanged")
    (is (= {2001 1.0} (lb/gdp-fall-adjusted {2001 1.0} {} {})) "no GDP data: unchanged")))

(deftest the-bis-credit-is-adjusted-only-under-the-option
  (let [series (fn [m] {:period (mapv #(+ 11 (* 12 %)) (keys m)) :value (vec (vals m))})
        db {["AAA" "finance/credit_gdp"] (series {2019 100.0 2020 120.0})
            ["AAA" "finance/household_credit_gdp"] (series {2019 50.0 2020 60.0})}
        gdp {"AAA" {:growth {2020 -10.0} :inflation {2020 0.0}}}]
    (is (= {2019 100.0 2020 120.0} (get-in (#'lb/jst-bis db #{"AAA"}) ["AAA" :credit])))
    (let [a (#'lb/jst-bis db #{"AAA"} nil gdp)]
      (is (< (Math/abs (- 108.0 (get-in a ["AAA" :credit 2020]))) 1e-9))
      (is (< (Math/abs (- 54.0 (get-in a ["AAA" :mortgages 2020]))) 1e-9) "household credit too")
      (is (= 100.0 (get-in a ["AAA" :credit 2019]))))))
