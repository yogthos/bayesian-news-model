(ns bnm.french
  "Readers for Ken French's international country files (data/raw/slow/
  market/intl/*.Dat)."
  (:require [clojure.string :as str]))

(defn- section
  "Lines after the first line matching `header`, up to the next blank."
  [lines header]
  (->> lines
       (drop-while #(not (str/includes? % header)))
       (drop 1)
       (drop-while #(not (re-find #"^\s*\d{4}" %)))
       (take-while #(re-find #"^\s*\d{4}" %))))

(defn read-country
  "{:ret {month dollar-return} :ratios {year {:ep :yld}}} from a French
  country file."
  [file]
  (let [lines (str/split-lines (slurp file))
        rets (into {} (for [l (section lines "Value-Weight Dollar Returns      All 4 Data Items Not Reqd")
                            :let [[d m] (str/split (str/trim l) #"\s+")]
                            :when (= 6 (count d))]
                        [(+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 4 6))))
                         (/ (parse-double m) 100.0)]))
        ratios (into {} (for [l (section lines "Ratios x 100")
                              :let [[y _ _ ep _ yld] (str/split (str/trim l) #"\s+")]]
                          [(parse-long y) {:ep (/ (parse-double ep) 100.0) :yld (/ (parse-double yld) 100.0)}]))]
    {:ret rets :ratios ratios}))

