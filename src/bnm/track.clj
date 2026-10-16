(ns bnm.track
  "Scoring the model live, the way the backtest scores it (see `bnm.eval`),
  so that what it learns from its own record carries on from training.

  Time is cut into periods of :period-bins bins (13: quarters, as a backtest
  run with `--every 13` cuts it). The model's :track holds:

    :period    the period being observed
    :actual    its observed totals so far, {id [per channel]}
    :hist      each node's totals over the last `bnm.eval/history-length`
               periods, oldest first: its stratum, and the baseline an
               escalation is measured from
    :clim      the climatology of escalation over the periods scored
    :forecasts the forecasts made for periods at their start, by period (see
               `register`): the next period's is made on the eve of its
               start, before the one it follows is over
    :record    the escalation scores of the last `record-length` periods

  As bins are learned (`observe`), their observations add to :actual. When a
  period ends, its forecast is scored against what was observed: the
  outcomes recalibrate the model's probabilities, the climatology learns from
  them, and the period's totals join the history. A model trained by
  `bnm.train` with `--every 13` ships its history and climatology, so a
  live model starts with decades of both."
  (:require [bnm.calibrate :as cal]
            [bnm.eval :as eval]
            [bnm.geo :as geo]
            [bnm.model :as model]
            [bnm.ontology :as ont]))

(def default-period-bins 13)

(def record-length
  "How many periods' scores the record keeps."
  12)

(defn period-bins ^long [m]
  (long (get-in m [:config :period-bins] default-period-bins)))

(defn period-of ^long [m ^long b] (quot b (period-bins m)))

(defn period-start ^long [m ^long p] (* p (period-bins m)))

;; --- what a node's history says --------------------------------------------------------------

(defn baseline
  "What an escalation of node `id`'s channel `c` over `horizon` bins is
  measured from, and its stratum and benchmarks: {:last :stratum :p-clim
  :p-hist}. With a history, :last is the last period's total; without, the
  node's typical total over the horizon, `typical`."
  [track id c horizon typical]
  (let [series (mapv #(nth % c) (get-in track [:hist id]))
        ch (ont/channels c)
        level (geo/kind id)]
    (if (seq series)
      (let [st (eval/stratum (reduce max series) horizon)
            pc (eval/climate-rate (:clim track) level ch st)]
        {:last (peek series) :stratum st :p-clim pc :p-hist (eval/history-rate series horizon pc)})
      (let [st (eval/stratum typical horizon)
            pc (eval/climate-rate (:clim track) level ch st)]
        {:last typical :stratum st :p-clim pc :p-hist pc}))))

;; --- the forecast of a period ------------------------------------------------------------------

(defn register
  "The model with `rows` (from `bnm.core/outlook`) kept as the forecast of
  the period they cover, when they cover exactly one: made after its eve,
  for :period-bins bins. Otherwise the model as it was. Only the escalation
  channels are kept, which are what is scored."
  [m rows]
  (let [{:keys [bin horizon]} (first rows)
        p (when bin (period-of m (inc bin)))]
    (if (and p (= horizon (period-bins m)) (= (inc bin) (period-start m p)))
      (assoc-in m [:track :forecasts p]
                {:rows (into {}
                             (for [[id rs] (group-by :node rows)]
                               [id (into {}
                                         (for [r rs :when (some #{(:channel r)} eval/escalation-channels)]
                                           [(:channel r) (select-keys r [:p-escalate :p-cal :p-hist :p-clim
                                                                         :stratum :last])]))]))})
      m)))

(defn registered?
  "Whether the model holds a forecast for the period starting after `bin`."
  [m bin]
  (contains? (get-in m [:track :forecasts]) (period-of m (inc bin))))

(defn- score
  "The forecast's rows scored against `actual`, as `bnm.eval/rows` gives
  them for a backtest."
  [m {:keys [rows]} actual]
  (let [h (period-bins m)]
    (vec (for [[id chs] rows
               [ch {:keys [p-escalate last] :as r}] chs
               :let [a (double (nth (get actual id) (ont/channel-index ch) 0.0))]]
           (assoc r :id id :level (geo/kind id) :channel ch :actual a :p-escalate p-escalate
                  :escalated (eval/escalated? a last h))))))

(defn- summary
  "A period's escalation scores, by kind and channel, all strata pooled."
  [period rows]
  {:period period
   :escalation (into (sorted-map)
                     (for [[k g] (group-by (juxt :level :channel) rows)]
                       [k (eval/escalation-scores g)]))})

(defn- close
  "The model with its period closed: its forecast scored, the calibration
  and climatology learned from it, its totals added to the history."
  [m]
  (let [{:keys [period actual forecasts hist clim record]} (:track m)
        rows (when-let [fc (get forecasts period)] (score m fc actual))
        scored (filterv #(eval/scored-kinds (:level %)) rows)
        outcomes (eval/outcomes rows)]
    (-> m
        (cond-> (seq outcomes) (update :calibration #(cal/learn (or % {}) outcomes)))
        (assoc :track {:hist (eval/add-history (or hist {}) actual)
                       :clim (cond-> (or clim {}) (seq scored) (eval/learn-climate scored))
                       :record (cond-> (vec record)
                                 (seq rows) (-> (conj (summary period scored))
                                                (->> (take-last record-length) vec)))
                       :actual {}
                       :forecasts (into {} (filter #(> (key %) period)) forecasts)}))))

(defn observe
  "The model with bin `b`'s observations, {id double[]}, added to its track,
  closing the period before when `b` starts a new one."
  [m b obs]
  (let [p (period-of m b)
        m (cond
            (nil? (get-in m [:track :period])) (assoc-in m [:track :period] p)
            (not= p (get-in m [:track :period])) (-> (close m) (assoc-in [:track :period] p))
            :else m)]
    (update-in m [:track :actual]
               (fn [actual]
                 (reduce-kv (fn [acc id ^doubles arr]
                              (if (some pos? arr)
                                (assoc acc id (let [prev (get acc id)]
                                                (mapv #(+ (if prev (nth prev %) 0.0) (aget arr %))
                                                      (range model/C))))
                                acc))
                            (or actual {}) obs)))))

;; --- for storing -------------------------------------------------------------------------------

(defn ->part
  "The track as plain data: arrays as vectors."
  [track]
  (when track
    (update track :hist #(into {} (for [[id v] %] [id (mapv vec v)])))))

(defn <-part
  "The track from its plain data: the history as arrays, as `bnm.eval`
  keeps it."
  [part]
  (when part
    (update part :hist #(into {} (for [[id v] %] [id (mapv double-array v)])))))

(defn record
  "How the model's forecasts have scored live, by kind and channel, pooled
  over the periods in the record: `bnm.eval/escalation-scores` of each, from
  the periods' summaries (a mean weighted by each period's count)."
  [m]
  (let [periods (get-in m [:track :record])]
    (into (sorted-map)
          (for [k (distinct (mapcat (comp keys :escalation) periods))
                :let [xs (keep #(get-in % [:escalation k]) periods)
                      n (reduce + (map :n xs))]
                :when (pos? n)]
            [k (into {:n n :periods (count xs) :positives (reduce + (map :positives xs))}
                     (for [f [:brier-model :brier-cal :brier-hist :brier-clim]
                           :let [ws (keep #(when (f %) [(:n %) (f %)]) xs)
                                 wn (reduce + (map first ws))]
                           :when (pos? wn)]
                       [f (/ (reduce + (map #(* (first %) (second %)) ws)) wn)]))]))))
