(ns bnm.recalibrate-online
  "Trial T8, online (bead bnm-3qu.9): the base recalibrated as it runs. At
  the year node of a run (an onset in (t, t + 2], seen from the end of
  year t), per kind,

    logit(y) = a_t + b_t L,   L the base's log odds,

  with (a_t, b_t) a state learned only from origins whose outcomes are in
  by the origin: the forecast at t has seen the windows (t', t' + 2] with
  t' + 2 + lag <= t, nothing else. A fixed recalibration fitted on
  1960-2002 lost out of sample (docs/PHASE-CHANGES.md, T2/T8): the base's
  miscalibration drifts, so the recalibration has to drift with it.

  The state is a dynamic logistic regression (augur.dynamic) on [1, L - c]
  (c a fixed centre, so the intercept is the correction at a typical
  forecast). Each year its mean reverts toward the prior's, the identity
  (a, b) = (0, 1): m <- target + phi (m - target), the shrinkage; then it
  is discounted (C / delta, the random walk's noise) and updated with the
  year's newly resolved rows. The discount is not chosen: dynamic model averaging over a small
  grid of discounts weighs them by how well each forecast the rows as they
  resolved (Raftery, Karny & Ettler 2010). With :pool w > 0 the reversion
  target is pooled across kinds, (1 - w) identity + w the kinds' mean
  state at the start of the year: a hierarchical prior over kinds. On the
  design years (scored 1981-2002) phi 0.95 and w 0.5 did best of phi in
  {0.9, 0.95, 0.98, 1} and w in {0, 0.5}; pooling gained +0.0008 log per
  row over the kinds alone.

  Scored on origins --from to --to - 2 (windows ending by --to), learned
  from --learn-from (1960) onward throughout, which is legitimate on the
  design years because the filter is causal: no fitted year is scored.
  Log score, Brier and recall in the top 10% (within kind and origin year)
  per row, raw base against online, per kind, per group and pooled, with
  90% intervals from a moving-block bootstrap of origin years (blocks of
  --block years, as the two-year windows overlap).

  The recalibrated forecasts are written as year-node rows of the
  lattice's forecasts.tsv (scale place origin-month p y) under
  data/runs/<--out-run>/<kind>/, every origin from --learn-from on, y
  given only where its window ends by --to.

  No outcome whose window ends after --to is read.

    jolt -A:tools -M -m bnm.recalibrate-online [--run t44-world-norel]
      [--from 1981] [--to 2002] [--learn-from 1960] [--revert 0.95]
      [--pool 0.5] [--lag 0] [--draws 1000] [--block 2] [--seed 1]
      [--out data/runs/sequencing/recalibrate-online-design.tsv]
      [--out-run t44-world-norel-online]"
  (:require [augur.dynamic :as d]
            [augur.math :as m]
            [bnm.kinds :as kinds]
            [bnm.lattice-banking :as lb]
            [bnm.relation-diag :as rd]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- the filter ---------------------------------------------------------------------

(defn defaults
  "The filter's settings: the discount grid averaged over, DMA's forgetting,
  the yearly reversion phi toward the prior (1: none), the prior variances
  of the centred intercept and the slope, the centre, extra years of lag
  before an outcome counts as known, and the pooling weight."
  []
  {:discounts [0.9 0.95 0.98 1.0] :forget 0.99 :revert 0.95
   :prior-var [1.0 0.25] :centre -2.5 :lag 0 :pool 0.5})

(defn- identity-mean [{:keys [centre]}] [(double centre) 1.0])

(defn- one-model [{:keys [prior-var]} mean discount]
  (d/logistic {:mean mean :var prior-var :discount discount}))

(defn initial
  "A kind's starting state: the DMA over the discount grid, each model's
  mean at the raw (a, b) given (the identity by default)."
  ([opts] (initial opts 0.0 1.0))
  ([{:keys [discounts forget centre] :as opts} a b]
   (let [mean [(+ a (* b centre)) b]]
     (d/averaged (into {} (for [dl discounts] [dl (one-model opts mean dl)])) {:forget forget}))))

(defn- mean-of
  "The state's centred mean, the models' weighted."
  [st]
  (let [w (d/weights st)]
    (reduce (fn [acc [k mdl]] (mapv + acc (map #(* (w k) %) (d/means mdl)))) [0.0 0.0] (:models st))))

(defn ab
  "The raw (a, b) of a centred mean."
  [{:keys [centre]} [m0 m1]]
  [(- m0 (* m1 centre)) m1])

(defn- revert
  "Each model's mean pulled toward `target` by phi: m <- target + phi (m -
  target). Its covariance is left to the discount, which carries the
  random walk's noise."
  [st target {:keys [revert]}]
  (let [phi (double revert)]
    (if (>= phi 1.0)
      st
      (update st :models
              update-vals
              (fn [mdl]
                (let [^doubles mm (:m mdl)
                      m' (double-array (:k mdl))]
                  (dotimes [i (:k mdl)] (aset m' i (+ (double (target i)) (* phi (- (aget mm i) (double (target i)))))))
                  (assoc mdl :m m')))))))

(defn- x-of [{:keys [centre]} L] [1.0 (- (double L) (double centre))])

(defn run-online
  "`by-kind` {kind [{:c :t :L :y}]}, :y nil where unknown. Year by year
  from the first origin to the last: each kind's state reverts (toward
  the identity, or the pooled target), is discounted and learns from the
  rows of origin t - 2 - lag (their windows closed by t), then forecasts
  the rows of origin t. {:forecasts {kind [row + :p :raw]} :trace {kind {t
  [a b]}} :weights {kind {t {discount weight}}}}, the trace and weights as
  they stood for the forecasts at t. Optional :start {kind state}."
  [by-kind {:keys [lag pool start] :as opts}]
  (let [ks (vec (sort (keys by-kind)))
        idx (into {} (for [k ks] [k (group-by :t (by-kind k))]))
        ts (mapcat #(map :t %) (vals by-kind))]
    (if (empty? ts)
      {:forecasts {} :trace {} :weights {}}
      (let [t0 (apply min ts) t1 (apply max ts)
            id (identity-mean opts)
            pool (double (or pool 0.0))]
        (loop [t t0
               states (into {} (for [k ks] [k (or (get start k) (initial opts))]))
               out {:forecasts (zipmap ks (repeat [])) :trace (zipmap ks (repeat {})) :weights (zipmap ks (repeat {}))}]
          (if (> t t1)
            out
            (let [pooled (when (pos? pool)
                           (let [ms (map mean-of (vals states))]
                             (mapv #(/ % (count ms)) (reduce #(mapv + %1 %2) ms))))
                  target (if pooled (mapv #(+ (* (- 1.0 pool) %1) (* pool %2)) id pooled) id)
                  learn-t (- t 2 (long lag))
                  states (into {} (for [k ks]
                                    (let [rows (for [r (get-in idx [k learn-t]) :when (some? (:y r))]
                                                 {:x (x-of opts (:L r)) :y (:y r)})]
                                      [k (d/step (revert (states k) target opts) rows)])))
                  out (reduce (fn [out k]
                                (let [st (states k)
                                      fs (for [r (get-in idx [k t])]
                                           (assoc r :p (d/predict st (x-of opts (:L r))) :raw (m/expit (:L r))))]
                                  (-> out
                                      (update-in [:forecasts k] into fs)
                                      (assoc-in [:trace k t] (ab opts (mean-of st)))
                                      (assoc-in [:weights k t] (d/weights st)))))
                              out ks)]
              (recur (inc t) states out))))))))

;; --- scores -------------------------------------------------------------------------

(defn- yv ^double [r] (if (:y r) 1.0 0.0))

(defn- lg ^double [r p] (Math/log (if (:y r) p (- 1.0 p))))

(defn mean-log [rows f] (/ (reduce + (map #(lg % (f %)) rows)) (max 1 (count rows))))

(defn brier [rows f] (/ (reduce + (map #(Math/pow (- (yv %) (f %)) 2) rows)) (max 1 (count rows))))

(defn top-recall
  "The share of onsets in the top 10% of rows by (f row) within each kind
  and origin year."
  [rows f]
  (let [tot (count (filter :y rows))
        hit (reduce + (for [[_ rs] (group-by (juxt :kind :t) rows)
                            :let [n (long (Math/ceil (* 0.1 (count rs))))]]
                        (count (filter :y (take n (sort-by (comp - f) rs))))))]
    (if (pos? tot) (/ (double hit) tot) Double/NaN)))

(defn block-bootstrap
  "90% interval of the mean of (f row) over rows, origin years resampled in
  circular moving blocks of `block` years."
  [rows f block draws seed]
  (let [years (vec (sort (distinct (map :t rows))))
        sums (let [by (group-by :t rows)] (mapv (fn [t] [(reduce + (map f (by t))) (count (by t))]) years))
        ny (count years) nb (long (Math/ceil (/ (double ny) block)))
        g (m/rng seed)
        xs (double-array (sort (for [_ (range draws)]
                                 (let [pick (for [_ (range nb)
                                                  :let [s (long (Math/floor (* ny (m/next-double! g))))]
                                                  j (range block)]
                                              (sums (mod (+ s j) ny)))]
                                   (/ (reduce + (map first pick)) (max 1 (reduce + (map second pick))))))))]
    [(m/quantile xs 0.05) (m/quantile xs 0.95)]))

;; --- data ---------------------------------------------------------------------------

(defn rows-of
  "Rows {:kind :c :t :L :y} for cells [c t] with t >= `from` where the base
  is known; :y the outcome only where the window (t, t + 2] ends by `to`
  (else nil: never asked)."
  [{:keys [cells kind from to base outcome]}]
  (vec (for [[c t] cells
             :when (>= t from)
             :let [L (base c t)] :when (some? L)]
         {:kind kind :c c :t t :L (double L)
          :y (when (<= (+ t 2) to) (outcome c t))})))

(defn frames-of
  "{kind rows}: the run's year-node forecasts (countries) and outcomes from
  the kind's catalogue, as bnm.calibration-regime reads them."
  [run cats ks from to]
  (into {} (for [k ks :let [yr (rd/read-year run k)]]
             [k (rows-of {:cells (for [[c byo] yr [o _] byo :when (= 11 (mod o 12))] [c (quot o 12)])
                          :kind k :from from :to to
                          :base (fn [c t] (when-let [[p _] (get-in yr [c (+ (* 12 t) 11)])]
                                            (m/logit (max 1e-6 (min (- 1.0 1e-6) p)))))
                          :outcome (fn [c t] (let [o (+ (* 12 t) 11)] (#'lb/onset-in? (cats k) c o o (+ o 24))))})])))

(defn forecast-lines
  "Year-node rows in the lattice's forecasts.tsv format."
  [rows]
  (apply str (for [{:keys [c t p y]} rows]
               (str "year\t" c "\t" (+ (* 12 t) 11) "\t" p "\t" (if (nil? y) "" y) "\n"))))

(def groups
  "The kind groups of T2/T8, and the rest."
  {"financial" #{"banking_crisis" "sovereign_default" "growth_collapse" "fiscal_consolidation" "currency_crisis"}
   "social" #{"protest_surge" "violence_surge" "conflict_onset" "war_onset" "coup"}
   "other" #{"inflation_crisis" "market_crash" "food_price_shock"}})

;; --- the run ------------------------------------------------------------------------

(defn parse-opts [args]
  (let [o (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        n (fn [k dflt] (or (some-> (o k) parse-long) dflt))
        x (fn [k dflt] (or (some-> (o k) parse-double) dflt))
        run (or (:run o) "t44-world-norel")]
    {:run run
     :from (n :from 1981) :to (n :to 2002) :learn-from (n :learn-from 1960)
     :revert (x :revert (:revert (defaults))) :pool (x :pool (:pool (defaults))) :lag (n :lag 0)
     :draws (n :draws 1000) :block (n :block 2) :seed (n :seed 1)
     :out (or (:out o) (str "data/runs/sequencing/recalibrate-online-" (n :from 1981) "-" (n :to 2002) ".tsv"))
     :out-run (or (:out-run o) (str run "-online"))}))

(defn -main [& args]
  (let [{:keys [run from to learn-from revert pool lag draws block seed out out-run]} (parse-opts args)
        _ (when (> to 2002) (println "note: outcomes to" to "are read (the registered test)"))
        cats (kinds/catalogues)
        ks (vec (sort (filter #(and (cats %) (.exists (io/file (str "data/runs/" run "/" % "/forecasts.tsv"))))
                              (apply concat (vals groups)))))
        frames (frames-of run cats ks learn-from to)
        base (assoc (defaults) :revert revert :pool pool :lag lag)
        variants (concat [["online" base]
                          ["online-unpooled" (assoc base :pool 0.0)]
                          ["online-no-reversion" (assoc base :revert 1.0)]]
                         (for [dl (:discounts base)] [(str "fixed-delta-" dl) (assoc base :discounts [dl])]))
        results (into {} (for [[nm o] variants] [nm (run-online frames o)]))
        scored (fn [nm k] (filterv #(and (some? (:y %)) (<= from (:t %)) (<= (+ 2 (:t %)) to)) (get-in results [nm :forecasts k])))
        buf (StringBuilder.)
        say (fn [s] (println s) (flush) (.append buf (str s "\n")))
        iv (fn [[a b]] (format "[%+.5f, %+.5f]" a b))]
    (say (str "# T8 online: " run "'s year node recalibrated as it runs; learned from " learn-from
              " (windows closed by each origin, lag " lag "), scored origins " from "-" (- to 2) " (windows to " to ")"))
    (say (str "# filter: " (pr-str base) "; DMA over the discounts; "
              block "-year block bootstrap, " draws " draws, seed " seed))
    (say "table\tvariant\tscope\tn\tonsets\tlog-raw\tlog-online\tlog gain [90%]\tbrier-raw\tbrier-online\tbrier change [90%]\ttop10-raw\ttop10-online")
    (doseq [[nm _] variants
            [scope sks] (concat (for [k ks] [k [k]])
                                (for [[g gks] (sort groups) :let [sks (filterv gks ks)] :when (seq sks)] [(str "group:" g) sks])
                                [["pooled:financial+social" (filterv (into (groups "financial") (groups "social")) ks)]
                                 ["pooled:all" ks]])
            :when (or (= nm "online") (str/starts-with? scope "pooled") (str/starts-with? scope "group"))
            :let [rs (vec (mapcat #(scored nm %) sks))]
            :when (seq rs)]
      (say (format "score\t%s\t%s\t%d\t%d\t%.5f\t%.5f\t%+.5f %s\t%.5f\t%.5f\t%+.5f %s\t%.3f\t%.3f"
                   nm scope (count rs) (count (filter :y rs))
                   (mean-log rs :raw) (mean-log rs :p)
                   (- (mean-log rs :p) (mean-log rs :raw)) (iv (block-bootstrap rs #(- (lg % (:p %)) (lg % (:raw %))) block draws seed))
                   (brier rs :raw) (brier rs :p)
                   (- (brier rs :p) (brier rs :raw)) (iv (block-bootstrap rs #(- (Math/pow (- (yv %) (:p %)) 2) (Math/pow (- (yv %) (:raw %)) 2)) block draws seed))
                   (top-recall rs :raw) (top-recall rs :p))))
    ;; each variant against the primary, paired by row
    (say "table\tvariant\tscope\tn\tlog: variant - online [90%]\tbrier: variant - online [90%]")
    (doseq [[nm _] (rest variants)
            [scope sks] [["pooled:financial+social" (filterv (into (groups "financial") (groups "social")) ks)] ["pooled:all" ks]]
            :let [rs (vec (mapcat (fn [k] (map (fn [a b] (assoc a :q (:p b))) (scored "online" k) (scored nm k))) sks))]
            :when (seq rs)]
      (say (format "versus\t%s\t%s\t%d\t%+.5f %s\t%+.5f %s" nm scope (count rs)
                   (- (mean-log rs :q) (mean-log rs :p)) (iv (block-bootstrap rs #(- (lg % (:q %)) (lg % (:p %))) block draws seed))
                   (- (brier rs :q) (brier rs :p)) (iv (block-bootstrap rs #(- (Math/pow (- (yv %) (:q %)) 2) (Math/pow (- (yv %) (:p %)) 2)) block draws seed)))))
    ;; the learned recalibration through time, for the record
    (say "trace\tkind\torigin\ta\tb\tdiscount weights")
    (let [{:keys [trace weights]} (results "online")]
      (doseq [k ks t (filter #(zero? (mod % 5)) (range learn-from (inc (- to 2))))
              :let [[a b] (get-in trace [k t])] :when a]
        (say (format "trace\t%s\t%d\t%+.3f\t%.3f\t%s" k t a b
                     (str/join " " (for [[dl w] (sort (get-in weights [k t]))] (format "%s:%.2f" dl w)))))))
    (io/make-parents out)
    (spit out (str buf))
    (println "wrote" out)
    (doseq [k ks :let [f (str "data/runs/" out-run "/" k "/forecasts.tsv")]]
      (io/make-parents f)
      (spit f (forecast-lines (sort-by (juxt :t :c) (get-in results ["online" :forecasts k])))))
    (println "wrote" (str "data/runs/" out-run "/<kind>/forecasts.tsv") "for" (count ks) "kinds")))
