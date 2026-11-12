(ns bnm.world-state-test
  "The world node's state (docs/RECURSIVE.md §3, recursive step 2): world
  series read as of t with their publication lags (loop audit Test 3b)."
  (:require [bnm.node :as node]
            [bnm.world-state :as ws]
            [clojure.test :refer [deftest is testing]]))

(defn- monthly
  "Entries [period available value] from month `from` to `to`, each
  published `lag` months after its period."
  [from to lag f]
  (vec (for [p (range from (inc to))] [p (+ p lag) (double (f p))])))

(def ^:private t0 (* 12 2000))

(def ^:private state
  (ws/world-state
   {:food {:entries (monthly t0 (+ t0 60) 1 #(Math/log (+ 100.0 (- % t0)))) :lag 1}
    :brent {:entries (monthly t0 (+ t0 60) 0 #(Math/log (+ 50.0 (* 0.5 (- % t0))))) :lag 0}
    :vix {:entries (monthly t0 (+ t0 60) 0 (constantly (Math/log 20.0))) :lag 0}
    :credit {:entries (monthly t0 (+ t0 60) 0 (constantly 4.0)) :lag 0}
    :policy {:entries (monthly t0 (+ t0 60) 0 #(* 0.01 (- % t0))) :lag 0}
    :usd {:entries (monthly (+ t0 30) (+ t0 60) 0 (constantly (Math/log 100.0))) :lag 0}}))

(deftest test-3b-a-read-after-t-throws
  (let [t (+ t0 40)
        r (ws/reader state t)]
    (testing "a value published by t is read and recorded"
      (is (= (Math/log 139.0) (ws/read-at r :food (dec t))))
      (is (= [{:dir :world :series :food :period (dec t) :known-at t}] (ws/reads r))))
    (testing "a value published after t is refused"
      (is (thrown? clojure.lang.ExceptionInfo (ws/read-at r :food t))))
    (testing "a value not in the data is unknown, not refused"
      (is (nil? (ws/read-at r :food (+ t0 200))))))
  (testing "the series shifted by one month (published at its period, declared lag 1 -> 0): refused"
    (let [shifted (ws/world-state {:food {:entries (monthly t0 (+ t0 60) 1 identity) :lag 0}})]
      (is (thrown? clojure.lang.ExceptionInfo (ws/gauges shifted (+ t0 40) nil)))))
  (testing "every gauge read is at or before t"
    (let [t (+ t0 45)
          {:keys [reads]} (ws/gauges state t nil)]
      (is (seq reads))
      (is (every? #(<= (:known-at %) t) reads)))))

(deftest gauges-direct-and-through-exposure
  (let [t (+ t0 40)
        {:keys [x names]} (ws/gauges state t {:food 0.2 :energy -0.1 :open 0.5})
        g (zipmap names x)]
    (is (= ws/gauge-names names))
    (testing "food over 12 and 24 months, at the last month published (t - 1)"
      (is (< (Math/abs (- (:food12 g) (- (Math/log 139.0) (Math/log 127.0)))) 1e-12))
      (is (< (Math/abs (- (:food24 g) (- (Math/log 139.0) (Math/log 115.0)))) 1e-12)))
    (testing "brent's at t itself, published the same month"
      (is (< (Math/abs (- (:brent12 g) (- (Math/log 70.0) (Math/log 64.0)))) 1e-12)))
    (is (< (Math/abs (- (:policy12 g) 0.12)) 1e-12))
    (is (= (Math/log 20.0) (:vix g)))
    (is (= 4.0 (:credit3 g)))
    (testing "the dollar starts later: unknown before"
      (is (nil? (:usd12 g)) "its value a year before t is not in the data")
      (is (some? (:usd12 (zipmap names (:x (ws/gauges state (+ t0 45) nil)))))))
    (testing "each world move times the country's exposure to it"
      (is (< (Math/abs (- (:food12-x-food g) (* 0.2 (:food12 g)))) 1e-12))
      (is (< (Math/abs (- (:brent12-x-energy g) (* -0.1 (:brent12 g)))) 1e-12))
      (is (< (Math/abs (- (:credit3-x-open g) 2.0)) 1e-12)))
    (testing "an unknown exposure leaves its products unknown"
      (let [g (zipmap names (:x (ws/gauges state t nil)))]
        (is (nil? (:food12-x-food g)))
        (is (some? (:food12 g)))))))

(deftest the-world-node-s-state-is-the-world-state
  ;; issue 10: the world node's state is the world's prices and credit, not
  ;; the share of states with any onset
  (let [t (+ t0 45)
        n (ws/world-node state t :year)]
    (is (= [:world "001" :year] (:id n)))
    (is (empty? (node/problems n)) (node/problems n))
    (is (= (set (take 7 ws/gauge-names)) (set (keys (:prices n)))))
    (is (every? #(<= (:known-at %) t) (:reads n)))
    (is (not (node/holds-issued? n)))
    (is (<= (:known n) t))))
