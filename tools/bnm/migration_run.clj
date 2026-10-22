(ns bnm.migration-run
  "Writes data/derived/stocks/migration.csv (iso,year,variable,value; the
  year a value is known at) from bnm.migration over the EXIOBASE-product
  panel of scripts/migration.py, checks that its shares sum to one per
  product-year, and prints spot checks (bead bnm-3ka.2).

    jolt -A:tools -M -m bnm.migration-run"
  (:require [bnm.csv :as csv]
            [bnm.migration :as mig]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def in-path "data/derived/econ/migration.csv")
(def out-path "data/derived/stocks/migration.csv")

(defn- read-panel [path]
  (with-open [r (io/reader path)]
    (let [[header & rows] (csv/records r \,)
          ix (zipmap header (range))
          f (fn [row k] (nth row (ix k)))]
      (mapv (fn [row]
              {:product (f row "product") :year (parse-long (f row "year")) :known (parse-long (f row "known"))
               :exporter (f row "exporter") :value (parse-double (f row "value"))
               :share (parse-double (f row "share"))
               :cap-intensity (let [s (f row "cap_intensity")] (when-not (str/blank? s) (parse-double s)))
               :cap-class (let [s (f row "cap_class")] (when-not (str/blank? s) s))})
            rows))))

(defn -main [& _]
  (let [rows (read-panel in-path)
        sums (reduce (fn [m {:keys [product year share]}] (update m [product year] (fnil + 0.0) share)) {} rows)
        bad (filter #(> (Math/abs (- 1.0 (val %))) 1e-6) sums)
        _ (when (seq bad) (throw (ex-info "shares do not sum to 1" {:examples (take 5 bad)})))
        a (mig/country-years rows)
        years (range (apply min (map :known rows)) (inc (apply max (map :known rows))))
        out (mig/stock-rows {:aggregates a} years)]
    (println (count rows) "panel rows," (count sums) "product-years, shares sum to 1")
    (with-open [w (io/writer out-path)]
      (.write w "iso,year,variable,value\n")
      (doseq [{:keys [iso year variable value]} out]
        (.write w (str iso "," year "," variable "," value "\n"))))
    (println (count out) "rows to" out-path)
    (doseq [iso ["USA" "GBR" "DEU" "JPN" "CHN" "KOR" "TWN"]
            y [1997 2007 2017 2026]
            :let [g (into {} (for [r out :when (and (= iso (:iso r)) (= y (:year r)))] [(:variable r) (:value r)]))]
            :when (seq g)]
      (println iso y (format "ci-share %.3f d10 %s rca %.2f cap-intensity %.4f"
                             (g "migration/ci-share") (some->> (g "migration/ci-share-d10") (format "%.3f"))
                             (g "migration/ci-rca") (g "migration/cap-intensity"))))))
