(ns bnm.econ-factors-test
  (:require [bnm.econ-factors :as ef]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [tol a b] (and a b (< (Math/abs (- (double a) (double b))) tol)))

;; A deterministic one-factor world: country c has factor f_c (a level plus a
;; slow drift); indicator j reads mu_j + l_j f + noise. "b" loads negatively.
(def truth {"a" 1.0 "b" -0.8 "c" 0.6 "d" 0.9})
(def countries (mapv #(str "C" %) (range 40)))

(defn- noise [i j y]
  ;; a fixed pseudo-random value in [-0.5, 0.5)
  (let [h (Math/abs (long (hash [i j y])))]
    (- (/ (double (mod h 10007)) 10007.0) 0.5)))

(defn- level [i] (- (* 0.1 i) 2.0))

(def rows
  (vec (for [[i c] (map-indexed vector countries)
             y (range 1990 2011)
             [j l] truth
             :let [f (+ (level i) (* 0.01 (- y 2000)))]]
         {:iso c :year y :known (+ y 2) :indicator j
          :value (+ 10.0 (* l f) (* 0.4 (noise i j y)))})))

(defn- corr [xs ys]
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sxx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
        syy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
    (/ sxy (Math/sqrt (* sxx syy)))))

(deftest fit-recovers-one-factor
  (let [fit (ef/fit-rows rows {:anchors ["a"]})
        l (:loading fit)]
    (testing "loadings carry the true pattern and signs"
      (is (pos? (l "a")))
      (is (neg? (l "b")))
      (is (= (map #(Math/signum (double %)) (vals truth))
             (map #(Math/signum (double (l %))) (keys truth)))))
    (testing "standardised loadings are near 1 when noise is small"
      (is (every? #(< 0.85 (Math/abs (double %)) 1.01) (vals l))))
    (testing "scores order the countries by their true level"
      (let [x (fn [i] (into {} (for [r rows :when (and (= (:iso r) (countries i)) (= 2000 (:year r)))]
                                [(:indicator r) (:value r)])))
            s (mapv #(:f (ef/score fit (x %))) (range 40))]
        (is (> (corr s (map level (range 40))) 0.98))))))

(deftest missing-data
  (let [;; drop "c" and "d" for half the countries, and "a" for some country-years
        holes (vec (remove (fn [{:keys [iso indicator year]}]
                             (let [i (parse-long (subs iso 1))]
                               (or (and (even? i) (#{"c" "d"} indicator))
                                   (and (= indicator "a") (zero? (mod (+ i year) 3))))))
                           rows))
        full (ef/fit-rows rows {:anchors ["a"]})
        part (ef/fit-rows holes {:anchors ["a"]})]
    (testing "the fit with holes is close to the full fit"
      (doseq [j (keys truth)]
        (is (close? 0.08 ((:loading full) j) ((:loading part) j)) j)))
    (testing "a score from fewer indicators is less certain, and says how many it used"
      (let [x {"a" 11.0 "b" 9.0 "c" 10.6 "d" 10.9}
            s4 (ef/score full x)
            s1 (ef/score full (select-keys x ["a"]))]
        (is (= 4 (:k s4)))
        (is (= 1 (:k s1)))
        (is (< (:sd s4) (:sd s1)))
        (is (pos? (:f s1)))))
    (testing "no indicators, no score"
      (is (nil? (ef/score full {})))
      (is (nil? (ef/score full {"zzz" 1.0}))))
    (testing "an indicator with too few observations is left out of the fit"
      (let [few (conj holes {:iso "C0" :year 2000 :known 2002 :indicator "e" :value 3.0})
            fit (ef/fit-rows few {:anchors ["a"] :min-obs 30})]
        (is (nil? ((:loading fit) "e")))
        (is ((:loading fit) "a"))))))

(deftest sign-normalisation
  (testing "the anchor's loading is positive whatever the data's orientation"
    (let [flip (mapv #(update % :value -) rows)]
      (is (pos? ((:loading (ef/fit-rows flip {:anchors ["a"]})) "a")))
      (is (pos? ((:loading (ef/fit-rows rows {:anchors ["b"]})) "b")))
      (is (neg? ((:loading (ef/fit-rows rows {:anchors ["b"]})) "a")))))
  (testing "the next anchor decides when the first is absent from the fit"
    (let [no-a (vec (remove #(= "a" (:indicator %)) rows))]
      (is (pos? ((:loading (ef/fit-rows no-a {:anchors ["a" "c"]})) "c")))))
  (testing "renaming countries does not change the sign or the loadings"
    (let [renamed (mapv #(update % :iso (fn [s] (str "Z" s))) rows)
          f1 (ef/fit-rows rows {:anchors ["a"]})
          f2 (ef/fit-rows renamed {:anchors ["a"]})]
      (doseq [j (keys truth)]
        (is (close? 1e-9 ((:loading f1) j) ((:loading f2) j)))))))

(deftest expanding-fits-are-causal
  (let [years (range 1995 2014)
        base (ef/expanding rows years {:anchors ["a"]})
        ;; wild values known from 2008 on, and a new country known in 2010
        later (into (mapv (fn [r] (if (>= (:known r) 2008) (update r :value * 5.0) r)) rows)
                    (for [y (range 2008 2011) [j _] truth]
                      {:iso "NEW" :year y :known (+ y 2) :indicator j :value 99.0}))
        more (ef/expanding later years {:anchors ["a"]})
        upto (fn [res] (filter #(< (:year %) 2008) (:scores res)))]
    (testing "scores and fits before 2008 do not see what is known from 2008"
      (is (= (upto base) (upto more)))
      (is (= (select-keys (:fits base) (range 1995 2008))
             (select-keys (:fits more) (range 1995 2008)))))
    (testing "and they do change after"
      (is (not= (:scores base) (:scores more))))
    (testing "the first fit uses only rows known by its year"
      (is (= (count (filter #(<= (:known %) 1995) rows))
             (* 4 (:n (get (:fits base) 1995))))))
    (testing "a year scores each country from values known by then, at most stale years old"
      (let [s (first (filter #(and (= "C5" (:iso %)) (= 2000 (:year %))) (:scores base)))]
        (is (= 1998 (:data-year s)))
        (is (= 4 (:k s))))
      (let [r (ef/expanding rows (range 2012 2020) {:anchors ["a"] :stale 4})
            years (set (map :year (filter #(= "C5" (:iso %)) (:scores r))))]
        (is (contains? years 2014))
        (is (not (contains? years 2015)) "data end 2010, stale beyond 2014")))))

(deftest stock-rows
  (let [res (ef/expanding rows (range 1995 2000) {:anchors ["a"]})
        out (ef/stock-rows "financialisation" res)]
    (is (= #{"econ/financialisation" "econ/financialisation-sd" "econ/financialisation-n"}
           (set (map :variable out))))
    (is (every? #(and (string? (:iso %)) (int? (:year %)) (number? (:value %))) out))
    (is (= (count (:scores res)) (count (filter #(= "econ/financialisation" (:variable %)) out))))))

(deftest robust-scaling
  (testing "one wild value does not shrink an indicator's loading"
    (let [wild (conj rows {:iso "C3" :year 1995 :known 1997 :indicator "a" :value 1.0e6})
          l ((:loading (ef/fit-rows wild {:anchors ["a"]})) "a")]
      (is (> l 0.85)))))

;; Depth g drives every indicator; the factor of interest h drives them with
;; mixed signs. With g as the control the factor is h, not g.
(def ctl-rows
  (vec (for [[i c] (map-indexed vector countries)
             y (range 1990 2011)
             :let [g (- (* 0.1 i) 2.0)
                   h (* 1.5 (noise i "h" 0))
                   e (fn [j] (* 0.2 (noise i j y)))]
             r [{:indicator "depth" :value (+ g (e "depth"))}
                {:indicator "a" :value (+ g h (e "a"))}
                {:indicator "b" :value (+ g (* 0.8 h) (e "b"))}
                {:indicator "c" :value (- g h (e "c"))}
                {:indicator "d" :value (+ (* 0.5 g) (* 0.9 h) (e "d"))}]]
         (assoc r :iso c :year y :known (+ y 2)))))

(deftest control-indicator
  (let [h (fn [i] (* 1.5 (noise i "h" 0)))
        g (fn [i] (- (* 0.1 i) 2.0))
        x (fn [rs i] (into {} (for [r rs :when (and (= (:iso r) (countries i)) (= 2000 (:year r)))]
                                [(:indicator r) (:value r)])))
        free (ef/fit-rows ctl-rows {:anchors ["a"]})
        ctl (ef/fit-rows ctl-rows {:anchors ["a"] :control "depth"})
        s (fn [fit] (mapv #(:f (ef/score fit (x ctl-rows %))) (range 40)))]
    (testing "without the control the factor is mostly depth"
      (is (> (Math/abs (corr (s free) (map g (range 40)))) 0.7)))
    (testing "with it the factor is the other one, and the control has no loading"
      (is (> (corr (s ctl) (map h (range 40))) 0.95))
      (is (nil? ((:loading ctl) "depth")))
      (is (neg? ((:loading ctl) "c"))))
    (testing "a row without the control cannot be scored or fitted"
      (is (nil? (ef/score ctl (dissoc (x ctl-rows 3) "depth"))))
      (let [no-ctl (vec (remove #(and (= "depth" (:indicator %)) (< (:year %) 2000)) ctl-rows))]
        (is (= (* 40 11) (:n (ef/fit-rows no-ctl {:anchors ["a"] :control "depth"}))))))))

(deftest weak-anchor-skipped
  (testing "an anchor that barely loads does not set the sign; the next one does"
    (let [junk (for [[i c] (map-indexed vector countries) y (range 1990 2011)]
                 {:iso c :year y :known (+ y 2) :indicator "junk" :value (noise i "junk" y)})
          rs (into rows junk)]
      (is (< (Math/abs ((:loading (ef/fit-rows rs {:anchors ["junk" "b"]})) "junk")) 0.2))
      (is (pos? ((:loading (ef/fit-rows rs {:anchors ["junk" "b"]})) "b")))
      (is (neg? ((:loading (ef/fit-rows rs {:anchors ["junk" "b"]})) "a"))))))

;; ---------------------------------------------------------------- scaling tails

(deftest skewed-indicator-keeps-its-top
  (testing "a skewed indicator's upper tail below the 99th percentile is not clipped flat"
    ;; 300 values: most near 0, a long right tail (an index like the IMF FD
    ;; index, where the rich economies sit many MADs above the median)
    (let [xs (vec (for [i (range 300)] (if (< i 200) (* 0.001 i) (* 0.01 (- i 150)))))
          obs (mapv (fn [x] {"s" x}) (conj xs 1.0e6))
          stats (#'ef/moments obs ["s"] 4.0)
          z (fn [x] (get (#'ef/standardise {"s" x} stats 4.0) "s"))
          top (map z [1.2 1.3 1.4 1.45])]
      (is (apply < top) "distinct values far above 4 MAD stay distinct")
      (is (< (z 1.0e6) (* 1.05 (z 1.5))) "the wild value is held near the 99th percentile")))
  (testing "a symmetric indicator is still clipped at 4 robust SDs"
    (let [xs (vec (for [i (range 400)] (- (* 0.01 i) 2.0)))
          obs (mapv (fn [x] {"s" x}) (conj xs 1.0e6 -1.0e6))
          stats (#'ef/moments obs ["s"] 4.0)
          z (fn [x] (get (#'ef/standardise {"s" x} stats 4.0) "s"))]
      (is (= (z 1.0e6) (z 1.0e5)))
      (is (= (z -1.0e6) (z -1.0e5))))))

;; ---------------------------------------------------------------- balanced panel

(deftest balanced-panel-fit
  (let [;; "c" and "d" only for odd countries, as the NFC accounts cover a subset
        sparse (vec (remove (fn [{:keys [iso indicator]}]
                              (and (even? (parse-long (subs iso 1))) (#{"c" "d"} indicator)))
                            rows))
        bal (ef/fit-rows sparse {:anchors ["a"] :balanced ["c" "d"]})
        full (ef/fit-rows rows {:anchors ["a"]})]
    (testing "only the country-years holding every balanced indicator train the fit"
      (is (= (* 20 21) (:n bal)))
      (is (= ["c" "d"] (:balanced bal))))
    (testing "the loadings match the full fit's"
      (doseq [j (keys truth)]
        (is (close? 0.08 ((:loading full) j) ((:loading bal) j)) j)))
    (testing "a country without the balanced indicators is still scored from what it has"
      (let [s (ef/score bal {"a" 11.0 "b" 9.0})]
        (is (= 2 (:k s)))
        (is (pos? (:f s)))))
    (testing "the expanding fit is balanced in every year"
      (let [res (ef/expanding sparse (range 1995 2000) {:anchors ["a"] :balanced ["c" "d"]})]
        (doseq [[y fit] (:fits res)]
          (is (= (* 20 (- y 1991)) (:n fit)) y))))))

;; ---------------------------------------------------------------- index

(def signs {"a" 1 "b" -1 "c" 1 "d" 1})

(deftest index-construction
  (let [fit (ef/fit-rows rows {:method :index :signs signs})
        x (fn [i] (into {} (for [r rows :when (and (= (:iso r) (countries i)) (= 2000 (:year r)))]
                            [(:indicator r) (:value r)])))
        s (mapv #(:f (ef/score fit (x %))) (range 40))]
    (testing "an equal-weight index of signed, standardised indicators orders the countries"
      (is (= :index (:method fit)))
      (is (> (corr s (map level (range 40))) 0.98)))
    (testing "the sign prior is applied: a high b reads low"
      (is (neg? (:f (ef/score fit {"b" 11.0}))))
      (is (pos? (:f (ef/score fit {"a" 11.0})))))
    (testing "the index is in SD units over the training rows"
      (let [all (for [[_ o] (ef/observations rows)] (:f (ef/score fit o)))
            n (count all) m (/ (reduce + all) n)
            sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) all)) (dec n)))]
        (is (close? 1e-6 0.0 m))
        (is (close? 1e-6 1.0 sd))))
    (testing "equal weights: each indicator counts the same"
      (let [z (fn [j v] (:f (ef/score fit {j v})))]
        (is (close? 1e-9 (+ (z "a" 11.0) (z "c" 10.6)) (* 2 (:f (ef/score fit {"a" 11.0 "c" 10.6})))))))
    (testing "no indicators, or too few, no score"
      (is (nil? (ef/score fit {})))
      (is (nil? (ef/score fit {"zzz" 1.0})))
      (is (nil? (ef/score (assoc fit :min-k 2) {"a" 11.0})))
      (is (= 2 (:k (ef/score (assoc fit :min-k 2) {"a" 11.0 "b" 9.0})))))
    (testing "an indicator without a sign is not used"
      (let [f2 (ef/fit-rows rows {:method :index :signs (dissoc signs "d")})]
        (is (nil? (get-in f2 [:signs "d"])))
        (is (= 3 (:k (ef/score f2 (x 3)))))))
    (testing "the index has no posterior SD"
      (is (nil? (:sd (ef/score fit (x 3))))))))

(deftest index-is-causal
  (let [years (range 1995 2014)
        opts {:method :index :signs signs}
        base (ef/expanding rows years opts)
        later (into (mapv (fn [r] (if (>= (:known r) 2008) (update r :value * 5.0) r)) rows)
                    (for [y (range 2008 2011) [j _] truth]
                      {:iso "NEW" :year y :known (+ y 2) :indicator j :value 99.0}))
        more (ef/expanding later years opts)
        upto (fn [res] (filter #(< (:year %) 2008) (:scores res)))]
    (is (seq (upto base)))
    (is (= (upto base) (upto more)))
    (is (= (select-keys (:fits base) (range 1995 2008))
           (select-keys (:fits more) (range 1995 2008))))
    (is (not= (:scores base) (:scores more)))
    (testing "the 1995 index is scaled on rows known by 1995 only"
      (is (= (count (distinct (map (juxt :iso :year) (filter #(<= (:known %) 1995) rows))))
             (:n (get (:fits base) 1995)))))))

(deftest index-stock-rows
  (let [res (ef/expanding rows (range 1995 1998) {:method :index :signs signs})
        out (ef/stock-rows "financialisation" res)]
    (is (= #{"econ/financialisation" "econ/financialisation-n"} (set (map :variable out))))
    (is (every? #(< (Math/abs (double (:value %))) 1.0e300) out))))
