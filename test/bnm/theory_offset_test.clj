(ns bnm.theory-offset-test
  "--theory-offset true (bnm-ris): banking's year node reads a theory
  forecast as its offset, a logit on its own credit stretch and three-year
  credit growth refit at each origin on what is known (bnm.theory)."
  (:require [bnm.lattice-banking :as lb]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is]]))

(deftest the-option
  (is (false? (wr/theory-offset-choice {})))
  (is (true? (wr/theory-offset-choice {:theory-offset "true"})))
  (is (thrown? Exception (wr/theory-offset-choice {:theory-offset "yes"}))))

(deftest the-theory-reads-credit-stretch-and-growth
  (is (= [:credit :boom-credit3] (mapv first lb/theory-inputs)))
  (is (= [(first (:stretches lb/year-groups)) (first (:booms lb/year-groups))] (mapv second lb/theory-inputs))
      "the year row's credit stretch and three-year credit growth"))
