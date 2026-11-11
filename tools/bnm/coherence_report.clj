(ns bnm.coherence-report
  "How far a run's book is from the identities of the recursive model
  (docs/RECURSIVE.md §4, C1/C4/C5): a diagnostic, nothing fitted, no
  forecast changed.

    jolt -A:tools -M -m bnm.coherence-report --run t50-world-norel [--kinds a,b] [--out file.md]

  For each kind's forecasts.tsv (the forecasts as issued; without
  :state-from they are also the states):
  - place tree, at every scale (C1 for rates, C4 at v = 0): each parent's
    p against 1 - Π(1 - p_c) over its children at the same origin, and its
    rate per month, -log(1 - p)/w, against Σ λ_c, as log(λ_P / Σ λ_c);
  - time tree (C5): each year node's p for (t, t+24] against the survival
    chain of its quarters' per-month rates: the lead quarter issued at
    t - 3 for (t, t+3], the lead at t for (t+3, t+12] and the w2 quarter
    at t for (t+12, t+24]; non-overlapping, so composable (the quarters'
    window probabilities themselves overlap and are not).
  A gap is p less its composition: positive when the node says more than
  its parts. The composition counts only the children whose own target is
  known. Beside the gaps, each table has the observed rate, and a
  calibration table sets every node's mean p against its observed rate:
  a parent below its composition can be dependence among the children or
  the children's p running above their outcomes, and the calibration
  columns tell the two apart where the countries are calibrated. Summaries by period of the origin: 1946-2002 and 2003-2022
  (development), 2023 on."
  (:require [bnm.compose :as c]
            [bnm.geo :as geo]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def windows
  "Each scale's window (months) and offset from the origin (tools/bnm/
  lattice_banking.clj scales)."
  {"quarter-lead" {:offset 3 :w 9} "quarter-w2" {:offset 12 :w 12} "year" {:offset 0 :w 24}})

(def periods [["1946-2002" 1946 2002] ["2003-2022" 2003 2022] ["2023-" 2023 9999]])

(defn- period-of [t] (let [y (quot (long t) 12)] (some (fn [[n a b]] (when (<= a y b) n)) periods)))

(defn read-book
  "{[scale place] (sorted-map origin {:p :y})} of a forecasts.tsv: the
  forecast and its outcome (nil where the node's target is unknown:
  outside its coverage, or not at risk)."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[scale place origin p y] (str/split line #"\t" -1)]
                (update acc [scale place] (fnil assoc (sorted-map)) (parse-long origin)
                        {:p (parse-double p) :y (case y "true" true "false" false nil)})))
            {} (line-seq r))))

(defn- p-of "An entry's p (a bare number is a p whose outcome is counted)." [e] (if (map? e) (:p e) e))
(defn- y-of [e] (when (map? e) (:y e)))
(defn- scored? "Whether an entry's own target is known." [e] (or (number? e) (some? (:y e))))

(defn level-of [place]
  (cond (= "world" place) "world" (geo/region? place) "region" (geo/subregion? place) "subregion" :else "country"))

(defn tree
  "{parent [children]} over the places in `places`: country under its
  subregion, subregion under its region, region under the world."
  [places]
  (let [in (set places)
        parent (fn [p] (case (level-of p)
                         "world" nil
                         "region" "world"
                         (first (filter in (geo/parents p)))))]
    (reduce (fn [acc p] (if-let [q (parent p)] (if (in q) (update acc q (fnil conj []) p) acc) acc))
            {} (sort places))))

