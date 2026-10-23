(ns bnm.supply-check
  "A report on the product graph (bnm.supply; docs/STOCK-FLOW.md, \"Product
  graph\"), from data/derived/supply/ as scripts/product-graph.py and
  scripts/oil-products.py write it:

    the graph's size: products by group, recipe rows by kind, regions;
    the balances: refinery mass balance per region, product balances;
    EXIOBASE 2011 refinery output against the UN energy statistics' 2011
      production, per country, for the main refined products;
    the light products' scaling to UNSD (scaling.csv);
    validation: crude's downstream, basic plastics' and N fertiliser's
      upstream;
    Germany 2022: gas for use in Germany cut 26%, industry's cut July
      2022 to March 2023 (Moll, Schularick & Zachmann 2023), against what
      followed: energy-intensive output down by up to 20%, the rest of
      industry hardly;
    three shocks, qualitatively: natural gas for use in Europe cut 40% (N
      fertiliser, then agriculture, steel), Middle East crude output cut
      50% (refining in Asia, against Europe and the Americas), US lignite
      cut 10% and Indonesia's coal 50% (a station's coals one pool);
    the rare-earth chain (bnm.rare-earths): its sub-nodes' sizes and the
      split's balances, supply shares from production, China's separation
      cut 50% down to magnets, EV motors and wind generators, the chain's
      multipliers against chemicals n.e.c.'s, and the shocks above with
      and without the sub-nodes.

  The checks marked ok/FAIL are the real-data tests (kept out of the unit
  suite, which runs on synthetic graphs: they take minutes); the run
  exits 1 when one fails.

    jolt -A:tools -M -m bnm.supply-check"
  (:require [bnm.csv :as csv]
            [bnm.essentiality :as es]
            [bnm.food :as food]
            [bnm.rare-earths :as re]
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

(defn- rule-report
  "Physical evidence against value shares (sup/negligible-heat): the rows
  the hybrid table finds absent or negligible, the artefacts that made
  critical, and the feedstocks that stay critical."
  [g]
  (println "== physical evidence decides (sup/negligible-heat)")
  (let [rs (sup/recipe-rows g)
        neg (filter :negligible rs)
        row (fn [r o i] (get-in g [:recipes [r o] i]))]
    (println (format " %d rows absent or negligible in the hybrid table (%d with a value share of 5%% or more), left with value added"
                     (count neg) (count (filter #(>= (or (:share %) 0.0) sup/critical-share) neg))))
    (doseq [r ["IT" "NL" "PL"]]
      (let [x (row r "C_NFER" "C_COIL")]
        (check (and x (= :substitutable (:kind x)) (:negligible x))
               (format "%s N fertiliser's crude (%.0f%% of its value, %s t per t) is not critical" r
                       (* 100 (or (:share x) 0.0)) (:phys x)))))
    (doseq [r ["NL" "GB" "HU"]]
      (let [x (row r "C_POWN" "C_GASE")]
        (check (and x (= :substitutable (:kind x)) (:negligible x))
               (format "%s nuclear power's natural gas (%.2f%% of its value, %.2g t per TJ) is not critical, nor an input" r
                       (* 100 (or (:share x) 0.0)) (:phys x)))))
    (doseq [[o i what] [["C_NFER" "C_GASE" "gas into N fertiliser"] ["C_PLAS" "C_NAPT" "naphtha into plastics"]
                        ["C_STEL" "C_COKE" "coke into steel"]]]
      (let [xs (filter #(and (= o (:output %)) (= i (:input %)) (>= (or (:phys %) 0.0) sup/feedstock)) rs)]
        (check (and (seq xs) (every? #(= :critical (:kind %)) xs))
               (format "%s critical in every region with %.1f t per t or more (%d)" what sup/feedstock (count xs)))))))

(defn- override-report
  "The hybrid rows scripts/product-graph.py overrode (overrides.csv): each
  in the graph at its cited value, and what it decides."
  [g]
  (println "== hybrid rows overridden (overrides.csv)")
  (let [os (rows "overrides.csv")
        row (fn [r o i] (get-in g [:recipes [r o] i]))
        written (filter #(row (:region %) (:output %) (:input %)) os)]
    (doseq [[src xs] (sort-by key (group-by :source os))]
      (println (format " %-31s %4d rows in %3d recipes" src (count xs) (count (distinct (map (juxt :region :output) xs))))))
    (check (and (seq os) (every? #(let [x (row (:region %) (:output %) (:input %))]
                                    (and (= (:source %) (:override x))
                                         (< (Math/abs (- (:phys x) (parse-double (:value %)))) (* 1e-5 (parse-double (:value %))))))
                                 written)
                (= (count written) (count os)))
           (format "every overridden row is in the graph at its value, with its source (%d)" (count os)))
    (doseq [r ["LT" "TR" "WA" "WL" "WF" "PL" "HR" "GB"]]
      (let [x (row r "C_NFER" "C_GASE")]
        (check (and x (= "usgs-ammonia" (:override x)) (= :critical (:kind x)))
               (format "%s N fertiliser's gas at %.3f t per t (USGS ammonia), critical" r (or (:phys x) 0.0)))))
    (let [mine #{"unsd-mining-electricity" "unsd-mining-electricity-median"}
          rs (filter #(mine (:override %)) (sup/recipe-rows g))]
      (check (and (seq rs) (not-any? :negligible rs))
             (format "no overridden mining electricity row is negligible (%d rows)" (count rs)))
      (doseq [r ["DE" "ES" "CN"]]
        (let [x (row r "C_SDCL" "C_POWC")]
          (println (format "  %s sand and clay: coal power %s TJ per t, %s, %s" r (:phys x) (:override x) (some-> (:kind x) name))))))))

(defn- balance-report [g]
  (println "== balances (data/derived/supply/balances.csv)")
  (let [bs (rows "balances.csv")
        refin (fn [k] (for [b bs :when (and (= (:check b) k) (seq (:residual b)) (> (parse-double (:input b)) 1e6))]
                        [(:region b) (parse-double (:residual b))]))]
    (doseq [[k what] [["refinery-unscaled" "refined products out over crude, NGL, other hydrocarbons and feedstocks in, before the UNSD scaling"]
                      ["refinery" "the same, scaled"]
                      ["refinery-all-unscaled" "refined products out over every hydrocarbon, product, additive and biofuel in, before"]
                      ["refinery-all" "the same, scaled"]
                      ["refinery-nogas-unscaled" "against all that but natural gas (gas processing's dry gas goes back to the grid unrecorded), before"]
                      ["refinery-nogas" "the same, scaled: a refinery's own fuel and losses, 6-8% in practice"]]]
      (let [r (refin k) xs (map second r)]
        (when (seq xs)
          (println (format " %s (%s): %d regions, median %+.3f, range %+.3f to %+.3f" k what (count r)
                           (median xs) (reduce min xs) (reduce max xs))))))
    (let [ps (for [b bs :when (str/starts-with? (:check b) "product:") :when (seq (:residual b))]
               [(subs (:check b) 8) (Math/abs (parse-double (:residual b)))])
          off (filter #(> (second %) 0.01) ps)
          scaled #{"C_NAPT" "C_LPGA" "C_BITU" "C_NSPP" "C_KJET" "C_KERO"}
          sc (filter (comp scaled first) ps)]
      (println (format " product balances: %d region-products, %d off by more than 1%%, all of them %s"
                       (count ps) (count off)
                       (str/join "/" (distinct (map #(:group (get-in g [:products (first %)])) off)))))
      (check (and (seq sc) (every? #(< (second %) 1e-3) sc))
             (format "the scaled products' supply equals their use in every region, to the tables' rounding (%d region-products, largest %.1g)"
                     (count sc) (reduce max 0.0 (map second sc)))))))

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

(defn- scaling-report
  "What scripts/product-graph.py did to naphtha, LPG, bitumen, other
  products and kerosene (scaling.csv): EXIOBASE's 2011 figures against
  UNSD's, fitted to them by origin (production) and destination (use)."
  []
  (println "== light products scaled to UNSD 2011 (scaling.csv)")
  (let [rs (rows "scaling.csv")
        n #(when (seq %) (parse-double %))
        by (into {} (for [r rs] [[(:region r) (:products r) (:kind r)] r]))]
    (doseq [[[ps kind] xs] (sort-by key (group-by (juxt :products :kind) rs))]
      (let [un (filter #(= "unsd" (:source %)) xs)
            before (map #(/ (n (:exiobase_kt %)) (n (:unsd_kt %))) un)
            after (map #(/ (n (:fitted_kt %)) (n (:unsd_kt %))) un)
            tot (fn [k] (/ (reduce + (keep #(n (k %)) xs)) 1000.0))]
        (println (format " %-13s %-10s %2d regions to UNSD: EXIOBASE/UNSD median %.2f before, %.2f after (%.2f-%.2f); world %.0f -> %.0f Mt"
                         ps kind (count un) (median before) (median after) (reduce min after) (reduce max after)
                         (tot :exiobase_kt) (tot :fitted_kt)))
        (when (= kind "production")
          (check (< 0.95 (median after) 1.05) (str ps " production fitted to UNSD (median within 5%)")))))
    (println " the rest of the world, naphtha (kt; UNSD the sum of each region's countries):")
    (doseq [r ["WA" "WE" "WF" "WL" "WM"] k ["production" "use"]
            :let [x (by [r "C_NAPT" k])] :when x]
      (println (format "  %s %-10s EXIOBASE %6.0f  UNSD %6s  fitted %6.0f  %s" r k (n (:exiobase_kt x))
                       (or (:unsd_kt x) "-") (n (:fitted_kt x)) (:source x))))
    (let [x (by ["WM" "C_NAPT" "use"])]
      (check (and x (= "unsd" (:source x)) (< 0.95 (/ (n (:fitted_kt x)) (n (:unsd_kt x))) 1.05))
             "the Middle East's naphtha use fitted to UNSD's (no longer a free destination)"))))

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

(def energy-intensive
  "Destatis's energy-intensive industries (Moll et al. 2023, figure 6):
  paper, coke and refined products, chemicals, basic metals, non-metallic
  minerals."
  (into #{"C_PULP" "C_PAPE" "C_COKE" "C_CHEM" "C_NFER" "C_PFER" "C_PLAS" "C_STEL" "C_PREM" "C_ALUM" "C_LZTP"
          "C_COPP" "C_ONFM" "C_METC" "C_GLAS" "C_CRMC" "C_BRIK" "C_CMNT" "C_ONMM"}
        sup/refined))

(defn- germany-2022
  "Gas for use in Germany cut 26% (industry's cut, Moll et al. 2023):
  energy-intensive output and the rest of manufacturing, weighted by
  their 2011 output value, at the energy bundle's elasticity and two
  others."
  [g h]
  (println "== Germany 2022: gas for use in Germany cut 26% (industry's cut July 2022 - March 2023, Moll et al. 2023)")
  (let [value (into {} (for [r (rows "production.csv") :when (and (= "DE" (:region r)) (seq (:value_2011 r)))]
                         [(:product r) (parse-double (:value_2011 r))]))
        ;; value added: output less its inputs (Destatis's index weights
        ;; by value added, so refining, big in output and thin in value
        ;; added, does not swamp it)
        va (into {} (for [[p v] value
                          :let [ins (get-in g [:recipes ["DE" p]])
                                s (reduce + 0.0 (keep (comp :share val) ins))]]
                      [p (* v (max 0.0 (- 1.0 s)))]))
        other (set (for [[c {gr :group}] (:products g) :when (or (= gr "manufactured") (#{"C_TEXT" "C_WOOD"} c))] c))
        wmean (fn [wt po ps] (let [ps (filter wt ps)]
                               (/ (reduce + (map #(* (wt %) (loss po ["DE" %])) ps)) (reduce + (map wt ps)))))
        mean (partial wmean value)
        run (fn [h] (:products (sup/outage g {["DE" "C_GASE"] 0.26} {:at :use :hypergraph h})))
        po (run h)
        ei (mean po energy-intensive) ot (mean po other)
        ei-va (wmean va po energy-intensive) ot-va (wmean va po other)]
    (println (format " energy-intensive output %s, other manufacturing %s (weighted by 2011 output value)" (pct ei) (pct ot)))
    (println (format " energy-intensive output %s, other manufacturing %s (weighted by 2011 value added, as Destatis's index; it fell close to 20%% at its low, the rest hardly)"
                     (pct ei-va) (pct ot-va)))
    (println (format "  N fert %s  chemicals %s  plastics %s  paper %s  glass %s  ceramics %s  cement %s  steel %s  aluminium %s  machinery %s  vehicles %s"
                     (pct (loss po ["DE" "C_NFER"])) (pct (loss po ["DE" "C_CHEM"])) (pct (loss po ["DE" "C_PLAS"]))
                     (pct (loss po ["DE" "C_PAPE"])) (pct (loss po ["DE" "C_GLAS"])) (pct (loss po ["DE" "C_CRMC"]))
                     (pct (loss po ["DE" "C_CMNT"])) (pct (loss po ["DE" "C_STEL"])) (pct (loss po ["DE" "C_ALUM"]))
                     (pct (loss po ["DE" "C_MACH"])) (pct (loss po ["DE" "C_MOTO"]))))
    (check (< 0.02 ei-va 0.26) "energy-intensive output falls, by less than the gas")
    (check (< ot-va (/ ei-va 2)) "the rest of manufacturing by less than half as much")
    (doseq [es [0.25 1.0]]
      (let [po (run (sup/hypergraph g sup/default-sigma 1e-3 es))]
        (println (format "  energy-sigma %.2f: energy-intensive %s, other %s (value added), chemicals %s, paper %s, steel %s"
                         es (pct (wmean va po energy-intensive)) (pct (wmean va po other))
                         (pct (loss po ["DE" "C_CHEM"])) (pct (loss po ["DE" "C_PAPE"])) (pct (loss po ["DE" "C_STEL"]))))))))

(defn- shock-report [g h]
  (do
    (germany-2022 g h)
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
      (let [steel ["DE" "FR" "NL" "PL" "IT" "ES" "GB" "BE" "AT" "CZ"]
            ls (map #(loss po [% "C_STEL"]) steel)]
        (println " steel:" (str/join "  " (map #(str %1 " " (pct %2)) steel ls)))
        (check (every? #(< % 0.36) ls) "steel loses less than 90% of the cut everywhere (gas its heat, not its feedstock)")
        (check (< (/ (reduce + ls) (count ls)) 0.25) "and well under the cut on average"))
      (check (< (loss po ["DE" "C_FVEG"]) 0.05) "Germany's vegetables lose under 5% (machinery is no ingredient of a crop)")
      (let [made (fn [p] (filter #(get-in g [:recipes [% p]]) europe))
            gp (map #(loss po [% "C_POWG"]) (made "C_POWG"))
            nu (map (juxt identity #(loss po [% "C_POWN"])) (made "C_POWN"))]
        (println (format " gas power: %d European regions, loss %s to %s; nuclear: %s" (count gp)
                         (pct (reduce min gp)) (pct (reduce max gp))
                         (str/join " " (for [[r x] nu] (str r " " (pct x))))))
        (check (every? #(< 0.35 % 0.45) gp) "gas power loses about the cut in every European region making it")
        ;; not zero in the steady state: nuclear fuel buys chemicals
        ;; (conversion and enrichment, 34% of its value in Germany); with
        ;; its fuel stocks (stock-report) it loses nothing for years
        (check (and (every? #(< (second %) 0.2) nu) (< (median (map second nu)) 0.1))
               "nuclear power well under gas power in the steady state (under 20% everywhere, median under 10%: its fuel's chemicals)"))
      (println " loss by product, Europe's four largest N fertiliser makers and two outside:")
      (doseq [r ["DE" "FR" "NL" "PL" "US" "CN"]]
        (println (format "  %s  N fert %s  P fert %s  wheat %s  cereals %s  vegetables %s  cattle %s  gas power %s  steel %s"
                         r (pct (loss po [r "C_NFER"])) (pct (loss po [r "C_PFER"])) (pct (loss po [r "C_WHEA"]))
                         (pct (loss po [r "C_OCER"])) (pct (loss po [r "C_FVEG"])) (pct (loss po [r "C_CATL"]))
                         (pct (loss po [r "C_POWG"])) (pct (loss po [r "C_STEL"])))))
      (println " hardest hit industries in Europe:")
      (doseq [[[r j] a] (take 12 (sort-by val (filter (fn [[[r _] _]] (some #{r} europe)) io)))]
        (println (format "  %s %s %s" r j (pct (- 1.0 a))))))
    (println "== natural gas for use cut 40% in the Middle East and Mexico (gas-based DRI, sup/dri-share)")
    (let [cut {["WM" "C_GASE"] 0.4 ["MX" "C_GASE"] 0.4}
          o (:products (sup/outage g cut {:at :use :hypergraph h}))
          heat (with-redefs [sup/dri-share {}] (:products (sup/outage g cut {:at :use})))]
      (doseq [r ["WM" "MX"]]
        (println (format "  %s steel %s with the DRI share %.1f, %s with its gas as heat only; N fert %s, gas power %s"
                         r (pct (loss o [r "C_STEL"])) (sup/dri-share r) (pct (loss heat [r "C_STEL"]))
                         (pct (loss o [r "C_NFER"])) (pct (loss o [r "C_POWG"]))))
        (check (< (- (* 0.4 (sup/dri-share r)) 1e-3) (loss o [r "C_STEL"])) (str r " steel loses at least its DRI part of the cut"))))
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
        (check (> (loss io ["JP" "A_REFN"]) (loss io ["US" "A_REFN"])) "Japan's more than America's")))
    (println "== shock 3: coal, a power station's coals one pool (sup/coal-pooled)")
    (let [row (fn [r o i] (get-in g [:recipes [r o] i]))
          us (for [i ["C_SUBC" "C_OTBC" "C_LIBC"]] (row "US" "C_POWC" i))
          lig (/ (or (:share (row "US" "C_POWC" "C_LIBC")) 0.0) (reduce + (map #(or (:share %) 0.0) us)))
          po (:products (sup/outage g {["US" "C_LIBC"] 0.1} {:hypergraph h}))
          id (:products (sup/outage g {["ID" "C_SUBC"] 0.5 ["ID" "C_OTBC"] 0.5 ["ID" "C_LIBC"] 0.5} {:hypergraph h}))]
      (println (format " US coal power's coals: %s; lignite %.1f%% of their value"
                       (str/join " " (for [[i r] (map vector ["C_SUBC" "C_OTBC" "C_LIBC"] us)] (str i " " (some-> (:pool r) name))))
                       (* 100 lig)))
      (check (every? #(= :coal (:pool %)) us) "America's coal power: sub-bituminous, other bituminous and lignite one pool")
      (check (< (Math/abs (- (loss po ["US" "C_POWC"]) (* 0.1 lig))) 0.002)
             (format "US lignite cut 10%%: its coal power loses %s, lignite's share of the pool (not 10%%)" (pct (loss po ["US" "C_POWC"]))))
      (println " Indonesia's coal output cut 50%, coal power:"
               (str/join "  " (for [r ["ID" "IN" "CN" "JP" "KR" "WA" "SI" "RO"]] (str r " " (pct (loss id [r "C_POWC"])))))))))

(def report-months [1 3 6 12 24])

(defn- path-line
  "One line per node `k` of a path: its loss at report-months, and the
  steady state's (outage)."
  [label path steady k]
  (println (format "  %-26s %s   steady %s" label
                   (str/join " " (for [t report-months] (format "%4s" (pct (loss (:products (nth path (dec t))) k)))))
                   (pct (loss steady k)))))

(defn- ind-line
  "path-line for an industry."
  [label path steady k]
  (println (format "  %-26s %s   steady %s" label
                   (str/join " " (for [t report-months] (format "%4s" (pct (loss (:industries (nth path (dec t))) k)))))
                   (pct (loss steady k)))))

(defn- rises
  "The largest rise of any product's availability from one month to the
  next (a stock only falls, so a path only falls: at most tol)."
  [path]
  (reduce max 0.0 (for [[a b] (partition 2 1 path)
                        k (distinct (concat (keys (:products a)) (keys (:products b))))]
                    (- (get-in b [:products k] 1.0) (get-in a [:products k] 1.0)))))

(defn- below-steady
  "The largest amount by which a path's availability falls below the
  steady state's anywhere (a stock only helps: at most tol)."
  [path steady]
  (reduce max 0.0 (for [m path [k v] (:products m)] (- (get steady k 1.0) v))))

(defn- stock-report
  "Stocks (data/derived/supply/stocks.csv) and the outage month by month
  (sup/outage-path): the European gas cut, the Middle East crude cut and
  a wheat harvest shortfall, at months 1, 3, 6, 12 and 24 against the
  steady state (sup/outage)."
  [g h rows]
  (println "== stocks (stocks.csv, months of use; sup/outage-path)")
  (doseq [[[p b] rs] (sort-by key (group-by (juxt :product :basis) rows))]
    (let [ms (sort (map :months rs))]
      (println (format "  %-7s %-5s %2d regions, median %5.2f months (%.2f-%.2f)  %s" p b (count rs)
                       (median ms) (first ms) (last ms) (:source (first rs))))))
  (let [st (sup/stocks rows)
        months (apply max report-months)
        hdr (fn [what] (println (format "  %-26s %s" (str what ", loss at month") (str/join " " (map #(format "%4d" %) report-months)))))]
    (println "== shock 1 with stocks: natural gas for use in Europe cut 40%")
    (let [cut (into {} (for [r europe] [[r "C_GASE"] 0.4]))
          steady (:products (sup/outage g cut {:at :use :hypergraph h}))
          path (sup/outage-path g cut st {:at :use :hypergraph h :months months})
          nuc (filter #(get-in g [:recipes [% "C_POWN"]]) europe)]
      (println " updates by month" (mapv :updates path))
      (hdr "product")
      (doseq [r ["DE" "FR" "IT" "NL" "PL" "GB"]]
        (println (format "  %s gas storage %.2f months, fertiliser %.1f" r (get st [r "C_GASE"] 0.0) (get st [r "C_NFER"] 0.0)))
        (doseq [[p what] [["C_NFER" "N fertiliser"] ["C_POWG" "gas power"] ["C_POWN" "nuclear power"]
                          ["C_WHEA" "wheat"] ["C_STEL" "steel"] ["C_CHEM" "chemicals"]]]
          (when (get-in g [:recipes [r p]]) (path-line (str r " " what) path steady [r p]))))
      ;; its fuel, the critical input, is held by its stocks; its upkeep
      ;; (construction, metals, machinery) waits for the next planned
      ;; outage (sup/upkeep-months), so it loses near nothing in a year
      (let [m12 (reduce max 0.0 (for [m (take 12 path) r nuc] (loss (:products m) [r "C_POWN"])))
            drawn (reduce max 0.0 (for [r nuc :let [s0 (get st [r "C_NUCF"])] :when s0]
                                    (/ (- s0 (get-in (nth path 11) [:stocks [r "C_NUCF"]] s0)) s0)))
            up (for [[[r p i] m] (:upkeep (nth path 11)) :when (and (= p "C_POWN") (some #{r} nuc))] [r i m])
            least (when (seq up) (apply min-key #(nth % 2) up))]
        (println (format "  nuclear upkeep (%.0f months deferrable): least left at month 12 %s"
                         sup/upkeep-months (if least (apply format "%s %s %.2f months" least) "none drawn")))
        (check (< m12 0.01) (format "nuclear power loses under 1%% through month 12 in all %d European regions making it (largest %.2f%%)"
                                    (count nuc) (* 100 m12)))
        (check (< drawn 0.1) (format "its fuel stocks hold it: under a tenth of them drawn by month 12 (most %.1f%%)" (* 100 drawn))))
      (let [stored (filter #(> (get st [% "C_GASE"] 0.0) 1.0) ["DE" "FR" "IT" "NL" "PL" "AT" "HU" "CZ" "SK"])]
        (check (every? #(< (loss (:products (first path)) [% "C_NFER"]) (* 0.1 (loss steady [% "C_NFER"]))) stored)
               (str "gas storage delays the N fertiliser cut: under a tenth of its steady loss in month 1 where storage holds over a month of use ("
                    (str/join " " stored) ")"))
        (let [drained (filter #(< (get st [% "C_GASE"] 0.0) 4.0) stored)]
          (check (every? #(< (Math/abs (- (loss (:products (nth path 11)) [% "C_NFER"]) (loss steady [% "C_NFER"]))) 0.01) drained)
                 (str "and the steady loss by month 12 where the storage (under 4 months at 0.4 a month) is drawn ("
                      (str/join " " drained) ")"))))
      (check (< (below-steady path steady) 1e-4) "no month loses more than the steady state (stocks only help)")
      (check (< (rises path) 1e-4) "no product's availability rises from one month to the next (stocks only fall)"))
    (println "== shock 2 with stocks: Middle East (WM) crude output cut 50%")
    (let [cut {["WM" "C_COIL"] 0.5}
          o (sup/outage g cut {:hypergraph h})
          steady (:products o)
          path (sup/outage-path g cut st {:hypergraph h :months months})
          asia ["CN" "IN" "JP" "KR" "ID" "WA"]]
      (println " updates by month" (mapv :updates path))
      (hdr "refining industry")
      (doseq [r ["WM" "JP" "IN" "KR" "CN" "WA" "DE" "US"]]
        (println (format "  %s crude stock %.2f months, diesel %.2f" r (get st [r "C_COIL"] 0.0) (get st [r "C_DOIL"] 0.0)))
        (ind-line (str r " refining") path (:industries o) [r "A_REFN"])
        (doseq [[p what] [["C_DOIL" "diesel"] ["C_PLAS" "plastics"]]]
          (path-line (str r " " what) path steady [r p])))
      (check (every? #(< (loss (:industries (first path)) [% "A_REFN"]) (loss (:industries o) [% "A_REFN"])) ["JP" "IN" "KR" "CN"])
             "crude stocks hold Asia's refining above its steady loss in month 1 (JP IN KR CN)")
      (check (< (loss (:industries (first path)) ["CN" "A_REFN"]) (* 0.5 (loss (:industries o) ["CN" "A_REFN"])))
             (format "China's crude stock (a prior, %.2f months) holds its refining under half its steady loss in month 1"
                     (get st ["CN" "C_COIL"] 0.0)))
      (check (< (rises path) 1e-4) "no product's availability rises from one month to the next")
      (let [jp (map #(loss (:industries %) ["JP" "A_REFN"]) path)]
        (check (every? (fn [[a b]] (<= a (+ b 1e-6))) (partition 2 1 jp)) "Japan's refining loss never falls back as its stocks drain"))
      (check (< (Math/abs (- (loss (:industries (last path)) ["JP" "A_REFN"]) (loss (:industries o) ["JP" "A_REFN"]))) 0.01)
             "Japan's refining at its steady loss by month 24, the stocks drained")
      (check (< (below-steady path steady) 1e-4) "no month loses more than the steady state"))
    (println "== a wheat harvest 30% short everywhere, with grain stocks (bnm.food's storage pressure)")
    (let [regions (distinct (map first (keys (:recipes g))))
          cut (into {} (for [r regions :when (get-in g [:recipes [r "C_WHEA"]])] [[r "C_WHEA"] 0.3]))
          steady (:products (sup/outage g cut {:hypergraph h}))
          path (sup/outage-path g cut st {:hypergraph h :months months})
          use-t (into {} (for [r regions] [r (reduce + 0.0 (keep :quantity (get-in g [:use [r "C_WHEA"]])))]))
          world-use (reduce + (vals use-t))
          stu (fn [s] (/ (reduce + (for [r regions] (* (get s [r "C_WHEA"] 0.0) (/ (use-t r) 12.0)))) world-use))
          stu0 (stu st)
          pressures (mapv #(food/storage-pressure (max 1e-9 (stu (:stocks %))) stu0 food/pressure-c) path)]
      (println (format " world wheat stocks %.2f months of use at the start (stocks-to-use %.3f)" (* 12 stu0) stu0))
      (hdr "product")
      (doseq [r ["US" "CN" "IN" "RU" "DE" "WM" "WF"]]
        (doseq [[p what] [["C_OFOD" "food products nec"] ["C_CATL" "cattle"]]]
          (path-line (str r " " what) path steady [r p])))
      (println (format "  storage pressure (bnm.food, reference the stocks at the start): %s"
                       (str/join " " (for [t report-months] (format "%.2f" (nth pressures (dec t)))))))
      (check (every? #(< (loss (:products (first path)) [% "C_OFOD"]) (* 0.5 (max 1e-9 (loss steady [% "C_OFOD"])))) ["US" "CN" "IN"])
             "grain stocks buffer the shortfall: food products lose under half their steady loss in month 1 (US CN IN)")
      (check (apply < (take 6 pressures)) "storage pressure rises month by month as the stocks are drawn (months 1-6)")
      (check (< (below-steady path steady) 1e-4) "no month loses more than the steady state"))))

;; ---------------------------------------------------------------- rare earths

(defn- world-avail
  "The output-weighted availability of `product` over regions (tonnes in
  the graph's production rows) in an outage's :products."
  [g products product]
  (let [q (for [r (:production g) :when (= product (:product r))
                :let [x (parse-double (:quantity r))] :when (pos? x)]
            [(:region r) x])
        t (reduce + 0.0 (map second q))]
    (/ (reduce + 0.0 (for [[r x] q] (* x (get products [r product] 1.0)))) t)))

(defn- multiplier
  "Value added lost in the world (EUR M, 2011) per EUR of use lost when
  every region's use of `product` is cut by `f` (steady state), as the
  essentiality losses read it (bnm.essentiality: lambda = loss / (f x use))."
  [g h va uses product f]
  (let [regions (distinct (map first (keys (:recipes g))))
        use (reduce + 0.0 (for [r regions] (get uses [r product] 0.0)))
        cut (into {} (for [r regions :when (pos? (get uses [r product] 0.0))] [[r product] f]))
        o (sup/outage g cut {:at :use :hypergraph h :tol (* f 1e-3)})]
    {:use use :loss (:world (es/outage-loss va (:products o))) :lambda (/ (:world (es/outage-loss va (:products o))) (* f use))}))

(defn- shift
  "The largest change of any EXIOBASE product's availability (nodes in
  both graphs, sub-nodes aside) between two outages' :products."
  [g a b]
  (let [ks (distinct (concat (keys a) (keys b)))
        sub? #(get-in g [:products (second %) :parent])]
    (reduce (fn [[m k0] k] (let [d (Math/abs (- (get a k 1.0) (get b k 1.0)))] (if (> d m) [d k] [m k0])))
            [0.0 nil] (remove sub? ks))))

(defn- rare-earth-report
  "The rare-earth chain (bead bnm-1pq.31; bnm.rare-earths): its nodes and
  sizes, the balances of the split, supply shares from production, a cut
  of China's separation passed down to magnets, EV motors and wind
  generators, the chain's multipliers against chemicals n.e.c.'s, and the
  existing shocks with and without the sub-nodes."
  [rows0 rows g h g0 h0]
  (println "== rare earths: the chain as sub-nodes (bnm.rare-earths, bnm.supply/split-subnodes)")
  (let [specs (get-in rows [:split :specs])
        prod (group-by :product (:production g))
        num #(parse-double %)]
    (doseq [{:keys [code name parent inputs basis]} specs]
      (let [rs (prod code)
            t (reduce + 0.0 (map (comp num :quantity) rs))
            v (reduce + 0.0 (map (comp num :value_2011) rs))
            top (take 4 (sort-by (comp - second) (for [r rs] [(:region r) (/ (num (:quantity r)) t)])))]
        (println (format "  %s %-44s in %s: %,10.0f t  EUR %,7.0f M (%5.1f EUR/kg)  %s" code name parent t v (/ (* 1e3 v) t)
                         (str/join " " (for [[r s] top] (format "%s %.2f" r s)))))
        (println (format "         chain %s  basis %s" (or inputs "-") (dissoc basis :split-eur-m)))))
    (let [b (sup/split-balances rows0 rows)
          sum (fn [xs k] (reduce + 0.0 (keep k xs)))
          sub-t (fn [c] (reduce + 0.0 (for [r (prod c)] (num (:quantity r)))))
          sub-v (reduce + 0.0 (for [{:keys [code]} specs r (prod code)] (num (:value_2011 r))))]
      (println (format "  balances (largest relative residual, every key of either side): output %.1e, trade %.1e, use %.1e, inputs %.1e, input values %.1e"
                       (:output b) (:trade b) (:use b) (:inputs b) (:input-values b)))
      (println (format "  made by the split, set aside above: within a family (no product is its own input in the tables) %,.0f t, EUR %,.0f M %s"
                       (sum (:within b) :tonnes) (sum (:within b) :value) (mapv (juxt :recipe #(Math/round (:tonnes %))) (:within b))))
      (println (format "  clamped (a chain input over the parent's row) %,.0f t, EUR %.1f M %s"
                       (sum (:clamped b) :tonnes) (sum (:clamped b) :value)
                       (mapv (juxt #(or (:recipe %) (:trade %) (:use %)) #(Math/round (or (:tonnes %) (:short %) 0.0)) #(format "%.1f" (or (:value %) 0.0))) (:clamped b))))
      (check (every? #(< % 1e-9) ((juxt :output :trade :use :inputs :input-values) b))
             "every family's output, trade, use, inputs and input values conserved by the split, but what it records making")
      (check (and (< (sum (:clamped b) :tonnes) (* 0.05 (sub-t "C_NDFB"))) (< (sum (:clamped b) :value) (* 1e-3 sub-v)))
             (format "nothing material clamped: %.1f%% of the magnets' tonnes, %.3f%% of the sub-nodes' value"
                     (/ (* 100 (sum (:clamped b) :tonnes)) (sub-t "C_NDFB")) (/ (* 100 (sum (:clamped b) :value)) sub-v))))
    (let [ox-spec (first (filter #(= "C_REOX" (:code %)) specs))
          s-cn (/ (get-in ox-spec [:output "CN"]) (reduce + (vals (:output ox-spec))))
          lo (re/load-specs dir rows0 {:bound :quota})
          s-lo (let [o (:output (first (filter #(= "C_REOX" (:code %)) lo)))] (/ (o "CN") (reduce + (vals o))))
          imp (get-in g [:imports ["DE" "C_REOX"]])]
      (println (format "  China's share of separation, %d: %.3f (balance, upper bound), %.3f (quota, lower bound); exports gave it 0.17-0.22 (essentiality review)"
                       (get-in ox-spec [:basis :shares-year]) s-cn s-lo))
      (println (format "  Germany draws its oxides from %s" (str/join " " (for [[o s] (sort-by (comp - val) imp)] (format "%s %.3f" o s)))))
      (check (< (Math/abs (- (get imp "CN" 0.0) s-cn)) 1e-9) "a region's oxide supply shares are the production shares")
      (println "== rare-earth shock: China's separation (C_REOX output) cut 50%")
      (let [o (sup/outage g {["CN" "C_REOX"] 0.5} {:hypergraph h})
            ps (:products o)
            w (into {} (for [p ["C_REOX" "C_REMT" "C_NDFB" "C_EVMO" "C_WGEN" "C_PMMO" "C_MOTO" "C_ELMA" "C_CHEM"]]
                         [p (- 1.0 (world-avail g ps p))]))
            expect (* 0.5 s-cn)]
        (println (format "  world loss: oxides %s, metal %s, NdFeB %s, EV motors %s, wind generators %s, magnet motors n.e.c. %s; cars %.2f%%, electrical machinery %.2f%%, chemicals %.3f%%"
                         (pct (w "C_REOX")) (pct (w "C_REMT")) (pct (w "C_NDFB")) (pct (w "C_EVMO")) (pct (w "C_WGEN"))
                         (pct (w "C_PMMO")) (* 100 (w "C_MOTO")) (* 100 (w "C_ELMA")) (* 100 (w "C_CHEM"))))
        (doseq [[hz e] [["3 months" 0.05] ["12 months" 0.15] ["steady" 0.30]]]
          (let [rho (fn [s] (min 1.0 (/ (* e (- 1.0 s)) s)))]
            (println (format "  less reallocation at %s (bnm.essentiality, e_H %.2f): rho %.3f, NdFeB loss %s (quota bound: %s)"
                             hz e (rho s-cn) (pct (* (w "C_NDFB") (- 1.0 (rho s-cn))))
                             (pct (* 0.5 s-lo (- 1.0 (rho s-lo))))))))
        (check (< (Math/abs (- (w "C_NDFB") expect)) (* 0.05 expect))
               (format "world NdFeB loses about half of China's share of separation (%.3f against %.3f)" (w "C_NDFB") expect))
        (check (and (< (Math/abs (- (w "C_EVMO") (w "C_NDFB"))) 0.01) (< (Math/abs (- (w "C_WGEN") (w "C_NDFB"))) 0.01))
               "EV motors and wind generators lose with their magnets")
        (check (< (w "C_MOTO") 0.01) "cars lose under 1% (EV motors a small part of them in 2011)")))
    (println "== rare-earth multipliers (steady, every region's use cut 50%; value added lost per EUR of use lost)")
    (let [va (es/value-added g (:production g))
          va0 (es/value-added g0 (:production rows0))
          uses (es/use-values (:trade rows) (:production g))
          uses0 (es/use-values (:trade rows0) (map #(update-vals % (fn [v] (if (number? v) (str v) v))) (:production rows0)))
          m (into {} (for [p ["C_REOX" "C_REMT" "C_NDFB" "C_CHEM"]] [p (multiplier g h va uses p 0.5)]))
          m0 (multiplier g0 h0 va0 uses0 "C_CHEM" 0.5)]
      (doseq [[p {:keys [use loss lambda]}] (sort m)]
        (println (format "  %-7s use EUR %,10.0f M  loss %,10.0f M  lambda %.2f" p use loss lambda)))
      (println (format "  C_CHEM without the sub-nodes: use EUR %,10.0f M  loss %,10.0f M  lambda %.2f" (:use m0) (:loss m0) (:lambda m0)))
      (check (> (:lambda (m "C_REOX")) (:lambda m0))
             (format "rare-earth oxides' multiplier (%.2f) above chemicals n.e.c.'s (%.2f): it reaches the magnet chain"
                     (:lambda (m "C_REOX")) (:lambda m0))))
    (println "== the existing shocks with and without the sub-nodes (largest change of an EXIOBASE product's availability)")
    (doseq [[what cut opts] [["natural gas for use in Europe cut 40%" (into {} (for [r europe] [[r "C_GASE"] 0.4])) {:at :use}]
                             ["Middle East crude output cut 50%" {["WM" "C_COIL"] 0.5} {}]]]
      (let [a (:products (sup/outage g cut (assoc opts :hypergraph h)))
            b (:products (sup/outage g0 cut (assoc opts :hypergraph h0)))
            [d k] (shift g a b)]
        ;; not zero: magnets and the chain come from where they are made;
        ;; the magnet motors follow their parent's trade (as pooled at
        ;; China's 0.63 they had cost Bulgaria's lignite-heat loop 0.4%
        ;; under the crude cut, through China's machinery)
        (check (< d 0.01) (format "%s: largest change %.4f (%s), under 1%%" what d k))))))

(defn -main [& _]
  (if-not (.exists (io/file dir "recipes.csv"))
    (println "bnm.supply-check: no" dir "- run python -I scripts/product-graph.py first")
    (let [rows0 (sup/load-rows dir {:subnodes false})
          specs (re/load-specs dir rows0)
          rows (cond-> rows0 specs (sup/split-subnodes specs))
          rows (update rows :production #(mapv (fn [r] (update-vals r (fn [v] (if (number? v) (str v) v)))) %))
          g (assoc (sup/graph rows) :split (:split rows) :production (:production rows))]
      (size-report g)
      (rule-report g)
      (when (.exists (io/file dir "overrides.csv")) (override-report g))
      (balance-report g)
      (when (.exists (io/file dir "scaling.csv")) (scaling-report))
      (when (.exists (io/file dir "oil-products.csv")) (unsd-report))
      (validation-report g)
      (let [h (sup/hypergraph g)]
        (shock-report g h)
        (when specs
          (let [g0 (sup/graph rows0)]
            (rare-earth-report rows0 rows g h g0 (sup/hypergraph g0))))
        (if (.exists (io/file dir "stocks.csv"))
          (stock-report g h (sup/stock-rows (str (io/file dir "stocks.csv"))))
          (println "== stocks: no" dir "stocks.csv - run python -I scripts/supply-stocks.py")))
      (println (format "== checks: %d failed" @failures))
      (when (pos? @failures) (System/exit 1)))))
