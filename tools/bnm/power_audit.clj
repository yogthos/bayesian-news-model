(ns bnm.power-audit
  "Trial 0 of bnm-3qu: how large an effect could trial 45's design find?
  Synthetic states with a known odds ratio per SD are injected into the
  real rows of a base run (t44-world-norel): each kind's year-end forecasts
  and countries, design years 1960-2002 and test years 2003-2022, as
  bnm.state-diag reads them. Outcomes are redrawn as

    y* ~ Bernoulli(sigmoid(logit p + u_year + beta x))

  with u_year a year shock whose sd is estimated per kind from the design
  years' overdispersion (year-shock-sd). Three kinds of state x,
  standardized on the design rows: :own (AR(1) at 0.9 within each country,
  independent across them), :level (a persistent country mean plus a
  little noise, like arable land), :world (one AR(1) series over years, the
  same at every country).

  1. The diagnostic: the share of draws in which x passes
     bnm.relation-diag/passes? (the clustered score test against its
     placebo, another country's x or for a world series x ten years
     before), at k = 4530/13 (trial 45's screen per kind), 10 and 1.
  2. Out of sample, 2003-2022: an oracle (knows beta) and a realistic
     model (beta fitted on the draw's design years) against the base, the
     paired bootstrap of bnm.world-score (countries and years resampled,
     one-sided 5%), per kind and pooled with kinds weighed equally; the
     minimum detectable Brier gain at 80% power (the equivalence bound).

    jolt -A:tools -M -m bnm.power-audit [--run t44-world-norel] [--sims 200]
      [--oos-sims 100] [--reps 400] [--kinds a,b] [--types own,level,world]
      [--out data/runs/sequencing/power-audit.tsv]"
  (:require [augur.math :as m]
            [bnm.factors :as fac]
            [bnm.kinds :as kinds]
            [bnm.paired :as pd]
            [bnm.relation-diag :as rd]
            [bnm.warm-up :as wu]
            [clojure.string :as str]))

(def odds-ratios [1.0 1.1 1.2 1.35 1.5 1.8 2.5])

(def list-sizes
  "Candidates per kind: trial 45's screen, a theory-led list, one
  pre-registered hypothesis."
  [(/ 4530 13.0) 10 1])

(def state-types [:own :level :world])

(defn- logit [p] (Math/log (/ (max 1e-6 p) (max 1e-6 (- 1.0 p)))))

(defn year-rows
  "[{:c :t :p :o :y}] of a kind's year node (`years` {country (sorted-map
  origin [p y])}, bnm.relation-diag/read-year): year-end origins of the
  countries `cs`, resolved, their years from `from` to `to`; :o the
  forecast's log odds, as bnm.state-diag/rows reads it. With the
  catalogue's `lag`, the base's warm-up rows (bnm.warm-up: p near 0.5
  before it has read an outcome) are left out: outcomes drawn from them
  would be coin flips."
  ([years cs from to] (year-rows years cs from to nil))
  ([years cs from to lag]
   (let [years (if lag (wu/drop-node years lag) years)]
     (vec (for [c cs [o [p y]] (get years c)
                :when (and (some? y) (= 11 (mod o 12)) (<= from (quot o 12) to))]
            {:c c :t (quot o 12) :p (double p) :o (logit p) :y y})))))

