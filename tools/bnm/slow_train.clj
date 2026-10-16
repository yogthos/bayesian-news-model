(ns bnm.slow-train
  "Trains the slow layer on data/slow/ (see scripts/slow-normalize.py) month
  by month, each value fed in the month it became available, and reports how
  well each variable's changes were forecast out of sample: as no change,
  from its own history, and with the other domains and the global drivers.

    jolt -A:tools -M -m bnm.slow-train [--from 1960] [--to 2026] [--name slow] [--config EDN]

  Writes data/runs/<name>/slow-report.txt."
  (:require [bnm.slow :as slow]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-globals
  [["world" "energy/oil_brent"] ["world" "energy/gas_europe"] ["world" "food/price_index"]
   ["world" "commodities/all_index"] ["world" "commodities/copper"] ["world" "finance/vix"]
   ["world" "finance/usd_broad"] ["world" "climate/enso"] ["world" "climate/global_temperature"]
   ["world" "uncertainty/policy"] ["USA" "finance/policy_rate"] ["USA" "finance/curve_10y3m"]
   ["USA" "finance/spread_baa"] ["USA" "finance/conditions"] ["USA" "economy/industrial_production"]
   ["USA" "trade/freight_index"] ["world" "technology/research_ai"]])

(defn month-of
  "\"2008-09-30\" -> 2008 * 12 + 8"
  ^long [s]
  (+ (* 12 (parse-long (subs s 0 4))) (dec (parse-long (subs s 5 7)))))

(defn month-str [^long m] (format "%04d-%02d" (quot m 12) (inc (rem m 12))))

(defn read-variables [path]
  (with-open [r (io/reader path)]
    (vec (for [line (line-seq r)
               :let [[nm domain tr freq source desc] (str/split line #"\t" -1)]]
           {:name nm :domain domain :transform tr :freq freq :source source :description desc}))))

(defn read-observations
  "Observations grouped by the month they became available: {month [obs]}."
  [path from to]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[unit v _ pend avail value] (str/split line #"\t" -1)
                    a (month-of avail)]
                (if (<= from (quot a 12) to)
                  (update acc a (fnil conj []) {:unit unit :var v :period (month-of pend)
                                                :value (parse-double value)})
                  acc)))
            {} (line-seq r))))

(defn- pct [a b] (if (and a b (pos? b)) (format "%+6.1f%%" (* 100.0 (- 1.0 (/ a b)))) "    -  "))

(defn- driver-str [d]
  (let [s (:source d)
        src (if (vector? s) (str (first s) ":" (second s)) s)]
    (format "%s %s %+.2f" src (name (:feature d)) (double (:beta d)))))

(defn report [mdl variables]
  (with-out-str
    (println "Out-of-sample forecasts of each variable's next change, scaled by its own history:")
    (println "mean squared error as no change, from its own history, with other domains and globals, and the")
    (println "two averaged by their records; the average's gain over own history, the weight it now gives the")
    (println "full model, and the strongest outside drivers")
    (doseq [[domain vs] (sort-by key (group-by :domain variables))]
      (println)
      (println domain)
      (doseq [{nm :name} (sort-by :name vs)
              :let [sk (slow/skill mdl nm)]
              :when sk]
        (println (format "  %-42s n=%7d  zero %.3f  own %.3f  full %.3f  dma %.3f  gain %s  w %.2f   %s"
                         nm (:n sk) (:zero sk) (:own sk) (:full sk) (:dma sk) (pct (:dma sk) (:own sk)) (:w-full sk)
                         (str/join ", " (map driver-str (take 3 (slow/drivers mdl nm))))))))))

(defn run [{:keys [from to name config]}]
  (let [from (parse-long (or from "1950"))
        to (parse-long (or to "2026"))
        run-name (or name "slow")
        dir (str "data/runs/" run-name)
        cfg (merge {:globals default-globals} (some-> config edn/read-string))
        variables (read-variables "data/slow/variables.tsv")
        t0 (System/currentTimeMillis)
        by-month (read-observations "data/slow/series.tsv" from to)
        _ (println (count by-month) "months of observations read in" (- (System/currentTimeMillis) t0) "ms")
        months (range (apply min (keys by-month)) (inc (apply max (keys by-month))))
        mdl (reduce (fn [mdl t]
                      (when (zero? (rem t 120)) (println (month-str t) (- (System/currentTimeMillis) t0) "ms") (flush))
                      (slow/step! mdl t (get by-month t [])))
                    (slow/create cfg variables)
                    months)
        text (report mdl variables)]
    (io/make-parents (str dir "/x"))
    (spit (str dir "/slow-report.txt") text)
    (println text)
    mdl))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
