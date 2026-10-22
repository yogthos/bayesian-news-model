(ns bnm.tech-demand-run
  "Writes data/derived/stocks/tech-demand.csv from bnm.tech-demand and
  prints coverage and the validation (bead bnm-1pq.7):

    python -I scripts/tech-trade.py       # BACI EV and accumulator trade
    python -I scripts/tech-demand-ref.py  # silver and silicon references
    jolt -A:tools -M -m bnm.tech-demand-run

  Validation, nothing tuned:
    1. implied world Li, Co, Ni and graphite demand from batteries (the EV
       route and the accumulator-trade route) against world mine
       production (data/derived/stocks/stages.csv, bnm.stages) after 2015:
       the ratio's order of magnitude and the direction of year-on-year
       growth;
    2. PV's implied silver and silicon against world silver mine output
       (BGS, MCS) and silicon metal output (MYB, MCS);
    3. the correlation of the projected 5-year demand growth (fits known
       at the base year's known year) with the realised trade rise of the
       battery sub-nodes: bnm.essentials/rise score at data year t+5
       (its 8-year window, t-2..t+5), averaged over the material's HS92
       codes;
    4. the projected 5-year growth of world demand per material against
       the realised one (the demand five data years later over the base
       year's, both as written): sign agreement and correlation."
  (:require [bnm.essentials :as ess]
            [bnm.essentials-check :as ec]
            [bnm.tech-demand :as td]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def out "data/derived/stocks/tech-demand.csv")
(def years (range 1997 2027))

(def battery-materials ["lithium" "cobalt" "nickel" "graphite"])

(def subnodes
  "Battery sub-nodes of each material (data/derived/supply/subnodes.csv, HS92)."
  {"lithium" ["283691" "282520"]
   "cobalt" ["282200"]
   "nickel" ["283324"]
   "graphite" ["250410" "250490" "380110"]})

(defn- f [x d] (if (some? x) (format (str "%." d "f") (double x)) "-"))
(defn- g [x] (if (some? x) (format "%.3g" (double x)) "-"))

(defn- mean [xs] (when (seq xs) (/ (reduce + xs) (count xs))))

(defn pearson [pairs]
  (when (>= (count pairs) 3)
    (let [xs (map first pairs) ys (map second pairs) mx (mean xs) my (mean ys)
          sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
          sxx (reduce + (map #(Math/pow (- % mx) 2) xs))
          syy (reduce + (map #(Math/pow (- % my) 2) ys))]
      (when (and (pos? sxx) (pos? syy)) (/ sxy (Math/sqrt (* sxx syy)))))))

(defn- ranks [xs]
  (let [idx (sort-by #(nth xs %) (range (count xs)))
        r (double-array (count xs))]
    (loop [i 0]
      (when (< i (count idx))
        (let [j (loop [j i] (if (and (< (inc j) (count idx)) (= (nth xs (nth idx (inc j))) (nth xs (nth idx i)))) (recur (inc j)) j))
              avg (/ (+ i j 2.0) 2.0)]
          (doseq [k (range i (inc j))] (aset r (nth idx k) avg))
          (recur (inc j)))))
    (vec r)))

(defn spearman [pairs]
  (when (>= (count pairs) 3)
    (pearson (map vector (ranks (mapv first pairs)) (ranks (mapv second pairs))))))

;; ---------------------------------------------------------------- references

(defn- mine-world
  "{commodity {data-year world mine output}} from the stages stock file,
  the latest value known for each data year."
  [path]
  (with-open [r (io/reader path)]
    (let [ls (rest (line-seq r))
          by (reduce (fn [acc l]
                       (let [[iso y v x] (str/split l #"," -1)]
                         (if-let [[_ c k] (and (= "WLD" iso) (re-matches #"stages/(.+)-mine-(world|state-year)" v))]
                           (assoc-in acc [c (parse-long y) k] (parse-double x))
                           acc)))
                     {} ls)]
      (update-vals by (fn [ys] (into (sorted-map)
                                     (for [[_ {w "world" sy "state-year"}] (sort-by key ys) :when (and w sy)]
                                       [(long sy) w])))))))

(defn- ref-series [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc l] (let [[s y v] (str/split l #",")] (assoc-in acc [s (parse-long y)] (parse-double v))))
            {} (doall (rest (line-seq r))))))

(defn- at-t [entries t] (some #(when (= t (:t %)) (:x %)) entries))

(defn- world-demand
  "{data-year tonnes} of material m from technology k at WLD."
  [data k m]
  (into (sorted-map)
        (for [{:keys [t x]} (get-in data [:adds k "WLD"])
              :let [d (get (td/material-demand k x (:ints data) t) m)] :when d]
          [t d])))

(defn- yoy [series t] (let [a (series (dec t)) b (series t)] (when (and a b (pos? a) (pos? b)) (Math/log (/ b a)))))

;; ---------------------------------------------------------------- report

(defn- validate-batteries [data mine]
  (println "\n1. World battery-material demand against world mine production (data years)")
  (doseq [m battery-materials]
    (let [ev (world-demand data :ev m) li (world-demand data :li-ion m) mn (get mine m {})
          ts (filter #(>= % 2015) (sort (distinct (concat (keys ev) (keys li)))))]
      (println (str "\n  " m " (t)   year  ev-route  trade-route  mine  ev/mine  trade/mine  dlog(ev)  dlog(trade)  dlog(mine)"))
      (doseq [t ts]
        (println (str "   " t "  " (g (ev t)) "  " (g (li t)) "  " (g (mn t)) "  "
                      (g (some-> (ev t) (/ (or (mn t) Double/NaN)))) "  " (g (some-> (li t) (/ (or (mn t) Double/NaN)))) "  "
                      (f (yoy ev t) 3) "  " (f (yoy li t) 3) "  " (f (yoy mn t) 3))))
      (doseq [[nm s] [["ev" ev] ["trade" li]]]
        (let [ps (keep #(let [a (yoy s %) b (yoy mn %)] (when (and a b) [a b])) (filter #(>= % 2016) (keys s)))
              agree (count (filter (fn [[a b]] (= (pos? a) (pos? b))) ps))
              lt (filter #(and (s %) (mn %) (>= % 2015)) (keys s))
              t0 (first lt) t1 (last lt)]
          (println (str "   " nm ": yoy direction agrees " agree "/" (count ps)
                        ", r(yoy) " (f (pearson ps) 2)
                        (when (and t0 t1 (< t0 t1))
                          (str ", log growth " t0 "-" t1 ": demand " (f (Math/log (/ (s t1) (s t0))) 2)
                               " mine " (f (Math/log (/ (mn t1) (mn t0))) 2)))))))))
  (println "  (mine: bnm.stages world mine output; lithium is USGS MCS only, data years 2021- in the stock file)"))

(defn- validate-pv [data refs]
  (println "\n2. PV's implied silver and silicon against world output (data years)")
  (doseq [[m rs] [["silver" "silver-mine"] ["silicon" "silicon-metal"]]]
    (let [d (world-demand data :pv m) r (get refs rs)]
      (println (str "\n  " m "  year  pv-demand(t)  " rs "(t)  share  dlog(pv)  dlog(ref)"))
      (doseq [t (filter #(>= % 2005) (keys d))]
        (println (str "   " t "  " (g (d t)) "  " (g (r t)) "  " (f (some-> (d t) (/ (or (r t) Double/NaN))) 3) "  "
                      (f (yoy d t) 3) "  " (f (yoy r t) 3))))
      (let [ps (keep #(let [a (yoy d %) b (yoy r %)] (when (and a b) [a b])) (filter #(>= % 2006) (keys d)))]
        (println (str "   r(yoy) " (f (pearson ps) 2) " over " (count ps) " years"))))))

(defn- validate-rise [data rows]
  (println "\n3. Projected 5-year demand growth (WLD) against the realised trade rise, score at t+5")
  (let [hs (filter #(contains? (set (mapcat val subnodes)) (:hs6 %))
                   (ec/read-world "data/derived/supply/hs6-world.csv" "HS92"))
        last-year (reduce max (map :year hs))
        score (memoize (fn [y] (into {} (for [e (ess/rise hs y)] [(:hs6 e) (:score e)]))))
        w (group-by :year (filter #(= "WLD" (:iso %)) rows))
        val-at (fn [a v] (some #(when (= v (:variable %)) (:value %)) (w a)))]
    (doseq [[route suffix] [["trade route (850780)" "-li-ion-growth5"] ["EV + PV + wind total" "-growth5"]]]
      (println (str "\n  " route ":  material  t  growth5  rise(t+5)"))
      (let [pairs (for [m battery-materials a years
                        :let [t (val-at a "tech-demand/state-year")
                              t (if (= suffix "-li-ion-growth5") (- a 2) t)
                              gr (val-at a (str "tech-demand/" m suffix))
                              rs (when (and t (<= (+ t 5) last-year)) (keep (score (+ t 5)) (subnodes m)))]
                        :when (and gr (seq rs))]
                    [m t gr (mean rs)])]
        (doseq [[m t gr r] pairs] (println (str "   " m "  " t "  " (f gr 3) "  " (f r 3))))
        (doseq [m battery-materials :let [ps (map #(subvec (vec %) 2) (filter #(= m (first %)) pairs))]]
          (println (str "   " m ": n " (count ps) " pearson " (f (pearson ps) 2) " spearman " (f (spearman ps) 2))))
        (let [ps (map #(subvec (vec %) 2) pairs)]
          (println (str "   pooled: n " (count ps) " pearson " (f (pearson ps) 2) " spearman " (f (spearman ps) 2))))))))

(defn- validate-projection [rows]
  (println "\n4. Projected against realised 5-year log growth of world demand (EV + PV + wind)")
  (let [w (reduce (fn [acc {:keys [variable year value]}] (assoc-in acc [variable year] value))
                  {} (filter #(= "WLD" (:iso %)) rows))
        sy (w "tech-demand/state-year")]
    (doseq [m ["lithium" "cobalt" "nickel" "graphite" "copper" "steel" "silicon" "silver" "rare-earths"]
            :let [gs (w (str "tech-demand/" m "-growth5")) d (w (str "tech-demand/" m))
                  ps (for [[a gr] (sort-by key gs)
                           :let [now (get d a) fut (get d (+ a 5))]
                           :when (and now fut (pos? now) (pos? fut) (= (+ 5 (sy a)) (sy (+ a 5))))]
                       [gr (Math/log (/ fut now))])]]
      (println (str "  " m ": n " (count ps)
                    " sign agrees " (count (filter (fn [[a b]] (= (pos? a) (pos? b))) ps))
                    " mean projected " (f (mean (map first ps)) 2) " mean realised " (f (mean (map second ps)) 2)
                    " pearson " (f (pearson ps) 2))))))

(defn- coverage [rows]
  (println "Coverage (countries with a value, first and last year known):")
  (doseq [[v rs] (sort-by key (group-by :variable rows))
          :when (re-matches #"tech-demand/[a-z-]+" v)]
    (println (str "  " v "  " (count (distinct (map :iso rs))) "  " (reduce min (map :year rs)) "-" (reduce max (map :year rs))))))

(defn -main [& _]
  (let [data (td/load-data ".")
        rows (td/rows data years)]
    (td/write-csv out rows)
    (println "wrote" out (count rows) "rows")
    (coverage rows)
    (validate-batteries data (mine-world "data/derived/stocks/stages.csv"))
    (validate-pv data (ref-series "data/derived/supply/tech-demand-ref.csv"))
    (validate-rise data rows)
    (validate-projection rows)
    (shutdown-agents)))
