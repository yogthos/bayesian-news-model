(ns bnm.reconcile
  "The quarter nodes' windows made coherent with the year node's forecast
  (docs/DOWNSCALE.md, step 3): at each quarter origin, the lead and w2
  windows and the first quarter (the year's hazard over its three months)
  are the parts of the next two years; their chance of any onset is pooled
  with the year's latest forecast for the place, issued before the origin,
  and every part's hazard scaled to meet it (bnm.lattice/reconcile).

    jolt -A:tools -M -m bnm.reconcile --run lattice-banking [--kind banking_crisis]

  Writes data/runs/<run>-rec/: forecasts.tsv (the run's, its country
  quarter rows reconciled; those with no year forecast before them left
  out) and preds.tsv (model lattice), scored like any run."
  (:require [bnm.lattice :as lt]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn reconciled
  "The reconciled quarter rows of `rows` ({:scale :place :origin :p :y}),
  `horizons` each scale's window in months; rows with no year forecast
  before them are left out."
  [rows horizons]
  (let [years (into {} (for [[place rs] (group-by :place (filter #(= :year (:scale %)) rows))]
                         [place (into (sorted-map) (map (juxt :origin :p) rs))]))
        quarters (group-by (juxt :place :origin) (filter #(#{:quarter-lead :quarter-w2} (:scale %)) rows))
        first-q (/ 3.0 (:year horizons))]
    (for [[[place t] rs] quarters
          :let [by (into {} (map (juxt :scale identity) rs))
                lead (by :quarter-lead) w2 (by :quarter-w2)
                py (some-> (get years place) (rsubseq < t) first val)]
          :when (and lead w2 py)
          :let [r (- 1.0 (Math/pow (- 1.0 py) first-q))
                [a b] (lt/reconcile (:p lead) (:p w2) r py)]
          row [(assoc lead :p a) (assoc w2 :p b)]]
      row)))

(defn -main [& args]
  (let [{:keys [run kind]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        dir (str "data/runs/" run)
        out-dir (str dir "-rec")
        kind (or kind "banking_crisis")
        rows (with-open [r (io/reader (str dir "/forecasts.tsv"))]
               (vec (for [l (line-seq r)
                          :let [[s place o p y] (str/split l #"\t" -1)]]
                      {:scale (keyword s) :place place :origin (parse-long o) :p (parse-double p)
                       :y (case y "true" true "false" false nil)})))
        rec (reconciled (filter #(= 3 (count (:place %))) rows) {:quarter-lead 9 :quarter-w2 12 :year 24})
        quarter? #(#{:quarter-lead :quarter-w2} (:scale %))
        out (concat (remove #(and (quarter? %) (= 3 (count (:place %)))) rows) rec)]
    (io/make-parents (str out-dir "/x"))
    (with-open [w (io/writer (str out-dir "/forecasts.tsv"))]
      (doseq [{:keys [scale place origin p y]} (sort-by (juxt :origin :scale :place) out)]
        (.write w (str (name scale) "\t" place "\t" origin "\t" p "\t" (if (nil? y) "" y) "\n"))))
    (with-open [w (io/writer (str out-dir "/preds.tsv"))]
      (doseq [{:keys [scale place origin p y]} (sort-by (juxt :origin :place :scale) rec)]
        (.write w (str kind "\t" ({:quarter-lead "lead" :quarter-w2 "w2"} scale) "\tlattice\t" (quot (- origin 2) 3)
                       "\t" place "\t" p "\t" (if (nil? y) "nil" y) "\n"))))
    (println "wrote" (count rec) "reconciled rows to" out-dir)))
