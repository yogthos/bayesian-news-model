(ns bnm.catalogue-through-test
  "Loop audit Test 7: a catalogue that stops (:through) before t less its
  publication lag is unknown in its uncovered tail, never counted as calm."
  (:require [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [clojure.test :refer [deftest is testing]]))

(def ^:private cat
  ;; two countries from 1990, onsets in 2000 and 2010 (and 2022 at "BBB"); the catalogue covers
  ;; "AAA" to the end of 2020 and "BBB" to the end of 2030; known 6 months on
  {:onsets {"AAA" (sorted-set (* 12 2000) (* 12 2010)) "BBB" (sorted-set (* 12 2010) (* 12 2022))}
   :from {"AAA" (* 12 1990) "BBB" (* 12 1990)}
   :through {"AAA" (+ 11 (* 12 2020)) "BBB" (+ 11 (* 12 2030))}
   :known-lag 6
   :unknown-past-through true})

(deftest history-is-unknown-past-the-catalogue
  (testing "covered: counted as before"
    (is (= 2.0 (nth (#'lb/history cat "AAA" (* 12 2015)) 2)))
    (is (some? (#'lb/history cat "AAA" (+ 5 (* 12 2021)))) "t - lag is still covered"))
  (testing "t less its lag past :through: unknown, not years of calm"
    (is (nil? (#'lb/history cat "AAA" (* 12 2023))))
    (is (some? (#'lb/history cat "BBB" (* 12 2023))))))

(deftest counts-are-unknown-past-the-catalogue
  (is (= 1.0 (#'lp/count-known cat "AAA" (* 12 2015) 120)))
  (is (nil? (#'lp/count-known cat "AAA" (* 12 2023) 120)))
  (is (= 1.0 (#'lp/count-known cat "BBB" (* 12 2015) 120))))

(deftest off-by-default
  (let [old (dissoc cat :unknown-past-through)]
    (is (some? (#'lb/history old "AAA" (* 12 2023))) "the bases before Test 7 read the tail as before")
    (is (= 0.0 (#'lp/count-known old "AAA" (* 12 2023) 120)))))

(deftest the-world-share-counts-only-covered-places
  (let [in-system (constantly true)]
    (is (= 1.0 (lp/world-share cat ["AAA" "BBB"] in-system (+ 6 (* 12 2010)))))
    (is (= 0.0 (lp/world-share cat ["AAA" "BBB"] in-system (* 12 2016))))
    (testing "a place past its catalogue drops out of the share, it is not a calm one"
      (is (= 1.0 (lp/world-share cat ["AAA" "BBB"] in-system (+ 6 (* 12 2023))))))
    (testing "no place covered: unknown"
      (is (nil? (lp/world-share cat ["AAA"] in-system (* 12 2024)))))))
