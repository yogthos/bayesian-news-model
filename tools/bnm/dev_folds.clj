(ns bnm.dev-folds
  "The development protocol (docs/PLAN.md 0.2, docs/PRINCIPLES.md 13):
  how a result on the years to 2022 is evaluated now that they are
  development data.

  - Rolling origin: each test year in turn is scored, trained only on rows
    whose origins are at least a gap before the year starts. The gap is at
    least the label window, so no training label reaches into the scored
    rows (refused otherwise).
  - Grouped by year and by episode: a year's rows are scored together,
    and a training row whose label window touches a global episode that
    any scored row's window touches is dropped, so one global event never
    sits on both sides. At the year node the gap already does this: with
    a known-lag of two months or more a training label ends at least a
    year before a scored label begins, and no episode of the list spans
    that (it drops no row of t48-world-norel, test years 1990-2022). It
    bites at the quarter nodes, whose labels start in the test year.
  - The drop from naive folds: the same fit scored on random k-fold
    splits of the rows (augur.math/rng-mixed), the leak those let in, is
    reported beside the grouped score.
  - A standard diagnostic: the last 20% of origins by time held out, with
    the gap, labelled development, not validation.

  A random 80/20 split of rows is not used: neighbouring years share
  outcomes (overlapping windows, episodes that span countries and years),
  so a random split scores rows whose outcomes the training rows already
  hold.

  Rows are maps with :t (the origin, a month), :lo and :hi (the label
  window (lo, hi], months) and whatever the fit reads.

    jolt -A:tools -M -m bnm.dev-folds --run t48-world-norel [--scale year] [--from 1960 --to 2022]
                                       [--test-from 1990] [--k 5] [--seed 1]

  runs the protocol on a run's country rows with a plain fit (each
  place's smoothed past rate) for every kind, and writes
  data/runs/<run>/dev-folds.txt."
  (:require [augur.math :as m]
            [bnm.benchmarks :as bm]
            [bnm.kinds :as kinds]
            [bnm.lattice-banking :as lb]
            [bnm.paired :as pd]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]))

(def global-episodes
  "Global episodes, calendar years: events that reach many countries at
  once, so their rows are kept on one side of a split. A list of record,
  not fitted; extend it as data."
  [{:name "oil-1973" :from 1973 :to 1975}
   {:name "oil-1979-debt-1982" :from 1979 :to 1983}
   {:name "1990-1992" :from 1990 :to 1992}
   {:name "asia-russia-1997" :from 1997 :to 1999}
   {:name "dotcom-2001" :from 2001 :to 2002}
   {:name "gfc-2008" :from 2007 :to 2009}
   {:name "euro-arab-spring-2010" :from 2010 :to 2012}
   {:name "covid-2020" :from 2020 :to 2021}
   {:name "energy-food-2022" :from 2022 :to 2023}])

(defn label-years
  "The calendar years the row's label window (lo, hi] touches."
  [{:keys [lo hi]}]
  (set (range (quot (inc lo) 12) (inc (quot hi 12)))))

