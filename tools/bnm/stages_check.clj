(ns bnm.stages-check
  "Validation report for the stage stocks (beads bnm-1pq.4, bnm-3ka.3)
  against the targets in docs/ECONOMIC-CHARACTER.md 7d and
  docs/ESSENTIALS.md sections 5 and 8 (step 3).

    jolt -A:tools -M -m bnm.stages-check

  Reads data/derived/supply/stages.csv (chosen sources), stages-alt.csv
  (USGS MYB, cross-check only), stage-plants.csv and the written stocks
  data/derived/stocks/stages.csv. Shares here are by data year, from every
  row of that year (the current vintage); the stocks carry the same values
  at the year known."
  (:require [bnm.stages :as stg]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- by-year
  "{iso quantity} of commodity c, stage s, data year y."
  [rows c s y]
  (reduce (fn [m r] (if (and (= c (:commodity r)) (= s (:stage r)) (= y (:year r)))
                      (update m (:iso r) (fnil + 0.0) (:quantity r))
                      m))
          {} rows))

(defn- share [rows c s y iso] (get (stg/shares (by-year rows c s y)) iso))

(defn- d [rows c a b y]
  (let [sa (stg/shares (by-year rows c a y)) sb (stg/shares (by-year rows c b y))]
    (when (and (seq sa) (seq sb)) (stg/displacement sa sb))))

(defn- f2 [x] (if x (format "%.2f" (double x)) "  - "))

(def results (atom []))

(defn- target [label ok? detail]
  (swap! results conj [label ok?])
  (println (format "  [%s] %s: %s" (if ok? "met" "NOT MET") label detail)))

(defn- within [x lo hi] (and x (<= lo x hi)))

(defn -main [& _]
  (let [rows (stg/read-rows stg/in-path)
        alt (when (.exists (io/file "data/derived/supply/stages-alt.csv")) (stg/read-rows "data/derived/supply/stages-alt.csv"))
        plants (when (.exists (io/file stg/plants-path)) (stg/read-rows stg/plants-path))
        years [1975 1980 1985 1990 1995 2000 2005 2010 2013 2015 2017 2020 2022 2023 2024]]
    (println "Stage table:" (count rows) "rows;" (count (distinct (map (juxt :commodity :stage) rows))) "commodity-stages;"
             "alternative (MYB)" (count alt) "rows; plants" (count plants))
    (println "Sources per commodity-stage:")
    (doseq [[[c s] rs] (sort (group-by (juxt :commodity :stage) rows))]
      (println (format "  %-12s %-9s %-6s %4d-%4d  %-16s known +%s"
                       c s (str/replace (:source (first rs)) #"\d+$" "")
                       (apply min (map :year rs)) (apply max (map :year rs)) (:unit (first rs))
                       (str/join "/" (sort (distinct (map #(- (:known %) (:year %)) rs)))))))

    (println "\nUnits and causality")
    (target "one unit per commodity-stage" (nil? (stg/check-units rows)) "checked")
    (target "every row known after its data year" (every? #(> (:known %) (:year %)) rows)
            (str (count (remove #(> (:known %) (:year %)) rows)) " rows not"))
    (let [out (stg/stocks rows)
          sy (filter #(str/ends-with? (:variable %) "-state-year") out)]
      (target "every stock at or after its data are known (BGS lag 2, MCS edition year)"
              (every? #(>= (:year %) (+ 1 (long (:value %)))) sy)
              (str (count sy) " state-year rows; min lag "
                   (apply min (map #(- (:year %) (long (:value %))) sy)) " years")))

    (println "\nDisplacement D by data year (mine against the last stage)")
    (println (str "  " (format "%-22s" "") (str/join " " (map #(format "%5d" %) years))))
    (doseq [[c a b] [["cobalt" "mine" "refinery"] ["copper" "mine" "refinery"] ["nickel" "mine" "refinery"]
                     ["tin" "mine" "smelter"] ["zinc" "mine" "smelter"] ["aluminium" "mine" "refinery"]
                     ["aluminium" "mine" "smelter"] ["iron" "mine" "refinery"] ["lead" "mine" "refinery"]
                     ["magnesium" "mine" "smelter"]]]
      (println (str "  " (format "%-22s" (str c " " a "->" b)) (str/join " " (map #(format "%5s" (f2 (d rows c a b %))) years)))))

    (println "\nTargets")
    (let [d75 (d rows "cobalt" "mine" "refinery" 1975) d23 (d rows "cobalt" "mine" "refinery" 2023)]
      (target "cobalt D about 0.2 in 1975, about 0.9 in 2023 (7d: 0.21, 0.90)"
              (and (within d75 0.15 0.27) (within d23 0.85 0.95))
              (str (f2 d75) " -> " (f2 d23))))
    (let [s22 (share rows "cobalt" "refinery" 2022 "CHN") s23 (share rows "cobalt" "refinery" 2023 "CHN")
          s24 (share rows "cobalt" "refinery" 2024 "CHN") m22 (when alt (share alt "cobalt" "refinery" 2022 "CHN"))]
      (target "China's refined cobalt share about 80% (2022-24, within 0.05)"
              (every? #(within % 0.75 0.85) [s22 s23 s24])
              (str "BGS 2022 " (f2 s22) ", 2023 " (f2 s23) ", 2024 " (f2 s24) "; MYB 2022 " (f2 m22))))
    (let [d80 (d rows "copper" "mine" "refinery" 1980) d23 (d rows "copper" "mine" "refinery" 2023)]
      (target "copper D 0.31 (1980) -> 0.55 (2023), within 0.05"
              (and (within d80 0.26 0.36) (within d23 0.50 0.60))
              (str (f2 d80) " -> " (f2 d23))))
    (let [ds (into (sorted-map) (for [y (range 2008 2025)] [y (d rows "nickel" "mine" "refinery" y)]))
          peak (apply max (keep ds (range 2008 2014)))
          d23 (ds 2023)]
      (target "nickel D falls after Indonesia's ore export bans (2014, 2020): 2023 below the 2008-13 peak by 0.05+"
              (and d23 (< d23 (- peak 0.05)))
              (str "peak " (f2 peak) "; " (str/join ", " (for [[y v] ds :when (#{2013 2014 2016 2019 2020 2021 2022 2023 2024} y)] (str y " " (f2 v))))))
      (println (str "        Indonesia mine/refinery share: "
                    (str/join ", " (for [y [2010 2013 2014 2016 2019 2020 2023 2024]]
                                     (str y " " (f2 (share rows "nickel" "mine" y "IDN")) "/" (f2 (share rows "nickel" "refinery" y "IDN"))))))))
    (let [ss (for [y (range 2000 2006)] [y (share rows "rare-earths" "mine" y "CHN")])
          ms (when alt (for [y (range 2000 2006)] [y (share alt "rare-earths" "mine" y "CHN")]))]
      (target "China's rare-earth mine share 93-97% in 2000-05, within 0.02 (ESSENTIALS: 0.97 in 2005)"
              (and (every? #(within (second %) 0.91 0.99) ss) (within (second (last ss)) 0.95 0.99))
              (str "BGS " (str/join ", " (map (fn [[y v]] (str y " " (f2 v))) ss))
                   (when ms (str "; MYB " (str/join ", " (map (fn [[y v]] (str y " " (f2 v))) ms))))))
      (when (some #(< (second %) 0.93) ss)
        (println "        strictly, below the 93% floor in"
                 (str/join ", " (for [[y v] ss :when (< v 0.93)] y)) "(BGS; MYB lower still in 2000-01)")))
    (let [c22 (share rows "cobalt" "mine" 2022 "COD") m22 (when alt (share alt "cobalt" "mine" 2022 "COD"))]
      (target "Congo's 2022 share of mined cobalt 0.73 (ESSENTIALS step 3, MYB; within 0.04 across sources)"
              (within c22 0.69 0.77) (str "BGS " (f2 c22) "; MYB " (f2 m22))))
    (let [g22 (share rows "gallium" "refinery" 2022 "CHN") m22 (when alt (share alt "gallium" "refinery" 2022 "CHN"))]
      (target "China's primary gallium 2022 0.98 (within 0.02)" (within g22 0.96 1.0)
              (str "BGS " (f2 g22) "; MYB " (f2 m22))))
    (let [hs (for [y [2000 2010 2022]]
               (let [sh (stg/shares (by-year rows "graphite" "mine" y))] [y (stg/hhi sh) (get sh "CHN")]))
          ms (when alt (for [y [2000 2010 2022]]
                         (let [sh (stg/shares (by-year alt "graphite" "mine" y))] [y (stg/hhi sh) (get sh "CHN")])))]
      (println (str "  natural graphite HHI (China): BGS "
                    (str/join ", " (map (fn [[y h c]] (str y " " (f2 h) " (" (f2 c) ")")) hs))
                    "; MYB deduplicated " (str/join ", " (map (fn [[y h c]] (str y " " (f2 h) " (" (f2 c) ")")) ms))
                    "; ESSENTIALS section 5 table (MYB, details summed): 0.30 (0.51), 0.48 (0.67), 0.69 (0.83)")))

    (println "\nPlants (MYB vol. III 2024, no US): HHI by capacity, plants, coverage of the stage's output")
    (let [ps (stg/plant-stocks plants rows)
          key-of (fn [r] (let [[_ c s] (re-matches #"stages/(.+?)-(mine|smelter|refinery|chemical)-plant-.*" (:variable r))] [c s]))
          country-hhi (fn [c s]
                        (let [rs (filter #(and (= c (:commodity %)) (= s (:stage %))) rows)]
                          (when (seq rs)
                            (stg/hhi (stg/shares (by-year rows c s (apply max (map :year rs))))))))]
      (doseq [[[c s] rs] (sort (group-by key-of ps))]
        (let [g (fn [suf] (some #(when (str/ends-with? (:variable %) suf) (:value %)) rs))]
          (println (format "  %-12s %-9s plant HHI %s  plants %3d  coverage %s  (country HHI %s)"
                           c s (f2 (g "-plant-hhi")) (long (or (g "-plant-count") 0)) (f2 (g "-plant-coverage"))
                           (f2 (country-hhi c s)))))))

    (let [n (count @results) ok (count (filter second @results))]
      (println (format "\n%d of %d targets met." ok n)))))
