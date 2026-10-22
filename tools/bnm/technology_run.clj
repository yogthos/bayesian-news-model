(ns bnm.technology-run
  "Writes data/derived/stocks/technology.csv (iso,year,variable,value; the
  year a value is known at) from bnm.technology, and prints coverage and
  spot checks (bead bnm-62z.11).

    jolt -A:tools -M -m bnm.technology-run

  Sources:
    data/slow/series.tsv  World Bank WDI and the rest of the slow store,
      each value known at its availability date (the store's own: WDI's
      year y-1 by September of y). Diffusion: internet users (%),
      mobile subscriptions (per 100), access to electricity (%). Leading
      inflows: R&D (% GDP), resident and nonresident patent applications
      and scientific articles per million people (population from the
      store, known when both are), high-tech share of manufactured
      exports. World series under iso WLD (bnm.states reads a series held
      only for WLD as a time signal common to every country): research
      output by field (OpenAlex, works per million works) and the
      frontier's semiconductor production (FRED, US, the December index;
      a USA-only series would be one country, too few for bnm.factors).
      Values are the store's current vintage, each dated by its first
      availability: later revisions (WDI's, and OpenAlex's field tagging,
      a 2020s classifier applied to old works) are later knowledge read
      early, which this cannot undo.
    data/raw/slow/chat/FinalCHAT_72909.csv  Comin & Hobijn's Cross-country
      Historical Adoption of Technology (NBER w15319, 2009; free for
      research), 1800-2008: telephones, cars, televisions, radios,
      computers and tractors per 100 people (CHAT's population, in
      thousands; radios are in thousands), electricity output in kWh per
      person. Known with a stated lag of 2 years after the data year (the
      statistical yearbooks it compiles were out within about that); the
      compilation itself is a 2009 vintage (data to 2008 at most, most series ending by
      2002, every series stale by 2007 known), so its revisions and
      interpolations are later knowledge read early.

  Eras: CHAT's technologies (\"historical\", to about 2006 known: most series end by 2002) and the
  slow store's (\"recent\", from about 1991) each have their own gap, so
  neither jumps when CHAT ends; productivity-inflow chains across both.

  The frontier is the United States (Comin & Hobijn's benchmark)."
  (:require [bnm.geo :as geo]
            [bnm.slow-train :as st]
            [bnm.technology :as tech]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def series-path "data/slow/series.tsv")
(def variables-path "data/slow/variables.tsv")
(def chat-path "data/raw/slow/chat/FinalCHAT_72909.csv")
(def out-path "data/derived/stocks/technology.csv")

(def frontier "USA")
(def chat-lag "Years from a CHAT data year to the year it is taken as known." 2)
(def years (range 1880 2027))

(def wdi-techs
  "Diffusion technologies from the slow store: name -> [variable cfg]."
  {"internet" ["technology/internet_users" {:cap 100.0 :saturation 100.0}]
   ;; subscriptions per 100 people: one each is full adoption, more (several
   ;; SIMs) is not more adoption
   "mobile" ["technology/mobile_subscriptions" {:cap 150.0 :saturation 100.0}]
   "electricity_access" ["energy/electricity_access" {:cap 100.0 :saturation 100.0}]})

(def chat-techs
  "Diffusion technologies from CHAT: name -> [column scale cfg], scale
  turning the column per person into the level (per 100 people, or kWh
  per person)."
  {"telephone" ["telephone" 100.0 {:cap 100.0}]
   "cars" ["vehicle_car" 100.0 {:cap 100.0}]
   "tv" ["tv" 100.0 {:cap 100.0}]
   "radio" ["radio" 100000.0 {}]
   "computers" ["computer" 100.0 {:cap 100.0}]
   "tractors" ["ag_tractor" 100.0 {}]
   "electricity" ["elecprod" 1.0 {}]})

(def leading
  "Leading inflows from the slow store: output name -> [variable per-million?]."
  {"rd_intensity" ["technology/rd_gdp" false]
   "patents_resident_pm" ["technology/patents_resident" true]
   "patents_nonresident_pm" ["technology/patents_nonresident" true]
   "articles_pm" ["technology/articles" true]
   "hightech_exports_share" ["technology/hightech_exports_share" false]})

(def eras {"historical" (set (keys chat-techs)) "recent" (set (keys wdi-techs))})

(def research-fields ["ai" "batteries" "internet" "machine_learning" "photovoltaics"
                      "quantum_computing" "robotics" "semiconductors"])

;; ---------------------------------------------------------------- reading

(defn read-store
  "{[unit var] [entries]} of the store's annual values for `vars`, raw (no
  transform); a monthly series keeps its December values. :t the data
  year, :known the year it became available."
  [path vars]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[unit v freq pend avail value] (str/split line #"\t" -1)]
                (if (and (vars v) (seq value) (or (not= freq "m") (= "12" (subs pend 5 7))))
                  (let [x (parse-double value)]
                    (if x
                      (update acc [unit v] (fnil conj [])
                              {:t (quot (st/month-of pend) 12) :known (quot (st/month-of avail) 12) :x x})
                      acc))
                  acc)))
            {} (line-seq r))))