(defn place-gaps
  "Rows {:scale :level :place :t :p :y :comp :gap :log-ratio} for every
  parent and origin where all its children issued and its own target is
  known: the composition is over the children whose own target is known,
  the ones the parent's target counts (review of step 1: a child outside
  its coverage or not at risk was composed in, and the parent never
  counts it)."
  [book]
  (let [places (set (map second (keys book)))
        kids (tree places)]
    (for [[scale {:keys [w]}] windows
          [parent cs] kids
          [t e] (get book [scale parent])
          :when (scored? e)
          :let [es (keep #(get-in book [[scale %] t]) cs)]
          :when (= (count es) (count cs))
          :let [cps (map p-of (filter scored? es))]
          :when (seq cps)
          :let [p (p-of e)
                nodes (map-indexed (fn [i q] {:id i :hazard {:k {:rate (c/p->rate q w)}}}) cps)
                comp (c/any-onset nodes :k {:z 0.0 :z-var 0.0} w)
                lam (c/p->rate p w)
                lsum (:rate (c/rate-sum nodes :k))]]
      {:scale scale :level (level-of parent) :place parent :t t :p p :y (y-of e) :comp comp :gap (- p comp)
       :log-ratio (when (and (pos? lam) (pos? lsum)) (Math/log (/ lam lsum)))})))

(defn calibration-rows
  "Rows {:scale :level :place :t :p :y} of every node and origin whose
  outcome is known, countries included: the forecasts against what
  happened, beside the composition gaps (review of step 1: parents below
  their composition is mostly the countries' p running above their
  observed rates)."
  [book]
  (for [[[scale place] byo] book
        :when (windows scale)
        [t e] byo
        :when (some? (y-of e))]
    {:scale scale :level (level-of place) :place place :t t :p (p-of e) :y (y-of e)}))

(defn time-gaps
  "Rows {:scale \"year\" :level :place :t :p :comp :gap :log-ratio} of each
  year node against its quarters' per-month rates over its window."
  [book]
  (for [[[scale place] byo] book
        :when (= "year" scale)
        [t e] byo
        :let [p (p-of e)
              lead-before (some-> (get-in book [["quarter-lead" place] (- t 3)]) p-of)
              lead (some-> (get-in book [["quarter-lead" place] t]) p-of)
              w2 (some-> (get-in book [["quarter-w2" place] t]) p-of)]
        :when (and lead-before lead w2)
        :let [chain (c/survival-chain [{:from t :to (+ t 3) :rate (c/p->rate lead-before 9)}
                                       {:from (+ t 3) :to (+ t 12) :rate (c/p->rate lead 9)}
                                       {:from (+ t 12) :to (+ t 24) :rate (c/p->rate w2 12)}])
              lam (c/p->rate p 24)]]
    {:scale scale :level (level-of place) :place place :t t :p p :y (y-of e) :comp (:p chain) :gap (- p (:p chain))
     :log-ratio (when (and (pos? lam) (pos? (:cumhaz chain))) (Math/log (/ (* 24 lam) (:cumhaz chain))))}))

(defn- quantile [xs q] (let [v (vec (sort xs))] (when (seq v) (v (min (dec (count v)) (long (* q (count v))))))))

(defn summary
  "{:n :mean :mean-abs :p90-abs :median-log-ratio :mean-p :mean-comp
  :share-below :mean-y :n-y} of rows: :share-below the share of rows whose
  gap is below zero, :mean-y the observed rate over the rows whose outcome
  is known (:n-y of them)."
  [rows]
  (let [gs (keep :gap rows) n (count rows)
        lr (keep :log-ratio rows)
        ys (keep :y rows)]
    (when (pos? n)
      {:n n
       :mean (when (seq gs) (/ (reduce + gs) (count gs)))
       :mean-abs (when (seq gs) (/ (reduce + (map #(Math/abs (double %)) gs)) (count gs)))
       :p90-abs (quantile (map #(Math/abs (double %)) gs) 0.9)
       :median-log-ratio (quantile lr 0.5)
       :share-below (when (seq gs) (/ (double (count (filter neg? gs))) (count gs)))
       :mean-p (/ (reduce + (map :p rows)) n)
       :mean-comp (let [cs (keep :comp rows)] (when (seq cs) (/ (reduce + cs) (count cs))))
       :mean-y (when (seq ys) (/ (double (count (filter true? ys))) (count ys)))
       :n-y (count ys)})))

(defn- f4 [x] (if x (format "%+.4f" (double x)) "-"))
(defn- p4 [x] (if x (format "%.4f" (double x)) "-"))

(defn- table [title rows group-keys]
  (let [groups (sort-by key (group-by (apply juxt group-keys) rows))]
    (concat [(str "\n" title "\n")
             (str "| " (str/join " | " (map name group-keys)) " | n | mean p | mean observed | mean composed | mean gap | share below | mean abs gap | p90 abs gap | median log(λ/Σλ) |")
             (str "|" (str/join "|" (repeat (+ 9 (count group-keys)) "---")) "|")]
            (for [[k rs] groups :let [{:keys [n mean mean-abs p90-abs median-log-ratio mean-p mean-comp share-below mean-y]} (summary rs)]]
              (str "| " (str/join " | " k) " | " n " | " (p4 mean-p) " | " (p4 mean-y) " | " (p4 mean-comp) " | " (f4 mean)
                   " | " (p4 share-below) " | " (p4 mean-abs) " | " (p4 p90-abs) " | " (f4 median-log-ratio) " |")))))

(defn- calibration-table [title rows group-keys]
  (let [groups (sort-by key (group-by (apply juxt group-keys) rows))]
    (concat [(str "\n" title "\n")
             (str "| " (str/join " | " (map name group-keys)) " | n | mean p | mean observed | p less observed |")
             (str "|" (str/join "|" (repeat (+ 4 (count group-keys)) "---")) "|")]
            (for [[k rs] groups :let [{:keys [n mean-p mean-y]} (summary rs)]]
              (str "| " (str/join " | " k) " | " n " | " (p4 mean-p) " | " (p4 mean-y) " | " (f4 (- mean-p mean-y)) " |")))))

(defn report
  "Markdown lines for run `run` (data/runs/<run>) over `kinds`."
  [run kinds]
  (let [rows (vec (for [k kinds
                        :let [book (read-book (str "data/runs/" run "/" k "/forecasts.tsv"))]
                        r (concat (map #(assoc % :id :place) (place-gaps book))
                                  (map #(assoc % :id :time) (time-gaps book))
                                  (map #(assoc % :id :calibration) (calibration-rows book)))
                        :let [per (period-of (:t r))]
                        :when per]
                    (assoc r :kind k :period per)))
        place (filter #(= :place (:id %)) rows)
        time (filter #(= :time (:id %)) rows)
        cal (filter #(= :calibration (:id %)) rows)
        dev #(= "2003-2022" (:period %))]
    (concat
     [(str "## " run)]
     (table "Place tree (C1 rates, C4 at v = 0), all kinds pooled: parent's p against 1 - Π(1 - p_c)" place [:period :scale :level])
     (table "Time tree (C5): year p against its quarters' per-month rates, all kinds pooled" time [:period :level])
     (table "Place tree, 2003-2022, year scale, by kind" (filter #(and (dev %) (= "year" (:scale %))) place) [:kind :level])
     (table "Time tree, 2003-2022, by kind (every level)" (filter dev time) [:kind])
     (calibration-table "Calibration, 2003-2022, year scale: every node's mean p against its observed rate" (filter #(and (dev %) (= "year" (:scale %))) cal) [:kind :level]))))

(defn -main [& args]
  (let [{:keys [run kinds out]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        ks (if kinds (str/split kinds #",")
               (sort (for [f (.listFiles (io/file (str "data/runs/" run))) :when (.isDirectory f)] (.getName f))))
        lines (report run ks)]
    (if out
      (spit out (str (str/join "\n" lines) "\n"))
      (doseq [l lines] (println l)))))
