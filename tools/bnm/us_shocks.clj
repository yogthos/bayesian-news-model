(ns bnm.us-shocks
  "Shock dimensions the crash audit found missing from the US drawdown
  hazard (docs/INVARIANTS.md): oil (WTI, FRED, monthly from 1946, deflated
  by CPI) and geopolitical risk (Caldara & Iacoviello's historical index,
  monthly from 1900). Each is read a month late, as its stretch against
  its own previous ten years and as its change over twelve months."
  (:require [bnm.invariants :as iv]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def names [:oil-stretch :oil12 :gpr-stretch :gpr12 :shocks-present])

(defn- read-monthly
  "{month value} from a csv with an ISO date in the first column and the
  value in column `col`."
  [path col]
  (with-open [r (io/reader path)]
    (into {} (for [line (rest (line-seq r))
                   :let [cells (str/split line #",")
                         v (some-> (get cells col) str/trim not-empty parse-double)
                         d (first cells)]
                   :when (and v (re-matches #"\d{4}-\d{2}.*" d))]
               [(+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 5 7)))) v]))))

(defn inputs
  "f of month t giving the values for `names`."
  []
  (let [oil (read-monthly "data/raw/slow/market/WTISPLC.csv" 1)
        cpi (read-monthly "data/raw/slow/market/CPIAUCNS.csv" 1)
        gpr (read-monthly "data/raw/slow/gpr/gpr.csv" 1)
        real-oil (into {} (for [[m v] oil :let [c (cpi m)] :when c] [m (Math/log (/ v c))]))
        lgpr (into {} (for [[m v] gpr :when (pos? v)] [m (Math/log v)]))
        ;; the last 121 months up to s as a vector ending at s, for a 120-month stretch
        stretch (fn [series s] (iv/stretch (mapv series (range (- s 120) (inc s))) 120 120 60))
        change (fn [series s] (when-let [a (series s)] (when-let [b (series (- s 12))] (- a b))))]
    (fn [t]
      (let [s (dec t)
            vals [(stretch real-oil s) (change real-oil s) (stretch lgpr s) (change lgpr s)]]
        (conj vals (when (some some? vals) 1.0))))))
