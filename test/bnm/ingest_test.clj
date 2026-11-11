(ns bnm.ingest-test
  (:require [bnm.ingest :as ingest]
            [clojure.test :refer [deftest is]]
            [dodeca.core :as a5]))

(defn- row
  "GDELT fields cut to `ingest/gdelt-a5-columns`: London-to-London protest
  located by ActionGeo type `geo` at `lat` `lon`."
  [geo lat lon]
  ["20261014" "GBR" "" "" "" "" "GBR" "" "" "" "" "14" geo "UK" "UKH9" lat lon])

(deftest a-located-event-lands-in-its-cell
  (let [[_ a1 _ _ _ adm] (ingest/gdelt-a5-row (row "4" "51.5074" "-0.1276"))
        cell (a5/lon-lat->cell [-0.1276 51.5074] ingest/cell-resolution)]
    (is (= "GBR" a1))
    (is (= (str "GBR.A" (.toUpperCase (a5/cell->hex cell))) adm))))

(deftest country-and-us-state-points-have-no-cell
  (is (nil? (nth (ingest/gdelt-a5-row (row "1" "54" "-2")) 5)))
  (is (nil? (nth (ingest/gdelt-a5-row (row "2" "40" "-105")) 5))))

(deftest a-row-without-coordinates-keeps-the-event
  (let [r (ingest/gdelt-a5-row (row "4" "" ""))]
    (is (= "GBR" (nth r 1)))
    (is (nil? (nth r 5)))))
