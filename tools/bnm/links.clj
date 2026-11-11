(ns bnm.links
  "Links that cost nothing (bead bnm-56c.2, docs/PLAN.md 0.4): what a
  group with nothing in it costs the lattice, and under which weighting
  it costs nothing.

    jolt -A:tools -M -m bnm.links --runs a,b,c --bench b0 [--kinds k1,k2] [--from 2003 --to 2022]
    jolt -A:tools -M -m bnm.links --folds run --bench b0 [--kinds k1,k2]
    jolt -A:tools -M -m bnm.links --noise n1,n2,n3,n4 --bench b0 [--kinds k1,k2]

  Prints, for each run against the bench, the paired Brier and log of the
  year and quarter-lead nodes on their own outcomes (bnm.paired/
  world-pair), pooled over the kinds weighed equally and per kind.

  --folds: the year nodes' paired Brier and log, `run` against the bench,
  in development folds of origin years grouped so that each of
  bnm.dev-folds' global episodes in 2003-2022 sits in one fold (the
  lattice's forecasts are online, so every fold is already scored from
  origins before it: rolling origin by construction).

  --noise: the acceptance rule of bnm-56c.2, fixed before the candidate's
  full runs: each noise draw's year-node Brier against the bench (the
  same weighting without the group), 2003-2022, thirteen kinds; accepted
  when their mean is within 0.0003 of zero. The interval beside it is the
  watch-list rule v2's for a fresh draw (amendment A3)."
  (:require [bnm.kinds :as kinds]
            [bnm.paired :as pd]
            [bnm.watchlist :as wl]
            [clojure.string :as str]))

(defn- fmt [{:keys [diff lo hi]}] (format "%+.5f [%+.5f %+.5f]" diff lo hi))

(def folds
  "Development folds of origin years, 2003-2022: each global episode of
  bnm.dev-folds (gfc-2008, euro-arab-spring-2010, covid-2020,
  energy-food-2022) inside one fold."
  [[2003 2006] [2007 2009] [2010 2012] [2013 2019] [2020 2022]])

(def tolerance "The acceptance tolerance on the mean noise cost." 0.0003)

(defn noise-acceptance
  "{:mean :sd :lo :hi :accept} of `costs`, each noise draw's year-node
  Brier against its bench: the mean, the draws' spread, the 90% interval
  for a fresh draw (mean +/- t(0.95, R - 1) sd sqrt(1 + 1/R), the v2
  rule's form after A3), accepted when the mean is within `tolerance` of
  zero."
  [costs]
  (let [r (count costs)
        mu (/ (reduce + costs) (double r))
        s (wl/sd costs)
        half (* (wl/t-quantile 0.95 (dec r)) s (Math/sqrt (+ 1.0 (/ 1.0 r))))]
    {:mean mu :sd s :lo (- mu half) :hi (+ mu half) :accept (<= (Math/abs mu) tolerance)}))

(defn fold-report
  "Lines: `run` against `bench` at the year nodes, in each fold and pooled."
  [run bench ks]
  (for [[a b] (conj folds [2003 2022])
        :let [{:keys [pooled]} (pd/world-pair run bench ks "year" a b 400 0.1)]]
    (format "   %d-%d  Brier %s  log %s%s" a b (fmt pooled) (fmt (:log pooled))
            (if (neg? (:hi pooled)) "  LOSS" ""))))

(defn -main [& args]
  (let [{:keys [runs bench kinds from to scales noise] fold-run :folds} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        from (or (some-> from parse-long) 2003)
        to (or (some-> to parse-long) 2022)]
    (when noise
      (let [costs (vec (for [n (str/split noise #",")]
                         (let [{:keys [pooled]} (pd/world-pair n bench ks "year" from to 400 0.1)]
                           (println (format "   %-24s Brier %s  log %s" n (fmt pooled) (fmt (:log pooled))))
                           (:diff pooled))))
            {:keys [mean sd lo hi accept]} (noise-acceptance costs)]
        (println (format "noise cost against %s, %d draws: mean %+.5f, sd %.5f, fresh draw 90%% [%+.5f %+.5f]: %s"
                         bench (count costs) mean sd lo hi
                         (if accept (format "within %.4f of zero, accepted" tolerance) (format "not within %.4f of zero" tolerance))))))
    (when fold-run
      (println fold-run "against" bench "at the year nodes, by development fold:")
      (doseq [l (fold-report fold-run bench ks)] (println l)))
    (doseq [run (when runs (str/split runs #","))
            scale (str/split (or scales "year,quarter-lead") #",")
            :let [{:keys [pooled per-kind]} (pd/world-pair run bench ks scale from to 400 0.1)]]
      (println (format "%-28s %-12s Brier %s  log %s" run scale (fmt pooled) (fmt (:log pooled))))
      (println "   " (str/join "  " (for [[k {:keys [brier]}] (sort-by key per-kind)] (format "%s %+.5f" k (:diff brier))))))))
