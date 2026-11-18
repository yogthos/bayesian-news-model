(ns bnm.metrics-gate-test
  "The code-quality ratchet: CI fails when a change adds a near-duplicate
  pair, a critical function, an unused require or private function, or a
  large new recurring pattern that the committed baseline does not hold."
  (:require [bnm.metrics-gate :as mg]
            [clojure.test :refer [deftest is testing]]))

(def report
  {:queue [{:path "src/a.clj" :name "f" :severity :critical :line 10}
           {:path "src/a.clj" :name "g" :severity :warning :line 30}]
   :duplicates [{:distance 0.1 :a {:path "src/b.clj" :name "x" :line 1} :b {:path "src/a.clj" :name "y" :line 9}}]
   :abstractions [{:pattern '(into {} (for [?a ?b] ?c)) :uses 40 :saving 300}
                  {:pattern '(+ ?a 1) :uses 3 :saving 10}]
   :cleanup {:unused-requires [{:path "src/a.clj" :lib 'clojure.string :alias 'str}]
             :unused-private [{:path "src/b.clj" :name "h" :line 4}]
             :unused-public [{:path "src/b.clj" :name "k" :line 5}]}})

(deftest identities-ignore-line-numbers-and-pair-order
  (let [ids (mg/identities report)]
    (is (= #{["src/a.clj" "f"]} (:critical ids)) "warnings are not gated")
    (is (= #{#{["src/a.clj" "y"] ["src/b.clj" "x"]}} (:duplicates ids)))
    (is (= #{["src/a.clj" "clojure.string"]} (:unused-requires ids)))
    (is (= #{["src/b.clj" "h"]} (:unused-private ids)))
    (is (= #{"(into {} (for [?a ?b] ?c))"} (:abstractions ids)) "only patterns large enough to gate")
    (is (= (mg/identities report)
           (mg/identities (-> report
                              (assoc-in [:queue 0 :line] 99)
                              (assoc :duplicates [{:distance 0.1 :a {:path "src/a.clj" :name "y" :line 2} :b {:path "src/b.clj" :name "x" :line 3}}]))))
        "moving code or swapping a pair is not a new finding")))

(deftest the-gate-fails-only-on-what-is-new
  (let [base (mg/identities report)]
    (testing "the same report passes"
      (is (mg/pass? (mg/compare-ids base (mg/identities report)))))
    (testing "a new near-duplicate fails, naming it"
      (let [r (update report :duplicates conj {:distance 0.2 :a {:path "src/c.clj" :name "z"} :b {:path "src/a.clj" :name "f"}})
            c (mg/compare-ids base (mg/identities r))]
        (is (not (mg/pass? c)))
        (is (= #{#{["src/c.clj" "z"] ["src/a.clj" "f"]}} (get-in c [:new :duplicates])))))
    (testing "a new critical function fails"
      (is (not (mg/pass? (mg/compare-ids base (mg/identities (update report :queue conj {:path "src/c.clj" :name "q" :severity :critical})))))))
    (testing "removing findings passes and lists them, so the baseline can be lowered"
      (let [c (mg/compare-ids base (mg/identities (assoc report :duplicates [] :queue [])))]
        (is (mg/pass? c))
        (is (= #{["src/a.clj" "f"]} (get-in c [:gone :critical])))))))
