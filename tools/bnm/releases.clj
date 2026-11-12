(ns bnm.releases
  "When each annual source was published (bnm-56c.23): the release table in
  resources/bnm/releases.tsv (source, release, published YYYY-MM, last data
  year, columns first published in it), which scripts/slow-normalize.py
  reads for PWT too. A source read at a fixed offset (Maddison and NMC at
  the data year less one) ran up to five years ahead of its releases; read
  through `read-year` it is read no later than published. Before a
  source's first tabulated release the fixed offset stands."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- month-of [s] (+ (* 12 (parse-long (subs s 0 4))) (dec (parse-long (subs s 5 7)))))

(defn read-releases
  "{source [{:release :published (month) :last-year :adds #{columns}}]},
  oldest first."
  ([] (read-releases (io/resource "bnm/releases.tsv")))
  ([src]
   (with-open [r (io/reader src)]
     (let [[header & rows] (line-seq r)
           ks (map keyword (str/split header #"\t"))]
       (->> rows
            (map #(zipmap ks (str/split % #"\t" -1)))
            (map (fn [{:keys [source release published last_year adds]}]
                   {:source source :release release :published (month-of published)
                    :last-year (parse-long last_year)
                    :adds (set (remove str/blank? (str/split (or adds "") #",")))}))
            (group-by :source)
            (into {} (map (fn [[s rs]] [s (vec (sort-by :published rs))]))))))))

(defn published
  "The latest data year `source` had published by month `t`, nil before
  its first tabulated release."
  [rels source t]
  (some->> (get rels source) (filter #(<= (:published %) t)) seq (map :last-year) (reduce max)))

(defn introduced
  "The month the release that first published `column` of `source` came
  out, nil when the table names none."
  [rels source column]
  (some #(when (contains? (:adds %) column) (:published %)) (get rels source)))

(defn read-year
  "The data year to read `source` at, at month `t`, where the fixed offset
  would read `y`: no later than the latest published."
  [rels source t y]
  (if-let [p (published rels source t)] (min y p) y))
