(ns bnm.watchlist
  "A use-case scoring rule for the event model: the watch list (bead
  bnm-82j.1). Brier and log scores are dominated by the many near-zero
  country-years and barely move when the few high-risk ones are ranked
  better; what the model is for is a ranked list of the countries to watch
  for each kind next year.

  For each kind and year-end origin t, the countries are ranked by the year
  node's probability of an onset in (t, t+2] (only the forecast made at t),
  and the list is its top k, k fixed in advance: 5, 10, 20 and 10% of the
  countries ranked (rounded up). Rows tied at the cutoff share the slots
  left (as augur.score/recall-parts), so the order rows come in never
  matters.
  - recall: the share of the window's onsets that were listed, pooled over
    origins within a kind, then the kinds weighed equally;
  - precision: listed onsets over slots, pooled the same way;
  - lead time: for an onset in year Y (caught when listed at origin Y-1 or
    Y-2, whose windows hold it), how many origins before Y the country
    entered the unbroken listing that caught it (capped at 10); a row is
    listed when its share of a slot is at least one half.
  Two samples, reported apart: all at-risk country-years, and the hard
  ones (no onset of the kind in the ten years to the origin, the project's
  definition: bnm.onset/hard-row? at the year), ranked among themselves:
  of the quiet countries, which to watch.

  Comparisons are paired, on the rows both sides forecast: a model's recall
  less another's, the interval from a bootstrap of blocks of origin years
  (two years, the window's length, so overlapping windows stay together),
  the kinds weighed equally in each draw. Against the run's bench, its
  placebo, and two naive lists from the catalogue's onsets known at the
  origin (onset month + the catalogue's known-lag at or before it):
  persistence (the most recent onset first) and climatology (the
  country's rate per covered year).

    jolt -A:tools -M -m bnm.watchlist --run t44-world --bench t44-world-norel
                                      [--placebo t44-world-placebo] [--from 2003 --to 2022]
                                      [--reps 1000] [--kinds a,b]
    jolt -A:tools -M -m bnm.watchlist --base t44-world-norel
    jolt -A:tools -M -m bnm.watchlist --power t44-world-norel [--betas 0,0.25,0.5,0.75,1] [--sims 100] [--kinds a,b]
    jolt -A:tools -M -m bnm.watchlist --record true

  --record runs the base table, every world trial on disk against its bench
  and placebo, and the power check, into
  data/runs/sequencing/watchlist-record.txt."
  (:require [augur.math :as m]
            [bnm.kinds :as kinds]
            [bnm.paired :as pd]
            [bnm.relation-diag :as rd]
            [bnm.warm-up :as wu]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- the list --------------------------------------------------------------------

(def ks "List lengths, fixed in advance; :p10 is 10% of the countries ranked." [5 10 20 :p10])

(defn k-for
  "Slots in a list of `k` over `n` rows: k (never more than n), or 10% of n
  rounded up for :p10."
  [k n]
  (if (= k :p10) (long (Math/ceil (* 0.1 n))) (min (long k) (long n))))

(defn top-k-shares
  "Each score's share of the top `k` slots, in the scores' order: 1 above
  the cutoff, 0 below, and for scores tied at it the slots left over their
  number."
  [scores k]
  (let [scores (vec scores)
        out (double-array (count scores))]
    (loop [[[_ idx] & more] (sort-by #(- (double (key %))) (group-by #(double (scores %)) (range (count scores))))
           left (double k)]
      (when (and idx (pos? left))
        (let [n (count idx) share (min 1.0 (/ left n))]
          (doseq [i idx] (aset out i share))
          (recur more (- left (* share n))))))
    (vec out)))

(defn cell-hits
  "{:hits :pos :slots} of one kind-origin's list of `k` by probabilities
  `ps` against outcomes `ys`."
  [ps ys k]
  (let [k (k-for k (count ps))
        s (top-k-shares ps k)]
    {:hits (reduce + 0.0 (map (fn [sh y] (if y sh 0.0)) s ys))
     :pos (count (filter identity ys))
     :slots k}))

(defn pooled-rates
  "Recall and precision of cells {kind {origin {:hits :pos :slots}}},
  pooled over origins within a kind, then the kinds weighed equally (a kind
  without onsets left out of recall)."
  [cells]
  (let [per (into {} (for [[k byt] cells
                           :let [h (reduce + 0.0 (map :hits (vals byt)))
                                 p (reduce + (map :pos (vals byt)))
                                 s (reduce + (map :slots (vals byt)))]]
                       [k {:recall (when (pos? p) (/ h p)) :precision (when (pos? s) (/ h s)) :pos p :hits h}]))
        mean (fn [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))]
    {:per-kind per
     :recall (mean (keep :recall (vals per)))
     :precision (mean (keep :precision (vals per)))}))

;; --- the paired bootstrap --------------------------------------------------------

(defn weighted-stat
  "The mean over kinds of each kind's weighted sum of numerators over its
  weighted sum of denominators, cells {kind {year [num den]}}, `w` {year
  weight}; a kind whose denominators draw no weight is left out."
  [cells w]
  (let [rs (keep (fn [[_ byy]]
                   (let [[n d] (reduce (fn [[n d] [y [a b]]] (let [wy (double (get w y 0))] [(+ n (* wy a)) (+ d (* wy b))]))
                                       [0.0 0.0] byy)]
                     (when (pos? d) (/ n d))))
                 cells)]
    (if (seq rs) (/ (reduce + rs) (double (count rs))) 0.0)))

(defn year-weights
  "{year draws}: `years` cut into consecutive blocks of `block`, as many
  blocks drawn with replacement as there are, each year weighted by its
  block's draws."
  [years block g]
  (let [blocks (vec (partition-all block (sort years)))
        nb (count blocks)
        counts (frequencies (repeatedly nb #(min (dec nb) (long (* nb (m/next-double! g))))))]
    (into {} (for [[i b] (map-indexed vector blocks) y b] [y (get counts i 0)]))))

(defn paired-boot
  "{:diff :lo :hi} of `weighted-stat` over cells {kind {year [num den]}}
  (num a paired difference), the 1 - alpha interval from :reps draws of
  blocks of :block years, from :seed."
  [cells years {:keys [reps seed alpha block]}]
  (let [g (m/rng seed)
        draws (vec (sort (repeatedly reps #(weighted-stat cells (year-weights years block g)))))
        at (fn [q] (nth draws (min (dec reps) (long (* q reps)))))]
    {:diff (weighted-stat cells (zipmap years (repeat 1))) :lo (at (/ alpha 2.0)) :hi (at (- 1.0 (/ alpha 2.0)))}))

;; --- history, hard rows and naive lists ------------------------------------------

(defn known
  "Onsets (months) known at origin `o`: onset month plus `lag` at or before it."
  [onsets lag o]
  (subseq onsets <= (- o lag)))

(defn persistence
  "The persistence list's score at origin `o`: minus the years since the last
  known onset (the most recent first), -1000 with none."
  [onsets lag o]
  (if-let [l (last (known onsets lag o))] (- (/ (- o l) 12.0)) -1000.0))

(defn climatology
  "The climatology list's score at origin `o`: known onsets per year covered
  since `from` (months)."
  [onsets from lag o]
  (/ (count (known onsets lag o)) (max 1.0 (/ (- o from) 12.0))))

(defn hard?
  "No onset of the kind in the ten years to origin `o`: months (o-120, o]."
  [onsets o]
  (empty? (subseq onsets > (- o 120) <= o)))

;; --- ranking, events and leads ---------------------------------------------------

(defn rank-rows
  "Rows [{:c :t :p {source p} :y}] with :share {[source k] share} added:
  each source's list of each k at each origin, from its probabilities
  alone."
  [rows sources lens]
  (vec (mapcat (fn [[_ rs]]
                 (let [rs (vec rs)
                       sh (into {} (for [s sources k lens]
                                     [[s k] (top-k-shares (map #(get-in % [:p s]) rs) (k-for k (count rs)))]))]
                   (map-indexed (fn [i r] (assoc r :share (update-vals sh #(% i)))) rs)))
               (sort-by key (group-by :t rows)))))

(defn events
  "[[country year]] of onsets (`onsets` {country months}) whose year Y has a
  positive row at origin Y-1 or Y-2 among `rows`."
  [rows onsets]
  (let [pos (set (for [r rows :when (:y r)] [(:c r) (:t r)]))]
    (sort (distinct (for [[c ms] onsets mo ms
                          :let [y (quot mo 12)]
                          :when (or (pos [c (dec y)]) (pos [c (- y 2)]))]
                      [c y])))))

(def max-lead 10)

(defn lead-time
  "Origins before year `y` the country entered the unbroken listing that
  caught its onset (`listed?` of an origin year), nil when not listed at
  y-1 or y-2; capped at `max-lead`."
  [listed? y]
  (when-let [c (cond (listed? (dec y)) (dec y) (listed? (- y 2)) (- y 2))]
    (loop [t c]
      (if (and (listed? (dec t)) (< (- y (dec t)) (inc max-lead)))
        (recur (dec t))
        (min max-lead (- y t))))))

(defn leads
  "Lead times of the caught `evs` for `source`'s list of `k`, listed meaning
  a share of a slot of at least one half."
  [rows evs source k]
  (let [on (set (for [r rows :when (>= (get-in r [:share [source k]] 0.0) 0.5)] [(:c r) (:t r)]))]
    (vec (keep (fn [[c y]] (lead-time #(on [c %]) y)) evs))))

;; --- reading runs ----------------------------------------------------------------

(def ^:private read-run
  (memoize (fn [run kind from to] (pd/read-lattice (str run "/" kind) "year" from to))))

(defn kind-rows
  "Rows {:c :t :p {source p} :y :hard} of one kind at year-end origins from
  `from` - max-lead to `to`, on the keys every run in `runs` ({source run})
  forecast and resolved, the outcome the first run's; :pers and :clim from
  the catalogue `cat`."
  [runs kind cat from to]
  (let [srcs (vec runs)
        ps (into {} (for [[s r] srcs] [s (read-run r kind (- from max-lead) to)]))
        [s0] (first srcs)
        lag (:known-lag cat 0)]
    (vec (for [[[c o] [_ y]] (ps s0)
               :when (every? #(get-in ps [% [c o]]) (map first srcs))
               :let [on (get-in cat [:onsets c] (sorted-set))
                     fr (get-in cat [:from c])]
               :when fr]
           {:c c :t (quot o 12) :y y :hard (hard? on o)
            :p (-> (into {} (for [[s] srcs] [s (first (get-in ps [s [c o]]))]))
                   (assoc :pers (persistence on lag o) :clim (climatology on fr lag o)))}))))

(defn- cells
  "{origin {:hits :pos :slots}} of ranked rows for `source`'s list of `k`."
  [ranked source k from to]
  (into {} (for [[t rs] (group-by :t ranked) :when (<= from t to)]
             [t {:hits (reduce + 0.0 (for [r rs :when (:y r)] (get-in r [:share [source k]])))
                 :pos (count (filter :y rs))
                 :slots (k-for k (count rs))}])))

(defn- diff-cells
  "{origin [hits-a - hits-b, positives]}."
  [ranked a b k from to]
  (let [ca (cells ranked a k from to) cb (cells ranked b k from to)]
    (into {} (for [[t {:keys [hits pos]}] ca] [t [(- hits (:hits (cb t))) pos]]))))

(defn sample-of [rows sample] (if (= sample :hard) (filterv :hard rows) rows))

;; --- comparisons ------------------------------------------------------------------

(defn compare-sources
  "For each sample (:hard, :all) and k: each source's recall, precision and
  mean lead (kinds equally), and for each pair [a b] in `pairs` a's recall
  less b's {:diff :lo :hi}. `by-kind` {kind {:rows :events}}."
  [by-kind sources pairs {:keys [from to] :as opts}]
  (let [years (range from (inc to))]
    (into {} (for [sample [:hard :all]
                   :let [ranked (into {} (for [[kd {:keys [rows]}] by-kind] [kd (rank-rows (sample-of rows sample) sources ks)]))
                         evs (into {} (for [[kd {:keys [onsets]}] by-kind]
                                        [kd (filter (fn [[_ y]] (<= (+ from 1) y (+ to 2))) (events (ranked kd) onsets))]))]
                   k ks]
               [[sample k]
                {:rates (into {} (for [s sources]
                                   [s (assoc (pooled-rates (into {} (for [[kd rs] ranked] [kd (cells rs s k from to)])))
                                             :lead (let [per (keep (fn [[kd rs]] (let [ls (leads rs (evs kd) s k)]
                                                                                   (when (seq ls) (/ (reduce + ls) (double (count ls))))))
                                                                   ranked)]
                                                     (when (seq per) (/ (reduce + per) (count per))))
                                             :caught (let [per (keep (fn [[kd rs]] (let [e (evs kd)]
                                                                                     (when (seq e) (/ (count (leads rs e s k)) (double (count e))))))
                                                                     ranked)]
                                                       (when (seq per) (/ (reduce + per) (count per)))))]))
                 :pairs (into {} (for [[a b] pairs]
                                   [[a b] (paired-boot (into {} (for [[kd rs] ranked] [kd (diff-cells rs a b k from to)]))
                                                       years opts)]))
                 :per-kind (into {} (for [[kd rs] ranked]
                                      [kd (into {} (for [s sources] [s (cells rs s k from to)]))]))}]))))

;; --- the power check ---------------------------------------------------------------

(defn simulate-rows
  "The base's rows with outcomes redrawn: a signal z ~ N(0,1) per row that
  only the model knows shifts the base's log odds by beta z (:m); the
  outcome follows the model when `informative?`, else the base (the test's
  size)."
  [rows beta g informative?]
  (mapv (fn [r]
          (let [pb (max 1e-6 (min (- 1.0 1e-6) (double (get-in r [:p :b]))))
                pm (m/expit (+ (m/logit pb) (* beta (m/normal! g))))]
            (-> r (assoc-in [:p :m] pm) (assoc :y (< (m/next-double! g) (if informative? pm pb))))))
        rows))

(defn mde
  "The mean recall gain at which the recall test's power first reaches
  `target`, interpolated linearly in beta between the signal rows of a
  `power` result; nil if it never does."
  [res target]
  (let [rs (vec (sort-by :beta (filter :signal res)))]
    (some (fn [[a b]]
            (when (and (< (:recall-power a) target) (>= (:recall-power b) target))
              (let [f (/ (- target (:recall-power a)) (- (:recall-power b) (:recall-power a)))]
                (+ (:gain a) (* f (- (:gain b) (:gain a)))))))
          (partition 2 1 rs))))

(defn- brier-cells [rows from to]
  (into {} (for [[t rs] (group-by :t rows) :when (<= from t to)]
             [t [(reduce + 0.0 (map #(- (pd/brier (get-in % [:p :b]) (:y %)) (pd/brier (get-in % [:p :m]) (:y %))) rs))
                 (count rs)]])))

(defn power
  "For each beta, over `sims` draws on the base's test-year rows (`by-kind`
  {kind rows} with :p {:b p}): the share finding the signal (lower end of
  the interval above 0) with top-10 recall on hard onsets and with Brier
  on the hard rows, under the signal and under the null, and the mean
  recall gain."
  [by-kind betas sims {:keys [from to seed] :as opts}]
  (let [years (range from (inc to))
        hard (update-vals by-kind (fn [rs] (filterv #(and (:hard %) (<= from (:t %) to)) rs)))]
    (vec (for [beta betas informative? [false true]
               :let [g (m/rng (+ seed (long (* 1000 beta)) (if informative? 7 0)))
                     res (vec (for [_ (range sims)]
                                (let [sim (update-vals hard #(simulate-rows % beta g informative?))
                                      ranked (update-vals sim #(rank-rows % [:m :b] [10]))
                                      r (paired-boot (update-vals ranked #(diff-cells % :m :b 10 from to)) years opts)
                                      b (paired-boot (update-vals sim #(brier-cells % from to)) years opts)]
                                  [(pos? (:lo r)) (pos? (:lo b)) (:diff r)])))
                     share (fn [i] (/ (count (filter #(nth % i) res)) (double sims)))]]
           {:beta beta :signal informative? :recall-power (share 0) :brier-power (share 1)
            :gain (/ (reduce + (map #(nth % 2) res)) (double sims))}))))

;; --- report ------------------------------------------------------------------------

(defn- fmtd [{:keys [diff lo hi]}] (format "%+.3f [%+.3f %+.3f]%s" diff lo hi (cond (pos? lo) " GAIN" (neg? hi) " LOSS" :else "")))
(defn- f3 [x] (if x (format "%.3f" (double x)) "  -  "))
(defn- f1 [x] (if x (format "%.1f" (double x)) " - "))
(defn- kname [k] (if (= k :p10) "10%" (str k)))

(defn- differing-kinds
  "The kinds whose year-node forecasts differ between two runs at the test
  origins."
  [run bench kds from to]
  (filter (fn [kd] (let [a (read-run run kd (- from max-lead) to) b (read-run bench kd (- from max-lead) to)]
                     (some (fn [[key [pa]]] (when-let [[pb] (b key)] (> (Math/abs (- (double pa) (double pb))) 1e-12))) a)))
          kds))

(defn- by-kind [runs kds cats from to]
  (into {} (for [kd kds] [kd {:rows (kind-rows runs kd (cats kd) from to) :onsets (get-in cats [kd :onsets])}])))

(defn- all-kinds [run]
  (sort (filter #(.exists (io/file (str "data/runs/" run "/" % "/forecasts.tsv"))) (keys kinds/tiers))))

(defn report-base
  "The base against persistence and climatology, every kind."
  [say run cats {:keys [from to] :as opts}]
  (let [kds (all-kinds run)
        res (compare-sources (by-kind {:m run} kds cats from to) [:m :pers :clim] [[:m :pers] [:m :clim]] opts)]
    (say (format "A. The base (%s) against persistence and climatology, %d kinds, origins %d-%d." run (count kds) from to))
    (say "   recall / precision / mean lead (origins) / share caught; differences in recall, 90% interval, years in blocks of 2.")
    (doseq [sample [:hard :all] k ks
            :let [{:keys [rates pairs]} (res [sample k])
                  rp (fn [s] (let [{:keys [recall precision lead caught]} (rates s)] (format "%s/%s/%s/%s" (f3 recall) (f3 precision) (f1 lead) (f3 caught))))]]
      (say (format "   %-4s k=%-3s base %s  pers %s  clim %s  base-pers %s  base-clim %s"
                   (name sample) (kname k) (rp :m) (rp :pers) (rp :clim) (fmtd (pairs [:m :pers])) (fmtd (pairs [:m :clim])))))
    (say "   per kind, hard rows, k=10: positive country-origins, recall base / pers / clim")
    (let [{:keys [per-kind]} (res [:hard 10])]
      (doseq [[kd by] (sort-by key per-kind)
              :let [r (fn [s] (let [c (vals (by s)) p (reduce + (map :pos c))] (when (pos? p) (/ (reduce + (map :hits c)) p))))]]
        (say (format "     %-22s %4d  %s  %s  %s" kd (reduce + (map :pos (vals (by :m)))) (f3 (r :m)) (f3 (r :pers)) (f3 (r :clim))))))
    res))

(defn report-pair
  "A run against another (its bench or its placebo), on the kinds where they
  differ."
  [say run other cats {:keys [from to kinds] :as opts}]
  (let [kds (or kinds (differing-kinds run other (all-kinds run) from to))]
    (if (empty? kds)
      (say (format "   %s against %s: identical forecasts, nothing to compare" run other))
      (let [res (compare-sources (by-kind {:m run :b other} kds cats from to) [:m :b] [[:m :b]] opts)]
        (say (format "   %s against %s, %d kinds (%s):" run other (count kds) (str/join "," kds)))
        (doseq [sample [:hard :all] k ks
                :let [{:keys [rates pairs]} (res [sample k])]]
          (say (format "     %-4s k=%-3s recall %s vs %s  diff %s  precision %s vs %s  lead %s vs %s"
                       (name sample) (kname k) (f3 (get-in rates [:m :recall])) (f3 (get-in rates [:b :recall]))
                       (fmtd (pairs [:m :b])) (f3 (get-in rates [:m :precision])) (f3 (get-in rates [:b :precision]))
                       (f1 (get-in rates [:m :lead])) (f1 (get-in rates [:b :lead])))))
        (let [{:keys [per-kind]} (res [:hard 10])]
          (say (str "     hard k=10 per kind (positive country-origins, recall run/other): "
                    (str/join "; " (for [[kd by] (sort-by key per-kind)
                                         :let [p (reduce + (map :pos (vals (by :m))))
                                               r (fn [s] (when (pos? p) (/ (reduce + (map :hits (vals (by s)))) p)))]]
                                     (format "%s %d %s/%s" kd p (f3 (r :m)) (f3 (r :b))))))))
        res))))

(def trials
  "The world trials on disk: [label run bench placebo]."
  [["trial 42" "t42-world" "t42-world-norel" nil]
   ["trial 43" "t43-world" "t42-world-norel" "t43-world-placebo"]
   ["trial 44" "t44-world" "t44-world-norel" "t44-world-placebo"]
   ["trial 45" "t45-world" "t44-world-norel" "t45-world-placebo"]
   ["trial 44 gauge fix (record)" "t44-world-norel" "t42-world-norel" nil]
   ["trial T8b (online recalibration)" "t44-world-norel-online-registered" "t44-world-norel" nil]
   ["t46 rebuild (regression check)" "t46-world-norel" "t44-world-norel" nil]])

(defn report-power [say run cats betas sims {:keys [from to kinds] :as opts}]
  (let [kds (or kinds (all-kinds run))
        ;; outcomes are drawn from the base: its warm-up rows (bnm.warm-up, p
        ;; near 0.5 before it has read an outcome) would draw coin flips
        rows (into {} (for [kd kds
                            :let [w (wu/warm-up-origins (rd/read-year run kd) (:known-lag (cats kd) 0))]]
                        [kd (vec (for [r (kind-rows {:m run} kd (cats kd) from to) :when (not (w (:t r)))]
                                   (assoc r :p {:b (get-in r [:p :m])})))]))
        res (power rows betas sims opts)]
    (say (format "C. Power on %s's hard rows, origins %d-%d, %d kinds: outcomes redrawn, a signal of log-odds size beta per row" run from to (count kds)))
    (say (format "   known only to the model; %d draws a size, %d bootstrap draws each. Share finding it (lower 90%% bound > 0):" sims (:reps opts)))
    (say "   beta  outcome  top-10 recall (hard)  Brier (hard rows)  mean recall gain")
    (doseq [{:keys [beta signal recall-power brier-power gain]} res]
      (say (format "   %.3f %-7s  %.2f                  %.2f               %+.3f" beta (if signal "signal" "null") recall-power brier-power gain)))
    (say (format "   minimum detectable recall gain at 80%% power: %s" (if-let [d (mde res 0.8)] (format "%+.3f" d) "not reached")))
    res))

(defn parse-opts [args]
  (let [o (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))]
    (assoc o
           :from (or (some-> (:from o) parse-long) 2003)
           :to (or (some-> (:to o) parse-long) 2022)
           :reps (or (some-> (:reps o) parse-long) 1000)
           :seed 1 :alpha 0.1 :block 2
           :sims (or (some-> (:sims o) parse-long) 100)
           :betas (if-let [b (:betas o)] (mapv parse-double (str/split b #",")) [0.0 0.25 0.375 0.5 0.75 1.0])
           :kinds (some-> (:kinds o) (str/split #",") seq vec))))

(defn -main [& args]
  (let [{:keys [run bench placebo base record betas sims] pw :power :as opts} (parse-opts args)
        cats (kinds/catalogues)
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out (str s "\n"))))
        hdr #(say "Watch-list rescoring (bnm.watchlist): a record, not a test. Top-k lists by the year node's probability of"
                  "an onset in (t, t+2], hard = no onset of the kind in the ten years to the origin, ranked among themselves.")]
    (cond
      record
      (do (hdr)
          (report-base say "t44-world-norel" cats opts)
          (say)
          (say "B. Trials rescored against their bench and placebo (the kinds where the forecasts differ).")
          (doseq [[label r b p] trials
                  :when (.exists (io/file (str "data/runs/" r)))]
            (say (str " " label))
            (report-pair say r b cats (dissoc opts :kinds))
            (when (and p (.exists (io/file (str "data/runs/" p)))) (report-pair say r p cats (dissoc opts :kinds))))
          (say)
          (report-power say "t44-world-norel" cats betas sims (assoc opts :reps 200 :kinds nil))
          (spit "data/runs/sequencing/watchlist-record.txt" (str out)))
      base (do (hdr) (report-base say base cats opts) (spit (str "data/runs/" base "/watchlist-base.txt") (str out)))
      pw (do (report-power say pw cats betas sims (assoc opts :reps (min (:reps opts) 200)))
             (spit (str "data/runs/" pw "/watchlist-power.txt") (str out)))
      :else (do (hdr)
                (report-pair say run bench cats opts)
                (when placebo (report-pair say run placebo cats opts))
                (spit (str "data/runs/" run "/watchlist-vs-" bench ".txt") (str out))))))
