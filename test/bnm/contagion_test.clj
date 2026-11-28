(ns bnm.contagion-test
  "bnm.contagion (bnm-ris): a country's banks' exposure to bank-equity
  stress abroad, its cross-border claims on each country weighted by that
  country's bank-equity fall, over its GDP, each as known at the origin."
  (:require [bnm.contagion :as ct]
            [bnm.lattice-banking :as lb]
            [clojure.test :refer [deftest is testing]]))

(defn- dec-of [y] (+ (* 12 y) 11))

(def data
  {:claims {"IRL" (sorted-map (+ (* 12 2007) 6) {"USA" 100000.0 "DEU" 50000.0}   ; known Jul 2007
                              (+ (* 12 2008) 0) {"USA" 300000.0})}               ; known Jan 2008
   :gdp {"IRL" (sorted-map (+ (* 12 2006) 8) 2.0e11 (+ (* 12 2007) 8) 2.5e11)}
   :returns {"USA" {2007 -0.32 2008 -0.5} "DEU" {2007 0.1 2008 -0.4}}})

(deftest exposure-weights-claims-by-the-fall-abroad
  (testing "Dec 2007: Jul 2007's claims, the GDP released by then, 2007's returns; a rise abroad counts 0"
    (is (< (Math/abs (- (/ (* 100000.0 1e6 0.32) 2.5e11) (ct/exposure data "IRL" (dec-of 2007)))) 1e-12)))
  (testing "Dec 2008: the later claims"
    (is (< (Math/abs (- (/ (* 300000.0 1e6 0.5) 2.5e11) (ct/exposure data "IRL" (dec-of 2008)))) 1e-12)))
  (testing "before any claims are known, or for a country that reports none: nil"
    (is (nil? (ct/exposure data "IRL" (dec-of 2006))))
    (is (nil? (ct/exposure data "USA" (dec-of 2007)))))
  (testing "a counterparty with no return counts 0"
    (is (zero? (ct/exposure (assoc data :returns {}) "IRL" (dec-of 2007))))))

(deftest standardized-against-the-earlier-origins
  (let [raw (fn [u t] (case u "a" (double (quot t 12)) "b" 0.0 nil))
        os (mapv dec-of (range 2000 2015))
        z (ct/place-z raw ["a" "b" "c"] os 6)]
    (is (nil? (z "a" (os 2))) "too few earlier values")
    (let [t (os 10)
          past (for [o (filter #(< % t) os) u ["a" "b"]] (raw u o))
          m (/ (reduce + past) (count past))
          sd (Math/sqrt (/ (reduce + (map #(Math/pow (- % m) 2) past)) (count past)))]
      (is (< (Math/abs (- (/ (- (raw "a" t) m) sd) (z "a" t))) 1e-12)))
    (is (nil? (z "c" (os 10))) "no value, nil (the theory reads its mean)")
    (is (<= (z "a" (peek os)) 5.0) "capped at 5")))

(deftest the-theory-reads-per-place-extras
  (let [f (lb/theory-row (fn [u _] (when (= u "a") [1.0 2.0])) ["a"] [[:x 0]] nil nil [(fn [u t] (when (= u "a") (* 0.5 t)))])]
    (is (= [1.0 4.0] (f "a" 8)))
    (is (nil? (f "b" 8)))))

(deftest reads-the-files
  ;; data/ is not in git: without the files (CI) the test skips itself
  (if-let [missing (seq (remove #(.exists (java.io.File. ^String %)) ["data/derived/lbs-claims.csv" "data/slow/series.tsv" "data/derived/lattice-annual.csv"]))]
    (println "  (skipped:" (first missing) "not built)")
    (let [d (ct/read-data "data/derived/lbs-claims.csv" "data/slow/series.tsv" "data/derived/lattice-annual.csv")]
      (is (= 117711.0 (get-in d [:claims "IRL" (+ (* 12 2007) 5 5) "USA"])))
      (is (number? (val (first (get-in d [:gdp "USA"])))))
      (is (< (Math/abs (- -0.32008442282676697 (get-in d [:returns "USA" 2007]))) 1e-12)))))

(deftest the-flag-needs-the-theory
  (require 'bnm.world-run)
  (let [choice (resolve 'bnm.world-run/theory-contagion-choice)]
    (is (false? (choice {})))
    (is (true? (choice {:theory-contagion "true" :theory-offset "true"})))
    (is (thrown? Exception (choice {:theory-contagion "true"})) "the exposure is a theory input")
    (is (thrown? Exception (choice {:theory-contagion "yes" :theory-offset "true"})))))

(deftest the-theory-reads-the-log-of-one-plus-the-exposure
  (is (< (Math/abs (- (Math/log1p (/ (* 100000.0 1e6 0.32) 2.5e11)) (ct/log-exposure data "IRL" (dec-of 2007)))) 1e-12))
  (is (nil? (ct/log-exposure data "USA" (dec-of 2007)))))
