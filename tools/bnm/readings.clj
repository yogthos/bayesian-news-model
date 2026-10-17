(ns bnm.readings
  "Today's readings, collected from what the pipeline (scripts/readings.sh)
  has just made: how fresh the data is, the US drawdown hazard of record
  (raw and recalibrated, with its calibration monitor), the onset hazards'
  likeliest countries for the latest quarter (with theirs), and the event
  model's outlook. Written to data/derived/readings.txt.

  A CUSUM chart above its threshold says the forecasts have drifted from
  calibration (up: events more frequent than forecast; down: less) and the
  model should be refit or recalibrated before it is trusted.

    jolt -A:tools -M -m bnm.readings [--onsets data/derived/onset-preds.tsv] [--n 10]"
  (:require [bnm.hazard :as hz]
            [bnm.paths :as paths]
            [bnm.slow-train :as st]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def fresh-vars
  "Series whose latest period says how current the inputs are."
  [["USA" "economy/unemployment"] ["USA" "finance/credit_gap"] ["world" "energy/oil_brent"]
   ["world" "finance/vix"] ["world" "politics/geopolitical_risk"] ["world" "trade/supply_pressure"]
   ["USA" "politics/protest"]])

(defn- latest-periods
  "{[unit var] latest period string} from data/slow/series.tsv."
  []
  (let [want (set fresh-vars)
        acc (java.util.HashMap.)]
    (with-open [r (io/reader "data/slow/series.tsv")]
      (doseq [line (line-seq r)
              :let [[u v _ pend] (str/split line #"\t" 5)]
              :when (want [u v])]
        (let [k [u v]]
          (when (pos? (compare pend (or (.get acc k) ""))) (.put acc k pend)))))
    (into {} acc)))

(def kinds ["conflict_onset" "war_onset" "protest_surge" "violence_surge" "coup" "banking_crisis"])

(defn- onset-readings
  "{kind {:q latest quarter :top [[unit p] ...] :cusum ...}} from an onsets run's
  preds.tsv, window w1, for `model`: stack by default, the log-linear
  combination refit each year on resolved outcomes, which recalibrates as it
  combines."
  [path n model]
  (let [rows (with-open [r (io/reader path)]
               (into [] (keep (fn [line]
                                (let [[kind win m q unit p y] (str/split line #"\t")]
                                  (when (and (= win "w1") (= m model))
                                    [kind (parse-long q) unit (parse-double p) y]))))
                     (line-seq r)))]
    (into {} (for [[kind rs] (group-by first rows)]
               (let [q (apply max (map second rs))
                     now (filter #(= q (second %)) rs)
                     resolved (sort-by second (filter #(and (#{"true" "false"} (nth % 4)) (>= (second %) (- q 40))) rs))
                     ps (map #(nth % 3) resolved) ys (map #(= "true" (nth % 4)) resolved)]
                 [kind {:q q
                        :top (take n (sort-by #(- (nth % 3)) now))
                        :base (/ (count (filter true? ys)) (double (max 1 (count ys))))
                        :cusum {:up (hz/cusum ps ys 2.0 :overlap 4) :down (hz/cusum ps ys 0.5 :overlap 4)}}])))))

(defn- flag
  "The monitor's verdict now (the charts' current values against h = 4),
  with the largest value of the last ten years as history: a chart that
  crossed and came back down records a past drift, not a present one.
  Overlapping outcome windows make successive forecasts dependent, so the
  charts run hotter than the nominal false-alarm rate."
  [{:keys [up down]}]
  (let [now (cond (> (:last up) 4.0) "DRIFT now: events more frequent than forecast"
                  (> (:last down) 4.0) "DRIFT now: events less frequent than forecast"
                  :else "calibrated now")]
    (format "%s (CUSUM up %.1f, down %.1f; ten-year max %.1f, %.1f)"
            now (double (:last up)) (double (:last down)) (double (:max up)) (double (:max down)))))

(defn -main [& args]
  (let [{:keys [onsets n model]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        model (or model "stack")
        onsets (or onsets (str paths/derived "onset-preds.tsv"))
        n (parse-long (or n "10"))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (.append out s) (.append out "\n")))]
    (say "READINGS" (str (java.time.LocalDate/now)))
    (say)
    (say "Data, latest period:")
    (doseq [[[u v] p] (sort-by key (latest-periods))] (say (format "  %-6s %-28s %s" u v p)))
    (say)
    (say "US market: probability of a fall from today's level within the horizon (model of record).")
    (doseq [f (sort-by #(.getName %) (filter #(str/starts-with? (.getName %) "trader-now-") (.listFiles (io/file paths/derived))))]
      (let [{:keys [target month p p-recal base percentile cusum]} (edn/read-string (slurp f))
            c {:up {:max (:up cusum) :last (:up-now cusum)} :down {:max (:down cusum) :last (:down-now cusum)}}]
        (say (format "  %-16s %s  recalibrated %.3f (raw %.3f), base rate %.3f, above %.0f%% of months since 1990; %s"
                     target month (double p-recal) (double p) (double base) (* 100 percentile) (flag c)))))
    (say)
    (if (.exists (io/file onsets))
      (do (say (str "Onsets: the countries likeliest to see one within a year (" model " model), latest quarter."))
          (doseq [[kind {:keys [q top base cusum]}] (sort-by (comp #(.indexOf ^java.util.List kinds %) key) (onset-readings onsets n model))]
            (say (format "  %s, from %s (base rate %.3f; %s):" kind (st/month-str (+ 2 (* 3 q))) base (flag cusum)))
            (say (str "    " (str/join ", " (for [[_ _ u p] top] (format "%s %.2f" u p)))))))
      (say "Onsets: no predictions at" onsets "(scripts/readings.sh --onsets makes them)."))
    (say)
    (let [f (io/file (str paths/derived "event-outlook.txt"))]
      (if (.exists f)
        (doseq [line (str/split-lines (slurp f))] (say line))
        (say "Event model: no outlook yet (bnm.live).")))
    (spit (str paths/derived "readings.txt") (str out))))
