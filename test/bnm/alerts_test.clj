(ns bnm.alerts-test
  "The alert layer at the user's 4:1 cost ratio (bnm.alerts)."
  (:require [bnm.alerts :as al]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-12))

(deftest the-threshold-is-the-cost-ratio
  ;; alert when p >= C_false / (C_false + C_miss)
  (is (close? 0.2 (al/threshold-of 4.0)))
  (is (close? 0.5 (al/threshold-of 1.0))))

(deftest an-alert-at-exactly-the-threshold
  (is (al/alert? 0.2 0.2) "p equal to the threshold alerts")
  (is (al/alert? (/ 1.0 (+ 1.0 4.0)) (al/threshold-of 4.0)))
  (is (not (al/alert? 0.19999999 0.2)))
  (is (al/alert? 0.9 0.2)))

(deftest the-cost-of-a-row
  ;; a miss costs 4, a false alarm 1, a hit or a quiet row nothing
  (let [cost (al/cost-fn 0.2 4.0)]
    (is (= 4.0 (cost 0.1 true)))
    (is (= 0.0 (cost 0.2 true)))
    (is (= 1.0 (cost 0.2 false)))
    (is (= 0.0 (cost 0.1 false)))))

(deftest hit-and-false-alarm-counts
  (let [rows [[0.20 true] [0.50 true] [0.10 true]       ; two hits, one miss
              [0.25 false] [0.05 false] [0.15 false] [0.19 false]] ; one false alarm
        {:keys [n pos hits misses false-alarms hit-rate false-alarm-rate alert-share cost]} (al/confusion rows 0.2 4.0)]
    (is (= [7 3 2 1 1] [n pos hits misses false-alarms]))
    (is (close? (/ 2.0 3) hit-rate))
    (is (close? 0.25 false-alarm-rate))
    (is (close? (/ 3.0 7) alert-share))
    (is (close? (/ (+ (* 1 4.0) 1.0) 7) cost) "(misses x 4 + false alarms) / rows")
    (is (close? (/ 4.0 7) (:constant (al/confusion rows 0.2 4.0))) "the better of never (3 x 4) and always (4 FA)")
    (is (close? (/ 3.0 7) (:constant (al/confusion rows 0.5 1.0))) "at 1:1, never alerting: min(3, 4)")))

(deftest empty-rows
  (let [c (al/confusion [] 0.2 4.0)]
    (is (= 0 (:n c)))
    (is (nil? (:hit-rate c)))
    (is (nil? (:false-alarm-rate c)))
    (is (nil? (:cost c))))
  (testing "no onsets: no hit rate, a false-alarm rate"
    (let [c (al/confusion [[0.3 false] [0.1 false]] 0.2 4.0)]
      (is (nil? (:hit-rate c)))
      (is (close? 0.5 (:false-alarm-rate c)))))
  (is (empty? (al/origins {})))
  (is (empty? (al/episodes {} {:rule :ratio :ratio 2.0 :min-rows 1}))))

(def ^:private forecasts
  ;; {[place origin] [p y]}: origin 2005 (24071) at rate 3/4, the others at 0 or 1/4
  (let [o #(+ (* 12 %) 11)]
    (merge
     {["AAA" (o 2003)] [0.10 false] ["BBB" (o 2003)] [0.10 false] ["CCC" (o 2003)] [0.10 false] ["DDD" (o 2003)] [0.10 true]
      ["AAA" (o 2004)] [0.10 false] ["BBB" (o 2004)] [0.10 false] ["CCC" (o 2004)] [0.10 false] ["DDD" (o 2004)] [0.10 false]
      ["AAA" (o 2005)] [0.30 true] ["BBB" (o 2005)] [0.10 true] ["CCC" (o 2005)] [0.10 true] ["DDD" (o 2005)] [0.30 false]
      ["AAA" (o 2006)] [0.10 false] ["BBB" (o 2006)] [0.10 true] ["CCC" (o 2006)] [0.10 false] ["DDD" (o 2006)] [0.10 false]})))

(deftest origins-and-episodes
  (let [os (al/origins forecasts)]
    (is (= [2003 2004 2005 2006] (map :year os)))
    (is (close? 0.75 (:rate (nth os 2))))
    (is (close? 0.2 (:p (nth os 2))) "the mean issued p"))
  (testing "ratio rule: the kind's rate over the span is 5/16, so 2x is 0.625"
    (let [[e & more] (al/episodes forecasts {:rule :ratio :ratio 2.0 :min-rows 1})]
      (is (= 2005 (:year e)))
      (is (nil? more))
      (is (:caught (al/caught e 0.2)) "mean p 0.2 at a threshold of 0.2 is caught")
      (is (not (:caught (al/caught e 0.21))))))
  (testing "decile rule: the top tenth of origins, at least one"
    (is (= [2005] (map :year (al/episodes forecasts {:rule :decile :min-rows 1})))))
  (testing "an origin with too few rows is not an episode"
    (is (empty? (al/episodes forecasts {:rule :ratio :ratio 2.0 :min-rows 5})))))

(deftest the-cost-difference
  ;; the run alerts at the onset the bench misses: the run is cheaper (+)
  (let [o #(+ (* 12 %) 11)
        places ["AAA" "BBB" "CCC" "DDD" "EEE"]
        years (range 2003 2013)
        y? (fn [pl yr] (and (= pl "AAA") (odd? yr)))
        run (into {} (for [pl places yr years] [[pl (o yr)] [(if (y? pl yr) 0.4 0.05) (y? pl yr)]]))
        bench (into {} (for [pl places yr years] [[pl (o yr)] [0.05 (y? pl yr)]]))
        {:keys [diff lo hi]} (al/cost-diff {"k" run} {"k" bench} 0.2 4.0 200)]
    (is (pos? diff) "bench cost minus run cost")
    (is (close? (/ (* 5 4.0) 50) diff) "five misses at 4 over 50 rows")
    (is (<= lo diff hi))
    (testing "and the other way round"
      (is (neg? (:diff (al/cost-diff {"k" bench} {"k" run} 0.2 4.0 200)))))
    (testing "an empty kind is left out"
      (is (close? (/ (* 5 4.0) 50) (:diff (al/cost-diff {"k" run "e" {}} {"k" bench "e" {}} 0.2 4.0 200)))))))

(deftest reading-a-run
  ;; December origins, resolved rows, year scale; countries or the world node
  (let [dir (io/file (System/getProperty "java.io.tmpdir") "bnm-alerts-test")
        f (io/file dir "forecasts.tsv")]
    (.mkdirs dir)
    (spit f (str "year\tAAA\t24071\t0.3\ttrue\n"
                 "year\tAAA\t24070\t0.3\ttrue\n"       ; November: not an origin
                 "year\tBBB\t24071\t0.1\t\n"           ; unresolved
                 "quarter-lead\tAAA\t24071\t0.9\tfalse\n"
                 "year\tr/asia\t24071\t0.2\tfalse\n"
                 "year\tworld\t24071\t0.8\ttrue\n"
                 "year\tCCC\t24251\t0.2\tfalse\n"))    ; 2020: outside 2003-2010
    (is (= {["AAA" 24071] [0.3 true]} (al/read-node (str f) :country 2003 2010)))
    (is (= {["world" 24071] [0.8 true]} (al/read-node (str f) :world 2003 2010)))))

(deftest the-fixed-outliers
  ;; CALIBRATION 10-13's table
  (is (= 9 (count al/outliers)))
  (is (some #{["food_price_shock" 2006]} al/outliers))
  (is (some #{["growth_collapse" 2019]} al/outliers)))
