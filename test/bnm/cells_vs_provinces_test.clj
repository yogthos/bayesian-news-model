(ns bnm.cells-vs-provinces-test
  (:require [augur.math :as m]
            [bnm.cell-profiles :as cp]
            [bnm.cells-vs-provinces :as cvp]
            [clojure.test :refer [deftest is testing]]))

(deftest cell-nodes-are-parts
  (is (= "AFG@746a800000000000" (cvp/cell-part "AFG.A746A800000000000")))
  (is (= "USA@102800000000000" (cvp/cell-part "USA.A102800000000000")))
  (is (nil? (cvp/cell-part "USA.AK")) "a province id that starts with A is not a cell")
  (is (nil? (cvp/cell-part "AFG.01"))))

(deftest periods-and-the-test-window
  (is (= 2015 (cvp/period-year "20150205")))
  (is (not (cvp/test-window? ["20100107" "20141030"])))
  (is (cvp/test-window? ["20141030" "20150129"])))

(deftest standardized-within-the-year
  (let [z (cvp/standardize {"a" [1.0 nil] "b" [3.0 2.0] "c" [nil 4.0] "d" [2.0 6.0]})]
    (is (neg? (first (z "a"))) "below the mean")
    (is (< (Math/abs (+ (first (z "a")) (first (z "b")))) 1e-9) "symmetric about the mean")
    (is (= 0.0 (first (z "d"))) "at the mean")
    (is (= 0.0 (first (z "c")) (second (z "a"))) "missing reads as the mean")
    (is (< (Math/abs (- 1.0 (/ (reduce + (map #(let [x (first (z %))] (* x x)) ["a" "b" "d"])) 3.0))) 1e-9) "unit spread over the known")
    (is (= {} (cvp/standardize {})))
    (testing "capped at 5"
      (let [big (cvp/standardize (assoc (zipmap (map str (range 100)) (repeat [0.0])) "x" [1000.0]))]
        (is (= 5.0 (first (big "x"))))))
    (testing "a constant column is all zeros"
      (is (= [0.0 0.0] (mapv first (vals (cvp/standardize {"a" [1.0] "b" [1.0]}))))))))

(deftest the-placebo-shuffles-within-each-country
  (let [nodes (vec (concat (for [i (range 12)] (str "AAA.A" i)) (for [i (range 9)] (str "BBB.A" i))))
        iso-of #(subs % 0 3)
        d1 (cvp/donors nodes iso-of 1)
        d2 (cvp/donors nodes iso-of 2)]
    (is (= (set nodes) (set (keys d1)) (set (vals d1))) "a permutation")
    (is (every? (fn [[n d]] (= (iso-of n) (iso-of d))) d1) "never across a border")
    (is (= d1 (cvp/donors (reverse nodes) iso-of 1)) "the seed fixes it, whatever the order given")
    (is (not= d1 d2) "another seed, another shuffle")
    (is (< (count (filter (fn [[n d]] (= n d)) d1)) 6) "most cells read another's features")))

(deftest unit-vectors-in-gauge-order
  (let [prof (zipmap cp/features (map double (range 11)))
        row (zipmap cvp/feature-cols [0.1 -0.2 0.3 nil 1.0 0.0 2.0 3.0 nil])
        v (cvp/unit-vector prof row)]
    (is (= (count cvp/gauge-names) (count v)))
    (is (= (range 11) (map long (take 11 v))) "the profile first")
    (is (= [0.1 -0.2 0.3] (subvec v 11 14)) "growth and the within share raw")
    (is (nil? (nth v 14)) "missing stays missing")
    (is (< (Math/abs (- (Math/log 2.0) (nth v 15))) 1e-12) "counts are log1p")
    (is (every? nil? (cvp/unit-vector nil nil)))
    (is (= (set (range (count cvp/gauge-names))) (set (mapcat val cvp/gauge-groups))) "every gauge in one group")))

(deftest provinces-are-their-cells-summed
  ;; two cells: c1 all in province P1; c2 half P1, half P2 (by land)
  (let [xw {:by-cell {"c1" [{:cell "c1" :iso "AAA" :admin1 "AAA.P1" :share 1.0 :flag "land"}]
                      "c2" [{:cell "c2" :iso "AAA" :admin1 "AAA.P1" :share 0.5 :flag "land"}
                            {:cell "c2" :iso "AAA" :admin1 "AAA.P2" :share 0.5 :flag "land"}]}}
        g {"ghsl/pop" {2010 {:known 2011 :values {"AAA@c1" 100.0 "AAA@c2" 50.0}}}}
        pg (cvp/province-grids xw g)
        pac (cvp/province-counts xw {"AAA@c1" {:extraction 2.0} "AAA@c2" {:extraction 4.0 :processing 1.0}})]
    (is (= {"AAA.P1" 125.0 "AAA.P2" 25.0} (get-in pg ["ghsl/pop" 2010 :values])))
    (is (= 2011 (get-in pg ["ghsl/pop" 2010 :known])) "known when the cells' values are")
    (is (= {"AAA.P1" {:extraction 4.0 :processing 0.5} "AAA.P2" {:extraction 2.0 :processing 0.5}} pac))))

;; --- the sensor join ---------------------------------------------------------------------

(deftest lights-across-the-join-are-masked
  (let [v (vec (range (count cvp/gauge-names)))
        li (.indexOf cvp/gauge-names :serv-lights-pc)
        gi (.indexOf cvp/gauge-names :light-growth-within)
        others #(vec (keep-indexed (fn [i x] (when-not (#{li gi} i) x)) %))]
    (testing "the join year: every node's lights growth spans it"
      (let [m (cvp/mask-lights v cvp/join-year false)]
        (is (nil? (nth m gi)))
        (is (= li (nth m li)))
        (is (= (others v) (others m)))))
    (testing "a part the splice could not put on one scale: no lights from the join on"
      (is (= v (cvp/mask-lights v (dec cvp/join-year) true)) "before the join its lights are DMSP's")
      (let [m (cvp/mask-lights v (inc cvp/join-year) true)]
        (is (nil? (nth m gi)))
        (is (nil? (nth m li)))
        (is (= (others v) (others m)))))
    (is (= v (cvp/mask-lights v (inc cvp/join-year) false)) "a spliced part keeps its lights")
    (is (= 2016 cvp/join-year) "lights lag a year; features read the year before: 2014's VIIRS is read in 2016")))

(deftest a-province-breaks-with-most-of-its-lights
  (let [xw {:by-cell {"c1" [{:cell "c1" :iso "AAA" :admin1 "AAA.P1" :share 1.0 :flag "land"}]
                      "c2" [{:cell "c2" :iso "AAA" :admin1 "AAA.P2" :share 1.0 :flag "land"}]
                      "c3" [{:cell "c3" :iso "AAA" :admin1 "AAA.P2" :share 1.0 :flag "land"}]}}
        dmsp {"AAA@c1" 300.0 "AAA@c2" 100.0 "AAA@c3" 300.0}]
    (is (= #{"AAA.P2"} (cvp/broken-provinces xw dmsp #{"AAA@c3"})) "three quarters of P2's lights")
    (is (= #{} (cvp/broken-provinces xw dmsp #{"AAA@c2"})) "a quarter is not most")))

;; --- the stage ------------------------------------------------------------------------

(defn- world
  "Synthetic violence rows: 60 places over 30 periods; a place's gauge g
  (fixed, known) raises its escalation odds; the event model's p knows
  nothing of g."
  [seed]
  (let [r (m/rng seed)
        places (vec (for [i (range 60)] (str "AAA.A" i)))
        g (into {} (for [p places] [p (- (* 2.0 (m/next-double! r)) 1.0)]))
        periods (vec (for [t (range 30)] (format "%d%04d" (+ 2000 (quot t 4)) (+ 101 (* 300 (mod t 4))))))]
    {:g g
     :rows (vec (for [[t lbl] (map-indexed vector periods) p places]
                  (let [y (< (m/next-double! r) (m/expit (+ -2.5 (* 1.5 (g p)))))]
                    {:id p :period lbl :stratum (if (even? t) :quiet :low)
                     :p-escalate 0.1 :p-clim 0.1 :escalated y})))}))

(deftest the-stage-reads-the-gauges
  (let [{:keys [g rows]} (world 3)
        with (cvp/stage rows (fn [p _] (into [(g p)] (repeat (dec (count cvp/gauge-names)) 0.0))) {})
        without (cvp/stage rows (constantly nil) {})
        late #(filter (fn [r] (>= (compare (:period r) "2004") 0)) %)]
    (is (= (count rows) (count with)) "a forecast for every row")
    (is (every? #(< 0.0 (:p-stage %) 1.0) with))
    (is (> (:auc (cvp/scores (late with))) (+ 0.1 (or (:auc (cvp/scores (late without))) 0.5)))
        "the gauge ranks the escalations once learned")
    (is (> (:bss (cvp/scores (late with))) (:bss (cvp/scores (late without)))))
    (testing "without gauges and with nothing learned yet, the stage forecasts the event model"
      (let [first-p (:period (first rows))]
        (is (every? #(< (Math/abs (- (:p-stage %) 0.1)) 0.01) (filter #(= first-p (:period %)) without)))))))

(deftest the-stage-reads-nothing-from-the-future
  (let [{:keys [g rows]} (world 4)
        gauges (fn [p _] (into [(g p)] (repeat (dec (count cvp/gauge-names)) 0.0)))
        cut "20030101"
        flipped (mapv #(if (pos? (compare (:period %) cut)) (update % :escalated not) %) rows)
        a (cvp/stage rows gauges {})
        b (cvp/stage flipped gauges {})
        upto (fn [rs] (map :p-stage (filter #(<= (compare (:period %) "20030401") 0) rs)))]
    (is (= (upto a) (upto b)) "outcomes after a period do not move its forecast")
    (is (not= (map :p-stage a) (map :p-stage b)) "they do move later ones")))

(deftest scores-are-the-quiet-rows
  (let [rows [{:stratum :quiet :p-stage 0.9 :p-clim 0.5 :escalated true}
              {:stratum :quiet :p-stage 0.1 :p-clim 0.5 :escalated false}
              {:stratum :low :p-stage 0.1 :p-clim 0.5 :escalated true}]
        s (cvp/scores rows)]
    (is (= 2 (:n s)))
    (is (= 1.0 (:auc s)))
    (is (< (Math/abs (- (:bss s) (- 1.0 (/ 0.01 0.25)))) 1e-9))
    (is (= 1.0 (:auc (cvp/scores (map #(assoc % :p-escalate (:p-stage %)) rows) :p-escalate))) "any probability key")))

(deftest the-bootstrap-pairs-periods
  (let [mk (fn [p good?] (for [i (range 40)]
                           {:period (str "2001" (format "%04d" p)) :stratum :quiet :p-clim 0.3
                            :escalated (< i 12) :p-stage (if good? (if (< i 12) 0.6 0.2) 0.3)}))
        a (mapcat #(mk % true) (range 10))
        b (mapcat #(mk % false) (range 10))
        {:keys [auc bss]} (cvp/bootstrap a b {:reps 50 :seed 1})]
    (is (every? pos? (map first [auc bss])) "a beats b throughout: the lower bound is above 0")
    (is (<= (first auc) (second auc)))))
