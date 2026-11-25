;; Conflict onsets: of the scored rows whose last onset is unpublished at
;; the 18-month lag (t140-base, December origins 2003-2022), how many fall
;; in the year before the origin (an annual release by September shows
;; them) and how many in the origin year. Diagnosis after history_tap.clj.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/aftermath/conflict_release_calendar.clj
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
  (let [xs (filter #(= :unpub (:group %)) rows)
        lastm (fn [{:keys [u t]}] (some->> (get c u) (filter #(<= % t)) seq (apply max)))
        prev (filter #(= (dec (quot (:t %) 12)) (quot (lastm %) 12)) xs)
        same (filter #(= (quot (:t %) 12) (quot (lastm %) 12)) xs)]
    (println "scored unpublished-recent rows" (count xs))
    (doseq [[l g] [["last onset in the previous year (a Sept release calendar shows it)" prev] ["last onset in the origin year (hidden either way)" same]]]
      (println (format "  %-68s n %3d rate %.3f p %.3f" l (count g) (or (rate g) 0.0) (or (mean (map :p g)) 0.0))))))