(def chat-names
  "CHAT's country names the registry does not know; nil: no country now."
  {"Burma" "MMR" "Ivory Coast" "CIV" "Venezuala" "VEN" "Bosnia-Herzegovina" "BIH"
   "Slovak Republic" "SVK" "South Korea" "KOR" "Macedonia" "MKD" "Swaziland" "SWZ"
   "Democratic Republic of the Congo" "COD" "Republic of the Congo" "COG" "Russia" "RUS"
   "Iran" "IRN" "Syria" "SYR" "Laos" "LAO" "Taiwan" "TWN" "Moldova" "MDA" "Czech Republic" "CZE"
   "Tanzania" "TZA" "Vietnam" "VNM" "Czechoslovakia" "CSK" "Gambia" "GMB" "Bolivia" "BOL"
   "Hong Kong" "HKG" "United States" "USA" "United Kingdom" "GBR" "Netherlands" "NLD"
   "North Vietnam" nil "South Vietnam" nil "South Yemen" nil "Indochina" nil})

(defn iso-of-name []
  (let [reg (into {} (for [c (:countries @geo/registry) n (cons (:name c) (:names c))]
                       [(str/lower-case n) (:iso3 c)]))]
    (fn [nm] (if (contains? chat-names nm) (chat-names nm) (reg (str/lower-case nm))))))

(defn read-chat
  "{tech {iso [entries]}} from CHAT, levels per person scaled, known
  chat-lag years after the data year; and the names left unmapped."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          hs (mapv str/trim (str/split h #","))
          ix (zipmap hs (range))
          iso-of (iso-of-name)
          unmapped (atom (sorted-set))
          num (fn [f c] (let [s (str/trim (nth f (ix c) ""))] (when (seq s) (parse-double s))))
          data (reduce (fn [acc l]
                         (let [f (str/split l #"," -1)
                               nm (str/trim (first f))
                               iso (iso-of nm)
                               t (some-> (nth f (ix "year")) str/trim parse-long)
                               pop (num f "xlpopulation")]
                           (when-not (or iso (contains? chat-names nm)) (swap! unmapped conj nm))
                           (if (and iso t pop (pos? pop))
                             (reduce (fn [acc [k [col scale]]]
                                       (if-let [x (num f col)]
                                         (update-in acc [k iso] (fnil conj [])
                                                    {:t t :known (+ t chat-lag) :x (/ (* x scale) (* pop 1000.0))})
                                         acc))
                                     acc chat-techs)
                             acc)))
                       {} ls)]
      {:data data :unmapped @unmapped})))

;; ---------------------------------------------------------------- output

(defn write-rows [path rows]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value\n")
    (doseq [{:keys [iso year variable value]} rows]
      (.write w (str iso "," year "," variable "," value "\n")))))

(defn- index [rows] (into {} (map (fn [r] [[(:iso r) (:year r) (:variable r)] (:value r)])) rows))

(defn- f1 [x] (if x (format "%7.1f" (double x)) "      -"))
(defn- f2 [x] (if x (format "%7.2f" (double x)) "      -"))

