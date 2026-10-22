(ns bnm.edges-check
  "Edge proposal (docs/ESSENTIALS.md section 8 step 7, bead bnm-1pq.8) on
  BACI: bnm.essentials/edges for every data year, from hs6-world.csv (the
  rise) and hs6-country.csv.gz (importers and exporters by country), both
  written by scripts/baci.py. Each year reads only rows with :year <= Y.

  Registered targets (section 8 step 7, and the task that built it): the
  input's proposals include the output, as its top proposal, and it beats
  the placebo, in some data year up to the given one in which the input is
  a rise candidate (top 1%):

    lithium hydroxide 282520 -> Li-ion 850780, by 2018
    lithium carbonate 283691 -> Li-ion 850780, by 2019
    cobalt oxides 282200     -> Li-ion 850780, by 2018
    nickel sulphate 283324   -> Li-ion 850780, by 2019
    Li-ion 850780            -> cars n.e.s. 870390, by 2022
    polysilicon 280461       -> PV cells 854140, by 2012

  In HS12 (data years from 2019) Li-ion is 850760. The target inputs are
  also scored in years they are not candidates, to show where the output
  stands; those rows do not count as met and are left out of precision.

  Precision is held out: an edge is known when the EXIOBASE recipe
  (recipes.csv, share_2011) of the output's parent takes the input's parent
  at a median share over regions of at least 0.005 (0.001 and 0.02 shown
  too). Parents are the sub-node parent (subnodes.csv) where the code is a
  sub-node, else the concordance's heaviest EXIOBASE product. EXIOBASE's
  products pool widely (C_CHEM is in most recipes), so the base rate is
  high and the test is the lift over it. recipes.csv has no diagonal (a
  product is not its own input there), so a pair within one parent
  (lithium hydroxide -> cathode salts 284190, both C_CHEM) can never be
  known; precision is also shown without those pairs.

  Precision is also given at the sub-node level, where the recipe parents
  (C_CHEM -> C_ELMA) are too coarse to mean much: over the scored pairs
  whose input and output are both sub-node codes, an edge is known when it
  is one of `subnode-recipes`, the sub-node links of the recipe priors
  (resources/bnm/recipe-priors.edn: li-ion/lithium, cobalt, nickel,
  graphite; ev/battery, ev/copper; pv/silicon, pv/copper).

  Robustness (review of bnm-1pq.8): --placebo-exclude hs2 leaves the
  candidate's whole HS2 chapter out of the placebo pool (default its HS4
  heading); --weight rca scores countries by specialisation instead of
  value (bnm.essentials/edge-defaults).

  Writes data/derived/supply/edge-proposals-<edition>[-<variant>].csv
  (every scored pair of the rise candidates and the targets).

    jolt -A:tools -M -m bnm.edges-check [edition] [first-year] [last-year]
         [--placebo-exclude hs4|hs2] [--weight value|rca]"
  (:require [bnm.concordance :as conc]
            [bnm.csv :as csv]
            [bnm.essentials :as ess]
            [bnm.essentials-check :as ec]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def dir "data/derived/supply")

(defn targets [edition]
  (let [li (if (= edition "HS92") "850780" "850760")]
    [["282520" li 2018 "lithium hydroxide -> Li-ion"]
     ["283691" li 2019 "lithium carbonate -> Li-ion"]
     ["282200" li 2018 "cobalt oxides -> Li-ion"]
     ["283324" li 2019 "nickel sulphate -> Li-ion"]
     [li "870390" 2022 "Li-ion -> cars n.e.s."]
     ["280461" "854140" 2012 "polysilicon -> PV cells"]]))

(def subnode-recipes
  "Sub-node links of the recipe priors (see the namespace doc), as
  [input output] sub-node names of subnodes.csv."
  [["lithium_carbonate" "li_ion_batteries"] ["lithium_hydroxide" "li_ion_batteries"]
   ["cobalt_oxides" "li_ion_batteries"] ["nickel_sulphate" "li_ion_batteries"]
   ["graphite_natural" "li_ion_batteries"] ["graphite_synthetic" "li_ion_batteries"]
   ["li_ion_batteries" "evs"] ["copper_cathode" "evs"]
   ["polysilicon" "pv_cells"] ["copper_cathode" "pv_cells"]])

(defn- windows [y] (let [lo (- y 7)] (concat (range lo (+ lo 3)) (range (- y 2) (inc y)))))

(defn- plan-codes [{:keys [candidates growing pools]}]
  (into (set candidates) (concat growing (mapcat val pools))))

