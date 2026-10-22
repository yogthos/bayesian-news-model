(ns bnm.material
  "The material base's located assets (docs/STOCK-FLOW.md, bead
  bnm-62z.20) on equal-area A5 cells (dodeca): power plants, and later
  mines, fields and refineries, indexed where they stand rather than by
  province, so density and concentration are measured on the ground.
  Writes data/derived/material/plant-cells.csv and concentration.csv.

  The plants are the WRI Global Power Plant Database v1.3 (CC BY 4.0), a
  snapshot of capacity data mostly from 2017-2019, published in 2021: it
  has no retired plants and no history, so both files carry its vintage
  and are not to be read causally before then (fuel split and
  concentration at the snapshot only).

    jolt -A:tools -M -m bnm.material [--resolution 6]"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [dodeca.core :as a5]))

(def vintage
  "The year the plant database became known (v1.3, June 2021)."
  2021)

(def ^:private iso-alias
  "Country codes outside ISO 3166 written one way across sources."
  {"KOS" "XKX" "XKS" "XKX"})

(defn cells
  "[{:cell :iso :mw :fuels {fuel mw} :plants {fuel n}}] of `plants` ({:iso
  :fuel :mw :lon :lat}) on A5 cells at `resolution` (6: about 8,300 km2).
  A cell across a border is one entry per country."
  [plants resolution]
  (->> plants
       (group-by (fn [{:keys [iso lon lat]}] [iso (a5/lon-lat->cell [lon lat] resolution)]))
       (map (fn [[[iso cell] ps]]
              {:cell (a5/cell->hex cell) :iso iso
               :mw (reduce + 0.0 (map :mw ps))
               :fuels (reduce (fn [m {:keys [fuel mw]}] (update m fuel (fnil + 0.0) mw)) {} ps)
               :plants (reduce (fn [m {:keys [fuel mw]}] (if (pos? mw) (update m fuel (fnil inc 0)) m)) {} ps)}))
       (sort-by (juxt :iso :cell))
       vec))

(defn concentration
  "{iso {fuel {:hhi :hhi-norm :top-share :cells :plants :mw}}}: each
  country's capacity of each fuel across the cells that hold some, its
  Herfindahl index (1 all in one cell), the index normalised for the
  number of cells N, (H - 1/N)/(1 - 1/N) (0 spread evenly; 1 when N = 1),
  the largest cell's share, and the plant count (a country with one plant
  has H = 1 by construction)."
  [cells]
  (into {} (for [[iso cs] (group-by :iso cells)]
             [iso (into {} (for [fuel (distinct (mapcat (comp keys :fuels) cs))
                                 :let [mws (filter pos? (keep #(get-in % [:fuels fuel]) cs))
                                       total (reduce + 0.0 mws)]
                                 :when (pos? total)
                                 :let [shares (map #(/ % total) mws)
                                       n (count mws)
                                       hhi (reduce + (map #(* % %) shares))]]
                             [fuel {:hhi hhi
                                    :hhi-norm (if (= 1 n) 1.0 (/ (- hhi (/ 1.0 n)) (- 1.0 (/ 1.0 n))))
                                    :top-share (apply max shares)
                                    :cells n
                                    :plants (reduce + (keep #(get-in % [:plants fuel]) cs))
                                    :mw total}]))])))

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
  "The WRI Global Power Plant Database's plants with coordinates and a
  positive capacity: [{:iso :fuel :mw :lon :lat :year}], the commissioning
  year rounded (some are capacity-weighted means of units), Kosovo as XKX."
  [path]
  (with-open [r (io/reader path)]
    (let [[head & rows] (line-seq r)
          ix (zipmap (split-csv head) (range))]
      (vec (for [l rows :let [f (split-csv l) g #(nth f (ix %) "")
                              mw (some-> (not-empty (g "capacity_mw")) parse-double)
                              lon (some-> (not-empty (g "longitude")) parse-double)
                              lat (some-> (not-empty (g "latitude")) parse-double)]
                 :when (and mw (pos? mw) lon lat)]
             {:iso (get iso-alias (g "country") (g "country"))
              :fuel (str/lower-case (g "primary_fuel")) :mw mw :lon lon :lat lat
              :year (some-> (not-empty (g "commissioning_year")) parse-double Math/round long)})))))

(defn -main [& args]
  (let [{:keys [resolution]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        res (or (some-> resolution parse-long) 6)
        ps (read-plants "data/raw/material/gppd/x/global_power_plant_database.csv")
        cs (cells ps res)
        conc (concentration cs)]
    (with-open [w (io/writer "data/derived/material/plant-cells.csv")]
      (.write w "cell,iso,fuel,mw,plants,vintage\n")
      (doseq [{:keys [cell iso fuels plants]} cs [fuel mw] (sort fuels) :when (pos? mw)]
        (.write w (str cell "," iso "," fuel "," mw "," (get plants fuel 0) "," vintage "\n"))))
    (with-open [w (io/writer "data/derived/material/concentration.csv")]
      (.write w "iso,fuel,cells,plants,mw,hhi,hhi_norm,top_share,vintage\n")
      (doseq [[iso fs] (sort conc) [fuel {:keys [cells plants mw hhi hhi-norm top-share]}] (sort fs)]
        (.write w (str iso "," fuel "," cells "," plants "," mw "," hhi "," hhi-norm "," top-share "," vintage "\n"))))
    (println (count ps) "plants," (count cs) "cells")))
