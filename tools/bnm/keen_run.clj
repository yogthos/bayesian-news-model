(ns bnm.keen-run
  "The macro-financial core (bnm.keen) per country and year, from the slow
  store and the Jordà-Schularick-Taylor Macrohistory Database:

    omega   the labour share (Penn World Table labsh, economy/labor_share)
    lambda  1 - unemployment: JST unemp where it runs, else World Bank
            economy/unemployment
    d       private non-financial credit to GDP (households and firms; G&CL's
            d is firms' alone), one source at a time so its level does not
            jump within a calibration window: at each year end, BIS total
            credit (finance/credit_gdp, all lenders) if 20 of its data years
            are known by then, else JST bank loans over GDP (tloans/gdp) if
            20 are, else the longest of the three, World Bank domestic
            credit to the private sector included (bnm.keen/choose-d);
            keen/d-source says which: 1 BIS, 2 JST, 3 World Bank. A switch
            recalibrates, and d's level may step there
    r       the real long rate: JST ltrate less CPI inflation, else the OECD
            long yield (finance/yield_long) less World Bank inflation
    alpha   growth of real GDP per head (JST rgdpmad, else WDI)
    beta    population growth (JST pop, else WDI), for the labour force
    nu      capital to output (PWT, economy/capital_output)

  Each value enters at the year end it was published: the slow store's own
  availability dates (annual means of quarterly or monthly series once their
  last month is out), JST's data year y at the end of y + 1; and no data year
  is read at or after the year it is used for (bnm.keen/known). Values are
  the latest vintages, so revisions (World Bank, PWT, BIS) and series built
  after the fact (PWT's labour share, the ILO's modelled unemployment) are
  read as if published at those dates.

    jolt -A:tools -M -m bnm.keen-run [--countries USA,ESP,JPN] [--out PATH]

  Writes data/derived/stocks/keen.csv: iso,year,variable,value."
  (:require [bnm.keen :as keen]
            [bnm.macrohistory :as mh]
            [bnm.slow-train :as st]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def slow-vars
  #{"economy/labor_share" "economy/unemployment" "finance/credit_gdp" "finance/private_credit_gdp"
    "finance/yield_long" "economy/inflation" "economy/gdp_per_capita" "people/population"
    "economy/capital_output"})

(def ^:private per-year {"a" 1 "q" 4 "m" 12})

(def d-sources [:d-bis :d-jst :d-wdi])

(defn read-slow
  "{[iso var] {data-year [year-known value]}} for slow-vars, raw values (not
  the store's modelling transforms), each variable at its own frequency in
  data/slow/variables.tsv; quarterly and monthly ones as the year's mean
  once every period of it is out, known the year its last one was."
  [series-path variables-path]
  (let [freq (into {} (for [v (st/read-variables variables-path) :when (slow-vars (:name v))] [(:name v) (:freq v)]))
        acc (java.util.HashMap.)]
    (with-open [r (io/reader series-path)]
      (doseq [line (line-seq r)
              :let [[unit v f pend avail value] (str/split line #"\t" -1)]
              :when (and (slow-vars v) (= f (freq v)))
              :let [x (parse-double value)]
              :when x]
        (let [k [unit v (parse-long (subs pend 0 4))]]
          (.put acc k (conj (or (.get acc k) []) [(parse-long (subs avail 0 4)) x])))))
    (reduce (fn [m [[unit v yr] obs]]
              (if (>= (count obs) (per-year (freq v)))
                (assoc-in m [[unit v] yr] [(apply max (map first obs)) (/ (reduce + (map second obs)) (count obs))])
                m))
            {} acc)))

(defn- growth
  "{yr [known g]}: log growth of a {yr [known level]} series, known when both years are."
  [s]
  (into {} (for [[yr [a x]] s :let [[b x0] (get s (dec yr))]
                 :when (and x0 (pos? x) (pos? x0))]
             [yr [(max a b) (Math/log (/ x x0))]])))

(defn- scale [s f] (into {} (for [[yr [a x]] s] [yr [a (f x)]])))

(defn jst-panels
  "{iso {var {yr [known value]}}} from JST: each data year known at the end of the next."
  [path]
  (let [rows (group-by :iso (mh/read-jst path))]
    (into {} (for [[iso rs] rows]
               (let [by (into (sorted-map) (for [r rs :when (:year r)] [(:year r) r]))
                     ser (fn [f] (into (sorted-map) (for [[yr r] by :let [x (f r)] :when (some? x)] [yr [(inc yr) (double x)]])))
                     cpi (ser :cpi)
                     infl (growth cpi)]
                 [iso {:lambda (ser #(some-> (:unemp %) (/ 100.0) (->> (- 1.0))))
                       :d (ser #(when (and (:tloans %) (:gdp %) (pos? (:gdp %))) (/ (:tloans %) (:gdp %))))
                       :r (into (sorted-map) (for [[yr r] by :let [i (get infl yr)] :when (and (:ltrate r) i)]
                                               [yr [(inc yr) (- (/ (:ltrate r) 100.0) (Math/expm1 (second i)))]]))
                       :alpha (growth (ser :rgdpmad))
                       :beta (growth (ser :pop))}])))))

(defn- prefer
  "Year by year, the first series that has the year."
  [& ss]
  (reduce (fn [acc s] (merge s acc)) {} ss))

(defn panel
  "One country's panel for bnm.keen/country-rows, its debt as d-sources,
  one of which bnm.keen/choose-d takes at each year end."
  [iso slow jst]
  (let [s (fn [v] (get slow [iso v] {}))
        j (get jst iso {})
        infl (s "economy/inflation")
        slow-r (into {} (for [[yr [a y]] (s "finance/yield_long") :let [[b i] (get infl yr)] :when i]
                          [yr [(max a b) (- (/ y 100.0) (/ i 100.0))]]))]
    {:panel {:omega (s "economy/labor_share")
             :lambda (prefer (:lambda j) (scale (s "economy/unemployment") #(- 1.0 (/ % 100.0))))
             :d-bis (scale (s "finance/credit_gdp") #(/ % 100.0))
             :d-jst (:d j {})
             :d-wdi (scale (s "finance/private_credit_gdp") #(/ % 100.0))
             :r (prefer (:r j) slow-r)
             :alpha (prefer (:alpha j) (growth (s "economy/gdp_per_capita")))
             :beta (prefer (:beta j) (growth (s "people/population")))
             :nu (s "economy/capital_output")}}))

(defn- spot [rows iso years vars]
  (println (str "\n" iso))
  (let [by (group-by (juxt :year :variable) (filter #(= iso (:iso %)) rows))]
    (doseq [y years]
      (println " " y (str/join " " (for [v vars :let [x (some-> (by [y v]) first :value)] :when x]
                                     (format "%s=%.3f" (subs v (count keen/prefix)) x)))))))

(defn -main [& args]
  (let [opts (apply hash-map args)
        out (get opts "--out" "data/derived/stocks/keen.csv")
        t0 (System/currentTimeMillis)
        slow (read-slow "data/slow/series.tsv" "data/slow/variables.tsv")
        jst (jst-panels "data/raw/slow/jst/jst.csv")
        failures (atom 0)
        isos (if-let [c (get opts "--countries")]
               (str/split c #",")
               ;; every country with a labour share (counting its years over
               ;; the whole sample would choose countries by later data)
               (sort (for [[[iso v] s] slow :when (and (= v "economy/labor_share") (seq s))] iso)))
        _ (println "read" (count slow) "series in" (quot (- (System/currentTimeMillis) t0) 1000) "s;" (count isos) "countries")
        results (doall
                 (pmap (fn [iso]
                         (let [{:keys [panel]} (panel iso slow jst)]
                           (when (and (seq (:omega panel)) (seq (:lambda panel)) (some (comp seq panel) d-sources))
                             (let [t (System/currentTimeMillis)
                                   rows (keen/country-rows iso panel {:d-sources d-sources :failures failures})
                                   srcs (distinct (keep #(when (= "keen/d-source" (:variable %)) (long (:value %))) rows))]
                               (println iso "d-sources" (vec srcs) (count rows) "rows"
                                        (count (distinct (map :year (filter #(= "keen/calibrated" (:variable %)) rows))))
                                        "calibrated years" (str (quot (- (System/currentTimeMillis) t) 1000) "s"))
                               (flush)
                               rows))))
                       isos))
        rows (vec (apply concat results))]
    (io/make-parents out)
    (with-open [w (io/writer out)]
      (doseq [line (keen/csv-lines rows)] (.write w (str line "\n"))))
    (let [years (fn [v] (distinct (map (juxt :iso :year) (filter #(= v (:variable %)) rows))))]
      (println "\nwrote" (count rows) "rows to" out)
      (println "country-years with a state:" (count (years "keen/omega"))
               "with the model:" (count (years "keen/calibrated"))
               "countries with the model:" (count (distinct (map first (years "keen/calibrated"))))
               "variables:" (count (distinct (map :variable rows)))
               "failed calibrations:" @failures))
    (let [vars (map #(str keen/prefix %)
                    ["omega" "lambda" "d" "d-source" "profit-share" "d-bar" "d-critical" "bad-basin-prob"
                     "employment-collapse-prob" "minsky" "minsky-observed" "gap-d"])]
      (spot rows "USA" (range 2000 2011) vars)
      (spot rows "ESP" (range 2003 2013) vars)
      (spot rows "JPN" (range 1985 1995) vars))
    (println "\ndone in" (quot (- (System/currentTimeMillis) t0) 1000) "s")))
