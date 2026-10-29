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
    jolt -A:tools -M -m bnm.watchlist --v2 true --run fs-a-fix --bench fs-norel --nulls a,b,c [--kinds a,b]
    jolt -A:tools -M -m bnm.watchlist --calibrate sequencing/out.txt --bench fs-norel --nulls a,b,c

  --record runs the base table, every world trial on disk against its bench
  and placebo, and the power check, into
  data/runs/sequencing/watchlist-record.txt."
  (:require [augur.math :as m]
            [augur.special :as sp]
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

;; --- v2: against a null of same-shape groups (bead bnm-82j.6) ---------------------
;;
;; The 82j.1 rule's interval resamples origins with the two models fixed, so
;; it cannot see that any change at a node reshuffles the year node's top
;; ten: a group of nine noise inputs "gained" +0.014 [+0.005, +0.021]. v2
;; puts the candidate against R groups of its shape (the same inputs at the
;; same nodes) holding nothing: noise, or its own inputs shuffled across
;; countries or in time. The gain is the candidate's recall less the null
;; draws' mean, its interval a t prediction interval holding the draws'
;; spread (a fresh draw differs from the mean of R by sd0^2 (1 + 1/R)) and
;; the origins' bootstrap variance of the gain, R - 1 degrees of freedom.

(defn t-quantile
  "The `p` quantile of Student's t with `df` degrees of freedom (bisection
  on augur.special/t-cdf)."
  [p df]
  (loop [lo -60.0 hi 60.0 i 0]
    (let [mid (* 0.5 (+ lo hi))]
      (if (or (> i 100) (< (- hi lo) 1e-10))
        mid
        (if (< (sp/t-cdf mid (double df)) p) (recur mid hi (inc i)) (recur lo mid (inc i)))))))

