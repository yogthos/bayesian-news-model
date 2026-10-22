(ns bnm.calibration-regime
  "Trial T2 (bead bnm-3qu.3, docs/LIT-NEXT-STEPS.md ranked trial 2): is the
  base's calibration different when a named stock is stretched? At the
  year node (t44-world-norel), for each kind group, a logit of the node's
  own outcome (an onset in (t, t + 2], seen from the end of year t) on the
  base's log odds L:

    logit(y) = a_k + b_k (L - m_k) + c R + d R (L - m_k)

  a_k and b_k the plain recalibration per kind (the base is far from
  calibrated: slopes well under 1), m_k the kind's mean L on the design
  rows, R the regime: the named state, signed and demeaned by year, above
  its 80th percentile over the design years' country-years. c is the
  level shift in the regime at a typical forecast, d the change of slope:
  the base's signal amplified (d > 0) or muted in the regime. c and d are
  shared by the kinds of a group; each kind reads its own state:

    financial: banking crises, sovereign defaults, growth collapses,
               fiscal consolidations (the credit gap, +); currency crises
               (reserves, -: the regime is low reserves)
    social:    protest and violence surges, conflict and war onsets (the
               youth bulge, +); coups (anocracy, +)

  The test: the year-clustered score test of (c, d) = 0 (the score at the
  recalibration-only fit, each row's score residualized on the nuisance,
  its variance summed within origin years): a chi-squared on 2 df; the
  one-step estimates I^-1 U and their sandwich z. Its null: country labels
  of each state permuted (each country reads another's series whole), the
  regime recomputed (demeaned, thresholded on the design years), the
  statistics refitted; one-sided p for d and c, the joint stat's p. The
  placebo: the state read at the next country with it that year. Terciles
  (top and bottom against the middle) for the record.

  Interactions, for the record, state x trigger as named in the
  literature, the regime R their conjunction (each in its top tercile):
    credit gap x house-price boom (3-year real growth, BIS or JST,
      demeaned by year), banking crises, rows where house prices are known
      (Greenwood et al. 2022; Richter et al. 2021): beyond the base, which
      already reads JST house prices through its macrohistory features;
    food import share x the world grain price gap, protest surges
      (Hendrix & Haggard 2015; Bellemare 2015).
  'zone': the conjunction alone; 'crossed': with the two parts' levels as
  nuisance, so c and d are the interaction proper.

  Out of sample: the regime recalibration (a_k, b_k, c, d) against the
  plain one (a_k, b_k) and the raw base, on mean log score, Brier and
  recall in the top 10% per kind and year. When the test years are the
  design years, leave one era out (1960-69, 1970-79, 1980-89, 1990-);
  otherwise fitted on the design years and scored on the test years.

  No outcome after --to is read; thresholds, centres and out-of-sample
  fits come from the design years (--design-from, --design-to) only.

    jolt -A:tools -M -m bnm.calibration-regime [--run t44-world-norel]
      [--from 1960] [--to 2002] [--design-from 1960] [--design-to 2002]
      [--draws 200] [--seed 1] [--out data/runs/sequencing/calibration-t2-design.tsv]"
  (:require [augur.logit :as lg]
            [augur.math :as m]
            [bnm.horizon-profile :as hp]
            [bnm.kinds :as kinds]
            [bnm.lattice-banking :as lb]
            [bnm.macrohistory :as mh]
            [bnm.relation-diag :as rd]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- small linear algebra ---------------------------------------------------------

(defn- outer-add!
  "a += s u v' (row major, n x n... or k x q with q = (count v))."
  [^doubles a s u v]
  (let [q (count v)]
    (dotimes [i (count u)]
      (let [si (* s (double (nth u i)))]
        (when-not (zero? si)
          (dotimes [j q] (aset a (+ (* i q) j) (+ (aget a (+ (* i q) j)) (* si (double (nth v j)))))))))))

(defn- inverse
  "The inverse of a symmetric positive definite n x n matrix (row major)."
  ^doubles [^doubles a n]
  (let [out (double-array (* n n))]
    (dotimes [j n]
      (let [e (double-array n) _ (aset e j 1.0)
            x (m/solve-spd a e n)]
        (dotimes [i n] (aset out (+ (* i n) j) (aget ^doubles x i)))))
    out))

(defn- mat* [^doubles a ^doubles b n k p]
  ;; (n x k)(k x p)
  (let [out (double-array (* n p))]
    (dotimes [i n] (dotimes [j p] (aset out (+ (* i p) j) (loop [l 0 s 0.0] (if (< l k) (recur (inc l) (+ s (* (aget a (+ (* i k) l)) (aget b (+ (* l p) j))))) s)))))
    out))

(defn- mat-vec [^doubles a v n]
  (vec (for [i (range n)] (reduce + (map-indexed (fn [j x] (* (aget a (+ (* i n) j)) x)) v)))))

;; --- the design and the tests -----------------------------------------------------

(defn design
  "Rows {:kind :L :r [regimes] (:n [extra nuisance])} with :x the nuisance
  (an intercept and a slope on L - centre per kind: 1, the other kinds'
  dummies, L - m, (L - m) x dummies, then :n) and :z the tested terms (each
  regime, then each regime x (L - m))."
  [rows kinds centres]
  (let [others (vec (rest kinds))]
    (mapv (fn [{:keys [kind L r n] :as row}]
            (let [l (- (double L) (double (centres kind)))
                  ds (mapv #(if (= kind %) 1.0 0.0) others)]
              (assoc row
                     :x (vec (concat [1.0] ds [l] (map #(* l %) ds) (map double n)))
                     :z (vec (concat (map double r) (map #(* l (double %)) r)))
                     :w 1.0 :o 0.0)))
          rows)))

(defn- yv ^double [r] (if (:y r) 1.0 0.0))

(defn score-test
  "The year-clustered score test of the :z terms = 0 at the fit of the :x
  terms alone. {:stat (chi-squared, :df = |z|) :est (one-step I^-1 U) :se
  (sandwich) :zs :n :pos}; nil when a tested term has no variation."
  [rows]
  (let [rows (vec rows)
        q (count (:x (first rows))) k (count (:z (first rows)))
        b (lg/fit (map #(select-keys % [:x :y :w :o]) rows) q {:prior 1e-4})
        ps (mapv #(lg/predict b (:x %) 0.0) rows)
        axx (double-array (* q q)) azx (double-array (* k q))
        _ (doseq [[r p] (map vector rows ps)
                  :let [w (* p (- 1.0 p))]]
            (outer-add! axx w (:x r) (:x r))
            (outer-add! azx w (:z r) (:x r)))
        _ (dotimes [i q] (aset axx (+ (* i q) i) (+ (aget axx (+ (* i q) i)) 1e-9)))
        ;; gamma = azx axx^-1 (k x q)
        gam (mat* azx (inverse axx q) k q q)
        zt (fn [r] (let [x (:x r) z (:z r)]
                     (vec (for [i (range k)] (- (double (z i)) (loop [j 0 s 0.0] (if (< j q) (recur (inc j) (+ s (* (aget ^doubles gam (+ (* i q) j)) (double (x j))))) s)))))))
        info (double-array (* k k))
        us (mapv (fn [r p] (let [zz (zt r)] (outer-add! info (* p (- 1.0 p)) zz zz) [(:g r) (mapv #(* (- (yv r) p) %) zz)])) rows ps)
        u (reduce #(mapv + %1 %2) (repeat k 0.0) (map second us))
        v (double-array (* k k))
        _ (doseq [[_ ts] (group-by first us) :let [s (reduce #(mapv + %1 %2) (repeat k 0.0) (map second ts))]] (outer-add! v 1.0 s s))]
    (when (every? #(> (aget info (+ (* % k) %)) 1e-8) (range k))
      (let [iinv (inverse info k)
            est (mat-vec iinv u k)
            cov (mat* (mat* iinv v k k k) iinv k k k)
            se (vec (for [i (range k)] (Math/sqrt (max 1e-300 (aget cov (+ (* i k) i))))))
            vinv (inverse v k)]
        {:stat (reduce + (map * u (mat-vec vinv u k))) :df k :u u :est est :se se :zs (mapv / est se)
         :n (count rows) :pos (count (filter :y rows))}))))

(defn full-fit
  "The maximum-likelihood fit of nuisance and tested terms together:
  :coef the tested terms', with year-clustered sandwich :se and :z."
  [rows]
  (let [rows (mapv #(assoc % :xx (into (:x %) (:z %))) rows)
        q (count (:x (first rows))) k (count (:z (first rows))) n (+ q k)
        b (lg/fit (map #(hash-map :x (:xx %) :y (:y %) :w 1.0 :o 0.0) rows) n {:prior 1e-4})
        a (double-array (* n n)) bb (double-array (* n n))
        _ (doseq [r rows :let [p (lg/predict b (:xx r) 0.0)]] (outer-add! a (* p (- 1.0 p)) (:xx r) (:xx r)))
        _ (doseq [[_ rs] (group-by :g rows)
                  :let [s (reduce #(mapv + %1 %2) (repeat n 0.0) (for [r rs :let [p (lg/predict b (:xx r) 0.0)]] (mapv #(* (- (yv r) p) %) (:xx r))))]]
            (outer-add! bb 1.0 s s))
        _ (dotimes [i n] (aset a (+ (* i n) i) (+ (aget a (+ (* i n) i)) 1e-9)))
        ai (inverse a n)
        cov (mat* (mat* ai bb n n n) ai n n n)
        coef (vec (for [i (range q n)] (aget ^doubles b i)))
        se (vec (for [i (range q n)] (Math/sqrt (max 1e-300 (aget cov (+ (* i n) i))))))]
    {:coef coef :se se :z (mapv / coef se) :beta (vec b)}))

;; --- regimes ---------------------------------------------------------------------

(defn demean
  "`panel` ({iso {year value}}) less each year's mean over the countries
  with it that year (no scaling); a year with one country is dropped.
  Causal: the mean at t uses only values at t."
  [panel]
  (let [by-year (reduce (fn [acc [c byy]] (reduce (fn [acc [y x]] (update acc y (fnil conj []) [c x])) acc byy)) {} panel)]
    (reduce (fn [acc [y cxs]]
              (if (< (count cxs) 2)
                acc
                (let [mu (/ (reduce + (map second cxs)) (count cxs))]
                  (reduce (fn [acc [c x]] (assoc-in acc [c y] (- x mu))) acc cxs))))
            {} by-year)))

(defn threshold
  "The q-quantile of the panel's values over years from-to (the design
  years): nothing later moves it."
  [panel from to q]
  (let [xs (double-array (sort (for [[_ byy] panel [y x] byy :when (<= from y to)] (double x))))]
    (m/quantile xs q)))

(defn above
  "A reader (fn [c t]) of 1.0 where the panel's value at t is above `thr`,
  0.0 where not, nil where unknown."
  [panel thr]
  (fn [c t] (when-let [x (get-in panel [c t])] (if (> x thr) 1.0 0.0))))

(defn- signed [panel s] (if (= s 1) panel (update-vals panel (fn [byy] (update-vals byy #(* s %))))))

(defn placebo-panel
  "The panel read at the next country with it that year (hp/placebo-read)."
  [panel]
  (let [rd (hp/placebo-read panel)]
    (into {} (for [[c byy] panel
                   :let [z (into (sorted-map) (for [[t _] byy :let [x (rd c t)] :when (some? x)] [t x]))]
                   :when (seq z)]
               [c z]))))

(defn permute-panel
  "Country labels shuffled: each country takes another's whole series
  (its years with it)."
  [panel g]
  (let [cs (vec (sort (keys panel)))
        a (long-array (range (count cs)))]
    (doseq [i (reverse (range 1 (count cs)))]
      (let [j (long (Math/floor (* (m/next-double! g) (inc i)))) t (aget a i)]
        (aset a i (aget a j)) (aset a j t)))
    (into {} (map-indexed (fn [i c] [c (panel (cs (aget a i)))]) cs))))

(defn regime-readers
  "{state reader} for the panels: each signed, demeaned by year, compared
  with its design-year quantiles. `mode`: a number q (one regime: above
  q), :terciles ([top, bottom])."
  [panels kinds [dfrom dto] mode]
  (into {} (for [[st s] (distinct (map (juxt :state :sign) (vals kinds)))
                 :let [p (demean (signed (panels st) s))]]
             [st (if (= mode :terciles)
                   (let [hi (above p (threshold p dfrom dto (/ 2.0 3.0)))
                         lo (above p (threshold p dfrom dto (/ 1.0 3.0)))]
                     (fn [c t] (when-let [h (hi c t)] [h (- 1.0 (lo c t))])))
                   (let [r (above p (threshold p dfrom dto mode))]
                     (fn [c t] (when-let [x (r c t)] [x]))))])))

(defn centres-of
  "Each kind's mean base log odds over its frame rows with origins in the
  design years (outcome by dto)."
  [frames [dfrom dto]]
  (into {} (for [[k rs] frames :let [ls (for [r rs :when (and (>= (:t r) dfrom) (<= (+ 2 (:t r)) dto))] (:L r))]]
             [k (if (seq ls) (/ (reduce + ls) (count ls)) 0.0)])))

(defn- assemble
  "Design rows of `frames` where `regime` ({kind (fn [c t] -> {:r :n} or
  [r...])}) is known."
  [frames kinds regime centres]
  (let [ks (vec (sort (keys frames)))]
    (design (vec (for [k ks r (frames k) :let [v ((regime k) (:c r) (:t r))] :when v]
                   (if (map? v) (merge r v) (assoc r :r v)))) ks centres)))

(defn observe
  "Score test and full fit of the regime terms on `frames`."
  [{:keys [frames kinds design q centres regime-of]} panels]
  (let [centres (or centres (centres-of frames design))
        regime (or (some-> regime-of (apply [panels]))
                   (let [rr (regime-readers panels kinds design q)] (update-vals kinds #(rr (:state %)))))
        rows (assemble frames kinds regime centres)]
    (when (and (seq rows) (pos? (count (filter :y rows))))
      (when-let [s (score-test rows)]
        (assoc s :fit (full-fit rows) :rows rows)))))

(defn permutation-test
  "`opts`: :panels {state panel} :kinds {kind {:state :sign}} :frames {kind
  [{:kind :c :t :g :L :y}]} :design [from to] :q (a quantile or
  :terciles) :draws :seed, optional :centres and :regime-of (fn [panels]
  -> {kind reader}) and :permute (the states to permute; all by default).
  The observed statistics, and their null from permuted country labels."
  [{:keys [panels draws seed permute] :as opts}]
  (let [obs (observe opts panels)
        m (quot (count (:est obs)) 2)
        g (m/rng seed)
        stat-of (fn [o] (when o
                          {:stat (:stat o) :zd ((:zs o) m) :zc ((:zs o) 0) :d ((:est o) m)
                           :dd (when (> m 1) (/ (- ((:est o) m) ((:est o) (inc m)))
                                                (Math/sqrt (+ (Math/pow ((:se o) m) 2) (Math/pow ((:se o) (inc m)) 2)))))}))
        nulls (vec (keep (fn [_] (stat-of (observe (dissoc opts :rows)
                                                   (reduce (fn [ps st] (update ps st permute-panel g))
                                                           panels (or permute (keys panels))))))
                         (range draws)))
        so (stat-of obs)
        p (fn [k] (/ (+ 1.0 (count (filter #(>= (k %) (k so)) nulls))) (+ 1.0 (count nulls))))
        mean (fn [xs] (/ (reduce + xs) (max 1 (count xs))))]
    (merge (dissoc obs :rows)
           {:obs so :draws (count nulls)
            :c ((:coef (:fit obs)) 0) :d ((:coef (:fit obs)) m)
            :p-stat (p :stat) :p-d (p :zd) :p-c (p :zc) :p-dd (when (:dd so) (p :dd))
            :null-mean-d (mean (map :d nulls)) :null-mean-stat (mean (map :stat nulls))})))

;; --- out of sample ------------------------------------------------------------------

(defn- mean-log [rows ps] (/ (reduce + (map (fn [r p] (Math/log (if (:y r) p (- 1.0 p)))) rows ps)) (max 1 (count rows))))
(defn- brier [rows ps] (/ (reduce + (map (fn [r p] (Math/pow (- (yv r) p) 2)) rows ps)) (max 1 (count rows))))

(defn top-recall
  "The share of onsets in the top 10% of rows by `ps` within each kind and
  origin year."
  [rows ps]
  (let [groups (group-by (fn [[r _]] [(:kind r) (:t r)]) (map vector rows ps))
        hit (reduce + (for [[_ rps] groups
                            :let [n (long (Math/ceil (* 0.1 (count rps))))]]
                        (count (filter (comp :y first) (take n (sort-by (comp - second) rps))))))
        tot (count (filter :y rows))]
    (if (pos? tot) (/ (double hit) tot) Double/NaN)))

(defn out-of-sample
  "The plain recalibration (a_k, b_k) and the regime one (+ c, d) fitted
  on `design` rows ({:kind :L :r :y :t}), scored on `test`, against the
  raw base. Centres from the design rows."
  [drows trows kinds]
  (let [kinds (vec kinds)
        centres (into {} (for [k kinds :let [ls (map :L (filter #(= k (:kind %)) drows))]] [k (if (seq ls) (/ (reduce + ls) (count ls)) 0.0)]))
        dz (design drows kinds centres) tz (design trows kinds centres)
        q (count (:x (first dz))) k (count (:z (first dz)))
        recal (lg/fit (map #(select-keys % [:x :y :w :o]) dz) q {:prior 1e-4})
        regime (lg/fit (map #(hash-map :x (into (:x %) (:z %)) :y (:y %) :w 1.0 :o 0.0) dz) (+ q k) {:prior 1e-4})
        p-raw (mapv #(m/expit (:L %)) tz)
        p-rec (mapv #(lg/predict recal (:x %) 0.0) tz)
        p-reg (mapv #(lg/predict regime (into (:x %) (:z %)) 0.0) tz)]
    {:centres centres :recal (vec recal) :regime (vec regime) :n (count tz) :pos (count (filter :y tz))
     :log-raw (mean-log tz p-raw) :log-recal (mean-log tz p-rec) :log-regime (mean-log tz p-reg)
     :log-gain (- (mean-log tz p-reg) (mean-log tz p-rec))
     :brier-raw (brier tz p-raw) :brier-recal (brier tz p-rec) :brier-regime (brier tz p-reg)
     :recall-raw (top-recall tz p-raw) :recall-recal (top-recall tz p-rec) :recall-regime (top-recall tz p-reg)
     :per-row (mapv (fn [r a b c] {:t (:t r) :raw (Math/log (if (:y r) a (- 1.0 a))) :rec (Math/log (if (:y r) b (- 1.0 b))) :reg (Math/log (if (:y r) c (- 1.0 c)))}) tz p-raw p-rec p-reg)}))

(defn year-bootstrap
  "90% interval of the mean per-row difference (f row), origin years
  resampled."
  [per-row f draws seed]
  (let [by-year (vec (vals (group-by :t per-row)))
        sums (mapv (fn [rs] [(reduce + (map f rs)) (count rs)]) by-year)
        g (m/rng seed) ny (count sums)
        xs (double-array (sort (for [_ (range draws)]
                                 (let [pick (repeatedly ny #(sums (long (Math/floor (* ny (m/next-double! g))))))]
                                   (/ (reduce + (map first pick)) (max 1 (reduce + (map second pick))))))))]
    [(m/quantile xs 0.05) (m/quantile xs 0.95)]))

(declare rows)

;; --- data ---------------------------------------------------------------------------

(def groups
  "One pre-registered state per kind (docs/LIT-NEXT-STEPS.md), c and d
  shared within a group."
  [{:name "financial"
    :kinds {"banking_crisis" {:state "credit-gap" :sign 1} "sovereign_default" {:state "credit-gap" :sign 1}
            "growth_collapse" {:state "credit-gap" :sign 1} "fiscal_consolidation" {:state "credit-gap" :sign 1}
            "currency_crisis" {:state "reserves" :sign -1}}}
   {:name "social"
    :kinds {"protest_surge" {:state "youth-bulge" :sign 1} "violence_surge" {:state "youth-bulge" :sign 1}
            "conflict_onset" {:state "youth-bulge" :sign 1} "war_onset" {:state "youth-bulge" :sign 1}
            "coup" {:state "anocracy" :sign 1}}}])

(defn frames-of
  "{kind [{:kind :c :t :g :L :y}]}: the year node's year-end forecasts
  (log odds of an onset in 24 months) and outcomes, origins from `from`,
  outcome windows ending by `to`."
  [run cats kinds from to]
  (into {} (for [k kinds
                 :let [yr (rd/read-year run k)]]
             [k (rows {:cells (for [[c byo] yr [o _] byo :when (= 11 (mod o 12))] [c (quot o 12)])
                       :kind k :from from :to to
                       :base (fn [c t] (when-let [[p _] (get-in yr [c (+ (* 12 t) 11)])] (m/logit (max 1e-6 (min (- 1.0 1e-6) p)))))
                       :outcome (fn [c t] (let [o (+ (* 12 t) 11)] (#'lb/onset-in? (cats k) c o o (+ o 24))))
                       :read (constantly [])})])))

(defn rows
  "Rows {:kind :c :t :g :L :y} for cells [c t] with origins from `from`
  and the window (t, t + 2] ending by `to`, where the base, the outcome
  and `read` are known. :g, the cluster, is the origin year."
  [{:keys [cells kind from to base outcome read]}]
  (vec (for [[c t] cells
             :when (and (>= t from) (<= (+ t 2) to))
             :let [L (base c t)] :when (some? L)
             :let [y (outcome c t)] :when (some? y)
             :let [r (read c t)] :when (some? r)]
         {:kind kind :c c :t t :g t :L (double L) :y y :r r})))

(defn- slow-sub
  "{iso [[period-month avail-year value]]} of a slow-store series."
  [v]
  (with-open [r (io/reader "data/slow/series.tsv")]
    (reduce (fn [acc line]
              (let [[unit var _ pend avail value] (str/split line #"\t" -1)]
                (if-let [x (and (= var v) (parse-double value))]
                  (update acc unit (fnil conj []) [(+ (* 12 (parse-long (subs pend 0 4))) (dec (parse-long (subs pend 5 7))))
                                                   (parse-long (subs avail 0 4)) x])
                  acc)))
            {} (line-seq r))))

(defn house-growth
  "{iso {year g}}: 3-year log growth of real house prices as known at the
  end of each year: BIS (finance/real_house_prices, the latest quarter
  published by then, within a year of it, against the same quarter three
  years before); else JST (hpnom / cpi, data year d known at d + 1, at
  most a year old)."
  []
  (let [bis (slow-sub "finance/real_house_prices")
        jst (group-by :iso (mh/read-jst "data/raw/slow/jst/jst.csv"))
        from-bis (into {} (for [[iso obs] bis
                                :let [z (into (sorted-map)
                                              (for [y hp/years
                                                    :let [known (filter (fn [[_ a _]] (<= a y)) obs)
                                                          by-p (into {} (map (fn [[p _ x]] [p x]) known))
                                                          p (when (seq by-p) (apply max (keys by-p)))]
                                                    :when (and p (>= p (- (+ (* 12 y) 11) 12)))
                                                    :let [x0 (by-p (- p 36))]
                                                    :when (and x0 (pos? x0) (pos? (by-p p)))]
                                                [y (Math/log (/ (by-p p) x0))]))]
                                :when (seq z)]
                            [iso z]))
        from-jst (into {} (for [[iso rs] jst
                                :let [h (into {} (for [r rs :let [hp (:hpnom r) cpi (:cpi r)] :when (and hp cpi (pos? hp) (pos? cpi))] [(:year r) (/ hp cpi)]))
                                      z (into (sorted-map) (for [y hp/years :let [d (dec y)] :when (and (h d) (h (- d 3)))] [y (Math/log (/ (h d) (h (- d 3))))]))]
                                :when (seq z)]
                            [iso z]))]
    (merge-with (fn [j b] (merge j b)) from-jst from-bis)))

(defn food-parts
  "[food import share panel {iso {year share}}, world grain gap {year gap}]
  as horizon-profile reads them."
  []
  (let [slow (hp/read-slow-annual "data/slow/series.tsv" ["food/imports_share"])
        stocks (#'hp/table->recs ["data/derived/stocks/cycles.csv"])
        world (fn [v] (hp/as-known (get-in stocks [v "WLD"]) hp/years 1))
        ws (map world ["cycles/wheat-price-gap" "cycles/maize-price-gap" "cycles/rice-price-gap"])]
    [(#'hp/known-panel (get slow "food/imports_share") 3)
     (into (sorted-map) (for [y hp/years :let [xs (keep #(get % y) ws)] :when (= 3 (count xs))] [y (/ (reduce + xs) 3.0)]))]))

;; --- the run ------------------------------------------------------------------------

(defn parse-opts [args]
  (let [o (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        n (fn [k d] (or (some-> (o k) parse-long) d))]
    {:run (or (:run o) "t44-world-norel")
     :from (n :from 1960) :to (n :to 2002)
     :design-from (n :design-from 1960) :design-to (n :design-to 2002)
     :draws (n :draws 200) :seed (n :seed 1)
     :out (or (:out o) "data/runs/sequencing/calibration-t2-design.tsv")}))

(def eras [[1960 1969] [1970 1979] [1980 1989] [1990 2000]])

(defn- conj-regime
  "A reader of the conjunction: the state in its top tercile (signed,
  demeaned, design-year threshold) and the trigger (fn [c t] -> 0/1/nil).
  {:r [state x trigger] :n [state trigger]} when crossed, else :r alone."
  [panel design trigger crossed?]
  (let [p (demean panel)
        hi (above p (threshold p (first design) (second design) (/ 2.0 3.0)))]
    (fn [c t] (let [s (hi c t) tr (trigger c t)]
                (when (and s tr) (if crossed? {:r [(* s tr)] :n [s tr]} {:r [(* s tr)] :n []}))))))

(defn -main [& args]
  (let [{:keys [run from to design-from design-to draws seed out]} (parse-opts args)
        design [design-from design-to]
        _ (when (> to 2002) (println "note: outcomes to" to "are read (the registered test)"))
        cats (kinds/catalogues)
        panels (hp/state-panels)
        all-kinds (distinct (mapcat (comp keys :kinds) groups))
        frames (frames-of run cats all-kinds from to)
        dframes (if (and (= from design-from) (= to design-to)) frames (frames-of run cats all-kinds design-from design-to))
        buf (StringBuilder.)
        say (fn [s] (println s) (flush) (.append buf (str s "\n")))
        f3 (fn [x] (if (and x (not (Double/isNaN x))) (format "%+.3f" (double x)) "NA"))]
    (say (str "# T2: calibration of " run "'s year node by stock regime; test rows: origins " from "-" (- to 2) " (windows to " to
              "); thresholds, centres and out-of-sample fits: design years " design-from "-" design-to "; " draws " permutations, seed " seed))
    (say "# model: logit(y) = a_k + b_k (L - m_k) + sum c R + d R (L - m_k); y an onset in (t, t+2]; L the base's log odds; clusters origin years")
    (say "table\tgroup\tvariant\tterm\tn\tonsets\tcoef\tse\tz\tone-step\tscore-z\tstat\tdf\tplacebo-coef\tplacebo-z\tplacebo-stat\tnull-mean\tp")
    (doseq [{gname :name gkinds :kinds} groups
            [variant q] [["p80" 0.8] ["terciles" :terciles]]]
      (let [fr (select-keys frames (keys gkinds))
            cents (centres-of (select-keys dframes (keys gkinds)) design)
            sts (distinct (map :state (vals gkinds)))
            ps (select-keys panels sts)
            opts {:panels ps :kinds gkinds :frames fr :design design :q q :draws draws :seed seed :centres cents}
            res (permutation-test opts)
            plc (observe opts (update-vals ps placebo-panel))
            terms (if (= q :terciles) ["c-top" "c-bottom" "d-top" "d-bottom"] ["c" "d"])
            m (quot (count terms) 2)]
        (doseq [[i term] (map-indexed vector terms)]
          (say (format "regime\t%s\t%s\t%s\t%d\t%d\t%s\t%.3f\t%s\t%s\t%s\t%.2f\t%d\t%s\t%s\t%.2f\t%s\t%s"
                       gname variant term (:n res) (:pos res) (f3 ((:coef (:fit res)) i)) ((:se (:fit res)) i) (f3 ((:z (:fit res)) i))
                       (f3 ((:est res) i)) (f3 ((:zs res) i)) (:stat res) (:df res)
                       (f3 (some-> plc :fit :coef (nth i))) (f3 (some-> plc :zs (nth i))) (double (or (:stat plc) Double/NaN))
                       (if (= i m) (f3 (:null-mean-d res)) (if (zero? i) (format "stat %.2f" (:null-mean-stat res)) ""))
                       (cond (= i 0) (format "c>0 %.3f; joint %.3f" (:p-c res) (:p-stat res))
                             (= i m) (format "d>0 %.3f" (:p-d res))
                             (and (= i (inc m)) (:p-dd res)) (format "d-top>d-bottom %.3f (z %s)" (:p-dd res) (f3 (:dd (:obs res))))
                             :else ""))))
        ;; per kind, for the record (no permutation)
        (when (number? q)
          (doseq [k (sort (keys gkinds))
                  :let [o (observe (assoc opts :frames (select-keys fr [k]) :kinds (select-keys gkinds [k])) ps)]
                  :when o]
            (say (format "per-kind\t%s\t%s\t%s\t%d\t%d\tc %s (z %s)\td %s (z %s)\tstat %.2f"
                         gname variant k (:n o) (:pos o) (f3 ((:coef (:fit o)) 0)) (f3 ((:z (:fit o)) 0))
                         (f3 ((:coef (:fit o)) 1)) (f3 ((:z (:fit o)) 1)) (:stat o)))))))
    ;; interactions
    (let [[food grain] (food-parts)
          house (house-growth)
          gthr (m/quantile (double-array (sort (for [[y x] grain :when (<= design-from y design-to)] x))) (/ 2.0 3.0))
          grain-hi (fn [_ t] (when-let [x (grain t)] (if (> x gthr) 1.0 0.0)))
          hd (demean house)
          house-hi (above hd (threshold hd design-from design-to (/ 2.0 3.0)))
          specs [{:name "credit-x-house" :kind "banking_crisis" :state "credit-gap" :panel (panels "credit-gap") :trigger house-hi}
                 {:name "food-x-grain" :kind "protest_surge" :state "food-share" :panel food :trigger grain-hi}]]
      (doseq [{:keys [name kind state panel trigger]} specs
              crossed? [false true]
              :let [variant (if crossed? "crossed" "zone")
                    kinds {kind {:state state :sign 1}}
                    regime-of (fn [ps] {kind (conj-regime (ps state) design trigger crossed?)})
                    opts {:panels {state panel} :kinds kinds :frames (select-keys frames [kind]) :design design :q 0.8
                          :draws draws :seed seed :centres (centres-of (select-keys dframes [kind]) design) :regime-of regime-of}
                    res (permutation-test opts)
                    plc (observe opts {state (placebo-panel panel)})]]
        (doseq [[i term] [[0 "c"] [1 "d"]]]
          (say (format "interaction\t%s\t%s\t%s\t%d\t%d\t%s\t%.3f\t%s\t%s\t%s\t%.2f\t%d\t%s\t%s\t%.2f\t%s\t%s"
                       name variant term (:n res) (:pos res) (f3 ((:coef (:fit res)) i)) ((:se (:fit res)) i) (f3 ((:z (:fit res)) i))
                       (f3 ((:est res) i)) (f3 ((:zs res) i)) (:stat res) (:df res)
                       (f3 (some-> plc :fit :coef (nth i))) (f3 (some-> plc :zs (nth i))) (double (or (:stat plc) Double/NaN))
                       (if (= i 1) (f3 (:null-mean-d res)) (format "stat %.2f" (:null-mean-stat res)))
                       (if (= i 0) (format "c>0 %.3f; joint %.3f" (:p-c res) (:p-stat res)) (format "d>0 %.3f" (:p-d res))))))
        (say (format "# %s %s: %d rows in the regime" name variant (count (filter #(pos? (first (:z %))) (:rows (observe opts {state panel}))))))))
    ;; out of sample: the p80 regime
    (say "oos\tgroup\tscheme\tn\tonsets\tlog-raw\tlog-recal\tlog-regime\tlog-gain (regime - recal) [90%]\trecal - raw [90%]\tbrier-raw\tbrier-recal\tbrier-regime\ttop10-raw\ttop10-recal\ttop10-regime")
    (doseq [{gname :name gkinds :kinds} groups]
      (let [ks (vec (sort (keys gkinds)))
            ps (select-keys panels (distinct (map :state (vals gkinds))))
            rr (regime-readers ps gkinds design 0.8)
            with-r (fn [frs] (vec (for [k ks r (frs k) :let [v ((rr (:state (gkinds k))) (:c r) (:t r))] :when v] (assoc r :r v))))
            test-rows (with-r frames)
            design-rows (with-r dframes)
            rotation? (and (= from design-from) (= to design-to))
            parts (if rotation?
                    (for [[a b] eras] (out-of-sample (filterv #(not (<= a (:t %) b)) test-rows) (filterv #(<= a (:t %) b) test-rows) ks))
                    [(out-of-sample design-rows test-rows ks)])
            per-row (vec (mapcat :per-row parts))
            n (count per-row)
            avg (fn [k] (/ (reduce + (map k per-row)) n))
            wavg (fn [k] (/ (reduce + (map #(* (:n %) (k %)) parts)) (reduce + (map :n parts))))
            wrec (fn [k] (/ (reduce + (map #(* (:pos %) (k %)) parts)) (max 1 (reduce + (map :pos parts)))))
            [g1 g2] (year-bootstrap per-row #(- (:reg %) (:rec %)) 1000 seed)
            [r1 r2] (year-bootstrap per-row #(- (:rec %) (:raw %)) 1000 seed)]
        (say (format "oos\t%s\t%s\t%d\t%d\t%.5f\t%.5f\t%.5f\t%+.5f [%+.5f, %+.5f]\t%+.5f [%+.5f, %+.5f]\t%.5f\t%.5f\t%.5f\t%.3f\t%.3f\t%.3f"
                     gname (if rotation? "leave-one-era-out" (str "fit " design-from "-" design-to ", scored " from "-" to))
                     n (reduce + (map :pos parts)) (avg :raw) (avg :rec) (avg :reg) (- (avg :reg) (avg :rec)) g1 g2
                     (- (avg :rec) (avg :raw)) r1 r2
                     (wavg :brier-raw) (wavg :brier-recal) (wavg :brier-regime)
                     (wrec :recall-raw) (wrec :recall-recal) (wrec :recall-regime)))))
    (io/make-parents out)
    (spit out (str buf))
    (println "wrote" out)))
