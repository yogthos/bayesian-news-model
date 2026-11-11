(ns bnm.buffers-test
  (:require [bnm.buffers :as b]
            [bnm.stockflow :as sf]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (and (some? a) (some? b) (< (Math/abs (- (double a) (double b))) 1e-9)))

(def rec
  (b/record {:id "test/X/tank" :name "tank" :unit "barrels" :time-unit "year"
             :holder {:unit "refiner" :place "X"}
             :measure {:frequency 1 :lag 0}
             :usable {:share 1.0 :sd 0.0}}))

(defn- obs [pairs] (mapv (fn [[t v]] {:t t :v v}) pairs))

(def falling {:stock (obs (for [t (range 0 6)] [t (- 100.0 (* 10.0 t))]))})

(defn- by-window [rows inv w] (first (filter #(and (= inv (:invariant %)) (= w (:window %))) rows)))

(deftest record-schema
  (testing "defaults and the fields every record carries"
    (is (= "tank" (:name rec)))
    (is (= [] (:inflows rec)))
    (is (= [] (:outflows rec))))
  (testing "a record without a name, unit or holder is refused"
    (is (thrown? clojure.lang.ExceptionInfo (b/record {:id "a" :unit "t" :holder {:unit "u" :place "X"}})))
    (is (thrown? clojure.lang.ExceptionInfo (b/record {:id "a" :name "a" :holder {:unit "u" :place "X"}})))
    (is (thrown? clojure.lang.ExceptionInfo (b/record {:id "a" :name "a" :unit "t"}))))
  (testing "a usable share outside [0, 1] is refused"
    (is (thrown? clojure.lang.ExceptionInfo (b/record (assoc rec :usable {:share 1.5}))))))

(deftest known-by
  (testing "an observation is known at :known, else at t plus the record's lag"
    (let [r (assoc-in rec [:measure :lag] 1)]
      (is (= [0 1 2 3 4] (mapv :t (b/known r (:stock falling) 5))))
      (is (= [0 1] (mapv :t (b/known r [{:t 0 :v 1.0} {:t 1 :v 1.0 :known 2} {:t 2 :v 1.0 :known 9}] 5))))))
  (testing "no lag declared: known at t, flagged"
    (let [r (update rec :measure dissoc :lag)]
      (is (contains? (b/lag-flags r) :lag-assumed-zero)))))

(deftest constant-drawdown
  (let [rows (b/cover rec falling 5 {:windows [1 3 5]})]
    (doseq [w [1 3 5] :let [c (by-window rows :cover w)]]
      (testing (str "window " w)
        (is (close? 5.0 (:value c)) "50 left, drawn 10 a year")
        (is (close? 10.0 (:exhaustion c)) "the exact exhaustion date")
        (is (close? 0.0 (:sd c)))
        (is (= 5 (:known c)))
        (is (empty? (:flags c)))))
    (testing "the drain rate is the inverse, per time unit"
      (is (close? 0.2 (:value (by-window rows :drain-rate 3))))))
  (testing "from the flows when both are measured: out 30, in 20"
    (let [s {:stock (obs [[5 50.0]])
             :outflow (obs (for [t (range 0 6)] [t 30.0]))
             :inflow (obs (for [t (range 0 6)] [t 20.0]))}
          c (by-window (b/cover rec s 5 {:windows [3]}) :cover 3)]
      (is (close? 5.0 (:value c)))
      (is (= :flows (:drawdown-from c))))))

(deftest growing-stock
  (let [s {:stock (obs (for [t (range 0 6)] [t (+ 50.0 (* 10.0 t))]))}
        rows (b/cover rec s 5 {:windows [1 3] :cap 100.0})
        c (by-window rows :cover 3)]
    (is (close? 100.0 (:value c)) "long cover, at the cap, never negative")
    (is (contains? (:flags c) :not-draining))
    (is (nil? (:exhaustion c)))
    (is (neg? (:value (by-window rows :drain-rate 3))) "the drain rate carries the sign")))

(deftest empty-stock
  (testing "an empty stock has no cover, drawn or not: never the long-cover cap"
    (let [s {:stock (obs (for [t (range 0 6)] [t 0.0]))}
          rows (b/cover rec s 5 {:windows [3] :cap 100.0})
          c (by-window rows :cover 3)]
      (is (close? 0.0 (:value c)))
      (is (contains? (:flags c) :empty))
      (is (nil? (:value (by-window rows :drain-rate 3))))
      (is (contains? (:flags (by-window rows :drain-rate 3)) :empty)))))

(deftest unusable-share
  (let [r (assoc rec :usable {:share 0.5 :sd 0.1})
        c (by-window (b/cover r falling 5 {:windows [3]}) :cover 3)]
    (is (close? 2.5 (:value c)))
    (is (close? 7.5 (:exhaustion c)))
    (is (close? (* 2.5 0.2) (:sd c)) "the usable share's relative spread carries over"))
  (testing "a series of the usable share: the latest known wins"
    (let [s (assoc falling :usable [{:t 2 :v 0.5} {:t 5 :v 0.03 :known 6}])
          c (by-window (b/cover rec s 5 {:windows [3]}) :cover 3)]
      (is (close? 2.5 (:value c)) "the 0.03 is not known until 6")))
  (testing "no usable share: headline used and flagged, never zero"
    (let [c (by-window (b/cover (dissoc rec :usable) falling 5 {:windows [3]}) :cover 3)]
      (is (close? 5.0 (:value c)))
      (is (contains? (:flags c) :usable-missing)))))

(deftest restoration-beyond-cover
  (let [slow (b/restoration (assoc rec :restore {:time 8.0 :sd 0.0}) falling 5 {:windows [3]})
        fast (b/restoration (assoc rec :restore {:time 2.0 :sd 0.0}) falling 5 {:windows [3]})]
    (is (close? 1.6 (:value (first slow))))
    (is (contains? (:flags (first slow)) :runs-out-first))
    (is (close? 0.4 (:value (first fast))))
    (is (not (contains? (:flags (first fast)) :runs-out-first))))
  (testing "no restoration time: nil and flagged"
    (let [r (first (b/restoration rec falling 5 {:windows [3]}))]
      (is (nil? (:value r)))
      (is (contains? (:flags r) :missing-restore)))))

(deftest nothing-after-as-of
  (let [future-rows (into (:stock falling) (obs [[6 1000.0] [7 -5.0] [8 0.0]]))
        late (conj (:stock falling) {:t 4.5 :v 0.0 :known 6})
        a (b/invariants rec falling 5 {:windows [1 3 5]})
        b1 (b/invariants rec {:stock future-rows} 5 {:windows [1 3 5]})
        b2 (b/invariants rec {:stock late} 5 {:windows [1 3 5]})]
    (is (= a b1) "values after the as-of date change nothing")
    (is (= a b2) "a value dated before but published after changes nothing")
    (is (every? #(<= (or (:known %) 0) 5) a)))
  (testing "a lag moves the last known value back"
    (let [c (by-window (b/cover (assoc-in rec [:measure :lag] 1) falling 5 {:windows [3]}) :cover 3)]
      (is (close? 6.0 (:value c)))
      (is (close? 10.0 (:exhaustion c)))
      (is (= 5 (:known c))))))

(deftest missing-is-not-zero
  (let [rows (b/invariants rec {} 5 {:windows [3]})]
    (is (seq rows))
    (is (every? #(nil? (:value %)) rows) "no stock, no values")
    (is (every? #(seq (:flags %)) rows)))
  (testing "rigidity with an outflow of unknown rigidity is nil"
    (let [r (assoc rec :outflows [{:var "debt-service" :rigid 1.0} {:var "imports"}])
          s {:flows {"debt-service" (obs [[5 10.0]]) "imports" (obs [[5 30.0]])}}
          x (b/rigidity r s 5 {:windows [1]})]
      (is (nil? (:value x)))
      (is (contains? (:flags x) :missing-rigidity)))))

(deftest rigidity-share
  (let [r (assoc rec :outflows [{:var "debt-service" :rigid 1.0 :rigid-sd 0.0} {:var "imports" :rigid 0.5 :rigid-sd 0.0}])
        s {:flows {"debt-service" (obs [[4 10.0] [5 10.0]]) "imports" (obs [[4 30.0] [5 30.0]])}}
        x (b/rigidity r s 5 {:windows [2]})]
    (is (close? (/ 25.0 40.0) (:value x)))
    (is (close? 0.0 (:sd x)))))

(deftest claim-gap-bands
  (let [r (assoc rec :claims {:rate 0.05 :maturity 10.0})
        at (fn [c] (b/claim-gap r {:claims (obs [[5 c]]) :income (obs [[5 100.0]])} 5 {}))]
    (is (close? 3.0 (:value (at 300.0))))
    (is (= :hedge (:band (at 300.0))) "income covers interest and principal: 300 x 0.15 = 45")
    (is (= :speculative (:band (at 1000.0))) "covers the interest only: 50 < 100 < 150")
    (is (= :ponzi (:band (at 2500.0))) "covers neither: interest 125")
    (testing "no rate: the ratio without a band, flagged"
      (let [x (b/claim-gap rec {:claims (obs [[5 300.0]]) :income (obs [[5 100.0]])} 5 {})]
        (is (close? 3.0 (:value x)))
        (is (nil? (:band x)))
        (is (contains? (:flags x) :missing-terms))))))

(deftest carry-idle-holder-market
  (testing "carry: earns 0.04, loses 0.01 funding and 0.05 decay (a 20-year life)"
    (let [x (b/carry (assoc rec :carry {:cost 0.01 :yield 0.04} :life 20.0) {} 5 {})]
      (is (close? -0.02 (:value x)))))
  (testing "carry with no yield is nil, not a loss"
    (is (nil? (:value (b/carry (assoc rec :carry {:cost 0.01}) {} 5 {})))))
  (testing "idle share and its binding complement"
    (let [x (b/idle-share (assoc rec :idle {:share 0.3 :sd 0.05 :binding "grid connection"}) {} 5 {})]
      (is (close? 0.3 (:value x)))
      (is (= "grid connection" (:binding x)))))
  (testing "holder concentration (HHI of shares) and leverage"
    (let [[h l] (b/holder (assoc rec :holders {"a" 3.0 "b" 1.0} :leverage {:value 12.0 :sd 2.0}) {} 5 {})]
      (is (close? (+ 0.5625 0.0625) (:value h)))
      (is (close? 12.0 (:value l)))))
  (testing "market reading: the cover a price implies (competitive storage, bnm.food's form)"
    (let [r (assoc rec :market {:ref-price 100.0 :ref-cover 0.3 :c 0.2})
          at (fn [p] (b/market-reading r {:price (obs [[5 p]])} 5 {}))]
      (is (close? 0.3 (:value (at 100.0))))
      (is (close? 0.15 (:value (at (* 100.0 (/ 1.2 0.7)))))))))

(deftest dimension-free-measures
  (let [s {:stock (obs (for [t (range 0 30)] [t (if (< t 29) (+ 100.0 (if (even? t) 1.0 -1.0)) 50.0)]))}
        rows (b/displacement rec s 29 {:disp-windows [5 10 20]})
        d (first (filter #(= :displacement (:invariant %)) rows))]
    (is (neg? (:value d)) "the stock fell far below its own past")
    (is (some #(= :resonance (:invariant %)) rows))
    (is (some #(= :stock-x-flow (:invariant %)) rows)))
  (testing "breadth across stocks at one date"
    (let [rows [{:invariant :displacement :value 2.0} {:invariant :displacement :value -0.5}
                {:invariant :displacement :value nil} {:invariant :displacement :value 3.0}]]
      (is (close? (/ 2.0 3.0) (:value (b/breadth rows {:threshold 1.5 :min-n 2})))))))

(deftest flat-rows
  (let [rows (b/invariants rec falling 5 {:windows [1 3]})]
    (is (every? #(every? (partial contains? %) [:stock :holder :place :date :invariant :value :sd :known :flags]) rows))
    (is (every? #(= "test/X/tank" (:stock %)) rows))
    (is (every? #(= 5 (:date %)) rows))))

;; -- the engine's graph ---------------------------------------------------------

(def tank-model
  (sf/model {:stocks {:tank {:init 10.0 :unit "barrels"} :ash {:init 0.0 :unit "barrels"} :belief {:init 1.0 :info true}}
             :flows {:fill {:to :tank :rate (fn [_] 1.0) :unit "barrels/year"}
                     :burn {:from :tank :to :ash :rate (fn [_] 2.0) :unit "barrels/year"}}
             :aux {}}))

(def modules
  [{:module :tanks :group :W :place :world :dt 0.5
    :stocks {:tank {:unit "barrels"} :ash {:unit "barrels"} :belief {:unit "index" :info true}}
    :emits [{:var :tank :unit "barrels" :phase :level} {:var :fill :unit "barrels/year"} {:var :burn :unit "barrels/year"}]}
   {:module :books :label "books" :group :F :place :USA :dt 1
    :stocks {:bsm {:unit "matrix"}}
    :reads [{:var "flows" :unit "matrix" :lag 1 :phase :level :from :ledger}]
    :emits [{:var "bsm" :unit "matrix" :phase :level :known {:data-year 0}}]}
   {:module :ledger :group :D :place :USA :dt 1 :stocks {}
    :emits [{:var "flows" :unit "matrix" :known {:data-year 0}}]}
   {:module :table :group :R :place :country :dt 1 :table "x.csv" :stocks {:capacity {:unit "units"}}
    :emits [{:var "table/*" :unit "mixed" :known {:data-year 2}}]}])

(deftest engine-stocks
  (let [{:keys [records levels]} (b/engine-stocks modules {:models {:tanks tank-model}})
        by (into {} (map (juxt :id identity)) records)
        tank (by "tanks/world/tank")]
    (testing "a stock-flow model's flows attach to their stocks, with counterparts"
      (is (= [{:var :fill :unit "barrels/year" :counterpart nil :source :model}] (:inflows tank)))
      (is (= [{:var :burn :unit "barrels/year" :counterpart "tanks/world/ash" :source :model}] (:outflows tank)))
      (is (= :material (:kind tank)))
      (is (= :information (:kind (by "tanks/world/belief")))))
    (testing "a lagged level read of another module's rate is an inflow, inferred"
      (let [bsm (by "books/USA/bsm")]
        (is (= [{:var "flows" :unit "matrix" :counterpart nil :from :ledger :source :inferred}] (:inflows bsm)))
        (is (= 0 (get-in bsm [:measure :lag])))))
    (testing "a stock with no declared flows"
      (is (empty? (:inflows (by "table/country/capacity"))))
      (is (= 2 (get-in (by "table/country/capacity") [:measure :lag])) "the module's one known rule"))
    (testing "the report"
      (let [rep (b/report records)]
        (is (= 5 (:stocks rep)))
        (is (= #{"table/country/capacity"} (set (:no-flows rep))) "the information stock is not a buffer")
        (is (= {:tanks 3 :books 1 :table 1} (:by-module rep)))))
    (is (= 2 (count levels)) "the level emits, measures of stocks")))

(deftest mappings
  (let [ms (b/read-mappings "resources/buffers/stocks.edn")]
    (is (seq ms))
    (is (every? :id (map b/record (:records ms))) "every mapped record passes the schema")
    (testing "an extension adds flows to an engine stock by pattern"
      (let [r (b/record {:id "sfc-finance/USA/bsm" :name "bsm" :unit "u" :holder {:unit "sfc-finance" :place "USA"}})
            [r'] (b/apply-extensions [r] [{:match "sfc-finance/*/bsm" :usable {:share 1.0 :sd 0.0}}])]
        (is (= {:share 1.0 :sd 0.0} (:usable r')))))))

(deftest supply-inventories
  (let [rs (b/supply-records [{:region "DE" :product "C_GASE" :months "1.55" :year "2011" :known "2013" :basis "data" :source "GIE"}
                              {:region "CN" :product "C_COIL" :months "1.13" :year "2011" :known "2026" :basis "prior" :source "NBS"}])]
    (is (= 2 (count (:records rs))))
    (is (= "supply/DE/C_GASE" (:id (first (:records rs)))))
    (is (= "months of use" (:unit (first (:records rs)))))
    (is (= [{:t 2011 :v 1.55 :known 2013}] (get-in rs [:series "supply/DE/C_GASE" :stock])))
    (is (contains? (set (:flags (second (:records rs)))) :prior))))
