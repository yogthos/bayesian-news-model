(ns bnm.divergence-attribution
  "H4 as attribution (bead bnm-3ka.7; docs/ECONOMIC-CHARACTER.md 8e): what
  share of the year node's skill on market crashes and banking crises the
  asset-growth divergence states account for, by trial T4's method
  (bnm.attribution, docs/T4-ATTRIBUTION.md), against the same placebos
  (the next country's states; the coverage-matched placebo primary).

  States, from data/derived/stocks/econ-divergence.csv as known at each
  year end (the latest data year published by then, at most three years
  old):
    equity-gap     econ/divergence-equity (JST equity over GDP, else WDI
                   market cap over GDP)
    valuation-gap  econ/divergence-valuation (JST price over dividends)
    house-gap      econ/divergence-house (JST house prices over GDP per head)
    wealth-gap     econ/divergence-wealth (WID private wealth over income)
    divergence     econ/divergence, the composite (its own group)
  and the nested comparison 8e asks for: the T4 financial states credit-gap
  and keen-debt-gap (bnm.horizon-profile/state-panels) alone and with the
  divergence states. The divergence states split into 'where' and 'when'
  by T4's rule (between-country share of variance, year means removed, at
  least 0.5) on the fitted span. Within mode is 'where' (each year's count
  fixed), raw mode adds 'when' (timing).

    jolt -A:tools -M -m bnm.divergence-attribution [--from 1960] [--to 2002] [--split 1980]
      [--fit-from 1960 --fit-to 2002] [--kinds banking_crisis,market_crash]
      [--penalties data/runs/sequencing/divergence-h4-penalties.edn]
      [--reps 1000] [--seed 1] [--out data/runs/sequencing/divergence-h4-design.tsv]

  Options as bnm.attribution's. Design: the rotation on 1960-2002, the
  penalties chosen and written. Registered (after registration only):
  --fit-from 1960 --fit-to 2002 --from 2002 --to 2022 with the design's
  penalties."
  (:require [bnm.attribution :as at]
            [bnm.econ-divergence :as dv]
            [bnm.horizon-profile :as hp]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def stock-path "data/derived/stocks/econ-divergence.csv")

(def state-vars
  {"equity-gap" "econ/divergence-equity" "valuation-gap" "econ/divergence-valuation"
   "house-gap" "econ/divergence-house" "wealth-gap" "econ/divergence-wealth"
   "divergence" "econ/divergence"})

(def divergence-states ["equity-gap" "valuation-gap" "house-gap" "wealth-gap"])
(def financial-states ["credit-gap" "keen-debt-gap"])
(def max-age "Data years older than this at a year end are not carried." 3)

(defn divergence-panels
  "{state {iso {year value}}} as known at each year end from a stock read
  by bnm.econ-divergence/read-stock."
  [stock]
  (into {} (for [[st v] state-vars :let [by-iso (get stock v)] :when by-iso]
             [st (into {} (for [[iso recs] by-iso :let [k (hp/as-known recs hp/years max-age)] :when (seq k)] [iso k]))])))

(defn groups
  "[[name states]] from the divergence states' between-country shares."
  [between]
  (let [where (filterv #(>= (between %) at/where-cut) divergence-states)
        when' (filterv #(< (between %) at/where-cut) divergence-states)]
    (cond-> [["divergence" divergence-states]
             ["composite" ["divergence"]]
             ["financial" financial-states]
             ["financial+divergence" (into financial-states divergence-states)]]
      (and (seq where) (seq when')) (conj ["where" where] ["when" when']))))

(defn parse-opts [args]
  (let [o (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        base (at/parse-opts args)]
    (assoc base
           :kinds (or (:kinds base) ["banking_crisis" "market_crash"])
           :penalties (or (o :penalties) "data/runs/sequencing/divergence-h4-penalties.edn")
           :out (or (o :out) "data/runs/sequencing/divergence-h4-design.tsv"))))

(defn -main [& args]
  (let [{:keys [run from to fit-from fit-to lambdas penalties reps seed out kinds] :as opts} (parse-opts args)
        _ (when (not= (some? fit-from) (some? fit-to)) (throw (ex-info "--fit-from and --fit-to go together" opts)))
        sp (at/splits opts)
        registered? (some? fit-from)
        span-origins (set (mapcat #(mapcat at/era-origins ((juxt :fit :score) %)) sp))
        rows-by-kind (into (sorted-map) (for [kd kinds :let [rs (filterv #(span-origins (:t %)) (at/year-rows run kd))] :when (seq rs)] [kd rs]))
        cs (set (map :c (mapcat val rows-by-kind)))
        panels (update-vals (merge (select-keys (hp/state-panels) financial-states)
                                   (divergence-panels (dv/read-stock stock-path)))
                            #(select-keys % cs))
        [bf bt] (if registered? [fit-from fit-to] [from to])
        between (into (sorted-map) (for [s (concat divergence-states ["divergence"] financial-states)] [s (at/between-share (panels s) bf bt)]))
        gs (groups between)
        fixed (when registered? (edn/read-string (slurp penalties)))
        t0 (System/currentTimeMillis)
        res (at/evaluate {:rows-by-kind rows-by-kind :panels panels :groups gs :splits sp
                          :lambdas lambdas :penalties fixed :reps reps :seed seed})
        buf (StringBuilder.)
        say (fn [s] (.append buf (str s "\n")))]
    (say (str "# H4 divergence attribution (T4 method), run " run ", "
              (if registered? (str "fitted on " fit-from "-" fit-to ", scored " from "-" to)
                  (str "design rotation " from "-" (:split opts) " / " (inc (:split opts)) "-" to))))
    (say (str "# splits " (pr-str sp)))
    (say "# rows scored and onsets per kind: kind\trows\tonsets")
    (doseq [[kd [n e]] (:n res)] (say (format "#  %s\t%d\t%d" kd n e)))
    (say "# states: state\tcountries\tcountry-years\tbetween-country share")
    (doseq [[s b] between :let [c (hp/coverage (panels s) bf bt)]]
      (say (format "#  %s\t%d\t%d\t%.3f" s (:countries c) (:rows c) (double b))))
    (say (str "# groups " (pr-str gs)))
    (say (str "# ridge precision, " (if registered? (str "fixed from " penalties) "chosen on the rotation") ": " (pr-str (:penalties res))))
    (say (str "# within (primary, 'where'): forecasts shifted per scored year to M0's level; raw: timing kept ('when' too)"))
    (say (str "# base recal: a + b logit p. Intervals 90%, " reps " draws of two-year blocks, seed " seed))
    (say "mode\tbase\tgroup\tkind\tscore\tstat\tvalue\tlo\thi")
    (doseq [{:keys [mode base group kind score stat value lo hi]} (:results res)]
      (say (format "%s\t%s\t%s\t%s\t%s\t%s\t%.6f\t%.6f\t%.6f" mode base group kind score stat (double (or value Double/NaN)) (double lo) (double hi))))
    (io/make-parents out)
    (spit out (str buf))
    (when-not registered?
      (spit penalties (pr-str (:penalties res)))
      (println "wrote" penalties))
    (println "wrote" out (format "%.0fs" (/ (- (System/currentTimeMillis) t0) 1000.0)))))
