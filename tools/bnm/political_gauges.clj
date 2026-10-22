(ns bnm.political-gauges
  "The political kinds' catalogues and slow gauges for the lattice and the
  anatomy (docs/ANATOMY-POLITICAL.md).

  The catalogue joins the long catalogues (data/derived/political-long.csv,
  scripts/political-long.py: Correlates of War intra-state wars from 1816,
  Archigos irregular removals from 1875) before the modern one
  (data/slow/events.tsv) begins, each state covered from its entry into the
  state system.

  The year gauges, per country and data year, from
  data/derived/political-annual.csv (V-Dem, Maddison, Correlates of War
  material capabilities) and WDI, follow the structural accounts of
  instability: regime type and its middle (Goldstone et al. 2010),
  repression and exclusion, the military's place (Powell 2012: spending per
  soldier), growth and its reversal (Davies 1962), demographic pressure
  (urban growth, the working-age share, infant mortality), elite
  overproduction (tertiary enrolment against growth, Turchin 2003) and
  polarization (the top's income share against the bottom half's)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn read-long
  "{kind {iso #{years}}} from data/derived/political-long.csv."
  ([] (read-long "data/derived/political-long.csv"))
  ([path]
   (with-open [r (io/reader path)]
     (reduce (fn [acc l] (let [[k iso y] (str/split l #",")] (update-in acc [k iso] (fnil conj #{}) (parse-long y))))
             {} (rest (line-seq r))))))

(defn read-states
  "{iso [from to]}: the years each state is in the system."
  ([] (read-states "data/derived/political-states.csv"))
  ([path]
   (with-open [r (io/reader path)]
     (into {} (for [l (rest (line-seq r)) :let [[iso a b] (str/split l #",")]] [iso [(parse-long a) (parse-long b)]])))))

(defn read-annual
  "{var {iso {year value}}} from data/derived/political-annual.csv."
  ([] (read-annual "data/derived/political-annual.csv"))
  ([path]
   (with-open [r (io/reader path)]
     (reduce (fn [acc l] (let [[iso y v x] (str/split l #",")] (assoc-in acc [v iso (parse-long y)] (parse-double x))))
             {} (rest (line-seq r))))))

(def long-from
  "The first year of each kind's long catalogue."
  {"conflict_onset" 1816 "war_onset" 1816 "coup" 1875 "protest_surge" 1980 "violence_surge" 1980})

(def modern
  "The first year of each kind's modern catalogue, from which its onsets
  alone are the labels."
  {"conflict_onset" 1946 "war_onset" 1946 "coup" 1945 "protest_surge" 1980 "violence_surge" 1980})

(defn catalogue
  "{:onsets :busy :from :through :known-lag} for `kind`, in months, as
  bnm.lattice-banking/catalogue: the long catalogue's years before the
  modern one's first (an annual onset at the year's first month), the
  modern one's onsets and spans after; each state from its entry into the
  system or the long catalogue's start, whichever is later. :known-lag is
  the months after an onset's quarter before it is known."
  [{:keys [kind sets coverage-q long states long-from modern]}]
  (let [ev (get-in sets [:onsets kind])
        spans (get-in sets [:spans kind])
        cut (* 12 modern)
        early (into {} (for [[u ys] long] [u (set (keep #(when (< (* 12 %) cut) (* 12 %)) ys))]))
        months (merge-with into early (update-vals ev #(set (map (fn [q] (* 3 q)) %))))
        busy (merge-with into early (update-vals spans #(set (mapcat (fn [q] (range (* 3 q) (+ 3 (* 3 q)))) %))))
        units (into (set (keys states)) (keys ev))]
    {:onsets (into {} (for [u units] [u (into (sorted-set) (get months u))]))
     :busy (into {} (for [u units] [u (into (sorted-set) (get busy u))]))
     :from (into {} (for [u units] [u (* 12 (max long-from (first (get states u [modern]))))]))
     :through (into {} (for [u units] [u (* 3 (inc coverage-q))]))
     :known-lag (* 3 (inc (get-in sets [:lags kind] 0)))}))

(defn anocracy
  "How far a regime stands in the middle, between autocracy and democracy
  (1 at an electoral democracy index of 0.5, 0 at either end)."
  [polyarchy]
  (when polyarchy (- 1.0 (Math/abs (- (* 2.0 (double polyarchy)) 1.0)))))

(def year-names
  [:polyarchy :anocracy :polyarchy3 :physical-integrity :physical-integrity3 :repression :repression3
   :participation :military-executive :neopatrimonial :social-exclusion :economic-exclusion :rule-of-law :assembly
   :log-gdppc :growth3 :population-growth3 :urban-growth5 :soldiers-per-capita :spending-per-soldier3
   :infant-mortality :working-age :tertiary5 :inflation :food-inflation :top10 :bottom50
   ;; austerity as Ponticelli & Voth measure it (data/derived/fiscal.csv)
   :spending-growth :balance-change])

(defn- change [ys y k] (when-let [a (get ys y)] (when-let [b (get ys (- y k))] (- a b))))
(defn- lg [x] (when (and x (pos? x)) (Math/log x)))
(defn- slog [x] (when x (* (Math/signum (double x)) (Math/log1p (Math/abs (double x))))))
(defn- by-year [annual v iso] (get-in annual [v iso] {}))

(defn year-row
  "The year gauges for a country in data year `y`, in year-names' order (nil
  where unknown): V-Dem's year (published by the next origin), the
  national accounts and material capabilities a year further back
  (published late), WDI's through `wdi` ((wdi iso var year), the value
  known at that year's end, each value's publication applied) as known at
  the origin, data year + 1."
  [annual wdi iso y]
  (let [v (fn [k] (get-in annual [k iso y]))
        vy (fn [k] (by-year annual k iso))
        p (dec y)
        gdppc (update-vals (vy "mad_gdppc") lg)
        pop (update-vals (vy "mad_pop") lg)
        tpop (vy "nmc_tpop") upop (vy "nmc_upop")
        urban (into {} (for [[yr t] tpop :let [u (get upop yr)] :when (and u (pos? t) (pos? u))] [yr (Math/log (/ u t))]))
        per-soldier (into {} (for [[yr m] (vy "nmc_milex") :let [s (get (vy "nmc_milper") yr)] :when (and s (pos? s) (pos? m))]
                               [yr (Math/log (/ m s))]))
        soldiers (let [s (get (vy "nmc_milper") p) t (get tpop p)] (when (and s t (pos? t)) (/ s t)))
        neg (fn [x] (when x (- x)))
        ;; WDI as known at the origin, the data year's end + 1: wdi applies
        ;; each value's publication, so the year before's figures are there
        q (inc y)
        w (fn [k yr] (wdi iso k yr))
        wchange (fn [k yr n] (when-let [a (w k yr)] (when-let [b (w k (- yr n))] (- a b))))]
    [(v "vdem_polyarchy") (anocracy (v "vdem_polyarchy")) (change (vy "vdem_polyarchy") y 3)
     (v "vdem_clphy") (change (vy "vdem_clphy") y 3)
     ;; V-Dem's CSO repression is higher where there is less of it
     (neg (v "vdem_csreprss")) (neg (change (vy "vdem_csreprss") y 3))
     (v "vdem_cspart") (v "vdem_ex_military") (v "vdem_neopat") (v "vdem_xpe_exlsocgr") (v "vdem_xpe_exlecon")
     (v "vdem_rule") (v "vdem_caassemb")
     (get gdppc p) (change gdppc p 3) (change pop p 3)
     ;; urbanization: material capabilities to 2016, WDI's urban share after
     (or (change urban p 5) (some-> (wchange "people/urban_share" q 5) (/ 100.0)))
     soldiers (change per-soldier p 3)
     (w "people/infant_mortality" q) (w "people/working_age_share" q) (wchange "people/tertiary_enrolment" q 5)
     (slog (w "economy/inflation" q)) (slog (w "food/food_inflation" q))
     (get-in annual ["wid_top10" iso p]) (get-in annual ["wid_bottom50" iso p])
     (some-> (change (update-vals (vy "gov_exp_real") lg) p 1))
     (change (vy "gov_bal_gdp") p 1)]))
