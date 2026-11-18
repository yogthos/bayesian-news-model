(ns bnm.wide-credit-test
  "--wide-credit (with --dated-credit): the banking node's credit-family
  and house-price inputs for countries outside the Macrohistory panel,
  from the BIS, by the same transforms and publication lag as the JST
  countries' after the 2003 splice."
  (:require [bnm.invariants :as iv]
            [bnm.lattice-banking :as lb]
            [bnm.macrohistory :as mh]
            [bnm.stocks :as sk]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(defn- wiggle [seed i] (Math/sin (+ (* 0.7 i) (* 1.3 seed))))

(defn- jst-rows [iso k]
  (vec (for [y (range 1960 2021) :let [i (- y 1960)]]
         {:iso iso :year y :gdp 100.0 :tloans (* k (+ 60.0 (* 0.5 i) (* 5.0 (wiggle 1 i))))})))

(defn- bis-of
  "A BIS-like annual series in percent of GDP, years `from` to 2024."
  [seed from level]
  (into {} (for [y (range from 2025) :let [i (- y 1960)]]
             [y (+ level (* 0.8 i) (* 6.0 (wiggle seed i)))])))

(defn- close? [a b] (or (and (nil? a) (nil? b)) (and a b (< (Math/abs (- (double a) (double b))) 1e-9))))

(deftest the-panel-scale-is-the-median-of-the-jst-countries
  ;; three JST countries at 0.5, 0.6 and 0.8 of the BIS's level: growth
  ;; outside the panel is put in JST's units by the middle one
  (let [rows (into [] (mapcat (fn [[c k]] (jst-rows c k))) [["AAA" 0.5] ["BBB" 0.6] ["CCC" 0.8]])
        bis (into {} (for [c ["AAA" "BBB" "CCC"]]
                       [c {:credit (into {} (for [r rows :when (= c (:iso r))] [(:year r) (* 100.0 (/ (:tloans r) (:gdp r)) (/ 1.0 ({"AAA" 0.5 "BBB" 0.6 "CCC" 0.8} c)))]))}]))]
    (is (close? 0.006 (mh/panel-scale rows bis 2002)))))

(deftest outside-the-panel-the-same-transforms-as-inside
  ;; a country outside the panel with the BIS series a JST country has
  ;; gets the same stretches, long stretches and gap z at every year from
  ;; the splice; its growth is the BIS change times the panel's scale
  (let [rows (jst-rows "AAA" 1.0)
        credit (bis-of 3 1960 90.0)
        hh (bis-of 4 1975 40.0)
        house (update-vals (bis-of 5 1970 4.0) #(Math/log %))
        inside (mh/dated-inputs rows {"AAA" {:credit credit :mortgages hh}} 2002)
        scale (mh/panel-scale rows {"AAA" {:credit credit}} 2002)
        outside (mh/dated-inputs rows {"XXX" {:credit credit :mortgages hh :house-prices house}} 2002
                                 {:dims [:credit :mortgages :house-prices] :scale scale})
        hv (vec (for [y (range 1870 2025)] (get house y)))]
    (doseq [y (range 2002 2025) kind [:specific :short :long :flow] k [:credit :mortgages]]
      (is (close? (get-in inside [["AAA" y] kind k]) (get-in outside [["XXX" y] kind k])) (str y kind k)))
    (doseq [y (range 2002 2025)]
      (is (close? (get-in inside [["AAA" y] :gap :credit-gap-z]) (get-in outside [["XXX" y] :gap :credit-gap-z])) (str "gap " y))
      (is (close? (get-in inside [["AAA" y] :growth :credit]) (get-in outside [["XXX" y] :growth :credit])) (str "growth " y))
      (is (close? (* scale (- (credit y) (credit (- y 3)))) (get-in outside [["XXX" y] :growth :credit]))))
    (testing "house prices: the log real index against its own decade and twenty years, growth its three-year log change"
      (doseq [y [2002 2010 2020]]
        (is (close? (iv/stretch hv (- y 1870) 10 6) (get-in outside [["XXX" y] :specific :house-prices])))
        (is (close? (iv/stretch hv (- y 1870) 20 12) (get-in outside [["XXX" y] :long :house-prices])))
        (is (close? (- (house y) (house (- y 3))) (get-in outside [["XXX" y] :growth :house])))))
    (is (every? some? (map #(get-in outside (into [["XXX" 2010]] %)) [[:specific :credit] [:specific :house-prices] [:long :credit] [:gap :credit-gap-z] [:growth :credit]]))
        "the check is not of nils")
    (testing "inside the panel, nothing changes: no house prices, no house growth from the BIS"
      (is (nil? (get-in inside [["AAA" 2010] :specific :house-prices])))
      (is (nil? (get-in inside [["AAA" 2010] :growth :house]))))))

(deftest too-short-a-history-stays-unknown
  ;; a series from 2000: no stretch until six past years, no long stretch
  ;; until twelve, no gap until ten past gaps (nineteen years)
  (let [credit (bis-of 3 2000 50.0)
        v (mh/dated-inputs nil {"XXX" {:credit credit}} 2002 {:dims [:credit :mortgages :house-prices] :scale 0.006})]
    (is (nil? (get-in v [["XXX" 2005] :specific :credit])))
    (is (some? (get-in v [["XXX" 2006] :specific :credit])))
    (is (nil? (get-in v [["XXX" 2011] :long :credit])))
    (is (some? (get-in v [["XXX" 2012] :long :credit])))
    (is (nil? (get-in v [["XXX" 2018] :gap :credit-gap-z])))
    (is (some? (get-in v [["XXX" 2019] :gap :credit-gap-z])))))

(def ^:private cat {:onsets {} :from {} :through {}})

(defn- wide-values []
  {["XXX" 2006] {:specific {:credit 1.0 :mortgages 0.5 :house-prices 0.2} :long {:credit 0.4 :mortgages 0.3 :house-prices 0.1}
                 :growth {:credit 0.01 :house 0.05} :gap {:credit-gap-z 0.9}}
   ["XXX" 2007] {:specific {:credit 2.0 :mortgages 1.5 :house-prices 1.2} :long {:credit 1.4 :mortgages 1.3 :house-prices 1.1}
                 :growth {:credit 0.02 :house 0.15} :gap {:credit-gap-z 1.9}}})

(deftest outside-the-panel-a-value-is-read-only-once-published
  ;; the year node at December 2008 reads row 2007 (the BIS's fourth quarter
  ;; out in June 2008, JST's rows a year on); in November, row 2006
  (let [opts {:dated-credit {:from 2002 :values (wide-values)}}
        yr (fn [t] (#'lb/year-raw {} {} cat "XXX" t opts))
        half (fn [t] (#'lb/half-raw {} cat "XXX" t opts))
        a (count lb/year-specific) b (count lb/year-invariants) c (count lb/year-process) d (count lb/year-stabilizers)]
    (is (= [2.0 1.5 1.2] (subvec (yr (+ (* 12 2008) 11)) 0 3)))
    (is (= [1.0 0.5 0.2] (subvec (yr (+ (* 12 2008) 10)) 0 3)))
    (is (= [0.02 0.15] (subvec (yr (+ (* 12 2008) 11)) (+ a b c d 3) (+ a b c d 5))) "credit and house growth")
    (is (= [1.4 1.3 1.1 1.9] (subvec (half (+ (* 12 2008) 11)) 0 4)))
    (is (every? nil? (subvec (yr (+ (* 12 2008) 11)) 3 a)) "the other stretches stay unknown")
    (is (every? nil? (map #(nth (yr (+ (* 12 2008) 11)) %) (range a (+ a 7))))
        "the displacement needs five dimensions: unknown")
    (testing "before the splice, nothing"
      (is (nil? (first (#'lb/year-raw {} {} cat "XXX" (+ (* 12 2003) 11)
                                      {:dated-credit {:from 2004 :values {["XXX" 2002] {:specific {:credit 1.0}}}}})))))))

(deftest off-is-t79-bis
  ;; --wide-credit false: no row for a country outside the panel, so its
  ;; year node reads the world's quantities alone, as under --dated-credit
  (let [yr (#'lb/year-raw {} {} cat "XXX" (+ (* 12 2008) 11) {:dated-credit {:from 2002 :values {}}})]
    (is (every? nil? (take (count lb/year-specific) yr))))
  (testing "a JST country's row past the panel reads the same with house keys absent"
    (let [values {["AAA" 2022] {:specific {:credit 1.5 :mortgages -0.5} :growth {:credit 0.02}
                                :long {:credit 0.7 :mortgages 0.1} :gap {:credit-gap-z 1.1}}}
          r (#'lb/dated-row {} {:from 2002 :values values} "AAA" 2022)]
      (is (= {:credit 1.5 :mortgages -0.5} (:specific r)))
      (is (= {:credit 0.7 :mortgages 0.1 :credit-gap-z 1.1} (:specific-long r))))))

(deftest the-wide-values-cover-only-countries-outside-the-panel
  ;; finance/credit_gdp, finance/household_credit_gdp and
  ;; finance/real_house_prices (log) as fourth quarters
  (let [q (fn [vs] (sk/series (for [[y v] vs] [(+ (* 12 y) 11) (+ (* 12 y) 15) v])))
        years (range 1980 2011)
        db {["AAA" "finance/credit_gdp"] (q (for [y years] [y (+ 100.0 (* 5.0 (wiggle 1 y)))]))
            ["XXX" "finance/credit_gdp"] (q (for [y years] [y (+ 50.0 y -1980 (* 4.0 (wiggle 2 y)))]))
            ["XXX" "finance/real_house_prices"] (q (for [y years] [y (Math/log (+ 100.0 (* 9.0 (wiggle 3 y))))]))}
        rows (jst-rows "AAA" 1.0)
        w (lb/wide-credit-values db rows #{"AAA" "XXX" "YYY"})]
    (is (= #{"XXX"} (set (map first (keys w)))))
    (is (some? (get-in w [["XXX" 2008] :specific :house-prices])))
    (is (nil? (get-in w [["XXX" 2001] :specific :credit])) "before the splice, none")))

(deftest wide-credit-option
  (is (false? (wr/wide-credit-choice {})))
  (is (false? (wr/wide-credit-choice {:wide-credit "false"})))
  (is (true? (wr/wide-credit-choice {:wide-credit "true" :dated-credit "true"})))
  (is (thrown? Exception (wr/wide-credit-choice {:wide-credit "true"})) "needs --dated-credit true")
  (is (thrown? Exception (wr/wide-credit-choice {:wide-credit "yes" :dated-credit "true"})))
  (is (= :bank (wr/wide-credit-choice {:wide-credit "bank" :dated-credit "true"})) "bank credit for the credit input"))

(deftest bank-credit-for-the-credit-input
  ;; --wide-credit bank: the credit input outside the panel from the BIS's
  ;; bank credit (JST's concept), growth by the panel's ratio of JST's
  ;; loans to the BIS's bank credit; household credit and house prices as
  ;; under true
  (let [q (fn [vs] (sk/series (for [[y v] vs] [(+ (* 12 y) 11) (+ (* 12 y) 15) v])))
        years (range 1980 2011)
        rows (jst-rows "AAA" 1.0)
        all-lender (into {} (for [y years] [y (+ 300.0 (* 8.0 (- y 1980)) (* 9.0 (wiggle 2 y)))]))
        bank (into {} (for [y years] [y (+ 60.0 (* 0.5 (- y 1980)) (* 4.0 (wiggle 6 y)))]))
        aaa-bank (into {} (for [r rows] [(:year r) (* 100.0 (/ (:tloans r) (:gdp r)))]))
        db {["XXX" "finance/credit_gdp"] (q all-lender)
            ["XXX" "finance/household_credit_gdp"] (q (for [y years] [y (+ 40.0 (* 3.0 (wiggle 4 y)))]))}
        w (lb/wide-credit-values db rows #{"AAA" "XXX"} {:bank {"XXX" bank "AAA" aaa-bank}})
        v (vec (for [y (range 1870 2011)] (get bank y)))]
    (is (close? (iv/stretch v (- 2008 1870) 10 6) (get-in w [["XXX" 2008] :specific :credit])))
    (is (close? (* 0.01 (- (bank 2008) (bank 2005))) (get-in w [["XXX" 2008] :growth :credit]))
        "JST's loans are the BIS's bank credit here: the ratio is a percentage's")
    (is (= (get-in (lb/wide-credit-values db rows #{"AAA" "XXX"}) [["XXX" 2008] :specific :mortgages])
           (get-in w [["XXX" 2008] :specific :mortgages])))))
