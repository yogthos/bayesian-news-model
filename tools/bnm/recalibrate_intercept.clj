(ns bnm.recalibrate-intercept
  "bnm-7yg.4 (t157-recal, adopted 26 November 2026 at inflation crises):
  rolling, out-of-sample, intercept-only recalibration of a kind's year
  country nodes. At each December origin t from `from` on, every issued log
  odds L moves by a_t, the MAP intercept of y ~ expit(L + a) over the
  resolved rows of origins t - lag - 12 (window - 1) to t - lag, prior
  N(0, 1). The ranking within an origin is unchanged; other scales,
  aggregates and other months are as issued. The study's own code is
  docs/studies/recalibrate_intercept.py; this matches it.")

(def lag "Months: a 24-month window and up to 18 months' catalogue lag, at December origins." 48)
(def window "Years of resolved origins read." 10)
(def prior-sd 1.0)
(def ^:private eps 1e-6)

(defn logit [p]
  (let [p (min (max (double p) eps) (- 1.0 eps))]
    (Math/log (/ p (- 1.0 p)))))

(defn expit [z]
  (if (< z -700.0) 0.0 (/ 1.0 (+ 1.0 (Math/exp (- z))))))

(defn intercept
  "rows [[L y]]: the MAP shift under a N(0, prior-sd^2) prior, by damped
  Newton steps (at most 1 each: plain Newton overshoots from far off)."
  [rows]
  (loop [a 0.0 i 0]
    (if (>= i 200)
      a
      (let [ps (map (fn [[l _]] (expit (+ l a))) rows)
            g (+ (reduce + (map (fn [p [_ y]] (- p (if y 1.0 0.0))) ps rows)) (/ a (* prior-sd prior-sd)))
            h (+ (reduce + (map #(* % (- 1.0 %)) ps)) (/ 1.0 (* prior-sd prior-sd)))
            step (max -1.0 (min 1.0 (/ g h)))
            a (- a step)]
        (if (< (Math/abs step) 1e-10) a (recur a (inc i)))))))

(defn- country-december? [{:keys [scale place origin]}]
  (and (= :year scale) (= 3 (count place)) (= 11 (mod origin 12))))

(defn shifts
  "{origin a} at the December origins from the year `from` on, from the
  forecasts' resolved year country rows."
  [forecasts from]
  (let [by (reduce (fn [acc {:keys [origin p y] :as f}]
                     (if (and (country-december? f) (some? y))
                       (update acc origin (fnil conj []) [(logit p) y])
                       acc))
                   (sorted-map) forecasts)
        os (into (sorted-set) (keep #(when (country-december? %) (:origin %)) forecasts))]
    (into (sorted-map)
          (for [o os :when (>= (quot o 12) from)
                :let [lo (- o lag (* 12 (dec window))) hi (- o lag)
                      train (mapcat val (subseq by >= lo <= hi))]]
            [o (if (seq train) (intercept train) 0.0)]))))

(defn recalibrate
  "The forecasts with each year country row at a December origin from
  `from` on moved by its origin's shift."
  [forecasts from]
  (let [a (shifts forecasts from)]
    (mapv (fn [{:keys [origin p] :as f}]
            (if-let [s (and (country-december? f) (get a origin))]
              (assoc f :p (expit (+ (logit p) s)))
              f))
          forecasts)))
