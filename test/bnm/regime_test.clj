(ns bnm.regime-test
  "The break as a regime switch (docs/PLAN.md 1.3; bead bnm-56c.4): a
  stock the engine holds gives each outflow the lesser of what is wanted
  and what it can supply; a cut outflow is a cut inflow downstream."
  (:require [bnm.buffers :as buf]
            [bnm.coupled :as coupled]
            [bnm.engine :as eng]
            [bnm.modules.held :as held]
            [bnm.modules.world3 :as mw]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-10))

(defn- holder
  "A module holding one stock `k` (level var <id>/<k>) that wants constant
  outflows `wants` {var rate}, reads `reads`, and emits `extra` {var rate}
  of its own each step."
  [id group k {:keys [init floor sharing inflows outflows reads wants extra]}]
  (let [lv (str (name id) "/" (name k))]
    {:module id :group group :place :world :dt 1
     :reads (or reads [])
     :emits (into [{:var lv :unit "u" :phase :level}]
                  (for [v (distinct (concat (keys wants) (keys extra)))] {:var v :unit "u/year"}))
     :stocks {k (cond-> {:unit "u" :level lv :init init :inflows (or inflows []) :outflows outflows}
                  floor (assoc :floor floor) sharing (assoc :sharing sharing))}
     :step (fn [s _ _ _ _] {:state s :emits (merge wants extra)})}))

(defn- recs [r id k] (get-in r [:regimes [id k]]))

;; -- the declarations ------------------------------------------------------------

