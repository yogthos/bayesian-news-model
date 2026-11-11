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
  (testing "rigidity with an outflow's level unknown is nil"
    (let [r (assoc rec :outflows [{:var "debt-service" :rigid 1.0} {:var "imports" :rigid 0.5}])
          s {:flows {"debt-service" (obs [[5 10.0]])}}
          x (b/rigidity r s 5 {:windows [1]})]
      (is (nil? (:value x)))
      (is (contains? (:flags x) :missing-flow)))))

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
        (is (= [{:var "flows" :unit "matrix" :counterpart nil :from :ledger :source :inferred :net true}] (:inflows bsm))
            "a lagged read is a net flow: its direction is not known")
        (is (= 0 (get-in bsm [:measure :lag])))))
    (testing "a stock with no declared flows"
      (is (empty? (:inflows (by "table/country/capacity"))))
      (is (= 2 (get-in (by "table/country/capacity") [:measure :lag])) "the module's one known rule"))
    (testing "the report"
      (let [rep (b/report records)]
        (is (= 5 (:stocks rep)))
        (is (= #{"table/country/capacity"} (set (:no-flows rep))) "the information stock is not a buffer")
        (is (= {:tanks 3 :books 1 :table 1} (:by-module rep)))
        (is (= {:material 4 :information 1} (:by-kind rep)))))
    (is (= 2 (count levels)) "the level emits, measures of stocks")))

(deftest stock-unit-from-flows
  (let [m (sf/model {:stocks {:pool {:init 1.0}} :flows {:rain {:to :pool :rate (fn [_] 1.0) :unit "litres/year"}
                                                       :leak {:from :pool :rate (fn [_] 1.0) :unit "litres per year"}} :aux {}})
        {:keys [records]} (b/engine-stocks [{:module :pools :place :world :dt 1 :stocks {:pool {}} :emits []}] {:models {:pools m}})
        r (first records)]
    (is (= "litres" (:unit r)) "a stock with no unit takes the one its flows share, per time")
    (is (contains? (:flags r) :unit-from-flows))
    (is (not (contains? (:flags (first (b/cover r {:stock (obs [[0 1.0]])} 0 {:windows [1]}))) :flow-unit-mismatch)))))

