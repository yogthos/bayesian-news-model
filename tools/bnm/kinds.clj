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

(def at-risk-rules
  "Each catalogue's rule for when it can date an onset (bnm-96j), beyond
  the busy months bnm.lattice-banking/onset-in? already leaves out: the
  :grid of months it dates onsets on ([step phase]) and the :refractory
  months after an onset before the next (its once-in-n rule). Annual
  catalogues date in January; the quarterly food catalogue at each
  quarter's last month (once in four quarters); the mechanical ones
  (data/slow/events.tsv: 12 months' cool-off) at the quarter's first.
  Banking, conflict, war onsets, coups and sovereign defaults have no rule
  beyond their busy months: two onsets may come close together."
  {"growth_collapse" {:grid [12 0] :refractory 60}
   "currency_crisis" {:grid [12 0] :refractory 60}
   "fiscal_consolidation" {:grid [12 0] :refractory 36}
   ;; and a run's later years and the year after it: :barred, from the data
   "inflation_crisis" {:grid [12 0] :refractory 24}
   "sovereign_default" {:grid [12 0]}
   "food_price_shock" {:grid [3 2] :refractory 12}
   "market_crash" {:grid [3 0] :refractory 12}
   "protest_surge" {:grid [3 0] :refractory 12}
   "violence_surge" {:grid [3 0] :refractory 12}})

(defn with-catalogue-at-risk
  "`cats` with each kind's rule (`at-risk-rules`) for world-run --at-risk
  catalogue: inflation's barred months from the db's inflation, the food
  catalogue's grid by month under --food-outcomes monthly (`food`), the
  growth catalogue's at quarter ends, once in twenty quarters, under
  --growth-outcomes quarterly (`growth`, bnm-56c.38)."
  ([cats db food] (with-catalogue-at-risk cats db food nil))
  ([cats db food growth]
  (into {} (for [[k cat] cats]
             [k (cond-> (merge cat (at-risk-rules k))
                  (and (= k "food_price_shock") (= "monthly" food)) (assoc :grid [1 0])
                  (and (= k "growth_collapse") (= "quarterly" growth)) (assoc :grid [3 2] :refractory 60)
                  (= k "inflation_crisis")
                  (assoc :barred (into {} (for [u (keys (:onsets cat))]
                                            [u (ee/inflation-barred (ee/by-year-inflation db u))]))))]))))

(defn annual
  "A catalogue by year: {:onsets {u #{years}} :cover {u [from to]}}."
  [{:keys [onsets from through]}]
  {:onsets (into {} (for [[u ms] onsets] [u (set (map #(quot % 12) ms))]))
   :cover (into {} (for [[u f] from :let [t (get through u)] :when t] [u [(quot (+ f 11) 12) (quot (- t 11) 12)]]))})
