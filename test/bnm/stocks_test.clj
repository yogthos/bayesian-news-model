(ns bnm.stocks-test
  (:require [bnm.stocks :as sk]
            [clojure.test :refer [deftest is testing]]))

(defn- monthly
  "A monthly series published a month after each period: value v(p)."
  [from to v]
  (sk/series (for [p (range from (inc to))] [p (inc p) (double (v p))])))

(deftest reads-values-as-they-were-known
  (let [s (monthly 0 100 identity)]
    (testing "the latest value published by t"
      (is (= 49.0 (sk/value s 50)))
      (is (nil? (sk/value s 0)) "nothing published yet"))
    (testing "change over a horizon, ending at the latest known value"
      (is (= 36.0 (sk/change s 50 36)))
      (is (nil? (sk/change s 20 36)) "not enough history"))
    (testing "stale series give nothing"
      (is (= 100.0 (sk/value s 105)))
      (is (nil? (sk/value s 120 {:stale 12}))))))

(deftest mixes-frequencies
  ;; annual values at year ends, known nine months later
  (let [s (sk/series (for [y (range 10)] [(+ 11 (* 12 y)) (+ 20 (* 12 y)) (* 10.0 y)]))]
    (is (= 30.0 (sk/value s 60)))
    (is (= 20.0 (sk/change s 60 24)))))

(deftest measures-volatility
  (let [flat (monthly 0 60 (constantly 1.0))
        zigzag (monthly 0 60 #(if (even? %) 1.0 -1.0))]
    (is (= 0.0 (sk/volatility flat 50 12)))
    (is (> (sk/volatility zigzag 50 12) 1.5))))