(deftest mappings
  (let [ms (b/read-mappings "resources/buffers/stocks.edn")]
    (is (seq ms))
    (is (every? :id (map b/record (:records ms))) "every mapped record passes the schema")
    (testing "an extension adds flows to an engine stock by pattern"
      (let [r (b/record {:id "sfc-finance/USA/bsm" :name "bsm" :unit "u" :holder {:unit "sfc-finance" :place "USA"}})
            [r'] (b/apply-extensions [r] [{:match "sfc-finance/*/bsm" :usable {:share 1.0 :sd 0.0}}])]
        (is (= {:share 1.0 :sd 0.0} (:usable r')))))
    (testing "the engine's state variables are marked in data, not code"
      (let [mk (fn [id] (b/record {:id id :name "x" :unit "share" :holder {:unit "m" :place "USA"}}))
            rs (b/apply-extensions (mapv mk ["sfc-accounts/USA/w" "sfc-accounts/USA/lambda" "keen/country/omega"
                                             "technology/country/adoption" "food/country/stock"])
                                   (:extensions ms))]
        (is (= [:state :state :state :state :material] (mapv :kind rs)))))
    (testing "the PSD and EWN records are a revised vintage"
      (is (every? #(contains? (set (:flags %)) :revised-vintage)
                  (filter #(re-find #"^(grain|reserves)/" (:id %)) (:records ms)))))))

(deftest supply-inventories
  (let [rs (b/supply-records [{:region "DE" :product "C_GASE" :months "1.55" :year "2011" :known "2013" :basis "data" :source "GIE"}
                              {:region "CN" :product "C_COIL" :months "1.13" :year "2011" :known "2026" :basis "prior" :source "NBS"}])]
    (is (= 2 (count (:records rs))))
    (is (= "supply/DE/C_GASE" (:id (first (:records rs)))))
    (is (= "months of use" (:unit (first (:records rs)))))
    (is (= [{:t 2011 :v 1.55 :known 2013}] (get-in rs [:series "supply/DE/C_GASE" :stock])))
    (is (contains? (set (:flags (second (:records rs)))) :prior))))

;; -- review (bnm-56c.3): causality, units, definitions ----------------------------

(def full-rec
  (b/record {:id "test/X/all" :name "all" :unit "barrels" :time-unit "year"
             :holder {:unit "refiner" :place "X"}
             :outflows [{:var "a" :rigid 1.0 :rigid-sd 0.0} {:var "b" :rigid 0.2 :rigid-sd 0.0}]
             :measure {:frequency 1 :lag 1}
             :usable {:share 0.8 :sd 0.05} :restore {:time 2.0 :sd 0.5}
             :carry {:cost 0.02 :yield 0.05} :life 20.0 :claims {:rate 0.05 :maturity 10.0}
             :holders {"p" 2.0 "q" 1.0} :market {:ref-price 100.0 :ref-cover 0.3 :c 0.2}}))

(defn- ser [f] (obs (for [t (range 0 25)] [t (f t)])))

(def full-series
  {:stock (ser #(- 500.0 (* 7.0 %) (if (even? %) 3.0 0.0))) :inflow (ser #(+ 20.0 (mod % 3)))
   :outflow (ser #(+ 27.0 (mod % 2))) :flows {"a" (ser (constantly 5.0)) "b" (ser #(+ 20.0 %))}
   :usable (ser #(- 0.9 (* 0.001 %))) :claims (ser #(+ 300.0 %)) :income (ser #(+ 100.0 (mod % 4)))
   :price (ser #(+ 90.0 (mod % 5))) :idle (ser (constantly 0.1)) :leverage (ser #(+ 3.0 (* 0.1 %)))
   :restore (ser (constantly 1.5))})

(defn- perturb
  "Every series with values after `as-of` added: dated later, dated before
  but published later, and late revisions of values already there."
  [s as-of]
  (let [junk (fn [os] (into (vec os) (concat (obs [[(+ as-of 1) -9e9] [(+ as-of 2) 9e9]])
                                             [{:t (- as-of 0.5) :v 1e6 :known (+ as-of 0.5)}
                                              {:t (- as-of 3) :v -1e6 :known (+ as-of 1)}
                                              {:t as-of :v 0.0 :known (+ as-of 1e-6)}])))]
    (-> (into {} (for [[k v] s] [k (if (map? v) (update-vals v junk) (junk v))]))
        (update :flows assoc "c" (obs [[(+ as-of 1) 5.0]])))))

(deftest nothing-after-as-of-anywhere
  (let [as-of 20 opts {:windows [1 3 5]}
        a (b/invariants full-rec full-series as-of opts)
        p (b/invariants full-rec (perturb full-series as-of) as-of opts)]
    (is (some #(some? (:value %)) a))
    (is (= (set (map :invariant a))
           #{:cover :drain-rate :gross-cover :restoration :rigidity :carry :claim-gap :idle-share :holder-hhi
             :holder-leverage :market-reading :displacement :resonance :stock-x-flow}))
    (is (every? some? (map :value (filter #(#{:cover :drain-rate :rigidity :claim-gap :idle-share :market-reading :displacement}
                                             (:invariant %)) a)))
        "every invariant has a value, so the perturbation is tested on all")
    (is (= a p) "nothing published after the as-of date changes any row")
    (is (every? #(<= (or (:known %) 0) as-of) a))
    (testing "the boundary: known exactly at the as-of date is read, a moment later is not"
      (let [s {:stock (conj (vec (obs [[0 100.0] [1 90.0]])) {:t 2 :v 80.0 :known 3})}
            r (assoc rec :measure {:frequency 1 :lag 0})]
        (is (= 2 (:t (b/latest r (:stock s) 3))))
        (is (= 1 (:t (b/latest r (:stock s) 2.999))))))))

(deftest revisions
  (testing "two vintages of one date: the latest known by the as-of date, counted once"
    (let [s [{:t 0 :v 100.0 :known 0} {:t 1 :v 90.0 :known 1} {:t 1 :v 50.0 :known 3} {:t 1 :v 70.0 :known 2}]]
      (is (= [100.0 90.0] (mapv :v (b/known rec s 1))))
      (is (= [100.0 70.0] (mapv :v (b/known rec s 2))))
      (is (= [100.0 50.0] (mapv :v (b/known rec s 5)))))))

(deftest time-units
  (testing "the same stock measured monthly and yearly has the same cover in years"
    (let [yr (b/record (assoc rec :time-unit "year"))
          mo (b/record (assoc rec :time-unit "month" :id "test/X/tank-m"))
          ys {:stock (obs (for [t (range 0 6)] [t (- 100.0 (* 12.0 t))]))}
          ms {:stock (obs (for [t (range 0 61)] [t (- 100.0 (* 1.0 t))]))}
          cy (by-window (b/cover yr ys 5 {:windows [3]}) :cover 3)
          cm (by-window (b/cover mo ms 60 {:windows [3]}) :cover 3)]
      (is (close? (/ 40.0 12.0) (:value cy)))
      (is (close? (/ 40.0 12.0) (:value cm)) "40 months of cover is 3.33 years")
      (is (close? (/ 1.0 (/ 40.0 12.0)) (:value (by-window (b/cover mo ms 60 {:windows [3]}) :drain-rate 3)))
          "the drain rate is per year")
      (is (close? 100.0 (:exhaustion cm)) "the exhaustion date stays on the series' own axis")))
  (testing "carry and claim gap in years"
    (let [q (b/record (assoc rec :time-unit "quarter" :carry {:cost 0.01 :yield 0.02} :claims {:rate 0.01 :maturity 40.0}))]
      (is (close? 0.04 (:value (b/carry q {} 5 {}))) "0.01 a quarter is 0.04 a year")
      (is (close? 1.0 (:value (b/claim-gap q {:claims (obs [[5 100.0]]) :income (obs [[5 25.0]])} 5 {})))
          "four quarters of income is a year")
      (is (= :hedge (:band (b/claim-gap q {:claims (obs [[5 100.0]]) :income (obs [[5 25.0]])} 5 {}))))))
  (testing "an unknown time unit is refused"
    (is (thrown? clojure.lang.ExceptionInfo (b/record (assoc rec :time-unit "fortnight"))))))

(deftest flow-units
  (testing "a declared flow whose unit is not the stock's per time is flagged"
    (let [r (assoc rec :outflows [{:var "x" :unit "tonnes per year"}])
          c (by-window (b/cover r falling 5 {:windows [3]}) :cover 3)]
      (is (contains? (:flags c) :flow-unit-mismatch))
      (is (not (contains? (:flags (by-window (b/cover (assoc rec :outflows [{:var "x" :unit "barrels/year"}]) falling 5 {:windows [3]}) :cover 3))
                          :flow-unit-mismatch))))))

(deftest drawdown-sign-and-fallback
  (testing "more in than out is not draining, from the flows"
    (let [s {:stock (obs [[5 50.0]]) :outflow (obs (for [t (range 0 6)] [t 20.0])) :inflow (obs (for [t (range 0 6)] [t 30.0]))}
          rows (b/cover rec s 5 {:windows [3]})]
      (is (contains? (:flags (by-window rows :cover 3)) :not-draining))
      (is (close? -0.2 (:value (by-window rows :drain-rate 3))))))
  (testing "flows that stopped long ago: the stock's slope, not nothing"
    (let [s (assoc falling :outflow (obs [[5 30.0]]) :inflow (obs [[0 20.0]]))
          c (by-window (b/cover rec s 5 {:windows [1]}) :cover 1)]
      (is (close? 5.0 (:value c)))
      (is (= :stock (:drawdown-from c))))))

(deftest spreads-near-zero
  (testing "a drawdown of exactly zero keeps its spread in the drain rate"
    (let [s {:stock (obs [[5 100.0]]) :outflow (obs [[4 10.0 ] [5 12.0]]) :inflow (obs [[4 12.0] [5 10.0]])}
          d (by-window (b/cover rec s 5 {:windows [2]}) :drain-rate 2)]
      (is (close? 0.0 (:value d)))
      (is (pos? (:sd d)) "the drawdown is uncertain, so is its rate")))
  (testing "claims of zero still carry the claims' sd"
    (let [x (b/claim-gap rec {:claims [{:t 5 :v 0.0 :sd 10.0}] :income [{:t 5 :v 100.0 :sd 0.0}]} 5 {})]
      (is (close? 0.0 (:value x)))
      (is (close? 0.1 (:sd x))))))

(deftest restoration-continuous
  (testing "an empty stock has run out: restoration is not infinite"
    (let [s {:stock (obs (for [t (range 0 6)] [t (max 0.0 (- 20.0 (* 10.0 t)))]))}
          r (first (b/restoration (assoc rec :restore {:time 2.0 :sd 0.0}) s 5 {:windows [3]}))]
      (is (not (and (number? (:value r)) (Double/isInfinite (double (:value r))))))
      (is (contains? (:flags r) :empty))))
  (testing "past the cap the restoration is the time times the drain rate, not the time over the cap"
    (let [s {:stock (obs (for [t (range 0 6)] [t (- 1000.0 (* 1.0 t))]))}
          r (first (b/restoration (assoc rec :restore {:time 2.0 :sd 0.0}) s 5 {:windows [3] :cap 100.0}))]
      (is (close? (* 2.0 (/ 1.0 995.0)) (:value r))))))

(deftest rigidity-partial
  (testing "an outflow of unknown rigidity bounds the share: midpoint and a uniform spread"
    (let [r (assoc rec :outflows [{:var "debt-service" :rigid 1.0} {:var "imports"}])
          s {:flows {"debt-service" (obs [[5 10.0]]) "imports" (obs [[5 30.0]])}}
          x (b/rigidity r s 5 {:windows [1]})]
      (is (close? (+ 0.25 0.375) (:value x)) "between 0.25 (imports all cut) and 1")
      (is (close? (/ 0.75 (Math/sqrt 12.0)) (:sd x)))
      (is (contains? (:flags x) :partial-rigidity))))
  (testing "no outflow's rigidity known: nil"
    (let [r (assoc rec :outflows [{:var "imports"}])
          x (b/rigidity r {:flows {"imports" (obs [[5 30.0]])}} 5 {:windows [1]})]
      (is (nil? (:value x)))
      (is (contains? (:flags x) :missing-rigidity)))))

(deftest guards
  (testing "a zero rate: never Ponzi, no division error"
    (let [at (fn [c] (:band (b/claim-gap (assoc rec :claims {:rate 0.0 :maturity 10.0}) {:claims (obs [[5 c]]) :income (obs [[5 100.0]])} 5 {})))]
      (is (= :hedge (at 300.0)) "principal over ten years: 30 a year from 100")
      (is (= :speculative (at 3000.0)))))
  (testing "a maturity of zero is not a term"
    (is (contains? (:flags (b/claim-gap (assoc rec :claims {:rate 0.05 :maturity 0}) {:claims (obs [[5 300.0]]) :income (obs [[5 100.0]])} 5 {}))
                   :missing-terms)))
  (testing "claims and income of different dates are flagged"
    (is (contains? (:flags (b/claim-gap rec {:claims (obs [[5 300.0]]) :income (obs [[3 100.0]])} 5 {})) :dates-differ)))
  (testing "a price at or below zero has no reading"
    (let [x (b/market-reading (assoc rec :market {:ref-price 100.0 :ref-cover 0.3 :c 0.2}) {:price (obs [[5 0.0]])} 5 {})]
      (is (nil? (:value x)))
      (is (contains? (:flags x) :bad-price)))))

(deftest record-flags-reach-rows
  (let [r (assoc rec :flags #{:revised-vintage})]
    (is (every? #(contains? (:flags %) :revised-vintage) (b/invariants r falling 5 {:windows [3]})))))

(deftest not-a-buffer
  (testing "an information or state stock gets no buffer values, only its displacement"
    (doseq [k [:information :state]]
      (let [rows (b/invariants (assoc rec :kind k) falling 5 {:windows [3]})
            buf (remove #(#{:displacement :resonance :stock-x-flow} (:invariant %)) rows)]
        (is (seq buf))
        (is (every? #(nil? (:value %)) buf))
        (is (every? #(contains? (:flags %) :not-a-buffer) buf))))))

(deftest supply-units
  (let [{:keys [records]} (b/supply-records [{:region "DE" :product "C_GASE" :months "1.55" :year "2011" :known "2013" :basis "data" :source "GIE"}])]
    (is (= "year" (:time-unit (first records))) "t is a year")))

(deftest gross-cover-against-gross-use
  (let [s {:stock (obs [[0 100.0] [1 90.0] [2 80.0]])
           :outflow (obs [[1 40.0] [2 60.0]])}]
    (testing "usable stock over the mean gross outflow in the window, in years"
      (is (close? (/ 80.0 60.0) (:value (b/gross-cover rec s 2 {:windows [1]}))))
      (is (close? (/ 80.0 50.0) (:value (b/gross-cover rec s 2 {:windows [2]})))))
    (testing "the usable share applies"
      (is (close? (/ 40.0 60.0) (:value (b/gross-cover (assoc rec :usable {:share 0.5 :sd 0.0}) s 2 {:windows [1]})))))
    (testing "a monthly record's outflow is per month: years are its months over 12"
      (let [m (assoc rec :time-unit "month")]
        (is (close? (/ 80.0 50.0 12.0) (:value (b/gross-cover m s 2 {:windows [1]}))) "a 1-year window is 12 months: both outflows")))
    (testing "no outflow: nil, flagged, never zero"
      (let [r (b/gross-cover rec (dissoc s :outflow) 2 {:windows [1]})]
        (is (nil? (:value r)))
        (is (contains? (:flags r) :missing-outflow))))
    (testing "nothing after the as-of date"
      (is (close? (/ 90.0 40.0) (:value (b/gross-cover rec s 1 {:windows [1]})))))))

(deftest displacement-of-a-named-series
  (let [jump (obs (for [t (range 0 30)] [t (if (< t 29) (+ 10.0 (if (even? t) 0.1 -0.1)) 20.0)]))
        r (assoc rec :displace :claims)
        rows (b/displacement r {:claims jump} 29 {:disp-windows [5 10 20]})
        d (first (filter #(= :displacement (:invariant %)) rows))]
    (is (pos? (:value d)) "the record's :displace series is the one measured")
    (is (nil? (:value (first (filter #(= :displacement (:invariant %)) (b/displacement rec {:claims jump} 29 {}))))))))

(deftest rigidity-from-a-series
  (testing "a record with no outflows reads its latest known :rigidity observation"
    (let [r (b/rigidity rec {:rigidity (obs [[0 0.1] [1 0.2] [2 0.3]])} 1 {})]
      (is (close? 0.2 (:value r)))
      (is (close? 1.0 (:known r))))))
