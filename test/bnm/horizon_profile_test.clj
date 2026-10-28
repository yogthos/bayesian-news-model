(ns bnm.horizon-profile-test
  (:require [augur.math :as m]
            [bnm.horizon-profile :as hp]
            [bnm.relation-diag]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(deftest a-value-is-read-as-known
  ;; records {data-year [year-known value]}: at year end Y the latest data
  ;; year published by Y, no older than max-age; nothing published later
  (let [recs {1990 [1991 1.0] 1991 [1992 2.0] 1992 [1995 3.0]}
        k (hp/as-known recs (range 1985 2001) 3)]
    (is (nil? (k 1990)) "1990's value is out in 1991")
    (is (= 1.0 (k 1991)))
    (is (= 2.0 (k 1993)))
    (is (= 2.0 (k 1994)) "1992's is not out until 1995")
    (is (= 3.0 (k 1995)))
    (is (nil? (k 1996)) "1992 is more than three years old at the end of 1996")))

(deftest the-outcome-is-the-onset-year-t-plus-h
  ;; origin t is the end of year t (month 12t + 11); lead h asks for an
  ;; onset in year t + h exactly, months (12(t+h-1)+11, 12(t+h)+11]
  (let [cat {:onsets {"AAA" (sorted-set (+ (* 12 1975) 4))}
             :from {"AAA" (* 12 1950)} :through {"AAA" (* 12 2010)}}]
    (is (= [false false true false false] (mapv #(hp/outcome cat "AAA" 1972 %) [1 2 3 4 5])))
    (is (= [true false false false false] (mapv #(hp/outcome cat "AAA" 1974 %) [1 2 3 4 5])))
    (is (= [false false false false false] (mapv #(hp/outcome cat "AAA" 1975 %) [1 2 3 4 5]))
        "an onset in the origin's own year is not ahead of it")
    (testing "not at risk while a crisis runs, as the lattice's target"
      (let [busy (assoc cat :busy {"AAA" (into (sorted-set) (range (* 12 1973) (* 12 1974)))})]
        (is (nil? (hp/outcome busy "AAA" 1972 2)) "in a crisis before the year opens")
        (is (nil? (hp/outcome busy "AAA" 1972 3)))
        (is (false? (hp/outcome busy "AAA" 1972 1)))))
    (testing "outside the catalogue's cover"
      (is (nil? (hp/outcome cat "AAA" 2009 2))))))

(deftest the-base-s-two-year-window-as-a-yearly-hazard
  ;; the year node forecasts an onset within 24 months; a constant hazard
  ;; gives the year's chance 1 - (1 - p)^(1/2)
  (is (close? (Math/log (/ 0.1 0.9)) (hp/annual-logit 0.19 24) 1e-9)))

(defn- synthetic
  "60 countries x 43 years; x iid N(0,1) by country and year; the onset in
  year s at country c has log odds -2.5 + b x(c, s - lag), so x at origin
  t drives the onset in year t + lag only."
  [seed b lag]
  (let [g (m/rng seed)
        cs (mapv #(format "C%02d" %) (range 60))
        x (into {} (for [c cs t (range 1950 2010)] [[c t] (m/normal! g)]))
        y (into {} (for [c cs s (range 1960 2010)
                         :let [e (+ -2.5 (* b (x [c (- s lag)])))]]
                     [[c s] (< (m/next-double! g) (/ 1.0 (+ 1.0 (Math/exp (- e)))))]))]
    {:cells (for [c cs t (range 1960 2003)] [c t])
     :x x :y y
     :outcome (fn [c t h] (get y [c (+ t h)]))
     :offset (fn [_ _] (Math/log (/ 0.076 0.924)))
     :read (fn [c t] (get x [c t]))}))

(deftest an-effect-at-lag-three-peaks-at-h-three
  (let [{:keys [cells outcome offset read]} (synthetic 11 1.0 3)
        prof (into {} (for [h (range 1 6)]
                        [h (hp/fit (hp/rows {:cells cells :outcome outcome :offset offset :read read
                                             :h h :from 1960 :to 2002 :kind "k"}))]))]
    (is (= 3 (key (apply max-key (comp :coef val) prof))))
    (is (close? 1.0 (:coef (prof 3)) 0.25))
    (is (every? #(< (Math/abs (:coef (prof %))) 0.2) [1 2 4 5]))
    (is (> (:z (prof 3)) 5.0))))

(deftest outcomes-after-the-design-years-are-not-read
  ;; with lead h, origins stop at to - h: no outcome after `to` is touched
  (let [{:keys [cells offset read y]} (synthetic 3 0.0 1)
        seen (atom 0)
        outcome (fn [c t h] (swap! seen max (+ t h)) (get y [c (+ t h)]))]
    (doseq [h (range 1 6)]
      (let [rs (hp/rows {:cells cells :outcome outcome :offset offset :read read :h h :from 1960 :to 2002 :kind "k"})]
        (is (= 2002 (apply max (map :g rs))) "clusters are the outcome years")
        (is (= (- 2002 h) (apply max (map :t rs))))))
    (is (<= @seen 2002))))

(deftest the-state-is-never-read-after-the-origin
  ;; bait: the state in year s is the outcome of year s itself. Read at the
  ;; origin it is unrelated to the onset h years on; read at t + h it would
  ;; predict perfectly
  (let [{:keys [cells outcome offset y]} (synthetic 5 0.0 1)
        reads (atom 0)
        read (fn [c t] (swap! reads max t) (if (get y [c t]) 1.0 0.0))]
    (doseq [h (range 1 6)]
      (let [r (hp/fit (hp/rows {:cells cells :outcome outcome :offset offset :read read :h h :from 1960 :to 2002 :kind "k"}))]
        (is (< (Math/abs (:coef r)) 0.2) (str "h " h))))
    (is (<= @reads 2001) "the latest origin with an outcome by 2002 is 2001")))

(deftest the-total-effect-offset-is-each-kind-s-rate
  ;; without the base, the offset is the kind's own rate in the rows, so
  ;; kinds pooled keep their own levels
  (let [rows (concat (for [i (range 10)] {:kind "a" :y (< i 1) :o 9.0})
                     (for [i (range 10)] {:kind "b" :y (< i 5) :o 9.0}))
        t (hp/total-offsets rows)]
    (is (close? (Math/log (/ 0.1 0.9)) (:o (first (filter #(= "a" (:kind %)) t))) 1e-9))
    (is (close? 0.0 (:o (first (filter #(= "b" (:kind %)) t))) 1e-9))))

(deftest the-horizon-statistics
  ;; skill is s * coef, s the expected sign. (a) slow states: the mean of
  ;; s * [coef(h 4,5) - coef(h 1,2)], > 0 when skill rises with lead.
  ;; (b) fast states: the mean of s * coef(h 1,2), > 0 when right-signed
  ;; at short leads; their decline s * [coef(h 1,2) - coef(h 4,5)] is for
  ;; the record and counts only when (b) is positive
  (let [specs {"slow" {:sign 1 :speed :slow} "slow-neg" {:sign -1 :speed :slow}
               "fast" {:sign 1 :speed :fast} "fast-wrong" {:sign 1 :speed :fast}}]
    (testing "a slow state whose right-signed effect rises with h gives (a) > 0"
      (let [coefs {"slow" {1 0.0 2 0.1 3 0.2 4 0.3 5 0.4} "slow-neg" {1 0.0 2 -0.1 3 -0.2 4 -0.3 5 -0.4}}]
        (is (close? 0.3 (hp/slow-stat coefs specs) 1e-12))
        (is (pos? (hp/slow-stat (select-keys coefs ["slow-neg"]) specs)) "a negative sign, more negative with lead")))
    (testing "a fast state's wrong-signed coefficient growing with h counts against it"
      (let [coefs {"fast-wrong" {1 -0.1 2 -0.1 3 -0.2 4 -0.3 5 -0.3}}
            r (hp/fast-stats coefs specs)]
        (is (neg? (:short r)) "(b) < 0: wrong-signed at short leads")
        (is (pos? (:decline r)) "its 'decline' looks like theory's: the wrong sign growing")
        (is (false? (:consistent? r)))))
    (testing "a right-signed fast state that fades is consistent"
      (let [r (hp/fast-stats {"fast" {1 0.4 2 0.4 3 0.2 4 0.0 5 0.0}} specs)]
        (is (close? 0.4 (:short r) 1e-12))
        (is (close? 0.4 (:decline r) 1e-12))
        (is (true? (:consistent? r)))))
    (testing "only states of the right speed enter each statistic"
      (let [coefs {"slow" {1 0.0 2 0.0 3 0.0 4 1.0 5 1.0} "fast" {1 0.4 2 0.4 3 0.2 4 0.0 5 0.0}}]
        (is (close? 1.0 (hp/slow-stat coefs specs) 1e-12))
        (is (close? 0.4 (:short (hp/fast-stats coefs specs)) 1e-12))))))

(deftest a-permutation-keeps-the-countries-values
  ;; country labels shuffled: each country reads another's series whole
  (let [read (fn [c t] (when (#{"A" "B" "C"} c) (+ (* 100 (int (first c))) t)))
        p (hp/permuted read ["A" "B" "C"] (m/rng 1))]
    (is (= (set (for [c ["A" "B" "C"]] (p c 2000))) (set (for [c ["A" "B" "C"]] (read c 2000)))))
    (is (every? #(= (- (p % 2001) (p % 2000)) 1) ["A" "B" "C"]))))

(deftest the-placebo-reads-another-country-known-that-year
  ;; the next country, in sorted order, among those with the state that
  ;; year: so a sparse state keeps every row it has
  (let [panel {"A" {2000 1.0 2001 1.1} "B" {2001 2.1} "C" {2000 3.0 2001 3.1} "D" {1999 4.0}}
        p (hp/placebo-read panel)]
    (is (= 3.0 (p "A" 2000)) "B has no 2000: C is next")
    (is (= 1.0 (p "C" 2000)) "wrapping round")
    (is (= 2.1 (p "A" 2001)))
    (is (nil? (p "D" 1999)) "alone that year")
    (is (nil? (p "B" 2000)) "no value of its own")))

(deftest the-coefficient-is-the-fitted-one-per-sd
  (let [{:keys [cells outcome offset read]} (synthetic 21 0.8 2)
        rs (hp/rows {:cells cells :outcome outcome :offset offset :read read :h 2 :from 1960 :to 2002 :kind "k"})]
    (is (close? (:coef (hp/fit rs)) (hp/coef rs) 1e-6))))

(deftest a-state-demeaned-by-year
  ;; less each year's mean over the countries with the state that year,
  ;; then scaled to unit spread; a year with one country has no contrast
  (let [d (hp/demean-by-year {"A" {2000 1.0 2001 5.0} "B" {2000 3.0 2001 7.0} "C" {1999 9.0}})]
    (is (close? (- (get-in d ["A" 2000])) (get-in d ["B" 2000]) 1e-12))
    (is (close? (get-in d ["A" 2000]) (get-in d ["A" 2001]) 1e-12) "the world's rise from 2000 to 2001 is gone")
    (is (close? 1.0 (Math/abs (get-in d ["A" 2000])) 1e-12) "unit spread")
    (is (nil? (get-in d ["C" 1999])))))

(deftest demeaning-centres-the-permutation-null
  ;; a state that is a world series plus country noise, outcomes driven
  ;; by the world (a year effect the offset does not hold): permuting
  ;; country labels keeps the world part, so the raw state's null is
  ;; centred away from zero; demeaned by year it is centred on zero
  (let [g (m/rng 31)
        cs (mapv #(format "C%02d" %) (range 60))
        w (into {} (map vector (range 1950 2010) (reductions + (repeatedly 60 #(m/normal! g)))))
        panel (into {} (for [c cs] [c (into (sorted-map) (for [t (range 1950 2010)] [t (+ (w t) (* 0.5 (m/normal! g)))]))]))
        wz (let [ws (vals w) mu (/ (reduce + ws) (count ws))
                 sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) ws)) (count ws)))]
             (fn [t] (/ (- (w t) mu) sd)))
        y (into {} (for [c cs s (range 1961 2010)]
                     [[c s] (< (m/next-double! g) (/ 1.0 (+ 1.0 (Math/exp (- (+ -2.5 (* 0.8 (wz (dec s)))))))))]))
        cells (for [c cs t (range 1960 2003)] [c t])
        base {:cells cells :outcome (fn [c t h] (get y [c (+ t h)])) :offset (fn [_ _] -2.5) :h 1 :from 1960 :to 2002 :kind "k"}
        null-mean (fn [p] (let [pg (m/rng 5)
                                vs (for [_ (range 20)]
                                     (hp/coef (hp/rows (assoc base :read (hp/permuted (fn [c t] (get-in p [c t])) cs pg)))))]
                            (/ (reduce + vs) 20.0)))]
    (is (> (null-mean panel) 0.2) "raw: the world component survives permutation")
    (is (< (Math/abs (null-mean (hp/demean-by-year panel))) 0.05) "demeaned: centred on zero")))

(deftest options-from-the-command-line
  (let [o (hp/parse-opts [])]
    (is (= 1960 (:from o)))
    (is (= 2002 (:to o)))
    (is (= (map :name hp/named) (map :name (:states o))) "all ten by default"))
  (let [o (hp/parse-opts ["--from" "2003" "--to" "2022" "--states" "credit-gap,reserves"])]
    (is (= 2003 (:from o)))
    (is (= 2022 (:to o)))
    (is (= ["credit-gap" "reserves"] (map :name (:states o)))))
  (is (thrown? Exception (hp/parse-opts ["--states" "credit-gap,no-such-state"])) "a misspelt name is an error"))

(deftest from-drops-earlier-origins
  (let [{:keys [cells offset read outcome]} (synthetic 3 0.0 1)
        rs (hp/rows {:cells cells :outcome outcome :offset offset :read read :h 2 :from 1975 :to 2002 :kind "k"})]
    (is (= 1975 (apply min (map :t rs))))
    (is (= 2000 (apply max (map :t rs))))))

(deftest a-subset-of-states-sets-what-the-statistics-pool
  (let [coefs {"credit-gap" {1 0.0 2 0.0 3 0.0 4 0.4 5 0.4}
               "youth-bulge" {1 0.4 2 0.4 3 0.0 4 0.0 5 0.0}
               "reserves" {1 -0.2 2 -0.2 3 0.0 4 0.0 5 0.0}
               "debt-service" {1 -0.2 2 -0.2 3 0.0 4 0.0 5 0.0}}
        pick (fn [s] (:states (hp/parse-opts ["--states" s])))
        [a b] (hp/horizon-stats coefs (pick "credit-gap,youth-bulge,reserves,debt-service"))
        [a' b'] (hp/horizon-stats coefs (pick "credit-gap,reserves"))]
    (is (close? 0.0 a 1e-12) "credit gap rises, the youth bulge falls")
    (is (close? 0.4 a' 1e-12) "the credit gap alone")
    (is (close? 0.0 b 1e-12) "reserves right-signed, debt service wrong")
    (is (close? 0.2 b' 1e-12) "reserves alone")))

(deftest the-registered-years-read-nothing-outside-them
  ;; --from 2003 --to 2022: no origin before 2003, no outcome after 2022
  (let [g (m/rng 9)
        cs (mapv #(format "C%02d" %) (range 20))
        y (into {} (for [c cs s (range 1990 2030)] [[c s] (< (m/next-double! g) 0.1)]))
        outcomes (atom []) reads (atom [])
        outcome (fn [c t h] (swap! outcomes conj (+ t h)) (get y [c (+ t h)]))
        read (fn [c t] (swap! reads conj t) (m/normal! g))
        cells (for [c cs t (range 1990 2025)] [c t])]
    (doseq [h (range 1 6)]
      (hp/rows {:cells cells :outcome outcome :offset (fn [_ _] -2.0) :read read :h h :from 2003 :to 2022 :kind "k"}))
    (is (= 2003 (apply min @reads)))
    (is (= 2021 (apply max @reads)))
    (is (<= 2004 (apply min @outcomes)))
    (is (= 2022 (apply max @outcomes)))))

(deftest the-states-are-read-through-the-registered-years
  ;; the slow-store states are tabulated as known over hp/years: through
  ;; 2021 at least, the last origin of a run to 2022
  (is (= 1950 (first hp/years)))
  (is (<= 2021 (last hp/years))))

(deftest the-base-s-warm-up-is-no-offset
  ;; the year node at its prior before it has read an outcome (bnm-0cf)
  (let [node {"AAA" (into (sorted-map) (for [t (range 1950 2000)] [(+ 11 (* 12 t)) [(if (< t 1963) 0.5 0.05) (when (>= t 1960) false)]]))}
        {:keys [cells base]} (with-redefs [bnm.relation-diag/read-year (fn [_ _] node)] (#'hp/cells-of "run" "k" 3))]
    (is (= (range 1963 2000) (map second cells)))
    (is (nil? (base "AAA" 1962)))
    (is (some? (base "AAA" 1963)))))
