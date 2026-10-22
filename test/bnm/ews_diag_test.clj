(ns bnm.ews-diag-test
  (:require [bnm.ews-diag :as ed]
            [clojure.test :refer [deftest is]]))

(deftest one-warning-per-kind-from-the-design-years
  ;; of a kind's (series, indicator) results, those gaining past its
  ;; forecast (LR >= 3.84) and at least twice their placebo's; the one that
  ;; gains most, its sign the design years'; none, no warning
  (let [r (fn [lr coef plr] {:lr lr :coef coef :placebo-lr plr})]
    (is (= ["economy/gdp_growth" :variance -1]
           (ed/choose {["economy/gdp_growth" :variance] (r 27.8 -1.0 0.6)
                       ["economy/inflation" :ac1] (r 6.3 -0.2 0.2)
                       ["economy/inflation" :variance] (r 11.8 -30.0 6.3)})))
    (is (nil? (ed/choose {["economy/inflation" :variance] (r 11.8 -30.0 6.3)})) "its placebo gains half as much")
    (is (nil? (ed/choose {["economy/inflation" :ac1] (r 3.0 0.2 0.0)})))))
