(ns bnm.signal-decl-test
  "docs/SIGNALS.md: signals declared as data, read point in time, and a
  node's inputs assembled from the declarations."
  (:require [bnm.signals :as sg]
            [bnm.stocks :as sk]
            [clojure.test :refer [deftest is testing]]))

(defn- annual
  "A series of annual values for `years`, each December's published six
  months later."
  [years->x]
  (sk/series (for [[y x] years->x] [(+ (* 12 y) 11) (+ (* 12 y) 17) x])))

(def credit {2000 50.0 2001 52.0 2002 51.0 2003 53.0 2004 54.0 2005 52.0 2006 55.0
             2007 70.0 2008 64.0})
(def db {["AAA" "finance/credit"] (annual credit)
         ["AAA" "trade/exports_share"] (annual {2006 0.5 2007 0.5})
         ["world" "prices/index"] (annual {2005 100.0 2006 110.0 2007 121.0})})

(defn- dec-origin [y] (+ (* 12 y) 11))

(deftest reads-are-point-in-time
  (let [rd (fn [decl t] (sg/read-signal db decl "AAA" t))]
    (testing "value: Dec 2007 is published Jun 2008, so Dec 2007's origin reads 2006"
      (is (= 55.0 (rd {:name :c :var "finance/credit" :read [:value]} (dec-origin 2007))))
      (is (= 70.0 (rd {:name :c :var "finance/credit" :read [:value]} (dec-origin 2008)))))
    (testing "change over 12 months of periods"
      (is (= 15.0 (rd {:name :c :var "finance/credit" :read [:change 12]} (dec-origin 2008)))))
    (testing "a fixed unit"
      (is (= 110.0 (rd {:name :w :var "prices/index" :unit "world" :read [:value]} (dec-origin 2007)))))
    (testing "an exposure multiplies"
      (is (= (* 0.5 15.0) (rd {:name :x :var "finance/credit" :read [:change 12]
                               :times {:var "trade/exports_share" :read [:value]}}
                              (dec-origin 2008)))))
    (testing "nothing published yet: nil"
      (is (nil? (rd {:name :c :var "finance/credit" :read [:value]} (dec-origin 1999))))
      (is (nil? (rd {:name :c :var "nope" :read [:value]} (dec-origin 2007)))))))

(deftest stretch-reads-the-published-observations
  ;; at Dec 2008 the published values are 2000..2007; 2007 against 2000..2006
  (let [past [50.0 52.0 51.0 53.0 54.0 52.0 55.0]
        expect (sg/z-against 70.0 past {:min 6 :cap 5.0})]
    (is (some? expect))
    (is (= expect (sg/read-signal db {:name :s :var "finance/credit" :read [:stretch {:window 10 :min 6 :cap 5.0}]} "AAA" (dec-origin 2008)))))
  (is (nil? (sg/read-signal db {:name :s :var "finance/credit" :read [:stretch {:window 10 :min 6}]} "AAA" (dec-origin 2004)))
      "five published values: too short"))

(deftest turn-is-a-stretched-observation-moving-back
  (let [d {:name :t :var "finance/credit" :read [:turn {:window 10 :min 6 :threshold 1.0}]}
        z (fn [x past] (sg/z-against x past {:min 6}))
        z07 (z 70.0 [50.0 52.0 51.0 53.0 54.0 52.0 55.0])
        z08 (z 64.0 [50.0 52.0 51.0 53.0 54.0 52.0 55.0 70.0])]
    (is (>= z07 1.0) "2007 stretched")
    (is (< z08 z07) "2008 moved back")
    (testing "Dec 2009 reads 2008 against 2007: the size of the move back"
      (is (= (Math/abs (- z08 z07)) (sg/read-signal (assoc db ["AAA" "finance/credit"] (annual (assoc credit 2009 64.0))) d "AAA" (dec-origin 2009)))))
    (testing "Dec 2008 reads 2007, still rising: no turn"
      (is (= 0.0 (sg/read-signal db d "AAA" (dec-origin 2008)))))
    (testing "too short a history for either stretch: unknown"
      (is (nil? (sg/read-signal db d "AAA" (dec-origin 2005)))))))

(deftest a-node-is-assembled-from-declarations
  (let [decls [{:name :credit-s :var "finance/credit" :read [:stretch {:window 10 :min 6}] :domain :credit :sign 1 :prior [0.3 0.1]}
               {:name :world-p :var "prices/index" :unit "world" :read [:change 12] :domain :external :sign 1}
               {:name :credit-t :var "finance/credit" :read [:turn {:window 10 :min 6}] :domain :credit :sign 1 :prior [0.4 0.0625]}]
        {:keys [row names groups named-prior]} (sg/assemble db decls)]
    (is (= [:credit-s :world-p :credit-t] names))
    (is (= {:credit [0 2] :external [1]} groups))
    (is (= {:credit-s [0.3 0.1] :credit-t [0.4 0.0625]} named-prior))
    (is (= [(sg/read-signal db (decls 0) "AAA" (dec-origin 2008)) 11.0 0.0] (row "AAA" (dec-origin 2008))))
    (testing "declarations are checked"
      (is (thrown? Exception (sg/assemble db [{:name :a :var "x" :read [:value]} {:name :a :var "y" :read [:value]}])) "names unique")
      (is (thrown? Exception (sg/assemble db [{:name :a :var "x" :read [:median]}])) "known reads only")
      (is (thrown? Exception (sg/assemble db [{:name :a :read [:value]}])) "a source"))))
