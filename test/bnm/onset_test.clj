(ns bnm.onset-test
  (:require [bnm.math :as m]
            [bnm.onset :as o]
            [clojure.test :refer [deftest is testing]]))

(def onsets (sorted-set 100))

(deftest labels-count-onsets-in-the-window
  (is (o/label onsets 99 [1 4]))
  (is (o/label onsets 96 [1 4]))
  (is (not (o/label onsets 95 [1 4])))
  (is (not (o/label onsets 99 [2 4])) "the onset falls before the lead window"))

(deftest a-row-is-at-risk-only-before-any-onset-it-could-not-see-coming
  (testing "not while an onset is recent"
    (is (not (o/at-risk? onsets 100 [1 4])))
    (is (not (o/at-risk? onsets 103 [1 4])))
    (is (o/at-risk? onsets 104 [1 4])))
  (testing "not when the onset falls between the origin and the window"
    (is (not (o/at-risk? onsets 99 [2 4])) "onset next quarter, window from two")
    (is (o/at-risk? onsets 98 [2 4]))
    (is (not (o/at-risk? onsets 96 [5 8])))
    (is (not (o/at-risk? onsets 99 [5 8])))
    (is (o/at-risk? onsets 95 [5 8]))
    (is (o/at-risk? onsets 99 [1 4]) "w1 has no gap")))

(deftest hard-rows-have-no-onset-in-ten-years
  (is (not (o/hard-row? onsets 139)))
  (is (o/hard-row? onsets 141))
  (is (o/hard-row? onsets 99)))

(deftest the-last-quarter-counted-is-the-last-complete-one
  ;; months as 12y + m - 1
  (let [month (fn [y m] (+ (* 12 y) (dec m)))]
    (is (= (+ (* 4 2026) 2) (o/last-complete-quarter (month 2026 9))) "September closes Q3")
    (is (= (+ (* 4 2026) 2) (o/last-complete-quarter (month 2026 10))) "October leaves Q4 open")
    (is (= (+ (* 4 2025) 3) (o/last-complete-quarter (month 2026 1))))))

