(ns bnm.essentials-check
  "The registered rise targets of docs/ESSENTIALS.md section 8 step 2 on
  BACI HS92 (data/derived/supply/hs6-world.csv, scripts/baci.py):

    lithium hydroxide (282520) in the top 1% by data year 2018
    lithium carbonate (283691) in the top 1% by data year 2019
    cobalt oxides (282200) in the top 1% in data year 2018
    Li-ion accumulators (850780) and cars n.e.s. (870390) in the top 1%
    by data year 2022

  Prints the ranks of the listed codes for 2008-2024 (section 6's table)
  and whether each target is met; exits 1 if one is not. These targets
  were set from the exploratory ranks, so they check the code, not the
  method (see bnm.essentials).

  Then two reports that do not affect the exit code:
  - the targets under other thresholds (floor, jump, carry), to show how
    much they lean on the chosen ones;
  - the held-out test of section 8 step 8: precision of the rise top k
    against the EU 2023 critical raw materials (COM(2023) 160 Annex II)
    and the USGS 2022 critical minerals (87 FR 10381), against a random k
    from the ranked products. The lists never enter the score. Each list
    is read at the data year known when it was published (EU 2023: 2021,
    USGS 2022: 2020) and, descriptively, at 2014-2022.
    Materials become HS92 codes two ways: `strict`, the sub-nodes of
    data/derived/supply/subnodes.csv whose material is listed (the
    concordance only reaches EXIOBASE products, which pool all ores and
    metals, so it cannot name a material); `broad`, the table below, every
    listed material at its raw stages (ore, primary compound, unwrought
    metal, ferro-alloy), written from the HS92 product names and the lists
    alone, before any rank was looked at. Pooled codes (811291 gallium with
    hafnium, indium, niobium; 270112 all bituminous coal for coking coal;
    261590 Nb, Ta and V ores) count as listed.

    jolt -A:tools -M -m bnm.essentials-check [path] [edition] [subnodes]"
  (:require [bnm.concordance :as conc]
            [bnm.essentials :as ess]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def codes
  [["282520" "lithium hydroxide"]
   ["283691" "lithium carbonate"]
   ["282200" "cobalt oxides"]
   ["283324" "nickel sulphate"]
   ["850780" "accumulators n.e.s. (Li-ion)"]
   ["870390" "cars n.e.s. (EV, hybrid)"]
   ["871190" "motorcycles n.e.s. (e-bike)"]
   ["850511" "permanent magnets"]
   ["284690" "rare-earth compounds"]
   ["280461" "polysilicon"]
   ["854140" "PV cells and LEDs"]
   ["720310" "direct-reduced iron"]
   ["750110" "nickel matte"]
   ["870332" "diesel cars 1.5-2.5 l"]])

(def targets
  "[code name rule year]: :by, top 1% in some data year up to `year`; :in,
  top 1% in `year` itself."
  [["282520" "lithium hydroxide" :by 2018]
   ["283691" "lithium carbonate" :by 2019]
   ["282200" "cobalt oxides" :in 2018]
   ["850780" "accumulators n.e.s. (Li-ion)" :by 2022]
   ["870390" "cars n.e.s. (EV, hybrid)" :by 2022]])

(defn- num [s] (when-not (str/blank? s) (Double/parseDouble s)))

(defn read-world
  "The rows of one `edition` of hs6-world.csv as {:hs :year :known :hs6
  :share :tonnes :tonnes-cover :value} (:tonnes-cover nil in a file
  written before the column existed)."
  [path edition]
  (with-open [r (io/reader path)]
    (let [[header & lines] (line-seq r)
          ks (mapv keyword (str/split header #","))
          prefix (str edition ",")]
      (->> lines
           (filter #(str/starts-with? % prefix))
           (mapv (fn [line]
                   (let [m (zipmap ks (str/split line #"," -1))]
                     {:hs (:hs m) :hs6 (:hs6 m)
                      :year (Long/parseLong (:year m)) :known (Long/parseLong (:known m))
                      :value (num (:value m)) :tonnes (num (:tonnes m))
                      :tonnes-cover (num (:tonnes_cover m))
                      :share (num (:share m))})))))))

(defn- met? [ranks [code _ rule year]]
  (let [pct (fn [y] (:pct (get-in ranks [y code])))
        ok (fn [y] (when-let [p (pct y)] (<= p 0.01)))]
    (case rule
      :in (ok year)
      :by (some ok (filter #(<= % year) (keys ranks))))))

(def eu-2023
  "COM(2023) 160 Annex II: material -> HS92 codes."
  {"antimony" ["261710" "282580" "811000"]
   "arsenic" ["280480"]
   "bauxite" ["260600"]
   "baryte" ["251110"]
   "beryllium" ["811211" "811219"]
   "bismuth" ["810600"]
   "boron" ["252810" "252890" "281000" "284011" "284019" "284020"]
   "cobalt" ["260500" "282200" "282734" "810510" "810590"]
   "coking coal" ["270112"]
   "copper" ["260300" "740200" "740311" "740312" "740313" "740319"]
   "feldspar" ["252910"]
   "fluorspar" ["252921" "252922"]
   "gallium" ["811291"]
   "germanium" ["811230" "282560"]
   "hafnium" ["811291"]
   "helium" ["280429"]
   "rare earths (heavy, light)" ["280530" "284610" "284690"]
   "lithium" ["282520" "283691"]
   "magnesium" ["810411" "810419"]
   "manganese" ["260200" "282010" "282090" "720211" "720219" "720230" "811100"]
   "natural graphite" ["250410" "250490"]
   "nickel, battery grade" ["283324" "750210"]
   "niobium" ["261590" "720293" "811291"]
   "phosphate rock" ["251010" "251020"]
   "phosphorus" ["280470"]
   "platinum group metals" ["711011" "711021" "711031" "711041"]
   "scandium" ["280530"]
   "silicon metal" ["280461" "280469"]
   "strontium" ["283692"]
   "tantalum" ["261590" "810310"]
   "titanium metal" ["810810"]
   "tungsten" ["261100" "284180" "720280" "810110" "810191"]
   "vanadium" ["261590" "282530" "720292" "811240"]})

(def usgs-2022
  "The 2022 final list of critical minerals (87 FR 10381): mineral -> HS92
  codes. The fifteen rare earths and yttrium share codes."
  {"aluminum" ["260600" "281820" "760110" "760120"]
   "antimony" ["261710" "282580" "811000"]
   "arsenic" ["280480"]
   "barite" ["251110"]
   "beryllium" ["811211" "811219"]
   "bismuth" ["810600"]
   "rare earths and yttrium" ["280530" "284610" "284690"]
   "cesium, rubidium" ["280519"]
   "chromium" ["261000" "281910" "281990" "720241" "720249" "811220"]
   "cobalt" ["260500" "282200" "282734" "810510" "810590"]
   "fluorspar" ["252921" "252922"]
   "gallium, hafnium, indium" ["811291"]
   "germanium" ["811230" "282560"]
   "graphite" ["250410" "250490"]
   "lithium" ["282520" "283691"]
   "magnesium" ["810411" "810419"]
   "manganese" ["260200" "282010" "282090" "720211" "720219" "720230" "811100"]
   "nickel" ["260400" "283324" "720260" "750110" "750120" "750210" "750220"]
   "niobium" ["261590" "720293"]
   "platinum group metals" ["711011" "711021" "711031" "711041"]
   "scandium" ["280530"]
   "tantalum" ["261590" "810310"]
   "tellurium" ["280450"]
   "tin" ["260900" "800110" "800120"]
   "titanium" ["261400" "720291" "810810"]
   "tungsten" ["261100" "284180" "720280" "810110" "810191"]
   "vanadium" ["261590" "282530" "720292" "811240"]
   "zinc" ["260800" "790111" "790112" "790120"]
   "zirconium" ["261510" "282560" "810910"]})

(def subnode-lists
  "Sub-nodes whose material is on each list (products made from listed
  materials, like magnets, cells and cars, are not themselves listed)."
  {:eu #{"lithium_carbonate" "lithium_hydroxide" "cobalt_ore" "cobalt_oxides" "cobalt_unwrought"
         "cobalt_articles" "rare_earth_compounds" "rare_earth_metals" "polysilicon" "germanium"
         "gallium_pool" "graphite_natural" "nickel_sulphate" "manganese_ore" "ferromanganese"
         "manganese_metal" "manganese_oxides" "copper_cathode"}
   :usgs #{"lithium_carbonate" "lithium_hydroxide" "cobalt_ore" "cobalt_oxides" "cobalt_unwrought"
           "cobalt_articles" "rare_earth_compounds" "rare_earth_metals" "germanium" "gallium_pool"
           "graphite_natural" "nickel_sulphate" "manganese_ore" "ferromanganese" "manganese_metal"
           "manganese_oxides"}})

(def in-sample
  "The codes the registered targets were set on: left out in the
  `-targets` rows, so the held-out result does not lean on them."
  #{"282520" "283691" "282200"})

(defn label-sets
  "{[list mapping] #{hs6}} from the tables above and the sub-nodes."
  [subnodes]
  (let [strict (fn [k] (into #{} (comp (filter #(and (= "HS92" (:edition %))
                                                     (contains? (subnode-lists k) (:subnode %))))
                                       (map :code))
                             subnodes))
        broad (fn [m] (into #{} cat (vals m)))]
    {[:eu :strict] (strict :eu) [:eu :broad] (broad eu-2023)
     [:usgs :strict] (strict :usgs) [:usgs :broad] (broad usgs-2022)}))

(def variants
  [["as registered" {}]
   ["floor 2 ppm" {:min-share 2e-6}] ["floor 10 ppm" {:min-share 1e-5}]
   ["floor 20 ppm" {:min-share 2e-5}]
   ["jump 0.3" {:jump 0.3}] ["jump 1.0" {:jump 1.0}]
   ["carry 0.3" {:carry 0.3}] ["carry 0.7" {:carry 0.7}]
   ["window 6" {:window 6}] ["window 10" {:window 10}]])

(defn -main [& [path edition subnodes-path]]
  (let [path (or path "data/derived/supply/hs6-world.csv")
        edition (or edition "HS92")
        subnodes-path (or subnodes-path "data/derived/supply/subnodes.csv")
        rows (read-world path edition)
        years (range 2008 2025)
        rank-years (fn [opts ys]
                     (into {} (for [y ys]
                                [y (into {} (map (juxt :hs6 identity))
                                         (ess/rise rows y (assoc opts :rank-edition edition)))])))
        ranks (rank-years {} years)
        cell (fn [y code] (if-let [r (:rank (get-in ranks [y code]))] (str r) "-"))]
    (println (format "%s rows from %s; ranked per year: %s" edition path
                     (str/join " " (for [y years] (str y ":" (some :n (vals (ranks y))))))))
    (println (str/join "\t" (concat ["code" "product"] years)))
    (doseq [[code name] codes]
      (println (str/join "\t" (concat [code name] (map #(cell % code) years)))))
    (println)
    (let [results (doall
                   (for [[code name rule year :as t] targets]
                     (let [ok (met? ranks t)]
                       (println (format "%s %s top 1%% %s %d: %s (ranks %s)"
                                        code name (clojure.core/name rule) year
                                        (if ok "met" "NOT met")
                                        (str/join " " (for [y (range 2014 (inc year))]
                                                        (str y ":" (cell y code) "/" (some :n (vals (ranks y))))))))
                       ok)))]
      (println)
      (println "targets under other thresholds (not a search: the registered ones stay)")
      (doseq [[label opts] variants]
        (let [rk (if (empty? opts) ranks (rank-years opts (range 2014 2023)))]
          (println (format "  %-14s %s" label
                           (str/join " " (for [[code name :as t] targets]
                                           (str code ":" (if (met? rk t) "met" "no"))))))))
      (println)
      (println "held out: precision of the rise top k against the critical lists (random = base rate)")
      (let [sets (label-sets (conc/load-subnodes subnodes-path))
            report (fn [k-list mapping y labels tag]
                     (doseq [k [20 38 100]]
                       (let [{:keys [hits precision base p n labeled]}
                             (ess/precision (vals (ranks y)) labels k)]
                         (println (format "  %-5s %-6s %-8s %d top %3d: %2d hits, precision %.3f vs random %.3f (%d of %d ranked listed), lift %.1f, p %.3g"
                                          (name k-list) (name mapping) tag y k hits precision base labeled n
                                          (if (pos? base) (/ precision base) 0.0) p))))
                     (let [top (take 100 (sort-by :rank (filter :rank (vals (ranks y)))))]
                       (println (format "        hits in the top 100: %s"
                                        (str/join " " (for [e top :when (contains? labels (:hs6 e))]
                                                        (str (:hs6 e) "@" (:rank e))))))))]
        (doseq [[[k-list mapping] labels] (sort-by key sets)
                :let [y (if (= k-list :eu) 2021 2020)]]
          (report k-list mapping y labels "")
          (report k-list mapping y (reduce disj labels in-sample) "-targets"))
        (println)
        (println "  by data year, top 38 (about 1%), broad mapping: hits/38 vs expected")
        (doseq [[k-list mapping] [[:eu :broad] [:usgs :broad]]]
          (println (format "  %-5s %s" (name k-list)
                           (str/join " " (for [y (range 2014 2023)
                                               :let [{:keys [hits base k]} (ess/precision (vals (ranks y)) (sets [k-list mapping]) 38)]]
                                           (format "%d:%d/%.1f" y hits (* base k))))))))
      (System/exit (if (every? identity results) 0 1)))))
