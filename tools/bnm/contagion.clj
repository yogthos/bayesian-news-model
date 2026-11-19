(ns bnm.contagion
  "Exposure to banking stress abroad (bnm-ris): what separated 2007-08's
  crisis countries was how their banks were exposed to the markets that
  broke, which no own-credit input reads. A country's exposure at origin t
  is the sum over counterparties of its banks' cross-border claims on each
  (BIS locational banking statistics, scripts/lbs-claims.py, the latest
  quarter published by t) times that country's bank-equity fall over the
  origin's calendar year (Baron, Verner and Xiong's bank returns, market
  prices known at the year's end; a rise counts 0), over its GDP in US
  dollars (WDI, the latest released by t)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- month-of [date] (let [[y m] (str/split date #"-")] (+ (* 12 (parse-long y)) (dec (parse-long m)))))

(defn read-data
  "{:claims {rep (sorted-map known-month {cp usd-millions})}
    :gdp {iso (sorted-map release-month usd)}
    :returns {iso {year bank-return}}}"
  [claims-csv series-tsv annual-csv]
  {:claims (with-open [r (io/reader claims-csv)]
             (reduce (fn [acc l]
                       (let [[rep cp _ v k] (str/split l #",")]
                         (update acc rep (fnil assoc-in (sorted-map)) [(parse-long k) cp] (parse-double v))))
                     {} (rest (line-seq r))))
   :gdp (with-open [r (io/reader series-tsv)]
          (reduce (fn [acc l]
                    (if (str/includes? l "\teconomy/gdp_usd\t")
                      (let [[u _ _ _ rel v] (str/split l #"\t")]
                        (update acc u (fnil assoc (sorted-map)) (month-of rel) (parse-double v)))
                      acc))
                  {} (line-seq r)))
   :returns (with-open [r (io/reader annual-csv)]
              (reduce (fn [acc l]
                        (if (str/includes? l ",bvx_bank_ret,")
                          (let [[u y _ v] (str/split l #",")] (assoc-in acc [u (parse-long y)] (parse-double v)))
                          acc))
                      {} (rest (line-seq r))))})

(defn- latest [sm t] (some-> (rsubseq sm <= t) first val))

(defn exposure
  "Country `u`'s raw exposure at origin t, or nil without claims or GDP
  known by then."
  [{:keys [claims gdp returns]} u t]
  (let [cs (some-> (get claims u) (latest t))
        g (some-> (get gdp u) (latest t))
        y (quot t 12)]
    (when (and cs g (pos? g))
      (/ (reduce + 0.0 (for [[cp v] cs :let [r (get-in returns [cp y])] :when (and r (neg? r))] (* v 1e6 (- r))))
         g))))

(defn place-z
  "(fn [place origin]) -> `raw`'s value standardized by the mean and sd of
  every place's values at the earlier `origins` (at least `min-n`),
  capped at 5; nil where raw is nil or the past too thin."
  [raw places origins min-n]
  (let [vals-at (memoize (fn [t] (vec (keep #(raw % t) places))))
        os (vec (sort origins))]
    (memoize
     (fn [u t]
       (when-let [x (raw u t)]
         (let [past (mapcat vals-at (take-while #(< % t) os))
               n (count past)]
           (when (>= n min-n)
             (let [m (/ (reduce + past) (double n))
                   sd (Math/sqrt (/ (reduce + (map #(Math/pow (- % m) 2) past)) (double n)))]
               (when (pos? sd) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd))))))))))))

(defn log-exposure
  "log(1 + exposure): the financial centres' claims run to several times
  their GDP (Luxembourg 12 at Dec 2008, Ireland 2.1, the UK 1.0), which a
  plain standardization would let decide the slope."
  [data u t]
  (some-> (exposure data u t) Math/log1p))
