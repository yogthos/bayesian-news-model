(ns bnm.economic-events-test
  (:require [bnm.economic-events :as ee]
            [bnm.lattice-political :as lp]
            [bnm.named :as nm]
            [bnm.political-gauges :as pg]
            [bnm.releases]
            [bnm.slow-train :as st]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(deftest inflation-crises
  ;; consumer prices up 40% or more in the year; a run of such years is one
  ;; crisis, from its first year
  (is (= #{1990 1999} (ee/inflation-crises {1988 10.0 1989 25.0 1990 45.0 1991 300.0 1992 60.0 1993 12.0 1999 41.0})))
  (is (= #{} (ee/inflation-crises {2000 39.9}))))

(deftest growth-collapses
  ;; growth of output per head more than two standard deviations below the
  ;; country's own previous twenty years (at least fifteen of them), and
  ;; negative; once in five years
  (let [steady (into {} (for [y (range 1960 1981)] [y (* 100.0 (Math/exp (* 0.02 (- y 1960))) (if (odd? y) 1.003 0.997))]))
        hit (assoc steady 1981 (* (steady 1980) 0.95) 1982 (* (steady 1980) 0.93))]
    (is (= #{1981} (ee/growth-collapses hit)) "a fall of 5% after steady 2% growth; 1982 is the same episode")
    (is (= #{} (ee/growth-collapses steady)))
    (is (= #{} (ee/growth-collapses (select-keys hit (range 1975 1983)))) "too short a past to judge")))

(deftest sovereign-crises-joined
  ;; Laeven-Valencia's and Reinhart-Rogoff's dates, the same default dated a
  ;; year apart counted once
  (is (= #{1982 2001} (ee/join #{1982 2001} #{1983 2002})))
  (is (= #{1982 1990} (ee/join #{1982} #{1990}))))

(deftest food-price-shocks
  ;; twelve-month food inflation of 15% or more and 10 points above its
  ;; own previous five years' mean, by quarter; once a year
  (let [calm (into {} (for [q (range 8000 8020)] [q 3.0]))
        shock (assoc calm 8020 20.0 8021 22.0)]
    (is (= #{8020} (ee/food-shocks shock)))
    (is (= #{} (ee/food-shocks calm)))
    (is (= #{} (ee/food-shocks (assoc (into {} (for [q (range 8000 8020)] [q 12.0])) 8020 18.0)))
        "only 6 points above its own mean")))

(deftest a-catalogue-in-the-lattice-s-form
  ;; annual onsets at the year's first month, busy that year, covered from
  ;; the first year the rule can judge to the last with data, known 18
  ;; months on
  (let [cat (ee/catalogue {"AAA" {:onsets #{1990} :from 1975 :through 2020}})]
    (is (= [(* 12 1990)] (vec (get-in cat [:onsets "AAA"]))))
    (is (= (* 12 1975) (get-in cat [:from "AAA"])))
    (is (= (+ 11 (* 12 2020)) (get-in cat [:through "AAA"])))
    (is (contains? (get-in cat [:busy "AAA"]) (+ 5 (* 12 1990))))
    (is (= 18 (:known-lag cat)))))

(deftest spending-led-consolidations
  ;; the balance improves by 1.5 points of GDP or more and spending falls by
  ;; a point or more: the spending cuts that drive unrest (Ponticelli &
  ;; Voth), at Alesina & Ardagna's threshold; once in three years
  (let [bal {2009 -15.4 2010 -11.4 2011 -10.5 2012 -9.3 2013 -13.6 2014 -3.8}
        exp {2009 54.8 2010 53.1 2011 55.1 2012 57.9 2013 63.9 2014 51.5}]
    (is (= #{2010 2014} (ee/consolidations bal exp)) "Greece: 2010 (+4.0, -1.7) and 2014 (+9.8, -12.4)")
    (is (= #{} (ee/consolidations {2000 -5.0 2001 -3.0} {2000 40.0 2001 40.5})) "a tax-led one is not")))

(deftest runs-start-a-crisis
  (is (= #{1982 1990} (ee/onsets-of-runs #{1982 1983 1984 1990}))))

(deftest quarterly-food-onsets-not-before-their-data
  ;; bnm-56c.32: the quarterly rule reads each quarter's last month with
  ;; data, so the onset is dated at that month, never the quarter's first
  (let [calm (into {} (for [m (range 24000 24060)] [m 3.0]))
        shock (assoc calm 24060 3.0 24061 3.0 24062 20.0)]
    (is (= #{24062} (ee/quarterly-food-onsets shock)) "the quarter 8020's value is March's: dated March")
    (is (= #{24060} (ee/quarterly-food-onsets (assoc calm 24060 20.0))) "a quarter with one month: that month")
    (is (every? (fn [o] (contains? shock o)) (ee/quarterly-food-onsets shock)))
    (is (= (set (map #(+ 2 (* 3 %)) (ee/food-shocks (into {} (for [[m x] (sort shock)] [(quot m 3) x])))))
           (ee/quarterly-food-onsets shock))
        "the same quarters as the rule by quarter")))

(deftest food-price-shocks-by-month
  ;; the quarterly rule by month (bnm-56c.30): twelve-month food inflation of
  ;; 15% or more and 10 points above the mean of the previous sixty months
  ;; (at least thirty-six of them), once in twelve months, dated at the
  ;; month the rule first fires
  (let [calm (into {} (for [m (range 24000 24060)] [m 3.0]))
        shock (assoc calm 24060 12.0 24061 16.0 24062 22.0 24072 30.0)]
    (is (= #{24061} (ee/food-shocks-monthly shock)) "the month it crosses, not the quarter's first month; 24072 is eleven months on")
    (is (= #{24061 24073} (ee/food-shocks-monthly (assoc shock 24073 31.0))) "again once twelve months have passed")
    (is (= #{} (ee/food-shocks-monthly calm)))
    (is (= #{} (ee/food-shocks-monthly (select-keys shock (range 24030 24063)))) "thirty past months: too few to judge")
    (is (= #{} (ee/food-shocks-monthly (assoc (into {} (for [m (range 24000 24060)] [m 12.0])) 24060 18.0)))
        "only 6 points above its own mean")))

(deftest a-monthly-food-catalogue
  ;; onsets in months, busy three months, covered from the first month the
  ;; rule can judge (36 months of past) to the last month with data, known
  ;; at the food CPI's publication lag, not the annual sources' 18 months
  (let [infl (into {} (for [m (range 24000 24100)] [m (if (= m 24070) 40.0 3.0)]))
        cat (ee/monthly-food-catalogue {"AAA" infl})]
    (is (= [24070] (vec (get-in cat [:onsets "AAA"]))))
    (is (= #{24070 24071 24072} (set (get-in cat [:busy "AAA"]))))
    (is (= 24036 (get-in cat [:from "AAA"])))
    (is (= 24099 (get-in cat [:through "AAA"])))
    (is (= ee/food-publication-lag (:known-lag cat)))
    (is (< (:known-lag cat) 18))))

(deftest no-food-outcome-before-it-is-published
  ;; FAOSTAT publishes its monthly food CPIs quarterly (releases in
  ;; resources/bnm/releases.tsv). A month is read no earlier than the
  ;; release that carries it, and a month a release did not carry is not
  ;; read at that release's date.
  (let [rs (get (bnm.releases/read-releases) "faostat_cp")]
    (is (seq rs) "the table dates FAOSTAT's consumer price releases")
    (doseq [{:keys [published last-month release]} rs]
      (is (some? last-month) release)
      (is (>= (ee/food-known-at last-month) published) (str release ": its newest month not before it came out"))
      (is (> (ee/food-known-at (inc last-month)) published) (str release ": the next month not out at its date")))
    (is (= (+ 24000 ee/food-publication-lag) (ee/food-known-at 24000)))))

(deftest the-flag-swaps-only-the-food-catalogue
  ;; --food-outcomes monthly: food price shocks from the monthly series in
  ;; the db ({[iso variable] {:period [months] :value [...]}}), every other
  ;; kind's catalogue as it was
  (let [db {["AAA" "food/food_inflation"] {:period (vec (range 24000 24100))
                                           :value (vec (for [m (range 24000 24100)] (if (= m 24070) 40.0 3.0)))}
            ["AAA" "food/food_cpi"] {:period [24000] :value [1.0]}}
        cats {"food_price_shock" {:onsets {"AAA" #{24069}} :known-lag 18} "coup" {:known-lag 12}}
        out (ee/with-monthly-food cats db)]
    (is (= [24070] (vec (get-in out ["food_price_shock" :onsets "AAA"]))))
    (is (= ee/food-publication-lag (get-in out ["food_price_shock" :known-lag])))
    (is (= {:known-lag 12} (out "coup")))))

;; --- quarterly growth collapses (bnm-56c.37, docs/GROWTH-QUARTERLY.md) ---------

(defn- steady-growth
  "Annual log growth of output per head, 2% a year with a little noise."
  [y0 y1]
  (into {} (for [y (range y0 y1)] [y (+ 0.02 (if (odd? y) 0.003 -0.003))])))

(defn- qi [y k] (+ (* 4 y) (dec k)))

(deftest quarterly-growth-collapses
  ;; a quarter's per-head growth on the year before is negative and more than
  ;; 2.5 standard deviations below the mean of the previous twenty years'
  ;; annual growth (at least fifteen of them); once in twenty quarters
  (let [ag (steady-growth 1980 2010)
        calm (into {} (for [y (range 2000 2010) k (range 1 5)] [(qi y k) 0.02]))
        hit (assoc calm (qi 2008 4) -0.04 (qi 2009 1) -0.05 (qi 2009 2) -0.03)]
    (is (= #{(qi 2008 4)} (ee/quarterly-growth-collapses hit ag)) "2009Q1-Q2 are the same episode")
    (is (= #{} (ee/quarterly-growth-collapses calm ag)))
    (is (= #{(qi 2008 4) (qi 2013 4)}
           (ee/quarterly-growth-collapses (assoc hit (qi 2013 4) -0.04) (merge ag (steady-growth 2010 2014))))
        "again once twenty quarters have passed")
    (is (= #{} (ee/quarterly-growth-collapses {(qi 2008 4) 0.005} ag)) "below the line but positive: no collapse")
    (is (= #{} (ee/quarterly-growth-collapses hit (steady-growth 1995 2010))) "thirteen past years: too few to judge 2008")
    (is (= (ee/quarterly-growth-collapses hit ag) (ee/quarterly-growth-collapses hit (merge ag {2008 -0.5 2009 -0.9})))
        "the line reads only the years before the quarter's")))

(deftest a-quarterly-growth-catalogue
  ;; onsets at the quarter's last month (its data's, as quarterly-food-onsets),
  ;; busy twelve months, covered from the first quarter the rule can judge to
  ;; the last with data, known five months after the quarter
  (let [ag (steady-growth 1980 2010)
        g (assoc (into {} (for [y (range 1990 2010) k (range 1 5)] [(qi y k) 0.02])) (qi 2009 2) -0.06)
        cat (ee/quarterly-growth-catalogue {"AAA" g} {"AAA" ag})]
    (is (= [(+ (* 12 2009) 5)] (vec (get-in cat [:onsets "AAA"]))) "2009Q2: June")
    (is (= (set (range (+ (* 12 2009) 5) (+ (* 12 2009) 17))) (set (get-in cat [:busy "AAA"]))))
    (is (= (* 12 1995) (get-in cat [:from "AAA"])) "1995Q1: fifteen years of annual growth since 1980")
    (is (= (+ (* 12 2009) 11) (get-in cat [:through "AAA"])))
    (is (= 5 (:known-lag cat)))
    (is (= {} (:from (ee/quarterly-growth-catalogue {"AAA" g} {"AAA" (steady-growth 2000 2010)})))
        "a unit the rule never judges is not covered")))

(deftest the-flag-swaps-only-the-growth-catalogue
  ;; --growth-outcomes quarterly: growth collapses from the quarterly
  ;; catalogue at its covered units only, every other kind as it was
  (let [cats {"growth_collapse" {:onsets {"AAA" #{24000} "BBB" #{24012}} :from {"AAA" 0 "BBB" 0} :known-lag 18}
              "coup" {:known-lag 12}}
        q {:onsets {"AAA" #{24005}} :busy {"AAA" #{24005}} :from {"AAA" 23000} :through {"AAA" 24100} :known-lag 5}
        out (ee/with-quarterly-growth cats q)]
    (is (= q (out "growth_collapse")))
    (is (= {:known-lag 12} (out "coup")))
    (is (thrown? Exception (ee/with-quarterly-growth cats (assoc-in q [:from "ZZZ"] 0)))
        "a unit the annual catalogue does not cover")))

;; On the data (skipped without data/derived/gdp-quarterly.csv, written by
;; scripts/quarterly-gdp.py): the agreement the feasibility study measured.

(def ^:private quarterly-data
  (delay (when (and (.exists (io/file "data/derived/gdp-quarterly.csv")) (.exists (io/file "data/derived/econ-events.csv")))
           (let [db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))
                 annual (pg/read-annual)
                 cats (ee/read-catalogues)
                 units (sort (keys (get-in cats ["growth_collapse" :from])))
                 levels (into {} (for [u units] [u (ee/per-head-levels (get-in annual ["mad_gdppc" u] {}) db u)]))
                 q (ee/read-quarterly-growth "data/derived/gdp-quarterly.csv")]
             {:cats cats :levels levels :q q
              :qcat (ee/quarterly-growth-catalogue (select-keys q units) (update-vals levels ee/log-growth))}))))

(deftest the-annual-rebuild-matches-the-catalogue
  ;; the per-head levels the quarterly rule's line reads are the annual
  ;; catalogue's: rebuilt, the rule gives its onsets exactly
  (when-let [{:keys [cats levels]} @quarterly-data]
    (let [{:keys [onsets from through]} (cats "growth_collapse")]
      (is (= 171 (count from)))
      (doseq [[u ms] onsets]
        (is (= (set (map #(quot % 12) ms))
               (set (filter #(<= (quot (from u) 12) % (quot (through u) 12)) (ee/growth-collapses (levels u)))))
            u)))))

(defn- agreement
  "Annual onsets since 1980 with quarterly growth for all four quarters of
  their year, each with the first quarterly onset in y-1 Q3 to y Q4."
  [annual q qcat]
  (for [[u ms] (:onsets annual) m ms
        :let [y (quot m 12) g (get q u {})]
        :when (and (>= y 1980) (every? #(contains? g (+ (* 4 y) %)) (range 4)))]
    {:unit u :year y
     :hit (first (filter #(<= (+ (* 12 (dec y)) 6) % (+ (* 12 y) 11)) (get-in qcat [:onsets u])))}))

(deftest quarterly-growth-agrees-with-the-annual-catalogue
  ;; docs/GROWTH-QUARTERLY.md: recall about 0.87 of the testable annual
  ;; onsets since 1980, 31 of 33 in 2008-09, 2020 dated mostly 2020Q2
  (when-let [{:keys [cats q qcat]} @quarterly-data]
    (let [rows (agreement (cats "growth_collapse") q qcat)
          share (/ (double (count (filter :hit rows))) (count rows))
          in (fn [ys] (filter #(contains? ys (:year %)) rows))
          q2020 (frequencies (for [[_ ms] (:onsets qcat) m ms :when (<= (* 12 2019) m (+ (* 12 2021) 11))]
                               (- (quot m 3) (* 4 2020))))]
      (is (= 104 (count (filter (set (keys (get-in cats ["growth_collapse" :from]))) (keys q)))) "lattice units with quarterly data")
      (is (= 103 (count (:from qcat))) "units covered: Kosovo's data end in 2021, before its annual line has fifteen years")
      (is (<= 0.85 share 0.89) (str "recall " share " of " (count rows)))
      (is (= [31 33] [(count (filter :hit (in #{2008 2009}))) (count (in #{2008 2009}))]) "2008-09")
      (is (> (get q2020 1 0) (* 0.7 (reduce + (vals q2020)))) (str "2020 onsets mostly 2020Q2: " (sort q2020))))))

(deftest no-quarterly-onset-before-its-quarter-is-out
  (when-let [{:keys [q levels qcat]} @quarterly-data]
    (is (every? #(= 2 (mod % 3)) (mapcat val (:onsets qcat))) "dated at a quarter's last month")
    (is (= 5 (:known-lag qcat)))
    ;; the catalogue from the data up to each quarter of 2008-09 has the
    ;; whole's onsets to that quarter: an onset reads no later quarter
    (let [ag (update-vals levels ee/log-growth)
          upto (fn [c k] (into {} (for [[u ms] (:onsets c) :let [s (set (filter #(<= % (+ 2 (* 3 k))) ms))] :when (seq s)] [u s])))]
      (doseq [k (range (qi 2008 1) (qi 2010 1))]
        (let [cut (ee/quarterly-growth-catalogue (update-vals q #(into {} (filter (fn [[i _]] (<= i k)) %))) ag)]
          (is (= (upto qcat k) (upto cut k)) k))))
    ;; and the lattice reads an onset five months after its quarter ends
    (let [[u o] (first (for [[u ms] (sort (:onsets qcat)) o ms :when (>= (- o (get-in qcat [:from u])) 24)] [u o]))]
      (is (= 0.0 (#'lp/count-known qcat u (+ o 4) 12)))
      (is (= 1.0 (#'lp/count-known qcat u (+ o 5) 12))))))
