(ns bnm.econ-structure-run
  "Writes data/derived/stocks/econ-structure.csv from bnm.econ-structure over
  scripts/econ-structure.py's data/derived/econ/structure-inputs.csv (bead
  bnm-3ka.1), and prints spot checks.

  Columns iso,year,variable,value,known,source: year is the data year,
  known the first year the value could be read (unlike the other stock
  tables, whose year is the year known), source the benchmark, the spliced
  segment (\"lrwiod*oecd\") or the bare spine segment.

    python -I scripts/econ-structure.py
    jolt -A:tools run -m bnm.econ-structure-run"
  (:require [bnm.csv :as csv]
            [bnm.econ-structure :as es]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def in-path "data/derived/econ/structure-inputs.csv")
(def out-path "data/derived/stocks/econ-structure.csv")

(defn- read-inputs [path]
  (with-open [r (io/reader path)]
    (let [[header & rows] (csv/records r \,)
          ix (zipmap header (range))
          num (fn [row k] (let [s (nth row (ix k))] (when-not (str/blank? s) (parse-double s))))]
      (mapv (fn [row]
              {:iso (nth row (ix "iso")) :year (parse-long (nth row (ix "year"))) :source (nth row (ix "source"))
               :fin (num row "fin") :man (num row "man")})
            rows))))

(defn -main [& _]
  (let [rows (read-inputs in-path)
        out (vec (es/series rows))]
    (with-open [w (io/writer out-path)]
      (.write w "iso,year,variable,value,known,source\n")
      (doseq [{:keys [iso year variable value known source]} out]
        (.write w (str iso "," year "," variable "," value "," known "," source "\n"))))
    (println (count rows) "input rows," (count out) "rows to" out-path)
    (println "sources:" (into (sorted-map) (frequencies (map :source out))))
    (doseq [iso ["USA" "GBR" "DEU" "JPN" "KOR" "CHN" "FRA"]
            :let [g (into {} (for [r out :when (= iso (:iso r))] [[(:variable r) (:year r)] r]))]]
      (println iso
               (str/join " " (for [y [1965 1980 1995 2000 2014 2020]
                                   :let [f (g ["econ/finance-va-share" y]) m (g ["econ/manufacturing-va-share" y])]
                                   :when f]
                               (format "%d fin %.1f%% man %.1f%% (%s, k%d)" y (* 100 (:value f))
                                       (* 100 (or (:value m) Double/NaN)) (:source f) (:known f))))))))
