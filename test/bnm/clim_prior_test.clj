(ns bnm.clim-prior-test
  "bnm-56c.26: --clim-prior at named kinds, not only all of them (t152)."
  (:require [clojure.test :refer [deftest is testing]]
            [bnm.world-run :as wr]))

(def ks ["food_price_shock" "market_crash" "coup"])

(deftest clim-prior-kinds-choice
  (testing "off without the option"
    (is (nil? (wr/clim-prior-kinds nil ks))))
  (testing "kinds: every kind run"
    (is (= (set ks) (wr/clim-prior-kinds "kinds" ks))))
  (testing "a comma list: those kinds only"
    (is (= #{"food_price_shock"} (wr/clim-prior-kinds "food_price_shock" ks)))
    (is (= #{"food_price_shock" "coup"} (wr/clim-prior-kinds "food_price_shock,coup" ks))))
  (testing "a kind not run is refused"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"--clim-prior names a kind not run: war_onset"
                          (wr/clim-prior-kinds "food_price_shock,war_onset" ks)))))

(deftest clim-prior-only-at-the-chosen-kinds
  (let [specs {"food_price_shock" {:a 1} "market_crash" {:a 2}}
        out (wr/with-clim-prior specs #{"food_price_shock"} (atom {}))]
    (is (fn? (get-in out ["food_price_shock" :clim-prior])))
    (is (= {:a 2} (out "market_crash")))
    (is (= specs (wr/with-clim-prior specs nil (atom {}))))))