(defn- corr [pairs]
  (let [n (count pairs)]
    (when (> n 5)
      (let [mx (/ (reduce + (map first pairs)) n) my (/ (reduce + (map second pairs)) n)
            sxy (reduce + (map (fn [[x y]] (* (- x mx) (- y my))) pairs))
            sxx (reduce + (map (fn [[x _]] (* (- x mx) (- x mx))) pairs))
            syy (reduce + (map (fn [[_ y]] (* (- y my) (- y my))) pairs))]
        (/ sxy (Math/sqrt (* sxx syy)))))))

(defn report [rows store]
  (let [ix (index rows)
        v (fn [iso y var] (ix [iso y var]))
        by-var (group-by :variable rows)]
    (println "rows" (count rows) " countries" (count (set (map :iso rows))))
    (println "\ncoverage: variable  countries  years known")
    (doseq [[var rs] (sort-by key by-var)]
      (println (format "  %-34s %4d  %d-%d" var (count (set (map :iso rs)))
                       (reduce min (map :year rs)) (reduce max (map :year rs)))))
    (println "\ninternet: lag behind the USA (years) / adoption (share of 100%), by year known")
    (doseq [iso ["USA" "KOR" "SWE" "CHN" "BRA" "IND" "NGA"]]
      (println (format "  %s %s" iso (str/join " " (for [y [2000 2005 2010 2015 2020 2025]]
                                                     (str y (f1 (v iso y (tech/var-name "internet" "lag"))) "/" (str/trim (f2 (v iso y (tech/var-name "internet" "adoption"))))))))))
    (println "\nmobile lag (years):")
    (doseq [iso ["CHN" "IND" "NGA"]]
      (println (format "  %s %s" iso (str/join " " (for [y [2000 2005 2010 2015 2020 2025]] (str y (f1 (v iso y (tech/var-name "mobile" "lag")))))))))
    (println "\nlag behind the USA (years), median over countries, by year known (n countries); the USA's speed and midpoint known 1997")
    (doseq [k (sort (keys (merge chat-techs wdi-techs)))]
      (println (format "  %-18s %s   USA speed %s midpoint %s" k
                       (str/join " " (for [y [1930 1950 1975 1997 2010 2025]
                                           :let [ls (vec (sort (keep #(v % y (tech/var-name k "lag")) (set (map :iso rows)))))]]
                                       (format "%d %5s (%3d)" y (if (seq ls) (format "%.0f" (double (ls (quot (count ls) 2)))) "-") (count ls))))
                       (f2 (v "USA" 1997 (tech/var-name k "speed"))) (f1 (v "USA" 1997 (tech/var-name k "midpoint"))))))
    (println "\ncatch-up: historical gap lag count break | recent gap lag count break | inflow")
    (doseq [iso ["CHN" "IND" "NGA" "KOR"]]
      (println " " iso)
      (doseq [y [1960 1970 1980 1990 2000 2005 2010 2012 2013 2015 2020 2025]]
        (println (format "    %d  %s %s %s %s | %s %s %s %s | %s" y
                         (f2 (v iso y "technology/gap-historical")) (f1 (v iso y "technology/lag-historical"))
                         (f1 (v iso y "technology/gap-historical-count")) (f1 (v iso y "technology/gap-historical-break"))
                         (f2 (v iso y "technology/gap-recent")) (f1 (v iso y "technology/lag-recent"))
                         (f1 (v iso y "technology/gap-recent-count")) (f1 (v iso y "technology/gap-recent-break"))
                         (f2 (v iso y "technology/productivity-inflow"))))))
    (println "\nleading inflows at 2022 known: R&D % GDP, resident patents per million, articles per million")
    (doseq [iso ["USA" "KOR" "CHN" "DEU" "JPN" "IND" "BRA" "NGA"]]
      (println (format "  %s %s %s %s" iso (f2 (v iso 2022 "technology/rd-intensity")) (f1 (v iso 2022 "technology/patents-resident-pm"))
                       (f1 (v iso 2022 "technology/articles-pm")))))
    (println "\nsign check: each era's gap against log GDP per capita (WDI, known the same year), across countries")
    (let [gdp (into {} (for [[[iso var] es] store :when (= var "economy/gdp_per_capita")] [iso es]))]
      (doseq [era (sort (keys eras)) y [1975 1995 2005 2015 2024]]
        (let [pairs (vec (for [iso (set (map :iso rows))
                               :let [g (v iso y (tech/var-name "gap" era)) p (some-> (gdp iso) (tech/latest y 3))]
                               :when (and g p (pos? p))]
                           [g (Math/log p)]))]
          (println (format "  %-10s %d  n %3d  corr %s" era y (count pairs) (f2 (corr pairs)))))))
    (println "\nadoption above 1 (should be none):" (count (filter #(and (str/ends-with? (:variable %) "-adoption") (> (:value %) 1.0)) rows)))
    (println "variables not technology/ kebab-case:" (vec (remove #(re-matches #"technology/[a-z0-9]+(-[a-z0-9]+)*" %) (keys by-var))))))

(defn identification
  "The ensemble smoother's posterior for the frontier's internet curve, as
  known in `year`: what the data pin down."
  [store year]
  (let [obs (tech/as-of (store [frontier "technology/internet_users"]) year)
        post (tech/calibrate-diffusion obs {:ceiling {:mean 90.0 :sd 0.3 :log true}
                                            :speed {:mean 0.3 :sd 0.5 :log true}
                                            :midpoint {:mean 2000.0 :sd 10.0}
                                            :noise 2.0 :members 60 :seed 1})]
    (println (format "\nES-MDA posterior, USA internet known %d (median, sd): ceiling %.1f (%.1f) speed %.3f (%.3f) midpoint %.1f (%.1f)"
                     year
                     (get-in post [:params :ceiling :median]) (get-in post [:params :ceiling :sd])
                     (get-in post [:params :speed :median]) (get-in post [:params :speed :sd])
                     (get-in post [:params :midpoint :median]) (get-in post [:params :midpoint :sd])))))

(defn -main [& _]
  (let [vars (set (concat (map first (vals wdi-techs)) (map first (vals leading))
                          ["people/population" "economy/gdp_per_capita" "technology/semiconductor_production"]
                          (map #(str "technology/research_" %) research-fields)))
        known-vars (set (map :name (st/read-variables variables-path)))
        _ (when-let [miss (seq (remove known-vars vars))] (println "not in variables.tsv:" (vec miss)))
        store (read-store series-path vars)
        countries (fn [var] (into {} (for [[[u v] es] store :when (and (= v var) (geo/country? u))] [u es])))
        {chat :data unmapped :unmapped} (read-chat chat-path)
        _ (when (seq unmapped) (println "CHAT names not mapped:" (vec unmapped)))
        data (merge chat (into {} (for [[k [var _]] wdi-techs] [k (countries var)])))
        techs (merge (update-vals wdi-techs second) (update-vals chat-techs #(nth % 2)))
        diffusion (tech/technology-rows {:techs techs :eras eras :data data :frontier frontier :years years})
        pop (countries "people/population")
        lead (reduce (fn [acc [out [var pm?]]]
                       (reduce (fn [acc [iso es]]
                                 (let [es (if pm? (tech/per-million es (get pop iso)) es)]
                                   (if (seq es) (assoc-in acc [iso (tech/var-name out)] es) acc)))
                               acc (countries var)))
                     {} leading)
        world {"WLD" (merge (into {} (for [f research-fields :let [es (store ["world" (str "technology/research_" f)])] :when es]
                                       [(tech/var-name "research" f) es]))
                            (when-let [es (store ["USA" "technology/semiconductor_production"])]
                              {(tech/var-name "frontier-semiconductor-production") es}))}
        rows (vec (sort-by (juxt :iso :year :variable)
                           (concat diffusion (tech/latest-rows lead years 3) (tech/latest-rows world years 1))))]
    (write-rows out-path rows)
    (println "wrote" out-path)
    (report rows store)
    (identification store 2005)
    (identification store 2025)))
