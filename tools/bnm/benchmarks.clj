(ns bnm.benchmarks
  "Benchmarks every forecast is scored against (docs/PLAN.md 0.3), beside
  climatology and persistence, each a probability from what was known at
  the origin:

  - :last12, the last 12 months of the same place. The ViEWS benchmark
    (the last 12 months of the same country), for onsets: whether the
    place had an onset of the kind in the last 12 months published at the
    origin, (t - lag - 12, t - lag] with lag the catalogue's known-lag,
    made a probability by the level's past rate among rows in the same
    state. The literal 12 months to the origin are never usable: no onset
    in them is known yet when the lag is a year or more, and a scored row
    is at risk only when no crisis was under way in them, so on the rows
    scored they are always quiet (checked on t48-world-norel's year
    nodes, every kind, origins 1960-2022: 0 of 97,188 rows).
  - :share, the parent's probability times the place's long-run share:
    the parent node's forecast (the base's own, at the same scale and
    origin) times the place's share of the onsets of the parent's members
    known at the origin (an equal share when none is known). Written for
    sub-national places, where the parent is the country; at the country
    level the parent is the subregion.
  - :clim, the place's own past rate at the same node, smoothed with one
    pseudo-row at the level's past rate.

  A row's outcome is known `ends` + lag months after its origin (ends the
  window's offset plus horizon), and only rows known by the origin are
  read.

    jolt -A:tools -M -m bnm.benchmarks --run t48-world-norel [--from 2003 --to 2022] [--reps 400]

  prints the base's Brier and log skill against each benchmark by kind and
  scale (a development figure: those years are development data) and
  writes it to data/runs/<run>/benchmarks-dev.txt."
  (:require [augur.score :as sc]
            [bnm.geo :as geo]
            [bnm.kinds :as kinds]
            [bnm.lattice-banking :as lb]
            [bnm.paired :as pd]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def brier pd/brier)
(def log-loss pd/log-loss)

(def ends
  "Months from a scale's origin to its window's end."
  (into {} (for [{:keys [name offset horizon]} lb/scales] [name (+ offset horizon)])))

;; --- the pieces ------------------------------------------------------------------

(defn published-12
  "[lo hi]: the window (lo, hi] of the last 12 months whose onsets are
  known at origin `t`."
  [{:keys [known-lag]} t]
  (let [hi (- t (or known-lag 0))] [(- hi 12) hi]))

(defn last-12?
  "Whether `place` had an onset in the last 12 months published at `t`."
  [cat place t]
  (let [[lo hi] (published-12 cat t)]
    (boolean (seq (subseq (get-in cat [:onsets place] (sorted-set)) > lo <= hi)))))

(defn ten-years?
  "Whether `place` had an onset in the ten years to `t` known at `t`:
  (t - 120, t - lag], the persistence baseline's state (the forward
  ledger's amendment L1; the watch list's hard rows are those without)."
  [cat place t]
  (let [lag (or (:known-lag cat) 0)]
    (boolean (seq (subseq (get-in cat [:onsets place] (sorted-set)) > (- t 120) <= (- t lag))))))