(defn episodes-of
  "Names of the episodes the row's label window touches."
  [row episodes]
  (let [ys (label-years row)]
    (set (for [{:keys [name from to]} episodes :when (some #(<= from % to) ys)] name))))

(defn rolling-folds
  "[{:test-year :train :test}]: each year of `test-years` scored on its
  rows (origins in the year), trained on rows with origin + `gap` at or
  before the year's first month and no episode in common with the scored
  rows. `gap` must be at least `window`."
  [rows {:keys [test-years gap window episodes]}]
  (when (< gap window)
    (throw (ex-info (str "bnm.dev-folds: the gap (" gap " months) is shorter than the label window (" window ")")
                    {:gap gap :window window})))
  (let [eps (into {} (map (fn [r] [r (episodes-of r episodes)]) rows))]
    (vec (for [y test-years
               :let [start (* 12 y)
                     test (filterv #(= y (quot (:t %) 12)) rows)
                     test-eps (into #{} (mapcat eps) test)]
               :when (seq test)]
           {:test-year y
            :test test
            :train (filterv #(and (<= (+ (:t %) gap) start) (empty? (set/intersection test-eps (eps %)))) rows)}))))

(defn naive-folds
  "[{:train :test}]: the rows dealt at random into `k` folds, the order
  from augur.math/rng-mixed `seed`."
  [rows k seed]
  (let [g (m/rng-mixed seed k (count rows))
        fold-of (vec (map first (sort-by second (map-indexed (fn [i _] [(mod i k) (m/next-double! g)]) rows))))]
    (vec (for [f (range k)]
           {:test (vec (keep-indexed (fn [i r] (when (= f (fold-of i)) r)) rows))
            :train (vec (keep-indexed (fn [i r] (when (not= f (fold-of i)) r)) rows))}))))

(defn last-share-holdout
  "{:train :test :label :development}: the rows at the last `share` of the
  distinct origins held out, trained on rows `gap` months before the
  first of them."
  [rows {:keys [share gap]}]
  (let [origins (vec (sort (distinct (map :t rows))))
        n (long (Math/ceil (* share (count origins))))
        cut (nth origins (- (count origins) n))]
    {:label :development
     :cut cut
     :test (filterv #(>= (:t %) cut) rows)
     :train (filterv #(<= (+ (:t %) gap) cut) rows)}))

(defn score-folds
  "Mean loss over every scored row of `folds`, each predicted by `fit` of
  its fold's training rows ((fit train) -> (fn [row] p))."
  [folds fit loss]
  (let [ls (vec (mapcat (fn [{:keys [train test]}]
                          (let [f (fit train)] (map #(loss (f %) (:y %)) test)))
                        folds))]
    (when (seq ls) (/ (reduce + ls) (double (count ls))))))

(defn fold-drop
  "{:naive :grouped :drop}: the mean loss on random k-folds and on the
  rolling, grouped folds over the same scored rows (the test years'),
  and grouped less naive (above 0: the naive folds flattered the fit)."
  [rows fit loss {:keys [k seed test-years] :as opts}]
  (let [scored (set (filter #(contains? (set test-years) (quot (:t %) 12)) rows))
        naive (mapv #(update % :test (fn [t] (filterv scored t))) (naive-folds rows k seed))
        grouped (rolling-folds rows opts)
        a (score-folds naive fit loss)
        b (score-folds grouped fit loss)]
    {:naive a :grouped b :drop (- b a)}))

;; --- the protocol on a run ---------------------------------------------------------

(defn rate-fit
  "A plain fit: each place's past rate in the training rows, smoothed
  with one pseudo-row at their overall rate."
  [train]
  (let [rate (if (seq train) (/ (count (filter :y train)) (double (count train))) 0.0)
        by (group-by :place train)]
    (fn [{:keys [place]}]
      (let [rs (get by place)]
        (bm/smoothed (count (filter :y rs)) (count rs) rate)))))

(defn -main [& args]
  (let [{:keys [run scale from to test-from k seed]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        run (or run "t48-world-norel")
        scale (keyword (or scale "year"))
        from (or (some-> from parse-long) 1960)
        to (or (some-> to parse-long) 2022)
        test-from (or (some-> test-from parse-long) 1990)
        k (or (some-> k parse-long) 5)
        seed (or (some-> seed parse-long) 1)
        {:keys [offset horizon]} (first (filter #(= scale (:name %)) lb/scales))
        cats (kinds/catalogues)
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (.append out (str s "\n"))))
        ks (sort (filter #(.exists (io/file (str "data/runs/" run "/" % "/forecasts.tsv"))) (keys kinds/tiers)))]
    (say (format "Development protocol (bnm.dev-folds) on %s, %s nodes' country rows, origins %d-%d; fit: each place's past rate." run (name scale) from to))
    (say (format "Rolling origin, test years %d-%d, gap = window + known-lag, global episodes kept on one side; naive: %d random folds (seed %d)." test-from to k seed))
    (say "Brier, mean per row. drop = grouped - naive (the naive folds' flattery). Last 20% of origins: development, not validation.")
    (say (format "  %-22s %6s  %8s %8s %8s   %8s" "kind" "rows" "naive" "grouped" "drop" "last-20%"))
    (doseq [kd ks
            :let [lag (:known-lag (cats kd) 0)
                  window (+ offset horizon)
                  rows (vec (for [[[c o] [p y]] (pd/read-lattice (str run "/" kd) (name scale) from to)]
                              {:place c :t o :lo (+ o offset) :hi (+ o offset horizon) :y y :p p}))
                  opts {:k k :seed seed :test-years (range test-from (inc to)) :gap (+ window lag) :window window
                        :episodes global-episodes}
                  {:keys [naive grouped drop]} (fold-drop rows rate-fit bm/brier opts)
                  hold (last-share-holdout rows {:share 0.2 :gap (+ window lag)})
                  last20 (score-folds [hold] rate-fit bm/brier)]]
      (say (format "  %-22s %6d  %.5f  %.5f %+.5f   %.5f" kd (count rows) naive grouped drop last20)))
    (spit (str "data/runs/" run "/dev-folds.txt") (str out))))
