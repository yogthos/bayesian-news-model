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
