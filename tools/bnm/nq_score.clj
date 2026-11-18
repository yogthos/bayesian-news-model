(ns bnm.nq-score
  "Scores of the next-quarter scale (bnm-56c.38, docs/RECURSIVE.md step 4b)
  on its own rows: country nodes, every quarter-end origin in the years
  `from` to `to`, resolved rows only.

    jolt -A:tools -M -m bnm.nq-score --run RUN [--from 2003 --to 2022] [--reps 400]

  Against two references on the same rows and the same outcome:
  - a causal climatology: at origin t the kind's rate among the country rows
    of the scale already resolved by t (issued at s, known at s + 3 + the
    catalogue's lag), (n1 + 0.5) / (n + 1);
  - the quarter-lead's implied hazard for the same quarter: its forecast
    issued at t - 3 for (t, t + 9] carried to (t, t + 3] under a constant
    hazard, 1 - (1 - p)^(1/3). Its own target is the run's catalogue, which
    for food (quarterly rule) and growth (annual) is not the scale's.
  Brier and log score differences (+ = the next quarter's loss is lower),
  90% intervals resampling countries and years (augur.score/paired-loss-
  2way), per kind and pooled with the kinds weighed equally; alerts at 4:1
  for each; and the quarter origins of bnm.alerts/quarter-outliers."
  (:require [augur.score :as sc]
            [bnm.alerts :as al]
            [bnm.kinds :as kinds]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def lags
  "The scale's catalogue lag per kind (months after the window): the
  monthly food catalogue's, the quarterly growth catalogue's, the event
  catalogues' (bnm.world-run/nq-catalogues)."
  {"market_crash" 3 "coup" 3 "protest_surge" 3 "violence_surge" 3 "food_price_shock" 9 "growth_collapse" 5})

(defn causal-clim
  "{[place origin] p}: for each row of `rows` ({[place origin] [p y]}), the
  rate among the rows known by its origin (issued at s, known at s + 3 +
  `lag`), (n1 + 0.5) / (n + 1)."
  [rows lag]
  (let [by-o (into (sorted-map) (update-vals (group-by (comp second key) rows) (fn [es] (map (comp second val) es))))
        os (keys by-o)
        ;; {origin [rows onsets]} counted over every origin up to it
        cum (loop [[o & more] os n 0 n1 0 acc (sorted-map)]
              (if-not o acc
                      (let [ys (by-o o) n (+ n (count ys)) n1 (+ n1 (count (filter true? ys)))]
                        (recur more n n1 (assoc acc o [n n1])))))
        upto (fn [s] (if-let [e (first (rsubseq cum <= s))] (val e) [0 0]))]
    (into {} (for [[[place o] _] rows
                   :let [[n n1] (upto (- o 3 lag))]]
               [[place o] (/ (+ n1 0.5) (+ n 1.0))]))))

(defn implied
  "(fn [[place t]]) -> the quarter-lead's hazard for (t, t+3] from its
  forecast at t - 3 (`ql`, {[place origin] p}), or nil."
  [ql]
  (fn [[place t]]
    (when-let [p (get ql [place (- t 3)])]
      (- 1.0 (Math/pow (- 1.0 (double p)) (/ 1.0 3.0))))))

(defn- read-all
  "{[place origin] [p y]} of every country row of `scale` (resolved or not
  when `all?`)."
  [file scale]
  (with-open [r (io/reader file)]
    (reduce (fn [acc line]
              (let [[s place o p y] (str/split line #"\t")]
                (if (and (= s scale) (= 3 (count place)))
                  (assoc acc [place (parse-long o)] [(parse-double p) (case y "true" true "false" false nil)])
                  acc)))
            {} (line-seq r))))

(def brier (fn [p y] (let [d (- (double p) (if y 1.0 0.0))] (* d d))))
(def logloss (fn [p y] (let [p (Math/max 1e-6 (Math/min (- 1.0 1e-6) (double p)))] (- (Math/log (if y p (- 1.0 p)))))))

(defn- fmt [{:keys [diff lo hi]}]
  (if diff (format "%+.4f [%+.4f %+.4f]%s" diff lo hi (cond (pos? lo) " GAIN" (neg? hi) " LOSS" :else "")) "-"))

(defn report [{:keys [run from to reps]}]
  (let [ks (sort (filter #(.exists (io/file (str "data/runs/" run "/" % "/forecasts.tsv"))) (keys kinds/tiers)))
        data (into {} (for [k ks
                            :let [f (str "data/runs/" run "/" k "/forecasts.tsv")
                                  all (read-all f "quarter-next")]
                            :when (seq all)]
                        (let [resolved (into {} (filter (comp some? second val) all))
                              clim (causal-clim resolved (lags k))
                              ql (into {} (for [[key [p _]] (read-all f "quarter-lead")] [key p]))
                              imp (implied ql)
                              rows (into {} (filter (fn [[[_ o] _]] (<= from (quot o 12) to)) resolved))]
                          [k {:rows rows :clim clim :imp imp}])))
        out (transient [])
        say (fn [& xs] (conj! out (apply format xs)))
        t (al/threshold-of 4.0)
        paired (fn [k-rows ref loss]
                 (let [rs (vec (for [[k {:keys [rows] :as d}] k-rows [[place o :as key] [p y]] rows
                                     :let [b ((ref d) key)] :when b]
                                 [(quot o 12) p b y place k]))]
                   (when (seq rs) (assoc (sc/paired-loss-2way rs loss {:reps reps :seed 1 :alpha 0.1 :per-block 1}) :n (count rs)))))]
    (say "%s: the next-quarter scale, country nodes, quarter-end origins %d-%d, resolved rows" run from to)
    (say "")
    (say "%-18s %6s %4s %6s %7s %7s %7s  %-28s %-28s %-28s %-28s" "kind" "rows" "pos" "mean p" "Brier" "clim" "ql-imp"
         "Brier vs clim" "log vs clim" "Brier vs ql-imp (rows)" "log vs ql-imp")
    (doseq [[k {:keys [rows clim imp] :as d}] (sort-by key data)
            :let [n (count rows) pos (count (filter (comp second val) rows))
                  mb (fn [f] (/ (reduce + (for [[key [p y]] rows] (brier (f key p) y))) n))
                  with-ql (filter #(imp (key %)) rows)
                  qb (when (seq with-ql) (/ (reduce + (for [[key [_ y]] with-ql] (brier (imp key) y))) (count with-ql)))
                  m {k d}
                  pq (paired m :imp brier)]]
      (say "%-18s %6d %4d %6.3f %7.4f %7.4f %7s  %-28s %-28s %-28s %-28s" k n pos (/ (reduce + (map (comp first val) rows)) n)
           (mb (fn [_ p] p)) (mb (fn [key _] (clim key))) (if qb (format "%.4f" qb) "-")
           (fmt (paired m :clim brier)) (fmt (paired m :clim logloss))
           (str (fmt pq) (if pq (str " (" (:n pq) ")") "")) (fmt (paired m :imp logloss))))
    (say "%-18s %50s %-28s %-28s %-28s %-28s" "pooled (kinds equal)" ""
         (fmt (paired data :clim brier)) (fmt (paired data :clim logloss)) (fmt (paired data :imp brier)) (fmt (paired data :imp logloss)))
    (say "")
    (say "Alerts at p >= %.3f (a miss costs 4 false alarms): cost per row, hit and false-alarm rates; dcost = reference cost - next quarter's" t)
    (say "%-18s %6s  %-22s %-22s %-22s  %-28s %-28s" "kind" "rows" "next quarter" "clim" "ql-imp (its rows)" "dcost vs clim" "dcost vs ql-imp")
    (let [cfn (al/cost-fn t 4.0)
          cell (fn [prs] (let [{:keys [cost hit-rate false-alarm-rate]} (al/confusion prs t 4.0)]
                           (format "%.3f %5.1f%% %5.1f%%" cost (* 100 (or hit-rate 0.0)) (* 100 (or false-alarm-rate 0.0)))))]
      (doseq [[k {:keys [rows clim imp] :as d}] (sort-by key data)
              :let [wq (filter #(imp (key %)) rows)]]
        (say "%-18s %6d  %-22s %-22s %-22s  %-28s %-28s" k (count rows) (cell (vals rows))
             (cell (for [[key [_ y]] rows] [(clim key) y]))
             (if (seq wq) (cell (for [[key [_ y]] wq] [(imp key) y])) "-")
             (fmt (paired {k d} :clim cfn)) (fmt (paired {k d} :imp cfn))))
      (say "%-18s %6s  %-22s %-22s %-22s  %-28s %-28s" "pooled (kinds equal)" "" "" "" ""
           (fmt (paired data :clim cfn)) (fmt (paired data :imp cfn))))
    (say "")
    (say "Episodes by quarter origin: rows, realized rate, mean p of the next quarter, the climatology and the quarter-lead's implied hazard (its rows)")
    (say "%-18s %7s %5s %6s %6s %6s %6s" "kind" "origin" "rows" "rate" "nq p" "clim" "ql-imp")
    (doseq [[k lo hi] al/quarter-outliers :when (data k)
            :let [{:keys [rows clim imp]} (data k)]
            [o es] (sort-by key (group-by (comp second key) (filter #(<= lo (second (key %)) hi) rows)))
            :let [n (count es) mean (fn [xs] (when (seq xs) (/ (reduce + xs) (count xs))))
                  qs (keep #(imp (key %)) es)]]
      (say "%-18s %7s %5d %6.3f %6.3f %6.3f %6s %s" k (al/label "quarter-next" o) n
           (/ (count (filter (comp second val) es)) (double n)) (mean (map (comp first val) es)) (mean (map #(clim (key %)) es))
           (if (seq qs) (format "%.3f" (mean qs)) "-") (if (al/alert? (mean (map (comp first val) es)) t) "caught" "missed")))
    (persistent! out)))

(defn -main [& args]
  (let [{:keys [run from to reps]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))]
    (when-not run (throw (ex-info "bnm.nq-score: --run is required" {})))
    (doseq [l (report {:run run :from (or (some-> from parse-long) 2003) :to (or (some-> to parse-long) 2022)
                       :reps (or (some-> reps parse-long) 400)})]
      (println l))))
