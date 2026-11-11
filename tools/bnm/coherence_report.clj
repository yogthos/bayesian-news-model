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
  its parts. Summaries by period of the origin: 1946-2002 and 2003-2022
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
  "{[scale place] (sorted-map origin p)} of a forecasts.tsv."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[scale place origin p] (str/split line #"\t")]
                (update acc [scale place] (fnil assoc (sorted-map)) (parse-long origin) (parse-double p))))
            {} (line-seq r))))

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
  "Rows {:scale :level :place :t :p :comp :gap :log-ratio} for every parent
  and origin where all its children issued."
  [book]
  (let [places (set (map second (keys book)))
        kids (tree places)]
    (for [[scale {:keys [w]}] windows
          [parent cs] kids
          [t p] (get book [scale parent])
          :let [cps (keep #(get-in book [[scale %] t]) cs)]
          :when (= (count cps) (count cs))
          :let [nodes (map-indexed (fn [i q] {:id i :hazard {:k {:rate (c/p->rate q w)}}}) cps)
                comp (c/any-onset nodes :k {:z 0.0 :z-var 0.0} w)
                lam (c/p->rate p w)
                lsum (:rate (c/rate-sum nodes :k))]]
      {:scale scale :level (level-of parent) :place parent :t t :p p :comp comp :gap (- p comp)
       :log-ratio (when (and (pos? lam) (pos? lsum)) (Math/log (/ lam lsum)))})))

(defn time-gaps
  "Rows {:scale \"year\" :level :place :t :p :comp :gap :log-ratio} of each
  year node against its quarters' per-month rates over its window."
  [book]
  (for [[[scale place] byo] book
        :when (= "year" scale)
        [t p] byo
        :let [lead-before (get-in book [["quarter-lead" place] (- t 3)])
              lead (get-in book [["quarter-lead" place] t])
              w2 (get-in book [["quarter-w2" place] t])]
        :when (and lead-before lead w2)
        :let [chain (c/survival-chain [{:from t :to (+ t 3) :rate (c/p->rate lead-before 9)}
                                       {:from (+ t 3) :to (+ t 12) :rate (c/p->rate lead 9)}
                                       {:from (+ t 12) :to (+ t 24) :rate (c/p->rate w2 12)}])
              lam (c/p->rate p 24)]]
    {:scale scale :level (level-of place) :place place :t t :p p :comp (:p chain) :gap (- p (:p chain))
     :log-ratio (when (and (pos? lam) (pos? (:cumhaz chain))) (Math/log (/ (* 24 lam) (:cumhaz chain))))}))

(defn- quantile [xs q] (let [v (vec (sort xs))] (when (seq v) (v (min (dec (count v)) (long (* q (count v))))))))

(defn summary
  "{:n :mean :mean-abs :p90-abs :median-log-ratio :mean-p :mean-comp} of rows."
  [rows]
  (let [gs (map :gap rows) n (count rows)
        lr (keep :log-ratio rows)]
    (when (pos? n)
      {:n n :mean (/ (reduce + gs) n) :mean-abs (/ (reduce + (map #(Math/abs (double %)) gs)) n)
       :p90-abs (quantile (map #(Math/abs (double %)) gs) 0.9)
       :median-log-ratio (quantile lr 0.5)
       :mean-p (/ (reduce + (map :p rows)) n) :mean-comp (/ (reduce + (map :comp rows)) n)})))

(defn- f4 [x] (if x (format "%+.4f" (double x)) "-"))
(defn- p4 [x] (if x (format "%.4f" (double x)) "-"))

(defn- table [title rows group-keys]
  (let [groups (sort-by key (group-by (apply juxt group-keys) rows))]
    (concat [(str "\n" title "\n")
             (str "| " (str/join " | " (map name group-keys)) " | n | mean p | mean composed | mean gap | mean abs gap | p90 abs gap | median log(λ/Σλ) |")
             (str "|" (str/join "|" (repeat (+ 7 (count group-keys)) "---")) "|")]
            (for [[k rs] groups :let [{:keys [n mean mean-abs p90-abs median-log-ratio mean-p mean-comp]} (summary rs)]]
              (str "| " (str/join " | " k) " | " n " | " (p4 mean-p) " | " (p4 mean-comp) " | " (f4 mean) " | " (p4 mean-abs)
                   " | " (p4 p90-abs) " | " (f4 median-log-ratio) " |")))))

(defn report
  "Markdown lines for run `run` (data/runs/<run>) over `kinds`."
  [run kinds]
  (let [rows (vec (for [k kinds
                        :let [book (read-book (str "data/runs/" run "/" k "/forecasts.tsv"))]
                        r (concat (map #(assoc % :id :place) (place-gaps book))
                                  (map #(assoc % :id :time) (time-gaps book)))
                        :let [per (period-of (:t r))]
                        :when per]
                    (assoc r :kind k :period per)))
        place (filter #(= :place (:id %)) rows)
        time (filter #(= :time (:id %)) rows)
        dev #(= "2003-2022" (:period %))]
    (concat
     [(str "## " run)]
     (table "Place tree (C1 rates, C4 at v = 0), all kinds pooled: parent's p against 1 - Π(1 - p_c)" place [:period :scale :level])
     (table "Time tree (C5): year p against its quarters' per-month rates, all kinds pooled" time [:period :level])
     (table "Place tree, 2003-2022, year scale, by kind" (filter #(and (dev %) (= "year" (:scale %))) place) [:kind :level])
     (table "Time tree, 2003-2022, by kind (every level)" (filter dev time) [:kind]))))

(defn -main [& args]
  (let [{:keys [run kinds out]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        ks (if kinds (str/split kinds #",")
               (sort (for [f (.listFiles (io/file (str "data/runs/" run))) :when (.isDirectory f)] (.getName f))))
        lines (report run ks)]
    (if out
      (spit out (str (str/join "\n" lines) "\n"))
      (doseq [l lines] (println l)))))
