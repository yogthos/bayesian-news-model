(ns bnm.econ-coupling-test
  (:require [augur.math :as m]
            [bnm.econ-coupling :as ec]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(deftest the-two-way-fixed-effects-slope-is-recovered
  ;; y = 0.7 x + pair effect + year effect + noise, x correlated with the
  ;; pair effect (the reason for the fixed effects)
  (let [g (m/rng 3)
        pairs (range 60) years (range 2001 2021)
        pe (into {} (for [p pairs] [p (* 3.0 (m/normal! g))]))
        ye (into {} (for [y years] [y (m/normal! g)]))
        rows (vec (for [p pairs y years
                        :let [x (+ (pe p) (m/normal! g))]]
                    {:g p :t y :x [x] :y (+ (* 0.7 x) (pe p) (ye y) (* 0.5 (m/normal! g)))}))
        {:keys [b se n groups]} (ec/fe-ols rows)]
    (is (close? 0.7 (first b) 0.05) (str "b " (vec b)))
    (is (< 0.0 (first se) 0.05))
    (is (= 1200 n))
    (is (= 60 groups))
    (testing "two regressors: the interaction's slope too"
      (let [rows (vec (for [r rows :let [x (first (:x r)) z (m/normal! g)]]
                        (assoc r :x [x (* x z)] :y (+ (:y r) (* -0.4 x z)))))
            {:keys [b]} (ec/fe-ols rows)]
        (is (close? 0.7 (first b) 0.05))
        (is (close? -0.4 (second b) 0.05))))))

(deftest winsorizing-clips-the-tails-only
  (let [xs (concat (range 100) [1e6 -1e6])
        f (ec/winsorizer xs 0.01)]
    (is (= 50.0 (f 50)))
    (is (< (f 1e6) 1000))
    (is (> (f -1e6) -1000))))

(def pairs-rows
  ;; holder H, issuer I: claims rise by 10 a year, surplus 10 a year, I's GDP 100
  (vec (for [y (range 2001 2011)]
         {:h "H" :i "I" :year y :known (+ y 2) :surplus 10.0 :pip (* 10.0 (- y 2000)) :dip nil :gcap nil :gdp-i 100.0})))

(deftest changes-come-from-consecutive-years-only
  (let [obs (ec/observations pairs-rows :pip)]
    (is (= 9 (count obs)) "the first year has no change")
    (is (every? #(close? 0.1 (:y %) 1e-12) obs))
    (is (every? #(close? 0.1 (first (:x %)) 1e-12) obs))
    (testing "a missing year breaks the change"
      (let [gap (vec (remove #(= 2005 (:year %)) pairs-rows))]
        (is (= 7 (count (ec/observations gap :pip))))))
    (testing "each observation is known when its later year is"
      (is (every? #(= (+ 2 (:t %)) (:known %)) obs)))))

(deftest the-combined-position-needs-both-parts
  (let [rows [{:pip 1.0 :dip 2.0} {:pip 1.0 :dip nil}]]
    (is (= 3.0 (ec/position (first rows) :pip+dip)))
    (is (nil? (ec/position (second rows) :pip+dip)))))

(deftest stock-rows-read-nothing-later
  (let [rows (vec (for [h ["A" "B" "C" "D"] y (range 2001 2012)]
                    {:h h :i "I" :year y :known (+ y 2) :surplus (if (#{"A" "B"} h) 5.0 -5.0)
                     :pip (* (if (#{"A" "B"} h) 3.0 1.0) (- y 2000)) :dip 1.0 :gcap nil :gdp-i 100.0}))
        st (ec/stock-rows rows)
        share (filter #(= "econ/coupling-liability-share" (:variable %)) st)]
    (is (seq share))
    (doseq [r st]
      (is (= (:year r) (:known r)))
      (is (>= (:known r) (+ 2 (:data-year r)))))
    (testing "the liability share: claims of partners with a surplus over all"
      (let [r (first (filter #(= 2005 (:data-year %)) share))]
        ;; A and B: 3*5 + 1 each; C and D: 5 + 1 each
        (is (close? (/ 32.0 44.0) (:value r) 1e-12))))
    (testing "values after a data year change nothing at it"
      (let [later (mapv #(if (> (:year %) 2006) (update % :pip * 100.0) %) rows)
            at (fn [st] (set (filter #(<= (:data-year %) 2006) st)))]
        (is (= (at st) (at (ec/stock-rows later))))))))
