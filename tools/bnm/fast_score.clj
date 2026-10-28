(ns bnm.fast-score
  "The registered tests of the fast-signal trials (bead bnm-82j.2,
  docs/PHASE-CHANGES.md): a run with one family at the quarter nodes
  (bnm.fast-run) against the base without it.

  1. (primary) the watch-list rule (bnm.watchlist): top-10 recall on hard
     onsets of the year node's forecasts, the run less the bench, the
     thirteen kinds weighed equally, its 90% interval from 1,000 draws of
     two-year blocks of origins, and the one-sided p (the share of draws at
     or below zero) for Holm across the families;
  2. the ledger's metrics: the watch list at every k, all rows and hard,
     precision and lead, against the bench and the placebo; the run and the
     bench against persistence and climatology; paired Brier and log of
     the year and quarter nodes on their own outcomes (bnm.paired/
     world-pair), against the bench and the placebo.
  The design years (origins 1980-2002) are reported first, for the
  record; the test is origins `--from` to `--to`.

    jolt -A:tools -M -m bnm.fast-score --run fs-a --bench fs-norel --placebo fs-a-placebo
                                       [--from 2003 --to 2022] [--design-from 1980 --design-to 2002]
    jolt -A:tools -M -m bnm.fast-score --holm 0.01,0.2,0.04

  Writes data/runs/<run>/fast-score.txt."
  (:require [augur.math :as m]
            [bnm.kinds :as kinds]
            [bnm.paired :as pd]
            [bnm.watchlist :as wl]
            [clojure.string :as str]))

(defn boot
  "{:diff :lo :hi :p} of `cells` ({kind {year [num den]}}) as
  bnm.watchlist/paired-boot draws them (the same interval), with p the
  share of draws at or below zero."
  [cells years {:keys [reps seed alpha block]}]
  (let [g (m/rng seed)
        draws (vec (sort (repeatedly reps #(wl/weighted-stat cells (wl/year-weights years block g)))))
        at (fn [q] (nth draws (min (dec reps) (long (* q reps)))))]
    {:diff (wl/weighted-stat cells (zipmap years (repeat 1)))
     :lo (at (/ alpha 2.0)) :hi (at (- 1.0 (/ alpha 2.0)))
     :p (/ (count (filter #(<= % 0.0) draws)) (double reps))}))

(defn primary
  "Top-10 recall on hard onsets, `run` less `bench`, over `kds`, origins
  `from`-`to`."
  [run bench kds cats {:keys [from to] :as opts}]
  (let [ranked (into {} (for [kd kds]
                          [kd (wl/rank-rows (filterv :hard (wl/kind-rows {:m run :b bench} kd (cats kd) from to)) [:m :b] [10])]))
        cells (into {} (for [[kd rs] ranked] [kd (#'wl/diff-cells rs :m :b 10 from to)]))
        rates (fn [s] (:recall (wl/pooled-rates (into {} (for [[kd rs] ranked] [kd (#'wl/cells rs s 10 from to)])))))]
    (assoc (boot cells (range from (inc to)) opts) :run (rates :m) :bench (rates :b))))

(defn holm
  "Holm's step-down at level `alpha` over {name p}: {name reject?}."
  [ps alpha]
  (let [sorted (sort-by val ps)
        k (count sorted)]
    (loop [[[n p] & more] sorted i 0 out {}]
      (if-not n
        out
        (if (<= p (/ alpha (- k i)))
          (recur more (inc i) (assoc out n true))
          (into out (for [[n] (cons [n p] more)] [n false])))))))

(defn- fmt [{:keys [diff lo hi]}] (format "%+.5f [%+.5f %+.5f]" diff lo hi))

(defn -main [& args]
  (let [{:keys [run bench placebo holm] :as o} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))]
    (if holm
      (let [ps (into {} (map-indexed (fn [i p] [(inc i) (parse-double p)]) (str/split holm #",")))]
        (doseq [[i r] (sort-by key (bnm.fast-score/holm ps 0.05))]
          (println (format "family %d p %.4f %s" i (ps i) (if r "rejects (gain)" "does not reject")))))
      (let [opts (wl/parse-opts args)
            from (:from opts) to (:to opts)
            dfrom (or (some-> (:design-from o) parse-long) 1980)
            dto (or (some-> (:design-to o) parse-long) 2002)
            cats (kinds/catalogues)
            kds (sort (keys kinds/tiers))
            out (StringBuilder.)
            say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out (str s "\n"))))
            prim (fn [label a b f t]
                   (let [{:keys [diff lo hi p] r :run bb :bench} (primary a b kds cats (assoc opts :from f :to t))]
                     (say (format "   %s, origins %d-%d: recall %.3f vs %.3f, diff %+.3f [%+.3f %+.3f], one-sided p %.3f%s"
                                  label f t r bb diff lo hi p (cond (pos? lo) " GAIN" (< hi 0.03) " (equivalent to nothing)" :else "")))))]
        (say (format "Fast-signal trial: %s against %s (placebo %s), all thirteen kinds." run bench (or placebo "none")))
        (say "Design years, for the record (nothing is chosen on them):")
        (prim "top-10 recall, hard onsets, run - bench" run bench dfrom dto)
        (say "1. Primary:")
        (prim "top-10 recall, hard onsets, run - bench" run bench from to)
        (when placebo (prim "the same, run - placebo" run placebo from to))
        (say "2. The ledger's metrics.")
        (wl/report-pair say run bench cats (assoc opts :kinds kds))
        (when placebo (wl/report-pair say run placebo cats (assoc opts :kinds kds)))
        (say)
        (wl/report-base say run cats opts)
        (say)
        (doseq [[label other] (cond-> [["bench" bench]] placebo (conj ["placebo" placebo]))
                scale ["year" "quarter"]
                :let [{:keys [pooled per-kind]} (pd/world-pair run other kds scale from to 400 0.1)]]
          (say (format "   %s against %s, %s nodes on their own outcomes, %d-%d, pooled  Brier %s  log %s"
                       run label scale from to (fmt pooled) (fmt (:log pooled))))
          (doseq [[k {:keys [brier n pos]}] (sort-by key per-kind)]
            (say (format "     %-22s n=%6d pos=%4d  Brier %s%s" k n pos (fmt brier) (cond (pos? (:lo brier)) " GAIN" (neg? (:hi brier)) " LOSS" :else "")))))
        (spit (str "data/runs/" run "/fast-score.txt") (str out))))))