(deftest a-held-stock-is-declared-as-data
  (let [ok (holder :a :W :s {:init 10.0 :outflows [{:var "a/out"}] :wants {"a/out" 1.0}})
        errs (fn [m] (set (filter #{:holdings} (map :error (eng/validate [m])))))]
    (is (empty? (eng/validate [ok])) "no :observe needed: the engine gives the level")
    (is (= [[:s (get-in ok [:stocks :s])]] (eng/held-stocks ok)))
    (is (= #{:holdings} (errs (assoc-in ok [:stocks :s :level] "a/none"))) "the level must be a declared :level emit")
    (is (= #{:holdings} (errs (assoc-in ok [:stocks :s :outflows] [{:var "b/out"}]))) "an outflow is the module's own emit")
    (is (= #{:holdings} (errs (assoc-in ok [:stocks :s :inflows] [{:var "z/in"}]))) "an inflow is an emit or a read")
    (is (= #{:holdings} (errs (assoc-in ok [:stocks :s :sharing] :alphabetical))))
    (is (= #{:holdings} (errs (assoc-in ok [:stocks :s :outflows 0 :shortfall] :vanish))) "a cut must land somewhere known")
    (is (= #{:holdings} (errs (assoc-in ok [:stocks :s :outflows 0 :rigid] 1.5))))
    (is (= #{:holdings} (errs (assoc-in ok [:stocks :s :floor] -1.0))))
    (is (= #{:holdings} (errs (update-in ok [:stocks :s] dissoc :init))))))

(deftest the-share-of-a-cut-is-data
  (let [ws [{:wanted 4.0 :rigid 1.0} {:wanted 8.0 :rigid 0.0}]]
    (is (= [4.0 8.0] (eng/share-cut :pro-rata ws 12.0)) "enough: nothing cut")
    (is (every? true? (map close? [2.0 4.0] (eng/share-cut :pro-rata ws 6.0))) "pro rata: each by half")
    (is (every? true? (map close? [4.0 2.0] (eng/share-cut :rigid-first ws 6.0))) "the rigid one first")
    (is (every? true? (map close? [3.0 0.0] (eng/share-cut :rigid-first ws 3.0))) "short of the rigid part: it is cut too")
    (is (= [0.0 0.0] (eng/share-cut :rigid-first ws 0.0)))
    (is (every? true? (map close? [(+ 2.0 (/ 2.0 3)) (+ 4.0 (/ 4.0 3))] (eng/share-cut :rigid-first [{:wanted 4.0 :rigid 0.5} {:wanted 8.0 :rigid 0.5}] 8.0)))
        "a part-rigid flow: its rigid share first, then the flexible parts pro rata")))

;; -- demonstrations ------------------------------------------------------------------

(deftest a-stock-drained-to-its-floor-switches-regime
  ;; 10 at the start, 1 in and 3 wanted out a year, floor 2: 10 8 6 4 2, and
  ;; at 2 only 2 + 1 - 2 = 1 can go
  (let [a (holder :a :W :s {:init 10.0 :floor 2.0 :inflows [{:var "a/in"}] :outflows [{:var "a/out"}]
                            :wants {"a/out" 3.0} :extra {"a/in" 1.0}})
        r (eng/run [a] {:from 2000 :to 2006})
        rs (recs r :a :s)]
    (is (= [10.0 8.0 6.0 4.0 2.0 2.0 2.0] (get-in r [:emits :a "a/s"])) "the level, held at the floor once there")
    (is (= [:buffered :buffered :buffered :buffered :constrained :constrained :constrained] (map :regime rs)))
    (is (= 2004 (:t (first (filter #(= :constrained (:regime %)) rs)))) "the switch on the right step")
    (is (= [3.0 3.0 3.0 3.0 1.0 1.0 1.0] (get-in r [:emits :a "a/out"])) "readers see what was delivered")
    (let [x (nth rs 4)]
      (is (= {:wanted 3.0 :delivered 1.0 :shortfall 2.0 :cut ["a/out"]} (select-keys x [:wanted :delivered :shortfall :cut])))
      (is (= {:wanted 3.0 :delivered 1.0 :shortfall 2.0 :booked :unmet-demand :amount 2.0} (get-in x [:flows "a/out"]))
          "an undeclared booking is unmet demand"))
    (testing "the default run is untouched: a module holding no stock leaves no record"
      (is (not (contains? (eng/run [(-> (dissoc a :stocks) (update :emits #(vec (rest %))))] {:from 2000 :to 2001}) :regimes))))))

(defn- chain
  "A gives B gives C, each wanting 5 a year out, B and C reading their
  inflow at `lag`; A starts with 12 and gets nothing; B and C start
  with `start`."
  [lag start]
  [(holder :a :W :s {:init 12.0 :outflows [{:var "a/out" :counterpart "b/world/s"}] :wants {"a/out" 5.0}})
   (holder :b :F :s {:init start :reads [{:var "a/out" :unit "u/year" :lag lag}]
                     :inflows [{:var "a/out"}] :outflows [{:var "b/out" :counterpart "c/world/s"}] :wants {"b/out" 5.0}})
   (holder :c :R :s {:init start :reads [{:var "b/out" :unit "u/year" :lag lag}]
                     :inflows [{:var "b/out"}] :outflows [{:var "c/out"}] :wants {"c/out" 5.0}})])

(defn- switch-at [r id] (:t (first (filter #(= :constrained (:regime %)) (recs r id :s)))))

(deftest exhausting-one-stock-cascades-down-the-chain
  (testing "read a year late: the cut reaches each a year after the one before"
    (let [r (eng/run (chain 1 5.0) {:from 2000 :to 2006})]
      (is (= [2002 2003 2004] (map #(switch-at r %) [:a :b :c])))
      (is (= [5.0 5.0 2.0 0.0 0.0 0.0 0.0] (get-in r [:emits :a "a/out"])))
      (is (= [5.0 5.0 5.0 2.0 0.0 0.0 0.0] (get-in r [:emits :b "b/out"])))
      (is (= [5.0 5.0 5.0 5.0 2.0 0.0 0.0] (get-in r [:emits :c "c/out"])))))
  (testing "read the same year: the whole chain switches in one step"
    (let [r (eng/run (chain 0 0.0) {:from 2000 :to 2004})]
      (is (= [2002 2002 2002] (map #(switch-at r %) [:a :b :c])))
      (is (= [5.0 5.0 2.0 0.0 0.0] (get-in r [:emits :c "c/out"])))))
  (testing "no chain is written: the modules name only their own flows"
    (is (every? (fn [m] (every? #(= (name (:module m)) (first (clojure.string/split (:var %) #"/")))
                                (mapcat :outflows (vals (:stocks m)))))
                (chain 1 5.0)))))

(deftest a-rigid-outflow-is-served-first
  (let [mk (fn [sharing] (holder :a :W :s {:init 10.0 :sharing sharing
                                          :outflows [{:var "a/debt-service" :rigid 1.0 :shortfall :arrears :counterpart "row/world/claims"}
                                                     {:var "a/imports" :rigid 0.0 :shortfall :unmet-demand}]
                                          :wants {"a/debt-service" 4.0 "a/imports" 8.0}}))]
    (let [x (first (recs (eng/run [(mk :rigid-first)] {:from 2000 :to 2000}) :a :s))]
      (is (= :constrained (:regime x)))
      (is (= ["a/imports"] (:cut x)) "only the flexible one")
      (is (close? 4.0 (get-in x [:flows "a/debt-service" :delivered])))
      (is (close? 6.0 (get-in x [:flows "a/imports" :delivered]))))
    (let [r (eng/run [(mk :pro-rata)] {:from 2000 :to 2001})
          x (first (recs r :a :s))]
      (is (= #{"a/debt-service" "a/imports"} (set (:cut x))) "pro rata cuts both")
      (is (close? (/ 40.0 12.0) (get-in x [:flows "a/debt-service" :delivered])))
      (is (= :arrears (get-in x [:flows "a/debt-service" :booked])))
      (is (close? (+ (- 4.0 (/ 40.0 12.0)) 4.0) (get-in r [:holdings :books [:a :s "a/debt-service" :arrears]]))
          "arrears accumulate: the second year nothing is left to pay"))))

(deftest nothing-after-the-step-is-read
  ;; wanted outflows and the floor come from a series; changing the series
  ;; after 2003 must leave every record through 2003 as it was
  (let [mk (fn [ser] (held/series-module {:module :x :place :world :stock :s :unit "u"
                                         :init 10.0 :floor "floor"
                                         :inflows [{:var "in"}] :outflows [{:var "out" :rigid 0.0}]
                                         :series ser}))
        base {"in" {2000 1.0 2001 1.0 2002 1.0 2003 1.0 2004 1.0 2005 1.0}
              "out" {2000 3.0 2001 4.0 2002 5.0 2003 2.0 2004 3.0 2005 3.0}
              "floor" {2000 1.0 2001 1.0 2002 1.0 2003 1.0 2004 1.0 2005 1.0}}
        later (-> base (assoc-in ["out" 2004] 50.0) (assoc-in ["in" 2005] -9.0) (assoc-in ["floor" 2004] 8.0))
        a (eng/run [(mk base)] {:from 2000 :to 2005})
        b (eng/run [(mk later)] {:from 2000 :to 2005})
        upto (fn [r] (take-while #(<= (:t %) 2003) (recs r :x :s)))]
    (is (= 4 (count (upto a))))
    (is (= (upto a) (upto b)))
    (is (not= (recs a :x :s) (recs b :x :s)) "the change after 2003 is seen from 2004")
    (testing "a lagged inflow is last step's delivered value, never this step's"
      (let [r (eng/run (chain 1 5.0) {:from 2000 :to 2003})]
        (is (= [0.0 5.0 5.0 2.0] (map :inflow (recs r :b :s))))))))

;; -- the accounts close with cuts active -------------------------------------------------

(deftest the-accounts-close-with-cuts
  ;; a closed economy: households pay firms, firms pay households and the
  ;; rest of the world, which pays households back a year later; each wants
  ;; more than it has, so cuts bind; nothing comes from or goes outside
  (let [hh (holder :hh :W :deposits {:init 5.0 :sharing :rigid-first
                                     :reads [{:var "firm/wages" :unit "u/year"} {:var "row/remit" :unit "u/year" :lag 1}]
                                     :inflows [{:var "firm/wages"} {:var "row/remit"}]
                                     :outflows [{:var "hh/spend" :counterpart "firm/world/cash" :rigid 0.2 :shortfall :forced-saving}]
                                     :wants {"hh/spend" 9.0}})
        firm (holder :firm :F :cash {:init 3.0 :sharing :rigid-first
                                     :reads [{:var "hh/spend" :unit "u/year"}]
                                     :inflows [{:var "hh/spend"}]
                                     :outflows [{:var "firm/wages" :counterpart "hh/world/deposits" :rigid 0.6 :shortfall :arrears}
                                                {:var "firm/imports" :counterpart "row/world/reserves" :rigid 0.0 :shortfall :price}]
                                     :wants {"firm/wages" 6.0 "firm/imports" 4.0}})
        row (holder :row :R :reserves {:init 1.0
                                       :reads [{:var "firm/imports" :unit "u/year"}]
                                       :inflows [{:var "firm/imports"}]
                                       :outflows [{:var "row/remit" :counterpart "hh/world/deposits"}]
                                       :wants {"row/remit" 3.0}})
        ;; hh reads firm's wages within the year, but firm reads hh's spending
        ;; within the year too: a cycle, so hh reads the wages a year late
        hh (update hh :reads (fn [rs] (mapv #(if (= "firm/wages" (:var %)) (assoc % :lag 1) %) rs)))
        r (eng/run [hh firm row] {:from 2000 :to 2010})
        all (for [[k rs] (:regimes r) x rs] (assoc x :key k))
        by-t (group-by :t all)
        ;; what was sent at t on a lagged edge arrives at t + 1: in transit
        transit (fn [t] (reduce + 0.0 (for [x (by-t t) [v f] (:flows x) :when (#{"firm/wages" "row/remit"} v)] (:delivered f))))]
    (is (some #(= :constrained (:regime %)) all) "cuts are active")
    (testing "each stock integrates what was delivered"
      (doseq [x all] (is (close? (:next x) (+ (:level x) (- (:inflow x) (:delivered x)))) (pr-str (:key x) (:t x)))))
    (testing "what was wanted is delivered or booked"
      (doseq [x all [_ f] (:flows x)] (is (close? (:wanted f) (+ (:delivered f) (:shortfall f))))))
    (testing "money is neither made nor lost by a cut: stocks plus what is in transit stay at 9"
      (doseq [t (range 2000 2011)]
        (is (close? 9.0 (+ (reduce + (map :next (by-t t))) (transit t))) (str t))))
    (testing "the bookings: arrears a claim of households on firms, the rest recorded"
      (let [books (get-in r [:holdings :books])]
        (is (pos? (books [:firm :cash "firm/wages" :arrears] 0.0)))
        (is (close? (reduce + (for [x all [v f] (:flows x) :when (= "firm/wages" v)] (:amount f)))
                    (books [:firm :cash "firm/wages" :arrears])))))))

;; -- the default run, and the real demonstration's builder -----------------------------------

(deftest the-golden-modules-hold-no-stock
  ;; the constraint is always on for a stock that declares outflows; the
  ;; golden runs' modules declare none, so it cannot bind there
  (let [ms (mw/modules)]
    (is (every? empty? (map eng/held-stocks ms)))
    (is (not (contains? (eng/run ms {:from 1900.0 :to 1902.0}) :regimes)))))

(deftest a-buffer-record-becomes-a-held-stock
  (let [rec (buf/record {:id "reserves/X/fx" :name "fx reserves" :unit "USD m" :holder {:unit "central bank" :place "X"}
                         :outflows [{:var "external debt service" :rigid 1.0 :shortfall :arrears :counterpart "row"}
                                    {:var "import payments" :counterpart "row"}]})
        m (held/record-module {:module :x :place :X :record rec :init 10.0 :floor 0.0
                              :inflows [{:var "receipts"}] :sharing :rigid-first
                              :series {"receipts" {2000 2.0} "external debt service" {2000 5.0} "import payments" {2000 9.0}}})
        x (first (recs (eng/run [m] {:from 2000 :to 2000}) :x :fx-reserves))]
    (is (empty? (eng/validate [m])))
    (is (= ["x/import payments"] (:cut x)) "the record's rigid debt service is paid first")
    (is (close? 7.0 (get-in x [:flows "x/import payments" :delivered])))
    (testing "the engine-stocks records read the declared flows"
      (let [r (first (:records (buf/engine-stocks [m] {})))]
        (is (= ["x/external debt service" "x/import payments"] (map :var (:outflows r))))
        (is (= 1.0 (:rigid (first (:outflows r)))))))))

;; -- review (bead bnm-56c.4): the whole-step rule, missing values, the accounts --------

(defn- spy
  "A module that reads `v` at `lag` and keeps every value it saw in its
  state, emitting it back as <id>/seen."
  [id group v lag]
  {:module id :group group :place :world :dt 1 :init []
   :reads [{:var v :unit "u/year" :lag lag :as "x"}]
   :emits [{:var (str (name id) "/seen") :unit "u/year"}]
   :step (fn [s in _ _ _] {:state (conj s (get in "x")) :emits {(str (name id) "/seen") (get in "x")}})})

(deftest no-reader-sees-an-uncut-outflow
  ;; the stock can give 1 a year from its third year; it wants 3: any
  ;; reader that saw 3 after the switch saw the uncut value
  (let [a (holder :a :W :s {:init 4.0 :inflows [{:var "a/in"}] :outflows [{:var "a/out"}]
                            :wants {"a/out" 3.0} :extra {"a/in" 1.0}})
        seen (atom [])
        r (eng/run [a (spy :z0 :F "a/out" 0) (spy :z1 :F "a/out" 1)]
                   {:from 2000 :to 2005 :on-step (fn [id t e] (when (= id :a) (swap! seen conj (get e "a/out"))))})
        delivered (map #(get-in % [:flows "a/out" :delivered]) (recs r :a :s))]
    (is (= [3.0 3.0 1.0 1.0 1.0 1.0] delivered))
    (is (= delivered (get-in r [:states :z0])) "a lag-0 reader gets the cut the same step")
    (is (= (cons nil (butlast delivered)) (get-in r [:states :z1])) "a lag-1 reader gets it a year later")
    (is (= delivered @seen) "the table sees only what was delivered")
    (is (= delivered (get-in r [:emits :a "a/out"])))))

(deftest an-analysis-may-not-rewrite-what-a-held-stock-settled
  (let [a (holder :a :W :s {:init 4.0 :outflows [{:var "a/out"}] :wants {"a/out" 3.0} :extra {"a/other" 1.0}})
        rn (eng/advance (eng/runner [a (spy :z1 :F "a/out" 1)] {:from 2000 :to 2002}))]
    (is (thrown? clojure.lang.ExceptionInfo (eng/update-emits rn :a #(assoc % "a/out" 9.0))) "an outflow the stock delivered")
    (is (thrown? clojure.lang.ExceptionInfo (eng/update-emits rn :a #(assoc % "a/s" 9.0))) "the level the engine holds")
    (is (some? (eng/update-emits rn :a #(assoc % "a/other" 9.0))) "an emit the stock does not use")))

(deftest a-shared-held-stock-is-the-same-in-every-member
  ;; a module shared by an ensemble's members is stepped by member 0; the
  ;; others must keep its holdings too, or a member that takes over member
  ;; 0's place starts the stock again from its :init
  (let [a (holder :a :W :s {:init 4.0 :outflows [{:var "a/out"}] :wants {"a/out" 3.0}})
        r (coupled/run-ensemble (fn [_] [a (spy :z0 :F "a/out" 0)])
                                {:members 2 :from 2000 :to 2003 :shared #{:a} :keep true})
        [m0 m1] (:members r)]
    (is (= (:holdings m0) (:holdings m1)))
    (is (= (:regimes m0) (:regimes m1)))
    (is (= [3.0 1.0 0.0 0.0] (get-in r [:states 1 :z0])) "member 1's reader sees the delivered values")))

(deftest a-missing-value-never-becomes-zero
  (let [mk (fn [ser floor] (held/series-module {:module :x :place :world :stock :s :unit "u" :init 10.0 :floor floor
                                               :inflows [{:var "in"}] :outflows [{:var "out"} {:var "other"}]
                                               :series ser}))
        base {"in" {2000 1.0 2001 1.0 2002 1.0} "out" {2000 2.0 2001 2.0 2002 2.0}
              "other" {2000 1.0 2001 1.0 2002 1.0} "floor" {2000 0.0 2001 0.0 2002 0.0}}
        run (fn [ser] (eng/run [(mk ser "floor")] {:from 2000 :to 2002}))
        nan? (fn [x] (and (number? x) (Double/isNaN (double x))))]
    (testing "a missing outflow"
      (let [r (run (update base "out" dissoc 2001))
            x (second (recs r :x :s))]
        (is (nil? (get-in r [:emits :x "x/out" 1])) "not emitted, and not emitted as 0")
        (is (= :unknown (:regime x)))
        (is (nan? (:next x)) "the level after it is unknown")
        (is (nan? (get-in r [:emits :x "x/other" 1])) "what the other outflow delivered is unknown")
        (is (nan? (get-in r [:emits :x "x/s" 2])))))
    (testing "a missing inflow"
      (let [x (second (recs (run (update base "in" dissoc 2001)) :x :s))]
        (is (= :unknown (:regime x)))
        (is (nan? (:next x)))))
    (testing "a missing floor"
      (let [x (second (recs (run (update base "floor" dissoc 2001)) :x :s))]
        (is (= :unknown (:regime x)))
        (is (nan? (:next x)))))
    (testing "a NaN outflow is unknown, not buffered"
      (let [x (second (recs (run (assoc-in base ["out" 2001] ##NaN)) :x :s))]
        (is (= :unknown (:regime x)))))
    (testing "the first year is untouched"
      (is (= :buffered (:regime (first (recs (run (update base "out" dissoc 2001)) :x :s))))))))

(deftest edge-cases-of-the-constraint
  (let [run1 (fn [opts] (first (recs (eng/run [(holder :a :W :s opts)] {:from 2000 :to 2000}) :a :s)))]
    (testing "a negative inflow is not the stock's to cut: it can take the level below its floor, and says so"
      (let [x (run1 {:init 5.0 :floor 2.0 :inflows [{:var "a/in"}] :outflows [{:var "a/out"}]
                     :wants {"a/out" 1.0} :extra {"a/in" -6.0}})]
        (is (= 0.0 (get-in x [:flows "a/out" :delivered])))
        (is (close? -1.0 (:next x)))
        (is (contains? (:flags x) :below-floor))))
    (testing "an outflow wanted negative is an inflow, never cut"
      (let [x (run1 {:init 1.0 :outflows [{:var "a/out"} {:var "a/back"}] :wants {"a/out" 3.0 "a/back" -2.0}})]
        (is (close? 3.0 (get-in x [:flows "a/out" :delivered])))
        (is (close? 0.0 (:next x)))))
    (testing "a floor above the level at the start: nothing can go"
      (let [x (run1 {:init 1.0 :floor 3.0 :outflows [{:var "a/out"}] :wants {"a/out" 1.0}})]
        (is (= :constrained (:regime x)))
        (is (= 0.0 (:available x)))
        (is (close? 1.0 (:next x)))))
    (testing "two outflows to one counterpart are booked apart"
      (let [x (run1 {:init 2.0 :outflows [{:var "a/p" :shortfall :arrears :counterpart "b/world/s"}
                                          {:var "a/q" :shortfall :arrears :counterpart "b/world/s"}]
                     :wants {"a/p" 2.0 "a/q" 2.0}})]
        (is (close? 1.0 (get-in x [:flows "a/p" :amount])))
        (is (close? 1.0 (get-in x [:flows "a/q" :amount])))))))

(deftest arrears-net-to-zero
  (let [a (holder :a :W :s {:init 2.0 :outflows [{:var "a/p" :shortfall :arrears :counterpart "b/world/t"}
                                                 {:var "a/q" :shortfall :arrears :counterpart "c/world/u"}]
                            :wants {"a/p" 2.0 "a/q" 2.0}})
        r (eng/run [a] {:from 2000 :to 2002})
        pos (eng/arrears (:holdings r) [a])]
    (is (close? 0.0 (reduce + (vals pos))) "the holder's liability is its counterparts' asset")
    (is (close? -10.0 (pos "a/world/s")))
    (is (close? 5.0 (pos "b/world/t")))
    (is (close? 5.0 (pos "c/world/u")))
    (testing "an outflow booked as arrears names who holds the claim"
      (is (= #{:holdings} (set (map :error (eng/validate [(update-in a [:stocks :s :outflows 0] dissoc :counterpart)]))))))))

(deftest a-cut-cannot-vanish-between-modules
  ;; when an outflow's counterpart is a module of the run, that module must
  ;; read the flow, and a stock it holds there must count it as an inflow:
  ;; else what was delivered leaves one account and enters none
  (let [a (holder :a :W :s {:init 2.0 :outflows [{:var "a/out" :counterpart "b/world/s"}] :wants {"a/out" 3.0}})
        b (holder :b :F :s {:init 0.0 :reads [{:var "a/out" :unit "u/year"}] :inflows [{:var "a/out"}]
                            :outflows [{:var "b/out"}] :wants {"b/out" 0.0}})
        errs (fn [ms] (set (map :error (eng/validate ms))))]
    (is (empty? (errs [a b])))
    (is (= #{:holdings} (errs [a (-> b (assoc :reads []) (assoc-in [:stocks :s :inflows] []))])) "the counterpart does not read it")
    (is (= #{:holdings} (errs [a (assoc-in b [:stocks :s :inflows] [])])) "it reads it but its stock does not count it")))

(deftest stocks-that-feed-each-other-in-a-ring-are-an-error
  (let [m {:module :m :group :W :place :world :dt 1 :reads []
           :emits [{:var "m/a" :unit "u" :phase :level} {:var "m/b" :unit "u" :phase :level} {:var "m/c" :unit "u" :phase :level}
                   {:var "m/ab" :unit "u/year"} {:var "m/bc" :unit "u/year"} {:var "m/ca" :unit "u/year"}]
           :stocks {:a {:unit "u" :level "m/a" :init 1.0 :inflows [{:var "m/ca"}] :outflows [{:var "m/ab"}]}
                    :b {:unit "u" :level "m/b" :init 1.0 :inflows [{:var "m/ab"}] :outflows [{:var "m/bc"}]}
                    :c {:unit "u" :level "m/c" :init 1.0 :inflows [{:var "m/bc"}] :outflows [{:var "m/ca"}]}}
           :step (fn [s _ _ _ _] {:state s :emits {"m/ab" 1.0 "m/bc" 1.0 "m/ca" 1.0}})}]
    (is (contains? (set (map :error (eng/validate [m]))) :holdings) "three stocks in a ring cannot be ordered within the step")))

(deftest engine-stocks-give-a-read-inflow-its-unit
  (let [hh (holder :hh :W :deposits {:init 5.0 :reads [{:var "firm/wages" :unit "u/year" :lag 1}]
                                     :inflows [{:var "firm/wages"}] :outflows [{:var "hh/spend"}] :wants {"hh/spend" 9.0}})
        r (first (:records (buf/engine-stocks [hh] {})))]
    (is (= ["u/year"] (map :unit (:inflows r))) "an inflow read from another module has the read's unit")
    (is (= ["u/year"] (map :unit (:outflows r))))))

(deftest the-holder-sees-its-own-level-at-t
  ;; a module may not read its own emit; the engine gives it the level it
  ;; holds in its inputs, the level at t, the same its record starts from
  (let [a (-> (holder :a :W :s {:init 4.0 :outflows [{:var "a/out"}] :wants {"a/out" 3.0}})
              (assoc :init [] :step (fn [s in _ _ _] {:state (conj s (get in "a/s")) :emits {"a/out" 3.0}})))
        r (eng/run [a] {:from 2000 :to 2002})]
    (is (= (map :level (recs r :a :s)) (get-in r [:states :a])))
    (is (= [4.0 1.0 0.0] (get-in r [:states :a])))))
