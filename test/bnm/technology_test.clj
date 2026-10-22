(ns bnm.technology-test
  (:require [bnm.technology :as tech]
            [bnm.stockflow :as sf]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (and a b (< (Math/abs (- (double a) (double b))) tol)))

(defn- curve
  "Entries {:t :known :x} of a logistic, known `lag` years after the data
  year, with deterministic pseudo-noise of size `noise`."
  [K s tau years lag noise]
  (vec (for [[i t] (map-indexed vector years)]
         {:t t :known (+ t lag)
          :x (max 0.0 (+ (tech/logistic K s tau t) (* noise (Math/sin (* 1.7 i)))))})))

;; ---------------------------------------------------------------- logistic fit

(deftest logistic-closed-form
  (is (close? 40.0 (tech/logistic 80.0 0.3 1995.0 1995.0) 1e-9) "half the ceiling at the midpoint")
  (is (< (tech/logistic 80.0 0.3 1995.0 1960.0) 0.01))
  (is (> (tech/logistic 80.0 0.3 1995.0 2040.0) 79.9)))

(deftest fit-recovers-synthetic-parameters
  (testing "a full S, light noise"
    (let [obs (map (juxt :t :x) (curve 80.0 0.3 1995.0 (range 1970 2021) 0 0.8))
          f (tech/fit-logistic obs {:cap 100.0})]
      (is (close? 80.0 (:ceiling f) 2.0))
      (is (close? 0.3 (:speed f) 0.03))
      (is (close? 1995.0 (:midpoint f) 0.7))
      (is (:identified f) "past the inflection the ceiling is identified")))
  (testing "a fast technology with no cap: the ceiling found from the data"
    (let [obs (map (juxt :t :x) (curve 12.0 0.6 1960.0 (range 1940 1990) 0 0.05))
          f (tech/fit-logistic obs {})]
      (is (close? 12.0 (:ceiling f) 0.5))
      (is (close? 0.6 (:speed f) 0.06))
      (is (close? 1960.0 (:midpoint f) 0.5))))
  (testing "early in diffusion the ceiling is not identified"
    (let [obs (map (juxt :t :x) (curve 80.0 0.3 1995.0 (range 1975 1986) 0 0.0))
          f (tech/fit-logistic obs {:cap 100.0})]
      (is (some? f))
      (is (not (:identified f))))))

(deftest fit-ceiling-honest
  (testing "a plateau, then a late rise (US telephones known 1950): the ceiling not below the latest levels"
    (let [obs (concat (for [t (range 1880 1946)] [t (tech/logistic 17.0 0.11 1915.0 t)])
                      [[1946 22.0] [1947 24.0] [1948 26.0]])
          f (tech/fit-logistic obs {:cap 100.0})]
      (is (>= (:ceiling f) 26.0))))
  (testing "the ceiling is never below the highest level seen (a dip, then a rise)"
    (let [obs (for [t (range 1900 1951)]
                [t (* (if (<= 1930 t 1936) 0.8 1.0) 0.5 (Math/exp (* 0.08 (- t 1900))))])
          f (tech/fit-logistic obs {:cap 100.0})]
      (is (>= (:ceiling f) (reduce max (map second obs))))))
  (testing "still growing exponentially: the ceiling is not identified"
    (let [obs (for [t (range 1900 1951)] [t (* 0.5 (Math/exp (* 0.08 (- t 1900))))])
          f (tech/fit-logistic obs {:cap 100.0})]
      (is (not (:identified f)))))
  (testing "a ceiling at the search's bound is not identified"
    (let [obs (map (juxt :t :x) (curve 100.0 0.3 1990.0 (range 1960 2001) 0 0.0))
          f (tech/fit-logistic obs {:cap 100.0})]
      (is (<= (:ceiling f) 120.0))
      (is (not (and (:identified f) (>= (:ceiling f) (* 0.98 120.0)))) "at the bound means unidentified"))))

(deftest fit-is-nil-without-data
  (is (nil? (tech/fit-logistic [] {})))
  (is (nil? (tech/fit-logistic [[2000 1.0] [2001 2.0] [2002 3.0]] {})) "too few points")
  (is (nil? (tech/fit-logistic (for [t (range 1990 2010)] [t 0.0]) {})) "no adoption")
  (is (nil? (tech/fit-logistic (for [t (range 1990 2010)] [t 5.0]) {})) "no diffusion"))

;; ---------------------------------------------------------------- lag behind the frontier

(deftest lag-on-a-synthetic-leader-and-follower
  (let [years (range 1960 2021)
        leader (map (juxt :t :x) (curve 90.0 0.25 1985.0 years 0 0.0))
        lead-fit (tech/fit-logistic leader {:cap 100.0})]
    (testing "a follower 15 years behind"
      (let [follower (map (juxt :t :x) (curve 90.0 0.25 2000.0 years 0 0.0))]
        (is (close? 15.0 (tech/lag leader lead-fit follower {}) 0.5))))
    (testing "a follower ahead of the leader: a negative lag"
      (let [ahead (map (juxt :t :x) (curve 90.0 0.25 1980.0 (range 1960 1986) 0 0.0))
            leader* (filter #(<= (first %) 1988) leader)
            fit* (tech/fit-logistic leader* {:cap 100.0})]
        (is (close? -5.0 (tech/lag leader* fit* ahead {}) 1.0))))
    (testing "no adoption, no lag"
      (is (nil? (tech/lag leader lead-fit (for [t years] [t 0.0]) {})))
      (is (nil? (tech/lag leader lead-fit [] {}))))))

(deftest reach-time-interpolates-the-frontier
  (let [frontier [[2000 10.0] [2001 20.0] [2002 30.0]]]
    (is (close? 2000.5 (tech/reach-time frontier nil 15.0) 1e-9))
    (is (close? 2001.0 (tech/reach-time frontier nil 20.0) 1e-9))
    (is (nil? (tech/reach-time frontier nil 50.0)) "beyond the frontier, no fit: unknown")
    (testing "the first time it was reached, a later fall notwithstanding"
      (is (close? 2000.5 (tech/reach-time (conj frontier [2003 12.0]) nil 15.0) 1e-9)))
    (testing "a frontier already there when first seen: when it got there is unknown"
      (is (nil? (tech/reach-time [[1990 100.0] [1991 100.0]] nil 100.0)))
      (is (nil? (tech/reach-time [[1990 100.0] [1991 100.0]] nil 60.0))))
    (testing "the fit extrapolated no further than :horizon years beyond the frontier's data"
      (let [fr (for [t (range 1980 1991)] [t (tech/logistic 90.0 0.3 1995.0 t)])
            fit {:ceiling 90.0 :speed 0.3 :midpoint 1995.0 :identified true}]
        (is (close? 1975.0 (tech/reach-time fr fit (tech/logistic 90.0 0.3 1995.0 1975.0)) 1e-6))
        (is (close? 1955.0 (tech/reach-time fr fit (tech/logistic 90.0 0.3 1995.0 1940.0)) 1e-6)
            "40 years before its data: censored at the horizon, not dropped")
        (is (close? 2000.0 (tech/reach-time fr fit (tech/logistic 90.0 0.3 1995.0 2000.0)) 1e-6))
        (let [t90 (+ 1995.0 (/ (Math/log 9.0) 0.3))]
          (is (close? t90 (tech/reach-time fr fit (tech/logistic 90.0 0.3 1995.0 2010.0)) 1e-6)
              "near the ceiling: no later than when the fit reaches 90% of it")
          (is (close? t90 (tech/reach-time fr fit 95.0) 1e-6)
              "above the identified ceiling: the same bound, not infinity"))
        (is (close? 2015.0 (tech/reach-time fr (assoc fit :speed 0.05) 80.0) 1e-6)
            "a slow fit: censored at the horizon")
        (is (close? 1990.0 (tech/reach-time fr (assoc fit :identified false) 80.0) 1e-6)
            "beyond the data on an unidentified fit: no later than its last point (at least at the frontier)")))))

;; ---------------------------------------------------------------- causality

(deftest as-of-reads-only-what-is-known
  (let [es [{:t 2000 :known 2001 :x 1.0} {:t 2001 :known 2002 :x 2.0} {:t 2002 :known 2003 :x 3.0}]]
    (is (= [[2000 1.0] [2001 2.0]] (tech/as-of es 2002)))
    (is (= [] (tech/as-of es 2000)))))

(def techs
  {"web" {:cap 100.0 :saturation 100.0}
   "cars" {}})

(defn- panel []
  {"web" {"USA" (curve 90.0 0.3 1998.0 (range 1985 2021) 1 0.3)
          "AAA" (curve 90.0 0.3 2008.0 (range 1990 2021) 1 0.3)
          "BBB" (curve 60.0 0.25 2015.0 (range 1995 2021) 1 0.3)}
   "cars" {"USA" (curve 50.0 0.2 1940.0 (range 1900 2006) 2 0.2)
           "AAA" (curve 40.0 0.2 1965.0 (range 1920 2006) 2 0.2)}})

(deftest no-data-beyond-the-known-year
  (let [base (panel)
        cut 2004
        ;; everything known after `cut` replaced with nonsense
        spoiled (update-vals base (fn [by] (update-vals by (fn [es] (mapv #(if (> (:known %) cut) (assoc % :x 1e6) %) es)))))
        rows-of #(set (filter (fn [r] (<= (:year r) cut)) (tech/technology-rows {:techs techs :data % :frontier "USA" :years (range 1990 2021)})))]
    (is (seq (rows-of base)))
    (is (= (rows-of base) (rows-of spoiled)))))

;; ---------------------------------------------------------------- schema

(def eras {"recent" #{"web"} "historical" #{"cars"}})

(defn- value-of [rows]
  (let [ix (into {} (map (fn [r] [[(:iso r) (:year r) (:variable r)] (:value r)])) rows)]
    (fn [iso y var] (ix [iso y var]))))

(deftest rows-schema
  (let [rows (tech/technology-rows {:techs techs :eras eras :data (panel) :frontier "USA" :years (range 1990 2021)})
        vars (set (map :variable rows))
        v (value-of rows)]
    (is (seq rows))
    (is (every? #(and (string? (:iso %)) (integer? (:year %)) (string? (:variable %))
                      (double? (:value %)) (< (Math/abs (:value %)) Double/POSITIVE_INFINITY))
                rows)
        "iso,year,variable,value; no missing values written")
    (is (= (count rows) (count (set (map (juxt :iso :year :variable) rows)))) "one value per iso, year, variable")
    (testing "every variable under technology/, kebab-case (bnm.states merges files by name)"
      (is (every? #(re-matches #"technology/[a-z0-9]+(-[a-z0-9]+)*" %) vars) (pr-str (remove #(re-matches #"technology/[a-z0-9]+(-[a-z0-9]+)*" %) vars))))
    (doseq [x ["technology/web-level" "technology/web-adoption" "technology/web-lag" "technology/web-speed"
               "technology/web-midpoint" "technology/gap-recent" "technology/gap-historical"
               "technology/lag-recent" "technology/gap-recent-count" "technology/gap-recent-break"
               "technology/productivity-inflow"]]
      (is (contains? vars x) x))
    (is (not-any? #{"tech_gap" "technology/gap"} vars) "no gap across eras: its set breaks when an era's data end")
    (testing "the year is the year known: web data for 1990 known in 1991"
      (is (= 1991 (reduce min (map :year (filter #(and (= "AAA" (:iso %)) (= "technology/web-level" (:variable %))) rows))))))
    (testing "a follower lags, the frontier's lag is zero"
      (is (close? 10.0 (v "AAA" 2015 "technology/web-lag") 1.5))
      (is (close? 0.0 (v "USA" 2015 "technology/web-lag") 1e-9))
      (is (pos? (v "AAA" 2015 "technology/gap-recent")))
      (is (close? 0.0 (v "USA" 2015 "technology/gap-recent") 1e-9)))
    (testing "adoption is a share: between 0 and 1"
      (is (every? #(<= 0.0 (:value %) 1.0) (filter #(re-find #"-adoption$" (:variable %)) rows))))
    (testing "missing is absent: BBB has no cars, so no cars rows"
      (is (empty? (filter #(and (= "BBB" (:iso %)) (= "technology/cars-level" (:variable %))) rows))))))

(deftest gap-breaks-flagged
  (let [rows (tech/technology-rows {:techs techs :eras {"all" #{"web" "cars"}} :data (panel)
                                    :frontier "USA" :years (range 1985 2021)})
        v (value-of rows)]
    (testing "a technology entering the era's set: a break"
      (is (= 1.0 (v "AAA" 1991 "technology/gap-all-break")))
      (is (= 2.0 (v "AAA" 1991 "technology/gap-all-count"))))
    (is (= 0.0 (v "AAA" 2000 "technology/gap-all-break")) "the same set as the year before: none")
    (testing "a technology leaving it (cars data end 2005, stale from 2010): a break"
      (is (= 1.0 (v "AAA" 2010 "technology/gap-all-break")))
      (is (= 1.0 (v "AAA" 2010 "technology/gap-all-count"))))))

(deftest adoption-capped-at-saturation
  (testing "subscriptions above one per person are full adoption, not more"
    (let [data {"mobile" {"USA" (curve 110.0 0.3 2000.0 (range 1985 2021) 1 0.0)
                          "ARE" (curve 200.0 0.3 2000.0 (range 1985 2021) 1 0.0)}}
          rows (tech/technology-rows {:techs {"mobile" {:cap 150.0 :saturation 100.0}} :data data
                                      :frontier "USA" :years (range 1990 2021)})
          v (value-of rows)]
      (is (= 1.0 (v "ARE" 2020 "technology/mobile-adoption")))
      (is (= 1.0 (v "USA" 2020 "technology/mobile-adoption")))
      (is (some? (v "ARE" 2020 "technology/mobile-lag")) "a level the frontier never reached still has a (censored) lag"))))

;; ---------------------------------------------------------------- aggregates

(deftest gap-and-inflow
  (let [prev {"a" {:gap 0.5 :lag 20.0 :frontier-adoption 1.0}
              "b" {:gap 0.2 :lag 10.0 :frontier-adoption 0.5}}
        cur {"a" {:gap 0.4 :lag 19.0 :frontier-adoption 1.0}
             "b" {:gap 0.2 :lag 11.0 :frontier-adoption 0.5}
             "c" {:gap 0.9 :lag nil :frontier-adoption 0.1}}
        all #{"a" "b" "c"}]
    (testing "the gap weighted by the frontier's adoption"
      (is (close? (/ (+ (* 1.0 0.4) (* 0.5 0.2) (* 0.1 0.9)) 1.6) (:gap (tech/aggregate cur all)) 1e-9))
      (is (close? 15.0 (:lag (tech/aggregate cur all)) 1e-9) "lags averaged where known")
      (is (= 3.0 (:count (tech/aggregate cur all))))
      (is (= #{"a" "b" "c"} (:techs (tech/aggregate cur all)))))
    (testing "only the era's technologies"
      (is (close? 0.4 (:gap (tech/aggregate cur #{"a"})) 1e-9))
      (is (nil? (:gap (tech/aggregate cur #{"z"})))))
    (testing "the inflow: the gap closed, over the technologies known both years"
      (is (close? (/ (+ (* 1.0 0.1) (* 0.5 0.0)) 1.5) (tech/inflow prev cur) 1e-9)))
    (is (nil? (tech/inflow {} cur)) "no common technology, no inflow")
    (is (nil? (:gap (tech/aggregate {} all))))))

;; ---------------------------------------------------------------- leading inflows

(deftest per-million-known-when-both-are
  (let [patents [{:t 2000 :known 2001 :x 500.0} {:t 2001 :known 2002 :x 600.0}]
        pop [{:t 2000 :known 2001 :x 1e7} {:t 2001 :known 2003 :x 1.1e7}]]
    (is (= [{:t 2000 :known 2001 :x 50.0} {:t 2001 :known 2003 :x (/ 600.0 11.0)}]
           (tech/per-million patents pop)))
    (is (= [] (tech/per-million patents [])))))

(deftest latest-known-value
  (let [es [{:t 2000 :known 2001 :x 1.0} {:t 2002 :known 2003 :x 3.0}]]
    (is (= 1.0 (tech/latest es 2002 3)))
    (is (= 3.0 (tech/latest es 2003 3)))
    (is (nil? (tech/latest es 2000 3)) "nothing known yet")
    (is (nil? (tech/latest es 2009 3)) "stale: data more than three years old")))

;; ---------------------------------------------------------------- the stock

(deftest diffusion-stock-integrates-its-inflow
  (let [m (tech/diffusion-model {:ceiling 80.0 :speed 0.3 :midpoint 1995.0 :start 1960.0})
        run (sf/run m {:from 1960.0 :to 2030.0 :dt 0.01})
        a (get-in run [:stocks :adopters])
        idx #(long (Math/round (/ (- % 1960.0) 0.01)))]
    (is (close? (tech/logistic 80.0 0.3 1995.0 1995.0) (nth a (idx 1995.0)) 0.5))
    (is (close? (tech/logistic 80.0 0.3 1995.0 2010.0) (nth a (idx 2010.0)) 0.5))
    (is (< (Math/abs (sf/imbalance run)) 1e-6) "the stock changes only by its inflow")))

(deftest calibrate-recovers-speed
  (let [obs (map (juxt :t :x) (curve 80.0 0.3 1995.0 (range 1970 2021 2) 0 0.5))
        post (tech/calibrate-diffusion obs {:ceiling {:mean 70.0 :sd 0.3 :log true}
                                             :speed {:mean 0.2 :sd 0.5 :log true}
                                             :midpoint {:mean 1990.0 :sd 10.0}
                                             :noise 1.0 :members 60 :seed 3})]
    (is (close? 0.3 (get-in post [:params :speed :median]) 0.05))
    (is (close? 1995.0 (get-in post [:params :midpoint :median]) 1.5))
    (is (close? 80.0 (get-in post [:params :ceiling :median]) 4.0))))
