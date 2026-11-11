(ns bnm.time-test
  (:require [bnm.time :as t]
            [clojure.test :refer [deftest is]]))

(deftest calendar-round-trips
  (doseq [d ["19700101" "19790101" "20000229" "20231007" "21001231"]]
    (is (= d (t/date-str (t/day d)))))
  (is (= 19637 (t/day "20231007"))))
