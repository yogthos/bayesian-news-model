(ns bnm.world-run-test
  (:require [bnm.world-run :as wr]
            [clojure.test :refer [deftest is]]))

(deftest the-placebo-reads-another-country
  (let [src (wr/placebo-source ["CCC" "AAA" "BBB"])]
    (is (= ["BBB" "CCC" "AAA"] (map src ["AAA" "BBB" "CCC"])))
    (is (= "Western Africa" (src "Western Africa")) "a region reads itself")))

(deftest one-nested-input-per-parent
  (let [cat {:onsets {"AAA" (sorted-set 100)} :from {"AAA" 0} :known-lag 12}
        books (atom {"bank" {} "infl" {}})
        sp (wr/with-relations {:scales [{:name :year}]} ["bank" "infl"] books {"bank" cat "infl" cat}
                              {"bank" #{:year} "infl" #{:year}}
                              {"bank" {:form :post3 :sign 1} "infl" {:form :e1 :sign -1}}
                              identity)
        {:keys [x]} ((:relations sp) :year "AAA" 120)]
    (is (= 2 (count x)))
    (is (= 1.0 (first x)) "bank's onset known in the last three years")
    (is (:relation-nest sp))
    (is (= {:rel/bank [0] :rel/infl [1]} (get-in sp [:relation-groups :year])))
    (is (= {7 [0.2 0.1] 8 [-0.2 0.1]} ((:prior sp) :year :country {:parts {:rel/bank [7] :rel/infl [8]}})))))

(deftest a-region-reads-its-countries
  ;; above the countries, the mean of the members' inputs: the share of a
  ;; region's countries in the parent's post-crisis regime
  (let [cat {:onsets {"AAA" (sorted-set 100)} :from {"AAA" 0 "BBB" 0} :known-lag 12}
        sp (wr/with-relations {:scales [{:name :year}] :parent {"AAA" "Reg" "BBB" "Reg" "Reg" "world"}}
                              ["bank"] (atom {"bank" {}}) {"bank" cat} {"bank" #{:year}}
                              {"bank" {:form :post3 :sign 1}} identity)]
    (is (= [0.5] (:x ((:relations sp) :year "Reg" 120))))
    (is (= [0.5] (:x ((:relations sp) :year "world" 120))))
    (is (= [1.0] (:x ((:relations sp) :year "AAA" 120))))))

(deftest no-parent-no-relations
  (let [sp {:scales [{:name :year}] :prior nil}]
    (is (= sp (wr/with-relations sp [] (atom {}) {} {} {} identity))
        "a kind without a parent is the world without relations")))
