(ns bnm.fstats-test
  "Expected values from numpy and scipy on the same inputs."
  (:require [bnm.fstats :as fs]
            [bnm.math :as m]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) (* 1e-9 (max 1.0 (Math/abs (double b))))))

(def y [0.005 0.0169 -0.006 -0.0306 -0.0132 -0.0347 0.0074 0.0586 -0.0147 -0.0198 0.0246 0.0193 0.0092 -0.0322 0.0038 0.0328 -0.0488 -0.0133 -0.071 -0.0466 -0.0687 -0.0044 -0.0457 0.0159 0.0113 -0.0025 -0.0957 -0.0165 0.0031 0.0095 -0.0562 -0.0141 -0.0341 -0.0274 0.0474 -0.0273 0.0037 0.0404 -0.0183 0.0005 0.0094 0.0076 -0.044 0.008 0.0594 -0.0569 0.0394 0.0098 -0.0207 0.085 0.0355 -0.043 0.008 0.0281 -0.0026 0.0323 0.0023 0.0317 0.0625 -0.022])
(def f [0.0101 -0.0063 0.0045 -0.0423 -0.0188 -0.0138 0.0317 0.0544 -0.0416 -0.0273 0.0293 -0.0515 -0.0086 -0.0101 0.0414 0.033 -0.022 -0.0125 -0.0263 0.0342 -0.031 -0.0079 -0.0006 0.0036 -0.0 -0.0317 -0.0266 -0.0158 0.0384 0.0249 -0.0151 0.0183 -0.0179 0.0258 0.0166 0.0118 -0.0351 0.025 -0.0536 -0.0584 -0.0038 -0.0222 -0.0058 0.0722 -0.0046 -0.0333 0.0205 0.0202 -0.009 0.0218 0.0342 0.0052 -0.0261 0.0086 0.0028 -0.0194 0.011 -0.0137 0.0504 0.0017])
(def b (vec (repeat 60 0.003)))
(def r [0.01447 -0.01955 0.00407 -0.08989 -0.04657 0.02814 -0.09643 0.05233 -0.0773 0.04784 -0.03227 0.04895 0.01655 -0.06684 0.07246 0.08209 0.00671 -0.0037 0.00201 -0.03876 0.06493 -0.01714 0.00744 -0.02966 -0.0213 -0.05389 0.07285 0.0023 0.0583 0.01067 -0.02472 -0.00633 -0.01801 0.0104 -0.00876 -0.005 -0.05893 -0.03034 0.0927 -0.02356 -0.0427 0.02687 0.08036 -0.0627 -0.00043 -0.0216 -0.07805 0.04675 0.00883 0.01357 -0.02762 0.03274 -0.01696 0.00285 -0.04541 -0.05081 0.07678 -0.01536 0.02458 0.00831 -0.01206 -0.0154 0.0415 -0.00509 0.00243 0.01111 0.06883 0.04403 0.02913 -0.01818 -0.0591 0.05748 0.05832 0.00296 0.03709 0.04907 0.05156 0.05607 -0.01278 0.08575 -0.05233 0.05309 0.0347 0.05368 0.10395 0.08422 -0.04726 -0.07443 0.05084 -0.04075 0.00938 0.05199 -0.07219 -0.0955 0.02296 0.01222 -0.00229 0.01193 -0.03303 -0.06567 0.00167 -0.03859 -0.07217 0.03528 0.00693 0.03033 -0.03946 -0.0229 -0.03995 -0.03433 0.01977 -0.02915 0.0278 0.02699 0.11126 -0.05964 0.0544 0.00553 0.0093 -0.06249])

(deftest newey-west
  (is (= 3 (fs/nw-lags 60)))
  (is (close? 0.001394088062962963 (fs/long-run-variance y 3))))

