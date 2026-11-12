(ns bnm.lattice-political-test
  (:require [bnm.lattice-political :as lp]
            [bnm.stocks :as sk]
            [clojure.test :refer [deftest is]]))

(deftest bnm-h8d-the-lattice-reads-the-penn-world-table-at-its-publication-lag
  ;; the quarters' political economy (PWT profit rate, labour share) went
  ;; missing at every September origin under the default 30 months'
  ;; staleness; with :pwt-stale the lattice reads bnm.named/publication-stale
  ;; (36), as bnm.onsets does. Off, the base of record's reading
  (let [pwt (sk/series (for [y (range 2000 2024)] [(+ 11 (* 12 y)) (+ 11 (* 12 (+ y 2))) 0.1]))
        db {["X" "economy/profit_rate"] pwt ["X" "economy/labor_share"] pwt}
        sep (+ 8 (* 12 2024))
        [_ base] (lp/political-economy-of db ["X"] [sep] {})
        [_ fixed] (lp/political-economy-of db ["X"] [sep] {:pwt-stale true})]
    (is (nil? (first (base "X" sep))))
    (is (= 0.1 (first (fixed "X" sep))))))

(deftest bnm-pjg-6-the-delay-test-reads-the-year-row-a-year-later
  ;; the year gauges' annual rows are current revised downloads with an
  ;; assumed one-year lag; the delay test reads them `delay` years later
  (let [dec2010 (+ 11 (* 12 2010))]
    (is (= 2009 (lp/year-row-year dec2010 0)) "the base: the year before the one ended")
    (is (= 2008 (lp/year-row-year dec2010 1)))))