(defn read-panel
  "The country panel of `edition`, streamed from the gzip file: flows of
  the rows whose (year, hs6) is in `needed` ({year #{hs6}}), and country
  totals over every product (the :rca weight)."
  [path edition needed]
  (let [prefix (str edition ",")]
    (with-open [r (io/reader (java.util.zip.GZIPInputStream. (io/input-stream path)))]
      (let [[_ & lines] (line-seq r)]
        (ess/country-panel
         (eduction
          (comp (filter #(str/starts-with? % prefix))
                (map (fn [line]
                       (let [[_ y _ hs6 c ex im] (str/split line #"," -1)]
                         {:hs6 hs6 :year (Long/parseLong y) :country c
                          :exports (Double/parseDouble ex) :imports (Double/parseDouble im)}))))
          lines)
         (:cap ess/edge-defaults)
         (fn [{:keys [year hs6]}] (contains? (get needed year #{}) hs6)))))))

(defn parents-fn
  "hs6 -> #{EXIOBASE product} for `edition` (see the namespace doc)."
  [subnodes concordance edition]
  (let [sn (reduce (fn [m r] (update m (:code r) (fnil conj #{}) (:parent r)))
                   {} (filter #(= edition (:edition %)) subnodes))
        heavy (into {} (for [[code rs] (group-by :code (filter #(= edition (:edition %)) concordance))]
                         [code #{(:product (apply max-key :weight rs))}]))]
    (fn [code] (or (sn code) (heavy code) #{}))))

(defn- fmt-p [{:keys [proposed hits precision pairs known base p top-n top-hits top-precision]}]
  (format "proposed %4d, %4d known: precision %.3f vs base %.3f (%d of %d pairs), lift %.2f, p %.3g; top-1 %d/%d = %.3f"
          proposed hits precision base known pairs (if (pos? base) (/ precision base) 0.0) p
          top-hits top-n top-precision))

(defn- parse-args
  "[positional flags] of the command line: flags {:placebo-exclude :weight}."
  [args]
  (loop [[a & more :as as] args pos [] flags {}]
    (cond
      (empty? as) [pos flags]
      (= a "--placebo-exclude") (recur (rest more) pos (assoc flags :placebo-exclude (keyword (first more))))
      (= a "--weight") (recur (rest more) pos (assoc flags :weight (keyword (first more))))
      :else (recur more (conj pos a) flags))))

(defn -main [& args]
  (let [[[edition y0 y1] flags] (parse-args args)
        edition (or edition "HS92")
        y0 (if y0 (parse-long y0) (if (= edition "HS92") 2008 2019))
        y1 (if y1 (parse-long y1) 2024)
        years (range y0 (inc y1))
        world (ec/read-world (str dir "/hs6-world.csv") edition)
        opts (merge {:rank-edition edition} flags)
        variant (str/join "-" (keep (fn [[k d]] (let [v (get flags k)] (when (and v (not= v d)) (name v))))
                                    [[:placebo-exclude :hs4] [:weight :value]]))
        _ (println (format "%s, placebos without the same %s, weight %s" edition
                           (name (get opts :placebo-exclude :hs4)) (name (get opts :weight :value))))
        ts (targets edition)
        t-inputs (set (map first ts))
        natural (into {} (for [y years] [y (:candidates (ess/edge-plan world y opts))]))
        opts-y (fn [y] (assoc opts :candidates (into (natural y) t-inputs)))
        plans (into {} (for [y years] [y (ess/edge-plan world y (opts-y y))]))
        needed (reduce (fn [m [y plan]]
                         (let [cs (plan-codes plan)]
                           (reduce #(update %1 %2 (fnil into #{}) cs) m (windows y))))
                       {} plans)
        t0 (System/currentTimeMillis)
        panel (read-panel (str dir "/hs6-country.csv.gz") edition needed)
        _ (println (format "%s: panel of %d product-years, %d countries, read in %.0f s"
                           edition (count (:flows panel)) (count (:index panel))
                           (/ (- (System/currentTimeMillis) t0) 1000.0)))
        subnodes (conc/load-subnodes (str dir "/subnodes.csv"))
        parents (parents-fn subnodes (conc/load-concordance (str dir "/concordance.csv")) edition)
        sn-codes (set (keep #(when (= edition (:edition %)) (:code %)) subnodes))
        sn-links (ess/subnode-links subnodes edition subnode-recipes)
        sn-known? (fn [a b] (contains? sn-links [a b]))
        sn-pair? (fn [e] (and (sn-codes (:input e)) (sn-codes (:output e))))
        recipes (mapv (fn [r] {:region (:region r) :output (:output r) :input (:input r)
                               :share (or (parse-double (str (:share_2011 r))) 0.0)})
                      (csv/read-maps (str dir "/recipes.csv") \,))
        floors [0.001 0.005 0.02]
        links (into {} (for [f floors] [f (ess/recipe-links recipes f)]))
        known? (fn [f] (fn [a b] (ess/known-edge? (links f) parents a b)))
        all (vec (mapcat (fn [y]
                           (let [t (System/currentTimeMillis)
                                 es (mapv #(assoc % :candidate (contains? (natural y) (:input %)))
                                          (ess/edges world panel y (opts-y y)))
                                 nat (filter :candidate es)]
                             (println (format "%d: %d candidates, %d growing outputs, %d pairs, %d proposed (%.0f s)"
                                              y (count (natural y)) (count (:growing (plans y)))
                                              (count nat) (count (filter :proposed nat))
                                              (/ (- (System/currentTimeMillis) t) 1000.0)))
                             es))
                         years))
        nat (filter :candidate all)
        by-key (into {} (map (juxt (juxt :year :input :output) identity)) all)]
    (println)
    (println "targets: rank of the output among the input's proposals by year (* candidate that year, P proposed)")
    (doseq [[a b by label] ts]
      (let [rows (for [y years :let [e (by-key [y a b])]]
                   [y e (contains? (natural y) a)])
            met (some (fn [[y e cand]] (and cand e (<= y by) (= 1 (:rank e)) (:proposed e))) rows)
            proposed (some (fn [[y e cand]] (and cand e (<= y by) (:proposed e))) rows)]
        (println (format "  %s %s -> %s by %d: top proposal %s, proposed %s" label a b by
                         (if met "met" "NOT met") (if proposed "yes" "no")))
        (println (str "    "
                      (str/join " " (for [[y e cand] rows]
                                      (str y (when cand "*") ":"
                                           (if e (format "%d%s(z%.1f,p%.2f)" (:rank e) (if (:proposed e) "P" "") (:z e) (:p e))
                                               "-"))))))))
    (println)
    (println "held out: precision of the proposals (rise candidates only) against recipe links")
    (doseq [f floors]
      (println (format "  floor %.3f, all years: %s" f (fmt-p (ess/edge-precision nat (known? f))))))
    (doseq [y years]
      (println (format "  floor 0.005, %d: %s" y (fmt-p (ess/edge-precision (filter #(= y (:year %)) nat) (known? 0.005))))))
    (let [diff (remove #(seq (filter (parents (:input %)) (parents (:output %)))) nat)]
      (println (format "  floor 0.005, without same-parent pairs (%d of %d): %s" (count diff) (count nat)
                       (fmt-p (ess/edge-precision diff (known? 0.005))))))
    (println "held out at the sub-node level: pairs of sub-node codes against the recipe priors' sub-node links")
    (println (format "  candidates: %s" (fmt-p (ess/edge-precision (filter sn-pair? nat) sn-known?))))
    (println (format "  every scored pair (targets in every year too): %s"
                     (fmt-p (ess/edge-precision (filter sn-pair? all) sn-known?))))
    (println (format "  candidates, recipe parents (floor 0.005) on the same pairs: %s"
                     (fmt-p (ess/edge-precision (filter sn-pair? nat) (known? 0.005)))))
    (println)
    (println "top proposal of each candidate, by year (proposed only)")
    (doseq [y years]
      (println (format "  %d: %s" y
                       (str/join " " (for [e (sort-by :input (filter #(and (= y (:year %)) (:candidate %) (= 1 (:rank %)) (:proposed %)) all))]
                                       (str (:input e) ">" (:output e) (if ((known? 0.005) (:input e) (:output e)) "" "?")))))))
    (let [out (str dir "/edge-proposals-" (str/lower-case edition) (when (seq variant) (str "-" variant)) ".csv")
          ks [:year :known :input :output :candidate :rank :z :p :proposed :overlap :overlap-early
              :overlap-growth :comove :placebos]]
      (with-open [w (io/writer out)]
        (.write w (str (str/join "," (map name ks)) ",known_edge,known_subnode\n"))
        (doseq [e all]
          (.write w (str (str/join "," (map #(let [v (% e)] (cond (float? v) (format "%.6g" v) (nil? v) "" :else (str v))) ks))
                         "," (if ((known? 0.005) (:input e) (:output e)) 1 0)
                         "," (cond (not (sn-pair? e)) "" (sn-known? (:input e) (:output e)) 1 :else 0) "\n"))))
      (println (format "\n%s: %d rows" out (count all))))))
