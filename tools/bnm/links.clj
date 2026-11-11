(ns bnm.links
  "Links that cost nothing (bead bnm-56c.2, docs/PLAN.md 0.4): what a
  group with nothing in it costs the lattice, and under which weighting
  it costs nothing.

    jolt -A:tools -M -m bnm.links --runs a,b,c --bench b0 [--kinds k1,k2] [--from 2003 --to 2022]
    jolt -A:tools -M -m bnm.links --folds run --bench b0 [--kinds k1,k2]
    jolt -A:tools -M -m bnm.links --noise n1,n2,n3,n4 --bench b0 [--kinds k1,k2]
    jolt -A:tools -M -m bnm.links --weights a,b [--kinds k1,k2] [--from 2003 --to 2022]

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
            [clojure.java.io :as io]
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

(defn entropy
  "The weights' entropy over the log of their number: 1 even, 0 on one."
  [ws]
  (let [vs (filter pos? (map double (vals ws))) z (reduce + 0.0 vs) n (count ws)]
    (if (< n 2) 0.0 (/ (- (reduce + 0.0 (map #(let [p (/ % z)] (* p (Math/log p))) vs))) (Math/log n)))))

(defn- ranks [xs]
  ;; average ranks, ties shared
  (let [sorted (sort-by second (map-indexed vector xs))
        groups (partition-by second sorted)]
    (loop [gs groups at 0 out {}]
      (if-let [g (first gs)]
        (let [r (+ at (/ (dec (count g)) 2.0))]
          (recur (rest gs) (+ at (count g)) (into out (for [[i _] g] [i r]))))
        (mapv out (range (count xs)))))))

(defn rank-agreement
  "Spearman's correlation of two weightings over the groups of the first."
  [a b]
  (let [ks (sort-by str (keys a))
        ra (ranks (map #(double (a % 0.0)) ks)) rb (ranks (map #(double (b % 0.0)) ks))
        n (count ks) ma (/ (reduce + ra) n) mb (/ (reduce + rb) n)
        cov (reduce + (map #(* (- %1 ma) (- %2 mb)) ra rb))
        sa (Math/sqrt (reduce + (map #(let [d (- % ma)] (* d d)) ra)))
        sb (Math/sqrt (reduce + (map #(let [d (- % mb)] (* d d)) rb)))]
    (if (and (pos? sa) (pos? sb)) (/ cov (* sa sb)) 0.0)))

(defn node-weights
  "A node's weights as recorded; under :group-shrink, its groups' scales."
  [ws]
  (let [sc (into {} (filter #(= "scale" (namespace (key %))) ws))]
    (if (seq sc) sc ws)))

(defn dynamics
  "{:entropy :last :top-changes :agreement} of [[year weights] ...]."
  [series]
  (let [ws (map second series)
        tops (map #(key (apply max-key val %)) ws)]
    {:entropy (/ (reduce + (map entropy ws)) (count ws))
     :last (entropy (last ws))
     :top-changes (count (filter (fn [[a b]] (not= a b)) (partition 2 1 tops)))
     :agreement (if (next ws) (/ (reduce + (map (fn [[a b]] (rank-agreement a b)) (partition 2 1 ws))) (dec (count ws))) 1.0)}))

(defn read-weights
  "{[scale level] (sorted-map year {group weight})} of a kind's weights.tsv."
  [file]
  (with-open [r (io/reader file)]
    (reduce (fn [acc line]
              (let [[s l y g w] (str/split line #"\t")
                    g (if (str/includes? g "/") (keyword (first (str/split g #"/")) (second (str/split g #"/"))) (keyword g))]
                (update acc [s l] (fnil assoc-in (sorted-map)) [(parse-long y) g] (parse-double w))))
            {} (doall (line-seq r)))))

(defn weights-report
  "Lines: each run's weight dynamics by kind at the country nodes."
  [runs ks from to]
  (for [run runs k ks
        :let [w (read-weights (str "data/runs/" run "/" k "/weights.tsv"))]
        scale ["year" "quarter-lead"]
        :let [series (vec (for [[y ws] (get w [scale "country"]) :when (<= from y to)] [y (node-weights ws)]))]
        :when (seq series)
        :let [{:keys [entropy last top-changes agreement]} (dynamics series)
              fast (seq (keep #(some (fn [[g v]] (when (= "fast" (namespace g)) v)) (second %)) series))
              top (key (apply max-key val (second (peek series))))]]
    (format "%-22s %-18s %-12s entropy %.2f (last %.2f)  top changes %2d  rank agreement %+.2f  top %s%s"
            run k scale entropy last top-changes agreement (name top)
            (if fast (format "  fast group %.3f" (/ (reduce + fast) (count fast))) ""))))

(defn -main [& args]
  (let [{:keys [runs bench kinds from to scales noise] fold-run :folds weight-runs :weights} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
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
    (when weight-runs
      (doseq [l (weights-report (str/split weight-runs #",") ks from to)] (println l)))
    (when fold-run
      (println fold-run "against" bench "at the year nodes, by development fold:")
      (doseq [l (fold-report fold-run bench ks)] (println l)))
    (doseq [run (when runs (str/split runs #","))
            scale (str/split (or scales "year,quarter-lead") #",")
            :let [{:keys [pooled per-kind]} (pd/world-pair run bench ks scale from to 400 0.1)]]
      (println (format "%-28s %-12s Brier %s  log %s" run scale (fmt pooled) (fmt (:log pooled))))
      (println "   " (str/join "  " (for [[k {:keys [brier]}] (sort-by key per-kind)] (format "%s %+.5f" k (:diff brier))))))))