(defn past-counts
  "(fn [key t]) -> [onsets rows] of `rows` ({:t :y}) under `key-fn` whose
  outcome is known by t: :t + `after` <= t, :y true or false."
  [rows key-fn after]
  (let [by (update-vals (group-by key-fn (filter #(boolean? (:y %)) rows))
                        (fn [rs]
                          (let [sorted (sort-by :t rs)]
                            (loop [[r & more] sorted k 0 n 0 acc (sorted-map)]
                              (if-not r
                                acc
                                (let [k (+ k (if (:y r) 1 0)) n (inc n)]
                                  (recur more k n (assoc acc (+ (:t r) after) [k n]))))))))]
    (fn [key t]
      (or (some-> (get by key) (rsubseq <= t) first val) [0 0]))))

(defn smoothed
  "(k + prior) / (n + 1): a rate with one pseudo-row at `prior`."
  [k n prior]
  (/ (+ k (double prior)) (+ n 1.0)))

(defn long-run-share
  "`place`'s share of the onsets of `members` known at `t`; with none
  known, an equal share."
  [{:keys [onsets known-lag]} members place t]
  (let [cut (- t (or known-lag 0))
        n-of (fn [c] (count (subseq (get onsets c (sorted-set)) <= cut)))
        total (reduce + (map n-of members))]
    (if (pos? total)
      (/ (n-of place) (double total))
      (/ 1.0 (max 1 (count members))))))

(defn with-benchmarks
  "`rows` ({:place :t :y}, one kind, scale and level) with :clim, :last12,
  :share, :rate and :persist added, each nil where nothing it needs is
  known yet. :rate is the level's observed rate (a constant across
  places); :persist that rate among rows in the same ten-year state
  (`ten-years?`), smoothed as :last12 is. opts: :cat the kind's
  catalogue, :ends the scale's months to its window's end, :parent (fn
  [place]), :members {parent [places]}, :parent-p (fn [parent t]) the
  parent node's forecast."
  [rows {:keys [cat ends parent members parent-p]}]
  (let [after (+ ends (or (:known-lag cat) 0))
        rows (mapv #(assoc % :flag (last-12? cat (:place %) (:t %)) :ten (ten-years? cat (:place %) (:t %))) rows)
        level (past-counts rows (constantly :all) after)
        own (past-counts rows :place after)
        state (past-counts rows :flag after)
        ten (past-counts rows :ten after)]
    (mapv (fn [{:keys [place t flag] :as r}]
            (let [[lk ln] (level :all t)
                  rate (when (pos? ln) (/ lk (double ln)))
                  pp (when parent-p (some-> (parent place) (parent-p t)))]
              (-> (dissoc r :ten)
                  (assoc :clim (when rate (let [[k n] (own place t)] (smoothed k n rate)))
                         :last12 (when rate (let [[k n] (state flag t)] (smoothed k n rate)))
                         :share (when pp (* (double pp) (long-run-share cat (get members (parent place) []) place t)))
                         :rate rate
                         :persist (when rate (let [[k n] (ten (:ten r) t)] (smoothed k n rate)))))))
          rows)))

(defn skill
  "1 - mean loss of `a` over mean loss of `b` on `rows`, both keys
  present: above 0 when `a` is better."
  [rows a b loss]
  (let [rs (filter #(and (some? (a %)) (some? (b %))) rows)
        la (reduce + (map #(loss (double (a %)) (:y %)) rs))
        lb (reduce + (map #(loss (double (b %)) (:y %)) rs))]
    (when (pos? lb) (- 1.0 (/ la lb)))))

;; --- a run's rows ----------------------------------------------------------------

(defn read-rows
  "{[scale place] [{:t :p :y}]} of a lattice run's forecasts at the
  scale's own origins (quarter ends, year ends), every place; :y nil when
  unresolved or not at risk."
  [dir]
  (with-open [r (io/reader (str dir "/forecasts.tsv"))]
    (reduce (fn [acc line]
              (let [[s place o p y] (str/split line #"\t")
                    scale (keyword s)
                    o (parse-long o)]
                (update acc [scale place] (fnil conj []) {:place place :t o :p (parse-double p)
                                                          :y (case y "true" true "false" false nil)})))
            {} (line-seq r))))

(defn country-parents
  "{country subregion} and {subregion [countries]} for `countries`."
  [countries]
  (let [par (into {} (for [c countries :let [s (first (geo/parents c))] :when s] [c s]))]
    [par (update-vals (group-by par (keys par)) vec)]))

(defn kind-benchmarks
  "{scale [rows]}: a run's country rows of one kind with the benchmarks."
  [dir cat]
  (let [by (read-rows dir)
        countries (distinct (for [[[_ p]] by :when (= 3 (count p))] p))
        [par members] (country-parents countries)
        p-at (into {} (for [[[s place] rs] by :when (not= 3 (count place)) r rs] [[s place (:t r)] (:p r)]))]
    (into {} (for [scale (distinct (map first (keys by)))]
               [scale (with-benchmarks (vec (mapcat (fn [c] (get by [scale c])) countries))
                        {:cat cat :ends (ends scale) :parent par :members members
                         :parent-p (fn [place t] (p-at [scale place t]))})]))))

;; --- the development report ------------------------------------------------------

(def benchmarks [:clim :last12 :share])

(defn- year-end? [scale t] (if (#{:quarter-lead :quarter-w2} scale) (= 2 (mod t 3)) (= 11 (mod t 12))))

(defn report
  "The base's skill against each benchmark by kind and scale on rows with
  origins in years `from`-`to`, resolved and every benchmark known."
  [say run cats {:keys [from to reps]}]
  (let [ks (sort (filter #(.exists (io/file (str "data/runs/" run "/" % "/forecasts.tsv"))) (keys kinds/tiers)))
        all (into {} (pmap (fn [k] [k (kind-benchmarks (str "data/runs/" run "/" k) (cats k))]) ks))
        rows-of (fn [k scale] (filterv #(and (boolean? (:y %)) (year-end? scale (:t %)) (<= from (quot (:t %) 12) to)
                                             (every? (fn [b] (some? (b %))) benchmarks))
                                       (get-in all [k scale])))
        fmt (fn [x] (if x (format "%+.3f" x) "   -  "))
        ival (fn [rs b loss] (let [{:keys [diff lo hi]} (sc/paired-loss-2way (for [r rs] [(quot (:t r) 12) (:p r) (b r) (:y r) (:place r)])
                                                                            loss {:reps reps :seed 1 :alpha 0.1 :per-block 1})]
                               (format "%+.5f [%+.5f %+.5f]" diff lo hi)))]
    (say (format "Base %s against the benchmarks, country rows, origins %d-%d (development data, not a test)." run from to))
    (say "Skill = 1 - loss(base) / loss(benchmark); above 0 the base is better. Brier, then log (floor 1e-4).")
    (say "Benchmarks: clim (the place's past rate), last12 (the last 12 months published at the origin),")
    (say "share (the subregion's forecast times the country's long-run share of its known onsets).")
    (doseq [scale [:year :quarter-lead :quarter-w2 :half-decade]]
      (say)
      (say (format "%s  %-20s %6s %5s  %-23s %-23s  per-row Brier gain on last12, 90%%" (name scale) "kind" "n" "pos" "Brier clim/last12/share" "log clim/last12/share"))
      (doseq [k ks :let [rs (rows-of k scale)] :when (seq rs)]
        (say (format "   %-25s %6d %5d  %s %s %s  %s %s %s  %s" k (count rs) (count (filter :y rs))
                     (fmt (skill rs :p :clim brier)) (fmt (skill rs :p :last12 brier)) (fmt (skill rs :p :share brier))
                     (fmt (skill rs :p :clim log-loss)) (fmt (skill rs :p :last12 log-loss)) (fmt (skill rs :p :share log-loss))
                     (ival rs :last12 brier)))))
    all))

(defn -main [& args]
  (let [{:keys [run from to reps]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        run (or run "t48-world-norel")
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (.append out (str s "\n"))))]
    (report say run (kinds/catalogues) {:from (or (some-> from parse-long) 2003) :to (or (some-> to parse-long) 2022)
                                        :reps (or (some-> reps parse-long) 400)})
    (spit (str "data/runs/" run "/benchmarks-dev.txt") (str out))))
