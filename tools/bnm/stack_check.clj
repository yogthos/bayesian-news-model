(ns bnm.stack-check
  "An onset head for quiet nodes (bnm-enb.13): self-excitation cannot predict
  an onset from zero history, but the escalation rows carry three forecasters
  that know different things: the model's probability, the node's own
  history and the climatology of its stratum. A log-linear stack of their
  logits (bnm.hazard/fit-stack), learned per level, channel and stratum on
  the periods before each one, against the model's raw and calibrated
  probabilities.

  With a second argument, an onsets run whose preds.tsv adds the slow
  layer's country onset hazard (protest and violence surges, made at the
  last complete quarter before the period) as a fourth forecaster: the test
  of linking the slow stocks to the event model (bnm-t5b.4).

    jolt -A:tools -M -m bnm.stack-check v3-q-iso [events-2]"
  (:require [augur.pool :as pl]
            [augur.score :as sc]
            [bnm.eval :as ev]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- brier [ps ys] (/ (reduce + (map (fn [p y] (let [d (- p (if y 1.0 0.0))] (* d d))) ps ys)) (count ys)))

(def surge {:protest "protest_surge" :violence "violence_surge"})

(defn- read-onset
  "{[kind unit q] p} from an onsets run's pool predictions, window w1."
  [run]
  (with-open [r (io/reader (str "data/runs/" run "/preds.tsv"))]
    (into {} (for [line (line-seq r)
                   :let [[kind win model q unit p] (str/split line #"\t")]
                   :when (and (= win "w1") (= model "pool") (#{"protest_surge" "violence_surge"} kind))]
               [[kind unit (parse-long q)] (parse-double p)]))))

(defn -main [run & [onsets]]
  (let [onset (if onsets (read-onset onsets) {})
        q-before (fn [period] (let [y (parse-long (subs period 0 4)) m (dec (parse-long (subs period 4 6)))]
                                (dec (quot (+ (* 12 y) m) 3))))
        rows (cond->> (ev/read-scored (str "data/runs/" run "/scored.edn") :country nil)
               onsets (keep (fn [r] (when-let [k (surge (:channel r))]
                                      (when-let [p (onset [k (:id r) (q-before (:period r))])]
                                        (assoc r :p-onset p))))))
        periods (sort (distinct (map :period rows)))
        by-period (group-by :period rows)
        key-of (juxt :channel :stratum)
        xs (fn [r] (cond-> [(:p-escalate r) (:p-hist r) (:p-clim r)] onsets (conj (:p-onset r))))
        stacked (vec (mapcat (fn [[i per]]
                               (let [past (mapcat by-period (take i periods))
                                     fits (into {} (for [[k g] (group-by key-of past)
                                                         :when (>= (count (filter :escalated g)) 10)]
                                                     [k (pl/fit-stack (map xs g) (map :escalated g) (repeat 1.0) {:prior 10.0})]))]
                                 (for [r (by-period per)]
                                   (assoc r :p-stack (if-let [b (fits (key-of r))] (pl/predict-stack b (xs r)) (:p-cal r))))))
                             (map-indexed vector periods)))
        f3 #(format "%.3f" (double %))]
    (println "Country escalation, the stack of model, own history and climatology learned on earlier periods")
    (println "Brier skill against climatology (model, calibrated, stack) and AUC (model, stack), by stratum")
    (doseq [[[ch st] g] (sort-by (comp str key) (group-by key-of stacked))
            :let [ys (map :escalated g)
                  bc (brier (map :p-clim g) ys)
                  sk #(* 100 (- 1 (/ (brier (map % g) ys) bc)))]
            :when (some true? ys)]
      (println (format "  %-16s %-6s n=%5d  BSS %+6.1f%% %+6.1f%% %+6.1f%%  auc %s %s"
                       (name ch) (name st) (count g) (sk :p-escalate) (sk :p-cal) (sk :p-stack)
                       (f3 (sc/auc (map :p-escalate g) ys)) (f3 (sc/auc (map :p-stack g) ys)))))))
