(ns bnm.interactions
  "Trials T7 and T3 of bnm-3qu (docs/LIT-NEXT-STEPS.md, docs/T3-PAIRS.md):
  named stock x trigger interactions over the year node's base, and the
  principal contradiction as a softmax with a temperature.

  The rows are T2's (bnm.calibration-regime): each kind's year-end
  forecast of t44-world-norel, an onset in (t, t + 2] seen from the end of
  year t, L the base's log odds; origins from --from, windows ending by
  --to. The states and triggers are read as known at the end of year t.

  States (stretched = high; each demeaned by year, then scaled by its
  spread over the design years): bnm.horizon-profile's named states with
  reserves turned into low reserves and food import exposure replaced by
  the plain food import share (the grain price gap is a trigger here), and
  the agriculture share of GDP.

  Triggers (stress = high; scaled by their design-year spread, not
  demeaned: a world tightening is a trigger too):
    house-growth       3-year real house-price growth (BIS, JST fallback;
                       calibration-regime/house-growth)
    equity-drawdown    log share prices' fall from their 24-month peak
                       (OECD, the latest month known)
    tightening         12-month change in the 3-month rate (OECD), else
                       the policy rate (BIS), else JST's short rate a year
                       late (data year d known at d + 1)
    tot-fall           12-month fall in the log commodity terms of trade
                       (IMF, from 1980)
    export-price-fall  the fall in the country's export prices from world
                       commodity prices: fuel, metal and food shares of
                       merchandise exports (WDI, at most 5 years old) times
                       the year's change in log oil, mean metal and mean
                       grain prices (the cycle stocks' world rows)
    grain-gap          the world grain price gap (wheat, maize, rice;
                       the same at every country)
    drought            minus the mean log precipitation ratio over the
                       latest 12 months known (CRU via CCKP)
    election           a legislative or presidential election held in
                       year t (V-Dem; an election is public when held,
                       though V-Dem codes it the next March)
    neighbour-conflict log(1 + onsets of conflict, war, violence surges
                       and coups in the other countries of the subregion
                       over the 36 months to the origin, as known: each
                       catalogue's own known-lag)

  T7 (diagnostic): --export writes the design rows (no outcome after
  2002) for scripts/t7_shap.py, the boosted trees and SHAP interactions.

  T3: for each pair (`pairs`) a logit over its kinds

    logit(y) = a_k + b_k (L - m_k) + alpha s + beta g + gamma s g

  a_k, b_k the recalibration nuisance per kind (as calibration-regime), s
  the state, g the trigger; gamma tested by the year-clustered score test
  (calibration-regime/score-test) at the fit without it, one-sided in the
  pair's sign; its null from permutations of whole country series of the
  state (demeaned and scaled again each draw), its placebo the state read
  at the next country with it that year. Holm over the pairs on the
  permutation p.

  The principal contradiction: in each group, the gauges of a row are its
  kinds' pairs' products c_j = sign_j s_j g_j / sd_j (sd_j over the design
  rows). They are aggregated as A = sum softmax(tau c)_j c_j over the
  gauges known: tau 0 the mean, tau infinite the argmax. logit(y) =
  nuisance + theta A, the nuisance the recalibration and every state's and
  trigger's level in the group's pairs (unknown: 0 and a flag). tau is
  profiled over a grid; the likelihood ratios against tau 0 (the mean) and
  tau infinite (the argmax) are reported. Out of sample (leave one era out
  when the test years are the design years, else fitted on the design
  years): the recalibration, the levels, the pairs entered one by one, the
  mean, the argmax and tau chosen on the fitting rows, on log score per
  row with 90% intervals from resampled origin years.

  No outcome after --to is read; scales, centres and out-of-sample fits
  come from the design years (--design-from, --design-to) only.

    jolt -A:tools -M -m bnm.interactions [--run t44-world-norel]
      [--from 1960] [--to 2002] [--design-from 1960] [--design-to 2002]
      [--draws 200] [--seed 1] [--out data/runs/sequencing/interactions-t3-design.tsv]
      [--export data/runs/sequencing/t7-design-rows.tsv]"
  (:require [augur.logit :as lg]
            [augur.math :as m]
            [bnm.calibration-regime :as cr]
            [bnm.geo :as geo]
            [bnm.horizon-profile :as hp]
            [bnm.kinds :as kinds]
            [bnm.macrohistory :as mh]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- readers of monthly series as known at a year end ----------------------------

(defn latest-known
  "[period value] of the latest period in `obs` ([[period-month
  avail-year value]]) published by the end of year y, and no more than
  `max-age` months before December of y; nil if none."
  [obs y max-age]
  (let [cut (- (+ (* 12 y) 11) max-age)]
    (reduce (fn [best [p a x]]
              (if (and (<= a y) (>= p cut) (or (nil? best) (> p (first best)))) [p x] best))
            nil obs)))

(defn- by-period [obs] (into {} (map (fn [[p _ x]] [p x])) obs))

(defn monthly-change
  "x at the latest known period p less x at p - `span`."
  [obs y span max-age]
  (when-let [[p x] (latest-known obs y max-age)]
    (when-let [x0 (get (by-period obs) (- p span))]
      (- (double x) (double x0)))))

(defn window-mean
  "The mean of x over the `months` periods up to the latest known."
  [obs y months max-age]
  (when-let [[p _] (latest-known obs y max-age)]
    (let [bp (by-period obs)
          xs (keep #(get bp %) (range (- p (dec months)) (inc p)))]
      (when (seq xs) (/ (reduce + xs) (count xs))))))

(defn drawdown
  "log of the peak over the `window` months up to the latest known less
  log x there (x > 0): 0 at a peak."
  [obs y window max-age]
  (when-let [[p x] (latest-known obs y max-age)]
    (when (pos? x)
      (let [bp (by-period obs)
            peak (reduce max x (keep #(let [v (get bp %)] (when (and v (pos? v)) v)) (range (- p window) p)))]
        (- (Math/log peak) (Math/log x))))))

(defn peer-count
  "Onsets (months) of `onsets` ({unit sorted-set}) at the peers of `unit`
  (`peers`: {unit [units]}, itself left out) in (o - lag - window, o - lag]."
  [onsets peers unit o lag window]
  (let [hi (- o lag) lo (- hi window)]
    (reduce + (for [p (get peers unit) :when (not= p unit)]
                (count (subseq (get onsets p (sorted-set)) > lo <= hi))))))

;; --- panels ----------------------------------------------------------------------

(defn standardize
  "`panel` ({iso {year x}}) less its mean, over its spread, both over years
  dfrom..dto (the design years) only."
  [panel dfrom dto]
  (let [xs (for [[_ byy] panel [y x] byy :when (<= dfrom y dto)] (double x))
        n (count xs)
        mu (if (pos? n) (/ (reduce + xs) n) 0.0)
        sd (if (pos? n) (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) n)) 1.0)
        sd (if (> sd 1e-12) sd 1.0)]
    (update-vals panel (fn [byy] (update-vals byy #(/ (- % mu) sd))))))

(defn state-transform
  "A state as the tests read it: demeaned by year, then standardized on
  the design years."
  [panel dfrom dto]
  (standardize (cr/demean panel) dfrom dto))

(defn- slow-monthly [v] (#'cr/slow-sub v))

(defn- panel-of
  "{iso {year (f obs y)}} over the years of horizon-profile."
  [by-iso f]
  (into {} (for [[iso obs] by-iso :when (not= iso "WLD")
                 :let [z (into (sorted-map) (for [y hp/years :let [x (f obs y)] :when (some? x)] [y (double x)]))]
                 :when (seq z)]
             [iso z])))

(defn state-panels
  "{state {iso {year value}}}, raw (signed: stretched is high)."
  []
  (let [hps (hp/state-panels)
        slow (hp/read-slow-annual "data/slow/series.tsv" ["food/imports_share" "economy/agriculture_share"])]
    (merge (select-keys hps ["credit-gap" "keen-debt-gap" "debt-service" "youth-bulge" "elite-overproduction"
                             "technology-gap" "fuel-price-gap" "anocracy"])
           {"low-reserves" (update-vals (hps "reserves") (fn [byy] (update-vals byy -)))
            "food-import-share" (#'hp/known-panel (get slow "food/imports_share") 3)
            "agri-share" (#'hp/known-panel (get slow "economy/agriculture_share") 3)})))

(def state-names
  ["credit-gap" "keen-debt-gap" "debt-service" "low-reserves" "fuel-price-gap" "youth-bulge"
   "elite-overproduction" "anocracy" "technology-gap" "food-import-share" "agri-share"])

(def trigger-names
  ["house-growth" "equity-drawdown" "tightening" "tot-fall" "export-price-fall" "grain-gap"
   "drought" "election" "neighbour-conflict"])

(def political-kinds ["conflict_onset" "war_onset" "violence_surge" "coup"])

(defn- world-change
  "{year change} of the mean of the world rows of cycle log prices `vs`
  between consecutive years known."
  [stocks vs]
  (let [ws (map #(hp/as-known (get-in stocks [% "WLD"]) hp/years 1) vs)
        mean (into (sorted-map) (for [y hp/years :let [xs (keep #(get % y) ws)] :when (= (count xs) (count vs))]
                                  [y (/ (reduce + xs) (count xs))]))]
    (into (sorted-map) (for [[y x] mean :let [x0 (get mean (dec y))] :when x0] [y (- x x0)]))))

(defn trigger-panels
  "{trigger panel}: {iso {year value}}, or {:world {year value}} for a
  series the same at every country. Raw, stress high."
  [cats]
  (let [jst (group-by :iso (mh/read-jst "data/raw/slow/jst/jst.csv"))
        r3 (slow-monthly "finance/rate_3m")
        pol (slow-monthly "finance/policy_rate")
        jst-stir (into {} (for [[iso rs] jst
                                :let [s (into {} (for [r rs :when (:stir r)] [(:year r) (:stir r)]))
                                      z (into (sorted-map) (for [y hp/years :let [a (s (dec y)) b (s (- y 2))] :when (and a b)] [y (- a b)]))]
                                :when (seq z)]
                            [iso z]))
        tight (merge-with merge jst-stir
                          (panel-of pol #(monthly-change %1 %2 12 12))
                          (panel-of r3 #(monthly-change %1 %2 12 12)))
        stocks (#'hp/table->recs ["data/derived/stocks/cycles.csv"])
        d-oil (world-change stocks ["cycles/oil-wti-log-price"])
        d-metal (world-change stocks ["cycles/copper-log-price" "cycles/aluminium-log-price" "cycles/nickel-log-price"])
        d-grain (world-change stocks ["cycles/wheat-log-price" "cycles/maize-log-price" "cycles/rice-log-price"])
        shares (hp/read-slow-annual "data/slow/series.tsv" ["energy/fuel_exports_share" "trade/metal_exports_share" "food/exports_share"])
        sh (fn [v] (#'hp/known-panel (get shares v) 5))
        [fuel metal food] (map sh ["energy/fuel_exports_share" "trade/metal_exports_share" "food/exports_share"])
        export-fall (into {} (for [iso (distinct (concat (keys fuel) (keys metal) (keys food)))
                                   :let [z (into (sorted-map)
                                                 (for [y hp/years
                                                       :let [f (get-in fuel [iso y]) mt (get-in metal [iso y]) fd (get-in food [iso y])
                                                             o (d-oil y) me (d-metal y) g (d-grain y)]
                                                       :when (and f mt fd o me g)]
                                                   [y (- (/ (+ (* f o) (* mt me) (* fd g)) 100.0))]))]
                                   :when (seq z)]
                               [iso z]))
        [_ grain] (cr/food-parts)
        elections (let [a (hp/read-slow-annual "data/slow/series.tsv" ["regime/election_legislative" "regime/election_presidential"])
                        ;; by data year: held in year t, whenever V-Dem publishes it
                        by-d (fn [v] (update-vals (get a v) (fn [recs] (into {} (for [[d [_ x]] recs] [d x])))))
                        l (by-d "regime/election_legislative") p (by-d "regime/election_presidential")]
                    (into {} (for [iso (distinct (concat (keys l) (keys p)))
                                   :let [z (into (sorted-map) (for [y hp/years
                                                                    :let [a (get-in l [iso y]) b (get-in p [iso y])]
                                                                    :when (or a b)]
                                                                [y (if (or (and a (pos? a)) (and b (pos? b))) 1.0 0.0)]))]
                                   :when (seq z)]
                               [iso z])))
        subs (group-by #(first (geo/parents %)) (geo/countries))
        peers (into {} (for [[_ cs] subs c cs] [c cs]))
        neighbour (into {} (for [c (keys peers)
                                 :let [z (into (sorted-map)
                                               (for [y hp/years
                                                     :let [o (+ (* 12 y) 11)]]
                                                 [y (Math/log1p (reduce + (for [k political-kinds :let [cat (cats k)] :when cat]
                                                                            (peer-count (:onsets cat) peers c o (or (:known-lag cat) 12) 36))))]))]]
                             [c z]))]
    {"house-growth" (cr/house-growth)
     "equity-drawdown" (panel-of (slow-monthly "markets/share_prices") #(drawdown %1 %2 24 12))
     "tightening" tight
     "tot-fall" (panel-of (update-vals (slow-monthly "trade/commodity_tot") (fn [obs] (vec (for [[p a x] obs :when (pos? x)] [p a (Math/log x)]))))
                          #(some-> (monthly-change %1 %2 12 12) -))
     "export-price-fall" export-fall
     "grain-gap" {:world grain}
     "drought" (panel-of (slow-monthly "climate/precipitation_anomaly") #(some-> (window-mean %1 %2 12 24) -))
     "election" elections
     "neighbour-conflict" neighbour}))

(defn trigger-transform
  "A trigger as read: standardized on the design years (a world series
  over its years)."
  [panel dfrom dto]
  (if-let [w (:world panel)]
    {:world (get (standardize {"WLD" w} dfrom dto) "WLD")}
    (standardize panel dfrom dto)))

(defn reader
  "(fn [c t]) of a transformed panel."
  [panel]
  (if-let [w (:world panel)] (fn [_ t] (get w t)) (fn [c t] (get-in panel [c t]))))

;; --- the principal contradiction -----------------------------------------------

(defn softmax-agg
  "sum_j softmax(tau c)_j c_j over the known (non-nil) cs: tau 0 the mean,
  ##Inf the largest. nil when none is known."
  [cs tau]
  (let [cs (vec (keep identity cs))]
    (when (seq cs)
      (if (Double/isInfinite (double tau))
        (apply max cs)
        (let [mx (apply max cs)
              ws (mapv #(Math/exp (* tau (- % mx))) cs)
              z (reduce + ws)]
          (/ (reduce + (map * ws cs)) z))))))

(defn- recal-x
  "1, the other kinds' dummies, L - m, (L - m) x dummies."
  [kind L kinds centres]
  (let [others (vec (rest kinds))
        l (- (double L) (double (centres kind)))
        ds (mapv #(if (= kind %) 1.0 0.0) others)]
    (vec (concat [1.0] ds [l] (map #(* l %) ds)))))

(defn gauge-design
  "Rows with :x the recalibration, the nuisance :n and the aggregate of
  :gauges at `tau` last; rows without a known gauge dropped."
  [rows kinds centres tau]
  (vec (for [{:keys [kind L gauges n] :as r} rows
             :let [a (softmax-agg gauges tau)] :when a]
         (assoc r :x (conj (into (recal-x kind L kinds centres) (map double n)) (double a)) :w 1.0 :o 0.0))))

(defn linear-design
  "Rows with :x the recalibration, the nuisance :n and each gauge (0 where
  unknown): the interactions entered one by one."
  [rows kinds centres]
  (mapv (fn [{:keys [kind L gauges n] :as r}]
          (assoc r :x (-> (recal-x kind L kinds centres) (into (map double n)) (into (map #(double (or % 0.0)) gauges)))
                 :w 1.0 :o 0.0))
        rows))

(defn- loglik [rows b]
  (reduce + (for [r rows :let [p (lg/predict b (:x r) 0.0)]] (Math/log (max 1e-300 (if (:y r) p (- 1.0 p)))))))

(defn- fit-design [rows] (lg/fit rows (count (:x (first rows))) {:prior 1e-4}))

(defn tau-profile
  "The rows' log likelihood at each tau of `taus` (theta and the nuisance
  refitted): {:tau the best, :theta its theta, :profile [[tau ll theta]],
  :lr-vs-mean 2(ll best - ll tau 0), :lr-vs-max 2(ll best - ll tau inf)}."
  [rows kinds centres taus]
  (let [prof (vec (for [tau taus
                        :let [d (gauge-design rows kinds centres tau) b (fit-design d)]]
                    [tau (loglik d b) (aget ^doubles b (dec (alength ^doubles b)))]))
        [tb lb th] (apply max-key second prof)
        at (fn [t] (some (fn [[t' l _]] (when (= t' t) l)) prof))]
    {:tau tb :theta th :profile prof :ll lb
     :lr-vs-mean (when-let [l0 (at 0.0)] (* 2.0 (- lb l0)))
     :lr-vs-max (when-let [li (at ##Inf)] (* 2.0 (- lb li)))}))

;; --- the pair tests -------------------------------------------------------------

(defn holm
  "Holm-adjusted p values, in the input's order."
  [ps]
  (let [n (count ps)
        order (sort-by #(nth ps %) (range n))
        adj (loop [[i & more] order k 0 run 0.0 acc {}]
              (if (nil? i) acc
                  (let [a (max run (min 1.0 (* (- n k) (double (nth ps i)))))]
                    (recur more (inc k) a (assoc acc i a)))))]
    (mapv adj (range n))))

(defn pair-design
  "Rows {:kind :L :s :tr} with :x the recalibration then s and the
  trigger, :z [s x trigger]."
  [rows kinds centres]
  (mapv (fn [{:keys [kind L s tr] :as r}]
          (assoc r :x (into (recal-x kind L kinds centres) [(double s) (double tr)])
                 :z [(* (double s) (double tr))] :w 1.0 :o 0.0))
        rows))

(defn pair-score
  "gamma's year-clustered score test on rows {:kind :L :s :tr :y :g}:
  {:est (one-step) :z (sandwich) :n :pos}."
  [rows kinds centres]
  (when-let [s (and (seq rows) (pos? (count (filter :y rows))) (cr/score-test (pair-design rows kinds centres)))]
    {:est ((:est s) 0) :z ((:zs s) 0) :n (:n s) :pos (:pos s)}))

(defn pair-test
  "pair-score with gamma's maximum-likelihood fit: :coef :se :z-ml."
  [rows kinds centres]
  (when-let [s (pair-score rows kinds centres)]
    (let [f (cr/full-fit (pair-design rows kinds centres))]
      (assoc s :coef ((:coef f) 0) :se ((:se f) 0) :z-ml ((:z f) 0)))))

;; --- pairs and rows ------------------------------------------------------------

(def groups
  {"financial" ["banking_crisis" "currency_crisis" "sovereign_default" "growth_collapse"
                "fiscal_consolidation" "inflation_crisis" "market_crash"]
   "political" ["protest_surge" "violence_surge" "coup" "conflict_onset" "war_onset"]})

(def pairs
  "The pairs T3 tests (docs/T3-PAIRS.md): credit gap x house prices named
  in advance from the literature (Greenwood et al. 2022; Richter et al.
  2021) and kept by T2's record; the rest chosen by T7 on the design
  years, each over its whole group (the trees did not tell the kinds
  apart), signed as T7 found them. :sign the expected sign of gamma."
  [{:name "credit-x-house" :group "financial" :state "credit-gap" :trigger "house-growth"
    :kinds ["banking_crisis"] :sign 1 :source "literature"}
   {:name "youth-x-grain" :group "financial" :state "youth-bulge" :trigger "grain-gap"
    :kinds (groups "financial") :sign -1 :source "T7"}
   {:name "youth-x-neighbour" :group "financial" :state "youth-bulge" :trigger "neighbour-conflict"
    :kinds (groups "financial") :sign 1 :source "T7"}
   {:name "technology-x-grain" :group "political" :state "technology-gap" :trigger "grain-gap"
    :kinds (groups "political") :sign 1 :source "T7"}])

(defn frames
  "{kind rows} (calibration-regime/frames-of) for the kinds of `groups`."
  [run cats from to]
  (cr/frames-of run cats (distinct (mapcat val groups)) from to))

(defn export-rows!
  "The design rows for T7: one line per kind's row with the base, the
  transformed states and triggers (NA where unknown)."
  [path fr groups sreaders treaders]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w (str (str/join "\t" (concat ["group" "kind" "iso" "t" "y" "L"] state-names trigger-names)) "\n"))
    (doseq [[g ks] groups k ks r (get fr k)
            :let [v (fn [rd] (let [x (rd (:c r) (:t r))] (if (some? x) (format "%.5f" (double x)) "NA")))]]
      (.write w (str (str/join "\t" (concat [g k (:c r) (:t r) (if (:y r) 1 0) (format "%.5f" (:L r))]
                                            (map #(v (sreaders %)) state-names)
                                            (map #(v (treaders %)) trigger-names)))
                     "\n")))))

(defn pair-rows
  "Rows {:kind :c :t :g :L :y :s :tr} of `fr` ({kind rows}) for `kinds`
  where the state (reader `sr`) and the trigger (`tr`) are known."
  [fr kinds sr tr]
  (vec (for [k kinds r (get fr k)
             :let [s (sr (:c r) (:t r)) g (tr (:c r) (:t r))]
             :when (and (some? s) (some? g))]
         (assoc r :s (double s) :tr (double g)))))

(defn gauge-rows
  "The rows of a group's kinds with :gauges (one per pair of the group,
  sign_j s g / sd_j, nil where unknown or not the kind's) and :n (each
  state's and trigger's level, 0 where unknown, and a flag of unknown);
  rows with no gauge known dropped. `sds` {pair name sd}."
  [fr kinds gpairs sreaders treaders sds]
  (let [inputs (vec (distinct (concat (map (fn [p] [:s (:state p)]) gpairs) (map (fn [p] [:t (:trigger p)]) gpairs))))]
    (vec (for [k kinds r (get fr k)
               :let [c (:c r) t (:t r)
                     val-of (fn [[w nm]] ((if (= w :s) (sreaders nm) (treaders nm)) c t))
                     gs (mapv (fn [{:keys [state trigger sign kinds name]}]
                                (when (some #{k} kinds)
                                  (let [s ((sreaders state) c t) g ((treaders trigger) c t)]
                                    (when (and (some? s) (some? g)) (/ (* sign s g) (sds name))))))
                              gpairs)]
               :when (some some? gs)]
           (assoc r :gauges gs
                  :n (vec (mapcat (fn [in] (let [v (val-of in)] (if (some? v) [(double v) 0.0] [0.0 1.0]))) inputs)))))))

;; --- the run ------------------------------------------------------------------------

(defn parse-opts [args]
  (let [o (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        n (fn [k d] (or (some-> (o k) parse-long) d))]
    {:run (or (:run o) "t44-world-norel")
     :from (n :from 1960) :to (n :to 2002)
     :design-from (n :design-from 1960) :design-to (n :design-to 2002)
     :draws (n :draws 200) :seed (n :seed 1)
     :out (or (:out o) "data/runs/sequencing/interactions-t3-design.tsv")
     :export (:export o)}))

(def taus [0.0 0.25 0.5 1.0 2.0 4.0 8.0 ##Inf])

(defn- f3 [x] (if (and x (not (Double/isNaN (double x)))) (format "%+.3f" (double x)) "NA"))

(defn- mean-log [rows ps] (/ (reduce + (map (fn [r p] (Math/log (max 1e-300 (if (:y r) p (- 1.0 p))))) rows ps)) (max 1 (count rows))))

(defn- per-row-scores
  "Log scores per test row of each model fitted on `drows`: {:t ... model
  score}. Models: recal, levels, pairs (one by one), mean, argmax, tau
  (chosen on drows by the profile); and the tau chosen."
  [drows trows kinds centres]
  (let [score (fn [dd td] (let [b (fit-design dd)] (mapv #(let [p (lg/predict b (:x %) 0.0)] (Math/log (max 1e-300 (if (:y %) p (- 1.0 p))))) td)))
        strip (fn [rows f] (mapv #(assoc % :x (f (:x %))) rows))
        nk (count (recal-x (first kinds) 0.0 kinds (constantly 0.0)))
        nn (count (:n (first drows)))
        lin-d (linear-design drows kinds centres) lin-t (linear-design trows kinds centres)
        recal (score (strip lin-d #(subvec % 0 nk)) (strip lin-t #(subvec % 0 nk)))
        levels (score (strip lin-d #(subvec % 0 (+ nk nn))) (strip lin-t #(subvec % 0 (+ nk nn))))
        pairs (score lin-d lin-t)
        at (fn [tau] (score (gauge-design drows kinds centres tau) (gauge-design trows kinds centres tau)))
        tau (:tau (tau-profile drows kinds centres taus))]
    {:tau tau
     :rows (mapv (fn [r a b c d e f] {:t (:t r) :recal a :levels b :pairs c :mean d :argmax e :tau f})
                 trows recal levels pairs (at 0.0) (at ##Inf) (at tau))}))

(defn run-t3
  [{:keys [run from to design-from design-to draws seed out]} cats raw-states raw-triggers]
  (let [rotation? (and (= from design-from) (= to design-to))
        fr (frames run cats from to)
        dfr (if rotation? fr (frames run cats design-from design-to))
        st (fn [panel] (reader (state-transform panel design-from design-to)))
        sreaders (into {} (for [s state-names] [s (st (raw-states s))]))
        treaders (into {} (for [t trigger-names] [t (reader (trigger-transform (raw-triggers t) design-from design-to))]))
        buf (StringBuilder.)
        say (fn [s] (println s) (flush) (.append buf (str s "\n")))
        g (m/rng seed)]
    (say (str "# T3: stock x trigger interactions over " run "'s year node; test rows: origins " from "-" (- to 2) " (windows to " to
              "); scales, centres and out-of-sample fits: design years " design-from "-" design-to "; " draws " permutations, seed " seed))
    (say "# logit(y) = a_k + b_k (L - m_k) + alpha s + beta g + gamma s g; y an onset in (t, t+2]; L the base's log odds; clusters origin years; s demeaned by year, s and g scaled on the design years")
    (say "pair\tname\tsource\tstate\ttrigger\tkinds\tsign\tn\tonsets\tgamma (ML)\tse\tz (ML)\tone-step\tscore z\tplacebo score z\tnull mean z\tp (one-sided, permutation)\tHolm")
    (let [res (vec (for [{:keys [name state trigger kinds sign source]} pairs]
                     (let [centres (cr/centres-of (select-keys dfr kinds) [design-from design-to])
                           tr (treaders trigger)
                           rows (pair-rows fr kinds (sreaders state) tr)
                           obs (pair-test rows kinds centres)
                           plc (pair-score (pair-rows fr kinds (st (cr/placebo-panel (raw-states state))) tr) kinds centres)
                           nulls (vec (keep (fn [_] (:z (pair-score (pair-rows fr kinds (st (cr/permute-panel (raw-states state) g)) tr) kinds centres)))
                                            (range draws)))
                           p (/ (+ 1.0 (count (filter #(>= (* sign %) (* sign (:z obs))) nulls))) (+ 1.0 (count nulls)))]
                       {:name name :source source :state state :trigger trigger :kinds kinds :sign sign :obs obs :plc plc
                        :p p :null-mean (/ (reduce + nulls) (max 1 (count nulls)))})))
          adj (holm (mapv :p res))]
      (doseq [[{:keys [name source state trigger kinds sign obs plc p null-mean]} h] (map vector res adj)]
        (say (format "pair\t%s\t%s\t%s\t%s\t%s\t%+d\t%d\t%d\t%s\t%.3f\t%s\t%s\t%s\t%s\t%s\t%.3f\t%.3f"
                     name source state trigger (if (= 1 (count kinds)) (first kinds) (str (count kinds) " kinds")) sign
                     (:n obs) (:pos obs) (f3 (:coef obs)) (:se obs) (f3 (:z-ml obs)) (f3 (:est obs)) (f3 (:z obs))
                     (f3 (:z plc)) (f3 null-mean) p h))))
    ;; the principal contradiction, per group
    (say "tau\tgroup\tgauges\tn\tonsets\ttau-hat\ttheta\tLR vs mean (tau 0)\tLR vs argmax (tau inf)\tprofile (tau:ll)")
    (doseq [[gname kinds] (sort-by key groups)
            :let [gp (filterv #(= gname (:group %)) pairs)]
            :when (seq gp)]
      (let [kinds (vec (sort kinds))
            centres (cr/centres-of (select-keys dfr kinds) [design-from design-to])
            ;; each gauge's spread over the design rows
            sds (into {} (for [{:keys [name state trigger kinds]} gp
                               :let [xs (map #(* (:s %) (:tr %)) (pair-rows dfr kinds (sreaders state) (treaders trigger)))
                                     n (count xs) mu (/ (reduce + xs) (max 1 n))
                                     sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) (max 1 n)))]]
                           [name (if (> sd 1e-12) sd 1.0)]))
            rows (gauge-rows fr kinds gp sreaders treaders sds)
            drows (if rotation? rows (gauge-rows dfr kinds gp sreaders treaders sds))
            prof (tau-profile rows kinds centres taus)
            most (apply max (map #(count (keep identity (:gauges %))) rows))]
        (say (format "tau\t%s\t%s\t%d\t%d\t%s\t%s\t%.2f\t%.2f\t%s%s"
                     gname (str/join "," (map :name gp)) (count rows) (count (filter :y rows))
                     (str (:tau prof)) (f3 (:theta prof)) (or (:lr-vs-mean prof) Double/NaN) (or (:lr-vs-max prof) Double/NaN)
                     (str/join " " (for [[t l _] (:profile prof)] (format "%s:%.2f" t l)))
                     (if (< most 2) "\t(at most one gauge per row: tau not identified)" "")))
        ;; out of sample
        (let [parts (if rotation?
                      (for [[a b] cr/eras
                            :let [d (filterv #(not (<= a (:t %) b)) rows) t (filterv #(<= a (:t %) b) rows)]
                            :when (and (seq t) (seq d))]
                        (per-row-scores d t kinds centres))
                      [(per-row-scores drows rows kinds centres)])
              per-row (vec (mapcat :rows parts))
              n (count per-row)
              avg (fn [k] (/ (reduce + (map k per-row)) (max 1 n)))
              ci (fn [a b] (let [[lo hi] (cr/year-bootstrap per-row #(- (a %) (b %)) 1000 seed)]
                             (format "%+.5f [%+.5f, %+.5f]" (- (avg a) (avg b)) lo hi)))]
          (say (format "oos\t%s\t%s\t%d rows\ttaus chosen %s\tlog recal %.5f levels %.5f pairs %.5f mean %.5f argmax %.5f tau %.5f"
                       gname (if rotation? "leave-one-era-out" (str "fit " design-from "-" design-to ", scored " from "-" to))
                       n (str/join "," (map :tau parts)) (avg :recal) (avg :levels) (avg :pairs) (avg :mean) (avg :argmax) (avg :tau)))
          (doseq [[label a b] [["pairs - levels" :pairs :levels] ["levels - recal" :levels :recal] ["mean - levels" :mean :levels]
                               ["argmax - mean" :argmax :mean] ["tau - mean" :tau :mean] ["tau - argmax" :tau :argmax]]]
            (say (format "oos\t%s\t%s\t%s" gname label (ci a b)))))))
    (io/make-parents out)
    (spit out (str buf))
    (println "wrote" out)))

(defn -main [& args]
  (let [{:keys [run from to design-from design-to export] :as opts} (parse-opts args)
        _ (when (> to 2002) (println "note: outcomes to" to "are read (the registered test)"))
        cats (kinds/catalogues)
        raw-states (state-panels)
        raw-triggers (trigger-panels cats)]
    (if export
      (do (when (> to 2002) (throw (ex-info "T7 export reads the design years only" {:to to})))
          (export-rows! export (frames run cats from to) groups
                        (into {} (for [s state-names] [s (reader (state-transform (raw-states s) design-from design-to))]))
                        (into {} (for [t trigger-names] [t (reader (trigger-transform (raw-triggers t) design-from design-to))])))
          (println "wrote" export))
      (run-t3 opts cats raw-states raw-triggers))))
