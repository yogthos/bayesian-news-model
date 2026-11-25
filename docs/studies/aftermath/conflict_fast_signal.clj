;; Conflict onsets, t140-base year forecasts at December origins 2003-2022:
;; GDELT violence (politics/violence, the 15 published months before the
;; origin, log level) against the issued p, by recent-onset group (none,
;; last onset unpublished at the 18-month lag, published). Diagnosis after
;; history_tap.clj (bnm-7ny).
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/aftermath/conflict_fast_signal.clj
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.named :as nm] '[bnm.slow-train :as st] '[clojure.string :as str])
(let [opts (wr/options (vec (lg/command "diag" lg/base-end)))
      cats (wr/run-catalogues opts)
      c (:onsets (cats "conflict_onset")) lag 18
      db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv") {:real-lags true :vars #{"politics/violence"}})
      raw (fn [u] (if-let [{:keys [period value]} (db [u "politics/violence"])]
                    (into {} (map (fn [p v] [(long p) (Math/expm1 (double v))]) period value)) {}))
      mean (fn [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))
      fc (for [l (str/split-lines (slurp "data/runs/t140-base/conflict_onset/forecasts.tsv"))
               :let [[s u o p y] (str/split l #"\t") o (parse-long o)]
               :when (and (= "year" s) (= 3 (count u)) (#{"true" "false"} y) (= 11 (mod o 12)) (<= 2003 (quot o 12) 2022))]
           {:u u :t o :p (parse-double p) :y (= "true" y)})
      rows (for [{:keys [u t] :as r} fc
                 :let [xs (raw u) a (mean (keep xs (range (- t 17) (- t 2))))] :when a
                 :let [last-m (some->> (get c u) (filter #(<= % t)) seq (apply max))]]
             (assoc r :f (Math/log (+ a 0.1))
                    :group (cond (nil? last-m) :none (> (quot (- t last-m) 12) 4) :none (> (+ last-m lag) t) :unpub :else :pub)))
      auc (fn [k xs] (let [ps (map k (filter :y xs)) ns (vec (map k (remove :y xs)))]
                       (when (and (seq ps) (seq ns)) (/ (reduce + (for [p ps n ns] (cond (> p n) 1.0 (= p n) 0.5 :else 0.0))) (* (count ps) (double (count ns)))))))
      rate (fn [xs] (mean (map #(if (:y %) 1.0 0.0) xs)))
      terc (fn [k xs] (let [s (vec (sort-by k xs)) n (count s)] [(subvec s 0 (quot n 3)) (subvec s (quot n 3) (quot (* 2 n) 3)) (subvec s (quot (* 2 n) 3))]))]
  (println "rows with the node's target and GDELT:" (count rows))
  (doseq [g [:none :unpub :pub]]
    (let [xs (filter #(= g (:group %)) rows)]
      (println (format "%-6s n %4d rate %.3f p %.3f  AUC p %.3f  AUC f %.3f" (name g) (count xs) (rate xs) (mean (map :p xs)) (auc :p xs) (auc :f xs)))
      ;; within each tercile of the issued p, onset rate by f below/above the tercile's median
      (doseq [[i b] (map-indexed vector (terc :p xs))]
        (let [s (vec (sort-by :f b)) h (quot (count s) 2) lo (subvec s 0 h) hi (subvec s h)]
          (println (format "   p tercile %d (mean p %.3f): rate f low %.3f  f high %.3f   p low %.3f p high %.3f" i (mean (map :p b)) (rate lo) (rate hi) (mean (map :p lo)) (mean (map :p hi))))))))
)
