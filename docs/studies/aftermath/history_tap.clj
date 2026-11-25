;; Why a kind's year node under-forecasts its recent rows (an own onset in
;; the last 0-4 years, as recent_by_year.py) on the base command, through
;; the inputs it already has: the history group (since-last, own10, own30).
;; Runs the kind alone on t140-base's command, taps the year node's country
;; rows at December origins (bnm.lattice :tap, its models as issued) and, for
;; each recent row, reads every model again with the history columns set to
;; the month's no-history row (the row with the least own30, then own10, then
;; the most since-last). Prints by year, recent rows only: rows, onset rate,
;; the share whose last onset was not yet published at the origin (the
;; history inputs cannot see it), the issued p, the history group's change
;; in the issued mix's log odds, the weight on models that read history, and
;; each model's mean p; then pooled 2003-2022 by published / unpublished.
;; Diagnosis after t147 (bnm-7ny). First checks the issued forecasts against
;; a world run's.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/aftermath/history_tap.clj <kind> <check-run> [from-year]
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[augur.dynamic :as d] '[clojure.string :as str])
(let [[k check from] *command-line-args*
      from (or (some-> from parse-long) 1990)
      drop-flag (fn [args f] (let [i (.indexOf ^java.util.List args f)] (if (neg? i) args (into (subvec args 0 i) (subvec args (+ i 2))))))
      args (reduce drop-flag (vec (lg/command "diag" lg/base-end)) ["--aftermath-gauges" "--direction-gauges" "--direction-at" "--theory-decay"])
      opts (wr/options args)
      cats (wr/run-catalogues opts)
      cat (cats k)
      onsets (:onsets cat)
      lag (or (:known-lag cat) 0)
      sp0 (get (wr/pipeline-specs opts [k] lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) [k] cats) :nq #{}}) k)
      ;; inputs' 0 is the intercept, gauge i is input i + 1
      hix (mapv #(inc (long %)) (get-in sp0 [:gauge-groups :year :history]))
      _ (assert (= 3 (count hix)) (str "the :history group is not three columns: " hix))
      [i-since i-own10 i-own30] hix
      lay (lt/layout-of sp0 :year 0)
      reads-history (set (for [[g ixs] (:groups lay) :when (or (= :all g) (some (set hix) ixs))] g))
      rows (atom [])
      sp (assoc sp0 :tap (fn [{:keys [scale level t] :as r}]
                           (when (and (= :year scale) (= :country level) (= 11 (mod t 12)) (<= from (quot t 12) 2022))
                             (swap! rows conj (select-keys r [:place :t :x :o :w :models])))))
      end (:end sp)
      res (loop [s (lt/start sp)] (if (> (:t s) end) (lt/finish s) (recur (lt/step sp s))))
      issued (into {} (for [{:keys [scale place origin p]} (:forecasts res)
                            :when (and (= :year scale) (= 3 (count place)) (= 11 (mod origin 12)) (<= 2003 (quot origin 12) 2022))]
                        [[place origin] p]))
      world (into {} (for [l (str/split-lines (slurp (str "data/runs/" check "/" k "/forecasts.tsv")))
                           :let [[s u o p] (str/split l #"\t")]
                           :when (and (= "year" s) (= 3 (count u)))]
                       [[u (parse-long o)] (parse-double p)]))
      diffs (keep (fn [[key p]] (when-let [q (world key)] (Math/abs (- p q)))) issued)
      _ (println k "check against" check ": matched" (count diffs) "max |p - world p|" (if (seq diffs) (apply max diffs) "none"))
      _ (println "models reading history:" (str/join " " (map name (sort reads-history))) " history inputs" hix)
      mean (fn [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))
      sig (fn [z] (/ 1.0 (+ 1.0 (Math/exp (- z)))))
      last-onset (fn [u t] (some->> (get onsets u) (filter #(<= % t)) seq (apply max)))
      read-row (fn [x0h {:keys [place t x o w models]}]
                 (let [x0 (reduce (fn [v i] (assoc v i (nth x0h i))) (vec x) hix)
                       fs (into {} (for [[g m] models] [g (:f (d/predictive m x o))]))
                       f0 (into {} (for [[g m] models] [g (:f (d/predictive m x0 o))]))
                       m (last-onset place t)]
                   {:t t :place place :w w :fs fs :issued (issued [place t])
                    :mix (reduce + (for [[g f] fs] (* (get w g 0.0) f)))
                    :dh (reduce + (for [[g f] fs] (* (get w g 0.0) (- f (f0 g)))))
                    :wh (reduce + (for [[g v] w :when (reads-history g)] v))
                    :published (and m (<= (+ m lag) t))
                    :y (:y ((:target sp) :year place t))
                    :hist (mapv #(nth x %) hix)}))
      by-t (group-by :t @rows)
      out (for [t (sort (keys by-t))
                :let [rs (by-t t)
                      ;; the month's no-history row: least own30, least own10, most since-last
                      z (first (sort-by (fn [{:keys [x]}] [(nth x i-own30) (nth x i-own10) (- (nth x i-since))]) rs))
                      recent (filter (fn [{:keys [place]}] (when-let [m (last-onset place t)] (<= (quot (- t m) 12) 4))) rs)]]
            {:t t :read (mapv #(read-row (vec (:x z)) %) recent)})
      rate (fn [xs] (mean (keep #(when (some? (:y %)) (if (:y %) 1.0 0.0)) xs)))
      groups (sort (distinct (mapcat (comp keys :fs) (mapcat :read out))))
      fmt (fn [v] (if v (format "%.3f" v) "  -  "))]
  (println (format "%-5s %4s %6s %6s %6s %7s %5s  %s" "year" "n" "rate" "unpub" "p" "hist" "w(h)"
                   (str/join " " (map #(format "%-9s" (subs (name %) 0 (min 9 (count (name %))))) groups))))
  (doseq [{:keys [t read]} out :when (seq read)]
    (println (format "%-5d %4d %6s %6s %6s %+7.3f %5.2f  %s" (quot t 12) (count read) (fmt (rate read))
                     (fmt (mean (map #(if (:published %) 0.0 1.0) read)))
                     (fmt (mean (map #(sig (:mix %)) read)))
                     (mean (map :dh read)) (mean (map :wh read))
                     (str/join " " (map (fn [g] (format "%-9s" (fmt (mean (keep #(some-> (get-in % [:fs g]) sig) read))))) groups)))))
  (let [pool (filter #(<= 2003 (quot (:t %) 12)) (mapcat :read out))]
    (println "mix check: mean |sigmoid(mix) - issued p|" (mean (keep #(when (:issued %) (Math/abs (- (sig (:mix %)) (:issued %)))) pool)))
    (println "pooled 2003-2022, recent rows:")
    (doseq [[label xs] [["all" pool] ["published" (filter :published pool)] ["unpublished" (remove :published pool)]]]
      (println (format "  %-12s n %4d rate %s p %s hist %+.3f w(h) %.2f  w by model: %s" label (count xs) (fmt (rate xs))
                       (fmt (mean (map #(sig (:mix %)) xs))) (or (mean (map :dh xs)) 0.0) (or (mean (map :wh xs)) 0.0)
                       (str/join " " (for [g groups] (format "%s %.2f" (name g) (or (mean (map #(get (:w %) g 0.0) xs)) 0.0))))))
      (println (format "  %-12s model p: %s" "" (str/join " " (for [g groups] (format "%s %s" (name g) (fmt (mean (keep #(some-> (get-in % [:fs g]) sig) xs)))))))))))
