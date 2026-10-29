(ns bnm.shock-trial
  "Supply shocks as event triggers (bead bnm-82j.3; docs/PHASE-CHANGES.md,
  the registration of trial SS): do country-level supply shocks (the share
  of a country's food, fertiliser and energy supply lost to its suppliers'
  shortfalls and to disrupted straits, bnm.supply-shocks) tell which
  countries to watch for food price shocks, protest surges and violence
  surges beyond the base?

  Rows: the base's year node (t44-world-norel), year-end origins t, an
  onset within (t, t+2]. At each origin the models are refitted on the
  rows whose outcome was published by then (origins to `fit-cut`: the
  window closed and the catalogue's known-lag passed), from `fit-from`,
  each with a fixed effect per fitted origin year (so the shocks are read
  across countries within a year, not from how their coverage grows over
  the years), the scored year at the level that reproduces the fitted
  rows' rate (bnm.attribution/level):
    :base  the base as it is;
    :bp    a + b logit p (the base recalibrated: ranks as the base);
    :s     :bp plus the three families' losses (food, fertiliser, energy),
           each log(1 + L / 0.01), standardized on the fitted rows, ridge
           precision `lambda` per standardized coefficient (fixed in
           advance, not tuned);
    :plc   the same with each country's losses read from another country's
           (a random derangement, seed 1; a country keeps its own coverage:
           where the other has none, the year's mean);
    :tim   the same with the losses dated `time-lag` years late (the value
           of t - 6 at t, so still known at t).
  With fewer than `min-onsets` onsets in the fitted rows every model is the
  base.

  Scores (bnm.watchlist): top-k recall on hard and all rows, :s against
  :base, :plc, :tim, the base against persistence and climatology, the
  kinds equally weighed, 90% intervals from 1,000 draws of two-year blocks
  of origins; per kind, the one-sided bootstrap p of :s over :base for
  Holm; log and Brier per row of :s against :bp (paired), and AUC per kind.

    jolt -A:tools -M -m bnm.shock-trial --design true     design years (origins 1993-2000)
    jolt -A:tools -M -m bnm.shock-trial --power true      power on the test rows (base only)
    jolt -A:tools -M -m bnm.shock-trial --test true       the registered test (origins 2003-2022)
    jolt -A:tools -M -m bnm.shock-trial --test true --nulls 8   the watch-list rule v2, descriptive (bnm-82j.6)
    [--rebuild true] rebuilds data/derived/stocks/supply-shocks.csv from data/derived/shocks
    [--run t44-world-norel] the base; [--out path] in place of the mode's registered file"
  (:require [augur.math :as m]
            [augur.score :as sc]
            [bnm.attribution :as at]
            [bnm.kinds :as kinds]
            [bnm.supply-shocks :as ss]
            [bnm.warm-up :as wu]
            [bnm.watchlist :as wl]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def trial-kinds ["food_price_shock" "protest_surge" "violence_surge"])
(def run "The registered base; --run reruns on another." "t44-world-norel")
(def lambda "Ridge precision per standardized shock coefficient." 10.0)
(def scale "Losses enter as log(1 + L / scale)." 0.01)
(def time-lag "Years the time placebo dates the losses late." 6)
(def min-onsets 5)
(def seed 1)
(def panel-path "data/derived/stocks/supply-shocks.csv")

;; --- pieces ---------------------------------------------------------------------------

(defn feature [l] (if (nil? l) 0.0 (Math/log1p (/ (max 0.0 (double l)) scale))))

(defn swap-panel
  "The exposure placebo of `panel` ({c {t v}}): where c has a value, the
  value of (perm c) at t, else the mean of the year's values; where c has
  none, none."
  [panel perm]
  (let [means (update-vals (group-by first (for [[_ byt] panel [t v] byt] [t v]))
                           #(/ (reduce + (map second %)) (count %)))]
    (into {} (for [[c byt] panel]
               [c (into (sorted-map) (for [[t _] byt]
                                       [t (if-some [v (get-in panel [(perm c) t])] v (means t))]))]))))

(defn holm
  "Holm's step-down at `alpha`: whether each p (in the order given) rejects."
  [ps alpha]
  (let [m (count ps)
        order (sort-by #(nth ps %) (range m))
        rej (loop [[i & more] order j 0 out #{}]
              (if (and i (<= (nth ps i) (/ alpha (- m j))))
                (recur more (inc j) (conj out i))
                out))]
    (mapv #(contains? rej %) (range m))))

;; --- the fits ---------------------------------------------------------------------------

(def models {:bp nil :s :real :plc :exposure :tim :time})

(defn- logit ^double [p] (let [p (max 1e-6 (min (- 1.0 1e-6) (double p)))] (Math/log (/ p (- 1.0 p)))))

(defn- drow [r src]
  {:t (:t r) :y (:y r) :o 0.0
   :x (double-array (cons (logit (:p r)) (when src (get-in r [:x src]))))})

(defn forecasts
  "Rows of one kind with :p {:base :bp :s :plc :tim} at origins from..to,
  each origin's models fitted on the rows from `fit-from` to its fit-cut,
  the base's warm-up rows left out (bnm.warm-up): with `first-origin` (the
  kind's first origin) marked here, else the rows' own :warm-up marks
  (bnm.attribution/year-rows), else marked from the rows' first origin."
  [rows {:keys [lag from to fit-from first-origin]}]
  (let [fitted (wu/fit-rows (cond first-origin (wu/mark rows lag first-origin)
                                  (some #(contains? % :warm-up) rows) rows
                                  :else (wu/mark rows lag (reduce min (map :t rows)))))]
    (vec (apply concat
                (pmap (fn [t]
                        (let [cut (wu/fit-cut t lag)
                              fit (filterv #(<= fit-from (:t %) cut) fitted)
                              score (filterv #(= t (:t %)) rows)
                              base (fn [r] (assoc r :p (zipmap [:base :bp :s :plc :tim] (repeat (:p r)))))]
                          (if (< (count (filter :y fit)) min-onsets)
                            (map base score)
                            (let [preds (into {} (for [[id src] models
                                                       :let [fr (mapv #(drow % src) fit)
                                                             k (alength ^doubles (:x (first fr)))
                                                             f (at/fit-fe fr k (vec (cons 1e-4 (repeat (dec k) lambda))))
                                                             a (at/level f fr)]]
                                                   [id (mapv #(m/expit (+ a (at/linear f (drow % src)))) score)]))]
                              (map-indexed (fn [i r] (assoc r :p (assoc (update-vals preds #(nth % i)) :base (:p r))))
                                           score)))))
                      (range from (inc to)))))))

;; --- rows ---------------------------------------------------------------------------------

(defn build-panels
  "{family {iso {t loss}}} from data/derived/shocks for `isos`, `years`."
  [isos years]
  (let [db (ss/read-db "data/derived/shocks")]
    (into {} (pmap (fn [f] [f (ss/panel db f isos years)]) ss/family-order))))

(defn write-panels [panels path]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value\n")
    (doseq [f ss/family-order [c byt] (sort-by key (panels f)) [t v] byt]
      (.write w (format "%s,%d,shocks/%s-loss,%.6f\n" c t f (double v))))))

(defn read-panels [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[c t v x] (str/split line #",")
                    f (second (re-find #"shocks/(.*)-loss" v))]
                (assoc-in acc [f c (parse-long t)] (parse-double x))))
            {} (rest (line-seq r)))))

(defn with-features
  "Rows with :x {:real :exposure :time}, three features each in family
  order, and :hard, :pers, :clim from the catalogue `cat`; rows of
  countries the catalogue does not cover dropped."
  [rows panels perm cat]
  (let [exp (update-vals panels #(swap-panel % perm))
        tim (update-vals panels #(ss/shift-panel % time-lag))
        fx (fn [ps c t] (mapv #(feature (get-in ps [% c t])) ss/family-order))
        lag (:known-lag cat 0)]
    (vec (for [{:keys [c t] :as r} rows
               :let [on (get-in cat [:onsets c] (sorted-set))
                     fr (get-in cat [:from c])
                     o (+ (* 12 t) 11)]
               :when fr]
           (assoc r :x {:real (fx panels c t) :exposure (fx exp c t) :time (fx tim c t)}
                  :hard (wl/hard? on o) :pers (wl/persistence on lag o) :clim (wl/climatology on fr lag o))))))

;; --- scores -------------------------------------------------------------------------------

(defn- boot-p
  "Draws of a weighted-stat over `cells` ({kind {year [num den]}}) and the
  one-sided p: the share of draws at or below 0."
  [cells years {:keys [reps block]}]
  (let [g (m/rng seed)
        ds (vec (repeatedly reps #(wl/weighted-stat cells (wl/year-weights years block g))))]
    (/ (count (filter #(<= % 0.0) ds)) (double reps))))

(defn- loss-cells [rows a b loss from to]
  (into {} (for [[t rs] (group-by :t rows) :when (<= from t to)]
             [t [(reduce + 0.0 (map #(- (loss (get-in % [:p b]) (:y %)) (loss (get-in % [:p a]) (:y %))) rs)) (count rs)]])))

(defn- log-loss ^double [p y] (let [p (max 1e-12 (min (- 1.0 1e-12) (double p)))] (- (Math/log (if y p (- 1.0 p))))))
(defn- brier ^double [p y] (let [d (- (double p) (if y 1.0 0.0))] (* d d)))

(defn evaluate
  "Every score of the scored rows `by-kind` ({kind rows with :p}), with
  the catalogues' onsets for the lead times."
  [by-kind cats {:keys [from to] :as opts}]
  (let [years (range from (inc to))
        wlk (into {} (for [[kd rs] by-kind]
                       [kd {:rows (mapv #(update % :p assoc :pers (:pers %) :clim (:clim %)) rs)
                            :onsets (get-in cats [kd :onsets])}]))
        pairs [[:s :base] [:s :plc] [:s :tim] [:plc :base] [:tim :base] [:base :pers] [:base :clim]]
        cmp (wl/compare-sources wlk [:s :base :plc :tim :pers :clim] pairs opts)
        per-kind-p (into (sorted-map)
                         (for [[kd {:keys [rows]}] wlk
                               :let [ranked (wl/rank-rows (wl/sample-of rows :hard) [:s :base] [10])
                                     cells {kd (#'wl/diff-cells ranked :s :base 10 from to)}]]
                           [kd {:p (boot-p cells years opts) :ci (wl/paired-boot cells years opts)}]))
        hp (holm (vec (map :p (vals per-kind-p))) 0.05)
        loss (fn [a b f sample] (wl/paired-boot (into {} (for [[kd rs] by-kind] [kd (loss-cells (wl/sample-of rs sample) a b f from to)]))
                                                years opts))
        auc (into (sorted-map) (for [[kd rs] by-kind sample [:all :hard]
                                     :let [rs (wl/sample-of rs sample)]]
                                 [[kd sample] (into {} (for [k [:base :bp :s :plc :tim]]
                                                         [k (sc/auc (mapv #(get-in % [:p k]) rs) (mapv :y rs))]))]))]
    {:cmp cmp
     :per-kind (into (sorted-map) (map (fn [[kd v] h] [kd (assoc v :holm h)]) per-kind-p hp))
     :log {:s-bp (loss :s :bp log-loss :all) :s-base (loss :s :base log-loss :all) :plc-bp (loss :plc :bp log-loss :all)
           :tim-bp (loss :tim :bp log-loss :all) :bp-base (loss :bp :base log-loss :all) :s-bp-hard (loss :s :bp log-loss :hard)}
     :brier {:s-bp (loss :s :bp brier :all)}
     :auc auc
     :n (into (sorted-map) (for [[kd rs] by-kind] [kd {:rows (count rs) :onsets (count (filter :y rs))
                                                       :hard (count (filter :hard rs)) :hard-onsets (count (filter #(and (:hard %) (:y %)) rs))
                                                       :refit (count (remove #(= (get-in % [:p :s]) (get-in % [:p :base])) rs))}]))}))

(def equivalence
  "The registered bound: the gain the power check finds 80% of the time on
  these three kinds' hard rows (data/runs/sequencing/shock-trial-power.txt)."
  0.047)
(def rule-bound "The watch-list rule's bound (seven to thirteen kinds), reported too." 0.03)
(def harm-log "Harm when the lower end of :s less :bp, log per row, is below this." -0.002)

(defn verdict
  "pass, fail (equivalent to nothing), or level, by the registered rule."
  [{:keys [cmp log]}]
  (let [prim (get-in cmp [[:hard 10] :pairs [:s :base]])
        plc (get-in cmp [[:hard 10] :pairs [:s :plc]])
        harm? (< (:lo (:s-bp log)) harm-log)]
    (cond (and (pos? (:lo prim)) (pos? (:lo plc)) (not harm?)) "pass"
          (< (:hi prim) equivalence) (if harm? "fail (equivalent to nothing on the watch list; no harm on log not shown)" "fail (equivalent to nothing)")
          :else "level")))

;; --- the watch-list rule v2 (bead bnm-82j.6) ------------------------------------------------

(defn with-null-draws
  "The rows of the first of `draws` (scored rows, one vector per
  derangement seed) with each draw's exposure placebo as :n0, :n1, ...
  in :p, joined on country and origin: the null draws of the watch-list
  rule v2 (bnm.watchlist), each the losses read at another country."
  [draws]
  (let [idx (mapv #(into {} (for [r %] [[(:c r) (:t r)] (get-in r [:p :plc])])) draws)]
    (mapv (fn [r] (update r :p merge (into {} (map-indexed (fn [i ix] [(keyword (str "n" i)) (ix [(:c r) (:t r)])]) idx))))
          (first draws))))

(defn report-v2
  "The v2 rule on the scored rows with `r` null draws: top-10 recall on
  hard onsets, :s over the draws' mean (bnm.watchlist/null-test), and log
  per row of :s over the draws' mean (harm, against the registered -0.002)."
  [say by-kind r {:keys [from to alpha] :as opts}]
  (let [years (range from (inc to))
        ns (mapv #(keyword (str "n" %)) (range r))
        ranked (into {} (for [[kd rs] by-kind] [kd (wl/rank-rows (wl/sample-of rs :hard) (into [:s :base] ns) [10])]))
        cells (fn [src] (into {} (for [[kd rs] ranked] [kd (#'wl/cells rs src 10 from to)])))
        ng (wl/null-gain {:c (cells :s) :b (cells :base) :nulls (mapv cells ns)} years opts)
        t (wl/null-test (:cand ng) (:nulls ng) alpha)
        lg (fn [src] (wl/paired-boot (into {} (for [[kd rs] by-kind] [kd (loss-cells rs src :bp log-loss from to)])) years opts))
        lc (lg :s)
        ln (mapv #(:diff (lg %)) ns)
        ;; the draws' spread alone, for the gain and the harm (A3)
        h (wl/null-test (:diff lc) ln alpha)]
    (say (format "Watch-list rule v2 (descriptive): S against %d exposure-placebo draws (the registered derangement, seed 1, and %d from mixed seeds), origins %d-%d" r (dec r) from to))
    (say (format "   top-10 recall, hard onsets, less the base: S %+.4f; draws %s (mean %+.4f, sd %.4f)"
                 (:cand ng) (str/join " " (map #(format "%+.4f" %) (:nulls ng))) (:mean0 t) (:sd0 t)))
    (say (format "   gain over the null mean %+.4f [%+.4f %+.4f], one-sided p %.3f: %s" (:g t) (:lo t) (:hi t) (:p t) (if (:pass t) "GAIN" "no gain")))
    (say (format "   log per row over B': S %+.5f [%+.5f %+.5f]; draws %s; over the null mean %+.5f [%+.5f %+.5f]: %s"
                 (:diff lc) (:lo lc) (:hi lc) (str/join " " (map #(format "%+.5f" %) ln)) (:g h) (:lo h) (:hi h)
                 (cond (neg? (:hi h)) "HARM (costs more than the null draws)" (>= (:lo h) harm-log) "no harm"
                       :else "no harm beyond the null draws' cost; no-harm not shown")))
    (when (>= r 10)
      ;; the rule's size on the draws alone: each in turn the candidate
      ;; against R of the others (amendment A3: the draws' spread alone)
      (let [cs (mapv cells ns)
            draws (mapv #(:cand (wl/null-gain {:c % :b (cells :base) :nulls [%]} years (assoc opts :reps 2))) cs)
            old (count (filter (fn [c] (pos? (:lo (wl/paired-boot (into {} (for [[kd byt] c] [kd (into {} (for [[y {:keys [hits pos]}] byt] [y [(- hits (get-in (cells :base) [kd y :hits] 0.0)) pos]]))])) years opts)))) cs))]
        (doseq [rr [4 8 12 16] :when (< rr r)]
          (let [fp (wl/false-pass draws rr alpha {:subsets 300 :seed 1})]
            (say (format "   calibration, R=%d: a draw against %d others passes %d of %d (%.3f)" rr rr (:passes fp) (:tests fp) (:rate fp)))
            (let [hs (mapv (fn [[i s]] (neg? (:hi (wl/null-test (ln i) (mapv ln s) alpha)))) (wl/loo-sets r rr {:subsets 300 :seed 1}))]
              (say (format "   harm's size, R=%d: log harm %d of %d (%.3f)" rr (count (filter true? hs)) (count hs) (/ (count (filter true? hs)) (double (count hs))))))))
        (say (format "   (the 82j.1 rule: a draw gains on the base %d of %d times)" old r))))
    {:gain t :harm h}))

;; --- report -------------------------------------------------------------------------------

(defn- fmtd [{:keys [diff lo hi]}] (format "%+.4f [%+.4f %+.4f]" diff lo hi))
(defn- f3 [x] (if x (format "%.3f" (double x)) "  -  "))

(defn report [say res {:keys [from to]}]
  (let [{:keys [cmp per-kind log brier auc n]} res]
    (say (format "Rows (origins %d-%d): kind  rows  onsets  hard  hard onsets  rows refitted" from to))
    (doseq [[kd v] n] (say (format "  %-18s %5d %5d %5d %5d %5d" kd (:rows v) (:onsets v) (:hard v) (:hard-onsets v) (:refit v))))
    (say "Watch list (kinds equally weighed): recall per source; pairs, 90% interval")
    (doseq [sample [:hard :all] k wl/ks
            :let [{:keys [rates pairs]} (cmp [sample k])]]
      (say (format "  %-4s k=%-3s s %s base %s plc %s tim %s pers %s clim %s" (name sample) (if (= k :p10) "10%" (str k))
                   (f3 (get-in rates [:s :recall])) (f3 (get-in rates [:base :recall])) (f3 (get-in rates [:plc :recall]))
                   (f3 (get-in rates [:tim :recall])) (f3 (get-in rates [:pers :recall])) (f3 (get-in rates [:clim :recall]))))
      (say (str "        " (str/join "  " (for [[a b :as pr] [[:s :base] [:s :plc] [:s :tim] [:plc :base] [:tim :base] [:base :pers] [:base :clim]]]
                                             (str (name a) "-" (name b) " " (fmtd (pairs pr))))))))
    (say "  hard k=10 precision s / base, mean lead s / base:"
         (f3 (get-in cmp [[:hard 10] :rates :s :precision])) "/" (f3 (get-in cmp [[:hard 10] :rates :base :precision]))
         (f3 (get-in cmp [[:hard 10] :rates :s :lead])) "/" (f3 (get-in cmp [[:hard 10] :rates :base :lead])))
    (say "Per kind, hard k=10, s - base: recall gain [90%], one-sided bootstrap p, Holm (3 kinds, 5%)")
    (doseq [[kd {:keys [p ci holm]}] per-kind] (say (format "  %-18s %s  p %.3f  %s" kd (fmtd ci) p (if holm "rejects" "-"))))
    (say "Log score per row (gain of the first over the second), kinds equally weighed:")
    (doseq [[k v] log] (say (format "  %-10s %s" (name k) (fmtd v))))
    (say (format "  Brier s-bp %s" (fmtd (:s-bp brier))))
    (say "AUC per kind: base / bp / s / plc / tim")
    (doseq [[[kd sample] v] auc] (say (format "  %-18s %-4s %s" kd (name sample) (str/join " / " (map #(f3 (v %)) [:base :bp :s :plc :tim])))))
    (say (format "Primary upper end %+.4f: below the registered bound +%.3f %s, below the rule's +%.2f %s"
                 (:hi (get-in cmp [[:hard 10] :pairs [:s :base]])) equivalence
                 (< (:hi (get-in cmp [[:hard 10] :pairs [:s :base]])) equivalence)
                 rule-bound (< (:hi (get-in cmp [[:hard 10] :pairs [:s :base]])) rule-bound)))
    (say "Verdict:" (verdict res))))

(defn- coverage [say panels from to]
  (say (format "Shock panels (share of supply lost), years %d-%d: family  countries  country-years  share > 1%%  mean" from to))
  (doseq [f ss/family-order
          :let [vs (for [[_ byt] (panels f) [t v] byt :when (<= from t to)] v)]]
    (say (format "  %-7s %4d %6d %.2f %.4f" f (count (filter (fn [[_ byt]] (some #(<= from % to) (keys byt))) (panels f)))
                 (count vs) (if (seq vs) (/ (count (filter #(> % 0.01) vs)) (double (count vs))) 0.0)
                 (if (seq vs) (/ (reduce + vs) (count vs)) 0.0)))))

(defn parse-opts [args]
  (let [o (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))]
    (assoc o :run (or (:run o) run)
           :reps (or (some-> (:reps o) parse-long) 1000) :seed seed :alpha 0.1 :block 2)))

(defn out-path
  "Where a mode writes: --out, else the registered file of the mode (the
  test's v2 reading beside it)."
  [{:keys [design power nulls out]}]
  (or out
      (cond power "data/runs/sequencing/shock-trial-power.txt"
            :else (str "data/runs/sequencing/shock-trial-" (if design "design" "test") (when nulls "-v2") ".txt"))))

(defn -main [& args]
  (let [{:keys [design power test rebuild nulls run] :as opts} (parse-opts args)
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out (str s "\n"))))
        cats (kinds/catalogues)]
    (if power
      (do (wl/report-power say run cats [0.0 0.25 0.375 0.5 0.75 1.0] 100
                           (assoc opts :from 2003 :to 2022 :reps 200 :kinds trial-kinds))
          (spit (out-path opts) (str out)))
      (let [[kds from to last-row] (if design
                                    [["protest_surge" "violence_surge"] 1993 2000 2000]
                                    [trial-kinds 2003 2022 2022])
            _ (when-not (or design test) (throw (ex-info "--design true, --power true or --test true" {})))
            rows (into {} (for [kd kds] [kd (filterv #(<= 1980 (:t %) last-row) (at/year-rows run kd))]))
            ;; every trial kind's countries, so the panels and the placebo's derangement are the same in every mode
            isos (sort (distinct (for [kd trial-kinds r (at/year-rows run kd)] (:c r))))
            panels (if (or rebuild (not (.exists (io/file panel-path))))
                     (let [p (build-panels isos (range 1980 2023))] (write-panels p panel-path) p)
                     (read-panels panel-path))
            score (fn [sd] (into {} (for [kd kds]
                                      [kd (forecasts (with-features (rows kd) panels (ss/derangement isos sd) (cats kd))
                                                     {:lag (:known-lag (cats kd) 0) :from from :to to :fit-from 1980})])))
            scored (score seed)
            o (assoc opts :from from :to to)
            path (out-path opts)]
        (say (format "Supply shocks as event triggers (bnm.shock-trial), %s, run %s, kinds %s"
                     (if design "design years" "registered test") run (str/join "," kds)))
        (say (format "Fits refitted at each origin on origins 1980 to fit-cut; ridge %.0f; losses log(1 + L/%.2f); placebos: derangement seed %d, time %d years late"
                     lambda scale seed time-lag))
        (say "The base's warm-up rows (origins before it had read a published outcome, p near 0.5) left out of the fits")
        (coverage say panels from to)
        (if nulls
          (let [r (parse-long nulls)
                ;; the registered placebo's seed, then seeds mixed by
                ;; augur.math/seed-of: consecutive seeds give augur's rng
                ;; correlated streams (bnm-0z5)
                draws (cons scored (map #(score (m/seed-of seed %)) (range 1 r)))]
            (report-v2 say (into {} (for [kd kds] [kd (with-null-draws (map #(% kd) draws))])) r o))
          (report say (evaluate scored cats o) o))
        (io/make-parents path)
        (spit path (str out))
        (println "wrote" path)))))
