;; The slope a kind's year node puts on the aftermath's level d (t143's
;; encoding: d alone in the node's :core), year by year, and what it trades
;; against. Runs the kind alone on the base command with --year-decay k
;; --year-decay-core true --year-decay-form level (and --year-decay-net world
;; with variant net), taps the year node's country rows at December origins
;; (bnm.lattice :tap, its models as issued) and, for each row, reads every
;; model again with d's column set to the month's no-onset value (the
;; column's least value that month: d is 0 for most rows). Prints by year:
;; rows, recent rows (d above none), their onset rate and the rest's, the
;; issued mix's change in log odds from d on the recent rows, its slope per
;; standardized unit, the top model and its slope; then the inputs most
;; correlated with d over the recent rows of 1990-2022. Diagnosis after t146
;; (bnm-7ny). First checks the issued forecasts against a world run's.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/aftermath/decay_slope.clj <kind> <level|net> <check-run> [from-year]
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[augur.dynamic :as d] '[bnm.lattice-political :as lp] '[clojure.string :as str])
(let [[k variant check from] *command-line-args*
      from (or (some-> from parse-long) 1950)
      drop-flag (fn [args f] (let [i (.indexOf ^java.util.List args f)] (if (neg? i) args (into (subvec args 0 i) (subvec args (+ i 2))))))
      args (reduce drop-flag (vec (lg/command "diag" lg/base-end)) ["--aftermath-gauges" "--direction-gauges" "--direction-at" "--theory-decay"])
      args (cond-> (into args ["--year-decay" k "--year-decay-core" "true" "--year-decay-form" "level"])
             (= "net" variant) (into ["--year-decay-net" "world"]))
      opts (wr/options args)
      cats (wr/run-catalogues opts)
      sp0 (get (wr/pipeline-specs opts [k] lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) [k] cats) :nq #{}}) k)
      ;; d is the :decay gauge group's one column; the inputs' 0 is the
      ;; intercept, gauge i is input i + 1
      dix (get-in sp0 [:gauge-groups :year :decay])
      _ (assert (= 1 (count dix)) (str "the :decay group is not d alone: " dix))
      ix (inc (long (first dix)))
      ;; the political year node names its base gauges (bnm.lattice-political/year-names)
      names (or (seq (get-in sp0 [:gauge-names :year])) (map name lp/year-names))
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
      _ (println k variant "check against" check ": matched" (count diffs) "max |p - world p|" (if (seq diffs) (apply max diffs) "none"))
      by-t (group-by :t @rows)
      mean (fn [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))
      read-row (fn [z0 {:keys [place t x o w models]}]
                 (let [x0 (assoc (vec x) ix z0)
                       dz (- (nth x ix) z0)
                       dg (into {} (for [[g m] models] [g (- (:f (d/predictive m x o)) (:f (d/predictive m x0 o)))]))
                       y (:y ((:target sp) :year place t))]
                   {:dz dz :dg dg :mix (reduce + (for [[g v] dg] (* (get w g 0.0) v))) :w w :y y :x x}))
      out (for [t (sort (keys by-t))
                :let [rs (by-t t) z0 (apply min (map #(nth (:x %) ix) rs))
                      read (map #(read-row z0 %) rs)
                      recent (filter #(> (:dz %) 1e-6) read)
                      rest-rows (remove #(> (:dz %) 1e-6) read)
                      rate (fn [xs] (mean (keep #(when (some? (:y %)) (if (:y %) 1.0 0.0)) xs)))
                      w (:w (first rs))
                      top (key (apply max-key val w))]]
            {:t t :read read :line (format "%d  rows %4d recent %3d  rate %s / %s  d's change %s  slope %s  top %s %.2f slope %s"
                                           (quot t 12) (count rs) (count recent)
                                           (if-let [r (rate recent)] (format "%.3f" r) "  -  ") (if-let [r (rate rest-rows)] (format "%.3f" r) "  -  ")
                                           (if-let [m (mean (map :mix recent))] (format "%+.3f" m) "   -  ")
                                           (if-let [m (mean (map #(/ (:mix %) (:dz %)) recent))] (format "%+.3f" m) "   -  ")
                                           (name top) (get w top)
                                           (if-let [m (mean (map #(/ (get (:dg %) top) (:dz %)) recent))] (format "%+.3f" m) "   -  "))})]
  (doseq [{:keys [line]} out] (println line))
  ;; what d moves with: correlation of d's column with every other input, recent rows 1990-2022
  (let [rs (mapcat :read (filter #(<= 1990 (quot (:t %) 12)) out))
        rs (filter #(> (:dz %) 1e-6) rs)
        n (count rs)
        col (fn [i] (map #(nth (:x %) i) rs))
        corr (fn [a b] (let [ma (mean a) mb (mean b)
                             cov (reduce + (map #(* (- %1 ma) (- %2 mb)) a b))
                             va (reduce + (map #(let [u (- % ma)] (* u u)) a)) vb (reduce + (map #(let [u (- % mb)] (* u u)) b))]
                         (if (and (pos? va) (pos? vb)) (/ cov (Math/sqrt (* va vb))) 0.0)))
        dcol (col ix)
        width (count (:x (first rs)))
        lay (lt/layout-of sp :year 0)
        group-of (fn [i] (or (some (fn [[g ixs]] (when (and (not= :all g) (some #{i} ixs)) (name g))) (:groups lay)) "-"))
        label (fn [i] (cond (and (seq names) (<= 1 i (count names))) (str (nth names (dec i)))
                            (<= 1 i (get-in sp [:gauge-width :year])) (str "gauge " (dec i))
                            :else (str "input " i)))
        cs (sort-by (comp - #(Math/abs ^double %) second)
                    (for [i (range 1 width) :when (not= i ix)] [i (corr dcol (col i))]))]
    (println "inputs most correlated with d over" n "recent rows, 1990-2022:")
    (doseq [[i c] (take 8 cs)] (println (format "  %-28s %-14s %+.2f" (label i) (group-of i) c)))))
