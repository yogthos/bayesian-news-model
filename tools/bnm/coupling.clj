(ns bnm.coupling
  "The fast layer fed by the slow one (docs/SHOCKS.md, step 2): does the
  onset hazard known at an origin add to the event model's calibrated
  escalation probability for the quarter after it? Per channel, a stack of
  the two (bnm.hazard/stack-out-of-sample, refit yearly on what was known)
  against the escalation probability recalibrated the same way alone, held
  out from 2008, scored as bnm.anticipation scores onsets: log score and
  Brier with intervals over years, recall at a 10% alert budget, AUC, and
  matched concordance against the event model; over all countries and over
  the quiet ones (the stratum where a country's own events say least).

    jolt -A:tools -M -m bnm.coupling [--run v6-q] [--onsets onsets-live] [--model pool]

  Writes data/runs/<run>/coupling-<onsets>.txt and the stack's coefficients,
  fit on every resolved row, to coupling-<onsets>.edn."
  (:require [bnm.hazard :as hz]
            [bnm.eval :as ev]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def inputs
  "The onset kinds whose hazards feed each channel's escalation."
  {:violence ["violence_surge" "conflict_onset"]
   :protest ["protest_surge"]
   :coercion ["conflict_onset"]})

(def held-out 2008)

(defn- origin-quarter
  "The last quarter closed before a period labelled yyyymmdd starts."
  [label]
  (let [y (parse-long (subs label 0 4)) m (parse-long (subs label 4 6))]
    (dec (quot (+ (* 12 y) m -1) 3))))

(defn- load-onsets
  "{[kind unit quarter] p} for the w1 window of `model`."
  [path model kinds]
  (with-open [r (io/reader path)]
    (into {} (for [line (line-seq r)
                   :let [[k w m q u p] (str/split line #"\t")]
                   :when (and (= w "w1") (= m model) (kinds k))]
               [[k u (parse-long q)] (parse-double p)]))))

(defn- ll [rows]
  (when (seq rows)
    (/ (reduce + (for [[_ p y] rows :let [p (min (max (double p) 1e-6) (- 1.0 1e-6))]]
                   (Math/log (if y p (- 1.0 p)))))
       (double (count rows)))))

(defn- neg-brier [rows]
  (when (seq rows) (- (hz/brier (map second rows) (map #(nth % 2) rows)))))

(defn- r10 [rows] (hz/recall-at-budget rows 0.1))

(defn- line [label rows-a rows-b]
  ;; rows-a the stack, rows-b the recalibrated event model, the same rows in the same order
  (assert (= (map (juxt first #(nth % 4)) rows-a) (map (juxt first #(nth % 4)) rows-b)))
  (let [paired (mapv (fn [[q a y] [_ b _]] [q a b y]) rows-a rows-b)
        gain (fn [metric] (hz/paired-gain paired metric 500 7))
        fmt (fn [{:keys [diff lo hi]}] (format "%+.4f [%+.4f %+.4f]%s" diff lo hi (if (pos? lo) "*" " ")))
        ys (map #(nth % 2) rows-a)]
    (format "  %-6s n=%6d pos=%5d  auc %.3f -> %.3f  r@10 %.3f -> %.3f  log %s  brier %s  matched %.3f"
            label (count rows-a) (count (filter true? ys))
            (hz/auc (map second rows-b) ys) (hz/auc (map second rows-a) ys)
            (r10 rows-b) (r10 rows-a)
            (fmt (gain ll)) (fmt (gain neg-brier))
            (or (hz/matched-concordance (map (fn [[q a b y]] {:period q :p-model a :p-bench b :y y}) paired) 4) 0.5))))

(defn -main [& args]
  (let [{:keys [run onsets model]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        run (or run "v6-q") onsets (or onsets "onsets-live") model (or model "pool")
        dir (str "data/runs/" run)
        hazards (load-onsets (str "data/runs/" onsets "/preds.tsv") model (set (mapcat val inputs)))
        clip #(min (max (double %) 1e-4) (- 1.0 1e-4))
        from (* 4 held-out)
        results
        (for [[ch kinds] inputs
              :let [rows (for [r (ev/read-scored (str dir "/scored.edn") :country ch)
                               :let [q (origin-quarter (:period r))
                                     hs (map #(get hazards [% (:id r) q]) kinds)]
                               :when (every? some? hs)]
                           {:q q :id (:id r) :quiet? (= :quiet (:stratum r)) :y (boolean (:escalated r))
                            :ps (list* (clip (:p-cal r)) (clip (:p-hist r)) (map clip hs))})
                    ;; [quarter ps outcome quiet? id]
                    stack-rows (mapv (juxt :q :ps :y :quiet? :id) rows)
                    alone-rows (mapv (fn [[q ps y & more]] (into [q (take 2 ps) y] more)) stack-rows)
                    ;; each stratum's own stack, against the event model recalibrated per stratum
                    by-stratum {:by #(nth % 3)}
                    pooled (hz/stack-out-of-sample stack-rows from {})
                    both (hz/stack-out-of-sample stack-rows from by-stratum)
                    alone (hz/stack-out-of-sample alone-rows from by-stratum)
                    quiet #(filterv (fn [r] (nth r 3)) %)
                    beta (into {} (for [[k g] (group-by :quiet? rows)]
                                    [(if k :quiet :other) (vec (hz/fit-stack (map :ps g) (map :y g) (repeat 1.0) {}))]))]]
          {:channel ch :kinds kinds :beta beta
           :text (str (format "%s (event model + %s)\n" (name ch) (str/join ", " kinds))
                      (line "all" both alone) "\n"
                      (line "quiet" (quiet both) (quiet alone)) "\n"
                      (line "pooled" (quiet pooled) (quiet alone)) "\n")})
        out (str (format "Coupling: %s's calibrated country escalation probability with the onset hazards (%s, %s, w1) known at each origin,\n"
                         run onsets model)
                 (format "a stack refit yearly per stratum, against a stack of the escalation probability and own history alone; held out from %d.\n" held-out)
                 "AUC and recall at a 10% budget recalibrated -> stacked; log score and Brier gain with 90% intervals over years (* above zero);\n"
                 "matched: the share of escalations ranked above the 4 non-escalations of their quarter closest on the event model.\n"
                 (str/join (map :text results)))]
    (print out)
    (spit (str dir "/coupling-" onsets ".txt") out)
    (spit (str dir "/coupling-" onsets ".edn")
          (pr-str {:onsets onsets :model model
                   :channels (into {} (for [{:keys [channel kinds beta]} results] [channel {:kinds kinds :beta beta}]))}))))
