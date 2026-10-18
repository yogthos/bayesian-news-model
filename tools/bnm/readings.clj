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
            [bnm.weekly]
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

(def tune-years
  "The years each kind's model of record is chosen on, before the years held
  out (bnm.onsets/tune-years)."
  [2000 2007])

(defn- onset-readings
  "{kind {:q latest quarter :model :top [[unit p] ...] :cusum ...}} from an
  onsets run's preds.tsv, window w1. The model is `model` when given, else
  per kind the one with the best log score over `tune-years`
  (bnm.hazard/pick-model): the stack, the log-linear combination refit each
  year, lost to the country's own history for banking crises there and after."
  [path n model]
  (let [rows (with-open [r (io/reader path)]
               (into [] (keep (fn [line]
                                (let [[kind win m q unit p y] (str/split line #"\t")]
                                  (when (and (= win "w1") (not= m "base") (or (nil? model) (= m model)))
                                    [kind m (parse-long q) unit (parse-double p) y]))))
                     (line-seq r)))]
    (into {} (for [[kind by-kind] (group-by first rows)
                   :let [by-model (group-by second by-kind)
                         resolved-of (fn [rs] (for [[_ _ q _ p y] rs :when (#{"true" "false"} y)] [q p (= "true" y)]))
                         chosen (or model (hz/pick-model (update-vals by-model resolved-of)
                                                         (first tune-years) (second tune-years)))
                         rs (mapv (fn [[k _ q u p y]] [k q u p y]) (get by-model chosen))]]
               (let [q (apply max (map second rs))
                     now (filter #(= q (second %)) rs)
                     resolved (sort-by second (filter #(and (#{"true" "false"} (nth % 4)) (>= (second %) (- q 40))) rs))
                     ps (map #(nth % 3) resolved) ys (map #(= "true" (nth % 4)) resolved)
                     known (for [[_ q _ p y] rs :when (#{"true" "false"} y)] [q p (= "true" y)])]
                 [kind {:q q :model chosen
                        :top (take n (sort-by #(- (nth % 3)) now))
                        :base (/ (count (filter true? ys)) (double (max 1 (count ys))))
                        :cusum {:up (hz/cusum ps ys 2.0 :overlap 4) :down (hz/cusum ps ys 0.5 :overlap 4)
                                :gain (hz/recalibration-gain known (- q 40) {:overlap 4})
                                ;; over every year with five years of outcomes before it
                                :gain-all (hz/recalibration-gain known (+ (apply min (map first known)) 20) {:overlap 4})}}])))))

(defn- flag
  "The monitor's verdict now (the charts' current values against h = 4),
  with the largest value of the last ten years as history: a chart that
  crossed and came back down records a past drift, not a present one.
  Overlapping outcome windows make successive forecasts dependent, so the
  charts run hotter than the nominal false-alarm rate."
  [{:keys [up down gain gain-all]}]
  (let [drift? (or (> (:last up) 4.0) (> (:last down) 4.0))
        now (cond (> (:last up) 4.0) "DRIFT now: events more frequent than forecast"
                  (> (:last down) 4.0) "DRIFT now: events less frequent than forecast"
                  :else "calibrated now")
        ;; rare events come in waves: a quiet or busy stretch trips the chart
        ;; without the forecasts being worse than a refit on it would be; a
        ;; refit that helps in the stretch can still lose across the record
        refit (when (and drift? gain gain-all)
                (cond
                  (not (pos? gain))
                  (format "; refitting on recent outcomes would not have helped (log score %+.4f over ten years): a stretch, not a shift" gain)
                  (pos? gain-all)
                  (format "; refitting on recent outcomes would have helped (log score %+.4f over ten years, %+.4f over the record): refit" gain gain-all)
                  :else
                  (format "; refitting would have helped over ten years (%+.4f) but lost over the record (%+.4f), where quiet stretches ended in waves" gain gain-all)))]
    (format "%s (CUSUM up %.1f, down %.1f; ten-year max %.1f, %.1f%s)"
            now (double (:last up)) (double (:last down)) (double (:max up)) (double (:max down)) (or refit ""))))

(defn -main [& args]
  (let [{:keys [onsets n model]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
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
    ;; the banking-crisis channel: the panel's crisis hazard (credit gap, displacement)
    ;; applied to the US, and the 12-month drawdown risk including it by total
    ;; probability, k = 0.29 the excess chance of a 25% fall given a crisis, measured
    ;; on the Macrohistory panel before 2000 (docs/OPERATIONS.md)
    (let [f (io/file paths/trader-inputs)
          rows (when (.exists f) (rest (str/split-lines (slurp f))))
          header (when (.exists f) (str/split (first (str/split-lines (slurp f))) #"\t"))
          ci (when header (.indexOf ^java.util.List header "crisis-transfer"))
          last-row (when (and ci (>= ci 0))
                     (last (filter #(not-empty (get (str/split % #"\t" -1) ci)) rows)))
          now (io/file (str paths/derived "trader-now-20-within-12m.edn"))]
      (when (and last-row (.exists now))
        (let [cells (str/split last-row #"\t" -1)
              lo (parse-double (get cells ci))
              pc (/ 1.0 (+ 1.0 (Math/exp (- lo))))
              {:keys [p-recal]} (edn/read-string (slurp now))
              k 0.29
              pm (- 1.0 (* (- 1.0 p-recal) (- 1.0 (* k pc))))]
          (say (format "  US banking crisis within 2 years (panel hazard on the credit gap), %s: %.3f"
                       (st/month-str (parse-long (first cells))) pc))
          (say (format "  20%% within 12m including the banking-crisis channel: %.3f" pm)))))
    ;; the reaction layer: once a fall starts the hazards are slow, and the 200-day
    ;; average and a volatility jump fire first (scripts/detection-lag.py)
    (let [;; FRED's daily S&P 500 (to yesterday) when it is newer than French's returns
          ;; (a month or more behind); price only, enough for averages and volatility
          sp (let [f (io/file "data/raw/slow/market/SP500.csv")]
               (when (.exists f)
                 (vec (keep (fn [line] (let [[d v] (str/split line #",")
                                             x (some-> v str/trim not-empty parse-double)]
                                         (when x [(str/replace d "-" "") x])))
                            (rest (str/split-lines (slurp f)))))))
          french (bnm.weekly/read-daily)
          use-sp? (and (seq sp) (> (compare (first (peek sp)) (first (peek french))) 0))
          [daily w] (if use-sp?
                      [(mapv (fn [[[_ a] [d b]]] [d (- (/ b a) 1.0)]) (partition 2 1 sp)) (mapv second (rest sp))]
                      (let [rs (mapv second french)]
                        [french (subvec (vec (reductions * 1.0 (map #(+ 1.0 %) rs))) 1)]))
          rets (mapv second daily)
          n (count w)
          sd (fn [xs] (let [m (/ (reduce + xs) (count xs))]
                        (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) xs)) (count xs)))))
          last-day (first (peek daily))
          sma200 (/ (reduce + (subvec w (- n 200))) 200.0)
          vr (/ (sd (subvec rets (- n 21))) (sd (subvec rets (- n 252))))
          hi (apply max (subvec w (- n 252)))
          dd (- 1.0 (/ (peek w) hi))]
      (say (format "  Reaction layer, %s-%s-%s: market %s its 200-day average (%+.1f%%); 21-day volatility %.2f times its year; %.1f%% below its 52-week high%s"
                   (subs last-day 0 4) (subs last-day 4 6) (subs last-day 6 8)
                   (if (< (peek w) sma200) "BELOW" "above") (* 100 (- (/ (peek w) sma200) 1.0)) vr (* 100 dd)
                   (if (or (< (peek w) sma200) (> vr 1.8)) " -- a fall may be under way" ""))))
    (say)
    (if (.exists (io/file onsets))
      (do (say (str "Onsets: the countries likeliest to see one within a year, latest quarter; per kind the model"
                    " with the best log score over " (str/join "-" tune-years) (when model (str " (here " model ")")) "."))
          (doseq [[kind {:keys [q model top base cusum]}] (sort-by (comp #(.indexOf ^java.util.List kinds %) key) (onset-readings onsets n model))]
            (say (format "  %s, from %s (%s model; base rate %.3f; %s):" kind (st/month-str (+ 2 (* 3 q))) model base (flag cusum)))
            (say (str "    " (str/join ", " (for [[_ _ u p] top] (format "%s %.2f" u p)))))))
      (say "Onsets: no predictions at" onsets "(scripts/readings.sh --onsets makes them)."))
    (say)
    (let [f (io/file (str paths/derived "event-outlook.txt"))]
      (if (.exists f)
        (doseq [line (str/split-lines (slurp f))] (say line))
        (say "Event model: no outlook yet (bnm.live).")))
    (spit (str paths/derived "readings.txt") (str out))))
