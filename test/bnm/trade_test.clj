(ns bnm.trade-test
  (:require [bnm.trade :as tr]
            [clojure.test :refer [deftest is testing]]))

(deftest labels-drawdowns-ahead
  ;; wealth 1, 1.1, 0.85, 0.8, 1.2
  (let [rets [0.10 (- (/ 0.85 1.1) 1.0) (- (/ 0.8 0.85) 1.0) 0.5]
        w (tr/wealth rets)]
    (is (= 5 (count w)))
    (testing "falls 20% below today's level within h months"
      (is (true? (tr/drawdown-ahead? w 0 3 0.2)))
      (is (false? (tr/drawdown-ahead? w 0 1 0.2)))
      (is (true? (tr/drawdown-ahead? w 1 1 0.2)) "1.1 to 0.85 is 23%")
      (is (nil? (tr/drawdown-ahead? w 3 3 0.2)) "not enough future"))))

(deftest backtests-exposure
  (let [mkt [0.10 -0.20 0.10 0.05]
        rf [0.01 0.01 0.01 0.01]]
    (testing "always invested is buy and hold"
      (let [r (tr/backtest mkt rf (repeat 4 1.0) {:cost 0.0})]
        (is (< (Math/abs (- (:total r) (dec (* 1.1 0.8 1.1 1.05)))) 1e-12))
        (is (< (Math/abs (- (:max-drawdown r) 0.2)) 1e-12))))
    (testing "out of the market for the fall, paying for two switches"
      (let [r (tr/backtest mkt rf [1.0 0.0 1.0 1.0] {:cost 0.001})]
        (is (< (Math/abs (- (:total r) (dec (* 1.1 1.01 0.999 1.1 0.999 1.05)))) 1e-12))
        (is (= 2 (:switches r)))))))

(deftest annualizes-by-period
  (let [mkt (vec (take 104 (cycle [0.01 -0.005])))
        rf (vec (repeat 104 0.0))
        m (tr/backtest mkt rf (repeat 104 1.0) {:cost 0.0})
        w (tr/backtest mkt rf (repeat 104 1.0) {:cost 0.0 :periods 52})]
    (is (< (Math/abs (- (:sharpe w) (* (:sharpe m) (Math/sqrt (/ 52.0 12.0))))) 1e-9))
    (is (> (:annual w) (:annual m)))))

(deftest moving-average-rule
  ;; invested when wealth is above its trailing mean
  (let [w [1.0 1.1 1.2 1.0 0.9 1.3]]
    (is (= [1.0 1.0 1.0 0.0 0.0 1.0] (tr/sma-exposure w 3)))))
