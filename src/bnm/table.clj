(ns bnm.table
  "Plain CSV tables (no quoted fields) as the pipeline reads and writes
  them: stocks, value tables, monthly series."
  (:require [bnm.time :as t]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn write-csv!
  "Writes `header` then (line row) for each of `rows` to `path`, making its
  parent directories; `header` and each line without the newline."
  [path header line rows]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w (str header "\n"))
    (doseq [r rows]
      (.write w (str (line r) "\n")))))

(defn write-stock
  "Stock rows as iso,year,variable,value,known,data_year, the value to six
  decimals."
  [rows path]
  (write-csv! path "iso,year,variable,value,known,data_year"
              (fn [{:keys [iso year variable value known data-year]}]
                (str iso "," year "," variable "," (format "%.6f" (double value)) "," known "," data-year))
              rows))

(defn write-values
  "Rows as iso,year,variable,value, the value as it prints."
  [path rows]
  (write-csv! path "iso,year,variable,value"
              (fn [{:keys [iso year variable value]}] (str iso "," year "," variable "," value))
              rows))

(defn read-csv-maps
  "Rows of a plain CSV (no quoted commas) as maps keyed by the header."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r) ks (mapv keyword (str/split h #","))]
      (mapv #(zipmap ks (str/split % #"," -1)) ls))))

(defn read-monthly
  "{month value} (bnm.time/month-index) of a CSV with a header, an ISO date
  in the first column and the value in column `col` (default 1); rows
  without a value or a YYYY-MM date are left out."
  ([path] (read-monthly path 1))
  ([path col]
   (with-open [r (io/reader path)]
     (into {} (for [line (rest (line-seq r))
                    :let [cells (str/split line #",")
                          v (some-> (get cells col) str/trim not-empty parse-double)
                          d (first cells)]
                    :when (and v (re-matches #"\d{4}-\d{2}.*" d))]
                [(t/month-index d) v])))))

(defn read-known-panel
  "{key {iso (sorted-map year [known value])}} of a CSV with iso, known and
  value columns, `key-col` naming the outer key's column and `year-col`
  the year's."
  [path key-col year-col]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (reduce (fn [acc l]
                (let [f (str/split l #"," -1) g #(nth f (ix %))]
                  (update-in acc [(g key-col) (g "iso")] (fnil assoc (sorted-map))
                             (parse-long (g year-col)) [(parse-long (g "known")) (parse-double (g "value"))])))
              {} ls))))
