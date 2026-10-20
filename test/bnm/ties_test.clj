(ns bnm.ties-test
  (:require [bnm.ties :as ties]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest partners-weighted-by-the-trailing-year
  ;; A deals with B three times as much as with C over the four quarters to q
  (let [dyads {8 {["A" "B"] {:coop 3.0} ["A" "C"] {:coop 1.0}}
               9 {["A" "B"] {:coop 3.0} ["A" "C"] {:coop 1.0}}}
        w (ties/partner-weights dyads 9 10)]
    (is (close? 0.75 (get-in w ["A" "B"])))
    (is (close? 0.25 (get-in w ["A" "C"])))
    (is (close? 1.0 (get-in w ["B" "A"])) "B's only partner is A")
    (testing "nothing from after the quarter"
      (is (= {} (ties/partner-weights dyads 7 10))))
    (testing "only the top partners, renormalized"
      (is (= {"B" 1.0} (get (ties/partner-weights dyads 9 1) "A"))))))

(deftest cross-pressure-is-conflict-between-ones-partners
  ;; A's partners B and C fight each other; D's partners E and F cooperate
  (let [dyads {9 {["A" "B"] {:coop 1.0} ["A" "C"] {:coop 1.0}
                  ["B" "C"] {:conflict 9.0 :coop 1.0}
                  ["D" "E"] {:coop 1.0} ["D" "F"] {:coop 1.0}
                  ["E" "F"] {:coop 10.0}}}
        w (ties/partner-weights dyads 9 10)
        share (ties/conflict-shares dyads 9)]
    (is (> (ties/cross-pressure (w "A") share) 0.2))
    (is (close? 0.0 (ties/cross-pressure (w "D") share)))
    (is (close? 0.0 (ties/cross-pressure {"B" 1.0} share)) "one partner: no pair to be torn between")))

(deftest features-per-country-and-quarter
  (let [dyads (into {} (for [q (range 4 10)]
                         [q {["A" "B"] {:coop 4.0} ["A" "C"] {:coop 4.0} ["B" "C"] {:coop 2.0}}]))
        ;; B's violence jumps in quarter 9
        counts (into {} (for [q (range 4 10) u ["A" "B" "C"]]
                          [[u q] {"assault-did" (if (and (= u "B") (= q 9)) 100.0 10.0) "protest-did" 10.0}]))
        near? (constantly false)
        fs (ties/features dyads counts near?)
        x (fs ["A" 9])
        at #(aget ^doubles x (.indexOf ^java.util.List ties/labels %))]
    (is (= (count ties/labels) (alength ^doubles x)))
    (is (> (at :far-violence) 0.5) "a partner's violence surge reaches A")
    (is (close? 0.0 (at :far-protest)))
    (is (close? 0.0 (at :near-violence)) "no partner is near")
    (is (close? 0.5 (at :concentration)) "two equal partners")
    (is (nil? (fs ["A" 3])) "no trailing year yet")))

(deftest placebo-shuffles-within-each-quarter
  (let [xs {["A" 1] [1.0] ["B" 1] [2.0] ["C" 1] [3.0] ["A" 2] [4.0] ["B" 2] [5.0]}
        p (ties/placebo xs 7)]
    (is (= (set (keys xs)) (set (keys p))))
    (is (= #{[1.0] [2.0] [3.0]} (set (map p [["A" 1] ["B" 1] ["C" 1]]))) "values stay in their quarter")
    (is (= #{[4.0] [5.0]} (set (map p [["A" 2] ["B" 2]]))))
    (is (= p (ties/placebo xs 7)) "seeded")
    (let [ys (into {} (for [i (range 50)] [[(str i) 1] [(double i)]]))]
      (is (not= ys (ties/placebo ys 7)) "it moves values"))))

(deftest placebo-keeps-each-row-in-its-stratum
  ;; the last value is a presence flag: values move only among rows with the same flag
  (let [xs (into {} (for [i (range 40)] [[(str i) 1] [(double i) (if (even? i) 1.0 0.0)]]))
        p (ties/placebo xs 3 peek)]
    (is (every? (fn [[k v]] (= (peek v) (peek (xs k)))) p))
    (is (not= xs p))
    (is (= (set (vals xs)) (set (vals p))))))

(deftest partners-can-be-weighted-another-way
  ;; weights from elsewhere (UN voting agreement): A's only partner is C,
  ;; though A has no events with C, so C's surge reaches A
  (let [dyads (into {} (for [q (range 4 10)] [q {["A" "B"] {:coop 4.0} ["B" "C"] {:coop 1.0}}]))
        counts (into {} (for [q (range 4 10) u ["A" "B" "C"]]
                          [[u q] {"assault-did" (if (and (= u "C") (= q 9)) 100.0 10.0)}]))
        fs (ties/features dyads counts (constantly false) {:weights-of (fn [_] {"A" {"C" 1.0}})})
        at #(aget ^doubles (fs ["A" 9]) (.indexOf ^java.util.List ties/labels %))]
    (is (> (at :far-violence) 1.0))
    (is (nil? (fs ["B" 9])) "only countries with weights")))
