(ns bnm.calibrate-test
  (:require [bnm.calibrate :as cal]
            [bnm.model]
            [clojure.test :refer [deftest is testing]]))

(deftest overconfidence-is-learned-away
  ;; forecasts of 0.9 that come true half the time
  (let [outcomes (for [i (range 200)] {:channel :violence :p 0.9 :outcome (even? i)})
        c (cal/learn {} outcomes)]
    (is (< 0.45 (cal/calibrate c :violence 0.9) 0.55))
    (is (= 0.3 (cal/calibrate c :protest 0.3)) "an unseen channel passes through")
    (is (= 0.03 (cal/calibrate c :violence 0.03)) "an empty bin keeps the raw value")))

(deftest spread-is-learned-from-errors
  ;; forecasts whose 80% range is half as wide as their errors need
  (let [sd 0.2
        z1 1.2815515655446004
        rows (fn [f]
               (for [i (range 400)]
                 (let [e (* (if (even? i) 1.0 -1.0) 2.0 sd)]
                   {:level :country :channel :violence :stratum :active
                    :q10 (Math/expm1 (- 3.0 (* f z1 sd))) :q50 (Math/expm1 3.0)
                    :q90 (Math/expm1 (+ 3.0 (* f z1 sd))) :actual (Math/expm1 (+ 3.0 e))})))
        s1 (cal/learn-spread {} (rows 1.0))
        ;; the next forecasts come out widened by what was learned
        s (cal/learn-spread s1 (rows (cal/spread-factor s1 [:country :violence :active])))
        k (cal/spread-factor s [:country :violence :active])]
    (is (< 1.8 k 2.2) k)
    (is (= 1.0 (cal/spread-factor s [:actor :violence :active])) "an unseen key keeps the spread")
    (testing "widening scales the log spread and keeps the mean"
      (let [xs (double-array [0.0 (Math/expm1 2.9) (Math/expm1 3.0) (Math/expm1 3.1) (Math/expm1 3.2)])
            w (cal/widen 2.0 xs)
            mean #(/ (areduce ^doubles % i t 0.0 (+ t (aget ^doubles % i))) 5.0)
            gap #(- (Math/log1p (aget ^doubles % 3)) (Math/log1p (aget ^doubles % 2)))]
        (is (< (Math/abs (- (mean w) (mean xs))) 1e-9))
        (is (> (gap w) (* 1.5 (gap xs))))
        (is (apply <= (vec w)) "still sorted")
        (is (= 0.0 (aget w 0)) "never below zero")))))

(deftest pool-adjacent-violators
  (testing "the weighted least-squares nondecreasing fit"
    (is (= [1.0 2.5 2.5 4.0] (cal/isotonic [1.0 3.0 2.0 4.0] [1.0 1.0 1.0 1.0])))
    (is (= [1.0 2.25 2.25] (cal/isotonic [1.0 3.0 2.0] [1.0 1.0 3.0])))
    (is (= [2.0 2.0 2.0] (cal/isotonic [3.0 2.0 1.0] [1.0 1.0 1.0])))
    (is (= [] (cal/isotonic [] [])))))

(deftest calibration-is-monotone
  ;; forecasts of 0.45 come true 60% of the time, forecasts of 0.65 only 30%:
  ;; the calibrated map may not rank the higher forecast lower
  (let [outcomes (concat (for [i (range 100)] {:channel :violence :p 0.45 :outcome (< (mod i 10) 6)})
                         (for [i (range 100)] {:channel :violence :p 0.65 :outcome (< (mod i 10) 3)}))
        c (cal/learn {} outcomes)]
    (is (<= (cal/calibrate c :violence 0.45) (cal/calibrate c :violence 0.65)))
    (is (< 0.35 (cal/calibrate c :violence 0.45) 0.5) "the two pooled")))

(deftest isotonic-recalibration-map
  ;; forecasts of 0.1 come true 20% of the time, of 0.5 30%, of 0.9 30%
  (let [pairs (concat (for [i (range 10)] [0.1 (< i 2)]) (for [i (range 10)] [0.5 (< i 3)])
                      (for [i (range 10)] [0.9 (< i 3)]))
        f (cal/isotonic-map (map first pairs) (map second pairs))]
    (is (< (Math/abs (- 0.2 (f 0.1))) 1e-12))
    (is (< (Math/abs (- 0.3 (f 0.9))) 1e-12))
    (is (< (Math/abs (- 0.2 (f 0.3))) 1e-12) "between forecasts, the step below")
    (is (< (Math/abs (- 0.2 (f 0.01))) 1e-12) "below the lowest, the lowest step")))

(deftest logistic-recalibration
  ;; overconfident forecasts: the truth is half as extreme on the logit scale
  (let [logit #(Math/log (/ % (- 1.0 %)))
        sigm #(/ 1.0 (+ 1.0 (Math/exp (- %))))
        ps (for [i (range 2000)] (sigm (- (* 6.0 (/ (mod i 100) 100.0)) 3.0)))
        ys (for [[i p] (map-indexed vector ps)] (< (mod (* i 7919) 1000) (* 1000 (sigm (* 0.5 (logit p))))))
        f (cal/logistic-map ps ys)]
    (testing "pulls extreme forecasts in, and keeps their order"
      (is (< (f 0.95) 0.9))
      (is (> (f 0.05) 0.1))
      (is (< (f 0.3) (f 0.31))))))

(deftest skewed-spread
  ;; log errors: below the median as the spread says, above it three times as large,
  ;; and two outcomes in three below the median
  (let [sd 0.2 z1 1.2815515655446004
        rows (for [i (range 600)]
               (let [e (case (mod i 3) 0 (* 3.0 sd) 1 (- sd) 2 (- (* 0.5 sd)))]
                 {:level :country :channel :violence :stratum :active
                  :q10 (Math/expm1 (- 3.0 (* z1 sd))) :q50 (Math/expm1 3.0)
                  :q90 (Math/expm1 (+ 3.0 (* z1 sd))) :actual (Math/expm1 (+ 3.0 e))}))
        s (cal/learn-spread {} rows)
        {:keys [lo hi shift]} (cal/spread-factors s [:country :violence :active])]
    (testing "the upper side widens more than the lower"
      (is (> hi (* 2.0 lo)) [lo hi]))
    (testing "more outcomes below the median than above shift it down"
      (is (neg? shift)))
    (testing "an unseen key keeps the forecast"
      (is (= {:lo 1.0 :hi 1.0 :shift 0.0} (cal/spread-factors s [:actor :violence :active]))))
    (testing "skewed widening stretches each side by its own factor around the shifted median"
      (let [xs (double-array (map #(Math/expm1 %) [2.0 3.0 4.0]))
            out (cal/widen-skewed {:lo 1.0 :hi 2.0 :shift 0.0} xs)]
        (is (< (Math/abs (- (Math/log1p (aget out 0)) 2.0)) 1e-9))
        (is (< (Math/abs (- (Math/log1p (aget out 2)) 5.0)) 1e-9))))))

(deftest sides-widen-and-keep-the-mean
  (let [xs (double-array (map #(Math/expm1 %) [2.0 2.5 3.0 3.5 4.0]))
        out (cal/widen-sides {:lo 1.0 :hi 2.0} xs)
        sum #(areduce ^doubles % i t 0.0 (+ t (aget ^doubles % i)))]
    (is (< (Math/abs (- (sum xs) (sum out))) 1e-6) "the mean is kept")
    (is (> (/ (aget out 4) (aget out 2)) (/ (aget xs 4) (aget xs 2))) "the upper side is stretched")))

(deftest spread-learned-from-coverage
  ;; the factor moves the 80% range toward covering 80% of outcomes, whatever
  ;; the errors around the median: too wide shrinks, too narrow widens
  (let [rows (fn [inside n]
               (for [i (range n)]
                 {:level :country :channel :violence :stratum :active
                  :q10 10.0 :q50 20.0 :q90 30.0 :actual (if (< i inside) 20.0 100.0)}))
        k #(cal/coverage-factor % [:country :violence :active])]
    (is (= 1.0 (k {})) "an unseen key keeps the forecast")
    (is (< (k (cal/learn-coverage {} (rows 95 100))) 1.0) "95% covered: narrower")
    (is (> (k (cal/learn-coverage {} (rows 60 100))) 1.0) "60% covered: wider")
    (is (< (Math/abs (- 1.0 (k (cal/learn-coverage {} (rows 80 100))))) 0.02) "80% covered: about the same")
    (testing "it compounds over periods toward the target"
      (let [s (reduce (fn [s _] (cal/learn-coverage s (rows 95 100))) {} (range 5))]
        (is (< (k s) (k (cal/learn-coverage {} (rows 95 100)))))))))

(deftest the-mode-picks-the-learner-and-the-widening
  (let [rows (for [i (range 100)]
               {:level :country :channel :violence :stratum :active
                :q10 10.0 :q50 20.0 :q90 30.0 :actual (if (< i 95) 20.0 100.0)})
        key [:country :violence :active]
        s (cal/learn-by :coverage {} rows)
        xs (double-array [1.0 2.0 4.0 8.0 16.0])]
    (is (< (cal/coverage-factor s key) 1.0) ":coverage learns from coverage")
    (is (= (vec (cal/widen (cal/coverage-factor s key) xs)) (vec (cal/widen-by :coverage s key xs))))
    (is (= 1.0 (cal/coverage-factor (cal/learn-by :symmetric {} rows) key)) ":symmetric does not")))

(deftest coverage-is-the-default-mode
  (is (= :coverage (:spread-mode bnm.model/defaults))))

(deftest the-shock-is-on-at-a-fixed-size-by-default
  ;; v6-q refit: a fixed 0.4 evened the tails below the country; learning the
  ;; size from the tails chased the median it moved, and scored worse
  (is (= {:sd 0.4 :volume 20.0} (:shock bnm.model/defaults)))
  (is (nil? (cal/shock-learning {:shock {:sd 0.4 :volume 20.0}})))
  (is (= {:shock-sd 0.4} (cal/shock-learning {:shock {:sd 0.4 :volume 20.0 :learn true}}))))

(deftest each-tail-learned-from-its-own-coverage
  (let [key [:dyad :violence :quiet]
        ;; 14% above the range, 6% below: the upper side widens, the lower narrows
        rows (for [i (range 100)]
               {:level :dyad :channel :violence :stratum :quiet :q10 10.0 :q50 20.0 :q90 30.0
                :actual (cond (< i 6) 1.0 (< i 20) 100.0 :else 20.0)})
        s (cal/learn-by :coverage-sides {} rows)
        {:keys [lo hi]} (cal/side-factors s key)]
    (is (> hi 1.0) "upper tail too thin: wider above")
    (is (< lo 1.0) "lower tail too fat: narrower below")
    (testing "10% each side leaves both about where they were"
      (let [even (for [i (range 100)]
                   {:level :dyad :channel :violence :stratum :quiet :q10 10.0 :q50 20.0 :q90 30.0
                    :actual (cond (< i 10) 1.0 (< i 20) 100.0 :else 20.0)})
            {:keys [lo hi]} (cal/side-factors (cal/learn-by :coverage-sides {} even) key)]
        (is (< (Math/abs (- 1.0 lo)) 0.02))
        (is (< (Math/abs (- 1.0 hi)) 0.02))))
    (testing "widen-by stretches each side by its factor, keeping the mean"
      (let [xs (double-array [1.0 2.0 4.0 8.0 16.0])
            ys (cal/widen-by :coverage-sides s key xs)
            mean #(/ (reduce + (seq %)) (alength ^doubles %))]
        (is (= (vec (cal/widen-sides {:lo lo :hi hi} xs)) (vec ys)))
        (is (< (Math/abs (- (mean xs) (mean ys))) 1e-9))))
    (testing "unknown keys are left alone"
      (is (= {:lo 1.0 :hi 1.0} (cal/side-factors {} key))))))

(deftest the-shock-learned-from-tail-asymmetry
  ;; the forecast shock's log-sd per level and channel grows while outcomes
  ;; fall above the range more often than below it, shrinks while below
  ;; outnumbers above, and holds when the tails are even
  (let [rows (fn [below above]
               (for [i (range 100)]
                 {:level :dyad :channel :violence :stratum :quiet :q10 10.0 :q50 20.0 :q90 30.0
                  :actual (cond (< i below) 1.0 (< i (+ below above)) 100.0 :else 20.0)}))
        s #(cal/shock-scale % :dyad :violence 0.4)]
    (is (= 0.4 (s {})) "an unseen key starts at the configured sd")
    (is (> (s (cal/learn-shock {} (rows 6 14) 0.4)) 0.4) "upper tail thin: a bigger shock")
    (is (< (s (cal/learn-shock {} (rows 14 6) 0.4)) 0.4) "lower tail thin: a smaller one")
    (is (< (Math/abs (- 0.4 (s (cal/learn-shock {} (rows 10 10) 0.4)))) 1e-9) "even: unchanged")
    (is (= 0.4 (cal/shock-scale (cal/learn-shock {} (rows 6 14) 0.4) :country :violence 0.4))
        "other levels are their own")
    (testing "it never goes below zero"
      (is (>= (s (reduce (fn [sp _] (cal/learn-shock sp (rows 40 0) 0.4)) {} (range 50))) 0.0)))
    (testing "learn-by learns it only when the shock is on"
      (is (= 0.4 (s (cal/learn-by :coverage {} (rows 6 14)))))
      (is (> (s (cal/learn-by :coverage {} (rows 6 14) {:shock-sd 0.4})) 0.4))
      (is (= (cal/coverage-factor (cal/learn-by :coverage {} (rows 6 14)) [:dyad :violence :quiet])
             (cal/coverage-factor (cal/learn-by :coverage {} (rows 6 14) {:shock-sd 0.4}) [:dyad :violence :quiet]))
          "the coverage factor is learned as before"))))
