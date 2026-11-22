(ns bnm.bloc-theory-test
  "--bloc-theory (bnm-58f): kind=k pairs, each kind's year node adding k x
  the bloc exposure (bnm.blocs) to its offset."
  (:require [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(deftest the-choice-reads-kind-k-pairs
  (is (nil? (wr/bloc-theory-choice {})))
  (is (= {"growth_collapse" 8.0} (wr/bloc-theory-choice {:bloc-theory "growth_collapse=8"})))
  (is (= {"growth_collapse" 8.0 "inflation_crisis" 2.5}
         (wr/bloc-theory-choice {:bloc-theory "growth_collapse=8,inflation_crisis=2.5"})))
  (is (thrown? Exception (wr/bloc-theory-choice {:bloc-theory "growth_collapse"})))
  (is (thrown? Exception (wr/bloc-theory-choice {:bloc-theory "no_such_kind=1"}))))

(def data {:dep {"FIN" {2012 {:value 0.1 :known 2014}}} :tie {"FIN" {2013 1.0}}})

(deftest only-the-named-kinds-year-nodes
  (let [specs (wr/with-bloc-theory {"growth_collapse" {:levels []} "coup" {:levels []}} {"growth_collapse" 8.0} data)
        f (get-in specs ["growth_collapse" :offset-add])
        t (+ (* 12 2014) 11)]
    (is (nil? (get-in specs ["coup" :offset-add])))
    (is (< (Math/abs (- 0.8 (f :year "FIN" t))) 1e-12))
    (is (nil? (f :quarter-lead "FIN" t)) "the year node only: k is a year's")
    (testing "no choice, specs unchanged"
      (is (= {"coup" {:levels []}} (wr/with-bloc-theory {"coup" {:levels []}} nil data))))))

(deftest it-adds-to-an-offset-add-already-there
  ;; the t133 audit: it replaced one, dropping a theory layer applied before it
  (let [t (+ (* 12 2014) 11)
        prev (fn [scale place _] (when (= :year scale) ({"FIN" 0.25 "SWE" 0.5} place)))
        f (get-in (wr/with-bloc-theory {"growth_collapse" {:levels [] :offset-add prev}} {"growth_collapse" 8.0} data)
                  ["growth_collapse" :offset-add])]
    (is (< (Math/abs (- 1.05 (f :year "FIN" t))) 1e-12) "the two summed")
    (is (= 0.5 (f :year "SWE" t)) "no exposure: the earlier layer's alone")
    (is (nil? (f :quarter-lead "FIN" t)))))