(deftest groups-with-gaps-get-a-presence-column
  (let [rows [{:unit "A" :q 1} {:unit "B" :q 1}]
        [w xs] (o/with-presence 2 {["A" 1] (double-array [3.0 4.0])} rows)]
    (is (= 3 w))
    (is (= [3.0 4.0 1.0] (vec (xs ["A" 1]))))
    (is (nil? (xs ["B" 1])) "missing rows are filled with zeros downstream, presence 0"))
  (testing "a group covering every row is left alone"
    (let [xs {["A" 1] (double-array [3.0])}
          [w xs'] (o/with-presence 1 xs [{:unit "A" :q 1}])]
      (is (= 1 w))
      (is (identical? xs xs')))))

(deftest priors-are-chosen-each-year-on-what-was-known
  ;; run a is right in 2000, run b in 2001 and 2002; the window closes 4 quarters on
  (let [mk (fn [ps] (vec (for [[yr p y] ps] [(* 4 yr) p y "A"])))
        a {:prior 1.0 :preds (mk [[2000 0.9 true] [2001 0.1 true] [2002 0.1 true] [2003 0.5 true]])}
        b {:prior 2.0 :preds (mk [[2000 0.1 true] [2001 0.95 true] [2002 0.9 true] [2003 0.6 true]])}
        score (fn [ps] (when (seq ps) (/ (reduce + (map (fn [[_ p y]] (Math/log (if y p (- 1 p)))) ps)) (count ps))))
        {:keys [prior preds]} (o/choose-by-year [a b] score [1 4] a)]
    (is (= [0.9 0.1 0.9 0.6] (mapv second preds))
        "2000 takes the default; 2001 knows 2000, where a wins; 2002 and 2003 know 2001 too, where b wins more")
    (is (= 2.0 prior) "the choice of the last year")))

(deftest histories-of-every-kind
  (let [events {"coup" {"A" (sorted-set 90 100)} "protest_surge" {"A" (sorted-set 98)}}
        x (o/histories events ["coup" "protest_surge" "war_onset"] "A" 100)]
    (is (= 6 (alength ^doubles x)))
    (is (< (Math/abs (- (Math/log 3.0) (aget ^doubles x 0))) 1e-9) "two coups up to q")
    (is (< (Math/abs (- 1.0 (aget ^doubles x 1))) 1e-9) "the last this quarter")
    (is (< (Math/abs (- (Math/exp -0.1) (aget ^doubles x 3))) 1e-9) "a protest surge two quarters back")
    (is (= [0.0 0.0] (vec (drop 4 x))) "no war")
    (is (= [0.0 0.0] (vec (take 2 (o/histories events ["coup"] "A" 89)))) "nothing from after q")))

(deftest events-carry-their-span-and-when-they-were-known
  ;; a war known only to the year 2000 (quarters 8000-8003), published mid-2001
  (let [{:keys [onsets spans known]} (o/event-sets [["war_onset" "A" 8000 8003 8005] ["coup" "A" 8010 8010 8010]])]
    (is (= #{8000} (set (get-in onsets ["war_onset" "A"]))))
    (is (= #{8000 8001 8002 8003} (set (get-in spans ["war_onset" "A"]))))
    (is (= #{8005} (set (get-in known ["war_onset" "A"]))))
    (is (= #{8010} (set (get-in known ["coup" "A"]))))
    (testing "a row a year after the earliest date, but within a year of the latest, is not at risk"
      (is (o/at-risk? (get-in onsets ["war_onset" "A"]) 8004 [1 4]))
      (is (not (o/at-risk? (get-in spans ["war_onset" "A"]) 8004 [1 4]))))
    (testing "another kind's history sees the war only once published"
      (is (= [0.0 0.0] (vec (o/histories known ["war_onset"] "A" 8003))))
      (is (pos? (aget (o/histories known ["war_onset"] "A" 8005) 0))))))

(deftest labels-are-final-only-once-their-onsets-are-published
  ;; a kind's reporting lag is the longest it took any onset to be known;
  ;; a row's label is final when its window has closed and that lag passed
  (let [{:keys [lags]} (o/event-sets [["war_onset" "A" 8000 8003 8005] ["war_onset" "B" 8100 8103 8104]
                                      ["coup" "A" 8010 8010 8010]])]
    (is (= {"war_onset" 5 "coup" 0} lags))
    (is (= 8009 (o/resolves-at 8000 [1 4] 5)) "window to 8004, then five quarters to publish")
    (is (= 8004 (o/resolves-at 8000 [1 4] 0)))))

(deftest onsets-among-neighbours
  ;; peers: {level {unit [other units]}}; each kind and level gives the
  ;; decayed count of the peers' onsets known by q, log1p
  (let [events {"coup" {"MLI" (sorted-set 100) "BFA" (sorted-set 104) "NER" (sorted-set 120)}}
        peers {:sub {"NER" ["MLI" "BFA"] "MLI" ["BFA" "NER"]} :reg {"NER" ["MLI" "BFA" "FRA"]}}
        x (vec (o/neighbourhood events ["coup"] peers "NER" 104))]
    (is (= 2 (count x)) "one per kind and level")
    (is (< (Math/abs (- (first x) (Math/log1p (+ (Math/exp -1.0) 1.0)))) 1e-12)
        "MLI four quarters back decays to e^-1, BFA this quarter counts 1")
    (is (= (first x) (second x)) "the region sees the same onsets")
    (is (zero? (first (o/neighbourhood events ["coup"] peers "NER" 99))) "nothing known yet")
    (is (< (Math/abs (- (second (o/neighbourhood events ["coup"] peers "MLI" 120)) (Math/log1p 1.0))) 1e-12)
        "NER's this quarter counts; BFA's, four years back, is past the window; MLI's own never counts")))

(deftest at-risk-flags
  ;; inputs past column `keep` become two flags: in the top or bottom tail of
  ;; the training rows (Keilis-Borok; Billakanti & Shin 2026)
  (let [xs (for [i (range 100)] (double-array [1.0 0.5 (double i) (if (< i 10) 1.0 0.0)]))
        cuts (o/tail-cuts xs 2 0.2)]
    (is (= 2 (count cuts)) "a pair of cuts per column past the kept ones")
    (is (= [19.0 80.0] (mapv double (first cuts))) "the 20th and 80th percentiles")
    (is (= [1.0 0.5 0.0 1.0 0.0 0.0] (vec (o/tail-flags (double-array [1.0 0.5 5.0 0.0]) 2 cuts))))
    (is (= [1.0 0.5 1.0 0.0 0.0 0.0] (vec (o/tail-flags (double-array [1.0 0.5 95.0 0.0]) 2 cuts))))
    (testing "a rare flag stays a flag: its top tail is its 1s"
      (is (= [1.0 0.0] (subvec (vec (o/tail-flags (double-array [1.0 0.5 50.0 1.0]) 2 cuts)) 4))))))

(deftest groups-no-model-uses
  (is (= ["leaders" "neighbours"] (o/unused-groups ["history" "named"] ["leaders" "neighbours"])))
  (is (empty? (o/unused-groups ["history" "named+leaders-placebo" "gbt-neighbours"] ["leaders" "neighbours"])))
  (is (empty? (o/unused-groups ["at-named+mix"] ["mix"])))
  (is (empty? (o/unused-groups ["knn-named+mix" "hs-named+ties"] ["mix" "ties"])))
  (is (= ["mix"] (o/unused-groups ["mixed"] ["mix"])) "whole names only"))

(deftest forecasts-for-groups-of-units
  (let [rows {["MLI" 10] [0.1 false] ["NER" 10] [0.2 true] ["FRA" 10] [0.5 false] ["MLI" 11] [0.1 false]}
        of {"MLI" "s/west" "NER" "s/west" "FRA" "s/europe"}
        out (o/aggregate rows of)]
    (is (< (Math/abs (- 0.28 (first (out ["s/west" 10])))) 1e-12) "any of them: 1 - 0.9 x 0.8")
    (is (true? (second (out ["s/west" 10]))))
    (is (= [0.5 false] (out ["s/europe" 10])))
    (is (= [0.1 false] (out ["s/west" 11])))
    (is (= 4 (count (o/aggregate (assoc rows ["XXX" 10] [0.3 false]) of))) "units without a group are their own")))

(deftest wave-kernels
  ;; onsets of any kind among the unit's groups (and the world), each decayed
  ;; at several rates; a fit with a fast positive and a slow negative weight
  ;; is excitation followed by a refractory period
  (let [events {"coup" {"MLI" (sorted-set 100) "BFA" (sorted-set 104)} "protest_surge" {"NER" (sorted-set 96) "FRA" (sorted-set 104)}}
        groups {:subregion {"MLI" "w" "BFA" "w" "NER" "w" "FRA" "e"}}
        waves (o/wave-index events ["coup" "protest_surge"] groups)
        x (vec (o/waves waves groups "NER" 104 [1.0 4.0]))]
    (is (= 4 (count x)) "subregion and world, each at two rates")
    (is (< (Math/abs (- (nth x 1) (Math/log1p (+ 1.0 (Math/exp -1.0))))) 1e-12)
        "subregion at four quarters: BFA now, MLI a year back; NER's own never counts")
    (is (< (Math/abs (- (nth x 3) (Math/log1p (+ 2.0 (Math/exp -1.0))))) 1e-12) "the world adds FRA")
    (is (< (Math/abs (- (nth x 0) (Math/log1p 1.0))) 1e-12) "fast decay forgets MLI, past three taus")
    (is (zero? (first (o/waves waves groups "NER" 99 [1.0 4.0]))) "nothing yet but its own")))

(deftest regional-dispersion
  ;; onsets drawn independently at the forecast rates disperse like their
  ;; Poisson-binomial variance; onsets that come together in a group do not
  (let [g (m/rng 11)
        units (for [r (range 20) c (range 10)] [(str "r" r "c" c) (str "r" r)])
        of (into {} units)
        p 0.05
        indep (into {} (for [q (range 200) [u _] units] [[u q] [p (< (m/next-double! g) p)]]))
        waves (into {} (for [q (range 200) r (range 20)
                             :let [hit (< (m/next-double! g) p)]
                             c (range 10)]
                         [[(str "r" r "c" c) q] [p hit]]))]
    (is (< (Math/abs (- 1.0 (:ratio (o/dispersion indep of)))) 0.1))
    (is (> (:ratio (o/dispersion waves of)) 5.0) "a whole group at once")
    (is (= 4000 (:groups (o/dispersion indep of))) "group-quarters")))

(deftest regional-frailty
  ;; a region whose onsets run above its forecasts lifts its members'
  ;; hazards, from outcomes resolved by each row's quarter only
  (let [g (m/rng 13)
        units (for [r (range 10) c (range 8)] [(str "r" r "c" c) (str "r" r)])
        of (into {} units)
        hot? (fn [r q] (and (< r 3) (< 40 q 120)))
        preds (vec (for [q (range 160) [u r] units
                         :let [rate (if (hot? (parse-long (subs r 1)) q) 0.15 0.03)]]
                     [q 0.03 (< (m/next-double! g) rate) u]))
        adj (o/frailty preds of [1 4] {:discount 0.9 :prior 2.0})
        ls (fn [ps] (reduce + (for [[_ p y] ps] (Math/log (if y p (- 1.0 p))))))]
    (is (= (count preds) (count adj)))
    (is (> (ls adj) (ls preds)) "better where regions run hot")
    (let [at (fn [u q] (second (first (filter #(and (= u (nth % 3)) (= q (first %))) adj))))]
      (is (> (at "r0c0" 100) 0.06) "a hot region's member lifted")
      (is (< (Math/abs (- (at "r9c0" 100) 0.03)) 0.015) "a calm region's left near its forecast")
      (is (= 0.03 (at "r0c0" 3)) "nothing resolved yet: unchanged"))))

(deftest eras-split-by-when-labels-resolve
  ;; decision eras score only rows whose labels resolve before the lock; the
  ;; rest belong to the confirmation period, whatever year they forecast from
  (let [lock 2023 q22 (+ (* 4 2022) 3)]
    (is (o/in-era? (* 4 2020) [2 4] 0 [2015 2022] lock false))
    (is (not (o/in-era? q22 [2 4] 0 [2015 2022] lock false)) "late 2022 resolves in 2023")
    (is (o/in-era? q22 [2 4] 0 [2023 2026] lock true) "so it is confirmation")
    (is (not (o/in-era? (- q22 4) [2 4] 5 [2015 2022] lock false)) "a lag pushes it past the lock too")
    (is (not (o/in-era? (* 4 2010) [2 4] 0 [2023 2026] lock true)))))
