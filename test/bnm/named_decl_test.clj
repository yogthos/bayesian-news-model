(ns bnm.named-decl-test
  "bnm.named's builders moved onto declarations (docs/SIGNALS.md step 3):
  each must give, on the slow table itself, exactly what the hand-built
  version gave (kept here verbatim as the reference)."
  (:require [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(def ^:private quote-stale
  {"finance/spread_baa" 3 "finance/curve_10y3m" 3 "finance/vix" 3
   "fao/food" 4 "fao/cereals" 4 "fao/oils" 4 "pinksheet/fertiliser" 4})

(defn- getter
  ([db unit] (getter db unit {}))
  ([db unit stale]
   (fn
     ([v t] (some-> (db [unit v]) (sk/value t {:stale (get stale v 30)})))
     ([v t months] (some-> (db [unit v]) (sk/change t months {:stale (get stale v 30)}))))))

(defn- x* [a b] (when (and a b) (* (double a) (double b))))

(defn- ref-leading
  "bnm.named/leading's reader before the move, verbatim."
  [db]
  (fn [unit t]
    (let [us (getter db "USA" quote-stale)
          wq (getter db "world" quote-stale)
          w (getter db "world")
          g (getter db unit)
          food12 (w "food/price_index" t 12)]
      [(us "finance/spread_baa" t 12) (us "finance/curve_10y3m" t) (wq "finance/vix" t 12)
       food12 (x* food12 (g "food/imports_share" t))
       (wq "pinksheet/fertiliser" t 12) (wq "fao/food" t 6) (wq "fao/cereals" t 6) (wq "fao/oils" t 6)])))

(def ^:private series-path "data/slow/series.tsv")

(deftest leading-from-declarations-is-the-hand-built-reader
  (if-not (.exists (io/file series-path))
    (println "leading-from-declarations-is-the-hand-built-reader: no" series-path ", skipped")
    (let [db (merge (nm/load-series series-path (st/read-variables "data/slow/variables.tsv") {:real-lags true})
                    (nm/load-food2))
          old (ref-leading db)
          [names new] (nm/leading db)
          units (sort (distinct (map first (filter #(= "food/imports_share" (second %)) (keys db)))))]
      (is (= nm/leading-names names))
      (is (< 50 (count units)))
      (doseq [u (cons "XXX" units) t (range (* 12 1990) (* 12 2026) 3)]
        (is (= (old u t) (new u t)) [u t])))))
