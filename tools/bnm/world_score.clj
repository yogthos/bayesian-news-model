(ns bnm.world-score
  "Trials 42 and 43's tests on a world run (bnm.world-run):

  1. each kind's year node with relations against the same world without
     them, origins 2003-2022, on its own outcomes; per kind and pooled with
     the kinds weighed equally (bnm.paired/world-pair), over the kinds the
     graph links (--graph; every kind without one); a kind with no parent
     should match the bench exactly, and its largest difference is printed;
  2. the world's joint forecasts: for each edge of the graph, each country
     and year end from 2003, the chance that both kinds have an onset within
     three years, from the simulation, against the product of the same two
     simulated marginals (the kinds treated as independent), on whether both
     happened; paired Brier and log score, edges weighed equally;
  3. with --placebo, the run against its placebo (each country reading its
     parents at another), pooled as 1.

    jolt -A:tools -M -m bnm.world-score --run world --bench world-norel [--reps 400]
                                        [--graph path] [--ews path] [--placebo run]

  Prints and writes data/runs/<run>/world-score.txt."
  (:require [augur.score :as sc]
            [bnm.kinds :as kinds]
            [bnm.paired :as pd]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- brier [p y] (let [d (- (double p) (if y 1.0 0.0))] (* d d)))
(defn- log-loss [p y] (- (Math/log (Math/max 1e-4 (double (if y p (- 1.0 p)))))))

(defn joint-rows
  "Rows [year joint product both place edge] from world-joint.tsv lines and
  the annual catalogues, for country-years whose next three years both
  catalogues cover."
  [lines cats]
  (for [l lines
        :let [[j k place y pj pk joint] (str/split l #"\t")
              y (parse-long y)
              cj (cats j) ck (cats k)
              covered? (fn [c] (when-let [[a b] (get-in c [:cover place])] (and (<= a (inc y)) (<= (+ y 3) b))))]
        :when (and (covered? cj) (covered? ck))
        :let [hit? (fn [c] (some #(< y % (+ y 4)) (get-in c [:onsets place])))
              both (boolean (and (hit? cj) (hit? ck)))]]
    [y (parse-double joint) (* (parse-double pj) (parse-double pk)) both place (str j ">" k)]))

(defn -main [& args]
  (let [{:keys [run bench reps graph placebo ews]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        reps (or (some-> reps parse-long) 400)
        all-ks (sort (filter #(.exists (io/file (str "data/runs/" run "/" % "/forecasts.tsv"))) (keys kinds/tiers)))
        ;; the kinds with any relation input: the graph's children and the kinds warned
        linked (when graph (into (set (map second (keys (read-string (slurp graph)))))
                                 (when ews (keys (read-string (slurp ews))))))
        ks (if linked (filter linked all-ks) all-ks)
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (.append out (str s "\n"))))
        fmt (fn [{:keys [diff lo hi]}] (format "%+.5f [%+.5f %+.5f]%s" diff lo hi (if (pos? lo) " GAIN" "")))
        {:keys [per-kind pooled]} (pd/world-pair run bench ks "year" 2003 2022 reps 0.1)]
    (say (format "1. %s against %s, year nodes on their own outcomes, origins 2003-2022" run bench))
    (say (format "   pooled (kinds equally)  Brier %s  log %s" (fmt pooled) (fmt (:log pooled))))
    (doseq [[k {:keys [brier log n pos]}] (sort-by key per-kind)]
      (say (format "   %-20s n=%5d pos=%4d  Brier %s  log %s" k n pos (fmt brier) (fmt log))))
    (doseq [k (remove (set ks) all-ks)
            :let [a (pd/read-lattice (str run "/" k) "year" 1870 2022)
                  b (pd/read-lattice (str bench "/" k) "year" 1870 2022)
                  d (reduce max 0.0 (for [[key [pa _]] a :let [[pb _] (b key)] :when pb] (Math/abs (- (double pa) (double pb)))))]]
      (say (format "   %-20s no parent: largest difference from %s %.2e%s" k bench d (if (< d 1e-12) "" "  MISMATCH"))))
    (when placebo
      (let [{:keys [pooled]} (pd/world-pair run placebo ks "year" 2003 2022 reps 0.1)]
        (say (format "3. %s against its placebo %s, pooled  Brier %s  log %s" run placebo (fmt pooled) (fmt (:log pooled))))))
    (let [cats (update-vals (kinds/catalogues) kinds/annual)
          rows (with-open [r (io/reader (str "data/runs/" run "/world-joint.tsv"))] (vec (joint-rows (line-seq r) cats)))
          opts {:reps reps :seed 1 :alpha 0.1 :per-block 1}]
      (say (format "2. joint forecasts against independence, %d edge-country-years, %d with both onsets" (count rows) (count (filter #(nth % 3) rows))))
      (say (format "   pooled (edges equally)  Brier %s  log %s" (fmt (sc/paired-loss-2way rows brier opts)) (fmt (sc/paired-loss-2way rows log-loss opts))))
      (doseq [[e rs] (sort-by key (group-by #(nth % 5) rows))
              :when (>= (count (filter #(nth % 3) rs)) 5)]
        (say (format "   %-40s n=%5d both=%4d  Brier %s" e (count rs) (count (filter #(nth % 3) rs)) (fmt (sc/paired-loss-2way rs brier opts))))))
    (spit (str "data/runs/" run "/world-score.txt") (str out))))
