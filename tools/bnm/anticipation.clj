(ns bnm.anticipation
  "Whether the onset models anticipate, scored as the epic's acceptance
  test fixes it in advance (docs/PHASE-CHANGES.md): every model against the
  country's own onset history at fixed alert budgets, out of sample, in
  2003-14 and 2015-26 separately, over all country-quarters and over the
  hard ones (no onset of the kind in ten years), with 90% intervals
  resampling whole years, and matched-control concordance (each onset
  against the four non-onsets of its quarter that history rates closest: 0.5
  is knowing only what history knows).

    jolt -A:tools -M -m bnm.anticipation [--run onsets-live] [--kinds a,b] [--bench history]

  --bench sets the model every other is scored against: a group's placebo
  (ties-placebo) asks whether the group knows anything about the country.

  Reads data/runs/<run>/preds.tsv; writes anticipation.txt beside it."
  (:require [bnm.hazard :as hz]
            [bnm.onsets :as onsets]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def eras [["03-14" 2003 2014] ["15-26" 2015 2026]])

(def default-bench "history")

(defn- read-preds
  "{[kind window] {model {[unit q] [p y]}}}, resolved rows only."
  [path kinds]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[kind w m q unit p y] (str/split line #"\t")]
                (if (and (#{"true" "false"} y) (or (nil? kinds) (kinds kind)) (not= m "base"))
                  (assoc-in acc [[kind w] m [unit (parse-long q)]] [(parse-double p) (= "true" y)])
                  acc)))
            {} (line-seq r))))

(defn- f3 [x] (if x (format "%+.3f" (double x)) "   -  "))

(defn- line-for
  "One model's scores against history on `keys` ([unit q])."
  [model-rows bench-rows keys]
  (let [rows (vec (for [k keys
                        :let [[pa y] (model-rows k) [pb _] (bench-rows k)]
                        :when (and pa pb)]
                    [(second k) pa pb y]))
        pos (count (filter #(nth % 3) rows))]
    (when (and (pos? pos) (< pos (count rows)))
      (let [r5 #(hz/recall-at-budget % 0.05)
            r10 #(hz/recall-at-budget % 0.10)
            u (fn [rs] (hz/usefulness (map second rs) (map #(nth % 2) rs) 0.9))]
        {:n (count rows) :pos pos
         :r5 (r5 (map (fn [[q a _ y]] [q a y]) rows)) :r5-gain (hz/paired-recall-gain rows 0.05 200 1)
         :r10 (r10 (map (fn [[q a _ y]] [q a y]) rows)) :r10-gain (hz/paired-recall-gain rows 0.10 200 1)
         :u (u (map (fn [[q a _ y]] [q a y]) rows)) :u-gain (hz/paired-usefulness-gain rows 0.9 200 1)
         :matched (hz/matched-concordance (map (fn [[q a b y]] {:period q :p-model a :p-bench b :y y}) rows) 4)}))))

(defn -main [& args]
  (let [{:keys [run kinds bench]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        bench (or bench default-bench)
        dir (str "data/runs/" (or run "onsets-live"))
        kinds (when kinds (set (str/split kinds #",")))
        preds (read-preds (str dir "/preds.tsv") kinds)
        events (onsets/read-events "data/slow/events.tsv")
        gain (fn [{:keys [diff lo hi]}] (format "%s [%s %s]%s" (f3 diff) (f3 lo) (f3 hi) (if (pos? lo) "*" " ")))
        header (str (format "Anticipation: each model against %s (out of sample).\n" bench)
                    "Recall in the top 5% and 10% of each quarter and Sarlin usefulness (mu 0.9), each with its gain over\n"
                    "history and the gain's 90% interval resampling years (* when it excludes zero); matched: the share\n"
                    "of onsets ranked above the four non-onsets of their quarter that history rates closest (0.5 = no more\n"
                    "than history). hard: no onset of the kind in the ten years before.\n")
        ;; each block (kind, window, era, hard or all) on a thread of its own,
        ;; printed in order as they finish
        blocks (for [[[kind w] by-model] (sort-by key preds)
                     :let [b (get by-model bench)]
                     :when b
                     [era lo hi] eras
                     hard? [false true]]
                 (future
                   (let [in (filter (fn [[unit q]] (and (<= lo (quot q 4) hi)
                                                        (or (not hard?)
                                                            (onsets/hard-row? (get-in events [kind unit] (sorted-set)) q))))
                                    (keys b))]
                     (with-out-str
                       (println (format "%s %s %s%s" kind w era (if hard? " hard" "")))
                       (doseq [[m rows] (sort-by key by-model)
                               :when (not= m bench)
                               :let [s (line-for rows b in)]
                               :when s]
                         (println (format "  %-8s n=%6d pos=%4d  r@5%% %.3f %s  r@10%% %.3f %s  U %.3f %s  matched %.2f"
                                          m (:n s) (:pos s) (double (:r5 s)) (gain (:r5-gain s)) (double (:r10 s)) (gain (:r10-gain s))
                                          (double (:u s)) (gain (:u-gain s)) (double (or (:matched s) 0.5)))))))))
        out (StringBuilder. header)
        _ (print header)
        _ (doseq [blk (doall blocks)]
            (let [s @blk] (print s) (flush) (.append out s)))
        out (str out)
        file (if (= bench default-bench) "anticipation.txt" (str "anticipation-vs-" bench ".txt"))]
    (print out)
    (spit (str dir "/" file) out)))
