(ns bnm.capacity-test
  (:require [bnm.capacity :as cap]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (and (number? a) (number? b) (< (Math/abs (- (double a) (double b))) 1e-9)))

;; -- time to build -----------------------------------------------------------------------------

(deftest priors-are-distributions-over-whole-years-with-sources
  (doseq [[ind {:keys [p range sources]}] cap/build-time]
    (is (close? 1.0 (reduce + (vals p))) (str ind " sums to 1"))
    (is (every? #(and (integer? %) (pos? %)) (keys p)) (str ind " build times are whole years"))
    (is (= range [(apply min (keys p)) (apply max (keys p))]) (str ind " range is its support"))
    (is (seq sources) (str ind " cites its sources")))
  (is (= (cap/prior "C24-C25") (cap/build-time "C24-C25")))
  (is (= (cap/prior "G") (cap/build-time :default)) "an industry without its own prior takes the default"))

(deftest spending-spread-over-the-build-completes-with-these-lags
  (testing "a two-year build: half the spending in the completion year, half the year before"
    (is (= {0 0.5 1 0.5} (cap/completion-weights {2 1.0}))))
  (testing "a mix of builds"
    (let [w (cap/completion-weights {1 0.5 3 0.5})]
      (is (close? (+ 0.5 (/ 0.5 3)) (w 0)))
      (is (close? (/ 0.5 3) (w 1)))
      (is (close? (/ 0.5 3) (w 2)))
      (is (close? 1.0 (reduce + (vals w))))))
  (is (close? 0.5 (cap/mean-lag {2 1.0})) "a two-year build: the mean dollar completes half a year later")
  (doseq [[ind {:keys [p]}] cap/build-time]
    (is (close? 1.0 (reduce + (vals (cap/completion-weights p)))) (str ind))))

;; -- investment into completions ------------------------------------------------------------------

(deftest investment-rates-are-over-the-opening-stock
  (is (close? 0.1 (cap/investment-rate 10.0 100.0)))
  (is (nil? (cap/investment-rate 10.0 0.0)))
  (is (nil? (cap/investment-rate nil 100.0))))

(deftest completions-read-only-investment-already-made
  (let [p {2 0.5 3 0.5} ;; weights: L0 1/4+1/6, L1 1/4+1/6, L2 1/6
        rates {2000 0.10 2001 0.20 2002 0.40}]
    (testing "the completion rate for year y sums the years before it, never y's own"
      (is (close? (+ (* (+ 0.25 (/ 1.0 6)) 0.40) (* (/ 1.0 6) 0.20)) (cap/completion-rate p rates 2003)))
      (is (= (cap/completion-rate p rates 2003) (cap/completion-rate p (assoc rates 2003 99.0) 2003))
          "year 2003's own investment does not enter its completions"))
    (testing "a missing year in the pipeline leaves it unknown"
      (is (some? (cap/completion-rate p rates 2002)) "2001 (L1) and 2000 (L2) are there")
      (is (nil? (cap/completion-rate p rates 2001)) "1999 (L2) is not")
      (is (nil? (cap/completion-rate p (dissoc rates 2001) 2003))))
    (is (nil? (cap/completion-rate {1 1.0} rates 2003)) "a one-year build has nothing in the pipeline")))

;; -- financing: credit plus retained profits --------------------------------------------------------

(deftest financed-investment-is-retained-profits-by-surplus-plus-external-finance-by-credit
  (let [gos {"A" 10.0 "C10-C12" 30.0 "C20" 10.0 "D-E" 20.0 "H" 30.0 "L" 500.0 "O-Q" 99.0 "K" 50.0 "C" 40.0 "TOT" 1000.0}
        shares {"NFC" 0.5 "A" 0.05 "C" 0.15 "L" 0.10}
        f (cap/financed-investment {"b8g" 80.0 "nl" -20.0} gos shares)]
    (testing "retained profits split by gross operating surplus over the NFC industries (no L, K, O-Q, aggregates)"
      (is (= #{"A" "C10-C12" "C20" "D-E" "H"} (set (keys f)))))
    (testing "external finance (-B9) by credit: a section's share of NFC credit, split within it by surplus"
      ;; cs: A 0.1, C 0.3, L 0.2 (dropped), others 1 - 0.1 - 0.3 - 0.2 = 0.4 over D-E and H by surplus
      (is (close? (+ (* 80 0.1) (* 20 0.1)) (f "A")))
      (is (close? (+ (* 80 0.3) (* 20 0.3 0.75)) (f "C10-C12")))
      (is (close? (+ (* 80 0.1) (* 20 0.3 0.25)) (f "C20")))
      (is (close? (+ (* 80 0.2) (* 20 0.4 0.4)) (f "D-E")))
      (is (close? (+ (* 80 0.3) (* 20 0.4 0.6)) (f "H"))))
    (testing "real estate's credit leaves the NFC industries: the total is retained plus external less L's part"
      (is (close? (+ 80 (* 20 0.8)) (reduce + (vals f)))))
    (testing "without credit shares, external finance follows surplus too"
      (let [g (cap/financed-investment {"b8g" 80.0 "nl" -20.0} gos nil)]
        (is (close? 100.0 (reduce + (vals g))))
        (is (close? (* 100 0.1) (g "A")))))
    (testing "nothing without the NFC accounts or surplus"
      (is (nil? (cap/financed-investment {"b8g" 80.0} gos shares)))
      (is (nil? (cap/financed-investment {"b8g" 80.0 "nl" 1.0} {"L" 5.0} shares))))))
