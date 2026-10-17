(ns bnm.slow-test
  (:require [bnm.math :as m]
            [bnm.slow :as slow]
            [clojure.test :refer [deftest is testing]]))

(deftest solves-symmetric-positive-definite-systems
  (let [a (double-array [4 1 0, 1 3 1, 0 1 2])
        b (double-array [1 2 3])
        x (m/solve-spd a b 3)]
    (doseq [i (range 3)]
      (is (< (Math/abs (- (aget b i) (reduce + (for [j (range 3)] (* (aget a (+ (* 3 i) j)) (aget x j))))))
             1e-9)))))

(def vars [{:name "economy/output" :domain "economy" :transform "log"}
           {:name "energy/oil" :domain "energy" :transform "log"}
           {:name "food/prices" :domain "food" :transform "log"}])

(deftest trends-at-several-scales
  ;; a price that grows steadily, then faster: its trends are positive, and its
  ;; acceleration turns positive when growth speeds up
  (let [mdl (slow/create {} vars)
        feed (fn [mdl months growth]
               (reduce (fn [mdl t]
                         (slow/step! mdl t [{:unit "world" :var "energy/oil" :period t
                                             :value (Math/exp (growth t))}]))
                       mdl months))
        steady #(* 0.01 %)
        mdl (feed mdl (range 0 200) steady)
        before (slow/features mdl "world" "energy/oil")
        mdl (feed mdl (range 200 236) #(+ (steady 200) (* 0.05 (- % 200))))
        after (slow/features mdl "world" "energy/oil")]
    (is (pos? (:trend-12-36 before)))
    (is (< (Math/abs (:accel before)) 0.5) "steady growth does not accelerate")
    (is (> (:accel after) 1.0) "faster growth does")
    (is (> (:gap-120 after) (:gap-120 before)))))

(deftest mixes-frequencies-and-respects-availability
  (let [mdl (slow/create {} vars)
        ;; an annual value for period 11 (December) that becomes known in month 20
        mdl (slow/step! mdl 11 [])
        mdl (slow/step! mdl 20 [{:unit "EGY" :var "economy/output" :period 11 :value 100.0}])
        mdl (reduce (fn [mdl y]
                      (slow/step! mdl (+ 20 (* 12 y))
                                  [{:unit "EGY" :var "economy/output" :period (+ 11 (* 12 y))
                                    :value (* 100.0 (Math/pow 1.03 y))}]))
                    mdl (range 1 15))
        f (slow/features mdl "EGY" "economy/output")]
    (is (pos? (:trend-12-36 f)) "steady annual growth shows as a trend")
    (is (= (+ 11 (* 12 14)) (slow/last-period mdl "EGY" "economy/output")))))

(defn- driven-world
  "Monthly oil (a random walk with drifts that persist) in the world, and food
  prices in ten countries whose growth follows oil's recent trend two months
  later, plus noise."
  [months seed]
  (let [g (m/rng seed)]
    (loop [t 0 oil 0.0 drift 0.0 hist [] out []]
      (if (< t months)
        (let [drift (+ (* 0.97 drift) (* 0.004 (m/normal! g)))
              oil (+ oil drift (* 0.02 (m/normal! g)))
              hist (conj hist oil)
              lagged (if (> t 14) (- (hist (- t 2)) (hist (- t 14))) 0.0)]
          (recur (inc t) oil drift hist
                 (conj out (vec (cons {:unit "world" :var "energy/oil" :period t :value (Math/exp oil)}
                                      (for [c (range 10)]
                                        {:unit (str "C" c) :var "food/prices" :period t
                                         :food-growth (+ (* 0.08 lagged) (* 0.01 (m/normal! g)))}))))))
        out))))

(defn- run-world [cfg months seed]
  ;; food prices integrate their growth
  (let [levels (atom {})]
    (reduce (fn [mdl [t obs]]
              (slow/step! mdl t (for [o obs]
                                  (if-let [gr (:food-growth o)]
                                    (let [lv (swap! levels update (:unit o) (fnil + 0.0) gr)]
                                      (-> o (dissoc :food-growth) (assoc :value (Math/exp (get lv (:unit o))))))
                                    o))))
            (slow/create cfg vars)
            (map-indexed vector (driven-world months seed)))))

(deftest learns-how-one-domain-drives-another
  (let [mdl (run-world {:globals [["world" "energy/oil"]]} 600 3)
        sk (slow/skill mdl "food/prices")
        top (first (slow/drivers mdl "food/prices"))]
    (testing "oil's trend is the strongest driver of food prices"
      (is (= ["world" "energy/oil"] (:source top)) (pr-str (take 3 (slow/drivers mdl "food/prices")))))
    (testing "and knowing it beats both a random walk and food's own history, out of sample"
      (is (< (:full sk) (:own sk)) (pr-str sk))
      (is (< (:full sk) (:zero sk)) (pr-str sk)))))

(deftest model-averaging-keeps-what-helps
  ;; the averaged forecast is about as good as the better of the two models,
  ;; whether the other domains help (oil drives food) or are noise (food alone)
  (let [helps (slow/skill (run-world {:globals [["world" "energy/oil"]]} 600 3) "food/prices")]
    (is (<= (:dma helps) (* 1.02 (min (:own helps) (:full helps)))) (pr-str helps))
    (is (< (:dma helps) (:own helps)) (pr-str helps))
    (testing "and where the outside inputs only add noise, it falls back on the series' own"
      (let [mdl (reduce (fn [mdl t]
                          (slow/step! mdl t [{:unit "world" :var "energy/oil" :period t
                                              :value (Math/exp (Math/sin (* 0.37 t)))}
                                             {:unit "C1" :var "economy/output" :period t
                                              :value (Math/exp (* 0.01 t (+ 1.0 (* 0.3 (Math/sin (* 0.05 t))))))}]))
                        (slow/create {:globals [["world" "energy/oil"]] :cross-shrink 0.001} vars)
                        (range 600))
            sk (slow/skill mdl "economy/output")]
        (is (<= (:dma sk) (* 1.05 (min (:own sk) (:full sk)))) (pr-str sk))))))

(deftest scenarios-propagate-a-shock-through-the-drivers
  ;; oil's trend drives food prices: a sustained rise in oil, rolled forward
  ;; through the learned regressions, raises a country's food prices
  (let [mdl (run-world {:globals [["world" "energy/oil"]]} 600 3)
        resp (slow/scenario mdl {:unit "C1" :shocks [["world" "energy/oil" 0.3]] :months 12})
        food (get resp "food/prices")]
    (is (= 12 (count food)))
    (is (pos? (last food)) (pr-str food))
    (testing "the model itself is untouched"
      (let [again (slow/scenario mdl {:unit "C1" :shocks [["world" "energy/oil" 0.3]] :months 12})]
        (is (= food (get again "food/prices")))))))
