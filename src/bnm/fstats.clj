(ns bnm.fstats
  "The probabilistic and deflated Sharpe ratios (Bailey & López de Prado
  2012, 2014), for strategies built on forecasts. The forecast tests
  themselves are augur.ftest."
  (:require [augur.special :refer [phi phi-inv]]))

;; --- Sharpe ratios ------------------------------------------------------------------------

(defn sharpe-stats
  "Per-period Sharpe ratio of excess returns xs (sample sd), with their
  skewness and kurtosis (population moments, so 3 for a normal). A series
  that never moves has a ratio of 0 when it is zero (out of the market) and
  normal moments."
  [xs]
  (let [n (double (count xs))
        mu (/ (double (reduce + xs)) n)
        mom (fn [k] (/ (double (reduce + (map #(Math/pow (- % mu) k) xs))) n))
        m2 (mom 2)]
    (if (> m2 (* 1e-14 mu mu))
      {:sr (/ mu (Math/sqrt (* m2 (/ n (- n 1.0)))))
       :skew (/ (mom 3) (Math/pow m2 1.5))
       :kurt (/ (mom 4) (* m2 m2))
       :n (long n)}
      {:sr (if (zero? mu) 0.0 Double/NaN) :skew 0.0 :kurt 3.0 :n (long n)})))

(defn- psr ^double [{:keys [sr skew kurt n]} ^double sr*]
  (phi (/ (* (- sr sr*) (Math/sqrt (- n 1.0)))
          (Math/sqrt (+ (- 1.0 (* skew sr)) (* (/ (- kurt 1.0) 4.0) sr sr))))))

(defn probabilistic-sharpe
  "The probability that the true Sharpe ratio of xs exceeds sr*, allowing
  for the sample's length, skewness and kurtosis."
  ^double [xs sr*]
  (psr (sharpe-stats xs) sr*))

(defn expected-max-sharpe
  "The expected largest of n-trials Sharpe ratios with true value zero and
  variance var-sr across trials."
  ^double [n-trials var-sr]
  (let [g 0.5772156649015329 n (double n-trials)]
    (* (Math/sqrt var-sr) (+ (* (- 1.0 g) (phi-inv (- 1.0 (/ 1.0 n))))
                             (* g (phi-inv (- 1.0 (/ 1.0 (* n Math/E)))))))))

(defn deflated-sharpe
  "The probabilistic Sharpe ratio of xs against the best of n-trials
  worthless strategies whose Sharpe ratios vary by var-sr."
  ^double [xs n-trials var-sr]
  (psr (sharpe-stats xs) (expected-max-sharpe n-trials var-sr)))
