(ns bnm.episode-states
  "When a country is inside an episode of a kind, as it could be known at
  the time: the aftermath's clock from an episode's end (docs/AFTERMATH-
  STOCKS.md, rank 5). The catalogues hold onsets and their dates'
  uncertainty, not how long an episode lasts; each kind's state comes from
  its own source, by its own onset rule without the cool-off:

  - conflict and war onsets: the years a conflict or a war is active
    (scripts/episode-states.py: Correlates of War before 1946, UCDP after),
    each year's last month, known when the year's onsets are (the
    catalogue's :known-lag after its first month);
  - protest and violence surges: the months the GDELT channel stays at 1 or
    more per 1000 events and three times its mean of the 36 months before
    (scripts/slow-normalize.py's rule), known at the catalogue's lag;
  - food price shocks: the months food inflation stays at 15% or more and
    10 points above its mean of the 60 months before (bnm.economic-events/
    food-shocks-monthly's rule), known at the food CPI's publication lag.

  A mark is [month known]: inside an episode at `month`, readable from
  month `known`."
  (:require [bnm.economic-events :as ee]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- mean [xs] (/ (reduce + xs) (count xs)))

(defn surge-months
  "The months of {month value} (the channel's events per 1000, untransformed)
  at 1 or more and three times the mean of the 36 months before, with at
  least 24 of them."
  [xs]
  (set (for [[m v] xs
             :let [past (keep #(get xs %) (range (- m 36) m))]
             :when (and (>= (count past) 24) (>= v 1.0) (>= v (* 3.0 (mean past))))]
         m)))

(defn food-shock-months
  "The months of {month twelve-month food inflation} at 15% or more and 10
  points above the mean of the 60 months before, with at least 36 of them."
  [xs]
  (set (for [[m x] xs
             :let [past (keep #(get xs %) (range (- m 60) m))]
             :when (and (>= (count past) 36) (>= x 15.0) (>= (- x (mean past)) 10.0))]
         m)))

(defn annual-marks
  "Marks for active years: each year's last month, known `lag` months after
  its first."
  [years lag]
  (vec (for [y (sort years)] [(+ 11 (* 12 y)) (+ lag (* 12 y))])))

(defn monthly-marks
  "Marks for months, each known `lag` months after it."
  [months lag]
  (vec (for [m (sort months)] [m (+ m lag)])))

(defn end-decay
  "(fn [unit t]) -> 2^(-(t - e)/half), e the latest month inside an episode
  known by t ({unit [[month known] ...]}), 0 with none known."
  [marks half]
  (fn [u t]
    (if-let [e (some->> (get marks u) (keep (fn [[m k]] (when (<= k t) m))) seq (apply max))]
      (Math/pow 0.5 (/ (- t e) (double half)))
      0.0)))

(defn read-annual
  "{kind {iso #{years}}} from data/derived/episode-states.csv."
  ([] (read-annual "data/derived/episode-states.csv"))
  ([path]
   (with-open [r (io/reader path)]
     (reduce (fn [acc l] (let [[k iso y] (str/split l #",")] (update-in acc [k iso] (fnil conj #{}) (parse-long y))))
             {} (rest (line-seq r))))))

(def ^:private channel {"protest_surge" "politics/protest" "violence_surge" "politics/violence"})

(defn- raw-by-month
  "{month value} of the db's series, its log1p undone where `log1p`."
  [db u v log1p]
  (if-let [{:keys [period value]} (db [u v])]
    (into {} (for [i (range (count period))]
               [(long (nth period i)) (cond-> (double (nth value i)) log1p Math/expm1)]))
    {}))

(defn violence-level
  "GDELT violence's published level at origin `t` from {month events per
  1000} (untransformed): log of the mean over the 15 months t-17..t-3 (a
  3-month lag, as violence surges'), plus 0.1; nil under 12 of them
  (t148, after docs/studies/aftermath/conflict_fast_signal.clj)."
  [xs t]
  (let [vs (keep #(get xs %) (range (- t 17) (- t 2)))]
    (when (>= (count vs) 12)
      (Math/log (+ 0.1 (mean vs))))))

(defn violence-row
  "(fn [unit origin]) -> [violence-level] or nil, from the db's
  politics/violence (stored log1p)."
  [db]
  (let [raw (memoize (fn [u] (raw-by-month db u "politics/violence" true)))]
    (fn [u t] (when-let [v (violence-level (raw u) t)] [v]))))

(def kinds #{"conflict_onset" "war_onset" "protest_surge" "violence_surge" "food_price_shock"})

(defn marks
  "{unit marks} for `kind` from the annual states (`annual`, read-annual),
  the db (bnm.named/load-series, transformed as variables.tsv says) and its
  catalogue `cat`, whose onsets are marks too (at its :known-lag)."
  [kind annual db cat]
  (when-not (kinds kind) (throw (ex-info (str "bnm.episode-states: no episode states for " kind) {:kind kind})))
  (let [lag (:known-lag cat 0)
        units (set (keep (fn [[u v]] (when (= v (case kind "food_price_shock" "food/food_inflation" (channel kind))) u)) (keys db)))
        state (case kind
                ("conflict_onset" "war_onset")
                (into {} (for [[u ys] (get annual kind)] [u (annual-marks ys lag)]))
                ("protest_surge" "violence_surge")
                (into {} (for [u units] [u (monthly-marks (surge-months (raw-by-month db u (channel kind) true)) lag)]))
                "food_price_shock"
                (into {} (for [u units] [u (monthly-marks (food-shock-months (raw-by-month db u "food/food_inflation" false))
                                                          ee/food-publication-lag)])))
        onsets (into {} (for [[u ms] (:onsets cat)] [u (monthly-marks ms lag)]))]
    (merge-with into state onsets)))
