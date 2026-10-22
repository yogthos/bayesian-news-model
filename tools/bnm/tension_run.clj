(ns bnm.tension-run
  "Social tension and structural-demographic stress per country and year
  as known (bnm.tension), from the data on disk:

    jolt -A:tools -M -m bnm.tension-run

  Writes data/derived/stocks/tension.csv (iso,year,variable,value; year
  the year by whose end the value is known) and prints coverage and spot
  checks.

  Publication, as bnm.political-gauges reads it: WDI (data/slow/series.tsv)
  by each value's own availability date, but tertiary enrolment (UNESCO)
  and government debt no earlier than two years after their data year
  (the store's availability dates are its period end plus nine months for
  every series, which these two do not meet); Maddison, Correlates of War,
  the Extended PWT, WID, IMF GDD and the fiscal accounts two years after
  their data year (the year-row's national accounts a year behind V-Dem's
  year, known at the next origin). Countries only: the gazetteer's, and
  the historical states; WDI's aggregates left out, Kosovo as XKX (UVK's
  records read where XKX has none)."
  (:require [bnm.geo :as geo]
            [bnm.political-gauges :as pg]
            [bnm.tension :as tn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def wdi-vars
  #{"economy/gdp_per_capita" "people/urban_share" "people/working_age_share" "people/tertiary_enrolment"
    "debt/government_gdp" "people/population"})

(def annual-lag 2)

(def wdi-min-lag
  "Years from data year to publication at the earliest, where the store's
  availability date is too early."
  {"people/tertiary_enrolment" 2 "debt/government_gdp" 2})

(defn wdi-known
  "The year a WDI value of `var` for `data-year` is known, given the year
  of its availability date."
  ^long [var data-year avail-year]
  (max (long avail-year) (+ (long data-year) (long (get wdi-min-lag var 0)))))

(def historical
  "States no longer on the map, kept (their rows are history)."
  #{"SUN" "YUG" "CSK" "DDR" "YMD" "SCG" "ETP" "SDF" "BLX" "ANT" "TCH" "GDR" "YAR"})

(def aliases "Codes whose records another code reads." {"XKX" ["UVK"]})

(defn country-iso? [iso] (boolean (or (historical iso) (and (geo/country? iso) (not (some #{iso} (mapcat val aliases)))))))

(defn- month-of ^long [s] (+ (* 12 (parse-long (subs s 0 4))) (dec (parse-long (subs s 5 7)))))

(defn read-wdi
  "{var {iso {data-year [known-year value]}}} from the store, raw values,
  a value known in the year of its availability date."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[unit v _ pend avail value] (str/split line #"\t" -1)]
                (if (and (wdi-vars v) (seq value))
                  (let [d (quot (month-of pend) 12)]
                    (assoc-in acc [v unit d] [(wdi-known v d (quot (month-of avail) 12)) (parse-double value)]))
                  acc)))
            {} (line-seq r))))

(defn- sorted [m] (into (sorted-map) m))

(defn country-records
  "The records bnm.tension/country-stocks reads, for `iso`."
  [annual wdi iso]
  (let [codes (cons iso (aliases iso))
        a (fn [var f] (or (first (remove empty? (for [c codes] (tn/annual-records annual var c annual-lag f)))) (sorted-map)))
        w (fn [var f] (sorted (for [[d [k x]] (some #(get-in wdi [var %]) codes) :let [v (f x)] :when (some? v)] [d [k v]])))
        real (a "gov_exp_real" tn/ln)
        per-head (fn [pop] (sorted (for [[d [k x]] real :let [[kp p] (get pop d)] :when (and p (pos? p))] [d [(max k kp) (- x (Math/log p))]])))
        cow-urban (sorted (for [[d [k t]] (a "nmc_tpop" identity)
                                :let [[_ u] (get (a "nmc_upop" identity) d)]
                                :when (and u (pos? t) (pos? u))]
                            [d [k (Math/log (/ u t))]]))]
    {:income [(w "economy/gdp_per_capita" tn/ln) (a "mad_gdppc" tn/ln)]
     :urban [(w "people/urban_share" #(tn/ln (/ % 100.0))) cow-urban]
     :debt [(a "gdd_government" identity) (w "debt/government_gdp" identity)]
     :labour-share (a "epwt_labour_share" identity)
     :working-age (w "people/working_age_share" identity)
     :tertiary (w "people/tertiary_enrolment" identity)
     :top10 (a "wid_top10" identity)
     :bottom50 (a "wid_bottom50" identity)
     ;; real spending per head, its growth read within one population source
     :spending [(per-head (w "people/population" identity)) (per-head (a "mad_pop" identity))]
     :balance (a "gov_bal_gdp" identity)}))

(defn- fmt [x] (if (== x (Math/rint x)) (str (long x)) (str x)))

(defn -main [& _]
  (let [annual (merge (pg/read-annual) (pg/read-annual "data/derived/lattice-annual.csv")
                      (pg/read-annual "data/derived/fiscal.csv"))
        wdi (read-wdi "data/slow/series.tsv")
        isos (into (sorted-set)
                   (concat (for [v ["mad_gdppc" "nmc_tpop" "epwt_labour_share" "wid_top10" "gdd_government" "gov_bal_gdp" "gov_exp_gdp"]
                                 iso (keys (get annual v))] iso)
                           (for [[_ m] wdi iso (keys m)] iso)))
        isos (filter country-iso? isos)
        to 2026
        stocks (into (sorted-map)
                     (for [iso isos
                           :let [st (tn/country-stocks (country-records annual wdi iso) {})
                                 st (into (sorted-map) (filter #(<= (key %) to) st))]
                           :when (seq st)]
                       [iso st]))
        out "data/derived/stocks/tension.csv"]
    (io/make-parents out)
    (with-open [wr (io/writer out)]
      (.write wr (str tn/header "\n"))
      (doseq [[iso st] stocks [_ y v x] (tn/rows iso st)]
        (.write wr (str iso "," y "," v "," (fmt x) "\n"))))
    (println "wrote" out)
    (println "\ncoverage: variable, countries, country-years, first-last known year, countries in 2010")
    (doseq [v tn/variables
            :let [cy (for [[iso st] stocks [y row] st :when (contains? row v)] [iso y])
                  ys (map second cy)]]
      (println (format "  %-18s %4d %6d  %s-%s  %d" v (count (set (map first cy))) (count cy)
                       (if (seq ys) (apply min ys) "-") (if (seq ys) (apply max ys) "-")
                       (count (filter #(= 2010 (second %)) cy)))))
    (println "\nspot checks (year known):")
    (doseq [[iso ys] [["TUN" (range 2005 2012)] ["EGY" (range 2005 2012)] ["SYR" (range 2005 2012)]
                      ["VEN" (range 2010 2019)] ["GRC" (range 2007 2015)] ["ARG" (range 1997 2004)]
                      ["IDN" (range 1995 2001)] ["USA" (range 2005 2012)]]]
      (println iso)
      (println (str "  year " (str/join " " (map #(format "%9s" %) ["gap" "perceived" "expected" "trust" "mmp" "emp" "sfd" "psi"]))))
      (doseq [y ys :let [row (get-in stocks [iso y])]]
        (println (str "  " y " " (str/join " " (for [v ["progress-gap" "perceived-progress" "expected-progress" "trust" "mmp" "emp" "sfd" "psi"]]
                                                  (if-let [x (get row v)] (format "%9.3f" x) (format "%9s" "-"))))))))))
