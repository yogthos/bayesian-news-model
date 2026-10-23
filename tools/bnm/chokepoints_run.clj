(ns bnm.chokepoints-run
  "Writes data/derived/stocks/chokepoints.csv from bnm.chokepoints and
  prints the coverage, the validation and the PortWatch check (bead
  bnm-1pq.10, docs/ESSENTIALS.md section 8 step 9):

    python -I scripts/routes.py energy    # BACI HS92 crude, LNG, products, bilateral
    python -I scripts/routes.py routes    # routes.csv, routes-closed.csv
    jolt -A:tools -M -m bnm.chokepoints-run

  Stocks: chokepoint/<strait>-<product> and chokepoint/seaborne-<product>
  by importer and WLD, year = BACI data year + 2. Products: every HS2
  chapter (hs01 .. hs97), `all`, the sub-nodes of subnodes.csv and
  crude-oil, lng, oil-products. A strait share under 0.01 is not written
  (absent = below 1%; at 0.001 the file is 276 MB); the seaborne share
  always is.

  Report, nothing tuned:
    1. coverage: BACI pairs with flows, how many are maritime and routed,
       the value shares;
    2. validation: Hormuz shares of Japan's, Korea's, India's and China's
       crude imports; Malacca for East Asian crude and LNG; Suez and
       Bab-el-Mandeb for Asia-Europe container chapters; the world's
       crude and LNG through Hormuz;
    3. PortWatch: the routed world tonnes through each strait against
       PortWatch's transit capacity (deadweight), across straits, 2019-23;
       and the observed disruptions (the Red Sea from December 2023,
       Panama's drought from mid-2023, Suez in March 2021) against the
       routed exposure of the flows they hit."
  (:require [bnm.chokepoints :as cp]
            [bnm.tech-demand-run :as tdr]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def dir "data/derived/supply")
(def out "data/derived/stocks/chokepoints.csv")
(def floor 0.01)
(def check-years (range 2019 2024))

(defn- f [x d] (if (some? x) (format (str "%." d "f") (double x)) "-"))
(defn- mean [xs] (when (seq xs) (/ (reduce + xs) (count xs))))

(def asia #{"CHN" "JPN" "KOR" "TWN" "HKG" "VNM" "THA" "MYS" "IDN" "SGP" "PHL"})
(def europe #{"DEU" "NLD" "BEL" "FRA" "ITA" "ESP" "GBR" "POL" "SWE"})
(def container-chapters #{"39" "61" "62" "64" "84" "85" "87" "94" "95"})

;; ---------------------------------------------------------------- streaming

(defn- run-file
  "Streams one flow file through bnm.chokepoints, writing its rows; with
  `all?` the `all` product too. -> {data-year {[product iso] shares+}}
  for the products and importers in `keep-pred`."
  [w routes path parse all? keep-pred]
  (let [kept (atom {})]
    (cp/gz-lines path
                 (fn [ls]
                   (cp/by-year routes ls parse
                               (fn [y agg]
                                 (let [agg (if all? (merge agg (cp/all-product agg)) agg)]
                                   (cp/write-rows! w (cp/rows agg y floor))
                                   (swap! kept assoc y (into {} (filter (comp keep-pred key) agg)))
                                   (println "  " path y (count agg)))))))
    @kept))

(defn- pair-stats
  "Coverage and the Asia-Europe container shares over the HS2 pairs of
  check-years, in one pass: {:pairs {[e i] value} :value {kind value}
  :asia-eu {strait value, :total value}}."
  [routes path]
  (cp/gz-lines path
               (fn [ls]
                 (reduce (fn [acc l]
                           (if-let [{:keys [year product class exporter importer value]} (cp/hs2-flow l)]
                             (if (some #{year} check-years)
                               (let [r (get-in routes [exporter importer class])
                                     s (cp/seaborne-share r)
                                     kind (cond (nil? r) :no-route
                                                (not (:maritime? r)) :land
                                                (pos? s) :sea
                                                :else :sea-unrouted)
                                     acc (-> acc
                                             (update-in [:pairs [exporter importer]] (fnil + 0.0) value)
                                             (update-in [:kind [exporter importer]] #(or % kind))
                                             (update-in [:value kind] (fnil + 0.0) value)
                                             (update-in [:value :total] (fnil + 0.0) value))]
                                 (if (and (asia exporter) (europe importer) (container-chapters (subs product 2)))
                                   (reduce-kv (fn [a c x] (update-in a [:asia-eu c] (fnil + 0.0) (* value s x)))
                                              (-> acc (update-in [:asia-eu :total] (fnil + 0.0) value)
                                                  (update-in [:asia-eu :seaborne] (fnil + 0.0) (* value s)))
                                              (:through r))
                                   acc))
                               acc)
                             acc))
                         {} ls))))

;; ---------------------------------------------------------------- PortWatch

(defn read-portwatch
  "{portid [{:date :year :month :n :tanker :container :capacity}]} of the
  daily chokepoint transits."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (reduce (fn [acc l]
                (let [x (str/split l #"," -1) g #(nth x (ix %))]
                  (update acc (g "portid") (fnil conj [])
                          {:date (g "date") :year (parse-long (g "year")) :month (parse-long (g "month"))
                           :n (parse-double (g "n_total")) :tanker (parse-double (g "n_tanker"))
                           :container (parse-double (g "n_container")) :capacity (parse-double (g "capacity"))})))
              {} ls))))

(defn- daily-mean [days k from to]
  (mean (keep #(when (and (>= (compare (:date %) from) 0) (<= (compare (:date %) to) 0)) (k %)) days)))

;; ---------------------------------------------------------------- report

(defn- share-of [kept y product iso strait]
  (some-> (get-in kept [y [product iso]]) cp/shares (get strait)))

(def gulf
  "Exporters whose ports are all or mostly inside Hormuz."
  #{"SAU" "ARE" "KWT" "IRQ" "QAT" "IRN" "BHR"})

(defn- gulf-share
  "{[iso year] share of the importer's crude imports by value from the Gulf exporters}."
  [path isos]
  (let [acc (cp/gz-lines path
                         (fn [ls]
                           (reduce (fn [m l]
                                     (if-let [{:keys [year exporter importer value]} (cp/hs6-flow {"270900" "crude-oil"} l)]
                                       (if (isos importer)
                                         (cond-> (update-in m [[importer year] :total] (fnil + 0.0) value)
                                           (gulf exporter) (update-in [[importer year] :gulf] (fnil + 0.0) value))
                                         m)
                                       m))
                                   {} ls)))]
    (update-vals acc #(/ (or (:gulf %) 0.0) (:total %)))))

(defn- validation [energy hs2 subs]
  (println "\n2. Validation (data years; share of the importer's imports by value)")
  (println "\n  Hormuz share of crude imports (270900)")
  (println "   iso   1995  2000  2005  2010  2015  2019  2020  2021  2022  2023  2024   seaborne-2023")
  (doseq [iso ["JPN" "KOR" "IND" "CHN" "TWN" "WLD"]]
    (println (str "   " iso "  " (str/join "  " (for [y [1995 2000 2005 2010 2015 2019 2020 2021 2022 2023 2024]]
                                                   (f (share-of energy y "crude-oil" iso "hormuz") 2)))
                  "   " (f (share-of energy 2023 "crude-oil" iso :seaborne) 2))))
  (let [g (gulf-share (str dir "/hs6-energy-pairs.csv.gz") #{"JPN" "KOR" "IND" "CHN" "TWN"})]
    (println "  from Gulf exporters (SAU ARE KWT IRQ QAT IRN BHR; the bound if all their crude passed Hormuz)")
    (doseq [iso ["JPN" "KOR" "IND" "CHN" "TWN"]]
      (println (str "   " iso "  " (str/join "  " (for [y [1995 2000 2005 2010 2015 2019 2020 2021 2022 2023 2024]]
                                                     (f (g [iso y]) 2)))))))
  (doseq [[prod lbl] [["crude-oil" "crude (270900)"] ["lng" "LNG (271111)"]]]
    (println (str "\n  Malacca, Hormuz and Lombok shares of " lbl ", mean 2019-2023"))
    (doseq [iso ["JPN" "KOR" "CHN" "TWN" "IND" "WLD"]]
      (println (str "   " iso "  malacca " (f (mean (keep #(share-of energy % prod iso "malacca") check-years)) 2)
                    "  hormuz " (f (mean (keep #(share-of energy % prod iso "hormuz") check-years)) 2)
                    "  lombok " (f (mean (keep #(share-of energy % prod iso "lombok") check-years)) 2)
                    "  seaborne " (f (mean (keep #(share-of energy % prod iso :seaborne) check-years)) 2)))))
  (println "\n  World trade through Hormuz, mean 2019-2023 (value share of world imports)")
  (doseq [p ["crude-oil" "lng" "oil-products" "hs27" "all"]]
    (let [src (if (#{"hs27" "all"} p) hs2 energy)]
      (println (str "   " p "  " (f (mean (keep #(share-of src % p "WLD" "hormuz") check-years)) 3)))))
  (println "\n  EU sub-node exposure, 2023 (DEU): Suez, Malacca, Bab-el-Mandeb")
  (doseq [p (sort (distinct (map first (keys (get subs 2023)))))]
    (when (get-in subs [2023 [p "DEU"]])
      (println (str "   " p "  suez " (f (share-of subs 2023 p "DEU" "suez") 2)
                    "  malacca " (f (share-of subs 2023 p "DEU" "malacca") 2)
                    "  bab-el-mandeb " (f (share-of subs 2023 p "DEU" "bab-el-mandeb") 2)
                    "  seaborne " (f (share-of subs 2023 p "DEU" :seaborne) 2))))))

(defn- asia-europe [{:keys [asia-eu]}]
  (println "\n  Asia -> Europe, container chapters 39 61 62 64 84 85 87 94 95, 2019-2023 (value shares)")
  (let [t (:total asia-eu)]
    (doseq [k [:seaborne "suez" "bab-el-mandeb" "malacca" "gibraltar" "cape-of-good-hope" "dover"]]
      (println (str "   " (name k) "  " (f (/ (get asia-eu k 0.0) t) 3))))))

(defn- coverage [{:keys [pairs kind value]}]
  (println "\n1. Coverage, BACI HS92 HS2 pairs, data years 2019-2023")
  (let [n (frequencies (vals kind))
        t (:total value)]
    (println (str "   pairs with flows " (count pairs) ": sea and routed " (get n :sea 0)
                  ", land " (get n :land 0) ", sea without a route " (get n :sea-unrouted 0)
                  ", no route row " (get n :no-route 0)))
    (println (str "   value: sea and routed " (f (/ (get value :sea 0.0) t) 3)
                  ", land " (f (/ (get value :land 0.0) t) 3)
                  ", sea without a route " (f (/ (get value :sea-unrouted 0.0) t) 4)
                  ", no route row " (f (/ (get value :no-route 0.0) t) 4)))))

(defn- portwatch-check [hs2 routes-closed]
  (println "\n3. PortWatch")
  (let [pw (read-portwatch "data/raw/material/imf-portwatch/Daily_Chokepoints_Data.csv")
        routed (fn [c] (mean (keep #(get-in hs2 [% ["all" "WLD"] :through-tonnes c]) check-years)))
        observed (fn [c] (mean (for [y check-years]
                                 (reduce + (keep #(when (= y (:year %)) (:capacity %)) (pw (cp/straits c)))))))
        pairs (for [c (sort (keys cp/straits))] [c (routed c) (observed c)])]
    (println "\n  Routed world tonnes (BACI, all chapters, maritime) against PortWatch capacity (dwt), mean a year 2019-2023")
    (doseq [[c r o] (sort-by #(- (or (nth % 2) 0)) pairs)]
      (println (str "   " c "  routed " (format "%.3g" (double (or r 0))) " t   observed " (format "%.3g" (double (or o 0))) " dwt")))
    (let [ps (for [[_ r o] pairs :when (and r o (pos? r) (pos? o))] [r o])]
      (println (str "   across " (count ps) " straits: spearman " (f (tdr/spearman ps) 2)
                    ", pearson of logs " (f (tdr/pearson (map (fn [[a b]] [(Math/log a) (Math/log b)]) ps)) 2))))
    (println "\n  Disruptions, mean daily transits (all ships; tankers; containers)")
    (doseq [[lbl c base dis] [["Red Sea: Bab-el-Mandeb" "bab-el-mandeb" ["2023-01-01" "2023-10-31"] ["2024-01-01" "2024-12-31"]]
                              ["Red Sea: Suez" "suez" ["2023-01-01" "2023-10-31"] ["2024-01-01" "2024-12-31"]]
                              ["Red Sea: Cape of Good Hope" "cape-of-good-hope" ["2023-01-01" "2023-10-31"] ["2024-01-01" "2024-12-31"]]
                              ["Panama drought" "panama" ["2022-10-01" "2023-03-31"] ["2023-10-01" "2024-03-31"]]
                              ["Suez, Ever Given" "suez" ["2021-03-01" "2021-03-22"] ["2021-03-24" "2021-03-28"]]]]
      (let [days (pw (cp/straits c))
            m (fn [k [a b]] (daily-mean days k a b))]
        (println (str "   " lbl " " (str/join " -> " [(str (first base) ".." (second base)) (str (first dis) ".." (second dis))])
                      ": all " (f (m :n base) 1) " -> " (f (m :n dis) 1) " (" (f (* 100 (dec (/ (m :n dis) (m :n base)))) 0) "%)"
                      "; tankers " (f (m :tanker base) 1) " -> " (f (m :tanker dis) 1)
                      "; containers " (f (m :container base) 1) " -> " (f (m :container dis) 1)))))
    (println "\n  Routed exposure of the flows hit, 2019-2023 (value shares of world trade through each; WLD all)")
    (doseq [c ["bab-el-mandeb" "suez" "panama" "cape-of-good-hope" "hormuz" "malacca"]]
      (println (str "   " c "  all " (f (mean (keep #(share-of hs2 % "all" "WLD" c) check-years)) 3)
                    "  hs27 " (f (mean (keep #(share-of hs2 % "hs27" "WLD" c) check-years)) 3)
                    "  hs85 " (f (mean (keep #(share-of hs2 % "hs85" "WLD" c) check-years)) 3))))
    (println "\n  Closures (routes-closed.csv): detour and stranded share of the pairs through the strait")
    (doseq [[e i cls] [["CHN" "NLD" "general"] ["CHN" "DEU" "general"] ["SAU" "NLD" "tanker"] ["QAT" "GBR" "tanker"]
                       ["USA" "JPN" "tanker"] ["USA" "CHN" "bulk"] ["SAU" "KOR" "tanker"] ["SAU" "JPN" "tanker"]]]
      (doseq [r (get routes-closed [e i cls])]
        (println (str "   " e "->" i " " cls " closed " (:closed r) ": affected " (:affected r)
                      " stranded " (:stranded r) " extra km " (:extra r)))))))

(defn- read-closed [path pairs]
  (with-open [r (io/reader path)]
    (reduce (fn [acc l]
              (let [[e i cls c a s x] (str/split l #"," -1)]
                (if (pairs [e i cls]) (update acc [e i cls] (fnil conj []) {:closed c :affected a :stranded s :extra x}) acc)))
            {} (rest (line-seq r)))))

(defn -main [& _]
  (let [t0 (System/currentTimeMillis)
        routes (cp/read-routes (str dir "/routes.csv"))
        _ (println "routes read" (- (System/currentTimeMillis) t0) "ms")
        subnodes (cp/read-subnodes (str dir "/subnodes.csv"))
        val-isos #{"JPN" "KOR" "IND" "CHN" "TWN" "DEU" "WLD"}
        [energy hs2 subs]
        (with-open [w (io/writer out)]
          (.write w "iso,year,variable,value\n")
          [(run-file w routes (str dir "/hs6-energy-pairs.csv.gz") (partial cp/hs6-flow cp/energy-products) false
                     (fn [[_ iso]] (val-isos iso)))
           (run-file w routes (str dir "/hs2-pairs.csv.gz") cp/hs2-flow true
                     (fn [[p iso]] (or (= "WLD" iso) (and (val-isos iso) (#{"all" "hs27" "hs85"} p)))))
           (run-file w routes (str dir "/hs6-essentials.csv.gz") (partial cp/hs6-flow subnodes) false
                     (fn [[_ iso]] (val-isos iso)))])]
    (println "wrote" out)
    (let [ps (pair-stats routes (str dir "/hs2-pairs.csv.gz"))]
      (coverage ps)
      (validation energy hs2 subs)
      (asia-europe ps))
    (portwatch-check hs2 (read-closed (str dir "/routes-closed.csv")
                                      #{["CHN" "NLD" "general"] ["CHN" "DEU" "general"] ["SAU" "NLD" "tanker"] ["QAT" "GBR" "tanker"]
                                        ["USA" "JPN" "tanker"] ["USA" "CHN" "bulk"] ["SAU" "KOR" "tanker"] ["SAU" "JPN" "tanker"]}))
    (shutdown-agents)))
