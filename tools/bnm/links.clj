(ns bnm.links
  "Links that cost nothing (bead bnm-56c.2, docs/PLAN.md 0.4): what a
  group with nothing in it costs the lattice, and under which weighting
  it costs nothing.

    jolt -A:tools -M -m bnm.links --runs a,b,c --bench b0 [--kinds k1,k2] [--from 2003 --to 2022]

  Prints, for each run against the bench, the paired Brier and log of the
  year and quarter-lead nodes on their own outcomes (bnm.paired/
  world-pair), pooled over the kinds weighed equally and per kind."
  (:require [bnm.kinds :as kinds]
            [bnm.paired :as pd]
            [clojure.string :as str]))

(defn- fmt [{:keys [diff lo hi]}] (format "%+.5f [%+.5f %+.5f]" diff lo hi))

(defn -main [& args]
  (let [{:keys [runs bench kinds from to scales]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        from (or (some-> from parse-long) 2003)
        to (or (some-> to parse-long) 2022)]
    (doseq [run (str/split runs #",")
            scale (str/split (or scales "year,quarter-lead") #",")
            :let [{:keys [pooled per-kind]} (pd/world-pair run bench ks scale from to 400 0.1)]]
      (println (format "%-28s %-12s Brier %s  log %s" run scale (fmt pooled) (fmt (:log pooled))))
      (println "   " (str/join "  " (for [[k {:keys [brier]}] (sort-by key per-kind)] (format "%s %+.5f" k (:diff brier))))))))
