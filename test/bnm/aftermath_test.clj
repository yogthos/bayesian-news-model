(ns bnm.aftermath-test
  "bnm.aftermath (reports/Crisis aftermath and trend reversal.md, rank 3):
  every kind's year node learns, on top of the log odds it already has,
  what its own and the other kinds' past onsets do to the next one, the
  sign left to the data."
  (:require [augur.math :as m]
            [bnm.aftermath :as af]
            [clojure.test :refer [deftest is testing]]))

(def ^:private close? #(< (Math/abs (- (double %1) (double %2))) 1e-9))

(deftest the-kernels-of-past-onsets
  (let [ons {"coup" {"A" [100 160]} "war_onset" {"A" [150] "B" [10]}}
        row (af/kernel-row ons "coup" ["coup" "war_onset"])]
    (testing "own onsets, halving each year (fast) and every four (slow), summed"
      (let [[fast slow ever other] (row "A" 172)]
        (is (close? fast (+ (Math/pow 0.5 (/ 72 12.0)) 0.5)) "72 and 12 months back")
        (is (close? slow (+ (Math/pow 0.5 (/ 72 48.0)) (Math/pow 0.5 (/ 12 48.0)))))
        (is (= 1.0 ever))
        (is (close? other (Math/pow 0.5 (/ 22 12.0))) "the other kinds' onsets, fast")))
    (testing "an onset after the origin is not known"
      (is (= [0.0 0.0 0.0 0.0] (row "A" 99)))
      (is (close? 1.0 (first (row "A" 100))) "one in the origin's month is"))
    (testing "a unit with none"
      (is (= [0.0 0.0 0.0 0.0] (row "C" 172))))
    (is (= 4 af/width))))

(defn- world [sign]
  ;; country-years whose onset chance rises (sign +1) or falls (-1) in the
  ;; year after an onset of their own
  (let [g (m/rng 7)
        us (mapv #(str "u" %) (range 40))
        os (range 11 1200 12)
        ys (reduce (fn [acc t]
                     (reduce (fn [acc u]
                               (let [prev (get acc [u (- t 12)])
                                     p (m/expit (+ -2.0 (if prev (* sign 1.5) 0.0)))]
                                 (assoc acc [u t] (< (m/next-double! g) p))))
                             acc us))
                   {} os)
        ;; an onset in the year after origin t is dated at t + 1
        ons {"k" (into {} (for [u us] [u (vec (for [t os :when (ys [u t])] (inc t)))]))}]
    {:ons ons :us us :os os
     :sp {:scales [{:name :year :per 12 :horizon 12}]
          :levels [[:country us] [:world ["world"]]]
          :parent (constantly "world") :start 0 :end 1199
          :target (fn [scale u t] (when (and (= :year scale) (contains? ys [u t]))
                                    {:y (ys [u t]) :known-at (+ t 12)}))
          :offset (fn [scale _ _] (when (= :year scale) -2.0))}}))

(deftest the-sign-is-learned
  (doseq [[sign want] [[1 pos?] [-1 neg?]]]
    (let [{:keys [ons sp os]} (world sign)
          sp2 (af/with-aftermath sp (af/kernel-row ons "k" ["k"]))
          b (get-in sp2 [:aftermath :fits (last os)])]
      (is (want (aget ^doubles b 1)) (str "own fast kernel, sign " sign))
      (is (= -2.0 ((:offset sp2) :year "u1" (last os))) "the offset kept")
      (testing "added to the year node, past the intercept, only at the year scale"
        (let [u "u1" t (last os) x ((af/kernel-row ons "k" ["k"]) u t)]
          (is (close? ((:offset-add sp2) :year u t)
                      (reduce + (map * (rest (vec b)) x))))
          (is (nil? ((:offset-add sp2) :quarter u t))))))))

(deftest it-adds-to-an-offset-add-already-there
  (let [{:keys [ons sp os]} (world 1)
        t (last os)
        sp1 (assoc sp :offset-add (fn [scale _ _] (when (= :year scale) 0.25)))
        alone (af/with-aftermath sp (af/kernel-row ons "k" ["k"]))
        both (af/with-aftermath sp1 (af/kernel-row ons "k" ["k"]))]
    (is (some? ((:offset-add both) :year "u1" t)))
    (testing "fit on the base the node already has (offset plus offset-add), the two summed"
      (let [b (get-in both [:aftermath :fits t])
            x ((af/kernel-row ons "k" ["k"]) "u1" t)]
        (is (close? ((:offset-add both) :year "u1" t)
                    (+ 0.25 (reduce + (map * (rest (vec b)) x)))))
        (is (not= (vec (get-in alone [:aftermath :fits t])) (vec b)))))))
