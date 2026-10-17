(ns bnm.scales
  "Do small crashes teach about big ones? (docs/INVARIANTS.md, bnm-t5b.16.11)

  Large falls are too few to learn from: about a dozen US bear markets since
  1926. Small ones are many. If the dynamics before a fall look the same at
  every scale once time is measured in the fall's own horizon and size in
  volatility units (the Omori and productivity laws hold across magnitudes:
  Petersen, Wang, Havlin & Stanley 2010), a hazard learned on small falls
  should rank large ones. If large crashes are dragon kings, outliers with
  their own amplifying mechanisms (Sornette 2009), it should not.

  Weekly US market from French's daily returns. Three scales, each a fall
  of about 1.2 annual standard deviations scaled by the square root of its
  horizon: 5% within 4 weeks, 10% within 13 weeks, 20% within 52 weeks.
  Inputs at window w = the horizon, in volatility units: momentum, the
  drawdown from the w-week high, the distance below the w-week average, the
  deceleration of momentum, and the ratio of volatility over w weeks to
  volatility over 4w. One fit on 1930-1989 per scale (and one pooled over
  scales); scored on 1990 on at every scale.

    jolt -A:tools -M -m bnm.scales [--name scales-1]"
  (:require [bnm.hazard :as hz]
            [bnm.paths]
            [bnm.trade :as tr]
            [bnm.trader :as us]
            [bnm.weekly :as wk]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def scales [{:label "5% in 4 weeks" :h 4 :depth 0.05}
             {:label "10% in 13 weeks" :h 13 :depth 0.10}
             {:label "20% in 52 weeks" :h 52 :depth 0.20}])

(def names [:mom :drawdown :below-average :deceleration :vol-ratio])

(defn features
  "Scale-free inputs at week k for window w (weeks), or nil without 8w weeks
  of history. `lw` is log wealth by week index, `days` the daily returns up
  to the end of week k."
  [lw days k w]
  (when (>= k (* 8 w))
    (let [d-long (take-last (* 5 4 w) (days k))
          s (us/sd d-long)
          unit (* (max s 1e-6) (Math/sqrt (* 5.0 w)))
          mom (/ (- (lw k) (lw (- k w))) unit)
          prev (/ (- (lw (- k w)) (lw (- k (* 2 w)))) unit)
          hi (apply max (map lw (range (- k (dec w)) (inc k))))
          avg (/ (reduce + (map lw (range (- k (dec w)) (inc k)))) w)]
      [mom (/ (- (lw k) hi) unit) (/ (- (lw k) avg) unit) (- prev mom)
       (/ (us/sd (take-last (max 5 (* 5 w)) (days k))) (max s 1e-9))])))

(defn run [{:keys [name]}]
  (let [name (or name "scales")
        daily (wk/read-daily)
        weeks (wk/weeks daily)
        w (tr/wealth (mapv :mkt weeks))
        lw (fn [k] (Math/log (w (inc k))))
        ;; daily returns up to the end of week k, as a vector prefix
        day-ends (vec (reductions + (map #(count (:days %)) weeks)))
        all-days (vec (mapcat :days weeks))
        days (fn [k] (subvec all-days 0 (day-ends k)))
        year-of (fn [k] (quot (:month (weeks k)) 12))
        n (count weeks)
        rows (into {} (for [{:keys [h depth] :as sc} scales]
                        [h (vec (for [k (range n)
                                      :let [x (features lw days k h)
                                            y (tr/drawdown-ahead? w (inc k) h depth)]
                                      :when (and x (some? y))]
                                  {:k k :yr (year-of k) :x (double-array (cons 1.0 x)) :y y}))]))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        train-of (fn [rs] (for [r rs :when (<= 1930 (:yr r) 1989)] {:x (:x r) :y (:y r) :w 1.0}))
        fit (fn [train] (hz/fit train (inc (count names)) {:prior 10.0}))
        ;; pooled over scales, the scale's own intercept as dummies
        pooled-x (fn [r h] (double-array (concat (seq (:x r)) (for [{h' :h} (rest scales)] (if (= h h') 1.0 0.0)))))
        pooled (hz/fit (for [{:keys [h]} scales r (rows h) :when (<= 1930 (:yr r) 1989)]
                         {:x (pooled-x r h) :y (:y r) :w 1.0})
                       (+ 1 (count names) (dec (count scales))) {:prior 10.0})
        betas (into {} (for [{:keys [h]} scales] [h (fit (train-of (rows h)))]))
        auc (fn [beta xf rs] (let [rs (filter #(>= (:yr %) 1990) rs)]
                               (hz/auc (map #(hz/predict beta (xf %)) rs) (map :y rs))))
        fmt (fn [x] (if x (format "%.3f" (double x)) "  -  "))]
    (say "Do small falls teach about big ones? Weekly US market; fitted on 1930-1989, scored on 1990 on.")
    (say "Rows are the scale fitted on, columns the scale scored. Inputs are scale-free (volatility units,")
    (say "windows equal to the horizon).")
    (say (format "  %-22s %s" "fitted on \\ scored" (str/join "  " (map #(format "%-16s" (:label %)) scales))))
    (doseq [{:keys [h label]} scales]
      (let [pos (count (filter :y (train-of (rows h))))]
        (say (format "  %-22s %s   (%d positive weeks in 1930-89)" label
                     (str/join "  " (for [{h2 :h} scales] (format "%-16s" (fmt (auc (betas h) :x (rows h2))))))
                     pos))))
    (say (format "  %-22s %s" "pooled over scales"
                 (str/join "  " (for [{h2 :h} scales] (format "%-16s" (fmt (auc pooled #(pooled-x % h2) (rows h2))))))))
    (say)
    (say "Coefficients (intercept, then" (str/join " " (map clojure.core/name names)) "):")
    (doseq [{:keys [h label]} scales]
      (say (format "  %-22s %s" label (str/join " " (map #(format "%6.2f" %) (seq (betas h)))))))
    ;; in real time: refit each January on the small falls already resolved, score the
    ;; year's weeks with the inputs at the large scale; the last week of each month
    ;; gives the month's value (the logit), for the monthly hazard
    (let [small (rows 4)
          large-x (into {} (for [k (range n) :let [x (features lw days k 52)] :when x] [k (double-array (cons 1.0 x))]))
          first-week (fn [yr] (first (filter #(= yr (year-of %)) (range n))))
          series (for [yr (range 1940 (inc (year-of (dec n))))
                       :let [k0 (first-week yr)
                             beta (fit (for [r small :when (< (+ (:k r) 4) k0)] {:x (:x r) :y (:y r) :w 1.0}))]
                       k (range k0 n) :while (= yr (year-of k))
                       :let [x (large-x k)] :when x]
                   [(:month (weeks k)) (let [p (hz/predict beta x)] (Math/log (/ p (- 1.0 p))))])
          by-month (into (sorted-map) series)
          dir (str "data/runs/" name)]
      (io/make-parents (str dir "/x"))
      (spit (str dir "/transfer.tsv") (str/join (for [[m v] by-month] (str m "\t" v "\n"))))
      (io/make-parents bnm.paths/small-fall-transfer)
      (spit bnm.paths/small-fall-transfer (str/join (for [[m v] by-month] (str m "\t" v "\n"))))
      (spit (str dir "/scales.txt") (str out)))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
