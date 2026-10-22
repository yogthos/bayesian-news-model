(ns bnm.tech-demand
  "Technology demand for materials: adoption x material intensity, with
  learning (bead bnm-1pq.7, docs/ESSENTIALS.md section 8 step 6).

  Demand for material m in country c and data year t is the sum over
  technologies k of the new capacity added in t times the intensity of m
  in k in year t:

    D(m, c, t) = sum_k additions(k, c, t) x intensity(m, k, t)

  The intensities are bnm.recipes' posteriors (recipes-filtered.csv, read
  at run time, mean, region WLD): kWh per EV and kg of Li, Co, Ni and
  graphite per kWh, kg of copper per EV, t per GW of PV and of wind. Their
  learning (silicon -9%/yr from 2004, silver -12%/yr from 2008, cobalt
  -10%/yr from 2015) is the drift already in the priors, so the posterior
  of year t carries it; a projection moves each coefficient on by the
  prior's drift (the filter's own prediction step: `drift`).

  Technologies and their units of additions:
    :ev      battery-electric cars: BACI HS17 870380 imports, tonnes / 1.8
             t a car (`cars`). Apparent supply where there are no sales
             data: imports only, so a producer's home sales (China's) are
             missing; the world is the sum of imports, i.e. world trade,
             about a third of world sales. 2017- (HS17).
    :pv      GW of solar added: the yearly rise in EIA installed capacity
             (data/derived/material/capacity.csv power_solar), falls
             counted as zero.
    :wind    GW of wind added, the same (power_wind). EIA does not split
             onshore and offshore: the onshore coefficients are used, so
             rare earths (offshore 239 t/GW against 14) are a lower bound.
    :li-ion  kWh of Li-ion accumulators traded: HS92 850780 imports (all
             accumulators other than lead-acid and NiCd: NiMH is in it)
             x kwh-per-tonne. A second route to battery demand, 1995-,
             kept out of the totals (an EV's battery would count twice).
  Battery storage has no source on disk and is left out.

  Adoption (the diffusion fitted by bnm.technology/fit-logistic, rolling,
  only what is known by the year):
    :ev      EV share of passenger car imports (870380 over all 8703
             tonnes), %, cap 100; demand follows the level (a flow), the
             car market held at its last known size.
    :pv      solar share of electricity generation (OWID), %, cap 100;
    :wind    wind share, the same; capacity added follows the share's
             derivative (total generation and capacity factor held).
    :li-ion  cumulative accumulator imports since 1995, no cap (the
             machinery's ceiling search, at most three times the highest
             level); additions follow the derivative.
  The projected 5-year growth of demand for m (`growth5`) is the log of
  the projected demand five years after the base data year over demand in
  it: each technology's demand moved by its fitted adoption (`growth`) and
  by the intensity drift. It is written only when every technology in the
  total has a fit.

  Known years: BACI and EIA capacity data year + 2, OWID + 1, the recipe
  posterior of year t at t. Rows are by the year known; the data year
  they describe is tech-demand/state-year (bookkeeping).

  Variables, per country (ISO3) and WLD (the sum of countries' additions;
  for the fits, the world's own adoption series), in tonnes:
    tech-demand/<m>             EVs + PV + wind
    tech-demand/<m>-<k>         one technology's part (k ev, pv, wind, and
                                li-ion: the trade route, not in the total)
    tech-demand/<m>-growth5     projected 5-year log growth of the total
    tech-demand/<m>-li-ion-growth5  the same for the trade route
    tech-demand/<m>-count       technologies in the total (bookkeeping)
    tech-demand/<k>-additions   cars, GW or kWh
    tech-demand/<k>-adoption    the latest adoption level known
    tech-demand/<k>-speed, -midpoint  the fitted logistic
    tech-demand/state-year      the data year (bookkeeping)"
  (:require [bnm.csv :as csv]
            [bnm.recipes :as recipes]
            [bnm.technology :as tech]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ---------------------------------------------------------------- constants

(def tonnes-per-car
  "Kerb mass of a battery-electric car, t (a mid-size BEV is 1.6-2.1 t;
  stated, not fitted)."
  1.8)

(def kwh-per-tonne
  "kWh per tonne of traded accumulators: pack-level specific energy of
  about 150 Wh/kg (cells 200-270, packs with casing and cooling less;
  stated, not fitted)."
  150.0)

(def min-cover
  "The share of a flow's value whose tonnes must be known for its tonnes to
  count (as bnm.essentials)."
  0.5)

(def horizon 5)

(def techs
  "Per technology: :materials {material [[recipe ...] factor]} (demand in
  t = additions x product of the recipes' coefficients x factor), :kind of
  adoption (:level, demand follows it; :stock, demand follows its
  derivative), :fit options for bnm.technology/fit-logistic, :total?"
  {:ev {:materials {"lithium" [["ev/battery" "li-ion/lithium"] 1e-3]
                    "cobalt" [["ev/battery" "li-ion/cobalt"] 1e-3]
                    "nickel" [["ev/battery" "li-ion/nickel"] 1e-3]
                    "graphite" [["ev/battery" "li-ion/graphite"] 1e-3]
                    "copper" [["ev/copper"] 1e-3]}
        :kind :level :fit {:cap 100.0} :total? true}
   :pv {:materials {"silicon" [["pv/silicon"] 1.0]
                    "silver" [["pv/silver"] 1.0]
                    "copper" [["pv/copper"] 1.0]
                    "steel" [["pv/steel"] 1.0]}
        :kind :stock :fit {:cap 100.0} :total? true}
   :wind {:materials {"copper" [["wind-onshore/copper"] 1.0]
                      "steel" [["wind-onshore/steel"] 1.0]
                      "rare-earths" [["wind-onshore/rare-earths"] 1.0]}
          :kind :stock :fit {:cap 100.0} :total? true}
   :li-ion {:materials {"lithium" [["li-ion/lithium"] (* kwh-per-tonne 1e-3)]
                        "cobalt" [["li-ion/cobalt"] (* kwh-per-tonne 1e-3)]
                        "nickel" [["li-ion/nickel"] (* kwh-per-tonne 1e-3)]
                        "graphite" [["li-ion/graphite"] (* kwh-per-tonne 1e-3)]}
            :kind :stock :fit {} :total? false}})

(defn fit-opts [k] (get-in techs [k :fit]))

;; ---------------------------------------------------------------- series

(defn- finite? [x] (and x (< (Math/abs (double x)) Double/POSITIVE_INFINITY)))

(defn cover-tonnes
  "Tonnes scaled up to the whole value (the flows without tonnes at the
  covered unit value), nil when under `cover` of the value has tonnes."
  [t v vt cover]
  (when (and t v vt (pos? (double v)) (>= (/ (double vt) (double v)) cover))
    (* (double t) (/ (double v) (double vt)))))

