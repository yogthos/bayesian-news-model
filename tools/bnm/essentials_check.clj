(ns bnm.essentials-check
  "The registered rise targets of docs/ESSENTIALS.md section 8 step 2 on
  BACI HS92 (data/derived/supply/hs6-world.csv, scripts/baci.py):

    lithium hydroxide (282520) in the top 1% by data year 2018
    lithium carbonate (283691) in the top 1% by data year 2019
    cobalt oxides (282200) in the top 1% in data year 2018

  Prints the ranks of the listed codes for 2008-2024 (section 6's table)
  and whether each target is met; exits 1 if one is not.

    jolt -A:tools -M -m bnm.essentials-check [path] [edition]"
  (:require [bnm.essentials :as ess]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def codes
  [["282520" "lithium hydroxide"]
   ["283691" "lithium carbonate"]
   ["282200" "cobalt oxides"]
   ["283324" "nickel sulphate"]
   ["850780" "accumulators n.e.s. (Li-ion)"]
   ["870390" "cars n.e.s. (EV, hybrid)"]
   ["871190" "motorcycles n.e.s. (e-bike)"]
   ["850511" "permanent magnets"]
   ["284690" "rare-earth compounds"]
   ["280461" "polysilicon"]
   ["854140" "PV cells and LEDs"]
   ["720310" "direct-reduced iron"]
   ["750110" "nickel matte"]
   ["870332" "diesel cars 1.5-2.5 l"]])

(def targets
  "[code name rule year]: :by, top 1% in some data year up to `year`; :in,
  top 1% in `year` itself."
  [["282520" "lithium hydroxide" :by 2018]
   ["283691" "lithium carbonate" :by 2019]
   ["282200" "cobalt oxides" :in 2018]])

(defn- num [s] (when-not (str/blank? s) (Double/parseDouble s)))

(defn read-world
  "The rows of one `edition` of hs6-world.csv as {:hs :year :known :hs6
  :share :tonnes :value}."
  [path edition]
  (with-open [r (io/reader path)]
    (let [[header & lines] (line-seq r)
          ks (mapv keyword (str/split header #","))
          prefix (str edition ",")]
      (->> lines
           (filter #(str/starts-with? % prefix))
           (mapv (fn [line]
                   (let [m (zipmap ks (str/split line #"," -1))]
                     {:hs (:hs m) :hs6 (:hs6 m)
                      :year (Long/parseLong (:year m)) :known (Long/parseLong (:known m))
                      :value (num (:value m)) :tonnes (num (:tonnes m))
                      :share (num (:share m))})))))))

(defn- met? [ranks [code _ rule year]]
  (let [pct (fn [y] (:pct (get-in ranks [y code])))
        ok (fn [y] (when-let [p (pct y)] (<= p 0.01)))]
    (case rule
      :in (ok year)
      :by (some ok (filter #(<= % year) (keys ranks))))))

(defn -main [& [path edition]]
  (let [path (or path "data/derived/supply/hs6-world.csv")
        edition (or edition "HS92")
        rows (read-world path edition)
        years (range 2008 2025)
        ranks (into {} (for [y years]
                         [y (into {} (map (juxt :hs6 identity)) (ess/rise rows y))]))
        cell (fn [y code] (if-let [r (:rank (get-in ranks [y code]))] (str r) "-"))]
    (println (format "%s rows from %s; ranked per year: %s" edition path
                     (str/join " " (for [y years] (str y ":" (:n (first (vals (ranks y)))))))))
    (println (str/join "\t" (concat ["code" "product"] years)))
    (doseq [[code name] codes]
      (println (str/join "\t" (concat [code name] (map #(cell % code) years)))))
    (println)
    (let [results (for [[code name rule year :as t] targets]
                    (let [ok (met? ranks t)]
                      (println (format "%s %s top 1%% %s %d: %s (ranks %s)"
                                       code name (clojure.core/name rule) year
                                       (if ok "met" "NOT met")
                                       (str/join " " (for [y (range 2014 (inc year))]
                                                       (str y ":" (cell y code) "/" (:n (get-in ranks [y code])))))))
                      ok))]
      (System/exit (if (every? identity (doall results)) 0 1)))))
