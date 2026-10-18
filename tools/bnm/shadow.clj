(ns bnm.shadow
  "A shadow record without waiting for one: a training run's model saved at
  an origin (--save-origins) is carried forward through the bins after it
  exactly as the live model is (bnm.live: forecasting each quarter on its
  eve, scoring it when the quarter ends, recalibrating, restructuring
  yearly), and its live record is set beside the backtest's scores of the
  same quarters. Where they differ the live path does something the backtest
  doesn't.

    jolt -A:tools -M -m bnm.shadow v6-q 20230126 [--source gdelt]

  Writes data/runs/<run>/shadow-<origin>.txt."
  (:require [bnm.core :as core]
            [bnm.eval :as ev]
            [bnm.live :as live]
            [bnm.series :as series]
            [bnm.store :as store]
            [bnm.time :as t]
            [bnm.track :as track]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- start
  "The origin's model with the live scoring state the backtest had there:
  the history and climatology from its context, and its first quarter's
  forecast registered, as the live model would have on that eve."
  [dir origin]
  (let [ctx (edn/read-string (slurp (str dir "/context-" origin ".edn")))
        m (-> (store/read-file (str dir "/origin-" origin ".edn"))
              (assoc :pending {})
              (update-in [:config :restructure-every] #(or % 52))
              (assoc :track {:actual {} :hist (:hist ctx) :clim (:clim ctx)})
              series/with-routes)
        m (update m :track track/<-part)]
    (if (core/period-eve? m)
      (core/register m (core/outlook m {:horizon 13 :sims 100 :seed (:bin m)}))
      m)))

(defn- backtest
  "The backtest's scores of the periods from `origin` on: escalation by kind
  and channel from scored.edn, ranges from rows/."
  [dir origin labels]
  (let [want (set labels)
        scored (filter #(want (:period %)) (ev/read-scored (str dir "/scored.edn") :country nil))
        rows (for [lbl labels
                   :let [f (io/file dir "rows" (str lbl ".tsv"))]
                   :when (.exists f)
                   line (str/split-lines (slurp f))
                   :let [[_ level ch _ _ actual _ q10 _ q90] (str/split line #"\t")]
                   :when (= level ":country")]
               {:channel (keyword ch) :actual (parse-double actual) :q10 (parse-double q10) :q90 (parse-double q90)})]
    {:escalation (into {} (for [[ch g] (group-by :channel scored)] [ch (ev/escalation-scores g)]))
     :ranges (into {} (for [[ch g] (group-by :channel rows)
                            :let [n (double (count g))
                                  below (/ (count (filter #(< (:actual %) (:q10 %)) g)) n)
                                  above (/ (count (filter #(> (:actual %) (:q90 %)) g)) n)]]
                        [ch {:cover80 (- 1.0 below above) :below below :above above}]))}))

(defn -main [run origin & args]
  (let [{:keys [source]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        dir (str "data/runs/" run)
        m (live/catch-up (start dir origin) (or source "gdelt"))
        rec (core/record m)
        ;; the periods the live record holds (the last `record-length`), labelled
        ;; as the backtest labels them, by their first bin's date
        pb (track/period-bins m)
        labels (sort (for [{:keys [period]} (get-in m [:track :record])]
                       (t/date-str (* period pb (get-in m [:config :bin-days])))))
        bt (backtest dir origin labels)
        f2 #(format "%.4f" (double (or % 0.0)))
        out (with-out-str
              (println (format "Shadow of %s from %s: the live path through bin %d against the backtest's %d periods (%s to %s)."
                               run origin (:bin m) (count labels) (first labels) (last labels)))
              (println "Country escalation Brier, calibrated / climatology / own history (n, escalations); 80% range: inside, below, above")
              (doseq [ch [:violence :protest :coercion :material-coop :verbal-conflict :verbal-coop]
                      :let [l (get rec [:country ch])
                            b (get-in bt [:escalation ch])
                            r (get-in bt [:ranges ch])]]
                (println (format "  %-16s live     %s / %s / %s (%d, %d over %d periods)  range %.2f %.2f %.2f"
                                 (name ch) (f2 (:brier-cal l)) (f2 (:brier-clim l)) (f2 (:brier-hist l))
                                 (:n l 0) (:positives l 0) (:periods l 0)
                                 (double (:cover80 l 0.0)) (double (:below l 0.0)) (double (:above l 0.0))))
                (println (format "  %-16s backtest %s / %s / %s (%d, %d)                range %.2f %.2f %.2f"
                                 "" (f2 (:brier-cal b)) (f2 (:brier-clim b)) (f2 (:brier-hist b))
                                 (:n b 0) (:positives b 0)
                                 (double (:cover80 r 0.0)) (double (:below r 0.0)) (double (:above r 0.0))))))]
    (print out)
    (spit (str dir "/shadow-" origin ".txt") out)))
