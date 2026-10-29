(ns bnm.robustness-test
  (:require [augur.math :as m]
            [bnm.attribution :as at]
            [bnm.robustness :as rb]
            [bnm.stocks :as sk]
            [clojure.set]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

;; --- eras ---------------------------------------------------------------------------

(deftest the-design-rotation-leaves-one-era-out
  (let [ss (rb/design-splits [1960 1971 1982 1993] 2002)]
    (is (= 4 (count ss)))
    (is (= [[1960 1968] [1971 1979] [1982 1990] [1993 2000]] (map (comp :era first :eras) ss))
        "each era holds the origins whose window (t, t+2] lies inside it")
    (doseq [{:keys [fit eras]} ss
            {:keys [score]} eras
            :let [fy (set (mapcat at/outcome-years fit))]]
      (is (seq score))
      (is (not-any? fy (mapcat at/outcome-years score)) "no fitted outcome year is a scored one")
      (is (<= (apply max (mapcat at/outcome-years (concat fit score))) 2002) "nothing after the design years"))
    (testing "every scored origin is scored once"
      (let [scored (mapcat #(mapcat :score (:eras %)) ss)]
        (is (= (count scored) (count (set scored))))))))

(deftest the-registered-run-fits-once-and-slices-the-scored-years
  (let [[s :as ss] (rb/registered-splits 1960 2002 2002 2022 [2002 2007 2012 2017])]
    (is (= 1 (count ss)))
    (is (= (set (range 1960 2001)) (:fit s)))
    (is (= [[2002 2006] [2007 2011] [2012 2016] [2017 2020]] (map :era (:eras s))))
    (is (= (set (range 2002 2021)) (set (mapcat :score (:eras s)))))
    (is (not-any? (set (mapcat at/outcome-years (:fit s))) (mapcat at/outcome-years (mapcat :score (:eras s))))))
  (testing "a fitted span that reaches a scored outcome year is refused"
    (is (thrown? Exception (rb/registered-splits 1960 2002 2001 2022 [2001 2007])))
    (is (thrown? Exception (rb/registered-splits 1960 2004 2002 2022 [2002 2007])))))

(deftest rows-are-cut-to-the-span-before-anything-is-read
  (let [rows (for [t (range 1990 2010)] {:c "A" :t t :y true :p 0.1})
        kept (rb/in-span rows (set (range 1990 2001)))]
    (is (= 2000 (apply max (map :t kept))))
    (is (<= (apply max (mapcat (comp at/outcome-years :t) kept)) 2002))))

;; --- dropout --------------------------------------------------------------------------

(deftest the-year-gauges-split-by-publication
  ;; V-Dem a year late, the national accounts, capabilities, WID and the
  ;; fiscal series two, WDI by each value's own publication
  (let [{:keys [vdem accounts wdi]} rb/year-row-sources]
    (is (= (set (range 29)) (into (set vdem) (concat accounts wdi))))
    (is (empty? (filter (set vdem) (concat accounts wdi))))
    (is (empty? (filter (set accounts) wdi)))))

(deftest doubled-lags-read-each-source-twice-as-late
  (let [row (fn [tag] (vec (repeat 29 tag)))
        r (rb/compose-row (row :vdem) (row :accounts) (row :wdi))]
    (doseq [i (:vdem rb/year-row-sources)] (is (= :vdem (r i))))
    (doseq [i (:accounts rb/year-row-sources)] (is (= :accounts (r i))))
    (doseq [i (:wdi rb/year-row-sources)] (is (= :wdi (r i)))))
  (testing "a series' publication lag doubled"
    (let [s (rb/double-lag (sk/series [[0 6 1.0] [12 18 2.0] [24 26 3.0]]))]
      (is (= 1.0 (sk/value s 12)) "period 0, published at 6, now at 12")
      (is (= 1.0 (sk/value s 23)))
      (is (= 2.0 (sk/value s 24)))
      (is (= 3.0 (sk/value s 28)) "period 24, published at 26, now at 28")))
  (testing "a later period is never known before an earlier one"
    (let [s (rb/double-lag (sk/series [[0 10 1.0] [12 14 2.0]]))]
      (is (nil? (sk/value s 19)))
      (is (= 2.0 (sk/value s 20)) "period 12 would be known at 16, held to period 0's 20")))
  (testing "a catalogue's known-lag doubled"
    (is (= 24 (:known-lag (rb/double-known-lag {:known-lag 12 :onsets {}}))))))

(deftest a-fifth-of-the-countries-go-dark-each-year
  (let [cs (mapv #(format "C%03d" %) (range 150))
        d (rb/dark-countries 1 1990 cs 0.2)]
    (is (= 30 (count d)))
    (is (every? (set cs) d))
    (is (= d (rb/dark-countries 1 1990 (reverse cs) 0.2)) "the draw does not depend on the order given")
    (is (not= d (rb/dark-countries 1 1991 cs 0.2)) "another year, other countries")
    (is (not= d (rb/dark-countries 2 1990 cs 0.2)) "another draw, other countries")))

(deftest dark-years-are-independent-across-years-and-draws
  ;; seeded at 1000003 (seed + d) + t, each country's key across years was a
  ;; Weyl walk: the spread of dark-year counts per country swung with the
  ;; draw, 1.65 to 2.60 over seeds 1-10, against 2.19 for independent years
  (let [cs (mapv #(format "C%03d" %) (range 200))
        ys (range 1990 2020)
        counts (fn [seed] (let [ds (map #(rb/dark-countries seed % cs 0.2) ys)]
                            (map (fn [c] (count (filter #(contains? % c) ds))) cs)))
        sd (fn [xs] (let [n (double (count xs)) mu (/ (reduce + xs) n)]
                      (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) n))))
        iid (Math/sqrt (* 30 0.2 0.8))]
    (doseq [seed (range 1 11) :let [v (sd (counts seed))]]
      (is (< (- iid 0.35) v (+ iid 0.35)) [seed v iid]))
    (let [overlap (fn [d] (/ (double (reduce + (map #(count (clojure.set/intersection (rb/dark-countries d % cs 0.2)
                                                                                       (rb/dark-countries (inc d) % cs 0.2)))
                                                    ys)))
                             (* 40 30)))]
      (doseq [d [1 2 5]]
        (is (< (Math/abs (- (overlap d) 0.2)) 0.04) [d (overlap d)])))))

(deftest a-knocked-out-input-is-unknown
  (is (= [nil 2.0 nil 4.0] (rb/knock [1.0 2.0 3.0 4.0] [0 2])))
  (is (= [1.0 2.0] (rb/knock [1.0 2.0] []))))

;; --- statistics ----------------------------------------------------------------------

(deftest worst-case-and-its-lowest-decile
  (let [xs (map double (range 1 41))]
    (is (= 1.0 (:minimax (rb/worst-case xs))))
    (is (close? (m/quantile (double-array xs) 0.1) (:decile (rb/worst-case xs)) 1e-12)))
  (is (Double/isNaN (:minimax (rb/worst-case [])))))

(deftest the-degradation-ratio
  ;; B loses 0.004, B+D 0.001: a quarter of the base's loss
  (is (close? 0.25 (rb/degradation 0.010 0.006 0.012 0.011) 1e-12))
  (is (Double/isNaN (rb/degradation 0.01 0.01 0.02 0.01)) "the base loses nothing: undefined"))

(deftest spread-over-eras
  (is (close? 0.0 (rb/sd [0.5 0.5 0.5]) 1e-12))
  (is (close? 1.0 (rb/sd [1.0 3.0]) 1e-12) "population spread"))

(deftest a-calibration-slope-past-group-levels
  (let [g (m/rng 5)
        groups (vec (repeatedly 40 #(- (* 1.5 (m/normal! g)) 3.0)))
        rows (vec (for [i (range 12000)
                        :let [k (mod i 40) l (* 1.3 (m/normal! g))]]
                    {:g k :l l :y (< (m/next-double! g) (m/expit (+ (groups k) (* 0.5 l))))}))
        b (rb/slope-fe rows (vec (repeat (count rows) 1.0)))]
    (is (close? 0.5 b 0.08) (str "slope " b))
    (testing "integer weights act as copies of rows"
      (let [w (vec (for [i (range (count rows))] (if (< (mod i 7) 2) 2.0 1.0)))
            copied (vec (mapcat (fn [r wi] (repeat (long wi) r)) rows w))]
        (is (close? (rb/slope-fe copied (vec (repeat (count copied) 1.0))) (rb/slope-fe rows w) 1e-6))))
    (testing "a zero weight drops a row"
      (let [w (vec (for [i (range (count rows))] (if (even? i) 1.0 0.0)))]
        (is (close? (rb/slope-fe (vec (take-nth 2 rows)) (vec (repeat (quot (inc (count rows)) 2) 1.0)))
                    (rb/slope-fe rows w) 1e-6))))))

;; --- synthetic robustness ---------------------------------------------------------------

(defn- synthetic
  "Two kinds on 140 countries x 1960-2002. A country level x drives the
  onsets. The base's gauges: an event gauge that reads x with noise, a
  slow gauge of noise and a history gauge of noise; the states panel holds
  x with noise. The lattice's forecast knows x."
  [seed]
  (let [g (m/rng seed)
        cs (mapv #(format "C%03d" %) (range 140))
        x (into {} (for [c cs] [c (m/normal! g)]))
        u (into {} (for [t (range 1960 2003)] [t (* 0.5 (m/normal! g))]))
        rows-of (fn [] (vec (for [c cs t (range 1960 2001)
                                  :let [e (+ -2.6 (u t) (* 1.0 (x c)))]]
                              {:c c :t t :y (< (m/next-double! g) (m/expit e)) :p (m/expit e)})))
        rows-by-kind {"k1" (rows-of) "k2" (rows-of)}
        gv (fn [c] (let [ev (+ (x c) (* 0.3 (m/normal! g)))] [ev (m/normal! g) (m/normal! g)]))
        gauges (into {} (for [[kd rs] rows-by-kind]
                          [kd (into {} (for [{:keys [c t]} rs :let [v (gv c)]]
                                         [[c t] {:intact v :lag2 (assoc v 0 (+ (x c) (* 1.5 (m/normal! g))))}]))]))
        panel (into {} (for [c cs] [c (into {} (for [t (range 1950 2003)] [t (+ (x c) (* 0.3 (m/normal! g)))]))]))]
    (rb/evaluate {:rows-by-kind rows-by-kind
                  :gauges gauges
                  :gauge-groups {:events [0] :slow [1] :history [2]}
                  :panels {"s" panel}
                  :splits (rb/design-splits [1960 1971 1982 1993] 2002)
                  :lambdas [1.0 100.0]
                  :reps 30 :seed 1 :dark-frac 0.2 :dark-draws 2})))

(defn- value [res criterion route stat & [scenario score]]
  (some #(when (and (= criterion (:criterion %)) (= route (:route %)) (= stat (:stat %))
                    (= (or scenario "-") (:scenario %)) (= (or score "log") (:score %)))
           (:value %))
        (:results res)))

(deftest the-states-hold-skill-up-when-the-event-gauge-fails
  (let [res (synthetic 31)]
    (testing "the base has skill and the states add to the gauges"
      (is (pos? (value res "skill" "lattice" "B")))
      (is (pos? (value res "skill" "gauges" "G")))
      (is (> (value res "skill" "gauges" "GD") (value res "skill" "gauges" "G"))))
    (testing "dropping the event gauge costs the gauge base, less with the states"
      (is (pos? (value res "dropout" "gauges" "loss-G" "events")))
      (is (< (value res "dropout" "gauges" "ratio-D" "events") 0.6))
      (is (> (value res "dropout" "gauges" "ratio-P" "events") 0.8) "another country's states do not stand in"))
    (testing "dropping the noise gauges costs nothing"
      (is (< (Math/abs (value res "dropout" "gauges" "loss-G" "slow")) 0.002)))
    (testing "the dark and lagged scenarios are scored"
      (is (some? (value res "dropout" "gauges" "ratio-D" "dark")))
      (is (pos? (value res "dropout" "gauges" "loss-G" "lag2"))))
    (testing "worst case, calibration and era spread are reported"
      (is (some? (value res "worst-case" "lattice" "minimax-D")))
      (is (<= (value res "worst-case" "lattice" "minimax-D") (value res "worst-case" "lattice" "decile-D")))
      (is (close? (- (value res "worst-case" "lattice" "worst-era-D") (value res "worst-case" "lattice" "worst-era-P"))
                  (value res "worst-case" "lattice" "worst-era-D-less-P") 1e-12))
      (is (close? 1.0 (value res "calibration" "lattice" "mean-slope-B" "-" "-") 0.25)
          "the lattice's forecast is the truth: slope near one")
      (is (some? (value res "calibration" "lattice" "drift-D-less-B" "-" "-")))
      (is (some? (value res "era-spread" "lattice" "sd-ratio-D"))))
    (testing "intervals bracket the point"
      (doseq [r (:results res)
              :when (and (= "dropout" (:criterion r)) (= "loss-G" (:stat r)) (= "events" (:scenario r)) (= "log" (:score r)))]
        (is (<= (:lo r) (:value r) (:hi r)))))))

(deftest no-warm-up-row-reaches-a-fit
  ;; the base's warm-up rows (bnm-0cf, at/year-rows marks them) are cut from every fit
  (let [rows (vec (for [t (range 1979 1995)] {:c "A" :t t :y (odd? t) :p 0.1 :warm-up (< t 1982)}))
        fit (rb/fit-rows-of rows (set (range 1979 1990)))]
    (is (= (range 1982 1990) (map :t fit)))))