(defn sd
  "The sample standard deviation of `xs` (n - 1)."
  [xs]
  (let [n (count xs) mu (/ (reduce + xs) (double n))]
    (Math/sqrt (/ (reduce + (map #(let [d (- % mu)] (* d d)) xs)) (dec n)))))

(defn null-test
  "The v2 decision for a candidate whose gain over the bench is `c`,
  against `nulls` (each null draw's gain over the same bench, the same
  rows), `vb` the origins' bootstrap variance of the candidate less the
  nulls' mean, the 1 - `alpha` two-sided interval (a pass when its lower
  end is above 0: one-sided alpha/2). nil with fewer than two draws."
  [c nulls vb alpha]
  (let [r (count nulls)]
    (when (>= r 2)
      (let [mu (/ (reduce + nulls) (double r))
            s0 (sd nulls)
            g (- c mu)
            se (Math/sqrt (+ (* s0 s0 (+ 1.0 (/ 1.0 r))) (double vb)))
            q (t-quantile (- 1.0 (/ alpha 2.0)) (dec r))
            lo (- g (* q se))]
        {:c c :mean0 mu :sd0 s0 :r r :g g :se se :df (dec r) :lo lo :hi (+ g (* q se))
         :p (if (pos? se) (- 1.0 (sp/t-cdf (/ g se) (double (dec r)))) (if (pos? g) 0.0 1.0))
         :pass (pos? lo)}))))

(defn- hits-cells
  "{kind {year [hits pos]}} of cells {kind {year {:hits :pos}}}, the hits
  `f` of the year's cells across sources."
  [cells-by-src f years]
  (let [{:keys [c]} cells-by-src]
    (into {} (for [[k byy] c]
               [k (into {} (for [y years :let [cell (byy y)] :when cell]
                             [y [(f k y) (:pos cell)]]))]))))

(defn null-gain
  "From cells {kind {origin {:hits :pos}}} of the candidate :c, the bench
  :b and the null draws :nulls (one list's cells each, the same rows):
  :g the candidate's recall less the nulls' mean, :cand and :nulls each
  less the bench, :vb the variance of g over :reps draws of blocks of
  origins."
  [{:keys [c b nulls] :as cs} years {:keys [reps seed block]}]
  (let [h (fn [cells k y] (get-in cells [k y :hits] 0.0))
        r (count nulls)
        gcells (hits-cells cs (fn [k y] (- (h c k y) (/ (reduce + (map #(h % k y) nulls)) (double r)))) years)
        less-b (fn [x] (weighted-stat (hits-cells cs (fn [k y] (- (h x k y) (h b k y))) years) (zipmap years (repeat 1))))
        g0 (m/rng seed)
        draws (vec (repeatedly reps #(weighted-stat gcells (year-weights years block g0))))
        mu (/ (reduce + draws) (double reps))]
    {:g (weighted-stat gcells (zipmap years (repeat 1)))
     :cand (less-b c)
     :nulls (mapv less-b nulls)
     :vb (/ (reduce + (map #(let [d (- % mu)] (* d d)) draws)) (double (max 1 (dec reps))))}))

(defn false-pass
  "The rule's size on null draws alone: each of `draws` (gains over the
  bench) in turn the candidate, against `r` of the others (every subset,
  or :subsets random ones when there are more), `vb-of` (fn [i others])
  its origins' variance against those others (against all of them it
  understates a subset's: the mean of fewer draws moves more over
  origins). {:rate :tests :passes}."
  [draws vb-of r alpha {:keys [subsets seed]}]
  (let [n (count draws)
        g (m/rng seed)
        choose (fn choose [xs k] (cond (zero? k) [[]] (empty? xs) [] :else (concat (map #(cons (first xs) %) (choose (rest xs) (dec k))) (choose (rest xs) k))))
        tests (for [i (range n)
                    :let [others (vec (remove #{i} (range n)))
                          all (when (<= (count others) 16) (choose others r))
                          sets (if (and all (<= (count all) subsets))
                                 all
                                 (repeatedly subsets #(take r (sort-by (fn [_] (m/next-double! g)) others))))]
                    s sets]
                (:pass (null-test (draws i) (mapv draws s) (vb-of i (vec s)) alpha)))
        tests (vec tests)]
    {:tests (count tests) :passes (count (filter true? tests))
     :rate (/ (count (filter true? tests)) (double (max 1 (count tests))))}))

(defn null-power
  "The null test's power at a true gain `gain` over the null mean, the null
  draws' spread `s` (sd0 with the origins' share folded in: a fresh draw's
  sd), `r` draws: simulated, the null mean and spread estimated from r
  normal draws each time."
  [gain s r alpha {:keys [sims seed]}]
  (let [g (m/rng seed)
        q (t-quantile (- 1.0 (/ alpha 2.0)) (dec r))
        hits (count (filter identity
                            (repeatedly sims
                                        (fn [] (let [ns (repeatedly r #(* s (m/normal! g)))
                                                     c (+ gain (* s (m/normal! g)))
                                                     mu (/ (reduce + ns) (double r))
                                                     se (* (sd ns) (Math/sqrt (+ 1.0 (/ 1.0 r))))]
                                                 (> (- c mu) (* q se)))))))]
    (/ hits (double sims))))

(defn null-mde
  "The smallest gain over the null mean the null test finds 80% of the
  time, in steps of s/20 (spread `s`, `r` draws)."
  [s r alpha opts]
  (some (fn [i] (let [gain (* i (/ s 20.0))] (when (>= (null-power gain s r alpha opts) 0.8) gain))) (range 0 400)))

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

(defn- source-cells
  "{source {kind {origin {:hits :pos :slots}}}} of the top-`k` lists on the
  hard rows every run in `runs` ({source run}) forecasts, origins
  `from`-`to`."
  [runs kds cats k from to]
  (let [ranked (into {} (for [kd kds] [kd (rank-rows (filterv :hard (kind-rows runs kd (cats kd) from to)) (keys runs) [k])]))]
    (into {} (for [s (keys runs)] [s (into {} (for [[kd rs] ranked] [kd (cells rs s k from to)]))]))))

(def harm-bound "No harm shown while the year nodes' Brier, the candidate less the null mean, has its lower end at or above this." -0.0003)

(defn harm-verdict
  "Harm from a null test on Brier gains over the bench (`null-test`'s
  {:lo :hi}): :harm when the candidate costs more than the null groups
  (the upper end below 0), :no-harm when the lower end is at or above
  `harm-bound`, else :not-shown."
  [{:keys [lo hi] :as t}]
  (cond (nil? t) :not-shown
        (neg? hi) :harm
        (>= lo harm-bound) :no-harm
        :else :not-shown))

(defn v2-verdict
  "The registered v2 decision from the gain's `null-test` (`g`), the
  harm's (`h`, Brier gains over the null mean) and the trial's detectable
  gain `mde`: a gain when the lower end is above 0 (2), equivalent to
  nothing when the upper end is below `mde` (3), the harm verdict (4),
  adopted on a gain that is not harm."
  [g h mde]
  (let [harm (harm-verdict h)
        gain (boolean (:pass g))]
    {:gain gain
     :equivalent (boolean (and g mde (< (:hi g) mde)))
     :harm harm
     :adopt (and gain (not= :harm harm))}))

(defn report-v2
  "The v2 rule (bead bnm-82j.6) for `run` against `bench` and the null
  draws `nulls` (runs of the same shape): the gain on the primary (top-10
  recall, hard onsets) over the nulls' mean, and the year nodes' Brier
  over the nulls' mean (harm)."
  [say run bench nulls cats {:keys [from to kinds alpha] :as opts}]
  (let [kds (or kinds (all-kinds run))
        years (range from (inc to))
        ns (vec (map-indexed (fn [i n] [(keyword (str "n" i)) n]) nulls))
        sc (source-cells (into {:c run :b bench} ns) kds cats 10 from to)
        ng (null-gain {:c (sc :c) :b (sc :b) :nulls (mapv #(sc (first %)) ns)} years opts)
        t (null-test (:cand ng) (:nulls ng) (:vb ng) alpha)
        brier (fn [r] (:pooled (pd/world-pair r bench kds "year" from to 400 alpha)))
        bc (brier run)
        bn (mapv #(:diff (brier %)) nulls)
        z (t-quantile 0.95 1e6)
        h (null-test (:diff bc) bn (Math/pow (/ (- (:hi bc) (:lo bc)) (* 2 z)) 2) alpha)
        ;; the trial's own spread: the draws' sd and their origins'
        ;; variance, each draw against the others (3)
        cs (mapv #(sc (first %)) ns)
        vb0 (/ (reduce + (for [i (range (count cs))]
                           (:vb (null-gain {:c (cs i) :b (sc :b) :nulls (vec (concat (subvec cs 0 i) (subvec cs (inc i))))} years opts))))
               (double (count cs)))
        spread (Math/sqrt (+ (* (:sd0 t) (:sd0 t)) vb0))
        mde (null-mde spread (count nulls) alpha {:sims 10000 :seed 1})
        v (v2-verdict t h mde)]
    (say (format "Watch-list rule v2: %s against %s and %d null draws (%s), %d kinds, origins %d-%d." run bench (count nulls) (str/join "," nulls) (count kds) from to))
    (say (format "   top-10 recall, hard onsets, less the bench: candidate %+.4f; null draws %s (mean %+.4f, sd %.4f)"
                 (:cand ng) (str/join " " (map #(format "%+.4f" %) (:nulls ng))) (:mean0 t) (:sd0 t)))
    (say (format "   gain over the null mean %+.4f [%+.4f %+.4f] (se %.4f: null sd x sqrt(1+1/R) and origins' sd %.4f; t, %d df), one-sided p %.3f: %s"
                 (:g t) (:lo t) (:hi t) (:se t) (Math/sqrt (:vb ng)) (:df t) (:p t) (if (:pass t) "GAIN" "no gain")))
    (say (format "   year nodes' Brier less the bench: candidate %+.5f [%+.5f %+.5f]; null draws %s"
                 (:diff bc) (:lo bc) (:hi bc) (str/join " " (map #(format "%+.5f" %) bn))))
    (say (format "   harm, Brier over the null mean %+.5f [%+.5f %+.5f]: %s"
                 (:g h) (:lo h) (:hi h) ({:harm "HARM (costs more than the null groups)" :no-harm "no harm (lower end within the bound)"
                                          :not-shown "no harm beyond the null groups' cost; no-harm not shown"} (harm-verdict h))))
    (say (format "   verdict: %s; detectable gain at 80%% %+.4f (spread %.4f)%s; year nodes' Brier against the bare bench %+.5f [%+.5f %+.5f]"
                 (if (:adopt v) "ADOPT (a gain, no harm beyond the null groups)" "not adopted")
                 mde spread (if (:equivalent v) ", equivalent to nothing" "") (:diff bc) (:lo bc) (:hi bc)))
    {:gain t :harm h :ng ng :verdict v :mde mde}))

(defn report-calibration
  "The v2 rule's size and power from null draws alone (`nulls`, one shape,
  one bench): each draw in turn the candidate against R of the others."
  [say bench nulls cats {:keys [from to kinds alpha] :as opts}]
  (let [kds (or kinds (all-kinds bench))
        years (range from (inc to))
        ns (vec (map-indexed (fn [i n] [(keyword (str "n" i)) n]) nulls))
        sc (source-cells (into {:b bench} ns) kds cats 10 from to)
        cs (mapv #(sc (first %)) ns)
        per (vec (for [i (range (count cs))]
                   (null-gain {:c (cs i) :b (sc :b) :nulls (vec (concat (subvec cs 0 i) (subvec cs (inc i))))} years opts)))
        draws (mapv :cand per)
        vb (mapv :vb per)
        s0 (sd draws)
        vbm (/ (reduce + vb) (count vb))
        s (Math/sqrt (+ (* s0 s0) vbm))]
    (say (format "Watch-list rule v2, calibration on %d null draws against %s, %d kinds, origins %d-%d." (count nulls) bench (count kds) from to))
    (doseq [[n d v] (map vector nulls draws vb)]
      (say (format "   %-22s recall less the bench %+.4f  (origins' sd of its gain over the others %.4f)" n d (Math/sqrt v))))
    (say (format "   between-draw sd %.4f, origins' sd %.4f, together %.4f; the 82j.1 rule passes %d of %d"
                 s0 (Math/sqrt vbm) s (count (filter #(pos? (:lo (paired-boot (into {} (for [[kd byt] (cs %)] [kd (into {} (for [[t {:keys [hits pos]}] byt] [t [(- hits (get-in sc [:b kd t :hits])) pos]]))])) years opts))) (range (count cs)))) (count cs)))
    (doseq [r (filter #(< % (count nulls)) [3 4 6 8 10 12])]
      (let [fp (false-pass draws (fn [i s] (:vb (null-gain {:c (cs i) :b (sc :b) :nulls (mapv cs s)} years opts))) r alpha {:subsets 300 :seed 1})
            f0 (false-pass draws (constantly 0.0) r alpha {:subsets 300 :seed 1})]
        (say (format "   R=%2d: false passes %d of %d (%.3f; without the origins' variance %.3f); detectable gain at 80%% (spread %.4f) %+.4f"
                     r (:passes fp) (:tests fp) (:rate fp) (:rate f0) s (null-mde s r alpha {:sims 10000 :seed 1})))))
    (doseq [r [16 24 32]]
      (say (format "   R=%2d (simulated only): detectable gain at 80%% %+.4f" r (null-mde s r alpha {:sims 10000 :seed 1}))))
    {:draws draws :vb vb :s s}))

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
  (let [{:keys [run bench placebo base record betas sims v2 calibrate nulls] pw :power :as opts} (parse-opts args)
        nulls (some-> nulls (str/split #","))
        cats (kinds/catalogues)
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out (str s "\n"))))
        hdr #(say "Watch-list rescoring (bnm.watchlist): a record, not a test. Top-k lists by the year node's probability of"
                  "an onset in (t, t+2], hard = no onset of the kind in the ten years to the origin, ranked among themselves.")]
    (cond
      v2 (do (report-v2 say run bench nulls cats opts)
             (spit (str "data/runs/" run "/watchlist-v2.txt") (str out)))
      calibrate (do (report-calibration say bench nulls cats opts)
                    (spit (str "data/runs/" calibrate) (str out)))
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
