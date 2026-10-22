(ns bnm.recipes-check
  "The recipe filter over the data, and its validation (bead bnm-1pq.6;
  docs/ESSENTIALS.md 8 step 5).

    jolt -A:tools -M -m bnm.recipes-check

  Needs data/derived/supply/recipe-obs/ (scripts/recipe-obs.py) beside
  the product graph's tables. Writes data/derived/supply/
  recipes-filtered.csv (recipe,region,year,mean,sd,median,n_obs: the
  posterior known each year) and prints the priors, the posteriors and
  the validation targets. OWID's power mix is held out: read here only."
  (:require [bnm.recipes :as rc]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def dir "data/derived/supply")
(def owid "data/raw/material/owid-energy/owid-energy-data.csv")

(def results (atom []))

(defn- target [label ok? detail]
  (swap! results conj [label ok?])
  (println (format "  [%s] %s: %s" (if (= :na ok?) "n/a" (if ok? "met" "NOT MET")) label detail)))

(defn- row [runs id region year]
  (first (filter #(and (= region (:region %)) (= year (:year %))) (:rows (runs id)))))

(defn- g [x] (if x (format "%.3g" (double x)) "-"))

(defn- ms [r] (if r (str (g (:mean r)) " +/- " (g (:sd r))) "-"))

(defn- owid-fossil
  "{[country year] fossil share of electricity, 0-1}."
  []
  (with-open [r (io/reader owid)]
    (let [[h & ls] (line-seq r)
          ks (str/split h #",")
          ix (into {} (map-indexed (fn [i k] [k i]) ks))
          c (ix "country") y (ix "year") f (ix "fossil_share_elec")]
      (into {} (for [l ls
                     :let [v (str/split l #"," -1)
                           x (nth v f "")]
                     :when (not (str/blank? x))]
                 [[(nth v c) (parse-long (nth v y))] (/ (parse-double x) 100.0)])))))

(defn- priors-table [pr runs]
  (println "\nPriors (90% range; floor; observations used / flagged by the screen):")
  (println (format "  %-26s %-10s %-22s %-9s %-6s %s" "recipe" "mean" "range" "floor" "obs" "unit"))
  (doseq [p (:recipes pr) :let [{:keys [n-total flags]} (runs (:id p))]]
    (println (format "  %-26s %-10s %-22s %-9s %-6s %s" (:id p) (g (:mean p))
                     (str "[" (g (:lo p)) ", " (g (:hi p)) "]") (g (:floor p))
                     (str (- n-total flags) "/" flags) (:unit p)))))

(defn- posteriors [pr runs]
  (println "\nPosteriors known in 2000, 2011, 2016 and 2025 (mean +/- sd; WLD, or the region with most data):")
  (doseq [p (:recipes pr)
          :let [rows (:rows (runs (:id p)))
                regions (set (map :region rows))
                r (if (regions "WLD") "WLD"
                      (key (apply max-key val (frequencies (map :region (filter #(pos? (:n-obs %)) rows))
                                                ))))]]
    (println (format "  %-26s %-4s %-18s %-18s %-18s %-18s noise sd %s" (:id p) r
                     (ms (row runs (:id p) r 2000)) (ms (row runs (:id p) r 2011))
                     (ms (row runs (:id p) r 2016)) (ms (row runs (:id p) r 2025))
                     (str/join "," (map #(g (Math/sqrt (:s %))) (:noise (runs (:id p)))))))))

(defn- validate [pr runs]
  (println "\nValidation:")
  ;; floors
  (let [bad (for [p (:recipes pr) r (:rows (runs (:id p)))
                  :when (or (< (:mean r) (:floor p)) (< (:median r) (:floor p)))]
              [(:id p) (:region r) (:year r)])]
    (target "every posterior at or above its floor" (empty? bad)
            (str (reduce + (map #(count (:rows (runs (:id %)))) (:recipes pr))) " rows, "
                 (count bad) " below")))
  ;; ammonia
  (let [rows (filter #(and (= 2025 (:year %)) (pos? (:n-obs %))) (:rows (runs "ammonia/gas")))
        in (filter #(<= 0.55 (:mean %) 0.75) rows)
        wide (filter #(<= 0.5 (:mean %) 0.9) rows)]
    (println (str "    gas per t NH3, 2025, by region: "
                  (str/join " " (map #(str (:region %) " " (g (:mean %))) (sort-by :mean rows)))))
    (target "gas per t NH3 in 0.55-0.75 in gas-route regions (2025)"
            (>= (count in) (* 0.8 (count rows)))
            (str (count in) " of " (count rows) " regions; in the step-5 range 0.5-0.9: " (count wide)))
    (doseq [r ["DE" "NL" "US" "RU"]]
      (println (str "    " r " 2014 " (ms (row runs "ammonia/gas" r 2014)) "  2025 " (ms (row runs "ammonia/gas" r 2025))))))
  (let [c (row runs "ammonia/coal" "CN" 2025) s (row runs "ammonia/coal-share" "CN" 2025)
        p (rc/recipe pr "ammonia/coal")]
    (target "the coal route applies in China" (and c s (> (:mean s) 0.5) (<= (:lo p) (:mean c) (:hi p)))
            (str "coal per t NH3 " (ms c) " (prior " (:lo p) "-" (:hi p) "), coal share of output " (ms s))))
  ;; steel ore
  (let [r (:rows (runs "steel-bf/iron-ore"))
        out (remove #(<= 1.4 (:mean %) 1.7) r)]
    (target "steel ore per t hot metal within 1.4-1.7 (step 5)" (empty? out)
            (str "WLD 2000 " (ms (row runs "steel-bf/iron-ore" "WLD" 2000)) ", 2025 "
                 (ms (row runs "steel-bf/iron-ore" "WLD" 2025)) "; " (count out) " of " (count r) " years outside")))
  ;; power
  (let [o (owid-fossil)
        names {"WLD" "World" "DK" "Denmark" "GB" "United Kingdom" "DE" "Germany" "CN" "China" "FR" "France"}]
    (println "    fossil share of power, posterior known in Y (data to Y-2) against OWID in Y and Y-2:")
    (doseq [[r nm] names
            y [2000 2010 2015 2020 2024]
            :let [p (row runs "power/fossil-share" r y)]
            :when p]
      (println (format "      %-3s %d  posterior %s   OWID %s (Y), %s (Y-2)" r y (ms p)
                       (g (o [nm y])) (g (o [nm (- y 2)])))))
    (let [w10 (row runs "power/fossil-share" "WLD" 2010) w24 (row runs "power/fossil-share" "WLD" 2024)
          lagged (for [y (range 2000 2025) :let [p (row runs "power/fossil-share" "WLD" y) x (o ["World" (- y 2)])]
                       :when (and p x)] (Math/abs (- (:mean p) x)))]
      (target "world fossil share tracks OWID (67.5% in 2010, 59.1% in 2024)"
              (and (< (Math/abs (- (:mean w10) 0.675)) 0.03) (< (Math/abs (- (:mean w24) 0.591)) 0.04))
              (str "posterior 2010 " (ms w10) ", 2024 " (ms w24)
                   "; mean |posterior(Y) - OWID(Y-2)| 2000-2024 " (g (/ (reduce + lagged) (count lagged))))))
    (let [falls (for [r ["DK" "GB" "DE"]
                      :let [a (row runs "power/fossil-share" r 1995) b (row runs "power/fossil-share" r 2025)]]
                  [r (when (and a b) (- (:mean a) (:mean b)))])]
      (target "Denmark, the UK and Germany fall by more than 20 points (1995 to 2025)"
              (every? #(and (second %) (> (second %) 0.2)) falls)
              (str/join ", " (map (fn [[r d]] (str r " " (g d))) falls)))))
  ;; cobalt
  (let [a (row runs "li-ion/cobalt" "WLD" 2015) b (row runs "li-ion/cobalt" "WLD" 2025)]
    (target "battery cobalt per kWh falls after 2015 (from data)" :na
            (str "2015 " (ms a) ", 2025 " (ms b) ": the fall is the prior's chemistry drift; no held series "
                 "measures cobalt per kWh (BACI tonnes of Li-ion cells and cobalt chemicals do not give kWh), "
                 "so the data cannot show it"))))

(defn -main [& _]
  (let [pr (rc/priors)
        runs (rc/build dir)
        out (str (io/file dir "recipes-filtered.csv"))]
    (rc/write-csv out runs)
    (println "wrote" out (reduce + (map #(count (:rows %)) (vals runs))) "rows")
    (priors-table pr runs)
    (posteriors pr runs)
    (validate pr runs)
    (let [rs @results]
      (println (format "\n%d of %d targets met (%d not applicable)"
                       (count (filter #(true? (second %)) rs))
                       (count (remove #(= :na (second %)) rs))
                       (count (filter #(= :na (second %)) rs)))))))
