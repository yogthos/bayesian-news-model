(ns bnm.offset-add-test
  "bnm.lattice :offset-add (bnm-58f): a theory's log odds added on top of a
  node's offset, whether that is what is known or the climatology, the
  climatology part still marked as such."
  (:require [augur.math :as m]
            [bnm.lattice :as lt]
            [clojure.test :refer [deftest is testing]]))

(def countries ["a1" "a2" "b1" "b2"])
(def parent {"a1" "A" "a2" "A" "b1" "B" "b2" "B"})
(def months 240)

(defn- spec [seed]
  (let [g (m/rng seed)
        crisis (into {} (for [c countries y (range 20)] [[c y] (< (m/next-double! g) 0.15)]))]
    {:scales [{:name :year :per 12 :horizon 12 :discount 0.995}]
     :levels [[:country countries] [:region ["A" "B"]]]
     :parent parent
     :start 0 :end (dec months)
     :forget 0.99
     :target (fn [_ place origin]
               (let [cs (if (= 2 (count place)) [place] (filter #(= place (parent %)) countries))
                     y (quot (inc origin) 12)]
                 (when (< y 20)
                   {:y (boolean (some #(crisis [% y]) cs)) :known-at (+ origin 12)})))
     :gauges (fn [_ _ _] [(m/normal! g)])
     :gauge-width {:year 1}
     :trace :inputs}))

(defn- os [r] (into {} (for [{:keys [place origin o]} (:trace r)] [[place origin] o])))

(deftest the-added-log-odds-sit-on-the-climatology
  (let [base (os (lt/run (spec 3)))
        add (os (lt/run (assoc (spec 3) :offset-add (fn [_ place t] (when (and (= "a1" place) (>= t 119)) 1.5)))))]
    (is (seq base))
    (testing "a1 from the origin it applies: the climatology plus 1.5"
      (doseq [[[place t] o] add :when (and (= "a1" place) (>= t 119))]
        (is (< (Math/abs (- o (base [place t]) 1.5)) 1e-9) [place t])))
    (testing "everywhere else, unchanged (nil adds nothing)"
      (doseq [[[place t] o] add :when (not (and (= "a1" place) (>= t 119)))]
        (is (< (Math/abs (- o (base [place t]))) 1e-9) [place t])))))

(deftest the-added-log-odds-sit-on-what-is-known
  (let [known (fn [_ place _] (when (= "b1" place) -1.0))
        base (os (lt/run (assoc (spec 4) :offset known)))
        add (os (lt/run (assoc (spec 4) :offset known :offset-add (fn [_ place _] (when (#{"b1" "b2"} place) 0.5)))))]
    (doseq [[[place t] o] add :when (#{"b1" "b2"} place)]
      (is (< (Math/abs (- o (base [place t]) 0.5)) 1e-9) [place t]))
    (doseq [[[place t] o] add :when (= "b1" place)]
      (is (< (Math/abs (- o -0.5)) 1e-9)))))

(deftest it-refuses-what-it-would-ignore
  (is (thrown? Exception (lt/run (assoc (spec 5) :offset-add 1.0))))
  (is (thrown? Exception (lt/run (assoc (spec 5) :offset-add (fn [_ _ _] 1.0)
                                        :downscale {:year {:from :half-decade}})))))