(defn cars [tonnes] (/ (double tonnes) tonnes-per-car))

(defn additions
  "A stock's entries [{:t :known :x}] to its yearly additions, the rise
  over consecutive data years (a fall adds nothing), known when both
  years are."
  [stock]
  (let [v (vec (sort-by :t stock))]
    (vec (for [[a b] (partition 2 1 v) :when (= (:t b) (inc (:t a)))]
           {:t (:t b) :known (max (:known a) (:known b))
            :x (max 0.0 (- (double (:x b)) (double (:x a))))}))))

(defn cumulative
  "Running sum of yearly entries, known when every year so far is."
  [entries]
  (vec (rest (reductions (fn [acc {:keys [t known x]}]
                           {:t t :known (max known (or (:known acc) known)) :x (+ (double (or (:x acc) 0.0)) (double x))})
                         {} (sort-by :t entries)))))

(defn world-sum
  "{iso entries} to the world's: the sum by data year, known when every
  part is."
  [by-iso]
  (->> (vals by-iso) (apply concat) (group-by :t) (sort-by key)
       (mapv (fn [[t es]] {:t t :known (reduce max (map :known es)) :x (reduce + (map #(double (:x %)) es))}))))

;; ---------------------------------------------------------------- the model

(defn fit-as-of
  "bnm.technology's logistic through the entries known by y."
  [entries y opts]
  (tech/fit-logistic (tech/as-of entries y) opts))

(defn growth
  "The log growth of demand over h years from t on the fitted logistic:
  :level, the level's; :stock, its derivative's (s L (1 - L/K)). nil
  without a fit or where the curve is flat."
  [{:keys [ceiling speed midpoint] :as fit} kind t h]
  (when fit
    (let [L #(tech/logistic ceiling speed midpoint %)
          f (case kind
              :level L
              :stock #(let [l (L %)] (* speed l (- 1.0 (/ l ceiling)))))
          a (f t) b (f (+ t h))]
      (when (and (pos? a) (pos? b))
        (let [g (Math/log (/ b a))] (when (finite? g) g))))))

(defn drift
  "The log change of the recipe's coefficient from t to t+h that its prior
  predicts (bnm.recipes' centre: the learning drift from :drift-from on);
  0 without a prior or drift."
  [p t h]
  (if (and p (:drift p))
    (- (:m (recipes/prior-state p (+ t h))) (:m (recipes/prior-state p t)))
    0.0))

(defn- intensity
  "The product of the recipes' coefficients in year t x factor, nil when
  one is unknown."
  [ints [rs factor] t]
  (let [vs (map #(get-in ints [% t]) rs)]
    (when (every? some? vs) (* (double factor) (reduce * 1.0 (map double vs))))))

(defn material-demand
  "{material tonnes (nil where a coefficient is unknown)} for `adds` units
  of technology k added in year t."
  [k adds ints t]
  (into {} (for [[m spec] (get-in techs [k :materials])]
             [m (some-> (intensity ints spec t) (* (double adds)))])))

(defn- material-drift [priors k m t]
  (reduce + 0.0 (map #(drift (get priors %) t horizon) (first (get-in techs [k :materials m])))))

(defn- latest-entry [entries y]
  (some->> entries (filter #(<= (:known %) y)) seq (apply max-key :t)))

(defn- row [iso year variable value]
  (when (finite? value) {:iso iso :year year :variable variable :value (double value)}))

(defn- v [& parts] (str "tech-demand/" (str/join "-" (map name parts))))

(defn- project
  "{:now tonnes :next projected tonnes :count n} of material m over the
  technologies' {k {m tonnes}} at base year t, nil when one lacks a
  growth."
  [parts m gk priors t]
  (let [ps (keep (fn [[k by]] (when-let [d (get by m)] [k d])) parts)]
    (when (seq ps)
      (let [now (reduce + (map second ps))
            nxt (map (fn [[k d]] (when-let [g (gk k)] (* d (Math/exp (+ g (material-drift priors k m t)))))) ps)]
        {:now now :count (count ps)
         :growth (when (and (pos? now) (every? some? nxt))
                   (Math/log (/ (reduce + nxt) now)))}))))

(defn rows-at
  "Rows {:iso :year :variable :value} of country iso known in year y, from
  data {:adds {k {iso entries}} :adopt {k {iso entries}} :ints {recipe
  {year coefficient}} :priors {recipe prior}}."
  [{:keys [adds adopt ints priors]} iso y]
  (let [latest (into {} (for [k (keys techs) :let [e (latest-entry (get-in adds [k iso]) y)] :when e] [k e]))
        total-ks (filter #(get-in techs [% :total?]) (keys latest))
        t (when (seq total-ks) (reduce max (map #(:t (latest %)) total-ks)))
        fits (into {} (for [k (keys techs)] [k (fit-as-of (get-in adopt [k iso] []) y (fit-opts k))]))
        demand (fn [k] (let [e (latest k)]
                         (into {} (filter val (material-demand k (:x e) (into {} (for [[r by] ints] [r (into {} (filter #(<= (key %) y) by))])) (:t e))))))
        totals (into {} (for [k total-ks :when (= t (:t (latest k)))] [k (demand k)]))
        li (when-let [e (latest :li-ion)] (demand :li-ion))
        gk (fn [base] (fn [k] (growth (fits k) (get-in techs [k :kind]) base horizon)))
        mats (sort (distinct (mapcat keys (vals totals))))]
    (vec
     (keep identity
           (concat
            (when t [(row iso y (v "state-year") t)])
            (for [m mats
                  :let [p (project totals m (gk t) priors t)]
                  r [(row iso y (v m) (:now p))
                     (row iso y (v m "count") (:count p))
                     (row iso y (v m "growth5") (:growth p))]]
              r)
            (for [[k by] totals [m d] by] (row iso y (v m k) d))
            (when li
              (let [lt (:t (latest :li-ion))]
                (for [[m d] li
                      r [(row iso y (v m "li-ion") d)
                         (row iso y (v m "li-ion" "growth5") (:growth (project {:li-ion li} m (gk lt) priors lt)))]]
                  r)))
            (for [[k e] latest] (row iso y (v k "additions") (:x e)))
            (for [k (keys techs)
                  :let [a (latest-entry (get-in adopt [k iso]) y) f (fits k)]
                  r [(when a (row iso y (v k "adoption") (:x a)))
                     (row iso y (v k "speed") (:speed f))
                     (row iso y (v k "midpoint") (:midpoint f))]]
              r))))))

(defn rows
  "rows-at for every country in the data and every year, sorted."
  [data years]
  (let [isos (sort (distinct (concat (mapcat (comp keys val) (:adds data)) (mapcat (comp keys val) (:adopt data)))))]
    (->> (pmap (fn [iso] (vec (mapcat #(rows-at data iso %) years))) isos)
         (apply concat)
         (sort-by (juxt :iso :year :variable))
         vec)))

;; ---------------------------------------------------------------- reading

(defn- num [s] (when (and s (not (str/blank? s))) (parse-double s)))

(defn- read-csv [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (csv/records r \,) ks (mapv keyword h)]
      (mapv #(zipmap ks %) (doall ls)))))

(defn read-intensities
  "{recipe {year mean}} of region WLD from recipes-filtered.csv."
  [path]
  (reduce (fn [acc {:keys [recipe region year mean]}]
            (if (= "WLD" region) (assoc-in acc [recipe (parse-long year)] (parse-double mean)) acc))
          {} (read-csv path)))

(defn read-trade
  "From tech-trade.csv (scripts/tech-trade.py): {:ev {iso entries} cars
  imported, :ev-share {iso entries} %, :ev-share-world entries (world EV
  over world car import tonnes, the flows with tonnes), :li-ion {iso
  entries} tonnes}."
  [path]
  (let [rs (read-csv path)
        t (fn [r] (cover-tonnes (num (:imports_t r)) (num (:imports_v r)) (num (:imports_vt r)) min-cover))
        e (fn [r x] {:t (parse-long (:year r)) :known (parse-long (:known r)) :x x})
        hs17 (filter #(= "HS17" (:hs %)) rs)
        evs (group-by (juxt :iso :year) (filter #(= "870380" (:hs6 %)) hs17))
        all-cars (group-by (juxt :iso :year) hs17)
        by-iso (fn [es] (update-vals (group-by first es) #(vec (sort-by :t (map second %)))))]
    {:ev (by-iso (for [[[iso _] [r]] evs :let [x (t r)] :when x] [iso (e r (cars x))]))
     :ev-share (by-iso (for [[[iso y] [r]] evs
                             :let [x (t r) cs (all-cars [iso y])
                                   ts (map t cs)]
                             :when (and x (every? some? ts) (pos? (reduce + ts)))]
                         [iso (e r (* 100.0 (/ x (reduce + ts))))]))
     :ev-share-world (let [sum-by (fn [rs] (update-vals (group-by #(parse-long (:year %)) rs)
                                                       (fn [g] [(reduce max (map #(parse-long (:known %)) g))
                                                                (reduce + 0.0 (keep t g))])))
                           ev (sum-by (filter #(= "870380" (:hs6 %)) hs17))
                           all (sum-by hs17)]
                       (vec (for [[y [kn x]] (sort-by key ev) :let [[_ tot] (all y)] :when (pos? tot)]
                              {:t y :known kn :x (* 100.0 (/ x tot))})))
     :li-ion (by-iso (for [r rs :when (and (= "HS92" (:hs r)) (= "850780" (:hs6 r))) :let [x (t r)] :when x]
                       [(:iso r) (e r x)]))}))

(defn read-capacity
  "{:pv {iso entries} :wind {iso entries}}, GW installed, from the EIA rows
  of data/derived/material/capacity.csv, known `lag` years on."
  [path lag]
  (let [want {"power_solar" :pv "power_wind" :wind}]
    (reduce (fn [acc {:keys [iso year variable value]}]
              (if-let [k (want variable)]
                (if-let [x (num value)]
                  (update-in acc [k iso] (fnil conj []) {:t (parse-long year) :known (+ lag (parse-long year)) :x (/ x 1000.0)})
                  acc)
                acc))
            {} (read-csv path))))

(defn read-owid-shares
  "{:pv {iso entries} :wind {iso entries}}: solar and wind shares of
  electricity, %, OWID energy data, known `lag` years on; World as WLD."
  [path lag]
  (reduce (fn [acc {:keys [country iso_code year solar_share_elec wind_share_elec]}]
            (let [iso (cond (= "World" country) "WLD"
                            (and (= 3 (count iso_code)) (not (str/starts-with? iso_code "OWID"))) iso_code)
                  y (parse-long year)]
              (if iso
                (reduce (fn [acc [k s]]
                          (if-let [x (num s)] (update-in acc [k iso] (fnil conj []) {:t y :known (+ y lag) :x x}) acc))
                        acc [[:pv solar_share_elec] [:wind wind_share_elec]])
                acc)))
          {} (read-csv path)))

(defn load-data
  "The model's data from the files under `dir` (the repo root)."
  [dir]
  (let [f #(str dir "/" %)
        trade (read-trade (f "data/derived/supply/tech-trade.csv"))
        cap (read-capacity (f "data/derived/material/capacity.csv") 2)
        shares (read-owid-shares (f "data/raw/material/owid-energy/owid-energy-data.csv") 1)
        sorted (fn [m] (update-vals m #(vec (sort-by :t %))))
        pv-adds (update-vals (sorted (:pv cap)) additions)
        wind-adds (update-vals (sorted (:wind cap)) additions)
        li (:li-ion trade)
        with-world (fn [m] (assoc m "WLD" (world-sum m)))]
    {:adds {:ev (with-world (:ev trade))
            :pv (with-world pv-adds)
            :wind (with-world wind-adds)
            :li-ion (with-world li)}
     :adopt {:ev (assoc (:ev-share trade) "WLD" (:ev-share-world trade))
             :pv (sorted (:pv shares))
             :wind (sorted (:wind shares))
             :li-ion (update-vals (with-world li) cumulative)}
     :ints (read-intensities (f "data/derived/supply/recipes-filtered.csv"))
     :priors (into {} (for [p (:recipes (recipes/priors))] [(:id p) p]))}))

(defn write-csv
  "iso,year,variable,value (the year known)."
  [path rows]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value\n")
    (doseq [{:keys [iso year variable value]} rows]
      (.write w (str iso "," year "," variable "," (format "%.6g" (double value)) "\n")))))