;; a standard normal on 21 points, for expectations over a year shock
(def ^:private nodes (mapv #(/ % 3.5) (range -10 11)))
(def ^:private node-weights
  (let [w (map #(Math/exp (* -0.5 % %)) nodes) s (reduce + w)] (mapv #(/ % s) w)))

(defn- shock-var
  "The variance over a year shock of sd `s` of the year's expected count."
  [lps s]
  (let [vs (mapv (fn [z] (reduce + (map #(m/expit (+ % (* s z))) lps))) nodes)
        mu (reduce + (map * node-weights vs))]
    (reduce + (map (fn [w v] (* w (- v mu) (- v mu))) node-weights vs))))

(defn year-shock-sd
  "The sd of a year shock on the log odds, common to every row of a year,
  by the method of moments: the yearly counts' squared departures from
  the base's expected counts (centred for an overall bias), less their
  binomial variance, matched to what a shock of sd s adds (the variance
  of the year's expected count over the shock). 0 when the counts are not
  overdispersed."
  [rows]
  (let [by (vals (group-by :t rows))
        d (mapv (fn [rs] (reduce + (map #(- (if (:y %) 1.0 0.0) (:p %)) rs))) by)
        w (mapv (fn [rs] (reduce + (map #(* (:p %) (- 1.0 (:p %))) rs))) by)
        b (/ (reduce + d) (max 1e-12 (reduce + w)))
        excess (- (reduce + (map (fn [di wi] (let [e (- di (* b wi))] (* e e))) d w)) (reduce + w))
        lps (mapv (fn [rs] (mapv :o rs)) by)
        added (fn [s] (reduce + (map #(shock-var % s) lps)))]
    (if (<= excess 0.0)
      0.0
      (loop [lo 0.0 hi 3.0 i 0]
        (let [mid (* 0.5 (+ lo hi))]
          (if (= i 40) mid (if (< (added mid) excess) (recur mid hi (inc i)) (recur lo mid (inc i)))))))))

(defn- ar1
  "An AR(1) at `rho` with unit stationary variance over the years `from`
  to `to`, scaled by `sd`: {year x}."
  [g rho sd from to]
  (let [e (Math/sqrt (- 1.0 (* rho rho)))]
    (loop [t from x (m/normal! g) acc (transient {})]
      (if (> t to)
        (persistent! acc)
        (recur (inc t) (+ (* rho x) (* e (m/normal! g))) (assoc! acc t (* sd x)))))))

(defn state-panel
  "{country {year x}} over the years `from` to `to`: :own an AR(1) at 0.9
  within each country, independent across them; :level a persistent
  country mean (variance 0.9) plus an AR(1) at 0.9 of variance 0.1; :world
  one AR(1) at 0.9, the same at every country."
  [g kind cs from to]
  (case kind
    :own (into {} (for [c cs] [c (ar1 g 0.9 1.0 from to)]))
    :level (into {} (for [c cs :let [mu (* (Math/sqrt 0.9) (m/normal! g))]]
                      [c (update-vals (ar1 g 0.9 (Math/sqrt 0.1) from to) #(+ mu %))]))
    :world (let [w (ar1 g 0.9 1.0 from to)] (zipmap cs (repeat w)))))

(defn placebo
  "(fn [country year]) the placebo reads, as bnm.state-diag builds it:
  another country's x (the countries rotated by one in order), or for a
  world series x ten years before."
  [kind cs x]
  (if (= kind :world)
    (fn [c t] (get-in x [c (- t 10)]))
    (let [other (zipmap cs (concat (rest cs) [(first cs)]))]
      (fn [c t] (get-in x [(other c) t])))))

(defn standardize
  "The panel `x` less its mean over the design `rows` ({:c :t}), over its
  sd there."
  [x rows]
  (let [vs (keep #(get-in x [(:c %) (:t %)]) rows)
        n (max 1 (count vs))
        mu (/ (reduce + vs) n)
        sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) vs)) n))
        sd (if (pos? sd) sd 1.0)]
    (update-vals x (fn [byt] (update-vals byt #(/ (- % mu) sd))))))

(defn fit-offset
  "The maximum-likelihood coefficient b of a logit with offsets `os` on the
  one input `xs` (nil: an intercept), no prior: augur.logit/fit's free
  first coefficient, by Newton's steps."
  ^double [os xs ys]
  (let [os (double-array os)
        n (alength os)
        xs (if xs (double-array xs) (double-array n 1.0))
        ys (double-array (map #(if % 1.0 0.0) ys))]
    (loop [b 0.0 i 0]
      (let [[gr h] (loop [r 0 gr 0.0 h 0.0]
                     (if (< r n)
                       (let [x (aget xs r) p (m/expit (+ (aget os r) (* b x)))]
                         (recur (inc r) (+ gr (* x (- (aget ys r) p))) (+ h (* x x p (- 1.0 p)))))
                       [gr h]))
            step (if (pos? h) (/ gr h) 0.0)
            ;; damped against overshooting where the likelihood is flat
            step (max -5.0 (min 5.0 step))
            b' (+ b step)]
        (if (or (< (Math/abs step) 1e-10) (>= i 100)) b' (recur b' (inc i)))))))

(defn score-pair
  "[lr-real lr-placebo]: bnm.relation-diag/clustered-test's statistic (the
  score at the offset-and-intercept fit, its variance summed within each
  year) for the inputs `xa` and `xb` on the same `rows` ({:o :t}) with
  outcomes `ys`, one null fit for both."
  [rows ys xa xb]
  (let [os (mapv :o rows)
        b0 (fit-offset os nil ys)
        ps (double-array (map #(m/expit (+ b0 %)) os))
        n (alength ps)
        ws (double-array (map #(* % (- 1.0 %)) ps))
        sw (reduce + ws)
        res (double-array (map (fn [y p] (- (if y 1.0 0.0) p)) ys ps))
        yix (let [ix (zipmap (distinct (map :t rows)) (range))] (int-array (map #(ix (:t %)) rows)))
        ny (inc (reduce max -1 yix))
        stat (fn [xs]
               (let [xs (double-array xs)
                     xbar (if (pos? sw) (/ (loop [r 0 a 0.0] (if (< r n) (recur (inc r) (+ a (* (aget ws r) (aget xs r)))) a)) sw) 0.0)
                     by (double-array ny)]
                 (dotimes [r n]
                   (let [k (aget yix r)] (aset by k (+ (aget by k) (* (- (aget xs r) xbar) (aget res r))))))
                 (let [u (reduce + by) v (reduce + (map #(* % %) by))]
                   (if (pos? v) (/ (* u u) v) 0.0))))]
    [(stat xa) (stat xb)]))

(defn paired-diffs
  "augur.score/paired-loss-2way's statistic and interval for several
  columns `ds` of per-row loss differences (the bench's less the model's)
  on the same `rows` [period _ _ _ unit group], one set of bootstrap
  weights for all (the same draws: units at 4, blocks of :per-block
  periods): [{:diff :lo :hi} ...], one per column."
  [rows ds {:keys [reps seed alpha per-block]}]
  (let [rows (vec rows)
        n (count rows)
        ds (mapv double-array ds)
        gi (let [ix (zipmap (distinct (map #(nth % 5 nil) rows)) (range))] (int-array (map #(ix (nth % 5 nil)) rows)))
        ng (inc (reduce max -1 gi))
        units (vec (distinct (map #(nth % 4) rows)))
        blocks (vec (distinct (map #(quot (long (first %)) (long per-block)) rows)))
        ui (let [ix (zipmap units (range))] (int-array (map #(ix (nth % 4)) rows)))
        bi (let [ix (zipmap blocks (range))] (int-array (map #(ix (quot (long (first %)) (long per-block))) rows)))
        nu (count units) nb (count blocks)
        g (m/rng seed)
        gmd (fn [^doubles d ^doubles w]
              (let [s (double-array ng) t (double-array ng)]
                (dotimes [r n]
                  (let [k (aget gi r) wr (aget w r)]
                    (aset s k (+ (aget s k) (* wr (aget d r))))
                    (aset t k (+ (aget t k) wr))))
                (loop [k 0 acc 0.0 c 0]
                  (if (< k ng)
                    (if (pos? (aget t k)) (recur (inc k) (+ acc (/ (aget s k) (aget t k))) (inc c)) (recur (inc k) acc c))
                    (if (pos? c) (/ acc c) 0.0)))))
        draws (let [acc (mapv (fn [_] (double-array reps)) ds)]
                (dotimes [i reps]
                  (let [wu (double-array nu) wb (double-array nb) w (double-array n)]
                    (dotimes [_ nu] (let [j (min (dec nu) (long (* nu (m/next-double! g))))] (aset wu j (inc (aget wu j)))))
                    (dotimes [_ nb] (let [j (min (dec nb) (long (* nb (m/next-double! g))))] (aset wb j (inc (aget wb j)))))
                    (dotimes [r n] (aset w r (* (aget wu (aget ui r)) (aget wb (aget bi r)))))
                    (doseq [[a d] (map vector acc ds)] (aset ^doubles a i (double (gmd d w))))))
                (mapv #(vec (sort %)) acc))
        ones (double-array n 1.0)
        at (fn [dr q] (nth dr (min (dec reps) (long (* q reps)))))]
    (mapv (fn [d dr] {:diff (gmd d ones) :lo (at dr (/ alpha 2.0)) :hi (at dr (- 1.0 (/ alpha 2.0)))}) ds draws)))

(defn paired-losses
  "augur.score/paired-loss-2way for several `losses` on the same `rows`
  [period p-model p-bench outcome unit group], the same draws for all
  (paired-diffs)."
  [rows losses opts]
  (paired-diffs rows (mapv (fn [loss] (map (fn [[_ pm pb y]] (- (double (loss pb y)) (double (loss pm y)))) rows)) losses) opts))

(defn- draw-outcomes
  "Outcomes for `rows` from the base's log odds, the year shocks `u` and
  beta times `xs`."
  [g rows u beta xs]
  (mapv (fn [r x] (< (m/next-double! g) (m/expit (+ (:o r) (get u (:t r) 0.0) (* beta x))))) rows xs))

(defn- year-shocks [g years s]
  (into {} (for [t years] [t (if (pos? s) (* s (m/normal! g)) 0.0)])))

(defn- span [rows] [(- (reduce min (map :t rows)) 20) (reduce max (map :t rows))])

(defn diag-draw
  "One draw on a kind's design `rows`: a fresh state of `kind`, year
  shocks of sd `s`, outcomes; {:lr :placebo-lr} of the real state and its
  placebo."
  [g rows cs kind beta s]
  (let [[from to] (span rows)
        x (standardize (state-panel g kind cs from to) rows)
        pl (placebo kind cs x)
        ;; the placebo known where the state is, as state-diag keeps rows
        rows (filterv #(some? (pl (:c %) (:t %))) rows)
        xa (mapv #(get-in x [(:c %) (:t %)]) rows)
        xb (mapv #(pl (:c %) (:t %)) rows)
        u (year-shocks g (distinct (map :t rows)) s)
        ys (draw-outcomes g rows u beta xa)
        [a b] (score-pair rows ys xa xb)]
    {:lr a :placebo-lr b}))

(defn diag-sims
  "`n` draws (diag-draw) from seeds `seed`, `seed` + 1, ..."
  [rows cs kind beta s n seed]
  (vec (pmap #(diag-draw (m/rng (+ seed %)) rows cs kind beta s) (range n))))

(defn pass-share
  "The share of draws passing bnm.relation-diag/passes? at `k` candidates."
  [res k]
  (/ (count (filter #(rd/passes? % k) res)) (double (max 1 (count res)))))

(defn mc-error "The Monte Carlo standard error of a share `p` from `n` draws." [p n]
  (Math/sqrt (/ (* p (- 1.0 p)) (max 1 n))))

(defn- slim [r] (-> r (dissoc :draws) (assoc :gain? (pos? (:lo r)))))

(defn oos-sim
  "One draw over every kind (`rows-by-kind` {kind rows} on design and test
  years): a state of `kind` (one panel, standardized per kind on its design
  rows), year shocks of sd (`sds` kind) per kind, outcomes with `beta`.
  Per kind its diagnostic {:lr :placebo-lr} on the design years, and on
  the test years the oracle (beta known) and the realistic model (beta
  fitted on the design years, the base as offset) against the base,
  bnm.world-score's paired bootstrap at one-sided 5% (`reps`); pooled with
  kinds weighed equally. Results {:diff :lo :hi :gain?}."
  [rows-by-kind cs kind beta sds dfrom dto tfrom tto g reps]
  (let [all (mapcat val rows-by-kind)
        from (- (reduce min (map :t all)) 20) to (reduce max (map :t all))
        x0 (state-panel g kind cs from to)
        opts {:reps reps :seed 1 :alpha 0.1 :per-block 1}
        per (into (sorted-map)
                  (for [[kd rows] (sort-by key rows-by-kind)
                        :let [design (filterv #(<= dfrom (:t %) dto) rows)
                              test (filterv #(<= tfrom (:t %) tto) rows)
                              x (standardize x0 design)
                              pl (placebo kind cs x)
                              design (filterv #(some? (pl (:c %) (:t %))) design)
                              xd (mapv #(get-in x [(:c %) (:t %)]) design)
                              xt (mapv #(get-in x [(:c %) (:t %)]) test)
                              u (year-shocks g (distinct (map :t rows)) (get sds kd 0.0))
                              yd (draw-outcomes g design u beta xd)
                              yt (draw-outcomes g test u beta xt)
                              [a b] (score-pair design yd xd (mapv #(pl (:c %) (:t %)) design))
                              bhat (fit-offset (mapv :o design) xd yd)
                              tr (fn [bb] (mapv (fn [r y xv] [(:t r) (m/expit (+ (:o r) (* bb xv))) (:p r) y (:c r) kd]) test yt xt))]]
                    [kd {:diag {:lr a :placebo-lr b} :bhat bhat :oracle (tr beta) :realistic (tr bhat)}]))
        ;; one set of bootstrap weights for both models and both scores
        score (fn [ora rea]
                (let [d (fn [loss rs] (map (fn [[_ pm pb y]] (- (double (loss pb y)) (double (loss pm y)))) rs))
                      [ob ol rb rl] (map slim (paired-diffs ora [(d pd/brier ora) (d pd/log-loss ora) (d pd/brier rea) (d pd/log-loss rea)] opts))]
                  {:oracle {:brier ob :log ol} :realistic {:brier rb :log rl}}))]
    {:per-kind (update-vals per (fn [{:keys [oracle realistic] :as r}] (merge (dissoc r :oracle :realistic) (score oracle realistic))))
     :pooled (score (vec (mapcat :oracle (vals per))) (vec (mapcat :realistic (vals per))))}))

(defn min-detectable
  "{:or :gain :power} where power first reaches `target` over `grid` ([{:or
  :power :gain}], by OR), interpolated on log OR; nil if it never does."
  [grid target]
  (let [grid (vec (sort-by :or grid))]
    (if (and (seq grid) (>= (:power (first grid)) target))
      (first grid)
      (some (fn [[a b]]
              (when (and (< (:power a) target) (>= (:power b) target))
                (let [f (/ (- target (:power a)) (- (:power b) (:power a)))
                      lo (Math/log (:or a)) hi (Math/log (:or b))]
                  {:or (Math/exp (+ lo (* f (- hi lo)))) :gain (+ (:gain a) (* f (- (:gain b) (:gain a)))) :power target})))
            (partition 2 1 grid)))))

(defn- mean [xs] (if (seq xs) (/ (reduce + xs) (double (count xs))) 0.0))

(defn -main [& args]
  (let [{:keys [run sims oos-sims reps kinds types out]} (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        run (or run "t44-world-norel")
        sims (or (some-> sims parse-long) 200)
        oos-sims (or (some-> oos-sims parse-long) 100)
        reps (or (some-> reps parse-long) 400)
        types (if types (mapv keyword (str/split types #",")) state-types)
        out (or out "data/runs/sequencing/power-audit.tsv")
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        years (into {} (pmap (fn [kd] [kd (rd/read-year run kd)]) ks))
        places (set (mapcat keys (vals years)))
        tree (fac/place-tree places)
        cs (vec (sort (filter tree places)))
        all-rows (into (sorted-map) (for [kd ks] [kd (year-rows (years kd) cs 1960 2022 (wu/known-lag kd))]))
        ;; as state-diag: a kind needs five design-year onsets to be tried
        rows (into (sorted-map) (filter (fn [[_ rs]] (>= (count (filter #(and (:y %) (<= (:t %) 2000)) rs)) 5)) all-rows))
        ks (vec (keys rows))
        design (update-vals rows (fn [rs] (filterv #(<= (:t %) 2000) rs)))
        sds (into (sorted-map) (pmap (fn [[kd rs]] [kd (year-shock-sd rs)]) design))
        buf (StringBuilder.)
        say (fn [s] (println s) (.append buf (str s "\n")))
        t0 (System/currentTimeMillis)
        el #(format "%.0fs" (/ (- (System/currentTimeMillis) t0) 1000.0))]
    (say "section\tkind\ttype\tor\tshocks\tk_or_model\tscore\tn\tvalue\tmc_se\tmean_gain")
    (doseq [kd (remove (set ks) (keys all-rows))]
      (say (format "skipped\t%s\t\t\t\t\t\t%d\t\t\t" kd (count (filter #(and (:y %) (<= (:t %) 2000)) (all-rows kd))))))
    (doseq [[kd s] sds]
      (say (format "year-shock-sd\t%s\t\t\t\t\t\t%d\t%.3f\t\t" kd (count (filter :y (design kd))) s)))
    ;; 1. the diagnostic, per kind, with and without year shocks
    (let [diag (atom {})]
      (doseq [ty types orr odds-ratios shocks? [true false] kd ks]
        (let [res (diag-sims (design kd) cs ty (Math/log orr) (if shocks? (sds kd) 0.0) sims (hash [kd ty orr shocks?]))]
          (doseq [k list-sizes :let [p (pass-share res k)]]
            (swap! diag update [ty orr shocks? k] (fnil conj []) p)
            (say (format "diag\t%s\t%s\t%.2f\t%s\t%.1f\t\t%d\t%.3f\t%.3f\t" kd (name ty) orr shocks? (double k) sims p (mc-error p sims))))))
      (doseq [[[ty orr shocks? k] ps] (sort-by key @diag) :let [p (mean ps)]]
        ;; mean over kinds; its MC error from the kinds' independent draws
        (say (format "diag-mean\tall\t%s\t%.2f\t%s\t%.1f\t\t%d\t%.3f\t%.3f\t" (name ty) orr shocks? (double k) (* sims (count ps)) p
                     (/ (Math/sqrt (reduce + (map #(* % (- 1.0 %)) ps))) (* (count ps) (Math/sqrt sims))))))
      (binding [*out* *err*] (println "diagnostic done" (el))))
    ;; 2. out of sample, with year shocks
    (let [grid (atom {})]
      (doseq [ty types orr odds-ratios]
        (let [draws (vec (pmap #(oos-sim rows cs ty (Math/log orr) sds 1960 2002 2003 2022 (m/rng (+ 7919 (* 31 %) (hash [ty orr]))) reps)
                               (range oos-sims)))]
          (doseq [model [:oracle :realistic] sc [:brier :log]
                  [kd get-r] (concat [["pooled" #(get-in % [:pooled model sc])]]
                                     (for [kd ks] [kd #(get-in % [:per-kind kd model sc])]))
                  :let [rs (map get-r draws)
                        p (/ (count (filter :gain? rs)) (double oos-sims))
                        g (mean (map :diff rs))]]
            (swap! grid assoc-in [[kd ty model sc] orr] {:or orr :power p :gain g})
            (say (format "oos\t%s\t%s\t%.2f\ttrue\t%s\t%s\t%d\t%.3f\t%.3f\t%.6f" kd (name ty) orr (name model) (name sc) oos-sims p (mc-error p oos-sims) g)))
          (binding [*out* *err*] (println "oos" ty orr (el)))))
      (doseq [[[kd ty model sc] by-or] (sort-by key @grid)
              :let [r (min-detectable (vals by-or) 0.8)]]
        (say (format "mde\t%s\t%s\t%s\ttrue\t%s\t%s\t%d\t%s\t\t%s" kd (name ty) (if r (format "%.2f" (:or r)) ">2.5") (name model) (name sc) oos-sims
                     (if r "0.800" "") (if r (format "%.6f" (:gain r)) "")))))
    (spit out (str buf))
    (println "wrote" out (el))))
