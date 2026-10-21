(ns bnm.kinds
  "Every kind of event the world model forecasts, its catalogue and its
  tier (docs/WORLD-MODEL.md): the material base (wuli), institutions and
  processes (shili), actors (renli), after Gu & Zhu's Wuli-Shili-Renli and
  base and superstructure."
  (:require [bnm.economic-events :as ee]
            [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [bnm.macrohistory :as mh]
            [bnm.named :as nm]
            [bnm.onsets :as on]
            [bnm.slow-train :as st]))

(def tiers
  {"growth_collapse" :wuli "inflation_crisis" :wuli "food_price_shock" :wuli
   "banking_crisis" :shili "currency_crisis" :shili "sovereign_default" :shili
   "fiscal_consolidation" :shili "market_crash" :shili
   "protest_surge" :renli "violence_surge" :renli "coup" :renli "conflict_onset" :renli "war_onset" :renli})

(def tier-order {:wuli 0 :shili 1 :renli 2})

(defn- market-crashes
  "market_crash in the lattice's catalogue form: the modern catalogue's
  onsets and spans, each country covered from two years after its share
  prices begin (the rule's 24-month high) to the catalogue's coverage."
  [db]
  (let [sets (on/read-event-sets "data/slow/events.tsv")
        cov-q (some (fn [[k q]] (when (= k "market_crash") q)) (on/read-coverage "data/slow/coverage.tsv"))
        ev (get-in sets [:onsets "market_crash"])
        spans (get-in sets [:spans "market_crash"])
        starts (into {} (for [[[u v] s] db :when (= v "markets/share_prices")] [u (+ 24 (long (first (:period s))))]))]
    {:onsets (into {} (for [[u _] starts] [u (into (sorted-set) (map #(* 3 %) (get ev u)))]))
     :busy (into {} (for [[u _] starts] [u (into (sorted-set) (mapcat (fn [q] (range (* 3 q) (+ 3 (* 3 q)))) (get spans u)))]))
     :from starts
     :through (into {} (for [[u _] starts] [u (* 3 (inc cov-q))]))
     :known-lag (* 3 (inc (get-in sets [:lags "market_crash"] 0)))}))

(defn catalogues
  "{kind catalogue} for every kind, in months as bnm.lattice-banking/
  catalogue gives them."
  []
  (let [db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))]
    (merge {"banking_crisis" (assoc (lb/catalogue (mh/read-jst "data/raw/slow/jst/jst.csv")) :known-lag lb/known-lag)
            "market_crash" (market-crashes db)}
           (lp/catalogues)
           (ee/read-catalogues))))

(defn annual
  "A catalogue by year: {:onsets {u #{years}} :cover {u [from to]}}."
  [{:keys [onsets from through]}]
  {:onsets (into {} (for [[u ms] onsets] [u (set (map #(quot % 12) ms))]))
   :cover (into {} (for [[u f] from :let [t (get through u)] :when t] [u [(quot (+ f 11) 12) (quot (- t 11) 12)]]))})
