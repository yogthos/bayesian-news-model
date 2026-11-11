(ns bnm.mix-test
  (:require [bnm.mix :as mix]
            [clojure.test :refer [deftest is testing]]))

(defn- at [feats q type part]
  (let [i (.indexOf ^java.util.List mix/types type)]
    (aget ^doubles (get feats q) (+ i (if (= part :shift) (count mix/types) 0)))))

(deftest the-mix-is-shares-their-stock-and-their-shift
  (let [calm {"consult-did" 90.0 "assault-did" 10.0}
        feats (mix/features (sorted-map 1 calm 2 calm 3 calm 4 {"consult-did" 50.0 "assault-did" 50.0}))]
    (testing "a steady mix: the stock is the share, nothing shifts"
      (is (< (Math/abs (- 0.1 (at feats 3 "assault-did" :stock))) 1e-9))
      (is (< (Math/abs (at feats 3 "assault-did" :shift)) 1e-9)))
    (testing "violence rising as a share shifts up, and the stock follows"
      (is (> (at feats 4 "assault-did" :shift) 0.3))
      (is (< (at feats 4 "consult-did" :shift) -0.3))
      (is (< 0.1 (at feats 4 "assault-did" :stock) 0.5)))
    (testing "a handful of events says little"
      (let [few (mix/features (sorted-map 1 calm 2 {"assault-did" 2.0}))]
        (is (< (at few 2 "assault-did" :shift) 0.2))))
    (testing "a quarter without events keeps the stock and shifts nothing"
      (let [gap (mix/features (sorted-map 1 calm 3 calm))]
        (is (contains? gap 2))
        (is (= (at gap 1 "assault-did" :stock) (at gap 2 "assault-did" :stock)))
        (is (zero? (at gap 2 "assault-did" :shift)))))))
