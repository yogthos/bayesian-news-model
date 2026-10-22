(ns bnm.material-test
  (:require [bnm.material :as mat]
            [clojure.test :refer [deftest is testing]]))

(def plants
  ;; two gas plants in one place, one far away; a coal plant
  [{:iso "AAA" :fuel "gas" :mw 300.0 :lon 10.0 :lat 50.0}
   {:iso "AAA" :fuel "gas" :mw 100.0 :lon 10.001 :lat 50.001}
   {:iso "AAA" :fuel "gas" :mw 100.0 :lon 20.0 :lat 40.0}
   {:iso "AAA" :fuel "coal" :mw 500.0 :lon 12.0 :lat 48.0}])

(deftest assets-to-cells
  (let [cells (mat/cells plants 6)]
    (is (= 3 (count cells)) "the two gas plants share a cell")
    (is (= 400.0 (get-in (first (filter #(= 400.0 (get-in % [:fuels "gas"])) cells)) [:fuels "gas"])))
    (is (= 1000.0 (reduce + (map :mw cells))) "nothing lost")))

(deftest concentration-of-capacity
  ;; the Herfindahl index of a country's capacity across cells, by fuel,
  ;; and the largest cell's share: how much one local shock takes
  (let [c (mat/concentration (mat/cells plants 6))]
    (is (< (Math/abs (- (get-in c ["AAA" "gas" :hhi]) (+ (* 0.8 0.8) (* 0.2 0.2)))) 1e-12))
    (is (= 0.8 (get-in c ["AAA" "gas" :top-share])))
    (is (= 1.0 (get-in c ["AAA" "coal" :hhi])) "one cell: all of it")
    (is (= 2 (get-in c ["AAA" "gas" :cells])))))

(deftest csv-lines-with-quoted-commas
  (is (= ["AFG" "Kajaki, Helmand" "33.0" ""] (mat/split-csv "AFG,\"Kajaki, Helmand\",33.0,")))
  (is (= ["a" "say \"hi\"" "b"] (mat/split-csv "a,\"say \"\"hi\"\"\",b"))))
