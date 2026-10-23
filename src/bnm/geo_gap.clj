(ns bnm.geo-gap
  "Material capability against financial power, by country and year, and
  its gap between the major powers against their dyadic tension (bead
  bnm-3ka.9; docs/ECONOMIC-CHARACTER.md section 5, H9). Descriptive: the
  units are 28 dyads.

  Input: scripts/geo-gap.py's world shares (iso, data year, known,
  indicator, value) and its dyad table.

  Stock (data/derived/stocks/geo-capability.csv; year = known, with known
  and data_year columns), per country and data year:
    geo/material-share   the mean of the world shares in crude steel,
                         primary energy and manufacturing value added held
                         (at least two)
    geo/financial-share  the mean of the world shares in stock market
                         capitalisation and in allocated FX reserves by
                         currency (both; from 1995)
    geo/capability-gap   material less financial share: positive for a
                         producer without matching financial power, negative
                         for a financial power without matching production
  each known when the latest part is.

  The dyad panel: for dyad (a, b) at origin year t (a year end), the level
  |gap_a - gap_b| with each gap as known at t (the latest data year
  published by then, at most three years old), its change over five
  years, and the outcome in year t + 1. Fits: a linear probability (or
  linear, for restrictions) with dyad and year fixed effects, clustered by
  dyad (bnm.econ-coupling/fe-ols), and a permutation check that hands each
  dyad another dyad's gap series."
  (:require [augur.math :as m]
            [bnm.econ-coupling :as ec]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def material-parts ["steel-share" "energy-share" "manuf-share"])
(def financial-parts ["mcap-share" "reserve-share"])
(def max-age 3)

(defn- mean [xs] (/ (reduce + xs) (count xs)))

(defn- combine
  "{iso {data-year [known mean]}} over `parts` of `inputs` with at least
  `need` of them present."
  [inputs parts need]
  (let [isos (distinct (mapcat #(keys (get inputs %)) parts))]
    (into {} (for [iso isos
                   :let [years (distinct (mapcat #(keys (get-in inputs [% iso])) parts))
                         byy (into (sorted-map)
                                   (for [d years
                                         :let [ps (keep #(get-in inputs [% iso d]) parts)]
                                         :when (>= (count ps) need)]
                                     [d [(reduce max (map first ps)) (mean (map second ps))]]))]
                   :when (seq byy)]
               [iso byy]))))

(defn series
  "{variable {iso {data-year [known value]}}}."
  [inputs]
  (let [mat (combine inputs material-parts 2)
        fin (combine inputs financial-parts 2)
        gap (into {} (for [[iso byy] mat
                           :let [g (into (sorted-map) (for [[d [k x]] byy :let [[k2 f] (get-in fin [iso d])] :when f]
                                                        [d [(max k k2) (- x f)]]))]
                           :when (seq g)]
                       [iso g]))]
    {"geo/material-share" mat "geo/financial-share" fin "geo/capability-gap" gap}))

(defn stock-rows [inputs]
  (->> (for [[v by-iso] (series inputs) [iso byy] by-iso [d [k x]] byy]
         {:iso iso :year k :known k :data-year d :variable v :value x})
       (sort-by (juxt :variable :iso :data-year))
       vec))

;; --- the dyad panel --------------------------------------------------------------------

(defn as-known
  "The value of `recs` ({data-year [known value]}) at year end t: the latest
  data year published by then, at most max-age years old."
  [recs t]
  (let [ds (keep (fn [[d [k x]]] (when (<= k t) [d x])) recs)]
    (when (seq ds)
      (let [[d x] (apply max-key first ds)]
        (when (>= d (- t max-age)) x)))))

(defn dyad-panel
  "[{:g [a b] :t :level :change :y :x [level]}]: the dyads' gap distance as
  known at t and the outcome `k` in year t + 1."
  [gap dyads k]
  (let [out (into {} (for [r dyads :when (some? (get r k))] [[(:a r) (:b r) (:year r)] (double (get r k))]))
        dist (fn [a b t] (let [ga (as-known (get gap a) t) gb (as-known (get gap b) t)]
                           (when (and ga gb) (Math/abs (- ga gb)))))]
    (vec (for [[[a b y] v] (sort-by key out)
               :let [t (dec y) lv (dist a b t)]
               :when lv]
           {:g [a b] :t t :level lv :change (some->> (dist a b (- t 5)) (- lv)) :y v :x [lv]}))))

(defn permutation-p
  "The share of `reps` draws, each handing every dyad another dyad's
  predictor series (a random permutation of the dyads), whose slope is at
  least as large in absolute value as the real one ((1 + count) / (1 +
  reps))."
  [rows reps seed]
  (let [b0 (Math/abs (double (first (:b (ec/fe-ols rows)))))
        gs (vec (sort (distinct (map :g rows))))
        xs (into {} (map (juxt (juxt :g :t) :x) rows))
        g (m/rng seed)
        shuffle' (fn [v] (let [a (object-array v)]
                           (loop [i (dec (alength a))]
                             (when (pos? i)
                               (let [j (long (* (inc i) (m/next-double! g))) t (aget a i)]
                                 (aset a i (aget a j)) (aset a j t) (recur (dec i)))))
                           (vec a)))
        hits (count (filter identity
                            (for [_ (range reps)]
                              (let [pm (zipmap gs (shuffle' gs))
                                    rs (vec (keep (fn [r] (when-let [x (xs [(pm (:g r)) (:t r)])] (assoc r :x x))) rows))]
                                (and (> (count rs) 10)
                                     (>= (Math/abs (double (first (:b (ec/fe-ols rs))))) b0))))))]
    (/ (inc hits) (double (inc reps)))))

;; --- io --------------------------------------------------------------------------------

(defn read-inputs [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (reduce (fn [acc l]
                (let [f (str/split l #"," -1) g #(nth f (ix %))]
                  (assoc-in acc [(g "indicator") (g "iso") (parse-long (g "year"))]
                            [(parse-long (g "known")) (parse-double (g "value"))])))
              {} ls))))

(defn read-dyads [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))
          num #(when (seq %) (parse-double %))]
      (mapv (fn [l]
              (let [f (str/split l #"," -1) g #(nth f (ix %))]
                {:a (g "a") :b (g "b") :year (parse-long (g "year"))
                 :mid-onset (num (g "mid_onset")) :mid-any (num (g "mid_any")) :mid-hostlev (num (g "mid_hostlev"))
                 :er-ab (num (g "er_ab")) :er-ba (num (g "er_ba"))}))
            ls))))

(defn write-stock [rows path]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value,known,data_year\n")
    (doseq [{:keys [iso year variable value known data-year]} rows]
      (.write w (str iso "," year "," variable "," (format "%.6f" (double value)) "," known "," data-year "\n")))))
