(ns bnm.tension-test
  (:require [bnm.tension :as tn]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

(defn- path
  "{year log-income} from 1950 for the yearly growth rates `gs`."
  [gs]
  (into (sorted-map) (map vector (iterate inc 1950) (reductions + 0.0 (map #(Math/log (+ 1.0 %)) gs)))))

(defn- recs
  "Records {data-year [known-year value]} known `lag` years after."
  [ys lag]
  (into (sorted-map) (for [[y v] ys] [y [(+ y lag) v]])))

;; ---------------------------------------------------------------- progress

(deftest steady-growth-is-perceived-and-expected
  ;; TREND on an exponential path converges on its growth rate; expectation
  ;; catches up, and the gap closes
  (let [segs (tn/chain [(recs (path (repeat 40 0.03)) 0)] {})
        pr (tn/progress segs {})
        late (pr 1985)]
    (is (= 1 (count segs)))
    (is (close? 0.03 (:perceived late) 1e-3))
    (is (close? 0.03 (:expected late) 2e-3))
    (is (close? 0.0 (:tension late) 2e-3))
    (is (nil? (pr 1950)) "the first years are burn-in: nothing")))

(deftest a-j-curve-raises-the-gap
  ;; Davies: a long rise and then a sharp reversal; perceived progress turns
  ;; at once, expectation, formed on the rise, lags it
  (let [gs (concat (repeat 15 0.05) (repeat 4 -0.04))
        pr (tn/progress (tn/chain [(recs (path gs) 0)] {}) {})
        peak (+ 1950 15)
        before (:tension (pr (dec peak)))
        after (:tension (pr (+ peak 3)))]
    (is (< (Math/abs before) 0.01) "on the rise expectation has caught up")
    (is (> after 0.04) "after the reversal expected runs well ahead of perceived")
    (is (> after (+ before 0.04)))
    (is (< (:perceived (pr (+ peak 3))) 0.0))
    (is (> (:expected (pr (+ peak 3))) 0.0) "expectation still positive")))

(deftest a-slump-then-recovery-closes-the-gap-again
  (let [gs (concat (repeat 10 0.03) (repeat 3 -0.05) (repeat 25 0.03))
        pr (tn/progress (tn/chain [(recs (path gs) 0)] {}) {})]
    (is (> (:tension (pr 1963)) 0.03))
    (is (< (Math/abs (:tension (pr 1987))) 0.005))))

;; ---------------------------------------------------------------- chain

(deftest the-chain-counts-each-data-year-once
  ;; a slow source (lag 2) and a fresh one (lag 1) from 1960: the switch
  ;; must neither skip nor double the year in between
  (let [lv (path (repeat 30 0.02))
        slow (recs lv 2)
        fresh (recs (into {} (filter #(>= (key %) 1959) lv)) 1)
        [seg] (tn/chain [fresh slow] {})]
    (is (close? (lv 1958) (seg 1960) 1e-12) "1960 knows 1958 from the slow source")
    (is (close? (lv 1960) (seg 1961) 1e-12) "1961 knows 1960 from the fresh source, 1959 counted once")
    (is (close? (lv 1975) (seg 1976) 1e-12))))

(deftest a-hole-in-the-data-breaks-the-chain
  (let [lv (path (repeat 30 0.02))
        holed (recs (into (sorted-map) (remove #(#{1960 1961 1962 1963 1964} (key %)) lv)) 0)
        segs (tn/chain [holed] {})]
    (is (= 2 (count segs)))
    (is (= 1959 (apply max (keys (first segs)))) "no new data year: the segment ends")
    (is (every? #(>= % 1965) (keys (second segs))))))

(deftest latest-is-as-known
  (let [r (recs {2000 1.0 2001 2.0 2002 3.0} 2)]
    (is (= [2001 2.0] (tn/latest r 2003 3)))
    (is (= [2000 1.0] (tn/latest r 2002 3)))
    (is (nil? (tn/latest r 2001 3)) "nothing known yet")
    (is (nil? (tn/latest r 2010 3)) "too stale")))

(deftest annual-records-carry-their-lag
  (let [annual {"mad_gdppc" {"AAA" {2000 10.0}}}]
    (is (= {2000 [2002 10.0]} (tn/annual-records annual "mad_gdppc" "AAA" 2 identity)))
    (is (= {2000 [2002 (Math/log 10.0)]} (tn/annual-records annual "mad_gdppc" "AAA" 2 tn/ln)))
    (is (= {} (tn/annual-records annual "mad_gdppc" "BBB" 2 identity)))))

;; ---------------------------------------------------------------- trust

(deftest trust-adjusts-slowly-toward-its-target
  (let [target (into (sorted-map) (concat (for [y (range 1990 2000)] [y 0.0]) (for [y (range 2000 2030)] [y 1.0])))
        tr (tn/trust target {:tau-trust 8.0})]
    (is (nil? (tr 1989)) "nothing before a target")
    (is (close? 0.0 (tr 1999) 1e-12) "starts at its target, in equilibrium")
    (is (close? (/ 1.0 8.0) (tr 2000) 1e-12) "the year's target moves it a step of 1/tau")
    (is (apply < (map tr (range 2000 2030))) "rises monotonically")
    (is (< (tr 2029) 1.0))
    (is (close? (- 1.0 (Math/pow (- 1.0 (/ 1.0 8.0)) 8)) (tr 2007) 1e-12) "first-order smoothing")))

(deftest trust-holds-where-its-target-is-unknown
  (let [target (sorted-map 2000 0.0 2001 1.0 2005 1.0)
        tr (tn/trust target {:tau-trust 4.0})]
    (is (= (tr 2001) (tr 2002) (tr 2004)) "no target: no adjustment")
    (is (> (tr 2005) (tr 2004)))))

(deftest trust-targets-inequality-and-spending
  (let [ys (range 1980 2020)
        ineq (into {} (for [y ys] [y (if (< y 2010) 1.0 (+ 1.0 (* 0.01 (mod y 3)) (* 0.1 (- y 2009))))]))
        ineq (merge-with + ineq (into {} (for [y ys] [y (* 0.01 (mod y 4))])))
        tgt (tn/trust-target ineq {} {:min-n 8})]
    (is (< (tgt 2019) -1.0) "rising inequality, falling trust target")
    (is (nil? (tgt 1981)) "too little history to standardize")))

;; ---------------------------------------------------------------- normalization

(deftest z-scores-are-within-country-and-causal
  (let [xs (into (sorted-map) (for [y (range 1960 2000)] [y (Math/sin y)]))
        z (tn/zscore xs {:min-n 10 :clip 3.0})
        z-cut (tn/zscore (into (sorted-map) (filter #(<= (key %) 1980) xs)) {:min-n 10 :clip 3.0})]
    (is (nil? (z 1965)))
    (is (some? (z 1969)))
    (is (= (z 1980) (z-cut 1980)) "a later value does not move an earlier score")
    (is (every? #(<= -3.0 % 3.0) (vals z)))
    (is (= 3.0 ((tn/zscore (assoc xs 2000 1e6) {:min-n 10 :clip 3.0}) 2000)) "clipped")
    (is (empty? (tn/zscore (zipmap (range 1960 2000) (repeat 1.0)) {:min-n 10})) "no spread: no score")))

(deftest no-component-dominates-psi
  ;; Turchin's EMP grows as e^2 and swamps a product; standardized and
  ;; added in logs, each component's share of Psi's variance stays modest
  (let [ys (range 1900 2020)
        seed (atom 7)
        unif (fn [] (swap! seed #(mod (+ (* 1103515245 %) 12345) 2147483648)) (/ (+ 0.5 @seed) 2147483648.0))
        noise (fn [] (* (Math/sqrt (* -2.0 (Math/log (unif)))) (Math/cos (* 2.0 Math/PI (unif)))))
        e (map #(+ 2.0 (Math/sin (* 0.13 %)) (* 0.2 (noise))) ys)
        raw {:mmp (zipmap ys (map (fn [_] (noise)) ys))
             :emp (zipmap ys (map #(Math/pow (* 50.0 %) 2) e))
             :sfd (zipmap ys (map (fn [_] (* 0.01 (noise))) ys))}
        subs (update-vals raw #(tn/zscore % {:min-n 10 :clip 3.0}))
        psi (tn/psi subs {})
        yrs (filter #(and (psi %) (> % 1950)) ys)
        variance (fn [xs] (let [m (/ (reduce + xs) (count xs))] (/ (reduce + (map #(* (- % m) (- % m)) xs)) (dec (count xs)))))
        tot (variance (map psi yrs))
        share (fn [k] (/ (variance (map (subs k) yrs)) tot))]
    (is (seq yrs))
    (doseq [k [:mmp :emp :sfd]] (is (< 0.15 (share k) 0.7) (str k " share " (share k))))
    (testing "weights scale a term, unset ones count 1"
      (is (close? (+ (* 2.0 ((subs :mmp) 2000)) ((subs :emp) 2000) ((subs :sfd) 2000))
                  ((tn/psi subs {:mmp 2.0}) 2000) 1e-12)))))

;; ---------------------------------------------------------------- the country

(defn- country
  "Records for one country, each source known `lag` years late as the tool
  makes them, with data years 1950-2015."
  []
  (let [ys (range 1950 2016)
        f (fn [g] (into (sorted-map) (for [y ys] [y (g y)])))
        inc-path (path (for [y ys] (if (< 2005 y 2010) -0.04 (+ 0.03 (* 0.01 (Math/sin y))))))]
    {:income [(recs (into {} (filter #(>= (key %) 1960) inc-path)) 1) (recs inc-path 2)]
     :urban [(recs (f #(Math/log (+ 20.0 (* 0.5 (- % 1950)) (Math/sin %)))) 1)]
     :debt [(recs (f #(+ 40.0 (* 10 (Math/sin (* 0.2 %))))) 2)]
     :labour-share (recs (f #(+ 0.55 (* 0.02 (Math/cos %)))) 2)
     :working-age (recs (f #(+ 55.0 (* 0.2 (- % 1950)) (Math/sin (* 3 %)))) 1)
     :tertiary (recs (f #(+ 5.0 (* 0.4 (- % 1950)) (Math/cos (* 2 %)))) 1)
     :top10 (recs (f #(+ 0.4 (* 0.02 (Math/sin (* 0.5 %))))) 2)
     :bottom50 (recs (f #(+ 0.15 (* 0.01 (Math/cos (* 0.7 %))))) 2)
     :spending (recs (f #(+ 30.0 (Math/sin (* 0.3 %)))) 2)
     :balance (recs (f #(- (* 3 (Math/sin (* 0.4 %))) 2.0)) 2)}))

(def opts {:from 1950 :to 2018})

(deftest the-country-schema
  (let [st (tn/country-stocks (country) opts)
        rows (tn/rows "AAA" st)]
    (is (seq rows))
    (doseq [[iso y v x] rows]
      (is (= "AAA" iso)) (is (integer? y)) (is (contains? (set tn/variables) v) v)
      (is (and (double? x) (== x x) (< (Math/abs x) 1e300)) [y v x]))
    (is (= (count rows) (count (set (map #(subvec % 0 3) rows)))) "one value per country, year and variable")
    (is (every? (set (map #(nth % 2) rows)) tn/variables) "every variable present with full data")
    (is (= "iso,year,variable,value" tn/header))))

(deftest nothing-is-read-before-it-is-known
  ;; every record known after year Y removed, the outputs at Y are unchanged
  (let [full (tn/country-stocks (country) opts)
        cut-at (fn [y r] (into (sorted-map) (filter (fn [[_ [k _]]] (<= k y)) r)))]
    (doseq [y [1975 1990 2004 2008 2011]
            :let [c (-> (country)
                        (update :income #(mapv (partial cut-at y) %))
                        (update :urban #(mapv (partial cut-at y) %))
                        (update :debt #(mapv (partial cut-at y) %)))
                  c (reduce #(update %1 %2 (partial cut-at y)) c
                            [:labour-share :working-age :tertiary :top10 :bottom50 :spending :balance])
                  part (tn/country-stocks c (assoc opts :to y))]]
      (is (= (full y) (part y)) (str "year " y)))))

(deftest missing-data-is-nil-not-zero
  (let [c (assoc (country) :tertiary {} :top10 {} :debt [] :balance {})
        st (tn/country-stocks c opts)
        y 2010]
    (is (nil? (get-in st [y "emp-credentials"])))
    (is (nil? (get-in st [y "emp"])) "no elite component: no EMP")
    (is (nil? (get-in st [y "sfd"])))
    (is (nil? (get-in st [y "psi"])) "Psi needs all three")
    (is (some? (get-in st [y "mmp"])) "MMP from what it has")
    (is (some? (get-in st [y "tension"])))
    (is (not-any? #(= "psi" (nth % 2)) (tn/rows "AAA" st)) "nil is left out, never written as 0"))
  (let [st (tn/country-stocks {:income []} opts)]
    (is (empty? (tn/rows "AAA" st)) "no data, no rows")))

(deftest the-reversal-shows-in-the-country
  ;; the synthetic income falls 2006-2009 (data years): known a year later
  (let [st (tn/country-stocks (country) opts)]
    (is (> (get-in st [2009 "tension"]) (+ 0.03 (get-in st [2004 "tension"]))))
    (is (> (get-in st [2009 "mmp-stagnation"]) (get-in st [2004 "mmp-stagnation"])))))
