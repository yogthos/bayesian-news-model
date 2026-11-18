(ns bnm.banking-gauges-test
  "bnm-cpv, --banking-gauges: the world's leading gauges at banking's year
  node, with the priors of docs/LEADING.md \"Banking gauges\", fixed
  before any run."
  (:require [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(deftest banking-gauges-option
  (is (nil? (wr/banking-gauges-choice {})))
  (is (nil? (wr/banking-gauges-choice {:banking-gauges "false"})))
  (is (= :theory (wr/banking-gauges-choice {:banking-gauges "true"})))
  (is (= :flat (wr/banking-gauges-choice {:banking-gauges "flat"})))
  (is (thrown? Exception (wr/banking-gauges-choice {:banking-gauges "yes"}))))

(deftest the-gauges-and-their-priors-are-the-registered-ones
  (is (= [:baa12 :us-curve :vix12 :world-credit3] lb/leading-names))
  (is (= {:world-credit3 [0.4 0.0625] :baa12 [0.3 0.0625] :us-curve [-0.2 0.0625] :vix12 [0.2 0.0625]}
         (lb/leading-priors-for :theory)))
  (testing "flat: the same gauges at the node's default prior, mean 0 and variance 1"
    (is (= (zipmap lb/leading-names (repeat [0.0 1.0])) (lb/leading-priors-for :flat)))))

(deftest the-row-is-the-us-gauges-then-world-credit
  ;; bnm.named/leading's first three, as the political kinds read them,
  ;; then the world's three-year credit change at the origin
  (let [lead-f (fn [u t] [(+ t 0.1) (+ t 0.2) (+ t 0.3) :food12 (keyword u)])
        wc {35 2.5}
        row (lb/leading-row lead-f wc)]
    (is (= [35.1 35.2 35.3 2.5] (row "AAA" 35)))
    (is (= [38.1 38.2 38.3 nil] (row "AAA" 38)) "no world credit at the origin: nil, read as 0")))

(deftest the-year-node-names-every-column-and-groups-the-gauges
  (let [w (lb/leading-wiring 61 :theory)]
    (is (= 65 (count (:names w))))
    (is (= lb/leading-names (subvec (:names w) 61)))
    (is (apply distinct? (:names w)) "own columns named apart from the gauges")
    (is (not-any? (set lb/leading-names) (subvec (:names w) 0 61)))
    (is (= [61 62 63 64] (:group w)))
    (is (= (lb/leading-priors-for :theory) (:named-prior w)))
    (is (= (lb/leading-priors-for :flat) (:named-prior (lb/leading-wiring 61 :flat))))))

(deftest the-world-gauges-set-banking's-frailty-level
  (let [names (:names (lb/leading-wiring 3 :theory))
        out (wr/with-frailty-gauges {"banking_crisis" {:frailty {} :gauge-names {:year names}}} true)]
    (is (= [:baa12 :us-curve :vix12 :world-credit3] (get-in out ["banking_crisis" :frailty :gauges :year])))))

(deftest the-history-standardizer-is-shared
  ;; moved to bnm.lattice-banking so banking can read it; the political
  ;; kinds call the same function
  (is (= lb/history-standardizer lp/history-standardizer)))
