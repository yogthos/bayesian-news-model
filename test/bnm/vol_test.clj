(ns bnm.vol-test
  "GARCH expectations from the arch package (8.0.0) on test/bnm/garch-series.txt,
  a simulated GARCH(1,1) with t innovations."
  (:require [augur.math :as m]
            [bnm.vol :as vol]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def r (mapv parse-double (str/split-lines (slurp (io/file "test/bnm/garch-series.txt")))))

(defn- near? [a b tol] (< (Math/abs (- (double a) (double b))) (* tol (max 1.0 (Math/abs (double b))))))

(deftest backcast-as-arch
  (is (near? 1.089439588405401 (vol/backcast r) 1e-12)))

(deftest gaussian-garch
  (let [fit (vol/garch-fit r)]
    (is (near? -2019.4229896990028 (:llf fit) 1e-7))
    (is (near? 0.03393328080478602 (:omega fit) 1e-3))
    (is (near? 0.09870526401365513 (:alpha fit) 1e-3))
    (is (near? 0.8694752374738711 (:beta fit) 1e-3))
    (testing "variance forecasts 1-5 steps ahead"
      (doseq [[h want] (map vector (vol/garch-forecast fit r 5)
                            [1.7735909197084216 1.7510894268818087 1.7293039202727205 1.7082116175587736 1.687790461339658])]
        (is (near? want h 1e-3))))))

(deftest a-warm-start-reaches-the-same-optimum
  (let [cold (vol/garch-fit r)
        warm (vol/garch-fit r {:start cold})]
    (is (near? (:llf cold) (:llf warm) 1e-9))))

(deftest student-t-garch
  (let [fit (vol/garch-fit r {:dist :t})]
    (is (near? -1983.369192265125 (:llf fit) 1e-7))
    (is (near? 6.532815605231097 (:nu fit) 1e-2))
    (is (near? 0.8796289857197036 (:beta fit) 1e-3))))

(deftest ewma-variance
  (testing "lambda weights the last squared return against the previous variance"
    (is (near? (+ (* 0.94 2.0) (* 0.06 9.0)) (vol/ewma-step 2.0 3.0 0.94) 1e-15)))
  (is (= (count r) (count (vol/ewma r 0.94)))))

(deftest har-recovers-its-coefficients
  ;; realized variances built from the HAR equation itself, plus a little noise
  (let [g (m/rng 9)
        rv (loop [v (vec (repeat 30 1.0)) i 0]
             (if (= 600 (count v)) v
                 (let [d (peek v) w (/ (reduce + (subvec v (- (count v) 5))) 5.0)
                       mo (/ (reduce + (subvec v (- (count v) 22))) 22.0)]
                   (recur (conj v (+ 0.1 (* 0.4 d) (* 0.3 w) (* 0.2 mo) (* 0.1 (Math/abs (m/normal! g))))) (inc i)))))
        {:keys [beta]} (vol/har-fit rv)]
    ;; the weekly and monthly means are nearly collinear: their split is
    ;; weakly identified, their sum and the daily term are not
    (is (near? 0.4 (beta 1) 0.05))
    (is (near? 0.5 (+ (beta 2) (beta 3)) 0.05))
    (is (pos? (vol/har-forecast {:beta beta} rv)))))
