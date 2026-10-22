(ns bnm.material
  "The material base's located assets (docs/STOCK-FLOW.md, bead
  bnm-62z.19-.20) on equal-area A5 cells (dodeca): power plants, and later
  mines, fields and refineries, indexed where they stand rather than by
  province, so density and concentration are measured on the ground.
  Writes data/derived/material/plant-cells.csv and concentration.csv.

    jolt -A:tools -M -m bnm.material [--resolution 6]"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [dodeca.core :as a5]))

(defn cells
  "[{:cell :iso :mw :fuels {fuel mw}}] of `plants` ({:iso :fuel :mw :lon
  :lat}) on A5 cells at `resolution` (6: about 8,300 km2)."
  [plants resolution]
  (->> plants
       (group-by (fn [{:keys [iso lon lat]}] [iso (a5/lon-lat->cell [lon lat] resolution)]))
       (map (fn [[[iso cell] ps]]
              {:cell (a5/cell->hex cell) :iso iso
               :mw (reduce + 0.0 (map :mw ps))
               :fuels (reduce (fn [m {:keys [fuel mw]}] (update m fuel (fnil + 0.0) mw)) {} ps)}))
       (sort-by (juxt :iso :cell))
       vec))

(defn concentration
  "{iso {fuel {:hhi :top-share :cells :mw}}}: each country's capacity of
  each fuel across its cells, its Herfindahl index (1 all in one cell)
  and the largest cell's share."
  [cells]
  (into {} (for [[iso cs] (group-by :iso cells)]
             [iso (into {} (for [fuel (distinct (mapcat (comp keys :fuels) cs))
                                 :let [mws (keep #(get-in % [:fuels fuel]) cs)
                                       total (reduce + mws)
                                       shares (map #(/ % total) mws)]
                                 :when (pos? total)]
                             [fuel {:hhi (reduce + (map #(* % %) shares)) :top-share (apply max shares)
                                    :cells (count mws) :mw total}]))])))

(defn split-csv
  "The fields of one CSV line: commas inside quotes kept, doubled quotes
  read as one."
  [line]
  (loop [cs (seq line) field (StringBuilder.) out [] quoted false]
    (if-let [c (first cs)]
      (cond (and quoted (= c \") (= (second cs) \")) (recur (nnext cs) (.append field \") out true)
            (= c \") (recur (next cs) field out (not quoted))
            (and (= c \,) (not quoted)) (recur (next cs) (StringBuilder.) (conj out (str field)) false)
            :else (recur (next cs) (.append field c) out quoted))
      (conj out (str field)))))

(defn read-plants
  "The WRI Global Power Plant Database's plants: [{:iso :fuel :mw :lon :lat
  :year}]."
  [path]
  (with-open [r (io/reader path)]
    (let [[head & rows] (line-seq r)
          ix (zipmap (split-csv head) (range))]
      (vec (for [l rows :let [f (split-csv l) g #(nth f (ix %) "")]
                 :when (and (seq (g "latitude")) (seq (g "capacity_mw")))]
             {:iso (g "country") :fuel (str/lower-case (g "primary_fuel")) :mw (parse-double (g "capacity_mw"))
              :lon (parse-double (g "longitude")) :lat (parse-double (g "latitude"))
              :year (some-> (not-empty (g "commissioning_year")) parse-double long)})))))

(defn -main [& args]
  (let [{:keys [resolution]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        res (or (some-> resolution parse-long) 6)
        ps (read-plants "data/raw/material/gppd/x/global_power_plant_database.csv")
        cs (cells ps res)
        conc (concentration cs)]
    (with-open [w (io/writer "data/derived/material/plant-cells.csv")]
      (.write w "cell,iso,fuel,mw\n")
      (doseq [{:keys [cell iso fuels]} cs [fuel mw] (sort fuels)] (.write w (str cell "," iso "," fuel "," mw "\n"))))
    (with-open [w (io/writer "data/derived/material/concentration.csv")]
      (.write w "iso,fuel,cells,mw,hhi,top_share\n")
      (doseq [[iso fs] (sort conc) [fuel {:keys [cells mw hhi top-share]}] (sort fs)]
        (.write w (str iso "," fuel "," cells "," mw "," hhi "," top-share "\n"))))
    (println (count ps) "plants," (count cs) "cells")))
