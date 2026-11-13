(ns bnm.alerts
  "The alert layer at the user's cost ratio (decision of 2026-11-13: a
  missed crisis costs 4 times a false alarm). Forecasts stay calibrated and
  acceptance stays on proper scores and the fold rule; this reports what an
  alert drawn from them costs. Alert when p >= C_false / (C_false + C_miss),
  0.2 at 4:1.

    jolt -A:tools -M -m bnm.alerts --run RUN [--bench BASE] [--threshold 0.2] [--miss-cost 4]
                                   [--from 2003 --to 2022] [--episodes ratio|decile]
                                   [--ratio 2] [--min-rows 10] [--reps 400]

  For each kind's year node (December origins, resolved rows only, as
  bnm.paired/read-lattice reads them):

  - countries: hit rate (onset rows alerted), false-alarm rate (quiet rows
    alerted), alert share, expected cost per row ((misses x C_miss + false
    alarms) / rows), the cost of the better constant rule (never or always
    alerting, knowing the span's rate) and, with --bench, the bench's cost minus the run's
    (+ = the run is cheaper), its 90% interval resampling countries and
    years both (augur.score/paired-loss-2way, as bnm.world-score does),
    per kind and pooled with the kinds weighed equally;
  - episodes: December origins whose realized country rate is at least
    --ratio times the kind's rate over the span (default), or in the top
    tenth of the kind's origins (--episodes decile), with at least
    --min-rows resolved rows; caught when the mean issued p is at least the
    threshold, the missed ones listed;
  - the world node, where the run has one: the same counts;
  - the fixed outliers of docs/CALIBRATION.md 10-13: mean p and caught."
  (:require [augur.score :as sc]
            [bnm.kinds :as kinds]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn threshold-of
  "The cost-minimizing alert threshold when a miss costs `miss-cost` false
  alarms: C_false / (C_false + C_miss)."
  [miss-cost]
  (/ 1.0 (+ 1.0 (double miss-cost))))

(defn alert? [p t] (>= (double p) (double t)))

(defn cost-fn
  "The loss of a row [p y] under alerts at `t`: `miss-cost` for an onset not
  alerted, 1 for a quiet row alerted, 0 otherwise."
  [t miss-cost]
  (fn [p y] (cond (and y (not (alert? p t))) (double miss-cost)
                  (and (not y) (alert? p t)) 1.0
                  :else 0.0)))

(defn- share [a b] (when (pos? b) (/ (double a) b)))

(defn confusion
  "{:n :pos :hits :misses :false-alarms :alerts :hit-rate :false-alarm-rate
  :alert-share :cost :constant} of rows [p y] at threshold `t`; rates nil
  when their denominator is empty. :constant is the cost per row of the
  better constant rule, never or always alerting: min(misses x C_miss,
  quiet rows) / rows, the no-skill reference."
  [rows t miss-cost]
  (let [n (count rows)
        pos (count (filter second rows))
        hits (count (filter (fn [[p y]] (and y (alert? p t))) rows))
        fa (count (filter (fn [[p y]] (and (not y) (alert? p t))) rows))
        misses (- pos hits)]
    {:n n :pos pos :hits hits :misses misses :false-alarms fa :alerts (+ hits fa)
     :hit-rate (share hits pos) :false-alarm-rate (share fa (- n pos))
     :alert-share (share (+ hits fa) n)
     :cost (share (+ (* misses (double miss-cost)) fa) n)
     :constant (share (min (* pos (double miss-cost)) (- n pos)) n)}))

