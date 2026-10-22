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
    (is (nil? (:x (in :post3 "C" 400))) "before the catalogue covers the place: unknown")
    (is (nil? (:x (in :post3 "B" 400))) "a place it never covers")
    (testing "destabilization: the latest state less its own recent mean"
      (is (= 1.0 (:x (in :destab "A" 72))))
      (is (= (for [o [71 59 47 35 23 11]] {:dir :relation :kind "bank" :scale :year :place "A" :origin o})
             (:reads (in :destab "A" 72)))
          "the latest state and the five it is set against, each read")
      (is (nil? (:x (in :destab "A" 40))) "fewer than three before it: unknown")
      (is (nil? (:x (in :destab "B" 72)))))
    (is (thrown? clojure.lang.ExceptionInfo (in :level "A" 72)) "the level is not a link's form")))

(deftest raw-inputs-know-what-they-do-not-know
  ;; raw-input's nil where the value is unknown (not covered yet, no state
  ;; issued, too few to destabilize against), and edge-input's
  (let [cat {:onsets {"A" (sorted-set)} :from {"A" 0 "C" 500} :known-lag 12}
        book {[:year "A"] (sorted-map 11 {:f -2.0})}
        p {:kind "bank" :book book :catalogue cat}]
    (is (nil? (:x (rl/raw-input :post3 p :year "C" 400))))
    (is (nil? (:x (rl/raw-input :destab p :year "A" 40))))
    (is (nil? (:x (rl/raw-input :level p :year "A" 5))))
    (is (= -2.0 (:x (rl/raw-input :level p :year "A" 12))))
    (is (nil? (:x (rl/edge-input :destab p :year "A" 40))))
    (is (nil? (:x (rl/edge-input :post3 p :year "C" 400))))))
