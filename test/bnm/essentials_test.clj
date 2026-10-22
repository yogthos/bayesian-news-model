(ns bnm.essentials-test
  (:require [bnm.essentials :as ess]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (and a b (< (Math/abs (- (double a) (double b))) tol)))

;; ---------------------------------------------------------------- Theil-Sen

(deftest median-odd-and-even
  (is (= 3.0 (ess/median [5 1 3])))
  (is (= 2.5 (ess/median [4 1 3 2])))
  (is (nil? (ess/median []))))

(deftest theil-sen-recovers-a-line
  (testing "an exact line"
    (is (close? 2.0 (ess/theil-sen (for [x (range 8)] [x (+ 1.0 (* 2.0 x))])) 1e-12)))
  (testing "two wild outliers in eight points leave the slope alone"
    (let [pts (for [x (range 2000 2008)]
                [x (cond (= x 2003) 50.0 (= x 2006) -40.0 :else (* -0.3 (- x 2000)))])]
      (is (close? -0.3 (ess/theil-sen pts) 1e-12))))
  (testing "deterministic noise around a slope of 0.15"
    (let [pts (for [x (range 20)] [x (+ (* 0.15 x) (* 0.05 (Math/sin (* 2.3 x))))])]
      (is (close? 0.15 (ess/theil-sen pts) 0.01))))
  (is (nil? (ess/theil-sen [[1 2.0]])) "one point has no slope"))

;; ---------------------------------------------------------------- panels

(defn- row [hs6 year share tonnes]
  {:hs "HS92" :hs6 hs6 :year year :known (+ year 2) :share share :tonnes tonnes})

(defn- grower
  "A product whose share and tonnes grow at log rates `gs` and `gt` a year."
  [hs6 s0 t0 gs gt years]
  (for [y years]
    (row hs6 y (* s0 (Math/exp (* gs (- y (first years)))))
         (* t0 (Math/exp (* gt (- y (first years))))))))

(def years (range 1995 2025))

(def panel
  (vec (concat
        (grower "000001" 1e-4 1e5 0.30 0.25 years) ; fastest
        (grower "000002" 1e-4 1e5 0.10 0.12 years)
        (grower "000003" 1e-4 1e5 -0.05 -0.05 years) ; falling
        (grower "000004" 1e-4 1e5 0.60 0.0 years)   ; value only: a price story
        (grower "000005" 1e-8 1e2 0.40 0.40 years)  ; too small to rank early
        (mapcat (fn [i] (grower (format "%06d" i) 1e-4 1e5 (* 0.002 i) (* 0.002 i) years))
                (range 6 40)))))

(deftest smaller-trend-and-ranks
  (let [r (ess/rise panel 2010)
        by (into {} (map (juxt :hs6 identity)) r)]
    (is (= 1 (:rank (by "000001"))) "the fastest riser is first")
    (is (close? 0.25 (:score (by "000001")) 1e-4) "the smaller of the two trends")
    (is (close? 0.30 (:share-slope (by "000001")) 1e-4))
    (is (close? 0.0 (:score (by "000004")) 1e-4) "value up, tonnes flat: no rise")
    (is (< (:rank (by "000002")) (:rank (by "000004"))))
    (is (= (count (filter :rank r)) (:n (by "000001"))))
    (is (= (:n (by "000001")) (:rank (by "000003"))) "the faller is last")
    (is (nil? (:rank (by "000005"))) "below the share floor: not ranked")
    (is (= 2010 (:year (by "000001"))))
    (is (= 2012 (:known (by "000001"))) "known two years on")
    (is (close? (/ 1.0 (:n (by "000001"))) (:pct (by "000001")) 1e-12))))

(deftest window-needs-eight-years
  (is (empty? (filter :rank (ess/rise panel 2001))) "1995-2001 is seven years")
  (is (seq (filter :rank (ess/rise panel 2002)))))

;; ---------------------------------------------------------------- causality

(defn- corrupt
  "`rows` with every row past `pred` replaced by a wild value."
  [rows pred]
  (mapv (fn [{:keys [year] :as r}]
          (if (pred r)
            (assoc r :share (* 1e-3 (+ 1.5 (Math/sin (* 7.1 year))))
                   :tonnes (* 1e7 (+ 1.5 (Math/cos (* 3.3 year)))))
            r))
        rows))

(defn- ranks [r] (into {} (map (juxt :hs6 (juxt :rank :score))) r))

(deftest no-read-after-the-data-year
  (doseq [y [2005 2012 2018]]
    (is (= (ranks (ess/rise panel y))
           (ranks (ess/rise (corrupt panel #(> (:year %) y)) y)))
        (str "a corrupted future leaves the " y " ranks unchanged")))
  (testing "a corrupted present does change them (the test can fail)"
    (is (not= (ranks (ess/rise panel 2012))
              (ranks (ess/rise (corrupt panel #(= (:year %) 2012)) 2012))))))

(deftest as-of-reads-only-what-is-known
  (let [a 2020
        r (ess/rise-known panel a)]
    (is (= 2018 (:year (first r))) "known 2020: data to 2018")
    (is (every? #(<= (:known %) a) r))
    (is (= (ranks r)
           (ranks (ess/rise-known (corrupt panel #(> (:known %) a)) a))))
    (is (= (ranks r) (ranks (ess/rise panel 2018))))))

;; ---------------------------------------------------------------- revisions

(defn- stepped
  "A flat product whose share and tonnes jump by `jump` in log terms at
  `at`, when its codes are moved at an HS revision."
  [hs6 at jump]
  (for [y years]
    (let [f (if (>= y at) (Math/exp jump) 1.0)]
      (row hs6 y (* 1e-4 f) (* 1e5 f)))))

(deftest revision-steps-are-filtered
  (let [p (vec (concat panel (stepped "900001" 2012 2.0) (stepped "900002" 2010 2.0)))
        at (fn [y code] (first (filter #(= code (:hs6 %)) (ess/rise p y))))]
    (testing "a step at the 2012 revision is not a rise while it is in the window"
      (doseq [y (range 2012 2019)]
        (is (nil? (:rank (at y "900001"))) (str y))
        (is (:revision-step (at y "900001")) (str y)))
      (is (:rank (at 2019 "900001")) "eligible again once the 2011-12 step leaves the window"))
    (testing "the same step in a non-revision year is left in"
      (is (:rank (at 2012 "900002"))))
    (testing "steady growth across a revision is not a step"
      (is (:rank (at 2014 "000001")))
      (is (not (:revision-step (at 2014 "000001")))))
    (testing "every listed revision year"
      (is (= #{1996 2002 2007 2012 2017 2022} (set ess/revisions)))
      (doseq [rev [1996 2002 2007 2017 2022]]
        (let [q (vec (concat panel (stepped "900003" rev 2.0)))]
          (is (nil? (:rank (first (filter #(= "900003" (:hs6 %)) (ess/rise q (max 2002 (min 2024 (+ rev 2))))))))
              (str rev)))))))

(deftest editions-rank-separately
  (let [hs12 (map #(assoc % :hs "HS12") (grower "850760" 1e-4 1e5 0.9 0.9 (range 2012 2025)))
        r (ess/rise (into panel hs12) 2020)]
    (is (= 1 (:rank (first (filter #(= "850760" (:hs6 %)) r)))))
    (is (= 1 (:rank (first (filter #(and (= "HS92" (:hs %)) (= "000005" (:hs6 %))) r)))) "HS92's own fastest")))
