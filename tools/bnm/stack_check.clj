(ns bnm.stack-check
  "An onset head for quiet nodes (bnm-enb.13): self-excitation cannot predict
  an onset from zero history, but the escalation rows carry three forecasters
  that know different things: the model's probability, the node's own
  history and the climatology of its stratum. A log-linear stack of their
  logits (bnm.hazard/fit-stack), learned per level, channel and stratum on
  the periods before each one, against the model's raw and calibrated
  probabilities.

    jolt -A:tools -M -m bnm.stack-check v3-q-iso"
  (:require [bnm.eval :as ev]
            [bnm.hazard :as hz]
            [clojure.string :as str]))

(defn- brier [ps ys] (/ (reduce + (map (fn [p y] (let [d (- p (if y 1.0 0.0))] (* d d))) ps ys)) (count ys)))

(defn -main [run]
  (let [rows (ev/read-scored (str "data/runs/" run "/scored.edn") :country nil)
        periods (sort (distinct (map :period rows)))
        by-period (group-by :period rows)
        key-of (juxt :channel :stratum)
        xs (fn [r] [(:p-escalate r) (:p-hist r) (:p-clim r)])
        stacked (vec (mapcat (fn [[i per]]
                               (let [past (mapcat by-period (take i periods))
                                     fits (into {} (for [[k g] (group-by key-of past)
                                                         :when (>= (count (filter :escalated g)) 10)]
                                                     [k (hz/fit-stack (map xs g) (map :escalated g) (repeat 1.0) {:prior 10.0})]))]
                                 (for [r (by-period per)]
                                   (assoc r :p-stack (if-let [b (fits (key-of r))] (hz/predict-stack b (xs r)) (:p-cal r))))))
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
                       (f3 (hz/auc (map :p-escalate g) ys)) (f3 (hz/auc (map :p-stack g) ys)))))))
