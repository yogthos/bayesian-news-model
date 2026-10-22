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

(deftest one-rank-per-year-on-hs92
  (let [hs12 (map #(assoc % :hs "HS12") (grower "850760" 1e-4 1e5 0.9 0.9 (range 2012 2025)))
        r (ess/rise (into panel hs12) 2020)
        li (first (filter #(= "850760" (:hs6 %)) r))
        hs92 (filter #(= "HS92" (:hs %)) r)]
    (testing "the rank is HS92's alone"
      (is (nil? (:rank li)) "a later edition's code has no rank of its own")
      (is (= 1 (:rank (first (filter #(= "000005" (:hs6 %)) hs92)))) "HS92's own fastest")
      (is (= (count (filter :rank r)) (count (filter :rank hs92))))
      (is (every? #(= (count (filter :rank hs92)) (:n %)) (filter :rank r))))
    (testing "later editions carry a detail rank within their own edition"
      (is (= 1 (:detail-rank li)))
      (is (= 1 (:detail-n li)))
      (is (every? #(nil? (:detail-rank %)) hs92)))
    (testing "another edition can be chosen as the ranked one"
      (is (= 1 (:rank (first (filter #(= "850760" (:hs6 %)) (ess/rise (into panel hs12) 2020 {:rank-edition "HS12"})))))))))

;; ---------------------------------------------------------------- tonnes gaps

(defn- gappy
  "`grower` rows with the tonnes of the years in `gaps` blanked (BACI has
  no quantity for them), and the years in `covers` ({year cover}) carrying
  :tonnes-cover with tonnes counted only for that share of the value."
  [hs6 gs gt gaps covers]
  (for [r (grower hs6 1e-4 1e5 gs gt years)]
    (cond-> r
      (contains? gaps (:year r)) (assoc :tonnes nil)
      (contains? covers (:year r)) (-> (assoc :tonnes-cover (covers (:year r)))
                                       (update :tonnes * (covers (:year r)))))))

(deftest missing-tonnes-are-unknown-not-zero
  (let [p (vec (concat panel
                       (gappy "800001" 0.20 0.20 #{2009 2010} {})
                       (gappy "800002" 0.30 0.05 #{2003 2004} {})
                       (gappy "800003" 0.20 0.20 #{2004 2006 2008} {})
                       (gappy "800004" 0.30 0.05 #{} {2003 0.4 2004 0.3 2005 0.45})
                       (gappy "800006" 0.30 0.05 #{} {2003 0.6 2004 0.7 2005 0.8})))
        by (into {} (map (juxt :hs6 identity)) (ess/rise p 2010))]
    (testing "two blank years at the end of the window are not a collapse"
      (is (close? 0.20 (:tonnes-slope (by "800001")) 1e-6))
      (is (:rank (by "800001")))
      (is (not (:tonnes-gap (by "800001")))))
    (testing "two blank years at the start are not a rise"
      (is (close? 0.05 (:score (by "800002")) 1e-6)))
    (testing "fewer than six years of tonnes: no tonnes trend, not ranked, flagged"
      (is (:tonnes-gap (by "800003")))
      (is (nil? (:tonnes-slope (by "800003"))))
      (is (nil? (:rank (by "800003")))))
    (testing "partly covered tonnes are scaled up to the year's value"
      (is (close? 0.05 (:tonnes-slope (by "800006")) 1e-6) "an undercount early is not a rise")
      (is (not (:tonnes-gap (by "800006")))))
    (testing "a year whose tonnes cover under half its value is a gap"
      (is (:tonnes-gap (by "800004")))
      (is (nil? (:rank (by "800004")))))
    (testing "a year without a row is still no trade, known zero tonnes"
      (let [late (vec (concat panel (grower "800005" 1e-4 1e5 0.1 0.1 (range 2006 2025))))
            e (first (filter #(= "800005" (:hs6 %)) (ess/rise late 2010)))]
        (is (not (:tonnes-gap e)))
        (is (:rank e))))))

;; ---------------------------------------------------------------- held-out precision

(deftest precision-against-labels
  (let [entries (for [[i c] (map-indexed vector "abcdefghij")]
                  {:hs6 (str c) :rank (inc i) :eligible true})
        unranked {:hs6 "z" :rank nil :eligible false}
        r (ess/precision (conj (vec entries) unranked) #{"a" "c" "z" "q"} 3)]
    (is (= 3 (:k r)))
    (is (= 2 (:hits r)))
    (is (close? (/ 2.0 3) (:precision r) 1e-12))
    (is (= 10 (:n r)) "only ranked products are drawn from")
    (is (= 2 (:labeled r)) "labels outside the ranked set do not count")
    (is (close? 0.2 (:base r) 1e-12) "a random k's expected precision")
    (is (close? (/ 8.0 120) (:p r) 1e-12) "hypergeometric P(X >= 2), N 10, K 2, k 3")
    (is (close? 1.0 (:p (ess/precision entries #{"j"} 3)) 1e-12) "no hits: p is 1")))

;; ---------------------------------------------------------------- edge proposal

(deftest overlap-of-shares
  (let [d (fn [& xs] (double-array xs))]
    (is (close? 1.0 (ess/overlap (d 1 2 0) (d 2 4 0)) 1e-12) "the same shape, any scale")
    (is (close? 0.0 (ess/overlap (d 1 0 0) (d 0 0 5)) 1e-12) "disjoint")
    (is (close? 0.5 (ess/overlap (d 1 1 0) (d 0 1 1)) 1e-12))
    (is (close? 0.0 (ess/overlap (d 0 0 0) (d 1 1 1)) 1e-12) "empty is no overlap")))

(deftest weighted-correlation
  (let [d (fn [& xs] (double-array xs))]
    (is (close? 1.0 (ess/weighted-corr (d 1 2 3) (d 2 4 6) (d 1 1 1)) 1e-12))
    (is (close? -1.0 (ess/weighted-corr (d 1 2 3) (d 3 2 1) (d 1 1 1)) 1e-12))
    (is (close? 1.0 (ess/weighted-corr (d 1 2 3 9) (d 2 4 6 -50) (d 1 1 1 0)) 1e-12)
        "a zero weight drops the point")
    (is (nil? (ess/weighted-corr (d 1 1 1) (d 1 2 3) (d 1 1 1))) "no variance: undefined")))

(def countries (mapv #(str "C" %) (range 20)))

(defn- wave [i k] (+ 1.2 (Math/sin (+ (* 1.7 i) (* 0.9 k)))))

(defn- flows
  "Country rows of product `hs6` for `years`: exports and imports of
  country index c in year y from (ex c y) and (im c y)."
  [hs6 years ex im]
  (for [y years [c name] (map-indexed vector countries)
        :let [e (double (ex c y)) m (double (im c y))]
        :when (or (pos? e) (pos? m))]
    {:hs6 hs6 :year y :country name :exports e :imports m}))

(def edge-years (range 1995 2015))

(defn- ramp [y] (Math/exp (* 0.35 (- y 1995))))

(def edge-world
  (vec (concat
        (map #(assoc % :value (* 1e9 (:share %))) (grower "282520" 1e-4 1e5 0.30 0.30 edge-years))
        (map #(assoc % :value (* 1e9 (:share %))) (grower "850780" 1e-3 1e5 0.20 0.20 edge-years))
        (map #(assoc % :value (* 1e9 (:share %))) (grower "720310" 1e-3 1e5 0.25 0.25 edge-years))
        (mapcat (fn [i] (map #(assoc % :value (* 1e9 (:share %)))
                             (grower (format "%04d00" (+ 3000 i)) (* 1e-4 (+ 0.5 (* 0.02 i))) 1e5
                                     (* 0.001 i) (* 0.001 i) edge-years)))
                (range 60)))))

(def edge-countries
  (vec (concat
        ;; the input: imported by C0-C2 at a growing rate, C10-C12 flat
        (flows "282520" edge-years (constantly 0.0)
               (fn [c y] (cond (< c 3) (* 10 (ramp y)) (<= 10 c 12) 50.0 :else 0.0)))
        ;; the downstream: exported by C0-C2, growing
        (flows "850780" edge-years
               (fn [c y] (if (< c 3) (* 100 (ramp y)) 1.0)) (fn [c _] (wave c 1)))
        ;; a growing product made elsewhere (C15-C19)
        (flows "720310" edge-years
               (fn [c y] (if (>= c 15) (* 100 (ramp y)) 0.0)) (fn [c _] (wave c 2)))
        ;; similar-sized products traded flat everywhere
        (mapcat (fn [i] (flows (format "%04d00" (+ 3000 i)) edge-years
                               (fn [c _] (* 40 (wave c i))) (fn [c _] (* 40 (wave c (+ i 7))))))
                (range 60)))))

(deftest placebo-pool-is-like-sized-and-unrelated
  (let [pool (ess/placebo-pool edge-world "282520" 2010 10)
        v (fn [code] (:value (first (filter #(and (= code (:hs6 %)) (= 2010 (:year %))) edge-world))))]
    (is (= 10 (count pool)))
    (is (not (contains? (set pool) "282520")))
    (is (every? #(not= "2825" (subs % 0 4)) pool) "not the same heading")
    (let [d (fn [c] (Math/abs (Math/log (/ (v c) (v "282520")))))
          others (remove (set (conj pool "282520")) (distinct (map :hs6 edge-world)))]
      (is (<= (apply max (map d pool)) (apply min (map d others))) "the nearest in size"))))

(deftest proposes-the-downstream-its-importers-make
  (let [panel (ess/country-panel edge-countries)
        opts {:candidates #{"282520"} :placebos 39}
        es (ess/edges edge-world panel 2010 opts)
        by (into {} (map (juxt (juxt :input :output) identity)) es)
        hit (by ["282520" "850780"])
        decoy (by ["282520" "720310"])]
    (is hit "the downstream is in the growing set")
    (is (= 1 (:rank hit)) "the top proposal")
    (is (:proposed hit))
    (is (<= (:p hit) 0.05) "beats the like-sized placebos")
    (is (pos? (:overlap-growth hit)))
    (is (> (:comove hit) 0.5))
    (is (= 2010 (:year hit)))
    (is (= 2012 (:known hit)))
    (is decoy "the other grower is scored")
    (is (not (:proposed decoy)) "importers that do not make it: no edge")
    (is (every? #(not= "282520" (:output %)) es) "no self edge")
    (testing "candidates default to the rise top share"
      (is (= #{"282520"} (set (map :input (ess/edges edge-world panel 2010 {:cand-pct 0.02 :placebos 39}))))))))

(deftest edges-read-nothing-after-the-data-year
  (let [y 2008
        bad (fn [r] (> (:year r) y))
        world2 (corrupt edge-world bad)
        cty2 (mapv (fn [r] (if (bad r) (assoc r :imports (* 1e6 (wave 3 (:year r))) :exports 7.0) r))
                   edge-countries)
        opts {:candidates #{"282520"} :placebos 20}
        key (fn [es] (into {} (map (juxt (juxt :input :output) (juxt :z :p :rank))) es))
        a (key (ess/edges edge-world (ess/country-panel edge-countries) y opts))]
    (is (seq a))
    (is (= a (key (ess/edges world2 (ess/country-panel cty2) y opts))))
    (testing "a corrupted present does change them"
      (let [cty3 (mapv (fn [r] (if (= y (:year r)) (assoc r :imports (* 1e6 (wave 3 (:year r)))) r))
                       edge-countries)]
        (is (not= a (key (ess/edges edge-world (ess/country-panel cty3) y opts))))))))

(deftest known-edges-from-recipes-and-parents
  (let [recipes [{:region "A" :output "C_ELMA" :input "C_CHEM" :share 0.02}
                 {:region "B" :output "C_ELMA" :input "C_CHEM" :share 0.01}
                 {:region "C" :output "C_ELMA" :input "C_CHEM" :share 0.0}
                 {:region "A" :output "C_MOTO" :input "C_CHEM" :share 0.001}
                 {:region "B" :output "C_MOTO" :input "C_CHEM" :share 0.002}]
        links (ess/recipe-links recipes 0.005)
        parents {"282520" #{"C_CHEM"} "850780" #{"C_ELMA"} "870390" #{"C_MOTO"}}]
    (is (= #{["C_CHEM" "C_ELMA"]} links) "the median share over regions, at or above the floor")
    (is (ess/known-edge? links parents "282520" "850780"))
    (is (not (ess/known-edge? links parents "282520" "870390")))
    (is (not (ess/known-edge? links parents "999999" "850780")) "unmapped: not known")))

(deftest edge-precision-against-known
  (let [es [{:input "a" :output "x" :proposed true :rank 1}
            {:input "a" :output "y" :proposed true :rank 2}
            {:input "a" :output "z" :proposed false :rank 3}
            {:input "b" :output "x" :proposed false :rank 1}]
        known? (fn [a b] (contains? #{["a" "x"] ["b" "x"] ["a" "z"]} [a b]))
        r (ess/edge-precision es known?)]
    (is (= 2 (:proposed r)))
    (is (= 1 (:hits r)))
    (is (close? 0.5 (:precision r) 1e-12))
    (is (= 4 (:pairs r)))
    (is (= 3 (:known r)))
    (is (close? 0.75 (:base r) 1e-12))
    (is (close? (:p r) (ess/hypergeometric-tail 4 3 2 1) 1e-12))
    (is (= 2 (:top-n r)) "the top proposal of each input")
    (is (close? 1.0 (:top-precision r) 1e-12))))

;; ---------------------------------------------------------------- review of the edge proposal (bnm-1pq.8)

(deftest growing-set-ignores-a-tonnes-only-revision-step
  ;; 850780 in HS92: value share smooth through 2007, tonnes x6.6 at the
  ;; 2007 revision. The downstream set ranks value share alone, so a
  ;; tonnes step must not drop it (it still drops it from the rise rank).
  (let [step (for [y edge-years]
               (row "841000" y (* 1e-4 (Math/exp (* 0.40 (- y 1995))))
                    (* 1e5 (if (>= y 2007) 8.0 1.0))))
        world (into edge-world (map #(assoc % :value (* 1e9 (:share %)))) step)
        r (first (filter #(= "841000" (:hs6 %)) (ess/rise world 2010)))
        plan (ess/edge-plan world 2010 {:candidates #{"282520"}})]
    (is (:revision-step r) "the tonnes step still keeps it out of the rise rank")
    (is (not (:share-step r)) "no step in value share")
    (is (some #{"841000"} (:growing plan)) "in the downstream set")))

(deftest placebo-pool-can-exclude-the-chapter
  (let [world (into edge-world
                    (for [i (range 5) y edge-years]
                      {:hs "HS92" :hs6 (format "28%02d10" (+ 30 i)) :year y :known (+ y 2)
                       :share 1e-4 :tonnes 1e5 :value (* 9e6 (+ 1.0 (* 0.01 i)))}))
        h4 (ess/placebo-pool world "282520" 2010 10)
        h2 (ess/placebo-pool world "282520" 2010 10 {:placebo-exclude :hs2})]
    (is (some #(= "28" (subs % 0 2)) h4) "by default other chapter-28 headings stay in")
    (is (= 10 (count h2)))
    (is (every? #(not= "28" (subs % 0 2)) h2) "with :hs2 none of chapter 28")))

(deftest country-totals-are-over-every-row
  (let [rows [{:hs6 "a" :year 2000 :country "X" :exports 1.0 :imports 2.0}
              {:hs6 "b" :year 2000 :country "X" :exports 3.0 :imports 0.0}
              {:hs6 "b" :year 2000 :country "Y" :exports 0.0 :imports 5.0}]
        p (ess/country-panel rows 4 #(= "a" (:hs6 %)))
        ^doubles t (get-in p [:totals 2000])
        x ((:index p) "X") y ((:index p) "Y")]
    (is (= #{["a" 2000]} (set (keys (:flows p)))) "flows only of the kept rows")
    (is (= 4.0 (aget t x)) "exports over every product")
    (is (= 2.0 (aget t (+ 4 x))))
    (is (= 5.0 (aget t (+ 4 y))) "a country seen only in rows not kept still has totals")))

(deftest rca-overlap-ignores-country-size
  ;; Multiply every flow of C5 (all products, so its totals too) by 50: the
  ;; value overlap moves, the specialisation overlap does not.
  (let [big (mapv (fn [r] (if (= "C5" (:country r))
                            (-> r (update :exports * 50.0) (update :imports * 50.0))
                            r))
                  edge-countries)
        opts {:candidates #{"282520"} :placebos 20}
        key (fn [es] (into {} (map (juxt (juxt :input :output) (juxt :overlap :overlap-early))) es))
        run (fn [rows o] (key (ess/edges edge-world (ess/country-panel rows) 2010 (merge opts o))))
        rca {:weight :rca :rca-floor 0.0}
        a (run edge-countries rca) b (run big rca)]
    (is (seq a))
    (is (every? (fn [[k [o oe]]] (let [[o2 oe2] (b k)] (and (close? o o2 1e-9) (close? oe oe2 1e-9)))) a))
    (is (not= (run edge-countries {}) (run big {})) "by value, the big country moves it")
    (testing "the planted edge is still found by specialisation"
      (let [e (first (filter #(= "850780" (:output %))
                             (ess/edges edge-world (ess/country-panel edge-countries) 2010 (merge opts rca))))]
        (is (= 1 (:rank e)))))))

(deftest subnode-links-from-names
  (let [subnodes [{:subnode "lithium_carbonate" :edition "HS92" :code "283691"}
                  {:subnode "li_ion_batteries" :edition "HS92" :code "850780"}
                  {:subnode "li_ion_batteries" :edition "HS12" :code "850760"}
                  {:subnode "evs" :edition "HS92" :code "870390"}]
        links (ess/subnode-links subnodes "HS92" [["lithium_carbonate" "li_ion_batteries"]
                                                  ["li_ion_batteries" "evs"]])]
    (is (= #{["283691" "850780"] ["850780" "870390"]} links))
    (is (= #{} (ess/subnode-links subnodes "HS12" [["lithium_carbonate" "li_ion_batteries"]]))
        "a name without a code in the edition gives no link")))
