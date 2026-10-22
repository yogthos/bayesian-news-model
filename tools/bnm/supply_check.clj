(ns bnm.supply-check
  "A report on the product graph (bnm.supply; docs/STOCK-FLOW.md, \"Product
  graph\"), from data/derived/supply/ as scripts/product-graph.py and
  scripts/oil-products.py write it:

    the graph's size: products by group, recipe rows by kind, regions;
    the balances: refinery mass balance per region, product balances;
    EXIOBASE 2011 refinery output against the UN energy statistics' 2011
      production, per country, for the main refined products;
    validation: crude's downstream, basic plastics' and N fertiliser's
      upstream;
    two shocks, qualitatively: natural gas for use in Europe cut 40% (N
      fertiliser, then agriculture), Middle East crude output cut 50%
      (refining in Asia, against Europe and the Americas).

  The checks marked ok/FAIL are the real-data tests (kept out of the unit
  suite, which runs on synthetic graphs: they take minutes); the run
  exits 1 when one fails.

    jolt -A:tools -M -m bnm.supply-check"
  (:require [bnm.csv :as csv]
            [bnm.supply :as sup]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private failures (atom 0))

(defn- check
  "Print ok or FAIL for `pass?` with `what`; count the failures."
  [pass? what]
  (when-not pass? (swap! failures inc))
  (println (format "  %s %s" (if pass? "ok  " "FAIL") what)))

(def dir "data/derived/supply")

(def europe ["AT" "BE" "BG" "CY" "CZ" "DE" "DK" "EE" "ES" "FI" "FR" "GR" "HR" "HU" "IE" "IT" "LT" "LU"
             "LV" "MT" "NL" "PL" "PT" "RO" "SE" "SI" "SK" "GB" "NO" "CH" "WE"])

(def iso3
  {"AU" "AUS" "AT" "AUT" "BE" "BEL" "BR" "BRA" "BG" "BGR" "CA" "CAN" "CN" "CHN" "CY" "CYP" "CZ" "CZE"
   "DK" "DNK" "EE" "EST" "FI" "FIN" "FR" "FRA" "DE" "DEU" "GR" "GRC" "HU" "HUN" "HR" "HRV" "IN" "IND"
   "ID" "IDN" "IE" "IRL" "IT" "ITA" "JP" "JPN" "LV" "LVA" "LT" "LTU" "LU" "LUX" "MT" "MLT" "MX" "MEX"
   "NL" "NLD" "NO" "NOR" "PL" "POL" "PT" "PRT" "RO" "ROU" "RU" "RUS" "SK" "SVK" "SI" "SVN" "ZA" "ZAF"
   "KR" "KOR" "ES" "ESP" "SE" "SWE" "CH" "CHE" "TR" "TUR" "GB" "GBR" "US" "USA"})

(defn- rows
  "The rows of a CSV in `dir`, quoted fields read as such (oil-products'
  flow names hold commas)."
  [file]
  (with-open [r (io/reader (str (io/file dir file)))]
    (let [[h & ls] (line-seq r) ks (mapv keyword (csv/parse-line h \,))]
      (mapv #(zipmap ks (csv/parse-line % \,)) (doall ls)))))

(defn- median [xs] (let [v (vec (sort xs)) n (count v)] (when (pos? n) (v (quot n 2)))))

(defn- pct [x] (format "%.0f%%" (* 100.0 x)))

(defn- loss [o k] (if-let [a (get o k)] (- 1.0 a) 0.0))

(defn- size-report [g]
  (println "== the graph")
  (println " products" (count (:products g)) "by group"
           (into (sorted-map) (frequencies (map :group (vals (:products g))))))
  (let [rs (sup/recipe-rows g)]
    (println " regions" (count (distinct (map :region rs)))
             "; recipes (region, output)" (count (:recipes g))
             "; rows" (count rs) (frequencies (map :kind rs))
             "; physical rows" (count (filter :physical? rs)))
    (println " value shares left out (above 1) or scaled (an output's inputs worth 1 or more of it):"
             (sup/share-flags g) "rows")
    (println " use rows whose value ratio is above 1 (value_rough, not a share):"
             (count (filter :value-rough (apply concat (vals (:use g))))))))

(defn- balance-report [g]
  (println "== balances (data/derived/supply/balances.csv)")
  (let [bs (rows "balances.csv")
        refin (fn [k] (for [b bs :when (and (= (:check b) k) (seq (:residual b)) (> (parse-double (:input b)) 1e6))]
                        [(:region b) (parse-double (:residual b))]))]
    (doseq [[k what] [["refinery" "refined products out over crude, NGL, other hydrocarbons and feedstocks in"]
                      ["refinery-all" "refined products out over every hydrocarbon, product, additive and biofuel in"]]]
      (let [r (refin k) xs (map second r)]
        (println (format " %s (%s): %d regions, median %+.3f, range %+.3f to %+.3f" k what (count r)
                         (median xs) (reduce min xs) (reduce max xs)))))
    (let [ps (for [b bs :when (str/starts-with? (:check b) "product:") :when (seq (:residual b))]
               [(subs (:check b) 8) (Math/abs (parse-double (:residual b)))])
          off (filter #(> (second %) 0.01) ps)]
      (println (format " product balances: %d region-products, %d off by more than 1%%, all of them %s"
                       (count ps) (count off)
                       (str/join "/" (distinct (map #(:group (get-in g [:products (first %)])) off))))))))

(defn- unsd-report
  "EXIOBASE's 2011 refinery output against UNSD's 2011 production, per
  product (or products together: jet and other kerosene, which countries
  split differently; Spain reports its jet as other kerosene)."
  []
  (println "== EXIOBASE 2011 production against UNSD 2011 (flow 01, kt; countries above 100 kt)")
  (let [ex (into {} (for [r (rows "production.csv") :when (= "tonnes" (:unit r))]
                      [[(:region r) (:product r)] (/ (parse-double (:quantity r)) 1000.0)]))
        un (into {} (for [r (rows "oil-products.csv") :when (and (= "2011" (:year r)) (= "01" (:flow r)))]
                      [[(:iso r) (:exiobase r)] (parse-double (:value r))]))]
    (doseq [ps [["C_MGSL"] ["C_DOIL"] ["C_KJET"] ["C_KERO"] ["C_KJET" "C_KERO"] ["C_FOIL"]
                ["C_NAPT"] ["C_LPGA"] ["C_RGAS"] ["C_BITU"] ["C_NSPP"]]]
      (let [sum (fn [m k ps] (let [xs (keep #(m [k %]) ps)] (when (seq xs) (reduce + xs))))
            ratios (for [[r i] iso3 :let [e (sum ex r ps) u (sum un i ps)] :when (and e u (> u 100.0))] [r (/ e u)])
            xs (map second ratios)]
        (when (seq xs)
          (println (format " %-13s %2d countries, EXIOBASE/UNSD median %.2f, within 20%% %2d, off most %s"
                           (str/join "+" ps) (count ratios) (median xs) (count (filter #(< 0.8 % 1.2) xs))
                           (str/join " " (map (fn [[r x]] (format "%s %.2f" r x))
                                              (take 4 (sort-by #(- (Math/abs (Math/log (second %)))) ratios)))))))))))

(defn- validation-report [g]
  (println "== validation")
  (let [d (sup/downstream g "US" "C_COIL")
        ps (set (map :product (filter #(= "US" (:region %)) (:products d))))
        is (set (map :industry (filter #(= "US" (:region %)) (:industries d))))]
    (println " US crude downstream:" (count (:products d)) "products," (count (:industries d)) "industries (all regions)")
    (check (every? (set (map :product (:products d))) ["C_MGSL" "C_KJET" "C_DOIL" "C_NAPT" "C_PLAS"])
           "US crude reaches gasoline, jet, diesel, naphtha and basic plastics")
    (check (some #{"A_TLND" "A_TAIR" "A_TWAS"} (map :industry (:industries d))) "and transport")
    (check (some #{"A_CHEM" "A_PLAS"} (map :industry (:industries d))) "and chemicals")
    (doseq [p ["C_MGSL" "C_KJET" "C_DOIL" "C_NAPT" "C_PLAS"]]
      (let [x (first (filter #(= ["US" p] [(:region %) (:product %)]) (:products d)))]
        (println (format "  %s %-7s depth %s dependence %.3f exposure %.3f" (if x "ok  " "MISS") p
                         (:depth x) (or (:dependence x) 0.0) (or (:exposure x) 0.0)))))
    (doseq [[what cs] [["transport" ["A_TLND" "A_TAIR" "A_TWAS" "A_TWAI" "A_TRAI"]] ["chemicals" ["A_CHEM" "A_PLAS"]]]]
      (println (format "  %s %s: %s" (if (some is cs) "ok  " "MISS") what
                       (str/join ", " (for [x (:industries d) :when (and (= "US" (:region x)) (some #{(:industry x)} cs))]
                                        (format "%s depth %d dep %.3f exp %.3f" (:industry x) (:depth x) (:dependence x) (:exposure x))))))))
  (doseq [[r p needs] [["DE" "C_PLAS" [["C_NAPT" "C_COIL"] ["C_GASE"]]] ["US" "C_PLAS" [["C_NAPT" "C_COIL"] ["C_GASE"]]]
                       ["DE" "C_NFER" [["C_GASE"]]] ["US" "C_NFER" [["C_GASE"]]] ["CN" "C_NFER" [["C_GASE"]]]]]
    (let [u (sup/upstream g r p)
          by (reduce (fn [m {:keys [product quantity dependence]}]
                       (-> m (update-in [product :q] (fnil + 0.0) quantity) (update-in [product :v] (fnil + 0.0) dependence)))
                     {} u)]
      (doseq [alts needs]
        (let [hit (filter by alts)]
          (check (seq hit) (format "%s %s upstream %s: %s" r p (str/join "/" alts)
                                   (str/join ", " (for [h hit] (format "%s %.3f t/t, dependence %.3f" h (get-in by [h :q]) (get-in by [h :v])))))))))))

(defn- shock-report [g]
  (let [h (sup/hypergraph g)]
    (println "== shock 1: natural gas for use in Europe cut 40% (all origins)")
    (let [o (sup/outage g (into {} (for [r europe] [[r "C_GASE"] 0.4])) {:at :use :hypergraph h})
          po (:products o) io (:industries o)]
      (println " region-products losing 1% or more:" (count (filter #(< % 0.99) (vals po)))
               "; region-industries:" (count (filter #(< % 0.99) (vals io)))
               "; settled" (:settled o))
      (doseq [r ["DE" "FR" "PL"]]
        (check (< 0.35 (loss po [r "C_NFER"])) (str r " N fertiliser loses over 35% (gas its feedstock)"))
        (check (< 0.0 (loss po [r "C_WHEA"]) (loss po [r "C_NFER"])) (str r " wheat loses something, less than N fertiliser"))
        (check (< 0.3 (loss po [r "C_POWG"])) (str r " gas-fired electricity loses over 30%")))
      (check (< (loss po ["US" "C_NFER"]) 0.1) "America's N fertiliser barely")
      (println " loss by product, Europe's four largest N fertiliser makers and two outside:")
      (doseq [r ["DE" "FR" "NL" "PL" "US" "CN"]]
        (println (format "  %s  N fert %s  P fert %s  wheat %s  cereals %s  vegetables %s  cattle %s  gas power %s  steel %s"
                         r (pct (loss po [r "C_NFER"])) (pct (loss po [r "C_PFER"])) (pct (loss po [r "C_WHEA"]))
                         (pct (loss po [r "C_OCER"])) (pct (loss po [r "C_FVEG"])) (pct (loss po [r "C_CATL"]))
                         (pct (loss po [r "C_POWG"])) (pct (loss po [r "C_STEL"])))))
      (println " hardest hit industries in Europe:")
      (doseq [[[r j] a] (take 12 (sort-by val (filter (fn [[[r _] _]] (some #{r} europe)) io)))]
        (println (format "  %s %s %s" r j (pct (- 1.0 a))))))
    (println "== shock 2: Middle East (WM) crude output cut 50%")
    (let [o (sup/outage g {["WM" "C_COIL"] 0.5} {:hypergraph h})
          po (:products o) io (:industries o)]
      (println " region-products losing 1% or more:" (count (filter #(< % 0.99) (vals po)))
               "; region-industries:" (count (filter #(< % 0.99) (vals io)))
               "; settled" (:settled o))
      (doseq [r ["WM" "IN" "KR" "JP" "CN" "WA" "ID" "DE" "FR" "GB" "US" "BR" "RU"]]
        (println (format "  %s  refining %s  gasoline %s  diesel %s  jet %s  naphtha %s  plastics %s"
                         r (pct (loss io [r "A_REFN"])) (pct (loss po [r "C_MGSL"])) (pct (loss po [r "C_DOIL"]))
                         (pct (loss po [r "C_KJET"])) (pct (loss po [r "C_NAPT"])) (pct (loss po [r "C_PLAS"])))))
      (let [asia ["CN" "IN" "JP" "KR" "ID" "WA"] west ["DE" "FR" "GB" "IT" "ES" "NL" "US" "CA" "BR" "MX"]
            mean (fn [rs] (/ (reduce + (map #(loss io [% "A_REFN"]) rs)) (count rs)))]
        (println (format " refining loss, mean: Asia (CN IN JP KR ID WA) %s, Europe and the Americas %s"
                         (pct (mean asia)) (pct (mean west))))
        (check (> (mean asia) (mean west)) "Asian refining loses more than Europe's and the Americas'")
        (check (> (loss io ["JP" "A_REFN"]) (loss io ["US" "A_REFN"])) "Japan's more than America's")))))

(defn -main [& _]
  (if-not (.exists (io/file dir "recipes.csv"))
    (println "bnm.supply-check: no" dir "- run python -I scripts/product-graph.py first")
    (let [g (sup/load-graph dir)]
      (size-report g)
      (balance-report g)
      (when (.exists (io/file dir "oil-products.csv")) (unsd-report))
      (validation-report g)
      (shock-report g)
      (println (format "== checks: %d failed" @failures))
      (when (pos? @failures) (System/exit 1)))))
