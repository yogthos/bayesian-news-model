(ns bnm.econ-divergence-test
  (:require [bnm.econ-divergence :as dv]
            [bnm.divergence-attribution :as da]
            [bnm.attribution :as at]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(def series
  ;; {data-year [known value]}
  (into (sorted-map) (for [y (range 1950 1990)] [y [(inc y) (double (- y 1950))]])))

(deftest a-gap-is-the-value-less-its-trailing-mean
  (let [[k g] (dv/gap series 1980)]
    ;; the 25 years 1955-1979 have values 5..29, mean 17
    (is (= 1981 k))
    (is (close? (- 30.0 17.0) g 1e-12)))
  (testing "fewer than min-history prior years: no gap"
    (is (nil? (dv/gap series 1955)))
    (is (some? (dv/gap series (+ 1950 dv/min-history))))))

(deftest a-gap-reads-nothing-later-than-its-data-year
  (doseq [y (range 1960 1990)
          :let [later (into (sorted-map) (for [[d [k v]] series] [d [k (if (> d y) (* 1000.0 v) v)]]))]]
    (is (= (dv/gap series y) (dv/gap later y)) (str y)))
  (testing "the gap is known when its last input is"
    (let [mixed (assoc series 1970 [1975 20.0])
          [k _] (dv/gap mixed 1980)]
      (is (= 1981 k) "a late input inside the window holds back nothing past the row's own known year")
      (is (= 1975 (first (dv/gap mixed 1970))) "the row's own late known year")))
  (testing "the window's inputs are all known by the gap's known year"
    (let [mixed (assoc series 1970 [1985 20.0])
          [k _] (dv/gap mixed 1980)]
      (is (= 1985 k)))))

(deftest the-composite-scales-on-what-is-known
  (let [gaps {"a" {"X" {1980 [1981 1.0] 1990 [1991 3.0]} "Y" {1980 [1981 -1.0] 1990 [1991 -3.0]}}}
        sd-early (dv/known-sd gaps "a" 1981)
        sd-late (dv/known-sd gaps "a" 1991)]
    (is (close? 1.0 sd-early 1e-12) "only the two 1980 gaps are known in 1981")
    (is (close? (Math/sqrt 5.0) sd-late 1e-12))
    (testing "a later value does not change an earlier scale"
      (is (= sd-early (dv/known-sd (assoc-in gaps ["a" "X" 1990] [1991 1000.0]) "a" 1981))))))

(deftest stock-rows-carry-their-known-year
  (let [inputs {"equity-gdp" {"USA" series} "wealth-income" {"USA" (into (sorted-map) (for [[d [_ v]] series] [d [(+ d 2) v]]))}}
        rows (dv/stock-rows inputs)]
    (is (seq rows))
    (doseq [{:keys [year known data-year variable]} rows]
      (is (= year known))
      (is (> known data-year) variable))
    (is (some #(= "econ/divergence" (:variable %)) rows))
    (is (every? #(re-find #"^econ/divergence" (:variable %)) rows))
    (testing "the composite is known when its last component is"
      (let [by (group-by (juxt :variable :data-year) rows)]
        (doseq [[[v d] [r]] by :when (= v "econ/divergence")]
          (is (>= (:known r) (+ d 2)) (str d)))))))

;; --- the attribution wiring ---------------------------------------------------------

(deftest the-wiring-reads-the-stock-as-known
  (let [stock {"econ/divergence-equity" {"USA" {1980 [1981 0.5] 1981 [1982 0.7]}}}
        panels (da/divergence-panels stock)]
    (is (= {1981 0.5 1982 0.7 1983 0.7 1984 0.7} (select-keys (get-in panels ["equity-gap" "USA"]) [1980 1981 1982 1983 1984])))
    (is (nil? (get-in panels ["equity-gap" "USA" 1980])) "nothing before it is known")))

(deftest the-groups-nest-divergence-over-the-financial-states
  (let [gs (into {} (da/groups {"equity-gap" 0.2 "valuation-gap" 0.3 "house-gap" 0.7 "wealth-gap" 0.9}))]
    (is (= da/divergence-states (gs "divergence")))
    (is (= ["divergence"] (gs "composite")))
    (is (= ["credit-gap" "keen-debt-gap"] (gs "financial")))
    (is (= (into ["credit-gap" "keen-debt-gap"] da/divergence-states) (gs "financial+divergence")))
    (is (= ["house-gap" "wealth-gap"] (gs "where")))
    (is (= ["equity-gap" "valuation-gap"] (gs "when"))))
  (testing "no where or when group when one side is empty"
    (let [gs (into {} (da/groups {"equity-gap" 0.9 "valuation-gap" 0.9 "house-gap" 0.9 "wealth-gap" 0.9}))]
      (is (not (contains? gs "when")))
      (is (not (contains? gs "where"))))))

(deftest the-defaults-are-the-design-years-and-the-two-kinds
  (let [o (da/parse-opts [])]
    (is (= ["banking_crisis" "market_crash"] (:kinds o)))
    (is (= [1960 2002 1980] ((juxt :from :to :split) o)))
    (is (nil? (:fit-from o)))
    (is (re-find #"divergence" (:out o)))
    (is (re-find #"divergence" (:penalties o))))
  (testing "the registered run scores 2003-2022 only, fitted on the design years"
    (let [o (da/parse-opts ["--fit-from" "1960" "--fit-to" "2002" "--from" "2002" "--to" "2022"])
          [{:keys [fit score]}] (at/splits o)]
      (is (= [1960 2002] fit))
      (is (= #{2003 2022} (set ((juxt first last) (sort (distinct (mapcat at/outcome-years (at/era-origins score))))))))))
  (testing "the design never reads an outcome after 2002"
    (doseq [{:keys [fit score]} (at/splits (da/parse-opts []))]
      (is (<= (apply max (mapcat at/outcome-years (concat (at/era-origins fit) (at/era-origins score)))) 2002)))))
