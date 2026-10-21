(ns bnm.onsets-test
  (:require [augur.hier :as hier]
            [augur.logit :as lgt]
            [augur.math :as m]
            [bnm.onsets :as on]
            [clojure.test :refer [deftest is testing]]))

(deftest the-tree-above-a-country
  (is (= ["r/asia" "s/western-asia" "SYR"] (on/hier-path "SYR")))
  (is (= ["XXX"] (on/hier-path "XXX")) "a unit outside the tree is its own"))

(deftest nested-intercepts-beat-one-intercept-where-regions-differ
  ;; two regions of four subregions of five countries; onsets depend on an
  ;; input and on the region, which the input does not carry
  (let [g (m/rng 8)
        paths (for [r (range 2) s (range 4) c (range 5)] [(str "r" r) (str "r" r "s" s) (str "r" r "s" s "c" c)])
        draw (fn [n] (vec (for [path paths _ (range n)
                                :let [x (m/normal! g)
                                      eta (+ -3.0 (* 0.8 x) (if (= "r0" (first path)) 1.2 -0.4))]]
                            {:x (double-array [1.0 x]) :y (< (m/next-double! g) (m/expit eta)) :w 1.0 :path path})))
        train (draw 60)
        test (draw 60)
        flat (lgt/fit train 2 {:prior 1.0})
        {:keys [beta fx prior]} (on/fit-hier train 2 {} on/hier-opts)
        loss (fn [f] (/ (reduce + (for [{:keys [y] :as r} test :let [p (f r)]] (- (Math/log (if y p (- 1.0 p)))))) (count test)))]
    (is (< (loss #(lgt/predict beta (:x %) (hier/effect fx (:path %))))
           (loss #(lgt/predict flat (:x %)))))
    (testing "the slope is still found"
      (is (< 0.6 (aget ^doubles beta 1) 1.0) (vec beta)))
    (testing "the coefficients' prior precision is estimated with the variances"
      (is (< 0.0 prior 1e12) prior))))

(deftest parallel-fits-keep-their-order
  (let [ran (atom 0)
        thunks (for [i (range 23)] #(do (swap! ran inc) (* i i)))]
    (is (= (mapv #(* % %) (range 23)) (on/in-parallel 4 thunks)))
    (is (= 23 @ran) "each once")
    (is (= (on/in-parallel 1 thunks) (on/in-parallel 8 thunks)))
    (is (= [] (on/in-parallel 3 [])))))

(deftest non-finite-forecasts-stop-the-run
  (is (= 0.25 (on/checked 0.25 {:model :history})))
  (let [e (try (on/checked ##NaN {:model :hier-history :year 2003}) nil
               (catch Exception e e))]
    (is e "NaN throws")
    (is (= 2003 (:year (ex-data e)))))
  (is (thrown? Exception (on/checked ##Inf {}))))

(deftest country-means-look-only-backwards
  ;; the Mundlak (within-between) terms: each country's mean of the inputs
  ;; past the constant and the two history columns, from rows before the cut
  (let [row (fn [u q a b] {:unit u :q q :x (double-array [1.0 0.0 0.0 a b])})
        rows [(row "A" 0 1.0 10.0) (row "A" 1 3.0 20.0) (row "A" 8 100.0 100.0) (row "B" 0 5.0 0.0)]
        means (on/country-means rows 3 8)
        extend (on/with-means means)]
    (is (= [2.0 15.0] (vec (get-in means [:by "A"]))) "A's rows before quarter 8 only")
    (is (= [1.0 0.0 0.0 7.0 9.0 2.0 15.0] (vec (extend "A" (double-array [1.0 0.0 0.0 7.0 9.0]))))
        "the row's own inputs, then its country's means")
    (is (= [3.0 10.0] (drop 5 (vec (extend "C" (double-array [1.0 0.0 0.0 0.0 0.0])))))
        "a country with no earlier rows takes the mean of all rows before the cut")))

(deftest hier-fits-stop-once-settled
  (let [g (m/rng 9)
        paths (for [r (range 2) s (range 3) c (range 4)] [(str "r" r) (str "r" r "s" s) (str "r" r "s" s "c" c)])
        train (vec (for [path paths _ (range 40)
                         :let [x (m/normal! g)]]
                     {:x (double-array [1.0 x]) :y (< (m/next-double! g) (m/expit (+ -2.5 (* 0.8 x)))) :w 1.0 :path path}))
        capped (on/fit-hier train 2 {} on/hier-opts)
        cold (on/fit-hier train 2 {} (assoc on/hier-opts :rounds 100))
        warm (on/fit-hier train 2 cold on/hier-opts)]
    (is (<= (:rounds capped) (:rounds on/hier-opts)))
    (is (< (:rounds cold) 100) "it settles")
    (is (<= (:rounds warm) 2) "started where it settled, it stops at once")))