(defn read-node
  "{[place origin] [p y]} of a kind's forecasts.tsv at the year scale,
  December origins in the years `from` to `to`, resolved rows only;
  `node` :country (three-letter places) or :world."
  [file node from to]
  (with-open [r (io/reader file)]
    (reduce (fn [acc line]
              (let [[s place o p y] (str/split line #"\t")
                    o (parse-long o)]
                (if (and (= s "year") (#{"true" "false"} y)
                         (if (= node :world) (= place "world") (= 3 (count place)))
                         (= 11 (mod o 12)) (<= from (quot o 12) to))
                  (assoc acc [place o] [(parse-double p) (= y "true")])
                  acc)))
            {} (line-seq r))))

(defn origins
  "[{:year :n :rate :p}] of a node's forecasts, by origin: the realized
  rate and the mean issued p over the origin's resolved rows."
  [fs]
  (vec (for [[o rows] (sort-by key (group-by (comp second key) fs))
             :let [n (count rows)]]
         {:year (quot o 12) :n n
          :rate (/ (count (filter (comp second val) rows)) (double n))
          :p (/ (reduce + (map (comp first val) rows)) (double n))})))

(defn episodes
  "The origins of `fs` that are episodes: with at least :min-rows rows, a
  rate at least :ratio times the kind's pooled rate (:rule :ratio), or in
  the top tenth of origins by rate, at least one (:rule :decile)."
  [fs {:keys [rule ratio min-rows]}]
  (let [os (filter #(>= (:n %) min-rows) (origins fs))]
    (when (seq os)
      (case rule
        :ratio (let [rate (/ (count (filter (comp second val) fs)) (double (count fs)))]
                 (filterv #(and (pos? (:rate %)) (>= (:rate %) (* ratio rate))) os))
        :decile (let [k (long (Math/ceil (* 0.1 (count os))))
                      cut (:rate (nth (sort-by (comp - :rate) os) (dec k)))]
                  (filterv #(and (pos? (:rate %)) (>= (:rate %) cut)) os))))))

(defn caught [e t] (assoc e :caught (alert? (:p e) t)))

(defn cost-diff
  "The bench's mean cost per row minus the run's on the rows both issued,
  kinds weighed equally, from {kind {[place origin] [p y]}} each: {:diff
  :lo :hi}, 90% interval resampling countries and years both."
  [run bench t miss-cost reps]
  (let [rows (vec (for [[k a] run, [key [pa y]] a
                        :let [[pb _] (get-in bench [k key])] :when pb]
                    [(quot (second key) 12) pa pb y (first key) k]))]
    (when (seq rows)
      (sc/paired-loss-2way rows (cost-fn t miss-cost) {:reps reps :seed 1 :alpha 0.1 :per-block 1}))))

(def outliers
  "The fixed outlier origins of docs/CALIBRATION.md 10-13's table."
  [["food_price_shock" 2006] ["food_price_shock" 2007] ["food_price_shock" 2020] ["food_price_shock" 2021]
   ["market_crash" 2006] ["market_crash" 2007]
   ["growth_collapse" 2007] ["growth_collapse" 2008] ["growth_collapse" 2019]])

(defn- kind-file [run k] (str "data/runs/" run "/" k "/forecasts.tsv"))

(defn- pct [x] (if x (format "%5.1f%%" (* 100.0 x)) "     -"))

(defn report
  "The report's lines."
  [{:keys [run bench t miss-cost from to rule ratio min-rows reps]}]
  (let [ks (sort (filter #(.exists (io/file (kind-file run %))) (keys kinds/tiers)))
        read (fn [r node] (into {} (for [k ks :when (.exists (io/file (kind-file r k)))]
                                     [k (read-node (kind-file r k) node from to)])))
        cs (read run :country)
        bs (when bench (read bench :country))
        out (transient [])
        say (fn [& xs] (conj! out (apply format xs)))
        cfn (cost-fn t miss-cost)
        ep-opts {:rule rule :ratio ratio :min-rows min-rows}
        rule-text (if (= rule :ratio)
                    (format "rate at least %.1fx the kind's %d-%d rate" ratio from to)
                    "rate in the top tenth of the kind's origins")]
    (say "%s: alerts at p >= %.3f (a miss costs %.0f false alarms), year nodes, December origins %d-%d%s"
         run t miss-cost from to (if bench (str ", against " bench) ""))
    (say "")
    (say "Countries. hit = onset rows alerted, FA = quiet rows alerted, cost = (misses x %.0f + FA) / rows, const = cost of never or always alerting, the better%s"
         miss-cost (if bench "; dcost = bench cost - run cost (+ = run cheaper), 90% interval over countries and years" ""))
    (say "%-20s %5s %4s  %6s %6s %6s %6s %6s%s" "kind" "rows" "pos" "hit" "FA" "alerts" "const" "cost"
         (if bench (format "  %6s  %-26s" "bench" "dcost") ""))
    (doseq [k ks
            :let [fs (cs k) {:keys [n pos hit-rate false-alarm-rate alert-share cost constant]} (confusion (vals fs) t miss-cost)]]
      (if (zero? n)
        (say "%-20s no resolved rows" k)
        (say "%-20s %5d %4d  %s %s %s %6.3f %6.3f%s" k n pos (pct hit-rate) (pct false-alarm-rate) (pct alert-share) constant cost
             (if-let [b (some-> bs (get k))]
               (let [{:keys [diff lo hi]} (cost-diff {k fs} {k b} t miss-cost reps)
                     bc (:cost (confusion (vals (select-keys b (keys fs))) t miss-cost))]
                 (if diff (format "  %6.3f  %+.4f [%+.4f %+.4f]%s" bc diff lo hi (cond (pos? lo) " GAIN" (neg? hi) " LOSS" :else "")) ""))
               ""))))
    (let [kinds-c (keep #(let [c (confusion (vals (cs %)) t miss-cost)] (when (pos? (:n c)) c)) ks)
          mean (fn [f] (let [xs (keep f kinds-c)] (when (seq xs) (/ (reduce + xs) (count xs)))))]
      (say "%-20s %5d %4d  %s %s %s %6.3f %6.3f%s" "pooled (kinds equal)" (reduce + (map :n kinds-c)) (reduce + (map :pos kinds-c))
           (pct (mean :hit-rate)) (pct (mean :false-alarm-rate)) (pct (mean :alert-share)) (or (mean :constant) 0.0) (or (mean :cost) 0.0)
           (if-let [{:keys [diff lo hi]} (when bs (cost-diff cs bs t miss-cost reps))]
             (format "  %6s  %+.4f [%+.4f %+.4f]%s" "" diff lo hi (cond (pos? lo) " GAIN" (neg? hi) " LOSS" :else ""))
             "")))
    (say "")
    (say "Episodes: December origins with at least %d resolved rows and a %s; caught when the mean issued p >= %.3f." min-rows rule-text t)
    (say "rate>=t: episodes whose realized rate itself reaches the threshold, which an origin-level calibrated forecast would alert on.")
    (say "%-20s %8s %7s %6s  %s" "kind" "episodes" "rate>=t" "caught" "missed (origin: mean p / rate)")
    (let [all (for [k ks
                    :let [es (map #(caught % t) (episodes (cs k) ep-opts))]]
                (do (say "%-20s %8d %7d %6d  %s" k (count es) (count (filter #(alert? (:rate %) t) es)) (count (filter :caught es))
                         (str/join ", " (for [e es :when (not (:caught e))] (format "%d: %.3f / %.3f" (:year e) (:p e) (:rate e)))))
                    es))
          es (apply concat all)]
      (say "%-20s %8d %7d %6d" "all" (count es) (count (filter #(alert? (:rate %) t) es)) (count (filter :caught es))))
    (when bs
      (say "%s's episodes (the same rule on its own forecasts; the rates are the same outcomes):" bench)
      (let [es (for [k ks e (episodes (bs k) ep-opts)] (assoc (caught e t) :kind k))]
        (say "   %d episodes, %d caught; caught: %s" (count es) (count (filter :caught es))
             (str/join ", " (for [e es :when (:caught e)] (format "%s %d" (:kind e) (:year e)))))))
    (let [ws (read run :world)
          wb (when bench (read bench :world))]
      (when (some seq (vals ws))
        (say "")
        (say "World node (one row per origin: any onset in the world)")
        (say "%-20s %5s %4s %5s %6s %4s %6s%s" "kind" "rows" "pos" "hits" "misses" "FA" "cost" (if wb (format " %6s" "bench") ""))
        (doseq [k ks :let [fs (ws k)] :when (seq fs)
                :let [{:keys [n pos hits misses false-alarms cost]} (confusion (vals fs) t miss-cost)]]
          (say "%-20s %5d %4d %5d %6d %4d %6.3f%s" k n pos hits misses false-alarms cost
               (if-let [b (some-> wb (get k) (select-keys (keys fs)) seq)]
                 (format " %6.3f" (:cost (confusion (vals b) t miss-cost))) "")))))
    (say "")
    (say "Fixed outliers (CALIBRATION 10-13): mean p at the December origin, caught when >= %.3f" t)
    (say "%-20s %6s %5s %6s %6s%s" "kind" "origin" "rows" "rate" "p" (if bs (format " %6s %6s" "bench" "") ""))
    (doseq [[k y] outliers
            :let [o (some #(when (= y (:year %)) %) (origins (filter #(= y (quot (second (key %)) 12)) (cs k))))
                  b (when bs (some #(when (= y (:year %)) %) (origins (filter #(= y (quot (second (key %)) 12)) (bs k)))))]]
      (if-not o
        (say "%-20s %6d  no resolved rows" k y)
        (say "%-20s %6d %5d %6.3f %6.3f %-6s%s" k y (:n o) (:rate o) (:p o) (if (alert? (:p o) t) "caught" "missed")
             (if b (format " %6.3f %s" (:p b) (if (alert? (:p b) t) "caught" "missed")) ""))))
    (persistent! out)))

(defn -main [& args]
  (let [{:keys [run bench threshold from to episodes ratio reps] miss :miss-cost min-rows :min-rows}
        (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        miss-cost (or (some-> miss parse-double) 4.0)]
    (when-not run (throw (ex-info "bnm.alerts: --run is required" {})))
    (doseq [l (report {:run run :bench bench :miss-cost miss-cost
                       :t (or (some-> threshold parse-double) (threshold-of miss-cost))
                       :from (or (some-> from parse-long) 2003) :to (or (some-> to parse-long) 2022)
                       :rule (keyword (or episodes "ratio")) :ratio (or (some-> ratio parse-double) 2.0)
                       :min-rows (or (some-> min-rows parse-long) 10) :reps (or (some-> reps parse-long) 400)})]
      (println l))))
