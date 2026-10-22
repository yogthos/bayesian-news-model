(ns bnm.geo-test
  (:require [bnm.geo :as geo]
            [clojure.test :refer [deftest is testing]]))

(deftest country-names
  (is (= "DEU" (geo/resolve-name "Germany")))
  (is (= "NLD" (geo/resolve-name "Netherlands")) "GeoNames calls it The Netherlands")
  (is (= "NLD" (geo/resolve-name "NETHERLANDS")))
  (is (= "BHS" (geo/resolve-name "Bahamas, The")) "a trailing article")
  (is (= "GMB" (geo/resolve-name "The Gambia")) "a leading one")
  (is (nil? (geo/resolve-name "Atlantis")))
  (is (nil? (geo/resolve-name "The")))
  (testing "dropping \"the\" makes no new match between the Congos"
    (is (= "COG" (geo/resolve-name "Congo")))
    (is (= "COG" (geo/resolve-name "Congo, The")))
    (is (= "COG" (geo/resolve-name "Republic of the Congo")))
    (is (= "COD" (geo/resolve-name "Democratic Republic of the Congo")))
    (is (= "COD" (geo/resolve-name "Congo, Democratic Republic of the")))
    (is (not= "COG" (geo/resolve-name "Congo, the Democratic Republic of")) "not the Republic")
    (is (= "SDN" (geo/resolve-name "The Sudan")))
    (is (= "SSD" (geo/resolve-name "South Sudan")))
    (is (= "NER" (geo/resolve-name "The Niger")))
    (is (= "ANT" (geo/resolve-name "The Netherlands Antilles")) "not the Netherlands")))
