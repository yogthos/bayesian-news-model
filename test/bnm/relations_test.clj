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
    (is (= 0.0 (rl/excursion cat "B" 200 12.0)) "a place the catalogue does not cover"))
  (testing "a place at its steady rate has no excursion on average, whatever the lag"
    (doseq [lag [0 12 18] tau [12.0 60.0]]
      (let [cat {:onsets {"A" (into (sorted-set) (range 0 2400 24))} :from {"A" 0} :known-lag lag}
            xs (for [t (range 600 2400)] (rl/excursion cat "A" t tau))]
        (is (< (Math/abs (/ (reduce + xs) (count xs))) 0.03) (str "lag " lag " tau " tau))))))

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
    (is (= 0.0 (rl/coupling books ["a" "b" "c"] :year "P" 11)) "nothing from before t")
    (testing "the top fifth among the place's peers: a region's state sets no country's threshold"
      (let [books (update books "a" assoc [:year "Africa"] (sorted-map 11 {:f 10.0}))
            country? #(= 1 (count %))]
        (is (= 2.0 (rl/coupling books ["a" "b" "c"] :year "P" 23 #(rl/stress-cut (books %) :year 23 country?))))
        (is (= 1.0 (rl/coupling books ["a" "b" "c"] :year "P" 23 #(rl/stress-cut (books %) :year 23 (constantly true))))
            "without the peers the region raises the cut")))))

(deftest the-principal-contradiction
  ;; the candidate group contributing most to the log odds (coefficients
  ;; times inputs), by size; its signed contribution
  (let [means [0.0 0.5 -2.0 1.0 0.1]
        x [1.0 1.0 0.5 0.2 6.0]
        groups {:a [1] :b [2] :c [3 4] :gauges [1 2]}]
    (is (= [:b -1.0] (rl/principal means x groups #{:a :b :c})))
    (is (= [:c 0.8] (rl/principal means x groups #{:a :c})))
    (is (nil? (rl/principal means x groups #{})))))

(deftest one-input-per-link
  ;; what a parent passes along one edge, in the edge's form: :post1 and
  ;; :post3 an onset known in the last one or three years (the post-crisis
  ;; regime), :e1 and :e5 the excursions, :destab the parent's state less
  ;; its own mean over the five origins before; never its level
  (let [cat {:onsets {"A" (sorted-set 100)} :from {"A" 0 "C" 500} :known-lag 12}
        book {[:year "A"] (into (sorted-map) (for [[o f] [[11 -2.0] [23 -2.0] [35 -2.0] [47 -2.0] [59 -2.0] [71 -1.0]]] [o {:f f}]))}
        in (fn [form place t] (rl/edge-input form {:kind "bank" :book book :catalogue cat} :year place t))]
    (is (= 0.0 (:x (in :post3 "A" 111))) "not known yet")
    (is (= 1.0 (:x (in :post3 "A" 112))))
    (is (= 1.0 (:x (in :post3 "A" 147))))
    (is (= 0.0 (:x (in :post3 "A" 148))) "three years after it became known")
    (is (= 1.0 (:x (in :post1 "A" 123))))
    (is (= 0.0 (:x (in :post1 "A" 124))))
    (is (= (rl/excursion cat "A" 150 12.0) (:x (in :e1 "A" 150))))
    (is (= (rl/excursion cat "A" 150 60.0) (:x (in :e5 "A" 150))))
    (is (= 0.0 (:x (in :post3 "C" 400))) "before the catalogue covers the place")
    (is (= 0.0 (:x (in :post3 "B" 400))) "a place it never covers")
    (testing "destabilization: the latest state less its own recent mean"
      (is (= 1.0 (:x (in :destab "A" 72))))
      (is (= [{:dir :relation :kind "bank" :scale :year :place "A" :origin 71}] (:reads (in :destab "A" 72))))
      (is (= 0.0 (:x (in :destab "A" 40))) "fewer than three before it: unknown")
      (is (= 0.0 (:x (in :destab "B" 72)))))
    (is (thrown? clojure.lang.ExceptionInfo (in :level "A" 72)) "the level is not a link's form")))

(deftest raw-inputs-know-what-they-do-not-know
  ;; raw-input's nil where the value is unknown (not covered yet, no state
  ;; issued, too few to destabilize against); edge-input reads it as 0
  (let [cat {:onsets {"A" (sorted-set)} :from {"A" 0 "C" 500} :known-lag 12}
        book {[:year "A"] (sorted-map 11 {:f -2.0})}
        p {:kind "bank" :book book :catalogue cat}]
    (is (nil? (:x (rl/raw-input :post3 p :year "C" 400))))
    (is (nil? (:x (rl/raw-input :destab p :year "A" 40))))
    (is (nil? (:x (rl/raw-input :level p :year "A" 5))))
    (is (= -2.0 (:x (rl/raw-input :level p :year "A" 12))))
    (is (= 0.0 (:x (rl/edge-input :destab p :year "A" 40))))
    (is (= 0.0 (:x (rl/edge-input :post3 p :year "C" 400))))))