(deftest return-forecasts
  (is (close? 0.09068412398372161 (fs/oos-r2 y f b)))
  (let [{:keys [stat p]} (fs/clark-west y f b 3)]
    (is (close? 4.834833064450931 stat))
    (is (close? 6.662870761786621e-07 p)))
  (let [{:keys [stat p]} (fs/diebold-mariano (map #(let [e (- %1 %2)] (* e e)) y f)
                                             (map #(let [e (- %1 %2)] (* e e)) y b) 3)]
    (is (close? -0.6007158396505118 stat))
    (is (close? 0.548029267211511 p)))
  (let [{:keys [hit stat p]} (fs/pesaran-timmermann y f)]
    (is (close? (/ 2.0 3.0) hit))
    (is (close? 2.6445341229533574 stat))
    (is (close? 0.0040901729104615425 p)))
  (testing "forecasts of one sign carry no timing information"
    (let [{:keys [hit p]} (fs/pesaran-timmermann [1.0 -1.0 1.0] [0.1 0.2 0.3])]
      (is (close? (/ 2.0 3.0) hit))
      (is (= 1.0 p))))
  (testing "identical forecasts give no difference to test"
    (let [{:keys [stat p]} (fs/clark-west y b b 3)]
      (is (= 0.0 stat))
      (is (= 0.5 p)))
    (is (= 1.0 (:p (fs/diebold-mariano [1.0 2.0 3.0] [1.0 2.0 3.0] 2))))))

(deftest distributions
  (is (close? 0.07363827012030295 (fs/chi2-sf 3.2 1)))
  (is (close? 0.2018965179946554 (fs/chi2-sf 3.2 2)))
  (is (close? 0.057558451972636406 (fs/chi2-sf 7.5 3)))
  (is (close? 0.1251503170853385 (fs/t-cdf -1.3 5.0)))
  (is (close? 0.9432370873896009 (fs/t-cdf 2.1 3.5)))
  (is (close? 0.024097654305104653 (fs/crps-normal 0.01 0.04 -0.03)))
  (is (< (Math/abs (- 0.024153222509929205 (fs/crps-t 0.01 0.04 5.0 -0.03))) 1e-8))
  (is (close? 1.7032915654316123 (fs/log-t 0.01 0.04 5.0 -0.03)))
  (is (close? 0.0 (fs/qlike 2.0 2.0)))
  (is (> (fs/qlike 1.0 2.0) 0.0)))

(deftest coverage
  (let [{:keys [stat p]} (fs/kupiec 9 60 0.1)]
    (is (close? 1.4682137342721902 stat))
    (is (close? 0.2256277377816313 p)))
  (let [{:keys [stat p]} (fs/christoffersen [0 0 1 0 0 0 1 1 0 0 0 0 1 0 0 0 0 0 1 0 0 1 0 0 0 0 0 0 1 0])]
    (is (close? 0.5310044432812475 stat))
    (is (close? 0.4661848765790597 p)))
  (testing "Berkowitz: uniform PITs pass, a biased and too-narrow forecast fails"
    (let [g (m/rng 5)
          u (repeatedly 400 #(m/next-double! g))
          z (repeatedly 400 #(+ 0.4 (* 1.5 (m/normal! g))))]
      (is (> (:p (fs/berkowitz u)) 0.05))
      (is (< (:p (fs/berkowitz (map m/normal-cdf z))) 0.001)))))

(deftest sharpe-ratios
  (let [{:keys [sr skew kurt]} (fs/sharpe-stats r)]
    (is (close? 0.05254542678235058 sr))
    (is (close? 0.0428094801150112 skew))
    (is (close? 2.381659340609931 kurt)))
  (is (close? 0.7168721673874834 (fs/probabilistic-sharpe r 0.0)))
  (is (close? 0.08853814526583416 (fs/expected-max-sharpe 24 0.002)))
  (is (close? 0.34720079654780867 (fs/deflated-sharpe r 24 0.002))))

(deftest superior-predictive-ability
  (let [g (m/rng 11)
        noise (fn [] (vec (repeatedly 500 #(m/normal! g))))
        ;; columns: each model's per-period gain over the benchmark
        null [(noise) (noise) (noise)]
        edge [(noise) (mapv #(+ 0.25 %) (noise)) (noise)]]
    (is (> (:p (fs/spa null {:block 10 :reps 999 :seed 1})) 0.1))
    (is (< (:p (fs/spa edge {:block 10 :reps 999 :seed 1})) 0.01))))
