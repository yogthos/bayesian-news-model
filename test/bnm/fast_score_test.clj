(ns bnm.fast-score-test
  (:require [bnm.fast-score :as fs]
            [bnm.watchlist :as wl]
            [clojure.test :refer [deftest is testing]]))

(deftest holm-steps-down
  (is (= {:a true :b true :c false} (fs/holm {:a 0.01 :b 0.02 :c 0.06} 0.05)) "0.01 <= 0.05/3, 0.02 <= 0.05/2, 0.06 > 0.05")
  (is (= {:a false :b false :c false} (fs/holm {:a 0.02 :b 0.004 :c 0.5} 0.01)) "0.004 > 0.01/3")
  (is (= {:a true :b false :c false} (fs/holm {:a 0.001 :b 0.04 :c 0.03} 0.05)) "stops at the first that fails"))

(deftest the-same-interval-as-the-watch-list
  (let [cells {"k1" (into {} (for [y (range 2003 2023)] [y [(if (even? y) 1.0 -0.5) 4]]))
               "k2" (into {} (for [y (range 2003 2023)] [y [(if (zero? (mod y 3)) 1.0 0.0) 2]]))}
        years (range 2003 2023)
        opts {:reps 200 :seed 1 :alpha 0.1 :block 2}
        a (wl/paired-boot cells years opts)
        b (fs/boot cells years opts)]
    (is (= a (select-keys b [:diff :lo :hi])))
    (is (<= 0.0 (:p b) 1.0))
    (testing "a clear gain has a small p"
      (is (< (:p (fs/boot {"k" (into {} (for [y years] [y [1.0 2]]))} years opts)) 0.01)))))
