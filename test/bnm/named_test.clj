(ns bnm.named-test
  (:require [bnm.named :as nm]
            [bnm.stocks :as sk]
            [clojure.test :refer [deftest is testing]]))

(defn- monthly
  "A series of `v` from month a to b, each published `lag` months after."
  [a b lag v]
  (sk/series (for [t (range a (inc b))] [t (+ t lag) (v t)])))

(defn- build
  "A family over sources \"s\" (inputs :a, :b) and \"c\" (input :c), each
  stale after 6 months, and :d in no group."
  [db]
  (fn []
    [[:a :b :c :d]
     (fn [u t]
       (let [g #(some-> (db [u %]) (sk/value t {:stale 6}))]
         [(g "s") (some-> (g "s") (* 2.0)) (g "c") (g "d")]))]))

(def groups [[:a :b] [:c]])

(deftest a-lapsed-source-keeps-its-last-values-and-is-flagged
  (let [db {["X" "s"] (monthly 0 100 1 double)
            ["X" "c"] (monthly 0 300 1 #(- (double %)))
            ["X" "d"] (monthly 0 100 1 (constantly 7.0))
            ;; Y never had "s"
            ["Y" "c"] (monthly 0 300 1 (constantly 1.0))}
        [labels f] (nm/vectorizer (build db) groups)
        v #(vec (f %1 %2))]
    (is (= [:a :b :c :d :a-present :c-present :a-lapsed :c-lapsed] labels))
    (testing "fresh"
      (is (= [99.0 198.0 -99.0 7.0 1.0 1.0 0.0 0.0] (v "X" 100))))
    (testing "past its staleness: the last values, present, lapsed"
      (is (= [100.0 200.0 -200.0 7.0 1.0 1.0 1.0 0.0] (v "X" 201))))
    (testing "a unit the source never covered reads absent, as before"
      (is (= [0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0] (v "Y" 201))))
    (testing "an input in no group keeps its last value"
      (is (= 7.0 (nth (v "X" 250) 3))))))

(deftest a-resumed-source-is-read-fresh-again
  (let [db {["X" "s"] (sk/series (concat (for [t (range 0 101)] [t (inc t) 1.0])
                                         (for [t (range 150 201)] [t (inc t) 5.0])))
            ["X" "c"] (monthly 0 300 1 (constantly 1.0))}
        [_ f] (nm/vectorizer (build db) groups)]
    (is (= 1.0 (nth (vec (f "X" 130)) 6)) "lapsed between")
    (is (= [5.0 10.0] (subvec (vec (f "X" 160)) 0 2)))
    (is (= 0.0 (nth (vec (f "X" 160)) 6)) "fresh once it resumes")))

(deftest nothing-published-after-t-is-read
  ;; the value for month 100 is published at month 200: before then the
  ;; lapsed source carries month 99's, not it
  (let [db {["X" "s"] (sk/series (concat (for [t (range 0 100)] [t (inc t) 1.0]) [[100 200 9.0]]))
            ["X" "c"] (monthly 0 300 1 (constantly 1.0))}
        [_ f] (nm/vectorizer (build db) groups)]
    (is (= [1.0 2.0] (subvec (vec (f "X" 150)) 0 2)))
    (is (= 1.0 (nth (vec (f "X" 150)) 6)))
    (is (= [9.0 18.0] (subvec (vec (f "X" 200)) 0 2)) "read once published")
    (is (false? sk/*keep-stale*) "the binding does not leak")))

(deftest the-families-label-their-lapses
  (doseq [fam (keys nm/presence)]
    (let [p (count (nm/presence fam))]
      (is (= p (count (filter #(.endsWith (name %) "-lapsed") (nm/lapsed-labels fam)))) fam))))

(deftest the-penn-world-table-does-not-lapse-each-september
  ;; a year ending in December published two years later: under 30 months'
  ;; staleness the latest year was stale every September
  (let [pwt (sk/series (for [y (range 2000 2024)] [(+ 11 (* 12 y)) (+ 11 (* 12 (+ y 2))) 0.1]))
        db {["X" "economy/profit_rate"] pwt ["X" "economy/labor_share"] pwt}
        sep (+ 8 (* 12 2024))
        [_ f30] (nm/political-economy db ["X"] [sep])
        [_ f36] (nm/political-economy db ["X"] [sep] {:stale nm/publication-stale})]
    (is (nil? (first (f30 "X" sep))))
    (is (= 0.1 (first (f36 "X" sep))))))
