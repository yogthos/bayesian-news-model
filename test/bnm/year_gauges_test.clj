(ns bnm.year-gauges-test
  "bnm.year-gauges (t134): a row of inputs joins the year node's gauges as
  a group of its own."
  (:require [bnm.year-gauges :as yg]
            [clojure.test :refer [deftest is testing]]))

(def ^:private close? #(< (Math/abs (- (double %1) (double %2))) 1e-9))

(defn- gauge-spec []
  {:scales [{:name :quarter :per 3 :horizon 3} {:name :year :per 12 :horizon 12}]
   :levels [[:country ["A" "B"]] [:world ["world"]]]
   :parent {"A" "world" "B" "world"}
   :start 0 :end 1199
   :gauges (fn [scale place t] (if (= :year scale) [1.0 2.0] [3.0]))
   :gauge-width {:quarter 1 :year 2}
   :gauge-groups {:year {:a [0] :b [1]}}
   :gauge-names {:year [:x :y]}})

(deftest a-row-joins-as-its-own-group
  (let [row (fn [u t] (if (= u "A") [(double t) 1.0 nil] nil))
        sp (yg/with-group (gauge-spec) row 3 :global [:p :q :r] :stable)]
    (is (= 5 (get-in sp [:gauge-width :year])))
    (is (= 1 (get-in sp [:gauge-width :quarter])) "other scales untouched")
    (is (= [2 3 4] (get-in sp [:gauge-groups :year :global])))
    (is (= [0] (get-in sp [:gauge-groups :year :a])))
    (is (= [:x :y :p :q :r] (get-in sp [:gauge-names :year])))
    (is (= [3.0] ((:gauges sp) :quarter "A" 600)))
    (let [a ((:gauges sp) :year "A" 1199) b ((:gauges sp) :year "B" 1199) w ((:gauges sp) :year "world" 1199)]
      (is (= [1.0 2.0] (subvec a 0 2)) "the node's own gauges first")
      (is (pos? (nth a 2)) "standardized on the past")
      (testing "a missing value, or a missing row, reads 0"
        (is (= 0.0 (nth a 4)))
        (is (= [0.0 0.0 0.0] (subvec b 2))))
      (is (close? (nth w 2) (/ (+ (nth a 2) (nth b 2)) 2)) "an aggregate reads its countries' mean"))))

(deftest a-spec-without-named-groups-keeps-its-gauges-as-one
  (let [sp (yg/with-group (dissoc (gauge-spec) :gauge-groups :gauge-names) (constantly [0.0]) 1 :g [:p] :stable)]
    (is (= {:gauges [0 1] :g [2]} (get-in sp [:gauge-groups :year])))
    (is (nil? (:gauge-names sp)))))

(deftest the-names-must-match-the-width
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"names"
                        (yg/with-group (gauge-spec) (constantly [0.0]) 1 :g [:p :q] :stable))))
