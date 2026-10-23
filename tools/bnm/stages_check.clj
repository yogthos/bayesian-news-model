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
  (:require [bnm.csv :as csv]
            [bnm.stages :as stg]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- by-year
  "{iso quantity} of commodity c, stage s, data year y: its latest edition."
  [rows c s y]
  (reduce (fn [m r] (update m (:iso r) (fnil + 0.0) (:quantity r)))
          {} (stg/vintage (filter #(and (= c (:commodity %)) (= s (:stage %)) (= y (:year %))) rows)
                          Long/MAX_VALUE)))

(defn- by-year-sum
  "{iso quantity} of commodity c, stage s, data year y, every row summed:
  stages-alt.csv, one row per country-year, each from the latest MYB
  edition that reports that country (editions differ by country)."
  [rows c s y]
  (reduce (fn [m r] (if (and (= c (:commodity r)) (= s (:stage r)) (= y (:year r)))
                      (update m (:iso r) (fnil + 0.0) (:quantity r))
                      m))
          {} rows))

(defn- share [rows c s y iso] (get (stg/shares (by-year rows c s y)) iso))

(defn- alt-share [rows c s y iso] (get (stg/shares (by-year-sum rows c s y)) iso))

(defn- d [rows c a b y]
  (stg/displacement (by-year rows c a y) (by-year rows c b y)))

(defn- world-breaks
  "[[commodity stage year ratio]] where world output changes by a factor
  over 1.6 from one data year to the next: coverage or definition breaks."
  [rows]
  (for [[[c s] rs] (sort (group-by (juxt :commodity :stage) rows))
        :let [w (into (sorted-map) (for [y (distinct (map :year rs))]
                                     [y (reduce + 0.0 (vals (by-year rs c s y)))]))]
        [y q] w
        :let [q0 (get w (dec y))]
        :when (and q0 (pos? q0) (pos? q) (> (max (/ q q0) (/ q0 q)) 1.6))]
    [c s y (/ q q0)]))

(defn- f2 [x] (if x (format "%.2f" (double x)) "  - "))

(def results (atom []))

(defn- target [label ok? detail]
  (swap! results conj [label ok?])
  (println (format "  [%s] %s: %s" (if ok? "met" "NOT MET") label detail)))

(defn- within [x lo hi] (and x (<= lo x hi)))

;; ---------------------------------------------------------------- chemical stages

(def chem-path "data/derived/supply/stages-chem.csv")

(defn- pnum [s] (when (seq s) (parse-double s)))

(defn- chem-edition
  "{iso row} of a chemical stage's data year, latest edition; the quota
  alternative under :quota."
  [prov c y]
  (let [ps (filter #(and (= c (:commodity %)) (= (str y) (:year %))) prov)
        k (when (seq ps) (apply max (map #(parse-long (:known %)) ps)))]
    (into {} (for [p ps :when (= (str k) (:known p))]
               [(if (= "quota" (:method p)) :quota (:iso p)) p]))))

(defn- residual-line
  "China's balance split: its own mine output (direct) and the residual,
  the sum over the others of mine output less their own output (M - P).
  Positive terms are ore the others mine and do not process (bought by
  China, or stocked, or lost); negative ones are processing beyond their
  own ore (imported feed). Stock changes cannot be told apart from it."
  [rows ms c y]
  (let [chn (get ms "CHN")
        p (some-> chn :quantity pnum)
        m (or (some-> chn :mine pnum) 0.0)
        scale (if (= c "lithium") 5.323 1.0)
        mine (update-vals (dissoc (by-year rows c "mine" y) "CHN") #(* scale %))
        out (into {} (for [[iso r] (dissoc ms "CHN" :quota)] [iso (or (pnum (:quantity r)) 0.0)]))
        gap (into {} (for [iso (into (set (keys mine)) (keys out))]
                       [iso (- (get mine iso 0.0) (get out iso 0.0))]))
        pos (filter (comp pos? val) gap)]
    (when (and p (pos? p))
      (format "  %-12s %d  China %d kt: own mine %.2f, residual %.2f = others' unprocessed ore %.2f (%s) + processing beyond own ore %.2f"
              c y (Math/round (/ p 1000.0)) (/ m p) (/ (- p m) p)
              (/ (reduce + 0.0 (vals pos)) p)
              (str/join " " (for [[iso v] (take 4 (sort-by (comp - val) pos))] (format "%s %.2f" iso (/ v p))))
              (/ (reduce + 0.0 (filter neg? (vals gap))) p)))))

(defn- chemical-stages [rows]
  (println "\nChemical stages (bead bnm-1pq.15; built: trade + domestic use, brine producers at mine output, China closing the mass balance, 2025 from plant capacities)")
  (let [prov (when (.exists (io/file chem-path)) (vec (csv/read-maps chem-path \,)))
        yrs (fn [c s] (sort (distinct (map :year (filter #(and (= c (:commodity %)) (= s (:stage %))) rows)))))
        line (fn [c s y]
               (let [sh (stg/shares (by-year rows c s y))
                     ms (chem-edition prov c y)
                     m (when (seq sh) (stg/shares (by-year rows c "mine" y)))]
                 (format "  %-12s %d  %s  HHI %s  D %s  (%s)" c y
                         (str/join " " (for [[iso v] (take 4 (sort-by (comp - val) sh))] (str iso " " (f2 v))))
                         (f2 (stg/hhi sh)) (f2 (when (seq m) (stg/displacement m sh)))
                         (str/join "," (sort (distinct (keep :method (vals (dissoc ms :quota)))))))))]
    (doseq [[c s] [["lithium" "chemical"] ["rare-earths" "separation"]]
            y (yrs c s) :when (or (= c "lithium") (>= y 2015) (zero? (mod y 5)))]
      (println (line c s y)))
    (println "  China's balance is its own mine output plus a residual (latest edition):")
    (doseq [[c s] [["lithium" "chemical"] ["rare-earths" "separation"]]
            y (range 2018 2025)
            :let [l (residual-line rows (chem-edition prov c y) c y)] :when l]
      (println l))
    (let [ss (for [y (range 2020 2024)] [y (share rows "lithium" "chemical" y "CHN")])]
      (target "China's share of refined lithium chemicals 0.60-0.70 in 2020-23 (USGS/IEA)"
              (every? #(within (second %) 0.60 0.70) ss)
              (str (str/join ", " (map (fn [[y v]] (str y " " (f2 v))) ss))
                   "; Chile and Argentina at their mine output (brine). 2023: world mine output +39%, mostly"
                   " spodumene (Australia, Africa, Brazil) with no refiner on record but China's, so the"
                   " balance gives China that ore and any concentrate stocked in 2023")))
    (let [ss (for [y (range 2020 2024)] [y (share rows "rare-earths" "separation" y "CHN")])
          quota (for [y (range 2019 2025)
                      :let [ms (chem-edition prov "rare-earths" y)
                            q (some-> (:quota ms) :quantity pnum)
                            b (some-> (get ms "CHN") :quantity pnum)
                            others (reduce + 0.0 (vals (dissoc (by-year rows "rare-earths" "separation" y) "CHN")))]
                      :when (and q b)]
                  [y (/ q (+ q others)) (/ q b)])]
      (target "China's share of rare-earth separation 0.85-0.90 in 2020-23 (primary: the balance)"
              (every? #(within (second %) 0.85 0.90) ss)
              (str (str/join ", " (map (fn [[y v]] (str y " " (f2 v))) ss))
                   "; an upper bound (takes stock changes and losses)"))
      (println (str "        alternative, China at its MIIT separation quota (stage separation-quota; a lower bound: domestic ore only): "
                    (str/join ", " (map (fn [[y v r]] (str y " " (f2 v) " (quota/balance " (f2 r) ")")) quota)))))
    (let [ds (for [y (range 2020 2026)] [y (d rows "lithium" "mine" "chemical" y)])]
      (target "lithium D mine->chemical high (0.5+) in every year 2020-25: Australian spodumene refined in China"
              (every? #(and (second %) (>= (second %) 0.5)) ds)
              (str (str/join ", " (map (fn [[y v]] (str y " " (f2 v))) ds))
                   "; rare earths " (str/join ", " (for [y [2020 2022 2024]] (str y " " (f2 (d rows "rare-earths" "mine" "separation" y))))))))
    (let [pc (chem-edition prov "lithium" 2022) w (some-> (get pc "CHN") :world pnum)
          u (some-> (get pc "CHN") :domestic_use pnum)]
      (println (str "  China's implied domestic use of lithium chemicals (balance less its net exports), 2022: "
                    (f2 (when (and u w) (/ u w))) " of world use; no battery cell output by country is held to check it")))
    (println "  EU CRM 2023 factsheets (internal check only): \"approximately 85% of refined REEs are produced in China\";"
             "China \"has most of the world's refining capacity\" for lithium")
    (let [inc (->> prov
                   (filter #(and (= "1" (:incomplete %)) (= "CHN" (:iso %)) (= "balance" (:method %))))
                   (group-by (juxt :commodity :known))
                   (sort))]
      (println "  Editions whose plant list misses a producer a later list has (incomplete = 1; China reads high there,"
               "an artefact of when the lists were published, not a change in China):")
      (doseq [[[c k] ps] inc
              :let [ys (sort (map #(parse-long (:year %)) ps))]]
        (println (format "    %-12s edition %s: data years %s" c k
                         (if (> (count ys) 4) (str (first ys) "-" (last ys) " (" (count ys) ")") (str/join " " ys)))))
      (println "    e.g. rare-earth separation 2021 in the 2023 edition reads China"
               (f2 (get (stg/shares (reduce (fn [m r] (update m (:iso r) (fnil + 0.0) (:quantity r))) {}
                                            (filter #(and (= "rare-earths" (:commodity %)) (= "separation" (:stage %))
                                                          (= 2021 (:year %)) (= 2023 (:known %))) rows)))
                        "CHN"))
               "(no Malaysia until the 2024 list)"))))

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
                   (apply min (map #(- (:year %) (long (:value %))) sy)) " years"))
      (let [ds (filter #(re-find #"-(d|d-.+|non-resource)$" (:variable %)) out)]
        (target "D and the non-resource share in [0, 1]" (every? #(<= 0.0 (:value %) 1.0) ds)
                (str (count ds) " rows, " (f2 (apply min (map :value ds))) " to " (f2 (apply max (map :value ds)))))))

    (println "\nWorld output breaks (factor over 1.6 year on year; coverage or definition, not repaired unless noted)")
    (doseq [[c s y r] (world-breaks rows)]
      (println (format "  %-12s %-9s %d  x%.2f" c s y (double r))))

    (println "\nDisplacement D by data year (mine against the last stage)")
    (println (str "  " (format "%-22s" "") (str/join " " (map #(format "%5d" %) years))))
    (doseq [[c a b] [["cobalt" "mine" "refinery"] ["copper" "mine" "refinery"] ["nickel" "mine" "refinery"]
                     ["tin" "mine" "smelter"] ["zinc" "mine" "smelter"] ["aluminium" "mine" "refinery"]
                     ["aluminium" "mine" "smelter"] ["iron" "mine" "refinery"] ["lead" "mine" "refinery"]
                     ["magnesium" "mine" "smelter"]]]
      (println (str "  " (format "%-22s" (str c " " a "->" b)) (str/join " " (map #(format "%5s" (f2 (d rows c a b %))) years)))))

    (println "\nTargets")
    (let [d75 (d rows "cobalt" "mine" "refinery" 1975) d23 (d rows "cobalt" "mine" "refinery" 2023)
          d76 (/ (reduce + (map #(d rows "cobalt" "mine" "refinery" %) (range 1976 1981))) 5)]
      ;; 7d's 0.21 for 1975 came from BGS's 1972-75 Zaire and Zambia ore
      ;; content (twice their metal); with those years repaired 1975 is in
      ;; line with 1976-80
      (target "cobalt D in 1975 within 0.05 of its 1976-80 mean (no break), 0.85-0.95 in 2023 (7d: 0.90)"
              (and d75 (< (Math/abs (- d75 d76)) 0.05) (within d23 0.85 0.95))
              (str (f2 d75) " (1976-80 " (f2 d76) ") -> " (f2 d23) "; 7d's 0.21 was before the 1972-75 repair")))
    (let [s22 (share rows "cobalt" "refinery" 2022 "CHN") s23 (share rows "cobalt" "refinery" 2023 "CHN")
          s24 (share rows "cobalt" "refinery" 2024 "CHN") m22 (when alt (alt-share alt "cobalt" "refinery" 2022 "CHN"))]
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
          ms (when alt (for [y (range 2000 2006)] [y (alt-share alt "rare-earths" "mine" y "CHN")]))
          early (map second (take 3 ss)) late (map second (drop 3 ss))]
      ;; USGS MCS 2005 and 2006 (rare earths): the US mined 5,000 t of
      ;; bastnaesite concentrate (REO) a year at Mountain Pass through 2002
      ;; and none in 2003-05; their world tables give China 92/99.1 kt
      ;; (0.93) in 2003, 95/102 (0.93) in 2004, 98/105 (0.93) in 2005. With
      ;; the US 5 kt, USGS's own China, CIS, India and Malaysia figures give
      ;; 0.88-0.90 in 2000-02. "93-97% in 2000-05" holds only from 2003.
      (target "China's rare-earth mine share 0.86-0.93 in 2000-02 (US Mountain Pass 5 kt a year, MCS 2005), 0.93-0.99 in 2003-05"
              (and (every? #(within % 0.86 0.93) early) (every? #(within % 0.93 0.99) late))
              (str "BGS " (str/join ", " (map (fn [[y v]] (str y " " (f2 v))) ss))
                   (when ms (str "; MYB " (str/join ", " (map (fn [[y v]] (str y " " (f2 v))) ms))
                                 " (MYB 2000 counts Kyrgyzstan's 16.5 kt, a processor)")))))
    (let [c22 (share rows "cobalt" "mine" 2022 "COD") m22 (when alt (alt-share alt "cobalt" "mine" 2022 "COD"))]
      (target "Congo's 2022 share of mined cobalt 0.73 (ESSENTIALS step 3, MYB; within 0.04 across sources)"
              (within c22 0.69 0.77) (str "BGS " (f2 c22) "; MYB " (f2 m22))))
    (let [g22 (share rows "gallium" "refinery" 2022 "CHN") m22 (when alt (alt-share alt "gallium" "refinery" 2022 "CHN"))]
      (target "China's primary gallium 2022 0.98 (within 0.02)" (within g22 0.96 1.0)
              (str "BGS " (f2 g22) "; MYB " (f2 m22))))
    (let [hs (for [y [2000 2010 2022]]
               (let [sh (stg/shares (by-year rows "graphite" "mine" y))] [y (stg/hhi sh) (get sh "CHN")]))
          ms (when alt (for [y [2000 2010 2022]]
                         (let [sh (stg/shares (by-year-sum alt "graphite" "mine" y))] [y (stg/hhi sh) (get sh "CHN")])))]
      (println (str "  natural graphite HHI (China): BGS "
                    (str/join ", " (map (fn [[y h c]] (str y " " (f2 h) " (" (f2 c) ")")) hs))
                    "; MYB deduplicated " (str/join ", " (map (fn [[y h c]] (str y " " (f2 h) " (" (f2 c) ")")) ms))
                    "; ESSENTIALS section 5 table (MYB, details summed): 0.30 (0.51), 0.48 (0.67), 0.69 (0.83)")))

    (println "\nPlants (MYB vol. III 2024, USGS US facilities 2026, China copper smelters from USGS China GIS 2023): HHI by capacity, plants, coverage of the stage's output")
    (let [ps (stg/plant-stocks plants rows)
          key-of (fn [r] (let [[_ c s] (re-matches #"stages/(.+?)-(mine|smelter|refinery|chemical|separation)-plant-.*" (:variable r))] [c s]))
          country-hhi (fn [c s]
                        (let [rs (filter #(and (= c (:commodity %)) (= s (:stage %))) rows)]
                          (when (seq rs)
                            (stg/hhi (stg/shares (by-year rows c s (apply max (map :year rs))))))))]
      (doseq [[[c s] rs] (sort (group-by key-of ps))]
        (let [g (fn [suf] (some #(when (str/ends-with? (:variable %) suf) (:value %)) rs))]
          (println (format "  %-12s %-9s plant HHI %s  plants %3d  coverage %s  (country HHI %s)"
                           c s (f2 (g "-plant-hhi")) (long (or (g "-plant-count") 0)) (f2 (g "-plant-coverage"))
                           (f2 (country-hhi c s)))))))

    (chemical-stages rows)

    (let [n (count @results) ok (count (filter second @results))]
      (println (format "\n%d of %d targets met." ok n)))))
