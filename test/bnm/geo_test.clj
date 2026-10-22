(ns bnm.geo-test
  (:require [bnm.geo :as geo]
            [clojure.test :refer [deftest is]]))

(deftest country-names
  (is (= "DEU" (geo/resolve-name "Germany")))
  (is (= "NLD" (geo/resolve-name "Netherlands")) "GeoNames calls it The Netherlands")
  (is (= "NLD" (geo/resolve-name "NETHERLANDS")))
  (is (= "BHS" (geo/resolve-name "Bahamas, The")) "a trailing article")
  (is (= "GMB" (geo/resolve-name "The Gambia")) "a leading one")
  (is (nil? (geo/resolve-name "Atlantis")))
  (is (nil? (geo/resolve-name "The"))))
