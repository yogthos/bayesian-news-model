(ns bnm.econ-coupling-run
  "H5 (bead bnm-3ka.8): the coupling regressions and the stock.

    python -I scripts/econ-coupling.py
    jolt -A:tools -M -m bnm.econ-coupling-run

  Writes data/derived/econ/coupling-h5.txt (the fits) and
  data/derived/stocks/econ-coupling.csv (bnm.econ-coupling)."
  (:require [bnm.econ-coupling :as ec]
            [bnm.states :as states]
            [clojure.string :as str]))

(def pairs-path "data/derived/econ/coupling-pairs.csv")
(def stock-path "data/derived/stocks/econ-coupling.csv")
(def out-path "data/derived/econ/coupling-h5.txt")

(def offshore
  "Offshore and conduit centres (IMF 2000 list and Damgaard, Elkjaer &
  Johannesen's conduits), fixed before the fit."
  #{"LUX" "IRL" "NLD" "CHE" "SGP" "HKG" "CYM" "BMU" "VGB" "JEY" "GGY" "IMN" "BHS" "BRB" "PAN" "MUS" "CYP" "MLT"
    "LIE" "MCO" "AND" "CUW" "ABW" "SXM" "TCA" "KNA" "VCT" "LCA" "ATG" "GIB" "BHR" "MAC" "SYC" "VUT" "WSM" "MHL"
    "LBR" "BLZ" "GRD" "DMA" "VIR"})

(defn regime-at
  "(fn [iso known]) -> the holder's econ/investment-regime-index known by then."
  []
  (let [p (get (states/read-states ["data/derived/stocks/econ-factors.csv"]) "econ/investment-regime-index")]
    (fn [iso k] (some-> (get p iso) (rsubseq <= k) first val))))

(defn- line [label measure {:keys [b se t n groups]}]
  (format "%-34s %-8s b %+.4f  se %.4f  t %+.2f  n %6d  pairs %5d%s" label (name measure) (first b) (first se) (first t) n groups
          (if (> (count b) 1) (format "   x*regime b %+.4f se %.4f t %+.2f" (second b) (second se) (second t)) "")))

(defn -main [& _]
  (let [rows (ec/read-pairs pairs-path)
        rg (regime-at)
        buf (StringBuilder.)
        say (fn [s] (println s) (.append buf (str s "\n")))
        fit (fn [label measure pred]
              (let [obs (ec/clip (filterv pred (ec/observations rows measure)))]
                (when (> (count obs) 30)
                  (say (line label measure (ec/fe-ols obs))))))]
    (say "# H5: change in h's claims on i (i's new liabilities to h) / GDP_i on h's goods surplus with i / GDP_i")
    (say "# ordered-pair and year fixed effects, SE clustered by ordered pair, x and y clipped at 1%/99%")
    (say "# PIP 2001-2024 (reported by the holder), DIP 2009-2024, GCAP 2007-2020 (nationality restated); USD")
    (doseq [measure [:pip :dip :pip+dip :gcap]]
      (fit "all pairs" measure (constantly true))
      (fit "no offshore centres" measure #(not (or (offshore (:h %)) (offshore (:i %)))))
      (fit "issuer USA or GBR" measure #(#{"USA" "GBR"} (:i %)))
      (fit "issuer USA" measure #(= "USA" (:i %)))
      (fit "issuer GBR" measure #(= "GBR" (:i %)))
      (fit "holder CHN, DEU, JPN, KOR" measure #(#{"CHN" "DEU" "JPN" "KOR"} (:h %))))
    (say "# with the holder's investment-regime index (econ/investment-regime-index, as known), x and x*regime")
    (doseq [measure [:pip :pip+dip]
            [label pred] [["all pairs" (constantly true)] ["issuer USA or GBR" #(#{"USA" "GBR"} (:i %))]]]
      (let [obs (->> (ec/observations rows measure)
                     (filter pred)
                     (keep (fn [o] (when-let [r (rg (:h o) (:known o))] (assoc o :x [(first (:x o)) (* r (first (:x o)))]))))
                     ec/clip)]
        (when (> (count obs) 30) (say (line (str label ", regime") measure (ec/fe-ols obs))))))
    (spit out-path (str buf))
    (let [st (ec/stock-rows rows)]
      (ec/write-stock st stock-path)
      (doseq [[v rs] (group-by :variable st)]
        (println v (count (distinct (map :iso rs))) "issuers" (count rs) "rows"))
      (doseq [iso ["USA" "GBR" "CHN" "DEU"]]
        (println iso (str/join " " (for [r st :when (and (= iso (:iso r)) (= "econ/coupling-liability-share" (:variable r)) (#{2002 2008 2014 2020 2023} (:data-year r)))]
                                     (format "%d:%.2f" (:data-year r) (:value r)))))))
    (println "wrote" out-path stock-path)))
