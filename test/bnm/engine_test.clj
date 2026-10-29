(ns bnm.engine-test
  (:require [bnm.engine :as eng]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- m
  "A module that emits nothing of its own (the declarations are under test)."
  [id group reads emits & [extra]]
  (merge {:module id :group group :place :world :reads reads :emits emits :dt 1
          :step (fn [s _ _ _ _] {:state s :emits {}})}
         extra))

(defn- errors [ms] (set (map :error (eng/validate ms))))

(deftest reads-need-an-emitter-or-a-source
  (let [a (m :a :W [] [{:var "a/x" :unit "t"}])]
    (is (empty? (eng/validate [a (m :b :F [{:var "a/x" :unit "t"}] [])])))
    (is (= #{:no-emitter} (errors [a (m :b :F [{:var "a/y" :unit "t"}] [])])))
    (is (empty? (eng/validate [a (m :b :F [{:var "wdi/gdp" :unit "USD" :source "WDI"}] [])])) "a data source")
    (testing "a family of variables, by glob"
      (is (empty? (eng/validate [(m :a :W [] [{:var "food/*" :unit "t"}]) (m :b :F [{:var "food/wheat-production" :unit "t"}] [])])))
      (is (empty? (eng/validate [(m :a :W [] [{:var "food/*" :unit "t"}]) (m :b :F [{:var "food/*-hhi" :unit "t"}] [])]))))))

(deftest units-must-match-the-emitters
  (let [a (m :a :W [] [{:var "a/x" :unit "share of GDP"}])]
    (is (= #{:unit} (errors [a (m :b :F [{:var "a/x" :unit "% GDP"}] [])])))
    (is (empty? (eng/validate [a (m :b :F [{:var "a/x" :unit "share of  GDP"}] [])])) "whitespace aside")))

(deftest same-year-edges-form-a-dag
  (let [a (m :a :R [{:var "b/y" :unit "u"}] [{:var "a/x" :unit "u"}])
        b (m :b :R [{:var "a/x" :unit "u"}] [{:var "b/y" :unit "u"}])]
    (is (= #{:cycle} (errors [a b])))
    (is (empty? (eng/validate [(assoc-in a [:reads 0 :lag] 1) b])) "a back edge read a year late")))

(deftest the-causal-order-runs-w-c-f-r-d-p
  (let [p (m :p :P [] [{:var "p/regime" :unit "index"}])
        c (m :c :C [{:var "p/regime" :unit "index"}] [{:var "c/dir" :unit "sd"}])]
    (is (= #{:group-order} (errors [p c])) "politics to control within the year")
    (is (empty? (eng/validate [p (assoc-in c [:reads 0 :lag] 1)])) "politics to control a year late")
    (let [f (m :f :F [{:var "c/dir" :unit "sd"}] [{:var "f/credit" :unit "% GDP"}])
          w (m :w :W [] [{:var "w/oil" :unit "log gap"}])
          r (m :r :R [{:var "f/credit" :unit "% GDP"} {:var "w/oil" :unit "log gap"}] [])
          ms [r f p w (assoc-in c [:reads 0 :lag] 1)]
          co (eng/causal-order ms)]
      (is (= [:w :c :f :r :p] (:rate (eng/order ms))))
      (is (= "W -> C -> F -> R -> P" (:text co)))
      (is (empty? (:violations co))))))

(deftest known-years-agree
  (let [a (m :a :W [] [{:var "a/x" :unit "u" :known {:data-year 2}}])]
    (is (= #{:known} (errors [a (m :b :F [{:var "a/x" :unit "u" :known {:data-year 1}}] [])]))
        "a reader cannot assume the value sooner than its emitter publishes it")
    (is (empty? (eng/validate [a (m :b :F [{:var "a/x" :unit "u" :known {:data-year 3}}] [])])))))

(deftest a-variable-two-places-emit-is-read-from-one
  (let [ctry (m :food :R [] [{:var "food/wheat-production" :unit "t"}] {:place :country})
        wld (m :food-world :W [] [{:var "food/wheat-production" :unit "t"}])]
    (is (= #{:ambiguous} (errors [ctry wld (m :cy :W [{:var "food/wheat-production" :unit "t"}] [])])))
    (is (empty? (eng/validate [ctry wld (m :cy :W [{:var "food/wheat-production" :unit "t" :from :food-world}] [])])))))

(defn- counter
  "A stock that grows by its inflow each year, emitting its level."
  [id group inflow-var]
  {:module id :group group :place :world :dt 1 :init 0.0
   :reads (if inflow-var [{:var inflow-var :unit "u"}] [])
   :emits [{:var (str (name id) "/level") :unit "u"}]
   :stocks {:level {:unit "u"}}
   :step (fn [s in _ _ dt] {:state (+ s (* dt (or (get in inflow-var) 1.0)))
                            :emits {(str (name id) "/level") s}})})

(deftest stepping-follows-the-causal-order-and-the-lags
  (let [a (counter :a :W nil)
        b (counter :b :R "a/level")
        c {:module :c :group :P :place :world :dt 1
           :reads [{:var "b/level" :unit "u"} {:var "b/level" :unit "u" :lag 1 :as :b-last}]
           :emits [{:var "c/change" :unit "u"}]
           :step (fn [s in _ _ _] {:state s :emits {"c/change" (when-let [l (in :b-last)] (- (in "b/level") l))}})}
        r (eng/run [c b a] {:from 2000 :to 2004})]
    (is (= [2000 2001 2002 2003 2004] (:t r)))
    (is (= [0.0 1.0 2.0 3.0 4.0] (get-in r [:emits :a "a/level"])))
    (is (= [0.0 0.0 1.0 3.0 6.0] (get-in r [:emits :b "b/level"])) "b's inflow is a's level the same year")
    (is (= [nil 0.0 1.0 2.0 3.0] (get-in r [:emits :c "c/change"])) "a lag-1 read is last year's value, none before the start")))

(deftest levels-before-rates-break-a-module-cycle
  ;; capital's output depends only on its stock (a level), which agriculture
  ;; reads; capital's investment reads agriculture's allocation (a rate):
  ;; cyclic between the modules, not between the variables
  (let [cap {:module :cap :group :W :place :world :dt 0.5 :init 10.0
             :reads [{:var :fioaa :unit "fraction"}]
             :emits [{:var :io :unit "$/year" :phase :level} {:var :ic :unit "$"}]
             :observe (fn [s _ _ _] {:io (* 0.5 s)})
             :step (fn [s in _ _ dt] {:state (+ s (* dt (- (* 0.5 s (- 1.0 (in :fioaa))) (* 0.1 s)))) :emits {:ic s}})}
        agr {:module :agr :group :W :place :world :dt 0.5
             :reads [{:var :io :unit "$/year"}]
             :emits [{:var :fioaa :unit "fraction"}]
             :step (fn [s in _ _ _] {:state s :emits {:fioaa (/ 1.0 (+ 2.0 (/ (in :io) 10.0)))}})}
        r (eng/run [cap agr] {:from 0.0 :to 2.0})
        ;; the same model in one piece
        mono (loop [t 0 s 10.0 out []]
               (let [io (* 0.5 s) f (/ 1.0 (+ 2.0 (/ io 10.0))) out (conj out s)]
                 (if (= t 4) out (recur (inc t) (+ s (* 0.5 (- (* io (- 1.0 f)) (* 0.1 s)))) out))))]
    (is (empty? (eng/validate [cap agr])))
    (is (= {:level [:cap :agr] :rate [:agr :cap]} (eng/order [cap agr])))
    (is (= mono (get-in r [:emits :cap :ic])))
    (is (= [0.0 0.5 1.0 1.5 2.0] (:t r)) "half-year substeps")
    (testing "without the level phase the same modules are cyclic"
      (is (= #{:cycle} (errors [(assoc cap :emits [{:var :io :unit "$/year"} {:var :ic :unit "$"}]) agr]))))))

(deftest the-run-refuses-what-is-not-declared
  (let [bad (assoc (counter :a :W nil) :step (fn [s _ _ _ _] {:state s :emits {"a/other" 1.0}}))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not declared" (eng/run [bad] {:from 2000 :to 2001}))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cycle"
                        (eng/run [(m :a :R [{:var "b/y" :unit "u"}] [{:var "a/x" :unit "u"}])
                                  (m :b :R [{:var "a/x" :unit "u"}] [{:var "b/y" :unit "u"}])]
                                 {:from 2000 :to 2001}))))

(deftest table-rows-keep-the-known-year-rule
  (let [decl {:module :e :group :F :place :country
              :emits [{:var "e/gap" :unit "log" :known {:data-year 2}}]}
        rows (eng/table-rows decl 2010 {"e/gap" [["USA" "0.5" "2008" nil] ["DEU" "" nil nil]]})]
    (is (= [["USA" 2010 "e" "e/gap" "0.5" "" "2008" 2010 "model"] ["DEU" 2010 "e" "e/gap" "" "" "" 2010 "model"]] rows))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"known"
                          (eng/table-rows decl 2010 {"e/gap" [["USA" "0.5" "2009" nil]]}))
        "a data year published sooner than the module's rule")
    (is (= [["WLD" 1900.5 "w" :pop "1.6E9" "" "" 1900.5 "model"]]
           (eng/table-rows {:module :w :place :world :emits [{:var :pop}]} 1900.5 {:pop 1.6e9}))
        "a number is the place's one value")))

(deftest the-graph-dumps-as-text-and-data
  (let [a (m :a :W [] [{:var "a/x" :unit "u" :channel :W1}])
        b (m :b :R [{:var "a/x" :unit "u"} {:var "src/y" :unit "u" :source "a file"}] [])
        txt (eng/dump [a b])
        g (eng/graph-data [a b])]
    (is (str/includes? txt "a/x"))
    (is (str/includes? txt "W -> R"))
    (is (= [{:from :a :to :b :var "a/x" :lag 0 :phase :rate :emit-phase :rate :unit "u" :channel :W1}] (:edges g)))
    (is (not-any? fn? (mapcat vals (:modules g))) "data only")))

;; -- review (bead bnm-4hv.9): same-year causality, lags, known years, families

(deftest level-emits-come-from-an-observe
  (testing "a :level emit with no :observe would be read a step late"
    (let [a (m :a :W [] [{:var "a/x" :unit "u" :phase :level}])
          b (m :b :R [{:var "a/x" :unit "u" :phase :level}] [])]
      (is (= #{:observe} (errors [a b])))
      (is (empty? (eng/validate [(assoc a :observe (fn [_ _ _ _] {"a/x" 1.0})) b]))))))

(deftest observe-sees-only-stocks-and-levels
  (let [seen (atom #{})
        lv {:module :lv :group :W :place :world :dt 1 :init 1.0
            :emits [{:var "lv/x" :unit "u" :phase :level}]
            :observe (fn [s _ _ _] {"lv/x" s})
            :step (fn [s _ _ _ _] {:state (inc s) :emits {}})}
        rt {:module :rt :group :W :place :world :dt 1
            :emits [{:var "rt/y" :unit "u"}]
            :step (fn [s _ _ t _] {:state s :emits {"rt/y" (double t)}})}
        obs {:module :obs :group :R :place :world :dt 1 :init 0.0
             :reads [{:var "lv/x" :unit "u" :phase :level} {:var "rt/y" :unit "u"}]
             :emits [{:var "obs/z" :unit "u" :phase :level} {:var "obs/w" :unit "u"}]
             :observe (fn [s in _ _] (swap! seen into (keys in)) {"obs/z" s})
             :step (fn [s in _ _ _] {:state (+ s (in "rt/y")) :emits {"obs/w" (in "lv/x")}})}]
    (eng/run [obs rt lv] {:from 2000 :to 2002})
    (is (= #{"lv/x"} @seen) "a same-year rate never reaches an observe")
    (testing "an observe may emit only its declared levels"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"level"
                            (eng/run [rt lv (assoc obs :observe (fn [s _ _ _] {"obs/z" s "obs/w" 1.0}))] {:from 2000 :to 2001}))))
    (testing "a step may not change a level its observe gave"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"level"
                            (eng/run [rt lv (assoc obs :step (fn [s in _ _ _] {:state s :emits {"obs/z" (+ s (in "rt/y"))}}))]
                                     {:from 2000 :to 2001})))
      (is (some? (eng/run [rt lv (assoc obs :step (fn [s _ _ _ _] {:state s :emits {"obs/z" s}}))] {:from 2000 :to 2001}))
          "re-emitting the same value is fine"))))

(deftest lags-are-whole-steps-and-never-negative
  (let [a (m :a :W [] [{:var "a/x" :unit "u"}])]
    (is (= #{:lag} (errors [a (m :b :R [{:var "a/x" :unit "u" :lag -1}] [])])) "a negative lag reads the future")
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"lag"
                          (eng/run [a (m :b :R [{:var "a/x" :unit "u" :lag 0.25}] [])] {:from 2000 :to 2001}))
        "a lag shorter than a step would be read as this step's value, unordered")
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"lag"
                          (eng/run [a (m :b :R [{:var "a/x" :unit "u" :lag 1.5}] [])] {:from 2000 :to 2004})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"whole number of steps"
                          (eng/run [a] {:from 2000 :to 2002.5})) "the run's span is whole steps too")))

(deftest a-value-is-never-seen-before-it-is-known
  (let [seen (atom [])
        early {:module :e :group :F :place :country :dt 1
               :emits [{:var "e/gap" :unit "log" :known {:data-year 2}}]
               ;; at 2001 it reports data year 2000: a year too soon
               :step (fn [s _ _ t _] {:state s :emits {"e/gap" [["USA" "0.5" (str (if (= t 2001) (dec t) (- t 2))) nil]]}})}
        reader {:module :r :group :R :place :country :dt 1
                :reads [{:var "e/gap" :unit "log" :known {:data-year 2}}]
                :emits []
                :step (fn [s in _ t _] (swap! seen conj [t (in "e/gap")]) {:state s :emits {}})}]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"known" (eng/run [reader early] {:from 2000 :to 2003})))
    (is (= [[2000 [["USA" "0.5" "1998" nil]]]] @seen) "the reader stepped 2000 and never saw 2001's early row")
    (testing "a number has no data year to check; a row with none is not checked"
      (is (some? (eng/run [(assoc early :step (fn [s _ _ _ _] {:state s :emits {"e/gap" [["USA" "0.5" "" nil]]}}))] {:from 2000 :to 2001}))))))

(deftest families-match-only-what-they-name
  (is (eng/glob-match? "cycles/*" "cycles/oil-price-gap"))
  (is (not (eng/glob-match? "cycles/*" "cycles-x")))
  (is (not (eng/glob-match? "cycles/*" "cycles")))
  (is (not (eng/glob-match? "cycles/*-gap" "cycles/oil-gap-sd")))
  (let [cy (m :cy :W [] [{:var "cycles/*-price-gap" :unit "log gap"} {:var "cycles/*-exposure" :unit "export share x log gap"}
                         {:var "cycles/*" :unit "mixed"}])
        cx (m :cx :W [] [{:var "cycles-x/*" :unit "other"}])]
    (testing "two families overlap only when one string can match both"
      (is (empty? (eng/validate [cy cx (m :r :R [{:var "cycles/*-exposure" :unit "export share x log gap"}] [])]))
          "not the -price-gap family, whose unit differs")
      (is (= [:cy] (map :from (eng/edges [cy cx (m :r :R [{:var "cycles/*" :unit "mixed"}] [])]))) "cycles/* is not cycles-x/*")
      (is (= #{:no-emitter} (errors [cy (m :r :R [{:var "cycles-x" :unit "other"}] [])])))
      (is (empty? (eng/validate [(m :cy :W [] [{:var "cycles/*-price-gap" :unit "log gap"} {:var "cycles/*-exp*" :unit "share"}])
                                 (m :r :R [{:var "cycles/*-exposure" :unit "share"}] [])]))
          "a prefix in common is not an overlap"))
    (testing "the most specific declaration covers a variable, whatever their order"
      (let [rev (update cy :emits (comp vec reverse))]
        (is (= "export share x log gap" (:unit (eng/emit-for rev "cycles/oil-exposure"))))
        (is (= "mixed" (:unit (eng/emit-for rev "cycles/oil-beta"))))))))

(deftest table-rows-carry-a-spread
  (is (= [["USA" 2010 "e" "e/x" "0.5" "0.1" "2009" 2010 "model"]]
         (eng/table-rows {:module :e :place :country :emits [{:var "e/x"}]} 2010 {"e/x" [["USA" "0.5" "2009" nil "0.1"]]}))))

;; -- phase B: stepping in lockstep, shared modules, spans, mixed steps ----------------------

(deftest a-runner-steps-as-run-does
  (let [a (counter :a :W nil)
        b (counter :b :R "a/level")
        opts {:from 2000 :to 2004}
        r (eng/run [b a] opts)
        stepped (eng/result (nth (iterate eng/advance (eng/runner [b a] opts)) 5))]
    (is (= (:t r) (:t stepped)))
    (is (= (:emits r) (:emits stepped)))
    (is (= (:states r) (:states stepped)))
    (is (eng/done? (nth (iterate eng/advance (eng/runner [b a] opts)) 5)))
    (is (not (eng/done? (nth (iterate eng/advance (eng/runner [b a] opts)) 4))))
    (testing "between steps the states can be read and replaced (an analysis)"
      (let [rn (eng/advance (eng/runner [b a] opts))
            rn (eng/update-states rn #(assoc % :a 10.0))
            r2 (eng/result (nth (iterate eng/advance rn) 4))]
        (is (= [0.0 10.0 11.0 12.0 13.0] (get-in r2 [:emits :a "a/level"])))
        (is (= 1.0 (:a (eng/states (eng/advance (eng/runner [b a] opts))))))))))

(deftest a-shared-modules-emits-are-given-not-stepped
  (let [calls (atom 0)
        a (assoc (counter :a :W nil) :step (fn [s _ _ _ dt] (swap! calls inc) {:state (+ s dt) :emits {"a/level" s}}))
        b (counter :b :R "a/level")
        rn (eng/runner [b a] {:from 2000 :to 2002})
        given (fn [i] {:a {:rate {"a/level" (* 100.0 (inc i))}}})
        out (eng/result (reduce (fn [rn i] (eng/advance rn {:given (given i)})) rn (range 3)))]
    (is (zero? @calls) "the given module is not stepped")
    (is (= [100.0 200.0 300.0] (get-in out [:emits :a "a/level"])))
    (is (= [0.0 100.0 300.0] (get-in out [:emits :b "b/level"])) "its readers see the given values")))

(deftest a-module-steps-only-within-its-span
  (let [a (counter :a :W nil)
        b (assoc (counter :b :R "a/level") :span [2002 2003])
        r (eng/run [a b] {:from 2000 :to 2005})]
    (is (= [nil nil 0.0 2.0 nil nil] (get-in r [:emits :b "b/level"])))
    (is (= [0.0 1.0 2.0 3.0 4.0 5.0] (get-in r [:emits :a "a/level"])))
    (is (= 5.0 (get-in r [:states :b])) "its state stays where its span ended")
    (testing "outside its span its readers see nothing, not its last emits"
      (let [seen (atom [])
            c {:module :c :group :P :place :world :dt 1 :init nil :reads [{:var "b/level" :unit "u"}] :emits []
               :step (fn [s in _ t _] (swap! seen conj [t (get in "b/level")]) {:state s :emits {}})}
            lagged (assoc c :module :d :reads [{:var "b/level" :unit "u" :lag 1}])]
        (eng/run [a b c] {:from 2000 :to 2005})
        (is (= [[2000 nil] [2001 nil] [2002 0.0] [2003 2.0] [2004 nil] [2005 nil]] @seen))
        (reset! seen [])
        (eng/run [a b lagged] {:from 2000 :to 2005})
        (is (= [[2000 nil] [2001 nil] [2002 nil] [2003 0.0] [2004 2.0] [2005 nil]] @seen))))))

(deftest a-yearly-module-sees-whole-years-in-a-half-year-run
  (let [seen (atom [])
        y {:module :y :group :R :place :world :dt 1 :reads [] :emits [{:var "y/x" :unit "u"}]
           :step (fn [s _ _ t _] (swap! seen conj t) {:state s :emits {"y/x" 1.0}})}
        h (assoc (counter :h :W nil) :dt 0.5)]
    (eng/run [y h] {:from 1990.0 :to 1992.0})
    (is (= [1990 1991 1992] @seen))
    (is (every? #(instance? Long %) @seen) "an integral time as a whole number, as a table's years are")))

(deftest table-rows-skip-matrices-and-take-a-label
  (let [m {:module :sfc-finance-USA :label "sfc-finance" :place :USA :emits [{:var "sfc/bsm" :unit "matrix"} {:var "sfc/x" :unit "u"}]}]
    (is (= [["USA" 2010 "sfc-finance" "sfc/x" "0.5" "" "" 2010 "model"]]
           (eng/table-rows m 2010 {"sfc/bsm" {:hh {:money {:a 1.0}}} "sfc/x" 0.5}))
        "a matrix (a map) is not a row; the module column is the label")
    (is (= [["USA" 2010 "sfc-finance" "sfc/x" "0.5" "" "" 2010 "model"]]
           (eng/table-rows m 2010.0 {"sfc/x" 0.5}))
        "an integral time is written as a whole year")))

(deftest parameters-can-be-relearned-between-steps
  (let [a {:module :a :group :W :place :world :dt 1 :init 0.0 :reads [] :params {:k 1.0}
           :emits [{:var "a/x" :unit "u"}]
           :step (fn [s _ ps _ _] {:state s :emits {"a/x" (:k ps)}})}
        rn (eng/advance (eng/runner [a] {:from 2000 :to 2001}))
        r (eng/result (eng/advance (eng/update-params rn #(assoc-in % [:a :k] 5.0))))]
    (is (= [1.0 5.0] (get-in r [:emits :a "a/x"])))))

(deftest an-analysis-can-correct-the-last-emits
  ;; a rescaled level (bnm.sfc-analysis's nominal scale) must reach what the
  ;; next step reads of the last step, as the states do
  (let [a (counter :a :W nil)
        c {:module :c :group :R :place :world :dt 1 :init 0.0
           :reads [{:var "a/level" :unit "u" :lag 1}] :emits [{:var "c/read" :unit "u"}]
           :step (fn [s in _ _ _] {:state s :emits {"c/read" (get in "a/level")}})}
        rn (eng/advance (eng/runner [a c] {:from 2000 :to 2002}))
        rn (eng/update-emits rn :a #(assoc % "a/level" 100.0))
        r (eng/result (eng/advance rn))]
    (is (= [nil 100.0] (get-in r [:emits :c "c/read"])))))

(deftest an-analysis-may-not-correct-emits-already-read-within-their-step
  ;; the whole-step rule: a reader at lag 0 consumed the step's emits within
  ;; the step, so changing them afterwards would give one emit two values
  (let [a (counter :a :W nil)
        b (counter :b :R "a/level")
        rn (eng/advance (eng/runner [a b] {:from 2000 :to 2002}))]
    (is (thrown? clojure.lang.ExceptionInfo (eng/update-emits rn :a #(assoc % "a/level" 100.0))))
    (is (some? (eng/update-emits rn :a identity)) "an unchanged emit is no correction")))
