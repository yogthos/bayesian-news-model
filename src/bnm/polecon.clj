(ns bnm.polecon
  "Political economy stocks from annual national accounts, read point in
  time: the profit rate, the wage share and the investment share, the
  quantities Marxian crisis theory and Goodwin's distributive cycle put at
  the centre (docs/REGIMES.md). An annual series maps year to value; year Y
  is known from month `lag` of year Y+1 (months count from year 0, as
  12*year + month-1).")

(defn ratio
  "{year a/b} for the years both have."
  [a b]
  (into {} (for [[y x] a :let [d (get b y)] :when (and d (not (zero? d)))] [y (/ (double x) d)])))

(defn profit-rate
  "{year profits/capital of the year before}: the return on the capital
  advanced."
  [profits capital]
  (into {} (for [[y p] profits :let [k (get capital (dec y))] :when (and k (pos? k))] [y (/ (double p) k)])))

(defn annual-series [m lag]
  {:years (vec (sort (keys m))) :value m :lag lag})

(defn known
  "[year value] of the latest year known at month t, or nil."
  [{:keys [years value lag]} t]
  (when-let [y (last (take-while #(<= (+ (* 12 (inc %)) lag) t) years))]
    [y (value y)]))

(defn change
  "The latest known value minus the value k years before it."
  [s t k]
  (when-let [[y v] (known s t)]
    (when-let [b (get (:value s) (- y k))] (- v b))))

(defn deviation
  "The latest known value minus the mean of the years in the `window`
  before it, given at least n of them."
  [s t window n]
  (when-let [[y v] (known s t)]
    (let [prev (keep #(get (:value s) %) (range (- y window) y))]
      (when (>= (count prev) n) (- v (/ (reduce + prev) (count prev)))))))
