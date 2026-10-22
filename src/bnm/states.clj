(ns bnm.states
  "Stock-flow states as country-year tables (data/derived/stocks/*.csv,
  data/derived/material/*.csv: iso,year,variable,value, the year the value
  is known), read for the event lattice (bead bnm-62z.13): each state's own
  part at each country (bnm.factors), causal."
  (:require [bnm.factors :as fac]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn read-states
  "{variable {iso (sorted-map year value)}} from long tables
  iso,year,variable,value[,...]; empty values left out (unknown)."
  [paths]
  (reduce (fn [acc path]
            (with-open [r (io/reader path)]
              (let [[h & ls] (line-seq r)
                    ix (zipmap (str/split h #",") (range))]
                (reduce (fn [acc l]
                          (let [f (str/split l #"," -1)
                                g #(nth f (ix %) "")
                                v (g "value")]
                            (if (and (seq v) (seq (g "iso")))
                              (update-in acc [(g "variable") (g "iso")] (fnil assoc (sorted-map)) (parse-long (g "year")) (parse-double v))
                              acc)))
                        acc ls))))
          {} paths))

(defn own-parts
  "{variable {iso {year own part}}}: each state's own part at each country
  (bnm.factors), causal."
  [states tree]
  (into {} (pmap (fn [[v panel]]
                   (let [panel (into {} (filter (comp tree key) panel))
                         parts (when (> (count panel) 3) (fac/decompose panel tree {:discount 0.99}))]
                     [v (update-vals (or parts {}) #(update-vals % :own))]))
                 states)))

