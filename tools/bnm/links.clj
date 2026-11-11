(ns bnm.links
  "Links that cost nothing (bead bnm-56c.2, docs/PLAN.md 0.4): what a
  group with nothing in it costs the lattice, and under which weighting
  it costs nothing.

    jolt -A:tools -M -m bnm.links --runs a,b,c --bench b0 [--kinds k1,k2] [--from 2003 --to 2022]
    jolt -A:tools -M -m bnm.links --folds run --bench b0 [--kinds k1,k2]

  Prints, for each run against the bench, the paired Brier and log of the
  year and quarter-lead nodes on their own outcomes (bnm.paired/
  world-pair), pooled over the kinds weighed equally and per kind.

  --folds: the year nodes' paired Brier and log, `run` against the bench,
  in development folds of origin years grouped so that each of
  bnm.dev-folds' global episodes in 2003-2022 sits in one fold (the
  lattice's forecasts are online, so every fold is already scored from
  origins before it: rolling origin by construction)."
  (:require [bnm.kinds :as kinds]
            [bnm.paired :as pd]
            [clojure.string :as str]))

(defn- fmt [{:keys [diff lo hi]}] (format "%+.5f [%+.5f %+.5f]" diff lo hi))

(def folds
  "Development folds of origin years, 2003-2022: each global episode of
  bnm.dev-folds (gfc-2008, euro-arab-spring-2010, covid-2020,
  energy-food-2022) inside one fold."
  [[2003 2006] [2007 2009] [2010 2012] [2013 2019] [2020 2022]])

(defn fold-report
  "Lines: `run` against `bench` at the year nodes, in each fold and pooled."
  [run bench ks]
  (for [[a b] (conj folds [2003 2022])
        :let [{:keys [pooled]} (pd/world-pair run bench ks "year" a b 400 0.1)]]
    (format "   %d-%d  Brier %s  log %s%s" a b (fmt pooled) (fmt (:log pooled))
            (if (neg? (:hi pooled)) "  LOSS" ""))))

(defn -main [& args]
  (let [{:keys [runs bench kinds from to scales] fold-run :folds} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        from (or (some-> from parse-long) 2003)
        to (or (some-> to parse-long) 2022)]
    (when fold-run
      (println fold-run "against" bench "at the year nodes, by development fold:")
      (doseq [l (fold-report fold-run bench ks)] (println l)))
    (doseq [run (when runs (str/split runs #","))
            scale (str/split (or scales "year,quarter-lead") #",")
            :let [{:keys [pooled per-kind]} (pd/world-pair run bench ks scale from to 400 0.1)]]
      (println (format "%-28s %-12s Brier %s  log %s" run scale (fmt pooled) (fmt (:log pooled))))
      (println "   " (str/join "  " (for [[k {:keys [brier]}] (sort-by key per-kind)] (format "%s %+.5f" k (:diff brier))))))))
