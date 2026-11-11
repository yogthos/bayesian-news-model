(ns bnm.coherence-report-test
  (:require [bnm.coherence-report :as cr]
            [bnm.compose :as c]
            [clojure.test :refer [deftest is testing]]))

;; a book coherent by construction: constant rates per country, parents
;; at the composition of their children, quarters at the year's rate
(def ^:private rates {"KEN" 0.002 "TZA" 0.001 "UGA" 0.003})

(defn- book [bump]
  (let [ts (range (+ (* 12 2005) 11) (* 12 2010) 12)
        sub (reduce + (vals rates))
        cell (fn [scale place lam] [[scale place] (into (sorted-map) (for [t (concat ts (map #(- % 3) ts))]
                                                                         [t (c/rate->p lam (:w (cr/windows scale)))]))])]
    (into {} (concat (for [[pl lam] rates scale ["year" "quarter-lead" "quarter-w2"]] (cell scale pl lam))
                     (for [scale ["year" "quarter-lead" "quarter-w2"]] (cell scale "s/eastern-africa" (* bump sub)))))))

(deftest a-coherent-book-has-no-gaps
  (let [b (book 1.0)
        pg (cr/place-gaps b)
        tg (cr/time-gaps b)]
    (is (seq pg))
    (is (every? #(< (Math/abs (:gap %)) 1e-12) pg))
    (is (every? #(< (Math/abs (:log-ratio %)) 1e-9) pg))
    (is (= 20 (count tg)) "four places, five years")
    (is (every? #(< (Math/abs (:gap %)) 1e-12) tg)))
  (testing "a parent saying twice its children's rate"
    (let [pg (cr/place-gaps (book 2.0))]
      (is (every? #(< (Math/abs (- (:log-ratio %) (Math/log 2.0))) 1e-9) pg))
      (is (every? #(pos? (:gap %)) pg))))
  (testing "the tree from the places present"
    (is (= {"s/eastern-africa" ["KEN" "TZA" "UGA"]} (cr/tree ["KEN" "TZA" "UGA" "s/eastern-africa"])))))

;; -- review of step 1, findings 1 and 2 ---------------------------------------------

(defn- scored-book
  "One subregion of three countries at one origin, each entry {:p :y}: the
  parent's target counts only the children whose own target is known."
  [y-uga]
  (let [t (+ 11 (* 12 2005))
        w 24
        cell (fn [place p y] [["year" place] (sorted-map t {:p p :y y})])]
    (into {} [(cell "KEN" (c/rate->p 0.002 w) true)
              (cell "TZA" (c/rate->p 0.001 w) false)
              (cell "UGA" (c/rate->p 0.003 w) y-uga)
              (cell "s/eastern-africa" (c/rate->p 0.006 w) true)])))

(deftest children-without-a-target-are-not-composed
  (let [[row] (cr/place-gaps (scored-book nil))
        kt (- 1.0 (* (- 1.0 (c/rate->p 0.002 24)) (- 1.0 (c/rate->p 0.001 24))))]
    (is (< (Math/abs (- (:comp row) kt)) 1e-12) "UGA, outside its coverage, is left out")
    (is (true? (:y row)) "the parent's outcome is beside it"))
  (testing "with every child scored, all three compose"
    (let [[row] (cr/place-gaps (scored-book false))]
      (is (< (Math/abs (:gap row)) 1e-12))))
  (testing "a parent whose own target is unknown gives no row"
    (is (empty? (cr/place-gaps (assoc-in (scored-book false) [["year" "s/eastern-africa"] (+ 11 (* 12 2005)) :y] nil))))))

(deftest read-book-keeps-the-outcome
  (let [f (java.io.File/createTempFile "forecasts" ".tsv")]
    (spit f "year\tKEN\t24071\t0.1\ttrue\nyear\tTZA\t24071\t0.2\t\nyear\tUGA\t24071\t0.3\tfalse\n")
    (let [b (cr/read-book (str f))]
      (is (= {:p 0.1 :y true} (get-in b [["year" "KEN"] 24071])))
      (is (= {:p 0.2 :y nil} (get-in b [["year" "TZA"] 24071])))
      (is (= {:p 0.3 :y false} (get-in b [["year" "UGA"] 24071]))))))

(deftest the-summary-carries-calibration-and-the-share-below
  (let [rows [{:gap -0.1 :p 0.2 :comp 0.3 :y true} {:gap 0.05 :p 0.1 :comp 0.05 :y false}
              {:gap -0.02 :p 0.3 :comp 0.32 :y nil} {:gap -0.01 :p 0.1 :comp 0.11 :y false}]
        s (cr/summary rows)]
    (is (= 0.75 (:share-below s)))
    (is (< (Math/abs (- (/ 1.0 3.0) (:mean-y s))) 1e-12) "over the rows whose outcome is known")
    (is (= 3 (:n-y s))))
  (testing "a calibration row for every node with an outcome, countries included"
    (let [cal (cr/calibration-rows (scored-book false))]
      (is (= 4 (count cal)))
      (is (= #{"country" "subregion"} (set (map :level cal)))))))
