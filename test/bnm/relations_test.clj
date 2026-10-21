(ns bnm.relations-test
  (:require [bnm.relations :as rl]
            [clojure.test :refer [deftest is testing]]))

(deftest onsets-as-an-excursion-known-by-then
  ;; onsets known by t (month + known-lag <= t), decaying over tau months,
  ;; less what the place's rate so far would give
  (let [cat {:onsets {"A" (sorted-set 100)} :from {"A" 0} :known-lag 12}]
    (is (= 0.0 (rl/excursion cat "A" 111 12.0)) "not yet known")
    (is (pos? (rl/excursion cat "A" 112 12.0)) "known: above the place's level")
    (is (> (rl/excursion cat "A" 112 12.0) (rl/excursion cat "A" 160 12.0)) "fading")
    (is (= 0.0 (rl/excursion cat "B" 200 12.0)) "a place the catalogue does not cover")))

(deftest a-parent-s-state-from-before-t
  (let [book {[:year "A"] (sorted-map 11 {:f -2.0 :q 0.3} 23 {:f -1.0 :q 0.2})}
        cat {:onsets {"A" (sorted-set)} :from {"A" 0} :known-lag 12}
        {:keys [x reads]} (rl/parent-inputs {:kind "bank" :book book :catalogue cat} :year "A" 23 0.5)]
    (is (= -2.0 (first x)) "the latest issued before t, not at t")
    (is (= 4 (count x)))
    (is (= -1.0 (nth x 3)) "the state through the moderator")
    (is (= [{:dir :relation :kind "bank" :scale :year :place "A" :origin 11}] reads))
    (testing "none issued yet: zeros, nothing read"
      (let [{:keys [x reads]} (rl/parent-inputs {:kind "bank" :book book :catalogue cat} :year "A" 11 0.5)]
        (is (= [0.0 0.0 0.0 0.0] x))
        (is (empty? reads))))))

(deftest dimensions-stressed-together
  ;; how many kinds stand in their own top fifth across places at their
  ;; latest origin before t
  (let [mk (fn [fs] (into {} (for [[p f] fs] [[:year p] (sorted-map 11 {:f f})])))
        books {"a" (mk {"P" 3.0 "Q" 0.0 "R" 0.1 "S" 0.2 "T" 0.3})
               "b" (mk {"P" 2.0 "Q" 0.0 "R" 0.1 "S" 0.2 "T" 0.3})
               "c" (mk {"P" 0.0 "Q" 5.0 "R" 0.1 "S" 0.2 "T" 0.3})}]
    (is (= 2.0 (rl/coupling books ["a" "b" "c"] :year "P" 23)))
    (is (= 1.0 (rl/coupling books ["a" "b" "c"] :year "Q" 23)))
    (is (= 0.0 (rl/coupling books ["a" "b" "c"] :year "P" 11)) "nothing from before t")))

(deftest the-principal-contradiction
  ;; the candidate group contributing most to the log odds (coefficients
  ;; times inputs), by size; its signed contribution
  (let [means [0.0 0.5 -2.0 1.0 0.1]
        x [1.0 1.0 0.5 0.2 6.0]
        groups {:a [1] :b [2] :c [3 4] :gauges [1 2]}]
    (is (= [:b -1.0] (rl/principal means x groups #{:a :b :c})))
    (is (= [:c 0.8] (rl/principal means x groups #{:a :c})))
    (is (nil? (rl/principal means x groups #{})))))
