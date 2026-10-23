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

  Technologies and their units of additions (bead bnm-1pq.25 for EV
  sales and the wind split):
    :ev      battery-electric cars sold: IEA Global EV Outlook 2026 via OWID
             (data/raw/material/owid-iea-ev-sales; tech-installs.csv,
             scripts/tech-demand-ref.py), 2010-, 62 countries, WLD IEA's
             own world total (its rest of world included). Sales, so a
             producer's home market counts: the old route, BACI HS17 870380
             imports at 1.8 t a car (`cars`), was 20-44% of world BEV sales
             in 2017-2024 and 0-9% of China's; it is kept as a cross-check
             only (load-data's :ev-imports, bnm.tech-demand-run section 5).
    :phev    plug-in hybrid cars sold, the same source: battery materials
             only, a BEV's battery x `phev-battery`.
    :pv      GW of solar added: the yearly rise in EIA installed capacity
             (data/derived/material/capacity.csv power_solar), falls
             counted as zero. The intensities are per watt of module
             (ITRPV, Wp); where EIA counts inverter (AC) capacity the
             additions, and so the demand, are low by the DC/AC ratio.
    :wind    GW of onshore wind added: the yearly rise in IRENASTAT's
             onshore capacity (data/raw/material/irena-stat), 2001-, the
             onshore coefficients.
    :wind-offshore  GW of offshore wind added, IRENASTAT's offshore
             capacity: wind-offshore/copper (8000 t/GW against 2900) and
             wind-offshore/rare-earths (239 against 14). There is no
             offshore steel prior: the onshore one is used, a lower bound
             (monopiles and jackets are heavier). World wind rare earths
             are 1.2-2.3x the all-onshore figure in 2010-2020 and 3.9x in
             2021 (20 GW offshore, China's rush), copper 1.03-1.4x.
    :li-ion  kWh of Li-ion accumulators traded: HS92 850780 imports (all
             accumulators other than lead-acid and NiCd: NiMH is in it)
             x kwh-per-tonne. A second route to battery demand, 1995-,
             kept out of the totals (an EV's battery would count twice).
             In 2021-2023 it is within 6% of the EV route's (BEV + PHEV)
             lithium, cobalt, nickel and graphite.
  Battery storage has no source on disk and is left out; so are EV buses,
  vans and trucks (IEA's car series only).

  Adoption (bnm.technology/fit-logistic, rolling, only what is known by
  the year):
    :ev      BEV share of new car sales (IEA), %, cap 100; demand follows
             the level (a flow), the car market held at its last known
             size.
    :phev    PHEV share of new car sales, the same.
    :pv      solar share of electricity generation (OWID), %, cap 100;
    :wind    wind share (onshore and offshore), the same; capacity added
             follows the share's derivative (total generation and
             capacity factor held).
    :wind-offshore  offshore GW installed, no cap (the exponential arm,
             never the logistic); additions follow the derivative.
    :li-ion  cumulative accumulator imports since 1995, no cap (the
             machinery's ceiling search, at most three times the highest
             level); additions follow the derivative.

  Projection (`project-growth`, bead bnm-1pq.19). A logistic fitted to
  adoption still on its exponential arm does not identify its ceiling: the
  fit puts the midpoint at or before the data's end and projects falling
  additions (world PV silver and silicon, the Li-ion trade route: projected
  falls against realised 5-year rises of about +1.1 log). The rule: the
  logistic only where bnm.technology's test says the ceiling is identified
  and the data have reached half the cap (a share's 100%; without a cap,
  never); otherwise the exponential arm, the slope of ln(adoption) over
  the last five data years known held for the horizon, the level capped
  at the cap. Additions on a falling share, or on an exponential that
  meets the cap within the horizon, are not projected. The rule was chosen
  after that failure was seen, and its half-the-cap condition after a
  first version (the identified test alone) was scored and changed
  little: on history, not a registered trial.

  The projected 5-year growth of demand for m (`growth5`) is the log of
  the projected demand five years after the base data year over demand in
  it: each technology's demand moved by its adoption's projected growth
  and by the intensity drift. It is written only when every technology in
  the total has a projection.

  Known years: BACI and EIA capacity data year + 2, OWID, IEA's EV sales
  and IRENA + 1 (`install-lag`), the recipe posterior of year t at t
  (recipes-filtered.csv's row t uses only the observations known by t).
  A row known in Y totals the technologies at one data year t: the
  earliest of their latest data years known by Y, a technology more than
  `stale-gap` years behind the newest left out; each at its t entry and
  the posterior of t, so never a posterior of a year after Y. Rows are by the year known; the data year they describe
  is tech-demand/state-year (bookkeeping). Values are as published now
  (revised), as everywhere in data/derived.

  Variables, per country (ISO3) and WLD (the sum of countries' additions;
  for the fits, the world's own adoption series), in tonnes:
    tech-demand/<m>             BEVs + PHEVs + PV + onshore + offshore wind
    tech-demand/<m>-<k>         one technology's part (k ev, phev, pv, wind,
                                wind-offshore, and
                                li-ion: the trade route, not in the total)
    tech-demand/<m>-growth5     projected 5-year log growth of the total
    tech-demand/<m>-li-ion-growth5  the same for the trade route
    tech-demand/<m>-count       technologies in the total (bookkeeping)
    tech-demand/<k>-additions   cars, GW or kWh
    tech-demand/<k>-adoption    the latest adoption level known
    tech-demand/<k>-speed, -midpoint  the fitted logistic
    tech-demand/<k>-identified  1 when bnm.technology calls its ceiling
                                identified, else 0
    tech-demand/<k>-logistic    1 when the logistic projects (`logistic?`),
                                0 when the exponential arm does
    tech-demand/<k>-slope       the exponential arm's slope
    tech-demand/state-year      the data year (bookkeeping)"
  (:require [bnm.csv :as csv]
            [bnm.recipes :as recipes]
            [bnm.technology :as tech]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ---------------------------------------------------------------- constants

(def kwh-per-tonne
  "kWh per tonne of traded accumulators, about 150 Wh/kg. Stated, not
  fitted, and not from a source on disk; derived from the priors: 0.12 kg
  Li per kWh (li-ion/lithium) is 17.3 mol, which in an NMC cathode
  (LiMO2, about 97 g/mol) is 1.7 kg of cathode, with 1.0 kg of graphite
  (li-ion/graphite) 2.7 kg of active material per kWh. Active material is
  taken as about 60% of a cell's mass (foils, electrolyte, separator and
  can the rest): 4.5 kg/kWh, 220 Wh/kg a cell; cells taken as about 65% of
  a pack's mass (casing, cooling, electronics): 6.9 kg/kWh, 145 Wh/kg a
  pack. HS 850780 holds both cells and packs, and NiMH (lighter in energy,
  about half), so 150 is the middle of a range of perhaps 100-220. At 150
  the priors put Li at 1.8% and graphite at 15% of the traded mass."
  150.0)

(def tonnes-per-car
  "Kerb mass of a battery-electric car, t. Stated, not fitted: a 55 kWh
  pack (ev/battery's mean) at kwh-per-tonne is 0.37 t, on a compact or
  mid-size glider of about 1.4 t. The DOE Critical Materials Assessment
  2023 (Table B.2, on disk) has 2.07 t for the 2020 sales-weighted US
  light-duty vehicle of every drivetrain, a heavier fleet than the world's
  traded BEVs; small Chinese EVs are near 1 t. BACI's tonnes are the
  goods' net mass. The error is proportional: a world mean 10% lighter
  would mean 10% more cars and demand."
  1.8)

(def phev-battery
  "A plug-in hybrid's battery over a battery-electric car's (ev/battery).
  Stated, not fitted, and not from a source on disk: PHEV packs are about
  15-25 kWh against the BEV prior's 55 (Chinese extended-range models of
  2023- reach 30-40), so a third, in a range of perhaps 0.25-0.5. PHEVs
  add no copper here (their motor and wiring are not in a prior): a lower
  bound on copper."
  0.33)

(def stale-gap
  "Data years a technology may lag the newest one and still join a
  country's total (`rows-at`)."
  2)

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
   :phev {:materials {"lithium" [["ev/battery" "li-ion/lithium"] (* phev-battery 1e-3)]
                      "cobalt" [["ev/battery" "li-ion/cobalt"] (* phev-battery 1e-3)]
                      "nickel" [["ev/battery" "li-ion/nickel"] (* phev-battery 1e-3)]
                      "graphite" [["ev/battery" "li-ion/graphite"] (* phev-battery 1e-3)]}
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
   :wind-offshore {:materials {"copper" [["wind-offshore/copper"] 1.0]
                               "steel" [["wind-onshore/steel"] 1.0]
                               "rare-earths" [["wind-offshore/rare-earths"] 1.0]}
                   :kind :stock :fit {} :total? true}
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

(def window
  "Data years of adoption the exponential arm's slope is fitted on."
  5)

(defn exp-slope
  "The slope of ln x on t (least squares) over the positive points of the
  last `window` data years of obs ([[t x]...], oldest first), nil with
  fewer than three."
  [obs]
  (when-let [tl (some-> (seq obs) last first)]
    (let [pts (for [[t x] obs :when (and (> t (- tl window)) x (pos? (double x)))]
                [(double t) (Math/log (double x))])]
      (when (>= (count pts) 3)
        (let [n (count pts)
              mt (/ (reduce + (map first pts)) n)
              mz (/ (reduce + (map second pts)) n)
              stt (reduce + (map (fn [[t _]] (* (- t mt) (- t mt))) pts))
              stz (reduce + (map (fn [[t z]] (* (- t mt) (- z mz))) pts))]
          (when (pos? stt) (/ stz stt)))))))

(defn logistic?
  "Whether the logistic projects: its ceiling identified by bnm.technology's
  test and the data at half the cap at least. A share's physical ceiling
  is its cap; a fitted ceiling far below it is a plateau the data have not
  shown to be one (bnm.technology: identified ceilings on plateaus are
  often passed later). Without a cap there is no a priori ceiling to
  identify against: never."
  [fit obs cap]
  (boolean (and (:identified fit) cap (seq obs)
                (>= (reduce max (map #(double (second %)) obs)) (* 0.5 (double cap))))))

(defn project-growth
  "The projection rule (bead bnm-1pq.19): the log growth over h years from
  data year t of a technology's demand, from its fit and the adoption
  points it was fitted on (obs, known by the row's year).

    `logistic?`: the logistic, `growth`;
    otherwise the exponential arm: E(u) = x_last exp(s (u - t_last)), s
    the `exp-slope`, held for the horizon and capped at cap (nil: none).
      :level  ln(C(t+h) / C(t)), C = min(cap, E);
      :stock  additions follow E's derivative: s h, nil when s <= 0 or
              E reaches the cap within the horizon.
  nil without a fit or a slope.

  Chosen after the logistic's failure was seen (world PV, Li-ion trade:
  projected falls against realised rises), not registered before the
  data: the design years are history. A first version used
  bnm.technology's identified test alone; it calls world PV's and wind's
  ceilings identified at shares of 1-8% (a ceiling 1.2-2 times the data)
  and changed little, so the half-the-cap condition was added on that
  second look."
  [fit obs kind cap t h]
  (when fit
    (if (logistic? fit obs cap)
      (growth fit kind t h)
      (when-let [s (exp-slope obs)]
        (let [[tl xl] (last obs)
              E #(* (double xl) (Math/exp (* s (- (double %) (double tl)))))
              C #(if cap (min (double cap) (E %)) (E %))]
          (case kind
            :level (let [a (C t) b (C (+ t h))]
                     (when (and (pos? a) (pos? b)) (Math/log (/ b a))))
            :stock (when (and (pos? s) (or (nil? cap) (<= (E (+ t h)) (double cap))))
                     (* s h))))))))

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
        newest (some->> (filter #(get-in techs [% :total?]) (keys latest)) seq (map #(:t (latest %))) (reduce max))
        total-ks (filter #(and (get-in techs [% :total?]) (>= (:t (latest %)) (- newest stale-gap))) (keys latest))
        t (when (seq total-ks) (reduce min (map #(:t (latest %)) total-ks)))
        at-t (fn [k] (some #(when (and (= t (:t %)) (<= (:known %) y)) %) (get-in adds [k iso])))
        latest (merge latest (into {} (for [k total-ks :let [e (at-t k)] :when e] [k e])))
        obs (into {} (for [k (keys techs)] [k (tech/as-of (get-in adopt [k iso] []) y)]))
        fits (into {} (for [k (keys techs)] [k (tech/fit-logistic (obs k) (fit-opts k))]))
        demand (fn [k] (let [e (latest k)]
                         (into {} (filter val (material-demand k (:x e) (into {} (for [[r by] ints] [r (into {} (filter #(<= (key %) y) by))])) (:t e))))))
        totals (into {} (for [k total-ks :when (= t (:t (latest k)))] [k (demand k)]))
        li (when-let [e (latest :li-ion)] (demand :li-ion))
        gk (fn [base] (fn [k] (project-growth (fits k) (obs k) (get-in techs [k :kind]) (get-in techs [k :fit :cap]) base horizon)))
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
                     (row iso y (v k "midpoint") (:midpoint f))
                     (when f (row iso y (v k "identified") (if (:identified f) 1.0 0.0)))
                     (when f (row iso y (v k "logistic") (if (logistic? f (obs k) (get-in techs [k :fit :cap])) 1.0 0.0)))
                     (when f (row iso y (v k "slope") (exp-slope (obs k))))]]
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

(def install-series
  {"bev-sales" :bev-sales "phev-sales" :phev-sales "bev-share" :bev-share "phev-share" :phev-share
   "wind-onshore" :wind-onshore "wind-offshore" :wind-offshore})

(defn read-installs
  "{series {iso entries}} from tech-installs.csv (scripts/tech-demand-ref.py),
  known `lag` years on: :bev-sales and :phev-sales cars, :bev-share and
  :phev-share % of new cars (IEA Global EV Outlook via OWID, WLD IEA's
  world), :wind-onshore and :wind-offshore GW installed (IRENASTAT's MW)."
  [path lag]
  (let [m (reduce (fn [acc {:keys [iso year series value]}]
                    (let [k (install-series series) x (num value) y (parse-long year)]
                      (if (and k x)
                        (update-in acc [k iso] (fnil conj [])
                                   {:t y :known (+ y lag) :x (if (#{:wind-onshore :wind-offshore} k) (/ x 1000.0) x)})
                        acc)))
                  {} (read-csv path))]
    (update-vals m (fn [by] (update-vals by #(vec (sort-by :t %)))))))

(defn load-installs
  "{:adds :adopt} of the technologies read-installs feeds: :ev and :phev
  cars sold (WLD IEA's own world total, which holds the countries IEA does
  not list; the sum of the countries where it is missing), adopted as
  their shares of new car sales; :wind-offshore GW added (WLD the sum of
  countries), adopted as GW installed (no cap: `logistic?` never, the
  exponential arm); :wind onshore GW added (its adoption, OWID's wind
  share, is load-data's)."
  [ins]
  (let [world-or-sum (fn [m] (if (get m "WLD") m (assoc m "WLD" (world-sum m))))
        adds (fn [k] (let [m (update-vals (get ins k {}) additions)] (assoc (dissoc m "WLD") "WLD" (world-sum (dissoc m "WLD")))))]
    {:adds {:ev (world-or-sum (get ins :bev-sales {}))
            :phev (world-or-sum (get ins :phev-sales {}))
            :wind (adds :wind-onshore)
            :wind-offshore (adds :wind-offshore)}
     :adopt {:ev (get ins :bev-share {})
             :phev (get ins :phev-share {})
             :wind-offshore (let [m (get ins :wind-offshore {})] (assoc m "WLD" (world-sum m)))}}))

(def install-lag
  "Data years before IEA's EV sales and IRENA's capacities are known: the
  Global EV Outlook of year t+1 (spring) and IRENA's statistics of t+1
  (March, in full by July) carry year t."
  1)

(defn load-data
  "The model's data from the files under `dir` (the repo root). EVs (BEV
  and PHEV) are IEA's sales and wind IRENA's onshore and offshore
  capacity (bead bnm-1pq.25); the BACI EV imports are kept under
  :ev-imports for the cross-check only."
  [dir]
  (let [f #(str dir "/" %)
        trade (read-trade (f "data/derived/supply/tech-trade.csv"))
        cap (read-capacity (f "data/derived/material/capacity.csv") 2)
        shares (read-owid-shares (f "data/raw/material/owid-energy/owid-energy-data.csv") 1)
        ins (load-installs (read-installs (f "data/derived/supply/tech-installs.csv") install-lag))
        sorted (fn [m] (update-vals m #(vec (sort-by :t %))))
        pv-adds (update-vals (sorted (:pv cap)) additions)
        li (:li-ion trade)
        with-world (fn [m] (assoc m "WLD" (world-sum m)))]
    {:adds (merge (:adds ins)
                  {:pv (with-world pv-adds)
                   :li-ion (with-world li)})
     :adopt (merge (:adopt ins)
                   {:pv (sorted (:pv shares))
                    :wind (sorted (:wind shares))
                    :li-ion (update-vals (with-world li) cumulative)})
     :ev-imports {:cars (with-world (:ev trade))
                  :share (assoc (:ev-share trade) "WLD" (:ev-share-world trade))}
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
