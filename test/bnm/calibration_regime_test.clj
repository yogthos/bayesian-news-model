(ns bnm.calibration-regime-test
  (:require [augur.math :as m]
            [bnm.calibration-regime :as cr]
            [bnm.relation-diag]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(defn- expit [e] (/ 1.0 (+ 1.0 (Math/exp (- e)))))

(defn- synthetic-rows
  "Two kinds, 40 years x 120 countries. The base's log odds L ~ N(-2.5, 1),
  miscalibrated per kind (slope 0.5 in one, 0.8 in the other); a regime
  R (20% of rows) changes the level by c and the slope by d."
  [seed c d]
  (let [g (m/rng seed)]
    (vec (for [kind ["a" "b"] t (range 1960 2000) i (range 120)
               :let [L (+ -2.5 (m/normal! g))
                     r (if (< (m/next-double! g) 0.2) 1.0 0.0)
                     b (if (= kind "a") 0.5 0.8)
                     e (+ -1.0 (* b (+ L 2.5)) -2.0 (* r (+ c (* d (+ L 2.5)))))]]
           {:kind kind :t t :g t :c (str "C" i) :L L :r [r]
            :y (< (m/next-double! g) (expit e))}))))

(deftest the-design-holds-a-and-b-per-kind
  ;; nuisance: an intercept and a slope on the centred base per kind;
  ;; tested: each regime's level and its slope change
  (let [rows (cr/design [{:kind "a" :L -2.0 :r [1.0]} {:kind "b" :L -3.0 :r [0.0]}] ["a" "b"] {"a" -2.5 "b" -2.5})]
    (is (= [1.0 0.0 0.5 0.0] (:x (first rows))) "kind a: intercept, b's dummy, L - centre, b's slope")
    (is (= [1.0 1.0 -0.5 -0.5] (:x (second rows))))
    (is (= [1.0 0.5] (:z (first rows))) "R and R (L - centre)")
    (is (= [0.0 0.0] (:z (second rows))))))

(deftest a-synthetic-slope-change-is-recovered
  (let [rows (cr/design (synthetic-rows 7 0.0 0.6) ["a" "b"] {"a" -2.5 "b" -2.5})
        s (cr/score-test rows)
        f (cr/full-fit rows)]
    (is (close? 0.6 (second (:coef f)) 0.2) "d by maximum likelihood")
    (is (close? 0.0 (first (:coef f)) 0.2) "c")
    (is (close? 0.6 (second (:est s)) 0.25) "the one-step d from the score")
    (is (> (second (:zs s)) 3.0))
    (is (> (:stat s) 10.0))
    (is (= 2 (:df s)))))

(deftest the-per-kind-miscalibration-is-not-a-regime-effect
  ;; no regime effect; kinds miscalibrated differently: the regime terms
  ;; stay at zero because a and b are fitted per kind
  (let [rows (cr/design (synthetic-rows 9 0.0 0.0) ["a" "b"] {"a" -2.5 "b" -2.5})
        s (cr/score-test rows)]
    (is (< (:stat s) 9.2) "chi-squared 2 df, under 1%")
    (is (every? #(< (Math/abs %) 3.0) (:zs s)))))

(deftest a-level-shift-is-recovered
  (let [f (cr/full-fit (cr/design (synthetic-rows 12 0.7 0.0) ["a" "b"] {"a" -2.5 "b" -2.5}))]
    (is (close? 0.7 (first (:coef f)) (* 2.5 (first (:se f)))) "within 2.5 standard errors")
    (is (close? 0.0 (second (:coef f)) 0.2))
    (is (> (first (:z f)) 3.0))))

(deftest the-score-test-is-year-clustered
  ;; the same rows duplicated within each year: the statistic is unchanged
  ;; when clusters are years (each year's score sum doubles, as does the
  ;; total), but would double if rows were independent
  (let [rows (cr/design (synthetic-rows 13 0.0 0.3) ["a" "b"] {"a" -2.5 "b" -2.5})
        s1 (cr/score-test rows)
        s2 (cr/score-test (into rows rows))]
    (is (close? (:stat s1) (:stat s2) (* 0.02 (:stat s1))))))

;; --- regimes ---------------------------------------------------------------

(deftest demeaned-by-year-without-scaling
  (let [d (cr/demean {"A" {2000 1.0 2001 5.0} "B" {2000 3.0 2001 7.0} "C" {1999 9.0}})]
    (is (= -1.0 (get-in d ["A" 2000])))
    (is (= 1.0 (get-in d ["B" 2001])))
    (is (nil? (get-in d ["C" 1999])) "a lone country has no contrast")))

(deftest thresholds-come-from-the-design-years-only
  (let [panel {"A" {1990 1.0 1991 2.0 2010 100.0} "B" {1990 3.0 1991 4.0 2010 -100.0}}
        q (cr/threshold panel 1960 2002 0.5)]
    (is (close? 2.5 q 1e-12) "the median of 1, 2, 3, 4")
    (is (= q (cr/threshold (assoc-in panel ["A" 2010] 1e9) 1960 2002 0.5)) "later values do not move it")
    (is (= q (cr/threshold (assoc-in panel ["A" 2011] -5.0) 1960 2002 0.5)))))

(deftest a-regime-at-t-reads-the-state-at-t-only
  ;; causality: the indicator at t is the state at t against a fixed
  ;; threshold; changing the state after t changes nothing at t
  (let [panel {"A" {1990 1.0 1991 3.0} "B" {1990 2.0 1991 0.5}}
        r (cr/above panel 1.5)
        r' (cr/above (assoc-in panel ["A" 1991] -9.0) 1.5)]
    (is (= 0.0 (r "A" 1990)))
    (is (= 1.0 (r "A" 1991)))
    (is (= (r "A" 1990) (r' "A" 1990)))
    (is (nil? (r "A" 1989)))))

(deftest outcomes-after-to-are-not-read
  ;; the year node's window is (t, t + 2]: the last origin is to - 2, and
  ;; the outcome function is never asked about a later window
  (let [seen (atom 0)
        cells (for [c ["A" "B"] t (range 1960 2010)] [c t])
        rows (cr/rows {:cells cells :kind "k" :from 1960 :to 2002
                       :base (fn [_ _] -2.0)
                       :outcome (fn [c t] (swap! seen max (+ t 2)) (= c "A"))
                       :read (fn [_ _] [0.0])})]
    (is (= 2000 (apply max (map :t rows))))
    (is (= 1960 (apply min (map :t rows))))
    (is (<= @seen 2002))
    (is (every? #(= (:t %) (:g %)) rows) "clusters are origin years")))

(deftest the-out-of-sample-fit-reads-design-rows-only
  ;; parameters fitted on the design rows: changing test-year outcomes
  ;; changes the score, never the fitted coefficients
  (let [rows (synthetic-rows 17 0.0 0.5)
        design (filterv #(< (:t %) 1985) rows)
        test (filterv #(>= (:t %) 1985) rows)
        flipped (mapv #(update % :y not) test)
        a (cr/out-of-sample design test ["a" "b"])
        b (cr/out-of-sample design flipped ["a" "b"])]
    (is (= (:centres a) (:centres b)))
    (is (= (vec (:recal a)) (vec (:recal b))))
    (is (= (vec (:regime a)) (vec (:regime b))))
    (is (not= (:log-regime a) (:log-regime b)))
    (is (pos? (:log-gain a)) "the regime recalibration wins on held-out years with a real slope change")))

(deftest no-effect-gives-a-null-under-the-permutation
  ;; a panel state unrelated to outcomes, but with a world component and
  ;; year shocks in the outcomes: the permutation p is not small, and the
  ;; null is centred
  (let [g (m/rng 23)
        cs (mapv #(format "C%02d" %) (range 80))
        w (into {} (map vector (range 1960 2003) (reductions + (repeatedly 43 #(m/normal! g)))))
        shock (into {} (for [t (range 1960 2003)] [t (* 0.7 (m/normal! g))]))
        panel (into {} (for [c cs] [c (into (sorted-map) (for [t (range 1960 2003)] [t (+ (w t) (m/normal! g))]))]))
        base (into {} (for [c cs t (range 1960 2003)] [[c t] (+ -2.5 (m/normal! g))]))
        y (into {} (for [c cs t (range 1960 2003)] [[c t] (< (m/next-double! g) (expit (+ (* 0.6 (base [c t])) -1.0 (shock t))))]))
        res (cr/permutation-test {:panels {"s" panel} :kinds {"k" {:state "s" :sign 1}}
                                  :frames {"k" (for [c cs t (range 1960 2001)]
                                                 {:kind "k" :c c :t t :g t :L (base [c t]) :y (y [c t])})}
                                  :design [1960 2002] :q 0.8 :draws 60 :seed 3})]
    (is (> (:p-stat res) 0.05))
    (is (> (:p-d res) 0.05))
    (is (< (Math/abs (:null-mean-d res)) 0.15))))

(deftest a-slope-change-shows-under-the-permutation
  (let [g (m/rng 29)
        cs (mapv #(format "C%02d" %) (range 80))
        panel (into {} (for [c cs] [c (into (sorted-map) (for [t (range 1960 2003)] [t (m/normal! g)]))]))
        thr (cr/threshold (cr/demean panel) 1960 2002 0.8)
        frames (vec (for [c cs t (range 1960 2001)
                          :let [L (+ -2.5 (m/normal! g))
                                r (if (> (get-in (cr/demean panel) [c t]) thr) 1.0 0.0)]]
                      {:kind "k" :c c :t t :g t :L L
                       :y (< (m/next-double! g) (expit (+ -1.0 (* 0.5 (+ L 2.5)) -2.0 (* r 0.8 (+ L 2.5)))))}))
        res (cr/permutation-test {:panels {"s" panel} :kinds {"k" {:state "s" :sign 1}}
                                  :frames {"k" frames} :design [1960 2002] :q 0.8 :draws 60 :seed 3})]
    (is (< (:p-d res) 0.05))
    (is (close? 0.8 (:d res) (* 2.5 (second (:se (:fit res))))) "within 2.5 standard errors")))

(deftest options-from-the-command-line
  (let [o (cr/parse-opts [])]
    (is (= [1960 2002 1960 2002] ((juxt :from :to :design-from :design-to) o))))
  (let [o (cr/parse-opts ["--from" "2003" "--to" "2022"])]
    (is (= [2003 2022 1960 2002] ((juxt :from :to :design-from :design-to) o)))))

(deftest a-permutation-moves-whole-series
  (let [panel {"A" {1990 1.0 1991 2.0} "B" {1995 5.0} "C" {1990 7.0}}
        p (cr/permute-panel panel (m/rng 4))]
    (is (= (set (vals panel)) (set (vals p))) "each series kept whole, years and all")
    (is (= (set (keys panel)) (set (keys p))))))

(deftest the-base-s-warm-up-rows-are-not-recalibrated
  ;; before the year node has read an outcome it sits at its prior, p 0.5
  ;; for every country (bnm-0cf): those rows enter no fit
  (let [node {"AAA" (into (sorted-map) (for [t (range 1950 2000)] [(+ 11 (* 12 t)) [(if (< t 1963) 0.5 0.05) (when (>= t 1960) false)]]))}
        cat {:onsets {"AAA" (sorted-set (+ 2 (* 12 1975)))} :busy {} :from {"AAA" 0} :through {"AAA" (* 12 2100)} :known-lag 3}
        fr (with-redefs [bnm.relation-diag/read-year (fn [_ _] node)]
             (cr/frames-of "run" {"k" cat} ["k"] 1950 2002))]
    (is (= (range 1963 2000) (map :t (fr "k"))))))
