(ns bnm.blocs-test
  "bnm.blocs (bnm-58f): a bloc rupture's exposure on real data, dependence
  on the power times the western tie, each as known when the rupture came."
  (:require [bnm.blocs :as bl]
            [clojure.test :refer [deftest is testing]]))

(def data
  {:dep {"FIN" {2011 {:value 0.15 :known 2013} 2012 {:value 0.14 :known 2014} 2013 {:value 0.13 :known 2015}
                2019 {:value 0.06 :known 2021} 2020 {:value 0.05 :known 2022}}
         "KAZ" {2012 {:value 0.2 :known 2014}}}
   :tie {"FIN" {2013 1.0 2021 1.0} "KAZ" {2013 0.0}}})

(defn- month [y m] (+ (* 12 y) (dec m)))

(deftest exposure-is-dependence-times-tie-as-known-at-the-rupture
  (testing "before the 2014 rupture, none"
    (is (= 0.0 (bl/exposure data "FIN" (month 2013 12)))))
  (testing "from March 2014 for 24 months: 2012's dependence (known in 2014) x 2013's tie"
    (is (= 0.14 (bl/exposure data "FIN" (month 2014 3))))
    (is (= 0.14 (bl/exposure data "FIN" (month 2015 12))))
    (is (= 0.14 (bl/exposure data "FIN" (month 2016 2))))
    (is (= 0.0 (bl/exposure data "FIN" (month 2016 3)))))
  (testing "a country with no tie bears none"
    (is (= 0.0 (bl/exposure data "KAZ" (month 2014 12)))))
  (testing "2022: 2020's dependence x 2021's tie"
    (is (= 0.05 (bl/exposure data "FIN" (month 2022 12)))))
  (testing "a country with no data, none"
    (is (= 0.0 (bl/exposure data "XXX" (month 2014 12))))))

(deftest the-addition-scales-the-exposure
  (let [f (bl/offset-add data 8.0)]
    (is (< (Math/abs (- 1.12 (f :year "FIN" (month 2014 12)))) 1e-12))
    (is (nil? (f :year "FIN" (month 2013 12))) "nothing where there is no exposure")
    (is (nil? (f :year "Europe" (month 2014 12))) "aggregates are left to their own climatology")))

(deftest reads-the-derived-files
  ;; data/ is not in git: without the files (CI) the test skips itself
  (if-let [missing (seq (remove #(.exists (java.io.File. ^String %)) ["data/derived/alignment-annual.csv" "data/derived/alignment-tie.csv"]))]
    (println "  (skipped:" (first missing) "not built)")
    (let [d (bl/read-data "data/derived/alignment-annual.csv" "data/derived/alignment-tie.csv")]
      (is (= 1.0 (get-in d [:tie "POL" 2013])))
      (is (= 2014 (get-in d [:dep "POL" 2012 :known]))))))
