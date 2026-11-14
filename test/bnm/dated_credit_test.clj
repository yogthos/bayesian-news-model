(ns bnm.dated-credit-test
  "bnm-9vm option A, --dated-credit: the banking node's credit-family
  inputs from the BIS series from the December 2003 origin, spliced in
  transformed space, each read only once published."
  (:require [bnm.invariants :as iv]
            [bnm.lattice-banking :as lb]
            [bnm.macrohistory :as mh]
            [bnm.stocks :as sk]
            [bnm.world-run :as wr]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- wiggle [seed i] (Math/sin (+ (* 0.7 i) (* 1.3 seed))))

(defn- jst-rows
  "A contiguous synthetic Macrohistory panel for `iso`, 1960-2020, with
  enough dimensions for the displacement invariants."
  [iso]
  (vec (for [y (range 1960 2021) :let [i (- y 1960)]]
         {:iso iso :year y :gdp 100.0 :pop 1.0 :cpi 100.0 :crisisJST 0.0
          :tloans (+ 60.0 (* 0.5 i) (* 5.0 (wiggle 1 i))) :tmort (+ 30.0 (* 0.4 i) (* 3.0 (wiggle 2 i)))
          :hpnom (+ 100.0 i (* 4.0 (wiggle 3 i))) :iy (+ 0.2 (* 0.02 (wiggle 4 i)))
          :debtgdp (+ 50.0 (* 5.0 (wiggle 5 i))) :stir (+ 4.0 (wiggle 6 i)) :unemp (+ 6.0 (wiggle 7 i))})))

(defn- ratio [rows k] (into {} (for [r rows] [(:year r) (* 100.0 (/ (k r) (:gdp r)))])))

(defn- jst-gaps [rows]
  (into {} (for [[y g] (mh/one-sided-gaps (sort (ratio rows :tloans)))] [[(:iso (first rows)) y] g])))

(defn- by-key [built] (into {} (for [r built] [[(:iso r) (:year r)] r])))

(def paths
  [[:specific :credit] [:specific :mortgages] [:specific-long :credit] [:specific-long :mortgages]
   [:specific-long :credit-gap-z] [:growth-3y :credit] [:invariant :norm] [:invariant :norm-long]
   [:invariant :resonance] [:invariant :flow-norm] [:invariant :breadth] [:invariant :world-norm]])

(defn- close? [a b] (or (and (nil? a) (nil? b)) (and a b (< (Math/abs (- (double a) (double b))) 1e-9))))

(deftest the-one-sided-gap-is-the-script-s
  ;; scripts/jst-credit-gap.py (statsmodels' hpfilter, lambda 1562.5) on
  ;; the Macrohistory panel's credit to GDP: the same filter in Clojure
  (let [rows (filter #(= "USA" (:iso %)) (mh/read-jst "data/raw/slow/jst/jst.csv"))
        xs (sort (for [r rows :when (and (:tloans r) (:gdp r))] [(:year r) (* 100.0 (/ (:tloans r) (:gdp r)))]))
        ours (mh/one-sided-gaps xs)
        theirs (into {} (for [l (rest (str/split-lines (slurp "data/derived/jst-credit-gap.csv")))
                              :let [[iso y g] (str/split l #",")] :when (= "USA" iso)]
                          [(parse-long y) (parse-double g)]))]
    (is (= (set (keys theirs)) (set (keys ours))))
    (is (every? (fn [[y g]] (< (Math/abs (- g (ours y))) 1e-6)) theirs))))

(deftest a-level-difference-cancels-at-the-splice
  ;; BIS total credit runs above JST's bank loans: a series at 1.7 times
  ;; JST's level (and household credit at twice its mortgages) gives every
  ;; input the same value spliced or not, at every year
  (let [rows (jst-rows "AAA")
        bis {"AAA" {:credit (update-vals (ratio rows :tloans) #(* 1.7 %))
                    :mortgages (update-vals (ratio rows :tmort) #(* 2.0 %))}}
        dated {:from 2002 :values (mh/dated-inputs rows bis 2002)}
        plain (by-key (mh/build rows nil nil (jst-gaps rows)))
        spliced (by-key (mh/build rows nil nil (jst-gaps rows) dated))]
    (doseq [y (range 1990 2021) p paths]
      (is (close? (get-in (plain ["AAA" y]) p) (get-in (spliced ["AAA" y]) p)) (str y " " p)))
    (is (every? some? (map #(get-in (spliced ["AAA" 2005]) %) paths)) "every input known: the check is not of nils")))

(deftest the-spliced-inputs-are-the-bis-series-own-transforms
  ;; from the splice on, each input is computed on the BIS series' own
  ;; history alone, never its current value against JST's past; before it,
  ;; JST's
  (let [rows (jst-rows "AAA")
        jst-credit (ratio rows :tloans)
        bis-credit (into {} (for [[y v] jst-credit] [y (* 1.5 (+ v (if (>= y 1990) (* 2.0 (- y 1990)) 0.0) (* 3.0 (wiggle 9 y))))]))
        bis {"AAA" {:credit bis-credit :mortgages (ratio rows :tmort)}}
        dated {:from 2002 :values (mh/dated-inputs rows bis 2002)}
        plain (by-key (mh/build rows nil nil (jst-gaps rows)))
        spliced (by-key (mh/build rows nil nil (jst-gaps rows) dated))
        v (vec (for [y (range 1870 2021)] (get bis-credit y)))
        at (fn [y] (- y 1870))]
    (doseq [y [2002 2005 2010]]
      (is (close? (iv/stretch v (at y) 10 6) (get-in spliced [["AAA" y] :specific :credit])) (str "stretch " y))
      (is (close? (iv/stretch v (at y) 20 12) (get-in spliced [["AAA" y] :specific-long :credit])) (str "long " y)))
    (testing "three-year growth on the BIS series, in JST's units by the ten years before the splice"
      (let [k (/ (/ (reduce + (map #(get jst-credit %) (range 1993 2003))) 10)
                 (/ (reduce + (map #(get bis-credit %) (range 1993 2003))) 10))]
        ;; JST's credit is a ratio, the BIS's a percentage
        (is (close? (* 0.01 k (- (bis-credit 2005) (bis-credit 2002)))
                    (get-in spliced [["AAA" 2005] :growth-3y :credit])))))
    (testing "the gap on the BIS series' own one-sided trend, in units of its own past gaps"
      (let [g (into {} (mh/one-sided-gaps (sort bis-credit)))]
        (is (close? (mh/gap-z g 2005) (get-in spliced [["AAA" 2005] :specific-long :credit-gap-z])))))
    (doseq [y [1995 2001] p paths]
      (is (close? (get-in (plain ["AAA" y]) p) (get-in (spliced ["AAA" y]) p)) (str "before the splice " y " " p)))
    (is (not (close? (get-in plain [["AAA" 2005] :specific :credit]) (get-in spliced [["AAA" 2005] :specific :credit]))))))

(deftest too-short-a-bis-history-keeps-jst
  ;; household credit from 1999 (Switzerland's BIS series): no stretch
  ;; against six past years until 2005, JST's in the meantime
  (let [rows (jst-rows "AAA")
        hh (into {} (for [[y v] (ratio rows :tmort) :when (>= y 1999)] [y (* 1.3 (+ v (* 4.0 (wiggle 8 y))))]))
        dated {:from 2002 :values (mh/dated-inputs rows {"AAA" {:credit (ratio rows :tloans) :mortgages hh}} 2002)}
        plain (by-key (mh/build rows nil nil (jst-gaps rows)))
        spliced (by-key (mh/build rows nil nil (jst-gaps rows) dated))]
    (is (close? (get-in plain [["AAA" 2003] :specific :mortgages]) (get-in spliced [["AAA" 2003] :specific :mortgages])))
    (is (not (close? (get-in plain [["AAA" 2006] :specific :mortgages]) (get-in spliced [["AAA" 2006] :specific :mortgages]))))))

(deftest off-is-the-base
  (let [rows (jst-rows "AAA") gaps (jst-gaps rows)]
    (is (= (mh/build rows nil nil gaps) (mh/build rows nil nil gaps nil)))))

(deftest the-bis-year-is-its-fourth-quarter
  ;; each year's value is its December quarter; a year without one (the
  ;; latest, part published) has none
  (let [s (sk/series (concat (for [y [2000 2001] q [2 5 8 11] :let [p (+ (* 12 y) q)]] [p (+ p 6) (+ y (/ q 100.0))])
                             [[(+ (* 12 2002) 2) (+ (* 12 2002) 8) 9.0]]))]
    (is (= {2000 2000.11 2001 2001.11} (lb/q4-annual s)))))

(deftest a-value-is-read-only-once-published
  ;; year y's value, at the end of December y plus its lag: the BIS's
  ;; fourth quarter in its June release (lag 6, ALFRED's vintages of its
  ;; credit series 2016-26), JST's rows by its sources' lags (12)
  (is (= 2007 (lb/read-year (+ (* 12 2008) 11) lb/jst-lag)))
  (is (= 2007 (lb/read-year (+ (* 12 2008) 11) lb/bis-lag)))
  (is (= 2007 (lb/read-year (+ (* 12 2008) 5) lb/bis-lag)) "the June release")
  (is (= 2006 (lb/read-year (+ (* 12 2008) 4) lb/bis-lag)) "not in May")
  (is (= 2006 (lb/read-year (+ (* 12 2008) 10) lb/jst-lag)) "JST's row of 2007 not before December 2008")
  (testing "the year node reads the latest row published at its origin"
    (let [jst {["AAA" 2006] {:specific {:credit 1.0}} ["AAA" 2007] {:specific {:credit 2.0}}}
          cat {:onsets {} :from {} :through {}}
          slot (fn [t] (first (#'lb/year-raw jst {} cat "AAA" t {:dated-credit {:from 2002 :values {}}})))]
      (is (= 2.0 (slot (+ (* 12 2008) 11))))
      (is (= 1.0 (slot (+ (* 12 2008) 10)))))))

(deftest past-the-macrohistory-panel-the-bis-inputs-alone
  ;; JST ends in 2020; from the December 2022 origin the year node has the
  ;; BIS's credit inputs for its countries and nothing of JST's
  (let [values {["AAA" 2022] {:specific {:credit 1.5 :mortgages -0.5} :growth {:credit 0.02}
                              :long {:credit 0.7 :mortgages 0.1} :gap {:credit-gap-z 1.1}}}
        cat {:onsets {} :from {} :through {}}
        opts {:dated-credit {:from 2002 :values values}}
        yr (#'lb/year-raw {} {} cat "AAA" (+ (* 12 2023) 11) opts)
        half (#'lb/half-raw {} cat "AAA" (+ (* 12 2023) 11) opts)
        a (count lb/year-specific) b (count lb/year-invariants) c (count lb/year-process) d (count lb/year-stabilizers)]
    (is (= [1.5 -0.5] (subvec yr 0 2)))
    (is (= 0.02 (nth yr (+ a b c d 3))) "credit growth")
    (is (nil? (nth yr 2)) "house prices stay JST's: none")
    (is (= [0.7 0.1 nil 1.1] (subvec half 0 4)))
    (is (nil? (first (#'lb/year-raw {} {} cat "AAA" (+ (* 12 2023) 11) {}))) "off: nothing")))

(deftest household-credit-from-the-splice
  (let [jst (into {} (for [y (range 1980 2010)] [y (+ 40.0 (* 3.0 (wiggle 1 y)))]))
        bis (into {} (for [y (range 1999 2010)] [y (+ 60.0 y -1999)]))]
    (is (= (#'lb/stretch-at jst 2001) (lb/dated-stretch jst bis 2002 2001)) "before it, JST's")
    (is (= (#'lb/stretch-at jst 2003) (lb/dated-stretch jst bis 2002 2003)) "BIS too short: JST's")
    (is (= (#'lb/stretch-at bis 2006) (lb/dated-stretch jst bis 2002 2006)))
    (is (= (#'lb/stretch-at jst 2006) (lb/dated-stretch jst nil 2002 2006)) "no BIS series: JST's")))

(deftest dated-credit-option
  (is (false? (wr/dated-credit-choice {})))
  (is (false? (wr/dated-credit-choice {:dated-credit "false"})))
  (is (true? (wr/dated-credit-choice {:dated-credit "true"})))
  (is (thrown? Exception (wr/dated-credit-choice {:dated-credit "yes"}))))
