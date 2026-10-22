(ns bnm.cycles-test
  (:require [bnm.cycles :as cy]
            [bnm.cycles-run :as run]
            [bnm.food :as food]
            [bnm.stockflow :as sf]
            [clojure.java.io :as io]
            [augur.math :as m]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

(defn- price-path
  "log price relative to equilibrium, every dt, from a run of `params`
  started off equilibrium (capacity short by 20%)."
  [params years]
  (let [r (cy/simulate (merge {:k0 0.8 :g 0.0 :pi 0.0} params) {:from 0.0 :to years :dt 0.05})]
    {:t (:t r) :gap (get-in r [:aux :price-gap]) :run r}))

;; ---------------------------------------------------------------------------
;; Structure

(deftest equilibrium-stays-put
  (let [r (cy/simulate {:k0 1.0 :i0 1.0 :g 0.0 :pi 0.0} {:from 0.0 :to 30.0 :dt 0.1})]
    (testing "started at its equilibrium, nothing moves"
      (is (every? #(close? % 0.0 1e-6) (get-in r [:aux :price-gap])))
      (is (every? #(close? % 1.0 1e-6) (get-in r [:aux :coverage-ratio])))
      (is (every? #(close? % 1.0 1e-6) (get-in r [:aux :pipeline-ratio]))))
    (testing "the accounts close"
      (is (< (sf/imbalance r) 1e-6)))))

(deftest price-is-a-decreasing-table-of-coverage
  (let [p #(cy/price-effect % 2.0)]
    (is (close? 1.0 (p 1.0) 1e-12) "f(1) = 1")
    (is (> (p 0.5) (p 1.0) (p 1.5)))
    (is (= (p 0.01) (p 0.1)) "flat beyond the table's ends")))

;; ---------------------------------------------------------------------------
;; Meadows (1970): commodity cycles whose period scales with the delays

(deftest oscillates-with-period-scaling-with-the-delays
  (let [base {:tau-c 3.0 :tau-s 2.0 :tau-p 1.0 :tau-d 0.5 :life 15.0 :beta 0.3 :tau-pa 0.5 :cover-d 0.25}
        ;; every time constant, desired coverage (years of demand) included
        scaled (into base (for [k [:tau-c :tau-s :tau-p :tau-d :life :tau-pa :cover-d]] [k (* 2.0 (base k))]))
        {:keys [t gap]} (price-path base 80.0)
        p1 (cy/period t gap)
        p2 (cy/period (:t (price-path scaled 160.0)) (:gap (price-path scaled 160.0)))]
    (testing "it cycles"
      (is (some? p1) "at least two peaks")
      (is (< 4.0 p1 30.0) (str "a period of " p1 " years")))
    (testing "doubling every time constant doubles the period"
      (is (close? (/ p2 p1) 2.0 0.2) (str p1 " -> " p2)))
    (testing "a longer construction time alone lengthens it"
      (let [g (price-path (assoc base :tau-c 6.0) 120.0)
            p3 (cy/period (:t g) (:gap g))]
        (is (> p3 (* 1.15 p1)) (str p1 " -> " p3))))))

;; ---------------------------------------------------------------------------
;; Sterman (1989): under-weighting the supply line makes the overshoot

(deftest under-weighted-supply-line-overshoots
  (let [base {:tau-c 3.0 :tau-s 2.0 :tau-p 1.0 :tau-d 0.5 :life 15.0}
        lo (price-path (assoc base :beta 0.1) 60.0)
        hi (price-path (assoc base :beta 1.0) 60.0)
        late (fn [{:keys [t gap]}] (for [[ti x] (map vector t gap) :when (> ti 20.0)] (Math/abs x)))
        trough (fn [{:keys [gap]}] (apply min gap))]
    (testing "capacity short: the price starts high, and with beta < 1 falls well below equilibrium"
      (is (pos? (apply max (take 40 (:gap lo)))))
      (is (< (trough lo) (* 2.0 (trough hi)) -0.02) (str (trough lo) " against " (trough hi))))
    (testing "beta = 1 is damped: little is left after twenty years; beta < 1 keeps cycling"
      (is (< (apply max (late hi)) 0.02) (str (apply max (late hi))))
      (is (> (apply max (late lo)) (* 5.0 (apply max (late hi))))))))

;; ---------------------------------------------------------------------------
;; Calibration recovers what made the data

(def truth {:tau-c 4.0 :beta 0.25 :eta 1.5 :k0 0.8 :i0 1.0 :g 0.02 :pi 0.01 :p-ref 50.0})

(defn- synthetic
  "{:price {year mean price} :output {year production}}: annual, from a run
  of the truth with small noise, demand shocked every two years as the
  calibration's prior says (sd 0.3)."
  [years seed]
  (let [g (m/rng seed)
        shocks (into {} (for [i (range 18)] [(keyword (str "s" i)) (* 0.3 (m/normal! g))]))
        r (cy/simulate (merge truth shocks {:seg 2.0}) {:from 1980.0 :to (+ 1980.0 years) :dt 0.25})
        at (fn [k y] (nth (get-in r [:aux k]) (long (Math/round (/ (- (+ y 0.5) 1980.0) 0.25)))))]
    {:price (into (sorted-map) (for [y (range 1980 (+ 1980 (long years)))]
                                 [y (Math/exp (+ (at :log-price y) (* 0.03 (m/normal! g))))]))
     :output (into (sorted-map) (for [y (range 1980 (+ 1980 (long years)))]
                                  [y (* 7.0 (Math/exp (+ (at :log-output y) (* 0.01 (m/normal! g)))))]))}))

(deftest calibration-recovers-synthetic-parameters
  ;; two draws of shocks and noise; with fewer members (40) the ensemble
  ;; collapses on some draws, confident and wrong
  (doseq [seed [7 21]]
    (let [{:keys [price output]} (synthetic 35 seed)
          cal (cy/calibrate-window {:price price :output output}
                                   {:members 100 :iterations 4 :seed 3 :noise {:log-price 0.05 :log-output 0.02}})
          p (:posterior cal)]
      (testing (str "draw " seed ": construction time and supply-line weight near the truth")
        (is (close? (get-in p [:tau-c :median]) 4.0 0.8) (str (p :tau-c)))
        (is (close? (get-in p [:beta :median]) 0.25 0.12) (str (p :beta))))
      (testing "the data narrow them from the prior"
        (is (< (get-in p [:tau-c :log-sd]) (get-in cy/priors [:tau-c :sd])))
        (is (< (get-in p [:beta :log-sd]) (get-in cy/priors [:beta :sd]))))
      (testing "the price reference, demand growth and price elasticity of coverage"
        (is (close? (Math/log (get-in p [:p-ref :median])) (Math/log 50.0) 0.15))
        (is (close? (get-in p [:g :mean]) 0.02 0.01))
        (is (close? (get-in p [:eta :median]) 1.5 0.3))))))

;; ---------------------------------------------------------------------------
;; Rows: causal, and in the states schema

(defn- panel-of
  "A commodity panel ({var {data-year [year-known value]}}) from synthetic
  data: prices known the year they are, output the year after."
  [{:keys [price output]}]
  {:price (into (sorted-map) (for [[y x] price] [y [y x]]))
   :output (into (sorted-map) (for [[y x] output] [y [(inc y) x]]))})

(def fast {:members 16 :iterations 2 :every 5 :min-years 8 :from 1990 :to 2014 :forecast-members 8})

(deftest rows-are-causal
  (let [data (synthetic 35 11)
        full (cy/commodity-rows "test" (panel-of data) fast)
        ;; everything from 2005 on replaced by nonsense
        cut (-> data
                (update :price #(into (sorted-map) (for [[y x] %] [y (if (>= y 2005) (* x 10.0) x)])))
                (update :output #(into (sorted-map) (for [[y x] %] [y (if (>= y 2005) (* x 3.0) x)]))))
        cut-rows (cy/commodity-rows "test" (panel-of cut) fast)
        upto (fn [rows y] (set (filter #(<= (:year %) y) rows)))]
    (is (seq (upto full 2005)))
    (testing "rows known by 2005 see nothing from 2005 on (prices) or published after 2005 (output)"
      (is (= (upto full 2005) (upto cut-rows 2005))))
    (testing "later rows do"
      (is (not= (set full) (set cut-rows))))))

(deftest rows-follow-the-states-schema
  (let [rows (cy/commodity-rows "test" (panel-of (synthetic 35 13)) fast)
        lines (cy/csv-lines rows)]
    (is (= "iso,year,variable,value" (first lines)))
    (is (every? #(= "WLD" (:iso %)) rows))
    (is (every? #(and (integer? (:year %)) (string? (:variable %)) (double? (:value %)) (< (Math/abs (:value %)) ##Inf)) rows))
    (testing "named cycles/<commodity>-<name>, kebab-case, as the other stock files"
      (is (every? #(str/starts-with? (:variable %) "cycles/test-") rows))
      (is (not-any? #(str/includes? (:variable %) "_") rows)))
    (testing "the variables the bead asks for"
      (let [vs (set (map :variable rows))]
        (doseq [v ["log-price" "price-gap" "model-price-gap" "coverage" "pipeline" "capacity-gap" "dir-1y" "dir-2y" "dir-3y"
                   "beta" "beta-sd" "construction-time" "construction-time-sd" "period" "calibrated"]]
          (is (vs (str "cycles/test-" v)) v))))
    (testing "no row is ahead of the data it reads: the first year has a full minimum window"
      (is (>= (apply min (map :year rows)) (+ 1980 8))))))

(deftest directions-start-from-the-observed-price
  ;; calibrations at 1990, 1995, 2000 ...: the row of 1996 reads the
  ;; calibration of 1995 (data years to 1994) and last year's price (1995)
  (let [data (synthetic 35 11)
        rows (fn [d] (into {} (for [r (cy/commodity-rows "test" (panel-of d) fast)] [[(:year r) (:variable r)] (:value r)])))
        a (rows data)
        b (rows (update data :price #(update % 1995 * Math/E)))
        v (fn [m n] (get m [1996 (str "cycles/test-" n)]))]
    (is (= 1995.0 (v a "calibrated")))
    (testing "rows before 1996 do not see the 1995 price"
      (is (= (into {} (filter #(< (first (key %)) 1996) a)) (into {} (filter #(< (first (key %)) 1996) b)))))
    (testing "a price e times higher: the observed gap one higher, the model's own state the same"
      (is (close? (- (v b "log-price") (v a "log-price")) 1.0 1e-9))
      (is (close? (- (v b "price-gap") (v a "price-gap")) 1.0 1e-9))
      (is (close? (v b "model-price-gap") (v a "model-price-gap") 1e-12)))
    (testing "the expected changes run from the observed price, not from the model's: one lower"
      (doseq [h ["dir-1y" "dir-2y" "dir-3y"]]
        (is (close? (- (v b h) (v a h)) -1.0 1e-9) h)))))

(deftest shocks-cover-long-windows
  (testing "a window of 80 years has a demand shock per segment, not the last of 32 held from year 64"
    (let [r (cy/simulate {:s35 0.5 :last-seg 45.0 :seg 2.0} {:from 0.0 :to 85.0 :dt 0.5})
          at (fn [t] (nth (get-in r [:aux :shock]) (long (/ t 0.5))))]
      (is (close? (at 71.0) 0.5 1e-12))
      (is (close? (at 69.0) 0.0 1e-12))
      (is (close? (at 73.0) 0.0 1e-12)))))

(deftest window-observations-normalize-each-quantity-at-its-first-year
  (let [{:keys [obs learn y0]} (cy/window-observations {:price (sorted-map 1990 10.0 1991 11.0 1992 12.0)
                                                         :demand (sorted-map 1989 1.0 1991 5.0 1992 6.0)
                                                         :output (sorted-map 1990 2.0 1992 4.0)})]
    (is (= 1990 y0))
    (testing "demand starting after the first price year is used, relative to its first value in the window"
      (is (some #{:d-scale} learn))
      (is (nil? (get-in obs [1990.5 :log-demand])))
      (is (close? (get-in obs [1991.5 :log-demand]) 0.0 1e-12))
      (is (close? (get-in obs [1992.5 :log-demand]) (Math/log 1.2) 1e-12)))
    (testing "output at the first price year as before"
      (is (some #{:q-scale} learn))
      (is (close? (get-in obs [1992.5 :log-output]) (Math/log 2.0) 1e-12)))
    (testing "no coverage observed, cover-d not learned"
      (is (not (some #{:cover-d} learn))))))

(deftest deflate-divides-by-the-index-known-by-then
  (is (= {2000 [2001 100.0] 2001 [2002 100.0]}
         (update-vals (cy/deflate {2000 [2000 110.0] 2001 [2001 121.0] 2002 [2002 50.0]}
                                  {2000 [2001 1.1] 2001 [2002 1.21]})
                      (fn [[k x]] [k (/ (Math/round (* x 1e9)) 1e9)])))
      "known the later of the two, a year without the index left out"))

;; ---------------------------------------------------------------------------
;; The tool's readers

(defn- tmp [lines]
  (let [f (java.io.File/createTempFile "cycles" ".csv")]
    (.deleteOnExit f)
    (spit f (str (str/join "\n" lines) "\n"))
    (.getPath f)))

(deftest food-rows-are-read-at-their-data-year
  (let [path (tmp ["iso,year,variable,value"
                   "WLD,2000,food/wheat-production,5.0E8"
                   "WLD,2000,food/wheat-use,4.0E8"
                   "WLD,2000,food/wheat-stocks-to-use,0.3"
                   "WLD,2000,food/grains-production,9.0E8"
                   "USA,2000,food/wheat-production,6.0E7"])
        f (run/read-food path)]
    (testing "food.csv's names (food/<item>-<name>); the data year food/lag before the year known"
      (is (= {(- 2000 food/lag) [2000 5.0E8]} (get-in f ["wheat" :output])))
      (is (= {(- 2000 food/lag) [2000 4.0E8]} (get-in f ["wheat" :demand]))))
    (testing "FAO's stock level drifts (a cumulated change above its low so far): not read as coverage"
      (is (nil? (get-in f ["wheat" :coverage]))))
    (testing "only the world's rows, only the three crops"
      (is (= #{"wheat"} (set (keys f)))))))

(deftest monthly-csv-is-known-the-month-after
  (let [path (tmp (cons "observation_date,X" (for [m (range 1 13)] (format "1950-%02d-01,%d.0" m m))))
        es (run/read-monthly-csv path)]
    (is (= 12 (count es)))
    (is (= {1950 [1951 6.5]} (run/annual-prices es)) "December's value is out in January")
    (testing "a month missing (the US CPI of October 2025, never published): a year only with :min-months allowing it"
      (let [es (remove #(= (first %) (+ (* 12 1950) 9)) es)]
        (is (= {} (run/annual-prices es)))
        (is (= {1950 [1951 (/ (- 78.0 10.0) 11.0)]} (run/annual-prices es 11)))))))

(deftest exposure-is-share-times-phase
  (let [phase {2010 0.4 2011 -0.2 2020 0.1}
        shares {"SAU" (sorted-map 2008 [2009 80.0] 2009 [2011 70.0])}
        out (cy/exposure-rows phase shares "cycles/fuel-exposure" {:max-lag 5})
        r9 (fn [x] (/ (Math/round (* x 1e9)) 1e9))]
    (testing "the latest share known by the year (data years before it), as a fraction, times that year's phase"
      (is (= #{{:iso "SAU" :year 2010 :variable "cycles/fuel-exposure" :value (r9 (* 0.8 0.4))}
               {:iso "SAU" :year 2011 :variable "cycles/fuel-exposure" :value (r9 (* 0.7 -0.2))}}
             (set (map #(update % :value r9) out)))))
    (testing "a share older than the lag allows is not used"
      (is (not-any? #(= 2020 (:year %)) out)))
    (testing "at most :max-lag years before the year"
      (let [ys (fn [yr] (set (map :year (cy/exposure-rows {2016 0.5} {"SAU" {yr [(inc yr) 50.0]}} "v" {:max-lag 5}))))]
        (is (= #{2016} (ys 2011)))
        (is (= #{} (ys 2010)))))))
